package villagecompute.calendar.integration;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.services.CalendarService;

import io.quarkus.test.junit.QuarkusTest;

/**
 * End-to-end integration tests for template application workflows.
 *
 * <p>
 * Tests: 1. Apply template with moon phases enabled 2. Apply template with Hebrew calendar enabled 3. Apply template
 * with custom holiday configuration 4. Verify configuration is deep-copied (not shallow referenced)
 *
 * <p>
 * All tests use the service layer to verify that JSONB configuration fields are properly deep-copied during template
 * application.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TemplateWorkflowTest {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    CalendarService calendarService;

    private CalendarUser testUser;
    private CalendarTemplate moonTemplate;
    private CalendarTemplate hebrewTemplate;
    private CalendarTemplate holidayTemplate;
    private CalendarTemplate deepCopyTemplate;

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

        // Create moon phases template
        moonTemplate = new CalendarTemplate();
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
            fail("Failed to create moon template configuration: " + e.getMessage());
        }
        moonTemplate.persist();

        // Create Hebrew calendar template
        hebrewTemplate = new CalendarTemplate();
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
            fail("Failed to create Hebrew template configuration: " + e.getMessage());
        }
        hebrewTemplate.persist();

        // Create holiday template
        holidayTemplate = new CalendarTemplate();
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
            fail("Failed to create holiday template configuration: " + e.getMessage());
        }
        holidayTemplate.persist();

        // Create deep copy test template
        deepCopyTemplate = new CalendarTemplate();
        deepCopyTemplate.name = "Deep Copy Test Template " + System.currentTimeMillis();
        deepCopyTemplate.description = "Verify configuration is deep copied, not shallow referenced";
        deepCopyTemplate.isActive = true;
        deepCopyTemplate.isFeatured = false;
        deepCopyTemplate.displayOrder = 4;
        try {
            deepCopyTemplate.configuration = objectMapper.readTree("""
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
            fail("Failed to create deep copy template configuration: " + e.getMessage());
        }
        deepCopyTemplate.persist();
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
        // Delete templates
        if (moonTemplate != null && moonTemplate.id != null) {
            CalendarTemplate.deleteById(moonTemplate.id);
        }
        if (hebrewTemplate != null && hebrewTemplate.id != null) {
            CalendarTemplate.deleteById(hebrewTemplate.id);
        }
        if (holidayTemplate != null && holidayTemplate.id != null) {
            CalendarTemplate.deleteById(holidayTemplate.id);
        }
        if (deepCopyTemplate != null && deepCopyTemplate.id != null) {
            CalendarTemplate.deleteById(deepCopyTemplate.id);
        }
    }

    // ============================================================================
    // TEST: Apply Template with Moon Phases Enabled
    // ============================================================================

    @Test
    @Order(1)
    @Transactional
    void testTemplateWorkflow_ApplyTemplateWithMoonPhasesEnabled() {
        // Create calendar from template via service layer (bypasses GraphQL auth)
        UserCalendar calendar = calendarService.createCalendar("My Astronomy Calendar", 2025, moonTemplate.id, null, // No
                                                                                                                     // custom
                                                                                                                     // configuration
                                                                                                                     // -
                                                                                                                     // use
                                                                                                                     // template
                                                                                                                     // config
                true, // isPublic
                testUser, // Authenticated user
                null // No session ID
        );

        // Verify calendar was created
        assertNotNull(calendar, "Calendar should be created");
        assertNotNull(calendar.id, "Calendar should have an ID");
        assertEquals("My Astronomy Calendar", calendar.name);
        assertEquals(2025, calendar.year);
        assertEquals(testUser.id, calendar.user.id);

        // Verify calendar inherits moon phases setting from template
        UserCalendar retrievedCalendar = UserCalendar.findById(calendar.id);
        assertNotNull(retrievedCalendar, "Calendar should exist in database");
        assertNotNull(retrievedCalendar.configuration, "Configuration should not be null");
        assertTrue(retrievedCalendar.configuration.get("showMoonPhases").asBoolean(),
                "Calendar should inherit showMoonPhases=true from template");
        assertTrue(retrievedCalendar.configuration.get("showMoonIllumination").asBoolean(),
                "Calendar should inherit showMoonIllumination=true from template");
        assertEquals(24, retrievedCalendar.configuration.get("moonSize").asInt(),
                "Calendar should inherit moonSize=24 from template");
        assertEquals("astronomy", retrievedCalendar.configuration.get("theme").asText(),
                "Calendar should inherit theme from template");
    }

    // ============================================================================
    // TEST: Apply Template with Hebrew Calendar Enabled
    // ============================================================================

    @Test
    @Order(2)
    @Transactional
    void testTemplateWorkflow_ApplyTemplateWithHebrewCalendarEnabled() {
        // Use the Hebrew template created in @BeforeEach instead of creating a new one
        // This avoids transaction/persistence issues with creating entities inside test methods

        // Verify the template exists
        assertNotNull(hebrewTemplate, "Hebrew template should exist from @BeforeEach");
        assertNotNull(hebrewTemplate.id, "Hebrew template should have an ID");

        // Create calendar from template via service layer (bypasses GraphQL auth)
        UserCalendar calendar = calendarService.createCalendar("My Hebrew Calendar", 2025, hebrewTemplate.id, null, // No
                                                                                                                    // custom
                                                                                                                    // configuration
                                                                                                                    // -
                                                                                                                    // use
                                                                                                                    // template
                                                                                                                    // config
                false, // isPublic
                testUser, // Authenticated user
                null // No session ID
        );

        // Verify calendar was created
        assertNotNull(calendar, "Calendar should be created");
        assertNotNull(calendar.id, "Calendar should have an ID");
        assertEquals("My Hebrew Calendar", calendar.name);
        assertEquals(2025, calendar.year);
        assertFalse(calendar.isPublic);

        // Verify calendar inherits Hebrew calendar setting from template
        UserCalendar retrievedCalendar = UserCalendar.findById(calendar.id);
        assertNotNull(retrievedCalendar, "Calendar should exist in database");
        assertNotNull(retrievedCalendar.configuration, "Configuration should not be null");
        assertTrue(retrievedCalendar.configuration.get("showHebrewDates").asBoolean(),
                "Calendar should inherit showHebrewDates=true from template");
        assertTrue(retrievedCalendar.configuration.get("showHebrewHolidays").asBoolean(),
                "Calendar should inherit showHebrewHolidays=true from template");
        assertEquals("Taamey Frank CLM", retrievedCalendar.configuration.get("hebrewFont").asText(),
                "Calendar should inherit Hebrew font from template");
    }

    // ============================================================================
    // TEST: Apply Template with Custom Holiday Configuration
    // ============================================================================

    @Test
    @Order(3)
    @Transactional
    void testTemplateWorkflow_ApplyTemplateWithCustomHolidayConfig() {
        // Use the holiday template created in @BeforeEach instead of creating a new one
        // This avoids transaction/persistence issues with creating entities inside test methods

        // Verify the template exists
        assertNotNull(holidayTemplate, "Holiday template should exist from @BeforeEach");
        assertNotNull(holidayTemplate.id, "Holiday template should have an ID");

        // Create calendar from template via service layer (bypasses GraphQL auth)
        UserCalendar calendar = calendarService.createCalendar("My Holiday Calendar", 2025, holidayTemplate.id, null, // No
                                                                                                                      // custom
                                                                                                                      // configuration
                                                                                                                      // -
                                                                                                                      // use
                                                                                                                      // template
                                                                                                                      // config
                true, // isPublic
                testUser, // Authenticated user
                null // No session ID
        );

        // Verify calendar was created
        assertNotNull(calendar, "Calendar should be created");
        assertNotNull(calendar.id, "Calendar should have an ID");

        // Verify all holiday config fields are cloned correctly in database
        UserCalendar retrievedCalendar = UserCalendar.findById(calendar.id);
        assertNotNull(retrievedCalendar, "Calendar should exist in database");
        assertNotNull(retrievedCalendar.configuration, "Configuration should not be null");

        JsonNode holidays = retrievedCalendar.configuration.get("holidays");
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

        JsonNode holidayStyle = retrievedCalendar.configuration.get("holidayStyle");
        assertNotNull(holidayStyle, "Holiday style should be present");
        assertTrue(holidayStyle.get("boldText").asBoolean(), "boldText should be cloned");
        assertEquals(14, holidayStyle.get("fontSize").asInt(), "fontSize should be cloned");
        assertEquals("#FFF8DC", holidayStyle.get("backgroundColor").asText(), "backgroundColor should be cloned");
    }

    // ============================================================================
    // TEST: Template Configuration Deep Copy (Not Shallow Reference)
    // ============================================================================

    @Test
    @Order(4)
    @Transactional
    void testTemplateWorkflow_ConfigurationIsDeepCopied() {
        // Use the deep copy template created in @BeforeEach instead of creating a new one
        // This avoids transaction/persistence issues with creating entities inside test methods

        // Verify the template exists
        assertNotNull(deepCopyTemplate, "Deep copy template should exist from @BeforeEach");
        assertNotNull(deepCopyTemplate.id, "Deep copy template should have an ID");

        // Create calendar 1 from template via service layer
        UserCalendar calendar1 = calendarService.createCalendar("Calendar 1", 2025, deepCopyTemplate.id, null, // Use
                                                                                                               // template
                                                                                                               // config
                true, testUser, null);

        assertNotNull(calendar1);
        assertNotNull(calendar1.id);

        // Verify calendar 1 has original template configuration
        UserCalendar retrieved1 = UserCalendar.findById(calendar1.id);
        assertEquals("original", retrieved1.configuration.get("theme").asText());
        assertEquals("original",
                retrieved1.configuration.get("nested").get("level1").get("level2").get("value").asText());

        // Update calendar 1's configuration via service layer
        JsonNode modifiedConfig;
        try {
            modifiedConfig = objectMapper.readTree("""
                    {
                        "theme": "modified",
                        "nested": {
                            "level1": {
                                "level2": {
                                    "value": "modified"
                                }
                            }
                        }
                    }
                    """);
        } catch (Exception e) {
            fail("Failed to create modified configuration: " + e.getMessage());
            return; // Never reached but satisfies compiler
        }

        calendarService.updateCalendar(calendar1.id, "Calendar 1", modifiedConfig, null, // Keep same isPublic
                testUser);

        // Verify calendar 1 was updated
        UserCalendar updatedCalendar1 = UserCalendar.findById(calendar1.id);
        assertEquals("modified", updatedCalendar1.configuration.get("theme").asText());
        assertEquals("modified",
                updatedCalendar1.configuration.get("nested").get("level1").get("level2").get("value").asText());

        // Create calendar 2 from the same template
        UserCalendar calendar2 = calendarService.createCalendar("Calendar 2", 2025, deepCopyTemplate.id, null, // Use
                                                                                                               // template
                                                                                                               // config
                                                                                                               // (should
                                                                                                               // still
                                                                                                               // be
                                                                                                               // original)
                true, testUser, null);

        // Verify calendar 2 still has original template configuration (deep copy verification)
        UserCalendar retrieved2 = UserCalendar.findById(calendar2.id);
        assertEquals("original", retrieved2.configuration.get("theme").asText(),
                "Calendar 2 should have original template config (not affected by calendar 1" + " update)");
        assertEquals("original",
                retrieved2.configuration.get("nested").get("level1").get("level2").get("value").asText(),
                "Calendar 2 nested values should be original");

        // Verify template configuration unchanged
        CalendarTemplate refreshedTemplate = CalendarTemplate.findById(deepCopyTemplate.id);
        assertNotNull(refreshedTemplate);
        assertEquals("original", refreshedTemplate.configuration.get("theme").asText(),
                "Template should not be affected by calendar modifications");
        assertEquals("original",
                refreshedTemplate.configuration.get("nested").get("level1").get("level2").get("value").asText(),
                "Template nested values should not be affected");
    }
}
