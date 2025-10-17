package villagecompute.calendar.integration;

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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for template application workflows.
 *
 * Tests:
 * 1. Apply template with moon phases enabled
 * 2. Apply template with Hebrew calendar enabled
 * 3. Apply template with custom holiday configuration
 *
 * All tests use GraphQL API via REST Assured to verify that JSONB configuration
 * fields are properly deep-copied during template application.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TemplateWorkflowTest {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AuthenticationService authService;

    private CalendarUser testUser;
    private String jwtToken;

    @BeforeEach
    @Transactional
    void setup() {
        // Create test user
        testUser = new CalendarUser();
        testUser.oauthProvider = "GOOGLE";
        testUser.oauthSubject = "template-test-" + System.currentTimeMillis();
        testUser.email = "template-test-" + System.currentTimeMillis() + "@example.com";
        testUser.displayName = "Template Test User";
        testUser.persist();

        // Generate JWT token for authentication
        jwtToken = authService.issueJWT(testUser);
    }

    @AfterEach
    @Transactional
    void cleanup() {
        // Clean up in correct order due to foreign keys
        if (testUser != null && testUser.id != null) {
            // Delete calendars first
            UserCalendar.delete("user.id", testUser.id);
            // Delete user
            CalendarUser.deleteById(testUser.id);
        }
        // Templates are cleaned up per-test
    }

    // ============================================================================
    // TEST: Apply Template with Moon Phases Enabled
    // ============================================================================

    @Test
    @Order(1)
    @Transactional
    void testTemplateWorkflow_ApplyTemplateWithMoonPhasesEnabled() {
        // Create template with moon phases configuration enabled
        CalendarTemplate moonTemplate = new CalendarTemplate();
        moonTemplate.name = "Moon Phases Template " + System.currentTimeMillis();
        moonTemplate.description = "Template with astronomy features";
        moonTemplate.isActive = true;
        moonTemplate.isFeatured = true;
        moonTemplate.displayOrder = 1;

        try {
            moonTemplate.configuration = objectMapper.readTree("""
                {
                    "theme": "astronomy",
                    "colorScheme": "dark",
                    "showMoonPhases": true,
                    "showMoonIllumination": true,
                    "moonSize": 24,
                    "showWeekNumbers": false
                }
                """);
        } catch (Exception e) {
            fail("Failed to create template configuration: " + e.getMessage());
        }

        moonTemplate.persist();

        try {
            // Create calendar from template via GraphQL
            String createMutation = String.format("""
                mutation {
                    createCalendar(input: {
                        name: "My Astronomy Calendar"
                        year: 2025
                        templateId: "%s"
                        isPublic: true
                    }) {
                        id
                        name
                        configuration
                        template {
                            configuration
                        }
                    }
                }
                """, moonTemplate.id.toString());

            String calendarId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + jwtToken)
                .body(Map.of("query", createMutation))
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.createCalendar.id", notNullValue())
                .body("data.createCalendar.configuration.showMoonPhases", equalTo(true))
                .body("data.createCalendar.configuration.showMoonIllumination", equalTo(true))
                .body("data.createCalendar.configuration.moonSize", equalTo(24))
                .body("data.createCalendar.configuration.theme", equalTo("astronomy"))
                .body("errors", nullValue())
                .extract()
                .path("data.createCalendar.id");

            // Verify calendar inherits moon phases setting in database
            UserCalendar calendar = UserCalendar.findById(java.util.UUID.fromString(calendarId));
            assertNotNull(calendar, "Calendar should exist in database");
            assertNotNull(calendar.configuration, "Configuration should not be null");
            assertTrue(calendar.configuration.get("showMoonPhases").asBoolean(),
                "Calendar should inherit showMoonPhases=true from template");
            assertTrue(calendar.configuration.get("showMoonIllumination").asBoolean(),
                "Calendar should inherit showMoonIllumination=true from template");
            assertEquals(24, calendar.configuration.get("moonSize").asInt(),
                "Calendar should inherit moonSize=24 from template");
            assertEquals("astronomy", calendar.configuration.get("theme").asText(),
                "Calendar should inherit theme from template");
        } finally {
            CalendarTemplate.deleteById(moonTemplate.id);
        }
    }

    // ============================================================================
    // TEST: Apply Template with Hebrew Calendar Enabled
    // ============================================================================

    @Test
    @Order(2)
    @Transactional
    void testTemplateWorkflow_ApplyTemplateWithHebrewCalendarEnabled() {
        // Create template with Hebrew calendar configuration enabled
        CalendarTemplate hebrewTemplate = new CalendarTemplate();
        hebrewTemplate.name = "Hebrew Calendar Template " + System.currentTimeMillis();
        hebrewTemplate.description = "Template with Hebrew calendar features";
        hebrewTemplate.isActive = true;
        hebrewTemplate.isFeatured = false;
        hebrewTemplate.displayOrder = 2;

        try {
            hebrewTemplate.configuration = objectMapper.readTree("""
                {
                    "theme": "traditional",
                    "colorScheme": "blue",
                    "showHebrewDates": true,
                    "showHebrewHolidays": true,
                    "hebrewFont": "Taamey Frank CLM",
                    "showWeekNumbers": true
                }
                """);
        } catch (Exception e) {
            fail("Failed to create template configuration: " + e.getMessage());
        }

        hebrewTemplate.persist();

        try {
            // Create calendar from template via GraphQL
            String createMutation = String.format("""
                mutation {
                    createCalendar(input: {
                        name: "My Hebrew Calendar"
                        year: 2025
                        templateId: "%s"
                        isPublic: false
                    }) {
                        id
                        name
                        configuration
                    }
                }
                """, hebrewTemplate.id.toString());

            String calendarId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + jwtToken)
                .body(Map.of("query", createMutation))
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.createCalendar.id", notNullValue())
                .body("data.createCalendar.configuration.showHebrewDates", equalTo(true))
                .body("data.createCalendar.configuration.showHebrewHolidays", equalTo(true))
                .body("data.createCalendar.configuration.hebrewFont", equalTo("Taamey Frank CLM"))
                .body("errors", nullValue())
                .extract()
                .path("data.createCalendar.id");

            // Verify calendar inherits Hebrew calendar setting in database
            UserCalendar calendar = UserCalendar.findById(java.util.UUID.fromString(calendarId));
            assertNotNull(calendar, "Calendar should exist in database");
            assertNotNull(calendar.configuration, "Configuration should not be null");
            assertTrue(calendar.configuration.get("showHebrewDates").asBoolean(),
                "Calendar should inherit showHebrewDates=true from template");
            assertTrue(calendar.configuration.get("showHebrewHolidays").asBoolean(),
                "Calendar should inherit showHebrewHolidays=true from template");
            assertEquals("Taamey Frank CLM", calendar.configuration.get("hebrewFont").asText(),
                "Calendar should inherit Hebrew font from template");
        } finally {
            CalendarTemplate.deleteById(hebrewTemplate.id);
        }
    }

    // ============================================================================
    // TEST: Apply Template with Custom Holiday Configuration
    // ============================================================================

    @Test
    @Order(3)
    @Transactional
    void testTemplateWorkflow_ApplyTemplateWithCustomHolidayConfig() {
        // Create template with custom holiday configuration
        CalendarTemplate holidayTemplate = new CalendarTemplate();
        holidayTemplate.name = "Holiday Template " + System.currentTimeMillis();
        holidayTemplate.description = "Template with custom US holidays";
        holidayTemplate.isActive = true;
        holidayTemplate.isFeatured = true;
        holidayTemplate.displayOrder = 3;

        try {
            holidayTemplate.configuration = objectMapper.readTree("""
                {
                    "theme": "festive",
                    "colorScheme": "red",
                    "showWeekNumbers": false,
                    "holidays": {
                        "country": "US",
                        "includeNational": true,
                        "includeReligious": true,
                        "includeObservances": false,
                        "customHolidays": [
                            {
                                "name": "Company Anniversary",
                                "date": "2025-03-15",
                                "color": "#FFD700"
                            },
                            {
                                "name": "Team Building Day",
                                "date": "2025-06-20",
                                "color": "#00CED1"
                            }
                        ]
                    },
                    "holidayStyle": {
                        "boldText": true,
                        "fontSize": 14,
                        "backgroundColor": "#FFF8DC"
                    }
                }
                """);
        } catch (Exception e) {
            fail("Failed to create template configuration: " + e.getMessage());
        }

        holidayTemplate.persist();

        try {
            // Create calendar from template via GraphQL
            String createMutation = String.format("""
                mutation {
                    createCalendar(input: {
                        name: "My Holiday Calendar"
                        year: 2025
                        templateId: "%s"
                        isPublic: true
                    }) {
                        id
                        name
                        configuration
                    }
                }
                """, holidayTemplate.id.toString());

            String calendarId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + jwtToken)
                .body(Map.of("query", createMutation))
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.createCalendar.id", notNullValue())
                .body("data.createCalendar.configuration.holidays.country", equalTo("US"))
                .body("data.createCalendar.configuration.holidays.includeNational", equalTo(true))
                .body("data.createCalendar.configuration.holidays.includeReligious", equalTo(true))
                .body("data.createCalendar.configuration.holidays.customHolidays", hasSize(2))
                .body("data.createCalendar.configuration.holidayStyle.boldText", equalTo(true))
                .body("data.createCalendar.configuration.holidayStyle.fontSize", equalTo(14))
                .body("errors", nullValue())
                .extract()
                .path("data.createCalendar.id");

            // Verify all holiday config fields are cloned correctly in database
            UserCalendar calendar = UserCalendar.findById(java.util.UUID.fromString(calendarId));
            assertNotNull(calendar, "Calendar should exist in database");
            assertNotNull(calendar.configuration, "Configuration should not be null");

            JsonNode holidays = calendar.configuration.get("holidays");
            assertNotNull(holidays, "Holidays config should be present");
            assertEquals("US", holidays.get("country").asText(), "Country should be cloned");
            assertTrue(holidays.get("includeNational").asBoolean(), "includeNational should be cloned");
            assertTrue(holidays.get("includeReligious").asBoolean(), "includeReligious should be cloned");
            assertFalse(holidays.get("includeObservances").asBoolean(), "includeObservances should be cloned");

            JsonNode customHolidays = holidays.get("customHolidays");
            assertNotNull(customHolidays, "Custom holidays should be present");
            assertTrue(customHolidays.isArray(), "Custom holidays should be an array");
            assertEquals(2, customHolidays.size(), "Should have 2 custom holidays");
            assertEquals("Company Anniversary", customHolidays.get(0).get("name").asText());
            assertEquals("2025-03-15", customHolidays.get(0).get("date").asText());
            assertEquals("#FFD700", customHolidays.get(0).get("color").asText());

            JsonNode holidayStyle = calendar.configuration.get("holidayStyle");
            assertNotNull(holidayStyle, "Holiday style should be present");
            assertTrue(holidayStyle.get("boldText").asBoolean(), "boldText should be cloned");
            assertEquals(14, holidayStyle.get("fontSize").asInt(), "fontSize should be cloned");
            assertEquals("#FFF8DC", holidayStyle.get("backgroundColor").asText(), "backgroundColor should be cloned");
        } finally {
            CalendarTemplate.deleteById(holidayTemplate.id);
        }
    }

    // ============================================================================
    // TEST: Template Configuration Deep Copy (Not Shallow Reference)
    // ============================================================================

    @Test
    @Order(4)
    @Transactional
    void testTemplateWorkflow_ConfigurationIsDeepCopied() {
        // Create template with nested configuration
        CalendarTemplate template = new CalendarTemplate();
        template.name = "Deep Copy Test Template " + System.currentTimeMillis();
        template.description = "Verify configuration is deep copied, not shallow referenced";
        template.isActive = true;
        template.isFeatured = false;
        template.displayOrder = 4;

        try {
            template.configuration = objectMapper.readTree("""
                {
                    "theme": "original",
                    "nested": {
                        "level1": {
                            "level2": {
                                "value": "original"
                            }
                        }
                    }
                }
                """);
        } catch (Exception e) {
            fail("Failed to create template configuration: " + e.getMessage());
        }

        template.persist();

        try {
            // Create calendar from template
            String createMutation = String.format("""
                mutation {
                    createCalendar(input: {
                        name: "Calendar 1"
                        year: 2025
                        templateId: "%s"
                    }) {
                        id
                        configuration
                    }
                }
                """, template.id.toString());

            String calendar1Id = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + jwtToken)
                .body(Map.of("query", createMutation))
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.createCalendar.id", notNullValue())
                .body("errors", nullValue())
                .extract()
                .path("data.createCalendar.id");

            // Update calendar 1's configuration
            String updateMutation = String.format("""
                mutation {
                    updateCalendar(
                        id: "%s"
                        input: {
                            configuration: {
                                theme: "modified",
                                nested: {
                                    level1: {
                                        level2: {
                                            value: "modified"
                                        }
                                    }
                                }
                            }
                        }
                    ) {
                        id
                        configuration
                    }
                }
                """, calendar1Id);

            given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + jwtToken)
                .body(Map.of("query", updateMutation))
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.updateCalendar.configuration.theme", equalTo("modified"))
                .body("data.updateCalendar.configuration.nested.level1.level2.value", equalTo("modified"))
                .body("errors", nullValue());

            // Create calendar 2 from the same template
            String createMutation2 = String.format("""
                mutation {
                    createCalendar(input: {
                        name: "Calendar 2"
                        year: 2025
                        templateId: "%s"
                    }) {
                        id
                        configuration
                    }
                }
                """, template.id.toString());

            given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + jwtToken)
                .body(Map.of("query", createMutation2))
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.createCalendar.id", notNullValue())
                .body("data.createCalendar.configuration.theme", equalTo("original"))
                .body("data.createCalendar.configuration.nested.level1.level2.value", equalTo("original"))
                .body("errors", nullValue());

            // Verify template configuration unchanged
            CalendarTemplate refreshedTemplate = CalendarTemplate.findById(template.id);
            assertNotNull(refreshedTemplate);
            assertEquals("original", refreshedTemplate.configuration.get("theme").asText(),
                "Template should not be affected by calendar modifications");
            assertEquals("original",
                refreshedTemplate.configuration.get("nested").get("level1").get("level2").get("value").asText(),
                "Template nested values should not be affected");
        } finally {
            CalendarTemplate.deleteById(template.id);
        }
    }
}
