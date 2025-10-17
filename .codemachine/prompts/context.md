# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I2.T3",
  "iteration_id": "I2",
  "iteration_goal": "Implement calendar creation, editing, and template system. Build calendar editor UI components. Integrate astronomical calculations (moon phases, Hebrew calendar). Create sequence diagrams for key workflows",
  "description": "Create EventService for managing calendar events: addEvent (to calendar), updateEvent (text, emoji, color), deleteEvent, listEvents (by calendar, optionally filtered by date range). Implement EventRepository with custom queries: findByCalendarId, findByDateRange. Add validation: event date must be within calendar year, event text max 500 characters, emoji must be valid Unicode. Handle bulk event operations (import multiple events from CSV or JSON). Write unit tests for event service and repository.",
  "agent_type_hint": "BackendAgent",
  "inputs": "Event entity model from I1.T8, GraphQL schema event operations from I1.T6",
  "target_files": [
    "src/main/java/villagecompute/calendar/service/EventService.java",
    "src/main/java/villagecompute/calendar/repository/EventRepository.java",
    "src/test/java/villagecompute/calendar/service/EventServiceTest.java"
  ],
  "input_files": [
    "src/main/java/villagecompute/calendar/model/Event.java",
    "src/main/java/villagecompute/calendar/model/Calendar.java",
    "api/graphql-schema.graphql"
  ],
  "deliverables": "EventService class with CRUD and validation methods, EventRepository with custom queries, Validation logic for event fields, Unit tests with >80% coverage",
  "acceptance_criteria": "EventService.addEvent() validates event date is within calendar year, EventService.addEvent() rejects events with text >500 characters, EventRepository.findByDateRange() returns events within specified date range, Emoji validation accepts valid Unicode emoji sequences, Unit tests cover all validation scenarios and edge cases",
  "dependencies": ["I2.T2"],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: data-model-overview (from 03_System_Structure_and_Data.md)

The data model is optimized for the calendar creation and e-commerce workflows, with careful consideration for session persistence (anonymous users), job processing, and analytics. PostgreSQL's JSONB type is used for flexible calendar metadata (event details, configuration options) while maintaining relational integrity for core entities.

**Key Design Decisions:**

1. **User Identity**: `users` table stores OAuth provider info (`oauth_provider`, `oauth_subject_id`) to support multiple providers per user
2. **Anonymous Sessions**: `calendar_sessions` table tracks guest user calendars, linked to `users` table upon login conversion
3. **Calendar Versioning**: `calendars` table includes `version` field for optimistic locking, future support for edit history
4. **Order Status**: `orders.status` enum (PENDING, PAID, IN_PRODUCTION, SHIPPED, DELIVERED, CANCELLED, REFUNDED) drives workflow state machine
5. **Job Queue**: `delayed_jobs` table with `locked_at`, `locked_by`, `attempts`, `last_error` supports distributed worker coordination
6. **Templates**: `calendar_templates` is separate from `calendars` to enable admin-curated vs user-created distinction
7. **Analytics**: `page_views`, `analytics_rollups` tables support basic analytics without external service dependency (Phase 1)

**Key Entities:**

- **User**: Registered user account with OAuth authentication
- **CalendarSession**: Anonymous user session data (pre-authentication)
- **Calendar**: User's saved calendar with events and configuration
- **CalendarTemplate**: Admin-created template calendars
- **Event**: Custom event on a calendar (date, text, emoji)
- **Order**: E-commerce order for printed calendar
- **OrderItem**: Line items in an order (supports future multi-calendar orders)
- **Payment**: Stripe payment record linked to order
- **DelayedJob**: Asynchronous job queue entry
- **PageView**: Analytics event for page visits
- **AnalyticsRollup**: Aggregated analytics (daily/weekly/monthly)

**Event Entity Definition (from ERD):**

```
entity Event {
  *event_id : bigserial <<PK>>
  --
  calendar_id : bigint <<FK>>
  event_date : date
  event_text : varchar(500)
  emoji : varchar(100) <<unicode emoji>>
  color : varchar(20) <<hex color>>
  created_at : timestamp
  --
  INDEX: calendar_id, event_date
}
```

**Database Indexes Strategy:**

- **Primary Keys**: All tables use `bigserial` auto-incrementing primary keys for simplicity and performance
- **Foreign Keys**: Enforce referential integrity with cascading deletes where appropriate (e.g., `Calendar.user_id` ON DELETE CASCADE)
- **Lookup Indexes**: Composite indexes on frequently queried columns (e.g., `(user_id, created_at)` for calendar lists)
- **Unique Constraints**: `users.email`, `orders.order_number`, `calendar.share_token` for business logic enforcement
- **JSONB Indexes**: GIN indexes on `calendar.config` for filtering by holiday sets, astronomy options

### Context: task-i2-t3 (from 02_Iteration_I2.md)

**Task ID:** `I2.T3`

**Description:**
Create EventService for managing calendar events: addEvent (to calendar), updateEvent (text, emoji, color), deleteEvent, listEvents (by calendar, optionally filtered by date range). Implement EventRepository with custom queries: findByCalendarId, findByDateRange. Add validation: event date must be within calendar year, event text max 500 characters, emoji must be valid Unicode. Handle bulk event operations (import multiple events from CSV or JSON). Write unit tests for event service and repository.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- Event entity model from I1.T8
- GraphQL schema event operations from I1.T6

**Input Files:**
- `src/main/java/villagecompute/calendar/model/Event.java`
- `src/main/java/villagecompute/calendar/model/Calendar.java`
- `api/graphql-schema.graphql`

**Target Files:**
- `src/main/java/villagecompute/calendar/service/EventService.java`
- `src/main/java/villagecompute/calendar/repository/EventRepository.java`
- `src/test/java/villagecompute/calendar/service/EventServiceTest.java`

**Deliverables:**
- EventService class with CRUD and validation methods
- EventRepository with custom queries
- Validation logic for event fields
- Unit tests with >80% coverage

**Acceptance Criteria:**
- `EventService.addEvent()` validates event date is within calendar year
- `EventService.addEvent()` rejects events with text >500 characters
- `EventRepository.findByDateRange()` returns events within specified date range
- Emoji validation accepts valid Unicode emoji sequences
- Unit tests cover all validation scenarios and edge cases

**Dependencies:** `I2.T2` (requires CalendarService for authorization context)

**Parallelizable:** Partially (can start concurrently with I2.T2 if calendar authorization logic mocked)

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### CRITICAL FINDING: Event Model Does Not Yet Exist

I searched the entire codebase and **found no existing Event entity model**. The task description references "Event entity model from I1.T8" but this entity has NOT been created yet. The current system stores events differently:

**Current Event Storage Pattern:**
- Events are currently embedded in the `UserCalendar.configuration` JSONB field
- The `CalendarResource.java` shows events are stored as `Map<String, String> eventTitles` (date -> title mapping)
- The GraphQL schema shows: `UserCalendar.configuration` is a JSON field containing embedded event data with structure: `{ events: [...], layout: {...}, colors: {...}, astronomy: {...} }`

**CRITICAL DECISION REQUIRED:**
You must decide whether to:
1. **Create a new separate Event entity** with its own database table (as the task specification suggests)
2. **Continue using the embedded JSON approach** and create service/repository layers that work with the JSON structure

**Recommendation:** Given that the task explicitly mentions "Event entity model from I1.T8" and requires "EventRepository with custom queries: findByCalendarId, findByDateRange", you SHOULD create a proper Event entity. However, you'll also need to create a database migration script for the events table since it doesn't exist yet.

### Relevant Existing Code

*   **File:** `src/main/java/villagecompute/calendar/data/models/UserCalendar.java`
    *   **Summary:** This is the Calendar entity that uses Panache ActiveRecord pattern. It contains the parent relationship for events. The class extends `DefaultPanacheEntityWithTimestamps` and includes a `configuration` JSONB field where events are currently stored.
    *   **Recommendation:** You MUST create a bidirectional relationship with the Event entity. Add `@OneToMany(mappedBy = "calendar", cascade = CascadeType.ALL, orphanRemoval = true)` for the events collection. The `UserCalendar` already has version control (`@Version`) for optimistic locking.
    *   **Important:** The calendar has a `year` field (Integer) that you'll need to validate events against. Events must have dates within this calendar year.

*   **File:** `src/main/java/villagecompute/calendar/services/CalendarService.java`
    *   **Summary:** This is the primary service for calendar operations. It demonstrates the exact patterns you should follow for EventService including authorization checks, validation methods, transaction management, and error handling.
    *   **Recommendation:** You MUST follow the same architectural patterns:
        - Use `@ApplicationScoped` CDI scope
        - Inject repository using `@Inject`
        - Use `@Transactional` for all write operations
        - Create separate private methods for authorization (`checkReadAccess`, `checkWriteAccess`)
        - Create separate private methods for validation (`validateEventInput`)
        - Use `Logger.getLogger(EventService.class)` for logging
        - Follow the same method signature patterns (e.g., passing `CalendarUser currentUser` for authorization)
    *   **Authorization Pattern:** Events don't have their own authorization - they inherit from their parent calendar. You MUST call `CalendarService` or duplicate its authorization logic to check if the user can modify the calendar before adding/updating/deleting events.

*   **File:** `api/schema.graphql`
    *   **Summary:** The complete GraphQL schema showing that events are currently embedded in `UserCalendar.configuration` as JSON.
    *   **Recommendation:** The current schema does NOT have separate Event type, Query, or Mutation operations for events. Events are managed through calendar updates. You do NOT need to modify the GraphQL schema for this task - focus only on the service and repository layers. The GraphQL integration will come in a later task (I2.T6).
    *   **Note:** The schema shows `UserCalendar.configuration: JSON` contains: `{ events: [...], layout: {...}, colors: {...}, astronomy: {...} }`

*   **File:** `src/test/java/villagecompute/calendar/services/CalendarServiceTest.java`
    *   **Summary:** Comprehensive unit test example showing the exact testing patterns required for this project.
    *   **Recommendation:** You MUST follow this test structure exactly:
        - Extend `@QuarkusTest`
        - Use `@BeforeEach` to create test data (users, calendars)
        - Use `@AfterEach` to clean up with `Entity.deleteAll()`
        - Use `@Transactional` annotation on test methods that modify data
        - Inject `ObjectMapper` for creating test JSON data
        - Group tests by operation (CREATE TESTS, UPDATE TESTS, DELETE TESTS, etc.)
        - Test all validation scenarios (null inputs, invalid data, boundary conditions)
        - Test authorization scenarios (owner, admin, other user, anonymous)
        - Test edge cases (not found, concurrent updates)
        - Use descriptive test names: `testOperationName_Scenario_ExpectedResult`

*   **File:** `src/main/java/villagecompute/calendar/api/CalendarResource.java`
    *   **Summary:** Shows that events are currently stored as `Map<String, String> eventTitles` (date -> title mapping) in the request/response objects.
    *   **Note:** This is a REST endpoint, not the GraphQL layer. Your EventService should NOT depend on this class.

### Implementation Tips & Notes

*   **CRITICAL: Create Event Entity First:** You must create `src/main/java/villagecompute/calendar/data/models/Event.java` before creating the service. The entity should:
    - Extend `DefaultPanacheEntityWithTimestamps` (like UserCalendar does)
    - Use `@Entity` and `@Table(name = "events")`
    - Add indexes: `@Index(name = "idx_events_calendar", columnList = "calendar_id, event_date")`
    - Have fields: `calendar` (ManyToOne), `eventDate` (LocalDate), `eventText` (String, max 500), `emoji` (String, max 100), `color` (String, max 20)
    - Use Jakarta validation annotations: `@NotNull`, `@Size(max = 500)`
    - Add static query methods following Panache pattern: `findByCalendar()`, `findByDateRange()`

*   **CRITICAL: Create Database Migration:** The events table does NOT exist yet. You must create `migrations/src/main/resources/scripts/013_create_events_table.sql` with:
    ```sql
    CREATE TABLE events (
        event_id BIGSERIAL PRIMARY KEY,
        calendar_id BIGINT NOT NULL REFERENCES user_calendars(id) ON DELETE CASCADE,
        event_date DATE NOT NULL,
        event_text VARCHAR(500),
        emoji VARCHAR(100),
        color VARCHAR(20),
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );
    CREATE INDEX idx_events_calendar ON events(calendar_id, event_date);
    ```

*   **Repository Pattern:** This project uses Panache ActiveRecord pattern, NOT separate repository classes. Your "EventRepository" should actually be static methods on the Event entity itself (like UserCalendar.findByUser()). However, if you want to create a separate repository class for consistency, inject it like `CalendarService` injects `UserCalendarRepository`.

*   **Validation Strategy:** For emoji validation, use Java's built-in Unicode support. Valid emoji regex pattern: `[\p{So}\p{Emoji}]` or use `Character.isEmoji()` if available. Consider using a library like `emoji-java` for robust emoji validation, or implement a simple check that the string contains valid Unicode supplementary characters.

*   **Date Range Validation:** For checking if event date is within calendar year:
    ```java
    LocalDate eventDate = ...; // the event date
    int calendarYear = calendar.year;
    if (eventDate.getYear() != calendarYear) {
        throw new IllegalArgumentException("Event date must be in calendar year " + calendarYear);
    }
    ```

*   **Bulk Operations:** For CSV/JSON import, use Jackson `ObjectMapper` (already available in tests) to parse JSON arrays. For CSV, consider using Apache Commons CSV or OpenCSV library (check `pom.xml` for existing dependencies).

*   **Transaction Management:** ALL write operations (add, update, delete, bulk import) MUST be annotated with `@Transactional`. Read operations (list, find) do NOT need transactions.

*   **Error Handling:** Use IllegalArgumentException for validation errors, SecurityException for authorization failures (like CalendarService does). Let Quarkus handle exception mapping to HTTP status codes.

*   **Logging Best Practices:** Follow CalendarService logging pattern:
    - Use `LOG.infof()` for important state changes (add, update, delete)
    - Use `LOG.debugf()` for read operations
    - Use `LOG.errorf()` for errors
    - Use `LOG.warnf()` for security violations

*   **Testing Coverage:** To achieve >80% coverage, you must test:
    - All CRUD operations (add, update, delete, list)
    - All validation rules (date in year, text length, emoji validity)
    - Authorization scenarios (owner can modify, others cannot)
    - Edge cases (event not found, calendar not found, null inputs)
    - Bulk operations (import multiple events)
    - Date range queries (events between dates)

*   **Package Structure:** Follow existing patterns:
    - Entities: `villagecompute.calendar.data.models`
    - Services: `villagecompute.calendar.services`
    - Repositories: `villagecompute.calendar.data.repositories` (if separate classes)
    - Tests: `villagecompute.calendar.services` (test package mirrors main package)

*   **Dependencies:** The project uses Quarkus 3.x with Panache ORM. Check `pom.xml` for version details. Do NOT add new dependencies without documenting why they're needed.

### Warning: Schema Migration Required

The current database schema does NOT have an events table. Task I1.T7 created migration scripts but did NOT include events (it was supposed to be in script 005_create_events_table.sql). You have two options:

1. **Create the missing migration script** as described above
2. **Modify the existing database schema** in development and document the change

Recommendation: Create migration script 013_create_events_table.sql since scripts 001-012 likely already exist for other tables.

### Coordination with CalendarService

Your EventService will need to coordinate with CalendarService for authorization. You have two options:

1. **Inject CalendarService** and call `calendarService.getCalendar(calendarId, currentUser)` which performs authorization checks
2. **Duplicate authorization logic** by checking if `calendar.user.id.equals(currentUser.id)` or `currentUser.isAdmin`

Recommendation: Option 1 (inject CalendarService) is cleaner and maintains single source of truth for authorization logic.

---

**End of Task Briefing Package**
