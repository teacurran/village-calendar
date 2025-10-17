# Project Plan: Village Calendar - Iteration 2

---

<!-- anchor: iteration-2-plan -->
## Iteration 2: Core Calendar Functionality & User Features

**Iteration ID:** `I2`

**Goal:** Implement calendar creation, editing, and template system. Build calendar editor UI components. Integrate astronomical calculations (moon phases, Hebrew calendar). Create sequence diagrams for key workflows.

**Prerequisites:** `I1` (Project foundation, database schema, entity models, GraphQL stubs)

**Duration Estimate:** 3-4 weeks

**Deliverables:**
- Functional calendar CRUD operations (GraphQL API)
- Calendar editor UI (Vue components with PrimeVue)
- Template system (admin creates templates, users apply them)
- Event management (create, edit, delete calendar events)
- Astronomical calculation integration (moon phases, Hebrew calendar dates)
- Sequence diagrams for OAuth login, order placement, PDF generation
- Unit and integration tests for calendar services

---

<!-- anchor: task-i2-t1 -->
### Task 2.1: Generate Sequence Diagrams for Key Workflows (PlantUML)

**Task ID:** `I2.T1`

**Description:**
Create three PlantUML sequence diagrams documenting critical interaction flows: (1) OAuth Login Flow - showing user authentication via Google/Facebook/Apple, token exchange, user creation/retrieval, JWT token generation; (2) Order Placement Flow - showing cart checkout, Stripe Checkout session creation, payment webhook processing, order status updates, email job enqueueing; (3) PDF Generation Flow - showing async job workflow, calendar data retrieval, Batik rendering, R2 upload, status polling. Each diagram must include all actors (User, Vue SPA, Quarkus API, external services, database), show message sequences with proper timing, include error scenarios (alt/opt blocks), and follow PlantUML sequence diagram best practices.

**Agent Type Hint:** `DiagrammingAgent`

**Inputs:**
- Sequence diagram descriptions from Plan Section 3.7.3 "Key Interaction Flows"
- OAuth authentication architecture from Plan Section 3.8.1
- Order placement workflow from Plan Section 3.7.3 Flow 2
- PDF generation workflow from Plan Section 3.7.3 Flow 3

**Input Files:** []

**Target Files:**
- `docs/diagrams/sequence_oauth_login.puml`
- `docs/diagrams/sequence_order_placement.puml`
- `docs/diagrams/sequence_pdf_generation.puml`

**Deliverables:**
- Three PlantUML sequence diagram files rendering correctly
- PNG exports of all diagrams for documentation
- Diagrams accurately reflect workflows described in architecture plan
- Error handling scenarios included (alt blocks for failures)

**Acceptance Criteria:**
- All three PlantUML files validate and render without errors
- OAuth login diagram shows complete flow from user click to JWT token response
- Order placement diagram includes Stripe webhook validation and async email job
- PDF generation diagram shows job queue polling, worker processing, R2 upload
- Timing and activation bars clearly show which components are active
- Error scenarios documented (e.g., OAuth provider down, Stripe API failure)

**Dependencies:** `I1.T1` (requires docs directory structure)

**Parallelizable:** Yes (can run concurrently with backend implementation tasks)

---

<!-- anchor: task-i2-t2 -->
### Task 2.2: Implement Calendar Service and Repository

**Task ID:** `I2.T2`

**Description:**
Create CalendarService with business logic for calendar operations: createCalendar (from template or blank), updateCalendar (modify config, events), deleteCalendar (soft delete or hard delete), getCalendar (by ID with authorization check), listCalendars (by user, with pagination). Implement CalendarRepository with Panache custom queries: findByUserId, findByShareToken, findTemplates (isActive=true). Handle calendar versioning (optimistic locking with version field). Implement session-to-user conversion (migrate calendars from CalendarSession to User on login). Add authorization checks (user can only access own calendars unless admin). Write unit tests for all service methods.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- Calendar entity model from I1.T8
- GraphQL schema calendar queries/mutations from I1.T6
- Calendar service requirements from Plan Section 2

**Input Files:**
- `src/main/java/com/villagecompute/calendar/model/Calendar.java`
- `src/main/java/com/villagecompute/calendar/model/User.java`
- `api/graphql-schema.graphql`

**Target Files:**
- `src/main/java/com/villagecompute/calendar/service/CalendarService.java`
- `src/main/java/com/villagecompute/calendar/repository/CalendarRepository.java`
- `src/test/java/com/villagecompute/calendar/service/CalendarServiceTest.java`

**Deliverables:**
- CalendarService class with all CRUD methods implemented
- CalendarRepository with custom query methods
- Authorization logic (user can only access own calendars)
- Optimistic locking (version field prevents concurrent update conflicts)
- Unit tests achieving >80% code coverage for service layer

**Acceptance Criteria:**
- `CalendarService.createCalendar()` persists new calendar to database
- `CalendarService.updateCalendar()` throws exception if version mismatch
- `CalendarService.listCalendars(userId)` returns only user's calendars
- `CalendarRepository.findByShareToken()` returns public calendar or null
- Authorization check throws UnauthorizedException if user accesses other user's calendar
- All unit tests pass with `./mvnw test`

**Dependencies:** `I1.T8` (requires Calendar entity), `I1.T9` (requires User service for authorization)

**Parallelizable:** No (complex business logic requires focused implementation)

---

<!-- anchor: task-i2-t3 -->
### Task 2.3: Implement Event Service and Repository

**Task ID:** `I2.T3`

**Description:**
Create EventService for managing calendar events: addEvent (to calendar), updateEvent (text, emoji, color), deleteEvent, listEvents (by calendar, optionally filtered by date range). Implement EventRepository with custom queries: findByCalendarId, findByDateRange. Add validation: event date must be within calendar year, event text max 500 characters, emoji must be valid Unicode. Handle bulk event operations (import multiple events from CSV or JSON). Write unit tests for event service and repository.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- Event entity model from I1.T8
- GraphQL schema event operations from I1.T6

**Input Files:**
- `src/main/java/com/villagecompute/calendar/model/Event.java`
- `src/main/java/com/villagecompute/calendar/model/Calendar.java`
- `api/graphql-schema.graphql`

**Target Files:**
- `src/main/java/com/villagecompute/calendar/service/EventService.java`
- `src/main/java/com/villagecompute/calendar/repository/EventRepository.java`
- `src/test/java/com/villagecompute/calendar/service/EventServiceTest.java`

**Deliverables:**
- EventService class with CRUD and validation methods
- EventRepository with custom queries
- Validation logic for event fields
- Unit tests with >80% coverage

**Acceptance Criteria:**
- `EventService.addEvent()` validates event date is within calendar year
- `EventService.addEvent()` rejects events with text >500 characters
- `EventRepository.findByDateRange()` returns events within specified date range
- Emoji validation accepts valid Unicode emoji sequences
- Unit tests cover all validation scenarios and edge cases

**Dependencies:** `I2.T2` (requires CalendarService for authorization context)

**Parallelizable:** Partially (can start concurrently with I2.T2 if calendar authorization logic mocked)

---

<!-- anchor: task-i2-t4 -->
### Task 2.4: Implement Template Service and Admin Operations

**Task ID:** `I2.T4`

**Description:**
Create TemplateService for managing calendar templates: createTemplate (admin only), updateTemplate, deleteTemplate (soft delete with isActive=false), listTemplates (public listing for users), applyTemplate (clone template config to new calendar). Implement TemplateRepository with queries: findActive, findById. Add RBAC enforcement (only admin role can create/edit templates). Implement template cloning logic (deep copy config JSONB, preserve event definitions). Create admin-specific GraphQL mutations (createTemplate, updateTemplate) with @RolesAllowed("admin") annotation. Write integration tests for template application workflow.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- CalendarTemplate entity from I1.T8
- Admin requirements from Plan Section "Admin" features
- RBAC model from Plan Section 3.8.1

**Input Files:**
- `src/main/java/com/villagecompute/calendar/model/CalendarTemplate.java`
- `src/main/java/com/villagecompute/calendar/model/Calendar.java`
- `api/graphql-schema.graphql`

**Target Files:**
- `src/main/java/com/villagecompute/calendar/service/TemplateService.java`
- `src/main/java/com/villagecompute/calendar/repository/TemplateRepository.java`
- `src/main/java/com/villagecompute/calendar/api/graphql/TemplateResolver.java` (update from stub)
- `src/test/java/com/villagecompute/calendar/service/TemplateServiceTest.java`

**Deliverables:**
- TemplateService with CRUD and cloning methods
- TemplateRepository with active template queries
- RBAC enforcement (admin-only template creation)
- GraphQL resolver for template mutations
- Integration tests for template application

**Acceptance Criteria:**
- `TemplateService.createTemplate()` throws UnauthorizedException if user role != admin
- `TemplateService.applyTemplate()` creates new calendar with cloned config
- `TemplateRepository.findActive()` returns only templates with isActive=true
- Template cloning preserves all config fields (holidays, astronomy settings)
- GraphQL mutation `createTemplate` requires admin JWT token

**Dependencies:** `I2.T2` (requires CalendarService for template application)

**Parallelizable:** Yes (can develop concurrently with EventService)

---

<!-- anchor: task-i2-t5 -->
### Task 2.5: Integrate Astronomical Calculation Libraries

**Task ID:** `I2.T5`

**Description:**
Integrate SunCalc library (or Java port) for moon phase calculations and Proj4J for geospatial projections. Create AstronomicalService with methods: calculateMoonPhases (for calendar year, returns array of phase dates and illumination percentages), calculateHebrewCalendarDates (convert Gregorian dates to Hebrew calendar), calculateSeasonalEvents (equinoxes, solstices). Store calculation results in calendar.config JSONB field or separate table (if complex). Add configuration option in calendar editor to enable/disable astronomical overlays. Write unit tests verifying calculation accuracy against known astronomical data (e.g., 2025 full moon dates).

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- Astronomical calculation requirements from Plan Section "Features"
- SunCalc and Proj4J library documentation

**Input Files:**
- `pom.xml` (add SunCalc/Proj4J dependencies)
- `src/main/java/com/villagecompute/calendar/model/Calendar.java`

**Target Files:**
- `src/main/java/com/villagecompute/calendar/service/AstronomicalService.java`
- `src/main/java/com/villagecompute/calendar/util/astronomical/MoonPhaseCalculator.java`
- `src/main/java/com/villagecompute/calendar/util/astronomical/HebrewCalendarConverter.java`
- `src/test/java/com/villagecompute/calendar/service/AstronomicalServiceTest.java`

**Deliverables:**
- AstronomicalService class with calculation methods
- SunCalc/Proj4J integration (Maven dependencies added)
- Moon phase and Hebrew calendar calculation logic
- Unit tests verifying calculation accuracy

**Acceptance Criteria:**
- `AstronomicalService.calculateMoonPhases(2025)` returns correct full moon dates for 2025
- `AstronomicalService.calculateHebrewCalendarDates()` converts Gregorian to Hebrew dates accurately
- Unit tests compare calculations against NASA moon phase data or Hebrew calendar tables
- Service methods handle edge cases (leap years, timezone conversions)
- Calculation performance: <100ms for full year of moon phases

**Dependencies:** `I2.T2` (requires Calendar entity and config structure)

**Parallelizable:** Yes (independent library integration)

---

<!-- anchor: task-i2-t6 -->
### Task 2.6: Implement Calendar GraphQL Resolvers

**Task ID:** `I2.T6`

**Description:**
Replace stub implementations in CalendarResolver with real service calls. Implement queries: `calendar(id)` (fetch calendar with events, authorize user), `calendars(userId, year)` (list user's calendars with pagination). Implement mutations: `createCalendar(input)` (validate input, call CalendarService), `updateCalendar(id, input)` (authorize, update), `deleteCalendar(id)` (authorize, soft/hard delete). Inject SecurityIdentity for user context. Implement DataLoader pattern to prevent N+1 queries when fetching related entities (e.g., calendar with user and events). Add error handling (map service exceptions to GraphQL errors). Write integration tests for all resolver methods.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- CalendarService from Task I2.T2
- EventService from Task I2.T3
- GraphQL schema from I1.T6

**Input Files:**
- `src/main/java/com/villagecompute/calendar/service/CalendarService.java`
- `src/main/java/com/villagecompute/calendar/service/EventService.java`
- `src/main/java/com/villagecompute/calendar/api/graphql/CalendarResolver.java` (stub from I1.T10)
- `api/graphql-schema.graphql`

**Target Files:**
- `src/main/java/com/villagecompute/calendar/api/graphql/CalendarResolver.java` (updated)
- `src/main/java/com/villagecompute/calendar/api/graphql/dataloader/CalendarDataLoader.java`
- `src/test/java/com/villagecompute/calendar/api/graphql/CalendarResolverTest.java`

**Deliverables:**
- All calendar query/mutation resolvers implemented
- DataLoader pattern implemented for efficient queries
- Authorization checks (user can only query own calendars)
- Error handling (service exceptions mapped to GraphQL errors)
- Integration tests for all resolvers

**Acceptance Criteria:**
- GraphQL query `{ calendar(id: "123") { title events { eventText } } }` returns calendar with events
- Unauthorized access to other user's calendar returns GraphQL error
- `createCalendar` mutation persists calendar and returns new ID
- DataLoader batches queries (e.g., fetching 10 calendars with users requires 2 DB queries, not 11)
- Integration tests verify end-to-end GraphQL request/response flow

**Dependencies:** `I2.T2` (CalendarService), `I2.T3` (EventService), `I1.T10` (resolver stubs)

**Parallelizable:** No (requires services to be implemented first)

---

<!-- anchor: task-i2-t7 -->
### Task 2.7: Build Calendar Editor UI Components (Vue)

**Task ID:** `I2.T7`

**Description:**
Create Vue.js components for the calendar editor interface: CalendarEditor.vue (main editor container, loads calendar data, manages state), CalendarGrid.vue (visual calendar grid with month/date layout), EventEditor.vue (form for adding/editing events), EmojiPicker.vue (emoji selection dialog), HolidaySelector.vue (checkbox list of holiday sets), AstronomyToggle.vue (enable/disable moon phases, Hebrew calendar). Use PrimeVue components (DataTable, Dialog, InputText, Calendar, MultiSelect, Checkbox). Implement calendar state management in Pinia store (calendarStore.ts). Integrate with GraphQL API (use queries to load calendar, mutations to save changes). Add real-time preview updates (calendar grid updates as user adds events). Handle loading states and error messages.

**Agent Type Hint:** `FrontendAgent`

**Inputs:**
- Calendar editor requirements from Plan Section "Features"
- PrimeVue component documentation
- GraphQL schema from I1.T6

**Input Files:**
- `frontend/src/views/CalendarEditor.vue` (placeholder from I1.T11)
- `frontend/src/stores/calendar.ts` (create new)
- `api/graphql-schema.graphql`

**Target Files:**
- `frontend/src/views/CalendarEditor.vue` (updated)
- `frontend/src/components/calendar/CalendarGrid.vue`
- `frontend/src/components/calendar/EventEditor.vue`
- `frontend/src/components/calendar/EmojiPicker.vue`
- `frontend/src/components/calendar/HolidaySelector.vue`
- `frontend/src/components/calendar/AstronomyToggle.vue`
- `frontend/src/stores/calendar.ts`
- `frontend/src/graphql/calendar-queries.ts` (GraphQL queries/mutations)

**Deliverables:**
- Functional calendar editor UI with all sub-components
- Calendar grid displays year with 12 months, dates clickable to add events
- Event editor dialog allows adding/editing event text, emoji, color
- Emoji picker shows categorized emoji list (PrimeVue Dialog + emoji data)
- Holiday selector updates calendar config in Pinia store
- Astronomy toggle enables/disables moon phase and Hebrew calendar overlays
- GraphQL integration (load calendar, save changes)

**Acceptance Criteria:**
- Calendar editor loads existing calendar via GraphQL query on mount
- Clicking date in calendar grid opens event editor dialog
- Adding event updates Pinia store and triggers GraphQL mutation
- Emoji picker allows selecting emoji, updates event in editor
- Holiday selector changes persist to backend (updateCalendar mutation)
- Real-time preview: changes visible immediately without page reload
- Loading spinner shown while GraphQL queries in flight
- Error messages displayed if GraphQL mutations fail

**Dependencies:** `I2.T6` (requires GraphQL resolvers), `I1.T11` (requires routing setup)

**Parallelizable:** Partially (UI components can be developed with mock data, then integrated with API)

---

<!-- anchor: task-i2-t8 -->
### Task 2.8: Implement Template Gallery UI (Vue)

**Task ID:** `I2.T8`

**Description:**
Create template gallery view component displaying admin-curated calendar templates. Implement TemplateGallery.vue with grid layout (PrimeVue DataView or custom grid), showing template thumbnails, names, descriptions. Add filtering (by category, year, holiday set - future expansion). Implement "Start from Template" button that creates new calendar based on selected template (calls createCalendar mutation with templateId). Create TemplateCard.vue component for individual template preview. Add loading states, empty state ("No templates available"), error handling. Integrate with GraphQL (query templates, mutation createCalendar).

**Agent Type Hint:** `FrontendAgent`

**Inputs:**
- Template gallery requirements from Plan Section "Features"
- PrimeVue DataView component documentation

**Input Files:**
- `frontend/src/views/Home.vue` (add link to templates)
- `frontend/src/stores/calendar.ts`
- `api/graphql-schema.graphql`

**Target Files:**
- `frontend/src/views/TemplateGallery.vue`
- `frontend/src/components/calendar/TemplateCard.vue`
- `frontend/src/graphql/template-queries.ts`

**Deliverables:**
- Template gallery view with grid layout
- Template cards show thumbnail, name, description
- "Start from Template" button creates new calendar
- GraphQL integration (fetch templates, create calendar)
- Loading and error states handled

**Acceptance Criteria:**
- Gallery loads templates via GraphQL query on mount
- Template cards display thumbnail images (placeholder if none)
- Clicking "Start from Template" creates calendar and redirects to editor
- Empty state shown if no active templates
- Error message displayed if GraphQL query fails
- Responsive design (grid adapts to mobile, tablet, desktop)

**Dependencies:** `I2.T4` (TemplateService), `I2.T6` (GraphQL resolvers)

**Parallelizable:** Yes (can develop concurrently with calendar editor)

---

<!-- anchor: task-i2-t9 -->
### Task 2.9: Implement Guest Session Persistence

**Task ID:** `I2.T9`

**Description:**
Implement guest session management allowing anonymous users to create calendars before authenticating. Create SessionService for managing CalendarSession entities: createSession (generate UUID, store in localStorage), saveCalendarToSession (persist calendar data in JSONB session_data field), convertSessionToUser (on login, transfer session calendars to user account). Update CalendarService.createCalendar to accept optional sessionId (if user not authenticated). Update frontend to store sessionId in localStorage, include in GraphQL mutations if user not logged in. Implement session expiration (30 days, cleanup job deletes expired sessions). Add "Sign in to save" prompt in calendar editor for guest users.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- Guest session requirements from Plan Section "User Experience"
- CalendarSession entity from I1.T8

**Input Files:**
- `src/main/java/com/villagecompute/calendar/model/CalendarSession.java`
- `src/main/java/com/villagecompute/calendar/service/CalendarService.java`
- `frontend/src/stores/user.ts`

**Target Files:**
- `src/main/java/com/villagecompute/calendar/service/SessionService.java`
- `src/main/java/com/villagecompute/calendar/repository/SessionRepository.java`
- `src/main/java/com/villagecompute/calendar/api/graphql/SessionResolver.java`
- `frontend/src/utils/session.ts` (localStorage helpers)
- `src/test/java/com/villagecompute/calendar/service/SessionServiceTest.java`

**Deliverables:**
- SessionService with CRUD and conversion methods
- CalendarService supports sessionId parameter for guest users
- Frontend stores sessionId in localStorage
- Session-to-user conversion on login (GraphQL mutation)
- Session expiration cleanup job (Quarkus Scheduler)
- Unit tests for session service

**Acceptance Criteria:**
- Guest user can create calendar without logging in
- Calendar saved to session (CalendarSession entity in database)
- sessionId persisted in browser localStorage
- On login, convertGuestSession mutation transfers calendars to user account
- Expired sessions (>30 days old) deleted by cleanup job
- Frontend prompts guest user to sign in to save permanently

**Dependencies:** `I2.T2` (CalendarService), `I1.T9` (OAuth login for conversion)

**Parallelizable:** Partially (backend can be developed, frontend integration requires editor work)

---

<!-- anchor: task-i2-t10 -->
### Task 2.10: Write Integration Tests for Calendar Workflows

**Task ID:** `I2.T10`

**Description:**
Create end-to-end integration tests for critical calendar workflows using Quarkus test framework with REST Assured for GraphQL API testing. Test scenarios: (1) Create calendar from template - authenticate, query templates, create calendar from template, verify calendar created with correct config; (2) Add events to calendar - create calendar, add multiple events, query calendar with events, verify events returned; (3) Update calendar - create calendar, update config (enable astronomy), verify changes persisted; (4) Guest session conversion - create calendar as guest, authenticate, convert session, verify calendar transferred to user. Use test database (H2 or Testcontainers PostgreSQL). Achieve >70% code coverage for service and API layers.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- All implemented services and resolvers from I2 tasks
- Quarkus testing and REST Assured documentation

**Input Files:**
- All service and resolver classes from I2 tasks
- `api/graphql-schema.graphql`

**Target Files:**
- `src/test/java/com/villagecompute/calendar/integration/CalendarWorkflowTest.java`
- `src/test/java/com/villagecompute/calendar/integration/TemplateWorkflowTest.java`
- `src/test/java/com/villagecompute/calendar/integration/GuestSessionWorkflowTest.java`

**Deliverables:**
- Integration tests for all critical calendar workflows
- Tests use GraphQL API (not direct service calls)
- Test database setup and teardown automated
- Tests achieve >70% coverage for service/API layers
- All tests pass with `./mvnw verify`

**Acceptance Criteria:**
- Template workflow test creates calendar from template, verifies config cloned
- Event workflow test adds 5 events, queries calendar, verifies all 5 returned
- Update workflow test modifies calendar config, verifies changes in database
- Guest session workflow test creates calendar as guest, converts on login, verifies ownership
- Tests run in isolation (each test creates own test data, cleans up after)
- Integration tests complete in <60 seconds

**Dependencies:** All I2 tasks (requires complete calendar functionality)

**Parallelizable:** No (final integration testing task)

---

<!-- anchor: iteration-2-summary -->
### Iteration 2 Summary

**Total Tasks:** 10

**Completion Criteria:**
- All tasks marked as completed
- Calendar CRUD operations fully functional (backend + frontend)
- Template system working (admin creates, users apply)
- Event management integrated into calendar editor
- Astronomical calculations working (moon phases, Hebrew calendar)
- Guest session persistence and conversion implemented
- Sequence diagrams completed and documented
- Integration tests passing with adequate coverage

**Risk Mitigation:**
- If astronomical library integration proves complex, defer to Iteration 3 and implement as optional feature
- If DataLoader pattern causes issues, fall back to simpler query approach (accept N+1 queries initially)
- If guest session conversion has edge cases, implement admin tool to manually merge accounts

**Next Iteration Preview:**
Iteration 3 will focus on e-commerce functionality: Stripe payment integration, order management, checkout flow, and email notification system.
