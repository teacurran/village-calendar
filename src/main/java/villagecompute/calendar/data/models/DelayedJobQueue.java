package villagecompute.calendar.data.models;

/**
 * Enum defining job queue types with priorities.
 * Higher priority jobs are processed first.
 */
public enum DelayedJobQueue {
    EMAIL_ORDER_CONFIRMATION(10),
    EMAIL_SHIPPING_NOTIFICATION(10),
    EMAIL_GENERAL(5);

    private final int priority;

    DelayedJobQueue(int priority) {
        this.priority = priority;
    }

    public int getPriority() {
        return priority;
    }
}
