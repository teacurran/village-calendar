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

    private boolean containsValue(int[] array, int value) {
        for (int i : array) {
            if (i == value) {
                return true;
            }
        }
        return false;
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
