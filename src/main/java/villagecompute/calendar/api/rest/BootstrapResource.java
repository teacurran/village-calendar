package villagecompute.calendar.api.rest;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;
import villagecompute.calendar.api.dto.AuthResponse;
import villagecompute.calendar.api.dto.UserInfo;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.services.AuthenticationService;

import java.util.Optional;

/**
 * REST resource for bootstrapping the application with an initial admin user.
 * This endpoint is only accessible when no admin users exist in the system.
 */
@Path("/bootstrap")
@Tag(name = "Bootstrap", description = "Bootstrap endpoints for initial admin setup")
public class BootstrapResource {

    private static final Logger LOG = Logger.getLogger(BootstrapResource.class);

    @Inject
    AuthenticationService authenticationService;

    /**
     * Check if the system needs to be bootstrapped.
     * Returns the bootstrap status and whether admin creation is allowed.
     */
    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Check bootstrap status",
        description = "Check if the system needs to be bootstrapped with an initial admin user. " +
            "Returns needsBootstrap=true when no admin users exist in the database."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Bootstrap status retrieved successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = BootstrapStatus.class)
            )
        )
    })
    public Response getBootstrapStatus() {
        LOG.debug("Checking bootstrap status");

        boolean hasAdmins = CalendarUser.hasAdminUsers();
        long totalUsers = CalendarUser.count();

        BootstrapStatus status = new BootstrapStatus();
        status.needsBootstrap = !hasAdmins;
        status.totalUsers = totalUsers;
        status.hasAdmins = hasAdmins;

        LOG.infof("Bootstrap status: needsBootstrap=%b, totalUsers=%d, hasAdmins=%b",
            status.needsBootstrap, status.totalUsers, status.hasAdmins);

        return Response.ok(status).build();
    }

    /**
     * Create the first admin user by promoting an existing OAuth user.
     * This endpoint is only accessible when no admin users exist.
     *
     * @param request Bootstrap request with user email to promote
     * @return AuthResponse with JWT token and user information
     */
    @POST
    @Path("/create-admin")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    @Operation(
        summary = "Create first admin user",
        description = "Promotes an existing OAuth user to admin status. This endpoint is only " +
            "accessible when no admin users exist in the system. The user must have already " +
            "authenticated via OAuth (Google or Facebook) before being promoted to admin."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Admin user created successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = AuthResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "400",
            description = "Invalid request or user not found",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @APIResponse(
            responseCode = "403",
            description = "Bootstrap not allowed - admin users already exist",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    public Response createAdmin(BootstrapRequest request) {
        LOG.infof("Attempting to create admin user for email: %s", request.email);

        // Check if admin users already exist
        if (CalendarUser.hasAdminUsers()) {
            LOG.warn("Admin creation rejected - admin users already exist");
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ErrorResponse("Admin users already exist. Bootstrap is not allowed."))
                .build();
        }

        // Validate request
        if (request.email == null || request.email.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("Email is required"))
                .build();
        }

        // Find user by email
        Optional<CalendarUser> userOpt = CalendarUser.findByEmail(request.email);
        if (userOpt.isEmpty()) {
            LOG.errorf("User not found with email: %s", request.email);
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("User not found. Please authenticate via OAuth first."))
                .build();
        }

        CalendarUser user = userOpt.get();

        // Check if user is already an admin (shouldn't happen, but just in case)
        if (user.isAdmin != null && user.isAdmin) {
            LOG.warnf("User %s is already an admin", user.email);
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("User is already an admin"))
                .build();
        }

        // Promote user to admin
        user.isAdmin = true;
        user.persist();

        LOG.infof("Successfully promoted user to admin: %s (ID: %s)", user.email, user.id);

        // Issue new JWT with ADMIN role
        String jwtToken = authenticationService.issueJWT(user);

        // Create response
        AuthResponse response = AuthResponse.of(
            jwtToken,
            UserInfo.fromEntity(user)
        );

        return Response.ok(response).build();
    }

    /**
     * List all users for bootstrap selection.
     * Only accessible when no admin users exist.
     */
    @GET
    @Path("/users")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "List users for bootstrap",
        description = "Returns a list of all users who have authenticated via OAuth. " +
            "This endpoint helps in selecting which user to promote to admin. " +
            "Only accessible when no admin users exist."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "User list retrieved successfully"
        ),
        @APIResponse(
            responseCode = "403",
            description = "Bootstrap not allowed - admin users already exist"
        )
    })
    public Response getUsers() {
        LOG.debug("Fetching users for bootstrap");

        // Check if admin users already exist
        if (CalendarUser.hasAdminUsers()) {
            LOG.warn("User list request rejected - admin users already exist");
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ErrorResponse("Admin users already exist. Bootstrap is not allowed."))
                .build();
        }

        // Get all users
        var users = CalendarUser.<CalendarUser>listAll()
            .stream()
            .map(UserInfo::fromEntity)
            .toList();

        LOG.infof("Returning %d users for bootstrap selection", users.size());
        return Response.ok(users).build();
    }

    // ============================================================================
    // REQUEST/RESPONSE TYPES
    // ============================================================================

    public static class BootstrapStatus {
        public boolean needsBootstrap;
        public long totalUsers;
        public boolean hasAdmins;
    }

    public static class BootstrapRequest {
        public String email;
    }

    public static class ErrorResponse {
        public String error;

        public ErrorResponse(String error) {
            this.error = error;
        }
    }
}
