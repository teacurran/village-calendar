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
    public Map<LocalDate, HolidayType> getHolidays(int year, String setId) {
        String canonicalSet = mapHolidaySetId(setId);
        return switch (canonicalSet) {
            case SET_US -> buildUSHolidays(year);
            case SET_JEWISH, SET_HEBREW -> buildJewishHolidays(year);
            case SET_CHRISTIAN -> buildChristianHolidays(year);
            case SET_CANADIAN -> buildCanadianHolidays(year);
            case SET_UK -> buildUKHolidays(year);
            case SET_MAJOR_WORLD -> buildMajorWorldHolidays(year);
            case SET_MEXICAN -> buildMexicanHolidays(year);
            case SET_PAGAN, SET_WICCAN -> buildPaganHolidays(year);
            case SET_HINDU -> buildHinduHolidays(year);
            case SET_ISLAMIC, SET_MUSLIM -> buildIslamicHolidays(year);
            case SET_CHINESE, SET_LUNAR -> buildChineseHolidays(year);
            case SET_SECULAR, SET_FUN -> buildSecularHolidays(year);
            default -> new HashMap<>();
        };
    }

    // ========================================
    // US Federal Holidays
    // ========================================

    /** Get US Federal Holidays for a given year with both name and emoji */
    private Map<LocalDate, HolidayType> buildUSHolidays(int year) {
        Map<LocalDate, HolidayType> holidays = new HashMap<>();

        // New Year's Day - January 1
        holidays.put(LocalDate.of(year, Month.JANUARY, 1), new HolidayType(HOLIDAY_NEW_YEARS_DAY, "🎉"));

        // Martin Luther King Jr. Day - 3rd Monday in January
        LocalDate mlkDay = LocalDate.of(year, Month.JANUARY, 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.MONDAY));
        holidays.put(mlkDay, new HolidayType("Martin Luther King Jr. Day", "🕊️"));

        // Presidents' Day - 3rd Monday in February
        LocalDate presidentsDay = LocalDate.of(year, Month.FEBRUARY, 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.MONDAY));
        holidays.put(presidentsDay, new HolidayType("Presidents' Day", "🏛️"));

        // Memorial Day - Last Monday in May
        LocalDate memorialDay = LocalDate.of(year, Month.MAY, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY));
        holidays.put(memorialDay, new HolidayType("Memorial Day", "🎖️"));

        // Juneteenth - June 19
        holidays.put(LocalDate.of(year, Month.JUNE, 19), new HolidayType("Juneteenth", "🎉"));

        // Independence Day - July 4
        holidays.put(LocalDate.of(year, Month.JULY, 4), new HolidayType("Independence Day", "🇺🇸"));

        // Labor Day - 1st Monday in September
        LocalDate laborDay = LocalDate.of(year, Month.SEPTEMBER, 1)
                .with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));
        holidays.put(laborDay, new HolidayType("Labor Day", "👷"));

        // Columbus Day - 2nd Monday in October
        LocalDate columbusDay = LocalDate.of(year, Month.OCTOBER, 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.MONDAY));
        holidays.put(columbusDay, new HolidayType("Columbus Day", "🌍"));

        // Halloween - October 31
        holidays.put(LocalDate.of(year, Month.OCTOBER, 31), new HolidayType("Halloween", "🎃"));

        // Veterans Day - November 11
        holidays.put(LocalDate.of(year, Month.NOVEMBER, 11), new HolidayType("Veterans Day", "🎖️"));

        // Thanksgiving - 4th Thursday in November
        LocalDate thanksgiving = LocalDate.of(year, Month.NOVEMBER, 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY));
        holidays.put(thanksgiving, new HolidayType("Thanksgiving", "🦃"));

        // Christmas Day - December 25
        holidays.put(LocalDate.of(year, Month.DECEMBER, 25), new HolidayType("Christmas Day", "🎄"));

        return holidays;
    }

    /**
     * Build Jewish holidays for a given Gregorian year with both name and emoji. Holidays are calculated dynamically
     * based on the Hebrew calendar.
     */
    private Map<LocalDate, HolidayType> buildJewishHolidays(int gregorianYear) {
        Map<LocalDate, HolidayType> holidays = new HashMap<>();

        // Jewish holidays span two Hebrew years within one Gregorian year
        // For a Gregorian year, we need holidays from:
        // - The Hebrew year that started in the previous fall (for spring holidays)
        // - The Hebrew year that starts in the current fall (for fall holidays)

        int hebrewYearForSpring = gregorianYear + 3760; // Approximate
        int hebrewYearForFall = gregorianYear + 3761; // Hebrew year starting in fall of gregorianYear

        // Spring holidays (from Hebrew year that started previous fall)
        LocalDate purim = calculatePurim(hebrewYearForSpring);
        if (purim.getYear() == gregorianYear) {
            holidays.put(purim, new HolidayType("Purim", "🎭"));
        }

        LocalDate passoverStart = calculatePassoverStart(hebrewYearForSpring);
        if (passoverStart.getYear() == gregorianYear) {
            holidays.put(passoverStart, new HolidayType("Passover", "🍷"));
            holidays.put(passoverStart.plusDays(7), new HolidayType("Passover", "🍷"));
        }

        LocalDate shavuot = calculateShavuot(hebrewYearForSpring);
        if (shavuot.getYear() == gregorianYear) {
            holidays.put(shavuot, new HolidayType("Shavuot", "📜"));
        }

        // Fall holidays (from Hebrew year starting in current fall)
        LocalDate roshHashanah = calculateRoshHashanah(hebrewYearForFall);
        if (roshHashanah.getYear() == gregorianYear) {
            holidays.put(roshHashanah, new HolidayType("Rosh Hashanah", "🍎"));
            holidays.put(roshHashanah.plusDays(1), new HolidayType("Rosh Hashanah", "🍎"));
        }

        LocalDate yomKippur = calculateYomKippur(hebrewYearForFall);
        if (yomKippur.getYear() == gregorianYear) {
            holidays.put(yomKippur, new HolidayType("Yom Kippur", "✡️"));
        }

        LocalDate sukkot = calculateSukkotStart(hebrewYearForFall);
        if (sukkot.getYear() == gregorianYear) {
            holidays.put(sukkot, new HolidayType("Sukkot", "🌿"));
            holidays.put(sukkot.plusDays(7), new HolidayType("Shemini Atzeret", "📜"));
            holidays.put(sukkot.plusDays(8), new HolidayType("Simchat Torah", "📜"));
        }

        LocalDate chanukah = calculateChanukahStart(hebrewYearForFall);
        if (chanukah.getYear() == gregorianYear) {
            holidays.put(chanukah, new HolidayType("Chanukah", "🕎"));
        }

        return holidays;
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

    // ========================================
    // Mexican/Hispanic Holidays
    // ========================================

    /** Build Mexican holidays for a given year with both name and emoji */
    private Map<LocalDate, HolidayType> buildMexicanHolidays(int year) {
        Map<LocalDate, HolidayType> holidays = new HashMap<>();

        holidays.put(LocalDate.of(year, Month.JANUARY, 6), new HolidayType("Día de los Reyes", "👑"));
        holidays.put(LocalDate.of(year, Month.FEBRUARY, 5), new HolidayType("Día de la Constitución", "📜"));
        holidays.put(LocalDate.of(year, Month.MARCH, 21), new HolidayType("Natalicio de Benito Juárez", "⚖️"));
        holidays.put(LocalDate.of(year, Month.MAY, 5), new HolidayType("Cinco de Mayo", "🇲🇽"));
        holidays.put(LocalDate.of(year, Month.SEPTEMBER, 16), new HolidayType("Día de la Independencia", "🎉"));
        holidays.put(LocalDate.of(year, Month.NOVEMBER, 1), new HolidayType("Día de los Muertos", "💀"));
        holidays.put(LocalDate.of(year, Month.NOVEMBER, 2), new HolidayType("Día de los Muertos", "💀"));
        holidays.put(LocalDate.of(year, Month.NOVEMBER, 20), new HolidayType("Día de la Revolución", "🎖️"));
        holidays.put(LocalDate.of(year, Month.DECEMBER, 12), new HolidayType("Virgen de Guadalupe", "🌹"));
        // Las Posadas - December 16-24 (only first day gets emoji)
        holidays.put(LocalDate.of(year, Month.DECEMBER, 16), new HolidayType("Las Posadas", "🕯️"));
        for (int day = 17; day <= 24; day++) {
            holidays.put(LocalDate.of(year, Month.DECEMBER, day), new HolidayType("Las Posadas", null));
        }

        return holidays;
    }

    // ========================================
    // Pagan/Wiccan Holidays (Wheel of the Year)
    // ========================================

    /** Build Pagan/Wiccan holidays for a given year with both name and emoji */
    private Map<LocalDate, HolidayType> buildPaganHolidays(int year) {
        Map<LocalDate, HolidayType> holidays = new HashMap<>();

        // Imbolc - February 1-2
        holidays.put(LocalDate.of(year, Month.FEBRUARY, 1), new HolidayType("Imbolc", "🕯️"));
        holidays.put(LocalDate.of(year, Month.FEBRUARY, 2), new HolidayType("Imbolc", null));

        // Ostara - Spring Equinox
        holidays.put(calculateSpringEquinox(year), new HolidayType("Ostara", "🐣"));

        // Beltane - May 1
        holidays.put(LocalDate.of(year, Month.MAY, 1), new HolidayType("Beltane", "🔥"));

        // Litha - Summer Solstice
        holidays.put(calculateSummerSolstice(year), new HolidayType("Litha", "☀️"));

        // Lughnasadh/Lammas - August 1
        holidays.put(LocalDate.of(year, Month.AUGUST, 1), new HolidayType("Lughnasadh", "🌾"));

        // Mabon - Autumn Equinox
        holidays.put(calculateAutumnEquinox(year), new HolidayType("Mabon", "🍂"));

        // Samhain - October 31 - November 1
        holidays.put(LocalDate.of(year, Month.OCTOBER, 31), new HolidayType("Samhain", "🎃"));
        holidays.put(LocalDate.of(year, Month.NOVEMBER, 1), new HolidayType("Samhain", null));

        // Yule - Winter Solstice
        holidays.put(calculateWinterSolstice(year), new HolidayType("Yule", "🌲"));

        return holidays;
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

    /** Build Hindu holidays for a given year with both name and emoji */
    private Map<LocalDate, HolidayType> buildHinduHolidays(int year) {
        Map<LocalDate, HolidayType> holidays = new HashMap<>();

        // Makar Sankranti - January 14
        holidays.put(LocalDate.of(year, Month.JANUARY, 14), new HolidayType("Makar Sankranti", "🪁"));

        // Holi - Full moon in Phalguna (Feb-Mar)
        LocalDate holi = calculateHoli(year);
        if (holi != null) {
            holidays.put(holi, new HolidayType("Holi", "🎨"));
        }

        // Ram Navami - 9th day of Chaitra (Mar-Apr)
        LocalDate ramNavami = calculateRamNavami(year);
        if (ramNavami != null) {
            holidays.put(ramNavami, new HolidayType("Ram Navami", "🏹"));
        }

        // Janmashtami - 8th day of Bhadrapada (Aug-Sep)
        LocalDate janmashtami = calculateJanmashtami(year);
        if (janmashtami != null) {
            holidays.put(janmashtami, new HolidayType("Janmashtami", "🪈"));
        }

        // Ganesh Chaturthi - 4th day of Bhadrapada (Aug-Sep)
        LocalDate ganeshChaturthi = calculateGaneshChaturthi(year);
        if (ganeshChaturthi != null) {
            holidays.put(ganeshChaturthi, new HolidayType("Ganesh Chaturthi", "🐘"));
        }

        // Navaratri - 9 nights starting 1st day of Ashvin (Sep-Oct)
        LocalDate navaratri = calculateNavaratri(year);
        if (navaratri != null) {
            holidays.put(navaratri, new HolidayType("Navaratri", "💃"));
        }

        // Dussehra/Vijayadashami - 10th day of Ashvin
        LocalDate dussehra = calculateDussehra(year);
        if (dussehra != null) {
            holidays.put(dussehra, new HolidayType("Dussehra", "🏹"));
        }

        // Diwali - New moon in Kartik (Oct-Nov)
        LocalDate diwali = calculateDiwali(year);
        if (diwali != null) {
            holidays.put(diwali, new HolidayType("Diwali", "🪔"));
        }

        return holidays;
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

    /** Build Islamic holidays for a given Gregorian year with both name and emoji */
    private Map<LocalDate, HolidayType> buildIslamicHolidays(int year) {
        Map<LocalDate, HolidayType> holidays = new HashMap<>();

        // Islamic calendar is purely lunar, ~11 days shorter than Gregorian
        // Calculate major holidays for both Islamic years that fall in this Gregorian year
        int[] hijriYears = getHijriYearsInGregorianYear(year);

        for (int hijriYear : hijriYears) {
            // Islamic New Year - 1 Muharram
            LocalDate islamicNewYear = hijriToGregorian(hijriYear, 1, 1);
            if (islamicNewYear.getYear() == year) {
                holidays.put(islamicNewYear, new HolidayType("Islamic New Year", "🌙"));
            }

            // Ashura - 10 Muharram
            LocalDate ashura = hijriToGregorian(hijriYear, 1, 10);
            if (ashura.getYear() == year) {
                holidays.put(ashura, new HolidayType("Ashura", "🤲"));
            }

            // Mawlid al-Nabi (Prophet's Birthday) - 12 Rabi' al-Awwal
            LocalDate mawlid = hijriToGregorian(hijriYear, 3, 12);
            if (mawlid.getYear() == year) {
                holidays.put(mawlid, new HolidayType("Mawlid al-Nabi", "☪️"));
            }

            // Ramadan Start - 1 Ramadan
            LocalDate ramadanStart = hijriToGregorian(hijriYear, 9, 1);
            if (ramadanStart.getYear() == year) {
                holidays.put(ramadanStart, new HolidayType("Ramadan Begins", "🌙"));
            }

            // Laylat al-Qadr - 27 Ramadan (Night of Power)
            LocalDate laylatAlQadr = hijriToGregorian(hijriYear, 9, 27);
            if (laylatAlQadr.getYear() == year) {
                holidays.put(laylatAlQadr, new HolidayType("Laylat al-Qadr", "✨"));
            }

            // Eid al-Fitr - 1 Shawwal
            LocalDate eidFitr = hijriToGregorian(hijriYear, 10, 1);
            if (eidFitr.getYear() == year) {
                holidays.put(eidFitr, new HolidayType("Eid al-Fitr", "🎉"));
            }

            // Eid al-Adha - 10 Dhul Hijjah
            LocalDate eidAdha = hijriToGregorian(hijriYear, 12, 10);
            if (eidAdha.getYear() == year) {
                holidays.put(eidAdha, new HolidayType("Eid al-Adha", "🐑"));
            }
        }

        return holidays;
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

    /** Build Chinese holidays for a given year with both name and emoji */
    private Map<LocalDate, HolidayType> buildChineseHolidays(int year) {
        Map<LocalDate, HolidayType> holidays = new HashMap<>();

        // Chinese New Year - 2nd new moon after winter solstice
        LocalDate chineseNewYear = calculateChineseNewYear(year);
        if (chineseNewYear != null) {
            holidays.put(chineseNewYear, new HolidayType("Chinese New Year", "🧧"));
            holidays.put(chineseNewYear.plusDays(14), new HolidayType("Lantern Festival", "🏮"));
        }

        // Qingming Festival - April 4 or 5 (15 days after spring equinox)
        LocalDate qingming = calculateSpringEquinox(year).plusDays(15);
        holidays.put(qingming, new HolidayType("Qingming Festival", "🪦"));

        // Dragon Boat Festival - 5th day of 5th lunar month
        LocalDate dragonBoat = calculateDragonBoatFestival(year);
        if (dragonBoat != null) {
            holidays.put(dragonBoat, new HolidayType("Dragon Boat Festival", "🐉"));
        }

        // Mid-Autumn Festival - 15th day of 8th lunar month
        LocalDate midAutumn = calculateMidAutumnFestival(year);
        if (midAutumn != null) {
            holidays.put(midAutumn, new HolidayType("Mid-Autumn Festival", "🥮"));
        }

        // Double Ninth Festival - 9th day of 9th lunar month
        LocalDate doubleNinth = calculateDoubleNinthFestival(year);
        if (doubleNinth != null) {
            holidays.put(doubleNinth, new HolidayType("Double Ninth Festival", "🏔️"));
        }

        return holidays;
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

    /** Build fun secular American holidays for a given year with both name and emoji */
    private Map<LocalDate, HolidayType> buildSecularHolidays(int year) {
        Map<LocalDate, HolidayType> holidays = new HashMap<>();

        // Groundhog Day - February 2
        holidays.put(LocalDate.of(year, Month.FEBRUARY, 2), new HolidayType("Groundhog Day", "🦫"));

        // Valentine's Day - February 14
        holidays.put(LocalDate.of(year, Month.FEBRUARY, 14), new HolidayType("Valentine's Day", "❤️"));

        // St. Patrick's Day - March 17
        holidays.put(LocalDate.of(year, Month.MARCH, 17), new HolidayType("St. Patrick's Day", "☘️"));

        // April Fools' Day - April 1
        holidays.put(LocalDate.of(year, Month.APRIL, 1), new HolidayType("April Fools' Day", "🃏"));

        // Earth Day - April 22
        holidays.put(LocalDate.of(year, Month.APRIL, 22), new HolidayType("Earth Day", "🌍"));

        // Cinco de Mayo - May 5 (popular in US)
        holidays.put(LocalDate.of(year, Month.MAY, 5), new HolidayType("Cinco de Mayo", "🌮"));

        // Mother's Day - 2nd Sunday in May
        LocalDate mothersDay = LocalDate.of(year, Month.MAY, 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.SUNDAY));
        holidays.put(mothersDay, new HolidayType("Mother's Day", "💐"));

        // Father's Day - 3rd Sunday in June
        LocalDate fathersDay = LocalDate.of(year, Month.JUNE, 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.SUNDAY));
        holidays.put(fathersDay, new HolidayType("Father's Day", "👔"));

        // Pride Month start - June 1
        holidays.put(LocalDate.of(year, Month.JUNE, 1), new HolidayType("Pride Month", EMOJI_RAINBOW_FLAG));

        // Halloween - October 31
        holidays.put(LocalDate.of(year, Month.OCTOBER, 31), new HolidayType("Halloween", "🎃"));

        // New Year's Eve - December 31
        holidays.put(LocalDate.of(year, Month.DECEMBER, 31), new HolidayType("New Year's Eve", "🍾"));

        return holidays;
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
    // Christian Holidays (Easter-based)
    // ========================================

    /** Build Christian holidays for a given year with both name and emoji */
    private Map<LocalDate, HolidayType> buildChristianHolidays(int year) {
        Map<LocalDate, HolidayType> holidays = new HashMap<>();

        LocalDate easter = calculateEasterSunday(year);
        holidays.put(easter, new HolidayType("Easter", "🐑"));
        holidays.put(easter.minusDays(2), new HolidayType(HOLIDAY_GOOD_FRIDAY, "🐟"));
        holidays.put(easter.minusDays(7), new HolidayType("Palm Sunday", "🌿"));
        holidays.put(easter.minusDays(46), new HolidayType("Ash Wednesday", "✝️"));
        holidays.put(easter.plusDays(39), new HolidayType("Ascension", "☁️"));
        holidays.put(easter.plusDays(49), new HolidayType("Pentecost", "🕊️"));
        holidays.put(LocalDate.of(year, Month.DECEMBER, 25), new HolidayType(HOLIDAY_CHRISTMAS, "🎄"));
        holidays.put(LocalDate.of(year, Month.DECEMBER, 24), new HolidayType("Christmas Eve", "🕯️"));
        holidays.put(LocalDate.of(year, Month.JANUARY, 6), new HolidayType("Epiphany", "⭐"));
        holidays.put(LocalDate.of(year, Month.NOVEMBER, 1), new HolidayType("All Saints", "👼"));

        return holidays;
    }

    /** Calculate Easter Sunday using the Anonymous Gregorian algorithm */
    private LocalDate calculateEasterSunday(int year) {
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

    /** Build Canadian holidays for a given year with both name and emoji */
    private Map<LocalDate, HolidayType> buildCanadianHolidays(int year) {
        Map<LocalDate, HolidayType> holidays = new HashMap<>();

        holidays.put(LocalDate.of(year, Month.JANUARY, 1), new HolidayType(HOLIDAY_NEW_YEARS_DAY, "🎉"));
        LocalDate familyDay = getNthWeekdayOfMonth(year, Month.FEBRUARY, DayOfWeek.MONDAY, 3);
        holidays.put(familyDay, new HolidayType("Family Day", EMOJI_FAMILY));
        LocalDate easter = calculateEasterSunday(year);
        holidays.put(easter.minusDays(2), new HolidayType(HOLIDAY_GOOD_FRIDAY, "🐟"));
        LocalDate victoriaDay = calculateVictoriaDay(year);
        holidays.put(victoriaDay, new HolidayType("Victoria Day", "👑"));
        holidays.put(LocalDate.of(year, Month.JULY, 1), new HolidayType("Canada Day", "🍁"));
        LocalDate labourDay = getNthWeekdayOfMonth(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 1);
        holidays.put(labourDay, new HolidayType("Labour Day", "👷"));
        LocalDate thanksgiving = getNthWeekdayOfMonth(year, Month.OCTOBER, DayOfWeek.MONDAY, 2);
        holidays.put(thanksgiving, new HolidayType("Thanksgiving", "🦃"));
        holidays.put(LocalDate.of(year, Month.NOVEMBER, 11), new HolidayType("Remembrance Day", "🎖️"));
        holidays.put(LocalDate.of(year, Month.DECEMBER, 25), new HolidayType(HOLIDAY_CHRISTMAS, "🎄"));
        holidays.put(LocalDate.of(year, Month.DECEMBER, 26), new HolidayType("Boxing Day", "🎁"));

        return holidays;
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

    /** Build UK holidays for a given year with both name and emoji */
    private Map<LocalDate, HolidayType> buildUKHolidays(int year) {
        Map<LocalDate, HolidayType> holidays = new HashMap<>();

        holidays.put(LocalDate.of(year, Month.JANUARY, 1), new HolidayType(HOLIDAY_NEW_YEARS_DAY, "🎉"));
        LocalDate easter = calculateEasterSunday(year);
        holidays.put(easter.minusDays(2), new HolidayType(HOLIDAY_GOOD_FRIDAY, "🐟"));
        holidays.put(easter.plusDays(1), new HolidayType("Easter Monday", "🐰"));
        LocalDate earlyMay = getNthWeekdayOfMonth(year, Month.MAY, DayOfWeek.MONDAY, 1);
        holidays.put(earlyMay, new HolidayType("Early May", "🌸"));
        LocalDate springBank = getLastWeekdayOfMonth(year, Month.MAY, DayOfWeek.MONDAY);
        holidays.put(springBank, new HolidayType("Spring Bank", "🌷"));
        LocalDate summerBank = getLastWeekdayOfMonth(year, Month.AUGUST, DayOfWeek.MONDAY);
        holidays.put(summerBank, new HolidayType("Summer Bank", "☀️"));
        holidays.put(LocalDate.of(year, Month.DECEMBER, 25), new HolidayType(HOLIDAY_CHRISTMAS, "🎄"));
        holidays.put(LocalDate.of(year, Month.DECEMBER, 26), new HolidayType("Boxing Day", "🎁"));

        return holidays;
    }

    // ========================================
    // Major World Holidays
    // ========================================

    /** Build major world holidays for a given year with both name and emoji */
    private Map<LocalDate, HolidayType> buildMajorWorldHolidays(int year) {
        Map<LocalDate, HolidayType> holidays = new HashMap<>();

        holidays.put(LocalDate.of(year, Month.JANUARY, 1), new HolidayType(HOLIDAY_NEW_YEARS_DAY, "🎉"));
        holidays.put(LocalDate.of(year, Month.FEBRUARY, 14), new HolidayType("Valentine's Day", "❤️"));
        holidays.put(LocalDate.of(year, Month.MARCH, 17), new HolidayType("St. Patrick's", "☘️"));
        holidays.put(LocalDate.of(year, Month.APRIL, 22), new HolidayType("Earth Day", "🌍"));
        holidays.put(LocalDate.of(year, Month.MAY, 1), new HolidayType("Workers' Day", "👷"));
        holidays.put(LocalDate.of(year, Month.OCTOBER, 31), new HolidayType("Halloween", "🎃"));
        holidays.put(LocalDate.of(year, Month.DECEMBER, 25), new HolidayType(HOLIDAY_CHRISTMAS, "🎄"));
        holidays.put(LocalDate.of(year, Month.DECEMBER, 31), new HolidayType("New Year's Eve", "🎉"));
        LocalDate easter = calculateEasterSunday(year);
        holidays.put(easter, new HolidayType("Easter", "🐰"));

        return holidays;
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
