package villagecompute.calendar.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Service for managing product catalog and pricing. This is the single source of truth for product codes and prices.
 */
@ApplicationScoped
public class ProductService {

    /** Product code for printed calendar poster */
    public static final String PRODUCT_CODE_PRINT = "print";

    /** Product code for digital PDF download */
    public static final String PRODUCT_CODE_PDF = "pdf";

    /** Product definition with all attributes. Use {@link #builder()} to construct instances. */
    public static class Product {
        public final String code;
        public final String name;
        public final String description;
        public final BigDecimal price;
        public final List<String> features;
        public final String icon;
        public final String badge;
        public final int displayOrder;

        private Product(Builder b) {
            this.code = b.code;
            this.name = b.name;
            this.description = b.description;
            this.price = b.price;
            this.features = b.features;
            this.icon = b.icon;
            this.badge = b.badge;
            this.displayOrder = b.displayOrder;
        }

        /** Create a new {@link Builder} for fluent construction of a {@link Product}. */
        public static Builder builder() {
            return new Builder();
        }

        /** Fluent builder for {@link Product}. Avoids the multi-parameter constructor (sonar:S107). */
        public static class Builder {
            private String code;
            private String name;
            private String description;
            private BigDecimal price;
            private List<String> features;
            private String icon;
            private String badge;
            private int displayOrder;

            public Builder code(String value) {
                this.code = value;
                return this;
            }

            public Builder name(String value) {
                this.name = value;
                return this;
            }

            public Builder description(String value) {
                this.description = value;
                return this;
            }

            public Builder price(BigDecimal value) {
                this.price = value;
                return this;
            }

            public Builder features(List<String> value) {
                this.features = value;
                return this;
            }

            public Builder icon(String value) {
                this.icon = value;
                return this;
            }

            public Builder badge(String value) {
                this.badge = value;
                return this;
            }

            public Builder displayOrder(int value) {
                this.displayOrder = value;
                return this;
            }

            public Product build() {
                return new Product(this);
            }
        }
    }

    // Product catalog - single source of truth for pricing
    private static final Map<String, Product> PRODUCTS = Map.of(PRODUCT_CODE_PRINT,
            Product.builder().code(PRODUCT_CODE_PRINT).name("Printed 35\" x 23\" Poster")
                    .description("Beautiful printed calendar shipped directly to your door.")
                    .price(new BigDecimal("25.00"))
                    .features(List.of("Premium quality paper stock", "Vibrant, long-lasting colors",
                            "PDF download included free", "Ships within 3-5 business days"))
                    .icon("pi-print").badge("Most Popular").displayOrder(1).build(),
            PRODUCT_CODE_PDF,
            Product.builder().code(PRODUCT_CODE_PDF).name("Digital PDF Download")
                    .description("High-resolution PDF file ready for printing at home or any print shop.")
                    .price(new BigDecimal("5.00"))
                    .features(List.of("Instant download after purchase", "Unlimited personal prints"))
                    .icon("pi-file-pdf").badge(null).displayOrder(2).build());

    /** Get all available products, sorted by display order */
    public List<Product> getAllProducts() {
        return PRODUCTS.values().stream().sorted((a, b) -> Integer.compare(a.displayOrder, b.displayOrder)).toList();
    }

    /** Get a product by its code */
    public Optional<Product> getProduct(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(PRODUCTS.get(code));
    }

    /**
     * Get the price for a product code
     *
     * @throws IllegalArgumentException
     *             if product code is invalid
     */
    public BigDecimal getPrice(String productCode) {
        if (productCode == null) {
            throw new IllegalArgumentException("Invalid product code: null");
        }
        Product product = PRODUCTS.get(productCode);
        if (product == null) {
            throw new IllegalArgumentException("Invalid product code: " + productCode);
        }
        return product.price;
    }

    /** Validate that a product code exists */
    public boolean isValidProductCode(String code) {
        if (code == null) {
            return false;
        }
        return PRODUCTS.containsKey(code);
    }

    /** Get the default product code */
    public String getDefaultProductCode() {
        return PRODUCT_CODE_PRINT;
    }
}
