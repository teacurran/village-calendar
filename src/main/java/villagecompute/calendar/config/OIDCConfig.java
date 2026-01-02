package villagecompute.calendar.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.runtime.StartupEvent;

/**
 * Configuration for OIDC (OpenID Connect) OAuth2 providers. Logs configuration status at startup for debugging
 * purposes.
 */
@ApplicationScoped
public class OIDCConfig {

    private static final Logger LOG = Logger.getLogger(OIDCConfig.class);
    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String STATUS_NOT_CONFIGURED = "NOT CONFIGURED";
    private static final String STATUS_CONFIGURED = "configured";
    private static final String PLACEHOLDER_CLIENT_ID = "placeholder";

    @ConfigProperty(name = "quarkus.oidc.google.enabled")
    boolean googleEnabled;

    @ConfigProperty(name = "quarkus.oidc.facebook.enabled")
    boolean facebookEnabled;

    @ConfigProperty(name = "quarkus.oidc.apple.enabled")
    boolean appleEnabled;

    @ConfigProperty(name = "quarkus.oidc.google.client-id")
    String googleClientId;

    @ConfigProperty(name = "quarkus.oidc.facebook.client-id")
    String facebookClientId;

    @ConfigProperty(name = "quarkus.oidc.apple.client-id")
    String appleClientId;

    void onStart(@Observes StartupEvent event) {
        LOG.info("=== OIDC Configuration ===");
        LOG.infof("Google OAuth: %s (Client ID: %s)", googleEnabled ? STATUS_ENABLED : STATUS_DISABLED,
                googleClientId.equals(PLACEHOLDER_CLIENT_ID) ? STATUS_NOT_CONFIGURED : STATUS_CONFIGURED);
        LOG.infof("Facebook OAuth: %s (Client ID: %s)", facebookEnabled ? STATUS_ENABLED : STATUS_DISABLED,
                facebookClientId.equals(PLACEHOLDER_CLIENT_ID) ? STATUS_NOT_CONFIGURED : STATUS_CONFIGURED);
        LOG.infof("Apple OAuth: %s (Client ID: %s)", appleEnabled ? STATUS_ENABLED : STATUS_DISABLED,
                appleClientId.equals(PLACEHOLDER_CLIENT_ID) ? STATUS_NOT_CONFIGURED : STATUS_CONFIGURED);
        LOG.info("==========================");
    }
}
