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

/**
 * Entity representing a line item within a calendar order.
 * Each order can have multiple items (prints, PDFs, etc.)
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

    @Version
    @Column(name = "version", nullable = false)
    public Long version;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_items_order"))
    @Ignore
    public CalendarOrder order;

    /**
     * The calendar design for this line item (optional - may be null for non-calendar products)
     */
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
     * Product name/description for display
     */
    @Size(max = 255)
    @Column(name = "product_name", length = 255)
    public String productName;

    /**
     * Calendar year (for calendar products)
     */
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
}
