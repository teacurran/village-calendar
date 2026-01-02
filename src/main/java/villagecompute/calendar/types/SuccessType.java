package villagecompute.calendar.types;

/**
 * Standard success response type for REST API endpoints. Serializes to JSON as: {"status": "success"}
 */
public record SuccessType(String status) {

    private static final SuccessType SUCCESS = new SuccessType("success");

    public static SuccessType ok() {
        return SUCCESS;
    }
}
