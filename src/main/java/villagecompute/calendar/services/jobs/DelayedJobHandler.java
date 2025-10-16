package villagecompute.calendar.services.jobs;

/**
 * Interface for DelayedJob handlers.
 * Implementations process specific job types (e.g., email sending).
 */
public interface DelayedJobHandler {
    /**
     * Execute the job.
     *
     * @param actorId ID of the entity to process
     * @throws Exception if job execution fails
     */
    void run(String actorId) throws Exception;
}
