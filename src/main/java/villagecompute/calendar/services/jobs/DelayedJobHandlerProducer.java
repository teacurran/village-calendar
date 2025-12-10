package villagecompute.calendar.services.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import villagecompute.calendar.data.models.DelayedJobQueue;

import java.util.Map;

/**
 * CDI producer for mapping DelayedJobQueue types to their handlers.
 * This allows the DelayedJobService to look up the correct handler
 * for each queue type at runtime.
 */
@ApplicationScoped
public class DelayedJobHandlerProducer {

    @Inject
    OrderEmailJobHandler orderEmailJobHandler;

    @Inject
    ShippingNotificationJobHandler shippingNotificationJobHandler;

    @Inject
    OrderCancellationJobHandler orderCancellationJobHandler;

    /**
     * Produce a map of queue types to their handlers.
     *
     * @return Map of DelayedJobQueue to DelayedJobHandler
     */
    @Produces
    @ApplicationScoped
    public Map<DelayedJobQueue, DelayedJobHandler> delayedJobHandlers() {
        return Map.of(
            DelayedJobQueue.EMAIL_ORDER_CONFIRMATION, orderEmailJobHandler,
            DelayedJobQueue.EMAIL_SHIPPING_NOTIFICATION, shippingNotificationJobHandler,
            DelayedJobQueue.EMAIL_GENERAL, orderCancellationJobHandler
        );
    }
}
