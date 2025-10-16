package villagecompute.calendar.api.graphql;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.graphql.*;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;
import villagecompute.calendar.api.graphql.inputs.CalendarInput;
import villagecompute.calendar.api.graphql.inputs.CalendarUpdateInput;
import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.data.repositories.CalendarTemplateRepository;
import villagecompute.calendar.services.AuthenticationService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * GraphQL resolver for calendar queries and mutations.
 * Handles user authentication, calendar CRUD operations, and authorization.
 */
@GraphQLApi
@ApplicationScoped
public class CalendarGraphQL {

    private static final Logger LOG = Logger.getLogger(CalendarGraphQL.class);

    @Inject
    JsonWebToken jwt;

    @Inject
    AuthenticationService authService;

    @Inject
    CalendarTemplateRepository templateRepository;

    // ============================================================================
    // QUERIES
    // ============================================================================

    /**
     * Get the currently authenticated user.
     * Returns null if not authenticated.
     *
     * @return CalendarUser or null
     */
    @Query("me")
    @Description("Get the currently authenticated user. Requires valid JWT token in Authorization header. Returns null if not authenticated.")
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
            LOG.warnf("User not found for JWT subject: %s", jwt.getSubject());
            return null;
        }

        LOG.infof("Returning current user: %s (ID: %s)", user.get().email, user.get().id);
        return user.get();
    }

    /**
     * Get calendars for the authenticated user.
     * Requires authentication.
     *
     * @param year Optional year filter
     * @return List of user calendars
     */
    @Query("myCalendars")
    @Description("Get calendars for the authenticated user. Requires authentication.")
    @RolesAllowed("USER")
    public List<UserCalendar> myCalendars(
        @Description("Filter by calendar year (optional)")
        Integer year
    ) {
        LOG.infof("Query: myCalendars(year=%s)", year);

        // Get current user from JWT
        Optional<CalendarUser> currentUser = authService.getCurrentUser(jwt);
        if (currentUser.isEmpty()) {
            LOG.error("User not found despite passing @RolesAllowed check");
            throw new IllegalStateException("Unauthorized: User not found");
        }

        CalendarUser user = currentUser.get();
        UUID userId = user.id;

        // Query calendars with optional year filter
        List<UserCalendar> calendars;
        if (year != null) {
            calendars = UserCalendar.findByUserAndYear(userId, year);
            LOG.infof("Found %d calendars for user %s in year %d", calendars.size(), user.email, year);
        } else {
            calendars = UserCalendar.findByUser(userId).list();
            LOG.infof("Found %d calendars for user %s (all years)", calendars.size(), user.email);
        }

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
    @Description("Get a single calendar by ID. Returns calendar if user owns it or if it's public.")
    @PermitAll
    public UserCalendar calendar(
        @Name("id")
        @Description("Calendar ID")
        @NonNull
        String id
    ) {
        LOG.infof("Query: calendar(id=%s)", id);

        try {
            UUID calendarId = UUID.fromString(id);
            Optional<UserCalendar> calendarOpt = UserCalendar.<UserCalendar>findByIdOptional(calendarId);

            if (calendarOpt.isEmpty()) {
                LOG.warnf("Calendar not found: %s", id);
                return null;
            }

            UserCalendar calendar = calendarOpt.get();

            // Check access: either public or user owns it
            boolean isPublic = calendar.isPublic;
            boolean isOwner = false;

            if (jwt != null && jwt.getSubject() != null) {
                Optional<CalendarUser> currentUser = authService.getCurrentUser(jwt);
                if (currentUser.isPresent() && calendar.user != null) {
                    isOwner = calendar.user.id.equals(currentUser.get().id);
                }
            }

            if (!isPublic && !isOwner) {
                LOG.warnf("Access denied to private calendar %s", id);
                return null;
            }

            LOG.infof("Returning calendar: %s (name=%s, owner=%s)",
                id, calendar.name, calendar.user != null ? calendar.user.email : "anonymous");
            return calendar;

        } catch (IllegalArgumentException e) {
            LOG.errorf("Invalid UUID format for calendar ID: %s", id);
            throw new IllegalArgumentException("Invalid calendar ID format", e);
        }
    }

    // ============================================================================
    // MUTATIONS
    // ============================================================================

    /**
     * Create a new calendar based on a template.
     * Requires authentication.
     *
     * @param input Calendar creation data
     * @return Created calendar
     */
    @Mutation("createCalendar")
    @Description("Create a new calendar based on a template. Requires authentication. The calendar is created in DRAFT status.")
    @RolesAllowed("USER")
    @Transactional
    public UserCalendar createCalendar(
        @Name("input")
        @Description("Calendar creation data")
        @NotNull
        @Valid
        CalendarInput input
    ) {
        LOG.infof("Mutation: createCalendar(name=%s, year=%d, templateId=%s)",
            input.name, input.year, input.templateId);

        // Get current user
        Optional<CalendarUser> currentUser = authService.getCurrentUser(jwt);
        if (currentUser.isEmpty()) {
            LOG.error("User not found despite passing @RolesAllowed check");
            throw new IllegalStateException("Unauthorized: User not found");
        }

        CalendarUser user = currentUser.get();

        // Validate template exists and is active
        UUID templateId;
        try {
            templateId = UUID.fromString(input.templateId);
        } catch (IllegalArgumentException e) {
            LOG.errorf("Invalid template ID format: %s", input.templateId);
            throw new IllegalArgumentException("Invalid template ID format");
        }

        CalendarTemplate template = CalendarTemplate.<CalendarTemplate>findByIdOptional(templateId).orElse(null);
        if (template == null) {
            LOG.errorf("Template not found: %s", input.templateId);
            throw new IllegalArgumentException("Template not found");
        }

        if (!template.isActive) {
            LOG.errorf("Template is inactive: %s", input.templateId);
            throw new IllegalStateException("Template is not active");
        }

        // Create the calendar
        UserCalendar calendar = new UserCalendar();
        calendar.user = user;
        calendar.name = input.name;
        calendar.year = input.year;
        calendar.template = template;
        calendar.configuration = input.configuration;
        calendar.isPublic = input.isPublic != null ? input.isPublic : true; // Default to public

        // Persist the calendar
        calendar.persist();

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
    @Description("Update an existing calendar's customization. Requires authentication and calendar ownership.")
    @RolesAllowed("USER")
    @Transactional
    public UserCalendar updateCalendar(
        @Name("id")
        @Description("Calendar ID to update")
        @NotNull
        String id,

        @Name("input")
        @Description("Calendar update data")
        @NotNull
        @Valid
        CalendarUpdateInput input
    ) {
        LOG.infof("Mutation: updateCalendar(id=%s)", id);

        // Get current user
        Optional<CalendarUser> currentUser = authService.getCurrentUser(jwt);
        if (currentUser.isEmpty()) {
            LOG.error("User not found despite passing @RolesAllowed check");
            throw new IllegalStateException("Unauthorized: User not found");
        }

        CalendarUser user = currentUser.get();

        // Find the calendar
        UUID calendarId;
        try {
            calendarId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            LOG.errorf("Invalid calendar ID format: %s", id);
            throw new IllegalArgumentException("Invalid calendar ID format");
        }

        Optional<UserCalendar> calendarOpt = UserCalendar.<UserCalendar>findByIdOptional(calendarId);
        if (calendarOpt.isEmpty()) {
            LOG.errorf("Calendar not found: %s", id);
            throw new IllegalArgumentException("Calendar not found");
        }

        UserCalendar calendar = calendarOpt.get();

        // Verify ownership
        if (calendar.user == null || !calendar.user.id.equals(user.id)) {
            LOG.errorf("User %s attempted to update calendar %s owned by another user",
                user.email, id);
            throw new SecurityException("Unauthorized: You don't own this calendar");
        }

        // Apply updates
        if (input.name != null) {
            calendar.name = input.name;
        }
        if (input.configuration != null) {
            calendar.configuration = input.configuration;
        }
        if (input.isPublic != null) {
            calendar.isPublic = input.isPublic;
        }

        // Persist changes
        calendar.persist();

        LOG.infof("Updated calendar: %s (ID: %s)", calendar.name, calendar.id);

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
    @Description("Delete a calendar. Requires authentication and calendar ownership. Cannot delete calendars with paid orders.")
    @RolesAllowed("USER")
    @Transactional
    public Boolean deleteCalendar(
        @Name("id")
        @Description("Calendar ID to delete")
        @NotNull
        String id
    ) {
        LOG.infof("Mutation: deleteCalendar(id=%s)", id);

        // Get current user
        Optional<CalendarUser> currentUser = authService.getCurrentUser(jwt);
        if (currentUser.isEmpty()) {
            LOG.error("User not found despite passing @RolesAllowed check");
            throw new IllegalStateException("Unauthorized: User not found");
        }

        CalendarUser user = currentUser.get();

        // Find the calendar
        UUID calendarId;
        try {
            calendarId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            LOG.errorf("Invalid calendar ID format: %s", id);
            throw new IllegalArgumentException("Invalid calendar ID format");
        }

        Optional<UserCalendar> calendarOpt = UserCalendar.<UserCalendar>findByIdOptional(calendarId);
        if (calendarOpt.isEmpty()) {
            LOG.errorf("Calendar not found: %s", id);
            throw new IllegalArgumentException("Calendar not found");
        }

        UserCalendar calendar = calendarOpt.get();

        // Verify ownership
        if (calendar.user == null || !calendar.user.id.equals(user.id)) {
            LOG.errorf("User %s attempted to delete calendar %s owned by another user",
                user.email, id);
            throw new SecurityException("Unauthorized: You don't own this calendar");
        }

        // Check for paid orders
        if (calendar.orders != null && !calendar.orders.isEmpty()) {
            boolean hasPaidOrders = calendar.orders.stream()
                .anyMatch(order -> CalendarOrder.STATUS_PAID.equals(order.status)
                    || CalendarOrder.STATUS_PROCESSING.equals(order.status)
                    || CalendarOrder.STATUS_SHIPPED.equals(order.status)
                    || CalendarOrder.STATUS_DELIVERED.equals(order.status));

            if (hasPaidOrders) {
                LOG.errorf("Cannot delete calendar %s: has paid orders", id);
                throw new IllegalStateException("Cannot delete calendar with paid orders");
            }
        }

        // Delete the calendar
        calendar.delete();

        LOG.infof("Deleted calendar: %s (ID: %s)", calendar.name, calendar.id);

        return true;
    }
}
