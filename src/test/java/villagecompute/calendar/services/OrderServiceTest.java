package villagecompute.calendar.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OrderService.
 * Tests order creation, status updates, and lifecycle transitions.
 */
@QuarkusTest
class OrderServiceTest {

    @Inject
    OrderService orderService;

    @Inject
    ObjectMapper objectMapper;

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

        // Create test shipping address
        testShippingAddress = objectMapper.createObjectNode();
        testShippingAddress.put("name", "Test User");
        testShippingAddress.put("line1", "123 Test St");
        testShippingAddress.put("city", "Test City");
        testShippingAddress.put("state", "TC");
        testShippingAddress.put("postalCode", "12345");
        testShippingAddress.put("country", "USA");
    }

    @Test
    @Transactional
    void testCreateOrder() {
        // Given
        Integer quantity = 2;
        BigDecimal unitPrice = new BigDecimal("29.99");

        // When
        CalendarOrder order = orderService.createOrder(
            testUser,
            testCalendar,
            quantity,
            unitPrice,
            testShippingAddress
        );

        // Then
        assertNotNull(order);
        assertNotNull(order.id);
        assertEquals(testUser.id, order.user.id);
        assertEquals(testCalendar.id, order.calendar.id);
        assertEquals(quantity, order.quantity);
        assertEquals(unitPrice, order.unitPrice);
        assertEquals(new BigDecimal("59.98"), order.totalPrice);
        assertEquals(CalendarOrder.STATUS_PENDING, order.status);
        assertNotNull(order.shippingAddress);
        assertNull(order.paidAt);
    }

    @Test
    @Transactional
    void testCreateOrderWithInvalidQuantity() {
        // Given
        Integer invalidQuantity = 0;
        BigDecimal unitPrice = new BigDecimal("29.99");

        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
            orderService.createOrder(
                testUser,
                testCalendar,
                invalidQuantity,
                unitPrice,
                testShippingAddress
            )
        );
    }

    @Test
    @Transactional
    void testCreateOrderWithInvalidPrice() {
        // Given
        Integer quantity = 1;
        BigDecimal invalidPrice = new BigDecimal("-10.00");

        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
            orderService.createOrder(
                testUser,
                testCalendar,
                quantity,
                invalidPrice,
                testShippingAddress
            )
        );
    }

    @Test
    @Transactional
    void testUpdateOrderStatus() {
        // Given
        CalendarOrder order = createTestOrder();
        String notes = "Payment confirmed via Stripe";

        // When
        CalendarOrder updatedOrder = orderService.updateOrderStatus(
            order.id,
            CalendarOrder.STATUS_PAID,
            notes
        );

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
        CalendarOrder shippedOrder = orderService.updateOrderStatus(
            order.id,
            CalendarOrder.STATUS_SHIPPED,
            "Shipped via USPS"
        );

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
        assertThrows(IllegalStateException.class, () ->
            orderService.updateOrderStatus(order.id, CalendarOrder.STATUS_PROCESSING, "Cannot update")
        );
    }

    @Test
    @Transactional
    void testInvalidStatusTransition() {
        // Given
        CalendarOrder order = createTestOrder();

        // When & Then - Cannot go from PENDING to SHIPPED directly
        assertThrows(IllegalStateException.class, () ->
            orderService.updateOrderStatus(order.id, CalendarOrder.STATUS_SHIPPED, "Invalid transition")
        );
    }

    @Test
    @Transactional
    void testGetOrdersByStatus() {
        // Given
        createTestOrder(); // PENDING
        CalendarOrder paidOrder = createTestOrder();
        orderService.updateOrderStatus(paidOrder.id, CalendarOrder.STATUS_PAID, "Paid");

        // When
        List<CalendarOrder> pendingOrders = orderService.getOrdersByStatus(CalendarOrder.STATUS_PENDING);
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
        Optional<CalendarOrder> foundOrder = orderService.findByStripePaymentIntent(paymentIntentId);

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

        orderService.updateOrderStatus(order.id, CalendarOrder.STATUS_PROCESSING, "Order processing");
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
        orderService.updateOrderStatus(order.id, CalendarOrder.STATUS_CANCELLED, "Customer requested cancellation");

        // Then
        order = CalendarOrder.findById(order.id);
        assertEquals(CalendarOrder.STATUS_CANCELLED, order.status);
        assertTrue(order.isTerminal());
    }

    // Helper method to create a test order
    private CalendarOrder createTestOrder() {
        return orderService.createOrder(
            testUser,
            testCalendar,
            1,
            new BigDecimal("29.99"),
            testShippingAddress
        );
    }
}
