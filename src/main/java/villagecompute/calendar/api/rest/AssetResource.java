package villagecompute.calendar.api.rest;

import java.util.UUID;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;

import villagecompute.calendar.data.models.CartItem;
import villagecompute.calendar.data.models.ItemAsset;
import villagecompute.calendar.util.MimeTypes;

/**
 * REST resource for accessing asset content (SVGs stored in item_assets table) and cart item thumbnails.
 */
@Path("/")
public class AssetResource {

    private static final Logger LOG = Logger.getLogger(AssetResource.class);

    private static final String SVG_CACHE_CONTROL = "public, max-age=31536000"; // 1 year

    /** Get the SVG content of an asset by its ID */
    @GET
    @Path("/assets/{assetId}")
    @Produces(MimeTypes.IMAGE_SVG)
    public Response getAssetContent(@PathParam("assetId") String assetId) {
        try {
            UUID id = UUID.fromString(assetId);
            ItemAsset asset = ItemAsset.findById(id);

            if (asset == null) {
                LOG.warnf("Asset not found: %s", assetId);
                return Response.status(Response.Status.NOT_FOUND).entity("Asset not found").build();
            }

            return Response.ok(asset.svgContent)
                    .header(MimeTypes.HEADER_CONTENT_TYPE,
                            asset.contentType != null ? asset.contentType : MimeTypes.IMAGE_SVG)
                    .header(MimeTypes.HEADER_CACHE_CONTROL, SVG_CACHE_CONTROL) // Cache for 1 year (assets are
                                                                               // immutable)
                    .build();

        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid asset ID format: %s", assetId);
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid asset ID format").build();
        } catch (Exception e) {
            LOG.errorf(e, "Error fetching asset %s", assetId);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error fetching asset").build();
        }
    }

    /**
     * Get thumbnail SVG for a cart item. Works uniformly for both calendars (with stored SVG) and mazes (with assets).
     */
    @GET
    @Path("/cart-items/{itemId}/thumbnail.svg")
    @Produces("image/svg+xml")
    public Response getCartItemThumbnail(@PathParam("itemId") String itemId) {
        try {
            UUID id = UUID.fromString(itemId);
            CartItem cartItem = CartItem.findById(id);

            if (cartItem == null) {
                LOG.warnf("Cart item not found: %s", itemId);
                return Response.status(Response.Status.NOT_FOUND).entity("Cart item not found").build();
            }

            // Priority 1: Check for assets (mazes and new-style items)
            String svg = extractSvgFromAssets(cartItem);
            if (svg == null) {
                // Priority 2: Check for SVG in configuration (legacy calendars)
                svg = extractSvgFromConfiguration(cartItem.configuration);
            }

            if (svg != null) {
                return svgResponse(svg, MimeTypes.IMAGE_SVG);
            }

            // No SVG available
            LOG.warnf("No SVG content available for cart item: %s", itemId);
            return Response.status(Response.Status.NOT_FOUND).entity("No thumbnail available for this cart item")
                    .build();

        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid cart item ID format: %s", itemId);
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid cart item ID format").build();
        } catch (Exception e) {
            LOG.errorf(e, "Error fetching cart item thumbnail %s", itemId);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error fetching thumbnail").build();
        }
    }

    /**
     * Returns the main asset's SVG content for the given cart item, or {@code null} if none is available.
     */
    private String extractSvgFromAssets(CartItem cartItem) {
        if (cartItem.assets == null || cartItem.assets.isEmpty()) {
            return null;
        }
        ItemAsset mainAsset = cartItem.getMainAsset();
        if (mainAsset == null || mainAsset.svgContent == null) {
            return null;
        }
        return mainAsset.svgContent;
    }

    /**
     * Extracts an SVG value from a legacy JSON configuration string, or returns {@code null} when one cannot be found.
     */
    private String extractSvgFromConfiguration(String configuration) {
        if (configuration == null || !configuration.contains("\"svgContent\"")) {
            return null;
        }
        final String marker = "\"svgContent\":\"";
        int start = configuration.indexOf(marker);
        if (start < 0) {
            return null;
        }
        start += marker.length();
        int end = findUnescapedQuote(configuration, start);
        if (end <= start) {
            return null;
        }
        return decodeJsonStringValue(configuration.substring(start, end));
    }

    /**
     * Finds the index of the next unescaped double-quote starting at {@code from}, or -1 if not found.
     */
    private int findUnescapedQuote(String source, int from) {
        int end = source.indexOf('"', from);
        while (end > 0 && source.charAt(end - 1) == '\\') {
            end = source.indexOf('"', end + 1);
        }
        return end;
    }

    /**
     * Decodes a JSON-escaped string value (handles escaped quotes, newlines, and backslashes).
     */
    private String decodeJsonStringValue(String value) {
        return value.replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\");
    }

    /**
     * Builds a successful SVG response with the standard caching headers.
     */
    private Response svgResponse(String svg, String contentType) {
        return Response.ok(svg).header(MimeTypes.HEADER_CONTENT_TYPE, contentType)
                .header(MimeTypes.HEADER_CACHE_CONTROL, SVG_CACHE_CONTROL).build();
    }
}
