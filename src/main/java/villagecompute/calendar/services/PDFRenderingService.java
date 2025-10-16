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

            // Create transcoder for PDF
            Transcoder transcoder = new PDFTranscoder();

            // Set PDF page size
            transcoder.addTranscodingHint(PDFTranscoder.KEY_WIDTH, PDF_WIDTH_POINTS);
            transcoder.addTranscodingHint(PDFTranscoder.KEY_HEIGHT, PDF_HEIGHT_POINTS);

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
