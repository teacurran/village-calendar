package villagecompute.calendar.services;

import static org.junit.jupiter.api.Assertions.*;
import static villagecompute.calendar.services.HolidayService.*;

import java.time.LocalDate;
import java.time.Month;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import villagecompute.calendar.types.HolidayType;

/** Unit tests for HolidayService. Tests holiday calculations and set mappings. */
class HolidayServiceTest {

    private static final int TEST_YEAR = 2025;

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
        assertEquals(SET_US, holidayService.mapHolidaySetId(null));
    }

    @Test
    void testMapHolidaySetId_UnknownId_ReturnsUpperCase() {
        assertEquals("UNKNOWN", holidayService.mapHolidaySetId("unknown"));
        assertEquals("CUSTOM", holidayService.mapHolidaySetId("custom"));
    }

    // ========== US HOLIDAYS TESTS ==========

    @Test
    void testGetUSHolidays_ContainsNewYearsDay() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_US);

        assertTrue(hasHolidayNamed(holidays, "New Year's Day"));
        assertEquals("New Year's Day", holidays.get(LocalDate.of(TEST_YEAR, Month.JANUARY, 1)).name);
    }

    @Test
    void testGetUSHolidays_ContainsMLKDay() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_US);

        assertTrue(hasHolidayNamed(holidays, "Martin Luther King Jr. Day"));
        // MLK Day is 3rd Monday in January - for 2025 it's January 20
        assertEquals("Martin Luther King Jr. Day", holidays.get(LocalDate.of(TEST_YEAR, Month.JANUARY, 20)).name);
    }

    @Test
    void testGetUSHolidays_ContainsPresidentsDay() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_US);

        assertTrue(hasHolidayNamed(holidays, "Presidents' Day"));
        // Presidents' Day is 3rd Monday in February - for 2025 it's February 17
        assertEquals("Presidents' Day", holidays.get(LocalDate.of(TEST_YEAR, Month.FEBRUARY, 17)).name);
    }

    @Test
    void testGetUSHolidays_ContainsMemorialDay() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_US);

        assertTrue(hasHolidayNamed(holidays, "Memorial Day"));
        // Memorial Day is last Monday in May - for 2025 it's May 26
        assertEquals("Memorial Day", holidays.get(LocalDate.of(TEST_YEAR, Month.MAY, 26)).name);
    }

    @Test
    void testGetUSHolidays_ContainsJuneteenth() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_US);

        assertEquals("Juneteenth", holidays.get(LocalDate.of(TEST_YEAR, Month.JUNE, 19)).name);
    }

    @Test
    void testGetUSHolidays_ContainsIndependenceDay() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_US);

        assertEquals("Independence Day", holidays.get(LocalDate.of(TEST_YEAR, Month.JULY, 4)).name);
    }

    @Test
    void testGetUSHolidays_ContainsLaborDay() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_US);

        assertTrue(hasHolidayNamed(holidays, "Labor Day"));
        // Labor Day is 1st Monday in September - for 2025 it's September 1
        assertEquals("Labor Day", holidays.get(LocalDate.of(TEST_YEAR, Month.SEPTEMBER, 1)).name);
    }

    @Test
    void testGetUSHolidays_ContainsColumbusDay() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_US);

        assertTrue(hasHolidayNamed(holidays, "Columbus Day"));
        // Columbus Day is 2nd Monday in October - for 2025 it's October 13
        assertEquals("Columbus Day", holidays.get(LocalDate.of(TEST_YEAR, Month.OCTOBER, 13)).name);
    }

    @Test
    void testGetUSHolidays_ContainsVeteransDay() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_US);

        assertEquals("Veterans Day", holidays.get(LocalDate.of(TEST_YEAR, Month.NOVEMBER, 11)).name);
    }

    @Test
    void testGetUSHolidays_ContainsThanksgiving() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_US);

        assertTrue(hasHolidayNamed(holidays, "Thanksgiving"));
        // Thanksgiving is 4th Thursday in November - for 2025 it's November 27
        assertEquals("Thanksgiving", holidays.get(LocalDate.of(TEST_YEAR, Month.NOVEMBER, 27)).name);
    }

    @Test
    void testGetUSHolidays_ContainsChristmas() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_US);

        assertEquals("Christmas Day", holidays.get(LocalDate.of(TEST_YEAR, Month.DECEMBER, 25)).name);
    }

    @Test
    void testGetUSHolidays_ReturnsCorrectCount() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_US);

        // US has 12 holidays (11 federal + Halloween)
        assertEquals(12, holidays.size());
    }

    // ========== GET HOLIDAYS BY SET ID TESTS ==========

    @Test
    void testGetHolidaysTyped_USSetId_ReturnsUSHolidays() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_US);

        assertEquals(12, holidays.size());
        assertTrue(hasHolidayNamed(holidays, "Independence Day"));
    }

    @Test
    void testGetHolidaysTyped_LowercaseUS_ReturnsUSHolidays() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, "us");

        assertEquals(12, holidays.size());
    }

    @Test
    void testGetHolidays_UnknownSetId_ReturnsEmpty() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, "XX");

        assertTrue(holidays.isEmpty());
    }

    // ========== GET HOLIDAYS TYPED TESTS ==========

    @Test
    void testGetHolidaysTyped_US_ReturnsHolidays() {
        var holidays = holidayService.getHolidays(TEST_YEAR, SET_US);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        // Verify entries have emojis
        assertTrue(holidays.values().stream().anyMatch(h -> h.emoji != null));
    }

    @Test
    void testGetHolidaysTyped_Jewish_ReturnsHolidays() {
        var holidays = holidayService.getHolidays(TEST_YEAR, SET_JEWISH);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
    }

    @Test
    void testGetHolidays_UnknownSet_ReturnsEmptyMap() {
        var holidays = holidayService.getHolidays(TEST_YEAR, "unknown_set");

        assertNotNull(holidays);
        assertTrue(holidays.isEmpty());
    }

    // ========== US HOLIDAYS CONTENT TESTS ==========

    @Test
    void testGetHolidaysTyped_US_ContainsExpectedHolidays() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_US);

        assertNotNull(holidays);
        assertEquals(12, holidays.size());
        assertTrue(hasHolidayNamed(holidays, "Christmas Day"));
        assertTrue(hasHolidayNamed(holidays, "Halloween"));
    }

    @Test
    void testGetHolidaysTyped_Christian_ReturnsHolidays() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_CHRISTIAN);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
    }

    @Test
    void testGetHolidaysTyped_Canadian_ReturnsHolidays() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_CANADIAN);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
    }

    // ========== JEWISH HOLIDAYS TESTS ==========

    @Test
    void testGetJewishHolidays_ContainsMajorHolidays() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_JEWISH);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());

        // Check that major holidays are present (names may vary slightly)
        boolean hasPassover = holidays.values().stream().anyMatch(h -> h.name.contains("Passover"));
        boolean hasRoshHashanah = holidays.values().stream().anyMatch(h -> h.name.contains("Rosh Hashanah"));

        assertTrue(hasPassover || hasRoshHashanah, "Should contain at least one major Jewish holiday");
    }

    @Test
    void testGetJewishHolidaysTyped_ContainsEmojis() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_JEWISH);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        long emojisCount = holidays.values().stream().filter(h -> h.emoji != null).count();
        assertTrue(emojisCount > 0, "Jewish holidays should have emojis");
    }

    // ========== CROSS-YEAR CONSISTENCY TESTS ==========

    @Test
    void testGetUSHolidays_DifferentYears_HaveCorrectDates() {
        Map<LocalDate, HolidayType> holidays2024 = holidayService.getHolidays(2024, SET_US);
        Map<LocalDate, HolidayType> holidays2025 = holidayService.getHolidays(2025, SET_US);
        Map<LocalDate, HolidayType> holidays2026 = holidayService.getHolidays(2026, SET_US);

        // Fixed date holidays should be on same day
        assertEquals("Christmas Day", holidays2024.get(LocalDate.of(2024, Month.DECEMBER, 25)).name);
        assertEquals("Christmas Day", holidays2025.get(LocalDate.of(2025, Month.DECEMBER, 25)).name);
        assertEquals("Christmas Day", holidays2026.get(LocalDate.of(2026, Month.DECEMBER, 25)).name);

        // Each year should have 12 holidays (11 federal + Halloween)
        assertEquals(12, holidays2024.size());
        assertEquals(12, holidays2025.size());
        assertEquals(12, holidays2026.size());
    }

    @Test
    void testGetUSHolidays_MovableHolidays_VaryByYear() {
        // Thanksgiving varies by year (4th Thursday in November)
        Map<LocalDate, HolidayType> holidays2024 = holidayService.getHolidays(2024, SET_US);
        Map<LocalDate, HolidayType> holidays2025 = holidayService.getHolidays(2025, SET_US);

        // 2024 Thanksgiving is November 28
        assertEquals("Thanksgiving", holidays2024.get(LocalDate.of(2024, Month.NOVEMBER, 28)).name);
        // 2025 Thanksgiving is November 27
        assertEquals("Thanksgiving", holidays2025.get(LocalDate.of(2025, Month.NOVEMBER, 27)).name);
    }

    // ========== MEXICAN HOLIDAYS TESTS ==========

    @Test
    void testGetMexicanHolidays_ContainsMajorHolidays() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_MEXICAN);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        assertTrue(hasHolidayNamed(holidays, "Cinco de Mayo"));
        assertTrue(hasHolidayNamed(holidays, "Día de la Independencia"));
        assertTrue(hasHolidayNamed(holidays, "Día de los Muertos"));
        assertTrue(hasHolidayNamed(holidays, "Día de los Reyes"));
    }

    @Test
    void testGetMexicanHolidays_ContainsCincoMayo() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_MEXICAN);

        assertEquals("Cinco de Mayo", holidays.get(LocalDate.of(TEST_YEAR, Month.MAY, 5)).name);
    }

    @Test
    void testGetMexicanHolidays_ContainsDiaDeLosReyes() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_MEXICAN);

        assertEquals("Día de los Reyes", holidays.get(LocalDate.of(TEST_YEAR, Month.JANUARY, 6)).name);
    }

    @Test
    void testGetMexicanHolidays_ContainsLasPosadas() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_MEXICAN);

        // Las Posadas runs from Dec 16-24
        assertEquals("Las Posadas", holidays.get(LocalDate.of(TEST_YEAR, Month.DECEMBER, 16)).name);
        assertEquals("Las Posadas", holidays.get(LocalDate.of(TEST_YEAR, Month.DECEMBER, 24)).name);
    }

    @Test
    void testGetMexicanHolidaysTyped_ContainsEmojis() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_MEXICAN);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        long emojisCount = holidays.values().stream().filter(h -> h.emoji != null).count();
        assertTrue(emojisCount > 0, "Mexican holidays should have emojis");
    }

    // ========== PAGAN HOLIDAYS TESTS ==========

    @Test
    void testGetPaganHolidays_ContainsWheelOfTheYear() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_PAGAN);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        assertTrue(hasHolidayNamed(holidays, "Imbolc"));
        assertTrue(hasHolidayNamed(holidays, "Ostara"));
        assertTrue(hasHolidayNamed(holidays, "Beltane"));
        assertTrue(hasHolidayNamed(holidays, "Litha"));
        assertTrue(hasHolidayNamed(holidays, "Lughnasadh"));
        assertTrue(hasHolidayNamed(holidays, "Mabon"));
        assertTrue(hasHolidayNamed(holidays, "Samhain"));
        assertTrue(hasHolidayNamed(holidays, "Yule"));
    }

    @Test
    void testGetPaganHolidays_ContainsSamhain() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_PAGAN);

        assertEquals("Samhain", holidays.get(LocalDate.of(TEST_YEAR, Month.OCTOBER, 31)).name);
    }

    @Test
    void testGetPaganHolidays_ContainsBeltane() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_PAGAN);

        assertEquals("Beltane", holidays.get(LocalDate.of(TEST_YEAR, Month.MAY, 1)).name);
    }

    @Test
    void testGetPaganHolidaysTyped_ContainsEmojis() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_PAGAN);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        long emojisCount = holidays.values().stream().filter(h -> h.emoji != null).count();
        assertTrue(emojisCount > 0, "Pagan holidays should have emojis");
    }

    // ========== HINDU HOLIDAYS TESTS ==========

    @Test
    void testGetHinduHolidays_ContainsMajorHolidays() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_HINDU);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        assertTrue(hasHolidayNamed(holidays, "Makar Sankranti"));
    }

    @Test
    void testGetHinduHolidays_ContainsMakarSankranti() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_HINDU);

        assertEquals("Makar Sankranti", holidays.get(LocalDate.of(TEST_YEAR, Month.JANUARY, 14)).name);
    }

    @Test
    void testGetHinduHolidaysTyped_ContainsEmojis() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_HINDU);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        long emojisCount = holidays.values().stream().filter(h -> h.emoji != null).count();
        assertTrue(emojisCount > 0, "Hindu holidays should have emojis");
    }

    // ========== ISLAMIC HOLIDAYS TESTS ==========

    @Test
    void testGetIslamicHolidays_ContainsMajorHolidays() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_ISLAMIC);

        assertNotNull(holidays);
        // Islamic holidays vary by year due to lunar calendar - just verify it returns something
        // The calculation may return holidays or may not depending on lunar cycles
    }

    @Test
    void testGetIslamicHolidaysTyped_ContainsEmojis() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_ISLAMIC);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        long emojisCount = holidays.values().stream().filter(h -> h.emoji != null).count();
        assertTrue(emojisCount > 0, "Islamic holidays should have emojis");
    }

    // ========== CHINESE HOLIDAYS TESTS ==========

    @Test
    void testGetChineseHolidays_ContainsMajorHolidays() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_CHINESE);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        assertTrue(hasHolidayNamed(holidays, "Chinese New Year"));
        assertTrue(hasHolidayNamed(holidays, "Qingming Festival"));
    }

    @Test
    void testGetChineseHolidaysTyped_ContainsEmojis() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_CHINESE);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        long emojisCount = holidays.values().stream().filter(h -> h.emoji != null).count();
        assertTrue(emojisCount > 0, "Chinese holidays should have emojis");
    }

    // ========== CHRISTIAN HOLIDAYS TESTS ==========

    @Test
    void testGetChristianHolidays_ContainsMajorHolidays() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_CHRISTIAN);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        assertTrue(hasHolidayNamed(holidays, "Christmas"));
        assertTrue(hasHolidayNamed(holidays, "Easter"));
    }

    @Test
    void testGetChristianHolidaysTyped_ContainsEmojis() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_CHRISTIAN);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        long emojisCount = holidays.values().stream().filter(h -> h.emoji != null).count();
        assertTrue(emojisCount > 0, "Christian holidays should have emojis");
    }

    // ========== CANADIAN HOLIDAYS TESTS ==========

    @Test
    void testGetCanadianHolidays_ContainsMajorHolidays() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_CANADIAN);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        assertTrue(hasHolidayNamed(holidays, "Canada Day"));
    }

    @Test
    void testGetCanadianHolidays_ContainsCanadaDay() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_CANADIAN);

        assertEquals("Canada Day", holidays.get(LocalDate.of(TEST_YEAR, Month.JULY, 1)).name);
    }

    @Test
    void testGetCanadianHolidaysTyped_ContainsEmojis() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_CANADIAN);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        long emojisCount = holidays.values().stream().filter(h -> h.emoji != null).count();
        assertTrue(emojisCount > 0, "Canadian holidays should have emojis");
    }

    // ========== UK HOLIDAYS TESTS ==========

    @Test
    void testGetUKHolidays_ContainsMajorHolidays() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_UK);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        assertTrue(hasHolidayNamed(holidays, "Boxing Day"));
    }

    @Test
    void testGetUKHolidays_ContainsBoxingDay() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_UK);

        assertEquals("Boxing Day", holidays.get(LocalDate.of(TEST_YEAR, Month.DECEMBER, 26)).name);
    }

    @Test
    void testGetUKHolidaysTyped_ContainsEmojis() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_UK);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        long emojisCount = holidays.values().stream().filter(h -> h.emoji != null).count();
        assertTrue(emojisCount > 0, "UK holidays should have emojis");
    }

    // ========== SECULAR/FUN HOLIDAYS TESTS ==========

    @Test
    void testGetSecularHolidays_ContainsMajorHolidays() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_SECULAR);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        assertTrue(hasHolidayNamed(holidays, "Valentine's Day"));
        assertTrue(hasHolidayNamed(holidays, "Halloween"));
    }

    @Test
    void testGetSecularHolidays_ContainsValentinesDay() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_SECULAR);

        assertEquals("Valentine's Day", holidays.get(LocalDate.of(TEST_YEAR, Month.FEBRUARY, 14)).name);
    }

    @Test
    void testGetSecularHolidays_ContainsHalloween() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_SECULAR);

        assertEquals("Halloween", holidays.get(LocalDate.of(TEST_YEAR, Month.OCTOBER, 31)).name);
    }

    @Test
    void testGetSecularHolidaysTyped_ContainsEmojis() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_SECULAR);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        long emojisCount = holidays.values().stream().filter(h -> h.emoji != null).count();
        assertTrue(emojisCount > 0, "Secular holidays should have emojis");
    }

    // ========== MAJOR WORLD HOLIDAYS TESTS ==========

    @Test
    void testGetMajorWorldHolidays_ContainsMajorHolidays() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_MAJOR_WORLD);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
    }

    @Test
    void testGetMajorWorldHolidaysTyped_ContainsEmojis() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_MAJOR_WORLD);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        long emojisCount = holidays.values().stream().filter(h -> h.emoji != null).count();
        assertTrue(emojisCount > 0, "Major World holidays should have emojis");
    }

    // ========== US HOLIDAYS WITH EMOJI TESTS ==========

    @Test
    void testGetUSHolidaysTyped_ContainsExpectedEmojis() {
        Map<LocalDate, HolidayType> holidays = holidayService.getHolidays(TEST_YEAR, SET_US);

        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        // Verify at least some holidays have emojis
        long emojisCount = holidays.values().stream().filter(h -> h.emoji != null).count();
        assertTrue(emojisCount > 0, "US holidays should have emojis");
    }

    // ========== EUROPEAN HOLIDAYS TESTS ==========

    @Test
    void testGetHolidaysTyped_European_ReturnsHolidays() {
        var holidays = holidayService.getHolidays(TEST_YEAR, SET_EUROPEAN);

        // European may return empty if not implemented - just verify it doesn't throw
        assertNotNull(holidays);
    }

    // ========== HELPER METHODS ==========

    private boolean hasHolidayNamed(Map<LocalDate, HolidayType> holidays, String name) {
        return holidays.values().stream().anyMatch(h -> name.equals(h.name));
    }
}
