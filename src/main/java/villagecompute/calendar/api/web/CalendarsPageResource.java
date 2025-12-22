package villagecompute.calendar.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.qute.Template;
import io.quarkus.vertx.web.Route;
import io.quarkus.vertx.web.RouteBase;
import io.smallrye.common.annotation.Blocking;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.services.CalendarRenderingService;
import villagecompute.calendar.services.PDFRenderingService;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Serves dynamically rendered calendar product pages using Qute templates.
 * These pages are rendered at runtime from the database, making development easier.
 */
@RouteBase(path = "/calendars", produces = "text/html")
public class CalendarsPageResource {

    private static final Logger LOG = Logger.getLogger(CalendarsPageResource.class);

    @Inject
    Template calendarsIndex;

    @Inject
    Template calendarsProduct;

    @Inject
    CalendarRenderingService calendarRenderingService;

    @Inject
    PDFRenderingService pdfRenderingService;

    /**
     * Redirect /calendars to /calendars/ for consistency
     */
    @Route(path = "", methods = Route.HttpMethod.GET)
    public void indexRedirect(RoutingContext rc) {
        rc.response()
            .setStatusCode(301)
            .putHeader("Location", "/calendars/")
            .end();
    }

    /**
     * Calendar product index page at /calendars/
     */
    @Route(path = "/", methods = Route.HttpMethod.GET)
    @Blocking
    public void index(RoutingContext rc) {
        List<CalendarTemplate> calendars = CalendarTemplate.findActiveWithSlug();

        String html = calendarsIndex
            .data("title", "Pre-Designed Calendars")
            .data("description", "Browse our collection of beautiful, customizable calendars. Premium prints shipped to your door.")
            .data("calendars", calendars.stream().map(this::toProductView).toList())
            .data("currentYear", java.time.Year.now().getValue())
            .render();

        rc.response()
            .putHeader("Content-Type", "text/html")
            .end(html);
    }

    /**
     * Individual calendar product page at /calendars/{slug}
     */
    @Route(path = "/:slug", methods = Route.HttpMethod.GET)
    @Blocking
    public void product(RoutingContext rc) {
        String slug = rc.pathParam("slug");

        // Don't handle asset requests here (they have file extensions)
        if (slug.contains(".")) {
            rc.next();
            return;
        }

        CalendarTemplate calendar = CalendarTemplate.findBySlug(slug);

        if (calendar == null) {
            rc.response()
                .setStatusCode(404)
                .putHeader("Content-Type", "text/html")
                .end("<html><body><h1>Calendar Not Found</h1><p>The calendar you're looking for doesn't exist.</p><p><a href=\"/calendars/\">Browse all calendars</a></p></body></html>");
            return;
        }

        // Generate SVG preview
        String svgContent = generateSvgForTemplate(calendar);

        CalendarProductView view = toProductView(calendar);

        String html = calendarsProduct
            .data("calendar", view)
            .data("svgContent", svgContent)
            .data("configuration", calendar.getConfigurationJson())
            .data("currentYear", java.time.Year.now().getValue())
            .render();

        rc.response()
            .putHeader("Content-Type", "text/html")
            .end(html);
    }

    /**
     * Serve SVG content for a calendar at /calendars/{slug}/{slug}.svg
     */
    @Route(path = "/:slug/:filename", methods = Route.HttpMethod.GET)
    @Blocking
    public void asset(RoutingContext rc) {
        String slug = rc.pathParam("slug");
        String filename = rc.pathParam("filename");

        // Handle SVG requests
        if (filename.endsWith(".svg")) {
            String expectedFilename = slug + ".svg";
            if (!filename.equals(expectedFilename)) {
                rc.response()
                    .setStatusCode(404)
                    .end("Asset not found");
                return;
            }

            CalendarTemplate calendar = CalendarTemplate.findBySlug(slug);
            if (calendar == null) {
                rc.response()
                    .setStatusCode(404)
                    .end("Calendar not found");
                return;
            }

            String svgContent = generateSvgForTemplate(calendar);

            rc.response()
                .putHeader("Content-Type", "image/svg+xml")
                .putHeader("Cache-Control", "public, max-age=3600")
                .end(svgContent);
            return;
        }

        // Handle PNG requests - renders with white borders like print preview
        if (filename.endsWith(".png")) {
            String expectedFilename = slug + ".png";
            if (!filename.equals(expectedFilename)) {
                rc.response()
                    .setStatusCode(404)
                    .end("Asset not found");
                return;
            }

            CalendarTemplate calendar = CalendarTemplate.findBySlug(slug);
            if (calendar == null) {
                rc.response()
                    .setStatusCode(404)
                    .end("Calendar not found");
                return;
            }

            String svgContent = generateSvgForTemplate(calendar);

            // Wrap SVG with white background and margins to match print artboard
            String wrappedSvg = calendarRenderingService.wrapSvgForPreview(svgContent);

            try {
                // Generate PNG thumbnail (1200px width for good quality on index page)
                byte[] pngBytes = pdfRenderingService.renderSVGToPNG(wrappedSvg, 1200);

                rc.response()
                    .putHeader("Content-Type", "image/png")
                    .putHeader("Cache-Control", "public, max-age=3600")
                    .end(Buffer.buffer(pngBytes));
            } catch (Exception e) {
                LOG.errorf(e, "Failed to generate PNG for calendar: %s", slug);
                rc.response()
                    .setStatusCode(500)
                    .end("Failed to generate PNG");
            }
            return;
        }

        // Unknown asset type
        rc.response()
            .setStatusCode(404)
            .end("Asset not found");
    }

    /**
     * Generate SVG from a calendar template using CalendarRenderingService.
     */
    private String generateSvgForTemplate(CalendarTemplate template) {
        int year = LocalDate.now().getYear() + 1;
        CalendarRenderingService.CalendarConfig config = buildConfigFromTemplate(template, year);
        return calendarRenderingService.generateCalendarSVG(config);
    }

    /**
     * Build CalendarConfig from a template's JSON configuration.
     */
    private CalendarRenderingService.CalendarConfig buildConfigFromTemplate(CalendarTemplate template, int year) {
        CalendarRenderingService.CalendarConfig config = new CalendarRenderingService.CalendarConfig();
        config.year = year;

        if (template.configuration != null) {
            applyJsonConfiguration(config, template.configuration);
        }

        // Always use the target year, even if configuration has a different year
        config.year = year;
        return config;
    }

    /**
     * Apply JSON configuration to CalendarConfig.
     * Mirrors the logic from StaticContentResource.
     */
    private void applyJsonConfiguration(CalendarRenderingService.CalendarConfig config, JsonNode jsonConfig) {
        try {
            // String fields
            if (jsonConfig.has("theme")) config.theme = jsonConfig.get("theme").asText();
            if (jsonConfig.has("moonDisplayMode")) config.moonDisplayMode = jsonConfig.get("moonDisplayMode").asText();

            // Boolean fields
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

            // Complex types: customDates
            if (jsonConfig.has("customDates") && jsonConfig.get("customDates").isObject()) {
                JsonNode customDates = jsonConfig.get("customDates");
                customDates.fields().forEachRemaining(entry -> {
                    String date = entry.getKey();
                    JsonNode value = entry.getValue();
                    if (value.isTextual()) {
                        config.customDates.put(date, value.asText());
                    } else if (value.isObject()) {
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

            // Complex types: eventTitles
            if (jsonConfig.has("eventTitles") && jsonConfig.get("eventTitles").isObject()) {
                JsonNode eventTitles = jsonConfig.get("eventTitles");
                eventTitles.fields().forEachRemaining(entry -> {
                    config.eventTitles.put(entry.getKey(), entry.getValue().asText());
                });
            }

            // Complex types: holidays
            if (jsonConfig.has("holidays") && jsonConfig.get("holidays").isArray()) {
                config.holidays.clear();
                jsonConfig.get("holidays").forEach(holiday -> {
                    config.holidays.add(holiday.asText());
                });
            }

            // Complex types: holidaySets
            if (jsonConfig.has("holidaySets") && jsonConfig.get("holidaySets").isArray()) {
                config.holidaySets.clear();
                jsonConfig.get("holidaySets").forEach(set -> {
                    config.holidaySets.add(set.asText());
                });
            }

            // Complex types: holidayEmojis
            if (jsonConfig.has("holidayEmojis") && jsonConfig.get("holidayEmojis").isObject()) {
                config.holidayEmojis.clear();
                JsonNode holidayEmojis = jsonConfig.get("holidayEmojis");
                holidayEmojis.fields().forEachRemaining(entry -> {
                    config.holidayEmojis.put(entry.getKey(), entry.getValue().asText());
                });
            }

            // Complex types: holidayNames
            if (jsonConfig.has("holidayNames") && jsonConfig.get("holidayNames").isObject()) {
                config.holidayNames.clear();
                JsonNode holidayNames = jsonConfig.get("holidayNames");
                holidayNames.fields().forEachRemaining(entry -> {
                    config.holidayNames.put(entry.getKey(), entry.getValue().asText());
                });
            }

        } catch (Exception e) {
            LOG.warnf(e, "Error applying JSON configuration");
        }
    }

    private CalendarProductView toProductView(CalendarTemplate template) {
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.US);
        String priceFormatted = currencyFormat.format((template.priceCents != null ? template.priceCents : 2999) / 100.0);

        // Extract year from configuration if available, otherwise use next year
        int year = LocalDate.now().getYear() + 1;
        if (template.configuration != null && template.configuration.has("year")) {
            year = template.configuration.get("year").asInt(year);
        }

        return new CalendarProductView(
            template.id.toString(),
            template.slug,
            template.name,
            template.name + " " + year + " Calendar",
            template.description,
            template.ogDescription,
            template.metaKeywords,
            priceFormatted,
            year
        );
    }

    /**
     * View model for calendar product data in templates.
     */
    public record CalendarProductView(
        String templateId,
        String slug,
        String name,
        String title,
        String description,
        String ogDescription,
        String keywords,
        String priceFormatted,
        int year
    ) {}
}
