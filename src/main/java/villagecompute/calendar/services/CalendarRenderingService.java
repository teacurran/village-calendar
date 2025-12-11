package villagecompute.calendar.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.batik.transcoder.Transcoder;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.fop.svg.PDFTranscoder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class CalendarRenderingService {

  @Inject
  HolidayService holidayService;

  @Inject
  EmojiSvgService emojiSvgService;

  // ===========================================
  // PRINT DIMENSIONS (in inches)
  // ===========================================
  // Page size for printing
  public static final float PAGE_WIDTH_INCHES = 35f;
  public static final float PAGE_HEIGHT_INCHES = 23f;

  // Margin on all sides - change this value to adjust margins (e.g., 1.0f for 1 inch)
  public static final float MARGIN_INCHES = 0.5f;

  // Printable area (calculated from page size minus margins)
  public static final float PRINTABLE_WIDTH_INCHES = PAGE_WIDTH_INCHES - (2 * MARGIN_INCHES);   // 34"
  public static final float PRINTABLE_HEIGHT_INCHES = PAGE_HEIGHT_INCHES - (2 * MARGIN_INCHES); // 22"

  // Points per inch (PDF standard)
  private static final float POINTS_PER_INCH = 72f;
  // ===========================================

  // Calendar configuration options
  public static class CalendarConfig {
    public int year = LocalDate.now().getYear();
    public String theme = "default";
    public boolean showMoonPhases = false;
    public boolean showMoonIllumination = false;
    public boolean showFullMoonOnly = false;
    public boolean showWeekNumbers = false;
    public boolean compactMode = false;
    public boolean showDayNames = true;
    public boolean showDayNumbers = true;
    public boolean showGrid = true;
    public boolean highlightWeekends = true;
    public boolean rotateMonthNames = false;
    public double latitude = 0;  // Default: No location (no rotation)
    public double longitude = 0; // Default: No location (no rotation)
    public LocalTime observationTime = LocalTime.of(20, 0); // Default: 8:00 PM for moon calculations
    public String timeZone = "America/New_York"; // Time zone for calculations
    public int moonSize = 24; // Default moon radius in pixels
    public int moonOffsetX = 30; // Horizontal offset in pixels from left edge of cell
    public int moonOffsetY = 30; // Vertical offset in pixels from top edge of cell
    public String moonBorderColor = "#c1c1c1"; // Default border color
    public double moonBorderWidth = 0.5; // Default border width
    // Color customization
    public String yearColor = null; // Will use theme default if null
    public String monthColor = null; // Will use theme default if null
    public String dayTextColor = null; // Will use theme default if null
    public String dayNameColor = null; // Will use theme default if null
    public String gridLineColor = "#c1c1c1"; // Default grid color
    public String weekendBgColor = null; // Will use theme default if null
    public String holidayColor = "#ff5252"; // Default holiday color
    public String customDateColor = "#4caf50"; // Default custom date color
    public String moonDarkColor = "#c1c1c1"; // Default moon dark side
    public String moonLightColor = "#FFFFFF"; // Default moon light side
    public String emojiPosition = "bottom-left"; // Position of emojis in calendar cells
    public Map<String, Object> customDates = new HashMap<>(); // date -> emoji/text or CustomEventDisplay object
    public Map<String, String> eventTitles = new HashMap<>(); // date -> title mapping
    public Set<String> holidays = new HashSet<>();
    public Map<String, String> holidayEmojis = new HashMap<>(); // date -> emoji for holidays
    public Map<String, String> holidayNames = new HashMap<>(); // date -> holiday name for text display
    public List<String> holidaySets = new ArrayList<>(); // List of holiday set IDs to include
    public String eventDisplayMode = "large"; // "large", "large-text", "small", "text", or "none"
    public String emojiFont = "noto-color"; // "noto-color" (default) or "noto-mono" for monochrome
    public String locale = "en-US";
    public DayOfWeek firstDayOfWeek = DayOfWeek.SUNDAY;
    public String layoutStyle = "grid"; // "grid" for 12x31 layout, "traditional" for 4x3 month grid
  }

  /**
   * Returns the CSS font-family string for emoji rendering based on config.
   * Noto Color Emoji is the default (Apache 2.0 licensed, safe for commercial printing).
   * Noto Emoji (monochrome) is available for a text-only look.
   */
  private static String getEmojiFontFamily(CalendarConfig config) {
    if ("noto-mono".equals(config.emojiFont)) {
      // Noto Emoji (monochrome version) - black and white outline style
      // Include DejaVu Sans as fallback which has wide Unicode coverage on Linux servers
      return "'Noto Emoji', 'DejaVu Sans', 'Segoe UI Symbol', 'Symbola', sans-serif";
    }
    // Default: Noto Color Emoji (Apache 2.0 licensed, safe for commercial printing)
    // IMPORTANT: Only use Noto fonts for licensing compliance
    // - Noto Color Emoji: Full color emoji (primary)
    // - Noto Emoji: Monochrome fallback for PDF rendering where color fonts don't work
    // - DejaVu Sans: Wide Unicode coverage fallback
    return "'Noto Color Emoji', 'Noto Emoji', 'DejaVu Sans', 'Symbola', sans-serif";
  }

  // Emoji substitutions for monochrome mode (newer emojis -> older compatible ones)
  private static final Map<String, String> MONOCHROME_EMOJI_SUBSTITUTIONS = new HashMap<>();
  static {
    // Jewish
    MONOCHROME_EMOJI_SUBSTITUTIONS.put("🕎", "✡️");      // Menorah -> Star of David
    // Mexican
    MONOCHROME_EMOJI_SUBSTITUTIONS.put("🎖️", "⭐");      // Military Medal -> Star
    // Hindu
    MONOCHROME_EMOJI_SUBSTITUTIONS.put("🪁", "☀️");      // Kite -> Sun (Makar Sankranti)
    MONOCHROME_EMOJI_SUBSTITUTIONS.put("🪈", "🎵");      // Flute -> Music Note (Janmashtami)
    MONOCHROME_EMOJI_SUBSTITUTIONS.put("🪔", "🕯️");     // Diya Lamp -> Candle (Diwali)
    // Islamic
    MONOCHROME_EMOJI_SUBSTITUTIONS.put("🤲", "🙏");      // Palms Up -> Folded Hands (Ashura)
    MONOCHROME_EMOJI_SUBSTITUTIONS.put("☪️", "🌙");      // Star and Crescent -> Crescent Moon
    // Chinese
    MONOCHROME_EMOJI_SUBSTITUTIONS.put("🧧", "🏮");      // Red Envelope -> Lantern (Chinese New Year)
    MONOCHROME_EMOJI_SUBSTITUTIONS.put("🪦", "🌸");      // Headstone -> Blossom (Qingming)
    MONOCHROME_EMOJI_SUBSTITUTIONS.put("🥮", "🌕");      // Moon Cake -> Full Moon (Mid-Autumn)
    MONOCHROME_EMOJI_SUBSTITUTIONS.put("🏔️", "⛰️");     // Snow Mountain -> Mountain (Double Ninth)
    // Secular
    MONOCHROME_EMOJI_SUBSTITUTIONS.put("🦫", "🐿️");     // Beaver -> Chipmunk (Groundhog Day)
    MONOCHROME_EMOJI_SUBSTITUTIONS.put("🏳️‍🌈", "🌈");    // Pride Flag -> Rainbow
  }

  /**
   * Substitutes emojis that aren't available in Noto Emoji monochrome font
   * with compatible alternatives.
   */
  private static String substituteEmojiForMonochrome(String emoji, CalendarConfig config) {
    if (!"noto-mono".equals(config.emojiFont)) {
      return emoji; // No substitution needed for color mode
    }
    return MONOCHROME_EMOJI_SUBSTITUTIONS.getOrDefault(emoji, emoji);
  }

  /**
   * Render an emoji as either inline SVG (preferred for PDF) or text with font fallback.
   * Uses EmojiSvgService for vector rendering when available.
   *
   * @param emoji The emoji character(s) to render
   * @param x X position (center for centered, left for left-aligned)
   * @param y Y position (center for centered, baseline for text)
   * @param size Font size in pixels
   * @param config Calendar configuration
   * @param centered Whether the emoji should be centered at x,y
   * @return SVG element string (either <svg> or <text>)
   */
  private String renderEmoji(String emoji, double x, double y, int size, CalendarConfig config, boolean centered) {
    // Check if monochrome mode is enabled
    boolean isMonochrome = "noto-mono".equals(config.emojiFont);

    // Try to use SVG rendering for better PDF compatibility
    if (emojiSvgService != null && emojiSvgService.hasEmojiSvg(emoji)) {
      // For SVG, x/y is top-left corner, so adjust if centered
      double svgX = centered ? x - size / 2.0 : x;
      double svgY = centered ? y - size / 2.0 : y - size; // text y is baseline, svg y is top
      return emojiSvgService.getEmojiAsSvg(emoji, svgX, svgY, size, isMonochrome);
    }

    // Fall back to text rendering with emoji font
    String processedEmoji = substituteEmojiForMonochrome(emoji, config);
    if (centered) {
      return String.format(
        "<text x=\"%.1f\" y=\"%.1f\" style=\"font-size: %dpx; text-anchor: middle; dominant-baseline: middle; font-family: %s;\">%s</text>",
        x, y, size, getEmojiFontFamily(config), processedEmoji
      );
    } else {
      return String.format(
        "<text x=\"%.1f\" y=\"%.1f\" style=\"font-size: %dpx; font-family: %s;\">%s</text>",
        x, y, size, getEmojiFontFamily(config), processedEmoji
      );
    }
  }

  /**
   * Renders a month name label with optional rotation.
   * When rotated, text is centered in cellWidth and rotated -90 degrees.
   * When not rotated, text is positioned at (x, y) with the specified anchor.
   *
   * @param monthName The month name to display
   * @param x X position for non-rotated text
   * @param y Y position (vertical center of the row)
   * @param cellWidth Width of the month label cell (used for centering when rotated)
   * @param rotated Whether to rotate the text -90 degrees
   * @param textAnchor Text anchor for non-rotated text ("start", "middle", or "end")
   * @return SVG text element string
   */
  private String renderMonthName(String monthName, int x, int y, int cellWidth, boolean rotated, String textAnchor) {
    if (rotated) {
      int centerX = cellWidth / 2;
      return String.format(
        "<text x=\"%d\" y=\"%d\" class=\"month-name\" text-anchor=\"middle\" transform=\"rotate(-90 %d %d)\">%s</text>%n",
        centerX, y, centerX, y, monthName
      );
    } else {
      return String.format(
        "<text x=\"%d\" y=\"%d\" class=\"month-name\" text-anchor=\"%s\">%s</text>%n",
        x, y, textAnchor, monthName
      );
    }
  }

  /**
   * Renders holiday emoji and text based on eventDisplayMode.
   * Shared between grid and weekday-grid layouts for consistency.
   *
   * @param holidayEmoji The emoji to display (may be empty)
   * @param holidayName The holiday name for text modes (may be empty)
   * @param cellX Cell X position
   * @param cellY Cell Y position
   * @param cellWidth Cell width
   * @param cellHeight Cell height
   * @param shouldShowMoon Whether moon is displayed (affects large emoji positioning)
   * @param config Calendar configuration
   * @return SVG string for holiday content
   */
  private String renderHolidayContent(String holidayEmoji, String holidayName,
      int cellX, int cellY, int cellWidth, int cellHeight,
      boolean shouldShowMoon, CalendarConfig config) {

    if ("none".equals(config.eventDisplayMode)) {
      return "";
    }

    StringBuilder result = new StringBuilder();
    boolean isLargeMode = "large".equals(config.eventDisplayMode) || "large-text".equals(config.eventDisplayMode);

    // Render emoji based on display mode
    if (isLargeMode && !holidayEmoji.isEmpty()) {
      // Large/large-text mode: centered emoji
      int emojiX = cellX + cellWidth / 2;
      int fontSize = Math.max(16, cellHeight / 3);
      int emojiY;
      if (shouldShowMoon) {
        // Position between center and bottom when moon is showing
        int centerY = cellY + cellHeight / 2 + 5;
        int bottomY = cellY + cellHeight - fontSize / 2 - 5;
        emojiY = (centerY + bottomY) / 2;
      } else {
        emojiY = cellY + cellHeight / 2 + 5;
      }
      result.append(renderEmoji(holidayEmoji, emojiX, emojiY, fontSize, config, true));
      result.append("\n");
    } else if ("small".equals(config.eventDisplayMode) && !holidayEmoji.isEmpty()) {
      // Small mode: bottom-left corner
      int emojiX = cellX + 5;
      int emojiY = cellY + cellHeight - 5;
      int fontSize = Math.max(10, cellHeight / 6);
      result.append(renderEmoji(holidayEmoji, emojiX, emojiY, fontSize, config, false));
      result.append("\n");
    }

    // Holiday name text for large-text and text modes
    boolean showHolidayText = ("large-text".equals(config.eventDisplayMode) || "text".equals(config.eventDisplayMode))
        && !holidayName.isEmpty();
    if (showHolidayText) {
      int textX = cellX + cellWidth / 2;
      int textY = cellY + cellHeight - 3;
      int textSize = Math.max(5, cellWidth / 10);
      result.append(String.format(
        "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" font-size=\"%d\" fill=\"%s\" font-family=\"Helvetica, Arial, sans-serif\">%s</text>%n",
        textX, textY, textSize, config.holidayColor, escapeXml(holidayName)
      ));
    }

    return result.toString();
  }

  // Color themes
  private static final Map<String, ThemeColors> THEMES = new HashMap<>();

  // Vermont monthly colors for weekends
  private static final String[][] VERMONT_MONTHLY_COLORS = {
    // January - Snowy and frosty hues
    {"#E8F1F2", "#E8F1F2", "#E8F1F2", "#E8F1F2", "#E8F1F2", "#E8F1F2", "#E8F1F2", "#E8F1F2", "#E8F1F2", "#E8F1F2"},
    // February - Late winter
    {"#F0F8FF", "#F0F8FF", "#F0F8FF", "#F0F8FF", "#F0F8FF", "#F0F8FF", "#F0F8FF", "#F0F8FF", "#F0F8FF", "#F0F8FF"},
    // March - Early spring
    {"#E9F7EF", "#E9F7EF", "#E9F7EF", "#E9F7EF", "#E9F7EF", "#E9F7EF", "#E9F7EF", "#E9F7EF", "#d1a4fd", "#d1a4fd"},
    // April - Spring bloom
    {"#7CFC00", "#c8fc9f", "#c8fc9f", "#c8fc9f", "#c8fc9f", "#c8fc9f", "#c8fc9f", "#c8fc9f", "#c8fc9f", "#d1a4fd"},
    // May - Late spring, lush
    {"#82E0AA", "#D0ECE7", "#A2D9CE", "#73C6B6", "#45B39D", "#58D68D", "#82E0AA", "#ABEBC6", "#D5F5E3", "#FEF9E7"},
    // June - Early summer
    {"#66CDAA", "#66CDAA", "#66CDAA", "#66CDAA", "#66CDAA", "#66CDAA", "#66CDAA", "#66CDAA", "#66CDAA", "#66CDAA"},
    // July - Mid summer
    {"#3CB371", "#3CB371", "#3CB371", "#3CB371", "#3CB371", "#3CB371", "#3CB371", "#3CB371", "#3CB371", "#3CB371"},
    // August - Late summer
    {"#5bf0ff", "#5bf0ff", "#5bf0ff", "#5bf0ff", "#5bf0ff", "#5bf0ff", "#5bf0ff", "#5bf0ff", "#5bf0ff", "#5bf0ff"},
    // September - Early fall
    {"#FAD7A0", "#F8C471", "#F5B041", "#F39C12", "#F39C12", "#F39C12", "#F39C12", "#F39C12", "#FFD700", "#FFD700"},
    // October - Peak fall foliage
    {"#FF4500", "#FF8C00", "#DAA520", "rgba(180,120,60,0.4)", "rgba(190,130,70,0.4)",
      "rgba(200,140,80,0.4)", "rgba(210,160,100,0.4)", "rgba(225,190,140,0.4)", "#C0C0C0", "#808080"},
    // November - Late fall
    {"rgba(161,161,161,0.30)", "rgba(161,161,161,0.30)", "rgba(161,161,161,0.30)", "rgba(161,161,161,0.30)",
      "rgba(161,161,161,0.30)", "rgba(161,161,161,0.30)", "rgba(161,161,161,0.30)", "rgba(161,161,161,0.30)",
      "rgba(161,161,161,0.30)", "rgba(161,161,161,0.30)"},
    // December - Winter
    {"rgba(161,161,161,0.6)", "rgba(161,161,161,0.6)", "rgba(161,161,161,0.6)", "rgba(161,161,161,0.6)",
      "rgba(161,161,161,0.6)", "rgba(161,161,161,0.6)", "rgba(161,161,161,0.6)", "rgba(161,161,161,0.6)",
      "rgba(161,161,161,0.6)", "rgba(161,161,161,0.6)"}
  };

  // Lakeshore monthly colors - cool blue gradient through the year
  private static final String[][] LAKESHORE_MONTHLY_COLORS = {
    // January - Icy blue
    {"#e3f2fd", "#e3f2fd", "#e3f2fd", "#e3f2fd", "#e3f2fd", "#e3f2fd", "#e3f2fd", "#e3f2fd", "#e3f2fd", "#e3f2fd"},
    // February - Pale azure
    {"#e1f5fe", "#e1f5fe", "#e1f5fe", "#e1f5fe", "#e1f5fe", "#e1f5fe", "#e1f5fe", "#e1f5fe", "#e1f5fe", "#e1f5fe"},
    // March - Soft sky
    {"#b3e5fc", "#b3e5fc", "#b3e5fc", "#b3e5fc", "#b3e5fc", "#b3e5fc", "#b3e5fc", "#b3e5fc", "#b3e5fc", "#b3e5fc"},
    // April - Spring mist
    {"#e0f7fa", "#e0f7fa", "#e0f7fa", "#e0f7fa", "#e0f7fa", "#e0f7fa", "#e0f7fa", "#e0f7fa", "#e0f7fa", "#e0f7fa"},
    // May - Aqua tint
    {"#b2ebf2", "#b2ebf2", "#b2ebf2", "#b2ebf2", "#b2ebf2", "#b2ebf2", "#b2ebf2", "#b2ebf2", "#b2ebf2", "#b2ebf2"},
    // June - Light cyan
    {"#b2dfdb", "#b2dfdb", "#b2dfdb", "#b2dfdb", "#b2dfdb", "#b2dfdb", "#b2dfdb", "#b2dfdb", "#b2dfdb", "#b2dfdb"},
    // July - Tropical mist
    {"#80deea", "#80deea", "#80deea", "#80deea", "#80deea", "#80deea", "#80deea", "#80deea", "#80deea", "#80deea"},
    // August - Seafoam
    {"#84ffff", "#84ffff", "#84ffff", "#84ffff", "#84ffff", "#84ffff", "#84ffff", "#84ffff", "#84ffff", "#84ffff"},
    // September - Pale teal
    {"#a7ffeb", "#a7ffeb", "#a7ffeb", "#a7ffeb", "#a7ffeb", "#a7ffeb", "#a7ffeb", "#a7ffeb", "#a7ffeb", "#a7ffeb"},
    // October - Cool aqua
    {"#b2dfdb", "#b2dfdb", "#b2dfdb", "#b2dfdb", "#b2dfdb", "#b2dfdb", "#b2dfdb", "#b2dfdb", "#b2dfdb", "#b2dfdb"},
    // November - Soft cyan
    {"#b3e5fc", "#b3e5fc", "#b3e5fc", "#b3e5fc", "#b3e5fc", "#b3e5fc", "#b3e5fc", "#b3e5fc", "#b3e5fc", "#b3e5fc"},
    // December - Winter blue
    {"#bbdefb", "#bbdefb", "#bbdefb", "#bbdefb", "#bbdefb", "#bbdefb", "#bbdefb", "#bbdefb", "#bbdefb", "#bbdefb"}
  };

  // Sunset Glow monthly colors - warm pastel peach/pink/coral tones
  private static final String[][] SUNSET_MONTHLY_COLORS = {
    // January - Blush pink
    {"#fce4ec", "#fce4ec", "#fce4ec", "#fce4ec", "#fce4ec", "#fce4ec", "#fce4ec", "#fce4ec", "#fce4ec", "#fce4ec"},
    // February - Rose tint
    {"#f8bbd0", "#f8bbd0", "#f8bbd0", "#f8bbd0", "#f8bbd0", "#f8bbd0", "#f8bbd0", "#f8bbd0", "#f8bbd0", "#f8bbd0"},
    // March - Soft coral
    {"#ffcdd2", "#ffcdd2", "#ffcdd2", "#ffcdd2", "#ffcdd2", "#ffcdd2", "#ffcdd2", "#ffcdd2", "#ffcdd2", "#ffcdd2"},
    // April - Pale peach
    {"#ffccbc", "#ffccbc", "#ffccbc", "#ffccbc", "#ffccbc", "#ffccbc", "#ffccbc", "#ffccbc", "#ffccbc", "#ffccbc"},
    // May - Light apricot
    {"#ffe0b2", "#ffe0b2", "#ffe0b2", "#ffe0b2", "#ffe0b2", "#ffe0b2", "#ffe0b2", "#ffe0b2", "#ffe0b2", "#ffe0b2"},
    // June - Cream gold
    {"#fff8e1", "#fff8e1", "#fff8e1", "#fff8e1", "#fff8e1", "#fff8e1", "#fff8e1", "#fff8e1", "#fff8e1", "#fff8e1"},
    // July - Warm butter
    {"#ffecb3", "#ffecb3", "#ffecb3", "#ffecb3", "#ffecb3", "#ffecb3", "#ffecb3", "#ffecb3", "#ffecb3", "#ffecb3"},
    // August - Soft amber
    {"#ffe082", "#ffe082", "#ffe082", "#ffe082", "#ffe082", "#ffe082", "#ffe082", "#ffe082", "#ffe082", "#ffe082"},
    // September - Mellow peach
    {"#ffd180", "#ffd180", "#ffd180", "#ffd180", "#ffd180", "#ffd180", "#ffd180", "#ffd180", "#ffd180", "#ffd180"},
    // October - Light terracotta
    {"#ffab91", "#ffab91", "#ffab91", "#ffab91", "#ffab91", "#ffab91", "#ffab91", "#ffab91", "#ffab91", "#ffab91"},
    // November - Dusty rose
    {"#ffcdd2", "#ffcdd2", "#ffcdd2", "#ffcdd2", "#ffcdd2", "#ffcdd2", "#ffcdd2", "#ffcdd2", "#ffcdd2", "#ffcdd2"},
    // December - Winter blush
    {"#f8bbd0", "#f8bbd0", "#f8bbd0", "#f8bbd0", "#f8bbd0", "#f8bbd0", "#f8bbd0", "#f8bbd0", "#f8bbd0", "#f8bbd0"}
  };

  // Forest Floor monthly colors - light sage and mint tones
  private static final String[][] FOREST_MONTHLY_COLORS = {
    // January - Frost sage
    {"#e8f5e9", "#e8f5e9", "#e8f5e9", "#e8f5e9", "#e8f5e9", "#e8f5e9", "#e8f5e9", "#e8f5e9", "#e8f5e9", "#e8f5e9"},
    // February - Pale mint
    {"#c8e6c9", "#c8e6c9", "#c8e6c9", "#c8e6c9", "#c8e6c9", "#c8e6c9", "#c8e6c9", "#c8e6c9", "#c8e6c9", "#c8e6c9"},
    // March - Spring bud
    {"#dcedc8", "#dcedc8", "#dcedc8", "#dcedc8", "#dcedc8", "#dcedc8", "#dcedc8", "#dcedc8", "#dcedc8", "#dcedc8"},
    // April - Fresh leaf
    {"#c5e1a5", "#c5e1a5", "#c5e1a5", "#c5e1a5", "#c5e1a5", "#c5e1a5", "#c5e1a5", "#c5e1a5", "#c5e1a5", "#c5e1a5"},
    // May - Soft fern
    {"#aed581", "#aed581", "#aed581", "#aed581", "#aed581", "#aed581", "#aed581", "#aed581", "#aed581", "#aed581"},
    // June - Light moss
    {"#c8e6c9", "#c8e6c9", "#c8e6c9", "#c8e6c9", "#c8e6c9", "#c8e6c9", "#c8e6c9", "#c8e6c9", "#c8e6c9", "#c8e6c9"},
    // July - Meadow mist
    {"#a5d6a7", "#a5d6a7", "#a5d6a7", "#a5d6a7", "#a5d6a7", "#a5d6a7", "#a5d6a7", "#a5d6a7", "#a5d6a7", "#a5d6a7"},
    // August - Summer sage
    {"#b9f6ca", "#b9f6ca", "#b9f6ca", "#b9f6ca", "#b9f6ca", "#b9f6ca", "#b9f6ca", "#b9f6ca", "#b9f6ca", "#b9f6ca"},
    // September - Pale olive
    {"#dcedc8", "#dcedc8", "#dcedc8", "#dcedc8", "#dcedc8", "#dcedc8", "#dcedc8", "#dcedc8", "#dcedc8", "#dcedc8"},
    // October - Light cedar
    {"#d7ccc8", "#d7ccc8", "#d7ccc8", "#d7ccc8", "#d7ccc8", "#d7ccc8", "#d7ccc8", "#d7ccc8", "#d7ccc8", "#d7ccc8"},
    // November - Soft birch
    {"#efebe9", "#efebe9", "#efebe9", "#efebe9", "#efebe9", "#efebe9", "#efebe9", "#efebe9", "#efebe9", "#efebe9"},
    // December - Winter pine
    {"#e8f5e9", "#e8f5e9", "#e8f5e9", "#e8f5e9", "#e8f5e9", "#e8f5e9", "#e8f5e9", "#e8f5e9", "#e8f5e9", "#e8f5e9"}
  };

  static {
    // Default theme
    THEMES.put("default", new ThemeColors(
      "#000000", // text
      "#ffffff", // background
      "#f0f0f0", // weekend background
      "#333333", // month header
      "#666666"  // weekday header
    ));

    // Vermont Weekends theme - colors will be applied per month
    THEMES.put("vermontWeekends", new ThemeColors(
      "#000000",
      "#ffffff",
      null, // weekend color will be dynamic
      "#1b5e20",
      "#333333"
    ));

    // Rainbow themes
    THEMES.put("rainbowWeekends", new ThemeColors(
      "#000000",
      "#ffffff",
      null, // will be calculated dynamically
      "#e91e63",
      "#9c27b0"
    ));

    THEMES.put("rainbowDays", new ThemeColors(
      "#000000",
      null, // will be calculated dynamically
      null,
      "#333333",
      "#666666"
    ));

    // Lakeshore theme - cool blue tones
    THEMES.put("lakeshoreWeekends", new ThemeColors(
      "#000000",
      "#ffffff",
      null, // weekend color will be dynamic per month
      "#1565c0",
      "#333333"
    ));

    // Sunset Glow theme - warm orange/pink tones
    THEMES.put("sunsetWeekends", new ThemeColors(
      "#000000",
      "#ffffff",
      null, // weekend color will be dynamic per month
      "#e65100",
      "#333333"
    ));

    // Forest Floor theme - earth/green tones
    THEMES.put("forestWeekends", new ThemeColors(
      "#000000",
      "#ffffff",
      null, // weekend color will be dynamic per month
      "#2e7d32",
      "#333333"
    ));
  }

  private static class ThemeColors {
    final String text;
    final String background;
    final String weekendBackground;
    final String monthHeader;
    final String weekdayHeader;

    ThemeColors(String text, String background, String weekendBackground,
                String monthHeader, String weekdayHeader) {
      this.text = text;
      this.background = background;
      this.weekendBackground = weekendBackground;
      this.monthHeader = monthHeader;
      this.weekdayHeader = weekdayHeader;
    }
  }

  public String generateCalendarSVG(CalendarConfig config) {
    // Populate holidays from holidaySets if provided
    if (config.holidaySets != null && !config.holidaySets.isEmpty()) {
      for (String setId : config.holidaySets) {
        // Map frontend set IDs to backend set names
        String backendSetName = mapHolidaySetId(setId);
        Map<String, String> setHolidayEmojis = getHolidaysWithEmoji(config.year, backendSetName);
        config.holidays.addAll(setHolidayEmojis.keySet());
        config.holidayEmojis.putAll(setHolidayEmojis);
        // Also get holiday names for text display modes
        Map<String, String> setHolidayNames = getHolidayNames(config.year, backendSetName);
        config.holidayNames.putAll(setHolidayNames);
      }
    }

    // Choose layout style
    if ("grid".equals(config.layoutStyle)) {
      return generateGridCalendarSVG(config);
    } else if ("weekday-grid".equals(config.layoutStyle)) {
      return generateWeekdayGridCalendarSVG(config);
    } else {
      return generateTraditionalCalendarSVG(config);
    }
  }

  // Map frontend holiday set IDs to backend set names
  private String mapHolidaySetId(String setId) {
    if (setId == null) return "US";
    switch (setId.toLowerCase()) {
      case "us": return "US";
      case "jewish": return "JEWISH";
      case "christian": return "CHRISTIAN";
      case "muslim":
      case "islamic": return "ISLAMIC";
      case "buddhist": return "BUDDHIST";
      case "hindu":
      case "in": return "HINDU";
      case "canadian":
      case "ca": return "CANADIAN";
      case "uk": return "UK";
      case "european": return "EUROPEAN";
      case "major_world": return "MAJOR_WORLD";
      case "mexican":
      case "mx": return "MEXICAN";
      case "pagan":
      case "wiccan": return "PAGAN";
      case "chinese":
      case "cn":
      case "lunar": return "CHINESE";
      case "secular":
      case "fun": return "SECULAR";
      default: return setId.toUpperCase();
    }
  }

  // New grid layout (12 rows x 31 columns)
  private String generateGridCalendarSVG(CalendarConfig config) {
    ThemeColors theme = THEMES.getOrDefault(config.theme, THEMES.get("default"));

    int year = config.year;
    int cellWidth = config.compactMode ? 40 : 50;
    int cellHeight = config.compactMode ? 60 : 75;
    int headerHeight = 100;
    int monthLabelWidth = cellWidth; // First column for month names

    // Grid dimensions: 32 columns (1 for month name + 31 for days) x 12 rows
    int gridWidth = 32 * cellWidth;
    int gridHeight = 12 * cellHeight;
    int svgWidth = gridWidth;
    int svgHeight = gridHeight + headerHeight;

    StringBuilder svg = new StringBuilder();
    svg.append(String.format(
      "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\" preserveAspectRatio=\"xMidYMid meet\">%n",
      svgWidth, svgHeight, svgWidth, svgHeight
    ));

    // Add styles
    svg.append("<style>\n");
    svg.append(String.format(".year-text { fill: %s; font-family: Helvetica, Arial, sans-serif; font-size: 80px; font-weight: bold; }%n", config.yearColor != null ? config.yearColor : theme.monthHeader));
    svg.append(String.format(".month-name { fill: %s; font-family: Helvetica, Arial, sans-serif; font-size: 20px; font-weight: bold; }%n", config.monthColor != null ? config.monthColor : theme.monthHeader));
    svg.append(String.format(".day-text { fill: %s; font-family: Helvetica, Arial, sans-serif; font-size: 12px; }%n", config.dayTextColor != null ? config.dayTextColor : theme.text));
    svg.append(String.format(".day-name { fill: %s; font-family: Arial, sans-serif; font-size: 8px; }%n", config.dayNameColor != null ? config.dayNameColor : theme.weekdayHeader));
    svg.append(String.format(".grid-line { stroke: %s; stroke-width: 1; fill: none; }%n", config.gridLineColor));
    // Use "none" as fallback if weekendBgColor is null to avoid invalid CSS
    String weekendBg = config.weekendBgColor != null && !config.weekendBgColor.isEmpty()
        ? config.weekendBgColor
        : (theme.weekendBackground != null ? theme.weekendBackground : "none");
    svg.append(String.format(".weekend-bg { fill: %s; }%n", weekendBg));
    svg.append(String.format(".holiday { fill: %s; font-weight: bold; }%n", config.holidayColor));
    svg.append(String.format(".custom-date { fill: %s; }%n", config.customDateColor));
//        svg.append(".today { stroke: #2196f3; stroke-width: 2; fill: none; }%n");
    svg.append("</style>\n");

    // Add year header
    svg.append(String.format(
      "<text x=\"50\" y=\"80\" class=\"year-text\">%d</text>%n",
      year
    ));

    // Generate grid for each month (row)
    Locale locale = Locale.forLanguageTag(config.locale);
    for (int monthNum = 1; monthNum <= 12; monthNum++) {
      YearMonth yearMonth = YearMonth.of(year, monthNum);
      Month month = Month.of(monthNum);
      String monthName = month.getDisplayName(TextStyle.SHORT, locale);
      int daysInMonth = yearMonth.lengthOfMonth();

      int rowY = (monthNum - 1) * cellHeight + headerHeight;

      // Month name in first column
      int monthX = 5;
      int monthY = rowY + cellHeight / 2 + 5;
      svg.append(renderMonthName(monthName, monthX, monthY, cellWidth, config.rotateMonthNames, "start"));

      // Generate cells for each day
      int weekendIndex = 0; // Track weekend index for Vermont colors
      for (int day = 1; day <= 31; day++) {
        int cellX = day * cellWidth;
        int cellY = rowY;

        // Skip if day doesn't exist in this month
        if (day > daysInMonth) {
          // Draw empty cell with grid
          if (config.showGrid) {
            String pdfSafeColor = convertColorForPDF("rgba(255, 255, 255, 0)");
            svg.append(String.format(
              "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" class=\"grid-line\" fill=\"%s\"/>%n",
              cellX, cellY, cellWidth, cellHeight, pdfSafeColor
            ));
          }
          continue;
        }

        LocalDate date = yearMonth.atDay(day);
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        boolean isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;

        // Increment weekend index for Vermont colors
        if (isWeekend) {
          weekendIndex++;
        }

        // Cell background
        String cellBackground = getCellBackgroundColor(config, date, monthNum, day, isWeekend, weekendIndex - 1);
        String pdfSafeColor = convertColorForPDF(cellBackground);
        if (pdfSafeColor != null && !pdfSafeColor.equals("none") && !pdfSafeColor.equals("#ffffff")) {
          svg.append(String.format(
            "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"%s\"/>%n",
            cellX, cellY, cellWidth, cellHeight, pdfSafeColor
          ));
        }

        // Moon display (illumination, phase symbols, or full moon only)
        boolean shouldShowMoon = config.showMoonIllumination ||
          (config.showMoonPhases && isMoonPhaseDay(date)) ||
          (config.showFullMoonOnly && isFullMoonDay(date));

        if (shouldShowMoon) {
          // Use X/Y offset settings for precise positioning
          int moonX = cellX + config.moonOffsetX;
          int moonY = cellY + config.moonOffsetY;
          // Move moon up by 3px when text will be displayed below
          boolean hasTextBelow = "large-text".equals(config.eventDisplayMode) || "text".equals(config.eventDisplayMode);
          if (hasTextBelow) {
            moonY -= 3;
          }

          // For moon phases, only show on phase days
          if (config.showMoonPhases && !isMoonPhaseDay(date)) {
            // Skip non-phase days
          } else if (config.showFullMoonOnly && !isFullMoonDay(date)) {
            // Skip non-full-moon days
          } else {
            // Use same size for both modes
            svg.append(generateMoonIlluminationSVG(date, moonX, moonY,
              config.latitude, config.longitude, config));
          }
        }

        // Check for holidays or custom dates
        String dateStr = date.toString();
        String dayClass = "day-text"; // Always use same class for consistent styling
        String dayText = String.valueOf(day);
        String holidayEmoji = substituteEmojiForMonochrome(
            config.holidayEmojis.getOrDefault(dateStr, ""), config);
        String customEmoji = "";
        boolean isHoliday = config.holidays.contains(dateStr);

        CustomEventDisplay eventDisplay = null;
        if (config.customDates.containsKey(dateStr)) {
          Object customData = config.customDates.get(dateStr);

          if (customData instanceof String) {
            // Old format: just emoji string
            customEmoji = substituteEmojiForMonochrome((String) customData, config);
          } else if (customData instanceof Map) {
            // New format: object with emoji and display settings
            Map<String, Object> dataMap = (Map<String, Object>) customData;
            if (dataMap.containsKey("emoji")) {
              customEmoji = substituteEmojiForMonochrome((String) dataMap.get("emoji"), config);
              if (dataMap.containsKey("displaySettings") && dataMap.get("displaySettings") instanceof Map) {
                Map<String, Object> settings = (Map<String, Object>) dataMap.get("displaySettings");
                eventDisplay = new CustomEventDisplay(customEmoji, settings);
              } else {
                eventDisplay = new CustomEventDisplay(customEmoji);
              }
            }
          }
        }

        // Holiday emoji/text (if applicable) - render using shared helper
        String holidayName = config.holidayNames.getOrDefault(dateStr, "");
        svg.append(renderHolidayContent(holidayEmoji, holidayName,
            cellX, cellY, cellWidth, cellHeight, shouldShowMoon, config));

        // Apply holiday color to day number if it's a holiday
        if (isHoliday && config.holidayColor != null) {
          dayClass = "holiday";
        }

        // Custom emoji with enhanced positioning
        // Skip if holiday emoji already rendered for this date to avoid duplicates
        if (!customEmoji.isEmpty() && holidayEmoji.isEmpty()) {
          if (eventDisplay != null) {
            // Use custom display settings
            double emojiX = cellX + (cellWidth * eventDisplay.getEmojiX(50) / 100.0);
            double emojiY = cellY + (cellHeight * eventDisplay.getEmojiY(50) / 100.0);

            // Scale emoji size proportionally to cell size
            // Preview is 100x140, actual cell is 50x75 (or 40x60 compact)
            // So scale factor is roughly 0.5 for width
            double scaleFactor = cellWidth / 100.0;
            int scaledSize = (int) (eventDisplay.getEmojiSize(12) * scaleFactor);
            int emojiSize = Math.max(8, Math.min(24, scaledSize)); // Clamp between 8-24px

            svg.append(renderEmoji(customEmoji, emojiX, emojiY, emojiSize, config, true));
            svg.append("\n");
          } else {
            // Fall back to original positioning logic
            int emojiX = cellX;
            int emojiY = cellY;

            // Calculate position based on config.emojiPosition
            switch (config.emojiPosition) {
              case "top-left":
                emojiX += 5;
                emojiY += 13;
                break;
              case "top-center":
                emojiX += cellWidth / 2 - 5;
                emojiY += 13;
                break;
              case "top-right":
                emojiX += cellWidth - 15;
                emojiY += 13;
                break;
              case "middle-left":
                emojiX += 5;
                emojiY += cellHeight / 2 + 5;
                break;
              case "middle-center":
                emojiX += cellWidth / 2 - 5;
                emojiY += cellHeight / 2 + 5;
                break;
              case "middle-right":
                emojiX += cellWidth - 15;
                emojiY += cellHeight / 2 + 5;
                break;
              case "bottom-left":
              default:
                emojiX += 5;
                emojiY += cellHeight - 5;
                break;
              case "bottom-center":
                emojiX += cellWidth / 2 - 5;
                emojiY += cellHeight - 5;
                break;
              case "bottom-right":
                emojiX += cellWidth - 15;
                emojiY += cellHeight - 5;
                break;
            }

            svg.append(renderEmoji(customEmoji, emojiX, emojiY, 12, config, false));
            svg.append("\n");
          }
        }

        // Event title with enhanced positioning
        if (config.eventTitles.containsKey(dateStr)) {
          String title = config.eventTitles.get(dateStr);

          if (eventDisplay != null) {
            // Use custom text display settings
            double textX = cellX + (cellWidth * eventDisplay.getTextX(50) / 100.0);
            double textY = cellY + (cellHeight * eventDisplay.getTextY(70) / 100.0);

            // Scale text size proportionally
            double scaleFactor = cellWidth / 100.0;
            int scaledSize = (int) (eventDisplay.getTextSize(7) * scaleFactor);
            int textSize = Math.max(5, Math.min(12, scaledSize)); // Clamp between 5-12px
            String textColor = eventDisplay.getTextColor(config.customDateColor);
            String textAlign = eventDisplay.getTextAlign("center");
            double rotation = eventDisplay.getTextRotation();
            String fontWeight = eventDisplay.isTextBold() ? "bold" : "normal";

            // Text anchor based on alignment
            String textAnchor = "middle";
            if ("left".equals(textAlign)) textAnchor = "start";
            else if ("right".equals(textAlign)) textAnchor = "end";

            // Handle text wrapping for SVG
            if (eventDisplay.isTextWrap() && title.length() > 8) {
              // Split text into multiple lines for wrapping effect
              String[] words = title.split(" ");
              StringBuilder line1 = new StringBuilder();
              StringBuilder line2 = new StringBuilder();
              int charCount = 0;

              for (String word : words) {
                if (charCount + word.length() <= 8) {
                  if (line1.length() > 0) line1.append(" ");
                  line1.append(word);
                  charCount += word.length() + 1;
                } else if (line2.length() == 0) {
                  line2.append(word);
                } else {
                  line2.append("…");
                  break;
                }
              }

              String transform = rotation != 0
                ? String.format(" transform=\"rotate(%.1f %.1f %.1f)\"", rotation, textX, textY)
                : "";

              // Render two lines with proper spacing
              double lineHeight = textSize * 1.2;
              svg.append(String.format(
                "<text x=\"%.1f\" y=\"%.1f\" style=\"font-size: %dpx; fill: %s; font-weight: %s; text-anchor: %s; font-family: Arial, sans-serif;\"%s>%s</text>%n",
                textX, textY - lineHeight/2, textSize, textColor, fontWeight, textAnchor, transform, line1.toString()
              ));
              if (line2.length() > 0) {
                svg.append(String.format(
                  "<text x=\"%.1f\" y=\"%.1f\" style=\"font-size: %dpx; fill: %s; font-weight: %s; text-anchor: %s; font-family: Arial, sans-serif;\"%s>%s</text>%n",
                  textX, textY + lineHeight/2, textSize, textColor, fontWeight, textAnchor, transform, line2.toString()
                ));
              }
            } else {
              // Single line text
              if (!eventDisplay.isTextWrap() && title.length() > 10) {
                title = title.substring(0, 9) + "…";
              }

              String transform = rotation != 0
                ? String.format(" transform=\"rotate(%.1f %.1f %.1f)\"", rotation, textX, textY)
                : "";

              svg.append(String.format(
                "<text x=\"%.1f\" y=\"%.1f\" style=\"font-size: %dpx; fill: %s; font-weight: %s; text-anchor: %s; font-family: Arial, sans-serif;\"%s>%s</text>%n",
                textX, textY, textSize, textColor, fontWeight, textAnchor, transform, title
              ));
            }
          } else {
            // Original title rendering
            // Truncate title if too long
            if (title.length() > 10) {
              title = title.substring(0, 9) + "…";
            }
            svg.append(String.format(
              "<text x=\"%d\" y=\"%d\" style=\"font-size: 7px; fill: %s;\">%s</text>%n",
              cellX + 5, cellY + 38, config.customDateColor, title
            ));
          }
        }

        // Day number - use inline styles for better PDF compatibility
        if (config.showDayNumbers) {
          String dayFill = config.dayTextColor != null ? config.dayTextColor : theme.text;
          String fontWeight = "normal";
          if (isHoliday && config.holidayColor != null) {
            dayFill = config.holidayColor;
            fontWeight = "bold";
          } else if (config.customDates.containsKey(dateStr) && config.customDateColor != null) {
            dayFill = config.customDateColor;
          }
          svg.append(String.format(
            "<text x=\"%d\" y=\"%d\" fill=\"%s\" font-weight=\"%s\" font-family=\"Helvetica, Arial, sans-serif\" font-size=\"12px\">%s</text>%n",
            cellX + 5, cellY + 14, dayFill, fontWeight, dayText
          ));
        }

        // Day name (if enabled)
        if (config.showDayNames) {
          String dayName = dayOfWeek.getDisplayName(TextStyle.SHORT, locale).substring(0, 2);
          svg.append(String.format(
            "<text x=\"%d\" y=\"%d\" class=\"day-name\">%s</text>%n",
            cellX + 5, cellY + 26, dayName
          ));
        }

        // Grid lines
        if (config.showGrid) {
          svg.append(String.format(
            "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" class=\"grid-line\" fill=\"none\" stroke=\"%s\" stroke-width=\"1\"/>%n",
            cellX, cellY, cellWidth, cellHeight, config.gridLineColor
          ));
        }
      }
    }

    // Outer border for the entire grid
    if (config.showGrid) {
      svg.append(String.format(
        "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" class=\"grid-line\" fill=\"none\" stroke=\"%s\" stroke-width=\"2\"/>%n",
        cellWidth, headerHeight - 1, cellWidth * 31, cellHeight * 12 + 2, config.gridLineColor
      ));
    }

    svg.append("</svg>");
    return svg.toString();
  }

  // Weekday aligned grid layout (12 rows x 37 columns - 7 days * 5 weeks + 2 padding)
  private String generateWeekdayGridCalendarSVG(CalendarConfig config) {
    ThemeColors theme = THEMES.getOrDefault(config.theme, THEMES.get("default"));

    int year = config.year;
    int cellWidth = config.compactMode ? 40 : 50;
    int cellHeight = config.compactMode ? 60 : 75;
    int headerHeight = 100;
    int totalCols = 37; // 7 days * 5 weeks + 2 extra for months that span 6 weeks
    int svgWidth = cellWidth * (totalCols + 1) + 20; // Extra column for month labels
    int svgHeight = cellHeight * 12 + headerHeight + 20;

    StringBuilder svg = new StringBuilder();
    svg.append(String.format(
      "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\" preserveAspectRatio=\"xMidYMid meet\">%n",
      svgWidth, svgHeight, svgWidth, svgHeight
    ));

    // Add styles
    svg.append("<style>\n");
    svg.append(String.format(".year-text { fill: %s; font-family: Helvetica, Arial, sans-serif; font-size: 80px; font-weight: bold; }%n", config.yearColor != null ? config.yearColor : theme.monthHeader));
    svg.append(String.format(".month-name { fill: %s; font-family: Helvetica, Arial, sans-serif; font-size: 20px; font-weight: bold; }%n", config.monthColor != null ? config.monthColor : theme.monthHeader));
    svg.append(String.format(".day-text { fill: %s; font-family: Helvetica, Arial, sans-serif; font-size: 12px; }%n", config.dayTextColor != null ? config.dayTextColor : theme.text));
    svg.append(String.format(".day-name { fill: %s; font-family: Arial, sans-serif; font-size: 8px; }%n", config.dayNameColor != null ? config.dayNameColor : theme.weekdayHeader));
    svg.append(String.format(".grid-line { stroke: %s; stroke-width: 1; fill: none; }%n", config.gridLineColor));
    // Use "none" as fallback if weekendBgColor is null to avoid invalid CSS
    String weekendBg = config.weekendBgColor != null && !config.weekendBgColor.isEmpty()
        ? config.weekendBgColor
        : (theme.weekendBackground != null ? theme.weekendBackground : "none");
    svg.append(String.format(".weekend-bg { fill: %s; }%n", weekendBg));
    svg.append(String.format(".holiday { fill: %s; font-weight: bold; }%n", config.holidayColor));
    svg.append(String.format(".custom-date { fill: %s; }%n", config.customDateColor));
//        svg.append(".today { stroke: #2196f3; stroke-width: 2; fill: none; }%n");
    svg.append("</style>\n");

    // Background
    // svg.append(String.format("<rect width=\"%d\" height=\"%d\" class=\"grid-bg\"/>%n", svgWidth, svgHeight));

    // Year title
    svg.append(String.format(
      "<text x=\"50\" y=\"80\" class=\"year-text\">%d</text>%n",
      year
    ));

    // Generate each month row
    Locale locale = Locale.forLanguageTag(config.locale);
    for (int monthNum = 1; monthNum <= 12; monthNum++) {
      YearMonth yearMonth = YearMonth.of(year, monthNum);
      LocalDate firstDay = yearMonth.atDay(1);
      int daysInMonth = yearMonth.lengthOfMonth();

      // Calculate starting column based on first day of week (0 = Sunday, 6 = Saturday)
      int startCol = firstDay.getDayOfWeek().getValue() % 7; // Convert to Sunday=0

      int row = monthNum - 1;
      int cellY = headerHeight + row * cellHeight;

      // Month label
      String monthName = Month.of(monthNum).getDisplayName(TextStyle.SHORT, locale);
      int monthX = cellWidth - 5;
      int monthY = cellY + cellHeight / 2 + 4;
      svg.append(renderMonthName(monthName, monthX, monthY, cellWidth, config.rotateMonthNames, "end"));

      // Generate day cells
      int weekendIndex = 0; // Track weekend index for Vermont colors
      for (int day = 1; day <= daysInMonth; day++) {
        LocalDate date = yearMonth.atDay(day);
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        boolean isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;

        // Increment weekend index for Vermont colors
        if (isWeekend) {
          weekendIndex++;
        }

        int col = startCol + day - 1;
        int cellX = cellWidth + col * cellWidth;

        // Cell background
        String cellBackground = getCellBackgroundColor(config, date, monthNum, day, isWeekend, weekendIndex - 1);
        String pdfSafeColor = convertColorForPDF(cellBackground);
        if (pdfSafeColor != null && !pdfSafeColor.equals("none") && !pdfSafeColor.equals("#ffffff")) {
          svg.append(String.format(
            "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"%s\"/>%n",
            cellX, cellY, cellWidth, cellHeight, pdfSafeColor
          ));
        }

        // Check for holidays and custom dates
        String dateStr = String.format("%d-%02d-%02d", year, monthNum, day);
        boolean isHoliday = config.holidays != null && config.holidays.contains(dateStr);
        boolean isCustomDate = config.customDates != null && config.customDates.containsKey(dateStr);
        String holidayEmoji = config.holidayEmojis != null ?
            substituteEmojiForMonochrome(config.holidayEmojis.getOrDefault(dateStr, ""), config) : "";

        // Day number - positioned at top-left like grid layout, use inline styles for PDF compatibility
        if (config.showDayNumbers) {
          String dayFill = config.dayTextColor != null ? config.dayTextColor : theme.text;
          String fontWeight = "normal";
          if (isHoliday && config.holidayColor != null) {
            dayFill = config.holidayColor;
            fontWeight = "bold";
          } else if (isCustomDate && config.customDateColor != null) {
            dayFill = config.customDateColor;
          }
          svg.append(String.format(
            "<text x=\"%d\" y=\"%d\" fill=\"%s\" font-weight=\"%s\" font-family=\"Helvetica, Arial, sans-serif\" font-size=\"12px\">%d</text>%n",
            cellX + 5, cellY + 14, dayFill, fontWeight, day
          ));
        }

        // Day name (if enabled) - positioned below day number like grid layout
        if (config.showDayNames) {
          String dayName = dayOfWeek.getDisplayName(TextStyle.SHORT, locale).substring(0, 2);
          svg.append(String.format(
            "<text x=\"%d\" y=\"%d\" class=\"day-name\">%s</text>%n",
            cellX + 5, cellY + 26, dayName
          ));
        }

        // Moon display (illumination, phase symbols, or full moon only)
        // Rendered before emojis so emojis appear on top
        boolean shouldShowMoon = config.showMoonIllumination ||
          (config.showMoonPhases && isMoonPhaseDay(date)) ||
          (config.showFullMoonOnly && isFullMoonDay(date));

        if (shouldShowMoon) {
          // Use X/Y offset settings for precise positioning
          int moonX = cellX + config.moonOffsetX;
          int moonY = cellY + config.moonOffsetY;

          // For moon phases, only show on phase days
          if (config.showMoonPhases && !isMoonPhaseDay(date)) {
            // Skip non-phase days
          } else if (config.showFullMoonOnly && !isFullMoonDay(date)) {
            // Skip non-full-moon days
          } else {
            // Use generateMoonIlluminationSVG for proper rendering with user settings
            svg.append(generateMoonIlluminationSVG(date, moonX, moonY,
              config.latitude, config.longitude, config));
          }
        }

        // Holiday emoji/text (if applicable) - render using shared helper
        String holidayName = config.holidayNames.getOrDefault(dateStr, "");
        svg.append(renderHolidayContent(holidayEmoji, holidayName,
            cellX, cellY, cellWidth, cellHeight, shouldShowMoon, config));

        // Custom emoji - skip if holiday emoji already rendered to avoid duplicates
        if (isCustomDate && config.customDates.get(dateStr) != null && holidayEmoji.isEmpty()) {
          Object customDataObj = config.customDates.get(dateStr);
          String emoji = "📅";
          if (customDataObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> customData = (Map<String, Object>) customDataObj;
            emoji = customData.get("emoji") != null ? customData.get("emoji").toString() : "📅";
          } else if (customDataObj != null) {
            emoji = customDataObj.toString();
          }

          String position = config.emojiPosition != null ? config.emojiPosition : "bottom-left";

          int emojiX = cellX + (position.contains("right") ? cellWidth - 10 : 5);
          int emojiY = cellY + (position.contains("top") ? 15 : cellHeight - 5);

          svg.append(renderEmoji(emoji, emojiX, emojiY, 12, config, false));
          svg.append("\n");
        }

        // Grid lines
        if (config.showGrid) {
          svg.append(String.format(
            "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" class=\"grid-line\" fill=\"none\" stroke=\"%s\" stroke-width=\"1\"/>%n",
            cellX, cellY, cellWidth, cellHeight, config.gridLineColor
          ));
        }
      }
    }

    // No outer border for weekday-grid layout - only cell borders

    svg.append("</svg>");
    return svg.toString();
  }

  // Traditional layout (4x3 month grid)
  private String generateTraditionalCalendarSVG(CalendarConfig config) {
    ThemeColors theme = THEMES.getOrDefault(config.theme, THEMES.get("default"));

    int year = config.year;
    int cellSize = config.compactMode ? 18 : 25;
    int monthWidth = cellSize * 7 + 20;
    int monthHeight = cellSize * 8 + 40; // 6 weeks + headers

    // Calculate SVG dimensions (4x3 grid of months)
    int svgWidth = monthWidth * 4 + 40;
    int svgHeight = monthHeight * 3 + 60;

    StringBuilder svg = new StringBuilder();
    svg.append(String.format(
      "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\">%n",
      svgWidth, svgHeight, svgWidth, svgHeight
    ));

    // Add styles
    svg.append("<style>%n");
    svg.append(String.format(".calendar-text { fill: %s; font-family: Arial, sans-serif; font-size: 12px; }%n", config.dayTextColor != null ? config.dayTextColor : theme.text));
    svg.append(String.format(".month-header { fill: %s; font-weight: bold; font-size: 14px; }%n", config.monthColor != null ? config.monthColor : theme.monthHeader));
    svg.append(String.format(".weekday-header { fill: %s; font-size: 10px; }%n", config.dayNameColor != null ? config.dayNameColor : theme.weekdayHeader));
    // Use "none" as fallback if weekendBgColor is null to avoid invalid CSS
    String weekendBgYear = config.weekendBgColor != null && !config.weekendBgColor.isEmpty()
        ? config.weekendBgColor
        : (theme.weekendBackground != null ? theme.weekendBackground : "none");
    svg.append(String.format(".weekend { fill: %s; }%n", weekendBgYear));
    svg.append(String.format(".holiday { fill: %s; font-weight: bold; }%n", config.holidayColor));
    svg.append(String.format(".custom-date { fill: %s; }%n", config.customDateColor));
//        svg.append(".today { stroke: #2196f3; stroke-width: 2; fill: none; }%n");
    svg.append("</style>%n");

    // Add title
    svg.append(String.format(
      "<text x=\"%d\" y=\"30\" class=\"calendar-text\" style=\"font-size: 20px; font-weight: bold; text-anchor: middle;\">%d</text>%n",
      svgWidth / 2, year
    ));

    // Generate each month
    for (int month = 1; month <= 12; month++) {
      int row = (month - 1) / 4;
      int col = (month - 1) % 4;
      int x = col * monthWidth + 20;
      int y = row * monthHeight + 50;

      svg.append(generateMonthSVG(year, month, x, y, cellSize, theme, config));
    }

    svg.append("</svg>");
    return svg.toString();
  }

  private String generateMonthSVG(int year, int monthNum, int x, int y, int cellSize,
                                  ThemeColors theme, CalendarConfig config) {
    StringBuilder month = new StringBuilder();
    YearMonth yearMonth = YearMonth.of(year, monthNum);
    Month monthEnum = Month.of(monthNum);
    Locale locale = Locale.forLanguageTag(config.locale);

    // Month header
    String monthName = monthEnum.getDisplayName(TextStyle.FULL, locale);
    month.append(String.format(
      "<text x=\"%d\" y=\"%d\" class=\"month-header\" text-anchor=\"middle\">%s</text>%n",
      x + (cellSize * 7) / 2, y, monthName
    ));

    // Weekday headers
    DayOfWeek[] weekdays = getWeekdayOrder(config.firstDayOfWeek);
    for (int i = 0; i < 7; i++) {
      String dayName = weekdays[i].getDisplayName(TextStyle.SHORT, locale).substring(0, 1);
      month.append(String.format(
        "<text x=\"%d\" y=\"%d\" class=\"weekday-header\" text-anchor=\"middle\">%s</text>%n",
        x + i * cellSize + cellSize / 2, y + 15, dayName
      ));
    }

    // Days grid
    LocalDate firstDay = yearMonth.atDay(1);
    int daysInMonth = yearMonth.lengthOfMonth();
    int startDayOfWeek = getDayOfWeekIndex(firstDay.getDayOfWeek(), config.firstDayOfWeek);

    for (int day = 1; day <= daysInMonth; day++) {
      LocalDate date = yearMonth.atDay(day);
      int dayIndex = startDayOfWeek + day - 1;
      int weekRow = dayIndex / 7;
      int weekCol = dayIndex % 7;

      int dayX = x + weekCol * cellSize;
      int dayY = y + 25 + weekRow * cellSize;

      // Weekend background
      if (isWeekend(weekdays[weekCol])) {
        String weekendFill = config.weekendBgColor != null && !config.weekendBgColor.isEmpty()
          ? config.weekendBgColor
          : (theme.weekendBackground != null ? theme.weekendBackground : "#f0f0f0");
        String pdfSafeWeekendFill = convertColorForPDF(weekendFill);
        month.append(String.format(
          "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" class=\"weekend\" fill=\"%s\"/>%n",
          dayX, dayY, cellSize, cellSize, pdfSafeWeekendFill
        ));
      }

      // Moon display for year view (illumination, phase symbols, or full moon only)
      boolean shouldShowMoonYearView = config.showMoonIllumination ||
        (config.showMoonPhases && isMoonPhaseDay(date)) ||
        (config.showFullMoonOnly && isFullMoonDay(date));

      if (shouldShowMoonYearView) {
        // Scale down X/Y offsets for year view's smaller cells
        int scaledOffsetX = config.moonOffsetX * cellSize / 100; // Scale based on cell size
        int scaledOffsetY = config.moonOffsetY * cellSize / 100;
        int moonX = dayX + scaledOffsetX;
        int moonY = dayY + scaledOffsetY;

        // Skip non-phase days if in phase mode
        if (config.showMoonPhases && !isMoonPhaseDay(date)) {
          // Skip non-phase days
        } else if (config.showFullMoonOnly && !isFullMoonDay(date)) {
          // Skip non-full-moon days
        } else {
          // Create a smaller moon for year view
          CalendarConfig yearConfig = new CalendarConfig();
          yearConfig.moonSize = Math.max(3, config.moonSize / 5); // Scale down for year view
          yearConfig.moonLightColor = config.moonLightColor;
          yearConfig.moonDarkColor = config.moonDarkColor;
          yearConfig.moonBorderColor = config.moonBorderColor;
          yearConfig.moonBorderWidth = config.moonBorderWidth * 0.5;

          month.append(generateMoonIlluminationSVG(date, moonX, moonY,
            config.latitude, config.longitude, yearConfig));
        }
      }

      // Check for holidays or custom dates
      String dateStr = date.toString();
      String dayClass = "calendar-text";
      String dayText = String.valueOf(day);

      String customEmoji = "";
      if (config.holidays.contains(dateStr)) {
        dayClass = "holiday";
      }

      CustomEventDisplay eventDisplay = null;
      if (config.customDates.containsKey(dateStr)) {
        dayClass = "custom-date";
        Object customData = config.customDates.get(dateStr);

        if (customData instanceof String) {
          // Old format: just emoji string
          customEmoji = substituteEmojiForMonochrome((String) customData, config);
        } else if (customData instanceof Map) {
          // New format: object with emoji and display settings
          Map<String, Object> dataMap = (Map<String, Object>) customData;
          if (dataMap.containsKey("emoji")) {
            customEmoji = substituteEmojiForMonochrome((String) dataMap.get("emoji"), config);
            if (dataMap.containsKey("displaySettings") && dataMap.get("displaySettings") instanceof Map) {
              Map<String, Object> settings = (Map<String, Object>) dataMap.get("displaySettings");
              eventDisplay = new CustomEventDisplay(customEmoji, settings);
            } else {
              eventDisplay = new CustomEventDisplay(customEmoji);
            }
          }
        }
      }

      // Day number
      if (config.showDayNumbers) {
        month.append(String.format(
          "<text x=\"%d\" y=\"%d\" class=\"%s\" text-anchor=\"middle\">%s</text>%n",
          dayX + cellSize / 2, dayY + cellSize / 2 + 4, dayClass, dayText
        ));
      }

      // Custom emoji with enhanced positioning for traditional layout
      if (!customEmoji.isEmpty()) {
        if (eventDisplay != null) {
          // Use custom display settings
          double emojiX = dayX + (cellSize * eventDisplay.getEmojiX(50) / 100.0);
          double emojiY = dayY + (cellSize * eventDisplay.getEmojiY(50) / 100.0);

          // Scale emoji size for traditional layout
          // Preview is 100x140, traditional cells are smaller (cellSize is typically 30-40px)
          double scaleFactor = cellSize / 100.0;
          int scaledSize = (int) (eventDisplay.getEmojiSize(9) * scaleFactor);
          int emojiSize = Math.max(6, Math.min(16, scaledSize)); // Clamp between 6-16px for smaller cells

          month.append(renderEmoji(customEmoji, emojiX, emojiY, emojiSize, config, true));
          month.append("\n");
        } else {
          // Fall back to original positioning logic
          int emojiX = dayX;
          int emojiY = dayY;

          // Calculate position based on config.emojiPosition
          // For traditional layout with smaller cells, we'll adjust the positions
          switch (config.emojiPosition) {
            case "top-left":
              emojiX += 3;
              emojiY += 10;
              break;
            case "top-center":
              emojiX += cellSize / 2 - 4;
              emojiY += 10;
              break;
            case "top-right":
              emojiX += cellSize - 10;
              emojiY += 10;
              break;
            case "middle-left":
              emojiX += 3;
              emojiY += cellSize / 2 + 3;
              break;
            case "middle-center":
              emojiX += cellSize / 2 - 4;
              emojiY += cellSize / 2 + 3;
              break;
            case "middle-right":
              emojiX += cellSize - 10;
              emojiY += cellSize / 2 + 3;
              break;
            case "bottom-left":
            default:
              emojiX += 3;
              emojiY += cellSize - 3;
              break;
            case "bottom-center":
              emojiX += cellSize / 2 - 4;
              emojiY += cellSize - 3;
              break;
            case "bottom-right":
              emojiX += cellSize - 10;
              emojiY += cellSize - 3;
              break;
          }

          month.append(renderEmoji(customEmoji, emojiX, emojiY, 9, config, false));
          month.append("\n");
        }
      }

      // Event title with enhanced positioning for traditional layout
      if (config.eventTitles.containsKey(dateStr)) {
        String title = config.eventTitles.get(dateStr);

        if (eventDisplay != null) {
          // Use custom text display settings
          double textX = dayX + (cellSize * eventDisplay.getTextX(50) / 100.0);
          double textY = dayY + (cellSize * eventDisplay.getTextY(80) / 100.0);

          // Scale text size for traditional layout
          double scaleFactor = cellSize / 100.0;
          int scaledSize = (int) (eventDisplay.getTextSize(6) * scaleFactor);
          int textSize = Math.max(4, Math.min(10, scaledSize)); // Clamp between 4-10px for smaller cells
          String textColor = eventDisplay.getTextColor(config.customDateColor);
          String textAlign = eventDisplay.getTextAlign("center");
          double rotation = eventDisplay.getTextRotation();
          String fontWeight = eventDisplay.isTextBold() ? "bold" : "normal";

          // Text anchor based on alignment
          String textAnchor = "middle";
          if ("left".equals(textAlign)) textAnchor = "start";
          else if ("right".equals(textAlign)) textAnchor = "end";

          // For traditional layout with small cells, simplified text handling
          if (title.length() > 6) {
            title = title.substring(0, 5) + "…";
          }

          String transform = rotation != 0
            ? String.format(" transform=\"rotate(%.1f %.1f %.1f)\"", rotation, textX, textY)
            : "";

          month.append(String.format(
            "<text x=\"%.1f\" y=\"%.1f\" style=\"font-size: %dpx; fill: %s; font-weight: %s; text-anchor: %s; font-family: Arial, sans-serif;\"%s>%s</text>%n",
            textX, textY, textSize, textColor, fontWeight, textAnchor, transform, title
          ));
        } else {
          // Original title rendering for traditional layout
          // Truncate title if too long for small cells
          if (title.length() > 8) {
            title = title.substring(0, 7) + "…";
          }
          month.append(String.format(
            "<text x=\"%d\" y=\"%d\" style=\"font-size: 6px; fill: %s;\" text-anchor=\"middle\">%s</text>%n",
            dayX + cellSize / 2, dayY + cellSize - 3, config.customDateColor, title
          ));
        }
      }
    }

    return month.toString();
  }

  private DayOfWeek[] getWeekdayOrder(DayOfWeek firstDay) {
    DayOfWeek[] days = new DayOfWeek[7];
    for (int i = 0; i < 7; i++) {
      days[i] = firstDay.plus(i);
    }
    return days;
  }

  private int getDayOfWeekIndex(DayOfWeek day, DayOfWeek firstDayOfWeek) {
    int dayValue = day.getValue(); // Monday = 1, Sunday = 7
    int firstDayValue = firstDayOfWeek.getValue();

    if (dayValue >= firstDayValue) {
      return dayValue - firstDayValue;
    } else {
      return 7 - firstDayValue + dayValue;
    }
  }

  private boolean isWeekend(DayOfWeek day) {
    return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
  }

  // Check if this is a moon phase transition day
  private boolean isMoonPhaseDay(LocalDate date) {
    // Calculate the exact phase for today, yesterday, and tomorrow
    double phaseToday = calculateMoonPhaseValue(date);
    double phaseYesterday = calculateMoonPhaseValue(date.minusDays(1));
    double phaseTomorrow = calculateMoonPhaseValue(date.plusDays(1));

    // Find the closest approach to each major phase point
    // We want to show the icon on the day that's closest to the exact phase

    // Check for New Moon (0.0 or 1.0)
    double distToday = Math.min(phaseToday, 1.0 - phaseToday);
    double distYesterday = Math.min(phaseYesterday, 1.0 - phaseYesterday);
    double distTomorrow = Math.min(phaseTomorrow, 1.0 - phaseTomorrow);
    if (distToday < distYesterday && distToday < distTomorrow && distToday < 0.017) {
      return true;
    }

    // Check for First Quarter (0.25)
    distToday = Math.abs(phaseToday - 0.25);
    distYesterday = Math.abs(phaseYesterday - 0.25);
    distTomorrow = Math.abs(phaseTomorrow - 0.25);
    if (distToday < distYesterday && distToday < distTomorrow && distToday < 0.017) {
      return true;
    }

    // Check for Full Moon (0.5)
    distToday = Math.abs(phaseToday - 0.5);
    distYesterday = Math.abs(phaseYesterday - 0.5);
    distTomorrow = Math.abs(phaseTomorrow - 0.5);
    if (distToday < distYesterday && distToday < distTomorrow && distToday < 0.017) {
      return true;
    }

    // Check for Last Quarter (0.75)
    distToday = Math.abs(phaseToday - 0.75);
    distYesterday = Math.abs(phaseYesterday - 0.75);
    distTomorrow = Math.abs(phaseTomorrow - 0.75);
    if (distToday < distYesterday && distToday < distTomorrow && distToday < 0.017) {
      return true;
    }

    return false;
  }

  // Check if this is a full moon day only
  private boolean isFullMoonDay(LocalDate date) {
    double phaseToday = calculateMoonPhaseValue(date);
    double phaseYesterday = calculateMoonPhaseValue(date.minusDays(1));
    double phaseTomorrow = calculateMoonPhaseValue(date.plusDays(1));

    // Check for Full Moon (0.5)
    double distToday = Math.abs(phaseToday - 0.5);
    double distYesterday = Math.abs(phaseYesterday - 0.5);
    double distTomorrow = Math.abs(phaseTomorrow - 0.5);
    return distToday < distYesterday && distToday < distTomorrow && distToday < 0.017;
  }

  // Calculate moon phase as a value from 0 to 1
  private double calculateMoonPhaseValue(LocalDate date) {
    // Known new moon date (January 6, 2000 at 18:14 UTC)
    LocalDate knownNewMoon = LocalDate.of(2000, 1, 6);

    // Calculate days since known new moon
    long daysSince = java.time.temporal.ChronoUnit.DAYS.between(knownNewMoon, date);

    // Synodic month is approximately 29.53059 days
    double synodicMonth = 29.53059;

    // Calculate phase as a fraction (0 = new moon, 0.5 = full moon)
    double phase = (daysSince % synodicMonth) / synodicMonth;
    if (phase < 0) phase += 1.0;

    return phase;
  }

  private String getMoonPhaseSymbol(LocalDate date) {
    MoonPhase phase = calculateMoonPhase(date);
    return getMoonPhaseSymbol(phase);
  }

  private String getMoonPhaseSymbol(MoonPhase phase) {
    switch (phase) {
      case NEW_MOON:
        return "🌑";
      case WAXING_CRESCENT:
        return "🌒";
      case FIRST_QUARTER:
        return "🌓";
      case WAXING_GIBBOUS:
        return "🌔";
      case FULL_MOON:
        return "🌕";
      case WANING_GIBBOUS:
        return "🌖";
      case LAST_QUARTER:
        return "🌗";
      case WANING_CRESCENT:
        return "🌘";
      default:
        return "";
    }
  }

  // Moon phase calculation using synodic month
  private enum MoonPhase {
    NEW_MOON, WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS,
    FULL_MOON, WANING_GIBBOUS, LAST_QUARTER, WANING_CRESCENT
  }

  private MoonPhase calculateMoonPhase(LocalDate date) {
    // Known new moon date (January 6, 2000 at 18:14 UTC)
    LocalDate knownNewMoon = LocalDate.of(2000, 1, 6);

    // Calculate days since known new moon
    long daysSince = java.time.temporal.ChronoUnit.DAYS.between(knownNewMoon, date);

    // Synodic month is approximately 29.53059 days
    double synodicMonth = 29.53059;

    // Calculate phase as a fraction (0 = new moon, 0.5 = full moon)
    double phase = (daysSince % synodicMonth) / synodicMonth;

    // Determine moon phase based on the fraction
    if (phase < 0.0625 || phase >= 0.9375) {
      return MoonPhase.NEW_MOON;
    } else if (phase < 0.1875) {
      return MoonPhase.WAXING_CRESCENT;
    } else if (phase < 0.3125) {
      return MoonPhase.FIRST_QUARTER;
    } else if (phase < 0.4375) {
      return MoonPhase.WAXING_GIBBOUS;
    } else if (phase < 0.5625) {
      return MoonPhase.FULL_MOON;
    } else if (phase < 0.6875) {
      return MoonPhase.WANING_GIBBOUS;
    } else if (phase < 0.8125) {
      return MoonPhase.LAST_QUARTER;
    } else {
      return MoonPhase.WANING_CRESCENT;
    }
  }

  // Clean PDF metadata to remove backend technology fingerprints
  private byte[] cleanPdfMetadata(byte[] pdfBytes, int year) {
    try {
      // Load the PDF document
      // PDFBox 3.x uses Loader class
      PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdfBytes);

      // Get and modify the document information
      PDDocumentInformation info = document.getDocumentInformation();

      // Clear existing metadata that reveals technology
      info.setProducer("villagecompute.com");  // Override Apache FOP producer string
      info.setCreator("Village Compute Calendar Generator");     // Override creator
      info.setTitle("Calendar " + year);
      info.setSubject("Calendar");

      // Remove or override any custom metadata that might exist
      info.setAuthor("");
      info.setKeywords("");

      // Save the modified PDF to a byte array
      ByteArrayOutputStream cleanOutputStream = new ByteArrayOutputStream();
      document.save(cleanOutputStream);
      document.close();

      return cleanOutputStream.toByteArray();

    } catch (Exception e) {
      // If cleaning fails, return original PDF
      System.err.println("Warning: Could not clean PDF metadata: " + e.getMessage());
      return pdfBytes;
    }
  }

  // Generate PDF using Apache Batik
  public byte[] generateCalendarPDF(CalendarConfig config) {
    try {
      // Generate SVG content
      String svgContent = generateCalendarSVG(config);

      // Debug: Log SVG content length
      System.out.println("SVG content length: " + svgContent.length());

      // Wrap SVG with margins for proper print layout
      String wrappedSvg = wrapSvgWithMargins(svgContent);

      // Create transcoder for PDF
      Transcoder transcoder = new PDFTranscoder();

      // PDF page size using constants (in points: 1 inch = 72 points)
      float pageWidthInPoints = PAGE_WIDTH_INCHES * POINTS_PER_INCH;
      float pageHeightInPoints = PAGE_HEIGHT_INCHES * POINTS_PER_INCH;

      // Set the page/output size
      transcoder.addTranscodingHint(
        PDFTranscoder.KEY_WIDTH,
        pageWidthInPoints
      );
      transcoder.addTranscodingHint(
        PDFTranscoder.KEY_HEIGHT,
        pageHeightInPoints
      );

      // Create input and output
      StringReader reader = new StringReader(wrappedSvg);
      TranscoderInput input = new TranscoderInput(reader);

      // Set a dummy URI to avoid null pointer exception with style elements
      // This is required when the SVG contains <style> tags
      input.setURI("file:///calendar.svg");

      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      TranscoderOutput output = new TranscoderOutput(outputStream);

      // Perform the transcoding
      transcoder.transcode(input, output);

      byte[] pdfBytes = outputStream.toByteArray();

      // Post-process the PDF to clean metadata
      pdfBytes = cleanPdfMetadata(pdfBytes, config.year);

      System.out.println("PDF generated successfully, size: " + pdfBytes.length + " bytes");

      return pdfBytes;

    } catch (Exception e) {
      System.err.println("Error generating PDF: " + e.getMessage());
      e.printStackTrace();
      // Return empty array on error
      return new byte[0];
    }
  }

  /**
   * Wraps the generated SVG with an outer SVG that adds margins for print layout.
   * The inner SVG is scaled to fit within the printable area (page size minus margins)
   * and centered on the page.
   */
  private String wrapSvgWithMargins(String innerSvg) {
    // Extract the viewBox from the inner SVG to get its dimensions
    java.util.regex.Pattern viewBoxPattern = java.util.regex.Pattern.compile("viewBox=\"([\\d.]+)\\s+([\\d.]+)\\s+([\\d.]+)\\s+([\\d.]+)\"");
    java.util.regex.Matcher matcher = viewBoxPattern.matcher(innerSvg);

    if (!matcher.find()) {
      // If no viewBox, return original SVG
      System.out.println("Warning: No viewBox found in SVG, returning without margins");
      return innerSvg;
    }

    float innerWidth = Float.parseFloat(matcher.group(3));
    float innerHeight = Float.parseFloat(matcher.group(4));

    // Calculate the printable area in the same units as the page
    // We'll use a coordinate system where the page is PAGE_WIDTH_INCHES x PAGE_HEIGHT_INCHES
    float pageWidth = PAGE_WIDTH_INCHES * 100;  // Scale up for better precision
    float pageHeight = PAGE_HEIGHT_INCHES * 100;
    float marginSize = MARGIN_INCHES * 100;
    float printableWidth = PRINTABLE_WIDTH_INCHES * 100;
    float printableHeight = PRINTABLE_HEIGHT_INCHES * 100;

    // Calculate scale to fit inner SVG within printable area while maintaining aspect ratio
    float scaleX = printableWidth / innerWidth;
    float scaleY = printableHeight / innerHeight;
    float scale = Math.min(scaleX, scaleY);

    // Calculate the actual size of the scaled content
    float scaledWidth = innerWidth * scale;
    float scaledHeight = innerHeight * scale;

    // Calculate offset - center horizontally, align to top vertically
    float offsetX = marginSize + (printableWidth - scaledWidth) / 2;
    float offsetY = marginSize; // Top-aligned, extra space goes to bottom

    // Remove the <?xml...?> declaration if present from inner SVG
    String cleanedInnerSvg = innerSvg.replaceFirst("<\\?xml[^?]*\\?>\\s*", "");

    // Build the wrapper SVG
    StringBuilder wrapper = new StringBuilder();
    wrapper.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    wrapper.append(String.format(
      "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"%.0f\" height=\"%.0f\" viewBox=\"0 0 %.0f %.0f\">\n",
      pageWidth, pageHeight, pageWidth, pageHeight
    ));

    // Add a white background for the full page
    wrapper.append(String.format(
      "  <rect width=\"%.0f\" height=\"%.0f\" fill=\"white\"/>\n",
      pageWidth, pageHeight
    ));

    // Position and scale the inner SVG content within the margins
    wrapper.append(String.format(
      "  <g transform=\"translate(%.2f, %.2f) scale(%.6f)\">\n",
      offsetX, offsetY, scale
    ));

    // Insert the inner SVG content (strip the outer <svg> tags)
    String innerContent = cleanedInnerSvg
      .replaceFirst("<svg[^>]*>", "")  // Remove opening <svg> tag
      .replaceFirst("</svg>\\s*$", ""); // Remove closing </svg> tag

    wrapper.append(innerContent);
    wrapper.append("\n  </g>\n");
    wrapper.append("</svg>");

    return wrapper.toString();
  }

  // Get common holidays for a year
  public Set<String> getHolidays(int year, String country) {
    Set<String> holidays = new HashSet<>();

    if ("US".equals(country)) {
      // New Year's Day
      holidays.add(LocalDate.of(year, 1, 1).toString());

      // Martin Luther King Jr. Day (3rd Monday in January)
      LocalDate mlkDay = getNthWeekdayOfMonth(year, Month.JANUARY, DayOfWeek.MONDAY, 3);
      holidays.add(mlkDay.toString());

      // Presidents' Day (3rd Monday in February)
      LocalDate presidentsDay = getNthWeekdayOfMonth(year, Month.FEBRUARY, DayOfWeek.MONDAY, 3);
      holidays.add(presidentsDay.toString());

      // Memorial Day (last Monday in May)
      LocalDate memorialDay = getLastWeekdayOfMonth(year, Month.MAY, DayOfWeek.MONDAY);
      holidays.add(memorialDay.toString());

      // Independence Day
      holidays.add(LocalDate.of(year, 7, 4).toString());

      // Labor Day (1st Monday in September)
      LocalDate laborDay = getNthWeekdayOfMonth(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 1);
      holidays.add(laborDay.toString());

      // Halloween
      holidays.add(LocalDate.of(year, 10, 31).toString());

      // Veterans Day
      holidays.add(LocalDate.of(year, 11, 11).toString());

      // Thanksgiving (4th Thursday in November)
      LocalDate thanksgiving = getNthWeekdayOfMonth(year, Month.NOVEMBER, DayOfWeek.THURSDAY, 4);
      holidays.add(thanksgiving.toString());

      // Christmas Day
      holidays.add(LocalDate.of(year, 12, 25).toString());
    } else if ("JEWISH".equals(country) || "HEBREW".equals(country)) {
      // Jewish holidays - calculated dynamically from Hebrew calendar
      holidays.addAll(holidayService.getJewishHolidays(year).keySet());
    } else if ("CHRISTIAN".equals(country)) {
      // Christian holidays
      // Easter Sunday (varies by year - using common dates)
      LocalDate easter = calculateEasterSunday(year);
      holidays.add(easter.toString()); // Easter Sunday

      // Good Friday (2 days before Easter)
      holidays.add(easter.minusDays(2).toString()); // Good Friday

      // Palm Sunday (7 days before Easter)
      holidays.add(easter.minusDays(7).toString()); // Palm Sunday

      // Ash Wednesday (46 days before Easter)
      holidays.add(easter.minusDays(46).toString()); // Ash Wednesday

      // Ascension Day (39 days after Easter)
      holidays.add(easter.plusDays(39).toString()); // Ascension Day

      // Pentecost (49 days after Easter)
      holidays.add(easter.plusDays(49).toString()); // Pentecost

      // Christmas Day
      holidays.add(LocalDate.of(year, 12, 25).toString());

      // Christmas Eve
      holidays.add(LocalDate.of(year, 12, 24).toString());

      // Epiphany
      holidays.add(LocalDate.of(year, 1, 6).toString());

      // All Saints Day
      holidays.add(LocalDate.of(year, 11, 1).toString());
    } else if ("CANADIAN".equals(country)) {
      // Canadian holidays
      holidays.add(LocalDate.of(year, 1, 1).toString()); // New Year's Day

      // Family Day - 3rd Monday in February (varies by province)
      LocalDate familyDay = getNthWeekdayOfMonth(year, Month.FEBRUARY, DayOfWeek.MONDAY, 3);
      holidays.add(familyDay.toString());

      // Good Friday
      LocalDate easter = calculateEasterSunday(year);
      holidays.add(easter.minusDays(2).toString());

      // Victoria Day - Monday before May 25
      LocalDate victoriaDay = LocalDate.of(year, 5, 25);
      while (victoriaDay.getDayOfWeek() != DayOfWeek.MONDAY) {
        victoriaDay = victoriaDay.minusDays(1);
      }
      holidays.add(victoriaDay.toString());

      // Canada Day - July 1
      holidays.add(LocalDate.of(year, 7, 1).toString());

      // Labour Day - 1st Monday in September
      LocalDate labourDay = getNthWeekdayOfMonth(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 1);
      holidays.add(labourDay.toString());

      // Thanksgiving - 2nd Monday in October
      LocalDate thanksgiving = getNthWeekdayOfMonth(year, Month.OCTOBER, DayOfWeek.MONDAY, 2);
      holidays.add(thanksgiving.toString());

      // Remembrance Day - November 11
      holidays.add(LocalDate.of(year, 11, 11).toString());

      // Christmas Day
      holidays.add(LocalDate.of(year, 12, 25).toString());

      // Boxing Day
      holidays.add(LocalDate.of(year, 12, 26).toString());
    } else if ("UK".equals(country)) {
      // UK Bank Holidays
      holidays.add(LocalDate.of(year, 1, 1).toString()); // New Year's Day

      // Good Friday and Easter Monday
      LocalDate easter = calculateEasterSunday(year);
      holidays.add(easter.minusDays(2).toString()); // Good Friday
      holidays.add(easter.plusDays(1).toString()); // Easter Monday

      // Early May Bank Holiday - 1st Monday in May
      LocalDate earlyMay = getNthWeekdayOfMonth(year, Month.MAY, DayOfWeek.MONDAY, 1);
      holidays.add(earlyMay.toString());

      // Spring Bank Holiday - Last Monday in May
      LocalDate springBank = getLastWeekdayOfMonth(year, Month.MAY, DayOfWeek.MONDAY);
      holidays.add(springBank.toString());

      // Summer Bank Holiday - Last Monday in August
      LocalDate summerBank = getLastWeekdayOfMonth(year, Month.AUGUST, DayOfWeek.MONDAY);
      holidays.add(summerBank.toString());

      // Christmas Day
      holidays.add(LocalDate.of(year, 12, 25).toString());

      // Boxing Day
      holidays.add(LocalDate.of(year, 12, 26).toString());
    } else if ("MAJOR_WORLD".equals(country)) {
      // Major world holidays (combination of various cultures)
      holidays.add(LocalDate.of(year, 1, 1).toString()); // New Year's Day
      holidays.add(LocalDate.of(year, 2, 14).toString()); // Valentine's Day
      holidays.add(LocalDate.of(year, 3, 17).toString()); // St. Patrick's Day
      holidays.add(LocalDate.of(year, 4, 22).toString()); // Earth Day
      holidays.add(LocalDate.of(year, 5, 1).toString()); // International Workers' Day
      holidays.add(LocalDate.of(year, 10, 31).toString()); // Halloween
      holidays.add(LocalDate.of(year, 12, 25).toString()); // Christmas
      holidays.add(LocalDate.of(year, 12, 31).toString()); // New Year's Eve

      // Easter
      LocalDate easter = calculateEasterSunday(year);
      holidays.add(easter.toString());
    } else if ("MEXICAN".equals(country)) {
      // Mexican holidays - calculated dynamically
      holidays.addAll(holidayService.getMexicanHolidays(year).keySet());
    } else if ("PAGAN".equals(country) || "WICCAN".equals(country)) {
      // Pagan/Wiccan holidays (Wheel of the Year)
      holidays.addAll(holidayService.getPaganHolidays(year).keySet());
    } else if ("HINDU".equals(country)) {
      // Hindu holidays
      holidays.addAll(holidayService.getHinduHolidays(year).keySet());
    } else if ("ISLAMIC".equals(country) || "MUSLIM".equals(country)) {
      // Islamic holidays
      holidays.addAll(holidayService.getIslamicHolidays(year).keySet());
    } else if ("CHINESE".equals(country) || "LUNAR".equals(country)) {
      // Chinese/Lunar New Year holidays
      holidays.addAll(holidayService.getChineseHolidays(year).keySet());
    } else if ("SECULAR".equals(country) || "FUN".equals(country)) {
      // Fun/Secular American holidays
      holidays.addAll(holidayService.getSecularHolidays(year).keySet());
    }

    return holidays;
  }

  // Get holidays with emoji mappings for a year
  public Map<String, String> getHolidaysWithEmoji(int year, String country) {
    Map<String, String> holidayEmojis = new HashMap<>();

    if ("US".equals(country)) {
      // US Holidays with emojis
      holidayEmojis.put(LocalDate.of(year, 1, 1).toString(), "🎉"); // New Year's Day

      LocalDate mlkDay = getNthWeekdayOfMonth(year, Month.JANUARY, DayOfWeek.MONDAY, 3);
      holidayEmojis.put(mlkDay.toString(), "🕊️"); // MLK Day

      LocalDate presidentsDay = getNthWeekdayOfMonth(year, Month.FEBRUARY, DayOfWeek.MONDAY, 3);
      holidayEmojis.put(presidentsDay.toString(), "🏛️"); // Presidents' Day

      LocalDate memorialDay = getLastWeekdayOfMonth(year, Month.MAY, DayOfWeek.MONDAY);
      holidayEmojis.put(memorialDay.toString(), "🎖️"); // Memorial Day

      holidayEmojis.put(LocalDate.of(year, 7, 4).toString(), "🇺🇸"); // Independence Day

      LocalDate laborDay = getNthWeekdayOfMonth(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 1);
      holidayEmojis.put(laborDay.toString(), "👷"); // Labor Day

      holidayEmojis.put(LocalDate.of(year, 10, 31).toString(), "🎃"); // Halloween

      holidayEmojis.put(LocalDate.of(year, 11, 11).toString(), "🎖️"); // Veterans Day

      LocalDate thanksgiving = getNthWeekdayOfMonth(year, Month.NOVEMBER, DayOfWeek.THURSDAY, 4);
      holidayEmojis.put(thanksgiving.toString(), "🦃"); // Thanksgiving

      holidayEmojis.put(LocalDate.of(year, 12, 25).toString(), "🎄"); // Christmas

    } else if ("JEWISH".equals(country) || "HEBREW".equals(country)) {
      // Jewish holidays with emojis - calculated dynamically
      holidayEmojis.putAll(holidayService.getJewishHolidaysWithEmoji(year));

    } else if ("CHRISTIAN".equals(country)) {
      LocalDate easter = calculateEasterSunday(year);
      holidayEmojis.put(easter.toString(), "🐑"); // Easter Sunday (Lamb of God)
      holidayEmojis.put(easter.minusDays(2).toString(), "🐟"); // Good Friday (Fish)
      holidayEmojis.put(easter.minusDays(7).toString(), "🌿"); // Palm Sunday
      holidayEmojis.put(easter.minusDays(46).toString(), "✝️"); // Ash Wednesday
      holidayEmojis.put(easter.plusDays(39).toString(), "☁️"); // Ascension Day
      holidayEmojis.put(easter.plusDays(49).toString(), "🕊️"); // Pentecost
      holidayEmojis.put(LocalDate.of(year, 12, 25).toString(), "🎄"); // Christmas
      holidayEmojis.put(LocalDate.of(year, 12, 24).toString(), "🕯️"); // Christmas Eve (Candle)
      holidayEmojis.put(LocalDate.of(year, 1, 6).toString(), "⭐"); // Epiphany
      holidayEmojis.put(LocalDate.of(year, 11, 1).toString(), "👼"); // All Saints Day

    } else if ("CANADIAN".equals(country)) {
      holidayEmojis.put(LocalDate.of(year, 1, 1).toString(), "🎉"); // New Year's Day
      LocalDate familyDay = getNthWeekdayOfMonth(year, Month.FEBRUARY, DayOfWeek.MONDAY, 3);
      holidayEmojis.put(familyDay.toString(), "👨‍👩‍👧‍👦"); // Family Day
      LocalDate easter = calculateEasterSunday(year);
      holidayEmojis.put(easter.minusDays(2).toString(), "🐟"); // Good Friday (Fish)
      LocalDate victoriaDay = LocalDate.of(year, 5, 25);
      while (victoriaDay.getDayOfWeek() != DayOfWeek.MONDAY) {
        victoriaDay = victoriaDay.minusDays(1);
      }
      holidayEmojis.put(victoriaDay.toString(), "👑"); // Victoria Day
      holidayEmojis.put(LocalDate.of(year, 7, 1).toString(), "🍁"); // Canada Day
      LocalDate labourDay = getNthWeekdayOfMonth(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 1);
      holidayEmojis.put(labourDay.toString(), "👷"); // Labour Day
      LocalDate thanksgiving = getNthWeekdayOfMonth(year, Month.OCTOBER, DayOfWeek.MONDAY, 2);
      holidayEmojis.put(thanksgiving.toString(), "🦃"); // Thanksgiving
      holidayEmojis.put(LocalDate.of(year, 11, 11).toString(), "🎖️"); // Remembrance Day
      holidayEmojis.put(LocalDate.of(year, 12, 25).toString(), "🎄"); // Christmas
      holidayEmojis.put(LocalDate.of(year, 12, 26).toString(), "🎁"); // Boxing Day

    } else if ("UK".equals(country)) {
      holidayEmojis.put(LocalDate.of(year, 1, 1).toString(), "🎉"); // New Year's Day
      LocalDate easter = calculateEasterSunday(year);
      holidayEmojis.put(easter.minusDays(2).toString(), "🐟"); // Good Friday (Fish)
      holidayEmojis.put(easter.plusDays(1).toString(), "🐰"); // Easter Monday
      LocalDate earlyMay = getNthWeekdayOfMonth(year, Month.MAY, DayOfWeek.MONDAY, 1);
      holidayEmojis.put(earlyMay.toString(), "🌸"); // Early May Bank Holiday
      LocalDate springBank = getLastWeekdayOfMonth(year, Month.MAY, DayOfWeek.MONDAY);
      holidayEmojis.put(springBank.toString(), "🌷"); // Spring Bank Holiday
      LocalDate summerBank = getLastWeekdayOfMonth(year, Month.AUGUST, DayOfWeek.MONDAY);
      holidayEmojis.put(summerBank.toString(), "☀️"); // Summer Bank Holiday
      holidayEmojis.put(LocalDate.of(year, 12, 25).toString(), "🎄"); // Christmas
      holidayEmojis.put(LocalDate.of(year, 12, 26).toString(), "🎁"); // Boxing Day

    } else if ("MAJOR_WORLD".equals(country)) {
      holidayEmojis.put(LocalDate.of(year, 1, 1).toString(), "🎉"); // New Year's Day
      holidayEmojis.put(LocalDate.of(year, 2, 14).toString(), "❤️"); // Valentine's Day
      holidayEmojis.put(LocalDate.of(year, 3, 17).toString(), "☘️"); // St. Patrick's Day
      holidayEmojis.put(LocalDate.of(year, 4, 22).toString(), "🌍"); // Earth Day
      holidayEmojis.put(LocalDate.of(year, 5, 1).toString(), "👷"); // International Workers' Day
      holidayEmojis.put(LocalDate.of(year, 10, 31).toString(), "🎃"); // Halloween
      holidayEmojis.put(LocalDate.of(year, 12, 25).toString(), "🎄"); // Christmas
      holidayEmojis.put(LocalDate.of(year, 12, 31).toString(), "🎉"); // New Year's Eve
      LocalDate easter = calculateEasterSunday(year);
      holidayEmojis.put(easter.toString(), "🐰"); // Easter

    } else if ("MEXICAN".equals(country)) {
      // Mexican holidays with emojis
      holidayEmojis.putAll(holidayService.getMexicanHolidaysWithEmoji(year));

    } else if ("PAGAN".equals(country) || "WICCAN".equals(country)) {
      // Pagan/Wiccan holidays with emojis
      holidayEmojis.putAll(holidayService.getPaganHolidaysWithEmoji(year));

    } else if ("HINDU".equals(country)) {
      // Hindu holidays with emojis
      holidayEmojis.putAll(holidayService.getHinduHolidaysWithEmoji(year));

    } else if ("ISLAMIC".equals(country) || "MUSLIM".equals(country)) {
      // Islamic holidays with emojis
      holidayEmojis.putAll(holidayService.getIslamicHolidaysWithEmoji(year));

    } else if ("CHINESE".equals(country) || "LUNAR".equals(country)) {
      // Chinese holidays with emojis
      holidayEmojis.putAll(holidayService.getChineseHolidaysWithEmoji(year));

    } else if ("SECULAR".equals(country) || "FUN".equals(country)) {
      // Secular holidays with emojis
      holidayEmojis.putAll(holidayService.getSecularHolidaysWithEmoji(year));
    }

    return holidayEmojis;
  }

  // Get holiday names for text display modes
  public Map<String, String> getHolidayNames(int year, String country) {
    Map<String, String> holidayNames = new HashMap<>();

    if ("US".equals(country)) {
      holidayNames.put(LocalDate.of(year, 1, 1).toString(), "New Year's Day");
      LocalDate mlkDay = getNthWeekdayOfMonth(year, Month.JANUARY, DayOfWeek.MONDAY, 3);
      holidayNames.put(mlkDay.toString(), "MLK Day");
      LocalDate presidentsDay = getNthWeekdayOfMonth(year, Month.FEBRUARY, DayOfWeek.MONDAY, 3);
      holidayNames.put(presidentsDay.toString(), "Presidents' Day");
      LocalDate memorialDay = getLastWeekdayOfMonth(year, Month.MAY, DayOfWeek.MONDAY);
      holidayNames.put(memorialDay.toString(), "Memorial Day");
      holidayNames.put(LocalDate.of(year, 7, 4).toString(), "Independence Day");
      LocalDate laborDay = getNthWeekdayOfMonth(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 1);
      holidayNames.put(laborDay.toString(), "Labor Day");
      holidayNames.put(LocalDate.of(year, 10, 31).toString(), "Halloween");
      holidayNames.put(LocalDate.of(year, 11, 11).toString(), "Veterans Day");
      LocalDate thanksgiving = getNthWeekdayOfMonth(year, Month.NOVEMBER, DayOfWeek.THURSDAY, 4);
      holidayNames.put(thanksgiving.toString(), "Thanksgiving");
      holidayNames.put(LocalDate.of(year, 12, 25).toString(), "Christmas");

    } else if ("JEWISH".equals(country) || "HEBREW".equals(country)) {
      holidayNames.putAll(holidayService.getJewishHolidays(year));

    } else if ("CHRISTIAN".equals(country)) {
      LocalDate easter = calculateEasterSunday(year);
      holidayNames.put(easter.toString(), "Easter");
      holidayNames.put(easter.minusDays(2).toString(), "Good Friday");
      holidayNames.put(easter.minusDays(7).toString(), "Palm Sunday");
      holidayNames.put(easter.minusDays(46).toString(), "Ash Wednesday");
      holidayNames.put(easter.plusDays(39).toString(), "Ascension");
      holidayNames.put(easter.plusDays(49).toString(), "Pentecost");
      holidayNames.put(LocalDate.of(year, 12, 25).toString(), "Christmas");
      holidayNames.put(LocalDate.of(year, 12, 24).toString(), "Christmas Eve");
      holidayNames.put(LocalDate.of(year, 1, 6).toString(), "Epiphany");
      holidayNames.put(LocalDate.of(year, 11, 1).toString(), "All Saints");

    } else if ("CANADIAN".equals(country)) {
      holidayNames.put(LocalDate.of(year, 1, 1).toString(), "New Year's Day");
      LocalDate familyDay = getNthWeekdayOfMonth(year, Month.FEBRUARY, DayOfWeek.MONDAY, 3);
      holidayNames.put(familyDay.toString(), "Family Day");
      LocalDate easter = calculateEasterSunday(year);
      holidayNames.put(easter.minusDays(2).toString(), "Good Friday");
      LocalDate victoriaDay = LocalDate.of(year, 5, 25);
      while (victoriaDay.getDayOfWeek() != DayOfWeek.MONDAY) {
        victoriaDay = victoriaDay.minusDays(1);
      }
      holidayNames.put(victoriaDay.toString(), "Victoria Day");
      holidayNames.put(LocalDate.of(year, 7, 1).toString(), "Canada Day");
      LocalDate labourDay = getNthWeekdayOfMonth(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 1);
      holidayNames.put(labourDay.toString(), "Labour Day");
      LocalDate thanksgiving = getNthWeekdayOfMonth(year, Month.OCTOBER, DayOfWeek.MONDAY, 2);
      holidayNames.put(thanksgiving.toString(), "Thanksgiving");
      holidayNames.put(LocalDate.of(year, 11, 11).toString(), "Remembrance Day");
      holidayNames.put(LocalDate.of(year, 12, 25).toString(), "Christmas");
      holidayNames.put(LocalDate.of(year, 12, 26).toString(), "Boxing Day");

    } else if ("UK".equals(country)) {
      holidayNames.put(LocalDate.of(year, 1, 1).toString(), "New Year's Day");
      LocalDate easter = calculateEasterSunday(year);
      holidayNames.put(easter.minusDays(2).toString(), "Good Friday");
      holidayNames.put(easter.plusDays(1).toString(), "Easter Monday");
      LocalDate earlyMay = getNthWeekdayOfMonth(year, Month.MAY, DayOfWeek.MONDAY, 1);
      holidayNames.put(earlyMay.toString(), "Early May");
      LocalDate springBank = getLastWeekdayOfMonth(year, Month.MAY, DayOfWeek.MONDAY);
      holidayNames.put(springBank.toString(), "Spring Bank");
      LocalDate summerBank = getLastWeekdayOfMonth(year, Month.AUGUST, DayOfWeek.MONDAY);
      holidayNames.put(summerBank.toString(), "Summer Bank");
      holidayNames.put(LocalDate.of(year, 12, 25).toString(), "Christmas");
      holidayNames.put(LocalDate.of(year, 12, 26).toString(), "Boxing Day");

    } else if ("MAJOR_WORLD".equals(country)) {
      holidayNames.put(LocalDate.of(year, 1, 1).toString(), "New Year's Day");
      holidayNames.put(LocalDate.of(year, 2, 14).toString(), "Valentine's Day");
      holidayNames.put(LocalDate.of(year, 3, 17).toString(), "St. Patrick's");
      holidayNames.put(LocalDate.of(year, 4, 22).toString(), "Earth Day");
      holidayNames.put(LocalDate.of(year, 5, 1).toString(), "Workers' Day");
      holidayNames.put(LocalDate.of(year, 10, 31).toString(), "Halloween");
      holidayNames.put(LocalDate.of(year, 12, 25).toString(), "Christmas");
      holidayNames.put(LocalDate.of(year, 12, 31).toString(), "New Year's Eve");
      LocalDate easter = calculateEasterSunday(year);
      holidayNames.put(easter.toString(), "Easter");

    } else if ("MEXICAN".equals(country)) {
      holidayNames.putAll(holidayService.getMexicanHolidays(year));

    } else if ("PAGAN".equals(country) || "WICCAN".equals(country)) {
      holidayNames.putAll(holidayService.getPaganHolidays(year));

    } else if ("HINDU".equals(country)) {
      holidayNames.putAll(holidayService.getHinduHolidays(year));

    } else if ("ISLAMIC".equals(country) || "MUSLIM".equals(country)) {
      holidayNames.putAll(holidayService.getIslamicHolidays(year));

    } else if ("CHINESE".equals(country) || "LUNAR".equals(country)) {
      holidayNames.putAll(holidayService.getChineseHolidays(year));

    } else if ("SECULAR".equals(country) || "FUN".equals(country)) {
      holidayNames.putAll(holidayService.getSecularHolidays(year));
    }

    return holidayNames;
  }

  // Calculate Easter Sunday using the Anonymous Gregorian algorithm
  private LocalDate calculateEasterSunday(int year) {
    int a = year % 19;
    int b = year / 100;
    int c = year % 100;
    int d = b / 4;
    int e = b % 4;
    int f = (b + 8) / 25;
    int g = (b - f + 1) / 3;
    int h = (19 * a + b - d - g + 15) % 30;
    int i = c / 4;
    int k = c % 4;
    int l = (32 + 2 * e + 2 * i - h - k) % 7;
    int m = (a + 11 * h + 22 * l) / 451;
    int month = (h + l - 7 * m + 114) / 31;
    int day = ((h + l - 7 * m + 114) % 31) + 1;
    return LocalDate.of(year, month, day);
  }

  private LocalDate getNthWeekdayOfMonth(int year, Month month, DayOfWeek dayOfWeek, int n) {
    LocalDate firstDay = LocalDate.of(year, month, 1);
    LocalDate firstWeekday = firstDay;

    while (firstWeekday.getDayOfWeek() != dayOfWeek) {
      firstWeekday = firstWeekday.plusDays(1);
    }

    return firstWeekday.plusWeeks(n - 1);
  }

  private LocalDate getLastWeekdayOfMonth(int year, Month month, DayOfWeek dayOfWeek) {
    YearMonth yearMonth = YearMonth.of(year, month);
    LocalDate lastDay = yearMonth.atEndOfMonth();

    while (lastDay.getDayOfWeek() != dayOfWeek) {
      lastDay = lastDay.minusDays(1);
    }

    return lastDay;
  }

  // Generate SVG for moon illumination visualization
  public String generateMoonIlluminationSVG(LocalDate date, int x, int y, double latitude, double longitude, CalendarConfig config) {
    StringBuilder svg = new StringBuilder();

    // Calculate moon illumination and position
    MoonIllumination illumination = calculateMoonIllumination(date);
    double phase = illumination.phase;
    double illuminatedFraction = illumination.fraction;

    // Calculate rotation angle based on observer's location
    // If latitude and longitude are both 0 (no location selected), don't rotate
    double rotationAngle = 0.0;
    if (latitude != 0.0 || longitude != 0.0) {
      // Calculate moon position for observer's location
      MoonPosition position = calculateMoonPosition(date, latitude, longitude);
      double parallacticAngle = position.parallacticAngle;

      // The moon's terminator (shadow line) rotates based on the observer's latitude
      rotationAngle = Math.toDegrees(parallacticAngle) * -1 - 45;
    }

    // Moon circle radius from configuration
    int radius = config.moonSize;

    // Create a group for the moon with rotation
    svg.append(String.format("<g transform=\"translate(%d, %d) rotate(%.1f)\">%n", x, y, rotationAngle));

    // Background circle (dark side)
    svg.append(String.format(
      "<circle r=\"%d\" fill=\"%s\"/>%n",
      radius, config.moonDarkColor
    ));

    // Calculate the illuminated path
    // The moon phase determines which side is lit
    // phase: 0 = new moon, 0.25 = first quarter, 0.5 = full moon, 0.75 = last quarter

    if (illuminatedFraction > 0 && illuminatedFraction < 1) {
      // Create path for illuminated portion
      boolean isWaxing = phase < 0.5;
      boolean isRightSideLit = isWaxing;

      // Calculate the ellipse width for the terminator (shadow line)
      double ellipseWidth = Math.abs(Math.cos(phase * 2 * Math.PI)) * radius;

      // For SVG arcs: sweep-flag determines clockwise (1) or counter-clockwise (0) direction
      // We don't actually need sweepFlag for this moon rendering approach
      // The terminator line is created by combining two arcs with different radii
      String largeArcFlag = illuminatedFraction > 0.5 ? "1" : "0";

      // Build the path
      String path;
      if (phase < 0.25 || phase > 0.75) {
        // Crescent moon
        path = String.format(
          "M 0,-%d A %d,%d 0 %s,%s 0,%d A %.1f,%d 0 %s,%s 0,-%d",
          radius, radius, radius, largeArcFlag, isRightSideLit ? "1" : "0", radius,
          ellipseWidth, radius, largeArcFlag, isRightSideLit ? "0" : "1", radius
        );
      } else {
        // Gibbous moon
        path = String.format(
          "M 0,-%d A %d,%d 0 %s,%s 0,%d A %.1f,%d 0 %s,%s 0,-%d",
          radius, radius, radius, "1", isRightSideLit ? "1" : "0", radius,
          ellipseWidth, radius, "0", isRightSideLit ? "1" : "0", radius
        );
      }

    svg.append(String.format(
        "<path d=\"%s\" fill=\"%s\"/>%n",
        path, config.moonLightColor
    ));
    } else if (illuminatedFraction >= 1) {
      // Full moon
      svg.append(String.format(
        "<circle r=\"%d\" fill=\"%s\"/>%n",
        radius, config.moonLightColor
      ));
    }
    // New moon is just the dark circle

    // Border with configurable color and width
    svg.append(String.format(
      "<circle r=\"%d\" fill=\"none\" stroke=\"%s\" stroke-width=\"%.1f\"/>%n",
      radius, config.moonBorderColor, config.moonBorderWidth
    ));

    svg.append("</g>\n");

    return svg.toString();
  }

  // Moon illumination data class
  private static class MoonIllumination {
    double fraction; // 0 to 1, illuminated fraction
    double phase;    // 0 to 1, phase in the lunar cycle
    double angle;    // angle in radians

    MoonIllumination(double fraction, double phase, double angle) {
      this.fraction = fraction;
      this.phase = phase;
      this.angle = angle;
    }
  }

  // Moon position data class
  private static class MoonPosition {
    double azimuth;         // azimuth in radians
    double altitude;        // altitude in radians
    double parallacticAngle; // parallactic angle in radians

    MoonPosition(double azimuth, double altitude, double parallacticAngle) {
      this.azimuth = azimuth;
      this.altitude = altitude;
      this.parallacticAngle = parallacticAngle;
    }
  }

  // Calculate moon illumination for a given date
  private MoonIllumination calculateMoonIllumination(LocalDate date) {
    // Use the same phase calculation for consistency
    double phase = calculateMoonPhaseValue(date);

    // Calculate illumination fraction using the phase
    // This is a simplified calculation
    double illumination = (1 - Math.cos(phase * 2 * Math.PI)) / 2;

    // Angle (simplified - actual calculation would need sun position)
    double angle = phase * 2 * Math.PI;

    return new MoonIllumination(illumination, phase, angle);
  }

  // Calculate moon position for observer's location
  private MoonPosition calculateMoonPosition(LocalDate date, double latitude, double longitude) {
    // Improved moon position calculation with better hemisphere awareness
    // The moon appears differently based on observer's location

    // Convert to radians
    double lat = Math.toRadians(latitude);
    double lng = Math.toRadians(longitude);

    // Calculate moon's approximate position in its orbit
    // Using synodic month for phase calculation
    LocalDate j2000 = LocalDate.of(2000, 1, 1);
    long daysSinceJ2000 = java.time.temporal.ChronoUnit.DAYS.between(j2000, date);
    double synodicMonth = 29.53058867;
    double moonAge = (daysSinceJ2000 - 5.5) % synodicMonth;
    if (moonAge < 0) moonAge += synodicMonth;

    // Calculate approximate moon declination
    // Moon's orbit is inclined about 5.14° to ecliptic, ecliptic is inclined 23.44° to equator
    // This gives moon declination range of approximately ±28.6°
    double moonOrbitPhase = (moonAge / synodicMonth) * 2 * Math.PI;
    double moonDeclination = Math.toRadians(28.6 * Math.sin(moonOrbitPhase + date.getDayOfYear() * Math.PI / 182.625));

    // Calculate hour angle based on time and longitude
    // This is simplified - actual calculation would need precise time
    double hourAngle = (date.getDayOfMonth() / 30.0) * 2 * Math.PI + lng;

    // Calculate altitude
    double altitude = Math.asin(
      Math.sin(lat) * Math.sin(moonDeclination) +
        Math.cos(lat) * Math.cos(moonDeclination) * Math.cos(hourAngle)
    );

    // Calculate azimuth
    double azimuth = Math.atan2(
      -Math.sin(hourAngle),
      Math.tan(moonDeclination) * Math.cos(lat) - Math.sin(lat) * Math.cos(hourAngle)
    );

    // Calculate parallactic angle - the rotation of the moon from observer's viewpoint
    // This is the key to showing different moon orientations by hemisphere
    double parallacticAngle;

    if (Math.abs(latitude) < 1.0) {
      // Near equator: moon appears to rotate throughout the night
      // East-West orientation changes
      parallacticAngle = hourAngle;
          } else {
      // Calculate standard parallactic angle
      double sinPA = Math.sin(hourAngle) * Math.cos(moonDeclination) / Math.cos(altitude);
      double cosPA = (Math.sin(moonDeclination) * Math.cos(lat) -
        Math.cos(moonDeclination) * Math.sin(lat) * Math.cos(hourAngle)) / Math.cos(altitude);
      parallacticAngle = Math.atan2(sinPA, cosPA);

      // Hemisphere adjustments
      if (latitude < 0) {
        // Southern hemisphere: moon appears "upside down" relative to northern view
        parallacticAngle += Math.PI;
      }

      // Additional adjustment based on latitude magnitude
      // This creates a gradual transition from equator to poles
      double latitudeFactor = Math.abs(latitude) / 90.0;
      parallacticAngle += (1 - latitudeFactor) * hourAngle * 0.3;
      }

    return new MoonPosition(azimuth, altitude, parallacticAngle);
    }

  // Calculate cell background color based on theme
  // Convert color formats to simple formats that Apache Batik can handle
  public static String convertColorForPDF(String color) {
    if (color == null || color.isEmpty()) {
      return "none";
    }

    // Handle rgba with transparency - convert to "none" if fully transparent
    if (color.startsWith("rgba")) {
      // Check if it's transparent (alpha = 0)
      if (color.contains(",0)") || color.contains(", 0)")) {
        return "none";
      }
      // Convert rgba to rgb (drop alpha channel)
      return color.replace("rgba", "rgb").replaceAll(",\\s*[0-9.]+\\)$", ")");
    }

    // Handle hsl format - convert to rgb
    if (color.startsWith("hsl")) {
      // Parse HSL values
      String values = color.substring(color.indexOf("(") + 1, color.indexOf(")"));
      String[] parts = values.split(",");
      if (parts.length >= 3) {
        try {
          int h = Integer.parseInt(parts[0].trim());
          int s = Integer.parseInt(parts[1].trim().replace("%", ""));
          int l = Integer.parseInt(parts[2].trim().replace("%", ""));

          // Convert HSL to RGB
          float hue = h / 360f;
          float saturation = s / 100f;
          float lightness = l / 100f;

          float c = (1 - Math.abs(2 * lightness - 1)) * saturation;
          float x = c * (1 - Math.abs((hue * 6) % 2 - 1));
          float m = lightness - c / 2;

          float r, g, b;
          int hueSegment = (int)(hue * 6);
          switch (hueSegment) {
            case 0: r = c; g = x; b = 0; break;
            case 1: r = x; g = c; b = 0; break;
            case 2: r = 0; g = c; b = x; break;
            case 3: r = 0; g = x; b = c; break;
            case 4: r = x; g = 0; b = c; break;
            default: r = c; g = 0; b = x; break;
          }

          int red = Math.round((r + m) * 255);
          int green = Math.round((g + m) * 255);
          int blue = Math.round((b + m) * 255);

          return String.format("#%02x%02x%02x", red, green, blue);
        } catch (Exception e) {
          // If parsing fails, return the original color
          return color;
        }
      }
    }

    // Return as-is for hex colors and other formats
    return color;
  }

  public static String getCellBackgroundColor(CalendarConfig config, LocalDate date, int monthNum, int dayNum,
                                        boolean isWeekend, int weekendIndex) {
    String theme = config.theme;

    // Rainbow days themes
    if (theme.startsWith("rainbowDays")) {
      int hue;
      int saturation = 100;
      int lightness = 90;

      if ("rainbowDays1".equals(theme)) {
        // Color by day of week
        hue = date.getDayOfWeek().getValue() * 30;
        lightness = 90;
      } else if ("rainbowDays2".equals(theme)) {
        // Color by day of month
        hue = (int) ((dayNum / 30.0) * 360);
        saturation = 100;
        lightness = 80;
      } else if ("rainbowDays3".equals(theme)) {
        // Color by day with distance-based lightness
        hue = (int) ((dayNum / 30.0) * 360);
        // Calculate distance from corner (Dec 31)
        double monthWeight = 3;
        double distance = Math.sqrt(Math.pow(12 - monthNum * monthWeight, 2) + Math.pow(31 - dayNum, 2));
        double maxDistance = Math.sqrt(Math.pow(12 * monthWeight, 2) + Math.pow(31, 2));
        double normalizedDistance = distance / maxDistance;
        int lightnessMin = 80;
        int lightnessMax = 90;
        lightness = (int) (lightnessMin + (1 - normalizedDistance) * (lightnessMax - lightnessMin));
      } else {
        // Default rainbow days
        hue = (int) ((dayNum / 30.0) * 360);
      }

      return String.format("hsl(%d, %d%%, %d%%)", hue, saturation, lightness);
    }

    // Weekend-specific themes
    if (isWeekend) {
      if ("rainbowWeekends".equals(theme)) {
        int hue = (int) ((date.getDayOfMonth() / 30.0) * 360);
        return String.format("hsl(%d, 100%%, 90%%)", hue);
      } else if ("vermontWeekends".equals(theme)) {
        // Use Vermont monthly colors for weekends
        String[] monthColors = VERMONT_MONTHLY_COLORS[monthNum - 1];
        if (weekendIndex >= 0 && weekendIndex < monthColors.length) {
          return monthColors[weekendIndex];
        }
        return monthColors[0]; // Fallback to first color
      } else if ("lakeshoreWeekends".equals(theme)) {
        // Use Lakeshore monthly colors for weekends
        String[] monthColors = LAKESHORE_MONTHLY_COLORS[monthNum - 1];
        if (weekendIndex >= 0 && weekendIndex < monthColors.length) {
          return monthColors[weekendIndex];
        }
        return monthColors[0];
      } else if ("sunsetWeekends".equals(theme)) {
        // Use Sunset Glow monthly colors for weekends
        String[] monthColors = SUNSET_MONTHLY_COLORS[monthNum - 1];
        if (weekendIndex >= 0 && weekendIndex < monthColors.length) {
          return monthColors[weekendIndex];
        }
        return monthColors[0];
      } else if ("forestWeekends".equals(theme)) {
        // Use Forest Floor monthly colors for weekends
        String[] monthColors = FOREST_MONTHLY_COLORS[monthNum - 1];
        if (weekendIndex >= 0 && weekendIndex < monthColors.length) {
          return monthColors[weekendIndex];
        }
        return monthColors[0];
      } else if (config.highlightWeekends) {
        // First check if user has specified a custom weekend color
        if (config.weekendBgColor != null && !config.weekendBgColor.isEmpty()) {
          return config.weekendBgColor;
        }
        // Otherwise use theme default
        ThemeColors themeColors = THEMES.get(config.theme);
        if (themeColors != null && themeColors.weekendBackground != null) {
          return themeColors.weekendBackground;
        }
      }
    }

    return "rgba(255, 255, 255, 0)"; // Transparent
  }

  /**
   * Escapes special XML characters in a string.
   */
  private String escapeXml(String text) {
    if (text == null) return "";
    return text
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;");
  }
}
