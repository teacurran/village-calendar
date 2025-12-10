package villagecompute.calendar.services;

import java.time.Duration;
import java.time.Instant;

/**
 * Retry strategy for delayed jobs using exponential backoff.
 * Formula: 5 seconds + N^4 seconds (capped at 7 days)
 */
public class DelayedJobRetryStrategy {

    private static final Duration MAX_RETRY_INTERVAL = Duration.ofDays(7);

    private DelayedJobRetryStrategy() {
        // Utility class, cannot be instantiated
    }

    /**
     * Calculate the next retry interval based on number of attempts.
     * Uses exponential backoff: 5 seconds + attempts^4 seconds.
     *
     * Example schedule:
     * - Attempt 1: 5 + 1 = 6 seconds
     * - Attempt 2: 5 + 16 = 21 seconds
     * - Attempt 3: 5 + 81 = 86 seconds (~1.4 minutes)
     * - Attempt 4: 5 + 256 = 261 seconds (~4.3 minutes)
     * - Attempt 5: 5 + 625 = 630 seconds (~10.5 minutes)
     * - Capped at 7 days for runaway jobs
     *
     * @param attempts Number of previous attempts
     * @return Instant when the job should be retried
     */
    public static Instant calculateNextRetryInterval(int attempts) {
        Duration baseInterval = Duration.ofSeconds(5);
        Duration nextInterval = baseInterval.plusSeconds((long) Math.pow(attempts, 4));
        Duration calculated = nextInterval.compareTo(MAX_RETRY_INTERVAL) > 0 ? MAX_RETRY_INTERVAL : nextInterval;
        return Instant.now().plus(calculated);
    }
}
