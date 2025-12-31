package villagecompute.calendar.api.types;

import java.util.UUID;

import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.Type;

/**
 * API response type for Stripe PaymentIntent. Returned by placeOrder mutation to initiate
 * payment on client. The client uses the clientSecret to complete payment via Stripe.js. The order
 * entity is created by webhook after payment succeeds.
 * Used by both REST and GraphQL endpoints.
 */
@Type("PaymentIntent")
@Description(
        "Stripe PaymentIntent for checkout flow. "
                + "Returned by placeOrder mutation to initiate payment on client. "
                + "The client uses the clientSecret to complete payment via Stripe.js.")
public class PaymentIntentResponse {

    @Description("Stripe PaymentIntent ID or Checkout Session ID")
    public String id;

    @Description("Client secret for Stripe.js (passed to confirmCardPayment) or checkout URL")
    public String clientSecret;

    @Description("Amount in cents (USD) - multiply dollar amount by 100")
    public Integer amount;

    @Description("Associated calendar ID (before order is created)")
    public UUID calendarId;

    @Description("Requested quantity")
    public Integer quantity;

    @Description("Payment status from Stripe")
    public String status;

    /**
     * Create a PaymentIntent response from Stripe Checkout Session data.
     *
     * @param sessionId Stripe Checkout Session ID
     * @param checkoutUrl Stripe checkout URL (used as client secret for redirect flow)
     * @param amountInCents Total amount in cents
     * @param calendarId Associated calendar ID
     * @param quantity Order quantity
     * @return PaymentIntentResponse instance
     */
    public static PaymentIntentResponse fromCheckoutSession(
            String sessionId,
            String checkoutUrl,
            Integer amountInCents,
            UUID calendarId,
            Integer quantity) {
        PaymentIntentResponse response = new PaymentIntentResponse();
        response.id = sessionId;
        response.clientSecret = checkoutUrl;
        response.amount = amountInCents;
        response.calendarId = calendarId;
        response.quantity = quantity;
        response.status = "requires_payment_method";
        return response;
    }
}
