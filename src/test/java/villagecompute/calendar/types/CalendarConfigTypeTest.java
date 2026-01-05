package villagecompute.calendar.types;

import static org.junit.jupiter.api.Assertions.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Tests for CalendarConfigType serialization and copy constructor. Ensures all fields are properly initialized and can
 * be serialized/deserialized via Jackson.
 */
class CalendarConfigTypeTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void defaultConstructor_SetsDefaultValues() {
        CalendarConfigType config = new CalendarConfigType();

        assertEquals(LocalDate.now().getYear(), config.year);
        assertEquals("default", config.theme);
        assertEquals("none", config.moonDisplayMode);
        assertFalse(config.showWeekNumbers);
        assertFalse(config.compactMode);
        assertTrue(config.showDayNames);
        assertTrue(config.showDayNumbers);
        assertTrue(config.showGrid);
        assertTrue(config.highlightWeekends);
        assertFalse(config.rotateMonthNames);
        assertEquals(0, config.latitude);
        assertEquals(0, config.longitude);
        assertEquals(LocalTime.of(20, 0), config.observationTime);
        assertEquals("America/New_York", config.timeZone);
        assertEquals(20, config.moonSize);
        assertEquals(25, config.moonOffsetX);
        assertEquals(36, config.moonOffsetY);
        assertEquals("#666666", config.moonBorderColor);
        assertEquals(1.5, config.moonBorderWidth);
        assertNull(config.yearColor);
        assertNull(config.monthColor);
        assertNull(config.dayTextColor);
        assertNull(config.dayNameColor);
        assertNotNull(config.gridLineColor);
        assertNull(config.weekendBgColor);
        assertEquals("#ff5252", config.holidayColor);
        assertNotNull(config.customDateColor);
        assertNotNull(config.moonDarkColor);
        assertNotNull(config.moonLightColor);
        assertEquals("bottom-left", config.emojiPosition);
        assertNotNull(config.customDates);
        assertTrue(config.customDates.isEmpty());
        assertNotNull(config.holidays);
        assertTrue(config.holidays.isEmpty());
        assertNotNull(config.holidaySets);
        assertTrue(config.holidaySets.isEmpty());
        assertEquals("large", config.eventDisplayMode);
        assertEquals("noto-color", config.emojiFont);
        assertEquals("en-US", config.locale);
        assertEquals(DayOfWeek.SUNDAY, config.firstDayOfWeek);
        assertEquals("grid", config.layoutStyle);
    }

    @Test
    void copyConstructor_CopiesAllFields() {
        CalendarConfigType original = new CalendarConfigType();
        original.year = 2025;
        original.theme = "dark";
        original.moonDisplayMode = "phases";
        original.showWeekNumbers = true;
        original.compactMode = true;
        original.showDayNames = false;
        original.showDayNumbers = false;
        original.showGrid = false;
        original.highlightWeekends = false;
        original.rotateMonthNames = true;
        original.latitude = 36.1627;
        original.longitude = -86.7816;
        original.observationTime = LocalTime.of(21, 30);
        original.timeZone = "America/Chicago";
        original.moonSize = 32;
        original.moonOffsetX = 40;
        original.moonOffsetY = 40;
        original.moonBorderColor = "#000000";
        original.moonBorderWidth = 1.0;
        original.yearColor = "#ffffff";
        original.monthColor = "#cccccc";
        original.dayTextColor = "#333333";
        original.dayNameColor = "#666666";
        original.gridLineColor = "#888888";
        original.weekendBgColor = "#f0f0f0";
        original.holidayColor = "#ff0000";
        original.customDateColor = "#00ff00";
        original.moonDarkColor = "#111111";
        original.moonLightColor = "#eeeeee";
        original.emojiPosition = "top-right";
        original.customDates.put(LocalDate.of(2025, 1, 1), new CustomDateEntryType("party", "New Year"));
        original.holidays.put(LocalDate.of(2025, 12, 25), new HolidayType("Christmas", "tree"));
        original.holidaySets.add("us-federal");
        original.eventDisplayMode = "small";
        original.emojiFont = "noto-mono";
        original.locale = "fr-FR";
        original.firstDayOfWeek = DayOfWeek.MONDAY;
        original.layoutStyle = "weekday-grid";

        CalendarConfigType copy = new CalendarConfigType(original);

        // Verify all scalar fields
        assertEquals(original.year, copy.year);
        assertEquals(original.theme, copy.theme);
        assertEquals(original.moonDisplayMode, copy.moonDisplayMode);
        assertEquals(original.showWeekNumbers, copy.showWeekNumbers);
        assertEquals(original.compactMode, copy.compactMode);
        assertEquals(original.showDayNames, copy.showDayNames);
        assertEquals(original.showDayNumbers, copy.showDayNumbers);
        assertEquals(original.showGrid, copy.showGrid);
        assertEquals(original.highlightWeekends, copy.highlightWeekends);
        assertEquals(original.rotateMonthNames, copy.rotateMonthNames);
        assertEquals(original.latitude, copy.latitude);
        assertEquals(original.longitude, copy.longitude);
        assertEquals(original.observationTime, copy.observationTime);
        assertEquals(original.timeZone, copy.timeZone);
        assertEquals(original.moonSize, copy.moonSize);
        assertEquals(original.moonOffsetX, copy.moonOffsetX);
        assertEquals(original.moonOffsetY, copy.moonOffsetY);
        assertEquals(original.moonBorderColor, copy.moonBorderColor);
        assertEquals(original.moonBorderWidth, copy.moonBorderWidth);
        assertEquals(original.yearColor, copy.yearColor);
        assertEquals(original.monthColor, copy.monthColor);
        assertEquals(original.dayTextColor, copy.dayTextColor);
        assertEquals(original.dayNameColor, copy.dayNameColor);
        assertEquals(original.gridLineColor, copy.gridLineColor);
        assertEquals(original.weekendBgColor, copy.weekendBgColor);
        assertEquals(original.holidayColor, copy.holidayColor);
        assertEquals(original.customDateColor, copy.customDateColor);
        assertEquals(original.moonDarkColor, copy.moonDarkColor);
        assertEquals(original.moonLightColor, copy.moonLightColor);
        assertEquals(original.emojiPosition, copy.emojiPosition);
        assertEquals(original.eventDisplayMode, copy.eventDisplayMode);
        assertEquals(original.emojiFont, copy.emojiFont);
        assertEquals(original.locale, copy.locale);
        assertEquals(original.firstDayOfWeek, copy.firstDayOfWeek);
        assertEquals(original.layoutStyle, copy.layoutStyle);

        // Verify collections are copied (not same reference)
        assertNotSame(original.customDates, copy.customDates);
        assertNotSame(original.holidays, copy.holidays);
        assertNotSame(original.holidaySets, copy.holidaySets);

        // Verify collection contents are equal
        assertEquals(original.customDates, copy.customDates);
        assertEquals(original.holidays, copy.holidays);
        assertEquals(original.holidaySets, copy.holidaySets);
    }

    @Test
    void copyConstructor_ModifyingCopyDoesNotAffectOriginal() {
        CalendarConfigType original = new CalendarConfigType();
        original.customDates.put(LocalDate.of(2025, 1, 1), new CustomDateEntryType("original"));
        original.holidays.put(LocalDate.of(2025, 12, 25), new HolidayType("Christmas", "tree"));

        CalendarConfigType copy = new CalendarConfigType(original);
        copy.customDates.put(LocalDate.of(2025, 1, 1), new CustomDateEntryType("modified"));
        copy.holidays.put(LocalDate.of(2025, 7, 4), new HolidayType("Independence Day", "fireworks"));

        assertEquals("original", original.customDates.get(LocalDate.of(2025, 1, 1)).emoji);
        assertEquals(1, original.holidays.size());
        assertEquals("Christmas", original.holidays.get(LocalDate.of(2025, 12, 25)).name);
    }

    @Test
    void jsonSerialization_RoundTrip() throws Exception {
        CalendarConfigType original = new CalendarConfigType();
        original.year = 2025;
        original.theme = "dark";
        original.moonDisplayMode = "full-only";
        original.latitude = 40.7128;
        original.longitude = -74.0060;
        original.customDates.put(LocalDate.of(2025, 1, 1), new CustomDateEntryType("fireworks", "New Year"));
        original.holidays.put(LocalDate.of(2025, 7, 4), new HolidayType("Independence Day", "flag"));

        String json = objectMapper.writeValueAsString(original);
        CalendarConfigType restored = objectMapper.readValue(json, CalendarConfigType.class);

        assertEquals(original.year, restored.year);
        assertEquals(original.theme, restored.theme);
        assertEquals(original.moonDisplayMode, restored.moonDisplayMode);
        assertEquals(original.latitude, restored.latitude);
        assertEquals(original.longitude, restored.longitude);
        assertEquals(original.customDates, restored.customDates);
        assertEquals(original.holidays, restored.holidays);
    }

    @Test
    void jsonSerialization_IgnoresUnknownProperties() throws Exception {
        String json = """
                {
                    "year": 2025,
                    "unknownField": "should be ignored",
                    "theme": "light"
                }
                """;

        CalendarConfigType config = objectMapper.readValue(json, CalendarConfigType.class);

        assertEquals(2025, config.year);
        assertEquals("light", config.theme);
    }

    @Test
    void jsonSerialization_MoonDisplayModePropertyMapping() throws Exception {
        String json = """
                {
                    "moonDisplayMode": "phases"
                }
                """;

        CalendarConfigType config = objectMapper.readValue(json, CalendarConfigType.class);
        assertEquals("phases", config.moonDisplayMode);

        String outputJson = objectMapper.writeValueAsString(config);
        assertTrue(outputJson.contains("\"moonDisplayMode\""));
    }

    @Test
    void jsonSerialization_ObservationTimePropertyMapping() throws Exception {
        CalendarConfigType config = new CalendarConfigType();
        config.observationTime = LocalTime.of(22, 30);

        String json = objectMapper.writeValueAsString(config);
        assertTrue(json.contains("observationTime"));
    }

    @Test
    void jsonSerialization_CustomDatesPropertyMapping() throws Exception {
        String json = """
                {
                    "customDates": {"2025-01-01": {"emoji": "party", "title": "New Year"}}
                }
                """;

        CalendarConfigType config = objectMapper.readValue(json, CalendarConfigType.class);
        LocalDate newYearDate = LocalDate.of(2025, 1, 1);
        assertEquals("party", config.customDates.get(newYearDate).emoji);
        assertEquals("New Year", config.customDates.get(newYearDate).title);
    }

    @Test
    void jsonSerialization_AllCollectionsMapping() throws Exception {
        String json = """
                {
                    "customDates": {"2025-01-01": {"emoji": "party", "title": "New Year"}},
                    "holidays": {"2025-12-25": {"name": "Christmas", "emoji": "tree"}},
                    "holidaySets": ["us-federal", "us-observances"]
                }
                """;

        CalendarConfigType config = objectMapper.readValue(json, CalendarConfigType.class);

        LocalDate newYearDate = LocalDate.of(2025, 1, 1);
        assertEquals("New Year", config.customDates.get(newYearDate).title);
        LocalDate christmasDate = LocalDate.of(2025, 12, 25);
        assertNotNull(config.holidays.get(christmasDate));
        assertEquals("tree", config.holidays.get(christmasDate).emoji);
        assertEquals("Christmas", config.holidays.get(christmasDate).name);
        assertEquals(2, config.holidaySets.size());
        assertTrue(config.holidaySets.contains("us-federal"));
    }
}
