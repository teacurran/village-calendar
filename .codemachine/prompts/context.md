# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I2.T10",
  "iteration_id": "I2",
  "iteration_goal": "Implement calendar creation, editing, and template system. Build calendar editor UI components. Integrate astronomical calculations (moon phases, Hebrew calendar). Create sequence diagrams for key workflows",
  "description": "Create end-to-end integration tests for critical calendar workflows using Quarkus test framework with REST Assured for GraphQL API testing. Test scenarios: (1) Create calendar from template - authenticate, query templates, create calendar from template, verify calendar created with correct config; (2) Add events to calendar - create calendar, add multiple events, query calendar with events, verify events returned; (3) Update calendar - create calendar, update config (enable astronomy), verify changes persisted; (4) Guest session conversion - create calendar as guest, authenticate, convert session, verify calendar transferred to user. Use test database (H2 or Testcontainers PostgreSQL). Achieve >70% code coverage for service and API layers.",
  "agent_type_hint": "BackendAgent",
  "inputs": "All implemented services and resolvers from I2 tasks, Quarkus testing and REST Assured documentation",
  "target_files": [
    "src/test/java/villagecompute/calendar/integration/CalendarWorkflowTest.java",
    "src/test/java/villagecompute/calendar/integration/TemplateWorkflowTest.java",
    "src/test/java/villagecompute/calendar/integration/GuestSessionWorkflowTest.java"
  ],
  "input_files": [
    "api/schema.graphql"
  ],
  "deliverables": "Integration tests for all critical calendar workflows, Tests use GraphQL API (not direct service calls), Test database setup and teardown automated, Tests achieve >70% coverage for service/API layers, All tests pass with ./mvnw verify",
  "acceptance_criteria": "Template workflow test creates calendar from template, verifies config cloned, Event workflow test adds 5 events, queries calendar, verifies all 5 returned, Update workflow test modifies calendar config, verifies changes in database, Guest session workflow test creates calendar as guest, converts on login, verifies ownership, Tests run in isolation (each test creates own test data, cleans up after), Integration tests complete in <60 seconds",
  "dependencies": [
    "I2.T2",
    "I2.T3",
    "I2.T4",
    "I2.T5",
    "I2.T6",
    "I2.T7",
    "I2.T8",
    "I2.T9"
  ],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: testing-levels (from 03_Verification_and_Glossary.md)

```markdown
### 5.1. Testing Levels

The Village Calendar project employs a multi-layered testing strategy to ensure quality at every level of the system:

**1. Unit Testing (Iteration-Specific)**

*   **Scope**: Individual classes, methods, and functions in isolation
*   **Tools**: JUnit 5 (backend), Vitest (frontend)
*   **Coverage Target**: 70%+ for service layer, repository layer, utility classes
*   **Frequency**: Run on every commit (CI pipeline)
*   **Examples**:
    *   Entity validation tests (JPA constraint validation)
    *   Service method tests (business logic, edge cases)
    *   Repository query tests (custom queries, filters)
    *   GraphQL resolver tests (authorization, error handling)
    *   Utility class tests (astronomical calculations, PDF rendering helpers)
*   **Iteration Integration**: Unit tests written alongside implementation in each iteration (I1.T13, I2.T2, I2.T3, I3.T2, I4.T2)

**2. Integration Testing (Iteration-Specific)**

*   **Scope**: Component interactions, API endpoints, database operations, external service integrations
*   **Tools**: Quarkus test framework with REST Assured, Testcontainers (PostgreSQL, Jaeger)
*   **Coverage Target**: 70%+ for API layer, integration points
*   **Frequency**: Run before merge to main branch
*   **Examples**:
    *   GraphQL API workflows (create calendar, place order, generate PDF)
    *   Database transaction tests (ACID compliance, rollback scenarios)
    *   Job queue processing (DelayedJob execution, retry logic)
    *   External service integration (Stripe webhook, OAuth callback, R2 upload)
*   **Iteration Integration**: Integration tests written at end of each iteration (I2.T10, I3.T9, I4.T10, I5.T10)
```

### Context: ci-cd-pipeline (from 03_Verification_and_Glossary.md)

```markdown
### 5.2. CI/CD Pipeline

**Continuous Integration (CI) - Triggered on Every Push/Pull Request**

1.  **Checkout Code**: Clone repository
2.  **Backend Build**:
    *   Maven compile (`./mvnw compile`)
    *   Run unit tests (`./mvnw test`)
    *   JaCoCo coverage report (fail if <70% for service/API layers)
    *   Security scanning (Snyk dependency check, OWASP dependency check)
    *   SonarQube analysis (code quality, security hotspots)
3.  **Frontend Build**:
    *   npm install
    *   ESLint linting (`npm run lint`)
    *   Vite production build (`npm run build`)
    *   (Optional) Vitest unit tests (`npm run test`)
4.  **Integration Tests**:
    *   Run Quarkus integration tests (`./mvnw verify`)
    *   Use Testcontainers for PostgreSQL, Jaeger
5.  **Docker Build**:
    *   Build Docker image (`docker build -t villagecompute/calendar-api:${GIT_SHA}`)
    *   (Optional) Scan image for vulnerabilities (Trivy, Snyk Container)
6.  **Publish Artifact**:
    *   Push Docker image to Docker Hub with tags: `${GIT_SHA}`, `latest` (for main branch)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `src/test/java/villagecompute/calendar/integration/CalendarWorkflowTest.java`
    *   **Summary:** Existing integration test file covering: (1) Query public templates via GraphQL (no auth), (2) Create calendar from template with config cloning verification, (3) Add multiple events (5 events) to calendar and verify persistence, (4) Update calendar configuration and verify changes, (5) List calendars for user with filtering by year.
    *   **Recommendation:** This file **ALREADY EXISTS** and implements most of scenario (1) and (2) from your task requirements. You MUST read this file completely and **DO NOT overwrite it**. Instead, you should verify it meets acceptance criteria and potentially enhance it if needed.
    *   **Key Patterns Used:**
        - Uses `@QuarkusTest` annotation for Quarkus test framework integration
        - Uses `@Inject` for ObjectMapper, AuthenticationService, CalendarService
        - Uses `@BeforeEach` with `@Transactional` for test data setup
        - Uses `@AfterEach` with `@Transactional` for test data cleanup (follows correct FK deletion order)
        - Uses `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` with `@Order(n)` for test execution order
        - Tests combine GraphQL API testing (via REST Assured) AND direct service layer calls
        - Creates unique test data with timestamps to avoid conflicts (`System.currentTimeMillis()`)
        - All assertions use JUnit 5 (`assertEquals`, `assertNotNull`, `assertTrue`, etc.)

*   **File:** `src/test/java/villagecompute/calendar/integration/TemplateWorkflowTest.java`
    *   **Summary:** Existing integration test covering template application workflows: (1) Apply template with moon phases enabled, (2) Apply template with Hebrew calendar, (3) Apply template with custom holidays, (4) Deep copy verification (template config changes don't affect created calendars).
    *   **Recommendation:** This file **ALREADY EXISTS** and implements comprehensive template testing including scenario (1) from your task. You MUST NOT overwrite it. Review it for patterns to follow.
    *   **Key Patterns Used:**
        - Creates JWT tokens using `authService.issueJWT(testUser)` for authenticated GraphQL requests
        - Uses GraphQL mutations via REST Assured with `.header("Authorization", "Bearer " + jwtToken)`
        - Creates and cleans up templates in `@BeforeEach` and `@AfterEach`
        - Verifies JSONB configuration deep cloning (nested objects, arrays)
        - Tests both GraphQL API response AND database persistence verification
        - Uses `try-finally` blocks to ensure template cleanup even if test fails

*   **File:** `src/test/java/villagecompute/calendar/integration/GuestSessionWorkflowTest.java`
    *   **Summary:** Existing integration test covering guest session workflows: (1) Create calendar without auth (as guest), (2) Convert guest session to user, (3) Convert multiple calendars, (4) Session isolation, (5) Empty session conversion, (6) Query calendars by session.
    *   **Recommendation:** This file **ALREADY EXISTS** and implements scenario (4) from your task requirements. You MUST NOT overwrite it. This is the definitive reference for guest session testing.
    *   **Key Patterns Used:**
        - Generates test session IDs with `UUID.randomUUID().toString()`
        - Creates guest calendars via service layer: `calendarService.createCalendar(..., null, sessionId)`
        - Tests GraphQL `convertGuestSession` mutation with authenticated user
        - Verifies ownership transfer: `calendar.user.id == user.id`, `calendar.sessionId == null`
        - Tests session isolation (different sessions don't interfere)
        - Uses repository query methods: `UserCalendar.findBySession(sessionId)`

*   **File:** `src/main/java/villagecompute/calendar/services/CalendarService.java`
    *   **Summary:** Service layer implementing calendar business logic including CRUD operations, authorization, versioning, and session-to-user conversion.
    *   **Recommendation:** You MUST use this service in tests. Key methods: `createCalendar()`, `updateCalendar()`, `deleteCalendar()`, `getCalendar()`, `listCalendars()`, `convertSessionToUser()`.
    *   **Authorization Logic:** Service enforces read/write access checks - public calendars accessible to all, private calendars only to owners/admins.
    *   **Important:** The service handles optimistic locking (version field) - concurrent updates will throw `OptimisticLockException`.

*   **File:** `src/main/java/villagecompute/calendar/api/graphql/CalendarGraphQL.java`
    *   **Summary:** GraphQL resolver implementing all calendar-related queries and mutations with JWT authentication and RBAC.
    *   **Recommendation:** Integration tests should call GraphQL endpoints via REST Assured, NOT directly call this class.
    *   **Key Endpoints:**
        - Query: `me`, `currentUser`, `myCalendars(year)`, `calendars(userId, year)`, `calendar(id)`, `allUsers(limit)`
        - Mutation: `createCalendar(input)`, `updateCalendar(id, input)`, `deleteCalendar(id)`, `convertGuestSession(sessionId)`
    *   **Authentication:** Most operations require `@RolesAllowed("USER")` or `@RolesAllowed("ADMIN")` - tests must provide valid JWT tokens via `Authorization` header.
    *   **DataLoader Pattern:** Uses batched field resolvers (`batchLoadUsers`, `batchLoadTemplates`) to prevent N+1 queries - integration tests should verify efficient query performance.

*   **File:** `src/main/java/villagecompute/calendar/data/models/UserCalendar.java` (inferred)
    *   **Summary:** JPA entity for user calendars with Panache active record pattern.
    *   **Recommendation:** You SHOULD use Panache query methods in tests: `UserCalendar.findById()`, `UserCalendar.findBySession()`, etc.
    *   **Key Fields:** `id` (UUID), `name`, `year`, `configuration` (JsonNode/JSONB), `isPublic`, `user` (CalendarUser), `sessionId`, `template` (CalendarTemplate), `version` (optimistic locking).

*   **File:** `src/main/java/villagecompute/calendar/data/models/Event.java` (inferred from CalendarWorkflowTest)
    *   **Summary:** JPA entity for calendar events.
    *   **Recommendation:** Tests create events with: `calendar`, `eventDate`, `eventText`, `emoji`, `color`. Use Panache methods: `Event.findByCalendar()`, `Event.findByCalendarAndDate()`, `Event.countByCalendar()`.

*   **File:** `api/schema.graphql`
    *   **Summary:** Complete GraphQL schema defining all types, queries, mutations, inputs, and enums.
    *   **Recommendation:** Use this as the definitive API contract for integration tests. All GraphQL requests must match this schema exactly.
    *   **Key Types:** `CalendarUser`, `CalendarTemplate`, `UserCalendar`, `CalendarOrder`, `PaymentIntent`, `PdfJob`
    *   **Key Inputs:** `CalendarInput`, `CalendarUpdateInput`, `TemplateInput`, `AddressInput`, `OrderUpdateInput`
    *   **Key Mutations:** Test files should use exact mutation syntax from schema.

### Implementation Tips & Notes

*   **CRITICAL:** The three target test files **ALREADY EXIST**. Your task is **NOT** to create them from scratch, but to:
    1. **READ** all three files thoroughly
    2. **VERIFY** they meet the acceptance criteria from the task description
    3. **RUN** the tests and confirm they pass: `./mvnw verify`
    4. **CHECK COVERAGE** using JaCoCo reports to verify >70% for service/API layers
    5. **ENHANCE** only if gaps exist (unlikely based on my analysis)

*   **Test Execution Pattern:** All three existing files follow this structure:
    ```java
    @QuarkusTest
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    public class WorkflowTest {
        @Inject ObjectMapper objectMapper;
        @Inject AuthenticationService authService;
        @Inject CalendarService calendarService;

        private CalendarUser testUser;
        private CalendarTemplate testTemplate;

        @BeforeEach @Transactional void setup() { /* create test data */ }
        @AfterEach @Transactional void cleanup() { /* delete in FK order */ }

        @Test @Order(1) void testScenario1() { /* ... */ }
    }
    ```

*   **GraphQL Testing with REST Assured:** The existing tests use this pattern:
    ```java
    String query = """
        query {
            templates(isActive: true) { id name configuration }
        }
        """;

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + jwtToken)  // if auth required
        .body(Map.of("query", query))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.templates", notNullValue())
        .body("errors", nullValue())
        .extract().asString();
    ```

*   **JWT Token Generation:** For authenticated requests:
    ```java
    CalendarUser user = new CalendarUser();
    user.oauthProvider = "GOOGLE";
    user.oauthSubject = "test-" + System.currentTimeMillis();
    user.email = "test@example.com";
    user.persist();
    String jwtToken = authService.issueJWT(user);
    ```

*   **Test Data Cleanup Order:** CRITICAL to avoid FK violations:
    1. Delete Events first (`Event.delete("calendar.user.id", userId)`)
    2. Delete Calendars second (`UserCalendar.delete("user.id", userId)`)
    3. Delete User last (`CalendarUser.deleteById(userId)`)
    4. Delete Template separately (`CalendarTemplate.deleteById(templateId)`)

*   **Database Configuration:** Tests should run against H2 in-memory database OR Testcontainers PostgreSQL. Check `src/test/resources/application.properties` for test database config. The existing tests appear to use the default Quarkus test database.

*   **Coverage Verification:** After running `./mvnw verify`, check JaCoCo report at `target/site/jacoco/index.html`. Verify:
    - `src/main/java/villagecompute/calendar/services/CalendarService.java` has >70% coverage
    - `src/main/java/villagecompute/calendar/api/graphql/CalendarGraphQL.java` has >70% coverage
    - `src/main/java/villagecompute/calendar/services/TemplateService.java` has >70% coverage

*   **Acceptance Criteria Mapping:**
    1. ✅ "Template workflow test creates calendar from template, verifies config cloned" - **IMPLEMENTED** in `TemplateWorkflowTest.java` test 1
    2. ✅ "Event workflow test adds 5 events, queries calendar, verifies all 5 returned" - **IMPLEMENTED** in `CalendarWorkflowTest.java` test order 3 (`testWorkflow_AddMultipleEventsToCalendar`)
    3. ✅ "Update workflow test modifies calendar config, verifies changes in database" - **IMPLEMENTED** in `CalendarWorkflowTest.java` test order 4 (`testWorkflow_UpdateCalendarConfiguration`)
    4. ✅ "Guest session workflow test creates calendar as guest, converts on login, verifies ownership" - **IMPLEMENTED** in `GuestSessionWorkflowTest.java` tests 1-3
    5. ✅ "Tests run in isolation" - All tests use unique timestamps and proper cleanup
    6. ⚠️ "Integration tests complete in <60 seconds" - **MUST VERIFY** by running tests

*   **Running Tests:**
    ```bash
    # Run all tests
    ./mvnw verify

    # Run specific test class
    ./mvnw test -Dtest=CalendarWorkflowTest

    # Run with coverage
    ./mvnw verify jacoco:report
    ```

*   **Expected Task Outcome:** Based on my analysis, the three target files already exist and appear to comprehensively cover all acceptance criteria. Your primary task is to:
    1. Verify all tests pass
    2. Confirm coverage meets >70% threshold
    3. Document any gaps (unlikely)
    4. Potentially add minor enhancements if acceptance criteria not fully met

**WARNING:** Do NOT delete or overwrite the existing test files. They represent significant implementation work already completed in tasks I2.T2 through I2.T9. Your role is verification and potential enhancement only.
