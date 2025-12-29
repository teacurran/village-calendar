package villagecompute.calendar.data.repositories;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import villagecompute.calendar.data.models.AnalyticsRollup;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class AnalyticsRollupRepositoryTest {

    @Inject TestDataCleaner testDataCleaner;

    @Inject AnalyticsRollupRepository repository;

    @Inject jakarta.persistence.EntityManager entityManager;

    @BeforeEach
    @Transactional
    void setUp() {
        testDataCleaner.deleteAll();
    }

    @Test
    @Transactional
    void testFindById() {
        // Given
        AnalyticsRollup rollup =
                createRollup(
                        "page_views",
                        "path",
                        "/templates",
                        Instant.now().minus(1, ChronoUnit.HOURS),
                        Instant.now(),
                        new BigDecimal("100.00"));
        repository.persist(rollup);
        entityManager.flush();

        // When
        Optional<AnalyticsRollup> found = repository.findById(rollup.id);

        // Then
        assertTrue(found.isPresent());
        assertEquals("page_views", found.get().metricName);
        assertEquals("/templates", found.get().dimensionValue);
    }

    @Test
    @Transactional
    void testFindByMetric() {
        // Given
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant hour1 = now.minus(1, ChronoUnit.HOURS);
        Instant hour2 = now.minus(2, ChronoUnit.HOURS);
        Instant hour3 = now.minus(3, ChronoUnit.HOURS);

        AnalyticsRollup rollup1 =
                createRollup("page_views", "path", "/home", hour1, now, new BigDecimal("100.00"));
        AnalyticsRollup rollup2 =
                createRollup("page_views", "path", "/about", hour2, now, new BigDecimal("200.00"));
        AnalyticsRollup rollup3 =
                createRollup(
                        "page_views", "path", "/contact", hour3, now, new BigDecimal("300.00"));
        AnalyticsRollup rollup4 =
                createRollup("revenue", "status", "PAID", hour1, now, new BigDecimal("1000.00"));

        repository.persist(rollup1);
        repository.persist(rollup2);
        repository.persist(rollup3);
        repository.persist(rollup4);
        entityManager.flush();

        // When
        List<AnalyticsRollup> results = repository.findByMetric("page_views");

        // Then
        assertEquals(3, results.size());
        assertTrue(results.stream().allMatch(r -> "page_views".equals(r.metricName)));
        // Verify ORDER BY periodStart DESC
        assertEquals(hour1, results.get(0).periodStart); // Most recent first
        assertEquals(hour2, results.get(1).periodStart);
        assertEquals(hour3, results.get(2).periodStart);
    }

    @Test
    @Transactional
    void testFindByMetricAndDimension() {
        // Given
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant hour1 = now.minus(1, ChronoUnit.HOURS);
        Instant hour2 = now.minus(2, ChronoUnit.HOURS);

        repository.persist(
                createRollup("page_views", "path", "/home", hour1, now, new BigDecimal("100.00")));
        repository.persist(
                createRollup("page_views", "path", "/about", hour2, now, new BigDecimal("200.00")));
        repository.persist(
                createRollup(
                        "page_views",
                        "template",
                        "template-1",
                        hour1,
                        now,
                        new BigDecimal("50.00")));
        repository.persist(
                createRollup("revenue", "path", "/home", hour1, now, new BigDecimal("500.00")));
        entityManager.flush();

        // When
        List<AnalyticsRollup> results = repository.findByMetricAndDimension("page_views", "path");

        // Then
        assertEquals(2, results.size());
        assertTrue(
                results.stream()
                        .allMatch(
                                r ->
                                        "page_views".equals(r.metricName)
                                                && "path".equals(r.dimensionKey)));
        // Verify ordering
        assertEquals(hour1, results.get(0).periodStart);
        assertEquals(hour2, results.get(1).periodStart);
    }

    @Test
    @Transactional
    void testFindByMetricAndDimensionValue() {
        // Given
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant hour1 = now.minus(1, ChronoUnit.HOURS);
        Instant hour2 = now.minus(2, ChronoUnit.HOURS);

        repository.persist(
                createRollup(
                        "page_views", "path", "/templates", hour1, now, new BigDecimal("100.00")));
        repository.persist(
                createRollup(
                        "page_views", "path", "/templates", hour2, now, new BigDecimal("150.00")));
        repository.persist(
                createRollup("page_views", "path", "/home", hour1, now, new BigDecimal("200.00")));
        repository.persist(
                createRollup(
                        "conversions", "path", "/templates", hour1, now, new BigDecimal("5.00")));
        entityManager.flush();

        // When
        List<AnalyticsRollup> results =
                repository.findByMetricAndDimensionValue("page_views", "path", "/templates");

        // Then
        assertEquals(2, results.size());
        assertTrue(
                results.stream()
                        .allMatch(
                                r ->
                                        "page_views".equals(r.metricName)
                                                && "path".equals(r.dimensionKey)
                                                && "/templates".equals(r.dimensionValue)));
        // Verify DESC ordering
        assertEquals(hour1, results.get(0).periodStart);
        assertEquals(hour2, results.get(1).periodStart);
    }

    @Test
    @Transactional
    void testFindByTimeRange() {
        // Given
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant since = now.minus(5, ChronoUnit.HOURS);
        Instant until = now.minus(1, ChronoUnit.HOURS);

        // Inside range
        repository.persist(
                createRollup(
                        "page_views",
                        "path",
                        "/home",
                        now.minus(4, ChronoUnit.HOURS),
                        now.minus(3, ChronoUnit.HOURS),
                        new BigDecimal("100.00")));
        repository.persist(
                createRollup(
                        "page_views",
                        "path",
                        "/about",
                        now.minus(2, ChronoUnit.HOURS),
                        now.minus(1, ChronoUnit.HOURS),
                        new BigDecimal("200.00")));

        // Outside range (too old)
        repository.persist(
                createRollup(
                        "page_views",
                        "path",
                        "/old",
                        now.minus(10, ChronoUnit.HOURS),
                        now.minus(9, ChronoUnit.HOURS),
                        new BigDecimal("50.00")));

        // Outside range (too new)
        repository.persist(
                createRollup(
                        "page_views",
                        "path",
                        "/new",
                        now.minus(30, ChronoUnit.MINUTES),
                        now,
                        new BigDecimal("75.00")));

        entityManager.flush();

        // When
        List<AnalyticsRollup> results = repository.findByTimeRange(since, until);

        // Then
        assertEquals(2, results.size());
        assertTrue(
                results.stream()
                        .allMatch(
                                r ->
                                        !r.periodStart.isBefore(since)
                                                && r.periodStart.isBefore(until)));
    }

    @Test
    @Transactional
    void testFindByMetricAndTimeRange() {
        // Given
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant since = now.minus(5, ChronoUnit.HOURS);
        Instant until = now.minus(1, ChronoUnit.HOURS);

        Instant start1 = now.minus(4, ChronoUnit.HOURS);
        Instant end1 = now.minus(3, ChronoUnit.HOURS);
        Instant start2 = now.minus(2, ChronoUnit.HOURS);
        Instant end2 = now.minus(1, ChronoUnit.HOURS);

        // Matching metric and time range
        repository.persist(
                createRollup(
                        "page_views", "path", "/home", start1, end1, new BigDecimal("100.00")));
        repository.persist(
                createRollup(
                        "page_views", "path", "/about", start2, end2, new BigDecimal("200.00")));

        // Wrong metric
        repository.persist(
                createRollup("revenue", "path", "/home", start1, end1, new BigDecimal("500.00")));

        // Wrong time range
        repository.persist(
                createRollup(
                        "page_views",
                        "path",
                        "/old",
                        now.minus(10, ChronoUnit.HOURS),
                        now.minus(9, ChronoUnit.HOURS),
                        new BigDecimal("50.00")));

        entityManager.flush();

        // When
        List<AnalyticsRollup> results =
                repository.findByMetricAndTimeRange("page_views", since, until);

        // Then
        assertEquals(2, results.size());
        assertTrue(
                results.stream()
                        .allMatch(
                                r ->
                                        "page_views".equals(r.metricName)
                                                && !r.periodStart.isBefore(since)
                                                && !r.periodEnd.isAfter(until)));
        // Verify DESC ordering
        assertEquals(start2, results.get(0).periodStart);
        assertEquals(start1, results.get(1).periodStart);
    }

    @Test
    @Transactional
    void testSumByMetricAndTimeRange() {
        // Given
        // Truncate to microseconds since H2 doesn't preserve nanosecond precision
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant since = now.minus(5, ChronoUnit.HOURS);
        Instant until = now.minus(1, ChronoUnit.HOURS);

        Instant start1 = now.minus(4, ChronoUnit.HOURS);
        Instant end1 = now.minus(3, ChronoUnit.HOURS);
        Instant start2 = now.minus(2, ChronoUnit.HOURS);
        Instant end2 = now.minus(1, ChronoUnit.HOURS);
        Instant start3 = now.minus(3, ChronoUnit.HOURS);
        Instant end3 = now.minus(2, ChronoUnit.HOURS);

        // Should be summed (3 different periods for revenue)
        repository.persist(
                createRollup("revenue", "status", "PAID", start1, end1, new BigDecimal("100.00")));
        repository.persist(
                createRollup("revenue", "status", "PAID", start2, end2, new BigDecimal("200.00")));
        repository.persist(
                createRollup("revenue", "status", "PAID", start3, end3, new BigDecimal("300.00")));

        // Should not be summed (wrong metric)
        repository.persist(
                createRollup(
                        "page_views", "path", "/home", start1, end1, new BigDecimal("999.00")));

        // Should not be summed (outside time range)
        repository.persist(
                createRollup(
                        "revenue",
                        "status",
                        "PAID",
                        now.minus(10, ChronoUnit.HOURS),
                        now.minus(9, ChronoUnit.HOURS),
                        new BigDecimal("500.00")));

        entityManager.flush();

        // When
        BigDecimal sum = repository.sumByMetricAndTimeRange("revenue", since, until);

        // Then
        assertNotNull(sum);
        assertEquals(new BigDecimal("600.00"), sum);
    }

    @Test
    @Transactional
    void testSumByMetricAndTimeRange_NoResults() {
        // Given - no matching data

        // When
        BigDecimal sum =
                repository.sumByMetricAndTimeRange(
                        "nonexistent_metric",
                        Instant.now().minus(1, ChronoUnit.HOURS),
                        Instant.now());

        // Then
        assertNotNull(sum);
        assertEquals(BigDecimal.ZERO, sum);
    }

    @Test
    @Transactional
    void testPersist() {
        // Given
        AnalyticsRollup rollup =
                createRollup(
                        "conversions",
                        "template",
                        "template-123",
                        Instant.now().minus(1, ChronoUnit.HOURS),
                        Instant.now(),
                        new BigDecimal("42.00"));

        // When
        repository.persist(rollup);
        entityManager.flush();

        // Then
        assertNotNull(rollup.id);
        assertEquals(1, repository.count());
    }

    @Test
    @Transactional
    void testListAll() {
        // Given
        repository.persist(
                createRollup(
                        "metric1",
                        "dim1",
                        "val1",
                        Instant.now().minus(1, ChronoUnit.HOURS),
                        Instant.now(),
                        new BigDecimal("10.00")));
        repository.persist(
                createRollup(
                        "metric2",
                        "dim2",
                        "val2",
                        Instant.now().minus(2, ChronoUnit.HOURS),
                        Instant.now(),
                        new BigDecimal("20.00")));
        repository.persist(
                createRollup(
                        "metric3",
                        "dim3",
                        "val3",
                        Instant.now().minus(3, ChronoUnit.HOURS),
                        Instant.now(),
                        new BigDecimal("30.00")));
        entityManager.flush();

        // When
        List<AnalyticsRollup> all = repository.listAll();

        // Then
        assertEquals(3, all.size());
    }

    @Test
    @Transactional
    void testDelete() {
        // Given
        AnalyticsRollup rollup =
                createRollup(
                        "to_delete",
                        "dim",
                        "val",
                        Instant.now().minus(1, ChronoUnit.HOURS),
                        Instant.now(),
                        new BigDecimal("100.00"));
        repository.persist(rollup);
        entityManager.flush();
        java.util.UUID rollupId = rollup.id;

        // When
        repository.delete(rollup);
        entityManager.flush();

        // Then
        assertTrue(repository.findById(rollupId).isEmpty());
        assertEquals(0, repository.count());
    }

    @Test
    @Transactional
    void testCount() {
        // Given
        repository.persist(
                createRollup(
                        "metric1",
                        "dim1",
                        "val1",
                        Instant.now().minus(1, ChronoUnit.HOURS),
                        Instant.now(),
                        new BigDecimal("10.00")));
        repository.persist(
                createRollup(
                        "metric2",
                        "dim2",
                        "val2",
                        Instant.now().minus(2, ChronoUnit.HOURS),
                        Instant.now(),
                        new BigDecimal("20.00")));
        entityManager.flush();

        // When
        long count = repository.count();

        // Then
        assertEquals(2, count);
    }

    private AnalyticsRollup createRollup(
            String metricName,
            String dimensionKey,
            String dimensionValue,
            Instant periodStart,
            Instant periodEnd,
            BigDecimal value) {
        AnalyticsRollup rollup = new AnalyticsRollup();
        rollup.metricName = metricName;
        rollup.dimensionKey = dimensionKey;
        rollup.dimensionValue = dimensionValue;
        rollup.periodStart = periodStart;
        rollup.periodEnd = periodEnd;
        rollup.value = value;
        return rollup;
    }
}
