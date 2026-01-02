package villagecompute.calendar.jobs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import villagecompute.calendar.services.SessionService;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests for SessionCleanupJob - scheduled job that cleans up expired guest session calendars.
 */
@QuarkusTest
class SessionCleanupJobTest {

    @Inject
    SessionCleanupJob sessionCleanupJob;

    @InjectMock
    SessionService sessionService;

    @BeforeEach
    void setUp() {
        reset(sessionService);
    }

    @Test
    void testCleanupExpiredSessions_Success_DeletesSessions() {
        // Given - session service returns a count of deleted sessions
        when(sessionService.deleteExpiredSessions()).thenReturn(5);

        // When
        sessionCleanupJob.cleanupExpiredSessions();

        // Then - verify service was called
        verify(sessionService).deleteExpiredSessions();
    }

    @Test
    void testCleanupExpiredSessions_NoExpiredSessions_CompletesSuccessfully() {
        // Given - no expired sessions
        when(sessionService.deleteExpiredSessions()).thenReturn(0);

        // When
        sessionCleanupJob.cleanupExpiredSessions();

        // Then - verify service was called, no exception thrown
        verify(sessionService).deleteExpiredSessions();
    }

    @Test
    void testCleanupExpiredSessions_LargeNumberOfSessions_CompletesSuccessfully() {
        // Given - many expired sessions
        when(sessionService.deleteExpiredSessions()).thenReturn(1000);

        // When
        sessionCleanupJob.cleanupExpiredSessions();

        // Then
        verify(sessionService).deleteExpiredSessions();
    }

    @Test
    void testCleanupExpiredSessions_ServiceThrowsException_DoesNotRethrow() {
        // Given - service throws exception
        when(sessionService.deleteExpiredSessions()).thenThrow(new RuntimeException("Database error"));

        // When - job should handle exception gracefully
        assertDoesNotThrow(() -> sessionCleanupJob.cleanupExpiredSessions());

        // Then - service was still called
        verify(sessionService).deleteExpiredSessions();
    }

    @Test
    void testCleanupExpiredSessions_ServiceThrowsNullPointerException_DoesNotRethrow() {
        // Given - service throws NPE
        when(sessionService.deleteExpiredSessions()).thenThrow(new NullPointerException("Unexpected null"));

        // When - job should handle exception gracefully and not crash scheduler
        assertDoesNotThrow(() -> sessionCleanupJob.cleanupExpiredSessions());

        // Then
        verify(sessionService).deleteExpiredSessions();
    }

    @Test
    void testCleanupExpiredSessions_ServiceThrowsIllegalStateException_DoesNotRethrow() {
        // Given - service throws IllegalStateException
        when(sessionService.deleteExpiredSessions()).thenThrow(new IllegalStateException("Invalid state"));

        // When
        assertDoesNotThrow(() -> sessionCleanupJob.cleanupExpiredSessions());

        // Then
        verify(sessionService).deleteExpiredSessions();
    }

    @Test
    void testCleanupExpiredSessions_CalledMultipleTimes_EachCallIndependent() {
        // Given
        when(sessionService.deleteExpiredSessions()).thenReturn(3).thenReturn(7).thenReturn(0);

        // When
        sessionCleanupJob.cleanupExpiredSessions();
        sessionCleanupJob.cleanupExpiredSessions();
        sessionCleanupJob.cleanupExpiredSessions();

        // Then - each call is independent
        verify(sessionService, times(3)).deleteExpiredSessions();
    }
}
