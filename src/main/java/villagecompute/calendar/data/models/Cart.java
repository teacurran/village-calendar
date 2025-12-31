package villagecompute.calendar.data.models;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

import org.eclipse.microprofile.graphql.Ignore;

/**
 * Shopping cart for both authenticated users and guest sessions. Guest carts use sessionId,
 * authenticated carts use user.
 */
@Entity
@Table(
        name = "carts",
        indexes = {
            @Index(name = "idx_carts_user", columnList = "user_id"),
            @Index(name = "idx_carts_session", columnList = "session_id")
        })
public class Cart extends DefaultPanacheEntityWithTimestamps {

    /** User who owns this cart (null for guest carts) */
    @ManyToOne
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_carts_user"))
    @Ignore
    public CalendarUser user;

    /** Session ID for guest carts (null for authenticated user carts) */
    @Column(name = "session_id", length = 255)
    public String sessionId;

    /** Cart items */
    @OneToMany(
            mappedBy = "cart",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    public List<CartItem> items = new ArrayList<>();

    /** Get cart for authenticated user, create if doesn't exist */
    public static Cart getOrCreateForUser(CalendarUser user) {
        Cart cart = find("user", user).firstResult();
        if (cart == null) {
            cart = new Cart();
            cart.user = user;
            cart.persist();
        }
        return cart;
    }

    /** Get cart for guest session, create if doesn't exist */
    public static Cart getOrCreateForSession(String sessionId) {
        Cart cart = find("sessionId", sessionId).firstResult();
        if (cart == null) {
            cart = new Cart();
            cart.sessionId = sessionId;
            cart.persist();
        }
        return cart;
    }

    /** Calculate cart subtotal */
    public BigDecimal getSubtotal() {
        return items.stream()
                .map(item -> item.getLineTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Get total item count */
    public int getItemCount() {
        return items.stream().mapToInt(item -> item.quantity).sum();
    }

    /**
     * Add an item to cart. Each item is treated as a new line item since each generated SVG is
     * unique and should not be merged.
     */
    public CartItem addItem(
            String generatorType,
            String description,
            int quantity,
            BigDecimal unitPrice,
            String configuration,
            String productCode) {
        CartItem newItem = new CartItem();
        newItem.cart = this;
        newItem.generatorType = generatorType;
        newItem.description = description;
        newItem.quantity = quantity;
        newItem.unitPrice = unitPrice;
        newItem.configuration = configuration;
        newItem.productCode = productCode;
        newItem.persist();
        items.add(newItem);
        return newItem;
    }

    /** Remove item from cart */
    public void removeItem(CartItem item) {
        items.remove(item);
        item.delete();
    }

    /** Clear all items from cart */
    public void clearItems() {
        items.forEach(CartItem::delete);
        items.clear();
    }
}
