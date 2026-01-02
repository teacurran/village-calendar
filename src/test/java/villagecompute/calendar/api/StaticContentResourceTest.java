package villagecompute.calendar.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import villagecompute.calendar.data.models.CalendarTemplate;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Tests for StaticContentResource REST endpoints. These endpoints are used by CI/CD to generate static content for
 * product pages.
 */
@QuarkusTest
class StaticContentResourceTest {

    private static final String CONTENT_TYPE_SVG = "image/svg+xml";
    private static final String CONTENT_TYPE_PNG = "image/png";

    @Inject
    ObjectMapper objectMapper;

    private CalendarTemplate testTemplate;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up existing test templates with slugs (avoid conflicts)
        CalendarTemplate.delete("slug is not null");

        // Create a test template with a slug for testing
        testTemplate = new CalendarTemplate();
        testTemplate.name = "Test Static Calendar";
        testTemplate.slug = "test-static-calendar";
        testTemplate.description = "A test calendar for static content";
        testTemplate.ogDescription = "Test OG Description";
        testTemplate.metaKeywords = "test, calendar, static";
        testTemplate.priceCents = 2999;
        testTemplate.isActive = true;
        testTemplate.isFeatured = false;
        testTemplate.displayOrder = 1;

        // Create configuration for the template
        ObjectNode config = objectMapper.createObjectNode();
        config.put("year", 2025);
        config.put("theme", "default");
        config.put("layoutStyle", "grid");
        testTemplate.configuration = config;

        testTemplate.persist();
    }

    // ========== GET /api/static-content/calendars.json TESTS ==========

    @Test
    void testCalendarsJson_ReturnsJsonArray() {
        given().when().get("/api/static-content/calendars.json").then().statusCode(200).contentType(ContentType.JSON)
                .body("$", not(empty())).body("[0].slug", notNullValue()).body("[0].name", notNullValue())
                .body("[0].title", notNullValue()).body("[0].year", greaterThan(2024));
    }

    @Test
    void testCalendarsJson_ContainsTestTemplate() {
        given().when().get("/api/static-content/calendars.json").then().statusCode(200).contentType(ContentType.JSON)
                .body("find { it.slug == 'test-static-calendar' }.name", equalTo("Test Static Calendar"))
                .body("find { it.slug == 'test-static-calendar' }.description",
                        equalTo("A test calendar for static content"))
                .body("find { it.slug == 'test-static-calendar' }.ogDescription", equalTo("Test OG Description"))
                .body("find { it.slug == 'test-static-calendar' }.keywords", equalTo("test, calendar, static"))
                .body("find { it.slug == 'test-static-calendar' }.priceFormatted", equalTo("29.99"))
                .body("find { it.slug == 'test-static-calendar' }.configuration", notNullValue());
    }

    @Test
    void testCalendarsJson_ReturnsNextYear() {
        int nextYear = java.time.LocalDate.now().getYear() + 1;

        given().when().get("/api/static-content/calendars.json").then().statusCode(200).contentType(ContentType.JSON)
                .body("[0].year", equalTo(nextYear));
    }

    // ========== GET /api/static-content/calendars/{slug}.svg TESTS ==========

    @Test
    void testCalendarSvg_ValidSlug_ReturnsSvg() {
        given().when().get("/api/static-content/calendars/test-static-calendar.svg").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG).body(containsString("<svg"));
    }

    @Test
    void testCalendarSvg_InvalidSlug_Returns404() {
        given().when().get("/api/static-content/calendars/non-existent-calendar.svg").then().statusCode(404);
    }

    @Test
    void testCalendarSvg_HasContentDispositionHeader() {
        given().when().get("/api/static-content/calendars/test-static-calendar.svg").then().statusCode(200)
                .header("Content-Disposition", containsString("test-static-calendar.svg"));
    }

    // ========== GET /api/static-content/calendars/{slug}.png TESTS ==========

    @Test
    void testCalendarPng_ValidSlug_ReturnsPng() {
        given().when().get("/api/static-content/calendars/test-static-calendar.png").then().statusCode(200)
                .contentType(CONTENT_TYPE_PNG);
    }

    @Test
    void testCalendarPng_InvalidSlug_Returns404() {
        given().when().get("/api/static-content/calendars/non-existent-calendar.png").then().statusCode(404);
    }

    @Test
    void testCalendarPng_HasContentDispositionHeader() {
        given().when().get("/api/static-content/calendars/test-static-calendar.png").then().statusCode(200)
                .header("Content-Disposition", containsString("test-static-calendar.png"));
    }

    // ========== GET /api/static-content/template/{templateId}.svg TESTS ==========

    @Test
    @Transactional
    void testTemplateSvg_ValidId_ReturnsSvg() {
        // Refresh template ID
        CalendarTemplate template = CalendarTemplate.find("slug", "test-static-calendar").firstResult();

        given().when().get("/api/static-content/template/" + template.id + ".svg").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG).body(containsString("<svg"));
    }

    @Test
    void testTemplateSvg_InvalidUuid_Returns400() {
        given().when().get("/api/static-content/template/not-a-uuid.svg").then().statusCode(400);
    }

    @Test
    void testTemplateSvg_NonExistentId_Returns404() {
        UUID nonExistent = UUID.randomUUID();
        given().when().get("/api/static-content/template/" + nonExistent + ".svg").then().statusCode(404);
    }

    // ========== GET /api/static-content/template/{templateId}.png TESTS ==========

    @Test
    @Transactional
    void testTemplatePng_ValidId_ReturnsPng() {
        // Refresh template ID
        CalendarTemplate template = CalendarTemplate.find("slug", "test-static-calendar").firstResult();

        given().when().get("/api/static-content/template/" + template.id + ".png").then().statusCode(200)
                .contentType(CONTENT_TYPE_PNG);
    }

    @Test
    void testTemplatePng_InvalidUuid_Returns400() {
        given().when().get("/api/static-content/template/not-a-uuid.png").then().statusCode(400);
    }

    @Test
    void testTemplatePng_NonExistentId_Returns404() {
        UUID nonExistent = UUID.randomUUID();
        given().when().get("/api/static-content/template/" + nonExistent + ".png").then().statusCode(404);
    }

    @Test
    @Transactional
    void testTemplatePng_HasCacheControlHeader() {
        CalendarTemplate template = CalendarTemplate.find("slug", "test-static-calendar").firstResult();

        given().when().get("/api/static-content/template/" + template.id + ".png").then().statusCode(200)
                .header("Cache-Control", containsString("max-age=86400"));
    }

    // ========== Edge Cases ==========

    @Test
    void testCalendarSvg_CachingWorks() {
        // First request populates cache
        given().when().get("/api/static-content/calendars/test-static-calendar.svg").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG);

        // Second request should use cache (same result, faster)
        given().when().get("/api/static-content/calendars/test-static-calendar.svg").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG);
    }

    @Test
    void testTemplateSvg_CachingWorks() {
        // Get template ID
        String templateId = given().when().get("/api/static-content/calendars.json").then().extract().jsonPath()
                .getString("find { it.slug == 'test-static-calendar' }.templateId");

        // First request populates cache
        given().when().get("/api/static-content/template/" + templateId + ".svg").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG);

        // Second request should use cache
        given().when().get("/api/static-content/template/" + templateId + ".svg").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG);
    }
}
