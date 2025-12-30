package villagecompute.calendar.api.graphql.inputs;

import jakarta.validation.constraints.*;

import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.Input;

import com.fasterxml.jackson.databind.JsonNode;

/** GraphQL input type for creating a new calendar. Validated using Bean Validation annotations. */
@Input("CalendarInput")
@Description("Input for creating a new calendar.")
public class CalendarInput {

    @NotNull(message = "Calendar name is required") @NotBlank(message = "Calendar name cannot be blank")
    @Size(max = 255, message = "Calendar name must not exceed 255 characters")
    @Description("Calendar name")
    public String name;

    @NotNull(message = "Year is required") @Min(value = 2020, message = "Year must be 2020 or later")
    @Max(value = 2099, message = "Year must be 2099 or earlier")
    @Description("Calendar year")
    public Integer year;

    @NotNull(message = "Template ID is required") @Description("Template to base calendar on")
    public String templateId;

    @Description("User customization overrides (JSONB)")
    @io.smallrye.graphql.api.AdaptWith(
            villagecompute.calendar.api.graphql.scalars.JsonNodeAdapter.class)
    public JsonNode configuration;

    @Description("Whether calendar is publicly visible")
    public Boolean isPublic;

    // Default constructor required by SmallRye GraphQL
    public CalendarInput() {}

    // Constructor for testing
    public CalendarInput(
            String name,
            Integer year,
            String templateId,
            JsonNode configuration,
            Boolean isPublic) {
        this.name = name;
        this.year = year;
        this.templateId = templateId;
        this.configuration = configuration;
        this.isPublic = isPublic;
    }
}
