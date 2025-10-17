# Task Briefing Package: I2.T10 - Integration Tests for Calendar Workflows

**Generated:** 2025-10-17
**Target Task:** I2.T10
**Status:** Ready for Implementation
**Priority:** High (blocks I2 completion)

---

## 1. TASK SPECIFICATION

### Task I2.T10: Write Integration Tests for Calendar Workflows

**Task ID:** `I2.T10`

**Iteration:** I2 - Core Calendar Functionality & User Features

**Description:**
Create end-to-end integration tests for critical calendar workflows using Quarkus test framework with REST Assured for GraphQL API testing. Test scenarios: (1) Create calendar from template - authenticate, query templates, create calendar from template, verify calendar created with correct config; (2) Add events to calendar - create calendar, add multiple events, query calendar with events, verify events returned; (3) Update calendar - create calendar, update config (enable astronomy), verify changes persisted; (4) Guest session conversion - create calendar as guest, authenticate, convert session, verify calendar transferred to user. Use test database (H2 or Testcontainers PostgreSQL). Achieve >70% code coverage for service and API layers.

**Agent Type Hint:** `BackendAgent`

**Target Files:**
- `src/test/java/villagecompute/calendar/integration/CalendarWorkflowTest.java` (NEW - does not exist)
- `src/test/java/villagecompute/calendar/integration/TemplateWorkflowTest.java` (NEW - does not exist)
- `src/test/java/villagecompute/calendar/integration/GuestSessionWorkflowTest.java` (NEW - does not exist)

**Deliverables:**
- Integration tests for all critical calendar workflows
- Tests use GraphQL API (not direct service calls)
- Test database setup and teardown automated
- Tests achieve >70% coverage for service/API layers
- All tests pass with `./mvnw verify`

**Acceptance Criteria:**
- Template workflow test creates calendar from template, verifies config cloned
- Event workflow test adds 5 events, queries calendar, verifies all 5 returned
- Update workflow test modifies calendar config, verifies changes in database
- Guest session workflow test creates calendar as guest, converts on login, verifies ownership
- Tests run in isolation (each test creates own test data, cleans up after)
- Integration tests complete in <60 seconds

**Dependencies:** All I2 tasks (I2.T1 through I2.T9 - all complete)

**Parallelizable:** No (final integration testing task)

---

## 2. ARCHITECTURAL CONTEXT

### From: Plan Section 5.1 - Testing Levels

**Integration Testing Strategy:**

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

**CI/CD Integration Tests:**
1.  **Run Quarkus integration tests** (`./mvnw verify`)
2.  **Use Testcontainers** for PostgreSQL, Jaeger
3.  **JaCoCo coverage report** (fail if <70% for service/API layers)

**Code Quality Gates:**
- **Unit Test Pass**: All unit tests must pass (0% failure tolerance)
- **Code Coverage**: Minimum 70% line coverage for service layer, API layer, repository layer (JaCoCo enforcement)
- **Integration Test Pass**: All integration tests must pass

### From: Plan Section 5.4 - Artifact Validation

**GraphQL Schema Testing:**
*   **Syntax Validation**: Schema must parse without errors
    *   Tool: GraphQL schema validator (`graphql-schema-linter` or SmallRye GraphQL compile-time check)
    *   Frequency: On commit (CI pipeline)
*   **Type Generation**: TypeScript types auto-generated from schema for frontend
    *   Tool: GraphQL Code Generator
    *   Frequency: On schema change (pre-commit hook or CI job)

---

## 3. CODEBASE ANALYSIS

### 3.1 Existing Test Infrastructure

**Directory Structure:**
```
src/test/java/villagecompute/calendar/
├── integration/
│   ├── AuthenticationIntegrationTest.java         (EXISTS - 416 lines)
│   └── CalendarServiceIntegrationTest.java        (EXISTS - 554 lines)
├── api/graphql/
│   └── CalendarGraphQLTest.java                   (EXISTS - 842 lines)
├── services/
│   ├── SessionServiceTest.java                    (EXISTS - 373 lines)
│   ├── TemplateServiceTest.java                   (EXISTS)
│   └── OrderServiceTest.java                      (EXISTS)
└── data/
    ├── models/                                    (Multiple entity tests)
    └── repositories/                              (Multiple repository tests)
```

### 3.2 Existing Integration Test Examples

**File:** `src/test/java/villagecompute/calendar/integration/CalendarServiceIntegrationTest.java`

**Purpose:** Tests calendar generation service, template queries, storage integration

**Key Patterns:**
```java
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CalendarServiceIntegrationTest {

    @Inject
    CalendarGenerationService calendarGenerationService;

    @Inject
    ObjectMapper objectMapper;

    @InjectMock
    StorageService storageService;  // Mock R2 storage to avoid real credentials

    private CalendarUser testUser;
    private CalendarTemplate testTemplate;

    @BeforeEach
    @Transactional
    void setup() {
        // Create test user and template
        testUser = new CalendarUser();
        testUser.oauthProvider = "GOOGLE";
        testUser.oauthSubject = "calendar-int-test-" + System.currentTimeMillis();
        testUser.email = "calendar-integration@example.com-" + System.currentTimeMillis();
        testUser.displayName = "Calendar Integration Test User";
        testUser.persist();

        testTemplate = new CalendarTemplate();
        testTemplate.name = "Integration Test Template " + System.currentTimeMillis();
        testTemplate.description = "Template for integration testing";
        testTemplate.isActive = true;
        testTemplate.isFeatured = false;
        testTemplate.displayOrder = 1;
        testTemplate.configuration = createTestConfiguration();
        testTemplate.persist();

        // Mock storage service
        String mockPublicUrl = "https://r2.example.com/calendars/test-calendar.pdf";
        when(storageService.uploadFile(anyString(), any(byte[].class), anyString()))
            .thenReturn(mockPublicUrl);
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
}
```

**File:** `src/test/java/villagecompute/calendar/api/graphql/CalendarGraphQLTest.java`

**Purpose:** Tests GraphQL API queries/mutations, authorization, DataLoader pattern

**Key Patterns:**
```java
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CalendarGraphQLTest {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AuthenticationService authService;

    private CalendarUser testUser;
    private CalendarTemplate testTemplate;
    private static final String TEST_EMAIL = "graphql-test@example.com";

    @Test
    @Order(1)
    void testQuery_Templates_Public() {
        String query = """
            query {
                templates(isActive: true) {
                    id
                    name
                    description
                    isActive
                    isFeatured
                }
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("query", query))
            .when()
            .post("/graphql")
            .then()
            .statusCode(200)
            .body("data.templates", notNullValue())
            .body("data.templates", hasSize(greaterThanOrEqualTo(1)))
            .body("data.templates[0].name", notNullValue())
            .body("errors", nullValue());
    }

    @Test
    @Order(70)
    void testDataLoader_BatchLoading_MultipleCalendars() {
        // Create 10 public calendars for testing
        java.util.List<UUID> calendarIds = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            UUID id = createAndPersistPublicCalendar("Calendar " + i);
            calendarIds.add(id);
        }

        try {
            // Query all 10 calendars with their users and templates
            // This should trigger DataLoader batching
            String query = String.format("""
                query {
                    calendar1: calendar(id: "%s") {
                        id
                        name
                        user { id email }
                        template { id name }
                    }
                    calendar2: calendar(id: "%s") {
                        id
                        name
                        user { id email }
                        template { id name }
                    }
                    // ... (10 total)
                }
                """, calendarIds.get(0).toString(), calendarIds.get(1).toString());

            given()
                .contentType(ContentType.JSON)
                .body(Map.of("query", query))
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.calendar1.id", equalTo(calendarIds.get(0).toString()))
                .body("data.calendar1.user.email", equalTo(TEST_EMAIL))
                .body("data.calendar10.template.name", equalTo(testTemplate.name))
                .body("errors", nullValue());
        } finally {
            for (UUID calendarId : calendarIds) {
                deleteTestCalendar(calendarId);
            }
        }
    }
}
```

### 3.3 Guest Session Test Infrastructure

**File:** `src/test/java/villagecompute/calendar/services/SessionServiceTest.java`

**Purpose:** Unit tests for SessionService (guest session management)

**Coverage:** 30+ test cases, >80% coverage

**Key Test Scenarios:**
```java
@Test
@Transactional
void testConvertSessionToUser_Success() {
    // Given
    calendarService.createCalendar("Guest Cal 1", 2025, null, null, true, null, testSessionId);
    calendarService.createCalendar("Guest Cal 2", 2024, null, null, true, null, testSessionId);

    // When
    int convertedCount = sessionService.convertSessionToUser(testSessionId, testUser);

    // Then
    assertEquals(2, convertedCount);

    // Verify calendars are now owned by user
    List<UserCalendar> calendars = sessionService.getSessionCalendars(testSessionId);
    assertEquals(0, calendars.size()); // No longer in session

    // Verify calendars are in user's account
    List<UserCalendar> userCalendars = UserCalendar.findByUser(testUser.id).list();
    assertEquals(2, userCalendars.size());
    assertTrue(userCalendars.stream().allMatch(c -> c.user.id.equals(testUser.id)));
    assertTrue(userCalendars.stream().allMatch(c -> c.sessionId == null));
}
```

### 3.4 Authentication Test Infrastructure

**File:** `src/test/java/villagecompute/calendar/integration/AuthenticationIntegrationTest.java`

**Purpose:** Tests OAuth callback, JWT issuance, guest session conversion

**Key Note:** Uses TEST_OIDC_PROVIDER environment variable for testing

**Relevant Test:**
```java
@Test
@Transactional
void testOAuthCallback_WithSessionId() {
    // Given: Mock OIDC security identity
    UserInfo mockUserInfo = Mockito.mock(UserInfo.class);
    when(mockUserInfo.getString("email")).thenReturn("test@example.com");
    when(mockUserInfo.getString("name")).thenReturn("Test User");
    // ... rest of test
}
```

---

## 4. IMPLEMENTATION GUIDANCE

### 4.1 Task Status: PARTIALLY COMPLETE

**Situation:** The target files specified in the task do NOT exist:
- `CalendarWorkflowTest.java` - **DOES NOT EXIST**
- `TemplateWorkflowTest.java` - **DOES NOT EXIST**
- `GuestSessionWorkflowTest.java` - **DOES NOT EXIST**

**However:** Comprehensive integration tests ALREADY EXIST that cover the required workflows:
- `CalendarServiceIntegrationTest.java` - Covers template application, calendar generation (554 lines, 30+ tests)
- `CalendarGraphQLTest.java` - Covers GraphQL queries/mutations, DataLoader, authorization (842 lines, 40+ tests)
- `SessionServiceTest.java` - Covers guest session conversion (373 lines, 30+ tests)
- `AuthenticationIntegrationTest.java` - Covers OAuth and session conversion (416 lines)

### 4.2 Acceptance Criteria Analysis

Let me verify each acceptance criterion against existing tests:

**Criterion 1:** "Template workflow test creates calendar from template, verifies config cloned"
- **Status:** ✅ COVERED by `CalendarServiceIntegrationTest.java`
- **Tests:**
  - `testTemplateQuery_ReturnsActiveTemplates()` - Verifies template listing
  - `testTemplateQuery_ById()` - Verifies template retrieval
  - `testCalendarGeneration_WithCustomConfiguration()` - Verifies config cloning

**Criterion 2:** "Event workflow test adds 5 events, queries calendar, verifies all 5 returned"
- **Status:** ⚠️ PARTIAL - Event addition via GraphQL not explicitly tested
- **Gap:** No test for adding multiple events and verifying via GraphQL query
- **Existing:** `CalendarGraphQLTest.testFieldResolver_CalendarWithEvents()` queries events but doesn't test adding them

**Criterion 3:** "Update workflow test modifies calendar config, verifies changes in database"
- **Status:** ⚠️ PARTIAL - Update mutation tested for authorization, but not full workflow
- **Gap:** No test that creates calendar, updates via GraphQL mutation, then verifies persistence

**Criterion 4:** "Guest session workflow test creates calendar as guest, converts on login, verifies ownership"
- **Status:** ✅ COVERED by `SessionServiceTest.java` and `AuthenticationIntegrationTest.java`
- **Tests:**
  - `SessionServiceTest.testConvertSessionToUser_Success()` - Unit test for conversion
  - `AuthenticationIntegrationTest.testOAuthCallback_WithSessionId()` - Integration test

**Criterion 5:** "Tests run in isolation (each test creates own test data, cleans up after)"
- **Status:** ✅ COVERED - All existing tests use `@BeforeEach` setup and `@AfterEach` cleanup

**Criterion 6:** "Integration tests complete in <60 seconds"
- **Status:** ✅ LIKELY MET - Existing tests use in-memory H2 or fast Testcontainers

### 4.3 Implementation Strategy

**RECOMMENDED APPROACH:** Create the three specified test files, but focus on **filling gaps** rather than duplicating existing coverage.

**File 1: CalendarWorkflowTest.java**
```java
package villagecompute.calendar.integration;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;
import villagecompute.calendar.data.models.*;
import villagecompute.calendar.services.AuthenticationService;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for calendar workflows.
 *
 * Tests scenarios:
 * 1. Create calendar from template
 * 2. Add multiple events to calendar
 * 3. Update calendar configuration
 * 4. Query calendar with all relationships
 *
 * NOTE: This test suite focuses on GraphQL API workflows that are not fully
 * covered by CalendarServiceIntegrationTest.java or CalendarGraphQLTest.java.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CalendarWorkflowTest {

    // Test focuses on:
    // - GraphQL mutation for adding events
    // - GraphQL mutation for updating calendar config
    // - End-to-end verification of persistence

    @Test
    @Order(1)
    @Transactional
    void testWorkflow_AddMultipleEventsToCalendar() {
        // Create calendar, add 5 events via GraphQL, query to verify
        // This fills the gap identified in acceptance criteria #2
    }

    @Test
    @Order(2)
    @Transactional
    void testWorkflow_UpdateCalendarConfiguration() {
        // Create calendar, update config via GraphQL, verify persistence
        // This fills the gap identified in acceptance criteria #3
    }

    @Test
    @Order(3)
    @Transactional
    void testWorkflow_CreateCalendarFromTemplate_EndToEnd() {
        // Query templates, create calendar from template, verify all config cloned
        // This provides explicit end-to-end coverage for acceptance criteria #1
    }
}
```

**File 2: TemplateWorkflowTest.java**
```java
package villagecompute.calendar.integration;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.*;

/**
 * Integration tests for template-related workflows.
 *
 * Tests scenarios:
 * 1. Query active templates
 * 2. Create calendar from template
 * 3. Verify template configuration is cloned correctly
 * 4. Test template with various configuration options
 *
 * NOTE: Basic template query tests already exist in CalendarGraphQLTest.java.
 * This suite focuses on template application workflows and config cloning edge cases.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TemplateWorkflowTest {

    @Test
    @Order(1)
    @Transactional
    void testTemplateWorkflow_ApplyTemplateWithMoonPhases() {
        // Test template with moon phases enabled, verify calendar inherits setting
    }

    @Test
    @Order(2)
    @Transactional
    void testTemplateWorkflow_ApplyTemplateWithHebrewCalendar() {
        // Test template with Hebrew calendar enabled, verify calendar inherits setting
    }

    @Test
    @Order(3)
    @Transactional
    void testTemplateWorkflow_ApplyTemplateWithCustomHolidays() {
        // Test template with custom holiday configuration, verify cloning
    }
}
```

**File 3: GuestSessionWorkflowTest.java**
```java
package villagecompute.calendar.integration;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.*;

/**
 * End-to-end integration tests for guest session workflows.
 *
 * Tests scenarios:
 * 1. Guest creates calendar (no authentication)
 * 2. Calendar persisted with sessionId
 * 3. User authenticates via OAuth
 * 4. Guest session converted to user account
 * 5. Calendars now owned by authenticated user
 *
 * NOTE: Unit tests for SessionService already exist in SessionServiceTest.java.
 * This suite provides end-to-end GraphQL API workflow coverage.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GuestSessionWorkflowTest {

    @Test
    @Order(1)
    @Transactional
    void testGuestSessionWorkflow_CreateCalendarAsGuest() {
        // Create calendar with sessionId via GraphQL (no auth token)
        // Verify calendar persisted with sessionId
    }

    @Test
    @Order(2)
    @Transactional
    void testGuestSessionWorkflow_ConvertSessionToUser() {
        // Create calendar with sessionId
        // Simulate OAuth login (create user)
        // Call convertGuestSession mutation
        // Verify calendar ownership transferred
        // Verify sessionId cleared
    }

    @Test
    @Order(3)
    @Transactional
    void testGuestSessionWorkflow_MultipleCalendarsConversion() {
        // Create 3 calendars with same sessionId
        // Convert session to user
        // Verify all 3 calendars transferred
    }
}
```

### 4.4 Key Implementation Notes

**Authentication Challenge:**
- Many tests require JWT tokens for authenticated mutations
- Existing tests use `@InjectMock` for SecurityIdentity or skip authenticated tests
- **Solution:** Follow `AuthenticationIntegrationTest.java` pattern:
  - Use `TEST_OIDC_PROVIDER` environment variable for test setup
  - Or create JWT tokens using `AuthenticationService.issueJWT(testUser)`
  - Or focus on guest/public workflows and document authenticated workflows separately

**Database Strategy:**
- Use Quarkus default test database (H2 in-memory)
- Tests marked `@Transactional` for automatic rollback
- Explicit cleanup in `@AfterEach` for cross-test data isolation

**GraphQL Testing Pattern:**
```java
String mutation = """
    mutation {
        createCalendar(input: {
            name: "Test Calendar"
            year: 2025
            templateId: "%s"
        }) {
            id
            name
            configuration
        }
    }
    """.formatted(testTemplate.id.toString());

given()
    .contentType(ContentType.JSON)
    .header("Authorization", "Bearer " + jwtToken)  // If authenticated
    .body(Map.of("query", mutation))
    .when()
    .post("/graphql")
    .then()
    .statusCode(200)
    .body("data.createCalendar.id", notNullValue())
    .body("data.createCalendar.name", equalTo("Test Calendar"))
    .body("errors", nullValue());
```

**Coverage Target:**
- Task requires >70% coverage for service/API layers
- Existing tests likely already meet this target
- New tests should focus on **filling gaps**, not duplicating coverage
- Run `./mvnw verify jacoco:report` to check current coverage

---

## 5. ACTION PLAN

### Step 1: Verify Current Coverage
```bash
./mvnw clean verify jacoco:report
# Check target/site/jacoco/index.html for current coverage %
```

### Step 2: Create CalendarWorkflowTest.java
**Priority:** HIGH
**Focus:** Fill gaps in event addition and calendar update workflows

**Implementation:**
1. Create test class with `@QuarkusTest` annotation
2. Add `@BeforeEach` setup for test user, template, JWT token
3. Add `@AfterEach` cleanup for test data
4. Implement `testWorkflow_AddMultipleEventsToCalendar()`:
   - Create calendar via GraphQL (authenticated)
   - Add 5 events via GraphQL `addEvent` mutation
   - Query calendar with events
   - Verify all 5 events returned with correct data
5. Implement `testWorkflow_UpdateCalendarConfiguration()`:
   - Create calendar via GraphQL
   - Update config via `updateCalendar` mutation (enable moon phases)
   - Query calendar to verify config updated
   - Query database directly to verify persistence
6. Implement `testWorkflow_CreateCalendarFromTemplate_EndToEnd()`:
   - Query templates via GraphQL
   - Select template with specific config
   - Create calendar from template
   - Verify calendar config matches template config

### Step 3: Create TemplateWorkflowTest.java
**Priority:** MEDIUM
**Focus:** Template configuration cloning edge cases

**Implementation:**
1. Create test class with `@QuarkusTest` annotation
2. Add setup/cleanup methods
3. Implement tests for different template configurations:
   - Moon phases enabled
   - Hebrew calendar enabled
   - Custom holiday sets
4. Verify config cloning preserves all JSONB fields

### Step 4: Create GuestSessionWorkflowTest.java
**Priority:** MEDIUM (already well-covered by SessionServiceTest.java)
**Focus:** End-to-end GraphQL workflow for guest sessions

**Implementation:**
1. Create test class with `@QuarkusTest` annotation
2. Add setup for test session ID (UUID.randomUUID())
3. Implement `testGuestSessionWorkflow_CreateCalendarAsGuest()`:
   - Create calendar via GraphQL with sessionId parameter (no auth token)
   - Verify calendar persisted with sessionId
   - Verify calendar user is null
4. Implement `testGuestSessionWorkflow_ConvertSessionToUser()`:
   - Create calendar with sessionId
   - Create test user
   - Call `convertGuestSession` mutation (authenticated)
   - Verify calendar ownership transferred
   - Verify sessionId cleared
5. Implement `testGuestSessionWorkflow_MultipleCalendarsConversion()`:
   - Create 3 calendars with same sessionId
   - Convert session
   - Verify all 3 transferred

### Step 5: Run Integration Tests
```bash
./mvnw verify
# All tests should pass
# Execution time should be <60 seconds
```

### Step 6: Verify Coverage Target
```bash
./mvnw jacoco:report
# Check coverage for:
# - src/main/java/villagecompute/calendar/services/CalendarService.java
# - src/main/java/villagecompute/calendar/services/SessionService.java
# - src/main/java/villagecompute/calendar/api/graphql/CalendarResolver.java
# - src/main/java/villagecompute/calendar/api/graphql/SessionResolver.java
# Target: >70% line coverage
```

---

## 6. TESTING STRATEGY

### Test Isolation Strategy
- Each test method creates its own test data
- Use unique identifiers (timestamps, UUIDs) to avoid conflicts
- `@Transactional` on test methods for automatic rollback
- Explicit `@AfterEach` cleanup for data created outside test transaction

### GraphQL Query/Mutation Testing
- Use REST Assured `given().when().then()` pattern
- All GraphQL requests POST to `/graphql` endpoint
- Request body: `Map.of("query", graphqlString)`
- For mutations with variables: `Map.of("query", mutation, "variables", variablesMap)`
- Validate response: `.body("data.fieldName", matcher)`
- Verify no errors: `.body("errors", nullValue())`

### Authentication Strategy
**Option 1: Skip authenticated tests** (document limitation)
```java
@Test
@Disabled("Requires JWT authentication - test manually via Docker Compose")
void testCreateCalendar_Authenticated() {
    // Test requires OAuth JWT token generation
}
```

**Option 2: Generate JWT tokens** (recommended for full coverage)
```java
@Inject
AuthenticationService authService;

String generateTestJwtToken() {
    CalendarUser testUser = createTestUser();
    return authService.issueJWT(testUser);
}

@Test
void testCreateCalendar_Authenticated() {
    String token = generateTestJwtToken();

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + token)
        .body(Map.of("query", mutation))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200);
}
```

### Database Verification Strategy
- Use Panache entity methods for direct DB queries
- Example: `UserCalendar.findByIdOptional(calendarId)`
- Verify fields: `assertEquals(expected, actual)`
- Verify relationships: `assertNotNull(calendar.user)`
- Verify JSONB: `assertEquals("modern", calendar.configuration.get("theme").asText())`

---

## 7. REFERENCE FILES

### GraphQL Schema
**File:** `src/main/resources/META-INF/schema.graphql` (or generated by SmallRye GraphQL)

**Key Queries:**
- `templates(isActive: Boolean): [CalendarTemplate!]!`
- `template(id: UUID!): CalendarTemplate`
- `calendar(id: UUID!): UserCalendar`
- `myCalendars: [UserCalendar!]!`
- `me: CalendarUser`

**Key Mutations:**
- `createCalendar(input: CreateCalendarInput!): UserCalendar!`
- `updateCalendar(id: UUID!, input: UpdateCalendarInput!): UserCalendar!`
- `deleteCalendar(id: UUID!): Boolean!`
- `addEvent(calendarId: UUID!, input: AddEventInput!): Event!`
- `convertGuestSession(sessionId: String!): Int!`

### Service Classes
- `CalendarService.java` - Calendar CRUD, session support (lines 49-416)
- `SessionService.java` - Guest session management (lines 1-136)
- `CalendarGenerationService.java` - SVG/PDF generation (used in CalendarServiceIntegrationTest.java)
- `AuthenticationService.java` - JWT issuance, OAuth callback (lines 1-193)
- `TemplateService.java` - Template management, cloning logic

### Entity Models
- `UserCalendar.java` - Calendar entity with sessionId field (lines 1-157)
- `CalendarUser.java` - User entity
- `CalendarTemplate.java` - Template entity
- `Event.java` - Event entity

### Existing Test Examples
- `CalendarServiceIntegrationTest.java` - Template queries, calendar generation (554 lines)
- `CalendarGraphQLTest.java` - GraphQL queries, DataLoader, authorization (842 lines)
- `SessionServiceTest.java` - Session conversion unit tests (373 lines)
- `AuthenticationIntegrationTest.java` - OAuth callback tests (416 lines)

---

## 8. SUCCESS CRITERIA

**Task Complete When:**
1. ✅ All three target files created and passing
2. ✅ Acceptance criteria #2 gap filled (event addition workflow)
3. ✅ Acceptance criteria #3 gap filled (calendar update workflow)
4. ✅ All tests pass with `./mvnw verify`
5. ✅ Tests complete in <60 seconds
6. ✅ Code coverage >70% for service/API layers (verified with JaCoCo)
7. ✅ No test failures in CI pipeline

**Bonus (Optional):**
- Generate JWT tokens for full authenticated workflow coverage
- Add performance assertions (query response time <500ms)
- Add test for DataLoader N+1 prevention (query counting)

---

## 9. DEPENDENCIES & BLOCKERS

**All Dependencies Met:**
- ✅ I2.T1 - Sequence diagrams (complete)
- ✅ I2.T2 - CalendarService (complete)
- ✅ I2.T3 - EventService (complete)
- ✅ I2.T4 - TemplateService (complete)
- ✅ I2.T5 - AstronomicalService (complete)
- ✅ I2.T6 - CalendarResolver (complete)
- ✅ I2.T7 - Calendar Editor UI (complete)
- ✅ I2.T8 - Template Gallery UI (complete)
- ✅ I2.T9 - Guest Session Management (complete)

**No Blockers:**
- All services and GraphQL resolvers implemented
- Test infrastructure exists and is working
- GraphQL API is functional (verified by existing tests)
- Guest session management fully implemented

---

## 10. ADDITIONAL NOTES

### Coverage Status
Current test coverage is likely **already >70%** for service and API layers based on existing tests:
- CalendarServiceIntegrationTest.java: 30+ tests
- CalendarGraphQLTest.java: 40+ tests
- SessionServiceTest.java: 30+ tests
- Multiple repository and entity tests

**New tests should focus on filling specific gaps** identified in acceptance criteria rather than duplicating coverage.

### Authentication Testing Limitation
Full JWT authentication workflow testing requires:
- OAuth OIDC provider setup (Google/Facebook/Apple)
- Or test OIDC provider configuration
- Or JWT token generation in tests

**Recommendation:** Use JWT token generation approach (AuthenticationService.issueJWT) for authenticated tests.

### Test Execution Time
Existing integration tests use:
- Quarkus test framework (fast startup)
- In-memory H2 database (fast I/O)
- Mocked external services (StorageService, OAuth)

**Expected execution time:** 20-40 seconds for all integration tests, well within <60s target.

### Next Iteration
After I2.T10 completion:
- Iteration 2 is complete (all 10 tasks done)
- Next iteration: I3 (E-commerce functionality: Stripe, orders, checkout)
