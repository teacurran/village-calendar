package villagecompute.calendar.data.repositories;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.repositories.TestDataCleaner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class CalendarUserRepositoryTest {

    @Inject
    TestDataCleaner testDataCleaner;

    @Inject
    CalendarUserRepository repository;

    @Inject
    jakarta.persistence.EntityManager entityManager;

    @BeforeEach
    @Transactional
    void setUp() {
        testDataCleaner.deleteAll();
    }

    @Test
    @Transactional
    void testFindByOAuthSubject() {
        // Given
        CalendarUser user = createUser("GOOGLE", "google-123", "test@example.com");
        repository.persist(user);
        entityManager.flush();

        // When
        Optional<CalendarUser> found = repository.findByOAuthSubject("GOOGLE", "google-123");

        // Then
        assertTrue(found.isPresent());
        assertEquals("test@example.com", found.get().email);
    }

    @Test
    @Transactional
    void testFindByOAuthSubject_NotFound() {
        // When
        Optional<CalendarUser> found = repository.findByOAuthSubject("GOOGLE", "nonexistent");

        // Then
        assertFalse(found.isPresent());
    }

    @Test
    @Transactional
    void testFindByEmail() {
        // Given
        CalendarUser user = createUser("FACEBOOK", "fb-456", "user@test.com");
        repository.persist(user);
        entityManager.flush();

        // When
        Optional<CalendarUser> found = repository.findByEmail("user@test.com");

        // Then
        assertTrue(found.isPresent());
        assertEquals("FACEBOOK", found.get().oauthProvider);
    }

    @Test
    @Transactional
    void testFindActiveUsersSince() {
        // Given
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant weekAgo = now.minus(7, ChronoUnit.DAYS);

        CalendarUser recentUser = createUser("GOOGLE", "recent", "recent@test.com");
        recentUser.lastLoginAt = now;
        repository.persist(recentUser);

        CalendarUser oldUser = createUser("GOOGLE", "old", "old@test.com");
        oldUser.lastLoginAt = weekAgo;
        repository.persist(oldUser);

        entityManager.flush();

        // When
        List<CalendarUser> activeUsers = repository.findActiveUsersSince(yesterday);

        // Then
        assertEquals(1, activeUsers.size());
        assertEquals("recent@test.com", activeUsers.get(0).email);
    }

    @Test
    @Transactional
    void testFindByProvider() {
        // Given
        repository.persist(createUser("GOOGLE", "g1", "google1@test.com"));
        repository.persist(createUser("GOOGLE", "g2", "google2@test.com"));
        repository.persist(createUser("FACEBOOK", "f1", "facebook1@test.com"));
        entityManager.flush();

        // When
        List<CalendarUser> googleUsers = repository.findByProvider("GOOGLE");

        // Then
        assertEquals(2, googleUsers.size());
        assertTrue(googleUsers.stream().allMatch(u -> "GOOGLE".equals(u.oauthProvider)));
    }

    @Test
    @Transactional
    void testUniqueConstraint() {
        // Given
        repository.persist(createUser("GOOGLE", "duplicate", "test1@example.com"));

        // When/Then - attempting to insert duplicate OAuth provider+subject should fail
        CalendarUser duplicate = createUser("GOOGLE", "duplicate", "test2@example.com");
        assertThrows(Exception.class, () -> {
            repository.persist(duplicate);
            repository.flush();
        });
    }

    private CalendarUser createUser(String provider, String subject, String email) {
        CalendarUser user = new CalendarUser();
        user.oauthProvider = provider;
        user.oauthSubject = subject;
        user.email = email;
        user.displayName = "Test User";
        return user;
    }
}
