package villagecompute.calendar.services;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import villagecompute.calendar.data.models.DelayedJob;
import villagecompute.calendar.data.repositories.TestDataCleaner;
import villagecompute.calendar.services.exceptions.DelayedJobException;
import villagecompute.calendar.services.jobs.DelayedJobHandlerRegistry;
import villagecompute.calendar.services.jobs.OrderEmailJobHandler;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DelayedJobService.
 * Tests job creation, processing, retry logic, and failure handling.
 */
@QuarkusTest
class DelayedJobServiceTest {

    @Inject
    DelayedJobService delayedJobService;

    @Inject
    DelayedJobHandlerRegistry handlerRegistry;

    @Inject
    TestDataCleaner testDataCleaner;

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            testDataCleaner.deleteAll();
        });
    }

    // ============================================================================
    // ENQUEUE TESTS
    // ============================================================================

    @Test
    void testEnqueue_CreatesJobWithCorrectData() {
        // Given
        String actorId = "order-123";

        // When
        DelayedJob job = QuarkusTransaction.requiringNew().call(() ->
            delayedJobService.enqueue(OrderEmailJobHandler.class, actorId)
        );

        // Then
        assertNotNull(job);
        assertNotNull(job.id);
        assertEquals(actorId, job.actorId);
        assertEquals("OrderEmailJobHandler", job.queueName);
        assertEquals(10, job.priority); // OrderEmailJobHandler has priority 10
        assertFalse(job.complete);
        assertFalse(job.locked);
    }

    @Test
    void testEnqueue_WithRunAt_SchedulesForFuture() {
        // Given
        String actorId = "order-456";
        Instant futureTime = Instant.now().plus(1, ChronoUnit.HOURS);

        // When
        DelayedJob job = QuarkusTransaction.requiringNew().call(() ->
            delayedJobService.enqueue(OrderEmailJobHandler.class, actorId, futureTime)
        );

        // Then
        assertNotNull(job);
        assertEquals(actorId, job.actorId);
        assertTrue(job.runAt.isAfter(Instant.now()));
    }

    @Test
    void testEnqueueWithDelay_AddsDelayToCurrentTime() {
        // Given
        String actorId = "order-789";
        Duration delay = Duration.ofMinutes(30);
        Instant beforeEnqueue = Instant.now();

        // When
        DelayedJob job = QuarkusTransaction.requiringNew().call(() ->
            delayedJobService.enqueueWithDelay(OrderEmailJobHandler.class, actorId, delay)
        );

        // Then
        assertNotNull(job);
        assertTrue(job.runAt.isAfter(beforeEnqueue.plus(Duration.ofMinutes(29))));
        assertTrue(job.runAt.isBefore(beforeEnqueue.plus(Duration.ofMinutes(31))));
    }

    @Test
    void testEnqueue_ThrowsForUnregisteredHandler() {
        // Given - A non-existent handler class
        abstract class FakeHandler implements villagecompute.calendar.services.jobs.DelayedJobHandler {}

        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
            QuarkusTransaction.requiringNew().call(() ->
                delayedJobService.enqueue(FakeHandler.class, "test-actor")
            )
        );
    }

    // ============================================================================
    // JOB PROCESSING TESTS (via async EventBus)
    // ============================================================================

    @Test
    void testEnqueue_PublishesEventForProcessing() throws InterruptedException {
        // Given - Use a fake order ID; the handler will fail to find it
        // but it tests that the job IS processed
        String actorId = UUID.randomUUID().toString();

        // When - Enqueue a job (it will be processed asynchronously)
        UUID jobId = QuarkusTransaction.requiringNew().call(() -> {
            DelayedJob job = delayedJobService.enqueue(OrderEmailJobHandler.class, actorId);
            return job.id;
        });

        // Wait for async processing (EventBus)
        Thread.sleep(1500);

        // Then - Job should be processed (and marked as complete with failure,
        // since the order doesn't exist - which is a non-recoverable error)
        DelayedJob processedJob = QuarkusTransaction.requiringNew().call(() ->
            DelayedJob.findById(jobId)
        );
        assertTrue(processedJob.complete, "Job should be complete after async processing");
        assertTrue(processedJob.completedWithFailure, "Job should have failed (order not found)");
        assertNotNull(processedJob.completedAt);
        assertEquals(1, processedJob.attempts);
    }

    @Test
    void testEnqueue_FutureJob_NotProcessedImmediately() throws InterruptedException {
        // Given
        String actorId = "order-future-" + System.currentTimeMillis();
        Instant futureTime = Instant.now().plus(1, ChronoUnit.HOURS);

        // When - Enqueue a future job
        UUID jobId = QuarkusTransaction.requiringNew().call(() -> {
            DelayedJob job = delayedJobService.enqueue(OrderEmailJobHandler.class, actorId, futureTime);
            return job.id;
        });

        // Wait for any async processing attempt
        Thread.sleep(1000);

        // Then - Job should NOT be processed (it's scheduled for the future)
        DelayedJob job = QuarkusTransaction.requiringNew().call(() ->
            DelayedJob.findById(jobId)
        );
        assertFalse(job.complete, "Future job should not be complete");
        assertFalse(job.locked, "Future job should be unlocked");
        assertEquals(0, job.attempts, "Future job should have 0 attempts");
    }

    // ============================================================================
    // GET DELAYED JOB TO WORK ON TESTS
    // ============================================================================

    @Test
    void testGetDelayedJobToWorkOn_LocksJob() {
        // Given
        UUID jobId = QuarkusTransaction.requiringNew().call(() -> {
            DelayedJob job = createReadyJob("actor-lock-test");
            return job.id;
        });

        // When
        DelayedJob lockedJob = QuarkusTransaction.requiringNew().call(() ->
            delayedJobService.getDelayedJobToWorkOn(jobId)
        );

        // Then
        assertNotNull(lockedJob);
        assertTrue(lockedJob.locked);
        assertNotNull(lockedJob.lockedAt);
    }

    @Test
    void testGetDelayedJobToWorkOn_ReturnsNullForAlreadyLockedJob() {
        // Given
        UUID jobId = QuarkusTransaction.requiringNew().call(() -> {
            DelayedJob job = createReadyJob("actor-already-locked");
            job.locked = true;
            job.lockedAt = Instant.now();
            job.persist();
            return job.id;
        });

        // When
        DelayedJob result = QuarkusTransaction.requiringNew().call(() ->
            delayedJobService.getDelayedJobToWorkOn(jobId)
        );

        // Then
        assertNull(result);
    }

    @Test
    void testGetDelayedJobToWorkOn_ReturnsNullForCompleteJob() {
        // Given
        UUID jobId = QuarkusTransaction.requiringNew().call(() -> {
            DelayedJob job = createReadyJob("actor-already-complete");
            job.complete = true;
            job.completedAt = Instant.now();
            job.persist();
            return job.id;
        });

        // When
        DelayedJob result = QuarkusTransaction.requiringNew().call(() ->
            delayedJobService.getDelayedJobToWorkOn(jobId)
        );

        // Then
        assertNull(result);
    }

    @Test
    void testGetDelayedJobToWorkOn_ReturnsNullForNonexistentJob() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When
        DelayedJob result = QuarkusTransaction.requiringNew().call(() ->
            delayedJobService.getDelayedJobToWorkOn(nonExistentId)
        );

        // Then
        assertNull(result);
    }

    // ============================================================================
    // FIND READY TO RUN TESTS
    // ============================================================================

    @Test
    void testFindReadyToRun_FindsEligibleJobs() {
        // Given
        QuarkusTransaction.requiringNew().run(() -> {
            createReadyJob("actor-ready-1");
            createReadyJob("actor-ready-2");

            // Create a future job (should not be found)
            DelayedJob futureJob = new DelayedJob();
            futureJob.actorId = "actor-future";
            futureJob.queueName = "OrderEmailJobHandler";
            futureJob.priority = 10;
            futureJob.runAt = Instant.now().plus(1, ChronoUnit.HOURS);
            futureJob.persist();

            // Create a completed job (should not be found)
            DelayedJob completeJob = new DelayedJob();
            completeJob.actorId = "actor-complete";
            completeJob.queueName = "OrderEmailJobHandler";
            completeJob.priority = 10;
            completeJob.runAt = Instant.now().minus(1, ChronoUnit.HOURS);
            completeJob.complete = true;
            completeJob.persist();
        });

        // When
        List<DelayedJob> readyJobs = QuarkusTransaction.requiringNew().call(() ->
            DelayedJob.findReadyToRun(100)
        );

        // Then
        assertEquals(2, readyJobs.size());
    }

    @Test
    void testFindReadyToRun_OrdersByPriorityAndRunAt() {
        // Given
        QuarkusTransaction.requiringNew().run(() -> {
            DelayedJob lowPriority = new DelayedJob();
            lowPriority.actorId = "actor-low";
            lowPriority.queueName = "OrderCancellationJobHandler";
            lowPriority.priority = 5;
            lowPriority.runAt = Instant.now().minus(2, ChronoUnit.HOURS);
            lowPriority.persist();

            DelayedJob highPriority = new DelayedJob();
            highPriority.actorId = "actor-high";
            highPriority.queueName = "OrderEmailJobHandler";
            highPriority.priority = 10;
            highPriority.runAt = Instant.now().minus(1, ChronoUnit.HOURS);
            highPriority.persist();
        });

        // When
        List<DelayedJob> readyJobs = QuarkusTransaction.requiringNew().call(() ->
            DelayedJob.findReadyToRun(100)
        );

        // Then - High priority job should come first
        assertEquals(2, readyJobs.size());
        assertEquals(10, readyJobs.get(0).priority);
        assertEquals(5, readyJobs.get(1).priority);
    }

    // ============================================================================
    // HANDLER REGISTRY TESTS
    // ============================================================================

    @Test
    void testHandlerRegistry_FindsRegisteredHandler() {
        // When
        var handler = handlerRegistry.getHandler("OrderEmailJobHandler");

        // Then
        assertTrue(handler.isPresent());
        assertNotNull(handler.get());
    }

    @Test
    void testHandlerRegistry_ReturnsEmptyForUnknownHandler() {
        // When
        var handler = handlerRegistry.getHandler("NonExistentHandler");

        // Then
        assertFalse(handler.isPresent());
    }

    @Test
    void testHandlerRegistry_FindsMetadataByClass() {
        // When
        var metadata = handlerRegistry.getMetadata(OrderEmailJobHandler.class);

        // Then
        assertTrue(metadata.isPresent());
        assertEquals("OrderEmailJobHandler", metadata.get().queueName());
        assertEquals(10, metadata.get().priority());
    }

    // ============================================================================
    // RETRY STRATEGY TESTS
    // ============================================================================

    @Test
    void testRetryStrategy_CalculatesExponentialBackoff() {
        // When - Test various attempt counts
        Instant retry1 = DelayedJobRetryStrategy.calculateNextRetryInterval(1);
        Instant retry2 = DelayedJobRetryStrategy.calculateNextRetryInterval(2);
        Instant retry3 = DelayedJobRetryStrategy.calculateNextRetryInterval(3);

        // Then - Each retry should be further in the future
        Instant now = Instant.now();
        assertTrue(retry1.isAfter(now));
        assertTrue(retry2.isAfter(retry1));
        assertTrue(retry3.isAfter(retry2));
    }

    // ============================================================================
    // CONCURRENT LOCKING TEST
    // ============================================================================

    @Test
    void testConcurrentLocking_OnlyOneSucceeds() {
        // Given
        UUID jobId = QuarkusTransaction.requiringNew().call(() -> {
            DelayedJob job = createReadyJob("actor-concurrent");
            return job.id;
        });

        // When - Try to lock the same job twice
        DelayedJob lock1 = QuarkusTransaction.requiringNew().call(() ->
            delayedJobService.getDelayedJobToWorkOn(jobId)
        );

        DelayedJob lock2 = QuarkusTransaction.requiringNew().call(() ->
            delayedJobService.getDelayedJobToWorkOn(jobId)
        );

        // Then - Only the first lock should succeed
        assertNotNull(lock1);
        assertNull(lock2);
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    private DelayedJob createReadyJob(String actorId) {
        DelayedJob job = new DelayedJob();
        job.actorId = actorId;
        job.queueName = "OrderEmailJobHandler";
        job.priority = 10;
        job.runAt = Instant.now().minus(1, ChronoUnit.HOURS);
        job.locked = false;
        job.complete = false;
        job.attempts = 0;
        job.persist();
        return job;
    }
}
