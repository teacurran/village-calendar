package villagecompute.calendar.api;

import static villagecompute.calendar.util.MimeTypes.DISPOSITION_INLINE_PREFIX;
import static villagecompute.calendar.util.MimeTypes.HEADER_CACHE_CONTROL;
import static villagecompute.calendar.util.MimeTypes.HEADER_CONTENT_DISPOSITION;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

import com.fasterxml.jackson.databind.ObjectMapper;

import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.services.CalendarRenderingService;
import villagecompute.calendar.services.PDFRenderingService;
import villagecompute.calendar.types.CalendarConfigType;

/**
 * API endpoint for static content generation. CI/CD calls these endpoints to download data and assets for static
 * product pages.
 *
 * <p>
 * Endpoints: - GET /api/static-content/calendars.json - JSON array of all calendar products - GET
 * /api/static-content/calendars/{slug}.svg - SVG for a specific calendar - GET /api/static-content/calendars/{slug}.png
 * - PNG thumbnail for OpenGraph
 */
@Path("/static-content")
public class StaticContentResource {

    private static final Logger LOG = Logger.getLogger(StaticContentResource.class);

    @Inject
    CalendarRenderingService calendarRenderingService;

    @Inject
    PDFRenderingService pdfRenderingService;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "site.url", defaultValue = "http://localhost:8080")
    String siteUrl;

    // Cache generated SVGs within a single request lifecycle to avoid regenerating for PNG
    private final Map<String, String> svgCache = new ConcurrentHashMap<>();
    private final Map<String, CalendarTemplate> templateCache = new ConcurrentHashMap<>();

    /**
     * Get JSON data for all calendars with slugs. This is saved to data/calendars.json by CI.
     *
     * <p>
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
            data.description = template.description != null ? template.description
                    : "Custom printable calendar from Village Compute";
            data.ogDescription = template.ogDescription != null ? template.ogDescription : data.description;
            data.keywords = template.metaKeywords != null ? template.metaKeywords
                    : "calendar, custom calendar, printable calendar, " + year;
            data.priceFormatted = String.format("%.2f",
                    (template.priceCents != null ? template.priceCents : 2999) / 100.0);
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
     * Get SVG for a specific calendar. This is saved to static/calendars/{slug}.svg by CI.
     *
     * <p>
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

        return Response.ok(svgContent).header(HEADER_CONTENT_DISPOSITION, DISPOSITION_INLINE_PREFIX + slug + ".svg\"")
                .build();
    }

    /**
     * Get PNG thumbnail for a specific calendar. This is saved to static/calendars/{slug}.png by CI. Used for OpenGraph
     * og:image tags.
     *
     * <p>
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

            return Response.ok(pngBytes).header(HEADER_CONTENT_DISPOSITION, DISPOSITION_INLINE_PREFIX + slug + ".png\"")
                    .build();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to generate PNG for calendar: %s", slug);
            throw new WebApplicationException("Failed to generate PNG", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get SVG for a specific calendar by template ID. Used by the cart page to show thumbnails.
     *
     * <p>
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
                .header(HEADER_CONTENT_DISPOSITION, DISPOSITION_INLINE_PREFIX + templateId + ".svg\"").build();
    }

    /**
     * Get PNG thumbnail for a specific calendar by template ID. Used by the cart page to show thumbnails.
     *
     * <p>
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
                    .header(HEADER_CONTENT_DISPOSITION, DISPOSITION_INLINE_PREFIX + templateId + ".png\"")
                    .header(HEADER_CACHE_CONTROL, "public, max-age=86400") // Cache for 1 day
                    .build();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to generate PNG for template ID: %s", templateId);
            throw new WebApplicationException("Failed to generate PNG", Response.Status.INTERNAL_SERVER_ERROR);
        }
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
        CalendarConfigType config = buildConfigFromTemplate(template, year);
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
        CalendarConfigType config = buildConfigFromTemplate(template, year);
        String svgContent = calendarRenderingService.generateCalendarSVG(config);

        svgCache.put(cacheKey, svgContent);
        return svgContent;
    }

    private CalendarConfigType buildConfigFromTemplate(CalendarTemplate template, int year) {
        CalendarConfigType config = new CalendarConfigType();
        config.year = year;

        if (template.configuration != null) {
            try {
                String configJson = objectMapper.writeValueAsString(template.configuration);
                config = objectMapper.readValue(configJson, CalendarConfigType.class);
            } catch (Exception e) {
                LOG.warnf(e, "Error parsing template configuration, using defaults");
            }
        }

        config.year = year;
        return config;
    }

    /**
     * JSON data for calendar products (no SVG content - that's separate).
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
}
