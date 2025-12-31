package villagecompute.calendar.exceptions;

/**
 * Base exception for all application-specific exceptions. Extends RuntimeException so it doesn't need to be declared in
 * method signatures.
 */
public class ApplicationException extends RuntimeException {

    public ApplicationException(String message) {
        super(message);
    }

    public ApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
