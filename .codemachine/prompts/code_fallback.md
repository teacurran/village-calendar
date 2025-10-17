# Code Refinement Task

The previous code submission did not pass verification. The generated code only partially addresses the task requirements. You must fix the following issues and resubmit your work.

---

## Original Task Description

Write SQL migration scripts for all database tables using MyBatis Migrations format. Create scripts in numbered order: 001_create_users_table.sql, 002_create_calendar_sessions_table.sql, 003_create_calendar_templates_table.sql, 004_create_calendars_table.sql, 005_create_events_table.sql, 006_create_orders_table.sql, 007_create_order_items_table.sql, 008_create_payments_table.sql, 009_create_delayed_jobs_table.sql, 010_create_page_views_table.sql, 011_create_analytics_rollups_table.sql. Each script must include: CREATE TABLE with all columns, primary key constraints, foreign key constraints with ON DELETE CASCADE/SET NULL, indexes (on foreign keys, frequently queried columns), unique constraints, comments on tables/columns. Follow PostgreSQL 17 syntax. Ensure PostGIS extension enabled in script 000_enable_postgis.sql.

---

## Issues Detected

**CRITICAL ISSUE #1: Incomplete Deliverables**

*   **Problem:** The task explicitly requires **12 migration scripts (000-011)** to be created, but only 2 files were delivered:
    - `000_enable_postgis.sql` (new - ✅ correct)
    - `001_initial_schema.sql` (modified existing file - this creates 4 tables together, NOT separate files as requested)

*   **Missing:** The following 10 migration scripts were NOT created:
    - `migrations/src/main/resources/scripts/001_create_users_table.sql`
    - `migrations/src/main/resources/scripts/002_create_calendar_sessions_table.sql`
    - `migrations/src/main/resources/scripts/003_create_calendar_templates_table.sql`
    - `migrations/src/main/resources/scripts/004_create_calendars_table.sql`
    - `migrations/src/main/resources/scripts/005_create_events_table.sql`
    - `migrations/src/main/resources/scripts/006_create_orders_table.sql`
    - `migrations/src/main/resources/scripts/007_create_order_items_table.sql`
    - `migrations/src/main/resources/scripts/008_create_payments_table.sql`
    - `migrations/src/main/resources/scripts/009_create_delayed_jobs_table.sql`
    - `migrations/src/main/resources/scripts/010_create_page_views_table.sql`
    - `migrations/src/main/resources/scripts/011_create_analytics_rollups_table.sql`

**CRITICAL ISSUE #2: Conflict with Existing Migration Files**

*   **Problem:** The existing migration history shows:
    - `001_initial_schema.sql` already exists and creates 4 MVP tables (calendar_users, calendar_templates, user_calendars, calendar_orders)
    - `002_add_admin_field.sql` already exists and adds is_admin field to calendar_users

*   **Conflict:** The task requests creating `001_create_users_table.sql`, `003_create_calendar_templates_table.sql`, `004_create_calendars_table.sql`, `006_create_orders_table.sql` which would conflict with the existing `001_initial_schema.sql` that already creates these 4 tables.

**CRITICAL ISSUE #3: ERD vs Task Description Mismatch**

*   **Problem:** The ERD at `docs/diagrams/database_erd.puml` (lines 174-195) explicitly states that ONLY 4 MVP tables are implemented:
    - calendar_users
    - calendar_templates
    - user_calendars
    - calendar_orders

*   **ERD Notes:** The following entities are marked as "FUTURE ENTITIES (PLANNED BUT NOT IMPLEMENTED)" in the ERD:
    - DelayedJob - Async job queue
    - PageView - Analytics tracking
    - AnalyticsRollup - Aggregated analytics
    - Separate Event table (events currently embedded in `user_calendars.configuration` JSONB)
    - Separate Payment table (payment details currently embedded in `calendar_orders` table via Stripe fields)
    - Separate OrderItem table (orders currently have single `quantity` field, not separate line items)
    - CalendarSession table (guest sessions handled via `session_id` column in `user_calendars`, not separate table)

*   **Impact:** The task asks for migrations for tables that the ERD explicitly states are NOT implemented in MVP.

---

## Best Approach to Fix

**You must reconcile the task requirements with the existing codebase reality. Here are two possible approaches:**

### Option A: Create Separate Migration Files for Each Entity (Following Task Literally)

If the task requirement is to create 11 separate migration files regardless of the ERD state, you must:

1. **RENAME the existing `001_initial_schema.sql`** to avoid conflicts:
   - Move `migrations/src/main/resources/scripts/001_initial_schema.sql` → `migrations/src/main/resources/scripts/001_initial_schema_old.sql.bak`
   - Update `migrations/src/main/resources/scripts/002_add_admin_field.sql` to reference the new migration numbering

2. **Create individual migration files** for each of the 4 MVP tables:
   - `001_create_users_table.sql` - Creates `calendar_users` table (extract from existing 001_initial_schema.sql lines 8-23)
   - `003_create_calendar_templates_table.sql` - Creates `calendar_templates` table (extract from existing 001_initial_schema.sql lines 26-44)
   - `004_create_calendars_table.sql` - Creates `user_calendars` table (extract from existing 001_initial_schema.sql lines 47-68)
   - `006_create_orders_table.sql` - Creates `calendar_orders` table (extract from existing 001_initial_schema.sql lines 71-95)

3. **Create placeholder/future migration files** for the 7 entities NOT in MVP:
   - `002_create_calendar_sessions_table.sql` - Add comment "-- FUTURE: Not implemented in MVP, sessions handled via user_calendars.session_id"
   - `005_create_events_table.sql` - Add comment "-- FUTURE: Not implemented in MVP, events embedded in user_calendars.configuration JSONB"
   - `007_create_order_items_table.sql` - Add comment "-- FUTURE: Not implemented in MVP, orders use single quantity field"
   - `008_create_payments_table.sql` - Add comment "-- FUTURE: Not implemented in MVP, payment data embedded in calendar_orders"
   - `009_create_delayed_jobs_table.sql` - Create table based on `DelayedJob.java` entity
   - `010_create_page_views_table.sql` - Add comment "-- FUTURE: Analytics not implemented in MVP"
   - `011_create_analytics_rollups_table.sql` - Add comment "-- FUTURE: Analytics not implemented in MVP"

4. **Update the migration numbering** to account for the new sequence:
   - The existing `002_add_admin_field.sql` would need to be renumbered (perhaps to `012_add_admin_field.sql` to come after all 11 entity migrations)

### Option B: Minimal Approach (Keeping Existing Schema, Adding Only Future Tables)

If the goal is to preserve the existing working migration history and only add the missing tables:

1. **KEEP the existing files unchanged**:
   - `000_enable_postgis.sql` (already created - ✅)
   - `001_initial_schema.sql` (creates 4 MVP tables - KEEP AS IS)
   - `002_add_admin_field.sql` (adds is_admin - KEEP AS IS)

2. **Create ONLY the future entity migrations** numbered 003-006:
   - `003_create_delayed_jobs_table.sql` - Create delayed_jobs table based on `DelayedJob.java` entity (this entity exists in code)
   - `004_create_page_views_table.sql` - Create page_views table with fields: id, session_id, user_id, path, referrer, user_agent, created
   - `005_create_analytics_rollups_table.sql` - Create analytics_rollups table with fields: id, metric_name, dimension_key, dimension_value, value, period_start, period_end, created
   - `006_add_future_placeholders.sql` - Add comments documenting the embedded data patterns (Event, Payment, OrderItem, CalendarSession)

3. **Document the architectural decision** to use embedded data patterns for Event/Payment/OrderItem/CalendarSession in the migration comments.

---

## Recommended Solution: Option B (Minimal, Preserves Existing History)

**Option B is strongly recommended** because:
1. The existing `001_initial_schema.sql` and `002_add_admin_field.sql` have already been applied (likely to development databases)
2. Renumbering/renaming existing migrations breaks MyBatis migration history tracking
3. The ERD clearly documents that only 4 tables are in MVP, with 7 entities planned for future
4. This approach adds the missing tables without disrupting existing schema

**Implementation Steps for Option B:**

1. **Create `migrations/src/main/resources/scripts/003_create_delayed_jobs_table.sql`**:
   - Extract table structure from `src/main/java/villagecompute/calendar/data/models/DelayedJob.java`
   - Include columns: id (UUID PK), priority (INTEGER), attempts (INTEGER), queue (VARCHAR - enum), actor_id (UUID nullable), last_error (TEXT), run_at (TIMESTAMPTZ), locked (BOOLEAN), locked_at (TIMESTAMPTZ), failed_at (TIMESTAMPTZ), complete (BOOLEAN), completed_at (TIMESTAMPTZ), completed_with_failure (BOOLEAN), failure_reason (TEXT), created, updated, version
   - Create index: `idx_delayed_jobs_queue_run_at ON delayed_jobs(queue, run_at, complete, locked)`
   - Add comments explaining the async job queue pattern

2. **Create `migrations/src/main/resources/scripts/004_create_page_views_table.sql`**:
   - Fields: id (UUID PK), session_id (VARCHAR(255) NOT NULL), user_id (UUID nullable FK → calendar_users), path (VARCHAR(500) NOT NULL), referrer (VARCHAR(500)), user_agent (VARCHAR(1000)), created (TIMESTAMPTZ), updated (TIMESTAMPTZ), version (BIGINT)
   - Indexes: (session_id, created DESC), (user_id, created DESC), (path, created DESC)
   - FK: user_id → calendar_users(id) ON DELETE SET NULL

3. **Create `migrations/src/main/resources/scripts/005_create_analytics_rollups_table.sql`**:
   - Fields: id (UUID PK), metric_name (VARCHAR(255) NOT NULL), dimension_key (VARCHAR(255)), dimension_value (VARCHAR(500)), value (DECIMAL(15,2) NOT NULL), period_start (TIMESTAMPTZ NOT NULL), period_end (TIMESTAMPTZ NOT NULL), created (TIMESTAMPTZ), updated (TIMESTAMPTZ), version (BIGINT)
   - Indexes: (metric_name, period_start, dimension_key), (period_start, period_end)
   - Add CHECK constraint: period_end > period_start

4. **Add documentation comments** in each migration file explaining:
   - Why Event/Payment/OrderItem/CalendarSession are NOT separate tables (embedded in JSONB for MVP flexibility)
   - Reference to the ERD diagram for architectural decisions

---

## Acceptance Criteria for Option B Solution

- `000_enable_postgis.sql` exists with uuid-ossp and PostGIS extensions (✅ already done)
- `001_initial_schema.sql` remains unchanged (creates 4 MVP tables)
- `002_add_admin_field.sql` remains unchanged (adds is_admin field)
- `003_create_delayed_jobs_table.sql` created with full table definition, indexes, comments
- `004_create_page_views_table.sql` created with full table definition, indexes, comments
- `005_create_analytics_rollups_table.sql` created with full table definition, indexes, comments
- All migration scripts follow MyBatis format with `-- //` header and `-- //@UNDO` rollback section
- SQL syntax is PostgreSQL 17 compatible
- Foreign key constraints use appropriate ON DELETE behaviors (CASCADE, SET NULL, RESTRICT)
- All timestamp fields use TIMESTAMPTZ (timezone-aware)
- All primary keys use UUID with uuid_generate_v4() default
- All tables include created, updated, version fields for optimistic locking

**Final Migration Order:**
```
000_enable_postgis.sql           → Enables extensions
001_initial_schema.sql           → Creates 4 MVP tables (existing)
002_add_admin_field.sql          → Adds is_admin to calendar_users (existing)
003_create_delayed_jobs_table.sql  → Creates delayed_jobs table (NEW)
004_create_page_views_table.sql    → Creates page_views table (NEW)
005_create_analytics_rollups_table.sql → Creates analytics_rollups table (NEW)
```

This solution delivers the core task requirement (database migrations for all planned tables) while respecting the existing migration history and ERD architectural decisions.
