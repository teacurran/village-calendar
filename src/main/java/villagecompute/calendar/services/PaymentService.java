package villagecompute.calendar.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;

import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.services.jobs.OrderEmailJobHandler;

/**
 * Service for integrating with Stripe payment processing. Handles PaymentIntent creation, updates, payment
 * confirmations, and webhook processing.
 */
@ApplicationScoped
public class PaymentService {

    private static final Logger LOG = Logger.getLogger(PaymentService.class);

    /** Metadata key for order ID in Stripe payment intents and checkout sessions */
    private static final String METADATA_ORDER_ID = "orderId";

    @ConfigProperty(
            name = "stripe.api.key")
    String stripeApiKey;

    @ConfigProperty(
            name = "stripe.publishable.key")
    String stripePublishableKey;

    @ConfigProperty(
            name = "stripe.webhook.secret",
            defaultValue = "")
    String webhookSecret;

    // Shipping rates (in cents) - matches ShippingService defaults
    @ConfigProperty(
            name = "calendar.shipping.domestic.standard",
            defaultValue = "5.99")
    String domesticStandardRate;

    @ConfigProperty(
            name = "calendar.shipping.domestic.priority",
            defaultValue = "9.99")
    String domesticPriorityRate;

    @ConfigProperty(
            name = "calendar.shipping.domestic.express",
            defaultValue = "14.99")
    String domesticExpressRate;

    // Tax configuration
    @ConfigProperty(
            name = "stripe.automatic.tax.enabled",
            defaultValue = "true")
    boolean automaticTaxEnabled;

    @Inject
    OrderService orderService;

    @Inject
    DelayedJobService delayedJobService;

    @PostConstruct
    void init() {
        initStripeApiKey(stripeApiKey);
        LOG.info("Payment service initialized with Stripe API");
    }

    /** Thread-safe static initialization of Stripe API key */
    private static synchronized void initStripeApiKey(String apiKey) {
        Stripe.apiKey = apiKey;
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
     * Create a PaymentIntent for an order. Returns client secret and payment intent ID for frontend processing.
     *
     * @param amount
     *            Total amount to charge
     * @param currency
     *            Currency code (default: "usd")
     * @param orderId
     *            Order ID for metadata
     * @return Map with clientSecret and paymentIntentId
     * @throws StripeException
     *             if Stripe API call fails
     */
    public Map<String, String> createPaymentIntent(BigDecimal amount, String currency, String orderId)
            throws StripeException {
        return createPaymentIntent(amount, currency, orderId, null, null, null, null);
    }

    /**
     * Create a PaymentIntent for an order with full breakdown for Stripe reporting. Includes subtotal, tax, and
     * shipping in metadata for tax reporting.
     *
     * @param amount
     *            Total amount to charge
     * @param currency
     *            Currency code (default: "usd")
     * @param orderId
     *            Order ID for metadata
     * @param subtotal
     *            Subtotal before tax and shipping (optional)
     * @param taxAmount
     *            Tax amount (optional)
     * @param shippingCost
     *            Shipping cost (optional)
     * @param orderNumber
     *            Human-readable order number (optional)
     * @return Map with clientSecret and paymentIntentId
     * @throws StripeException
     *             if Stripe API call fails
     */
    public Map<String, String> createPaymentIntent(BigDecimal amount, String currency, String orderId,
            BigDecimal subtotal, BigDecimal taxAmount, BigDecimal shippingCost, String orderNumber)
            throws StripeException {
        LOG.infof("Creating payment intent for order %s, amount $%.2f (tax: $%.2f)", orderId, amount,
                taxAmount != null ? taxAmount : BigDecimal.ZERO);

        // Convert BigDecimal to cents (Stripe uses smallest currency unit)
        long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValue();

        // Generate idempotency key to prevent duplicate charges
        String idempotencyKey = "order_" + orderId + "_" + System.currentTimeMillis();

        // Build description
        String description = orderNumber != null ? String.format("Village Calendar Order %s", orderNumber)
                : String.format("Village Calendar Order");

        PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder().setAmount(amountInCents)
                .setCurrency(currency != null ? currency : "usd").setDescription(description)
                .putMetadata(METADATA_ORDER_ID, orderId).setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build());

        // Add order number to metadata if available
        if (orderNumber != null) {
            paramsBuilder.putMetadata("orderNumber", orderNumber);
        }

        // Add financial breakdown to metadata for Stripe reporting
        if (subtotal != null) {
            long subtotalCents = subtotal.multiply(BigDecimal.valueOf(100)).longValue();
            paramsBuilder.putMetadata("subtotal", String.valueOf(subtotalCents));
        }
        if (taxAmount != null && taxAmount.compareTo(BigDecimal.ZERO) > 0) {
            long taxCents = taxAmount.multiply(BigDecimal.valueOf(100)).longValue();
            paramsBuilder.putMetadata("tax_amount", String.valueOf(taxCents));
        }
        if (shippingCost != null && shippingCost.compareTo(BigDecimal.ZERO) > 0) {
            long shippingCents = shippingCost.multiply(BigDecimal.valueOf(100)).longValue();
            paramsBuilder.putMetadata("shipping_cost", String.valueOf(shippingCents));
        }

        PaymentIntentCreateParams params = paramsBuilder.build();

        // Create PaymentIntent with idempotency key
        PaymentIntent intent = PaymentIntent.create(params,
                com.stripe.net.RequestOptions.builder().setIdempotencyKey(idempotencyKey).build());

        Map<String, String> response = new HashMap<>();
        response.put("clientSecret", intent.getClientSecret());
        response.put("paymentIntentId", intent.getId());

        LOG.infof("Created PaymentIntent %s for order %s (tax: %s cents)", intent.getId(), orderId,
                taxAmount != null ? taxAmount.multiply(BigDecimal.valueOf(100)).longValue() : 0);

        return response;
    }

    /**
     * Update a PaymentIntent with order details. Used to add order information after the PaymentIntent is created.
     *
     * @param paymentIntentId
     *            PaymentIntent ID
     * @param order
     *            Order details
     * @return Updated PaymentIntent
     * @throws StripeException
     *             if Stripe API call fails
     */
    public PaymentIntent updatePaymentIntent(String paymentIntentId, CalendarOrder order) throws StripeException {
        LOG.infof("Updating PaymentIntent %s with order details", paymentIntentId);

        PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);

        Map<String, Object> params = new HashMap<>();
        // Build description from items
        String description = order.items.isEmpty() ? "Calendar Order"
                : String.format("Calendar Order - %s", order.items.get(0).description);
        params.put("description", description);

        Map<String, String> metadata = new HashMap<>();
        metadata.put(METADATA_ORDER_ID, order.id.toString());
        metadata.put("itemCount", String.valueOf(order.items.size()));
        metadata.put("totalQuantity", String.valueOf(order.getTotalItemCount()));
        params.put("metadata", metadata);

        return intent.update(params);
    }

    /**
     * Confirm a payment intent (if manual confirmation is required).
     *
     * @param paymentIntentId
     *            PaymentIntent ID
     * @return Confirmed PaymentIntent
     * @throws StripeException
     *             if Stripe API call fails
     */
    public PaymentIntent confirmPayment(String paymentIntentId) throws StripeException {
        LOG.infof("Confirming payment intent: %s", paymentIntentId);

        PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);

        if ("requires_confirmation".equals(intent.getStatus())) {
            Map<String, Object> params = new HashMap<>();
            return intent.confirm(params);
        }

        LOG.infof("PaymentIntent %s does not require confirmation (status: %s)", paymentIntentId, intent.getStatus());
        return intent;
    }

    /**
     * Retrieve a PaymentIntent by ID.
     *
     * @param paymentIntentId
     *            PaymentIntent ID
     * @return PaymentIntent
     * @throws StripeException
     *             if Stripe API call fails
     */
    public PaymentIntent getPaymentIntent(String paymentIntentId) throws StripeException {
        LOG.debugf("Retrieving payment intent: %s", paymentIntentId);
        return PaymentIntent.retrieve(paymentIntentId);
    }

    /**
     * Cancel a PaymentIntent.
     *
     * @param paymentIntentId
     *            PaymentIntent ID
     * @return Cancelled PaymentIntent
     * @throws StripeException
     *             if Stripe API call fails
     */
    public PaymentIntent cancelPayment(String paymentIntentId) throws StripeException {
        LOG.infof("Cancelling payment intent: %s", paymentIntentId);
        PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
        return intent.cancel();
    }

    /**
     * Process successful payment from webhook. Updates order status to PAID and enqueues order confirmation email.
     * Implements idempotent processing to handle duplicate webhook deliveries.
     *
     * @param paymentIntentId
     *            Stripe Payment Intent ID
     * @param chargeId
     *            Stripe Charge ID (optional)
     * @return true if payment was processed, false if already processed (idempotent)
     */
    @Transactional
    public boolean processPaymentSuccess(String paymentIntentId, String chargeId) {
        LOG.infof("Processing payment success for PaymentIntent: %s", paymentIntentId);

        // Find the order by PaymentIntent ID
        Optional<CalendarOrder> orderOpt = orderService.findByStripePaymentIntent(paymentIntentId);
        if (orderOpt.isEmpty()) {
            // When using Stripe Checkout, checkout.session.completed is the primary event.
            // payment_intent.succeeded may arrive before the order's stripePaymentIntentId is set.
            // This is normal - checkout.session.completed will handle the order update.
            // Return success to acknowledge the webhook (no retry needed).
            LOG.infof("Order not found for PaymentIntent: %s (likely using Checkout flow -"
                    + " checkout.session.completed will handle)", paymentIntentId);
            return false;
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
        String noteEntry = String.format("[%s] Payment succeeded via PaymentIntent %s%n", timestamp, paymentIntentId);
        order.notes = order.notes == null ? noteEntry : order.notes + noteEntry;

        order.persist();
        LOG.infof("Order %s marked as PAID", order.id);

        // Enqueue confirmation email (within same transaction for consistency)
        try {
            delayedJobService.enqueue(OrderEmailJobHandler.class, order.id.toString());
            LOG.infof("Enqueued order confirmation email job for order %s", order.id);
        } catch (Exception e) {
            // Log but don't fail - scheduled processor will pick it up
            LOG.error("Failed to enqueue email job for order " + order.id, e);
        }

        return true;
    }

    /**
     * Process refund via Stripe API and update order record. Creates a refund in Stripe and updates the order notes.
     *
     * @param paymentIntentId
     *            Stripe Payment Intent ID to refund
     * @param amountInCents
     *            Amount to refund in cents (null for full refund)
     * @param reason
     *            Refund reason
     * @return Stripe Refund object
     * @throws StripeException
     *             if Stripe API call fails
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
        RefundCreateParams.Builder paramsBuilder = RefundCreateParams.builder().setPaymentIntent(paymentIntentId);

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
        String noteEntry = String.format("[%s] Refund processed: $%.2f (Refund ID: %s, Reason: %s)%n", timestamp,
                refundAmount, refund.getId(), refund.getReason());
        order.notes = order.notes == null ? noteEntry : order.notes + noteEntry;
        order.persist();

        LOG.infof("Refund %s created for order %s, amount: $%.2f", refund.getId(), order.id, refundAmount);

        return refund;
    }

    /**
     * Process refund webhook event from Stripe. Updates order notes with refund information. This is called when Stripe
     * sends a charge.refunded webhook event.
     *
     * @param chargeId
     *            Stripe Charge ID
     * @param refundId
     *            Stripe Refund ID
     * @param amountRefunded
     *            Amount refunded in cents
     */
    @Transactional
    public void processRefundWebhook(String chargeId, String refundId, Long amountRefunded) {
        LOG.infof("Processing refund webhook for Charge: %s (Refund: %s, Amount: %s cents)", chargeId, refundId,
                amountRefunded);

        // Find order by charge ID
        Optional<CalendarOrder> orderOpt = CalendarOrder.find("stripeChargeId", chargeId).firstResultOptional();
        if (orderOpt.isEmpty()) {
            LOG.warnf("Order not found for Charge ID: %s (may be a direct refund not linked to our" + " system)",
                    chargeId);
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
        String noteEntry = String.format("[%s] Refund received via webhook: $%.2f (Refund ID: %s, Charge: %s)%n",
                timestamp, refundAmount, refundId, chargeId);
        order.notes = order.notes == null ? noteEntry : order.notes + noteEntry;
        order.persist();

        LOG.infof("Recorded refund %s for order %s, amount: $%.2f", refundId, order.id, refundAmount);
    }

    // ========================================
    // STRIPE CHECKOUT SESSION (Embedded)
    // ========================================

    /**
     * Create a Stripe Checkout Session for embedded checkout. Returns the client secret for the embedded checkout
     * component.
     *
     * @param orderId
     *            Order ID for metadata
     * @param orderNumber
     *            Human-readable order number
     * @param lineItems
     *            List of line items with name, quantity, and unit amount in cents
     * @param customerEmail
     *            Customer email for receipts
     * @param successUrl
     *            URL to redirect after successful payment
     * @param cancelUrl
     *            URL to redirect if customer cancels
     * @param shippingRequired
     *            Whether to collect shipping address
     * @return Map with clientSecret for embedded checkout
     * @throws StripeException
     *             if Stripe API call fails
     */
    public Map<String, String> createCheckoutSession(String orderId, String orderNumber,
            List<CheckoutLineItem> lineItems, String customerEmail, String successUrl, String cancelUrl,
            boolean shippingRequired) throws StripeException {
        LOG.infof("Creating Checkout Session for order %s", orderId);

        SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT).setUiMode(SessionCreateParams.UiMode.EMBEDDED)
                .setReturnUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}").putMetadata(METADATA_ORDER_ID, orderId);

        if (orderNumber != null) {
            paramsBuilder.putMetadata("orderNumber", orderNumber);
        }

        // Add line items
        for (CheckoutLineItem item : lineItems) {
            paramsBuilder.addLineItem(SessionCreateParams.LineItem.builder()
                    .setPriceData(SessionCreateParams.LineItem.PriceData.builder().setCurrency("usd")
                            .setUnitAmount(item.unitAmountCents)
                            .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                    .setName(item.name).setDescription(item.description).build())
                            .build())
                    .setQuantity(item.quantity).build());
        }

        // Customer email for receipts
        if (customerEmail != null && !customerEmail.isEmpty()) {
            paramsBuilder.setCustomerEmail(customerEmail);
        }

        // Shipping address collection and shipping options
        if (shippingRequired) {
            paramsBuilder.setShippingAddressCollection(SessionCreateParams.ShippingAddressCollection.builder()
                    .addAllowedCountry(SessionCreateParams.ShippingAddressCollection.AllowedCountry.US)
                    .addAllowedCountry(SessionCreateParams.ShippingAddressCollection.AllowedCountry.CA).build());

            // Add shipping rate options
            long standardRateCents = Math.round(Double.parseDouble(domesticStandardRate) * 100);
            long priorityRateCents = Math.round(Double.parseDouble(domesticPriorityRate) * 100);
            long expressRateCents = Math.round(Double.parseDouble(domesticExpressRate) * 100);

            paramsBuilder.addShippingOption(SessionCreateParams.ShippingOption.builder()
                    .setShippingRateData(SessionCreateParams.ShippingOption.ShippingRateData.builder()
                            .setType(SessionCreateParams.ShippingOption.ShippingRateData.Type.FIXED_AMOUNT)
                            .setFixedAmount(SessionCreateParams.ShippingOption.ShippingRateData.FixedAmount.builder()
                                    .setAmount(standardRateCents).setCurrency("usd").build())
                            .setDisplayName("Standard Shipping")
                            .setDeliveryEstimate(SessionCreateParams.ShippingOption.ShippingRateData.DeliveryEstimate
                                    .builder()
                                    .setMinimum(
                                            SessionCreateParams.ShippingOption.ShippingRateData.DeliveryEstimate.Minimum
                                                    .builder()
                                                    .setUnit(
                                                            SessionCreateParams.ShippingOption.ShippingRateData.DeliveryEstimate.Minimum.Unit.BUSINESS_DAY)
                                                    .setValue(5L).build())
                                    .setMaximum(
                                            SessionCreateParams.ShippingOption.ShippingRateData.DeliveryEstimate.Maximum
                                                    .builder()
                                                    .setUnit(
                                                            SessionCreateParams.ShippingOption.ShippingRateData.DeliveryEstimate.Maximum.Unit.BUSINESS_DAY)
                                                    .setValue(7L).build())
                                    .build())
                            .build())
                    .build());

            paramsBuilder.addShippingOption(SessionCreateParams.ShippingOption.builder()
                    .setShippingRateData(SessionCreateParams.ShippingOption.ShippingRateData.builder()
                            .setType(SessionCreateParams.ShippingOption.ShippingRateData.Type.FIXED_AMOUNT)
                            .setFixedAmount(SessionCreateParams.ShippingOption.ShippingRateData.FixedAmount.builder()
                                    .setAmount(priorityRateCents).setCurrency("usd").build())
                            .setDisplayName("Priority Shipping")
                            .setDeliveryEstimate(SessionCreateParams.ShippingOption.ShippingRateData.DeliveryEstimate
                                    .builder()
                                    .setMinimum(
                                            SessionCreateParams.ShippingOption.ShippingRateData.DeliveryEstimate.Minimum
                                                    .builder()
                                                    .setUnit(
                                                            SessionCreateParams.ShippingOption.ShippingRateData.DeliveryEstimate.Minimum.Unit.BUSINESS_DAY)
                                                    .setValue(2L).build())
                                    .setMaximum(
                                            SessionCreateParams.ShippingOption.ShippingRateData.DeliveryEstimate.Maximum
                                                    .builder()
                                                    .setUnit(
                                                            SessionCreateParams.ShippingOption.ShippingRateData.DeliveryEstimate.Maximum.Unit.BUSINESS_DAY)
                                                    .setValue(3L).build())
                                    .build())
                            .build())
                    .build());

            paramsBuilder.addShippingOption(SessionCreateParams.ShippingOption.builder()
                    .setShippingRateData(SessionCreateParams.ShippingOption.ShippingRateData.builder()
                            .setType(SessionCreateParams.ShippingOption.ShippingRateData.Type.FIXED_AMOUNT)
                            .setFixedAmount(SessionCreateParams.ShippingOption.ShippingRateData.FixedAmount.builder()
                                    .setAmount(expressRateCents).setCurrency("usd").build())
                            .setDisplayName("Express Shipping")
                            .setDeliveryEstimate(SessionCreateParams.ShippingOption.ShippingRateData.DeliveryEstimate
                                    .builder()
                                    .setMinimum(
                                            SessionCreateParams.ShippingOption.ShippingRateData.DeliveryEstimate.Minimum
                                                    .builder()
                                                    .setUnit(
                                                            SessionCreateParams.ShippingOption.ShippingRateData.DeliveryEstimate.Minimum.Unit.BUSINESS_DAY)
                                                    .setValue(1L).build())
                                    .setMaximum(
                                            SessionCreateParams.ShippingOption.ShippingRateData.DeliveryEstimate.Maximum
                                                    .builder()
                                                    .setUnit(
                                                            SessionCreateParams.ShippingOption.ShippingRateData.DeliveryEstimate.Maximum.Unit.BUSINESS_DAY)
                                                    .setValue(2L).build())
                                    .build())
                            .build())
                    .build());

            LOG.infof("Added shipping options: Standard $%.2f, Priority $%.2f, Express $%.2f",
                    standardRateCents / 100.0, priorityRateCents / 100.0, expressRateCents / 100.0);
        }

        // Enable automatic tax calculation if configured
        if (automaticTaxEnabled) {
            paramsBuilder.setAutomaticTax(SessionCreateParams.AutomaticTax.builder().setEnabled(true).build());
            LOG.info("Automatic tax calculation enabled for checkout session");
        }

        Session session = Session.create(paramsBuilder.build());

        Map<String, String> response = new HashMap<>();
        response.put("clientSecret", session.getClientSecret());
        response.put("sessionId", session.getId());

        LOG.infof("Created Checkout Session %s for order %s", session.getId(), orderId);

        return response;
    }

    /**
     * Retrieve a Checkout Session by ID.
     *
     * @param sessionId
     *            Checkout Session ID
     * @return Session object
     * @throws StripeException
     *             if Stripe API call fails
     */
    public Session getCheckoutSession(String sessionId) throws StripeException {
        return Session.retrieve(sessionId);
    }

    /**
     * Process successful checkout from webhook using session ID. Retrieves the session from Stripe API. Called when
     * checkout.session.completed event is received.
     *
     * @param sessionId
     *            Stripe Checkout Session ID
     * @return true if processed, false if already processed (idempotent)
     */
    @Transactional
    public boolean processCheckoutSessionCompleted(String sessionId) throws StripeException {
        LOG.infof("Processing checkout.session.completed for session: %s", sessionId);
        Session session = Session.retrieve(sessionId);
        return processCheckoutSessionCompleted(session);
    }

    /**
     * Process successful checkout from webhook using Session object. Called with the session data deserialized from the
     * webhook event.
     *
     * @param session
     *            Stripe Checkout Session object
     * @return true if processed, false if already processed (idempotent)
     */
    @Transactional
    public boolean processCheckoutSessionCompleted(Session session) {
        String sessionId = session.getId();
        LOG.infof("Processing checkout.session.completed for session: %s", sessionId);

        Map<String, String> metadata = session.getMetadata();
        String orderId = metadata != null ? metadata.get(METADATA_ORDER_ID) : null;

        if (orderId == null) {
            LOG.warnf("No orderId in session metadata for session: %s", sessionId);
            return false;
        }

        // Find the order
        java.util.UUID orderUuid;
        try {
            orderUuid = java.util.UUID.fromString(orderId);
        } catch (IllegalArgumentException e) {
            LOG.errorf("Invalid orderId format in session %s: %s", sessionId, orderId);
            return false;
        }
        Optional<CalendarOrder> orderOpt = orderService.getOrderById(orderUuid);
        if (orderOpt.isEmpty()) {
            LOG.warnf("Order not found for session %s, orderId: %s", sessionId, orderId);
            throw new IllegalStateException("Order not found for session: " + sessionId);
        }

        CalendarOrder order = orderOpt.get();

        // Idempotent check
        if (CalendarOrder.STATUS_PAID.equals(order.status)) {
            LOG.infof("Order %s already marked as PAID, skipping (idempotent)", order.id);
            return false;
        }

        // Update order with session details
        order.status = CalendarOrder.STATUS_PAID;
        order.paidAt = Instant.now();
        order.stripePaymentIntentId = session.getPaymentIntent();

        // Update totals from Stripe session (which includes shipping and tax)
        if (session.getAmountTotal() != null) {
            order.totalPrice = BigDecimal.valueOf(session.getAmountTotal()).divide(BigDecimal.valueOf(100));
        }
        if (session.getAmountSubtotal() != null) {
            order.subtotal = BigDecimal.valueOf(session.getAmountSubtotal()).divide(BigDecimal.valueOf(100));
        }
        if (session.getTotalDetails() != null) {
            if (session.getTotalDetails().getAmountShipping() != null) {
                order.shippingCost = BigDecimal.valueOf(session.getTotalDetails().getAmountShipping())
                        .divide(BigDecimal.valueOf(100));
            }
            if (session.getTotalDetails().getAmountTax() != null) {
                order.taxAmount = BigDecimal.valueOf(session.getTotalDetails().getAmountTax())
                        .divide(BigDecimal.valueOf(100));
            }
        }

        LOG.infof("Updated order totals from Stripe: subtotal=$%.2f, shipping=$%.2f, tax=$%.2f," + " total=$%.2f",
                order.subtotal, order.shippingCost, order.taxAmount, order.totalPrice);

        // Always capture customer email from session (required for confirmation emails)
        if (session.getCustomerEmail() != null && !session.getCustomerEmail().isEmpty()) {
            order.customerEmail = session.getCustomerEmail();
            LOG.infof("Set customer email for order %s: %s", order.id, order.customerEmail);
        } else if (session.getCustomerDetails() != null && session.getCustomerDetails().getEmail() != null) {
            // Fallback to customer details email
            order.customerEmail = session.getCustomerDetails().getEmail();
            LOG.infof("Set customer email from customer details for order %s: %s", order.id, order.customerEmail);
        }

        // Store shipping address if collected (as JSON)
        if (session.getCollectedInformation() != null
                && session.getCollectedInformation().getShippingDetails() != null) {
            var shipping = session.getCollectedInformation().getShippingDetails();
            var address = shipping.getAddress();
            if (address != null) {
                // Store as JSON in the shippingAddress field
                // Format to match frontend expectations (firstName/lastName, address1, etc.)
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                try {
                    Map<String, Object> addressMap = new HashMap<>();

                    // Parse name into firstName/lastName (Stripe only gives full name)
                    String fullName = shipping.getName();
                    if (fullName != null && !fullName.isEmpty()) {
                        String[] nameParts = fullName.trim().split("\\s+", 2);
                        addressMap.put("firstName", nameParts[0]);
                        addressMap.put("lastName", nameParts.length > 1 ? nameParts[1] : "");
                        addressMap.put("name", fullName); // Keep original for reference
                    }

                    // Map Stripe field names to frontend expected names
                    addressMap.put("address1", address.getLine1());
                    addressMap.put("line1", address.getLine1()); // Keep both formats
                    addressMap.put("address2", address.getLine2());
                    addressMap.put("line2", address.getLine2());
                    addressMap.put("city", address.getCity());
                    addressMap.put("state", address.getState());
                    addressMap.put("postalCode", address.getPostalCode());
                    addressMap.put("country", address.getCountry());

                    order.shippingAddress = mapper.valueToTree(addressMap);

                    LOG.infof("Stored shipping address for order %s: %s, %s %s", order.id, address.getLine1(),
                            address.getCity(), address.getState());
                } catch (Exception e) {
                    LOG.warnf("Failed to store shipping address for order %s: %s", order.id, e.getMessage());
                }
            }
        }

        // Add note
        String timestamp = Instant.now().toString();
        String noteEntry = String.format("[%s] Payment succeeded via Checkout Session %s%n", timestamp, sessionId);
        order.notes = order.notes == null ? noteEntry : order.notes + noteEntry;

        order.persist();
        LOG.infof("Order %s marked as PAID via Checkout Session", order.id);

        // Enqueue confirmation email
        try {
            delayedJobService.enqueue(OrderEmailJobHandler.class, order.id.toString());
            LOG.infof("Enqueued order confirmation email job for order %s", order.id);
        } catch (Exception e) {
            // Log but don't fail - scheduled processor will pick it up
            LOG.error("Failed to enqueue email job for order " + order.id, e);
        }

        return true;
    }

    /** Line item for checkout session */
    public static class CheckoutLineItem {
        public String name;
        public String description;
        public long unitAmountCents;
        public long quantity;

        public CheckoutLineItem(String name, String description, long unitAmountCents, long quantity) {
            this.name = name;
            this.description = description;
            this.unitAmountCents = unitAmountCents;
            this.quantity = quantity;
        }
    }
}
