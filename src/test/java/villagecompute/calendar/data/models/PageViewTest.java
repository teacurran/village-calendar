package villagecompute.calendar.data.models;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import villagecompute.calendar.data.repositories.CalendarUserRepository;
import villagecompute.calendar.data.repositories.TestDataCleaner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PageViewTest {

    @Inject
    Validator validator;

    @Inject
    TestDataCleaner testDataCleaner;

    @Inject
    CalendarUserRepository calendarUserRepository;

    @Inject
    jakarta.persistence.EntityManager entityManager;

    private CalendarUser testUser;

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
        assertTrue(userViews.stream().allMatch(v -> v.user != null && v.user.id.equals(testUser.id)));
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
        // Manually set created date (normally not allowed, but for test)
        oldView.created = yesterday.minus(1, ChronoUnit.DAYS);

        PageView recentView1 = createValidPageView();
        recentView1.persist();

        PageView recentView2 = createValidPageView();
        recentView2.persist();

        // When
        List<PageView> recentViews = PageView.findByTimeRange(yesterday, tomorrow).list();

        // Then
        assertEquals(2, recentViews.size());
        assertTrue(recentViews.stream().allMatch(v ->
            v.created.isAfter(yesterday) && v.created.isBefore(tomorrow)
        ));
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
        oldView.created = yesterday.minus(2, ChronoUnit.DAYS);

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
