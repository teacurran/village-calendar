package villagecompute.calendar.services;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import villagecompute.calendar.data.models.DelayedJob;
import villagecompute.calendar.data.models.DelayedJobQueue;
import villagecompute.calendar.services.exceptions.DelayedJobException;
import villagecompute.calendar.services.jobs.DelayedJobHandler;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for processing delayed jobs asynchronously.
 * Uses Vert.x EventBus for immediate processing with a scheduled fallback
 * to catch any jobs that need retry or were missed.
 */
@ApplicationScoped
@Startup
public class DelayedJobService {

    private static final Logger LOG = Logger.getLogger(DelayedJobService.class);

    public static final String DELAYED_JOB_RUN = "delayed-job-run";

    @Inject
    EventBus eventBus;

    @Inject
    Map<DelayedJobQueue, DelayedJobHandler> delayedJobHandlers;

    /**
     * Create and immediately fire a delayed job.
     *
     * @param actorId ID of the entity to process
     * @param queue Job queue type
     * @return Created DelayedJob
     */
    @Transactional
    public DelayedJob createDelayedJob(String actorId, DelayedJobQueue queue) {
        return createDelayedJob(actorId, queue, Instant.now());
    }

    /**
     * Create a delayed job to run at a specific time.
     *
     * @param actorId ID of the entity to process
     * @param queue Job queue type
     * @param runAt When to run the job
     * @return Created DelayedJob
     */
    @Transactional
    public DelayedJob createDelayedJob(String actorId, DelayedJobQueue queue, Instant runAt) {
        DelayedJob delayedJob = DelayedJob.createDelayedJob(actorId, queue, runAt);

        LOG.infof("Created delayed job %s for queue %s, actor %s", delayedJob.id, queue, actorId);

        // Immediately fire the event to process it
        eventBus.publish(DELAYED_JOB_RUN, delayedJob.id.toString());

        return delayedJob;
    }

    /**
     * Event consumer - processes delayed jobs asynchronously.
     * blocking = true ensures this runs on a worker thread (required for @Transactional)
     *
     * @param jobId Job ID to process
     */
    @ConsumeEvent(value = DELAYED_JOB_RUN, blocking = true)
    @Transactional
    @WithSpan("DelayedJobService.handleDelayedJobRun")
    public void handleDelayedJobRun(String jobId) {
        DelayedJob job = getDelayedJobToWorkOn(UUID.fromString(jobId));

        if (job == null) {
            return; // Already processed or locked
        }

        try {
            // Check if it's time to run
            if (job.runAt.isAfter(Instant.now())) {
                LOG.debugf("Delayed job %s is not ready to run yet", jobId);
                job.unlock();
                job.persist();
                return;
            }

            // Get the handler for this queue
            DelayedJobHandler handler = delayedJobHandlers.get(job.queue);
            if (handler == null) {
                LOG.errorf("No handler found for queue: %s", job.queue);
                job.unlock();
                job.persist();
                return;
            }

            // Execute the handler
            LOG.infof("Processing delayed job %s with queue %s, actor %s", jobId, job.queue, job.actorId);
            handler.run(job.actorId);

            // Mark as complete
            job.attempts++;
            job.completedAt = Instant.now();
            job.complete = true;
            job.unlock();
            job.persist();

            LOG.infof("Delayed job %s completed successfully", jobId);
            Span.current().addEvent("Delayed job completed successfully");

        } catch (Exception e) {
            handleJobFailure(job, e);
        }
    }

    /**
     * Handle job failure - record error and schedule retry if appropriate.
     *
     * @param job The failed job
     * @param e The exception that caused the failure
     */
    @Transactional
    protected void handleJobFailure(DelayedJob job, Exception e) {
        LOG.errorf(e, "Delayed job %s failed: %s", job.id, e.getMessage());

        // Record error details
        job.lastError = Arrays.stream(e.getStackTrace())
            .limit(20)
            .map(StackTraceElement::toString)
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");
        job.failedAt = Instant.now();
        job.attempts++;

        // Check if it's a fatal error (non-recoverable)
        if (e instanceof DelayedJobException delayedJobException) {
            if (!delayedJobException.isRecoverable()) {
                job.complete = true;
                job.completedAt = Instant.now();
                job.completedWithFailure = true;
                job.failureReason = delayedJobException.getMessage();
                LOG.errorf("Delayed job %s failed fatally: %s", job.id, delayedJobException.getMessage());
            } else {
                job.failureReason = delayedJobException.getMessage();
            }
        }

        // Schedule retry if not complete
        if (!job.complete && !job.completedWithFailure) {
            job.runAt = DelayedJobRetryStrategy.calculateNextRetryInterval(job.attempts);
            LOG.infof("Delayed job %s scheduled for retry at %s (attempt %d)",
                job.id, job.runAt, job.attempts);
        }

        job.unlock();
        job.persist();

        Span.current().recordException(e);
    }

    /**
     * Scheduled processor - runs every 30 seconds to catch any jobs that need retry
     * or were missed during immediate processing.
     */
    @Scheduled(every = "30s")
    @Transactional
    @WithSpan("DelayedJobService.processScheduledJobs")
    public void processScheduledJobs() {
        List<DelayedJob> readyJobs = DelayedJob.findReadyToRun(100);

        if (!readyJobs.isEmpty()) {
            LOG.infof("Found %d delayed jobs ready to run", readyJobs.size());

            for (DelayedJob job : readyJobs) {
                // Fire event for each job
                eventBus.publish(DELAYED_JOB_RUN, job.id.toString());
            }
        }
    }

    /**
     * Atomically lock a job for processing.
     * Uses database UPDATE with WHERE clause to prevent race conditions.
     *
     * @param jobId Job ID to lock
     * @return Locked job, or null if already locked/complete
     */
    @Transactional
    protected DelayedJob getDelayedJobToWorkOn(UUID jobId) {
        // Atomically lock the job using UPDATE with WHERE clause
        // This prevents race conditions when multiple servers try to lock the same job
        int rowsUpdated = DelayedJob.update(
            "locked = true, lockedAt = ?1 WHERE id = ?2 AND locked = false AND complete = false",
            Instant.now(),
            jobId
        );

        if (rowsUpdated == 0) {
            LOG.debugf("Delayed job %s could not be locked (already locked or complete)", jobId);
            return null;
        }

        // Now fetch the locked job
        DelayedJob job = DelayedJob.findById(jobId);

        if (job == null) {
            LOG.errorf("Delayed job not found after locking: %s", jobId);
            return null;
        }

        LOG.debugf("Successfully locked delayed job %s", jobId);
        return job;
    }
}
