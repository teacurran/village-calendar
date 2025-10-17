package villagecompute.calendar.api.graphql;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.NonNull;
import org.eclipse.microprofile.graphql.Query;
import org.eclipse.microprofile.graphql.Source;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;
import villagecompute.calendar.api.graphql.inputs.CalendarInput;
import villagecompute.calendar.api.graphql.inputs.CalendarUpdateInput;
import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.services.AuthenticationService;
import villagecompute.calendar.services.CalendarService;
import villagecompute.calendar.services.EventService;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * GraphQL resolver for calendar queries and mutations.
 * Handles user authentication, calendar CRUD operations,
 * and authorization. Implements DataLoader pattern for
 * efficient batch loading of related entities.
 */
@GraphQLApi
@ApplicationScoped
public class CalendarGraphQL {

    /**
     * Logger for this class.
     */
    private static final Logger LOG =
        Logger.getLogger(CalendarGraphQL.class);

    /**
     * Default page size for pagination.
     */
    private static final int DEFAULT_PAGE_SIZE = 1000;

    /**
     * Maximum limit for user queries.
     */
    private static final int MAX_USER_LIMIT = 50;

    /**
     * JWT token for authenticated user.
     */
    @Inject
    private JsonWebToken jwt;

    /**
     * Authentication service for user management.
     */
    @Inject
    private AuthenticationService authService;

    /**
     * Calendar service for business logic.
     */
    @Inject
    private CalendarService calendarService;

    /**
     * Event service for event operations.
     */
    @Inject
    private EventService eventService;

    // ==================================================================
    // QUERIES
    // ==================================================================

    /**
     * Get the currently authenticated user.
     * Returns null if not authenticated.
     *
     * @return CalendarUser or null
     */
    @Query("me")
    @Description("Get the currently authenticated user. "
        + "Requires valid JWT token in Authorization header. "
        + "Returns null if not authenticated.")
    @PermitAll
    public CalendarUser me() {
        LOG.debug("Query: me()");

        // Check if JWT is present
        if (jwt == null || jwt.getSubject() == null) {
            LOG.debug("No JWT token present, returning null");
            return null;
        }

        Optional<CalendarUser> user = authService.getCurrentUser(jwt);
        if (user.isEmpty()) {
            LOG.warnf("User not found for JWT subject: %s",
                jwt.getSubject());
            return null;
        }

        LOG.infof("Returning current user: %s (ID: %s)",
            user.get().email, user.get().id);
        return user.get();
    }

    /**
     * Get the currently authenticated user (alias for 'me' query).
     * Returns null if not authenticated.
     *
     * @return CalendarUser or null
     */
    @Query("currentUser")
    @Description("Get the currently authenticated user. Alias for "
        + "'me' query. Requires valid JWT token in Authorization "
        + "header. Returns null if not authenticated.")
    @PermitAll
    public CalendarUser currentUser() {
        LOG.debug("Query: currentUser() - delegating to me()");
        return me();
    }

    /**
     * Get calendars for the authenticated user.
     * Requires authentication.
     *
     * @param year Optional year filter
     * @return List of user calendars
     */
    @Query("myCalendars")
    @Description("Get calendars for the authenticated user. "
        + "Requires authentication.")
    @RolesAllowed("USER")
    public List<UserCalendar> myCalendars(
        @Description("Filter by calendar year (optional)")
        final Integer year
    ) {
        LOG.infof("Query: myCalendars(year=%s)", year);

        // Get current user from JWT
        Optional<CalendarUser> currentUser =
            authService.getCurrentUser(jwt);
        if (currentUser.isEmpty()) {
            LOG.error("User not found despite passing "
                + "@RolesAllowed check");
            throw new IllegalStateException(
                "Unauthorized: User not found");
        }

        CalendarUser user = currentUser.get();
        UUID userId = user.id;

        // Query calendars using service with pagination
        List<UserCalendar> calendars = calendarService.listCalendars(
            userId, year, 0, DEFAULT_PAGE_SIZE, user);

        LOG.infof("Found %d calendars for user %s%s",
            calendars.size(), user.email,
            year != null ? " in year " + year : " (all years)");

        return calendars;
    }

    /**
     * Get calendars for a specific user (admin only) or filter by year.
     * If userId is provided, requires ADMIN role.
     * If userId is NOT provided, returns calendars for authenticated user.
     *
     * @param userId User ID to fetch calendars for (admin only)
     * @param year Optional year filter
     * @return List of user calendars
     */
    @Query("calendars")
    @Description("Get calendars for a specific user (admin only) or "
        + "filter by year. If userId is not provided, returns "
        + "calendars for authenticated user.")
    @RolesAllowed("USER")
    public List<UserCalendar> calendars(
        @Name("userId")
        @Description("User ID to fetch calendars for (admin only)")
        final String userId,

        @Name("year")
        @Description("Filter by calendar year (optional)")
        final Integer year
    ) {
        LOG.infof("Query: calendars(userId=%s, year=%s)", userId, year);

        // Get current user from JWT
        Optional<CalendarUser> currentUser =
            authService.getCurrentUser(jwt);
        if (currentUser.isEmpty()) {
            LOG.error("User not found despite passing "
                + "@RolesAllowed check");
            throw new IllegalStateException(
                "Unauthorized: User not found");
        }

        CalendarUser user = currentUser.get();

        // Determine target user ID
        UUID targetUserId;
        if (userId != null && !userId.isEmpty()) {
            // Admin access requested - verify admin role
            boolean isAdmin = jwt.getGroups() != null
                && jwt.getGroups().contains("ADMIN");
            if (!isAdmin) {
                LOG.errorf("Non-admin user %s attempted to access "
                    + "calendars for userId=%s", user.email, userId);
                throw new SecurityException("Unauthorized: ADMIN role "
                    + "required to access other users' calendars");
            }

            // Parse the provided user ID
            try {
                targetUserId = UUID.fromString(userId);
            } catch (IllegalArgumentException e) {
                LOG.errorf("Invalid user ID format: %s", userId);
                throw new IllegalArgumentException(
                    "Invalid user ID format");
            }

            LOG.infof("Admin %s accessing calendars for user %s",
                user.email, userId);
        } else {
            // No userId provided - return current user's calendars
            targetUserId = user.id;
            LOG.infof("Returning calendars for current user %s",
                user.email);
        }

        // Query calendars using service with pagination
        List<UserCalendar> calendars = calendarService.listCalendars(
            targetUserId, year, 0, DEFAULT_PAGE_SIZE, user);

        LOG.infof("Found %d calendars for user %s%s",
            calendars.size(), targetUserId,
            year != null ? " in year " + year : " (all years)");

        return calendars;
    }

    /**
     * Get a single calendar by ID.
     * Returns calendar if user owns it or if it's public.
     *
     * @param id Calendar ID
     * @return UserCalendar or null
     */
    @Query("calendar")
    @Description("Get a single calendar by ID. Returns calendar if "
        + "user owns it or if it's public.")
    @PermitAll
    public UserCalendar calendar(
        @Name("id")
        @Description("Calendar ID")
        @NonNull
        final String id
    ) {
        LOG.infof("Query: calendar(id=%s)", id);

        try {
            UUID calendarId = UUID.fromString(id);

            // Get current user (may be null for anonymous access)
            CalendarUser currentUser = null;
            if (jwt != null && jwt.getSubject() != null) {
                Optional<CalendarUser> userOpt =
                    authService.getCurrentUser(jwt);
                currentUser = userOpt.orElse(null);
            }

            // Use service to get calendar with authorization check
            try {
                UserCalendar calendar = calendarService.getCalendar(
                    calendarId, currentUser);
                LOG.infof("Returning calendar: %s (name=%s, owner=%s)",
                    id, calendar.name, calendar.user != null
                        ? calendar.user.email : "anonymous");
                return calendar;
            } catch (IllegalArgumentException e) {
                // Calendar not found
                LOG.warnf("Calendar not found: %s", id);
                return null;
            } catch (SecurityException e) {
                // Access denied
                LOG.warnf("Access denied to calendar %s: %s",
                    id, e.getMessage());
                return null;
            }

        } catch (IllegalArgumentException e) {
            LOG.errorf("Invalid UUID format for calendar ID: %s", id);
            throw new IllegalArgumentException(
                "Invalid calendar ID format", e);
        }
    }

    /**
     * Get all users (admin only).
     * Requires ADMIN role.
     *
     * @param limit Maximum number of users to return
     * @return List of users
     */
    @Query("allUsers")
    @Description("Get all users (admin only). Requires ADMIN role "
        + "in JWT claims.")
    @RolesAllowed("ADMIN")
    public List<CalendarUser> allUsers(
        @Name("limit")
        @Description("Maximum number of users to return (default: 50)")
        final Integer limit
    ) {
        LOG.infof("Query: allUsers(limit=%s)", limit);

        // Apply limit with default of 50
        int maxResults = (limit != null && limit > 0)
            ? limit : MAX_USER_LIMIT;

        // Query users with limit
        List<CalendarUser> users = CalendarUser
            .<CalendarUser>findAll()
            .page(0, maxResults)
            .list();

        LOG.infof("Returning %d users (limit=%d)",
            users.size(), maxResults);
        return users;
    }

    // ==================================================================
    // MUTATIONS
    // ==================================================================

    /**
     * Create a new calendar based on a template.
     * Requires authentication.
     *
     * @param input Calendar creation data
     * @return Created calendar
     */
    @Mutation("createCalendar")
    @Description("Create a new calendar based on a template. "
        + "Requires authentication. The calendar is created "
        + "in DRAFT status.")
    @RolesAllowed("USER")
    @Transactional
    public UserCalendar createCalendar(
        @Name("input")
        @Description("Calendar creation data")
        @NotNull
        @Valid
        final CalendarInput input
    ) {
        LOG.infof("Mutation: createCalendar(name=%s, year=%d, "
            + "templateId=%s)", input.name, input.year,
            input.templateId);

        // Get current user
        Optional<CalendarUser> currentUser =
            authService.getCurrentUser(jwt);
        if (currentUser.isEmpty()) {
            LOG.error("User not found despite passing "
                + "@RolesAllowed check");
            throw new IllegalStateException(
                "Unauthorized: User not found");
        }

        CalendarUser user = currentUser.get();

        // Parse and validate template ID
        UUID templateId;
        try {
            templateId = UUID.fromString(input.templateId);
        } catch (IllegalArgumentException e) {
            LOG.errorf("Invalid template ID format: %s",
                input.templateId);
            throw new IllegalArgumentException(
                "Invalid template ID format");
        }

        // Create calendar using service
        UserCalendar calendar = calendarService.createCalendar(
            input.name,
            input.year,
            templateId,
            input.configuration,
            input.isPublic,
            user,
            null // sessionId is null for authenticated users
        );

        LOG.infof("Created calendar: %s (ID: %s) for user %s",
            calendar.name, calendar.id, user.email);

        return calendar;
    }

    /**
     * Update an existing calendar's customization.
     * Requires authentication and calendar ownership.
     *
     * @param id Calendar ID
     * @param input Calendar update data
     * @return Updated calendar
     */
    @Mutation("updateCalendar")
    @Description("Update an existing calendar's customization. "
        + "Requires authentication and calendar ownership.")
    @RolesAllowed("USER")
    @Transactional
    public UserCalendar updateCalendar(
        @Name("id")
        @Description("Calendar ID to update")
        @NotNull
        final String id,

        @Name("input")
        @Description("Calendar update data")
        @NotNull
        @Valid
        final CalendarUpdateInput input
    ) {
        LOG.infof("Mutation: updateCalendar(id=%s)", id);

        // Get current user
        Optional<CalendarUser> currentUser =
            authService.getCurrentUser(jwt);
        if (currentUser.isEmpty()) {
            LOG.error("User not found despite passing "
                + "@RolesAllowed check");
            throw new IllegalStateException(
                "Unauthorized: User not found");
        }

        CalendarUser user = currentUser.get();

        // Parse calendar ID
        UUID calendarId;
        try {
            calendarId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            LOG.errorf("Invalid calendar ID format: %s", id);
            throw new IllegalArgumentException(
                "Invalid calendar ID format");
        }

        // Update calendar using service (handles auth and validation)
        UserCalendar calendar = calendarService.updateCalendar(
            calendarId,
            input.name,
            input.configuration,
            input.isPublic,
            user
        );

        LOG.infof("Updated calendar: %s (ID: %s)",
            calendar.name, calendar.id);

        return calendar;
    }

    /**
     * Delete a calendar.
     * Requires authentication and calendar ownership.
     * Cannot delete calendars with paid orders.
     *
     * @param id Calendar ID
     * @return true if deleted successfully
     */
    @Mutation("deleteCalendar")
    @Description("Delete a calendar. Requires authentication and "
        + "calendar ownership. Cannot delete calendars with "
        + "paid orders.")
    @RolesAllowed("USER")
    @Transactional
    public Boolean deleteCalendar(
        @Name("id")
        @Description("Calendar ID to delete")
        @NotNull
        final String id
    ) {
        LOG.infof("Mutation: deleteCalendar(id=%s)", id);

        // Get current user
        Optional<CalendarUser> currentUser =
            authService.getCurrentUser(jwt);
        if (currentUser.isEmpty()) {
            LOG.error("User not found despite passing "
                + "@RolesAllowed check");
            throw new IllegalStateException(
                "Unauthorized: User not found");
        }

        CalendarUser user = currentUser.get();

        // Parse calendar ID
        UUID calendarId;
        try {
            calendarId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            LOG.errorf("Invalid calendar ID format: %s", id);
            throw new IllegalArgumentException(
                "Invalid calendar ID format");
        }

        // Check for paid orders before deletion
        Optional<UserCalendar> calendarOpt = UserCalendar
            .<UserCalendar>findByIdOptional(calendarId);
        if (calendarOpt.isPresent()) {
            UserCalendar calendar = calendarOpt.get();
            // Check for paid orders
            if (calendar.orders != null && !calendar.orders.isEmpty()) {
                boolean hasPaidOrders = calendar.orders.stream()
                    .anyMatch(order ->
                        CalendarOrder.STATUS_PAID.equals(order.status)
                        || CalendarOrder.STATUS_PROCESSING.equals(
                            order.status)
                        || CalendarOrder.STATUS_SHIPPED.equals(
                            order.status)
                        || CalendarOrder.STATUS_DELIVERED.equals(
                            order.status));

                if (hasPaidOrders) {
                    LOG.errorf("Cannot delete calendar %s: "
                        + "has paid orders", id);
                    throw new IllegalStateException(
                        "Cannot delete calendar with paid orders");
                }
            }
        }

        // Delete calendar using service (handles authorization)
        calendarService.deleteCalendar(calendarId, user);

        LOG.infof("Deleted calendar: %s", id);

        return true;
    }

    /**
     * Convert anonymous guest session to authenticated user account.
     * Links all calendars from the guest session to the newly
     * authenticated user. Called after successful OAuth authentication
     * when user had existing session.
     *
     * @param sessionId Guest session ID to convert
     * @return User with linked calendars
     */
    @Mutation("convertGuestSession")
    @Description("Convert anonymous guest session to authenticated "
        + "user account. Links all calendars from the guest session "
        + "to the newly authenticated user.")
    @RolesAllowed("USER")
    @Transactional
    public CalendarUser convertGuestSession(
        @Name("sessionId")
        @Description("Guest session ID to convert")
        @NotNull
        final String sessionId
    ) {
        LOG.infof("Mutation: convertGuestSession(sessionId=%s)",
            sessionId);

        // Get current user
        Optional<CalendarUser> currentUser =
            authService.getCurrentUser(jwt);
        if (currentUser.isEmpty()) {
            LOG.error("User not found despite passing "
                + "@RolesAllowed check");
            throw new IllegalStateException(
                "Unauthorized: User not found");
        }

        CalendarUser user = currentUser.get();

        // Convert session calendars to user calendars using service
        int convertedCount = calendarService.convertSessionToUser(
            sessionId, user);

        LOG.infof("Converted %d calendars from session %s to user %s",
            convertedCount, sessionId, user.email);

        return user;
    }

    // ==================================================================
    // BATCHED FIELD RESOLVERS (DataLoader Pattern)
    // ==================================================================

    /**
     * Batched field resolver for UserCalendar.user relationship.
     * Prevents N+1 queries by batch-loading users for multiple calendars.
     * SmallRye GraphQL automatically batches field resolvers annotated
     * with @Source when the parameter is a List.
     *
     * @param calendars List of calendars to resolve users for
     * @return List of users in the same order as input calendars
     */
    @Name("user")
    @Description("Get the user who owns this calendar")
    public List<CalendarUser> batchLoadUsers(
        @Source final List<UserCalendar> calendars
    ) {
        LOG.debugf("Batch loading users for %d calendars", calendars.size());

        // Extract unique user IDs (handle nulls for guest calendars)
        List<UUID> userIds = calendars.stream()
            .map(c -> c.user != null ? c.user.id : null)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());

        if (userIds.isEmpty()) {
            // All calendars are guest sessions with no user
            return calendars.stream()
                .map(c -> (CalendarUser) null)
                .collect(Collectors.toList());
        }

        // Batch load all users in a single query using IN clause
        List<CalendarUser> users = CalendarUser.list("id IN ?1", userIds);

        // Create lookup map for O(1) access
        Map<UUID, CalendarUser> userMap = users.stream()
            .collect(Collectors.toMap(u -> u.id, u -> u));

        // Return users in same order as input calendars (DataLoader contract)
        List<CalendarUser> result = calendars.stream()
            .map(c -> c.user != null ? userMap.get(c.user.id) : null)
            .collect(Collectors.toList());

        LOG.debugf("Batch loaded %d unique users for %d calendars",
            users.size(), calendars.size());

        return result;
    }

    /**
     * Batched field resolver for UserCalendar.template relationship.
     * Prevents N+1 queries by batch-loading templates for multiple calendars.
     * SmallRye GraphQL automatically batches field resolvers annotated
     * with @Source when the parameter is a List.
     *
     * @param calendars List of calendars to resolve templates for
     * @return List of templates in the same order as input calendars
     */
    @Name("template")
    @Description("Get the template used by this calendar")
    public List<CalendarTemplate> batchLoadTemplates(
        @Source final List<UserCalendar> calendars
    ) {
        LOG.debugf("Batch loading templates for %d calendars",
            calendars.size());

        // Extract unique template IDs
        List<UUID> templateIds = calendars.stream()
            .map(c -> c.template != null ? c.template.id : null)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());

        if (templateIds.isEmpty()) {
            // No templates to load (should not happen in normal operation)
            return calendars.stream()
                .map(c -> (CalendarTemplate) null)
                .collect(Collectors.toList());
        }

        // Batch load all templates in a single query using IN clause
        List<CalendarTemplate> templates =
            CalendarTemplate.list("id IN ?1", templateIds);

        // Create lookup map for O(1) access
        Map<UUID, CalendarTemplate> templateMap = templates.stream()
            .collect(Collectors.toMap(t -> t.id, t -> t));

        // Return templates in same order as input calendars
        // (DataLoader contract)
        List<CalendarTemplate> result = calendars.stream()
            .map(c -> c.template != null
                ? templateMap.get(c.template.id) : null)
            .collect(Collectors.toList());

        LOG.debugf("Batch loaded %d unique templates for %d calendars",
            templates.size(), calendars.size());

        return result;
    }
}
