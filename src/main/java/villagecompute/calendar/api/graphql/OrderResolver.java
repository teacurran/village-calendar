package villagecompute.calendar.api.graphql;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;
import org.eclipse.microprofile.graphql.Source;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;

import villagecompute.calendar.api.graphql.inputs.PlaceOrderInput;
import villagecompute.calendar.api.types.PaymentIntentResponse;
import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.integration.stripe.StripeService;
import villagecompute.calendar.services.AuthenticationService;
import villagecompute.calendar.services.OrderService;
import villagecompute.calendar.util.Roles;

/**
 * GraphQL resolver for order operations. Handles order queries, order placement, and order
 * cancellation.
 */
@GraphQLApi
@ApplicationScoped
public class OrderResolver {

    private static final Logger LOG = Logger.getLogger(OrderResolver.class);

    // Product pricing configuration
    private static final BigDecimal STANDARD_CALENDAR_PRICE = new BigDecimal("29.99");

    @Inject JsonWebToken jwt;

    @Inject AuthenticationService authService;

    @Inject OrderService orderService;

    @Inject StripeService stripeService;

    @Inject ObjectMapper objectMapper;

    @ConfigProperty(
            name = "stripe.checkout.success.url",
            defaultValue = "http://localhost:3000/checkout/success")
    String checkoutSuccessUrl;

    @ConfigProperty(
            name = "stripe.checkout.cancel.url",
            defaultValue = "http://localhost:3000/checkout/cancel")
    String checkoutCancelUrl;

    // ==================================================================
    // QUERIES
    // ==================================================================

    /**
     * Get a single order by ID. Returns order if user owns it or user is admin.
     *
     * @param orderId Order ID
     * @return Order if found and authorized
     */
    @Query("order")
    @Description("Get a single order by ID. Returns order if user owns it (or user is admin).")
    @RolesAllowed({Roles.USER, Roles.ADMIN})
    public CalendarOrder order(@Name("id") @Description("Order ID") @NotNull final UUID orderId) {
        LOG.infof("Query: order(id=%s)", orderId);

        // Get current user
        Optional<CalendarUser> userOpt = authService.getCurrentUser(jwt);
        if (userOpt.isEmpty()) {
            LOG.error("User not found despite passing @RolesAllowed check");
            throw new SecurityException("Unauthorized: User not found");
        }

        CalendarUser currentUser = userOpt.get();

        // Fetch the order
        Optional<CalendarOrder> orderOpt = orderService.getOrderById(orderId);
        if (orderOpt.isEmpty()) {
            LOG.errorf("Order not found: %s", orderId);
            throw new IllegalArgumentException("Order not found");
        }

        CalendarOrder order = orderOpt.get();

        // Authorization check: user must own the order or be an admin
        boolean isAdmin = currentUser.isAdmin != null && currentUser.isAdmin;
        if (!isAdmin && !order.user.id.equals(currentUser.id)) {
            LOG.errorf(
                    "User %s attempted to access order %s owned by user %s",
                    currentUser.id, orderId, order.user.id);
            throw new SecurityException("Unauthorized: Cannot view order owned by another user");
        }

        LOG.infof("Found order %s with status %s", order.id, order.status);

        return order;
    }

    /**
     * Get orders for a specific user (admin only) with optional status filter. If userId is not
     * provided, returns orders for authenticated user.
     *
     * @param userId User ID to fetch orders for (admin only)
     * @param status Filter by order status (optional)
     * @return List of orders
     */
    @Query("orders")
    @Description("Get orders for a user. Admin can query any user, non-admin gets own orders.")
    @RolesAllowed({Roles.USER, Roles.ADMIN})
    public List<CalendarOrder> orders(
            @Name("userId") @Description("User ID to fetch orders for (admin only)")
                    final UUID userId,
            @Name("status") @Description("Filter by order status (optional)") final String status) {
        LOG.infof("Query: orders(userId=%s, status=%s)", userId, status);

        // Get current user
        Optional<CalendarUser> userOpt = authService.getCurrentUser(jwt);
        if (userOpt.isEmpty()) {
            LOG.error("User not found despite passing @RolesAllowed check");
            throw new SecurityException("Unauthorized: User not found");
        }

        CalendarUser currentUser = userOpt.get();
        boolean isAdmin = currentUser.isAdmin != null && currentUser.isAdmin;

        // Determine target user ID
        UUID targetUserId;
        if (userId != null && !userId.equals(currentUser.id)) {
            // Querying another user's orders - requires admin role
            if (!isAdmin) {
                LOG.errorf(
                        "User %s attempted to query orders for user %s without admin role",
                        currentUser.id, userId);
                throw new SecurityException("Cannot view orders for another user");
            }
            targetUserId = userId;
            LOG.infof("Admin %s querying orders for user %s", currentUser.id, userId);
        } else {
            // Query own orders
            targetUserId = userId != null ? userId : currentUser.id;
        }

        // Fetch orders
        List<CalendarOrder> orders = orderService.getUserOrders(targetUserId);

        // Filter by status if provided
        if (status != null && !status.isBlank()) {
            orders =
                    orders.stream()
                            .filter(o -> o.status.equals(status))
                            .collect(Collectors.toList());
        }

        LOG.infof("Found %d orders for user %s", orders.size(), targetUserId);

        return orders;
    }

    /**
     * Get orders for the authenticated user.
     *
     * @param status Filter by order status (optional)
     * @return List of orders
     */
    @Query("myOrders")
    @Description("Get orders for the authenticated user. Requires authentication.")
    @RolesAllowed({Roles.USER, Roles.ADMIN})
    public List<CalendarOrder> myOrders(
            @Name("status") @Description("Filter by order status (optional)") final String status) {
        LOG.infof("Query: myOrders(status=%s)", status);

        // Get current user
        Optional<CalendarUser> userOpt = authService.getCurrentUser(jwt);
        if (userOpt.isEmpty()) {
            LOG.error("User not found despite passing @RolesAllowed check");
            throw new SecurityException("Unauthorized: User not found");
        }

        CalendarUser currentUser = userOpt.get();

        // Fetch orders
        List<CalendarOrder> orders = orderService.getUserOrders(currentUser.id);

        // Filter by status if provided
        if (status != null && !status.isBlank()) {
            orders =
                    orders.stream()
                            .filter(o -> o.status.equals(status))
                            .collect(Collectors.toList());
        }

        LOG.infof("Found %d orders for current user %s", orders.size(), currentUser.id);

        return orders;
    }

    /**
     * Get all orders across all users (admin only).
     *
     * @param status Filter by order status (optional)
     * @param limit Maximum number of orders to return
     * @return List of orders
     */
    @Query("allOrders")
    @Description("Get all orders across all users (admin only). Requires ADMIN role in JWT claims.")
    @RolesAllowed(Roles.ADMIN)
    public List<CalendarOrder> allOrders(
            @Name("status") @Description("Filter by order status (optional)") final String status,
            @Name("limit") @Description("Maximum number of orders to return") final Integer limit) {
        LOG.infof("Query: allOrders(status=%s, limit=%d)", status, limit);

        List<CalendarOrder> orders;

        // Fetch orders by status or all orders
        if (status != null && !status.isBlank()) {
            orders = orderService.getOrdersByStatus(status);
        } else {
            orders = CalendarOrder.listAll();
        }

        // Apply limit
        int maxResults = limit != null && limit > 0 ? limit : 50;
        if (orders.size() > maxResults) {
            orders = orders.subList(0, maxResults);
        }

        LOG.infof("Found %d orders (limited to %d)", orders.size(), maxResults);

        return orders;
    }

    // ==================================================================
    // MUTATIONS
    // ==================================================================

    /**
     * Place an order for printed calendars. Creates order in PENDING status and returns Stripe
     * Checkout Session for payment. The order status is updated to PAID after successful payment
     * via webhook.
     *
     * @param input Order placement input
     * @return PaymentIntent with checkout URL
     */
    @Mutation("placeOrder")
    @Description(
            "Place an order for printed calendars. "
                    + "Requires authentication. Creates Stripe Checkout Session for payment.")
    @RolesAllowed({Roles.USER, Roles.ADMIN})
    @Transactional
    public PaymentIntentResponse placeOrder(
            @Name("input") @Description("Order details") @NotNull final PlaceOrderInput input) {
        LOG.infof(
                "Mutation: placeOrder(calendarId=%s, productType=%s, quantity=%d)",
                input.calendarId, input.productType, input.quantity);

        // Get current user
        Optional<CalendarUser> userOpt = authService.getCurrentUser(jwt);
        if (userOpt.isEmpty()) {
            LOG.error("User not found despite passing @RolesAllowed check");
            throw new SecurityException("Unauthorized: User not found");
        }

        CalendarUser currentUser = userOpt.get();

        // Parse calendar ID
        UUID calendarId;
        try {
            calendarId = UUID.fromString(input.calendarId);
        } catch (IllegalArgumentException e) {
            LOG.errorf("Invalid calendar ID format: %s", input.calendarId);
            throw new IllegalArgumentException("Invalid calendar ID format");
        }

        // Validate calendar exists and user owns it
        Optional<UserCalendar> calendarOpt = UserCalendar.findByIdOptional(calendarId);
        if (calendarOpt.isEmpty()) {
            LOG.errorf("Calendar not found: %s", input.calendarId);
            throw new IllegalArgumentException("Calendar not found");
        }

        UserCalendar calendar = calendarOpt.get();

        // Authorization check: user must own the calendar
        if (calendar.user == null || !calendar.user.id.equals(currentUser.id)) {
            LOG.errorf(
                    "User %s attempted to order calendar %s owned by user %s",
                    currentUser.id,
                    input.calendarId,
                    calendar.user != null ? calendar.user.id : "null");
            throw new SecurityException("Cannot order calendar owned by another user");
        }

        // Validate quantity
        if (input.quantity < 1) {
            LOG.errorf("Invalid quantity: %d", input.quantity);
            throw new IllegalArgumentException("Quantity must be at least 1");
        }

        // Convert shipping address to JsonNode
        JsonNode shippingAddressJson;
        try {
            ObjectNode addressNode = objectMapper.createObjectNode();
            addressNode.put("street", input.shippingAddress.street);
            if (input.shippingAddress.street2 != null) {
                addressNode.put("street2", input.shippingAddress.street2);
            }
            addressNode.put("city", input.shippingAddress.city);
            addressNode.put("state", input.shippingAddress.state);
            addressNode.put("postalCode", input.shippingAddress.postalCode);
            addressNode.put("country", input.shippingAddress.country);
            shippingAddressJson = addressNode;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to convert shipping address to JSON");
            throw new IllegalArgumentException("Invalid shipping address format");
        }

        // Determine unit price based on product type
        BigDecimal unitPrice = STANDARD_CALENDAR_PRICE;
        // TODO: Add different pricing for DESK_CALENDAR, POSTER, etc. when implemented

        // Create order in PENDING status
        CalendarOrder order =
                orderService.createOrder(
                        currentUser, calendar, input.quantity, unitPrice, shippingAddressJson);

        LOG.infof("Created order %s with total price $%.2f", order.id, order.totalPrice);

        // Create Stripe Checkout Session
        Session checkoutSession;
        try {
            checkoutSession =
                    stripeService.createCheckoutSession(
                            order,
                            checkoutSuccessUrl + "?session_id={CHECKOUT_SESSION_ID}",
                            checkoutCancelUrl);
        } catch (StripeException e) {
            LOG.errorf(e, "Failed to create Stripe Checkout Session for order %s", order.id);
            throw new RuntimeException("Failed to create payment session: " + e.getMessage());
        }

        // Store Stripe session ID in order for tracking
        // Note: We don't have a field for this in the current schema, but we can add it to notes
        String sessionNote =
                String.format("Stripe Checkout Session ID: %s%n", checkoutSession.getId());
        order.notes = order.notes == null ? sessionNote : sessionNote + order.notes;
        order.persist();

        LOG.infof(
                "Created Stripe Checkout Session %s for order %s. Checkout URL: %s",
                checkoutSession.getId(), order.id, checkoutSession.getUrl());

        // Build PaymentIntent response
        Integer amountInCents = order.totalPrice.multiply(BigDecimal.valueOf(100)).intValue();
        PaymentIntentResponse response =
                PaymentIntentResponse.fromCheckoutSession(
                        checkoutSession.getId(),
                        checkoutSession.getUrl(),
                        amountInCents,
                        calendar.id,
                        input.quantity);

        LOG.infof("Returning PaymentIntent for order %s", order.id);

        return response;
    }

    /**
     * Cancel an order and initiate refund. Can only cancel orders in PENDING or PAID status.
     *
     * @param orderId Order ID to cancel
     * @param reason Reason for cancellation (optional)
     * @return Cancelled order
     */
    @Mutation("cancelOrder")
    @Description(
            "Cancel an order and initiate refund. "
                    + "Requires authentication and order ownership (or admin role). "
                    + "Can only cancel orders in PENDING or PAID status.")
    @RolesAllowed({Roles.USER, Roles.ADMIN})
    @Transactional
    public CalendarOrder cancelOrder(
            @Name("orderId") @Description("Order ID to cancel") @NotNull final UUID orderId,
            @Name("reason") @Description("Reason for cancellation (optional, stored in notes)")
                    final String reason) {
        LOG.infof("Mutation: cancelOrder(orderId=%s, reason=%s)", orderId, reason);

        // Get current user
        Optional<CalendarUser> userOpt = authService.getCurrentUser(jwt);
        if (userOpt.isEmpty()) {
            LOG.error("User not found despite passing @RolesAllowed check");
            throw new SecurityException("Unauthorized: User not found");
        }

        CalendarUser currentUser = userOpt.get();
        boolean isAdmin = currentUser.isAdmin != null && currentUser.isAdmin;

        // Cancel the order via OrderService (handles authorization and validation)
        CalendarOrder cancelledOrder =
                orderService.cancelOrder(
                        orderId,
                        currentUser.id,
                        isAdmin,
                        reason != null ? reason : "No reason provided");

        LOG.infof("Cancelled order %s. Status: %s", cancelledOrder.id, cancelledOrder.status);

        return cancelledOrder;
    }

    // ==================================================================
    // BATCHED FIELD RESOLVERS (DataLoader Pattern)
    // ==================================================================

    /**
     * Batched field resolver for CalendarOrder.calendar relationship. Prevents N+1 queries by
     * batch-loading calendars for multiple orders. SmallRye GraphQL automatically batches field
     * resolvers annotated with @Source when the parameter is a List.
     *
     * @param orders List of orders to resolve calendars for
     * @return List of calendars in the same order as input orders
     */
    @Name("calendar")
    @Description("Get the calendar associated with this order")
    public List<UserCalendar> batchLoadCalendars(@Source final List<CalendarOrder> orders) {
        LOG.debugf("Batch loading calendars for %d orders", orders.size());

        // Extract unique calendar IDs from orders
        List<UUID> calendarIds =
                orders.stream()
                        .map(o -> o.calendar != null ? o.calendar.id : null)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList());

        if (calendarIds.isEmpty()) {
            LOG.debug("No calendar IDs to load");
            return orders.stream().map(o -> (UserCalendar) null).collect(Collectors.toList());
        }

        // Batch load calendars in a single query
        List<UserCalendar> calendars = UserCalendar.list("id in ?1", calendarIds);
        LOG.debugf("Loaded %d calendars in batch", calendars.size());

        // Create lookup map for O(1) access
        Map<UUID, UserCalendar> calendarMap =
                calendars.stream().collect(Collectors.toMap(c -> c.id, c -> c));

        // Return calendars in same order as input orders (DataLoader contract)
        List<UserCalendar> result =
                orders.stream()
                        .map(o -> o.calendar != null ? calendarMap.get(o.calendar.id) : null)
                        .collect(Collectors.toList());

        LOG.debugf("Returning %d calendars for %d orders", result.size(), orders.size());
        return result;
    }

    /**
     * Batched field resolver for CalendarOrder.user relationship. Prevents N+1 queries by
     * batch-loading users for multiple orders. SmallRye GraphQL automatically batches field
     * resolvers annotated with @Source when the parameter is a List.
     *
     * @param orders List of orders to resolve users for
     * @return List of users in the same order as input orders
     */
    @Name("user")
    @Description("Get the user who placed this order")
    public List<CalendarUser> batchLoadUsers(@Source final List<CalendarOrder> orders) {
        LOG.debugf("Batch loading users for %d orders", orders.size());

        // Extract unique user IDs from orders
        List<UUID> userIds =
                orders.stream()
                        .map(o -> o.user != null ? o.user.id : null)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList());

        if (userIds.isEmpty()) {
            LOG.debug("No user IDs to load");
            return orders.stream().map(o -> (CalendarUser) null).collect(Collectors.toList());
        }

        // Batch load users in a single query
        List<CalendarUser> users = CalendarUser.list("id in ?1", userIds);
        LOG.debugf("Loaded %d users in batch", users.size());

        // Create lookup map for O(1) access
        Map<UUID, CalendarUser> userMap =
                users.stream().collect(Collectors.toMap(u -> u.id, u -> u));

        // Return users in same order as input orders (DataLoader contract)
        List<CalendarUser> result =
                orders.stream()
                        .map(o -> o.user != null ? userMap.get(o.user.id) : null)
                        .collect(Collectors.toList());

        LOG.debugf("Returning %d users for %d orders", result.size(), orders.size());
        return result;
    }
}
