package villagecompute.calendar.api.graphql.types;

import java.util.List;

import org.eclipse.microprofile.graphql.Type;

/** GraphQL type representing a product in the catalog */
@Type("Product")
public class Product {
    public String code;
    public String name;
    public String description;
    public Double price;
    public List<String> features;
    public String icon;
    public String badge;
    public Integer displayOrder;

    public Product() {}

    public Product(
            String code,
            String name,
            String description,
            Double price,
            List<String> features,
            String icon,
            String badge,
            Integer displayOrder) {
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
