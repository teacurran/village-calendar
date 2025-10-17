package villagecompute.calendar.data.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import villagecompute.calendar.data.repositories.CalendarTemplateRepository;
import villagecompute.calendar.data.repositories.CalendarUserRepository;
import villagecompute.calendar.data.repositories.TestDataCleaner;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class UserCalendarTest {

    @Inject
    Validator validator;

    @Inject
    TestDataCleaner testDataCleaner;

    @Inject
    CalendarUserRepository calendarUserRepository;

    @Inject
    CalendarTemplateRepository templateRepository;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    jakarta.persistence.EntityManager entityManager;

    private CalendarUser testUser;
    private CalendarTemplate testTemplate;

    @BeforeEach
    @Transactional
    void setUp() {
        testDataCleaner.deleteAll();

        // Create test user
        testUser = new CalendarUser();
        testUser.oauthProvider = "GOOGLE";
        testUser.oauthSubject = "test-subject-calendar-test";
        testUser.email = "test@example.com";
        testUser.displayName = "Test User";
        testUser.isAdmin = false;
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

        // Flush to ensure entities are persisted
        entityManager.flush();
    }

    @Test
    @Transactional
    void testValidEntity_Success() {
        // Given
        UserCalendar calendar = createValidCalendar("Test Calendar", 2025);

        // When
        calendar.persist();
        entityManager.flush(); // Flush to generate ID and timestamps

        // Then
        assertNotNull(calendar.id);
        assertNotNull(calendar.created);
        assertNotNull(calendar.updated);
        assertEquals(0L, calendar.version);
    }

    @Test
    void testInvalidEntity_NullName() {
        // Given
        UserCalendar calendar = createValidCalendar("Test Calendar", 2025);
        calendar.name = null;

        // When
        Set<ConstraintViolation<UserCalendar>> violations = validator.validate(calendar);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<UserCalendar> violation = violations.iterator().next();
        assertEquals("name", violation.getPropertyPath().toString());
    }

    @Test
    void testInvalidEntity_NullYear() {
        // Given
        UserCalendar calendar = createValidCalendar("Test Calendar", 2025);
        calendar.year = null;

        // When
        Set<ConstraintViolation<UserCalendar>> violations = validator.validate(calendar);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<UserCalendar> violation = violations.iterator().next();
        assertEquals("year", violation.getPropertyPath().toString());
    }

    @Test
    void testInvalidEntity_NameTooLong() {
        // Given
        UserCalendar calendar = createValidCalendar("Test Calendar", 2025);
        calendar.name = "A".repeat(256); // Max is 255

        // When
        Set<ConstraintViolation<UserCalendar>> violations = validator.validate(calendar);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<UserCalendar> violation = violations.iterator().next();
        assertEquals("name", violation.getPropertyPath().toString());
    }

    @Test
    @Transactional
    void testJsonbSerialization() {
        // Given
        UserCalendar calendar = createValidCalendar("Test Calendar", 2025);
        ObjectNode jsonConfig = objectMapper.createObjectNode();
        jsonConfig.put("color", "blue");
        jsonConfig.put("fontSize", 12);
        jsonConfig.put("showMoonPhases", true);
        calendar.configuration = jsonConfig;
        calendar.persist();

        // When
        UserCalendar found = UserCalendar.findById(calendar.id);

        // Then
        assertNotNull(found.configuration);
        assertEquals("blue", found.configuration.get("color").asText());
        assertEquals(12, found.configuration.get("fontSize").asInt());
        assertTrue(found.configuration.get("showMoonPhases").asBoolean());
    }

    @Test
    @Transactional
    void testJsonbSerialization_NullJson() {
        // Given
        UserCalendar calendar = createValidCalendar("Test Calendar", 2025);
        calendar.configuration = null;
        calendar.persist();

        // When
        UserCalendar found = UserCalendar.findById(calendar.id);

        // Then
        assertNull(found.configuration);
    }

    @Test
    @Transactional
    void testFindBySession() {
        // Given
        UserCalendar cal1 = createSessionCalendar("session-123", "Cal 1", 2025);
        cal1.persist();
        UserCalendar cal2 = createSessionCalendar("session-123", "Cal 2", 2025);
        cal2.persist();
        UserCalendar cal3 = createSessionCalendar("session-456", "Cal 3", 2025);
        cal3.persist();

        // When
        List<UserCalendar> calendars = UserCalendar.findBySession("session-123").list();

        // Then
        assertEquals(2, calendars.size());
        assertTrue(calendars.stream().allMatch(c -> "session-123".equals(c.sessionId)));
    }

    @Test
    @Transactional
    void testFindByUserAndYear() {
        // Given
        UserCalendar cal1 = createValidCalendar("2025 Cal 1", 2025);
        cal1.persist();
        UserCalendar cal2 = createValidCalendar("2025 Cal 2", 2025);
        cal2.persist();
        UserCalendar cal3 = createValidCalendar("2026 Cal", 2026);
        cal3.persist();

        // When
        List<UserCalendar> calendars = UserCalendar.findByUserAndYear(testUser.id, 2025);

        // Then
        assertEquals(2, calendars.size());
        assertTrue(calendars.stream().allMatch(c -> c.year.equals(2025)));
    }

    @Test
    @Transactional
    void testFindByUser() {
        // Given
        UserCalendar cal1 = createValidCalendar("Cal 1", 2025);
        cal1.persist();
        UserCalendar cal2 = createValidCalendar("Cal 2", 2026);
        cal2.persist();
        UserCalendar cal3 = createValidCalendar("Cal 3", 2024);
        cal3.persist();

        // When
        List<UserCalendar> calendars = UserCalendar.findByUser(testUser.id).list();

        // Then
        assertEquals(3, calendars.size());
        // Should be ordered by year DESC
        assertEquals(2026, calendars.get(0).year);
        assertEquals(2025, calendars.get(1).year);
        assertEquals(2024, calendars.get(2).year);
    }

    @Test
    @Transactional
    void testFindBySessionAndName() {
        // Given
        UserCalendar cal1 = createSessionCalendar("session-123", "My Calendar", 2025);
        cal1.persist();
        UserCalendar cal2 = createSessionCalendar("session-123", "Other Calendar", 2025);
        cal2.persist();

        // When
        List<UserCalendar> calendars = UserCalendar.findBySessionAndName("session-123", "My Calendar").list();

        // Then
        assertEquals(1, calendars.size());
        assertEquals("My Calendar", calendars.get(0).name);
    }

    @Test
    @Transactional
    void testFindPublicById() {
        // Given
        UserCalendar publicCal = createValidCalendar("Public", 2025);
        publicCal.isPublic = true;
        publicCal.persist();

        UserCalendar privateCal = createValidCalendar("Private", 2025);
        privateCal.isPublic = false;
        privateCal.persist();

        // When
        UserCalendar foundPublic = UserCalendar.findPublicById(publicCal.id);
        UserCalendar foundPrivate = UserCalendar.findPublicById(privateCal.id);

        // Then
        assertNotNull(foundPublic);
        assertNull(foundPrivate);
    }

    @Test
    @Transactional
    void testFindPublicCalendars() {
        // Given
        UserCalendar pub1 = createValidCalendar("Public 1", 2025);
        pub1.isPublic = true;
        pub1.persist();

        UserCalendar pub2 = createValidCalendar("Public 2", 2025);
        pub2.isPublic = true;
        pub2.persist();

        UserCalendar priv = createValidCalendar("Private", 2025);
        priv.isPublic = false;
        priv.persist();

        // When
        List<UserCalendar> publicCalendars = UserCalendar.findPublicCalendars().list();

        // Then
        assertEquals(2, publicCalendars.size());
        assertTrue(publicCalendars.stream().allMatch(c -> c.isPublic));
    }

    @Test
    @Transactional
    void testCopyForSession() {
        // Given
        UserCalendar original = createValidCalendar("Original", 2025);
        ObjectNode config = objectMapper.createObjectNode();
        config.put("color", "red");
        original.configuration = config;
        original.generatedSvg = "<svg>test</svg>";
        original.persist();

        // When
        UserCalendar copy = original.copyForSession("new-session-123");

        // Then
        assertNull(copy.id); // Not persisted yet
        assertEquals("new-session-123", copy.sessionId);
        assertEquals("Original", copy.name);
        assertEquals(2025, copy.year);
        assertEquals("<svg>test</svg>", copy.generatedSvg);
        assertEquals("red", copy.configuration.get("color").asText());
        assertTrue(copy.isPublic);
    }

    @Test
    @Transactional
    void testRelationships_ManyToOneUser() {
        // Given
        UserCalendar calendar = createValidCalendar("Test Calendar", 2025);
        calendar.persist();

        // When
        UserCalendar found = UserCalendar.findById(calendar.id);

        // Then
        assertNotNull(found.user);
        assertEquals(testUser.id, found.user.id);
    }

    @Test
    @Transactional
    void testRelationships_ManyToOneTemplate() {
        // Given
        UserCalendar calendar = createValidCalendar("Test Calendar", 2025);
        calendar.persist();

        // When
        UserCalendar found = UserCalendar.findById(calendar.id);

        // Then
        assertNotNull(found.template);
        assertEquals(testTemplate.id, found.template.id);
    }

    @Test
    @Transactional
    void testRelationships_OneToManyOrders() {
        // Given
        UserCalendar calendar = createValidCalendar("Test Calendar", 2025);
        calendar.persist();

        CalendarOrder order = new CalendarOrder();
        order.user = testUser;
        order.calendar = calendar;
        order.quantity = 1;
        order.unitPrice = java.math.BigDecimal.valueOf(19.99);
        order.totalPrice = java.math.BigDecimal.valueOf(19.99);
        order.status = "PENDING";
        order.persist();

        entityManager.flush(); // Flush to ensure all entities are persisted
        entityManager.clear(); // Clear to force reload

        // When
        UserCalendar found = UserCalendar.findById(calendar.id);
        // Access the collection size to trigger lazy loading
        int size = found.orders.size();

        // Then
        assertNotNull(found.orders);
        assertEquals(1, size);
        assertEquals(1, found.orders.get(0).quantity);
    }

    @Test
    @Transactional
    void testRelationships_CascadeRemoveOrders() {
        // Given
        UserCalendar calendar = createValidCalendar("Test Calendar", 2025);
        calendar.persist();

        CalendarOrder order = new CalendarOrder();
        order.user = testUser;
        order.calendar = calendar;
        order.quantity = 1;
        order.unitPrice = java.math.BigDecimal.valueOf(19.99);
        order.totalPrice = java.math.BigDecimal.valueOf(19.99);
        order.status = "PENDING";
        order.persist();

        // Flush to ensure all entities are in managed state
        entityManager.flush();
        entityManager.clear(); // Clear persistence context to reload fresh

        // When - Reload calendar to ensure it's in managed state with proper relationships
        UserCalendar managedCalendar = UserCalendar.findById(calendar.id);
        managedCalendar.delete();
        entityManager.flush();

        // Then
        assertEquals(0, CalendarOrder.count());
    }

    private UserCalendar createValidCalendar(String name, Integer year) {
        UserCalendar calendar = new UserCalendar();
        calendar.user = testUser;
        calendar.name = name;
        calendar.year = year;
        calendar.isPublic = true;
        calendar.template = testTemplate;
        return calendar;
    }

    private UserCalendar createSessionCalendar(String sessionId, String name, Integer year) {
        UserCalendar calendar = new UserCalendar();
        calendar.sessionId = sessionId;
        calendar.name = name;
        calendar.year = year;
        calendar.isPublic = true;
        calendar.template = testTemplate;
        return calendar;
    }
}
