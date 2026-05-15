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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
 * Tests for ShippingNotificationJobHandler. Exercises every branch of the handler's run() method including success,
 * order-not-found, missing tracking number, email send failure (recoverable), and template rendering. Uses a mocked
 * EmailService so we can simulate failures and verify dispatch behaviour.
 */
@QuarkusTest
class ShippingNotificationJobHandlerTest {

    @Inject
    ShippingNotificationJobHandler handler;

    @InjectMock
    EmailService emailService;

    @Inject
    ObjectMapper objectMapper;

    private CalendarUser testUser;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up test data in correct order
        CalendarOrderItem.deleteAll();
        CalendarOrder.deleteAll();
        CalendarUser.delete("oauthSubject like ?1", "shipping-test-%");

        testUser = new CalendarUser();
        testUser.oauthProvider = "GOOGLE";
        testUser.oauthSubject = "shipping-test-" + System.nanoTime();
        testUser.email = "shipping-customer@villagecompute.com";
        testUser.displayName = "Shipping Customer";
        testUser.persist();

        Mockito.reset(emailService);
    }

    @AfterEach
    @Transactional
    void tearDown() {
        CalendarOrderItem.deleteAll();
        CalendarOrder.deleteAll();
        CalendarUser.delete("oauthSubject like ?1", "shipping-test-%");
    }

    @Test
    @Transactional
    void testRun_OrderNotFound_ThrowsExceptionWithoutSendingEmail() {
        // Given - a random UUID that doesn't correspond to any order
        UUID missingId = UUID.randomUUID();

        // When / Then - handler currently flags this as recoverable=true (see source line 78)
        DelayedJobException ex = assertThrows(DelayedJobException.class, () -> {
            handler.run(missingId.toString());
        });
        assertTrue(ex.getMessage().contains(missingId.toString()), "Exception message should include order id");
        assertTrue(ex.getMessage().contains("Order not found"), "Message should describe the failure");
        verifyNoInteractions(emailService);
    }

    @Test
    @Transactional
    void testRun_NullTrackingNumber_ThrowsExceptionWithoutSendingEmail() {
        // Given - an order without a tracking number
        CalendarOrder order = createShippableOrder();
        order.trackingNumber = null;
        order.persist();

        // When / Then - missing tracking number short-circuits before email is dispatched
        DelayedJobException ex = assertThrows(DelayedJobException.class, () -> {
            handler.run(order.id.toString());
        });
        assertTrue(ex.getMessage().contains("no tracking number"), "Message should mention tracking number");
        verifyNoInteractions(emailService);
    }

    @Test
    @Transactional
    void testRun_BlankTrackingNumber_ThrowsExceptionWithoutSendingEmail() {
        // Given - an order whose tracking number is whitespace only
        CalendarOrder order = createShippableOrder();
        order.trackingNumber = "   ";
        order.persist();

        // When / Then
        DelayedJobException ex = assertThrows(DelayedJobException.class, () -> {
            handler.run(order.id.toString());
        });
        assertTrue(ex.getMessage().contains("no tracking number"), "Message should mention tracking number");
        verifyNoInteractions(emailService);
    }

    @Test
    @Transactional
    void testRun_SuccessfulShippingNotification_SendsHtmlEmail() throws Exception {
        // Given - a complete shippable order with tracking number
        CalendarOrder order = createShippableOrder();
        order.trackingNumber = "1Z999AA10123456784";
        order.persist();

        doNothing().when(emailService).sendHtmlEmail(anyString(), anyString(), anyString(), anyString());

        // When
        handler.run(order.id.toString());

        // Then - verify mailer was called with the correct subject and recipient
        verify(emailService).sendHtmlEmail(anyString(), eq(testUser.email),
                eq("Your Order Has Shipped! - Village Compute Calendar"), anyString());
    }

    @Test
    @Transactional
    void testRun_SuccessfulShippingNotification_RendersTemplateContent() throws Exception {
        // Given
        CalendarOrder order = createShippableOrder();
        order.trackingNumber = "TRACK-XYZ-12345";
        order.persist();

        doNothing().when(emailService).sendHtmlEmail(anyString(), anyString(), anyString(), anyString());

        // When
        handler.run(order.id.toString());

        // Then - the html body should include the tracking number and order number
        verify(emailService).sendHtmlEmail(anyString(), eq(testUser.email), anyString(), contains("TRACK-XYZ-12345"));
        verify(emailService, atLeastOnce()).sendHtmlEmail(anyString(), anyString(), anyString(),
                contains(order.orderNumber));
    }

    @Test
    @Transactional
    void testRun_EmailServiceFails_WrapsExceptionAsDelayedJobException() throws Exception {
        // Given
        CalendarOrder order = createShippableOrder();
        order.trackingNumber = "TRACK-FAIL-1";
        order.persist();

        doThrow(new EmailException("SMTP unavailable", new RuntimeException("connect timed out"))).when(emailService)
                .sendHtmlEmail(anyString(), anyString(), anyString(), anyString());

        // When / Then - handler's generic catch block flags as non-recoverable
        DelayedJobException ex = assertThrows(DelayedJobException.class, () -> {
            handler.run(order.id.toString());
        });
        assertFalse(ex.isRecoverable(), "Email failures fall through to the non-recoverable catch branch");
        assertNotNull(ex.getCause(), "Exception cause should be preserved");
        assertEquals("Failed to send shipping notification email", ex.getMessage());
    }

    @Test
    @Transactional
    void testRun_InvalidUuid_ThrowsIllegalArgumentException() {
        // Given - an actor id that is not a valid UUID
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> {
            handler.run("not-a-uuid");
        });
        verifyNoInteractions(emailService);
    }

    // ==================== Helpers ====================

    private CalendarOrder createShippableOrder() {
        CalendarOrder order = new CalendarOrder();
        order.user = testUser;
        order.customerEmail = testUser.email;
        order.subtotal = new BigDecimal("29.99");
        order.shippingCost = new BigDecimal("5.99");
        order.taxAmount = BigDecimal.ZERO;
        order.totalPrice = new BigDecimal("35.98");
        order.status = CalendarOrder.STATUS_SHIPPED;
        order.orderNumber = "VC-SHIP-" + System.nanoTime();
        order.shippingAddress = buildShippingAddress();
        order.paidAt = Instant.now().minusSeconds(3600);
        order.shippedAt = Instant.now();
        order.persist();

        CalendarOrderItem item = new CalendarOrderItem();
        item.order = order;
        item.description = "Test Calendar 2025";
        item.productType = CalendarOrderItem.TYPE_PRINT;
        item.setYear(2025);
        item.quantity = 1;
        item.unitPrice = new BigDecimal("29.99");
        item.lineTotal = new BigDecimal("29.99");
        item.persist();
        order.items.add(item);

        return order;
    }

    private ObjectNode buildShippingAddress() {
        ObjectNode address = objectMapper.createObjectNode();
        address.put("name", "Shipping Customer");
        address.put("line1", "1 Test Way");
        address.put("city", "Groton");
        address.put("state", "VT");
        address.put("postalCode", "05046");
        address.put("country", "US");
        return address;
    }
}
