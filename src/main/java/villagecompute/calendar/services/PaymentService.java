package villagecompute.calendar.services;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.data.models.DelayedJob;
import villagecompute.calendar.data.models.DelayedJobQueue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service for integrating with Stripe payment processing.
 * Handles PaymentIntent creation, updates, payment confirmations, and webhook processing.
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

    @Inject
    OrderService orderService;

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

    /**
     * Process successful payment from webhook.
     * Updates order status to PAID and enqueues order confirmation email.
     * Implements idempotent processing to handle duplicate webhook deliveries.
     *
     * @param paymentIntentId Stripe Payment Intent ID
     * @param chargeId Stripe Charge ID (optional)
     * @return true if payment was processed, false if already processed (idempotent)
     */
    @Transactional
    public boolean processPaymentSuccess(String paymentIntentId, String chargeId) {
        LOG.infof("Processing payment success for PaymentIntent: %s", paymentIntentId);

        // Find the order
        Optional<CalendarOrder> orderOpt = orderService.findByStripePaymentIntent(paymentIntentId);
        if (orderOpt.isEmpty()) {
            // Order may not be created yet (race condition between frontend order creation and webhook)
            // Throw exception so webhook returns 500 and Stripe will retry automatically
            LOG.warnf("Order not found for PaymentIntent: %s (race condition - Stripe will retry)", paymentIntentId);
            throw new IllegalStateException("Order not found for PaymentIntent: " + paymentIntentId + " (Stripe will retry)");
        }

        CalendarOrder order = orderOpt.get();

        // Idempotent check: if already paid, skip processing
        if (CalendarOrder.STATUS_PAID.equals(order.status)) {
            LOG.infof("Order %s already marked as PAID, skipping update (idempotent)", order.id);
            return false;
        }

        // Update order status to PAID
        order.status = CalendarOrder.STATUS_PAID;
        order.paidAt = Instant.now();

        // Store charge ID if available
        if (chargeId != null) {
            order.stripeChargeId = chargeId;
        }

        // Add payment success note
        String timestamp = Instant.now().toString();
        String noteEntry = String.format("[%s] Payment succeeded via PaymentIntent %s\n", timestamp, paymentIntentId);
        order.notes = order.notes == null ? noteEntry : order.notes + noteEntry;

        order.persist();
        LOG.infof("Order %s marked as PAID", order.id);

        // Enqueue confirmation email (within same transaction for consistency)
        DelayedJob emailJob = DelayedJob.createDelayedJob(
            order.id.toString(),
            DelayedJobQueue.EMAIL_ORDER_CONFIRMATION,
            Instant.now() // Send immediately
        );
        LOG.infof("Enqueued order confirmation email job %s for order %s", emailJob.id, order.id);

        return true;
    }

    /**
     * Process refund via Stripe API and update order record.
     * Creates a refund in Stripe and updates the order notes.
     *
     * @param paymentIntentId Stripe Payment Intent ID to refund
     * @param amountInCents Amount to refund in cents (null for full refund)
     * @param reason Refund reason
     * @return Stripe Refund object
     * @throws StripeException if Stripe API call fails
     */
    public Refund processRefund(String paymentIntentId, Long amountInCents, String reason) throws StripeException {
        LOG.infof("Processing refund for PaymentIntent: %s (amount: %s cents)", paymentIntentId, amountInCents);

        // Find the order
        Optional<CalendarOrder> orderOpt = orderService.findByStripePaymentIntent(paymentIntentId);
        if (orderOpt.isEmpty()) {
            LOG.errorf("Order not found for PaymentIntent: %s", paymentIntentId);
            throw new IllegalStateException("Order not found for PaymentIntent: " + paymentIntentId);
        }

        CalendarOrder order = orderOpt.get();

        // Build refund parameters
        RefundCreateParams.Builder paramsBuilder = RefundCreateParams.builder()
            .setPaymentIntent(paymentIntentId);

        if (amountInCents != null) {
            paramsBuilder.setAmount(amountInCents);
        }

        if (reason != null) {
            RefundCreateParams.Reason refundReason = switch (reason.toLowerCase()) {
                case "duplicate" -> RefundCreateParams.Reason.DUPLICATE;
                case "fraudulent" -> RefundCreateParams.Reason.FRAUDULENT;
                default -> RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER;
            };
            paramsBuilder.setReason(refundReason);
        }

        // Create refund via Stripe API
        Refund refund = Refund.create(paramsBuilder.build());

        // Update order notes with refund information
        String timestamp = Instant.now().toString();
        BigDecimal refundAmount = BigDecimal.valueOf(refund.getAmount()).divide(BigDecimal.valueOf(100));
        String noteEntry = String.format("[%s] Refund processed: $%.2f (Refund ID: %s, Reason: %s)\n",
            timestamp, refundAmount, refund.getId(), refund.getReason());
        order.notes = order.notes == null ? noteEntry : order.notes + noteEntry;
        order.persist();

        LOG.infof("Refund %s created for order %s, amount: $%.2f", refund.getId(), order.id, refundAmount);

        return refund;
    }

    /**
     * Process refund webhook event from Stripe.
     * Updates order notes with refund information.
     * This is called when Stripe sends a charge.refunded webhook event.
     *
     * @param chargeId Stripe Charge ID
     * @param refundId Stripe Refund ID
     * @param amountRefunded Amount refunded in cents
     */
    @Transactional
    public void processRefundWebhook(String chargeId, String refundId, Long amountRefunded) {
        LOG.infof("Processing refund webhook for Charge: %s (Refund: %s, Amount: %s cents)",
            chargeId, refundId, amountRefunded);

        // Find order by charge ID
        Optional<CalendarOrder> orderOpt = CalendarOrder.find("stripeChargeId", chargeId).firstResultOptional();
        if (orderOpt.isEmpty()) {
            LOG.warnf("Order not found for Charge ID: %s (may be a direct refund not linked to our system)", chargeId);
            return;
        }

        CalendarOrder order = orderOpt.get();

        // Check if refund already recorded (idempotent)
        if (order.notes != null && order.notes.contains("Refund ID: " + refundId)) {
            LOG.infof("Refund %s already recorded for order %s, skipping (idempotent)", refundId, order.id);
            return;
        }

        // Add refund note
        String timestamp = Instant.now().toString();
        BigDecimal refundAmount = BigDecimal.valueOf(amountRefunded).divide(BigDecimal.valueOf(100));
        String noteEntry = String.format("[%s] Refund received via webhook: $%.2f (Refund ID: %s, Charge: %s)\n",
            timestamp, refundAmount, refundId, chargeId);
        order.notes = order.notes == null ? noteEntry : order.notes + noteEntry;
        order.persist();

        LOG.infof("Recorded refund %s for order %s, amount: $%.2f", refundId, order.id, refundAmount);
    }
}
