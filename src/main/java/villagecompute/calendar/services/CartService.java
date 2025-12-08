package villagecompute.calendar.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import villagecompute.calendar.api.graphql.inputs.AddToCartInput;
import villagecompute.calendar.api.graphql.types.Cart;
import villagecompute.calendar.api.graphql.types.CartItem;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for cart operations
 * Handles cart persistence and business logic
 */
@ApplicationScoped
public class CartService {

    private static final Logger LOG = Logger.getLogger(CartService.class);

    @Inject
    ProductService productService;

    /**
     * Get or create cart for session
     */
    @Transactional
    public Cart getCart(String sessionId) {
        villagecompute.calendar.data.models.Cart cartEntity =
            villagecompute.calendar.data.models.Cart.getOrCreateForSession(sessionId);

        return toGraphQLCart(cartEntity);
    }

    /**
     * Add item to cart
     */
    @Transactional
    public Cart addToCart(String sessionId, AddToCartInput input) {
        villagecompute.calendar.data.models.Cart cartEntity =
            villagecompute.calendar.data.models.Cart.getOrCreateForSession(sessionId);

        // Get price from product catalog (preferred) or fall back to provided price
        BigDecimal unitPrice;
        String productCode = input.productCode;
        if (productCode != null && productService.isValidProductCode(productCode)) {
            unitPrice = productService.getPrice(productCode);
            LOG.infof("Using price from product catalog for '%s': $%.2f", productCode, unitPrice);
        } else if (input.unitPrice != null) {
            // Fallback for backwards compatibility - log warning
            unitPrice = BigDecimal.valueOf(input.unitPrice);
            LOG.warnf("Using client-provided price $%.2f - productCode not provided or invalid", unitPrice);
        } else {
            // Use default product price
            productCode = productService.getDefaultProductCode();
            unitPrice = productService.getPrice(productCode);
            LOG.infof("Using default product '%s' price: $%.2f", productCode, unitPrice);
        }

        cartEntity.addOrUpdateItem(
            input.templateId,
            input.templateName != null ? input.templateName : "Calendar " + input.year,
            input.year,
            input.quantity != null ? input.quantity : 1,
            unitPrice,
            input.configuration,
            productCode
        );

        return toGraphQLCart(cartEntity);
    }

    /**
     * Update item quantity
     */
    @Transactional
    public Cart updateQuantity(String sessionId, UUID itemId, Integer quantity) {
        villagecompute.calendar.data.models.Cart cartEntity =
            villagecompute.calendar.data.models.Cart.getOrCreateForSession(sessionId);

        villagecompute.calendar.data.models.CartItem item =
            villagecompute.calendar.data.models.CartItem.findById(itemId);

        if (item != null && item.cart.id.equals(cartEntity.id)) {
            if (quantity <= 0) {
                cartEntity.removeItem(item);
            } else {
                item.quantity = quantity;
                item.persist();
            }
        }

        return toGraphQLCart(cartEntity);
    }

    /**
     * Remove item from cart
     */
    @Transactional
    public Cart removeItem(String sessionId, UUID itemId) {
        villagecompute.calendar.data.models.Cart cartEntity =
            villagecompute.calendar.data.models.Cart.getOrCreateForSession(sessionId);

        villagecompute.calendar.data.models.CartItem item =
            villagecompute.calendar.data.models.CartItem.findById(itemId);

        if (item != null && item.cart.id.equals(cartEntity.id)) {
            cartEntity.removeItem(item);
        }

        return toGraphQLCart(cartEntity);
    }

    /**
     * Clear cart
     */
    @Transactional
    public Cart clearCart(String sessionId) {
        villagecompute.calendar.data.models.Cart cartEntity =
            villagecompute.calendar.data.models.Cart.getOrCreateForSession(sessionId);

        cartEntity.clearItems();

        return toGraphQLCart(cartEntity);
    }

    /**
     * Convert database cart entity to GraphQL cart type
     */
    private Cart toGraphQLCart(villagecompute.calendar.data.models.Cart cartEntity) {
        Cart cart = new Cart();
        cart.id = cartEntity.id.toString();
        cart.subtotal = cartEntity.getSubtotal().doubleValue();
        cart.taxAmount = 0.0; // TODO: Calculate tax
        cart.totalAmount = cart.subtotal + cart.taxAmount;
        cart.itemCount = cartEntity.getItemCount();
        cart.items = cartEntity.items.stream()
            .map(this::toGraphQLCartItem)
            .collect(Collectors.toList());

        return cart;
    }

    /**
     * Convert database cart item to GraphQL cart item
     */
    private CartItem toGraphQLCartItem(villagecompute.calendar.data.models.CartItem itemEntity) {
        CartItem item = new CartItem();
        item.id = itemEntity.id.toString();
        item.templateId = itemEntity.templateId;
        item.templateName = itemEntity.templateName;
        item.year = itemEntity.year;
        item.quantity = itemEntity.quantity;
        item.unitPrice = itemEntity.unitPrice.doubleValue();
        item.lineTotal = itemEntity.getLineTotal().doubleValue();
        item.productCode = itemEntity.productCode;
        item.configuration = itemEntity.configuration; // Already a String in the database
        return item;
    }
}
