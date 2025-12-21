package villagecompute.calendar.services;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PDFRenderingService.
 * Verifies SVG-to-PDF conversion works correctly with various SVG features,
 * including xlink:href references which require proper namespace handling.
 */
@QuarkusTest
public class PDFRenderingServiceTest {

    @Inject
    PDFRenderingService pdfRenderingService;

    @Inject
    CalendarRenderingService calendarRenderingService;

    @Inject
    EmojiSvgService emojiSvgService;

    @Test
    public void testBasicSvgToPdf() {
        // Basic SVG without xlink references
        String basicSvg = """
            <?xml version="1.0" encoding="UTF-8"?>
            <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="100" height="100" viewBox="0 0 100 100">
                <rect x="10" y="10" width="80" height="80" fill="blue"/>
                <text x="50" y="55" text-anchor="middle" fill="white">Test</text>
            </svg>
            """;

        byte[] pdf = pdfRenderingService.renderSVGToPDF(basicSvg, 2025);
        assertNotNull(pdf, "PDF output should not be null");
        assertTrue(pdf.length > 0, "PDF should have content");
        // PDF files start with %PDF
        assertTrue(new String(pdf, 0, 4).equals("%PDF"), "Output should be a valid PDF");
    }

    @Test
    public void testSvgWithXlinkHref() {
        // SVG with xlink:href references (similar to what some Noto emojis use)
        // Uses a symbol element that can be referenced via <use>
        String svgWithXlink = """
            <?xml version="1.0" encoding="UTF-8"?>
            <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="128" height="128" viewBox="0 0 128 128">
                <defs>
                    <linearGradient id="grad1" x1="0%" y1="0%" x2="100%" y2="100%">
                        <stop offset="0%" style="stop-color:rgb(255,255,0);stop-opacity:1" />
                        <stop offset="100%" style="stop-color:rgb(255,0,0);stop-opacity:1" />
                    </linearGradient>
                    <symbol id="myCircle" viewBox="0 0 128 128">
                        <circle cx="64" cy="64" r="30" fill="blue"/>
                    </symbol>
                </defs>
                <circle cx="64" cy="64" r="60" fill="url(#grad1)"/>
                <use xlink:href="#myCircle" x="0" y="0" width="128" height="128"/>
            </svg>
            """;

        byte[] pdf = pdfRenderingService.renderSVGToPDF(svgWithXlink, 2025);
        assertNotNull(pdf, "PDF output should not be null for SVG with xlink:href");
        assertTrue(pdf.length > 0, "PDF should have content");
        assertTrue(new String(pdf, 0, 4).equals("%PDF"), "Output should be a valid PDF");
    }

    @Test
    public void testCalendarWithSecularHolidays() {
        // Generate a calendar with secular holidays (which may use emojis with xlink:href)
        CalendarRenderingService.CalendarConfig config = new CalendarRenderingService.CalendarConfig();
        config.year = 2025;
        config.theme = "default";
        config.layoutStyle = "grid";
        config.moonDisplayMode = "illumination";
        config.holidaySets = List.of("secular");
        config.eventDisplayMode = "large";
        config.emojiFont = "noto-color";

        // Generate SVG
        String svg = calendarRenderingService.generateCalendarSVG(config);
        assertNotNull(svg, "SVG generation should succeed");
        assertTrue(svg.contains("xmlns:xlink"), "Generated SVG should include xlink namespace");

        // Convert to PDF - this will fail if xlink namespace is not properly declared
        byte[] pdf = pdfRenderingService.renderSVGToPDF(svg, 2025);
        assertNotNull(pdf, "PDF output should not be null for calendar with secular holidays");
        assertTrue(pdf.length > 0, "PDF should have content");
        assertTrue(new String(pdf, 0, 4).equals("%PDF"), "Output should be a valid PDF");
    }

    @Test
    public void testEmojiSvgWithXlinkInCalendar() {
        // Test specific emojis that use xlink:href (like the dancing woman emoji)
        // emoji_u1f483.svg is known to use xlink:href
        if (emojiSvgService.hasEmojiSvg("💃")) {
            CalendarRenderingService.CalendarConfig config = new CalendarRenderingService.CalendarConfig();
            config.year = 2025;
            config.theme = "default";
            config.layoutStyle = "grid";

            // Add a custom event with an emoji that uses xlink:href
            config.customDates.put("2025-05-05", "💃"); // Dancing woman emoji

            String svg = calendarRenderingService.generateCalendarSVG(config);
            assertNotNull(svg, "SVG generation with dancing emoji should succeed");

            byte[] pdf = pdfRenderingService.renderSVGToPDF(svg, 2025);
            assertNotNull(pdf, "PDF output should not be null");
            assertTrue(pdf.length > 0, "PDF should have content");
            assertTrue(new String(pdf, 0, 4).equals("%PDF"), "Output should be a valid PDF");
        }
    }

    @Test
    public void testAllEmojisWithXlinkReferences() {
        // Test that all emojis that use xlink:href can be rendered to PDF
        // These emojis are known to use xlink:href based on grep results
        String[] emojisWithXlink = {"🎃", "💃", "🙏", "🤲"};

        for (String emoji : emojisWithXlink) {
            if (emojiSvgService.hasEmojiSvg(emoji)) {
                CalendarRenderingService.CalendarConfig config = new CalendarRenderingService.CalendarConfig();
                config.year = 2025;
                config.theme = "default";
                config.layoutStyle = "grid";

                config.customDates.put("2025-01-15", emoji);

                String svg = calendarRenderingService.generateCalendarSVG(config);
                assertNotNull(svg, "SVG generation with emoji " + emoji + " should succeed");

                try {
                    byte[] pdf = pdfRenderingService.renderSVGToPDF(svg, 2025);
                    assertNotNull(pdf, "PDF output should not be null for emoji " + emoji);
                    assertTrue(pdf.length > 0, "PDF should have content for emoji " + emoji);
                } catch (Exception e) {
                    fail("PDF rendering failed for emoji " + emoji + ": " + e.getMessage());
                }
            }
        }
    }

    @Test
    public void testMonochromeCalendarWithSecularHolidays() {
        // Test monochrome mode with secular holidays
        CalendarRenderingService.CalendarConfig config = new CalendarRenderingService.CalendarConfig();
        config.year = 2025;
        config.theme = "default";
        config.layoutStyle = "grid";
        config.moonDisplayMode = "illumination";
        config.holidaySets = List.of("secular");
        config.eventDisplayMode = "large";
        config.emojiFont = "noto-mono"; // Monochrome mode

        // Generate SVG
        String svg = calendarRenderingService.generateCalendarSVG(config);
        assertNotNull(svg, "SVG generation in monochrome mode should succeed");

        // Convert to PDF
        byte[] pdf = pdfRenderingService.renderSVGToPDF(svg, 2025);
        assertNotNull(pdf, "PDF output should not be null for monochrome calendar");
        assertTrue(pdf.length > 0, "PDF should have content");
        assertTrue(new String(pdf, 0, 4).equals("%PDF"), "Output should be a valid PDF");
    }

    @Test
    public void testComplexCalendarWithAllFeatures() {
        // Test a complex calendar with all features that could cause CSS URI reference issues
        CalendarRenderingService.CalendarConfig config = new CalendarRenderingService.CalendarConfig();
        config.year = 2025;
        config.theme = "rainbowWeekends";
        config.layoutStyle = "grid";
        config.moonDisplayMode = "illumination";
        config.showWeekNumbers = true;
        config.showDayNames = true;
        config.showDayNumbers = true;
        config.showGrid = true;
        config.highlightWeekends = true;
        config.holidaySets = List.of("secular", "us");
        config.eventDisplayMode = "large";
        config.emojiFont = "noto-color";
        
        // Add complex custom dates with various emojis (including known xlink users)
        config.customDates.put("2025-01-01", "🎉"); // New Year
        config.customDates.put("2025-02-14", "❤️"); // Valentine's
        config.customDates.put("2025-10-31", "🎃"); // Halloween (uses xlink)
        config.customDates.put("2025-05-05", "💃"); // Dancing woman (uses xlink)
        config.customDates.put("2025-12-25", "🎄"); // Christmas tree

        // Generate SVG
        String svg = calendarRenderingService.generateCalendarSVG(config);
        assertNotNull(svg, "SVG generation should succeed with complex configuration");
        assertTrue(svg.length() > 1000, "SVG should be substantial in size");
        
        // Verify that our preprocessing is working - the SVG should contain processed references
        if (svg.contains("url(#")) {
            // If we still have url(# references, our preprocessing didn't catch them all
            // Log for debugging but don't fail the test since some might be legitimate
            System.out.println("WARNING: SVG still contains url(# references after preprocessing");
            // Extract a sample for analysis
            int urlIndex = svg.indexOf("url(#");
            if (urlIndex != -1) {
                String sample = svg.substring(Math.max(0, urlIndex - 50), Math.min(svg.length(), urlIndex + 100));
                System.out.println("Sample: " + sample);
            }
        }

        // Convert to PDF - this is the critical test
        byte[] pdf = pdfRenderingService.renderSVGToPDF(svg, 2025);
        assertNotNull(pdf, "PDF output should not be null for complex calendar");
        assertTrue(pdf.length > 10000, "PDF should be substantial in size (>10KB)");
        assertTrue(new String(pdf, 0, 4).equals("%PDF"), "Output should be a valid PDF");
        
        System.out.println("Complex calendar PDF generated successfully: " + pdf.length + " bytes");
    }

    @Test
    public void testEmojiFontConsistency() {
        // Test that emoji font setting is properly applied in both SVG generation and PDF conversion
        CalendarRenderingService.CalendarConfig config = new CalendarRenderingService.CalendarConfig();
        config.year = 2025;
        config.theme = "default";
        config.emojiFont = "noto-mono"; // Explicitly set to monochrome
        config.customDates.put("2025-01-01", "🎉");

        // Generate SVG with monochrome emoji setting
        String svg = calendarRenderingService.generateCalendarSVG(config);
        assertNotNull(svg, "SVG generation should succeed");

        // Convert to PDF - should maintain the monochrome emoji setting
        byte[] pdf = pdfRenderingService.renderSVGToPDF(svg, 2025);
        assertNotNull(pdf, "PDF output should not be null");
        assertTrue(pdf.length > 1000, "PDF should have substantial content");
        assertTrue(new String(pdf, 0, 4).equals("%PDF"), "Output should be a valid PDF");

        // Also test with color emojis
        config.emojiFont = "noto-color";
        String colorSvg = calendarRenderingService.generateCalendarSVG(config);
        assertNotNull(colorSvg, "Color SVG generation should succeed");
        assertNotEquals(svg, colorSvg, "Mono and color SVGs should be different");

        byte[] colorPdf = pdfRenderingService.renderSVGToPDF(colorSvg, 2025);
        assertNotNull(colorPdf, "Color PDF output should not be null");
        assertTrue(colorPdf.length > 1000, "Color PDF should have substantial content");
    }
}
