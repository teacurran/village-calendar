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

    @NotNull(
            message = "Template name is required")
    @NotBlank(
            message = "Template name cannot be blank")
    @Size(
            max = 255,
            message = "Template name must not exceed 255 characters")
    @Description("Template name")
    public String name;

    @Description("Template description")
    public String description;

    @NotNull(
            message = "Configuration is required")
    @NotBlank(
            message = "Configuration cannot be blank")
    @Description("JSON string containing template configuration")
    public String configuration;

    @Description("URL to thumbnail image")
    @Size(
            max = 500,
            message = "Thumbnail URL must not exceed 500 characters")
    public String thumbnailUrl;

    @Description("Whether template is active")
    public Boolean isActive;

    @Description("Whether template is featured")
    public Boolean isFeatured;

    @Description("Display order")
    @Min(
            value = 0,
            message = "Display order must be 0 or greater")
    public Integer displayOrder;

    @Description("SVG preview rendering")
    public String previewSvg;

    // Default constructor required by SmallRye GraphQL
    public TemplateInput() {
        // intentionally empty — default constructor required by SmallRye GraphQL deserialization
    }

    /**
     * Builder for TemplateInput to avoid long constructor parameter lists. Use {@link #builder()} to start a new
     * builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final TemplateInput input = new TemplateInput();

        public Builder name(String name) {
            input.name = name;
            return this;
        }

        public Builder description(String description) {
            input.description = description;
            return this;
        }

        public Builder configuration(String configuration) {
            input.configuration = configuration;
            return this;
        }

        public Builder thumbnailUrl(String thumbnailUrl) {
            input.thumbnailUrl = thumbnailUrl;
            return this;
        }

        public Builder isActive(Boolean isActive) {
            input.isActive = isActive;
            return this;
        }

        public Builder isFeatured(Boolean isFeatured) {
            input.isFeatured = isFeatured;
            return this;
        }

        public Builder displayOrder(Integer displayOrder) {
            input.displayOrder = displayOrder;
            return this;
        }

        public Builder previewSvg(String previewSvg) {
            input.previewSvg = previewSvg;
            return this;
        }

        public TemplateInput build() {
            return input;
        }
    }
}
