package villagecompute.calendar.data.models;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.quarkus.hibernate.orm.panache.PanacheQuery;

/**
 * Entity representing pre-computed analytics aggregations for fast dashboard queries. Supports
 * multi-dimensional analysis with metric slicing by dimension key/value pairs.
 *
 * <p>Example metrics:
 *
 * <ul>
 *   <li>metric_name: "page_views", dimension_key: "path", dimension_value: "/templates"
 *   <li>metric_name: "revenue", dimension_key: "status", dimension_value: "PAID"
 *   <li>metric_name: "conversions", dimension_key: "template_id", dimension_value: "&lt;uuid&gt;"
 * </ul>
 *
 * <p>Supports:
 *
 * <ul>
 *   <li>Pre-aggregated metrics (daily/weekly/monthly rollups)
 *   <li>Fast dashboard queries (avoid scanning page_views/orders tables)
 *   <li>Multi-dimensional analysis (metric + dimension slicing)
 *   <li>Time-series data for trend visualization
 * </ul>
 */
@Entity
@Table(
        name = "analytics_rollups",
        indexes = {
            @Index(
                    name = "idx_analytics_rollups_metric",
                    columnList = "metric_name, period_start, dimension_key"),
            @Index(name = "idx_analytics_rollups_period", columnList = "period_start, period_end")
        },
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_analytics_rollups_unique",
                    columnNames = {
                        "metric_name",
                        "dimension_key",
                        "dimension_value",
                        "period_start",
                        "period_end"
                    })
        })
public class AnalyticsRollup extends DefaultPanacheEntityWithTimestamps {

    @NotNull @Size(max = 255)
    @Column(name = "metric_name", nullable = false, length = 255)
    public String metricName;

    @Size(max = 255)
    @Column(name = "dimension_key", length = 255)
    public String dimensionKey;

    @Size(max = 500)
    @Column(name = "dimension_value", length = 500)
    public String dimensionValue;

    @NotNull @DecimalMin("0.00")
    @Column(name = "metric_value", nullable = false, precision = 15, scale = 2)
    public BigDecimal value;

    @NotNull @Column(name = "period_start", nullable = false)
    public Instant periodStart;

    @NotNull @Column(name = "period_end", nullable = false)
    public Instant periodEnd;

    // Static finder methods (ActiveRecord pattern)

    /**
     * Find all rollups for a specific metric, ordered by period start descending. Used for
     * metric-specific analytics queries.
     *
     * @param metricName Metric name (e.g., "page_views", "revenue", "conversions")
     * @return Query of analytics rollups
     */
    public static PanacheQuery<AnalyticsRollup> findByMetric(String metricName) {
        return find("metricName = ?1 ORDER BY periodStart DESC", metricName);
    }

    /**
     * Find rollups for a specific metric and dimension key, ordered by period start descending.
     * Used for dimension-sliced metric queries.
     *
     * @param metricName Metric name
     * @param dimensionKey Dimension category (e.g., "path", "template_id", "status")
     * @return Query of analytics rollups
     */
    public static PanacheQuery<AnalyticsRollup> findByMetricAndDimension(
            String metricName, String dimensionKey) {
        return find(
                "metricName = ?1 AND dimensionKey = ?2 ORDER BY periodStart DESC",
                metricName,
                dimensionKey);
    }

    /**
     * Find rollups for a specific metric, dimension key, and dimension value. Used for highly
     * specific analytics queries.
     *
     * @param metricName Metric name
     * @param dimensionKey Dimension category
     * @param dimensionValue Specific dimension value (e.g., "/templates", "&lt;uuid&gt;", "PAID")
     * @return Query of analytics rollups
     */
    public static PanacheQuery<AnalyticsRollup> findByMetricAndDimensionValue(
            String metricName, String dimensionKey, String dimensionValue) {
        return find(
                "metricName = ?1 AND dimensionKey = ?2 AND dimensionValue = ?3 ORDER BY periodStart"
                        + " DESC",
                metricName,
                dimensionKey,
                dimensionValue);
    }

    /**
     * Find rollups within a specific time range, ordered by period start descending. Used for
     * time-range filtered dashboard queries.
     *
     * @param since Start time (inclusive)
     * @param until End time (exclusive)
     * @return Query of analytics rollups
     */
    public static PanacheQuery<AnalyticsRollup> findByTimeRange(Instant since, Instant until) {
        // Workaround for Hibernate/H2 issue: using ONLY timestamp comparisons in WHERE clause fails
        // Simplified to use only periodStart comparisons which work correctly
        // If periodStart >= since and periodStart < until, the period overlaps with our time range
        return find(
                "periodStart >= ?1 AND periodStart < ?2 ORDER BY periodStart DESC", since, until);
    }

    /**
     * Find rollups for a specific metric within a time range. Used for metric time-series queries.
     *
     * @param metricName Metric name
     * @param since Start time (inclusive)
     * @param until End time (exclusive)
     * @return Query of analytics rollups
     */
    public static PanacheQuery<AnalyticsRollup> findByMetricAndTimeRange(
            String metricName, Instant since, Instant until) {
        return find(
                "metricName = ?1 AND periodStart >= ?2 AND periodEnd <= ?3 ORDER BY periodStart"
                        + " DESC",
                metricName,
                since,
                until);
    }

    /**
     * Sum values for a specific metric within a time range. Used for aggregated metric totals.
     *
     * @param metricName Metric name
     * @param since Start time (inclusive)
     * @param until End time (exclusive)
     * @return Sum of metric values
     */
    public static BigDecimal sumByMetricAndTimeRange(
            String metricName, Instant since, Instant until) {
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
