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
import villagecompute.calendar.services.CartService;
import villagecompute.calendar.services.SessionService;
import villagecompute.calendar.types.CartType;

/**
 * Cart Resource - REST API for shopping cart operations Supports both authenticated users and guest sessions via
 * X-Session-ID header
 */
@Path("/cart")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CartResource {

    private static final Logger LOG = Logger.getLogger(CartResource.class);

    // JSON field name constants
    private static final String FIELD_QUANTITY = "quantity";

    @Inject
    CartService cartService;

    @Inject
    SessionService sessionService;

    /** Get cart items for current session */
    @GET
    @Path("/items")
    public Response getCartItems() {
        String sessionId = sessionService.getCurrentSessionId();
        LOG.infof("REST: Fetching cart for session: %s", sessionId);

        CartType cart = cartService.getCart(sessionId);

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

        // Validate required fields - need generatorType or productId
        if (requestBody.get("generatorType") == null && requestBody.get("productId") == null) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "generatorType or productId is required");
            return Response.status(Response.Status.BAD_REQUEST).entity(errorResponse).build();
        }

        // Parse request body into AddToCartInput
        AddToCartInput input = new AddToCartInput();

        // Map generatorType (or productId for backwards compatibility)
        input.generatorType = requestBody.get("generatorType") != null ? (String) requestBody.get("generatorType")
                : (String) requestBody.get("productId");

        // Map description (or templateName for backwards compatibility)
        input.description = requestBody.get("description") != null ? (String) requestBody.get("description")
                : (String) requestBody.get("templateName");

        input.quantity = requestBody.get(FIELD_QUANTITY) != null ? ((Number) requestBody.get(FIELD_QUANTITY)).intValue()
                : 1;

        input.productCode = requestBody.get("productCode") != null ? (String) requestBody.get("productCode") : null;

        // Handle configuration - can be string or object
        if (requestBody.get("configuration") != null) {
            Object configObj = requestBody.get("configuration");
            if (configObj instanceof String) {
                input.configuration = (String) configObj;
            } else {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    input.configuration = mapper.writeValueAsString(configObj);
                } catch (Exception e) {
                    LOG.warnf("Could not serialize configuration: %s", e.getMessage());
                    input.configuration = configObj.toString();
                }
            }
        }

        CartType cart = cartService.addToCart(sessionId, input);

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

        CartType cart = cartService.removeItem(sessionId, UUID.fromString(itemId));

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
    public Response updateQuantity(@PathParam("itemId") String itemId, Map<String, Object> requestBody) {
        String sessionId = sessionService.getCurrentSessionId();
        Integer quantity = requestBody.get(FIELD_QUANTITY) != null
                ? ((Number) requestBody.get(FIELD_QUANTITY)).intValue()
                : null;

        LOG.infof("REST: Updating item %s quantity to %d for session: %s", itemId, quantity, sessionId);

        CartType cart = cartService.updateQuantity(sessionId, UUID.fromString(itemId), quantity);

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

        CartType cart = cartService.clearCart(sessionId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("itemCount", cart.itemCount);
        response.put("cart", cart);

        return Response.ok(response).build();
    }
}
