package villagecompute.calendar.api.rest;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.DelayedJob;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.services.CalendarService;
import villagecompute.calendar.services.OrderService;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration tests for Stripe webhook handling.
 *
 * <p>Tests cover: - Webhook signature validation (valid and invalid signatures) -
 * checkout.session.completed event processing - payment_intent.succeeded event processing -
 * charge.refunded event processing - Idempotent webhook processing (duplicate delivery handling) -
 * Email job enqueueing on payment success - Order status transitions
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StripeWebhookControllerTest {

    @Inject ObjectMapper objectMapper;

    @Inject CalendarService calendarService;

    @Inject OrderService orderService;

    @Inject EntityManager entityManager;

    @ConfigProperty(name = "stripe.webhook.secret")
    String webhookSecret;

    private CalendarUser testUser;
    private CalendarTemplate testTemplate;
    private UserCalendar testCalendar;
    private CalendarOrder testOrder;

    private static final String TEST_PAYMENT_INTENT_ID = "pi_test_123456789";
    private static final String TEST_SESSION_ID = "cs_test_123456789";
    private static final String TEST_CHARGE_ID = "ch_test_123456789";

    @BeforeEach
    void setup() throws Exception {
        // Wrap in programmatic transaction that commits immediately
        // This ensures test data is visible to HTTP requests made by RestAssured
        QuarkusTransaction.requiringNew()
                .run(
                        () -> {
                            try {
                                // Create test user
                                testUser = new CalendarUser();
                                testUser.oauthProvider = "GOOGLE";
                                testUser.oauthSubject =
                                        "webhook-test-" + System.currentTimeMillis();
                                testUser.email =
                                        "webhook-test-"
                                                + System.currentTimeMillis()
                                                + "@example.com";
                                testUser.displayName = "Webhook Test User";
                                testUser.persist();

                                // Create test template
                                testTemplate = new CalendarTemplate();
                                testTemplate.name = "Webhook Test Template";
                                testTemplate.description = "Template for webhook testing";
                                testTemplate.isActive = true;
                                testTemplate.isFeatured = false;
                                testTemplate.displayOrder = 1;
                                testTemplate.configuration = createTestConfiguration();
                                testTemplate.persist();

                                // Create test calendar
                                testCalendar =
                                        calendarService.createCalendar(
                                                "Test Calendar for Order",
                                                2025,
                                                testTemplate.id,
                                                null,
                                                true,
                                                testUser,
                                                null);

                                // Create test order in PENDING status
                                JsonNode shippingAddress =
                                        objectMapper.readTree(
                                                """
                    {
                        "line1": "123 Test St",
                        "city": "Test City",
                        "state": "CA",
                        "postalCode": "12345",
                        "country": "US"
                    }
                    """);

                                testOrder =
                                        orderService.createOrder(
                                                testUser,
                                                testCalendar,
                                                1,
                                                new BigDecimal("19.99"),
                                                shippingAddress);

                                // Set payment intent ID for webhook correlation
                                testOrder.stripePaymentIntentId = TEST_PAYMENT_INTENT_ID;
                                testOrder.persist();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
    }

    @AfterEach
    @Transactional
    void cleanup() {
        // Clean up in correct order due to foreign keys
        if (testUser != null && testUser.id != null) {
            // Delete delayed jobs first
            DelayedJob.delete("actorId = ?1", testOrder != null ? testOrder.id.toString() : "");
            // Delete all orders for this user
            CalendarOrder.delete("user.id", testUser.id);
            // Delete all calendars for this user
            UserCalendar.delete("user.id", testUser.id);
            // Delete user
            CalendarUser.deleteById(testUser.id);
        }
        if (testTemplate != null && testTemplate.id != null) {
            CalendarTemplate.deleteById(testTemplate.id);
        }
    }

    private JsonNode createTestConfiguration() {
        try {
            return objectMapper.readTree(
                    """
                {
                    "theme": "modern",
                    "colorScheme": "blue"
                }
                """);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Generate a valid Stripe webhook signature for testing. */
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
    // TEST: Invalid Signature Rejection
    // ============================================================================

    @Test
    @Order(1)
    void testWebhook_RejectInvalidSignature() {
        String payload =
                """
            {
                "id": "evt_test_invalid",
                "type": "checkout.session.completed",
                "data": {}
            }
            """;

        given().contentType(ContentType.JSON)
                .header("Stripe-Signature", "invalid_signature")
                .body(payload)
                .when()
                .post("/api/webhooks/stripe")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(2)
    void testWebhook_RejectMissingSignature() {
        String payload =
                """
            {
                "id": "evt_test_missing_sig",
                "type": "checkout.session.completed",
                "data": {}
            }
            """;

        given().contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/webhooks/stripe")
                .then()
                .statusCode(400);
    }

    // ============================================================================
    // TEST: checkout.session.completed Event Processing
    // ============================================================================

    @Test
    @Order(3)
    void testWebhook_CheckoutSessionCompleted_UpdatesOrderStatus() throws Exception {
        // Verify order is PENDING (fetch fresh from DB)
        CalendarOrder order = CalendarOrder.findById(testOrder.id);
        assertEquals(CalendarOrder.STATUS_PENDING, order.status);

        // Create webhook payload with proper Stripe event structure including metadata
        String payload =
                String.format(
                        """
            {
                "id": "evt_test_checkout_success",
                "object": "event",
                "api_version": "2024-11-20.acacia",
                "created": %d,
                "type": "checkout.session.completed",
                "data": {
                    "object": {
                        "id": "%s",
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
            """,
                        System.currentTimeMillis() / 1000,
                        TEST_SESSION_ID,
                        TEST_PAYMENT_INTENT_ID,
                        testOrder.id.toString());

        String signature = generateStripeSignature(payload);

        // Send webhook
        given().contentType(ContentType.JSON)
                .header("Stripe-Signature", signature)
                .body(payload)
                .when()
                .post("/api/webhooks/stripe")
                .then()
                .statusCode(200);

        // Clear persistence context to ensure fresh read from database
        entityManager.clear();

        // Verify order updated to PAID (fetch fresh from DB)
        order = CalendarOrder.findById(testOrder.id);
        assertEquals(CalendarOrder.STATUS_PAID, order.status);
        assertNotNull(order.paidAt);
        assertTrue(order.notes.contains("Payment succeeded"));
    }

    @Test
    @Order(4)
    void testWebhook_CheckoutSessionCompleted_EnqueuesEmailJob() throws Exception {
        // Clear any existing delayed jobs - use programmatic transaction
        QuarkusTransaction.requiringNew()
                .run(
                        () -> {
                            DelayedJob.delete("actorId", testOrder.id.toString());
                        });

        String payload =
                String.format(
                        """
            {
                "id": "evt_test_checkout_email",
                "object": "event",
                "api_version": "2024-11-20.acacia",
                "created": %d,
                "type": "checkout.session.completed",
                "data": {
                    "object": {
                        "id": "%s",
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
            """,
                        System.currentTimeMillis() / 1000,
                        TEST_SESSION_ID,
                        TEST_PAYMENT_INTENT_ID,
                        testOrder.id.toString());

        String signature = generateStripeSignature(payload);

        // Send webhook
        given().contentType(ContentType.JSON)
                .header("Stripe-Signature", signature)
                .body(payload)
                .when()
                .post("/api/webhooks/stripe")
                .then()
                .statusCode(200);

        // Clear persistence context to ensure fresh read from database
        entityManager.clear();

        // Verify email job enqueued
        List<DelayedJob> jobs = DelayedJob.find("actorId", testOrder.id.toString()).list();
        assertEquals(1, jobs.size(), "Should enqueue exactly one email job");

        DelayedJob emailJob = jobs.get(0);
        assertEquals(testOrder.id.toString(), emailJob.actorId);
        assertNotNull(emailJob.queueName);
        assertFalse(emailJob.complete);
    }

    // ============================================================================
    // TEST: Idempotent Webhook Processing (Duplicate Delivery)
    // ============================================================================

    @Test
    @Order(5)
    void testWebhook_IdempotentProcessing_NoDuplicateUpdates() throws Exception {
        // Clear delayed jobs - use programmatic transaction
        QuarkusTransaction.requiringNew()
                .run(
                        () -> {
                            DelayedJob.delete("actorId", testOrder.id.toString());
                        });

        String payload =
                String.format(
                        """
            {
                "id": "evt_test_idempotent",
                "object": "event",
                "api_version": "2024-11-20.acacia",
                "created": %d,
                "type": "checkout.session.completed",
                "data": {
                    "object": {
                        "id": "%s",
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
            """,
                        System.currentTimeMillis() / 1000,
                        TEST_SESSION_ID,
                        TEST_PAYMENT_INTENT_ID,
                        testOrder.id.toString());

        String signature = generateStripeSignature(payload);

        // Send webhook first time
        given().contentType(ContentType.JSON)
                .header("Stripe-Signature", signature)
                .body(payload)
                .when()
                .post("/api/webhooks/stripe")
                .then()
                .statusCode(200);

        // Clear persistence context to ensure fresh read from database
        entityManager.clear();

        // Verify order is PAID
        CalendarOrder order = CalendarOrder.findById(testOrder.id);
        assertEquals(CalendarOrder.STATUS_PAID, order.status);

        // Verify one email job enqueued
        List<DelayedJob> jobsAfterFirst =
                DelayedJob.find("actorId", testOrder.id.toString()).list();
        assertEquals(1, jobsAfterFirst.size());

        // Send SAME webhook again (duplicate delivery)
        given().contentType(ContentType.JSON)
                .header("Stripe-Signature", signature)
                .body(payload)
                .when()
                .post("/api/webhooks/stripe")
                .then()
                .statusCode(200);

        // Clear persistence context to ensure fresh read from database
        entityManager.clear();

        // Verify order still PAID (no change)
        order = CalendarOrder.findById(testOrder.id);
        assertEquals(CalendarOrder.STATUS_PAID, order.status);

        // Verify NO duplicate email job enqueued (still only 1 job)
        List<DelayedJob> jobsAfterSecond =
                DelayedJob.find("actorId", testOrder.id.toString()).list();
        assertEquals(1, jobsAfterSecond.size(), "Should NOT create duplicate email job");
    }

    // ============================================================================
    // TEST: payment_intent.succeeded Event Processing
    // ============================================================================

    @Test
    @Order(6)
    void testWebhook_PaymentIntentSucceeded_UpdatesOrderStatus() throws Exception {
        // Create new order for this test in a separate transaction
        // Use programmatic transaction to ensure data is committed before HTTP call
        UUID orderId =
                QuarkusTransaction.requiringNew()
                        .call(
                                () -> {
                                    try {
                                        JsonNode testAddress =
                                                objectMapper.readTree(
                                                        """
                    {
                        "street": "123 Test St",
                        "city": "Test City",
                        "state": "CA",
                        "postalCode": "12345",
                        "country": "US"
                    }
                    """);
                                        CalendarOrder order =
                                                orderService.createOrder(
                                                        testUser,
                                                        testCalendar,
                                                        1,
                                                        new BigDecimal("29.99"),
                                                        testAddress);
                                        order.stripePaymentIntentId = "pi_test_direct_flow";
                                        order.persist();
                                        return order.id;
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                });

        String payload =
                String.format(
                        """
            {
                "id": "evt_test_payment_intent",
                "object": "event",
                "api_version": "2024-11-20.acacia",
                "created": %d,
                "type": "payment_intent.succeeded",
                "data": {
                    "object": {
                        "id": "pi_test_direct_flow",
                        "object": "payment_intent",
                        "latest_charge": "%s",
                        "status": "succeeded"
                    }
                }
            }
            """,
                        System.currentTimeMillis() / 1000, TEST_CHARGE_ID);

        String signature = generateStripeSignature(payload);

        // Send webhook
        given().contentType(ContentType.JSON)
                .header("Stripe-Signature", signature)
                .body(payload)
                .when()
                .post("/api/webhooks/stripe")
                .then()
                .statusCode(200);

        // Clear persistence context to ensure fresh read from database
        entityManager.clear();

        // Verify order updated
        CalendarOrder order = CalendarOrder.findById(orderId);
        assertEquals(CalendarOrder.STATUS_PAID, order.status);
        assertEquals(TEST_CHARGE_ID, order.stripeChargeId);
        assertNotNull(order.paidAt);

        // Cleanup
        QuarkusTransaction.requiringNew()
                .run(
                        () -> {
                            DelayedJob.delete("actorId", orderId.toString());
                            CalendarOrder.deleteById(orderId);
                        });
    }

    // ============================================================================
    // TEST: charge.refunded Event Processing
    // ============================================================================

    @Test
    @Order(7)
    void testWebhook_ChargeRefunded_UpdatesOrderNotes() throws Exception {
        // Mark order as PAID first - use programmatic transaction to commit before HTTP call
        QuarkusTransaction.requiringNew()
                .run(
                        () -> {
                            CalendarOrder order = CalendarOrder.findById(testOrder.id);
                            order.status = CalendarOrder.STATUS_PAID;
                            order.stripeChargeId = TEST_CHARGE_ID;
                            order.persist();
                        });

        String payload =
                String.format(
                        """
            {
                "id": "evt_test_refund",
                "object": "event",
                "api_version": "2024-11-20.acacia",
                "created": %d,
                "type": "charge.refunded",
                "data": {
                    "object": {
                        "id": "%s",
                        "object": "charge",
                        "refunds": {
                            "object": "list",
                            "data": [
                                {
                                    "id": "re_test_123",
                                    "object": "refund",
                                    "amount": 1999,
                                    "status": "succeeded"
                                }
                            ]
                        }
                    }
                }
            }
            """,
                        System.currentTimeMillis() / 1000, TEST_CHARGE_ID);

        String signature = generateStripeSignature(payload);

        // Send webhook
        given().contentType(ContentType.JSON)
                .header("Stripe-Signature", signature)
                .body(payload)
                .when()
                .post("/api/webhooks/stripe")
                .then()
                .statusCode(200);

        // Clear persistence context to ensure fresh read from database
        entityManager.clear();

        // Verify refund recorded in order notes
        CalendarOrder updatedOrder = CalendarOrder.findById(testOrder.id);
        assertNotNull(updatedOrder.notes);
        assertTrue(updatedOrder.notes.contains("Refund received"));
        assertTrue(updatedOrder.notes.contains("re_test_123"));
        assertTrue(updatedOrder.notes.contains("19.99")); // Amount in dollars
    }

    @Test
    @Order(8)
    void testWebhook_ChargeRefunded_IdempotentProcessing() throws Exception {
        // Mark order as PAID - use programmatic transaction to commit before HTTP call
        QuarkusTransaction.requiringNew()
                .run(
                        () -> {
                            CalendarOrder order = CalendarOrder.findById(testOrder.id);
                            order.status = CalendarOrder.STATUS_PAID;
                            order.stripeChargeId = TEST_CHARGE_ID;
                            order.persist();
                        });

        String payload =
                String.format(
                        """
            {
                "id": "evt_test_refund_idempotent",
                "object": "event",
                "api_version": "2024-11-20.acacia",
                "created": %d,
                "type": "charge.refunded",
                "data": {
                    "object": {
                        "id": "%s",
                        "object": "charge",
                        "refunds": {
                            "object": "list",
                            "data": [
                                {
                                    "id": "re_test_idempotent",
                                    "object": "refund",
                                    "amount": 1999,
                                    "status": "succeeded"
                                }
                            ]
                        }
                    }
                }
            }
            """,
                        System.currentTimeMillis() / 1000, TEST_CHARGE_ID);

        String signature = generateStripeSignature(payload);

        // Send webhook first time
        given().contentType(ContentType.JSON)
                .header("Stripe-Signature", signature)
                .body(payload)
                .when()
                .post("/api/webhooks/stripe")
                .then()
                .statusCode(200);

        // Clear persistence context to ensure fresh read from database
        entityManager.clear();

        CalendarOrder orderAfterFirst = CalendarOrder.findById(testOrder.id);
        String notesAfterFirst = orderAfterFirst.notes;
        int refundCount1 = countOccurrences(notesAfterFirst, "re_test_idempotent");
        assertEquals(1, refundCount1, "Refund should be recorded once");

        // Send SAME webhook again (duplicate delivery)
        given().contentType(ContentType.JSON)
                .header("Stripe-Signature", signature)
                .body(payload)
                .when()
                .post("/api/webhooks/stripe")
                .then()
                .statusCode(200);

        // Clear persistence context to ensure fresh read from database
        entityManager.clear();

        // Verify refund NOT duplicated in notes
        CalendarOrder orderAfterSecond = CalendarOrder.findById(testOrder.id);
        String notesAfterSecond = orderAfterSecond.notes;
        int refundCount2 = countOccurrences(notesAfterSecond, "re_test_idempotent");
        assertEquals(1, refundCount2, "Refund should still only appear once (idempotent)");
    }

    // ============================================================================
    // TEST: Unknown Event Type Handling
    // ============================================================================

    @Test
    @Order(9)
    void testWebhook_UnknownEventType_ReturnsSuccess() throws Exception {
        String payload =
                """
            {
                "id": "evt_test_unknown",
                "type": "payment_intent.created",
                "data": {
                    "object": {
                        "id": "pi_test_unknown"
                    }
                }
            }
            """;

        String signature = generateStripeSignature(payload);

        // Stripe best practice: return 200 for unknown event types
        given().contentType(ContentType.JSON)
                .header("Stripe-Signature", signature)
                .body(payload)
                .when()
                .post("/api/webhooks/stripe")
                .then()
                .statusCode(200);
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    private int countOccurrences(String text, String substring) {
        if (text == null || substring == null) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }
}
