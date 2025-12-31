package villagecompute.calendar.api.graphql.inputs;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.Input;

/**
 * Input type for shipping address. Matches AddressInput type defined in GraphQL schema. Will be stored as JSONB in the
 * database.
 */
@Input("AddressInput")
@Description("Shipping address for order delivery")
public class AddressInput {

    @NotNull(message = "Street is required") @Size(max = 255, message = "Street must not exceed 255 characters")
    @Description("Street address")
    public String street;

    @Size(max = 255, message = "Street2 must not exceed 255 characters")
    @Description("Apartment, suite, etc. (optional)")
    public String street2;

    @NotNull(message = "City is required") @Size(max = 100, message = "City must not exceed 100 characters")
    @Description("City")
    public String city;

    @NotNull(message = "State is required") @Size(max = 50, message = "State must not exceed 50 characters")
    @Description("State/province code (e.g., \"TN\", \"CA\")")
    public String state;

    @NotNull(message = "Postal code is required") @Size(max = 20, message = "Postal code must not exceed 20 characters")
    @Description("Postal/ZIP code")
    public String postalCode;

    @NotNull(message = "Country is required") @Size(max = 50, message = "Country must not exceed 50 characters")
    @Description("Country code (ISO 3166-1 alpha-2, e.g., \"US\")")
    public String country;
}
