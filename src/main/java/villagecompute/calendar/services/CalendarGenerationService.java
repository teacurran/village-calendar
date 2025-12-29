package villagecompute.calendar.services;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.services.exceptions.CalendarGenerationException;
import villagecompute.calendar.services.exceptions.StorageException;

/**
 * Main orchestration service for calendar generation. Coordinates SVG generation, PDF rendering,
 * and storage upload.
 *
 * <p>This service extracts and refactors the calendar generation logic from the main VillageCMS
 * application into a standalone microservice architecture.
 */
@ApplicationScoped
public class CalendarGenerationService {

    private static final Logger LOG = Logger.getLogger(CalendarGenerationService.class);

    @Inject CalendarRenderingService calendarRenderingService;

    @Inject AstronomicalCalculationService astronomicalService;

    @Inject PDFRenderingService pdfRenderingService;

    @Inject StorageService storageService;

    @Inject ObjectMapper objectMapper;

    /**
     * Generate a calendar PDF for a given UserCalendar entity.
     *
     * <p>This method: 1. Loads the template and merges configurations 2. Generates SVG using
     * CalendarService 3. Renders PDF using PDFRenderingService 4. Uploads to Cloudflare R2 using
     * StorageService 5. Updates UserCalendar.generatedPdfUrl with the public URL
     *
     * @param userCalendar The UserCalendar entity to generate a PDF for
     * @return The public URL of the generated PDF
     * @throws CalendarGenerationException if generation fails
     * @throws StorageException if upload fails
     */
    @Transactional
    public String generateCalendar(UserCalendar userCalendar) {
        if (userCalendar == null) {
            throw new IllegalArgumentException("UserCalendar cannot be null");
        }

        if (userCalendar.year == null) {
            throw new IllegalArgumentException("UserCalendar year cannot be null");
        }

        LOG.infof(
                "Generating calendar for UserCalendar ID: %s, Year: %d",
                userCalendar.id, userCalendar.year);

        try {
            // Step 1: Build configuration by merging template + user configuration
            CalendarRenderingService.CalendarConfig config = buildCalendarConfig(userCalendar);

            // Step 2: Generate SVG
            LOG.debug("Generating SVG...");
            String svgContent = calendarRenderingService.generateCalendarSVG(config);

            // Optionally store the generated SVG in the UserCalendar entity
            userCalendar.generatedSvg = svgContent;

            // Step 3: Render PDF from SVG
            LOG.debug("Rendering PDF from SVG...");
            byte[] pdfBytes = pdfRenderingService.renderSVGToPDF(svgContent, config.year);

            if (pdfBytes == null || pdfBytes.length == 0) {
                throw new CalendarGenerationException("PDF rendering produced empty output");
            }

            LOG.infof("PDF generated successfully, size: %d bytes", pdfBytes.length);

            // Step 4: Upload to Cloudflare R2
            LOG.debug("Uploading PDF to Cloudflare R2...");
            String filename = generatePDFFilename(userCalendar);
            String publicUrl = storageService.uploadFile(filename, pdfBytes, "application/pdf");

            LOG.infof("PDF uploaded successfully to: %s", publicUrl);

            // Step 5: Update UserCalendar with the generated PDF URL
            userCalendar.generatedPdfUrl = publicUrl;
            // Note: The transaction will automatically persist changes to the entity
            // No need to call persist() explicitly for managed entities

            LOG.infof("Calendar generation complete for UserCalendar ID: %s", userCalendar.id);

            return publicUrl;

        } catch (StorageException e) {
            LOG.errorf(e, "Failed to upload PDF to R2 for UserCalendar ID: %s", userCalendar.id);
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to generate calendar for UserCalendar ID: %s", userCalendar.id);
            throw new CalendarGenerationException(
                    "Calendar generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generate calendar SVG only (without PDF rendering or upload). Uses buildCalendarConfig to
     * properly derive moonDisplayMode -> boolean flags.
     *
     * @param userCalendar The UserCalendar entity
     * @return SVG content string
     */
    public String generateCalendarSVG(UserCalendar userCalendar) {
        CalendarRenderingService.CalendarConfig config = buildCalendarConfig(userCalendar);
        return calendarRenderingService.generateCalendarSVG(config);
    }

    /**
     * Build a CalendarConfig by merging template configuration with user configuration. User
     * settings override template defaults.
     *
     * @param userCalendar The UserCalendar entity
     * @return Merged CalendarConfig
     */
    private CalendarRenderingService.CalendarConfig buildCalendarConfig(UserCalendar userCalendar) {
        CalendarRenderingService.CalendarConfig config =
                new CalendarRenderingService.CalendarConfig();

        // Start with default config values
        config.year = userCalendar.year;

        // Apply template configuration if present
        if (userCalendar.template != null && userCalendar.template.configuration != null) {
            LOG.debugf(
                    "Applying template configuration from template: %s",
                    userCalendar.template.name);
            CalendarRenderingService.applyJsonConfiguration(
                    config, userCalendar.template.configuration);
        }

        // Override with user configuration if present
        if (userCalendar.configuration != null) {
            LOG.debugf("Applying user configuration overrides");
            CalendarRenderingService.applyJsonConfiguration(config, userCalendar.configuration);
        }

        // Ensure year is always set from UserCalendar
        config.year = userCalendar.year;

        return config;
    }

    /**
     * Generate a unique filename for the PDF based on the UserCalendar. Format: calendar-{userId or
     * sessionId}-{year}-{uuid}.pdf
     *
     * @param userCalendar The UserCalendar entity
     * @return Generated filename
     */
    private String generatePDFFilename(UserCalendar userCalendar) {
        String prefix;
        if (userCalendar.user != null && userCalendar.user.id != null) {
            prefix = "user-" + userCalendar.user.id.toString().substring(0, 8);
        } else if (userCalendar.sessionId != null) {
            prefix =
                    "session-"
                            + userCalendar.sessionId.substring(
                                    0, Math.min(8, userCalendar.sessionId.length()));
        } else {
            prefix = "anonymous";
        }

        String uuid =
                userCalendar.id != null
                        ? userCalendar.id.toString().substring(0, 8)
                        : UUID.randomUUID().toString().substring(0, 8);

        return String.format("calendar-%s-%d-%s.pdf", prefix, userCalendar.year, uuid);
    }
}
