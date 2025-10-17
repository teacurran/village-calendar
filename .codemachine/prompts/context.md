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
    "migrations/src/main/resources/scripts/000_enable_postgis.sql",
    "migrations/src/main/resources/scripts/001_create_users_table.sql",
    "migrations/src/main/resources/scripts/002_create_calendar_sessions_table.sql",
    "migrations/src/main/resources/scripts/003_create_calendar_templates_table.sql",
    "migrations/src/main/resources/scripts/004_create_calendars_table.sql",
    "migrations/src/main/resources/scripts/005_create_events_table.sql",
    "migrations/src/main/resources/scripts/006_create_orders_table.sql",
    "migrations/src/main/resources/scripts/007_create_order_items_table.sql",
    "migrations/src/main/resources/scripts/008_create_payments_table.sql",
    "migrations/src/main/resources/scripts/009_create_delayed_jobs_table.sql",
    "migrations/src/main/resources/scripts/010_create_page_views_table.sql",
    "migrations/src/main/resources/scripts/011_create_analytics_rollups_table.sql"
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

### Context: Database ERD MVP Implementation (from database_erd.puml)

```markdown
The ERD diagram shows the **ACTUAL IMPLEMENTED MVP SCHEMA** with only 4 core entities:

**Core MVP Tables:**
1. `calendar_users` - OAuth authenticated users
2. `calendar_templates` - Reusable design templates
3. `user_calendars` - User-created calendars
4. `calendar_orders` - E-commerce orders

**Key Schema Details:**
- All tables use UUID primary keys (uuid_generate_v4())
- Optimistic locking with version BIGINT field on all tables
- Timestamp fields: created TIMESTAMPTZ, updated TIMESTAMPTZ
- JSONB columns: configuration (templates & calendars), shipping_address (orders)
- Guest sessions: user_id nullable in user_calendars, session_id VARCHAR(255)

**Foreign Key Cascade Behaviors:**
- user_calendars.user_id → calendar_users(id) ON DELETE CASCADE
- user_calendars.template_id → calendar_templates(id) ON DELETE SET NULL
- calendar_orders.user_id → calendar_users(id) ON DELETE RESTRICT
- calendar_orders.calendar_id → user_calendars(id) ON DELETE RESTRICT

**Index Strategy:**
- Primary keys: UUID with default uuid_generate_v4()
- Foreign key indexes: All FK columns indexed
- Composite indexes: (user_id, year DESC), (user_id, created DESC), (is_active, display_order, name)
- Partial index: is_admin (WHERE is_admin = true)
- GIN index: configuration JSONB fields for JSON queries

**IMPORTANT - Future Entities NOT in MVP:**
The ERD legend explicitly states these are "Planned but Not Implemented":
- DelayedJob
- PageView
- AnalyticsRollup
- Separate Event table (events currently embedded in calendar configuration JSONB)
- Separate Payment table (payment details currently embedded in orders table)
- Separate OrderItem table (orders currently have single quantity field, not line items)
```

### Context: calendar_users Table Details

```sql
-- From existing 001_initial_schema.sql (reference implementation)
CREATE TABLE calendar_users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    oauth_provider VARCHAR(50) NOT NULL,
    oauth_subject VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    profile_image_url VARCHAR(500),
    last_login_at TIMESTAMPTZ,
    created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_calendar_users_oauth UNIQUE (oauth_provider, oauth_subject)
);

CREATE INDEX idx_calendar_users_email ON calendar_users(email);
CREATE INDEX idx_calendar_users_last_login ON calendar_users(last_login_at DESC);

-- From 002_add_admin_field.sql
ALTER TABLE calendar_users ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT false;
CREATE INDEX idx_calendar_users_admin ON calendar_users(is_admin) WHERE is_admin = true;

COMMENT ON TABLE calendar_users IS 'OAuth authenticated users for the calendar service';
COMMENT ON COLUMN calendar_users.oauth_provider IS 'OAuth provider (GOOGLE, FACEBOOK)';
COMMENT ON COLUMN calendar_users.oauth_subject IS 'Unique identifier from OAuth provider (sub claim)';
COMMENT ON COLUMN calendar_users.is_admin IS 'Flag indicating whether user has admin privileges';
```

### Context: calendar_templates Table Details

```sql
CREATE TABLE calendar_templates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    thumbnail_url VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT true,
    is_featured BOOLEAN NOT NULL DEFAULT false,
    display_order INTEGER NOT NULL DEFAULT 0,
    configuration JSONB NOT NULL,
    preview_svg TEXT,
    created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_calendar_templates_name ON calendar_templates(name);
CREATE INDEX idx_calendar_templates_active ON calendar_templates(is_active, display_order, name);
CREATE INDEX idx_calendar_templates_featured ON calendar_templates(is_featured, is_active, display_order);
CREATE INDEX idx_calendar_templates_config_gin ON calendar_templates USING GIN(configuration);

COMMENT ON TABLE calendar_templates IS 'Reusable calendar design templates with JSONB configuration';
COMMENT ON COLUMN calendar_templates.configuration IS 'JSONB field for template configuration (colors, layout, features)';
```

### Context: user_calendars Table Details

```sql
CREATE TABLE user_calendars (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID,
    session_id VARCHAR(255),
    template_id UUID,
    name VARCHAR(255) NOT NULL,
    year INTEGER NOT NULL,
    is_public BOOLEAN NOT NULL DEFAULT true,
    configuration JSONB,
    generated_svg TEXT,
    generated_pdf_url VARCHAR(500),
    created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_user_calendars_user FOREIGN KEY (user_id)
        REFERENCES calendar_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_calendars_template FOREIGN KEY (template_id)
        REFERENCES calendar_templates(id) ON DELETE SET NULL
);

CREATE INDEX idx_user_calendars_user ON user_calendars(user_id, year DESC);
CREATE INDEX idx_user_calendars_session ON user_calendars(session_id, updated DESC);
CREATE INDEX idx_user_calendars_template ON user_calendars(template_id);
CREATE INDEX idx_user_calendars_public ON user_calendars(is_public, updated DESC);

COMMENT ON TABLE user_calendars IS 'User-created calendars with customizations, supports both authenticated users and anonymous sessions';
COMMENT ON COLUMN user_calendars.user_id IS 'Reference to authenticated user (nullable for anonymous sessions)';
COMMENT ON COLUMN user_calendars.session_id IS 'Session identifier for anonymous users';
COMMENT ON COLUMN user_calendars.year IS 'Calendar year (e.g., 2025)';
```

### Context: calendar_orders Table Details

```sql
CREATE TABLE calendar_orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    calendar_id UUID NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit_price DECIMAL(10, 2) NOT NULL,
    total_price DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    shipping_address JSONB,
    stripe_payment_intent_id VARCHAR(255),
    stripe_charge_id VARCHAR(255),
    notes TEXT,
    paid_at TIMESTAMPTZ,
    shipped_at TIMESTAMPTZ,
    created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_calendar_orders_user FOREIGN KEY (user_id)
        REFERENCES calendar_users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_calendar_orders_calendar FOREIGN KEY (calendar_id)
        REFERENCES user_calendars(id) ON DELETE RESTRICT
);

CREATE INDEX idx_calendar_orders_user ON calendar_orders(user_id, created DESC);
CREATE INDEX idx_calendar_orders_status ON calendar_orders(status, created DESC);
CREATE INDEX idx_calendar_orders_calendar ON calendar_orders(calendar_id);
CREATE INDEX idx_calendar_orders_stripe_payment ON calendar_orders(stripe_payment_intent_id);

COMMENT ON TABLE calendar_orders IS 'E-commerce orders for printed calendars with Stripe payment integration';
COMMENT ON COLUMN calendar_orders.status IS 'Order status: PENDING, PAID, PROCESSING, SHIPPED, DELIVERED, CANCELLED';
COMMENT ON COLUMN calendar_orders.shipping_address IS 'JSONB field for shipping address details';
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `migrations/src/main/resources/scripts/001_initial_schema.sql`
    *   **Summary:** This is the **EXISTING COMPLETE INITIAL SCHEMA MIGRATION** that creates all 4 MVP tables (calendar_users, calendar_templates, user_calendars, calendar_orders). It also enables the uuid-ossp extension.
    *   **CRITICAL FINDING:** **THE MVP DATABASE SCHEMA IS ALREADY FULLY IMPLEMENTED!** The existing 001_initial_schema.sql contains all 4 core tables with correct columns, indexes, foreign keys, and comments.
    *   **Recommendation:** **DO NOT CREATE DUPLICATE MIGRATIONS FOR THE 4 CORE TABLES.** The task description is inconsistent with the actual codebase state. You should NOT create 001_create_users_table.sql, 003_create_calendar_templates_table.sql, 004_create_calendars_table.sql, or 006_create_orders_table.sql as these tables are already created in 001_initial_schema.sql.

*   **File:** `migrations/src/main/resources/scripts/002_add_admin_field.sql`
    *   **Summary:** Adds the is_admin field to calendar_users table with a partial index. This demonstrates the ALTER TABLE migration pattern.
    *   **Pattern to Follow:** Shows proper MyBatis migration format with `-- //` header, forward DDL, and `-- //@UNDO` rollback section.

*   **File:** `docs/diagrams/database_erd.puml`
    *   **Summary:** The ERD diagram legend **EXPLICITLY STATES** that Event, OrderItem, Payment, DelayedJob, PageView, and AnalyticsRollup are "Future Entities (Planned but Not Implemented)" in the MVP.
    *   **CRITICAL INSIGHT:** The task description requests creating migrations for 11 entity tables, but the ERD shows only 4 tables are implemented. This is a major discrepancy.
    *   **Recommendation:** **CLARIFY WITH USER IMMEDIATELY** - The ERD clearly indicates that 7 entities are "future/planned" and NOT part of MVP implementation. Events are embedded in JSONB, payments are embedded in orders, there are no separate OrderItem, DelayedJob, PageView, or AnalyticsRollup tables.

*   **File:** `src/main/java/villagecompute/calendar/data/models/DelayedJob.java`
    *   **Summary:** JPA entity for delayed_jobs table exists with columns: priority, attempts, queue (ENUM), actorId, lastError, runAt, locked, lockedAt, failedAt, complete, completedAt, completedWithFailure, failureReason. Index: idx_delayed_jobs_queue_run_at(queue, run_at, complete, locked).
    *   **CONFLICT:** The DelayedJob entity exists in code but the ERD explicitly says it's NOT implemented in MVP.
    *   **Recommendation:** Ask user whether DelayedJob table should be created or if it's truly "future" as ERD states.

*   **File:** `src/main/java/villagecompute/calendar/data/models/DefaultPanacheEntityWithTimestamps.java`
    *   **Summary:** Base class for all entities providing: id (UUID with @GeneratedValue), created (Instant), updated (Instant), version (Long).
    *   **Standard Pattern:** ALL tables must include: id UUID, created TIMESTAMPTZ, updated TIMESTAMPTZ, version BIGINT with proper defaults.

*   **File:** `migrations/README.md`
    *   **Summary:** Documents MyBatis migration structure. Scripts must be in src/main/resources/scripts/ with format NNN_description.sql.
    *   **Important:** "Applied Migrations" section lists only 001 and 002, confirming that migrations 003+ have NOT been created yet.

*   **File:** `docs/guides/database-setup.md`
    *   **Summary:** Comprehensive PostgreSQL 17 + PostGIS setup guide. PostGIS extension requires superuser privileges.
    *   **Note:** Docker setup includes docker/postgresql/init/01-enable-postgis.sql which already enables PostGIS. However, a migration script for PostGIS would ensure consistency across all environments.

*   **File:** `docker/postgresql/init/01-enable-postgis.sql`
    *   **Summary:** Docker initialization script that enables PostGIS extension.
    *   **Pattern:** `CREATE EXTENSION IF NOT EXISTS postgis;` - This is the correct syntax for enabling PostGIS.

### Implementation Tips & Notes

*   **CRITICAL DECISION REQUIRED:** There is a fundamental conflict between:
    1. **Task Description:** Requests creating 12 migration scripts (000-011) for 11 entity tables
    2. **ERD Diagram:** Shows only 4 MVP tables, explicitly lists 7 entities as "Future/Not Implemented"
    3. **Existing Code:** Contains 001_initial_schema.sql with all 4 MVP tables already created

*   **Recommended Action:** **IMMEDIATELY ASK USER FOR CLARIFICATION:**

    "The task description requests creating migrations for 11 entity tables, but I've discovered:

    1. The ERD diagram (database_erd.puml) shows only 4 tables in MVP implementation
    2. Migration 001_initial_schema.sql already creates all 4 core tables
    3. The ERD legend explicitly states these are 'Future Entities (Planned but Not Implemented)':
       - Event table (events embedded in calendar JSONB)
       - OrderItem table (single quantity field in orders)
       - Payment table (payment fields embedded in orders)
       - DelayedJob, PageView, AnalyticsRollup tables

    Should I:
    A) Create ONLY 000_enable_postgis.sql (PostGIS migration)?
    B) Create migrations for the 7 'future' entities despite ERD showing them as not implemented?
    C) Do nothing as the MVP schema is already complete in 001_initial_schema.sql?"

*   **MyBatis Migration Format Template:**
    ```sql
    -- //
    -- Description of what this migration does
    -- Include purpose, tables affected, and any important notes
    -- //

    -- Enable extensions (if needed)
    CREATE EXTENSION IF NOT EXISTS extension_name;

    -- Create table
    CREATE TABLE table_name (
        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
        column_name TYPE CONSTRAINTS,
        created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
        version BIGINT NOT NULL DEFAULT 0,
        CONSTRAINT fk_name FOREIGN KEY (col) REFERENCES other_table(id) ON DELETE CASCADE
    );

    -- Create indexes
    CREATE INDEX idx_table_column ON table_name(column);
    CREATE INDEX idx_table_composite ON table_name(col1, col2 DESC);
    CREATE INDEX idx_table_jsonb_gin ON table_name USING GIN(jsonb_column);

    -- Add comments
    COMMENT ON TABLE table_name IS 'Description';
    COMMENT ON COLUMN table_name.column_name IS 'Description';

    -- //@UNDO

    -- Drop in reverse order
    DROP TABLE IF EXISTS table_name;
    DROP EXTENSION IF EXISTS extension_name;
    ```

*   **If Creating 000_enable_postgis.sql:**
    ```sql
    -- //
    -- Enable PostGIS extension for PostgreSQL
    -- Provides geospatial capabilities for future location-based features
    -- IMPORTANT: This requires superuser privileges
    -- //

    -- Enable PostGIS extension
    CREATE EXTENSION IF NOT EXISTS postgis;

    -- Enable PostGIS topology extension (optional for advanced features)
    CREATE EXTENSION IF NOT EXISTS postgis_topology;

    -- Add comment
    COMMENT ON EXTENSION postgis IS 'PostGIS geometry, geography, and raster spatial types and functions';

    -- //@UNDO

    -- Drop PostGIS extensions (only if not used by other schemas)
    DROP EXTENSION IF EXISTS postgis_topology;
    DROP EXTENSION IF EXISTS postgis;
    ```

*   **Naming Conventions (from existing code):**
    - Table names: snake_case (e.g., `calendar_users`, `user_calendars`)
    - Index names: `idx_[table]_[column(s)]` (e.g., `idx_calendar_users_email`)
    - Foreign key constraints: `fk_[child_table]_[parent_table]` (e.g., `fk_user_calendars_user`)
    - Unique constraints: `uk_[table]_[columns]` (e.g., `uk_calendar_users_oauth`)

*   **Data Type Guidelines:**
    - Primary keys: `UUID` with `DEFAULT uuid_generate_v4()`
    - Timestamps: `TIMESTAMPTZ` (timezone-aware)
    - Booleans: `BOOLEAN` with `NOT NULL DEFAULT false/true`
    - Money: `DECIMAL(10, 2)` for currency amounts
    - Text: `VARCHAR(n)` for limited length, `TEXT` for unlimited
    - JSON data: `JSONB` (binary JSON, better performance than JSON)
    - Enums: Store as `VARCHAR(50)` (e.g., order status)

*   **Index Strategy (from existing migrations):**
    - Index ALL foreign key columns
    - Index frequently queried columns (email, last_login_at, status, created)
    - Use composite indexes for common query patterns (user_id + year, user_id + created DESC)
    - Use partial indexes for selective filtering (WHERE is_admin = true)
    - Use GIN indexes for JSONB columns to enable efficient JSON queries

*   **Foreign Key Cascade Rules:**
    - `ON DELETE CASCADE`: Use when child should be deleted with parent (user_calendars.user_id)
    - `ON DELETE SET NULL`: Use when relationship is optional (user_calendars.template_id)
    - `ON DELETE RESTRICT`: Use when deletion should be prevented (calendar_orders.user_id/calendar_id)

### Strategic Recommendation

**DO NOT PROCEED WITHOUT USER CLARIFICATION!**

The task has a fundamental conflict:
- Task requests creating 11 entity migrations
- ERD shows only 4 MVP tables (already created in 001_initial_schema.sql)
- 7 entities are explicitly marked as "Future/Not Implemented"

Recommended workflow:
1. **Ask user for clarification** (see question template above)
2. **If user says "MVP only"**: Create only 000_enable_postgis.sql, mark task complete
3. **If user says "create future entities"**: Create migrations 003-011 for DelayedJob, PageView, etc.
4. **Do NOT create duplicate migrations** for calendar_users, calendar_templates, user_calendars, or calendar_orders

The safest action is to **STOP and ASK** rather than create migrations that conflict with existing schema or ERD documentation.
