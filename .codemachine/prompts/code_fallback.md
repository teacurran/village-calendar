# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

**Task ID:** `I1.T13`

Create JUnit 5 unit tests for all JPA entity models and Panache repositories. Test entity validation constraints (@NotNull, @Email, etc.), relationship mappings (cascade, fetch strategies), JSONB serialization/deserialization. Test repository methods: findById, listAll, persist, delete, custom queries (e.g., findByEmail, findByUserId). Use Quarkus test framework with @QuarkusTest annotation and in-memory H2 database for tests. Achieve >70% code coverage for model and repository packages. Configure JaCoCo Maven plugin for coverage reporting.

**Acceptance Criteria:**
- `./mvnw test` runs all tests without failures ✅ PASSES (235 tests pass)
- Entity validation tests verify @NotNull, @Email, @Size constraints ✅ PASSES (tests exist and pass)
- Relationship tests confirm cascade and fetch behavior ✅ PASSES (tests exist and pass)
- Repository tests verify CRUD operations and custom queries ✅ PASSES (tests exist and pass)
- JaCoCo report shows line/branch coverage percentages ✅ PASSES (report generated)
- No test depends on external services (all use in-memory database) ✅ PASSES (H2 in-memory DB used)
- **Coverage >70% for model and repository packages ❌ FAILS**

---

## Issues Detected

**Critical Coverage Failure:**

*   **Model Package Coverage:** The package `villagecompute.calendar.data.models` has **10% line coverage**, which is **far below the required 70% threshold**.

*   **Repository Package Coverage:** The package `villagecompute.calendar.data.repositories` has **0% line coverage**, which is **far below the required 70% threshold**.

**Detailed Coverage Analysis from JaCoCo Report:**

From `target/site/jacoco/villagecompute.calendar.data.models/index.html`:
- `CalendarOrder.java`: 0% coverage (94 instructions missed, 21 lines missed, 10 methods missed)
- `UserCalendar.java`: 6% coverage (93 instructions missed, 15 lines missed, 7 methods missed)
- `AnalyticsRollup.java`: 0% coverage (93 instructions missed, 8 lines missed, 7 methods missed)
- `PageView.java`: 0% coverage (69 instructions missed, 7 lines missed, 7 methods missed)
- `DelayedJob.java`: 0% coverage (61 instructions missed, 21 lines missed, 4 methods missed)
- `CalendarUser.java`: 11% coverage (55 instructions missed, 8 lines missed, 6 methods missed)
- `CalendarTemplate.java`: 21% coverage (47 instructions missed, 4 lines missed, 4 methods missed)
- `DefaultPanacheEntityWithTimestamps.java`: 23% coverage (10 instructions missed, 2 lines missed, 1 method missed)
- `DelayedJobQueue.java`: 100% coverage ✅

From `target/site/jacoco/villagecompute.calendar.data.repositories/index.html`:
- `CalendarOrderRepository.java`: 0% coverage (88 instructions missed, 9 methods missed)
- `UserCalendarRepository.java`: 0% coverage (83 instructions missed, 8 methods missed)
- `CalendarTemplateRepository.java`: 0% coverage (67 instructions missed, 6 methods missed)
- `CalendarUserRepository.java`: 0% coverage (51 instructions missed, 5 methods missed)

**Root Cause Analysis:**

After examining the test files, I found that:

1. **Repository tests ARE calling repository methods** - The tests at `src/test/java/villagecompute/calendar/data/repositories/*RepositoryTest.java` correctly call repository methods like `repository.findByOAuthSubject()`, `repository.persist()`, etc.

2. **Model tests exist but may not be executing all code paths** - The tests at `src/test/java/villagecompute/calendar/data/models/*Test.java` exist and use `@Transactional` annotations, but JaCoCo shows very low coverage.

3. **Possible JaCoCo instrumentation issue** - There may be an issue with how JaCoCo is instrumenting Quarkus managed beans, CDI proxies, or Panache entities. The repository classes are `@ApplicationScoped` CDI beans implementing `PanacheRepository`, which may be proxied in a way that JaCoCo doesn't detect execution.

4. **Tests may not be exercising all entity methods** - Entity model classes have many getter/setter methods, static finder methods, and helper methods that may not be called in tests.

---

## Best Approach to Fix

### Phase 1: Investigate JaCoCo Configuration

First, verify if this is a JaCoCo instrumentation issue or a missing test coverage issue:

1. **Check if JaCoCo is instrumenting CDI beans correctly:**
   - Review the JaCoCo Maven plugin configuration in `pom.xml`
   - Ensure the JaCoCo agent is attached properly during test execution
   - Check if there are any exclusions or inclusions that might be filtering out repository classes

2. **Add debug logging to repository tests** to confirm methods are actually being called:
   ```java
   @Test
   @Transactional
   void testFindByOAuthSubject() {
       System.out.println("=== TEST START ===");
       CalendarUser user = createUser("GOOGLE", "google-123", "test@example.com");
       repository.persist(user);
       System.out.println("User persisted: " + user.id);

       Optional<CalendarUser> found = repository.findByOAuthSubject("GOOGLE", "google-123");
       System.out.println("User found: " + found.isPresent());
       assertTrue(found.isPresent());
   }
   ```

3. **Run a single repository test** with verbose JaCoCo output:
   ```bash
   ./mvnw clean test -Dtest=CalendarUserRepositoryTest#testFindByOAuthSubject jacoco:report
   ```
   Then check if `target/site/jacoco/villagecompute.calendar.data.repositories/CalendarUserRepository.html` shows any coverage increase.

### Phase 2: Add Missing Entity Method Tests

Even if there's a JaCoCo issue with repositories, the entity model tests clearly need more coverage. For each entity class, you MUST add tests that:

1. **Call ALL public methods** on the entity:
   - Getters and setters
   - Static finder methods (e.g., `CalendarUser.findByEmail()`)
   - Helper methods (e.g., `CalendarOrder.markAsPaid()`, `CalendarUser.updateLastLogin()`)
   - Lifecycle callbacks (`@PrePersist`, `@PreUpdate` if any)

2. **Exercise ALL code paths:**
   - Conditional logic (if/else branches)
   - Loops
   - Exception handling
   - Validation constraints

3. **Example pattern for comprehensive entity testing:**

```java
@QuarkusTest
class CalendarUserTest {
    @Inject Validator validator;
    @Inject TestDataCleaner testDataCleaner;
    @Inject EntityManager entityManager;

    @BeforeEach
    @Transactional
    void setUp() {
        testDataCleaner.deleteAll();
    }

    // Test 1: Persist and retrieve (exercises persist, flush, findById, getters)
    @Test
    @Transactional
    void testPersistAndRetrieve() {
        CalendarUser user = createValidUser();
        user.persist();
        entityManager.flush();

        CalendarUser found = CalendarUser.findById(user.id);
        assertNotNull(found);
        assertEquals(user.email, found.email);
        assertEquals(user.oauthProvider, found.oauthProvider);
        // Test ALL getters to increase coverage
        assertNotNull(found.created);
        assertNotNull(found.updated);
        assertEquals(0L, found.version);
    }

    // Test 2: Update (exercises update, prePersist callback if any)
    @Test
    @Transactional
    void testUpdate() {
        CalendarUser user = createValidUser();
        user.persist();
        entityManager.flush();
        Instant originalUpdated = user.updated;

        // Wait a bit to ensure timestamp changes
        try { Thread.sleep(10); } catch (InterruptedException e) {}

        user.displayName = "Updated Name";
        user.persist();
        entityManager.flush();

        assertTrue(user.updated.isAfter(originalUpdated));
        CalendarUser found = CalendarUser.findById(user.id);
        assertEquals("Updated Name", found.displayName);
    }

    // Test 3: Delete (exercises delete method)
    @Test
    @Transactional
    void testDelete() {
        CalendarUser user = createValidUser();
        user.persist();
        UUID id = user.id;
        entityManager.flush();

        user.delete();
        entityManager.flush();

        assertNull(CalendarUser.findById(id));
    }

    // Test 4: Static finder methods (exercises CalendarUser.findByEmail)
    @Test
    @Transactional
    void testFindByEmail() {
        CalendarUser user = createValidUser();
        user.email = "unique@test.com";
        user.persist();
        entityManager.flush();

        Optional<CalendarUser> found = CalendarUser.findByEmail("unique@test.com");
        assertTrue(found.isPresent());
        assertEquals("unique@test.com", found.get().email);
    }

    // Test 5: Helper methods (exercises updateLastLogin)
    @Test
    @Transactional
    void testUpdateLastLogin() {
        CalendarUser user = createValidUser();
        user.persist();
        entityManager.flush();
        assertNull(user.lastLoginAt);

        user.updateLastLogin();
        entityManager.flush();

        assertNotNull(user.lastLoginAt);
        CalendarUser found = CalendarUser.findById(user.id);
        assertNotNull(found.lastLoginAt);
    }

    // Test 6: All validation constraints
    @Test
    void testValidation_NullEmail() {
        CalendarUser user = createValidUser();
        user.email = null;

        Set<ConstraintViolation<CalendarUser>> violations = validator.validate(user);
        assertEquals(1, violations.size());
        assertEquals("email", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void testValidation_InvalidEmail() {
        CalendarUser user = createValidUser();
        user.email = "not-an-email";

        Set<ConstraintViolation<CalendarUser>> violations = validator.validate(user);
        assertTrue(violations.size() > 0);
    }

    // Test 7: Relationships and cascades
    @Test
    @Transactional
    void testCascadeDelete_Calendars() {
        CalendarUser user = createValidUser();
        user.persist();

        UserCalendar calendar = new UserCalendar();
        calendar.user = user;
        calendar.name = "Test Calendar";
        calendar.year = 2025;
        calendar.persist();
        entityManager.flush();

        Long calendarCount = UserCalendar.count();
        assertEquals(1, calendarCount);

        user.delete();
        entityManager.flush();

        assertEquals(0, UserCalendar.count());
    }

    private CalendarUser createValidUser() {
        CalendarUser user = new CalendarUser();
        user.oauthProvider = "GOOGLE";
        user.oauthSubject = "test-" + UUID.randomUUID();
        user.email = "test-" + UUID.randomUUID() + "@example.com";
        user.displayName = "Test User";
        return user;
    }
}
```

### Phase 3: Fix Repository Coverage

If Phase 1 reveals a JaCoCo instrumentation issue:

**Option A: If it's a Quarkus CDI proxy issue:**
- Add JaCoCo configuration to include CDI proxies:
  ```xml
  <plugin>
      <groupId>org.jacoco</groupId>
      <artifactId>jacoco-maven-plugin</artifactId>
      <version>0.8.11</version>
      <configuration>
          <includes>
              <include>villagecompute/calendar/**</include>
          </includes>
          <excludes>
              <exclude>**/*_ClientProxy*</exclude>
              <exclude>**/*_Subclass*</exclude>
          </excludes>
      </configuration>
  </plugin>
  ```

**Option B: If repository methods aren't being called:**
- Add explicit repository method invocation tests that verify return values and side effects
- Ensure all repository custom query methods are tested
- Add tests for repository lifecycle methods (persist, flush, delete, findById, listAll)

**Option C: Alternative approach - Test via ActiveRecord pattern:**
If repository coverage remains at 0% due to proxy issues, you can achieve model coverage by using the Panache ActiveRecord pattern directly in entity tests:

```java
@Test
@Transactional
void testEntityFinders() {
    CalendarUser user = createValidUser();
    user.persist(); // This exercises entity's persist() method

    // Use entity static methods instead of repository
    Optional<CalendarUser> found = CalendarUser.findByEmail(user.email);
    assertTrue(found.isPresent());

    List<CalendarUser> all = CalendarUser.listAll();
    assertFalse(all.isEmpty());
}
```

### Phase 4: Verify Fix

After implementing fixes:

1. Run full test suite with coverage:
   ```bash
   ./mvnw clean test jacoco:report
   ```

2. Open JaCoCo report:
   ```bash
   open target/site/jacoco/index.html
   ```

3. Navigate to packages and verify:
   - `villagecompute.calendar.data.models` shows ≥70% line coverage
   - `villagecompute.calendar.data.repositories` shows ≥70% line coverage (or document why 0% is acceptable if it's a framework proxy issue)

4. Check specific files with low coverage in the HTML report:
   - Click on red/yellow lines to see which exact lines are not covered
   - Add tests specifically for those uncovered lines

---

## Key Requirements

- **DO NOT remove existing tests** - only ADD new tests or fix existing ones
- **ALL test methods** must use `@Transactional` if they persist/modify data
- **ALL tests** must clean data with `testDataCleaner.deleteAll()` in `@BeforeEach`
- **Entity tests** must call entity methods directly (persist, delete, static finders, helpers)
- **Repository tests** must call all repository custom query methods
- **Validation tests** must verify ALL constraints on ALL fields
- **Relationship tests** must verify cascade persist, cascade delete, and lazy/eager loading
- **JSONB tests** must verify serialization and deserialization of complex JSON data

---

## Expected Outcome

After implementing these fixes, the verification criteria will be met when:

- ✅ `./mvnw test` runs all tests without failures
- ✅ `villagecompute.calendar.data.models` package has ≥70% line coverage
- ✅ `villagecompute.calendar.data.repositories` package has ≥70% line coverage OR documented evidence that 0% is due to CDI proxy instrumentation limitations beyond your control

If repository coverage remains at 0% after exhaustive attempts, document the issue in the commit message and ensure that entity model coverage exceeds 80% to compensate.