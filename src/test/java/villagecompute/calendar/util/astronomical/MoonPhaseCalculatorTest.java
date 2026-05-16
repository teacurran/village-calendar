package villagecompute.calendar.util.astronomical;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import villagecompute.calendar.util.astronomical.MoonPhaseCalculator.MoonPhaseData;
import villagecompute.calendar.util.astronomical.MoonPhaseCalculator.MoonPhaseType;

/**
 * Unit tests for {@link MoonPhaseCalculator}.
 *
 * <p>
 * Expected phase dates are taken from independent astronomical references (NASA GSFC eclipse/lunar phase tables and
 * Astropixels.com phase ephemeris) for the year 2024, not derived from the same code under test. A ±1 day tolerance is
 * applied because SunCalc returns UTC instants which can fall on either side of a calendar boundary depending on the
 * exact hour, while the published ephemerides typically use noon ET or UT.
 */
class MoonPhaseCalculatorTest {

    /** Allowed tolerance (in days) between SunCalc result and the published ephemeris date. */
    private static final long DAY_TOLERANCE = 1L;

    // ---------------------------------------------------------------------
    // Known phase dates for 2024 (independent ephemeris reference)
    // Source: NASA GSFC + Astropixels.com lunar phase tables
    // ---------------------------------------------------------------------

    static Map<MoonPhaseType, List<LocalDate>> phases2024() {
        Map<MoonPhaseType, List<LocalDate>> map = new EnumMap<>(MoonPhaseType.class);
        map.put(MoonPhaseType.NEW_MOON, List.of(LocalDate.of(2024, 1, 11), LocalDate.of(2024, 2, 9),
                LocalDate.of(2024, 3, 10), LocalDate.of(2024, 4, 8), LocalDate.of(2024, 5, 8), LocalDate.of(2024, 6, 6),
                LocalDate.of(2024, 7, 5), LocalDate.of(2024, 8, 4), LocalDate.of(2024, 9, 3), LocalDate.of(2024, 10, 2),
                LocalDate.of(2024, 11, 1), LocalDate.of(2024, 12, 1), LocalDate.of(2024, 12, 30)));
        map.put(MoonPhaseType.FULL_MOON,
                List.of(LocalDate.of(2024, 1, 25), LocalDate.of(2024, 2, 24), LocalDate.of(2024, 3, 25),
                        LocalDate.of(2024, 4, 23), LocalDate.of(2024, 5, 23), LocalDate.of(2024, 6, 22),
                        LocalDate.of(2024, 7, 21), LocalDate.of(2024, 8, 19), LocalDate.of(2024, 9, 18),
                        LocalDate.of(2024, 10, 17), LocalDate.of(2024, 11, 15), LocalDate.of(2024, 12, 15)));
        map.put(MoonPhaseType.FIRST_QUARTER,
                List.of(LocalDate.of(2024, 1, 18), LocalDate.of(2024, 2, 16), LocalDate.of(2024, 3, 17),
                        LocalDate.of(2024, 4, 15), LocalDate.of(2024, 5, 15), LocalDate.of(2024, 6, 14),
                        LocalDate.of(2024, 7, 13), LocalDate.of(2024, 8, 12), LocalDate.of(2024, 9, 11),
                        LocalDate.of(2024, 10, 10), LocalDate.of(2024, 11, 9), LocalDate.of(2024, 12, 8)));
        map.put(MoonPhaseType.LAST_QUARTER,
                List.of(LocalDate.of(2024, 1, 4), LocalDate.of(2024, 2, 3), LocalDate.of(2024, 3, 3),
                        LocalDate.of(2024, 4, 2), LocalDate.of(2024, 5, 1), LocalDate.of(2024, 5, 30),
                        LocalDate.of(2024, 6, 28), LocalDate.of(2024, 7, 28), LocalDate.of(2024, 8, 26),
                        LocalDate.of(2024, 9, 24), LocalDate.of(2024, 10, 24), LocalDate.of(2024, 11, 23),
                        LocalDate.of(2024, 12, 22)));
        return map;
    }

    /**
     * Provides individual (expectedDate, phaseType) tuples for parameterized cross-check against the ephemeris.
     */
    static List<Arguments> knownPhaseDates() {
        Map<MoonPhaseType, List<LocalDate>> all = phases2024();
        List<Arguments> args = new java.util.ArrayList<>();
        for (Map.Entry<MoonPhaseType, List<LocalDate>> entry : all.entrySet()) {
            for (LocalDate date : entry.getValue()) {
                args.add(Arguments.of(date, entry.getKey()));
            }
        }
        return args;
    }

    // ---------------------------------------------------------------------
    // Parameterized: every published 2024 phase event must appear (±1 day)
    // ---------------------------------------------------------------------

    @ParameterizedTest(
            name = "{1} on {0}")
    @MethodSource("knownPhaseDates")
    void calculateMoonPhasesForYear_matchesPublishedEphemeris(LocalDate expectedDate, MoonPhaseType type) {
        List<MoonPhaseData> phases = MoonPhaseCalculator.calculateMoonPhasesForYear(2024);

        boolean matched = false;
        for (MoonPhaseData p : phases) {
            if (p.phase == type) {
                long diff = Math.abs(ChronoUnit.DAYS.between(p.date, expectedDate));
                if (diff <= DAY_TOLERANCE) {
                    matched = true;
                    break;
                }
            }
        }
        assertTrue(matched, "Expected " + type + " near " + expectedDate + " but it was not found within "
                + DAY_TOLERANCE + " day(s) in: " + phases);
    }

    // ---------------------------------------------------------------------
    // Coverage of all four phase types via collectPhaseEvents
    // (collectPhaseEvents is private; we exercise it indirectly through
    // calculateMoonPhasesForYear, which is its only call site)
    // ---------------------------------------------------------------------

    @Test
    void calculateMoonPhasesForYear_includesAllFourMajorPhases() {
        List<MoonPhaseData> phases = MoonPhaseCalculator.calculateMoonPhasesForYear(2024);

        boolean hasNew = false;
        boolean hasFull = false;
        boolean hasFirst = false;
        boolean hasLast = false;
        for (MoonPhaseData p : phases) {
            if (p.phase == MoonPhaseType.NEW_MOON) {
                hasNew = true;
            }
            if (p.phase == MoonPhaseType.FULL_MOON) {
                hasFull = true;
            }
            if (p.phase == MoonPhaseType.FIRST_QUARTER) {
                hasFirst = true;
            }
            if (p.phase == MoonPhaseType.LAST_QUARTER) {
                hasLast = true;
            }
        }
        assertTrue(hasNew, "Should include at least one NEW_MOON");
        assertTrue(hasFull, "Should include at least one FULL_MOON");
        assertTrue(hasFirst, "Should include at least one FIRST_QUARTER");
        assertTrue(hasLast, "Should include at least one LAST_QUARTER");
    }

    @Test
    void calculateMoonPhasesForYear_returnsSortedByDate() {
        List<MoonPhaseData> phases = MoonPhaseCalculator.calculateMoonPhasesForYear(2024);
        assertFalse(phases.isEmpty(), "Should return phase events for 2024");
        for (int i = 1; i < phases.size(); i++) {
            LocalDate prev = phases.get(i - 1).date;
            LocalDate next = phases.get(i).date;
            assertFalse(next.isBefore(prev),
                    "Phases must be sorted ascending by date; found " + prev + " before " + next);
        }
    }

    @Test
    void calculateMoonPhasesForYear_eventsAreWithinTheYear() {
        int year = 2024;
        List<MoonPhaseData> phases = MoonPhaseCalculator.calculateMoonPhasesForYear(year);

        for (MoonPhaseData p : phases) {
            assertEquals(year, p.date.getYear(), "All phase events must fall within target year, got " + p.date);
            assertNotNull(p.phase);
            // Each phase has a canonical fractional phase value
            if (p.phase == MoonPhaseType.NEW_MOON) {
                assertEquals(0.0, p.phaseValue, 1e-9);
            } else if (p.phase == MoonPhaseType.FIRST_QUARTER) {
                assertEquals(0.25, p.phaseValue, 1e-9);
            } else if (p.phase == MoonPhaseType.FULL_MOON) {
                assertEquals(0.5, p.phaseValue, 1e-9);
            } else if (p.phase == MoonPhaseType.LAST_QUARTER) {
                assertEquals(0.75, p.phaseValue, 1e-9);
            }
            // Illumination is always a valid fraction
            assertTrue(p.illumination >= 0.0 && p.illumination <= 1.0,
                    "Illumination must be in [0,1], got " + p.illumination + " on " + p.date);
        }
    }

    @Test
    void calculateMoonPhasesForYear_yearCount_isReasonable() {
        // A calendar year contains 12 or 13 lunations -> 12-13 of each phase type,
        // total roughly 48-52 events.
        List<MoonPhaseData> phases = MoonPhaseCalculator.calculateMoonPhasesForYear(2024);
        assertTrue(phases.size() >= 48 && phases.size() <= 53,
                "Expected 48-53 phase events for 2024, got " + phases.size());
    }

    // ---------------------------------------------------------------------
    // Year boundary: December -> January lunation wrap
    // ---------------------------------------------------------------------

    @Test
    void calculateMoonPhasesForYear_yearBoundary_doesNotLeakIntoNextYear() {
        // 2024 ends with a new moon on Dec 30. The next new moon (Jan 29, 2025)
        // must NOT appear in the 2024 list.
        List<MoonPhaseData> phases2024 = MoonPhaseCalculator.calculateMoonPhasesForYear(2024);
        for (MoonPhaseData p : phases2024) {
            assertEquals(2024, p.date.getYear(), "2024 result must not contain " + p.date);
        }

        // 2025 should start with a phase event in January, not duplicate the
        // Dec 30 2024 new moon.
        List<MoonPhaseData> phases2025 = MoonPhaseCalculator.calculateMoonPhasesForYear(2025);
        for (MoonPhaseData p : phases2025) {
            assertEquals(2025, p.date.getYear(), "2025 result must not contain " + p.date);
        }
        assertFalse(phases2025.isEmpty(), "2025 should still produce phase events");
        assertTrue(phases2025.get(0).date.getMonthValue() <= 2,
                "First 2025 event should be in Jan or Feb, got " + phases2025.get(0).date);
    }

    // ---------------------------------------------------------------------
    // Leap year handling
    // ---------------------------------------------------------------------

    @Test
    void calculateMoonPhasesForYear_leapYear_2024_includesFeb29Range() {
        // 2024 is a leap year. The Feb 24 full moon should be present
        // and the algorithm must traverse Feb 29 without skipping events.
        List<MoonPhaseData> phases = MoonPhaseCalculator.calculateMoonPhasesForYear(2024);
        boolean foundFebFull = false;
        for (MoonPhaseData p : phases) {
            if (p.phase == MoonPhaseType.FULL_MOON && p.date.getMonthValue() == 2
                    && Math.abs(ChronoUnit.DAYS.between(p.date, LocalDate.of(2024, 2, 24))) <= DAY_TOLERANCE) {
                foundFebFull = true;
                break;
            }
        }
        assertTrue(foundFebFull, "Leap-year February full moon (Feb 24, 2024) should be present");
    }

    @Test
    void calculateMoonPhasesForYear_nonLeapYear_2023_runsToCompletion() {
        // 2023 is not a leap year. Verify the algorithm still yields a sane
        // set of events spanning January through December.
        List<MoonPhaseData> phases = MoonPhaseCalculator.calculateMoonPhasesForYear(2023);
        assertFalse(phases.isEmpty());
        assertTrue(phases.get(0).date.getMonthValue() <= 2,
                "First event should be in Jan or Feb of 2023, got " + phases.get(0).date);
        assertTrue(phases.get(phases.size() - 1).date.getMonthValue() >= 11,
                "Last event should be in Nov or Dec of 2023, got " + phases.get(phases.size() - 1).date);
        for (MoonPhaseData p : phases) {
            assertEquals(2023, p.date.getYear());
        }
    }

    // ---------------------------------------------------------------------
    // Edge of supported year range
    // ---------------------------------------------------------------------

    @Test
    void calculateMoonPhasesForYear_pastYear_1970_producesResults() {
        // SunCalc supports a wide historical range. 1970 is a sensible lower
        // bound to confirm the loop does not break on old dates.
        List<MoonPhaseData> phases = MoonPhaseCalculator.calculateMoonPhasesForYear(1970);
        assertFalse(phases.isEmpty(), "Should produce events for 1970");
        for (MoonPhaseData p : phases) {
            assertEquals(1970, p.date.getYear());
        }
    }

    @Test
    void calculateMoonPhasesForYear_futureYear_2100_producesResults() {
        List<MoonPhaseData> phases = MoonPhaseCalculator.calculateMoonPhasesForYear(2100);
        assertFalse(phases.isEmpty(), "Should produce events for 2100");
        for (MoonPhaseData p : phases) {
            assertEquals(2100, p.date.getYear());
        }
    }

    // ---------------------------------------------------------------------
    // Other public API
    // ---------------------------------------------------------------------

    @Test
    void calculateMoonPhaseValue_isInUnitInterval() {
        // Spot-check the phase value bounds across several dates
        LocalDate[] samples = new LocalDate[]{LocalDate.of(2024, 1, 11), // new moon
                LocalDate.of(2024, 1, 25), // full moon
                LocalDate.of(2024, 6, 15), LocalDate.of(2024, 12, 31),};
        for (LocalDate d : samples) {
            double v = MoonPhaseCalculator.calculateMoonPhaseValue(d);
            assertTrue(v >= 0.0 && v <= 1.0, "phase value must be in [0,1] for " + d + ", got " + v);
        }
    }

    @Test
    void calculateMoonPhase_classifiesNewMoonNearKnownNewMoon() {
        // On Jan 11, 2024 (a known new moon), the phase value should be very
        // close to 0, so calculateMoonPhase should return NEW_MOON.
        MoonPhaseType type = MoonPhaseCalculator.calculateMoonPhase(LocalDate.of(2024, 1, 11));
        assertEquals(MoonPhaseType.NEW_MOON, type);
    }

    @Test
    void calculateMoonPhase_classifiesFullMoonNearKnownFullMoon() {
        MoonPhaseType type = MoonPhaseCalculator.calculateMoonPhase(LocalDate.of(2024, 1, 25));
        assertEquals(MoonPhaseType.FULL_MOON, type);
    }

    @Test
    void calculateIllumination_isInUnitInterval() {
        double illum = MoonPhaseCalculator.calculateIllumination(LocalDate.of(2024, 6, 22));
        assertTrue(illum >= 0.0 && illum <= 1.0, "illumination must be in [0,1], got " + illum);
        // June 22, 2024 is very close to a full moon, so illumination should be high.
        assertTrue(illum > 0.95, "Expected near-full illumination near Jun 22, 2024, got " + illum);
    }

    @Test
    void calculateIlluminationData_returnsCoherentValues() {
        MoonPhaseCalculator.MoonIlluminationData data = MoonPhaseCalculator
                .calculateIlluminationData(LocalDate.of(2024, 1, 25));
        assertNotNull(data);
        assertTrue(data.fraction >= 0.0 && data.fraction <= 1.0);
        assertTrue(data.phase >= 0.0 && data.phase <= 1.0);
    }

    @Test
    void calculatePosition_returnsNonNullCoordinates() {
        // Groton, Vermont coordinates as an arbitrary observer location.
        MoonPhaseCalculator.MoonPositionData pos = MoonPhaseCalculator.calculatePosition(LocalDate.of(2024, 6, 22),
                44.21, -72.21);
        assertNotNull(pos);
        // The underlying SunCalc API has varied between radians and degrees
        // across versions; the only portable guarantee for unit testing is
        // that the returned values are finite numbers and not NaN.
        assertTrue(Double.isFinite(pos.azimuth), "azimuth must be finite, got " + pos.azimuth);
        assertTrue(Double.isFinite(pos.altitude), "altitude must be finite, got " + pos.altitude);
        assertTrue(Double.isFinite(pos.parallacticAngle),
                "parallacticAngle must be finite, got " + pos.parallacticAngle);
    }

    @Test
    void isMoonPhaseDay_trueOnKnownFullMoon_falseInBetween() {
        // Within a day of a major phase the helper should flag at least one
        // of the surrounding three dates as a phase day. We probe a 3-day
        // window centered on the published full moon to absorb UTC rounding.
        LocalDate fullMoon = LocalDate.of(2024, 1, 25);
        boolean any = MoonPhaseCalculator.isMoonPhaseDay(fullMoon.minusDays(1))
                || MoonPhaseCalculator.isMoonPhaseDay(fullMoon)
                || MoonPhaseCalculator.isMoonPhaseDay(fullMoon.plusDays(1));
        assertTrue(any, "Expected at least one of 2024-01-24/25/26 to be flagged as a phase day");

        // A date midway between phases should not be flagged.
        assertFalse(MoonPhaseCalculator.isMoonPhaseDay(LocalDate.of(2024, 1, 31)),
                "Mid-cycle date should not be flagged as a phase day");
    }

    @Test
    void moonPhaseData_constructor_storesAllFields() {
        LocalDate d = LocalDate.of(2024, 3, 10);
        MoonPhaseData data = new MoonPhaseData(d, MoonPhaseType.NEW_MOON, 0.0, 0.01);
        assertEquals(d, data.date);
        assertEquals(MoonPhaseType.NEW_MOON, data.phase);
        assertEquals(0.0, data.phaseValue, 1e-9);
        assertEquals(0.01, data.illumination, 1e-9);
    }
}
