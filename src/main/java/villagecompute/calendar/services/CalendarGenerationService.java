package villagecompute.calendar.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.services.exceptions.CalendarGenerationException;
import villagecompute.calendar.services.exceptions.StorageException;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Main orchestration service for calendar generation.
 * Coordinates SVG generation, PDF rendering, and storage upload.
 * <p>
 * This service extracts and refactors the calendar generation logic from the main VillageCMS application
 * into a standalone microservice architecture.
 */
@ApplicationScoped
public class CalendarGenerationService {

    private static final Logger LOG = Logger.getLogger(CalendarGenerationService.class);

    @Inject
    CalendarRenderingService calendarRenderingService;

    @Inject
    AstronomicalCalculationService astronomicalService;

    @Inject
    PDFRenderingService pdfRenderingService;

    @Inject
    StorageService storageService;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Generate a calendar PDF for a given UserCalendar entity.
     * <p>
     * This method:
     * 1. Loads the template and merges configurations
     * 2. Generates SVG using CalendarService
     * 3. Renders PDF using PDFRenderingService
     * 4. Uploads to Cloudflare R2 using StorageService
     * 5. Updates UserCalendar.generatedPdfUrl with the public URL
     *
     * @param userCalendar The UserCalendar entity to generate a PDF for
     * @return The public URL of the generated PDF
     * @throws CalendarGenerationException if generation fails
     * @throws StorageException           if upload fails
     */
    @Transactional
    public String generateCalendar(UserCalendar userCalendar) {
        if (userCalendar == null) {
            throw new IllegalArgumentException("UserCalendar cannot be null");
        }

        if (userCalendar.year == null) {
            throw new IllegalArgumentException("UserCalendar year cannot be null");
        }

        LOG.infof("Generating calendar for UserCalendar ID: %s, Year: %d", userCalendar.id, userCalendar.year);

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
            throw new CalendarGenerationException("Calendar generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Build a CalendarConfig by merging template configuration with user configuration.
     * User settings override template defaults.
     *
     * @param userCalendar The UserCalendar entity
     * @return Merged CalendarConfig
     */
    private CalendarRenderingService.CalendarConfig buildCalendarConfig(UserCalendar userCalendar) {
        CalendarRenderingService.CalendarConfig config = new CalendarRenderingService.CalendarConfig();

        // Start with default config values
        config.year = userCalendar.year;

        // Apply template configuration if present
        if (userCalendar.template != null && userCalendar.template.configuration != null) {
            LOG.debugf("Applying template configuration from template: %s", userCalendar.template.name);
            applyJsonConfiguration(config, userCalendar.template.configuration);
        }

        // Override with user configuration if present
        if (userCalendar.configuration != null) {
            LOG.debugf("Applying user configuration overrides");
            applyJsonConfiguration(config, userCalendar.configuration);
        }

        // Ensure year is always set from UserCalendar
        config.year = userCalendar.year;

        return config;
    }

    /**
     * Apply JSON configuration to a CalendarConfig object.
     * This handles deserialization from the JSONB field.
     *
     * @param config     The CalendarConfig to modify
     * @param jsonConfig The JSON configuration node
     */
    private void applyJsonConfiguration(CalendarRenderingService.CalendarConfig config, JsonNode jsonConfig) {
        try {
            // Boolean fields
            if (jsonConfig.has("theme")) config.theme = jsonConfig.get("theme").asText();
            if (jsonConfig.has("showMoonPhases")) config.showMoonPhases = jsonConfig.get("showMoonPhases").asBoolean();
            if (jsonConfig.has("showMoonIllumination"))
                config.showMoonIllumination = jsonConfig.get("showMoonIllumination").asBoolean();
            if (jsonConfig.has("showFullMoonOnly")) config.showFullMoonOnly = jsonConfig.get("showFullMoonOnly").asBoolean();
            if (jsonConfig.has("showWeekNumbers")) config.showWeekNumbers = jsonConfig.get("showWeekNumbers").asBoolean();
            if (jsonConfig.has("compactMode")) config.compactMode = jsonConfig.get("compactMode").asBoolean();
            if (jsonConfig.has("showDayNames")) config.showDayNames = jsonConfig.get("showDayNames").asBoolean();
            if (jsonConfig.has("showDayNumbers")) config.showDayNumbers = jsonConfig.get("showDayNumbers").asBoolean();
            if (jsonConfig.has("showGrid")) config.showGrid = jsonConfig.get("showGrid").asBoolean();
            if (jsonConfig.has("highlightWeekends"))
                config.highlightWeekends = jsonConfig.get("highlightWeekends").asBoolean();
            if (jsonConfig.has("rotateMonthNames"))
                config.rotateMonthNames = jsonConfig.get("rotateMonthNames").asBoolean();

            // Numeric fields
            if (jsonConfig.has("latitude")) config.latitude = jsonConfig.get("latitude").asDouble();
            if (jsonConfig.has("longitude")) config.longitude = jsonConfig.get("longitude").asDouble();
            if (jsonConfig.has("moonSize")) config.moonSize = jsonConfig.get("moonSize").asInt();
            if (jsonConfig.has("moonOffsetX")) config.moonOffsetX = jsonConfig.get("moonOffsetX").asInt();
            if (jsonConfig.has("moonOffsetY")) config.moonOffsetY = jsonConfig.get("moonOffsetY").asInt();
            if (jsonConfig.has("moonBorderWidth")) config.moonBorderWidth = jsonConfig.get("moonBorderWidth").asDouble();

            // Color fields
            if (jsonConfig.has("yearColor")) config.yearColor = jsonConfig.get("yearColor").asText();
            if (jsonConfig.has("monthColor")) config.monthColor = jsonConfig.get("monthColor").asText();
            if (jsonConfig.has("dayTextColor")) config.dayTextColor = jsonConfig.get("dayTextColor").asText();
            if (jsonConfig.has("dayNameColor")) config.dayNameColor = jsonConfig.get("dayNameColor").asText();
            if (jsonConfig.has("gridLineColor")) config.gridLineColor = jsonConfig.get("gridLineColor").asText();
            if (jsonConfig.has("weekendBgColor")) config.weekendBgColor = jsonConfig.get("weekendBgColor").asText();
            if (jsonConfig.has("holidayColor")) config.holidayColor = jsonConfig.get("holidayColor").asText();
            if (jsonConfig.has("customDateColor")) config.customDateColor = jsonConfig.get("customDateColor").asText();
            if (jsonConfig.has("moonDarkColor")) config.moonDarkColor = jsonConfig.get("moonDarkColor").asText();
            if (jsonConfig.has("moonLightColor")) config.moonLightColor = jsonConfig.get("moonLightColor").asText();
            if (jsonConfig.has("moonBorderColor")) config.moonBorderColor = jsonConfig.get("moonBorderColor").asText();

            // String fields
            if (jsonConfig.has("emojiPosition")) config.emojiPosition = jsonConfig.get("emojiPosition").asText();
            if (jsonConfig.has("emojiFont")) config.emojiFont = jsonConfig.get("emojiFont").asText();
            if (jsonConfig.has("eventDisplayMode")) config.eventDisplayMode = jsonConfig.get("eventDisplayMode").asText();
            if (jsonConfig.has("locale")) config.locale = jsonConfig.get("locale").asText();
            if (jsonConfig.has("layoutStyle")) config.layoutStyle = jsonConfig.get("layoutStyle").asText();
            if (jsonConfig.has("timeZone")) config.timeZone = jsonConfig.get("timeZone").asText();

            // Enum fields
            if (jsonConfig.has("firstDayOfWeek")) {
                String dow = jsonConfig.get("firstDayOfWeek").asText();
                try {
                    config.firstDayOfWeek = java.time.DayOfWeek.valueOf(dow);
                } catch (IllegalArgumentException e) {
                    LOG.warnf("Invalid firstDayOfWeek value: %s", dow);
                }
            }

            // Complex types: customDates (Map<String, Object>)
            if (jsonConfig.has("customDates") && jsonConfig.get("customDates").isObject()) {
                JsonNode customDates = jsonConfig.get("customDates");
                customDates.fields().forEachRemaining(entry -> {
                    String date = entry.getKey();
                    JsonNode value = entry.getValue();
                    if (value.isTextual()) {
                        config.customDates.put(date, value.asText());
                    } else if (value.isObject()) {
                        config.customDates.put(date, objectMapper.convertValue(value, java.util.Map.class));
                    }
                });
            }

            // Complex types: eventTitles (Map<String, String>)
            if (jsonConfig.has("eventTitles") && jsonConfig.get("eventTitles").isObject()) {
                JsonNode eventTitles = jsonConfig.get("eventTitles");
                eventTitles.fields().forEachRemaining(entry -> {
                    config.eventTitles.put(entry.getKey(), entry.getValue().asText());
                });
            }

            // Complex types: holidays (Set<String>)
            if (jsonConfig.has("holidays") && jsonConfig.get("holidays").isArray()) {
                jsonConfig.get("holidays").forEach(holiday -> {
                    config.holidays.add(holiday.asText());
                });
            }

            // Complex types: holidaySets (List<String>)
            if (jsonConfig.has("holidaySets") && jsonConfig.get("holidaySets").isArray()) {
                jsonConfig.get("holidaySets").forEach(set -> {
                    config.holidaySets.add(set.asText());
                });
            }

            // Complex types: holidayEmojis (Map<String, String>)
            if (jsonConfig.has("holidayEmojis") && jsonConfig.get("holidayEmojis").isObject()) {
                JsonNode holidayEmojis = jsonConfig.get("holidayEmojis");
                holidayEmojis.fields().forEachRemaining(entry -> {
                    config.holidayEmojis.put(entry.getKey(), entry.getValue().asText());
                });
            }

            // Complex types: holidayNames (Map<String, String>)
            if (jsonConfig.has("holidayNames") && jsonConfig.get("holidayNames").isObject()) {
                JsonNode holidayNames = jsonConfig.get("holidayNames");
                holidayNames.fields().forEachRemaining(entry -> {
                    config.holidayNames.put(entry.getKey(), entry.getValue().asText());
                });
            }

        } catch (Exception e) {
            LOG.warnf(e, "Error applying JSON configuration, using defaults");
        }
    }

    /**
     * Generate a unique filename for the PDF based on the UserCalendar.
     * Format: calendar-{userId or sessionId}-{year}-{uuid}.pdf
     *
     * @param userCalendar The UserCalendar entity
     * @return Generated filename
     */
    private String generatePDFFilename(UserCalendar userCalendar) {
        String prefix;
        if (userCalendar.user != null && userCalendar.user.id != null) {
            prefix = "user-" + userCalendar.user.id.toString().substring(0, 8);
        } else if (userCalendar.sessionId != null) {
            prefix = "session-" + userCalendar.sessionId.substring(0, Math.min(8, userCalendar.sessionId.length()));
        } else {
            prefix = "anonymous";
        }

        String uuid = userCalendar.id != null ? userCalendar.id.toString().substring(0, 8) : UUID.randomUUID().toString().substring(0, 8);

        return String.format("calendar-%s-%d-%s.pdf", prefix, userCalendar.year, uuid);
    }
}
