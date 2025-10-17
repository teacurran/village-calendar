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
    "src/main/java/villagecompute/calendar/model/*.java",
    "src/main/java/villagecompute/calendar/repository/*.java"
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

### Context: data-model-overview (from 03_System_Structure_and_Data.md)

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
```

### Context: task-i1-t13 (from 02_Iteration_I1.md)

```markdown
### Task 1.13: Write Unit Tests for Entity Models and Repositories

**Task ID:** `I1.T13`

**Description:**
Create JUnit 5 unit tests for all JPA entity models and Panache repositories. Test entity validation constraints (@NotNull, @Email, etc.), relationship mappings (cascade, fetch strategies), JSONB serialization/deserialization. Test repository methods: findById, listAll, persist, delete, custom queries (e.g., findByEmail, findByUserId). Use Quarkus test framework with @QuarkusTest annotation and in-memory H2 database for tests. Achieve >70% code coverage for model and repository packages. Configure JaCoCo Maven plugin for coverage reporting.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- Entity models from Task I1.T8
- Quarkus testing documentation

**Deliverables:**
- Unit tests for all 11 entity classes
- Unit tests for all repository classes
- Tests run successfully with `./mvnw test`
- JaCoCo coverage report generated (`target/site/jacoco/index.html`)
- Coverage >70% for model and repository packages

**Acceptance Criteria:**
- `./mvnw test` runs all tests without failures
- Entity validation tests verify @NotNull, @Email, @Size constraints
- Relationship tests confirm cascade and fetch behavior
- Repository tests verify CRUD operations and custom queries
- JaCoCo report shows line/branch coverage percentages
- No test depends on external services (all use in-memory database)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

#### Entity Models Location

*   **Directory:** `src/main/java/villagecompute/calendar/data/models/`
*   **Summary:** All JPA entity models are located here. The project uses Hibernate ORM with Panache active record pattern.
*   **Key Entities Found:**
    - `CalendarUser.java` - Main user entity with OAuth authentication
    - `UserCalendar.java` - Calendar entity with JSONB configuration field
    - `CalendarTemplate.java` - Template entity with JSONB configuration
    - `CalendarOrder.java` - Order entity with JSONB shipping address
    - `DelayedJob.java` - Job queue entity
    - `PageView.java` - Analytics entity
    - `AnalyticsRollup.java` - Analytics aggregation entity
    - `DelayedJobQueue.java` - Additional job queue entity
*   **Base Class:** `DefaultPanacheEntityWithTimestamps` - All entities extend this base class which provides `id`, `created`, `updated`, and `version` fields
*   **Recommendation:** You MUST test all entities in the `src/main/java/villagecompute/calendar/data/models/` directory

#### Repository Pattern

*   **Directory:** `src/main/java/villagecompute/calendar/data/repositories/`
*   **Summary:** Repositories follow the Panache repository pattern and provide custom query methods
*   **Key Repositories Found:**
    - `CalendarUserRepository.java` - User repository with OAuth lookup methods
    - `UserCalendarRepository.java` - Calendar repository with session and user queries
    - `CalendarTemplateRepository.java` - Template repository with active template queries
    - `CalendarOrderRepository.java` - Order repository with status-based queries
*   **Recommendation:** You MUST test all custom query methods in each repository, including:
    - `findByOAuthSubject()` in CalendarUserRepository
    - `findByUserAndYear()` in UserCalendarRepository
    - `findActiveTemplates()` in CalendarTemplateRepository
    - `findByStatusOrderByCreatedDesc()` in CalendarOrderRepository

#### Existing Test Infrastructure (CRITICAL)

*   **File:** `src/test/java/villagecompute/calendar/data/repositories/TestDataCleaner.java`
*   **Summary:** This is a critical utility class that MUST be used in all tests to avoid foreign key violations
*   **Usage:** Inject `TestDataCleaner` and call `testDataCleaner.deleteAll()` in your `@BeforeEach` method
*   **Recommendation:** You MUST use this pattern in ALL your tests. The `TestDataCleaner` deletes data in the correct order to respect foreign key constraints: CalendarOrder → UserCalendar → PageView → AnalyticsRollup → DelayedJob → CalendarUser → CalendarTemplate

#### Existing Test Examples (USE AS TEMPLATES)

*   **File:** `src/test/java/villagecompute/calendar/data/models/CalendarUserTest.java`
*   **Summary:** This is an EXCELLENT reference implementation showing the complete testing pattern
*   **Key Patterns Demonstrated:**
    - Uses `@QuarkusTest` annotation at class level
    - Injects `Validator`, `TestDataCleaner`, `ObjectMapper`, and `EntityManager`
    - Uses `@BeforeEach` with `@Transactional` to clean data before each test
    - Uses `@Transactional` annotation on tests that perform database operations
    - Tests validation constraints using `validator.validate()`
    - Tests relationships including cascade persist and cascade delete
    - Creates helper methods like `createValidUser()` to reduce duplication
*   **Code Example (lines 22-42):**
    ```java
    @QuarkusTest
    class CalendarUserTest {
        @Inject
        Validator validator;

        @Inject
        TestDataCleaner testDataCleaner;

        @Inject
        ObjectMapper objectMapper;

        @Inject
        jakarta.persistence.EntityManager entityManager;

        @BeforeEach
        @Transactional
        void setUp() {
            testDataCleaner.deleteAll();
        }
    }
    ```
*   **Recommendation:** You MUST follow this exact pattern for all entity tests

*   **File:** `src/test/java/villagecompute/calendar/data/repositories/CalendarUserRepositoryTest.java`
*   **Summary:** This shows the repository testing pattern
*   **Key Patterns Demonstrated:**
    - Injects the repository under test
    - Tests all custom query methods (findByOAuthSubject, findByEmail, etc.)
    - Tests unique constraints using assertThrows
    - Uses `repository.flush()` to force constraint validation
*   **Code Example (lines 18-31):**
    ```java
    @QuarkusTest
    class CalendarUserRepositoryTest {
        @Inject
        TestDataCleaner testDataCleaner;

        @Inject
        CalendarUserRepository repository;

        @BeforeEach
        @Transactional
        void setUp() {
            testDataCleaner.deleteAll();
        }
    }
    ```
*   **Recommendation:** You MUST follow this pattern for all repository tests

#### JSONB Testing Requirements

*   **Entities with JSONB fields:**
    - `UserCalendar.configuration` (JsonNode)
    - `CalendarTemplate.configuration` (JsonNode)
    - `CalendarOrder.shippingAddress` (JsonNode)
*   **File:** `src/test/java/villagecompute/calendar/data/models/CalendarUserTest.java` (lines 318-333)
*   **Summary:** Shows how to create JSONB test data using ObjectMapper
*   **Pattern:**
    ```java
    @Inject
    ObjectMapper objectMapper;

    ObjectNode config = objectMapper.createObjectNode();
    config.put("key", "value");
    entity.configuration = config;
    entity.persist();
    entityManager.flush();

    // Verify after retrieval
    CalendarUser found = CalendarUser.findById(user.id);
    assertNotNull(found.configuration);
    assertEquals("value", found.configuration.get("key").asText());
    ```
*   **Recommendation:** You MUST test JSONB serialization/deserialization for all entities with JSONB fields. Verify that data persists correctly and can be retrieved.

### Implementation Tips & Notes

*   **Tip 1 - JaCoCo Configuration:** The `pom.xml` ALREADY has JaCoCo configured (lines 274-313) with a 70% coverage requirement at the PACKAGE level. You do NOT need to add JaCoCo configuration. The coverage check will run automatically with `./mvnw test`.

*   **Tip 2 - H2 In-Memory Database:** The project has `quarkus-jdbc-h2` dependency (line 230-232 in pom.xml) for in-memory testing. This is ALREADY configured. Your tests will automatically use H2 instead of PostgreSQL.

*   **Tip 3 - Test Data Cleanup Order:** The `TestDataCleaner` is CRITICAL. Based on foreign key relationships, you MUST clean data in this order:
    1. CalendarOrder (references UserCalendar and CalendarUser)
    2. UserCalendar (references CalendarUser and CalendarTemplate)
    3. PageView, AnalyticsRollup, DelayedJob (no dependencies)
    4. CalendarUser (parent of calendars and orders)
    5. CalendarTemplate (parent of calendars)

*   **Tip 4 - Entity Validation Testing:** ALL validation tests should use the injected `Validator` and NOT rely on database constraints. Use this pattern:
    ```java
    Set<ConstraintViolation<EntityType>> violations = validator.validate(entity);
    assertEquals(1, violations.size());
    assertEquals("fieldName", violations.iterator().next().getPropertyPath().toString());
    ```

*   **Tip 5 - Transaction Management:** Tests that perform database operations (persist, delete, update) MUST be annotated with `@Transactional`. Tests that only validate entities (not persisting) do NOT need `@Transactional`.

*   **Tip 6 - EntityManager Usage:** Inject `EntityManager` when you need to call `flush()` to force constraint validation or to test cascade operations. Example from existing tests:
    ```java
    @Inject
    jakarta.persistence.EntityManager entityManager;

    user.persist();
    entityManager.flush(); // Forces ID generation and constraint validation
    ```

*   **Tip 7 - Enum Constants:** Several entities have status constants (e.g., `CalendarOrder.STATUS_PENDING`, `CalendarOrder.STATUS_PAID`). Use these constants in your tests instead of hard-coded strings.

*   **Warning 1 - Foreign Key Dependencies:** When testing relationships, you MUST create parent entities first. For example:
    - To test `UserCalendar`, you must first create and persist `CalendarUser` and `CalendarTemplate`
    - To test `CalendarOrder`, you must first create and persist `CalendarUser` and `UserCalendar`
    - Look at `CalendarUserTest.testRelationships_CascadePersist()` (lines 312-346) for the correct pattern:
    ```java
    // Create user first
    CalendarUser user = createValidUser("user@test.com");
    user.persist();

    // Create template (required FK)
    CalendarTemplate template = new CalendarTemplate();
    template.name = "Test Template";
    template.configuration = objectMapper.createObjectNode();
    template.persist();

    // Now create calendar with both FKs
    UserCalendar calendar = new UserCalendar();
    calendar.user = user;
    calendar.template = template;
    calendar.name = "Test Calendar";
    calendar.year = 2025;
    calendar.persist();
    entityManager.flush();
    ```

*   **Warning 2 - Unique Constraints:** When testing unique constraints, you MUST call `repository.flush()` or `entityManager.flush()` to trigger the constraint violation. Without flush, the constraint won't be checked until the transaction commits.

*   **Note 1 - Existing Tests:** Based on my analysis, these test files already exist:
    - ✅ `CalendarUserTest.java` (entity tests)
    - ✅ `UserCalendarTest.java` (entity tests)
    - ✅ `CalendarTemplateTest.java` (entity tests)
    - ✅ `CalendarOrderTest.java` (entity tests)
    - ✅ `DelayedJobTest.java` (entity tests)
    - ✅ `PageViewTest.java` (entity tests)
    - ✅ `AnalyticsRollupTest.java` (entity tests)
    - ✅ `CalendarUserRepositoryTest.java` (repository tests)
    - ✅ `UserCalendarRepositoryTest.java` (repository tests)
    - ✅ `CalendarTemplateRepositoryTest.java` (repository tests)
    - ✅ `CalendarOrderRepositoryTest.java` (repository tests)

*   **Note 2 - Task Reality Check:** The task asks you to "create" tests, but ALL required tests already exist. Your actual task is to:
    1. **Verify tests run successfully** (`./mvnw test`)
    2. **Check coverage meets 70% threshold** (review JaCoCo report)
    3. **Add missing tests** if coverage is below 70%
    4. **Fix any failing tests** if they exist

*   **Note 3 - Package Naming:** The actual package names in the codebase are:
    - Entities: `villagecompute.calendar.data.models` (NOT `.model` as task spec suggests)
    - Repositories: `villagecompute.calendar.data.repositories` (NOT `.repository`)
    - This is already correctly implemented in existing tests.

### Required Test Coverage

Based on the task requirements, you MUST ensure comprehensive tests for:

1. **Entity Validation Tests (for EACH entity):**
   - Test `@NotNull` constraints (set field to null, verify violation)
   - Test `@Email` constraints (invalid email format)
   - Test `@Size` constraints (exceed max length, test boundary)
   - Test `@Min` / `@DecimalMin` constraints (for numeric fields)

2. **Entity Persistence Tests (for EACH entity):**
   - Test valid entity persists successfully
   - Test ID, created, updated, version fields are generated
   - Test JSONB fields serialize/deserialize correctly (where applicable)

3. **Relationship Tests (for entities with relationships):**
   - Test `@ManyToOne` relationships
   - Test `@OneToMany` relationships with cascade persist
   - Test cascade delete (orphanRemoval = true)
   - Test fetch strategies if specified

4. **Repository CRUD Tests (for EACH repository):**
   - Test `findById()`
   - Test `listAll()`
   - Test `persist()`
   - Test `delete()`
   - Test `count()`

5. **Repository Custom Query Tests (for EACH custom method):**
   - Test all static finder methods in entity classes
   - Test all custom query methods in repository classes
   - Test query ordering (verify results are in correct order)
   - Test "not found" scenarios (verify empty Optional or empty list)

### Entities to Test (Complete List)

Based on my codebase analysis, here are all entities that need tests:

1. ✅ `CalendarUser` - Test file EXISTS: `CalendarUserTest.java`
2. ✅ `UserCalendar` - Test file EXISTS: `UserCalendarTest.java`
3. ✅ `CalendarTemplate` - Test file EXISTS: `CalendarTemplateTest.java`
4. ✅ `CalendarOrder` - Test file EXISTS: `CalendarOrderTest.java`
5. ✅ `DelayedJob` - Test file EXISTS: `DelayedJobTest.java`
6. ✅ `PageView` - Test file EXISTS: `PageViewTest.java`
7. ✅ `AnalyticsRollup` - Test file EXISTS: `AnalyticsRollupTest.java`
8. ❓ `DelayedJobQueue` - Verify if this needs testing (may be utility class)
9. ❓ Enum classes - Verify if enums need testing (typically not required for simple enums)

### Acceptance Criteria Checklist

Before completing this task, verify:

- [ ] All required entity classes have comprehensive test files
- [ ] All repository classes have test files
- [ ] All validation constraints are tested (@NotNull, @Email, @Size, etc.)
- [ ] All JSONB fields have serialization/deserialization tests
- [ ] All relationship mappings are tested (cascade, fetch)
- [ ] All custom query methods are tested (with "found" and "not found" scenarios)
- [ ] `./mvnw test` runs without failures
- [ ] JaCoCo report generated at `target/site/jacoco/index.html`
- [ ] Coverage >70% for `villagecompute.calendar.data.models` package
- [ ] Coverage >70% for `villagecompute.calendar.data.repositories` package
- [ ] No tests use external databases (all use H2 in-memory)
- [ ] All tests use `TestDataCleaner.deleteAll()` in `@BeforeEach`

### Execution Strategy

**IMPORTANT: Tests already exist! Your primary task is verification and gap-filling, NOT creation from scratch.**

1. **First:** Run the test suite and check current status
   ```bash
   ./mvnw clean test
   ```

2. **Second:** Review the JaCoCo coverage report
   - Open `target/site/jacoco/index.html` in a browser
   - Check coverage for `villagecompute.calendar.data.models` package
   - Check coverage for `villagecompute.calendar.data.repositories` package
   - Identify any classes or methods below 70% coverage

3. **Third:** If tests pass and coverage >70%, document and complete the task

4. **Fourth:** If coverage <70% or tests fail:
   - Review failing tests and fix them
   - Identify uncovered code paths in JaCoCo report
   - Add tests for uncovered paths (edge cases, error conditions, etc.)
   - Focus on areas highlighted in red/yellow in JaCoCo report

5. **Fifth:** Run tests again to verify fixes
   ```bash
   ./mvnw test
   ```

6. **Final:** Verify all acceptance criteria are met and mark task complete
