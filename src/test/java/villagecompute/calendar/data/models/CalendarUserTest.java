package villagecompute.calendar.data.models;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import villagecompute.calendar.data.repositories.TestDataCleaner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class CalendarUserTest {

    @Inject
    Validator validator;

    @Inject
    TestDataCleaner testDataCleaner;

    @BeforeEach
    @Transactional
    void setUp() {
        testDataCleaner.deleteAll();
    }

    @Test
    @Transactional
    void testValidEntity_Success() {
        // Given
        CalendarUser user = createValidUser("user1@test.com");

        // When
        user.persist();

        // Then
        assertNotNull(user.id);
        assertNotNull(user.created);
        assertNotNull(user.updated);
        assertEquals(0L, user.version);
    }

    @Test
    void testInvalidEntity_NullOAuthProvider() {
        // Given
        CalendarUser user = createValidUser("user@test.com");
        user.oauthProvider = null;

        // When
        Set<ConstraintViolation<CalendarUser>> violations = validator.validate(user);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<CalendarUser> violation = violations.iterator().next();
        assertEquals("oauthProvider", violation.getPropertyPath().toString());
    }

    @Test
    void testInvalidEntity_NullOAuthSubject() {
        // Given
        CalendarUser user = createValidUser("user@test.com");
        user.oauthSubject = null;

        // When
        Set<ConstraintViolation<CalendarUser>> violations = validator.validate(user);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<CalendarUser> violation = violations.iterator().next();
        assertEquals("oauthSubject", violation.getPropertyPath().toString());
    }

    @Test
    void testInvalidEntity_NullEmail() {
        // Given
        CalendarUser user = createValidUser("user@test.com");
        user.email = null;

        // When
        Set<ConstraintViolation<CalendarUser>> violations = validator.validate(user);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<CalendarUser> violation = violations.iterator().next();
        assertEquals("email", violation.getPropertyPath().toString());
    }

    @Test
    void testInvalidEntity_InvalidEmailFormat() {
        // Given
        CalendarUser user = createValidUser("invalid-email");

        // When
        Set<ConstraintViolation<CalendarUser>> violations = validator.validate(user);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<CalendarUser> violation = violations.iterator().next();
        assertEquals("email", violation.getPropertyPath().toString());
    }

    @Test
    void testInvalidEntity_OAuthProviderTooLong() {
        // Given
        CalendarUser user = createValidUser("user@test.com");
        user.oauthProvider = "A".repeat(51); // Max is 50

        // When
        Set<ConstraintViolation<CalendarUser>> violations = validator.validate(user);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<CalendarUser> violation = violations.iterator().next();
        assertEquals("oauthProvider", violation.getPropertyPath().toString());
    }

    @Test
    void testInvalidEntity_EmailTooLong() {
        // Given
        CalendarUser user = createValidUser("user@test.com");
        user.email = "a".repeat(250) + "@test.com"; // Max is 255

        // When
        Set<ConstraintViolation<CalendarUser>> violations = validator.validate(user);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<CalendarUser> violation = violations.iterator().next();
        assertEquals("email", violation.getPropertyPath().toString());
    }

    @Test
    @Transactional
    void testFindByOAuthSubject() {
        // Given
        CalendarUser user = createValidUser("user@test.com");
        user.persist();

        // When
        Optional<CalendarUser> found = CalendarUser.findByOAuthSubject("GOOGLE", "test-subject");

        // Then
        assertTrue(found.isPresent());
        assertEquals(user.id, found.get().id);
        assertEquals("user@test.com", found.get().email);
    }

    @Test
    @Transactional
    void testFindByOAuthSubject_NotFound() {
        // Given
        CalendarUser user = createValidUser("user@test.com");
        user.persist();

        // When
        Optional<CalendarUser> found = CalendarUser.findByOAuthSubject("GOOGLE", "wrong-subject");

        // Then
        assertFalse(found.isPresent());
    }

    @Test
    @Transactional
    void testFindByEmail() {
        // Given
        CalendarUser user = createValidUser("user@test.com");
        user.persist();

        // When
        Optional<CalendarUser> found = CalendarUser.findByEmail("user@test.com");

        // Then
        assertTrue(found.isPresent());
        assertEquals(user.id, found.get().id);
    }

    @Test
    @Transactional
    void testFindByEmail_NotFound() {
        // Given
        CalendarUser user = createValidUser("user@test.com");
        user.persist();

        // When
        Optional<CalendarUser> found = CalendarUser.findByEmail("wrong@test.com");

        // Then
        assertFalse(found.isPresent());
    }

    @Test
    @Transactional
    void testFindActiveUsersSince() {
        // Given
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        CalendarUser activeUser1 = createValidUser("active1@test.com");
        activeUser1.lastLoginAt = now;
        activeUser1.persist();

        CalendarUser activeUser2 = createValidUser("active2@test.com");
        activeUser2.lastLoginAt = yesterday;
        activeUser2.persist();

        CalendarUser inactiveUser = createValidUser("inactive@test.com");
        inactiveUser.lastLoginAt = twoDaysAgo;
        inactiveUser.persist();

        // When
        List<CalendarUser> activeUsers = CalendarUser.findActiveUsersSince(yesterday).list();

        // Then
        assertEquals(2, activeUsers.size());
        // Should be ordered by lastLoginAt DESC
        assertEquals(activeUser1.id, activeUsers.get(0).id);
        assertEquals(activeUser2.id, activeUsers.get(1).id);
    }

    @Test
    @Transactional
    void testUpdateLastLogin() {
        // Given
        CalendarUser user = createValidUser("user@test.com");
        user.lastLoginAt = null;
        user.persist();

        // When
        user.updateLastLogin();

        // Then
        assertNotNull(user.lastLoginAt);
        assertTrue(user.lastLoginAt.isAfter(Instant.now().minus(5, ChronoUnit.SECONDS)));
    }

    @Test
    @Transactional
    void testHasAdminUsers_True() {
        // Given
        CalendarUser adminUser = createValidUser("admin@test.com");
        adminUser.isAdmin = true;
        adminUser.persist();

        // When
        boolean hasAdmins = CalendarUser.hasAdminUsers();

        // Then
        assertTrue(hasAdmins);
    }

    @Test
    @Transactional
    void testHasAdminUsers_False() {
        // Given
        CalendarUser regularUser = createValidUser("user@test.com");
        regularUser.isAdmin = false;
        regularUser.persist();

        // When
        boolean hasAdmins = CalendarUser.hasAdminUsers();

        // Then
        assertFalse(hasAdmins);
    }

    @Test
    @Transactional
    void testFindAdminUsers() {
        // Given
        CalendarUser admin1 = createValidUser("admin1@test.com");
        admin1.isAdmin = true;
        admin1.persist();

        CalendarUser admin2 = createValidUser("admin2@test.com");
        admin2.isAdmin = true;
        admin2.persist();

        CalendarUser regularUser = createValidUser("user@test.com");
        regularUser.isAdmin = false;
        regularUser.persist();

        // When
        List<CalendarUser> admins = CalendarUser.findAdminUsers().list();

        // Then
        assertEquals(2, admins.size());
        assertTrue(admins.stream().allMatch(u -> u.isAdmin));
    }

    @Test
    @Transactional
    void testRelationships_CascadePersist() {
        // Given
        CalendarUser user = createValidUser("user@test.com");
        user.persist();

        UserCalendar calendar = new UserCalendar();
        calendar.user = user;
        calendar.name = "Test Calendar";
        calendar.year = 2025;
        calendar.persist();

        // When
        CalendarUser foundUser = CalendarUser.findById(user.id);

        // Then
        assertNotNull(foundUser);
        assertNotNull(foundUser.calendars);
        assertEquals(1, foundUser.calendars.size());
        assertEquals("Test Calendar", foundUser.calendars.get(0).name);
    }

    @Test
    @Transactional
    void testRelationships_CascadeRemove() {
        // Given
        CalendarUser user = createValidUser("user@test.com");
        user.persist();

        UserCalendar calendar = new UserCalendar();
        calendar.user = user;
        calendar.name = "Test Calendar";
        calendar.year = 2025;
        calendar.persist();

        // When
        user.delete();

        // Then
        assertEquals(0, UserCalendar.count());
    }

    private CalendarUser createValidUser(String email) {
        CalendarUser user = new CalendarUser();
        user.oauthProvider = "GOOGLE";
        user.oauthSubject = "test-subject";
        user.email = email;
        user.displayName = "Test User";
        user.isAdmin = false;
        return user;
    }
}
