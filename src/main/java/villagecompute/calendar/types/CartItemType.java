package villagecompute.calendar.types;

import java.util.List;

import org.eclipse.microprofile.graphql.Type;

/** API type representing a cart item. Used by both REST and GraphQL endpoints. */
@Type("CartItem")
public class CartItemType {
    public String id;
    public String generatorType;
    public String description;
    public List<AssetType> assets;
    public Integer quantity;
    public Double unitPrice;
    public Double lineTotal;
    public String productCode;
    public String configuration;

    public CartItemType() {
    }

    public CartItemType(String id, String generatorType, String description, Integer quantity, Double unitPrice,
            Double lineTotal, String productCode, String configuration, List<AssetType> assets) {
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

    /** Create a CartItemType from the entity. */
    public static CartItemType fromEntity(villagecompute.calendar.data.models.CartItem entity) {
        if (entity == null)
            return null;

        CartItemType item = new CartItemType();
        item.id = entity.id.toString();
        item.quantity = entity.quantity;
        item.unitPrice = entity.unitPrice != null ? entity.unitPrice.doubleValue() : null;
        item.lineTotal = entity.getLineTotal() != null ? entity.getLineTotal().doubleValue() : null;
        item.productCode = entity.productCode;
        item.configuration = entity.configuration;
        item.generatorType = entity.generatorType;
        item.description = entity.description;

        // Convert assets
        if (entity.assets != null && !entity.assets.isEmpty()) {
            item.assets = entity.assets.stream().map(AssetType::fromEntity).toList();
        }

        return item;
    }
}
