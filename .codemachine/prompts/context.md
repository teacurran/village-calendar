# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I1.T4",
  "iteration_id": "I1",
  "iteration_goal": "Establish project infrastructure, define data models, create architectural artifacts, and implement foundational backend/frontend scaffolding",
  "description": "Generate PlantUML entity-relationship diagram for the complete database schema. Include all entities: User, CalendarSession, Calendar, Event, CalendarTemplate, Order, OrderItem, Payment, DelayedJob, PageView, AnalyticsRollup. Show primary keys, foreign keys, key fields (not all columns), cardinalities (1:1, 1:N, N:M). Use PlantUML entity syntax with proper relationship notation. Ensure diagram matches data model overview from Plan Section 2.",
  "agent_type_hint": "DiagrammingAgent",
  "inputs": "Data model overview from Plan Section 2 \"Data Model Overview\", Entity descriptions with fields, relationships, and constraints",
  "target_files": [
    "docs/diagrams/database_erd.puml"
  ],
  "input_files": [],
  "deliverables": "PlantUML ERD file rendering correctly, Diagram shows all 11 entities with relationships, PNG export of diagram (docs/diagrams/database_erd.png)",
  "acceptance_criteria": "PlantUML file validates and renders without errors, All entities from data model section included, Primary keys marked with <<PK>>, foreign keys with <<FK>>, Relationship cardinalities correctly shown (1:N, 1:1), Indexes and unique constraints annotated in diagram or comments",
  "dependencies": [
    "I1.T1"
  ],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### IMPORTANT NOTE: Architecture Documentation Location

**The architecture documentation files referenced in the manifests are NOT present in the repository.** The actual source of truth for the data model is:

1. **Database migration scripts** in `migrations/src/main/resources/scripts/`
2. **JPA entity models** in `src/main/java/villagecompute/calendar/data/models/`

Based on my analysis of the codebase, I have identified the following **actual entities** that exist:

### Actual Database Schema (from migrations and JPA entities)

**Existing tables (4 total):**

1. **calendar_users** - OAuth authenticated users
   - Primary Key: `id` (UUID)
   - Fields: oauth_provider, oauth_subject, email, display_name, profile_image_url, last_login_at, is_admin, created, updated, version
   - Unique Constraint: (oauth_provider, oauth_subject)
   - Indexes: email, last_login_at, is_admin (partial, where is_admin = true)
   - Relationships:
     * One-to-Many → user_calendars (cascade ALL, orphanRemoval)
     * One-to-Many → calendar_orders (cascade ALL, orphanRemoval)

2. **calendar_templates** - Reusable calendar designs
   - Primary Key: `id` (UUID)
   - Fields: name, description, thumbnail_url, is_active, is_featured, display_order, configuration (JSONB), preview_svg, created, updated, version
   - Indexes: name, (is_active, display_order, name), (is_featured, is_active, display_order), configuration (GIN index)
   - Relationships:
     * One-to-Many → user_calendars

3. **user_calendars** - User-created calendars
   - Primary Key: `id` (UUID)
   - Fields: user_id (FK), session_id, template_id (FK), name, year, is_public, configuration (JSONB), generated_svg, generated_pdf_url, created, updated, version
   - Foreign Keys:
     - user_id → calendar_users(id) ON DELETE CASCADE (optional/nullable - for guest sessions)
     - template_id → calendar_templates(id) ON DELETE SET NULL
   - Indexes: (user_id, year DESC), (session_id, updated DESC), template_id, (is_public, updated DESC)
   - Relationships:
     * Many-to-One → calendar_users (optional)
     * Many-to-One → calendar_templates (optional)
     * One-to-Many → calendar_orders (cascade ALL, orphanRemoval)

4. **calendar_orders** - E-commerce orders
   - Primary Key: `id` (UUID)
   - Fields: user_id (FK), calendar_id (FK), quantity, unit_price, total_price, status, shipping_address (JSONB), stripe_payment_intent_id, stripe_charge_id, notes, paid_at, shipped_at, created, updated, version
   - Foreign Keys:
     - user_id → calendar_users(id) ON DELETE RESTRICT
     - calendar_id → user_calendars(id) ON DELETE RESTRICT
   - Indexes: (user_id, created DESC), (status, created DESC), calendar_id, stripe_payment_intent_id
   - Relationships:
     * Many-to-One → calendar_users (mandatory)
     * Many-to-One → user_calendars (mandatory)

### Entities Mentioned in Task Description But NOT Implemented

The task description mentions these entities which **do NOT exist** in the current codebase:
- **CalendarSession** - No separate table; session_id is a field in user_calendars for guest tracking
- **Event** - No separate table; events are stored in JSONB configuration field in user_calendars
- **OrderItem** - No separate table; orders are single-item only currently (quantity field in calendar_orders)
- **Payment** - No separate table; payment info is embedded in calendar_orders (stripe_payment_intent_id, stripe_charge_id, paid_at)
- **DelayedJob** - Mentioned in architecture but not yet implemented in migrations
- **PageView** - Analytics table mentioned but not yet implemented
- **AnalyticsRollup** - Analytics table mentioned but not yet implemented

### Data Model Summary

The **actual implemented data model** (MVP scope) consists of **4 entities** supporting:
- **User authentication**: OAuth-based user accounts with admin flag
- **Guest sessions**: Anonymous users can create calendars using session_id before authenticating
- **Template system**: Admin-curated calendar templates with JSONB configuration
- **Calendar creation**: Users (authenticated or guest) create calendars from templates
- **E-commerce**: Single-item orders for printed calendars with Stripe integration
- **JSONB flexibility**: Configuration stored as JSON for flexible schema evolution

**Key architectural decisions observed:**
1. **Simplified MVP schema**: Focus on core e-commerce flow, defer advanced features
2. **Embedded data patterns**: Events in calendar config JSONB, payment data in orders table
3. **Guest session support**: session_id field enables anonymous users without separate table
4. **JSONB for flexibility**: Avoid premature normalization, use JSONB for evolving schemas
5. **Optimistic locking**: All tables have version field for concurrent update protection

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `migrations/src/main/resources/scripts/001_initial_schema.sql`
    *   **Summary:** This is the master database schema definition. It creates all 4 core tables (calendar_users, calendar_templates, user_calendars, calendar_orders) with complete column definitions, foreign keys, indexes, and table comments.
    *   **Recommendation:** You MUST use this file as the authoritative source for the database schema. The ERD should accurately reflect ALL columns, data types, constraints, and relationships defined here.
    *   **Key Details:**
        - All tables use UUID primary keys with `uuid_generate_v4()`
        - All tables have standard timestamp fields: created, updated, version (for optimistic locking)
        - JSONB columns: configuration (templates, calendars), shipping_address (orders)
        - Foreign key ON DELETE behaviors:
          * user_calendars.user_id → CASCADE (delete calendars when user deleted)
          * user_calendars.template_id → SET NULL (preserve calendar if template deleted)
          * calendar_orders.user_id → RESTRICT (prevent user deletion if orders exist)
          * calendar_orders.calendar_id → RESTRICT (prevent calendar deletion if orders exist)
        - Composite indexes: (user_id, year DESC), (user_id, created DESC)
        - Partial index: is_admin WHERE is_admin = true
        - GIN index: configuration (for JSONB queries)

*   **File:** `migrations/src/main/resources/scripts/002_add_admin_field.sql`
    *   **Summary:** Migration that adds the is_admin field to calendar_users table with partial index. This was added after the initial schema.
    *   **Recommendation:** The ERD MUST reflect the is_admin field as part of calendar_users, since this migration has been applied. Include a note that this was added in migration 002.

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarUser.java`
    *   **Summary:** JPA entity for calendar_users table. Uses Panache active record pattern. Defines relationships to UserCalendar and CalendarOrder.
    *   **Recommendation:** Cross-reference JPA annotations to ensure ERD relationship cardinalities match. Note the @OneToMany relationships with cascade ALL and orphanRemoval.
    *   **Key Details:**
        - @OneToMany to calendars (UserCalendar.user) with cascade ALL, orphanRemoval true
        - @OneToMany to orders (CalendarOrder.user) with cascade ALL, orphanRemoval true
        - Validation: @Email on email, @Size constraints on varchar fields
        - Static finder methods: findByOAuthSubject, findByEmail, findActiveUsersSince, hasAdminUsers, findAdminUsers

*   **File:** `src/main/java/villagecompute/calendar/data/models/UserCalendar.java`
    *   **Summary:** JPA entity for user_calendars table. Supports both authenticated users (user_id) and guest sessions (session_id). Links to CalendarTemplate and has CalendarOrders.
    *   **Recommendation:** The ERD MUST show user_id as optional (nullable) and session_id as alternative identifier for anonymous users. Template relationship is optional (ON DELETE SET NULL).
    *   **Key Details:**
        - @ManyToOne to user (optional=true, can be null for guest sessions)
        - @ManyToOne to template (optional=true, ON DELETE SET NULL)
        - @OneToMany to orders with cascade ALL, orphanRemoval true
        - configuration field is JsonNode (JSONB column)
        - Static finder methods: findBySession, findByUserAndYear, findByUser, findPublicById, findPublicCalendars
        - Note: session_id field enables guest users without separate CalendarSession table

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarTemplate.java`
    *   **Summary:** JPA entity for calendar_templates table. Templates are admin-curated reusable calendar designs with JSONB configuration.
    *   **Recommendation:** The ERD should show CalendarTemplate as a standalone entity with a one-to-many relationship to UserCalendar. Note the JSONB configuration field and boolean flags.
    *   **Key Details:**
        - @OneToMany to userCalendars (UserCalendar.template)
        - configuration field is JsonNode (JSONB column, NOT NULL)
        - Boolean flags: isActive, isFeatured
        - display_order INTEGER for template ordering
        - Static finder methods: findByName, findActiveTemplates, findActive, findFeatured

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarOrder.java`
    *   **Summary:** JPA entity for calendar_orders table. Represents e-commerce orders with Stripe payment integration. Note: No separate Payment or OrderItem tables - data is embedded.
    *   **Recommendation:** The ERD should show mandatory relationships to both CalendarUser and UserCalendar. Note the ON DELETE RESTRICT constraints to prevent data loss. Show Stripe payment fields as embedded data.
    *   **Key Details:**
        - @ManyToOne to user (mandatory, optional=false, ON DELETE RESTRICT)
        - @ManyToOne to calendar (mandatory, optional=false, ON DELETE RESTRICT)
        - shippingAddress field is JsonNode (JSONB column)
        - BigDecimal for monetary values (unit_price, total_price with precision 10, scale 2)
        - Status constants: PENDING, PAID, PROCESSING, SHIPPED, DELIVERED, CANCELLED
        - Stripe integration fields: stripe_payment_intent_id, stripe_charge_id
        - Timestamp fields: paid_at, shipped_at (nullable)
        - Static finder methods: findByUser, findByStatusOrderByCreatedDesc, findByCalendar, findByStripePaymentIntent, findRecentOrders
        - Helper methods: markAsPaid(), markAsShipped(), cancel(), isTerminal()

*   **File:** `docs/diagrams/component_diagram.puml`
    *   **Summary:** Existing PlantUML component diagram showing the C4 Level 3 internal architecture of the Quarkus API. Uses C4-PlantUML standard library.
    *   **Recommendation:** You SHOULD follow the same PlantUML styling and structure conventions used in this diagram. Use similar header comments and layout directives for consistency.
    *   **Key Details:**
        - Uses `!include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Component.puml`
        - Has a descriptive title
        - Uses LAYOUT_WITH_LEGEND() directive
        - Well-organized with Container_Boundary groupings for visual clarity

*   **File:** `docs/diagrams/data-model.mmd`
    *   **Summary:** Existing Mermaid diagram file showing entity relationships. This is an alternative format (Mermaid, not PlantUML).
    *   **Recommendation:** You can review this for entity understanding, but you MUST create a NEW PlantUML format file, NOT Mermaid. The task specifically requires PlantUML syntax.

### Implementation Tips & Notes

*   **Tip:** For PlantUML ERD diagrams, use the standard entity syntax with proper stereotypes:
    ```
    entity "table_name" as alias {
      * <<PK>> column_name : TYPE
      --
      * <<FK>> fk_column : TYPE
      column_name : TYPE
      column_name : TYPE
      ..
      <<index>> index_name
    }
    ```

*   **Tip:** Show relationships using proper PlantUML cardinality notation:
    - One-to-many: `EntityA ||--o{ EntityB : "relationship_name"`
    - One-to-one: `EntityA ||--|| EntityB : "relationship_name"`
    - Many-to-one: `EntityB }o--|| EntityA : "relationship_name"`
    - Optional many-to-one: `EntityB }o..o| EntityA : "relationship_name"` (dotted line for nullable FK)

*   **Note:** The task description mentions 11 entities from a theoretical plan, but the current codebase only has 4 tables implemented in the MVP scope. You MUST create the ERD based on the **actual implemented schema**, not the theoretical 11-entity model. The 4 entities are:
    1. calendar_users
    2. calendar_templates
    3. user_calendars
    4. calendar_orders

*   **Critical:** Document the **reality of what exists**, not what was planned. Add a diagram title and notes section explaining that this is the MVP schema (4 entities) and that additional entities (DelayedJob, PageView, AnalyticsRollup, etc.) are planned for future iterations.

*   **Note:** All tables use optimistic locking (version BIGINT field). You SHOULD include this field in the ERD and add a note explaining optimistic concurrency control.

*   **Note:** JSONB columns (configuration, shipping_address) are a key architectural feature. Add annotations or notes in the ERD to highlight these flexible schema fields and their purpose:
    - calendar_templates.configuration: Template design config (colors, layout, features)
    - user_calendars.configuration: User-specific calendar config and embedded events
    - calendar_orders.shipping_address: Customer shipping details

*   **Warning:** The user_calendars table has TWO ways to identify ownership:
    - **user_id**: For authenticated users (nullable FK to calendar_users)
    - **session_id**: For anonymous guest users (VARCHAR, no FK)

    The ERD should clearly show user_id as optional/nullable with a note about guest session support. This is a critical architectural pattern.

*   **Warning:** Foreign key ON DELETE behaviors are critical for data integrity:
    - user_calendars.user_id: **CASCADE** - If user deleted, cascade delete their calendars
    - user_calendars.template_id: **SET NULL** - If template deleted, keep calendar but clear template reference
    - calendar_orders.user_id: **RESTRICT** - Prevent deletion of users who have orders
    - calendar_orders.calendar_id: **RESTRICT** - Prevent deletion of calendars that have orders

    Add annotations or notes in the ERD showing these ON DELETE behaviors.

*   **Best Practice:** Include index annotations, especially for:
    - Composite indexes: (user_id, year DESC), (user_id, created DESC), (status, created DESC)
    - Partial indexes: is_admin WHERE is_admin = true
    - GIN indexes: configuration (JSONB)
    - Simple indexes: email, last_login_at, session_id, template_id, calendar_id, stripe_payment_intent_id

*   **Best Practice:** Show unique constraints clearly:
    - calendar_users: UNIQUE (oauth_provider, oauth_subject) - Ensures one account per OAuth identity

*   **Best Practice:** After creating the .puml file, you should generate the PNG export using PlantUML. The PNG should be saved to `docs/diagrams/database_erd.png` as specified in the deliverables. You can use the PlantUML CLI or an online renderer.

*   **Important:** Add a legend or notes section to the diagram explaining:
    1. This is the MVP schema (4 entities)
    2. Additional entities planned for future iterations (DelayedJob, PageView, AnalyticsRollup, etc.)
    3. JSONB columns used for flexible schema evolution
    4. Guest session support via session_id field (no separate CalendarSession table)
    5. Events embedded in user_calendars.configuration (no separate Event table)
    6. Payment data embedded in calendar_orders (no separate Payment table)
    7. Single-item orders (no separate OrderItem table, quantity field in calendar_orders)

*   **Tip:** Use PlantUML's `note` syntax to add important annotations:
    ```
    note right of user_calendars
      Guest sessions: user_id can be NULL,
      session_id identifies anonymous users
    end note
    ```

*   **Tip:** Use color coding to visually distinguish table types:
    - Core entities (calendar_users, user_calendars)
    - Configuration entities (calendar_templates)
    - Transactional entities (calendar_orders)

### Critical Clarification: MVP Schema vs. Planned Schema

**YOU MUST CREATE AN ERD FOR THE 4 ACTUAL TABLES, NOT 11 HYPOTHETICAL TABLES.**

The task description appears to be based on a planning document that envisioned 11 entities for the complete system, but the actual MVP implementation only has 4 tables. Your ERD should document the **reality of the codebase as it exists today**.

**Actual Implementation (4 tables):**
1. ✅ calendar_users (corresponds to "User" in plan)
2. ✅ calendar_templates (corresponds to "CalendarTemplate" in plan)
3. ✅ user_calendars (corresponds to "Calendar" in plan, with embedded "Event" and "CalendarSession" concepts)
4. ✅ calendar_orders (corresponds to "Order" in plan, with embedded "Payment" and "OrderItem" concepts)

**Not Yet Implemented (7 entities from plan):**
- ❌ CalendarSession (concept exists as session_id field in user_calendars)
- ❌ Event (concept exists as JSONB array in user_calendars.configuration)
- ❌ OrderItem (concept exists as quantity field in calendar_orders)
- ❌ Payment (concept exists as Stripe fields in calendar_orders)
- ❌ DelayedJob (planned for async job queue)
- ❌ PageView (planned for analytics)
- ❌ AnalyticsRollup (planned for analytics)

**Your task:** Create an accurate ERD showing the 4 implemented tables, with clear notes explaining the MVP scope and architectural decisions (embedded data patterns, JSONB flexibility, guest session support).
