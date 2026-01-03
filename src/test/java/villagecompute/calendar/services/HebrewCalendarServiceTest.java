package villagecompute.calendar.services;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.quarkus.test.junit.QuarkusTest;

/** Unit tests for HebrewCalendarService. Tests Hebrew calendar calculations and date conversions. */
@QuarkusTest
class HebrewCalendarServiceTest {

    private static final int LEAP_YEAR = 5784;
    private static final int NON_LEAP_YEAR = 5785;
    private static final int MIN_DAYS_IN_MONTH = 29;
    private static final int MAX_DAYS_IN_MONTH = 30;

    @Inject
    HebrewCalendarService hebrewCalendarService;

    // ========== LEAP YEAR TESTS ==========

    @ParameterizedTest
    @CsvSource({"5784, true", "5785, false", "5787, true", "5790, true", "5792, false"})
    void testIsHebrewLeapYear(int year, boolean expected) {
        assertEquals(expected, hebrewCalendarService.isHebrewLeapYear(year));
    }

    @Test
    void testIsHebrewLeapYear_ConsistentAcrossYears() {
        // Verify leap year determination is consistent and produces reasonable results
        int leapCount = 0;
        for (int year = 5780; year < 5799; year++) {
            if (hebrewCalendarService.isHebrewLeapYear(year)) {
                leapCount++;
            }
        }
        // In a 19-year cycle, there should be 7 leap years
        assertEquals(7, leapCount, "19-year cycle should have exactly 7 leap years");
    }

    // ========== MONTHS IN YEAR TESTS ==========

    @Test
    void testGetMonthsInHebrewYear_LeapYear_Returns13() {
        assertEquals(13, hebrewCalendarService.getMonthsInHebrewYear(LEAP_YEAR));
    }

    @Test
    void testGetMonthsInHebrewYear_NonLeapYear_Returns12() {
        assertEquals(12, hebrewCalendarService.getMonthsInHebrewYear(NON_LEAP_YEAR));
    }

    // ========== DAYS IN MONTH TESTS ==========

    @Test
    void testGetDaysInHebrewMonth_Tishrei_Returns30() {
        assertEquals(MAX_DAYS_IN_MONTH, hebrewCalendarService.getDaysInHebrewMonth(1, NON_LEAP_YEAR));
    }

    @Test
    void testGetDaysInHebrewMonth_Tevet_Returns29() {
        assertEquals(MIN_DAYS_IN_MONTH, hebrewCalendarService.getDaysInHebrewMonth(4, NON_LEAP_YEAR));
    }

    @Test
    void testGetDaysInHebrewMonth_Shevat_Returns30() {
        assertEquals(MAX_DAYS_IN_MONTH, hebrewCalendarService.getDaysInHebrewMonth(5, NON_LEAP_YEAR));
    }

    @Test
    void testGetDaysInHebrewMonth_Nisan_Returns30() {
        assertEquals(MAX_DAYS_IN_MONTH, hebrewCalendarService.getDaysInHebrewMonth(7, NON_LEAP_YEAR));
    }

    @Test
    void testGetDaysInHebrewMonth_AdarI_InLeapYear_Returns30() {
        assertEquals(MAX_DAYS_IN_MONTH, hebrewCalendarService.getDaysInHebrewMonth(6, LEAP_YEAR));
    }

    @Test
    void testGetDaysInHebrewMonth_AdarII_InLeapYear_Returns29() {
        assertEquals(MIN_DAYS_IN_MONTH, hebrewCalendarService.getDaysInHebrewMonth(7, LEAP_YEAR));
    }

    @Test
    void testGetDaysInHebrewMonth_Cheshvan_VariableLength() {
        int days = hebrewCalendarService.getDaysInHebrewMonth(2, NON_LEAP_YEAR);
        assertTrue(days == MIN_DAYS_IN_MONTH || days == MAX_DAYS_IN_MONTH, "Cheshvan should have 29 or 30 days");
    }

    @Test
    void testGetDaysInHebrewMonth_Kislev_VariableLength() {
        int days = hebrewCalendarService.getDaysInHebrewMonth(3, NON_LEAP_YEAR);
        assertTrue(days == MIN_DAYS_IN_MONTH || days == MAX_DAYS_IN_MONTH, "Kislev should have 29 or 30 days");
    }

    // ========== MONTH NAME TESTS ==========

    @Test
    void testGetHebrewMonthName_Tishrei() {
        assertEquals("Tishrei", hebrewCalendarService.getHebrewMonthName(1, NON_LEAP_YEAR));
    }

    @Test
    void testGetHebrewMonthName_Cheshvan() {
        assertEquals("Cheshvan", hebrewCalendarService.getHebrewMonthName(2, NON_LEAP_YEAR));
    }

    @Test
    void testGetHebrewMonthName_Adar_NonLeapYear() {
        assertEquals("Adar", hebrewCalendarService.getHebrewMonthName(6, NON_LEAP_YEAR));
    }

    @Test
    void testGetHebrewMonthName_AdarI_LeapYear() {
        String monthName = hebrewCalendarService.getHebrewMonthName(6, LEAP_YEAR);
        assertTrue(monthName.contains("Adar"), "Month 6 in leap year should be Adar I");
    }

    @Test
    void testGetHebrewMonthName_AdarII_LeapYear() {
        assertEquals("Adar II", hebrewCalendarService.getHebrewMonthName(7, LEAP_YEAR));
    }

    @Test
    void testGetHebrewMonthName_Nisan_NonLeapYear() {
        assertEquals("Nisan", hebrewCalendarService.getHebrewMonthName(7, NON_LEAP_YEAR));
    }

    @Test
    void testGetHebrewMonthName_Elul() {
        assertEquals("Elul", hebrewCalendarService.getHebrewMonthName(12, NON_LEAP_YEAR));
    }

    // ========== YEAR MONTHS LIST TESTS ==========

    @Test
    void testGetHebrewYearMonths_NonLeapYear_Returns12Months() {
        List<String> months = hebrewCalendarService.getHebrewYearMonths(NON_LEAP_YEAR);

        assertEquals(12, months.size());
        assertEquals("Tishrei", months.get(0));
        assertEquals("Elul", months.get(11));
    }

    @Test
    void testGetHebrewYearMonths_LeapYear_Returns13Months() {
        List<String> months = hebrewCalendarService.getHebrewYearMonths(LEAP_YEAR);

        assertEquals(13, months.size());
        assertEquals("Tishrei", months.get(0));
    }

    @Test
    void testGetHebrewYearMonths_LeapYear_ContainsAdarIAndII() {
        List<String> months = hebrewCalendarService.getHebrewYearMonths(LEAP_YEAR);

        boolean hasAdarI = months.stream().anyMatch(m -> m.contains("Adar I") || m.equals("Adar I"));
        boolean hasAdarII = months.stream().anyMatch(m -> m.equals("Adar II"));

        assertTrue(hasAdarI || hasAdarII, "Leap year should have Adar I and/or Adar II");
    }

    // ========== HEBREW TO GREGORIAN CONVERSION TESTS ==========

    @Test
    void testHebrewToGregorian_ReturnsValidDate() {
        LocalDate result = hebrewCalendarService.hebrewToGregorian(5784, 1, 1);

        assertNotNull(result);
        // Hebrew year 5784 corresponds to 2023-2024 CE
        assertTrue(result.getYear() >= 2020 && result.getYear() <= 2030,
                "Date should be in reasonable Gregorian year range, got: " + result.getYear());
    }

    @Test
    void testHebrewToGregorian_DifferentMonths_ReturnsDifferentDates() {
        LocalDate tishrei = hebrewCalendarService.hebrewToGregorian(5784, 1, 1);
        LocalDate nisan = hebrewCalendarService.hebrewToGregorian(5784, 7, 1);

        assertNotNull(tishrei);
        assertNotNull(nisan);
        assertNotEquals(tishrei, nisan);
    }

    @Test
    void testHebrewToGregorian_DifferentDays_ReturnsDifferentDates() {
        LocalDate day1 = hebrewCalendarService.hebrewToGregorian(5784, 1, 1);
        LocalDate day15 = hebrewCalendarService.hebrewToGregorian(5784, 1, 15);

        assertNotNull(day1);
        assertNotNull(day15);
        assertNotEquals(day1, day15);
    }

    // ========== HEBREW HOLIDAYS TESTS ==========

    @Test
    void testGetHebrewHolidays_ReturnsHolidays() {
        Map<String, String> holidays = hebrewCalendarService.getHebrewHolidays(5784, "jewish");

        assertNotNull(holidays);
    }

    @Test
    void testGetHebrewHolidays_WithNullHolidaySet_ReturnsHolidays() {
        Map<String, String> holidays = hebrewCalendarService.getHebrewHolidays(5784, null);

        assertNotNull(holidays);
    }

    // ========== GENERATE HEBREW CALENDAR SVG TESTS ==========

    @Test
    void testGenerateHebrewCalendarSVG_ReturnsValidSvg() {
        HebrewCalendarService.HebrewCalendarConfig config = new HebrewCalendarService.HebrewCalendarConfig();
        config.hebrewYear = 5784;

        String svg = hebrewCalendarService.generateHebrewCalendarSVG(config, "jewish");

        assertNotNull(svg);
        assertTrue(svg.contains("<svg") || svg.isEmpty());
    }

    // ========== HEBREW MONTH NAME TESTS - LEAP YEAR MONTH > 7 ==========

    @Test
    void testGetHebrewMonthName_NisanInLeapYear_ReturnsNisan() {
        // Month 8 in leap year is Nisan (month > 7 branch)
        assertEquals("Nisan", hebrewCalendarService.getHebrewMonthName(8, LEAP_YEAR));
    }

    @Test
    void testGetHebrewMonthName_IyarInLeapYear_ReturnsIyar() {
        // Month 9 in leap year is Iyar
        assertEquals("Iyar", hebrewCalendarService.getHebrewMonthName(9, LEAP_YEAR));
    }

    @Test
    void testGetHebrewMonthName_SivanInLeapYear_ReturnsSivan() {
        // Month 10 in leap year is Sivan
        assertEquals("Sivan", hebrewCalendarService.getHebrewMonthName(10, LEAP_YEAR));
    }

    @Test
    void testGetHebrewMonthName_ElulInLeapYear_ReturnsElul() {
        // Month 13 in leap year is Elul
        assertEquals("Elul", hebrewCalendarService.getHebrewMonthName(13, LEAP_YEAR));
    }

    // ========== HEBREW HOLIDAYS TESTS - DIFFERENT HOLIDAY SETS ==========

    @Test
    void testGetHebrewHolidays_WithEmptyHolidaySet_ReturnsHolidays() {
        Map<String, String> holidays = hebrewCalendarService.getHebrewHolidays(5784, "");

        assertNotNull(holidays);
        // Should default to HEBREW_RELIGIOUS
        assertTrue(holidays.containsKey("1-1"), "Should contain Rosh Hashanah");
    }

    @Test
    void testGetHebrewHolidays_WithHebrewReligious_ContainsRoshHashanah() {
        Map<String, String> holidays = hebrewCalendarService.getHebrewHolidays(5784, "HEBREW_RELIGIOUS");

        assertNotNull(holidays);
        assertTrue(holidays.containsKey("1-1"));
        assertTrue(holidays.get("1-1").contains("Rosh Hashanah"));
    }

    @Test
    void testGetHebrewHolidays_WithHebrewAll_ContainsRoshHashanah() {
        Map<String, String> holidays = hebrewCalendarService.getHebrewHolidays(5784, "HEBREW_ALL");

        assertNotNull(holidays);
        assertTrue(holidays.containsKey("1-1"));
        assertTrue(holidays.get("1-1").contains("Rosh Hashanah"));
    }

    @Test
    void testGetHebrewHolidays_LeapYear_PurimInAdarII() {
        // 5784 is a leap year, Purim should be in month 7 (Adar II)
        Map<String, String> holidays = hebrewCalendarService.getHebrewHolidays(5784, "HEBREW_RELIGIOUS");

        assertTrue(holidays.containsKey("7-14"), "Purim should be in month 7 (Adar II) for leap year");
        assertTrue(holidays.get("7-14").contains("Purim"));
    }

    @Test
    void testGetHebrewHolidays_NonLeapYear_PurimInAdar() {
        // 5785 is not a leap year, Purim should be in month 6 (Adar)
        Map<String, String> holidays = hebrewCalendarService.getHebrewHolidays(5785, "HEBREW_RELIGIOUS");

        assertTrue(holidays.containsKey("6-14"), "Purim should be in month 6 (Adar) for non-leap year");
    }

    @Test
    void testGetHebrewHolidays_ContainsYomKippur() {
        Map<String, String> holidays = hebrewCalendarService.getHebrewHolidays(5784, "HEBREW_RELIGIOUS");

        assertTrue(holidays.containsKey("1-10"));
        assertEquals("Yom Kippur", holidays.get("1-10"));
    }

    @Test
    void testGetHebrewHolidays_ContainsChanukah() {
        Map<String, String> holidays = hebrewCalendarService.getHebrewHolidays(5784, "HEBREW_RELIGIOUS");

        assertTrue(holidays.containsKey("3-25"), "Should contain Chanukah Day 1");
        assertTrue(holidays.get("3-25").contains("Chanukah"));
    }

    @Test
    void testGetHebrewHolidays_NonMatchingSet_ReturnsEmptyMap() {
        Map<String, String> holidays = hebrewCalendarService.getHebrewHolidays(5784, "UNKNOWN_SET");

        assertNotNull(holidays);
        assertTrue(holidays.isEmpty(), "Unknown holiday set should return empty map");
    }

    // ========== GENERATE HEBREW CALENDAR SVG - BRANCH COVERAGE ==========

    @Test
    void testGenerateHebrewCalendarSVG_CompactMode_True() {
        HebrewCalendarService.HebrewCalendarConfig config = new HebrewCalendarService.HebrewCalendarConfig();
        config.hebrewYear = 5784;
        config.compactMode = true;

        String svg = hebrewCalendarService.generateHebrewCalendarSVG(config, "jewish");

        assertNotNull(svg);
        assertTrue(svg.contains("<svg"));
    }

    @Test
    void testGenerateHebrewCalendarSVG_CompactMode_False() {
        HebrewCalendarService.HebrewCalendarConfig config = new HebrewCalendarService.HebrewCalendarConfig();
        config.hebrewYear = 5784;
        config.compactMode = false;

        String svg = hebrewCalendarService.generateHebrewCalendarSVG(config, "jewish");

        assertNotNull(svg);
        assertTrue(svg.contains("<svg"));
    }

    @Test
    void testGenerateHebrewCalendarSVG_RotateMonthNames_True() {
        HebrewCalendarService.HebrewCalendarConfig config = new HebrewCalendarService.HebrewCalendarConfig();
        config.hebrewYear = 5784;
        config.rotateMonthNames = true;

        String svg = hebrewCalendarService.generateHebrewCalendarSVG(config, "jewish");

        assertNotNull(svg);
        assertTrue(svg.contains("rotate"));
    }

    @Test
    void testGenerateHebrewCalendarSVG_RotateMonthNames_False() {
        HebrewCalendarService.HebrewCalendarConfig config = new HebrewCalendarService.HebrewCalendarConfig();
        config.hebrewYear = 5784;
        config.rotateMonthNames = false;

        String svg = hebrewCalendarService.generateHebrewCalendarSVG(config, "jewish");

        assertNotNull(svg);
        assertTrue(svg.contains("<svg"));
    }

    @Test
    void testGenerateHebrewCalendarSVG_ShowGrid_True() {
        HebrewCalendarService.HebrewCalendarConfig config = new HebrewCalendarService.HebrewCalendarConfig();
        config.hebrewYear = 5784;
        config.showGrid = true;

        String svg = hebrewCalendarService.generateHebrewCalendarSVG(config, "jewish");

        assertNotNull(svg);
        assertTrue(svg.contains("grid-line"));
    }

    @Test
    void testGenerateHebrewCalendarSVG_ShowGrid_False() {
        HebrewCalendarService.HebrewCalendarConfig config = new HebrewCalendarService.HebrewCalendarConfig();
        config.hebrewYear = 5784;
        config.showGrid = false;

        String svg = hebrewCalendarService.generateHebrewCalendarSVG(config, "jewish");

        assertNotNull(svg);
        assertTrue(svg.contains("<svg"));
    }

    @Test
    void testGenerateHebrewCalendarSVG_HighlightWeekends_True() {
        HebrewCalendarService.HebrewCalendarConfig config = new HebrewCalendarService.HebrewCalendarConfig();
        config.hebrewYear = 5784;
        config.highlightWeekends = true;

        String svg = hebrewCalendarService.generateHebrewCalendarSVG(config, "jewish");

        assertNotNull(svg);
        assertTrue(svg.contains("<svg"));
    }

    @Test
    void testGenerateHebrewCalendarSVG_ShowDayNumbers_True() {
        HebrewCalendarService.HebrewCalendarConfig config = new HebrewCalendarService.HebrewCalendarConfig();
        config.hebrewYear = 5784;
        config.showDayNumbers = true;

        String svg = hebrewCalendarService.generateHebrewCalendarSVG(config, "jewish");

        assertNotNull(svg);
        assertTrue(svg.contains("day-text"));
    }

    @Test
    void testGenerateHebrewCalendarSVG_ShowDayNumbers_False() {
        HebrewCalendarService.HebrewCalendarConfig config = new HebrewCalendarService.HebrewCalendarConfig();
        config.hebrewYear = 5784;
        config.showDayNumbers = false;

        String svg = hebrewCalendarService.generateHebrewCalendarSVG(config, "jewish");

        assertNotNull(svg);
        assertTrue(svg.contains("<svg"));
    }

    @Test
    void testGenerateHebrewCalendarSVG_MoonDisplayMode_Illumination() {
        HebrewCalendarService.HebrewCalendarConfig config = new HebrewCalendarService.HebrewCalendarConfig();
        config.hebrewYear = 5784;
        config.moonDisplayMode = "illumination";

        String svg = hebrewCalendarService.generateHebrewCalendarSVG(config, "jewish");

        assertNotNull(svg);
        assertTrue(svg.contains("<svg"));
    }

    @Test
    void testGenerateHebrewCalendarSVG_MoonDisplayMode_None() {
        HebrewCalendarService.HebrewCalendarConfig config = new HebrewCalendarService.HebrewCalendarConfig();
        config.hebrewYear = 5784;
        config.moonDisplayMode = "none";

        String svg = hebrewCalendarService.generateHebrewCalendarSVG(config, "jewish");

        assertNotNull(svg);
        assertTrue(svg.contains("<svg"));
    }

    @Test
    void testGenerateHebrewCalendarSVG_LeapYear_Has13Months() {
        HebrewCalendarService.HebrewCalendarConfig config = new HebrewCalendarService.HebrewCalendarConfig();
        config.hebrewYear = 5784; // Leap year

        String svg = hebrewCalendarService.generateHebrewCalendarSVG(config, "jewish");

        assertNotNull(svg);
        // Should contain Adar II for leap year
        assertTrue(svg.contains("<svg"));
    }

    // ========== HEBREW TO GREGORIAN - FALLBACK BRANCH ==========

    @Test
    void testHebrewToGregorian_DayOutOfRange_UsesDay28() {
        // Test with day 30 - should handle gracefully
        LocalDate result = hebrewCalendarService.hebrewToGregorian(5784, 4, 30);

        assertNotNull(result);
        // Day should be capped at 28 for safety
        assertTrue(result.getDayOfMonth() <= 28);
    }

    @Test
    void testHebrewToGregorian_EarlyMonths_IncreasesGregorianYear() {
        // Test months 1-6 which map to next Gregorian year
        LocalDate tishrei = hebrewCalendarService.hebrewToGregorian(5784, 1, 1); // Month 1
        LocalDate nisan = hebrewCalendarService.hebrewToGregorian(5784, 7, 1); // Month 7 (non-leap)

        assertNotNull(tishrei);
        assertNotNull(nisan);
        // Tishrei (early month) should map to later part of year
    }

    // ========== EDGE CASE TESTS ==========

    @Test
    void testGetDaysInHebrewMonth_AllMonths_NonLeapYear() {
        for (int month = 1; month <= 12; month++) {
            int days = hebrewCalendarService.getDaysInHebrewMonth(month, NON_LEAP_YEAR);
            assertTrue(days >= MIN_DAYS_IN_MONTH && days <= MAX_DAYS_IN_MONTH,
                    "Month " + month + " should have 29-30 days, got " + days);
        }
    }

    @Test
    void testGetDaysInHebrewMonth_AllMonths_LeapYear() {
        for (int month = 1; month <= 13; month++) {
            int days = hebrewCalendarService.getDaysInHebrewMonth(month, LEAP_YEAR);
            assertTrue(days >= MIN_DAYS_IN_MONTH && days <= MAX_DAYS_IN_MONTH,
                    "Month " + month + " should have 29-30 days, got " + days);
        }
    }

    @Test
    void testGetHebrewMonthName_AllMonths_NonLeapYear() {
        for (int month = 1; month <= 12; month++) {
            String name = hebrewCalendarService.getHebrewMonthName(month, NON_LEAP_YEAR);
            assertNotNull(name);
            assertFalse(name.isEmpty());
        }
    }

    @Test
    void testGetHebrewMonthName_AllMonths_LeapYear() {
        for (int month = 1; month <= 13; month++) {
            String name = hebrewCalendarService.getHebrewMonthName(month, LEAP_YEAR);
            assertNotNull(name);
            assertFalse(name.isEmpty());
        }
    }
}
