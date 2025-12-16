package villagecompute.calendar.api;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.services.CalendarRenderingService;
import villagecompute.calendar.services.PDFRenderingService;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API endpoint for static content generation.
 * CI/CD calls these endpoints to download data and assets for static product pages.
 *
 * Endpoints:
 * - GET /api/static-content/calendars.json - JSON array of all calendar products
 * - GET /api/static-content/calendars/{slug}.svg - SVG for a specific calendar
 * - GET /api/static-content/calendars/{slug}.png - PNG thumbnail for OpenGraph
 */
@Path("/static-content")
public class StaticContentResource {

    private static final Logger LOG = Logger.getLogger(StaticContentResource.class);

    @Inject
    CalendarRenderingService calendarRenderingService;

    @Inject
    PDFRenderingService pdfRenderingService;

    @ConfigProperty(name = "site.url", defaultValue = "http://localhost:8080")
    String siteUrl;

    // Cache generated SVGs within a single request lifecycle to avoid regenerating for PNG
    private final Map<String, String> svgCache = new ConcurrentHashMap<>();
    private final Map<String, CalendarTemplate> templateCache = new ConcurrentHashMap<>();

    /**
     * Get JSON data for all calendars with slugs.
     * This is saved to data/calendars.json by CI.
     *
     * GET /api/static-content/calendars.json
     */
    @GET
    @Path("/calendars.json")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public List<CalendarJsonData> getCalendarsJson() {
        LOG.info("Fetching calendars JSON for static content");

        List<CalendarTemplate> templates = CalendarTemplate.findActiveWithSlug();
        LOG.infof("Found %d templates with slugs", templates.size());

        int year = LocalDate.now().getYear() + 1; // Generate for next year
        List<CalendarJsonData> results = new ArrayList<>();

        for (CalendarTemplate template : templates) {
            CalendarJsonData data = new CalendarJsonData();
            data.slug = template.slug;
            data.templateId = template.id.toString();
            data.name = template.name;
            data.title = template.name + " " + year + " Calendar";
            data.description = template.description != null ? template.description : "Custom printable calendar from Village Compute";
            data.ogDescription = template.ogDescription != null ? template.ogDescription : data.description;
            data.keywords = template.metaKeywords != null ? template.metaKeywords : "calendar, custom calendar, printable calendar, " + year;
            data.priceFormatted = String.format("%.2f", (template.priceCents != null ? template.priceCents : 2999) / 100.0);
            data.year = year;
            // Include full configuration JSON for cart/customization - frozen at build time
            if (template.configuration != null) {
                data.configuration = template.configuration.toString();
            }
            results.add(data);

            // Cache template for SVG/PNG generation
            templateCache.put(template.slug, template);
        }

        return results;
    }

    /**
     * Get SVG for a specific calendar.
     * This is saved to static/calendars/{slug}.svg by CI.
     *
     * GET /api/static-content/calendars/{slug}.svg
     */
    @GET
    @Path("/calendars/{slug}.svg")
    @Produces("image/svg+xml")
    @Transactional
    public Response getCalendarSvg(@PathParam("slug") String slug) {
        LOG.infof("Generating SVG for calendar: %s", slug);

        CalendarTemplate template = getTemplateBySlug(slug);
        String svgContent = generateSvgForTemplate(template);

        return Response.ok(svgContent)
                .header("Content-Disposition", "inline; filename=\"" + slug + ".svg\"")
                .build();
    }

    /**
     * Get PNG thumbnail for a specific calendar.
     * This is saved to static/calendars/{slug}.png by CI.
     * Used for OpenGraph og:image tags.
     *
     * GET /api/static-content/calendars/{slug}.png
     */
    @GET
    @Path("/calendars/{slug}.png")
    @Produces("image/png")
    @Transactional
    public Response getCalendarPng(@PathParam("slug") String slug) {
        LOG.infof("Generating PNG for calendar: %s", slug);

        CalendarTemplate template = getTemplateBySlug(slug);
        String svgContent = generateSvgForTemplate(template);

        // Wrap SVG with white background and margins to match print artboard
        String wrappedSvg = calendarRenderingService.wrapSvgForPreview(svgContent);

        try {
            // Generate PNG thumbnail (1200px width for social sharing)
            byte[] pngBytes = pdfRenderingService.renderSVGToPNG(wrappedSvg, 1200);

            return Response.ok(pngBytes)
                    .header("Content-Disposition", "inline; filename=\"" + slug + ".png\"")
                    .build();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to generate PNG for calendar: %s", slug);
            throw new WebApplicationException("Failed to generate PNG", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get SVG for a specific calendar by template ID.
     * Used by the cart page to show thumbnails.
     *
     * GET /api/static-content/template/{templateId}.svg
     */
    @GET
    @Path("/template/{templateId}.svg")
    @Produces("image/svg+xml")
    @Transactional
    public Response getCalendarSvgByTemplateId(@PathParam("templateId") String templateId) {
        LOG.infof("Generating SVG for template ID: %s", templateId);

        CalendarTemplate template = getTemplateById(templateId);
        String svgContent = generateSvgForTemplateById(template);

        return Response.ok(svgContent)
                .header("Content-Disposition", "inline; filename=\"" + templateId + ".svg\"")
                .build();
    }

    /**
     * Get PNG thumbnail for a specific calendar by template ID.
     * Used by the cart page to show thumbnails.
     *
     * GET /api/static-content/template/{templateId}.png
     */
    @GET
    @Path("/template/{templateId}.png")
    @Produces("image/png")
    @Transactional
    public Response getCalendarPngByTemplateId(@PathParam("templateId") String templateId) {
        LOG.infof("Generating PNG for template ID: %s", templateId);

        CalendarTemplate template = getTemplateById(templateId);
        String svgContent = generateSvgForTemplateById(template);

        // Wrap SVG with white background and margins to match print artboard
        String wrappedSvg = calendarRenderingService.wrapSvgForPreview(svgContent);

        try {
            // Generate PNG thumbnail (400px width for cart)
            byte[] pngBytes = pdfRenderingService.renderSVGToPNG(wrappedSvg, 400);

            return Response.ok(pngBytes)
                    .header("Content-Disposition", "inline; filename=\"" + templateId + ".png\"")
                    .header("Cache-Control", "public, max-age=86400") // Cache for 1 day
                    .build();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to generate PNG for template ID: %s", templateId);
            throw new WebApplicationException("Failed to generate PNG", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Legacy endpoint - kept for backwards compatibility.
     * Returns all template data including SVG content.
     *
     * @deprecated Use /calendars.json, /calendars/{slug}.svg, /calendars/{slug}.png instead
     */
    @GET
    @Path("/templates")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    @Deprecated
    public List<TemplateStaticData> getTemplatesForStaticGeneration() {
        LOG.info("Fetching templates for static content generation (legacy endpoint)");

        List<CalendarTemplate> templates = CalendarTemplate.findActiveWithSlug();
        int year = LocalDate.now().getYear() + 1;
        List<TemplateStaticData> results = new ArrayList<>();

        for (CalendarTemplate template : templates) {
            try {
                TemplateStaticData data = generateLegacyTemplateData(template, year);
                results.add(data);
            } catch (Exception e) {
                LOG.errorf(e, "Failed to generate data for template: %s", template.slug);
            }
        }

        return results;
    }

    private CalendarTemplate getTemplateBySlug(String slug) {
        // Check cache first
        CalendarTemplate cached = templateCache.get(slug);
        if (cached != null) {
            return cached;
        }

        // Query database
        CalendarTemplate template = CalendarTemplate.findBySlug(slug);
        if (template == null) {
            throw new WebApplicationException("Calendar not found: " + slug, Response.Status.NOT_FOUND);
        }

        templateCache.put(slug, template);
        return template;
    }

    private CalendarTemplate getTemplateById(String templateId) {
        // Check cache first
        CalendarTemplate cached = templateCache.get(templateId);
        if (cached != null) {
            return cached;
        }

        // Query database
        try {
            CalendarTemplate template = CalendarTemplate.findById(java.util.UUID.fromString(templateId));
            if (template == null) {
                throw new WebApplicationException("Template not found: " + templateId, Response.Status.NOT_FOUND);
            }
            templateCache.put(templateId, template);
            return template;
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException("Invalid template ID: " + templateId, Response.Status.BAD_REQUEST);
        }
    }

    private String generateSvgForTemplate(CalendarTemplate template) {
        // Check cache first - use slug as key
        String cacheKey = template.slug != null ? template.slug : template.id.toString();
        String cached = svgCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        int year = LocalDate.now().getYear() + 1;
        CalendarRenderingService.CalendarConfig config = buildConfigFromTemplate(template, year);
        String svgContent = calendarRenderingService.generateCalendarSVG(config);

        svgCache.put(cacheKey, svgContent);
        return svgContent;
    }

    private String generateSvgForTemplateById(CalendarTemplate template) {
        // Use ID as cache key
        String cacheKey = template.id.toString();
        String cached = svgCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        int year = LocalDate.now().getYear() + 1;
        CalendarRenderingService.CalendarConfig config = buildConfigFromTemplate(template, year);
        String svgContent = calendarRenderingService.generateCalendarSVG(config);

        svgCache.put(cacheKey, svgContent);
        return svgContent;
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
            // Boolean fields
            if (jsonConfig.has("theme")) config.theme = jsonConfig.get("theme").asText();
            if (jsonConfig.has("showMoonPhases")) config.showMoonPhases = jsonConfig.get("showMoonPhases").asBoolean();
            if (jsonConfig.has("showMoonIllumination")) config.showMoonIllumination = jsonConfig.get("showMoonIllumination").asBoolean();
            if (jsonConfig.has("showFullMoonOnly")) config.showFullMoonOnly = jsonConfig.get("showFullMoonOnly").asBoolean();

            // Derive moon display flags from moonDisplayMode - this ALWAYS overrides explicit booleans
            // because moonDisplayMode is the authoritative source (Vue sends stale boolean values)
            if (jsonConfig.has("moonDisplayMode")) {
                String moonDisplayMode = jsonConfig.get("moonDisplayMode").asText();
                config.showMoonPhases = "phases".equals(moonDisplayMode);
                config.showMoonIllumination = "illumination".equals(moonDisplayMode);
                config.showFullMoonOnly = "full-only".equals(moonDisplayMode);
            }
            if (jsonConfig.has("showWeekNumbers")) config.showWeekNumbers = jsonConfig.get("showWeekNumbers").asBoolean();
            if (jsonConfig.has("compactMode")) config.compactMode = jsonConfig.get("compactMode").asBoolean();
            if (jsonConfig.has("showDayNames")) config.showDayNames = jsonConfig.get("showDayNames").asBoolean();
            if (jsonConfig.has("showDayNumbers")) config.showDayNumbers = jsonConfig.get("showDayNumbers").asBoolean();
            if (jsonConfig.has("showGrid")) config.showGrid = jsonConfig.get("showGrid").asBoolean();
            if (jsonConfig.has("highlightWeekends")) config.highlightWeekends = jsonConfig.get("highlightWeekends").asBoolean();
            if (jsonConfig.has("rotateMonthNames")) config.rotateMonthNames = jsonConfig.get("rotateMonthNames").asBoolean();

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
                        // Convert to Map for complex event display objects
                        java.util.Map<String, Object> map = new java.util.HashMap<>();
                        value.fields().forEachRemaining(f -> {
                            if (f.getValue().isTextual()) map.put(f.getKey(), f.getValue().asText());
                            else if (f.getValue().isNumber()) map.put(f.getKey(), f.getValue().asDouble());
                            else if (f.getValue().isBoolean()) map.put(f.getKey(), f.getValue().asBoolean());
                        });
                        config.customDates.put(date, map);
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
            LOG.warnf(e, "Error applying JSON configuration");
        }
    }

    private TemplateStaticData generateLegacyTemplateData(CalendarTemplate template, int year) {
        String svgContent = generateSvgForTemplate(template);

        TemplateStaticData data = new TemplateStaticData();
        data.slug = template.slug;
        data.templateId = template.id.toString();
        data.name = template.name;
        data.title = template.name + " " + year + " Calendar";
        data.description = template.description != null ? template.description : "Custom printable calendar from Village Compute";
        data.ogDescription = template.ogDescription != null ? template.ogDescription : data.description;
        data.keywords = template.metaKeywords != null ? template.metaKeywords : "calendar, custom calendar, printable calendar, " + year;
        data.thumbnailUrl = null; // No longer uploading to R2 during API call
        data.priceFormatted = String.format("%.2f", (template.priceCents != null ? template.priceCents : 2999) / 100.0);
        data.svgContent = svgContent;
        data.year = year;
        data.siteUrl = siteUrl;

        return data;
    }

    /**
     * JSON data for calendar products (no SVG content - that's separate).
     * This matches the structure expected by ROQ @DataMapping.
     */
    public static class CalendarJsonData {
        public String slug;
        public String templateId;
        public String name;
        public String title;
        public String description;
        public String ogDescription;
        public String keywords;
        public String priceFormatted;
        public int year;
        public String configuration; // Full calendar config JSON, frozen at build time
    }

    /**
     * Legacy data transfer object (includes SVG content).
     * @deprecated Use CalendarJsonData instead
     */
    @Deprecated
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
