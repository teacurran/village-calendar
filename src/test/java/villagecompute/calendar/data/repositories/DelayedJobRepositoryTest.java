package villagecompute.calendar.data.repositories;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import villagecompute.calendar.data.models.DelayedJob;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class DelayedJobRepositoryTest {

    private static final String QUEUE_ORDER_EMAIL = "OrderEmailJobHandler";
    private static final String QUEUE_SHIPPING = "ShippingNotificationJobHandler";
    private static final String QUEUE_CANCELLATION = "OrderCancellationJobHandler";

    @Inject TestDataCleaner testDataCleaner;

    @Inject DelayedJobRepository repository;

    @Inject jakarta.persistence.EntityManager entityManager;

    @BeforeEach
    @Transactional
    void setUp() {
        testDataCleaner.deleteAll();
    }

    @Test
    @Transactional
    void testFindById() {
        // Given
        DelayedJob job =
                createJob(
                        QUEUE_CANCELLATION, "actor-123", Instant.now().plus(1, ChronoUnit.MINUTES));
        repository.persist(job);
        entityManager.flush();

        // When
        Optional<DelayedJob> found = repository.findById(job.id);

        // Then
        assertTrue(found.isPresent());
        assertEquals("actor-123", found.get().actorId);
        assertEquals(QUEUE_CANCELLATION, found.get().queueName);
    }

    @Test
    @Transactional
    void testFindReadyToRun() {
        // Given
        Instant now = Instant.now();

        // Ready to run (runAt in past)
        DelayedJob ready1 =
                createJob(QUEUE_ORDER_EMAIL, "actor-1", now.minus(10, ChronoUnit.MINUTES));
        ready1.priority = 10;
        repository.persist(ready1);

        DelayedJob ready2 =
                createJob(QUEUE_CANCELLATION, "actor-2", now.minus(5, ChronoUnit.MINUTES));
        ready2.priority = 5;
        repository.persist(ready2);

        // Not ready (runAt in future)
        DelayedJob notReady =
                createJob(QUEUE_CANCELLATION, "actor-3", now.plus(10, ChronoUnit.MINUTES));
        repository.persist(notReady);

        // Already complete
        DelayedJob complete =
                createJob(QUEUE_CANCELLATION, "actor-4", now.minus(20, ChronoUnit.MINUTES));
        complete.complete = true;
        repository.persist(complete);

        // Locked
        DelayedJob locked =
                createJob(QUEUE_CANCELLATION, "actor-5", now.minus(15, ChronoUnit.MINUTES));
        locked.locked = true;
        locked.lockedAt = now.minus(1, ChronoUnit.MINUTES);
        repository.persist(locked);

        entityManager.flush();

        // When
        List<DelayedJob> readyJobs = repository.findReadyToRun(10);

        // Then
        assertEquals(2, readyJobs.size());
        assertTrue(
                readyJobs.stream()
                        .allMatch(
                                j -> !j.runAt.isAfter(Instant.now()) && !j.complete && !j.locked));
        // Verify ORDER BY priority DESC, runAt ASC
        assertEquals(ready1.id, readyJobs.get(0).id); // Higher priority first
        assertEquals(ready2.id, readyJobs.get(1).id);
    }

    @Test
    @Transactional
    void testFindReadyToRun_RespectsLimit() {
        // Given
        Instant now = Instant.now();

        // Create 5 ready jobs
        for (int i = 0; i < 5; i++) {
            DelayedJob job =
                    createJob(
                            QUEUE_CANCELLATION, "actor-" + i, now.minus(i + 1, ChronoUnit.MINUTES));
            repository.persist(job);
        }
        entityManager.flush();

        // When
        List<DelayedJob> readyJobs = repository.findReadyToRun(3);

        // Then
        assertEquals(3, readyJobs.size());
    }

    @Test
    @Transactional
    void testFindByQueueName() {
        // Given
        repository.persist(
                createJobWithPriority(
                        QUEUE_ORDER_EMAIL,
                        "actor-1",
                        10,
                        Instant.now().plus(5, ChronoUnit.MINUTES)));
        repository.persist(
                createJobWithPriority(
                        QUEUE_ORDER_EMAIL,
                        "actor-2",
                        5,
                        Instant.now().plus(10, ChronoUnit.MINUTES)));
        repository.persist(
                createJobWithPriority(
                        QUEUE_CANCELLATION,
                        "actor-3",
                        5,
                        Instant.now().plus(1, ChronoUnit.MINUTES)));
        repository.persist(
                createJobWithPriority(
                        QUEUE_SHIPPING, "actor-4", 10, Instant.now().plus(2, ChronoUnit.MINUTES)));
        entityManager.flush();

        // When
        List<DelayedJob> orderConfirmationJobs = repository.findByQueueName(QUEUE_ORDER_EMAIL);

        // Then
        assertEquals(2, orderConfirmationJobs.size());
        assertTrue(
                orderConfirmationJobs.stream()
                        .allMatch(j -> QUEUE_ORDER_EMAIL.equals(j.queueName)));
        // Verify ORDER BY priority DESC, runAt ASC
        assertEquals(10, orderConfirmationJobs.get(0).priority);
        assertEquals(5, orderConfirmationJobs.get(1).priority);
    }

    @Test
    @Transactional
    void testFindByActorId() {
        // Given
        Instant now = Instant.now();

        DelayedJob job1 =
                createJob(QUEUE_CANCELLATION, "actor-123", now.minus(10, ChronoUnit.MINUTES));
        repository.persist(job1);

        // Delay to ensure different created timestamps
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
        }

        DelayedJob job2 =
                createJob(QUEUE_ORDER_EMAIL, "actor-123", now.minus(5, ChronoUnit.MINUTES));
        repository.persist(job2);

        DelayedJob job3 =
                createJob(QUEUE_CANCELLATION, "actor-456", now.minus(3, ChronoUnit.MINUTES));
        repository.persist(job3);

        entityManager.flush();

        // When
        List<DelayedJob> actorJobs = repository.findByActorId("actor-123");

        // Then
        assertEquals(2, actorJobs.size());
        assertTrue(actorJobs.stream().allMatch(j -> "actor-123".equals(j.actorId)));
        // Verify ORDER BY created DESC (most recent first)
        assertEquals(job2.id, actorJobs.get(0).id);
        assertEquals(job1.id, actorJobs.get(1).id);
    }

    @Test
    @Transactional
    void testFindIncomplete() {
        // Given
        DelayedJob incomplete1 =
                createJob(QUEUE_CANCELLATION, "actor-1", Instant.now().plus(1, ChronoUnit.MINUTES));
        incomplete1.complete = false;
        incomplete1.priority = 10;
        repository.persist(incomplete1);

        DelayedJob incomplete2 =
                createJob(QUEUE_ORDER_EMAIL, "actor-2", Instant.now().plus(5, ChronoUnit.MINUTES));
        incomplete2.complete = false;
        incomplete2.priority = 5;
        repository.persist(incomplete2);

        DelayedJob complete =
                createJob(QUEUE_CANCELLATION, "actor-3", Instant.now().plus(2, ChronoUnit.MINUTES));
        complete.complete = true;
        complete.completedAt = Instant.now();
        repository.persist(complete);

        entityManager.flush();

        // When
        List<DelayedJob> incompleteJobs = repository.findIncomplete();

        // Then
        assertEquals(2, incompleteJobs.size());
        assertTrue(incompleteJobs.stream().allMatch(j -> !j.complete));
        // Verify ORDER BY priority DESC, runAt ASC
        assertEquals(10, incompleteJobs.get(0).priority);
        assertEquals(5, incompleteJobs.get(1).priority);
    }

    @Test
    @Transactional
    void testFindFailed() {
        // Given
        Instant now = Instant.now();

        DelayedJob failed1 =
                createJob(QUEUE_CANCELLATION, "actor-1", now.minus(10, ChronoUnit.MINUTES));
        failed1.completedWithFailure = true;
        failed1.failedAt = now.minus(5, ChronoUnit.MINUTES);
        failed1.failureReason = "Network error";
        repository.persist(failed1);

        // Delay to ensure different failedAt timestamps
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
        }

        DelayedJob failed2 =
                createJob(QUEUE_ORDER_EMAIL, "actor-2", now.minus(20, ChronoUnit.MINUTES));
        failed2.completedWithFailure = true;
        failed2.failedAt = now.minus(1, ChronoUnit.MINUTES);
        failed2.failureReason = "Timeout";
        repository.persist(failed2);

        DelayedJob successful =
                createJob(QUEUE_CANCELLATION, "actor-3", now.minus(15, ChronoUnit.MINUTES));
        successful.complete = true;
        successful.completedWithFailure = false;
        successful.completedAt = now.minus(2, ChronoUnit.MINUTES);
        repository.persist(successful);

        entityManager.flush();

        // When
        List<DelayedJob> failedJobs = repository.findFailed();

        // Then
        assertEquals(2, failedJobs.size());
        assertTrue(failedJobs.stream().allMatch(j -> j.completedWithFailure));
        // Verify ORDER BY failedAt DESC (most recent failure first)
        assertEquals(failed2.id, failedJobs.get(0).id);
        assertEquals(failed1.id, failedJobs.get(1).id);
    }

    @Test
    @Transactional
    void testPersist() {
        // Given
        DelayedJob job =
                createJob(QUEUE_SHIPPING, "order-456", Instant.now().plus(30, ChronoUnit.MINUTES));

        // When
        repository.persist(job);
        entityManager.flush();

        // Then
        assertNotNull(job.id);
        assertEquals(1, repository.count());
        assertEquals(0, job.attempts);
        assertFalse(job.locked);
        assertFalse(job.complete);
    }

    @Test
    @Transactional
    void testListAll() {
        // Given
        repository.persist(createJob(QUEUE_CANCELLATION, "actor-1", Instant.now()));
        repository.persist(createJob(QUEUE_ORDER_EMAIL, "actor-2", Instant.now()));
        repository.persist(createJob(QUEUE_SHIPPING, "actor-3", Instant.now()));
        entityManager.flush();

        // When
        List<DelayedJob> all = repository.listAll();

        // Then
        assertEquals(3, all.size());
    }

    @Test
    @Transactional
    void testDelete() {
        // Given
        DelayedJob job = createJob(QUEUE_CANCELLATION, "to-delete", Instant.now());
        repository.persist(job);
        entityManager.flush();
        java.util.UUID jobId = job.id;

        // When
        repository.delete(job);
        entityManager.flush();

        // Then
        assertTrue(repository.findById(jobId).isEmpty());
        assertEquals(0, repository.count());
    }

    @Test
    @Transactional
    void testCount() {
        // Given
        repository.persist(createJob(QUEUE_CANCELLATION, "actor-1", Instant.now()));
        repository.persist(createJob(QUEUE_ORDER_EMAIL, "actor-2", Instant.now()));
        entityManager.flush();

        // When
        long count = repository.count();

        // Then
        assertEquals(2, count);
    }

    @Test
    @Transactional
    void testJobUnlock() {
        // Given
        DelayedJob job = createJob(QUEUE_CANCELLATION, "actor-1", Instant.now());
        job.locked = true;
        job.lockedAt = Instant.now();
        repository.persist(job);
        entityManager.flush();

        // When
        job.unlock();
        repository.persist(job);
        entityManager.flush();

        // Then
        Optional<DelayedJob> found = repository.findById(job.id);
        assertTrue(found.isPresent());
        assertFalse(found.get().locked);
        assertNull(found.get().lockedAt);
    }

    private DelayedJob createJob(String queueName, String actorId, Instant runAt) {
        DelayedJob job = new DelayedJob();
        job.queueName = queueName;
        job.actorId = actorId;
        job.runAt = runAt;
        job.priority = 5; // Default priority
        job.attempts = 0;
        job.complete = false;
        job.locked = false;
        job.completedWithFailure = false;
        return job;
    }

    private DelayedJob createJobWithPriority(
            String queueName, String actorId, int priority, Instant runAt) {
        DelayedJob job = createJob(queueName, actorId, runAt);
        job.priority = priority;
        return job;
    }
}
