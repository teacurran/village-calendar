package villagecompute.calendar.types;

import java.util.List;
import java.util.Map;

/**
 * Parameter object for {@code OrderService.processCheckout}. Groups the data required to record a completed checkout
 * after Stripe payment is confirmed.
 *
 * @param paymentIntentId
 *            Stripe PaymentIntent ID
 * @param email
 *            Customer email
 * @param shippingAddress
 *            Shipping address map
 * @param billingAddress
 *            Billing address map (optional, may be {@code null})
 * @param items
 *            Cart items (list of maps with item details)
 * @param subtotal
 *            Order subtotal
 * @param shippingCost
 *            Shipping cost
 * @param taxAmount
 *            Tax amount
 * @param totalAmount
 *            Total amount charged
 */
public record CheckoutRequestType(String paymentIntentId, String email, Map<String, Object> shippingAddress,
        Map<String, Object> billingAddress, List<Map<String, Object>> items, Double subtotal, Double shippingCost,
        Double taxAmount, Double totalAmount) {

    /** Convenience constructor without billing address. */
    public CheckoutRequestType(String paymentIntentId, String email, Map<String, Object> shippingAddress,
            List<Map<String, Object>> items, Double subtotal, Double shippingCost, Double taxAmount,
            Double totalAmount) {
        this(paymentIntentId, email, shippingAddress, null, items, subtotal, shippingCost, taxAmount, totalAmount);
    }
}
