package villagecompute.calendar.api.graphql.inputs;

import org.eclipse.microprofile.graphql.Input;

/**
 * Input type for adding items to cart
 */
@Input("AddToCartInput")
public class AddToCartInput {
    public String templateId;
    public String templateName;
    public Integer year;
    public Integer quantity;
    public Double unitPrice;
    public String configuration;

    public AddToCartInput() {
    }

    public AddToCartInput(String templateId, String templateName, Integer year, Integer quantity, Double unitPrice, String configuration) {
        this.templateId = templateId;
        this.templateName = templateName;
        this.year = year;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.configuration = configuration;
    }
}
