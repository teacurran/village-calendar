package villagecompute.calendar.api;

import static villagecompute.calendar.util.MimeTypes.HEADER_CONTENT_DISPOSITION;

import java.util.Optional;
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

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.data.models.CalendarOrderItem;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.ItemAsset;
import villagecompute.calendar.services.AuthenticationService;
import villagecompute.calendar.services.OrderService;
import villagecompute.calendar.services.PDFRenderingService;

/** REST endpoint for secure order-related operations */
@Path("/orders")
public class OrderResource {

    private static final Logger LOG = Logger.getLogger(OrderResource.class);

    @Inject
    OrderService orderService;

    @Inject
    PDFRenderingService pdfRenderingService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    JsonWebToken jwt;

    @Inject
    AuthenticationService authService;

    /**
     * Download PDF for a specific order item. This endpoint retrieves the saved SVG content from the order and
     * generates PDF, ensuring consistency with frontend previews and eliminating security risks of accepting arbitrary
     * SVG content.
     *
     * @param orderNumber
     *            The order number (public identifier)
     * @param itemId
     *            The order item ID
     * @return PDF file as response
     */
    @GET
    @Path("/{orderNumber}/items/{itemId}/pdf")
    @Produces("application/pdf")
    public Response downloadOrderItemPDF(@PathParam("orderNumber") String orderNumber, @PathParam("itemId") UUID itemId,
            @Context SecurityContext securityContext) {

        try {
            LOG.infof("PDF download request for order %s, item %s", orderNumber, itemId);

            // Find the order by order number
            CalendarOrder order = orderService.findByOrderNumber(orderNumber);
            if (order == null) {
                LOG.warnf("Order not found: %s", orderNumber);
                return Response.status(Response.Status.NOT_FOUND).type(MediaType.TEXT_PLAIN).entity("Order not found")
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
                LOG.warnf("Order item not found: %s in order %s with %d items", itemId, orderNumber,
                        order.items.size());

                // List available item IDs for debugging
                StringBuilder availableIds = new StringBuilder("Available item IDs: ");
                for (CalendarOrderItem item : order.items) {
                    availableIds.append(item.id).append(", ");
                }
                LOG.warnf(availableIds.toString());

                return Response.status(Response.Status.NOT_FOUND).type(MediaType.TEXT_PLAIN)
                        .entity("Order item not found. Item ID: " + itemId).build();
            }

            // Security check - verify user can access this order
            if (!canUserAccessOrder(order)) {
                LOG.warnf("Access denied to order %s", orderNumber);
                return Response.status(Response.Status.FORBIDDEN).type(MediaType.TEXT_PLAIN)
                        .entity("Access denied to this order").build();
            }

            // Extract SVG content from the order item configuration
            String svgContent = extractSvgFromOrderItem(orderItem);
            if (svgContent == null || svgContent.isEmpty()) {
                LOG.warnf("No SVG content found for order item %s", itemId);
                return Response.status(Response.Status.BAD_REQUEST).type(MediaType.TEXT_PLAIN)
                        .entity("No calendar content found for this item").build();
            }

            // Get the calendar year for PDF metadata
            int year = orderItem.getYear();

            // Generate PDF from the stored SVG content
            byte[] pdf = pdfRenderingService.renderSVGToPDF(svgContent, year);

            if (pdf == null || pdf.length == 0) {
                LOG.errorf("PDF generation failed for order item %s", itemId);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).type(MediaType.TEXT_PLAIN)
                        .entity("Failed to generate PDF").build();
            }

            LOG.infof("PDF generated successfully for order %s, item %s: %d bytes", orderNumber, itemId, pdf.length);

            // Create filename: calendar-ORDER123-customer-2025.pdf
            String customerName = sanitizeForFilename(
                    order.customerEmail != null ? order.customerEmail.split("@")[0] : "customer");
            String filename = String.format("calendar-%s-%s-%d.pdf", orderNumber, customerName, year);

            return Response.ok(pdf).type("application/pdf")
                    .header(HEADER_CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header("X-Order-Number", orderNumber).header("X-Item-Id", itemId.toString()).build();

        } catch (Exception e) {
            LOG.errorf(e, "Error generating PDF for order %s, item %s", orderNumber, itemId);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).type(MediaType.TEXT_PLAIN)
                    .entity("PDF generation failed: " + e.getMessage()).build();
        }
    }

    /**
     * Extract SVG content from order item assets.
     *
     * @param orderItem
     *            The order item
     * @return SVG content string, or null if not found
     */
    private String extractSvgFromOrderItem(CalendarOrderItem orderItem) {
        LOG.debugf("Extracting SVG from order item %s", orderItem.id);

        // Get the main SVG asset from the order item
        ItemAsset mainAsset = orderItem.getMainAsset();
        if (mainAsset != null && mainAsset.svgContent != null && !mainAsset.svgContent.isEmpty()) {
            LOG.debugf("Found SVG in main asset for item %s (%d chars)", orderItem.id, mainAsset.svgContent.length());
            return mainAsset.svgContent;
        }

        // Fallback: check configuration JSON for legacy orders
        if (orderItem.configuration != null && orderItem.configuration.has("generatedSvg")) {
            String svgContent = orderItem.configuration.get("generatedSvg").asText();
            if (svgContent != null && !svgContent.isEmpty()) {
                LOG.debugf("Found SVG in configuration.generatedSvg for item %s (%d chars)", orderItem.id,
                        svgContent.length());
                return svgContent;
            }
        }

        LOG.warnf("No SVG content found for order item %s", orderItem.id);
        return null;
    }

    /** Sanitize string for use in filename */
    private String sanitizeForFilename(String input) {
        if (input == null)
            return "customer";
        return input.replaceAll("[^a-zA-Z0-9.-]", "").toLowerCase();
    }

    /**
     * Check if the current user can access the given order.
     *
     * <p>
     * Access is granted if:
     * <ul>
     * <li>User is authenticated and has ADMIN role</li>
     * <li>User is authenticated and owns the order (order.user matches current user)</li>
     * <li>Order is a guest order (no user) - access via order number acts as shared secret</li>
     * </ul>
     *
     * @param order
     *            The order to check access for
     * @return true if access is allowed
     */
    private boolean canUserAccessOrder(CalendarOrder order) {
        // Guest orders (no user) can be accessed by anyone with the order number
        // The order number serves as a shared secret sent to the customer's email
        if (order.user == null) {
            return true;
        }

        // Check if user is authenticated
        Optional<CalendarUser> currentUser = authService.getCurrentUser(jwt);
        if (currentUser.isEmpty()) {
            return false;
        }

        CalendarUser user = currentUser.get();

        // Admins can access any order
        if (Boolean.TRUE.equals(user.isAdmin)) {
            return true;
        }

        // User can only access their own orders
        return order.user.id.equals(user.id);
    }
}
