package villagecompute.calendar.api.graphql.dataloader;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.jboss.logging.Logger;
import villagecompute.calendar.data.models.Event;
import villagecompute.calendar.data.repositories.EventRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * DataLoader for batch loading Event entities for multiple calendars.
 * Prevents N+1 queries when fetching events for multiple calendars.
 *
 * Example: Fetching 10 calendars that each have events will result in 2 queries:
 * 1. Query to fetch 10 calendars
 * 2. Query to batch fetch all events (SELECT * FROM event WHERE calendar_id IN (...))
 *
 * Without DataLoader, it would be 11 queries (1 for calendars + 10 separate event queries).
 *
 * Note: This DataLoader returns a LIST of events per calendar, not a single event.
 */
@ApplicationScoped
public class EventDataLoader {

    private static final Logger LOG = Logger.getLogger(EventDataLoader.class);

    @Inject
    EventRepository eventRepository;

    /**
     * Create a DataLoader for batching event queries.
     * This method is called by the GraphQL context for each request.
     *
     * @return DataLoader for Event lists keyed by calendar ID
     */
    public DataLoader<UUID, List<Event>> createDataLoader() {
        return DataLoaderFactory.newDataLoader(this::batchLoadEvents);
    }

    /**
     * Batch load events for multiple calendars.
     * Called by DataLoader when multiple calendar IDs are requested.
     *
     * @param calendarIds List of calendar IDs to load events for
     * @return CompletionStage with list of event lists in the same order as requested calendar IDs
     */
    private CompletionStage<List<List<Event>>> batchLoadEvents(List<UUID> calendarIds) {
        LOG.debugf("Batch loading events for %d calendars", calendarIds.size());

        return CompletableFuture.supplyAsync(() -> {
            // Fetch all events in a single query
            List<Event> events = eventRepository.findByCalendarIds(calendarIds);

            // Group events by calendar ID
            Map<UUID, List<Event>> eventsByCalendar = events.stream()
                .collect(Collectors.groupingBy(event -> event.calendar.id));

            // Return event lists in the same order as requested calendar IDs
            // DataLoader requires the result list to be in the same order as the input
            // Return empty list for calendars with no events
            return calendarIds.stream()
                .map(calendarId -> eventsByCalendar.getOrDefault(calendarId, List.of()))
                .collect(Collectors.toList());
        });
    }
}
