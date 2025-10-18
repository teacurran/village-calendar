package villagecompute.calendar.api.graphql.types;

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.microprofile.graphql.Type;

/**
 * GraphQL type representing a cart item
 * Stub implementation for future e-commerce functionality
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
    public JsonNode configuration;

    public CartItem() {
    }

    public CartItem(String id, String templateId, String templateName, Integer year, Integer quantity, Double unitPrice, Double lineTotal, JsonNode configuration) {
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
