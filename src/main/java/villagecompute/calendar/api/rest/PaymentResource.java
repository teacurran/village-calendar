package villagecompute.calendar.api.rest;

import com.stripe.exception.StripeException;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import villagecompute.calendar.services.PaymentService;

import java.math.BigDecimal;
import java.util.HashMap;
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

            // Generate order number
            String orderNumber = "VC-" + System.currentTimeMillis();

            // TODO: Create order record in database with order details
            // For now, just return success with order number
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
            LOG.errorf("Error confirming payment: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to confirm payment"))
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
