package villagecompute.calendar.data.repositories;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import villagecompute.calendar.data.models.AnalyticsRollup;

import io.quarkus.hibernate.orm.panache.PanacheRepository;

/**
 * Repository for AnalyticsRollup entities. Provides custom query methods for analytics aggregation
 * and time-series queries.
 */
@ApplicationScoped
public class AnalyticsRollupRepository implements PanacheRepository<AnalyticsRollup> {

    /**
     * Find analytics rollup by ID.
     *
     * @param id Rollup ID
     * @return Optional containing the rollup if found
     */
    public java.util.Optional<AnalyticsRollup> findById(java.util.UUID id) {
        return find("id", id).firstResultOptional();
    }

    /**
     * Find all rollups for a specific metric, ordered by period start descending. Used for
     * metric-specific analytics queries.
     *
     * @param metricName Metric name (e.g., "page_views", "revenue", "conversions")
     * @return List of analytics rollups
     */
    public List<AnalyticsRollup> findByMetric(String metricName) {
        return find("metricName = ?1 ORDER BY periodStart DESC", metricName).list();
    }

    /**
     * Find rollups for a specific metric and dimension key, ordered by period start descending.
     * Used for dimension-sliced metric queries.
     *
     * @param metricName Metric name
     * @param dimensionKey Dimension category (e.g., "path", "template_id", "status")
     * @return List of analytics rollups
     */
    public List<AnalyticsRollup> findByMetricAndDimension(String metricName, String dimensionKey) {
        return find(
                        "metricName = ?1 AND dimensionKey = ?2 ORDER BY periodStart DESC",
                        metricName,
                        dimensionKey)
                .list();
    }

    /**
     * Find rollups for a specific metric, dimension key, and dimension value. Used for highly
     * specific analytics queries.
     *
     * @param metricName Metric name
     * @param dimensionKey Dimension category
     * @param dimensionValue Specific dimension value (e.g., "/templates", "<uuid>", "PAID")
     * @return List of analytics rollups
     */
    public List<AnalyticsRollup> findByMetricAndDimensionValue(
            String metricName, String dimensionKey, String dimensionValue) {
        return find(
                        "metricName = ?1 AND dimensionKey = ?2 AND dimensionValue = ?3 ORDER BY"
                                + " periodStart DESC",
                        metricName,
                        dimensionKey,
                        dimensionValue)
                .list();
    }

    /**
     * Find rollups within a specific time range, ordered by period start descending. Used for
     * time-range filtered dashboard queries.
     *
     * @param since Start time (inclusive)
     * @param until End time (exclusive)
     * @return List of analytics rollups
     */
    public List<AnalyticsRollup> findByTimeRange(Instant since, Instant until) {
        return find(
                        "periodStart >= ?1 AND periodStart < ?2 ORDER BY periodStart DESC",
                        since,
                        until)
                .list();
    }

    /**
     * Find rollups for a specific metric within a time range. Used for metric time-series queries.
     *
     * @param metricName Metric name
     * @param since Start time (inclusive)
     * @param until End time (exclusive)
     * @return List of analytics rollups
     */
    public List<AnalyticsRollup> findByMetricAndTimeRange(
            String metricName, Instant since, Instant until) {
        return find(
                        "metricName = ?1 AND periodStart >= ?2 AND periodEnd <= ?3 ORDER BY"
                                + " periodStart DESC",
                        metricName,
                        since,
                        until)
                .list();
    }

    /**
     * Sum values for a specific metric within a time range. Used for aggregated metric totals.
     *
     * @param metricName Metric name
     * @param since Start time (inclusive)
     * @param until End time (exclusive)
     * @return Sum of metric values
     */
    public BigDecimal sumByMetricAndTimeRange(String metricName, Instant since, Instant until) {
        return find(
                        "SELECT COALESCE(SUM(value), 0) FROM AnalyticsRollup "
                                + "WHERE metricName = ?1 AND periodStart >= ?2 AND periodEnd <= ?3",
                        metricName,
                        since,
                        until)
                .project(BigDecimal.class)
                .firstResult();
    }
}
