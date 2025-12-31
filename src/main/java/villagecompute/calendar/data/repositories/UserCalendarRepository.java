package villagecompute.calendar.data.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import villagecompute.calendar.data.models.UserCalendar;

import io.quarkus.hibernate.orm.panache.PanacheRepository;

/**
 * Repository for UserCalendar entities. Provides custom query methods for user calendar management.
 */
@ApplicationScoped
public class UserCalendarRepository implements PanacheRepository<UserCalendar> {

    /**
     * Find calendars by authenticated user and year. This is the required custom query method from the task
     * specification.
     *
     * @param userId
     *            User ID
     * @param year
     *            Calendar year
     * @return List of calendars for the user and year
     */
    public List<UserCalendar> findByUserAndYear(UUID userId, Integer year) {
        return find("user.id = ?1 AND year = ?2 ORDER BY updated DESC", userId, year).list();
    }

    /**
     * Find all calendars for a specific user.
     *
     * @param userId
     *            User ID
     * @return List of user's calendars
     */
    public List<UserCalendar> findByUser(UUID userId) {
        return find("user.id = ?1 ORDER BY year DESC, updated DESC", userId).list();
    }

    /**
     * Find calendars by session ID (for anonymous users).
     *
     * @param sessionId
     *            Session identifier
     * @return List of calendars ordered by update time
     */
    public List<UserCalendar> findBySession(String sessionId) {
        return find("sessionId = ?1 ORDER BY updated DESC", sessionId).list();
    }

    /**
     * Find a public calendar by ID.
     *
     * @param id
     *            Calendar ID
     * @return Optional containing the calendar if found and public
     */
    public Optional<UserCalendar> findPublicById(UUID id) {
        return find("id = ?1 AND isPublic = true", id).firstResultOptional();
    }

    /**
     * Find all public calendars.
     *
     * @param limit
     *            Maximum number of results
     * @return List of public calendars
     */
    public List<UserCalendar> findPublicCalendars(int limit) {
        return find("isPublic = true ORDER BY updated DESC").page(0, limit).list();
    }

    /**
     * Find calendars by template.
     *
     * @param templateId
     *            Template ID
     * @return List of calendars using the template
     */
    public List<UserCalendar> findByTemplate(UUID templateId) {
        return find("template.id = ?1 ORDER BY created DESC", templateId).list();
    }

    /**
     * Find calendars by year.
     *
     * @param year
     *            Calendar year
     * @return List of calendars for the specified year
     */
    public List<UserCalendar> findByYear(Integer year) {
        return find("year = ?1 ORDER BY updated DESC", year).list();
    }
}
