package villagecompute.calendar.services.jobs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.services.EmailService;
import villagecompute.calendar.services.exceptions.DelayedJobException;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

/**
 * DelayedJob handler for sending shipping notification emails. Renders HTML email templates using Qute and sends via
 * EmailService.
 */
@ApplicationScoped
@DelayedJobConfig(priority = 10, description = "Shipping notification email sender")
public class ShippingNotificationJobHandler implements DelayedJobHandler {

    private static final Logger LOG = Logger.getLogger(ShippingNotificationJobHandler.class);

    @Inject
    EmailService emailService;

    @ConfigProperty(name = "email.order.from", defaultValue = "Village Compute Calendar <orders@villagecompute.com>")
    String orderFromEmail;

    @ConfigProperty(name = "app.base-url", defaultValue = "https://calendar.villagecompute.com")
    String baseUrl;

    /** Type-safe Qute templates for shipping emails. */
    @CheckedTemplate(basePath = "email-templates/ShippingNotificationJobHandler")
    public static class Templates {
        /**
         * Shipping notification email template.
         *
         * @param order
         *            Calendar order
         * @param stylesheet
         *            CSS stylesheet
         * @return Template instance
         */
        public static native TemplateInstance shippingNotification(CalendarOrder order, String stylesheet);
    }

    @Override
    @WithSpan("ShippingNotificationJobHandler.run")
    public void run(String actorId) throws Exception {
        LOG.infof("Processing shipping notification email for order: %s", actorId);

        // Find the order
        CalendarOrder order = CalendarOrder.findById(UUID.fromString(actorId));

        if (order == null) {
            LOG.errorf("Order not found: %s", actorId);
            throw new DelayedJobException(true, "Order not found: " + actorId);
        }

        // Validate order has tracking number
        if (order.trackingNumber == null || order.trackingNumber.isBlank()) {
            LOG.warnf("Order %s marked as shipped but has no tracking number", actorId);
            // This is a permanent failure - order should have tracking number when shipped
            throw new DelayedJobException(true, "Order has no tracking number: " + actorId);
        }

        // Add tracing attributes
        Span.current().setAttribute("order.id", order.id.toString());
        Span.current().setAttribute("order.user.email", order.user.email);
        Span.current().setAttribute("order.status", order.status);
        Span.current().setAttribute("order.trackingNumber", order.trackingNumber);

        try {
            // Load CSS from resources
            String css = loadResourceAsString("css/email.css");

            // Render the shipping notification email template
            String subject = "Your Order Has Shipped! - Village Compute Calendar";
            String htmlContent = Templates.shippingNotification(order, css).render();

            // Send the email
            emailService.sendHtmlEmail(orderFromEmail, order.user.email, subject, htmlContent);

            LOG.infof("Shipping notification email sent successfully to: %s", order.user.email);
            Span.current().addEvent("Shipping notification email sent");

        } catch (IOException e) {
            LOG.error("Failed to load email resources", e);
            throw new DelayedJobException(true, "Failed to load email resources", e);
        } catch (Exception e) {
            LOG.error("Failed to send shipping notification email", e);
            throw new DelayedJobException(false, "Failed to send shipping notification email", e);
        }
    }

    /**
     * Load a resource file as a string.
     *
     * @param resourcePath
     *            Resource path
     * @return File contents
     * @throws IOException
     *             if resource cannot be loaded
     */
    private String loadResourceAsString(String resourcePath) throws IOException {
        try (var inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
