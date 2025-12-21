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
import villagecompute.calendar.data.models.CalendarOrderItem;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.services.AuthenticationService;
import villagecompute.calendar.services.OrderService;
import villagecompute.calendar.services.PaymentService;
import villagecompute.calendar.services.ProductService;
import villagecompute.calendar.util.OrderNumberGenerator;

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

    // Product code constants
    private static final String PRODUCT_CODE_PRINT = "print";

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

    @Inject
    ProductService productService;

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
     * Get a single order by order number (e.g., "VC-MB2B-UN2Z").
     * This is a public query for order confirmation pages - no auth required.
     * Only returns order if found (does not expose order existence to unauthorized users).
     *
     * @param orderNumber Order number (e.g., "VC-MB2B-UN2Z")
     * @return Order if found
     */
    @Query("orderByNumber")
    @Description("Get a single order by order number. Used for order confirmation pages.")
    public CalendarOrder orderByNumber(
        @Name("orderNumber")
        @Description("Order number (e.g., VC-MB2B-UN2Z)")
        @NonNull
        String orderNumber
    ) {
        LOG.infof("Query: orderByNumber(orderNumber=%s)", orderNumber);

        // Find order by order number
        CalendarOrder order = CalendarOrder.findByOrderNumber(orderNumber).firstResult();

        if (order == null) {
            LOG.warnf("Order not found by number: %s", orderNumber);
            return null;
        }

        LOG.infof("Found order %s (id=%s) with %d items", orderNumber, order.id,
            order.items != null ? order.items.size() : 0);

        return order;
    }

    /**
     * Get a single order by order number and UUID (secure lookup).
     * This is a public query for the order status page - no auth required.
     * Both the order number AND UUID must match for security (prevents enumeration).
     *
     * @param orderNumber Order number (e.g., "VC-MB2B-UN2Z")
     * @param orderId Order UUID
     * @return Order if found and both identifiers match
     */
    @Query("orderByNumberAndId")
    @Description("Get a single order by order number and UUID. Used for secure public order status pages.")
    public CalendarOrder orderByNumberAndId(
        @Name("orderNumber")
        @Description("Order number (e.g., VC-MB2B-UN2Z)")
        @NonNull
        String orderNumber,
        @Name("orderId")
        @Description("Order UUID")
        @NonNull
        String orderId
    ) {
        LOG.infof("Query: orderByNumberAndId(orderNumber=%s, orderId=%s)", orderNumber, orderId);

        // Parse UUID
        java.util.UUID orderUuid;
        try {
            orderUuid = java.util.UUID.fromString(orderId);
        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid orderId format: %s", orderId);
            return null;
        }

        // Find order by order number first
        CalendarOrder order = CalendarOrder.findByOrderNumber(orderNumber).firstResult();

        if (order == null) {
            LOG.warnf("Order not found by number: %s", orderNumber);
            return null;
        }

        // Verify UUID matches (security check to prevent enumeration)
        if (!order.id.equals(orderUuid)) {
            LOG.warnf("Order UUID mismatch for order number: %s (expected=%s, got=%s)",
                orderNumber, order.id, orderId);
            return null;
        }

        LOG.infof("Found order %s (id=%s) with %d items", orderNumber, order.id,
            order.items != null ? order.items.size() : 0);

        return order;
    }

    /**
     * Get a single order by Stripe checkout session ID.
     * This is used when returning from Stripe embedded checkout.
     * Retrieves the session from Stripe to get the orderId from metadata.
     *
     * @param sessionId Stripe checkout session ID
     * @return Order if found
     */
    @Query("orderByStripeSessionId")
    @Description("Get a single order by Stripe checkout session ID. Used for order confirmation after Stripe redirect.")
    public CalendarOrder orderByStripeSessionId(
        @Name("sessionId")
        @Description("Stripe checkout session ID")
        @NonNull
        String sessionId
    ) {
        LOG.infof("Query: orderByStripeSessionId(sessionId=%s)", sessionId);

        try {
            // Retrieve the session from Stripe
            com.stripe.model.checkout.Session session = paymentService.getCheckoutSession(sessionId);

            if (session == null) {
                LOG.warnf("Stripe session not found: %s", sessionId);
                return null;
            }

            // Extract orderId from session metadata
            String orderId = session.getMetadata().get("orderId");
            if (orderId == null || orderId.isEmpty()) {
                LOG.warnf("No orderId in session metadata for session: %s", sessionId);
                return null;
            }

            // Parse and look up the order
            UUID orderUuid;
            try {
                orderUuid = UUID.fromString(orderId);
            } catch (IllegalArgumentException e) {
                LOG.errorf("Invalid orderId format in session %s: %s", sessionId, orderId);
                return null;
            }

            Optional<CalendarOrder> orderOpt = orderService.getOrderById(orderUuid);
            if (orderOpt.isEmpty()) {
                LOG.warnf("Order not found for session %s, orderId: %s", sessionId, orderId);
                return null;
            }

            LOG.infof("Found order %s for session %s", orderId, sessionId);
            return orderOpt.get();

        } catch (com.stripe.exception.StripeException e) {
            LOG.errorf(e, "Failed to retrieve Stripe session: %s", sessionId);
            return null;
        }
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

    /**
     * Create a Stripe Checkout Session for embedded checkout.
     * This creates an order and returns a client secret for the embedded checkout component.
     * The checkout session handles payment, shipping address collection, and tax calculation.
     *
     * @param input Checkout session input
     * @return Checkout session response with clientSecret
     */
    @Mutation("createCheckoutSession")
    @Description("Create a Stripe Checkout Session for embedded checkout. Returns clientSecret for Stripe embedded checkout component.")
    @Transactional
    public CheckoutSessionResponse createCheckoutSession(
        @Name("input")
        @Description("Checkout session input")
        @NotNull
        @Valid
        CheckoutSessionInput input
    ) {
        LOG.infof("Mutation: createCheckoutSession(cartId=%s, email=%s)", input.cartId, input.customerEmail);

        try {
            // Get cart items and create line items
            List<PaymentService.CheckoutLineItem> lineItems = new java.util.ArrayList<>();

            // Parse cart items from input
            if (input.items != null) {
                for (CheckoutItemInput item : input.items) {
                    // Get price from ProductService if unitPrice is not provided
                    long unitAmountCents;
                    if (item.unitPrice != null && item.unitPrice > 0) {
                        unitAmountCents = Math.round(item.unitPrice * 100);
                    } else {
                        // Look up price from product catalog
                        String productCode = item.productCode != null ? item.productCode : PRODUCT_CODE_PRINT;
                        BigDecimal price = productService.getPrice(productCode);
                        unitAmountCents = price.multiply(BigDecimal.valueOf(100)).longValue();
                        LOG.infof("Using product catalog price for '%s': $%.2f", productCode, price);
                    }
                    lineItems.add(new PaymentService.CheckoutLineItem(
                        item.name,
                        item.description != null ? item.description : "Calendar " + item.year,
                        unitAmountCents,
                        item.quantity
                    ));
                }
            }

            if (lineItems.isEmpty()) {
                throw new IllegalArgumentException("No items in cart");
            }

            // Create a pending order
            // For now, we'll create a minimal order that will be updated when the session completes
            CalendarOrder order = new CalendarOrder();

            // Get current user if authenticated
            Optional<CalendarUser> currentUser = authService.getCurrentUser(jwt);
            if (currentUser.isPresent()) {
                order.user = currentUser.get();
            }

            // Set order details
            order.status = CalendarOrder.STATUS_PENDING;
            order.quantity = lineItems.stream().mapToInt(i -> (int) i.quantity).sum();

            // Calculate totals from line items
            BigDecimal subtotal = lineItems.stream()
                .map(i -> BigDecimal.valueOf(i.unitAmountCents * i.quantity).divide(BigDecimal.valueOf(100)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            order.unitPrice = subtotal.divide(BigDecimal.valueOf(order.quantity), 2, java.math.RoundingMode.HALF_UP);
            order.subtotal = subtotal;
            order.totalPrice = subtotal; // Will be updated with tax/shipping from Stripe

            // Generate order number
            order.orderNumber = OrderNumberGenerator.generateSecureOrderNumber();

            order.persist();
            LOG.infof("Created pending order %s for checkout session", order.id);

            // Create order items from input
            if (input.items != null) {
                for (CheckoutItemInput itemInput : input.items) {
                    CalendarOrderItem orderItem = new CalendarOrderItem();
                    orderItem.order = order;

                    // Determine product type from productCode
                    String productCode = itemInput.productCode != null ? itemInput.productCode : PRODUCT_CODE_PRINT;
                    orderItem.productType = "pdf".equalsIgnoreCase(productCode)
                        ? CalendarOrderItem.TYPE_PDF
                        : CalendarOrderItem.TYPE_PRINT;

                    orderItem.productName = itemInput.name;
                    orderItem.calendarYear = itemInput.year != null ? itemInput.year : java.time.Year.now().getValue();
                    orderItem.quantity = itemInput.quantity != null ? itemInput.quantity : 1;

                    // Get unit price
                    if (itemInput.unitPrice != null && itemInput.unitPrice > 0) {
                        orderItem.unitPrice = BigDecimal.valueOf(itemInput.unitPrice);
                    } else {
                        orderItem.unitPrice = productService.getPrice(productCode);
                    }

                    // Calculate line total
                    orderItem.calculateLineTotal();

                    // Parse and store configuration
                    if (itemInput.configuration != null && !itemInput.configuration.isEmpty()) {
                        try {
                            orderItem.configuration = objectMapper.readTree(itemInput.configuration);
                        } catch (Exception e) {
                            LOG.warnf("Failed to parse item configuration: %s", e.getMessage());
                        }
                    }

                    orderItem.itemStatus = CalendarOrderItem.STATUS_PENDING;
                    orderItem.persist();
                    order.items.add(orderItem);

                    LOG.infof("Created order item: %s (%s) x%d @ $%.2f",
                        orderItem.productName, orderItem.productType, orderItem.quantity, orderItem.unitPrice);
                }
            }

            // Determine if shipping is required (any print products)
            boolean shippingRequired = input.items != null && input.items.stream()
                .anyMatch(item -> PRODUCT_CODE_PRINT.equals(item.productCode));

            // Build success/cancel URLs
            String baseUrl = input.returnUrl != null ? input.returnUrl : "https://villagecompute.com";
            String successUrl = baseUrl + "/order-confirmation";

            // Create checkout session
            Map<String, String> sessionDetails = paymentService.createCheckoutSession(
                order.id.toString(),
                order.orderNumber,
                lineItems,
                input.customerEmail,
                successUrl,
                baseUrl,
                shippingRequired
            );

            // Update order with session ID
            order.notes = String.format("[%s] Checkout session created: %s\n",
                java.time.Instant.now(), sessionDetails.get("sessionId"));
            order.persist();

            // Return response
            CheckoutSessionResponse response = new CheckoutSessionResponse();
            response.clientSecret = sessionDetails.get("clientSecret");
            response.sessionId = sessionDetails.get("sessionId");
            response.orderId = order.id.toString();
            response.orderNumber = order.orderNumber;

            LOG.infof("Created checkout session %s for order %s", response.sessionId, order.id);

            return response;

        } catch (StripeException e) {
            LOG.errorf(e, "Failed to create checkout session");
            throw new RuntimeException("Failed to create checkout session: " + e.getMessage());
        }
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

    /**
     * Response type for createCheckoutSession mutation.
     */
    @Type("CheckoutSessionResponse")
    public static class CheckoutSessionResponse {
        @NonNull
        @Description("Stripe client secret for embedded checkout")
        public String clientSecret;

        @NonNull
        @Description("Stripe checkout session ID")
        public String sessionId;

        @NonNull
        @Description("Order ID")
        public String orderId;

        @Description("Order number for display")
        public String orderNumber;
    }

    // ============================================================================
    // INPUT TYPES
    // ============================================================================

    /**
     * Input for createCheckoutSession mutation.
     */
    @Input("CheckoutSessionInput")
    public static class CheckoutSessionInput {
        @Description("Cart ID (optional)")
        public String cartId;

        @Description("Customer email for receipts")
        public String customerEmail;

        @Description("Items to checkout")
        public List<CheckoutItemInput> items;

        @Description("Return URL after checkout completes")
        public String returnUrl;
    }

    /**
     * Item input for checkout session.
     */
    @Input("CheckoutItemInput")
    public static class CheckoutItemInput {
        @NonNull
        @Description("Item name")
        public String name;

        @Description("Item description")
        public String description;

        @NonNull
        @Description("Quantity")
        public Integer quantity;

        @NonNull
        @Description("Unit price in dollars")
        public Double unitPrice;

        @Description("Year for calendar items")
        public Integer year;

        @Description("Product code (e.g., 'print', 'pdf')")
        public String productCode;

        @Description("Template/calendar ID")
        public String templateId;

        @Description("Configuration JSON")
        public String configuration;
    }
}
