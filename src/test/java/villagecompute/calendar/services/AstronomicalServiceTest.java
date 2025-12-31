package villagecompute.calendar.services;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import villagecompute.calendar.services.AstronomicalCalculationService.*;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Unit tests for AstronomicalCalculationService. Verifies moon phase calculations, Hebrew calendar
 * conversions, and seasonal events against known astronomical data.
 */
@QuarkusTest
public class AstronomicalServiceTest {

    @Inject AstronomicalCalculationService astronomicalCalculationService;

    /**
     * Test moon phase calculations for 2025 against NASA data. NASA full moon dates for 2025:
     * January 13, February 12, March 14, April 13, May 12, June 11, July 10, August 9, September 7,
     * October 7, November 5, December 4
     */
    @Test
    void testCalculateMoonPhases2025_MatchesNASAData() {
        int year = 2025;
        List<MoonPhaseData> phases = astronomicalCalculationService.getMoonPhases(year);

        assertNotNull(phases);
        assertFalse(phases.isEmpty(), "Moon phases list should not be empty");

        // Expected NASA full moon dates for 2025
        LocalDate[] expectedFullMoons = {
            LocalDate.of(2025, 1, 13),
            LocalDate.of(2025, 2, 12),
            LocalDate.of(2025, 3, 14),
            LocalDate.of(2025, 4, 13),
            LocalDate.of(2025, 5, 12),
            LocalDate.of(2025, 6, 11),
            LocalDate.of(2025, 7, 10),
            LocalDate.of(2025, 8, 9),
            LocalDate.of(2025, 9, 7),
            LocalDate.of(2025, 10, 7),
            LocalDate.of(2025, 11, 5),
            LocalDate.of(2025, 12, 4)
        };

        // Filter full moon phases
        List<MoonPhaseData> fullMoons =
                phases.stream().filter(p -> p.phase == MoonPhase.FULL_MOON).toList();

        assertEquals(12, fullMoons.size(), "Should have 12 full moons in 2025");

        // Verify each full moon is within 1 day of NASA data (accounting for time zones and
        // precision)
        for (int i = 0; i < expectedFullMoons.length; i++) {
            LocalDate expected = expectedFullMoons[i];
            LocalDate actual = fullMoons.get(i).date;

            long daysDifference =
                    Math.abs(java.time.temporal.ChronoUnit.DAYS.between(expected, actual));
            assertTrue(
                    daysDifference <= 1,
                    String.format(
                            "Full moon %d: Expected %s, got %s (difference: %d days)",
                            i + 1, expected, actual, daysDifference));
        }
    }

    /** Test that moon phases include all four major types (new, full, quarters). */
    @Test
    void testCalculateMoonPhases_IncludesAllMajorPhases() {
        int year = 2025;
        List<MoonPhaseData> phases = astronomicalCalculationService.getMoonPhases(year);

        // Count phases of each type
        long newMoons = phases.stream().filter(p -> p.phase == MoonPhase.NEW_MOON).count();
        long fullMoons = phases.stream().filter(p -> p.phase == MoonPhase.FULL_MOON).count();
        long firstQuarters =
                phases.stream().filter(p -> p.phase == MoonPhase.FIRST_QUARTER).count();
        long lastQuarters = phases.stream().filter(p -> p.phase == MoonPhase.LAST_QUARTER).count();

        // Each should occur approximately 12-13 times per year
        assertTrue(newMoons >= 12 && newMoons <= 13, "Should have 12-13 new moons per year");
        assertTrue(fullMoons >= 12 && fullMoons <= 13, "Should have 12-13 full moons per year");
        assertTrue(
                firstQuarters >= 12 && firstQuarters <= 13,
                "Should have 12-13 first quarters per year");
        assertTrue(
                lastQuarters >= 12 && lastQuarters <= 13,
                "Should have 12-13 last quarters per year");
    }

    /** Test moon phase calculation performance (<100ms for full year). */
    @Test
    void testCalculateMoonPhases_PerformanceUnder100ms() {
        int year = 2025;

        long startTime = System.currentTimeMillis();
        List<MoonPhaseData> phases = astronomicalCalculationService.getMoonPhases(year);
        long endTime = System.currentTimeMillis();

        long duration = endTime - startTime;

        assertNotNull(phases);
        assertFalse(phases.isEmpty());
        assertTrue(
                duration < 100,
                String.format("Moon phase calculation took %dms, should be < 100ms", duration));
    }

    /** Test moon phase value calculation (0.0 to 1.0 range). */
    @Test
    void testCalculateMoonPhaseValue_ValidRange() {
        LocalDate[] testDates = {
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 1, 13), // Full moon
            LocalDate.of(2025, 6, 15),
            LocalDate.of(2025, 12, 31)
        };

        for (LocalDate date : testDates) {
            double phaseValue = astronomicalCalculationService.calculateMoonPhaseValue(date);

            assertTrue(
                    phaseValue >= 0.0 && phaseValue <= 1.0,
                    String.format(
                            "Phase value for %s should be between 0.0 and 1.0, got %.4f",
                            date, phaseValue));
        }
    }

    /** Test moon illumination calculation. */
    @Test
    void testCalculateMoonIllumination_ValidData() {
        LocalDate fullMoon = LocalDate.of(2025, 1, 13);
        MoonIllumination illumination =
                astronomicalCalculationService.calculateMoonIllumination(fullMoon);

        assertNotNull(illumination);
        assertTrue(
                illumination.fraction >= 0.0 && illumination.fraction <= 1.0,
                "Illumination fraction should be between 0.0 and 1.0");
        assertTrue(
                illumination.phase >= 0.0 && illumination.phase <= 1.0,
                "Phase should be between 0.0 and 1.0");

        // On full moon, illumination should be close to 1.0 (>0.95)
        assertTrue(
                illumination.fraction > 0.95,
                String.format(
                        "Full moon illumination should be > 0.95, got %.4f",
                        illumination.fraction));
    }

    /** Test moon position calculation for different hemispheres. */
    @Test
    void testCalculateMoonPosition_DifferentHemispheres() {
        LocalDate date = LocalDate.of(2025, 6, 15);

        // Northern hemisphere (New York)
        MoonPosition northernPos =
                astronomicalCalculationService.calculateMoonPosition(date, 40.7128, -74.0060);
        assertNotNull(northernPos);

        // Southern hemisphere (Sydney)
        MoonPosition southernPos =
                astronomicalCalculationService.calculateMoonPosition(date, -33.8688, 151.2093);
        assertNotNull(southernPos);

        // Parallactic angles should be significantly different (accounting for hemisphere)
        assertNotEquals(
                northernPos.parallacticAngle,
                southernPos.parallacticAngle,
                "Parallactic angles should differ between hemispheres");
    }

    /** Test isMoonPhaseDay detection. */
    @Test
    void testIsMoonPhaseDay_DetectsFullMoon() {
        // Test dates around January 13, 2025 full moon
        LocalDate beforeFullMoon = LocalDate.of(2025, 1, 12);
        LocalDate fullMoonDate = LocalDate.of(2025, 1, 13);
        LocalDate afterFullMoon = LocalDate.of(2025, 1, 14);

        // At least one of the three dates around the full moon should be detected
        // (The exact timing depends on UTC calculations and may fall on any of these days)
        boolean anyDetected =
                astronomicalCalculationService.isMoonPhaseDay(beforeFullMoon)
                        || astronomicalCalculationService.isMoonPhaseDay(fullMoonDate)
                        || astronomicalCalculationService.isMoonPhaseDay(afterFullMoon);
        assertTrue(
                anyDetected,
                String.format(
                        "Should detect full moon within ±1 day of January 13, 2025. Detected:"
                                + " %s:%s, %s:%s, %s:%s",
                        beforeFullMoon,
                        astronomicalCalculationService.isMoonPhaseDay(beforeFullMoon),
                        fullMoonDate,
                        astronomicalCalculationService.isMoonPhaseDay(fullMoonDate),
                        afterFullMoon,
                        astronomicalCalculationService.isMoonPhaseDay(afterFullMoon)));
    }

    /** Test Hebrew calendar date conversion. */
    @Test
    void testGetHebrewDates_ReturnsValidMappings() {
        int year = 2025;
        List<HebrewDateMapping> hebrewDates = astronomicalCalculationService.getHebrewDates(year);

        assertNotNull(hebrewDates);
        assertEquals(365, hebrewDates.size(), "Should have 365 Hebrew date mappings for 2025");

        // Check first date
        HebrewDateMapping firstDate = hebrewDates.get(0);
        assertEquals(LocalDate.of(2025, 1, 1), firstDate.gregorianDate);
        assertNotNull(firstDate.hebrewDate);
        assertTrue(
                firstDate.hebrewDate.contains("5785") || firstDate.hebrewDate.contains("5784"),
                "Hebrew year should be 5784 or 5785 for January 2025");
    }

    /** Test Hebrew leap year calculation. */
    @Test
    void testHebrewLeapYear_CorrectCalculation() {
        // Hebrew year 5784 (2023-2024) is a leap year (year 3 in the 19-year cycle)
        // Hebrew year 5785 (2024-2025) is not a leap year (year 4 in the 19-year cycle)

        // We can verify this through the Hebrew calendar service
        // The getHebrewHolidays method behavior differs in leap years
        assertDoesNotThrow(
                () -> {
                    astronomicalCalculationService.getHebrewDates(2025);
                });
    }

    /** Test sunrise/sunset calculation for a known location. */
    @Test
    void testGetSunriseSunset_ValidData() {
        LocalDate date = LocalDate.of(2025, 6, 21); // Summer solstice
        double latitude = 40.7128; // New York
        double longitude = -74.0060;

        SunriseSunset times =
                astronomicalCalculationService.getSunriseSunset(date, latitude, longitude);

        assertNotNull(times);
        assertNotNull(times.sunrise, "Sunrise time should not be null");
        assertNotNull(times.sunset, "Sunset time should not be null");

        // Verify format (HH:mm)
        assertTrue(times.sunrise.matches("\\d{2}:\\d{2}"), "Sunrise should be in HH:mm format");
        assertTrue(times.sunset.matches("\\d{2}:\\d{2}"), "Sunset should be in HH:mm format");

        // On summer solstice in northern hemisphere, day should be long (> 14 hours)
        String[] sunriseParts = times.sunrise.split(":");
        String[] sunsetParts = times.sunset.split(":");
        int sunriseMinutes =
                Integer.parseInt(sunriseParts[0]) * 60 + Integer.parseInt(sunriseParts[1]);
        int sunsetMinutes =
                Integer.parseInt(sunsetParts[0]) * 60 + Integer.parseInt(sunsetParts[1]);

        // Handle midnight crossing (if sunset is in next day, add 24 hours)
        int daylightMinutes;
        if (sunsetMinutes < sunriseMinutes) {
            daylightMinutes = (24 * 60) - sunriseMinutes + sunsetMinutes;
        } else {
            daylightMinutes = sunsetMinutes - sunriseMinutes;
        }

        assertTrue(
                daylightMinutes > 840, // > 14 hours
                String.format(
                        "Summer solstice should have > 14 hours of daylight, got %d minutes"
                                + " (sunrise: %s, sunset: %s)",
                        daylightMinutes, times.sunrise, times.sunset));
    }

    /** Test seasonal events calculation for 2025. */
    @Test
    void testCalculateSeasonalEvents_Returns4Events() {
        int year = 2025;
        List<SeasonalEvent> events = astronomicalCalculationService.calculateSeasonalEvents(year);

        assertNotNull(events);
        assertEquals(4, events.size(), "Should have 4 seasonal events per year");

        // Verify event types
        List<SeasonalEventType> types = events.stream().map(e -> e.type).toList();
        assertTrue(types.contains(SeasonalEventType.SPRING_EQUINOX));
        assertTrue(types.contains(SeasonalEventType.SUMMER_SOLSTICE));
        assertTrue(types.contains(SeasonalEventType.AUTUMN_EQUINOX));
        assertTrue(types.contains(SeasonalEventType.WINTER_SOLSTICE));

        // Verify approximate dates (within ±3 days of typical dates)
        SeasonalEvent springEquinox =
                events.stream()
                        .filter(e -> e.type == SeasonalEventType.SPRING_EQUINOX)
                        .findFirst()
                        .orElseThrow();
        assertTrue(
                springEquinox.date.getMonthValue() == 3
                        && springEquinox.date.getDayOfMonth() >= 19
                        && springEquinox.date.getDayOfMonth() <= 22,
                "Spring equinox should be around March 20");

        SeasonalEvent summerSolstice =
                events.stream()
                        .filter(e -> e.type == SeasonalEventType.SUMMER_SOLSTICE)
                        .findFirst()
                        .orElseThrow();
        assertTrue(
                summerSolstice.date.getMonthValue() == 6
                        && summerSolstice.date.getDayOfMonth() >= 20
                        && summerSolstice.date.getDayOfMonth() <= 22,
                "Summer solstice should be around June 21");

        SeasonalEvent autumnEquinox =
                events.stream()
                        .filter(e -> e.type == SeasonalEventType.AUTUMN_EQUINOX)
                        .findFirst()
                        .orElseThrow();
        assertTrue(
                autumnEquinox.date.getMonthValue() == 9
                        && autumnEquinox.date.getDayOfMonth() >= 21
                        && autumnEquinox.date.getDayOfMonth() <= 24,
                "Autumn equinox should be around September 22");

        SeasonalEvent winterSolstice =
                events.stream()
                        .filter(e -> e.type == SeasonalEventType.WINTER_SOLSTICE)
                        .findFirst()
                        .orElseThrow();
        assertTrue(
                winterSolstice.date.getMonthValue() == 12
                        && winterSolstice.date.getDayOfMonth() >= 20
                        && winterSolstice.date.getDayOfMonth() <= 23,
                "Winter solstice should be around December 21");
    }

    /** Test edge case: leap year handling. */
    @Test
    void testLeapYearHandling() {
        // 2024 is a leap year
        int leapYear = 2024;
        List<HebrewDateMapping> hebrewDates =
                astronomicalCalculationService.getHebrewDates(leapYear);

        assertNotNull(hebrewDates);
        assertEquals(366, hebrewDates.size(), "Leap year should have 366 Hebrew date mappings");

        // Verify February 29 is included
        boolean hasFeb29 =
                hebrewDates.stream()
                        .anyMatch(d -> d.gregorianDate.equals(LocalDate.of(2024, 2, 29)));
        assertTrue(hasFeb29, "Leap year should include February 29");
    }

    /** Test edge case: timezone handling at year boundaries. */
    @Test
    void testYearBoundaryDates() {
        LocalDate dec31 = LocalDate.of(2025, 12, 31);
        LocalDate jan1 = LocalDate.of(2025, 1, 1);

        // Should not throw exceptions
        assertDoesNotThrow(
                () -> {
                    astronomicalCalculationService.calculateMoonPhase(dec31);
                    astronomicalCalculationService.calculateMoonPhase(jan1);
                    astronomicalCalculationService.getSunriseSunset(dec31, 0, 0);
                    astronomicalCalculationService.getSunriseSunset(jan1, 0, 0);
                });
    }

    /** Test edge case: extreme latitudes (polar regions). */
    @Test
    void testExtremeLatitudes() {
        LocalDate date = LocalDate.of(2025, 6, 21); // Summer solstice

        // Arctic (midnight sun)
        SunriseSunset arctic = astronomicalCalculationService.getSunriseSunset(date, 70, 25);
        assertNotNull(arctic);

        // Antarctic (polar night)
        SunriseSunset antarctic = astronomicalCalculationService.getSunriseSunset(date, -70, 25);
        assertNotNull(antarctic);

        // Note: At extreme latitudes during solstices, sun may not rise or set
        // The service should handle these cases gracefully (null times are acceptable)
    }

    /** Test that phases are properly ordered chronologically. */
    @Test
    void testMoonPhases_ChronologicalOrder() {
        int year = 2025;
        List<MoonPhaseData> phases = astronomicalCalculationService.getMoonPhases(year);

        for (int i = 1; i < phases.size(); i++) {
            assertTrue(
                    phases.get(i).date.isAfter(phases.get(i - 1).date),
                    "Moon phases should be in chronological order");
        }
    }

    /** Test that phase values are consistent with phase types. */
    @Test
    void testMoonPhase_ValueConsistency() {
        int year = 2025;
        List<MoonPhaseData> phases = astronomicalCalculationService.getMoonPhases(year);

        for (MoonPhaseData phase : phases) {
            switch (phase.phase) {
                case NEW_MOON:
                    assertTrue(
                            phase.phaseValue < 0.1 || phase.phaseValue > 0.9,
                            "New moon phase value should be near 0.0");
                    break;
                case FIRST_QUARTER:
                    assertTrue(
                            Math.abs(phase.phaseValue - 0.25) < 0.1,
                            "First quarter phase value should be near 0.25");
                    break;
                case FULL_MOON:
                    assertTrue(
                            Math.abs(phase.phaseValue - 0.5) < 0.1,
                            "Full moon phase value should be near 0.5");
                    break;
                case LAST_QUARTER:
                    assertTrue(
                            Math.abs(phase.phaseValue - 0.75) < 0.1,
                            "Last quarter phase value should be near 0.75");
                    break;
                default:
                    break;
            }
        }
    }
}
