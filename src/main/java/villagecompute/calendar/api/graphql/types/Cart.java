package villagecompute.calendar.api.graphql.types;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.graphql.Type;

/**
 * GraphQL type representing a shopping cart Stub implementation for future e-commerce functionality
 */
@Type("Cart")
public class Cart {
    public String id;
    public Double subtotal;
    public Double taxAmount;
    public Double totalAmount;
    public Integer itemCount;
    public List<CartItem> items;

    public Cart() {
        this.id = "";
        this.subtotal = 0.0;
        this.taxAmount = 0.0;
        this.totalAmount = 0.0;
        this.itemCount = 0;
        this.items = new ArrayList<>();
    }

    public Cart(
            String id,
            Double subtotal,
            Double taxAmount,
            Double totalAmount,
            Integer itemCount,
            List<CartItem> items) {
        this.id = id;
        this.subtotal = subtotal;
        this.taxAmount = taxAmount;
        this.totalAmount = totalAmount;
        this.itemCount = itemCount;
        this.items = items;
    }
}
