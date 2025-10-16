package villagecompute.calendar.services.exceptions;

/**
 * Exception thrown by DelayedJob handlers to indicate job failure.
 * Includes information about whether the job should be retried.
 */
public class DelayedJobException extends Exception {

    private final boolean recoverable;

    /**
     * Create a DelayedJobException.
     *
     * @param recoverable Whether this job should be retried
     * @param message Error message
     */
    public DelayedJobException(boolean recoverable, String message) {
        super(message);
        this.recoverable = recoverable;
    }

    /**
     * Create a DelayedJobException with a cause.
     *
     * @param recoverable Whether this job should be retried
     * @param message Error message
     * @param cause Underlying exception
     */
    public DelayedJobException(boolean recoverable, String message, Throwable cause) {
        super(message, cause);
        this.recoverable = recoverable;
    }

    /**
     * Check if this job should be retried.
     *
     * @return true if the job is recoverable and should be retried
     */
    public boolean isRecoverable() {
        return recoverable;
    }
}
