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
        // intentionally empty — default constructor required by JSON/GraphQL deserialization
    }

    /** Returns a new builder for constructing {@link ProductType} instances. */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link ProductType}. */
    public static final class Builder {
        private String code;
        private String name;
        private String description;
        private Double price;
        private List<String> features;
        private String icon;
        private String badge;
        private Integer displayOrder;

        private Builder() {
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder price(Double price) {
            this.price = price;
            return this;
        }

        public Builder features(List<String> features) {
            this.features = features;
            return this;
        }

        public Builder icon(String icon) {
            this.icon = icon;
            return this;
        }

        public Builder badge(String badge) {
            this.badge = badge;
            return this;
        }

        public Builder displayOrder(Integer displayOrder) {
            this.displayOrder = displayOrder;
            return this;
        }

        public ProductType build() {
            ProductType product = new ProductType();
            product.code = this.code;
            product.name = this.name;
            product.description = this.description;
            product.price = this.price;
            product.features = this.features;
            product.icon = this.icon;
            product.badge = this.badge;
            product.displayOrder = this.displayOrder;
            return product;
        }
    }
}
