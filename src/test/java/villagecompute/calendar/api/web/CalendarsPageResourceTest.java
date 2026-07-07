package villagecompute.calendar.api.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static villagecompute.calendar.util.MimeTypes.HEADER_CACHE_CONTROL;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import villagecompute.calendar.data.models.CalendarTemplate;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;

/** Tests for CalendarsPageResource. Tests the calendar product pages at /calendars/ */
@QuarkusTest
class CalendarsPageResourceTest {

    @Inject
    ObjectMapper objectMapper;

    private CalendarTemplate testTemplate;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up existing templates with our test slug
        CalendarTemplate.delete("slug", "test-calendar-template");

        // Create a test template with a slug
        testTemplate = new CalendarTemplate();
        testTemplate.name = "Test Calendar Template";
        testTemplate.slug = "test-calendar-template";
        testTemplate.description = "A test calendar for unit tests";
        testTemplate.ogDescription = "OG description for testing";
        testTemplate.metaKeywords = "test, calendar";
        testTemplate.isActive = true;
        testTemplate.isFeatured = false;
        testTemplate.displayOrder = 1;
        testTemplate.priceCents = 2999;
        testTemplate.configuration = objectMapper.createObjectNode().put("theme", "modern");
        testTemplate.persist();
    }

    // ============================================================================
    // REDIRECT TESTS
    // ============================================================================

    @Test
    void testIndexRedirect_ReturnsRedirectToTrailingSlash() {
        // When/Then: GET /calendars redirects to /calendars/
        given().redirects().follow(false).when().get("/calendars").then().statusCode(301).header("Location",
                equalTo("/calendars/"));
    }

    // ============================================================================
    // INDEX PAGE TESTS
    // ============================================================================

    @Test
    void testIndex_ReturnsHtmlWithCalendarList() {
        // When/Then: GET /calendars/ returns HTML with calendar list
        given().when().get("/calendars/").then().statusCode(200).contentType(containsString("text/html"))
                .body(containsString("Pre-Designed Calendars"));
    }

    @Test
    void testIndex_IncludesActiveCalendarsWithSlugs() {
        // When/Then: The test template should appear in the list
        given().when().get("/calendars/").then().statusCode(200).body(containsString("Test Calendar Template"));
    }

    // ============================================================================
    // PRODUCT PAGE TESTS
    // ============================================================================

    @Test
    void testProduct_ReturnsProductPageForValidSlug() {
        // When/Then: GET /calendars/test-calendar-template returns product page
        given().when().get("/calendars/test-calendar-template").then().statusCode(200)
                .contentType(containsString("text/html")).body(containsString("Test Calendar Template"));
    }

    @Test
    void testProduct_Returns404ForInvalidSlug() {
        // When/Then: GET /calendars/nonexistent returns 404
        given().when().get("/calendars/nonexistent-calendar-slug").then().statusCode(404)
                .body(containsString("Calendar Not Found"));
    }

    @Test
    void testProduct_PassesThroughRequestsWithFileExtensions() {
        // When/Then: GET /calendars/something.css should pass through (not match product route)
        // This will likely 404 from static resource handling, but shouldn't hit our product
        // endpoint
        given().when().get("/calendars/something.css").then().statusCode(404);
    }

    // ============================================================================
    // ASSET TESTS (SVG)
    // ============================================================================

    @Test
    void testAsset_ReturnsSvgForValidSlug() {
        // When/Then: GET /calendars/test-calendar-template/test-calendar-template.svg returns SVG
        given().when().get("/calendars/test-calendar-template/test-calendar-template.svg").then().statusCode(200)
                .contentType(containsString("image/svg+xml"))
                .header(HEADER_CACHE_CONTROL, containsString("max-age=3600")).body(containsString("<svg"));
    }

    @Test
    void testAsset_Returns404ForMismatchedSvgFilename() {
        // When/Then: GET /calendars/test-calendar-template/wrong-name.svg returns 404
        given().when().get("/calendars/test-calendar-template/wrong-name.svg").then().statusCode(404)
                .body(containsString("Asset not found"));
    }

    @Test
    void testAsset_Returns404ForSvgWithNonexistentSlug() {
        // When/Then: GET /calendars/nonexistent/nonexistent.svg returns 404
        given().when().get("/calendars/nonexistent/nonexistent.svg").then().statusCode(404)
                .body(containsString("Calendar not found"));
    }

    // ============================================================================
    // ASSET TESTS (PNG)
    // ============================================================================

    @Test
    void testAsset_ReturnsPngForValidSlug() {
        // When/Then: GET /calendars/test-calendar-template/test-calendar-template.png returns PNG
        given().when().get("/calendars/test-calendar-template/test-calendar-template.png").then().statusCode(200)
                .contentType(containsString("image/png")).header(HEADER_CACHE_CONTROL, containsString("max-age=3600"));
    }

    @Test
    void testAsset_Returns404ForMismatchedPngFilename() {
        // When/Then: GET /calendars/test-calendar-template/wrong-name.png returns 404
        given().when().get("/calendars/test-calendar-template/wrong-name.png").then().statusCode(404)
                .body(containsString("Asset not found"));
    }

    @Test
    void testAsset_Returns404ForPngWithNonexistentSlug() {
        // When/Then: GET /calendars/nonexistent/nonexistent.png returns 404
        given().when().get("/calendars/nonexistent/nonexistent.png").then().statusCode(404)
                .body(containsString("Calendar not found"));
    }

    // ============================================================================
    // ASSET TESTS (Unknown Types)
    // ============================================================================

    @Test
    void testAsset_Returns404ForUnknownAssetType() {
        // When/Then: GET /calendars/test-calendar-template/test-calendar-template.pdf returns 404
        given().when().get("/calendars/test-calendar-template/test-calendar-template.pdf").then().statusCode(404)
                .body(containsString("Asset not found"));
    }

    @Test
    void testAsset_Returns404ForFilenameWithoutExtension() {
        // When/Then: GET /calendars/test-calendar-template/somefile returns 404
        given().when().get("/calendars/test-calendar-template/somefile").then().statusCode(404)
                .body(containsString("Asset not found"));
    }

    // ============================================================================
    // SPA ROUTE TESTS
    // ============================================================================
    // The SPA index.html is not present on the classpath in test runs, so the
    // fallback path attempts to reach the Vite dev server. In CI/test it
    // returns 503 (dev server not running) or 200 if it has been started.

    @Test
    void testProduct_GeneratorSpaRoute_DoesNotReturn404() {
        // When/Then: GET /calendars/generator hits the SPA serve path, not the
        // CalendarTemplate 404 page. We accept any non-404 status because the
        // dev server presence varies.
        given().when().get("/calendars/generator").then().statusCode(anyOf(is(200), is(503)));
    }

    @Test
    void testProduct_NewSpaRoute_DoesNotReturn404() {
        // When/Then: GET /calendars/new hits the SPA serve path
        given().when().get("/calendars/new").then().statusCode(anyOf(is(200), is(503)));
    }

    @Test
    void testProduct_EditSpaRoute_DoesNotReturn404() {
        // When/Then: GET /calendars/edit hits the SPA serve path
        given().when().get("/calendars/edit").then().statusCode(anyOf(is(200), is(503)));
    }

    // ============================================================================
    // PRODUCT PAGE - VIEW MODEL VARIANTS
    // ============================================================================

    @Test
    void testProduct_UsesYearFromConfiguration() {
        // Given: a template with year set in its configuration. Use a separate
        // committed transaction so the HTTP call below sees the row.
        QuarkusTransaction.requiringNew().run(() -> {
            CalendarTemplate.delete("slug", "test-calendar-year");
            CalendarTemplate yearTemplate = new CalendarTemplate();
            yearTemplate.name = "Year Configured Template";
            yearTemplate.slug = "test-calendar-year";
            yearTemplate.description = "Calendar with explicit year";
            yearTemplate.isActive = true;
            yearTemplate.isFeatured = false;
            yearTemplate.displayOrder = 2;
            yearTemplate.priceCents = 3999;
            yearTemplate.configuration = objectMapper.createObjectNode().put("theme", "vintage").put("year", 2042);
            yearTemplate.persist();
        });

        try {
            // When/Then: the rendered page reflects the configuration year
            given().when().get("/calendars/test-calendar-year").then().statusCode(200)
                    .contentType(containsString("text/html")).body(containsString("Year Configured Template"))
                    .body(containsString("2042"));
        } finally {
            QuarkusTransaction.requiringNew().run(() -> CalendarTemplate.delete("slug", "test-calendar-year"));
        }
    }

    @Test
    void testProduct_HandlesNullPriceCents() {
        // Given: a template with null priceCents (covers the default-fallback branch)
        QuarkusTransaction.requiringNew().run(() -> {
            CalendarTemplate.delete("slug", "test-calendar-null-price");
            CalendarTemplate priceTemplate = new CalendarTemplate();
            priceTemplate.name = "Null Price Template";
            priceTemplate.slug = "test-calendar-null-price";
            priceTemplate.description = "Calendar with null price";
            priceTemplate.isActive = true;
            priceTemplate.isFeatured = false;
            priceTemplate.displayOrder = 3;
            priceTemplate.priceCents = null;
            priceTemplate.configuration = objectMapper.createObjectNode().put("theme", "classic");
            priceTemplate.persist();
        });

        try {
            // When/Then: the page still renders (defaults to $29.99)
            given().when().get("/calendars/test-calendar-null-price").then().statusCode(200)
                    .contentType(containsString("text/html")).body(containsString("Null Price Template"))
                    .body(containsString("29.99"));
        } finally {
            QuarkusTransaction.requiringNew().run(() -> CalendarTemplate.delete("slug", "test-calendar-null-price"));
        }
    }

    @Test
    void testProduct_HandlesInvalidConfigurationGracefully() {
        // Given: a template whose configuration cannot deserialize to CalendarConfigType
        // (the "year" field is a string instead of an int)
        QuarkusTransaction.requiringNew().run(() -> {
            CalendarTemplate.delete("slug", "test-calendar-bad-config");
            CalendarTemplate badTemplate = new CalendarTemplate();
            badTemplate.name = "Bad Config Template";
            badTemplate.slug = "test-calendar-bad-config";
            badTemplate.description = "Calendar with bad configuration";
            badTemplate.isActive = true;
            badTemplate.isFeatured = false;
            badTemplate.displayOrder = 4;
            badTemplate.priceCents = 1999;
            badTemplate.configuration = objectMapper.createObjectNode().put("year", "not-a-number");
            badTemplate.persist();
        });

        try {
            // When/Then: the resource catches the parse exception and still serves the page
            given().when().get("/calendars/test-calendar-bad-config").then().statusCode(200)
                    .contentType(containsString("text/html")).body(containsString("Bad Config Template"));
        } finally {
            QuarkusTransaction.requiringNew().run(() -> CalendarTemplate.delete("slug", "test-calendar-bad-config"));
        }
    }
}
