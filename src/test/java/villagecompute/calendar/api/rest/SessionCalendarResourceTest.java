package villagecompute.calendar.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.models.UserCalendar;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SessionCalendarResource.
 * Tests session-based calendar management endpoints.
 */
@QuarkusTest
class SessionCalendarResourceTest {

    @Inject
    ObjectMapper objectMapper;

    private String testSessionId;
    private CalendarTemplate testTemplate;

    @BeforeEach
    void setUp() {
        testSessionId = "test-session-" + UUID.randomUUID();

        QuarkusTransaction.requiringNew().run(() -> {
            // Clean up test data
            UserCalendar.delete("sessionId like ?1", "test-session-%");
            CalendarTemplate.delete("slug", "test-session-template");

            // Create a test template
            testTemplate = new CalendarTemplate();
            testTemplate.name = "Test Session Template";
            testTemplate.slug = "test-session-template";
            testTemplate.description = "Template for session tests";
            testTemplate.isActive = true;
            testTemplate.priceCents = 2999;
            testTemplate.configuration = objectMapper.createObjectNode()
                .put("theme", "modern")
                .put("year", 2025);
            testTemplate.persist();
        });
    }

    // ============================================================================
    // GET /session-calendar/current TESTS
    // ============================================================================

    @Test
    void testGetCurrentCalendar_WithNoSession_ReturnsBadRequest() {
        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/session-calendar/current")
            .then()
            .statusCode(400)
            .body("error", equalTo("No session found"));
    }

    @Test
    void testGetCurrentCalendar_WithEmptySession_ReturnsBadRequest() {
        given()
            .accept(ContentType.JSON)
            .header("X-Session-ID", "")
            .when()
            .get("/api/session-calendar/current")
            .then()
            .statusCode(400)
            .body("error", equalTo("No session found"));
    }

    @Test
    void testGetCurrentCalendar_WithNoExistingCalendar_ReturnsNotFound() {
        given()
            .accept(ContentType.JSON)
            .header("X-Session-ID", testSessionId)
            .when()
            .get("/api/session-calendar/current")
            .then()
            .statusCode(404)
            .body("error", equalTo("No calendar for this session"));
    }

    @Test
    void testGetCurrentCalendar_WithExistingCalendar_ReturnsCalendar() {
        // Create a calendar for this session
        UUID calendarId = QuarkusTransaction.requiringNew().call(() -> {
            UserCalendar calendar = new UserCalendar();
            calendar.sessionId = testSessionId;
            calendar.name = "Test Current Calendar";
            calendar.year = 2025;
            calendar.isPublic = true;
            calendar.configuration = objectMapper.createObjectNode().put("theme", "classic");
            calendar.persist();
            return calendar.id;
        });

        given()
            .accept(ContentType.JSON)
            .header("X-Session-ID", testSessionId)
            .when()
            .get("/api/session-calendar/current")
            .then()
            .statusCode(200)
            .body("id", equalTo(calendarId.toString()))
            .body("name", equalTo("Test Current Calendar"))
            .body("sessionId", equalTo(testSessionId));
    }

    // ============================================================================
    // POST /session-calendar/save TESTS
    // ============================================================================

    @Test
    void testSaveSessionCalendar_WithNoSession_ReturnsBadRequest() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"name\": \"Test Calendar\"}")
            .when()
            .post("/api/session-calendar/save")
            .then()
            .statusCode(400)
            .body("error", equalTo("No session found"));
    }

    @Test
    void testSaveSessionCalendar_CreatesNewCalendar() {
        ObjectNode request = objectMapper.createObjectNode()
            .put("name", "My New Calendar");
        request.set("configuration", objectMapper.createObjectNode()
            .put("theme", "modern")
            .put("year", 2025));

        given()
            .header("X-Session-ID", testSessionId)
            .contentType(ContentType.JSON)
            .body(request.toString())
            .when()
            .post("/api/session-calendar/save")
            .then()
            .statusCode(200)
            .body("id", notNullValue())
            .body("sessionId", equalTo(testSessionId));

        // Verify calendar was created
        UserCalendar calendar = QuarkusTransaction.requiringNew().call(() ->
            UserCalendar.<UserCalendar>find("sessionId", testSessionId).firstResult()
        );
        assertNotNull(calendar);
        assertEquals("My New Calendar", calendar.name);
        assertEquals(2025, calendar.year);
    }

    @Test
    void testSaveSessionCalendar_UpdatesExistingCalendar() {
        // Create existing calendar
        QuarkusTransaction.requiringNew().run(() -> {
            UserCalendar calendar = new UserCalendar();
            calendar.sessionId = testSessionId;
            calendar.name = "Original Name";
            calendar.year = 2024;
            calendar.isPublic = true;
            calendar.persist();
        });

        ObjectNode request = objectMapper.createObjectNode()
            .put("name", "Updated Name");
        request.set("configuration", objectMapper.createObjectNode()
            .put("year", 2025));

        given()
            .header("X-Session-ID", testSessionId)
            .contentType(ContentType.JSON)
            .body(request.toString())
            .when()
            .post("/api/session-calendar/save")
            .then()
            .statusCode(200);

        // Verify calendar was updated
        UserCalendar calendar = QuarkusTransaction.requiringNew().call(() ->
            UserCalendar.<UserCalendar>find("sessionId", testSessionId).firstResult()
        );
        assertEquals("Updated Name", calendar.name);
        assertEquals(2025, calendar.year);
    }

    @Test
    void testSaveSessionCalendar_WithNullConfiguration_UsesEmptyObject() {
        ObjectNode request = objectMapper.createObjectNode()
            .put("name", "Calendar Without Config");
        // No configuration set

        given()
            .header("X-Session-ID", testSessionId)
            .contentType(ContentType.JSON)
            .body(request.toString())
            .when()
            .post("/api/session-calendar/save")
            .then()
            .statusCode(200);

        // Verify calendar was created with default configuration
        UserCalendar calendar = QuarkusTransaction.requiringNew().call(() ->
            UserCalendar.<UserCalendar>find("sessionId", testSessionId).firstResult()
        );
        assertNotNull(calendar.configuration);
    }

    @Test
    void testSaveSessionCalendar_WithDefaultName() {
        ObjectNode request = objectMapper.createObjectNode();
        request.set("configuration", objectMapper.createObjectNode().put("year", 2025));
        // No name set

        given()
            .header("X-Session-ID", testSessionId)
            .contentType(ContentType.JSON)
            .body(request.toString())
            .when()
            .post("/api/session-calendar/save")
            .then()
            .statusCode(200);

        // Verify calendar was created with default name
        UserCalendar calendar = QuarkusTransaction.requiringNew().call(() ->
            UserCalendar.<UserCalendar>find("sessionId", testSessionId).firstResult()
        );
        assertEquals("Untitled Calendar", calendar.name);
    }

    // ============================================================================
    // POST /session-calendar/from-template/{templateId} TESTS
    // ============================================================================

    @Test
    void testCreateFromTemplate_WithNoSession_ReturnsBadRequest() {
        given()
            .contentType(ContentType.JSON)
            .when()
            .post("/api/session-calendar/from-template/" + UUID.randomUUID())
            .then()
            .statusCode(400)
            .body("error", equalTo("No session found"));
    }

    @Test
    void testCreateFromTemplate_WithInvalidTemplateId_ReturnsNotFound() {
        given()
            .header("X-Session-ID", testSessionId)
            .contentType(ContentType.JSON)
            .when()
            .post("/api/session-calendar/from-template/" + UUID.randomUUID())
            .then()
            .statusCode(404)
            .body("error", equalTo("Template not found"));
    }

    @Test
    void testCreateFromTemplate_WithInactiveTemplate_ReturnsNotFound() {
        // Create inactive template with unique slug
        String uniqueSlug = "inactive-" + UUID.randomUUID().toString().substring(0, 8);
        UUID inactiveTemplateId = QuarkusTransaction.requiringNew().call(() -> {
            CalendarTemplate template = new CalendarTemplate();
            template.name = "Inactive Template";
            template.slug = uniqueSlug;
            template.isActive = false;
            template.configuration = objectMapper.createObjectNode(); // Required field
            template.persist();
            return template.id;
        });

        given()
            .header("X-Session-ID", testSessionId)
            .contentType(ContentType.JSON)
            .when()
            .post("/api/session-calendar/from-template/" + inactiveTemplateId)
            .then()
            .statusCode(404)
            .body("error", equalTo("Template not found"));
    }

    @Test
    void testCreateFromTemplate_CreatesCalendarFromTemplate() {
        UUID templateId = QuarkusTransaction.requiringNew().call(() -> testTemplate.id);

        given()
            .header("X-Session-ID", testSessionId)
            .contentType(ContentType.JSON)
            .when()
            .post("/api/session-calendar/from-template/" + templateId)
            .then()
            .statusCode(200)
            .body("id", notNullValue())
            .body("configuration", notNullValue());

        // Verify calendar was created with template data
        UserCalendar calendar = QuarkusTransaction.requiringNew().call(() ->
            UserCalendar.<UserCalendar>find("sessionId", testSessionId).firstResult()
        );
        assertNotNull(calendar);
        assertEquals("Test Session Template", calendar.name);
        assertEquals(2025, calendar.year);
        assertTrue(calendar.isPublic);
    }

    @Test
    void testCreateFromTemplate_WithTemplateWithoutYear_UsesCurrentYear() {
        // Create template without year in configuration
        UUID templateId = QuarkusTransaction.requiringNew().call(() -> {
            CalendarTemplate template = new CalendarTemplate();
            template.name = "No Year Template";
            template.slug = "no-year-template";
            template.isActive = true;
            template.configuration = objectMapper.createObjectNode().put("theme", "minimal");
            template.persist();
            return template.id;
        });

        given()
            .header("X-Session-ID", testSessionId)
            .contentType(ContentType.JSON)
            .when()
            .post("/api/session-calendar/from-template/" + templateId)
            .then()
            .statusCode(200);

        // Verify calendar uses current year
        UserCalendar calendar = QuarkusTransaction.requiringNew().call(() ->
            UserCalendar.<UserCalendar>find("sessionId", testSessionId).firstResult()
        );
        assertEquals(java.time.LocalDate.now().getYear(), calendar.year);
    }

    // ============================================================================
    // GET /session-calendar/{id} TESTS
    // ============================================================================

    @Test
    void testGetCalendar_WithNonexistentId_ReturnsNotFound() {
        given()
            .accept(ContentType.JSON)
            .header("X-Session-ID", testSessionId)
            .when()
            .get("/api/session-calendar/" + UUID.randomUUID())
            .then()
            .statusCode(404)
            .body("error", equalTo("Calendar not found"));
    }

    @Test
    void testGetCalendar_OwnPublicCalendar_ReturnsWithIsOwnCalendarTrue() {
        UUID calendarId = QuarkusTransaction.requiringNew().call(() -> {
            UserCalendar calendar = new UserCalendar();
            calendar.sessionId = testSessionId;
            calendar.name = "My Public Calendar";
            calendar.year = 2025;
            calendar.isPublic = true;
            calendar.persist();
            return calendar.id;
        });

        given()
            .accept(ContentType.JSON)
            .header("X-Session-ID", testSessionId)
            .when()
            .get("/api/session-calendar/" + calendarId)
            .then()
            .statusCode(200)
            .body("calendar.id", equalTo(calendarId.toString()))
            .body("calendar.name", equalTo("My Public Calendar"))
            .body("isOwnCalendar", equalTo(true));
    }

    @Test
    void testGetCalendar_OtherUserPublicCalendar_ReturnsWithIsOwnCalendarFalse() {
        // Create calendar for another session
        UUID calendarId = QuarkusTransaction.requiringNew().call(() -> {
            UserCalendar calendar = new UserCalendar();
            calendar.sessionId = "other-session-id";
            calendar.name = "Other User Public Calendar";
            calendar.year = 2025;
            calendar.isPublic = true;
            calendar.persist();
            return calendar.id;
        });

        given()
            .accept(ContentType.JSON)
            .header("X-Session-ID", testSessionId)
            .when()
            .get("/api/session-calendar/" + calendarId)
            .then()
            .statusCode(200)
            .body("calendar.id", equalTo(calendarId.toString()))
            .body("isOwnCalendar", equalTo(false));
    }

    @Test
    void testGetCalendar_OtherUserPrivateCalendar_ReturnsForbidden() {
        // Create private calendar for another session
        UUID calendarId = QuarkusTransaction.requiringNew().call(() -> {
            UserCalendar calendar = new UserCalendar();
            calendar.sessionId = "other-session-id";
            calendar.name = "Other User Private Calendar";
            calendar.year = 2025;
            calendar.isPublic = false;
            calendar.persist();
            return calendar.id;
        });

        given()
            .accept(ContentType.JSON)
            .header("X-Session-ID", testSessionId)
            .when()
            .get("/api/session-calendar/" + calendarId)
            .then()
            .statusCode(403)
            .body("error", equalTo("Calendar is not public"));
    }

    @Test
    void testGetCalendar_OwnPrivateCalendar_ReturnsSuccessfully() {
        // Create private calendar for own session
        UUID calendarId = QuarkusTransaction.requiringNew().call(() -> {
            UserCalendar calendar = new UserCalendar();
            calendar.sessionId = testSessionId;
            calendar.name = "My Private Calendar";
            calendar.year = 2025;
            calendar.isPublic = false;
            calendar.persist();
            return calendar.id;
        });

        given()
            .accept(ContentType.JSON)
            .header("X-Session-ID", testSessionId)
            .when()
            .get("/api/session-calendar/" + calendarId)
            .then()
            .statusCode(200)
            .body("calendar.id", equalTo(calendarId.toString()))
            .body("isOwnCalendar", equalTo(true));
    }

    @Test
    void testGetCalendar_WithNoSessionHeader_CanViewPublicCalendar() {
        UUID calendarId = QuarkusTransaction.requiringNew().call(() -> {
            UserCalendar calendar = new UserCalendar();
            calendar.sessionId = "any-session";
            calendar.name = "Public Calendar";
            calendar.year = 2025;
            calendar.isPublic = true;
            calendar.persist();
            return calendar.id;
        });

        given()
            .accept(ContentType.JSON)
            .when()
            .get("/api/session-calendar/" + calendarId)
            .then()
            .statusCode(200)
            .body("isOwnCalendar", equalTo(false));
    }

    // ============================================================================
    // PUT /session-calendar/{id}/autosave TESTS
    // ============================================================================

    @Test
    void testAutosaveCalendar_WithNoSession_ReturnsBadRequest() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"name\": \"Test\"}")
            .when()
            .put("/api/session-calendar/" + UUID.randomUUID() + "/autosave")
            .then()
            .statusCode(400)
            .body("error", equalTo("No session found"));
    }

    @Test
    void testAutosaveCalendar_WithNonexistentCalendar_ReturnsNotFound() {
        given()
            .header("X-Session-ID", testSessionId)
            .contentType(ContentType.JSON)
            .body("{\"name\": \"Test\"}")
            .when()
            .put("/api/session-calendar/" + UUID.randomUUID() + "/autosave")
            .then()
            .statusCode(404)
            .body("error", equalTo("Calendar not found"));
    }

    @Test
    void testAutosaveCalendar_WithOtherSessionCalendar_ReturnsForbidden() {
        // Create calendar for another session
        UUID calendarId = QuarkusTransaction.requiringNew().call(() -> {
            UserCalendar calendar = new UserCalendar();
            calendar.sessionId = "other-session-id";
            calendar.name = "Other Calendar";
            calendar.year = 2025;
            calendar.isPublic = true;
            calendar.persist();
            return calendar.id;
        });

        given()
            .header("X-Session-ID", testSessionId)
            .contentType(ContentType.JSON)
            .body("{\"name\": \"Hacked\"}")
            .when()
            .put("/api/session-calendar/" + calendarId + "/autosave")
            .then()
            .statusCode(403)
            .body("error", equalTo("Cannot edit this calendar"));
    }

    @Test
    void testAutosaveCalendar_UpdatesConfiguration() {
        UUID calendarId = QuarkusTransaction.requiringNew().call(() -> {
            UserCalendar calendar = new UserCalendar();
            calendar.sessionId = testSessionId;
            calendar.name = "Original Name";
            calendar.year = 2024;
            calendar.isPublic = true;
            calendar.configuration = objectMapper.createObjectNode().put("theme", "old");
            calendar.persist();
            return calendar.id;
        });

        ObjectNode request = objectMapper.createObjectNode()
            .put("name", "Updated Name");
        request.set("configuration", objectMapper.createObjectNode()
            .put("theme", "new")
            .put("year", 2025));

        given()
            .header("X-Session-ID", testSessionId)
            .contentType(ContentType.JSON)
            .body(request.toString())
            .when()
            .put("/api/session-calendar/" + calendarId + "/autosave")
            .then()
            .statusCode(200)
            .body("success", equalTo(true))
            .body("id", equalTo(calendarId.toString()))
            .body("svg", notNullValue());

        // Verify updates
        UserCalendar calendar = QuarkusTransaction.requiringNew().call(() ->
            UserCalendar.findById(calendarId)
        );
        assertEquals("Updated Name", calendar.name);
        assertEquals(2025, calendar.year);
        assertEquals("new", calendar.configuration.get("theme").asText());
    }

    @Test
    void testAutosaveCalendar_WithNullSessionId_InCalendar_ReturnsForbidden() {
        // Create calendar with null sessionId
        UUID calendarId = QuarkusTransaction.requiringNew().call(() -> {
            UserCalendar calendar = new UserCalendar();
            calendar.sessionId = null;
            calendar.name = "Orphaned Calendar";
            calendar.year = 2025;
            calendar.isPublic = true;
            calendar.persist();
            return calendar.id;
        });

        given()
            .header("X-Session-ID", testSessionId)
            .contentType(ContentType.JSON)
            .body("{\"name\": \"Test\"}")
            .when()
            .put("/api/session-calendar/" + calendarId + "/autosave")
            .then()
            .statusCode(403)
            .body("error", equalTo("Cannot edit this calendar"));
    }

    // ============================================================================
    // POST /session-calendar/new TESTS
    // ============================================================================

    @Test
    void testCreateNewCalendar_WithNoSession_ReturnsBadRequest() {
        given()
            .contentType(ContentType.JSON)
            .when()
            .post("/api/session-calendar/new")
            .then()
            .statusCode(400)
            .body("error", equalTo("No session found"));
    }

    @Test
    void testCreateNewCalendar_CreatesCalendarWithDefaults() {
        given()
            .header("X-Session-ID", testSessionId)
            .contentType(ContentType.JSON)
            .when()
            .post("/api/session-calendar/new")
            .then()
            .statusCode(200)
            .body("id", notNullValue())
            .body("configuration", notNullValue())
            .body("svg", notNullValue())
            .body("existing", equalTo(false));

        // Verify calendar was created
        UserCalendar calendar = QuarkusTransaction.requiringNew().call(() ->
            UserCalendar.<UserCalendar>find("sessionId", testSessionId).firstResult()
        );
        assertNotNull(calendar);
        assertTrue(calendar.isPublic);
        assertNotNull(calendar.configuration);
    }

    @Test
    void testCreateNewCalendar_ReturnsExistingCalendarIfPresent() {
        // Create existing calendar
        UUID existingId = QuarkusTransaction.requiringNew().call(() -> {
            UserCalendar calendar = new UserCalendar();
            calendar.sessionId = testSessionId;
            calendar.name = "Existing Calendar";
            calendar.year = 2025;
            calendar.isPublic = true;
            calendar.generatedSvg = "<svg>existing</svg>";
            calendar.configuration = objectMapper.createObjectNode().put("theme", "existing");
            calendar.persist();
            return calendar.id;
        });

        given()
            .header("X-Session-ID", testSessionId)
            .contentType(ContentType.JSON)
            .when()
            .post("/api/session-calendar/new")
            .then()
            .statusCode(200)
            .body("id", equalTo(existingId.toString()))
            .body("existing", equalTo(true))
            .body("svg", equalTo("<svg>existing</svg>"));
    }

    @Test
    void testCreateNewCalendar_UsesDefaultTemplateIfAvailable() {
        // Create default template
        QuarkusTransaction.requiringNew().run(() -> {
            CalendarTemplate.delete("slug", "default");
            CalendarTemplate defaultTemplate = new CalendarTemplate();
            defaultTemplate.name = "Default Template";
            defaultTemplate.slug = "default";
            defaultTemplate.isActive = true;
            defaultTemplate.configuration = objectMapper.createObjectNode()
                .put("theme", "from-default-template")
                .put("year", 2026);
            defaultTemplate.persist();
        });

        given()
            .header("X-Session-ID", testSessionId)
            .contentType(ContentType.JSON)
            .when()
            .post("/api/session-calendar/new")
            .then()
            .statusCode(200)
            .body("existing", equalTo(false));

        // Verify calendar uses default template
        UserCalendar calendar = QuarkusTransaction.requiringNew().call(() ->
            UserCalendar.<UserCalendar>find("sessionId", testSessionId).firstResult()
        );
        assertEquals("Default Template", calendar.name);
        assertEquals("from-default-template", calendar.configuration.get("theme").asText());
    }

    @Test
    void testCreateNewCalendar_FallsBackToHardcodedDefaultsIfNoTemplate() {
        // Ensure no default template exists
        QuarkusTransaction.requiringNew().run(() -> {
            CalendarTemplate.delete("slug", "default");
        });

        given()
            .header("X-Session-ID", testSessionId)
            .contentType(ContentType.JSON)
            .when()
            .post("/api/session-calendar/new")
            .then()
            .statusCode(200)
            .body("existing", equalTo(false));

        // Verify calendar uses hardcoded defaults
        UserCalendar calendar = QuarkusTransaction.requiringNew().call(() ->
            UserCalendar.<UserCalendar>find("sessionId", testSessionId).firstResult()
        );
        assertEquals("Default Calendar", calendar.name);
        assertNotNull(calendar.configuration);
    }

    @Test
    void testCreateNewCalendar_WithInactiveDefaultTemplate_UsesHardcodedDefaults() {
        // Ensure no active default template exists, create inactive one
        QuarkusTransaction.requiringNew().run(() -> {
            // Delete any existing default template first
            CalendarTemplate existing = CalendarTemplate.<CalendarTemplate>find("slug", "default").firstResult();
            if (existing != null) {
                existing.delete();
            }

            CalendarTemplate defaultTemplate = new CalendarTemplate();
            defaultTemplate.name = "Inactive Default";
            defaultTemplate.slug = "default";
            defaultTemplate.isActive = false;
            defaultTemplate.configuration = objectMapper.createObjectNode(); // Required field
            defaultTemplate.persist();
        });

        given()
            .header("X-Session-ID", testSessionId)
            .contentType(ContentType.JSON)
            .when()
            .post("/api/session-calendar/new")
            .then()
            .statusCode(200);

        // Verify calendar uses hardcoded defaults (not inactive template)
        UserCalendar calendar = QuarkusTransaction.requiringNew().call(() ->
            UserCalendar.<UserCalendar>find("sessionId", testSessionId).firstResult()
        );
        assertEquals("Default Calendar", calendar.name);
    }

    // ============================================================================
    // DTO TESTS
    // ============================================================================

    @Test
    void testSessionCalendarDTO_IncludesAllFields() {
        UUID calendarId = QuarkusTransaction.requiringNew().call(() -> {
            CalendarTemplate template = CalendarTemplate.<CalendarTemplate>find("slug", "test-session-template").firstResult();

            UserCalendar calendar = new UserCalendar();
            calendar.sessionId = testSessionId;
            calendar.name = "Full Calendar";
            calendar.year = 2025;
            calendar.isPublic = true;
            calendar.template = template;
            calendar.configuration = objectMapper.createObjectNode().put("theme", "complete");
            calendar.persist();
            return calendar.id;
        });

        given()
            .accept(ContentType.JSON)
            .header("X-Session-ID", testSessionId)
            .when()
            .get("/api/session-calendar/" + calendarId)
            .then()
            .statusCode(200)
            .body("calendar.id", equalTo(calendarId.toString()))
            .body("calendar.name", equalTo("Full Calendar"))
            .body("calendar.sessionId", equalTo(testSessionId))
            .body("calendar.isPublic", equalTo(true))
            .body("calendar.templateId", notNullValue())
            .body("calendar.configuration.theme", equalTo("complete"));
    }

    @Test
    void testSessionCalendarDTO_WithNullTemplate_ReturnsNullTemplateId() {
        UUID calendarId = QuarkusTransaction.requiringNew().call(() -> {
            UserCalendar calendar = new UserCalendar();
            calendar.sessionId = testSessionId;
            calendar.name = "No Template Calendar";
            calendar.year = 2025;
            calendar.isPublic = true;
            calendar.template = null;
            calendar.persist();
            return calendar.id;
        });

        given()
            .accept(ContentType.JSON)
            .header("X-Session-ID", testSessionId)
            .when()
            .get("/api/session-calendar/" + calendarId)
            .then()
            .statusCode(200)
            .body("calendar.templateId", nullValue());
    }
}
