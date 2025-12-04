package villagecompute.calendar.api.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stripe.exception.StripeException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.graphql.*;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;
import villagecompute.calendar.api.graphql.inputs.OrderInput;
import villagecompute.calendar.api.graphql.inputs.OrderStatusUpdateInput;
import villagecompute.calendar.api.graphql.inputs.PlaceOrderInput;
import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.services.AuthenticationService;
import villagecompute.calendar.services.OrderService;
import villagecompute.calendar.services.PaymentService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * GraphQL resolver for order queries and mutations.
 * Handles order creation, payment processing, and order management.
 */
@GraphQLApi
@ApplicationScoped
public class OrderGraphQL {

    private static final Logger LOG = Logger.getLogger(OrderGraphQL.class);

    // Base price per calendar (can be made configurable later)
    private static final BigDecimal BASE_UNIT_PRICE = new BigDecimal("29.99");

    @Inject
    JsonWebToken jwt;

    @Inject
    AuthenticationService authService;

    @Inject
    OrderService orderService;

    @Inject
    PaymentService paymentService;

    @Inject
    ObjectMapper objectMapper;

    // ============================================================================
    // QUERIES
    // ============================================================================

    /**
     * Get orders for the authenticated user.
     * Optionally filter by order status.
     *
     * @param status Optional order status filter
     * @return List of user's orders
     */
    @Query("myOrders")
    @Description("Get orders for the authenticated user. Requires authentication.")
    @RolesAllowed("USER")
    public List<CalendarOrder> myOrders(
        @Name("status")
        @Description("Filter by order status (optional)")
        String status
    ) {
        LOG.debugf("Query: myOrders(status=%s)", status);

        // Get current user from JWT
        Optional<CalendarUser> currentUser = authService.getCurrentUser(jwt);
        if (currentUser.isEmpty()) {
            LOG.error("User not found despite passing @RolesAllowed check");
            throw new IllegalStateException("Unauthorized: User not found");
        }

        CalendarUser user = currentUser.get();
        List<CalendarOrder> orders = orderService.getUserOrders(user.id);

        // Apply status filter if provided
        if (status != null && !status.isEmpty()) {
            orders = orders.stream()
                .filter(order -> status.equals(order.status))
                .toList();
            LOG.infof("Found %d orders for user %s with status %s", orders.size(), user.email, status);
        } else {
            LOG.infof("Found %d orders for user %s (all statuses)", orders.size(), user.email);
        }

        return orders;
    }

    /**
     * Get orders for a specific user (admin only) with optional status filter.
     * If userId is provided, requires ADMIN role.
     * If userId is NOT provided, returns orders for authenticated user.
     *
     * @param userId User ID to fetch orders for (admin only)
     * @param status Order status filter (optional)
     * @return List of orders
     */
    @Query("orders")
    @Description("Get orders for a specific user (admin only) with optional status filter. If userId is not provided, returns orders for authenticated user.")
    @RolesAllowed("USER")
    public List<CalendarOrder> orders(
        @Name("userId")
        @Description("User ID to fetch orders for (admin only)")
        String userId,

        @Name("status")
        @Description("Filter by order status (optional)")
        String status
    ) {
        LOG.infof("Query: orders(userId=%s, status=%s)", userId, status);

        // Get current user from JWT
        Optional<CalendarUser> currentUser = authService.getCurrentUser(jwt);
        if (currentUser.isEmpty()) {
            LOG.error("User not found despite passing @RolesAllowed check");
            throw new IllegalStateException("Unauthorized: User not found");
        }

        CalendarUser user = currentUser.get();

        // Determine target user ID
        UUID targetUserId;
        if (userId != null && !userId.isEmpty()) {
            // Admin access requested - verify admin role
            boolean isAdmin = jwt.getGroups() != null && jwt.getGroups().contains("ADMIN");
            if (!isAdmin) {
                LOG.errorf("Non-admin user %s attempted to access orders for userId=%s",
                    user.email, userId);
                throw new SecurityException("Unauthorized: ADMIN role required to access other users' orders");
            }

            // Parse the provided user ID
            try {
                targetUserId = UUID.fromString(userId);
            } catch (IllegalArgumentException e) {
                LOG.errorf("Invalid user ID format: %s", userId);
                throw new IllegalArgumentException("Invalid user ID format");
            }

            LOG.infof("Admin %s accessing orders for user %s", user.email, userId);
        } else {
            // No userId provided - return current user's orders
            targetUserId = user.id;
            LOG.infof("Returning orders for current user %s", user.email);
        }

        // Get orders for target user
        List<CalendarOrder> orders = orderService.getUserOrders(targetUserId);

        // Apply status filter if provided
        if (status != null && !status.isEmpty()) {
            orders = orders.stream()
                .filter(order -> status.equals(order.status))
                .toList();
            LOG.infof("Found %d orders for user %s with status %s", orders.size(), targetUserId, status);
        } else {
            LOG.infof("Found %d orders for user %s (all statuses)", orders.size(), targetUserId);
        }

        return orders;
    }

    /**
     * Get a single order by ID.
     * User must own the order, or be an admin.
     *
     * @param id Order ID
     * @return Order if found and authorized
     */
    @Query("order")
    @Description("Get a single order by ID. User must own the order or be an admin.")
    @RolesAllowed("USER")
    public CalendarOrder order(
        @Name("id")
        @Description("Order ID")
        @NonNull
        String id
    ) {
        LOG.infof("Query: order(id=%s)", id);

        // Get current user
        Optional<CalendarUser> currentUser = authService.getCurrentUser(jwt);
        if (currentUser.isEmpty()) {
            throw new IllegalStateException("Unauthorized: User not found");
        }

        CalendarUser user = currentUser.get();

        // Parse order ID
        UUID orderId;
        try {
            orderId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            LOG.errorf("Invalid order ID format: %s", id);
            throw new IllegalArgumentException("Invalid order ID format");
        }

        // Find the order
        Optional<CalendarOrder> orderOpt = orderService.getOrderById(orderId);
        if (orderOpt.isEmpty()) {
            LOG.warnf("Order not found: %s", id);
            return null;
        }

        CalendarOrder order = orderOpt.get();

        // Check authorization (user must own the order or be admin)
        boolean isOwner = order.user != null && order.user.id.equals(user.id);
        boolean isAdmin = jwt.getGroups() != null && jwt.getGroups().contains("ADMIN");

        if (!isOwner && !isAdmin) {
            LOG.warnf("User %s attempted to access order %s owned by another user",
                user.email, id);
            throw new SecurityException("Unauthorized: You don't have access to this order");
        }

        return order;
    }

    /**
     * Get all orders across all users (admin only).
     * Supports optional status filter and result limit.
     *
     * @param status Order status filter (optional)
     * @param limit Maximum number of orders to return
     * @return List of orders
     */
    @Query("allOrders")
    @Description("Get all orders across all users (admin only). Requires ADMIN role in JWT claims.")
    @RolesAllowed("ADMIN")
    public List<CalendarOrder> allOrders(
        @Name("status")
        @Description("Filter by order status (optional)")
        String status,

        @Name("limit")
        @Description("Maximum number of orders to return (default: 50)")
        Integer limit
    ) {
        LOG.infof("Query: allOrders(status=%s, limit=%s)", status, limit);

        // Apply limit with default of 50
        int maxResults = (limit != null && limit > 0) ? limit : 50;

        // Get orders filtered by status (if provided)
        List<CalendarOrder> orders;
        if (status != null && !status.isEmpty()) {
            orders = orderService.getOrdersByStatus(status);
            // Apply limit to filtered results
            if (orders.size() > maxResults) {
                orders = orders.subList(0, maxResults);
            }
        } else {
            // Get all orders with limit
            orders = CalendarOrder.<CalendarOrder>findAll()
                .page(0, maxResults)
                .list();
        }

        LOG.infof("Returning %d orders (status=%s, limit=%d)", orders.size(), status, maxResults);
        return orders;
    }

    // ============================================================================
    // MUTATIONS
    // ============================================================================

    /**
     * Create a new order and Stripe PaymentIntent.
     * Returns the order with Stripe client secret for payment processing.
     *
     * @param input Order creation data
     * @return Created order with payment details
     */
    @Mutation("createOrder")
    @Description("Create a new order and Stripe PaymentIntent. Returns order with clientSecret for payment.")
    @RolesAllowed("USER")
    @Transactional
    public CreateOrderResponse createOrder(
        @Name("input")
        @Description("Order creation data")
        @NotNull
        @Valid
        OrderInput input
    ) {
        LOG.infof("Mutation: createOrder(calendarId=%s, quantity=%d)",
            input.calendarId, input.quantity);

        // Get current user
        Optional<CalendarUser> currentUser = authService.getCurrentUser(jwt);
        if (currentUser.isEmpty()) {
            LOG.error("User not found despite passing @RolesAllowed check");
            throw new IllegalStateException("Unauthorized: User not found");
        }

        CalendarUser user = currentUser.get();

        // Validate and find the calendar
        UUID calendarId;
        try {
            calendarId = UUID.fromString(input.calendarId);
        } catch (IllegalArgumentException e) {
            LOG.errorf("Invalid calendar ID format: %s", input.calendarId);
            throw new IllegalArgumentException("Invalid calendar ID format");
        }

        Optional<UserCalendar> calendarOpt = UserCalendar.<UserCalendar>findByIdOptional(calendarId);
        if (calendarOpt.isEmpty()) {
            LOG.errorf("Calendar not found: %s", input.calendarId);
            throw new IllegalArgumentException("Calendar not found");
        }

        UserCalendar calendar = calendarOpt.get();

        // Verify ownership
        if (calendar.user == null || !calendar.user.id.equals(user.id)) {
            LOG.errorf("User %s attempted to order calendar %s owned by another user",
                user.email, input.calendarId);
            throw new SecurityException("Unauthorized: You don't own this calendar");
        }

        // Convert shipping address to JSON
        ObjectNode shippingAddressJson = objectMapper.valueToTree(input.shippingAddress);

        // Create the order
        CalendarOrder order = orderService.createOrder(
            user,
            calendar,
            input.quantity,
            BASE_UNIT_PRICE,
            shippingAddressJson
        );

        // Create Stripe PaymentIntent with full breakdown for tax reporting
        try {
            // Calculate subtotal (unit price * quantity)
            BigDecimal subtotal = order.unitPrice.multiply(BigDecimal.valueOf(order.quantity));

            Map<String, String> paymentDetails = paymentService.createPaymentIntent(
                order.totalPrice,
                "usd",
                order.id.toString(),
                subtotal,
                order.taxAmount,
                order.shippingCost,
                order.orderNumber
            );

            // Update order with Stripe payment intent ID
            order.stripePaymentIntentId = paymentDetails.get("paymentIntentId");
            order.persist();

            LOG.infof("Created order %s with PaymentIntent %s",
                order.id, order.stripePaymentIntentId);

            // Return response with client secret
            CreateOrderResponse response = new CreateOrderResponse();
            response.order = order;
            response.clientSecret = paymentDetails.get("clientSecret");
            return response;

        } catch (StripeException e) {
            LOG.errorf(e, "Failed to create PaymentIntent for order %s", order.id);
            throw new RuntimeException("Failed to create payment intent: " + e.getMessage());
        }
    }

    /**
     * Place an order for printed calendars.
     * Alternative to createOrder mutation with more explicit input structure.
     * Returns Stripe PaymentIntent for checkout processing.
     *
     * TODO: Implement placeOrder with ProductType support and pricing logic
     *
     * @param input Order placement data (includes productType)
     * @return Created order with payment details
     */
    @Mutation("placeOrder")
    @Description("Place an order for printed calendars. Alternative to createOrder with structured input type. Returns PaymentIntent for checkout.")
    @RolesAllowed("USER")
    @Transactional
    public CreateOrderResponse placeOrder(
        @Name("input")
        @Description("Order placement data")
        @NotNull
        @Valid
        PlaceOrderInput input
    ) {
        LOG.infof("Mutation: placeOrder(calendarId=%s, productType=%s, quantity=%d) (STUB IMPLEMENTATION)",
            input.calendarId, input.productType, input.quantity);

        // Get current user
        Optional<CalendarUser> currentUser = authService.getCurrentUser(jwt);
        if (currentUser.isEmpty()) {
            LOG.error("User not found despite passing @RolesAllowed check");
            throw new IllegalStateException("Unauthorized: User not found");
        }

        CalendarUser user = currentUser.get();

        // TODO: Implement the actual placeOrder logic:
        // 1. Validate calendar ID and verify ownership (similar to createOrder)
        // 2. Use productType to determine pricing (different prices for WALL_CALENDAR, DESK_CALENDAR, POSTER)
        // 3. Create order with product-specific pricing via orderService
        // 4. Create Stripe PaymentIntent via paymentService
        // 5. Return CreateOrderResponse with order and clientSecret
        //
        // Note: This differs from createOrder by accepting PlaceOrderInput which includes productType field.
        // The productType allows different product formats with different pricing.

        LOG.warnf("placeOrder mutation not yet implemented. Would create order for calendar %s with product type %s",
            input.calendarId, input.productType);

        throw new UnsupportedOperationException(
            "placeOrder mutation not yet implemented. " +
            "TODO: Implement product-type-specific pricing and order creation. " +
            "Use createOrder mutation as a temporary alternative."
        );
    }

    /**
     * Update order status (admin only).
     *
     * @param input Order status update data
     * @return Updated order
     */
    @Mutation("updateOrderStatus")
    @Description("Update order status (admin only). Used to move orders through fulfillment stages.")
    @RolesAllowed("ADMIN")
    @Transactional
    public CalendarOrder updateOrderStatus(
        @Name("input")
        @Description("Order status update data")
        @NotNull
        @Valid
        OrderStatusUpdateInput input
    ) {
        LOG.infof("Mutation: updateOrderStatus(orderId=%s, status=%s)",
            input.orderId, input.status);

        // Parse order ID
        UUID orderId;
        try {
            orderId = UUID.fromString(input.orderId);
        } catch (IllegalArgumentException e) {
            LOG.errorf("Invalid order ID format: %s", input.orderId);
            throw new IllegalArgumentException("Invalid order ID format");
        }

        // Update the order
        return orderService.updateOrderStatus(orderId, input.status, input.notes);
    }

    /**
     * Cancel an order and initiate refund.
     * Requires authentication and order ownership (or admin role).
     * Can only cancel orders in PENDING or PAID status.
     * Automatically triggers Stripe refund for paid orders.
     *
     * TODO: Implement Stripe refund integration and order cancellation logic
     *
     * @param orderId Order ID to cancel
     * @param reason Reason for cancellation (optional, stored in notes)
     * @return Cancelled order
     */
    @Mutation("cancelOrder")
    @Description("Cancel an order and initiate refund. Can only cancel orders in PENDING or PAID status.")
    @RolesAllowed("USER")
    @Transactional
    public CalendarOrder cancelOrder(
        @Name("orderId")
        @Description("Order ID to cancel")
        @NonNull
        String orderId,

        @Name("reason")
        @Description("Reason for cancellation (optional, stored in notes)")
        String reason
    ) {
        LOG.infof("Mutation: cancelOrder(orderId=%s, reason=%s) (STUB IMPLEMENTATION)",
            orderId, reason);

        // Get current user
        Optional<CalendarUser> currentUser = authService.getCurrentUser(jwt);
        if (currentUser.isEmpty()) {
            throw new IllegalStateException("Unauthorized: User not found");
        }

        CalendarUser user = currentUser.get();

        // Parse order ID
        UUID orderIdUuid;
        try {
            orderIdUuid = UUID.fromString(orderId);
        } catch (IllegalArgumentException e) {
            LOG.errorf("Invalid order ID format: %s", orderId);
            throw new IllegalArgumentException("Invalid order ID format");
        }

        // Find the order
        Optional<CalendarOrder> orderOpt = orderService.getOrderById(orderIdUuid);
        if (orderOpt.isEmpty()) {
            LOG.warnf("Order not found: %s", orderId);
            throw new IllegalArgumentException("Order not found");
        }

        CalendarOrder order = orderOpt.get();

        // Check authorization (user must own the order or be admin)
        boolean isOwner = order.user != null && order.user.id.equals(user.id);
        boolean isAdmin = jwt.getGroups() != null && jwt.getGroups().contains("ADMIN");

        if (!isOwner && !isAdmin) {
            LOG.warnf("User %s attempted to cancel order %s owned by another user",
                user.email, orderId);
            throw new SecurityException("Unauthorized: You don't have access to this order");
        }

        // TODO: Implement the actual cancellation logic:
        // 1. Verify order is in PENDING or PAID status (not PROCESSING, SHIPPED, or DELIVERED)
        // 2. If order is PAID, initiate Stripe refund via paymentService
        // 3. Update order status to CANCELLED
        // 4. Store cancellation reason in notes field
        // 5. Update order timestamps

        LOG.warnf("Order cancellation not yet implemented. Order %s would be cancelled with reason: %s",
            orderId, reason);

        throw new UnsupportedOperationException(
            "Order cancellation not yet implemented. " +
            "TODO: Implement Stripe refund integration and order status update to CANCELLED."
        );
    }

    // ============================================================================
    // RESPONSE TYPES
    // ============================================================================

    /**
     * Response type for createOrder mutation.
     * Includes the order and Stripe client secret for payment processing.
     */
    @Type("CreateOrderResponse")
    public static class CreateOrderResponse {
        @NonNull
        public CalendarOrder order;

        @NonNull
        @Description("Stripe client secret for payment processing")
        public String clientSecret;
    }
}
