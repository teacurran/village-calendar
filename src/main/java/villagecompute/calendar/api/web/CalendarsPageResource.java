package villagecompute.calendar.api.web;

import static villagecompute.calendar.util.MimeTypes.*;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.services.CalendarRenderingService;
import villagecompute.calendar.services.PDFRenderingService;

import io.quarkus.qute.Template;
import io.quarkus.vertx.web.Route;
import io.quarkus.vertx.web.RouteBase;
import io.smallrye.common.annotation.Blocking;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Serves dynamically rendered calendar product pages using Qute templates. These pages are rendered
 * at runtime from the database, making development easier.
 */
@RouteBase(path = "/calendars", produces = "text/html")
public class CalendarsPageResource {

    private static final Logger LOG = Logger.getLogger(CalendarsPageResource.class);

    private static final String TEXT_HTML = "text/html";
    private static final String ASSET_NOT_FOUND = "Asset not found";

    @Inject Template calendarsIndex;

    @Inject Template calendarsProduct;

    @Inject CalendarRenderingService calendarRenderingService;

    @Inject PDFRenderingService pdfRenderingService;

    /** Redirect /calendars to /calendars/ for consistency */
    @Route(path = "", methods = Route.HttpMethod.GET)
    public void indexRedirect(RoutingContext rc) {
        rc.response().setStatusCode(301).putHeader("Location", "/calendars/").end();
    }

    /** Calendar product index page at /calendars/ */
    @Route(path = "/", methods = Route.HttpMethod.GET)
    @Blocking
    public void index(RoutingContext rc) {
        List<CalendarTemplate> calendars = CalendarTemplate.findActiveWithSlug();

        String html =
                calendarsIndex
                        .data("title", "Pre-Designed Calendars")
                        .data(
                                "description",
                                "Browse our collection of beautiful, customizable calendars."
                                        + " Premium prints shipped to your door.")
                        .data("calendars", calendars.stream().map(this::toProductView).toList())
                        .data("currentYear", java.time.Year.now().getValue())
                        .render();

        rc.response().putHeader(HEADER_CONTENT_TYPE, TEXT_HTML).end(html);
    }

    // SPA routes under /calendars that should be served by the Vue SPA
    private static final java.util.Set<String> SPA_ROUTES =
            java.util.Set.of("generator", "new", "edit");

    /** Individual calendar product page at /calendars/{slug} */
    @Route(path = "/:slug", methods = Route.HttpMethod.GET)
    @Blocking
    public void product(RoutingContext rc) {
        String slug = rc.pathParam("slug");

        // Don't handle asset requests here (they have file extensions)
        if (slug.contains(".")) {
            rc.next();
            return;
        }

        // Serve SPA index.html for SPA routes
        if (SPA_ROUTES.contains(slug)) {
            serveSpaIndex(rc);
            return;
        }

        CalendarTemplate calendar = CalendarTemplate.findBySlug(slug);

        if (calendar == null) {
            rc.response()
                    .setStatusCode(404)
                    .putHeader(HEADER_CONTENT_TYPE, TEXT_HTML)
                    .end(
                            "<html><body><h1>Calendar Not Found</h1><p>The calendar you're looking"
                                    + " for doesn't exist.</p><p><a href=\"/calendars/\">Browse all"
                                    + " calendars</a></p></body></html>");
            return;
        }

        // Generate SVG preview
        String svgContent = generateSvgForTemplate(calendar);

        CalendarProductView view = toProductView(calendar);

        String html =
                calendarsProduct
                        .data("calendar", view)
                        .data("svgContent", svgContent)
                        .data("configuration", calendar.getConfigurationJson())
                        .data("currentYear", java.time.Year.now().getValue())
                        .render();

        rc.response().putHeader(HEADER_CONTENT_TYPE, TEXT_HTML).end(html);
    }

    /** Serve SVG content for a calendar at /calendars/{slug}/{slug}.svg */
    @Route(path = "/:slug/:filename", methods = Route.HttpMethod.GET)
    @Blocking
    public void asset(RoutingContext rc) {
        String slug = rc.pathParam("slug");
        String filename = rc.pathParam("filename");

        // Handle SVG requests
        if (filename.endsWith(".svg")) {
            String expectedFilename = slug + ".svg";
            if (!filename.equals(expectedFilename)) {
                rc.response().setStatusCode(404).end(ASSET_NOT_FOUND);
                return;
            }

            CalendarTemplate calendar = CalendarTemplate.findBySlug(slug);
            if (calendar == null) {
                rc.response().setStatusCode(404).end("Calendar not found");
                return;
            }

            String svgContent = generateSvgForTemplate(calendar);

            rc.response()
                    .putHeader(HEADER_CONTENT_TYPE, "image/svg+xml")
                    .putHeader("Cache-Control", "public, max-age=3600")
                    .end(svgContent);
            return;
        }

        // Handle PNG requests - renders with white borders like print preview
        if (filename.endsWith(".png")) {
            String expectedFilename = slug + ".png";
            if (!filename.equals(expectedFilename)) {
                rc.response().setStatusCode(404).end(ASSET_NOT_FOUND);
                return;
            }

            CalendarTemplate calendar = CalendarTemplate.findBySlug(slug);
            if (calendar == null) {
                rc.response().setStatusCode(404).end("Calendar not found");
                return;
            }

            String svgContent = generateSvgForTemplate(calendar);

            // Wrap SVG with white background and margins to match print artboard
            String wrappedSvg = calendarRenderingService.wrapSvgForPreview(svgContent);

            try {
                // Generate PNG thumbnail (1200px width for good quality on index page)
                byte[] pngBytes = pdfRenderingService.renderSVGToPNG(wrappedSvg, 1200);

                rc.response()
                        .putHeader(HEADER_CONTENT_TYPE, "image/png")
                        .putHeader("Cache-Control", "public, max-age=3600")
                        .end(Buffer.buffer(pngBytes));
            } catch (Exception e) {
                LOG.errorf(e, "Failed to generate PNG for calendar: %s", slug);
                rc.response().setStatusCode(500).end("Failed to generate PNG");
            }
            return;
        }

        // Unknown asset type
        rc.response().setStatusCode(404).end(ASSET_NOT_FOUND);
    }

    /** Generate SVG from a calendar template using CalendarRenderingService. */
    private String generateSvgForTemplate(CalendarTemplate template) {
        int year = LocalDate.now().getYear() + 1;
        CalendarRenderingService.CalendarConfig config = buildConfigFromTemplate(template, year);
        return calendarRenderingService.generateCalendarSVG(config);
    }

    /** Build CalendarConfig from a template's JSON configuration. */
    private CalendarRenderingService.CalendarConfig buildConfigFromTemplate(
            CalendarTemplate template, int year) {
        CalendarRenderingService.CalendarConfig config =
                new CalendarRenderingService.CalendarConfig();
        config.year = year;

        if (template.configuration != null) {
            CalendarRenderingService.applyJsonConfiguration(config, template.configuration);
        }

        // Always use the target year, even if configuration has a different year
        config.year = year;
        return config;
    }

    private CalendarProductView toProductView(CalendarTemplate template) {
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.US);
        String priceFormatted =
                currencyFormat.format(
                        (template.priceCents != null ? template.priceCents : 2999) / 100.0);

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
                year);
    }

    /** View model for calendar product data in templates. */
    public record CalendarProductView(
            String templateId,
            String slug,
            String name,
            String title,
            String description,
            String ogDescription,
            String keywords,
            String priceFormatted,
            int year) {}

    /**
     * Serve the SPA index.html for Vue Router routes.
     * In production, serves from META-INF/resources/index.html.
     * In dev mode, fetches from the Vite dev server.
     */
    private void serveSpaIndex(RoutingContext rc) {
        // Try to read the SPA index.html from the classpath (production build)
        try (InputStream is = getClass().getResourceAsStream("/META-INF/resources/index.html")) {
            if (is != null) {
                String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                rc.response().putHeader(HEADER_CONTENT_TYPE, TEXT_HTML).end(html);
                return;
            }
        } catch (Exception e) {
            LOG.warn("Failed to read SPA index.html from classpath", e);
        }

        // In dev mode, fetch index.html from Vite dev server
        rc.vertx()
                .createHttpClient()
                .request(
                        io.vertx.core.http.HttpMethod.GET,
                        5176,
                        "localhost",
                        "/index.html")
                .compose(req -> req.send())
                .compose(resp -> resp.body())
                .onSuccess(
                        body -> {
                            rc.response()
                                    .putHeader(HEADER_CONTENT_TYPE, TEXT_HTML)
                                    .end(body);
                        })
                .onFailure(
                        err -> {
                            LOG.error("Failed to fetch SPA index.html from Vite dev server", err);
                            rc.response()
                                    .setStatusCode(503)
                                    .end("SPA not available. Is the Vite dev server running?");
                        });
    }
}
