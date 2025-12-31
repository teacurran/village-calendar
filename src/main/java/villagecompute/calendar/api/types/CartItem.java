package villagecompute.calendar.api.types;

import java.util.List;

import org.eclipse.microprofile.graphql.Type;

/** API type representing a cart item. Used by both REST and GraphQL endpoints. */
@Type("CartItem")
public class CartItem {
    public String id;
    public String generatorType;
    public String description;
    public List<Asset> assets;
    public Integer quantity;
    public Double unitPrice;
    public Double lineTotal;
    public String productCode;
    public String configuration;

    public CartItem() {
    }

    public CartItem(String id, String generatorType, String description, Integer quantity, Double unitPrice,
            Double lineTotal, String productCode, String configuration, List<Asset> assets) {
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

    /** Create a CartItem from the entity. */
    public static CartItem fromEntity(villagecompute.calendar.data.models.CartItem entity) {
        if (entity == null)
            return null;

        CartItem item = new CartItem();
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
            item.assets = entity.assets.stream().map(Asset::fromEntity).toList();
        }

        return item;
    }
}
