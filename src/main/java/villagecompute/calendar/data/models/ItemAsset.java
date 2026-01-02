package villagecompute.calendar.data.models;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * Stores generated SVG content for cart items and orders. Supports multiple assets per item (e.g., main SVG and answer
 * key for mazes).
 */
@Entity
@Table(
        name = "item_assets")
public class ItemAsset extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(
            columnDefinition = "uuid")
    public UUID id;

    /** Key identifying the asset type: 'main', 'answer_key', 'thumbnail', etc. */
    @NotNull @Size(
            max = 50)
    @Column(
            name = "asset_key",
            nullable = false,
            length = 50)
    public String assetKey;

    /** MIME type of the content, typically 'image/svg+xml'. */
    @NotNull @Size(
            max = 50)
    @Column(
            name = "content_type",
            nullable = false,
            length = 50)
    public String contentType = "image/svg+xml";

    /** The actual SVG content. */
    @NotNull @Column(
            name = "svg_content",
            columnDefinition = "TEXT",
            nullable = false)
    public String svgContent;

    /** Width of the asset in inches (e.g., 35 for poster). */
    @Column(
            name = "width_inches",
            precision = 5,
            scale = 2)
    public BigDecimal widthInches;

    /** Height of the asset in inches (e.g., 23 for poster). */
    @Column(
            name = "height_inches",
            precision = 5,
            scale = 2)
    public BigDecimal heightInches;

    @Column(
            nullable = false)
    public OffsetDateTime created = OffsetDateTime.now();

    // Constants for common asset keys
    public static final String KEY_MAIN = "main";
    public static final String KEY_ANSWER_KEY = "answer_key";
    public static final String KEY_THUMBNAIL = "thumbnail";

    /** Creates a new ItemAsset with the given key and SVG content. */
    public static ItemAsset create(String assetKey, String svgContent) {
        ItemAsset asset = new ItemAsset();
        asset.assetKey = assetKey;
        asset.svgContent = svgContent;
        return asset;
    }

    /** Creates a new ItemAsset with dimensions. */
    public static ItemAsset create(String assetKey, String svgContent, BigDecimal widthInches,
            BigDecimal heightInches) {
        ItemAsset asset = create(assetKey, svgContent);
        asset.widthInches = widthInches;
        asset.heightInches = heightInches;
        return asset;
    }
}
