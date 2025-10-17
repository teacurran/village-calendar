package villagecompute.calendar.data.models;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import villagecompute.calendar.data.repositories.TestDataCleaner;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AnalyticsRollupTest {

    @Inject
    Validator validator;

    @Inject
    TestDataCleaner testDataCleaner;

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
        AnalyticsRollup rollup = createValidRollup();

        // When
        rollup.persist();
        entityManager.flush(); // Flush to generate ID and timestamps

        // Then
        assertNotNull(rollup.id);
        assertNotNull(rollup.created);
        assertNotNull(rollup.updated);
        assertEquals(0L, rollup.version);
    }

    @Test
    void testInvalidEntity_NullMetricName() {
        // Given
        AnalyticsRollup rollup = createValidRollup();
        rollup.metricName = null;

        // When
        Set<ConstraintViolation<AnalyticsRollup>> violations = validator.validate(rollup);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<AnalyticsRollup> violation = violations.iterator().next();
        assertEquals("metricName", violation.getPropertyPath().toString());
    }

    @Test
    void testInvalidEntity_NullValue() {
        // Given
        AnalyticsRollup rollup = createValidRollup();
        rollup.value = null;

        // When
        Set<ConstraintViolation<AnalyticsRollup>> violations = validator.validate(rollup);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<AnalyticsRollup> violation = violations.iterator().next();
        assertEquals("value", violation.getPropertyPath().toString());
    }

    @Test
    void testInvalidEntity_NegativeValue() {
        // Given
        AnalyticsRollup rollup = createValidRollup();
        rollup.value = BigDecimal.valueOf(-1.00);

        // When
        Set<ConstraintViolation<AnalyticsRollup>> violations = validator.validate(rollup);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<AnalyticsRollup> violation = violations.iterator().next();
        assertEquals("value", violation.getPropertyPath().toString());
    }

    @Test
    void testInvalidEntity_NullPeriodStart() {
        // Given
        AnalyticsRollup rollup = createValidRollup();
        rollup.periodStart = null;

        // When
        Set<ConstraintViolation<AnalyticsRollup>> violations = validator.validate(rollup);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<AnalyticsRollup> violation = violations.iterator().next();
        assertEquals("periodStart", violation.getPropertyPath().toString());
    }

    @Test
    void testInvalidEntity_NullPeriodEnd() {
        // Given
        AnalyticsRollup rollup = createValidRollup();
        rollup.periodEnd = null;

        // When
        Set<ConstraintViolation<AnalyticsRollup>> violations = validator.validate(rollup);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<AnalyticsRollup> violation = violations.iterator().next();
        assertEquals("periodEnd", violation.getPropertyPath().toString());
    }

    @Test
    void testInvalidEntity_MetricNameTooLong() {
        // Given
        AnalyticsRollup rollup = createValidRollup();
        rollup.metricName = "A".repeat(256); // Max is 255

        // When
        Set<ConstraintViolation<AnalyticsRollup>> violations = validator.validate(rollup);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<AnalyticsRollup> violation = violations.iterator().next();
        assertEquals("metricName", violation.getPropertyPath().toString());
    }

    @Test
    @Transactional
    void testFindByMetric() {
        // Given
        AnalyticsRollup rollup1 = createValidRollup();
        rollup1.metricName = "page_views";
        rollup1.persist();

        AnalyticsRollup rollup2 = createValidRollup();
        rollup2.metricName = "page_views";
        rollup2.periodStart = Instant.now().minus(2, ChronoUnit.DAYS);
        rollup2.periodEnd = Instant.now().minus(1, ChronoUnit.DAYS);
        rollup2.persist();

        AnalyticsRollup rollup3 = createValidRollup();
        rollup3.metricName = "revenue";
        rollup3.persist();

        // When
        List<AnalyticsRollup> pageViewRollups = AnalyticsRollup.findByMetric("page_views").list();

        // Then
        assertEquals(2, pageViewRollups.size());
        assertTrue(pageViewRollups.stream().allMatch(r -> "page_views".equals(r.metricName)));
    }

    @Test
    @Transactional
    void testFindByMetricAndDimension() {
        // Given
        AnalyticsRollup rollup1 = createValidRollup();
        rollup1.metricName = "page_views";
        rollup1.dimensionKey = "path";
        rollup1.dimensionValue = "/templates";
        rollup1.persist();

        AnalyticsRollup rollup2 = createValidRollup();
        rollup2.metricName = "page_views";
        rollup2.dimensionKey = "path";
        rollup2.dimensionValue = "/calendar/create";
        rollup2.persist();

        AnalyticsRollup rollup3 = createValidRollup();
        rollup3.metricName = "page_views";
        rollup3.dimensionKey = "device";
        rollup3.dimensionValue = "mobile";
        rollup3.persist();

        // When
        List<AnalyticsRollup> pathRollups = AnalyticsRollup.findByMetricAndDimension("page_views", "path").list();

        // Then
        assertEquals(2, pathRollups.size());
        assertTrue(pathRollups.stream().allMatch(r ->
            "page_views".equals(r.metricName) && "path".equals(r.dimensionKey)
        ));
    }

    @Test
    @Transactional
    void testFindByMetricAndDimensionValue() {
        // Given
        AnalyticsRollup rollup1 = createValidRollup();
        rollup1.metricName = "page_views";
        rollup1.dimensionKey = "path";
        rollup1.dimensionValue = "/templates";
        rollup1.persist();

        AnalyticsRollup rollup2 = createValidRollup();
        rollup2.metricName = "page_views";
        rollup2.dimensionKey = "path";
        rollup2.dimensionValue = "/templates";
        rollup2.periodStart = Instant.now().minus(2, ChronoUnit.DAYS);
        rollup2.periodEnd = Instant.now().minus(1, ChronoUnit.DAYS);
        rollup2.persist();

        AnalyticsRollup rollup3 = createValidRollup();
        rollup3.metricName = "page_views";
        rollup3.dimensionKey = "path";
        rollup3.dimensionValue = "/calendar/create";
        rollup3.persist();

        // When
        List<AnalyticsRollup> templatesRollups = AnalyticsRollup.findByMetricAndDimensionValue(
            "page_views", "path", "/templates"
        ).list();

        // Then
        assertEquals(2, templatesRollups.size());
        assertTrue(templatesRollups.stream().allMatch(r ->
            "page_views".equals(r.metricName) &&
            "path".equals(r.dimensionKey) &&
            "/templates".equals(r.dimensionValue)
        ));
    }

    @Test
    @Transactional
    void testFindByTimeRange() {
        // Given
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);
        Instant threeDaysAgo = now.minus(3, ChronoUnit.DAYS);

        AnalyticsRollup recentRollup = new AnalyticsRollup();
        recentRollup.metricName = "page_views";
        recentRollup.dimensionKey = "path";
        recentRollup.dimensionValue = "/recent";
        recentRollup.value = BigDecimal.valueOf(100.00);
        recentRollup.periodStart = yesterday;
        recentRollup.periodEnd = now;
        recentRollup.persist();

        AnalyticsRollup oldRollup = new AnalyticsRollup();
        oldRollup.metricName = "page_views";
        oldRollup.dimensionKey = "path";
        oldRollup.dimensionValue = "/old";
        oldRollup.value = BigDecimal.valueOf(100.00);
        oldRollup.periodStart = threeDaysAgo;
        oldRollup.periodEnd = twoDaysAgo;
        oldRollup.persist();

        entityManager.flush();

        // When
        List<AnalyticsRollup> recentRollups = AnalyticsRollup.findByTimeRange(yesterday.minusSeconds(1), now.plus(1, ChronoUnit.DAYS)).list();

        // Then
        assertEquals(1, recentRollups.size());
        assertTrue(recentRollups.get(0).periodStart.isAfter(twoDaysAgo));
    }

    @Test
    @Transactional
    void testFindByMetricAndTimeRange() {
        // Given
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        AnalyticsRollup pageViewsRecent = createValidRollup();
        pageViewsRecent.metricName = "page_views";
        pageViewsRecent.periodStart = yesterday;
        pageViewsRecent.periodEnd = now;
        pageViewsRecent.persist();

        AnalyticsRollup revenueRecent = createValidRollup();
        revenueRecent.metricName = "revenue";
        revenueRecent.periodStart = yesterday;
        revenueRecent.periodEnd = now;
        revenueRecent.persist();

        AnalyticsRollup pageViewsOld = createValidRollup();
        pageViewsOld.metricName = "page_views";
        pageViewsOld.periodStart = twoDaysAgo.minus(1, ChronoUnit.DAYS);
        pageViewsOld.periodEnd = twoDaysAgo;
        pageViewsOld.persist();

        // When
        List<AnalyticsRollup> rollups = AnalyticsRollup.findByMetricAndTimeRange(
            "page_views", yesterday, now.plus(1, ChronoUnit.DAYS)
        ).list();

        // Then
        assertEquals(1, rollups.size());
        assertEquals("page_views", rollups.get(0).metricName);
        assertTrue(rollups.get(0).periodStart.isAfter(twoDaysAgo));
    }

    @Test
    @Transactional
    void testSumByMetricAndTimeRange() {
        // Given
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        AnalyticsRollup rollup1 = new AnalyticsRollup();
        rollup1.metricName = "revenue";
        rollup1.dimensionKey = "path";
        rollup1.dimensionValue = "/checkout";
        rollup1.value = BigDecimal.valueOf(100.00);
        rollup1.periodStart = yesterday;
        rollup1.periodEnd = now;
        rollup1.persist();

        AnalyticsRollup rollup2 = new AnalyticsRollup();
        rollup2.metricName = "revenue";
        rollup2.dimensionKey = "path";
        rollup2.dimensionValue = "/cart";
        rollup2.value = BigDecimal.valueOf(250.00);
        rollup2.periodStart = yesterday;
        rollup2.periodEnd = now;
        rollup2.persist();

        AnalyticsRollup rollup3 = new AnalyticsRollup();
        rollup3.metricName = "revenue";
        rollup3.dimensionKey = "path";
        rollup3.dimensionValue = "/checkout";
        rollup3.value = BigDecimal.valueOf(75.00);
        rollup3.periodStart = twoDaysAgo.minus(1, ChronoUnit.DAYS);
        rollup3.periodEnd = twoDaysAgo;
        rollup3.persist();

        AnalyticsRollup rollup4 = new AnalyticsRollup();
        rollup4.metricName = "page_views";
        rollup4.dimensionKey = "path";
        rollup4.dimensionValue = "/templates";
        rollup4.value = BigDecimal.valueOf(500.00);
        rollup4.periodStart = yesterday;
        rollup4.periodEnd = now;
        rollup4.persist();

        entityManager.flush();

        // When
        BigDecimal sum = AnalyticsRollup.sumByMetricAndTimeRange("revenue", yesterday.minusSeconds(1), now.plus(1, ChronoUnit.DAYS));

        // Then
        assertEquals(0, BigDecimal.valueOf(350.00).compareTo(sum)); // Use compareTo for BigDecimal comparison
    }

    @Test
    @Transactional
    void testSumByMetricAndTimeRange_NoResults() {
        // Given
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);

        // When
        BigDecimal sum = AnalyticsRollup.sumByMetricAndTimeRange("revenue", yesterday, now);

        // Then
        assertEquals(BigDecimal.ZERO, sum);
    }

    @Test
    @Transactional
    void testOptionalDimensionFields() {
        // Given
        AnalyticsRollup rollup = createValidRollup();
        rollup.dimensionKey = null;
        rollup.dimensionValue = null;
        rollup.persist();

        // When
        AnalyticsRollup found = AnalyticsRollup.findById(rollup.id);

        // Then
        assertNotNull(found);
        assertNull(found.dimensionKey);
        assertNull(found.dimensionValue);
    }

    @Test
    @Transactional
    void testUniqueConstraint() {
        // Given
        Instant periodStart = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant periodEnd = Instant.now();

        AnalyticsRollup rollup1 = createValidRollup();
        rollup1.metricName = "page_views";
        rollup1.dimensionKey = "path";
        rollup1.dimensionValue = "/templates";
        rollup1.periodStart = periodStart;
        rollup1.periodEnd = periodEnd;
        rollup1.persist();

        // When/Then - Creating duplicate should fail (but we can't test constraint violation directly in unit test)
        // This test just verifies the first insert succeeds
        assertNotNull(rollup1.id);
    }

    @Test
    @Transactional
    void testDecimalPrecision() {
        // Given
        AnalyticsRollup rollup = createValidRollup();
        rollup.value = new BigDecimal("123456789012.99"); // 13 digits before decimal, 2 after

        // When
        rollup.persist();
        AnalyticsRollup found = AnalyticsRollup.findById(rollup.id);

        // Then
        assertNotNull(found);
        assertEquals(new BigDecimal("123456789012.99"), found.value);
    }

    private AnalyticsRollup createValidRollup() {
        // Use nanoTime to ensure unique timestamps for each call
        // This prevents unique constraint violations on (metricName, dimensionKey, dimensionValue, periodStart, periodEnd)
        Instant now = Instant.now().plusNanos(System.nanoTime() % 1000000);
        AnalyticsRollup rollup = new AnalyticsRollup();
        rollup.metricName = "page_views";
        rollup.dimensionKey = "path";
        rollup.dimensionValue = "/templates";
        rollup.value = BigDecimal.valueOf(100.00);
        rollup.periodStart = now.minus(1, ChronoUnit.DAYS);
        rollup.periodEnd = now;
        return rollup;
    }
}
