package villagecompute.calendar.data.models;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.eclipse.microprofile.graphql.Ignore;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Parameters;

/**
 * Entity representing an e-commerce order for printed calendars. Integrates with Stripe for payment processing.
 */
@Entity
@NamedQueries({@NamedQuery(
        name = CalendarOrder.QUERY_FIND_BY_ORDER_NUMBER_WITH_ITEMS,
        query = "SELECT DISTINCT o FROM CalendarOrder o " + "LEFT JOIN FETCH o.items i " + "LEFT JOIN FETCH i.assets "
                + "WHERE o.orderNumber = :orderNumber")})
@Table(
        name = "calendar_orders",
        indexes = {@Index(
                name = "idx_calendar_orders_user",
                columnList = "user_id, created DESC"),
                @Index(
                        name = "idx_calendar_orders_status",
                        columnList = "status, created DESC"),
                @Index(
                        name = "idx_calendar_orders_calendar",
                        columnList = "calendar_id"),
                @Index(
                        name = "idx_calendar_orders_stripe_payment",
                        columnList = "stripe_payment_intent_id"),
                @Index(
                        name = "idx_calendar_orders_order_number",
                        columnList = "order_number",
                        unique = true)})
public class CalendarOrder extends DefaultPanacheEntityWithTimestamps {

    public static final String QUERY_FIND_BY_ORDER_NUMBER_WITH_ITEMS = "CalendarOrder.findByOrderNumberWithItems";

    @ManyToOne(
            optional = true)
    @JoinColumn(
            name = "user_id",
            nullable = true,
            foreignKey = @ForeignKey(
                    name = "fk_calendar_orders_user"))
    @Ignore // GraphQL field resolver provided by OrderResolver.batchLoadUsers()
    public CalendarUser user; // Nullable for guest checkout

    /** Customer email (especially important for guest orders) */
    @Size(
            max = 255)
    @Column(
            name = "customer_email",
            length = 255)
    public String customerEmail;

    // ==================== Order Items ====================

    /** Line items in this order */
    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    public List<CalendarOrderItem> items = new ArrayList<>();

    /** Shipments for this order */
    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    public List<Shipment> shipments = new ArrayList<>();

    // ==================== Legacy Single-Item Fields (for backwards compatibility)
    // ====================

    /**
     * @deprecated Use items list instead. Kept for backwards compatibility with existing orders.
     */
    @Deprecated
    @ManyToOne(
            optional = true)
    @JoinColumn(
            name = "calendar_id",
            foreignKey = @ForeignKey(
                    name = "fk_calendar_orders_calendar"))
    @Ignore
    public UserCalendar calendar;

    /**
     * @deprecated Use items list instead. Kept for backwards compatibility.
     */
    @Deprecated
    @Min(1)
    @Column(
            nullable = true)
    public Integer quantity = 1;

    /**
     * @deprecated Use items list instead. Kept for backwards compatibility.
     */
    @Deprecated
    @DecimalMin("0.00")
    @Column(
            name = "unit_price",
            precision = 10,
            scale = 2)
    public BigDecimal unitPrice;

    // ==================== Order Totals ====================

    /** Subtotal (sum of all line items) */
    @DecimalMin("0.00")
    @Column(
            name = "subtotal",
            precision = 10,
            scale = 2)
    public BigDecimal subtotal;

    /** Shipping cost */
    @DecimalMin("0.00")
    @Column(
            name = "shipping_cost",
            precision = 10,
            scale = 2)
    public BigDecimal shippingCost;

    /** Tax amount */
    @DecimalMin("0.00")
    @Column(
            name = "tax_amount",
            precision = 10,
            scale = 2)
    public BigDecimal taxAmount;

    @NotNull @DecimalMin("0.00")
    @Column(
            name = "total_price",
            nullable = false,
            precision = 10,
            scale = 2)
    public BigDecimal totalPrice;

    @NotNull @Size(
            max = 50)
    @Column(
            nullable = false,
            length = 50)
    public String status = STATUS_PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "shipping_address",
            columnDefinition = "jsonb")
    @io.smallrye.graphql.api.AdaptWith(villagecompute.calendar.api.graphql.scalars.JsonNodeAdapter.class)
    public JsonNode shippingAddress;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "billing_address",
            columnDefinition = "jsonb")
    @io.smallrye.graphql.api.AdaptWith(villagecompute.calendar.api.graphql.scalars.JsonNodeAdapter.class)
    public JsonNode billingAddress;

    @Size(
            max = 255)
    @Column(
            name = "stripe_payment_intent_id",
            length = 255)
    public String stripePaymentIntentId;

    @Size(
            max = 255)
    @Column(
            name = "stripe_charge_id",
            length = 255)
    public String stripeChargeId;

    @Column(
            columnDefinition = "TEXT")
    public String notes;

    @Column(
            name = "paid_at")
    public Instant paidAt;

    @Column(
            name = "shipped_at")
    public Instant shippedAt;

    @Column(
            name = "cancelled_at")
    public Instant cancelledAt;

    @Size(
            max = 50)
    @Column(
            name = "order_number",
            unique = true,
            length = 50)
    public String orderNumber;

    @Size(
            max = 255)
    @Column(
            name = "tracking_number",
            length = 255)
    public String trackingNumber;

    // Order status constants
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_PRINTED = "PRINTED";
    public static final String STATUS_SHIPPED = "SHIPPED";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    // Static finder methods (ActiveRecord pattern)

    /**
     * Find all orders for a specific user, ordered by creation date descending.
     *
     * @param userId
     *            User ID
     * @return Query of orders
     */
    public static PanacheQuery<CalendarOrder> findByUser(UUID userId) {
        return find("user.id = ?1 ORDER BY created DESC", userId);
    }

    /**
     * Find orders by status, ordered by creation date descending. This is the required custom query method from the
     * task specification.
     *
     * @param status
     *            Order status
     * @return List of orders with the specified status
     */
    public static List<CalendarOrder> findByStatusOrderByCreatedDesc(String status) {
        return find("status = ?1 ORDER BY created DESC", status).list();
    }

    /**
     * Find all orders for a specific calendar.
     *
     * @param calendarId
     *            Calendar ID
     * @return Query of orders
     */
    public static PanacheQuery<CalendarOrder> findByCalendar(UUID calendarId) {
        return find("calendar.id = ?1 ORDER BY created DESC", calendarId);
    }

    /**
     * Find orders by Stripe Payment Intent ID.
     *
     * @param paymentIntentId
     *            Stripe Payment Intent ID
     * @return Query of orders
     */
    public static PanacheQuery<CalendarOrder> findByStripePaymentIntent(String paymentIntentId) {
        return find("stripePaymentIntentId", paymentIntentId);
    }

    /**
     * Find recent orders within a time range.
     *
     * @param since
     *            Start time
     * @return Query of recent orders
     */
    public static PanacheQuery<CalendarOrder> findRecentOrders(Instant since) {
        return find("created >= ?1 ORDER BY created DESC", since);
    }

    /**
     * Find order by order number (unique display number).
     *
     * @param orderNumber
     *            Order number (e.g., "VC-2025-00001")
     * @return Query of orders
     */
    public static PanacheQuery<CalendarOrder> findByOrderNumber(String orderNumber) {
        return find("orderNumber", orderNumber);
    }

    /**
     * Find order by order number with items and calendars eagerly loaded. Uses a single query with JOIN FETCH to avoid
     * N+1 queries.
     *
     * @param orderNumber
     *            Order number (e.g., "VC-2025-00001")
     * @return Optional containing the order with items loaded, or empty if not found
     */
    public static java.util.Optional<CalendarOrder> findByOrderNumberWithItems(String orderNumber) {
        return find("#" + QUERY_FIND_BY_ORDER_NUMBER_WITH_ITEMS, Parameters.with("orderNumber", orderNumber))
                .firstResultOptional();
    }

    /**
     * Count orders created in a specific year.
     *
     * @param year
     *            Year to count orders for
     * @return Count of orders created in the specified year
     */
    public static long countOrdersByYear(int year) {
        return count("EXTRACT(YEAR FROM created) = ?1", year);
    }

    // Helper methods

    /** Mark this order as paid and set the payment timestamp. */
    public void markAsPaid() {
        this.status = STATUS_PAID;
        this.paidAt = Instant.now();
        persist();
    }

    /** Mark this order as shipped and set the shipment timestamp. */
    public void markAsShipped() {
        this.status = STATUS_SHIPPED;
        this.shippedAt = Instant.now();
        persist();
    }

    /** Cancel this order. */
    public void cancel() {
        this.status = STATUS_CANCELLED;
        this.cancelledAt = Instant.now();
        persist();
    }

    /**
     * Check if this order is in a terminal state (completed or cancelled).
     *
     * @return true if the order is delivered or cancelled
     */
    public boolean isTerminal() {
        return STATUS_DELIVERED.equals(status) || STATUS_CANCELLED.equals(status);
    }

    // ==================== Item Management ====================

    /** Add an item to this order */
    public CalendarOrderItem addItem(CalendarOrderItem item) {
        item.order = this;
        items.add(item);
        return item;
    }

    /** Get all physical items that require shipping */
    public List<CalendarOrderItem> getPhysicalItems() {
        return items.stream().filter(CalendarOrderItem::requiresShipping).toList();
    }

    /** Get all digital items (PDFs) */
    public List<CalendarOrderItem> getDigitalItems() {
        return items.stream().filter(CalendarOrderItem::isDigital).toList();
    }

    /** Get items that haven't been assigned to a shipment yet */
    public List<CalendarOrderItem> getUnshippedItems() {
        return items.stream().filter(item -> item.requiresShipping() && item.shipment == null).toList();
    }

    /** Check if all items have been shipped or are digital */
    public boolean isFullyShipped() {
        return getUnshippedItems().isEmpty();
    }

    /** Get total item count (sum of quantities) */
    public int getTotalItemCount() {
        return items.stream().mapToInt(item -> item.quantity).sum();
    }

    /** Calculate and update subtotal from items */
    public void calculateSubtotal() {
        this.subtotal = items.stream().map(item -> item.lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Calculate and update total price from subtotal, shipping, and tax */
    public void calculateTotalPrice() {
        BigDecimal sub = subtotal != null ? subtotal : BigDecimal.ZERO;
        BigDecimal ship = shippingCost != null ? shippingCost : BigDecimal.ZERO;
        BigDecimal tax = taxAmount != null ? taxAmount : BigDecimal.ZERO;
        this.totalPrice = sub.add(ship).add(tax);
    }
}
