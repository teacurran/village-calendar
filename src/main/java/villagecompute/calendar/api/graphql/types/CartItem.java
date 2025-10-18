package villagecompute.calendar.api.graphql.types;

import org.eclipse.microprofile.graphql.Type;

/**
 * GraphQL type representing a cart item
 * Configuration is stored as JSON string
 */
@Type("CartItem")
public class CartItem {
    public String id;
    public String templateId;
    public String templateName;
    public Integer year;
    public Integer quantity;
    public Double unitPrice;
    public Double lineTotal;
    public String configuration;

    public CartItem() {
    }

    public CartItem(String id, String templateId, String templateName, Integer year, Integer quantity, Double unitPrice, Double lineTotal, String configuration) {
        this.id = id;
        this.templateId = templateId;
        this.templateName = templateName;
        this.year = year;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineTotal = lineTotal;
        this.configuration = configuration;
    }
}
