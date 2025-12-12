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
import villagecompute.calendar.services.EmojiSvgService;
import villagecompute.calendar.services.HebrewCalendarService;
import villagecompute.calendar.services.PDFRenderingService;
import jakarta.ws.rs.QueryParam;

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

  @Inject
  PDFRenderingService pdfRenderingService;

  @Inject
  EmojiSvgService emojiSvgService;

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
    public String emojiFont; // "noto-color" (default) or "noto-mono" for monochrome
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

  /**
   * Request for converting SVG to PDF directly
   */
  public static class SvgToPdfRequest {
    public String svgContent;
    public Integer year;
    // Optional: If provided, will regenerate SVG with these settings instead of using svgContent
    public CalendarRequest regenerateConfig;
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

  /**
   * Get an emoji SVG for preview purposes.
   * @param emoji The emoji character (URL encoded)
   * @param style The style: "noto-color", "noto-mono", or "mono-{color}"
   * @return SVG content
   */
  @GET
  @Path("/emoji-preview")
  @Produces("image/svg+xml")
  public Response getEmojiPreview(
      @QueryParam("emoji") String emoji,
      @QueryParam("style") String style) {

    if (emoji == null || emoji.isEmpty()) {
      emoji = "🎄"; // Default to Christmas tree
    }

    if (style == null || style.isEmpty()) {
      style = "noto-color";
    }

    // Determine if monochrome and what color
    boolean monochrome = !style.equals("noto-color");
    String colorHex = null;

    if (style.startsWith("mono-")) {
      // Extract color from style like "mono-red"
      colorHex = getColorForStyle(style);
    }

    String svg = emojiSvgService.getStandaloneSvg(emoji, monochrome, colorHex);

    if (svg == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity("Emoji SVG not found")
          .build();
    }

    return Response.ok(svg)
        .header("Cache-Control", "public, max-age=86400") // Cache for 24 hours
        .build();
  }

  private String getColorForStyle(String style) {
    return switch (style) {
      case "mono-red" -> "#DC2626";
      case "mono-blue" -> "#2563EB";
      case "mono-green" -> "#16A34A";
      case "mono-orange" -> "#EA580C";
      case "mono-purple" -> "#9333EA";
      case "mono-pink" -> "#EC4899";
      case "mono-teal" -> "#0D9488";
      case "mono-brown" -> "#92400E";
      case "mono-navy" -> "#1E3A5F";
      case "mono-maroon" -> "#7F1D1D";
      case "mono-olive" -> "#4D7C0F";
      case "mono-coral" -> "#F97316";
      default -> null; // noto-mono (black)
    };
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

  /**
   * Convert SVG content to PDF directly.
   * Used for downloading PDFs from stored SVG content (e.g., order confirmations).
   */
  @POST
  @Path("/svg-to-pdf")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces("application/pdf")
  public Response svgToPdf(SvgToPdfRequest request) {
    if (request == null) {
      return Response.status(Response.Status.BAD_REQUEST)
        .type(MediaType.TEXT_PLAIN)
        .entity("Request is required")
        .build();
    }

    int year = request.year != null ? request.year : java.time.LocalDate.now().getYear();
    String svgContent;

    try {
      // Check if we should regenerate SVG with new configuration (e.g., different emoji font)
      if (request.regenerateConfig != null) {
        // Regenerate SVG with updated configuration
        CalendarRenderingService.CalendarConfig config = buildConfig(request.regenerateConfig);
        
        // Generate fresh SVG based on calendar type
        if ("hebrew".equals(request.regenerateConfig.calendarType)) {
          // Generate Hebrew calendar
          HebrewCalendarService.HebrewCalendarConfig hebrewConfig = new HebrewCalendarService.HebrewCalendarConfig();
          hebrewConfig.hebrewYear = config.year;
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
          String holidaySet = request.regenerateConfig.holidaySet != null ? request.regenerateConfig.holidaySet : "HEBREW_RELIGIOUS";
          svgContent = hebrewCalendarService.generateHebrewCalendarSVG(hebrewConfig, holidaySet);
        } else {
          svgContent = calendarRenderingService.generateCalendarSVG(config);
        }
      } else if (request.svgContent != null && !request.svgContent.isEmpty()) {
        // Use provided SVG content
        svgContent = request.svgContent;
      } else {
        return Response.status(Response.Status.BAD_REQUEST)
          .type(MediaType.TEXT_PLAIN)
          .entity("Either svgContent or regenerateConfig is required")
          .build();
      }

      // Convert SVG to PDF using PDFRenderingService
      byte[] pdf = pdfRenderingService.renderSVGToPDF(svgContent, year);

      if (pdf == null || pdf.length == 0) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .type(MediaType.TEXT_PLAIN)
          .entity("Failed to generate PDF - empty result")
          .build();
      }

      return Response.ok(pdf)
        .type("application/pdf")
        .header("Content-Disposition", "attachment; filename=\"calendar-" + year + ".pdf\"")
        .build();

    } catch (Exception e) {
      e.printStackTrace();
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .type(MediaType.TEXT_PLAIN)
        .entity("PDF generation failed: " + e.getMessage())
        .build();
    }
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
    if (request.emojiFont != null) {
      config.emojiFont = request.emojiFont;
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
