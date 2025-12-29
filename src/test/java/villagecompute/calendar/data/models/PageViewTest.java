package villagecompute.calendar.data.models;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import villagecompute.calendar.data.repositories.CalendarUserRepository;
import villagecompute.calendar.data.repositories.TestDataCleaner;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class PageViewTest {

    @Inject Validator validator;

    @Inject TestDataCleaner testDataCleaner;

    @Inject CalendarUserRepository calendarUserRepository;

    @Inject jakarta.persistence.EntityManager entityManager;

    private CalendarUser testUser;

    @BeforeEach
    @Transactional
    void setUp() {
        testDataCleaner.deleteAll();

        // Create test user
        testUser = new CalendarUser();
        testUser.oauthProvider = "GOOGLE";
        testUser.oauthSubject = "test-subject-pageview-test";
        testUser.email = "test@example.com";
        testUser.displayName = "Test User";
        testUser.isAdmin = false;
        calendarUserRepository.persist(testUser);

        // Flush to ensure entities are persisted
        entityManager.flush();
    }

    @Test
    @Transactional
    void testValidEntity_Success() {
        // Given
        PageView pageView = createValidPageView();

        // When
        pageView.persist();
        entityManager.flush(); // Flush to generate ID and timestamps

        // Then
        assertNotNull(pageView.id);
        assertNotNull(pageView.created);
        assertNotNull(pageView.updated);
        assertEquals(0L, pageView.version);
    }

    @Test
    void testInvalidEntity_NullSessionId() {
        // Given
        PageView pageView = createValidPageView();
        pageView.sessionId = null;

        // When
        Set<ConstraintViolation<PageView>> violations = validator.validate(pageView);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<PageView> violation = violations.iterator().next();
        assertEquals("sessionId", violation.getPropertyPath().toString());
    }

    @Test
    void testInvalidEntity_NullPath() {
        // Given
        PageView pageView = createValidPageView();
        pageView.path = null;

        // When
        Set<ConstraintViolation<PageView>> violations = validator.validate(pageView);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<PageView> violation = violations.iterator().next();
        assertEquals("path", violation.getPropertyPath().toString());
    }

    @Test
    void testInvalidEntity_SessionIdTooLong() {
        // Given
        PageView pageView = createValidPageView();
        pageView.sessionId = "A".repeat(256); // Max is 255

        // When
        Set<ConstraintViolation<PageView>> violations = validator.validate(pageView);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<PageView> violation = violations.iterator().next();
        assertEquals("sessionId", violation.getPropertyPath().toString());
    }

    @Test
    void testInvalidEntity_PathTooLong() {
        // Given
        PageView pageView = createValidPageView();
        pageView.path = "/very/long/path/" + "a".repeat(500); // Max is 500

        // When
        Set<ConstraintViolation<PageView>> violations = validator.validate(pageView);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<PageView> violation = violations.iterator().next();
        assertEquals("path", violation.getPropertyPath().toString());
    }

    @Test
    @Transactional
    void testFindBySession() {
        // Given
        PageView view1 = createValidPageView();
        view1.sessionId = "session-123";
        view1.path = "/templates";
        view1.persist();

        PageView view2 = createValidPageView();
        view2.sessionId = "session-123";
        view2.path = "/calendar/create";
        view2.persist();

        PageView view3 = createValidPageView();
        view3.sessionId = "session-456";
        view3.path = "/templates";
        view3.persist();

        // When
        List<PageView> sessionViews = PageView.findBySession("session-123").list();

        // Then
        assertEquals(2, sessionViews.size());
        assertTrue(sessionViews.stream().allMatch(v -> "session-123".equals(v.sessionId)));
        // Should be ordered by created ASC
        assertEquals("/templates", sessionViews.get(0).path);
        assertEquals("/calendar/create", sessionViews.get(1).path);
    }

    @Test
    @Transactional
    void testFindByUser() {
        // Given
        PageView view1 = createValidPageView();
        view1.user = testUser;
        view1.path = "/templates";
        view1.persist();

        PageView view2 = createValidPageView();
        view2.user = testUser;
        view2.path = "/calendar/123";
        view2.persist();

        PageView view3 = createValidPageView();
        view3.user = null; // Anonymous user
        view3.path = "/templates";
        view3.persist();

        // When
        List<PageView> userViews = PageView.findByUser(testUser.id).list();

        // Then
        assertEquals(2, userViews.size());
        assertTrue(
                userViews.stream().allMatch(v -> v.user != null && v.user.id.equals(testUser.id)));
    }

    @Test
    @Transactional
    void testFindByPath() {
        // Given
        PageView view1 = createValidPageView();
        view1.path = "/templates";
        view1.persist();

        PageView view2 = createValidPageView();
        view2.path = "/templates";
        view2.persist();

        PageView view3 = createValidPageView();
        view3.path = "/calendar/create";
        view3.persist();

        // When
        List<PageView> templateViews = PageView.findByPath("/templates").list();

        // Then
        assertEquals(2, templateViews.size());
        assertTrue(templateViews.stream().allMatch(v -> "/templates".equals(v.path)));
    }

    @Test
    @Transactional
    void testFindByTimeRange() {
        // Given
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant tomorrow = now.plus(1, ChronoUnit.DAYS);

        PageView oldView = createValidPageView();
        oldView.persist();
        entityManager.flush(); // Flush to persist first

        // Manually set created date using native SQL
        entityManager
                .createNativeQuery("UPDATE page_views SET created = :newCreated WHERE id = :id")
                .setParameter("newCreated", yesterday.minus(1, ChronoUnit.DAYS))
                .setParameter("id", oldView.id)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear(); // Clear persistence context to force reload

        PageView recentView1 = createValidPageView();
        recentView1.persist();

        PageView recentView2 = createValidPageView();
        recentView2.persist();

        // When
        List<PageView> recentViews = PageView.findByTimeRange(yesterday, tomorrow).list();

        // Then
        assertEquals(2, recentViews.size());
        assertTrue(
                recentViews.stream()
                        .allMatch(
                                v -> v.created.isAfter(yesterday) && v.created.isBefore(tomorrow)));
    }

    @Test
    @Transactional
    void testFindByReferrer() {
        // Given
        PageView view1 = createValidPageView();
        view1.referrer = "https://google.com";
        view1.persist();

        PageView view2 = createValidPageView();
        view2.referrer = "https://google.com";
        view2.persist();

        PageView view3 = createValidPageView();
        view3.referrer = "https://facebook.com";
        view3.persist();

        // When
        List<PageView> googleReferrals = PageView.findByReferrer("https://google.com").list();

        // Then
        assertEquals(2, googleReferrals.size());
        assertTrue(googleReferrals.stream().allMatch(v -> "https://google.com".equals(v.referrer)));
    }

    @Test
    @Transactional
    void testCountByPathAndTimeRange() {
        // Given
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant tomorrow = now.plus(1, ChronoUnit.DAYS);

        PageView view1 = createValidPageView();
        view1.path = "/templates";
        view1.persist();

        PageView view2 = createValidPageView();
        view2.path = "/templates";
        view2.persist();

        PageView view3 = createValidPageView();
        view3.path = "/calendar/create";
        view3.persist();

        PageView oldView = createValidPageView();
        oldView.path = "/templates";
        oldView.persist();
        entityManager.flush(); // Flush to persist first

        // Manually set old created date using native SQL
        entityManager
                .createNativeQuery("UPDATE page_views SET created = :newCreated WHERE id = :id")
                .setParameter("newCreated", yesterday.minus(2, ChronoUnit.DAYS))
                .setParameter("id", oldView.id)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear(); // Clear persistence context to force reload

        // When
        long count = PageView.countByPathAndTimeRange("/templates", yesterday, tomorrow);

        // Then
        assertEquals(2, count);
    }

    @Test
    @Transactional
    void testAnonymousUser() {
        // Given
        PageView anonymousView = createValidPageView();
        anonymousView.user = null; // Anonymous
        anonymousView.persist();

        // When
        PageView found = PageView.findById(anonymousView.id);

        // Then
        assertNotNull(found);
        assertNull(found.user);
        assertEquals("session-abc123", found.sessionId);
    }

    @Test
    @Transactional
    void testOptionalFields() {
        // Given
        PageView pageView = createValidPageView();
        pageView.referrer = null;
        pageView.userAgent = null;
        pageView.persist();

        // When
        PageView found = PageView.findById(pageView.id);

        // Then
        assertNotNull(found);
        assertNull(found.referrer);
        assertNull(found.userAgent);
    }

    @Test
    @Transactional
    void testRelationships_ManyToOneUser() {
        // Given
        PageView pageView = createValidPageView();
        pageView.user = testUser;
        pageView.persist();

        // When
        PageView found = PageView.findById(pageView.id);

        // Then
        assertNotNull(found.user);
        assertEquals(testUser.id, found.user.id);
        assertEquals("test@example.com", found.user.email);
    }

    @Test
    @Transactional
    void testAllFields_SetAndRetrieve() {
        // Given - Create page view with ALL fields populated
        PageView pageView = new PageView();
        pageView.sessionId = "session-complete-456";
        pageView.user = testUser;
        pageView.path = "/calendar/123/edit";
        pageView.referrer = "https://reddit.com/r/productivity";
        pageView.userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

        // When
        pageView.persist();
        entityManager.flush();
        entityManager.clear();

        // Then - Verify ALL fields persisted and can be retrieved
        PageView found = PageView.findById(pageView.id);
        assertNotNull(found);
        assertEquals("session-complete-456", found.sessionId);
        assertEquals(testUser.id, found.user.id);
        assertEquals("/calendar/123/edit", found.path);
        assertEquals("https://reddit.com/r/productivity", found.referrer);
        assertEquals(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", found.userAgent);
        assertNotNull(found.created);
        assertNotNull(found.updated);
        assertEquals(0L, found.version);
    }

    @Test
    @Transactional
    void testUpdate_ModifiesUpdatedTimestamp() {
        // Given
        PageView pageView = createValidPageView();
        pageView.persist();
        entityManager.flush();
        Instant originalUpdated = pageView.updated;

        // Wait to ensure timestamp changes
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
        }

        // When
        pageView.path = "/updated/path";
        pageView.referrer = "https://updated.com";
        pageView.persist();
        entityManager.flush();

        // Then
        assertTrue(pageView.updated.isAfter(originalUpdated));
        assertEquals("/updated/path", pageView.path);
        assertEquals("https://updated.com", pageView.referrer);
        assertEquals(1L, pageView.version);
    }

    @Test
    @Transactional
    void testDelete_RemovesEntity() {
        // Given
        PageView pageView = createValidPageView();
        pageView.persist();
        java.util.UUID pageViewId = pageView.id;
        entityManager.flush();

        // When
        pageView.delete();
        entityManager.flush();

        // Then
        assertNull(PageView.findById(pageViewId));
    }

    @Test
    @Transactional
    void testListAll() {
        // Given
        createValidPageView().persist();
        createValidPageView().persist();
        entityManager.flush();

        // When
        List<PageView> allPageViews = PageView.listAll();

        // Then
        assertEquals(2, allPageViews.size());
    }

    @Test
    @Transactional
    void testCount() {
        // Given
        createValidPageView().persist();
        createValidPageView().persist();
        createValidPageView().persist();
        entityManager.flush();

        // When
        long count = PageView.count();

        // Then
        assertEquals(3, count);
    }

    @Test
    void testValidation_ReferrerTooLong() {
        // Given
        PageView pageView = createValidPageView();
        pageView.referrer = "https://example.com/" + "a".repeat(500); // Exceeds 500 char limit

        // When
        Set<ConstraintViolation<PageView>> violations = validator.validate(pageView);

        // Then
        assertTrue(violations.size() >= 1);
        boolean hasViolation =
                violations.stream()
                        .anyMatch(v -> "referrer".equals(v.getPropertyPath().toString()));
        assertTrue(hasViolation);
    }

    @Test
    void testValidation_UserAgentTooLong() {
        // Given
        PageView pageView = createValidPageView();
        pageView.userAgent = "Mozilla/5.0 " + "a".repeat(1000); // Exceeds 1000 char limit

        // When
        Set<ConstraintViolation<PageView>> violations = validator.validate(pageView);

        // Then
        assertTrue(violations.size() >= 1);
        boolean hasViolation =
                violations.stream()
                        .anyMatch(v -> "userAgent".equals(v.getPropertyPath().toString()));
        assertTrue(hasViolation);
    }

    private PageView createValidPageView() {
        PageView pageView = new PageView();
        pageView.sessionId = "session-abc123";
        pageView.path = "/templates";
        pageView.referrer = "https://google.com";
        pageView.userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)";
        pageView.user = null; // Can be null for anonymous users
        return pageView;
    }
}
