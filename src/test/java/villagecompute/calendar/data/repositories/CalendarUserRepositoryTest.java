package villagecompute.calendar.data.repositories;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import villagecompute.calendar.data.models.CalendarUser;

import io.quarkus.test.junit.QuarkusTest;

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

    @Test
    @Transactional
    void testListAll() {
        // Given
        repository.persist(createUser("GOOGLE", "g1", "g1@test.com"));
        repository.persist(createUser("GOOGLE", "g2", "g2@test.com"));
        repository.persist(createUser("FACEBOOK", "f1", "f1@test.com"));
        entityManager.flush();

        // When
        List<CalendarUser> allUsers = repository.listAll();

        // Then
        assertEquals(3, allUsers.size());
    }

    @Test
    @Transactional
    void testCount() {
        // Given
        repository.persist(createUser("GOOGLE", "g1", "g1@test.com"));
        repository.persist(createUser("GOOGLE", "g2", "g2@test.com"));
        entityManager.flush();

        // When
        long count = repository.count();

        // Then
        assertEquals(2, count);
    }

    @Test
    @Transactional
    void testDelete() {
        // Given
        CalendarUser user = createUser("GOOGLE", "delete-me", "delete@test.com");
        repository.persist(user);
        java.util.UUID userId = user.id;
        entityManager.flush();

        // When
        repository.delete(user);
        entityManager.flush();

        // Then
        assertTrue(CalendarUser.findByIdOptional(userId).isEmpty());
        assertEquals(0, repository.count());
    }

    @Test
    @Transactional
    void testPersistAndFlush() {
        // Given
        CalendarUser user = createUser("GOOGLE", "persist-flush", "persistflush@test.com");

        // When
        repository.persist(user);
        repository.flush();

        // Then
        assertNotNull(user.id);
        Optional<CalendarUser> foundOpt = CalendarUser.findByIdOptional(user.id);
        assertTrue(foundOpt.isPresent());
        assertEquals("persistflush@test.com", foundOpt.get().email);
    }

    @Test
    @Transactional
    void testFindAll() {
        // Given
        repository.persist(createUser("GOOGLE", "fa1", "fa1@test.com"));
        repository.persist(createUser("GOOGLE", "fa2", "fa2@test.com"));
        entityManager.flush();

        // When
        List<CalendarUser> users = repository.findAll().list();

        // Then
        assertEquals(2, users.size());
    }

    @Test
    @Transactional
    void testStream() {
        // Given
        repository.persist(createUser("GOOGLE", "s1", "s1@test.com"));
        repository.persist(createUser("GOOGLE", "s2", "s2@test.com"));
        entityManager.flush();

        // When
        long count = repository.streamAll().count();

        // Then
        assertEquals(2, count);
    }

    @Test
    @Transactional
    void testPersistMultiple() {
        // Given
        CalendarUser user1 = createUser("GOOGLE", "multi1", "multi1@test.com");
        CalendarUser user2 = createUser("GOOGLE", "multi2", "multi2@test.com");

        // When
        repository.persist(user1);
        repository.persist(user2);
        entityManager.flush();

        // Then
        assertEquals(2, repository.count());
        assertNotNull(user1.id);
        assertNotNull(user2.id);
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
