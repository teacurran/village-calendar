package villagecompute.calendar.api.types;

import java.math.BigDecimal;

import org.eclipse.microprofile.graphql.Type;

/**
 * API type representing an asset (SVG) associated with a cart or order item. Used by both REST and
 * GraphQL endpoints.
 */
@Type("Asset")
public class Asset {
    public String id;
    public String assetKey;
    public String contentType;
    public BigDecimal widthInches;
    public BigDecimal heightInches;

    // Note: svgContent is intentionally not exposed in API responses to avoid large payloads
    // Use a dedicated query/mutation if SVG content is needed

    public Asset() {}

    public Asset(
            String id,
            String assetKey,
            String contentType,
            BigDecimal widthInches,
            BigDecimal heightInches) {
        this.id = id;
        this.assetKey = assetKey;
        this.contentType = contentType;
        this.widthInches = widthInches;
        this.heightInches = heightInches;
    }

    public static Asset fromEntity(villagecompute.calendar.data.models.ItemAsset entity) {
        if (entity == null) return null;
        return new Asset(
                entity.id.toString(),
                entity.assetKey,
                entity.contentType,
                entity.widthInches,
                entity.heightInches);
    }
}
