package villagecompute.calendar.data.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import villagecompute.calendar.data.models.PageView;

import io.quarkus.hibernate.orm.panache.PanacheRepository;

/**
 * Repository for PageView entities. Provides custom query methods for analytics tracking and user behavior analysis.
 */
@ApplicationScoped
public class PageViewRepository implements PanacheRepository<PageView> {

    /**
     * Find page view by ID.
     *
     * @param id
     *            Page view ID
     * @return Optional containing the page view if found
     */
    public Optional<PageView> findById(UUID id) {
        return find("id", id).firstResultOptional();
    }

    /**
     * Find all page views for a specific session, ordered by creation time. Used for session reconstruction and funnel
     * analysis.
     *
     * @param sessionId
     *            Session identifier
     * @return List of page views
     */
    public List<PageView> findBySession(String sessionId) {
        return find("sessionId = ?1 ORDER BY created ASC", sessionId).list();
    }

    /**
     * Find all page views for a specific user, ordered by creation time descending. Used for user behavior tracking.
     *
     * @param userId
     *            User ID
     * @return List of page views
     */
    public List<PageView> findByUser(UUID userId) {
        return find("user.id = ?1 ORDER BY created DESC", userId).list();
    }

    /**
     * Find all page views for a specific path, ordered by creation time descending. Used for path analysis and popular
     * pages tracking.
     *
     * @param path
     *            URL path (e.g., "/templates", "/calendar/123/edit")
     * @return List of page views
     */
    public List<PageView> findByPath(String path) {
        return find("path = ?1 ORDER BY created DESC", path).list();
    }

    /**
     * Find page views within a time range, ordered by creation time descending. Used for time-based analytics queries.
     *
     * @param since
     *            Start time (inclusive)
     * @param until
     *            End time (exclusive)
     * @return List of page views
     */
    public List<PageView> findByTimeRange(Instant since, Instant until) {
        return find("created >= ?1 AND created < ?2 ORDER BY created DESC", since, until).list();
    }

    /**
     * Find page views for a specific referrer source. Used for traffic source attribution.
     *
     * @param referrer
     *            HTTP Referer header
     * @return List of page views
     */
    public List<PageView> findByReferrer(String referrer) {
        return find("referrer = ?1 ORDER BY created DESC", referrer).list();
    }

    /**
     * Count page views for a specific path within a time range. Used for analytics dashboards.
     *
     * @param path
     *            URL path
     * @param since
     *            Start time (inclusive)
     * @param until
     *            End time (exclusive)
     * @return Count of page views
     */
    public long countByPathAndTimeRange(String path, Instant since, Instant until) {
        return count("path = ?1 AND created >= ?2 AND created < ?3", path, since, until);
    }
}
