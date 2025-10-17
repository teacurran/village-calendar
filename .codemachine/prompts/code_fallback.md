# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

**Task ID:** `I1.T13`

Create JUnit 5 unit tests for all JPA entity models and Panache repositories. Test entity validation constraints (@NotNull, @Email, etc.), relationship mappings (cascade, fetch strategies), JSONB serialization/deserialization. Test repository methods: findById, listAll, persist, delete, custom queries (e.g., findByEmail, findByUserId). Use Quarkus test framework with @QuarkusTest annotation and in-memory H2 database for tests. Achieve >70% code coverage for model and repository packages. Configure JaCoCo Maven plugin for coverage reporting.

**Acceptance Criteria:**
- `./mvnw test` runs all tests without failures ✅ PASSES (220 tests pass)
- Entity validation tests verify @NotNull, @Email, @Size constraints
- Relationship tests confirm cascade and fetch behavior
- Repository tests verify CRUD operations and custom queries
- JaCoCo report shows line/branch coverage percentages ✅ Report generated
- No test depends on external services (all use in-memory database) ✅ PASSES
- **Coverage >70% for model and repository packages ❌ FAILS**

---

## Issues Detected

**Critical Coverage Failure:**

*   **Model Package Coverage:** The package `villagecompute.calendar.data.models` has **10% line coverage**, which is **far below the required 70% threshold**.

*   **Repository Package Coverage:** The package `villagecompute.calendar.data.repositories` has **0% line coverage**, which is **far below the required 70% threshold**.

*   **Root Cause Analysis:**
    -  All 220 tests execute successfully
    -  JaCoCo reports are generated correctly
    - However, JaCoCo shows that the actual production code in the entity models and repositories is NOT being executed during test runs
    - This indicates that either:
      1. The tests are not properly instantiating and calling methods on the entities/repositories, OR
      2. There's a configuration issue preventing JaCoCo from instrumenting the production classes, OR
      3. The tests are mostly testing validation logic via the `Validator` interface without actually persisting entities and exercising repository methods

**Specific Coverage Issues Found:**

From `target/site/jacoco/villagecompute.calendar.data.models/index.html`:
- `CalendarOrder.java`: 0% coverage (94 instructions missed, 21 lines missed)
- `UserCalendar.java`: 6% coverage (93 instructions missed, 15 lines missed)
- `AnalyticsRollup.java`: 0% coverage (93 instructions missed, 8 lines missed)
- `PageView.java`: 0% coverage (69 instructions missed, 7 lines missed)
- `DelayedJob.java`: 0% coverage (61 instructions missed, 21 lines missed)
- `CalendarUser.java`: 11% coverage (55 instructions missed, 8 lines missed)
- `CalendarTemplate.java`: 21% coverage (47 instructions missed, 4 lines missed)

From `target/site/jacoco/villagecompute.calendar.data.repositories/index.html`:
- `CalendarOrderRepository.java`: 0% coverage (88 instructions missed, 9 methods missed)
- `UserCalendarRepository.java`: 0% coverage (83 instructions missed, 8 methods missed)
- `CalendarTemplateRepository.java`: 0% coverage (67 instructions missed, 6 methods missed)
- `CalendarUserRepository.java`: 0% coverage (51 instructions missed, 5 methods missed)

---

## Best Approach to Fix

You MUST investigate and fix the coverage issues in two phases:

### Phase 1: Diagnose the Problem

1. **Check Test Implementation:** Open `src/test/java/villagecompute/calendar/data/models/CalendarUserTest.java` and verify:
   - Are tests actually calling `.persist()` on entities with `@Transactional`?
   - Are tests calling custom finder methods on entities (e.g., `CalendarUser.findByEmail()`)?
   - Are tests calling `entityManager.flush()` to force database operations?
   - Are tests only using `validator.validate()` without persisting?

2. **Check Repository Tests:** Open `src/test/java/villagecompute/calendar/data/repositories/CalendarUserRepositoryTest.java` and verify:
   - Are repository methods actually being called (e.g., `repository.persist()`, `repository.findByEmail()`)?
   - Are test methods annotated with `@Transactional`?
   - Is test data being created and persisted before calling repository methods?

3. **Run a Single Test with Debug:** Run one specific test and check if it actually exercises the production code:
   ```bash
   ./mvnw test -Dtest=CalendarUserTest#testPersistUser_Success
   ```
   Then check the JaCoCo report to see if `CalendarUser.java` coverage increased.

### Phase 2: Fix Coverage Gaps

Based on the diagnostic results, you need to:

**For Entity Tests** (to reach 70% coverage in `villagecompute.calendar.data.models`):

1. **Ensure ALL entity tests have these test categories:**
   - ✅ Validation tests (using `validator.validate()`) - these exist
   - ❌ **MISSING**: Persistence tests that actually call `.persist()` and `entityManager.flush()`
   - ❌ **MISSING**: Custom finder method tests that call static methods like `Entity.findByField()`
   - ❌ **MISSING**: Relationship tests that trigger cascade operations by actually deleting parent entities
   - ❌ **MISSING**: JSONB field tests that persist and retrieve entities with complex JSON data

2. **Add comprehensive test methods for each entity** following this pattern:

```java
@Test
@Transactional
void testPersistEntity_Success() {
    // Given
    Entity entity = createValidEntity();

    // When
    entity.persist();  // This MUST be called to exercise entity code
    entityManager.flush();

    // Then
    assertNotNull(entity.id);
    assertNotNull(entity.created);
    assertNotNull(entity.updated);

    // Verify retrieval exercises finder methods
    Entity found = Entity.findById(entity.id);
    assertNotNull(found);
    assertEquals(entity.name, found.name);
}

@Test
@Transactional
void testCustomFinderMethod() {
    // Given
    Entity e1 = createValidEntity();
    e1.email = "test1@example.com";
    e1.persist();
    entityManager.flush();

    // When - This exercises the custom query method
    Optional<Entity> result = Entity.findByEmail("test1@example.com");

    // Then
    assertTrue(result.isPresent());
    assertEquals("test1@example.com", result.get().email);
}

@Test
@Transactional
void testUpdateEntity() {
    // Given
    Entity entity = createValidEntity();
    entity.persist();
    entityManager.flush();
    Instant originalUpdated = entity.updated;

    // When
    entity.name = "Updated Name";
    entity.persist();  // Exercises update path
    entityManager.flush();

    // Then
    assertTrue(entity.updated.isAfter(originalUpdated));

    Entity found = Entity.findById(entity.id);
    assertEquals("Updated Name", found.name);
}

@Test
@Transactional
void testDeleteEntity() {
    // Given
    Entity entity = createValidEntity();
    entity.persist();
    UUID id = entity.id;
    entityManager.flush();

    // When
    entity.delete();  // Exercises delete method
    entityManager.flush();

    // Then
    assertNull(Entity.findById(id));
}
```

**For Repository Tests** (to reach 70% coverage in `villagecompute.calendar.data.repositories`):

1. **Ensure ALL repository methods are tested:**
   - Every custom query method (findByX, listAll, count methods)
   - CRUD operations via repository (persist, delete)
   - Methods with parameters and ordering clauses

2. **Add tests that actually call repository methods:**

```java
@Test
@Transactional
void testRepositoryPersist() {
    // Given
    Entity entity = createValidEntity();

    // When
    repository.persist(entity);  // MUST call repository method
    entityManager.flush();

    // Then
    assertNotNull(entity.id);
}

@Test
@Transactional
void testRepositoryFindByCustomParam() {
    // Given
    Entity e1 = createValidEntity();
    e1.status = "ACTIVE";
    repository.persist(e1);
    entityManager.flush();

    // When - MUST call repository custom query
    List<Entity> results = repository.findByStatus("ACTIVE");

    // Then
    assertEquals(1, results.size());
    assertEquals("ACTIVE", results.get(0).status);
}

@Test
@Transactional
void testRepositoryListAll() {
    // Given
    Entity e1 = createValidEntity();
    Entity e2 = createValidEntity();
    repository.persist(e1);
    repository.persist(e2);
    entityManager.flush();

    // When
    List<Entity> all = repository.listAll();

    // Then
    assertEquals(2, all.size());
}
```

### Phase 3: Verify Fix

After adding the missing tests:

1. Run tests and generate coverage:
   ```bash
   ./mvnw clean test
   ```

2. Check coverage report:
   ```bash
   open target/site/jacoco/index.html
   ```

3. Navigate to:
   - `villagecompute.calendar.data.models` → Verify ≥70% line coverage
   - `villagecompute.calendar.data.repositories` → Verify ≥70% line coverage

4. If coverage still below 70%, check which specific lines/methods are NOT covered in the JaCoCo HTML report (red/yellow lines) and add tests for those specific code paths.

---

## Key Requirements

- Do NOT remove existing tests - ADD new tests to increase coverage
- ALL new tests must use `@Transactional` annotation if they persist/modify data
- ALL tests must call `.persist()`, `.delete()`, or finder methods to exercise entity/repository code
- Use `entityManager.flush()` after persist operations to force database interaction
- Inject `EntityManager` and `TestDataCleaner` in all test classes
- Clean test data with `testDataCleaner.deleteAll()` in `@BeforeEach` method

After making fixes, the acceptance criteria will be met when:
- ✅ `./mvnw test` runs all tests without failures
- ✅ `villagecompute.calendar.data.models` package has ≥70% line coverage
- ✅ `villagecompute.calendar.data.repositories` package has ≥70% line coverage
