package villagecompute.calendar.util;

/**
 * Color constants for the application.
 *
 * <p>
 * Provides human-readable names for frequently used color values. All colors are in standard CSS hex format (#RRGGBB)
 * or rgba format for transparency.
 */
public final class Colors {

    private Colors() {
    }

    // ========================================
    // Core Colors
    // ========================================

    public static final String BLACK = "#000000";
    public static final String WHITE = "#ffffff";

    // ========================================
    // Grays
    // ========================================

    public static final String GRAY_900 = "#333333"; // Dark charcoal
    public static final String GRAY_600 = "#666666"; // Medium gray
    public static final String GRAY_400 = "#c1c1c1"; // Light gray / silver

    // ========================================
    // Seasonal Calendar Colors
    // ========================================

    /** Winter frost - January background */
    public static final String WINTER_FROST = "#E8F1F2";

    /** Early spring mint - March background */
    public static final String SPRING_MINT = "#E9F7EF";

    /** Spring bloom - April/May background */
    public static final String SPRING_LIME = "#c8fc9f";

    /** Lavender accent for spring */
    public static final String SPRING_LAVENDER = "#d1a4fd";

    /** Early summer - June background */
    public static final String SUMMER_AQUAMARINE = "#66CDAA";

    /** Mid summer - July background */
    public static final String SUMMER_GREEN = "#3CB371";

    /** Late summer - August background */
    public static final String SUMMER_CYAN = "#5bf0ff";

    // ========================================
    // UI Accent Colors
    // ========================================

    /** Warning/attention orange */
    public static final String ORANGE = "#F39C12";

    /** Success green (Material Design) */
    public static final String GREEN = "#4CAF50";

    /** Error/danger red */
    public static final String RED = "#DC2626";

    /** Gold accent */
    public static final String GOLD = "#FFD700";

    /** Emerald accent */
    public static final String EMERALD = "#82E0AA";

    // ========================================
    // Semi-transparent overlays
    // ========================================

    /** Light gray overlay - late fall */
    public static final String OVERLAY_GRAY_LIGHT = "rgba(161,161,161,0.30)";

    /** Medium gray overlay - winter */
    public static final String OVERLAY_GRAY_MEDIUM = "rgba(161,161,161,0.6)";
}
