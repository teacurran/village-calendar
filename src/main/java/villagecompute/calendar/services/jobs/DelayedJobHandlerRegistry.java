package villagecompute.calendar.services.jobs;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkus.runtime.Startup;

/**
 * Auto-discovers and registers all DelayedJobHandler implementations. Uses CDI Instance to find all beans implementing
 * DelayedJobHandler.
 */
@ApplicationScoped
@Startup
public class DelayedJobHandlerRegistry {

    private static final Logger LOG = Logger.getLogger(DelayedJobHandlerRegistry.class);

    @Inject
    Instance<DelayedJobHandler> allHandlers;

    private final Map<String, DelayedJobHandler> handlersByQueue = new HashMap<>();
    private final Map<Class<? extends DelayedJobHandler>, HandlerMetadata> metadataByClass = new HashMap<>();

    @PostConstruct
    void discoverHandlers() {
        for (DelayedJobHandler handler : allHandlers) {
            Class<? extends DelayedJobHandler> handlerClass = getActualClass(handler);

            DelayedJobConfig config = handlerClass.getAnnotation(DelayedJobConfig.class);

            int priority = 5;
            String description = "";
            if (config != null) {
                priority = config.priority();
                description = config.description();
            } else {
                LOG.warnf("Handler %s missing @DelayedJobConfig annotation, using defaults",
                        handlerClass.getSimpleName());
            }

            String queueName = handler.getQueueName();

            if (handlersByQueue.containsKey(queueName)) {
                throw new IllegalStateException("Duplicate queue name '" + queueName + "' for handlers: "
                        + handlersByQueue.get(queueName).getClass().getName() + " and " + handlerClass.getName());
            }

            HandlerMetadata metadata = new HandlerMetadata(queueName, priority, description, handlerClass);

            handlersByQueue.put(queueName, handler);
            metadataByClass.put(handlerClass, metadata);

            LOG.infof("Registered handler: %s (queue=%s, priority=%d)", handlerClass.getSimpleName(), queueName,
                    priority);
        }

        LOG.infof("Discovered %d delayed job handlers", handlersByQueue.size());
    }

    /** Get the actual handler class, unwrapping Quarkus proxies. */
    @SuppressWarnings("unchecked")
    private Class<? extends DelayedJobHandler> getActualClass(DelayedJobHandler handler) {
        Class<?> clazz = handler.getClass();
        if (clazz.getName().contains("_ClientProxy") || clazz.getName().contains("_Subclass")) {
            clazz = clazz.getSuperclass();
        }
        return (Class<? extends DelayedJobHandler>) clazz;
    }

    /** Get handler by queue name (for job execution). */
    public Optional<DelayedJobHandler> getHandler(String queueName) {
        return Optional.ofNullable(handlersByQueue.get(queueName));
    }

    /** Get metadata for a handler class (for job creation). */
    public Optional<HandlerMetadata> getMetadata(Class<? extends DelayedJobHandler> handlerClass) {
        return Optional.ofNullable(metadataByClass.get(handlerClass));
    }

    /** Get all registered queue names. */
    public Set<String> getRegisteredQueues() {
        return Collections.unmodifiableSet(handlersByQueue.keySet());
    }

    /** Metadata about a registered handler. */
    public record HandlerMetadata(String queueName, int priority, String description,
            Class<? extends DelayedJobHandler> handlerClass) {
    }
}
