# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

**Task I2.T10: Write Integration Tests for Calendar Workflows**

Create end-to-end integration tests for critical calendar workflows using Quarkus test framework with REST Assured for GraphQL API testing. Test scenarios: (1) Create calendar from template - authenticate, query templates, create calendar from template, verify calendar created with correct config; (2) Add events to calendar - create calendar, add multiple events, query calendar with events, verify events returned; (3) Update calendar - create calendar, update config (enable astronomy), verify changes persisted; (4) Guest session conversion - create calendar as guest, authenticate, convert session, verify calendar transferred to user. Use test database (H2 or Testcontainers PostgreSQL). Achieve >70% code coverage for service and API layers.

**Target Files:**
- `src/test/java/villagecompute/calendar/integration/CalendarWorkflowTest.java` (NEW - must be created)
- `src/test/java/villagecompute/calendar/integration/TemplateWorkflowTest.java` (NEW - must be created)
- `src/test/java/villagecompute/calendar/integration/GuestSessionWorkflowTest.java` (NEW - must be created)

**Acceptance Criteria:**
1. Template workflow test creates calendar from template, verifies config cloned
2. Event workflow test adds 5 events, queries calendar, verifies all 5 returned
3. Update workflow test modifies calendar config, verifies changes in database
4. Guest session workflow test creates calendar as guest, converts on login, verifies ownership
5. Tests run in isolation (each test creates own test data, cleans up after)
6. Integration tests complete in <60 seconds
7. Achieve >70% code coverage for service and API layers

---

## Issues Detected

### **Critical Issue #1: Missing Target Files**
*   **File Missing:** `src/test/java/villagecompute/calendar/integration/CalendarWorkflowTest.java` - The file does NOT exist.
*   **File Missing:** `src/test/java/villagecompute/calendar/integration/TemplateWorkflowTest.java` - The file does NOT exist.
*   **File Missing:** `src/test/java/villagecompute/calendar/integration/GuestSessionWorkflowTest.java` - The file does NOT exist.
*   **Impact:** The task explicitly requires creating these three new integration test files. NONE of them were created.

### **Critical Issue #2: Code Coverage Below Target**
*   **Coverage Issue:** Services layer (`villagecompute.calendar.services`) has only **49% coverage** (target: >70%).
*   **Coverage Issue:** API GraphQL layer (`villagecompute.calendar.api.graphql`) has only **17% coverage** (target: >70%).
*   **Coverage Issue:** API REST layer (`villagecompute.calendar.api.rest`) has **0% coverage**.
*   **Impact:** The task requires >70% coverage for service and API layers. Current coverage is significantly below target.

### **Critical Issue #3: Acceptance Criteria Not Met**
*   **Missing:** No test exists that "creates calendar from template and verifies config cloned" using GraphQL API.
*   **Missing:** No test exists that "adds 5 events, queries calendar, verifies all 5 returned" using GraphQL API.
*   **Missing:** No test exists that "modifies calendar config via GraphQL and verifies changes in database".
*   **Partial:** Guest session conversion is tested in `SessionServiceTest.java` (unit test) and `AuthenticationIntegrationTest.java`, but there is NO dedicated end-to-end GraphQL workflow test in `GuestSessionWorkflowTest.java` as required.

### **Issue #4: Linting Errors**
*   **Linting Error:** There are 3057 Checkstyle violations in the codebase (unrelated to this task, but build fails on `mvn checkstyle:check`).
*   **Note:** While these linting errors are pre-existing and not caused by this task, they indicate code quality issues that should be addressed separately.

---

## Best Approach to Fix

You MUST create the three missing integration test files as specified in the task description. Follow these instructions precisely:

### Step 1: Create `src/test/java/villagecompute/calendar/integration/CalendarWorkflowTest.java`

This file must contain end-to-end integration tests for calendar workflows using the **GraphQL API** (not direct service calls).

**Required Tests:**
1. **Test: Add Multiple Events to Calendar**
   - Create an authenticated user and JWT token
   - Create a calendar via GraphQL `createCalendar` mutation
   - Add 5 events via GraphQL `addEvent` mutation (or similar mutation from your schema)
   - Query the calendar with events via GraphQL query
   - Verify all 5 events are returned with correct data
   - Clean up test data in `@AfterEach`

2. **Test: Update Calendar Configuration**
   - Create an authenticated user and JWT token
   - Create a calendar via GraphQL mutation
   - Update the calendar config via GraphQL `updateCalendar` mutation (e.g., enable moon phases)
   - Query the calendar to verify config changes are reflected
   - Query the database directly (using Panache) to verify persistence
   - Clean up test data in `@AfterEach`

3. **Test: Create Calendar from Template (End-to-End)**
   - Create a test template with specific configuration
   - Create an authenticated user and JWT token
   - Query templates via GraphQL to find the test template
   - Create a calendar from the template via GraphQL `createCalendar` mutation with `templateId`
   - Query the created calendar
   - Verify the calendar config matches the template config (all JSONB fields cloned)
   - Clean up test data in `@AfterEach`

**Implementation Pattern:**
```java
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CalendarWorkflowTest {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AuthenticationService authService;

    private CalendarUser testUser;
    private CalendarTemplate testTemplate;
    private String jwtToken;

    @BeforeEach
    @Transactional
    void setup() {
        // Create test user
        testUser = new CalendarUser();
        testUser.oauthProvider = "GOOGLE";
        testUser.oauthSubject = "workflow-test-" + System.currentTimeMillis();
        testUser.email = "workflow-test@example.com";
        testUser.displayName = "Workflow Test User";
        testUser.persist();

        // Generate JWT token
        jwtToken = authService.issueJWT(testUser);

        // Create test template
        testTemplate = new CalendarTemplate();
        testTemplate.name = "Workflow Test Template";
        testTemplate.isActive = true;
        testTemplate.configuration = createTestConfiguration();
        testTemplate.persist();
    }

    @AfterEach
    @Transactional
    void cleanup() {
        // Clean up in correct order due to foreign keys
        if (testUser != null && testUser.id != null) {
            UserCalendar.delete("user.id", testUser.id);
            CalendarUser.deleteById(testUser.id);
        }
        if (testTemplate != null && testTemplate.id != null) {
            CalendarTemplate.deleteById(testTemplate.id);
        }
    }

    @Test
    @Order(1)
    void testWorkflow_AddMultipleEventsToCalendar() {
        // TODO: Implement this test following the pattern above
        // Use REST Assured to make GraphQL requests to /graphql endpoint
        // Example:
        // given()
        //     .contentType(ContentType.JSON)
        //     .header("Authorization", "Bearer " + jwtToken)
        //     .body(Map.of("query", mutation))
        //     .when()
        //     .post("/graphql")
        //     .then()
        //     .statusCode(200)
        //     .body("data.createCalendar.id", notNullValue());
    }

    // TODO: Implement remaining tests
}
```

### Step 2: Create `src/test/java/villagecompute/calendar/integration/TemplateWorkflowTest.java`

This file should contain integration tests focused on template application workflows and configuration cloning edge cases.

**Required Tests:**
1. **Test: Apply Template with Moon Phases Enabled**
   - Create template with moon phases configuration enabled
   - Create calendar from template via GraphQL
   - Verify calendar inherits moon phases setting in config

2. **Test: Apply Template with Hebrew Calendar Enabled**
   - Create template with Hebrew calendar configuration enabled
   - Create calendar from template via GraphQL
   - Verify calendar inherits Hebrew calendar setting in config

3. **Test: Apply Template with Custom Holiday Configuration**
   - Create template with custom holiday configuration
   - Create calendar from template via GraphQL
   - Verify all holiday config fields are cloned correctly

**Note:** These tests should focus on verifying that JSONB configuration fields are properly deep-copied during template application.

### Step 3: Create `src/test/java/villagecompute/calendar/integration/GuestSessionWorkflowTest.java`

This file must contain end-to-end integration tests for guest session workflows using the **GraphQL API**.

**Required Tests:**
1. **Test: Guest Creates Calendar Without Authentication**
   - Generate a test sessionId (UUID.randomUUID())
   - Create a calendar via GraphQL with sessionId parameter (NO auth token)
   - Verify calendar persisted with sessionId
   - Verify calendar user is null
   - Clean up test data

2. **Test: Convert Guest Session to User Account**
   - Generate a test sessionId
   - Create a calendar via GraphQL with sessionId (NO auth token)
   - Create a test user
   - Call `convertGuestSession` mutation with authentication
   - Verify calendar ownership transferred to user
   - Verify sessionId is cleared
   - Verify calendar user is set to testUser
   - Clean up test data

3. **Test: Convert Multiple Calendars from Guest Session**
   - Generate a test sessionId
   - Create 3 calendars via GraphQL with same sessionId (NO auth token)
   - Create a test user
   - Call `convertGuestSession` mutation
   - Verify all 3 calendars transferred to user account
   - Verify all 3 calendars have sessionId cleared
   - Clean up test data

### Step 4: Verification Steps

After creating the three test files:

1. **Run Tests:**
   ```bash
   ./mvnw verify
   ```
   - All tests must pass
   - Execution time must be <60 seconds

2. **Check Coverage:**
   ```bash
   ./mvnw jacoco:report
   ```
   - Open `target/site/jacoco/index.html`
   - Verify `villagecompute.calendar.services` has >70% coverage
   - Verify `villagecompute.calendar.api.graphql` has >70% coverage

3. **Verify All Acceptance Criteria Met:**
   - Template workflow test creates calendar from template, verifies config cloned ✓
   - Event workflow test adds 5 events, queries calendar, verifies all 5 returned ✓
   - Update workflow test modifies calendar config, verifies changes in database ✓
   - Guest session workflow test creates calendar as guest, converts on login, verifies ownership ✓
   - Tests run in isolation (each test creates own test data, cleans up after) ✓
   - Integration tests complete in <60 seconds ✓

---

## Additional Context

### Existing Test Infrastructure You Can Reference

1. **CalendarServiceIntegrationTest.java** - Shows how to:
   - Create test users and templates
   - Use `@BeforeEach` and `@AfterEach` for setup/cleanup
   - Mock external services (StorageService)

2. **CalendarGraphQLTest.java** - Shows how to:
   - Make GraphQL requests using REST Assured
   - Test GraphQL queries and mutations
   - Validate response structure

3. **SessionServiceTest.java** - Shows how to:
   - Test guest session conversion logic (unit test level)
   - Create test data for sessions

### GraphQL API Endpoint

All GraphQL requests should POST to `/graphql` with:
```java
given()
    .contentType(ContentType.JSON)
    .header("Authorization", "Bearer " + jwtToken)  // Only for authenticated requests
    .body(Map.of("query", graphqlQueryString))
    .when()
    .post("/graphql")
    .then()
    .statusCode(200)
    .body("data.fieldName", notNullValue())
    .body("errors", nullValue());
```

### Key Points

- **DO NOT** modify existing test files - create NEW files only
- **USE** GraphQL API for all tests (not direct service calls)
- **FOLLOW** the existing test patterns from `CalendarGraphQLTest.java` and `CalendarServiceIntegrationTest.java`
- **ENSURE** each test has proper setup (`@BeforeEach`) and cleanup (`@AfterEach`)
- **VERIFY** tests increase code coverage to >70% for service and API layers
- **CONFIRM** all tests pass and complete in <60 seconds

---

## Success Criteria

The task will be considered complete when:

1. ✅ File `src/test/java/villagecompute/calendar/integration/CalendarWorkflowTest.java` exists and contains required tests
2. ✅ File `src/test/java/villagecompute/calendar/integration/TemplateWorkflowTest.java` exists and contains required tests
3. ✅ File `src/test/java/villagecompute/calendar/integration/GuestSessionWorkflowTest.java` exists and contains required tests
4. ✅ All tests pass with `./mvnw verify`
5. ✅ Tests complete in <60 seconds
6. ✅ Code coverage for `villagecompute.calendar.services` is >70%
7. ✅ Code coverage for `villagecompute.calendar.api.graphql` is >70%
8. ✅ All acceptance criteria from the original task description are met
