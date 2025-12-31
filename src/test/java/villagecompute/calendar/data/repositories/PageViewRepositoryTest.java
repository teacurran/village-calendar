package villagecompute.calendar.data.repositories;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.PageView;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class PageViewRepositoryTest {

    @Inject
    TestDataCleaner testDataCleaner;

    @Inject
    PageViewRepository repository;

    @Inject
    CalendarUserRepository userRepository;

    @Inject
    jakarta.persistence.EntityManager entityManager;

    private CalendarUser testUser;

    @BeforeEach
    @Transactional
    void setUp() {
        testDataCleaner.deleteAll();

        // Create test user (required for PageView FK relationship)
        testUser = new CalendarUser();
        testUser.oauthProvider = "GOOGLE";
        testUser.oauthSubject = "test-subject-pageview-repo-test";
        testUser.email = "pageview-test@example.com";
        testUser.displayName = "PageView Test User";
        userRepository.persist(testUser);
        entityManager.flush();
    }

    @Test
    @Transactional
    void testFindById() {
        // Given
        PageView pageView = createPageView(testUser, "session-123", "/templates");
        repository.persist(pageView);
        entityManager.flush();

        // When
        Optional<PageView> found = repository.findById(pageView.id);

        // Then
        assertTrue(found.isPresent());
        assertEquals("session-123", found.get().sessionId);
        assertEquals("/templates", found.get().path);
    }

    @Test
    @Transactional
    void testFindBySession() {
        // Given
        Instant now = Instant.now();

        PageView pv1 = createPageView(testUser, "session-123", "/home");
        pv1.created = now.minus(3, ChronoUnit.MINUTES);
        repository.persist(pv1);

        PageView pv2 = createPageView(testUser, "session-123", "/templates");
        pv2.created = now.minus(2, ChronoUnit.MINUTES);
        repository.persist(pv2);

        PageView pv3 = createPageView(testUser, "session-123", "/checkout");
        pv3.created = now.minus(1, ChronoUnit.MINUTES);
        repository.persist(pv3);

        PageView pv4 = createPageView(testUser, "session-456", "/home");
        pv4.created = now;
        repository.persist(pv4);

        entityManager.flush();

        // When
        List<PageView> sessionViews = repository.findBySession("session-123");

        // Then
        assertEquals(3, sessionViews.size());
        assertTrue(sessionViews.stream().allMatch(pv -> "session-123".equals(pv.sessionId)));
        // Verify ORDER BY created ASC (oldest first for session reconstruction)
        assertEquals("/home", sessionViews.get(0).path);
        assertEquals("/templates", sessionViews.get(1).path);
        assertEquals("/checkout", sessionViews.get(2).path);
    }

    @Test
    @Transactional
    void testFindByUser() {
        // Given
        CalendarUser user2 = new CalendarUser();
        user2.oauthProvider = "FACEBOOK";
        user2.oauthSubject = "fb-user-2";
        user2.email = "user2@example.com";
        user2.displayName = "User 2";
        userRepository.persist(user2);

        Instant now = Instant.now();

        PageView pv1 = createPageView(testUser, "session-1", "/home");
        pv1.created = now.minus(3, ChronoUnit.MINUTES);
        repository.persist(pv1);

        PageView pv2 = createPageView(testUser, "session-1", "/templates");
        pv2.created = now.minus(1, ChronoUnit.MINUTES);
        repository.persist(pv2);

        PageView pv3 = createPageView(user2, "session-2", "/home");
        pv3.created = now;
        repository.persist(pv3);

        entityManager.flush();

        // When
        List<PageView> userViews = repository.findByUser(testUser.id);

        // Then
        assertEquals(2, userViews.size());
        assertTrue(userViews.stream().allMatch(pv -> pv.user.id.equals(testUser.id)));
        // Verify ORDER BY created DESC (most recent first)
        assertEquals("/templates", userViews.get(0).path);
        assertEquals("/home", userViews.get(1).path);
    }

    @Test
    @Transactional
    void testFindByPath() {
        // Given
        Instant now = Instant.now();

        PageView pv1 = createPageView(testUser, "session-1", "/templates");
        pv1.created = now.minus(3, ChronoUnit.MINUTES);
        repository.persist(pv1);

        PageView pv2 = createPageView(testUser, "session-2", "/templates");
        pv2.created = now.minus(1, ChronoUnit.MINUTES);
        repository.persist(pv2);

        PageView pv3 = createPageView(testUser, "session-3", "/home");
        pv3.created = now;
        repository.persist(pv3);

        entityManager.flush();

        // When
        List<PageView> pathViews = repository.findByPath("/templates");

        // Then
        assertEquals(2, pathViews.size());
        assertTrue(pathViews.stream().allMatch(pv -> "/templates".equals(pv.path)));
        // Verify ORDER BY created DESC (most recent first)
        assertEquals("session-2", pathViews.get(0).sessionId);
        assertEquals("session-1", pathViews.get(1).sessionId);
    }

    @Test
    @Transactional
    void testFindByTimeRange() {
        // Given
        Instant beforeCreation = Instant.now();

        // Create page views (will have recent timestamps due to @CreationTimestamp)
        PageView pv1 = createPageView(testUser, "session-1", "/home");
        repository.persist(pv1);

        PageView pv2 = createPageView(testUser, "session-2", "/templates");
        repository.persist(pv2);

        entityManager.flush();

        Instant afterCreation = Instant.now();

        // When - query for page views created within the time window
        List<PageView> rangeViews = repository.findByTimeRange(beforeCreation, afterCreation);

        // Then - should find both page views created during test
        assertEquals(2, rangeViews.size());
        assertTrue(rangeViews.stream()
                .allMatch(pv -> !pv.created.isBefore(beforeCreation) && pv.created.isBefore(afterCreation)));

        // When - query for future time range (should find none)
        Instant futureStart = afterCreation.plus(1, ChronoUnit.DAYS);
        Instant futureEnd = afterCreation.plus(2, ChronoUnit.DAYS);
        List<PageView> futureViews = repository.findByTimeRange(futureStart, futureEnd);

        // Then
        assertEquals(0, futureViews.size());
    }

    @Test
    @Transactional
    void testFindByReferrer() {
        // Given
        Instant now = Instant.now();

        PageView pv1 = createPageView(testUser, "session-1", "/home");
        pv1.referrer = "https://google.com";
        pv1.created = now.minus(3, ChronoUnit.MINUTES);
        repository.persist(pv1);

        PageView pv2 = createPageView(testUser, "session-2", "/templates");
        pv2.referrer = "https://google.com";
        pv2.created = now.minus(1, ChronoUnit.MINUTES);
        repository.persist(pv2);

        PageView pv3 = createPageView(testUser, "session-3", "/home");
        pv3.referrer = "https://facebook.com";
        pv3.created = now;
        repository.persist(pv3);

        entityManager.flush();

        // When
        List<PageView> googleReferrals = repository.findByReferrer("https://google.com");

        // Then
        assertEquals(2, googleReferrals.size());
        assertTrue(googleReferrals.stream().allMatch(pv -> "https://google.com".equals(pv.referrer)));
        // Verify ORDER BY created DESC
        assertEquals("/templates", googleReferrals.get(0).path);
        assertEquals("/home", googleReferrals.get(1).path);
    }

    @Test
    @Transactional
    void testCountByPathAndTimeRange() {
        // Given
        Instant beforeCreation = Instant.now();

        // Should be counted (correct path and created within time range)
        for (int i = 0; i < 5; i++) {
            PageView pv = createPageView(testUser, "session-" + i, "/templates");
            repository.persist(pv);
        }

        // Wrong path (should not be counted)
        PageView pvWrongPath = createPageView(testUser, "session-x", "/home");
        repository.persist(pvWrongPath);

        entityManager.flush();

        Instant afterCreation = Instant.now();

        // When
        long count = repository.countByPathAndTimeRange("/templates", beforeCreation, afterCreation);

        // Then
        assertEquals(5, count);

        // Verify count excludes wrong path
        long homeCount = repository.countByPathAndTimeRange("/home", beforeCreation, afterCreation);
        assertEquals(1, homeCount);

        // Verify count in future time range returns 0
        long futureCount = repository.countByPathAndTimeRange("/templates", afterCreation.plus(1, ChronoUnit.DAYS),
                afterCreation.plus(2, ChronoUnit.DAYS));
        assertEquals(0, futureCount);
    }

    @Test
    @Transactional
    void testPersist() {
        // Given
        PageView pageView = createPageView(testUser, "new-session", "/pricing");
        pageView.referrer = "https://twitter.com";
        pageView.userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";

        // When
        repository.persist(pageView);
        entityManager.flush();

        // Then
        assertNotNull(pageView.id);
        assertEquals(1, repository.count());
    }

    @Test
    @Transactional
    void testListAll() {
        // Given
        repository.persist(createPageView(testUser, "session-1", "/home"));
        repository.persist(createPageView(testUser, "session-2", "/templates"));
        repository.persist(createPageView(testUser, "session-3", "/checkout"));
        entityManager.flush();

        // When
        List<PageView> all = repository.listAll();

        // Then
        assertEquals(3, all.size());
    }

    @Test
    @Transactional
    void testDelete() {
        // Given
        PageView pageView = createPageView(testUser, "to-delete", "/test");
        repository.persist(pageView);
        entityManager.flush();
        java.util.UUID pageViewId = pageView.id;

        // When
        repository.delete(pageView);
        entityManager.flush();

        // Then
        assertTrue(repository.findById(pageViewId).isEmpty());
        assertEquals(0, repository.count());
    }

    @Test
    @Transactional
    void testCount() {
        // Given
        repository.persist(createPageView(testUser, "session-1", "/home"));
        repository.persist(createPageView(testUser, "session-2", "/templates"));
        entityManager.flush();

        // When
        long count = repository.count();

        // Then
        assertEquals(2, count);
    }

    @Test
    @Transactional
    void testFindBySession_EmptyResult() {
        // Given
        repository.persist(createPageView(testUser, "session-123", "/home"));
        entityManager.flush();

        // When
        List<PageView> results = repository.findBySession("nonexistent-session");

        // Then
        assertEquals(0, results.size());
    }

    @Test
    @Transactional
    void testFindByUser_NullUser() {
        // Given
        PageView anonymousView = createPageView(null, "session-anon", "/home");
        repository.persist(anonymousView);
        entityManager.flush();

        // When
        List<PageView> results = repository.findByUser(testUser.id);

        // Then
        assertEquals(0, results.size());
    }

    @Test
    @Transactional
    void testCountByPathAndTimeRange_ZeroResults() {
        // When
        long count = repository.countByPathAndTimeRange("/nonexistent", Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now());

        // Then
        assertEquals(0, count);
    }

    private PageView createPageView(CalendarUser user, String sessionId, String path) {
        PageView pageView = new PageView();
        pageView.user = user;
        pageView.sessionId = sessionId;
        pageView.path = path;
        pageView.referrer = null;
        pageView.userAgent = "Test User Agent";
        return pageView;
    }
}
