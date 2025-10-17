package villagecompute.calendar.data.models;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Event entity representing custom dates/events on a calendar.
 * Events must be within the calendar's year and have valid text, emoji, and color values.
 */
@Entity
@Table(
    name = "events",
    indexes = {
        @Index(name = "idx_events_calendar", columnList = "calendar_id, event_date"),
        @Index(name = "idx_events_calendar_date_range", columnList = "calendar_id, event_date DESC")
    }
)
public class Event extends DefaultPanacheEntityWithTimestamps {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calendar_id", nullable = false, foreignKey = @ForeignKey(name = "fk_events_calendar"))
    public UserCalendar calendar;

    @NotNull
    @Column(name = "event_date", nullable = false)
    public LocalDate eventDate;

    @Size(max = 500)
    @Column(name = "event_text", length = 500)
    public String eventText;

    @Size(max = 100)
    @Column(name = "emoji", length = 100)
    public String emoji;

    @Size(max = 20)
    @Column(name = "color", length = 20)
    public String color;

    // ========== QUERY METHODS (Panache Active Record Pattern) ==========

    /**
     * Find all events for a specific calendar.
     *
     * @param calendarId Calendar ID
     * @return Query of events ordered by date
     */
    public static PanacheQuery<Event> findByCalendar(UUID calendarId) {
        return find("calendar.id = ?1 ORDER BY eventDate ASC", calendarId);
    }

    /**
     * Find events for a calendar within a specific date range.
     *
     * @param calendarId Calendar ID
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return List of events in the date range
     */
    public static List<Event> findByDateRange(UUID calendarId, LocalDate startDate, LocalDate endDate) {
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
    public static Event findByCalendarAndDate(UUID calendarId, LocalDate eventDate) {
        return find("calendar.id = ?1 AND eventDate = ?2", calendarId, eventDate).firstResult();
    }

    /**
     * Count events for a calendar.
     *
     * @param calendarId Calendar ID
     * @return Number of events
     */
    public static long countByCalendar(UUID calendarId) {
        return count("calendar.id = ?1", calendarId);
    }

    /**
     * Delete all events for a calendar.
     *
     * @param calendarId Calendar ID
     * @return Number of deleted events
     */
    public static long deleteByCalendar(UUID calendarId) {
        return delete("calendar.id = ?1", calendarId);
    }
}
