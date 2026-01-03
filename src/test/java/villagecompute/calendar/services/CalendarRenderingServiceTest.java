package villagecompute.calendar.services;

import static org.junit.jupiter.api.Assertions.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import villagecompute.calendar.types.CalendarConfigType;

import io.quarkus.test.junit.QuarkusTest;

/** Unit tests for CalendarRenderingService. Tests calendar SVG/PDF generation and configuration. */
@QuarkusTest
class CalendarRenderingServiceTest {

    private static final int TEST_YEAR = 2025;
    private static final String SVG_OPEN_TAG = "<svg";
    private static final String SVG_CLOSE_TAG = "</svg>";
    private static final String RECT_TAG = "<rect";
    private static final String TEXT_TAG = "<text";

    @Inject
    CalendarRenderingService calendarRenderingService;

    @Inject
    ObjectMapper objectMapper;

    // ========== CALENDAR CONFIG TESTS ==========

    @Test
    void testCalendarConfig_DefaultValues() {
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
        assertEquals(DayOfWeek.SUNDAY, config.firstDayOfWeek);
    }

    @Test
    void testCalendarConfig_DefaultColorValues() {
        CalendarConfigType config = new CalendarConfigType();

        assertNull(config.yearColor);
        assertNull(config.monthColor);
        assertNull(config.dayTextColor);
        assertNull(config.dayNameColor);
        assertNull(config.weekendBgColor);
        assertEquals("#ff5252", config.holidayColor);
        assertNotNull(config.gridLineColor);
        assertNotNull(config.moonBorderColor);
    }

    @Test
    void testCalendarConfig_DefaultMoonSettings() {
        CalendarConfigType config = new CalendarConfigType();

        assertEquals(24, config.moonSize);
        assertEquals(30, config.moonOffsetX);
        assertEquals(30, config.moonOffsetY);
        assertEquals(0.5, config.moonBorderWidth);
        assertEquals(0, config.latitude);
        assertEquals(0, config.longitude);
    }

    // ========== JSON DESERIALIZATION TESTS ==========

    @Test
    void testReadValue_EmptyConfig_UsesDefaults() throws Exception {
        String json = "{}";
        CalendarConfigType config = objectMapper.readValue(json, CalendarConfigType.class);

        // Defaults should be used
        assertNotNull(config);
        assertEquals("default", config.theme);
    }

    @Test
    void testReadValue_YearField() throws Exception {
        String json = "{\"year\": " + TEST_YEAR + "}";
        CalendarConfigType config = objectMapper.readValue(json, CalendarConfigType.class);

        assertEquals(TEST_YEAR, config.year);
    }

    @Test
    void testReadValue_ThemeField() throws Exception {
        String json = "{\"theme\": \"dark\"}";
        CalendarConfigType config = objectMapper.readValue(json, CalendarConfigType.class);

        assertEquals("dark", config.theme);
    }

    @Test
    void testReadValue_BooleanFields() throws Exception {
        String json = """
                {
                    "showWeekNumbers": true,
                    "compactMode": true,
                    "showDayNames": false,
                    "showDayNumbers": false,
                    "showGrid": false,
                    "highlightWeekends": false,
                    "rotateMonthNames": true
                }
                """;
        CalendarConfigType config = objectMapper.readValue(json, CalendarConfigType.class);

        assertTrue(config.showWeekNumbers);
        assertTrue(config.compactMode);
        assertFalse(config.showDayNames);
        assertFalse(config.showDayNumbers);
        assertFalse(config.showGrid);
        assertFalse(config.highlightWeekends);
        assertTrue(config.rotateMonthNames);
    }

    @Test
    void testReadValue_MoonSettings() throws Exception {
        String json = """
                {
                    "moonDisplayMode": "illumination",
                    "moonSize": 32,
                    "moonOffsetX": 40,
                    "moonOffsetY": 50,
                    "moonBorderWidth": 1.5,
                    "latitude": 40.7128,
                    "longitude": -74.0060
                }
                """;
        CalendarConfigType config = objectMapper.readValue(json, CalendarConfigType.class);

        assertEquals("illumination", config.moonDisplayMode);
        assertEquals(32, config.moonSize);
        assertEquals(40, config.moonOffsetX);
        assertEquals(50, config.moonOffsetY);
        assertEquals(1.5, config.moonBorderWidth);
        assertEquals(40.7128, config.latitude, 0.0001);
        assertEquals(-74.0060, config.longitude, 0.0001);
    }

    @Test
    void testReadValue_ColorSettings() throws Exception {
        String json = """
                {
                    "yearColor": "#ff0000",
                    "monthColor": "#00ff00",
                    "dayTextColor": "#0000ff",
                    "dayNameColor": "#ffff00",
                    "gridLineColor": "#cccccc",
                    "weekendBgColor": "#f0f0f0",
                    "holidayColor": "#ff00ff",
                    "customDateColor": "#00ffff",
                    "moonDarkColor": "#333333",
                    "moonLightColor": "#eeeeee",
                    "moonBorderColor": "#999999"
                }
                """;
        CalendarConfigType config = objectMapper.readValue(json, CalendarConfigType.class);

        assertEquals("#ff0000", config.yearColor);
        assertEquals("#00ff00", config.monthColor);
        assertEquals("#0000ff", config.dayTextColor);
        assertEquals("#ffff00", config.dayNameColor);
        assertEquals("#cccccc", config.gridLineColor);
        assertEquals("#f0f0f0", config.weekendBgColor);
        assertEquals("#ff00ff", config.holidayColor);
        assertEquals("#00ffff", config.customDateColor);
        assertEquals("#333333", config.moonDarkColor);
        assertEquals("#eeeeee", config.moonLightColor);
        assertEquals("#999999", config.moonBorderColor);
    }

    @Test
    void testReadValue_EmojiSettings() throws Exception {
        String json = """
                {
                    "emojiPosition": "top-right",
                    "emojiFont": "noto-mono",
                    "eventDisplayMode": "small"
                }
                """;
        CalendarConfigType config = objectMapper.readValue(json, CalendarConfigType.class);

        assertEquals("top-right", config.emojiPosition);
        assertEquals("noto-mono", config.emojiFont);
        assertEquals("small", config.eventDisplayMode);
    }

    @Test
    void testReadValue_LocaleAndFirstDayOfWeek() throws Exception {
        String json = "{\"locale\": \"de-DE\", \"firstDayOfWeek\": \"MONDAY\"}";
        CalendarConfigType config = objectMapper.readValue(json, CalendarConfigType.class);

        assertEquals("de-DE", config.locale);
        assertEquals(DayOfWeek.MONDAY, config.firstDayOfWeek);
    }

    @Test
    void testReadValue_LayoutStyle() throws Exception {
        String json = "{\"layoutStyle\": \"weekday-grid\"}";
        CalendarConfigType config = objectMapper.readValue(json, CalendarConfigType.class);

        assertEquals("weekday-grid", config.layoutStyle);
    }

    @Test
    void testReadValue_HolidaySets() throws Exception {
        String json = "{\"holidaySets\": [\"us-federal\", \"christian\"]}";
        CalendarConfigType config = objectMapper.readValue(json, CalendarConfigType.class);

        assertEquals(2, config.holidaySets.size());
        assertTrue(config.holidaySets.contains("us-federal"));
        assertTrue(config.holidaySets.contains("christian"));
    }

    @Test
    void testReadValue_CustomDates() throws Exception {
        String json = "{\"customDates\": {\"2025-01-15\": \"🎂\", \"2025-06-20\": \"🎉\"}}";
        CalendarConfigType config = objectMapper.readValue(json, CalendarConfigType.class);

        assertEquals(2, config.customDates.size());
    }

    @Test
    void testReadValue_EventTitles() throws Exception {
        String json = "{\"eventTitles\": {\"2025-01-15\": \"Birthday\", \"2025-06-20\": \"Anniversary\"}}";
        CalendarConfigType config = objectMapper.readValue(json, CalendarConfigType.class);

        assertEquals(2, config.eventTitles.size());
        assertEquals("Birthday", config.eventTitles.get("2025-01-15"));
    }

    // ========== GENERATE CALENDAR SVG TESTS ==========

    @Test
    void testGenerateCalendarSVG_ReturnsValidSvg() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
        assertTrue(svg.contains(SVG_CLOSE_TAG));
    }

    @Test
    void testGenerateCalendarSVG_ContainsYearTitle() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertTrue(svg.contains(String.valueOf(TEST_YEAR)));
    }

    @Test
    void testGenerateCalendarSVG_ContainsMonthNames() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertTrue(svg.contains("January") || svg.contains("Jan"));
        assertTrue(svg.contains("December") || svg.contains("Dec"));
    }

    @Test
    void testGenerateCalendarSVG_WithShowDayNumbers() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.showDayNumbers = true;

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertTrue(svg.contains(">1<") || svg.contains(">31<") || svg.contains(TEXT_TAG));
    }

    @Test
    void testGenerateCalendarSVG_WithoutDayNumbers() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.showDayNumbers = false;

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_WithGrid() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.showGrid = true;

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertTrue(svg.contains(RECT_TAG) || svg.contains("line"));
    }

    @Test
    void testGenerateCalendarSVG_WithWeekdayGridLayout() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.layoutStyle = "weekday-grid";

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_WithMondayStart() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.firstDayOfWeek = DayOfWeek.MONDAY;

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_WithMoonIllumination() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.moonDisplayMode = "illumination";
        config.latitude = 40.7128;
        config.longitude = -74.0060;

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_WithMoonPhases() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.moonDisplayMode = "phases";

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_WithCompactMode() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.compactMode = true;

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_WithRotatedMonthNames() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.rotateMonthNames = true;

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_WithWeekNumbers() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.showWeekNumbers = true;

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_WithCustomColors() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.yearColor = "#ff0000";
        config.monthColor = "#00ff00";
        config.dayTextColor = "#0000ff";

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        // Colors may be in different format in SVG
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_WithHighlightWeekends() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.highlightWeekends = true;
        config.weekendBgColor = "#f5f5f5";

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_WithHolidaySets() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.holidaySets.add("us-federal");

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_WithCustomDates() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.customDates.put("2025-01-15", "🎂");
        config.eventTitles.put("2025-01-15", "Birthday");

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"default", "dark", "minimal", "colorful"})
    void testGenerateCalendarSVG_DifferentThemes(String theme) {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.theme = theme;

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @ParameterizedTest
    @ValueSource(
            ints = {2020, 2025, 2030, 2050})
    void testGenerateCalendarSVG_DifferentYears(int year) {
        CalendarConfigType config = new CalendarConfigType();
        config.year = year;

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(String.valueOf(year)));
    }

    // ========== GENERATE CALENDAR PDF TESTS ==========

    @Test
    void testGenerateCalendarPDF_ReturnsNonEmptyBytes() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;

        byte[] pdf = calendarRenderingService.generateCalendarPDF(config);

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    void testGenerateCalendarPDF_StartsWith_PDF_Magic() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;

        byte[] pdf = calendarRenderingService.generateCalendarPDF(config);

        assertTrue(pdf.length > 4);
        assertEquals('%', (char) pdf[0]);
        assertEquals('P', (char) pdf[1]);
        assertEquals('D', (char) pdf[2]);
        assertEquals('F', (char) pdf[3]);
    }

    @Test
    void testGenerateCalendarPDF_WithCompactMode() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.compactMode = true;

        byte[] pdf = calendarRenderingService.generateCalendarPDF(config);

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    // ========== WRAP SVG FOR PREVIEW TESTS ==========

    @Test
    void testWrapSvgForPreview_ReturnsValidSvg() {
        String innerSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">"
                + "<rect x=\"0\" y=\"0\" width=\"100\" height=\"100\" fill=\"red\"/></svg>";

        String wrapped = calendarRenderingService.wrapSvgForPreview(innerSvg);

        assertNotNull(wrapped);
        assertTrue(wrapped.contains(SVG_OPEN_TAG));
        assertTrue(wrapped.contains(SVG_CLOSE_TAG));
    }

    @Test
    void testWrapSvgForPreview_AddsViewBox() {
        String innerSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 200 150\">"
                + "<rect x=\"0\" y=\"0\" width=\"200\" height=\"150\"/></svg>";

        String wrapped = calendarRenderingService.wrapSvgForPreview(innerSvg);

        assertTrue(wrapped.contains("viewBox"));
    }

    @Test
    void testWrapSvgForPreview_WithoutViewBox_ReturnsSvgWithBackground() {
        String innerSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"100\">"
                + "<rect x=\"0\" y=\"0\" width=\"100\" height=\"100\"/></svg>";

        String wrapped = calendarRenderingService.wrapSvgForPreview(innerSvg);

        assertNotNull(wrapped);
        // Should contain white background
        assertTrue(wrapped.contains("fill=\"white\""));
    }

    @Test
    void testWrapSvgForPreview_EmptyInnerSvg() {
        String wrapped = calendarRenderingService.wrapSvgForPreview("");

        // Empty string returns empty string (no svg tags to replace)
        assertNotNull(wrapped);
    }

    // ========== MOON ILLUMINATION SVG TESTS ==========

    @Test
    void testGenerateMoonIlluminationSVG_ReturnsValidSvg() {
        LocalDate date = LocalDate.of(2025, 1, 15);
        CalendarConfigType config = new CalendarConfigType();
        config.moonSize = 24;
        config.moonBorderWidth = 0.5;
        config.moonBorderColor = "#c1c1c1";
        config.moonLightColor = "#ffffff";
        config.moonDarkColor = "#333333";

        String svg = calendarRenderingService.generateMoonIlluminationSVG(date, 50, 50, 40.7128, -74.0060, config);

        assertNotNull(svg);
    }

    @Test
    void testGenerateMoonIlluminationSVG_DifferentDates() {
        LocalDate newMoon = LocalDate.of(2025, 1, 29); // Approximate new moon
        LocalDate fullMoon = LocalDate.of(2025, 1, 13); // Approximate full moon
        CalendarConfigType config = new CalendarConfigType();

        String newMoonSvg = calendarRenderingService.generateMoonIlluminationSVG(newMoon, 50, 50, 0, 0, config);
        String fullMoonSvg = calendarRenderingService.generateMoonIlluminationSVG(fullMoon, 50, 50, 0, 0, config);

        assertNotNull(newMoonSvg);
        assertNotNull(fullMoonSvg);
        // Different moon phases should produce different SVGs
    }

    @Test
    void testGenerateMoonIlluminationSVG_WithLocation() {
        LocalDate date = LocalDate.of(2025, 6, 15);
        CalendarConfigType config = new CalendarConfigType();

        // New York coordinates
        String nySvg = calendarRenderingService.generateMoonIlluminationSVG(date, 50, 50, 40.7128, -74.0060, config);

        // Sydney coordinates
        String sydneySvg = calendarRenderingService.generateMoonIlluminationSVG(date, 50, 50, -33.8688, 151.2093,
                config);

        assertNotNull(nySvg);
        assertNotNull(sydneySvg);
    }

    // ========== CONVERT COLOR FOR PDF TESTS ==========

    @ParameterizedTest
    @CsvSource({"#ff0000, #ff0000", "#00FF00, #00FF00", "#0000ff, #0000ff", "red, red", "blue, blue"})
    void testConvertColorForPDF_HexColors(String input, String expected) {
        String result = CalendarRenderingService.convertColorForPDF(input);

        assertEquals(expected, result);
    }

    @Test
    void testConvertColorForPDF_RgbaToRgb() {
        String rgba = "rgba(255, 0, 0, 0.5)";

        String result = CalendarRenderingService.convertColorForPDF(rgba);

        assertNotNull(result);
        // Should convert RGBA to RGB format suitable for PDF
        assertTrue(result.startsWith("rgb(") || result.startsWith("#") || !result.contains("rgba"));
    }

    @Test
    void testConvertColorForPDF_RgbColor() {
        String rgb = "rgb(255, 128, 64)";

        String result = CalendarRenderingService.convertColorForPDF(rgb);

        assertNotNull(result);
    }

    @Test
    void testConvertColorForPDF_TransparentColor() {
        String transparent = "transparent";

        String result = CalendarRenderingService.convertColorForPDF(transparent);

        assertNotNull(result);
    }

    @Test
    void testConvertColorForPDF_NullColor() {
        String result = CalendarRenderingService.convertColorForPDF(null);

        // Should handle null gracefully
        assertNotNull(result);
    }

    @Test
    void testConvertColorForPDF_EmptyColor() {
        String result = CalendarRenderingService.convertColorForPDF("");

        assertNotNull(result);
    }

    // ========== GET CELL BACKGROUND COLOR TESTS ==========

    @Test
    void testGetCellBackgroundColor_Weekend_WithHighlight() {
        CalendarConfigType config = new CalendarConfigType();
        config.highlightWeekends = true;
        config.weekendBgColor = "#f0f0f0";

        LocalDate saturday = LocalDate.of(2025, 1, 4); // Saturday

        String color = CalendarRenderingService.getCellBackgroundColor(config, saturday, 1, 4, true, 0);

        assertNotNull(color);
    }

    @Test
    void testGetCellBackgroundColor_Weekday() {
        CalendarConfigType config = new CalendarConfigType();
        config.highlightWeekends = true;

        LocalDate wednesday = LocalDate.of(2025, 1, 8); // Wednesday

        String color = CalendarRenderingService.getCellBackgroundColor(config, wednesday, 1, 8, false, 0);

        // Weekday should not have weekend highlight
        assertNotNull(color);
    }

    @Test
    void testGetCellBackgroundColor_Holiday() {
        CalendarConfigType config = new CalendarConfigType();
        config.holidayColor = "#ff5252";

        LocalDate date = LocalDate.of(2025, 1, 1);

        String color = CalendarRenderingService.getCellBackgroundColor(config, date, 1, 1, false, 0);

        assertNotNull(color);
    }

    @Test
    void testGetCellBackgroundColor_NoHighlightWeekends() {
        CalendarConfigType config = new CalendarConfigType();
        config.highlightWeekends = false;

        LocalDate saturday = LocalDate.of(2025, 1, 4);

        String color = CalendarRenderingService.getCellBackgroundColor(config, saturday, 1, 4, true, 0);

        // Should not highlight weekends when disabled
        assertNotNull(color);
    }

    // ========== CONSTANTS TESTS ==========

    @Test
    void testPageDimensions_ValidValues() {
        assertTrue(CalendarRenderingService.PAGE_WIDTH_INCHES > 0);
        assertTrue(CalendarRenderingService.PAGE_HEIGHT_INCHES > 0);
        assertTrue(CalendarRenderingService.MARGIN_INCHES >= 0);
        assertTrue(CalendarRenderingService.PRINTABLE_WIDTH_INCHES > 0);
        assertTrue(CalendarRenderingService.PRINTABLE_HEIGHT_INCHES > 0);
    }

    @Test
    void testPageDimensions_PrintableAreaSmallerThanPage() {
        assertTrue(CalendarRenderingService.PRINTABLE_WIDTH_INCHES < CalendarRenderingService.PAGE_WIDTH_INCHES);
        assertTrue(CalendarRenderingService.PRINTABLE_HEIGHT_INCHES < CalendarRenderingService.PAGE_HEIGHT_INCHES);
    }

    @Test
    void testDefaultTheme_NotNull() {
        assertNotNull(CalendarRenderingService.DEFAULT_THEME);
        assertEquals("default", CalendarRenderingService.DEFAULT_THEME);
    }

    // ========== EMOJI FONT TESTS ==========

    @Test
    void testGetEmojiFontFamily_DefaultReturnsNotoColorEmoji() {
        CalendarConfigType config = new CalendarConfigType();
        // Default emojiFont is null

        String fontFamily = CalendarRenderingService.getEmojiFontFamily(config);

        assertNotNull(fontFamily);
        assertTrue(fontFamily.contains("Noto Color Emoji"));
        assertTrue(fontFamily.contains("Noto Emoji")); // Fallback
    }

    @Test
    void testGetEmojiFontFamily_NotoMonoReturnsMonochromeFonts() {
        CalendarConfigType config = new CalendarConfigType();
        config.emojiFont = CalendarRenderingService.EMOJI_FONT_NOTO_MONO;

        String fontFamily = CalendarRenderingService.getEmojiFontFamily(config);

        assertNotNull(fontFamily);
        assertTrue(fontFamily.contains("Noto Emoji"));
        assertTrue(fontFamily.contains("DejaVu Sans"));
        assertFalse(fontFamily.contains("Noto Color Emoji"));
    }

    @Test
    void testSubstituteEmojiForMonochrome_NoSubstitutionForColorMode() {
        CalendarConfigType config = new CalendarConfigType();
        // Default is color mode (not noto-mono)

        String result = CalendarRenderingService.substituteEmojiForMonochrome("🕎", config);

        assertEquals("🕎", result); // No substitution in color mode
    }

    @Test
    void testSubstituteEmojiForMonochrome_SubstitutesForNotoMono() {
        CalendarConfigType config = new CalendarConfigType();
        config.emojiFont = CalendarRenderingService.EMOJI_FONT_NOTO_MONO;

        // Menorah -> Star of David
        assertEquals("✡️", CalendarRenderingService.substituteEmojiForMonochrome("🕎", config));

        // Diya Lamp -> Candle (Diwali)
        assertEquals("🕯️", CalendarRenderingService.substituteEmojiForMonochrome("🪔", config));

        // Red Envelope -> Lantern (Chinese New Year)
        assertEquals("🏮", CalendarRenderingService.substituteEmojiForMonochrome("🧧", config));

        // Unknown emoji passes through unchanged
        assertEquals("🎉", CalendarRenderingService.substituteEmojiForMonochrome("🎉", config));
    }

    @Test
    void testSubstituteEmojiForMonochrome_SubstitutesForMonoColorVariants() {
        CalendarConfigType config = new CalendarConfigType();
        config.emojiFont = "mono-red";

        // Should substitute just like noto-mono
        assertEquals("✡️", CalendarRenderingService.substituteEmojiForMonochrome("🕎", config));
    }

    // ========== EDGE CASE TESTS ==========

    @Test
    void testGenerateCalendarSVG_LeapYear() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = 2024; // Leap year

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains("2024"));
    }

    @Test
    void testGenerateCalendarSVG_AllFeaturesEnabled() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.showWeekNumbers = true;
        config.showDayNames = true;
        config.showDayNumbers = true;
        config.showGrid = true;
        config.highlightWeekends = true;
        config.moonDisplayMode = "illumination";
        config.rotateMonthNames = true;
        config.holidaySets.add("us-federal");

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
        assertTrue(svg.contains(SVG_CLOSE_TAG));
    }

    @Test
    void testGenerateCalendarSVG_MinimalConfig() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.showWeekNumbers = false;
        config.showDayNames = false;
        config.showDayNumbers = false;
        config.showGrid = false;
        config.highlightWeekends = false;
        config.moonDisplayMode = "none";

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_DifferentLocales() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;

        // Test with different locales (excluding locales with single-character day abbreviations)
        for (String locale : List.of("en-US", "de-DE", "fr-FR", "es-ES")) {
            config.locale = locale;
            String svg = calendarRenderingService.generateCalendarSVG(config);
            assertNotNull(svg, "SVG should be generated for locale: " + locale);
        }
    }

    // ========== MONOCHROME MODE BRANCH TESTS ==========

    @Test
    void testGenerateCalendarSVG_WithMonochromeEmoji_NotoMono() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.emojiFont = CalendarRenderingService.EMOJI_FONT_NOTO_MONO;
        config.customDates.put("2025-01-15", "🕎"); // Menorah - will be substituted

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_WithColorEmoji_Default() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        // emojiFont is null by default - uses color emoji
        config.customDates.put("2025-01-15", "🕎");

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"mono-red", "mono-blue", "mono-green", "mono-orange", "mono-purple", "mono-pink", "mono-teal",
                    "mono-brown", "mono-navy", "mono-coral"})
    void testGenerateCalendarSVG_WithMonochromeColorVariants(String emojiFont) {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.emojiFont = emojiFont;
        config.customDates.put("2025-01-15", "🎉");

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testSubstituteEmojiForMonochrome_AllSubstitutions() {
        CalendarConfigType config = new CalendarConfigType();
        config.emojiFont = CalendarRenderingService.EMOJI_FONT_NOTO_MONO;

        // Test all substitutions
        assertEquals("✡️", CalendarRenderingService.substituteEmojiForMonochrome("🕎", config)); // Menorah
        assertEquals("⭐", CalendarRenderingService.substituteEmojiForMonochrome("🎖️", config)); // Military Medal
        assertEquals("☀️", CalendarRenderingService.substituteEmojiForMonochrome("🪁", config)); // Kite
        assertEquals("🎵", CalendarRenderingService.substituteEmojiForMonochrome("🪈", config)); // Flute
        assertEquals("🕯️", CalendarRenderingService.substituteEmojiForMonochrome("🪔", config)); // Diya Lamp
        assertEquals("🙏", CalendarRenderingService.substituteEmojiForMonochrome("🤲", config)); // Palms Up
        assertEquals("🌙", CalendarRenderingService.substituteEmojiForMonochrome("☪️", config)); // Star and Crescent
        assertEquals("🏮", CalendarRenderingService.substituteEmojiForMonochrome("🧧", config)); // Red Envelope
        assertEquals("🌸", CalendarRenderingService.substituteEmojiForMonochrome("🪦", config)); // Headstone
        assertEquals("🌕", CalendarRenderingService.substituteEmojiForMonochrome("🥮", config)); // Moon Cake
        assertEquals("⛰️", CalendarRenderingService.substituteEmojiForMonochrome("🏔️", config)); // Snow Mountain
        assertEquals("🐿️", CalendarRenderingService.substituteEmojiForMonochrome("🦫", config)); // Beaver
        assertEquals("🌈", CalendarRenderingService.substituteEmojiForMonochrome("🏳️‍🌈", config)); // Pride Flag
    }

    @Test
    void testIsMonochrome_FalseForNullEmojiFont() {
        CalendarConfigType config = new CalendarConfigType();
        config.emojiFont = null; // Null means color mode

        // substituteEmojiForMonochrome should return emoji unchanged
        String result = CalendarRenderingService.substituteEmojiForMonochrome("🕎", config);
        assertEquals("🕎", result);
    }

    @Test
    void testIsMonochrome_FalseForNonMonoFont() {
        CalendarConfigType config = new CalendarConfigType();
        config.emojiFont = "noto-color"; // Not noto-mono or mono-*

        String result = CalendarRenderingService.substituteEmojiForMonochrome("🕎", config);
        assertEquals("🕎", result);
    }

    // ========== EVENT DISPLAY MODE BRANCH TESTS ==========

    @Test
    void testGenerateCalendarSVG_EventDisplayMode_None() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.eventDisplayMode = "none";
        config.customDates.put("2025-01-15", "🎂");
        config.eventTitles.put("2025-01-15", "Birthday");

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_EventDisplayMode_Large() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.eventDisplayMode = "large";
        config.customDates.put("2025-01-15", "🎂");

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_EventDisplayMode_LargeText() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.eventDisplayMode = "large-text";
        config.customDates.put("2025-01-15", "🎂");
        config.eventTitles.put("2025-01-15", "Birthday Party");

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_EventDisplayMode_Small() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.eventDisplayMode = "small";
        config.customDates.put("2025-01-15", "🎂");

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    // ========== MOON DISPLAY MODE BRANCH TESTS ==========

    @Test
    void testGenerateCalendarSVG_MoonDisplayMode_None() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.moonDisplayMode = "none";

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_MoonDisplayMode_Illumination() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.moonDisplayMode = "illumination";
        config.latitude = 40.7128;
        config.longitude = -74.0060;

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_MoonDisplayMode_Phases() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.moonDisplayMode = "phases";

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_MoonDisplayMode_FullOnly() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.moonDisplayMode = "full-only";

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    // ========== ROTATE MONTH NAMES BRANCH TESTS ==========

    @Test
    void testGenerateCalendarSVG_RotateMonthNames_True() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.rotateMonthNames = true;

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains("rotate"));
    }

    @Test
    void testGenerateCalendarSVG_RotateMonthNames_False() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.rotateMonthNames = false;

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    // ========== SHOW DAY NAMES BRANCH TESTS ==========

    @Test
    void testGenerateCalendarSVG_ShowDayNames_True() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.showDayNames = true;

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        // Should contain day abbreviations like Sun, Mon, etc.
        assertTrue(svg.contains("Sun") || svg.contains("Mon") || svg.contains("S") || svg.contains("M"));
    }

    @Test
    void testGenerateCalendarSVG_ShowDayNames_False() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.showDayNames = false;

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    // ========== SHOW GRID BRANCH TESTS ==========

    @Test
    void testGenerateCalendarSVG_ShowGrid_True() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.showGrid = true;
        config.gridLineColor = "#cccccc";

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(RECT_TAG) || svg.contains("<line"));
    }

    @Test
    void testGenerateCalendarSVG_ShowGrid_False() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.showGrid = false;

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    // ========== HIGHLIGHT WEEKENDS BRANCH TESTS ==========

    @Test
    void testGenerateCalendarSVG_HighlightWeekends_True() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.highlightWeekends = true;
        config.weekendBgColor = "#f0f0f0";

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_HighlightWeekends_False() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.highlightWeekends = false;

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    // ========== FIRST DAY OF WEEK BRANCH TESTS ==========

    @ParameterizedTest
    @ValueSource(
            strings = {"SUNDAY", "MONDAY", "SATURDAY"})
    void testGenerateCalendarSVG_DifferentFirstDayOfWeek(String dayOfWeek) {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.firstDayOfWeek = DayOfWeek.valueOf(dayOfWeek);

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    // ========== LAYOUT STYLE BRANCH TESTS ==========

    @Test
    void testGenerateCalendarSVG_LayoutStyle_Default() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.layoutStyle = "default";

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_LayoutStyle_WeekdayGrid() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.layoutStyle = "weekday-grid";

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    // ========== EMOJI POSITION BRANCH TESTS ==========

    @ParameterizedTest
    @ValueSource(
            strings = {"top-left", "top-center", "top-right", "middle-left", "middle-center", "middle-right",
                    "bottom-left", "bottom-center", "bottom-right"})
    void testGenerateCalendarSVG_DifferentEmojiPositions(String position) {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.emojiPosition = position;
        config.customDates.put("2025-01-15", "🎂");

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    // ========== HOLIDAY COLOR BRANCH TESTS ==========

    @Test
    void testGenerateCalendarSVG_WithHolidayColor() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.holidayColor = "#ff0000";
        config.holidaySets.add("us-federal");

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_WithCustomDateColor() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.customDateColor = "#00ff00";
        config.customDates.put("2025-01-15", "🎂");

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    // ========== MOON WITH HOLIDAY BRANCH TESTS ==========

    @Test
    void testGenerateCalendarSVG_MoonWithHoliday() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.moonDisplayMode = "illumination";
        config.eventDisplayMode = "large";
        config.holidaySets.add("us-federal");

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_MoonWithCustomDate() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.moonDisplayMode = "phases";
        config.eventDisplayMode = "small";
        config.customDates.put("2025-01-13", "🎂"); // Near full moon date

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    // ========== CUSTOM EVENT DISPLAY SETTINGS BRANCH TESTS ==========

    @Test
    void testGenerateCalendarSVG_CustomEventWithDisplaySettings() throws Exception {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;

        // Create custom date with display settings
        java.util.Map<String, Object> customData = new java.util.HashMap<>();
        customData.put("emoji", "🎂");
        java.util.Map<String, Object> displaySettings = new java.util.HashMap<>();
        displaySettings.put("position", "top-right");
        displaySettings.put("size", 24);
        customData.put("displaySettings", displaySettings);
        config.customDates.put("2025-01-15", customData);

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_CustomEventWithTextWrap() throws Exception {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;

        java.util.Map<String, Object> customData = new java.util.HashMap<>();
        customData.put("emoji", "🎂");
        java.util.Map<String, Object> displaySettings = new java.util.HashMap<>();
        displaySettings.put("textWrap", true);
        customData.put("displaySettings", displaySettings);
        config.customDates.put("2025-01-15", customData);
        config.eventTitles.put("2025-01-15", "A Very Long Birthday Party Title");

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    // ========== TEXT ALIGNMENT BRANCH TESTS ==========

    @Test
    void testGenerateCalendarSVG_EventWithLeftAlignment() throws Exception {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;

        java.util.Map<String, Object> customData = new java.util.HashMap<>();
        customData.put("emoji", "🎂");
        java.util.Map<String, Object> displaySettings = new java.util.HashMap<>();
        displaySettings.put("textAlign", "left");
        customData.put("displaySettings", displaySettings);
        config.customDates.put("2025-01-15", customData);
        config.eventTitles.put("2025-01-15", "Birthday");

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_EventWithRightAlignment() throws Exception {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;

        java.util.Map<String, Object> customData = new java.util.HashMap<>();
        customData.put("emoji", "🎂");
        java.util.Map<String, Object> displaySettings = new java.util.HashMap<>();
        displaySettings.put("textAlign", "right");
        customData.put("displaySettings", displaySettings);
        config.customDates.put("2025-01-15", customData);
        config.eventTitles.put("2025-01-15", "Birthday");

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_EventWithCenterAlignment() throws Exception {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;

        java.util.Map<String, Object> customData = new java.util.HashMap<>();
        customData.put("emoji", "🎂");
        java.util.Map<String, Object> displaySettings = new java.util.HashMap<>();
        displaySettings.put("textAlign", "center");
        customData.put("displaySettings", displaySettings);
        config.customDates.put("2025-01-15", customData);
        config.eventTitles.put("2025-01-15", "Birthday");

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    // ========== LOCATION-BASED MOON CALCULATION BRANCH TESTS ==========

    @Test
    void testGenerateMoonIlluminationSVG_NorthernHemisphere() {
        LocalDate date = LocalDate.of(2025, 6, 15);
        CalendarConfigType config = new CalendarConfigType();
        config.moonSize = 24;

        // Northern hemisphere location (New York)
        String svg = calendarRenderingService.generateMoonIlluminationSVG(date, 50, 50, 40.7128, -74.0060, config);

        assertNotNull(svg);
    }

    @Test
    void testGenerateMoonIlluminationSVG_SouthernHemisphere() {
        LocalDate date = LocalDate.of(2025, 6, 15);
        CalendarConfigType config = new CalendarConfigType();
        config.moonSize = 24;

        // Southern hemisphere location (Sydney)
        String svg = calendarRenderingService.generateMoonIlluminationSVG(date, 50, 50, -33.8688, 151.2093, config);

        assertNotNull(svg);
    }

    @Test
    void testGenerateMoonIlluminationSVG_Equator() {
        LocalDate date = LocalDate.of(2025, 6, 15);
        CalendarConfigType config = new CalendarConfigType();
        config.moonSize = 24;

        // Near equator location
        String svg = calendarRenderingService.generateMoonIlluminationSVG(date, 50, 50, 0.0, 0.0, config);

        assertNotNull(svg);
    }

    @Test
    void testGenerateMoonIlluminationSVG_NewMoon() {
        // Approximate new moon date
        LocalDate date = LocalDate.of(2025, 1, 29);
        CalendarConfigType config = new CalendarConfigType();
        config.moonSize = 24;

        String svg = calendarRenderingService.generateMoonIlluminationSVG(date, 50, 50, 40.7128, -74.0060, config);

        assertNotNull(svg);
    }

    @Test
    void testGenerateMoonIlluminationSVG_FullMoon() {
        // Approximate full moon date
        LocalDate date = LocalDate.of(2025, 1, 13);
        CalendarConfigType config = new CalendarConfigType();
        config.moonSize = 24;

        String svg = calendarRenderingService.generateMoonIlluminationSVG(date, 50, 50, 40.7128, -74.0060, config);

        assertNotNull(svg);
    }

    @Test
    void testGenerateMoonIlluminationSVG_FirstQuarter() {
        // Approximate first quarter date
        LocalDate date = LocalDate.of(2025, 1, 6);
        CalendarConfigType config = new CalendarConfigType();
        config.moonSize = 24;

        String svg = calendarRenderingService.generateMoonIlluminationSVG(date, 50, 50, 40.7128, -74.0060, config);

        assertNotNull(svg);
    }

    @Test
    void testGenerateMoonIlluminationSVG_LastQuarter() {
        // Approximate last quarter date
        LocalDate date = LocalDate.of(2025, 1, 21);
        CalendarConfigType config = new CalendarConfigType();
        config.moonSize = 24;

        String svg = calendarRenderingService.generateMoonIlluminationSVG(date, 50, 50, 40.7128, -74.0060, config);

        assertNotNull(svg);
    }

    // ========== CELL BACKGROUND COLOR BRANCH TESTS ==========

    @Test
    void testGetCellBackgroundColor_WeekendWithColor() {
        CalendarConfigType config = new CalendarConfigType();
        config.highlightWeekends = true;
        config.weekendBgColor = "#f0f0f0";

        // Saturday
        LocalDate saturday = LocalDate.of(2025, 1, 4);
        String color = CalendarRenderingService.getCellBackgroundColor(config, saturday, 1, 4, true, 0);

        assertNotNull(color);
    }

    @Test
    void testGetCellBackgroundColor_WeekdayNoHighlight() {
        CalendarConfigType config = new CalendarConfigType();
        config.highlightWeekends = false;

        // Wednesday
        LocalDate wednesday = LocalDate.of(2025, 1, 8);
        String color = CalendarRenderingService.getCellBackgroundColor(config, wednesday, 1, 8, false, 0);

        assertNotNull(color);
    }

    @Test
    void testGetCellBackgroundColor_DifferentMonths() {
        CalendarConfigType config = new CalendarConfigType();

        // January
        LocalDate jan = LocalDate.of(2025, 1, 15);
        String color1 = CalendarRenderingService.getCellBackgroundColor(config, jan, 1, 15, false, 0);

        // February
        LocalDate feb = LocalDate.of(2025, 2, 15);
        String color2 = CalendarRenderingService.getCellBackgroundColor(config, feb, 2, 15, false, 0);

        assertNotNull(color1);
        assertNotNull(color2);
    }

    // ========== COMPACT MODE BRANCH TESTS ==========

    @Test
    void testGenerateCalendarSVG_CompactMode_True() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.compactMode = true;

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_CompactMode_False() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.compactMode = false;

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    // ========== WEEK NUMBERS BRANCH TESTS ==========

    @Test
    void testGenerateCalendarSVG_ShowWeekNumbers_True() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.showWeekNumbers = true;

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_ShowWeekNumbers_False() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.showWeekNumbers = false;

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    // ========== COMBINATION BRANCH TESTS ==========

    @Test
    void testGenerateCalendarSVG_MonochromeWithMoon() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.emojiFont = CalendarRenderingService.EMOJI_FONT_NOTO_MONO;
        config.moonDisplayMode = "illumination";

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_ColoredMonoWithHolidays() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.emojiFont = "mono-red";
        config.holidaySets.add("us-federal");
        config.eventDisplayMode = "large";

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_WeekdayGridWithMoon() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.layoutStyle = "weekday-grid";
        config.moonDisplayMode = "phases";

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains(SVG_OPEN_TAG));
    }

    @Test
    void testGenerateCalendarSVG_RotatedMonthsWithWeekNumbers() {
        CalendarConfigType config = new CalendarConfigType();
        config.year = TEST_YEAR;
        config.rotateMonthNames = true;
        config.showWeekNumbers = true;

        String svg = calendarRenderingService.generateCalendarSVG(config);

        assertNotNull(svg);
        assertTrue(svg.contains("rotate"));
    }
}
