package villagecompute.calendar.util;

import java.time.Year;

/**
 * Utility class for generating unique order numbers.
 * Order numbers follow the format: VC-YYYY-NNNNN
 * where YYYY is the year and NNNNN is a 5-digit zero-padded sequence number.
 *
 * Example: VC-2025-00001, VC-2025-00002, ..., VC-2025-99999
 */
public class OrderNumberGenerator {

    private static final String ORDER_NUMBER_PREFIX = "VC";
    private static final String ORDER_NUMBER_FORMAT = "%s-%d-%05d";

    /**
     * Generate a unique order number based on the current year and order count.
     *
     * @param year The year for the order (typically current year)
     * @param orderCountThisYear The count of orders already created in this year
     * @return Formatted order number (e.g., "VC-2025-00001")
     */
    public static String generateOrderNumber(int year, long orderCountThisYear) {
        // Sequence number is count + 1 (next order)
        long sequenceNumber = orderCountThisYear + 1;

        return String.format(
            ORDER_NUMBER_FORMAT,
            ORDER_NUMBER_PREFIX,
            year,
            sequenceNumber
        );
    }

    /**
     * Generate order number using current year.
     *
     * @param orderCountThisYear The count of orders already created in this year
     * @return Formatted order number (e.g., "VC-2025-00001")
     */
    public static String generateOrderNumber(long orderCountThisYear) {
        return generateOrderNumber(Year.now().getValue(), orderCountThisYear);
    }
}
