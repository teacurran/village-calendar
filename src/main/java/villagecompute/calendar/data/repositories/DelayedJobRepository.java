package villagecompute.calendar.data.repositories;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import villagecompute.calendar.data.models.DelayedJob;
import villagecompute.calendar.data.models.DelayedJobQueue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for DelayedJob entities.
 * Provides custom query methods for job processing and retry logic.
 */
@ApplicationScoped
public class DelayedJobRepository implements PanacheRepository<DelayedJob> {

    /**
     * Find delayed job by ID.
     *
     * @param id Job ID
     * @return Optional containing the job if found
     */
    public Optional<DelayedJob> findById(UUID id) {
        return find("id", id).firstResultOptional();
    }

    /**
     * Find jobs that are ready to run.
     *
     * @param limit Maximum number of jobs to return
     * @return List of ready jobs
     */
    public List<DelayedJob> findReadyToRun(int limit) {
        return find("#" + DelayedJob.QUERY_FIND_READY_TO_RUN, Parameters.with("now", Instant.now()))
            .range(0, limit - 1)
            .list();
    }

    /**
     * Find jobs by queue.
     *
     * @param queue Job queue
     * @return List of jobs in the queue
     */
    public List<DelayedJob> findByQueue(DelayedJobQueue queue) {
        return find("queue = ?1 ORDER BY priority DESC, runAt ASC", queue).list();
    }

    /**
     * Find jobs by actor ID.
     *
     * @param actorId Actor ID
     * @return List of jobs for the actor
     */
    public List<DelayedJob> findByActorId(String actorId) {
        return find("actorId = ?1 ORDER BY created DESC", actorId).list();
    }

    /**
     * Find incomplete jobs.
     *
     * @return List of incomplete jobs
     */
    public List<DelayedJob> findIncomplete() {
        return find("complete = false ORDER BY priority DESC, runAt ASC").list();
    }

    /**
     * Find failed jobs.
     *
     * @return List of failed jobs
     */
    public List<DelayedJob> findFailed() {
        return find("completedWithFailure = true ORDER BY failedAt DESC").list();
    }
}
