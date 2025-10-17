package villagecompute.calendar.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@ApplicationScoped
public class HebrewCalendarService {

    @Inject
    CalendarRenderingService calendarRenderingService;

    // Hebrew month names (transliterated)
    private static final String[] HEBREW_MONTHS = {
        "Tishrei",    // 1 - September/October
        "Cheshvan",   // 2 - October/November
        "Kislev",     // 3 - November/December
        "Tevet",      // 4 - December/January
        "Shevat",     // 5 - January/February
        "Adar",       // 6 - February/March (Adar I in leap year)
        "Nisan",      // 7 - March/April
        "Iyar",       // 8 - April/May
        "Sivan",      // 9 - May/June
        "Tammuz",     // 10 - June/July
        "Av",         // 11 - July/August
        "Elul"        // 12 - August/September
    };

    private static final String ADAR_II = "Adar II"; // Used in leap years

    // Days in each Hebrew month (non-leap year)
    private static final int[] MONTH_DAYS_REGULAR = {
        30, // Tishrei
        29, // Cheshvan (can be 29 or 30)
        30, // Kislev (can be 29 or 30)
        29, // Tevet
        30, // Shevat
        29, // Adar
        30, // Nisan
        29, // Iyar
        30, // Sivan
        29, // Tammuz
        30, // Av
        29  // Elul
    };

    // Hebrew calendar configuration
    public static class HebrewCalendarConfig extends CalendarRenderingService.CalendarConfig {
        public int hebrewYear = 5784; // Current Hebrew year (2023-2024)
        public boolean showHebrewDate = true;
        public boolean showGregorianDate = false;
        public boolean useHebrewNumerals = false;
    }

    /**
     * Check if a Hebrew year is a leap year
     * Hebrew calendar has a 19-year cycle with leap years in years 3, 6, 8, 11, 14, 17, and 19
     */
    public boolean isHebrewLeapYear(int year) {
        return ((7 * year + 1) % 19) < 7;
    }

    /**
     * Get the number of months in a Hebrew year
     */
    public int getMonthsInHebrewYear(int year) {
        return isHebrewLeapYear(year) ? 13 : 12;
    }

    /**
     * Get the number of days in a Hebrew month
     * This is a simplified version - actual calculation is more complex
     */
    public int getDaysInHebrewMonth(int month, int year) {
        boolean isLeap = isHebrewLeapYear(year);

        if (isLeap && month == 6) {
            // Adar I in leap year
            return 30;
        } else if (isLeap && month == 7) {
            // Adar II in leap year
            return 29;
        } else if (month == 2) {
            // Cheshvan can be 29 or 30 days
            return isLongCheshvan(year) ? 30 : 29;
        } else if (month == 3) {
            // Kislev can be 29 or 30 days
            return isLongKislev(year) ? 30 : 29;
        } else {
            int adjustedMonth = isLeap && month > 6 ? month - 1 : month;
            return MONTH_DAYS_REGULAR[adjustedMonth - 1];
        }
    }

    /**
     * Determine if Cheshvan has 30 days in the given year
     * This is a simplified calculation
     */
    private boolean isLongCheshvan(int year) {
        // Simplified: depends on the year type
        // In reality, this depends on complex calculations involving Rosh Hashanah
        return (year % 10) == 5 || (year % 10) == 8;
    }

    /**
     * Determine if Kislev has 30 days in the given year
     * This is a simplified calculation
     */
    private boolean isLongKislev(int year) {
        // Simplified: depends on the year type
        // In reality, this depends on complex calculations involving Rosh Hashanah
        return (year % 10) != 3 && (year % 10) != 6;
    }

    /**
     * Get the Hebrew month name
     */
    public String getHebrewMonthName(int month, int year) {
        boolean isLeap = isHebrewLeapYear(year);

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
     * Get all month names for a Hebrew year
     */
    public List<String> getHebrewYearMonths(int year) {
        List<String> months = new ArrayList<>();
        boolean isLeap = isHebrewLeapYear(year);

        for (int i = 1; i <= (isLeap ? 13 : 12); i++) {
            months.add(getHebrewMonthName(i, year));
        }

        return months;
    }

    /**
     * Convert Hebrew date to approximate Gregorian date
     * This is a simplified conversion - actual conversion is very complex
     */
    public LocalDate hebrewToGregorian(int hebrewYear, int hebrewMonth, int hebrewDay) {
        // Very simplified approximation
        // Hebrew year 5784 started approximately on September 16, 2023
        int baseGregorianYear = hebrewYear - 3760;

        // Adjust for month (Hebrew year starts in September/October)
        int gregorianMonth = (hebrewMonth + 8) % 12;
        if (gregorianMonth == 0) gregorianMonth = 12;

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
     * Get Hebrew holidays for a given year
     * Returns holidays in format suitable for Hebrew calendar display
     * @param hebrewYear The Hebrew year
     * @param holidaySet The holiday set to use (e.g., "HEBREW_RELIGIOUS", "HEBREW_CULTURAL", etc.)
     */
    public Map<String, String> getHebrewHolidays(int hebrewYear, String holidaySet) {
        Map<String, String> holidays = new HashMap<>();

        // Default to religious holidays if no set specified
        if (holidaySet == null || holidaySet.isEmpty()) {
            holidaySet = "HEBREW_RELIGIOUS";
        }

        if ("HEBREW_RELIGIOUS".equals(holidaySet) || "HEBREW_ALL".equals(holidaySet)) {
            // Major religious holidays (month-day format)
            holidays.put("1-1", "Rosh Hashanah (Day 1)");
            holidays.put("1-2", "Rosh Hashanah (Day 2)");
            holidays.put("1-10", "Yom Kippur");
            holidays.put("1-15", "Sukkot (Day 1)");
            holidays.put("1-16", "Sukkot (Day 2)");
            holidays.put("1-17", "Sukkot (Chol HaMoed)");
            holidays.put("1-18", "Sukkot (Chol HaMoed)");
            holidays.put("1-19", "Sukkot (Chol HaMoed)");
            holidays.put("1-20", "Sukkot (Chol HaMoed)");
            holidays.put("1-21", "Sukkot (Hoshana Rabbah)");
            holidays.put("1-22", "Shemini Atzeret");
            holidays.put("1-23", "Simchat Torah");

            holidays.put("3-25", "Chanukah (Day 1)");
            holidays.put("3-26", "Chanukah (Day 2)");
            holidays.put("3-27", "Chanukah (Day 3)");
            holidays.put("3-28", "Chanukah (Day 4)");
            holidays.put("3-29", "Chanukah (Day 5)");
            holidays.put("3-30", "Chanukah (Day 6)");
            holidays.put("4-1", "Chanukah (Day 7)");
            holidays.put("4-2", "Chanukah (Day 8)");

            holidays.put("5-15", "Tu BiShvat");

            // Purim - depends on leap year
            if (isHebrewLeapYear(hebrewYear)) {
                holidays.put("7-14", "Purim");
                holidays.put("7-15", "Shushan Purim");
            } else {
                holidays.put("6-14", "Purim");
                holidays.put("6-15", "Shushan Purim");
            }

            // Passover (always in Nisan, which is month 7 in regular year, 8 in leap year)
            int nisanMonth = isHebrewLeapYear(hebrewYear) ? 8 : 7;
            holidays.put(nisanMonth + "-15", "Passover (Day 1)");
            holidays.put(nisanMonth + "-16", "Passover (Day 2)");
            holidays.put(nisanMonth + "-17", "Passover (Chol HaMoed)");
            holidays.put(nisanMonth + "-18", "Passover (Chol HaMoed)");
            holidays.put(nisanMonth + "-19", "Passover (Chol HaMoed)");
            holidays.put(nisanMonth + "-20", "Passover (Chol HaMoed)");
            holidays.put(nisanMonth + "-21", "Passover (Day 7)");
            holidays.put(nisanMonth + "-22", "Passover (Day 8)");

            // More holidays
            int iyarMonth = isHebrewLeapYear(hebrewYear) ? 9 : 8;
            holidays.put(iyarMonth + "-18", "Lag BaOmer");

            int sivanMonth = isHebrewLeapYear(hebrewYear) ? 10 : 9;
            holidays.put(sivanMonth + "-6", "Shavuot (Day 1)");
            holidays.put(sivanMonth + "-7", "Shavuot (Day 2)");

            int avMonth = isHebrewLeapYear(hebrewYear) ? 12 : 11;
            holidays.put(avMonth + "-9", "Tisha B'Av");
        }

        return holidays;
    }

    /**
     * Generate Hebrew calendar SVG in grid layout
     */
    public String generateHebrewCalendarSVG(HebrewCalendarConfig config, String holidaySet) {
        StringBuilder svg = new StringBuilder();

        // Grid layout dimensions (similar to Gregorian grid)
        int cellWidth = config.compactMode ? 40 : 50;
        int cellHeight = config.compactMode ? 60 : 75;
        int headerHeight = 100;
        int monthLabelWidth = cellWidth * 2; // Wider for Hebrew month names

        // Get number of months in this Hebrew year
        int monthsInYear = getMonthsInHebrewYear(config.hebrewYear);

        // Grid dimensions: 32 columns (2 for month name + 30 for max days) x months rows
        int gridWidth = 32 * cellWidth;
        int gridHeight = monthsInYear * cellHeight;
        int svgWidth = gridWidth;
        int svgHeight = gridHeight + headerHeight;

        // Start SVG
        svg.append(String.format(
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\" preserveAspectRatio=\"xMidYMid meet\">%n",
            svgWidth, svgHeight, svgWidth, svgHeight
        ));

        // Add styles
        svg.append("<style>%n");
        svg.append(".year-text { fill: #000; font-family: Helvetica, Arial, sans-serif; font-size: 60px; font-weight: bold; }%n");
        svg.append(".year-subtitle { fill: #666; font-family: Helvetica, Arial, sans-serif; font-size: 20px; }%n");
        svg.append(".month-name { fill: #000; font-family: Helvetica, Arial, sans-serif; font-size: 16px; font-weight: bold; }%n");
        svg.append(".day-text { fill: #000; font-family: Helvetica, Arial, sans-serif; font-size: 12px; }%n");
        svg.append(".day-header { fill: #666; font-family: Arial, sans-serif; font-size: 10px; }%n");
        svg.append(".grid-line { stroke: #ccc; stroke-width: 0.5; fill: rgba(255, 255, 255, 0); }%n");
        svg.append(".shabbat-bg { fill: #f0f0ff; }%n");
        svg.append(".holiday-text { fill: #fff; font-family: Arial, sans-serif; font-size: 7px; text-anchor: middle; }%n");
        svg.append("</style>%n");

        // Add year header
        svg.append(String.format(
            "<text x=\"50\" y=\"60\" class=\"year-text\">%d</text>%n",
            config.hebrewYear
        ));

        // Add Gregorian year subtitle
        int gregorianStart = config.hebrewYear - 3760;
        int gregorianEnd = gregorianStart + 1;
        svg.append(String.format(
            "<text x=\"50\" y=\"85\" class=\"year-subtitle\">%d-%d CE</text>%n",
            gregorianStart, gregorianEnd
        ));

        // Add day number headers (1-30)
        for (int day = 1; day <= 30; day++) {
            int x = monthLabelWidth + (day - 1) * cellWidth + cellWidth / 2;
            int y = headerHeight - 10;
            svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" class=\"day-header\" text-anchor=\"middle\">%d</text>%n",
                x, y, day
            ));
        }

        // Generate grid for each Hebrew month (row)
        Map<String, String> holidays =  new HashMap<>();

        for (int month = 1; month <= monthsInYear; month++) {
            String monthName = getHebrewMonthName(month, config.hebrewYear);
            int daysInMonth = getDaysInHebrewMonth(month, config.hebrewYear);
            int rowY = (month - 1) * cellHeight + headerHeight;
          YearMonth yearMonth = YearMonth.of(config.hebrewYear, month);

            // Draw month name (rotated if configured)
            if (config.rotateMonthNames) {
                svg.append(String.format(
                    "<text x=\"%d\" y=\"%d\" class=\"month-name\" transform=\"rotate(-90 %d %d)\" text-anchor=\"middle\">%s</text>%n",
                    monthLabelWidth / 2, rowY + cellHeight / 2,
                    monthLabelWidth / 2, rowY + cellHeight / 2,
                    monthName
                ));
            } else {
                svg.append(String.format(
                    "<text x=\"%d\" y=\"%d\" class=\"month-name\" text-anchor=\"middle\">%s</text>%n",
                    monthLabelWidth / 2, rowY + cellHeight / 2 + 5,
                    monthName
                ));
            }

            // Draw cells for each day
            for (int day = 1; day <= 30; day++) {
                int cellX = monthLabelWidth + (day - 1) * cellWidth;
                int cellY = rowY;
                LocalDate date;
                try {
                  date = yearMonth.atDay(day);
                } catch (Exception e) {
                  date = null;
                }

                // Draw cell background
                int weekendIndex = 0; // Track weekend index for Vermont colors
                if (day <= daysInMonth) {
                    // Check if this is Shabbat (simplified - every 7th day starting from Saturday)
                    // In reality, this would need proper Hebrew date to day-of-week conversion
                    boolean isShabbat = (day % 7) == 0;

                    if (isShabbat && config.highlightWeekends) {
                      String cellBackground = CalendarRenderingService.getCellBackgroundColor(config, date, month, day, true, weekendIndex - 1);

                        svg.append(String.format(
                            "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" class=\"shabbat-bg\"/>%n",
                            cellX, cellY, cellWidth, cellHeight
                        ));
                    }

                    // Draw grid lines if enabled
                    if (config.showGrid) {
                      String pdfSafeColor = CalendarRenderingService.convertColorForPDF("rgba(255, 255, 255, 0)");
                      svg.append(String.format(
                        "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" class=\"grid-line\" fill=\"%s\"/>%n",
                        cellX, cellY, cellWidth, cellHeight, pdfSafeColor
                      ));
                    }

                    // Draw day number
                    if (config.showDayNumbers) {
                        svg.append(String.format(
                            "<text x=\"%d\" y=\"%d\" class=\"day-text\" text-anchor=\"middle\">%d</text>%n",
                            cellX + cellWidth / 2, cellY + 15, day
                        ));
                    }

                    // Check for holidays
                    String holidayKey = month + "-" + day;
                    String holidayName = holidays.get(holidayKey);

                    // Calculate moon illumination for this day
                    // This is simplified - in reality would need Hebrew to Gregorian date conversion
                    LocalDate approximateDate = hebrewToGregorian(config.hebrewYear, month, day);

                    // Draw moon with illumination
                    if (config.showMoonIllumination) {
                        int moonX = cellX + cellWidth / 2;
                        int moonY = cellY + cellHeight / 2 + config.moonOffsetY;

                        // Generate moon illumination
                        svg.append(calendarRenderingService.generateMoonIlluminationSVG(
                            approximateDate, moonX, moonY,
                            config.latitude, config.longitude, config
                        ));

                        // If there's a holiday, display it inside the moon
                        if (holidayName != null) {
                            // Wrap text if too long
                            drawWrappedTextInMoon(svg, holidayName, moonX, moonY, config.moonSize);
                        }
                    } else if (holidayName != null) {
                        // Show holiday text without moon
                        svg.append(String.format(
                            "<text x=\"%d\" y=\"%d\" class=\"holiday-text\" font-size=\"8\">%s</text>%n",
                            cellX + cellWidth / 2, cellY + cellHeight - 10,
                            holidayName.length() > 10 ? holidayName.substring(0, 10) : holidayName
                        ));
                    }
                } else {
                    // Day doesn't exist in this month - draw empty cell with light background
                    if (config.showGrid) {
                        svg.append(String.format(
                            "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"#f9f9f9\" stroke=\"#eee\" stroke-width=\"0.5\"/>%n",
                            cellX, cellY, cellWidth, cellHeight
                        ));
                    }
                }
            }
        }

        // Close SVG
        svg.append("</svg>");

        return svg.toString();
    }

    /**
     * Draw wrapped text inside moon circle
     */
    private void drawWrappedTextInMoon(StringBuilder svg, String text, int centerX, int centerY, int radius) {
        // Split text into words for wrapping
        String[] words = text.split(" ");
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();

        // Maximum characters per line based on moon size
        int maxCharsPerLine = radius / 3;

        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > maxCharsPerLine && currentLine.length() > 0) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder();
            }
            if (currentLine.length() > 0) {
                currentLine.append(" ");
            }
            currentLine.append(word);
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        // Calculate vertical spacing
        int lineHeight = 8;
        int totalHeight = lines.size() * lineHeight;
        int startY = centerY - totalHeight / 2 + lineHeight / 2;

        // Draw each line of text
        for (int i = 0; i < lines.size(); i++) {
            svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" class=\"holiday-text\" dominant-baseline=\"middle\">%s</text>%n",
                centerX, startY + i * lineHeight,
                lines.get(i)
            ));
        }
    }
}
