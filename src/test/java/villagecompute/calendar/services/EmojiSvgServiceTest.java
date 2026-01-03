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

    // Test emoji
    private static final String TEST_EMOJI = "🎄"; // Christmas tree
    private static final String TEST_EMOJI_NORMALIZED = "🎄"; // Same (no VS16)
    private static final String TEST_EMOJI_WITH_VS16 = "☀️"; // Has VS16
    private static final String TEST_EMOJI_WITHOUT_VS16 = "☀"; // Without VS16

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

    // ========== Helper Methods ==========

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
