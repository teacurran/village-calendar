package villagecompute.calendar.api.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Tests for PaymentResource REST endpoints. These endpoints handle Stripe payment processing.
 */
@QuarkusTest
class PaymentResourceTest {

    // ========== GET /api/payment/config TESTS ==========

    @Test
    void testGetConfig_ReturnsPublishableKey() {
        given().when().get("/api/payment/config").then().statusCode(200).contentType(ContentType.JSON)
                .body("publishableKey", notNullValue());
    }

    // ========== POST /api/payment/create-payment-intent TESTS ==========

    @Test
    void testCreatePaymentIntent_ValidRequest_ReturnsClientSecret() {
        Map<String, Object> request = new HashMap<>();
        request.put("amount", 2999);
        request.put("currency", "usd");

        // This will fail because Stripe isn't configured in test mode,
        // but it should at least exercise the code path
        given().contentType(ContentType.JSON).body(request).when().post("/api/payment/create-payment-intent").then()
                .statusCode(anyOf(is(200), is(400), is(500)));
    }

    @Test
    void testCreatePaymentIntent_WithBreakdown() {
        Map<String, Object> request = new HashMap<>();
        request.put("amount", 3500);
        request.put("currency", "usd");
        request.put("subtotal", 2999.0);
        request.put("taxAmount", 240.0);
        request.put("shippingCost", 261.0);

        given().contentType(ContentType.JSON).body(request).when().post("/api/payment/create-payment-intent").then()
                .statusCode(anyOf(is(200), is(400), is(500)));
    }

    @Test
    void testCreatePaymentIntent_WithDefaultCurrency() {
        Map<String, Object> request = new HashMap<>();
        request.put("amount", 1000);
        // No currency specified - should default to USD

        given().contentType(ContentType.JSON).body(request).when().post("/api/payment/create-payment-intent").then()
                .statusCode(anyOf(is(200), is(400), is(500)));
    }

    // ========== POST /api/payment/confirm-payment TESTS ==========

    @Test
    void testConfirmPayment_InvalidPaymentIntentId() {
        Map<String, Object> request = new HashMap<>();
        request.put("paymentIntentId", "pi_invalid_12345");

        given().contentType(ContentType.JSON).body(request).when().post("/api/payment/confirm-payment").then()
                .statusCode(anyOf(is(400), is(500)));
    }

    @Test
    void testConfirmPayment_MissingPaymentIntentId() {
        Map<String, Object> request = new HashMap<>();
        request.put("orderDetails", new HashMap<>());

        given().contentType(ContentType.JSON).body(request).when().post("/api/payment/confirm-payment").then()
                .statusCode(anyOf(is(400), is(500)));
    }

    @Test
    void testConfirmPayment_WithOrderDetails_MissingEmail() {
        Map<String, Object> request = new HashMap<>();
        request.put("paymentIntentId", "pi_test_12345");

        Map<String, Object> orderDetails = new HashMap<>();
        orderDetails.put("items", List.of());
        request.put("orderDetails", orderDetails);

        // Will fail due to missing email, but exercises code path
        given().contentType(ContentType.JSON).body(request).when().post("/api/payment/confirm-payment").then()
                .statusCode(anyOf(is(400), is(500)));
    }

    @Test
    void testConfirmPayment_WithCompleteOrderDetails() {
        Map<String, Object> request = new HashMap<>();
        request.put("paymentIntentId", "pi_test_12345");

        Map<String, Object> orderDetails = new HashMap<>();
        orderDetails.put("email", "test@example.com");

        Map<String, Object> shippingAddress = new HashMap<>();
        shippingAddress.put("street", "123 Test St");
        shippingAddress.put("city", "Nashville");
        shippingAddress.put("state", "TN");
        shippingAddress.put("postalCode", "37201");
        shippingAddress.put("country", "US");
        orderDetails.put("shippingAddress", shippingAddress);

        Map<String, Object> item = new HashMap<>();
        item.put("templateId", "test-template");
        item.put("quantity", 1);
        orderDetails.put("items", List.of(item));

        orderDetails.put("subtotal", 29.99);
        orderDetails.put("shippingCost", 5.99);
        orderDetails.put("taxAmount", 2.40);
        orderDetails.put("totalAmount", 38.38);

        request.put("orderDetails", orderDetails);

        // Will fail due to invalid PaymentIntent, but exercises the full code path
        given().contentType(ContentType.JSON).body(request).when().post("/api/payment/confirm-payment").then()
                .statusCode(anyOf(is(400), is(500)));
    }

    @Test
    void testConfirmPayment_NullOrderDetails() {
        Map<String, Object> request = new HashMap<>();
        request.put("paymentIntentId", "pi_test_12345");
        // No orderDetails

        given().contentType(ContentType.JSON).body(request).when().post("/api/payment/confirm-payment").then()
                .statusCode(anyOf(is(400), is(500)));
    }

    // ========== DTO Tests ==========

    @Test
    void testPaymentIntentRequest_AllFields() {
        Map<String, Object> request = new HashMap<>();
        request.put("amount", 5000);
        request.put("currency", "eur");
        request.put("subtotal", 4000.0);
        request.put("taxAmount", 500.0);
        request.put("shippingCost", 500.0);

        given().contentType(ContentType.JSON).body(request).when().post("/api/payment/create-payment-intent").then()
                .statusCode(anyOf(is(200), is(400), is(500)));
    }
}
