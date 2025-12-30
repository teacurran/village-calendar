package villagecompute.calendar.api;

import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.data.models.CalendarOrderItem;
import villagecompute.calendar.services.OrderService;
import villagecompute.calendar.services.PDFRenderingService;

/** REST endpoint for secure order-related operations */
@Path("/orders")
public class OrderResource {

    private static final Logger LOG = Logger.getLogger(OrderResource.class);

    @Inject OrderService orderService;

    @Inject PDFRenderingService pdfRenderingService;

    @Inject ObjectMapper objectMapper;

    /**
     * Download PDF for a specific order item. This endpoint retrieves the saved SVG content from
     * the order and generates PDF, ensuring consistency with frontend previews and eliminating
     * security risks of accepting arbitrary SVG content.
     *
     * @param orderNumber The order number (public identifier)
     * @param itemId The order item ID
     * @return PDF file as response
     */
    @GET
    @Path("/{orderNumber}/items/{itemId}/pdf")
    @Produces("application/pdf")
    public Response downloadOrderItemPDF(
            @PathParam("orderNumber") String orderNumber,
            @PathParam("itemId") UUID itemId,
            @Context SecurityContext securityContext) {

        try {
            LOG.infof("PDF download request for order %s, item %s", orderNumber, itemId);

            // Find the order by order number
            CalendarOrder order = orderService.findByOrderNumber(orderNumber);
            if (order == null) {
                LOG.warnf("Order not found: %s", orderNumber);
                return Response.status(Response.Status.NOT_FOUND)
                        .type(MediaType.TEXT_PLAIN)
                        .entity("Order not found")
                        .build();
            }

            // Log available items for debugging
            LOG.infof("Order %s has %d items", orderNumber, order.items.size());
            for (CalendarOrderItem item : order.items) {
                LOG.infof("  Item ID: %s, ProductType: %s", item.id, item.productType);
            }

            // Find the specific order item
            CalendarOrderItem orderItem = null;
            for (CalendarOrderItem item : order.items) {
                if (item.id.equals(itemId)) {
                    orderItem = item;
                    LOG.infof("Found matching item: %s", item.id);
                    break;
                }
            }

            if (orderItem == null) {
                LOG.warnf(
                        "Order item not found: %s in order %s with %d items",
                        itemId, orderNumber, order.items.size());

                // List available item IDs for debugging
                StringBuilder availableIds = new StringBuilder("Available item IDs: ");
                for (CalendarOrderItem item : order.items) {
                    availableIds.append(item.id).append(", ");
                }
                LOG.warnf(availableIds.toString());

                return Response.status(Response.Status.NOT_FOUND)
                        .type(MediaType.TEXT_PLAIN)
                        .entity("Order item not found. Item ID: " + itemId)
                        .build();
            }

            // Security check - verify user can access this order
            // For now, we allow access to any order by order number
            // TODO: Add proper user authentication and authorization
            // if (!canUserAccessOrder(securityContext, order)) {
            //     return Response.status(Response.Status.FORBIDDEN).build();
            // }

            // Extract SVG content from the order item configuration
            String svgContent = extractSvgFromOrderItem(orderItem);
            if (svgContent == null || svgContent.isEmpty()) {
                LOG.warnf("No SVG content found for order item %s", itemId);
                return Response.status(Response.Status.BAD_REQUEST)
                        .type(MediaType.TEXT_PLAIN)
                        .entity("No calendar content found for this item")
                        .build();
            }

            // Get the calendar year for PDF metadata
            int year = orderItem.getYear();

            // Generate PDF from the stored SVG content
            byte[] pdf = pdfRenderingService.renderSVGToPDF(svgContent, year);

            if (pdf == null || pdf.length == 0) {
                LOG.errorf("PDF generation failed for order item %s", itemId);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .type(MediaType.TEXT_PLAIN)
                        .entity("Failed to generate PDF")
                        .build();
            }

            LOG.infof(
                    "PDF generated successfully for order %s, item %s: %d bytes",
                    orderNumber, itemId, pdf.length);

            // Create filename: calendar-ORDER123-customer-2025.pdf
            String customerName =
                    sanitizeForFilename(
                            order.customerEmail != null
                                    ? order.customerEmail.split("@")[0]
                                    : "customer");
            String filename =
                    String.format("calendar-%s-%s-%d.pdf", orderNumber, customerName, year);

            return Response.ok(pdf)
                    .type("application/pdf")
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .header("X-Order-Number", orderNumber)
                    .header("X-Item-Id", itemId.toString())
                    .build();

        } catch (Exception e) {
            LOG.errorf(e, "Error generating PDF for order %s, item %s", orderNumber, itemId);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("PDF generation failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Extract SVG content from order item configuration. Checks multiple possible locations for the
     * SVG content.
     */
    private String extractSvgFromOrderItem(CalendarOrderItem orderItem) {
        LOG.infof("Extracting SVG from order item %s", orderItem.id);

        // First, try to get SVG from the linked UserCalendar
        if (orderItem.calendar != null) {
            LOG.infof("Item has linked calendar: %s", orderItem.calendar.id);
            if (orderItem.calendar.generatedSvg != null
                    && !orderItem.calendar.generatedSvg.isEmpty()) {
                LOG.infof(
                        "Found SVG in UserCalendar.generatedSvg for item %s (%d chars)",
                        orderItem.id, orderItem.calendar.generatedSvg.length());
                return orderItem.calendar.generatedSvg;
            } else {
                LOG.warnf(
                        "Calendar %s exists but generatedSvg is null/empty", orderItem.calendar.id);
            }
        } else {
            LOG.infof("Item %s has no linked calendar", orderItem.id);
        }

        // Fallback: try to extract from order item configuration JSON
        if (orderItem.configuration != null) {
            LOG.infof("Checking configuration JSON for SVG content");
            try {
                // Log configuration structure for debugging
                LOG.infof("Configuration keys: %s", orderItem.configuration.fieldNames());

                // Check for generatedSvg field
                if (orderItem.configuration.has("generatedSvg")) {
                    String svgContent = orderItem.configuration.get("generatedSvg").asText();
                    if (svgContent != null && !svgContent.isEmpty()) {
                        LOG.infof(
                                "Found SVG in configuration.generatedSvg for item %s (%d chars)",
                                orderItem.id, svgContent.length());
                        return svgContent;
                    }
                }

                LOG.warnf("Configuration exists but no generatedSvg field found");
            } catch (Exception e) {
                LOG.errorf(
                        e,
                        "Error extracting SVG from configuration for item %s: %s",
                        orderItem.id,
                        e.getMessage());
            }
        } else {
            LOG.warnf("Item %s has no configuration", orderItem.id);
        }

        LOG.errorf("No SVG content found for order item %s", orderItem.id);
        return null;
    }

    /** Sanitize string for use in filename */
    private String sanitizeForFilename(String input) {
        if (input == null) return "customer";
        return input.replaceAll("[^a-zA-Z0-9.-]", "").toLowerCase();
    }

    // TODO: Implement proper authorization
    // private boolean canUserAccessOrder(SecurityContext securityContext, CalendarOrder order) {
    //     // Check if current user owns this order or has admin privileges
    //     return true; // For now, allow access to all orders
    // }
}
