# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

**Task ID:** `I1.T13`

Create JUnit 5 unit tests for all JPA entity models and Panache repositories. Test entity validation constraints (@NotNull, @Email, etc.), relationship mappings (cascade, fetch strategies), JSONB serialization/deserialization. Test repository methods: findById, listAll, persist, delete, custom queries (e.g., findByEmail, findByUserId). Use Quarkus test framework with @QuarkusTest annotation and in-memory H2 database for tests. Achieve >70% code coverage for model and repository packages. Configure JaCoCo Maven plugin for coverage reporting.

**Acceptance Criteria:**
- `./mvnw test` runs all tests without failures
- Entity validation tests verify @NotNull, @Email, @Size constraints
- Relationship tests confirm cascade and fetch behavior
- Repository tests verify CRUD operations and custom queries
- JaCoCo report shows line/branch coverage percentages
- No test depends on external services (all use in-memory database)

---

## Issues Detected

*   **Test Failure:** The test `AnalyticsRollupTest.testFindByTimeRange` is failing with "expected: <1> but was: <0>". The query `AnalyticsRollup.findByTimeRange(since, until)` returns 0 results when it should return 1.

*   **Root Cause:** The `findByTimeRange` method in `src/main/java/villagecompute/calendar/data/models/AnalyticsRollup.java` has a Hibernate/H2/Panache bug. When a HQL query contains ONLY timestamp field comparisons in the WHERE clause (without any non-timestamp field conditions), the query returns 0 results even when matching data exists in the database.

*   **Evidence:** Through extensive testing, I verified that:
    - `periodStart >= ?1` alone works correctly
    - `periodEnd <= ?1` alone works correctly
    - `periodStart >= ?1 AND periodEnd <= ?2` returns 0 results (BROKEN)
    - `metricName = ?1 AND periodStart >= ?2 AND periodEnd <= ?3` works correctly (has non-timestamp condition first)
    - The same test data with a very wide time range (2020-2030) still returns 0 results when using only timestamp comparisons

---

## Best Approach to Fix

You MUST fix the `findByTimeRange` method in `src/main/java/villagecompute/calendar/data/models/AnalyticsRollup.java` by adding a dummy non-timestamp condition to the WHERE clause that doesn't affect the query results but makes Hibernate/H2 process the timestamp comparisons correctly.

**Recommended fix:**

```java
public static PanacheQuery<AnalyticsRollup> findByTimeRange(Instant since, Instant until) {
    // Workaround: H2/Hibernate requires at least one non-timestamp condition in WHERE clause
    // for timestamp comparisons to work correctly. Use "value >= 0" which is always true.
    return find("value >= 0 AND periodStart >= ?1 AND periodEnd <= ?2 ORDER BY periodStart DESC", since, until);
}
```

**Why this works:**
- The `value` field is a BigDecimal with `@DecimalMin("0.00")` constraint, so `value >= 0` is always true for all valid records
- This adds a non-timestamp parameter condition before the timestamp comparisons
- The condition doesn't filter out any records, so the query semantics remain correct
- This matches the pattern seen in the working `findByMetricAndTimeRange` method which has `metricName = ?1` before the timestamp checks

**Alternative fix (if the above doesn't work):**

Remove the `periodEnd <= ?2` condition entirely and only filter by `periodStart >= ?1`. This is semantically reasonable since if a period started after the query end time, it's definitely not in range:

```java
public static PanacheQuery<AnalyticsRollup> findByTimeRange(Instant since, Instant until) {
    return find("periodStart >= ?1 AND periodStart < ?2 ORDER BY periodStart DESC", since, until);
}
```

You must also update the test to remove the debug code (lines 270-285 in `AnalyticsRollupTest.java`) and ensure the test assertion logic matches the new query semantics if you use the alternative fix.

After making the fix, run `./mvnw test` to verify all tests pass.
