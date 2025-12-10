package villagecompute.calendar.services;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Arrays;
import java.util.List;

/**
 * Service for sending emails via Quarkus Mailer.
 * Includes domain filtering for non-production environments to prevent
 * accidental email sends to real customers during testing.
 */
@ApplicationScoped
public class EmailService {

    private static final Logger LOG = Logger.getLogger(EmailService.class);

    @Inject
    Mailer mailer;

    @Inject
    OpenTelemetry openTelemetry;

    @ConfigProperty(name = "quarkus.mailer.from", defaultValue = "no-reply@villagecompute.com")
    String defaultFromEmail;

    @ConfigProperty(name = "quarkus.profile", defaultValue = "dev")
    String profile;

    @ConfigProperty(name = "mail.enabled", defaultValue = "true")
    boolean emailEnabled;

    @ConfigProperty(name = "email.safe-test-domains", defaultValue = "villagecompute.com,grilledcheese.com,approachingpi.com")
    String safeTestDomains;

    /**
     * Get list of safe test domains.
     */
    private List<String> getSafeTestDomainsList() {
        return Arrays.asList(safeTestDomains.split(","));
    }

    /**
     * Check if running in production environment.
     *
     * @return true if production
     */
    private boolean isProduction() {
        return "prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile);
    }

    /**
     * Check if email domain is safe to send to in non-production environments.
     * In production, all domains are allowed.
     *
     * @param email Email address to check
     * @return true if safe to send
     */
    private boolean isEmailDomainSafe(String email) {
        // Always allow in production
        if (isProduction()) {
            return true;
        }

        // Extract domain from email
        if (email == null || !email.contains("@")) {
            return false;
        }

        String domain = email.substring(email.lastIndexOf("@") + 1).toLowerCase();
        List<String> safeDomains = getSafeTestDomainsList();

        return safeDomains.stream()
            .map(String::toLowerCase)
            .map(String::trim)
            .anyMatch(safeDomain -> domain.equals(safeDomain));
    }

    /**
     * Log blocked email with OpenTelemetry event for debugging.
     *
     * @param to Recipient email
     * @param subject Email subject
     * @param reason Reason for blocking
     */
    private void logBlockedEmail(String to, String subject, String reason) {
        Tracer tracer = openTelemetry.getTracer(EmailService.class.getName());
        Span span = tracer.spanBuilder("email.blocked").startSpan();

        try (Scope scope = span.makeCurrent()) {
            span.setAttribute("email.recipient", to);
            span.setAttribute("email.subject", subject);
            span.setAttribute("email.block_reason", reason);
            span.setAttribute("environment", profile);
            span.addEvent("Email blocked in non-production environment");

            LOG.warnf("EMAIL BLOCKED [%s]: Would have sent '%s' to %s - Reason: %s",
                profile.toUpperCase(), subject, to, reason);
        } finally {
            span.end();
        }
    }

    /**
     * Add environment prefix to email subject if not in production.
     *
     * @param subject Original subject
     * @return Prefixed subject
     */
    private String addEnvironmentPrefix(String subject) {
        if (!isProduction()) {
            return String.format("[%s] %s", profile.toUpperCase(), subject);
        }
        return subject;
    }

    /**
     * Send a simple text email.
     *
     * @param to Recipient email
     * @param subject Email subject
     * @param body Email body
     */
    public void sendEmail(String to, String subject, String body) {
        sendEmail(defaultFromEmail, to, subject, body);
    }

    /**
     * Send a text email with custom from address.
     *
     * @param from Sender email
     * @param to Recipient email
     * @param subject Email subject
     * @param body Email body
     */
    public void sendEmail(String from, String to, String subject, String body) {
        if (!emailEnabled) {
            LOG.info("Email disabled - would have sent: " + subject + " to " + to);
            return;
        }

        // Check domain safety in non-production
        if (!isEmailDomainSafe(to)) {
            logBlockedEmail(to, subject, "Domain not in safe test domains list");
            return;
        }

        try {
            String prefixedSubject = addEnvironmentPrefix(subject);
            Mail mail = Mail.withText(to, prefixedSubject, body)
                .setFrom(from);

            mailer.send(mail);
            LOG.infof("Email sent successfully from %s to: %s", from, to);
        } catch (Exception e) {
            LOG.error("Failed to send email to: " + to, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }

    /**
     * Send an HTML email.
     *
     * @param to Recipient email
     * @param subject Email subject
     * @param htmlBody HTML email body
     */
    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        sendHtmlEmail(defaultFromEmail, to, subject, htmlBody);
    }

    /**
     * Send an HTML email with custom from address.
     *
     * @param from Sender email
     * @param to Recipient email
     * @param subject Email subject
     * @param htmlBody HTML email body
     */
    public void sendHtmlEmail(String from, String to, String subject, String htmlBody) {
        if (!emailEnabled) {
            LOG.info("Email disabled - would have sent: " + subject + " to " + to);
            return;
        }

        // Check domain safety in non-production
        if (!isEmailDomainSafe(to)) {
            logBlockedEmail(to, subject, "Domain not in safe test domains list");
            return;
        }

        try {
            String prefixedSubject = addEnvironmentPrefix(subject);
            Mail mail = Mail.withHtml(to, prefixedSubject, htmlBody)
                .setFrom(from);

            mailer.send(mail);
            LOG.infof("HTML email sent successfully from %s to: %s", from, to);
        } catch (Exception e) {
            LOG.error("Failed to send HTML email to: " + to, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }
}
