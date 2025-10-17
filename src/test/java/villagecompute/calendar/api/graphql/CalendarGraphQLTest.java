package villagecompute.calendar.api.graphql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.services.AuthenticationService;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for GraphQL API endpoints.
 * Tests queries, mutations, authorization, and error handling.
 *
 * NOTE: These tests focus on public queries and unauthorized access scenarios.
 * Full authentication testing requires JWT token generation which is out of scope
 * for this iteration.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CalendarGraphQLTest {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AuthenticationService authService;

    private CalendarUser testUser;
    private CalendarTemplate testTemplate;
    private static final String TEST_EMAIL = "graphql-test@example.com";

    @BeforeEach
    @Transactional
    void setupTestData() {
        // Create test user (let Hibernate generate ID)
        testUser = new CalendarUser();
        testUser.oauthProvider = "GOOGLE";
        testUser.oauthSubject = "graphql-test-subject-" + System.currentTimeMillis();
        testUser.email = TEST_EMAIL;
        testUser.displayName = "GraphQL Test User";
        testUser.persist();

        // Create test template (let Hibernate generate ID)
        testTemplate = new CalendarTemplate();
        testTemplate.name = "GraphQL Test Template " + System.currentTimeMillis();
        testTemplate.description = "Test template for GraphQL tests";
        testTemplate.isActive = true;
        testTemplate.isFeatured = false;
        testTemplate.displayOrder = 1;
        testTemplate.configuration = createTestConfiguration();
        testTemplate.persist();
    }

    @AfterEach
    @Transactional
    void cleanupTestData() {
        // Clean up test data
        if (testTemplate != null && testTemplate.id != null) {
            CalendarTemplate.deleteById(testTemplate.id);
        }
        if (testUser != null && testUser.id != null) {
            CalendarUser.deleteById(testUser.id);
        }
    }

    private static JsonNode createTestConfiguration() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readTree("{\"theme\": \"modern\", \"colorScheme\": \"blue\"}");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ============================================================================
    // QUERY TESTS - PUBLIC QUERIES
    // ============================================================================

    @Test
    @Order(1)
    void testQuery_Templates_Public() {
        // Test: Public query for templates should work without authentication
        String query = """
            query {
                templates(isActive: true) {
                    id
                    name
                    description
                    isActive
                    isFeatured
                }
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("query", query))
            .when()
            .post("/graphql")
            .then()
            .statusCode(200)
            .body("data.templates", notNullValue())
            .body("data.templates", hasSize(greaterThanOrEqualTo(1)))
            .body("data.templates[0].name", notNullValue())
            .body("errors", nullValue());
    }

    @Test
    @Order(2)
    void testQuery_Template_ById() {
        // Test: Get single template by ID
        String query = String.format("""
            query {
                template(id: "%s") {
                    id
                    name
                    description
                    isActive
                }
            }
            """, testTemplate.id.toString());

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("query", query))
            .when()
            .post("/graphql")
            .then()
            .statusCode(200)
            .body("data.template.id", equalTo(testTemplate.id.toString()))
            .body("data.template.name", equalTo(testTemplate.name))
            .body("errors", nullValue());
    }

    @Test
    @Order(3)
    void testQuery_Me_Unauthenticated() {
        // Test: `me` query without authentication should return null
        String query = """
            query {
                me {
                    id
                    email
                    displayName
                }
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("query", query))
            .when()
            .post("/graphql")
            .then()
            .statusCode(200)
            .body("data.me", nullValue())
            .body("errors", nullValue());
    }

    @Test
    @Order(4)
    void testQuery_MyCalendars_Unauthenticated() {
        // Test: `myCalendars` without authentication should return error
        String query = """
            query {
                myCalendars {
                    id
                    name
                }
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("query", query))
            .when()
            .post("/graphql")
            .then()
            .statusCode(200)
            .body("errors", notNullValue());
    }

    // ============================================================================
    // MUTATION TESTS - UNAUTHORIZED ACCESS
    // ============================================================================

    @Test
    @Order(10)
    void testMutation_CreateCalendar_Unauthenticated() {
        // Test: Create calendar without authentication should fail
        String mutation = String.format("""
            mutation {
                createCalendar(input: {
                    name: "Unauthorized Calendar"
                    year: 2025
                    templateId: "%s"
                }) {
                    id
                }
            }
            """, testTemplate.id.toString());

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("query", mutation))
            .when()
            .post("/graphql")
            .then()
            .statusCode(200)
            .body("errors", notNullValue());
    }

    @Test
    @Order(11)
    void testMutation_UpdateCalendar_Unauthenticated() {
        // Test: Update calendar without authentication should fail
        String mutation = String.format("""
            mutation {
                updateCalendar(
                    id: "%s"
                    input: { name: "Unauthorized Update" }
                ) {
                    id
                }
            }
            """, UUID.randomUUID().toString());

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("query", mutation))
            .when()
            .post("/graphql")
            .then()
            .statusCode(200)
            .body("errors", notNullValue());
    }

    @Test
    @Order(12)
    void testMutation_DeleteCalendar_Unauthenticated() {
        // Test: Delete calendar without authentication should fail
        String mutation = String.format("""
            mutation {
                deleteCalendar(id: "%s")
            }
            """, UUID.randomUUID().toString());

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("query", mutation))
            .when()
            .post("/graphql")
            .then()
            .statusCode(200)
            .body("errors", notNullValue());
    }

    // ============================================================================
    // VALIDATION TESTS
    // ============================================================================

    @Test
    @Order(20)
    void testValidation_Template_InvalidId() {
        // Test: Query with invalid UUID should return null or error
        String query = """
            query {
                template(id: "invalid-uuid") {
                    id
                }
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("query", query))
            .when()
            .post("/graphql")
            .then()
            .statusCode(200)
            .body("errors", notNullValue());
    }

    @Test
    @Order(21)
    void testValidation_Template_NotFound() {
        // Test: Query with valid but non-existent UUID should return null
        String query = String.format("""
            query {
                template(id: "%s") {
                    id
                }
            }
            """, "00000000-0000-0000-0000-000000000099");

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("query", query))
            .when()
            .post("/graphql")
            .then()
            .statusCode(200)
            .body("data.template", nullValue())
            .body("errors", nullValue()); // No error, just null result
    }

    /**
     * Test the GraphQL schema introspection.
     * This verifies that the GraphQL endpoint is properly configured.
     */
    @Test
    @Order(30)
    void testGraphQL_SchemaIntrospection() {
        String query = """
            query {
                __schema {
                    queryType {
                        name
                    }
                    mutationType {
                        name
                    }
                }
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("query", query))
            .when()
            .post("/graphql")
            .then()
            .statusCode(200)
            .body("data.__schema.queryType.name", equalTo("Query"))
            .body("data.__schema.mutationType.name", equalTo("Mutation"))
            .body("errors", nullValue());
    }

    /**
     * Test that GraphQL endpoint correctly exposes the templates query.
     */
    @Test
    @Order(31)
    void testGraphQL_TemplatesQueryExists() {
        String query = """
            query {
                __type(name: "Query") {
                    name
                    fields {
                        name
                    }
                }
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("query", query))
            .when()
            .post("/graphql")
            .then()
            .statusCode(200)
            .body("data.__type.name", equalTo("Query"))
            .body("data.__type.fields.name", hasItems("templates", "template", "me", "myCalendars"))
            .body("errors", nullValue());
    }

    // ============================================================================
    // PUBLIC CALENDAR TESTS
    // ============================================================================

    @Test
    @Order(40)
    void testQuery_PublicCalendar() {
        // Create a public calendar for testing (in separate transaction to commit immediately)
        UUID calendarId = createAndPersistPublicCalendar();

        try {
            // Test: Public calendar should be accessible without authentication
            String query = String.format("""
                query {
                    calendar(id: "%s") {
                        id
                        name
                        year
                        isPublic
                    }
                }
                """, calendarId.toString());

            given()
                .contentType(ContentType.JSON)
                .body(Map.of("query", query))
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.calendar.id", equalTo(calendarId.toString()))
                .body("data.calendar.name", equalTo("Public Test Calendar"))
                .body("data.calendar.year", equalTo(2025))
                .body("data.calendar.isPublic", equalTo(true))
                .body("errors", nullValue());
        } finally {
            deleteTestCalendar(calendarId);
        }
    }

    @Transactional
    UUID createAndPersistPublicCalendar() {
        UserCalendar publicCalendar = new UserCalendar();
        publicCalendar.user = testUser;
        publicCalendar.name = "Public Test Calendar";
        publicCalendar.year = 2025;
        publicCalendar.template = testTemplate;
        publicCalendar.isPublic = true;
        publicCalendar.configuration = createTestConfiguration();
        publicCalendar.persist();
        return publicCalendar.id;
    }

    @Transactional
    void deleteTestCalendar(final UUID calendarId) {
        UserCalendar.deleteById(calendarId);
    }

    @Transactional
    UUID createAndPersistPublicCalendar(final String name) {
        UserCalendar publicCalendar = new UserCalendar();
        publicCalendar.user = testUser;
        publicCalendar.name = name;
        publicCalendar.year = 2025;
        publicCalendar.template = testTemplate;
        publicCalendar.isPublic = true;
        publicCalendar.configuration = createTestConfiguration();
        publicCalendar.persist();
        return publicCalendar.id;
    }

    @Test
    @Order(41)
    @Transactional
    void testQuery_PrivateCalendar_Unauthenticated() {
        // Create a private calendar for testing
        UserCalendar privateCalendar = new UserCalendar();
        privateCalendar.user = testUser;
        privateCalendar.name = "Private Test Calendar";
        privateCalendar.year = 2025;
        privateCalendar.template = testTemplate;
        privateCalendar.isPublic = false;
        privateCalendar.configuration = createTestConfiguration();
        privateCalendar.persist();

        try {
            // Test: Private calendar should NOT be accessible without authentication
            String query = String.format("""
                query {
                    calendar(id: "%s") {
                        id
                        name
                    }
                }
                """, privateCalendar.id.toString());

            given()
                .contentType(ContentType.JSON)
                .body(Map.of("query", query))
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.calendar", nullValue()) // Should return null for unauthorized access
                .body("errors", nullValue()); // No error, just null result
        } finally {
            privateCalendar.delete();
        }
    }

    @Test
    @Order(42)
    void testQuery_Calendar_NotFound() {
        // Test: Non-existent calendar should return null
        String query = String.format("""
            query {
                calendar(id: "%s") {
                    id
                    name
                }
            }
            """, "00000000-0000-0000-0000-000000000099");

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("query", query))
            .when()
            .post("/graphql")
            .then()
            .statusCode(200)
            .body("data.calendar", nullValue())
            .body("errors", nullValue());
    }

    @Test
    @Order(43)
    void testQuery_Calendar_InvalidId() {
        // Test: Invalid UUID format should return error
        String query = """
            query {
                calendar(id: "not-a-uuid") {
                    id
                    name
                }
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("query", query))
            .when()
            .post("/graphql")
            .then()
            .statusCode(200)
            .body("errors", notNullValue());
    }

    // ============================================================================
    // FIELD RESOLVER TESTS
    // ============================================================================

    @Test
    @Order(50)
    void testFieldResolver_CalendarWithEvents() {
        // Create a public calendar with events (in separate transaction)
        UUID calendarId = createAndPersistCalendarWithEvents();

        try {
            // Test: Query calendar with events field
            // Note: This tests the events field exposed via JPA relationship
            String query = String.format("""
                query {
                    calendar(id: "%s") {
                        id
                        name
                        events {
                            id
                            eventDate
                            eventText
                        }
                    }
                }
                """, calendarId.toString());

            given()
                .contentType(ContentType.JSON)
                .body(Map.of("query", query))
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.calendar.id", equalTo(calendarId.toString()))
                .body("data.calendar.events", notNullValue())
                .body("errors", nullValue());
        } finally {
            deleteTestCalendar(calendarId);
        }
    }

    @Transactional
    UUID createAndPersistCalendarWithEvents() {
        UserCalendar calendar = new UserCalendar();
        calendar.user = testUser;
        calendar.name = "Calendar with Events";
        calendar.year = 2025;
        calendar.template = testTemplate;
        calendar.isPublic = true;
        calendar.configuration = createTestConfiguration();
        calendar.persist();
        return calendar.id;
    }

    // ============================================================================
    // VALIDATION EDGE CASES
    // ============================================================================

    @Test
    @Order(60)
    void testMutation_CreateCalendar_InvalidTemplateId() {
        // Test: Creating calendar with non-existent template should fail
        String mutation = String.format("""
            mutation {
                createCalendar(input: {
                    name: "Test Calendar"
                    year: 2025
                    templateId: "%s"
                }) {
                    id
                }
            }
            """, "00000000-0000-0000-0000-000000000099");

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("query", mutation))
            .when()
            .post("/graphql")
            .then()
            .statusCode(200)
            .body("errors", notNullValue()); // Should fail due to authentication
    }

    @Test
    @Order(61)
    void testMutation_CreateCalendar_InvalidTemplateIdFormat() {
        // Test: Creating calendar with invalid UUID format should fail
        String mutation = """
            mutation {
                createCalendar(input: {
                    name: "Test Calendar"
                    year: 2025
                    templateId: "not-a-uuid"
                }) {
                    id
                }
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("query", mutation))
            .when()
            .post("/graphql")
            .then()
            .statusCode(200)
            .body("errors", notNullValue());
    }

    // ==========================================================================
    // DATALOADER INTEGRATION TESTS
    // ==========================================================================

    /**
     * Test that DataLoader pattern prevents N+1 queries when fetching
     * multiple calendars with related entities.
     *
     * Acceptance Criteria: Fetching 10 calendars with users and templates
     * should result in 3 DB queries (1 for calendars, 1 for users,
     * 1 for templates), not 21 queries (1 + 10 + 10).
     *
     * NOTE: This test verifies the DataLoader field resolvers are working.
     * SQL query counting would require additional test infrastructure
     * (hibernate statistics or database proxy), so we verify functional
     * correctness here.
     */
    @Test
    @Order(70)
    void testDataLoader_BatchLoading_MultipleCalendars() {
        // Create 10 public calendars for testing
        java.util.List<UUID> calendarIds = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            UUID id = createAndPersistPublicCalendar("Calendar " + i);
            calendarIds.add(id);
        }

        try {
            // Query all 10 calendars with their users and templates
            // This should trigger DataLoader batching
            String query = String.format("""
                query {
                    calendar1: calendar(id: "%s") {
                        id
                        name
                        user { id email }
                        template { id name }
                    }
                    calendar2: calendar(id: "%s") {
                        id
                        name
                        user { id email }
                        template { id name }
                    }
                    calendar3: calendar(id: "%s") {
                        id
                        name
                        user { id email }
                        template { id name }
                    }
                    calendar4: calendar(id: "%s") {
                        id
                        name
                        user { id email }
                        template { id name }
                    }
                    calendar5: calendar(id: "%s") {
                        id
                        name
                        user { id email }
                        template { id name }
                    }
                    calendar6: calendar(id: "%s") {
                        id
                        name
                        user { id email }
                        template { id name }
                    }
                    calendar7: calendar(id: "%s") {
                        id
                        name
                        user { id email }
                        template { id name }
                    }
                    calendar8: calendar(id: "%s") {
                        id
                        name
                        user { id email }
                        template { id name }
                    }
                    calendar9: calendar(id: "%s") {
                        id
                        name
                        user { id email }
                        template { id name }
                    }
                    calendar10: calendar(id: "%s") {
                        id
                        name
                        user { id email }
                        template { id name }
                    }
                }
                """,
                calendarIds.get(0).toString(),
                calendarIds.get(1).toString(),
                calendarIds.get(2).toString(),
                calendarIds.get(3).toString(),
                calendarIds.get(4).toString(),
                calendarIds.get(5).toString(),
                calendarIds.get(6).toString(),
                calendarIds.get(7).toString(),
                calendarIds.get(8).toString(),
                calendarIds.get(9).toString()
            );

            // Execute query and verify all calendars returned successfully
            // If DataLoader is working, this should complete without errors
            given()
                .contentType(ContentType.JSON)
                .body(Map.of("query", query))
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.calendar1.id",
                    equalTo(calendarIds.get(0).toString()))
                .body("data.calendar1.user.email", equalTo(TEST_EMAIL))
                .body("data.calendar1.template.name",
                    equalTo(testTemplate.name))
                .body("data.calendar10.id",
                    equalTo(calendarIds.get(9).toString()))
                .body("data.calendar10.user.email", equalTo(TEST_EMAIL))
                .body("data.calendar10.template.name",
                    equalTo(testTemplate.name))
                .body("errors", nullValue());

        } finally {
            // Clean up test data
            for (UUID calendarId : calendarIds) {
                deleteTestCalendar(calendarId);
            }
        }
    }

    /**
     * Test that field resolvers correctly resolve nested relationships.
     * Verifies that user and template fields are accessible on calendar.
     */
    @Test
    @Order(71)
    void testDataLoader_FieldResolvers_CalendarRelationships() {
        UUID calendarId = createAndPersistPublicCalendar(
            "Test Calendar for Field Resolvers");

        try {
            // Query calendar with nested user and template fields
            String query = String.format("""
                query {
                    calendar(id: "%s") {
                        id
                        name
                        year
                        user {
                            id
                            email
                            displayName
                            oauthProvider
                        }
                        template {
                            id
                            name
                            description
                            isActive
                            isFeatured
                        }
                    }
                }
                """, calendarId.toString());

            given()
                .contentType(ContentType.JSON)
                .body(Map.of("query", query))
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.calendar.id", equalTo(calendarId.toString()))
                .body("data.calendar.user.id",
                    equalTo(testUser.id.toString()))
                .body("data.calendar.user.email", equalTo(TEST_EMAIL))
                .body("data.calendar.user.displayName",
                    equalTo("GraphQL Test User"))
                .body("data.calendar.template.id",
                    equalTo(testTemplate.id.toString()))
                .body("data.calendar.template.name",
                    equalTo(testTemplate.name))
                .body("data.calendar.template.isActive", equalTo(true))
                .body("errors", nullValue());

        } finally {
            deleteTestCalendar(calendarId);
        }
    }
}
