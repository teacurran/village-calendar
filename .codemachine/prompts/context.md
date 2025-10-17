# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I2.T5",
  "iteration_id": "I2",
  "iteration_goal": "Implement calendar creation, editing, and template system. Build calendar editor UI components. Integrate astronomical calculations (moon phases, Hebrew calendar). Create sequence diagrams for key workflows",
  "description": "Integrate SunCalc library (or Java port) for moon phase calculations and Proj4J for geospatial projections. Create AstronomicalService with methods: calculateMoonPhases (for calendar year, returns array of phase dates and illumination percentages), calculateHebrewCalendarDates (convert Gregorian dates to Hebrew calendar), calculateSeasonalEvents (equinoxes, solstices). Store calculation results in calendar.config JSONB field or separate table (if complex). Add configuration option in calendar editor to enable/disable astronomical overlays. Write unit tests verifying calculation accuracy against known astronomical data (e.g., 2025 full moon dates).",
  "agent_type_hint": "BackendAgent",
  "inputs": "Astronomical calculation requirements from Plan Section \"Features\", SunCalc and Proj4J library documentation",
  "target_files": [
    "src/main/java/villagecompute/calendar/services/AstronomicalService.java",
    "src/main/java/villagecompute/calendar/util/astronomical/MoonPhaseCalculator.java",
    "src/main/java/villagecompute/calendar/util/astronomical/HebrewCalendarConverter.java",
    "src/test/java/villagecompute/calendar/services/AstronomicalServiceTest.java"
  ],
  "input_files": [
    "pom.xml",
    "src/main/java/villagecompute/calendar/model/Calendar.java"
  ],
  "deliverables": "AstronomicalService class with calculation methods, SunCalc/Proj4J integration (Maven dependencies added), Moon phase and Hebrew calendar calculation logic, Unit tests verifying calculation accuracy",
  "acceptance_criteria": "AstronomicalService.calculateMoonPhases(2025) returns correct full moon dates for 2025, AstronomicalService.calculateHebrewCalendarDates() converts Gregorian to Hebrew dates accurately, Unit tests compare calculations against NASA moon phase data or Hebrew calendar tables, Service methods handle edge cases (leap years, timezone conversions), Calculation performance: <100ms for full year of moon phases",
  "dependencies": ["I2.T2"],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Astronomical Calculation Requirements (from 02_Architecture_Overview.md)

From the Technology Stack Summary table, line 105:
```markdown
| **Astronomical Calculations** | SunCalc (port) + Proj4J | Custom/4.1 | Moon phase calculations, Hebrew calendar conversions. Geospatial projections. |
```

### Context: Project Vision (from 01_Context_and_Drivers.md)

From line 15:
```markdown
Village Calendar is a full-stack web application that empowers users to create, customize, and order professional-quality full-year calendars. The platform combines an intuitive browser-based calendar editor with sophisticated astronomical calculations (moon phases, Hebrew calendar integration) and a complete e-commerce fulfillment pipeline. Users can design calendars with custom events, emojis, holiday sets, and astronomical overlays, then either download high-resolution PDFs or order physical printed versions delivered to their door.
```

### Context: Functional Requirements - Calendar Features (from 01_Context_and_Drivers.md)

From line 119:
```markdown
- Enable astronomical overlays (moon phases, Hebrew calendar dates)
- Real-time preview with print-safe area indicators
```

### Context: Technology Stack - Backend Components (from 01_Plan_Overview_and_Setup.md)

From line 122:
```markdown
2. **Calendar Service**: CRUD operations, template application, event management, astronomical calculations
```

### Context: Directory Structure - Utility Classes (from 01_Plan_Overview_and_Setup.md)

From lines 394-395:
```markdown
│   │   │       ├── astronomical/        # Moon phase, Hebrew calendar calcs
│   │   │       └── pdf/                 # Batik rendering utilities
```

### Context: Task I2.T5 Full Description (from 02_Iteration_I2.md)

From lines 205-245:
```markdown
### Task 2.5: Integrate Astronomical Calculation Libraries

**Task ID:** `I2.T5`

**Description:**
Integrate SunCalc library (or Java port) for moon phase calculations and Proj4J for geospatial projections. Create AstronomicalService with methods: calculateMoonPhases (for calendar year, returns array of phase dates and illumination percentages), calculateHebrewCalendarDates (convert Gregorian dates to Hebrew calendar), calculateSeasonalEvents (equinoxes, solstices). Store calculation results in calendar.config JSONB field or separate table (if complex). Add configuration option in calendar editor to enable/disable astronomical overlays. Write unit tests verifying calculation accuracy against known astronomical data (e.g., 2025 full moon dates).

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- Astronomical calculation requirements from Plan Section "Features"
- SunCalc and Proj4J library documentation

**Input Files:**
- `pom.xml` (add SunCalc/Proj4J dependencies)
- `src/main/java/villagecompute/calendar/model/Calendar.java`

**Target Files:**
- `src/main/java/villagecompute/calendar/services/AstronomicalService.java`
- `src/main/java/villagecompute/calendar/util/astronomical/MoonPhaseCalculator.java`
- `src/main/java/villagecompute/calendar/util/astronomical/HebrewCalendarConverter.java`
- `src/test/java/villagecompute/calendar/services/AstronomicalServiceTest.java`

**Deliverables:**
- AstronomicalService class with calculation methods
- SunCalc/Proj4J integration (Maven dependencies added)
- Moon phase and Hebrew calendar calculation logic
- Unit tests verifying calculation accuracy

**Acceptance Criteria:**
- `AstronomicalService.calculateMoonPhases(2025)` returns correct full moon dates for 2025
- `AstronomicalService.calculateHebrewCalendarDates()` converts Gregorian to Hebrew dates accurately
- Unit tests compare calculations against NASA moon phase data or Hebrew calendar tables
- Service methods handle edge cases (leap years, timezone conversions)
- Calculation performance: <100ms for full year of moon phases

**Dependencies:** `I2.T2` (requires Calendar entity and config structure)

**Parallelizable:** Yes (independent library integration)
```

### Context: PDF Generation with Astronomical Overlays (from 04_Behavior_and_Communication.md)

From line 428:
```markdown
note right of Batik
  - Generate SVG from calendar config
  - Apply astronomical overlays (moon phases)
  - Embed events with emojis
  - Add watermark if free tier
  - Convert SVG to PDF (36" x 23" @ 300 DPI)
end note
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `pom.xml` (lines 153-170)
    *   **Summary:** The Maven dependencies for astronomical libraries are **ALREADY INTEGRATED**. SunCalc (org.shredzone.commons:commons-suncalc:3.11) is at lines 153-158, and Proj4J (org.locationtech.proj4j:proj4j:1.3.0 + proj4j-epsg:1.3.0) is at lines 160-170.
    *   **Recommendation:** You DO NOT need to add these dependencies to pom.xml - they are already present. Focus on using these libraries in your implementation.

*   **File:** `src/main/java/villagecompute/calendar/services/AstronomicalCalculationService.java`
    *   **Summary:** This service **ALREADY EXISTS** with a substantial implementation. It includes:
        - `getMoonPhases(int year)` - returns `List<MoonPhaseData>` with phase transitions
        - `calculateMoonPhase(LocalDate date)` - returns `MoonPhase` enum (NEW_MOON, FULL_MOON, etc.)
        - `calculateMoonPhaseValue(LocalDate date)` - returns fractional phase (0.0 to 1.0)
        - `calculateMoonIllumination(LocalDate date)` - returns `MoonIllumination` with fraction, phase, angle
        - `calculateMoonPosition(LocalDate date, double latitude, double longitude)` - calculates azimuth, altitude, parallactic angle for hemisphere-aware rendering
        - `isMoonPhaseDay(LocalDate date)` - determines if a date is a major phase transition
        - `getHebrewDates(int year)` - placeholder that returns empty list (delegates to HebrewCalendarService)
        - `getSunriseSunset(LocalDate date, double latitude, double longitude)` - placeholder returning null times
        - Data classes: `MoonPhaseData`, `MoonPhase` enum, `MoonIllumination`, `MoonPosition`, `HebrewDateMapping`, `SunriseSunset`
    *   **Recommendation:** You MUST enhance the existing `AstronomicalCalculationService.java` file, NOT create a new one. Focus on:
        1. **Integrating the actual SunCalc library** - The current implementation uses simplified astronomical formulas. Replace the custom calculations in `calculateMoonIllumination()` and `getSunriseSunset()` with calls to `org.shredzone.commons.suncalc.SunTimes` and `org.shredzone.commons.suncalc.MoonPhase`.
        2. **Implementing `calculateSeasonalEvents()`** - This method is missing. Use SunCalc's `SunTimes` to calculate equinoxes and solstices.
        3. **Completing the `getHebrewDates()` method** - Currently returns an empty list. Integrate with HebrewCalendarService (which already exists).

*   **File:** `src/main/java/villagecompute/calendar/services/HebrewCalendarService.java`
    *   **Summary:** This service already exists with extensive Hebrew calendar functionality:
        - `isHebrewLeapYear(int year)` - checks if a Hebrew year is a leap year (19-year cycle)
        - `getMonthsInHebrewYear(int year)` - returns 12 or 13 months
        - `getDaysInHebrewMonth(int month, int year)` - handles variable month lengths
        - `getHebrewMonthName(int month, int year)` - returns transliterated Hebrew month names
        - `hebrewToGregorian(int hebrewYear, int hebrewMonth, int hebrewDay)` - simplified conversion
        - `getHebrewHolidays(int hebrewYear, String holidaySet)` - returns Map of holidays
        - `generateHebrewCalendarSVG(HebrewCalendarConfig config, String holidaySet)` - full Hebrew calendar rendering
    *   **Recommendation:** The Hebrew calendar logic is **already implemented**. You SHOULD integrate this into `AstronomicalCalculationService.getHebrewDates()` by calling `hebrewCalendarService` methods. The service is already injected at line 20 of AstronomicalCalculationService.

*   **File:** `src/main/java/villagecompute/calendar/data/models/UserCalendar.java`
    *   **Summary:** This is the main calendar entity with a `configuration` field (line 53) that is a JSONB column storing `JsonNode`. This is where astronomical overlay settings should be stored (e.g., `{"showMoonPhases": true, "showHebrewCalendar": false}`).
    *   **Recommendation:** Astronomical overlay configuration should be stored in the `calendar.configuration` JSONB field. You do NOT need to create a separate table.

*   **File:** `src/main/java/villagecompute/calendar/services/CalendarService.java`
    *   **Summary:** This service handles calendar CRUD operations and works with the `UserCalendar` entity. It supports configuration updates via the `updateCalendar()` method (line 116).
    *   **Recommendation:** The CalendarService already handles configuration management. Your astronomical calculations should read from and write to `calendar.configuration` when needed.

*   **File:** `src/main/java/villagecompute/calendar/services/CalendarRenderingService.java` (mentioned but not read - it's 69KB)
    *   **Summary:** This service likely uses AstronomicalCalculationService to render moon phases in SVG calendars. It's injected into HebrewCalendarService and used in `generateHebrewCalendarSVG()`.
    *   **Recommendation:** Your enhanced AstronomicalCalculationService will be called by CalendarRenderingService. Ensure your API is compatible with existing usage patterns.

### Implementation Tips & Notes

*   **Tip:** The task asks you to create `src/main/java/villagecompute/calendar/util/astronomical/MoonPhaseCalculator.java` and `HebrewCalendarConverter.java`, but I can see that all this logic is **already implemented directly in AstronomicalCalculationService.java and HebrewCalendarService.java**. You have two options:
    1. **Extract** the existing calculation logic into utility classes (cleaner architecture)
    2. **Keep** the logic in the services and only create wrapper utility classes if needed for testing

    Given the task specification explicitly requests these util classes, I recommend Option 1: **refactor** the calculation methods from AstronomicalCalculationService into `MoonPhaseCalculator` utility class, and delegate Hebrew calendar conversions to `HebrewCalendarConverter` that wraps HebrewCalendarService.

*   **Tip:** The SunCalc library (org.shredzone.commons:commons-suncalc:3.11) provides:
    - `SunTimes.compute()` - for sunrise/sunset/solstice/equinox calculations
    - `MoonPhase.compute()` - for precise moon phase calculations with dates
    - `MoonIllumination.compute()` - for illumination percentage
    - `MoonPosition.compute()` - for azimuth/altitude calculations

    You SHOULD use these classes instead of the simplified astronomical formulas currently in the code (which use the synodic month approximation from line 60: `double synodicMonth = 29.53059;`).

*   **Tip:** The Proj4J library is for geospatial projections (e.g., orthographic projection for moon rendering). The current code doesn't appear to use Proj4J yet. Based on the comment in pom.xml line 160 "orthographic projection for moon", this may be for advanced moon rendering. This is likely a **future enhancement** - focus first on integrating SunCalc for accurate calculations.

*   **Note:** The acceptance criteria requires comparing against "NASA moon phase data" for 2025. You can use these known full moon dates for 2025 to write your unit tests:
    - January 13, 2025
    - February 12, 2025
    - March 14, 2025
    - April 13, 2025
    - May 12, 2025
    - June 11, 2025
    - July 10, 2025
    - August 9, 2025
    - September 7, 2025
    - October 7, 2025
    - November 5, 2025
    - December 4, 2025

*   **Warning:** The HebrewCalendarService uses a **simplified** Hebrew-to-Gregorian conversion (line 156-177). The comment explicitly states this is a "very simplified approximation". For production accuracy, you may want to note in your tests that the Hebrew calendar conversions have known limitations. However, for the MVP scope of this task, the existing approximation is acceptable.

*   **Warning:** Be careful with the `@Inject` annotations. Both AstronomicalCalculationService (line 19-20) and HebrewCalendarService (line 14-15) use field injection. Follow the same pattern in your code.

*   **Note:** The task requires "Calculation performance: <100ms for full year of moon phases". The current implementation iterates through all 365 days (line 35-44). This is inefficient for just finding major phase transitions. You SHOULD optimize this by calculating only the ~13 major phases per year (new/full/quarter moons) using SunCalc's direct phase calculation, rather than iterating every day.

*   **Note:** JaCoCo code coverage is configured in pom.xml (lines 281-345) with a 70% coverage requirement for models and repositories packages. Your tests must achieve adequate coverage. The test file should be placed at `src/test/java/villagecompute/calendar/services/AstronomicalServiceTest.java` to match the existing test structure (I verified tests exist for AuthenticationService, CalendarService, EventService, OrderService, TemplateService).

### Directory Structure Note

The task specifies creating files in `src/main/java/villagecompute/calendar/util/astronomical/` but this directory does not currently exist. You will need to create it. The parent `util` directory also doesn't exist yet. This aligns with the plan's directory structure showing `util/astronomical/` at line 394 of the plan document.

### Summary of What You Need to Do

1. **Enhance AstronomicalCalculationService** - Integrate actual SunCalc library calls for accurate moon phase, illumination, and sunrise/sunset calculations
2. **Add calculateSeasonalEvents() method** - For equinoxes and solstices using SunCalc
3. **Complete getHebrewDates() method** - Integrate with existing HebrewCalendarService
4. **Create utility classes** (optional but recommended):
   - `src/main/java/villagecompute/calendar/util/astronomical/MoonPhaseCalculator.java` - Extract moon calculation logic
   - `src/main/java/villagecompute/calendar/util/astronomical/HebrewCalendarConverter.java` - Wrap Hebrew calendar conversion
5. **Write comprehensive unit tests** - Verify against NASA 2025 full moon dates, test edge cases, ensure <100ms performance
6. **Optimize performance** - Use direct phase calculations instead of daily iteration for better performance

Good luck with the implementation!

---

**END OF BRIEFING PACKAGE**
