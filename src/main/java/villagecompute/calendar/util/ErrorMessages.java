package villagecompute.calendar.util;

/**
 * Centralized error message constants to avoid duplicated strings across the codebase.
 */
public final class ErrorMessages {

    private ErrorMessages() {
        // Utility class
    }

    // Session-related errors
    public static final String NO_SESSION_FOUND = "No session found";

    // Calendar-related errors
    public static final String CALENDAR_NOT_FOUND = "Calendar not found";
    public static final String CALENDAR_NOT_PUBLIC = "Calendar is not public";
    public static final String CANNOT_EDIT_CALENDAR = "Cannot edit this calendar";
    public static final String TEMPLATE_NOT_FOUND = "Template not found";

    // User-related errors
    public static final String USER_NOT_FOUND = "User not found";
    public static final String NOT_AUTHORIZED_UPDATE = "Not authorized to update this calendar";
    public static final String NOT_AUTHORIZED_VIEW = "Not authorized to view this calendar";
    public static final String NOT_AUTHORIZED_DELETE = "Not authorized to delete this calendar";

    // Order-related errors
    public static final String ORDER_NOT_FOUND = "Order not found";

    // Authentication errors
    public static final String AUTH_FAILED_PREFIX = "Authentication failed: ";
}
