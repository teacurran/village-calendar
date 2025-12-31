package villagecompute.calendar.api.graphql;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.graphql.*;

import villagecompute.calendar.api.types.Product;
import villagecompute.calendar.services.ProductService;

/** GraphQL API for product catalog */
@GraphQLApi
@ApplicationScoped
public class ProductGraphQL {

    @Inject ProductService productService;

    /** Get all available products */
    @Query("products")
    @Description("Get all available products with pricing")
    public List<Product> getProducts() {
        return productService.getAllProducts().stream().map(this::toGraphQLProduct).toList();
    }

    /** Get a single product by code */
    @Query("product")
    @Description("Get a product by its code")
    public Product getProduct(@Name("code") String code) {
        return productService.getProduct(code).map(this::toGraphQLProduct).orElse(null);
    }

    /** Get the default product code */
    @Query("defaultProductCode")
    @Description("Get the default product code for new carts")
    public String getDefaultProductCode() {
        return productService.getDefaultProductCode();
    }

    private Product toGraphQLProduct(ProductService.Product p) {
        return new Product(
                p.code,
                p.name,
                p.description,
                p.price.doubleValue(),
                p.features,
                p.icon,
                p.badge,
                p.displayOrder);
    }
}
