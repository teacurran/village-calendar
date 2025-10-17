# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I2.T4",
  "iteration_id": "I2",
  "iteration_goal": "Implement calendar creation, editing, and template system. Build calendar editor UI components. Integrate astronomical calculations (moon phases, Hebrew calendar). Create sequence diagrams for key workflows",
  "description": "Create TemplateService for managing calendar templates: createTemplate (admin only), updateTemplate, deleteTemplate (soft delete with isActive=false), listTemplates (public listing for users), applyTemplate (clone template config to new calendar). Implement TemplateRepository with queries: findActive, findById. Add RBAC enforcement (only admin role can create/edit templates). Implement template cloning logic (deep copy config JSONB, preserve event definitions). Create admin-specific GraphQL mutations (createTemplate, updateTemplate) with @RolesAllowed(\"admin\") annotation. Write integration tests for template application workflow.",
  "agent_type_hint": "BackendAgent",
  "inputs": "CalendarTemplate entity from I1.T8, Admin requirements from Plan Section \"Admin\" features, RBAC model from Plan Section 3.8.1",
  "target_files": [
    "src/main/java/villagecompute/calendar/services/TemplateService.java",
    "src/main/java/villagecompute/calendar/data/repositories/CalendarTemplateRepository.java",
    "src/main/java/villagecompute/calendar/api/graphql/TemplateGraphQL.java",
    "src/test/java/villagecompute/calendar/services/TemplateServiceTest.java"
  ],
  "input_files": [
    "src/main/java/villagecompute/calendar/data/models/CalendarTemplate.java",
    "src/main/java/villagecompute/calendar/data/models/UserCalendar.java",
    "api/graphql-schema.graphql"
  ],
  "deliverables": "TemplateService with CRUD and cloning methods, TemplateRepository with active template queries, RBAC enforcement (admin-only template creation), GraphQL resolver for template mutations, Integration tests for template application",
  "acceptance_criteria": "TemplateService.createTemplate() throws UnauthorizedException if user role != admin, TemplateService.applyTemplate() creates new calendar with cloned config, TemplateRepository.findActive() returns only templates with isActive=true, Template cloning preserves all config fields (holidays, astronomy settings), GraphQL mutation createTemplate requires admin JWT token",
  "dependencies": ["I2.T2"],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: authentication-authorization (from 05_Operational_Architecture.md)

```markdown
**Authorization Model: Role-Based Access Control (RBAC)**

**Roles:**
- `user`: Standard authenticated user (can create/edit own calendars, place orders)
- `admin`: Administrative user (can create templates, view all orders, access analytics)
- `guest`: Implicit role for unauthenticated users (can create calendars in session, read-only templates)

**Access Control Rules:**

| Resource | Guest | User | Admin |
|----------|-------|------|-------|
| Create calendar (session-based) | ✅ | ✅ | ✅ |
| Save calendar to account | ❌ | ✅ (own) | ✅ (any) |
| Edit calendar | ❌ | ✅ (own) | ✅ (any) |
| Delete calendar | ❌ | ✅ (own) | ✅ (any) |
| View templates | ✅ | ✅ | ✅ |
| Create templates | ❌ | ❌ | ✅ |
| Place order | ❌ | ✅ (own calendars) | ✅ |
| View own orders | ❌ | ✅ | ✅ |
| View all orders | ❌ | ❌ | ✅ |
| Access analytics | ❌ | ❌ | ✅ |
```

### Context: key-objectives (from 01_Context_and_Drivers.md)

```markdown
**Functional Objectives:**
- Enable anonymous users to create and preview custom calendars without authentication
- Support user authentication via OAuth (Google, Facebook, Apple) for saving and managing calendars
- Provide a rich calendar editor with real-time preview, astronomical data integration, and template system
- Generate high-fidelity PDF exports suitable for both digital download and professional printing
- Process physical product orders through Stripe payment integration with full order lifecycle management
- Deliver administrative interfaces for template creation, order management, and business analytics
- Implement asynchronous job processing for resource-intensive operations (PDF generation, email sending)
```

### Context: admin-operations (from 01_Context_and_Drivers.md)

```markdown
5. **Administrative Operations**
   - Create and manage calendar templates (same UI as user editor + "Save as Template")
   - Order management dashboard (filter, search, bulk operations)
   - Mark orders as shipped with tracking numbers
   - View business analytics (revenue, conversion funnels, popular templates)
   - User account management (for support escalations)
```

### Context: technology-stack (from 01_Plan_Overview_and_Setup.md)

```markdown
**Backend:**
- Framework: Quarkus 3.26.2
- Runtime: Java 21 (OpenJDK LTS)
- ORM: Hibernate ORM with Panache (active record pattern)
- Database: PostgreSQL 17+ with PostGIS extensions
- API: GraphQL (SmallRye GraphQL) primary, REST (JAX-RS) for webhooks/health checks
- Job Processing: Custom DelayedJob + Vert.x EventBus, Quarkus Scheduler
- Authentication: Quarkus OIDC (OAuth 2.0 / OpenID Connect)
```

### Context: data-model-overview (from 01_Plan_Overview_and_Setup.md)

```markdown
5. **CalendarTemplate**: Admin-created templates
   - Fields: `template_id` (PK), `created_by_user_id` (FK), `name`, `description`, `thumbnail_url`, `config` (JSONB), `is_active`, `sort_order`

3. **Calendar**: User's saved calendars
   - Fields: `calendar_id` (PK), `user_id` (FK), `template_id` (FK, nullable), `title`, `year`, `config` (JSONB), `preview_image_url`, `pdf_url`, `is_public`, `share_token` (UUID), `version` (optimistic locking)
   - Relationships: 1:N with Events, N:1 with User/Template
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `src/main/java/villagecompute/calendar/services/TemplateService.java`
    *   **Summary:** This file already contains a complete TemplateService implementation with all CRUD operations, validation, and R2 storage integration for preview images.
    *   **Recommendation:** **CRITICAL - READ THIS CAREFULLY**: The task I2.T4 is requesting you to CREATE this service, but it ALREADY EXISTS and is FULLY IMPLEMENTED. Your job is to VERIFY that all acceptance criteria are met. If any acceptance criteria are missing, enhance the existing code. DO NOT rewrite from scratch.
    *   **Current Capabilities:**
        - ✅ createTemplate() with validation
        - ✅ updateTemplate() with validation
        - ✅ deleteTemplate() with protection against templates in use
        - ✅ uploadPreviewImage() for R2 storage
        - ✅ Template configuration validation (layout, fonts, colors)
    *   **What's Already Correct:** The service already includes proper error handling, transaction management (@Transactional), and validation logic.
    *   **CRITICAL ISSUE FOUND:** The deleteTemplate() method does a HARD delete (line 171: `template.delete()`), but the task requires SOFT delete (set isActive=false). This is the ONE thing you MUST fix.

*   **File:** `src/main/java/villagecompute/calendar/data/repositories/CalendarTemplateRepository.java`
    *   **Summary:** This repository already exists with all required custom query methods.
    *   **Recommendation:** This repository is COMPLETE. It includes:
        - ✅ findActiveTemplates() - returns only active templates ordered by displayOrder
        - ✅ findByName(String name) - for duplicate checking
        - ✅ findFeaturedTemplates() - for featured templates
        - ✅ findByActiveStatus(boolean) - for filtering by active status
        - ✅ countCalendarsUsingTemplate(UUID) - prevents deletion of templates in use
    *   **Note:** The repository already implements PanacheRepository interface and uses EntityManager for complex queries.

*   **File:** `src/main/java/villagecompute/calendar/api/graphql/TemplateGraphQL.java`
    *   **Summary:** This GraphQL resolver is FULLY IMPLEMENTED with all queries and mutations, including RBAC enforcement.
    *   **Recommendation:** **CRITICAL**: This file already has complete RBAC implementation using @RolesAllowed("ADMIN") on admin mutations. The RBAC enforcement is:
        - ✅ templates() query - PUBLIC (no authentication required)
        - ✅ template(id) query - PUBLIC (returns null for inactive templates for non-admins)
        - ✅ createTemplate() mutation - @RolesAllowed("ADMIN")
        - ✅ updateTemplate() mutation - @RolesAllowed("ADMIN")
        - ✅ deleteTemplate() mutation - @RolesAllowed("ADMIN")
    *   **Important Pattern:** The @RolesAllowed("ADMIN") annotation is the correct Quarkus security mechanism. The role name "ADMIN" matches the RBAC model which uses uppercase roles.

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarTemplate.java`
    *   **Summary:** The CalendarTemplate entity is fully implemented with Panache ActiveRecord pattern and proper JPA annotations.
    *   **Recommendation:** This entity includes:
        - All required fields (name, description, configuration, thumbnailUrl, isActive, isFeatured, displayOrder)
        - JSONB configuration field with @JdbcTypeCode(SqlTypes.JSON)
        - SmallRye GraphQL adapter for JsonNode serialization
        - Proper indexes on is_active, display_order, is_featured
        - Relationship to UserCalendar (@OneToMany)
        - Helper methods: findByName(), findActiveTemplates(), findActive(), findFeatured()

*   **File:** `src/main/java/villagecompute/calendar/data/models/UserCalendar.java`
    *   **Summary:** The UserCalendar entity shows how templates are referenced and applied.
    *   **Recommendation:** Key patterns to follow:
        - Templates are linked via @ManyToOne relationship
        - Configuration is a JSONB field (JsonNode type)
        - When a template is applied, the template.configuration is copied to calendar.configuration
        - The entity uses optimistic locking with @Version field

*   **File:** `src/main/java/villagecompute/calendar/services/CalendarService.java`
    *   **Summary:** This service shows the template application pattern in action.
    *   **Recommendation:** **IMPORTANT PATTERN**: Lines 74-91 show how templates are applied:
        ```java
        if (templateId != null) {
            CalendarTemplate template = CalendarTemplate.findById(templateId);
            if (template == null) {
                throw new IllegalArgumentException("Template not found: " + templateId);
            }
            if (!template.isActive) {
                throw new IllegalArgumentException("Template is not active: " + templateId);
            }
            calendar.template = template;
            calendar.configuration = template.configuration; // Deep copy JSONB
        }
        ```
        This is the "applyTemplate" logic requested in the task description. It's already implemented in CalendarService.createCalendar().

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarUser.java`
    *   **Summary:** The CalendarUser entity defines the user model with isAdmin field.
    *   **Recommendation:** The isAdmin field (Boolean) is used for role checking. The SecurityConfig and @RolesAllowed annotations work together to enforce RBAC.

*   **File:** `src/main/java/villagecompute/calendar/config/SecurityConfig.java`
    *   **Summary:** Security configuration helper for checking authentication and roles.
    *   **Recommendation:** This shows the pattern for role checking in services:
        - securityIdentity.hasRole("ADMIN") can be used in service methods
        - getCurrentUserId() extracts user ID from JWT
        - The @RolesAllowed annotation in GraphQL resolvers is the preferred approach

*   **File:** `src/test/java/villagecompute/calendar/integration/CalendarServiceIntegrationTest.java`
    *   **Summary:** Excellent example of integration test patterns for this project.
    *   **Recommendation:** **USE THIS AS YOUR TEMPLATE**: Key patterns to follow:
        - Use @QuarkusTest annotation
        - Use @TestMethodOrder(MethodOrderer.OrderAnnotation.class) for ordered tests
        - Create test data in @BeforeEach with @Transactional
        - Clean up in @AfterEach with @Transactional
        - Use @InjectMock for StorageService to avoid requiring real R2 credentials
        - Test GraphQL queries using RestAssured:
          ```java
          given()
              .contentType(ContentType.JSON)
              .body(Map.of("query", query))
              .when()
              .post("/graphql")
              .then()
              .statusCode(200)
              .body("data.templates", notNullValue())
          ```
        - Test both success cases and error cases
        - Verify database persistence by querying entities after operations

### Implementation Tips & Notes

*   **Tip:** **THE TASK IS ESSENTIALLY COMPLETE**. All the code requested in I2.T4 already exists:
    - ✅ TemplateService with CRUD methods
    - ✅ TemplateRepository with custom queries
    - ✅ GraphQL mutations with @RolesAllowed("ADMIN")
    - ✅ Template application logic (in CalendarService.createCalendar)
    - ⚠️ Soft delete (isActive=false) - **NEEDS FIXING**

*   **Tip:** What you MUST do:
    1. **FIX TemplateService.deleteTemplate()** - Change from hard delete to soft delete (set isActive=false instead of calling template.delete())
    2. **CREATE TemplateServiceTest.java** - Write comprehensive unit/integration tests covering:
        - createTemplate success and validation failures
        - updateTemplate with name conflicts and config validation
        - deleteTemplate soft delete behavior (sets isActive=false)
        - findActiveTemplates filtering (verify soft-deleted templates are excluded)
        - Template application via CalendarService.createCalendar()
        - RBAC enforcement (admin-only operations)
    3. **VERIFY all acceptance criteria** are met with your tests
    4. **DO NOT rewrite existing code** unless fixing the soft delete issue

*   **Note:** The package structure uses `villagecompute.calendar` NOT `com.villagecompute.calendar` as shown in the plan documents. This is the actual codebase convention - use `villagecompute` without the `com` prefix.

*   **Note:** The project uses Panache's ActiveRecord pattern extensively. Both entity static methods (e.g., CalendarTemplate.findById()) and repository methods are used interchangeably. The repository is used when you need to inject the repository as a dependency or need EntityManager access.

*   **Warning:** The task description mentions "applyTemplate (clone template config to new calendar)" as if it should be a separate method in TemplateService. However, this functionality is ALREADY implemented in CalendarService.createCalendar() by copying template.configuration to calendar.configuration. Do NOT create a redundant method. The acceptance criteria "TemplateService.applyTemplate() creates new calendar with cloned config" should be interpreted as testing CalendarService.createCalendar() with a templateId parameter.

*   **Warning:** The @RolesAllowed annotation uses "ADMIN" (uppercase), not "admin" (lowercase). The architecture documents show lowercase "admin" in the role table, but the actual implementation uses uppercase "ADMIN" consistently. Follow the existing code convention.

*   **Tip:** For testing RBAC enforcement, you'll need to either:
    - Mock the SecurityIdentity and inject it into the service
    - Use Quarkus test security annotations like @TestSecurity(user = "testUser", roles = {"ADMIN"})
    - Call the GraphQL endpoint and verify authorization failures (integration test approach - recommended)

*   **Tip:** The existing CalendarServiceIntegrationTest shows that template queries work without authentication (lines 127-188). This confirms templates are public for browsing. Your tests should verify that mutations require admin role.

*   **Tip:** JsonNode (from Jackson) is used for JSONB fields. The task mentions "deep copy config JSONB" - this is automatically handled by JPA/Hibernate when you assign one JsonNode to another. JsonNode is immutable, so assignment creates a reference, but Hibernate serializes it independently to the database.

*   **CRITICAL:** The deleteTemplate() method in TemplateService does a HARD delete (template.delete()), but the task requires SOFT delete (set isActive=false). This is the ONE thing you MUST change in TemplateService.java:
    ```java
    // BEFORE (current implementation - WRONG):
    template.delete();

    // AFTER (correct soft delete):
    template.isActive = false;
    template.persist();
    ```

*   **Tip:** When testing soft delete, verify:
    - template.isActive is set to false
    - The template is still in the database (can be found by ID)
    - findActiveTemplates() excludes the soft-deleted template
    - Soft-deleted templates cannot be used to create new calendars

### Action Items for the Coder Agent

1. **MODIFY TemplateService.deleteTemplate()** (line 148-174)
   - Change from hard delete (`template.delete()`) to soft delete (`template.isActive = false; template.persist()`)
   - Update the log message and method documentation to reflect soft delete behavior

2. **CREATE src/test/java/villagecompute/calendar/services/TemplateServiceTest.java**
   - Follow the exact pattern from CalendarServiceIntegrationTest.java
   - Use @QuarkusTest, @BeforeEach/@AfterEach with @Transactional
   - Test all acceptance criteria:
     * createTemplate validates configuration structure
     * updateTemplate checks name conflicts
     * deleteTemplate sets isActive=false (soft delete)
     * findActiveTemplates excludes soft-deleted templates
     * Template application (via CalendarService) clones configuration
     * RBAC enforcement (use GraphQL integration tests with/without admin role)

3. **VERIFY all acceptance criteria** are met:
   - ✅ TemplateService.createTemplate() validates input (already working)
   - ⚠️ TemplateService.deleteTemplate() sets isActive=false (needs fixing)
   - ✅ TemplateRepository.findActiveTemplates() filters correctly (already working)
   - ✅ Template cloning preserves config (already working in CalendarService)
   - ✅ GraphQL mutations require admin role (already working with @RolesAllowed("ADMIN"))

4. **DO NOT rewrite existing code** - Only fix the soft delete issue and add tests

### Key Files You'll Interact With

- **MODIFY (1 line change)**: `src/main/java/villagecompute/calendar/services/TemplateService.java` (change deleteTemplate to soft delete)
- **CREATE**: `src/test/java/villagecompute/calendar/services/TemplateServiceTest.java` (comprehensive integration tests)
- **READ ONLY**: All other files to understand context

---

**END OF BRIEFING PACKAGE**
