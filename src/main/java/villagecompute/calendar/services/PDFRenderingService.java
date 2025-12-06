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

            // Set a data URI as base to avoid file path resolution issues
            // This prevents Batik from trying to resolve internal references as external files
            input.setURI("data:image/svg+xml,calendar");

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
        // Fix xlink namespace issues
        svgContent = fixXlinkNamespace(svgContent);
        
        // Fix CSS URI references that cause invalid URI errors
        svgContent = fixCSSUriReferences(svgContent);
        
        return svgContent;
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
            
            // Find the opening <svg> tag and add the xlink namespace
            String xlinkNamespace = "xmlns:xlink=\"http://www.w3.org/1999/xlink\"";
            
            // Look for the <svg> tag with its existing attributes
            int svgStart = svgContent.indexOf("<svg");
            if (svgStart != -1) {
                // Find the end of the opening svg tag
                int svgEnd = svgContent.indexOf(">", svgStart);
                if (svgEnd != -1) {
                    // Insert the xlink namespace before the closing >
                    String beforeTag = svgContent.substring(0, svgEnd);
                    String afterTag = svgContent.substring(svgEnd);
                    svgContent = beforeTag + " " + xlinkNamespace + afterTag;
                    LOG.debug("Added xlink namespace to SVG");
                }
            }
        }
        
        return svgContent;
    }

    /**
     * Fix CSS URI references that cause "invalid URI" errors in Batik.
     * The main issue is with the base URI resolution causing problems with internal ID references.
     *
     * @param svgContent Original SVG content
     * @return Fixed SVG content
     */
    private String fixCSSUriReferences(String svgContent) {
        // For now, just log that we're processing CSS URI references
        // The main fix is changing the base URI to a data URI scheme
        if (svgContent.contains("url(#")) {
            LOG.debug("SVG contains CSS URI references - using data URI base to prevent resolution issues");
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
