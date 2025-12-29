package villagecompute.calendar.api.rest;

import java.util.UUID;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;

import villagecompute.calendar.data.models.CartItem;
import villagecompute.calendar.data.models.ItemAsset;

/**
 * REST resource for accessing asset content (SVGs stored in item_assets table) and cart item
 * thumbnails.
 */
@Path("/api")
public class AssetResource {

    private static final Logger LOG = Logger.getLogger(AssetResource.class);

    /** Get the SVG content of an asset by its ID */
    @GET
    @Path("/assets/{assetId}")
    @Produces("image/svg+xml")
    public Response getAssetContent(@PathParam("assetId") String assetId) {
        try {
            UUID id = UUID.fromString(assetId);
            ItemAsset asset = ItemAsset.findById(id);

            if (asset == null) {
                LOG.warnf("Asset not found: %s", assetId);
                return Response.status(Response.Status.NOT_FOUND).entity("Asset not found").build();
            }

            return Response.ok(asset.svgContent)
                    .header(
                            "Content-Type",
                            asset.contentType != null ? asset.contentType : "image/svg+xml")
                    .header(
                            "Cache-Control",
                            "public, max-age=31536000") // Cache for 1 year (assets are immutable)
                    .build();

        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid asset ID format: %s", assetId);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Invalid asset ID format")
                    .build();
        } catch (Exception e) {
            LOG.errorf(e, "Error fetching asset %s", assetId);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error fetching asset")
                    .build();
        }
    }

    /**
     * Get thumbnail SVG for a cart item. Works uniformly for both calendars (with stored SVG) and
     * mazes (with assets).
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
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Cart item not found")
                        .build();
            }

            // Priority 1: Check for assets (mazes and new-style items)
            if (cartItem.assets != null && !cartItem.assets.isEmpty()) {
                ItemAsset mainAsset = cartItem.getMainAsset();
                if (mainAsset != null && mainAsset.svgContent != null) {
                    return Response.ok(mainAsset.svgContent)
                            .header("Content-Type", "image/svg+xml")
                            .header("Cache-Control", "public, max-age=31536000")
                            .build();
                }
            }

            // Priority 2: Check for SVG in configuration (legacy calendars)
            if (cartItem.configuration != null) {
                // Configuration is stored as a JSON string, try to extract svgContent
                String config = cartItem.configuration;
                if (config.contains("\"svgContent\"")) {
                    // Simple extraction - find svgContent value
                    int start = config.indexOf("\"svgContent\":\"");
                    if (start >= 0) {
                        start += "\"svgContent\":\"".length();
                        int end = config.indexOf("\"", start);
                        // Handle escaped quotes in SVG
                        while (end > 0 && config.charAt(end - 1) == '\\') {
                            end = config.indexOf("\"", end + 1);
                        }
                        if (end > start) {
                            String svg =
                                    config.substring(start, end)
                                            .replace("\\\"", "\"")
                                            .replace("\\n", "\n")
                                            .replace("\\\\", "\\");
                            return Response.ok(svg)
                                    .header("Content-Type", "image/svg+xml")
                                    .header("Cache-Control", "public, max-age=31536000")
                                    .build();
                        }
                    }
                }
            }

            // No SVG available
            LOG.warnf("No SVG content available for cart item: %s", itemId);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("No thumbnail available for this cart item")
                    .build();

        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid cart item ID format: %s", itemId);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Invalid cart item ID format")
                    .build();
        } catch (Exception e) {
            LOG.errorf(e, "Error fetching cart item thumbnail %s", itemId);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error fetching thumbnail")
                    .build();
        }
    }
}
