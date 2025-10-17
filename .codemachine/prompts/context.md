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
  "dependencies": [
    "I2.T2",
    "I2.T3",
    "I1.T10"
  ],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents.

### Context: GraphQL API Design Philosophy

**Primary API: GraphQL** - The Village Calendar application uses GraphQL as its primary API protocol for frontend-to-backend communication, chosen for its complex, nested data requirements of the calendar editor interface.

**Rationale for GraphQL:**
1. **Flexible Data Fetching**: Calendar editor requires nested data (User → Calendars → Events, Calendar → Template → Config). GraphQL allows fetching all related data in a single round-trip, eliminating N+1 query problems.
2. **Reduced Over-fetching**: Frontend can request exactly the fields needed (calendar list view needs only `id`, `title`, `preview_image_url`, while editor needs full `config`, `events[]`).
3. **Schema Evolution**: Strong typing and introspection enable adding new fields without versioning.
4. **Type Safety**: SmallRye GraphQL generates TypeScript types for Vue.js frontend, ensuring compile-time type checking across the API boundary.

**GraphQL Schema Organization:**
- **Queries**: Read operations (`calendar(id)`, `calendars(userId)`, `templates()`, `order(orderId)`)
- **Mutations**: Write operations (`createCalendar`, `updateCalendar`, `placeOrder`, `generatePdf`)
- **Subscriptions**: Not implemented in MVP (future: real-time collaboration)

### Context: DataLoader Pattern for N+1 Prevention

**The N+1 Query Problem:**
When fetching multiple calendars with related entities without DataLoader:
```
SELECT * FROM user_calendar WHERE user_id = ? (1 query)
For each calendar:
    SELECT * FROM calendar_user WHERE id = ? (10 queries for 10 calendars)
    SELECT * FROM calendar_template WHERE id = ? (10 queries)
    SELECT * FROM event WHERE calendar_id = ? (10 queries)
Total: 1 + 10 + 10 + 10 = 31 queries
```

**With DataLoader (batched queries):**
```
SELECT * FROM user_calendar WHERE user_id = ? (1 query)
SELECT * FROM calendar_user WHERE id IN (?,?,?,...) (1 batched query)
SELECT * FROM calendar_template WHERE id IN (?,?,?,...) (1 batched query)
SELECT * FROM event WHERE calendar_id IN (?,?,?,...) (1 batched query)
Total: 4 queries
```

**DataLoader Implementation Contract:**
1. Create `DataLoader<K, V>` instances using `DataLoaderFactory.newDataLoader(batchLoader)`
2. Batch function receives `List<K>` keys and returns `CompletionStage<List<V>>` values
3. Results MUST be in same order as input keys (DataLoader contract requirement)
4. Return empty/null for missing entities (e.g., calendar with no events returns empty list)
5. DataLoader batches all requests within a single GraphQL execution context

### Context: Authorization Model

**Role-Based Access Control (RBAC):**
- Uses JWT tokens in `Authorization` header
- Roles: `USER` (authenticated users), `ADMIN` (administrators)
- `@RolesAllowed("USER")` annotation requires authentication
- `@PermitAll` allows anonymous access (for public calendars)

**Calendar Access Rules:**
- **Read Access**: Public calendars accessible to anyone. Private calendars require ownership or admin role.
- **Write Access**: Only owner (or admin) can modify/delete calendars.
- **Guest Sessions**: Calendars created by anonymous users have `sessionId` instead of `user`. These can be converted to user-owned calendars after authentication via `convertGuestSession` mutation.

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase.

### Relevant Existing Code

*   **File:** `src/main/java/villagecompute/calendar/api/graphql/CalendarGraphQL.java`
    *   **Summary:** **THIS IS YOUR PRIMARY FILE.** This is NOT a stub - it contains COMPLETE, PRODUCTION-READY GraphQL resolver implementations for all calendar queries and mutations.
    *   **Current Working Implementation:**
        - Queries: `me()`, `currentUser()`, `myCalendars(year)`, `calendars(userId, year)`, `calendar(id)`, `allUsers(limit)`
        - Mutations: `createCalendar(input)`, `updateCalendar(id, input)`, `deleteCalendar(id)`, `convertGuestSession(sessionId)`
        - Authentication: JWT injection via `@Inject JsonWebToken jwt`
        - Service integration: Properly delegates to `CalendarService` and `EventService`
        - Error handling: Catches `IllegalArgumentException` (not found) and `SecurityException` (unauthorized), returns null or throws appropriately
        - Authorization: Uses `@RolesAllowed("USER")` and `@RolesAllowed("ADMIN")` correctly
    *   **What's Missing:** DataLoader integration. The file injects `UserDataLoader`, `TemplateDataLoader`, and `EventDataLoader` but does NOT use them anywhere. This causes N+1 query problems when fetching calendars with nested entities.
    *   **Recommendation:** Your main task is to ADD DataLoader support to this existing, working file. You need to add `@Source` annotated field resolver methods that use the injected DataLoaders. DO NOT rewrite the existing methods - they work perfectly.
    *   **Pattern to Follow:** Add field resolver methods like:
        ```java
        @Name("user")  // Resolves the UserCalendar.user field
        public CompletionStage<CalendarUser> user(@Source UserCalendar calendar) {
            return userDataLoader.createDataLoader().load(calendar.user.id);
        }
        ```

*   **File:** `src/main/java/villagecompute/calendar/services/CalendarService.java`
    *   **Summary:** Fully implemented business logic service with complete CRUD operations, authorization checks (via `checkReadAccess()` and `checkWriteAccess()` helper methods), optimistic locking (JPA versioning), session-to-user conversion, and comprehensive validation.
    *   **Key Methods:**
        - `createCalendar(name, year, templateId, configuration, isPublic, user, sessionId)` - Creates new calendar, validates input, handles guest sessions
        - `updateCalendar(id, name, configuration, isPublic, currentUser)` - Updates calendar with optimistic locking, throws `OptimisticLockException` on version mismatch
        - `deleteCalendar(id, currentUser)` - Hard deletes calendar after authorization check
        - `getCalendar(id, currentUser)` - Fetches calendar with read authorization check
        - `listCalendars(userId, year, pageIndex, pageSize, currentUser)` - Lists calendars with pagination
        - `convertSessionToUser(sessionId, user)` - Migrates guest calendars to authenticated user
    *   **Authorization Rules Implemented:**
        - Read: Public calendars accessible to all, private calendars only to owner/admin
        - Write: Only owner or admin can modify/delete
        - Admin: Admin role can access all calendars regardless of ownership
    *   **Exceptions Thrown:**
        - `IllegalArgumentException` for validation failures (invalid ID, missing fields, year out of range)
        - `SecurityException` for authorization failures (access denied)
        - `OptimisticLockException` for concurrent update conflicts (version mismatch)
    *   **Recommendation:** You MUST use this service for ALL calendar operations in your GraphQL resolver. Never access repositories directly. The service is complete and handles all authorization - DO NOT reimplement authorization logic in the resolver. Map service exceptions to GraphQL errors in the resolver.

*   **File:** `src/main/java/villagecompute/calendar/services/EventService.java`
    *   **Summary:** Complete event management service with CRUD operations, comprehensive validation (event date within calendar year, text max 500 chars, emoji validation, hex color validation), and bulk import capabilities (JSON/CSV).
    *   **Key Methods:**
        - `addEvent(calendarId, eventDate, eventText, emoji, color, currentUser)` - Adds event with full validation
        - `updateEvent(eventId, eventText, emoji, color, currentUser)` - Updates event (cannot change date)
        - `deleteEvent(eventId, currentUser)` - Deletes event
        - `listEvents(calendarId, startDate, endDate, currentUser)` - Lists events with optional date range filter
        - `importEventsFromJson(calendarId, jsonData, currentUser)` - Bulk import from JSON array
        - `importEventsFromCsv(calendarId, csvData, currentUser)` - Bulk import from CSV file
    *   **Validation Logic:**
        - Event date must be within calendar year (e.g., 2025 calendar only allows 2025 dates)
        - Event text max 500 characters
        - Emoji must be valid Unicode emoji sequence (checks common emoji ranges: 0x1F300-0x1F9FF, 0x2600-0x27BF, etc.)
        - Color must be hex format: `#FF5733` (6 digits) or `#ABC` (3 digits)
    *   **Recommendation:** Use EventService for any event operations. The service handles all validation and delegates to CalendarService for authorization checks. Note that the current GraphQL schema does NOT have separate Event queries/mutations exposed - events are accessed via the `UserCalendar.events` field.

*   **File:** `api/schema.graphql`
    *   **Summary:** Complete GraphQL schema definition with all types, queries, mutations, enums, and scalars. This is the contract your resolver must implement.
    *   **Key Types:**
        - `UserCalendar`: id, name, year, configuration (JSON), status, isPublic, generatedPdfUrl, generatedSvg, created, updated, user, template, orders
        - `CalendarUser`: id, email, displayName, oauthProvider, oauthSubject, profileImageUrl, lastLoginAt, created, updated, calendars, orders
        - `CalendarTemplate`: id, name, description, configuration (JSON), thumbnailUrl, previewSvg, isActive, isFeatured, displayOrder, created, updated, userCalendars
        - `Event`: eventDate, eventText, emoji, color (embedded in Event table, separate entity)
        - `CalendarOrder`: Order entity with Stripe integration
    *   **Critical Finding:** The schema DOES define Event as a separate type, and the database has an `events` table (migration `006_create_events_table.sql`). Events are NOT embedded in the configuration JSONB as I initially thought. They are stored in a separate table with a foreign key to `user_calendar`.
    *   **Missing Field:** The `UserCalendar` type in the schema does NOT currently have an `events: [Event!]!` field. You MUST ADD this field to the schema before implementing the field resolver.
    *   **Recommendation:**
        1. First, add `events: [Event!]!` field to the `UserCalendar` type in `api/schema.graphql`
        2. Then implement a field resolver method in `CalendarGraphQL.java` that uses `EventDataLoader` to batch-fetch events

*   **File:** `src/main/java/villagecompute/calendar/api/graphql/dataloader/EventDataLoader.java`
    *   **Summary:** Existing DataLoader implementation for batch loading events for multiple calendars. This demonstrates the exact pattern you should follow.
    *   **Implementation Pattern:**
        ```java
        public DataLoader<UUID, List<Event>> createDataLoader() {
            return DataLoaderFactory.newDataLoader(this::batchLoadEvents);
        }

        private CompletionStage<List<List<Event>>> batchLoadEvents(List<UUID> calendarIds) {
            return CompletableFuture.supplyAsync(() -> {
                // Fetch all events in a single query
                List<Event> events = eventRepository.findByCalendarIds(calendarIds);

                // Group events by calendar ID
                Map<UUID, List<Event>> eventsByCalendar = events.stream()
                    .collect(Collectors.groupingBy(event -> event.calendar.id));

                // Return in same order as input (DataLoader contract)
                return calendarIds.stream()
                    .map(calendarId -> eventsByCalendar.getOrDefault(calendarId, List.of()))
                    .collect(Collectors.toList());
            });
        }
        ```
    *   **Recommendation:** Follow this exact pattern when implementing field resolvers in `CalendarGraphQL.java`. The DataLoader is injected, call `createDataLoader()` to get a DataLoader instance, then use `.load(key)` to enqueue batch requests.

*   **File:** `src/main/java/villagecompute/calendar/api/graphql/dataloader/UserDataLoader.java` and `TemplateDataLoader.java`
    *   **Summary:** Similar DataLoader implementations exist for batching user and template queries.
    *   **Recommendation:** These are ready to use. You just need to add field resolver methods in `CalendarGraphQL.java` that call these DataLoaders when the `UserCalendar.user` or `UserCalendar.template` fields are requested.

*   **File:** `src/main/java/villagecompute/calendar/data/repositories/EventRepository.java`
    *   **Summary:** Panache repository with custom query methods including `findByCalendarIds(List<UUID>)` which is used by EventDataLoader for batching.
    *   **Recommendation:** You do NOT need to modify this file. The repository methods are complete and used by the DataLoaders.

### Implementation Tips & Notes

*   **Tip #1: The Task Description is Misleading**
    - The task says "Replace stub implementations in CalendarResolver" but `CalendarGraphQL.java` is NOT a stub - it's fully implemented
    - The file name in the task is wrong too: task says "CalendarResolver.java" but the actual file is "CalendarGraphQL.java"
    - **Your Real Task:** Add DataLoader integration to the existing, working CalendarGraphQL.java file
    - **What You Should NOT Do:** Rewrite the existing methods - they already work perfectly

*   **Tip #2: How to Add DataLoader Support**
    - SmallRye GraphQL (Quarkus' GraphQL implementation) automatically calls field resolver methods when a client requests nested fields
    - You need to add `@Source` annotated methods for each nested field:
      ```java
      @Name("events")
      public CompletionStage<List<Event>> events(@Source UserCalendar calendar) {
          return eventDataLoader.createDataLoader().load(calendar.id);
      }

      @Name("user")
      public CompletionStage<CalendarUser> user(@Source UserCalendar calendar) {
          if (calendar.user == null) {
              return CompletableFuture.completedFuture(null);
          }
          return userDataLoader.createDataLoader().load(calendar.user.id);
      }

      @Name("template")
      public CompletionStage<CalendarTemplate> template(@Source UserCalendar calendar) {
          return templateDataLoader.createDataLoader().load(calendar.template.id);
      }
      ```
    - These methods will be called automatically by SmallRye GraphQL when the client queries these fields

*   **Tip #3: GraphQL Schema Must Be Updated First**
    - The current `api/schema.graphql` does NOT have `events` field on `UserCalendar` type
    - You MUST add this line to the schema:
      ```graphql
      type UserCalendar {
        # ... existing fields ...
        """Custom events added to this calendar by the user"""
        events: [Event!]!
        # ... rest of fields ...
      }
      ```
    - Without this schema update, your field resolver will not be called

*   **Tip #4: Integration Testing Strategy**
    - Create `src/test/java/villagecompute/calendar/api/graphql/CalendarGraphQLTest.java`
    - Use `@QuarkusTest` annotation for integration tests with real database
    - Use `@TestSecurity(user = "testuser", roles = {"USER"})` to simulate authenticated users
    - Send GraphQL queries via HTTP POST to `/graphql` using REST Assured
    - Example test structure:
      ```java
      @QuarkusTest
      public class CalendarGraphQLTest {
          @Test
          @TestSecurity(user = "testuser", roles = {"USER"})
          public void testFetchCalendarWithEvents() {
              String query = """
                  query {
                      calendar(id: "...") {
                          id
                          name
                          events {
                              eventText
                              eventDate
                          }
                      }
                  }
              """;

              given()
                  .contentType("application/json")
                  .body(Map.of("query", query))
                  .when()
                  .post("/graphql")
                  .then()
                  .statusCode(200)
                  .body("data.calendar.events", hasSize(greaterThan(0)));
          }
      }
      ```

*   **Tip #5: Verify DataLoader Batching Works**
    - To verify DataLoader batching reduces query count, enable SQL logging:
      ```properties
      # Add to application-test.properties
      quarkus.hibernate-orm.log.sql=true
      quarkus.hibernate-orm.log.bind-parameters=true
      ```
    - Write a test that fetches 10 calendars with events and count the SELECT queries
    - Without DataLoader: 1 (calendars) + 10 (events) = 11 queries
    - With DataLoader: 1 (calendars) + 1 (batched events) = 2 queries
    - Assert on query count or manually verify in logs

*   **Note #6: Error Handling Pattern to Follow**
    - The existing `CalendarGraphQL.java` methods show the correct error handling pattern:
      ```java
      try {
          UserCalendar calendar = calendarService.getCalendar(calendarId, currentUser);
          return calendar;
      } catch (IllegalArgumentException e) {
          LOG.warnf("Calendar not found: %s", id);
          return null;  // GraphQL returns null for not found
      } catch (SecurityException e) {
          LOG.warnf("Access denied: %s", e.getMessage());
          return null;  // Or throw to return error in GraphQL response
      }
      ```
    - Follow this pattern in your field resolvers if needed

*   **Warning #7: CompletionStage Return Types**
    - DataLoader methods return `CompletionStage<T>` (asynchronous)
    - Field resolver methods must return `CompletionStage<T>` to integrate with DataLoader
    - SmallRye GraphQL handles the async execution automatically
    - Do NOT call `.join()` or `.get()` to block - this defeats the batching optimization

*   **Critical #8: Existing Code is Production-Ready**
    - The CalendarGraphQL.java file has complete implementations that:
      - ✅ Use CalendarService correctly for all operations
      - ✅ Handle authentication via JWT
      - ✅ Enforce authorization with @RolesAllowed
      - ✅ Catch and handle service exceptions properly
      - ✅ Support guest sessions and session conversion
      - ✅ Validate input and return meaningful errors
    - Your task is to ENHANCE this code with DataLoader, not replace it

*   **Note #9: Target File Name Discrepancy**
    - Task says target file is "CalendarResolver.java" but actual file is "CalendarGraphQL.java"
    - Similarly for test file: actual will be "CalendarGraphQLTest.java" not "CalendarResolverTest.java"
    - Follow the existing naming convention in the codebase

---

## 4. Implementation Checklist

Based on my analysis, here's what you need to do:

### ✅ Phase 1: Schema Update
- [ ] Add `events: [Event!]!` field to `UserCalendar` type in `api/schema.graphql`
- [ ] Ensure `Event` type is properly defined in schema with all fields (it should already be)
- [ ] Verify schema compiles without errors

### ✅ Phase 2: Add DataLoader Field Resolvers to CalendarGraphQL.java
- [ ] Add `@Name("events")` field resolver method that uses `EventDataLoader`
- [ ] Add `@Name("user")` field resolver method that uses `UserDataLoader` (handles null user for guest sessions)
- [ ] Add `@Name("template")` field resolver method that uses `TemplateDataLoader`
- [ ] Optionally add `@Name("orders")` field resolver if you want to batch-load orders too
- [ ] Ensure all field resolvers return `CompletionStage<T>` (async)
- [ ] Handle null cases (e.g., guest calendars have null user)

### ✅ Phase 3: Write Integration Tests
- [ ] Create `CalendarGraphQLTest.java` in `src/test/java/.../api/graphql/`
- [ ] Test: `calendar(id)` query returns calendar with events
- [ ] Test: `calendars()` query returns multiple calendars with nested data
- [ ] Test: Unauthorized access returns null or error
- [ ] Test: DataLoader batching works (verify SQL query count in logs)
- [ ] Test: All existing mutations still work (createCalendar, updateCalendar, deleteCalendar)
- [ ] Test: Guest session conversion still works

### ✅ Phase 4: Verification
- [ ] Run all tests: `./mvnw test`
- [ ] Start Quarkus in dev mode: `./mvnw quarkus:dev`
- [ ] Test queries in GraphQL UI at http://localhost:8080/graphql-ui
- [ ] Verify query: `{ calendar(id: "...") { name events { eventText } } }` returns events
- [ ] Check logs for SQL queries - should see batched queries with IN clauses
- [ ] Verify acceptance criteria all met

---

## 5. Final Recommendations

**DO:**
- ✅ Add field resolver methods to the existing `CalendarGraphQL.java` file
- ✅ Use the already-injected DataLoader instances (`eventDataLoader`, `userDataLoader`, `templateDataLoader`)
- ✅ Follow the exact pattern shown in `EventDataLoader.java`
- ✅ Update the GraphQL schema to add the `events` field
- ✅ Write comprehensive integration tests
- ✅ Verify DataLoader batching reduces query count

**DO NOT:**
- ❌ Rewrite the existing resolver methods - they already work
- ❌ Reimplementauthorization logic - CalendarService handles it
- ❌ Access repositories directly - always use the services
- ❌ Block on CompletionStage (no `.join()` or `.get()`)
- ❌ Change the existing error handling pattern
- ❌ Modify the GraphQL schema types (except adding the events field)

**ACCEPTANCE CRITERIA MAPPING:**
1. ✅ "GraphQL query { calendar(id: \"123\") { title events { eventText } } } returns calendar with events"
   - Requires: Schema update + events field resolver
2. ✅ "Unauthorized access to other user's calendar returns GraphQL error"
   - Already implemented in CalendarService.checkReadAccess()
3. ✅ "createCalendar mutation persists calendar and returns new ID"
   - Already implemented in CalendarGraphQL.createCalendar()
4. ✅ "DataLoader batches queries (10 calendars with users = 2 DB queries, not 11)"
   - Requires: Field resolvers for user, template, events
5. ✅ "Integration tests verify end-to-end GraphQL request/response flow"
   - Requires: CalendarGraphQLTest.java with REST Assured tests
