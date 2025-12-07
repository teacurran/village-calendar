package villagecompute.calendar.services;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.batik.transcoder.Transcoder;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.fop.svg.PDFTranscoder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.jboss.logging.Logger;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;

/**
 * Service for rendering SVG content to PDF using Apache Batik.
 * Also handles PDF metadata cleaning to remove technology fingerprints.
 */
@ApplicationScoped
public class PDFRenderingService {

    private static final Logger LOG = Logger.getLogger(PDFRenderingService.class);

    // PDF page size: 35w x 23h inches (landscape)
    // PDF units are in points (1 inch = 72 points)
    private static final float PDF_WIDTH_POINTS = 35 * 72;  // 2520 points
    private static final float PDF_HEIGHT_POINTS = 23 * 72;  // 1656 points

    /**
     * Render SVG content to PDF format.
     * Uses Apache Batik's PDFTranscoder for SVG to PDF conversion.
     *
     * @param svgContent The SVG content as a string
     * @param year       The calendar year (for PDF metadata)
     * @return PDF bytes
     * @throws RuntimeException if rendering fails
     */
    public byte[] renderSVGToPDF(String svgContent, int year) {
        if (svgContent == null || svgContent.isEmpty()) {
            throw new IllegalArgumentException("SVG content cannot be null or empty");
        }

        try {
            LOG.debugf("Rendering SVG to PDF (SVG length: %d bytes)", svgContent.length());

            // Preprocess SVG to fix various issues
            svgContent = preprocessSVG(svgContent);

            // Create transcoder for PDF
            PDFTranscoder transcoder = new PDFTranscoder();

            // Set PDF page size
            transcoder.addTranscodingHint(PDFTranscoder.KEY_WIDTH, PDF_WIDTH_POINTS);
            transcoder.addTranscodingHint(PDFTranscoder.KEY_HEIGHT, PDF_HEIGHT_POINTS);

            // Enable auto-font detection to use system fonts (Noto Emoji, DejaVu Sans, etc.)
            transcoder.addTranscodingHint(PDFTranscoder.KEY_AUTO_FONTS, Boolean.TRUE);

            // Create input and output
            StringReader reader = new StringReader(svgContent);
            TranscoderInput input = new TranscoderInput(reader);

            // Set a dummy URI to avoid null pointer exception with style elements
            // This is CRITICAL when the SVG contains <style> tags
            input.setURI("file:///calendar.svg");

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            TranscoderOutput output = new TranscoderOutput(outputStream);

            // Perform the transcoding
            LOG.debug("Transcoding SVG to PDF...");
            transcoder.transcode(input, output);

            byte[] pdfBytes = outputStream.toByteArray();

            LOG.infof("PDF generated successfully, size: %d bytes", pdfBytes.length);

            // Post-process the PDF to clean metadata
            pdfBytes = cleanPdfMetadata(pdfBytes, year);

            return pdfBytes;

        } catch (Exception e) {
            LOG.errorf(e, "Error rendering SVG to PDF");
            throw new RuntimeException("PDF rendering failed: " + e.getMessage(), e);
        }
    }

    /**
     * Preprocess SVG content to fix various issues that cause Batik transcoding problems.
     * 
     * @param svgContent Original SVG content
     * @return Fixed SVG content
     */
    private String preprocessSVG(String svgContent) {
        try {
            // Validate input
            if (svgContent == null || svgContent.trim().isEmpty()) {
                LOG.warn("SVG content is null or empty, skipping preprocessing");
                return svgContent;
            }
            
            // Ensure content starts with proper XML structure
            String trimmedContent = svgContent.trim();
            if (!trimmedContent.startsWith("<")) {
                LOG.warn("SVG content doesn't start with XML tag, skipping preprocessing");
                return svgContent;
            }
            
            // Fix xlink namespace issues
            svgContent = fixXlinkNamespace(svgContent);
            
            // Fix CSS URI references that cause invalid URI errors
            svgContent = fixCSSUriReferences(svgContent);
            
            return svgContent;
        } catch (Exception e) {
            LOG.warnf(e, "Error during SVG preprocessing, returning original content");
            return svgContent;
        }
    }

    /**
     * Fix SVG content that contains xlink:href attributes without proper namespace declaration.
     * This is required for Batik to properly process SVG with xlink references.
     *
     * @param svgContent Original SVG content
     * @return Fixed SVG content with proper xlink namespace declaration
     */
    private String fixXlinkNamespace(String svgContent) {
        // Check if SVG contains xlink:href but doesn't declare xmlns:xlink
        if (svgContent.contains("xlink:href") && !svgContent.contains("xmlns:xlink")) {
            LOG.debug("Fixing xlink namespace declaration in SVG");
            
            // Find the opening <svg> tag more carefully to handle XML properly
            String xlinkNamespace = "xmlns:xlink=\"http://www.w3.org/1999/xlink\"";
            
            // Use regex to find the svg tag and handle various formats
            java.util.regex.Pattern svgPattern = java.util.regex.Pattern.compile(
                "(<svg[^>]*?)>", 
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL
            );
            java.util.regex.Matcher matcher = svgPattern.matcher(svgContent);
            
            if (matcher.find()) {
                String svgOpenTag = matcher.group(1);
                String replacement = svgOpenTag + " " + xlinkNamespace + ">";
                svgContent = svgContent.replace(matcher.group(0), replacement);
                LOG.debug("Added xlink namespace to SVG using regex approach");
            } else {
                // Fallback to simple approach if regex fails
                int svgStart = svgContent.indexOf("<svg");
                if (svgStart != -1) {
                    int svgEnd = svgContent.indexOf(">", svgStart);
                    if (svgEnd != -1) {
                        String beforeTag = svgContent.substring(0, svgEnd);
                        String afterTag = svgContent.substring(svgEnd);
                        svgContent = beforeTag + " " + xlinkNamespace + afterTag;
                        LOG.debug("Added xlink namespace to SVG using fallback approach");
                    }
                }
            }
        }
        
        return svgContent;
    }

    /**
     * Fix CSS URI references that cause "invalid URI" errors in Batik.
     * The main issue is with the base URI resolution causing problems with internal ID references.
     *
     * IMPORTANT: Only clip-path can use bare #id syntax. Other properties like filter, fill, stroke
     * require the url(#id) syntax and should NOT be modified.
     *
     * @param svgContent Original SVG content
     * @return Fixed SVG content
     */
    private String fixCSSUriReferences(String svgContent) {
        if (svgContent.contains("url(#")) {
            LOG.debug("SVG contains CSS URI references - fixing clip-path properties only");

            // ONLY clip-path needs fixing - it can use bare #id syntax
            // Other properties (filter, fill, stroke, mask, markers) MUST keep url(#id) syntax
            // Modifying filter="url(#id)" to filter="#id" breaks the filter completely!

            String[] cssProperties = {
                "clip-path"
            };
            
            for (String property : cssProperties) {
                // Handle attribute form: property="url(#id)"
                java.util.regex.Pattern attributePattern = java.util.regex.Pattern.compile(
                    property + "\\s*=\\s*[\"']url\\(#([^)]+)\\)[\"']", 
                    java.util.regex.Pattern.CASE_INSENSITIVE
                );
                
                java.util.regex.Matcher matcher = attributePattern.matcher(svgContent);
                StringBuffer result = new StringBuffer();
                
                while (matcher.find()) {
                    String idRef = matcher.group(1);
                    String replacement = property + "=\"#" + idRef + "\"";
                    matcher.appendReplacement(result, replacement);
                    LOG.debugf("Fixed %s attribute: url(#%s) -> #%s", property, idRef, idRef);
                }
                matcher.appendTail(result);
                svgContent = result.toString();
                
                // Handle style form: style="...property:url(#id)..."
                java.util.regex.Pattern stylePattern = java.util.regex.Pattern.compile(
                    "(style\\s*=\\s*[\"'][^\"']*?)" + property + "\\s*:\\s*url\\(#([^)]+)\\)([^\"']*[\"'])", 
                    java.util.regex.Pattern.CASE_INSENSITIVE
                );
                
                matcher = stylePattern.matcher(svgContent);
                result = new StringBuffer();
                
                while (matcher.find()) {
                    String beforeProperty = matcher.group(1);
                    String idRef = matcher.group(2);
                    String afterProperty = matcher.group(3);
                    String replacement = beforeProperty + property + ":#" + idRef + afterProperty;
                    matcher.appendReplacement(result, replacement);
                    LOG.debugf("Fixed %s style: url(#%s) -> #%s", property, idRef, idRef);
                }
                matcher.appendTail(result);
                svgContent = result.toString();
            }
        }
        
        return svgContent;
    }

    /**
     * Clean PDF metadata to remove backend technology fingerprints.
     * Replaces Apache FOP producer strings with custom branding.
     *
     * @param pdfBytes Original PDF bytes
     * @param year     Calendar year for metadata
     * @return Cleaned PDF bytes
     */
    private byte[] cleanPdfMetadata(byte[] pdfBytes, int year) {
        try {
            LOG.debug("Cleaning PDF metadata...");

            // Load the PDF document using PDFBox 3.x Loader API
            PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdfBytes);

            // Get and modify the document information
            PDDocumentInformation info = document.getDocumentInformation();

            // Clear existing metadata that reveals technology
            info.setProducer("villagecompute.com");  // Override Apache FOP producer string
            info.setCreator("Village Compute Calendar Generator");
            info.setTitle("Calendar " + year);
            info.setSubject("Calendar");

            // Remove or override any custom metadata that might exist
            info.setAuthor("");
            info.setKeywords("");

            // Save the modified PDF to a byte array
            ByteArrayOutputStream cleanOutputStream = new ByteArrayOutputStream();
            document.save(cleanOutputStream);
            document.close();

            LOG.debug("PDF metadata cleaned successfully");

            return cleanOutputStream.toByteArray();

        } catch (Exception e) {
            // If cleaning fails, return original PDF
            LOG.warnf(e, "Warning: Could not clean PDF metadata, returning original PDF");
            return pdfBytes;
        }
    }
}
