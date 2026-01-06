package villagecompute.calendar.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.model.checkout.Session;

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
    private CalendarOrder testOrder;

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

        // Create test order
        testOrder = orderService.createOrder(testUser, testCalendar, 1, new BigDecimal("29.99"), shippingAddress);
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
        testOrder.stripePaymentIntentId = paymentIntentId;
        testOrder.persist();

        // When
        boolean result = paymentService.processPaymentSuccess(paymentIntentId, null);

        // Then
        assertTrue(result);
        testOrder = CalendarOrder.findById(testOrder.id);
        assertEquals(CalendarOrder.STATUS_PAID, testOrder.status);
        assertNotNull(testOrder.paidAt);
    }

    @Test
    @Transactional
    void testProcessPaymentSuccess_WithChargeId_StoresChargeId() {
        // Given
        String paymentIntentId = "pi_test_charge_" + UUID.randomUUID();
        String chargeId = "ch_test_" + UUID.randomUUID();
        testOrder.stripePaymentIntentId = paymentIntentId;
        testOrder.persist();

        // When
        boolean result = paymentService.processPaymentSuccess(paymentIntentId, chargeId);

        // Then
        assertTrue(result);
        testOrder = CalendarOrder.findById(testOrder.id);
        assertEquals(chargeId, testOrder.stripeChargeId);
    }

    @Test
    @Transactional
    void testProcessPaymentSuccess_AlreadyPaid_ReturnsFalse() {
        // Given - order already marked as paid
        String paymentIntentId = "pi_test_already_paid_" + UUID.randomUUID();
        testOrder.stripePaymentIntentId = paymentIntentId;
        testOrder.status = CalendarOrder.STATUS_PAID;
        testOrder.paidAt = Instant.now();
        testOrder.persist();

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
        testOrder.stripePaymentIntentId = paymentIntentId;
        testOrder.notes = null;
        testOrder.persist();

        // When
        paymentService.processPaymentSuccess(paymentIntentId, null);

        // Then
        testOrder = CalendarOrder.findById(testOrder.id);
        assertNotNull(testOrder.notes);
        assertTrue(testOrder.notes.contains("Payment succeeded"));
        assertTrue(testOrder.notes.contains(paymentIntentId));
    }

    @Test
    @Transactional
    void testProcessPaymentSuccess_AppendsToExistingNotes() {
        // Given
        String paymentIntentId = "pi_test_append_" + UUID.randomUUID();
        testOrder.stripePaymentIntentId = paymentIntentId;
        testOrder.notes = "Existing notes\n";
        testOrder.persist();

        // When
        paymentService.processPaymentSuccess(paymentIntentId, null);

        // Then
        testOrder = CalendarOrder.findById(testOrder.id);
        assertTrue(testOrder.notes.contains("Existing notes"));
        assertTrue(testOrder.notes.contains("Payment succeeded"));
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
        testOrder.stripeChargeId = chargeId;
        testOrder.notes = null;
        testOrder.persist();

        // When
        paymentService.processRefundWebhook(chargeId, refundId, 2999L);

        // Then
        testOrder = CalendarOrder.findById(testOrder.id);
        assertNotNull(testOrder.notes);
        assertTrue(testOrder.notes.contains("Refund received"));
        assertTrue(testOrder.notes.contains(refundId));
        assertTrue(testOrder.notes.contains("$29.99"));
    }

    @Test
    @Transactional
    void testProcessRefundWebhook_AlreadyRecorded_SkipsProcessing() {
        // Given - refund already recorded
        String chargeId = "ch_test_idempotent_" + UUID.randomUUID();
        String refundId = "re_test_idempotent_" + UUID.randomUUID();
        testOrder.stripeChargeId = chargeId;
        testOrder.notes = "Previous note\n[timestamp] Refund received via webhook: $29.99 (Refund ID: " + refundId
                + ", Charge: " + chargeId + ")\n";
        testOrder.persist();

        // When
        paymentService.processRefundWebhook(chargeId, refundId, 2999L);

        // Then - notes should not be duplicated
        testOrder = CalendarOrder.findById(testOrder.id);
        int refundCount = countOccurrences(testOrder.notes, refundId);
        assertEquals(1, refundCount);
    }

    @Test
    @Transactional
    void testProcessRefundWebhook_AppendsToExistingNotes() {
        // Given
        String chargeId = "ch_test_append_" + UUID.randomUUID();
        String refundId = "re_test_append_" + UUID.randomUUID();
        testOrder.stripeChargeId = chargeId;
        testOrder.notes = "Existing payment notes\n";
        testOrder.persist();

        // When
        paymentService.processRefundWebhook(chargeId, refundId, 500L);

        // Then
        testOrder = CalendarOrder.findById(testOrder.id);
        assertTrue(testOrder.notes.contains("Existing payment notes"));
        assertTrue(testOrder.notes.contains("Refund received"));
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
        testOrder.status = CalendarOrder.STATUS_PAID;
        testOrder.paidAt = Instant.now();
        testOrder.persist();

        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_already_paid");
        Map<String, String> metadata = new HashMap<>();
        metadata.put("orderId", testOrder.id.toString());
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
        metadata.put("orderId", testOrder.id.toString());
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
        testOrder = CalendarOrder.findById(testOrder.id);
        assertEquals(CalendarOrder.STATUS_PAID, testOrder.status);
        assertNotNull(testOrder.paidAt);
        assertEquals("customer@example.com", testOrder.customerEmail);
    }

    @Test
    @Transactional
    void testProcessCheckoutSessionCompleted_WithTotalDetails_UpdatesAmounts() {
        // Given
        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_totals");
        when(mockSession.getPaymentIntent()).thenReturn("pi_test_totals_" + UUID.randomUUID());
        Map<String, String> metadata = new HashMap<>();
        metadata.put("orderId", testOrder.id.toString());
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
        testOrder = CalendarOrder.findById(testOrder.id);
        assertEquals(new BigDecimal("38.97"), testOrder.totalPrice);
        assertEquals(new BigDecimal("29.99"), testOrder.subtotal);
        assertEquals(new BigDecimal("5.99"), testOrder.shippingCost);
        assertEquals(new BigDecimal("2.99"), testOrder.taxAmount);
    }

    @Test
    @Transactional
    void testProcessCheckoutSessionCompleted_CustomerEmailFromDetails() {
        // Given - customerEmail null, but customer details has email
        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_customer_details");
        when(mockSession.getPaymentIntent()).thenReturn("pi_test_details_" + UUID.randomUUID());
        Map<String, String> metadata = new HashMap<>();
        metadata.put("orderId", testOrder.id.toString());
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
        testOrder = CalendarOrder.findById(testOrder.id);
        assertEquals("details@example.com", testOrder.customerEmail);
    }

    @Test
    @Transactional
    void testProcessCheckoutSessionCompleted_EmptyCustomerEmail_UsesDetails() {
        // Given - customerEmail is empty string
        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_empty_email");
        when(mockSession.getPaymentIntent()).thenReturn("pi_test_empty_" + UUID.randomUUID());
        Map<String, String> metadata = new HashMap<>();
        metadata.put("orderId", testOrder.id.toString());
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
        testOrder = CalendarOrder.findById(testOrder.id);
        assertEquals("fallback@example.com", testOrder.customerEmail);
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
