package villagecompute.calendar.api.graphql.inputs;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.graphql.Input;

/**
 * Input type for shipping address.
 * Will be stored as JSONB in the database.
 */
@Input("ShippingAddressInput")
public class ShippingAddressInput {

    @NotNull(message = "Name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    public String name;

    @NotNull(message = "Address line 1 is required")
    @Size(max = 255, message = "Address line 1 must not exceed 255 characters")
    public String line1;

    @Size(max = 255, message = "Address line 2 must not exceed 255 characters")
    public String line2;

    @NotNull(message = "City is required")
    @Size(max = 100, message = "City must not exceed 100 characters")
    public String city;

    @NotNull(message = "State is required")
    @Size(max = 50, message = "State must not exceed 50 characters")
    public String state;

    @NotNull(message = "Postal code is required")
    @Size(max = 20, message = "Postal code must not exceed 20 characters")
    public String postalCode;

    @NotNull(message = "Country is required")
    @Size(max = 50, message = "Country must not exceed 50 characters")
    public String country;
}
