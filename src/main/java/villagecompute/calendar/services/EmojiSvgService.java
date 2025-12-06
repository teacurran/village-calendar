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
                emojiSvgCache.put(emoji, svgContent);
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
        String normalized = normalizeEmoji(emoji);
        String innerContent = emojiSvgCache.get(normalized);

        if (innerContent == null) {
            return null;
        }

        // Noto Emoji SVGs are typically 128x128 viewBox
        // We embed as a nested <svg> element with proper positioning and scaling
        return String.format(
            "<svg x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" viewBox=\"0 0 128 128\" overflow=\"visible\">%s</svg>",
            x, y, size, size, innerContent
        );
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
