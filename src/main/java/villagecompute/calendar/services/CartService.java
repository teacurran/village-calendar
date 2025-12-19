package villagecompute.calendar.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import villagecompute.calendar.api.graphql.inputs.AddToCartInput;
import villagecompute.calendar.api.graphql.inputs.AssetInput;
import villagecompute.calendar.api.graphql.types.Cart;
import villagecompute.calendar.api.graphql.types.CartItem;
import villagecompute.calendar.data.models.ItemAsset;

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
     * Add item to cart.
     * Supports both new generator-based items (with generatorType and assets) and legacy calendar items.
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

        int quantity = input.quantity != null ? input.quantity : 1;
        villagecompute.calendar.data.models.CartItem cartItem;

        // Check if using new generator-based format
        if (input.generatorType != null) {
            LOG.infof("Adding generator-based item: type=%s, description=%s, quantity=%d",
                input.generatorType, input.description, quantity);

            // Create new cart item (generator items are always new line items since SVGs are unique)
            cartItem = cartEntity.addItem(
                input.generatorType,
                input.description,
                quantity,
                unitPrice,
                input.configuration,
                productCode
            );

            // Create and attach asset records if provided
            if (input.assets != null && !input.assets.isEmpty()) {
                for (AssetInput assetInput : input.assets) {
                    ItemAsset asset = ItemAsset.create(
                        assetInput.assetKey,
                        assetInput.svgContent,
                        assetInput.widthInches,
                        assetInput.heightInches
                    );
                    asset.persist();
                    cartItem.addAsset(asset);
                    LOG.infof("Added asset '%s' to cart item", assetInput.assetKey);
                }
            }
        } else {
            // Legacy calendar format - use addOrUpdateItem for backward compatibility
            LOG.infof("Adding legacy calendar item: template=%s, year=%d, quantity=%d",
                input.templateId, input.year, quantity);

            cartItem = cartEntity.addOrUpdateItem(
                input.templateId,
                input.templateName != null ? input.templateName : "Calendar " + input.year,
                input.year != null ? input.year : 0,
                quantity,
                unitPrice,
                input.configuration,
                productCode
            );
        }

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
        return CartItem.fromEntity(itemEntity);
    }
}
