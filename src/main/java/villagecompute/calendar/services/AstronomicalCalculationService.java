package villagecompute.calendar.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.shredzone.commons.suncalc.SunTimes;
import villagecompute.calendar.util.astronomical.HebrewCalendarConverter;
import villagecompute.calendar.util.astronomical.MoonPhaseCalculator;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for astronomical calculations including moon phases, illumination, Hebrew calendar dates, and seasonal events.
 * <p>
 * This service provides accurate astronomical calculations using the SunCalc library,
 * delegating to utility classes for specific calculation types.
 */
@ApplicationScoped
public class AstronomicalCalculationService {

    @Inject
    HebrewCalendarService hebrewCalendarService;

    /**
     * Calculate moon phases for a given year using SunCalc library.
     * Returns a list of dates with their corresponding moon phase.
     * This is optimized to calculate major phase transitions directly rather than iterating all days.
     *
     * @param year The year to calculate moon phases for
     * @return List of MoonPhaseData for each major phase transition day
     */
    public List<MoonPhaseData> getMoonPhases(int year) {
        List<MoonPhaseCalculator.MoonPhaseData> utilPhases = MoonPhaseCalculator.calculateMoonPhasesForYear(year);

        // Convert utility class data to service data class
        List<MoonPhaseData> phases = new ArrayList<>();
        for (MoonPhaseCalculator.MoonPhaseData utilPhase : utilPhases) {
            MoonPhase phase = convertPhaseType(utilPhase.phase);
            phases.add(new MoonPhaseData(utilPhase.date, phase, utilPhase.phaseValue));
        }

        return phases;
    }

    /**
     * Convert utility class MoonPhaseType to service MoonPhase enum.
     */
    private MoonPhase convertPhaseType(MoonPhaseCalculator.MoonPhaseType type) {
        return switch (type) {
            case NEW_MOON -> MoonPhase.NEW_MOON;
            case WAXING_CRESCENT -> MoonPhase.WAXING_CRESCENT;
            case FIRST_QUARTER -> MoonPhase.FIRST_QUARTER;
            case WAXING_GIBBOUS -> MoonPhase.WAXING_GIBBOUS;
            case FULL_MOON -> MoonPhase.FULL_MOON;
            case WANING_GIBBOUS -> MoonPhase.WANING_GIBBOUS;
            case LAST_QUARTER -> MoonPhase.LAST_QUARTER;
            case WANING_CRESCENT -> MoonPhase.WANING_CRESCENT;
        };
    }

    /**
     * Calculate the moon phase for a specific date using SunCalc.
     *
     * @param date The date to calculate the moon phase for
     * @return The moon phase enum value
     */
    public MoonPhase calculateMoonPhase(LocalDate date) {
        MoonPhaseCalculator.MoonPhaseType type = MoonPhaseCalculator.calculateMoonPhase(date);
        return convertPhaseType(type);
    }

    /**
     * Calculate moon phase as a fractional value from 0 to 1.
     * 0 = new moon, 0.5 = full moon
     *
     * @param date The date to calculate
     * @return Moon phase value (0.0 to 1.0)
     */
    public double calculateMoonPhaseValue(LocalDate date) {
        return MoonPhaseCalculator.calculateMoonPhaseValue(date);
    }

    /**
     * Calculate moon illumination for a given date using SunCalc.
     *
     * @param date The date to calculate illumination for
     * @return MoonIllumination data with fraction, phase, and angle
     */
    public MoonIllumination calculateMoonIllumination(LocalDate date) {
        MoonPhaseCalculator.MoonIlluminationData data = MoonPhaseCalculator.calculateIlluminationData(date);
        return new MoonIllumination(data.fraction, data.phase, data.angle);
    }

    /**
     * Calculate moon position for an observer's location using SunCalc.
     * This affects how the moon appears (rotation) based on hemisphere.
     *
     * @param date      The date to calculate
     * @param latitude  Observer's latitude
     * @param longitude Observer's longitude
     * @return MoonPosition with azimuth, altitude, and parallactic angle
     */
    public MoonPosition calculateMoonPosition(LocalDate date, double latitude, double longitude) {
        MoonPhaseCalculator.MoonPositionData data = MoonPhaseCalculator.calculatePosition(date, latitude, longitude);
        return new MoonPosition(data.azimuth, data.altitude, data.parallacticAngle);
    }

    /**
     * Check if a date is a moon phase transition day (new/full/quarter) using SunCalc.
     *
     * @param date The date to check
     * @return true if the date is closest to a major moon phase
     */
    public boolean isMoonPhaseDay(LocalDate date) {
        return MoonPhaseCalculator.isMoonPhaseDay(date);
    }

    /**
     * Get Hebrew dates for a Gregorian year.
     * Converts all days in the year to their Hebrew calendar equivalents.
     *
     * @param year The Gregorian year
     * @return List of Hebrew date mappings
     */
    public List<HebrewDateMapping> getHebrewDates(int year) {
        List<HebrewDateMapping> dates = new ArrayList<>();

        // Get Hebrew holidays for the relevant Hebrew years
        // A Gregorian year spans approximately two Hebrew years
        int hebrewYearStart = year + 3760;
        int hebrewYearEnd = year + 3761;

        // Get holidays from HebrewCalendarService
        Map<String, String> holidays1 = hebrewCalendarService.getHebrewHolidays(hebrewYearStart, "HEBREW_ALL");
        Map<String, String> holidays2 = hebrewCalendarService.getHebrewHolidays(hebrewYearEnd, "HEBREW_ALL");

        // Merge holiday maps
        Map<String, String> allHolidays = new java.util.HashMap<>(holidays1);
        allHolidays.putAll(holidays2);

        // Generate mappings using HebrewCalendarConverter
        List<HebrewCalendarConverter.HebrewDateMapping> converterMappings =
                HebrewCalendarConverter.generateYearMappings(year, allHolidays);

        // Convert to service data class
        for (HebrewCalendarConverter.HebrewDateMapping mapping : converterMappings) {
            dates.add(new HebrewDateMapping(
                    mapping.gregorianDate,
                    mapping.hebrewDate,
                    mapping.holidayName
            ));
        }

        return dates;
    }

    /**
     * Calculate sunrise and sunset times for a location and date using SunCalc.
     *
     * @param date      The date
     * @param latitude  Latitude in degrees
     * @param longitude Longitude in degrees
     * @return SunriseSunset data with formatted times
     */
    public SunriseSunset getSunriseSunset(LocalDate date, double latitude, double longitude) {
        ZonedDateTime zdt = date.atStartOfDay(ZoneId.of("UTC"));

        SunTimes times = SunTimes.compute()
                .on(zdt)
                .at(latitude, longitude)
                .execute();

        // Format times as HH:mm
        String sunrise = times.getRise() != null ?
                String.format("%02d:%02d", times.getRise().getHour(), times.getRise().getMinute()) : null;
        String sunset = times.getSet() != null ?
                String.format("%02d:%02d", times.getSet().getHour(), times.getSet().getMinute()) : null;

        return new SunriseSunset(date, sunrise, sunset);
    }

    /**
     * Calculate seasonal events (equinoxes and solstices) for a given year using SunCalc.
     * Returns dates for spring equinox, summer solstice, autumn equinox, and winter solstice.
     *
     * @param year The year to calculate seasonal events for
     * @return List of SeasonalEvent objects
     */
    public List<SeasonalEvent> calculateSeasonalEvents(int year) {
        List<SeasonalEvent> events = new ArrayList<>();

        // For latitude/longitude, use a reference point (e.g., GMT 0,0)
        // Seasonal events are astronomical and occur at specific moments globally
        double refLatitude = 51.5; // London
        double refLongitude = 0.0;

        // Calculate Spring Equinox (around March 20)
        LocalDate springEquinoxDate = findEquinoxOrSolstice(year, 3, 19, 22, refLatitude, refLongitude, true);
        events.add(new SeasonalEvent(springEquinoxDate, "Spring Equinox", SeasonalEventType.SPRING_EQUINOX));

        // Calculate Summer Solstice (around June 21)
        LocalDate summerSolsticeDate = findSolstice(year, 6, 20, 22, refLatitude, refLongitude, true);
        events.add(new SeasonalEvent(summerSolsticeDate, "Summer Solstice", SeasonalEventType.SUMMER_SOLSTICE));

        // Calculate Autumn Equinox (around September 22)
        LocalDate autumnEquinoxDate = findEquinoxOrSolstice(year, 9, 21, 24, refLatitude, refLongitude, false);
        events.add(new SeasonalEvent(autumnEquinoxDate, "Autumn Equinox", SeasonalEventType.AUTUMN_EQUINOX));

        // Calculate Winter Solstice (around December 21)
        LocalDate winterSolsticeDate = findSolstice(year, 12, 20, 23, refLatitude, refLongitude, false);
        events.add(new SeasonalEvent(winterSolsticeDate, "Winter Solstice", SeasonalEventType.WINTER_SOLSTICE));

        return events;
    }

    /**
     * Find the exact date of an equinox by searching for equal day/night.
     */
    private LocalDate findEquinoxOrSolstice(int year, int month, int startDay, int endDay,
                                             double latitude, double longitude, boolean isSpring) {
        // Simple search: find the day with closest to 12 hours of daylight
        LocalDate closestDate = LocalDate.of(year, month, startDay);
        long closestDiff = Long.MAX_VALUE;

        for (int day = startDay; day <= endDay; day++) {
            LocalDate date = LocalDate.of(year, month, day);
            ZonedDateTime zdt = date.atStartOfDay(ZoneId.of("UTC"));

            SunTimes times = SunTimes.compute()
                    .on(zdt)
                    .at(latitude, longitude)
                    .execute();

            if (times.getRise() != null && times.getSet() != null) {
                long daylightMinutes = java.time.Duration.between(times.getRise(), times.getSet()).toMinutes();
                long diff = Math.abs(daylightMinutes - 720); // 720 minutes = 12 hours

                if (diff < closestDiff) {
                    closestDiff = diff;
                    closestDate = date;
                }
            }
        }

        return closestDate;
    }

    /**
     * Find the exact date of a solstice by searching for maximum/minimum daylight.
     */
    private LocalDate findSolstice(int year, int month, int startDay, int endDay,
                                    double latitude, double longitude, boolean isSummer) {
        LocalDate extremeDate = LocalDate.of(year, month, startDay);
        long extremeDaylight = isSummer ? Long.MIN_VALUE : Long.MAX_VALUE;

        for (int day = startDay; day <= endDay; day++) {
            LocalDate date = LocalDate.of(year, month, day);
            ZonedDateTime zdt = date.atStartOfDay(ZoneId.of("UTC"));

            SunTimes times = SunTimes.compute()
                    .on(zdt)
                    .at(latitude, longitude)
                    .execute();

            if (times.getRise() != null && times.getSet() != null) {
                long daylightMinutes = java.time.Duration.between(times.getRise(), times.getSet()).toMinutes();

                if ((isSummer && daylightMinutes > extremeDaylight) ||
                        (!isSummer && daylightMinutes < extremeDaylight)) {
                    extremeDaylight = daylightMinutes;
                    extremeDate = date;
                }
            }
        }

        return extremeDate;
    }

    // Data classes

    public static class MoonPhaseData {
        public LocalDate date;
        public MoonPhase phase;
        public double phaseValue;

        public MoonPhaseData(LocalDate date, MoonPhase phase, double phaseValue) {
            this.date = date;
            this.phase = phase;
            this.phaseValue = phaseValue;
        }
    }

    public enum MoonPhase {
        NEW_MOON, WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS,
        FULL_MOON, WANING_GIBBOUS, LAST_QUARTER, WANING_CRESCENT
    }

    public static class MoonIllumination {
        public double fraction; // 0 to 1, illuminated fraction
        public double phase;    // 0 to 1, phase in the lunar cycle
        public double angle;    // angle in radians

        public MoonIllumination(double fraction, double phase, double angle) {
            this.fraction = fraction;
            this.phase = phase;
            this.angle = angle;
        }
    }

    public static class MoonPosition {
        public double azimuth;         // azimuth in radians
        public double altitude;        // altitude in radians
        public double parallacticAngle; // parallactic angle in radians

        public MoonPosition(double azimuth, double altitude, double parallacticAngle) {
            this.azimuth = azimuth;
            this.altitude = altitude;
            this.parallacticAngle = parallacticAngle;
        }
    }

    public static class HebrewDateMapping {
        public LocalDate gregorianDate;
        public String hebrewDate;
        public String holidayName; // null if not a holiday

        public HebrewDateMapping(LocalDate gregorianDate, String hebrewDate, String holidayName) {
            this.gregorianDate = gregorianDate;
            this.hebrewDate = hebrewDate;
            this.holidayName = holidayName;
        }
    }

    public static class SunriseSunset {
        public LocalDate date;
        public String sunrise; // HH:mm format
        public String sunset;  // HH:mm format

        public SunriseSunset(LocalDate date, String sunrise, String sunset) {
            this.date = date;
            this.sunrise = sunrise;
            this.sunset = sunset;
        }
    }

    public static class SeasonalEvent {
        public LocalDate date;
        public String name;
        public SeasonalEventType type;

        public SeasonalEvent(LocalDate date, String name, SeasonalEventType type) {
            this.date = date;
            this.name = name;
            this.type = type;
        }
    }

    public enum SeasonalEventType {
        SPRING_EQUINOX, SUMMER_SOLSTICE, AUTUMN_EQUINOX, WINTER_SOLSTICE
    }
}
