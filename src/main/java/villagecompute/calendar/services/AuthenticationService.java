package villagecompute.calendar.services;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import villagecompute.calendar.data.models.CalendarUser;

import io.quarkus.oidc.UserInfo;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.build.JwtClaimsBuilder;

/**
 * Service for handling OAuth2 authentication and JWT token generation. Manages user creation, login tracking, and JWT
 * issuance for API access.
 */
@ApplicationScoped
public class AuthenticationService {

    private static final Logger LOG = Logger.getLogger(AuthenticationService.class);
    private static final Duration JWT_LIFESPAN = Duration.ofDays(1); // 24 hours

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    CalendarService calendarService;

    /**
     * Handle OAuth callback after successful authentication with an OAuth provider. Creates a new user if this is their
     * first login, or updates the last login timestamp for existing users. If a sessionId is provided, converts guest
     * session calendars to the authenticated user.
     *
     * @param provider
     *            OAuth provider name (e.g., "google", "facebook")
     * @param identity
     *            The authenticated security identity from OIDC
     * @param sessionId
     *            Optional guest session ID to convert to user account
     * @return The CalendarUser entity (created or updated)
     */
    @Transactional
    public CalendarUser handleOAuthCallback(String provider, SecurityIdentity identity, String sessionId) {
        LOG.infof("Handling OAuth callback for provider: %s, sessionId: %s", provider, sessionId);

        // Extract user information from the OIDC UserInfo
        UserInfo userInfo = identity.getAttribute("userinfo");

        String oauthSubject = identity.getPrincipal().getName(); // This is the 'sub' claim
        String email = null;
        String displayName = null;
        String profileImageUrl = null;

        if (userInfo != null) {
            email = userInfo.getString("email");
            displayName = userInfo.getString("name");
            profileImageUrl = userInfo.getString("picture");
            LOG.infof("Extracted from UserInfo - email: %s, name: %s", email, displayName);
        } else {
            // Fallback to SecurityIdentity attributes
            email = identity.getAttribute("email");
            displayName = identity.getAttribute("name");
            profileImageUrl = identity.getAttribute("picture");
            LOG.warnf("UserInfo not available, using SecurityIdentity attributes");
        }

        if (email == null || email.isBlank()) {
            LOG.errorf("Email not found in UserInfo or SecurityIdentity. UserInfo available: %s", userInfo != null);
            throw new IllegalArgumentException("Email is required from OAuth provider");
        }

        LOG.infof("OAuth user info - subject: %s, email: %s, name: %s", oauthSubject, email, displayName);

        // Look up existing user by OAuth provider and subject
        Optional<CalendarUser> existingUser = CalendarUser.findByOAuthSubject(provider.toUpperCase(), oauthSubject);

        CalendarUser user;
        if (existingUser.isPresent()) {
            // Update existing user
            user = existingUser.get();
            LOG.infof("Found existing user: %s (ID: %s)", user.email, user.id);

            // Update user information in case it changed at the OAuth provider
            user.email = email;
            user.displayName = displayName;
            user.profileImageUrl = profileImageUrl;
            user.updateLastLogin();

            LOG.infof("Updated existing user's last login: %s", user.email);
        } else {
            // Create new user
            user = new CalendarUser();
            user.oauthProvider = provider.toUpperCase();
            user.oauthSubject = oauthSubject;
            user.email = email;
            user.displayName = displayName;
            user.profileImageUrl = profileImageUrl;
            user.updateLastLogin();
            user.persist();

            LOG.infof("Created new user: %s (ID: %s)", user.email, user.id);
        }

        // Convert guest session calendars to user account if sessionId provided
        if (sessionId != null && !sessionId.isBlank()) {
            try {
                int convertedCount = calendarService.convertSessionToUser(sessionId, user);
                LOG.infof("Converted %d calendars from session %s to user %s", convertedCount, sessionId, user.email);
            } catch (Exception e) {
                // Log error but don't fail authentication
                LOG.errorf(e, "Error converting session %s to user %s. Session calendars will remain" + " unconverted.",
                        sessionId, user.email);
            }
        }

        return user;
    }

    /**
     * Issue a JWT token for a calendar user. The JWT contains user claims (sub, email, roles) and is valid for 24
     * hours.
     *
     * @param user
     *            The calendar user to issue a token for
     * @return JWT token string
     */
    public String issueJWT(CalendarUser user) {
        LOG.infof("Issuing JWT for user: %s (ID: %s)", user.email, user.id);

        // Determine user roles
        Set<String> roles = new HashSet<>();
        roles.add("USER");

        // Add ADMIN role if user is an admin
        if (user.isAdmin != null && user.isAdmin) {
            roles.add("ADMIN");
        }

        // Build JWT with required claims
        JwtClaimsBuilder claimsBuilder = Jwt.claims().subject(user.id.toString()) // User ID as subject
                .issuer("village-calendar").claim("email", user.email)
                .claim("name", user.displayName != null ? user.displayName : user.email).groups(roles) // Roles for
                                                                                                       // authorization
                .expiresIn(JWT_LIFESPAN.getSeconds());

        String token = claimsBuilder.sign();

        LOG.infof("Successfully issued JWT for user: %s", user.email);
        return token;
    }

    /**
     * Get the current authenticated user from the JWT token. This method is used by GraphQL resolvers to identify the
     * current user.
     *
     * @param jwt
     *            The JWT token from the Authorization header
     * @return Optional containing the CalendarUser if found
     */
    public Optional<CalendarUser> getCurrentUser(JsonWebToken jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            return Optional.empty();
        }

        try {
            String userIdStr = jwt.getSubject();
            UUID userId = UUID.fromString(userIdStr);
            return CalendarUser.findByIdOptional(userId);
        } catch (IllegalArgumentException e) {
            LOG.errorf("Invalid user ID in JWT subject: %s", jwt.getSubject());
            return Optional.empty();
        }
    }

    /**
     * Get the current authenticated user, throwing if not found. Use this in @RolesAllowed endpoints where a valid user
     * is guaranteed by the security layer.
     *
     * @param jwt
     *            The JWT token from the Authorization header
     * @return The CalendarUser
     * @throws SecurityException
     *             if user not found (should not happen in @RolesAllowed endpoints)
     */
    public CalendarUser requireCurrentUser(JsonWebToken jwt) {
        return getCurrentUser(jwt).orElseThrow(() -> {
            LOG.error("User not found despite passing @RolesAllowed check");
            return new SecurityException("Unauthorized: User not found");
        });
    }

    /**
     * Validate that the current request has a valid JWT token. This is mainly used for checking authentication status.
     *
     * @return true if the user is authenticated with a valid JWT
     */
    public boolean isAuthenticated() {
        return securityIdentity != null && !securityIdentity.isAnonymous();
    }
}
