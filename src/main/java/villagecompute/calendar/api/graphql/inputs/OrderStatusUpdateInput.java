package villagecompute.calendar.api.graphql.inputs;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.graphql.Input;

/**
 * Input type for updating order status (admin only).
 */
@Input("OrderStatusUpdateInput")
public class OrderStatusUpdateInput {

    @NotNull(message = "Order ID is required")
    public String orderId;

    @NotNull(message = "Status is required")
    @Size(max = 50, message = "Status must not exceed 50 characters")
    public String status;

    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    public String notes;
}
