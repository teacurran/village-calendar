package villagecompute.calendar.services;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for converting emoji characters to inline SVG graphics.
 * Uses Noto Emoji SVG files (Apache 2.0 licensed) for vector rendering.
 * This ensures emojis render correctly in PDFs without font dependencies.
 */
@ApplicationScoped
public class EmojiSvgService {

    private static final Logger LOG = Logger.getLogger(EmojiSvgService.class);

    // Map from emoji character to SVG content (inner content only, no outer <svg> wrapper)
    private final Map<String, String> emojiSvgCache = new HashMap<>();

    // Map from emoji to Noto Emoji filename (without .svg extension)
    // Noto Emoji files are named: emoji_u{codepoint}.svg or emoji_u{cp1}_u{cp2}.svg for ZWJ sequences
    private static final Map<String, String> EMOJI_TO_FILENAME = new HashMap<>();

    static {
        // US Holidays
        EMOJI_TO_FILENAME.put("🎆", "emoji_u1f386");  // Fireworks (New Year, July 4th)
        EMOJI_TO_FILENAME.put("❤️", "emoji_u2764");   // Red Heart (Valentine's)
        EMOJI_TO_FILENAME.put("☘️", "emoji_u2618");   // Shamrock (St. Patrick's)
        EMOJI_TO_FILENAME.put("🐰", "emoji_u1f430");  // Rabbit (Easter)
        EMOJI_TO_FILENAME.put("🇺🇸", "emoji_u1f1fa_1f1f8"); // US Flag (July 4th, Veterans Day)
        EMOJI_TO_FILENAME.put("🦃", "emoji_u1f983");  // Turkey (Thanksgiving)
        EMOJI_TO_FILENAME.put("🎃", "emoji_u1f383");  // Jack-o-lantern (Halloween)
        EMOJI_TO_FILENAME.put("💀", "emoji_u1f480");  // Skull (Halloween/Day of Dead)
        EMOJI_TO_FILENAME.put("🎄", "emoji_u1f384");  // Christmas Tree
        EMOJI_TO_FILENAME.put("🎁", "emoji_u1f381");  // Gift (Christmas)

        // Jewish Holidays
        EMOJI_TO_FILENAME.put("✡️", "emoji_u2721");   // Star of David
        EMOJI_TO_FILENAME.put("🕎", "emoji_u1f54e");  // Menorah (Hanukkah)

        // Islamic Holidays
        EMOJI_TO_FILENAME.put("🌙", "emoji_u1f319");  // Crescent Moon (Ramadan, Eid)
        EMOJI_TO_FILENAME.put("☪️", "emoji_u262a");   // Star and Crescent
        EMOJI_TO_FILENAME.put("🤲", "emoji_u1f932");  // Palms Up Together

        // Hindu Holidays
        EMOJI_TO_FILENAME.put("🪔", "emoji_u1fa94");  // Diya Lamp (Diwali)
        EMOJI_TO_FILENAME.put("🪁", "emoji_u1fa81");  // Kite (Makar Sankranti)
        EMOJI_TO_FILENAME.put("🪈", "emoji_u1fa88");  // Flute (Janmashtami)

        // Chinese Holidays
        EMOJI_TO_FILENAME.put("🧧", "emoji_u1f9e7");  // Red Envelope (Chinese New Year)
        EMOJI_TO_FILENAME.put("🏮", "emoji_u1f3ee");  // Red Lantern
        EMOJI_TO_FILENAME.put("🥮", "emoji_u1f96e");  // Moon Cake (Mid-Autumn)
        EMOJI_TO_FILENAME.put("🪦", "emoji_u1faa6");  // Headstone (Qingming)
        EMOJI_TO_FILENAME.put("🏔️", "emoji_u1f3d4");  // Snow-Capped Mountain

        // Other/Seasonal
        EMOJI_TO_FILENAME.put("🌸", "emoji_u1f338");  // Cherry Blossom (Spring)
        EMOJI_TO_FILENAME.put("☀️", "emoji_u2600");   // Sun
        EMOJI_TO_FILENAME.put("🌍", "emoji_u1f30d");  // Earth Globe (Earth Day)
        EMOJI_TO_FILENAME.put("🕊️", "emoji_u1f54a");  // Dove (Peace)
        EMOJI_TO_FILENAME.put("🙏", "emoji_u1f64f");  // Folded Hands
        EMOJI_TO_FILENAME.put("🎉", "emoji_u1f389");  // Party Popper
        EMOJI_TO_FILENAME.put("⭐", "emoji_u2b50");   // Star
        EMOJI_TO_FILENAME.put("🌕", "emoji_u1f315");  // Full Moon
        EMOJI_TO_FILENAME.put("🌈", "emoji_u1f308");  // Rainbow
        EMOJI_TO_FILENAME.put("🏳️‍🌈", "emoji_u1f3f3_200d_1f308"); // Rainbow Flag (Pride)
        EMOJI_TO_FILENAME.put("🐿️", "emoji_u1f43f");  // Chipmunk (Groundhog Day substitute)
        EMOJI_TO_FILENAME.put("🦫", "emoji_u1f9ab");  // Beaver (Groundhog Day substitute)
        EMOJI_TO_FILENAME.put("⛰️", "emoji_u26f0");   // Mountain
        EMOJI_TO_FILENAME.put("⚔️", "emoji_u2694");   // Crossed Swords
        EMOJI_TO_FILENAME.put("🎖️", "emoji_u1f396");  // Military Medal
        EMOJI_TO_FILENAME.put("🕯️", "emoji_u1f56f");  // Candle

        // Pagan/Wiccan Holidays (Wheel of the Year)
        EMOJI_TO_FILENAME.put("🐣", "emoji_u1f423");  // Hatching Chick (Ostara)
        EMOJI_TO_FILENAME.put("🔥", "emoji_u1f525");  // Fire (Beltane)
        EMOJI_TO_FILENAME.put("🌾", "emoji_u1f33e");  // Sheaf of Rice (Lughnasadh)
        EMOJI_TO_FILENAME.put("🍂", "emoji_u1f342");  // Fallen Leaf (Mabon)
        EMOJI_TO_FILENAME.put("🌲", "emoji_u1f332");  // Evergreen Tree (Yule)
        EMOJI_TO_FILENAME.put("🌿", "emoji_u1f33f");  // Herb (Sukkot)

        // Jewish Holidays (additional)
        EMOJI_TO_FILENAME.put("🍎", "emoji_u1f34e");  // Red Apple (Rosh Hashanah)
        EMOJI_TO_FILENAME.put("🍷", "emoji_u1f377");  // Wine Glass (Passover)
        EMOJI_TO_FILENAME.put("📜", "emoji_u1f4dc");  // Scroll (Shavuot, Simchat Torah)
        EMOJI_TO_FILENAME.put("🎭", "emoji_u1f3ad");  // Performing Arts (Purim)

        // Mexican Holidays
        EMOJI_TO_FILENAME.put("🇲🇽", "emoji_u1f1f2_1f1fd");  // Mexico Flag
        EMOJI_TO_FILENAME.put("👑", "emoji_u1f451");  // Crown (Día de los Reyes)
        EMOJI_TO_FILENAME.put("⚖️", "emoji_u2696");   // Balance Scale (Benito Juárez)
        EMOJI_TO_FILENAME.put("🌮", "emoji_u1f32e");  // Taco (Cinco de Mayo alternate)
        EMOJI_TO_FILENAME.put("💃", "emoji_u1f483");  // Woman Dancing

        // Various Holidays
        EMOJI_TO_FILENAME.put("✨", "emoji_u2728");   // Sparkles
        EMOJI_TO_FILENAME.put("🌹", "emoji_u1f339");  // Rose (Valentine's alternate)
        EMOJI_TO_FILENAME.put("💐", "emoji_u1f490");  // Bouquet (Mother's Day)
        EMOJI_TO_FILENAME.put("👔", "emoji_u1f454");  // Necktie (Father's Day)
        EMOJI_TO_FILENAME.put("🍾", "emoji_u1f37e");  // Champagne (New Year)
        EMOJI_TO_FILENAME.put("🎨", "emoji_u1f3a8");  // Artist Palette
        EMOJI_TO_FILENAME.put("🏹", "emoji_u1f3f9");  // Bow and Arrow
        EMOJI_TO_FILENAME.put("🐉", "emoji_u1f409");  // Dragon (Chinese New Year)
        EMOJI_TO_FILENAME.put("🐑", "emoji_u1f411");  // Sheep/Ram (Eid)
        EMOJI_TO_FILENAME.put("🐘", "emoji_u1f418");  // Elephant (Diwali/Hindu)
        EMOJI_TO_FILENAME.put("🦅", "emoji_u1f985");  // Eagle (July 4th alternate)
        EMOJI_TO_FILENAME.put("🛒", "emoji_u1f6d2");  // Shopping Cart (Black Friday)
        EMOJI_TO_FILENAME.put("🃏", "emoji_u1f0cf");  // Joker (April Fools)
    }

    @PostConstruct
    void init() {
        LOG.info("Initializing EmojiSvgService - loading emoji SVGs from resources");
        int loaded = 0;
        int failed = 0;

        for (Map.Entry<String, String> entry : EMOJI_TO_FILENAME.entrySet()) {
            String emoji = entry.getKey();
            String filename = entry.getValue();
            String svgContent = loadSvgFromResources(filename);
            if (svgContent != null) {
                // Normalize the emoji key when storing in cache to match lookup behavior
                emojiSvgCache.put(normalizeEmoji(emoji), svgContent);
                loaded++;
            } else {
                failed++;
                LOG.debugf("SVG not found for emoji %s (file: %s.svg)", emoji, filename);
            }
        }

        LOG.infof("EmojiSvgService initialized: %d emojis loaded, %d not found", loaded, failed);
    }

    /**
     * Load SVG content from resources folder.
     * Returns the inner content of the SVG (everything inside the root <svg> element).
     */
    private String loadSvgFromResources(String filename) {
        String resourcePath = "emoji-svg/" + filename + ".svg";
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                return null;
            }
            String fullSvg = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));

            // Extract the inner content (we'll wrap it in our own <svg> or <g> when embedding)
            return extractSvgInnerContent(fullSvg);
        } catch (Exception e) {
            LOG.warnf("Error loading emoji SVG %s: %s", filename, e.getMessage());
            return null;
        }
    }

    /**
     * Extract the inner content from an SVG file.
     * Removes the outer <svg> element and returns everything inside.
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
     * Check if an emoji has an SVG representation available.
     */
    public boolean hasEmojiSvg(String emoji) {
        return emojiSvgCache.containsKey(normalizeEmoji(emoji));
    }

    /**
     * Get the SVG representation of an emoji as an embeddable SVG element.
     *
     * @param emoji The emoji character(s)
     * @param x     X position
     * @param y     Y position
     * @param size  Size (width and height) in pixels
     * @return SVG element string, or null if emoji SVG not available
     */
    public String getEmojiAsSvg(String emoji, double x, double y, double size) {
        return getEmojiAsSvg(emoji, x, y, size, false);
    }

    /**
     * Get the SVG representation of an emoji as an embeddable SVG element.
     *
     * @param emoji      The emoji character(s)
     * @param x          X position
     * @param y          Y position
     * @param size       Size (width and height) in pixels
     * @param monochrome If true, apply grayscale filter for black & white rendering
     * @return SVG element string, or null if emoji SVG not available
     */
    public String getEmojiAsSvg(String emoji, double x, double y, double size, boolean monochrome) {
        String normalized = normalizeEmoji(emoji);
        String innerContent = emojiSvgCache.get(normalized);

        if (innerContent == null) {
            return null;
        }

        // Make IDs unique per embedded SVG to avoid conflicts and resolution issues
        String uniquePrefix = String.format("e%.0f_%.0f_", x, y);
        innerContent = makeIdsUnique(innerContent, uniquePrefix);

        // Noto Emoji SVGs are typically 128x128 viewBox
        // We embed as a nested <svg> element with proper positioning and scaling
        // Include xlink namespace for SVGs that use xlink:href attributes
        if (monochrome) {
            // Apply grayscale filter for monochrome mode
            // Using a unique filter ID based on position to avoid conflicts
            String filterId = uniquePrefix + "grayscale";
            return String.format(
                "<svg x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" viewBox=\"0 0 128 128\" overflow=\"visible\" xmlns:xlink=\"http://www.w3.org/1999/xlink\">" +
                "<defs><filter id=\"%s\"><feColorMatrix type=\"saturate\" values=\"0\"/></filter></defs>" +
                "<g filter=\"url(#%s)\">%s</g></svg>",
                x, y, size, size, filterId, filterId, innerContent
            );
        } else {
            return String.format(
                "<svg x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" viewBox=\"0 0 128 128\" overflow=\"visible\" xmlns:xlink=\"http://www.w3.org/1999/xlink\">%s</svg>",
                x, y, size, size, innerContent
            );
        }
    }

    /**
     * Make all IDs in SVG content unique by adding a prefix.
     * This prevents ID collisions when multiple emoji SVGs are embedded in the same document.
     * Updates both id="..." definitions and url(#...) / xlink:href="#..." references.
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
     * Normalize emoji by removing variation selectors for lookup.
     * Variation selector VS16 (U+FE0F) is often appended for emoji presentation.
     */
    private String normalizeEmoji(String emoji) {
        // Remove VS16 (emoji presentation selector)
        return emoji.replace("\uFE0F", "");
    }

    /**
     * Get all available emoji characters that have SVG representations.
     */
    public java.util.Set<String> getAvailableEmojis() {
        return emojiSvgCache.keySet();
    }
}
