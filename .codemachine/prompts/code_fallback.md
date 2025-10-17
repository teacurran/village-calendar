# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create JUnit 5 unit tests for all JPA entity models and Panache repositories. Test entity validation constraints (@NotNull, @Email, etc.), relationship mappings (cascade, fetch strategies), JSONB serialization/deserialization. Test repository methods: findById, listAll, persist, delete, custom queries (e.g., findByEmail, findByUserId). Use Quarkus test framework with @QuarkusTest annotation and in-memory H2 database for tests. Achieve >70% code coverage for model and repository packages. Configure JaCoCo Maven plugin for coverage reporting.

---

## Issues Detected

The test run revealed 89 failures out of 220 tests (25 failures + 64 errors). The issues fall into the following categories:

### Critical Issue #1: AnalyticsRollup Entity - Reserved Keyword Column Name

*   **Error:** H2 database schema creation fails for `analytics_rollups` table because `value` is a reserved keyword in H2
*   **SQL Error:** `Syntax error in SQL statement...expected "identifier"` when creating the table with column named `value`
*   **Impact:** The entire `analytics_rollups` table cannot be created, causing cascade failures for all related tests and indexes
*   **Root Cause:** The entity field `public BigDecimal value` in `AnalyticsRollup.java` line 63 maps to a column named `value` by default, which is a reserved keyword in H2

### Critical Issue #2: Test Data Isolation Failures - Unique Constraint Violations

*   **Error:** Multiple test methods in `CalendarUserTest` fail with unique constraint violation: `uk_calendar_users_oauth_INDEX_2 ON public.calendar_users(oauth_provider NULLS LAST, oauth_subject NULLS LAST)`
*   **Failing Tests:**
    - `CalendarUserTest.testFindActiveUsersSince` (line 220)
    - `CalendarUserTest.testFindByEmail` (line 178)
    - `CalendarUserTest.testFindByEmail_NotFound` (line 193)
    - `CalendarUserTest.testFindByOAuthSubject` (line 148)
    - `CalendarUserTest.testFindByOAuthSubject_NotFound` (line 164)
    - `CalendarUserTest.testHasAdminUsers_False` (line 269)
    - `CalendarUserTest.testHasAdminUsers_True` (line 254)
*   **Root Cause:** The `createValidUser()` helper method always creates users with the same `oauthSubject = "test-subject"` and `oauthProvider = "GOOGLE"`. This violates the unique constraint when multiple users are created within the same test method or when `TestDataCleaner.deleteAll()` doesn't properly clean up between test methods.

### Critical Issue #3: Referential Integrity Constraint Violations - Foreign Key Issues

*   **Error Pattern:** Multiple tests fail with `Referential integrity constraint violation` when trying to insert entities with foreign key references to non-existent parent entities
*   **Affected Tests:**
    - **CalendarOrder tests:** All tests fail because they reference non-existent `calendar_id` (FK to `user_calendars`) or `user_id` (FK to `calendar_users`)
    - **UserCalendar tests:** Tests fail because they reference non-existent `template_id` (FK to `calendar_templates`)
    - **PageView tests:** Tests fail because they reference non-existent `user_id` (FK to `calendar_users`)
    - **Repository tests:** `CalendarOrderRepositoryTest`, `UserCalendarRepositoryTest` all fail with same FK constraint violations
*   **Root Cause:** Test methods create child entities (CalendarOrder, UserCalendar, PageView) with foreign key references to parent entities, but the parent entities are NOT being persisted in the same transaction, or the foreign keys are set to random UUIDs that don't exist in the database.

### Critical Issue #4: Incorrect Property Paths in Entity Query Methods

*   **Error:** `CalendarUserTest.testFindAdminUsers` fails with: `Could not interpret path expression 'createdAt'`
*   **Root Cause:** The `CalendarUser.findAdminUsers()` method (or similar query methods) is using field name `createdAt` but the actual field name in the base entity `DefaultPanacheEntityWithTimestamps` is `created` (not `createdAt`).

### Critical Issue #5: Transient Instance References in Cascade Tests

*   **Error:** `TransientPropertyValue` exception: "Persistent instance of X references an unsaved transient instance of Y (persist the transient instance before flushing)"
*   **Failing Tests:**
    - `CalendarUserTest.testRelationships_CascadeRemove` (line 339)
    - `UserCalendarTest.testRelationships_CascadeRemoveOrders` (line 392)
    - `UserCalendarTest.testRelationships_ManyToOneTemplate`
    - `UserCalendarTest.testRelationships_ManyToOneUser`
*   **Root Cause:** Relationship tests are creating child entities and setting their parent references, but NOT persisting the parent entities first. The cascade tests are incorrectly assuming that setting a relationship will automatically persist the parent entity.

### Critical Issue #6: ArcUndeclaredThrowable Errors

*   **Error:** Multiple tests throw `ArcUndeclaredThrowable: Error invoking subclass method`
*   **Affected Tests:** Many tests in `CalendarOrderTest`, `UserCalendarTest`, `PageViewTest`
*   **Root Cause:** These are secondary errors caused by the test setup failures from Issues #2 and #3. When the `@BeforeEach` setup method fails or leaves the database in an inconsistent state, subsequent test methods throw these generic Arc (Quarkus CDI) errors.

---

## Best Approach to Fix

You MUST fix all the issues identified above. Here is the recommended approach:

### Fix #1: Rename AnalyticsRollup.value Column

**File:** `src/main/java/villagecompute/calendar/data/models/AnalyticsRollup.java`

Change line 63 to explicitly map the `value` field to a non-reserved column name:

```java
@NotNull
@DecimalMin("0.00")
@Column(name = "metric_value", nullable = false, precision = 15, scale = 2)  // Changed from default "value" to "metric_value"
public BigDecimal value;
```

This prevents H2 from treating `value` as a reserved keyword. The Java field name can stay as `value`, but the database column will be `metric_value`.

### Fix #2: Fix CalendarUserTest Unique Constraint Violations

**File:** `src/test/java/villagecompute/calendar/data/models/CalendarUserTest.java`

The `createValidUser()` helper method must generate UNIQUE `oauthSubject` values for each user. Change the method at line 342 to:

```java
private CalendarUser createValidUser(String email) {
    CalendarUser user = new CalendarUser();
    user.oauthProvider = "GOOGLE";
    user.oauthSubject = "test-subject-" + email;  // Make unique based on email
    user.email = email;
    user.displayName = "Test User";
    user.isAdmin = false;
    return user;
}
```

Alternatively, use a UUID or counter to ensure uniqueness:

```java
private CalendarUser createValidUser(String email) {
    CalendarUser user = new CalendarUser();
    user.oauthProvider = "GOOGLE";
    user.oauthSubject = "test-subject-" + java.util.UUID.randomUUID().toString();
    user.email = email;
    user.displayName = "Test User";
    user.isAdmin = false;
    return user;
}
```

### Fix #3: Fix Foreign Key Constraint Violations in All Entity Tests

You MUST ensure that ALL parent entities are persisted BEFORE creating child entities that reference them. The pattern to follow:

**For CalendarOrderTest:**

Before creating any `CalendarOrder`, you must:
1. Create and persist a `CalendarUser` (for the `user_id` FK)
2. Create and persist a `CalendarTemplate` (for the `template_id` FK in UserCalendar)
3. Create and persist a `UserCalendar` (for the `calendar_id` FK)
4. THEN create the `CalendarOrder` with references to the persisted entities

Example fix for `CalendarOrderTest`:

```java
@Test
@Transactional
void testValidEntity_Success() {
    // Step 1: Create and persist user
    CalendarUser user = new CalendarUser();
    user.oauthProvider = "GOOGLE";
    user.oauthSubject = "test-subject-" + UUID.randomUUID();
    user.email = "user@test.com";
    user.displayName = "Test User";
    user.isAdmin = false;
    user.persist();

    // Step 2: Create and persist template
    CalendarTemplate template = new CalendarTemplate();
    template.name = "Test Template";
    template.isActive = true;
    template.isFeatured = false;
    template.displayOrder = 1;
    template.configuration = objectMapper.createObjectNode();  // Inject ObjectMapper
    template.persist();

    // Step 3: Create and persist calendar
    UserCalendar calendar = new UserCalendar();
    calendar.user = user;
    calendar.template = template;
    calendar.name = "Test Calendar";
    calendar.year = 2025;
    calendar.isPublic = false;
    calendar.persist();

    // Step 4: NOW create the order
    CalendarOrder order = createValidOrder();
    order.user = user;
    order.calendar = calendar;
    order.persist();

    // Assertions...
}
```

**For UserCalendarTest:**

Before creating any `UserCalendar`, you must:
1. Create and persist a `CalendarTemplate` (for the `template_id` FK, if template is non-null)
2. Create and persist a `CalendarUser` (for the `user_id` FK, if user is non-null)
3. THEN create the `UserCalendar`

**For PageViewTest:**

Before creating any `PageView` with a `user_id`, you must:
1. Create and persist a `CalendarUser`
2. THEN create the `PageView` with reference to the persisted user

Apply this pattern to ALL tests that create entities with foreign key relationships.

### Fix #4: Fix Incorrect Field Names in Query Methods

**File:** `src/main/java/villagecompute/calendar/data/models/CalendarUser.java`

Search for any query methods that reference `createdAt` and change them to `created` (the actual field name in `DefaultPanacheEntityWithTimestamps`).

For example, if `findAdminUsers()` has:

```java
return find("isAdmin = true ORDER BY createdAt DESC");
```

Change it to:

```java
return find("isAdmin = true ORDER BY created DESC");
```

Do the same for any other entity classes that reference `createdAt`, `updatedAt` - they should be `created`, `updated`.

### Fix #5: Fix Cascade Relationship Tests

**Files:** `CalendarUserTest.java`, `UserCalendarTest.java`, etc.

For cascade tests, you MUST persist the parent entity BEFORE creating the child entity. Example fix for `CalendarUserTest.testRelationships_CascadeRemove()`:

```java
@Test
@Transactional
void testRelationships_CascadeRemove() {
    // Given
    CalendarUser user = createValidUser("user@test.com");
    user.persist();  // PERSIST USER FIRST

    // Create template (required FK for calendar)
    CalendarTemplate template = new CalendarTemplate();
    template.name = "Test Template";
    template.isActive = true;
    template.isFeatured = false;
    template.displayOrder = 1;
    template.configuration = objectMapper.createObjectNode();
    template.persist();  // PERSIST TEMPLATE

    UserCalendar calendar = new UserCalendar();
    calendar.user = user;  // Now this is a MANAGED entity
    calendar.template = template;  // Now this is a MANAGED entity
    calendar.name = "Test Calendar";
    calendar.year = 2025;
    calendar.isPublic = false;
    calendar.persist();

    // When
    user.delete();

    // Then
    assertEquals(0, UserCalendar.count());
}
```

Apply this pattern to ALL cascade tests.

### Fix #6: Review TestDataCleaner Order of Deletion

**File:** `src/test/java/villagecompute/calendar/data/repositories/TestDataCleaner.java`

Ensure that `TestDataCleaner.deleteAll()` deletes entities in the CORRECT order to avoid FK constraint violations:
1. Delete child entities FIRST (CalendarOrder, PageView, UserCalendar)
2. Delete parent entities LAST (CalendarUser, CalendarTemplate, DelayedJobQueue)

The current `TestDataCleaner` may already have this, but verify that `analytics_rollups` table deletion is included (once the column name issue is fixed).

### Additional Fixes Required:

1. **Check all helper methods:** Any `createValidX()` helper method that returns an entity with foreign keys MUST either:
   - Accept the parent entity as a parameter, OR
   - Create and persist all required parent entities inside the helper

2. **Inject ObjectMapper where needed:** Tests that work with JSONB fields (configuration, shippingAddress) must inject `ObjectMapper` to create `ObjectNode` instances:

```java
@Inject
ObjectMapper objectMapper;
```

3. **Fix any remaining query method bugs:** Search for incorrect field names in ALL entity classes (not just CalendarUser).

---

## Testing After Fixes

After applying all fixes, run:

```bash
./mvnw test
```

All tests MUST pass (0 failures, 0 errors). Then check coverage:

```bash
open target/site/jacoco/index.html
```

Verify that the `villagecompute.calendar.data.models` and `villagecompute.calendar.data.repositories` packages have >70% line coverage.

---

## Summary of Required Changes

1. **AnalyticsRollup.java:** Change `@Column` annotation for `value` field to use `name = "metric_value"`
2. **CalendarUser.java:** Fix any query methods using `createdAt` to use `created`
3. **CalendarUserTest.java:** Make `createValidUser()` generate unique `oauthSubject` values
4. **CalendarOrderTest.java:** Add proper parent entity setup (user, template, calendar) before creating orders
5. **UserCalendarTest.java:** Add proper parent entity setup (user, template) before creating calendars
6. **PageViewTest.java:** Add proper parent entity setup (user) before creating page views
7. **All cascade tests:** Persist parent entities BEFORE creating child entities
8. **All repository tests:** Follow the same FK constraint fix pattern as entity tests
9. **TestDataCleaner.java:** Verify deletion order and add AnalyticsRollup cleanup if missing

DO NOT proceed to mark the task as complete until ALL tests pass and coverage meets the 70% threshold.
