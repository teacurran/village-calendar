package villagecompute.calendar.exceptions;

/**
 * Exception thrown when email sending fails.
 */
public class EmailException extends ApplicationException {

    public EmailException(String message) {
        super(message);
    }

    public EmailException(String message, Throwable cause) {
        super(message, cause);
    }
}
