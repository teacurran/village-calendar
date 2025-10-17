# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I1.T7",
  "iteration_id": "I1",
  "iteration_goal": "Establish project infrastructure, define data models, create architectural artifacts, and implement foundational backend/frontend scaffolding",
  "description": "Write SQL migration scripts for all database tables using MyBatis Migrations format. Create scripts in numbered order: 001_create_users_table.sql, 002_create_calendar_sessions_table.sql, 003_create_calendar_templates_table.sql, 004_create_calendars_table.sql, 005_create_events_table.sql, 006_create_orders_table.sql, 007_create_order_items_table.sql, 008_create_payments_table.sql, 009_create_delayed_jobs_table.sql, 010_create_page_views_table.sql, 011_create_analytics_rollups_table.sql. Each script must include: CREATE TABLE with all columns, primary key constraints, foreign key constraints with ON DELETE CASCADE/SET NULL, indexes (on foreign keys, frequently queried columns), unique constraints, comments on tables/columns. Follow PostgreSQL 17 syntax. Ensure PostGIS extension enabled in script 000_enable_postgis.sql.",
  "agent_type_hint": "DatabaseAgent",
  "inputs": "Database ERD from Task I1.T4 (docs/diagrams/database_erd.puml), Data model overview from Plan Section 2, Indexing strategy from Plan Section 3.6",
  "target_files": [
    "migrations/scripts/000_enable_postgis.sql",
    "migrations/scripts/001_create_users_table.sql",
    "migrations/scripts/002_create_calendar_sessions_table.sql",
    "migrations/scripts/003_create_calendar_templates_table.sql",
    "migrations/scripts/004_create_calendars_table.sql",
    "migrations/scripts/005_create_events_table.sql",
    "migrations/scripts/006_create_orders_table.sql",
    "migrations/scripts/007_create_order_items_table.sql",
    "migrations/scripts/008_create_payments_table.sql",
    "migrations/scripts/009_create_delayed_jobs_table.sql",
    "migrations/scripts/010_create_page_views_table.sql",
    "migrations/scripts/011_create_analytics_rollups_table.sql"
  ],
  "input_files": [
    "docs/diagrams/database_erd.puml"
  ],
  "deliverables": "All 12 migration scripts (000-011) created, Scripts execute successfully on fresh PostgreSQL 17 database, MyBatis Migrations CLI can apply scripts (migrate up), Database schema matches ERD diagram",
  "acceptance_criteria": "All migration scripts run without SQL errors on PostgreSQL 17, Foreign key constraints properly defined (correct ON DELETE behavior), Indexes created on all foreign key columns and frequently queried fields, JSONB columns used for calendar.config, session_data, shipping_address, job payload, BIGSERIAL primary keys, UUID for session_id and share_token, Script execution order produces schema matching ERD",
  "dependencies": ["I1.T2", "I1.T4"],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: data-model-overview (from 03_System_Structure_and_Data.md)

The architecture document provides detailed entity definitions and database design decisions:

**Key Design Decisions:**

1. **User Identity**: `users` table stores OAuth provider info (`oauth_provider`, `oauth_subject_id`) to support multiple providers per user
2. **Anonymous Sessions**: `calendar_sessions` table tracks guest user calendars, linked to `users` table upon login conversion
3. **Calendar Versioning**: `calendars` table includes `version` field for optimistic locking, future support for edit history
4. **Order Status**: `orders.status` enum (PENDING, PAID, IN_PRODUCTION, SHIPPED, DELIVERED, CANCELLED, REFUNDED) drives workflow state machine
5. **Job Queue**: `delayed_jobs` table with `locked_at`, `locked_by`, `attempts`, `last_error` supports distributed worker coordination
6. **Templates**: `calendar_templates` is separate from `calendars` to enable admin-curated vs user-created distinction
7. **Analytics**: `page_views`, `analytics_rollups` tables support basic analytics without external service dependency (Phase 1)

**Database Indexes Strategy:**

- **Primary Keys**: All tables use `bigserial` auto-incrementing primary keys for simplicity and performance (NOTE: The ERD and implementation use UUID for some entities)
- **Foreign Keys**: Enforce referential integrity with cascading deletes where appropriate (e.g., `Calendar.user_id` ON DELETE CASCADE)
- **Lookup Indexes**: Composite indexes on frequently queried columns (e.g., `(user_id, created_at)` for calendar lists)
- **Unique Constraints**: `users.email`, `orders.order_number`, `calendar.share_token` for business logic enforcement
- **JSONB Indexes**: GIN indexes on `calendar.config` for filtering by holiday sets, astronomy options

**Data Volume Projections (First Year):**

- Users: ~10,000 registered (+ 50,000 anonymous sessions)
- Calendars: ~25,000 saved (2.5 calendars per registered user)
- Orders: ~5,000 (20% conversion from saved calendars)
- DelayedJobs: ~100,000 processed (PDF generation, emails, analytics)
- PageViews: ~500,000 (avg 10 page views per session)

### Context: database_erd.puml (from docs/diagrams/)

The ERD PlantUML file shows the **MVP implementation has only 4 core entities** (not the full 11 mentioned in the task description):

**Implemented in MVP:**
1. **calendar_users** - OAuth authenticated users
2. **calendar_templates** - Reusable design templates
3. **user_calendars** - User-created calendars with embedded event data
4. **calendar_orders** - E-commerce orders with embedded payment data

**Key Architectural Pattern Changes from Original Plan:**
- **Events embedded in calendar configuration JSONB** (no separate Event table)
- **Payment data embedded directly in orders table** via Stripe-specific fields (no separate Payment table)
- **Orders support single-item purchases** with quantity field (no separate OrderItem table)
- **Guest sessions handled via session_id column** in user_calendars (no separate CalendarSession table)
- **DelayedJob, PageView, AnalyticsRollup tables planned but not yet implemented**

**Field Details from ERD:**

**calendar_users:**
- `id` UUID (PK)
- `oauth_provider` VARCHAR(50)
- `oauth_subject` VARCHAR(255)
- `email` VARCHAR(255)
- `display_name` VARCHAR(255)
- `profile_image_url` VARCHAR(500)
- `last_login_at` TIMESTAMPTZ
- `is_admin` BOOLEAN (added in migration 002)
- `created`, `updated`, `version` (timestamps and optimistic locking)
- UNIQUE constraint: (oauth_provider, oauth_subject)
- Indexes: email, last_login_at DESC, is_admin (partial WHERE is_admin = true)

**calendar_templates:**
- `id` UUID (PK)
- `name` VARCHAR(255)
- `description` TEXT
- `thumbnail_url` VARCHAR(500)
- `is_active`, `is_featured` BOOLEAN
- `display_order` INTEGER
- `configuration` JSONB
- `preview_svg` TEXT
- `created`, `updated`, `version`
- Indexes: name, (is_active, display_order, name), (is_featured, is_active, display_order), GIN index on configuration

**user_calendars:**
- `id` UUID (PK)
- `user_id` UUID (nullable FK → calendar_users)
- `session_id` VARCHAR(255) (for anonymous users)
- `template_id` UUID (nullable FK → calendar_templates)
- `name` VARCHAR(255)
- `year` INTEGER
- `is_public` BOOLEAN
- `configuration` JSONB (contains embedded events)
- `generated_svg` TEXT
- `generated_pdf_url` VARCHAR(500)
- `created`, `updated`, `version`
- FK behaviors: user_id ON DELETE CASCADE, template_id ON DELETE SET NULL
- Indexes: (user_id, year DESC), (session_id, updated DESC), template_id, (is_public, updated DESC)

**calendar_orders:**
- `id` UUID (PK)
- `user_id` UUID (FK → calendar_users, ON DELETE RESTRICT)
- `calendar_id` UUID (FK → user_calendars, ON DELETE RESTRICT)
- `quantity` INTEGER
- `unit_price`, `total_price` DECIMAL(10,2)
- `status` VARCHAR(50) (PENDING, PAID, PROCESSING, SHIPPED, DELIVERED, CANCELLED)
- `shipping_address` JSONB
- `stripe_payment_intent_id`, `stripe_charge_id` VARCHAR(255)
- `notes` TEXT
- `paid_at`, `shipped_at` TIMESTAMPTZ
- `created`, `updated`, `version`
- Indexes: (user_id, created DESC), (status, created DESC), calendar_id, stripe_payment_intent_id

### Context: technological-constraints (from Plan/Architecture)

**Mandated Technologies:**
- PostgreSQL 17+ with PostGIS (required for astronomical calculations)
- MyBatis Migrations for database schema versioning
- UUID primary keys for distributed scalability
- JSONB for flexible schema evolution
- TIMESTAMPTZ for all timestamp fields (timezone-aware)

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `migrations/src/main/resources/scripts/001_initial_schema.sql`
    *   **Summary:** This is the existing initial migration that creates the 4 MVP tables (calendar_users, calendar_templates, user_calendars, calendar_orders). It serves as the foundation and pattern reference.
    *   **Recommendation:** You MUST NOT delete or modify this file. The task description mentions 11 separate migration files, but the ERD shows that the MVP only implements 4 tables with embedded data patterns. You should create **additional migration scripts** for the remaining entities (DelayedJob, PageView, AnalyticsRollup) following the same pattern, OR clarify with the task description that only 4 tables are needed for MVP.
    *   **Pattern to Follow:** The existing migration uses:
        - UUID primary keys with `uuid-ossp` extension
        - Proper TIMESTAMPTZ fields named `created`, `updated` (not `created_at`, `updated_at`)
        - `version` BIGINT for optimistic locking
        - Comprehensive indexes with descriptive names (e.g., `idx_calendar_users_email`)
        - Table and column comments for documentation
        - `//@UNDO` section with proper DROP TABLE order

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarUser.java`
    *   **Summary:** This is the JPA entity implementation for the calendar_users table. It extends `DefaultPanacheEntityWithTimestamps` which handles `id`, `created`, `updated`, and `version` fields automatically.
    *   **Recommendation:** The entity uses field names that **exactly match** the database column names (with snake_case mapped via `@Column` annotation). When creating migration scripts, ensure your column names align with the existing entity field names.
    *   **Key Pattern:** Notice the use of `@NotNull`, `@Size`, `@Email` validation annotations that correspond to database constraints. Your migration scripts should enforce these at the database level with `NOT NULL` and length constraints.

*   **File:** `api/schema.graphql`
    *   **Summary:** The GraphQL schema defines the API contract and includes detailed field descriptions. It shows the current MVP implementation with 4 main types (CalendarUser, CalendarTemplate, UserCalendar, CalendarOrder) and embedded data patterns.
    *   **Recommendation:** Your migration scripts should create tables that support the GraphQL schema types. Note that some GraphQL types like `PdfJob` and `PaymentIntent` are defined but the corresponding database tables may not exist yet (these are for future iterations).

*   **File:** `docs/diagrams/database_erd.puml`
    *   **Summary:** The ERD clearly documents the MVP's 4-table design with embedded data patterns. The legend explicitly states which entities are "Planned but Not Implemented" (DelayedJob, PageView, AnalyticsRollup, separate Event table, separate Payment table, separate OrderItem table).
    *   **Recommendation:** You MUST reconcile the task description (which asks for 11 migration scripts) with the ERD reality (which shows only 4 tables are implemented). The most likely resolution is that you should create the additional migration scripts for the future entities, but they should be designed to be run **after** the initial 4-table schema is in place.

### Implementation Tips & Notes

*   **Tip:** The task description asks for separate migration files for each entity, but the existing codebase has consolidated the 4 MVP tables into `001_initial_schema.sql`. You have two options:
    1. **Option A (Recommended):** Create additional migration scripts `002_add_delayed_jobs_table.sql`, `003_add_page_views_table.sql`, `004_add_analytics_rollups_table.sql` for the future entities mentioned in the task description. This maintains the existing schema while adding planned tables.
    2. **Option B:** Refactor the existing `001_initial_schema.sql` into separate files per table as the task description specifies. However, this would break the existing migration history if it has already been applied to any databases.

*   **Note:** The ERD shows that `user_id` and `template_id` in the `user_calendars` table are **nullable** to support guest sessions (session_id used instead) and calendars created without templates. Your migration scripts must reflect this with appropriate NULL/NOT NULL constraints.

*   **Warning:** The task description mentions creating separate `Event`, `OrderItem`, and `Payment` tables, but the ERD legend explicitly states these are **"currently embedded in JSONB"** for the MVP. Events are embedded in `user_calendars.configuration`, payment details are in `calendar_orders` table directly, and OrderItems are represented by the `quantity` field in `calendar_orders`. If you create these tables, they won't match the current entity model and GraphQL schema.

*   **Critical Decision Required:** The task asks for 11 migration scripts, but only 4 tables exist in the MVP. The remaining 7 tables (Event, OrderItem, Payment, CalendarSession, DelayedJob, PageView, AnalyticsRollup) are either embedded (Event, Payment, OrderItem) or planned for future iterations (DelayedJob, PageView, AnalyticsRollup). You should:
    1. Keep the existing `001_initial_schema.sql` as-is (it's working)
    2. Create **additional** migration scripts for the future tables: `002_add_delayed_jobs_table.sql`, `003_add_page_views_table.sql`, `004_add_analytics_rollups_table.sql`
    3. Document that Event/OrderItem/Payment tables are intentionally not created because the MVP uses embedded data patterns

*   **MyBatis Migrations Pattern:** Each migration file should follow this structure:
    ```sql
    -- //
    -- Description of what this migration does
    -- //

    -- Migration SQL here

    -- //@UNDO

    -- Rollback SQL here
    ```

*   **UUID Extension:** The `000_enable_postgis.sql` script should also enable `uuid-ossp` extension (it's already in `001_initial_schema.sql`, but should be in the base extension script).

*   **Column Naming Convention:** Use snake_case for all database columns (e.g., `user_id`, `created_at`) but note that the existing schema uses `created` and `updated` (not `created_at` and `updated_at`). Follow the existing convention for consistency.

*   **Foreign Key ON DELETE Behaviors:** From the ERD notes:
    - `user_calendars.user_id`: ON DELETE CASCADE (if user deleted, delete their calendars)
    - `user_calendars.template_id`: ON DELETE SET NULL (if template deleted, keep calendar but clear template reference)
    - `calendar_orders.user_id`: ON DELETE RESTRICT (cannot delete user with orders - data protection)
    - `calendar_orders.calendar_id`: ON DELETE RESTRICT (cannot delete calendar with orders - data protection)

*   **Indexing Strategy:** Every foreign key column should have an index. Additionally, create composite indexes for common query patterns:
    - `(user_id, created DESC)` for listing user's items chronologically
    - `(status, created DESC)` for filtering orders by status
    - GIN indexes on all JSONB columns for efficient JSONB querying

