package villagecompute.calendar.types;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import villagecompute.calendar.util.Colors;

/**
 * Calendar configuration type used for JSON serialization/deserialization. This class contains all configuration
 * options for calendar rendering.
 */
@JsonIgnoreProperties(
        ignoreUnknown = true)
public class CalendarConfigType {
    public int year = LocalDate.now().getYear();
    public String theme = "default";

    @JsonProperty("moonDisplayMode")
    public String moonDisplayMode = "none"; // "none", "illumination", "phases", "full-only"

    public boolean showWeekNumbers = false;
    public boolean compactMode = false;
    public boolean showDayNames = true;
    public boolean showDayNumbers = true;
    public boolean showGrid = true;
    public boolean highlightWeekends = true;
    public boolean rotateMonthNames = false;
    public double latitude = 0; // Default: No location (no rotation)
    public double longitude = 0; // Default: No location (no rotation)

    @JsonProperty("observationTime")
    public LocalTime observationTime = LocalTime.of(20, 0); // Default: 8:00 PM for moon calculations

    public String timeZone = "America/New_York"; // Time zone for calculations
    public int moonSize = 20; // Default moon size in pixels (full-size preset)
    public int moonOffsetX = 25; // Horizontal offset in pixels from left edge of cell
    public int moonOffsetY = 36; // Vertical offset in pixels from top edge of cell
    public String moonBorderColor = "#666666"; // Default border color
    public double moonBorderWidth = 1.5; // Default border width (full-size preset)

    // Color customization
    public String yearColor = null; // Will use theme default if null
    public String monthColor = null; // Will use theme default if null
    public String dayTextColor = null; // Will use theme default if null
    public String dayNameColor = null; // Will use theme default if null
    public String gridLineColor = Colors.GRAY_400; // Default grid color
    public String weekendBgColor = null; // Will use theme default if null
    public String holidayColor = "#ff5252"; // Default holiday color
    public String customDateColor = Colors.GREEN; // Default custom date color
    public String moonDarkColor = "#dddddd"; // Default moon dark side
    public String moonLightColor = "#FFFFFF"; // Default moon light side

    public String emojiPosition = "bottom-left"; // Position of emojis in calendar cells

    @JsonProperty("customDates")
    public Map<LocalDate, CustomDateEntryType> customDates = new HashMap<>(); // date -> custom date entry

    @JsonProperty("holidays")
    public Map<LocalDate, HolidayType> holidays = new HashMap<>(); // date -> holiday entry

    @JsonProperty("holidaySets")
    public List<String> holidaySets = new ArrayList<>(); // List of holiday set IDs to include

    public String eventDisplayMode = "large"; // "large", "large-text", "small", "small-text", "text", or "none" - for
                                              // holidays
    public String customEventDisplayMode = "large-text"; // Display mode for personal/custom events (same options as
                                                         // eventDisplayMode)
    public String emojiFont = "noto-color"; // "noto-color", "noto-mono", or "mono-{color}" for colored monochrome
    public String locale = "en-US";
    public DayOfWeek firstDayOfWeek = DayOfWeek.SUNDAY;
    public String layoutStyle = "grid"; // "grid" for 12x31 layout, "weekday-grid" for weekday-aligned layout

    public CalendarConfigType() {
    }

    /**
     * Copy constructor for creating a new config from an existing one.
     */
    public CalendarConfigType(CalendarConfigType other) {
        this.year = other.year;
        this.theme = other.theme;
        this.moonDisplayMode = other.moonDisplayMode;
        this.showWeekNumbers = other.showWeekNumbers;
        this.compactMode = other.compactMode;
        this.showDayNames = other.showDayNames;
        this.showDayNumbers = other.showDayNumbers;
        this.showGrid = other.showGrid;
        this.highlightWeekends = other.highlightWeekends;
        this.rotateMonthNames = other.rotateMonthNames;
        this.latitude = other.latitude;
        this.longitude = other.longitude;
        this.observationTime = other.observationTime;
        this.timeZone = other.timeZone;
        this.moonSize = other.moonSize;
        this.moonOffsetX = other.moonOffsetX;
        this.moonOffsetY = other.moonOffsetY;
        this.moonBorderColor = other.moonBorderColor;
        this.moonBorderWidth = other.moonBorderWidth;
        this.yearColor = other.yearColor;
        this.monthColor = other.monthColor;
        this.dayTextColor = other.dayTextColor;
        this.dayNameColor = other.dayNameColor;
        this.gridLineColor = other.gridLineColor;
        this.weekendBgColor = other.weekendBgColor;
        this.holidayColor = other.holidayColor;
        this.customDateColor = other.customDateColor;
        this.moonDarkColor = other.moonDarkColor;
        this.moonLightColor = other.moonLightColor;
        this.emojiPosition = other.emojiPosition;
        this.customDates = new HashMap<>(other.customDates);
        this.holidays = new HashMap<>(other.holidays);
        this.holidaySets = new ArrayList<>(other.holidaySets);
        this.eventDisplayMode = other.eventDisplayMode;
        this.customEventDisplayMode = other.customEventDisplayMode;
        this.emojiFont = other.emojiFont;
        this.locale = other.locale;
        this.firstDayOfWeek = other.firstDayOfWeek;
        this.layoutStyle = other.layoutStyle;
    }
}
