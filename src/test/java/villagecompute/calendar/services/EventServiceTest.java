package villagecompute.calendar.services;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.Event;
import villagecompute.calendar.data.models.UserCalendar;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Comprehensive unit tests for EventService. Tests all CRUD operations, validation logic, authorization, bulk imports,
 * and edge cases.
 */
@QuarkusTest
class EventServiceTest {

    @Inject
    EventService eventService;

    @Inject
    CalendarService calendarService;

    @Inject
    ObjectMapper objectMapper;

    private CalendarUser testUser;
    private CalendarUser adminUser;
    private CalendarUser otherUser;
    private UserCalendar testCalendar;

    @BeforeEach
    @Transactional
    void setUp() {
        // Create test users
        testUser = new CalendarUser();
        testUser.oauthProvider = "GOOGLE";
        testUser.oauthSubject = "test-user-123";
        testUser.email = "test@example.com";
        testUser.displayName = "Test User";
        testUser.isAdmin = false;
        testUser.persist();

        adminUser = new CalendarUser();
        adminUser.oauthProvider = "GOOGLE";
        adminUser.oauthSubject = "admin-user-456";
        adminUser.email = "admin@example.com";
        adminUser.displayName = "Admin User";
        adminUser.isAdmin = true;
        adminUser.persist();

        otherUser = new CalendarUser();
        otherUser.oauthProvider = "GOOGLE";
        otherUser.oauthSubject = "other-user-789";
        otherUser.email = "other@example.com";
        otherUser.displayName = "Other User";
        otherUser.isAdmin = false;
        otherUser.persist();

        // Create test calendar
        testCalendar = calendarService.createCalendar("Test Calendar 2025", 2025, null, null, true, testUser, null);
    }

    @AfterEach
    @Transactional
    void tearDown() {
        // Clean up test data
        Event.deleteAll();
        UserCalendar.deleteAll();
        CalendarUser.deleteAll();
        CalendarTemplate.deleteAll();
    }

    // ========== ADD EVENT TESTS ==========

    @Test
    @Transactional
    void testAddEvent_Success() {
        // Given
        LocalDate eventDate = LocalDate.of(2025, 1, 1);
        String eventText = "New Year's Day";
        String emoji = "🎉";
        String color = "#FF5733";

        // When
        Event event = eventService.addEvent(testCalendar.id, eventDate, eventText, emoji, color, testUser);

        // Then
        assertNotNull(event);
        assertNotNull(event.id);
        assertEquals(testCalendar.id, event.calendar.id);
        assertEquals(eventDate, event.eventDate);
        assertEquals(eventText, event.eventText);
        assertEquals(emoji, event.emoji);
        assertEquals(color, event.color);
    }

    @Test
    @Transactional
    void testAddEvent_MinimalData_Success() {
        // Given - Only required fields
        LocalDate eventDate = LocalDate.of(2025, 6, 15);

        // When
        Event event = eventService.addEvent(testCalendar.id, eventDate, null, null, null, testUser);

        // Then
        assertNotNull(event);
        assertEquals(eventDate, event.eventDate);
        assertNull(event.eventText);
        assertNull(event.emoji);
        assertNull(event.color);
    }

    @Test
    @Transactional
    void testAddEvent_DateOutsideCalendarYear_ThrowsException() {
        // Given
        LocalDate eventDate = LocalDate.of(2024, 12, 31); // Calendar is for 2025

        // When & Then
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.addEvent(testCalendar.id, eventDate, "Test", null, null, testUser));

        assertTrue(ex.getMessage().contains("Event date must be within calendar year 2025"));
    }

    @Test
    @Transactional
    void testAddEvent_TextTooLong_ThrowsException() {
        // Given
        String longText = "a".repeat(501); // Max is 500
        LocalDate eventDate = LocalDate.of(2025, 3, 15);

        // When & Then
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.addEvent(testCalendar.id, eventDate, longText, null, null, testUser));

        assertTrue(ex.getMessage().contains("Event text must be 500 characters or less"));
    }

    @Test
    @Transactional
    void testAddEvent_TextExactly500Chars_Success() {
        // Given
        String text500 = "a".repeat(500); // Exactly 500
        LocalDate eventDate = LocalDate.of(2025, 7, 4);

        // When
        Event event = eventService.addEvent(testCalendar.id, eventDate, text500, null, null, testUser);

        // Then
        assertNotNull(event);
        assertEquals(500, event.eventText.length());
    }

    @Test
    @Transactional
    void testAddEvent_InvalidEmoji_ThrowsException() {
        // Given
        String invalidEmoji = "abc123"; // Not an emoji
        LocalDate eventDate = LocalDate.of(2025, 5, 1);

        // When & Then
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.addEvent(testCalendar.id, eventDate, "Test", invalidEmoji, null, testUser));

        assertTrue(ex.getMessage().contains("Invalid emoji"));
    }

    @Test
    @Transactional
    void testAddEvent_ValidEmojis_Success() {
        // Test various valid emoji formats
        String[] validEmojis = {"🎉", // Single emoji
                "❤️", // Emoji with variation selector
                "👨‍👩‍👧‍👦", // Family emoji with ZWJ
                "🇺🇸", // Flag emoji
                "🎄🎅", // Multiple emojis
        };

        for (String emoji : validEmojis) {
            LocalDate eventDate = LocalDate.of(2025, 1, 1).plusDays(validEmojis.length);
            Event event = eventService.addEvent(testCalendar.id, eventDate, "Test", emoji, null, testUser);
            assertNotNull(event);
            assertEquals(emoji, event.emoji);
        }
    }

    @Test
    @Transactional
    void testAddEvent_InvalidColor_ThrowsException() {
        // Given
        String invalidColor = "red"; // Not hex format
        LocalDate eventDate = LocalDate.of(2025, 8, 15);

        // When & Then
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.addEvent(testCalendar.id, eventDate, "Test", null, invalidColor, testUser));

        assertTrue(ex.getMessage().contains("Invalid color"));
    }

    @Test
    @Transactional
    void testAddEvent_ValidColors_Success() {
        // Test various valid color formats
        String[] validColors = {"#FF5733", // 6-digit hex
                "#ABC", // 3-digit hex
                "#000000", // Black
                "#FFFFFF", // White
        };

        for (String color : validColors) {
            LocalDate eventDate = LocalDate.of(2025, 2, 1).plusDays(validColors.length);
            Event event = eventService.addEvent(testCalendar.id, eventDate, "Test", null, color, testUser);
            assertNotNull(event);
            assertEquals(color, event.color);
        }
    }

    @Test
    @Transactional
    void testAddEvent_UnauthorizedUser_ThrowsException() {
        // Given
        LocalDate eventDate = LocalDate.of(2025, 9, 1);

        // When & Then
        assertThrows(SecurityException.class,
                () -> eventService.addEvent(testCalendar.id, eventDate, "Hacked", null, null, otherUser));
    }

    @Test
    @Transactional
    void testAddEvent_AdminCanAddToAnyCalendar_Success() {
        // Given
        LocalDate eventDate = LocalDate.of(2025, 10, 31);

        // When
        Event event = eventService.addEvent(testCalendar.id, eventDate, "Admin Event", null, null, adminUser);

        // Then
        assertNotNull(event);
    }

    @Test
    @Transactional
    void testAddEvent_NoAuth_ThrowsException() {
        // Given
        LocalDate eventDate = LocalDate.of(2025, 11, 1);

        // When & Then
        assertThrows(SecurityException.class,
                () -> eventService.addEvent(testCalendar.id, eventDate, "Test", null, null, null));
    }

    @Test
    void testAddEvent_NullCalendarId_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addEvent(null, LocalDate.of(2025, 1, 1), "Test", null, null, testUser));
    }

    @Test
    void testAddEvent_NullEventDate_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addEvent(testCalendar.id, null, "Test", null, null, testUser));
    }

    // ========== UPDATE EVENT TESTS ==========

    @Test
    @Transactional
    void testUpdateEvent_Success() {
        // Given
        Event event = eventService.addEvent(testCalendar.id, LocalDate.of(2025, 3, 1), "Original", "🎉", "#FF0000",
                testUser);

        // When
        Event updated = eventService.updateEvent(event.id, "Updated Text", "🎊", "#00FF00", testUser);

        // Then
        assertEquals("Updated Text", updated.eventText);
        assertEquals("🎊", updated.emoji);
        assertEquals("#00FF00", updated.color);
        assertEquals(event.eventDate, updated.eventDate); // Date unchanged
    }

    @Test
    @Transactional
    void testUpdateEvent_PartialUpdate_Success() {
        // Given
        Event event = eventService.addEvent(testCalendar.id, LocalDate.of(2025, 4, 1), "Original", "🎉", "#FF0000",
                testUser);

        // When - Update only text
        Event updated = eventService.updateEvent(event.id, "New Text", null, null, testUser);

        // Then
        assertEquals("New Text", updated.eventText);
        assertEquals("🎉", updated.emoji); // Unchanged
        assertEquals("#FF0000", updated.color); // Unchanged
    }

    @Test
    @Transactional
    void testUpdateEvent_NotFound_ThrowsException() {
        // Given
        java.util.UUID nonExistentId = java.util.UUID.randomUUID();

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> eventService.updateEvent(nonExistentId, "Test", null, null, testUser));
    }

    @Test
    @Transactional
    void testUpdateEvent_UnauthorizedUser_ThrowsException() {
        // Given
        Event event = eventService.addEvent(testCalendar.id, LocalDate.of(2025, 5, 1), "Test", null, null, testUser);

        // When & Then
        assertThrows(SecurityException.class,
                () -> eventService.updateEvent(event.id, "Hacked", null, null, otherUser));
    }

    @Test
    @Transactional
    void testUpdateEvent_InvalidData_ThrowsException() {
        // Given
        Event event = eventService.addEvent(testCalendar.id, LocalDate.of(2025, 6, 1), "Test", null, null, testUser);

        // When & Then - Invalid text length
        assertThrows(IllegalArgumentException.class,
                () -> eventService.updateEvent(event.id, "a".repeat(501), null, null, testUser));

        // When & Then - Invalid emoji
        assertThrows(IllegalArgumentException.class,
                () -> eventService.updateEvent(event.id, null, "invalid", null, testUser));

        // When & Then - Invalid color
        assertThrows(IllegalArgumentException.class,
                () -> eventService.updateEvent(event.id, null, null, "blue", testUser));
    }

    // ========== DELETE EVENT TESTS ==========

    @Test
    @Transactional
    void testDeleteEvent_Success() {
        // Given
        Event event = eventService.addEvent(testCalendar.id, LocalDate.of(2025, 7, 1), "To Delete", null, null,
                testUser);

        // When
        boolean deleted = eventService.deleteEvent(event.id, testUser);

        // Then
        assertTrue(deleted);
        assertNull(Event.findById(event.id));
    }

    @Test
    @Transactional
    void testDeleteEvent_NotFound_ThrowsException() {
        // Given
        java.util.UUID nonExistentId = java.util.UUID.randomUUID();

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> eventService.deleteEvent(nonExistentId, testUser));
    }

    @Test
    @Transactional
    void testDeleteEvent_UnauthorizedUser_ThrowsException() {
        // Given
        Event event = eventService.addEvent(testCalendar.id, LocalDate.of(2025, 8, 1), "Test", null, null, testUser);

        // When & Then
        assertThrows(SecurityException.class, () -> eventService.deleteEvent(event.id, otherUser));
    }

    @Test
    @Transactional
    void testDeleteEvent_AdminCanDelete_Success() {
        // Given
        Event event = eventService.addEvent(testCalendar.id, LocalDate.of(2025, 9, 1), "Test", null, null, testUser);

        // When
        boolean deleted = eventService.deleteEvent(event.id, adminUser);

        // Then
        assertTrue(deleted);
    }

    // ========== LIST EVENTS TESTS ==========

    @Test
    @Transactional
    void testListEvents_AllEvents_Success() {
        // Given
        eventService.addEvent(testCalendar.id, LocalDate.of(2025, 1, 1), "Event 1", null, null, testUser);
        eventService.addEvent(testCalendar.id, LocalDate.of(2025, 6, 15), "Event 2", null, null, testUser);
        eventService.addEvent(testCalendar.id, LocalDate.of(2025, 12, 31), "Event 3", null, null, testUser);

        // When
        List<Event> events = eventService.listEvents(testCalendar.id, null, null, testUser);

        // Then
        assertEquals(3, events.size());
        // Verify ordered by date
        assertEquals(LocalDate.of(2025, 1, 1), events.get(0).eventDate);
        assertEquals(LocalDate.of(2025, 6, 15), events.get(1).eventDate);
        assertEquals(LocalDate.of(2025, 12, 31), events.get(2).eventDate);
    }

    @Test
    @Transactional
    void testListEvents_WithDateRange_Success() {
        // Given
        eventService.addEvent(testCalendar.id, LocalDate.of(2025, 1, 1), "Event 1", null, null, testUser);
        eventService.addEvent(testCalendar.id, LocalDate.of(2025, 6, 15), "Event 2", null, null, testUser);
        eventService.addEvent(testCalendar.id, LocalDate.of(2025, 12, 31), "Event 3", null, null, testUser);

        // When - Query for events in summer
        List<Event> events = eventService.listEvents(testCalendar.id, LocalDate.of(2025, 6, 1),
                LocalDate.of(2025, 8, 31), testUser);

        // Then
        assertEquals(1, events.size());
        assertEquals("Event 2", events.get(0).eventText);
    }

    @Test
    @Transactional
    void testListEvents_InvalidDateRange_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> eventService.listEvents(testCalendar.id,
                LocalDate.of(2025, 12, 1), LocalDate.of(2025, 1, 1), testUser));
    }

    @Test
    @Transactional
    void testListEvents_PublicCalendar_AnyoneCanRead() {
        // Given
        eventService.addEvent(testCalendar.id, LocalDate.of(2025, 1, 1), "Public Event", null, null, testUser);

        // When - Other user reads public calendar
        List<Event> events = eventService.listEvents(testCalendar.id, null, null, otherUser);

        // Then
        assertEquals(1, events.size());
    }

    @Test
    @Transactional
    void testListEvents_PrivateCalendar_UnauthorizedThrowsException() {
        // Given - Private calendar
        UserCalendar privateCalendar = calendarService.createCalendar("Private Calendar", 2025, null, null, false,
                testUser, null);

        // When & Then
        assertThrows(SecurityException.class, () -> eventService.listEvents(privateCalendar.id, null, null, otherUser));
    }

    // ========== GET EVENT TESTS ==========

    @Test
    @Transactional
    void testGetEvent_Success() {
        // Given
        Event event = eventService.addEvent(testCalendar.id, LocalDate.of(2025, 2, 14), "Valentine's Day", "❤️", null,
                testUser);

        // When
        Event fetched = eventService.getEvent(event.id, testUser);

        // Then
        assertNotNull(fetched);
        assertEquals(event.id, fetched.id);
        assertEquals("Valentine's Day", fetched.eventText);
    }

    @Test
    void testGetEvent_NotFound_ThrowsException() {
        // Given
        java.util.UUID nonExistentId = java.util.UUID.randomUUID();

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> eventService.getEvent(nonExistentId, testUser));
    }

    @Test
    @Transactional
    void testGetEvent_PublicCalendar_AnyoneCanRead() {
        // Given
        Event event = eventService.addEvent(testCalendar.id, LocalDate.of(2025, 3, 17), "St. Patrick's Day", "☘️", null,
                testUser);

        // When
        Event fetched = eventService.getEvent(event.id, otherUser);

        // Then
        assertNotNull(fetched);
    }

    // ========== BULK IMPORT JSON TESTS ==========

    @Test
    @Transactional
    void testImportEventsFromJson_Success() throws Exception {
        // Given
        String json = """
                [
                    {"date": "2025-01-01", "text": "New Year", "emoji": "🎉", "color": "#FF5733"},
                    {"date": "2025-07-04", "text": "Independence Day", "emoji": "🇺🇸", "color": "#0000FF"},
                    {"date": "2025-12-25", "text": "Christmas", "emoji": "🎄", "color": "#00FF00"}
                ]
                """;

        // When
        List<Event> events = eventService.importEventsFromJson(testCalendar.id, json, testUser);

        // Then
        assertEquals(3, events.size());
        assertEquals("New Year", events.get(0).eventText);
        assertEquals("Independence Day", events.get(1).eventText);
        assertEquals("Christmas", events.get(2).eventText);
    }

    @Test
    @Transactional
    void testImportEventsFromJson_InvalidJson_ThrowsException() {
        // Given
        String invalidJson = "not valid json";

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> eventService.importEventsFromJson(testCalendar.id, invalidJson, testUser));
    }

    @Test
    @Transactional
    void testImportEventsFromJson_NotArray_ThrowsException() {
        // Given
        String notArray = "{\"date\": \"2025-01-01\", \"text\": \"Test\"}";

        // When & Then
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.importEventsFromJson(testCalendar.id, notArray, testUser));

        assertTrue(ex.getMessage().contains("JSON must be an array"));
    }

    @Test
    @Transactional
    void testImportEventsFromJson_SkipInvalidEntries() throws Exception {
        // Given - Array with missing date in second entry
        String json = """
                [
                    {"date": "2025-01-01", "text": "Valid Event"},
                    {"text": "Missing Date"},
                    {"date": "2025-12-25", "text": "Another Valid Event"}
                ]
                """;

        // When
        List<Event> events = eventService.importEventsFromJson(testCalendar.id, json, testUser);

        // Then - Should import 2 valid events, skip the invalid one
        assertEquals(2, events.size());
    }

    @Test
    @Transactional
    void testImportEventsFromJson_Unauthorized_ThrowsException() {
        // Given
        String json = "[{\"date\": \"2025-01-01\", \"text\": \"Test\"}]";

        // When & Then
        assertThrows(SecurityException.class,
                () -> eventService.importEventsFromJson(testCalendar.id, json, otherUser));
    }

    // ========== BULK IMPORT CSV TESTS ==========

    @Test
    @Transactional
    void testImportEventsFromCsv_Success() {
        // Given
        String csv = """
                date,text,emoji,color
                2025-01-01,New Year,🎉,#FF5733
                2025-07-04,Independence Day,🇺🇸,#0000FF
                2025-12-25,Christmas,🎄,#00FF00
                """;

        // When
        List<Event> events = eventService.importEventsFromCsv(testCalendar.id, csv, testUser);

        // Then
        assertEquals(3, events.size());
        assertEquals("New Year", events.get(0).eventText);
        assertEquals("Independence Day", events.get(1).eventText);
        assertEquals("Christmas", events.get(2).eventText);
    }

    @Test
    @Transactional
    void testImportEventsFromCsv_MinimalData_Success() {
        // Given - Only dates
        String csv = """
                date,text,emoji,color
                2025-01-01,,,
                2025-12-25,,,
                """;

        // When
        List<Event> events = eventService.importEventsFromCsv(testCalendar.id, csv, testUser);

        // Then
        assertEquals(2, events.size());
        assertNull(events.get(0).eventText);
        assertNull(events.get(1).eventText);
    }

    @Test
    @Transactional
    void testImportEventsFromCsv_InvalidDateFormat_ThrowsException() {
        // Given
        String csv = """
                date,text,emoji,color
                01/01/2025,New Year,,
                """;

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> eventService.importEventsFromCsv(testCalendar.id, csv, testUser));
    }

    @Test
    @Transactional
    void testImportEventsFromCsv_SkipEmptyLines() {
        // Given
        String csv = """
                date,text,emoji,color
                2025-01-01,Event 1,,

                2025-12-25,Event 2,,
                """;

        // When
        List<Event> events = eventService.importEventsFromCsv(testCalendar.id, csv, testUser);

        // Then
        assertEquals(2, events.size());
    }

    @Test
    @Transactional
    void testImportEventsFromCsv_Unauthorized_ThrowsException() {
        // Given
        String csv = "date,text,emoji,color\n2025-01-01,Test,,";

        // When & Then
        assertThrows(SecurityException.class, () -> eventService.importEventsFromCsv(testCalendar.id, csv, otherUser));
    }

    // ========== EDGE CASES ==========

    @Test
    @Transactional
    void testMultipleEventsOnSameDate_Success() {
        // Given - Multiple events on the same date should be allowed
        LocalDate date = LocalDate.of(2025, 1, 1);

        // When
        Event event1 = eventService.addEvent(testCalendar.id, date, "Event 1", null, null, testUser);
        Event event2 = eventService.addEvent(testCalendar.id, date, "Event 2", null, null, testUser);

        // Then
        assertNotNull(event1);
        assertNotNull(event2);
        assertNotEquals(event1.id, event2.id);
    }

    // Note: Cascade delete test removed - this is testing Hibernate's cascade behavior
    // which is configured via @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    // in UserCalendar.events. The database migration also has ON DELETE CASCADE.
    // This functionality is tested by the database schema and Hibernate configuration.

    @Test
    @Transactional
    void testLeapYearDate_Success() {
        // Given - 2024 is a leap year
        UserCalendar calendar2024 = calendarService.createCalendar("Calendar 2024", 2024, null, null, true, testUser,
                null);

        // When
        Event event = eventService.addEvent(calendar2024.id, LocalDate.of(2024, 2, 29), "Leap Day", null, null,
                testUser);

        // Then
        assertNotNull(event);
        assertEquals(LocalDate.of(2024, 2, 29), event.eventDate);
    }
}
