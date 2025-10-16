package villagecompute.calendar.services;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import villagecompute.calendar.data.models.CalendarOrder;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for integrating with Stripe payment processing.
 * Handles PaymentIntent creation, updates, and payment confirmations.
 */
@ApplicationScoped
public class PaymentService {

    private static final Logger LOG = Logger.getLogger(PaymentService.class);

    @ConfigProperty(name = "stripe.api.key")
    String stripeApiKey;

    @ConfigProperty(name = "stripe.publishable.key")
    String stripePublishableKey;

    @ConfigProperty(name = "stripe.webhook.secret", defaultValue = "")
    String webhookSecret;

    @PostConstruct
    void init() {
        Stripe.apiKey = stripeApiKey;
        LOG.info("Payment service initialized with Stripe API");
    }

    /**
     * Get the publishable key for the frontend.
     *
     * @return Stripe publishable key
     */
    public String getPublishableKey() {
        return stripePublishableKey;
    }

    /**
     * Get the webhook secret for signature validation.
     *
     * @return Stripe webhook secret
     */
    public String getWebhookSecret() {
        return webhookSecret;
    }

    /**
     * Create a PaymentIntent for an order.
     * Returns client secret and payment intent ID for frontend processing.
     *
     * @param amount Total amount to charge
     * @param currency Currency code (default: "usd")
     * @param orderId Order ID for metadata
     * @return Map with clientSecret and paymentIntentId
     * @throws StripeException if Stripe API call fails
     */
    public Map<String, String> createPaymentIntent(
        BigDecimal amount,
        String currency,
        String orderId
    ) throws StripeException {
        LOG.infof("Creating payment intent for order %s, amount $%.2f", orderId, amount);

        // Convert BigDecimal to cents (Stripe uses smallest currency unit)
        long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValue();

        // Generate idempotency key to prevent duplicate charges
        String idempotencyKey = "order_" + orderId + "_" + System.currentTimeMillis();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
            .setAmount(amountInCents)
            .setCurrency(currency != null ? currency : "usd")
            .putMetadata("orderId", orderId)
            .setAutomaticPaymentMethods(
                PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                    .setEnabled(true)
                    .build()
            )
            .build();

        // Create PaymentIntent with idempotency key
        PaymentIntent intent = PaymentIntent.create(params,
            com.stripe.net.RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build()
        );

        Map<String, String> response = new HashMap<>();
        response.put("clientSecret", intent.getClientSecret());
        response.put("paymentIntentId", intent.getId());

        LOG.infof("Created PaymentIntent %s for order %s", intent.getId(), orderId);

        return response;
    }

    /**
     * Update a PaymentIntent with order details.
     * Used to add order information after the PaymentIntent is created.
     *
     * @param paymentIntentId PaymentIntent ID
     * @param order Order details
     * @return Updated PaymentIntent
     * @throws StripeException if Stripe API call fails
     */
    public PaymentIntent updatePaymentIntent(String paymentIntentId, CalendarOrder order)
        throws StripeException {
        LOG.infof("Updating PaymentIntent %s with order details", paymentIntentId);

        PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);

        Map<String, Object> params = new HashMap<>();
        params.put("description", String.format("Calendar Order - %s", order.calendar.name));

        Map<String, String> metadata = new HashMap<>();
        metadata.put("orderId", order.id.toString());
        metadata.put("calendarId", order.calendar.id.toString());
        metadata.put("quantity", order.quantity.toString());
        params.put("metadata", metadata);

        return intent.update(params);
    }

    /**
     * Confirm a payment intent (if manual confirmation is required).
     *
     * @param paymentIntentId PaymentIntent ID
     * @return Confirmed PaymentIntent
     * @throws StripeException if Stripe API call fails
     */
    public PaymentIntent confirmPayment(String paymentIntentId) throws StripeException {
        LOG.infof("Confirming payment intent: %s", paymentIntentId);

        PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);

        if ("requires_confirmation".equals(intent.getStatus())) {
            Map<String, Object> params = new HashMap<>();
            return intent.confirm(params);
        }

        LOG.infof("PaymentIntent %s does not require confirmation (status: %s)",
            paymentIntentId, intent.getStatus());
        return intent;
    }

    /**
     * Retrieve a PaymentIntent by ID.
     *
     * @param paymentIntentId PaymentIntent ID
     * @return PaymentIntent
     * @throws StripeException if Stripe API call fails
     */
    public PaymentIntent getPaymentIntent(String paymentIntentId) throws StripeException {
        LOG.debugf("Retrieving payment intent: %s", paymentIntentId);
        return PaymentIntent.retrieve(paymentIntentId);
    }

    /**
     * Cancel a PaymentIntent.
     *
     * @param paymentIntentId PaymentIntent ID
     * @return Cancelled PaymentIntent
     * @throws StripeException if Stripe API call fails
     */
    public PaymentIntent cancelPayment(String paymentIntentId) throws StripeException {
        LOG.infof("Cancelling payment intent: %s", paymentIntentId);
        PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
        return intent.cancel();
    }
}
