package villagecompute.calendar.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for application exception classes.
 */
class ApplicationExceptionTest {

    // ========== ApplicationException Tests ==========

    @Test
    void testApplicationException_MessageOnly_StoresMessage() {
        ApplicationException exception = new ApplicationException("Test error message");

        assertEquals("Test error message", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testApplicationException_WithCause_StoresMessageAndCause() {
        IllegalStateException cause = new IllegalStateException("Original error");
        ApplicationException exception = new ApplicationException("Wrapped error", cause);

        assertEquals("Wrapped error", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void testApplicationException_IsRuntimeException() {
        ApplicationException exception = new ApplicationException("Test");
        assertTrue(exception instanceof RuntimeException);
    }

    // ========== EmailException Tests ==========

    @Test
    void testEmailException_MessageOnly_StoresMessage() {
        EmailException exception = new EmailException("Failed to send email");

        assertEquals("Failed to send email", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testEmailException_WithCause_StoresMessageAndCause() {
        RuntimeException cause = new RuntimeException("SMTP connection failed");
        EmailException exception = new EmailException("Email sending failed", cause);

        assertEquals("Email sending failed", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void testEmailException_ExtendsApplicationException() {
        EmailException exception = new EmailException("Test");
        assertTrue(exception instanceof ApplicationException);
    }

    // ========== PaymentException Tests ==========

    @Test
    void testPaymentException_MessageOnly_StoresMessage() {
        PaymentException exception = new PaymentException("Payment declined");

        assertEquals("Payment declined", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testPaymentException_WithCause_StoresMessageAndCause() {
        Exception cause = new Exception("Stripe API error");
        PaymentException exception = new PaymentException("Payment processing failed", cause);

        assertEquals("Payment processing failed", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void testPaymentException_ExtendsApplicationException() {
        PaymentException exception = new PaymentException("Test");
        assertTrue(exception instanceof ApplicationException);
    }

    // ========== RenderingException Tests ==========

    @Test
    void testRenderingException_MessageOnly_StoresMessage() {
        RenderingException exception = new RenderingException("PDF generation failed");

        assertEquals("PDF generation failed", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testRenderingException_WithCause_StoresMessageAndCause() {
        Exception cause = new Exception("Font not found");
        RenderingException exception = new RenderingException("Failed to render document", cause);

        assertEquals("Failed to render document", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void testRenderingException_ExtendsApplicationException() {
        RenderingException exception = new RenderingException("Test");
        assertTrue(exception instanceof ApplicationException);
    }

    // ========== Exception Hierarchy Tests ==========

    @Test
    void testAllExceptions_AreRuntimeExceptions() {
        assertInstanceOf(RuntimeException.class, new ApplicationException("Test"));
        assertInstanceOf(RuntimeException.class, new EmailException("Test"));
        assertInstanceOf(RuntimeException.class, new PaymentException("Test"));
        assertInstanceOf(RuntimeException.class, new RenderingException("Test"));
    }

    @Test
    void testExceptionHierarchy_AllExtendApplicationException() {
        assertTrue(ApplicationException.class.isAssignableFrom(EmailException.class));
        assertTrue(ApplicationException.class.isAssignableFrom(PaymentException.class));
        assertTrue(ApplicationException.class.isAssignableFrom(RenderingException.class));
    }

    @Test
    void testExceptions_CanBeCaught_AsApplicationException() {
        try {
            throw new EmailException("Email failed");
        } catch (ApplicationException e) {
            assertEquals("Email failed", e.getMessage());
        }

        try {
            throw new PaymentException("Payment failed");
        } catch (ApplicationException e) {
            assertEquals("Payment failed", e.getMessage());
        }

        try {
            throw new RenderingException("Rendering failed");
        } catch (ApplicationException e) {
            assertEquals("Rendering failed", e.getMessage());
        }
    }
}
