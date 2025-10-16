package villagecompute.calendar.api.rest;

import io.quarkus.oidc.IdToken;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeIn;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;
import villagecompute.calendar.api.dto.AuthResponse;
import villagecompute.calendar.api.dto.UserInfo;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.services.AuthenticationService;

import java.net.URI;
import java.util.UUID;

/**
 * REST resource for handling OAuth2 authentication flows.
 * Provides endpoints for initiating OAuth login and handling callbacks.
 */
@Path("/auth")
@Tag(name = "Authentication", description = "OAuth2 authentication endpoints for Google and Facebook login")
@SecurityScheme(
    securitySchemeName = "BearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "JWT token obtained from OAuth2 callback endpoints"
)
public class AuthResource {

    private static final Logger LOG = Logger.getLogger(AuthResource.class);

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    @IdToken
    JsonWebToken idToken;

    @Inject
    AuthenticationService authenticationService;

    /**
     * Initiates OAuth login with Google.
     * This endpoint is protected by OIDC, so accessing it will trigger the OAuth flow.
     * After successful authentication, the user is redirected to the callback endpoint.
     */
    @GET
    @Path("/login/google")
    @Authenticated
    @Operation(
        summary = "Initiate Google OAuth login",
        description = "Redirects user to Google OAuth consent page. This endpoint triggers the OAuth2 " +
            "authorization code flow. After user grants permissions on Google's consent page, " +
            "they will be redirected back to /auth/google/callback with an authorization code."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "303",
            description = "Redirect to Google OAuth consent page or callback endpoint"
        ),
        @APIResponse(
            responseCode = "401",
            description = "Authentication required or OAuth flow failed"
        )
    })
    public Response loginWithGoogle() {
        LOG.info("Initiating Google OAuth login");
        // Quarkus OIDC will handle the redirect to Google
        // After authentication, user will be redirected to /auth/google/callback
        return Response.seeOther(URI.create("/auth/google/callback")).build();
    }

    /**
     * Handles the OAuth callback from Google after successful authentication.
     * Creates or updates the user record and issues a JWT token for API access.
     *
     * @return AuthResponse with JWT token and user information
     */
    @GET
    @Path("/google/callback")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Handle Google OAuth callback",
        description = "OAuth2 callback endpoint invoked by Google after user grants permissions. " +
            "Exchanges the authorization code for an access token, creates/updates the user record, " +
            "and issues a JWT token for subsequent API requests. This endpoint is NOT directly callable " +
            "by clients - it's part of the OAuth2 authorization code flow."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Authentication successful, JWT token issued",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = AuthResponse.class),
                examples = @ExampleObject(value = """
                    {
                      "token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
                      "user": {
                        "id": "550e8400-e29b-41d4-a716-446655440000",
                        "email": "user@example.com",
                        "displayName": "John Doe",
                        "profileImageUrl": "https://example.com/avatar.jpg"
                      }
                    }
                    """)
            )
        ),
        @APIResponse(
            responseCode = "500",
            description = "Authentication failed",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"error\": \"Authentication failed: ...\"}")
            )
        )
    })
    public Response handleGoogleCallback() {
        LOG.info("Handling Google OAuth callback");

        try {
            // Handle the OAuth callback and create/update user
            CalendarUser user = authenticationService.handleOAuthCallback("google", securityIdentity);

            // Issue JWT token for the user
            String jwtToken = authenticationService.issueJWT(user);

            // Create response with token and user info
            AuthResponse response = AuthResponse.of(
                jwtToken,
                UserInfo.fromEntity(user)
            );

            LOG.infof("Successfully authenticated user: %s", user.email);
            return Response.ok(response).build();

        } catch (Exception e) {
            LOG.errorf(e, "Error handling Google OAuth callback");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Authentication failed: " + e.getMessage()))
                .build();
        }
    }

    /**
     * Initiates OAuth login with Facebook.
     * This endpoint is protected by OIDC, so accessing it will trigger the OAuth flow.
     * After successful authentication, the user is redirected to the callback endpoint.
     */
    @GET
    @Path("/login/facebook")
    @Authenticated
    @Operation(
        summary = "Initiate Facebook OAuth login",
        description = "Redirects user to Facebook OAuth consent page. This endpoint triggers the OAuth2 " +
            "authorization code flow. After user grants permissions on Facebook's consent page, " +
            "they will be redirected back to /auth/facebook/callback with an authorization code."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "303",
            description = "Redirect to Facebook OAuth consent page or callback endpoint"
        ),
        @APIResponse(
            responseCode = "401",
            description = "Authentication required or OAuth flow failed"
        )
    })
    public Response loginWithFacebook() {
        LOG.info("Initiating Facebook OAuth login");
        // Quarkus OIDC will handle the redirect to Facebook
        // After authentication, user will be redirected to /auth/facebook/callback
        return Response.seeOther(URI.create("/auth/facebook/callback")).build();
    }

    /**
     * Handles the OAuth callback from Facebook after successful authentication.
     * Creates or updates the user record and issues a JWT token for API access.
     *
     * @return AuthResponse with JWT token and user information
     */
    @GET
    @Path("/facebook/callback")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Handle Facebook OAuth callback",
        description = "OAuth2 callback endpoint invoked by Facebook after user grants permissions. " +
            "Exchanges the authorization code for an access token, creates/updates the user record, " +
            "and issues a JWT token for subsequent API requests. This endpoint is NOT directly callable " +
            "by clients - it's part of the OAuth2 authorization code flow."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Authentication successful, JWT token issued",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = AuthResponse.class),
                examples = @ExampleObject(value = """
                    {
                      "token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
                      "user": {
                        "id": "550e8400-e29b-41d4-a716-446655440000",
                        "email": "user@example.com",
                        "displayName": "Jane Smith",
                        "profileImageUrl": "https://example.com/avatar.jpg"
                      }
                    }
                    """)
            )
        ),
        @APIResponse(
            responseCode = "500",
            description = "Authentication failed",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"error\": \"Authentication failed: ...\"}")
            )
        )
    })
    public Response handleFacebookCallback() {
        LOG.info("Handling Facebook OAuth callback");

        try {
            // Handle the OAuth callback and create/update user
            CalendarUser user = authenticationService.handleOAuthCallback("facebook", securityIdentity);

            // Issue JWT token for the user
            String jwtToken = authenticationService.issueJWT(user);

            // Create response with token and user info
            AuthResponse response = AuthResponse.of(
                jwtToken,
                UserInfo.fromEntity(user)
            );

            LOG.infof("Successfully authenticated user: %s", user.email);
            return Response.ok(response).build();

        } catch (Exception e) {
            LOG.errorf(e, "Error handling Facebook OAuth callback");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Authentication failed: " + e.getMessage()))
                .build();
        }
    }

    /**
     * Get current user information from JWT token.
     * Used to verify JWT validity and get user details.
     */
    @GET
    @Path("/me")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Get current authenticated user",
        description = "Returns user information based on the JWT token provided in the Authorization header. " +
            "Used to verify token validity and retrieve current user details."
    )
    @SecurityRequirement(name = "BearerAuth")
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "User information retrieved successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = UserInfo.class),
                examples = @ExampleObject(value = """
                    {
                      "id": "550e8400-e29b-41d4-a716-446655440000",
                      "email": "user@example.com",
                      "displayName": "John Doe",
                      "profileImageUrl": "https://example.com/avatar.jpg"
                    }
                    """)
            )
        ),
        @APIResponse(
            responseCode = "401",
            description = "Invalid or missing JWT token",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"error\": \"No valid JWT token found\"}")
            )
        ),
        @APIResponse(
            responseCode = "404",
            description = "User not found",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"error\": \"User not found\"}")
            )
        ),
        @APIResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"error\": \"Failed to fetch user: ...\"}")
            )
        )
    })
    public Response getCurrentUser() {
        LOG.info("Fetching current user information");

        try {
            // Use the JWT from the Authorization header to look up the user
            JsonWebToken jwt = securityIdentity.getPrincipal() instanceof JsonWebToken
                ? (JsonWebToken) securityIdentity.getPrincipal()
                : null;

            if (jwt == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse("No valid JWT token found"))
                    .build();
            }

            String userIdStr = jwt.getSubject();
            UUID userId = UUID.fromString(userIdStr);

            return CalendarUser.<CalendarUser>findByIdOptional(userId)
                .map(user -> Response.ok(UserInfo.fromEntity(user)).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("User not found"))
                    .build());

        } catch (Exception e) {
            LOG.errorf(e, "Error fetching current user");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Failed to fetch user: " + e.getMessage()))
                .build();
        }
    }

    /**
     * Simple error response for authentication failures.
     */
    public static class ErrorResponse {
        public String error;

        public ErrorResponse(String error) {
            this.error = error;
        }
    }
}
