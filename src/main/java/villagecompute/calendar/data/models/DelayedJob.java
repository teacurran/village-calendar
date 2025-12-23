package villagecompute.calendar.data.models;

import io.quarkus.panache.common.Parameters;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;

/**
 * Entity for asynchronous job processing with retry logic.
 * Jobs are executed via Vert.x EventBus with scheduled fallback.
 */
@Entity
@Table(
    name = "delayed_jobs",
    indexes = {
        @Index(name = "idx_delayed_jobs_queue_name_run_at", columnList = "queue_name, run_at, complete, locked")
    }
)
@NamedQuery(
    name = DelayedJob.QUERY_FIND_READY_TO_RUN,
    query = """
        FROM DelayedJob
        WHERE runAt <= :now
        AND complete = false
        AND locked = false
        ORDER BY priority DESC, runAt ASC
        """
)
public class DelayedJob extends DefaultPanacheEntityWithTimestamps {

    public static final String QUERY_FIND_READY_TO_RUN = "DelayedJob.findReadyToRun";

    @Column(nullable = false)
    public Integer priority = 0;

    @Column(nullable = false)
    public Integer attempts = 0;

    /**
     * Queue name - the handler class simple name.
     */
    @Column(name = "queue_name", nullable = false, length = 100)
    public String queueName;

    /**
     * UUID of the entity this job operates on (e.g., CalendarOrder ID).
     */
    @Column(nullable = false, columnDefinition = "VARCHAR(36)")
    public String actorId;

    @Column(columnDefinition = "TEXT")
    public String lastError;

    @Column(name = "run_at", nullable = false)
    public Instant runAt;

    @Column(nullable = false)
    public boolean locked = false;

    @Column(name = "locked_at")
    public Instant lockedAt;

    @Column(name = "failed_at")
    public Instant failedAt;

    @Column(nullable = false)
    public boolean complete = false;

    @Column(name = "completed_at")
    public Instant completedAt;

    @Column(name = "completed_with_failure", nullable = false)
    public boolean completedWithFailure = false;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    public String failureReason;

    /**
     * Factory method to create a DelayedJob.
     *
     * @param actorId ID of the entity to process
     * @param queueName Handler queue name
     * @param priority Job priority
     * @param runAt When to run the job
     * @return Created DelayedJob
     */
    public static DelayedJob createDelayedJob(String actorId, String queueName, int priority, Instant runAt) {
        DelayedJob delayedJob = new DelayedJob();
        delayedJob.actorId = actorId;
        delayedJob.runAt = runAt;
        delayedJob.queueName = queueName;
        delayedJob.priority = priority;
        delayedJob.persist();
        return delayedJob;
    }

    /**
     * Unlock this job so it can be retried.
     */
    public void unlock() {
        this.locked = false;
        this.lockedAt = null;
    }

    /**
     * Find jobs that are ready to run.
     *
     * @param limit Maximum number of jobs to return
     * @return List of ready jobs
     */
    public static List<DelayedJob> findReadyToRun(int limit) {
        return find("#" + QUERY_FIND_READY_TO_RUN, Parameters.with("now", Instant.now()))
            .range(0, limit - 1)
            .list();
    }
}
