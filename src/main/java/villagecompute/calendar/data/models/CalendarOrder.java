package villagecompute.calendar.data.models;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing an e-commerce order for printed calendars.
 * Integrates with Stripe for payment processing.
 */
@Entity
@Table(
    name = "calendar_orders",
    indexes = {
        @Index(name = "idx_calendar_orders_user", columnList = "user_id, created DESC"),
        @Index(name = "idx_calendar_orders_status", columnList = "status, created DESC"),
        @Index(name = "idx_calendar_orders_calendar", columnList = "calendar_id"),
        @Index(name = "idx_calendar_orders_stripe_payment", columnList = "stripe_payment_intent_id")
    }
)
public class CalendarOrder extends DefaultPanacheEntityWithTimestamps {

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_calendar_orders_user"))
    public CalendarUser user;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "calendar_id", nullable = false, foreignKey = @ForeignKey(name = "fk_calendar_orders_calendar"))
    public UserCalendar calendar;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    public Integer quantity = 1;

    @NotNull
    @DecimalMin("0.00")
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    public BigDecimal unitPrice;

    @NotNull
    @DecimalMin("0.00")
    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    public BigDecimal totalPrice;

    @NotNull
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    public String status = "PENDING";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shipping_address", columnDefinition = "jsonb")
    @io.smallrye.graphql.api.AdaptWith(villagecompute.calendar.api.graphql.scalars.JsonNodeAdapter.class)
    public JsonNode shippingAddress;

    @Size(max = 255)
    @Column(name = "stripe_payment_intent_id", length = 255)
    public String stripePaymentIntentId;

    @Size(max = 255)
    @Column(name = "stripe_charge_id", length = 255)
    public String stripeChargeId;

    @Column(columnDefinition = "TEXT")
    public String notes;

    @Column(name = "paid_at")
    public Instant paidAt;

    @Column(name = "shipped_at")
    public Instant shippedAt;

    // Order status constants
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_SHIPPED = "SHIPPED";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    // Static finder methods (ActiveRecord pattern)

    /**
     * Find all orders for a specific user, ordered by creation date descending.
     *
     * @param userId User ID
     * @return Query of orders
     */
    public static PanacheQuery<CalendarOrder> findByUser(UUID userId) {
        return find("user.id = ?1 ORDER BY created DESC", userId);
    }

    /**
     * Find orders by status, ordered by creation date descending.
     * This is the required custom query method from the task specification.
     *
     * @param status Order status
     * @return List of orders with the specified status
     */
    public static List<CalendarOrder> findByStatusOrderByCreatedDesc(String status) {
        return find("status = ?1 ORDER BY created DESC", status).list();
    }

    /**
     * Find all orders for a specific calendar.
     *
     * @param calendarId Calendar ID
     * @return Query of orders
     */
    public static PanacheQuery<CalendarOrder> findByCalendar(UUID calendarId) {
        return find("calendar.id = ?1 ORDER BY created DESC", calendarId);
    }

    /**
     * Find orders by Stripe Payment Intent ID.
     *
     * @param paymentIntentId Stripe Payment Intent ID
     * @return Query of orders
     */
    public static PanacheQuery<CalendarOrder> findByStripePaymentIntent(String paymentIntentId) {
        return find("stripePaymentIntentId", paymentIntentId);
    }

    /**
     * Find recent orders within a time range.
     *
     * @param since Start time
     * @return Query of recent orders
     */
    public static PanacheQuery<CalendarOrder> findRecentOrders(Instant since) {
        return find("created >= ?1 ORDER BY created DESC", since);
    }

    // Helper methods

    /**
     * Mark this order as paid and set the payment timestamp.
     */
    public void markAsPaid() {
        this.status = STATUS_PAID;
        this.paidAt = Instant.now();
        persist();
    }

    /**
     * Mark this order as shipped and set the shipment timestamp.
     */
    public void markAsShipped() {
        this.status = STATUS_SHIPPED;
        this.shippedAt = Instant.now();
        persist();
    }

    /**
     * Cancel this order.
     */
    public void cancel() {
        this.status = STATUS_CANCELLED;
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
}
