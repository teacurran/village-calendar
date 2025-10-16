package villagecompute.calendar.services;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Service for sending emails via Quarkus Mailer.
 * Handles both text and HTML email sending.
 */
@ApplicationScoped
public class EmailService {

    private static final Logger LOG = Logger.getLogger(EmailService.class);

    @Inject
    Mailer mailer;

    @ConfigProperty(name = "quarkus.mailer.from", defaultValue = "no-reply@villagecompute.com")
    String defaultFromEmail;

    @ConfigProperty(name = "quarkus.profile", defaultValue = "dev")
    String profile;

    /**
     * Check if running in production environment.
     *
     * @return true if production
     */
    private boolean isProduction() {
        return "prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile);
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
