package villagecompute.calendar.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.data.models.CalendarOrderItem;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.Shipment;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.services.jobs.DelayedJobHandler;
import villagecompute.calendar.services.jobs.OrderCancellationJobHandler;
import villagecompute.calendar.services.jobs.OrderEmailJobHandler;
import villagecompute.calendar.services.jobs.ShippingNotificationJobHandler;
import villagecompute.calendar.util.OrderNumberGenerator;

import io.opentelemetry.api.trace.Span;

/**
 * Service for managing calendar orders. Handles order creation, status updates, and order queries.
 */
@ApplicationScoped
public class OrderService {

    private static final Logger LOG = Logger.getLogger(OrderService.class);

    @Inject
    ShippingService shippingService;

    @Inject
    DelayedJobService delayedJobService;

    /**
     * Create a new order for a calendar. The order is created in PENDING status and needs payment confirmation.
     *
     * @param user
     *            User placing the order
     * @param calendar
     *            Calendar being ordered
     * @param quantity
     *            Number of calendars
     * @param unitPrice
     *            Price per calendar
     * @param shippingAddress
     *            Shipping address (JSON)
     * @return Created order
     */
    @Transactional
    public CalendarOrder createOrder(CalendarUser user, UserCalendar calendar, Integer quantity, BigDecimal unitPrice,
            com.fasterxml.jackson.databind.JsonNode shippingAddress) {
        LOG.infof("Creating order for user %s, calendar %s, quantity %d", user.email, calendar.id, quantity);

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
        order.shippingAddress = shippingAddress;

        // Calculate tax and shipping
        BigDecimal tax = calculateTax(order);
        BigDecimal shipping = calculateShipping(order);

        // Store the breakdown for Stripe reporting
        order.subtotal = subtotal;
        order.taxAmount = tax;
        order.shippingCost = shipping;

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

        // Create order item for the calendar
        CalendarOrderItem item = new CalendarOrderItem();
        item.order = order;
        item.generatorType = CalendarOrderItem.GENERATOR_CALENDAR;
        item.description = calendar.name != null ? calendar.name : "Calendar " + calendar.year;
        item.productType = CalendarOrderItem.TYPE_PRINT;
        item.quantity = quantity;
        item.unitPrice = unitPrice;
        item.lineTotal = subtotal;
        item.setYear(calendar.year != null ? calendar.year : Year.now().getValue());
        item.itemStatus = CalendarOrderItem.STATUS_PENDING;
        item.persist();
        order.items.add(item);

        LOG.infof("Created order %s (number: %s) with total price $%.2f (subtotal: $%.2f, tax: $%.2f,"
                + " shipping: $%.2f)", order.id, orderNumber, totalPrice, subtotal, tax, shipping);

        return order;
    }

    /**
     * Update order status (admin only). This method should only be called by authorized admin users.
     *
     * @param orderId
     *            Order ID
     * @param newStatus
     *            New status
     * @param notes
     *            Optional notes about the status change
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

        // Enqueue email notifications based on status changes
        if (CalendarOrder.STATUS_PAID.equals(newStatus)) {
            enqueueEmailJob(order, OrderEmailJobHandler.class);
        } else if (CalendarOrder.STATUS_SHIPPED.equals(newStatus)) {
            enqueueEmailJob(order, ShippingNotificationJobHandler.class);
        }

        // Append notes
        if (notes != null && !notes.isBlank()) {
            String timestamp = Instant.now().toString();
            String noteEntry = String.format("[%s] Status changed from %s to %s: %s%n", timestamp, oldStatus, newStatus,
                    notes);
            order.notes = order.notes == null ? noteEntry : order.notes + noteEntry;
        }

        order.persist();

        LOG.infof("Updated order %s from %s to %s", orderId, oldStatus, newStatus);

        return order;
    }

    /**
     * Get orders by status.
     *
     * @param status
     *            Order status
     * @return List of orders with the specified status
     */
    public List<CalendarOrder> getOrdersByStatus(String status) {
        LOG.debugf("Fetching orders with status: %s", status);
        return CalendarOrder.findByStatusOrderByCreatedDesc(status);
    }

    /**
     * Get all orders for a specific user.
     *
     * @param userId
     *            User ID
     * @return List of user's orders
     */
    public List<CalendarOrder> getUserOrders(UUID userId) {
        LOG.debugf("Fetching orders for user: %s", userId);
        return CalendarOrder.findByUser(userId).list();
    }

    /**
     * Get a single order by ID.
     *
     * @param orderId
     *            Order ID
     * @return Order if found
     */
    public Optional<CalendarOrder> getOrderById(UUID orderId) {
        return CalendarOrder.<CalendarOrder>findByIdOptional(orderId);
    }

    /**
     * Find an order by Stripe Payment Intent ID.
     *
     * @param paymentIntentId
     *            Stripe Payment Intent ID
     * @return Order if found
     */
    public Optional<CalendarOrder> findByStripePaymentIntent(String paymentIntentId) {
        return CalendarOrder.findByStripePaymentIntent(paymentIntentId).firstResultOptional();
    }

    /**
     * Cancel an order. Only allows cancellation of orders that are not yet in terminal states. For paid orders, a
     * refund may need to be processed separately via PaymentService (I3.T3).
     *
     * @param orderId
     *            Order ID to cancel
     * @param userId
     *            User requesting cancellation (for authorization)
     * @param isAdmin
     *            Whether the requesting user is an admin
     * @param cancellationReason
     *            Reason for cancellation
     * @return Cancelled order
     * @throws IllegalArgumentException
     *             if order not found
     * @throws SecurityException
     *             if user is not authorized to cancel the order
     * @throws IllegalStateException
     *             if order cannot be cancelled (already terminal)
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
            throw new IllegalStateException(String.format("Cannot cancel order in %s status", order.status));
        }

        // Validate status transition to CANCELLED
        validateStatusTransition(order.status, CalendarOrder.STATUS_CANCELLED);

        // Use the entity helper method to cancel
        String oldStatus = order.status;
        order.cancel();

        // Enqueue cancellation email notification
        enqueueEmailJob(order, OrderCancellationJobHandler.class);

        // Add cancellation note
        String timestamp = Instant.now().toString();
        String noteEntry = String.format("[%s] Order cancelled from status %s. Reason: %s%n", timestamp, oldStatus,
                cancellationReason != null ? cancellationReason : "No reason provided");
        order.notes = order.notes == null ? noteEntry : order.notes + noteEntry;

        // Note: Actual Stripe refund processing will be handled by PaymentService in I3.T3
        if (CalendarOrder.STATUS_PAID.equals(oldStatus) && order.stripePaymentIntentId != null) {
            String refundNote = String.format("[%s] TODO: Process refund via PaymentService for payment intent %s%n",
                    timestamp, order.stripePaymentIntentId);
            order.notes += refundNote;
        }

        order.persist();

        LOG.infof("Cancelled order %s from status %s", orderId, oldStatus);

        return order;
    }

    // ==================== Item Shipping Methods ====================

    /**
     * Mark a single order item as shipped. For PDF items, this can be done immediately without a physical shipment. For
     * print items, this typically happens as part of a shipment.
     *
     * @param itemId
     *            Order item ID
     * @return Updated order item
     */
    @Transactional
    public CalendarOrderItem markItemAsShipped(UUID itemId) {
        LOG.infof("Marking order item %s as shipped", itemId);

        Optional<CalendarOrderItem> itemOpt = CalendarOrderItem.<CalendarOrderItem>findByIdOptional(itemId);
        if (itemOpt.isEmpty()) {
            throw new IllegalArgumentException("Order item not found: " + itemId);
        }

        CalendarOrderItem item = itemOpt.get();
        item.itemStatus = CalendarOrderItem.STATUS_SHIPPED;
        item.persist();

        // Check if all items in the order are shipped
        updateOrderShippingStatus(item.order);

        LOG.infof("Marked order item %s as shipped", itemId);
        return item;
    }

    /**
     * Mark a single order item as delivered.
     *
     * @param itemId
     *            Order item ID
     * @return Updated order item
     */
    @Transactional
    public CalendarOrderItem markItemAsDelivered(UUID itemId) {
        LOG.infof("Marking order item %s as delivered", itemId);

        Optional<CalendarOrderItem> itemOpt = CalendarOrderItem.<CalendarOrderItem>findByIdOptional(itemId);
        if (itemOpt.isEmpty()) {
            throw new IllegalArgumentException("Order item not found: " + itemId);
        }

        CalendarOrderItem item = itemOpt.get();
        item.itemStatus = CalendarOrderItem.STATUS_DELIVERED;
        item.persist();

        // Check if all items in the order are delivered
        updateOrderDeliveryStatus(item.order);

        LOG.infof("Marked order item %s as delivered", itemId);
        return item;
    }

    /**
     * Create a shipment for selected items in an order.
     *
     * @param orderId
     *            Order ID
     * @param itemIds
     *            List of item IDs to include in the shipment
     * @param carrier
     *            Shipping carrier (USPS, UPS, FEDEX)
     * @param trackingNumber
     *            Tracking number
     * @return Created shipment
     */
    @Transactional
    public Shipment createShipment(UUID orderId, List<UUID> itemIds, String carrier, String trackingNumber) {
        LOG.infof("Creating shipment for order %s with %d items", orderId, itemIds.size());

        Optional<CalendarOrder> orderOpt = CalendarOrder.<CalendarOrder>findByIdOptional(orderId);
        if (orderOpt.isEmpty()) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }

        CalendarOrder order = orderOpt.get();

        // Create the shipment
        Shipment shipment = new Shipment();
        shipment.order = order;
        shipment.carrier = carrier;
        shipment.trackingNumber = trackingNumber;
        shipment.trackingUrl = shipment.generateTrackingUrl();
        shipment.status = Shipment.STATUS_LABEL_CREATED;
        shipment.labelCreatedAt = Instant.now();
        shipment.persist();

        // Add items to the shipment
        for (UUID itemId : itemIds) {
            Optional<CalendarOrderItem> itemOpt = CalendarOrderItem.<CalendarOrderItem>findByIdOptional(itemId);
            if (itemOpt.isPresent()) {
                CalendarOrderItem item = itemOpt.get();
                if (item.order.id.equals(orderId)) {
                    shipment.addItem(item);
                } else {
                    LOG.warnf("Item %s does not belong to order %s", itemId, orderId);
                }
            }
        }

        LOG.infof("Created shipment %s with %d items for order %s", shipment.id, shipment.items.size(), orderId);

        return shipment;
    }

    /**
     * Mark a shipment as shipped (package handed to carrier).
     *
     * @param shipmentId
     *            Shipment ID
     * @return Updated shipment
     */
    @Transactional
    public Shipment markShipmentAsShipped(UUID shipmentId) {
        LOG.infof("Marking shipment %s as shipped", shipmentId);

        Optional<Shipment> shipmentOpt = Shipment.<Shipment>findByIdOptional(shipmentId);
        if (shipmentOpt.isEmpty()) {
            throw new IllegalArgumentException("Shipment not found: " + shipmentId);
        }

        Shipment shipment = shipmentOpt.get();
        shipment.markAsShipped();

        // Update order status
        updateOrderShippingStatus(shipment.order);

        // Enqueue shipping notification email
        enqueueEmailJob(shipment.order, ShippingNotificationJobHandler.class);

        LOG.infof("Marked shipment %s as shipped", shipmentId);
        return shipment;
    }

    /**
     * Auto-ship all digital (PDF) items in an order. Call this when an order is paid to immediately mark PDF items as
     * shipped.
     *
     * @param orderId
     *            Order ID
     * @return Number of items auto-shipped
     */
    @Transactional
    public int autoShipDigitalItems(UUID orderId) {
        LOG.infof("Auto-shipping digital items for order %s", orderId);

        Optional<CalendarOrder> orderOpt = CalendarOrder.<CalendarOrder>findByIdOptional(orderId);
        if (orderOpt.isEmpty()) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }

        CalendarOrder order = orderOpt.get();
        int shippedCount = 0;

        for (CalendarOrderItem item : order.items) {
            if (item.isDigital() && CalendarOrderItem.STATUS_PENDING.equals(item.itemStatus)) {
                item.itemStatus = CalendarOrderItem.STATUS_SHIPPED;
                item.persist();
                shippedCount++;
                LOG.infof("Auto-shipped PDF item %s", item.id);
            }
        }

        // Update order status if needed
        if (shippedCount > 0) {
            updateOrderShippingStatus(order);
        }

        LOG.infof("Auto-shipped %d digital items for order %s", shippedCount, orderId);
        return shippedCount;
    }

    /**
     * Update order status based on item shipping status. If all items are shipped, mark order as shipped.
     */
    private void updateOrderShippingStatus(CalendarOrder order) {
        // Refresh order to get current items
        order = CalendarOrder.<CalendarOrder>findById(order.id);

        boolean allShipped = true;
        boolean anyShipped = false;

        for (CalendarOrderItem item : order.items) {
            if (CalendarOrderItem.STATUS_SHIPPED.equals(item.itemStatus)
                    || CalendarOrderItem.STATUS_DELIVERED.equals(item.itemStatus)) {
                anyShipped = true;
            } else if (!CalendarOrderItem.STATUS_CANCELLED.equals(item.itemStatus)) {
                allShipped = false;
            }
        }

        if (allShipped && anyShipped && !CalendarOrder.STATUS_SHIPPED.equals(order.status)) {
            order.status = CalendarOrder.STATUS_SHIPPED;
            order.shippedAt = Instant.now();
            order.persist();
            LOG.infof("Order %s fully shipped", order.id);
        } else if (anyShipped && CalendarOrder.STATUS_PAID.equals(order.status)) {
            order.status = CalendarOrder.STATUS_PROCESSING;
            order.persist();
            LOG.infof("Order %s partially shipped, moved to PROCESSING", order.id);
        }
    }

    /**
     * Update order status based on item delivery status. If all items are delivered, mark order as delivered.
     */
    private void updateOrderDeliveryStatus(CalendarOrder order) {
        // Refresh order to get current items
        order = CalendarOrder.<CalendarOrder>findById(order.id);

        boolean allDelivered = true;

        for (CalendarOrderItem item : order.items) {
            if (!CalendarOrderItem.STATUS_DELIVERED.equals(item.itemStatus)
                    && !CalendarOrderItem.STATUS_CANCELLED.equals(item.itemStatus)) {
                allDelivered = false;
                break;
            }
        }

        if (allDelivered && !CalendarOrder.STATUS_DELIVERED.equals(order.status)) {
            order.status = CalendarOrder.STATUS_DELIVERED;
            order.persist();
            LOG.infof("Order %s fully delivered", order.id);
        }
    }

    /**
     * Calculate tax for an order. TODO: Integrate with Stripe Tax API for automated tax calculation. For MVP, returns
     * zero as a placeholder.
     *
     * @param order
     *            Order to calculate tax for
     * @return Tax amount (currently always zero)
     */
    private BigDecimal calculateTax(CalendarOrder order) {
        // TODO: Future enhancement - integrate with Stripe Tax API
        // This will provide automated tax calculation based on:
        // - Shipping address (state/country)
        // - Product tax codes
        // - Real-time tax rate updates
        // - Automatic tax filing support
        return BigDecimal.ZERO;
    }

    /**
     * Calculate shipping cost for an order. Delegates to ShippingService for actual rate calculation.
     *
     * @param order
     *            Order to calculate shipping for
     * @return Shipping cost
     */
    private BigDecimal calculateShipping(CalendarOrder order) {
        return shippingService.calculateShippingCost(order);
    }

    /**
     * Enqueue an email job for the specified order. The job will be processed asynchronously by the DelayedJob worker.
     *
     * @param order
     *            Order to send email for
     * @param handlerClass
     *            The handler class to execute
     */
    private void enqueueEmailJob(CalendarOrder order, Class<? extends DelayedJobHandler> handlerClass) {
        try {
            delayedJobService.enqueue(handlerClass, order.id.toString());
            LOG.infof("Enqueued %s email job for order %s", handlerClass.getSimpleName(), order.id);
        } catch (Exception e) {
            // Log but don't fail the operation - the scheduled processor will pick it up
            LOG.error("Failed to enqueue email job for order " + order.id, e);
            Span.current().recordException(e);
        }
    }

    /**
     * Validate that a status transition is allowed.
     *
     * @param currentStatus
     *            Current order status
     * @param newStatus
     *            New order status
     * @throws IllegalStateException
     *             if transition is not allowed
     */
    private void validateStatusTransition(String currentStatus, String newStatus) {
        // Define allowed transitions
        // Flow: PENDING -> PAID -> PROCESSING -> PRINTED -> SHIPPED -> DELIVERED
        boolean isValid = switch (currentStatus) {
            case CalendarOrder.STATUS_PENDING ->
                newStatus.equals(CalendarOrder.STATUS_PAID) || newStatus.equals(CalendarOrder.STATUS_CANCELLED);
            case CalendarOrder.STATUS_PAID -> newStatus.equals(CalendarOrder.STATUS_PROCESSING)
                    || newStatus.equals(CalendarOrder.STATUS_PRINTED) || newStatus.equals(CalendarOrder.STATUS_SHIPPED)
                    || newStatus.equals(CalendarOrder.STATUS_CANCELLED);
            case CalendarOrder.STATUS_PROCESSING ->
                newStatus.equals(CalendarOrder.STATUS_PRINTED) || newStatus.equals(CalendarOrder.STATUS_SHIPPED)
                        || newStatus.equals(CalendarOrder.STATUS_CANCELLED);
            case CalendarOrder.STATUS_PRINTED ->
                newStatus.equals(CalendarOrder.STATUS_SHIPPED) || newStatus.equals(CalendarOrder.STATUS_CANCELLED);
            case CalendarOrder.STATUS_SHIPPED -> newStatus.equals(CalendarOrder.STATUS_DELIVERED);
            case CalendarOrder.STATUS_DELIVERED, CalendarOrder.STATUS_CANCELLED -> false; // Terminal states - no
                                                                                          // transitions allowed
            default -> false;
        };

        if (!isValid) {
            throw new IllegalStateException(
                    String.format("Invalid status transition from %s to %s", currentStatus, newStatus));
        }
    }

    // ==================== Checkout / Payment Methods ====================

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Process a completed checkout and create a single order with multiple items. This is called after Stripe payment
     * is confirmed.
     *
     * @param paymentIntentId
     *            Stripe PaymentIntent ID
     * @param email
     *            Customer email
     * @param shippingAddress
     *            Shipping address map
     * @param billingAddress
     *            Billing address map (optional)
     * @param items
     *            Cart items (list of maps with item details)
     * @param subtotal
     *            Order subtotal
     * @param shippingCost
     *            Shipping cost
     * @param taxAmount
     *            Tax amount
     * @param totalAmount
     *            Total amount charged
     * @return The order number
     */
    @Transactional
    public String processCheckout(String paymentIntentId, String email, Map<String, Object> shippingAddress,
            Map<String, Object> billingAddress, List<Map<String, Object>> items, Double subtotal, Double shippingCost,
            Double taxAmount, Double totalAmount) {
        LOG.infof("Processing checkout for email %s with %d items, PaymentIntent: %s", email,
                items != null ? items.size() : 0, paymentIntentId);

        // Find or create user (guest user if not found)
        CalendarUser user = findOrCreateGuestUser(email, shippingAddress);

        // Generate order number
        int currentYear = Year.now().getValue();
        long orderCountThisYear = CalendarOrder.countOrdersByYear(currentYear);
        String orderNumber = OrderNumberGenerator.generateOrderNumber(currentYear, orderCountThisYear);

        // Convert addresses to JsonNode
        JsonNode shippingAddressJson = objectMapper.valueToTree(shippingAddress);
        JsonNode billingAddressJson = billingAddress != null ? objectMapper.valueToTree(billingAddress) : null;

        // Create the order
        CalendarOrder order = new CalendarOrder();
        order.user = user;
        order.customerEmail = email;
        order.orderNumber = orderNumber;
        order.shippingAddress = shippingAddressJson;
        order.billingAddress = billingAddressJson;
        order.stripePaymentIntentId = paymentIntentId;
        order.subtotal = subtotal != null ? BigDecimal.valueOf(subtotal) : BigDecimal.ZERO;
        order.shippingCost = shippingCost != null ? BigDecimal.valueOf(shippingCost) : BigDecimal.ZERO;
        order.taxAmount = taxAmount != null ? BigDecimal.valueOf(taxAmount) : BigDecimal.ZERO;
        order.totalPrice = totalAmount != null ? BigDecimal.valueOf(totalAmount) : BigDecimal.ZERO;
        order.status = CalendarOrder.STATUS_PAID;
        order.paidAt = Instant.now();

        order.persist();

        // Add items to the order
        if (items != null && !items.isEmpty()) {
            for (Map<String, Object> cartItem : items) {
                CalendarOrderItem orderItem = createOrderItem(order, cartItem);
                order.items.add(orderItem);
            }
        }

        LOG.infof("Created order %s with %d items for PaymentIntent %s", orderNumber, order.items.size(),
                paymentIntentId);

        // Enqueue confirmation email
        enqueueEmailJob(order, OrderEmailJobHandler.class);

        return orderNumber;
    }

    /** Overload for backwards compatibility (without billingAddress) */
    @Transactional
    public String processCheckout(String paymentIntentId, String email, Map<String, Object> shippingAddress,
            List<Map<String, Object>> items, Double subtotal, Double shippingCost, Double taxAmount,
            Double totalAmount) {
        return processCheckout(paymentIntentId, email, shippingAddress, null, items, subtotal, shippingCost, taxAmount,
                totalAmount);
    }

    /** Create an order item from cart item data */
    private CalendarOrderItem createOrderItem(CalendarOrder order, Map<String, Object> cartItem) {
        // Extract cart item details - use description (new field) or fall back to templateName
        String description = (String) cartItem.get("description");
        if (description == null) {
            description = (String) cartItem.get("templateName"); // Legacy fallback
        }
        Integer year = Year.now().getValue();
        String configurationStr = (String) cartItem.get("configuration");
        if (configurationStr != null) {
            try {
                JsonNode config = objectMapper.readTree(configurationStr);
                if (config.has("year")) {
                    year = config.get("year").asInt(year);
                }
            } catch (Exception e) {
                LOG.warnf("Could not parse configuration for year: %s", e.getMessage());
            }
        }
        Integer quantity = cartItem.get("quantity") != null ? ((Number) cartItem.get("quantity")).intValue() : 1;
        Double unitPrice = cartItem.get("unitPrice") != null ? ((Number) cartItem.get("unitPrice")).doubleValue()
                : 29.99;
        Double lineTotal = cartItem.get("lineTotal") != null ? ((Number) cartItem.get("lineTotal")).doubleValue()
                : unitPrice * quantity;

        // Determine product type from configuration or description
        String productType = CalendarOrderItem.TYPE_PRINT;
        if (configurationStr != null) {
            try {
                JsonNode config = objectMapper.readTree(configurationStr);
                if (config.has("productType")) {
                    String pt = config.get("productType").asText();
                    if ("pdf".equalsIgnoreCase(pt) || "digital".equalsIgnoreCase(pt)) {
                        productType = CalendarOrderItem.TYPE_PDF;
                    }
                }
            } catch (Exception e) {
                LOG.warnf("Could not parse configuration for product type: %s", e.getMessage());
            }
        }
        if (description != null && description.toLowerCase().contains("pdf")) {
            productType = CalendarOrderItem.TYPE_PDF;
        }

        // Create a UserCalendar to store the calendar configuration (result not needed - persisted as side effect)
        createCalendarFromConfig(order.user, description, year, configurationStr);

        // Create the order item
        CalendarOrderItem item = new CalendarOrderItem();
        item.order = order;
        item.generatorType = CalendarOrderItem.GENERATOR_CALENDAR;
        item.description = description;
        item.productType = productType;
        item.setYear(year);
        item.quantity = quantity;
        item.unitPrice = BigDecimal.valueOf(unitPrice);
        item.lineTotal = BigDecimal.valueOf(lineTotal);
        item.itemStatus = CalendarOrderItem.STATUS_PENDING;

        // Store configuration as JSON
        if (configurationStr != null && !configurationStr.isEmpty()) {
            try {
                item.configuration = objectMapper.readTree(configurationStr);
            } catch (Exception e) {
                LOG.warnf("Failed to parse item configuration: %s", e.getMessage());
            }
        }

        item.persist();

        LOG.infof("Created order item: %s (%s) x%d @ $%.2f = $%.2f", description, productType, quantity, unitPrice,
                lineTotal);

        return item;
    }

    /** Find existing user by email or create a guest user. */
    @Transactional
    public CalendarUser findOrCreateGuestUser(String email, Map<String, Object> addressInfo) {
        // First try to find by email
        Optional<CalendarUser> existingUser = CalendarUser.findByEmail(email);
        if (existingUser.isPresent()) {
            LOG.infof("Found existing user for email %s", email);
            return existingUser.get();
        }

        // Create a guest user
        LOG.infof("Creating guest user for email %s", email);
        CalendarUser guestUser = new CalendarUser();
        guestUser.email = email;
        guestUser.oauthProvider = "GUEST";
        guestUser.oauthSubject = "guest_" + email; // Use email as unique identifier

        // Try to extract name from address info
        if (addressInfo != null) {
            String firstName = (String) addressInfo.get("firstName");
            String lastName = (String) addressInfo.get("lastName");
            if (firstName != null || lastName != null) {
                guestUser.displayName = ((firstName != null ? firstName : "") + " "
                        + (lastName != null ? lastName : "")).trim();
            }
        }

        guestUser.isAdmin = false;
        guestUser.persist();

        return guestUser;
    }

    /** Create a UserCalendar from cart item configuration. */
    private UserCalendar createCalendarFromConfig(CalendarUser user, String name, Integer year,
            String configurationStr) {
        UserCalendar calendar = new UserCalendar();
        calendar.user = user;
        calendar.name = name != null ? name : "Calendar " + year;
        calendar.year = year;
        calendar.isPublic = false; // Order calendars are private by default

        // Parse configuration if provided
        if (configurationStr != null && !configurationStr.isEmpty()) {
            try {
                JsonNode configJson = objectMapper.readTree(configurationStr);
                calendar.configuration = configJson;

                // Extract SVG if embedded in configuration
                if (configJson.has("generatedSvg")) {
                    calendar.generatedSvg = configJson.get("generatedSvg").asText();
                }
            } catch (Exception e) {
                LOG.warnf("Failed to parse calendar configuration: %s", e.getMessage());
                // Store as-is in a wrapper
                ObjectNode wrapper = objectMapper.createObjectNode();
                wrapper.put("raw", configurationStr);
                calendar.configuration = wrapper;
            }
        }

        calendar.persist();
        LOG.infof("Created UserCalendar %s for order", calendar.id);

        return calendar;
    }

    /**
     * Find order by order number with items loaded. Used for secure PDF downloads and order lookup.
     *
     * @param orderNumber
     *            The order number to search for
     * @return CalendarOrder if found with items loaded, null otherwise
     */
    @Transactional
    public CalendarOrder findByOrderNumber(String orderNumber) {
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            return null;
        }

        try {
            // Find order with items and calendars eagerly loaded in a single query
            Optional<CalendarOrder> orderOpt = CalendarOrder.findByOrderNumberWithItems(orderNumber.trim());

            if (orderOpt.isPresent()) {
                CalendarOrder order = orderOpt.get();
                LOG.infof("Found order %s with %d items", orderNumber, order.items.size());
                return order;
            } else {
                LOG.warnf("No order found with orderNumber: %s", orderNumber);
                return null;
            }
        } catch (Exception e) {
            LOG.errorf(e, "Error finding order by number %s: %s", orderNumber, e.getMessage());
            return null;
        }
    }
}
