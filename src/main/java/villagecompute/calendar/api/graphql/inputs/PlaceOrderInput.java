package villagecompute.calendar.api.graphql.inputs;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.eclipse.microprofile.graphql.Input;

import villagecompute.calendar.data.models.enums.ProductType;

/**
 * Input type for placing a new order. Alternative to createOrder mutation with more explicit input structure.
 */
@Input("PlaceOrderInput")
public class PlaceOrderInput {

    @NotNull(message = "Calendar ID is required") public String calendarId;

    @NotNull(message = "Product type is required") public ProductType productType;

    @NotNull(message = "Quantity is required") @Min(value = 1, message = "Quantity must be at least 1")
    public Integer quantity;

    @NotNull(message = "Shipping address is required") public AddressInput shippingAddress;
}
