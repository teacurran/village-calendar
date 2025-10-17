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

**2. Integration Testing (Iteration-Specific)**

*   **Scope**: Component interactions, API endpoints, database operations, external service integrations
*   **Tools**: Quarkus test framework with REST Assured, Testcontainers (PostgreSQL, Jaeger)
*   **Coverage Target**: 70%+ for API layer, integration points
*   **Frequency**: Run before merge to main branch
*   **Examples**:
    *   GraphQL API workflows (create calendar, place order, generate PDF)
    *   Database transaction tests (ACID compliance, rollback scenarios)
    *   Job queue processing (DelayedJob execution, retry logic)
    *   External service integration (Stripe webhook, OAuth callback, R2 upload)
*   **Iteration Integration**: Integration tests written at end of each iteration (I2.T10, I3.T9, I4.T10, I5.T10)
```

### Context: ci-cd-pipeline (from 03_Verification_and_Glossary.md)

```markdown
### 5.2. CI/CD Pipeline

**Continuous Integration (CI) - Triggered on Every Push/Pull Request**

1.  **Checkout Code**: Clone repository
2.  **Backend Build**:
    *   Maven compile (`./mvnw compile`)
    *   Run unit tests (`./mvnw test`)
    *   JaCoCo coverage report (fail if <70% for service/API layers)
    *   Security scanning (Snyk dependency check, OWASP dependency check)
    *   SonarQube analysis (code quality, security hotspots)
3.  **Frontend Build**:
    *   npm install
    *   ESLint linting (`npm run lint`)
    *   Vite production build (`npm run build`)
    *   (Optional) Vitest unit tests (`npm run test`)
4.  **Integration Tests**:
    *   Run Quarkus integration tests (`./mvnw verify`)
    *   Use Testcontainers for PostgreSQL, Jaeger
5.  **Docker Build**:
    *   Build Docker image (`docker build -t villagecompute/calendar-api:${GIT_SHA}`)
    *   (Optional) Scan image for vulnerabilities (Trivy, Snyk Container)
6.  **Publish Artifact**:
    *   Push Docker image to Docker Hub with tags: `${GIT_SHA}`, `latest` (for main branch)
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

*   **File:** `src/main/java/villagecompute/calendar/data/models/UserCalendar.java`
    *   **Summary:** This is one of the primary entity models using Panache active record pattern. It extends `DefaultPanacheEntityWithTimestamps` and includes validation constraints (@NotNull, @Size), JSONB column mapping, relationships (@ManyToOne, @OneToMany), and custom query methods using the active record pattern.
    *   **Recommendation:** This is an EXCELLENT reference for understanding the entity structure in this project. You MUST follow the same patterns when writing entity tests. Note the validation constraints, the JSONB handling for `configuration` field, and the relationship patterns.
    *   **Key Details:**
        - Uses `@JdbcTypeCode(SqlTypes.JSON)` for JSONB columns
        - Has custom query methods like `findBySession()`, `findByUserAndYear()`, `findByUser()`
        - Includes relationships with cascade and fetch strategies
        - Extends base class for common fields (id, created, updated, version)

*   **File:** `src/main/java/villagecompute/calendar/data/repositories/UserCalendarRepository.java`
    *   **Summary:** This is a Panache repository following the repository pattern (implements `PanacheRepository<UserCalendar>`). It's marked `@ApplicationScoped` and contains custom query methods.
    *   **Recommendation:** You MUST follow this exact pattern for repository classes. Note that this project uses BOTH active record pattern (methods on entity) AND repository pattern (separate repository classes). The repositories provide additional custom query methods.
    *   **Key Details:**
        - Implements `PanacheRepository<T>`
        - Uses `@ApplicationScoped` annotation
        - Custom queries use Panache query methods: `find()`, `list()`, `firstResultOptional()`
        - Query syntax uses positional parameters: "user.id = ?1 AND year = ?2"

*   **File:** `src/main/java/villagecompute/calendar/data/models/DefaultPanacheEntityWithTimestamps.java`
    *   **Summary:** This is the base entity class that all entities extend. It provides common fields: UUID id, created timestamp, updated timestamp, and version for optimistic locking.
    *   **Recommendation:** You MUST understand this base class because ALL entity tests will involve these common fields. The `@CreationTimestamp` and `@UpdateTimestamp` annotations are from Hibernate and auto-populate on persist/update.
    *   **Key Details:**
        - Uses `@MappedSuperclass` (not an entity itself)
        - UUID primary key with `@GeneratedValue`
        - `@Version` for optimistic locking
        - Timestamps are auto-managed by Hibernate

*   **File:** `src/test/java/villagecompute/calendar/data/repositories/UserCalendarRepositoryTest.java`
    *   **Summary:** This is an EXISTING test file that demonstrates the exact testing pattern used in this project. It uses `@QuarkusTest`, `@Transactional` for setup/teardown, and includes a `TestDataCleaner` utility.
    *   **Recommendation:** You MUST use this file as your PRIMARY template for writing new tests. This shows you:
        - How to structure test classes (`@QuarkusTest` annotation)
        - How to inject dependencies (`@Inject`)
        - How to use `@Transactional` for test data setup
        - How to use the `TestDataCleaner` for cleanup
        - The assertion style and test naming conventions
    *   **Key Details:**
        - Uses `@BeforeEach` with `@Transactional` to set up test data
        - Calls `testDataCleaner.deleteAll()` for clean slate
        - Creates test entities and persists them before assertions
        - Test method names follow pattern: `testMethodName()` (camelCase)

### Implementation Tips & Notes

*   **Tip:** I found that there are already some repository tests in place (UserCalendarRepositoryTest, CalendarOrderRepositoryTest, CalendarUserRepositoryTest, CalendarTemplateRepositoryTest). You SHOULD review these for consistency in testing patterns.

*   **Note:** The task description mentions testing "all 11 entity classes" but I see the actual entity package is `villagecompute.calendar.data.models` (NOT `villagecompute.calendar.model` as specified in target_files). You MUST adjust the package names accordingly.

*   **Note:** The actual repository package is `villagecompute.calendar.data.repositories` (NOT `villagecompute.calendar.repository`). Update target file paths.

*   **Warning:** JaCoCo is NOT currently configured in pom.xml. You MUST add the JaCoCo Maven plugin configuration to generate coverage reports. This typically goes in the `<build><plugins>` section.

*   **Tip:** The project uses Java 21 as indicated in pom.xml (`<maven.compiler.release>21</maven.compiler.release>`). Ensure your test code is compatible with Java 21 features if you use any.

*   **Tip:** For testing JSONB fields (like `configuration` in UserCalendar), you'll need to use Jackson's `ObjectMapper` to create test JSON nodes. I see this pattern in the existing test: `ObjectMapper objectMapper` is injected and used to create `ObjectNode` instances.

*   **Tip:** The project already has a `TestDataCleaner` utility (in `src/test/java/villagecompute/calendar/data/repositories/TestDataCleaner.java`) that handles cleanup between tests. You MUST use this in all tests to ensure isolation.

*   **Tip:** Quarkus @QuarkusTest uses an in-memory database automatically for testing. You do NOT need to configure H2 separately - it's handled by Quarkus test infrastructure.

*   **Warning:** The task says to test "all 11 entity classes" but based on my file survey, the actual entity models are:
    1. CalendarUser
    2. UserCalendar
    3. CalendarOrder
    4. CalendarTemplate
    5. DelayedJob
    6. DelayedJobQueue
    7. PageView
    8. AnalyticsRollup
    9. (and potentially others in enums subdirectory)

    You should verify the complete list by examining the `data/models` directory.

*   **Tip:** For testing validation constraints, you'll need to trigger validation. In Quarkus with Hibernate, validation happens on `persist()`. You can also manually trigger it by injecting a `Validator` and calling `validator.validate(entity)`.

*   **Tip:** For testing relationships and cascade behavior, create parent and child entities, persist the parent, and verify that the relationship is properly established or that cascade operations work (e.g., deleting parent deletes children with `CascadeType.ALL, orphanRemoval = true`).

*   **Tip:** The existing tests show that you should test repository custom query methods with multiple scenarios (e.g., `testFindByUserAndYear` creates 3 calendars with 2 matching the query, then asserts only 2 are returned).

### JaCoCo Configuration Guidance

You MUST add the following to pom.xml in the `<build><plugins>` section:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
        <execution>
            <id>jacoco-check</id>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>PACKAGE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.70</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

This configuration will:
- Prepare the JaCoCo agent before tests run
- Generate the coverage report in `target/site/jacoco/index.html`
- Enforce 70% line coverage minimum (build fails if not met)

### Testing Strategy Summary

1. **Start with entity validation tests**: Create test classes for each entity in `src/test/java/villagecompute/calendar/data/models/` and test all validation constraints.

2. **Test JSONB serialization**: For entities with JSONB columns, verify that complex JSON objects can be persisted and retrieved correctly.

3. **Test relationships**: Verify that `@ManyToOne`, `@OneToMany` relationships work, and test cascade/fetch strategies by creating related entities and performing operations.

4. **Test repository CRUD**: For each repository, test basic operations: `persist()`, `findById()`, `listAll()`, `delete()`.

5. **Test custom queries**: For repositories with custom query methods (like `findByUserAndYear()`), create test data and verify the queries return expected results.

6. **Achieve 70% coverage**: Run `./mvnw test` and check the JaCoCo report to ensure coverage meets the threshold. Add more tests if needed.

### Package Structure for Tests

Based on the actual codebase structure, your test classes should be organized as:

```
src/test/java/villagecompute/calendar/
├── data/
│   ├── models/
│   │   ├── CalendarUserTest.java
│   │   ├── UserCalendarTest.java
│   │   ├── CalendarOrderTest.java
│   │   ├── CalendarTemplateTest.java
│   │   ├── DelayedJobTest.java
│   │   ├── PageViewTest.java
│   │   └── AnalyticsRollupTest.java
│   └── repositories/
│       ├── (existing tests already present)
│       └── (add any missing repository tests)
```

---

## End of Task Briefing Package

This briefing provides all the context, guidance, and strategic direction needed to complete task I1.T13 successfully. Follow the patterns established in existing tests, use the TestDataCleaner for isolation, configure JaCoCo for coverage reporting, and ensure all entity validation and repository query functionality is thoroughly tested.
