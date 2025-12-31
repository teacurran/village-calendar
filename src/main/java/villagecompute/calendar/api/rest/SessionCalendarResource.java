package villagecompute.calendar.api.rest;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import villagecompute.calendar.api.types.ErrorResponse;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.services.CalendarGenerationService;
import villagecompute.calendar.services.CalendarRenderingService;
import villagecompute.calendar.services.SessionService;

import io.quarkus.logging.Log;

@Path("/session-calendar")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SessionCalendarResource {

    private static final String ERROR_GENERATING_SVG = "Error generating calendar SVG";
    private static final String CONFIGURATION = "configuration";
    private static final String COLOR_GRAY = "#c1c1c1";
    private static final String COLOR_BLACK = "#000000";

    @Inject
    SessionService sessionService;

    @Inject
    CalendarRenderingService calendarRenderingService;

    @Inject
    CalendarGenerationService calendarGenerationService;

    @Inject
    ObjectMapper objectMapper;

    // DTOs
    public static class SessionCalendarDTO {
        public UUID id;
        public String name;
        public JsonNode configuration;
        public UUID templateId;
        public String sessionId;
        public boolean isPublic;

        public static SessionCalendarDTO from(UserCalendar calendar) {
            SessionCalendarDTO dto = new SessionCalendarDTO();
            dto.id = calendar.id;
            dto.name = calendar.name;
            dto.configuration = calendar.configuration;
            dto.templateId = calendar.template != null ? calendar.template.id : null;
            dto.sessionId = calendar.sessionId;
            dto.isPublic = calendar.isPublic;
            return dto;
        }
    }

    public static class UpdateCalendarRequest {
        public JsonNode configuration;
        public String name;
    }

    /** Get the current session's active calendar */
    @GET
    @Path("/current")
    public Response getCurrentCalendar(@HeaderParam("X-Session-ID") String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ErrorResponse.of("No session found")).build();
        }

        UserCalendar calendar = UserCalendar.find("sessionId", sessionId).firstResult();

        if (calendar == null) {
            return Response.status(Response.Status.NOT_FOUND).entity(ErrorResponse.of("No calendar for this session"))
                    .build();
        }

        return Response.ok(SessionCalendarDTO.from(calendar)).build();
    }

    /** Create or update a calendar for the current session */
    @POST
    @Path("/save")
    @Transactional
    public Response saveSessionCalendar(@HeaderParam("X-Session-ID") String sessionId, UpdateCalendarRequest request) {
        if (sessionId == null || sessionId.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ErrorResponse.of("No session found")).build();
        }

        // Check if this session already has a working calendar
        UserCalendar calendar = UserCalendar.<UserCalendar>find("sessionId", sessionId).firstResult();

        if (calendar == null) {
            // Create new calendar for this session
            calendar = new UserCalendar();
            calendar.sessionId = sessionId;
            calendar.name = request.name != null ? request.name : "Untitled Calendar";
            calendar.isPublic = true;
            // Set default year
            calendar.year = java.time.LocalDate.now().getYear();
        }

        // Update configuration
        if (request.configuration != null) {
            calendar.configuration = request.configuration;

            // Extract year from configuration if present
            if (request.configuration.has("year")) {
                calendar.year = request.configuration.get("year").asInt();
            }
        } else if (calendar.configuration == null) {
            calendar.configuration = objectMapper.createObjectNode();
        }

        if (request.name != null) {
            calendar.name = request.name;
        }

        // Generate SVG preview if configuration exists
        try {
            if (calendar.configuration != null) {
                CalendarRenderingService.CalendarConfig config = objectMapper.treeToValue(calendar.configuration,
                        CalendarRenderingService.CalendarConfig.class);
                calendar.generatedSvg = calendarRenderingService.generateCalendarSVG(config);
            }
        } catch (Exception e) {
            Log.error(ERROR_GENERATING_SVG, e);
        }

        calendar.persist();

        Map<String, Object> response = new HashMap<>();
        response.put("id", calendar.id);
        response.put("sessionId", sessionId);

        return Response.ok(response).build();
    }

    /** Create calendar from template for session */
    @POST
    @Path("/from-template/{templateId}")
    @Transactional
    public Response createFromTemplate(@HeaderParam("X-Session-ID") String sessionId,
            @PathParam("templateId") UUID templateId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ErrorResponse.of("No session found")).build();
        }

        CalendarTemplate template = CalendarTemplate.findById(templateId);
        if (template == null || !template.isActive) {
            return Response.status(Response.Status.NOT_FOUND).entity(ErrorResponse.of("Template not found")).build();
        }

        // Create new calendar from template
        UserCalendar calendar = new UserCalendar();
        calendar.sessionId = sessionId;
        calendar.template = template;
        calendar.name = template.name;
        calendar.configuration = template.configuration;
        calendar.isPublic = true;

        // Extract year from template configuration
        if (template.configuration != null && template.configuration.has("year")) {
            calendar.year = template.configuration.get("year").asInt();
        } else {
            calendar.year = java.time.LocalDate.now().getYear();
        }

        // Generate SVG
        try {
            CalendarRenderingService.CalendarConfig config = objectMapper.treeToValue(template.configuration,
                    CalendarRenderingService.CalendarConfig.class);
            calendar.generatedSvg = calendarRenderingService.generateCalendarSVG(config);
        } catch (Exception e) {
            Log.error(ERROR_GENERATING_SVG, e);
        }

        calendar.persist();

        Map<String, Object> response = new HashMap<>();
        response.put("id", calendar.id);
        response.put(CONFIGURATION, calendar.configuration);

        return Response.ok(response).build();
    }

    /** Get a calendar by ID Returns the calendar and whether it belongs to the current session */
    @GET
    @Path("/{id}")
    public Response getCalendar(@HeaderParam("X-Session-ID") String sessionId, @PathParam("id") UUID id) {

        UserCalendar calendar = UserCalendar.findById(id);
        if (calendar == null) {
            return Response.status(Response.Status.NOT_FOUND).entity(ErrorResponse.of("Calendar not found")).build();
        }

        // Check if this is the session's own calendar
        boolean isOwnCalendar = sessionId != null && sessionId.equals(calendar.sessionId);

        // Only allow viewing if it's public or belongs to the session
        if (!calendar.isPublic && !isOwnCalendar) {
            return Response.status(Response.Status.FORBIDDEN).entity(ErrorResponse.of("Calendar is not public"))
                    .build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("calendar", SessionCalendarDTO.from(calendar));
        response.put("isOwnCalendar", isOwnCalendar);

        return Response.ok(response).build();
    }

    /**
     * Auto-save calendar configuration. Uses pessimistic locking to handle concurrent updates gracefully - last write
     * wins.
     */
    @PUT
    @Path("/{id}/autosave")
    @Transactional
    public Response autosaveCalendar(@HeaderParam("X-Session-ID") String sessionId, @PathParam("id") UUID id,
            UpdateCalendarRequest request) {
        if (sessionId == null || sessionId.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ErrorResponse.of("No session found")).build();
        }

        // Use pessimistic lock to serialize concurrent autosaves for the same calendar
        UserCalendar calendar = UserCalendar.getEntityManager().find(UserCalendar.class, id,
                LockModeType.PESSIMISTIC_WRITE);
        if (calendar == null) {
            return Response.status(Response.Status.NOT_FOUND).entity(ErrorResponse.of("Calendar not found")).build();
        }

        // Verify calendar belongs to current session
        if (calendar.sessionId == null || !calendar.sessionId.equals(sessionId)) {
            return Response.status(Response.Status.FORBIDDEN).entity(ErrorResponse.of("Cannot edit this calendar"))
                    .build();
        }

        // Update configuration
        if (request.configuration != null) {
            calendar.configuration = request.configuration;

            // Extract year from configuration if present
            if (request.configuration.has("year")) {
                calendar.year = request.configuration.get("year").asInt();
            }
        }

        if (request.name != null) {
            calendar.name = request.name;
        }

        // Regenerate SVG from the updated configuration
        String svg = null;
        try {
            svg = calendarGenerationService.generateCalendarSVG(calendar);
            calendar.generatedSvg = svg;
        } catch (Exception e) {
            // Log but don't fail the save - SVG generation is secondary
            Log.error(ERROR_GENERATING_SVG, e);
        }

        calendar.persist();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("id", calendar.id);
        if (svg != null) {
            response.put("svg", svg);
        }
        return Response.ok(response).build();
    }

    /**
     * Create a new session calendar with default configuration. Looks for a template with slug='default', otherwise
     * uses hardcoded defaults. Returns the calendar ID, configuration, and pre-generated SVG.
     */
    @POST
    @Path("/new")
    @Transactional
    public Response createNewCalendar(@HeaderParam("X-Session-ID") String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ErrorResponse.of("No session found")).build();
        }

        // Check if this session already has a calendar - return existing one
        UserCalendar existingCalendar = UserCalendar.<UserCalendar>find("sessionId", sessionId).firstResult();

        if (existingCalendar != null) {
            Map<String, Object> response = new HashMap<>();
            response.put("id", existingCalendar.id);
            response.put(CONFIGURATION, existingCalendar.configuration);
            response.put("svg", existingCalendar.generatedSvg);
            response.put("existing", true);
            return Response.ok(response).build();
        }

        // Look for default template
        CalendarTemplate defaultTemplate = CalendarTemplate.<CalendarTemplate>find("slug", "default").firstResult();

        CalendarRenderingService.CalendarConfig config;
        String calendarName;

        if (defaultTemplate != null && defaultTemplate.isActive) {
            // Use template configuration
            try {
                config = objectMapper.treeToValue(defaultTemplate.configuration,
                        CalendarRenderingService.CalendarConfig.class);
                calendarName = defaultTemplate.name;
            } catch (Exception e) {
                config = getDefaultCalendarConfig();
                calendarName = "Default Calendar";
            }
        } else {
            // Use hardcoded defaults
            config = getDefaultCalendarConfig();
            calendarName = "Default Calendar";
        }

        // Create new calendar
        UserCalendar calendar = new UserCalendar();
        calendar.sessionId = sessionId;
        calendar.name = calendarName;
        calendar.year = config.year;
        calendar.isPublic = true;

        // Convert config to JsonNode for storage
        try {
            calendar.configuration = objectMapper.valueToTree(config);
        } catch (Exception e) {
            calendar.configuration = objectMapper.createObjectNode();
        }

        // Generate SVG
        try {
            calendar.generatedSvg = calendarRenderingService.generateCalendarSVG(config);
        } catch (Exception e) {
            Log.error(ERROR_GENERATING_SVG, e);
        }

        calendar.persist();

        Map<String, Object> response = new HashMap<>();
        response.put("id", calendar.id);
        response.put(CONFIGURATION, calendar.configuration);
        response.put("svg", calendar.generatedSvg);
        response.put("existing", false);

        return Response.ok(response).build();
    }

    /**
     * Get default calendar configuration (hardcoded fallback). Used when no template with slug='default' exists.
     */
    private CalendarRenderingService.CalendarConfig getDefaultCalendarConfig() {
        CalendarRenderingService.CalendarConfig config = new CalendarRenderingService.CalendarConfig();

        // Calculate default year: current year if Jan-Mar, next year if Apr-Dec
        LocalDate now = LocalDate.now();
        int defaultYear = now.getMonthValue() <= 3 ? now.getYear() : now.getYear() + 1;

        config.year = defaultYear;
        config.theme = "default";
        config.layoutStyle = "grid";
        config.moonDisplayMode = "none";
        config.showWeekNumbers = false;
        config.compactMode = false;
        config.showDayNames = true;
        config.showDayNumbers = true;
        config.showGrid = true;
        config.highlightWeekends = true;
        config.rotateMonthNames = false;
        config.firstDayOfWeek = java.time.DayOfWeek.SUNDAY;
        config.latitude = 0;
        config.longitude = 0;
        config.moonSize = 20;
        config.moonOffsetX = 25;
        config.moonOffsetY = 45;
        config.moonBorderColor = COLOR_GRAY;
        config.moonBorderWidth = 0.5;
        config.yearColor = COLOR_BLACK;
        config.monthColor = COLOR_BLACK;
        config.dayTextColor = COLOR_BLACK;
        config.dayNameColor = "#666666";
        config.gridLineColor = COLOR_GRAY;
        config.weekendBgColor = "";
        config.holidayColor = "#ff5252";
        config.customDateColor = "#4caf50";
        config.moonDarkColor = COLOR_GRAY;
        config.moonLightColor = "#FFFFFF";
        config.emojiPosition = "bottom-left";
        config.eventDisplayMode = "large";
        config.emojiFont = "noto-color";
        config.locale = "en-US";

        return config;
    }
}
