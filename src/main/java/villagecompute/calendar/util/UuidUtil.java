package villagecompute.calendar.util;

import java.util.Optional;
import java.util.UUID;

import org.jboss.logging.Logger;

/**
 * Utility class for UUID parsing and validation. Consolidates duplicated UUID parsing patterns across the codebase.
 */
public final class UuidUtil {

    private static final Logger LOG = Logger.getLogger(UuidUtil.class);

    // Field name constants for use with parse() method
    public static final String FIELD_CALENDAR_ID = "calendar ID";
    public static final String FIELD_ORDER_ID = "order ID";
    public static final String FIELD_USER_ID = "user ID";
    public static final String FIELD_TEMPLATE_ID = "template ID";

    private UuidUtil() {
        // Utility class
    }

    /**
     * Parse a string to UUID, throwing IllegalArgumentException with descriptive message on failure.
     *
     * @param value
     *            String to parse
     * @param fieldName
     *            Name of the field for error messages (e.g., "calendar ID", "order ID")
     * @return Parsed UUID
     * @throws IllegalArgumentException
     *             if the string is not a valid UUID
     */
    public static UUID parse(String value, String fieldName) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            LOG.errorf("Invalid %s format: %s", fieldName, value);
            throw new IllegalArgumentException("Invalid " + fieldName + " format");
        }
    }

    /**
     * Parse a string to UUID, returning Optional.empty() on failure instead of throwing.
     *
     * @param value
     *            String to parse
     * @return Optional containing the UUID, or empty if parsing failed
     */
    public static Optional<UUID> tryParse(String value) {
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Check if a string is a valid UUID format.
     *
     * @param value
     *            String to check
     * @return true if valid UUID format, false otherwise
     */
    public static boolean isValid(String value) {
        return tryParse(value).isPresent();
    }
}
