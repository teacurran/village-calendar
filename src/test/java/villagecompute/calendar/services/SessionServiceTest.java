package villagecompute.calendar.services;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Unit tests for SessionService. Tests session management, calendar operations, conversion, and expiration.
 */
@QuarkusTest
class SessionServiceTest {

    @Inject
    SessionService sessionService;

    @Inject
    CalendarService calendarService;

    private CalendarUser testUser;
    private String testSessionId;

    @BeforeEach
    @Transactional
    void setUp() {
        // Create test user
        testUser = new CalendarUser();
        testUser.oauthProvider = "GOOGLE";
        testUser.oauthSubject = "test-user-session-123";
        testUser.email = "session-test@example.com";
        testUser.displayName = "Session Test User";
        testUser.isAdmin = false;
        testUser.persist();

        // Generate test session ID
        testSessionId = UUID.randomUUID().toString();
    }

    @AfterEach
    @Transactional
    void tearDown() {
        // Clean up test data in proper order due to foreign key constraints
        try {
            // Delete order items first (foreign key to orders)
            villagecompute.calendar.data.models.CalendarOrderItem.deleteAll();
        } catch (Exception e) {
            // Ignore if table doesn't exist or is empty
        }
        try {
            // Delete calendar_orders (foreign key to users)
            villagecompute.calendar.data.models.CalendarOrder.deleteAll();
        } catch (Exception e) {
            // Ignore if table doesn't exist or is empty
        }
        try {
            // Delete calendars
            UserCalendar.deleteAll();
        } catch (Exception e) {
            // Ignore cleanup errors
        }
        try {
            // Delete users
            CalendarUser.deleteAll();
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    // ========== SESSION ID GENERATION TESTS ==========

    @Test
    void testGenerateSessionId_Success() {
        // When
        String sessionId = sessionService.generateSessionId();

        // Then
        assertNotNull(sessionId);
        assertFalse(sessionId.isBlank());

        // Should be valid UUID format
        assertDoesNotThrow(() -> UUID.fromString(sessionId));
    }

    @Test
    void testGenerateSessionId_Uniqueness() {
        // When
        String sessionId1 = sessionService.generateSessionId();
        String sessionId2 = sessionService.generateSessionId();

        // Then
        assertNotEquals(sessionId1, sessionId2);
    }

    // ========== GET SESSION CALENDARS TESTS ==========

    @Test
    @Transactional
    void testGetSessionCalendars_Success() {
        // Given
        calendarService.createCalendar("Guest Cal 1", 2025, null, null, true, null, testSessionId);
        calendarService.createCalendar("Guest Cal 2", 2024, null, null, true, null, testSessionId);

        // When
        List<UserCalendar> calendars = sessionService.getSessionCalendars(testSessionId);

        // Then
        assertEquals(2, calendars.size());
        assertTrue(calendars.stream().allMatch(c -> c.sessionId.equals(testSessionId)));
        assertTrue(calendars.stream().allMatch(c -> c.user == null));
    }

    @Test
    @Transactional
    void testGetSessionCalendars_EmptySession_ReturnsEmpty() {
        // Given
        String emptySessionId = UUID.randomUUID().toString();

        // When
        List<UserCalendar> calendars = sessionService.getSessionCalendars(emptySessionId);

        // Then
        assertNotNull(calendars);
        assertEquals(0, calendars.size());
    }

    @Test
    void testGetSessionCalendars_NullSessionId_ReturnsEmpty() {
        // When
        List<UserCalendar> calendars = sessionService.getSessionCalendars(null);

        // Then
        assertNotNull(calendars);
        assertEquals(0, calendars.size());
    }

    @Test
    void testGetSessionCalendars_BlankSessionId_ReturnsEmpty() {
        // When
        List<UserCalendar> calendars = sessionService.getSessionCalendars("   ");

        // Then
        assertNotNull(calendars);
        assertEquals(0, calendars.size());
    }

    // ========== HAS SESSION CALENDARS TESTS ==========

    @Test
    @Transactional
    void testHasSessionCalendars_WithCalendars_ReturnsTrue() {
        // Given
        calendarService.createCalendar("Guest Cal", 2025, null, null, true, null, testSessionId);

        // When
        boolean hasCalendars = sessionService.hasSessionCalendars(testSessionId);

        // Then
        assertTrue(hasCalendars);
    }

    @Test
    @Transactional
    void testHasSessionCalendars_WithoutCalendars_ReturnsFalse() {
        // Given
        String emptySessionId = UUID.randomUUID().toString();

        // When
        boolean hasCalendars = sessionService.hasSessionCalendars(emptySessionId);

        // Then
        assertFalse(hasCalendars);
    }

    @Test
    void testHasSessionCalendars_NullSessionId_ReturnsFalse() {
        // When
        boolean hasCalendars = sessionService.hasSessionCalendars(null);

        // Then
        assertFalse(hasCalendars);
    }

    // ========== GET SESSION CALENDAR COUNT TESTS ==========

    @Test
    @Transactional
    void testGetSessionCalendarCount_Success() {
        // Given
        calendarService.createCalendar("Cal 1", 2025, null, null, true, null, testSessionId);
        calendarService.createCalendar("Cal 2", 2025, null, null, true, null, testSessionId);
        calendarService.createCalendar("Cal 3", 2025, null, null, true, null, testSessionId);

        // When
        long count = sessionService.getSessionCalendarCount(testSessionId);

        // Then
        assertEquals(3, count);
    }

    @Test
    @Transactional
    void testGetSessionCalendarCount_EmptySession_ReturnsZero() {
        // Given
        String emptySessionId = UUID.randomUUID().toString();

        // When
        long count = sessionService.getSessionCalendarCount(emptySessionId);

        // Then
        assertEquals(0, count);
    }

    @Test
    void testGetSessionCalendarCount_NullSessionId_ReturnsZero() {
        // When
        long count = sessionService.getSessionCalendarCount(null);

        // Then
        assertEquals(0, count);
    }

    // ========== CONVERT SESSION TO USER TESTS ==========

    @Test
    @Transactional
    void testConvertSessionToUser_Success() {
        // Given
        calendarService.createCalendar("Guest Cal 1", 2025, null, null, true, null, testSessionId);
        calendarService.createCalendar("Guest Cal 2", 2024, null, null, true, null, testSessionId);

        // When
        int convertedCount = sessionService.convertSessionToUser(testSessionId, testUser);

        // Then
        assertEquals(2, convertedCount);

        // Verify calendars are now owned by user
        List<UserCalendar> calendars = sessionService.getSessionCalendars(testSessionId);
        assertEquals(0, calendars.size()); // No longer in session

        // Verify calendars are in user's account
        List<UserCalendar> userCalendars = UserCalendar.findByUser(testUser.id).list();
        assertEquals(2, userCalendars.size());
        assertTrue(userCalendars.stream().allMatch(c -> c.user.id.equals(testUser.id)));
        assertTrue(userCalendars.stream().allMatch(c -> c.sessionId == null));
    }

    @Test
    @Transactional
    void testConvertSessionToUser_EmptySession_ReturnsZero() {
        // Given
        String emptySessionId = UUID.randomUUID().toString();

        // When
        int convertedCount = sessionService.convertSessionToUser(emptySessionId, testUser);

        // Then
        assertEquals(0, convertedCount);
    }

    @Test
    void testConvertSessionToUser_NullSessionId_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> sessionService.convertSessionToUser(null, testUser));
    }

    @Test
    void testConvertSessionToUser_BlankSessionId_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> sessionService.convertSessionToUser("   ", testUser));
    }

    @Test
    void testConvertSessionToUser_NullUser_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> sessionService.convertSessionToUser(testSessionId, null));
    }

    // ========== DELETE EXPIRED SESSIONS TESTS ==========

    @Test
    @Transactional
    void testDeleteExpiredSessions_DeletesOldCalendars() {
        // Given - Create calendar with old update timestamp
        UserCalendar oldCalendar = new UserCalendar();
        oldCalendar.sessionId = testSessionId;
        oldCalendar.name = "Old Guest Calendar";
        oldCalendar.year = 2024;
        oldCalendar.isPublic = true;
        oldCalendar.persist();

        // Manually set old update timestamp (31 days ago) using native SQL
        // to bypass @UpdateTimestamp annotation
        Instant oldTimestamp = Instant.now().minus(31, ChronoUnit.DAYS);
        UserCalendar.getEntityManager()
                .createNativeQuery("UPDATE user_calendars SET updated = :timestamp WHERE id = :id")
                .setParameter("timestamp", oldTimestamp).setParameter("id", oldCalendar.id).executeUpdate();

        // When
        int deletedCount = sessionService.deleteExpiredSessions();

        // Then
        assertTrue(deletedCount >= 1);
        assertNull(UserCalendar.findById(oldCalendar.id));
    }

    @Test
    @Transactional
    void testDeleteExpiredSessions_KeepsRecentCalendars() {
        // Given - Create recent guest calendar
        calendarService.createCalendar("Recent Guest Cal", 2025, null, null, true, null, testSessionId);

        // When
        sessionService.deleteExpiredSessions();

        // Then - Should not delete recent calendars
        List<UserCalendar> calendars = sessionService.getSessionCalendars(testSessionId);
        assertEquals(1, calendars.size());
    }

    @Test
    @Transactional
    void testDeleteExpiredSessions_OnlyDeletesSessionCalendars() {
        // Given - Create user calendar (should not be deleted)
        calendarService.createCalendar("User Cal", 2025, null, null, true, testUser, null);

        // Create old guest calendar
        UserCalendar oldGuestCal = new UserCalendar();
        oldGuestCal.sessionId = testSessionId;
        oldGuestCal.name = "Old Guest";
        oldGuestCal.year = 2024;
        oldGuestCal.isPublic = true;
        oldGuestCal.persist();

        // Manually set old update timestamp using native SQL
        Instant oldTimestamp = Instant.now().minus(31, ChronoUnit.DAYS);
        UserCalendar.getEntityManager()
                .createNativeQuery("UPDATE user_calendars SET updated = :timestamp WHERE id = :id")
                .setParameter("timestamp", oldTimestamp).setParameter("id", oldGuestCal.id).executeUpdate();

        // When
        sessionService.deleteExpiredSessions();

        // Then - User calendar should remain
        List<UserCalendar> userCalendars = UserCalendar.findByUser(testUser.id).list();
        assertEquals(1, userCalendars.size());
    }

    @Test
    @Transactional
    void testDeleteExpiredSessions_NoExpiredCalendars_ReturnsZero() {
        // Given - Only recent calendars
        calendarService.createCalendar("Recent Cal", 2025, null, null, true, null, testSessionId);

        // When
        int deletedCount = sessionService.deleteExpiredSessions();

        // Then
        assertEquals(0, deletedCount);
    }

    @Test
    @Transactional
    void testDeleteExpiredSessions_MultipleExpiredSessions() {
        // Given - Create multiple old guest calendars
        Instant oldTimestamp = Instant.now().minus(35, ChronoUnit.DAYS);
        for (int i = 0; i < 3; i++) {
            UserCalendar oldCal = new UserCalendar();
            oldCal.sessionId = "session-" + i;
            oldCal.name = "Old Cal " + i;
            oldCal.year = 2024;
            oldCal.isPublic = true;
            oldCal.persist();

            // Manually set old update timestamp using native SQL
            UserCalendar.getEntityManager()
                    .createNativeQuery("UPDATE user_calendars SET updated = :timestamp WHERE id = :id")
                    .setParameter("timestamp", oldTimestamp).setParameter("id", oldCal.id).executeUpdate();
        }

        // When
        int deletedCount = sessionService.deleteExpiredSessions();

        // Then
        assertTrue(deletedCount >= 3);
    }
}
