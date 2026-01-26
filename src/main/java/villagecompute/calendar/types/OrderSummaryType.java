package villagecompute.calendar.types;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.data.models.CalendarOrderItem;

/**
 * Flattened order summary for admin dashboard. Includes all data needed for the order list view without requiring
 * additional queries.
 */
public class OrderSummaryType {

    public UUID id;
    public String orderNumber;
    public String status;

    // Customer info (flattened)
    public String customerName;
    public String customerEmail;

    // Shipping address as a map for easy JSON serialization
    public Map<String, Object> shippingAddress;

    // Order totals
    public BigDecimal subtotal;
    public BigDecimal shippingCost;
    public BigDecimal taxAmount;
    public BigDecimal totalPrice;

    // Tracking
    public String trackingNumber;
    public String notes;

    // Timestamps
    public Instant created;
    public Instant paidAt;
    public Instant shippedAt;

    // Item summary (simplified)
    public List<OrderItemSummary> items;
    public int totalItemCount;

    public OrderSummaryType() {
    }

    /**
     * Create from CalendarOrder entity with eager-loaded relationships.
     */
    public static OrderSummaryType fromEntity(CalendarOrder order,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        OrderSummaryType summary = new OrderSummaryType();
        summary.id = order.id;
        summary.orderNumber = order.orderNumber;
        summary.status = order.status;

        // Customer info - prefer user displayName, fall back to shipping address name
        if (order.user != null && order.user.displayName != null && !order.user.displayName.isBlank()) {
            summary.customerName = order.user.displayName;
        } else if (order.shippingAddress != null && order.shippingAddress.has("name")) {
            summary.customerName = order.shippingAddress.get("name").asText();
        }

        // Email - prefer customerEmail, fall back to user email
        summary.customerEmail = order.customerEmail != null ? order.customerEmail
                : (order.user != null ? order.user.email : null);

        // Convert shipping address JsonNode to Map
        if (order.shippingAddress != null) {
            try {
                summary.shippingAddress = objectMapper.convertValue(order.shippingAddress, Map.class);
            } catch (Exception e) {
                // Ignore conversion errors
            }
        }

        // Totals
        summary.subtotal = order.subtotal;
        summary.shippingCost = order.shippingCost;
        summary.taxAmount = order.taxAmount;
        summary.totalPrice = order.totalPrice;

        // Tracking
        summary.trackingNumber = order.trackingNumber;
        summary.notes = order.notes;

        // Timestamps
        summary.created = order.created;
        summary.paidAt = order.paidAt;
        summary.shippedAt = order.shippedAt;

        // Items
        if (order.items != null) {
            summary.items = order.items.stream().map(OrderItemSummary::fromEntity).toList();
            summary.totalItemCount = order.items.stream().mapToInt(i -> i.quantity).sum();
        } else {
            summary.items = List.of();
            summary.totalItemCount = 0;
        }

        return summary;
    }

    /**
     * Simplified order item for list view.
     */
    public static class OrderItemSummary {

        public UUID id;
        public String productType;
        public String description;
        public int quantity;
        public BigDecimal unitPrice;
        public BigDecimal lineTotal;
        public String itemStatus;
        public int year;

        public static OrderItemSummary fromEntity(CalendarOrderItem item) {
            OrderItemSummary summary = new OrderItemSummary();
            summary.id = item.id;
            summary.productType = item.productType;
            summary.description = item.description;
            summary.quantity = item.quantity;
            summary.unitPrice = item.unitPrice;
            summary.lineTotal = item.lineTotal;
            summary.itemStatus = item.itemStatus;
            summary.year = item.getYear();
            return summary;
        }
    }
}
