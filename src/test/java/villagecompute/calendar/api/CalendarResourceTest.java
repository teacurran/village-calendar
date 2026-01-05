package villagecompute.calendar.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/** Tests for CalendarResource REST endpoints. */
@QuarkusTest
class CalendarResourceTest {

    private static final int TEST_YEAR = 2025;
    private static final String CONTENT_TYPE_SVG = "image/svg+xml";
    private static final String CONTENT_TYPE_PDF = "application/pdf";

    // ========== POST /api/calendar/generate TESTS ==========

    @Test
    void testGenerate_DefaultYear() {
        Map<String, Object> request = new HashMap<>();

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/generate").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG).body(containsString("<svg"));
    }

    @Test
    void testGenerate_SpecificYear() {
        Map<String, Object> request = new HashMap<>();
        request.put("year", TEST_YEAR);

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/generate").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG).body(containsString("<svg")).body(containsString("2025"));
    }

    @Test
    void testGenerate_WithTheme() {
        Map<String, Object> request = new HashMap<>();
        request.put("year", TEST_YEAR);
        request.put("theme", "dark");

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/generate").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG).body(containsString("<svg"));
    }

    @Test
    void testGenerate_HebrewCalendar() {
        Map<String, Object> request = new HashMap<>();
        request.put("calendarType", "hebrew");
        request.put("year", 5785);

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/generate").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG);
    }

    @Test
    void testGenerate_WithMoonDisplay() {
        Map<String, Object> request = new HashMap<>();
        request.put("year", TEST_YEAR);
        request.put("moonDisplayMode", "illumination");
        request.put("latitude", 40.7128);
        request.put("longitude", -74.0060);

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/generate").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG);
    }

    @Test
    void testGenerate_WithWeekNumbers() {
        Map<String, Object> request = new HashMap<>();
        request.put("year", TEST_YEAR);
        request.put("showWeekNumbers", true);

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/generate").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG);
    }

    @Test
    void testGenerate_CompactMode() {
        Map<String, Object> request = new HashMap<>();
        request.put("year", TEST_YEAR);
        request.put("compactMode", true);

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/generate").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG);
    }

    @Test
    void testGenerate_WithHolidaySets() {
        Map<String, Object> request = new HashMap<>();
        request.put("year", TEST_YEAR);
        request.put("holidaySets", List.of("us-federal", "christian"));

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/generate").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG);
    }

    @Test
    void testGenerate_WithCustomDates() {
        Map<String, Object> request = new HashMap<>();
        request.put("year", TEST_YEAR);
        Map<String, Object> customDates = new HashMap<>();
        customDates.put("2025-01-15", Map.of("emoji", "🎂"));
        customDates.put("2025-06-20", Map.of("emoji", "🎉"));
        request.put("customDates", customDates);

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/generate").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG);
    }

    @Test
    void testGenerate_WithEventTitles() {
        Map<String, Object> request = new HashMap<>();
        request.put("year", TEST_YEAR);
        Map<String, String> eventTitles = new HashMap<>();
        eventTitles.put("2025-01-15", "Birthday");
        request.put("eventTitles", eventTitles);

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/generate").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG);
    }

    @Test
    void testGenerate_WithCustomColors() {
        Map<String, Object> request = new HashMap<>();
        request.put("year", TEST_YEAR);
        request.put("yearColor", "#ff0000");
        request.put("monthColor", "#00ff00");
        request.put("dayTextColor", "#0000ff");

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/generate").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG);
    }

    @Test
    void testGenerate_WithoutDayNames() {
        Map<String, Object> request = new HashMap<>();
        request.put("year", TEST_YEAR);
        request.put("showDayNames", false);

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/generate").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG);
    }

    @Test
    void testGenerate_WithoutDayNumbers() {
        Map<String, Object> request = new HashMap<>();
        request.put("year", TEST_YEAR);
        request.put("showDayNumbers", false);

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/generate").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG);
    }

    @Test
    void testGenerate_WithoutGrid() {
        Map<String, Object> request = new HashMap<>();
        request.put("year", TEST_YEAR);
        request.put("showGrid", false);

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/generate").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG);
    }

    @Test
    void testGenerate_WithHighlightWeekends() {
        Map<String, Object> request = new HashMap<>();
        request.put("year", TEST_YEAR);
        request.put("highlightWeekends", true);
        request.put("weekendBgColor", "#f5f5f5");

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/generate").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG);
    }

    @Test
    void testGenerate_WithRotatedMonthNames() {
        Map<String, Object> request = new HashMap<>();
        request.put("year", TEST_YEAR);
        request.put("rotateMonthNames", true);

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/generate").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG);
    }

    @Test
    void testGenerate_MondayStart() {
        Map<String, Object> request = new HashMap<>();
        request.put("year", TEST_YEAR);
        request.put("firstDayOfWeek", "MONDAY");

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/generate").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG);
    }

    // ========== POST /api/calendar/generate-json TESTS ==========

    @Test
    void testGenerateJson_DefaultYear() {
        Map<String, Object> request = new HashMap<>();

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/generate-json").then()
                .statusCode(200).contentType(ContentType.JSON).body("svg", containsString("<svg"))
                .body("format", equalTo("svg"));
    }

    @Test
    void testGenerateJson_SpecificYear() {
        Map<String, Object> request = new HashMap<>();
        request.put("year", TEST_YEAR);

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/generate-json").then()
                .statusCode(200).contentType(ContentType.JSON).body("year", equalTo(TEST_YEAR))
                .body("svg", containsString("<svg"));
    }

    @Test
    void testGenerateJson_WithTheme() {
        Map<String, Object> request = new HashMap<>();
        request.put("year", TEST_YEAR);
        request.put("theme", "minimal");

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/generate-json").then()
                .statusCode(200).contentType(ContentType.JSON).body("theme", equalTo("minimal"));
    }

    // ========== GET /api/calendar/themes TESTS ==========

    @Test
    void testThemes_ReturnsThemeMap() {
        given().when().get("/api/calendar/themes").then().statusCode(200).contentType(ContentType.JSON).body("$",
                not(empty()));
    }

    @Test
    void testThemes_ContainsDefaultTheme() {
        given().when().get("/api/calendar/themes").then().statusCode(200).contentType(ContentType.JSON).body("default",
                notNullValue());
    }

    // ========== GET /api/calendar/emoji-preview TESTS ==========

    @Test
    void testEmojiPreview_DefaultEmoji() {
        given().when().get("/api/calendar/emoji-preview").then().statusCode(200).contentType(CONTENT_TYPE_SVG)
                .body(containsString("<svg"));
    }

    @Test
    void testEmojiPreview_SpecificEmoji() {
        // Use an emoji that is known to exist in the Noto emoji set (Christmas tree)
        given().queryParam("emoji", "🎄").when().get("/api/calendar/emoji-preview").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG);
    }

    @Test
    void testEmojiPreview_WithSize() {
        // Note: The endpoint doesn't have a size parameter, but emoji param works
        given().queryParam("emoji", "🎄").when().get("/api/calendar/emoji-preview").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG);
    }

    @Test
    void testEmojiPreview_MonochromeFont() {
        // Use 'style' param (not 'font') and an emoji known to exist
        given().queryParam("emoji", "🎄").queryParam("style", "noto-mono").when().get("/api/calendar/emoji-preview")
                .then().statusCode(200).contentType(CONTENT_TYPE_SVG);
    }

    // ========== POST /api/calendar/generate-pdf TESTS ==========

    @Test
    void testGeneratePdf_DefaultYear() {
        Map<String, Object> request = new HashMap<>();

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/generate-pdf").then()
                .statusCode(200).contentType(CONTENT_TYPE_PDF);
    }

    @Test
    void testGeneratePdf_SpecificYear() {
        Map<String, Object> request = new HashMap<>();
        request.put("year", TEST_YEAR);

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/generate-pdf").then()
                .statusCode(200).contentType(CONTENT_TYPE_PDF);
    }

    @Test
    void testGeneratePdf_WithTheme() {
        Map<String, Object> request = new HashMap<>();
        request.put("year", TEST_YEAR);
        request.put("theme", "colorful");

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/generate-pdf").then()
                .statusCode(200).contentType(CONTENT_TYPE_PDF);
    }

    // ========== POST /api/calendar/svg-to-pdf TESTS ==========

    @Test
    void testSvgToPdf_ValidSvg() {
        Map<String, Object> request = new HashMap<>();
        request.put("svgContent",
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\"><rect x=\"0\" y=\"0\" width=\"100\" height=\"100\" fill=\"red\"/></svg>");

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/svg-to-pdf").then()
                .statusCode(200).contentType(CONTENT_TYPE_PDF);
    }

    @Test
    void testSvgToPdf_WithYearAndSvgContent() {
        // svg-to-pdf requires either svgContent or regenerateConfig
        Map<String, Object> request = new HashMap<>();
        request.put("year", TEST_YEAR);
        request.put("svgContent", "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\"><text>Test "
                + TEST_YEAR + "</text></svg>");

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/svg-to-pdf").then()
                .statusCode(200).contentType(CONTENT_TYPE_PDF);
    }

    @Test
    void testSvgToPdf_EmptyRequest() {
        given().contentType(ContentType.JSON).body("{}").when().post("/api/calendar/svg-to-pdf").then()
                .statusCode(anyOf(is(200), is(400)));
    }

    // ========== GET /api/calendar/holidays TESTS ==========

    @Test
    void testHolidays_ReturnsHolidayList() {
        given().queryParam("year", TEST_YEAR).when().get("/api/calendar/holidays").then().statusCode(200)
                .contentType(ContentType.JSON);
    }

    @Test
    void testHolidays_WithHolidaySet() {
        given().queryParam("year", TEST_YEAR).queryParam("holidaySet", "us-federal").when()
                .get("/api/calendar/holidays").then().statusCode(200).contentType(ContentType.JSON);
    }

    @Test
    void testHolidays_WithJewishHolidaySet() {
        given().queryParam("year", TEST_YEAR).queryParam("holidaySet", "jewish").when().get("/api/calendar/holidays")
                .then().statusCode(200).contentType(ContentType.JSON);
    }

    @Test
    void testHolidays_WithChristianHolidaySet() {
        given().queryParam("year", TEST_YEAR).queryParam("holidaySet", "christian").when().get("/api/calendar/holidays")
                .then().statusCode(200).contentType(ContentType.JSON);
    }

    @Test
    void testHolidays_WithIslamicHolidaySet() {
        given().queryParam("year", TEST_YEAR).queryParam("holidaySet", "islamic").when().get("/api/calendar/holidays")
                .then().statusCode(200).contentType(ContentType.JSON);
    }

    // ========== EDGE CASES ==========

    @Test
    void testGenerate_LeapYear() {
        Map<String, Object> request = new HashMap<>();
        request.put("year", 2024); // Leap year

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/generate").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG).body(containsString("2024"));
    }

    @Test
    void testGenerate_FarFutureYear() {
        Map<String, Object> request = new HashMap<>();
        request.put("year", 2050);

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/generate").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG).body(containsString("2050"));
    }

    @Test
    void testGenerate_AllBooleanOptionsDisabled() {
        Map<String, Object> request = new HashMap<>();
        request.put("year", TEST_YEAR);
        request.put("showWeekNumbers", false);
        request.put("compactMode", false);
        request.put("showDayNames", false);
        request.put("showDayNumbers", false);
        request.put("showGrid", false);
        request.put("highlightWeekends", false);
        request.put("rotateMonthNames", false);

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/generate").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG);
    }

    @Test
    void testGenerate_AllBooleanOptionsEnabled() {
        Map<String, Object> request = new HashMap<>();
        request.put("year", TEST_YEAR);
        request.put("showWeekNumbers", true);
        request.put("compactMode", true);
        request.put("showDayNames", true);
        request.put("showDayNumbers", true);
        request.put("showGrid", true);
        request.put("highlightWeekends", true);
        request.put("rotateMonthNames", true);

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar/generate").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG);
    }
}
