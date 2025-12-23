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
import villagecompute.calendar.data.models.CalendarOrderItem;
import villagecompute.calendar.services.EmailService;
import villagecompute.calendar.services.PDFRenderingService;
import villagecompute.calendar.services.exceptions.DelayedJobException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DelayedJob handler for sending order confirmation emails.
 * Sends confirmation to both the customer and the admin.
 * Renders HTML email templates using Qute and sends via EmailService.
 */
@ApplicationScoped
@DelayedJobConfig(priority = 10, description = "Order confirmation email sender")
public class OrderEmailJobHandler implements DelayedJobHandler {

    private static final Logger LOG = Logger.getLogger(OrderEmailJobHandler.class);

    @Inject
    EmailService emailService;

    @Inject
    PDFRenderingService pdfRenderingService;

    // Preview image width in pixels (height calculated automatically from aspect ratio)
    private static final int PREVIEW_IMAGE_WIDTH = 600;

    @ConfigProperty(name = "email.order.from", defaultValue = "Village Compute Calendar <orders@villagecompute.com>")
    String orderFromEmail;

    @ConfigProperty(name = "email.admin.to", defaultValue = "terrence.curran@villagecompute.com")
    String adminEmail;

    @ConfigProperty(name = "app.base-url", defaultValue = "https://calendar.villagecompute.com")
    String baseUrl;

    /**
     * Type-safe Qute templates for order emails.
     */
    @CheckedTemplate(basePath = "email-templates/OrderEmailJobHandler")
    public static class Templates {
        /**
         * Order confirmation email template for customers.
         *
         * @param order Calendar order
         * @param stylesheet CSS stylesheet
         * @param previewImages List of base64 PNG data URIs for calendar previews (parallel to order.items)
         * @param baseUrl Base URL for order status links
         * @return Template instance
         */
        public static native TemplateInstance orderConfirmation(
            CalendarOrder order,
            String stylesheet,
            List<String> previewImages,
            String baseUrl
        );

        /**
         * Admin notification email template for new orders.
         *
         * @param order Calendar order
         * @param stylesheet CSS stylesheet
         * @param baseUrl Base URL for admin links
         * @param previewImages List of base64 PNG data URIs for calendar previews (parallel to order.items)
         * @return Template instance
         */
        public static native TemplateInstance adminOrderNotification(
            CalendarOrder order,
            String stylesheet,
            String baseUrl,
            List<String> previewImages
        );
    }

    @Override
    @WithSpan("OrderEmailJobHandler.run")
    public void run(String actorId) throws Exception {
        LOG.infof("Processing order confirmation email for order: %s", actorId);

        // Find the order with eagerly fetched relationships including item calendars
        CalendarOrder order = CalendarOrder.find(
            "SELECT DISTINCT o FROM CalendarOrder o " +
            "LEFT JOIN FETCH o.user " +
            "LEFT JOIN FETCH o.items i " +
            "LEFT JOIN FETCH i.calendar " +
            "LEFT JOIN FETCH o.calendar " +
            "WHERE o.id = ?1", UUID.fromString(actorId))
            .firstResult();

        if (order == null) {
            LOG.errorf("Order not found: %s", actorId);
            throw new DelayedJobException(false, "Order not found: " + actorId);
        }

        // Get customer email (prefer customerEmail field, fall back to user.email)
        String customerEmail = order.customerEmail != null ? order.customerEmail :
            (order.user != null ? order.user.email : null);

        if (customerEmail == null) {
            LOG.errorf("No customer email found for order: %s", actorId);
            throw new DelayedJobException(false, "No customer email found for order: " + actorId);
        }

        // Add tracing attributes
        Span.current().setAttribute("order.id", order.id.toString());
        Span.current().setAttribute("order.number", order.orderNumber);
        Span.current().setAttribute("order.customer.email", customerEmail);
        Span.current().setAttribute("order.status", order.status);

        try {
            // Load CSS from resources
            String css = loadResourceAsString("css/email.css");

            // Generate preview images for each order item's calendar
            List<String> previewImages = generatePreviewImages(order);
            LOG.infof("Generated %d preview images for order %s", previewImages.size(), order.orderNumber);

            // Send customer confirmation email
            String customerSubject = "Order Confirmation - Village Compute Calendar #" + order.orderNumber;
            String customerHtmlContent = Templates.orderConfirmation(order, css, previewImages, baseUrl).render();

            emailService.sendHtmlEmail(orderFromEmail, customerEmail, customerSubject, customerHtmlContent);
            LOG.infof("Order confirmation email sent successfully to customer: %s", customerEmail);
            Span.current().addEvent("Customer order confirmation email sent");

            // Send admin notification email
            String adminSubject = "New Order Received - #" + order.orderNumber;
            String adminHtmlContent = Templates.adminOrderNotification(order, css, baseUrl, previewImages).render();

            emailService.sendHtmlEmail(orderFromEmail, adminEmail, adminSubject, adminHtmlContent);
            LOG.infof("Order notification email sent to admin: %s", adminEmail);
            Span.current().addEvent("Admin order notification email sent");

        } catch (IOException e) {
            LOG.error("Failed to load email resources", e);
            throw new DelayedJobException(false, "Failed to load email resources", e);
        } catch (Exception e) {
            LOG.error("Failed to send order confirmation email", e);
            throw new DelayedJobException(true, "Failed to send order confirmation email", e);
        }
    }

    /**
     * Generate preview images for each order item's calendar.
     * Returns a list of base64 PNG data URIs, parallel to order.items.
     * Items without a calendar or SVG will have null in the corresponding position.
     *
     * @param order The order containing items
     * @return List of base64 PNG data URIs (or null for items without calendars)
     */
    private List<String> generatePreviewImages(CalendarOrder order) {
        List<String> previews = new ArrayList<>();

        if (order.items == null || order.items.isEmpty()) {
            return previews;
        }

        for (CalendarOrderItem item : order.items) {
            String previewDataUri = null;

            if (item.calendar != null && item.calendar.generatedSvg != null
                    && !item.calendar.generatedSvg.isEmpty()) {
                try {
                    previewDataUri = pdfRenderingService.renderSVGToPNGDataUri(
                        item.calendar.generatedSvg,
                        PREVIEW_IMAGE_WIDTH
                    );
                    LOG.debugf("Generated preview image for item %s (calendar %s)",
                        item.id, item.calendar.id);
                } catch (Exception e) {
                    LOG.warnf(e, "Failed to generate preview image for item %s, skipping", item.id);
                    // Continue without preview for this item
                }
            } else {
                LOG.debugf("No calendar SVG available for item %s", item.id);
            }

            previews.add(previewDataUri);
        }

        return previews;
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
