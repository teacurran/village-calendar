package villagecompute.calendar.exceptions;

/**
 * Exception thrown when payment processing fails (e.g., Stripe API errors).
 */
public class PaymentException extends ApplicationException {

    public PaymentException(String message) {
        super(message);
    }

    public PaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
