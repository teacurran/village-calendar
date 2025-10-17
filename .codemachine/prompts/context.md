# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I1.T3",
  "iteration_id": "I1",
  "iteration_goal": "Establish project infrastructure, define data models, create architectural artifacts, and implement foundational backend/frontend scaffolding",
  "description": "Create PlantUML component diagram showing the internal architecture of the Quarkus API application. Diagram must include: GraphQL API layer (resolvers), Service layer (CalendarService, UserService, OrderService, PdfService, EmailService), Repository layer (Panache repositories), Job Manager, Integration components (OAuth Client, Stripe Client, Email Sender, R2 Client), Security Filter. Show dependencies between components (e.g., GraphQL resolvers call services, services call repositories, services call integration clients). Use C4 Component diagram notation for clarity.",
  "agent_type_hint": "DiagrammingAgent",
  "inputs": "Component descriptions from Plan Section 2.3 \"Key Components/Services\", Technology stack (Quarkus, Panache, SmallRye GraphQL, Vert.x EventBus)",
  "target_files": ["docs/diagrams/component_diagram.puml"],
  "input_files": [],
  "deliverables": "PlantUML diagram file rendering correctly without syntax errors, Diagram accurately reflects components and dependencies described in architecture plan, PNG export of diagram for documentation (docs/diagrams/component_diagram.png)",
  "acceptance_criteria": "PlantUML file validates and renders in PlantUML viewer/IDE plugin, All components from architecture section included with correct relationships, Diagram follows C4 Component diagram conventions (containers, components, relationships), Visual clarity: components grouped by layer, clear dependency arrows",
  "dependencies": ["I1.T1"],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Key Components/Services (from 01_Plan_Overview_and_Setup.md)

```markdown
**Backend Components:**
1. **GraphQL API Layer**: Query/mutation resolvers, schema definition, DataLoader pattern for N+1 prevention
2. **Calendar Service**: CRUD operations, template application, event management, astronomical calculations
3. **User Service**: OAuth integration, session conversion (guest → authenticated), profile management
4. **Order Service**: Order placement, Stripe checkout creation, payment webhook handling, status updates
5. **PDF Service**: Job enqueueing, watermark logic, R2 upload coordination
6. **Job Manager**: DelayedJob queue management, EventBus publishing, priority assignment, retry logic
7. **Email Service**: Transactional email composition (order confirmations, shipping updates), SMTP delivery
8. **Repository Layer**: Panache repositories for User, Calendar, Order, DelayedJob, Template entities
9. **Security Layer**: JWT validation, RBAC enforcement, CSRF protection

**Frontend Components:**
1. **Calendar Editor**: Drag-drop event creation, emoji picker, holiday set selection, astronomical overlay toggles
2. **Template Gallery**: Browse/preview admin-curated templates, "Start from Template" flow
3. **User Dashboard**: My Calendars list (grid/list view), order history, account settings
4. **Checkout Flow**: Cart review, shipping address form, Stripe Checkout integration
5. **Admin Panel**: Template management, order processing dashboard, analytics visualization

**Async Job Workers:**
1. **PDF Generation Job**: Batik rendering, watermarking, R2 upload, calendar entity update
2. **Email Job**: Transactional email sending with retry logic
3. **Analytics Rollup Job**: Daily/weekly aggregation of page views, orders, revenue

**Key Diagrams Planned:**
- Component Diagram (PlantUML): Quarkus API internal components, dependencies (Created in Iteration 1)
- Database ERD (PlantUML): Entity relationships for users, calendars, orders, jobs (Created in Iteration 1)
- Sequence Diagrams (PlantUML): OAuth login flow, order placement flow, PDF generation flow (Created in Iteration 2)
- Deployment Diagram (PlantUML): Kubernetes topology, Cloudflare integration (Created in Iteration 1)
```

### Context: Technology Stack (from 01_Plan_Overview_and_Setup.md)

```markdown
**Backend:**
- Framework: Quarkus 3.26.2
- Runtime: Java 21 (OpenJDK LTS)
- ORM: Hibernate ORM with Panache (active record pattern)
- Database: PostgreSQL 17+ with PostGIS extensions
- API: GraphQL (SmallRye GraphQL) primary, REST (JAX-RS) for webhooks/health checks
- Job Processing: Custom DelayedJob + Vert.x EventBus, Quarkus Scheduler
- Authentication: Quarkus OIDC (OAuth 2.0 / OpenID Connect)
- Observability: OpenTelemetry → Jaeger (tracing), Micrometer → Prometheus (metrics)
- PDF Generation: Apache Batik 1.17 (SVG to PDF)
- Astronomical Calcs: SunCalc (port), Proj4J 4.1
```

### Context: Component Diagram (C4 Level 3) from Architecture Blueprint (from 03_System_Structure_and_Data.md)

```markdown
**Description:**

This diagram zooms into the **Quarkus API Application** container, revealing the major internal components and their dependencies. The architecture follows a layered pattern:

1. **GraphQL/REST Controllers**: HTTP entry points, request validation, response formatting
2. **Service Layer**: Business logic, workflow orchestration, transaction boundaries
3. **Repository Layer**: Data access via Panache, query construction, entity mapping
4. **Job Management**: DelayedJob enqueueing, EventBus integration
5. **Integration Components**: OAuth client, Stripe client, email sender, R2 client
6. **Security Components**: OIDC token validation, RBAC enforcement, CSRF protection

**Component Interaction Patterns:**

1. **GraphQL Query Flow**: Client → GraphQL API → Service Layer → Repository → PostgreSQL → Response mapping → Client
2. **Order Placement Flow**: Client → GraphQL Mutation (placeOrder) → Order Service → Stripe Client (create checkout) → Order Repository (persist) → Job Manager (enqueue email) → EventBus → Response
3. **PDF Generation Flow**: Calendar Service → PDF Service → Job Manager → Job Repository → EventBus → Job Worker (async) → Batik Renderer → R2 Client (upload)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `docs/diagrams/component-architecture.puml`
    *   **Summary:** This file already contains a PlantUML component diagram for the Village Calendar service. It uses C4 Component diagram notation and shows the API layer, Service layer, Data layer, and External Services. However, it may need to be updated or replaced to match the exact specifications in the current task (I1.T3).
    *   **Recommendation:** You SHOULD review this existing diagram carefully. The task asks you to create `docs/diagrams/component_diagram.puml` (note the underscore instead of hyphen). You can use the existing `component-architecture.puml` as a reference, but you MUST create a NEW file at the path specified in the task: `docs/diagrams/component_diagram.puml`. The new diagram must include all components specified in the architecture blueprint.

*   **File:** `src/main/java/villagecompute/calendar/services/`
    *   **Summary:** This directory contains the implemented service layer components. Key services identified:
        *   `AuthenticationService.java` - OAuth integration (corresponds to "User Service" in the plan)
        *   `CalendarService.java` - Calendar generation logic
        *   `CalendarGenerationService.java` - PDF rendering and calendar creation
        *   `OrderService.java` - E-commerce and fulfillment
        *   `PaymentService.java` - Stripe payment integration
        *   `EmailService.java` - Email notifications
        *   `StorageService.java` - R2 storage integration (corresponds to "R2 Client")
        *   `TemplateService.java` - Template management
        *   `AstronomicalCalculationService.java` - Moon phases, etc.
        *   `HebrewCalendarService.java` - Hebrew calendar calculations
        *   `PDFRenderingService.java` - PDF rendering logic
    *   **Recommendation:** Your component diagram MUST include these actual service implementations. Map the service file names to the component descriptions in the architecture plan.

*   **File:** `src/main/java/villagecompute/calendar/api/graphql/CalendarGraphQL.java`
    *   **Summary:** This file implements the GraphQL API layer using SmallRye GraphQL. It contains queries like `me()` and `myCalendars()`, and is annotated with `@GraphQLApi`.
    *   **Recommendation:** The diagram MUST show this GraphQL API component (and other GraphQL resolvers like `OrderGraphQL.java`, `TemplateGraphQL.java`) as the entry point that depends on the service layer.

*   **File:** `src/main/java/villagecompute/calendar/api/rest/`
    *   **Summary:** REST endpoints for OAuth callbacks (`AuthResource.java`), Stripe webhooks (`WebhookResource.java`), and bootstrapping (`BootstrapResource.java`).
    *   **Recommendation:** Include REST Controllers in your diagram to show that both GraphQL and REST are entry points to the system.

*   **File:** `src/main/java/villagecompute/calendar/data/repositories/`
    *   **Summary:** This directory contains Panache repository implementations:
        *   `CalendarUserRepository.java`
        *   `UserCalendarRepository.java`
        *   `CalendarTemplateRepository.java`
        *   `CalendarOrderRepository.java`
    *   **Recommendation:** Your diagram MUST show the Repository Layer with these specific repositories and their relationships to services.

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarUser.java`
    *   **Summary:** This is a JPA entity using Panache active record pattern. It extends `DefaultPanacheEntityWithTimestamps` and has static finder methods.
    *   **Recommendation:** This confirms that the repository layer uses Panache. Make sure the diagram accurately reflects "Panache Repository" as the technology for the data access layer.

*   **File:** `src/main/java/villagecompute/calendar/services/jobs/DelayedJobHandler.java`
    *   **Summary:** This is part of the async job processing system.
    *   **Recommendation:** The diagram should include a "Job Manager" component that handles DelayedJob enqueueing and EventBus publishing, as specified in the architecture.

### Implementation Tips & Notes

*   **Tip:** The architecture blueprint provides a complete PlantUML diagram example in section 3.5 (Component Diagram C4 Level 3). You SHOULD use this as your primary template, but you MUST verify it against the actual implemented code.

*   **Note:** There is an existing diagram at `docs/diagrams/component-architecture.puml` with similar content. However, the task explicitly requires a NEW file named `component_diagram.puml` (with underscore). Do NOT simply rename the old file. Create a new one that strictly follows the specifications in I1.T3.

*   **Warning:** The diagram MUST include ALL components mentioned in the acceptance criteria:
    - GraphQL API layer (resolvers) ✓
    - Service layer: CalendarService, UserService, OrderService, PdfService, EmailService ✓
    - Repository layer (Panache repositories) ✓
    - Job Manager ✓
    - Integration components: OAuth Client, Stripe Client, Email Sender, R2 Client ✓
    - Security Filter ✓

*   **Tip:** Use the C4 PlantUML include: `!include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Component.puml`

*   **Note:** The diagram should show dependencies as arrows:
    - GraphQL API → Services
    - Services → Repositories
    - Services → Integration Clients
    - Repositories → Database (external)
    - Integration Clients → External Systems

*   **Tip:** Group components by layer using `Container_Boundary` for visual clarity, as shown in the architecture blueprint example.

*   **Warning:** The acceptance criteria states the diagram must render correctly in a PlantUML viewer. Test your diagram by ensuring it has valid PlantUML syntax with no errors.

*   **Tip:** After creating the `.puml` file, you may need to generate a PNG export. However, the task deliverables mention "PNG export of diagram for documentation (docs/diagrams/component_diagram.png)". Check if you have access to a PlantUML rendering tool in your environment. If not, just ensure the .puml file is valid.

*   **Note:** Based on the actual codebase structure, here are the key mappings you should use:
    - "User Service" → `AuthenticationService` (handles OAuth, user management)
    - "Calendar Service" → `CalendarService` + `CalendarGenerationService` (calendar CRUD and generation)
    - "PDF Service" → `PDFRenderingService` (PDF rendering)
    - "Email Service" → `EmailService` (email notifications)
    - "Order Service" → `OrderService` + `PaymentService` (orders and payments)
    - "Job Manager" → `DelayedJobHandler` (async job processing)
    - "OAuth Client" → Part of `AuthenticationService` using Quarkus OIDC
    - "Stripe Client" → Used by `PaymentService`
    - "R2 Client" → `StorageService`
    - "Email Sender" → `EmailService`

*   **Warning:** The existing `component-architecture.puml` shows slightly different component names. Your NEW diagram should align with both the architecture plan AND the actual code. When there's a mismatch, prefer the actual implemented code structure but add descriptions that match the plan.

*   **Important:** The task requires you to show the following specific services from the plan:
    - **CalendarService** (maps to CalendarService + CalendarGenerationService)
    - **UserService** (maps to AuthenticationService)
    - **OrderService** (maps to OrderService + PaymentService)
    - **PdfService** (maps to PDFRenderingService)
    - **EmailService** (maps to EmailService)

    You should represent these conceptual services in the diagram even if they map to multiple Java classes.

*   **Tip:** Look at the existing `component-architecture.puml` to understand the current structure, but create a NEW diagram that:
    1. Uses the filename `component_diagram.puml` (with underscore)
    2. Matches the component list from the architecture blueprint
    3. Reflects the actual implemented code
    4. Uses clear, layered organization with Container_Boundary
    5. Shows all dependencies with relationship arrows

*   **Note:** The architecture blueprint example includes external systems (PostgreSQL, Cloudflare R2, OAuth Providers, Stripe API, Email Service, Vert.x EventBus) shown with `_Ext` suffixes. Your diagram should follow this pattern.
