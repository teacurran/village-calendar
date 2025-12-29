package villagecompute.calendar.util;

import java.security.SecureRandom;

/**
 * Utility class for generating unique order numbers. Order numbers follow the format: VC-XXXX-XXXX
 * where X is an alphanumeric character, making them non-guessable.
 *
 * <p>Example: VC-A3F2-K9M1, VC-B7N4-P2X8
 */
public class OrderNumberGenerator {

    private static final String ORDER_NUMBER_PREFIX = "VC";
    private static final SecureRandom RANDOM = new SecureRandom();

    // Alphanumeric characters excluding confusing ones (0, O, I, l, 1)
    private static final String CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    /**
     * Generate a unique, non-guessable order number. Uses cryptographically secure random
     * characters.
     *
     * @param year The year for the order (unused, kept for API compatibility)
     * @param orderCountThisYear The count of orders (unused, kept for API compatibility)
     * @return Formatted order number (e.g., "VC-A3F2-K9M1")
     */
    public static String generateOrderNumber(int year, long orderCountThisYear) {
        return generateSecureOrderNumber();
    }

    /**
     * Generate order number using current year.
     *
     * @param orderCountThisYear The count of orders (unused, kept for API compatibility)
     * @return Formatted order number (e.g., "VC-A3F2-K9M1")
     */
    public static String generateOrderNumber(long orderCountThisYear) {
        return generateSecureOrderNumber();
    }

    /**
     * Generate a secure, non-guessable order number.
     *
     * @return Formatted order number (e.g., "VC-A3F2-K9M1")
     */
    public static String generateSecureOrderNumber() {
        StringBuilder sb = new StringBuilder(ORDER_NUMBER_PREFIX);
        sb.append("-");

        // Generate first group (4 chars)
        for (int i = 0; i < 4; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }

        sb.append("-");

        // Generate second group (4 chars)
        for (int i = 0; i < 4; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }

        return sb.toString();
    }
}
