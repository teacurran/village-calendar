package villagecompute.calendar.services.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import villagecompute.calendar.data.models.ItemAsset;
import villagecompute.calendar.exceptions.EmailException;
import villagecompute.calendar.exceptions.RenderingException;
import villagecompute.calendar.services.EmailService;
import villagecompute.calendar.services.PDFRenderingService;
import villagecompute.calendar.services.exceptions.DelayedJobException;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Unit tests for {@link OrderEmailJobHandler#run(String)}.
 *
 * <p>
 * Exercises the branches of the handler:
 * <ul>
 * <li>Order not found (non-recoverable)</li>
 * <li>No customer email available (non-recoverable)</li>
 * <li>customerEmail fallback to user.email</li>
 * <li>Successful send to both customer and admin</li>
 * <li>EmailService failure (recoverable=true)</li>
 * <li>Preview image generation with valid SVG asset</li>
 * <li>Preview image generation when PDF rendering fails (continues without preview)</li>
 * <li>Preview image generation when item has no main asset</li>
 * <li>Preview image generation when main asset svgContent is null or empty</li>
 * <li>Handler annotation metadata</li>
 * </ul>
 */
@QuarkusTest
class OrderEmailJobHandlerTest {

    @Inject
    OrderEmailJobHandler handler;

    @Inject
    ObjectMapper objectMapper;

    @InjectMock
    EmailService emailService;

    @InjectMock
    PDFRenderingService pdfRenderingService;

    private CalendarUser testUser;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean any previous test data.
        CalendarOrderItem.deleteAll();
        CalendarOrder.deleteAll();
        CalendarUser.delete("oauthSubject like ?1", "email-test-%");
        ItemAsset.deleteAll();

        testUser = new CalendarUser();
        testUser.oauthProvider = "GOOGLE";
        testUser.oauthSubject = "email-test-" + System.nanoTime();
        testUser.email = "email-customer@villagecompute.com";
        testUser.displayName = "Email Test Customer";
        testUser.persist();

        // Reset mocks - default to success behavior.
        reset(emailService);
        reset(pdfRenderingService);
        doNothing().when(emailService).sendHtmlEmail(anyString(), anyString(), anyString(), anyString());
        when(pdfRenderingService.renderSVGToPNGDataUri(anyString(), anyInt()))
                .thenReturn("data:image/png;base64,FAKEPNG");
    }

    @AfterEach
    @Transactional
    void tearDown() {
        CalendarOrderItem.deleteAll();
        CalendarOrder.deleteAll();
        CalendarUser.delete("oauthSubject like ?1", "email-test-%");
        ItemAsset.deleteAll();
    }

    @Test
    void run_withMissingOrder_throwsNonRecoverableException() {
        // Given - a random UUID that does not match any order
        String missingId = UUID.randomUUID().toString();

        // When / Then
        DelayedJobException ex = assertThrows(DelayedJobException.class, () -> {
            handler.run(missingId);
        });

        assertFalse(ex.isRecoverable(), "Order not found should be non-recoverable");
        assertTrue(ex.getMessage().contains("Order not found"), "Message should mention 'Order not found'");

        // No emails should have been dispatched.
        verify(emailService, never()).sendHtmlEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @Transactional
    void run_withNoCustomerEmailAndNoUser_throwsNonRecoverableException() {
        // Given - an order with null user and null customerEmail
        CalendarOrder order = createOrder();
        order.user = null;
        order.customerEmail = null;
        order.persist();

        String orderId = order.id.toString();

        // When / Then
        DelayedJobException ex = assertThrows(DelayedJobException.class, () -> {
            handler.run(orderId);
        });

        assertFalse(ex.isRecoverable(), "Missing email should be non-recoverable");
        assertTrue(ex.getMessage().contains("No customer email found"),
                "Message should mention missing customer email");

        verify(emailService, never()).sendHtmlEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @Transactional
    void run_withCustomerEmailNullButUserPresent_usesUserEmail() throws Exception {
        // Given - order.customerEmail is null but order.user.email is populated (fallback path)
        CalendarOrder order = createOrder();
        order.customerEmail = null;
        order.persist();

        // When
        handler.run(order.id.toString());

        // Then - customer email goes to the user's email
        verify(emailService, atLeastOnce()).sendHtmlEmail(anyString(), eq(testUser.email),
                contains("Order Confirmation"), anyString());
    }

    @Test
    @Transactional
    void run_withValidOrder_sendsBothCustomerAndAdminEmails() throws Exception {
        // Given - a normal order
        CalendarOrder order = createOrder();
        order.persist();

        // When
        handler.run(order.id.toString());

        // Then - two emails total: one customer, one admin
        verify(emailService, times(2)).sendHtmlEmail(anyString(), anyString(), anyString(), anyString());

        // Customer subject should contain order number
        verify(emailService).sendHtmlEmail(anyString(), eq(order.customerEmail),
                eq("Order Confirmation - Village Compute Calendar #" + order.orderNumber), anyString());

        // Admin subject should contain "New Order Received"
        verify(emailService).sendHtmlEmail(anyString(), anyString(), eq("New Order Received - #" + order.orderNumber),
                anyString());
    }

    @Test
    @Transactional
    void run_whenCustomerEmailSendFails_throwsRecoverableException() {
        // Given - email service throws when sending
        CalendarOrder order = createOrder();
        order.persist();
        String orderId = order.id.toString();

        doThrow(new EmailException("SMTP transport offline", new RuntimeException("connection refused")))
                .when(emailService).sendHtmlEmail(anyString(), anyString(), anyString(), anyString());

        // When / Then
        DelayedJobException ex = assertThrows(DelayedJobException.class, () -> {
            handler.run(orderId);
        });

        assertTrue(ex.isRecoverable(), "Email failures should be retried (recoverable=true)");
        assertTrue(ex.getMessage().contains("Failed to send order confirmation email"),
                "Message should describe the send failure");
        assertNotNull(ex.getCause(), "Cause should be preserved");
    }

    @Test
    @Transactional
    void run_withItemHavingMainSvgAsset_generatesPreviewImage() throws Exception {
        // Given - an order with an item that has a non-empty main SVG asset
        CalendarOrder order = createOrder();
        CalendarOrderItem item = order.items.iterator().next();
        ItemAsset asset = ItemAsset.create(ItemAsset.KEY_MAIN, "<svg><rect/></svg>");
        asset.persist();
        item.addAsset(asset);
        item.persist();
        order.persist();

        // When
        handler.run(order.id.toString());

        // Then - pdfRenderingService was invoked for the asset
        verify(pdfRenderingService, atLeastOnce()).renderSVGToPNGDataUri(eq("<svg><rect/></svg>"), anyInt());
        // And both emails were still sent
        verify(emailService, times(2)).sendHtmlEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @Transactional
    void run_whenPreviewRenderingFails_continuesWithoutPreviewAndSendsEmails() throws Exception {
        // Given - an order with a main SVG asset, but the renderer throws
        CalendarOrder order = createOrder();
        CalendarOrderItem item = order.items.iterator().next();
        ItemAsset asset = ItemAsset.create(ItemAsset.KEY_MAIN, "<svg>broken</svg>");
        asset.persist();
        item.addAsset(asset);
        item.persist();
        order.persist();

        when(pdfRenderingService.renderSVGToPNGDataUri(anyString(), anyInt()))
                .thenThrow(new RenderingException("Batik failed", new RuntimeException("bad svg")));

        // When - run should not throw; preview is skipped per the handler's catch block
        handler.run(order.id.toString());

        // Then - emails still go out
        verify(emailService, times(2)).sendHtmlEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @Transactional
    void run_withItemHavingNoMainAsset_skipsPreviewAndSendsEmails() throws Exception {
        // Given - an order with an item that has no main asset (assets set is empty)
        CalendarOrder order = createOrder();
        order.persist();

        // When
        handler.run(order.id.toString());

        // Then - renderer never called, but emails still sent.
        verify(pdfRenderingService, never()).renderSVGToPNGDataUri(anyString(), anyInt());
        verify(emailService, times(2)).sendHtmlEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @Transactional
    void run_withItemHavingMainAssetButEmptySvg_skipsPreview() throws Exception {
        // Given - asset exists but svgContent is empty string
        CalendarOrder order = createOrder();
        CalendarOrderItem item = order.items.iterator().next();
        ItemAsset asset = ItemAsset.create(ItemAsset.KEY_MAIN, "");
        asset.persist();
        item.addAsset(asset);
        item.persist();
        order.persist();

        // When
        handler.run(order.id.toString());

        // Then - renderer never called because svgContent.isEmpty()
        verify(pdfRenderingService, never()).renderSVGToPNGDataUri(anyString(), anyInt());
        // Emails still go out.
        verify(emailService, times(2)).sendHtmlEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @Transactional
    void run_withOrderHavingNoItems_sendsEmailsWithoutPreviewCalls() throws Exception {
        // Given - an order with no items at all
        CalendarOrder order = new CalendarOrder();
        order.user = testUser;
        order.customerEmail = testUser.email;
        order.subtotal = new BigDecimal("0.00");
        order.shippingCost = BigDecimal.ZERO;
        order.taxAmount = BigDecimal.ZERO;
        order.totalPrice = new BigDecimal("0.00");
        order.status = CalendarOrder.STATUS_PAID;
        order.orderNumber = "VC-" + UUID.randomUUID();
        order.shippingAddress = createShippingAddress();
        order.paidAt = Instant.now();
        order.persist();

        // When
        handler.run(order.id.toString());

        // Then - renderer never called (no items); emails still sent.
        verify(pdfRenderingService, never()).renderSVGToPNGDataUri(anyString(), anyInt());
        verify(emailService, times(2)).sendHtmlEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void getHandlerConfig_returnsExpectedAnnotation() {
        // Sanity check on the @DelayedJobConfig metadata.
        // Read from the source class because CDI proxies do not retain annotations.
        DelayedJobConfig config = OrderEmailJobHandler.class.getAnnotation(DelayedJobConfig.class);
        assertNotNull(config, "Handler should be annotated with @DelayedJobConfig");
        assertEquals(10, config.priority(), "Priority should be 10");
        assertEquals("Order confirmation email sender", config.description());
    }

    // ==================== Helpers ====================

    private CalendarOrder createOrder() {
        CalendarOrder order = new CalendarOrder();
        order.user = testUser;
        order.customerEmail = testUser.email;
        order.subtotal = new BigDecimal("29.99");
        order.shippingCost = new BigDecimal("5.99");
        order.taxAmount = BigDecimal.ZERO;
        order.totalPrice = new BigDecimal("35.98");
        order.status = CalendarOrder.STATUS_PAID;
        order.orderNumber = "VC-" + UUID.randomUUID();
        order.shippingAddress = createShippingAddress();
        order.paidAt = Instant.now();
        order.persist();

        CalendarOrderItem item = new CalendarOrderItem();
        item.order = order;
        item.description = "Email Test Calendar";
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
        address.put("name", "Email Tester");
        address.put("line1", "2 Email Way");
        address.put("city", "Groton");
        address.put("state", "VT");
        address.put("postalCode", "05046");
        address.put("country", "US");
        return address;
    }
}
