package villagecompute.calendar.data.models;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.eclipse.microprofile.graphql.Ignore;

/**
 * Item in a shopping cart. Supports multiple generator types (calendar, maze, etc.) with frozen SVG assets.
 */
@Entity
@Table(
        name = "cart_items",
        indexes = {@Index(
                name = "idx_cart_items_cart",
                columnList = "cart_id")})
public class CartItem extends DefaultPanacheEntityWithTimestamps {

    @NotNull @ManyToOne(
            optional = false)
    @JoinColumn(
            name = "cart_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_cart_items_cart"))
    @Ignore
    public Cart cart;

    /** Type of generator: 'calendar', 'maze', etc. */
    @Size(
            max = 50)
    @Column(
            name = "generator_type",
            length = 50)
    public String generatorType;

    /** User-facing description like "2026 Calendar" or "Hard Orthogonal Maze" */
    @Size(
            max = 500)
    @Column(
            name = "description",
            length = 500)
    public String description;

    @NotNull @Min(1)
    @Column(
            nullable = false)
    public Integer quantity = 1;

    @NotNull @DecimalMin("0.01")
    @Column(
            name = "unit_price",
            nullable = false,
            precision = 10,
            scale = 2)
    public BigDecimal unitPrice;

    /** Product code (e.g., "print", "pdf") */
    @Size(
            max = 50)
    @Column(
            name = "product_code",
            length = 50)
    public String productCode;

    /**
     * JSON configuration for the generator (includes year for calendars, size/difficulty for mazes, etc.)
     */
    @Column(
            name = "configuration",
            columnDefinition = "TEXT")
    public String configuration;

    /** Assets (SVGs) associated with this cart item. */
    @ManyToMany(
            cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "cart_item_assets",
            joinColumns = @JoinColumn(
                    name = "cart_item_id"),
            inverseJoinColumns = @JoinColumn(
                    name = "asset_id"))
    @Ignore
    public Set<ItemAsset> assets = new HashSet<>();

    // Generator type constants
    public static final String GENERATOR_CALENDAR = "calendar";
    public static final String GENERATOR_MAZE = "maze";

    /** Calculate line total (quantity * unit price) */
    public BigDecimal getLineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    /** Add an asset to this cart item. */
    public void addAsset(ItemAsset asset) {
        assets.add(asset);
    }

    /** Get an asset by key (e.g., "main", "answer_key"). */
    public ItemAsset getAsset(String assetKey) {
        return assets.stream().filter(a -> assetKey.equals(a.assetKey)).findFirst().orElse(null);
    }

    /** Get the main SVG asset. */
    public ItemAsset getMainAsset() {
        return getAsset(ItemAsset.KEY_MAIN);
    }
}
