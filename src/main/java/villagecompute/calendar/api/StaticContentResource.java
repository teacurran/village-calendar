package villagecompute.calendar.api;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.services.CalendarRenderingService;
import villagecompute.calendar.services.PDFRenderingService;
import villagecompute.calendar.services.StorageService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * API endpoint for static content generation.
 * CI/CD calls this endpoint to fetch template data and generated content
 * for building static product pages.
 */
@Path("/static-content")
public class StaticContentResource {

    private static final Logger LOG = Logger.getLogger(StaticContentResource.class);

    @Inject
    CalendarRenderingService calendarRenderingService;

    @Inject
    PDFRenderingService pdfRenderingService;

    @Inject
    StorageService storageService;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "site.url", defaultValue = "http://localhost:8080")
    String siteUrl;

    /**
     * Get all templates with slugs, including generated SVG and thumbnail.
     * This endpoint is called by CI to generate static product pages.
     *
     * GET /api/static-content/templates
     *
     * Returns JSON array of templates with:
     * - Template metadata (name, description, slug, etc.)
     * - Generated SVG content
     * - Thumbnail URL (uploaded to R2)
     * - All data needed to write static HTML files
     */
    @GET
    @Path("/templates")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public List<TemplateStaticData> getTemplatesForStaticGeneration() {
        LOG.info("Fetching templates for static content generation");

        List<CalendarTemplate> templates = CalendarTemplate.findActiveWithSlug();
        LOG.infof("Found %d templates with slugs", templates.size());

        int year = LocalDate.now().getYear() + 1; // Generate for next year
        List<TemplateStaticData> results = new ArrayList<>();

        for (CalendarTemplate template : templates) {
            try {
                TemplateStaticData data = generateTemplateData(template, year);
                results.add(data);
                LOG.infof("Generated data for template: %s", template.slug);
            } catch (Exception e) {
                LOG.errorf(e, "Failed to generate data for template: %s", template.slug);
            }
        }

        return results;
    }

    private TemplateStaticData generateTemplateData(CalendarTemplate template, int year) {
        // Build CalendarConfig from template
        CalendarRenderingService.CalendarConfig config = buildConfigFromTemplate(template, year);

        // Generate SVG
        String svgContent = calendarRenderingService.generateCalendarSVG(config);

        // Generate and upload thumbnail
        String thumbnailUrl = generateAndUploadThumbnail(template, svgContent, year);

        // Update template with thumbnail URL
        template.generatedThumbnailUrl = thumbnailUrl;

        // Build response
        TemplateStaticData data = new TemplateStaticData();
        data.slug = template.slug;
        data.templateId = template.id.toString();
        data.name = template.name;
        data.title = template.name + " " + year + " Calendar";
        data.description = template.description != null ? template.description : "Custom printable calendar from Village Compute";
        data.ogDescription = template.ogDescription != null ? template.ogDescription : data.description;
        data.keywords = template.metaKeywords != null ? template.metaKeywords : "calendar, custom calendar, printable calendar, " + year;
        data.thumbnailUrl = thumbnailUrl;
        data.priceFormatted = String.format("%.2f", (template.priceCents != null ? template.priceCents : 2999) / 100.0);
        data.svgContent = svgContent;
        data.year = year;
        data.siteUrl = siteUrl;

        return data;
    }

    private String generateAndUploadThumbnail(CalendarTemplate template, String svgContent, int year) {
        try {
            // Generate PNG thumbnail (1200px width for social sharing)
            byte[] thumbnailBytes = pdfRenderingService.renderSVGToPNG(svgContent, 1200);

            // Upload to R2
            String thumbnailFilename = String.format("thumbnails/template-%s-%d.png", template.slug, year);
            return storageService.uploadFile(thumbnailFilename, thumbnailBytes, "image/png");
        } catch (Exception e) {
            LOG.errorf(e, "Failed to generate thumbnail for template: %s", template.slug);
            return null;
        }
    }

    private CalendarRenderingService.CalendarConfig buildConfigFromTemplate(CalendarTemplate template, int year) {
        CalendarRenderingService.CalendarConfig config = new CalendarRenderingService.CalendarConfig();
        config.year = year;

        if (template.configuration != null) {
            applyJsonConfiguration(config, template.configuration);
        }

        config.year = year;
        return config;
    }

    private void applyJsonConfiguration(CalendarRenderingService.CalendarConfig config, JsonNode jsonConfig) {
        try {
            if (jsonConfig.has("theme")) config.theme = jsonConfig.get("theme").asText();
            if (jsonConfig.has("showMoonPhases")) config.showMoonPhases = jsonConfig.get("showMoonPhases").asBoolean();
            if (jsonConfig.has("showMoonIllumination")) config.showMoonIllumination = jsonConfig.get("showMoonIllumination").asBoolean();
            if (jsonConfig.has("showWeekNumbers")) config.showWeekNumbers = jsonConfig.get("showWeekNumbers").asBoolean();
            if (jsonConfig.has("compactMode")) config.compactMode = jsonConfig.get("compactMode").asBoolean();
            if (jsonConfig.has("showDayNames")) config.showDayNames = jsonConfig.get("showDayNames").asBoolean();
            if (jsonConfig.has("showDayNumbers")) config.showDayNumbers = jsonConfig.get("showDayNumbers").asBoolean();
            if (jsonConfig.has("showGrid")) config.showGrid = jsonConfig.get("showGrid").asBoolean();
            if (jsonConfig.has("highlightWeekends")) config.highlightWeekends = jsonConfig.get("highlightWeekends").asBoolean();
            if (jsonConfig.has("rotateMonthNames")) config.rotateMonthNames = jsonConfig.get("rotateMonthNames").asBoolean();
            if (jsonConfig.has("latitude")) config.latitude = jsonConfig.get("latitude").asDouble();
            if (jsonConfig.has("longitude")) config.longitude = jsonConfig.get("longitude").asDouble();
            if (jsonConfig.has("moonSize")) config.moonSize = jsonConfig.get("moonSize").asInt();
            if (jsonConfig.has("yearColor")) config.yearColor = jsonConfig.get("yearColor").asText();
            if (jsonConfig.has("monthColor")) config.monthColor = jsonConfig.get("monthColor").asText();
            if (jsonConfig.has("dayTextColor")) config.dayTextColor = jsonConfig.get("dayTextColor").asText();
            if (jsonConfig.has("dayNameColor")) config.dayNameColor = jsonConfig.get("dayNameColor").asText();
            if (jsonConfig.has("gridLineColor")) config.gridLineColor = jsonConfig.get("gridLineColor").asText();
            if (jsonConfig.has("holidayColor")) config.holidayColor = jsonConfig.get("holidayColor").asText();
            if (jsonConfig.has("emojiFont")) config.emojiFont = jsonConfig.get("emojiFont").asText();
            if (jsonConfig.has("layoutStyle")) config.layoutStyle = jsonConfig.get("layoutStyle").asText();
        } catch (Exception e) {
            LOG.warnf(e, "Error applying JSON configuration");
        }
    }

    /**
     * Data transfer object for static page generation.
     */
    public static class TemplateStaticData {
        public String slug;
        public String templateId;
        public String name;
        public String title;
        public String description;
        public String ogDescription;
        public String keywords;
        public String thumbnailUrl;
        public String priceFormatted;
        public String svgContent;
        public int year;
        public String siteUrl;
    }
}
