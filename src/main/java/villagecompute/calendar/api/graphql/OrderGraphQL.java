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
     *
     * @return List of user's orders
     */
    @Query("myOrders")
    @Description("Get orders for the authenticated user. Requires authentication.")
    @RolesAllowed("USER")
    public List<CalendarOrder> myOrders() {
        LOG.debug("Query: myOrders()");

        // Get current user from JWT
        Optional<CalendarUser> currentUser = authService.getCurrentUser(jwt);
        if (currentUser.isEmpty()) {
            LOG.error("User not found despite passing @RolesAllowed check");
            throw new IllegalStateException("Unauthorized: User not found");
        }

        CalendarUser user = currentUser.get();
        List<CalendarOrder> orders = orderService.getUserOrders(user.id);

        LOG.infof("Found %d orders for user %s", orders.size(), user.email);
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
     * Get orders by status (admin only).
     *
     * @param status Order status
     * @return List of orders with the specified status
     */
    @Query("ordersByStatus")
    @Description("Get orders by status (admin only).")
    @RolesAllowed("ADMIN")
    public List<CalendarOrder> ordersByStatus(
        @Name("status")
        @Description("Order status (PENDING, PAID, PROCESSING, SHIPPED, DELIVERED, CANCELLED)")
        @NonNull
        String status
    ) {
        LOG.infof("Query: ordersByStatus(status=%s)", status);
        return orderService.getOrdersByStatus(status);
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

        // Create Stripe PaymentIntent
        try {
            Map<String, String> paymentDetails = paymentService.createPaymentIntent(
                order.totalPrice,
                "usd",
                order.id.toString()
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
