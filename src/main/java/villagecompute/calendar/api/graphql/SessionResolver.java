package villagecompute.calendar.api.graphql;

import java.util.List;
import java.util.UUID;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;

import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.services.AuthenticationService;
import villagecompute.calendar.services.CalendarService;
import villagecompute.calendar.services.SessionService;
import villagecompute.calendar.util.Roles;

/**
 * GraphQL resolver for guest session operations. Handles session-based calendar management for anonymous users and
 * session-to-user conversion on authentication.
 */
@GraphQLApi
@ApplicationScoped
public class SessionResolver {

    private static final Logger LOG = Logger.getLogger(SessionResolver.class);

    @Inject
    JsonWebToken jwt;

    @Inject
    AuthenticationService authService;

    @Inject
    SessionService sessionService;

    @Inject
    CalendarService calendarService;

    // ==================================================================
    // QUERIES
    // ==================================================================

    /**
     * Get all calendars for a session (guest user). No authentication required - uses sessionId to identify calendars.
     *
     * @param sessionId
     *            Session identifier
     * @return List of calendars for this session
     */
    @Query("sessionCalendars")
    @Description("Get all calendars for a guest session. No authentication required.")
    @PermitAll
    public List<UserCalendar> sessionCalendars(
            @Name("sessionId") @Description("Guest session ID") @NotNull final String sessionId) {
        LOG.infof("Query: sessionCalendars(sessionId=%s)", sessionId);

        List<UserCalendar> calendars = sessionService.getSessionCalendars(sessionId);

        LOG.infof("Found %d calendars for session %s", calendars.size(), sessionId);

        return calendars;
    }

    /**
     * Check if a session has any calendars. Useful for frontend to determine if session conversion is needed.
     *
     * @param sessionId
     *            Session identifier
     * @return true if session has calendars, false otherwise
     */
    @Query("hasSessionCalendars")
    @Description("Check if a session has any calendars. Used to determine if session conversion is" + " needed.")
    @PermitAll
    public boolean hasSessionCalendars(
            @Name("sessionId") @Description("Guest session ID") @NotNull final String sessionId) {
        LOG.debugf("Query: hasSessionCalendars(sessionId=%s)", sessionId);

        boolean hasCalendars = sessionService.hasSessionCalendars(sessionId);

        LOG.debugf("Session %s has calendars: %b", sessionId, hasCalendars);

        return hasCalendars;
    }

    // ==================================================================
    // MUTATIONS
    // ==================================================================

    /**
     * Create a new calendar for a guest session. No authentication required - uses sessionId for ownership.
     *
     * @param sessionId
     *            Session identifier
     * @param name
     *            Calendar name
     * @param year
     *            Calendar year
     * @param templateId
     *            Template ID to base calendar on
     * @return Created calendar
     */
    @Mutation("createSessionCalendar")
    @Description("Create a new calendar for a guest session. No authentication required.")
    @PermitAll
    @Transactional
    public UserCalendar createSessionCalendar(
            @Name("sessionId") @Description("Guest session ID") @NotNull final String sessionId,
            @Name("name") @Description("Calendar name") @NotNull final String name,
            @Name("year") @Description("Calendar year") @NotNull final Integer year,
            @Name("templateId") @Description("Template ID to base calendar on") @NotNull final String templateId) {
        LOG.infof("Mutation: createSessionCalendar(sessionId=%s, name=%s, year=%d, templateId=%s)", sessionId, name,
                year, templateId);

        // Validate session ID format
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Session ID is required");
        }

        // Parse template ID
        UUID templateUuid;
        try {
            templateUuid = UUID.fromString(templateId);
        } catch (IllegalArgumentException e) {
            LOG.errorf("Invalid template ID format: %s", templateId);
            throw new IllegalArgumentException("Invalid template ID format");
        }

        // Create calendar using service (user=null for guest session)
        UserCalendar calendar = calendarService.createCalendar(name, year, templateUuid, null, // configuration - use
                                                                                               // template defaults
                false, // isPublic
                null, // user - guest session
                sessionId);

        LOG.infof("Created session calendar: %s (ID: %s) for session %s", calendar.name, calendar.id, sessionId);

        return calendar;
    }

    /**
     * Convert guest session calendars to authenticated user calendars. Called after successful OAuth login to transfer
     * guest calendars. Requires authentication.
     *
     * @param sessionId
     *            Guest session ID to convert
     * @return Number of calendars converted
     */
    @Mutation("convertGuestSession")
    @Description("Convert anonymous guest session to authenticated user account. "
            + "Links all calendars from the guest session to the authenticated user. "
            + "Returns the number of calendars converted.")
    @RolesAllowed(Roles.USER)
    @Transactional
    public Integer convertGuestSession(
            @Name("sessionId") @Description("Guest session ID to convert") @NotNull final String sessionId) {
        LOG.infof("Mutation: convertGuestSession(sessionId=%s)", sessionId);

        CalendarUser user = authService.requireCurrentUser(jwt);

        // Convert session calendars to user calendars
        int convertedCount = sessionService.convertSessionToUser(sessionId, user);

        LOG.infof("Converted %d calendars from session %s to user %s (ID: %s)", convertedCount, sessionId, user.email,
                user.id);

        return convertedCount;
    }
}
