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

### Context: API Style and GraphQL Implementation (from 04_Behavior_and_Communication.md)

```markdown
#### 3.7.1. API Style

**Primary API: GraphQL**

The Village Calendar application uses **GraphQL** as its primary API protocol for frontend-to-backend communication. This choice is driven by the complex, nested data requirements of the calendar editor interface.

**Rationale for GraphQL:**

1. **Flexible Data Fetching**: Calendar editor requires nested data structures (User → Calendars → Events, Calendar → Template → Config). GraphQL allows fetching all related data in a single round-trip, eliminating the N+1 query problem common with REST.

2. **Reduced Over-fetching**: Frontend can request exactly the fields needed for each view (e.g., calendar list view only needs `id`, `title`, `preview_image_url`, while editor needs full `config`, `events[]`). This reduces payload size and improves performance on mobile connections.

3. **Schema Evolution**: GraphQL's strong typing and introspection enable adding new fields without versioning. Deprecated fields can be marked and gracefully removed over time.

4. **Developer Experience**: GraphQL Playground (auto-generated from schema) provides interactive API documentation and testing interface. Frontend developers can explore schema without reading separate docs.

5. **Type Safety**: SmallRye GraphQL generates TypeScript types for Vue.js frontend, ensuring compile-time type checking across the API boundary.

**GraphQL Schema Organization:**

- **Queries**: Read operations (e.g., `calendar(id)`, `calendars(userId)`, `templates()`, `order(orderId)`)
- **Mutations**: Write operations (e.g., `createCalendar`, `updateCalendar`, `placeOrder`, `generatePdf`)
- **Subscriptions**: Not implemented in MVP (future: real-time collaboration notifications)
```

### Context: Communication Patterns and DataLoader Strategy (from 04_Behavior_and_Communication.md)

```markdown
#### 3.7.2. Communication Patterns

**1. Synchronous Request/Response (GraphQL/REST over HTTPS)**

Used for operations requiring immediate feedback to the user:

- **Read Operations**: Fetching calendars, templates, orders (GraphQL queries)
- **Lightweight Writes**: Creating/updating calendar metadata (GraphQL mutations)
- **Authentication Flows**: OAuth redirects, token validation
- **Payment Initiation**: Creating Stripe checkout sessions

**Characteristics:**
- Client waits for server response (typically <500ms)
- Transactional consistency (database transaction commits before response)
- Error handling via HTTP status codes and GraphQL error extensions
- Retry logic in frontend for network failures
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `src/main/java/villagecompute/calendar/services/CalendarService.java`
    *   **Summary:** Fully implemented business logic service with complete CRUD operations for calendars. Includes authorization checks, optimistic locking, session-to-user conversion, and validation helpers.
    *   **Key Methods:** `createCalendar()`, `updateCalendar()`, `deleteCalendar()`, `getCalendar()`, `listCalendars()`, `convertSessionToUser()`
    *   **Authorization:** Contains `checkReadAccess()` and `checkWriteAccess()` methods that enforce RBAC (admin, owner, public access rules)
    *   **Recommendation:** You MUST import and use this service. All calendar operations should delegate to CalendarService methods. DO NOT reimplement authorization logic in the resolver - the service handles all authorization.

*   **File:** `src/main/java/villagecompute/calendar/services/EventService.java`
    *   **Summary:** Fully implemented event management service with CRUD operations, validation (date within year, text max 500 chars, emoji validation), and bulk import from JSON/CSV.
    *   **Key Methods:** `addEvent()`, `updateEvent()`, `deleteEvent()`, `listEvents()`, `importEventsFromJson()`, `importEventsFromCsv()`
    *   **Recommendation:** You SHOULD use EventService for any event-related operations. The service handles all validation and authorization through CalendarService.

*   **File:** `src/main/java/villagecompute/calendar/api/graphql/CalendarGraphQL.java`
    *   **Summary:** This is your PRIMARY file to work with. It's a partially implemented GraphQL resolver that already has WORKING implementations for most queries and mutations including: `me()`, `currentUser()`, `myCalendars()`, `calendars()`, `calendar()`, `allUsers()`, `createCalendar()`, `updateCalendar()`, `deleteCalendar()`, `convertGuestSession()`
    *   **Current State:** The file is NOT a stub - it has complete implementations that use CalendarService and EventService correctly. The task description says "replace stub implementations" but this file is ALREADY PRODUCTION-READY.
    *   **What's Working:**
        - JWT injection via `@Inject JsonWebToken jwt`
        - AuthenticationService integration for user context
        - All CRUD mutations delegate to CalendarService
        - Authorization checks properly implemented
        - Error handling with meaningful messages
    *   **What Needs DataLoader:** The current implementation does NOT have DataLoader pattern implemented. When fetching multiple calendars with related entities (events, template, user), this will cause N+1 query problems.
    *   **Recommendation:** Your MAIN task is to ADD DataLoader support to this existing, working file. DO NOT rewrite the existing logic - only enhance it with DataLoader for efficient batch loading.

*   **File:** `api/schema.graphql`
    *   **Summary:** Complete GraphQL schema with all types, queries, and mutations defined. This is the contract you must implement.
    *   **Key Types:** CalendarUser, UserCalendar, CalendarTemplate, CalendarOrder, Event (embedded in configuration JSONB)
    *   **Note:** Events are NOT a separate GraphQL type in the schema - they are embedded in the `UserCalendar.configuration` JSON field. The task description mentions "calendar with events" but the actual schema stores events in the configuration JSONB.
    *   **Recommendation:** Review the schema carefully. The DataLoader should focus on efficiently loading `UserCalendar.user`, `UserCalendar.template`, and `UserCalendar.orders` relationships, NOT individual events (since events are embedded in JSON).

*   **File:** `src/main/java/villagecompute/calendar/data/models/UserCalendar.java`
    *   **Summary:** JPA entity with relationships to User, Template, and Orders. Has JSONB `configuration` field that stores events.
    *   **Relationships:**
        - `@ManyToOne user` (many calendars belong to one user)
        - `@ManyToOne template` (many calendars based on one template)
        - `@OneToMany orders` (one calendar can have many orders)
    *   **Recommendation:** DataLoader should batch-load these relationships when multiple calendars are fetched.

*   **File:** `src/main/java/villagecompute/calendar/api/graphql/dataloader/EventDataLoader.java` (EXISTS)
    *   **Summary:** I found existing DataLoader implementations in the codebase! There's already an `EventDataLoader`, `TemplateDataLoader`, and `UserDataLoader` in the dataloader package.
    *   **Recommendation:** You SHOULD review these existing DataLoader classes as reference implementations. Follow the same pattern for any calendar-related batching.

### Implementation Tips & Notes

*   **Tip 1: DataLoader Pattern in Quarkus**
    - Quarkus uses SmallRye GraphQL which does NOT have built-in DataLoader support like graphql-java does.
    - The existing DataLoader implementations in the codebase show the project's approach: they appear to be custom implementations using batch loading logic.
    - You MUST examine the existing `EventDataLoader.java`, `TemplateDataLoader.java`, and `UserDataLoader.java` to understand the project's DataLoader pattern before implementing yours.

*   **Tip 2: N+1 Problem Locations**
    - **Main Issue:** When `calendars()` query fetches 10 calendars and the client requests `{ calendars { id name user { email } template { name } } }`, the current implementation will:
        1. Execute 1 query for calendars (SELECT * FROM calendars WHERE user_id = ?)
        2. Execute 10 queries for users (SELECT * FROM users WHERE id = ?) for each calendar's user
        3. Execute 10 queries for templates (SELECT * FROM templates WHERE id = ?) for each calendar's template
    - **Solution:** Implement DataLoader to batch these queries: 1 for calendars, 1 for all users, 1 for all templates = 3 total queries.

*   **Tip 3: Error Handling Pattern**
    - The existing CalendarGraphQL.java shows good error handling: it catches `IllegalArgumentException` for "not found" errors and `SecurityException` for authorization failures.
    - It returns `null` for queries (not found) and throws exceptions for mutations (validation errors).
    - You SHOULD maintain this pattern when adding DataLoader support.

*   **Tip 4: Integration Tests**
    - The task requires integration tests for resolver methods.
    - Quarkus provides `@QuarkusTest` annotation and REST Assured for GraphQL API testing.
    - You MUST test the DataLoader batching behavior: verify that fetching N calendars with users produces only 2 DB queries (1 for calendars, 1 for users).
    - Use Hibernate SQL logging to verify query count: `quarkus.hibernate-orm.log.sql=true` in test properties.

*   **Warning: GraphQL Schema vs Task Description Mismatch**
    - The task description says "fetch calendar with events" but the actual GraphQL schema (api/schema.graphql) does NOT define Event as a separate GraphQL type.
    - Events are embedded in `UserCalendar.configuration` JSON field.
    - The existing CalendarGraphQL.java correctly returns UserCalendar objects with embedded configuration.
    - DO NOT create a separate Event GraphQL type - follow the existing schema design.

*   **Note: Authorization Already Complete**
    - CalendarService already implements comprehensive authorization: `checkReadAccess()` and `checkWriteAccess()` with rules for admin, owner, public calendars.
    - The existing CalendarGraphQL.java already delegates to CalendarService for all operations.
    - You DO NOT need to add new authorization logic - it's already fully implemented and tested.

*   **Note: Existing Methods Are NOT Stubs**
    - Despite the task description saying "replace stub implementations", the CalendarGraphQL.java file contains COMPLETE, WORKING implementations.
    - Methods like `createCalendar()`, `updateCalendar()`, `calendar()`, etc. are fully functional with proper service delegation, authorization, and error handling.
    - Your task is to ENHANCE the existing code with DataLoader support, NOT to rewrite it from scratch.

### Critical Implementation Path

1. **FIRST:** Examine the existing DataLoader implementations (`EventDataLoader.java`, `TemplateDataLoader.java`, `UserDataLoader.java`) to understand the project's batching pattern.

2. **SECOND:** Create `CalendarDataLoader.java` following the same pattern. It should batch-load:
    - Users for calendars (Map<UUID calendarId, CalendarUser>)
    - Templates for calendars (Map<UUID calendarId, CalendarTemplate>)
    - Potentially Orders for calendars (Map<UUID calendarId, List<CalendarOrder>>)

3. **THIRD:** Integrate DataLoader into the EXISTING CalendarGraphQL.java resolver methods:
    - Add DataLoader injection
    - Modify queries to use DataLoader when fetching related entities
    - Ensure backward compatibility - do NOT break existing functionality

4. **FOURTH:** Write integration tests that verify:
    - Existing functionality still works (all CRUD operations)
    - DataLoader reduces query count (1 query for calendars + 1 for users instead of N+1)
    - Authorization still enforced correctly
    - Error handling unchanged

5. **FIFTH:** Test with actual GraphQL queries in GraphQL Playground to ensure the batching works end-to-end.

### Summary

**What Already Works:**
- Complete CalendarService with authorization, validation, and CRUD operations
- Complete EventService with validation and bulk imports
- Fully functional CalendarGraphQL resolver with all queries and mutations
- JWT authentication and user context injection
- Error handling and security checks

**What You Need to Add:**
- DataLoader pattern implementation for batch loading related entities
- CalendarDataLoader class following existing project patterns
- Integration tests verifying DataLoader batching behavior
- Documentation of DataLoader usage

**What You Should NOT Do:**
- Rewrite existing working resolver methods
- Add new authorization logic (it's already complete in CalendarService)
- Change the GraphQL schema (it's already finalized)
- Create separate Event GraphQL type (events are embedded in JSON)
