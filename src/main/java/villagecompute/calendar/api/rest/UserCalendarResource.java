package villagecompute.calendar.api.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.services.AuthenticationService;
import villagecompute.calendar.services.CalendarRenderingService;
import villagecompute.calendar.util.Roles;

import io.quarkus.logging.Log;

/**
 * REST Resource for user calendar operations Provides REST wrapper around GraphQL calendar
 * operations for backwards compatibility
 */
@Path("/calendar-templates/user")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserCalendarResource {

    @Inject JsonWebToken jwt;

    @Inject AuthenticationService authService;

    @Inject CalendarRenderingService calendarRenderingService;

    @Inject ObjectMapper objectMapper;

    // DTOs
    public static class SaveCalendarRequest {
        public UUID id;
        public String name;
        public JsonNode configuration;
        public String generatedSvg;
        public UUID templateId;
    }

    public static class CalendarResponse {
        public UUID id;
        public String name;
        public JsonNode configuration;
        public String generatedSvg;
        public UUID templateId;
        public boolean isPublic;

        public static CalendarResponse from(UserCalendar calendar) {
            CalendarResponse response = new CalendarResponse();
            response.id = calendar.id;
            response.name = calendar.name;
            response.configuration = calendar.configuration;
            response.generatedSvg = calendar.generatedSvg;
            response.templateId = calendar.template != null ? calendar.template.id : null;
            response.isPublic = calendar.isPublic;
            return response;
        }
    }

    /** Get all calendars for the authenticated user */
    @GET
    @Path("/calendars")
    @RolesAllowed(Roles.USER)
    public Response getCalendars() {
        CalendarUser user =
                authService
                        .getCurrentUser(jwt)
                        .orElseThrow(
                                () ->
                                        new WebApplicationException(
                                                "User not found", Response.Status.UNAUTHORIZED));

        List<UserCalendar> calendars =
                UserCalendar.find("user.id = ?1 and sessionId is null", user.id).list();

        List<CalendarResponse> response = calendars.stream().map(CalendarResponse::from).toList();

        return Response.ok(response).build();
    }

    /** Save or update a user calendar */
    @POST
    @Path("/save")
    @RolesAllowed(Roles.USER)
    @Transactional
    public Response saveCalendar(SaveCalendarRequest request) {
        CalendarUser user =
                authService
                        .getCurrentUser(jwt)
                        .orElseThrow(
                                () ->
                                        new WebApplicationException(
                                                "User not found", Response.Status.UNAUTHORIZED));

        UserCalendar calendar;

        if (request.id != null) {
            // Update existing calendar
            calendar = UserCalendar.findById(request.id);
            if (calendar == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Calendar not found"))
                        .build();
            }

            // Verify ownership
            if (!calendar.user.id.equals(user.id)) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(Map.of("error", "Not authorized to update this calendar"))
                        .build();
            }
        } else {
            // Create new calendar
            calendar = new UserCalendar();
            calendar.user = user;
            calendar.isPublic = false; // User calendars are private by default
            // Set default year
            calendar.year = java.time.LocalDate.now().getYear();
        }

        // Update fields
        if (request.name != null) {
            calendar.name = request.name;
        }
        if (request.configuration != null) {
            calendar.configuration = request.configuration;

            // Extract year from configuration if present
            if (request.configuration.has("year")) {
                calendar.year = request.configuration.get("year").asInt();
            }
        }
        if (request.generatedSvg != null) {
            calendar.generatedSvg = request.generatedSvg;
        }
        if (request.templateId != null) {
            CalendarTemplate template = CalendarTemplate.findById(request.templateId);
            if (template != null) {
                calendar.template = template;
            }
        }

        // Generate SVG if not provided and we have configuration
        if (calendar.generatedSvg == null && calendar.configuration != null) {
            try {
                CalendarRenderingService.CalendarConfig config =
                        objectMapper.treeToValue(
                                calendar.configuration,
                                CalendarRenderingService.CalendarConfig.class);
                calendar.generatedSvg = calendarRenderingService.generateCalendarSVG(config);
            } catch (Exception e) {
                Log.error("Error generating calendar SVG", e);
            }
        }

        calendar.persist();

        Map<String, Object> response = new HashMap<>();
        response.put("id", calendar.id);
        response.put("name", calendar.name);
        response.put("success", true);

        return Response.ok(response).build();
    }

    /** Get preview SVG for a calendar */
    @GET
    @Path("/calendars/{id}/preview")
    @RolesAllowed(Roles.USER)
    public Response getCalendarPreview(@PathParam("id") UUID id) {
        CalendarUser user =
                authService
                        .getCurrentUser(jwt)
                        .orElseThrow(
                                () ->
                                        new WebApplicationException(
                                                "User not found", Response.Status.UNAUTHORIZED));

        UserCalendar calendar = UserCalendar.findById(id);
        if (calendar == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Calendar not found"))
                    .build();
        }

        // Verify ownership
        if (!calendar.user.id.equals(user.id)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "Not authorized to view this calendar"))
                    .build();
        }

        if (calendar.generatedSvg == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "No preview available"))
                    .build();
        }

        return Response.ok(calendar.generatedSvg).type("image/svg+xml").build();
    }

    /** Delete a user calendar */
    @DELETE
    @Path("/calendars/{id}")
    @RolesAllowed(Roles.USER)
    @Transactional
    public Response deleteCalendar(@PathParam("id") UUID id) {
        CalendarUser user =
                authService
                        .getCurrentUser(jwt)
                        .orElseThrow(
                                () ->
                                        new WebApplicationException(
                                                "User not found", Response.Status.UNAUTHORIZED));

        UserCalendar calendar = UserCalendar.findById(id);
        if (calendar == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Calendar not found"))
                    .build();
        }

        // Verify ownership
        if (!calendar.user.id.equals(user.id)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "Not authorized to delete this calendar"))
                    .build();
        }

        calendar.delete();

        return Response.ok(Map.of("success", true)).build();
    }
}
