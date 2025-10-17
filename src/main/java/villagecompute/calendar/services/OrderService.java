package villagecompute.calendar.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.util.OrderNumberGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing calendar orders.
 * Handles order creation, status updates, and order queries.
 */
@ApplicationScoped
public class OrderService {

    private static final Logger LOG = Logger.getLogger(OrderService.class);

    /**
     * Create a new order for a calendar.
     * The order is created in PENDING status and needs payment confirmation.
     *
     * @param user User placing the order
     * @param calendar Calendar being ordered
     * @param quantity Number of calendars
     * @param unitPrice Price per calendar
     * @param shippingAddress Shipping address (JSON)
     * @return Created order
     */
    @Transactional
    public CalendarOrder createOrder(
        CalendarUser user,
        UserCalendar calendar,
        Integer quantity,
        BigDecimal unitPrice,
        com.fasterxml.jackson.databind.JsonNode shippingAddress
    ) {
        LOG.infof("Creating order for user %s, calendar %s, quantity %d",
            user.email, calendar.id, quantity);

        // Validate inputs
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }

        if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Unit price must be positive");
        }

        // Calculate subtotal
        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

        // Create the order (needed for tax/shipping calculation)
        CalendarOrder order = new CalendarOrder();
        order.user = user;
        order.calendar = calendar;
        order.quantity = quantity;
        order.unitPrice = unitPrice;
        order.shippingAddress = shippingAddress;

        // Calculate tax and shipping
        BigDecimal tax = calculateTax(order);
        BigDecimal shipping = calculateShipping(order);

        // Calculate total price: subtotal + tax + shipping
        BigDecimal totalPrice = subtotal.add(tax).add(shipping);
        order.totalPrice = totalPrice;

        // Set initial status
        order.status = CalendarOrder.STATUS_PENDING;

        // Generate unique order number
        int currentYear = Year.now().getValue();
        long orderCountThisYear = CalendarOrder.countOrdersByYear(currentYear);
        String orderNumber = OrderNumberGenerator.generateOrderNumber(currentYear, orderCountThisYear);
        order.orderNumber = orderNumber;

        // Persist the order
        order.persist();

        LOG.infof("Created order %s (number: %s) with total price $%.2f (subtotal: $%.2f, tax: $%.2f, shipping: $%.2f)",
            order.id, orderNumber, totalPrice, subtotal, tax, shipping);

        return order;
    }

    /**
     * Update order status (admin only).
     * This method should only be called by authorized admin users.
     *
     * @param orderId Order ID
     * @param newStatus New status
     * @param notes Optional notes about the status change
     * @return Updated order
     */
    @Transactional
    public CalendarOrder updateOrderStatus(UUID orderId, String newStatus, String notes) {
        LOG.infof("Updating order %s to status %s", orderId, newStatus);

        // Find the order
        Optional<CalendarOrder> orderOpt = CalendarOrder.<CalendarOrder>findByIdOptional(orderId);
        if (orderOpt.isEmpty()) {
            LOG.errorf("Order not found: %s", orderId);
            throw new IllegalArgumentException("Order not found");
        }

        CalendarOrder order = orderOpt.get();

        // Prevent updates to terminal orders
        if (order.isTerminal()) {
            LOG.errorf("Cannot update terminal order %s (status: %s)", orderId, order.status);
            throw new IllegalStateException("Cannot update a completed or cancelled order");
        }

        // Validate status transition
        validateStatusTransition(order.status, newStatus);

        // Update the order
        String oldStatus = order.status;
        order.status = newStatus;

        // Update timestamps based on status
        if (CalendarOrder.STATUS_PAID.equals(newStatus)) {
            order.paidAt = Instant.now();
        } else if (CalendarOrder.STATUS_SHIPPED.equals(newStatus)) {
            order.shippedAt = Instant.now();
        }

        // Append notes
        if (notes != null && !notes.isBlank()) {
            String timestamp = Instant.now().toString();
            String noteEntry = String.format("[%s] Status changed from %s to %s: %s\n",
                timestamp, oldStatus, newStatus, notes);
            order.notes = order.notes == null ? noteEntry : order.notes + noteEntry;
        }

        order.persist();

        LOG.infof("Updated order %s from %s to %s", orderId, oldStatus, newStatus);

        return order;
    }

    /**
     * Get orders by status.
     *
     * @param status Order status
     * @return List of orders with the specified status
     */
    public List<CalendarOrder> getOrdersByStatus(String status) {
        LOG.debugf("Fetching orders with status: %s", status);
        return CalendarOrder.findByStatusOrderByCreatedDesc(status);
    }

    /**
     * Get all orders for a specific user.
     *
     * @param userId User ID
     * @return List of user's orders
     */
    public List<CalendarOrder> getUserOrders(UUID userId) {
        LOG.debugf("Fetching orders for user: %s", userId);
        return CalendarOrder.findByUser(userId).list();
    }

    /**
     * Get a single order by ID.
     *
     * @param orderId Order ID
     * @return Order if found
     */
    public Optional<CalendarOrder> getOrderById(UUID orderId) {
        return CalendarOrder.<CalendarOrder>findByIdOptional(orderId);
    }

    /**
     * Find an order by Stripe Payment Intent ID.
     *
     * @param paymentIntentId Stripe Payment Intent ID
     * @return Order if found
     */
    public Optional<CalendarOrder> findByStripePaymentIntent(String paymentIntentId) {
        return CalendarOrder.findByStripePaymentIntent(paymentIntentId).firstResultOptional();
    }

    /**
     * Cancel an order.
     * Only allows cancellation of orders that are not yet in terminal states.
     * For paid orders, a refund may need to be processed separately via PaymentService (I3.T3).
     *
     * @param orderId Order ID to cancel
     * @param userId User requesting cancellation (for authorization)
     * @param isAdmin Whether the requesting user is an admin
     * @param cancellationReason Reason for cancellation
     * @return Cancelled order
     * @throws IllegalArgumentException if order not found
     * @throws SecurityException if user is not authorized to cancel the order
     * @throws IllegalStateException if order cannot be cancelled (already terminal)
     */
    @Transactional
    public CalendarOrder cancelOrder(UUID orderId, UUID userId, boolean isAdmin, String cancellationReason) {
        LOG.infof("Cancelling order %s, requested by user %s (admin: %b)", orderId, userId, isAdmin);

        // Find the order
        Optional<CalendarOrder> orderOpt = CalendarOrder.<CalendarOrder>findByIdOptional(orderId);
        if (orderOpt.isEmpty()) {
            LOG.errorf("Order not found: %s", orderId);
            throw new IllegalArgumentException("Order not found");
        }

        CalendarOrder order = orderOpt.get();

        // Authorization check: user must own the order or be an admin
        if (!isAdmin && !order.user.id.equals(userId)) {
            LOG.errorf("User %s attempted to cancel order %s owned by user %s", userId, orderId, order.user.id);
            throw new SecurityException("You are not authorized to cancel this order");
        }

        // Validate that the order can be cancelled
        if (order.isTerminal()) {
            LOG.errorf("Cannot cancel terminal order %s (status: %s)", orderId, order.status);
            throw new IllegalStateException(
                String.format("Cannot cancel order in %s status", order.status)
            );
        }

        // Validate status transition to CANCELLED
        validateStatusTransition(order.status, CalendarOrder.STATUS_CANCELLED);

        // Use the entity helper method to cancel
        String oldStatus = order.status;
        order.cancel();

        // Add cancellation note
        String timestamp = Instant.now().toString();
        String noteEntry = String.format("[%s] Order cancelled from status %s. Reason: %s\n",
            timestamp, oldStatus, cancellationReason != null ? cancellationReason : "No reason provided");
        order.notes = order.notes == null ? noteEntry : order.notes + noteEntry;

        // Note: Actual Stripe refund processing will be handled by PaymentService in I3.T3
        if (CalendarOrder.STATUS_PAID.equals(oldStatus) && order.stripePaymentIntentId != null) {
            String refundNote = String.format("[%s] TODO: Process refund via PaymentService for payment intent %s\n",
                timestamp, order.stripePaymentIntentId);
            order.notes += refundNote;
        }

        order.persist();

        LOG.infof("Cancelled order %s from status %s", orderId, oldStatus);

        return order;
    }

    /**
     * Calculate tax for an order.
     * TODO: Implement actual tax calculation in task I3.T8.
     * For now, returns zero as a placeholder.
     *
     * @param order Order to calculate tax for
     * @return Tax amount (currently always zero)
     */
    private BigDecimal calculateTax(CalendarOrder order) {
        // TODO: Implement tax calculation based on shipping address in I3.T8
        // Tax calculation will depend on:
        // - Shipping address (state/country)
        // - Tax rates configuration
        // - Taxable amount (subtotal)
        return BigDecimal.ZERO;
    }

    /**
     * Calculate shipping cost for an order.
     * TODO: Implement actual shipping calculation in task I3.T8.
     * For now, returns zero as a placeholder.
     *
     * @param order Order to calculate shipping for
     * @return Shipping cost (currently always zero)
     */
    private BigDecimal calculateShipping(CalendarOrder order) {
        // TODO: Implement shipping calculation based on order details in I3.T8
        // Shipping calculation will depend on:
        // - Shipping address (domestic vs international)
        // - Quantity and weight
        // - Shipping method (standard, express, etc.)
        return BigDecimal.ZERO;
    }

    /**
     * Validate that a status transition is allowed.
     *
     * @param currentStatus Current order status
     * @param newStatus New order status
     * @throws IllegalStateException if transition is not allowed
     */
    private void validateStatusTransition(String currentStatus, String newStatus) {
        // Define allowed transitions
        boolean isValid = switch (currentStatus) {
            case CalendarOrder.STATUS_PENDING ->
                newStatus.equals(CalendarOrder.STATUS_PAID) ||
                newStatus.equals(CalendarOrder.STATUS_CANCELLED);
            case CalendarOrder.STATUS_PAID ->
                newStatus.equals(CalendarOrder.STATUS_PROCESSING) ||
                newStatus.equals(CalendarOrder.STATUS_SHIPPED) ||
                newStatus.equals(CalendarOrder.STATUS_CANCELLED);
            case CalendarOrder.STATUS_PROCESSING ->
                newStatus.equals(CalendarOrder.STATUS_SHIPPED) ||
                newStatus.equals(CalendarOrder.STATUS_CANCELLED);
            case CalendarOrder.STATUS_SHIPPED ->
                newStatus.equals(CalendarOrder.STATUS_DELIVERED);
            case CalendarOrder.STATUS_DELIVERED, CalendarOrder.STATUS_CANCELLED ->
                false; // Terminal states - no transitions allowed
            default -> false;
        };

        if (!isValid) {
            throw new IllegalStateException(
                String.format("Invalid status transition from %s to %s", currentStatus, newStatus)
            );
        }
    }
}
