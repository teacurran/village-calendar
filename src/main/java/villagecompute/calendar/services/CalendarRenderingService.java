package villagecompute.calendar.services;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.apache.batik.transcoder.SVGAbstractTranscoder;
import org.apache.batik.transcoder.Transcoder;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.fop.svg.PDFTranscoder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;

import villagecompute.calendar.types.CalendarConfigType;
import villagecompute.calendar.types.CustomDateEntryType;
import villagecompute.calendar.types.HolidayType;
import villagecompute.calendar.util.Colors;

import io.quarkus.logging.Log;

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
    public static final float PRINTABLE_WIDTH_INCHES = PAGE_WIDTH_INCHES - (2 * MARGIN_INCHES); // 34"
    public static final float PRINTABLE_HEIGHT_INCHES = PAGE_HEIGHT_INCHES - (2 * MARGIN_INCHES); // 22"

    // Points per inch (PDF standard)
    private static final float POINTS_PER_INCH = 72f;
    // ===========================================

    public static final String DEFAULT_THEME = "default";

    /** Emoji font constant for monochrome (black & white outline) emoji style. */
    public static final String EMOJI_FONT_NOTO_MONO = "noto-mono";

    /**
     * Returns the CSS font-family string for emoji rendering based on config. Noto Color Emoji is the default (Apache
     * 2.0 licensed, safe for commercial printing). Noto Emoji (monochrome) is available for a text-only look.
     */
    static String getEmojiFontFamily(CalendarConfigType config) {
        if (EMOJI_FONT_NOTO_MONO.equals(config.emojiFont)) {
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
        MONOCHROME_EMOJI_SUBSTITUTIONS.put("🕎", "✡️"); // Menorah -> Star of David
        // Mexican
        MONOCHROME_EMOJI_SUBSTITUTIONS.put("🎖️", "⭐"); // Military Medal -> Star
        // Hindu
        MONOCHROME_EMOJI_SUBSTITUTIONS.put("🪁", "☀️"); // Kite -> Sun (Makar Sankranti)
        MONOCHROME_EMOJI_SUBSTITUTIONS.put("🪈", "🎵"); // Flute -> Music Note (Janmashtami)
        MONOCHROME_EMOJI_SUBSTITUTIONS.put("🪔", "🕯️"); // Diya Lamp -> Candle (Diwali)
        // Islamic
        MONOCHROME_EMOJI_SUBSTITUTIONS.put("🤲", "🙏"); // Palms Up -> Folded Hands (Ashura)
        MONOCHROME_EMOJI_SUBSTITUTIONS.put("☪️", "🌙"); // Star and Crescent -> Crescent Moon
        // Chinese
        MONOCHROME_EMOJI_SUBSTITUTIONS.put("🧧", "🏮"); // Red Envelope -> Lantern (Chinese New Year)
        MONOCHROME_EMOJI_SUBSTITUTIONS.put("🪦", "🌸"); // Headstone -> Blossom (Qingming)
        MONOCHROME_EMOJI_SUBSTITUTIONS.put("🥮", "🌕"); // Moon Cake -> Full Moon (Mid-Autumn)
        MONOCHROME_EMOJI_SUBSTITUTIONS.put("🏔️", "⛰️"); // Snow Mountain -> Mountain (Double Ninth)
        // Secular
        MONOCHROME_EMOJI_SUBSTITUTIONS.put("🦫", "🐿️"); // Beaver -> Chipmunk (Groundhog Day)
        MONOCHROME_EMOJI_SUBSTITUTIONS.put("🏳️‍🌈", "🌈"); // Pride Flag -> Rainbow
    }

    // Emoji color variants mapping (emojiFont value -> hex color)
    private static final Map<String, String> EMOJI_COLOR_MAP = new HashMap<>();

    static {
        EMOJI_COLOR_MAP.put("mono-red", "#DC2626");
        EMOJI_COLOR_MAP.put("mono-blue", "#2563EB");
        EMOJI_COLOR_MAP.put("mono-green", "#16A34A");
        EMOJI_COLOR_MAP.put("mono-orange", "#EA580C");
        EMOJI_COLOR_MAP.put("mono-purple", "#9333EA");
        EMOJI_COLOR_MAP.put("mono-pink", "#EC4899");
        EMOJI_COLOR_MAP.put("mono-teal", "#0D9488");
        EMOJI_COLOR_MAP.put("mono-brown", "#92400E");
        EMOJI_COLOR_MAP.put("mono-navy", "#1E3A5F");
        EMOJI_COLOR_MAP.put("mono-coral", "#F97316");
    }

    /**
     * Substitutes emojis that aren't available in Noto Emoji monochrome font with compatible alternatives. Also applies
     * to colored monochrome variants.
     */
    static String substituteEmojiForMonochrome(String emoji, CalendarConfigType config) {
        // Apply substitutions for noto-mono and all mono-* color variants
        boolean needsSubstitution = EMOJI_FONT_NOTO_MONO.equals(config.emojiFont)
                || (config.emojiFont != null && config.emojiFont.startsWith("mono-"));
        if (!needsSubstitution) {
            return emoji; // No substitution needed for color mode
        }
        return MONOCHROME_EMOJI_SUBSTITUTIONS.getOrDefault(emoji, emoji);
    }

    /**
     * Render an emoji as either inline SVG (preferred for PDF) or text with font fallback. Uses EmojiSvgService for
     * vector rendering when available.
     *
     * @param emoji
     *            The emoji character(s) to render
     * @param x
     *            X position (center for centered, left for left-aligned)
     * @param y
     *            Y position (center for centered, baseline for text)
     * @param size
     *            Font size in pixels
     * @param config
     *            Calendar configuration
     * @param centered
     *            Whether the emoji should be centered at x,y
     * @return SVG element string (either <svg> or <text>)
     */
    private String renderEmoji(String emoji, double x, double y, int size, CalendarConfigType config,
            boolean centered) {
        // Check if monochrome mode is enabled (noto-mono or any mono-* color variant)
        boolean isMonochrome = EMOJI_FONT_NOTO_MONO.equals(config.emojiFont)
                || (config.emojiFont != null && config.emojiFont.startsWith("mono-"));
        // Get color for colored monochrome variants
        String colorHex = EMOJI_COLOR_MAP.get(config.emojiFont);

        // Try to use SVG rendering for better PDF compatibility
        if (emojiSvgService != null && emojiSvgService.hasEmojiSvg(emoji)) {
            // For SVG, x/y is top-left corner, so adjust if centered
            double svgX = centered ? x - size / 2.0 : x;
            double svgY = centered ? y - size / 2.0 : y - size; // text y is baseline, svg y is top
            return emojiSvgService.getEmojiAsSvg(emoji, svgX, svgY, size, isMonochrome, colorHex);
        }

        // Fall back to text rendering with emoji font
        String processedEmoji = substituteEmojiForMonochrome(emoji, config);
        if (centered) {
            return String.format(
                    "<text x=\"%.1f\" y=\"%.1f\" style=\"font-size: %dpx; text-anchor: middle;"
                            + " dominant-baseline: middle; font-family: %s;\">%s</text>",
                    x, y, size, getEmojiFontFamily(config), processedEmoji);
        } else {
            return String.format(
                    "<text x=\"%.1f\" y=\"%.1f\" style=\"font-size: %dpx; font-family:" + " %s;\">%s</text>", x, y,
                    size, getEmojiFontFamily(config), processedEmoji);
        }
    }

    /**
     * Renders a month name label with optional rotation. When rotated, text is centered in cellWidth and rotated -90
     * degrees. When not rotated, text is positioned at (x, y) with the specified anchor. Uses inline styles for
     * immediate visual update (no CSS class dependency).
     *
     * @param monthName
     *            The month name to display
     * @param x
     *            X position for non-rotated text
     * @param y
     *            Y position (vertical center of the row)
     * @param cellWidth
     *            Width of the month label cell (used for centering when rotated)
     * @param rotated
     *            Whether to rotate the text -90 degrees
     * @param textAnchor
     *            Text anchor for non-rotated text ("start", "middle", or "end")
     * @param fillColor
     *            The fill color for the text
     * @return SVG text element string
     */
    private String renderMonthName(String monthName, int x, int y, int cellWidth, boolean rotated, String textAnchor,
            String fillColor) {
        if (rotated) {
            int centerX = cellWidth / 2;
            return String.format(
                    "<text x=\"%d\" y=\"%d\" fill=\"%s\" font-family=\"Helvetica, Arial,"
                            + " sans-serif\" font-size=\"20px\" font-weight=\"bold\""
                            + " text-anchor=\"middle\" transform=\"rotate(-90 %d %d)\">%s</text>%n",
                    centerX, y, fillColor, centerX, y, monthName);
        } else {
            return String.format("<text x=\"%d\" y=\"%d\" fill=\"%s\" font-family=\"Helvetica, Arial,"
                    + " sans-serif\" font-size=\"20px\" font-weight=\"bold\"" + " text-anchor=\"%s\">%s</text>%n", x, y,
                    fillColor, textAnchor, monthName);
        }
    }

    /**
     * Renders holiday emoji and text based on eventDisplayMode. Shared between grid and weekday-grid layouts for
     * consistency.
     *
     * @param holidayEmoji
     *            The emoji to display (may be empty)
     * @param holidayName
     *            The holiday name for text modes (may be empty)
     * @param cell
     *            Cell position and dimensions
     * @param config
     *            Calendar configuration
     * @return SVG string for holiday content
     */
    private String renderHolidayContent(String holidayEmoji, String holidayName, Cell cell, CalendarConfigType config) {

        if ("none".equals(config.eventDisplayMode)) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        boolean largeEmoji = "large".equals(config.eventDisplayMode) || "large-text".equals(config.eventDisplayMode);
        boolean smallEmoji = "small".equals(config.eventDisplayMode) || "small-text".equals(config.eventDisplayMode);

        // Render emoji based on display mode
        if (largeEmoji && !holidayEmoji.isEmpty()) {
            // Large/large-text mode: centered emoji, positioned higher to leave room for text
            int emojiX = cell.x() + cell.width() / 2;
            int fontSize = Math.max(16, cell.height() / 3);
            // Position emoji at center
            int emojiY = cell.y() + (int) (cell.height() * 0.50);
            result.append(renderEmoji(holidayEmoji, emojiX, emojiY, fontSize, config, true));
            result.append(System.lineSeparator());
        } else if (smallEmoji && !holidayEmoji.isEmpty()) {
            // Small/small-text mode: bottom-left corner, positioned slightly higher to leave room for text
            int emojiX = cell.x() + 5;
            int emojiY = cell.y() + cell.height() - 8;
            int fontSize = Math.max(10, cell.height() / 6);
            result.append(renderEmoji(holidayEmoji, emojiX, emojiY, fontSize, config, false));
            result.append(System.lineSeparator());
        }

        // Holiday name text for large-text, small-text, and text modes only
        boolean showHolidayText = ("large-text".equals(config.eventDisplayMode)
                || "small-text".equals(config.eventDisplayMode) || "text".equals(config.eventDisplayMode))
                && !holidayName.isEmpty();
        if (showHolidayText) {
            int textX = cell.x() + cell.width() / 2;
            int textY = cell.y() + cell.height() - 3;
            int textSize = Math.max(5, cell.width() / 10);
            result.append(String.format(
                    "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" font-size=\"%d\""
                            + " fill=\"%s\" font-family=\"Helvetica, Arial," + " sans-serif\">%s</text>%n",
                    textX, textY, textSize, config.holidayColor, escapeXml(holidayName)));
        }

        return result.toString();
    }

    // =============================================
    // HELPER METHODS FOR renderDayCell (extracted to reduce cognitive complexity)
    // =============================================

    /** Encapsulates cell position and dimensions */
    private record Cell(int x, int y, int width, int height) {
    }

    /** Encapsulates text styling properties for SVG text rendering */
    private record TextRenderStyle(int size, String color, String fontWeight, String textAnchor, double rotation) {
    }

    /** Renders the cell background color if needed */
    private void renderCellBackground(StringBuilder svg, Cell cell, String cellBackground) {
        String pdfSafeColor = convertColorForPDF(cellBackground);
        if (pdfSafeColor != null && !pdfSafeColor.equals("none") && !pdfSafeColor.equals(Colors.WHITE)) {
            svg.append(String.format("<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"%s\"/>%n", cell.x(),
                    cell.y(), cell.width(), cell.height(), pdfSafeColor));
        }
    }

    /** Determines if moon should be shown based on display mode and date */
    private boolean shouldShowMoon(LocalDate date, CalendarConfigType config) {
        return "illumination".equals(config.moonDisplayMode)
                || ("phases".equals(config.moonDisplayMode) && isMoonPhaseDay(date))
                || ("full-only".equals(config.moonDisplayMode) && isFullMoonDay(date));
    }

    /** Renders moon illumination if needed */
    private void renderMoonIfNeeded(StringBuilder svg, Cell cell, LocalDate date, boolean shouldShowMoon,
            CalendarConfigType config) {
        if (!shouldShowMoon) {
            return;
        }
        int moonX = cell.x() + config.moonOffsetX;
        int moonY = cell.y() + config.moonOffsetY;
        svg.append(generateMoonIlluminationSVG(date, moonX, moonY, config.latitude, config.longitude, config));
    }

    /** Gets the substituted emoji for a custom date entry */
    private String getCustomEmoji(CustomDateEntryType entry, CalendarConfigType config) {
        if (entry == null || entry.emoji == null) {
            return "";
        }
        return substituteEmojiForMonochrome(entry.emoji, config);
    }

    /** Calculates emoji position based on position string */
    private int[] calculateEmojiPosition(String position, Cell cell) {
        int emojiX = cell.x();
        int emojiY = cell.y();

        switch (position) {
            case "top-left" :
                emojiX += 5;
                emojiY += 13;
                break;
            case "top-center" :
                emojiX += cell.width() / 2 - 5;
                emojiY += 13;
                break;
            case "top-right" :
                emojiX += cell.width() - 15;
                emojiY += 13;
                break;
            case "middle-left" :
                emojiX += 5;
                emojiY += cell.height() / 2 + 5;
                break;
            case "middle-center" :
                emojiX += cell.width() / 2 - 5;
                emojiY += cell.height() / 2 + 5;
                break;
            case "middle-right" :
                emojiX += cell.width() - 15;
                emojiY += cell.height() / 2 + 5;
                break;
            case "bottom-center" :
                emojiX += cell.width() / 2 - 5;
                emojiY += cell.height() - 5;
                break;
            case "bottom-right" :
                emojiX += cell.width() - 15;
                emojiY += cell.height() - 5;
                break;
            case "bottom-left" :
            default :
                // Fall through to bottom-left positioning for unknown values
                emojiX += 5;
                emojiY += cell.height() - 5;
                break;
        }
        return new int[]{emojiX, emojiY};
    }

    /** Renders custom emoji with positioning */
    private void renderCustomEmoji(StringBuilder svg, String customEmoji, CustomDateEntryType eventDisplay, Cell cell,
            CalendarConfigType config) {
        if (customEmoji.isEmpty()) {
            return;
        }

        if (eventDisplay != null) {
            double emojiX = cell.x() + (cell.width() * eventDisplay.getEmojiX(50) / 100.0);
            double emojiY = cell.y() + (cell.height() * eventDisplay.getEmojiY(50) / 100.0);
            double scaleFactor = cell.width() / 100.0;
            int scaledSize = (int) (eventDisplay.getEmojiSize(12) * scaleFactor);
            int emojiSize = Math.max(8, Math.min(24, scaledSize));
            svg.append(renderEmoji(customEmoji, emojiX, emojiY, emojiSize, config, true));
        } else {
            int[] pos = calculateEmojiPosition(config.emojiPosition, cell);
            svg.append(renderEmoji(customEmoji, pos[0], pos[1], 12, config, false));
        }
        svg.append(System.lineSeparator());
    }

    /** Renders event title with optional wrapping and rotation */
    private void renderEventTitle(StringBuilder svg, String title, CustomDateEntryType eventDisplay, Cell cell,
            CalendarConfigType config) {
        if (title == null || title.isEmpty()) {
            return;
        }

        if (eventDisplay != null) {
            renderEventTitleWithDisplay(svg, title, eventDisplay, cell, config);
        } else {
            String displayTitle = title.length() > 10 ? title.substring(0, 9) + "…" : title;
            svg.append(String.format("<text x=\"%d\" y=\"%d\" style=\"font-size: 7px; fill: %s;\">%s</text>%n",
                    cell.x() + 5, cell.y() + 38, config.customDateColor, displayTitle));
        }
    }

    /** Renders event title with custom display settings */
    private void renderEventTitleWithDisplay(StringBuilder svg, String title, CustomDateEntryType eventDisplay,
            Cell cell, CalendarConfigType config) {
        double textX = cell.x() + (cell.width() * eventDisplay.getTextX(50) / 100.0);
        double textY = cell.y() + (cell.height() * eventDisplay.getTextY(70) / 100.0);
        double scaleFactor = cell.width() / 100.0;
        int scaledSize = (int) (eventDisplay.getTextSize(7) * scaleFactor);
        int textSize = Math.max(5, Math.min(12, scaledSize));
        String textColor = eventDisplay.getTextColor(config.customDateColor);
        String fontWeight = eventDisplay.isTextBold() ? "bold" : "normal";
        String textAnchor = getTextAnchor(eventDisplay.getTextAlign("center"));
        double rotation = eventDisplay.getTextRotation();

        TextRenderStyle style = new TextRenderStyle(textSize, textColor, fontWeight, textAnchor, rotation);

        if (eventDisplay.isTextWrap() && title.length() > 8) {
            renderWrappedText(svg, title, textX, textY, style);
        } else {
            renderSingleLineText(svg, title, textX, textY, style, eventDisplay.isTextWrap());
        }
    }

    /** Renders a single line of text, truncating if necessary */
    private void renderSingleLineText(StringBuilder svg, String title, double textX, double textY,
            TextRenderStyle style, boolean allowFullTitle) {
        String displayTitle = (!allowFullTitle && title.length() > 10) ? title.substring(0, 9) + "…" : title;
        String transform = style.rotation() != 0
                ? String.format(" transform=\"rotate(%.1f %.1f %.1f)\"", style.rotation(), textX, textY)
                : "";
        svg.append(String.format(
                "<text x=\"%.1f\" y=\"%.1f\" style=\"font-size: %dpx; fill: %s;"
                        + " font-weight: %s; text-anchor: %s; font-family: Arial, sans-serif;\"%s>%s</text>%n",
                textX, textY, style.size(), style.color(), style.fontWeight(), style.textAnchor(), transform,
                displayTitle));
    }

    /** Converts text alignment to SVG text-anchor */
    private String getTextAnchor(String textAlign) {
        if ("left".equals(textAlign)) {
            return "start";
        } else if ("right".equals(textAlign)) {
            return "end";
        }
        return "middle";
    }

    /** Renders wrapped text on two lines */
    private void renderWrappedText(StringBuilder svg, String title, double textX, double textY, TextRenderStyle style) {
        String[] words = title.split(" ");
        StringBuilder line1 = new StringBuilder();
        StringBuilder line2 = new StringBuilder();
        int charCount = 0;

        for (String word : words) {
            if (charCount + word.length() <= 8) {
                if (line1.length() > 0) {
                    line1.append(" ");
                }
                line1.append(word);
                charCount += word.length() + 1;
            } else if (line2.length() == 0) {
                line2.append(word);
            } else {
                line2.append("…");
                break;
            }
        }

        String transform = style.rotation() != 0
                ? String.format(" transform=\"rotate(%.1f %.1f %.1f)\"", style.rotation(), textX, textY)
                : "";
        double lineHeight = style.size() * 1.2;

        svg.append(String.format(
                "<text x=\"%.1f\" y=\"%.1f\" style=\"font-size: %dpx; fill: %s;"
                        + " font-weight: %s; text-anchor: %s; font-family: Arial, sans-serif;\"%s>%s</text>%n",
                textX, textY - lineHeight / 2, style.size(), style.color(), style.fontWeight(), style.textAnchor(),
                transform, line1.toString()));

        if (line2.length() > 0) {
            svg.append(String.format(
                    "<text x=\"%.1f\" y=\"%.1f\" style=\"font-size: %dpx; fill: %s;"
                            + " font-weight: %s; text-anchor: %s; font-family: Arial, sans-serif;\"%s>%s</text>%n",
                    textX, textY + lineHeight / 2, style.size(), style.color(), style.fontWeight(), style.textAnchor(),
                    transform, line2.toString()));
        }
    }

    /** Renders day number in cell */
    private void renderDayNumber(StringBuilder svg, Cell cell, int day, boolean isHoliday, boolean isCustomDate,
            CalendarConfigType config, ThemeColors theme) {
        if (!config.showDayNumbers) {
            return;
        }
        String dayFill = config.dayTextColor != null ? config.dayTextColor : theme.text;
        String fontWeight = "normal";
        if (isHoliday && config.holidayColor != null) {
            dayFill = config.holidayColor;
            fontWeight = "bold";
        } else if (isCustomDate && config.customDateColor != null) {
            dayFill = config.customDateColor;
        }
        svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" fill=\"%s\" font-weight=\"%s\""
                        + " font-family=\"Helvetica, Arial, sans-serif\" font-size=\"12px\">%d</text>%n",
                cell.x() + 5, cell.y() + 14, dayFill, fontWeight, day));
    }

    /** Renders day name abbreviation in cell */
    private void renderDayName(StringBuilder svg, Cell cell, DayOfWeek dayOfWeek, Locale locale,
            CalendarConfigType config, ThemeColors theme) {
        if (!config.showDayNames) {
            return;
        }
        String dayName = dayOfWeek.getDisplayName(TextStyle.SHORT, locale).substring(0, 2);
        String dayNameFill = config.dayNameColor != null ? config.dayNameColor : theme.weekdayHeader;

        // Check if large moon is enabled - if so, move day name to top-right to avoid overlap
        boolean largeMoonEnabled = !"none".equals(config.moonDisplayMode) && config.moonSize >= 15;
        int textX = largeMoonEnabled ? cell.x() + cell.width() - 5 : cell.x() + 5;
        int textY = largeMoonEnabled ? cell.y() + 14 : cell.y() + 26; // Top-right same as date, or below date
        String textAnchor = largeMoonEnabled ? "end" : "start";

        svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" fill=\"%s\" font-family=\"Arial, sans-serif\""
                        + " font-size=\"8px\" text-anchor=\"%s\">%s</text>%n",
                textX, textY, dayNameFill, textAnchor, dayName));
    }

    /** Renders grid lines around cell */
    private void renderGridLines(StringBuilder svg, Cell cell, CalendarConfigType config) {
        if (!config.showGrid) {
            return;
        }
        svg.append(String.format(
                "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"none\""
                        + " stroke=\"%s\" stroke-width=\"1\"/>%n",
                cell.x(), cell.y(), cell.width(), cell.height(), config.gridLineColor));
    }

    // =============================================
    // END HELPER METHODS
    // =============================================

    /**
     * Renders all content inside a day cell. Shared between grid and weekday-grid layouts. Both layouts calculate cell
     * position based on their own structure, then call this method to render identical day content.
     *
     * @param svg
     *            StringBuilder to append SVG content to
     * @param cell
     *            Cell position and dimensions
     * @param date
     *            The date being rendered
     * @param dayOfWeek
     *            Day of week for this date
     * @param isWeekend
     *            Whether this is a weekend day
     * @param monthNum
     *            Month number (1-12)
     * @param weekendIndex
     *            Weekend index for Vermont colors
     * @param locale
     *            Locale for day name formatting
     * @param config
     *            Calendar configuration
     * @param theme
     *            Theme colors
     */
    private void renderDayCell(StringBuilder svg, Cell cell, LocalDate date, DayOfWeek dayOfWeek, boolean isWeekend,
            int monthNum, int weekendIndex, Locale locale, CalendarConfigType config, ThemeColors theme) {

        int day = date.getDayOfMonth();

        // Cell background
        String cellBackground = getCellBackgroundColor(config, date, monthNum, day, isWeekend, weekendIndex);
        renderCellBackground(svg, cell, cellBackground);

        // Moon display
        boolean showMoon = shouldShowMoon(date, config);
        renderMoonIfNeeded(svg, cell, date, showMoon, config);

        // Check for holidays or custom dates
        HolidayType holiday = config.holidays.get(date);
        String holidayEmoji = holiday != null && holiday.emoji != null
                ? substituteEmojiForMonochrome(holiday.emoji, config)
                : "";
        String holidayName = holiday != null && holiday.name != null ? holiday.name : "";
        boolean isHoliday = holiday != null;

        // Get custom date entry and substituted emoji
        CustomDateEntryType customEntry = config.customDates.get(date);
        boolean isCustomDate = customEntry != null;
        String customEmoji = getCustomEmoji(customEntry, config);

        // Holiday emoji/text
        svg.append(renderHolidayContent(holidayEmoji, holidayName, cell, config));

        // Custom emoji - skip if holiday emoji already rendered
        if (holidayEmoji.isEmpty()) {
            renderCustomEmoji(svg, customEmoji, customEntry, cell, config);
        }

        // Event title from custom entry
        String title = customEntry != null ? customEntry.title : null;
        renderEventTitle(svg, title, customEntry, cell, config);

        // Day number
        renderDayNumber(svg, cell, day, isHoliday, isCustomDate, config, theme);

        // Day name
        renderDayName(svg, cell, dayOfWeek, locale, config, theme);

        // Grid lines
        renderGridLines(svg, cell, config);
    }

    // Color themes
    private static final Map<String, ThemeColors> THEMES = new HashMap<>();

    // Vermont monthly colors for weekends
    private static final String[][] VERMONT_MONTHLY_COLORS = {
            // January - Snowy and frosty hues
            {Colors.WINTER_FROST},
            // February - Late winter
            {"#F0F8FF"},
            // March - Early spring
            {Colors.SPRING_MINT, Colors.SPRING_MINT, Colors.SPRING_MINT, Colors.SPRING_MINT, Colors.SPRING_MINT,
                    Colors.SPRING_MINT, Colors.SPRING_MINT, Colors.SPRING_MINT, Colors.SPRING_LAVENDER,
                    Colors.SPRING_LAVENDER},
            // April - Spring bloom
            {"#7CFC00", Colors.SPRING_LIME, Colors.SPRING_LIME, Colors.SPRING_LIME, Colors.SPRING_LIME,
                    Colors.SPRING_LIME, Colors.SPRING_LIME, Colors.SPRING_LIME, Colors.SPRING_LIME,
                    Colors.SPRING_LAVENDER},
            // May - Late spring, lush
            {Colors.EMERALD, "#D0ECE7", "#A2D9CE", "#73C6B6", "#45B39D", "#58D68D", Colors.EMERALD, "#ABEBC6",
                    "#D5F5E3", "#FEF9E7"},
            // June - Early summer
            {Colors.SUMMER_AQUAMARINE},
            // July - Mid summer
            {Colors.SUMMER_GREEN},
            // August - Late summer
            {Colors.SUMMER_CYAN},
            // September - Early fall
            {"#FAD7A0", "#F8C471", "#F5B041", Colors.ORANGE, Colors.ORANGE, Colors.ORANGE, Colors.ORANGE, Colors.ORANGE,
                    Colors.GOLD, Colors.GOLD},
            // October - Peak fall foliage
            {"#FF4500", "#FF8C00", "#DAA520", "rgba(180,120,60,0.4)", "rgba(190,130,70,0.4)", "rgba(200,140,80,0.4)",
                    "rgba(210,160,100,0.4)", "rgba(225,190,140,0.4)", "#C0C0C0", "#808080"},
            // November - Late fall
            {Colors.OVERLAY_GRAY_LIGHT},
            // December - Winter
            {Colors.OVERLAY_GRAY_MEDIUM}};

    // Lakeshore monthly colors - cool blue gradient through the year (single color per month)
    private static final String[] LAKESHORE_MONTHLY_COLORS = {"#e3f2fd", // January - Icy blue
            "#e1f5fe", // February - Pale azure
            "#b3e5fc", // March - Soft sky
            "#e0f7fa", // April - Spring mist
            "#b2ebf2", // May - Aqua tint
            "#b2dfdb", // June - Light cyan
            "#80deea", // July - Tropical mist
            "#84ffff", // August - Seafoam
            "#a7ffeb", // September - Pale teal
            "#b2dfdb", // October - Cool aqua
            "#b3e5fc", // November - Soft cyan
            "#bbdefb" // December - Winter blue
    };

    // Sunset Glow monthly colors - warm pastel peach/pink/coral tones (single color per month)
    private static final String[] SUNSET_MONTHLY_COLORS = {"#fce4ec", // January - Blush pink
            "#f8bbd0", // February - Rose tint
            "#ffcdd2", // March - Soft coral
            "#ffccbc", // April - Pale peach
            "#ffe0b2", // May - Light apricot
            "#fff8e1", // June - Cream gold
            "#ffecb3", // July - Warm butter
            "#ffe082", // August - Soft amber
            "#ffd180", // September - Mellow peach
            "#ffab91", // October - Light terracotta
            "#ffcdd2", // November - Dusty rose
            "#f8bbd0" // December - Winter blush
    };

    // Forest Floor monthly colors - light sage and mint tones (single color per month)
    private static final String[] FOREST_MONTHLY_COLORS = {"#e8f5e9", // January - Frost sage
            "#c8e6c9", // February - Pale mint
            "#dcedc8", // March - Spring bud
            "#c5e1a5", // April - Fresh leaf
            "#aed581", // May - Soft fern
            "#c8e6c9", // June - Light moss
            "#a5d6a7", // July - Meadow mist
            "#b9f6ca", // August - Summer sage
            "#dcedc8", // September - Pale olive
            "#d7ccc8", // October - Light cedar
            "#efebe9", // November - Soft birch
            "#e8f5e9" // December - Winter pine
    };

    // Map theme names to their color arrays for DRY weekend color lookup
    private static final Map<String, Object> WEEKEND_THEME_COLORS = new HashMap<>();

    static {
        WEEKEND_THEME_COLORS.put("vermontWeekends", VERMONT_MONTHLY_COLORS);
        WEEKEND_THEME_COLORS.put("lakeshoreWeekends", LAKESHORE_MONTHLY_COLORS);
        WEEKEND_THEME_COLORS.put("sunsetWeekends", SUNSET_MONTHLY_COLORS);
        WEEKEND_THEME_COLORS.put("forestWeekends", FOREST_MONTHLY_COLORS);
    }

    static {
        // Default theme
        THEMES.put(DEFAULT_THEME, new ThemeColors(Colors.BLACK, // text
                Colors.WHITE, // background
                "#f0f0f0", // weekend background
                Colors.GRAY_900, // month header
                Colors.GRAY_600 // weekday header
        ));

        // Vermont Weekends theme - colors will be applied per month
        THEMES.put("vermontWeekends", new ThemeColors(Colors.BLACK, Colors.WHITE, null, // weekend color will be dynamic
                "#1b5e20", Colors.GRAY_900));

        // Rainbow themes
        THEMES.put("rainbowWeekends", new ThemeColors(Colors.BLACK, Colors.WHITE, null, // will be calculated
                                                                                        // dynamically
                "#e91e63", "#9c27b0"));

        THEMES.put("rainbowDays", new ThemeColors(Colors.BLACK, null, // will be calculated dynamically
                null, Colors.GRAY_900, Colors.GRAY_600));

        // Lakeshore theme - cool blue tones
        THEMES.put("lakeshoreWeekends", new ThemeColors(Colors.BLACK, Colors.WHITE, null, // weekend color will be
                                                                                          // dynamic per month
                "#1565c0", Colors.GRAY_900));

        // Sunset Glow theme - warm orange/pink tones
        THEMES.put("sunsetWeekends", new ThemeColors(Colors.BLACK, Colors.WHITE, null, // weekend color will be dynamic
                                                                                       // per month
                "#e65100", Colors.GRAY_900));

        // Forest Floor theme - earth/green tones
        THEMES.put("forestWeekends", new ThemeColors(Colors.BLACK, Colors.WHITE, null, // weekend color will be dynamic
                                                                                       // per month
                "#2e7d32", Colors.GRAY_900));
    }

    private static class ThemeColors {
        final String text;
        final String background;
        final String weekendBackground;
        final String monthHeader;
        final String weekdayHeader;

        ThemeColors(String text, String background, String weekendBackground, String monthHeader,
                String weekdayHeader) {
            this.text = text;
            this.background = background;
            this.weekendBackground = weekendBackground;
            this.monthHeader = monthHeader;
            this.weekdayHeader = weekdayHeader;
        }
    }

    public String generateCalendarSVG(CalendarConfigType config) {
        // Populate holidays from holidaySets if provided
        if (config.holidaySets != null && !config.holidaySets.isEmpty()) {
            for (String setId : config.holidaySets) {
                // Get holidays from HolidayService (handles ID mapping internally)
                Map<LocalDate, HolidayType> setHolidays = holidayService.getHolidays(config.year, setId);
                config.holidays.putAll(setHolidays);
            }
        }

        // Choose layout style
        if ("weekday-grid".equals(config.layoutStyle)) {
            return generateWeekdayGridCalendarSVG(config);
        } else {
            // Default to grid layout
            return generateGridCalendarSVG(config);
        }
    }

    // Grid layout modes
    private static final boolean LAYOUT_FIXED_GRID = false; // 12 rows x 31 columns
    private static final boolean LAYOUT_WEEKDAY_GRID = true; // 12 rows x 37 columns, weekday-aligned

    // Fixed grid layout (12 rows x 31 columns)
    private String generateGridCalendarSVG(CalendarConfigType config) {
        return generateCalendarGridSVG(config, LAYOUT_FIXED_GRID);
    }

    // Weekday aligned grid layout (12 rows x 37 columns)
    private String generateWeekdayGridCalendarSVG(CalendarConfigType config) {
        return generateCalendarGridSVG(config, LAYOUT_WEEKDAY_GRID);
    }

    /**
     * Unified calendar grid generator for both fixed and weekday-aligned layouts.
     *
     * @param config
     *            Calendar configuration
     * @param weekdayAligned
     *            If true, use weekday-aligned layout; if false, use fixed 31-column grid
     */
    private String generateCalendarGridSVG(CalendarConfigType config, boolean weekdayAligned) {
        ThemeColors theme = THEMES.getOrDefault(config.theme, THEMES.get(DEFAULT_THEME));

        int year = config.year;
        int cellWidth = config.compactMode ? 40 : 50;
        int cellHeight = config.compactMode ? 60 : 75;
        int headerHeight = 100;

        int[] dimensions = calculateGridDimensions(weekdayAligned, cellWidth, cellHeight, headerHeight);
        int svgWidth = dimensions[0];
        int svgHeight = dimensions[1];

        StringBuilder svg = new StringBuilder();
        appendSvgHeader(svg, svgWidth, svgHeight);
        appendGridStyles(svg, config, theme);
        appendYearTitle(svg, config, theme, year);

        // Generate each month row
        Locale locale = Locale.forLanguageTag(config.locale);
        for (int monthNum = 1; monthNum <= 12; monthNum++) {
            generateMonthRow(svg, year, monthNum, cellWidth, cellHeight, headerHeight, weekdayAligned, locale, config,
                    theme);
        }

        appendOuterBorder(svg, weekdayAligned, config, cellWidth, cellHeight, headerHeight);
        svg.append("</svg>");
        return svg.toString();
    }

    private int[] calculateGridDimensions(boolean weekdayAligned, int cellWidth, int cellHeight, int headerHeight) {
        if (weekdayAligned) {
            int totalCols = 37; // 7 days * 5 weeks + 2 extra for months that span 6 weeks
            return new int[]{cellWidth * (totalCols + 1) + 20, cellHeight * 12 + headerHeight + 20};
        }
        return new int[]{32 * cellWidth, 12 * cellHeight + headerHeight};
    }

    private void appendYearTitle(StringBuilder svg, CalendarConfigType config, ThemeColors theme, int year) {
        String yearFill = config.yearColor != null ? config.yearColor : theme.monthHeader;
        svg.append(String.format("<text x=\"50\" y=\"80\" fill=\"%s\" font-family=\"Helvetica, Arial,"
                + " sans-serif\" font-size=\"80px\" font-weight=\"bold\">%d</text>%n", yearFill, year));
    }

    private void generateMonthRow(StringBuilder svg, int year, int monthNum, int cellWidth, int cellHeight,
            int headerHeight, boolean weekdayAligned, Locale locale, CalendarConfigType config, ThemeColors theme) {
        YearMonth yearMonth = YearMonth.of(year, monthNum);
        int daysInMonth = yearMonth.lengthOfMonth();
        int rowY = (monthNum - 1) * cellHeight + headerHeight;

        appendMonthLabel(svg, monthNum, cellWidth, cellHeight, rowY, weekdayAligned, locale, config, theme);

        int startCol = weekdayAligned ? yearMonth.atDay(1).getDayOfWeek().getValue() % 7 : 0;
        int maxDay = weekdayAligned ? daysInMonth : 31;
        int weekendIndex = 0;

        for (int day = 1; day <= maxDay; day++) {
            int cellX = calculateCellX(weekdayAligned, day, startCol, cellWidth);
            int cellY = rowY;

            Cell cell = new Cell(cellX, cellY, cellWidth, cellHeight);

            if (!weekdayAligned && day > daysInMonth) {
                renderEmptyCell(svg, cell, config);
                continue;
            }

            LocalDate date = yearMonth.atDay(day);
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            boolean isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
            if (isWeekend) {
                weekendIndex++;
            }
            renderDayCell(svg, cell, date, dayOfWeek, isWeekend, monthNum, weekendIndex - 1, locale, config, theme);
        }
    }

    private void appendMonthLabel(StringBuilder svg, int monthNum, int cellWidth, int cellHeight, int rowY,
            boolean weekdayAligned, Locale locale, CalendarConfigType config, ThemeColors theme) {
        String monthName = Month.of(monthNum).getDisplayName(TextStyle.SHORT, locale);
        String monthFill = config.monthColor != null ? config.monthColor : theme.monthHeader;
        if (weekdayAligned) {
            svg.append(renderMonthName(monthName, cellWidth - 5, rowY + cellHeight / 2 + 4, cellWidth,
                    config.rotateMonthNames, "end", monthFill));
        } else {
            svg.append(renderMonthName(monthName, 5, rowY + cellHeight / 2 + 5, cellWidth, config.rotateMonthNames,
                    "start", monthFill));
        }
    }

    private int calculateCellX(boolean weekdayAligned, int day, int startCol, int cellWidth) {
        if (weekdayAligned) {
            int col = startCol + day - 1;
            return cellWidth + col * cellWidth;
        }
        return day * cellWidth;
    }

    private void appendOuterBorder(StringBuilder svg, boolean weekdayAligned, CalendarConfigType config, int cellWidth,
            int cellHeight, int headerHeight) {
        if (!weekdayAligned && config.showGrid) {
            svg.append(String.format(
                    "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"none\""
                            + " stroke=\"%s\" stroke-width=\"2\"/>%n",
                    cellWidth, headerHeight - 1, cellWidth * 31, cellHeight * 12 + 2, config.gridLineColor));
        }
    }

    private void appendSvgHeader(StringBuilder svg, int width, int height) {
        svg.append(String.format(
                "<svg xmlns=\"http://www.w3.org/2000/svg\""
                        + " xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"%d\""
                        + " height=\"%d\" viewBox=\"0 0 %d %d\" preserveAspectRatio=\"xMidYMid" + " meet\">%n",
                width, height, width, height));
    }

    private void appendGridStyles(StringBuilder svg, CalendarConfigType config, ThemeColors theme) {
        svg.append("<style>").append(System.lineSeparator());
        svg.append(String.format(
                ".year-text { fill: %s; font-family: Helvetica, Arial, sans-serif;"
                        + " font-size: 80px; font-weight: bold; }%n",
                config.yearColor != null ? config.yearColor : theme.monthHeader));
        svg.append(String.format(
                ".month-name { fill: %s; font-family: Helvetica, Arial, sans-serif;"
                        + " font-size: 20px; font-weight: bold; }%n",
                config.monthColor != null ? config.monthColor : theme.monthHeader));
        svg.append(String.format(
                ".day-text { fill: %s; font-family: Helvetica, Arial, sans-serif;" + " font-size: 12px; }%n",
                config.dayTextColor != null ? config.dayTextColor : theme.text));
        svg.append(String.format(".day-name { fill: %s; font-family: Arial, sans-serif; font-size: 8px; }%n",
                config.dayNameColor != null ? config.dayNameColor : theme.weekdayHeader));
        svg.append(String.format(".grid-line { stroke: %s; stroke-width: 1; fill: none; }%n", config.gridLineColor));

        String weekendBg = config.weekendBgColor != null && !config.weekendBgColor.isEmpty() ? config.weekendBgColor
                : (theme.weekendBackground != null ? theme.weekendBackground : "none");
        svg.append(String.format(".weekend-bg { fill: %s; }%n", weekendBg));
        svg.append(String.format(".holiday { fill: %s; font-weight: bold; }%n", config.holidayColor));
        svg.append(String.format(".custom-date { fill: %s; }%n", config.customDateColor));
        svg.append("</style>").append(System.lineSeparator());
    }

    private void renderEmptyCell(StringBuilder svg, Cell cell, CalendarConfigType config) {
        if (config.showGrid) {
            String pdfSafeColor = convertColorForPDF("rgba(255, 255, 255, 0)");
            svg.append(String.format(
                    "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"%s\""
                            + " stroke=\"%s\" stroke-width=\"1\"/>%n",
                    cell.x(), cell.y(), cell.width(), cell.height(), pdfSafeColor, config.gridLineColor));
        }
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
        if (phase < 0)
            phase += 1.0;

        return phase;
    }

    private String getMoonPhaseSymbol(LocalDate date) {
        MoonPhase phase = calculateMoonPhase(date);
        return getMoonPhaseSymbol(phase);
    }

    private String getMoonPhaseSymbol(MoonPhase phase) {
        switch (phase) {
            case NEW_MOON :
                return "🌑";
            case WAXING_CRESCENT :
                return "🌒";
            case FIRST_QUARTER :
                return "🌓";
            case WAXING_GIBBOUS :
                return "🌔";
            case FULL_MOON :
                return "🌕";
            case WANING_GIBBOUS :
                return "🌖";
            case LAST_QUARTER :
                return "🌗";
            case WANING_CRESCENT :
                return "🌘";
            default :
                return "";
        }
    }

    // Moon phase calculation using synodic month
    private enum MoonPhase {
        NEW_MOON, WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS, FULL_MOON, WANING_GIBBOUS, LAST_QUARTER, WANING_CRESCENT
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
            info.setProducer("villagecompute.com"); // Override Apache FOP producer string
            info.setCreator("Village Compute Calendar Generator"); // Override creator
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
            Log.warn("Could not clean PDF metadata", e);
            return pdfBytes;
        }
    }

    // Generate PDF using Apache Batik
    public byte[] generateCalendarPDF(CalendarConfigType config) {
        try {
            // Generate SVG content
            String svgContent = generateCalendarSVG(config);

            Log.debugf("SVG content length: %d", svgContent.length());

            // Wrap SVG with margins for proper print layout
            String wrappedSvg = wrapSvgWithMargins(svgContent);

            // Create transcoder for PDF
            Transcoder transcoder = new PDFTranscoder();

            // PDF page size using constants (in points: 1 inch = 72 points)
            float pageWidthInPoints = PAGE_WIDTH_INCHES * POINTS_PER_INCH;
            float pageHeightInPoints = PAGE_HEIGHT_INCHES * POINTS_PER_INCH;

            // Set the page/output size
            transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_WIDTH, pageWidthInPoints);
            transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_HEIGHT, pageHeightInPoints);

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

            Log.debugf("PDF generated successfully, size: %d bytes", pdfBytes.length);

            return pdfBytes;

        } catch (Exception e) {
            Log.error("Error generating PDF", e);
            // Return empty array on error
            return new byte[0];
        }
    }

    /**
     * Wraps the generated SVG with an outer SVG that adds margins for print layout. The inner SVG is scaled to fit
     * within the printable area (page size minus margins) and centered on the page.
     */
    private String wrapSvgWithMargins(String innerSvg) {
        // Extract the viewBox from the inner SVG to get its dimensions
        java.util.regex.Pattern viewBoxPattern = java.util.regex.Pattern
                .compile("viewBox=\"([\\d.]+)\\s+([\\d.]+)\\s+([\\d.]+)\\s+([\\d.]+)\"");
        java.util.regex.Matcher matcher = viewBoxPattern.matcher(innerSvg);

        if (!matcher.find()) {
            // If no viewBox, return original SVG
            Log.warn("No viewBox found in SVG, returning without margins");
            return innerSvg;
        }

        float innerWidth = Float.parseFloat(matcher.group(3));
        float innerHeight = Float.parseFloat(matcher.group(4));

        // Calculate the printable area in the same units as the page
        // We'll use a coordinate system where the page is PAGE_WIDTH_INCHES x PAGE_HEIGHT_INCHES
        float pageWidth = PAGE_WIDTH_INCHES * 100; // Scale up for better precision
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

        // Calculate offset - center horizontally, align to top vertically
        float offsetX = marginSize + (printableWidth - scaledWidth) / 2;
        float offsetY = marginSize; // Top-aligned, extra space goes to bottom

        // Remove the <?xml...?> declaration if present from inner SVG
        String cleanedInnerSvg = innerSvg.replaceFirst("<\\?xml[^?]*\\?>\\s*", "");

        // Build the wrapper SVG
        StringBuilder wrapper = new StringBuilder();
        wrapper.append(String.format("<?xml version=\"1.0\" encoding=\"UTF-8\"?>%n"));
        wrapper.append(String.format(
                "<svg xmlns=\"http://www.w3.org/2000/svg\""
                        + " xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"%.0f\""
                        + " height=\"%.0f\" viewBox=\"0 0 %.0f %.0f\">%n",
                pageWidth, pageHeight, pageWidth, pageHeight));

        // Add a white background for the full page
        wrapper.append(
                String.format("  <rect width=\"%.0f\" height=\"%.0f\" fill=\"white\"/>%n", pageWidth, pageHeight));

        // Position and scale the inner SVG content within the margins
        wrapper.append(
                String.format("  <g transform=\"translate(%.2f, %.2f) scale(%.6f)\">%n", offsetX, offsetY, scale));

        // Insert the inner SVG content (strip the outer <svg> tags)
        String innerContent = cleanedInnerSvg.replaceFirst("<svg[^>]*>", "") // Remove opening <svg> tag
                .replaceFirst("</svg>\\s*$", ""); // Remove closing </svg> tag

        wrapper.append(innerContent);
        wrapper.append(String.format("%n  </g>%n"));
        wrapper.append("</svg>");

        return wrapper.toString();
    }

    /**
     * Wraps the generated SVG with a white background and margins for preview/thumbnail rendering. Uses the same page
     * aspect ratio (35:23) as the print layout to match the artboard. This is a public method for use by
     * StaticContentResource for PNG generation.
     */
    public String wrapSvgForPreview(String innerSvg) {
        // Extract the viewBox from the inner SVG to get its dimensions
        java.util.regex.Pattern viewBoxPattern = java.util.regex.Pattern
                .compile("viewBox=\"([\\d.]+)\\s+([\\d.]+)\\s+([\\d.]+)\\s+([\\d.]+)\"");
        java.util.regex.Matcher matcher = viewBoxPattern.matcher(innerSvg);

        if (!matcher.find()) {
            // If no viewBox, return original SVG with just a white background added
            return innerSvg.replaceFirst("<svg([^>]*)>",
                    "<svg$1><rect width=\"100%\" height=\"100%\" fill=\"white\"/>");
        }

        float innerWidth = Float.parseFloat(matcher.group(3));
        float innerHeight = Float.parseFloat(matcher.group(4));

        // Use the same page aspect ratio as print (35:23)
        // We'll use a coordinate system where the page is PAGE_WIDTH_INCHES x PAGE_HEIGHT_INCHES
        // scaled by 100
        float pageWidth = PAGE_WIDTH_INCHES * 100; // 3500
        float pageHeight = PAGE_HEIGHT_INCHES * 100; // 2300
        float marginSize = MARGIN_INCHES * 100; // 50
        float printableWidth = PRINTABLE_WIDTH_INCHES * 100; // 3400
        float printableHeight = PRINTABLE_HEIGHT_INCHES * 100; // 2200

        // Calculate scale to fit inner SVG within printable area while maintaining aspect ratio
        float scaleX = printableWidth / innerWidth;
        float scaleY = printableHeight / innerHeight;
        float scale = Math.min(scaleX, scaleY);

        // Calculate the actual size of the scaled content
        float scaledWidth = innerWidth * scale;

        // Calculate offset - center horizontally, align to top vertically (with margin)
        float offsetX = marginSize + (printableWidth - scaledWidth) / 2;
        float offsetY = marginSize;

        // Remove the <?xml...?> declaration if present from inner SVG
        String cleanedInnerSvg = innerSvg.replaceFirst("<\\?xml[^?]*\\?>\\s*", "");

        // Build the wrapper SVG
        StringBuilder wrapper = new StringBuilder();
        wrapper.append(String.format("<?xml version=\"1.0\" encoding=\"UTF-8\"?>%n"));
        wrapper.append(String.format(
                "<svg xmlns=\"http://www.w3.org/2000/svg\""
                        + " xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"%.0f\""
                        + " height=\"%.0f\" viewBox=\"0 0 %.0f %.0f\">%n",
                pageWidth, pageHeight, pageWidth, pageHeight));

        // Add a white background for the full page
        wrapper.append(
                String.format("  <rect width=\"%.0f\" height=\"%.0f\" fill=\"white\"/>%n", pageWidth, pageHeight));

        // Position and scale the inner SVG content within the margins
        wrapper.append(
                String.format("  <g transform=\"translate(%.2f, %.2f) scale(%.6f)\">%n", offsetX, offsetY, scale));

        // Insert the inner SVG content (strip the outer <svg> tags)
        String innerContent = cleanedInnerSvg.replaceFirst("<svg[^>]*>", "") // Remove opening <svg> tag
                .replaceFirst("</svg>\\s*$", ""); // Remove closing </svg> tag

        wrapper.append(innerContent);
        wrapper.append(String.format("%n  </g>%n"));
        wrapper.append("</svg>");

        return wrapper.toString();
    }

    // Generate SVG for moon illumination visualization
    public String generateMoonIlluminationSVG(LocalDate date, int x, int y, double latitude, double longitude,
            CalendarConfigType config) {
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
        svg.append(String.format("<circle r=\"%d\" fill=\"%s\"/>%n", radius, config.moonDarkColor));

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
                path = String.format("M 0,-%d A %d,%d 0 %s,%s 0,%d A %.1f,%d 0 %s,%s 0,-%d", radius, radius, radius,
                        largeArcFlag, isRightSideLit ? "1" : "0", radius, ellipseWidth, radius, largeArcFlag,
                        isRightSideLit ? "0" : "1", radius);
            } else {
                // Gibbous moon
                path = String.format("M 0,-%d A %d,%d 0 %s,%s 0,%d A %.1f,%d 0 %s,%s 0,-%d", radius, radius, radius,
                        "1", isRightSideLit ? "1" : "0", radius, ellipseWidth, radius, "0", isRightSideLit ? "1" : "0",
                        radius);
            }

            svg.append(String.format("<path d=\"%s\" fill=\"%s\"/>%n", path, config.moonLightColor));
        } else if (illuminatedFraction >= 1) {
            // Full moon
            svg.append(String.format("<circle r=\"%d\" fill=\"%s\"/>%n", radius, config.moonLightColor));
        }
        // New moon is just the dark circle

        // Border with configurable color and width
        svg.append(String.format("<circle r=\"%d\" fill=\"none\" stroke=\"%s\" stroke-width=\"%.1f\"/>%n", radius,
                config.moonBorderColor, config.moonBorderWidth));

        svg.append("</g>").append(System.lineSeparator());

        return svg.toString();
    }

    // Moon illumination data class
    private static class MoonIllumination {
        double fraction; // 0 to 1, illuminated fraction
        double phase; // 0 to 1, phase in the lunar cycle
        double angle; // angle in radians

        MoonIllumination(double fraction, double phase, double angle) {
            this.fraction = fraction;
            this.phase = phase;
            this.angle = angle;
        }
    }

    // Moon position data class
    private static class MoonPosition {
        double azimuth; // azimuth in radians
        double altitude; // altitude in radians
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
        if (moonAge < 0)
            moonAge += synodicMonth;

        // Calculate approximate moon declination
        // Moon's orbit is inclined about 5.14° to ecliptic, ecliptic is inclined 23.44° to equator
        // This gives moon declination range of approximately ±28.6°
        double moonOrbitPhase = (moonAge / synodicMonth) * 2 * Math.PI;
        double moonDeclination = Math
                .toRadians(28.6 * Math.sin(moonOrbitPhase + date.getDayOfYear() * Math.PI / 182.625));

        // Calculate hour angle based on time and longitude
        // This is simplified - actual calculation would need precise time
        double hourAngle = (date.getDayOfMonth() / 30.0) * 2 * Math.PI + lng;

        // Calculate altitude
        double altitude = Math.asin(Math.sin(lat) * Math.sin(moonDeclination)
                + Math.cos(lat) * Math.cos(moonDeclination) * Math.cos(hourAngle));

        // Calculate azimuth
        double azimuth = Math.atan2(-Math.sin(hourAngle),
                Math.tan(moonDeclination) * Math.cos(lat) - Math.sin(lat) * Math.cos(hourAngle));

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
            double cosPA = (Math.sin(moonDeclination) * Math.cos(lat)
                    - Math.cos(moonDeclination) * Math.sin(lat) * Math.cos(hourAngle)) / Math.cos(altitude);
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

                    float r;
                    float g;
                    float b;
                    int hueSegment = (int) (hue * 6);
                    switch (hueSegment) {
                        case 0 :
                            r = c;
                            g = x;
                            b = 0;
                            break;
                        case 1 :
                            r = x;
                            g = c;
                            b = 0;
                            break;
                        case 2 :
                            r = 0;
                            g = c;
                            b = x;
                            break;
                        case 3 :
                            r = 0;
                            g = x;
                            b = c;
                            break;
                        case 4 :
                            r = x;
                            g = 0;
                            b = c;
                            break;
                        case 5 :
                        default :
                            r = c;
                            g = 0;
                            b = x;
                            break;
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

    public static String getCellBackgroundColor(CalendarConfigType config, LocalDate date, int monthNum, int dayNum,
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
                double distance = Math.sqrt(Math.pow(12 - monthNum * monthWeight, 2) + Math.pow(31d - dayNum, 2));
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
            }

            // Check for themed weekend colors (vermont, lakeshore, sunset, forest)
            String weekendColor = getWeekendThemeColor(theme, monthNum, weekendIndex);
            if (weekendColor != null) {
                return weekendColor;
            }

            if (config.highlightWeekends) {
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
     * Gets the weekend color for themed weekends (vermont, lakeshore, sunset, forest). Returns null if the theme is not
     * a weekend theme.
     */
    private static String getWeekendThemeColor(String theme, int monthNum, int weekendIndex) {
        Object colorData = WEEKEND_THEME_COLORS.get(theme);
        if (colorData == null) {
            return null;
        }

        // Vermont uses 2D array (varying colors within month), others use 1D array (single color
        // per month)
        if (colorData instanceof String[][] monthlyColors) {
            String[] monthColors = monthlyColors[monthNum - 1];
            if (weekendIndex >= 0 && weekendIndex < monthColors.length) {
                return monthColors[weekendIndex];
            }
            return monthColors[0];
        } else if (colorData instanceof String[] monthColors) {
            return monthColors[monthNum - 1];
        }

        return null;
    }

    /** Escapes special XML characters in a string. */
    private String escapeXml(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'",
                "&apos;");
    }
}
