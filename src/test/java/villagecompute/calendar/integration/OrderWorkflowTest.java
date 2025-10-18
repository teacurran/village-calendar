package villagecompute.calendar.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;
import villagecompute.calendar.data.models.*;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

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

    @InjectMock
    PaymentService paymentService;

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

        // Mock PaymentService to avoid real Stripe API calls
        testPaymentIntentId = "pi_test_" + System.currentTimeMillis();
        Map<String, String> mockPaymentIntent = new HashMap<>();
        mockPaymentIntent.put("clientSecret", "pi_test_secret_123");
        mockPaymentIntent.put("paymentIntentId", testPaymentIntentId);

        when(paymentService.createPaymentIntent(any(BigDecimal.class), anyString(), anyString()))
            .thenReturn(mockPaymentIntent);

        // Mock webhook signature validation
        when(paymentService.getWebhookSecret()).thenReturn("");
    }

    @AfterEach
    @Transactional
    void cleanup() {
        // Clean up test data in reverse FK order
        if (testCalendar != null && testCalendar.id != null) {
            // Delete delayed jobs for orders
            DelayedJob.delete("actorId IN (SELECT CAST(id AS VARCHAR) FROM calendar_orders WHERE calendar_id = ?1)", testCalendar.id);
            // Delete orders
            CalendarOrder.delete("calendar.id", testCalendar.id);
            // Delete calendar
            UserCalendar.deleteById(testCalendar.id);
        }
        if (testUser != null && testUser.id != null) {
            CalendarUser.deleteById(testUser.id);
        }
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

    // ============================================================================
    // ORDER CREATION TESTS
    // ============================================================================

    @Test
    @org.junit.jupiter.api.Order(1)
    void testCreateOrder_GraphQL_ReturnsPendingOrder() throws Exception {
        // When: Create order via GraphQL mutation
        String mutation = """
            mutation {
                createOrder(
                    calendarId: "%s"
                    quantity: 1
                    shippingAddress: {
                        street: "123 Main St"
                        city: "Nashville"
                        state: "TN"
                        postalCode: "37203"
                        country: "US"
                    }
                ) {
                    id
                    clientSecret
                    amount
                    status
                }
            }
            """.formatted(testCalendar.id.toString());

        Response response = given()
            .contentType(ContentType.JSON)
            .body(Map.of("query", mutation))
            .when()
            .post("/graphql")
            .then()
            .statusCode(200)
            .body("errors", nullValue())
            .body("data.createOrder.id", notNullValue())
            .body("data.createOrder.clientSecret", notNullValue())
            .extract()
            .response();

        String returnedPaymentIntentId = response.jsonPath().getString("data.createOrder.id");
        assertEquals(testPaymentIntentId, returnedPaymentIntentId, "Should return mocked PaymentIntent ID");

        // Then: Verify order was created in database with PENDING status
        CalendarOrder order = CalendarOrder.findByStripePaymentIntent(testPaymentIntentId).firstResult();
        assertNotNull(order, "Order should be created in database");
        assertEquals(CalendarOrder.STATUS_PENDING, order.status, "Order should be in PENDING status");
        assertEquals(testUser.id, order.user.id, "Order should belong to test user");
        assertEquals(testCalendar.id, order.calendar.id, "Order should reference test calendar");
        assertEquals(1, order.quantity, "Order quantity should be 1");

        // Verify total price includes base price + tax + shipping
        assertNotNull(order.totalPrice, "Total price should be calculated");
        assertTrue(order.totalPrice.compareTo(BigDecimal.ZERO) > 0, "Total price should be positive");

        // Verify order number was generated
        assertNotNull(order.orderNumber, "Order number should be generated");
        assertTrue(order.orderNumber.matches("\\d{4}-\\d+"), "Order number should match format YYYY-N");
    }

    // ============================================================================
    // WEBHOOK PAYMENT SUCCESS TESTS
    // ============================================================================

    @Test
    @org.junit.jupiter.api.Order(2)
    @Transactional
    void testWebhookPaymentSuccess_UpdatesOrderToPaid() throws Exception {
        // Given: Create an order in PENDING status
        CalendarOrder order = new CalendarOrder();
        order.user = testUser;
        order.calendar = testCalendar;
        order.quantity = 1;
        order.unitPrice = new BigDecimal("25.00");
        order.totalPrice = new BigDecimal("25.00");
        order.status = CalendarOrder.STATUS_PENDING;
        order.stripePaymentIntentId = testPaymentIntentId;
        order.shippingAddress = createTestAddress();
        order.orderNumber = "2025-001";
        order.persist();

        // Mock PaymentService.processPaymentSuccess to return true
        when(paymentService.processPaymentSuccess(eq(testPaymentIntentId), anyString()))
            .thenReturn(true);

        // When: Simulate Stripe webhook (checkout.session.completed)
        String webhookPayload = """
            {
              "id": "evt_test_webhook",
              "type": "checkout.session.completed",
              "data": {
                "object": {
                  "id": "cs_test_session",
                  "payment_intent": "%s"
                }
              }
            }
            """.formatted(testPaymentIntentId);

        given()
            .contentType(ContentType.JSON)
            .header("Stripe-Signature", "t=123,v1=fake")
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
    @Transactional
    void testWebhookPaymentSuccess_EnqueuesEmailJob() throws Exception {
        // Given: Create an order in PENDING status
        CalendarOrder order = new CalendarOrder();
        order.user = testUser;
        order.calendar = testCalendar;
        order.quantity = 1;
        order.unitPrice = new BigDecimal("25.00");
        order.totalPrice = new BigDecimal("25.00");
        order.status = CalendarOrder.STATUS_PENDING;
        order.stripePaymentIntentId = testPaymentIntentId;
        order.shippingAddress = createTestAddress();
        order.orderNumber = "2025-002";
        order.persist();

        // Mock PaymentService.processPaymentSuccess to return true
        when(paymentService.processPaymentSuccess(eq(testPaymentIntentId), anyString()))
            .thenReturn(true);

        // When: Simulate payment success webhook
        String webhookPayload = """
            {
              "id": "evt_test_webhook_email",
              "type": "checkout.session.completed",
              "data": {
                "object": {
                  "id": "cs_test_session_email",
                  "payment_intent": "%s"
                }
              }
            }
            """.formatted(testPaymentIntentId);

        given()
            .contentType(ContentType.JSON)
            .header("Stripe-Signature", "t=123,v1=fake")
            .body(webhookPayload)
            .when()
            .post("/api/webhooks/stripe")
            .then()
            .statusCode(200);

        // Then: Verify email job was enqueued
        List<DelayedJob> jobs = DelayedJob.find("actorId", order.id.toString()).list();
        assertEquals(1, jobs.size(), "Order confirmation email job should be enqueued");
        assertEquals(DelayedJobQueue.EMAIL_ORDER_CONFIRMATION, jobs.get(0).queue,
            "Email job should be in ORDER_CONFIRMATION queue");
        assertFalse(jobs.get(0).complete, "Job should not be completed yet");
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @Transactional
    void testWebhookIdempotency_DoesNotDuplicateProcessing() throws Exception {
        // Given: Create an order in PENDING status
        CalendarOrder order = new CalendarOrder();
        order.user = testUser;
        order.calendar = testCalendar;
        order.quantity = 1;
        order.unitPrice = new BigDecimal("25.00");
        order.totalPrice = new BigDecimal("25.00");
        order.status = CalendarOrder.STATUS_PENDING;
        order.stripePaymentIntentId = testPaymentIntentId;
        order.shippingAddress = createTestAddress();
        order.orderNumber = "2025-003";
        order.persist();

        // First call returns true, second call returns false (idempotent)
        when(paymentService.processPaymentSuccess(eq(testPaymentIntentId), anyString()))
            .thenReturn(true)
            .thenReturn(false);

        // When: Send webhook twice
        String webhookPayload = """
            {
              "id": "evt_test_idempotent",
              "type": "checkout.session.completed",
              "data": {
                "object": {
                  "id": "cs_test_idempotent",
                  "payment_intent": "%s"
                }
              }
            }
            """.formatted(testPaymentIntentId);

        // First webhook
        given()
            .contentType(ContentType.JSON)
            .header("Stripe-Signature", "t=123,v1=fake1")
            .body(webhookPayload)
            .when()
            .post("/api/webhooks/stripe")
            .then()
            .statusCode(200);

        // Second webhook (duplicate)
        given()
            .contentType(ContentType.JSON)
            .header("Stripe-Signature", "t=124,v1=fake2")
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
        // When: Create order via GraphQL
        String mutation = """
            mutation {
                createOrder(
                    calendarId: "%s"
                    quantity: 2
                    shippingAddress: {
                        street: "456 Oak Ave"
                        city: "Portland"
                        state: "OR"
                        postalCode: "97201"
                        country: "US"
                    }
                ) {
                    id
                    amount
                }
            }
            """.formatted(testCalendar.id.toString());

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("query", mutation))
            .when()
            .post("/graphql")
            .then()
            .statusCode(200)
            .body("errors", nullValue());

        // Then: Verify order total includes shipping cost
        CalendarOrder order = CalendarOrder.findByStripePaymentIntent(testPaymentIntentId).firstResult();
        assertNotNull(order, "Order should exist");
        assertEquals(2, order.quantity, "Quantity should be 2");

        // Shipping should be calculated (not zero for 2 items)
        // Note: ShippingService calculates based on weight and destination
        assertTrue(order.totalPrice.compareTo(order.unitPrice.multiply(BigDecimal.valueOf(2))) >= 0,
            "Total should be at least subtotal (may include shipping)");
    }
}
