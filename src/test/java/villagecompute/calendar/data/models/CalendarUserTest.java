package villagecompute.calendar.data.models;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import villagecompute.calendar.data.repositories.TestDataCleaner;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class CalendarUserTest {

    @Inject
    Validator validator;

    @Inject
    TestDataCleaner testDataCleaner;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    jakarta.persistence.EntityManager entityManager;

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
        entityManager.flush(); // Flush to generate ID and timestamps

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
        // Create email with 256 chars (max is 255): 246 chars + "@test.com" (9 chars) + 1 extra =
        // 256
        String localPart = "a".repeat(247); // 247 chars
        user.email = localPart + "@test.com"; // Total = 247 + 9 = 256 chars (violates @Size max=255)

        // When
        Set<ConstraintViolation<CalendarUser>> violations = validator.validate(user);

        // Then
        assertTrue(violations.size() >= 1); // Should have at least @Size violation, might also have @Email
        // violation
        boolean hasSizeViolation = violations.stream().anyMatch(v -> "email".equals(v.getPropertyPath().toString()));
        assertTrue(hasSizeViolation);
    }

    @Test
    @Transactional
    void testFindByOAuthSubject() {
        // Given
        CalendarUser user = createValidUser("user@test.com");
        user.persist();

        // When
        Optional<CalendarUser> found = CalendarUser.findByOAuthSubject("GOOGLE", "test-subject-user@test.com");

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
        // Truncate to microseconds since H2 doesn't preserve nanosecond precision
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
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

        // Create template (required FK for calendar)
        CalendarTemplate template = new CalendarTemplate();
        template.name = "Test Template";
        template.isActive = true;
        template.isFeatured = false;
        template.displayOrder = 1;
        ObjectNode config = objectMapper.createObjectNode();
        template.configuration = config;
        template.persist();

        UserCalendar calendar = new UserCalendar();
        calendar.user = user;
        calendar.template = template;
        calendar.name = "Test Calendar";
        calendar.year = 2025;
        calendar.persist();

        // Flush to ensure all entities are persisted
        entityManager.flush();
        entityManager.clear(); // Clear to force reload

        // When
        CalendarUser foundUser = CalendarUser.findById(user.id);
        // Access the collection size to trigger lazy loading
        int size = foundUser.calendars.size();

        // Then
        assertNotNull(foundUser);
        assertNotNull(foundUser.calendars);
        assertEquals(1, size);
        assertEquals("Test Calendar", foundUser.calendars.get(0).name);
    }

    @Test
    @Transactional
    void testRelationships_CascadeRemove() {
        // Given
        CalendarUser user = createValidUser("user@test.com");
        user.persist();

        // Create template (required FK for calendar)
        CalendarTemplate template = new CalendarTemplate();
        template.name = "Test Template";
        template.isActive = true;
        template.isFeatured = false;
        template.displayOrder = 1;
        ObjectNode config = objectMapper.createObjectNode();
        template.configuration = config;
        template.persist();

        UserCalendar calendar = new UserCalendar();
        calendar.user = user;
        calendar.template = template;
        calendar.name = "Test Calendar";
        calendar.year = 2025;
        calendar.persist();

        // Flush to ensure all entities are in managed state
        entityManager.flush();
        entityManager.clear(); // Clear persistence context to reload fresh

        // When - Reload user to ensure it's in managed state with proper relationships
        CalendarUser managedUser = CalendarUser.findById(user.id);
        managedUser.delete();
        entityManager.flush();

        // Then
        assertEquals(0, UserCalendar.count());
    }

    @Test
    @Transactional
    void testAllFields_SetAndRetrieve() {
        // Given - Create user with ALL fields populated
        CalendarUser user = new CalendarUser();
        user.oauthProvider = "GITHUB";
        user.oauthSubject = "github-subject-123";
        user.email = "complete@test.com";
        user.displayName = "Complete Test User";
        user.profileImageUrl = "https://example.com/profile.jpg"; // Often missed!
        user.lastLoginAt = Instant.now().minus(1, ChronoUnit.HOURS);
        user.isAdmin = true;

        // When
        user.persist();
        entityManager.flush();
        entityManager.clear();

        // Then - Verify ALL fields persisted and can be retrieved
        CalendarUser found = CalendarUser.findById(user.id);
        assertNotNull(found);
        assertEquals("GITHUB", found.oauthProvider);
        assertEquals("github-subject-123", found.oauthSubject);
        assertEquals("complete@test.com", found.email);
        assertEquals("Complete Test User", found.displayName);
        assertEquals("https://example.com/profile.jpg", found.profileImageUrl);
        assertNotNull(found.lastLoginAt);
        assertTrue(found.isAdmin);
        assertNotNull(found.created);
        assertNotNull(found.updated);
        assertEquals(0L, found.version);
    }

    @Test
    @Transactional
    void testUpdate_ModifiesUpdatedTimestamp() {
        // Given
        CalendarUser user = createValidUser("update@test.com");
        user.persist();
        entityManager.flush();
        Instant originalUpdated = user.updated;

        // Wait to ensure timestamp changes
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
        }

        // When
        user.displayName = "Modified Display Name";
        user.profileImageUrl = "https://example.com/new-photo.jpg";
        user.persist();
        entityManager.flush();

        // Then
        assertTrue(user.updated.isAfter(originalUpdated));
        assertEquals("Modified Display Name", user.displayName);
        assertEquals("https://example.com/new-photo.jpg", user.profileImageUrl);
        assertEquals(1L, user.version); // Optimistic locking version incremented
    }

    @Test
    @Transactional
    void testDelete_RemovesEntity() {
        // Given
        CalendarUser user = createValidUser("delete@test.com");
        user.persist();
        java.util.UUID userId = user.id;
        entityManager.flush();

        // When
        user.delete();
        entityManager.flush();

        // Then
        assertNull(CalendarUser.findById(userId));
        assertEquals(0, CalendarUser.count());
    }

    @Test
    @Transactional
    void testListAll() {
        // Given
        createValidUser("user1@test.com").persist();
        createValidUser("user2@test.com").persist();
        createValidUser("user3@test.com").persist();
        entityManager.flush();

        // When
        List<CalendarUser> allUsers = CalendarUser.listAll();

        // Then
        assertEquals(3, allUsers.size());
    }

    @Test
    @Transactional
    void testCount() {
        // Given
        createValidUser("user1@test.com").persist();
        createValidUser("user2@test.com").persist();
        entityManager.flush();

        // When
        long count = CalendarUser.count();

        // Then
        assertEquals(2, count);
    }

    @Test
    @Transactional
    void testFindById() {
        // Given
        CalendarUser user = createValidUser("findme@test.com");
        user.persist();
        entityManager.flush();

        // When
        CalendarUser found = CalendarUser.findById(user.id);

        // Then
        assertNotNull(found);
        assertEquals(user.id, found.id);
        assertEquals("findme@test.com", found.email);
    }

    @Test
    @Transactional
    void testProfileImageUrl_Optional() {
        // Given - User without profile image URL
        CalendarUser user = createValidUser("no-image@test.com");
        user.profileImageUrl = null;
        user.persist();
        entityManager.flush();

        // When
        CalendarUser found = CalendarUser.findById(user.id);

        // Then
        assertNull(found.profileImageUrl);
    }

    @Test
    void testValidation_ProfileImageUrlTooLong() {
        // Given
        CalendarUser user = createValidUser("user@test.com");
        user.profileImageUrl = "https://example.com/" + "a".repeat(500); // Exceeds 500 char limit

        // When
        Set<ConstraintViolation<CalendarUser>> violations = validator.validate(user);

        // Then
        assertTrue(violations.size() >= 1);
        boolean hasViolation = violations.stream()
                .anyMatch(v -> "profileImageUrl".equals(v.getPropertyPath().toString()));
        assertTrue(hasViolation);
    }

    @Test
    void testValidation_DisplayNameTooLong() {
        // Given
        CalendarUser user = createValidUser("user@test.com");
        user.displayName = "A".repeat(256); // Max is 255

        // When
        Set<ConstraintViolation<CalendarUser>> violations = validator.validate(user);

        // Then
        assertTrue(violations.size() >= 1);
        boolean hasViolation = violations.stream().anyMatch(v -> "displayName".equals(v.getPropertyPath().toString()));
        assertTrue(hasViolation);
    }

    @Test
    void testValidation_OAuthSubjectTooLong() {
        // Given
        CalendarUser user = createValidUser("user@test.com");
        user.oauthSubject = "A".repeat(256); // Max is 255

        // When
        Set<ConstraintViolation<CalendarUser>> violations = validator.validate(user);

        // Then
        assertTrue(violations.size() >= 1);
        boolean hasViolation = violations.stream().anyMatch(v -> "oauthSubject".equals(v.getPropertyPath().toString()));
        assertTrue(hasViolation);
    }

    private CalendarUser createValidUser(String email) {
        CalendarUser user = new CalendarUser();
        user.oauthProvider = "GOOGLE";
        user.oauthSubject = "test-subject-" + email; // Make unique based on email
        user.email = email;
        user.displayName = "Test User";
        user.isAdmin = false;
        return user;
    }
}
