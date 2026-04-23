package villagecompute.calendar.services;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for EmojiSvgService covering all branches including monochrome mode, color options, SVG cache hits/misses,
 * and fallback behaviors.
 */
class EmojiSvgServiceTest {

    private EmojiSvgService emojiSvgService;

    // Test SVG content
    private static final String TEST_SVG_CONTENT = "<circle cx=\"64\" cy=\"64\" r=\"50\"/>";
    private static final String TEST_VIEWBOX = "0 0 128 128";
    private static final String TEST_MONO_SVG_CONTENT = "<path d=\"M10,10 L100,100\"/>";
    private static final String TEST_MONO_VIEWBOX = "0 0 100 100";

    @BeforeEach
    void setUp() {
        emojiSvgService = new EmojiSvgService();
        // Initialize the service (loads from resources)
        emojiSvgService.init();
    }

    // ========== hasEmojiSvg(String emoji) Tests ==========

    @Test
    void testHasEmojiSvg_WithExistingEmoji_ReturnsTrue() {
        // Christmas tree emoji should be available
        assertTrue(emojiSvgService.hasEmojiSvg("🎄"));
    }

    @Test
    void testHasEmojiSvg_WithNonExistentEmoji_ReturnsFalse() {
        assertFalse(emojiSvgService.hasEmojiSvg("🦆")); // Duck not in mapping
    }

    @Test
    void testHasEmojiSvg_WithEmojiWithVS16_NormalizesAndFinds() {
        // Test emoji with VS16 (variation selector) - should be normalized
        assertTrue(emojiSvgService.hasEmojiSvg("☀️")); // Sun with VS16
    }

    @Test
    void testHasEmojiSvg_OnlyInColorCache_ReturnsTrue() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();

        // Add only to color cache
        getColorSvgCache(testService).put("coloronly", TEST_SVG_CONTENT);

        assertTrue(testService.hasEmojiSvg("coloronly"));
    }

    @Test
    void testHasEmojiSvg_OnlyInMonoCache_ReturnsTrue() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();

        // Add only to mono cache
        getMonoSvgCache(testService).put("monoonly", TEST_MONO_SVG_CONTENT);

        assertTrue(testService.hasEmojiSvg("monoonly"));
    }

    @Test
    void testHasEmojiSvg_InNeitherCache_ReturnsFalse() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();

        // Don't add to any cache
        assertFalse(testService.hasEmojiSvg("nonexistent"));
    }

    // ========== hasEmojiSvg(String emoji, boolean monochrome) Tests ==========

    @Test
    void testHasEmojiSvg_MonochromeTrue_ChecksMonoCache() throws Exception {
        // Set up a controlled test environment
        EmojiSvgService testService = createServiceWithTestCaches();

        // Add only to mono cache
        getMonoSvgCache(testService).put("test", TEST_MONO_SVG_CONTENT);

        assertTrue(testService.hasEmojiSvg("test", true));
        assertFalse(testService.hasEmojiSvg("test", false));
    }

    @Test
    void testHasEmojiSvg_MonochromeFalse_ChecksColorCache() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();

        // Add only to color cache
        getColorSvgCache(testService).put("test", TEST_SVG_CONTENT);

        assertTrue(testService.hasEmojiSvg("test", false));
        assertFalse(testService.hasEmojiSvg("test", true));
    }

    @Test
    void testHasEmojiSvg_BothCaches_BothReturnTrue() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();

        // Add to both caches
        getColorSvgCache(testService).put("test", TEST_SVG_CONTENT);
        getMonoSvgCache(testService).put("test", TEST_MONO_SVG_CONTENT);

        assertTrue(testService.hasEmojiSvg("test", false));
        assertTrue(testService.hasEmojiSvg("test", true));
    }

    // ========== getStandaloneSvg Tests ==========

    @Test
    void testGetStandaloneSvg_ColorMode_ReturnsColorSvg() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();
        getColorSvgCache(testService).put("test", TEST_SVG_CONTENT);
        getColorViewBoxCache(testService).put("test", TEST_VIEWBOX);

        String result = testService.getStandaloneSvg("test", false, null);

        assertNotNull(result);
        assertTrue(result.contains(TEST_SVG_CONTENT));
        assertTrue(result.contains("viewBox=\"" + TEST_VIEWBOX + "\""));
        assertTrue(result.contains("<svg xmlns=\"http://www.w3.org/2000/svg\""));
    }

    @Test
    void testGetStandaloneSvg_MonochromeMode_ReturnsMonoSvg() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();
        getMonoSvgCache(testService).put("test", TEST_MONO_SVG_CONTENT);
        getMonoViewBoxCache(testService).put("test", TEST_MONO_VIEWBOX);

        String result = testService.getStandaloneSvg("test", true, null);

        assertNotNull(result);
        assertTrue(result.contains(TEST_MONO_SVG_CONTENT));
        assertTrue(result.contains("viewBox=\"" + TEST_MONO_VIEWBOX + "\""));
    }

    @Test
    void testGetStandaloneSvg_MonochromeMode_FallsBackToColorWhenMonoMissing() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();
        // Only add to color cache, not mono
        getColorSvgCache(testService).put("test", TEST_SVG_CONTENT);
        getColorViewBoxCache(testService).put("test", TEST_VIEWBOX);

        String result = testService.getStandaloneSvg("test", true, null);

        assertNotNull(result);
        assertTrue(result.contains(TEST_SVG_CONTENT)); // Should fall back to color
    }

    @Test
    void testGetStandaloneSvg_WithColorHex_AddsColorFill() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();
        getMonoSvgCache(testService).put("test", TEST_MONO_SVG_CONTENT);
        getMonoViewBoxCache(testService).put("test", TEST_MONO_VIEWBOX);

        String result = testService.getStandaloneSvg("test", true, "#DC2626");

        assertNotNull(result);
        assertTrue(result.contains("fill=\"#DC2626\""));
        assertTrue(result.contains("<g fill="));
    }

    @Test
    void testGetStandaloneSvg_WithColorHexWithoutHash_AddsHash() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();
        getMonoSvgCache(testService).put("test", TEST_MONO_SVG_CONTENT);
        getMonoViewBoxCache(testService).put("test", TEST_MONO_VIEWBOX);

        String result = testService.getStandaloneSvg("test", true, "DC2626");

        assertNotNull(result);
        assertTrue(result.contains("fill=\"#DC2626\"")); // Should add #
    }

    @Test
    void testGetStandaloneSvg_ColorHexWithColorMode_IgnoresColorHex() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();
        getColorSvgCache(testService).put("test", TEST_SVG_CONTENT);
        getColorViewBoxCache(testService).put("test", TEST_VIEWBOX);

        // colorHex should be ignored when monochrome=false
        String result = testService.getStandaloneSvg("test", false, "#DC2626");

        assertNotNull(result);
        assertFalse(result.contains("fill=\"#DC2626\"")); // Should NOT have fill
    }

    @Test
    void testGetStandaloneSvg_NonExistentEmoji_ReturnsNull() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();

        String result = testService.getStandaloneSvg("nonexistent", false, null);

        assertNull(result);
    }

    @Test
    void testGetStandaloneSvg_EmptyColorHex_IgnoresColorHex() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();
        getMonoSvgCache(testService).put("test", TEST_MONO_SVG_CONTENT);
        getMonoViewBoxCache(testService).put("test", TEST_MONO_VIEWBOX);

        String result = testService.getStandaloneSvg("test", true, "");

        assertNotNull(result);
        assertFalse(result.contains("<g fill=")); // Empty colorHex should be ignored
    }

    // ========== getEmojiAsSvg Tests ==========

    @Test
    void testGetEmojiAsSvg_BasicCall_ReturnsColorSvg() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();
        getColorSvgCache(testService).put("test", TEST_SVG_CONTENT);
        getColorViewBoxCache(testService).put("test", TEST_VIEWBOX);

        String result = testService.getEmojiAsSvg("test", 10, 20, 50);

        assertNotNull(result);
        assertTrue(result.contains("x=\"10.0\""));
        assertTrue(result.contains("y=\"20.0\""));
        assertTrue(result.contains("width=\"50.0\""));
        assertTrue(result.contains("height=\"50.0\""));
    }

    @Test
    void testGetEmojiAsSvg_MonochromeWithMonoSvg_UsesMonoSvg() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();
        getMonoSvgCache(testService).put("test", TEST_MONO_SVG_CONTENT);
        getMonoViewBoxCache(testService).put("test", TEST_MONO_VIEWBOX);
        getColorSvgCache(testService).put("test", TEST_SVG_CONTENT);

        String result = testService.getEmojiAsSvg("test", 10, 20, 50, true);

        assertNotNull(result);
        assertTrue(result.contains(TEST_MONO_SVG_CONTENT));
        // Should NOT have grayscale filter since mono SVG exists
        assertFalse(result.contains("feColorMatrix"));
    }

    @Test
    void testGetEmojiAsSvg_MonochromeWithoutMonoSvg_UsesGrayscaleFilter() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();
        // Only add color SVG, no mono
        getColorSvgCache(testService).put("test", TEST_SVG_CONTENT);
        getColorViewBoxCache(testService).put("test", TEST_VIEWBOX);

        String result = testService.getEmojiAsSvg("test", 10, 20, 50, true);

        assertNotNull(result);
        assertTrue(result.contains(TEST_SVG_CONTENT));
        // Should have grayscale filter as fallback
        assertTrue(result.contains("feColorMatrix type=\"saturate\" values=\"0\""));
        assertTrue(result.contains("filter=\"url(#"));
    }

    @Test
    void testGetEmojiAsSvg_WithColorHex_AddsColorFill() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();
        getMonoSvgCache(testService).put("test", TEST_MONO_SVG_CONTENT);
        getMonoViewBoxCache(testService).put("test", TEST_MONO_VIEWBOX);

        String result = testService.getEmojiAsSvg("test", 10, 20, 50, true, "#FF0000");

        assertNotNull(result);
        assertTrue(result.contains("fill=\"#FF0000\""));
        assertTrue(result.contains("<g"));
    }

    @Test
    void testGetEmojiAsSvg_ColorHexWithoutHash_AddsHash() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();
        getMonoSvgCache(testService).put("test", TEST_MONO_SVG_CONTENT);
        getMonoViewBoxCache(testService).put("test", TEST_MONO_VIEWBOX);

        String result = testService.getEmojiAsSvg("test", 10, 20, 50, true, "00FF00");

        assertNotNull(result);
        assertTrue(result.contains("fill=\"#00FF00\""));
    }

    @Test
    void testGetEmojiAsSvg_ColorHexWithNonMonochrome_IgnoresColorHex() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();
        getColorSvgCache(testService).put("test", TEST_SVG_CONTENT);
        getColorViewBoxCache(testService).put("test", TEST_VIEWBOX);

        String result = testService.getEmojiAsSvg("test", 10, 20, 50, false, "#FF0000");

        assertNotNull(result);
        assertFalse(result.contains("fill=\"#FF0000\"")); // Should ignore colorHex
    }

    @Test
    void testGetEmojiAsSvg_NonExistentEmoji_ReturnsNull() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();

        String result = testService.getEmojiAsSvg("nonexistent", 10, 20, 50);

        assertNull(result);
    }

    @Test
    void testGetEmojiAsSvg_IncludesXlinkNamespace() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();
        getColorSvgCache(testService).put("test", TEST_SVG_CONTENT);
        getColorViewBoxCache(testService).put("test", TEST_VIEWBOX);

        String result = testService.getEmojiAsSvg("test", 10, 20, 50);

        assertNotNull(result);
        assertTrue(result.contains("xmlns:xlink=\"http://www.w3.org/1999/xlink\""));
    }

    // ========== ID Uniqueness Tests ==========

    @Test
    void testGetEmojiAsSvg_MakesIdsUnique() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();
        String svgWithIds = "<defs><linearGradient id=\"grad1\"></linearGradient></defs>"
                + "<rect fill=\"url(#grad1)\"/>";
        getColorSvgCache(testService).put("test", svgWithIds);
        getColorViewBoxCache(testService).put("test", TEST_VIEWBOX);

        String result = testService.getEmojiAsSvg("test", 10, 20, 50);

        assertNotNull(result);
        // ID should be prefixed
        assertTrue(result.contains("id=\"e10_20_grad1\""));
        // Reference should also be updated
        assertTrue(result.contains("url(#e10_20_grad1)"));
    }

    @Test
    void testGetEmojiAsSvg_UpdatesXlinkHrefReferences() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();
        String svgWithXlink = "<defs><symbol id=\"mySymbol\"></symbol></defs>" + "<use xlink:href=\"#mySymbol\"/>";
        getColorSvgCache(testService).put("test", svgWithXlink);
        getColorViewBoxCache(testService).put("test", TEST_VIEWBOX);

        String result = testService.getEmojiAsSvg("test", 10, 20, 50);

        assertNotNull(result);
        assertTrue(result.contains("xlink:href=\"#e10_20_mySymbol\""));
    }

    @Test
    void testGetEmojiAsSvg_UpdatesHrefReferences() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();
        String svgWithHref = "<defs><symbol id=\"mySymbol2\"></symbol></defs>" + "<use href=\"#mySymbol2\"/>";
        getColorSvgCache(testService).put("test", svgWithHref);
        getColorViewBoxCache(testService).put("test", TEST_VIEWBOX);

        String result = testService.getEmojiAsSvg("test", 10, 20, 50);

        assertNotNull(result);
        assertTrue(result.contains("href=\"#e10_20_mySymbol2\""));
    }

    // ========== Emoji Normalization Tests ==========

    @Test
    void testEmojiNormalization_RemovesVS16() {
        // Sun emoji with VS16 should be found
        assertTrue(emojiSvgService.hasEmojiSvg("☀️")); // With VS16
        assertTrue(emojiSvgService.hasEmojiSvg("☀")); // Without VS16
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"🎆", "🏛️", "❤️", "☘️", "🐰", "🇺🇸"})
    void testHasEmojiSvg_CommonEmojis_ReturnsTrue(String emoji) {
        assertTrue(emojiSvgService.hasEmojiSvg(emoji));
    }

    // ========== getAvailableEmojis Tests ==========

    @Test
    void testGetAvailableEmojis_ReturnsCombinedSet() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();
        getColorSvgCache(testService).put("color1", TEST_SVG_CONTENT);
        getColorSvgCache(testService).put("both", TEST_SVG_CONTENT);
        getMonoSvgCache(testService).put("mono1", TEST_MONO_SVG_CONTENT);
        getMonoSvgCache(testService).put("both", TEST_MONO_SVG_CONTENT);

        Set<String> available = testService.getAvailableEmojis();

        assertTrue(available.contains("color1"));
        assertTrue(available.contains("mono1"));
        assertTrue(available.contains("both"));
        assertEquals(3, available.size()); // "both" should only appear once
    }

    @Test
    void testGetAvailableEmojis_WithRealService_ReturnsNonEmpty() {
        Set<String> available = emojiSvgService.getAvailableEmojis();

        assertFalse(available.isEmpty());
        // Should have many emojis loaded
        assertTrue(available.size() > 20);
    }

    // ========== Default ViewBox Tests ==========

    @Test
    void testGetEmojiAsSvg_MissingViewBox_UsesDefault() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();
        getColorSvgCache(testService).put("test", TEST_SVG_CONTENT);
        // Don't set viewBox - should use default

        String result = testService.getEmojiAsSvg("test", 10, 20, 50);

        assertNotNull(result);
        assertTrue(result.contains("viewBox=\"0 0 128 128\"")); // Default viewBox
    }

    // ========== Integration Tests with Real Resources ==========

    @Test
    void testGetEmojiAsSvg_ChristmasTree_ReturnsValidSvg() {
        String result = emojiSvgService.getEmojiAsSvg("🎄", 0, 0, 100);

        assertNotNull(result);
        assertTrue(result.startsWith("<svg"));
        assertTrue(result.endsWith("</svg>"));
    }

    @Test
    void testGetStandaloneSvg_ChristmasTree_ReturnsValidSvg() {
        String result = emojiSvgService.getStandaloneSvg("🎄", false, null);

        assertNotNull(result);
        assertTrue(result.startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\""));
        assertTrue(result.endsWith("</svg>"));
    }

    @Test
    void testGetStandaloneSvg_ChristmasTree_Monochrome_ReturnsValidSvg() {
        String result = emojiSvgService.getStandaloneSvg("🎄", true, null);

        assertNotNull(result);
        assertTrue(result.startsWith("<svg"));
    }

    @ParameterizedTest
    @CsvSource({"#FF0000, FF0000", "#00FF00, 00FF00", "#0000FF, 0000FF", "#FFFFFF, FFFFFF", "#000000, 000000"})
    void testGetStandaloneSvg_VariousColorHexFormats(String withHash, String withoutHash) {
        // Both formats should produce same fill color
        String result1 = emojiSvgService.getStandaloneSvg("🎄", true, withHash);
        String result2 = emojiSvgService.getStandaloneSvg("🎄", true, withoutHash);

        assertNotNull(result1);
        assertNotNull(result2);
        assertTrue(result1.contains("fill=\"" + withHash + "\""));
        assertTrue(result2.contains("fill=\"" + withHash + "\""));
    }

    // ========== Position and Size Tests ==========

    @ParameterizedTest
    @CsvSource({"0, 0, 10", "100, 200, 50", "50.5, 75.5, 25", "-10, -20, 100"})
    void testGetEmojiAsSvg_VariousPositionsAndSizes(double x, double y, double size) {
        String result = emojiSvgService.getEmojiAsSvg("🎄", x, y, size);

        assertNotNull(result);
        assertTrue(result.contains(String.format("x=\"%.1f\"", x)));
        assertTrue(result.contains(String.format("y=\"%.1f\"", y)));
        assertTrue(result.contains(String.format("width=\"%.1f\"", size)));
        assertTrue(result.contains(String.format("height=\"%.1f\"", size)));
    }

    // ========== Grayscale Filter ID Tests ==========

    @Test
    void testGetEmojiAsSvg_GrayscaleFilter_HasUniqueFilterId() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();
        getColorSvgCache(testService).put("test", TEST_SVG_CONTENT);
        getColorViewBoxCache(testService).put("test", TEST_VIEWBOX);

        String result1 = testService.getEmojiAsSvg("test", 10, 20, 50, true);
        String result2 = testService.getEmojiAsSvg("test", 30, 40, 50, true);

        assertNotNull(result1);
        assertNotNull(result2);
        // Filter IDs should be different based on position
        assertTrue(result1.contains("id=\"e10_20_grayscale\""));
        assertTrue(result2.contains("id=\"e30_40_grayscale\""));
    }

    // ========== extractSvgInnerContent Branch Coverage Tests ==========

    @Test
    void testExtractSvgInnerContent_NoSvgTag_ReturnsOriginal() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();
        String input = "<div>no svg here</div>";

        String result = invokeExtractSvgInnerContent(testService, input);

        assertEquals(input, result);
    }

    @Test
    void testExtractSvgInnerContent_NoClosingBracket_ReturnsOriginal() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();
        String input = "<svg viewBox=\"0 0 100 100\" this tag never closes";

        String result = invokeExtractSvgInnerContent(testService, input);

        assertEquals(input, result);
    }

    @Test
    void testExtractSvgInnerContent_NoClosingSvgTag_ReturnsOriginal() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();
        String input = "<svg viewBox=\"0 0 100 100\"><circle/>";

        String result = invokeExtractSvgInnerContent(testService, input);

        assertEquals(input, result);
    }

    @Test
    void testExtractSvgInnerContent_ValidSvg_ReturnsInnerContent() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();
        String input = "<svg viewBox=\"0 0 100 100\"><circle cx=\"50\" cy=\"50\" r=\"40\"/></svg>";

        String result = invokeExtractSvgInnerContent(testService, input);

        assertEquals("<circle cx=\"50\" cy=\"50\" r=\"40\"/>", result);
    }

    @Test
    void testExtractSvgInnerContent_ValidSvgWithWhitespace_ReturnsTrimmedContent() throws Exception {
        EmojiSvgService testService = createServiceWithTestCaches();
        String input = "<svg viewBox=\"0 0 100 100\">   <circle/>   </svg>";

        String result = invokeExtractSvgInnerContent(testService, input);

        assertEquals("<circle/>", result);
    }

    private String invokeExtractSvgInnerContent(EmojiSvgService service, String input) throws Exception {
        java.lang.reflect.Method method = EmojiSvgService.class.getDeclaredMethod("extractSvgInnerContent",
                String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, input);
    }

    // ========== Init Method Branch Coverage Tests ==========

    @Test
    void testInit_ColorSvgNotFound_HandlesGracefully() throws Exception {
        // Add a fake entry to EMOJI_TO_FILENAME that points to a non-existent file
        String testEmoji = "🧪"; // Test tube emoji - not in the real mapping
        String fakeFilename = "emoji_nonexistent_test_file";

        Map<String, String> emojiToFilename = getEmojiToFilenameMap();
        // Store original state
        String originalValue = emojiToFilename.get(testEmoji);

        try {
            // Add test entry pointing to non-existent file
            emojiToFilename.put(testEmoji, fakeFilename);

            // Create new service and init
            EmojiSvgService testService = new EmojiSvgService();
            testService.init();

            // The emoji should NOT be in the cache because the file doesn't exist
            assertFalse(testService.hasEmojiSvg(testEmoji, false),
                    "Emoji with non-existent color SVG should not be in color cache");
        } finally {
            // Clean up - restore original state
            if (originalValue == null) {
                emojiToFilename.remove(testEmoji);
            } else {
                emojiToFilename.put(testEmoji, originalValue);
            }
        }
    }

    @Test
    void testInit_MonoSvgNotFound_HandlesGracefully() throws Exception {
        // Test that mono SVG missing is handled gracefully
        // We can verify this by checking an emoji that exists in color but not mono
        EmojiSvgService testService = new EmojiSvgService();
        testService.init();

        // Get the caches to check their sizes
        Map<String, String> colorCache = getColorSvgCache(testService);

        // There should be some emojis where color exists but mono doesn't
        // (or vice versa) - the service should handle this gracefully
        assertFalse(colorCache.isEmpty(), "Color cache should have entries");
        // Mono cache may have fewer entries than color cache
        // The important thing is that init() completed without throwing
    }

    @Test
    void testInit_BothSvgsNotFound_HandlesGracefully() throws Exception {
        String testEmoji = "🔬"; // Microscope - not in the real mapping
        String fakeFilename = "emoji_completely_fake_file";

        Map<String, String> emojiToFilename = getEmojiToFilenameMap();
        String originalValue = emojiToFilename.get(testEmoji);

        try {
            emojiToFilename.put(testEmoji, fakeFilename);

            EmojiSvgService testService = new EmojiSvgService();
            testService.init();

            // Neither cache should have the emoji
            assertFalse(testService.hasEmojiSvg(testEmoji, false),
                    "Emoji with non-existent color SVG should not be in color cache");
            assertFalse(testService.hasEmojiSvg(testEmoji, true),
                    "Emoji with non-existent mono SVG should not be in mono cache");
        } finally {
            if (originalValue == null) {
                emojiToFilename.remove(testEmoji);
            } else {
                emojiToFilename.put(testEmoji, originalValue);
            }
        }
    }

    // ========== Helper Methods ==========

    @SuppressWarnings("unchecked")
    private Map<String, String> getEmojiToFilenameMap() throws Exception {
        Field field = EmojiSvgService.class.getDeclaredField("EMOJI_TO_FILENAME");
        field.setAccessible(true);
        return (Map<String, String>) field.get(null);
    }

    private EmojiSvgService createServiceWithTestCaches() throws Exception {
        EmojiSvgService service = new EmojiSvgService();
        // Don't call init() - we'll populate caches manually
        return service;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getColorSvgCache(EmojiSvgService service) throws Exception {
        Field field = EmojiSvgService.class.getDeclaredField("colorSvgCache");
        field.setAccessible(true);
        return (Map<String, String>) field.get(service);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getColorViewBoxCache(EmojiSvgService service) throws Exception {
        Field field = EmojiSvgService.class.getDeclaredField("colorViewBoxCache");
        field.setAccessible(true);
        return (Map<String, String>) field.get(service);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getMonoSvgCache(EmojiSvgService service) throws Exception {
        Field field = EmojiSvgService.class.getDeclaredField("monoSvgCache");
        field.setAccessible(true);
        return (Map<String, String>) field.get(service);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getMonoViewBoxCache(EmojiSvgService service) throws Exception {
        Field field = EmojiSvgService.class.getDeclaredField("monoViewBoxCache");
        field.setAccessible(true);
        return (Map<String, String>) field.get(service);
    }
}
