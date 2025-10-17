# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I1.T13",
  "iteration_id": "I1",
  "iteration_goal": "Establish project infrastructure, define data models, create architectural artifacts, and implement foundational backend/frontend scaffolding",
  "description": "Create JUnit 5 unit tests for all JPA entity models and Panache repositories. Test entity validation constraints (@NotNull, @Email, etc.), relationship mappings (cascade, fetch strategies), JSONB serialization/deserialization. Test repository methods: findById, listAll, persist, delete, custom queries (e.g., findByEmail, findByUserId). Use Quarkus test framework with @QuarkusTest annotation and in-memory H2 database for tests. Achieve >70% code coverage for model and repository packages. Configure JaCoCo Maven plugin for coverage reporting.",
  "agent_type_hint": "BackendAgent",
  "inputs": "Entity models from Task I1.T8, Quarkus testing documentation",
  "target_files": [
    "src/test/java/com/villagecompute/calendar/model/UserTest.java",
    "src/test/java/com/villagecompute/calendar/model/CalendarTest.java",
    "src/test/java/com/villagecompute/calendar/model/OrderTest.java",
    "src/test/java/com/villagecompute/calendar/repository/UserRepositoryTest.java",
    "src/test/java/com/villagecompute/calendar/repository/CalendarRepositoryTest.java",
    "pom.xml"
  ],
  "input_files": [
    "src/main/java/com/villagecompute/calendar/model/*.java",
    "src/main/java/com/villagecompute/calendar/repository/*.java"
  ],
  "deliverables": "Unit tests for all 11 entity classes, Unit tests for all repository classes, Tests run successfully with ./mvnw test, JaCoCo coverage report generated (target/site/jacoco/index.html), Coverage >70% for model and repository packages",
  "acceptance_criteria": "./mvnw test runs all tests without failures, Entity validation tests verify @NotNull, @Email, @Size constraints, Relationship tests confirm cascade and fetch behavior, Repository tests verify CRUD operations and custom queries, JaCoCo report shows line/branch coverage percentages, No test depends on external services (all use in-memory database)",
  "dependencies": ["I1.T8"],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Data Model Overview (from 03_System_Structure_and_Data.md)

```markdown
### 3.6. Data Model Overview & ERD

**Description:**

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

**Database Indexes Strategy:**

- **Primary Keys**: All tables use `bigserial` auto-incrementing primary keys for simplicity and performance
- **Foreign Keys**: Enforce referential integrity with cascading deletes where appropriate (e.g., `Calendar.user_id` ON DELETE CASCADE)
- **Lookup Indexes**: Composite indexes on frequently queried columns (e.g., `(user_id, created_at)` for calendar lists)
- **Unique Constraints**: `users.email`, `orders.order_number`, `calendar.share_token` for business logic enforcement
- **JSONB Indexes**: GIN indexes on `calendar.config` for filtering by holiday sets, astronomy options (e.g., `WHERE config @> '{"astronomy": {"moon_phases": true}}'`)
```

### Context: Technology Stack - Testing Tools (from 02_Architecture_Overview.md)

```markdown
| **Category** | **Technology** | **Version** | **Justification** |
|--------------|----------------|-------------|-------------------|
| **ORM** | Hibernate ORM with Panache | (bundled) | Active record pattern simplifies CRUD. Type-safe queries. Integrated with Quarkus transaction management. |
| **Database** | PostgreSQL | 17+ | Mandated. PostGIS extension for geospatial queries (future: location-based features). JSONB for flexible calendar metadata. Robust, proven, open-source. |
```

### Context: Testing Levels (from 03_Verification_and_Glossary.md)

```markdown
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
```

### Context: Code Quality Gates (from 03_Verification_and_Glossary.md)

```markdown
**Mandatory Gates (CI Pipeline Fails If Not Met)**

1.  **Compilation Success**: Backend and frontend must compile without errors
2.  **Unit Test Pass**: All unit tests must pass (0% failure tolerance)
3.  **Code Coverage**: Minimum 70% line coverage for service layer, API layer, repository layer (JaCoCo enforcement)
4.  **Linting**: ESLint must pass with 0 errors (warnings allowed, but discouraged)
5.  **Security Scan**: No critical severity vulnerabilities in dependencies (Snyk/OWASP)
6.  **Integration Test Pass**: All integration tests must pass
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `src/main/java/villagecompute/calendar/data/models/DefaultPanacheEntityWithTimestamps.java`
    *   **Summary:** Base class for all entity models providing UUID primary key (`id`), automatic timestamp management (`created`, `updated`), and optimistic locking (`version` field).
    *   **Recommendation:** All entity models extend this base class. Tests should verify that `created`, `updated`, and `version` fields are automatically populated on persist and update operations.

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarUser.java`
    *   **Summary:** Entity representing an OAuth authenticated user. Uses Panache active record pattern with static finder methods like `findByOAuthSubject()`, `findByEmail()`, `findActiveUsersSince()`.
    *   **Recommendation:** Tests already exist at `src/test/java/villagecompute/calendar/data/models/CalendarUserTest.java` with comprehensive coverage of validation, finder methods, and relationships. Use this as a reference pattern for other entity tests.

*   **File:** `src/main/java/villagecompute/calendar/data/models/UserCalendar.java`
    *   **Summary:** Entity representing user calendars with JSONB configuration field. Includes custom queries like `findByUserAndYear()`, `findBySession()`.
    *   **Recommendation:** Tests already exist at `src/test/java/villagecompute/calendar/data/models/UserCalendarTest.java`. Note the pattern for testing JSONB fields using Jackson ObjectMapper.

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarOrder.java`
    *   **Summary:** E-commerce order entity with BigDecimal price fields, status management, and Stripe integration fields. Includes helper methods like `markAsPaid()`, `markAsShipped()`.
    *   **Recommendation:** Tests already exist at `src/test/java/villagecompute/calendar/data/models/CalendarOrderTest.java`.

*   **File:** `src/main/java/villagecompute/calendar/data/repositories/CalendarUserRepository.java`
    *   **Summary:** Repository implementing PanacheRepository pattern with custom query methods. Methods delegate to Panache's fluent query API.
    *   **Recommendation:** Tests already exist at `src/test/java/villagecompute/calendar/data/repositories/CalendarUserRepositoryTest.java`. This pattern should be followed for other repositories.

*   **File:** `src/test/java/villagecompute/calendar/data/repositories/TestDataCleaner.java`
    *   **Summary:** Utility for cleaning test data in correct order to avoid foreign key violations. Injected as `@ApplicationScoped` bean and used in `@BeforeEach` setup.
    *   **Recommendation:** You MUST use this in all repository and integration tests to ensure clean state between tests.

*   **File:** `src/test/resources/application.properties`
    *   **Summary:** Test configuration using H2 in-memory database with PostgreSQL compatibility mode. Hibernate configured for `drop-and-create` schema generation.
    *   **Recommendation:** Tests automatically use this configuration via `@QuarkusTest` annotation. No additional setup required.

### Implementation Tips & Notes

*   **Tip:** **TESTS ALREADY EXIST!** My analysis shows that all 7 entity model tests AND all 4 repository tests are already implemented:
    - Entity Tests: `AnalyticsRollupTest`, `CalendarOrderTest`, `CalendarTemplateTest`, `CalendarUserTest`, `DelayedJobTest`, `PageViewTest`, `UserCalendarTest`
    - Repository Tests: `CalendarOrderRepositoryTest`, `CalendarTemplateRepositoryTest`, `CalendarUserRepositoryTest`, `UserCalendarRepositoryTest`

*   **Tip:** I ran `./mvnw clean test jacoco:report` and confirmed **all 220 tests pass successfully**. The JaCoCo report is generated at `target/site/jacoco/index.html`.

*   **Tip:** The test patterns established in the codebase follow Quarkus best practices:
    - Use `@QuarkusTest` annotation for integration with Quarkus runtime
    - Use `@Inject` for dependency injection (Validator, EntityManager, ObjectMapper, Repositories)
    - Use `@Transactional` on test methods that modify database state
    - Use `@BeforeEach` with `TestDataCleaner.deleteAll()` for clean state
    - Use Jakarta Bean Validation annotations for constraint testing
    - Use Panache's fluent query API in both entities (ActiveRecord) and repositories

*   **Note:** The actual package structure is `villagecompute.calendar` (not `com.villagecompute.calendar` as specified in the task's target_files). The tests use the correct package structure.

*   **Note:** JaCoCo Maven plugin is already configured in `pom.xml` with:
    - `jacoco:prepare-agent` execution in test phase
    - `jacoco:report` execution after test
    - `jacoco:check` execution for coverage enforcement
    - HTML report generation at `target/site/jacoco/`

*   **Warning:** The task specification asks for tests to be created, but they already exist and are passing. The task's acceptance criteria state "Coverage >70% for model and repository packages" - you SHOULD verify the current coverage level by examining the JaCoCo HTML report.

*   **Warning:** One entity mentioned in the architecture (Event) does not have a corresponding Java class in the current implementation. The project uses `UserCalendar` with JSONB configuration instead of separate Event entities. This is a valid design choice that simplifies the data model.

*   **Note:** The task mentions 11 entity classes, but only 8 are implemented (AnalyticsRollup, CalendarOrder, CalendarTemplate, CalendarUser, DelayedJob, DelayedJobQueue, PageView, UserCalendar). The separate Event, Payment, OrderItem entities mentioned in the architecture may have been consolidated or deferred to later iterations.

### Recommended Next Steps

1. **VERIFY COVERAGE**: Check the JaCoCo report at `target/site/jacoco/villagecompute.calendar.data.models/index.html` and `target/site/jacoco/villagecompute.calendar.data.repositories/index.html` to confirm >70% coverage.

2. **IF COVERAGE IS INSUFFICIENT**: Identify specific classes or methods with low coverage and add targeted tests to increase coverage above the 70% threshold.

3. **IF COVERAGE IS SUFFICIENT**: Mark task I1.T13 as DONE and document the coverage results in the task completion notes.

4. **OPTIONAL IMPROVEMENTS**: Consider adding tests for edge cases not currently covered:
   - Concurrent updates with optimistic locking (version conflicts)
   - JSONB query performance with GIN indexes
   - Cascade delete behavior with complex relationship graphs
   - Unique constraint violations

5. **DOCUMENTATION**: Update any test documentation to reflect the comprehensive test suite that exists.
