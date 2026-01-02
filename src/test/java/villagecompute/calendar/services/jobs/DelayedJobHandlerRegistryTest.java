package villagecompute.calendar.services.jobs;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests for DelayedJobHandlerRegistry - auto-discovers and registers job handlers.
 */
@QuarkusTest
class DelayedJobHandlerRegistryTest {

    @Inject
    DelayedJobHandlerRegistry registry;

    // ========== getHandler tests ==========

    @Test
    void testGetHandler_ExistingHandler_ReturnsHandler() {
        Optional<DelayedJobHandler> handler = registry.getHandler("OrderEmailJobHandler");

        assertTrue(handler.isPresent());
        assertNotNull(handler.get());
    }

    @Test
    void testGetHandler_OrderCancellationHandler_ReturnsHandler() {
        Optional<DelayedJobHandler> handler = registry.getHandler("OrderCancellationJobHandler");

        assertTrue(handler.isPresent());
    }

    @Test
    void testGetHandler_ShippingNotificationHandler_ReturnsHandler() {
        Optional<DelayedJobHandler> handler = registry.getHandler("ShippingNotificationJobHandler");

        assertTrue(handler.isPresent());
    }

    @Test
    void testGetHandler_NonExistentHandler_ReturnsEmpty() {
        Optional<DelayedJobHandler> handler = registry.getHandler("NonExistentHandler");

        assertFalse(handler.isPresent());
    }

    @Test
    void testGetHandler_NullQueueName_ReturnsEmpty() {
        Optional<DelayedJobHandler> handler = registry.getHandler(null);

        assertFalse(handler.isPresent());
    }

    @Test
    void testGetHandler_EmptyQueueName_ReturnsEmpty() {
        Optional<DelayedJobHandler> handler = registry.getHandler("");

        assertFalse(handler.isPresent());
    }

    // ========== getMetadata tests ==========

    @Test
    void testGetMetadata_OrderEmailJobHandler_ReturnsCorrectMetadata() {
        Optional<DelayedJobHandlerRegistry.HandlerMetadata> metadata = registry.getMetadata(OrderEmailJobHandler.class);

        assertTrue(metadata.isPresent());
        assertEquals("OrderEmailJobHandler", metadata.get().queueName());
        assertEquals(10, metadata.get().priority());
        assertEquals("Order confirmation email sender", metadata.get().description());
        assertEquals(OrderEmailJobHandler.class, metadata.get().handlerClass());
    }

    @Test
    void testGetMetadata_OrderCancellationJobHandler_ReturnsMetadata() {
        Optional<DelayedJobHandlerRegistry.HandlerMetadata> metadata = registry
                .getMetadata(OrderCancellationJobHandler.class);

        assertTrue(metadata.isPresent());
        assertNotNull(metadata.get().queueName());
        assertTrue(metadata.get().priority() > 0);
    }

    @Test
    void testGetMetadata_UnregisteredHandler_ReturnsEmpty() {
        // Using a class that is not a registered handler
        Optional<DelayedJobHandlerRegistry.HandlerMetadata> metadata = registry
                .getMetadata(TestUnregisteredHandler.class);

        assertFalse(metadata.isPresent());
    }

    // ========== getRegisteredQueues tests ==========

    @Test
    void testGetRegisteredQueues_ReturnsAllQueues() {
        Set<String> queues = registry.getRegisteredQueues();

        assertNotNull(queues);
        assertFalse(queues.isEmpty());
        assertTrue(queues.contains("OrderEmailJobHandler"));
        assertTrue(queues.contains("OrderCancellationJobHandler"));
        assertTrue(queues.contains("ShippingNotificationJobHandler"));
    }

    @Test
    void testGetRegisteredQueues_IsUnmodifiable() {
        Set<String> queues = registry.getRegisteredQueues();

        assertThrows(UnsupportedOperationException.class, () -> queues.add("NewQueue"));
    }

    @Test
    void testGetRegisteredQueues_ConsistentWithGetHandler() {
        Set<String> queues = registry.getRegisteredQueues();

        // Every queue should have a corresponding handler
        for (String queueName : queues) {
            Optional<DelayedJobHandler> handler = registry.getHandler(queueName);
            assertTrue(handler.isPresent(), "Queue " + queueName + " should have a handler");
        }
    }

    // ========== Handler metadata record tests ==========

    @Test
    void testHandlerMetadata_RecordComponents() {
        Optional<DelayedJobHandlerRegistry.HandlerMetadata> metadata = registry.getMetadata(OrderEmailJobHandler.class);
        assertTrue(metadata.isPresent());

        DelayedJobHandlerRegistry.HandlerMetadata m = metadata.get();

        // Test record components
        assertEquals("OrderEmailJobHandler", m.queueName());
        assertEquals(10, m.priority());
        assertNotNull(m.description());
        assertEquals(OrderEmailJobHandler.class, m.handlerClass());
    }

    @Test
    void testHandlerMetadata_Equality() {
        Optional<DelayedJobHandlerRegistry.HandlerMetadata> metadata1 = registry
                .getMetadata(OrderEmailJobHandler.class);
        Optional<DelayedJobHandlerRegistry.HandlerMetadata> metadata2 = registry
                .getMetadata(OrderEmailJobHandler.class);

        assertTrue(metadata1.isPresent());
        assertTrue(metadata2.isPresent());
        assertEquals(metadata1.get(), metadata2.get());
    }

    // ========== Handler default queue name tests ==========

    @Test
    void testDelayedJobHandler_DefaultQueueName_ReturnsClassSimpleName() {
        // Test that actual registered handlers return their class simple name
        Optional<DelayedJobHandler> handler = registry.getHandler("OrderEmailJobHandler");
        assertTrue(handler.isPresent());
        assertEquals("OrderEmailJobHandler", handler.get().getQueueName());
    }

    @Test
    void testDelayedJobHandler_QueueNames_MatchRegisteredQueues() {
        // Verify all handlers have queue names that match their registration
        for (String queueName : registry.getRegisteredQueues()) {
            Optional<DelayedJobHandler> handler = registry.getHandler(queueName);
            assertTrue(handler.isPresent());
            assertEquals(queueName, handler.get().getQueueName());
        }
    }

    // Test class that is not registered
    private abstract static class TestUnregisteredHandler implements DelayedJobHandler {
    }
}
