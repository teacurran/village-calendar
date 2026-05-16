package villagecompute.calendar.util.astronomical;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import villagecompute.calendar.util.astronomical.HebrewCalendarConverter.HebrewDateMapping;

/**
 * Tests for {@link HebrewCalendarConverter}.
 *
 * <p>
 * The class under test combines mathematically-correct Hebrew leap-year arithmetic (the 19-year Metonic cycle used by
 * the Hillel II calendar) with a deliberately simplified Gregorian-to-Hebrew approximation. These tests assert:
 *
 * <ul>
 * <li>Leap-year detection matches the externally documented Metonic cycle (positions 3, 6, 8, 11, 14, 17, 19).</li>
 * <li>Month-name resolution honors the Adar I / Adar II split in embolismic years.</li>
 * <li>Approximate conversions remain deterministic so downstream callers can rely on stable output.</li>
 * <li>Year-mapping enumeration produces 365/366 entries for Gregorian common/leap years.</li>
 * </ul>
 *
 * <p>
 * External reference values for Hebrew leap years (5779-5795) are cross-checked against Hebcal.com and the Wikipedia
 * "Hebrew calendar" article rather than computed by the algorithm under test.
 */
class HebrewCalendarConverterTest {

    @Nested
    @DisplayName("isHebrewLeapYear")
    class LeapYearTests {

        @Test
        void leapYearsInMetonicCycleAreDetected() {
            // External reference: Hebrew years 5779, 5782, 5784, 5787, 5790, 5793, 5795
            // correspond to positions 3, 6, 8, 11, 14, 17, 19 of the 19-year cycle.
            assertTrue(HebrewCalendarConverter.isHebrewLeapYear(5779));
            assertTrue(HebrewCalendarConverter.isHebrewLeapYear(5782));
            assertTrue(HebrewCalendarConverter.isHebrewLeapYear(5784));
            assertTrue(HebrewCalendarConverter.isHebrewLeapYear(5787));
            assertTrue(HebrewCalendarConverter.isHebrewLeapYear(5790));
            assertTrue(HebrewCalendarConverter.isHebrewLeapYear(5793));
            assertTrue(HebrewCalendarConverter.isHebrewLeapYear(5795));
        }

        @Test
        void commonYearsInMetonicCycleAreDetected() {
            // Non-leap positions in the same cycle (1, 2, 4, 5, 7, 9, 10, 12, 13, 15, 16, 18).
            assertFalse(HebrewCalendarConverter.isHebrewLeapYear(5777));
            assertFalse(HebrewCalendarConverter.isHebrewLeapYear(5778));
            assertFalse(HebrewCalendarConverter.isHebrewLeapYear(5780));
            assertFalse(HebrewCalendarConverter.isHebrewLeapYear(5781));
            assertFalse(HebrewCalendarConverter.isHebrewLeapYear(5783));
            assertFalse(HebrewCalendarConverter.isHebrewLeapYear(5785));
            assertFalse(HebrewCalendarConverter.isHebrewLeapYear(5786));
            assertFalse(HebrewCalendarConverter.isHebrewLeapYear(5788));
            assertFalse(HebrewCalendarConverter.isHebrewLeapYear(5789));
            assertFalse(HebrewCalendarConverter.isHebrewLeapYear(5791));
            assertFalse(HebrewCalendarConverter.isHebrewLeapYear(5792));
            assertFalse(HebrewCalendarConverter.isHebrewLeapYear(5794));
        }

        @Test
        void cycleRepeatsEvery19Years() {
            for (int offset = -19; offset <= 19; offset += 19) {
                assertEquals(HebrewCalendarConverter.isHebrewLeapYear(5784),
                        HebrewCalendarConverter.isHebrewLeapYear(5784 + offset));
                assertEquals(HebrewCalendarConverter.isHebrewLeapYear(5783),
                        HebrewCalendarConverter.isHebrewLeapYear(5783 + offset));
            }
        }
    }

    @Nested
    @DisplayName("getMonthsInHebrewYear")
    class MonthCountTests {

        @Test
        void leapYearHas13Months() {
            assertEquals(13, HebrewCalendarConverter.getMonthsInHebrewYear(5784));
            assertEquals(13, HebrewCalendarConverter.getMonthsInHebrewYear(5782));
        }

        @Test
        void commonYearHas12Months() {
            assertEquals(12, HebrewCalendarConverter.getMonthsInHebrewYear(5783));
            assertEquals(12, HebrewCalendarConverter.getMonthsInHebrewYear(5785));
        }
    }

    @Nested
    @DisplayName("getHebrewMonthName")
    class MonthNameTests {

        @Test
        void commonYearReturnsCanonicalNames() {
            int year = 5783; // common year
            assertEquals("Tishrei", HebrewCalendarConverter.getHebrewMonthName(1, year));
            assertEquals("Cheshvan", HebrewCalendarConverter.getHebrewMonthName(2, year));
            assertEquals("Kislev", HebrewCalendarConverter.getHebrewMonthName(3, year));
            assertEquals("Tevet", HebrewCalendarConverter.getHebrewMonthName(4, year));
            assertEquals("Shevat", HebrewCalendarConverter.getHebrewMonthName(5, year));
            assertEquals("Adar", HebrewCalendarConverter.getHebrewMonthName(6, year));
            assertEquals("Nisan", HebrewCalendarConverter.getHebrewMonthName(7, year));
            assertEquals("Iyar", HebrewCalendarConverter.getHebrewMonthName(8, year));
            assertEquals("Sivan", HebrewCalendarConverter.getHebrewMonthName(9, year));
            assertEquals("Tammuz", HebrewCalendarConverter.getHebrewMonthName(10, year));
            assertEquals("Av", HebrewCalendarConverter.getHebrewMonthName(11, year));
            assertEquals("Elul", HebrewCalendarConverter.getHebrewMonthName(12, year));
        }

        @Test
        void leapYearSplitsAdarIntoTwoMonths() {
            int year = 5784; // leap year
            assertEquals("Tishrei", HebrewCalendarConverter.getHebrewMonthName(1, year));
            assertEquals("Cheshvan", HebrewCalendarConverter.getHebrewMonthName(2, year));
            assertEquals("Kislev", HebrewCalendarConverter.getHebrewMonthName(3, year));
            assertEquals("Tevet", HebrewCalendarConverter.getHebrewMonthName(4, year));
            assertEquals("Shevat", HebrewCalendarConverter.getHebrewMonthName(5, year));
            assertEquals("Adar I", HebrewCalendarConverter.getHebrewMonthName(6, year));
            assertEquals("Adar II", HebrewCalendarConverter.getHebrewMonthName(7, year));
            assertEquals("Nisan", HebrewCalendarConverter.getHebrewMonthName(8, year));
            assertEquals("Iyar", HebrewCalendarConverter.getHebrewMonthName(9, year));
            assertEquals("Sivan", HebrewCalendarConverter.getHebrewMonthName(10, year));
            assertEquals("Tammuz", HebrewCalendarConverter.getHebrewMonthName(11, year));
            assertEquals("Av", HebrewCalendarConverter.getHebrewMonthName(12, year));
            assertEquals("Elul", HebrewCalendarConverter.getHebrewMonthName(13, year));
        }
    }

    @Nested
    @DisplayName("gregorianToHebrew (approximate)")
    class GregorianToHebrewTests {

        @Test
        void januaryFirstUsesPreviousAutumnHebrewYear() {
            // Jan-Aug: Hebrew year = Gregorian year + 3760.
            String result = HebrewCalendarConverter.gregorianToHebrew(LocalDate.of(2024, 1, 1));
            assertEquals("1 Shevat 5784", result);
        }

        @Test
        void septemberRollsToNextHebrewYear() {
            // Sep-Dec: Hebrew year = Gregorian year + 3761.
            String result = HebrewCalendarConverter.gregorianToHebrew(LocalDate.of(2024, 9, 16));
            assertEquals("16 Tishrei 5785", result);
        }

        @Test
        void decemberStaysInIncomingHebrewYear() {
            String result = HebrewCalendarConverter.gregorianToHebrew(LocalDate.of(2023, 12, 31));
            assertEquals("31 Tevet 5784", result);
        }

        @Test
        void augustInCommonYearMapsToElul() {
            // 2023 -> Hebrew 5783 (common). hebrewMonth = 12 -> Elul.
            String result = HebrewCalendarConverter.gregorianToHebrew(LocalDate.of(2023, 8, 15));
            assertEquals("15 Elul 5783", result);
        }

        @Test
        void augustInLeapHebrewYearShiftsToAv() {
            // 2024 -> Hebrew 5784 (leap). hebrewMonth = 12 -> HEBREW_MONTHS[10] = "Av".
            String result = HebrewCalendarConverter.gregorianToHebrew(LocalDate.of(2024, 8, 15));
            assertEquals("15 Av 5784", result);
        }

        @Test
        void leapYearGregorianDateProducesAdarSplit() {
            // 2024 is a Gregorian leap year; Hebrew year 5784 is also a leap year.
            // February gregorian maps to hebrewMonth=6 (Adar I in leap years).
            String result = HebrewCalendarConverter.gregorianToHebrew(LocalDate.of(2024, 2, 29));
            assertEquals("29 Adar I 5784", result);
        }

        @Test
        void marchInLeapHebrewYearMapsToAdarII() {
            // March maps to hebrewMonth=7 which is Adar II in leap years.
            String result = HebrewCalendarConverter.gregorianToHebrew(LocalDate.of(2024, 3, 10));
            assertEquals("10 Adar II 5784", result);
        }

        @Test
        void marchInCommonHebrewYearMapsToNisan() {
            // 2023 is common (Hebrew 5783 is common). March maps to hebrewMonth=7=Nisan in common years.
            String result = HebrewCalendarConverter.gregorianToHebrew(LocalDate.of(2023, 3, 10));
            assertEquals("10 Nisan 5783", result);
        }
    }

    @Nested
    @DisplayName("hebrewToGregorian (approximate)")
    class HebrewToGregorianTests {

        @Test
        void nisanFifteenthRoughlyMapsToSpring() {
            // Passover 15 Nisan 5784 - month 7 in the encoding above 6, so year does not increment.
            LocalDate result = HebrewCalendarConverter.hebrewToGregorian(5784, 7, 15);
            assertEquals(LocalDate.of(2024, 3, 15), result);
        }

        @Test
        void tishreiOneRoughlyMapsToAutumn() {
            // Rosh Hashanah 1 Tishrei 5784 - month 1, the code increments base year.
            LocalDate result = HebrewCalendarConverter.hebrewToGregorian(5784, 1, 1);
            assertEquals(LocalDate.of(2025, 9, 1), result);
        }

        @Test
        void monthWrapsThroughDecember() {
            // hebrewMonth 4 -> (4+8) % 12 = 0 -> clamped to 12; baseYear increments because hebrewMonth <= 6.
            LocalDate result = HebrewCalendarConverter.hebrewToGregorian(5784, 4, 1);
            assertEquals(LocalDate.of(2025, 12, 1), result);
        }

        @Test
        void dayGreaterThanTwentyEightIsClampedToTwentyEight() {
            // hebrewMonth 12 -> gregorianMonth (12+8)%12 = 8 (August), no year bump.
            LocalDate result = HebrewCalendarConverter.hebrewToGregorian(5784, 12, 30);
            assertEquals(LocalDate.of(2024, 8, 28), result);
        }

        @Test
        void thirteenthMonthIsConvertible() {
            // hebrewMonth 13 (Elul in leap year) -> gregorianMonth (13+8)%12 = 9 (September).
            LocalDate result = HebrewCalendarConverter.hebrewToGregorian(5784, 13, 5);
            assertEquals(LocalDate.of(2024, 9, 5), result);
        }

        @Test
        void invalidDayFallsThroughToFirstOfMonth() {
            // Passing day 0 makes LocalDate.of throw, which the code catches and falls back to day 1.
            LocalDate result = HebrewCalendarConverter.hebrewToGregorian(5784, 12, 0);
            assertEquals(LocalDate.of(2024, 8, 1), result);
        }
    }

    @Nested
    @DisplayName("generateYearMappings")
    class YearMappingTests {

        @Test
        void commonGregorianYearProduces365Mappings() {
            List<HebrewDateMapping> mappings = HebrewCalendarConverter.generateYearMappings(2023, null);
            assertEquals(365, mappings.size());
            assertEquals(LocalDate.of(2023, 1, 1), mappings.get(0).gregorianDate);
            assertEquals(LocalDate.of(2023, 12, 31), mappings.get(364).gregorianDate);
        }

        @Test
        void leapGregorianYearProduces366Mappings() {
            List<HebrewDateMapping> mappings = HebrewCalendarConverter.generateYearMappings(2024, null);
            assertEquals(366, mappings.size());
        }

        @Test
        void nullHolidayMapLeavesHolidayNameNull() {
            List<HebrewDateMapping> mappings = HebrewCalendarConverter.generateYearMappings(2023, null);
            for (HebrewDateMapping mapping : mappings) {
                assertNull(mapping.holidayName);
                assertNotNull(mapping.hebrewDate);
            }
        }

        @Test
        void matchingHolidayIsTaggedWhenHebrewDateContainsValue() {
            // The lookup compares whether the formatted Hebrew date string contains the value.
            // "Nisan" appears in March/April-ish dates per the simplified converter.
            Map<String, String> holidays = new HashMap<>();
            holidays.put("1-15", "Nisan");
            List<HebrewDateMapping> mappings = HebrewCalendarConverter.generateYearMappings(2023, holidays);

            boolean foundTagged = false;
            for (HebrewDateMapping mapping : mappings) {
                if ("Nisan".equals(mapping.holidayName)) {
                    foundTagged = true;
                    assertTrue(mapping.hebrewDate.contains("Nisan"));
                }
            }
            assertTrue(foundTagged, "Expected at least one mapping tagged with Nisan holiday");
        }

        @Test
        void emptyHolidayMapLeavesHolidayNameNull() {
            Map<String, String> holidays = new HashMap<>();
            List<HebrewDateMapping> mappings = HebrewCalendarConverter.generateYearMappings(2023, holidays);
            for (HebrewDateMapping mapping : mappings) {
                assertNull(mapping.holidayName);
            }
        }
    }

    @Nested
    @DisplayName("getDaysInHebrewMonth")
    class DaysInMonthTests {

        @Test
        void leapYearAdarIHasThirtyDays() {
            assertEquals(30, HebrewCalendarConverter.getDaysInHebrewMonth(6, 5784));
        }

        @Test
        void leapYearAdarIIHasTwentyNineDays() {
            assertEquals(29, HebrewCalendarConverter.getDaysInHebrewMonth(7, 5784));
        }

        @Test
        void leapYearNisanHasThirtyDays() {
            // month 8 in leap year is Nisan; adjustedMonth=7, monthDays[6]=30.
            assertEquals(30, HebrewCalendarConverter.getDaysInHebrewMonth(8, 5784));
        }

        @Test
        void leapYearElulHasTwentyNineDays() {
            // month 13 in leap year is Elul.
            assertEquals(29, HebrewCalendarConverter.getDaysInHebrewMonth(13, 5784));
        }

        @Test
        void cheshvanLengthDependsOnYearEnding() {
            // 5784 % 10 = 4 -> short (29 days).
            assertEquals(29, HebrewCalendarConverter.getDaysInHebrewMonth(2, 5784));
            // 5785 % 10 = 5 -> long (30 days).
            assertEquals(30, HebrewCalendarConverter.getDaysInHebrewMonth(2, 5785));
            // 5788 % 10 = 8 -> long (30 days).
            assertEquals(30, HebrewCalendarConverter.getDaysInHebrewMonth(2, 5788));
        }

        @Test
        void kislevLengthDependsOnYearEnding() {
            // 5783 % 10 = 3 -> short Kislev (29 days).
            assertEquals(29, HebrewCalendarConverter.getDaysInHebrewMonth(3, 5783));
            // 5786 % 10 = 6 -> short Kislev (29 days).
            assertEquals(29, HebrewCalendarConverter.getDaysInHebrewMonth(3, 5786));
            // 5784 % 10 = 4 -> long Kislev (30 days).
            assertEquals(30, HebrewCalendarConverter.getDaysInHebrewMonth(3, 5784));
        }

        @Test
        void tishreiInCommonYearHasThirtyDays() {
            assertEquals(30, HebrewCalendarConverter.getDaysInHebrewMonth(1, 5783));
        }

        @Test
        void tevetInCommonYearHasTwentyNineDays() {
            assertEquals(29, HebrewCalendarConverter.getDaysInHebrewMonth(4, 5783));
        }

        @Test
        void elulInCommonYearHasTwentyNineDays() {
            // Common year: month 12 is Elul. monthDays[11] = 29.
            assertEquals(29, HebrewCalendarConverter.getDaysInHebrewMonth(12, 5783));
        }

        @Test
        void shevatInCommonYearHasThirtyDays() {
            // Common year: month 5 is Shevat. monthDays[4] = 30.
            assertEquals(30, HebrewCalendarConverter.getDaysInHebrewMonth(5, 5783));
        }

        @Test
        void adarInCommonYearHasTwentyNineDays() {
            // Common year: month 6 is Adar. monthDays[5] = 29.
            assertEquals(29, HebrewCalendarConverter.getDaysInHebrewMonth(6, 5783));
        }
    }

    @Nested
    @DisplayName("HebrewDateMapping data class")
    class HebrewDateMappingTests {

        @Test
        void constructorAssignsAllFields() {
            LocalDate date = LocalDate.of(2024, 4, 23);
            HebrewDateMapping mapping = new HebrewDateMapping(date, "15 Nisan 5784", "Passover");
            assertEquals(date, mapping.gregorianDate);
            assertEquals("15 Nisan 5784", mapping.hebrewDate);
            assertEquals("Passover", mapping.holidayName);
        }

        @Test
        void constructorAllowsNullHoliday() {
            LocalDate date = LocalDate.of(2024, 1, 1);
            HebrewDateMapping mapping = new HebrewDateMapping(date, "1 Shevat 5784", null);
            assertNull(mapping.holidayName);
        }
    }
}
