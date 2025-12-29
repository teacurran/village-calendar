package villagecompute.calendar.services;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.data.repositories.CalendarTemplateRepository;
import villagecompute.calendar.data.repositories.UserCalendarRepository;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Comprehensive unit tests for CalendarService. Tests all CRUD operations, authorization logic,
 * optimistic locking, and session conversion.
 */
@QuarkusTest
class CalendarServiceTest {

    @Inject CalendarService calendarService;

    @Inject UserCalendarRepository calendarRepository;

    @Inject CalendarTemplateRepository templateRepository;

    @Inject ObjectMapper objectMapper;

    private CalendarUser testUser;
    private CalendarUser adminUser;
    private CalendarUser otherUser;
    private CalendarTemplate testTemplate;

    @BeforeEach
    @Transactional
    void setUp() throws Exception {
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

        // Create test template
        testTemplate = new CalendarTemplate();
        testTemplate.name = "Test Template";
        testTemplate.description = "A test template";
        testTemplate.isActive = true;
        testTemplate.isFeatured = false;
        testTemplate.displayOrder = 1;
        testTemplate.configuration =
                objectMapper.createObjectNode().put("theme", "default").put("showMoonPhases", true);
        testTemplate.persist();
    }

    @AfterEach
    @Transactional
    void tearDown() {
        // Clean up test data
        UserCalendar.deleteAll();
        CalendarUser.deleteAll();
        CalendarTemplate.deleteAll();
    }

    // ========== CREATE CALENDAR TESTS ==========

    @Test
    void testCreateCalendar_WithTemplate_Success() {
        // Given
        String name = "My Calendar 2025";
        Integer year = 2025;

        // When
        UserCalendar calendar =
                calendarService.createCalendar(
                        name, year, testTemplate.id, null, true, testUser, null);

        // Then
        assertNotNull(calendar);
        assertNotNull(calendar.id);
        assertEquals(name, calendar.name);
        assertEquals(year, calendar.year);
        assertEquals(testUser.id, calendar.user.id);
        assertNull(calendar.sessionId);
        assertTrue(calendar.isPublic);
        assertEquals(testTemplate.id, calendar.template.id);
        assertNotNull(calendar.configuration);
        assertEquals(0L, calendar.version); // Version starts at 0
    }

    @Test
    void testCreateCalendar_BlankWithoutTemplate_Success() {
        // Given
        String name = "Blank Calendar";
        Integer year = 2024;
        JsonNode customConfig = objectMapper.createObjectNode().put("theme", "custom");

        // When
        UserCalendar calendar =
                calendarService.createCalendar(
                        name, year, null, customConfig, false, testUser, null);

        // Then
        assertNotNull(calendar);
        assertEquals(name, calendar.name);
        assertEquals(year, calendar.year);
        assertNull(calendar.template);
        assertEquals("custom", calendar.configuration.get("theme").asText());
        assertFalse(calendar.isPublic);
    }

    @Test
    void testCreateCalendar_GuestUser_Success() {
        // Given
        String sessionId = UUID.randomUUID().toString();
        String name = "Guest Calendar";
        Integer year = LocalDate.now().getYear();

        // When
        UserCalendar calendar =
                calendarService.createCalendar(name, year, null, null, true, null, sessionId);

        // Then
        assertNotNull(calendar);
        assertNull(calendar.user);
        assertEquals(sessionId, calendar.sessionId);
        assertEquals(name, calendar.name);
    }

    @Test
    void testCreateCalendar_InvalidName_ThrowsException() {
        // When & Then
        assertThrows(
                IllegalArgumentException.class,
                () -> calendarService.createCalendar(null, 2025, null, null, true, testUser, null));

        assertThrows(
                IllegalArgumentException.class,
                () -> calendarService.createCalendar("", 2025, null, null, true, testUser, null));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        calendarService.createCalendar(
                                "   ", 2025, null, null, true, testUser, null));
    }

    @Test
    void testCreateCalendar_InvalidYear_ThrowsException() {
        // When & Then
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        calendarService.createCalendar(
                                "Test", 999, null, null, true, testUser, null));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        calendarService.createCalendar(
                                "Test", 10000, null, null, true, testUser, null));
    }

    @Test
    void testCreateCalendar_NoUserOrSession_ThrowsException() {
        // When & Then
        assertThrows(
                IllegalArgumentException.class,
                () -> calendarService.createCalendar("Test", 2025, null, null, true, null, null));
    }

    @Test
    void testCreateCalendar_BothUserAndSession_ThrowsException() {
        // When & Then
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        calendarService.createCalendar(
                                "Test", 2025, null, null, true, testUser, "session-123"));
    }

    @Test
    void testCreateCalendar_TemplateNotFound_ThrowsException() {
        // Given
        UUID nonExistentTemplateId = UUID.randomUUID();

        // When & Then
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        calendarService.createCalendar(
                                "Test", 2025, nonExistentTemplateId, null, true, testUser, null));
    }

    @Test
    @Transactional
    void testCreateCalendar_InactiveTemplate_ThrowsException() {
        // Given - Fetch the template in this transaction and modify it
        CalendarTemplate template = CalendarTemplate.findById(testTemplate.id);
        template.isActive = false;
        template.persist();

        // When & Then
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        calendarService.createCalendar(
                                "Test", 2025, template.id, null, true, testUser, null));
    }

    // ========== UPDATE CALENDAR TESTS ==========

    @Test
    @Transactional
    void testUpdateCalendar_Success() {
        // Given
        UserCalendar calendar =
                calendarService.createCalendar(
                        "Original Name", 2025, null, null, true, testUser, null);
        UUID calendarId = calendar.id;
        Long originalVersion = calendar.version;

        // When
        JsonNode newConfig = objectMapper.createObjectNode().put("updated", true);
        UserCalendar updated =
                calendarService.updateCalendar(
                        calendarId, "Updated Name", newConfig, false, testUser);

        // Then
        assertEquals("Updated Name", updated.name);
        assertEquals(newConfig, updated.configuration);
        assertFalse(updated.isPublic);
        // Fetch fresh instance to check version was incremented
        UserCalendar refreshed = UserCalendar.findById(calendarId);
        assertTrue(
                refreshed.version > originalVersion,
                "Version should increment after update (original: "
                        + originalVersion
                        + ", new: "
                        + refreshed.version
                        + ")");
    }

    @Test
    @Transactional
    void testUpdateCalendar_PartialUpdate_Success() {
        // Given
        UserCalendar calendar =
                calendarService.createCalendar("Original", 2025, null, null, true, testUser, null);

        // When - Update only name
        UserCalendar updated =
                calendarService.updateCalendar(calendar.id, "New Name", null, null, testUser);

        // Then
        assertEquals("New Name", updated.name);
        assertTrue(updated.isPublic); // Unchanged
    }

    @Test
    @Transactional
    void testUpdateCalendar_NotFound_ThrowsException() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When & Then
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        calendarService.updateCalendar(
                                nonExistentId, "New Name", null, null, testUser));
    }

    @Test
    @Transactional
    void testUpdateCalendar_UnauthorizedUser_ThrowsException() {
        // Given
        UserCalendar calendar =
                calendarService.createCalendar("Test", 2025, null, null, true, testUser, null);

        // When & Then
        assertThrows(
                SecurityException.class,
                () -> calendarService.updateCalendar(calendar.id, "Hacked", null, null, otherUser));
    }

    @Test
    @Transactional
    void testUpdateCalendar_AdminCanUpdateAnyCalendar_Success() {
        // Given
        UserCalendar calendar =
                calendarService.createCalendar("Test", 2025, null, null, true, testUser, null);

        // When
        UserCalendar updated =
                calendarService.updateCalendar(calendar.id, "Admin Updated", null, null, adminUser);

        // Then
        assertEquals("Admin Updated", updated.name);
    }

    @Test
    @Transactional
    void testUpdateCalendar_NoAuth_ThrowsException() {
        // Given
        UserCalendar calendar =
                calendarService.createCalendar("Test", 2025, null, null, true, testUser, null);

        // When & Then
        assertThrows(
                SecurityException.class,
                () -> calendarService.updateCalendar(calendar.id, "Hacked", null, null, null));
    }

    // ========== DELETE CALENDAR TESTS ==========

    @Test
    @Transactional
    void testDeleteCalendar_Success() {
        // Given
        UserCalendar calendar =
                calendarService.createCalendar("To Delete", 2025, null, null, true, testUser, null);
        UUID calendarId = calendar.id;

        // When
        boolean deleted = calendarService.deleteCalendar(calendarId, testUser);

        // Then
        assertTrue(deleted);
        assertNull(UserCalendar.findById(calendarId));
    }

    @Test
    @Transactional
    void testDeleteCalendar_NotFound_ThrowsException() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When & Then
        assertThrows(
                IllegalArgumentException.class,
                () -> calendarService.deleteCalendar(nonExistentId, testUser));
    }

    @Test
    @Transactional
    void testDeleteCalendar_UnauthorizedUser_ThrowsException() {
        // Given
        UserCalendar calendar =
                calendarService.createCalendar("Test", 2025, null, null, true, testUser, null);

        // When & Then
        assertThrows(
                SecurityException.class,
                () -> calendarService.deleteCalendar(calendar.id, otherUser));
    }

    @Test
    @Transactional
    void testDeleteCalendar_AdminCanDelete_Success() {
        // Given
        UserCalendar calendar =
                calendarService.createCalendar("Test", 2025, null, null, true, testUser, null);

        // When
        boolean deleted = calendarService.deleteCalendar(calendar.id, adminUser);

        // Then
        assertTrue(deleted);
    }

    // ========== GET CALENDAR TESTS ==========

    @Test
    @Transactional
    void testGetCalendar_OwnerAccess_Success() {
        // Given
        UserCalendar calendar =
                calendarService.createCalendar("Test", 2025, null, null, true, testUser, null);

        // When
        UserCalendar fetched = calendarService.getCalendar(calendar.id, testUser);

        // Then
        assertNotNull(fetched);
        assertEquals(calendar.id, fetched.id);
    }

    @Test
    @Transactional
    void testGetCalendar_PublicCalendar_AnyoneCanRead() {
        // Given
        UserCalendar calendar =
                calendarService.createCalendar("Public", 2025, null, null, true, testUser, null);

        // When
        UserCalendar fetched = calendarService.getCalendar(calendar.id, otherUser);

        // Then
        assertNotNull(fetched);
        assertEquals(calendar.id, fetched.id);
    }

    @Test
    @Transactional
    void testGetCalendar_PrivateCalendar_UnauthorizedThrowsException() {
        // Given
        UserCalendar calendar =
                calendarService.createCalendar("Private", 2025, null, null, false, testUser, null);

        // When & Then
        assertThrows(
                SecurityException.class, () -> calendarService.getCalendar(calendar.id, otherUser));
    }

    @Test
    @Transactional
    void testGetCalendar_PrivateCalendar_AnonymousThrowsException() {
        // Given
        UserCalendar calendar =
                calendarService.createCalendar("Private", 2025, null, null, false, testUser, null);

        // When & Then
        assertThrows(SecurityException.class, () -> calendarService.getCalendar(calendar.id, null));
    }

    @Test
    @Transactional
    void testGetCalendar_AdminCanAccessPrivate_Success() {
        // Given
        UserCalendar calendar =
                calendarService.createCalendar("Private", 2025, null, null, false, testUser, null);

        // When
        UserCalendar fetched = calendarService.getCalendar(calendar.id, adminUser);

        // Then
        assertNotNull(fetched);
    }

    @Test
    void testGetCalendar_NotFound_ThrowsException() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When & Then
        assertThrows(
                IllegalArgumentException.class,
                () -> calendarService.getCalendar(nonExistentId, testUser));
    }

    // ========== LIST CALENDARS TESTS ==========

    @Test
    @Transactional
    void testListCalendars_UserOwnCalendars_Success() {
        // Given
        calendarService.createCalendar("Cal 1", 2024, null, null, true, testUser, null);
        calendarService.createCalendar("Cal 2", 2025, null, null, true, testUser, null);
        calendarService.createCalendar("Cal 3", 2025, null, null, true, testUser, null);

        // When
        List<UserCalendar> calendars =
                calendarService.listCalendars(testUser.id, null, 0, 10, testUser);

        // Then
        assertEquals(3, calendars.size());
    }

    @Test
    @Transactional
    void testListCalendars_FilterByYear_Success() {
        // Given
        calendarService.createCalendar("Cal 2024", 2024, null, null, true, testUser, null);
        calendarService.createCalendar("Cal 2025-1", 2025, null, null, true, testUser, null);
        calendarService.createCalendar("Cal 2025-2", 2025, null, null, true, testUser, null);

        // When
        List<UserCalendar> calendars =
                calendarService.listCalendars(testUser.id, 2025, 0, 10, testUser);

        // Then
        assertEquals(2, calendars.size());
        assertTrue(calendars.stream().allMatch(c -> c.year == 2025));
    }

    @Test
    @Transactional
    void testListCalendars_Pagination_Success() {
        // Given
        for (int i = 0; i < 5; i++) {
            calendarService.createCalendar("Cal " + i, 2025, null, null, true, testUser, null);
        }

        // When - First page
        List<UserCalendar> page1 = calendarService.listCalendars(testUser.id, null, 0, 2, testUser);
        // When - Second page
        List<UserCalendar> page2 = calendarService.listCalendars(testUser.id, null, 1, 2, testUser);

        // Then
        assertEquals(2, page1.size());
        assertEquals(2, page2.size());
    }

    @Test
    @Transactional
    void testListCalendars_UnauthorizedUser_ThrowsException() {
        // Given
        calendarService.createCalendar("Test", 2025, null, null, true, testUser, null);

        // When & Then
        assertThrows(
                SecurityException.class,
                () -> calendarService.listCalendars(testUser.id, null, 0, 10, otherUser));
    }

    @Test
    @Transactional
    void testListCalendars_AdminCanListAnyUser_Success() {
        // Given
        calendarService.createCalendar("Test", 2025, null, null, true, testUser, null);

        // When
        List<UserCalendar> calendars =
                calendarService.listCalendars(testUser.id, null, 0, 10, adminUser);

        // Then
        assertEquals(1, calendars.size());
    }

    @Test
    void testListCalendars_NoAuth_ThrowsException() {
        // When & Then
        assertThrows(
                SecurityException.class,
                () -> calendarService.listCalendars(testUser.id, null, 0, 10, null));
    }

    // ========== SESSION CONVERSION TESTS ==========

    @Test
    @Transactional
    void testConvertSessionToUser_Success() {
        // Given
        String sessionId = "session-" + UUID.randomUUID();
        calendarService.createCalendar("Guest 1", 2025, null, null, true, null, sessionId);
        calendarService.createCalendar("Guest 2", 2024, null, null, true, null, sessionId);

        // When
        int converted = calendarService.convertSessionToUser(sessionId, testUser);

        // Then
        assertEquals(2, converted);

        // Verify calendars are now owned by user
        List<UserCalendar> userCalendars = calendarRepository.findByUser(testUser.id);
        assertEquals(2, userCalendars.size());
        assertTrue(userCalendars.stream().allMatch(c -> c.user.id.equals(testUser.id)));
        assertTrue(userCalendars.stream().allMatch(c -> c.sessionId == null));

        // Verify no calendars remain for session
        List<UserCalendar> sessionCalendars = calendarRepository.findBySession(sessionId);
        assertEquals(0, sessionCalendars.size());
    }

    @Test
    @Transactional
    void testConvertSessionToUser_NoCalendars_ReturnsZero() {
        // Given
        String sessionId = "empty-session";

        // When
        int converted = calendarService.convertSessionToUser(sessionId, testUser);

        // Then
        assertEquals(0, converted);
    }

    @Test
    void testConvertSessionToUser_NullSessionId_ThrowsException() {
        // When & Then
        assertThrows(
                IllegalArgumentException.class,
                () -> calendarService.convertSessionToUser(null, testUser));
    }

    @Test
    void testConvertSessionToUser_NullUser_ThrowsException() {
        // When & Then
        assertThrows(
                IllegalArgumentException.class,
                () -> calendarService.convertSessionToUser("session-123", null));
    }

    // ========== PUBLIC CALENDAR TESTS ==========

    @Test
    @Transactional
    void testFindPublicCalendar_PublicExists_ReturnsCalendar() {
        // Given
        UserCalendar calendar =
                calendarService.createCalendar("Public", 2025, null, null, true, testUser, null);

        // When
        UserCalendar found = calendarService.findPublicCalendar(calendar.id);

        // Then
        assertNotNull(found);
        assertEquals(calendar.id, found.id);
    }

    @Test
    @Transactional
    void testFindPublicCalendar_PrivateCalendar_ReturnsNull() {
        // Given
        UserCalendar calendar =
                calendarService.createCalendar("Private", 2025, null, null, false, testUser, null);

        // When
        UserCalendar found = calendarService.findPublicCalendar(calendar.id);

        // Then
        assertNull(found);
    }

    @Test
    void testFindPublicCalendar_NotFound_ReturnsNull() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When
        UserCalendar found = calendarService.findPublicCalendar(nonExistentId);

        // Then
        assertNull(found);
    }
}
