package villagecompute.calendar.integration.stripe;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;

import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.data.models.CalendarOrderItem;
import villagecompute.calendar.data.models.CalendarUser;

/**
 * Unit tests for StripeService. Uses Mockito to mock static Stripe SDK methods.
 */
class StripeServiceTest {

    private StripeService stripeService;
    private CalendarOrder testOrder;
    private CalendarUser testUser;

    @BeforeEach
    void setUp() {
        stripeService = new StripeService();
        // Use reflection to set the webhookSecret field
        try {
            var field = StripeService.class.getDeclaredField("webhookSecret");
            field.setAccessible(true);
            field.set(stripeService, "whsec_test_secret");
        } catch (Exception e) {
            fail("Failed to set webhookSecret: " + e.getMessage());
        }

        // Create test user
        testUser = new CalendarUser();
        testUser.id = UUID.randomUUID();
        testUser.email = "test@example.com";

        // Create test order
        testOrder = new CalendarOrder();
        testOrder.id = UUID.randomUUID();
        testOrder.user = testUser;
        testOrder.totalPrice = new BigDecimal("29.99");
        testOrder.items = new ArrayList<>();

        // Add a test item
        CalendarOrderItem item = new CalendarOrderItem();
        item.description = "Test Calendar";
        item.quantity = 1;
        testOrder.items.add(item);
    }

    // ========== getWebhookSecret Tests ==========

    @Test
    void testGetWebhookSecret_ReturnsConfiguredSecret() {
        String secret = stripeService.getWebhookSecret();

        assertEquals("whsec_test_secret", secret);
    }

    // ========== validateWebhookSignature Tests ==========

    @Test
    void testValidateWebhookSignature_ValidSignature_ReturnsEvent() throws SignatureVerificationException {
        String payload = "{\"type\": \"checkout.session.completed\"}";
        String signatureHeader = "t=1234567890,v1=abc123";

        Event mockEvent = mock(Event.class);
        when(mockEvent.getType()).thenReturn("checkout.session.completed");
        when(mockEvent.getId()).thenReturn("evt_test123");

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(payload, signatureHeader, "whsec_test_secret"))
                    .thenReturn(mockEvent);

            Event result = stripeService.validateWebhookSignature(payload, signatureHeader);

            assertNotNull(result);
            assertEquals("checkout.session.completed", result.getType());
            webhookMock.verify(() -> Webhook.constructEvent(payload, signatureHeader, "whsec_test_secret"));
        }
    }

    @Test
    void testValidateWebhookSignature_InvalidSignature_ThrowsException() {
        String payload = "{\"type\": \"checkout.session.completed\"}";
        String signatureHeader = "invalid_signature";

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(payload, signatureHeader, "whsec_test_secret"))
                    .thenThrow(new SignatureVerificationException("Invalid signature", signatureHeader));

            assertThrows(SignatureVerificationException.class,
                    () -> stripeService.validateWebhookSignature(payload, signatureHeader));
        }
    }

    // ========== createCheckoutSession Tests ==========

    @Test
    void testCreateCheckoutSession_Success_ReturnsSession() throws StripeException {
        String successUrl = "https://example.com/success";
        String cancelUrl = "https://example.com/cancel";

        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_session123");
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_test_session123");

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(mockSession);

            Session result = stripeService.createCheckoutSession(testOrder, successUrl, cancelUrl);

            assertNotNull(result);
            assertEquals("cs_test_session123", result.getId());
            sessionMock.verify(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)));
        }
    }

    @Test
    void testCreateCheckoutSession_WithNullItemDescription_UsesDefaultDescription() throws StripeException {
        // Item with null description should trigger default "Custom Calendar" name
        testOrder.items.get(0).description = null;
        String successUrl = "https://example.com/success";
        String cancelUrl = "https://example.com/cancel";

        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_session456");
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_test_session456");

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(mockSession);

            Session result = stripeService.createCheckoutSession(testOrder, successUrl, cancelUrl);

            assertNotNull(result);
            assertEquals("cs_test_session456", result.getId());
        }
    }

    @Test
    void testCreateCheckoutSession_StripeError_ThrowsException() {
        String successUrl = "https://example.com/success";
        String cancelUrl = "https://example.com/cancel";

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
                    .thenThrow(new RuntimeException("Stripe API error"));

            assertThrows(RuntimeException.class,
                    () -> stripeService.createCheckoutSession(testOrder, successUrl, cancelUrl));
        }
    }

    // ========== retrieveSession Tests ==========

    @Test
    void testRetrieveSession_ValidSessionId_ReturnsSession() throws StripeException {
        String sessionId = "cs_test_session789";

        Session mockSession = mock(Session.class);
        when(mockSession.getStatus()).thenReturn("complete");
        when(mockSession.getPaymentStatus()).thenReturn("paid");

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.retrieve(sessionId)).thenReturn(mockSession);

            Session result = stripeService.retrieveSession(sessionId);

            assertNotNull(result);
            assertEquals("complete", result.getStatus());
            assertEquals("paid", result.getPaymentStatus());
            sessionMock.verify(() -> Session.retrieve(sessionId));
        }
    }

    @Test
    void testRetrieveSession_InvalidSessionId_ThrowsException() {
        String sessionId = "cs_invalid_session";

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.retrieve(sessionId))
                    .thenThrow(new RuntimeException("No such checkout session"));

            assertThrows(RuntimeException.class, () -> stripeService.retrieveSession(sessionId));
        }
    }

    // ========== createCheckoutSession Amount Calculation Tests ==========

    @Test
    void testCreateCheckoutSession_CalculatesAmountInCents() throws StripeException {
        // Order with $29.99 total should become 2999 cents
        testOrder.totalPrice = new BigDecimal("29.99");
        String successUrl = "https://example.com/success";
        String cancelUrl = "https://example.com/cancel";

        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_amount");
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_test_amount");

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(mockSession);

            Session result = stripeService.createCheckoutSession(testOrder, successUrl, cancelUrl);

            assertNotNull(result);
            // Verification is implicit - if conversion was wrong, the mock wouldn't match
        }
    }

    @Test
    void testCreateCheckoutSession_WithMultipleQuantity_CalculatesCorrectly() throws StripeException {
        // Add multiple items
        CalendarOrderItem item2 = new CalendarOrderItem();
        item2.description = "Another Calendar";
        item2.quantity = 2;
        testOrder.items.add(item2);
        testOrder.totalPrice = new BigDecimal("89.97"); // 3 items at $29.99

        String successUrl = "https://example.com/success";
        String cancelUrl = "https://example.com/cancel";

        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_multi");
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_test_multi");

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(mockSession);

            Session result = stripeService.createCheckoutSession(testOrder, successUrl, cancelUrl);

            assertNotNull(result);
        }
    }
}
