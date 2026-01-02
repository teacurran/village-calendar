package villagecompute.calendar.services.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for service exception classes.
 */
class ServiceExceptionsTest {

    // ========== CalendarGenerationException Tests ==========

    @Test
    void testCalendarGenerationException_MessageOnly_StoresMessage() {
        CalendarGenerationException exception = new CalendarGenerationException("Generation failed");

        assertEquals("Generation failed", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testCalendarGenerationException_WithCause_StoresMessageAndCause() {
        RuntimeException cause = new RuntimeException("SVG parse error");
        CalendarGenerationException exception = new CalendarGenerationException("Failed to generate calendar", cause);

        assertEquals("Failed to generate calendar", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void testCalendarGenerationException_IsRuntimeException() {
        CalendarGenerationException exception = new CalendarGenerationException("Test");
        assertTrue(exception instanceof RuntimeException);
    }

    // ========== DelayedJobException Tests ==========

    @Test
    void testDelayedJobException_Recoverable_ReturnsTrue() {
        DelayedJobException exception = new DelayedJobException(true, "Temporary failure");

        assertTrue(exception.isRecoverable());
        assertEquals("Temporary failure", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testDelayedJobException_NotRecoverable_ReturnsFalse() {
        DelayedJobException exception = new DelayedJobException(false, "Permanent failure");

        assertFalse(exception.isRecoverable());
        assertEquals("Permanent failure", exception.getMessage());
    }

    @Test
    void testDelayedJobException_WithCause_Recoverable() {
        Exception cause = new Exception("Network timeout");
        DelayedJobException exception = new DelayedJobException(true, "API call failed", cause);

        assertTrue(exception.isRecoverable());
        assertEquals("API call failed", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void testDelayedJobException_WithCause_NotRecoverable() {
        Exception cause = new Exception("Invalid data format");
        DelayedJobException exception = new DelayedJobException(false, "Data validation failed", cause);

        assertFalse(exception.isRecoverable());
        assertEquals("Data validation failed", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void testDelayedJobException_IsCheckedException() {
        DelayedJobException exception = new DelayedJobException(true, "Test");
        assertInstanceOf(Exception.class, exception);
        // DelayedJobException extends Exception directly, not RuntimeException
        assertFalse(RuntimeException.class.isAssignableFrom(DelayedJobException.class));
    }

    // ========== StorageException Tests ==========

    @Test
    void testStorageException_MessageOnly_StoresMessage() {
        StorageException exception = new StorageException("Upload failed");

        assertEquals("Upload failed", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testStorageException_WithCause_StoresMessageAndCause() {
        RuntimeException cause = new RuntimeException("Connection refused");
        StorageException exception = new StorageException("R2 storage error", cause);

        assertEquals("R2 storage error", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void testStorageException_IsRuntimeException() {
        StorageException exception = new StorageException("Test");
        assertTrue(exception instanceof RuntimeException);
    }

    // ========== Exception Usage Pattern Tests ==========

    @Test
    void testDelayedJobException_CanBeCaughtAsException() {
        try {
            throw new DelayedJobException(true, "Retry me");
        } catch (Exception e) {
            assertEquals("Retry me", e.getMessage());
        }
    }

    @Test
    void testCalendarGenerationException_CanBeCaughtAsRuntimeException() {
        try {
            throw new CalendarGenerationException("Gen error");
        } catch (RuntimeException e) {
            assertEquals("Gen error", e.getMessage());
        }
    }

    @Test
    void testStorageException_CanBeCaughtAsRuntimeException() {
        try {
            throw new StorageException("Storage error");
        } catch (RuntimeException e) {
            assertEquals("Storage error", e.getMessage());
        }
    }

    @Test
    void testDelayedJobException_RecoverableState_IsImmutable() {
        DelayedJobException recoverable = new DelayedJobException(true, "Test");
        DelayedJobException nonRecoverable = new DelayedJobException(false, "Test");

        // Multiple calls should return consistent values
        assertTrue(recoverable.isRecoverable());
        assertTrue(recoverable.isRecoverable());

        assertFalse(nonRecoverable.isRecoverable());
        assertFalse(nonRecoverable.isRecoverable());
    }
}
