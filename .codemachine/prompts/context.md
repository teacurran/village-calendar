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

The following are relevant architectural principles extracted from the task requirements and codebase analysis.

### Testing Strategy Requirements

**Coverage Target:** Achieve >70% code coverage for model and repository packages using JaCoCo

**Testing Framework Stack:**
- JUnit 5 for test framework
- Quarkus @QuarkusTest for integration testing
- H2 in-memory database for tests (PostgreSQL in production)
- Jakarta Bean Validation for constraint testing
- Mockito for mocking (when needed)

**Test Categories Required:**
1. **Entity Validation Tests** - Verify all Jakarta Bean Validation constraints (@NotNull, @Email, @Size)
2. **Relationship Tests** - Verify cascade operations, orphan removal, fetch strategies
3. **JSONB Serialization Tests** - Verify JsonNode fields persist and retrieve correctly
4. **Repository CRUD Tests** - Verify basic Panache operations (persist, findById, delete, listAll)
5. **Custom Query Tests** - Verify all custom finder methods work correctly

### Data Model Architecture

**Entity Count:** 11 core entities (8 confirmed, 3 to verify)

**Key Design Patterns:**
- **ActiveRecord Pattern** - Entities extend PanacheEntity with static finder methods
- **Repository Pattern** - Separate repository classes for complex queries
- **JSONB Storage** - PostgreSQL JSONB for flexible configuration data
- **Optimistic Locking** - Version field for concurrency control
- **Soft Deletes** - Cascade operations manage data lifecycle

**Key Relationships:**
- CalendarUser (1) → (*) UserCalendar → (*) CalendarOrder
- CalendarTemplate (1) → (*) UserCalendar
- UserCalendar.configuration stored as JSONB

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase.

### Relevant Existing Code

#### **File:** `src/main/java/villagecompute/calendar/data/models/UserCalendar.java`
- **Summary**: Primary calendar entity with JPA annotations, validation constraints, and Panache ActiveRecord pattern.
- **Key Features**:
  - Extends `DefaultPanacheEntityWithTimestamps` (provides id, created, updated, version)
  - Has JSONB `configuration` field with `@JdbcTypeCode(SqlTypes.JSON)` and Jackson JsonNode mapping
  - Includes custom query methods: `findBySession()`, `findByUserAndYear()`, `findByUser()`, `findBySessionAndName()`, `findPublicById()`, `findPublicCalendars()`
  - Has relationships: `@ManyToOne` to CalendarUser and CalendarTemplate, `@OneToMany` to CalendarOrder with `CascadeType.ALL` and `orphanRemoval = true`
  - Validation: `@NotNull` on name and year, `@Size(max = 255)` on name and sessionId
- **Recommendation**: Your tests MUST verify all custom query methods, JSONB serialization, and cascade operations. Test `copyForSession()` helper method.

#### **File:** `src/main/java/villagecompute/calendar/data/models/CalendarUser.java`
- **Summary**: User entity with OAuth authentication fields and admin flag.
- **Key Features**:
  - Has `@Email` validation on email field, `@NotNull` on oauth_provider, oauth_subject, email
  - Unique constraint on (oauth_provider, oauth_subject)
  - Custom finder methods: `findByOAuthSubject()`, `findByEmail()`, `findActiveUsersSince()`
  - Has `updateLastLogin()` helper method that updates lastLoginAt and persists
  - Static methods: `hasAdminUsers()`, `findAdminUsers()`
  - Relationships: `@OneToMany` to UserCalendar and CalendarOrder with `CascadeType.ALL` and `orphanRemoval = true`
- **Recommendation**: Tests MUST verify email validation, unique constraint enforcement, all custom finder methods, and cascade delete operations.

#### **File:** `src/main/java/villagecompute/calendar/data/repositories/UserCalendarRepository.java`
- **Summary**: Repository implementing PanacheRepository<UserCalendar> with custom query methods.
- **Key Features**:
  - Methods: `findByUserAndYear()`, `findByUser()`, `findBySession()`, `findPublicById()`, `findPublicCalendars(limit)`, `findByTemplate()`, `findByYear()`
  - Uses HQL queries with ordering (e.g., "ORDER BY updated DESC", "ORDER BY year DESC")
- **Recommendation**: Repository tests MUST verify all custom query methods return correct results with proper ordering and filtering.

#### **File:** `src/test/java/villagecompute/calendar/data/models/UserCalendarTest.java` ✅ EXISTS
- **Summary**: EXCELLENT reference implementation showing complete testing patterns.
- **Key Patterns Demonstrated**:
  - Uses `@QuarkusTest` annotation at class level
  - Injects `Validator`, `TestDataCleaner`, `ObjectMapper`, `EntityManager`
  - Uses `@BeforeEach` with `@Transactional` to clean data before each test
  - Uses `@Transactional` on tests that persist data
  - Tests validation constraints using `validator.validate()`
  - Tests JSONB serialization by creating ObjectNode and verifying after retrieval
  - Tests relationships including cascade operations
  - Tests custom query methods (findBySession, findByUserAndYear, findByUser, etc.)
  - Uses helper methods like `createValidCalendar()` and `createSessionCalendar()` to reduce duplication
- **Recommendation**: This file is your GOLD STANDARD template. Copy this structure for any missing entity tests.

#### **File:** `src/test/java/villagecompute/calendar/data/models/CalendarUserTest.java` ✅ EXISTS
- **Summary**: Complete entity test showing validation testing patterns.
- **Key Patterns**:
  - Multiple validation tests for different constraints (null checks, email format, field length)
  - Uses `validator.validate(entity)` and checks `violations.size()` and `violation.getPropertyPath()`
  - Tests for unique constraints (not shown in snippet but pattern exists)
  - Tests custom finder methods with Optional return types
  - Tests relationship cascade persist and cascade remove
  - Tests the `updateLastLogin()` helper method
- **Recommendation**: Use this as template for comprehensive validation testing. Every constraint needs a test.

#### **File:** `src/test/java/villagecompute/calendar/data/repositories/UserCalendarRepositoryTest.java` ✅ EXISTS
- **Summary**: Complete repository test example.
- **Key Patterns**:
  - Injects repository under test, testDataCleaner, and related repositories
  - Creates test data in @BeforeEach (testUser, testTemplate)
  - Each test method creates specific test data and verifies query results
  - Tests verify ordering (e.g., year DESC), filtering (e.g., by year, by session), and limiting (e.g., findPublicCalendars(10))
  - Uses helper methods `createUserCalendar()` and `createSessionCalendar()`
- **Recommendation**: Follow this pattern for all repository tests. Create minimal test data, execute query, assert results.

#### **File:** `src/test/java/villagecompute/calendar/data/repositories/TestDataCleaner.java` ⚠️ CRITICAL
- **Summary**: Utility class for cleaning test database in correct FK constraint order.
- **Deletion Order**: CalendarOrder → UserCalendar → PageView → AnalyticsRollup → DelayedJob → CalendarUser → CalendarTemplate
- **Usage Pattern**:
  ```java
  @Inject TestDataCleaner testDataCleaner;

  @BeforeEach
  @Transactional
  void setUp() {
      testDataCleaner.deleteAll();
      // create test data
  }
  ```
- **Recommendation**: You MUST inject and use `TestDataCleaner.deleteAll()` in every test's @BeforeEach method. This prevents FK violations.

#### **File:** `pom.xml` ✅ JaCoCo CONFIGURED
- **Summary**: Maven config with all test dependencies and JaCoCo already configured.
- **Key Dependencies**:
  - `quarkus-junit5` (scope: test)
  - `quarkus-junit5-mockito` (scope: test)
  - `mockito-core` (scope: test)
  - `rest-assured` (scope: test)
  - `quarkus-jdbc-h2` (scope: test) - In-memory database
- **JaCoCo Configuration (lines 274-313)**:
  - Plugin version: 0.8.11
  - Executions: prepare-agent, report, jacoco-check
  - Coverage threshold: 70% (minimum 0.70) for LINE coverage at PACKAGE level
  - Report output: target/site/jacoco/index.html
- **Recommendation**: JaCoCo is ALREADY configured. Do NOT modify pom.xml. Just run `./mvnw test` and coverage will be generated.

### Implementation Tips & Notes

#### **Tip 1: Existing Tests Status**

Based on my codebase analysis, these test files ALREADY EXIST:

**Entity Tests (7 confirmed)**:
- ✅ CalendarUserTest.java
- ✅ UserCalendarTest.java
- ✅ CalendarTemplateTest.java
- ✅ CalendarOrderTest.java
- ✅ DelayedJobTest.java
- ✅ PageViewTest.java
- ✅ AnalyticsRollupTest.java

**Repository Tests (4 confirmed)**:
- ✅ CalendarUserRepositoryTest.java
- ✅ UserCalendarRepositoryTest.java
- ✅ CalendarTemplateRepositoryTest.java
- ✅ CalendarOrderRepositoryTest.java

**Your ACTUAL Task**: The task description says "create" tests, but tests already exist! Your real work is:
1. Run tests and verify they pass: `./mvnw test`
2. Check coverage meets 70%: Review `target/site/jacoco/index.html`
3. Add missing tests if coverage <70%
4. Fix any failing tests

#### **Tip 2: Standard Test Structure**

Every entity test MUST follow this structure:

```java
@QuarkusTest
class EntityNameTest {
    @Inject Validator validator;
    @Inject TestDataCleaner testDataCleaner;
    @Inject ObjectMapper objectMapper; // If entity has JSONB fields
    @Inject EntityManager entityManager;

    @BeforeEach
    @Transactional
    void setUp() {
        testDataCleaner.deleteAll();
        // Create test data for required FKs
    }

    @Test
    @Transactional
    void testValidEntity_Success() {
        // Given
        Entity entity = createValidEntity();

        // When
        entity.persist();
        entityManager.flush();

        // Then
        assertNotNull(entity.id);
        assertNotNull(entity.created);
        assertNotNull(entity.updated);
        assertEquals(0L, entity.version);
    }

    @Test
    void testInvalidEntity_NullField() {
        // Given
        Entity entity = createValidEntity();
        entity.requiredField = null;

        // When
        Set<ConstraintViolation<Entity>> violations = validator.validate(entity);

        // Then
        assertEquals(1, violations.size());
        assertEquals("requiredField", violations.iterator().next().getPropertyPath().toString());
    }

    private Entity createValidEntity() {
        Entity entity = new Entity();
        // Set all required fields
        return entity;
    }
}
```

#### **Tip 3: JSONB Testing Pattern**

For entities with JSONB fields (UserCalendar.configuration, CalendarTemplate.configuration, CalendarOrder.shippingAddress):

```java
@Inject ObjectMapper objectMapper;

@Test
@Transactional
void testJsonbSerialization() {
    // Given
    Entity entity = createValidEntity();
    ObjectNode jsonConfig = objectMapper.createObjectNode();
    jsonConfig.put("key1", "value1");
    jsonConfig.put("key2", 123);
    jsonConfig.put("key3", true);
    entity.configuration = jsonConfig;
    entity.persist();

    // When
    Entity found = Entity.findById(entity.id);

    // Then
    assertNotNull(found.configuration);
    assertEquals("value1", found.configuration.get("key1").asText());
    assertEquals(123, found.configuration.get("key2").asInt());
    assertTrue(found.configuration.get("key3").asBoolean());
}

@Test
@Transactional
void testJsonbSerialization_NullJson() {
    // Given
    Entity entity = createValidEntity();
    entity.configuration = null;
    entity.persist();

    // When
    Entity found = Entity.findById(entity.id);

    // Then
    assertNull(found.configuration);
}
```

#### **Tip 4: Relationship Testing Pattern**

For testing cascade operations:

```java
@Test
@Transactional
void testRelationships_CascadePersist() {
    // Given
    ParentEntity parent = createValidParent();
    parent.persist();

    ChildEntity child = new ChildEntity();
    child.parent = parent;
    child.name = "Test Child";
    child.persist();

    entityManager.flush();
    entityManager.clear(); // Clear to force reload

    // When
    ParentEntity foundParent = ParentEntity.findById(parent.id);
    int size = foundParent.children.size(); // Trigger lazy load

    // Then
    assertNotNull(foundParent.children);
    assertEquals(1, size);
    assertEquals("Test Child", foundParent.children.get(0).name);
}

@Test
@Transactional
void testRelationships_CascadeRemove() {
    // Given
    ParentEntity parent = createValidParent();
    parent.persist();

    ChildEntity child = new ChildEntity();
    child.parent = parent;
    child.name = "Test Child";
    child.persist();

    entityManager.flush();
    entityManager.clear(); // Reload fresh

    // When
    ParentEntity managedParent = ParentEntity.findById(parent.id);
    managedParent.delete();
    entityManager.flush();

    // Then
    assertEquals(0, ChildEntity.count()); // Children deleted via cascade
}
```

#### **Tip 5: Repository Test Pattern**

```java
@QuarkusTest
class EntityRepositoryTest {
    @Inject TestDataCleaner testDataCleaner;
    @Inject EntityRepository repository;

    @BeforeEach
    @Transactional
    void setUp() {
        testDataCleaner.deleteAll();
        // Create any required parent entities
    }

    @Test
    @Transactional
    void testCustomFinderMethod() {
        // Given
        Entity e1 = createEntity("param1");
        Entity e2 = createEntity("param1");
        Entity e3 = createEntity("param2");
        repository.persist(e1);
        repository.persist(e2);
        repository.persist(e3);

        // When
        List<Entity> results = repository.findByCustomParam("param1");

        // Then
        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(e -> "param1".equals(e.customParam)));
    }

    @Test
    @Transactional
    void testCustomFinderMethod_NotFound() {
        // Given (no matching entities)

        // When
        List<Entity> results = repository.findByCustomParam("nonexistent");

        // Then
        assertTrue(results.isEmpty());
    }
}
```

#### **Warning 1: Foreign Key Dependencies**

When testing entities with relationships, you MUST create parent entities first:

```java
// CORRECT order for UserCalendar tests:
CalendarUser user = new CalendarUser();
user.oauthProvider = "GOOGLE";
user.oauthSubject = "test-sub";
user.email = "test@example.com";
user.displayName = "Test User";
user.isAdmin = false;
userRepository.persist(user);

CalendarTemplate template = new CalendarTemplate();
template.name = "Test Template";
template.isActive = true;
template.displayOrder = 1;
template.configuration = objectMapper.createObjectNode();
templateRepository.persist(template);

// NOW you can create UserCalendar
UserCalendar calendar = new UserCalendar();
calendar.user = user;
calendar.template = template;
calendar.name = "Test Calendar";
calendar.year = 2025;
calendar.persist();
```

#### **Warning 2: EntityManager Flush Required**

To test constraints and force ID generation, you MUST call `entityManager.flush()`:

```java
// Without flush - ID might be null, constraints not checked
entity.persist();
assertNotNull(entity.id); // MIGHT FAIL

// With flush - ID generated, constraints checked
entity.persist();
entityManager.flush();
assertNotNull(entity.id); // GUARANTEED to work
```

#### **Warning 3: Package Name Discrepancy**

The task spec mentions paths like `src/test/java/villagecompute/calendar/model/` but the ACTUAL paths are:
- Models: `src/test/java/villagecompute/calendar/data/models/`
- Repositories: `src/test/java/villagecompute/calendar/data/repositories/`

Use the ACTUAL paths that match the existing codebase structure.

#### **Note 1: Test Coverage Requirements**

JaCoCo will check coverage at PACKAGE level with 70% minimum:
- Package: `villagecompute.calendar.data.models` must be ≥70%
- Package: `villagecompute.calendar.data.repositories` must be ≥70%

To improve coverage, focus on:
1. Testing all branches in if/else statements
2. Testing both success and failure paths
3. Testing edge cases (null, empty, boundary values)
4. Testing all methods in entity and repository classes

#### **Note 2: Running Tests**

Commands to run tests and check coverage:

```bash
# Run all tests
./mvnw clean test

# Run tests and generate coverage report
./mvnw clean test jacoco:report

# View coverage report (open in browser)
open target/site/jacoco/index.html

# Run tests for specific package
./mvnw test -Dtest=villagecompute.calendar.data.models.*

# Run specific test class
./mvnw test -Dtest=UserCalendarTest
```

#### **Note 3: Required Test Categories**

For EACH entity, ensure these test categories exist:

1. **Validation Tests**:
   - `testValidEntity_Success()` - Valid entity persists successfully
   - `testInvalidEntity_NullField()` - For each @NotNull field
   - `testInvalidEntity_EmailFormat()` - For @Email fields
   - `testInvalidEntity_FieldTooLong()` - For @Size fields

2. **JSONB Tests** (if entity has JSONB fields):
   - `testJsonbSerialization()` - Complex object persists and retrieves correctly
   - `testJsonbSerialization_NullJson()` - Null JSONB field handles correctly

3. **Relationship Tests** (if entity has relationships):
   - `testRelationships_ManyToOne()` - Parent relationship works
   - `testRelationships_OneToMany()` - Collection relationship works
   - `testRelationships_CascadePersist()` - Cascade operations work
   - `testRelationships_CascadeRemove()` - Orphan removal works

4. **Custom Query Tests** (for each static finder method):
   - `testFindByCustomParam()` - Method returns correct results
   - `testFindByCustomParam_NotFound()` - Method returns empty when not found
   - Verify ordering if query includes ORDER BY clause

For EACH repository, ensure:

1. **CRUD Tests**:
   - Basic Panache operations (persist, findById, delete, listAll, count)

2. **Custom Query Tests** (for each custom method):
   - Method returns correct results with proper filtering
   - Method returns results in correct order
   - Method handles "not found" scenario correctly
   - Method respects limit/pagination parameters

---

## 4. Execution Strategy

**IMPORTANT: Most tests already exist! Focus on verification and gap-filling.**

### Phase 1: Run Existing Tests

```bash
./mvnw clean test
```

**Expected Outcome**: Tests should pass. If any fail, read the error messages carefully.

### Phase 2: Check Coverage

```bash
./mvnw clean test jacoco:report
open target/site/jacoco/index.html
```

**In the JaCoCo report**:
1. Navigate to `villagecompute.calendar.data.models` package
2. Check line coverage percentage - must be ≥70%
3. Navigate to `villagecompute.calendar.data.repositories` package
4. Check line coverage percentage - must be ≥70%

**If coverage ≥70%**: Task is complete! Document the results and mark done.

**If coverage <70%**: Proceed to Phase 3.

### Phase 3: Identify Coverage Gaps

In the JaCoCo report:
- Red lines = not covered
- Yellow lines = partially covered (some branches not tested)
- Green lines = fully covered

Click on class names to see line-by-line coverage. Identify:
1. Which entity classes have low coverage
2. Which repository classes have low coverage
3. Which methods are not tested
4. Which branches are not tested (if/else, switch, try/catch)

### Phase 4: Add Missing Tests

Based on gaps identified in Phase 3, add tests for:
1. Untested validation constraints
2. Untested relationship operations
3. Untested custom query methods
4. Untested edge cases (null handling, boundary conditions)
5. Untested error paths (exceptions, validation failures)

Use the existing test files as templates.

### Phase 5: Verify Success

```bash
./mvnw clean test jacoco:report
open target/site/jacoco/index.html
```

Verify:
- [ ] All tests pass (no failures)
- [ ] Coverage ≥70% for `villagecompute.calendar.data.models`
- [ ] Coverage ≥70% for `villagecompute.calendar.data.repositories`

### Phase 6: Document and Complete

If all acceptance criteria met:
1. Document test results (number of tests, coverage %)
2. Mark task as done in task tracker
3. Commit any new test files created

---

## 5. Acceptance Criteria Checklist

Before marking this task complete, verify:

- [ ] `./mvnw test` runs without failures
- [ ] Entity validation tests verify @NotNull, @Email, @Size constraints for ALL entities
- [ ] JSONB serialization tests exist for entities with JsonNode fields (UserCalendar, CalendarTemplate, CalendarOrder)
- [ ] Relationship tests confirm cascade and fetch behavior for entities with @ManyToOne/@OneToMany
- [ ] Repository tests verify CRUD operations (persist, findById, delete, listAll, count)
- [ ] Repository tests verify all custom query methods with "found" and "not found" scenarios
- [ ] JaCoCo report generated at `target/site/jacoco/index.html`
- [ ] Coverage ≥70% for `villagecompute.calendar.data.models` package
- [ ] Coverage ≥70% for `villagecompute.calendar.data.repositories` package
- [ ] No tests use external databases (all use H2 in-memory configured in pom.xml)
- [ ] All tests use `TestDataCleaner.deleteAll()` in `@BeforeEach` method
- [ ] All tests follow the established patterns from existing test files
