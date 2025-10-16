package villagecompute.calendar.services;

import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.build.JwtClaimsBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;
import villagecompute.calendar.data.models.CalendarUser;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service for handling OAuth2 authentication and JWT token generation.
 * Manages user creation, login tracking, and JWT issuance for API access.
 */
@ApplicationScoped
public class AuthenticationService {

    private static final Logger LOG = Logger.getLogger(AuthenticationService.class);
    private static final Duration JWT_LIFESPAN = Duration.ofDays(1); // 24 hours

    @Inject
    SecurityIdentity securityIdentity;

    /**
     * Handle OAuth callback after successful authentication with an OAuth provider.
     * Creates a new user if this is their first login, or updates the last login timestamp
     * for existing users.
     *
     * @param provider OAuth provider name (e.g., "google", "facebook")
     * @param identity The authenticated security identity from OIDC
     * @return The CalendarUser entity (created or updated)
     */
    @Transactional
    public CalendarUser handleOAuthCallback(String provider, SecurityIdentity identity) {
        LOG.infof("Handling OAuth callback for provider: %s", provider);

        // Extract user information from the OIDC identity
        String oauthSubject = identity.getPrincipal().getName(); // This is the 'sub' claim
        String email = identity.getAttribute("email");
        String displayName = identity.getAttribute("name");
        String profileImageUrl = identity.getAttribute("picture");

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required from OAuth provider");
        }

        LOG.infof("OAuth user info - subject: %s, email: %s, name: %s", oauthSubject, email, displayName);

        // Look up existing user by OAuth provider and subject
        Optional<CalendarUser> existingUser = CalendarUser.findByOAuthSubject(
            provider.toUpperCase(),
            oauthSubject
        );

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

        return user;
    }

    /**
     * Issue a JWT token for a calendar user.
     * The JWT contains user claims (sub, email, roles) and is valid for 24 hours.
     *
     * @param user The calendar user to issue a token for
     * @return JWT token string
     */
    public String issueJWT(CalendarUser user) {
        LOG.infof("Issuing JWT for user: %s (ID: %s)", user.email, user.id);

        // Determine user roles (for now, all authenticated users get "USER" role)
        // In the future, this could check user.isAdmin or similar fields
        Set<String> roles = new HashSet<>();
        roles.add("USER");

        // Build JWT with required claims
        JwtClaimsBuilder claimsBuilder = Jwt.claims()
            .subject(user.id.toString())  // User ID as subject
            .issuer("village-calendar")
            .claim("email", user.email)
            .claim("name", user.displayName != null ? user.displayName : user.email)
            .groups(roles)  // Roles for authorization
            .expiresIn(JWT_LIFESPAN.getSeconds());

        String token = claimsBuilder.sign();

        LOG.infof("Successfully issued JWT for user: %s", user.email);
        return token;
    }

    /**
     * Get the current authenticated user from the JWT token.
     * This method is used by GraphQL resolvers to identify the current user.
     *
     * @param jwt The JWT token from the Authorization header
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
     * Validate that the current request has a valid JWT token.
     * This is mainly used for checking authentication status.
     *
     * @return true if the user is authenticated with a valid JWT
     */
    public boolean isAuthenticated() {
        return securityIdentity != null && !securityIdentity.isAnonymous();
    }
}
