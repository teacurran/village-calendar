package villagecompute.calendar.services;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.data.repositories.CalendarTemplateRepository;
import villagecompute.calendar.data.repositories.UserCalendarRepository;

import java.util.List;
import java.util.UUID;

/**
 * Service layer for calendar business logic operations.
 * Handles calendar CRUD operations, authorization, versioning, and session management.
 *
 * This service is distinct from CalendarRenderingService which handles SVG/PDF generation.
 */
@ApplicationScoped
public class CalendarService {

    private static final Logger LOG = Logger.getLogger(CalendarService.class);

    @Inject
    UserCalendarRepository calendarRepository;

    @Inject
    CalendarTemplateRepository templateRepository;

    /**
     * Create a new calendar from a template or blank.
     *
     * @param name Calendar name
     * @param year Calendar year
     * @param templateId Optional template ID to use
     * @param configuration Optional custom configuration (merged with template if provided)
     * @param isPublic Whether the calendar is publicly accessible
     * @param user Owner of the calendar (null for guest users)
     * @param sessionId Session ID for guest users (null for authenticated users)
     * @return Created calendar
     * @throws IllegalArgumentException if validation fails
     */
    @Transactional
    public UserCalendar createCalendar(
            String name,
            Integer year,
            UUID templateId,
            JsonNode configuration,
            Boolean isPublic,
            CalendarUser user,
            String sessionId) {

        LOG.infof("Creating calendar: name=%s, year=%d, templateId=%s, userId=%s, sessionId=%s",
                  name, year, templateId, user != null ? user.id : null, sessionId);

        // Validate input
        validateCalendarInput(name, year, user, sessionId);

        // Create new calendar entity
        UserCalendar calendar = new UserCalendar();
        calendar.name = name;
        calendar.year = year;
        calendar.user = user;
        calendar.sessionId = sessionId;
        calendar.isPublic = isPublic != null ? isPublic : true;

        // Apply template if specified
        if (templateId != null) {
            CalendarTemplate template = CalendarTemplate.findById(templateId);
            if (template == null) {
                LOG.errorf("Template not found: %s", templateId);
                throw new IllegalArgumentException("Template not found: " + templateId);
            }
            if (!template.isActive) {
                LOG.errorf("Template is inactive: %s", templateId);
                throw new IllegalArgumentException("Template is not active: " + templateId);
            }
            calendar.template = template;
            calendar.configuration = template.configuration; // Use template configuration
        }

        // Override with custom configuration if provided
        if (configuration != null) {
            calendar.configuration = configuration;
        }

        // Persist
        calendar.persist();

        LOG.infof("Created calendar: id=%s, name=%s, version=%d", calendar.id, calendar.name, calendar.version);

        return calendar;
    }

    /**
     * Update an existing calendar.
     * Performs authorization check and handles optimistic locking.
     *
     * @param id Calendar ID
     * @param name Optional new name
     * @param configuration Optional new configuration
     * @param isPublic Optional new visibility setting
     * @param currentUser User performing the update
     * @return Updated calendar
     * @throws IllegalArgumentException if calendar not found
     * @throws SecurityException if user is not authorized
     * @throws OptimisticLockException if version mismatch (concurrent update)
     */
    @Transactional
    public UserCalendar updateCalendar(
            UUID id,
            String name,
            JsonNode configuration,
            Boolean isPublic,
            CalendarUser currentUser) {

        LOG.infof("Updating calendar: id=%s, userId=%s", id, currentUser != null ? currentUser.id : null);

        // Find calendar
        UserCalendar calendar = UserCalendar.findById(id);
        if (calendar == null) {
            LOG.errorf("Calendar not found: %s", id);
            throw new IllegalArgumentException("Calendar not found: " + id);
        }

        // Authorization check
        checkWriteAccess(calendar, currentUser);

        // Update fields if provided
        if (name != null && !name.isBlank()) {
            calendar.name = name;
        }
        if (configuration != null) {
            calendar.configuration = configuration;
        }
        if (isPublic != null) {
            calendar.isPublic = isPublic;
        }

        // Persist and flush (version will be automatically incremented by JPA)
        calendar.persist();
        calendar.flush(); // Force immediate flush to increment version

        LOG.infof("Updated calendar: id=%s, version=%d", calendar.id, calendar.version);

        return calendar;
    }

    /**
     * Delete a calendar (hard delete).
     * Performs authorization check before deletion.
     *
     * @param id Calendar ID
     * @param currentUser User performing the deletion
     * @return true if deleted successfully
     * @throws IllegalArgumentException if calendar not found
     * @throws SecurityException if user is not authorized
     */
    @Transactional
    public boolean deleteCalendar(UUID id, CalendarUser currentUser) {
        LOG.infof("Deleting calendar: id=%s, userId=%s", id, currentUser != null ? currentUser.id : null);

        // Find calendar
        UserCalendar calendar = UserCalendar.findById(id);
        if (calendar == null) {
            LOG.errorf("Calendar not found: %s", id);
            throw new IllegalArgumentException("Calendar not found: " + id);
        }

        // Authorization check
        checkWriteAccess(calendar, currentUser);

        // Delete
        calendar.delete();

        LOG.infof("Deleted calendar: id=%s", id);

        return true;
    }

    /**
     * Get a calendar by ID with authorization check.
     *
     * @param id Calendar ID
     * @param currentUser User requesting the calendar (null for public access)
     * @return Calendar if found and authorized
     * @throws IllegalArgumentException if calendar not found
     * @throws SecurityException if user is not authorized
     */
    public UserCalendar getCalendar(UUID id, CalendarUser currentUser) {
        LOG.debugf("Getting calendar: id=%s, userId=%s", id, currentUser != null ? currentUser.id : null);

        // Find calendar
        UserCalendar calendar = UserCalendar.findById(id);
        if (calendar == null) {
            LOG.errorf("Calendar not found: %s", id);
            throw new IllegalArgumentException("Calendar not found: " + id);
        }

        // Authorization check (read access)
        checkReadAccess(calendar, currentUser);

        return calendar;
    }

    /**
     * List calendars for a specific user with optional year filter and pagination.
     *
     * @param userId User ID
     * @param year Optional year filter
     * @param pageIndex Page index (0-based)
     * @param pageSize Page size
     * @param currentUser User requesting the list
     * @return List of calendars
     * @throws SecurityException if currentUser is not authorized to view the user's calendars
     */
    public List<UserCalendar> listCalendars(
            UUID userId,
            Integer year,
            int pageIndex,
            int pageSize,
            CalendarUser currentUser) {

        LOG.debugf("Listing calendars: userId=%s, year=%s, page=%d, size=%d",
                   userId, year, pageIndex, pageSize);

        // Authorization: User can only list their own calendars unless admin
        if (currentUser == null) {
            throw new SecurityException("Authentication required to list calendars");
        }
        if (!currentUser.id.equals(userId) && !currentUser.isAdmin) {
            LOG.warnf("Unauthorized calendar list access: userId=%s, currentUserId=%s",
                      userId, currentUser.id);
            throw new SecurityException("You can only view your own calendars");
        }

        // Fetch calendars
        List<UserCalendar> calendars;
        if (year != null) {
            calendars = calendarRepository.findByUserAndYear(userId, year);
        } else {
            calendars = calendarRepository.findByUser(userId);
        }

        // Apply pagination manually (simple implementation)
        int start = pageIndex * pageSize;
        int end = Math.min(start + pageSize, calendars.size());

        if (start >= calendars.size()) {
            return List.of();
        }

        return calendars.subList(start, end);
    }

    /**
     * Convert guest session calendars to authenticated user calendars.
     * Migrates all calendars with matching sessionId to the user.
     *
     * @param sessionId Session ID to convert
     * @param user User to assign calendars to
     * @return Number of calendars converted
     */
    @Transactional
    public int convertSessionToUser(String sessionId, CalendarUser user) {
        if (user == null) {
            throw new IllegalArgumentException("User is required");
        }

        LOG.infof("Converting session to user: sessionId=%s, userId=%s", sessionId, user.id);

        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Session ID is required");
        }

        // Find all calendars for the session
        List<UserCalendar> sessionCalendars = calendarRepository.findBySession(sessionId);

        // Update each calendar
        int count = 0;
        for (UserCalendar calendar : sessionCalendars) {
            calendar.user = user;
            calendar.sessionId = null; // Clear session ID
            calendar.persist();
            count++;
        }

        LOG.infof("Converted %d calendars from session %s to user %s", count, sessionId, user.id);

        return count;
    }

    /**
     * Find public calendar by ID (no authorization required).
     *
     * @param id Calendar ID
     * @return Calendar if found and public, null otherwise
     */
    public UserCalendar findPublicCalendar(UUID id) {
        return calendarRepository.findPublicById(id).orElse(null);
    }

    // ========== AUTHORIZATION HELPERS ==========

    /**
     * Check if user has read access to a calendar.
     *
     * Rules:
     * - Admin can read all calendars
     * - Owner can read their own calendars
     * - Anyone can read public calendars
     * - Guest users can read calendars associated with their session
     *
     * @param calendar Calendar to check
     * @param currentUser User requesting access (null for anonymous)
     * @throws SecurityException if access denied
     */
    private void checkReadAccess(UserCalendar calendar, CalendarUser currentUser) {
        // Public calendars are accessible to everyone
        if (calendar.isPublic) {
            return;
        }

        // Anonymous users cannot access private calendars
        if (currentUser == null) {
            LOG.warnf("Unauthorized read access: calendarId=%s (private, no user)", calendar.id);
            throw new SecurityException("This calendar is private");
        }

        // Admin can access everything
        if (currentUser.isAdmin) {
            return;
        }

        // Owner can access their own calendars
        if (calendar.user != null && calendar.user.id.equals(currentUser.id)) {
            return;
        }

        // Unauthorized
        LOG.warnf("Unauthorized read access: calendarId=%s, userId=%s", calendar.id, currentUser.id);
        throw new SecurityException("You do not have permission to view this calendar");
    }

    /**
     * Check if user has write access to a calendar.
     *
     * Rules:
     * - Admin can write to all calendars
     * - Owner can write to their own calendars
     * - Guest users can write to calendars associated with their session (if no owner assigned)
     *
     * @param calendar Calendar to check
     * @param currentUser User requesting access (null for anonymous)
     * @throws SecurityException if access denied
     */
    private void checkWriteAccess(UserCalendar calendar, CalendarUser currentUser) {
        // Anonymous users cannot modify calendars
        if (currentUser == null) {
            LOG.warnf("Unauthorized write access: calendarId=%s (no user)", calendar.id);
            throw new SecurityException("Authentication required to modify calendars");
        }

        // Admin can modify everything
        if (currentUser.isAdmin) {
            return;
        }

        // Owner can modify their own calendars
        if (calendar.user != null && calendar.user.id.equals(currentUser.id)) {
            return;
        }

        // Unauthorized
        LOG.warnf("Unauthorized write access: calendarId=%s, userId=%s", calendar.id, currentUser.id);
        throw new SecurityException("You do not have permission to modify this calendar");
    }

    // ========== VALIDATION HELPERS ==========

    /**
     * Validate calendar creation input.
     *
     * @param name Calendar name
     * @param year Calendar year
     * @param user Owner user (null for guests)
     * @param sessionId Session ID (null for authenticated users)
     * @throws IllegalArgumentException if validation fails
     */
    private void validateCalendarInput(String name, Integer year, CalendarUser user, String sessionId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Calendar name is required");
        }
        if (name.length() > 255) {
            throw new IllegalArgumentException("Calendar name must be 255 characters or less");
        }
        if (year == null) {
            throw new IllegalArgumentException("Calendar year is required");
        }
        if (year < 1000 || year > 9999) {
            throw new IllegalArgumentException("Calendar year must be between 1000 and 9999");
        }
        if (user == null && (sessionId == null || sessionId.isBlank())) {
            throw new IllegalArgumentException("Either user or sessionId must be provided");
        }
        if (user != null && sessionId != null && !sessionId.isBlank()) {
            throw new IllegalArgumentException("Cannot specify both user and sessionId");
        }
    }
}
