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
    "src/test/java/villagecompute/calendar/model/UserTest.java",
    "src/test/java/villagecompute/calendar/model/CalendarTest.java",
    "src/test/java/villagecompute/calendar/model/OrderTest.java",
    "src/test/java/villagecompute/calendar/repository/UserRepositoryTest.java",
    "src/test/java/villagecompute/calendar/repository/CalendarRepositoryTest.java",
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

The following analysis is based on my direct review of the current codebase and test execution results.

### Current Situation: CRITICAL GAP IDENTIFIED

**Test Existence**: All entity and repository tests exist and are comprehensive (220 tests total, all passing).

**Coverage Problem**: Despite passing tests, JaCoCo coverage is FAR below the required 70% threshold:

```
Build Result: FAILURE
Coverage checks have not been met:

villagecompute.calendar.data.models:    18% (required: 70%)
villagecompute.calendar.data.repositories: 0% (required: 70%)
```

**Root Cause Analysis**: The tests are written to test behavior via validation and finder methods, but JaCoCo measures **instruction coverage**. Many model class instructions are not being executed by tests:

1. **Model Classes**: Tests validate constraints and call finder methods, but don't exercise:
   - Default constructors
   - Field initialization logic
   - Implicit getters/setters (if any)
   - JPA lifecycle callbacks
   - Relationship navigation code

2. **Repository Classes**: 0% coverage indicates repository tests may be calling entity static methods directly instead of going through repository instances.

### Relevant Existing Code

*   **File:** `src/main/java/villagecompute/calendar/data/models/DefaultPanacheEntityWithTimestamps.java`
    *   **Summary:** Base class for all entity models providing UUID primary key (`id`), automatic timestamp management (`created`, `updated`), and optimistic locking (`version` field).
    *   **Recommendation:** Tests should verify that `created`, `updated`, and `version` fields are automatically populated on persist and update operations. Current tests may not be exercising the @PrePersist and @PreUpdate callbacks.

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarUser.java`
    *   **Summary:** Entity representing an OAuth authenticated user. Uses Panache active record pattern with static finder methods like `findByOAuthSubject()`, `findByEmail()`, `findActiveUsersSince()`.
    *   **Recommendation:** Tests exist at `CalendarUserTest.java` but achieve only 11% coverage. Need to add tests that:
        - Exercise all fields (profileImageUrl is likely untested)
        - Test relationship loading (calendars, orders collections)
        - Test all code paths in finder methods

*   **File:** `src/main/java/villagecompute/calendar/data/models/UserCalendar.java`
    *   **Summary:** Entity representing user calendars with JSONB configuration field. Includes custom queries like `findByUserAndYear()`, `findBySession()`.
    *   **Recommendation:** Tests exist at `UserCalendarTest.java` but achieve only 6% coverage. Key gap: JSONB configuration field manipulation is likely not fully tested.

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarOrder.java`
    *   **Summary:** E-commerce order entity with BigDecimal price fields, status management, and Stripe integration fields. Includes helper methods like `markAsPaid()`, `markAsShipped()`.
    *   **Recommendation:** Tests exist at `CalendarOrderTest.java` but achieve 0% coverage. This is suspicious - the test may not be properly exercising the order lifecycle methods.

*   **File:** `src/main/java/villagecompute/calendar/data/repositories/CalendarUserRepository.java`
    *   **Summary:** Repository implementing PanacheRepository pattern with custom query methods. Methods delegate to Panache's fluent query API.
    *   **Recommendation:** Repository tests show 0% coverage. **CRITICAL ISSUE**: Tests are likely calling `CalendarUser.findByOAuthSubject()` directly (static method on entity) instead of calling `calendarUserRepository.findByOAuthSubject()`. Repository methods are never being invoked!

*   **File:** `src/test/java/villagecompute/calendar/data/repositories/TestDataCleaner.java`
    *   **Summary:** Utility for cleaning test data in correct order to avoid foreign key violations. Injected as `@ApplicationScoped` bean and used in `@BeforeEach` setup.
    *   **Recommendation:** You MUST use this in all repository and integration tests to ensure clean state between tests.

*   **File:** `src/test/resources/application.properties`
    *   **Summary:** Test configuration using H2 in-memory database with PostgreSQL compatibility mode. Hibernate configured for `drop-and-create` schema generation.
    *   **Recommendation:** Tests automatically use this configuration via `@QuarkusTest` annotation. No additional setup required.

### Implementation Tips & Notes

*   **Critical Finding #1**: Repository tests are NOT actually testing repository classes. Example from `CalendarUserRepositoryTest.java`:
    ```java
    // WRONG: Calls static method on entity, bypassing repository
    Optional<CalendarUser> found = CalendarUser.findByOAuthSubject("GOOGLE", "test-subject");

    // CORRECT: Should call instance method on injected repository
    Optional<CalendarUser> found = calendarUserRepository.findByOAuthSubject("GOOGLE", "test-subject");
    ```
    **Action Required**: ALL repository tests must be refactored to call repository instance methods, not entity static methods.

*   **Critical Finding #2**: Model tests focus on validation and finder methods but don't exercise all code paths. Need to add tests for:
    - All field combinations (set every field, not just required ones)
    - Relationship lazy loading (access collections and verify they load)
    - Relationship cascade operations (persist parent, verify child persisted)
    - Optimistic locking conflicts (concurrent updates, version mismatch)
    - JSONB field manipulation (set, retrieve, query JSONB data)
    - JPA lifecycle callbacks (verify @PrePersist, @PreUpdate called)

*   **Critical Finding #3**: Coverage measurement issue - JaCoCo counts bytecode instructions, not test assertions. A test can pass without exercising all instructions in a method. Need to:
    - Add more granular tests that exercise specific code branches
    - Test error paths and edge cases
    - Verify field-level coverage (set and get each field explicitly)

*   **Tip:** Use JaCoCo HTML reports to identify uncovered lines:
    ```bash
    open target/site/jacoco/villagecompute.calendar.data.models/CalendarUser.html
    ```
    Red/yellow highlighting shows which lines are not covered.

*   **Note:** The task specification mentions 11 entity classes, but only 9 are implemented:
    1. AnalyticsRollup
    2. CalendarOrder
    3. CalendarTemplate
    4. CalendarUser
    5. DefaultPanacheEntityWithTimestamps
    6. DelayedJob
    7. DelayedJobQueue
    8. PageView
    9. UserCalendar

    Missing from architecture: Event, Payment, OrderItem (these may have been consolidated into UserCalendar's JSONB config and CalendarOrder's fields).

*   **Note:** The task specification references package `com.villagecompute.calendar` but actual package is `villagecompute.calendar`. Use the actual package structure in all code.

### Recommended Approach to Achieve >70% Coverage

#### Phase 1: Fix Repository Tests (0% → 70% coverage)

1. **Refactor ALL repository tests** to inject and use repository instances instead of calling entity static methods:
   ```java
   @Inject
   CalendarUserRepository calendarUserRepository;

   @Test
   @Transactional
   void testFindByOAuthSubject() {
       // Use repository, not entity static method
       Optional<CalendarUser> found = calendarUserRepository.findByOAuthSubject("GOOGLE", "subject");
       // assertions...
   }
   ```

2. **Add tests for ALL repository methods** listed in each repository class.

3. **Add tests for base repository operations** inherited from PanacheRepository:
   - findById
   - listAll
   - persist
   - delete
   - count

#### Phase 2: Enhance Model Tests (18% → 70% coverage)

1. **For each entity, add field coverage tests**:
   ```java
   @Test
   @Transactional
   void testAllFieldsSet() {
       CalendarUser user = new CalendarUser();
       // Set EVERY field
       user.oauthProvider = "GOOGLE";
       user.oauthSubject = "subject";
       user.email = "test@example.com";
       user.displayName = "Test User";
       user.profileImageUrl = "https://example.com/photo.jpg"; // Often missed!
       user.lastLoginAt = Instant.now();
       user.isAdmin = false;

       user.persist();
       entityManager.flush();

       // Verify ALL fields persisted correctly
       CalendarUser found = CalendarUser.findById(user.id);
       assertEquals("https://example.com/photo.jpg", found.profileImageUrl);
       // ... assert all other fields
   }
   ```

2. **Add relationship loading tests**:
   ```java
   @Test
   @Transactional
   void testLazyLoadCalendars() {
       CalendarUser user = createAndPersistUser();
       UserCalendar calendar = createAndPersistCalendar(user);

       entityManager.clear(); // Detach all entities

       CalendarUser found = CalendarUser.findById(user.id);
       int size = found.calendars.size(); // Triggers lazy load

       assertEquals(1, size);
       assertEquals(calendar.id, found.calendars.get(0).id);
   }
   ```

3. **Add optimistic locking tests**:
   ```java
   @Test
   @Transactional
   void testOptimisticLockingConflict() {
       CalendarUser user = createAndPersistUser();
       UUID userId = user.id;

       // Simulate concurrent modification
       CalendarUser user1 = CalendarUser.findById(userId);
       CalendarUser user2 = CalendarUser.findById(userId);

       user1.displayName = "Updated 1";
       user1.persist();
       entityManager.flush();

       user2.displayName = "Updated 2";
       assertThrows(OptimisticLockException.class, () -> {
           user2.persist();
           entityManager.flush();
       });
   }
   ```

4. **Add JSONB tests** (for UserCalendar, CalendarTemplate):
   ```java
   @Test
   @Transactional
   void testJsonbConfigurationPersistence() {
       UserCalendar calendar = new UserCalendar();
       // ... set required fields

       ObjectNode config = objectMapper.createObjectNode();
       config.put("showMoonPhases", true);
       config.put("showHebrewDates", false);
       calendar.configuration = config;

       calendar.persist();
       entityManager.flush();
       entityManager.clear();

       UserCalendar found = UserCalendar.findById(calendar.id);
       assertTrue(found.configuration.get("showMoonPhases").asBoolean());
   }
   ```

#### Phase 3: Verify Coverage

1. Run full test suite with coverage:
   ```bash
   ./mvnw clean verify
   ```

2. Check JaCoCo report:
   ```bash
   open target/site/jacoco/index.html
   ```

3. Verify both packages meet 70% threshold:
   - villagecompute.calendar.data.models: >=70%
   - villagecompute.calendar.data.repositories: >=70%

4. If still below 70%, examine HTML reports to identify remaining uncovered lines and add targeted tests.

### Warning: Build Will Fail Until Coverage Fixed

The `./mvnw verify` command includes the `jacoco:check` goal which enforces 70% coverage. The build will continue to fail until both model and repository packages achieve the required coverage threshold. This is INTENTIONAL and part of the quality gates defined in the architecture.

**Current Status**: ❌ TASK I1.T13 IS NOT COMPLETE until `./mvnw verify` succeeds without coverage violations.
