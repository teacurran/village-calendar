package villagecompute.calendar.data.models;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.quarkus.hibernate.orm.panache.PanacheQuery;

/**
 * Entity representing analytics tracking for user navigation and behavior analysis. Captures user
 * navigation patterns and behavior for product insights.
 *
 * <p>Supports:
 *
 * <ul>
 *   <li>User behavior tracking (anonymous and authenticated)
 *   <li>Funnel analysis (template browsing -> customization -> checkout)
 *   <li>Session reconstruction via session_id
 *   <li>Referral source tracking
 * </ul>
 */
@Entity
@Table(
        name = "page_views",
        indexes = {
            @Index(name = "idx_page_views_session", columnList = "session_id, created DESC"),
            @Index(name = "idx_page_views_user", columnList = "user_id, created DESC"),
            @Index(name = "idx_page_views_path", columnList = "path, created DESC"),
            @Index(name = "idx_page_views_created", columnList = "created DESC")
        })
public class PageView extends DefaultPanacheEntityWithTimestamps {

    @NotNull @Size(max = 255)
    @Column(name = "session_id", nullable = false, length = 255)
    public String sessionId;

    @ManyToOne
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_page_views_user"))
    public CalendarUser user;

    @NotNull @Size(max = 500)
    @Column(nullable = false, length = 500)
    public String path;

    @Size(max = 500)
    @Column(length = 500)
    public String referrer;

    @Size(max = 1000)
    @Column(name = "user_agent", length = 1000)
    public String userAgent;

    // Static finder methods (ActiveRecord pattern)

    /**
     * Find all page views for a specific session, ordered by creation time. Used for session
     * reconstruction and funnel analysis.
     *
     * @param sessionId Session identifier
     * @return Query of page views
     */
    public static PanacheQuery<PageView> findBySession(String sessionId) {
        return find("sessionId = ?1 ORDER BY created ASC", sessionId);
    }

    /**
     * Find all page views for a specific user, ordered by creation time descending. Used for user
     * behavior tracking.
     *
     * @param userId User ID
     * @return Query of page views
     */
    public static PanacheQuery<PageView> findByUser(UUID userId) {
        return find("user.id = ?1 ORDER BY created DESC", userId);
    }

    /**
     * Find all page views for a specific path, ordered by creation time descending. Used for path
     * analysis and popular pages tracking.
     *
     * @param path URL path (e.g., "/templates", "/calendar/123/edit")
     * @return Query of page views
     */
    public static PanacheQuery<PageView> findByPath(String path) {
        return find("path = ?1 ORDER BY created DESC", path);
    }

    /**
     * Find page views within a time range, ordered by creation time descending. Used for time-based
     * analytics queries.
     *
     * @param since Start time (inclusive)
     * @param until End time (exclusive)
     * @return Query of page views
     */
    public static PanacheQuery<PageView> findByTimeRange(Instant since, Instant until) {
        return find("created >= ?1 AND created < ?2 ORDER BY created DESC", since, until);
    }

    /**
     * Find page views for a specific referrer source. Used for traffic source attribution.
     *
     * @param referrer HTTP Referer header
     * @return Query of page views
     */
    public static PanacheQuery<PageView> findByReferrer(String referrer) {
        return find("referrer = ?1 ORDER BY created DESC", referrer);
    }

    /**
     * Count page views for a specific path within a time range. Used for analytics dashboards.
     *
     * @param path URL path
     * @param since Start time (inclusive)
     * @param until End time (exclusive)
     * @return Count of page views
     */
    public static long countByPathAndTimeRange(String path, Instant since, Instant until) {
        return count("path = ?1 AND created >= ?2 AND created < ?3", path, since, until);
    }
}
