package villagecompute.calendar.api.graphql.inputs;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.Input;

/**
 * GraphQL input type for creating or updating a calendar template. Admin-only operations. Validated using Bean
 * Validation annotations.
 *
 * <p>
 * Note: configuration is accepted as a JSON string and parsed in the service layer.
 */
@Input("TemplateInput")
@Description("Input for creating or updating a calendar template (admin only).")
public class TemplateInput {

    @NotNull(message = "Template name is required") @NotBlank(message = "Template name cannot be blank")
    @Size(max = 255, message = "Template name must not exceed 255 characters")
    @Description("Template name")
    public String name;

    @Description("Template description")
    public String description;

    @NotNull(message = "Configuration is required") @NotBlank(message = "Configuration cannot be blank")
    @Description("JSON string containing template configuration")
    public String configuration;

    @Description("URL to thumbnail image")
    @Size(max = 500, message = "Thumbnail URL must not exceed 500 characters")
    public String thumbnailUrl;

    @Description("Whether template is active")
    public Boolean isActive;

    @Description("Whether template is featured")
    public Boolean isFeatured;

    @Description("Display order")
    @Min(value = 0, message = "Display order must be 0 or greater")
    public Integer displayOrder;

    @Description("SVG preview rendering")
    public String previewSvg;

    // Default constructor required by SmallRye GraphQL
    public TemplateInput() {
    }

    // Constructor for testing
    public TemplateInput(String name, String description, String configuration, String thumbnailUrl, Boolean isActive,
            Boolean isFeatured, Integer displayOrder, String previewSvg) {
        this.name = name;
        this.description = description;
        this.configuration = configuration;
        this.thumbnailUrl = thumbnailUrl;
        this.isActive = isActive;
        this.isFeatured = isFeatured;
        this.displayOrder = displayOrder;
        this.previewSvg = previewSvg;
    }
}
