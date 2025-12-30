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
     * Add item to cart or update quantity if already exists. Items are considered the same if they
     * have the same templateId, year, AND configuration. Different calendar designs (different
     * configurations) are separate line items.
     */
    public CartItem addOrUpdateItem(
            String templateId,
            String templateName,
            int year,
            int quantity,
            BigDecimal unitPrice,
            String configuration) {
        return addOrUpdateItem(
                templateId, templateName, year, quantity, unitPrice, configuration, null);
    }

    /**
     * Add item to cart or update quantity if already exists. Items are considered the same if they
     * have the same templateId (or both null), year, AND configuration. Different calendar designs
     * (different configurations) are separate line items.
     */
    public CartItem addOrUpdateItem(
            String templateId,
            String templateName,
            int year,
            int quantity,
            BigDecimal unitPrice,
            String configuration,
            String productCode) {
        // Check if item already exists with same template, year, AND configuration
        // When templateId is null (static page purchase), match by configuration + year
        CartItem existing =
                items.stream()
                        .filter(
                                item ->
                                        stringEquals(item.templateId, templateId)
                                                && item.year == year
                                                && configurationEquals(
                                                        item.configuration, configuration))
                        .findFirst()
                        .orElse(null);

        if (existing != null) {
            existing.quantity += quantity;
            existing.productCode = productCode;
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
            newItem.productCode = productCode;
            newItem.persist();
            items.add(newItem);
            return newItem;
        }
    }

    /**
     * Compare two strings for equality. Handles null values - two null strings are considered
     * equal.
     */
    private boolean stringEquals(String str1, String str2) {
        if (str1 == null && str2 == null) {
            return true;
        }
        if (str1 == null || str2 == null) {
            return false;
        }
        return str1.equals(str2);
    }

    /**
     * Compare two configuration strings for equality. Handles null values - two null configurations
     * are considered equal.
     */
    private boolean configurationEquals(String config1, String config2) {
        return stringEquals(config1, config2);
    }

    /**
     * Add a generic item to cart (supports new generator-based items with assets). Unlike legacy
     * items, generator-based items with SVGs are always treated as new line items since each
     * generated SVG is unique and should not be merged.
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
