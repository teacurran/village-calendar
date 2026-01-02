package villagecompute.calendar.types;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.graphql.Type;

/** API type representing a shopping cart. Used by both REST and GraphQL endpoints. */
@Type("Cart")
public class CartType {
    public String id;
    public Double subtotal;
    public Double taxAmount;
    public Double totalAmount;
    public Integer itemCount;
    public List<CartItemType> items;

    public CartType() {
        this.id = "";
        this.subtotal = 0.0;
        this.taxAmount = 0.0;
        this.totalAmount = 0.0;
        this.itemCount = 0;
        this.items = new ArrayList<>();
    }

    public CartType(String id, Double subtotal, Double taxAmount, Double totalAmount, Integer itemCount,
            List<CartItemType> items) {
        this.id = id;
        this.subtotal = subtotal;
        this.taxAmount = taxAmount;
        this.totalAmount = totalAmount;
        this.itemCount = itemCount;
        this.items = items;
    }
}
