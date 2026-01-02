package villagecompute.calendar.types;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Tests for the API type classes.
 */
class TypesTest {

    // ============================================================================
    // ErrorType Tests
    // ============================================================================

    @Test
    void errorType_RecordAccessor_ReturnsValue() {
        ErrorType error = new ErrorType("Something went wrong");
        assertEquals("Something went wrong", error.error());
    }

    @Test
    void errorType_Of_CreatesInstance() {
        ErrorType error = ErrorType.of("Test error message");
        assertEquals("Test error message", error.error());
    }

    @Test
    void errorType_Equality() {
        ErrorType error1 = ErrorType.of("Same message");
        ErrorType error2 = new ErrorType("Same message");
        assertEquals(error1, error2);
    }

    // ============================================================================
    // SuccessType Tests
    // ============================================================================

    @Test
    void successType_RecordAccessor_ReturnsValue() {
        SuccessType success = new SuccessType("success");
        assertEquals("success", success.status());
    }

    @Test
    void successType_Ok_ReturnsSingletonInstance() {
        SuccessType success1 = SuccessType.ok();
        SuccessType success2 = SuccessType.ok();
        assertSame(success1, success2);
    }

    @Test
    void successType_Ok_HasSuccessStatus() {
        SuccessType success = SuccessType.ok();
        assertEquals("success", success.status());
    }

    // ============================================================================
    // PaymentIntentType Tests
    // ============================================================================

    @Test
    void paymentIntentType_FieldsAssignable() {
        PaymentIntentType intent = new PaymentIntentType();
        intent.id = "pi_test123";
        intent.clientSecret = "secret_abc";
        intent.amount = 2999;
        intent.calendarId = UUID.randomUUID();
        intent.quantity = 2;
        intent.status = "succeeded";

        assertEquals("pi_test123", intent.id);
        assertEquals("secret_abc", intent.clientSecret);
        assertEquals(2999, intent.amount);
        assertNotNull(intent.calendarId);
        assertEquals(2, intent.quantity);
        assertEquals("succeeded", intent.status);
    }

    @Test
    void paymentIntentType_FromCheckoutSession_CreatesInstance() {
        UUID calendarId = UUID.randomUUID();
        PaymentIntentType intent = PaymentIntentType.fromCheckoutSession("cs_test123",
                "https://checkout.stripe.com/xxx", 2999, calendarId, 1);

        assertEquals("cs_test123", intent.id);
        assertEquals("https://checkout.stripe.com/xxx", intent.clientSecret);
        assertEquals(2999, intent.amount);
        assertEquals(calendarId, intent.calendarId);
        assertEquals(1, intent.quantity);
        assertEquals("requires_payment_method", intent.status);
    }

    @Test
    void paymentIntentType_FromCheckoutSession_WithNullCalendarId() {
        PaymentIntentType intent = PaymentIntentType.fromCheckoutSession("cs_test123",
                "https://checkout.stripe.com/xxx", 1500, null, 3);

        assertEquals("cs_test123", intent.id);
        assertNull(intent.calendarId);
        assertEquals(3, intent.quantity);
    }
}
