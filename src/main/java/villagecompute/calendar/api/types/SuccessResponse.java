package villagecompute.calendar.api.types;

/**
 * Standard success response type for REST API endpoints. Serializes to JSON as: {"status": "success"}
 */
public record SuccessResponse(String status) {

    private static final SuccessResponse SUCCESS = new SuccessResponse("success");

    public static SuccessResponse ok() {
        return SUCCESS;
    }
}
