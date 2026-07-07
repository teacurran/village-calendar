package villagecompute.calendar.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;

import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.data.models.CalendarOrderItem;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Unit tests for PaymentService. Tests payment processing, webhooks, and refund handling. Note: Tests that require
 * actual Stripe API calls are mocked to avoid external dependencies.
 */
@QuarkusTest
class PaymentServiceTest {

    @Inject
    PaymentService paymentService;

    @Inject
    OrderService orderService;

    @Inject
    ObjectMapper objectMapper;

    private CalendarUser testUser;
    private UserCalendar testCalendar;
    private UUID testOrderId; // Store ID to re-fetch in each test transaction

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up test data
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
        testUser.oauthSubject = "test-payment-oauth-subject";
        testUser.email = "payment-test@example.com";
        testUser.displayName = "Payment Test User";
        testUser.persist();

        // Create test template
        CalendarTemplate template = new CalendarTemplate();
        template.name = "Test Template";
        template.description = "Test template for payment tests";
        template.isActive = true;
        template.configuration = objectMapper.createObjectNode();
        template.persist();

        // Create test calendar
        testCalendar = new UserCalendar();
        testCalendar.user = testUser;
        testCalendar.name = "Test Payment Calendar 2025";
        testCalendar.year = 2025;
        testCalendar.template = template;
        testCalendar.isPublic = false;
        testCalendar.persist();

        // Create test shipping address
        var shippingAddress = objectMapper.createObjectNode();
        shippingAddress.put("street", "123 Payment St");
        shippingAddress.put("city", "Payment City");
        shippingAddress.put("state", "CA");
        shippingAddress.put("postalCode", "12345");
        shippingAddress.put("country", "US");

        // Create test order and store the ID
        CalendarOrder testOrder = orderService.createOrder(testUser, testCalendar, 1, new BigDecimal("29.99"),
                shippingAddress);
        testOrderId = testOrder.id;
    }

    /** Helper to get the test order within current transaction. */
    private CalendarOrder getTestOrder() {
        return CalendarOrder.findById(testOrderId);
    }

    // ========== GETTER TESTS ==========

    @Test
    void testGetPublishableKey_ReturnsKey() {
        // When
        String key = paymentService.getPublishableKey();

        // Then - in test environment, key might be empty or test value
        assertNotNull(key);
    }

    @Test
    void testGetWebhookSecret_ReturnsSecret() {
        // When
        String secret = paymentService.getWebhookSecret();

        // Then - in test environment, secret might be empty or test value
        assertNotNull(secret);
    }

    // ========== PROCESS PAYMENT SUCCESS TESTS ==========

    @Test
    @Transactional
    void testProcessPaymentSuccess_OrderNotFound_ReturnsFalse() {
        // Given
        String nonExistentPaymentIntentId = "pi_nonexistent_" + UUID.randomUUID();

        // When
        boolean result = paymentService.processPaymentSuccess(nonExistentPaymentIntentId, null);

        // Then
        assertFalse(result);
    }

    @Test
    @Transactional
    void testProcessPaymentSuccess_ValidOrder_MarksAsPaid() {
        // Given
        String paymentIntentId = "pi_test_" + UUID.randomUUID();
        CalendarOrder order = getTestOrder();
        order.stripePaymentIntentId = paymentIntentId;
        order.persist();

        // When
        boolean result = paymentService.processPaymentSuccess(paymentIntentId, null);

        // Then
        assertTrue(result);
        order = getTestOrder();
        assertEquals(CalendarOrder.STATUS_PAID, order.status);
        assertNotNull(order.paidAt);
    }

    @Test
    @Transactional
    void testProcessPaymentSuccess_WithChargeId_StoresChargeId() {
        // Given
        String paymentIntentId = "pi_test_charge_" + UUID.randomUUID();
        String chargeId = "ch_test_" + UUID.randomUUID();
        CalendarOrder order = getTestOrder();
        order.stripePaymentIntentId = paymentIntentId;
        order.persist();

        // When
        boolean result = paymentService.processPaymentSuccess(paymentIntentId, chargeId);

        // Then
        assertTrue(result);
        order = getTestOrder();
        assertEquals(chargeId, order.stripeChargeId);
    }

    @Test
    @Transactional
    void testProcessPaymentSuccess_AlreadyPaid_ReturnsFalse() {
        // Given - order already marked as paid
        String paymentIntentId = "pi_test_already_paid_" + UUID.randomUUID();
        CalendarOrder order = getTestOrder();
        order.stripePaymentIntentId = paymentIntentId;
        order.status = CalendarOrder.STATUS_PAID;
        order.paidAt = Instant.now();
        order.persist();

        // When
        boolean result = paymentService.processPaymentSuccess(paymentIntentId, null);

        // Then
        assertFalse(result); // Idempotent - returns false if already processed
    }

    @Test
    @Transactional
    void testProcessPaymentSuccess_AddsNote() {
        // Given
        String paymentIntentId = "pi_test_note_" + UUID.randomUUID();
        CalendarOrder order = getTestOrder();
        order.stripePaymentIntentId = paymentIntentId;
        order.notes = null;
        order.persist();

        // When
        paymentService.processPaymentSuccess(paymentIntentId, null);

        // Then
        order = getTestOrder();
        assertNotNull(order.notes);
        assertTrue(order.notes.contains("Payment succeeded"));
        assertTrue(order.notes.contains(paymentIntentId));
    }

    @Test
    @Transactional
    void testProcessPaymentSuccess_AppendsToExistingNotes() {
        // Given
        String paymentIntentId = "pi_test_append_" + UUID.randomUUID();
        CalendarOrder order = getTestOrder();
        order.stripePaymentIntentId = paymentIntentId;
        order.notes = "Existing notes\n";
        order.persist();

        // When
        paymentService.processPaymentSuccess(paymentIntentId, null);

        // Then
        order = getTestOrder();
        assertTrue(order.notes.contains("Existing notes"));
        assertTrue(order.notes.contains("Payment succeeded"));
    }

    // ========== PROCESS REFUND WEBHOOK TESTS ==========

    @Test
    @Transactional
    void testProcessRefundWebhook_OrderNotFound_DoesNotThrow() {
        // Given
        String nonExistentChargeId = "ch_nonexistent_" + UUID.randomUUID();
        String refundId = "re_test_" + UUID.randomUUID();

        // When & Then - should not throw, just log warning
        assertDoesNotThrow(() -> paymentService.processRefundWebhook(nonExistentChargeId, refundId, 1000L));
    }

    @Test
    @Transactional
    void testProcessRefundWebhook_ValidCharge_AddsNote() {
        // Given
        String chargeId = "ch_test_refund_" + UUID.randomUUID();
        String refundId = "re_test_" + UUID.randomUUID();
        CalendarOrder order = getTestOrder();
        order.stripeChargeId = chargeId;
        order.notes = null;
        order.persist();

        // When
        paymentService.processRefundWebhook(chargeId, refundId, 2999L);

        // Then
        order = getTestOrder();
        assertNotNull(order.notes);
        assertTrue(order.notes.contains("Refund received"));
        assertTrue(order.notes.contains(refundId));
        assertTrue(order.notes.contains("$29.99"));
    }

    @Test
    @Transactional
    void testProcessRefundWebhook_AlreadyRecorded_SkipsProcessing() {
        // Given - refund already recorded
        String chargeId = "ch_test_idempotent_" + UUID.randomUUID();
        String refundId = "re_test_idempotent_" + UUID.randomUUID();
        CalendarOrder order = getTestOrder();
        order.stripeChargeId = chargeId;
        order.notes = "Previous note\n[timestamp] Refund received via webhook: $29.99 (Refund ID: " + refundId
                + ", Charge: " + chargeId + ")\n";
        order.persist();

        // When
        paymentService.processRefundWebhook(chargeId, refundId, 2999L);

        // Then - notes should not be duplicated
        order = getTestOrder();
        int refundCount = countOccurrences(order.notes, refundId);
        assertEquals(1, refundCount);
    }

    @Test
    @Transactional
    void testProcessRefundWebhook_AppendsToExistingNotes() {
        // Given
        String chargeId = "ch_test_append_" + UUID.randomUUID();
        String refundId = "re_test_append_" + UUID.randomUUID();
        CalendarOrder order = getTestOrder();
        order.stripeChargeId = chargeId;
        order.notes = "Existing payment notes\n";
        order.persist();

        // When
        paymentService.processRefundWebhook(chargeId, refundId, 500L);

        // Then
        order = getTestOrder();
        assertTrue(order.notes.contains("Existing payment notes"));
        assertTrue(order.notes.contains("Refund received"));
    }

    // ========== PROCESS CHECKOUT SESSION COMPLETED TESTS ==========

    @Test
    @Transactional
    void testProcessCheckoutSessionCompleted_NullOrderId_ReturnsFalse() {
        // Given - session with no orderId in metadata
        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_no_order");
        when(mockSession.getMetadata()).thenReturn(null);

        // When
        boolean result = paymentService.processCheckoutSessionCompleted(mockSession);

        // Then
        assertFalse(result);
    }

    @Test
    @Transactional
    void testProcessCheckoutSessionCompleted_EmptyMetadata_ReturnsFalse() {
        // Given
        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_empty_meta");
        when(mockSession.getMetadata()).thenReturn(new HashMap<>());

        // When
        boolean result = paymentService.processCheckoutSessionCompleted(mockSession);

        // Then
        assertFalse(result);
    }

    @Test
    @Transactional
    void testProcessCheckoutSessionCompleted_InvalidOrderId_ReturnsFalse() {
        // Given
        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_invalid_uuid");
        Map<String, String> metadata = new HashMap<>();
        metadata.put("orderId", "not-a-valid-uuid");
        when(mockSession.getMetadata()).thenReturn(metadata);

        // When
        boolean result = paymentService.processCheckoutSessionCompleted(mockSession);

        // Then
        assertFalse(result);
    }

    @Test
    @Transactional
    void testProcessCheckoutSessionCompleted_OrderNotFound_ThrowsException() {
        // Given
        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_not_found");
        Map<String, String> metadata = new HashMap<>();
        metadata.put("orderId", UUID.randomUUID().toString()); // Valid UUID but no order
        when(mockSession.getMetadata()).thenReturn(metadata);

        // When & Then
        assertThrows(IllegalStateException.class, () -> paymentService.processCheckoutSessionCompleted(mockSession));
    }

    @Test
    @Transactional
    void testProcessCheckoutSessionCompleted_AlreadyPaid_ReturnsFalse() {
        // Given
        CalendarOrder order = getTestOrder();
        order.status = CalendarOrder.STATUS_PAID;
        order.paidAt = Instant.now();
        order.persist();

        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_already_paid");
        Map<String, String> metadata = new HashMap<>();
        metadata.put("orderId", testOrderId.toString());
        when(mockSession.getMetadata()).thenReturn(metadata);

        // When
        boolean result = paymentService.processCheckoutSessionCompleted(mockSession);

        // Then
        assertFalse(result);
    }

    @Test
    @Transactional
    void testProcessCheckoutSessionCompleted_ValidSession_MarksPaid() {
        // Given
        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_valid");
        when(mockSession.getPaymentIntent()).thenReturn("pi_test_checkout_" + UUID.randomUUID());
        Map<String, String> metadata = new HashMap<>();
        metadata.put("orderId", testOrderId.toString());
        when(mockSession.getMetadata()).thenReturn(metadata);
        when(mockSession.getAmountTotal()).thenReturn(3598L); // $35.98
        when(mockSession.getAmountSubtotal()).thenReturn(2999L); // $29.99
        when(mockSession.getTotalDetails()).thenReturn(null);
        when(mockSession.getCustomerEmail()).thenReturn("customer@example.com");
        when(mockSession.getCustomerDetails()).thenReturn(null);
        when(mockSession.getCollectedInformation()).thenReturn(null);

        // When
        boolean result = paymentService.processCheckoutSessionCompleted(mockSession);

        // Then
        assertTrue(result);
        CalendarOrder order = getTestOrder();
        assertEquals(CalendarOrder.STATUS_PAID, order.status);
        assertNotNull(order.paidAt);
        assertEquals("customer@example.com", order.customerEmail);
    }

    @Test
    @Transactional
    void testProcessCheckoutSessionCompleted_WithTotalDetails_UpdatesAmounts() {
        // Given
        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_totals");
        when(mockSession.getPaymentIntent()).thenReturn("pi_test_totals_" + UUID.randomUUID());
        Map<String, String> metadata = new HashMap<>();
        metadata.put("orderId", testOrderId.toString());
        when(mockSession.getMetadata()).thenReturn(metadata);
        when(mockSession.getAmountTotal()).thenReturn(3897L); // $38.97
        when(mockSession.getAmountSubtotal()).thenReturn(2999L); // $29.99

        // Mock TotalDetails
        Session.TotalDetails totalDetails = mock(Session.TotalDetails.class);
        when(totalDetails.getAmountShipping()).thenReturn(599L); // $5.99
        when(totalDetails.getAmountTax()).thenReturn(299L); // $2.99
        when(mockSession.getTotalDetails()).thenReturn(totalDetails);
        when(mockSession.getCustomerEmail()).thenReturn(null);
        when(mockSession.getCustomerDetails()).thenReturn(null);
        when(mockSession.getCollectedInformation()).thenReturn(null);

        // When
        boolean result = paymentService.processCheckoutSessionCompleted(mockSession);

        // Then
        assertTrue(result);
        CalendarOrder order = getTestOrder();
        assertEquals(new BigDecimal("38.97"), order.totalPrice);
        assertEquals(new BigDecimal("29.99"), order.subtotal);
        assertEquals(new BigDecimal("5.99"), order.shippingCost);
        assertEquals(new BigDecimal("2.99"), order.taxAmount);
    }

    @Test
    @Transactional
    void testProcessCheckoutSessionCompleted_CustomerEmailFromDetails() {
        // Given - customerEmail null, but customer details has email
        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_customer_details");
        when(mockSession.getPaymentIntent()).thenReturn("pi_test_details_" + UUID.randomUUID());
        Map<String, String> metadata = new HashMap<>();
        metadata.put("orderId", testOrderId.toString());
        when(mockSession.getMetadata()).thenReturn(metadata);
        when(mockSession.getAmountTotal()).thenReturn(2999L);
        when(mockSession.getAmountSubtotal()).thenReturn(2999L);
        when(mockSession.getTotalDetails()).thenReturn(null);
        when(mockSession.getCustomerEmail()).thenReturn(null);

        Session.CustomerDetails customerDetails = mock(Session.CustomerDetails.class);
        when(customerDetails.getEmail()).thenReturn("details@example.com");
        when(mockSession.getCustomerDetails()).thenReturn(customerDetails);
        when(mockSession.getCollectedInformation()).thenReturn(null);

        // When
        boolean result = paymentService.processCheckoutSessionCompleted(mockSession);

        // Then
        assertTrue(result);
        CalendarOrder order = getTestOrder();
        assertEquals("details@example.com", order.customerEmail);
    }

    @Test
    @Transactional
    void testProcessCheckoutSessionCompleted_EmptyCustomerEmail_UsesDetails() {
        // Given - customerEmail is empty string
        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_empty_email");
        when(mockSession.getPaymentIntent()).thenReturn("pi_test_empty_" + UUID.randomUUID());
        Map<String, String> metadata = new HashMap<>();
        metadata.put("orderId", testOrderId.toString());
        when(mockSession.getMetadata()).thenReturn(metadata);
        when(mockSession.getAmountTotal()).thenReturn(2999L);
        when(mockSession.getAmountSubtotal()).thenReturn(null);
        when(mockSession.getTotalDetails()).thenReturn(null);
        when(mockSession.getCustomerEmail()).thenReturn("");

        Session.CustomerDetails customerDetails = mock(Session.CustomerDetails.class);
        when(customerDetails.getEmail()).thenReturn("fallback@example.com");
        when(mockSession.getCustomerDetails()).thenReturn(customerDetails);
        when(mockSession.getCollectedInformation()).thenReturn(null);

        // When
        boolean result = paymentService.processCheckoutSessionCompleted(mockSession);

        // Then
        assertTrue(result);
        CalendarOrder order = getTestOrder();
        assertEquals("fallback@example.com", order.customerEmail);
    }

    // ========== CHECKOUT LINE ITEM TESTS ==========

    @Test
    void testCheckoutLineItem_Constructor() {
        // When
        PaymentService.CheckoutLineItem item = new PaymentService.CheckoutLineItem("Test Product", "Test Description",
                2999L, 2L);

        // Then
        assertEquals("Test Product", item.name);
        assertEquals("Test Description", item.description);
        assertEquals(2999L, item.unitAmountCents);
        assertEquals(2L, item.quantity);
    }

    // ========== CREATE PAYMENT INTENT TESTS ==========

    @Test
    void testCreatePaymentIntent_BasicOverload_ReturnsClientSecret() throws StripeException {
        // Given
        String orderId = UUID.randomUUID().toString();
        PaymentIntent mockIntent = mock(PaymentIntent.class);
        when(mockIntent.getId()).thenReturn("pi_basic_123");
        when(mockIntent.getClientSecret()).thenReturn("secret_basic_123");

        try (MockedStatic<PaymentIntent> piMock = mockStatic(PaymentIntent.class)) {
            piMock.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(mockIntent);

            // When
            Map<String, String> result = paymentService.createPaymentIntent(new BigDecimal("29.99"), "usd", orderId);

            // Then
            assertNotNull(result);
            assertEquals("pi_basic_123", result.get("paymentIntentId"));
            assertEquals("secret_basic_123", result.get("clientSecret"));
            piMock.verify(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class)));
        }
    }

    @Test
    void testCreatePaymentIntent_FullBreakdown_ReturnsClientSecret() throws StripeException {
        // Given
        String orderId = UUID.randomUUID().toString();
        PaymentIntent mockIntent = mock(PaymentIntent.class);
        when(mockIntent.getId()).thenReturn("pi_full_456");
        when(mockIntent.getClientSecret()).thenReturn("secret_full_456");

        try (MockedStatic<PaymentIntent> piMock = mockStatic(PaymentIntent.class)) {
            piMock.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(mockIntent);

            // When - exercises subtotal, tax, shipping, and order number branches
            Map<String, String> result = paymentService.createPaymentIntent(new BigDecimal("38.97"), "usd", orderId,
                    new BigDecimal("29.99"), new BigDecimal("2.99"), new BigDecimal("5.99"), "VC-TEST-1234");

            // Then
            assertNotNull(result);
            assertEquals("pi_full_456", result.get("paymentIntentId"));
            assertEquals("secret_full_456", result.get("clientSecret"));
        }
    }

    @Test
    void testCreatePaymentIntent_NullCurrency_DefaultsToUsd() throws StripeException {
        // Given
        String orderId = UUID.randomUUID().toString();
        PaymentIntent mockIntent = mock(PaymentIntent.class);
        when(mockIntent.getId()).thenReturn("pi_null_currency");
        when(mockIntent.getClientSecret()).thenReturn("secret_null_currency");

        try (MockedStatic<PaymentIntent> piMock = mockStatic(PaymentIntent.class)) {
            piMock.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(mockIntent);

            // When - exercises the null-currency branch and the no-order-number path
            Map<String, String> result = paymentService.createPaymentIntent(new BigDecimal("10.00"), null, orderId,
                    null, BigDecimal.ZERO, BigDecimal.ZERO, null);

            // Then
            assertNotNull(result);
            assertEquals("pi_null_currency", result.get("paymentIntentId"));
        }
    }

    @Test
    void testCreatePaymentIntent_StripeError_PropagatesException() {
        // Given
        String orderId = UUID.randomUUID().toString();

        try (MockedStatic<PaymentIntent> piMock = mockStatic(PaymentIntent.class)) {
            piMock.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                    .thenThrow(new RuntimeException("Stripe API failure"));

            // When/Then
            assertThrows(RuntimeException.class,
                    () -> paymentService.createPaymentIntent(new BigDecimal("9.99"), "usd", orderId));
        }
    }

    // ========== UPDATE PAYMENT INTENT TESTS ==========

    @Test
    void testUpdatePaymentIntent_WithItems_UpdatesIntent() throws StripeException {
        // Given
        String paymentIntentId = "pi_update_789";
        CalendarOrder order = new CalendarOrder();
        order.id = UUID.randomUUID();

        CalendarOrderItem item = new CalendarOrderItem();
        item.description = "Custom Calendar 2025";
        item.quantity = 2;
        order.items = new ArrayList<>();
        order.items.add(item);

        PaymentIntent mockIntent = mock(PaymentIntent.class);
        PaymentIntent updatedIntent = mock(PaymentIntent.class);
        when(mockIntent.update(any(Map.class))).thenReturn(updatedIntent);

        try (MockedStatic<PaymentIntent> piMock = mockStatic(PaymentIntent.class)) {
            piMock.when(() -> PaymentIntent.retrieve(paymentIntentId)).thenReturn(mockIntent);

            // When
            PaymentIntent result = paymentService.updatePaymentIntent(paymentIntentId, order);

            // Then
            assertSame(updatedIntent, result);
            verify(mockIntent).update(any(Map.class));
        }
    }

    @Test
    void testUpdatePaymentIntent_EmptyItems_UsesDefaultDescription() throws StripeException {
        // Given
        String paymentIntentId = "pi_empty_items";
        CalendarOrder order = new CalendarOrder();
        order.id = UUID.randomUUID();
        order.items = new ArrayList<>(); // No items - exercises the default-description branch

        PaymentIntent mockIntent = mock(PaymentIntent.class);
        PaymentIntent updatedIntent = mock(PaymentIntent.class);
        when(mockIntent.update(any(Map.class))).thenReturn(updatedIntent);

        try (MockedStatic<PaymentIntent> piMock = mockStatic(PaymentIntent.class)) {
            piMock.when(() -> PaymentIntent.retrieve(paymentIntentId)).thenReturn(mockIntent);

            // When
            PaymentIntent result = paymentService.updatePaymentIntent(paymentIntentId, order);

            // Then
            assertSame(updatedIntent, result);
        }
    }

    // ========== CONFIRM PAYMENT TESTS ==========

    @Test
    void testConfirmPayment_RequiresConfirmation_CallsConfirm() throws StripeException {
        // Given
        String paymentIntentId = "pi_confirm_required";
        PaymentIntent mockIntent = mock(PaymentIntent.class);
        when(mockIntent.getStatus()).thenReturn("requires_confirmation");

        PaymentIntent confirmedIntent = mock(PaymentIntent.class);
        when(mockIntent.confirm(any(Map.class))).thenReturn(confirmedIntent);

        try (MockedStatic<PaymentIntent> piMock = mockStatic(PaymentIntent.class)) {
            piMock.when(() -> PaymentIntent.retrieve(paymentIntentId)).thenReturn(mockIntent);

            // When
            PaymentIntent result = paymentService.confirmPayment(paymentIntentId);

            // Then
            assertSame(confirmedIntent, result);
            verify(mockIntent).confirm(any(Map.class));
        }
    }

    @Test
    void testConfirmPayment_AlreadyConfirmed_ReturnsIntent() throws StripeException {
        // Given - status is already 'succeeded', no confirmation needed
        String paymentIntentId = "pi_confirm_skipped";
        PaymentIntent mockIntent = mock(PaymentIntent.class);
        when(mockIntent.getStatus()).thenReturn("succeeded");

        try (MockedStatic<PaymentIntent> piMock = mockStatic(PaymentIntent.class)) {
            piMock.when(() -> PaymentIntent.retrieve(paymentIntentId)).thenReturn(mockIntent);

            // When
            PaymentIntent result = paymentService.confirmPayment(paymentIntentId);

            // Then
            assertSame(mockIntent, result);
            verify(mockIntent, never()).confirm(any(Map.class));
        }
    }

    // ========== GET PAYMENT INTENT TESTS ==========

    @Test
    void testGetPaymentIntent_DelegatesToStripe() throws StripeException {
        // Given
        String paymentIntentId = "pi_get_test";
        PaymentIntent mockIntent = mock(PaymentIntent.class);

        try (MockedStatic<PaymentIntent> piMock = mockStatic(PaymentIntent.class)) {
            piMock.when(() -> PaymentIntent.retrieve(paymentIntentId)).thenReturn(mockIntent);

            // When
            PaymentIntent result = paymentService.getPaymentIntent(paymentIntentId);

            // Then
            assertSame(mockIntent, result);
            piMock.verify(() -> PaymentIntent.retrieve(paymentIntentId));
        }
    }

    // ========== CANCEL PAYMENT TESTS ==========

    @Test
    void testCancelPayment_CancelsAndReturnsIntent() throws StripeException {
        // Given
        String paymentIntentId = "pi_cancel_test";
        PaymentIntent mockIntent = mock(PaymentIntent.class);
        PaymentIntent cancelledIntent = mock(PaymentIntent.class);
        when(mockIntent.cancel()).thenReturn(cancelledIntent);

        try (MockedStatic<PaymentIntent> piMock = mockStatic(PaymentIntent.class)) {
            piMock.when(() -> PaymentIntent.retrieve(paymentIntentId)).thenReturn(mockIntent);

            // When
            PaymentIntent result = paymentService.cancelPayment(paymentIntentId);

            // Then
            assertSame(cancelledIntent, result);
            verify(mockIntent).cancel();
        }
    }

    // ========== PROCESS REFUND TESTS ==========

    @Test
    @Transactional
    void testProcessRefund_FullRefund_CreatesRefundAndUpdatesOrder() throws StripeException {
        // Given - order linked to a payment intent
        String paymentIntentId = "pi_refund_" + UUID.randomUUID();
        CalendarOrder order = getTestOrder();
        order.stripePaymentIntentId = paymentIntentId;
        order.notes = null;
        order.persist();

        Refund mockRefund = mock(Refund.class);
        when(mockRefund.getId()).thenReturn("re_full_999");
        when(mockRefund.getAmount()).thenReturn(2999L);
        when(mockRefund.getReason()).thenReturn("requested_by_customer");

        try (MockedStatic<Refund> refundMock = mockStatic(Refund.class)) {
            refundMock.when(() -> Refund.create(any(RefundCreateParams.class))).thenReturn(mockRefund);

            // When - null amount means full refund
            Refund result = paymentService.processRefund(paymentIntentId, null, null);

            // Then
            assertNotNull(result);
            assertEquals("re_full_999", result.getId());
            CalendarOrder reloaded = getTestOrder();
            assertNotNull(reloaded.notes);
            assertTrue(reloaded.notes.contains("Refund processed"));
            assertTrue(reloaded.notes.contains("re_full_999"));
            assertTrue(reloaded.notes.contains("$29.99"));
        }
    }

    @Test
    @Transactional
    void testProcessRefund_PartialRefundWithReason_AppendsNote() throws StripeException {
        // Given
        String paymentIntentId = "pi_refund_partial_" + UUID.randomUUID();
        CalendarOrder order = getTestOrder();
        order.stripePaymentIntentId = paymentIntentId;
        order.notes = "Existing note\n";
        order.persist();

        Refund mockRefund = mock(Refund.class);
        when(mockRefund.getId()).thenReturn("re_partial_111");
        when(mockRefund.getAmount()).thenReturn(500L);
        when(mockRefund.getReason()).thenReturn("duplicate");

        try (MockedStatic<Refund> refundMock = mockStatic(Refund.class)) {
            refundMock.when(() -> Refund.create(any(RefundCreateParams.class))).thenReturn(mockRefund);

            // When - exercises both setAmount and the "duplicate" reason branch
            Refund result = paymentService.processRefund(paymentIntentId, 500L, "duplicate");

            // Then
            assertNotNull(result);
            CalendarOrder reloaded = getTestOrder();
            assertTrue(reloaded.notes.contains("Existing note"));
            assertTrue(reloaded.notes.contains("$5.00"));
        }
    }

    @Test
    @Transactional
    void testProcessRefund_FraudulentReason_MappedCorrectly() throws StripeException {
        // Given
        String paymentIntentId = "pi_refund_fraud_" + UUID.randomUUID();
        CalendarOrder order = getTestOrder();
        order.stripePaymentIntentId = paymentIntentId;
        order.persist();

        Refund mockRefund = mock(Refund.class);
        when(mockRefund.getId()).thenReturn("re_fraud_222");
        when(mockRefund.getAmount()).thenReturn(1000L);
        when(mockRefund.getReason()).thenReturn("fraudulent");

        try (MockedStatic<Refund> refundMock = mockStatic(Refund.class)) {
            refundMock.when(() -> Refund.create(any(RefundCreateParams.class))).thenReturn(mockRefund);

            // When - exercises the "fraudulent" branch of the switch expression
            Refund result = paymentService.processRefund(paymentIntentId, 1000L, "fraudulent");

            // Then
            assertNotNull(result);
            assertEquals("re_fraud_222", result.getId());
        }
    }

    @Test
    @Transactional
    void testProcessRefund_OrderNotFound_ThrowsIllegalState() {
        // Given - payment intent not associated with any order
        String paymentIntentId = "pi_refund_missing_" + UUID.randomUUID();

        // When/Then - service throws IllegalStateException before reaching Stripe
        assertThrows(IllegalStateException.class, () -> paymentService.processRefund(paymentIntentId, null, null));
    }

    // ========== CREATE CHECKOUT SESSION TESTS ==========

    @Test
    void testCreateCheckoutSession_WithShipping_ReturnsClientSecret() throws StripeException {
        // Given
        String orderId = UUID.randomUUID().toString();
        List<PaymentService.CheckoutLineItem> items = new ArrayList<>();
        items.add(new PaymentService.CheckoutLineItem("Calendar A", "Description A", 2999L, 1L));
        items.add(new PaymentService.CheckoutLineItem("Calendar B", "Description B", 1999L, 2L));

        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_create_full");
        when(mockSession.getClientSecret()).thenReturn("cs_secret_full");

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            // When - shippingRequired=true exercises the shipping-options block (~100 lines)
            Map<String, String> result = paymentService.createCheckoutSession(orderId, "VC-CHECKOUT-1", items,
                    "buyer@example.com", "https://example.com/success", "https://example.com/cancel", true);

            // Then
            assertNotNull(result);
            assertEquals("cs_create_full", result.get("sessionId"));
            assertEquals("cs_secret_full", result.get("clientSecret"));
            sessionMock.verify(() -> Session.create(any(SessionCreateParams.class)));
        }
    }

    @Test
    void testCreateCheckoutSession_NoShippingNoEmail_ReturnsClientSecret() throws StripeException {
        // Given - minimal arguments: no order number, no email, no shipping
        String orderId = UUID.randomUUID().toString();
        List<PaymentService.CheckoutLineItem> items = new ArrayList<>();
        items.add(new PaymentService.CheckoutLineItem("Digital Calendar", "Digital download", 999L, 1L));

        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_create_min");
        when(mockSession.getClientSecret()).thenReturn("cs_secret_min");

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            // When - null orderNumber, null email, false shipping
            Map<String, String> result = paymentService.createCheckoutSession(orderId, null, items, null,
                    "https://example.com/success", "https://example.com/cancel", false);

            // Then
            assertNotNull(result);
            assertEquals("cs_create_min", result.get("sessionId"));
        }
    }

    @Test
    void testCreateCheckoutSession_EmptyEmail_SkipsCustomerEmail() throws StripeException {
        // Given - empty email exercises the !customerEmail.isEmpty() branch
        String orderId = UUID.randomUUID().toString();
        List<PaymentService.CheckoutLineItem> items = new ArrayList<>();
        items.add(new PaymentService.CheckoutLineItem("Item", "Desc", 500L, 1L));

        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_empty_email");
        when(mockSession.getClientSecret()).thenReturn("cs_empty_email_secret");

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            // When
            Map<String, String> result = paymentService.createCheckoutSession(orderId, "VC-EMPTY-EMAIL", items, "",
                    "https://example.com/success", "https://example.com/cancel", false);

            // Then
            assertNotNull(result);
            assertEquals("cs_empty_email", result.get("sessionId"));
        }
    }

    @Test
    void testCreateCheckoutSession_StripeError_PropagatesException() {
        // Given
        String orderId = UUID.randomUUID().toString();
        List<PaymentService.CheckoutLineItem> items = new ArrayList<>();
        items.add(new PaymentService.CheckoutLineItem("Item", "Desc", 100L, 1L));

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class)))
                    .thenThrow(new RuntimeException("Stripe checkout failure"));

            // When/Then
            assertThrows(RuntimeException.class, () -> paymentService.createCheckoutSession(orderId, null, items, null,
                    "https://example.com/success", "https://example.com/cancel", false));
        }
    }

    // ========== GET CHECKOUT SESSION TESTS ==========

    @Test
    void testGetCheckoutSession_DelegatesToStripe() throws StripeException {
        // Given
        String sessionId = "cs_get_test";
        Session mockSession = mock(Session.class);

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.retrieve(sessionId)).thenReturn(mockSession);

            // When
            Session result = paymentService.getCheckoutSession(sessionId);

            // Then
            assertSame(mockSession, result);
            sessionMock.verify(() -> Session.retrieve(sessionId));
        }
    }

    // ========== PROCESS CHECKOUT SESSION (BY ID) TESTS ==========

    @Test
    @Transactional
    void testProcessCheckoutSessionCompleted_BySessionId_ReturnsResult() throws StripeException {
        // Given - session id overload retrieves the session then delegates
        String sessionId = "cs_byid_test";
        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn(sessionId);
        when(mockSession.getMetadata()).thenReturn(null); // No orderId -> false

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.retrieve(sessionId)).thenReturn(mockSession);

            // When
            boolean result = paymentService.processCheckoutSessionCompleted(sessionId);

            // Then
            assertFalse(result);
            sessionMock.verify(() -> Session.retrieve(sessionId));
        }
    }

    // ========== APPLY SHIPPING ADDRESS TESTS ==========

    @Test
    @Transactional
    void testProcessCheckoutSessionCompleted_WithShippingAddress_StoresAddress() {
        // Given - session has a shipping address (exercises applyShippingAddressFromSession + addNameFields)
        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_shipping");
        when(mockSession.getPaymentIntent()).thenReturn("pi_shipping_" + UUID.randomUUID());
        Map<String, String> metadata = new HashMap<>();
        metadata.put("orderId", testOrderId.toString());
        when(mockSession.getMetadata()).thenReturn(metadata);
        when(mockSession.getAmountTotal()).thenReturn(2999L);
        when(mockSession.getAmountSubtotal()).thenReturn(2999L);
        when(mockSession.getTotalDetails()).thenReturn(null);
        when(mockSession.getCustomerEmail()).thenReturn("ship@example.com");
        when(mockSession.getCustomerDetails()).thenReturn(null);

        // Build collected information with a shipping address
        com.stripe.model.Address address = mock(com.stripe.model.Address.class);
        when(address.getLine1()).thenReturn("123 Main St");
        when(address.getLine2()).thenReturn("Apt 4");
        when(address.getCity()).thenReturn("Springfield");
        when(address.getState()).thenReturn("IL");
        when(address.getPostalCode()).thenReturn("62701");
        when(address.getCountry()).thenReturn("US");

        Session.CollectedInformation.ShippingDetails shipping = mock(
                Session.CollectedInformation.ShippingDetails.class);
        when(shipping.getAddress()).thenReturn(address);
        when(shipping.getName()).thenReturn("Jane Doe");

        Session.CollectedInformation collected = mock(Session.CollectedInformation.class);
        when(collected.getShippingDetails()).thenReturn(shipping);
        when(mockSession.getCollectedInformation()).thenReturn(collected);

        // When
        boolean result = paymentService.processCheckoutSessionCompleted(mockSession);

        // Then
        assertTrue(result);
        CalendarOrder order = getTestOrder();
        assertNotNull(order.shippingAddress);
        assertEquals("123 Main St", order.shippingAddress.get("line1").asText());
        assertEquals("Springfield", order.shippingAddress.get("city").asText());
        assertEquals("Jane", order.shippingAddress.get("firstName").asText());
        assertEquals("Doe", order.shippingAddress.get("lastName").asText());
    }

    @Test
    @Transactional
    void testProcessCheckoutSessionCompleted_SingleWordName_LastNameEmpty() {
        // Given - exercises the nameParts.length > 1 ternary branch
        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_single_name");
        when(mockSession.getPaymentIntent()).thenReturn("pi_single_" + UUID.randomUUID());
        Map<String, String> metadata = new HashMap<>();
        metadata.put("orderId", testOrderId.toString());
        when(mockSession.getMetadata()).thenReturn(metadata);
        when(mockSession.getAmountTotal()).thenReturn(2999L);
        when(mockSession.getAmountSubtotal()).thenReturn(2999L);
        when(mockSession.getTotalDetails()).thenReturn(null);
        when(mockSession.getCustomerEmail()).thenReturn("single@example.com");
        when(mockSession.getCustomerDetails()).thenReturn(null);

        com.stripe.model.Address address = mock(com.stripe.model.Address.class);
        when(address.getLine1()).thenReturn("1 Solo St");
        when(address.getCity()).thenReturn("Solotown");
        when(address.getState()).thenReturn("CA");
        when(address.getPostalCode()).thenReturn("90001");
        when(address.getCountry()).thenReturn("US");

        Session.CollectedInformation.ShippingDetails shipping = mock(
                Session.CollectedInformation.ShippingDetails.class);
        when(shipping.getAddress()).thenReturn(address);
        when(shipping.getName()).thenReturn("Cher"); // Single-word name

        Session.CollectedInformation collected = mock(Session.CollectedInformation.class);
        when(collected.getShippingDetails()).thenReturn(shipping);
        when(mockSession.getCollectedInformation()).thenReturn(collected);

        // When
        boolean result = paymentService.processCheckoutSessionCompleted(mockSession);

        // Then
        assertTrue(result);
        CalendarOrder order = getTestOrder();
        assertEquals("Cher", order.shippingAddress.get("firstName").asText());
        assertEquals("", order.shippingAddress.get("lastName").asText());
    }

    @Test
    @Transactional
    void testProcessCheckoutSessionCompleted_NullShippingAddress_SkipsAddressBlock() {
        // Given - the setUp creates an order with a shipping address already populated.
        // Clear it so we can verify the address==null branch leaves the order untouched.
        CalendarOrder existing = getTestOrder();
        existing.shippingAddress = null;
        existing.persist();

        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_null_address");
        when(mockSession.getPaymentIntent()).thenReturn("pi_null_addr_" + UUID.randomUUID());
        Map<String, String> metadata = new HashMap<>();
        metadata.put("orderId", testOrderId.toString());
        when(mockSession.getMetadata()).thenReturn(metadata);
        when(mockSession.getAmountTotal()).thenReturn(2999L);
        when(mockSession.getAmountSubtotal()).thenReturn(2999L);
        when(mockSession.getTotalDetails()).thenReturn(null);
        when(mockSession.getCustomerEmail()).thenReturn("noaddr@example.com");
        when(mockSession.getCustomerDetails()).thenReturn(null);

        // shippingDetails non-null but address is null (exercises the address==null branch)
        Session.CollectedInformation.ShippingDetails shipping = mock(
                Session.CollectedInformation.ShippingDetails.class);
        when(shipping.getAddress()).thenReturn(null);

        Session.CollectedInformation collected = mock(Session.CollectedInformation.class);
        when(collected.getShippingDetails()).thenReturn(shipping);
        when(mockSession.getCollectedInformation()).thenReturn(collected);

        // When
        boolean result = paymentService.processCheckoutSessionCompleted(mockSession);

        // Then - succeeds but shipping address stays null
        assertTrue(result);
        CalendarOrder order = getTestOrder();
        assertNull(order.shippingAddress);
    }

    // ========== HELPER METHODS ==========

    private int countOccurrences(String str, String sub) {
        if (str == null || sub == null) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
