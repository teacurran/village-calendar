package villagecompute.calendar.services.exceptions;

/** Exception thrown when calendar generation fails. */
public class CalendarGenerationException extends RuntimeException {

    public CalendarGenerationException(String message) {
        super(message);
    }

    public CalendarGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
