# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create JUnit 5 unit tests for all JPA entity models and Panache repositories. Test entity validation constraints (@NotNull, @Email, etc.), relationship mappings (cascade, fetch strategies), JSONB serialization/deserialization. Test repository methods: findById, listAll, persist, delete, custom queries (e.g., findByEmail, findByUserId). Use Quarkus test framework with @QuarkusTest annotation and in-memory H2 database for tests. Achieve >70% code coverage for model and repository packages. Configure JaCoCo Maven plugin for coverage reporting.

---

## Issues Detected

### Critical Issue: Coverage Below Required Threshold

All 275 tests pass successfully, but the coverage is far below the 70% requirement:

```
[ERROR] Coverage checks have not been met:
- villagecompute.calendar.data.models: 10% coverage (required: 70%)
- villagecompute.calendar.data.repositories: 0% coverage (required: 70%)
```

**Build Result**: FAILURE - `./mvnw verify` fails due to jacoco:check violation

### Root Cause Analysis

After reviewing the JaCoCo reports and git diff, the problem is:

1. **Repository classes have 0% coverage** - ALL 7 repository classes show 0% instruction coverage:
   - AnalyticsRollupRepository: 0% (115 instructions missed)
   - CalendarOrderRepository: 0% (99 instructions missed)
   - PageViewRepository: 0% (91 instructions missed)
   - UserCalendarRepository: 0% (83 instructions missed)
   - CalendarTemplateRepository: 0% (67 instructions missed)
   - DelayedJobRepository: 0% (63 instructions missed)
   - CalendarUserRepository: 0% (62 instructions missed)

2. **Three NEW repository files were added but NOT TESTED**:
   - `src/main/java/villagecompute/calendar/data/repositories/AnalyticsRollupRepository.java` (untracked)
   - `src/main/java/villagecompute/calendar/data/repositories/DelayedJobRepository.java` (untracked)
   - `src/main/java/villagecompute/calendar/data/repositories/PageViewRepository.java` (untracked)

3. **Model classes have only 10% coverage** with specific gaps:
   - CalendarOrder: 0% (94 instructions missed, 21 lines missed)
   - AnalyticsRollup: 0% (93 instructions missed, 8 lines missed)
   - PageView: 0% (69 instructions missed, 7 lines missed)
   - DelayedJob: 0% (61 instructions missed, 21 lines missed)
   - CalendarUser: 11% (55 of 62 instructions missed)
   - UserCalendar: 6% (93 of 99 instructions missed)
   - CalendarTemplate: 21% (47 of 60 instructions missed)

### Specific Code Changes in Git Diff

The git diff shows modifications were made to:
1. `CalendarOrderRepository.java` - Added 10 lines of custom query methods
2. `CalendarUserRepository.java` - Added 11 lines of custom query methods
3. `CalendarOrderTest.java` - Modified 122 lines
4. `CalendarUserRepositoryTest.java` - Modified 17 lines

But these changes did NOT achieve the required coverage.

---

## Best Approach to Fix

You MUST create comprehensive repository tests that achieve >70% coverage for BOTH packages. The 0% repository coverage indicates **repository test files either don't exist or aren't properly testing repository instance methods**.

### Step 1: Create Missing Repository Test Files

You MUST create test files for the three new repositories:

1. **`src/test/java/villagecompute/calendar/data/repositories/AnalyticsRollupRepositoryTest.java`**
   - Test all custom query methods: findByMetric, findByMetricAndDimension, findByMetricAndDimensionValue, findByTimeRange, findByMetricAndTimeRange, sumByMetricAndTimeRange
   - Test base PanacheRepository methods: persist, findById, listAll, delete, count
   - Use @QuarkusTest and @Transactional annotations
   - Inject TestDataCleaner and call deleteAll() in @BeforeEach
   - Create test data with proper metric_name, dimension_key, dimension_value, period_start, period_end
   - Verify query results match expected filtering and ordering

2. **`src/test/java/villagecompute/calendar/data/repositories/DelayedJobRepositoryTest.java`**
   - Test all custom query methods: findReadyToRun, findByQueue, findByActorId, findIncomplete, findFailed
   - Test that findReadyToRun respects the limit parameter
   - Test that findByQueue orders by priority DESC, runAt ASC
   - Create test data with different DelayedJobQueue values (EMAIL_GENERAL, EMAIL_ORDER_CONFIRMATION, EMAIL_SHIPPING_NOTIFICATION)
   - Test locked vs unlocked jobs
   - Test complete vs incomplete vs failed jobs

3. **`src/test/java/villagecompute/calendar/data/repositories/PageViewRepositoryTest.java`**
   - Test all custom query methods: findBySession, findByUser, findByPath, findByTimeRange, findByReferrer, countByPathAndTimeRange
   - Test ordering (DESC/ASC) for each query
   - Create test data with different sessions, users, paths, time ranges
   - Verify count queries return correct numbers

### Step 2: Fix Existing Repository Tests

The existing repository tests show 0% coverage, which means they're likely calling entity static methods instead of repository instance methods. You MUST:

1. **CalendarUserRepositoryTest.java** - Ensure ALL methods call `calendarUserRepository.method()` NOT `CalendarUser.method()`
   - Replace: `CalendarUser.findByOAuthSubject()` → `calendarUserRepository.findByOAuthSubject()`
   - Replace: `CalendarUser.findById()` → `calendarUserRepository.findById()`
   - Replace: `CalendarUser.persist()` → `calendarUserRepository.persist()`
   - Replace: `CalendarUser.delete()` → `calendarUserRepository.delete()`
   - Test all 6 custom methods: findByOAuthSubject, findByEmail, findActiveUsersSince, findByProvider, findAdmins, listActiveUsers

2. **CalendarOrderRepositoryTest.java** - Based on git diff, this file exists. Ensure it tests ALL repository instance methods:
   - findByUser, findByCalendar, findByStatus, findByStripePaymentIntentId, findByStatusOrderByCreatedDesc
   - All base Panache methods
   - Use repository instance, NOT static entity methods

3. **Verify other repository tests exist and properly test repository instances**:
   - UserCalendarRepositoryTest.java
   - CalendarTemplateRepositoryTest.java

### Step 3: Improve Model Test Coverage

Model tests exist but only achieve 10% coverage. You MUST add tests that exercise ALL code paths:

1. **For entities with 0% coverage**, create test files that test:
   - All validation constraints (@NotNull, @Email, @Size, @Min, etc.)
   - All fields can be set and retrieved
   - Persistence (create, read, update, delete)
   - Optimistic locking (version field increments)
   - Timestamp fields (created, updated) are auto-populated
   - JSONB fields (if applicable) serialize/deserialize correctly

2. **For entities with low coverage** (CalendarUser 11%, UserCalendar 6%, CalendarTemplate 21%):
   - Review existing tests and identify missed code paths
   - Add tests for ALL fields (especially profileImageUrl, isAdmin, configuration, etc.)
   - Test relationship loading (lazy collection access)
   - Test cascade operations (persist parent → child persists, delete parent → child deletes)
   - Test all custom finder methods

### Step 4: Use Correct Test Patterns

You MUST follow these patterns in ALL repository tests:

```java
@QuarkusTest
public class SomeRepositoryTest {

    @Inject
    SomeRepository repository;  // Inject repository instance

    @Inject
    TestDataCleaner testDataCleaner;

    @Inject
    EntityManager entityManager;

    @BeforeEach
    @Transactional
    void setUp() {
        testDataCleaner.deleteAll();  // Clean state
    }

    @Test
    @Transactional
    void testFindByCustomQuery() {
        // Create test data using repository.persist()
        SomeEntity entity = new SomeEntity();
        entity.field = "value";
        repository.persist(entity);  // Use repository instance!
        entityManager.flush();

        // Test custom query using repository instance
        List<SomeEntity> results = repository.findByCustomQuery("value");

        // Verify results
        assertEquals(1, results.size());
        assertEquals("value", results.get(0).field);
    }
}
```

**CRITICAL**: NEVER call `Entity.findById()`, `Entity.persist()`, or other static methods in repository tests. ALWAYS use `repository.findById()`, `repository.persist()`, etc.

---

## Verification Steps

After making changes, verify success with:

```bash
./mvnw clean verify
```

This command will:
1. Run all 275+ tests (must pass with 0 failures)
2. Generate JaCoCo coverage report
3. Check coverage thresholds (both packages must reach 70%)
4. **Build will only succeed when coverage gates pass**

You can also view detailed coverage:
```bash
open target/site/jacoco/index.html
```

---

## Success Criteria

- [ ] `./mvnw verify` completes successfully without coverage violations
- [ ] `villagecompute.calendar.data.models` package shows ≥70% line coverage
- [ ] `villagecompute.calendar.data.repositories` package shows ≥70% line coverage
- [ ] All tests pass (0 failures, 0 errors)
- [ ] Three new repository test files created: AnalyticsRollupRepositoryTest, DelayedJobRepositoryTest, PageViewRepositoryTest
- [ ] All repository tests use repository instance methods, NOT entity static methods
- [ ] JaCoCo HTML report shows green coverage bars for both target packages
