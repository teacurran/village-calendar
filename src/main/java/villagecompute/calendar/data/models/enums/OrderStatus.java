package villagecompute.calendar.data.models.enums;

/**
 * Order fulfillment status tracking through the order lifecycle.
 *
 * <p>Status transitions typically follow this flow:
 * <pre>
 * PENDING -> PAID -> PROCESSING -> SHIPPED -> DELIVERED
 *    |        |
 *    v        v
 * CANCELLED REFUNDED
 * </pre>
 */
public enum OrderStatus {
    /**
     * Order created, awaiting payment confirmation from Stripe.
     */
    PENDING,

    /**
     * Payment successfully captured via Stripe.
     */
    PAID,

    /**
     * Calendar is being printed and prepared for shipment.
     */
    PROCESSING,

    /**
     * Order has been shipped to customer with tracking number.
     */
    SHIPPED,

    /**
     * Order successfully delivered to customer.
     */
    DELIVERED,

    /**
     * Order cancelled by customer or admin before fulfillment.
     */
    CANCELLED,

    /**
     * Payment refunded after cancellation.
     */
    REFUNDED
}
