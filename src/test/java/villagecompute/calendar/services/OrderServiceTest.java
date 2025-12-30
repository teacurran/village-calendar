package villagecompute.calendar.services;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;

import io.quarkus.test.junit.QuarkusTest;

/** Unit tests for OrderService. Tests order creation, status updates, and lifecycle transitions. */
@QuarkusTest
class OrderServiceTest {

    @Inject OrderService orderService;

    @Inject ObjectMapper objectMapper;

    private CalendarUser testUser;
    private UserCalendar testCalendar;
    private ObjectNode testShippingAddress;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up test data
        CalendarOrder.deleteAll();
        UserCalendar.deleteAll();
        CalendarUser.deleteAll();
        CalendarTemplate.deleteAll();

        // Create test user
        testUser = new CalendarUser();
        testUser.oauthProvider = "GOOGLE";
        testUser.oauthSubject = "test-oauth-subject";
        testUser.email = "test@example.com";
        testUser.displayName = "Test User";
        testUser.persist();

        // Create test template
        CalendarTemplate template = new CalendarTemplate();
        template.name = "Test Template";
        template.description = "Test template for unit tests";
        template.isActive = true;
        template.configuration = objectMapper.createObjectNode();
        template.persist();

        // Create test calendar
        testCalendar = new UserCalendar();
        testCalendar.user = testUser;
        testCalendar.name = "Test Calendar 2025";
        testCalendar.year = 2025;
        testCalendar.template = template;
        testCalendar.isPublic = false;
        testCalendar.persist();

        // Create test shipping address (using ISO 3166-1 alpha-2 country code)
        testShippingAddress = objectMapper.createObjectNode();
        testShippingAddress.put("street", "123 Test St");
        testShippingAddress.put("city", "Test City");
        testShippingAddress.put("state", "CA");
        testShippingAddress.put("postalCode", "12345");
        testShippingAddress.put("country", "US");
    }

    @Test
    @Transactional
    void testCreateOrder() {
        // Given
        Integer quantity = 2;
        BigDecimal unitPrice = new BigDecimal("29.99");

        // When
        CalendarOrder order =
                orderService.createOrder(
                        testUser, testCalendar, quantity, unitPrice, testShippingAddress);

        // Then
        assertNotNull(order);
        assertNotNull(order.id);
        assertEquals(testUser.id, order.user.id);
        assertEquals(testCalendar.id, order.calendar.id);
        assertEquals(quantity, order.quantity);
        assertEquals(unitPrice, order.unitPrice);
        // Total: (2 × $29.99) + $5.99 shipping + $0 tax = $65.97
        assertEquals(new BigDecimal("65.97"), order.totalPrice);
        assertEquals(CalendarOrder.STATUS_PENDING, order.status);
        assertNotNull(order.shippingAddress);
        assertNull(order.paidAt);
        assertNotNull(order.orderNumber, "Order number should be generated");
        assertTrue(
                order.orderNumber.matches("VC-[23456789A-HJ-NP-Z]{4}-[23456789A-HJ-NP-Z]{4}"),
                "Order number should match format VC-XXXX-XXXX");
    }

    @Test
    @Transactional
    void testCreateOrderWithInvalidQuantity() {
        // Given
        Integer invalidQuantity = 0;
        BigDecimal unitPrice = new BigDecimal("29.99");

        // When & Then
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        orderService.createOrder(
                                testUser,
                                testCalendar,
                                invalidQuantity,
                                unitPrice,
                                testShippingAddress));
    }

    @Test
    @Transactional
    void testCreateOrderWithInvalidPrice() {
        // Given
        Integer quantity = 1;
        BigDecimal invalidPrice = new BigDecimal("-10.00");

        // When & Then
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        orderService.createOrder(
                                testUser,
                                testCalendar,
                                quantity,
                                invalidPrice,
                                testShippingAddress));
    }

    @Test
    @Transactional
    void testUpdateOrderStatus() {
        // Given
        CalendarOrder order = createTestOrder();
        String notes = "Payment confirmed via Stripe";

        // When
        CalendarOrder updatedOrder =
                orderService.updateOrderStatus(order.id, CalendarOrder.STATUS_PAID, notes);

        // Then
        assertNotNull(updatedOrder);
        assertEquals(CalendarOrder.STATUS_PAID, updatedOrder.status);
        assertNotNull(updatedOrder.paidAt);
        assertNotNull(updatedOrder.notes);
        assertTrue(updatedOrder.notes.contains(notes));
    }

    @Test
    @Transactional
    void testUpdateOrderStatusToShipped() {
        // Given
        CalendarOrder order = createTestOrder();
        orderService.updateOrderStatus(order.id, CalendarOrder.STATUS_PAID, "Payment confirmed");

        // When
        CalendarOrder shippedOrder =
                orderService.updateOrderStatus(
                        order.id, CalendarOrder.STATUS_SHIPPED, "Shipped via USPS");

        // Then
        assertEquals(CalendarOrder.STATUS_SHIPPED, shippedOrder.status);
        assertNotNull(shippedOrder.shippedAt);
        assertNotNull(shippedOrder.paidAt);
    }

    @Test
    @Transactional
    void testUpdateTerminalOrderThrowsException() {
        // Given
        CalendarOrder order = createTestOrder();
        orderService.updateOrderStatus(order.id, CalendarOrder.STATUS_PAID, "Payment confirmed");
        orderService.updateOrderStatus(order.id, CalendarOrder.STATUS_SHIPPED, "Shipped");
        orderService.updateOrderStatus(order.id, CalendarOrder.STATUS_DELIVERED, "Delivered");

        // When & Then
        assertThrows(
                IllegalStateException.class,
                () ->
                        orderService.updateOrderStatus(
                                order.id, CalendarOrder.STATUS_PROCESSING, "Cannot update"));
    }

    @Test
    @Transactional
    void testInvalidStatusTransition() {
        // Given
        CalendarOrder order = createTestOrder();

        // When & Then - Cannot go from PENDING to SHIPPED directly
        assertThrows(
                IllegalStateException.class,
                () ->
                        orderService.updateOrderStatus(
                                order.id, CalendarOrder.STATUS_SHIPPED, "Invalid transition"));
    }

    @Test
    @Transactional
    void testGetOrdersByStatus() {
        // Given
        createTestOrder(); // PENDING
        CalendarOrder paidOrder = createTestOrder();
        orderService.updateOrderStatus(paidOrder.id, CalendarOrder.STATUS_PAID, "Paid");

        // When
        List<CalendarOrder> pendingOrders =
                orderService.getOrdersByStatus(CalendarOrder.STATUS_PENDING);
        List<CalendarOrder> paidOrders = orderService.getOrdersByStatus(CalendarOrder.STATUS_PAID);

        // Then
        assertEquals(1, pendingOrders.size());
        assertEquals(1, paidOrders.size());
        assertEquals(CalendarOrder.STATUS_PENDING, pendingOrders.get(0).status);
        assertEquals(CalendarOrder.STATUS_PAID, paidOrders.get(0).status);
    }

    @Test
    @Transactional
    void testGetUserOrders() {
        // Given
        CalendarOrder order1 = createTestOrder();
        CalendarOrder order2 = createTestOrder();

        // When
        List<CalendarOrder> userOrders = orderService.getUserOrders(testUser.id);

        // Then
        assertEquals(2, userOrders.size());
        assertTrue(userOrders.stream().allMatch(o -> o.user.id.equals(testUser.id)));
    }

    @Test
    @Transactional
    void testGetOrderById() {
        // Given
        CalendarOrder order = createTestOrder();

        // When
        Optional<CalendarOrder> foundOrder = orderService.getOrderById(order.id);

        // Then
        assertTrue(foundOrder.isPresent());
        assertEquals(order.id, foundOrder.get().id);
    }

    @Test
    @Transactional
    void testGetOrderByIdNotFound() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When
        Optional<CalendarOrder> foundOrder = orderService.getOrderById(nonExistentId);

        // Then
        assertFalse(foundOrder.isPresent());
    }

    @Test
    @Transactional
    void testFindByStripePaymentIntent() {
        // Given
        CalendarOrder order = createTestOrder();
        String paymentIntentId = "pi_test_12345";
        order.stripePaymentIntentId = paymentIntentId;
        order.persist();

        // When
        Optional<CalendarOrder> foundOrder =
                orderService.findByStripePaymentIntent(paymentIntentId);

        // Then
        assertTrue(foundOrder.isPresent());
        assertEquals(paymentIntentId, foundOrder.get().stripePaymentIntentId);
    }

    @Test
    @Transactional
    void testOrderStatusTransitions() {
        // Given
        CalendarOrder order = createTestOrder();

        // Test valid transitions: PENDING -> PAID -> PROCESSING -> SHIPPED -> DELIVERED
        orderService.updateOrderStatus(order.id, CalendarOrder.STATUS_PAID, "Payment confirmed");
        order = CalendarOrder.findById(order.id);
        assertEquals(CalendarOrder.STATUS_PAID, order.status);

        orderService.updateOrderStatus(
                order.id, CalendarOrder.STATUS_PROCESSING, "Order processing");
        order = CalendarOrder.findById(order.id);
        assertEquals(CalendarOrder.STATUS_PROCESSING, order.status);

        orderService.updateOrderStatus(order.id, CalendarOrder.STATUS_SHIPPED, "Order shipped");
        order = CalendarOrder.findById(order.id);
        assertEquals(CalendarOrder.STATUS_SHIPPED, order.status);

        orderService.updateOrderStatus(order.id, CalendarOrder.STATUS_DELIVERED, "Order delivered");
        order = CalendarOrder.findById(order.id);
        assertEquals(CalendarOrder.STATUS_DELIVERED, order.status);
        assertTrue(order.isTerminal());
    }

    @Test
    @Transactional
    void testCancelOrder() {
        // Given
        CalendarOrder order = createTestOrder();

        // When - Cancel from PENDING
        orderService.updateOrderStatus(
                order.id, CalendarOrder.STATUS_CANCELLED, "Customer requested cancellation");

        // Then
        order = CalendarOrder.findById(order.id);
        assertEquals(CalendarOrder.STATUS_CANCELLED, order.status);
        assertTrue(order.isTerminal());
    }

    @Test
    @Transactional
    void testOrderNumberGeneration() {
        // Given - Create multiple orders
        CalendarOrder order1 = createTestOrder();
        CalendarOrder order2 = createTestOrder();
        CalendarOrder order3 = createTestOrder();

        // Then - Order numbers should be sequential
        assertNotNull(order1.orderNumber);
        assertNotNull(order2.orderNumber);
        assertNotNull(order3.orderNumber);

        // Verify format
        assertTrue(order1.orderNumber.matches("VC-[23456789A-HJ-NP-Z]{4}-[23456789A-HJ-NP-Z]{4}"));
        assertTrue(order2.orderNumber.matches("VC-[23456789A-HJ-NP-Z]{4}-[23456789A-HJ-NP-Z]{4}"));
        assertTrue(order3.orderNumber.matches("VC-[23456789A-HJ-NP-Z]{4}-[23456789A-HJ-NP-Z]{4}"));

        // Verify uniqueness
        assertNotEquals(order1.orderNumber, order2.orderNumber);
        assertNotEquals(order2.orderNumber, order3.orderNumber);
        assertNotEquals(order1.orderNumber, order3.orderNumber);
    }

    @Test
    @Transactional
    void testOrderNumberIsUnique() {
        // Given
        CalendarOrder order1 = createTestOrder();
        String orderNumber = order1.orderNumber;

        // When - Try to find by order number
        Optional<CalendarOrder> foundOrder =
                CalendarOrder.findByOrderNumber(orderNumber).firstResultOptional();

        // Then
        assertTrue(foundOrder.isPresent());
        assertEquals(order1.id, foundOrder.get().id);
        assertEquals(orderNumber, foundOrder.get().orderNumber);
    }

    @Test
    @Transactional
    void testCancelOrderFromPending() {
        // Given
        CalendarOrder order = createTestOrder();
        String cancellationReason = "Customer changed mind";

        // When
        CalendarOrder cancelledOrder =
                orderService.cancelOrder(order.id, testUser.id, false, cancellationReason);

        // Then
        assertNotNull(cancelledOrder);
        assertEquals(CalendarOrder.STATUS_CANCELLED, cancelledOrder.status);
        assertNotNull(cancelledOrder.notes);
        assertTrue(cancelledOrder.notes.contains(cancellationReason));
        assertTrue(cancelledOrder.notes.contains("Order cancelled from status PENDING"));
        assertTrue(cancelledOrder.isTerminal());
    }

    @Test
    @Transactional
    void testCancelOrderFromPaid() {
        // Given
        CalendarOrder order = createTestOrder();
        order.stripePaymentIntentId = "pi_test_12345";
        orderService.updateOrderStatus(order.id, CalendarOrder.STATUS_PAID, "Payment confirmed");
        order = CalendarOrder.findById(order.id);

        // When
        CalendarOrder cancelledOrder =
                orderService.cancelOrder(order.id, testUser.id, false, "Customer requested refund");

        // Then
        assertEquals(CalendarOrder.STATUS_CANCELLED, cancelledOrder.status);
        assertNotNull(cancelledOrder.notes);
        assertTrue(cancelledOrder.notes.contains("Customer requested refund"));
        assertTrue(cancelledOrder.notes.contains("TODO: Process refund via PaymentService"));
        assertTrue(cancelledOrder.notes.contains("pi_test_12345"));
    }

    @Test
    @Transactional
    void testCancelOrderUnauthorizedUser() {
        // Given
        CalendarOrder order = createTestOrder();
        UUID unauthorizedUserId = UUID.randomUUID();

        // When & Then
        assertThrows(
                SecurityException.class,
                () ->
                        orderService.cancelOrder(
                                order.id,
                                unauthorizedUserId,
                                false, // Not admin
                                "Unauthorized attempt"));
    }

    @Test
    @Transactional
    void testCancelOrderAsAdmin() {
        // Given
        CalendarOrder order = createTestOrder();
        UUID adminUserId = UUID.randomUUID(); // Different user

        // When - Admin can cancel any order
        CalendarOrder cancelledOrder =
                orderService.cancelOrder(
                        order.id,
                        adminUserId,
                        true, // Is admin
                        "Admin cancellation");

        // Then
        assertEquals(CalendarOrder.STATUS_CANCELLED, cancelledOrder.status);
    }

    @Test
    @Transactional
    void testCancelTerminalOrderThrowsException() {
        // Given
        CalendarOrder order = createTestOrder();
        orderService.updateOrderStatus(order.id, CalendarOrder.STATUS_PAID, "Paid");
        orderService.updateOrderStatus(order.id, CalendarOrder.STATUS_SHIPPED, "Shipped");
        orderService.updateOrderStatus(order.id, CalendarOrder.STATUS_DELIVERED, "Delivered");

        // When & Then - Cannot cancel delivered order
        assertThrows(
                IllegalStateException.class,
                () ->
                        orderService.cancelOrder(
                                order.id, testUser.id, false, "Cannot cancel delivered order"));
    }

    @Test
    @Transactional
    void testCancelAlreadyCancelledOrder() {
        // Given
        CalendarOrder order = createTestOrder();
        orderService.cancelOrder(order.id, testUser.id, false, "First cancellation");

        // When & Then - Cannot cancel already cancelled order
        assertThrows(
                IllegalStateException.class,
                () ->
                        orderService.cancelOrder(
                                order.id, testUser.id, false, "Second cancellation"));
    }

    @Test
    @Transactional
    void testCancelNonExistentOrder() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When & Then
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        orderService.cancelOrder(
                                nonExistentId, testUser.id, false, "Cancel non-existent"));
    }

    @Test
    @Transactional
    void testCancelOrderFromProcessing() {
        // Given
        CalendarOrder order = createTestOrder();
        orderService.updateOrderStatus(order.id, CalendarOrder.STATUS_PAID, "Payment confirmed");
        orderService.updateOrderStatus(
                order.id, CalendarOrder.STATUS_PROCESSING, "Order processing");

        // When
        CalendarOrder cancelledOrder =
                orderService.cancelOrder(order.id, testUser.id, false, "Cancel during processing");

        // Then
        assertEquals(CalendarOrder.STATUS_CANCELLED, cancelledOrder.status);
        assertTrue(cancelledOrder.notes.contains("Order cancelled from status PROCESSING"));
    }

    @Test
    @Transactional
    void testOrderTotalCalculation() {
        // Given - Tax is zero (placeholder), shipping is $5.99
        Integer quantity = 3;
        BigDecimal unitPrice = new BigDecimal("25.00");

        // When
        CalendarOrder order =
                orderService.createOrder(
                        testUser, testCalendar, quantity, unitPrice, testShippingAddress);

        // Then - Total should be subtotal + tax (0) + shipping ($5.99)
        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity)); // $75.00
        BigDecimal expectedTotal = subtotal.add(new BigDecimal("5.99")); // $80.99
        assertEquals(expectedTotal, order.totalPrice);
        assertEquals(new BigDecimal("80.99"), order.totalPrice);
    }

    @Test
    @Transactional
    void testMultipleOrdersHaveUniqueOrderNumbers() {
        // Given - Create 10 orders
        List<CalendarOrder> orders = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            orders.add(createTestOrder());
        }

        // Then - All order numbers should be unique
        java.util.Set<String> orderNumbers =
                orders.stream()
                        .map(o -> o.orderNumber)
                        .collect(java.util.stream.Collectors.toSet());

        assertEquals(10, orderNumbers.size(), "All 10 order numbers should be unique");

        // Verify all match the format
        orders.forEach(
                order -> {
                    assertNotNull(order.orderNumber);
                    assertTrue(
                            order.orderNumber.matches(
                                    "VC-[23456789A-HJ-NP-Z]{4}-[23456789A-HJ-NP-Z]{4}"));
                });
    }

    // Helper method to create a test order
    private CalendarOrder createTestOrder() {
        return orderService.createOrder(
                testUser, testCalendar, 1, new BigDecimal("29.99"), testShippingAddress);
    }
}
