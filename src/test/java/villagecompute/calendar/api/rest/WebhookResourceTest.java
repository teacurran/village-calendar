package villagecompute.calendar.api.rest;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static villagecompute.calendar.util.MimeTypes.HEADER_STRIPE_SIGNATURE;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.data.models.CalendarOrderItem;
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
 * Additional unit / integration tests for {@link WebhookResource} that complement {@link StripeWebhookControllerTest}.
 * These tests focus on previously uncovered code paths in the resource: invalid payloads, blank signature headers,
 * payment_intent.payment_failed event handling, edge cases (missing IDs, missing order, missing refund data) and
 * idempotency for payment_intent.succeeded.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WebhookResourceTest {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    CalendarService calendarService;

    @Inject
    OrderService orderService;

    @Inject
    EntityManager entityManager;

    @ConfigProperty(
            name = "stripe.webhook.secret")
    String webhookSecret;

    private CalendarUser testUser;
    private CalendarTemplate testTemplate;
    private UserCalendar testCalendar;
    private CalendarOrder testOrder;

    private static final String TEST_PAYMENT_INTENT_ID = "pi_test_webhook_resource_" + System.nanoTime();
    private static final String TEST_CHARGE_ID = "ch_test_webhook_resource_" + System.nanoTime();

    @BeforeEach
    void setup() {
        QuarkusTransaction.requiringNew().run(() -> {
            try {
                testUser = new CalendarUser();
                testUser.oauthProvider = "GOOGLE";
                testUser.oauthSubject = "wrt-test-" + System.nanoTime();
                testUser.email = "wrt-test-" + System.nanoTime() + "@example.com";
                testUser.displayName = "WebhookResourceTest User";
                testUser.persist();

                testTemplate = new CalendarTemplate();
                testTemplate.name = "WebhookResourceTest Template";
                testTemplate.description = "Template for WebhookResourceTest";
                testTemplate.isActive = true;
                testTemplate.isFeatured = false;
                testTemplate.displayOrder = 1;
                testTemplate.configuration = objectMapper.readTree("""
                        {"theme": "modern", "colorScheme": "blue"}
                        """);
                testTemplate.persist();

                testCalendar = calendarService.createCalendar("WRT Test Calendar", 2025, testTemplate.id, null, true,
                        testUser, null);

                JsonNode shippingAddress = objectMapper.readTree("""
                        {
                            "line1": "123 Test St",
                            "city": "Test City",
                            "state": "CA",
                            "postalCode": "12345",
                            "country": "US"
                        }
                        """);

                testOrder = orderService.createOrder(testUser, testCalendar, 1, new BigDecimal("19.99"),
                        shippingAddress);
                testOrder.stripePaymentIntentId = TEST_PAYMENT_INTENT_ID;
                testOrder.persist();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to set up WebhookResourceTest fixtures", e);
            }
        });
    }

    @AfterEach
    @Transactional
    void cleanup() {
        if (testUser != null && testUser.id != null) {
            DelayedJob.delete("actorId = ?1", testOrder != null && testOrder.id != null ? testOrder.id.toString() : "");
            CalendarOrderItem.delete("order.id in (select o.id from CalendarOrder o where o.user.id = ?1)",
                    testUser.id);
            CalendarOrder.delete("user.id", testUser.id);
            UserCalendar.delete("user.id", testUser.id);
            CalendarUser.deleteById(testUser.id);
        }
        if (testTemplate != null && testTemplate.id != null) {
            CalendarTemplate.deleteById(testTemplate.id);
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
    // Signature validation edge cases
    // ============================================================================

    @Test
    @Order(1)
    void testWebhook_BlankSignatureHeader_Returns400() {
        String payload = """
                {"id": "evt_blank_sig", "type": "checkout.session.completed", "data": {}}
                """;

        given().contentType(ContentType.JSON).header(HEADER_STRIPE_SIGNATURE, "   ").body(payload).when()
                .post("/api/webhooks/stripe").then().statusCode(400);
    }

    @Test
    @Order(2)
    void testWebhook_MalformedSignatureFormat_Returns400() {
        String payload = """
                {"id": "evt_malformed_sig", "type": "checkout.session.completed", "data": {}}
                """;

        // Missing v1 segment - looks like a header but isn't valid
        given().contentType(ContentType.JSON).header(HEADER_STRIPE_SIGNATURE, "t=123,xx=abc").body(payload).when()
                .post("/api/webhooks/stripe").then().statusCode(400);
    }

    // ============================================================================
    // payment_intent.payment_failed branch
    // ============================================================================

    @Test
    @Order(3)
    void testWebhook_PaymentIntentFailed_AppendsFailureNote() throws Exception {
        String payload = String.format("""
                {
                    "id": "evt_pi_failed",
                    "object": "event",
                    "api_version": "2024-11-20.acacia",
                    "created": %d,
                    "type": "payment_intent.payment_failed",
                    "data": {
                        "object": {
                            "id": "%s",
                            "object": "payment_intent",
                            "status": "requires_payment_method",
                            "last_payment_error": {
                                "code": "card_declined",
                                "message": "Your card was declined."
                            }
                        }
                    }
                }
                """, System.currentTimeMillis() / 1000, TEST_PAYMENT_INTENT_ID);

        String signature = generateStripeSignature(payload);

        given().contentType(ContentType.JSON).header(HEADER_STRIPE_SIGNATURE, signature).body(payload).when()
                .post("/api/webhooks/stripe").then().statusCode(200);

        entityManager.clear();

        CalendarOrder order = CalendarOrder.findById(testOrder.id);
        assertNotNull(order.notes, "Notes should be populated after payment failure");
        assertTrue(order.notes.contains("Payment failed"), "Notes should record the failure");
        assertTrue(order.notes.contains("Your card was declined."), "Notes should include the Stripe failure message");
        // Status should remain PENDING - the resource only records a note for failures.
        assertEquals(CalendarOrder.STATUS_PENDING, order.status);
        assertNull(order.paidAt);
    }

    @Test
    @Order(4)
    void testWebhook_PaymentIntentFailed_MissingError_UsesUnknownErrorMessage() throws Exception {
        String payload = String.format("""
                {
                    "id": "evt_pi_failed_no_err",
                    "object": "event",
                    "api_version": "2024-11-20.acacia",
                    "created": %d,
                    "type": "payment_intent.payment_failed",
                    "data": {
                        "object": {
                            "id": "%s",
                            "object": "payment_intent",
                            "status": "requires_payment_method"
                        }
                    }
                }
                """, System.currentTimeMillis() / 1000, TEST_PAYMENT_INTENT_ID);

        String signature = generateStripeSignature(payload);

        given().contentType(ContentType.JSON).header(HEADER_STRIPE_SIGNATURE, signature).body(payload).when()
                .post("/api/webhooks/stripe").then().statusCode(200);

        entityManager.clear();

        CalendarOrder order = CalendarOrder.findById(testOrder.id);
        assertNotNull(order.notes);
        assertTrue(order.notes.contains("Unknown error"),
                "Should fall back to 'Unknown error' when last_payment_error is missing");
    }

    @Test
    @Order(5)
    void testWebhook_PaymentIntentFailed_UnknownOrder_Returns200WithoutChange() throws Exception {
        // Use a payment intent ID that doesn't match any order
        String unknownPiId = "pi_test_unmapped_" + System.nanoTime();
        String payload = String.format("""
                {
                    "id": "evt_pi_failed_unknown_order",
                    "object": "event",
                    "api_version": "2024-11-20.acacia",
                    "created": %d,
                    "type": "payment_intent.payment_failed",
                    "data": {
                        "object": {
                            "id": "%s",
                            "object": "payment_intent",
                            "last_payment_error": {"message": "Card declined"}
                        }
                    }
                }
                """, System.currentTimeMillis() / 1000, unknownPiId);

        String signature = generateStripeSignature(payload);

        // Resource should still acknowledge the webhook with 200 (no order to update is OK)
        given().contentType(ContentType.JSON).header(HEADER_STRIPE_SIGNATURE, signature).body(payload).when()
                .post("/api/webhooks/stripe").then().statusCode(200);

        entityManager.clear();
        // Our test order should be untouched
        CalendarOrder order = CalendarOrder.findById(testOrder.id);
        assertNull(order.notes, "Existing order should not be modified");
        assertEquals(CalendarOrder.STATUS_PENDING, order.status);
    }

    // ============================================================================
    // payment_intent.succeeded - missing order branch (Checkout flow)
    // ============================================================================

    @Test
    @Order(6)
    void testWebhook_PaymentIntentSucceeded_OrderNotFound_StillReturns200() throws Exception {
        String unknownPiId = "pi_test_no_order_" + System.nanoTime();
        String payload = String.format("""
                {
                    "id": "evt_pi_succeeded_no_order",
                    "object": "event",
                    "api_version": "2024-11-20.acacia",
                    "created": %d,
                    "type": "payment_intent.succeeded",
                    "data": {
                        "object": {
                            "id": "%s",
                            "object": "payment_intent",
                            "latest_charge": "ch_no_order",
                            "status": "succeeded"
                        }
                    }
                }
                """, System.currentTimeMillis() / 1000, unknownPiId);

        String signature = generateStripeSignature(payload);

        given().contentType(ContentType.JSON).header(HEADER_STRIPE_SIGNATURE, signature).body(payload).when()
                .post("/api/webhooks/stripe").then().statusCode(200);
    }

    // ============================================================================
    // charge.refunded edge cases
    // ============================================================================

    @Test
    @Order(7)
    void testWebhook_ChargeRefunded_NoRefundData_Returns200Quietly() throws Exception {
        QuarkusTransaction.requiringNew().run(() -> {
            CalendarOrder order = CalendarOrder.findById(testOrder.id);
            order.status = CalendarOrder.STATUS_PAID;
            order.stripeChargeId = TEST_CHARGE_ID;
            order.persist();
        });

        // No refunds.data array - the resource should still return 200 and not modify the order
        String payload = String.format("""
                {
                    "id": "evt_charge_no_refund_data",
                    "object": "event",
                    "api_version": "2024-11-20.acacia",
                    "created": %d,
                    "type": "charge.refunded",
                    "data": {
                        "object": {
                            "id": "%s",
                            "object": "charge"
                        }
                    }
                }
                """, System.currentTimeMillis() / 1000, TEST_CHARGE_ID);

        String signature = generateStripeSignature(payload);

        given().contentType(ContentType.JSON).header(HEADER_STRIPE_SIGNATURE, signature).body(payload).when()
                .post("/api/webhooks/stripe").then().statusCode(200);

        entityManager.clear();
        CalendarOrder order = CalendarOrder.findById(testOrder.id);
        // Notes should be null or not contain refund text
        if (order.notes != null) {
            assertTrue(!order.notes.contains("Refund received"),
                    "No refund should be recorded when refund data is absent");
        }
    }

    @Test
    @Order(8)
    void testWebhook_ChargeRefunded_EmptyRefundDataArray_Returns200() throws Exception {
        QuarkusTransaction.requiringNew().run(() -> {
            CalendarOrder order = CalendarOrder.findById(testOrder.id);
            order.status = CalendarOrder.STATUS_PAID;
            order.stripeChargeId = TEST_CHARGE_ID;
            order.persist();
        });

        String payload = String.format("""
                {
                    "id": "evt_charge_empty_refund_data",
                    "object": "event",
                    "api_version": "2024-11-20.acacia",
                    "created": %d,
                    "type": "charge.refunded",
                    "data": {
                        "object": {
                            "id": "%s",
                            "object": "charge",
                            "refunds": {"object": "list", "data": []}
                        }
                    }
                }
                """, System.currentTimeMillis() / 1000, TEST_CHARGE_ID);

        String signature = generateStripeSignature(payload);

        given().contentType(ContentType.JSON).header(HEADER_STRIPE_SIGNATURE, signature).body(payload).when()
                .post("/api/webhooks/stripe").then().statusCode(200);

        entityManager.clear();
        CalendarOrder order = CalendarOrder.findById(testOrder.id);
        if (order.notes != null) {
            assertTrue(!order.notes.contains("Refund received"),
                    "No refund should be recorded when data array is empty");
        }
    }

    // ============================================================================
    // Unknown event types - default branch in switch
    // ============================================================================

    @Test
    @Order(9)
    void testWebhook_AccountUpdated_DefaultBranch_Returns200() throws Exception {
        // account.updated is a real Stripe event type that this resource doesn't handle
        String payload = """
                {
                    "id": "evt_account_updated_test",
                    "object": "event",
                    "api_version": "2024-11-20.acacia",
                    "type": "account.updated",
                    "data": {
                        "object": {
                            "id": "acct_test_unknown",
                            "object": "account"
                        }
                    }
                }
                """;
        String signature = generateStripeSignature(payload);

        given().contentType(ContentType.JSON).header(HEADER_STRIPE_SIGNATURE, signature).body(payload).when()
                .post("/api/webhooks/stripe").then().statusCode(200);
    }

    @Test
    @Order(10)
    void testWebhook_InvoiceCreated_DefaultBranch_Returns200() throws Exception {
        String payload = """
                {
                    "id": "evt_invoice_test",
                    "object": "event",
                    "api_version": "2024-11-20.acacia",
                    "type": "invoice.created",
                    "data": {
                        "object": {
                            "id": "in_test_xyz",
                            "object": "invoice"
                        }
                    }
                }
                """;
        String signature = generateStripeSignature(payload);

        given().contentType(ContentType.JSON).header(HEADER_STRIPE_SIGNATURE, signature).body(payload).when()
                .post("/api/webhooks/stripe").then().statusCode(200);
    }

    // ============================================================================
    // Idempotency for payment_intent.succeeded
    // ============================================================================

    @Test
    @Order(11)
    void testWebhook_PaymentIntentSucceeded_DuplicateDelivery_Idempotent() throws Exception {
        // Pre-mark a fresh order in a new committed transaction so it is visible to RestAssured
        UUID orderId = QuarkusTransaction.requiringNew().call(() -> {
            try {
                JsonNode shippingAddress = objectMapper.readTree("""
                        {"line1": "1 Idempotent Way", "city": "Town", "state": "CA",
                         "postalCode": "00000", "country": "US"}
                        """);
                CalendarOrder order = orderService.createOrder(testUser, testCalendar, 1, new BigDecimal("12.34"),
                        shippingAddress);
                order.stripePaymentIntentId = "pi_test_idem_" + System.nanoTime();
                order.persist();
                return order.id;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create idempotency-test order", e);
            }
        });

        CalendarOrder created = CalendarOrder.findById(orderId);
        String paymentIntentId = created.stripePaymentIntentId;
        String chargeId = "ch_idem_" + System.nanoTime();

        String payload = String.format("""
                {
                    "id": "evt_pi_idem",
                    "object": "event",
                    "api_version": "2024-11-20.acacia",
                    "created": %d,
                    "type": "payment_intent.succeeded",
                    "data": {
                        "object": {
                            "id": "%s",
                            "object": "payment_intent",
                            "latest_charge": "%s",
                            "status": "succeeded"
                        }
                    }
                }
                """, System.currentTimeMillis() / 1000, paymentIntentId, chargeId);
        String signature = generateStripeSignature(payload);

        // First delivery
        given().contentType(ContentType.JSON).header(HEADER_STRIPE_SIGNATURE, signature).body(payload).when()
                .post("/api/webhooks/stripe").then().statusCode(200);

        entityManager.clear();
        CalendarOrder afterFirst = CalendarOrder.findById(orderId);
        assertEquals(CalendarOrder.STATUS_PAID, afterFirst.status);
        Instant firstPaidAt = afterFirst.paidAt;
        assertNotNull(firstPaidAt);
        assertEquals(chargeId, afterFirst.stripeChargeId);

        // Second delivery (duplicate)
        given().contentType(ContentType.JSON).header(HEADER_STRIPE_SIGNATURE, signature).body(payload).when()
                .post("/api/webhooks/stripe").then().statusCode(200);

        entityManager.clear();
        CalendarOrder afterSecond = CalendarOrder.findById(orderId);
        assertEquals(CalendarOrder.STATUS_PAID, afterSecond.status);
        // paidAt should not have been overwritten on the idempotent second delivery
        assertEquals(firstPaidAt, afterSecond.paidAt, "paidAt must not change on idempotent retry");

        // Cleanup the dedicated test order
        QuarkusTransaction.requiringNew().run(() -> {
            DelayedJob.delete("actorId", orderId.toString());
            CalendarOrder.deleteById(orderId);
        });
    }

    // ============================================================================
    // Invalid / malformed payloads (valid signature, garbage body)
    // ============================================================================

    @Test
    @Order(12)
    void testWebhook_EmptyBody_WithValidSignature_Returns400() throws Exception {
        String payload = "";
        String signature = generateStripeSignature(payload);

        given().contentType(ContentType.JSON).header(HEADER_STRIPE_SIGNATURE, signature).body(payload).when()
                .post("/api/webhooks/stripe").then().statusCode(400);
    }

    @Test
    @Order(13)
    void testWebhook_NonJsonBody_WithValidSignature_Returns400() throws Exception {
        String payload = "this is not json at all";
        String signature = generateStripeSignature(payload);

        given().contentType(ContentType.JSON).header(HEADER_STRIPE_SIGNATURE, signature).body(payload).when()
                .post("/api/webhooks/stripe").then().statusCode(400);
    }
}
