package villagecompute.calendar.api.rest;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cart Resource - Stub implementation for shopping cart
 * TODO: Implement full e-commerce cart functionality
 */
@Path("/cart")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CartResource {

    /**
     * Get cart items for current session
     * Stub implementation - returns empty cart
     */
    @GET
    @Path("/items")
    public Response getCartItems() {
        Map<String, Object> response = new HashMap<>();
        response.put("items", new ArrayList<>());
        response.put("total", 0.0);
        response.put("itemCount", 0);

        return Response.ok(response).build();
    }

    /**
     * Add item to cart
     * Stub implementation - returns success but doesn't actually add
     */
    @POST
    @Path("/items")
    public Response addToCart(Map<String, Object> item) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Cart functionality coming soon");
        response.put("itemCount", 0);

        return Response.ok(response).build();
    }

    /**
     * Remove item from cart
     */
    @DELETE
    @Path("/items/{itemId}")
    public Response removeFromCart(@PathParam("itemId") String itemId) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("itemCount", 0);

        return Response.ok(response).build();
    }

    /**
     * Clear cart
     */
    @DELETE
    @Path("/clear")
    public Response clearCart() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("itemCount", 0);

        return Response.ok(response).build();
    }
}
