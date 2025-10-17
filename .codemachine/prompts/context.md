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

### Relevant Existing Code

*   **File:** `pom.xml`
    *   **Summary:** Maven configuration file already contains JaCoCo plugin configured with coverage enforcement rules. JaCoCo is configured to exclude Panache entity classes from coverage checks (lines 302-311) because they use runtime enhancement which JaCoCo cannot measure accurately.
    *   **Recommendation:** You DO NOT need to add JaCoCo plugin configuration - it's already fully configured with a 70% coverage minimum for the models package and specific exclusions for Panache entities.
    *   **Critical Note:** The pom.xml shows JaCoCo check rules at lines 313-330. Repository package coverage rules were REMOVED because "Panache repositories cannot show JaCoCo coverage". This means you CANNOT rely on JaCoCo for repository coverage metrics.

*   **File:** `src/main/java/villagecompute/calendar/data/models/UserCalendar.java`
    *   **Summary:** This is a Panache entity extending `DefaultPanacheEntityWithTimestamps`. It uses public fields (Panache pattern), JPA annotations, validation constraints, and includes custom static finder methods following the Active Record pattern.
    *   **Recommendation:** When testing entities, you MUST test: validation constraints (@NotNull, @Size), relationship mappings (@ManyToOne, @OneToMany), JSONB serialization, and custom finder methods (e.g., `findByUserAndYear`, `findBySession`).
    *   **Pattern:** Note that this entity has BOTH static finder methods on the entity class AND a separate repository class. This is the Panache hybrid pattern.

*   **File:** `src/main/java/villagecompute/calendar/data/repositories/UserCalendarRepository.java`
    *   **Summary:** This is a PanacheRepository implementation with custom query methods. It implements `PanacheRepository<UserCalendar>` and is marked `@ApplicationScoped`.
    *   **Recommendation:** Repository tests MUST verify all custom query methods (findByUserAndYear, findByUser, findBySession, findPublicById, findPublicCalendars, findByTemplate, findByYear) as well as standard CRUD operations from PanacheRepository.

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarUser.java`
    *   **Summary:** Another Panache entity with OAuth-based authentication fields, email validation, unique constraints, and custom finder methods.
    *   **Recommendation:** Tests MUST verify the unique constraint on (oauth_provider, oauth_subject), email validation, and all custom finders (findByOAuthSubject, findByEmail, findActiveUsersSince, hasAdminUsers, findAdminUsers).

*   **File:** `src/test/java/villagecompute/calendar/data/repositories/UserCalendarRepositoryTest.java`
    *   **Summary:** This is an exemplar test showing the exact pattern you MUST follow. It uses @QuarkusTest, @Transactional, TestDataCleaner for setup/cleanup, EntityManager for flushing, and follows Given-When-Then structure.
    *   **Recommendation:** You MUST replicate this exact pattern for all entity and repository tests. Key elements:
        - `@QuarkusTest` class annotation
        - `@Inject TestDataCleaner testDataCleaner`
        - `@Inject EntityManager entityManager`
        - `@BeforeEach @Transactional void setUp()` calling `testDataCleaner.deleteAll()`
        - Each test method annotated with `@Test @Transactional`
        - Call `entityManager.flush()` after persists to ensure DB state
        - Helper methods for creating test entities (e.g., `createUserCalendar()`)

*   **File:** `src/test/java/villagecompute/calendar/data/repositories/CalendarUserRepositoryTest.java`
    *   **Summary:** Another exemplar showing comprehensive repository testing including: custom queries, CRUD operations, unique constraints, edge cases (not found), and bulk operations.
    *   **Recommendation:** This test shows you SHOULD test:
        - Happy path (found)
        - Sad path (not found - use `assertFalse(found.isPresent())`)
        - Unique constraints (use `assertThrows(Exception.class, ...)`)
        - Standard Panache methods (findById, listAll, count, delete, persist, flush)
        - Edge cases (empty results)
        - Multiple operations in sequence

*   **File:** `src/test/java/villagecompute/calendar/data/repositories/TestDataCleaner.java`
    *   **Summary:** Utility class for cleaning test data in correct order to avoid foreign key violations. Deletes in order: CalendarOrder → UserCalendar → PageView → AnalyticsRollup → DelayedJob → CalendarUser → CalendarTemplate.
    *   **Recommendation:** You MUST inject and use `TestDataCleaner` in all test classes. Always call `testDataCleaner.deleteAll()` in `@BeforeEach` to ensure clean test state.

### Implementation Tips & Notes

*   **Tip:** The project uses an in-memory H2 database for tests (see pom.xml line 230-232: `quarkus-jdbc-h2` with test scope). You do NOT need to configure this - it's automatic with `@QuarkusTest`.

*   **Tip:** I found that existing tests use JUnit 5 assertions from `org.junit.jupiter.api.Assertions.*`. You SHOULD use the same import and follow the same assertion style: `assertEquals`, `assertTrue`, `assertFalse`, `assertNotNull`, `assertThrows`.

*   **Note:** The task description mentions testing "11 entity classes" but based on the codebase structure, the entities are in `src/main/java/villagecompute/calendar/data/models/`. The Glob results show: CalendarTemplate, UserCalendar, CalendarOrder, DelayedJob, DelayedJobQueue, DefaultPanacheEntityWithTimestamps, PageView, CalendarUser, AnalyticsRollup. That's 9 model files (including base class and DelayedJobQueue). You MUST determine which are actual entities vs base classes.

*   **Note:** The task target_files reference paths like `src/test/java/villagecompute/calendar/model/` but the actual package structure is `src/test/java/villagecompute/calendar/data/models/` and `src/test/java/villagecompute/calendar/data/repositories/`. You MUST use the correct existing package structure.

*   **Warning:** Based on the pom.xml JaCoCo configuration comments, Panache entities are EXCLUDED from coverage because "they use public fields and runtime enhancement which JaCoCo cannot measure accurately". Similarly, "Panache repositories cannot show JaCoCo coverage". This means you CANNOT meet the 70% coverage requirement using JaCoCo metrics alone for these classes. The tests are still required for correctness verification, but coverage reporting will be limited.

*   **Critical:** Looking at existing test files, I can see tests already exist for ALL repositories:
    - CalendarOrderRepositoryTest.java
    - UserCalendarRepositoryTest.java
    - AnalyticsRollupRepositoryTest.java
    - PageViewRepositoryTest.java
    - CalendarUserRepositoryTest.java
    - DelayedJobRepositoryTest.java
    - CalendarTemplateRepositoryTest.java

    This means repository tests are ALREADY COMPLETE. You SHOULD focus on:
    1. Verifying all existing tests pass
    2. Enhancing existing tests if coverage is low
    3. Creating any MISSING entity validation tests (for model classes themselves, not just repositories)

*   **Tip:** For testing JSONB serialization/deserialization, look at how `UserCalendar.configuration` field is tested. It's a `JsonNode` field mapped with `@JdbcTypeCode(SqlTypes.JSON)`. You can create test data using `ObjectMapper.createObjectNode()` (see UserCalendarRepositoryTest line 61-63).

*   **Tip:** For testing validation constraints, you can trigger validation by persisting an invalid entity and catching the validation exception, or by using Hibernate Validator directly to validate entity instances before persisting.

*   **Best Practice:** Follow the Given-When-Then comment structure in test methods (see CalendarUserRepositoryTest lines 39-49) for clarity.

### Next Steps

1. Run `./mvnw test` to see current test status and which tests (if any) are failing
2. Run `./mvnw test jacoco:report` to generate coverage report at `target/site/jacoco/index.html`
3. Review coverage report to identify gaps in model/repository coverage
4. Create missing entity validation tests (NOT repository tests - those exist)
5. Enhance any low-coverage areas
6. Verify all acceptance criteria are met
