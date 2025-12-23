package villagecompute.calendar.data.models;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.graphql.Ignore;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * Entity representing a line item within an order.
 * Supports multiple generator types (calendar, maze, etc.) with frozen SVG assets.
 */
@Entity
@Table(
    name = "calendar_order_items",
    indexes = {
        @Index(name = "idx_order_items_order", columnList = "order_id"),
        @Index(name = "idx_order_items_calendar", columnList = "calendar_id"),
        @Index(name = "idx_order_items_shipment", columnList = "shipment_id")
    }
)
public class CalendarOrderItem extends DefaultPanacheEntityWithTimestamps {

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_items_order"))
    @Ignore
    public CalendarOrder order;

    /**
     * Type of generator: 'calendar', 'maze', etc.
     */
    @Size(max = 50)
    @Column(name = "generator_type", length = 50)
    public String generatorType;

    /**
     * User-facing description like "2026 Calendar" or "Hard Orthogonal Maze"
     */
    @Size(max = 500)
    @Column(name = "description", length = 500)
    public String description;

    /**
     * @deprecated Use generatorType instead. Kept for backward compatibility.
     * The calendar design for this line item (optional - may be null for non-calendar products)
     */
    @Deprecated
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calendar_id", foreignKey = @ForeignKey(name = "fk_order_items_calendar"))
    @Ignore
    public UserCalendar calendar;

    /**
     * Product type: PRINT, PDF, etc.
     */
    @NotNull
    @Size(max = 50)
    @Column(name = "product_type", nullable = false, length = 50)
    public String productType = "PRINT";

    /**
     * @deprecated Use description instead. Kept for backward compatibility.
     * Product name/description for display
     */
    @Deprecated
    @Size(max = 255)
    @Column(name = "product_name", length = 255)
    public String productName;

    /**
     * @deprecated Use configuration JSON instead. Kept for backward compatibility.
     * Calendar year (for calendar products)
     */
    @Deprecated
    @Column(name = "calendar_year")
    public Integer calendarYear;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    public Integer quantity = 1;

    @NotNull
    @DecimalMin("0.00")
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    public BigDecimal unitPrice;

    @NotNull
    @DecimalMin("0.00")
    @Column(name = "line_total", nullable = false, precision = 10, scale = 2)
    public BigDecimal lineTotal;

    /**
     * Configuration/customization details as JSON
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @io.smallrye.graphql.api.AdaptWith(villagecompute.calendar.api.graphql.scalars.JsonNodeAdapter.class)
    public JsonNode configuration;

    /**
     * Assets (SVGs) associated with this order item.
     */
    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "order_item_assets",
        joinColumns = @JoinColumn(name = "order_item_id"),
        inverseJoinColumns = @JoinColumn(name = "asset_id")
    )
    @Ignore
    public Set<ItemAsset> assets = new HashSet<>();

    /**
     * Shipment this item belongs to (null until shipped)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", foreignKey = @ForeignKey(name = "fk_order_items_shipment"))
    @Ignore
    public Shipment shipment;

    /**
     * Status of this specific item (for partial fulfillment)
     * PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED, REFUNDED
     */
    @Size(max = 50)
    @Column(name = "item_status", length = 50)
    public String itemStatus = "PENDING";

    // Product type constants
    public static final String TYPE_PRINT = "PRINT";
    public static final String TYPE_PDF = "PDF";

    // Generator type constants
    public static final String GENERATOR_CALENDAR = "calendar";
    public static final String GENERATOR_MAZE = "maze";

    // Item status constants
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_SHIPPED = "SHIPPED";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_REFUNDED = "REFUNDED";

    /**
     * Calculate line total from quantity and unit price
     */
    public void calculateLineTotal() {
        if (unitPrice != null && quantity != null) {
            this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    /**
     * Check if this is a physical product that needs shipping
     */
    public boolean requiresShipping() {
        return TYPE_PRINT.equals(productType);
    }

    /**
     * Check if this is a digital product
     */
    public boolean isDigital() {
        return TYPE_PDF.equals(productType);
    }

    /**
     * Add an asset to this order item.
     */
    public void addAsset(ItemAsset asset) {
        assets.add(asset);
    }

    /**
     * Get an asset by key (e.g., "main", "answer_key").
     */
    public ItemAsset getAsset(String assetKey) {
        return assets.stream()
            .filter(a -> assetKey.equals(a.assetKey))
            .findFirst()
            .orElse(null);
    }

    /**
     * Get the main SVG asset.
     */
    public ItemAsset getMainAsset() {
        return getAsset(ItemAsset.KEY_MAIN);
    }

    /**
     * Get the year from configuration, falling back to deprecated calendarYear field.
     * @return The year, or current year if not set
     */
    @SuppressWarnings("deprecation")
    public int getYear() {
        // First try configuration JSON
        if (configuration != null && configuration.has("year")) {
            return configuration.get("year").asInt();
        }
        // Fall back to deprecated field for backward compatibility
        if (calendarYear != null) {
            return calendarYear;
        }
        // Default to current year
        return java.time.Year.now().getValue();
    }

    /**
     * Set the year in configuration JSON.
     * Also sets deprecated calendarYear field for backward compatibility with older code.
     * @param year The year to set
     */
    @SuppressWarnings("deprecation")
    public void setYear(int year) {
        // Set in configuration JSON (preferred)
        if (configuration == null) {
            try {
                configuration = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
            } catch (Exception e) {
                // Fallback - shouldn't happen
            }
        }
        if (configuration != null && configuration.isObject()) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) configuration).put("year", year);
        }
        // Also set deprecated field for backward compatibility
        this.calendarYear = year;
    }
}
