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
  "input_files": ["api/schema.graphql"],
  "deliverables": "Integration tests for all critical calendar workflows, Tests use GraphQL API (not direct service calls), Test database setup and teardown automated, Tests achieve >70% coverage for service/API layers, All tests pass with ./mvnw verify",
  "acceptance_criteria": "Template workflow test creates calendar from template, verifies config cloned, Event workflow test adds 5 events, queries calendar, verifies all 5 returned, Update workflow test modifies calendar config, verifies changes in database, Guest session workflow test creates calendar as guest, converts on login, verifies ownership, Tests run in isolation (each test creates own test data, cleans up after), Integration tests complete in <60 seconds",
  "dependencies": ["I2.T2", "I2.T3", "I2.T4", "I2.T5", "I2.T6", "I2.T7", "I2.T8", "I2.T9"],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Testing Levels (from 03_Verification_and_Glossary.md)

**Integration Testing Specifications:**

Integration testing validates component interactions, API endpoints, database operations, and external service integrations. The strategy requires:

- **Scope**: Component interactions, API endpoints (GraphQL/REST), database transactions, job queue processing, external service integration (OAuth, Stripe webhooks, R2 uploads)
- **Tools**: Quarkus test framework with `@QuarkusTest`, REST Assured for HTTP API testing, Testcontainers for PostgreSQL/Jaeger
- **Coverage Target**: 70%+ line coverage for service layer, API layer (GraphQL resolvers, REST controllers), repository layer
- **Test Database**: H2 in-memory or Testcontainers PostgreSQL (for PostGIS testing)
- **Test Isolation**: Each test creates and cleans up its own data, transactional rollback where applicable

**Key Test Scenarios for Calendar Workflows:**
1. GraphQL API workflows (create calendar from template, update configuration, add events)
2. Database transaction tests (ACID compliance, optimistic locking, rollback scenarios)
3. Authorization enforcement (RBAC rules, owner-only access, admin access)
4. Guest session conversion (session persistence, user migration, data integrity)

### Context: GraphQL Schema - Queries and Mutations (from api/schema.graphql)

The GraphQL schema defines the complete API surface for calendar operations:

**Core Queries:**
```graphql
templates(isActive: Boolean = true, isFeatured: Boolean): [CalendarTemplate!]!
template(id: ID!): CalendarTemplate
calendar(id: ID!): UserCalendar
calendars(userId: ID, year: Int): [UserCalendar!]!
myCalendars(year: Int): [UserCalendar!]!
currentUser: CalendarUser
```

**Core Mutations:**
```graphql
createCalendar(input: CalendarInput!): UserCalendar!
updateCalendar(id: ID!, input: CalendarUpdateInput!): UserCalendar!
deleteCalendar(id: ID!): Boolean!
convertGuestSession(sessionId: ID!): CalendarUser!
createTemplate(input: TemplateInput!): CalendarTemplate!
```

**Input Types:**
```graphql
input CalendarInput {
  name: String!
  year: Int!
  templateId: ID!
  configuration: JSON      # JSONB customization overrides
  isPublic: Boolean
}

input CalendarUpdateInput {
  name: String
  configuration: JSON      # Updated customizations
  isPublic: Boolean
}
```

**Types:**
- `UserCalendar`: Main entity with fields `id`, `name`, `year`, `template`, `configuration` (JSONB), `isPublic`, `sessionId`, `status`, `user`
- `CalendarTemplate`: Template entity with `configuration` (JSONB), `isActive`, `isFeatured`, `displayOrder`
- `CalendarUser`: User entity with `id`, `email`, `oauthProvider`, `isAdmin`

### Context: Data Model Overview (from 03_System_Structure_and_Data.md)

**Entity Relationships for Testing:**

1. **UserCalendar Entity**:
   - Primary Key: `id` (UUID)
   - Foreign Keys: `user_id` (CalendarUser), `template_id` (CalendarTemplate)
   - Key Fields: `name` (VARCHAR 255), `year` (INT), `configuration` (JSONB), `isPublic` (BOOLEAN), `sessionId` (VARCHAR 255 nullable), `status` (CalendarStatus enum), `version` (INT for optimistic locking)
   - Relationships: N:1 with User, N:1 with Template
   - **Critical for testing**: `version` field enables optimistic locking (concurrent update detection)

2. **CalendarTemplate Entity**:
   - Primary Key: `id` (UUID)
   - Key Fields: `name`, `description`, `configuration` (JSONB), `isActive`, `isFeatured`, `displayOrder`
   - **Critical for testing**: `configuration` JSONB structure must include required fields: `layout`, `fonts`, `colors`

3. **CalendarUser Entity**:
   - Primary Key: `id` (UUID)
   - Key Fields: `oauthProvider` (enum: GOOGLE, FACEBOOK), `oauthSubject`, `email`, `displayName`, `isAdmin` (BOOLEAN)
   - **Critical for testing**: `isAdmin` flag controls authorization for template management

4. **Event Data Storage**:
   - Events are embedded in `UserCalendar.configuration` JSONB field for MVP
   - Structure: `{ events: [...], layout: {...}, colors: {...}, astronomy: {...} }`
   - Each event: `{ date: "2025-01-01", text: "Event", emoji: "🎉", color: "#FF0000" }`

### Context: Authentication & Authorization (from 05_Operational_Architecture.md)

**OAuth Authentication Flow:**
1. User authenticates via Google/Facebook OAuth 2.0
2. Backend exchanges authorization code for ID token (server-to-server)
3. Backend generates application JWT token with claims: `sub` (user_id), `role`, `email`, `exp` (24 hours)
4. Client stores JWT in localStorage, includes in `Authorization: Bearer {token}` header

**Authorization Rules (RBAC):**
- **Guest users**: Can create calendars with `sessionId`, no persistent user account
- **Authenticated users**: Can create/edit/delete own calendars (checked via `calendar.user.id == currentUser.id`)
- **Admin users**: Can access all calendars, create templates, view analytics

**Guest Session Management:**
- Anonymous users get UUID `session_id` stored in browser localStorage
- Calendars created with `sessionId` field set, `user` field null
- On login: `convertGuestSession` mutation transfers ownership (`UPDATE calendars SET user_id = ? WHERE session_id = ?`)
- Session expiration: 30 days (cleanup job deletes expired sessions)

### Context: Component Diagram - Service Layer (from 03_System_Structure_and_Data.md)

**CalendarService - Primary Business Logic:**
- Methods: `createCalendar()`, `updateCalendar()`, `deleteCalendar()`, `getCalendar()`, `listCalendars()`, `convertSessionToUser()`
- Responsibilities: Calendar CRUD, template application, authorization checks, optimistic locking, session-to-user migration
- Dependencies: CalendarRepository (Panache), TemplateRepository

**TemplateService - Template Management:**
- Methods: `createTemplate()` (admin only), `updateTemplate()`, `deleteTemplate()` (soft delete via `isActive=false`)
- Responsibilities: Template CRUD, configuration validation (requires: layout, fonts, colors), template cloning
- Dependencies: TemplateRepository, StorageService (for thumbnail uploads)

**EventService - Event Operations:**
- Methods: `addEvent()`, `updateEvent()`, `deleteEvent()`, `listEvents()`
- Note: For MVP, events stored in `calendar.configuration` JSONB, not separate Event table
- Event operations test via `updateCalendar()` mutation with modified configuration

**CalendarGenerationService - PDF Rendering:**
- Methods: `generateCalendar()` (SVG generation + PDF conversion + R2 upload)
- Dependencies: Batik transcoder, R2StorageService
- **For testing**: Mock `StorageService` to avoid requiring R2 credentials

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `src/test/java/villagecompute/calendar/integration/CalendarServiceIntegrationTest.java` (554 lines)
    *   **Summary:** Comprehensive integration test demonstrating all required patterns: `@QuarkusTest` setup, `@TestMethodOrder`, `@BeforeEach`/`@AfterEach` data lifecycle, REST Assured GraphQL testing, `@InjectMock` for external services
    *   **Recommendation:** You MUST study this file as your primary reference. It demonstrates:
        - Test organization: `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` + `@Order(n)` annotations
        - Setup pattern: `@BeforeEach @Transactional void setup()` creates test user and template
        - Cleanup pattern: `@AfterEach @Transactional void cleanup()` deletes in reverse FK order
        - Mocking external services: `@InjectMock StorageService storageService` + `when().thenReturn()` pattern
        - REST Assured GraphQL: `given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then().statusCode(200).body("data.field", matcher)`
        - Test data creation: `testUser.oauthProvider = "GOOGLE"`, `testTemplate.configuration = createTestConfiguration()`
        - JSONB handling: `objectMapper.readTree("""{ "key": "value" }""")`

*   **File:** `src/main/java/villagecompute/calendar/services/CalendarService.java` (lines 1-416)
    *   **Summary:** Service layer implementing calendar CRUD with authorization, validation, optimistic locking, and session management
    *   **Recommendation:** You MUST understand these methods for testing:
        - `createCalendar(name, year, templateId, configuration, isPublic, user, sessionId)`: Validates input (year 1000-9999, name <255 chars), applies template config, persists calendar, returns entity
        - `updateCalendar(id, name, configuration, isPublic, currentUser)`: Checks write access, updates fields, increments version (optimistic locking), persists
        - `deleteCalendar(id, currentUser)`: Checks write access, deletes (hard delete)
        - `convertSessionToUser(sessionId, user)`: Finds calendars by sessionId, sets user, clears sessionId, returns count
        - Authorization: `checkReadAccess()` (public calendars, owner, admin), `checkWriteAccess()` (owner, admin only)
        - Validation: throws `IllegalArgumentException` for invalid input, `SecurityException` for unauthorized access

*   **File:** `src/main/java/villagecompute/calendar/services/TemplateService.java` (lines 1-248)
    *   **Summary:** Template management with admin-only operations and configuration validation
    *   **Recommendation:** You SHOULD use these methods:
        - `createTemplate(TemplateInput)`: Validates configuration JSONB structure (requires fields: `layout`, `fonts`, `colors`), checks for duplicate names, persists
        - `validateTemplateConfiguration(JsonNode)`: Ensures required top-level fields present, throws `IllegalArgumentException` if missing
        - Configuration structure: `{ "layout": {...}, "fonts": {...}, "colors": {...}, "theme": "modern" }`

*   **File:** `api/schema.graphql` (855 lines)
    *   **Summary:** Complete GraphQL schema defining all queries, mutations, types, inputs, and enums
    *   **Recommendation:** You MUST reference this for exact field names and types in tests:
        - Use exact casing: `isPublic`, `displayName`, `oauthProvider` (not `is_public`, `display_name`)
        - Custom scalars: `JSON` (JSONB), `DateTime` (ISO-8601 timestamps), `BigDecimal` (currency)
        - Enums: `CalendarStatus` (DRAFT, GENERATING, READY, FAILED), `OAuthProvider` (GOOGLE, FACEBOOK)

*   **File:** `pom.xml` (lines 92-244)
    *   **Summary:** Maven test dependencies and JaCoCo coverage configuration
    *   **Recommendation:** You SHOULD verify:
        - Test dependencies available: `quarkus-junit5`, `quarkus-junit5-mockito`, `rest-assured`, `quarkus-jdbc-h2`, `quarkus-jacoco`
        - Coverage enforced: 70% minimum for packages `villagecompute.calendar.data.models` and `villagecompute.calendar.data.repositories` (lines 318-347)
        - Run tests: `./mvnw test` (unit tests only) or `./mvnw verify` (integration tests + coverage check)

### Implementation Tips & Notes

*   **Tip:** Follow the exact test class structure from `CalendarServiceIntegrationTest.java`:
    ```java
    @QuarkusTest
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class YourWorkflowTest {
        @Inject ServiceClass service;
        @Inject ObjectMapper objectMapper;
        @InjectMock StorageService storageService;

        private CalendarUser testUser;
        private CalendarTemplate testTemplate;

        @BeforeEach @Transactional void setup() {
            testUser = new CalendarUser();
            testUser.oauthProvider = "GOOGLE";
            testUser.oauthSubject = "test-" + System.currentTimeMillis();
            testUser.email = "test@example.com-" + System.currentTimeMillis();
            testUser.persist();

            testTemplate = new CalendarTemplate();
            testTemplate.name = "Test Template " + System.currentTimeMillis();
            testTemplate.configuration = createTestConfiguration();
            testTemplate.isActive = true;
            testTemplate.persist();

            when(storageService.uploadFile(anyString(), any(byte[].class), anyString()))
                .thenReturn("https://r2.example.com/test.pdf");
        }

        @AfterEach @Transactional void cleanup() {
            if (testUser != null) {
                UserCalendar.delete("user.id", testUser.id);
                CalendarUser.deleteById(testUser.id);
            }
            if (testTemplate != null) {
                CalendarTemplate.deleteById(testTemplate.id);
            }
        }
    }
    ```

*   **Tip:** Use REST Assured for ALL GraphQL API interactions (not direct service method calls):
    ```java
    String query = """
        query {
            templates(isActive: true) {
                id
                name
                configuration
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
        .body("data.templates", hasSize(greaterThanOrEqualTo(1)))
        .body("data.templates[0].name", equalTo(testTemplate.name))
        .body("errors", nullValue());
    ```

*   **Tip:** Create JSONB configuration using `ObjectMapper` for type-safe JSON:
    ```java
    private JsonNode createTestConfiguration() {
        try {
            return objectMapper.readTree("""
                {
                    "layout": {"type": "grid"},
                    "fonts": {"body": "Arial"},
                    "colors": {"primary": "#0000FF"},
                    "theme": "modern",
                    "showMoonPhases": true,
                    "events": []
                }
                """);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    ```

*   **Note:** Test data cleanup MUST happen in reverse foreign key order to avoid constraint violations:
    ```java
    @AfterEach @Transactional void cleanup() {
        // Delete UserCalendar first (has FK to User and Template)
        if (testUser != null) {
            UserCalendar.delete("user.id", testUser.id);
        }
        // Then delete User
        if (testUser != null) {
            CalendarUser.deleteById(testUser.id);
        }
        // Finally delete Template (no dependencies)
        if (testTemplate != null) {
            CalendarTemplate.deleteById(testTemplate.id);
        }
    }
    ```

*   **Note:** For guest session testing, create calendars WITHOUT a user:
    ```java
    UserCalendar guestCalendar = new UserCalendar();
    guestCalendar.user = null;  // Critical: no user for guest
    guestCalendar.sessionId = UUID.randomUUID().toString();
    guestCalendar.name = "Guest Calendar";
    guestCalendar.year = 2025;
    guestCalendar.template = testTemplate;
    guestCalendar.persist();

    // Later, convert session
    int converted = calendarService.convertSessionToUser(sessionId, authenticatedUser);
    assertEquals(1, converted);

    // Verify transfer
    UserCalendar.refresh(guestCalendar);
    assertEquals(authenticatedUser.id, guestCalendar.user.id);
    assertNull(guestCalendar.sessionId);
    ```

*   **Warning:** Authorization testing requires creating test users with JWT tokens. However, full OAuth flow testing is complex. **Recommended approach:**
    ```java
    // Option 1: Test at service layer (no GraphQL, direct method calls)
    @Test
    void testAuthorization_CannotAccessOtherUsersCalendar() {
        CalendarUser owner = createTestUser("owner@example.com");
        CalendarUser attacker = createTestUser("attacker@example.com");
        UserCalendar calendar = createTestCalendar(owner);

        assertThrows(SecurityException.class, () -> {
            calendarService.getCalendar(calendar.id, attacker);
        });
    }

    // Option 2: Test via GraphQL with explicit error checking
    @Test
    void testGraphQL_CreateCalendar_WithoutAuthentication() {
        String mutation = """
            mutation {
                createCalendar(input: {name: "Test", year: 2025, templateId: "%s"}) {
                    id
                }
            }
            """.formatted(testTemplate.id);

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("query", mutation))
            // NO Authorization header
            .when()
            .post("/graphql")
            .then()
            .statusCode(200)
            .body("errors", notNullValue())
            .body("errors[0].message", anyOf(
                containsString("Unauthorized"),
                containsString("Authentication required")
            ));
    }
    ```

*   **Warning:** Mock `StorageService` in ALL tests to avoid requiring real R2 credentials:
    ```java
    @InjectMock
    StorageService storageService;

    @BeforeEach
    void setup() {
        when(storageService.uploadFile(anyString(), any(byte[].class), anyString()))
            .thenReturn("https://r2.example.com/calendars/test-pdf-" + System.currentTimeMillis() + ".pdf");
    }
    ```

*   **Critical:** Events are stored in `calendar.configuration` JSONB field, NOT a separate Event table. To test event addition:
    ```java
    // Add events by updating calendar configuration
    JsonNode configWithEvents = objectMapper.readTree("""
        {
            "layout": {"type": "grid"},
            "fonts": {"body": "Arial"},
            "colors": {"primary": "#0000FF"},
            "events": [
                {"date": "2025-01-01", "text": "New Year", "emoji": "🎉", "color": "#FF0000"},
                {"date": "2025-12-25", "text": "Christmas", "emoji": "🎄", "color": "#00FF00"},
                {"date": "2025-07-04", "text": "Independence Day", "emoji": "🇺🇸", "color": "#0000FF"},
                {"date": "2025-10-31", "text": "Halloween", "emoji": "🎃", "color": "#FFA500"},
                {"date": "2025-11-25", "text": "Thanksgiving", "emoji": "🦃", "color": "#8B4513"}
            ]
        }
        """);

    calendar.configuration = configWithEvents;
    calendar.persist();

    // Verify via GraphQL query
    String query = """
        query {
            calendar(id: "%s") {
                configuration
            }
        }
        """.formatted(calendar.id);

    given()
        .contentType(ContentType.JSON)
        .body(Map.of("query", query))
        .when()
        .post("/graphql")
        .then()
        .body("data.calendar.configuration.events", hasSize(5))
        .body("data.calendar.configuration.events[0].text", equalTo("New Year"));
    ```

*   **Performance:** Tests MUST complete in <60 seconds total. Strategy:
    - Use H2 in-memory database (fast I/O, no disk writes)
    - Mock external services (StorageService, OAuth)
    - Minimize test data (create only what's needed per test)
    - Use `@Order` annotations to organize tests logically
    - Avoid Thread.sleep() or artificial delays

*   **Coverage:** JaCoCo plugin enforces 70% minimum. To check coverage:
    ```bash
    ./mvnw verify jacoco:report
    open target/site/jacoco/index.html
    # Check coverage for:
    # - villagecompute.calendar.data.models (>70%)
    # - villagecompute.calendar.data.repositories (>70%)
    ```

---

**End of Task Briefing Package**
