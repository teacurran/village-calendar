e# Village Calendar Database Migrations

This module contains MyBatis Migrations for the Village Calendar database schema.

## Directory Structure

```
migrations/
├── pom.xml
├── src/main/resources/
│   ├── environments/
│   │   ├── development.properties
│   │   ├── beta.properties
│   │   ├── testing.properties
│   │   └── production.properties
│   └── scripts/
│       ├── 001_initial_schema.sql
│       ├── 002_add_admin_field.sql
│       └── ... (future migrations)
```

## Running Migrations

All commands should be run from the `migrations` directory:

```bash
cd migrations
```

### Check Migration Status

```bash
mvn org.mybatis.maven:migrations-maven-plugin:status -Dmigration.env=development
```

### Apply Pending Migrations

```bash
mvn org.mybatis.maven:migrations-maven-plugin:up -Dmigration.env=development
```

### Rollback Last Migration

```bash
mvn org.mybatis.maven:migrations-maven-plugin:down -Dmigration.env=development
```

## Creating New Migrations

1. Create a new SQL file in `src/main/resources/scripts/` with the format:
   - `00X_description.sql` (e.g., `003_add_user_preferences.sql`)

2. Structure your migration file:

```sql
-- //
-- Description of the migration
-- //

-- Your SQL DDL statements here
ALTER TABLE ...;

-- //@UNDO

-- Rollback SQL statements here
ALTER TABLE ...;
```

## Environment Configuration

- **development**: Points to `localhost:5532` (Docker container port mapping)
- **beta**: Points to beta environment database
- **production**: Points to production database

## Applied Migrations

1. `001_initial_schema.sql` - Initial database schema (calendar_users, calendar_templates, user_calendars, calendar_orders)
2. `002_add_admin_field.sql` - Added is_admin field to calendar_users table for bootstrap functionality

## Notes

- Migration scripts must be in the `scripts/` directory (not `migrations/`)
- Always test migrations in development before applying to beta/production
- Each migration should include both the forward migration and the `//@UNDO` rollback
