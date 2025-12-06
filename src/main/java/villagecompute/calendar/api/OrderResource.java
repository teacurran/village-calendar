package villagecompute.calendar.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.Context;
import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.data.models.CalendarOrderItem;
import villagecompute.calendar.services.PDFRenderingService;
import villagecompute.calendar.services.OrderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;

/**
 * REST endpoint for secure order-related operations
 */
@Path("/orders")
public class OrderResource {

    private static final Logger LOG = Logger.getLogger(OrderResource.class);

    @Inject
    OrderService orderService;

    @Inject
    PDFRenderingService pdfRenderingService;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Download PDF for a specific order item.
     * This endpoint retrieves the saved SVG content from the order and generates PDF,
     * ensuring consistency with frontend previews and eliminating security risks
     * of accepting arbitrary SVG content.
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
            @PathParam("itemId") Long itemId,
            @Context SecurityContext securityContext) {

        try {
            LOG.infof("PDF download request for order %s, item %d", orderNumber, itemId);

            // Find the order by order number
            CalendarOrder order = orderService.findByOrderNumber(orderNumber);
            if (order == null) {
                LOG.warnf("Order not found: %s", orderNumber);
                return Response.status(Response.Status.NOT_FOUND)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Order not found")
                    .build();
            }

            // Find the specific order item
            CalendarOrderItem orderItem = null;
            for (CalendarOrderItem item : order.items) {
                if (item.id.equals(itemId)) {
                    orderItem = item;
                    break;
                }
            }

            if (orderItem == null) {
                LOG.warnf("Order item not found: %d in order %s", itemId, orderNumber);
                return Response.status(Response.Status.NOT_FOUND)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Order item not found")
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
                LOG.warnf("No SVG content found for order item %d", itemId);
                return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("No calendar content found for this item")
                    .build();
            }

            // Get the calendar year for PDF metadata
            int year = orderItem.calendarYear != null ? orderItem.calendarYear : 
                       java.time.LocalDate.now().getYear();

            // Generate PDF from the stored SVG content
            byte[] pdf = pdfRenderingService.renderSVGToPDF(svgContent, year);

            if (pdf == null || pdf.length == 0) {
                LOG.errorf("PDF generation failed for order item %d", itemId);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Failed to generate PDF")
                    .build();
            }

            LOG.infof("PDF generated successfully for order %s, item %d: %d bytes", 
                      orderNumber, itemId, pdf.length);

            // Create filename: calendar-ORDER123-customer-2025.pdf
            String customerName = sanitizeForFilename(order.customerEmail != null ? 
                                  order.customerEmail.split("@")[0] : "customer");
            String filename = String.format("calendar-%s-%s-%d.pdf", 
                                           orderNumber, customerName, year);

            return Response.ok(pdf)
                .type("application/pdf")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .header("X-Order-Number", orderNumber)
                .header("X-Item-Id", itemId.toString())
                .build();

        } catch (Exception e) {
            LOG.errorf(e, "Error generating PDF for order %s, item %d", orderNumber, itemId);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.TEXT_PLAIN)
                .entity("PDF generation failed: " + e.getMessage())
                .build();
        }
    }

    /**
     * Extract SVG content from order item configuration.
     * Checks multiple possible locations for the SVG content.
     */
    private String extractSvgFromOrderItem(CalendarOrderItem orderItem) {
        // First, try to get SVG from the linked UserCalendar
        if (orderItem.calendar != null && orderItem.calendar.generatedSvg != null) {
            LOG.debugf("Found SVG in UserCalendar.generatedSvg for item %d", orderItem.id);
            return orderItem.calendar.generatedSvg;
        }

        // Fallback: try to extract from order item configuration JSON
        if (orderItem.configuration != null) {
            try {
                // Check for svgContent field
                if (orderItem.configuration.has("svgContent")) {
                    String svgContent = orderItem.configuration.get("svgContent").asText();
                    if (svgContent != null && !svgContent.isEmpty()) {
                        LOG.debugf("Found SVG in configuration.svgContent for item %d", orderItem.id);
                        return svgContent;
                    }
                }

                // Check for generatedSvg field
                if (orderItem.configuration.has("generatedSvg")) {
                    String svgContent = orderItem.configuration.get("generatedSvg").asText();
                    if (svgContent != null && !svgContent.isEmpty()) {
                        LOG.debugf("Found SVG in configuration.generatedSvg for item %d", orderItem.id);
                        return svgContent;
                    }
                }
            } catch (Exception e) {
                LOG.warnf("Error extracting SVG from configuration for item %d: %s", 
                          orderItem.id, e.getMessage());
            }
        }

        LOG.warnf("No SVG content found for order item %d", orderItem.id);
        return null;
    }

    /**
     * Sanitize string for use in filename
     */
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