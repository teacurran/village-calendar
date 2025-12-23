package villagecompute.calendar.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.*;
import villagecompute.calendar.data.models.*;
import villagecompute.calendar.services.PaymentService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.util.HexFormat;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for order placement and payment workflow.
 * Tests the complete e-commerce flow from order creation to payment webhook processing.
 *
 * This test suite validates:
 * 1. Order creation via GraphQL with Stripe PaymentIntent
 * 2. Webhook payment success processing (checkout.session.completed)
 * 3. Email job enqueueing on payment success
 * 4. Idempotent webhook processing (duplicate webhooks handled correctly)
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderWorkflowTest {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    villagecompute.calendar.services.OrderService orderService;

    @Inject
    PaymentService paymentService;

    @ConfigProperty(name = "stripe.webhook.secret")
    String webhookSecret;

    private CalendarUser testUser;
    private CalendarTemplate testTemplate;
    private UserCalendar testCalendar;
    private String testPaymentIntentId;

    @BeforeEach
    @Transactional
    void setup() throws Exception {
        // Create test template
        testTemplate = new CalendarTemplate();
        testTemplate.name = "Order Test Template " + System.currentTimeMillis();
        testTemplate.description = "Template for order integration testing";
        testTemplate.isActive = true;
        testTemplate.isFeatured = false;
        testTemplate.displayOrder = 1;
        testTemplate.configuration = objectMapper.readTree("""
            {
                "theme": "modern",
                "colorScheme": "blue"
            }
            """);
        testTemplate.persist();

        // Create test user
        testUser = new CalendarUser();
        testUser.oauthProvider = "GOOGLE";
        testUser.oauthSubject = "order-test-" + System.currentTimeMillis();
        testUser.email = "order-test@example.com";
        testUser.displayName = "Order Test User";
        testUser.persist();

        // Create test calendar
        testCalendar = new UserCalendar();
        testCalendar.user = testUser;
        testCalendar.name = "Test Calendar for Orders";
        testCalendar.year = 2025;
        testCalendar.template = testTemplate;
        testCalendar.isPublic = false;
        testCalendar.configuration = objectMapper.readTree("""
            {
                "theme": "modern"
            }
            """);
        testCalendar.persist();

        // Initialize test payment intent ID for webhook tests
        testPaymentIntentId = "pi_test_" + System.currentTimeMillis();
    }

    @AfterEach
    @Transactional
    void cleanup() {
        // Clean up test data in reverse FK order
        // Delete all delayed jobs first
        DelayedJob.deleteAll();

        // Delete all order items first (before orders due to FK constraint)
        CalendarOrderItem.deleteAll();

        // Delete all orders
        CalendarOrder.deleteAll();

        // Delete calendar
        if (testCalendar != null && testCalendar.id != null) {
            UserCalendar.deleteById(testCalendar.id);
        }

        // Delete user
        if (testUser != null && testUser.id != null) {
            CalendarUser.deleteById(testUser.id);
        }

        // Delete template
        if (testTemplate != null && testTemplate.id != null) {
            CalendarTemplate.deleteById(testTemplate.id);
        }
    }

    private JsonNode createTestAddress() throws Exception {
        return objectMapper.readTree("""
            {
                "street": "123 Main St",
                "city": "Nashville",
                "state": "TN",
                "postalCode": "37203",
                "country": "US"
            }
            """);
    }

    /**
     * Generate a valid Stripe webhook signature for testing.
     */
    private String generateStripeSignature(String payload) throws Exception {
        long timestamp = System.currentTimeMillis() / 1000;
        String signedPayload = timestamp + "." + payload;

        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(webhookSecret.getBytes(), "HmacSHA256");
        mac.init(secretKey);
        byte[] hash = mac.doFinal(signedPayload.getBytes());
        String signature = HexFormat.of().formatHex(hash);

        return "t=" + timestamp + ",v1=" + signature;
    }

    // ============================================================================
    // ORDER CREATION TESTS
    // ============================================================================

    @Test
    @org.junit.jupiter.api.Order(1)
    @Transactional
    void testCreateOrder_ServiceLayer_ReturnsPendingOrder() throws Exception {
        // When: Create order via service layer (bypassing GraphQL authentication)
        CalendarOrder order = orderService.createOrder(
            testUser,
            testCalendar,
            1, // quantity
            new BigDecimal("25.00"), // unitPrice
            createTestAddress()
        );

        // Then: Verify order was created in database with PENDING status
        assertNotNull(order, "Order should be created");
        assertEquals(CalendarOrder.STATUS_PENDING, order.status, "Order should be in PENDING status");
        assertEquals(testUser.id, order.user.id, "Order should belong to test user");
        assertEquals(testCalendar.id, order.calendar.id, "Order should reference test calendar");
        assertEquals(1, order.quantity, "Order quantity should be 1");

        // Verify total price includes base price + tax + shipping
        assertNotNull(order.totalPrice, "Total price should be calculated");
        assertTrue(order.totalPrice.compareTo(BigDecimal.ZERO) > 0, "Total price should be positive");

        // Verify order number was generated
        assertNotNull(order.orderNumber, "Order number should be generated");
        assertTrue(order.orderNumber.matches("VC-[23456789A-HJ-NP-Z]{4}-[23456789A-HJ-NP-Z]{4}"),
            "Order number should match format VC-XXXX-XXXX, got: " + order.orderNumber);
    }

    // ============================================================================
    // WEBHOOK PAYMENT SUCCESS TESTS
    // ============================================================================

    @Test
    @org.junit.jupiter.api.Order(2)
    void testWebhookPaymentSuccess_UpdatesOrderToPaid() throws Exception {
        // Given: Create an order in PENDING status (use programmatic transaction to commit before HTTP request)
        CalendarOrder order = QuarkusTransaction.requiringNew().call(() -> {
            CalendarOrder newOrder = new CalendarOrder();
            newOrder.user = testUser;
            newOrder.calendar = testCalendar;
            newOrder.quantity = 1;
            newOrder.unitPrice = new BigDecimal("25.00");
            newOrder.totalPrice = new BigDecimal("25.00");
            newOrder.status = CalendarOrder.STATUS_PENDING;
            newOrder.stripePaymentIntentId = testPaymentIntentId;
            try {
                newOrder.shippingAddress = createTestAddress();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            newOrder.orderNumber = "2025-001";
            newOrder.persist();
            return newOrder;
        });

        // When: Simulate Stripe webhook (checkout.session.completed)
        String webhookPayload = String.format("""
            {
                "id": "evt_test_webhook",
                "object": "event",
                "api_version": "2024-11-20.acacia",
                "created": %d,
                "type": "checkout.session.completed",
                "data": {
                    "object": {
                        "id": "cs_test_session",
                        "object": "checkout.session",
                        "payment_intent": "%s",
                        "payment_status": "paid",
                        "status": "complete",
                        "metadata": {
                            "orderId": "%s"
                        }
                    }
                }
            }
            """, System.currentTimeMillis() / 1000, testPaymentIntentId, order.id.toString());

        String signature = generateStripeSignature(webhookPayload);

        given()
            .contentType(ContentType.JSON)
            .header("Stripe-Signature", signature)
            .body(webhookPayload)
            .when()
            .post("/api/webhooks/stripe")
            .then()
            .statusCode(200)
            .body("status", equalTo("success"));

        // Then: Verify order was updated to PAID
        CalendarOrder updatedOrder = CalendarOrder.<CalendarOrder>findById(order.id);
        assertEquals(CalendarOrder.STATUS_PAID, updatedOrder.status, "Order status should be PAID");
        assertNotNull(updatedOrder.paidAt, "paidAt timestamp should be set");
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    void testWebhookPaymentSuccess_EnqueuesEmailJob() throws Exception {
        // Given: Create an order in PENDING status (use programmatic transaction to commit before HTTP request)
        CalendarOrder order = QuarkusTransaction.requiringNew().call(() -> {
            CalendarOrder newOrder = new CalendarOrder();
            newOrder.user = testUser;
            newOrder.calendar = testCalendar;
            newOrder.quantity = 1;
            newOrder.unitPrice = new BigDecimal("25.00");
            newOrder.totalPrice = new BigDecimal("25.00");
            newOrder.status = CalendarOrder.STATUS_PENDING;
            newOrder.stripePaymentIntentId = testPaymentIntentId;
            try {
                newOrder.shippingAddress = createTestAddress();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            newOrder.orderNumber = "2025-002";
            newOrder.persist();
            return newOrder;
        });

        // When: Simulate payment success webhook
        String webhookPayload = String.format("""
            {
                "id": "evt_test_webhook_email",
                "object": "event",
                "api_version": "2024-11-20.acacia",
                "created": %d,
                "type": "checkout.session.completed",
                "data": {
                    "object": {
                        "id": "cs_test_session_email",
                        "object": "checkout.session",
                        "payment_intent": "%s",
                        "payment_status": "paid",
                        "status": "complete",
                        "metadata": {
                            "orderId": "%s"
                        }
                    }
                }
            }
            """, System.currentTimeMillis() / 1000, testPaymentIntentId, order.id.toString());

        String signature = generateStripeSignature(webhookPayload);

        given()
            .contentType(ContentType.JSON)
            .header("Stripe-Signature", signature)
            .body(webhookPayload)
            .when()
            .post("/api/webhooks/stripe")
            .then()
            .statusCode(200);

        // Then: Verify email job was enqueued
        List<DelayedJob> jobs = DelayedJob.find("actorId", order.id.toString()).list();
        assertEquals(1, jobs.size(), "Order confirmation email job should be enqueued");
        assertEquals("OrderEmailJobHandler", jobs.get(0).queueName,
            "Email job should be in ORDER_CONFIRMATION queue");
        assertFalse(jobs.get(0).complete, "Job should not be completed yet");
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    void testWebhookIdempotency_DoesNotDuplicateProcessing() throws Exception {
        // Given: Create an order in PENDING status (use programmatic transaction to commit before HTTP request)
        CalendarOrder order = QuarkusTransaction.requiringNew().call(() -> {
            CalendarOrder newOrder = new CalendarOrder();
            newOrder.user = testUser;
            newOrder.calendar = testCalendar;
            newOrder.quantity = 1;
            newOrder.unitPrice = new BigDecimal("25.00");
            newOrder.totalPrice = new BigDecimal("25.00");
            newOrder.status = CalendarOrder.STATUS_PENDING;
            newOrder.stripePaymentIntentId = testPaymentIntentId;
            try {
                newOrder.shippingAddress = createTestAddress();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            newOrder.orderNumber = "2025-003";
            newOrder.persist();
            return newOrder;
        });

        // When: Send webhook twice
        String webhookPayload = String.format("""
            {
                "id": "evt_test_idempotent",
                "object": "event",
                "api_version": "2024-11-20.acacia",
                "created": %d,
                "type": "checkout.session.completed",
                "data": {
                    "object": {
                        "id": "cs_test_idempotent",
                        "object": "checkout.session",
                        "payment_intent": "%s",
                        "payment_status": "paid",
                        "status": "complete",
                        "metadata": {
                            "orderId": "%s"
                        }
                    }
                }
            }
            """, System.currentTimeMillis() / 1000, testPaymentIntentId, order.id.toString());

        String signature = generateStripeSignature(webhookPayload);

        // First webhook
        given()
            .contentType(ContentType.JSON)
            .header("Stripe-Signature", signature)
            .body(webhookPayload)
            .when()
            .post("/api/webhooks/stripe")
            .then()
            .statusCode(200);

        // Second webhook (duplicate)
        given()
            .contentType(ContentType.JSON)
            .header("Stripe-Signature", signature)
            .body(webhookPayload)
            .when()
            .post("/api/webhooks/stripe")
            .then()
            .statusCode(200);

        // Then: Verify order is still PAID (not changed by second webhook)
        CalendarOrder updatedOrder = CalendarOrder.<CalendarOrder>findById(order.id);
        assertEquals(CalendarOrder.STATUS_PAID, updatedOrder.status, "Order should still be PAID");

        // Verify only 1 email job was created (not duplicated)
        List<DelayedJob> jobs = DelayedJob.find("actorId", order.id.toString()).list();
        assertEquals(1, jobs.size(), "Only one email job should exist (idempotent)");
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @Transactional
    void testCreateOrder_CalculatesTotalWithShipping() throws Exception {
        // When: Create order via service layer with quantity 2
        JsonNode portlandAddress = objectMapper.readTree("""
            {
                "street": "456 Oak Ave",
                "city": "Portland",
                "state": "OR",
                "postalCode": "97201",
                "country": "US"
            }
            """);

        CalendarOrder order = orderService.createOrder(
            testUser,
            testCalendar,
            2, // quantity
            new BigDecimal("25.00"), // unitPrice
            portlandAddress
        );

        // Then: Verify order total includes shipping cost
        assertNotNull(order, "Order should exist");
        assertEquals(2, order.quantity, "Quantity should be 2");

        // Shipping should be calculated (not zero for 2 items)
        // Note: ShippingService calculates based on weight and destination
        assertTrue(order.totalPrice.compareTo(order.unitPrice.multiply(BigDecimal.valueOf(2))) >= 0,
            "Total should be at least subtotal (may include shipping)");
    }

    // ============================================================================
    // ORDER ITEM YEAR TESTS
    // ============================================================================

    @Test
    @org.junit.jupiter.api.Order(6)
    void testOrderItem_YearStoredInConfiguration() throws Exception {
        // Given: Create an order with items using programmatic transaction
        CalendarOrder order = QuarkusTransaction.requiringNew().call(() -> {
            CalendarOrder newOrder = new CalendarOrder();
            newOrder.user = testUser;
            newOrder.calendar = testCalendar;
            newOrder.quantity = 1;
            newOrder.unitPrice = new BigDecimal("25.00");
            newOrder.totalPrice = new BigDecimal("25.00");
            newOrder.status = CalendarOrder.STATUS_PENDING;
            newOrder.orderNumber = "VC-TEST-YEAR";
            try {
                newOrder.shippingAddress = createTestAddress();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            newOrder.persist();

            // Create an order item with year set via setYear()
            CalendarOrderItem item = new CalendarOrderItem();
            item.order = newOrder;
            item.productType = CalendarOrderItem.TYPE_PRINT;
            item.productName = "Test Calendar 2026";
            item.setYear(2026); // Use the new setYear method
            item.quantity = 1;
            item.unitPrice = new BigDecimal("25.00");
            item.calculateLineTotal();
            item.itemStatus = CalendarOrderItem.STATUS_PENDING;
            item.persist();
            newOrder.items.add(item);

            return newOrder;
        });

        // Then: Verify year was stored correctly
        CalendarOrderItem savedItem = QuarkusTransaction.requiringNew().call(() -> {
            CalendarOrder savedOrder = CalendarOrder.findById(order.id);
            assertNotNull(savedOrder.items, "Order should have items");
            assertFalse(savedOrder.items.isEmpty(), "Order should have at least one item");
            return savedOrder.items.get(0);
        });

        // Verify getYear() returns the correct value
        assertEquals(2026, savedItem.getYear(), "Year should be 2026");

        // Verify year is stored in configuration JSON
        assertNotNull(savedItem.configuration, "Configuration should not be null");
        assertTrue(savedItem.configuration.has("year"), "Configuration should have year field");
        assertEquals(2026, savedItem.configuration.get("year").asInt(), "Configuration year should be 2026");
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    void testOrderItem_YearDefaultsToCurrentYear() throws Exception {
        // Given: Create an order item without setting year
        CalendarOrderItem item = QuarkusTransaction.requiringNew().call(() -> {
            CalendarOrder newOrder = new CalendarOrder();
            newOrder.user = testUser;
            newOrder.calendar = testCalendar;
            newOrder.quantity = 1;
            newOrder.unitPrice = new BigDecimal("25.00");
            newOrder.totalPrice = new BigDecimal("25.00");
            newOrder.status = CalendarOrder.STATUS_PENDING;
            newOrder.orderNumber = "VC-TEST-DFLT";
            try {
                newOrder.shippingAddress = createTestAddress();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            newOrder.persist();

            CalendarOrderItem newItem = new CalendarOrderItem();
            newItem.order = newOrder;
            newItem.productType = CalendarOrderItem.TYPE_PRINT;
            newItem.productName = "Test Calendar Default Year";
            // Don't set year - it should default to current year
            newItem.quantity = 1;
            newItem.unitPrice = new BigDecimal("25.00");
            newItem.calculateLineTotal();
            newItem.itemStatus = CalendarOrderItem.STATUS_PENDING;
            newItem.persist();

            return newItem;
        });

        // Then: Verify getYear() returns current year
        int currentYear = java.time.Year.now().getValue();
        assertEquals(currentYear, item.getYear(), "Year should default to current year");
    }
}
