package villagecompute.calendar.api.graphql;

import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Context;
import org.eclipse.microprofile.graphql.*;
import org.jboss.logging.Logger;
import villagecompute.calendar.api.graphql.inputs.AddToCartInput;
import villagecompute.calendar.api.graphql.types.Cart;
import villagecompute.calendar.services.CartService;
import villagecompute.calendar.services.SessionService;

import java.util.UUID;

/**
 * GraphQL API for shopping cart operations
 * Supports both authenticated users and guest sessions
 */
@GraphQLApi
@ApplicationScoped
public class CartGraphQL {

    private static final Logger LOG = Logger.getLogger(CartGraphQL.class);

    @Inject
    CartService cartService;

    @Inject
    SessionService sessionService;

    /**
     * Get the current user/session cart
     */
    @Query("cart")
    @Description("Get the current user's shopping cart")
    public Cart getCart() {
        String sessionId = sessionService.getCurrentSessionId();
        LOG.info("Fetching cart for session: " + sessionId);
        return cartService.getCart(sessionId);
    }

    /**
     * Add an item to the cart
     */
    @Mutation("addToCart")
    @Description("Add a calendar to the shopping cart")
    @Transactional
    public Cart addToCart(@Name("input") AddToCartInput input) {
        String sessionId = sessionService.getCurrentSessionId();
        LOG.info(String.format("Adding to cart for session %s: template=%s, year=%d, quantity=%d",
            sessionId, input.templateId, input.year, input.quantity));

        return cartService.addToCart(sessionId, input);
    }

    /**
     * Update the quantity of a cart item
     */
    @Mutation("updateCartItemQuantity")
    @Description("Update the quantity of an item in the cart")
    @Transactional
    public Cart updateCartItemQuantity(
        @Name("itemId") String itemId,
        @Name("quantity") Integer quantity
    ) {
        String sessionId = sessionService.getCurrentSessionId();
        LOG.info(String.format("Updating cart item %s to quantity %d for session %s",
            itemId, quantity, sessionId));

        return cartService.updateQuantity(sessionId, UUID.fromString(itemId), quantity);
    }

    /**
     * Remove an item from the cart
     */
    @Mutation("removeFromCart")
    @Description("Remove an item from the cart")
    @Transactional
    public Cart removeFromCart(@Name("itemId") String itemId) {
        String sessionId = sessionService.getCurrentSessionId();
        LOG.info(String.format("Removing item %s from cart for session %s", itemId, sessionId));

        return cartService.removeItem(sessionId, UUID.fromString(itemId));
    }

    /**
     * Clear all items from the cart
     */
    @Mutation("clearCart")
    @Description("Clear all items from the cart")
    @Transactional
    public Cart clearCart() {
        String sessionId = sessionService.getCurrentSessionId();
        LOG.info("Clearing cart for session " + sessionId);

        return cartService.clearCart(sessionId);
    }
}
