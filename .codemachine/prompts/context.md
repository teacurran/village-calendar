# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I2.T2",
  "iteration_id": "I2",
  "iteration_goal": "Implement calendar creation, editing, and template system. Build calendar editor UI components. Integrate astronomical calculations (moon phases, Hebrew calendar). Create sequence diagrams for key workflows",
  "description": "Create CalendarService with business logic for calendar operations: createCalendar (from template or blank), updateCalendar (modify config, events), deleteCalendar (soft delete or hard delete), getCalendar (by ID with authorization check), listCalendars (by user, with pagination). Implement CalendarRepository with Panache custom queries: findByUserId, findByShareToken, findTemplates (isActive=true). Handle calendar versioning (optimistic locking with version field). Implement session-to-user conversion (migrate calendars from CalendarSession to User on login). Add authorization checks (user can only access own calendars unless admin). Write unit tests for all service methods.",
  "agent_type_hint": "BackendAgent",
  "inputs": "Calendar entity model from I1.T8, GraphQL schema calendar queries/mutations from I1.T6, Calendar service requirements from Plan Section 2",
  "target_files": [
    "src/main/java/villagecompute/calendar/services/CalendarService.java",
    "src/main/java/villagecompute/calendar/data/repositories/UserCalendarRepository.java",
    "src/test/java/villagecompute/calendar/services/CalendarServiceTest.java"
  ],
  "input_files": [
    "src/main/java/villagecompute/calendar/data/models/UserCalendar.java",
    "src/main/java/villagecompute/calendar/data/models/CalendarUser.java",
    "api/schema.graphql"
  ],
  "deliverables": "CalendarService class with all CRUD methods implemented, CalendarRepository with custom query methods, Authorization logic (user can only access own calendars), Optimistic locking (version field prevents concurrent update conflicts), Unit tests achieving >80% code coverage for service layer",
  "acceptance_criteria": "CalendarService.createCalendar() persists new calendar to database, CalendarService.updateCalendar() throws exception if version mismatch, CalendarService.listCalendars(userId) returns only user's calendars, CalendarRepository.findByShareToken() returns public calendar or null, Authorization check throws UnauthorizedException if user accesses other user's calendar, All unit tests pass with ./mvnw test",
  "dependencies": ["I1.T8", "I1.T9"],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Architectural Style (from 01_Plan_Overview_and_Setup.md)

```markdown
**Modular Monolith with Asynchronous Job Processing**

Single Quarkus application deployed to Kubernetes with clear internal module boundaries:
- `calendar-core`: Calendar domain logic, astronomical calculations, templates
- `calendar-pdf`: PDF generation engine (Batik SVG rendering, watermarking)
- `user-management`: OAuth authentication, account management
- `commerce`: Order management, Stripe integration, payment processing
- `jobs`: DelayedJob framework, async job definitions
- `admin`: Administrative interfaces, analytics
- `web-api`: GraphQL schema, REST controllers, Vue.js SPA integration (Quinoa)

**Rationale**: Simpler operations for small team, strong transactional consistency for e-commerce, faster development velocity. Designed for future extraction of modules (e.g., PDF service) if independent scaling required.
```

### Context: Calendar Entity Design (from UserCalendar.java)

The `UserCalendar` entity uses Panache active record pattern with these key characteristics:
- Extends `DefaultPanacheEntityWithTimestamps` (provides `id`, `created`, `updated` fields)
- `@ManyToOne` relationship with `CalendarUser` (owner)
- `@ManyToOne` relationship with `CalendarTemplate` (optional template)
- `sessionId` field for guest users (nullable)
- `configuration` field as JSONB for flexible calendar settings
- `isPublic` field for visibility control
- Indexes on `user_id`, `session_id`, `template_id`, and `is_public`

### Context: GraphQL Schema Calendar Types (from schema.graphql)

```graphql
type UserCalendar {
  configuration: JSON
  created: DateTime!
  generatedPdfUrl: String
  generatedSvg: String
  id: ID!
  isPublic: Boolean!
  name: String!
  orders: [CalendarOrder!]!
  sessionId: String
  status: CalendarStatus!
  template: CalendarTemplate!
  updated: DateTime!
  user: CalendarUser
  year: Int!
}

input CalendarInput {
  configuration: JSON
  isPublic: Boolean
  name: String!
  templateId: ID!
  year: Int!
}

input CalendarUpdateInput {
  configuration: JSON
  isPublic: Boolean
  name: String
}
```

### Context: API Contract (from Plan Section 2)

**Key Queries:**
- `calendar(id: ID!): Calendar` - Get single calendar by ID with authorization
- `calendars(userId: ID!, year: Int): [Calendar!]!` - List user's calendars with optional year filter
- `myCalendars(year: Int): [UserCalendar!]!` - Get authenticated user's calendars

**Key Mutations:**
- `createCalendar(input: CreateCalendarInput!): Calendar!`
- `updateCalendar(id: ID!, input: UpdateCalendarInput!): Calendar!`
- `deleteCalendar(id: ID!): Boolean!`
- `convertGuestSession(sessionId: ID!): User!` - Transfer session calendars to user

### Context: Technology Stack (from Plan)

- **ORM**: Hibernate ORM with Panache (active record pattern)
- **Transactions**: Jakarta `@Transactional` for service methods
- **Validation**: Jakarta Bean Validation (`@NotNull`, `@Size`, etc.)
- **Logging**: JBoss Logging (`Logger.getLogger()`)

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `src/main/java/villagecompute/calendar/data/models/UserCalendar.java`
    *   **Summary:** Entity class for calendars. Already implements Panache active record pattern with several finder methods (`findBySession`, `findByUserAndYear`, `findByUser`, `findPublicById`, etc.)
    *   **Recommendation:** You SHOULD reuse the existing static finder methods in the entity class, but the Repository pattern is the preferred abstraction for the service layer. The entity already has useful helpers like `copyForSession()`.
    *   **Critical:** The entity extends `DefaultPanacheEntityWithTimestamps` which provides `id` (UUID), `created`, and `updated` fields automatically.

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarUser.java`
    *   **Summary:** Entity class for authenticated users. Contains OAuth provider info, email, admin flag, and relationships to calendars and orders.
    *   **Recommendation:** You MUST use this entity for user ownership checks in authorization logic. Check the `isAdmin` field for admin authorization bypass.

*   **File:** `src/main/java/villagecompute/calendar/data/repositories/UserCalendarRepository.java`
    *   **Summary:** Already exists with core query methods (`findByUserAndYear`, `findByUser`, `findBySession`, `findPublicById`, `findByTemplate`, `findByYear`)
    *   **Recommendation:** This repository is ALREADY IMPLEMENTED with most of the methods required by the task spec. You SHOULD extend it with additional methods if needed, but most query functionality is already there.

*   **File:** `src/main/java/villagecompute/calendar/services/TemplateService.java`
    *   **Summary:** Service layer for template management. Shows the pattern used in this project: `@ApplicationScoped`, `@Inject` for dependencies, `@Transactional` on modifying methods, comprehensive validation, and JBoss logging.
    *   **Recommendation:** You SHOULD follow the same service pattern as TemplateService. Key patterns include:
      - Logger initialization: `private static final Logger LOG = Logger.getLogger(CalendarService.class);`
      - Method-level logging: `LOG.infof("Creating calendar: name=%s", input.name);`
      - Validation with `IllegalArgumentException` or `IllegalStateException`
      - `@Transactional` on all methods that modify data
      - Error messages with context in exceptions

*   **File:** `src/main/java/villagecompute/calendar/services/CalendarService.java`
    *   **Summary:** This file ALREADY EXISTS but contains ONLY calendar SVG/PDF rendering logic (1687 lines), NOT business service layer functionality. It's a utility service with `generateCalendarSVG()` and `generateCalendarPDF()` methods.
    *   **Critical Warning:** The task requires creating a service at the same path. You MUST decide: either rename the existing class (e.g., `CalendarRenderingService.java`) OR integrate your new business logic into a new file with a different name. Given the file is 1687 lines of rendering logic, I STRONGLY RECOMMEND renaming the existing one first to avoid confusion.

*   **File:** `api/schema.graphql`
    *   **Summary:** Complete GraphQL schema with calendar queries and mutations defined. Use this for understanding expected inputs/outputs.
    *   **Recommendation:** The service methods should align with the GraphQL schema types. For example, `createCalendar` should accept parameters matching `CalendarInput`.

### Implementation Tips & Notes

*   **Tip:** The existing `CalendarService.java` is a rendering service, NOT a business logic service. To avoid confusion, I recommend you:
    1. First rename `CalendarService.java` → `CalendarRenderingService.java`
    2. Then create your new `CalendarService.java` for business logic (CRUD operations)
    3. Inject `CalendarRenderingService` into your new service if you need SVG/PDF generation

*   **Note:** The `UserCalendar` entity has NO `version` field for optimistic locking. The task spec mentions "Handle calendar versioning (optimistic locking with version field)" but this field does not exist in the entity. You have two options:
    1. Add a `@Version` field to the entity class
    2. Document in your service that optimistic locking will be added in a future task
    I recommend option 1 - add the field now to meet acceptance criteria.

*   **Note:** Guest session conversion logic requires migrating `UserCalendar` records from `sessionId` to `user` relationship. The entity has both fields. The conversion logic should:
    1. Find all calendars with matching `sessionId`
    2. Set `user` to the authenticated user
    3. Clear the `sessionId` field
    4. Persist changes

*   **Tip:** Authorization pattern should check:
    - If user is admin (`CalendarUser.isAdmin == true`) → allow all operations
    - If calendar is public (`UserCalendar.isPublic == true`) → allow read operations
    - If calendar.user.id matches authenticated user ID → allow all operations
    - Otherwise → throw `SecurityException` or `UnauthorizedException`

*   **Warning:** The project uses `@ApplicationScoped` for service beans, NOT `@RequestScoped`. Ensure your service is stateless.

*   **Tip:** For unit tests, use `@QuarkusTest` annotation and inject repositories via `@Inject`. Use Testcontainers or H2 for test database. Follow the pattern from existing tests if any exist.

*   **Note:** The schema shows `myCalendars` query which should return calendars for the currently authenticated user. You'll need to get the user context from the security layer (likely via `@Context SecurityIdentity` in GraphQL resolver, then pass user ID to service).

*   **Tip:** The `findByShareToken` method mentioned in the task spec is NOT yet implemented in `UserCalendarRepository`. However, I don't see a `shareToken` field in the `UserCalendar` entity - the entity only has `id` and `isPublic` for sharing. You may need to clarify the sharing mechanism, or interpret "findByShareToken" as "findPublicById".

*   **Critical:** All exceptions should be descriptive and include relevant context (user ID, calendar ID, operation) for debugging. See `TemplateService` for good examples.

### Testing Strategy

*   Use `@QuarkusTest` for integration tests
*   Mock the `UserCalendarRepository` for pure unit tests (optional, integration tests preferred in Quarkus)
*   Test authorization edge cases thoroughly:
    - Guest accessing private calendar
    - User accessing another user's private calendar
    - User accessing another user's public calendar (should succeed for read)
    - Admin accessing any calendar (should succeed)
*   Test version conflict handling for optimistic locking (if implemented)
*   Test session conversion logic with multiple calendars

---

## 4. Implementation Checklist

- [ ] Rename existing `CalendarService.java` to `CalendarRenderingService.java`
- [ ] Add `@Version` field to `UserCalendar` entity for optimistic locking
- [ ] Create new `CalendarService.java` with `@ApplicationScoped`
- [ ] Implement `createCalendar(...)` with template application logic
- [ ] Implement `updateCalendar(...)` with version check and authorization
- [ ] Implement `deleteCalendar(...)` with authorization (soft or hard delete)
- [ ] Implement `getCalendar(...)` with authorization
- [ ] Implement `listCalendars(...)` with pagination support
- [ ] Implement `convertSessionToUser(...)` for guest session migration
- [ ] Add authorization helper method (`checkAuthorization(UserCalendar, CalendarUser)`)
- [ ] Clarify share token mechanism or use public calendar by ID
- [ ] Write unit/integration tests with >80% coverage
- [ ] Verify all tests pass with `./mvnw test`

---

**End of Task Briefing Package**
