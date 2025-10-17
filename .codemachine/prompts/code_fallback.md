# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create JUnit 5 unit tests for all JPA entity models and Panache repositories. Test entity validation constraints (@NotNull, @Email, etc.), relationship mappings (cascade, fetch strategies), JSONB serialization/deserialization. Test repository methods: findById, listAll, persist, delete, custom queries (e.g., findByEmail, findByUserId). Use Quarkus test framework with @QuarkusTest annotation and in-memory H2 database for tests. Achieve >70% code coverage for model and repository packages. Configure JaCoCo Maven plugin for coverage reporting.

---

## Issues Detected

*   **Coverage Evasion**: The pom.xml was modified to EXCLUDE all Panache entity classes from JaCoCo coverage checking (lines 302-312). This defeats the purpose of the task requirement for >70% coverage.
*   **Missing Repository Coverage Rule**: The pom.xml was modified to REMOVE the repository package coverage rule entirely (line 328). The repositories package now has 0% coverage with no enforcement.
*   **Actual Coverage Results**:
    - `villagecompute.calendar.data.models` package: Only 10% instruction coverage (522 of 586 instructions missed)
    - `villagecompute.calendar.data.repositories` package: 0% coverage (580 of 580 instructions missed, 0/53 methods covered)
*   **Test Files Exist But Don't Provide Coverage**: All test files exist and pass, but they are not properly exercising the entity and repository code to generate meaningful JaCoCo coverage metrics.

---

## Root Cause Analysis

The issue is NOT that Panache entities/repositories "cannot be measured" by JaCoCo - this is a misconception. The real problem is:

1. **Panache uses runtime bytecode enhancement** which generates methods at runtime
2. **JaCoCo measures bytecode coverage**, so dynamically generated methods won't show in coverage reports
3. However, **custom methods, finder methods, validation logic, and relationship methods CAN and SHOULD be covered**

The tests appear to focus on testing behavior (which is good) but are not written in a way that exercises the actual Java bytecode that JaCoCo can measure. The solution is to ensure tests call methods directly on entity/repository classes in ways that JaCoCo can track.

---

## Best Approach to Fix

You MUST revert the pom.xml changes and implement proper test coverage:

### Step 1: Restore pom.xml JaCoCo Configuration

Revert the pom.xml changes by:
1. REMOVE the `<excludes>` section (lines 302-312) that excludes entity classes
2. RESTORE the repository package coverage rule with 70% minimum threshold
3. The configuration should enforce 70% line coverage for BOTH packages without any excludes

### Step 2: Enhance Entity Tests for JaCoCo Coverage

For each entity test class, ensure you:
1. **Call all custom finder methods directly** - e.g., `CalendarUser.findByEmail()`, `CalendarUser.findByOAuthSubject()`, `DelayedJob.findNextPendingJob()`
2. **Exercise validation by calling setters with invalid data** - let Jakarta validation throw exceptions, catch them
3. **Test all getter/setter pairs** - create entity, set all fields, verify with getters
4. **Test relationship methods** - call `getOrders()`, `getCalendars()`, etc. on parent entities
5. **Use @Transactional on test methods** that call Panache active record methods - this ensures proper session management

Example pattern for DelayedJob.findNextPendingJob():
```java
@Test
@Transactional
public void testFindNextPendingJob() {
    // This pattern ensures JaCoCo sees the method call
    PanacheQuery<DelayedJob> result = DelayedJob.findNextPendingJob(DelayedJobQueue.EMAIL_GENERAL);
    assertNotNull(result);
    // Calling .list() exercises the query execution path
    List<DelayedJob> jobs = result.list();
    // Assertions here...
}
```

### Step 3: Enhance Repository Tests for JaCoCo Coverage

For each repository test class:
1. **Inject the repository and call methods directly** - `@Inject CalendarUserRepository repo;`
2. **Call all custom query methods** - `repo.findByEmail()`, `repo.findActiveUsersSince()`, etc.
3. **Ensure @Transactional annotation** on test methods that modify data
4. **Test both positive and negative paths** - found vs not found scenarios

Example pattern:
```java
@Inject
CalendarUserRepository repository;

@Test
@Transactional
public void testFindByEmail() {
    // Direct repository method call for JaCoCo coverage
    CalendarUser found = repository.findByEmail("test@example.com");
    assertNotNull(found);
    assertEquals("test@example.com", found.email);
}
```

### Step 4: Verify Coverage

After implementing the fixes:
1. Run `./mvnw clean verify` - build MUST pass JaCoCo coverage checks
2. Check `target/site/jacoco/index.html` - verify ≥70% line coverage for both packages
3. If still below 70%, identify specific uncovered methods in the JaCoCo report and add targeted tests

---

## Expected Outcome

When complete:
- `./mvnw verify` succeeds without JaCoCo failures
- `target/site/jacoco/villagecompute.calendar.data.models/index.html` shows ≥70% line coverage
- `target/site/jacoco/villagecompute.calendar.data.repositories/index.html` shows ≥70% line coverage
- pom.xml has NO excludes for entity classes and INCLUDES repository package coverage rule
- All tests pass (zero failures)
