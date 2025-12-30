package villagecompute.calendar.config;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkus.security.identity.SecurityIdentity;

/**
 * Security configuration for the calendar service. Handles JWT-based authentication for GraphQL API
 * and REST endpoints.
 */
@ApplicationScoped
public class SecurityConfig {

    private static final Logger LOG = Logger.getLogger(SecurityConfig.class);

    @Inject SecurityIdentity securityIdentity;

    /**
     * Check if the current request is authenticated.
     *
     * @return true if the user is authenticated (has valid JWT or OIDC session)
     */
    public boolean isAuthenticated() {
        return securityIdentity != null && !securityIdentity.isAnonymous();
    }

    /**
     * Check if the current user has a specific role.
     *
     * @param role Role name to check (e.g., "USER", "ADMIN")
     * @return true if the user has the role
     */
    public boolean hasRole(String role) {
        return securityIdentity != null && securityIdentity.hasRole(role);
    }

    /**
     * Get the current user's ID from the security context.
     *
     * @return User ID (from JWT subject claim), or null if not authenticated
     */
    public UUID getCurrentUserId() {
        if (!isAuthenticated()) {
            return null;
        }

        try {
            String subject = securityIdentity.getPrincipal().getName();
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            LOG.errorf(
                    "Invalid user ID in security principal: %s",
                    securityIdentity.getPrincipal().getName());
            return null;
        }
    }

    /**
     * Get the current user's email from the security context.
     *
     * @return Email address, or null if not authenticated
     */
    public String getCurrentUserEmail() {
        if (!isAuthenticated()) {
            return null;
        }

        return securityIdentity.getAttribute("email");
    }
}
