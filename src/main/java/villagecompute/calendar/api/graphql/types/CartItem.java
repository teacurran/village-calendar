package villagecompute.calendar.api.graphql.types;

import org.eclipse.microprofile.graphql.Type;

import java.util.List;

/**
 * GraphQL type representing a cart item.
 * Supports both legacy calendar-specific fields and new generic generator fields.
 */
@Type("CartItem")
public class CartItem {
    public String id;

    // --- New generic fields ---
    public String generatorType;
    public String description;
    public List<Asset> assets;

    // --- Common fields ---
    public Integer quantity;
    public Double unitPrice;
    public Double lineTotal;
    public String productCode;
    public String configuration;

    // --- Legacy fields (deprecated, kept for backward compatibility) ---
    @Deprecated
    public String templateId;
    @Deprecated
    public String templateName;
    @Deprecated
    public Integer year;

    public CartItem() {
    }

    // Legacy constructor for backward compatibility
    public CartItem(String id, String templateId, String templateName, Integer year, Integer quantity, Double unitPrice, Double lineTotal, String productCode, String configuration) {
        this.id = id;
        this.templateId = templateId;
        this.templateName = templateName;
        this.year = year;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineTotal = lineTotal;
        this.productCode = productCode;
        this.configuration = configuration;
    }

    // New constructor for generator-based items
    public CartItem(String id, String generatorType, String description, Integer quantity, Double unitPrice, Double lineTotal, String productCode, String configuration, List<Asset> assets) {
        this.id = id;
        this.generatorType = generatorType;
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineTotal = lineTotal;
        this.productCode = productCode;
        this.configuration = configuration;
        this.assets = assets;
    }

    /**
     * Create a CartItem from the entity, supporting both legacy and new formats.
     */
    public static CartItem fromEntity(villagecompute.calendar.data.models.CartItem entity) {
        if (entity == null) return null;

        CartItem item = new CartItem();
        item.id = entity.id.toString();
        item.quantity = entity.quantity;
        item.unitPrice = entity.unitPrice != null ? entity.unitPrice.doubleValue() : null;
        item.lineTotal = entity.getLineTotal() != null ? entity.getLineTotal().doubleValue() : null;
        item.productCode = entity.productCode;
        item.configuration = entity.configuration;

        // New fields
        item.generatorType = entity.generatorType;
        item.description = entity.description;

        // Convert assets
        if (entity.assets != null && !entity.assets.isEmpty()) {
            item.assets = entity.assets.stream()
                .map(Asset::fromEntity)
                .toList();
        }

        // Legacy fields for backward compatibility
        item.templateId = entity.templateId;
        item.templateName = entity.templateName;
        item.year = entity.year;

        return item;
    }
}
