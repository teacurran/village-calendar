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

### Context: nfr-maintainability (from 01_Context_and_Drivers.md)

```markdown
#### 2.2.5. Maintainability & Extensibility

**Requirements:**
- Clear separation of concerns (UI, business logic, data access)
- Consistent coding patterns across features
- Comprehensive logging for debugging production issues
- Automated testing coverage (target 70%+ for critical paths)
- API versioning strategy for backward compatibility
- Database migration tooling (MyBatis Migrations already in use)

**Architectural Impact:**
- Layered architecture with Panache repositories, service layer, REST controllers
- GraphQL schema evolution without breaking existing clients
- Centralized logging (structured JSON logs) aggregated in observability stack
- OpenTelemetry instrumentation for distributed tracing
- API documentation via GraphQL introspection
- Feature flags for gradual rollout of new capabilities (future)
```

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

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarUser.java`
    *   **Summary:** This is a complete JPA entity using Panache active record pattern. It demonstrates proper validation annotations (@NotNull, @Email, @Size), relationship mappings (@OneToMany with cascade), and custom finder methods (findByOAuthSubject, findByEmail, findActiveUsersSince).
    *   **Recommendation:** You MUST use this as the reference pattern for all entity tests. The entity extends DefaultPanacheEntityWithTimestamps and uses public fields (Panache convention).

*   **File:** `src/test/java/villagecompute/calendar/data/models/CalendarUserTest.java`
    *   **Summary:** This is an **exemplary test file** that demonstrates the exact testing patterns you must follow. It includes: validation constraint tests, custom query tests, relationship cascade tests, all CRUD operations, optimistic locking tests, and comprehensive field testing.
    *   **Recommendation:** You MUST replicate this test structure for ALL remaining entity models. Key patterns to follow:
      - Use @QuarkusTest annotation on the test class
      - Inject Validator for constraint testing
      - Inject TestDataCleaner and call deleteAll() in @BeforeEach
      - Use @Transactional on test methods that modify data
      - Test ALL validation constraints individually
      - Test relationship cascade (persist and delete)
      - Test all custom finder methods
      - Test optimistic locking (version field increments on update)
      - Test all fields can be set and retrieved

*   **File:** `src/test/java/villagecompute/calendar/data/repositories/UserCalendarRepositoryTest.java`
    *   **Summary:** This is the **exemplary repository test** demonstrating how to test Panache repository custom query methods. It tests findByUserAndYear, findByUser, findBySession, findPublicById, findPublicCalendars, findByTemplate, findByYear.
    *   **Recommendation:** You MUST replicate this test structure for ALL remaining repository classes. Key patterns:
      - Use @QuarkusTest and @Transactional
      - Inject the repository under test
      - Create test data in setUp() with TestDataCleaner.deleteAll() first
      - Test each custom query method verifies correct filtering, ordering, and results
      - Use entityManager.flush() after persist operations to ensure DB sync

*   **File:** `src/test/java/villagecompute/calendar/data/repositories/TestDataCleaner.java`
    *   **Summary:** Utility class that deletes test data in correct order to avoid FK violations (Orders → Calendars → PageView → AnalyticsRollup → DelayedJob → Users → Templates).
    *   **Recommendation:** You MUST inject and use this in every test @BeforeEach method. Call testDataCleaner.deleteAll() to ensure test isolation.

*   **File:** `src/main/java/villagecompute/calendar/data/models/UserCalendar.java`
    *   **Summary:** Entity demonstrating JSONB field usage with @JdbcTypeCode(SqlTypes.JSON) and relationship to CalendarTemplate. Has custom finder methods in ActiveRecord pattern.
    *   **Recommendation:** When testing entities with JSONB fields, you MUST test serialization/deserialization. Use ObjectMapper to create JsonNode objects for testing.

*   **File:** `src/main/java/villagecompute/calendar/data/repositories/UserCalendarRepository.java`
    *   **Summary:** Repository implementing PanacheRepository<UserCalendar> with custom query methods. Shows proper use of find() with HQL queries and ORDER BY clauses.
    *   **Recommendation:** You MUST test all custom query methods in repositories. Verify ordering (DESC/ASC), filtering logic, and pagination where used.

### Implementation Tips & Notes

*   **Tip:** The project uses **Quarkus test framework** with the @QuarkusTest annotation. This automatically starts a test instance with dependency injection. All tests use an in-memory H2 database (configured in application.properties test profile).

*   **Tip:** The project has **JaCoCo already configured** in pom.xml with version 0.8.11. The task says "Configure JaCoCo Maven plugin" but I verified it's already there with executions for prepare-agent and report. You SHOULD verify the configuration is complete but DO NOT duplicate it.

*   **Note:** All entity models extend `DefaultPanacheEntityWithTimestamps` which provides: id (UUID), created (Instant), updated (Instant), version (Long for optimistic locking). You MUST test these inherited fields work correctly.

*   **Note:** The project uses **Jakarta validation** (not javax). Annotations are: jakarta.validation.constraints.NotNull, jakarta.validation.constraints.Email, etc. You MUST use the jakarta namespace.

*   **Warning:** Relationship tests MUST use entityManager.flush() and entityManager.clear() pattern to ensure proper persistence and reloading. See CalendarUserTest lines 336-337 and 376-377 for the correct pattern.

*   **Tip:** For JSONB field testing, you MUST inject ObjectMapper and use it to create JsonNode objects. Example from CalendarUserTest line 324: `ObjectNode config = objectMapper.createObjectNode();`

*   **Note:** The task target_files reference wrong package path "villagecompute.calendar.model" but actual path is "villagecompute.calendar.data.models" and "villagecompute.calendar.data.repositories". You MUST use the correct actual paths.

*   **Critical Pattern:** Test isolation requires calling testDataCleaner.deleteAll() in @BeforeEach to clear ALL tables in correct FK order. This prevents test interference and ensures predictable test state.

*   **Coverage Target:** The task requires >70% code coverage for model and repository packages. Based on the exemplary tests, you can achieve this by testing: all validation constraints, all custom finder methods, all CRUD operations, relationship cascade, optimistic locking, and JSONB serialization.

### Entities Requiring Tests (Based on Codebase Survey)

From my analysis, the following entities already have tests and should be used as references:
- ✅ CalendarUser (CalendarUserTest.java) - **COMPLETE REFERENCE**
- ✅ UserCalendar (UserCalendarTest.java)
- ✅ CalendarTemplate (CalendarTemplateTest.java)
- ✅ CalendarOrder (CalendarOrderTest.java)
- ✅ PageView (PageViewTest.java)
- ✅ AnalyticsRollup (AnalyticsRollupTest.java)
- ✅ DelayedJob (DelayedJobTest.java)

The task says "all 11 entity classes" but I found 7 existing entity test files. You should verify which entities are missing tests by comparing the entities in `src/main/java/villagecompute/calendar/data/models/` with existing test files.

### Repositories Requiring Tests (Based on Codebase Survey)

From my analysis, the following repositories already have tests:
- ✅ CalendarUserRepository (CalendarUserRepositoryTest.java)
- ✅ UserCalendarRepository (UserCalendarRepositoryTest.java) - **COMPLETE REFERENCE**
- ✅ CalendarTemplateRepository (CalendarTemplateRepositoryTest.java)
- ✅ CalendarOrderRepository (CalendarOrderRepositoryTest.java)

The task says "all repository classes" - you should verify which repositories exist in `src/main/java/villagecompute/calendar/data/repositories/` and ensure all have tests.

### Test Execution & Verification Commands

1. **Run all tests:** `./mvnw test`
2. **Generate JaCoCo report:** `./mvnw verify` (report at target/site/jacoco/index.html)
3. **Check specific package coverage:** Open target/site/jacoco/index.html and navigate to villagecompute.calendar.data.models and villagecompute.calendar.data.repositories packages
4. **Verify no external dependencies:** All tests should use @QuarkusTest with H2 in-memory database (no Docker containers, no external PostgreSQL)

---

## 4. Critical Success Factors

Based on my analysis, the task will be successful ONLY if:

1. ✅ You follow the **exact test patterns** from CalendarUserTest.java and UserCalendarRepositoryTest.java
2. ✅ Every entity test includes: validation tests, relationship tests, CRUD tests, custom finder tests, optimistic locking tests
3. ✅ Every repository test includes: all custom query methods tested with correct filtering and ordering
4. ✅ You use TestDataCleaner.deleteAll() in every @BeforeEach for test isolation
5. ✅ You achieve >70% code coverage for model and repository packages (verify with JaCoCo report)
6. ✅ All tests pass with ./mvnw test (0% failure tolerance per quality gates)
7. ✅ No tests depend on external services (all use in-memory H2 database with @QuarkusTest)
8. ✅ JaCoCo configuration in pom.xml is verified/completed (already exists, just verify it works)

**IMPORTANT:** The task description mentions target files with incorrect package paths. You MUST use the actual package structure:
- Actual: `src/main/java/villagecompute/calendar/data/models/`
- Actual: `src/main/java/villagecompute/calendar/data/repositories/`
- NOT: `src/main/java/villagecompute/calendar/model/` (incorrect)

The tests already exist for most entities/repositories, but you should verify completeness and coverage targets are met.
