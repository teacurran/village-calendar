package villagecompute.calendar.api.rest;

import com.stripe.exception.StripeException;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import villagecompute.calendar.services.OrderService;
import villagecompute.calendar.services.PaymentService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Payment Resource - REST API for Stripe payment processing
 */
@Path("/payment")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PaymentResource {

    private static final Logger LOG = Logger.getLogger(PaymentResource.class);

    @Inject
    PaymentService paymentService;

    @Inject
    OrderService orderService;

    /**
     * Get Stripe configuration (publishable key) for frontend
     */
    @GET
    @Path("/config")
    public Response getConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("publishableKey", paymentService.getPublishableKey());
        return Response.ok(config).build();
    }

    /**
     * Create a PaymentIntent for checkout
     */
    @POST
    @Path("/create-payment-intent")
    public Response createPaymentIntent(PaymentIntentRequest request) {
        LOG.infof("Creating payment intent for amount: %s %s", request.amount, request.currency);

        try {
            // Generate a temporary order ID for the payment intent
            // The actual order will be created after payment succeeds
            String tempOrderId = "temp_" + UUID.randomUUID().toString();

            BigDecimal amount = BigDecimal.valueOf(request.amount);
            String currency = request.currency != null ? request.currency : "usd";

            Map<String, String> result = paymentService.createPaymentIntent(amount, currency, tempOrderId);

            return Response.ok(result).build();
        } catch (StripeException e) {
            LOG.errorf("Stripe error creating payment intent: %s", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        } catch (Exception e) {
            LOG.errorf("Error creating payment intent: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to create payment intent"))
                    .build();
        }
    }

    /**
     * Confirm payment and create order after successful Stripe payment
     */
    @POST
    @Path("/confirm-payment")
    @SuppressWarnings("unchecked")
    public Response confirmPayment(ConfirmPaymentRequest request) {
        LOG.infof("Confirming payment for PaymentIntent: %s", request.paymentIntentId);

        try {
            // Verify the payment intent status with Stripe
            var paymentIntent = paymentService.getPaymentIntent(request.paymentIntentId);

            if (!"succeeded".equals(paymentIntent.getStatus())) {
                LOG.warnf("PaymentIntent %s has status %s, expected 'succeeded'",
                        request.paymentIntentId, paymentIntent.getStatus());
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Payment not completed"))
                        .build();
            }

            // Extract order details from request
            Map<String, Object> orderDetails = request.orderDetails;
            if (orderDetails == null) {
                LOG.warn("No order details provided in confirm-payment request");
                orderDetails = new HashMap<>();
            }

            // Extract fields from orderDetails
            String email = (String) orderDetails.get("email");
            if (email == null || email.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Email is required"))
                        .build();
            }

            Map<String, Object> shippingAddress = (Map<String, Object>) orderDetails.get("shippingAddress");
            List<Map<String, Object>> items = (List<Map<String, Object>>) orderDetails.get("items");
            Double subtotal = orderDetails.get("subtotal") != null ?
                    ((Number) orderDetails.get("subtotal")).doubleValue() : null;
            Double shippingCost = orderDetails.get("shippingCost") != null ?
                    ((Number) orderDetails.get("shippingCost")).doubleValue() : null;
            Double taxAmount = orderDetails.get("taxAmount") != null ?
                    ((Number) orderDetails.get("taxAmount")).doubleValue() : null;
            Double totalAmount = orderDetails.get("totalAmount") != null ?
                    ((Number) orderDetails.get("totalAmount")).doubleValue() : null;

            // Process checkout and create orders in database
            String orderNumber = orderService.processCheckout(
                    request.paymentIntentId,
                    email,
                    shippingAddress,
                    items,
                    subtotal,
                    shippingCost,
                    taxAmount,
                    totalAmount
            );

            LOG.infof("Payment confirmed, order %s created for PaymentIntent %s",
                    orderNumber, request.paymentIntentId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("orderNumber", orderNumber);
            result.put("paymentIntentId", request.paymentIntentId);

            return Response.ok(result).build();
        } catch (StripeException e) {
            LOG.errorf("Stripe error confirming payment: %s", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        } catch (Exception e) {
            LOG.errorf(e, "Error confirming payment: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to confirm payment: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Request object for creating a PaymentIntent
     */
    public static class PaymentIntentRequest {
        public double amount;
        public String currency;
    }

    /**
     * Request object for confirming payment
     */
    public static class ConfirmPaymentRequest {
        public String paymentIntentId;
        public Map<String, Object> orderDetails;
    }
}
