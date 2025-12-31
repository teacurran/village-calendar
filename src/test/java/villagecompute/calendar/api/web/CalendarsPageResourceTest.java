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
}
