package villagecompute.calendar.api.types;

/**
 * Standard error response type for REST API endpoints. Serializes to JSON as: {"error": "message"}
 */
public record ErrorResponse(String error) {

    public static ErrorResponse of(String message) {
        return new ErrorResponse(message);
    }
}
