package villagecompute.calendar.data.repositories;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.Event;
import villagecompute.calendar.data.models.UserCalendar;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EventRepository custom query methods.
 * Tests all custom query methods including findByCalendarId, findByDateRange, etc.
 */
@QuarkusTest
class EventRepositoryTest {

    @Inject
    EventRepository eventRepository;

    @Inject
    UserCalendarRepository calendarRepository;

    private CalendarUser testUser;
    private UserCalendar testCalendar;
    private UserCalendar otherCalendar;

    @BeforeEach
    @Transactional
    void setUp() {
        // Create test user
        testUser = new CalendarUser();
        testUser.oauthProvider = "GOOGLE";
        testUser.oauthSubject = "test-user-123";
        testUser.email = "test@example.com";
        testUser.displayName = "Test User";
        testUser.isAdmin = false;
        testUser.persist();

        // Create test calendars
        testCalendar = new UserCalendar();
        testCalendar.user = testUser;
        testCalendar.name = "Test Calendar 2025";
        testCalendar.year = 2025;
        testCalendar.isPublic = true;
        testCalendar.persist();

        otherCalendar = new UserCalendar();
        otherCalendar.user = testUser;
        otherCalendar.name = "Other Calendar 2025";
        otherCalendar.year = 2025;
        otherCalendar.isPublic = true;
        otherCalendar.persist();
    }

    @AfterEach
    @Transactional
    void tearDown() {
        // Clean up test data
        Event.deleteAll();
        UserCalendar.deleteAll();
        CalendarUser.deleteAll();
    }

    // ========== FIND BY CALENDAR ID TESTS ==========

    @Test
    @Transactional
    void testFindByCalendarId_Success() {
        // Given
        createEvent(testCalendar, LocalDate.of(2025, 3, 15), "Event 1");
        createEvent(testCalendar, LocalDate.of(2025, 1, 1), "Event 2");
        createEvent(testCalendar, LocalDate.of(2025, 12, 31), "Event 3");

        // When
        List<Event> events = eventRepository.findByCalendarId(testCalendar.id);

        // Then
        assertEquals(3, events.size());
        // Verify ordering by date ascending
        assertEquals(LocalDate.of(2025, 1, 1), events.get(0).eventDate);
        assertEquals(LocalDate.of(2025, 3, 15), events.get(1).eventDate);
        assertEquals(LocalDate.of(2025, 12, 31), events.get(2).eventDate);
    }

    @Test
    @Transactional
    void testFindByCalendarId_NoEvents_ReturnsEmpty() {
        // When
        List<Event> events = eventRepository.findByCalendarId(testCalendar.id);

        // Then
        assertTrue(events.isEmpty());
    }

    @Test
    @Transactional
    void testFindByCalendarId_IsolatesCalendars() {
        // Given - Events in different calendars
        createEvent(testCalendar, LocalDate.of(2025, 1, 1), "Calendar 1 Event");
        createEvent(otherCalendar, LocalDate.of(2025, 1, 1), "Calendar 2 Event");

        // When
        List<Event> events = eventRepository.findByCalendarId(testCalendar.id);

        // Then
        assertEquals(1, events.size());
        assertEquals("Calendar 1 Event", events.get(0).eventText);
    }

    // ========== FIND BY DATE RANGE TESTS ==========

    @Test
    @Transactional
    void testFindByDateRange_Success() {
        // Given
        createEvent(testCalendar, LocalDate.of(2025, 1, 1), "January");
        createEvent(testCalendar, LocalDate.of(2025, 6, 15), "June");
        createEvent(testCalendar, LocalDate.of(2025, 12, 31), "December");

        // When - Query for events in summer (June-August)
        List<Event> events = eventRepository.findByDateRange(
            testCalendar.id,
            LocalDate.of(2025, 6, 1),
            LocalDate.of(2025, 8, 31)
        );

        // Then
        assertEquals(1, events.size());
        assertEquals("June", events.get(0).eventText);
    }

    @Test
    @Transactional
    void testFindByDateRange_InclusiveBoundaries() {
        // Given
        createEvent(testCalendar, LocalDate.of(2025, 6, 1), "Start Date");
        createEvent(testCalendar, LocalDate.of(2025, 6, 15), "Middle Date");
        createEvent(testCalendar, LocalDate.of(2025, 6, 30), "End Date");

        // When - Query inclusive range
        List<Event> events = eventRepository.findByDateRange(
            testCalendar.id,
            LocalDate.of(2025, 6, 1),
            LocalDate.of(2025, 6, 30)
        );

        // Then
        assertEquals(3, events.size());
    }

    @Test
    @Transactional
    void testFindByDateRange_NoEventsInRange_ReturnsEmpty() {
        // Given
        createEvent(testCalendar, LocalDate.of(2025, 1, 1), "January");
        createEvent(testCalendar, LocalDate.of(2025, 12, 31), "December");

        // When - Query for summer events
        List<Event> events = eventRepository.findByDateRange(
            testCalendar.id,
            LocalDate.of(2025, 6, 1),
            LocalDate.of(2025, 8, 31)
        );

        // Then
        assertTrue(events.isEmpty());
    }

    @Test
    @Transactional
    void testFindByDateRange_SingleDay() {
        // Given
        createEvent(testCalendar, LocalDate.of(2025, 7, 4), "Independence Day");
        createEvent(testCalendar, LocalDate.of(2025, 7, 5), "Day After");

        // When - Query for single day
        List<Event> events = eventRepository.findByDateRange(
            testCalendar.id,
            LocalDate.of(2025, 7, 4),
            LocalDate.of(2025, 7, 4)
        );

        // Then
        assertEquals(1, events.size());
        assertEquals("Independence Day", events.get(0).eventText);
    }

    // ========== FIND BY CALENDAR AND DATE TESTS ==========

    @Test
    @Transactional
    void testFindByCalendarAndDate_Success() {
        // Given
        Event event = createEvent(testCalendar, LocalDate.of(2025, 1, 1), "New Year");

        // When
        Event found = eventRepository.findByCalendarAndDate(
            testCalendar.id,
            LocalDate.of(2025, 1, 1)
        );

        // Then
        assertNotNull(found);
        assertEquals(event.id, found.id);
        assertEquals("New Year", found.eventText);
    }

    @Test
    @Transactional
    void testFindByCalendarAndDate_NotFound_ReturnsNull() {
        // When
        Event found = eventRepository.findByCalendarAndDate(
            testCalendar.id,
            LocalDate.of(2025, 1, 1)
        );

        // Then
        assertNull(found);
    }

    @Test
    @Transactional
    void testFindByCalendarAndDate_MultipleEventsOnSameDate_ReturnsFirst() {
        // Given - Multiple events on same date
        Event event1 = createEvent(testCalendar, LocalDate.of(2025, 1, 1), "Event 1");
        Event event2 = createEvent(testCalendar, LocalDate.of(2025, 1, 1), "Event 2");

        // When
        Event found = eventRepository.findByCalendarAndDate(
            testCalendar.id,
            LocalDate.of(2025, 1, 1)
        );

        // Then
        assertNotNull(found);
        // Should return one of the events (order undefined)
        assertTrue(found.id.equals(event1.id) || found.id.equals(event2.id));
    }

    // ========== COUNT BY CALENDAR TESTS ==========

    @Test
    @Transactional
    void testCountByCalendar_Success() {
        // Given
        createEvent(testCalendar, LocalDate.of(2025, 1, 1), "Event 1");
        createEvent(testCalendar, LocalDate.of(2025, 6, 15), "Event 2");
        createEvent(testCalendar, LocalDate.of(2025, 12, 31), "Event 3");

        // When
        long count = eventRepository.countByCalendar(testCalendar.id);

        // Then
        assertEquals(3, count);
    }

    @Test
    @Transactional
    void testCountByCalendar_NoEvents_ReturnsZero() {
        // When
        long count = eventRepository.countByCalendar(testCalendar.id);

        // Then
        assertEquals(0, count);
    }

    @Test
    @Transactional
    void testCountByCalendar_IsolatesCalendars() {
        // Given - Events in different calendars
        createEvent(testCalendar, LocalDate.of(2025, 1, 1), "Event 1");
        createEvent(testCalendar, LocalDate.of(2025, 6, 15), "Event 2");
        createEvent(otherCalendar, LocalDate.of(2025, 1, 1), "Other Event");

        // When
        long count = eventRepository.countByCalendar(testCalendar.id);

        // Then
        assertEquals(2, count);
    }

    // ========== DELETE BY CALENDAR TESTS ==========

    @Test
    @Transactional
    void testDeleteByCalendar_Success() {
        // Given
        createEvent(testCalendar, LocalDate.of(2025, 1, 1), "Event 1");
        createEvent(testCalendar, LocalDate.of(2025, 6, 15), "Event 2");
        createEvent(otherCalendar, LocalDate.of(2025, 1, 1), "Other Event");

        // When
        long deletedCount = eventRepository.deleteByCalendar(testCalendar.id);

        // Then
        assertEquals(2, deletedCount);
        assertEquals(0, eventRepository.countByCalendar(testCalendar.id));
        assertEquals(1, eventRepository.countByCalendar(otherCalendar.id)); // Other calendar unaffected
    }

    @Test
    @Transactional
    void testDeleteByCalendar_NoEvents_ReturnsZero() {
        // When
        long deletedCount = eventRepository.deleteByCalendar(testCalendar.id);

        // Then
        assertEquals(0, deletedCount);
    }

    // ========== FIND BY DATE TESTS ==========

    @Test
    @Transactional
    void testFindByDate_Success() {
        // Given - Same date across different calendars
        createEvent(testCalendar, LocalDate.of(2025, 1, 1), "Calendar 1");
        createEvent(otherCalendar, LocalDate.of(2025, 1, 1), "Calendar 2");

        // When
        List<Event> events = eventRepository.findByDate(LocalDate.of(2025, 1, 1));

        // Then
        assertEquals(2, events.size());
    }

    @Test
    @Transactional
    void testFindByDate_NoEvents_ReturnsEmpty() {
        // When
        List<Event> events = eventRepository.findByDate(LocalDate.of(2025, 1, 1));

        // Then
        assertTrue(events.isEmpty());
    }

    // ========== SEARCH BY TEXT TESTS ==========

    @Test
    @Transactional
    void testSearchByText_Success() {
        // Given
        createEvent(testCalendar, LocalDate.of(2025, 1, 1), "New Year's Day");
        createEvent(testCalendar, LocalDate.of(2025, 7, 4), "Independence Day");
        createEvent(testCalendar, LocalDate.of(2025, 12, 25), "Christmas Day");

        // When
        List<Event> events = eventRepository.searchByText(testCalendar.id, "Day");

        // Then
        assertEquals(3, events.size()); // All contain "Day"
    }

    @Test
    @Transactional
    void testSearchByText_CaseInsensitive() {
        // Given
        createEvent(testCalendar, LocalDate.of(2025, 1, 1), "New Year");

        // When
        List<Event> events = eventRepository.searchByText(testCalendar.id, "year");

        // Then
        assertEquals(1, events.size());
        assertEquals("New Year", events.get(0).eventText);
    }

    @Test
    @Transactional
    void testSearchByText_PartialMatch() {
        // Given
        createEvent(testCalendar, LocalDate.of(2025, 1, 1), "Happy Birthday");

        // When
        List<Event> events = eventRepository.searchByText(testCalendar.id, "birth");

        // Then
        assertEquals(1, events.size());
    }

    @Test
    @Transactional
    void testSearchByText_NoMatches_ReturnsEmpty() {
        // Given
        createEvent(testCalendar, LocalDate.of(2025, 1, 1), "New Year");

        // When
        List<Event> events = eventRepository.searchByText(testCalendar.id, "christmas");

        // Then
        assertTrue(events.isEmpty());
    }

    @Test
    @Transactional
    void testSearchByText_IsolatesCalendars() {
        // Given
        createEvent(testCalendar, LocalDate.of(2025, 1, 1), "Test Event");
        createEvent(otherCalendar, LocalDate.of(2025, 1, 1), "Test Event in Other");

        // When
        List<Event> events = eventRepository.searchByText(testCalendar.id, "Test");

        // Then
        assertEquals(1, events.size());
        assertEquals("Test Event", events.get(0).eventText);
    }

    // ========== HELPER METHODS ==========

    private Event createEvent(UserCalendar calendar, LocalDate date, String text) {
        Event event = new Event();
        event.calendar = calendar;
        event.eventDate = date;
        event.eventText = text;
        event.persist();
        return event;
    }
}
