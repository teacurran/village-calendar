package villagecompute.calendar.types;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Type for custom date entries in calendar configuration. Each entry represents a custom event/date with emoji and
 * optional display settings.
 */
@JsonIgnoreProperties(
        ignoreUnknown = true)
public class CustomDateEntryType {
    @JsonProperty("emoji")
    public String emoji;

    @JsonProperty("emojiFont")
    public String emojiFont;

    @JsonProperty("title")
    public String title;

    @JsonProperty("displaySettings")
    public DisplaySettingsType displaySettings;

    public CustomDateEntryType() {
    }

    public CustomDateEntryType(String emoji) {
        this.emoji = emoji;
    }

    public CustomDateEntryType(String emoji, String title) {
        this.emoji = emoji;
        this.title = title;
    }

    public CustomDateEntryType(String emoji, String title, DisplaySettingsType displaySettings) {
        this.emoji = emoji;
        this.title = title;
        this.displaySettings = displaySettings;
    }

    public CustomDateEntryType(String emoji, DisplaySettingsType displaySettings) {
        this.emoji = emoji;
        this.displaySettings = displaySettings;
    }

    // Helper methods for accessing display settings with defaults
    public int getEmojiSize(int defaultSize) {
        return displaySettings != null && displaySettings.emojiSize != null ? displaySettings.emojiSize : defaultSize;
    }

    public double getEmojiX(double defaultX) {
        return displaySettings != null && displaySettings.emojiX != null ? displaySettings.emojiX : defaultX;
    }

    public double getEmojiY(double defaultY) {
        return displaySettings != null && displaySettings.emojiY != null ? displaySettings.emojiY : defaultY;
    }

    public int getTextSize(int defaultSize) {
        return displaySettings != null && displaySettings.textSize != null ? displaySettings.textSize : defaultSize;
    }

    public double getTextX(double defaultX) {
        return displaySettings != null && displaySettings.textX != null ? displaySettings.textX : defaultX;
    }

    public double getTextY(double defaultY) {
        return displaySettings != null && displaySettings.textY != null ? displaySettings.textY : defaultY;
    }

    public double getTextRotation() {
        return displaySettings != null && displaySettings.textRotation != null ? displaySettings.textRotation : 0;
    }

    public String getTextColor(String defaultColor) {
        return displaySettings != null && displaySettings.textColor != null ? displaySettings.textColor : defaultColor;
    }

    public String getTextAlign(String defaultAlign) {
        return displaySettings != null && displaySettings.textAlign != null ? displaySettings.textAlign : defaultAlign;
    }

    public boolean isTextBold() {
        return displaySettings != null && Boolean.TRUE.equals(displaySettings.textBold);
    }

    public boolean isTextWrap() {
        return displaySettings != null && Boolean.TRUE.equals(displaySettings.textWrap);
    }

    public String getEmojiFont(String defaultFont) {
        return emojiFont != null ? emojiFont : defaultFont;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        CustomDateEntryType that = (CustomDateEntryType) o;
        return Objects.equals(emoji, that.emoji) && Objects.equals(emojiFont, that.emojiFont)
                && Objects.equals(title, that.title) && Objects.equals(displaySettings, that.displaySettings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(emoji, emojiFont, title, displaySettings);
    }
}
