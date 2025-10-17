package villagecompute.calendar.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing guest user sessions and session-to-user conversions.
 * Handles session calendar operations, session expiration, and conversion to authenticated user accounts.
 */
@ApplicationScoped
public class SessionService {

    private static final Logger LOG = Logger.getLogger(SessionService.class);
    private static final int SESSION_EXPIRATION_DAYS = 30;

    @Inject
    CalendarService calendarService;

    /**
     * Generate a new session ID for guest users.
     * Uses UUID format for uniqueness and compatibility.
     *
     * @return New session ID string
     */
    public String generateSessionId() {
        String sessionId = UUID.randomUUID().toString();
        LOG.debugf("Generated new session ID: %s", sessionId);
        return sessionId;
    }

    /**
     * Convert guest session calendars to authenticated user calendars.
     * Delegates to CalendarService.convertSessionToUser() for the actual conversion.
     *
     * @param sessionId Session ID to convert
     * @param user User to assign calendars to
     * @return Number of calendars converted
     * @throws IllegalArgumentException if sessionId is null/blank or user is null
     */
    @Transactional
    public int convertSessionToUser(String sessionId, CalendarUser user) {
        LOG.infof("SessionService: Converting session to user: sessionId=%s, userId=%s",
                  sessionId, user != null ? user.id : null);

        // Delegate to CalendarService which already has the conversion logic
        return calendarService.convertSessionToUser(sessionId, user);
    }

    /**
     * Get all calendars for a given session ID.
     *
     * @param sessionId Session ID to query
     * @return List of calendars for this session
     */
    public List<UserCalendar> getSessionCalendars(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            LOG.warn("Attempted to get calendars with null/blank session ID");
            return List.of();
        }

        LOG.debugf("Fetching calendars for session: %s", sessionId);
        return UserCalendar.findBySession(sessionId).list();
    }

    /**
     * Delete expired guest session calendars.
     * Removes all calendars that have a sessionId (not converted to user) and
     * were last updated more than SESSION_EXPIRATION_DAYS ago.
     *
     * This method is called by the scheduled cleanup job.
     *
     * @return Number of expired calendars deleted
     */
    @Transactional
    public int deleteExpiredSessions() {
        LOG.info("Starting cleanup of expired guest session calendars");

        Instant expirationDate = Instant.now().minus(SESSION_EXPIRATION_DAYS, ChronoUnit.DAYS);

        // Find all calendars with sessionId that are expired
        List<UserCalendar> expiredCalendars = UserCalendar.find(
            "sessionId IS NOT NULL AND updated < ?1",
            expirationDate
        ).list();

        int count = 0;
        for (UserCalendar calendar : expiredCalendars) {
            LOG.debugf("Deleting expired session calendar: id=%s, sessionId=%s, updated=%s",
                      calendar.id, calendar.sessionId, calendar.updated);
            calendar.delete();
            count++;
        }

        LOG.infof("Deleted %d expired guest session calendars (older than %d days)",
                 count, SESSION_EXPIRATION_DAYS);

        return count;
    }

    /**
     * Check if a session has any calendars associated with it.
     *
     * @param sessionId Session ID to check
     * @return true if session has calendars, false otherwise
     */
    public boolean hasSessionCalendars(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }

        long count = UserCalendar.count("sessionId = ?1", sessionId);
        return count > 0;
    }

    /**
     * Get the count of calendars for a session.
     *
     * @param sessionId Session ID to count calendars for
     * @return Number of calendars in this session
     */
    public long getSessionCalendarCount(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return 0;
        }

        return UserCalendar.count("sessionId = ?1", sessionId);
    }
}
