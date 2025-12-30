package villagecompute.calendar.integration.stripe;

import java.math.BigDecimal;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;

import villagecompute.calendar.data.models.CalendarOrder;

/**
 * Service for integrating with Stripe Checkout Sessions. Handles checkout session creation,
 * retrieval, and webhook signature validation.
 *
 * <p>This service focuses on Stripe Checkout (hosted payment pages), which delegates payment form
 * rendering and card data handling to Stripe, ensuring PCI DSS compliance.
 *
 * <p>For PaymentIntent-based flows, see PaymentService.
 */
@ApplicationScoped
public class StripeService {

    private static final Logger LOG = Logger.getLogger(StripeService.class);

    @ConfigProperty(name = "stripe.api.key")
    String stripeApiKey;

    @ConfigProperty(name = "stripe.webhook.secret")
    String webhookSecret;

    @PostConstruct
    void init() {
        // Initialize Stripe SDK with API key
        // Note: Stripe.apiKey is static, so this may be redundant if PaymentService already sets it
        // However, this makes StripeService independently usable
        Stripe.apiKey = stripeApiKey;
        LOG.info("StripeService initialized with Stripe Checkout API");
    }

    /**
     * Create a Stripe Checkout Session for an order.
     *
     * <p>Returns a session with a checkout URL that redirects the customer to Stripe's hosted
     * payment page. After payment, Stripe redirects to success/cancel URLs.
     *
     * @param order The order to create a checkout session for
     * @param successUrl URL to redirect to after successful payment
     * @param cancelUrl URL to redirect to if user cancels payment
     * @return Stripe Checkout Session with checkout URL
     * @throws StripeException if Stripe API call fails
     */
    public Session createCheckoutSession(CalendarOrder order, String successUrl, String cancelUrl)
            throws StripeException {
        LOG.infof("Creating Stripe Checkout Session for order %s", order.id);

        // Convert price to cents (Stripe uses smallest currency unit)
        long amountInCents = order.totalPrice.multiply(BigDecimal.valueOf(100)).longValue();

        // Generate idempotency key to prevent duplicate session creation
        String idempotencyKey = "checkout_order_" + order.id + "_" + System.currentTimeMillis();

        // Build line items for the checkout session
        SessionCreateParams.LineItem lineItem =
                SessionCreateParams.LineItem.builder()
                        .setPriceData(
                                SessionCreateParams.LineItem.PriceData.builder()
                                        .setCurrency("usd")
                                        .setUnitAmount(amountInCents)
                                        .setProductData(
                                                SessionCreateParams.LineItem.PriceData.ProductData
                                                        .builder()
                                                        .setName(
                                                                "Custom Calendar: "
                                                                        + order.calendar.name)
                                                        .setDescription(
                                                                "Personalized printed calendar")
                                                        .build())
                                        .build())
                        .setQuantity(Long.valueOf(order.quantity))
                        .build();

        // Build checkout session parameters
        SessionCreateParams params =
                SessionCreateParams.builder()
                        .setMode(
                                SessionCreateParams.Mode
                                        .PAYMENT) // One-time payment (not subscription)
                        .setSuccessUrl(successUrl)
                        .setCancelUrl(cancelUrl)
                        .addLineItem(lineItem)
                        .putMetadata("order_id", order.id.toString())
                        .putMetadata("user_id", order.user.id.toString())
                        .putMetadata("calendar_id", order.calendar.id.toString())
                        .setClientReferenceId(
                                order.id.toString()) // For easy correlation in webhooks
                        .build();

        // Create checkout session with idempotency key
        Session session =
                Session.create(
                        params,
                        com.stripe.net.RequestOptions.builder()
                                .setIdempotencyKey(idempotencyKey)
                                .build());

        LOG.infof(
                "Created Stripe Checkout Session %s for order %s. Checkout URL: %s",
                session.getId(), order.id, session.getUrl());

        return session;
    }

    /**
     * Retrieve a Stripe Checkout Session by ID.
     *
     * <p>Useful for checking session status, payment status, and retrieving customer information
     * after checkout completion.
     *
     * @param sessionId Stripe Checkout Session ID (starts with "cs_")
     * @return Stripe Checkout Session
     * @throws StripeException if Stripe API call fails or session not found
     */
    public Session retrieveSession(String sessionId) throws StripeException {
        LOG.debugf("Retrieving Stripe Checkout Session: %s", sessionId);

        try {
            Session session = Session.retrieve(sessionId);
            LOG.debugf(
                    "Retrieved session %s with status: %s, payment_status: %s",
                    sessionId, session.getStatus(), session.getPaymentStatus());
            return session;
        } catch (StripeException e) {
            LOG.errorf(e, "Failed to retrieve Stripe Checkout Session %s", sessionId);
            throw e;
        }
    }

    /**
     * Validate a Stripe webhook signature.
     *
     * <p>Verifies that a webhook request actually came from Stripe by validating the HMAC signature
     * in the Stripe-Signature header. This prevents spoofing and ensures webhook integrity.
     *
     * <p>This method can be used as a reusable validation utility, though webhook validation is
     * already implemented in WebhookResource.
     *
     * @param payload Raw webhook request body (JSON string)
     * @param signatureHeader Value of the Stripe-Signature HTTP header
     * @return Parsed Stripe Event if signature is valid
     * @throws SignatureVerificationException if signature is invalid
     */
    public Event validateWebhookSignature(String payload, String signatureHeader)
            throws SignatureVerificationException {
        LOG.debugf("Validating Stripe webhook signature");

        try {
            // Stripe SDK verifies HMAC signature and parses the event
            Event event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);

            LOG.debugf(
                    "Webhook signature validated successfully. Event type: %s, Event ID: %s",
                    event.getType(), event.getId());

            return event;
        } catch (SignatureVerificationException e) {
            LOG.errorf(e, "Invalid Stripe webhook signature. Possible spoofing attempt.");
            throw e;
        }
    }

    /**
     * Get the webhook secret for external use. Useful for services that need to validate webhooks
     * independently.
     *
     * @return Stripe webhook secret
     */
    public String getWebhookSecret() {
        return webhookSecret;
    }
}
