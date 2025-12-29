package villagecompute.calendar.services;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

/**
 * Service for converting emoji characters to inline SVG graphics. Uses Noto Emoji SVG files (Apache
 * 2.0 licensed) for vector rendering. This ensures emojis render correctly in PDFs without font
 * dependencies.
 *
 * <p>Supports both color (Noto Color Emoji) and monochrome (Noto Emoji) variants.
 */
@ApplicationScoped
public class EmojiSvgService {

    private static final Logger LOG = Logger.getLogger(EmojiSvgService.class);

    // Map from emoji character to SVG content (inner content only, no outer <svg> wrapper)
    // Color SVGs from emoji-svg/ folder
    private final Map<String, String> colorSvgCache = new HashMap<>();
    private final Map<String, String> colorViewBoxCache = new HashMap<>();

    // Monochrome SVGs from emoji-svg-mono/ folder
    private final Map<String, String> monoSvgCache = new HashMap<>();
    private final Map<String, String> monoViewBoxCache = new HashMap<>();

    // Default viewBox for most emojis
    private static final String DEFAULT_VIEWBOX = "0 0 128 128";

    // Map from emoji to Noto Emoji filename (without .svg extension)
    // Noto Emoji files are named: emoji_u{codepoint}.svg or emoji_u{cp1}_u{cp2}.svg for ZWJ
    // sequences
    private static final Map<String, String> EMOJI_TO_FILENAME = new HashMap<>();

    static {
        // US Holidays
        EMOJI_TO_FILENAME.put("🎆", "emoji_u1f386"); // Fireworks
        EMOJI_TO_FILENAME.put("🏛️", "emoji_u1f3db"); // Classical Building (Presidents' Day)
        EMOJI_TO_FILENAME.put("❤️", "emoji_u2764"); // Red Heart (Valentine's)
        EMOJI_TO_FILENAME.put("☘️", "emoji_u2618"); // Shamrock (St. Patrick's)
        EMOJI_TO_FILENAME.put("🐰", "emoji_u1f430"); // Rabbit (Easter)
        EMOJI_TO_FILENAME.put("🇺🇸", "emoji_u1f1fa_1f1f8"); // US Flag (July 4th, Veterans Day)
        EMOJI_TO_FILENAME.put("👷", "emoji_u1f477"); // Construction Worker (Labor Day)
        EMOJI_TO_FILENAME.put("🦃", "emoji_u1f983"); // Turkey (Thanksgiving)
        EMOJI_TO_FILENAME.put("🎃", "emoji_u1f383"); // Jack-o-lantern (Halloween)
        EMOJI_TO_FILENAME.put("💀", "emoji_u1f480"); // Skull (Halloween/Day of Dead)
        EMOJI_TO_FILENAME.put("🎄", "emoji_u1f384"); // Christmas Tree
        EMOJI_TO_FILENAME.put("🎁", "emoji_u1f381"); // Gift (Christmas)

        // Christian Holidays
        EMOJI_TO_FILENAME.put("🐟", "emoji_u1f41f"); // Fish (Good Friday)
        EMOJI_TO_FILENAME.put("✝️", "emoji_u271d"); // Latin Cross (Ash Wednesday)
        EMOJI_TO_FILENAME.put("☁️", "emoji_u2601"); // Cloud (Ascension Day)
        EMOJI_TO_FILENAME.put("👼", "emoji_u1f47c"); // Baby Angel (All Saints Day)

        // Jewish Holidays
        EMOJI_TO_FILENAME.put("✡️", "emoji_u2721"); // Star of David
        EMOJI_TO_FILENAME.put("🕎", "emoji_u1f54e"); // Menorah (Hanukkah)

        // Islamic Holidays
        EMOJI_TO_FILENAME.put("🌙", "emoji_u1f319"); // Crescent Moon (Ramadan, Eid)
        EMOJI_TO_FILENAME.put("☪️", "emoji_u262a"); // Star and Crescent
        EMOJI_TO_FILENAME.put("🤲", "emoji_u1f932"); // Palms Up Together

        // Hindu Holidays
        EMOJI_TO_FILENAME.put("🪔", "emoji_u1fa94"); // Diya Lamp (Diwali)
        EMOJI_TO_FILENAME.put("🪁", "emoji_u1fa81"); // Kite (Makar Sankranti)
        EMOJI_TO_FILENAME.put("🪈", "emoji_u1fa88"); // Flute (Janmashtami)

        // Chinese Holidays
        EMOJI_TO_FILENAME.put("🧧", "emoji_u1f9e7"); // Red Envelope (Chinese New Year)
        EMOJI_TO_FILENAME.put("🏮", "emoji_u1f3ee"); // Red Lantern
        EMOJI_TO_FILENAME.put("🥮", "emoji_u1f96e"); // Moon Cake (Mid-Autumn)
        EMOJI_TO_FILENAME.put("🪦", "emoji_u1faa6"); // Headstone (Qingming)
        EMOJI_TO_FILENAME.put("🏔️", "emoji_u1f3d4"); // Snow-Capped Mountain

        // Other/Seasonal
        EMOJI_TO_FILENAME.put("🌸", "emoji_u1f338"); // Cherry Blossom (Spring)
        EMOJI_TO_FILENAME.put("☀️", "emoji_u2600"); // Sun
        EMOJI_TO_FILENAME.put("🌍", "emoji_u1f30d"); // Earth Globe (Earth Day)
        EMOJI_TO_FILENAME.put("🕊️", "emoji_u1f54a"); // Dove (Peace)
        EMOJI_TO_FILENAME.put("🙏", "emoji_u1f64f"); // Folded Hands
        EMOJI_TO_FILENAME.put("🎉", "emoji_u1f389"); // Party Popper
        EMOJI_TO_FILENAME.put("⭐", "emoji_u2b50"); // Star
        EMOJI_TO_FILENAME.put("🌕", "emoji_u1f315"); // Full Moon
        EMOJI_TO_FILENAME.put("🌈", "emoji_u1f308"); // Rainbow
        EMOJI_TO_FILENAME.put("🏳️‍🌈", "emoji_u1f3f3_200d_1f308"); // Rainbow Flag (Pride)
        EMOJI_TO_FILENAME.put("🐿️", "emoji_u1f43f"); // Chipmunk (Groundhog Day substitute)
        EMOJI_TO_FILENAME.put("🦫", "emoji_u1f9ab"); // Beaver (Groundhog Day substitute)
        EMOJI_TO_FILENAME.put("⛰️", "emoji_u26f0"); // Mountain
        EMOJI_TO_FILENAME.put("⚔️", "emoji_u2694"); // Crossed Swords
        EMOJI_TO_FILENAME.put("🎖️", "emoji_u1f396"); // Military Medal
        EMOJI_TO_FILENAME.put("🕯️", "emoji_u1f56f"); // Candle

        // Pagan/Wiccan Holidays (Wheel of the Year)
        EMOJI_TO_FILENAME.put("🐣", "emoji_u1f423"); // Hatching Chick (Ostara)
        EMOJI_TO_FILENAME.put("🔥", "emoji_u1f525"); // Fire (Beltane)
        EMOJI_TO_FILENAME.put("🌾", "emoji_u1f33e"); // Sheaf of Rice (Lughnasadh)
        EMOJI_TO_FILENAME.put("🍂", "emoji_u1f342"); // Fallen Leaf (Mabon)
        EMOJI_TO_FILENAME.put("🌲", "emoji_u1f332"); // Evergreen Tree (Yule)
        EMOJI_TO_FILENAME.put("🌿", "emoji_u1f33f"); // Herb (Sukkot)

        // Jewish Holidays (additional)
        EMOJI_TO_FILENAME.put("🍎", "emoji_u1f34e"); // Red Apple (Rosh Hashanah)
        EMOJI_TO_FILENAME.put("🍷", "emoji_u1f377"); // Wine Glass (Passover)
        EMOJI_TO_FILENAME.put("📜", "emoji_u1f4dc"); // Scroll (Shavuot, Simchat Torah)
        EMOJI_TO_FILENAME.put("🎭", "emoji_u1f3ad"); // Performing Arts (Purim)

        // Mexican Holidays
        EMOJI_TO_FILENAME.put("🇲🇽", "emoji_u1f1f2_1f1fd"); // Mexico Flag
        EMOJI_TO_FILENAME.put("👑", "emoji_u1f451"); // Crown (Día de los Reyes)
        EMOJI_TO_FILENAME.put("⚖️", "emoji_u2696"); // Balance Scale (Benito Juárez)
        EMOJI_TO_FILENAME.put("🌮", "emoji_u1f32e"); // Taco (Cinco de Mayo alternate)
        EMOJI_TO_FILENAME.put("💃", "emoji_u1f483"); // Woman Dancing

        // Canadian Holidays
        EMOJI_TO_FILENAME.put("🍁", "emoji_u1f341"); // Maple Leaf (Canada Day)
        EMOJI_TO_FILENAME.put(
                "👨‍👩‍👧‍👦",
                "emoji_u1f468_200d_1f469_200d_1f467_200d_1f466"); // Family (Family Day)

        // UK Holidays
        EMOJI_TO_FILENAME.put("🌷", "emoji_u1f337"); // Tulip (Spring Bank Holiday)

        // Various Holidays
        EMOJI_TO_FILENAME.put("✨", "emoji_u2728"); // Sparkles
        EMOJI_TO_FILENAME.put("🌹", "emoji_u1f339"); // Rose (Valentine's alternate)
        EMOJI_TO_FILENAME.put("💐", "emoji_u1f490"); // Bouquet (Mother's Day)
        EMOJI_TO_FILENAME.put("👔", "emoji_u1f454"); // Necktie (Father's Day)
        EMOJI_TO_FILENAME.put("🍾", "emoji_u1f37e"); // Champagne (New Year)
        EMOJI_TO_FILENAME.put("🎨", "emoji_u1f3a8"); // Artist Palette
        EMOJI_TO_FILENAME.put("🏹", "emoji_u1f3f9"); // Bow and Arrow
        EMOJI_TO_FILENAME.put("🐉", "emoji_u1f409"); // Dragon (Chinese New Year)
        EMOJI_TO_FILENAME.put("🐑", "emoji_u1f411"); // Sheep/Ram (Eid, Easter)
        EMOJI_TO_FILENAME.put("🐘", "emoji_u1f418"); // Elephant (Diwali/Hindu)
        EMOJI_TO_FILENAME.put("🦅", "emoji_u1f985"); // Eagle (July 4th alternate)
        EMOJI_TO_FILENAME.put("🛒", "emoji_u1f6d2"); // Shopping Cart (Black Friday)
        EMOJI_TO_FILENAME.put("🃏", "emoji_u1f0cf"); // Joker (April Fools)
    }

    @PostConstruct
    void init() {
        LOG.info("Initializing EmojiSvgService - loading emoji SVGs from resources");
        int colorLoaded = 0;
        int monoLoaded = 0;
        int colorFailed = 0;
        int monoFailed = 0;

        for (Map.Entry<String, String> entry : EMOJI_TO_FILENAME.entrySet()) {
            String emoji = entry.getKey();
            String colorFilename = entry.getValue();
            // Mono files use different naming: emoji_uXXXX.svg -> uXXXX.svg
            String monoFilename = colorFilename.replace("emoji_", "");

            // Load color SVG
            String[] colorResult = loadSvgFromResources("emoji-svg/" + colorFilename + ".svg");
            if (colorResult.length > 0) {
                String normalizedEmoji = normalizeEmoji(emoji);
                colorSvgCache.put(normalizedEmoji, colorResult[0]);
                colorViewBoxCache.put(normalizedEmoji, colorResult[1]);
                colorLoaded++;
            } else {
                colorFailed++;
                LOG.debugf("Color SVG not found for emoji %s (file: %s.svg)", emoji, colorFilename);
            }

            // Load monochrome SVG
            String[] monoResult = loadSvgFromResources("emoji-svg-mono/" + monoFilename + ".svg");
            if (monoResult.length > 0) {
                String normalizedEmoji = normalizeEmoji(emoji);
                monoSvgCache.put(normalizedEmoji, monoResult[0]);
                monoViewBoxCache.put(normalizedEmoji, monoResult[1]);
                monoLoaded++;
            } else {
                monoFailed++;
                LOG.debugf("Mono SVG not found for emoji %s (file: %s.svg)", emoji, monoFilename);
            }
        }

        LOG.infof(
                "EmojiSvgService initialized: color=%d/%d, mono=%d/%d",
                colorLoaded, colorLoaded + colorFailed, monoLoaded, monoLoaded + monoFailed);
    }

    /**
     * Load SVG content from resources folder. Returns an array with [innerContent, viewBox] or null
     * if not found.
     *
     * @param resourcePath Full resource path (e.g., "emoji-svg/emoji_u1f384.svg")
     * @return String array with [innerContent, viewBox] or null
     */
    private String[] loadSvgFromResources(String resourcePath) {
        try (InputStream is =
                Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                return new String[0];
            }
            String fullSvg =
                    new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                            .lines()
                            .collect(Collectors.joining(System.lineSeparator()));

            // Extract viewBox from the SVG
            String viewBox = extractViewBox(fullSvg);

            // Extract the inner content (we'll wrap it in our own <svg> or <g> when embedding)
            String innerContent = extractSvgInnerContent(fullSvg);

            return new String[] {innerContent, viewBox};
        } catch (Exception e) {
            LOG.warnf("Error loading emoji SVG %s: %s", resourcePath, e.getMessage());
            return new String[0];
        }
    }

    /** Extract viewBox attribute from SVG element. */
    private String extractViewBox(String fullSvg) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("viewBox=\"([^\"]+)\"");
        java.util.regex.Matcher matcher = pattern.matcher(fullSvg);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return DEFAULT_VIEWBOX;
    }

    /**
     * Extract the inner content from an SVG file. Removes the outer <svg> element and returns
     * everything inside.
     */
    private String extractSvgInnerContent(String fullSvg) {
        // Find the opening <svg> tag end
        int svgStart = fullSvg.indexOf("<svg");
        if (svgStart == -1) return fullSvg;

        int svgTagEnd = fullSvg.indexOf(">", svgStart);
        if (svgTagEnd == -1) return fullSvg;

        // Find the closing </svg> tag
        int svgEnd = fullSvg.lastIndexOf("</svg>");
        if (svgEnd == -1) return fullSvg;

        // Return everything between
        return fullSvg.substring(svgTagEnd + 1, svgEnd).trim();
    }

    /**
     * Check if an emoji has an SVG representation available. Returns true if either color or mono
     * SVG is available.
     */
    public boolean hasEmojiSvg(String emoji) {
        String normalized = normalizeEmoji(emoji);
        return colorSvgCache.containsKey(normalized) || monoSvgCache.containsKey(normalized);
    }

    /** Check if an emoji has a specific variant (color or mono) available. */
    public boolean hasEmojiSvg(String emoji, boolean monochrome) {
        String normalized = normalizeEmoji(emoji);
        if (monochrome) {
            return monoSvgCache.containsKey(normalized);
        }
        return colorSvgCache.containsKey(normalized);
    }

    /**
     * Get a standalone SVG for preview purposes (not for embedding in another SVG).
     *
     * @param emoji The emoji character(s)
     * @param monochrome If true, use monochrome SVG variant
     * @param colorHex Optional hex color for colorization (requires monochrome=true)
     * @return Complete standalone SVG string, or null if emoji SVG not available
     */
    public String getStandaloneSvg(String emoji, boolean monochrome, String colorHex) {
        String normalized = normalizeEmoji(emoji);

        String innerContent;
        String viewBox;

        if (monochrome) {
            innerContent = monoSvgCache.get(normalized);
            if (innerContent != null) {
                viewBox = monoViewBoxCache.getOrDefault(normalized, DEFAULT_VIEWBOX);
            } else {
                // Fall back to color SVG
                innerContent = colorSvgCache.get(normalized);
                viewBox = colorViewBoxCache.getOrDefault(normalized, DEFAULT_VIEWBOX);
            }
        } else {
            innerContent = colorSvgCache.get(normalized);
            viewBox = colorViewBoxCache.getOrDefault(normalized, DEFAULT_VIEWBOX);
        }

        if (innerContent == null) {
            return null;
        }

        if (colorHex != null && !colorHex.isEmpty() && monochrome) {
            String color = colorHex.startsWith("#") ? colorHex : "#" + colorHex;
            return String.format(
                    "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"%s\">"
                            + "<g fill=\"%s\">%s</g></svg>",
                    viewBox, color, innerContent);
        } else {
            return String.format(
                    "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"%s\">%s</svg>",
                    viewBox, innerContent);
        }
    }

    /**
     * Get the SVG representation of an emoji as an embeddable SVG element.
     *
     * @param emoji The emoji character(s)
     * @param x X position
     * @param y Y position
     * @param size Size (width and height) in pixels
     * @return SVG element string, or null if emoji SVG not available
     */
    public String getEmojiAsSvg(String emoji, double x, double y, double size) {
        return getEmojiAsSvg(emoji, x, y, size, false);
    }

    /**
     * Get the SVG representation of an emoji as an embeddable SVG element.
     *
     * @param emoji The emoji character(s)
     * @param x X position
     * @param y Y position
     * @param size Size (width and height) in pixels
     * @param monochrome If true, use monochrome SVG variant (or fall back to grayscale filter)
     * @return SVG element string, or null if emoji SVG not available
     */
    public String getEmojiAsSvg(String emoji, double x, double y, double size, boolean monochrome) {
        return getEmojiAsSvg(emoji, x, y, size, monochrome, null);
    }

    /**
     * Get the SVG representation of an emoji as an embeddable SVG element with optional
     * colorization.
     *
     * @param emoji The emoji character(s)
     * @param x X position
     * @param y Y position
     * @param size Size (width and height) in pixels
     * @param monochrome If true, use monochrome SVG variant (or fall back to grayscale filter)
     * @param colorHex Optional hex color (e.g., "#DC2626") to colorize the emoji (requires
     *     monochrome=true)
     * @return SVG element string, or null if emoji SVG not available
     */
    public String getEmojiAsSvg(
            String emoji, double x, double y, double size, boolean monochrome, String colorHex) {
        String normalized = normalizeEmoji(emoji);

        // Choose the appropriate cache
        String innerContent;
        String viewBox;
        boolean usingMonoSvg = false;

        if (monochrome) {
            // Try monochrome SVG first
            innerContent = monoSvgCache.get(normalized);
            if (innerContent != null) {
                usingMonoSvg = true;
                viewBox = monoViewBoxCache.getOrDefault(normalized, DEFAULT_VIEWBOX);
            } else {
                // Fall back to color SVG with grayscale filter
                innerContent = colorSvgCache.get(normalized);
                viewBox = colorViewBoxCache.getOrDefault(normalized, DEFAULT_VIEWBOX);
            }
        } else {
            // Use color SVG
            innerContent = colorSvgCache.get(normalized);
            viewBox = colorViewBoxCache.getOrDefault(normalized, DEFAULT_VIEWBOX);
        }

        if (innerContent == null) {
            return null;
        }

        // Make IDs unique per embedded SVG to avoid conflicts and resolution issues
        String uniquePrefix = String.format("e%.0f_%.0f_", x, y);
        innerContent = makeIdsUnique(innerContent, uniquePrefix);

        // Use the correct viewBox from the original SVG
        // We embed as a nested <svg> element with proper positioning and scaling
        // Include xlink namespace for SVGs that use xlink:href attributes
        if (colorHex != null && !colorHex.isEmpty() && monochrome) {
            // Colorized monochrome: wrap content in a group with fill color set
            // This handles SVGs that have no explicit fill (default black) as well as explicit
            // fills
            String color = colorHex.startsWith("#") ? colorHex : "#" + colorHex;
            return String.format(
                    "<svg x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" viewBox=\"%s\""
                            + " xmlns:xlink=\"http://www.w3.org/1999/xlink\"><g"
                            + " fill=\"%s\">%s</g></svg>",
                    x, y, size, size, viewBox, color, innerContent);
        } else if (monochrome && !usingMonoSvg) {
            // Fall back to grayscale filter for color SVG (when mono SVG not available)
            String filterId = uniquePrefix + "grayscale";
            return String.format(
                    "<svg x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" viewBox=\"%s\""
                            + " xmlns:xlink=\"http://www.w3.org/1999/xlink\"><defs><filter"
                            + " id=\"%s\"><feColorMatrix type=\"saturate\""
                            + " values=\"0\"/></filter></defs><g filter=\"url(#%s)\">%s</g></svg>",
                    x, y, size, size, viewBox, filterId, filterId, innerContent);
        } else {
            // Use SVG directly (either color or true mono)
            return String.format(
                    "<svg x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" viewBox=\"%s\""
                            + " xmlns:xlink=\"http://www.w3.org/1999/xlink\">%s</svg>",
                    x, y, size, size, viewBox, innerContent);
        }
    }

    /**
     * Make all IDs in SVG content unique by adding a prefix. This prevents ID collisions when
     * multiple emoji SVGs are embedded in the same document. Updates both id="..." definitions and
     * url(#...) / xlink:href="#..." references.
     */
    private String makeIdsUnique(String svgContent, String prefix) {
        // Find all IDs used in the SVG and replace them with prefixed versions
        // Pattern matches: id="something" and id='something'
        java.util.regex.Pattern idPattern = java.util.regex.Pattern.compile("id=\"([^\"]+)\"");
        java.util.regex.Matcher matcher = idPattern.matcher(svgContent);

        java.util.Set<String> foundIds = new java.util.HashSet<>();
        while (matcher.find()) {
            foundIds.add(matcher.group(1));
        }

        // Replace each ID and its references
        String result = svgContent;
        for (String id : foundIds) {
            String newId = prefix + id;
            // Replace id definitions
            result = result.replace("id=\"" + id + "\"", "id=\"" + newId + "\"");
            // Replace url(#id) references
            result = result.replace("url(#" + id + ")", "url(#" + newId + ")");
            // Replace xlink:href="#id" references
            result = result.replace("xlink:href=\"#" + id + "\"", "xlink:href=\"#" + newId + "\"");
            // Replace href="#id" references (SVG2 style)
            result = result.replace("href=\"#" + id + "\"", "href=\"#" + newId + "\"");
        }

        return result;
    }

    /**
     * Normalize emoji by removing variation selectors for lookup. Variation selector VS16 (U+FE0F)
     * is often appended for emoji presentation.
     */
    private String normalizeEmoji(String emoji) {
        // Remove VS16 (emoji presentation selector)
        return emoji.replace("\uFE0F", "");
    }

    /**
     * Get all available emoji characters that have SVG representations. Returns emojis from both
     * color and mono caches.
     */
    public java.util.Set<String> getAvailableEmojis() {
        java.util.Set<String> all = new java.util.HashSet<>();
        all.addAll(colorSvgCache.keySet());
        all.addAll(monoSvgCache.keySet());
        return all;
    }
}
