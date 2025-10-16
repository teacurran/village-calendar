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
}
