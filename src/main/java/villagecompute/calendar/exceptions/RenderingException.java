package villagecompute.calendar.exceptions;

/**
 * Exception thrown when PDF or image rendering fails.
 */
public class RenderingException extends ApplicationException {

    public RenderingException(String message) {
        super(message);
    }

    public RenderingException(String message, Throwable cause) {
        super(message, cause);
    }
}
