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
  "dependencies": [
    "I1.T8"
  ],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: testing-levels (from 03_Verification_and_Glossary.md)

```markdown
### 5.1. Testing Levels

The Village Calendar project employs a multi-layered testing strategy to ensure quality at every level of the system:

**1. Unit Testing (Iteration-Specific)**

*   **Scope**: Individual classes, methods, and functions in isolation
*   **Tools**: JUnit 5 (backend), Vitest (frontend)
*   **Coverage Target**: 70%+ for service layer, repository layer, utility classes
*   **Frequency**: Run on every commit (CI pipeline)
*   **Examples**:
    *   Entity validation tests (JPA constraint validation)
    *   Service method tests (business logic, edge cases)
    *   Repository query tests (custom queries, filters)
    *   GraphQL resolver tests (authorization, error handling)
    *   Utility class tests (astronomical calculations, PDF rendering helpers)
*   **Iteration Integration**: Unit tests written alongside implementation in each iteration (I1.T13, I2.T2, I2.T3, I3.T2, I4.T2)
```

### Context: code-quality-gates (from 03_Verification_and_Glossary.md)

```markdown
### 5.3. Code Quality Gates

**Mandatory Gates (CI Pipeline Fails If Not Met)**

1.  **Compilation Success**: Backend and frontend must compile without errors
2.  **Unit Test Pass**: All unit tests must pass (0% failure tolerance)
3.  **Code Coverage**: Minimum 70% line coverage for service layer, API layer, repository layer (JaCoCo enforcement)
4.  **Linting**: ESLint must pass with 0 errors (warnings allowed, but discouraged)
5.  **Security Scan**: No critical severity vulnerabilities in dependencies (Snyk/OWASP)
6.  **Integration Test Pass**: All integration tests must pass

**Advisory Gates (Warnings, Manual Review Required)**

1.  **Code Coverage <80%**: Warning if coverage below 80% (target is 70%, stretch goal 80%)
2.  **SonarQube Security Hotspots**: Security hotspots flagged for review (not blocking, but should be addressed)
3.  **Dependency Vulnerabilities (Medium Severity)**: Medium severity vulnerabilities logged, should be addressed in next sprint
4.  **Code Duplication >5%**: SonarQube detects code duplication exceeding 5% (refactoring recommended)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Current Test Status

**ENTITY MODEL TESTS - Status: ✅ ALL COMPLETE (7/7)**
- ✅ AnalyticsRollupTest.java
- ✅ CalendarOrderTest.java
- ✅ CalendarTemplateTest.java
- ✅ CalendarUserTest.java (★ EXEMPLARY REFERENCE)
- ✅ DelayedJobTest.java
- ✅ PageViewTest.java
- ✅ UserCalendarTest.java

**REPOSITORY TESTS - Status: ✅ ALL COMPLETE (7/7)**
- ✅ AnalyticsRollupRepositoryTest.java
- ✅ CalendarOrderRepositoryTest.java
- ✅ CalendarTemplateRepositoryTest.java
- ✅ CalendarUserRepositoryTest.java (★ EXEMPLARY REFERENCE)
- ✅ DelayedJobRepositoryTest.java
- ✅ PageViewRepositoryTest.java
- ✅ UserCalendarRepositoryTest.java

**TASK COMPLETION STATUS:** All test files exist. Your task is to ensure they achieve >70% code coverage as measured by JaCoCo.

### Relevant Existing Code

*   **File:** `src/test/java/villagecompute/calendar/data/models/CalendarUserTest.java`
    *   **Summary:** This file contains 34 comprehensive test methods covering validation constraints, relationship cascade operations, custom finder methods, CRUD operations, and field persistence. It serves as the gold standard pattern for entity testing.
    *   **Recommendation:** You SHOULD use this as your reference when analyzing test coverage gaps. Key testing patterns demonstrated:
      - Validation constraint testing (testInvalidEntity_NullOAuthProvider, testInvalidEntity_InvalidEmailFormat, etc.)
      - Relationship testing with flush/clear pattern (testRelationships_CascadePersist, testRelationships_CascadeRemove)
      - Custom finder methods (testFindByOAuthSubject, testFindByEmail, testFindActiveUsersSince)
      - Optimistic locking verification (testUpdate_ModifiesUpdatedTimestamp checks version increment)
      - Comprehensive field coverage (testAllFields_SetAndRetrieve tests ALL entity fields)
    *   **Key Pattern:** Always call `entityManager.flush()` and `entityManager.clear()` when testing relationships to ensure proper database persistence and reload.

*   **File:** `src/test/java/villagecompute/calendar/data/repositories/CalendarUserRepositoryTest.java`
    *   **Summary:** Contains 18 test methods verifying repository query methods, CRUD operations, unique constraints, and edge cases. Demonstrates proper repository testing patterns.
    *   **Recommendation:** You SHOULD use this as reference for repository test patterns. Critical patterns:
      - Test both positive cases (data found) and negative cases (empty results) - see testFindByOAuthSubject vs testFindByOAuthSubject_NotFound
      - Verify unique constraint violations throw exceptions (testUniqueConstraint)
      - Test custom query method ordering and filtering (testFindActiveUsersSince)
      - Use entityManager.flush() after persist to ensure DB synchronization

*   **File:** `pom.xml` (lines 275-334)
    *   **Summary:** JaCoCo Maven plugin is already configured with version 0.8.11. Configuration includes: prepare-agent execution, report generation on test phase, and jacoco-check with 70% minimum coverage rules for data.models and data.repositories packages.
    *   **Recommendation:** You do NOT need to modify pom.xml configuration. JaCoCo is fully configured and will enforce 70% coverage threshold automatically during build.
    *   **Important:** The coverage check will FAIL the build if either package falls below 70% line coverage.

*   **File:** `src/test/java/villagecompute/calendar/data/repositories/TestDataCleaner.java`
    *   **Summary:** Critical utility class that deletes all test data in correct FK order (Orders → Calendars → PageView → AnalyticsRollup → DelayedJob → Users → Templates). Prevents FK constraint violations during test cleanup.
    *   **Recommendation:** You MUST verify this is called in EVERY test class's @BeforeEach setUp() method. Test isolation depends on proper cleanup.

### Implementation Tips & Notes

*   **Tip:** All tests use Quarkus test framework (@QuarkusTest annotation) with automatic dependency injection. The test database is H2 in-memory, automatically configured - no Docker or external PostgreSQL needed.

*   **Note:** JaCoCo is already fully configured in pom.xml (version 0.8.11) with 70% minimum coverage enforcement for data.models and data.repositories packages. The build will automatically fail if coverage falls below 70%.

*   **Note:** All entity models extend `DefaultPanacheEntityWithTimestamps` which provides: id (UUID), created (Instant), updated (Instant), version (Long for optimistic locking). Tests must verify these inherited fields work correctly.

*   **Important:** The project uses Jakarta validation (not javax). Use: `jakarta.validation.constraints.*` for all validation annotations.

*   **Critical Pattern - Relationship Testing:** Use `entityManager.flush()` and `entityManager.clear()` pattern when testing cascade operations to force database persistence and reload. See CalendarUserTest.java for examples.

*   **Critical Pattern - Test Isolation:** EVERY test class MUST call `testDataCleaner.deleteAll()` in its @BeforeEach setUp() method. This clears all tables in correct FK order to prevent test data contamination.

*   **JSONB Field Testing:** For entities with JSONB columns, inject ObjectMapper and use it to create JsonNode test data: `ObjectNode config = objectMapper.createObjectNode();`

### Strategic Approach to Achieve 70% Coverage

**Step 1: Run Coverage Analysis**
1. Execute `./mvnw clean test` to run all existing tests
2. Execute `./mvnw jacoco:report` to generate coverage report
3. Open `target/site/jacoco/index.html` in browser
4. Navigate to `villagecompute.calendar.data.models` package
5. Navigate to `villagecompute.calendar.data.repositories` package
6. Identify specific classes and methods with low/zero coverage (red/yellow highlighting)

**Step 2: Analyze Coverage Gaps**
- For each entity class showing <70% coverage, identify uncovered lines:
  - Validation getters/setters not tested?
  - Custom finder methods not exercised?
  - Relationship cascade paths not covered?
  - Edge cases in business logic not tested?
- For each repository class showing <70% coverage, identify uncovered methods:
  - Custom query methods missing tests?
  - Error handling paths not tested?
  - Edge cases (empty results, null parameters) not covered?

**Step 3: Add Missing Tests**
- DO NOT create new test files - all test files already exist
- ADD test methods to existing test files to cover gaps
- Focus on high-impact areas: custom methods, validation constraints, relationships
- Follow patterns from CalendarUserTest.java and CalendarUserRepositoryTest.java

**Step 4: Verify Success**
1. Run `./mvnw clean verify` - build must pass without JaCoCo failures
2. Verify JaCoCo report shows ≥70% line coverage for both packages
3. All tests must pass (zero failures)

### Common Coverage Gap Patterns

Based on typical Panache/JPA projects, look for these common gaps:

**Entity Coverage Gaps:**
- ✗ Validation constraints with boundary values not tested (e.g., max length strings)
- ✗ Optional fields (nullable) not tested with null values
- ✗ Enum field validation not tested
- ✗ Custom finder methods returning PanacheQuery not fully exercised (call .list(), .stream(), .count())
- ✗ Optimistic locking collision scenarios not tested (concurrent updates)
- ✗ Cascade operations (persist, delete) on relationships not tested
- ✗ Bidirectional relationship integrity not tested

**Repository Coverage Gaps:**
- ✗ Custom query methods with complex HQL not tested
- ✗ Query methods returning empty results not tested (negative test cases)
- ✗ Query methods with ORDER BY clauses - ordering not verified
- ✗ Time range query edge cases not tested (boundary conditions)
- ✗ Aggregate methods (count, sum) not tested
- ✗ Methods with multiple parameters - all parameter combinations not tested

### Test Execution & Verification

**Development Workflow:**
1. `./mvnw test` - Run all unit tests quickly
2. `./mvnw verify` - Run tests + generate JaCoCo report + enforce coverage rules
3. Open `target/site/jacoco/index.html` - Visual coverage analysis
4. `./mvnw clean verify` - Clean build to ensure no stale artifacts affect results

**Coverage Verification:**
- Green bars in JaCoCo report = good coverage
- Yellow bars = partial coverage (some branches missed)
- Red bars = no coverage (critical gaps)
- Package-level view shows overall percentage for data.models and data.repositories

---

## 4. Critical Success Factors

The task will be successful when:

1. ✅ `./mvnw verify` completes successfully without JaCoCo coverage failures
2. ✅ JaCoCo report shows ≥70% line coverage for `villagecompute.calendar.data.models` package
3. ✅ JaCoCo report shows ≥70% line coverage for `villagecompute.calendar.data.repositories` package
4. ✅ All unit tests pass (zero failures)
5. ✅ No tests depend on external services (all use @QuarkusTest with H2 in-memory database)
6. ✅ All entity validation constraints are tested (@NotNull, @Email, @Size)
7. ✅ All relationship cascade operations are tested (persist and delete)
8. ✅ All repository custom query methods are tested (including edge cases)

**Action Plan:**
1. Run coverage analysis to identify gaps (see Step 1 above)
2. Add missing test methods to existing test files (DO NOT create new files)
3. Focus on high-value coverage: custom methods, validations, relationships
4. Verify success with `./mvnw clean verify`

**Key Insight:** The task description mentions creating tests for "all 11 entity classes" but the actual codebase has 7 main entities (CalendarUser, UserCalendar, CalendarTemplate, CalendarOrder, DelayedJob, PageView, AnalyticsRollup) plus DefaultPanacheEntityWithTimestamps (base class). All test files exist - your job is to ensure they provide sufficient coverage.
