package villagecompute.calendar.data.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.graphql.Ignore;

import java.math.BigDecimal;

/**
 * Item in a shopping cart
 */
@Entity
@Table(
    name = "cart_items",
    indexes = {
        @Index(name = "idx_cart_items_cart", columnList = "cart_id")
    }
)
public class CartItem extends DefaultPanacheEntityWithTimestamps {

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "cart_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cart_items_cart"))
    @Ignore
    public Cart cart;

    @NotNull
    @Size(min = 1, max = 255)
    @Column(name = "template_id", nullable = false, length = 255)
    public String templateId;

    @NotNull
    @Size(min = 1, max = 255)
    @Column(name = "template_name", nullable = false, length = 255)
    public String templateName;

    @NotNull
    @Min(2000)
    @Column(name = "calendar_year", nullable = false)
    public Integer year;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    public Integer quantity = 1;

    @NotNull
    @DecimalMin("0.01")
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    public BigDecimal unitPrice;

    /**
     * Product code (e.g., "print", "pdf")
     */
    @Size(max = 50)
    @Column(name = "product_code", length = 50)
    public String productCode;

    /**
     * JSON configuration for the calendar
     */
    @Column(name = "configuration", columnDefinition = "TEXT")
    public String configuration;

    /**
     * Calculate line total (quantity * unit price)
     */
    public BigDecimal getLineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
