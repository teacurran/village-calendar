package villagecompute.calendar.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import villagecompute.calendar.services.CalendarRenderingService;
import villagecompute.calendar.services.HebrewCalendarService;

import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Path("/calendar")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CalendarResource {

  @Inject
  CalendarRenderingService calendarRenderingService;

  @Inject
  HebrewCalendarService hebrewCalendarService;

  @Inject
  villagecompute.calendar.services.HolidayService holidayService;

  // Request/Response types
  public static class CalendarRequest {
    public String calendarType; // "gregorian" or "hebrew"
    public Integer year;
    public String theme;
    public Boolean showMoonPhases;
    public Boolean showMoonIllumination;
    public Boolean showFullMoonOnly;
    public Boolean showWeekNumbers;
    public Boolean compactMode;
    public Boolean showDayNames;
    public Boolean showDayNumbers;
    public Boolean showGrid;
    public Boolean highlightWeekends;
    public Boolean rotateMonthNames;
    public String layoutStyle; // "grid" or "traditional"
    public Double latitude;
    public Double longitude;
    public Integer moonSize;
    public Integer moonOffsetX;
    public Integer moonOffsetY;
    public String moonBorderColor;
    public Double moonBorderWidth;
    // Color customization
    public String yearColor;
    public String monthColor;
    public String dayTextColor;
    public String dayNameColor;
    public String gridLineColor;
    public String weekendBgColor;
    public String holidayColor;
    public String customDateColor;
    public String moonDarkColor;
    public String moonLightColor;
    public String emojiPosition; // Position of emojis in calendar cells
    public Map<String, Object> customDates;
    public Map<String, String> eventTitles; // date -> title mapping
    public Set<String> holidays;
    public String holidaySet; // Holiday set to use (e.g., "US", "JEWISH", "HEBREW_RELIGIOUS")
    public List<String> holidaySets; // List of holiday set IDs to include
    public String eventDisplayMode; // "small" or "large" for event/holiday display
    public String locale;
    public String firstDayOfWeek;
    public String observationTime; // Time for moon calculations (e.g., "20:00")
    public String timeZone; // Time zone for calculations
  }

  public static class CalendarResponse {
    public String svg;
    public String format;
    public Integer year;
    public String theme;
  }

  public static class HolidayResponse {
    public Set<String> holidays;
    public Map<String, String> holidayNames;
  }

  @POST
  @Path("/generate")
  @Produces(MediaType.APPLICATION_XML)
  public Response generateCalendar(CalendarRequest request) {
    // Build configuration from request
    CalendarRenderingService.CalendarConfig config = buildConfig(request);

    // Generate SVG
    String svg = calendarRenderingService.generateCalendarSVG(config);

    return Response.ok(svg)
      .header("Content-Type", "image/svg+xml")
      .build();
  }

  @POST
  @Path("/generate-json")
  @Produces(MediaType.APPLICATION_JSON)
  public CalendarResponse generateCalendarJson(CalendarRequest request) {
    // Build configuration from request
    CalendarRenderingService.CalendarConfig config = buildConfig(request);

    // Generate SVG based on calendar type
    String svg;
    if ("hebrew".equals(request.calendarType)) {
      // Generate Hebrew calendar
      HebrewCalendarService.HebrewCalendarConfig hebrewConfig = new HebrewCalendarService.HebrewCalendarConfig();
      hebrewConfig.hebrewYear = request.year != null ? request.year : 5784;
      hebrewConfig.theme = config.theme;
      hebrewConfig.showGrid = config.showGrid;
      hebrewConfig.highlightWeekends = config.highlightWeekends;
      hebrewConfig.showDayNumbers = config.showDayNumbers;
      hebrewConfig.compactMode = config.compactMode;
      hebrewConfig.rotateMonthNames = config.rotateMonthNames;
      // Copy all moon-related settings
      hebrewConfig.showMoonPhases = config.showMoonPhases;
      hebrewConfig.showMoonIllumination = config.showMoonIllumination;
      hebrewConfig.showFullMoonOnly = config.showFullMoonOnly;
      hebrewConfig.moonSize = config.moonSize;
      hebrewConfig.moonOffsetX = config.moonOffsetX;
      hebrewConfig.moonOffsetY = config.moonOffsetY;
      hebrewConfig.moonBorderColor = config.moonBorderColor;
      hebrewConfig.moonBorderWidth = config.moonBorderWidth;
      hebrewConfig.moonDarkColor = config.moonDarkColor;
      hebrewConfig.moonLightColor = config.moonLightColor;
      hebrewConfig.latitude = config.latitude;
      hebrewConfig.longitude = config.longitude;
      // Pass holiday set for Hebrew calendar (default to HEBREW_RELIGIOUS if not specified)
      String holidaySet = request.holidaySet != null ? request.holidaySet : "HEBREW_RELIGIOUS";
      svg = hebrewCalendarService.generateHebrewCalendarSVG(hebrewConfig, holidaySet);
    } else {
      svg = calendarRenderingService.generateCalendarSVG(config);
    }

    CalendarResponse response = new CalendarResponse();
    response.svg = svg;
    response.format = "svg";
    response.year = config.year;
    response.theme = config.theme;

    return response;
  }

  @GET
  @Path("/themes")
  public Response getAvailableThemes() {
    Map<String, String> themes = new HashMap<>();
    themes.put("default", "Default (Black & White)");
    themes.put("vermontWeekends", "Vermont Weekends (Green)");
    themes.put("rainbowWeekends", "Rainbow Weekends");

    return Response.ok(themes).build();
  }

  @POST
  @Path("/generate-pdf")
  @Produces("application/pdf")
  public Response generatePDF(CalendarRequest request) {
    // Build configuration
    CalendarRenderingService.CalendarConfig config = buildConfig(request);

    // Generate PDF
    byte[] pdf = calendarRenderingService.generateCalendarPDF(config);

    if (pdf.length == 0) {
      // PDF generation failed
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity("Failed to generate PDF")
        .build();
    }

    return Response.ok(pdf)
      .header("Content-Type", "application/pdf")
      .header("Content-Disposition", "attachment; filename=\"calendar-" + config.year + ".pdf\"")
      .build();
  }

  @GET
  @Path("/holidays")
  public Response getHolidays(
      @jakarta.ws.rs.QueryParam("year") Integer year,
      @jakarta.ws.rs.QueryParam("country") String country) {

    // Default to current year if not specified
    int holidayYear = year != null ? year : java.time.LocalDate.now().getYear();

    // Default to US if country not specified
    String holidayCountry = country != null ? country : "US";

    // Get holidays from service
    Map<String, String> holidayMap = holidayService.getHolidays(holidayYear, holidayCountry);

    HolidayResponse response = new HolidayResponse();
    response.holidays = holidayMap.keySet();
    response.holidayNames = holidayMap;

    return Response.ok(response).build();
  }

  private CalendarRenderingService.CalendarConfig buildConfig(CalendarRequest request) {
    CalendarRenderingService.CalendarConfig config = new CalendarRenderingService.CalendarConfig();

    if (request.year != null) {
      config.year = request.year;
    }
    if (request.theme != null) {
      config.theme = request.theme;
    }
    if (request.showMoonPhases != null) {
      config.showMoonPhases = request.showMoonPhases;
    }
    if (request.showMoonIllumination != null) {
      config.showMoonIllumination = request.showMoonIllumination;
    }
    if (request.showFullMoonOnly != null) {
      config.showFullMoonOnly = request.showFullMoonOnly;
    }
    if (request.latitude != null) {
      config.latitude = request.latitude;
    }
    if (request.longitude != null) {
      config.longitude = request.longitude;
    }
    if (request.showWeekNumbers != null) {
      config.showWeekNumbers = request.showWeekNumbers;
    }
    if (request.compactMode != null) {
      config.compactMode = request.compactMode;
    }
    if (request.showDayNames != null) {
      config.showDayNames = request.showDayNames;
    }
    if (request.showDayNumbers != null) {
      config.showDayNumbers = request.showDayNumbers;
    }
    if (request.showGrid != null) {
      config.showGrid = request.showGrid;
    }
    if (request.highlightWeekends != null) {
      config.highlightWeekends = request.highlightWeekends;
    }
    if (request.rotateMonthNames != null) {
      config.rotateMonthNames = request.rotateMonthNames;
    }
    if (request.layoutStyle != null) {
      config.layoutStyle = request.layoutStyle;
    }
    if (request.customDates != null) {
      config.customDates = request.customDates;
    }
    if (request.eventTitles != null) {
      config.eventTitles = request.eventTitles;
    }
    if (request.holidays != null) {
      config.holidays = request.holidays;
    }
    if (request.locale != null) {
      config.locale = request.locale;
    }
    if (request.firstDayOfWeek != null) {
      try {
        config.firstDayOfWeek = DayOfWeek.valueOf(request.firstDayOfWeek.toUpperCase());
      } catch (IllegalArgumentException e) {
        // Default to Sunday if invalid
        config.firstDayOfWeek = DayOfWeek.SUNDAY;
      }
    }
    if (request.moonSize != null) {
      config.moonSize = request.moonSize;
    }
    if (request.moonOffsetX != null) {
      config.moonOffsetX = request.moonOffsetX;
    }
    if (request.moonOffsetY != null) {
      config.moonOffsetY = request.moonOffsetY;
    }
    if (request.moonBorderColor != null) {
      config.moonBorderColor = request.moonBorderColor;
    }
    if (request.moonBorderWidth != null) {
      config.moonBorderWidth = request.moonBorderWidth;
    }
    if (request.yearColor != null) {
      config.yearColor = request.yearColor;
    }
    if (request.monthColor != null) {
      config.monthColor = request.monthColor;
    }
    if (request.dayTextColor != null) {
      config.dayTextColor = request.dayTextColor;
    }
    if (request.dayNameColor != null) {
      config.dayNameColor = request.dayNameColor;
    }
    if (request.gridLineColor != null) {
      config.gridLineColor = request.gridLineColor;
    }
    if (request.weekendBgColor != null) {
      config.weekendBgColor = request.weekendBgColor;
    }
    if (request.holidayColor != null) {
      config.holidayColor = request.holidayColor;
    }
    if (request.customDateColor != null) {
      config.customDateColor = request.customDateColor;
    }
    if (request.moonDarkColor != null) {
      config.moonDarkColor = request.moonDarkColor;
    }
    if (request.moonLightColor != null) {
      config.moonLightColor = request.moonLightColor;
    }
    if (request.emojiPosition != null) {
      config.emojiPosition = request.emojiPosition;
    }
    if (request.holidaySets != null) {
      config.holidaySets = request.holidaySets;
    }
    if (request.eventDisplayMode != null) {
      config.eventDisplayMode = request.eventDisplayMode;
    }
    if (request.observationTime != null) {
      try {
        config.observationTime = java.time.LocalTime.parse(request.observationTime);
      } catch (Exception e) {
        // Keep default if parsing fails
      }
    }
    if (request.timeZone != null) {
      config.timeZone = request.timeZone;
    }

    return config;
  }
}
