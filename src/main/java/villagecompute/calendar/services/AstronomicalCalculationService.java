package villagecompute.calendar.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for astronomical calculations including moon phases, illumination, and Hebrew calendar dates.
 * <p>
 * This service extracts astronomical calculation logic from the main CalendarService
 * to provide reusable moon phase and position calculations.
 */
@ApplicationScoped
public class AstronomicalCalculationService {

    @Inject
    HebrewCalendarService hebrewCalendarService;

    /**
     * Calculate moon phases for a given year.
     * Returns a list of dates with their corresponding moon phase.
     *
     * @param year The year to calculate moon phases for
     * @return List of MoonPhaseData for each major phase transition day
     */
    public List<MoonPhaseData> getMoonPhases(int year) {
        List<MoonPhaseData> phases = new ArrayList<>();

        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (isMoonPhaseDay(date)) {
                MoonPhase phase = calculateMoonPhase(date);
                double phaseValue = calculateMoonPhaseValue(date);
                phases.add(new MoonPhaseData(date, phase, phaseValue));
            }
        }

        return phases;
    }

    /**
     * Calculate the moon phase for a specific date.
     *
     * @param date The date to calculate the moon phase for
     * @return The moon phase enum value
     */
    public MoonPhase calculateMoonPhase(LocalDate date) {
        // Known new moon date (January 6, 2000 at 18:14 UTC)
        LocalDate knownNewMoon = LocalDate.of(2000, 1, 6);

        // Calculate days since known new moon
        long daysSince = java.time.temporal.ChronoUnit.DAYS.between(knownNewMoon, date);

        // Synodic month is approximately 29.53059 days
        double synodicMonth = 29.53059;

        // Calculate phase as a fraction (0 = new moon, 0.5 = full moon)
        double phase = (daysSince % synodicMonth) / synodicMonth;

        // Determine moon phase based on the fraction
        if (phase < 0.0625 || phase >= 0.9375) {
            return MoonPhase.NEW_MOON;
        } else if (phase < 0.1875) {
            return MoonPhase.WAXING_CRESCENT;
        } else if (phase < 0.3125) {
            return MoonPhase.FIRST_QUARTER;
        } else if (phase < 0.4375) {
            return MoonPhase.WAXING_GIBBOUS;
        } else if (phase < 0.5625) {
            return MoonPhase.FULL_MOON;
        } else if (phase < 0.6875) {
            return MoonPhase.WANING_GIBBOUS;
        } else if (phase < 0.8125) {
            return MoonPhase.LAST_QUARTER;
        } else {
            return MoonPhase.WANING_CRESCENT;
        }
    }

    /**
     * Calculate moon phase as a fractional value from 0 to 1.
     * 0 = new moon, 0.5 = full moon
     *
     * @param date The date to calculate
     * @return Moon phase value (0.0 to 1.0)
     */
    public double calculateMoonPhaseValue(LocalDate date) {
        // Known new moon date (January 6, 2000 at 18:14 UTC)
        LocalDate knownNewMoon = LocalDate.of(2000, 1, 6);

        // Calculate days since known new moon
        long daysSince = java.time.temporal.ChronoUnit.DAYS.between(knownNewMoon, date);

        // Synodic month is approximately 29.53059 days
        double synodicMonth = 29.53059;

        // Calculate phase as a fraction (0 = new moon, 0.5 = full moon)
        double phase = (daysSince % synodicMonth) / synodicMonth;
        if (phase < 0) phase += 1.0;

        return phase;
    }

    /**
     * Calculate moon illumination for a given date.
     *
     * @param date The date to calculate illumination for
     * @return MoonIllumination data with fraction, phase, and angle
     */
    public MoonIllumination calculateMoonIllumination(LocalDate date) {
        // Use the same phase calculation for consistency
        double phase = calculateMoonPhaseValue(date);

        // Calculate illumination fraction using the phase
        // This is a simplified calculation
        double illumination = (1 - Math.cos(phase * 2 * Math.PI)) / 2;

        // Angle (simplified - actual calculation would need sun position)
        double angle = phase * 2 * Math.PI;

        return new MoonIllumination(illumination, phase, angle);
    }

    /**
     * Calculate moon position for an observer's location.
     * This affects how the moon appears (rotation) based on hemisphere.
     *
     * @param date      The date to calculate
     * @param latitude  Observer's latitude
     * @param longitude Observer's longitude
     * @return MoonPosition with azimuth, altitude, and parallactic angle
     */
    public MoonPosition calculateMoonPosition(LocalDate date, double latitude, double longitude) {
        // Improved moon position calculation with better hemisphere awareness
        // The moon appears differently based on observer's location

        // Convert to radians
        double lat = Math.toRadians(latitude);
        double lng = Math.toRadians(longitude);

        // Calculate moon's approximate position in its orbit
        // Using synodic month for phase calculation
        LocalDate j2000 = LocalDate.of(2000, 1, 1);
        long daysSinceJ2000 = java.time.temporal.ChronoUnit.DAYS.between(j2000, date);
        double synodicMonth = 29.53058867;
        double moonAge = (daysSinceJ2000 - 5.5) % synodicMonth;
        if (moonAge < 0) moonAge += synodicMonth;

        // Calculate approximate moon declination
        // Moon's orbit is inclined about 5.14° to ecliptic, ecliptic is inclined 23.44° to equator
        // This gives moon declination range of approximately ±28.6°
        double moonOrbitPhase = (moonAge / synodicMonth) * 2 * Math.PI;
        double moonDeclination = Math.toRadians(28.6 * Math.sin(moonOrbitPhase + date.getDayOfYear() * Math.PI / 182.625));

        // Calculate hour angle based on time and longitude
        // This is simplified - actual calculation would need precise time
        double hourAngle = (date.getDayOfMonth() / 30.0) * 2 * Math.PI + lng;

        // Calculate altitude
        double altitude = Math.asin(
                Math.sin(lat) * Math.sin(moonDeclination) +
                        Math.cos(lat) * Math.cos(moonDeclination) * Math.cos(hourAngle)
        );

        // Calculate azimuth
        double azimuth = Math.atan2(
                -Math.sin(hourAngle),
                Math.tan(moonDeclination) * Math.cos(lat) - Math.sin(lat) * Math.cos(hourAngle)
        );

        // Calculate parallactic angle - the rotation of the moon from observer's viewpoint
        // This is the key to showing different moon orientations by hemisphere
        double parallacticAngle;

        if (Math.abs(latitude) < 1.0) {
            // Near equator: moon appears to rotate throughout the night
            // East-West orientation changes
            parallacticAngle = hourAngle;
        } else {
            // Calculate standard parallactic angle
            double sinPA = Math.sin(hourAngle) * Math.cos(moonDeclination) / Math.cos(altitude);
            double cosPA = (Math.sin(moonDeclination) * Math.cos(lat) -
                    Math.cos(moonDeclination) * Math.sin(lat) * Math.cos(hourAngle)) / Math.cos(altitude);
            parallacticAngle = Math.atan2(sinPA, cosPA);

            // Hemisphere adjustments
            if (latitude < 0) {
                // Southern hemisphere: moon appears "upside down" relative to northern view
                parallacticAngle += Math.PI;
            }

            // Additional adjustment based on latitude magnitude
            // This creates a gradual transition from equator to poles
            double latitudeFactor = Math.abs(latitude) / 90.0;
            parallacticAngle += (1 - latitudeFactor) * hourAngle * 0.3;
        }

        return new MoonPosition(azimuth, altitude, parallacticAngle);
    }

    /**
     * Check if a date is a moon phase transition day (new/full/quarter).
     *
     * @param date The date to check
     * @return true if the date is closest to a major moon phase
     */
    public boolean isMoonPhaseDay(LocalDate date) {
        // Calculate the exact phase for today, yesterday, and tomorrow
        double phaseToday = calculateMoonPhaseValue(date);
        double phaseYesterday = calculateMoonPhaseValue(date.minusDays(1));
        double phaseTomorrow = calculateMoonPhaseValue(date.plusDays(1));

        // Find the closest approach to each major phase point
        // We want to show the icon on the day that's closest to the exact phase

        // Check for New Moon (0.0 or 1.0)
        double distToday = Math.min(phaseToday, 1.0 - phaseToday);
        double distYesterday = Math.min(phaseYesterday, 1.0 - phaseYesterday);
        double distTomorrow = Math.min(phaseTomorrow, 1.0 - phaseTomorrow);
        if (distToday < distYesterday && distToday < distTomorrow && distToday < 0.017) {
            return true;
        }

        // Check for First Quarter (0.25)
        distToday = Math.abs(phaseToday - 0.25);
        distYesterday = Math.abs(phaseYesterday - 0.25);
        distTomorrow = Math.abs(phaseTomorrow - 0.25);
        if (distToday < distYesterday && distToday < distTomorrow && distToday < 0.017) {
            return true;
        }

        // Check for Full Moon (0.5)
        distToday = Math.abs(phaseToday - 0.5);
        distYesterday = Math.abs(phaseYesterday - 0.5);
        distTomorrow = Math.abs(phaseTomorrow - 0.5);
        if (distToday < distYesterday && distToday < distTomorrow && distToday < 0.017) {
            return true;
        }

        // Check for Last Quarter (0.75)
        distToday = Math.abs(phaseToday - 0.75);
        distYesterday = Math.abs(phaseYesterday - 0.75);
        distTomorrow = Math.abs(phaseTomorrow - 0.75);
        if (distToday < distYesterday && distToday < distTomorrow && distToday < 0.017) {
            return true;
        }

        return false;
    }

    /**
     * Get Hebrew dates for a Gregorian year.
     * Delegates to HebrewCalendarService.
     *
     * @param year The Gregorian year
     * @return List of Hebrew date mappings
     */
    public List<HebrewDateMapping> getHebrewDates(int year) {
        // Delegate to HebrewCalendarService if needed
        // For now, return an empty list - this can be expanded later
        List<HebrewDateMapping> dates = new ArrayList<>();

        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);

        // This would ideally use HebrewCalendarService to convert each date
        // Simplified implementation for now
        return dates;
    }

    /**
     * Calculate sunrise and sunset times for a location and date.
     * This is a placeholder - actual implementation would use SunCalc library.
     *
     * @param date      The date
     * @param latitude  Latitude
     * @param longitude Longitude
     * @return SunriseSunset data
     */
    public SunriseSunset getSunriseSunset(LocalDate date, double latitude, double longitude) {
        // Placeholder - would use org.shredzone.commons.suncalc library for accurate calculations
        // For now, return default values
        return new SunriseSunset(date, null, null);
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
}
