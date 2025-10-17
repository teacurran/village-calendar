# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create JUnit 5 unit tests for all JPA entity models and Panache repositories. Test entity validation constraints (@NotNull, @Email, etc.), relationship mappings (cascade, fetch strategies), JSONB serialization/deserialization. Test repository methods: findById, listAll, persist, delete, custom queries (e.g., findByEmail, findByUserId). Use Quarkus test framework with @QuarkusTest annotation and in-memory H2 database for tests. Achieve >70% code coverage for model and repository packages. Configure JaCoCo Maven plugin for coverage reporting.

---

## Issues Detected

### Critical Issue: Coverage Below Required Threshold

All 275 tests pass successfully, but the coverage gates are failing:

```
[ERROR] Coverage checks have not been met:
- villagecompute.calendar.data.models: 10% coverage (required: 70%)
- villagecompute.calendar.data.repositories: 0% coverage (required: 70%)
```

**Build Result**: FAILURE - `./mvnw verify` fails due to jacoco:check violation

---

## Root Cause Analysis

After reviewing the test code and JaCoCo reports, the problem is that **tests are calling entity static methods (Panache Active Record pattern) instead of going through repository instances**. This means:

1. **Repository classes have 0% coverage** because repository instance methods are never called
2. **Entity classes have low coverage** because tests only exercise validation and finder methods, not all code paths

### Specific Examples of the Problem:

**In CalendarOrderTest.java** (and similar model tests):
- Line 221: `CalendarOrder found = CalendarOrder.findById(order.id);` ❌ Calls entity static method
- Line 256: `List<CalendarOrder> orders = CalendarOrder.findByUser(testUser.id).list();` ❌ Bypasses repository
- Line 280: `List<CalendarOrder> pendingOrders = CalendarOrder.findByStatusOrderByCreatedDesc("PENDING");` ❌ Bypasses repository

**In CalendarUserRepositoryTest.java**:
- Line 144: `CalendarUser found = CalendarUser.findById(user.id);` ❌ Should use repository.findById()
- Line 197: `assertNull(CalendarUser.findById(userId));` ❌ Should use repository
- Line 211: `boolean deleted = CalendarUser.deleteById(userId);` ❌ Should use repository.deleteById()

### Coverage Breakdown by Entity:

From JaCoCo HTML reports (`target/site/jacoco/villagecompute.calendar.data.models/index.html`):

| Entity | Current Coverage | Status |
|--------|------------------|--------|
| CalendarOrder | 0% | 94 instructions missed, all methods uncovered |
| UserCalendar | 6% | 93 of 99 instructions missed |
| CalendarUser | 11% | 55 of 62 instructions missed |
| PageView | 0% | 69 instructions missed |
| AnalyticsRollup | 0% | 93 instructions missed |
| DelayedJob | 0% | 61 instructions missed |
| CalendarTemplate | 21% | 47 of 60 instructions missed |
| DefaultPanacheEntityWithTimestamps | 23% | 10 of 13 instructions missed |
| DelayedJobQueue | 100% | ✅ Fully covered |

---

## Best Approach to Fix

You MUST address **two distinct problems**:

### Problem 1: Repository Tests Must Actually Test Repository Classes (0% → 70%)

**All repository tests** (in `src/test/java/villagecompute/calendar/data/repositories/`) currently bypass the repository layer. You need to:

1. **Replace ALL entity static method calls with repository instance method calls** in these files:
   - CalendarUserRepositoryTest.java
   - CalendarOrderRepositoryTest.java (if exists)
   - UserCalendarRepositoryTest.java (if exists)
   - CalendarTemplateRepositoryTest.java (if exists)

2. **Pattern to follow**:
   ```java
   // WRONG - bypasses repository
   CalendarUser found = CalendarUser.findById(userId);
   boolean deleted = CalendarUser.deleteById(userId);

   // CORRECT - uses repository instance
   Optional<CalendarUser> found = repository.findById(userId);
   boolean deleted = repository.deleteById(userId);
   ```

3. **Ensure you test ALL repository methods** including:
   - Base PanacheRepository methods: findById, listAll, persist, delete, deleteById, count, flush, findAll, streamAll
   - Custom query methods: findByOAuthSubject, findByEmail, findActiveUsersSince, findByProvider (for CalendarUserRepository)
   - Equivalent custom methods for other repositories

### Problem 2: Model Tests Must Exercise More Code Paths (10-23% → 70%)

The model tests exist but don't achieve sufficient instruction coverage. For EACH entity test file in `src/test/java/villagecompute/calendar/data/models/`, you need to:

1. **Replace entity static method calls with repository calls**:
   ```java
   // WRONG
   CalendarOrder found = CalendarOrder.findById(order.id);

   // CORRECT - inject repository and use it
   @Inject
   CalendarOrderRepository orderRepository;

   CalendarOrder found = orderRepository.findById(order.id).orElse(null);
   ```

2. **Add tests that exercise ALL fields** (many fields are not set/tested):
   ```java
   @Test
   @Transactional
   void testAllFieldsPersistence() {
       CalendarUser user = new CalendarUser();
       user.oauthProvider = "GOOGLE";
       user.oauthSubject = "subject-123";
       user.email = "test@example.com";
       user.displayName = "Test User";
       user.profileImageUrl = "https://example.com/photo.jpg"; // Often missed!
       user.lastLoginAt = Instant.now();
       user.isAdmin = false;

       calendarUserRepository.persist(user);
       entityManager.flush();
       entityManager.clear();

       CalendarUser found = calendarUserRepository.findById(user.id).orElseThrow();
       assertEquals("https://example.com/photo.jpg", found.profileImageUrl); // Verify ALL fields
       assertEquals(false, found.isAdmin);
       // ... assert all other fields
   }
   ```

3. **Add relationship loading tests** to exercise lazy collection loading:
   ```java
   @Test
   @Transactional
   void testLazyLoadUserRelationships() {
       CalendarUser user = createAndPersistUser();
       UserCalendar calendar = createAndPersistCalendar(user);
       entityManager.clear(); // Detach all entities

       CalendarUser found = calendarUserRepository.findById(user.id).orElseThrow();
       int calendarCount = found.calendars.size(); // Triggers lazy load
       assertEquals(1, calendarCount);
   }
   ```

4. **Add lifecycle callback tests** to ensure @PrePersist/@PreUpdate work:
   ```java
   @Test
   @Transactional
   void testTimestampsAutoPopulated() {
       CalendarUser user = new CalendarUser();
       // ... set required fields
       assertNull(user.created); // Not set yet
       assertNull(user.updated);

       calendarUserRepository.persist(user);
       entityManager.flush();

       assertNotNull(user.created); // @PrePersist populated it
       assertNotNull(user.updated);
       assertEquals(0L, user.version);
   }
   ```

5. **For entities with JSONB fields** (UserCalendar, CalendarTemplate), add comprehensive JSONB tests:
   ```java
   @Test
   @Transactional
   void testJsonbComplexConfiguration() {
       UserCalendar calendar = new UserCalendar();
       // ... set required fields

       ObjectNode config = objectMapper.createObjectNode();
       config.put("showMoonPhases", true);
       config.put("showHebrewDates", false);
       ObjectNode holidayConfig = objectMapper.createObjectNode();
       holidayConfig.put("includeUSHolidays", true);
       holidayConfig.put("includeJewishHolidays", false);
       config.set("holidays", holidayConfig);
       calendar.configuration = config;

       calendarRepository.persist(calendar);
       entityManager.flush();
       entityManager.clear();

       UserCalendar found = calendarRepository.findById(calendar.id).orElseThrow();
       assertTrue(found.configuration.get("showMoonPhases").asBoolean());
       assertNotNull(found.configuration.get("holidays"));
       assertTrue(found.configuration.get("holidays").get("includeUSHolidays").asBoolean());
   }
   ```

---

## Specific Files That MUST Be Modified

Based on git diff and coverage reports, these files need changes:

### Must Fix (Currently Have Tests):
1. `src/test/java/villagecompute/calendar/data/models/CalendarOrderTest.java` - Replace 15+ entity static calls
2. `src/test/java/villagecompute/calendar/data/models/CalendarUserTest.java` - Replace entity static calls, add field coverage tests
3. `src/test/java/villagecompute/calendar/data/models/UserCalendarTest.java` - Replace entity static calls, add JSONB tests
4. `src/test/java/villagecompute/calendar/data/models/PageViewTest.java` - Replace entity static calls
5. `src/test/java/villagecompute/calendar/data/repositories/CalendarUserRepositoryTest.java` - Fix lines 144, 197, 211, 231

### Must Create (No Tests Found):
6. `src/test/java/villagecompute/calendar/data/repositories/CalendarOrderRepositoryTest.java` - Test all CalendarOrderRepository methods
7. `src/test/java/villagecompute/calendar/data/repositories/UserCalendarRepositoryTest.java` - Test all UserCalendarRepository methods
8. `src/test/java/villagecompute/calendar/data/repositories/CalendarTemplateRepositoryTest.java` - Test all CalendarTemplateRepository methods
9. `src/test/java/villagecompute/calendar/data/models/AnalyticsRollupTest.java` - Test AnalyticsRollup entity (0% coverage)
10. `src/test/java/villagecompute/calendar/data/models/DelayedJobTest.java` - Test DelayedJob entity (0% coverage)
11. `src/test/java/villagecompute/calendar/data/models/CalendarTemplateTest.java` - Enhance to 70% coverage (currently 21%)

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
- [ ] No linting errors
- [ ] JaCoCo HTML report shows green coverage for both target packages
