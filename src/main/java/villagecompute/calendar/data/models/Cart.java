package villagecompute.calendar.data.models;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.graphql.Ignore;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Shopping cart for both authenticated users and guest sessions.
 * Guest carts use sessionId, authenticated carts use user.
 */
@Entity
@Table(
    name = "carts",
    indexes = {
        @Index(name = "idx_carts_user", columnList = "user_id"),
        @Index(name = "idx_carts_session", columnList = "session_id")
    }
)
public class Cart extends DefaultPanacheEntityWithTimestamps {

    /**
     * User who owns this cart (null for guest carts)
     */
    @ManyToOne
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_carts_user"))
    @Ignore
    public CalendarUser user;

    /**
     * Session ID for guest carts (null for authenticated user carts)
     */
    @Column(name = "session_id", length = 255)
    public String sessionId;

    /**
     * Cart items
     */
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    public List<CartItem> items = new ArrayList<>();

    /**
     * Get cart for authenticated user, create if doesn't exist
     */
    public static Cart getOrCreateForUser(CalendarUser user) {
        Cart cart = find("user", user).firstResult();
        if (cart == null) {
            cart = new Cart();
            cart.user = user;
            cart.persist();
        }
        return cart;
    }

    /**
     * Get cart for guest session, create if doesn't exist
     */
    public static Cart getOrCreateForSession(String sessionId) {
        Cart cart = find("sessionId", sessionId).firstResult();
        if (cart == null) {
            cart = new Cart();
            cart.sessionId = sessionId;
            cart.persist();
        }
        return cart;
    }

    /**
     * Calculate cart subtotal
     */
    public BigDecimal getSubtotal() {
        return items.stream()
            .map(item -> item.getLineTotal())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get total item count
     */
    public int getItemCount() {
        return items.stream()
            .mapToInt(item -> item.quantity)
            .sum();
    }

    /**
     * Add item to cart or update quantity if already exists.
     * Items are considered the same if they have the same templateId, year, AND configuration.
     * Different calendar designs (different configurations) are separate line items.
     */
    public CartItem addOrUpdateItem(String templateId, String templateName, int year,
                                    int quantity, BigDecimal unitPrice, String configuration) {
        // Check if item already exists with same template, year, AND configuration
        CartItem existing = items.stream()
            .filter(item -> item.templateId.equals(templateId)
                && item.year == year
                && configurationEquals(item.configuration, configuration))
            .findFirst()
            .orElse(null);

        if (existing != null) {
            existing.quantity += quantity;
            existing.persist();
            return existing;
        } else {
            CartItem newItem = new CartItem();
            newItem.cart = this;
            newItem.templateId = templateId;
            newItem.templateName = templateName;
            newItem.year = year;
            newItem.quantity = quantity;
            newItem.unitPrice = unitPrice;
            newItem.configuration = configuration;
            newItem.persist();
            items.add(newItem);
            return newItem;
        }
    }

    /**
     * Compare two configuration strings for equality.
     * Handles null values - two null configurations are considered equal.
     */
    private boolean configurationEquals(String config1, String config2) {
        if (config1 == null && config2 == null) {
            return true;
        }
        if (config1 == null || config2 == null) {
            return false;
        }
        return config1.equals(config2);
    }

    /**
     * Remove item from cart
     */
    public void removeItem(CartItem item) {
        items.remove(item);
        item.delete();
    }

    /**
     * Clear all items from cart
     */
    public void clearItems() {
        items.forEach(CartItem::delete);
        items.clear();
    }
}
