# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I2.T6",
  "iteration_id": "I2",
  "iteration_goal": "Implement calendar creation, editing, and template system. Build calendar editor UI components. Integrate astronomical calculations (moon phases, Hebrew calendar). Create sequence diagrams for key workflows",
  "description": "Replace stub implementations in CalendarResolver with real service calls. Implement queries: calendar(id) (fetch calendar with events, authorize user), calendars(userId, year) (list user's calendars with pagination). Implement mutations: createCalendar(input) (validate input, call CalendarService), updateCalendar(id, input) (authorize, update), deleteCalendar(id) (authorize, soft/hard delete). Inject SecurityIdentity for user context. Implement DataLoader pattern to prevent N+1 queries when fetching related entities (e.g., calendar with user and events). Add error handling (map service exceptions to GraphQL errors). Write integration tests for all resolver methods.",
  "agent_type_hint": "BackendAgent",
  "inputs": "CalendarService from Task I2.T2, EventService from Task I2.T3, GraphQL schema from I1.T6",
  "target_files": [
    "src/main/java/villagecompute/calendar/api/graphql/CalendarResolver.java",
    "src/main/java/villagecompute/calendar/api/graphql/dataloader/CalendarDataLoader.java",
    "src/test/java/villagecompute/calendar/api/graphql/CalendarResolverTest.java"
  ],
  "input_files": [
    "src/main/java/villagecompute/calendar/service/CalendarService.java",
    "src/main/java/villagecompute/calendar/service/EventService.java",
    "src/main/java/villagecompute/calendar/api/graphql/CalendarResolver.java",
    "api/graphql-schema.graphql"
  ],
  "deliverables": "All calendar query/mutation resolvers implemented, DataLoader pattern implemented for efficient queries, Authorization checks (user can only query own calendars), Error handling (service exceptions mapped to GraphQL errors), Integration tests for all resolvers",
  "acceptance_criteria": "GraphQL query { calendar(id: \"123\") { title events { eventText } } } returns calendar with events, Unauthorized access to other user's calendar returns GraphQL error, createCalendar mutation persists calendar and returns new ID, DataLoader batches queries (e.g., fetching 10 calendars with users requires 2 DB queries, not 11), Integration tests verify end-to-end GraphQL request/response flow",
  "dependencies": ["I2.T2", "I2.T3", "I1.T10"],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: GraphQL API Style (from 04_Behavior_and_Communication.md)

From architecture blueprint line 23:
```markdown
1. **Flexible Data Fetching**: Calendar editor requires nested data structures (User → Calendars → Events, Calendar → Template → Config). GraphQL allows fetching all related data in a single round-trip, eliminating the N+1 query problem common with REST.
```

### Context: DataLoader Pattern for N+1 Prevention (from 05_Operational_Architecture.md)

From architecture blueprint line 411-419:
```markdown
1. **GraphQL DataLoader Pattern**: Batch and cache database queries to prevent N+1 problem

   // Without DataLoader: N+1 queries
   calendars.forEach(cal -> cal.getUser())  // N separate DB queries

   // With DataLoader: 2 queries
   UserDataLoader.loadMany(userIds)  // Single batch query
```

### Context: Design Trade-offs for GraphQL (from 06_Rationale_and_Future.md)

From architecture blueprint line 139:
```markdown
- GraphQL adds complexity (query parsing, N+1 problem mitigation). Team accepted this trade-off for frontend flexibility.
```

From architecture blueprint line 483:
```markdown
- Document resolver implementation patterns (DataLoader usage, N+1 prevention)
```

### Context: Task I2.T6 Full Description (from 02_Iteration_I2.md)

From plan document (iteration 2 tasks):
```markdown
### Task 2.6: Implement Calendar GraphQL Resolvers

**Task ID:** `I2.T6`

**Description:**
Replace stub implementations in CalendarResolver with real service calls. Implement queries: calendar(id) (fetch calendar with events, authorize user), calendars(userId, year) (list user's calendars with pagination). Implement mutations: createCalendar(input) (validate input, call CalendarService), updateCalendar(id, input) (authorize, update), deleteCalendar(id) (authorize, soft/hard delete). Inject SecurityIdentity for user context. Implement DataLoader pattern to prevent N+1 queries when fetching related entities (e.g., calendar with user and events). Add error handling (map service exceptions to GraphQL errors). Write integration tests for all resolver methods.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- CalendarService from Task I2.T2
- EventService from Task I2.T3
- GraphQL schema from I1.T6

**Input Files:**
- `src/main/java/villagecompute/calendar/service/CalendarService.java`
- `src/main/java/villagecompute/calendar/service/EventService.java`
- `src/main/java/villagecompute/calendar/api/graphql/CalendarResolver.java`
- `api/graphql-schema.graphql`

**Target Files:**
- `src/main/java/villagecompute/calendar/api/graphql/CalendarResolver.java`
- `src/main/java/villagecompute/calendar/api/graphql/dataloader/CalendarDataLoader.java`
- `src/test/java/villagecompute/calendar/api/graphql/CalendarResolverTest.java`

**Deliverables:**
- All calendar query/mutation resolvers implemented
- DataLoader pattern implemented for efficient queries
- Authorization checks (user can only query own calendars)
- Error handling (service exceptions mapped to GraphQL errors)
- Integration tests for all resolvers

**Acceptance Criteria:**
- GraphQL query { calendar(id: "123") { title events { eventText } } } returns calendar with events
- Unauthorized access to other user's calendar returns GraphQL error
- createCalendar mutation persists calendar and returns new ID
- DataLoader batches queries (e.g., fetching 10 calendars with users requires 2 DB queries, not 11)
- Integration tests verify end-to-end GraphQL request/response flow

**Dependencies:** `I2.T2`, `I2.T3`, `I1.T10` (requires CalendarService, EventService, and basic GraphQL stubs)

**Parallelizable:** No (depends on completed services)
```

### Context: GraphQL Schema Definition (from api/schema.graphql)

The GraphQL schema defines the following calendar-related operations:

**Queries:**
```graphql
# Get a single calendar by ID (lines 568-575)
calendar(id: ID!): UserCalendar

# Get calendars for a user or filter by year (lines 577-588)
calendars(userId: ID, year: Int): [UserCalendar!]!

# Get authenticated user's calendars (lines 608-612)
myCalendars(year: Int): [UserCalendar!]!

# Get currently authenticated user (lines 595-603)
currentUser: CalendarUser
me: CalendarUser
```

**Mutations:**
```graphql
# Create a new calendar (lines 726-736)
createCalendar(input: CalendarInput!): UserCalendar!

# Update an existing calendar (lines 816-823)
updateCalendar(id: ID!, input: CalendarUpdateInput!): UserCalendar!

# Delete a calendar (lines 767-774)
deleteCalendar(id: ID!): Boolean!

# Convert guest session to authenticated user (lines 720-724)
convertGuestSession(sessionId: ID!): CalendarUser!
```

**Key Types:**
```graphql
# UserCalendar type (lines 195-241) contains:
type UserCalendar {
  id: ID!
  name: String!
  year: Int!
  configuration: JSON
  status: CalendarStatus!
  isPublic: Boolean!
  sessionId: String
  generatedPdfUrl: String
  generatedSvg: String
  created: DateTime!
  updated: DateTime!
  user: CalendarUser     # Potential N+1 query
  template: CalendarTemplate!  # Potential N+1 query
  orders: [CalendarOrder!]!    # Potential N+1 query
}
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `src/main/java/villagecompute/calendar/api/graphql/CalendarGraphQL.java`
    *   **Summary:** This file **ALREADY CONTAINS A SUBSTANTIAL IMPLEMENTATION** of the calendar GraphQL API. It includes:
        - **User Queries:** `me()` and `currentUser()` queries (lines 55-89) that return the authenticated user using `AuthenticationService.getCurrentUser(jwt)`
        - **Calendar Queries:**
          - `myCalendars(year)` (lines 98-128) - fetches user's calendars with optional year filter
          - `calendars(userId, year)` (lines 139-199) - supports both admin access (with userId) and current user access (without userId)
          - `calendar(id)` (lines 208-254) - fetches single calendar with authorization checks (public or owner)
        - **Admin Query:** `allUsers(limit)` (lines 263-283) - requires ADMIN role
        - **Calendar Mutations:**
          - `createCalendar(input)` (lines 296-355) - creates calendar from template with validation
          - `updateCalendar(id, input)` (lines 365-433) - updates calendar with ownership verification
          - `deleteCalendar(id)` (lines 443-508) - deletes calendar with ownership check and order validation
          - `convertGuestSession(sessionId)` (lines 520-554) - **STUB IMPLEMENTATION** that throws UnsupportedOperationException
        - **Authorization:** Uses `@RolesAllowed("USER")` and `@RolesAllowed("ADMIN")` annotations extensively
        - **JWT Integration:** Injects `JsonWebToken jwt` and uses it for authentication context
        - **Service Injection:** Uses `AuthenticationService` and `CalendarTemplateRepository` but **DOES NOT YET INJECT CalendarService or EventService**
    *   **Recommendation:** The task description mentions "Replace stub implementations in CalendarResolver" but I found that CalendarGraphQL (not CalendarResolver) already has most functionality implemented. Your work should focus on:
        1. **CRITICAL:** The file uses direct Panache queries (`UserCalendar.findByUserAndYear()`, `UserCalendar.findByUser()`) instead of calling CalendarService. You MUST refactor these to use CalendarService and EventService for proper separation of concerns.
        2. **Implement DataLoader pattern** - Currently there's no DataLoader implementation, which means fetching related entities (user, template, events) will cause N+1 queries. You need to create `CalendarDataLoader`, `UserDataLoader`, `TemplateDataLoader`, and `EventDataLoader`.
        3. **Complete convertGuestSession** - Currently throws UnsupportedOperationException. Use `CalendarService.convertSessionToUser()` method.
        4. **Add event fetching** - The schema shows UserCalendar should have events, but this isn't currently exposed as a field resolver.

*   **File:** `src/main/java/villagecompute/calendar/services/CalendarService.java`
    *   **Summary:** This service provides all the business logic needed for calendar operations:
        - `createCalendar()` (lines 49-99) - accepts name, year, templateId, configuration, isPublic, user, sessionId
        - `updateCalendar()` (lines 116-153) - handles optimistic locking with version field
        - `deleteCalendar()` (lines 166-185) - hard delete with authorization
        - `getCalendar()` (lines 196-210) - fetches with authorization check
        - `listCalendars()` (lines 223-260) - lists with pagination and authorization
        - `convertSessionToUser()` (lines 270-297) - converts guest session calendars to authenticated user
        - **Authorization helpers:** `checkReadAccess()` (lines 324-349), `checkWriteAccess()` (lines 363-383)
        - **Validation:** `validateCalendarInput()` (lines 396-415)
    *   **Recommendation:** You MUST use this service in CalendarGraphQL instead of direct Panache queries. All authorization logic is already implemented here - reuse it.

*   **File:** `src/main/java/villagecompute/calendar/services/EventService.java`
    *   **Summary:** This service provides event management:
        - `addEvent()` (lines 68-111) - creates event with validation (max 500 chars, date within calendar year, valid emoji/color)
        - `updateEvent()` (lines 128-168) - updates text/emoji/color (cannot change date)
        - `deleteEvent()` (lines 182-201) - deletes with authorization
        - `listEvents()` (lines 216-243) - lists events for calendar with optional date range filter
        - `getEvent()` (lines 254-267) - fetch single event
        - **Bulk operations:** `importEventsFromJson()` (lines 282-341), `importEventsFromCsv()` (lines 356-430)
        - **Validation:** Validates emoji Unicode ranges (lines 520-561), hex color patterns (lines 569-578), 500-char limit (lines 506-512)
    *   **Recommendation:** You SHOULD add EventService to CalendarGraphQL to expose event operations. However, the task description focuses on calendar resolvers, not event CRUD. Events are accessed as nested fields on UserCalendar - use a DataLoader to batch-load events for multiple calendars.

*   **File:** `src/main/java/villagecompute/calendar/data/models/UserCalendar.java`
    *   **Summary:** The UserCalendar entity with Panache provides active record methods:
        - Static finder methods: `findByUser(UUID userId)`, `findByUserAndYear(UUID userId, int year)`, `findBySession(String sessionId)`, `findPublicById(UUID id)`
        - Fields: `id`, `user`, `sessionId`, `name`, `year`, `template`, `configuration` (JSONB), `isPublic`, `status`, `generatedPdfUrl`, `generatedSvg`, `created`, `updated`, `version`, `orders`
    *   **Recommendation:** The CalendarGraphQL currently calls these static methods directly (lines 120-124, 191-196). You MUST replace these with CalendarService calls to maintain proper layering.

*   **File:** `src/main/java/villagecompute/calendar/api/graphql/inputs/CalendarInput.java` and `CalendarUpdateInput.java`
    *   **Summary:** These input DTOs already exist and are used by CalendarGraphQL:
        - `CalendarInput`: name, year, templateId, configuration, isPublic
        - `CalendarUpdateInput`: name (optional), configuration (optional), isPublic (optional)
    *   **Recommendation:** These are already correctly integrated into the mutations. No changes needed.

*   **File:** `src/main/java/villagecompute/calendar/services/AuthenticationService.java`
    *   **Summary:** Already injected and used in CalendarGraphQL (line 40). Provides:
        - `getCurrentUser(JsonWebToken jwt)` - returns `Optional<CalendarUser>` by looking up oauth_subject claim
    *   **Recommendation:** Continue using this service as currently implemented. It correctly handles JWT-to-user mapping.

### Implementation Tips & Notes

*   **Tip:** The task asks for "CalendarResolver" but the actual file is named "CalendarGraphQL.java" (line 32: `public class CalendarGraphQL`). The task description appears to use "Resolver" as a generic term for GraphQL API classes. You should work with the existing CalendarGraphQL.java file.

*   **Tip:** DataLoader pattern in Quarkus with SmallRye GraphQL:
    - You need to create a `@ApplicationScoped` DataLoader class for each entity type that might have N+1 issues
    - Use `@GraphQLApi` annotation and inject the DataLoader into the GraphQL class
    - Example structure for UserDataLoader:
      ```java
      @ApplicationScoped
      public class UserDataLoader {
          @Inject
          CalendarUserRepository userRepository;

          @DataLoader
          public CompletionStage<List<CalendarUser>> loadUsers(List<UUID> userIds) {
              // Batch load all users in single query
              return CompletableFuture.supplyAsync(() ->
                  userRepository.findByIds(userIds)
              );
          }
      }
      ```
    - You need to implement custom finder methods in repositories to support batch loading (e.g., `CalendarUserRepository.findByIds(List<UUID>)`)

*   **Note:** The acceptance criteria states "fetching 10 calendars with users requires 2 DB queries, not 11". This means:
    - Query 1: Fetch 10 calendars (single query)
    - Query 2: Batch fetch all unique users for those calendars (single query using IN clause)
    - WITHOUT DataLoader: 1 query for calendars + 10 separate queries for each calendar's user = 11 queries
    - This same pattern applies to templates, events, and orders

*   **Warning:** The CalendarGraphQL class uses field injection (`@Inject` on fields) rather than constructor injection. Follow the same pattern for consistency:
    ```java
    @Inject
    CalendarService calendarService;

    @Inject
    EventService eventService;
    ```

*   **Warning:** Error handling in GraphQL is different from REST. The task requires "map service exceptions to GraphQL errors". In SmallRye GraphQL:
    - Throwing a standard exception will return a GraphQL error response with the exception message
    - The current code throws `IllegalArgumentException`, `SecurityException`, and `IllegalStateException` - these are automatically converted to GraphQL errors
    - You should continue this pattern and NOT catch exceptions unless you need to transform them

*   **Note:** The task mentions testing should verify "GraphQL query { calendar(id: \"123\") { title events { eventText } } }". However, looking at the schema (line 218), UserCalendar doesn't have a `title` field - it has `name`. Also, UserCalendar doesn't currently have an `events` field in the GraphQL schema, even though the entity has an `events` relationship. You need to add a field resolver for events:
    ```java
    @Query
    public List<Event> events(@Source UserCalendar calendar) {
        return eventService.listEvents(calendar.id, null, null, getCurrentUser());
    }
    ```

*   **Note:** The existing code already handles the major queries and mutations. The PRIMARY work for this task is:
    1. **Refactor to use services** - Replace direct Panache calls with CalendarService/EventService
    2. **Implement DataLoaders** - Create DataLoader classes for User, Template, and Event batching
    3. **Complete guest session conversion** - Implement convertGuestSession using CalendarService.convertSessionToUser()
    4. **Add event field resolver** - Expose events as a field on UserCalendar type
    5. **Write integration tests** - Test end-to-end GraphQL queries with REST Assured or similar

*   **Note:** Integration test structure for GraphQL in Quarkus:
    ```java
    @QuarkusTest
    public class CalendarGraphQLTest {
        @Test
        @TestSecurity(user = "testuser", roles = {"USER"})
        public void testCalendarQuery() {
            given()
                .contentType("application/json")
                .body("{\"query\": \"{ calendar(id: \\\"" + calendarId + "\\\") { name year } }\"}")
            .when()
                .post("/graphql")
            .then()
                .statusCode(200)
                .body("data.calendar.name", equalTo("Test Calendar"));
        }
    }
    ```

*   **Warning:** The GraphQL schema uses `ID!` type for identifiers, but the Java code uses `UUID`. Quarkus GraphQL automatically converts between String and UUID, so your resolvers should accept `String` parameters and convert to `UUID` using `UUID.fromString()` as the existing code does (e.g., line 220, 322, 394).

### Directory Structure Note

The task specifies creating `src/main/java/villagecompute/calendar/api/graphql/dataloader/` directory for DataLoader classes. This directory does not currently exist. You will need to create it.

The test file `src/test/java/villagecompute/calendar/api/graphql/CalendarResolverTest.java` should actually be `CalendarGraphQLTest.java` to match the actual class name.

### Summary of What You Need to Do

1. **Refactor CalendarGraphQL to use services** - Replace direct Panache queries with CalendarService and EventService calls
2. **Inject CalendarService and EventService** - Add these as @Inject fields
3. **Implement DataLoader classes:**
   - `UserDataLoader` - batch load CalendarUser entities
   - `TemplateDataLoader` - batch load CalendarTemplate entities
   - `EventDataLoader` - batch load Event entities for multiple calendars
4. **Add repository batch methods** - Implement `findByIds(List<UUID>)` in CalendarUserRepository, CalendarTemplateRepository, and EventRepository to support DataLoaders
5. **Complete convertGuestSession mutation** - Replace stub with call to `calendarService.convertSessionToUser()`
6. **Add event field resolver** - Expose `events` as a GraphQL field on UserCalendar type using EventService
7. **Write comprehensive integration tests** - Test all queries and mutations with REST Assured, verify DataLoader batching works (check query count), test authorization failures, test validation failures

Good luck with the implementation!

---

**END OF BRIEFING PACKAGE**
