package villagecompute.calendar.services.jobs;

/**
 * Interface for DelayedJob handlers. Implementations should be annotated with @ApplicationScoped and @DelayedJobConfig.
 * They are automatically discovered and registered at startup.
 */
public interface DelayedJobHandler {
    /**
     * Execute the job.
     *
     * @param actorId
     *            ID of the entity to process
     * @throws Exception
     *             if job execution fails
     */
    void run(String actorId) throws Exception;

    /**
     * Get the queue name for this handler. Default implementation uses the class simple name. Override to customize the
     * queue name.
     *
     * @return Queue identifier string
     */
    default String getQueueName() {
        Class<?> clazz = this.getClass();
        // Handle Quarkus proxy classes (e.g., MyHandler_ClientProxy)
        if (clazz.getName().contains("_ClientProxy") || clazz.getName().contains("_Subclass")) {
            clazz = clazz.getSuperclass();
        }
        return clazz.getSimpleName();
    }
}
