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
import villagecompute.calendar.data.models.CalendarOrderItem;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;

import io.quarkus.test.junit.QuarkusTest;

/** Unit tests for OrderService. Tests order creation, status updates, and lifecycle transitions. */
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
        // Clean up test data in correct order (children before parents due to FK constraints)
        // Note: OrderItems reference Shipments, so we need to clear items first or set shipment_id to null
        CalendarOrderItem.update("shipment = null where shipment is not null");
        villagecompute.calendar.data.models.Shipment.deleteAll();
        CalendarOrderItem.deleteAll();
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
        CalendarOrder order = orderService.createOrder(testUser, testCalendar, quantity, unitPrice,
                testShippingAddress);

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
        assertTrue(order.orderNumber.matches("VC-[23456789A-HJ-NP-Z]{4}-[23456789A-HJ-NP-Z]{4}"),
                "Order number should match format VC-XXXX-XXXX");
    }

    @Test
    @Transactional
    void testCreateOrderWithInvalidQuantity() {
        // Given
        Integer invalidQuantity = 0;
        BigDecimal unitPrice = new BigDecimal("29.99");

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(testUser, testCalendar,
                invalidQuantity, unitPrice, testShippingAddress));
    }

    @Test
    @Transactional
    void testCreateOrderWithInvalidPrice() {
        // Given
        Integer quantity = 1;
        BigDecimal invalidPrice = new BigDecimal("-10.00");

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> orderService.createOrder(testUser, testCalendar, quantity, invalidPrice, testShippingAddress));
    }

    @Test
    @Transactional
    void testUpdateOrderStatus() {
        // Given
        CalendarOrder order = createTestOrder();
        String notes = "Payment confirmed via Stripe";

        // When
        CalendarOrder updatedOrder = orderService.updateOrderStatus(order.id, CalendarOrder.STATUS_PAID, notes);

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
        CalendarOrder shippedOrder = orderService.updateOrderStatus(order.id, CalendarOrder.STATUS_SHIPPED,
                "Shipped via USPS");

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
        assertThrows(IllegalStateException.class,
                () -> orderService.updateOrderStatus(order.id, CalendarOrder.STATUS_PROCESSING, "Cannot update"));
    }

    @Test
    @Transactional
    void testInvalidStatusTransition() {
        // Given
        CalendarOrder order = createTestOrder();

        // When & Then - Cannot go from PENDING to SHIPPED directly
        assertThrows(IllegalStateException.class,
                () -> orderService.updateOrderStatus(order.id, CalendarOrder.STATUS_SHIPPED, "Invalid transition"));
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
        createTestOrder();
        createTestOrder();

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
        Optional<CalendarOrder> foundOrder = CalendarOrder.findByOrderNumber(orderNumber).firstResultOptional();

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
        CalendarOrder cancelledOrder = orderService.cancelOrder(order.id, testUser.id, false, cancellationReason);

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
        CalendarOrder cancelledOrder = orderService.cancelOrder(order.id, testUser.id, false,
                "Customer requested refund");

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
        assertThrows(SecurityException.class, () -> orderService.cancelOrder(order.id, unauthorizedUserId, false, // Not
                                                                                                                  // admin
                "Unauthorized attempt"));
    }

    @Test
    @Transactional
    void testCancelOrderAsAdmin() {
        // Given
        CalendarOrder order = createTestOrder();
        UUID adminUserId = UUID.randomUUID(); // Different user

        // When - Admin can cancel any order
        CalendarOrder cancelledOrder = orderService.cancelOrder(order.id, adminUserId, true, // Is admin
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
        assertThrows(IllegalStateException.class,
                () -> orderService.cancelOrder(order.id, testUser.id, false, "Cannot cancel delivered order"));
    }

    @Test
    @Transactional
    void testCancelAlreadyCancelledOrder() {
        // Given
        CalendarOrder order = createTestOrder();
        orderService.cancelOrder(order.id, testUser.id, false, "First cancellation");

        // When & Then - Cannot cancel already cancelled order
        assertThrows(IllegalStateException.class,
                () -> orderService.cancelOrder(order.id, testUser.id, false, "Second cancellation"));
    }

    @Test
    @Transactional
    void testCancelNonExistentOrder() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> orderService.cancelOrder(nonExistentId, testUser.id, false, "Cancel non-existent"));
    }

    @Test
    @Transactional
    void testCancelOrderFromProcessing() {
        // Given
        CalendarOrder order = createTestOrder();
        orderService.updateOrderStatus(order.id, CalendarOrder.STATUS_PAID, "Payment confirmed");
        orderService.updateOrderStatus(order.id, CalendarOrder.STATUS_PROCESSING, "Order processing");

        // When
        CalendarOrder cancelledOrder = orderService.cancelOrder(order.id, testUser.id, false,
                "Cancel during processing");

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
        CalendarOrder order = orderService.createOrder(testUser, testCalendar, quantity, unitPrice,
                testShippingAddress);

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
        java.util.Set<String> orderNumbers = orders.stream().map(o -> o.orderNumber)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(10, orderNumbers.size(), "All 10 order numbers should be unique");

        // Verify all match the format
        orders.forEach(order -> {
            assertNotNull(order.orderNumber);
            assertTrue(order.orderNumber.matches("VC-[23456789A-HJ-NP-Z]{4}-[23456789A-HJ-NP-Z]{4}"));
        });
    }

    // ==================== Shipping Methods Tests ====================

    @Test
    @Transactional
    void testMarkItemAsShipped() {
        // Given
        CalendarOrder order = createTestOrder();
        order.status = CalendarOrder.STATUS_PAID;
        order.persist();

        CalendarOrderItem item = new CalendarOrderItem();
        item.order = order;
        item.productType = CalendarOrderItem.TYPE_PRINT;
        item.productName = "Test Calendar";
        item.quantity = 1;
        item.unitPrice = new BigDecimal("29.99");
        item.lineTotal = new BigDecimal("29.99");
        item.itemStatus = CalendarOrderItem.STATUS_PENDING;
        item.persist();
        order.items.add(item);

        // When
        CalendarOrderItem shippedItem = orderService.markItemAsShipped(item.id);

        // Then
        assertEquals(CalendarOrderItem.STATUS_SHIPPED, shippedItem.itemStatus);
    }

    @Test
    @Transactional
    void testMarkItemAsShipped_NotFound() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> orderService.markItemAsShipped(nonExistentId));
    }

    @Test
    @Transactional
    void testMarkItemAsDelivered() {
        // Given
        CalendarOrder order = createTestOrder();
        order.status = CalendarOrder.STATUS_SHIPPED;
        order.persist();

        CalendarOrderItem item = new CalendarOrderItem();
        item.order = order;
        item.productType = CalendarOrderItem.TYPE_PRINT;
        item.productName = "Test Calendar";
        item.quantity = 1;
        item.unitPrice = new BigDecimal("29.99");
        item.lineTotal = new BigDecimal("29.99");
        item.itemStatus = CalendarOrderItem.STATUS_SHIPPED;
        item.persist();
        order.items.add(item);

        // When
        CalendarOrderItem deliveredItem = orderService.markItemAsDelivered(item.id);

        // Then
        assertEquals(CalendarOrderItem.STATUS_DELIVERED, deliveredItem.itemStatus);
    }

    @Test
    @Transactional
    void testMarkItemAsDelivered_NotFound() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> orderService.markItemAsDelivered(nonExistentId));
    }

    @Test
    @Transactional
    void testCreateShipment() {
        // Given
        CalendarOrder order = createTestOrder();
        order.status = CalendarOrder.STATUS_PAID;
        order.persist();

        CalendarOrderItem item1 = new CalendarOrderItem();
        item1.order = order;
        item1.productType = CalendarOrderItem.TYPE_PRINT;
        item1.productName = "Test Calendar 1";
        item1.quantity = 1;
        item1.unitPrice = new BigDecimal("29.99");
        item1.lineTotal = new BigDecimal("29.99");
        item1.itemStatus = CalendarOrderItem.STATUS_PENDING;
        item1.persist();
        order.items.add(item1);

        CalendarOrderItem item2 = new CalendarOrderItem();
        item2.order = order;
        item2.productType = CalendarOrderItem.TYPE_PRINT;
        item2.productName = "Test Calendar 2";
        item2.quantity = 1;
        item2.unitPrice = new BigDecimal("29.99");
        item2.lineTotal = new BigDecimal("29.99");
        item2.itemStatus = CalendarOrderItem.STATUS_PENDING;
        item2.persist();
        order.items.add(item2);

        // When
        villagecompute.calendar.data.models.Shipment shipment = orderService.createShipment(order.id,
                java.util.List.of(item1.id, item2.id), "USPS", "TRACK123456");

        // Then
        assertNotNull(shipment);
        assertNotNull(shipment.id);
        assertEquals("USPS", shipment.carrier);
        assertEquals("TRACK123456", shipment.trackingNumber);
        assertEquals(2, shipment.items.size());
    }

    @Test
    @Transactional
    void testCreateShipment_OrderNotFound() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> orderService.createShipment(nonExistentId, java.util.List.of(), "USPS", "TRACK123"));
    }

    @Test
    @Transactional
    void testMarkShipmentAsShipped() {
        // Given
        CalendarOrder order = createTestOrder();
        order.status = CalendarOrder.STATUS_PAID;
        order.persist();

        CalendarOrderItem item = new CalendarOrderItem();
        item.order = order;
        item.productType = CalendarOrderItem.TYPE_PRINT;
        item.productName = "Test Calendar";
        item.quantity = 1;
        item.unitPrice = new BigDecimal("29.99");
        item.lineTotal = new BigDecimal("29.99");
        item.itemStatus = CalendarOrderItem.STATUS_PENDING;
        item.persist();
        order.items.add(item);

        villagecompute.calendar.data.models.Shipment shipment = orderService.createShipment(order.id,
                java.util.List.of(item.id), "USPS", "TRACK123456");

        // When
        villagecompute.calendar.data.models.Shipment shippedShipment = orderService.markShipmentAsShipped(shipment.id);

        // Then
        assertNotNull(shippedShipment.shippedAt);
        assertEquals(villagecompute.calendar.data.models.Shipment.STATUS_SHIPPED, shippedShipment.status);
    }

    @Test
    @Transactional
    void testMarkShipmentAsShipped_NotFound() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> orderService.markShipmentAsShipped(nonExistentId));
    }

    @Test
    @Transactional
    void testAutoShipDigitalItems() {
        // Given
        CalendarOrder order = createTestOrder();
        order.status = CalendarOrder.STATUS_PAID;
        order.persist();

        // Add PDF item (digital)
        CalendarOrderItem pdfItem = new CalendarOrderItem();
        pdfItem.order = order;
        pdfItem.productType = CalendarOrderItem.TYPE_PDF;
        pdfItem.productName = "PDF Calendar";
        pdfItem.quantity = 1;
        pdfItem.unitPrice = new BigDecimal("9.99");
        pdfItem.lineTotal = new BigDecimal("9.99");
        pdfItem.itemStatus = CalendarOrderItem.STATUS_PENDING;
        pdfItem.persist();
        order.items.add(pdfItem);

        // Add print item (physical)
        CalendarOrderItem printItem = new CalendarOrderItem();
        printItem.order = order;
        printItem.productType = CalendarOrderItem.TYPE_PRINT;
        printItem.productName = "Print Calendar";
        printItem.quantity = 1;
        printItem.unitPrice = new BigDecimal("29.99");
        printItem.lineTotal = new BigDecimal("29.99");
        printItem.itemStatus = CalendarOrderItem.STATUS_PENDING;
        printItem.persist();
        order.items.add(printItem);

        // When
        int shippedCount = orderService.autoShipDigitalItems(order.id);

        // Then
        assertEquals(1, shippedCount);
        pdfItem = CalendarOrderItem.findById(pdfItem.id);
        assertEquals(CalendarOrderItem.STATUS_SHIPPED, pdfItem.itemStatus);
        printItem = CalendarOrderItem.findById(printItem.id);
        assertEquals(CalendarOrderItem.STATUS_PENDING, printItem.itemStatus);
    }

    @Test
    @Transactional
    void testAutoShipDigitalItems_OrderNotFound() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> orderService.autoShipDigitalItems(nonExistentId));
    }

    // ==================== Checkout Methods Tests ====================

    @Test
    @Transactional
    void testProcessCheckout() {
        // Given
        String paymentIntentId = "pi_test_checkout_123";
        String email = "checkout-test@example.com";
        java.util.Map<String, Object> shippingAddress = new java.util.HashMap<>();
        shippingAddress.put("firstName", "John");
        shippingAddress.put("lastName", "Doe");
        shippingAddress.put("street", "123 Main St");
        shippingAddress.put("city", "Portland");
        shippingAddress.put("state", "OR");
        shippingAddress.put("postalCode", "97201");
        shippingAddress.put("country", "US");

        java.util.List<java.util.Map<String, Object>> items = new java.util.ArrayList<>();
        java.util.Map<String, Object> item1 = new java.util.HashMap<>();
        item1.put("description", "Modern Calendar 2025");
        item1.put("quantity", 2);
        item1.put("unitPrice", 29.99);
        item1.put("lineTotal", 59.98);
        item1.put("configuration", "{\"theme\":\"modern\",\"year\":2025}");
        items.add(item1);

        Double subtotal = 59.98;
        Double shippingCost = 5.99;
        Double taxAmount = 0.0;
        Double totalAmount = 65.97;

        // When
        String orderNumber = orderService.processCheckout(paymentIntentId, email, shippingAddress, items, subtotal,
                shippingCost, taxAmount, totalAmount);

        // Then
        assertNotNull(orderNumber);
        assertTrue(orderNumber.matches("VC-[23456789A-HJ-NP-Z]{4}-[23456789A-HJ-NP-Z]{4}"));

        // Verify order was created
        CalendarOrder order = orderService.findByOrderNumber(orderNumber);
        assertNotNull(order);
        assertEquals(CalendarOrder.STATUS_PAID, order.status);
        assertEquals(paymentIntentId, order.stripePaymentIntentId);
        assertEquals(1, order.items.size());
    }

    @Test
    @Transactional
    void testProcessCheckout_WithBillingAddress() {
        // Given
        String paymentIntentId = "pi_test_billing_123";
        String email = "billing-test@example.com";
        java.util.Map<String, Object> shippingAddress = new java.util.HashMap<>();
        shippingAddress.put("firstName", "John");
        shippingAddress.put("lastName", "Doe");
        shippingAddress.put("street", "123 Main St");

        java.util.Map<String, Object> billingAddress = new java.util.HashMap<>();
        billingAddress.put("firstName", "John");
        billingAddress.put("lastName", "Doe");
        billingAddress.put("street", "456 Billing Ave");

        java.util.List<java.util.Map<String, Object>> items = new java.util.ArrayList<>();

        // When
        String orderNumber = orderService.processCheckout(paymentIntentId, email, shippingAddress, billingAddress,
                items, 0.0, 0.0, 0.0, 0.0);

        // Then
        assertNotNull(orderNumber);
        CalendarOrder order = orderService.findByOrderNumber(orderNumber);
        assertNotNull(order.billingAddress);
    }

    @Test
    @Transactional
    void testFindOrCreateGuestUser_ExistingUser() {
        // Given - testUser already exists with email test@example.com

        // When
        CalendarUser foundUser = orderService.findOrCreateGuestUser(testUser.email, null);

        // Then
        assertEquals(testUser.id, foundUser.id);
    }

    @Test
    @Transactional
    void testFindOrCreateGuestUser_NewGuest() {
        // Given
        String newEmail = "new-guest-" + System.currentTimeMillis() + "@example.com";
        java.util.Map<String, Object> addressInfo = new java.util.HashMap<>();
        addressInfo.put("firstName", "Jane");
        addressInfo.put("lastName", "Guest");

        // When
        CalendarUser guestUser = orderService.findOrCreateGuestUser(newEmail, addressInfo);

        // Then
        assertNotNull(guestUser);
        assertNotNull(guestUser.id);
        assertEquals(newEmail, guestUser.email);
        assertEquals("GUEST", guestUser.oauthProvider);
        assertEquals("Jane Guest", guestUser.displayName);
        assertFalse(guestUser.isAdmin);
    }

    @Test
    @Transactional
    void testFindByOrderNumber() {
        // Given
        CalendarOrder order = createTestOrder();

        // When
        CalendarOrder found = orderService.findByOrderNumber(order.orderNumber);

        // Then
        assertNotNull(found);
        assertEquals(order.id, found.id);
    }

    @Test
    @Transactional
    void testFindByOrderNumber_NotFound() {
        // Given
        String nonExistentOrderNumber = "VC-XXXX-XXXX";

        // When
        CalendarOrder found = orderService.findByOrderNumber(nonExistentOrderNumber);

        // Then
        assertNull(found);
    }

    @Test
    @Transactional
    void testFindByOrderNumber_NullOrEmpty() {
        // Given/When/Then
        assertNull(orderService.findByOrderNumber(null));
        assertNull(orderService.findByOrderNumber(""));
        assertNull(orderService.findByOrderNumber("   "));
    }

    // Helper method to create a test order
    private CalendarOrder createTestOrder() {
        return orderService.createOrder(testUser, testCalendar, 1, new BigDecimal("29.99"), testShippingAddress);
    }
}
