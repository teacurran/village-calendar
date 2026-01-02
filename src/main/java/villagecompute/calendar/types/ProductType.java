package villagecompute.calendar.types;

import java.util.List;

import org.eclipse.microprofile.graphql.Type;

/** API type representing a product in the catalog. Used by both REST and GraphQL endpoints. */
@Type("Product")
public class ProductType {
    public String code;
    public String name;
    public String description;
    public Double price;
    public List<String> features;
    public String icon;
    public String badge;
    public Integer displayOrder;

    public ProductType() {
    }

    public ProductType(String code, String name, String description, Double price, List<String> features, String icon,
            String badge, Integer displayOrder) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.price = price;
        this.features = features;
        this.icon = icon;
        this.badge = badge;
        this.displayOrder = displayOrder;
    }
}
