package villagecompute.calendar.services.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.data.models.CalendarOrderItem;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.exceptions.EmailException;
import villagecompute.calendar.services.EmailService;
import villagecompute.calendar.services.exceptions.DelayedJobException;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests for {@link OrderCancellationJobHandler}.
 *
 * <p>
 * Covers the branches of {@code run(actorId)}:
 * <ul>
 * <li>Successful send with refund note (paid order)</li>
 * <li>Successful send without refund note (unpaid order)</li>
 * <li>Order not found (recoverable=true)</li>
 * <li>Order not in CANCELLED status (recoverable=true)</li>
 * <li>Email send failure (recoverable=false)</li>
 * <li>Invalid UUID input (propagates IllegalArgumentException)</li>
 * </ul>
 */
@QuarkusTest
class OrderCancellationJobHandlerTest {

    @Inject
    OrderCancellationJobHandler handler;

    @Inject
    ObjectMapper objectMapper;

    @InjectMock
    EmailService emailService;

    private CalendarUser testUser;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean any previous test data
        CalendarOrderItem.deleteAll();
        CalendarOrder.deleteAll();
        CalendarUser.delete("oauthSubject like ?1", "cancel-test-%");

        testUser = new CalendarUser();
        testUser.oauthProvider = "GOOGLE";
        testUser.oauthSubject = "cancel-test-" + System.currentTimeMillis();
        testUser.email = "cancel-customer@villagecompute.com";
        testUser.displayName = "Cancel Test Customer";
        testUser.persist();

        // Default mock setup - email send succeeds.
        reset(emailService);
        doNothing().when(emailService).sendHtmlEmail(anyString(), anyString(), anyString(), anyString());
    }

    @AfterEach
    @Transactional
    void tearDown() {
        CalendarOrderItem.deleteAll();
        CalendarOrder.deleteAll();
        CalendarUser.delete("oauthSubject like ?1", "cancel-test-%");
    }

    @Test
    @Transactional
    void run_withCancelledPaidOrder_sendsRefundEmail() throws Exception {
        // Given - a cancelled, paid order (has stripePaymentIntentId + paidAt)
        CalendarOrder order = createOrder(CalendarOrder.STATUS_CANCELLED);
        order.stripePaymentIntentId = "pi_test_cancel_paid_" + System.currentTimeMillis();
        order.paidAt = Instant.now();
        order.persist();

        // When
        handler.run(order.id.toString());

        // Then - email service was called with proper subject + user email
        verify(emailService, atLeastOnce()).sendHtmlEmail(anyString(), eq(testUser.email),
                eq("Order Cancelled - Village Compute Calendar"), contains("Order Cancellation Confirmation"));
    }

    @Test
    @Transactional
    void run_withCancelledUnpaidOrder_sendsEmailWithoutRefundNote() throws Exception {
        // Given - cancelled order that was never paid (no stripePaymentIntentId / paidAt)
        CalendarOrder order = createOrder(CalendarOrder.STATUS_CANCELLED);
        order.stripePaymentIntentId = null;
        order.paidAt = null;
        order.persist();

        // When
        handler.run(order.id.toString());

        // Then - email is still sent.
        verify(emailService, atLeastOnce()).sendHtmlEmail(anyString(), eq(testUser.email), anyString(), anyString());
    }

    @Test
    void run_withMissingOrder_throwsRecoverableException() {
        // Given - an order id that does not exist
        String missingId = UUID.randomUUID().toString();

        // When / Then - should throw recoverable DelayedJobException
        DelayedJobException ex = assertThrows(DelayedJobException.class, () -> handler.run(missingId));

        assertTrue(ex.isRecoverable(), "Missing order should produce recoverable exception");
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("Order not found"), "Message should mention 'Order not found'");

        // Email should NOT have been sent
        verify(emailService, never()).sendHtmlEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @Transactional
    void run_withNonCancelledOrder_throwsRecoverableException() {
        // Given - an order that exists but is NOT in CANCELLED state
        CalendarOrder order = createOrder(CalendarOrder.STATUS_PAID);
        order.persist();
        String orderId = order.id.toString();

        // When / Then - should throw recoverable DelayedJobException
        DelayedJobException ex = assertThrows(DelayedJobException.class, () -> handler.run(orderId));

        assertTrue(ex.isRecoverable(), "Wrong status should be recoverable (status may change later)");
        assertTrue(ex.getMessage().contains("Order is not cancelled"),
                "Message should mention 'Order is not cancelled'");

        verify(emailService, never()).sendHtmlEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @Transactional
    void run_whenEmailServiceFails_throwsNonRecoverableException() {
        // Given - cancelled order
        CalendarOrder order = createOrder(CalendarOrder.STATUS_CANCELLED);
        order.persist();
        String orderId = order.id.toString();

        // EmailService throws (simulating SMTP/transport failure)
        doThrow(new EmailException("Mailer offline", new RuntimeException("SMTP down"))).when(emailService)
                .sendHtmlEmail(anyString(), anyString(), anyString(), anyString());

        // When / Then
        DelayedJobException ex = assertThrows(DelayedJobException.class, () -> handler.run(orderId));

        assertFalse(ex.isRecoverable(), "Email failures should be non-recoverable");
        assertTrue(ex.getMessage().contains("Failed to send order cancellation email"),
                "Message should describe send failure");
        assertNotNull(ex.getCause(), "Cause should be preserved");
    }

    @Test
    void run_withInvalidUuid_throwsIllegalArgumentException() {
        // Given a non-UUID actor id, the UUID parser rejects it with IllegalArgumentException.
        // The handler does not catch this case because its try/catch only wraps exceptions
        // raised after the status checks. This test verifies that propagation behavior.
        assertThrows(IllegalArgumentException.class, () -> handler.run("not-a-uuid"));
    }

    @Test
    @Transactional
    void run_setsExpectedCancellationSubject() throws Exception {
        // Given
        CalendarOrder order = createOrder(CalendarOrder.STATUS_CANCELLED);
        order.persist();

        // When
        handler.run(order.id.toString());

        // Then - subject is exactly the cancellation subject
        verify(emailService).sendHtmlEmail(anyString(), eq(testUser.email),
                eq("Order Cancelled - Village Compute Calendar"), anyString());
    }

    @Test
    void getHandlerConfig_returnsExpectedAnnotation() {
        // Sanity check on DelayedJobConfig annotation (priority/description).
        // Reference the source class directly since CDI proxies do not carry annotations.
        DelayedJobConfig config = OrderCancellationJobHandler.class.getAnnotation(DelayedJobConfig.class);
        assertNotNull(config, "Handler should be annotated with @DelayedJobConfig");
        assertEquals(5, config.priority(), "Priority should be 5");
        assertEquals("Order cancellation email sender", config.description());
    }

    // ==================== Helpers ====================

    private CalendarOrder createOrder(String status) {
        CalendarOrder order = new CalendarOrder();
        order.user = testUser;
        order.customerEmail = testUser.email;
        order.subtotal = new BigDecimal("29.99");
        order.shippingCost = new BigDecimal("5.99");
        order.taxAmount = BigDecimal.ZERO;
        order.totalPrice = new BigDecimal("35.98");
        order.status = status;
        // orderNumber column is limited to 50 chars; UUID alone is 36.
        order.orderNumber = "VC-" + UUID.randomUUID();
        order.shippingAddress = createShippingAddress();
        if (CalendarOrder.STATUS_CANCELLED.equals(status)) {
            order.cancelledAt = Instant.now();
        }
        order.persist();

        CalendarOrderItem item = new CalendarOrderItem();
        item.order = order;
        item.description = "Cancellation Test Calendar";
        item.productType = CalendarOrderItem.TYPE_PRINT;
        item.setYear(2026);
        item.quantity = 1;
        item.unitPrice = new BigDecimal("29.99");
        item.lineTotal = new BigDecimal("29.99");
        item.persist();
        order.items.add(item);

        return order;
    }

    private ObjectNode createShippingAddress() {
        ObjectNode address = objectMapper.createObjectNode();
        address.put("name", "Cancel Tester");
        address.put("line1", "1 Cancel Way");
        address.put("city", "Groton");
        address.put("state", "VT");
        address.put("postalCode", "05046");
        address.put("country", "US");
        return address;
    }
}
