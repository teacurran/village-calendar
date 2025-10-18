package villagecompute.calendar.api.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.services.CalendarRenderingService;
import villagecompute.calendar.services.SessionService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Path("/session-calendar")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SessionCalendarResource {

    @Inject
    SessionService sessionService;

    @Inject
    CalendarRenderingService calendarRenderingService;

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

    /**
     * Get the current session's active calendar
     */
    @GET
    @Path("/current")
    public Response getCurrentCalendar(@HeaderParam("X-Session-ID") String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "No session found"))
                .build();
        }

        UserCalendar calendar = UserCalendar.find("sessionId", sessionId)
            .firstResult();

        if (calendar == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "No calendar for this session"))
                .build();
        }

        return Response.ok(SessionCalendarDTO.from(calendar)).build();
    }

    /**
     * Create or update a calendar for the current session
     */
    @POST
    @Path("/save")
    @Transactional
    public Response saveSessionCalendar(
            @HeaderParam("X-Session-ID") String sessionId,
            UpdateCalendarRequest request) {
        if (sessionId == null || sessionId.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "No session found"))
                .build();
        }

        // Check if this session already has a working calendar
        UserCalendar calendar = UserCalendar.<UserCalendar>find("sessionId", sessionId)
            .firstResult();

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
                CalendarRenderingService.CalendarConfig config = objectMapper.treeToValue(
                    calendar.configuration,
                    CalendarRenderingService.CalendarConfig.class
                );
                calendar.generatedSvg = calendarRenderingService.generateCalendarSVG(config);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        calendar.persist();

        Map<String, Object> response = new HashMap<>();
        response.put("id", calendar.id);
        response.put("sessionId", sessionId);

        return Response.ok(response).build();
    }

    /**
     * Create calendar from template for session
     */
    @POST
    @Path("/from-template/{templateId}")
    @Transactional
    public Response createFromTemplate(
            @HeaderParam("X-Session-ID") String sessionId,
            @PathParam("templateId") UUID templateId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "No session found"))
                .build();
        }

        CalendarTemplate template = CalendarTemplate.findById(templateId);
        if (template == null || !template.isActive) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Template not found"))
                .build();
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
            CalendarRenderingService.CalendarConfig config = objectMapper.treeToValue(
                template.configuration,
                CalendarRenderingService.CalendarConfig.class
            );
            calendar.generatedSvg = calendarRenderingService.generateCalendarSVG(config);
        } catch (Exception e) {
            e.printStackTrace();
        }

        calendar.persist();

        Map<String, Object> response = new HashMap<>();
        response.put("id", calendar.id);
        response.put("configuration", calendar.configuration);

        return Response.ok(response).build();
    }

    /**
     * Auto-save calendar configuration
     */
    @PUT
    @Path("/{id}/autosave")
    @Transactional
    public Response autosaveCalendar(
            @HeaderParam("X-Session-ID") String sessionId,
            @PathParam("id") UUID id,
            UpdateCalendarRequest request) {
        if (sessionId == null || sessionId.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "No session found"))
                .build();
        }

        UserCalendar calendar = UserCalendar.findById(id);
        if (calendar == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Calendar not found"))
                .build();
        }

        // Verify calendar belongs to current session
        if (calendar.sessionId == null || !calendar.sessionId.equals(sessionId)) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(Map.of("error", "Cannot edit this calendar"))
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

        calendar.persist();

        return Response.ok(Map.of("success", true, "id", calendar.id)).build();
    }
}
