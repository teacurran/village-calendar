package villagecompute.calendar.services;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Unit tests for CustomEventDisplay. Tests constructors and helper methods for display settings. */
class CustomEventDisplayTest {

    private static final String TEST_EMOJI = "\uD83C\uDF89"; // party emoji
    private static final String EMOJI_SIZE_KEY = "emojiSize";
    private static final String EMOJI_X_KEY = "emojiX";
    private static final String EMOJI_Y_KEY = "emojiY";
    private static final String TEXT_SIZE_KEY = "textSize";
    private static final String TEXT_X_KEY = "textX";
    private static final String TEXT_Y_KEY = "textY";
    private static final String TEXT_ROTATION_KEY = "textRotation";
    private static final String TEXT_COLOR_KEY = "textColor";
    private static final String TEXT_ALIGN_KEY = "textAlign";
    private static final String TEXT_BOLD_KEY = "textBold";
    private static final String TEXT_WRAP_KEY = "textWrap";
    private static final double DOUBLE_TOLERANCE = 0.001;

    private CustomEventDisplay createDisplayWithSetting(String key, Object value) {
        Map<String, Object> settings = new HashMap<>();
        settings.put(key, value);
        return new CustomEventDisplay(TEST_EMOJI, settings);
    }

    private CustomEventDisplay createDisplayWithEmoji() {
        return new CustomEventDisplay(TEST_EMOJI);
    }

    // ========== CONSTRUCTOR TESTS ==========

    @Test
    void testDefaultConstructor() {
        CustomEventDisplay display = new CustomEventDisplay();

        assertNull(display.emoji);
        assertNotNull(display.displaySettings);
        assertTrue(display.displaySettings.isEmpty());
    }

    @Test
    void testConstructorWithEmoji() {
        CustomEventDisplay display = createDisplayWithEmoji();

        assertEquals(TEST_EMOJI, display.emoji);
        assertNotNull(display.displaySettings);
        assertTrue(display.displaySettings.isEmpty());
    }

    @Test
    void testConstructorWithEmojiAndSettings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put(EMOJI_SIZE_KEY, 24);
        settings.put(TEXT_BOLD_KEY, true);

        CustomEventDisplay display = new CustomEventDisplay("\uD83C\uDF84", settings);

        assertEquals("\uD83C\uDF84", display.emoji);
        assertEquals(2, display.displaySettings.size());
        assertEquals(24, display.displaySettings.get(EMOJI_SIZE_KEY));
        assertEquals(true, display.displaySettings.get(TEXT_BOLD_KEY));
    }

    @Test
    void testConstructorWithNullSettings() {
        CustomEventDisplay display = new CustomEventDisplay("\uD83C\uDF83", null);

        assertEquals("\uD83C\uDF83", display.emoji);
        assertNotNull(display.displaySettings);
        assertTrue(display.displaySettings.isEmpty());
    }

    // ========== EMOJI SIZE/POSITION TESTS ==========

    @Test
    void testGetEmojiSize_WithSetting() {
        CustomEventDisplay display = createDisplayWithSetting(EMOJI_SIZE_KEY, 32);

        assertEquals(32, display.getEmojiSize(16));
    }

    @Test
    void testGetEmojiSize_WithoutSetting_ReturnsDefault() {
        CustomEventDisplay display = createDisplayWithEmoji();

        assertEquals(16, display.getEmojiSize(16));
    }

    @Test
    void testGetEmojiX_WithSetting() {
        CustomEventDisplay display = createDisplayWithSetting(EMOJI_X_KEY, 100.5);

        assertEquals(100.5, display.getEmojiX(50.0), DOUBLE_TOLERANCE);
    }

    @Test
    void testGetEmojiX_WithoutSetting_ReturnsDefault() {
        CustomEventDisplay display = createDisplayWithEmoji();

        assertEquals(50.0, display.getEmojiX(50.0), DOUBLE_TOLERANCE);
    }

    @Test
    void testGetEmojiY_WithSetting() {
        CustomEventDisplay display = createDisplayWithSetting(EMOJI_Y_KEY, 75.25);

        assertEquals(75.25, display.getEmojiY(25.0), DOUBLE_TOLERANCE);
    }

    @Test
    void testGetEmojiY_WithoutSetting_ReturnsDefault() {
        CustomEventDisplay display = createDisplayWithEmoji();

        assertEquals(25.0, display.getEmojiY(25.0), DOUBLE_TOLERANCE);
    }

    // ========== TEXT SIZE/POSITION TESTS ==========

    @Test
    void testGetTextSize_WithSetting() {
        CustomEventDisplay display = createDisplayWithSetting(TEXT_SIZE_KEY, 18);

        assertEquals(18, display.getTextSize(12));
    }

    @Test
    void testGetTextSize_WithoutSetting_ReturnsDefault() {
        CustomEventDisplay display = createDisplayWithEmoji();

        assertEquals(12, display.getTextSize(12));
    }

    @Test
    void testGetTextX_WithSetting() {
        CustomEventDisplay display = createDisplayWithSetting(TEXT_X_KEY, 200.0);

        assertEquals(200.0, display.getTextX(100.0), DOUBLE_TOLERANCE);
    }

    @Test
    void testGetTextX_WithoutSetting_ReturnsDefault() {
        CustomEventDisplay display = createDisplayWithEmoji();

        assertEquals(100.0, display.getTextX(100.0), DOUBLE_TOLERANCE);
    }

    @Test
    void testGetTextY_WithSetting() {
        CustomEventDisplay display = createDisplayWithSetting(TEXT_Y_KEY, 150.5);

        assertEquals(150.5, display.getTextY(75.0), DOUBLE_TOLERANCE);
    }

    @Test
    void testGetTextY_WithoutSetting_ReturnsDefault() {
        CustomEventDisplay display = createDisplayWithEmoji();

        assertEquals(75.0, display.getTextY(75.0), DOUBLE_TOLERANCE);
    }

    // ========== TEXT ROTATION TESTS ==========

    @Test
    void testGetTextRotation_WithSetting() {
        CustomEventDisplay display = createDisplayWithSetting(TEXT_ROTATION_KEY, 45.0);

        assertEquals(45.0, display.getTextRotation(), DOUBLE_TOLERANCE);
    }

    @Test
    void testGetTextRotation_WithoutSetting_ReturnsZero() {
        CustomEventDisplay display = createDisplayWithEmoji();

        assertEquals(0.0, display.getTextRotation(), DOUBLE_TOLERANCE);
    }

    // ========== TEXT COLOR/ALIGN TESTS ==========

    @Test
    void testGetTextColor_WithSetting() {
        CustomEventDisplay display = createDisplayWithSetting(TEXT_COLOR_KEY, "#FF0000");

        assertEquals("#FF0000", display.getTextColor("#000000"));
    }

    @Test
    void testGetTextColor_WithoutSetting_ReturnsDefault() {
        CustomEventDisplay display = createDisplayWithEmoji();

        assertEquals("#000000", display.getTextColor("#000000"));
    }

    @Test
    void testGetTextAlign_WithSetting() {
        CustomEventDisplay display = createDisplayWithSetting(TEXT_ALIGN_KEY, "center");

        assertEquals("center", display.getTextAlign("left"));
    }

    @Test
    void testGetTextAlign_WithoutSetting_ReturnsDefault() {
        CustomEventDisplay display = createDisplayWithEmoji();

        assertEquals("left", display.getTextAlign("left"));
    }

    // ========== BOOLEAN FLAG TESTS ==========

    @Test
    void testIsTextBold_True() {
        CustomEventDisplay display = createDisplayWithSetting(TEXT_BOLD_KEY, true);

        assertTrue(display.isTextBold());
    }

    @Test
    void testIsTextBold_False() {
        CustomEventDisplay display = createDisplayWithSetting(TEXT_BOLD_KEY, false);

        assertFalse(display.isTextBold());
    }

    @Test
    void testIsTextBold_NotSet_ReturnsFalse() {
        CustomEventDisplay display = createDisplayWithEmoji();

        assertFalse(display.isTextBold());
    }

    @Test
    void testIsTextWrap_True() {
        CustomEventDisplay display = createDisplayWithSetting(TEXT_WRAP_KEY, true);

        assertTrue(display.isTextWrap());
    }

    @Test
    void testIsTextWrap_False() {
        CustomEventDisplay display = createDisplayWithSetting(TEXT_WRAP_KEY, false);

        assertFalse(display.isTextWrap());
    }

    @Test
    void testIsTextWrap_NotSet_ReturnsFalse() {
        CustomEventDisplay display = createDisplayWithEmoji();

        assertFalse(display.isTextWrap());
    }

    // ========== NUMBER TYPE HANDLING TESTS ==========

    @Test
    void testGetEmojiSize_WithIntegerType() {
        CustomEventDisplay display = createDisplayWithSetting(EMOJI_SIZE_KEY, Integer.valueOf(24));

        assertEquals(24, display.getEmojiSize(16));
    }

    @Test
    void testGetEmojiSize_WithLongType() {
        CustomEventDisplay display = createDisplayWithSetting(EMOJI_SIZE_KEY, Long.valueOf(24L));

        assertEquals(24, display.getEmojiSize(16));
    }

    @Test
    void testGetEmojiSize_WithDoubleType() {
        CustomEventDisplay display = createDisplayWithSetting(EMOJI_SIZE_KEY, Double.valueOf(24.7));

        assertEquals(24, display.getEmojiSize(16));
    }
}
