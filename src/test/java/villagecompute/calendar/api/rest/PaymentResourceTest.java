package villagecompute.calendar.api.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;

import villagecompute.calendar.exceptions.ApplicationException;
import villagecompute.calendar.services.OrderService;
import villagecompute.calendar.services.PaymentService;
import villagecompute.calendar.types.CheckoutRequestType;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Tests for PaymentResource REST endpoints.
 *
 * <p>
 * Covers each REST endpoint exposed by {@link PaymentResource}: success path, validation errors, Stripe-error wrapping
 * (mapped to 400), and unexpected-error wrapping (mapped to 500). Stripe is never contacted: both
 * {@link PaymentService} and {@link OrderService} are replaced with Mockito mocks via {@code @InjectMock}.
 */
@QuarkusTest
class PaymentResourceTest {

    private static final String CONFIG_PATH = "/api/payment/config";
    private static final String CREATE_INTENT_PATH = "/api/payment/create-payment-intent";
    private static final String CONFIRM_PATH = "/api/payment/confirm-payment";

    @InjectMock
    PaymentService paymentService;

    @InjectMock
    OrderService orderService;

    @BeforeEach
    void setUp() {
        // Default sensible mock behaviour; individual tests override as needed.
        when(paymentService.getPublishableKey()).thenReturn("pk_test_mock_123");
    }

    private Map<String, Object> baseIntentRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("amount", 29.99);
        request.put("currency", "usd");
        return request;
    }

    private Map<String, Object> buildOrderDetails(String email) {
        Map<String, Object> orderDetails = new HashMap<>();
        if (email != null) {
            orderDetails.put("email", email);
        }
        Map<String, Object> shipping = new HashMap<>();
        shipping.put("address1", "123 Test St");
        shipping.put("city", "Groton");
        shipping.put("state", "VT");
        shipping.put("postalCode", "05046");
        shipping.put("country", "US");
        orderDetails.put("shippingAddress", shipping);

        Map<String, Object> item = new HashMap<>();
        item.put("templateId", "tpl-1");
        item.put("quantity", 1);
        orderDetails.put("items", List.of(item));

        orderDetails.put("subtotal", 24.99);
        orderDetails.put("shippingCost", 5.00);
        orderDetails.put("taxAmount", 2.00);
        orderDetails.put("totalAmount", 31.99);
        return orderDetails;
    }

    // ========== GET /api/payment/config ==========

    @Test
    void testGetConfig_ReturnsPublishableKey() {
        when(paymentService.getPublishableKey()).thenReturn("pk_test_specific_key");

        given()
                .when().get(CONFIG_PATH)
                .then().statusCode(200)
                .contentType(ContentType.JSON)
                .body("publishableKey", equalTo("pk_test_specific_key"));
    }

    // ========== POST /api/payment/create-payment-intent ==========

    @Test
    void testCreatePaymentIntent_Success_ReturnsClientSecret() throws Exception {
        Map<String, String> stripeResponse = new HashMap<>();
        stripeResponse.put("clientSecret", "pi_test_secret_abc");
        stripeResponse.put("paymentIntentId", "pi_test_abc");

        when(paymentService.createPaymentIntent(any(BigDecimal.class), anyString(), anyString(), any(), any(), any(),
                any())).thenReturn(stripeResponse);

        Map<String, Object> request = baseIntentRequest();

        given().contentType(ContentType.JSON).body(request).when().post(CREATE_INTENT_PATH).then().statusCode(200)
                .body("clientSecret", equalTo("pi_test_secret_abc")).body("paymentIntentId", equalTo("pi_test_abc"));
    }

    @Test
    void testCreatePaymentIntent_WithFullBreakdown_PassesValuesToService() throws Exception {
        Map<String, String> stripeResponse = new HashMap<>();
        stripeResponse.put("clientSecret", "pi_test_secret_xyz");
        stripeResponse.put("paymentIntentId", "pi_test_xyz");

        when(paymentService.createPaymentIntent(any(BigDecimal.class), anyString(), anyString(), any(), any(), any(),
                any())).thenReturn(stripeResponse);

        Map<String, Object> request = new HashMap<>();
        request.put("amount", 31.99);
        request.put("currency", "usd");
        request.put("subtotal", 24.99);
        request.put("taxAmount", 2.00);
        request.put("shippingCost", 5.00);

        given().contentType(ContentType.JSON).body(request).when().post(CREATE_INTENT_PATH).then().statusCode(200)
                .body("clientSecret", equalTo("pi_test_secret_xyz"));

        verify(paymentService).createPaymentIntent(eq(BigDecimal.valueOf(31.99)), eq("usd"), anyString(),
                eq(BigDecimal.valueOf(24.99)), eq(BigDecimal.valueOf(2.00)), eq(BigDecimal.valueOf(5.00)), eq(null));
    }

    @Test
    void testCreatePaymentIntent_NullCurrency_DefaultsToUsd() throws Exception {
        Map<String, String> stripeResponse = new HashMap<>();
        stripeResponse.put("clientSecret", "pi_test_secret_def");
        stripeResponse.put("paymentIntentId", "pi_test_def");

        when(paymentService.createPaymentIntent(any(BigDecimal.class), anyString(), anyString(), any(), any(), any(),
                any())).thenReturn(stripeResponse);

        Map<String, Object> request = new HashMap<>();
        request.put("amount", 10.00);
        // currency intentionally omitted

        given().contentType(ContentType.JSON).body(request).when().post(CREATE_INTENT_PATH).then().statusCode(200);

        verify(paymentService).createPaymentIntent(any(BigDecimal.class), eq("usd"), anyString(), any(), any(), any(),
                any());
    }

    @Test
    void testCreatePaymentIntent_StripeFailure_Returns400WithErrorBody() throws Exception {
        StripeException stripeError = mock(StripeException.class);
        when(stripeError.getMessage()).thenReturn("Invalid amount");

        when(paymentService.createPaymentIntent(any(BigDecimal.class), anyString(), anyString(), any(), any(), any(),
                any())).thenThrow(stripeError);

        Map<String, Object> request = baseIntentRequest();

        given().contentType(ContentType.JSON).body(request).when().post(CREATE_INTENT_PATH).then().statusCode(400)
                .body("error", equalTo("Invalid amount"));
    }

    @Test
    void testCreatePaymentIntent_UnexpectedFailure_Returns500() throws Exception {
        when(paymentService.createPaymentIntent(
                any(BigDecimal.class), anyString(), anyString(), any(), any(), any(), any()))
                .thenThrow(new ApplicationException("DB unreachable"));

        Map<String, Object> request = baseIntentRequest();

        given().contentType(ContentType.JSON).body(request)
                .when().post(CREATE_INTENT_PATH)
                .then().statusCode(500)
                .body("error", equalTo("Failed to create payment intent"));
    }

    // ========== POST /api/payment/confirm-payment ==========

    @Test
    void testConfirmPayment_Success_ReturnsOrderNumber() throws Exception {
        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getStatus()).thenReturn("succeeded");
        when(paymentService.getPaymentIntent("pi_test_ok")).thenReturn(intent);
        when(orderService.processCheckout(any(CheckoutRequestType.class))).thenReturn("2026-001");

        Map<String, Object> request = new HashMap<>();
        request.put("paymentIntentId", "pi_test_ok");
        request.put("orderDetails", buildOrderDetails("buyer@example.com"));

        given().contentType(ContentType.JSON).body(request).when().post(CONFIRM_PATH).then().statusCode(200)
                .body("success", equalTo(true)).body("orderNumber", equalTo("2026-001"))
                .body("paymentIntentId", equalTo("pi_test_ok"));
    }

    @Test
    void testConfirmPayment_PaymentNotSucceeded_Returns400() throws Exception {
        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getStatus()).thenReturn("requires_payment_method");
        when(paymentService.getPaymentIntent("pi_test_failed")).thenReturn(intent);

        Map<String, Object> request = new HashMap<>();
        request.put("paymentIntentId", "pi_test_failed");
        request.put("orderDetails", buildOrderDetails("buyer@example.com"));

        given().contentType(ContentType.JSON).body(request).when().post(CONFIRM_PATH).then().statusCode(400)
                .body("error", equalTo("Payment not completed"));

        verify(orderService, never()).processCheckout(any(CheckoutRequestType.class));
    }

    @Test
    void testConfirmPayment_MissingEmail_Returns400() throws Exception {
        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getStatus()).thenReturn("succeeded");
        when(paymentService.getPaymentIntent("pi_test_no_email")).thenReturn(intent);

        Map<String, Object> request = new HashMap<>();
        request.put("paymentIntentId", "pi_test_no_email");
        request.put("orderDetails", buildOrderDetails(null));

        given().contentType(ContentType.JSON).body(request).when().post(CONFIRM_PATH).then().statusCode(400)
                .body("error", equalTo("Email is required"));

        verify(orderService, never()).processCheckout(any(CheckoutRequestType.class));
    }

    @Test
    void testConfirmPayment_EmptyEmail_Returns400() throws Exception {
        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getStatus()).thenReturn("succeeded");
        when(paymentService.getPaymentIntent("pi_test_empty_email")).thenReturn(intent);

        Map<String, Object> orderDetails = buildOrderDetails(null);
        orderDetails.put("email", "");

        Map<String, Object> request = new HashMap<>();
        request.put("paymentIntentId", "pi_test_empty_email");
        request.put("orderDetails", orderDetails);

        given().contentType(ContentType.JSON).body(request).when().post(CONFIRM_PATH).then().statusCode(400)
                .body("error", equalTo("Email is required"));
    }

    @Test
    void testConfirmPayment_NullOrderDetails_Returns400EmailRequired() throws Exception {
        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getStatus()).thenReturn("succeeded");
        when(paymentService.getPaymentIntent("pi_test_no_details")).thenReturn(intent);

        Map<String, Object> request = new HashMap<>();
        request.put("paymentIntentId", "pi_test_no_details");
        // orderDetails omitted on purpose

        given().contentType(ContentType.JSON).body(request).when().post(CONFIRM_PATH).then().statusCode(400)
                .body("error", equalTo("Email is required"));
    }

    @Test
    void testConfirmPayment_StripeFailure_Returns400WithStripeMessage() throws Exception {
        StripeException stripeError = mock(StripeException.class);
        when(stripeError.getMessage()).thenReturn("No such payment_intent");
        when(paymentService.getPaymentIntent("pi_test_stripe_err")).thenThrow(stripeError);

        Map<String, Object> request = new HashMap<>();
        request.put("paymentIntentId", "pi_test_stripe_err");
        request.put("orderDetails", buildOrderDetails("buyer@example.com"));

        given().contentType(ContentType.JSON).body(request).when().post(CONFIRM_PATH).then().statusCode(400)
                .body("error", equalTo("No such payment_intent"));
    }

    @Test
    void testConfirmPayment_OrderServiceFailure_Returns500() throws Exception {
        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getStatus()).thenReturn("succeeded");
        when(paymentService.getPaymentIntent("pi_test_order_err")).thenReturn(intent);
        when(orderService.processCheckout(any(CheckoutRequestType.class)))
                .thenThrow(new ApplicationException("DB write failed"));

        Map<String, Object> request = new HashMap<>();
        request.put("paymentIntentId", "pi_test_order_err");
        request.put("orderDetails", buildOrderDetails("buyer@example.com"));

        given().contentType(ContentType.JSON).body(request).when().post(CONFIRM_PATH).then().statusCode(500)
                .body("error", containsString("Failed to confirm payment"));
    }
}
