package villagecompute.calendar.util.astronomical;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.shredzone.commons.suncalc.MoonIllumination;
import org.shredzone.commons.suncalc.MoonPhase;
import org.shredzone.commons.suncalc.MoonPosition;

/**
 * Utility class for calculating moon phases, illumination, and positions using the SunCalc library.
 *
 * <p>
 * This class provides accurate astronomical calculations for: - Moon phase transitions (new moon, full moon, quarters)
 * - Moon illumination percentages - Moon position (azimuth, altitude, parallactic angle) for observer location
 */
public class MoonPhaseCalculator {

    /** Data class representing a moon phase transition event. */
    public static class MoonPhaseData {
        public LocalDate date;
        public MoonPhaseType phase;
        public double phaseValue;
        public double illumination;

        public MoonPhaseData(LocalDate date, MoonPhaseType phase, double phaseValue, double illumination) {
            this.date = date;
            this.phase = phase;
            this.phaseValue = phaseValue;
            this.illumination = illumination;
        }
    }

    /** Moon phase types. */
    public enum MoonPhaseType {
        NEW_MOON, WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS, FULL_MOON, WANING_GIBBOUS, LAST_QUARTER, WANING_CRESCENT
    }

    /** Data class for moon illumination details. */
    public static class MoonIlluminationData {
        public double fraction; // 0 to 1, illuminated fraction
        public double phase; // 0 to 1, phase in the lunar cycle
        public double angle; // angle in radians

        public MoonIlluminationData(double fraction, double phase, double angle) {
            this.fraction = fraction;
            this.phase = phase;
            this.angle = angle;
        }
    }

    /** Data class for moon position at observer's location. */
    public static class MoonPositionData {
        public double azimuth; // azimuth in radians
        public double altitude; // altitude in radians
        public double parallacticAngle; // parallactic angle in radians

        public MoonPositionData(double azimuth, double altitude, double parallacticAngle) {
            this.azimuth = azimuth;
            this.altitude = altitude;
            this.parallacticAngle = parallacticAngle;
        }
    }

    /**
     * Calculate all major moon phase transitions for a given year using SunCalc. This is optimized to directly
     * calculate the ~13 major phases rather than iterating all 365 days.
     *
     * @param year
     *            The year to calculate moon phases for
     * @return List of MoonPhaseData for each major phase transition
     */
    public static List<MoonPhaseData> calculateMoonPhasesForYear(int year) {
        List<MoonPhaseData> phases = new ArrayList<>();

        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);

        ZonedDateTime start = startDate.atStartOfDay(ZoneId.of("UTC"));
        ZonedDateTime end = endDate.atStartOfDay(ZoneId.of("UTC"));

        collectPhaseEvents(phases, start, end, endDate, MoonPhase.Phase.NEW_MOON, MoonPhaseType.NEW_MOON, 0.0);
        collectPhaseEvents(phases, start, end, endDate, MoonPhase.Phase.FULL_MOON, MoonPhaseType.FULL_MOON, 0.5);
        collectPhaseEvents(phases, start, end, endDate, MoonPhase.Phase.FIRST_QUARTER, MoonPhaseType.FIRST_QUARTER,
                0.25);
        collectPhaseEvents(phases, start, end, endDate, MoonPhase.Phase.LAST_QUARTER, MoonPhaseType.LAST_QUARTER, 0.75);

        // Sort by date
        phases.sort((a, b) -> a.date.compareTo(b.date));

        return phases;
    }

    /**
     * Collect all occurrences of a specific moon phase within the year window. Iterates from start, advancing
     * approximately one lunation (29 days) past each found event, until either no event is returned or the next event
     * falls outside the end date.
     *
     * @param phases
     *            Output list to which discovered phase events are appended
     * @param start
     *            Window start (UTC)
     * @param end
     *            Window end (UTC), used as the loop guard
     * @param endDate
     *            Inclusive last calendar date for the year (events after this are dropped)
     * @param suncalcPhase
     *            The SunCalc phase enum to query
     * @param phaseType
     *            The MoonPhaseType to record on the resulting data
     * @param phaseValue
     *            The fractional phase value (0.0, 0.25, 0.5, 0.75) to record
     */
    private static void collectPhaseEvents(List<MoonPhaseData> phases, ZonedDateTime start, ZonedDateTime end,
            LocalDate endDate, MoonPhase.Phase suncalcPhase, MoonPhaseType phaseType, double phaseValue) {
        ZonedDateTime current = start;
        while (current.isBefore(end)) {
            ZonedDateTime phaseTime = MoonPhase.compute().phase(suncalcPhase).on(current).execute().getTime();
            if (phaseTime == null || phaseTime.toLocalDate().isAfter(endDate)) {
                break;
            }
            LocalDate phaseDate = phaseTime.toLocalDate();
            double illumination = calculateIllumination(phaseDate);
            phases.add(new MoonPhaseData(phaseDate, phaseType, phaseValue, illumination));
            // Move to next lunation (add 29 days, slightly less than 29.53)
            current = phaseTime.plusDays(29);
        }
    }

    /**
     * Calculate the moon phase for a specific date.
     *
     * @param date
     *            The date to calculate the moon phase for
     * @return The moon phase type
     */
    public static MoonPhaseType calculateMoonPhase(LocalDate date) {
        double phaseValue = calculateMoonPhaseValue(date);

        // Determine moon phase based on the fraction
        if (phaseValue < 0.0625 || phaseValue >= 0.9375) {
            return MoonPhaseType.NEW_MOON;
        } else if (phaseValue < 0.1875) {
            return MoonPhaseType.WAXING_CRESCENT;
        } else if (phaseValue < 0.3125) {
            return MoonPhaseType.FIRST_QUARTER;
        } else if (phaseValue < 0.4375) {
            return MoonPhaseType.WAXING_GIBBOUS;
        } else if (phaseValue < 0.5625) {
            return MoonPhaseType.FULL_MOON;
        } else if (phaseValue < 0.6875) {
            return MoonPhaseType.WANING_GIBBOUS;
        } else if (phaseValue < 0.8125) {
            return MoonPhaseType.LAST_QUARTER;
        } else {
            return MoonPhaseType.WANING_CRESCENT;
        }
    }

    /**
     * Calculate moon phase as a fractional value from 0 to 1. 0 = new moon, 0.25 = first quarter, 0.5 = full moon, 0.75
     * = last quarter
     *
     * @param date
     *            The date to calculate
     * @return Moon phase value (0.0 to 1.0)
     */
    public static double calculateMoonPhaseValue(LocalDate date) {
        ZonedDateTime zdt = date.atStartOfDay(ZoneId.of("UTC"));

        MoonIllumination moonIllum = MoonIllumination.compute().on(zdt).execute();

        // Calculate phase from illumination fraction and angle
        // The angle tells us the direction (positive = waxing, negative = waning)
        double fraction = moonIllum.getFraction();
        double angle = moonIllum.getAngle(); // This is in radians

        // Phase value calculation based on illumination and angle
        // New moon: fraction ≈ 0
        // First quarter: fraction ≈ 0.5, angle > 0
        // Full moon: fraction ≈ 1.0
        // Last quarter: fraction ≈ 0.5, angle < 0

        double phaseValue;
        if (angle >= 0) {
            // Waxing phase (New -> First Quarter -> Full)
            // Map fraction 0-1 to phase 0-0.5
            phaseValue = fraction * 0.5;
        } else {
            // Waning phase (Full -> Last Quarter -> New)
            // Map fraction 1-0 to phase 0.5-1.0
            phaseValue = 0.5 + (1.0 - fraction) * 0.5;
        }

        return phaseValue;
    }

    /**
     * Calculate moon illumination for a given date using SunCalc.
     *
     * @param date
     *            The date to calculate illumination for
     * @return MoonIlluminationData with fraction, phase, and angle
     */
    public static MoonIlluminationData calculateIlluminationData(LocalDate date) {
        ZonedDateTime zdt = date.atStartOfDay(ZoneId.of("UTC"));

        MoonIllumination moonIllum = MoonIllumination.compute().on(zdt).execute();

        // Calculate the phase value (0-1) using our method
        double phaseValue = calculateMoonPhaseValue(date);

        return new MoonIlluminationData(moonIllum.getFraction(), phaseValue, // Use calculated phase value instead of
                                                                             // raw getPhase()
                moonIllum.getAngle());
    }

    /**
     * Calculate moon illumination percentage (0 to 1) for a given date.
     *
     * @param date
     *            The date to calculate illumination for
     * @return Illumination fraction (0.0 to 1.0)
     */
    public static double calculateIllumination(LocalDate date) {
        ZonedDateTime zdt = date.atStartOfDay(ZoneId.of("UTC"));

        return MoonIllumination.compute().on(zdt).execute().getFraction();
    }

    /**
     * Calculate moon position for an observer's location using SunCalc. This affects how the moon appears (rotation)
     * based on hemisphere.
     *
     * @param date
     *            The date to calculate
     * @param latitude
     *            Observer's latitude in degrees
     * @param longitude
     *            Observer's longitude in degrees
     * @return MoonPositionData with azimuth, altitude, and parallactic angle
     */
    public static MoonPositionData calculatePosition(LocalDate date, double latitude, double longitude) {
        ZonedDateTime zdt = date.atStartOfDay(ZoneId.of("UTC"));

        MoonPosition moonPos = MoonPosition.compute().on(zdt).at(latitude, longitude).execute();

        return new MoonPositionData(moonPos.getAzimuth(), moonPos.getAltitude(), moonPos.getParallacticAngle());
    }

    /**
     * Check if a date is a moon phase transition day (new/full/quarter). This is used to determine which days should
     * show moon phase icons.
     *
     * @param date
     *            The date to check
     * @return true if the date is closest to a major moon phase
     */
    public static boolean isMoonPhaseDay(LocalDate date) {
        double phaseValue = calculateMoonPhaseValue(date);
        double phaseYesterday = calculateMoonPhaseValue(date.minusDays(1));
        double phaseTomorrow = calculateMoonPhaseValue(date.plusDays(1));

        // Check proximity to major phases (0, 0.25, 0.5, 0.75)
        // New Moon (0.0 or 1.0)
        double distToday = Math.min(phaseValue, 1.0 - phaseValue);
        double distYesterday = Math.min(phaseYesterday, 1.0 - phaseYesterday);
        double distTomorrow = Math.min(phaseTomorrow, 1.0 - phaseTomorrow);
        if (distToday < distYesterday && distToday < distTomorrow && distToday < 0.017) {
            return true;
        }

        // First Quarter (0.25)
        distToday = Math.abs(phaseValue - 0.25);
        distYesterday = Math.abs(phaseYesterday - 0.25);
        distTomorrow = Math.abs(phaseTomorrow - 0.25);
        if (distToday < distYesterday && distToday < distTomorrow && distToday < 0.017) {
            return true;
        }

        // Full Moon (0.5)
        distToday = Math.abs(phaseValue - 0.5);
        distYesterday = Math.abs(phaseYesterday - 0.5);
        distTomorrow = Math.abs(phaseTomorrow - 0.5);
        if (distToday < distYesterday && distToday < distTomorrow && distToday < 0.017) {
            return true;
        }

        // Last Quarter (0.75)
        distToday = Math.abs(phaseValue - 0.75);
        distYesterday = Math.abs(phaseYesterday - 0.75);
        distTomorrow = Math.abs(phaseTomorrow - 0.75);
        return distToday < distYesterday && distToday < distTomorrow && distToday < 0.017;
    }
}
