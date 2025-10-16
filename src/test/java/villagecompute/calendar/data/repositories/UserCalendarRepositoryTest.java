package villagecompute.calendar.data.repositories;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class UserCalendarRepositoryTest {

    @Inject
    TestDataCleaner testDataCleaner;

    @Inject
    UserCalendarRepository userCalendarRepository;

    @Inject
    CalendarUserRepository calendarUserRepository;

    @Inject
    CalendarTemplateRepository templateRepository;

    @Inject
    ObjectMapper objectMapper;

    private CalendarUser testUser;
    private CalendarTemplate testTemplate;

    @BeforeEach
    @Transactional
    void setUp() {
        testDataCleaner.deleteAll();

        // Create test user
        testUser = new CalendarUser();
        testUser.oauthProvider = "GOOGLE";
        testUser.oauthSubject = "test-subject";
        testUser.email = "test@example.com";
        testUser.displayName = "Test User";
        calendarUserRepository.persist(testUser);

        // Create test template
        testTemplate = new CalendarTemplate();
        testTemplate.name = "Test Template";
        testTemplate.isActive = true;
        testTemplate.displayOrder = 1;
        ObjectNode config = objectMapper.createObjectNode();
        config.put("theme", "test");
        testTemplate.configuration = config;
        templateRepository.persist(testTemplate);
    }

    @Test
    @Transactional
    void testFindByUserAndYear() {
        // Given
        userCalendarRepository.persist(createUserCalendar(testUser, 2025, "Calendar 2025"));
        userCalendarRepository.persist(createUserCalendar(testUser, 2026, "Calendar 2026"));
        userCalendarRepository.persist(createUserCalendar(testUser, 2025, "Another 2025"));

        // When
        List<UserCalendar> calendars = userCalendarRepository.findByUserAndYear(testUser.id, 2025);

        // Then
        assertEquals(2, calendars.size());
        assertTrue(calendars.stream().allMatch(c -> c.year.equals(2025)));
    }

    @Test
    @Transactional
    void testFindByUser() {
        // Given
        userCalendarRepository.persist(createUserCalendar(testUser, 2025, "Cal 1"));
        userCalendarRepository.persist(createUserCalendar(testUser, 2026, "Cal 2"));
        userCalendarRepository.persist(createUserCalendar(testUser, 2024, "Cal 3"));

        // When
        List<UserCalendar> calendars = userCalendarRepository.findByUser(testUser.id);

        // Then
        assertEquals(3, calendars.size());
        // Should be ordered by year DESC
        assertEquals(2026, calendars.get(0).year);
        assertEquals(2025, calendars.get(1).year);
        assertEquals(2024, calendars.get(2).year);
    }

    @Test
    @Transactional
    void testFindBySession() {
        // Given
        UserCalendar cal1 = createSessionCalendar("session-123", 2025, "Session Cal 1");
        UserCalendar cal2 = createSessionCalendar("session-123", 2025, "Session Cal 2");
        UserCalendar cal3 = createSessionCalendar("session-456", 2025, "Other Session");

        userCalendarRepository.persist(cal1);
        userCalendarRepository.persist(cal2);
        userCalendarRepository.persist(cal3);

        // When
        List<UserCalendar> calendars = userCalendarRepository.findBySession("session-123");

        // Then
        assertEquals(2, calendars.size());
        assertTrue(calendars.stream().allMatch(c -> "session-123".equals(c.sessionId)));
    }

    @Test
    @Transactional
    void testFindPublicById() {
        // Given
        UserCalendar publicCal = createUserCalendar(testUser, 2025, "Public");
        publicCal.isPublic = true;
        userCalendarRepository.persist(publicCal);

        UserCalendar privateCal = createUserCalendar(testUser, 2025, "Private");
        privateCal.isPublic = false;
        userCalendarRepository.persist(privateCal);

        // When
        Optional<UserCalendar> foundPublic = userCalendarRepository.findPublicById(publicCal.id);
        Optional<UserCalendar> foundPrivate = userCalendarRepository.findPublicById(privateCal.id);

        // Then
        assertTrue(foundPublic.isPresent());
        assertFalse(foundPrivate.isPresent());
    }

    @Test
    @Transactional
    void testFindPublicCalendars() {
        // Given
        UserCalendar pub1 = createUserCalendar(testUser, 2025, "Public 1");
        pub1.isPublic = true;
        userCalendarRepository.persist(pub1);

        UserCalendar pub2 = createUserCalendar(testUser, 2025, "Public 2");
        pub2.isPublic = true;
        userCalendarRepository.persist(pub2);

        UserCalendar priv = createUserCalendar(testUser, 2025, "Private");
        priv.isPublic = false;
        userCalendarRepository.persist(priv);

        // When
        List<UserCalendar> publicCalendars = userCalendarRepository.findPublicCalendars(10);

        // Then
        assertEquals(2, publicCalendars.size());
        assertTrue(publicCalendars.stream().allMatch(c -> c.isPublic));
    }

    @Test
    @Transactional
    void testFindByTemplate() {
        // Given
        userCalendarRepository.persist(createUserCalendar(testUser, 2025, "Cal 1"));
        userCalendarRepository.persist(createUserCalendar(testUser, 2025, "Cal 2"));

        // When
        List<UserCalendar> calendars = userCalendarRepository.findByTemplate(testTemplate.id);

        // Then
        assertEquals(2, calendars.size());
        assertTrue(calendars.stream().allMatch(c -> c.template.id.equals(testTemplate.id)));
    }

    @Test
    @Transactional
    void testFindByYear() {
        // Given
        userCalendarRepository.persist(createUserCalendar(testUser, 2025, "2025 Cal 1"));
        userCalendarRepository.persist(createUserCalendar(testUser, 2025, "2025 Cal 2"));
        userCalendarRepository.persist(createUserCalendar(testUser, 2026, "2026 Cal"));

        // When
        List<UserCalendar> calendars2025 = userCalendarRepository.findByYear(2025);

        // Then
        assertEquals(2, calendars2025.size());
        assertTrue(calendars2025.stream().allMatch(c -> c.year.equals(2025)));
    }

    private UserCalendar createUserCalendar(CalendarUser user, Integer year, String name) {
        UserCalendar calendar = new UserCalendar();
        calendar.user = user;
        calendar.name = name;
        calendar.year = year;
        calendar.isPublic = true;
        calendar.template = testTemplate;
        return calendar;
    }

    private UserCalendar createSessionCalendar(String sessionId, Integer year, String name) {
        UserCalendar calendar = new UserCalendar();
        calendar.sessionId = sessionId;
        calendar.name = name;
        calendar.year = year;
        calendar.isPublic = true;
        calendar.template = testTemplate;
        return calendar;
    }
}
