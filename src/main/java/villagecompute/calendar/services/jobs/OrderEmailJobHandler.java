package villagecompute.calendar.services.jobs;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.services.EmailService;
import villagecompute.calendar.services.exceptions.DelayedJobException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * DelayedJob handler for sending order confirmation emails.
 * Renders HTML email templates using Qute and sends via EmailService.
 */
@ApplicationScoped
public class OrderEmailJobHandler implements DelayedJobHandler {

    private static final Logger LOG = Logger.getLogger(OrderEmailJobHandler.class);

    @Inject
    EmailService emailService;

    @ConfigProperty(name = "email.order.from", defaultValue = "Village Compute Calendar <orders@villagecompute.com>")
    String orderFromEmail;

    @ConfigProperty(name = "app.base-url", defaultValue = "https://calendar.villagecompute.com")
    String baseUrl;

    /**
     * Type-safe Qute templates for order emails.
     */
    @CheckedTemplate
    public static class Templates {
        /**
         * Order confirmation email template.
         *
         * @param order Calendar order
         * @param stylesheet CSS stylesheet
         * @return Template instance
         */
        public static native TemplateInstance orderConfirmation(
            CalendarOrder order,
            String stylesheet
        );
    }

    @Override
    @WithSpan("OrderEmailJobHandler.run")
    public void run(String actorId) throws Exception {
        LOG.infof("Processing order confirmation email for order: %s", actorId);

        // Find the order
        CalendarOrder order = CalendarOrder.findById(UUID.fromString(actorId));

        if (order == null) {
            LOG.errorf("Order not found: %s", actorId);
            throw new DelayedJobException(true, "Order not found: " + actorId);
        }

        // Add tracing attributes
        Span.current().setAttribute("order.id", order.id.toString());
        Span.current().setAttribute("order.user.email", order.user.email);
        Span.current().setAttribute("order.status", order.status);

        try {
            // Load CSS from resources
            String css = loadResourceAsString("css/email.css");

            // Render the confirmation email template
            String subject = "Order Confirmation - Village Compute Calendar";
            String htmlContent = Templates.orderConfirmation(order, css).render();

            // Send the email
            emailService.sendHtmlEmail(orderFromEmail, order.user.email, subject, htmlContent);

            LOG.infof("Order confirmation email sent successfully to: %s", order.user.email);
            Span.current().addEvent("Order confirmation email sent");

        } catch (IOException e) {
            LOG.error("Failed to load email resources", e);
            throw new DelayedJobException(true, "Failed to load email resources", e);
        } catch (Exception e) {
            LOG.error("Failed to send order confirmation email", e);
            throw new DelayedJobException(false, "Failed to send order confirmation email", e);
        }
    }

    /**
     * Load a resource file as a string.
     *
     * @param resourcePath Resource path
     * @return File contents
     * @throws IOException if resource cannot be loaded
     */
    private String loadResourceAsString(String resourcePath) throws IOException {
        try (var inputStream = Thread.currentThread().getContextClassLoader()
            .getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
