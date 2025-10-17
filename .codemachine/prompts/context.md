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
    "src/test/java/villagecompute/calendar/data/models/UserTest.java",
    "src/test/java/villagecompute/calendar/data/models/CalendarTest.java",
    "src/test/java/villagecompute/calendar/data/models/OrderTest.java",
    "src/test/java/villagecompute/calendar/data/repositories/UserRepositoryTest.java",
    "src/test/java/villagecompute/calendar/data/repositories/CalendarRepositoryTest.java",
    "pom.xml"
  ],
  "input_files": [
    "src/main/java/villagecompute/calendar/data/models/*.java",
    "src/main/java/villagecompute/calendar/data/repositories/*.java"
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

The following are the relevant sections from the architecture and plan documents.

### Context: Testing Strategy

The project follows a comprehensive testing strategy with the following requirements:

**Testing Levels:**
- Unit Tests: Test individual components in isolation using JUnit 5
- Integration Tests: Test component interactions using @QuarkusTest
- Coverage Target: >70% for service and model layers
- Coverage Tool: JaCoCo Maven plugin configured in pom.xml (lines 274-313)

**Key Testing Requirements:**
- All tests must use @QuarkusTest annotation for Quarkus test framework
- Tests should use in-memory H2 database (configured as test scope dependency)
- Entity validation tests must verify all Bean Validation constraints
- Relationship tests must verify cascade behaviors and fetch strategies
- JSONB serialization/deserialization must be tested for entities with JSON columns
- Repository tests must cover: findById, listAll, persist, delete, and all custom query methods

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### **CRITICAL FINDING: Tests Already Exist**

All required entity model tests and repository tests ALREADY EXIST in the codebase! The task asks you to "create" tests, but the implementation is already complete. Here's what exists:

**Entity Model Tests (All Complete):**
1. ✅ CalendarUserTest.java (22 test methods)
2. ✅ UserCalendarTest.java (19 test methods)
3. ✅ CalendarTemplateTest.java
4. ✅ CalendarOrderTest.java
5. ✅ PageViewTest.java
6. ✅ AnalyticsRollupTest.java
7. ✅ DelayedJobTest.java

**Repository Tests (All Complete):**
1. ✅ CalendarUserRepositoryTest.java
2. ✅ UserCalendarRepositoryTest.java
3. ✅ CalendarTemplateRepositoryTest.java
4. ✅ CalendarOrderRepositoryTest.java

**Integration Tests (Out of Scope but Present):**
- AuthenticationIntegrationTest.java
- CalendarServiceIntegrationTest.java
- CalendarGraphQLTest.java

### Relevant Existing Code

*   **File:** `src/test/java/villagecompute/calendar/data/models/CalendarUserTest.java`
    *   **Summary:** Comprehensive test suite for CalendarUser entity demonstrating the established testing patterns for this project.
    *   **Recommendation:** This file serves as the PERFECT template for understanding test patterns. Key patterns:
        - Uses `@QuarkusTest` annotation at class level
        - Injects `Validator validator` for constraint validation testing
        - Injects `TestDataCleaner testDataCleaner` and calls it in `@BeforeEach` method
        - Tests valid entity persistence with verification of id, created, updated, version fields
        - Tests each validation constraint individually with dedicated test methods
        - Tests custom query methods (e.g., findByOAuthSubject, findByEmail)
        - Tests relationship mappings (cascade persist, cascade delete)
        - Uses descriptive test method names: `testInvalidEntity_NullField`, `testFindByField_Success`
    *   **Coverage:** Achieves comprehensive coverage with 22 test methods covering validation, queries, relationships, and business logic.

*   **File:** `src/test/java/villagecompute/calendar/data/models/UserCalendarTest.java`
    *   **Summary:** Comprehensive test suite showing advanced patterns including JSONB serialization testing.
    *   **Key Patterns:**
        - Injects `ObjectMapper objectMapper` for creating JSON test data
        - Injects `jakarta.persistence.EntityManager entityManager` and calls `flush()` in setup
        - Tests JSONB serialization/deserialization with both populated and null values
        - Tests custom query methods with complex conditions
        - Tests utility methods like `copyForSession()`
        - Creates multiple helper methods for different test data scenarios

*   **File:** `src/test/java/villagecompute/calendar/data/repositories/CalendarUserRepositoryTest.java`
    *   **Summary:** Repository test suite focusing on custom query methods.
    *   **Key Patterns:**
        - Injects the repository interface being tested
        - Tests all custom query methods
        - Tests both success cases (data found) and failure cases (data not found)
        - Uses descriptive test names: `testMethodName_Scenario`

*   **File:** `pom.xml` (lines 274-313)
    *   **Summary:** JaCoCo Maven plugin is ALREADY CONFIGURED.
    *   **Configuration Details:**
        - JaCoCo version: 0.8.11
        - Execution phases: prepare-agent, report, check
        - Coverage requirement: 70% line coverage at PACKAGE level
        - Report location: target/site/jacoco/index.html
    *   **Recommendation:** No changes needed to pom.xml. JaCoCo is fully configured.

### Implementation Tips & Notes

*   **YOUR PRIMARY TASK:** Since tests already exist, your task is to:
    1. Run the test suite: `./mvnw test`
    2. Generate JaCoCo coverage report: The report is auto-generated during test execution
    3. Verify coverage meets >70% threshold
    4. If any tests fail, fix them
    5. If coverage < 70%, add tests to reach threshold
    6. Document findings and mark task complete

*   **Note:** The task specification lists incorrect file names:
    - Specifies "UserTest.java" but actual entity is "CalendarUser" (test: CalendarUserTest.java)
    - Specifies "CalendarTest.java" but actual entity is "UserCalendar" (test: UserCalendarTest.java)
    - Specifies "OrderTest.java" but actual entity is "CalendarOrder" (test: CalendarOrderTest.java)

    This is expected since the task specification was written before implementation. The actual entity names follow a more descriptive naming convention.

*   **Test Execution:** The project uses Quarkus with H2 in-memory database for tests (dependency `quarkus-jdbc-h2` with scope `test` in pom.xml). Tests run in isolated transactions with TestDataCleaner for cleanup.

*   **Coverage Analysis:** When you run `./mvnw test`, JaCoCo automatically generates a report at `target/site/jacoco/index.html`. Open this in a browser to see detailed coverage by package, class, and method.

*   **Expected Test Count:** Approximately 100+ test methods across:
    - 7 entity model test classes
    - 4 repository test classes
    - Multiple integration test classes (out of scope for this task)

*   **Database Configuration:** H2 is automatically configured for tests. Quarkus handles all database initialization.

### Task Completion Strategy

**Step 1: Run Tests and Generate Coverage**
```bash
./mvnw clean test
```

**Step 2: Analyze Coverage Report**
Open `target/site/jacoco/index.html` in a browser and verify:
- Coverage for `villagecompute.calendar.data.models` package (target: >70%)
- Coverage for `villagecompute.calendar.data.repositories` package (target: >70%)

**Step 3: Address Any Issues**
- If tests fail: Fix the failing tests
- If coverage < 70%: Add tests for uncovered code paths
- Focus on edge cases and error paths

**Step 4: Mark Complete**
- Verify all acceptance criteria met
- Update task status to done=true

---

## 4. Acceptance Criteria Checklist

- [ ] `./mvnw test` runs all tests without failures
- [ ] Entity validation tests verify @NotNull, @Email, @Size constraints
- [ ] Relationship tests confirm cascade and fetch behavior
- [ ] Repository tests verify CRUD operations (findById, listAll, persist, delete)
- [ ] Repository tests verify all custom query methods
- [ ] JaCoCo report generated at target/site/jacoco/index.html
- [ ] Coverage >70% for villagecompute.calendar.data.models package
- [ ] Coverage >70% for villagecompute.calendar.data.repositories package
- [ ] No test depends on external services (all use in-memory H2 database)

---

## End of Task Briefing Package

**Summary:** All required tests already exist. Your task is to verify they pass and meet coverage requirements. If issues are found, fix them. Otherwise, document that the task is complete and mark it done.
