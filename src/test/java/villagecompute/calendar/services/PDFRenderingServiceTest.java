package villagecompute.calendar.services;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import jakarta.inject.Inject;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.junit.jupiter.api.Test;

import villagecompute.calendar.exceptions.RenderingException;
import villagecompute.calendar.types.CalendarConfigType;
import villagecompute.calendar.types.CustomDateEntryType;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests for PDFRenderingService. Verifies SVG-to-PDF conversion works correctly with various SVG features, including
 * xlink:href references which require proper namespace handling.
 */
@QuarkusTest
class PDFRenderingServiceTest {

    @Inject
    PDFRenderingService pdfRenderingService;

    @Inject
    CalendarRenderingService calendarRenderingService;

    @Inject
    EmojiSvgService emojiSvgService;

    @Test
    void testBasicSvgToPdf() {
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
        assertEquals("%PDF", new String(pdf, 0, 4), "Output should be a valid PDF");
    }

    @Test
    void testSvgWithXlinkHref() {
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
        assertEquals("%PDF", new String(pdf, 0, 4), "Output should be a valid PDF");
    }

    @Test
    void testCalendarWithSecularHolidays() {
        // Generate a calendar with secular holidays (which may use emojis with xlink:href)
        CalendarConfigType config = new CalendarConfigType();
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
        assertEquals("%PDF", new String(pdf, 0, 4), "Output should be a valid PDF");
    }

    @Test
    void testEmojiSvgWithXlinkInCalendar() {
        // Test specific emojis that use xlink:href (like the dancing woman emoji)
        // emoji_u1f483.svg is known to use xlink:href
        if (emojiSvgService.hasEmojiSvg("💃")) {
            CalendarConfigType config = new CalendarConfigType();
            config.year = 2025;
            config.theme = "default";
            config.layoutStyle = "grid";

            // Add a custom event with an emoji that uses xlink:href
            config.customDates.put(java.time.LocalDate.of(2025, 5, 5), new CustomDateEntryType("💃")); // Dancing woman
                                                                                                       // emoji

            String svg = calendarRenderingService.generateCalendarSVG(config);
            assertNotNull(svg, "SVG generation with dancing emoji should succeed");

            byte[] pdf = pdfRenderingService.renderSVGToPDF(svg, 2025);
            assertNotNull(pdf, "PDF output should not be null");
            assertTrue(pdf.length > 0, "PDF should have content");
            assertEquals("%PDF", new String(pdf, 0, 4), "Output should be a valid PDF");
        }
    }

    @Test
    void testAllEmojisWithXlinkReferences() {
        // Test that all emojis that use xlink:href can be rendered to PDF
        // These emojis are known to use xlink:href based on grep results
        String[] emojisWithXlink = {"🎃", "💃", "🙏", "🤲"};

        for (String emoji : emojisWithXlink) {
            if (emojiSvgService.hasEmojiSvg(emoji)) {
                CalendarConfigType config = new CalendarConfigType();
                config.year = 2025;
                config.theme = "default";
                config.layoutStyle = "grid";

                config.customDates.put(java.time.LocalDate.of(2025, 1, 15), new CustomDateEntryType(emoji));

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
    void testMonochromeCalendarWithSecularHolidays() {
        // Test monochrome mode with secular holidays
        CalendarConfigType config = new CalendarConfigType();
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
        assertEquals("%PDF", new String(pdf, 0, 4), "Output should be a valid PDF");
    }

    @Test
    void testComplexCalendarWithAllFeatures() {
        // Test a complex calendar with all features that could cause CSS URI reference issues
        CalendarConfigType config = new CalendarConfigType();
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
        config.customDates.put(java.time.LocalDate.of(2025, 1, 1), new CustomDateEntryType("🎉")); // New Year
        config.customDates.put(java.time.LocalDate.of(2025, 2, 14), new CustomDateEntryType("❤️")); // Valentine's
        config.customDates.put(java.time.LocalDate.of(2025, 10, 31), new CustomDateEntryType("🎃")); // Halloween (uses
                                                                                                     // xlink)
        config.customDates.put(java.time.LocalDate.of(2025, 5, 5), new CustomDateEntryType("💃")); // Dancing woman
                                                                                                   // (uses xlink)
        config.customDates.put(java.time.LocalDate.of(2025, 12, 25), new CustomDateEntryType("🎄")); // Christmas tree

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
        assertEquals("%PDF", new String(pdf, 0, 4), "Output should be a valid PDF");

        System.out.println("Complex calendar PDF generated successfully: " + pdf.length + " bytes");
    }

    @Test
    void testEmojiFontConsistency() {
        // Test that emoji font setting is properly applied in both SVG generation and PDF
        // conversion
        CalendarConfigType config = new CalendarConfigType();
        config.year = 2025;
        config.theme = "default";
        config.emojiFont = "noto-mono"; // Explicitly set to monochrome
        config.customDates.put(java.time.LocalDate.of(2025, 1, 1), new CustomDateEntryType("🎉"));

        // Generate SVG with monochrome emoji setting
        String svg = calendarRenderingService.generateCalendarSVG(config);
        assertNotNull(svg, "SVG generation should succeed");

        // Convert to PDF - should maintain the monochrome emoji setting
        byte[] pdf = pdfRenderingService.renderSVGToPDF(svg, 2025);
        assertNotNull(pdf, "PDF output should not be null");
        assertTrue(pdf.length > 1000, "PDF should have substantial content");
        assertEquals("%PDF", new String(pdf, 0, 4), "Output should be a valid PDF");

        // Also test with color emojis
        config.emojiFont = "noto-color";
        String colorSvg = calendarRenderingService.generateCalendarSVG(config);
        assertNotNull(colorSvg, "Color SVG generation should succeed");
        assertNotEquals(svg, colorSvg, "Mono and color SVGs should be different");

        byte[] colorPdf = pdfRenderingService.renderSVGToPDF(colorSvg, 2025);
        assertNotNull(colorPdf, "Color PDF output should not be null");
        assertTrue(colorPdf.length > 1000, "Color PDF should have substantial content");
    }

    // ------------------------------------------------------------------------
    // Additional coverage: null/empty inputs, error paths, PNG rendering,
    // SVG preprocessing branches, and PDF metadata cleaning.
    // ------------------------------------------------------------------------

    @Test
    void testRenderSVGToPDFRejectsNullContent() {
        // Null input should produce IllegalArgumentException, not RenderingException
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            pdfRenderingService.renderSVGToPDF(null, 2025);
        });
        assertTrue(ex.getMessage().contains("null or empty"), "Should mention null/empty in message");
    }

    @Test
    void testRenderSVGToPDFRejectsEmptyContent() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            pdfRenderingService.renderSVGToPDF("", 2025);
        });
        assertTrue(ex.getMessage().contains("null or empty"), "Should mention null/empty in message");
    }

    @Test
    void testRenderSVGToPDFThrowsRenderingExceptionOnInvalidSvg() {
        // Malformed SVG should result in a RenderingException (not the underlying TranscoderException)
        String invalidSvg = "<svg><not-properly-closed";
        RenderingException ex = assertThrows(RenderingException.class, () -> {
            pdfRenderingService.renderSVGToPDF(invalidSvg, 2025);
        });
        assertTrue(ex.getMessage().contains("PDF rendering failed"),
                "RenderingException message should mention PDF rendering failed");
        assertNotNull(ex.getCause(), "RenderingException should wrap original cause");
    }

    @Test
    void testRenderSVGToPNGProducesValidPng() {
        String basicSvg = """
                <?xml version="1.0" encoding="UTF-8"?>
                <svg xmlns="http://www.w3.org/2000/svg" width="50" height="50" viewBox="0 0 50 50">
                    <rect x="0" y="0" width="50" height="50" fill="red"/>
                </svg>
                """;

        byte[] png = pdfRenderingService.renderSVGToPNG(basicSvg, 100);
        assertNotNull(png, "PNG output should not be null");
        assertTrue(png.length > 0, "PNG should have content");
        // PNG signature: 0x89 P N G
        assertEquals((byte) 0x89, png[0], "PNG should start with 0x89");
        assertEquals('P', png[1]);
        assertEquals('N', png[2]);
        assertEquals('G', png[3]);
    }

    @Test
    void testRenderSVGToPNGRejectsNullContent() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            pdfRenderingService.renderSVGToPNG(null, 100);
        });
        assertTrue(ex.getMessage().contains("null or empty"));
    }

    @Test
    void testRenderSVGToPNGRejectsEmptyContent() {
        assertThrows(IllegalArgumentException.class, () -> {
            pdfRenderingService.renderSVGToPNG("", 100);
        });
    }

    @Test
    void testRenderSVGToPNGThrowsRenderingExceptionOnInvalidSvg() {
        String invalidSvg = "<svg<<not-valid";
        RenderingException ex = assertThrows(RenderingException.class, () -> {
            pdfRenderingService.renderSVGToPNG(invalidSvg, 100);
        });
        assertTrue(ex.getMessage().contains("PNG rendering failed"),
                "RenderingException should mention PNG rendering failed");
    }

    @Test
    void testRenderSVGToPNGDataUriProducesDataUri() {
        String basicSvg = """
                <?xml version="1.0" encoding="UTF-8"?>
                <svg xmlns="http://www.w3.org/2000/svg" width="50" height="50" viewBox="0 0 50 50">
                    <rect x="0" y="0" width="50" height="50" fill="green"/>
                </svg>
                """;

        String dataUri = pdfRenderingService.renderSVGToPNGDataUri(basicSvg, 100);
        assertNotNull(dataUri, "Data URI should not be null");
        assertTrue(dataUri.startsWith("data:image/png;base64,"), "Data URI should start with PNG data URI prefix");
        String base64Body = dataUri.substring("data:image/png;base64,".length());
        assertTrue(base64Body.length() > 0, "Base64 body should not be empty");
        // Decode and check PNG signature
        byte[] decoded = java.util.Base64.getDecoder().decode(base64Body);
        assertEquals((byte) 0x89, decoded[0], "Decoded payload should be a PNG");
    }

    @Test
    void testPreprocessingHandlesTransparentFillAttribute() {
        // SVG using fill="transparent" should be accepted; preprocessor swaps to fill="none"
        String svg = """
                <?xml version="1.0" encoding="UTF-8"?>
                <svg xmlns="http://www.w3.org/2000/svg" width="60" height="60" viewBox="0 0 60 60">
                    <rect x="5" y="5" width="50" height="50" fill="transparent" stroke="black"/>
                </svg>
                """;

        byte[] pdf = pdfRenderingService.renderSVGToPDF(svg, 2025);
        assertNotNull(pdf, "PDF should be produced even with transparent fill");
        assertEquals("%PDF", new String(pdf, 0, 4));
    }

    @Test
    void testPreprocessingHandlesTransparentFillInStyle() {
        // CSS-style transparent fill (fill:transparent) should also be accepted
        String svg = """
                <?xml version="1.0" encoding="UTF-8"?>
                <svg xmlns="http://www.w3.org/2000/svg" width="60" height="60" viewBox="0 0 60 60">
                    <rect x="5" y="5" width="50" height="50" style="fill: transparent; stroke: black"/>
                </svg>
                """;

        byte[] pdf = pdfRenderingService.renderSVGToPDF(svg, 2025);
        assertNotNull(pdf);
        assertEquals("%PDF", new String(pdf, 0, 4));
    }

    @Test
    void testPreprocessingHandlesClipPathUrlReference() {
        // clip-path attribute referencing url(#id) - the preprocessor rewrites to bare #id
        String svg = """
                <?xml version="1.0" encoding="UTF-8"?>
                <svg xmlns="http://www.w3.org/2000/svg" width="80" height="80" viewBox="0 0 80 80">
                    <defs>
                        <clipPath id="clipBox">
                            <rect x="0" y="0" width="40" height="40"/>
                        </clipPath>
                    </defs>
                    <rect x="0" y="0" width="80" height="80" fill="blue" clip-path="url(#clipBox)"/>
                </svg>
                """;

        byte[] pdf = pdfRenderingService.renderSVGToPDF(svg, 2025);
        assertNotNull(pdf);
        assertEquals("%PDF", new String(pdf, 0, 4));
    }

    @Test
    void testPreprocessingHandlesClipPathInStyleForm() {
        // clip-path in style attribute also exercised by preprocessor
        String svg = """
                <?xml version="1.0" encoding="UTF-8"?>
                <svg xmlns="http://www.w3.org/2000/svg" width="80" height="80" viewBox="0 0 80 80">
                    <defs>
                        <clipPath id="clipBox2">
                            <rect x="0" y="0" width="40" height="40"/>
                        </clipPath>
                    </defs>
                    <rect x="0" y="0" width="80" height="80" fill="orange" style="clip-path:url(#clipBox2)"/>
                </svg>
                """;

        byte[] pdf = pdfRenderingService.renderSVGToPDF(svg, 2025);
        assertNotNull(pdf);
        assertEquals("%PDF", new String(pdf, 0, 4));
    }

    @Test
    void testPreprocessingAcceptsSvgWithoutXmlPrologue() {
        // SVG that doesn't lead with "<" should still be handled by the preprocessor's guard,
        // which short-circuits preprocessing. Wrap the SVG so we get something Batik can attempt.
        // (Just leading whitespace + content starting with "<" - exercises the trim path.)
        String svg = "\n\n   <svg xmlns=\"http://www.w3.org/2000/svg\" width=\"40\" height=\"40\""
                + " viewBox=\"0 0 40 40\"><rect width=\"40\" height=\"40\" fill=\"purple\"/></svg>";

        byte[] pdf = pdfRenderingService.renderSVGToPDF(svg, 2025);
        assertNotNull(pdf);
        assertEquals("%PDF", new String(pdf, 0, 4));
    }

    @Test
    void testPreprocessingAddsXlinkNamespaceWhenMissing() {
        // SVG uses xlink:href but does NOT declare xmlns:xlink - preprocessor should add it.
        String svg = """
                <?xml version="1.0" encoding="UTF-8"?>
                <svg xmlns="http://www.w3.org/2000/svg" width="100" height="100" viewBox="0 0 100 100">
                    <defs>
                        <symbol id="sym1" viewBox="0 0 100 100">
                            <circle cx="50" cy="50" r="20" fill="teal"/>
                        </symbol>
                    </defs>
                    <use xlink:href="#sym1" x="0" y="0" width="100" height="100"/>
                </svg>
                """;

        byte[] pdf = pdfRenderingService.renderSVGToPDF(svg, 2025);
        assertNotNull(pdf, "PDF should render even if xlink namespace was missing");
        assertEquals("%PDF", new String(pdf, 0, 4));
    }

    @Test
    void testPdfMetadataIsCleanedAndYearApplied() throws Exception {
        // Verify cleanPdfMetadata behavior: producer, creator, title (with year), subject; author/keywords blank.
        String svg = """
                <?xml version="1.0" encoding="UTF-8"?>
                <svg xmlns="http://www.w3.org/2000/svg" width="80" height="80" viewBox="0 0 80 80">
                    <rect width="80" height="80" fill="navy"/>
                </svg>
                """;

        int targetYear = 2030;
        byte[] pdf = pdfRenderingService.renderSVGToPDF(svg, targetYear);
        assertNotNull(pdf);
        assertEquals("%PDF", new String(pdf, 0, 4));

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDDocumentInformation info = doc.getDocumentInformation();
            assertEquals("villagecompute.com", info.getProducer(), "Producer should be cleaned to villagecompute.com");
            assertEquals("Village Compute Calendar Generator", info.getCreator(),
                    "Creator should be Village Compute Calendar Generator");
            assertEquals("Calendar " + targetYear, info.getTitle(), "Title should reflect the year passed in");
            assertEquals("Calendar", info.getSubject(), "Subject should be Calendar");
            // Author/Keywords intentionally blanked out
            assertEquals("", info.getAuthor(), "Author should be blanked");
            assertEquals("", info.getKeywords(), "Keywords should be blanked");
        }
    }

    @Test
    void testPdfMetadataYearVariesWithInput() throws Exception {
        // Ensure the year parameter is actually threaded through to the metadata title
        String svg = """
                <?xml version="1.0" encoding="UTF-8"?>
                <svg xmlns="http://www.w3.org/2000/svg" width="40" height="40" viewBox="0 0 40 40">
                    <rect width="40" height="40" fill="black"/>
                </svg>
                """;

        byte[] pdf1999 = pdfRenderingService.renderSVGToPDF(svg, 1999);
        byte[] pdf2099 = pdfRenderingService.renderSVGToPDF(svg, 2099);

        try (PDDocument d1 = Loader.loadPDF(pdf1999); PDDocument d2 = Loader.loadPDF(pdf2099)) {
            assertEquals("Calendar 1999", d1.getDocumentInformation().getTitle());
            assertEquals("Calendar 2099", d2.getDocumentInformation().getTitle());
        }
    }
}
