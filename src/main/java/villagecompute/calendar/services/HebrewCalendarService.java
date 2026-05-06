package villagecompute.calendar.services;

import java.time.LocalDate;
import java.util.*;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import villagecompute.calendar.types.CalendarConfigType;

@ApplicationScoped
public class HebrewCalendarService {

    @Inject
    CalendarRenderingService calendarRenderingService;

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

    // Days in each Hebrew month (non-leap year)
    private static final int[] MONTH_DAYS_REGULAR = {30, // Tishrei
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
            29 // Elul
    };

    // Hebrew calendar configuration
    public static class HebrewCalendarConfig extends CalendarConfigType {
        public int hebrewYear = 5784; // Current Hebrew year (2023-2024)
        public boolean showHebrewDate = true;
        public boolean showGregorianDate = false;
        public boolean useHebrewNumerals = false;
    }

    /**
     * Check if a Hebrew year is a leap year Hebrew calendar has a 19-year cycle with leap years in years 3, 6, 8, 11,
     * 14, 17, and 19
     */
    public boolean isHebrewLeapYear(int year) {
        return ((7 * year + 1) % 19) < 7;
    }

    /** Get the number of months in a Hebrew year */
    public int getMonthsInHebrewYear(int year) {
        return isHebrewLeapYear(year) ? 13 : 12;
    }

    /**
     * Get the number of days in a Hebrew month This is a simplified version - actual calculation is more complex
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

    /** Determine if Cheshvan has 30 days in the given year This is a simplified calculation */
    private boolean isLongCheshvan(int year) {
        // Simplified: depends on the year type
        // In reality, this depends on complex calculations involving Rosh Hashanah
        return (year % 10) == 5 || (year % 10) == 8;
    }

    /** Determine if Kislev has 30 days in the given year This is a simplified calculation */
    private boolean isLongKislev(int year) {
        // Simplified: depends on the year type
        // In reality, this depends on complex calculations involving Rosh Hashanah
        return (year % 10) != 3 && (year % 10) != 6;
    }

    /** Get the Hebrew month name */
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

    /** Get all month names for a Hebrew year */
    public List<String> getHebrewYearMonths(int year) {
        List<String> months = new ArrayList<>();
        boolean isLeap = isHebrewLeapYear(year);

        for (int i = 1; i <= (isLeap ? 13 : 12); i++) {
            months.add(getHebrewMonthName(i, year));
        }

        return months;
    }

    /**
     * Convert Hebrew date to approximate Gregorian date This is a simplified conversion - actual conversion is very
     * complex
     */
    public LocalDate hebrewToGregorian(int hebrewYear, int hebrewMonth, int hebrewDay) {
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
     * Get Hebrew holidays for a given year Returns holidays in format suitable for Hebrew calendar display
     *
     * @param hebrewYear
     *            The Hebrew year
     * @param holidaySet
     *            The holiday set to use (e.g., "HEBREW_RELIGIOUS", "HEBREW_CULTURAL", etc.)
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

    /** Generate Hebrew calendar SVG in grid layout */
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
        int svgHeight = gridHeight + headerHeight;

        appendSvgHeader(svg, gridWidth, svgHeight);
        appendSvgStyles(svg);
        appendYearHeader(svg, config.hebrewYear);
        appendDayNumberHeaders(svg, monthLabelWidth, cellWidth, headerHeight);

        // Generate grid for each Hebrew month (row)
        Map<String, String> holidays = new HashMap<>();
        for (int month = 1; month <= monthsInYear; month++) {
            appendMonthRow(svg, config, month, holidays, cellWidth, cellHeight, headerHeight, monthLabelWidth);
        }

        svg.append("</svg>");
        return svg.toString();
    }

    /** Append the opening SVG element. */
    private void appendSvgHeader(StringBuilder svg, int svgWidth, int svgHeight) {
        svg.append(String.format(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\""
                        + " viewBox=\"0 0 %d %d\" preserveAspectRatio=\"xMidYMid meet\">%n",
                svgWidth, svgHeight, svgWidth, svgHeight));
    }

    /** Append the embedded CSS style block. */
    private void appendSvgStyles(StringBuilder svg) {
        svg.append("<style>").append(System.lineSeparator());
        svg.append(".year-text { fill: #000; font-family: Helvetica, Arial, sans-serif; font-size:"
                + " 60px; font-weight: bold; }%n");
        svg.append(".year-subtitle { fill: #666; font-family: Helvetica, Arial, sans-serif; font-size:" + " 20px; }%n");
        svg.append(".month-name { fill: #000; font-family: Helvetica, Arial, sans-serif; font-size:"
                + " 16px; font-weight: bold; }%n");
        svg.append(".day-text { fill: #000; font-family: Helvetica, Arial, sans-serif; font-size:" + " 12px; }%n");
        svg.append(".day-header { fill: #666; font-family: Arial, sans-serif; font-size: 10px; }%n");
        svg.append(".grid-line { stroke: #ccc; stroke-width: 0.5; fill: rgba(255, 255, 255, 0); }%n");
        svg.append(".shabbat-bg { fill: #f0f0ff; }").append(System.lineSeparator());
        svg.append(".holiday-text { fill: #fff; font-family: Arial, sans-serif; font-size: 7px;"
                + " text-anchor: middle; }%n");
        svg.append("</style>").append(System.lineSeparator());
    }

    /** Append the Hebrew year title and Gregorian subtitle. */
    private void appendYearHeader(StringBuilder svg, int hebrewYear) {
        svg.append(String.format("<text x=\"50\" y=\"60\" class=\"year-text\">%d</text>%n", hebrewYear));

        int gregorianStart = hebrewYear - 3760;
        int gregorianEnd = gregorianStart + 1;
        svg.append(String.format("<text x=\"50\" y=\"85\" class=\"year-subtitle\">%d-%d CE</text>%n", gregorianStart,
                gregorianEnd));
    }

    /** Append day number column headers (1..30). */
    private void appendDayNumberHeaders(StringBuilder svg, int monthLabelWidth, int cellWidth, int headerHeight) {
        for (int day = 1; day <= 30; day++) {
            int x = monthLabelWidth + (day - 1) * cellWidth + cellWidth / 2;
            int y = headerHeight - 10;
            svg.append(String.format(
                    "<text x=\"%d\" y=\"%d\" class=\"day-header\"" + " text-anchor=\"middle\">%d</text>%n", x, y, day));
        }
    }

    /** Append a single month row (label + 30 day cells). */
    private void appendMonthRow(StringBuilder svg, HebrewCalendarConfig config, int month, Map<String, String> holidays,
            int cellWidth, int cellHeight, int headerHeight, int monthLabelWidth) {
        String monthName = getHebrewMonthName(month, config.hebrewYear);
        int daysInMonth = getDaysInHebrewMonth(month, config.hebrewYear);
        int rowY = (month - 1) * cellHeight + headerHeight;

        appendMonthLabel(svg, monthName, rowY, cellHeight, monthLabelWidth, config.rotateMonthNames);

        for (int day = 1; day <= 30; day++) {
            int cellX = monthLabelWidth + (day - 1) * cellWidth;
            appendDayCell(svg, config, month, day, daysInMonth, holidays, cellX, rowY, cellWidth, cellHeight);
        }
    }

    /** Append the rotated or upright month name label. */
    private void appendMonthLabel(StringBuilder svg, String monthName, int rowY, int cellHeight, int monthLabelWidth,
            boolean rotate) {
        if (rotate) {
            svg.append(String.format(
                    "<text x=\"%d\" y=\"%d\" class=\"month-name\"" + " transform=\"rotate(-90 %d %d)\""
                            + " text-anchor=\"middle\">%s</text>%n",
                    monthLabelWidth / 2, rowY + cellHeight / 2, monthLabelWidth / 2, rowY + cellHeight / 2, monthName));
        } else {
            svg.append(String.format(
                    "<text x=\"%d\" y=\"%d\" class=\"month-name\"" + " text-anchor=\"middle\">%s</text>%n",
                    monthLabelWidth / 2, rowY + cellHeight / 2 + 5, monthName));
        }
    }

    /** Append a single day cell, dispatching to active or empty rendering. */
    private void appendDayCell(StringBuilder svg, HebrewCalendarConfig config, int month, int day, int daysInMonth,
            Map<String, String> holidays, int cellX, int cellY, int cellWidth, int cellHeight) {
        if (day > daysInMonth) {
            appendEmptyDayCell(svg, config, cellX, cellY, cellWidth, cellHeight);
            return;
        }
        appendActiveDayCell(svg, config, month, day, holidays, cellX, cellY, cellWidth, cellHeight);
    }

    /** Append an empty placeholder cell for days that don't exist in this month. */
    private void appendEmptyDayCell(StringBuilder svg, HebrewCalendarConfig config, int cellX, int cellY, int cellWidth,
            int cellHeight) {
        if (config.showGrid) {
            svg.append(String.format("<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\""
                    + " fill=\"#f9f9f9\" stroke=\"#eee\"" + " stroke-width=\"0.5\"/>%n", cellX, cellY, cellWidth,
                    cellHeight));
        }
    }

    /** Append a populated day cell (background, grid, day number, moon, holiday). */
    private void appendActiveDayCell(StringBuilder svg, HebrewCalendarConfig config, int month, int day,
            Map<String, String> holidays, int cellX, int cellY, int cellWidth, int cellHeight) {
        // Check if this is Shabbat (simplified - every 7th day starting from Saturday)
        // In reality, this would need proper Hebrew date to day-of-week conversion
        boolean isShabbat = (day % 7) == 0;
        if (isShabbat && config.highlightWeekends) {
            svg.append(String.format("<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\"" + " class=\"shabbat-bg\"/>%n",
                    cellX, cellY, cellWidth, cellHeight));
        }

        if (config.showGrid) {
            String pdfSafeColor = CalendarRenderingService.convertColorForPDF("rgba(255, 255, 255, 0)");
            svg.append(String.format(
                    "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\"" + " class=\"grid-line\" fill=\"%s\"/>%n",
                    cellX, cellY, cellWidth, cellHeight, pdfSafeColor));
        }

        if (config.showDayNumbers) {
            svg.append(
                    String.format("<text x=\"%d\" y=\"%d\" class=\"day-text\"" + " text-anchor=\"middle\">%d</text>%n",
                            cellX + cellWidth / 2, cellY + 15, day));
        }

        String holidayName = holidays.get(month + "-" + day);
        appendMoonOrHoliday(svg, config, month, day, holidayName, cellX, cellY, cellWidth, cellHeight);
    }

    /** Append moon illumination (with optional holiday text inside) or plain holiday text. */
    private void appendMoonOrHoliday(StringBuilder svg, HebrewCalendarConfig config, int month, int day,
            String holidayName, int cellX, int cellY, int cellWidth, int cellHeight) {
        boolean illuminationMode = "illumination".equals(config.moonDisplayMode);
        if (illuminationMode) {
            // Calculate moon illumination for this day
            // This is simplified - in reality would need Hebrew to Gregorian date conversion
            LocalDate approximateDate = hebrewToGregorian(config.hebrewYear, month, day);
            int moonX = cellX + cellWidth / 2;
            int moonY = cellY + cellHeight / 2 + config.moonOffsetY;

            svg.append(calendarRenderingService.generateMoonIlluminationSVG(approximateDate, moonX, moonY,
                    config.latitude, config.longitude, config));

            if (holidayName != null) {
                drawWrappedTextInMoon(svg, holidayName, moonX, moonY, config.moonSize);
            }
            return;
        }

        if (holidayName != null) {
            String displayName = holidayName.length() > 10 ? holidayName.substring(0, 10) : holidayName;
            svg.append(String.format("<text x=\"%d\" y=\"%d\" class=\"holiday-text\"" + " font-size=\"8\">%s</text>%n",
                    cellX + cellWidth / 2, cellY + cellHeight - 10, displayName));
        }
    }

    /** Draw wrapped text inside moon circle */
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
                    "<text x=\"%d\" y=\"%d\" class=\"holiday-text\"" + " dominant-baseline=\"middle\">%s</text>%n",
                    centerX, startY + i * lineHeight, lines.get(i)));
        }
    }
}
