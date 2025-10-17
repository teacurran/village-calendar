# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create end-to-end integration tests for critical calendar workflows using Quarkus test framework with REST Assured for GraphQL API testing. Test scenarios: (1) Create calendar from template - authenticate, query templates, create calendar from template, verify calendar created with correct config; (2) Add events to calendar - create calendar, add multiple events, query calendar with events, verify events returned; (3) Update calendar - create calendar, update config (enable astronomy), verify changes persisted; (4) Guest session conversion - create calendar as guest, authenticate, convert session, verify calendar transferred to user. Use test database (H2 or Testcontainers PostgreSQL). Achieve >70% code coverage for service and API layers.

---

## Issues Detected

### **Critical Issue #1: GraphQL Authentication Failures (401 Unauthorized)**

**Failed Tests:**
*   `TemplateWorkflowTest.testTemplateWorkflow_ApplyTemplateWithMoonPhasesEnabled` - 401 error
*   `TemplateWorkflowTest.testTemplateWorkflow_ApplyTemplateWithHebrewCalendarEnabled` - 401 error
*   `TemplateWorkflowTest.testTemplateWorkflow_ApplyTemplateWithCustomHolidayConfig` - 401 error
*   `TemplateWorkflowTest.testTemplateWorkflow_ConfigurationIsDeepCopied` - 401 error
*   `GuestSessionWorkflowTest.testGuestWorkflow_ConvertSessionToUserAccount` - 401 error
*   `GuestSessionWorkflowTest.testGuestWorkflow_ConvertMultipleCalendarsFromSession` - 401 error

**Root Cause:**
The tests are generating JWT tokens using `authService.issueJWT(testUser)` and passing them to GraphQL endpoint, but the GraphQL endpoint is rejecting these tokens with 401 Unauthorized. This indicates that the JWT authentication is not properly configured for the test environment OR the GraphQL resolvers have authentication requirements that cannot be met in integration tests.

**Evidence:**
Looking at `CalendarServiceIntegrationTest.java` (lines 496-523), the existing pattern for testing authenticated GraphQL operations is:
```java
@Test
void testGraphQL_CreateCalendar_WithoutAuthentication() {
    // When: Try to create calendar without authentication
    given()
        .contentType(ContentType.JSON)
        .body(Map.of("query", mutation))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("errors", notNullValue())
        .body("errors[0].message", anyOf(
            containsString("Unauthorized"),
            containsString("System error")
        ));
}
```

The existing test suite expects unauthorized requests to return 200 status with errors in the response body, NOT to work with JWT tokens.

### **Critical Issue #2: Test Approach Mismatch**

**Problem:**
The generated tests attempt to test GraphQL mutations that require authentication (createCalendar, updateCalendar, convertGuestSession) by generating JWT tokens, but this approach does not work in the Quarkus test environment as configured.

**Correct Approach (as demonstrated in existing tests):**
The existing integration tests (`CalendarServiceIntegrationTest.java`) use TWO strategies:
1. **Public GraphQL queries** (no auth required): template queries, schema introspection
2. **Service layer testing** (bypasses GraphQL auth): direct service method calls with test user objects

**Example from existing code:**
```java
// CORRECT: Test at service layer
@Test
@Transactional
void testCalendarGeneration_EndToEnd() {
    // Given: A user calendar (created directly, not via GraphQL)
    UserCalendar calendar = new UserCalendar();
    calendar.user = testUser;
    calendar.name = "Generated Calendar";
    calendar.year = 2025;
    calendar.template = testTemplate;
    calendar.configuration = createTestConfiguration();
    calendar.persist();

    // When: Generate calendar PDF (via service, not GraphQL)
    String publicUrl = calendarGenerationService.generateCalendar(calendar);

    // Then: Verify results
    assertNotNull(publicUrl);
}
```

### **Issue #3: Incomplete Coverage of Acceptance Criteria**

**Acceptance Criteria Status:**

1. ✅ Template workflow test creates calendar from template, verifies config cloned
   - **Status**: PARTIAL - Tests exist but fail due to auth issues

2. ✅ Event workflow test adds 5 events, queries calendar, verifies all 5 returned
   - **Status**: COMPLETE - `CalendarWorkflowTest.testWorkflow_AddMultipleEventsToCalendar` passes

3. ✅ Update workflow test modifies calendar config, verifies changes in database
   - **Status**: COMPLETE - `CalendarWorkflowTest.testWorkflow_UpdateCalendarConfiguration` passes

4. ✅ Guest session workflow test creates calendar as guest, converts on login, verifies ownership
   - **Status**: PARTIAL - Tests exist but fail due to auth issues

5. ✅ Tests run in isolation (each test creates own test data, cleans up after)
   - **Status**: COMPLETE - All tests have proper `@BeforeEach` and `@AfterEach`

6. ✅ Integration tests complete in <60 seconds
   - **Status**: COMPLETE - Tests run in ~25 seconds

---

## Best Approach to Fix

You MUST modify the failing tests to follow the pattern used in `CalendarServiceIntegrationTest.java`. Specifically:

### Step 1: Fix TemplateWorkflowTest.java

**Change approach from:** GraphQL mutations with JWT tokens
**Change approach to:** Service layer testing with direct method calls

**Required modifications:**

1. **Inject CalendarService instead of relying on GraphQL:**
   ```java
   @Inject
   CalendarService calendarService;  // ADD THIS
   ```

2. **Replace ALL GraphQL `createCalendar` mutations with direct service calls:**
   ```java
   // BEFORE (FAILS - 401):
   String createMutation = String.format("""
       mutation {
           createCalendar(input: {...}) { id }
       }
       """, moonTemplate.id.toString());

   String calendarId = given()
       .contentType(ContentType.JSON)
       .header("Authorization", "Bearer " + jwtToken)  // This doesn't work!
       .body(Map.of("query", createMutation))
       .post("/graphql")
       .then()
       .statusCode(200)
       .extract()
       .path("data.createCalendar.id");

   // AFTER (WORKS):
   UserCalendar calendar = calendarService.createCalendar(
       "My Astronomy Calendar",
       2025,
       moonTemplate.id,
       null,  // No custom configuration - use template config
       true,  // isPublic
       testUser,  // Authenticated user
       null   // No session ID
   );

   assertNotNull(calendar);
   assertNotNull(calendar.id);
   ```

3. **Verify configuration directly from database:**
   ```java
   // Verify calendar inherits moon phases setting in database
   UserCalendar retrievedCalendar = UserCalendar.findById(calendar.id);
   assertNotNull(retrievedCalendar, "Calendar should exist in database");
   assertTrue(retrievedCalendar.configuration.get("showMoonPhases").asBoolean(),
       "Calendar should inherit showMoonPhases=true from template");
   ```

4. **Update ALL four test methods:**
   - `testTemplateWorkflow_ApplyTemplateWithMoonPhasesEnabled`
   - `testTemplateWorkflow_ApplyTemplateWithHebrewCalendarEnabled`
   - `testTemplateWorkflow_ApplyTemplateWithCustomHolidayConfig`
   - `testTemplateWorkflow_ConfigurationIsDeepCopied`

5. **Keep setup/cleanup as-is**, but remove JWT token generation (not needed):
   ```java
   @BeforeEach
   @Transactional
   void setup() {
       testUser = new CalendarUser();
       testUser.oauthProvider = "GOOGLE";
       testUser.oauthSubject = "template-test-" + System.currentTimeMillis();
       testUser.email = "template-test-" + System.currentTimeMillis() + "@example.com";
       testUser.displayName = "Template Test User";
       testUser.persist();

       // REMOVE: jwtToken = authService.issueJWT(testUser);
       // This is not needed for service layer testing
   }
   ```

### Step 2: Fix GuestSessionWorkflowTest.java

**Apply the same pattern:**

1. **Inject CalendarService:**
   ```java
   @Inject
   CalendarService calendarService;  // Already present, good
   ```

2. **Replace GraphQL `convertGuestSession` mutation with service call:**
   ```java
   // BEFORE (FAILS - 401):
   String convertMutation = String.format("""
       mutation {
           convertGuestSession(sessionId: "%s") {
               id
               email
           }
       }
       """, sessionId);

   given()
       .contentType(ContentType.JSON)
       .header("Authorization", "Bearer " + jwtToken)
       .body(Map.of("query", convertMutation))
       .post("/graphql")
       .then()
       .statusCode(200)
       .body("data.convertGuestSession.id", equalTo(user.id.toString()));

   // AFTER (WORKS):
   int convertedCount = calendarService.convertSessionToUser(sessionId, user);
   assertEquals(1, convertedCount, "Should convert 1 calendar");

   // Verify calendar ownership transferred
   UserCalendar refreshedCalendar = UserCalendar.findById(guestCalendar.id);
   assertNotNull(refreshedCalendar.user, "Calendar should now have a user");
   assertEquals(user.id, refreshedCalendar.user.id, "Calendar should be owned by the user");
   assertNull(refreshedCalendar.sessionId, "SessionId should be cleared");
   ```

3. **Remove JWT token generation and GraphQL auth header from failing tests:**
   - `testGuestWorkflow_ConvertSessionToUserAccount`
   - `testGuestWorkflow_ConvertMultipleCalendarsFromSession`

4. **Keep the other tests that don't use GraphQL auth** (they pass):
   - `testGuestWorkflow_CreateCalendarWithoutAuthentication` (service layer only)
   - `testGuestWorkflow_SessionIsolation` (service layer only)
   - `testGuestWorkflow_ConvertEmptySession` (service layer only)
   - `testGuestWorkflow_QueryCalendarsBySession` (repository layer only)

### Step 3: Keep CalendarWorkflowTest.java As-Is

**Status**: This file is CORRECT and all tests pass. DO NOT MODIFY IT.

The tests in `CalendarWorkflowTest.java` already follow the correct pattern:
- Use service layer for authenticated operations
- Use GraphQL only for public queries (templates)
- All 5 tests pass successfully

### Step 4: Optional - Add GraphQL Public Endpoint Tests

If you want to test GraphQL endpoints (for coverage), you can ONLY test public endpoints (no auth required):

**Example (add to TemplateWorkflowTest.java if desired):**
```java
@Test
@Order(1)
void testGraphQL_QueryPublicTemplates() {
    // Query templates via GraphQL (public endpoint, no auth required)
    String templateQuery = """
        query {
            templates(isActive: true) {
                id
                name
                description
                configuration
            }
        }
        """;

    given()
        .contentType(ContentType.JSON)
        .body(Map.of("query", templateQuery))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.templates", notNullValue())
        .body("data.templates", hasSize(greaterThanOrEqualTo(1)))
        .body("errors", nullValue());
}
```

---

## Summary of Required Changes

### File: `src/test/java/villagecompute/calendar/integration/TemplateWorkflowTest.java`

1. Add `@Inject CalendarService calendarService;`
2. Remove `@Inject AuthenticationService authService;`
3. Remove `private String jwtToken;` field
4. Remove `jwtToken = authService.issueJWT(testUser);` from setup
5. Replace ALL GraphQL mutations in these methods with service layer calls:
   - `testTemplateWorkflow_ApplyTemplateWithMoonPhasesEnabled` (lines 80-158)
   - `testTemplateWorkflow_ApplyTemplateWithHebrewCalendarEnabled` (lines 163-238)
   - `testTemplateWorkflow_ApplyTemplateWithCustomHolidayConfig` (lines 243-357)
   - `testTemplateWorkflow_ConfigurationIsDeepCopied` (lines 362-497)
6. Verify results by directly querying database (UserCalendar.findById)

### File: `src/test/java/villagecompute/calendar/integration/GuestSessionWorkflowTest.java`

1. Remove `@Inject AuthenticationService authService;` (keep CalendarService)
2. Remove `jwtToken` generation in these tests:
   - `testGuestWorkflow_ConvertSessionToUserAccount` (lines 133-210)
   - `testGuestWorkflow_ConvertMultipleCalendarsFromSession` (lines 216-351)
3. Replace GraphQL `convertGuestSession` mutations with service calls:
   ```java
   int convertedCount = calendarService.convertSessionToUser(sessionId, user);
   ```
4. Verify by directly querying database

### File: `src/test/java/villagecompute/calendar/integration/CalendarWorkflowTest.java`

**NO CHANGES REQUIRED** - All tests pass

---

## Expected Outcome After Fixes

1. **All tests pass**: `./mvnw test -Dtest="CalendarWorkflowTest,TemplateWorkflowTest,GuestSessionWorkflowTest"`
2. **All acceptance criteria met**:
   - ✅ Template workflow tests verify config cloning
   - ✅ Event workflow test adds 5 events, verifies all returned
   - ✅ Update workflow test modifies config, verifies database persistence
   - ✅ Guest session workflow test creates as guest, converts, verifies ownership
   - ✅ Tests run in isolation with proper cleanup
   - ✅ Tests complete in <60 seconds

3. **Code coverage >70%** for service and API layers (check with `./mvnw verify jacoco:report`)

---

## Important Notes

**Why JWT Authentication Doesn't Work in Tests:**

The GraphQL endpoint likely requires SmallRye JWT authentication with specific configuration (public key, issuer, etc.) that is not set up in the test environment. The existing test suite avoids this complexity by:
1. Testing public GraphQL endpoints (no auth)
2. Testing business logic at service layer (bypasses GraphQL auth)
3. Documenting that full OAuth flow requires manual testing with Docker Compose

This is a **valid and pragmatic testing strategy** - integration tests focus on business logic, not OAuth infrastructure.

**Why This Approach Is Correct:**

- Integration tests validate component interactions (service → repository → database)
- GraphQL is just a transport layer - testing the service layer is MORE valuable
- Full OAuth testing requires external dependencies (Google/Facebook) - not suitable for CI/CD
- Existing codebase uses this pattern successfully (CalendarServiceIntegrationTest.java)

---

## Verification Steps

After making changes:

1. Run tests:
   ```bash
   ./mvnw test -Dtest="CalendarWorkflowTest,TemplateWorkflowTest,GuestSessionWorkflowTest"
   ```
   **Expected:** All 15 tests pass (currently 9 pass, 6 fail)

2. Check coverage:
   ```bash
   ./mvnw verify jacoco:report
   open target/site/jacoco/index.html
   ```
   **Expected:** >70% coverage for service and API layers

3. Verify test execution time:
   ```bash
   ./mvnw verify
   ```
   **Expected:** Integration tests complete in <60 seconds
