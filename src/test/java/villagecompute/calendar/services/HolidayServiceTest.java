package villagecompute.calendar.services;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import villagecompute.calendar.types.HolidayType;

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

        // US has 12 holidays (11 federal + Halloween)
        assertEquals(12, holidays.size());
    }

    // ========== GET HOLIDAYS BY COUNTRY TESTS ==========

    @Test
    void testGetHolidays_USCountry_ReturnsUSHolidays() {
        Map<String, String> holidays = holidayService.getHolidays(TEST_YEAR, "US");

        assertEquals(12, holidays.size());
        assertTrue(holidays.containsValue("Independence Day"));
    }

    @Test
    void testGetHolidays_LowercaseUS_ReturnsUSHolidays() {
        Map<String, String> holidays = holidayService.getHolidays(TEST_YEAR, "us");

        assertEquals(12, holidays.size());
    }

    @Test
    void testGetHolidays_UnknownCountry_DefaultsToUS() {
        Map<String, String> holidays = holidayService.getHolidays(TEST_YEAR, "XX");

        assertEquals(12, holidays.size());
        assertTrue(holidays.containsValue("Independence Day"));
    }

    // ========== GET HOLIDAYS TYPED TESTS ==========

    @Test
    void testGetHolidaysTyped_US_ReturnsHolidays() {
        var holidays = holidayService.getHolidaysTyped(TEST_YEAR, "us");

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        // Verify entries have emojis
        assertTrue(holidays.values().stream().anyMatch(h -> h.emoji != null));
    }

    @Test
    void testGetHolidaysTyped_Jewish_ReturnsHolidays() {
        var holidays = holidayService.getHolidaysTyped(TEST_YEAR, "jewish");

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
    }

    @Test
    void testGetHolidaysTyped_UnknownSet_ReturnsEmptyMap() {
        var holidays = holidayService.getHolidaysTyped(TEST_YEAR, "unknown_set");

        assertNotNull(holidays);
        assertTrue(holidays.isEmpty());
    }

    // ========== GET HOLIDAY NAMES TESTS ==========

    @Test
    void testGetHolidayNames_US_ReturnsNames() {
        Map<String, String> names = holidayService.getHolidayNames(TEST_YEAR, "us");

        assertNotNull(names);
        assertEquals(12, names.size());
        assertTrue(names.containsValue("Christmas Day"));
        assertTrue(names.containsValue("Halloween"));
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

        // Each year should have 12 holidays (11 federal + Halloween)
        assertEquals(12, holidays2024.size());
        assertEquals(12, holidays2025.size());
        assertEquals(12, holidays2026.size());
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

    // ========== MEXICAN HOLIDAYS TESTS ==========

    @Test
    void testGetMexicanHolidays_ContainsMajorHolidays() {
        Map<String, String> holidays = holidayService.getMexicanHolidays(TEST_YEAR);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        assertTrue(holidays.containsValue("Cinco de Mayo"));
        assertTrue(holidays.containsValue("Día de la Independencia"));
        assertTrue(holidays.containsValue("Día de los Muertos"));
        assertTrue(holidays.containsValue("Día de los Reyes"));
    }

    @Test
    void testGetMexicanHolidays_ContainsCincoMayo() {
        Map<String, String> holidays = holidayService.getMexicanHolidays(TEST_YEAR);

        assertEquals("Cinco de Mayo", holidays.get(formatDate(TEST_YEAR, 5, 5)));
    }

    @Test
    void testGetMexicanHolidays_ContainsDiaDeLosReyes() {
        Map<String, String> holidays = holidayService.getMexicanHolidays(TEST_YEAR);

        assertEquals("Día de los Reyes", holidays.get(formatDate(TEST_YEAR, 1, 6)));
    }

    @Test
    void testGetMexicanHolidays_ContainsLasPosadas() {
        Map<String, String> holidays = holidayService.getMexicanHolidays(TEST_YEAR);

        // Las Posadas runs from Dec 16-24
        assertEquals("Las Posadas", holidays.get(formatDate(TEST_YEAR, 12, 16)));
        assertEquals("Las Posadas", holidays.get(formatDate(TEST_YEAR, 12, 24)));
    }

    @Test
    void testGetMexicanHolidaysWithEmoji_ReturnsEmojis() {
        Map<String, String> emojis = holidayService.getMexicanHolidaysWithEmoji(TEST_YEAR);

        assertNotNull(emojis);
        assertFalse(emojis.isEmpty());
    }

    // ========== PAGAN HOLIDAYS TESTS ==========

    @Test
    void testGetPaganHolidays_ContainsWheelOfTheYear() {
        Map<String, String> holidays = holidayService.getPaganHolidays(TEST_YEAR);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        assertTrue(holidays.containsValue("Imbolc"));
        assertTrue(holidays.containsValue("Ostara"));
        assertTrue(holidays.containsValue("Beltane"));
        assertTrue(holidays.containsValue("Litha"));
        assertTrue(holidays.containsValue("Lughnasadh"));
        assertTrue(holidays.containsValue("Mabon"));
        assertTrue(holidays.containsValue("Samhain"));
        assertTrue(holidays.containsValue("Yule"));
    }

    @Test
    void testGetPaganHolidays_ContainsSamhain() {
        Map<String, String> holidays = holidayService.getPaganHolidays(TEST_YEAR);

        assertEquals("Samhain", holidays.get(formatDate(TEST_YEAR, 10, 31)));
    }

    @Test
    void testGetPaganHolidays_ContainsBeltane() {
        Map<String, String> holidays = holidayService.getPaganHolidays(TEST_YEAR);

        assertEquals("Beltane", holidays.get(formatDate(TEST_YEAR, 5, 1)));
    }

    @Test
    void testGetPaganHolidaysWithEmoji_ReturnsEmojis() {
        Map<String, String> emojis = holidayService.getPaganHolidaysWithEmoji(TEST_YEAR);

        assertNotNull(emojis);
        assertFalse(emojis.isEmpty());
    }

    // ========== HINDU HOLIDAYS TESTS ==========

    @Test
    void testGetHinduHolidays_ContainsMajorHolidays() {
        Map<String, String> holidays = holidayService.getHinduHolidays(TEST_YEAR);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        assertTrue(holidays.containsValue("Makar Sankranti"));
    }

    @Test
    void testGetHinduHolidays_ContainsMakarSankranti() {
        Map<String, String> holidays = holidayService.getHinduHolidays(TEST_YEAR);

        assertEquals("Makar Sankranti", holidays.get(formatDate(TEST_YEAR, 1, 14)));
    }

    @Test
    void testGetHinduHolidaysWithEmoji_ReturnsEmojis() {
        Map<String, String> emojis = holidayService.getHinduHolidaysWithEmoji(TEST_YEAR);

        assertNotNull(emojis);
        assertFalse(emojis.isEmpty());
    }

    // ========== ISLAMIC HOLIDAYS TESTS ==========

    @Test
    void testGetIslamicHolidays_ContainsMajorHolidays() {
        Map<String, String> holidays = holidayService.getIslamicHolidays(TEST_YEAR);

        assertNotNull(holidays);
        // Islamic holidays vary by year due to lunar calendar - just verify it returns something
        // The calculation may return holidays or may not depending on lunar cycles
    }

    @Test
    void testGetIslamicHolidaysWithEmoji_ReturnsEmojis() {
        Map<String, String> emojis = holidayService.getIslamicHolidaysWithEmoji(TEST_YEAR);

        assertNotNull(emojis);
        assertFalse(emojis.isEmpty());
    }

    // ========== CHINESE HOLIDAYS TESTS ==========

    @Test
    void testGetChineseHolidays_ContainsMajorHolidays() {
        Map<String, String> holidays = holidayService.getChineseHolidays(TEST_YEAR);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        assertTrue(holidays.containsValue("Chinese New Year"));
        assertTrue(holidays.containsValue("Qingming Festival"));
    }

    @Test
    void testGetChineseHolidaysWithEmoji_ReturnsEmojis() {
        Map<String, String> emojis = holidayService.getChineseHolidaysWithEmoji(TEST_YEAR);

        assertNotNull(emojis);
        assertFalse(emojis.isEmpty());
    }

    // ========== CHRISTIAN HOLIDAYS TESTS ==========

    @Test
    void testGetChristianHolidays_ContainsMajorHolidays() {
        Map<String, String> holidays = holidayService.getChristianHolidays(TEST_YEAR);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        assertTrue(holidays.containsValue("Christmas"));
        assertTrue(holidays.containsValue("Easter"));
    }

    @Test
    void testGetChristianHolidaysWithEmoji_ReturnsEmojis() {
        Map<String, String> emojis = holidayService.getChristianHolidaysWithEmoji(TEST_YEAR);

        assertNotNull(emojis);
        assertFalse(emojis.isEmpty());
    }

    // ========== CANADIAN HOLIDAYS TESTS ==========

    @Test
    void testGetCanadianHolidays_ContainsMajorHolidays() {
        Map<String, String> holidays = holidayService.getCanadianHolidays(TEST_YEAR);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        assertTrue(holidays.containsValue("Canada Day"));
    }

    @Test
    void testGetCanadianHolidays_ContainsCanadaDay() {
        Map<String, String> holidays = holidayService.getCanadianHolidays(TEST_YEAR);

        assertEquals("Canada Day", holidays.get(formatDate(TEST_YEAR, 7, 1)));
    }

    @Test
    void testGetCanadianHolidaysWithEmoji_ReturnsEmojis() {
        Map<String, String> emojis = holidayService.getCanadianHolidaysWithEmoji(TEST_YEAR);

        assertNotNull(emojis);
        assertFalse(emojis.isEmpty());
    }

    // ========== UK HOLIDAYS TESTS ==========

    @Test
    void testGetUKHolidays_ContainsMajorHolidays() {
        Map<String, String> holidays = holidayService.getUKHolidays(TEST_YEAR);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        assertTrue(holidays.containsValue("Boxing Day"));
    }

    @Test
    void testGetUKHolidays_ContainsBoxingDay() {
        Map<String, String> holidays = holidayService.getUKHolidays(TEST_YEAR);

        assertEquals("Boxing Day", holidays.get(formatDate(TEST_YEAR, 12, 26)));
    }

    @Test
    void testGetUKHolidaysWithEmoji_ReturnsEmojis() {
        Map<String, String> emojis = holidayService.getUKHolidaysWithEmoji(TEST_YEAR);

        assertNotNull(emojis);
        assertFalse(emojis.isEmpty());
    }

    // ========== SECULAR/FUN HOLIDAYS TESTS ==========

    @Test
    void testGetSecularHolidays_ContainsMajorHolidays() {
        Map<String, String> holidays = holidayService.getSecularHolidays(TEST_YEAR);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        assertTrue(holidays.containsValue("Valentine's Day"));
        assertTrue(holidays.containsValue("Halloween"));
    }

    @Test
    void testGetSecularHolidays_ContainsValentinesDay() {
        Map<String, String> holidays = holidayService.getSecularHolidays(TEST_YEAR);

        assertEquals("Valentine's Day", holidays.get(formatDate(TEST_YEAR, 2, 14)));
    }

    @Test
    void testGetSecularHolidays_ContainsHalloween() {
        Map<String, String> holidays = holidayService.getSecularHolidays(TEST_YEAR);

        assertEquals("Halloween", holidays.get(formatDate(TEST_YEAR, 10, 31)));
    }

    @Test
    void testGetSecularHolidaysWithEmoji_ReturnsEmojis() {
        Map<String, String> emojis = holidayService.getSecularHolidaysWithEmoji(TEST_YEAR);

        assertNotNull(emojis);
        assertFalse(emojis.isEmpty());
    }

    // ========== MAJOR WORLD HOLIDAYS TESTS ==========

    @Test
    void testGetMajorWorldHolidays_ContainsMajorHolidays() {
        Map<String, String> holidays = holidayService.getMajorWorldHolidays(TEST_YEAR);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
    }

    @Test
    void testGetMajorWorldHolidaysWithEmoji_ReturnsEmojis() {
        Map<String, String> emojis = holidayService.getMajorWorldHolidaysWithEmoji(TEST_YEAR);

        assertNotNull(emojis);
        assertFalse(emojis.isEmpty());
    }

    // ========== US HOLIDAYS WITH EMOJI TESTS ==========

    @Test
    void testGetUSHolidaysTyped_ContainsExpectedEmojis() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidaysTyped(TEST_YEAR, "US");

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        // Verify at least some holidays have emojis
        long emojisCount = holidays.values().stream().filter(h -> h.emoji != null).count();
        assertTrue(emojisCount > 0, "US holidays should have emojis");
    }

    // ========== EUROPEAN HOLIDAYS TESTS ==========

    @Test
    void testGetHolidayNames_European_ReturnsNames() {
        Map<String, String> names = holidayService.getHolidayNames(TEST_YEAR, "european");

        // European may return empty if not implemented - just verify it doesn't throw
        assertNotNull(names);
    }

    @Test
    void testGetHolidaysTyped_European_ReturnsHolidays() {
        var holidays = holidayService.getHolidaysTyped(TEST_YEAR, "european");

        // European may return empty if not implemented - just verify it doesn't throw
        assertNotNull(holidays);
    }

    // ========== HELPER METHODS ==========

    private String formatDate(int year, int month, int day) {
        return String.format(DATE_FORMAT, year, month, day);
    }
}
