package villagecompute.calendar.api.graphql.inputs;

import java.math.BigDecimal;

import org.eclipse.microprofile.graphql.Input;

/** Input type for SVG assets when adding to cart */
@Input("AssetInput")
public class AssetInput {
    /** Key identifying the asset type: 'main', 'answer_key', 'thumbnail', etc. */
    public String assetKey;

    /** The SVG content */
    public String svgContent;

    /** Width of the asset in inches (e.g., 35 for poster) */
    public BigDecimal widthInches;

    /** Height of the asset in inches (e.g., 23 for poster) */
    public BigDecimal heightInches;

    public AssetInput() {}

    public AssetInput(String assetKey, String svgContent) {
        this.assetKey = assetKey;
        this.svgContent = svgContent;
    }

    public AssetInput(
            String assetKey, String svgContent, BigDecimal widthInches, BigDecimal heightInches) {
        this.assetKey = assetKey;
        this.svgContent = svgContent;
        this.widthInches = widthInches;
        this.heightInches = heightInches;
    }
}
