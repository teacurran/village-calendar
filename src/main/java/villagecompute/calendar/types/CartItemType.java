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
        // intentionally empty — default constructor required by JSON/GraphQL deserialization
    }

    /** Create a CartItemType from the entity. */
    public static CartItemType fromEntity(villagecompute.calendar.data.models.CartItem entity) {
        if (entity == null) {
            return null;
        }

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
