package villagecompute.calendar.services;

import java.util.HashMap;
import java.util.Map;

public class CustomEventDisplay {
    public String emoji;
    public Map<String, Object> displaySettings = new HashMap<>();

    public CustomEventDisplay() {}

    public CustomEventDisplay(String emoji) {
        this.emoji = emoji;
    }

    public CustomEventDisplay(String emoji, Map<String, Object> displaySettings) {
        this.emoji = emoji;
        this.displaySettings = displaySettings != null ? displaySettings : new HashMap<>();
    }

    // Helper methods for accessing display settings
    public int getEmojiSize(int defaultSize) {
        return displaySettings.containsKey("emojiSize")
                ? ((Number) displaySettings.get("emojiSize")).intValue()
                : defaultSize;
    }

    public double getEmojiX(double defaultX) {
        return displaySettings.containsKey("emojiX")
                ? ((Number) displaySettings.get("emojiX")).doubleValue()
                : defaultX;
    }

    public double getEmojiY(double defaultY) {
        return displaySettings.containsKey("emojiY")
                ? ((Number) displaySettings.get("emojiY")).doubleValue()
                : defaultY;
    }

    public int getTextSize(int defaultSize) {
        return displaySettings.containsKey("textSize")
                ? ((Number) displaySettings.get("textSize")).intValue()
                : defaultSize;
    }

    public double getTextX(double defaultX) {
        return displaySettings.containsKey("textX")
                ? ((Number) displaySettings.get("textX")).doubleValue()
                : defaultX;
    }

    public double getTextY(double defaultY) {
        return displaySettings.containsKey("textY")
                ? ((Number) displaySettings.get("textY")).doubleValue()
                : defaultY;
    }

    public double getTextRotation() {
        return displaySettings.containsKey("textRotation")
                ? ((Number) displaySettings.get("textRotation")).doubleValue()
                : 0;
    }

    public String getTextColor(String defaultColor) {
        return displaySettings.containsKey("textColor")
                ? (String) displaySettings.get("textColor")
                : defaultColor;
    }

    public String getTextAlign(String defaultAlign) {
        return displaySettings.containsKey("textAlign")
                ? (String) displaySettings.get("textAlign")
                : defaultAlign;
    }

    public boolean isTextBold() {
        return displaySettings.containsKey("textBold")
                ? (Boolean) displaySettings.get("textBold")
                : false;
    }

    public boolean isTextWrap() {
        return displaySettings.containsKey("textWrap")
                ? (Boolean) displaySettings.get("textWrap")
                : false;
    }
}
