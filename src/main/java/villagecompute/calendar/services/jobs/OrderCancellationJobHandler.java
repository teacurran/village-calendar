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
 * DelayedJob handler for sending order cancellation emails. Renders HTML email templates using Qute
 * and sends via EmailService.
 */
@ApplicationScoped
@DelayedJobConfig(priority = 5, description = "Order cancellation email sender")
public class OrderCancellationJobHandler implements DelayedJobHandler {

    private static final Logger LOG = Logger.getLogger(OrderCancellationJobHandler.class);

    @Inject EmailService emailService;

    @ConfigProperty(
            name = "email.order.from",
            defaultValue = "Village Compute Calendar <orders@villagecompute.com>")
    String orderFromEmail;

    @ConfigProperty(name = "app.base-url", defaultValue = "https://calendar.villagecompute.com")
    String baseUrl;

    /** Type-safe Qute templates for cancellation emails. */
    @CheckedTemplate(basePath = "email-templates/OrderCancellationJobHandler")
    public static class Templates {
        /**
         * Order cancellation email template.
         *
         * @param order Calendar order
         * @param stylesheet CSS stylesheet
         * @param includeRefundNote Whether to include refund processing note
         * @return Template instance
         */
        public static native TemplateInstance orderCancellation(
                CalendarOrder order, String stylesheet, boolean includeRefundNote);
    }

    @Override
    @WithSpan("OrderCancellationJobHandler.run")
    public void run(String actorId) throws Exception {
        LOG.infof("Processing order cancellation email for order: %s", actorId);

        // Find the order
        CalendarOrder order = CalendarOrder.findById(UUID.fromString(actorId));

        if (order == null) {
            LOG.errorf("Order not found: %s", actorId);
            throw new DelayedJobException(true, "Order not found: " + actorId);
        }

        // Validate order is actually cancelled
        if (!CalendarOrder.STATUS_CANCELLED.equals(order.status)) {
            LOG.warnf("Order %s is not in CANCELLED status (current: %s)", actorId, order.status);
            throw new DelayedJobException(true, "Order is not cancelled: " + actorId);
        }

        // Add tracing attributes
        Span.current().setAttribute("order.id", order.id.toString());
        Span.current().setAttribute("order.user.email", order.user.email);
        Span.current().setAttribute("order.status", order.status);

        try {
            // Load CSS from resources
            String css = loadResourceAsString("css/email.css");

            // Determine if refund note should be included
            // Refunds are needed if order was paid and has a payment intent
            boolean includeRefundNote = order.stripePaymentIntentId != null && order.paidAt != null;

            // Render the cancellation email template
            String subject = "Order Cancelled - Village Compute Calendar";
            String htmlContent =
                    Templates.orderCancellation(order, css, includeRefundNote).render();

            // Send the email
            emailService.sendHtmlEmail(orderFromEmail, order.user.email, subject, htmlContent);

            LOG.infof("Order cancellation email sent successfully to: %s", order.user.email);
            Span.current().addEvent("Order cancellation email sent");

        } catch (IOException e) {
            LOG.error("Failed to load email resources", e);
            throw new DelayedJobException(true, "Failed to load email resources", e);
        } catch (Exception e) {
            LOG.error("Failed to send order cancellation email", e);
            throw new DelayedJobException(false, "Failed to send order cancellation email", e);
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
        try (var inputStream =
                Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
