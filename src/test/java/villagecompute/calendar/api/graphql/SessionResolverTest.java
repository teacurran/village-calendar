package villagecompute.calendar.api.graphql;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import villagecompute.calendar.data.models.CalendarTemplate;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Tests for SessionResolver GraphQL endpoints. These endpoints handle guest session operations.
 */
@QuarkusTest
class SessionResolverTest {

    @Inject
    ObjectMapper objectMapper;

    private String testSessionId;
    private UUID testTemplateId;

    @BeforeEach
    void setUp() {
        testSessionId = "test-session-" + UUID.randomUUID();

        // Create test template
        testTemplateId = QuarkusTransaction.requiringNew().call(() -> {
            CalendarTemplate template = new CalendarTemplate();
            template.name = "Test Template";
            template.slug = "test-template-" + System.currentTimeMillis();
            template.isActive = true;
            template.priceCents = 2999;
            template.configuration = objectMapper.createObjectNode().put("theme", "modern");
            template.persist();
            return template.id;
        });
    }

    // ========== sessionCalendars Query Tests ==========

    @Test
    void testSessionCalendars_EmptySession_ReturnsEmptyList() {
        String query = """
                query {
                    sessionCalendars(sessionId: "%s") {
                        id
                        name
                        year
                    }
                }
                """.formatted(testSessionId);

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("data.sessionCalendars", hasSize(0)).body("errors", nullValue());
    }

    @Test
    void testSessionCalendars_WithCalendars_ReturnsList() {
        // First create a calendar for the session
        String createMutation = """
                mutation {
                    createSessionCalendar(
                        sessionId: "%s",
                        name: "Test Calendar",
                        year: 2025,
                        templateId: "%s"
                    ) {
                        id
                        name
                    }
                }
                """.formatted(testSessionId, testTemplateId);

        given().contentType(ContentType.JSON).body(Map.of("query", createMutation)).when().post("/graphql").then()
                .statusCode(200).body("data.createSessionCalendar.id", notNullValue());

        // Now query session calendars
        String query = """
                query {
                    sessionCalendars(sessionId: "%s") {
                        id
                        name
                        year
                    }
                }
                """.formatted(testSessionId);

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("data.sessionCalendars", hasSize(1))
                .body("data.sessionCalendars[0].name", equalTo("Test Calendar"))
                .body("data.sessionCalendars[0].year", equalTo(2025));
    }

    // ========== hasSessionCalendars Query Tests ==========

    @Test
    void testHasSessionCalendars_EmptySession_ReturnsFalse() {
        String query = """
                query {
                    hasSessionCalendars(sessionId: "%s")
                }
                """.formatted(testSessionId);

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("data.hasSessionCalendars", equalTo(false));
    }

    @Test
    void testHasSessionCalendars_WithCalendars_ReturnsTrue() {
        // First create a calendar for the session
        String createMutation = """
                mutation {
                    createSessionCalendar(
                        sessionId: "%s",
                        name: "Test Calendar",
                        year: 2025,
                        templateId: "%s"
                    ) {
                        id
                    }
                }
                """.formatted(testSessionId, testTemplateId);

        given().contentType(ContentType.JSON).body(Map.of("query", createMutation)).when().post("/graphql").then()
                .statusCode(200);

        // Now check hasSessionCalendars
        String query = """
                query {
                    hasSessionCalendars(sessionId: "%s")
                }
                """.formatted(testSessionId);

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("data.hasSessionCalendars", equalTo(true));
    }

    // ========== createSessionCalendar Mutation Tests ==========

    @Test
    void testCreateSessionCalendar_ValidInput_CreatesCalendar() {
        String mutation = """
                mutation {
                    createSessionCalendar(
                        sessionId: "%s",
                        name: "My 2025 Calendar",
                        year: 2025,
                        templateId: "%s"
                    ) {
                        id
                        name
                        year
                        isPublic
                    }
                }
                """.formatted(testSessionId, testTemplateId);

        given().contentType(ContentType.JSON).body(Map.of("query", mutation)).when().post("/graphql").then()
                .statusCode(200).body("data.createSessionCalendar.id", notNullValue())
                .body("data.createSessionCalendar.name", equalTo("My 2025 Calendar"))
                .body("data.createSessionCalendar.year", equalTo(2025))
                .body("data.createSessionCalendar.isPublic", equalTo(false));
    }

    @Test
    void testCreateSessionCalendar_BlankSessionId_ReturnsError() {
        String mutation = """
                mutation {
                    createSessionCalendar(
                        sessionId: "   ",
                        name: "Test Calendar",
                        year: 2025,
                        templateId: "%s"
                    ) {
                        id
                    }
                }
                """.formatted(testTemplateId);

        given().contentType(ContentType.JSON).body(Map.of("query", mutation)).when().post("/graphql").then()
                .statusCode(200).body("errors", hasSize(greaterThan(0)));
    }

    @Test
    void testCreateSessionCalendar_InvalidTemplateId_ReturnsError() {
        String mutation = """
                mutation {
                    createSessionCalendar(
                        sessionId: "%s",
                        name: "Test Calendar",
                        year: 2025,
                        templateId: "not-a-uuid"
                    ) {
                        id
                    }
                }
                """.formatted(testSessionId);

        given().contentType(ContentType.JSON).body(Map.of("query", mutation)).when().post("/graphql").then()
                .statusCode(200).body("errors", hasSize(greaterThan(0)));
    }

    @Test
    void testCreateSessionCalendar_NonExistentTemplate_ReturnsError() {
        UUID nonExistentTemplate = UUID.randomUUID();
        String mutation = """
                mutation {
                    createSessionCalendar(
                        sessionId: "%s",
                        name: "Test Calendar",
                        year: 2025,
                        templateId: "%s"
                    ) {
                        id
                    }
                }
                """.formatted(testSessionId, nonExistentTemplate);

        given().contentType(ContentType.JSON).body(Map.of("query", mutation)).when().post("/graphql").then()
                .statusCode(200).body("errors", hasSize(greaterThan(0)));
    }

    @Test
    void testCreateSessionCalendar_MultipleCalendars_AllCreated() {
        // Create first calendar
        String mutation1 = """
                mutation {
                    createSessionCalendar(
                        sessionId: "%s",
                        name: "Calendar One",
                        year: 2025,
                        templateId: "%s"
                    ) {
                        id
                    }
                }
                """.formatted(testSessionId, testTemplateId);

        given().contentType(ContentType.JSON).body(Map.of("query", mutation1)).when().post("/graphql").then()
                .statusCode(200);

        // Create second calendar
        String mutation2 = """
                mutation {
                    createSessionCalendar(
                        sessionId: "%s",
                        name: "Calendar Two",
                        year: 2026,
                        templateId: "%s"
                    ) {
                        id
                    }
                }
                """.formatted(testSessionId, testTemplateId);

        given().contentType(ContentType.JSON).body(Map.of("query", mutation2)).when().post("/graphql").then()
                .statusCode(200);

        // Verify both calendars exist
        String query = """
                query {
                    sessionCalendars(sessionId: "%s") {
                        id
                        name
                    }
                }
                """.formatted(testSessionId);

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("data.sessionCalendars", hasSize(2));
    }

    // ========== convertGuestSession Mutation Tests ==========

    @Test
    void testConvertGuestSession_NoAuth_ReturnsUnauthorized() {
        String mutation = """
                mutation {
                    convertGuestSession(sessionId: "%s")
                }
                """.formatted(testSessionId);

        given().contentType(ContentType.JSON).body(Map.of("query", mutation)).when().post("/graphql").then()
                .statusCode(200).body("errors", hasSize(greaterThan(0)));
    }

    @Test
    void testConvertGuestSession_InvalidToken_ReturnsError() {
        String mutation = """
                mutation {
                    convertGuestSession(sessionId: "%s")
                }
                """.formatted(testSessionId);

        // Invalid token returns 401 Unauthorized
        given().contentType(ContentType.JSON).header("Authorization", "Bearer invalid-token")
                .body(Map.of("query", mutation)).when().post("/graphql").then().statusCode(401);
    }
}
