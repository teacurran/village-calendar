package villagecompute.calendar.api.graphql.inputs;

import jakarta.validation.constraints.Size;

import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.Input;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * GraphQL input type for updating an existing calendar. All fields are optional - only provided
 * fields will be updated.
 */
@Input("CalendarUpdateInput")
@Description("Input for updating an existing calendar.")
public class CalendarUpdateInput {

    @Size(max = 255, message = "Calendar name must not exceed 255 characters")
    @Description("Updated calendar name")
    public String name;

    @Description("Updated customization overrides (JSONB)")
    @io.smallrye.graphql.api.AdaptWith(
            villagecompute.calendar.api.graphql.scalars.JsonNodeAdapter.class)
    public JsonNode configuration;

    @Description("Updated public visibility")
    public Boolean isPublic;

    // Default constructor required by SmallRye GraphQL
    public CalendarUpdateInput() {}

    // Constructor for testing
    public CalendarUpdateInput(String name, JsonNode configuration, Boolean isPublic) {
        this.name = name;
        this.configuration = configuration;
        this.isPublic = isPublic;
    }

    /**
     * Check if this update has any actual changes.
     *
     * @return true if at least one field is non-null
     */
    public boolean hasChanges() {
        return name != null || configuration != null || isPublic != null;
    }
}
