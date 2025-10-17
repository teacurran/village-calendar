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
import villagecompute.calendar.data.models.Event;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.services.AuthenticationService;
import villagecompute.calendar.services.CalendarService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for critical calendar workflows.
 *
 * Tests:
 * 1. Create calendar from template and verify config cloned
 * 2. Add multiple events to calendar and verify persistence
 * 3. Update calendar configuration and verify changes persisted
 * 4. Query public templates via GraphQL (no authentication required)
 *
 * Note: These tests use service layer for authenticated operations due to JWT
 * authentication complexity in test environment. GraphQL authorization is covered
 * by CalendarGraphQLTest.java.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CalendarWorkflowTest {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AuthenticationService authService;

    @Inject
    CalendarService calendarService;

    private CalendarUser testUser;
    private CalendarTemplate testTemplate;

    @BeforeEach
    @Transactional
    void setup() {
        // Create test user
        testUser = new CalendarUser();
        testUser.oauthProvider = "GOOGLE";
        testUser.oauthSubject = "workflow-test-" + System.currentTimeMillis();
        testUser.email = "workflow-test-" + System.currentTimeMillis() + "@example.com";
        testUser.displayName = "Workflow Test User";
        testUser.persist();

        // Create test template with specific configuration
        testTemplate = new CalendarTemplate();
        testTemplate.name = "Workflow Test Template " + System.currentTimeMillis();
        testTemplate.description = "Template for workflow testing";
        testTemplate.isActive = true;
        testTemplate.isFeatured = false;
        testTemplate.displayOrder = 1;
        testTemplate.configuration = createTestConfiguration();
        testTemplate.persist();
    }

    @AfterEach
    @Transactional
    void cleanup() {
        // Clean up in correct order due to foreign keys
        if (testUser != null && testUser.id != null) {
            // Delete events first
            Event.delete("calendar.user.id", testUser.id);
            // Delete calendars
            UserCalendar.delete("user.id", testUser.id);
            // Delete user
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
                    "showMoonPhases": false,
                    "showWeekNumbers": true,
                    "compactMode": false
                }
                """);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ============================================================================
    // TEST: Query Public Templates via GraphQL (No Authentication Required)
    // ============================================================================

    @Test
    @Order(1)
    void testWorkflow_QueryPublicTemplates() {
        // Query templates via GraphQL (public endpoint, no auth required)
        String templateQuery = """
            query {
                templates(isActive: true) {
                    id
                    name
                    description
                    configuration
                }
            }
            """;

        String response = given()
            .contentType(ContentType.JSON)
            .body(Map.of("query", templateQuery))
            .when()
            .post("/graphql")
            .then()
            .statusCode(200)
            .body("data.templates", notNullValue())
            .body("data.templates", hasSize(greaterThanOrEqualTo(1)))
            .body("errors", nullValue())
            .extract()
            .asString();

        assertTrue(response.contains(testTemplate.name), "Template should be in the list");
    }

    // ============================================================================
    // TEST: Create Calendar from Template and Verify Config Cloned
    // ============================================================================

    @Test
    @Order(2)
    @Transactional
    void testWorkflow_CreateCalendarFromTemplate_VerifyConfigCloned() {
        // Create calendar from template via service layer
        UserCalendar calendar = calendarService.createCalendar(
            "Test Calendar from Template",
            2025,
            testTemplate.id,
            null,  // No custom configuration - use template config
            true,  // Public
            testUser,
            null   // No session ID (authenticated user)
        );

        assertNotNull(calendar, "Calendar should be created");
        assertNotNull(calendar.id, "Calendar should have an ID");
        assertEquals("Test Calendar from Template", calendar.name);
        assertEquals(2025, calendar.year);
        assertEquals(testTemplate.id, calendar.template.id);
        assertTrue(calendar.isPublic);

        // Verify configuration was cloned from template
        assertNotNull(calendar.configuration, "Calendar configuration should not be null");
        assertEquals("modern", calendar.configuration.get("theme").asText(),
            "Configuration should be cloned from template");
        assertEquals("blue", calendar.configuration.get("colorScheme").asText(),
            "Configuration should be cloned from template");
        assertTrue(calendar.configuration.get("showWeekNumbers").asBoolean(),
            "Configuration should be cloned from template");
        assertFalse(calendar.configuration.get("showMoonPhases").asBoolean(),
            "Configuration should be cloned from template");

        // Verify in database
        UserCalendar retrievedCalendar = UserCalendar.findById(calendar.id);
        assertNotNull(retrievedCalendar, "Calendar should be persisted");
        assertEquals("modern", retrievedCalendar.configuration.get("theme").asText());
        assertEquals("blue", retrievedCalendar.configuration.get("colorScheme").asText());
    }

    // ============================================================================
    // TEST: Add 5 Events to Calendar
    // ============================================================================

    @Test
    @Order(3)
    @Transactional
    void testWorkflow_AddMultipleEventsToCalendar() {
        // Create a calendar first
        UserCalendar calendar = calendarService.createCalendar(
            "Calendar for Events Test",
            2025,
            testTemplate.id,
            null,
            true,
            testUser,
            null
        );

        assertNotNull(calendar.id);

        // Add 5 events to the calendar
        Event event1 = new Event();
        event1.calendar = calendar;
        event1.eventDate = LocalDate.of(2025, 1, 1);
        event1.eventText = "New Year";
        event1.emoji = "🎉";
        event1.color = "#FF0000";
        event1.persist();

        Event event2 = new Event();
        event2.calendar = calendar;
        event2.eventDate = LocalDate.of(2025, 12, 25);
        event2.eventText = "Christmas";
        event2.emoji = "🎄";
        event2.color = "#00FF00";
        event2.persist();

        Event event3 = new Event();
        event3.calendar = calendar;
        event3.eventDate = LocalDate.of(2025, 7, 4);
        event3.eventText = "Independence Day";
        event3.emoji = "🇺🇸";
        event3.color = "#0000FF";
        event3.persist();

        Event event4 = new Event();
        event4.calendar = calendar;
        event4.eventDate = LocalDate.of(2025, 10, 31);
        event4.eventText = "Halloween";
        event4.emoji = "🎃";
        event4.color = "#FFA500";
        event4.persist();

        Event event5 = new Event();
        event5.calendar = calendar;
        event5.eventDate = LocalDate.of(2025, 11, 27);
        event5.eventText = "Thanksgiving";
        event5.emoji = "🦃";
        event5.color = "#8B4513";
        event5.persist();

        // Query calendar with events from database
        List<Event> events = Event.findByCalendar(calendar.id).list();

        // Verify all 5 events returned
        assertEquals(5, events.size(), "Should have 5 events");

        // Verify event details
        Event newYearEvent = Event.findByCalendarAndDate(calendar.id, LocalDate.of(2025, 1, 1));
        assertNotNull(newYearEvent, "New Year event should exist");
        assertEquals("New Year", newYearEvent.eventText);
        assertEquals("🎉", newYearEvent.emoji);
        assertEquals("#FF0000", newYearEvent.color);

        Event christmasEvent = Event.findByCalendarAndDate(calendar.id, LocalDate.of(2025, 12, 25));
        assertNotNull(christmasEvent, "Christmas event should exist");
        assertEquals("Christmas", christmasEvent.eventText);
        assertEquals("🎄", christmasEvent.emoji);
        assertEquals("#00FF00", christmasEvent.color);

        // Verify event count
        long eventCount = Event.countByCalendar(calendar.id);
        assertEquals(5, eventCount, "Event count should be 5");
    }

    // ============================================================================
    // TEST: Update Calendar Configuration
    // ============================================================================

    @Test
    @Order(4)
    @Transactional
    void testWorkflow_UpdateCalendarConfiguration() {
        // Create a calendar first
        UserCalendar calendar = calendarService.createCalendar(
            "Calendar for Update Test",
            2025,
            testTemplate.id,
            null,
            false,  // Private
            testUser,
            null
        );

        assertNotNull(calendar.id);
        assertFalse(calendar.isPublic, "Calendar should be private initially");

        // Verify initial configuration
        assertFalse(calendar.configuration.get("showMoonPhases").asBoolean(),
            "Moon phases should be disabled initially");

        // Create new configuration with moon phases enabled
        JsonNode updatedConfig;
        try {
            updatedConfig = objectMapper.readTree("""
                {
                    "theme": "modern",
                    "colorScheme": "blue",
                    "showMoonPhases": true,
                    "showWeekNumbers": false,
                    "compactMode": true
                }
                """);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Update calendar via service layer
        UserCalendar updatedCalendar = calendarService.updateCalendar(
            calendar.id,
            "Updated Calendar Name",
            updatedConfig,
            true,  // Make public
            testUser
        );

        // Verify updates
        assertEquals("Updated Calendar Name", updatedCalendar.name);
        assertTrue(updatedCalendar.isPublic, "Calendar should now be public");
        assertNotNull(updatedCalendar.configuration, "Configuration should not be null");
        assertTrue(updatedCalendar.configuration.get("showMoonPhases").asBoolean(),
            "Moon phases should be enabled");
        assertTrue(updatedCalendar.configuration.get("compactMode").asBoolean(),
            "Compact mode should be enabled");
        assertFalse(updatedCalendar.configuration.get("showWeekNumbers").asBoolean(),
            "Week numbers should be disabled");

        // Verify changes persisted in database
        UserCalendar dbCalendar = UserCalendar.findById(calendar.id);
        assertNotNull(dbCalendar, "Calendar should exist in database");
        assertEquals("Updated Calendar Name", dbCalendar.name);
        assertTrue(dbCalendar.isPublic);
        assertTrue(dbCalendar.configuration.get("showMoonPhases").asBoolean(),
            "Database should reflect moon phases enabled");
        assertTrue(dbCalendar.configuration.get("compactMode").asBoolean(),
            "Database should reflect compact mode enabled");
    }

    // ============================================================================
    // TEST: List Calendars for User
    // ============================================================================

    @Test
    @Order(5)
    @Transactional
    void testWorkflow_ListCalendarsForUser() {
        // Create multiple calendars for the test user
        UserCalendar calendar1 = calendarService.createCalendar(
            "My Calendar 2025",
            2025,
            testTemplate.id,
            null,
            true,
            testUser,
            null
        );

        UserCalendar calendar2 = calendarService.createCalendar(
            "My Calendar 2026",
            2026,
            testTemplate.id,
            null,
            false,
            testUser,
            null
        );

        UserCalendar calendar3 = calendarService.createCalendar(
            "My Calendar 2025 #2",
            2025,
            testTemplate.id,
            null,
            true,
            testUser,
            null
        );

        // Query calendars via service layer
        List<UserCalendar> allCalendars = calendarService.listCalendars(
            testUser.id,
            null,  // No year filter
            0,     // Page 0
            10,    // Page size 10
            testUser
        );

        assertEquals(3, allCalendars.size(), "Should have 3 calendars");

        // Query calendars for specific year
        List<UserCalendar> calendars2025 = calendarService.listCalendars(
            testUser.id,
            2025,  // Filter by year 2025
            0,
            10,
            testUser
        );

        assertEquals(2, calendars2025.size(), "Should have 2 calendars for 2025");

        // Query calendars for 2026
        List<UserCalendar> calendars2026 = calendarService.listCalendars(
            testUser.id,
            2026,
            0,
            10,
            testUser
        );

        assertEquals(1, calendars2026.size(), "Should have 1 calendar for 2026");
    }
}
