package villagecompute.calendar.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Service for managing product catalog and pricing. This is the single source of truth for product
 * codes and prices.
 */
@ApplicationScoped
public class ProductService {

    /** Product definition with all attributes */
    public static class Product {
        public final String code;
        public final String name;
        public final String description;
        public final BigDecimal price;
        public final List<String> features;
        public final String icon;
        public final String badge;
        public final int displayOrder;

        public Product(
                String code,
                String name,
                String description,
                BigDecimal price,
                List<String> features,
                String icon,
                String badge,
                int displayOrder) {
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

    // Product catalog - single source of truth for pricing
    private static final Map<String, Product> PRODUCTS =
            Map.of(
                    "print",
                            new Product(
                                    "print",
                                    "Printed 35\" x 23\" Poster",
                                    "Beautiful printed calendar shipped directly to your door.",
                                    new BigDecimal("25.00"),
                                    List.of(
                                            "Premium quality paper stock",
                                            "Vibrant, long-lasting colors",
                                            "PDF download included free",
                                            "Ships within 3-5 business days"),
                                    "pi-print",
                                    "Most Popular",
                                    1),
                    "pdf",
                            new Product(
                                    "pdf",
                                    "Digital PDF Download",
                                    "High-resolution PDF file ready for printing at home or any"
                                            + " print shop.",
                                    new BigDecimal("5.00"),
                                    List.of(
                                            "Instant download after purchase",
                                            "Unlimited personal prints"),
                                    "pi-file-pdf",
                                    null,
                                    2));

    /** Get all available products, sorted by display order */
    public List<Product> getAllProducts() {
        return PRODUCTS.values().stream()
                .sorted((a, b) -> Integer.compare(a.displayOrder, b.displayOrder))
                .toList();
    }

    /** Get a product by its code */
    public Optional<Product> getProduct(String code) {
        return Optional.ofNullable(PRODUCTS.get(code));
    }

    /**
     * Get the price for a product code
     *
     * @throws IllegalArgumentException if product code is invalid
     */
    public BigDecimal getPrice(String productCode) {
        Product product = PRODUCTS.get(productCode);
        if (product == null) {
            throw new IllegalArgumentException("Invalid product code: " + productCode);
        }
        return product.price;
    }

    /** Validate that a product code exists */
    public boolean isValidProductCode(String code) {
        return PRODUCTS.containsKey(code);
    }

    /** Get the default product code */
    public String getDefaultProductCode() {
        return "print";
    }
}
