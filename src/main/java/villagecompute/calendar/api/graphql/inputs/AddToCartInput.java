package villagecompute.calendar.api.graphql.inputs;

import java.util.List;

import org.eclipse.microprofile.graphql.Input;

/**
 * Input type for adding items to cart. Supports both legacy calendar-specific fields and new
 * generic generator fields.
 */
@Input("AddToCartInput")
public class AddToCartInput {
    // --- New generic fields ---

    /** Type of generator: 'calendar', 'maze', etc. */
    public String generatorType;

    /** User-facing description like "2026 Calendar" or "Hard Orthogonal Maze" */
    public String description;

    /** SVG assets to store with this cart item (main, answer_key, etc.) */
    public List<AssetInput> assets;

    // --- Legacy fields (for backwards compatibility) ---

    /** @deprecated Use generatorType and configuration instead */
    public String templateId;

    /** @deprecated Use description instead */
    public String templateName;

    /** @deprecated Include year in configuration JSON instead */
    public Integer year;

    // --- Common fields ---

    public Integer quantity;
    public String productCode; // Product code (e.g., "print", "pdf") - price looked up from backend
    public String configuration; // JSON configuration for the generator

    public AddToCartInput() {}

    // New constructor for generator-based items
    public AddToCartInput(
            String generatorType,
            String description,
            Integer quantity,
            String productCode,
            String configuration,
            List<AssetInput> assets) {
        this.generatorType = generatorType;
        this.description = description;
        this.quantity = quantity;
        this.productCode = productCode;
        this.configuration = configuration;
        this.assets = assets;
    }
}
