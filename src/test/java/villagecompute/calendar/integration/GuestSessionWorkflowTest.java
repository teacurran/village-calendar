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
import villagecompute.calendar.services.CalendarService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for guest session workflows using GraphQL API.
 *
 * Tests:
 * 1. Guest creates calendar without authentication (with sessionId)
 * 2. Convert guest session to user account
 * 3. Convert multiple calendars from guest session
 *
 * All tests use GraphQL API via REST Assured to verify guest session management
 * and ownership transfer on authentication.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GuestSessionWorkflowTest {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AuthenticationService authService;

    @Inject
    CalendarService calendarService;

    private CalendarTemplate testTemplate;

    @BeforeEach
    @Transactional
    void setup() {
        // Create test template
        testTemplate = new CalendarTemplate();
        testTemplate.name = "Guest Session Test Template " + System.currentTimeMillis();
        testTemplate.description = "Template for guest session testing";
        testTemplate.isActive = true;
        testTemplate.isFeatured = false;
        testTemplate.displayOrder = 1;
        testTemplate.configuration = createTestConfiguration();
        testTemplate.persist();
    }

    @AfterEach
    @Transactional
    void cleanup() {
        // Clean up template
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
                    "showWeekNumbers": true
                }
                """);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ============================================================================
    // TEST: Guest Creates Calendar Without Authentication
    // ============================================================================

    @Test
    @Order(1)
    @Transactional
    void testGuestWorkflow_CreateCalendarWithoutAuthentication() {
        // Generate a test sessionId
        String sessionId = UUID.randomUUID().toString();

        // Create calendar via service layer (simulating guest creation)
        // Note: GraphQL API requires authentication, so we test at service layer
        UserCalendar guestCalendar = calendarService.createCalendar(
            "Guest Calendar Test",
            2025,
            testTemplate.id,
            null,  // No custom configuration
            true,  // Public
            null,  // No user (guest)
            sessionId  // Session ID for guest
        );

        assertNotNull(guestCalendar, "Calendar should be created");
        assertNotNull(guestCalendar.id, "Calendar should have an ID");
        assertEquals(sessionId, guestCalendar.sessionId, "Calendar should have sessionId set");
        assertNull(guestCalendar.user, "Calendar should not have a user (guest)");
        assertEquals("Guest Calendar Test", guestCalendar.name);
        assertEquals(2025, guestCalendar.year);
        assertTrue(guestCalendar.isPublic);

        // Verify calendar persisted in database
        UserCalendar retrievedCalendar = UserCalendar.findById(guestCalendar.id);
        assertNotNull(retrievedCalendar, "Calendar should be persisted");
        assertEquals(sessionId, retrievedCalendar.sessionId);
        assertNull(retrievedCalendar.user, "User should be null for guest calendar");

        // Clean up
        UserCalendar.deleteById(guestCalendar.id);
    }

    // ============================================================================
    // TEST: Convert Guest Session to User Account
    // ============================================================================

    @Test
    @Order(2)
    @Transactional
    void testGuestWorkflow_ConvertSessionToUserAccount() {
        // Generate a test sessionId
        String sessionId = UUID.randomUUID().toString();

        // Create guest calendar
        UserCalendar guestCalendar = calendarService.createCalendar(
            "Guest Calendar to Convert",
            2025,
            testTemplate.id,
            null,
            true,
            null,  // No user
            sessionId
        );

        assertNotNull(guestCalendar.id);
        assertEquals(sessionId, guestCalendar.sessionId);
        assertNull(guestCalendar.user);

        // Create authenticated user
        CalendarUser user = new CalendarUser();
        user.oauthProvider = "GOOGLE";
        user.oauthSubject = "guest-convert-test-" + System.currentTimeMillis();
        user.email = "guest-convert-" + System.currentTimeMillis() + "@example.com";
        user.displayName = "Converted Guest User";
        user.persist();

        try {
            // Generate JWT token
            String jwtToken = authService.issueJWT(user);

            // Convert session using GraphQL mutation
            String convertMutation = String.format("""
                mutation {
                    convertGuestSession(sessionId: "%s") {
                        id
                        email
                        displayName
                        calendars {
                            id
                            name
                            sessionId
                        }
                    }
                }
                """, sessionId);

            given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + jwtToken)
                .body(Map.of("query", convertMutation))
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.convertGuestSession.id", equalTo(user.id.toString()))
                .body("data.convertGuestSession.email", equalTo(user.email))
                .body("errors", nullValue());

            // Verify calendar ownership transferred
            UserCalendar refreshedCalendar = UserCalendar.findById(guestCalendar.id);
            assertNotNull(refreshedCalendar.user, "Calendar should now have a user");
            assertEquals(user.id, refreshedCalendar.user.id, "Calendar should be owned by the user");
            assertNull(refreshedCalendar.sessionId, "SessionId should be cleared");

            // Verify via service layer
            int convertedCount = calendarService.convertSessionToUser(sessionId, user);
            assertEquals(0, convertedCount, "Should return 0 since already converted");

            // Clean up
            UserCalendar.deleteById(guestCalendar.id);
        } finally {
            CalendarUser.deleteById(user.id);
        }
    }

    // ============================================================================
    // TEST: Convert Multiple Calendars from Guest Session
    // ============================================================================

    @Test
    @Order(3)
    @Transactional
    void testGuestWorkflow_ConvertMultipleCalendarsFromSession() {
        // Generate a test sessionId
        String sessionId = UUID.randomUUID().toString();

        // Create 3 guest calendars with same sessionId
        UserCalendar calendar1 = calendarService.createCalendar(
            "Guest Calendar 1",
            2025,
            testTemplate.id,
            null,
            true,
            null,
            sessionId
        );

        UserCalendar calendar2 = calendarService.createCalendar(
            "Guest Calendar 2",
            2026,
            testTemplate.id,
            null,
            false,
            null,
            sessionId
        );

        UserCalendar calendar3 = calendarService.createCalendar(
            "Guest Calendar 3",
            2027,
            testTemplate.id,
            null,
            true,
            null,
            sessionId
        );

        // Verify all calendars have sessionId and no user
        assertEquals(sessionId, calendar1.sessionId);
        assertEquals(sessionId, calendar2.sessionId);
        assertEquals(sessionId, calendar3.sessionId);
        assertNull(calendar1.user);
        assertNull(calendar2.user);
        assertNull(calendar3.user);

        // Create authenticated user
        CalendarUser user = new CalendarUser();
        user.oauthProvider = "GOOGLE";
        user.oauthSubject = "multi-convert-test-" + System.currentTimeMillis();
        user.email = "multi-convert-" + System.currentTimeMillis() + "@example.com";
        user.displayName = "Multi Calendar User";
        user.persist();

        try {
            // Generate JWT token
            String jwtToken = authService.issueJWT(user);

            // Convert session using GraphQL mutation
            String convertMutation = String.format("""
                mutation {
                    convertGuestSession(sessionId: "%s") {
                        id
                        email
                        calendars {
                            id
                            name
                            sessionId
                        }
                    }
                }
                """, sessionId);

            given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + jwtToken)
                .body(Map.of("query", convertMutation))
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.convertGuestSession.id", equalTo(user.id.toString()))
                .body("errors", nullValue());

            // Verify all 3 calendars transferred to user account
            UserCalendar refreshed1 = UserCalendar.findById(calendar1.id);
            UserCalendar refreshed2 = UserCalendar.findById(calendar2.id);
            UserCalendar refreshed3 = UserCalendar.findById(calendar3.id);

            assertNotNull(refreshed1.user, "Calendar 1 should have user");
            assertNotNull(refreshed2.user, "Calendar 2 should have user");
            assertNotNull(refreshed3.user, "Calendar 3 should have user");

            assertEquals(user.id, refreshed1.user.id, "Calendar 1 should be owned by user");
            assertEquals(user.id, refreshed2.user.id, "Calendar 2 should be owned by user");
            assertEquals(user.id, refreshed3.user.id, "Calendar 3 should be owned by user");

            // Verify all sessionIds cleared
            assertNull(refreshed1.sessionId, "Calendar 1 sessionId should be cleared");
            assertNull(refreshed2.sessionId, "Calendar 2 sessionId should be cleared");
            assertNull(refreshed3.sessionId, "Calendar 3 sessionId should be cleared");

            // Verify via GraphQL query
            String myCalendarsQuery = """
                query {
                    myCalendars {
                        id
                        name
                        year
                        sessionId
                    }
                }
                """;

            given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + jwtToken)
                .body(Map.of("query", myCalendarsQuery))
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.myCalendars", hasSize(3))
                .body("data.myCalendars[0].sessionId", nullValue())
                .body("data.myCalendars[1].sessionId", nullValue())
                .body("data.myCalendars[2].sessionId", nullValue())
                .body("errors", nullValue());

            // Clean up
            UserCalendar.deleteById(calendar1.id);
            UserCalendar.deleteById(calendar2.id);
            UserCalendar.deleteById(calendar3.id);
        } finally {
            CalendarUser.deleteById(user.id);
        }
    }

    // ============================================================================
    // TEST: Guest Session Isolation (Different Sessions Don't Interfere)
    // ============================================================================

    @Test
    @Order(4)
    @Transactional
    void testGuestWorkflow_SessionIsolation() {
        // Generate two different sessionIds
        String sessionId1 = UUID.randomUUID().toString();
        String sessionId2 = UUID.randomUUID().toString();

        // Create calendar for session 1
        UserCalendar calendar1 = calendarService.createCalendar(
            "Session 1 Calendar",
            2025,
            testTemplate.id,
            null,
            true,
            null,
            sessionId1
        );

        // Create calendar for session 2
        UserCalendar calendar2 = calendarService.createCalendar(
            "Session 2 Calendar",
            2025,
            testTemplate.id,
            null,
            true,
            null,
            sessionId2
        );

        // Create user for session 1
        CalendarUser user1 = new CalendarUser();
        user1.oauthProvider = "GOOGLE";
        user1.oauthSubject = "isolation-test-1-" + System.currentTimeMillis();
        user1.email = "isolation-1-" + System.currentTimeMillis() + "@example.com";
        user1.displayName = "User 1";
        user1.persist();

        try {
            // Convert only session 1
            int convertedCount = calendarService.convertSessionToUser(sessionId1, user1);
            assertEquals(1, convertedCount, "Should convert 1 calendar for session 1");

            // Verify calendar1 transferred to user1
            UserCalendar refreshed1 = UserCalendar.findById(calendar1.id);
            assertNotNull(refreshed1.user);
            assertEquals(user1.id, refreshed1.user.id);
            assertNull(refreshed1.sessionId);

            // Verify calendar2 still belongs to session 2 (not affected)
            UserCalendar refreshed2 = UserCalendar.findById(calendar2.id);
            assertNull(refreshed2.user, "Calendar 2 should still be a guest calendar");
            assertEquals(sessionId2, refreshed2.sessionId, "Calendar 2 sessionId should be unchanged");

            // Clean up
            UserCalendar.deleteById(calendar1.id);
            UserCalendar.deleteById(calendar2.id);
        } finally {
            CalendarUser.deleteById(user1.id);
        }
    }

    // ============================================================================
    // TEST: Convert Empty Session (No Calendars)
    // ============================================================================

    @Test
    @Order(5)
    @Transactional
    void testGuestWorkflow_ConvertEmptySession() {
        // Generate sessionId with no calendars
        String emptySessionId = UUID.randomUUID().toString();

        // Create user
        CalendarUser user = new CalendarUser();
        user.oauthProvider = "GOOGLE";
        user.oauthSubject = "empty-session-test-" + System.currentTimeMillis();
        user.email = "empty-session-" + System.currentTimeMillis() + "@example.com";
        user.displayName = "Empty Session User";
        user.persist();

        try {
            // Convert session with no calendars
            int convertedCount = calendarService.convertSessionToUser(emptySessionId, user);
            assertEquals(0, convertedCount, "Should convert 0 calendars for empty session");

            // Verify user still exists
            CalendarUser retrievedUser = CalendarUser.findById(user.id);
            assertNotNull(retrievedUser);
            assertEquals(user.email, retrievedUser.email);
        } finally {
            CalendarUser.deleteById(user.id);
        }
    }

    // ============================================================================
    // TEST: Query Calendars by Session (Before Conversion)
    // ============================================================================

    @Test
    @Order(6)
    @Transactional
    void testGuestWorkflow_QueryCalendarsBySession() {
        // Generate sessionId
        String sessionId = UUID.randomUUID().toString();

        // Create 2 calendars for this session
        UserCalendar calendar1 = calendarService.createCalendar(
            "Session Query Calendar 1",
            2025,
            testTemplate.id,
            null,
            true,
            null,
            sessionId
        );

        UserCalendar calendar2 = calendarService.createCalendar(
            "Session Query Calendar 2",
            2026,
            testTemplate.id,
            null,
            false,
            null,
            sessionId
        );

        try {
            // Query calendars by session using repository
            List<UserCalendar> sessionCalendars = UserCalendar.findBySession(sessionId).list();

            assertEquals(2, sessionCalendars.size(), "Should find 2 calendars for session");
            assertTrue(
                sessionCalendars.stream().anyMatch(c -> c.name.equals("Session Query Calendar 1")),
                "Should include calendar 1"
            );
            assertTrue(
                sessionCalendars.stream().anyMatch(c -> c.name.equals("Session Query Calendar 2")),
                "Should include calendar 2"
            );

            // Verify all calendars have correct sessionId
            sessionCalendars.forEach(cal -> {
                assertEquals(sessionId, cal.sessionId, "Calendar should have correct sessionId");
                assertNull(cal.user, "Calendar should not have a user");
            });
        } finally {
            // Clean up
            UserCalendar.deleteById(calendar1.id);
            UserCalendar.deleteById(calendar2.id);
        }
    }
}
