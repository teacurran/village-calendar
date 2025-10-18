package villagecompute.calendar.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;
import villagecompute.calendar.data.models.*;
import villagecompute.calendar.services.OrderService;
import villagecompute.calendar.services.PaymentService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration tests for admin order management workflows.
 * Tests admin-only operations like updating order status, querying all orders, and managing fulfillment.
 *
 * This test suite validates:
 * 1. Admin updating order status (PAID → PROCESSING → SHIPPED → DELIVERED)
 * 2. Admin adding tracking numbers when shipping orders
 * 3. Email job enqueueing on order status changes (shipping notifications)
 * 4. Admin querying all orders across users
 * 5. Authorization checks (only admin can update orders, users can cancel own orders)
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminOrderWorkflowTest {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    OrderService orderService;

    @Inject
    EntityManager entityManager;

    @InjectMock
    PaymentService paymentService;

    private CalendarUser testUser;
    private CalendarUser testUser2;
    private CalendarUser adminUser;
    private CalendarTemplate testTemplate;
    private UserCalendar testCalendar;
    private UserCalendar testCalendar2;

    @BeforeEach
    @Transactional
    void setup() throws Exception {
        // Create test template
        testTemplate = new CalendarTemplate();
        testTemplate.name = "Admin Test Template " + System.currentTimeMillis();
        testTemplate.description = "Template for admin testing";
        testTemplate.isActive = true;
        testTemplate.isFeatured = false;
        testTemplate.displayOrder = 1;
        testTemplate.configuration = objectMapper.readTree("""
            {
                "theme": "modern"
            }
            """);
        testTemplate.persist();

        // Create regular test user 1
        testUser = new CalendarUser();
        testUser.oauthProvider = "GOOGLE";
        testUser.oauthSubject = "admin-test-user1-" + System.currentTimeMillis();
        testUser.email = "admin-test-user1@example.com";
        testUser.displayName = "Admin Test User 1";
        testUser.isAdmin = false;
        testUser.persist();

        // Create regular test user 2
        testUser2 = new CalendarUser();
        testUser2.oauthProvider = "GOOGLE";
        testUser2.oauthSubject = "admin-test-user2-" + System.currentTimeMillis();
        testUser2.email = "admin-test-user2@example.com";
        testUser2.displayName = "Admin Test User 2";
        testUser2.isAdmin = false;
        testUser2.persist();

        // Create admin user
        adminUser = new CalendarUser();
        adminUser.oauthProvider = "GOOGLE";
        adminUser.oauthSubject = "admin-test-admin-" + System.currentTimeMillis();
        adminUser.email = "admin@example.com";
        adminUser.displayName = "Admin User";
        adminUser.isAdmin = true;
        adminUser.persist();

        // Create test calendar for user 1
        testCalendar = new UserCalendar();
        testCalendar.user = testUser;
        testCalendar.name = "Test Calendar 1";
        testCalendar.year = 2025;
        testCalendar.template = testTemplate;
        testCalendar.isPublic = false;
        testCalendar.configuration = objectMapper.readTree("""
            {
                "theme": "modern"
            }
            """);
        testCalendar.persist();

        // Create test calendar for user 2
        testCalendar2 = new UserCalendar();
        testCalendar2.user = testUser2;
        testCalendar2.name = "Test Calendar 2";
        testCalendar2.year = 2025;
        testCalendar2.template = testTemplate;
        testCalendar2.isPublic = false;
        testCalendar2.configuration = objectMapper.readTree("""
            {
                "theme": "modern"
            }
            """);
        testCalendar2.persist();

        // Mock PaymentService
        Map<String, String> mockPaymentIntent = new HashMap<>();
        mockPaymentIntent.put("clientSecret", "pi_test_secret_admin");
        mockPaymentIntent.put("paymentIntentId", "pi_test_admin_" + System.currentTimeMillis());

        when(paymentService.createPaymentIntent(any(BigDecimal.class), anyString(), anyString()))
            .thenReturn(mockPaymentIntent);
        when(paymentService.getWebhookSecret()).thenReturn("");
    }

    @AfterEach
    @Transactional
    void cleanup() {
        // Clean up test data in reverse FK order
        // Delete all delayed jobs first
        DelayedJob.deleteAll();

        // Delete all orders
        CalendarOrder.deleteAll();

        // Delete calendars
        if (testCalendar != null && testCalendar.id != null) {
            UserCalendar.deleteById(testCalendar.id);
        }
        if (testCalendar2 != null && testCalendar2.id != null) {
            UserCalendar.deleteById(testCalendar2.id);
        }

        // Delete users
        if (testUser != null && testUser.id != null) {
            CalendarUser.deleteById(testUser.id);
        }
        if (testUser2 != null && testUser2.id != null) {
            CalendarUser.deleteById(testUser2.id);
        }
        if (adminUser != null && adminUser.id != null) {
            CalendarUser.deleteById(adminUser.id);
        }

        // Delete template
        if (testTemplate != null && testTemplate.id != null) {
            CalendarTemplate.deleteById(testTemplate.id);
        }
    }

    private JsonNode createTestAddress() throws Exception {
        return objectMapper.readTree("""
            {
                "street": "123 Admin St",
                "city": "Nashville",
                "state": "TN",
                "postalCode": "37203",
                "country": "US"
            }
            """);
    }

    // ============================================================================
    // ADMIN ORDER STATUS UPDATE TESTS
    // ============================================================================

    @Test
    @org.junit.jupiter.api.Order(1)
    @Transactional
    void testAdminUpdateOrderStatus_ToShipped() throws Exception {
        // Given: Create a PAID order
        String paymentIntentId = "pi_test_shipped_" + System.currentTimeMillis();
        CalendarOrder order = new CalendarOrder();
        order.user = testUser;
        order.calendar = testCalendar;
        order.quantity = 1;
        order.unitPrice = new BigDecimal("25.00");
        order.totalPrice = new BigDecimal("25.00");
        order.status = CalendarOrder.STATUS_PAID;
        order.stripePaymentIntentId = paymentIntentId;
        order.shippingAddress = createTestAddress();
        order.orderNumber = "2025-201";
        order.persist();

        // When: Admin updates order to SHIPPED with tracking number (via service layer)
        CalendarOrder updatedOrder = orderService.updateOrderStatus(
            order.id,
            CalendarOrder.STATUS_SHIPPED,
            "Package shipped via USPS"
        );
        updatedOrder.trackingNumber = "TRACK123456";
        updatedOrder.persist();

        // Flush to ensure persistence
        entityManager.flush();
        entityManager.clear();

        // Then: Verify order was updated
        CalendarOrder shippedOrder = CalendarOrder.<CalendarOrder>findById(order.id);
        assertEquals(CalendarOrder.STATUS_SHIPPED, shippedOrder.status, "Order should be SHIPPED");
        assertEquals("TRACK123456", shippedOrder.trackingNumber, "Tracking number should be set");
        assertNotNull(shippedOrder.shippedAt, "shippedAt timestamp should be set");
        assertNotNull(shippedOrder.notes, "Notes should contain status change");
        assertTrue(shippedOrder.notes.contains("SHIPPED"), "Notes should mention SHIPPED status");
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @Transactional
    void testAdminUpdateOrderStatus_EnqueuesShippingEmail() throws Exception {
        // Given: Create a PAID order
        String paymentIntentId = "pi_test_email_shipped_" + System.currentTimeMillis();
        CalendarOrder order = new CalendarOrder();
        order.user = testUser;
        order.calendar = testCalendar;
        order.quantity = 1;
        order.unitPrice = new BigDecimal("25.00");
        order.totalPrice = new BigDecimal("25.00");
        order.status = CalendarOrder.STATUS_PAID;
        order.stripePaymentIntentId = paymentIntentId;
        order.shippingAddress = createTestAddress();
        order.orderNumber = "2025-202";
        order.persist();

        // When: Admin updates order to SHIPPED
        orderService.updateOrderStatus(
            order.id,
            CalendarOrder.STATUS_SHIPPED,
            "Order shipped"
        );

        entityManager.flush();

        // Then: Verify shipping notification email job was enqueued
        List<DelayedJob> jobs = DelayedJob.find("actorId", order.id.toString()).list();
        boolean foundShippingEmail = jobs.stream()
            .anyMatch(job -> job.queue == DelayedJobQueue.EMAIL_SHIPPING_NOTIFICATION);
        assertTrue(foundShippingEmail, "Shipping notification email job should be enqueued");
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @Transactional
    void testAdminUpdateOrderStatus_ToProcessing() throws Exception {
        // Given: Create a PAID order
        String paymentIntentId = "pi_test_processing_" + System.currentTimeMillis();
        CalendarOrder order = new CalendarOrder();
        order.user = testUser;
        order.calendar = testCalendar;
        order.quantity = 1;
        order.unitPrice = new BigDecimal("25.00");
        order.totalPrice = new BigDecimal("25.00");
        order.status = CalendarOrder.STATUS_PAID;
        order.stripePaymentIntentId = paymentIntentId;
        order.shippingAddress = createTestAddress();
        order.orderNumber = "2025-203";
        order.persist();

        // When: Admin updates order to PROCESSING
        CalendarOrder updatedOrder = orderService.updateOrderStatus(
            order.id,
            CalendarOrder.STATUS_PROCESSING,
            "Started printing calendar"
        );

        entityManager.flush();
        entityManager.clear();

        // Then: Verify order status updated
        CalendarOrder processingOrder = CalendarOrder.<CalendarOrder>findById(order.id);
        assertEquals(CalendarOrder.STATUS_PROCESSING, processingOrder.status, "Order should be PROCESSING");
        assertTrue(processingOrder.notes.contains("PROCESSING"), "Notes should mention PROCESSING status");
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @Transactional
    void testAdminUpdateOrderStatus_ToDelivered() throws Exception {
        // Given: Create a SHIPPED order
        String paymentIntentId = "pi_test_delivered_" + System.currentTimeMillis();
        CalendarOrder order = new CalendarOrder();
        order.user = testUser;
        order.calendar = testCalendar;
        order.quantity = 1;
        order.unitPrice = new BigDecimal("25.00");
        order.totalPrice = new BigDecimal("25.00");
        order.status = CalendarOrder.STATUS_SHIPPED;
        order.stripePaymentIntentId = paymentIntentId;
        order.shippingAddress = createTestAddress();
        order.orderNumber = "2025-204";
        order.trackingNumber = "TRACK999";
        order.persist();

        // When: Admin updates order to DELIVERED
        orderService.updateOrderStatus(
            order.id,
            CalendarOrder.STATUS_DELIVERED,
            "Package delivered successfully"
        );

        entityManager.flush();
        entityManager.clear();

        // Then: Verify order is delivered
        CalendarOrder deliveredOrder = CalendarOrder.<CalendarOrder>findById(order.id);
        assertEquals(CalendarOrder.STATUS_DELIVERED, deliveredOrder.status, "Order should be DELIVERED");
        assertNotNull(deliveredOrder.notes, "Notes should be set");
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @Transactional
    void testAdminCannotUpdateTerminalOrder() throws Exception {
        // Given: Create a DELIVERED order (terminal status)
        String paymentIntentId = "pi_test_terminal_" + System.currentTimeMillis();
        CalendarOrder order = new CalendarOrder();
        order.user = testUser;
        order.calendar = testCalendar;
        order.quantity = 1;
        order.unitPrice = new BigDecimal("25.00");
        order.totalPrice = new BigDecimal("25.00");
        order.status = CalendarOrder.STATUS_DELIVERED;
        order.stripePaymentIntentId = paymentIntentId;
        order.shippingAddress = createTestAddress();
        order.orderNumber = "2025-205";
        order.persist();

        // When/Then: Attempting to update terminal order should fail
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            orderService.updateOrderStatus(
                order.id,
                CalendarOrder.STATUS_CANCELLED,
                "Trying to cancel delivered order"
            );
        });

        assertTrue(exception.getMessage().contains("Cannot update"),
            "Exception should mention order cannot be updated");
    }

    // ============================================================================
    // ADMIN QUERY TESTS
    // ============================================================================

    @Test
    @org.junit.jupiter.api.Order(6)
    @Transactional
    void testAdminQueryAllOrders_ReturnsAllUsers() throws Exception {
        // Given: Create orders for different users
        String pi1 = "pi_test_query1_" + System.currentTimeMillis();
        CalendarOrder order1 = new CalendarOrder();
        order1.user = testUser;
        order1.calendar = testCalendar;
        order1.quantity = 1;
        order1.unitPrice = new BigDecimal("25.00");
        order1.totalPrice = new BigDecimal("25.00");
        order1.status = CalendarOrder.STATUS_PAID;
        order1.stripePaymentIntentId = pi1;
        order1.shippingAddress = createTestAddress();
        order1.orderNumber = "2025-301";
        order1.persist();

        String pi2 = "pi_test_query2_" + System.currentTimeMillis();
        CalendarOrder order2 = new CalendarOrder();
        order2.user = testUser2;
        order2.calendar = testCalendar2;
        order2.quantity = 2;
        order2.unitPrice = new BigDecimal("25.00");
        order2.totalPrice = new BigDecimal("50.00");
        order2.status = CalendarOrder.STATUS_PROCESSING;
        order2.stripePaymentIntentId = pi2;
        order2.shippingAddress = createTestAddress();
        order2.orderNumber = "2025-302";
        order2.persist();

        entityManager.flush();

        // When: Query all orders via service (simulating admin)
        List<CalendarOrder> allOrders = CalendarOrder.listAll();

        // Then: Should include orders from both users
        assertTrue(allOrders.size() >= 2, "Should return at least 2 orders");

        boolean foundOrder1 = allOrders.stream().anyMatch(o -> o.id.equals(order1.id));
        boolean foundOrder2 = allOrders.stream().anyMatch(o -> o.id.equals(order2.id));

        assertTrue(foundOrder1, "Should include order from testUser");
        assertTrue(foundOrder2, "Should include order from testUser2");
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @Transactional
    void testQueryOrdersByStatus() throws Exception {
        // Given: Create orders with different statuses
        String pi1 = "pi_test_status1_" + System.currentTimeMillis();
        CalendarOrder paidOrder = new CalendarOrder();
        paidOrder.user = testUser;
        paidOrder.calendar = testCalendar;
        paidOrder.quantity = 1;
        paidOrder.unitPrice = new BigDecimal("25.00");
        paidOrder.totalPrice = new BigDecimal("25.00");
        paidOrder.status = CalendarOrder.STATUS_PAID;
        paidOrder.stripePaymentIntentId = pi1;
        paidOrder.shippingAddress = createTestAddress();
        paidOrder.orderNumber = "2025-401";
        paidOrder.persist();

        String pi2 = "pi_test_status2_" + System.currentTimeMillis();
        CalendarOrder shippedOrder = new CalendarOrder();
        shippedOrder.user = testUser;
        shippedOrder.calendar = testCalendar;
        shippedOrder.quantity = 1;
        shippedOrder.unitPrice = new BigDecimal("25.00");
        shippedOrder.totalPrice = new BigDecimal("25.00");
        shippedOrder.status = CalendarOrder.STATUS_SHIPPED;
        shippedOrder.stripePaymentIntentId = pi2;
        shippedOrder.shippingAddress = createTestAddress();
        shippedOrder.orderNumber = "2025-402";
        shippedOrder.trackingNumber = "TRACK456";
        shippedOrder.persist();

        entityManager.flush();

        // When: Query by status
        List<CalendarOrder> paidOrders = orderService.getOrdersByStatus(CalendarOrder.STATUS_PAID);
        List<CalendarOrder> shippedOrders = orderService.getOrdersByStatus(CalendarOrder.STATUS_SHIPPED);

        // Then: Should filter by status
        boolean foundPaidOrder = paidOrders.stream().anyMatch(o -> o.id.equals(paidOrder.id));
        boolean foundShippedOrder = shippedOrders.stream().anyMatch(o -> o.id.equals(shippedOrder.id));

        assertTrue(foundPaidOrder, "Should find PAID order");
        assertTrue(foundShippedOrder, "Should find SHIPPED order");

        // Verify PAID orders don't include SHIPPED order
        boolean paidHasShipped = paidOrders.stream().anyMatch(o -> o.id.equals(shippedOrder.id));
        assertFalse(paidHasShipped, "PAID orders should not include SHIPPED order");
    }

    // ============================================================================
    // AUTHORIZATION TESTS
    // ============================================================================

    @Test
    @org.junit.jupiter.api.Order(8)
    @Transactional
    void testUserCanCancelOwnOrder() throws Exception {
        // Given: testUser owns an order
        String paymentIntentId = "pi_test_own_cancel_" + System.currentTimeMillis();
        CalendarOrder order = new CalendarOrder();
        order.user = testUser;
        order.calendar = testCalendar;
        order.quantity = 1;
        order.unitPrice = new BigDecimal("25.00");
        order.totalPrice = new BigDecimal("25.00");
        order.status = CalendarOrder.STATUS_PENDING;
        order.stripePaymentIntentId = paymentIntentId;
        order.shippingAddress = createTestAddress();
        order.orderNumber = "2025-501";
        order.persist();

        // When: User cancels their own order
        CalendarOrder cancelledOrder = orderService.cancelOrder(
            order.id,
            testUser.id,
            false, // not admin
            "Changed my mind"
        );

        // Then: Should succeed
        assertEquals(CalendarOrder.STATUS_CANCELLED, cancelledOrder.status, "Order should be cancelled");
        assertTrue(cancelledOrder.notes.contains("Changed my mind"), "Notes should include reason");
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @Transactional
    void testUserCannotCancelOtherUserOrder() throws Exception {
        // Given: testUser owns an order
        String paymentIntentId = "pi_test_other_cancel_" + System.currentTimeMillis();
        CalendarOrder order = new CalendarOrder();
        order.user = testUser;
        order.calendar = testCalendar;
        order.quantity = 1;
        order.unitPrice = new BigDecimal("25.00");
        order.totalPrice = new BigDecimal("25.00");
        order.status = CalendarOrder.STATUS_PENDING;
        order.stripePaymentIntentId = paymentIntentId;
        order.shippingAddress = createTestAddress();
        order.orderNumber = "2025-502";
        order.persist();

        // When/Then: testUser2 tries to cancel testUser's order (should fail)
        SecurityException exception = assertThrows(SecurityException.class, () -> {
            orderService.cancelOrder(
                order.id,
                testUser2.id, // different user
                false, // not admin
                "Trying to cancel someone else's order"
            );
        });

        assertTrue(exception.getMessage().contains("not authorized"),
            "Exception should mention authorization failure");

        // Verify order status unchanged
        CalendarOrder unchangedOrder = CalendarOrder.<CalendarOrder>findById(order.id);
        assertEquals(CalendarOrder.STATUS_PENDING, unchangedOrder.status,
            "Order status should remain PENDING");
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @Transactional
    void testAdminCanCancelAnyOrder() throws Exception {
        // Given: testUser owns an order
        String paymentIntentId = "pi_test_admin_cancel_" + System.currentTimeMillis();
        CalendarOrder order = new CalendarOrder();
        order.user = testUser;
        order.calendar = testCalendar;
        order.quantity = 1;
        order.unitPrice = new BigDecimal("25.00");
        order.totalPrice = new BigDecimal("25.00");
        order.status = CalendarOrder.STATUS_PAID;
        order.stripePaymentIntentId = paymentIntentId;
        order.shippingAddress = createTestAddress();
        order.orderNumber = "2025-503";
        order.persist();

        // When: Admin cancels the order (even though they don't own it)
        CalendarOrder cancelledOrder = orderService.cancelOrder(
            order.id,
            adminUser.id,
            true, // is admin
            "Admin cancelled order"
        );

        // Then: Should succeed
        assertEquals(CalendarOrder.STATUS_CANCELLED, cancelledOrder.status,
            "Admin should be able to cancel any order");
        assertTrue(cancelledOrder.notes.contains("Admin cancelled order"),
            "Notes should include admin reason");
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    @Transactional
    void testStatusTransitionValidation() throws Exception {
        // Given: Create a PAID order
        String paymentIntentId = "pi_test_transition_" + System.currentTimeMillis();
        CalendarOrder order = new CalendarOrder();
        order.user = testUser;
        order.calendar = testCalendar;
        order.quantity = 1;
        order.unitPrice = new BigDecimal("25.00");
        order.totalPrice = new BigDecimal("25.00");
        order.status = CalendarOrder.STATUS_PAID;
        order.stripePaymentIntentId = paymentIntentId;
        order.shippingAddress = createTestAddress();
        order.orderNumber = "2025-504";
        order.persist();

        // When/Then: Invalid transition (PAID → PENDING) should fail
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            orderService.updateOrderStatus(
                order.id,
                CalendarOrder.STATUS_PENDING,
                "Trying invalid transition"
            );
        });

        assertTrue(exception.getMessage().contains("Invalid status transition"),
            "Exception should mention invalid transition");

        // Verify valid transitions work
        // PAID → PROCESSING (valid)
        CalendarOrder updatedOrder = orderService.updateOrderStatus(order.id, CalendarOrder.STATUS_PROCESSING, "Valid transition");
        assertEquals(CalendarOrder.STATUS_PROCESSING, updatedOrder.status);

        // PROCESSING → SHIPPED (valid)
        CalendarOrder shippedOrder = orderService.updateOrderStatus(order.id, CalendarOrder.STATUS_SHIPPED, "Valid transition");
        assertEquals(CalendarOrder.STATUS_SHIPPED, shippedOrder.status);
    }
}
