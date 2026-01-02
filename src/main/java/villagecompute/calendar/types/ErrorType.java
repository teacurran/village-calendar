package villagecompute.calendar.types;

/**
 * Standard error response type for REST API endpoints. Serializes to JSON as: {"error": "message"}
 */
public record ErrorType(String error) {

    public static ErrorType of(String message) {
        return new ErrorType(message);
    }
}
