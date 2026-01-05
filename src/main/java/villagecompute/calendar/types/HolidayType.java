package villagecompute.calendar.types;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Type for holiday entries in calendar configuration. Each entry represents a holiday with name and optional emoji.
 */
@JsonIgnoreProperties(
        ignoreUnknown = true)
public class HolidayType {
    @JsonProperty("name")
    public String name;

    @JsonProperty("emoji")
    public String emoji;

    public HolidayType() {
    }

    public HolidayType(String name) {
        this.name = name;
    }

    public HolidayType(String name, String emoji) {
        this.name = name;
        this.emoji = emoji;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        HolidayType that = (HolidayType) o;
        return Objects.equals(name, that.name) && Objects.equals(emoji, that.emoji);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, emoji);
    }
}
