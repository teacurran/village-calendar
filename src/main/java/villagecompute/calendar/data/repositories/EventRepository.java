package villagecompute.calendar.data.repositories;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import villagecompute.calendar.data.models.Event;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repository for Event entities.
 * Provides custom query methods for event management and filtering.
 */
@ApplicationScoped
public class EventRepository implements PanacheRepository<Event> {

    /**
     * Find all events for a specific calendar.
     *
     * @param calendarId Calendar ID
     * @return List of events ordered by date ascending
     */
    public List<Event> findByCalendarId(UUID calendarId) {
        return find("calendar.id = ?1 ORDER BY eventDate ASC", calendarId).list();
    }

    /**
     * Find events for a calendar within a specific date range.
     * This is one of the required custom query methods from the task specification.
     *
     * @param calendarId Calendar ID
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return List of events in the date range ordered by date
     */
    public List<Event> findByDateRange(UUID calendarId, LocalDate startDate, LocalDate endDate) {
        return find("calendar.id = ?1 AND eventDate >= ?2 AND eventDate <= ?3 ORDER BY eventDate ASC",
                    calendarId, startDate, endDate).list();
    }

    /**
     * Find a specific event by calendar and date.
     *
     * @param calendarId Calendar ID
     * @param eventDate Event date
     * @return Event if found, null otherwise
     */
    public Event findByCalendarAndDate(UUID calendarId, LocalDate eventDate) {
        return find("calendar.id = ?1 AND eventDate = ?2", calendarId, eventDate).firstResult();
    }

    /**
     * Count events for a specific calendar.
     *
     * @param calendarId Calendar ID
     * @return Number of events
     */
    public long countByCalendar(UUID calendarId) {
        return count("calendar.id = ?1", calendarId);
    }

    /**
     * Delete all events for a specific calendar.
     *
     * @param calendarId Calendar ID
     * @return Number of deleted events
     */
    public long deleteByCalendar(UUID calendarId) {
        return delete("calendar.id = ?1", calendarId);
    }

    /**
     * Find events by date across all calendars (useful for admin queries).
     *
     * @param eventDate Event date
     * @return List of events on that date
     */
    public List<Event> findByDate(LocalDate eventDate) {
        return find("eventDate = ?1 ORDER BY calendar.id", eventDate).list();
    }

    /**
     * Find events containing specific text (case-insensitive search).
     *
     * @param calendarId Calendar ID
     * @param searchText Text to search for
     * @return List of matching events
     */
    public List<Event> searchByText(UUID calendarId, String searchText) {
        return find("calendar.id = ?1 AND LOWER(eventText) LIKE ?2 ORDER BY eventDate ASC",
                    calendarId, "%" + searchText.toLowerCase() + "%").list();
    }

    /**
     * Batch load events for multiple calendars.
     * Used by DataLoader to prevent N+1 queries.
     *
     * @param calendarIds List of calendar IDs
     * @return List of events for all the calendars ordered by calendar ID and date
     */
    public List<Event> findByCalendarIds(List<UUID> calendarIds) {
        if (calendarIds == null || calendarIds.isEmpty()) {
            return List.of();
        }
        return find("calendar.id IN ?1 ORDER BY calendar.id, eventDate ASC", calendarIds).list();
    }
}
