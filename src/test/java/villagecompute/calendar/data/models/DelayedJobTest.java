package villagecompute.calendar.data.models;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import villagecompute.calendar.data.repositories.TestDataCleaner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class DelayedJobTest {

    @Inject
    TestDataCleaner testDataCleaner;

    @Inject
    jakarta.persistence.EntityManager entityManager;

    @BeforeEach
    @Transactional
    void setUp() {
        testDataCleaner.deleteAll();
    }

    @Test
    @Transactional
    void testValidEntity_Success() {
        // Given
        DelayedJob job = createValidJob();

        // When
        job.persist();
        entityManager.flush(); // Flush to generate ID and timestamps

        // Then
        assertNotNull(job.id);
        assertNotNull(job.created);
        assertNotNull(job.updated);
        assertEquals(0L, job.version);
    }

    @Test
    @Transactional
    void testDefaultValues() {
        // Given
        DelayedJob job = new DelayedJob();
        job.actorId = "test-actor-id";
        job.queue = DelayedJobQueue.EMAIL_ORDER_CONFIRMATION;
        job.runAt = Instant.now();

        // When
        job.persist();

        // Then
        assertEquals(0, job.priority); // Default 0
        assertEquals(0, job.attempts); // Default 0
        assertFalse(job.locked); // Default false
        assertFalse(job.complete); // Default false
        assertFalse(job.completedWithFailure); // Default false
    }

    @Test
    @Transactional
    void testCreateDelayedJob() {
        // Given
        String actorId = "order-123";
        DelayedJobQueue queue = DelayedJobQueue.EMAIL_ORDER_CONFIRMATION;
        Instant runAt = Instant.now().plus(5, ChronoUnit.MINUTES);

        // When
        DelayedJob job = DelayedJob.createDelayedJob(actorId, queue, runAt);

        // Then
        assertNotNull(job.id);
        assertEquals(actorId, job.actorId);
        assertEquals(queue, job.queue);
        assertEquals(runAt, job.runAt);
        assertEquals(queue.getPriority(), job.priority);
        assertFalse(job.locked);
        assertFalse(job.complete);
    }

    @Test
    @Transactional
    void testCreateDelayedJob_PriorityFromQueue() {
        // Given
        DelayedJobQueue highPriorityQueue = DelayedJobQueue.EMAIL_ORDER_CONFIRMATION;
        DelayedJobQueue lowPriorityQueue = DelayedJobQueue.EMAIL_GENERAL;

        // When
        DelayedJob highPriorityJob = DelayedJob.createDelayedJob("actor1", highPriorityQueue, Instant.now());
        DelayedJob lowPriorityJob = DelayedJob.createDelayedJob("actor2", lowPriorityQueue, Instant.now());

        // Then
        assertEquals(10, highPriorityJob.priority);
        assertEquals(5, lowPriorityJob.priority);
    }

    @Test
    @Transactional
    void testUnlock() {
        // Given
        DelayedJob job = createValidJob();
        job.locked = true;
        job.lockedAt = Instant.now();
        job.persist();

        // When
        job.unlock();

        // Then
        assertFalse(job.locked);
        assertNull(job.lockedAt);
    }

    @Test
    @Transactional
    void testFindReadyToRun() {
        // Given
        Instant now = Instant.now();
        Instant future = now.plus(1, ChronoUnit.HOURS);
        Instant past = now.minus(1, ChronoUnit.HOURS);

        // Ready to run (in the past, not complete, not locked)
        DelayedJob readyJob1 = createValidJob();
        readyJob1.runAt = past;
        readyJob1.complete = false;
        readyJob1.locked = false;
        readyJob1.priority = 10;
        readyJob1.persist();

        DelayedJob readyJob2 = createValidJob();
        readyJob2.runAt = past.minus(1, ChronoUnit.MINUTES);
        readyJob2.complete = false;
        readyJob2.locked = false;
        readyJob2.priority = 5;
        readyJob2.persist();

        // Not ready (in the future)
        DelayedJob futureJob = createValidJob();
        futureJob.runAt = future;
        futureJob.complete = false;
        futureJob.locked = false;
        futureJob.persist();

        // Not ready (already complete)
        DelayedJob completeJob = createValidJob();
        completeJob.runAt = past;
        completeJob.complete = true;
        completeJob.locked = false;
        completeJob.persist();

        // Not ready (locked)
        DelayedJob lockedJob = createValidJob();
        lockedJob.runAt = past;
        lockedJob.complete = false;
        lockedJob.locked = true;
        lockedJob.persist();

        // When
        List<DelayedJob> readyJobs = DelayedJob.findReadyToRun(10);

        // Then
        assertEquals(2, readyJobs.size());
        // Should be ordered by priority DESC, runAt ASC
        assertEquals(10, readyJobs.get(0).priority);
        assertEquals(5, readyJobs.get(1).priority);
    }

    @Test
    @Transactional
    void testFindReadyToRun_WithLimit() {
        // Given
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);

        DelayedJob job1 = createValidJob();
        job1.runAt = past;
        job1.complete = false;
        job1.locked = false;
        job1.persist();

        DelayedJob job2 = createValidJob();
        job2.runAt = past;
        job2.complete = false;
        job2.locked = false;
        job2.persist();

        DelayedJob job3 = createValidJob();
        job3.runAt = past;
        job3.complete = false;
        job3.locked = false;
        job3.persist();

        // When
        List<DelayedJob> readyJobs = DelayedJob.findReadyToRun(2);

        // Then
        assertEquals(2, readyJobs.size());
    }

    @Test
    @Transactional
    void testQueueEnumValues() {
        // Given/When
        DelayedJob emailConfirmationJob = createValidJob();
        emailConfirmationJob.queue = DelayedJobQueue.EMAIL_ORDER_CONFIRMATION;
        emailConfirmationJob.persist();

        DelayedJob emailShippingJob = createValidJob();
        emailShippingJob.queue = DelayedJobQueue.EMAIL_SHIPPING_NOTIFICATION;
        emailShippingJob.persist();

        DelayedJob emailGeneralJob = createValidJob();
        emailGeneralJob.queue = DelayedJobQueue.EMAIL_GENERAL;
        emailGeneralJob.persist();

        // Then
        DelayedJob found1 = DelayedJob.findById(emailConfirmationJob.id);
        assertEquals(DelayedJobQueue.EMAIL_ORDER_CONFIRMATION, found1.queue);

        DelayedJob found2 = DelayedJob.findById(emailShippingJob.id);
        assertEquals(DelayedJobQueue.EMAIL_SHIPPING_NOTIFICATION, found2.queue);

        DelayedJob found3 = DelayedJob.findById(emailGeneralJob.id);
        assertEquals(DelayedJobQueue.EMAIL_GENERAL, found3.queue);
    }

    @Test
    @Transactional
    void testJobLifecycle() {
        // Given
        DelayedJob job = createValidJob();
        job.runAt = Instant.now().minus(1, ChronoUnit.HOURS);
        job.persist();

        // Step 1: Job is ready to run
        List<DelayedJob> readyJobs = DelayedJob.findReadyToRun(1);
        assertEquals(1, readyJobs.size());

        // Step 2: Lock the job
        DelayedJob jobToRun = readyJobs.get(0);
        jobToRun.locked = true;
        jobToRun.lockedAt = Instant.now();
        jobToRun.persist();

        // Step 3: Verify locked job is not in ready list
        List<DelayedJob> readyJobsAfterLock = DelayedJob.findReadyToRun(10);
        assertEquals(0, readyJobsAfterLock.size());

        // Step 4: Mark as complete
        jobToRun.complete = true;
        jobToRun.completedAt = Instant.now();
        jobToRun.persist();

        // Step 5: Verify complete job is not in ready list
        List<DelayedJob> readyJobsAfterComplete = DelayedJob.findReadyToRun(10);
        assertEquals(0, readyJobsAfterComplete.size());
    }

    @Test
    @Transactional
    void testJobRetryScenario() {
        // Given
        DelayedJob job = createValidJob();
        job.runAt = Instant.now().minus(1, ChronoUnit.HOURS);
        job.locked = true;
        job.lockedAt = Instant.now().minus(30, ChronoUnit.MINUTES);
        job.attempts = 1;
        job.lastError = "Network timeout";
        job.persist();

        // When - unlock for retry
        job.unlock();
        job.attempts++;
        job.persist();

        // Then
        assertFalse(job.locked);
        assertNull(job.lockedAt);
        assertEquals(2, job.attempts);

        // Verify it's ready to run again
        List<DelayedJob> readyJobs = DelayedJob.findReadyToRun(1);
        assertEquals(1, readyJobs.size());
    }

    private DelayedJob createValidJob() {
        DelayedJob job = new DelayedJob();
        job.actorId = "test-actor-" + System.nanoTime();
        job.queue = DelayedJobQueue.EMAIL_ORDER_CONFIRMATION;
        job.runAt = Instant.now();
        job.priority = 0;
        job.attempts = 0;
        job.locked = false;
        job.complete = false;
        job.completedWithFailure = false;
        return job;
    }
}
