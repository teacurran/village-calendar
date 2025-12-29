package villagecompute.calendar.api.rest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;

import villagecompute.calendar.api.graphql.inputs.AddToCartInput;
import villagecompute.calendar.api.graphql.types.Cart;
import villagecompute.calendar.services.CartService;
import villagecompute.calendar.services.SessionService;

/**
 * Cart Resource - REST API for shopping cart operations Supports both authenticated users and guest
 * sessions via X-Session-ID header
 */
@Path("/cart")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CartResource {

    private static final Logger LOG = Logger.getLogger(CartResource.class);

    @Inject CartService cartService;

    @Inject SessionService sessionService;

    /** Get cart items for current session */
    @GET
    @Path("/items")
    public Response getCartItems() {
        String sessionId = sessionService.getCurrentSessionId();
        LOG.infof("REST: Fetching cart for session: %s", sessionId);

        Cart cart = cartService.getCart(sessionId);

        Map<String, Object> response = new HashMap<>();
        response.put("items", cart.items);
        response.put("total", cart.totalAmount);
        response.put("subtotal", cart.subtotal);
        response.put("taxAmount", cart.taxAmount);
        response.put("itemCount", cart.itemCount);

        return Response.ok(response).build();
    }

    /** Add item to cart */
    @POST
    @Path("/items")
    @Transactional
    public Response addToCart(Map<String, Object> requestBody) {
        String sessionId = sessionService.getCurrentSessionId();
        LOG.infof("REST: Adding to cart for session: %s, body: %s", sessionId, requestBody);

        // Validate required fields
        if (requestBody.get("productId") == null && requestBody.get("templateId") == null) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "productId or templateId is required");
            return Response.status(Response.Status.BAD_REQUEST).entity(errorResponse).build();
        }

        // Parse request body into AddToCartInput
        AddToCartInput input = new AddToCartInput();

        // Support both productId (frontend) and templateId (direct API)
        input.templateId =
                requestBody.get("productId") != null
                        ? (String) requestBody.get("productId")
                        : (String) requestBody.get("templateId");

        input.quantity =
                requestBody.get("quantity") != null
                        ? ((Number) requestBody.get("quantity")).intValue()
                        : 1;

        // Parse configuration to extract year and name if provided as JSON
        String configStr =
                requestBody.get("configuration") != null
                        ? requestBody.get("configuration").toString()
                        : null;

        if (configStr != null && configStr.startsWith("{")) {
            try {
                // Parse the configuration JSON to extract year and name
                com.fasterxml.jackson.databind.ObjectMapper mapper =
                        new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> configData = mapper.readValue(configStr, Map.class);

                if (configData.get("year") != null) {
                    input.year = ((Number) configData.get("year")).intValue();
                }
                if (configData.get("name") != null) {
                    input.templateName = (String) configData.get("name");
                }
                input.configuration = configStr;
            } catch (Exception e) {
                LOG.warnf("Could not parse configuration JSON: %s", e.getMessage());
                input.configuration = configStr;
            }
        }

        // Fall back to direct fields if not in configuration
        if (input.year == null && requestBody.get("year") != null) {
            input.year = ((Number) requestBody.get("year")).intValue();
        }
        if (input.templateName == null && requestBody.get("templateName") != null) {
            input.templateName = (String) requestBody.get("templateName");
        }
        if (input.unitPrice == null && requestBody.get("unitPrice") != null) {
            input.unitPrice = ((Number) requestBody.get("unitPrice")).doubleValue();
        }

        // Set default year if still not provided
        if (input.year == null) {
            input.year = java.time.Year.now().getValue();
            LOG.infof("No year provided, using current year: %d", input.year);
        }

        Cart cart = cartService.addToCart(sessionId, input);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Item added to cart");
        response.put("itemCount", cart.itemCount);
        response.put("cart", cart);

        return Response.ok(response).build();
    }

    /** Remove item from cart */
    @DELETE
    @Path("/items/{itemId}")
    @Transactional
    public Response removeFromCart(@PathParam("itemId") String itemId) {
        String sessionId = sessionService.getCurrentSessionId();
        LOG.infof("REST: Removing item %s from cart for session: %s", itemId, sessionId);

        Cart cart = cartService.removeItem(sessionId, UUID.fromString(itemId));

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("itemCount", cart.itemCount);
        response.put("cart", cart);

        return Response.ok(response).build();
    }

    /** Update item quantity */
    @PATCH
    @Path("/items/{itemId}")
    @Transactional
    public Response updateQuantity(
            @PathParam("itemId") String itemId, Map<String, Object> requestBody) {
        String sessionId = sessionService.getCurrentSessionId();
        Integer quantity =
                requestBody.get("quantity") != null
                        ? ((Number) requestBody.get("quantity")).intValue()
                        : null;

        LOG.infof(
                "REST: Updating item %s quantity to %d for session: %s",
                itemId, quantity, sessionId);

        Cart cart = cartService.updateQuantity(sessionId, UUID.fromString(itemId), quantity);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("itemCount", cart.itemCount);
        response.put("cart", cart);

        return Response.ok(response).build();
    }

    /** Clear cart */
    @DELETE
    @Path("/clear")
    @Transactional
    public Response clearCart() {
        String sessionId = sessionService.getCurrentSessionId();
        LOG.infof("REST: Clearing cart for session: %s", sessionId);

        Cart cart = cartService.clearCart(sessionId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("itemCount", cart.itemCount);
        response.put("cart", cart);

        return Response.ok(response).build();
    }
}
