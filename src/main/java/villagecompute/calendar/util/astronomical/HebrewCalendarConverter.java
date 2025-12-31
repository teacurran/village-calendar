package villagecompute.calendar.util.astronomical;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility class for converting between Gregorian and Hebrew calendar dates.
 *
 * <p>
 * This class provides methods to: - Convert Gregorian dates to Hebrew calendar dates - Convert Hebrew dates to
 * Gregorian dates (approximate) - Check for Hebrew leap years - Get Hebrew month names and lengths
 *
 * <p>
 * Note: The conversion uses simplified algorithms. For production-grade accuracy, consider integrating a specialized
 * library like Hebcal or Jewish Calendar API.
 */
public class HebrewCalendarConverter {

    /** Data class representing a Hebrew date mapping. */
    public static class HebrewDateMapping {
        public LocalDate gregorianDate;
        public String hebrewDate;
        public String holidayName; // null if not a holiday

        public HebrewDateMapping(LocalDate gregorianDate, String hebrewDate, String holidayName) {
            this.gregorianDate = gregorianDate;
            this.hebrewDate = hebrewDate;
            this.holidayName = holidayName;
        }
    }

    // Hebrew month names (transliterated)
    private static final String[] HEBREW_MONTHS = {"Tishrei", // 1 - September/October
            "Cheshvan", // 2 - October/November
            "Kislev", // 3 - November/December
            "Tevet", // 4 - December/January
            "Shevat", // 5 - January/February
            "Adar", // 6 - February/March (Adar I in leap year)
            "Nisan", // 7 - March/April
            "Iyar", // 8 - April/May
            "Sivan", // 9 - May/June
            "Tammuz", // 10 - June/July
            "Av", // 11 - July/August
            "Elul" // 12 - August/September
    };

    private static final String ADAR_II = "Adar II"; // Used in leap years

    /**
     * Check if a Hebrew year is a leap year. Hebrew calendar has a 19-year cycle with leap years in years 3, 6, 8, 11,
     * 14, 17, and 19.
     *
     * @param hebrewYear
     *            The Hebrew year
     * @return true if it's a leap year
     */
    public static boolean isHebrewLeapYear(int hebrewYear) {
        return ((7 * hebrewYear + 1) % 19) < 7;
    }

    /**
     * Get the number of months in a Hebrew year (12 or 13).
     *
     * @param hebrewYear
     *            The Hebrew year
     * @return 13 if leap year, 12 otherwise
     */
    public static int getMonthsInHebrewYear(int hebrewYear) {
        return isHebrewLeapYear(hebrewYear) ? 13 : 12;
    }

    /**
     * Get the Hebrew month name for a given month number.
     *
     * @param month
     *            The month number (1-13)
     * @param hebrewYear
     *            The Hebrew year
     * @return The month name
     */
    public static String getHebrewMonthName(int month, int hebrewYear) {
        boolean isLeap = isHebrewLeapYear(hebrewYear);

        if (isLeap) {
            if (month == 6) {
                return HEBREW_MONTHS[5] + " I"; // Adar I
            } else if (month == 7) {
                return ADAR_II; // Adar II
            } else if (month > 7) {
                return HEBREW_MONTHS[month - 2];
            }
        }

        return HEBREW_MONTHS[month - 1];
    }

    /**
     * Convert a Gregorian date to a Hebrew date string. This is a simplified conversion algorithm.
     *
     * @param gregorianDate
     *            The Gregorian date
     * @return Hebrew date in format "Day Month Year" (e.g., "15 Nisan 5784")
     */
    public static String gregorianToHebrew(LocalDate gregorianDate) {
        // Simplified conversion using approximation
        // For production use, integrate Hebcal or a proper Hebrew calendar library

        // Hebrew year approximately starts in September/October
        // Base calculation: Hebrew year = Gregorian year + 3760 or 3761
        int gregorianYear = gregorianDate.getYear();
        int gregorianMonth = gregorianDate.getMonthValue();

        // If we're in Jan-Aug, we're in the later part of the Hebrew year
        int hebrewYear = gregorianYear + 3760;
        if (gregorianMonth >= 9) {
            hebrewYear = gregorianYear + 3761;
        }

        // Approximate Hebrew month based on Gregorian month
        // This is very simplified and not accurate for actual conversions
        int hebrewMonth = ((gregorianMonth + 3) % 12) + 1;
        if (gregorianMonth >= 9) {
            hebrewMonth = gregorianMonth - 8;
        } else {
            hebrewMonth = gregorianMonth + 4;
        }

        // Approximate day (this is the least accurate part)
        int hebrewDay = gregorianDate.getDayOfMonth();

        String monthName = getHebrewMonthName(hebrewMonth, hebrewYear);
        return String.format("%d %s %d", hebrewDay, monthName, hebrewYear);
    }

    /**
     * Convert a Hebrew date to an approximate Gregorian date. This is a simplified conversion algorithm.
     *
     * @param hebrewYear
     *            The Hebrew year
     * @param hebrewMonth
     *            The Hebrew month (1-13)
     * @param hebrewDay
     *            The Hebrew day (1-30)
     * @return Approximate Gregorian date
     */
    public static LocalDate hebrewToGregorian(int hebrewYear, int hebrewMonth, int hebrewDay) {
        // Very simplified approximation
        // Hebrew year 5784 started approximately on September 16, 2023
        int baseGregorianYear = hebrewYear - 3760;

        // Adjust for month (Hebrew year starts in September/October)
        int gregorianMonth = (hebrewMonth + 8) % 12;
        if (gregorianMonth == 0)
            gregorianMonth = 12;

        // If we're in the early months (Tishrei-Adar), we're in the next Gregorian year
        if (hebrewMonth <= 6) {
            baseGregorianYear++;
        }

        // Create approximate date
        try {
            return LocalDate.of(baseGregorianYear, gregorianMonth, Math.min(hebrewDay, 28));
        } catch (Exception e) {
            // Fallback to first of month if day is invalid
            return LocalDate.of(baseGregorianYear, gregorianMonth, 1);
        }
    }

    /**
     * Generate Hebrew date mappings for all days in a Gregorian year. This includes conversion of each day to its
     * Hebrew equivalent and any associated holidays.
     *
     * @param gregorianYear
     *            The Gregorian year
     * @param holidays
     *            Map of Hebrew holidays (key: "month-day", value: holiday name)
     * @return List of Hebrew date mappings for each day of the year
     */
    public static List<HebrewDateMapping> generateYearMappings(int gregorianYear, Map<String, String> holidays) {
        List<HebrewDateMapping> mappings = new ArrayList<>();

        LocalDate startDate = LocalDate.of(gregorianYear, 1, 1);
        LocalDate endDate = LocalDate.of(gregorianYear, 12, 31);

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            String hebrewDateStr = gregorianToHebrew(date);

            // Extract Hebrew month-day for holiday lookup (simplified)
            // In a real implementation, this would parse the converted date properly
            String holidayName = null;
            if (holidays != null) {
                // Simplified holiday lookup - would need proper parsing in production
                for (Map.Entry<String, String> entry : holidays.entrySet()) {
                    if (hebrewDateStr.contains(entry.getValue())) {
                        holidayName = entry.getValue();
                        break;
                    }
                }
            }

            mappings.add(new HebrewDateMapping(date, hebrewDateStr, holidayName));
        }

        return mappings;
    }

    /**
     * Get the number of days in a Hebrew month. This is a simplified calculation.
     *
     * @param month
     *            The Hebrew month (1-13)
     * @param hebrewYear
     *            The Hebrew year
     * @return Number of days in the month
     */
    public static int getDaysInHebrewMonth(int month, int hebrewYear) {
        boolean isLeap = isHebrewLeapYear(hebrewYear);

        // Days in each Hebrew month (simplified)
        int[] monthDays = {30, 29, 30, 29, 30, 29, 30, 29, 30, 29, 30, 29};

        if (isLeap && month == 6) {
            // Adar I in leap year
            return 30;
        } else if (isLeap && month == 7) {
            // Adar II in leap year
            return 29;
        } else if (month == 2) {
            // Cheshvan can be 29 or 30 days (simplified)
            return isLongCheshvan(hebrewYear) ? 30 : 29;
        } else if (month == 3) {
            // Kislev can be 29 or 30 days (simplified)
            return isLongKislev(hebrewYear) ? 30 : 29;
        } else {
            int adjustedMonth = isLeap && month > 6 ? month - 1 : month;
            return monthDays[adjustedMonth - 1];
        }
    }

    /**
     * Determine if Cheshvan has 30 days in the given year. This is a simplified calculation.
     *
     * @param hebrewYear
     *            The Hebrew year
     * @return true if Cheshvan has 30 days
     */
    private static boolean isLongCheshvan(int hebrewYear) {
        // Simplified: depends on the year type
        return (hebrewYear % 10) == 5 || (hebrewYear % 10) == 8;
    }

    /**
     * Determine if Kislev has 30 days in the given year. This is a simplified calculation.
     *
     * @param hebrewYear
     *            The Hebrew year
     * @return true if Kislev has 30 days
     */
    private static boolean isLongKislev(int hebrewYear) {
        // Simplified: depends on the year type
        return (hebrewYear % 10) != 3 && (hebrewYear % 10) != 6;
    }
}
