package villagecompute.calendar.services;

import java.io.BufferedReader;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.Event;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.data.repositories.EventRepository;

/**
 * Service layer for event business logic operations. Handles event CRUD operations, validation,
 * authorization, and bulk imports.
 *
 * <p>Events are custom dates/occasions that users add to their calendars. Each event must be within
 * the calendar's year and have valid data.
 */
@ApplicationScoped
public class EventService {

    private static final Logger LOG = Logger.getLogger(EventService.class);

    // Hex color pattern (e.g., #FF5733 or #ABC)
    private static final Pattern HEX_COLOR_PATTERN =
            Pattern.compile("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$");

    @Inject EventRepository eventRepository;

    @Inject CalendarService calendarService;

    @Inject ObjectMapper objectMapper;

    // ========== CREATE OPERATIONS ==========

    /**
     * Add a new event to a calendar. Validates that: - Event date is within calendar year - Event
     * text is max 500 characters - Emoji is valid Unicode (if provided) - User has write access to
     * the calendar
     *
     * @param calendarId Calendar ID
     * @param eventDate Event date
     * @param eventText Event text/description (max 500 chars)
     * @param emoji Unicode emoji (optional)
     * @param color Hex color code (optional, e.g., #FF5733)
     * @param currentUser User adding the event
     * @return Created event
     * @throws IllegalArgumentException if validation fails
     * @throws SecurityException if user is not authorized
     */
    @Transactional
    public Event addEvent(
            UUID calendarId,
            LocalDate eventDate,
            String eventText,
            String emoji,
            String color,
            CalendarUser currentUser) {

        LOG.infof(
                "Adding event: calendarId=%s, date=%s, userId=%s",
                calendarId, eventDate, currentUser != null ? currentUser.id : null);

        // Validate input
        if (calendarId == null) {
            throw new IllegalArgumentException("Calendar ID is required");
        }
        if (eventDate == null) {
            throw new IllegalArgumentException("Event date is required");
        }

        // Get calendar and check authorization (CalendarService handles authorization)
        UserCalendar calendar = calendarService.getCalendar(calendarId, currentUser);

        // Check write access
        checkWriteAccess(calendar, currentUser);

        // Validate event data
        validateEventInput(calendar, eventDate, eventText, emoji, color);

        // Create event entity
        Event event = new Event();
        event.calendar = calendar;
        event.eventDate = eventDate;
        event.eventText = eventText;
        event.emoji = emoji;
        event.color = color;

        // Persist
        event.persist();

        LOG.infof("Created event: id=%s, calendarId=%s, date=%s", event.id, calendarId, eventDate);

        return event;
    }

    // ========== UPDATE OPERATIONS ==========

    /**
     * Update an existing event's text, emoji, or color. Cannot change the event date - delete and
     * recreate instead.
     *
     * @param eventId Event ID
     * @param eventText New event text (null to keep current)
     * @param emoji New emoji (null to keep current)
     * @param color New color (null to keep current)
     * @param currentUser User updating the event
     * @return Updated event
     * @throws IllegalArgumentException if event not found or validation fails
     * @throws SecurityException if user is not authorized
     */
    @Transactional
    public Event updateEvent(
            UUID eventId, String eventText, String emoji, String color, CalendarUser currentUser) {

        LOG.infof(
                "Updating event: id=%s, userId=%s",
                eventId, currentUser != null ? currentUser.id : null);

        // Find event
        Event event = Event.findById(eventId);
        if (event == null) {
            LOG.errorf("Event not found: %s", eventId);
            throw new IllegalArgumentException("Event not found: " + eventId);
        }

        // Check write access to calendar
        checkWriteAccess(event.calendar, currentUser);

        // Validate updates
        if (eventText != null) {
            validateEventText(eventText);
            event.eventText = eventText;
        }
        if (emoji != null) {
            validateEmoji(emoji);
            event.emoji = emoji;
        }
        if (color != null) {
            validateColor(color);
            event.color = color;
        }

        // Persist
        event.persist();

        LOG.infof("Updated event: id=%s, calendarId=%s", event.id, event.calendar.id);

        return event;
    }

    // ========== DELETE OPERATIONS ==========

    /**
     * Delete an event.
     *
     * @param eventId Event ID
     * @param currentUser User deleting the event
     * @return true if deleted successfully
     * @throws IllegalArgumentException if event not found
     * @throws SecurityException if user is not authorized
     */
    @Transactional
    public boolean deleteEvent(UUID eventId, CalendarUser currentUser) {
        LOG.infof(
                "Deleting event: id=%s, userId=%s",
                eventId, currentUser != null ? currentUser.id : null);

        // Find event
        Event event = Event.findById(eventId);
        if (event == null) {
            LOG.errorf("Event not found: %s", eventId);
            throw new IllegalArgumentException("Event not found: " + eventId);
        }

        // Check write access
        checkWriteAccess(event.calendar, currentUser);

        // Delete
        event.delete();

        LOG.infof("Deleted event: id=%s, calendarId=%s", eventId, event.calendar.id);

        return true;
    }

    // ========== READ OPERATIONS ==========

    /**
     * List all events for a calendar, optionally filtered by date range.
     *
     * @param calendarId Calendar ID
     * @param startDate Optional start date filter (inclusive)
     * @param endDate Optional end date filter (inclusive)
     * @param currentUser User requesting the list
     * @return List of events
     * @throws IllegalArgumentException if calendar not found
     * @throws SecurityException if user is not authorized
     */
    public List<Event> listEvents(
            UUID calendarId, LocalDate startDate, LocalDate endDate, CalendarUser currentUser) {

        LOG.debugf(
                "Listing events: calendarId=%s, startDate=%s, endDate=%s, userId=%s",
                calendarId, startDate, endDate, currentUser != null ? currentUser.id : null);

        // Get calendar and check authorization (read access)
        UserCalendar calendar = calendarService.getCalendar(calendarId, currentUser);

        // Fetch events
        List<Event> events;
        if (startDate != null && endDate != null) {
            // Validate date range
            if (startDate.isAfter(endDate)) {
                throw new IllegalArgumentException(
                        "Start date must be before or equal to end date");
            }
            events = eventRepository.findByDateRange(calendarId, startDate, endDate);
        } else {
            events = eventRepository.findByCalendarId(calendarId);
        }

        LOG.debugf("Found %d events for calendar %s", events.size(), calendarId);

        return events;
    }

    /**
     * Get a single event by ID.
     *
     * @param eventId Event ID
     * @param currentUser User requesting the event
     * @return Event
     * @throws IllegalArgumentException if event not found
     * @throws SecurityException if user is not authorized
     */
    public Event getEvent(UUID eventId, CalendarUser currentUser) {
        LOG.debugf(
                "Getting event: id=%s, userId=%s",
                eventId, currentUser != null ? currentUser.id : null);

        Event event = Event.findById(eventId);
        if (event == null) {
            LOG.errorf("Event not found: %s", eventId);
            throw new IllegalArgumentException("Event not found: " + eventId);
        }

        // Check read access to calendar
        calendarService.getCalendar(event.calendar.id, currentUser);

        return event;
    }

    // ========== BULK OPERATIONS ==========

    /**
     * Import multiple events from JSON format. JSON format: [{"date": "2025-01-01", "text": "New
     * Year", "emoji": "🎉", "color": "#FF5733"}, ...]
     *
     * @param calendarId Calendar ID
     * @param jsonData JSON array of events
     * @param currentUser User importing events
     * @return List of created events
     * @throws IllegalArgumentException if JSON is invalid or validation fails
     * @throws SecurityException if user is not authorized
     */
    @Transactional
    public List<Event> importEventsFromJson(
            UUID calendarId, String jsonData, CalendarUser currentUser) {

        LOG.infof(
                "Importing events from JSON: calendarId=%s, userId=%s",
                calendarId, currentUser != null ? currentUser.id : null);

        // Get calendar and check authorization
        UserCalendar calendar = calendarService.getCalendar(calendarId, currentUser);
        checkWriteAccess(calendar, currentUser);

        List<Event> createdEvents = new ArrayList<>();

        try {
            // Parse JSON
            JsonNode rootNode = objectMapper.readTree(jsonData);

            if (!rootNode.isArray()) {
                throw new IllegalArgumentException("JSON must be an array of events");
            }

            // Process each event
            for (JsonNode eventNode : rootNode) {
                String dateStr = eventNode.has("date") ? eventNode.get("date").asText() : null;
                String text = eventNode.has("text") ? eventNode.get("text").asText() : null;
                String emoji = eventNode.has("emoji") ? eventNode.get("emoji").asText() : null;
                String color = eventNode.has("color") ? eventNode.get("color").asText() : null;

                if (dateStr == null || dateStr.isBlank()) {
                    LOG.warnf("Skipping event with missing date: %s", eventNode);
                    continue;
                }

                LocalDate eventDate = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);

                // Validate and create event
                validateEventInput(calendar, eventDate, text, emoji, color);

                Event event = new Event();
                event.calendar = calendar;
                event.eventDate = eventDate;
                event.eventText = text;
                event.emoji = emoji;
                event.color = color;
                event.persist();

                createdEvents.add(event);
            }

            LOG.infof(
                    "Imported %d events from JSON for calendar %s",
                    createdEvents.size(), calendarId);

        } catch (Exception e) {
            LOG.errorf(e, "Failed to import events from JSON");
            throw new IllegalArgumentException("Invalid JSON format: " + e.getMessage(), e);
        }

        return createdEvents;
    }

    /**
     * Import multiple events from CSV format. CSV format: date,text,emoji,color (header row
     * required) Example: 2025-01-01,New Year,🎉,#FF5733
     *
     * @param calendarId Calendar ID
     * @param csvData CSV data with header row
     * @param currentUser User importing events
     * @return List of created events
     * @throws IllegalArgumentException if CSV is invalid or validation fails
     * @throws SecurityException if user is not authorized
     */
    @Transactional
    public List<Event> importEventsFromCsv(
            UUID calendarId, String csvData, CalendarUser currentUser) {

        LOG.infof(
                "Importing events from CSV: calendarId=%s, userId=%s",
                calendarId, currentUser != null ? currentUser.id : null);

        // Get calendar and check authorization
        UserCalendar calendar = calendarService.getCalendar(calendarId, currentUser);
        checkWriteAccess(calendar, currentUser);

        List<Event> createdEvents = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new StringReader(csvData))) {
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                // Skip header row
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                // Skip empty lines
                if (line.trim().isEmpty()) {
                    continue;
                }

                // Parse CSV line (simple comma split - doesn't handle quoted commas)
                String[] parts = line.split(",", -1);
                if (parts.length < 1) {
                    LOG.warnf("Skipping invalid CSV line: %s", line);
                    continue;
                }

                String dateStr = parts[0].trim();
                String text = parts.length > 1 ? parts[1].trim() : null;
                String emoji = parts.length > 2 ? parts[2].trim() : null;
                String color = parts.length > 3 ? parts[3].trim() : null;

                if (dateStr.isEmpty()) {
                    LOG.warnf("Skipping CSV line with missing date: %s", line);
                    continue;
                }

                LocalDate eventDate = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);

                // Validate and create event
                validateEventInput(calendar, eventDate, text, emoji, color);

                Event event = new Event();
                event.calendar = calendar;
                event.eventDate = eventDate;
                event.eventText = text != null && !text.isEmpty() ? text : null;
                event.emoji = emoji != null && !emoji.isEmpty() ? emoji : null;
                event.color = color != null && !color.isEmpty() ? color : null;
                event.persist();

                createdEvents.add(event);
            }

            LOG.infof(
                    "Imported %d events from CSV for calendar %s",
                    createdEvents.size(), calendarId);

        } catch (DateTimeParseException e) {
            LOG.errorf(e, "Failed to parse date in CSV");
            throw new IllegalArgumentException(
                    "Invalid date format in CSV. Use ISO format (YYYY-MM-DD)", e);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to import events from CSV");
            throw new IllegalArgumentException("Invalid CSV format: " + e.getMessage(), e);
        }

        return createdEvents;
    }

    // ========== AUTHORIZATION HELPERS ==========

    /**
     * Check if user has write access to a calendar. Delegates to CalendarService for consistency.
     *
     * @param calendar Calendar to check
     * @param currentUser User requesting access
     * @throws SecurityException if access denied
     */
    private void checkWriteAccess(UserCalendar calendar, CalendarUser currentUser) {
        // Anonymous users cannot modify calendars
        if (currentUser == null) {
            LOG.warnf("Unauthorized write access: calendarId=%s (no user)", calendar.id);
            throw new SecurityException("Authentication required to modify events");
        }

        // Admin can modify everything
        if (Boolean.TRUE.equals(currentUser.isAdmin)) {
            return;
        }

        // Owner can modify their own calendars
        if (calendar.user != null && calendar.user.id.equals(currentUser.id)) {
            return;
        }

        // Unauthorized
        LOG.warnf(
                "Unauthorized write access: calendarId=%s, userId=%s", calendar.id, currentUser.id);
        throw new SecurityException("You do not have permission to modify events on this calendar");
    }

    // ========== VALIDATION HELPERS ==========

    /**
     * Validate event input data.
     *
     * @param calendar Parent calendar
     * @param eventDate Event date
     * @param eventText Event text
     * @param emoji Emoji
     * @param color Color
     * @throws IllegalArgumentException if validation fails
     */
    private void validateEventInput(
            UserCalendar calendar,
            LocalDate eventDate,
            String eventText,
            String emoji,
            String color) {

        // Validate date is within calendar year
        if (eventDate.getYear() != calendar.year) {
            throw new IllegalArgumentException(
                    String.format(
                            "Event date must be within calendar year %d (got %d)",
                            calendar.year, eventDate.getYear()));
        }

        // Validate event text
        validateEventText(eventText);

        // Validate emoji
        validateEmoji(emoji);

        // Validate color
        validateColor(color);
    }

    /**
     * Validate event text.
     *
     * @param eventText Event text
     * @throws IllegalArgumentException if validation fails
     */
    private void validateEventText(String eventText) {
        if (eventText != null && eventText.length() > 500) {
            throw new IllegalArgumentException(
                    String.format(
                            "Event text must be 500 characters or less (got %d characters)",
                            eventText.length()));
        }
    }

    /**
     * Validate emoji is valid Unicode emoji sequence.
     *
     * @param emoji Emoji string
     * @throws IllegalArgumentException if validation fails
     */
    private void validateEmoji(String emoji) {
        if (emoji == null || emoji.isEmpty()) {
            return; // Emoji is optional
        }

        // Check length
        if (emoji.length() > 100) {
            throw new IllegalArgumentException(
                    String.format(
                            "Emoji must be 100 characters or less (got %d characters)",
                            emoji.length()));
        }

        // Check if contains valid emoji characters
        // This is a simplified check - validates common emoji Unicode ranges
        boolean hasEmoji = false;
        for (int i = 0; i < emoji.length(); ) {
            int codePoint = emoji.codePointAt(i);

            // Check for common emoji ranges
            // Emoticons and symbols (0x1F300-0x1F9FF)
            // Misc symbols (0x2600-0x27BF)
            // Variation selectors (0xFE00-0xFE0F)
            // Zero-width joiner (0x200D)
            // Regional indicators for flags (0x1F1E6-0x1F1FF)
            // Skin tone modifiers (0x1F3FB-0x1F3FF)
            if ((codePoint >= 0x1F300 && codePoint <= 0x1F9FF)
                    || (codePoint >= 0x2600 && codePoint <= 0x27BF)
                    || (codePoint >= 0xFE00 && codePoint <= 0xFE0F)
                    || codePoint == 0x200D
                    || (codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF)
                    || (codePoint >= 0x1F3FB && codePoint <= 0x1F3FF)
                    || Character.getType(codePoint) == Character.OTHER_SYMBOL) {
                hasEmoji = true;
            }

            i += Character.charCount(codePoint);
        }

        if (!hasEmoji) {
            throw new IllegalArgumentException(
                    "Invalid emoji: must be valid Unicode emoji sequence");
        }
    }

    /**
     * Validate color is valid hex color code.
     *
     * @param color Color string
     * @throws IllegalArgumentException if validation fails
     */
    private void validateColor(String color) {
        if (color == null || color.isEmpty()) {
            return; // Color is optional
        }

        if (!HEX_COLOR_PATTERN.matcher(color).matches()) {
            throw new IllegalArgumentException(
                    "Invalid color: must be hex color code (e.g., #FF5733 or #ABC)");
        }
    }
}
