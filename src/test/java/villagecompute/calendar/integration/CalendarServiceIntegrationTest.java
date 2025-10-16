package villagecompute.calendar.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.services.CalendarGenerationService;
import villagecompute.calendar.services.StorageService;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration tests for calendar service end-to-end workflow.
 * Tests calendar generation, template queries, and service integration.
 *
 * This test suite validates:
 * 1. Public template queries (no authentication required)
 * 2. Calendar generation service (SVG + PDF + R2 upload)
 * 3. Service layer integration (CalendarGenerationService, StorageService)
 *
 * Note: Tests that require authentication are handled separately or require
 * Docker Compose manual testing. This suite focuses on core business logic
 * that can be tested without full JWT authentication flow.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CalendarServiceIntegrationTest {

    @Inject
    CalendarGenerationService calendarGenerationService;

    @Inject
    ObjectMapper objectMapper;

    @InjectMock
    StorageService storageService;

    private static final String TEST_PROVIDER = "GOOGLE";

    private CalendarUser testUser;
    private CalendarTemplate testTemplate;

    @BeforeEach
    @Transactional
    void setup() {
        // Create test user
        testUser = new CalendarUser();
        testUser.oauthProvider = TEST_PROVIDER;
        testUser.oauthSubject = "calendar-int-test-" + System.currentTimeMillis();
        testUser.email = "calendar-integration@example.com-" + System.currentTimeMillis();
        testUser.displayName = "Calendar Integration Test User";
        testUser.persist();

        // Create test template
        testTemplate = new CalendarTemplate();
        testTemplate.name = "Integration Test Template " + System.currentTimeMillis();
        testTemplate.description = "Template for integration testing";
        testTemplate.isActive = true;
        testTemplate.isFeatured = false;
        testTemplate.displayOrder = 1;
        testTemplate.configuration = createTestConfiguration();
        testTemplate.persist();

        // Mock storage service to avoid requiring real R2 credentials
        String mockPublicUrl = "https://r2.example.com/calendars/test-calendar.pdf";
        when(storageService.uploadFile(anyString(), any(byte[].class), anyString()))
            .thenReturn(mockPublicUrl);
    }

    @AfterEach
    @Transactional
    void cleanup() {
        // Clean up test data - delete in correct order due to foreign keys
        if (testUser != null && testUser.id != null) {
            // Delete user calendars first
            UserCalendar.delete("user.id", testUser.id);
            // Then delete user
            CalendarUser.deleteById(testUser.id);
        }

        if (testTemplate != null && testTemplate.id != null) {
            CalendarTemplate.deleteById(testTemplate.id);
        }
    }

    private JsonNode createTestConfiguration() {
        try {
            return objectMapper.readTree("""
                {
                    "theme": "modern",
                    "colorScheme": "blue",
                    "showMoonPhases": true,
                    "showMoonIllumination": false,
                    "showWeekNumbers": true,
                    "compactMode": false
                }
                """);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ============================================================================
    // TEMPLATE QUERY TESTS (Public - No Authentication Required)
    // ============================================================================

    @Test
    @Order(1)
    void testTemplateQuery_ReturnsActiveTemplates() {
        // When: Query for active templates
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

        // Then: Should return templates including our test template
        Response response = given()
            .contentType(ContentType.JSON)
            .body(Map.of("query", query))
            .when()
            .post("/graphql")
            .then()
            .statusCode(200)
            .body("data.templates", notNullValue())
            .body("data.templates", hasSize(greaterThanOrEqualTo(1)))
            .body("errors", nullValue())
            .extract()
            .response();

        String responseBody = response.asString();
        assertTrue(responseBody.contains(testTemplate.name), "Should include test template");
    }

    @Test
    @Order(2)
    void testTemplateQuery_ById() {
        // When: Query for specific template by ID
        String query = String.format("""
            query {
                template(id: "%s") {
                    id
                    name
                    description
                    isActive
                    configuration
                }
            }
            """, testTemplate.id.toString());

        // Then: Should return the specific template
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("query", query))
            .when()
            .post("/graphql")
            .then()
            .statusCode(200)
            .body("data.template.id", equalTo(testTemplate.id.toString()))
            .body("data.template.name", equalTo(testTemplate.name))
            .body("data.template.isActive", equalTo(true))
            .body("data.template.configuration", notNullValue())
            .body("errors", nullValue());
    }

    @Test
    @Order(3)
    void testTemplateQuery_InvalidId() {
        // When: Query with invalid UUID
        String query = """
            query {
                template(id: "invalid-uuid") {
                    id
                }
            }
            """;

        // Then: Should return error
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
    // CALENDAR GENERATION SERVICE TESTS
    // ============================================================================

    @Test
    @Order(10)
    @Transactional
    void testCalendarGeneration_EndToEnd() {
        // Given: A user calendar
        UserCalendar calendar = new UserCalendar();
        calendar.user = testUser;
        calendar.name = "Generated Calendar";
        calendar.year = 2025;
        calendar.template = testTemplate;
        calendar.configuration = createTestConfiguration();
        calendar.persist();

        // When: Generate calendar PDF
        String publicUrl = calendarGenerationService.generateCalendar(calendar);

        // Then: Calendar should be generated successfully
        assertNotNull(publicUrl, "Public URL should be returned");
        assertFalse(publicUrl.isEmpty(), "Public URL should not be empty");

        // Verify generatedSvg is populated
        assertNotNull(calendar.generatedSvg, "Generated SVG should be stored");
        assertTrue(calendar.generatedSvg.contains("<svg"), "Generated SVG should be valid SVG");

        // Verify generatedPdfUrl is set
        assertEquals(publicUrl, calendar.generatedPdfUrl, "Generated PDF URL should match returned URL");

        // Verify storage service was called
        Mockito.verify(storageService, Mockito.times(1))
            .uploadFile(anyString(), any(byte[].class), anyString());
    }

    @Test
    @Order(11)
    @Transactional
    void testCalendarGeneration_ValidatesSVGOutput() {
        // Given: A user calendar
        UserCalendar calendar = new UserCalendar();
        calendar.user = testUser;
        calendar.name = "SVG Validation Test";
        calendar.year = 2025;
        calendar.template = testTemplate;
        calendar.persist();

        // When: Generate calendar
        String publicUrl = calendarGenerationService.generateCalendar(calendar);

        // Then: SVG should be generated correctly
        assertNotNull(publicUrl, "Public URL should be generated");
        assertNotNull(calendar.generatedSvg, "SVG should be generated");
        assertTrue(calendar.generatedSvg.length() > 0, "SVG should not be empty");

        // Verify SVG contains expected elements
        assertTrue(calendar.generatedSvg.contains("2025"), "SVG should contain year");
        assertTrue(calendar.generatedSvg.contains("<svg"), "SVG should have svg tag");
        assertTrue(calendar.generatedSvg.contains("</svg>"), "SVG should have closing svg tag");
    }

    @Test
    @Order(12)
    @Transactional
    void testCalendarGeneration_WithCustomConfiguration() {
        // Given: A calendar with custom configuration
        UserCalendar calendar = new UserCalendar();
        calendar.user = testUser;
        calendar.name = "Custom Config Calendar";
        calendar.year = 2025;
        calendar.template = testTemplate;

        // Custom configuration with specific options
        try {
            calendar.configuration = objectMapper.readTree("""
                {
                    "theme": "dark",
                    "showMoonPhases": true,
                    "showWeekNumbers": false,
                    "compactMode": true,
                    "moonSize": 20
                }
                """);
        } catch (Exception e) {
            fail("Failed to create test configuration: " + e.getMessage());
        }

        calendar.persist();

        // When: Generate calendar
        String publicUrl = calendarGenerationService.generateCalendar(calendar);

        // Then: Calendar should be generated with custom settings
        assertNotNull(publicUrl, "Public URL should be generated");
        assertNotNull(calendar.generatedSvg, "SVG should be generated");
        assertNotNull(calendar.generatedPdfUrl, "PDF URL should be set");
    }

    @Test
    @Order(13)
    @Transactional
    void testCalendarGeneration_WithoutTemplate() {
        // Given: A calendar without template (should use default settings)
        UserCalendar calendar = new UserCalendar();
        calendar.user = testUser;
        calendar.name = "No Template Calendar";
        calendar.year = 2025;
        calendar.template = null; // No template
        calendar.persist();

        // When: Generate calendar
        String publicUrl = calendarGenerationService.generateCalendar(calendar);

        // Then: Calendar should be generated with default settings
        assertNotNull(publicUrl, "Public URL should be generated");
        assertNotNull(calendar.generatedSvg, "SVG should be generated");
        assertTrue(calendar.generatedSvg.contains("2025"), "SVG should contain year");
    }

    @Test
    @Order(14)
    @Transactional
    void testCalendarGeneration_MultipleCalendars() {
        // Given: Multiple calendars for the same user
        UserCalendar calendar1 = new UserCalendar();
        calendar1.user = testUser;
        calendar1.name = "Calendar 2025";
        calendar1.year = 2025;
        calendar1.template = testTemplate;
        calendar1.persist();

        UserCalendar calendar2 = new UserCalendar();
        calendar2.user = testUser;
        calendar2.name = "Calendar 2026";
        calendar2.year = 2026;
        calendar2.template = testTemplate;
        calendar2.persist();

        // When: Generate both calendars
        String url1 = calendarGenerationService.generateCalendar(calendar1);
        String url2 = calendarGenerationService.generateCalendar(calendar2);

        // Then: Both should be generated successfully
        assertNotNull(url1, "First calendar URL should be generated");
        assertNotNull(url2, "Second calendar URL should be generated");
        // Note: URLs will be the same due to mocked StorageService returning fixed URL
        // In real implementation, URLs would be unique based on filename

        assertTrue(calendar1.generatedSvg.contains("2025"), "First calendar should be for 2025");
        assertTrue(calendar2.generatedSvg.contains("2026"), "Second calendar should be for 2026");
    }

    // ============================================================================
    // DATABASE INTEGRATION TESTS
    // ============================================================================

    @Test
    @Order(20)
    @Transactional
    void testUserCalendar_Persistence() {
        // Given: A new calendar
        UserCalendar calendar = new UserCalendar();
        calendar.user = testUser;
        calendar.name = "Persistence Test";
        calendar.year = 2025;
        calendar.template = testTemplate;
        calendar.isPublic = true;
        calendar.persist();

        UUID calendarId = calendar.id;
        assertNotNull(calendarId, "Calendar ID should be assigned");

        // When: Retrieve calendar from database
        Optional<UserCalendar> retrievedCalendar = UserCalendar.findByIdOptional(calendarId);

        // Then: Calendar should be persisted correctly
        assertTrue(retrievedCalendar.isPresent(), "Calendar should be retrievable");
        assertEquals("Persistence Test", retrievedCalendar.get().name);
        assertEquals(2025, retrievedCalendar.get().year);
        assertTrue(retrievedCalendar.get().isPublic);
        assertEquals(testUser.id, retrievedCalendar.get().user.id);
        assertEquals(testTemplate.id, retrievedCalendar.get().template.id);
    }

    @Test
    @Order(21)
    @Transactional
    void testUserCalendar_FindByUserAndYear() {
        // Given: Multiple calendars for a user
        UserCalendar calendar2025a = new UserCalendar();
        calendar2025a.user = testUser;
        calendar2025a.name = "Calendar 2025 A";
        calendar2025a.year = 2025;
        calendar2025a.template = testTemplate;
        calendar2025a.persist();

        UserCalendar calendar2025b = new UserCalendar();
        calendar2025b.user = testUser;
        calendar2025b.name = "Calendar 2025 B";
        calendar2025b.year = 2025;
        calendar2025b.template = testTemplate;
        calendar2025b.persist();

        UserCalendar calendar2026 = new UserCalendar();
        calendar2026.user = testUser;
        calendar2026.name = "Calendar 2026";
        calendar2026.year = 2026;
        calendar2026.template = testTemplate;
        calendar2026.persist();

        // When: Query by user and year
        var calendars2025 = UserCalendar.findByUserAndYear(testUser.id, 2025);
        var calendars2026 = UserCalendar.findByUserAndYear(testUser.id, 2026);

        // Then: Should return correct calendars
        assertEquals(2, calendars2025.size(), "Should find 2 calendars for 2025");
        assertEquals(1, calendars2026.size(), "Should find 1 calendar for 2026");
    }

    @Test
    @Order(22)
    @Transactional
    void testUserCalendar_ConfigurationJsonb() {
        // Given: A calendar with JSONB configuration
        UserCalendar calendar = new UserCalendar();
        calendar.user = testUser;
        calendar.name = "JSONB Test";
        calendar.year = 2025;
        calendar.template = testTemplate;

        JsonNode config = createTestConfiguration();
        calendar.configuration = config;
        calendar.persist();

        UUID calendarId = calendar.id;

        // When: Retrieve and verify configuration
        Optional<UserCalendar> retrieved = UserCalendar.findByIdOptional(calendarId);

        // Then: JSONB configuration should be persisted correctly
        assertTrue(retrieved.isPresent());
        assertNotNull(retrieved.get().configuration);
        assertEquals("modern", retrieved.get().configuration.get("theme").asText());
        assertTrue(retrieved.get().configuration.get("showMoonPhases").asBoolean());
    }

    // ============================================================================
    // ERROR HANDLING TESTS
    // ============================================================================

    @Test
    @Order(30)
    void testCalendarGeneration_NullCalendar() {
        // When/Then: Should throw exception for null calendar
        assertThrows(IllegalArgumentException.class, () -> {
            calendarGenerationService.generateCalendar(null);
        }, "Should throw exception for null calendar");
    }

    @Test
    @Order(31)
    @Transactional
    void testCalendarGeneration_NullYear() {
        // Given: Calendar with null year
        UserCalendar calendar = new UserCalendar();
        calendar.user = testUser;
        calendar.name = "No Year";
        calendar.year = null; // Invalid
        calendar.persist();

        // When/Then: Should throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            calendarGenerationService.generateCalendar(calendar);
        }, "Should throw exception for null year");
    }

    // ============================================================================
    // GRAPHQL ENDPOINT TESTS (Unauthenticated)
    // ============================================================================

    @Test
    @Order(40)
    void testGraphQL_CreateCalendar_WithoutAuthentication() {
        // When: Try to create calendar without authentication
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

        // Then: Should fail with authorization error
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("query", mutation))
            .when()
            .post("/graphql")
            .then()
            .statusCode(200)
            .body("errors", notNullValue())
            .body("errors[0].message", anyOf(
                containsString("Unauthorized"),
                containsString("System error")
            ));
    }

    @Test
    @Order(41)
    void testGraphQL_SchemaIntrospection() {
        // When: Query GraphQL schema
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

        // Then: Should return schema information
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
}
