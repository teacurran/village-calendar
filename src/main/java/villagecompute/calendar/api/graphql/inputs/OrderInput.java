package villagecompute.calendar.api.graphql.inputs;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.graphql.Input;

/**
 * Input type for creating a new calendar order.
 */
@Input("OrderInput")
public class OrderInput {

    @NotNull(message = "Calendar ID is required")
    public String calendarId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    public Integer quantity;

    @NotNull(message = "Shipping address is required")
    public AddressInput shippingAddress;
}
