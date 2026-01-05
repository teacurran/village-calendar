package villagecompute.calendar.services;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import villagecompute.calendar.types.HolidayType;

/**
 * Service for calculating holidays for different countries. Jewish holidays are calculated dynamically using Hebrew
 * calendar algorithms.
 */
@ApplicationScoped
public class HolidayService {

    // ========================================
    // Holiday Set Constants
    // ========================================

    public static final String SET_US = "US";
    public static final String SET_JEWISH = "JEWISH";
    public static final String SET_HEBREW = "HEBREW"; // Alias for JEWISH
    public static final String SET_CHRISTIAN = "CHRISTIAN";
    public static final String SET_ISLAMIC = "ISLAMIC";
    public static final String SET_MUSLIM = "MUSLIM"; // Alias for ISLAMIC
    public static final String SET_BUDDHIST = "BUDDHIST";
    public static final String SET_HINDU = "HINDU";
    public static final String SET_CANADIAN = "CANADIAN";
    public static final String SET_UK = "UK";
    public static final String SET_EUROPEAN = "EUROPEAN";
    public static final String SET_MAJOR_WORLD = "MAJOR_WORLD";
    public static final String SET_MEXICAN = "MEXICAN";
    public static final String SET_PAGAN = "PAGAN";
    public static final String SET_WICCAN = "WICCAN"; // Alias for PAGAN
    public static final String SET_CHINESE = "CHINESE";
    public static final String SET_LUNAR = "LUNAR"; // Alias for CHINESE
    public static final String SET_SECULAR = "SECULAR";
    public static final String SET_FUN = "FUN"; // Alias for SECULAR

    // ========================================
    // Holiday Name Constants
    // ========================================
    private static final String HOLIDAY_NEW_YEARS_DAY = "New Year's Day";
    private static final String HOLIDAY_GOOD_FRIDAY = "Good Friday";
    private static final String HOLIDAY_CHRISTMAS = "Christmas";

    // ========================================
    // Emoji Constants (with ZWJ characters escaped for SonarQube S2479)
    // ========================================
    // Family emoji (U+1F468 U+200D U+1F469 U+200D U+1F467 U+200D U+1F466)
    private static final String EMOJI_FAMILY = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66";
    // Pride rainbow flag (U+1F3F3 U+FE0F U+200D U+1F308)
    private static final String EMOJI_RAINBOW_FLAG = "\uD83C\uDFF3\uFE0F\u200D\uD83C\uDF08";

    /**
     * Map frontend holiday set IDs to canonical backend set names. Handles case-insensitive matching and various
     * aliases.
     *
     * @param setId
     *            The frontend set ID (e.g., "us", "jewish", "ca")
     * @return The canonical backend set name (e.g., "US", "JEWISH", "CANADIAN")
     */
    public String mapHolidaySetId(String setId) {
        if (setId == null)
            return SET_US;
        return switch (setId.toLowerCase()) {
            case "us", "us-federal" -> SET_US;
            case "jewish" -> SET_JEWISH;
            case "christian" -> SET_CHRISTIAN;
            case "muslim", "islamic" -> SET_ISLAMIC;
            case "buddhist" -> SET_BUDDHIST;
            case "hindu", "in" -> SET_HINDU;
            case "canadian", "ca" -> SET_CANADIAN;
            case "uk" -> SET_UK;
            case "european" -> SET_EUROPEAN;
            case "major_world" -> SET_MAJOR_WORLD;
            case "mexican", "mx" -> SET_MEXICAN;
            case "pagan", "wiccan" -> SET_PAGAN;
            case "chinese", "cn", "lunar" -> SET_CHINESE;
            case "secular", "fun" -> SET_SECULAR;
            default -> setId.toUpperCase();
        };
    }

    /**
     * Get holidays with full type information for a year and holiday set.
     *
     * @param year
     *            The calendar year
     * @param setId
     *            The holiday set ID (will be mapped to canonical name)
     * @return Map of LocalDate to HolidayType entries
     */
    public Map<LocalDate, HolidayType> getHolidaysTyped(int year, String setId) {
        Map<String, String> names = getHolidayNames(year, setId);
        Map<String, String> emojis = getHolidaysWithEmoji(year, setId);

        Map<LocalDate, HolidayType> holidays = new HashMap<>();
        for (Map.Entry<String, String> entry : names.entrySet()) {
            String dateStr = entry.getKey();
            LocalDate date = LocalDate.parse(dateStr);
            String name = entry.getValue();
            String emoji = emojis.get(dateStr);
            holidays.put(date, new HolidayType(name, emoji));
        }
        // Add any emoji-only entries (dates with emoji but no name)
        for (Map.Entry<String, String> entry : emojis.entrySet()) {
            LocalDate date = LocalDate.parse(entry.getKey());
            if (!holidays.containsKey(date)) {
                holidays.put(date, new HolidayType(null, entry.getValue()));
            }
        }
        return holidays;
    }

    /**
     * Get holidays with emoji mappings for a year and holiday set.
     *
     * @param year
     *            The calendar year
     * @param setId
     *            The holiday set ID (will be mapped to canonical name)
     * @return Map of date strings to emoji characters
     * @deprecated Use {@link #getHolidaysTyped(int, String)} instead
     */
    @Deprecated
    public Map<String, String> getHolidaysWithEmoji(int year, String setId) {
        String canonicalSet = mapHolidaySetId(setId);
        return switch (canonicalSet) {
            case SET_US -> getUSHolidaysWithEmoji(year);
            case SET_JEWISH, SET_HEBREW -> getJewishHolidaysWithEmoji(year);
            case SET_CHRISTIAN -> getChristianHolidaysWithEmoji(year);
            case SET_CANADIAN -> getCanadianHolidaysWithEmoji(year);
            case SET_UK -> getUKHolidaysWithEmoji(year);
            case SET_MAJOR_WORLD -> getMajorWorldHolidaysWithEmoji(year);
            case SET_MEXICAN -> getMexicanHolidaysWithEmoji(year);
            case SET_PAGAN, SET_WICCAN -> getPaganHolidaysWithEmoji(year);
            case SET_HINDU -> getHinduHolidaysWithEmoji(year);
            case SET_ISLAMIC, SET_MUSLIM -> getIslamicHolidaysWithEmoji(year);
            case SET_CHINESE, SET_LUNAR -> getChineseHolidaysWithEmoji(year);
            case SET_SECULAR, SET_FUN -> getSecularHolidaysWithEmoji(year);
            default -> new HashMap<>();
        };
    }

    /**
     * Get holiday names for a year and holiday set.
     *
     * @param year
     *            The calendar year
     * @param setId
     *            The holiday set ID (will be mapped to canonical name)
     * @return Map of date strings to holiday names
     */
    public Map<String, String> getHolidayNames(int year, String setId) {
        String canonicalSet = mapHolidaySetId(setId);
        return switch (canonicalSet) {
            case SET_US -> getUSHolidays(year);
            case SET_JEWISH, SET_HEBREW -> getJewishHolidays(year);
            case SET_CHRISTIAN -> getChristianHolidays(year);
            case SET_CANADIAN -> getCanadianHolidays(year);
            case SET_UK -> getUKHolidays(year);
            case SET_MAJOR_WORLD -> getMajorWorldHolidays(year);
            case SET_MEXICAN -> getMexicanHolidays(year);
            case SET_PAGAN, SET_WICCAN -> getPaganHolidays(year);
            case SET_HINDU -> getHinduHolidays(year);
            case SET_ISLAMIC, SET_MUSLIM -> getIslamicHolidays(year);
            case SET_CHINESE, SET_LUNAR -> getChineseHolidays(year);
            case SET_SECULAR, SET_FUN -> getSecularHolidays(year);
            default -> new HashMap<>();
        };
    }

    // ========================================
    // US Federal Holidays
    // ========================================

    /** Get US Federal Holidays for a given year */
    public Map<String, String> getUSHolidays(int year) {
        Map<String, String> holidays = new HashMap<>();

        // New Year's Day - January 1
        holidays.put(formatDate(LocalDate.of(year, Month.JANUARY, 1)), HOLIDAY_NEW_YEARS_DAY);

        // Martin Luther King Jr. Day - 3rd Monday in January
        LocalDate mlkDay = LocalDate.of(year, Month.JANUARY, 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.MONDAY));
        holidays.put(formatDate(mlkDay), "Martin Luther King Jr. Day");

        // Presidents' Day - 3rd Monday in February
        LocalDate presidentsDay = LocalDate.of(year, Month.FEBRUARY, 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.MONDAY));
        holidays.put(formatDate(presidentsDay), "Presidents' Day");

        // Memorial Day - Last Monday in May
        LocalDate memorialDay = LocalDate.of(year, Month.MAY, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY));
        holidays.put(formatDate(memorialDay), "Memorial Day");

        // Juneteenth - June 19
        holidays.put(formatDate(LocalDate.of(year, Month.JUNE, 19)), "Juneteenth");

        // Independence Day - July 4
        holidays.put(formatDate(LocalDate.of(year, Month.JULY, 4)), "Independence Day");

        // Labor Day - 1st Monday in September
        LocalDate laborDay = LocalDate.of(year, Month.SEPTEMBER, 1)
                .with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));
        holidays.put(formatDate(laborDay), "Labor Day");

        // Columbus Day - 2nd Monday in October
        LocalDate columbusDay = LocalDate.of(year, Month.OCTOBER, 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.MONDAY));
        holidays.put(formatDate(columbusDay), "Columbus Day");

        // Halloween - October 31
        holidays.put(formatDate(LocalDate.of(year, Month.OCTOBER, 31)), "Halloween");

        // Veterans Day - November 11
        holidays.put(formatDate(LocalDate.of(year, Month.NOVEMBER, 11)), "Veterans Day");

        // Thanksgiving - 4th Thursday in November
        LocalDate thanksgiving = LocalDate.of(year, Month.NOVEMBER, 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY));
        holidays.put(formatDate(thanksgiving), "Thanksgiving");

        // Christmas Day - December 25
        holidays.put(formatDate(LocalDate.of(year, Month.DECEMBER, 25)), "Christmas Day");

        return holidays;
    }

    /** Get holidays for any country (currently only supports US) */
    public Map<String, String> getHolidays(int year, String country) {
        if ("US".equalsIgnoreCase(country)) {
            return getUSHolidays(year);
        }

        // Default to US holidays if country not supported
        return getUSHolidays(year);
    }

    /**
     * Get Jewish holidays for a given Gregorian year. Holidays are calculated dynamically based on the Hebrew calendar.
     */
    public Map<String, String> getJewishHolidays(int gregorianYear) {
        Map<String, String> holidays = new HashMap<>();

        // Jewish holidays span two Hebrew years within one Gregorian year
        // For a Gregorian year, we need holidays from:
        // - The Hebrew year that started in the previous fall (for spring holidays)
        // - The Hebrew year that starts in the current fall (for fall holidays)

        int hebrewYearForSpring = gregorianYear + 3760; // Approximate
        int hebrewYearForFall = gregorianYear + 3761; // Hebrew year starting in fall of gregorianYear

        // Spring holidays (from Hebrew year that started previous fall)
        addPurim(holidays, hebrewYearForSpring);
        addPassover(holidays, hebrewYearForSpring);
        addShavuot(holidays, hebrewYearForSpring);

        // Fall holidays (from Hebrew year starting in current fall)
        addRoshHashanah(holidays, hebrewYearForFall);
        addYomKippur(holidays, hebrewYearForFall);
        addSukkot(holidays, hebrewYearForFall);
        addChanukah(holidays, hebrewYearForFall, gregorianYear);

        return holidays;
    }

    /** Get Jewish holidays with emoji mappings */
    public Map<String, String> getJewishHolidaysWithEmoji(int gregorianYear) {
        Map<String, String> holidayEmojis = new HashMap<>();

        int hebrewYearForSpring = gregorianYear + 3760;
        int hebrewYearForFall = gregorianYear + 3761;

        // Spring holidays
        LocalDate purim = calculatePurim(hebrewYearForSpring);
        if (purim != null && purim.getYear() == gregorianYear) {
            holidayEmojis.put(purim.toString(), "🎭");
        }

        LocalDate passoverStart = calculatePassoverStart(hebrewYearForSpring);
        if (passoverStart != null && passoverStart.getYear() == gregorianYear) {
            holidayEmojis.put(passoverStart.toString(), "🍷");
            holidayEmojis.put(passoverStart.plusDays(7).toString(), "🍷"); // Last day
        }

        LocalDate shavuot = calculateShavuot(hebrewYearForSpring);
        if (shavuot != null && shavuot.getYear() == gregorianYear) {
            holidayEmojis.put(shavuot.toString(), "📜");
        }

        // Fall holidays
        LocalDate roshHashanah = calculateRoshHashanah(hebrewYearForFall);
        if (roshHashanah != null && roshHashanah.getYear() == gregorianYear) {
            holidayEmojis.put(roshHashanah.toString(), "🍎");
            holidayEmojis.put(roshHashanah.plusDays(1).toString(), "🍎");
        }

        LocalDate yomKippur = calculateYomKippur(hebrewYearForFall);
        if (yomKippur != null && yomKippur.getYear() == gregorianYear) {
            holidayEmojis.put(yomKippur.toString(), "✡️");
        }

        LocalDate sukkot = calculateSukkotStart(hebrewYearForFall);
        if (sukkot != null && sukkot.getYear() == gregorianYear) {
            holidayEmojis.put(sukkot.toString(), "🌿");
            holidayEmojis.put(sukkot.plusDays(7).toString(), "📜"); // Simchat Torah (diaspora)
        }

        LocalDate chanukah = calculateChanukahStart(hebrewYearForFall);
        if (chanukah != null && chanukah.getYear() == gregorianYear) {
            holidayEmojis.put(chanukah.toString(), "🕎");
        }

        return holidayEmojis;
    }

    // ========================================
    // Hebrew Calendar Calculation Methods
    // Using Rata Die (fixed day) algorithm
    // ========================================

    /** Check if a Hebrew year is a leap year (has 13 months) */
    private boolean isHebrewLeapYear(int hebrewYear) {
        return ((7 * hebrewYear + 1) % 19) < 7;
    }

    /** Calculate the number of months in a Hebrew year */
    private int monthsInHebrewYear(int hebrewYear) {
        return isHebrewLeapYear(hebrewYear) ? 13 : 12;
    }

    /** Calculate the number of elapsed months before Hebrew year */
    private long hebrewMonthsElapsed(int hebrewYear) {
        long y = hebrewYear - 1L;
        return (235 * y + 1) / 19;
    }

    /**
     * Calculate the Rata Die (fixed day number) for 1 Tishrei of a Hebrew year. This is the number of days since the
     * Rata Die epoch (Dec 31, 1 BCE proleptic Gregorian).
     */
    private long hebrewNewYear(int hebrewYear) {
        long monthsElapsed = hebrewMonthsElapsed(hebrewYear);
        long partsElapsed = 204 + 793 * (monthsElapsed % 1080);
        long hoursElapsed = 11 + 12 * monthsElapsed + 793 * (monthsElapsed / 1080) + partsElapsed / 1080;
        long day = 29 * monthsElapsed + hoursElapsed / 24;

        // Remaining parts
        long parts = (hoursElapsed % 24) * 1080 + partsElapsed % 1080;

        // Apply postponement rules (dehiyot)
        int dayOfWeek = (int) ((day + 1) % 7); // 0=Sunday, 1=Monday, etc.

        // ADU rule: Rosh Hashanah cannot fall on Sunday (0), Wednesday (3), or Friday (5)
        if (dayOfWeek == 0 || dayOfWeek == 3 || dayOfWeek == 5) {
            day++;
        }
        // Molad Zakein: If molad is at or after noon, postpone
        else if (parts >= 19440) {
            day++;
            dayOfWeek = (int) ((day + 1) % 7);
            if (dayOfWeek == 0 || dayOfWeek == 3 || dayOfWeek == 5) {
                day++;
            }
        }

        // Hebrew epoch adjustment: 1 Tishrei 1 = -1373427 in Rata Die
        return day - 1373427;
    }

    /** Calculate the number of days in a Hebrew year */
    private int daysInHebrewYear(int hebrewYear) {
        return (int) (hebrewNewYear(hebrewYear + 1) - hebrewNewYear(hebrewYear));
    }

    /** Determine if Cheshvan is long (30 days) in a given year */
    private boolean isLongCheshvan(int hebrewYear) {
        int days = daysInHebrewYear(hebrewYear);
        return days == 355 || days == 385;
    }

    /** Determine if Kislev is short (29 days) in a given year */
    private boolean isShortKislev(int hebrewYear) {
        int days = daysInHebrewYear(hebrewYear);
        return days == 353 || days == 383;
    }

    /**
     * Calculate the number of days in a Hebrew month. Hebrew months: 1=Nisan, 2=Iyar, 3=Sivan, 4=Tammuz, 5=Av, 6=Elul,
     * 7=Tishrei, 8=Cheshvan, 9=Kislev, 10=Tevet, 11=Shevat, 12=Adar 13=Adar II (leap years only)
     */
    private int daysInHebrewMonth(int hebrewYear, int hebrewMonth) {
        switch (hebrewMonth) {
            case 1 : // Nisan
            case 3 : // Sivan
            case 5 : // Av
            case 7 : // Tishrei
            case 11 : // Shevat
                return 30;
            case 2 : // Iyar
            case 4 : // Tammuz
            case 6 : // Elul
            case 10 : // Tevet
            case 13 : // Adar II
                return 29;
            case 8 : // Cheshvan - variable
                return isLongCheshvan(hebrewYear) ? 30 : 29;
            case 9 : // Kislev - variable
                return isShortKislev(hebrewYear) ? 29 : 30;
            case 12 : // Adar (or Adar I in leap year)
                return isHebrewLeapYear(hebrewYear) ? 30 : 29;
            default :
                return 30;
        }
    }

    /** Convert Hebrew date to Rata Die (fixed day number) */
    private long hebrewToRataDie(int hebrewYear, int hebrewMonth, int hebrewDay) {
        long day = hebrewNewYear(hebrewYear) + hebrewDay - 1;

        // Add days from Tishrei to given month
        if (hebrewMonth < 7) {
            // Months after Tishrei to end of year, then from Nisan to given month
            for (int m = 7; m <= monthsInHebrewYear(hebrewYear); m++) {
                day += daysInHebrewMonth(hebrewYear, m);
            }
            for (int m = 1; m < hebrewMonth; m++) {
                day += daysInHebrewMonth(hebrewYear, m);
            }
        } else {
            // Months from Tishrei to given month
            for (int m = 7; m < hebrewMonth; m++) {
                day += daysInHebrewMonth(hebrewYear, m);
            }
        }

        return day;
    }

    /** Convert Rata Die to Gregorian date */
    private LocalDate rataDieToGregorian(long rataDie) {
        // Rata Die 1 = January 1, 1 CE (proleptic Gregorian)
        return LocalDate.ofEpochDay(rataDie - 719163); // 719163 = days from RD epoch to Unix epoch
    }

    /** Convert Hebrew date to Gregorian date */
    private LocalDate hebrewToGregorian(int hebrewYear, int hebrewMonth, int hebrewDay) {
        long rataDie = hebrewToRataDie(hebrewYear, hebrewMonth, hebrewDay);
        return rataDieToGregorian(rataDie);
    }

    // ========================================
    // Holiday Calculation Methods
    // ========================================

    private LocalDate calculateRoshHashanah(int hebrewYear) {
        return hebrewToGregorian(hebrewYear, 7, 1); // 1 Tishrei
    }

    private LocalDate calculateYomKippur(int hebrewYear) {
        return hebrewToGregorian(hebrewYear, 7, 10); // 10 Tishrei
    }

    private LocalDate calculateSukkotStart(int hebrewYear) {
        return hebrewToGregorian(hebrewYear, 7, 15); // 15 Tishrei
    }

    private LocalDate calculateChanukahStart(int hebrewYear) {
        return hebrewToGregorian(hebrewYear, 9, 25); // 25 Kislev
    }

    private LocalDate calculatePurim(int hebrewYear) {
        // Purim is 14 Adar (or 14 Adar II in leap years)
        int adarMonth = isHebrewLeapYear(hebrewYear) ? 13 : 12;
        return hebrewToGregorian(hebrewYear, adarMonth, 14);
    }

    private LocalDate calculatePassoverStart(int hebrewYear) {
        return hebrewToGregorian(hebrewYear, 1, 15); // 15 Nisan
    }

    private LocalDate calculateShavuot(int hebrewYear) {
        return hebrewToGregorian(hebrewYear, 3, 6); // 6 Sivan
    }

    private void addRoshHashanah(Map<String, String> holidays, int hebrewYear) {
        LocalDate date = calculateRoshHashanah(hebrewYear);
        if (date != null) {
            holidays.put(formatDate(date), "Rosh Hashanah");
            holidays.put(formatDate(date.plusDays(1)), "Rosh Hashanah");
        }
    }

    private void addYomKippur(Map<String, String> holidays, int hebrewYear) {
        LocalDate date = calculateYomKippur(hebrewYear);
        if (date != null) {
            holidays.put(formatDate(date), "Yom Kippur");
        }
    }

    private void addSukkot(Map<String, String> holidays, int hebrewYear) {
        LocalDate date = calculateSukkotStart(hebrewYear);
        if (date != null) {
            holidays.put(formatDate(date), "Sukkot");
            holidays.put(formatDate(date.plusDays(7)), "Shemini Atzeret");
            holidays.put(formatDate(date.plusDays(8)), "Simchat Torah");
        }
    }

    private void addChanukah(Map<String, String> holidays, int hebrewYear, int gregorianYear) {
        LocalDate date = calculateChanukahStart(hebrewYear);
        // Chanukah might fall in December of the current year or next year
        if (date != null && date.getYear() == gregorianYear) {
            holidays.put(formatDate(date), "Chanukah");
        }
    }

    private void addPurim(Map<String, String> holidays, int hebrewYear) {
        LocalDate date = calculatePurim(hebrewYear);
        if (date != null) {
            holidays.put(formatDate(date), "Purim");
        }
    }

    private void addPassover(Map<String, String> holidays, int hebrewYear) {
        LocalDate date = calculatePassoverStart(hebrewYear);
        if (date != null) {
            holidays.put(formatDate(date), "Passover");
            holidays.put(formatDate(date.plusDays(7)), "Passover");
        }
    }

    private void addShavuot(Map<String, String> holidays, int hebrewYear) {
        LocalDate date = calculateShavuot(hebrewYear);
        if (date != null) {
            holidays.put(formatDate(date), "Shavuot");
        }
    }

    /** Format date as YYYY-MM-DD */
    private String formatDate(LocalDate date) {
        return date.toString();
    }

    // ========================================
    // Mexican/Hispanic Holidays
    // ========================================

    /** Get Mexican holidays for a given year */
    public Map<String, String> getMexicanHolidays(int year) {
        Map<String, String> holidays = new HashMap<>();

        // Día de los Reyes (Epiphany/Three Kings Day) - January 6
        holidays.put(formatDate(LocalDate.of(year, Month.JANUARY, 6)), "Día de los Reyes");

        // Constitution Day - February 5 (observed first Monday of February)
        holidays.put(formatDate(LocalDate.of(year, Month.FEBRUARY, 5)), "Día de la Constitución");

        // Benito Juárez Birthday - March 21 (observed third Monday of March)
        holidays.put(formatDate(LocalDate.of(year, Month.MARCH, 21)), "Natalicio de Benito Juárez");

        // Cinco de Mayo - May 5
        holidays.put(formatDate(LocalDate.of(year, Month.MAY, 5)), "Cinco de Mayo");

        // Mexican Independence Day - September 16
        holidays.put(formatDate(LocalDate.of(year, Month.SEPTEMBER, 16)), "Día de la Independencia");

        // Día de los Muertos - November 1-2
        holidays.put(formatDate(LocalDate.of(year, Month.NOVEMBER, 1)), "Día de los Muertos");
        holidays.put(formatDate(LocalDate.of(year, Month.NOVEMBER, 2)), "Día de los Muertos");

        // Revolution Day - November 20
        holidays.put(formatDate(LocalDate.of(year, Month.NOVEMBER, 20)), "Día de la Revolución");

        // Día de la Virgen de Guadalupe - December 12
        holidays.put(formatDate(LocalDate.of(year, Month.DECEMBER, 12)), "Virgen de Guadalupe");

        // Las Posadas - December 16-24
        for (int day = 16; day <= 24; day++) {
            holidays.put(formatDate(LocalDate.of(year, Month.DECEMBER, day)), "Las Posadas");
        }

        return holidays;
    }

    /** Get Mexican holidays with emoji mappings */
    public Map<String, String> getMexicanHolidaysWithEmoji(int year) {
        Map<String, String> holidayEmojis = new HashMap<>();

        holidayEmojis.put(LocalDate.of(year, Month.JANUARY, 6).toString(), "👑"); // Día de los Reyes
        holidayEmojis.put(LocalDate.of(year, Month.FEBRUARY, 5).toString(), "📜"); // Constitution Day
        holidayEmojis.put(LocalDate.of(year, Month.MARCH, 21).toString(), "⚖️"); // Benito Juárez
        holidayEmojis.put(LocalDate.of(year, Month.MAY, 5).toString(), "🇲🇽"); // Cinco de Mayo
        holidayEmojis.put(LocalDate.of(year, Month.SEPTEMBER, 16).toString(), "🎉"); // Independence Day
        holidayEmojis.put(LocalDate.of(year, Month.NOVEMBER, 1).toString(), "💀"); // Día de los Muertos
        holidayEmojis.put(LocalDate.of(year, Month.NOVEMBER, 2).toString(), "💀"); // Día de los Muertos
        holidayEmojis.put(LocalDate.of(year, Month.NOVEMBER, 20).toString(), "🎖️"); // Revolution Day
        holidayEmojis.put(LocalDate.of(year, Month.DECEMBER, 12).toString(), "🌹"); // Virgen de Guadalupe
        holidayEmojis.put(LocalDate.of(year, Month.DECEMBER, 16).toString(), "🕯️"); // Las Posadas start

        return holidayEmojis;
    }

    // ========================================
    // Pagan/Wiccan Holidays (Wheel of the Year)
    // ========================================

    /** Get Pagan/Wiccan holidays for a given year */
    public Map<String, String> getPaganHolidays(int year) {
        Map<String, String> holidays = new HashMap<>();

        // Imbolc - February 1-2
        holidays.put(formatDate(LocalDate.of(year, Month.FEBRUARY, 1)), "Imbolc");
        holidays.put(formatDate(LocalDate.of(year, Month.FEBRUARY, 2)), "Imbolc");

        // Ostara - Spring Equinox (calculated)
        LocalDate springEquinox = calculateSpringEquinox(year);
        holidays.put(formatDate(springEquinox), "Ostara");

        // Beltane - May 1
        holidays.put(formatDate(LocalDate.of(year, Month.MAY, 1)), "Beltane");

        // Litha - Summer Solstice (calculated)
        LocalDate summerSolstice = calculateSummerSolstice(year);
        holidays.put(formatDate(summerSolstice), "Litha");

        // Lughnasadh/Lammas - August 1
        holidays.put(formatDate(LocalDate.of(year, Month.AUGUST, 1)), "Lughnasadh");

        // Mabon - Autumn Equinox (calculated)
        LocalDate autumnEquinox = calculateAutumnEquinox(year);
        holidays.put(formatDate(autumnEquinox), "Mabon");

        // Samhain - October 31 - November 1
        holidays.put(formatDate(LocalDate.of(year, Month.OCTOBER, 31)), "Samhain");
        holidays.put(formatDate(LocalDate.of(year, Month.NOVEMBER, 1)), "Samhain");

        // Yule - Winter Solstice (calculated)
        LocalDate winterSolstice = calculateWinterSolstice(year);
        holidays.put(formatDate(winterSolstice), "Yule");

        return holidays;
    }

    /** Get Pagan holidays with emoji mappings */
    public Map<String, String> getPaganHolidaysWithEmoji(int year) {
        Map<String, String> holidayEmojis = new HashMap<>();

        holidayEmojis.put(LocalDate.of(year, Month.FEBRUARY, 1).toString(), "🕯️"); // Imbolc
        holidayEmojis.put(calculateSpringEquinox(year).toString(), "🐣"); // Ostara
        holidayEmojis.put(LocalDate.of(year, Month.MAY, 1).toString(), "🔥"); // Beltane
        holidayEmojis.put(calculateSummerSolstice(year).toString(), "☀️"); // Litha
        holidayEmojis.put(LocalDate.of(year, Month.AUGUST, 1).toString(), "🌾"); // Lughnasadh
        holidayEmojis.put(calculateAutumnEquinox(year).toString(), "🍂"); // Mabon
        holidayEmojis.put(LocalDate.of(year, Month.OCTOBER, 31).toString(), "🎃"); // Samhain
        holidayEmojis.put(calculateWinterSolstice(year).toString(), "🌲"); // Yule

        return holidayEmojis;
    }

    // ========================================
    // Astronomical Calculations for Solstices/Equinoxes
    // Using simplified algorithm accurate to within ~1 day
    // ========================================

    /** Calculate spring equinox date (March 19-21) */
    private LocalDate calculateSpringEquinox(int year) {
        // Jean Meeus algorithm approximation
        double jde = calculateEquinoxSolstice(year, 0); // 0 = spring equinox
        return julianDayToLocalDate(jde);
    }

    /** Calculate summer solstice date (June 20-22) */
    private LocalDate calculateSummerSolstice(int year) {
        double jde = calculateEquinoxSolstice(year, 1); // 1 = summer solstice
        return julianDayToLocalDate(jde);
    }

    /** Calculate autumn equinox date (September 22-24) */
    private LocalDate calculateAutumnEquinox(int year) {
        double jde = calculateEquinoxSolstice(year, 2); // 2 = autumn equinox
        return julianDayToLocalDate(jde);
    }

    /** Calculate winter solstice date (December 20-23) */
    private LocalDate calculateWinterSolstice(int year) {
        double jde = calculateEquinoxSolstice(year, 3); // 3 = winter solstice
        return julianDayToLocalDate(jde);
    }

    /**
     * Calculate equinox or solstice using Jean Meeus algorithm type: 0=spring equinox, 1=summer solstice, 2=autumn
     * equinox, 3=winter solstice
     */
    private double calculateEquinoxSolstice(int year, int type) {
        double y = (year - 2000) / 1000.0;
        double jde0;

        switch (type) {
            case 0 : // Spring Equinox (March)
                jde0 = 2451623.80984 + 365242.37404 * y + 0.05169 * y * y - 0.00411 * y * y * y
                        - 0.00057 * y * y * y * y;
                break;
            case 1 : // Summer Solstice (June)
                jde0 = 2451716.56767 + 365241.62603 * y + 0.00325 * y * y + 0.00888 * y * y * y
                        - 0.00030 * y * y * y * y;
                break;
            case 2 : // Autumn Equinox (September)
                jde0 = 2451810.21715 + 365242.01767 * y - 0.11575 * y * y + 0.00337 * y * y * y
                        + 0.00078 * y * y * y * y;
                break;
            case 3 : // Winter Solstice (December)
            default :
                jde0 = 2451900.05952 + 365242.74049 * y - 0.06223 * y * y - 0.00823 * y * y * y
                        + 0.00032 * y * y * y * y;
                break;
        }

        return jde0;
    }

    /** Convert Julian Day Number to LocalDate */
    private LocalDate julianDayToLocalDate(double jd) {
        // Convert JD to Unix timestamp then to LocalDate
        long unixDays = (long) (jd - 2440587.5);
        return LocalDate.ofEpochDay(unixDays);
    }

    // ========================================
    // Hindu Holidays (Major festivals)
    // Uses approximations based on lunar calendar
    // ========================================

    /** Get Hindu holidays for a given year */
    public Map<String, String> getHinduHolidays(int year) {
        Map<String, String> holidays = new HashMap<>();

        // Calculate Hindu holidays based on lunar calendar approximations
        LocalDate makarSankranti = LocalDate.of(year, Month.JANUARY, 14);
        holidays.put(formatDate(makarSankranti), "Makar Sankranti");

        // Holi - Full moon in Phalguna (Feb-Mar)
        LocalDate holi = calculateHoli(year);
        if (holi != null) {
            holidays.put(formatDate(holi), "Holi");
        }

        // Ram Navami - 9th day of Chaitra (Mar-Apr)
        LocalDate ramNavami = calculateRamNavami(year);
        if (ramNavami != null) {
            holidays.put(formatDate(ramNavami), "Ram Navami");
        }

        // Janmashtami - 8th day of Bhadrapada (Aug-Sep)
        LocalDate janmashtami = calculateJanmashtami(year);
        if (janmashtami != null) {
            holidays.put(formatDate(janmashtami), "Janmashtami");
        }

        // Ganesh Chaturthi - 4th day of Bhadrapada (Aug-Sep)
        LocalDate ganeshChaturthi = calculateGaneshChaturthi(year);
        if (ganeshChaturthi != null) {
            holidays.put(formatDate(ganeshChaturthi), "Ganesh Chaturthi");
        }

        // Navaratri - 9 nights starting 1st day of Ashvin (Sep-Oct)
        LocalDate navaratri = calculateNavaratri(year);
        if (navaratri != null) {
            holidays.put(formatDate(navaratri), "Navaratri");
        }

        // Dussehra/Vijayadashami - 10th day of Ashvin
        LocalDate dussehra = calculateDussehra(year);
        if (dussehra != null) {
            holidays.put(formatDate(dussehra), "Dussehra");
        }

        // Diwali - New moon in Kartik (Oct-Nov)
        LocalDate diwali = calculateDiwali(year);
        if (diwali != null) {
            holidays.put(formatDate(diwali), "Diwali");
        }

        return holidays;
    }

    /** Get Hindu holidays with emoji mappings */
    public Map<String, String> getHinduHolidaysWithEmoji(int year) {
        Map<String, String> holidayEmojis = new HashMap<>();

        holidayEmojis.put(LocalDate.of(year, Month.JANUARY, 14).toString(), "🪁"); // Makar Sankranti

        LocalDate holi = calculateHoli(year);
        if (holi != null) {
            holidayEmojis.put(holi.toString(), "🎨"); // Holi
        }

        LocalDate ramNavami = calculateRamNavami(year);
        if (ramNavami != null) {
            holidayEmojis.put(ramNavami.toString(), "🏹"); // Ram Navami
        }

        LocalDate janmashtami = calculateJanmashtami(year);
        if (janmashtami != null) {
            holidayEmojis.put(janmashtami.toString(), "🪈"); // Janmashtami
        }

        LocalDate ganeshChaturthi = calculateGaneshChaturthi(year);
        if (ganeshChaturthi != null) {
            holidayEmojis.put(ganeshChaturthi.toString(), "🐘"); // Ganesh Chaturthi
        }

        LocalDate navaratri = calculateNavaratri(year);
        if (navaratri != null) {
            holidayEmojis.put(navaratri.toString(), "💃"); // Navaratri
        }

        LocalDate dussehra = calculateDussehra(year);
        if (dussehra != null) {
            holidayEmojis.put(dussehra.toString(), "🏹"); // Dussehra
        }

        LocalDate diwali = calculateDiwali(year);
        if (diwali != null) {
            holidayEmojis.put(diwali.toString(), "🪔"); // Diwali
        }

        return holidayEmojis;
    }

    // Hindu holiday calculations (approximations based on lunar phases)
    private LocalDate calculateHoli(int year) {
        // Holi falls on the full moon in Phalguna (Feb-Mar)
        // Approximation: search for full moon between Feb 15 and Mar 31
        return findFullMoonInRange(year, Month.FEBRUARY, 15, Month.MARCH, 31);
    }

    private LocalDate calculateRamNavami(int year) {
        // Ram Navami is the 9th day of Chaitra (Mar-Apr)
        // Approximation: ~8 days after the new moon following spring equinox
        LocalDate springEquinox = calculateSpringEquinox(year);
        LocalDate newMoon = findNewMoonAfter(springEquinox);
        return newMoon != null ? newMoon.plusDays(8) : null;
    }

    private LocalDate calculateJanmashtami(int year) {
        // 8th day of Krishna Paksha in Bhadrapada (Aug-Sep)
        // Approximation: 8 days after full moon in Aug-Sep
        LocalDate fullMoon = findFullMoonInRange(year, Month.AUGUST, 1, Month.SEPTEMBER, 15);
        return fullMoon != null ? fullMoon.plusDays(8) : null;
    }

    private LocalDate calculateGaneshChaturthi(int year) {
        // 4th day of Shukla Paksha in Bhadrapada (Aug-Sep)
        // Approximation: ~4 days after new moon in late Aug/early Sep
        LocalDate newMoon = findNewMoonInRange(year, Month.AUGUST, 15, Month.SEPTEMBER, 15);
        return newMoon != null ? newMoon.plusDays(4) : null;
    }

    private LocalDate calculateNavaratri(int year) {
        // Starts on 1st day of Ashvin (Sep-Oct)
        // Approximation: day after new moon in late Sep/early Oct
        LocalDate newMoon = findNewMoonInRange(year, Month.SEPTEMBER, 15, Month.OCTOBER, 15);
        return newMoon != null ? newMoon.plusDays(1) : null;
    }

    private LocalDate calculateDussehra(int year) {
        // 10th day of Ashvin
        LocalDate navaratri = calculateNavaratri(year);
        return navaratri != null ? navaratri.plusDays(9) : null;
    }

    private LocalDate calculateDiwali(int year) {
        // New moon in Kartik (Oct-Nov)
        return findNewMoonInRange(year, Month.OCTOBER, 15, Month.NOVEMBER, 15);
    }

    // ========================================
    // Islamic Holidays
    // Uses Hijri calendar calculations
    // ========================================

    /** Get Islamic holidays for a given Gregorian year */
    public Map<String, String> getIslamicHolidays(int year) {
        Map<String, String> holidays = new HashMap<>();

        // Islamic calendar is purely lunar, ~11 days shorter than Gregorian
        // Calculate major holidays for both Islamic years that fall in this Gregorian year
        int[] hijriYears = getHijriYearsInGregorianYear(year);

        for (int hijriYear : hijriYears) {
            // Islamic New Year - 1 Muharram
            LocalDate islamicNewYear = hijriToGregorian(hijriYear, 1, 1);
            if (islamicNewYear != null && islamicNewYear.getYear() == year) {
                holidays.put(formatDate(islamicNewYear), "Islamic New Year");
            }

            // Ashura - 10 Muharram
            LocalDate ashura = hijriToGregorian(hijriYear, 1, 10);
            if (ashura != null && ashura.getYear() == year) {
                holidays.put(formatDate(ashura), "Ashura");
            }

            // Mawlid al-Nabi (Prophet's Birthday) - 12 Rabi' al-Awwal
            LocalDate mawlid = hijriToGregorian(hijriYear, 3, 12);
            if (mawlid != null && mawlid.getYear() == year) {
                holidays.put(formatDate(mawlid), "Mawlid al-Nabi");
            }

            // Ramadan Start - 1 Ramadan
            LocalDate ramadanStart = hijriToGregorian(hijriYear, 9, 1);
            if (ramadanStart != null && ramadanStart.getYear() == year) {
                holidays.put(formatDate(ramadanStart), "Ramadan Begins");
            }

            // Laylat al-Qadr - 27 Ramadan (Night of Power)
            LocalDate laylatAlQadr = hijriToGregorian(hijriYear, 9, 27);
            if (laylatAlQadr != null && laylatAlQadr.getYear() == year) {
                holidays.put(formatDate(laylatAlQadr), "Laylat al-Qadr");
            }

            // Eid al-Fitr - 1 Shawwal
            LocalDate eidFitr = hijriToGregorian(hijriYear, 10, 1);
            if (eidFitr != null && eidFitr.getYear() == year) {
                holidays.put(formatDate(eidFitr), "Eid al-Fitr");
            }

            // Eid al-Adha - 10 Dhul Hijjah
            LocalDate eidAdha = hijriToGregorian(hijriYear, 12, 10);
            if (eidAdha != null && eidAdha.getYear() == year) {
                holidays.put(formatDate(eidAdha), "Eid al-Adha");
            }
        }

        return holidays;
    }

    /** Get Islamic holidays with emoji mappings */
    public Map<String, String> getIslamicHolidaysWithEmoji(int year) {
        Map<String, String> holidayEmojis = new HashMap<>();

        int[] hijriYears = getHijriYearsInGregorianYear(year);

        for (int hijriYear : hijriYears) {
            LocalDate islamicNewYear = hijriToGregorian(hijriYear, 1, 1);
            if (islamicNewYear != null && islamicNewYear.getYear() == year) {
                holidayEmojis.put(islamicNewYear.toString(), "🌙"); // Islamic New Year
            }

            LocalDate ashura = hijriToGregorian(hijriYear, 1, 10);
            if (ashura != null && ashura.getYear() == year) {
                holidayEmojis.put(ashura.toString(), "🤲"); // Ashura
            }

            LocalDate mawlid = hijriToGregorian(hijriYear, 3, 12);
            if (mawlid != null && mawlid.getYear() == year) {
                holidayEmojis.put(mawlid.toString(), "☪️"); // Mawlid
            }

            LocalDate ramadanStart = hijriToGregorian(hijriYear, 9, 1);
            if (ramadanStart != null && ramadanStart.getYear() == year) {
                holidayEmojis.put(ramadanStart.toString(), "🌙"); // Ramadan
            }

            LocalDate laylatAlQadr = hijriToGregorian(hijriYear, 9, 27);
            if (laylatAlQadr != null && laylatAlQadr.getYear() == year) {
                holidayEmojis.put(laylatAlQadr.toString(), "✨"); // Laylat al-Qadr
            }

            LocalDate eidFitr = hijriToGregorian(hijriYear, 10, 1);
            if (eidFitr != null && eidFitr.getYear() == year) {
                holidayEmojis.put(eidFitr.toString(), "🎉"); // Eid al-Fitr
            }

            LocalDate eidAdha = hijriToGregorian(hijriYear, 12, 10);
            if (eidAdha != null && eidAdha.getYear() == year) {
                holidayEmojis.put(eidAdha.toString(), "🐑"); // Eid al-Adha
            }
        }

        return holidayEmojis;
    }

    // Hijri calendar calculations
    private int[] getHijriYearsInGregorianYear(int gregorianYear) {
        // Approximate Hijri year calculation
        // Hijri year 1 started July 16, 622 CE
        int approxHijriYear = (int) ((gregorianYear - 622) * 33.0 / 32.0) + 1;
        return new int[]{approxHijriYear, approxHijriYear + 1};
    }

    /** Convert Hijri date to Gregorian Using simplified Kuwaiti algorithm */
    private LocalDate hijriToGregorian(int hijriYear, int hijriMonth, int hijriDay) {
        // Calculate Julian Day Number using the Kuwaiti algorithm
        int jd = (int) (((11 * hijriYear + 3) / 30.0) + 354 * hijriYear + 30 * hijriMonth - (hijriMonth - 1) / 2.0
                + hijriDay + 1948440 - 385);

        // Convert Julian Day to Gregorian
        return julianDayToLocalDate(jd);
    }

    // ========================================
    // Chinese/Lunar New Year Holidays
    // ========================================

    /** Get Chinese holidays for a given year */
    public Map<String, String> getChineseHolidays(int year) {
        Map<String, String> holidays = new HashMap<>();

        // Chinese New Year - 2nd new moon after winter solstice
        LocalDate chineseNewYear = calculateChineseNewYear(year);
        if (chineseNewYear != null) {
            holidays.put(formatDate(chineseNewYear), "Chinese New Year");
            holidays.put(formatDate(chineseNewYear.plusDays(14)), "Lantern Festival");
        }

        // Qingming Festival - April 4 or 5 (15 days after spring equinox)
        LocalDate qingming = calculateSpringEquinox(year).plusDays(15);
        holidays.put(formatDate(qingming), "Qingming Festival");

        // Dragon Boat Festival - 5th day of 5th lunar month
        LocalDate dragonBoat = calculateDragonBoatFestival(year);
        if (dragonBoat != null) {
            holidays.put(formatDate(dragonBoat), "Dragon Boat Festival");
        }

        // Mid-Autumn Festival - 15th day of 8th lunar month
        LocalDate midAutumn = calculateMidAutumnFestival(year);
        if (midAutumn != null) {
            holidays.put(formatDate(midAutumn), "Mid-Autumn Festival");
        }

        // Double Ninth Festival - 9th day of 9th lunar month
        LocalDate doubleNinth = calculateDoubleNinthFestival(year);
        if (doubleNinth != null) {
            holidays.put(formatDate(doubleNinth), "Double Ninth Festival");
        }

        return holidays;
    }

    /** Get Chinese holidays with emoji mappings */
    public Map<String, String> getChineseHolidaysWithEmoji(int year) {
        Map<String, String> holidayEmojis = new HashMap<>();

        LocalDate chineseNewYear = calculateChineseNewYear(year);
        if (chineseNewYear != null) {
            holidayEmojis.put(chineseNewYear.toString(), "🧧"); // Chinese New Year
            holidayEmojis.put(chineseNewYear.plusDays(14).toString(), "🏮"); // Lantern Festival
        }

        LocalDate qingming = calculateSpringEquinox(year).plusDays(15);
        holidayEmojis.put(qingming.toString(), "🪦"); // Qingming

        LocalDate dragonBoat = calculateDragonBoatFestival(year);
        if (dragonBoat != null) {
            holidayEmojis.put(dragonBoat.toString(), "🐉"); // Dragon Boat
        }

        LocalDate midAutumn = calculateMidAutumnFestival(year);
        if (midAutumn != null) {
            holidayEmojis.put(midAutumn.toString(), "🥮"); // Mid-Autumn
        }

        LocalDate doubleNinth = calculateDoubleNinthFestival(year);
        if (doubleNinth != null) {
            holidayEmojis.put(doubleNinth.toString(), "🏔️"); // Double Ninth
        }

        return holidayEmojis;
    }

    private LocalDate calculateChineseNewYear(int year) {
        // Chinese New Year falls on the 2nd new moon after winter solstice
        LocalDate winterSolstice = calculateWinterSolstice(year - 1);
        LocalDate firstNewMoon = findNewMoonAfter(winterSolstice);
        if (firstNewMoon == null)
            return null;
        return findNewMoonAfter(firstNewMoon.plusDays(1));
    }

    private LocalDate calculateDragonBoatFestival(int year) {
        // 5th day of 5th lunar month
        // Approximation: ~4-5 months after Chinese New Year
        LocalDate cny = calculateChineseNewYear(year);
        if (cny == null)
            return null;
        // Find 5th new moon after Chinese New Year, then add 4 days
        LocalDate newMoon = cny;
        for (int i = 0; i < 4; i++) {
            newMoon = findNewMoonAfter(newMoon.plusDays(1));
            if (newMoon == null)
                return null;
        }
        return newMoon.plusDays(4);
    }

    private LocalDate calculateMidAutumnFestival(int year) {
        // 15th day of 8th lunar month (full moon)
        // Approximation: full moon in September/October
        return findFullMoonInRange(year, Month.SEPTEMBER, 1, Month.OCTOBER, 15);
    }

    private LocalDate calculateDoubleNinthFestival(int year) {
        // 9th day of 9th lunar month
        // Approximation: ~8 months after Chinese New Year
        LocalDate cny = calculateChineseNewYear(year);
        if (cny == null)
            return null;
        LocalDate newMoon = cny;
        for (int i = 0; i < 8; i++) {
            newMoon = findNewMoonAfter(newMoon.plusDays(1));
            if (newMoon == null)
                return null;
        }
        return newMoon.plusDays(8);
    }

    // ========================================
    // Fun/Secular American Holidays
    // ========================================

    /** Get fun secular American holidays for a given year */
    public Map<String, String> getSecularHolidays(int year) {
        Map<String, String> holidays = new HashMap<>();

        // Groundhog Day - February 2
        holidays.put(formatDate(LocalDate.of(year, Month.FEBRUARY, 2)), "Groundhog Day");

        // Valentine's Day - February 14
        holidays.put(formatDate(LocalDate.of(year, Month.FEBRUARY, 14)), "Valentine's Day");

        // St. Patrick's Day - March 17
        holidays.put(formatDate(LocalDate.of(year, Month.MARCH, 17)), "St. Patrick's Day");

        // April Fools' Day - April 1
        holidays.put(formatDate(LocalDate.of(year, Month.APRIL, 1)), "April Fools' Day");

        // Earth Day - April 22
        holidays.put(formatDate(LocalDate.of(year, Month.APRIL, 22)), "Earth Day");

        // Cinco de Mayo - May 5 (popular in US)
        holidays.put(formatDate(LocalDate.of(year, Month.MAY, 5)), "Cinco de Mayo");

        // Mother's Day - 2nd Sunday in May
        LocalDate mothersDay = LocalDate.of(year, Month.MAY, 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.SUNDAY));
        holidays.put(formatDate(mothersDay), "Mother's Day");

        // Father's Day - 3rd Sunday in June
        LocalDate fathersDay = LocalDate.of(year, Month.JUNE, 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.SUNDAY));
        holidays.put(formatDate(fathersDay), "Father's Day");

        // Pride Month start - June 1
        holidays.put(formatDate(LocalDate.of(year, Month.JUNE, 1)), "Pride Month");

        // Halloween - October 31
        holidays.put(formatDate(LocalDate.of(year, Month.OCTOBER, 31)), "Halloween");

        // New Year's Eve - December 31
        holidays.put(formatDate(LocalDate.of(year, Month.DECEMBER, 31)), "New Year's Eve");

        return holidays;
    }

    /** Get secular holidays with emoji mappings */
    public Map<String, String> getSecularHolidaysWithEmoji(int year) {
        Map<String, String> holidayEmojis = new HashMap<>();

        holidayEmojis.put(LocalDate.of(year, Month.FEBRUARY, 2).toString(), "🦫"); // Groundhog Day
        holidayEmojis.put(LocalDate.of(year, Month.FEBRUARY, 14).toString(), "❤️"); // Valentine's Day
        holidayEmojis.put(LocalDate.of(year, Month.MARCH, 17).toString(), "☘️"); // St. Patrick's Day
        holidayEmojis.put(LocalDate.of(year, Month.APRIL, 1).toString(), "🃏"); // April Fools
        holidayEmojis.put(LocalDate.of(year, Month.APRIL, 22).toString(), "🌍"); // Earth Day
        holidayEmojis.put(LocalDate.of(year, Month.MAY, 5).toString(), "🌮"); // Cinco de Mayo

        LocalDate mothersDay = LocalDate.of(year, Month.MAY, 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.SUNDAY));
        holidayEmojis.put(mothersDay.toString(), "💐"); // Mother's Day

        LocalDate fathersDay = LocalDate.of(year, Month.JUNE, 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.SUNDAY));
        holidayEmojis.put(fathersDay.toString(), "👔"); // Father's Day

        holidayEmojis.put(LocalDate.of(year, Month.JUNE, 1).toString(), EMOJI_RAINBOW_FLAG); // Pride Month
        holidayEmojis.put(LocalDate.of(year, Month.OCTOBER, 31).toString(), "🎃"); // Halloween

        holidayEmojis.put(LocalDate.of(year, Month.DECEMBER, 31).toString(), "🍾"); // New Year's Eve

        return holidayEmojis;
    }

    // ========================================
    // Lunar Phase Helper Methods
    // ========================================

    /** Calculate moon phase (0 = new moon, 0.5 = full moon) */
    private double getMoonPhase(LocalDate date) {
        // Simplified synodic month calculation
        // Reference new moon: January 6, 2000
        long daysSinceReference = date.toEpochDay() - LocalDate.of(2000, 1, 6).toEpochDay();
        double synodicMonth = 29.53058867;
        double phase = (daysSinceReference % synodicMonth) / synodicMonth;
        if (phase < 0)
            phase += 1.0;
        return phase;
    }

    /** Find the new moon within a date range */
    private LocalDate findNewMoonInRange(int year, Month startMonth, int startDay, Month endMonth, int endDay) {
        LocalDate start = LocalDate.of(year, startMonth, startDay);
        LocalDate end = LocalDate.of(year, endMonth, endDay);

        LocalDate bestDate = null;
        double bestPhase = 1.0;

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            double phase = getMoonPhase(date);
            // New moon is at phase 0 (or very close to 0 or 1)
            double distFromNew = Math.min(phase, 1.0 - phase);
            if (distFromNew < bestPhase) {
                bestPhase = distFromNew;
                bestDate = date;
            }
        }

        return bestDate;
    }

    /** Find the full moon within a date range */
    private LocalDate findFullMoonInRange(int year, Month startMonth, int startDay, Month endMonth, int endDay) {
        LocalDate start = LocalDate.of(year, startMonth, startDay);
        LocalDate end = LocalDate.of(year, endMonth, endDay);

        LocalDate bestDate = null;
        double bestDist = 1.0;

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            double phase = getMoonPhase(date);
            // Full moon is at phase 0.5
            double distFromFull = Math.abs(phase - 0.5);
            if (distFromFull < bestDist) {
                bestDist = distFromFull;
                bestDate = date;
            }
        }

        return bestDate;
    }

    /** Find the next new moon after a given date */
    private LocalDate findNewMoonAfter(LocalDate startDate) {
        LocalDate bestDate = null;
        double bestPhase = 1.0;

        // Search up to 35 days (more than one lunar cycle)
        for (int i = 1; i <= 35; i++) {
            LocalDate date = startDate.plusDays(i);
            double phase = getMoonPhase(date);
            double distFromNew = Math.min(phase, 1.0 - phase);
            if (distFromNew < bestPhase) {
                bestPhase = distFromNew;
                bestDate = date;
            }
        }

        return bestDate;
    }

    // ========================================
    // US Holidays with Emoji
    // ========================================

    /** Get US holidays with emoji mappings */
    public Map<String, String> getUSHolidaysWithEmoji(int year) {
        Map<String, String> holidayEmojis = new HashMap<>();

        holidayEmojis.put(LocalDate.of(year, Month.JANUARY, 1).toString(), "🎉"); // New Year's Day

        LocalDate mlkDay = getNthWeekdayOfMonth(year, Month.JANUARY, DayOfWeek.MONDAY, 3);
        holidayEmojis.put(mlkDay.toString(), "🕊️"); // MLK Day

        LocalDate presidentsDay = getNthWeekdayOfMonth(year, Month.FEBRUARY, DayOfWeek.MONDAY, 3);
        holidayEmojis.put(presidentsDay.toString(), "🏛️"); // Presidents' Day

        LocalDate memorialDay = getLastWeekdayOfMonth(year, Month.MAY, DayOfWeek.MONDAY);
        holidayEmojis.put(memorialDay.toString(), "🎖️"); // Memorial Day

        holidayEmojis.put(LocalDate.of(year, Month.JUNE, 19).toString(), "🎉"); // Juneteenth

        holidayEmojis.put(LocalDate.of(year, Month.JULY, 4).toString(), "🇺🇸"); // Independence Day

        LocalDate laborDay = getNthWeekdayOfMonth(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 1);
        holidayEmojis.put(laborDay.toString(), "👷"); // Labor Day

        LocalDate columbusDay = getNthWeekdayOfMonth(year, Month.OCTOBER, DayOfWeek.MONDAY, 2);
        holidayEmojis.put(columbusDay.toString(), "🌍"); // Columbus Day

        holidayEmojis.put(LocalDate.of(year, Month.OCTOBER, 31).toString(), "🎃"); // Halloween

        holidayEmojis.put(LocalDate.of(year, Month.NOVEMBER, 11).toString(), "🎖️"); // Veterans Day

        LocalDate thanksgiving = getNthWeekdayOfMonth(year, Month.NOVEMBER, DayOfWeek.THURSDAY, 4);
        holidayEmojis.put(thanksgiving.toString(), "🦃"); // Thanksgiving

        holidayEmojis.put(LocalDate.of(year, Month.DECEMBER, 25).toString(), "🎄"); // Christmas

        return holidayEmojis;
    }

    // ========================================
    // Christian Holidays (Easter-based)
    // ========================================

    /** Get Christian holidays for a given year */
    public Map<String, String> getChristianHolidays(int year) {
        Map<String, String> holidays = new HashMap<>();

        LocalDate easter = calculateEasterSunday(year);
        holidays.put(formatDate(easter), "Easter");
        holidays.put(formatDate(easter.minusDays(2)), HOLIDAY_GOOD_FRIDAY);
        holidays.put(formatDate(easter.minusDays(7)), "Palm Sunday");
        holidays.put(formatDate(easter.minusDays(46)), "Ash Wednesday");
        holidays.put(formatDate(easter.plusDays(39)), "Ascension");
        holidays.put(formatDate(easter.plusDays(49)), "Pentecost");
        holidays.put(formatDate(LocalDate.of(year, Month.DECEMBER, 25)), HOLIDAY_CHRISTMAS);
        holidays.put(formatDate(LocalDate.of(year, Month.DECEMBER, 24)), "Christmas Eve");
        holidays.put(formatDate(LocalDate.of(year, Month.JANUARY, 6)), "Epiphany");
        holidays.put(formatDate(LocalDate.of(year, Month.NOVEMBER, 1)), "All Saints");

        return holidays;
    }

    /** Get Christian holidays with emoji mappings */
    public Map<String, String> getChristianHolidaysWithEmoji(int year) {
        Map<String, String> holidayEmojis = new HashMap<>();

        LocalDate easter = calculateEasterSunday(year);
        holidayEmojis.put(easter.toString(), "🐑"); // Easter Sunday (Lamb of God)
        holidayEmojis.put(easter.minusDays(2).toString(), "🐟"); // Good Friday (Fish)
        holidayEmojis.put(easter.minusDays(7).toString(), "🌿"); // Palm Sunday
        holidayEmojis.put(easter.minusDays(46).toString(), "✝️"); // Ash Wednesday
        holidayEmojis.put(easter.plusDays(39).toString(), "☁️"); // Ascension Day
        holidayEmojis.put(easter.plusDays(49).toString(), "🕊️"); // Pentecost
        holidayEmojis.put(LocalDate.of(year, Month.DECEMBER, 25).toString(), "🎄"); // Christmas
        holidayEmojis.put(LocalDate.of(year, Month.DECEMBER, 24).toString(), "🕯️"); // Christmas Eve
        holidayEmojis.put(LocalDate.of(year, Month.JANUARY, 6).toString(), "⭐"); // Epiphany
        holidayEmojis.put(LocalDate.of(year, Month.NOVEMBER, 1).toString(), "👼"); // All Saints Day

        return holidayEmojis;
    }

    /** Calculate Easter Sunday using the Anonymous Gregorian algorithm */
    public LocalDate calculateEasterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }

    // ========================================
    // Canadian Holidays
    // ========================================

    /** Get Canadian holidays for a given year */
    public Map<String, String> getCanadianHolidays(int year) {
        Map<String, String> holidays = new HashMap<>();

        holidays.put(formatDate(LocalDate.of(year, Month.JANUARY, 1)), HOLIDAY_NEW_YEARS_DAY);
        LocalDate familyDay = getNthWeekdayOfMonth(year, Month.FEBRUARY, DayOfWeek.MONDAY, 3);
        holidays.put(formatDate(familyDay), "Family Day");
        LocalDate easter = calculateEasterSunday(year);
        holidays.put(formatDate(easter.minusDays(2)), HOLIDAY_GOOD_FRIDAY);
        LocalDate victoriaDay = calculateVictoriaDay(year);
        holidays.put(formatDate(victoriaDay), "Victoria Day");
        holidays.put(formatDate(LocalDate.of(year, Month.JULY, 1)), "Canada Day");
        LocalDate labourDay = getNthWeekdayOfMonth(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 1);
        holidays.put(formatDate(labourDay), "Labour Day");
        LocalDate thanksgiving = getNthWeekdayOfMonth(year, Month.OCTOBER, DayOfWeek.MONDAY, 2);
        holidays.put(formatDate(thanksgiving), "Thanksgiving");
        holidays.put(formatDate(LocalDate.of(year, Month.NOVEMBER, 11)), "Remembrance Day");
        holidays.put(formatDate(LocalDate.of(year, Month.DECEMBER, 25)), HOLIDAY_CHRISTMAS);
        holidays.put(formatDate(LocalDate.of(year, Month.DECEMBER, 26)), "Boxing Day");

        return holidays;
    }

    /** Get Canadian holidays with emoji mappings */
    public Map<String, String> getCanadianHolidaysWithEmoji(int year) {
        Map<String, String> holidayEmojis = new HashMap<>();

        holidayEmojis.put(LocalDate.of(year, Month.JANUARY, 1).toString(), "🎉"); // New Year's Day
        LocalDate familyDay = getNthWeekdayOfMonth(year, Month.FEBRUARY, DayOfWeek.MONDAY, 3);
        holidayEmojis.put(familyDay.toString(), EMOJI_FAMILY); // Family Day
        LocalDate easter = calculateEasterSunday(year);
        holidayEmojis.put(easter.minusDays(2).toString(), "🐟"); // Good Friday
        LocalDate victoriaDay = calculateVictoriaDay(year);
        holidayEmojis.put(victoriaDay.toString(), "👑"); // Victoria Day
        holidayEmojis.put(LocalDate.of(year, Month.JULY, 1).toString(), "🍁"); // Canada Day
        LocalDate labourDay = getNthWeekdayOfMonth(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 1);
        holidayEmojis.put(labourDay.toString(), "👷"); // Labour Day
        LocalDate thanksgiving = getNthWeekdayOfMonth(year, Month.OCTOBER, DayOfWeek.MONDAY, 2);
        holidayEmojis.put(thanksgiving.toString(), "🦃"); // Thanksgiving
        holidayEmojis.put(LocalDate.of(year, Month.NOVEMBER, 11).toString(), "🎖️"); // Remembrance Day
        holidayEmojis.put(LocalDate.of(year, Month.DECEMBER, 25).toString(), "🎄"); // Christmas
        holidayEmojis.put(LocalDate.of(year, Month.DECEMBER, 26).toString(), "🎁"); // Boxing Day

        return holidayEmojis;
    }

    /** Calculate Victoria Day - Monday on or before May 24 */
    private LocalDate calculateVictoriaDay(int year) {
        LocalDate may24 = LocalDate.of(year, Month.MAY, 24);
        while (may24.getDayOfWeek() != DayOfWeek.MONDAY) {
            may24 = may24.minusDays(1);
        }
        return may24;
    }

    // ========================================
    // UK Holidays
    // ========================================

    /** Get UK holidays for a given year */
    public Map<String, String> getUKHolidays(int year) {
        Map<String, String> holidays = new HashMap<>();

        holidays.put(formatDate(LocalDate.of(year, Month.JANUARY, 1)), HOLIDAY_NEW_YEARS_DAY);
        LocalDate easter = calculateEasterSunday(year);
        holidays.put(formatDate(easter.minusDays(2)), HOLIDAY_GOOD_FRIDAY);
        holidays.put(formatDate(easter.plusDays(1)), "Easter Monday");
        LocalDate earlyMay = getNthWeekdayOfMonth(year, Month.MAY, DayOfWeek.MONDAY, 1);
        holidays.put(formatDate(earlyMay), "Early May");
        LocalDate springBank = getLastWeekdayOfMonth(year, Month.MAY, DayOfWeek.MONDAY);
        holidays.put(formatDate(springBank), "Spring Bank");
        LocalDate summerBank = getLastWeekdayOfMonth(year, Month.AUGUST, DayOfWeek.MONDAY);
        holidays.put(formatDate(summerBank), "Summer Bank");
        holidays.put(formatDate(LocalDate.of(year, Month.DECEMBER, 25)), HOLIDAY_CHRISTMAS);
        holidays.put(formatDate(LocalDate.of(year, Month.DECEMBER, 26)), "Boxing Day");

        return holidays;
    }

    /** Get UK holidays with emoji mappings */
    public Map<String, String> getUKHolidaysWithEmoji(int year) {
        Map<String, String> holidayEmojis = new HashMap<>();

        holidayEmojis.put(LocalDate.of(year, Month.JANUARY, 1).toString(), "🎉"); // New Year's Day
        LocalDate easter = calculateEasterSunday(year);
        holidayEmojis.put(easter.minusDays(2).toString(), "🐟"); // Good Friday
        holidayEmojis.put(easter.plusDays(1).toString(), "🐰"); // Easter Monday
        LocalDate earlyMay = getNthWeekdayOfMonth(year, Month.MAY, DayOfWeek.MONDAY, 1);
        holidayEmojis.put(earlyMay.toString(), "🌸"); // Early May Bank Holiday
        LocalDate springBank = getLastWeekdayOfMonth(year, Month.MAY, DayOfWeek.MONDAY);
        holidayEmojis.put(springBank.toString(), "🌷"); // Spring Bank Holiday
        LocalDate summerBank = getLastWeekdayOfMonth(year, Month.AUGUST, DayOfWeek.MONDAY);
        holidayEmojis.put(summerBank.toString(), "☀️"); // Summer Bank Holiday
        holidayEmojis.put(LocalDate.of(year, Month.DECEMBER, 25).toString(), "🎄"); // Christmas
        holidayEmojis.put(LocalDate.of(year, Month.DECEMBER, 26).toString(), "🎁"); // Boxing Day

        return holidayEmojis;
    }

    // ========================================
    // Major World Holidays
    // ========================================

    /** Get major world holidays for a given year */
    public Map<String, String> getMajorWorldHolidays(int year) {
        Map<String, String> holidays = new HashMap<>();

        holidays.put(formatDate(LocalDate.of(year, Month.JANUARY, 1)), HOLIDAY_NEW_YEARS_DAY);
        holidays.put(formatDate(LocalDate.of(year, Month.FEBRUARY, 14)), "Valentine's Day");
        holidays.put(formatDate(LocalDate.of(year, Month.MARCH, 17)), "St. Patrick's");
        holidays.put(formatDate(LocalDate.of(year, Month.APRIL, 22)), "Earth Day");
        holidays.put(formatDate(LocalDate.of(year, Month.MAY, 1)), "Workers' Day");
        holidays.put(formatDate(LocalDate.of(year, Month.OCTOBER, 31)), "Halloween");
        holidays.put(formatDate(LocalDate.of(year, Month.DECEMBER, 25)), HOLIDAY_CHRISTMAS);
        holidays.put(formatDate(LocalDate.of(year, Month.DECEMBER, 31)), "New Year's Eve");
        LocalDate easter = calculateEasterSunday(year);
        holidays.put(formatDate(easter), "Easter");

        return holidays;
    }

    /** Get major world holidays with emoji mappings */
    public Map<String, String> getMajorWorldHolidaysWithEmoji(int year) {
        Map<String, String> holidayEmojis = new HashMap<>();

        holidayEmojis.put(LocalDate.of(year, Month.JANUARY, 1).toString(), "🎉"); // New Year's Day
        holidayEmojis.put(LocalDate.of(year, Month.FEBRUARY, 14).toString(), "❤️"); // Valentine's Day
        holidayEmojis.put(LocalDate.of(year, Month.MARCH, 17).toString(), "☘️"); // St. Patrick's Day
        holidayEmojis.put(LocalDate.of(year, Month.APRIL, 22).toString(), "🌍"); // Earth Day
        holidayEmojis.put(LocalDate.of(year, Month.MAY, 1).toString(), "👷"); // International Workers' Day
        holidayEmojis.put(LocalDate.of(year, Month.OCTOBER, 31).toString(), "🎃"); // Halloween
        holidayEmojis.put(LocalDate.of(year, Month.DECEMBER, 25).toString(), "🎄"); // Christmas
        holidayEmojis.put(LocalDate.of(year, Month.DECEMBER, 31).toString(), "🎉"); // New Year's Eve
        LocalDate easter = calculateEasterSunday(year);
        holidayEmojis.put(easter.toString(), "🐰"); // Easter

        return holidayEmojis;
    }

    // ========================================
    // Date Calculation Helpers
    // ========================================

    /** Get the nth weekday of a month (e.g., 3rd Monday of January) */
    private LocalDate getNthWeekdayOfMonth(int year, Month month, DayOfWeek dayOfWeek, int n) {
        LocalDate firstDay = LocalDate.of(year, month, 1);
        LocalDate firstWeekday = firstDay;

        while (firstWeekday.getDayOfWeek() != dayOfWeek) {
            firstWeekday = firstWeekday.plusDays(1);
        }

        return firstWeekday.plusWeeks(n - 1L);
    }

    /** Get the last weekday of a month (e.g., last Monday of May) */
    private LocalDate getLastWeekdayOfMonth(int year, Month month, DayOfWeek dayOfWeek) {
        LocalDate lastDay = LocalDate.of(year, month, 1).plusMonths(1).minusDays(1);

        while (lastDay.getDayOfWeek() != dayOfWeek) {
            lastDay = lastDay.minusDays(1);
        }

        return lastDay;
    }
}
