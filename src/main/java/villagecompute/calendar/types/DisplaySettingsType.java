package villagecompute.calendar.types;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Type for custom event display settings. Controls how emojis and text are positioned and styled in calendar cells.
 */
@JsonIgnoreProperties(
        ignoreUnknown = true)
public class DisplaySettingsType {
    @JsonProperty("emojiSize")
    public Integer emojiSize;

    @JsonProperty("emojiX")
    public Double emojiX;

    @JsonProperty("emojiY")
    public Double emojiY;

    @JsonProperty("textSize")
    public Integer textSize;

    @JsonProperty("textX")
    public Double textX;

    @JsonProperty("textY")
    public Double textY;

    @JsonProperty("textRotation")
    public Double textRotation;

    @JsonProperty("textColor")
    public String textColor;

    @JsonProperty("textAlign")
    public String textAlign;

    @JsonProperty("textBold")
    public Boolean textBold;

    @JsonProperty("textWrap")
    public Boolean textWrap;

    public DisplaySettingsType() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        DisplaySettingsType that = (DisplaySettingsType) o;
        return Objects.equals(emojiSize, that.emojiSize) && Objects.equals(emojiX, that.emojiX)
                && Objects.equals(emojiY, that.emojiY) && Objects.equals(textSize, that.textSize)
                && Objects.equals(textX, that.textX) && Objects.equals(textY, that.textY)
                && Objects.equals(textRotation, that.textRotation) && Objects.equals(textColor, that.textColor)
                && Objects.equals(textAlign, that.textAlign) && Objects.equals(textBold, that.textBold)
                && Objects.equals(textWrap, that.textWrap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(emojiSize, emojiX, emojiY, textSize, textX, textY, textRotation, textColor, textAlign,
                textBold, textWrap);
    }
}
