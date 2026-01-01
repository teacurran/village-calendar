package villagecompute.calendar.services;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Unit tests for HolidayService. Tests holiday calculations and set mappings. */
class HolidayServiceTest {

    private static final int TEST_YEAR = 2025;
    private static final String DATE_FORMAT = "%d-%02d-%02d";

    private HolidayService holidayService;

    @BeforeEach
    void setUp() {
        holidayService = new HolidayService();
    }

    // ========== MAP HOLIDAY SET ID TESTS ==========

    @ParameterizedTest
    @CsvSource({"us, US", "US, US", "jewish, JEWISH", "JEWISH, JEWISH", "christian, CHRISTIAN", "muslim, ISLAMIC",
            "islamic, ISLAMIC", "buddhist, BUDDHIST", "hindu, HINDU", "in, HINDU", "canadian, CANADIAN", "ca, CANADIAN",
            "uk, UK", "european, EUROPEAN", "major_world, MAJOR_WORLD", "mexican, MEXICAN", "mx, MEXICAN",
            "pagan, PAGAN", "wiccan, PAGAN", "chinese, CHINESE", "cn, CHINESE", "lunar, CHINESE", "secular, SECULAR",
            "fun, SECULAR"})
    void testMapHolidaySetId_ValidIds(String input, String expected) {
        assertEquals(expected, holidayService.mapHolidaySetId(input));
    }

    @Test
    void testMapHolidaySetId_NullInput_ReturnsUS() {
        assertEquals(HolidayService.SET_US, holidayService.mapHolidaySetId(null));
    }

    @Test
    void testMapHolidaySetId_UnknownId_ReturnsUpperCase() {
        assertEquals("UNKNOWN", holidayService.mapHolidaySetId("unknown"));
        assertEquals("CUSTOM", holidayService.mapHolidaySetId("custom"));
    }

    // ========== US HOLIDAYS TESTS ==========

    @Test
    void testGetUSHolidays_ContainsNewYearsDay() {
        Map<String, String> holidays = holidayService.getUSHolidays(TEST_YEAR);

        assertTrue(holidays.containsValue("New Year's Day"));
        assertEquals("New Year's Day", holidays.get(formatDate(TEST_YEAR, 1, 1)));
    }

    @Test
    void testGetUSHolidays_ContainsMLKDay() {
        Map<String, String> holidays = holidayService.getUSHolidays(TEST_YEAR);

        assertTrue(holidays.containsValue("Martin Luther King Jr. Day"));
        // MLK Day is 3rd Monday in January - for 2025 it's January 20
        assertEquals("Martin Luther King Jr. Day", holidays.get(formatDate(TEST_YEAR, 1, 20)));
    }

    @Test
    void testGetUSHolidays_ContainsPresidentsDay() {
        Map<String, String> holidays = holidayService.getUSHolidays(TEST_YEAR);

        assertTrue(holidays.containsValue("Presidents' Day"));
        // Presidents' Day is 3rd Monday in February - for 2025 it's February 17
        assertEquals("Presidents' Day", holidays.get(formatDate(TEST_YEAR, 2, 17)));
    }

    @Test
    void testGetUSHolidays_ContainsMemorialDay() {
        Map<String, String> holidays = holidayService.getUSHolidays(TEST_YEAR);

        assertTrue(holidays.containsValue("Memorial Day"));
        // Memorial Day is last Monday in May - for 2025 it's May 26
        assertEquals("Memorial Day", holidays.get(formatDate(TEST_YEAR, 5, 26)));
    }

    @Test
    void testGetUSHolidays_ContainsJuneteenth() {
        Map<String, String> holidays = holidayService.getUSHolidays(TEST_YEAR);

        assertEquals("Juneteenth", holidays.get(formatDate(TEST_YEAR, 6, 19)));
    }

    @Test
    void testGetUSHolidays_ContainsIndependenceDay() {
        Map<String, String> holidays = holidayService.getUSHolidays(TEST_YEAR);

        assertEquals("Independence Day", holidays.get(formatDate(TEST_YEAR, 7, 4)));
    }

    @Test
    void testGetUSHolidays_ContainsLaborDay() {
        Map<String, String> holidays = holidayService.getUSHolidays(TEST_YEAR);

        assertTrue(holidays.containsValue("Labor Day"));
        // Labor Day is 1st Monday in September - for 2025 it's September 1
        assertEquals("Labor Day", holidays.get(formatDate(TEST_YEAR, 9, 1)));
    }

    @Test
    void testGetUSHolidays_ContainsColumbusDay() {
        Map<String, String> holidays = holidayService.getUSHolidays(TEST_YEAR);

        assertTrue(holidays.containsValue("Columbus Day"));
        // Columbus Day is 2nd Monday in October - for 2025 it's October 13
        assertEquals("Columbus Day", holidays.get(formatDate(TEST_YEAR, 10, 13)));
    }

    @Test
    void testGetUSHolidays_ContainsVeteransDay() {
        Map<String, String> holidays = holidayService.getUSHolidays(TEST_YEAR);

        assertEquals("Veterans Day", holidays.get(formatDate(TEST_YEAR, 11, 11)));
    }

    @Test
    void testGetUSHolidays_ContainsThanksgiving() {
        Map<String, String> holidays = holidayService.getUSHolidays(TEST_YEAR);

        assertTrue(holidays.containsValue("Thanksgiving"));
        // Thanksgiving is 4th Thursday in November - for 2025 it's November 27
        assertEquals("Thanksgiving", holidays.get(formatDate(TEST_YEAR, 11, 27)));
    }

    @Test
    void testGetUSHolidays_ContainsChristmas() {
        Map<String, String> holidays = holidayService.getUSHolidays(TEST_YEAR);

        assertEquals("Christmas Day", holidays.get(formatDate(TEST_YEAR, 12, 25)));
    }

    @Test
    void testGetUSHolidays_ReturnsCorrectCount() {
        Map<String, String> holidays = holidayService.getUSHolidays(TEST_YEAR);

        // US has 11 federal holidays
        assertEquals(11, holidays.size());
    }

    // ========== GET HOLIDAYS BY COUNTRY TESTS ==========

    @Test
    void testGetHolidays_USCountry_ReturnsUSHolidays() {
        Map<String, String> holidays = holidayService.getHolidays(TEST_YEAR, "US");

        assertEquals(11, holidays.size());
        assertTrue(holidays.containsValue("Independence Day"));
    }

    @Test
    void testGetHolidays_LowercaseUS_ReturnsUSHolidays() {
        Map<String, String> holidays = holidayService.getHolidays(TEST_YEAR, "us");

        assertEquals(11, holidays.size());
    }

    @Test
    void testGetHolidays_UnknownCountry_DefaultsToUS() {
        Map<String, String> holidays = holidayService.getHolidays(TEST_YEAR, "XX");

        assertEquals(11, holidays.size());
        assertTrue(holidays.containsValue("Independence Day"));
    }

    // ========== GET HOLIDAYS WITH EMOJI TESTS ==========

    @Test
    void testGetHolidaysWithEmoji_US_ReturnsEmojis() {
        Map<String, String> emojis = holidayService.getHolidaysWithEmoji(TEST_YEAR, "us");

        assertNotNull(emojis);
        assertFalse(emojis.isEmpty());
    }

    @Test
    void testGetHolidaysWithEmoji_Jewish_ReturnsEmojis() {
        Map<String, String> emojis = holidayService.getHolidaysWithEmoji(TEST_YEAR, "jewish");

        assertNotNull(emojis);
        assertFalse(emojis.isEmpty());
    }

    @Test
    void testGetHolidaysWithEmoji_UnknownSet_ReturnsEmptyMap() {
        Map<String, String> emojis = holidayService.getHolidaysWithEmoji(TEST_YEAR, "unknown_set");

        assertNotNull(emojis);
        assertTrue(emojis.isEmpty());
    }

    // ========== GET HOLIDAY NAMES TESTS ==========

    @Test
    void testGetHolidayNames_US_ReturnsNames() {
        Map<String, String> names = holidayService.getHolidayNames(TEST_YEAR, "us");

        assertNotNull(names);
        assertEquals(11, names.size());
        assertTrue(names.containsValue("Christmas Day"));
    }

    @Test
    void testGetHolidayNames_Jewish_ReturnsNames() {
        Map<String, String> names = holidayService.getHolidayNames(TEST_YEAR, "jewish");

        assertNotNull(names);
        assertFalse(names.isEmpty());
        // Jewish holidays vary but should contain major holidays
        assertTrue(names.values().stream().anyMatch(n -> n.contains("Passover") || n.contains("Rosh Hashanah")));
    }

    @Test
    void testGetHolidayNames_Christian_ReturnsNames() {
        Map<String, String> names = holidayService.getHolidayNames(TEST_YEAR, "christian");

        assertNotNull(names);
        assertFalse(names.isEmpty());
    }

    @Test
    void testGetHolidayNames_Canadian_ReturnsNames() {
        Map<String, String> names = holidayService.getHolidayNames(TEST_YEAR, "canadian");

        assertNotNull(names);
        assertFalse(names.isEmpty());
    }

    @Test
    void testGetHolidayNames_UnknownSet_ReturnsEmptyMap() {
        Map<String, String> names = holidayService.getHolidayNames(TEST_YEAR, "unknown_set");

        assertNotNull(names);
        assertTrue(names.isEmpty());
    }

    // ========== JEWISH HOLIDAYS TESTS ==========

    @Test
    void testGetJewishHolidays_ContainsMajorHolidays() {
        Map<String, String> holidays = holidayService.getJewishHolidays(TEST_YEAR);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());

        // Check that major holidays are present (names may vary slightly)
        boolean hasPassover = holidays.values().stream().anyMatch(n -> n.contains("Passover"));
        boolean hasRoshHashanah = holidays.values().stream().anyMatch(n -> n.contains("Rosh Hashanah"));

        assertTrue(hasPassover || hasRoshHashanah, "Should contain at least one major Jewish holiday");
    }

    @Test
    void testGetJewishHolidaysWithEmoji_ReturnsEmojis() {
        Map<String, String> emojis = holidayService.getJewishHolidaysWithEmoji(TEST_YEAR);

        assertNotNull(emojis);
        assertFalse(emojis.isEmpty());
    }

    // ========== CROSS-YEAR CONSISTENCY TESTS ==========

    @Test
    void testGetUSHolidays_DifferentYears_HaveCorrectDates() {
        Map<String, String> holidays2024 = holidayService.getUSHolidays(2024);
        Map<String, String> holidays2025 = holidayService.getUSHolidays(2025);
        Map<String, String> holidays2026 = holidayService.getUSHolidays(2026);

        // Fixed date holidays should be on same day
        assertEquals("Christmas Day", holidays2024.get(formatDate(2024, 12, 25)));
        assertEquals("Christmas Day", holidays2025.get(formatDate(2025, 12, 25)));
        assertEquals("Christmas Day", holidays2026.get(formatDate(2026, 12, 25)));

        // Each year should have 11 holidays
        assertEquals(11, holidays2024.size());
        assertEquals(11, holidays2025.size());
        assertEquals(11, holidays2026.size());
    }

    @Test
    void testGetUSHolidays_MovableHolidays_VaryByYear() {
        // Thanksgiving varies by year (4th Thursday in November)
        Map<String, String> holidays2024 = holidayService.getUSHolidays(2024);
        Map<String, String> holidays2025 = holidayService.getUSHolidays(2025);

        // 2024 Thanksgiving is November 28
        assertEquals("Thanksgiving", holidays2024.get(formatDate(2024, 11, 28)));
        // 2025 Thanksgiving is November 27
        assertEquals("Thanksgiving", holidays2025.get(formatDate(2025, 11, 27)));
    }

    // ========== HELPER METHODS ==========

    private String formatDate(int year, int month, int day) {
        return String.format(DATE_FORMAT, year, month, day);
    }
}
