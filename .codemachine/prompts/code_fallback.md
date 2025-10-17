# Code Refinement Task

The previous code submission did not pass verification. **NO IMPLEMENTATION WAS PERFORMED** for task I1.T7. The task description contains fundamental conflicts with the existing codebase that must be resolved before any code can be written.

---

## Original Task Description

Write SQL migration scripts for all database tables using MyBatis Migrations format. Create scripts in numbered order: 001_create_users_table.sql, 002_create_calendar_sessions_table.sql, 003_create_calendar_templates_table.sql, 004_create_calendars_table.sql, 005_create_events_table.sql, 006_create_orders_table.sql, 007_create_order_items_table.sql, 008_create_payments_table.sql, 009_create_delayed_jobs_table.sql, 010_create_page_views_table.sql, 011_create_analytics_rollups_table.sql. Each script must include: CREATE TABLE with all columns, primary key constraints, foreign key constraints with ON DELETE CASCADE/SET NULL, indexes (on foreign keys, frequently queried columns), unique constraints, comments on tables/columns. Follow PostgreSQL 17 syntax. Ensure PostGIS extension enabled in script 000_enable_postgis.sql.

---

## Issues Detected

**CRITICAL CONFLICT #1: Duplicate Migration Scripts**

*   **Issue:** The task requests creating migration scripts 001_create_users_table.sql, 003_create_calendar_templates_table.sql, 004_create_calendars_table.sql, and 006_create_orders_table.sql
*   **Problem:** The file `migrations/src/main/resources/scripts/001_initial_schema.sql` **ALREADY EXISTS** and creates all 4 core MVP tables:
    - `calendar_users` (lines 10-25)
    - `calendar_templates` (lines 28-46)
    - `user_calendars` (lines 49-70)
    - `calendar_orders` (lines 73-97)
*   **Impact:** Creating separate migration scripts would either:
    1. Duplicate existing schema creation (causing SQL errors)
    2. Require deleting 001_initial_schema.sql (breaking existing installations)

**CRITICAL CONFLICT #2: ERD vs Task Description Mismatch**

*   **Issue:** The task description requests creating 11 entity tables, but the ERD diagram (`docs/diagrams/database_erd.puml` lines 189-195) **explicitly states** these are "Future Entities (Planned but Not Implemented)":
    - DelayedJob
    - PageView
    - AnalyticsRollup
    - Separate Event table (events are currently embedded in calendar configuration JSONB)
    - Separate Payment table (payment details are currently embedded in orders table)
    - Separate OrderItem table (orders currently have single quantity field, not line items)
    - CalendarSession table (not mentioned in ERD at all)
*   **Problem:** The ERD documentation shows only 4 MVP tables are implemented, contradicting the task request for 11 tables
*   **Impact:** Unclear whether to create migrations for "future" entities or respect the ERD MVP scope

**CRITICAL CONFLICT #3: DelayedJob Entity Ambiguity**

*   **Issue:** A complete JPA entity exists at `src/main/java/villagecompute/calendar/data/models/DelayedJob.java` with full table definition, but the ERD marks it as "Planned but Not Implemented"
*   **Problem:** Code exists for DelayedJob entity but no database migration exists, and ERD says it's not in MVP
*   **Impact:** Unclear whether DelayedJob table should be created in migration 009 or deferred to future iteration

**CRITICAL CONFLICT #4: Acceptance Criteria Inconsistency**

*   **Issue:** Acceptance criteria states "BIGSERIAL primary keys, UUID for session_id and share_token"
*   **Problem:** Existing 001_initial_schema.sql uses **UUID primary keys with uuid_generate_v4()**, not BIGSERIAL
*   **Impact:** The acceptance criteria conflicts with existing implementation pattern

---

## Best Approach to Fix

**You MUST obtain user clarification on the following questions before proceeding:**

### Question 1: Migration Numbering Strategy
The existing migrations are:
- `001_initial_schema.sql` - Creates all 4 MVP tables (users, templates, calendars, orders)
- `002_add_admin_field.sql` - Adds is_admin to calendar_users

**Options:**
- **Option A (Minimal):** Create only `000_enable_postgis.sql` since PostGIS is not currently enabled in migrations (it's only in Docker init script). Do NOT create any other migrations as the 4 MVP tables already exist.
- **Option B (Future Entities):** Create migrations 003-011 for the 7 "future" entities listed in the ERD, understanding they are NOT part of MVP but preparing for future iterations.
- **Option C (Do Nothing):** Mark task complete as-is since MVP schema is already implemented in 001_initial_schema.sql.

**Ask the user:** "Which option should I implement? The MVP database schema already exists. Should I create only the PostGIS migration (Option A), create future entity migrations (Option B), or mark the task complete (Option C)?"

### Question 2: DelayedJob Table Decision
A `DelayedJob.java` entity exists in the codebase but the ERD marks it as "Future/Not Implemented".

**Ask the user:** "Should I create the `delayed_jobs` table migration based on the existing DelayedJob.java entity, or should this table remain deferred as the ERD indicates?"

### Question 3: Primary Key Type Clarification
The acceptance criteria states "BIGSERIAL primary keys" but existing migrations use "UUID with uuid_generate_v4()".

**Ask the user:** "Should new migrations use UUID primary keys (matching existing 001_initial_schema.sql pattern) or BIGSERIAL as stated in acceptance criteria?"

### Question 4: Entity Scope Clarification
The task requests creating tables for entities that the ERD explicitly marks as "not implemented in MVP":
- CalendarSession (not in ERD at all)
- Event (embedded in JSONB, not separate table)
- OrderItem (single quantity field in orders, not separate table)
- Payment (embedded in orders table, not separate table)
- PageView (future entity)
- AnalyticsRollup (future entity)

**Ask the user:** "Should I create separate database tables for entities that are currently embedded in JSONB or marked as future entities, or should I respect the ERD's MVP scope of 4 tables?"

---

## Recommended Implementation Plan (After User Clarification)

**If user chooses Option A (Minimal - PostGIS Only):**

1. Create `migrations/src/main/resources/scripts/000_enable_postgis.sql`:
   ```sql
   -- //
   -- Enable PostGIS extension for PostgreSQL
   -- Provides geospatial capabilities for future location-based features
   -- //

   CREATE EXTENSION IF NOT EXISTS postgis;

   COMMENT ON EXTENSION postgis IS 'PostGIS geometry, geography, and raster spatial types and functions';

   -- //@UNDO

   DROP EXTENSION IF EXISTS postgis;
   ```

2. No other migrations needed since 4 MVP tables already exist in 001_initial_schema.sql
3. Mark task I1.T7 as complete

**If user chooses Option B (Future Entities):**

Create migrations 003-011 for future entities (DelayedJob, PageView, AnalyticsRollup) using the MyBatis migration format template. Base DelayedJob migration on the existing DelayedJob.java entity structure.

**Action Required: STOP and request user clarification before writing any code.**
