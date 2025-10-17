# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I1.T8",
  "iteration_id": "I1",
  "iteration_goal": "Establish project infrastructure, define data models, create architectural artifacts, and implement foundational backend/frontend scaffolding",
  "description": "Create Java JPA entity classes for all database tables using Hibernate ORM with Panache active record pattern. Implement entities: User, CalendarSession, Calendar, Event, CalendarTemplate, Order, OrderItem, Payment, DelayedJob, PageView, AnalyticsRollup. Each entity must extend PanacheEntity (or PanacheEntityBase if custom ID type), include JPA annotations (@Entity, @Table, @Column, @Id, @GeneratedValue, @ManyToOne, @OneToMany, @Enumerated), use proper column types (JSONB mapped to String or custom type), define relationships with correct fetch strategies (LAZY for collections), add validation annotations (@NotNull, @Size, @Email). Create enum classes: OrderStatus, ProductType, OAuthProvider, UserRole. Ensure entity field names match GraphQL schema for consistency.",
  "agent_type_hint": "BackendAgent",
  "inputs": "Database migration scripts from Task I1.T7, GraphQL schema from Task I1.T6, Data model overview from Plan Section 2",
  "target_files": [
    "src/main/java/villagecompute/calendar/model/User.java",
    "src/main/java/villagecompute/calendar/model/CalendarSession.java",
    "src/main/java/villagecompute/calendar/model/Calendar.java",
    "src/main/java/villagecompute/calendar/model/Event.java",
    "src/main/java/villagecompute/calendar/model/CalendarTemplate.java",
    "src/main/java/villagecompute/calendar/model/Order.java",
    "src/main/java/villagecompute/calendar/model/OrderItem.java",
    "src/main/java/villagecompute/calendar/model/Payment.java",
    "src/main/java/villagecompute/calendar/model/DelayedJob.java",
    "src/main/java/villagecompute/calendar/model/PageView.java",
    "src/main/java/villagecompute/calendar/model/AnalyticsRollup.java",
    "src/main/java/villagecompute/calendar/model/enums/OrderStatus.java",
    "src/main/java/villagecompute/calendar/model/enums/ProductType.java",
    "src/main/java/villagecompute/calendar/model/enums/OAuthProvider.java",
    "src/main/java/villagecompute/calendar/model/enums/UserRole.java"
  ],
  "input_files": [
    "migrations/scripts/*.sql",
    "api/graphql-schema.graphql"
  ],
  "deliverables": "All 11 entity classes and 4 enum classes created, Entities compile without errors, Quarkus dev mode starts without Hibernate validation errors, Entities match database schema (field types, constraints, relationships)",
  "acceptance_criteria": "./mvnw compile succeeds with no compilation errors, ./mvnw quarkus:dev starts and Hibernate schema validation passes, Relationships correctly mapped (@ManyToOne, @OneToMany with proper cascade/fetch), JSONB columns handled (e.g., calendar.config as String with JSON serialization), Enums defined for all status/type fields (OrderStatus, ProductType, etc.), Entity classes include basic constructors, getters/setters (or Lombok if preferred)",
  "dependencies": [
    "I1.T7"
  ],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: data-model-overview (from 03_System_Structure_and_Data.md)

```markdown
### 3.6. Data Model Overview & ERD

**Description:**

The data model is optimized for the calendar creation and e-commerce workflows, with careful consideration for session persistence (anonymous users), job processing, and analytics. PostgreSQL's JSONB type is used for flexible calendar metadata (event details, configuration options) while maintaining relational integrity for core entities.

**Key Design Decisions:**

1. **User Identity**: `users` table stores OAuth provider info (`oauth_provider`, `oauth_subject_id`) to support multiple providers per user
2. **Anonymous Sessions**: `calendar_sessions` table tracks guest user calendars, linked to `users` table upon login conversion
3. **Calendar Versioning**: `calendars` table includes `version` field for optimistic locking, future support for edit history
4. **Order Status**: `orders.status` enum (PENDING, PAID, IN_PRODUCTION, SHIPPED, DELIVERED, CANCELLED, REFUNDED) drives workflow state machine
5. **Job Queue**: `delayed_jobs` table with `locked_at`, `locked_by`, `attempts`, `last_error` supports distributed worker coordination
6. **Templates**: `calendar_templates` is separate from `calendars` to enable admin-curated vs user-created distinction
7. **Analytics**: `page_views`, `analytics_rollups` tables support basic analytics without external service dependency (Phase 1)

**Key Entities:**

- **User**: Registered user account with OAuth authentication
- **CalendarSession**: Anonymous user session data (pre-authentication)
- **Calendar**: User's saved calendar with events and configuration
- **CalendarTemplate**: Admin-created template calendars
- **Event**: Custom event on a calendar (date, text, emoji)
- **Order**: E-commerce order for printed calendar
- **OrderItem**: Line items in an order (supports future multi-calendar orders)
- **Payment**: Stripe payment record linked to order
- **DelayedJob**: Asynchronous job queue entry
- **PageView**: Analytics event for page visits
- **AnalyticsRollup**: Aggregated analytics (daily/weekly/monthly)

**Database Indexes Strategy:**

- **Primary Keys**: All tables use `bigserial` auto-incrementing primary keys for simplicity and performance
- **Foreign Keys**: Enforce referential integrity with cascading deletes where appropriate (e.g., `Calendar.user_id` ON DELETE CASCADE)
- **Lookup Indexes**: Composite indexes on frequently queried columns (e.g., `(user_id, created_at)` for calendar lists)
- **Unique Constraints**: `users.email`, `orders.order_number`, `calendar.share_token` for business logic enforcement
- **JSONB Indexes**: GIN indexes on `calendar.config` for filtering by holiday sets, astronomy options
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `src/main/java/villagecompute/calendar/data/models/DefaultPanacheEntityWithTimestamps.java`
    *   **Summary:** This is the abstract base class that ALL entity models MUST extend. It provides UUID primary key, created/updated timestamps (via Hibernate annotations), and optimistic locking version field.
    *   **Recommendation:** You MUST extend this class for all entities. The existing entities (CalendarUser, UserCalendar, etc.) already follow this pattern.
    *   **Key Pattern:**
        ```java
        @MappedSuperclass
        public abstract class DefaultPanacheEntityWithTimestamps extends PanacheEntityBase {
            @Id @GeneratedValue public UUID id;
            @CreationTimestamp @Column(nullable = false, updatable = false) public Instant created;
            @UpdateTimestamp public Instant updated;
            @Version public Long version;
        }
        ```

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarUser.java`
    *   **Summary:** EXISTING User entity implementation. This is already complete and demonstrates the correct pattern.
    *   **Status:** ✅ **ALREADY EXISTS** - Do NOT recreate
    *   **Key Patterns to Follow:**
        - Extends DefaultPanacheEntityWithTimestamps
        - Uses public fields (no getters/setters) - this is the Panache/Quarkus pattern
        - Includes validation annotations (@NotNull, @Email, @Size)
        - Has bidirectional relationships (@OneToMany with mappedBy)
        - Implements static finder methods (ActiveRecord pattern)

*   **File:** `src/main/java/villagecompute/calendar/data/models/UserCalendar.java`
    *   **Summary:** EXISTING Calendar entity (named UserCalendar to avoid conflicts).
    *   **Status:** ✅ **ALREADY EXISTS** - Do NOT recreate
    *   **JSONB Mapping Pattern (CRITICAL - Use this exact pattern):**
        ```java
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb", nullable = true)
        @io.smallrye.graphql.api.AdaptWith(villagecompute.calendar.api.graphql.scalars.JsonNodeAdapter.class)
        public JsonNode configuration;
        ```

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarTemplate.java`
    *   **Summary:** EXISTING Template entity.
    *   **Status:** ✅ **ALREADY EXISTS** - Do NOT recreate

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarOrder.java`
    *   **Summary:** EXISTING Order entity.
    *   **Status:** ✅ **ALREADY EXISTS** - Do NOT recreate
    *   **Note:** Embeds payment details (stripePaymentIntentId, stripeChargeId) directly rather than separate Payment entity

*   **File:** `src/main/java/villagecompute/calendar/data/models/DelayedJob.java`
    *   **Summary:** EXISTING job queue entity.
    *   **Status:** ✅ **ALREADY EXISTS** - Do NOT recreate
    *   **Key Pattern:** Uses @NamedQuery for complex queries

*   **File:** `src/main/java/villagecompute/calendar/data/models/DelayedJobQueue.java`
    *   **Summary:** EXISTING enum for job queue types.
    *   **Status:** ✅ **ALREADY EXISTS** - Use as reference for creating other enums

*   **File:** `migrations/src/main/resources/scripts/001_initial_schema.sql`
    *   **Summary:** Migration defining calendar_users, calendar_templates, user_calendars, calendar_orders tables.
    *   **Recommendation:** Entity field mappings MUST match this schema exactly (column names, types, constraints).

*   **File:** `migrations/src/main/resources/scripts/004_create_page_views_table.sql`
    *   **Summary:** PageView table schema for analytics.
    *   **Status:** ⚠️ **ENTITY NEEDS TO BE CREATED**

*   **File:** `migrations/src/main/resources/scripts/005_create_analytics_rollups_table.sql`
    *   **Summary:** AnalyticsRollup table schema.
    *   **Status:** ⚠️ **ENTITY NEEDS TO BE CREATED**

*   **File:** `api/schema.graphql`
    *   **Summary:** Complete GraphQL schema defining all types and enums.
    *   **Recommendation:** Entity field names MUST match GraphQL type field names for consistency.
    *   **Enums Defined:**
        - OrderStatus: CANCELLED, DELIVERED, PAID, PENDING, PROCESSING, REFUNDED, SHIPPED
        - CalendarStatus: DRAFT, FAILED, GENERATING, READY
        - OAuthProvider: FACEBOOK, GOOGLE
        - ProductType: DESK_CALENDAR, POSTER, WALL_CALENDAR

### Implementation Tips & Notes

*   **🚨 CRITICAL REALITY CHECK:** The task description expects 11 entities, but only some actually exist based on the MVP implementation:

    **✅ ALREADY IMPLEMENTED (Do NOT recreate):**
    1. CalendarUser (the "User" entity)
    2. UserCalendar (the "Calendar" entity)
    3. CalendarTemplate
    4. CalendarOrder (the "Order" entity)
    5. DelayedJob

    **⚠️ NEED TO CREATE:**
    6. PageView (migration 004 exists)
    7. AnalyticsRollup (migration 005 exists)

    **❌ DO NOT EXIST IN MVP (intentionally embedded):**
    8. CalendarSession - Guest sessions handled via sessionId field in UserCalendar
    9. Event - Events stored in JSONB configuration field, not separate table
    10. OrderItem - Represented by quantity field in CalendarOrder
    11. Payment - Payment details embedded in CalendarOrder (stripePaymentIntentId fields)

*   **PACKAGE STRUCTURE:** Entities are in `villagecompute.calendar.data.models` NOT `villagecompute.calendar.model` as specified in the task. Use the existing package structure.

*   **ENUMS TO CREATE:**
    - OrderStatus - Based on GraphQL schema values
    - ProductType - Based on GraphQL schema
    - OAuthProvider - Currently VARCHAR in DB, should be enum
    - CalendarStatus - For PDF generation status tracking

    (Note: UserRole may not be needed as is_admin boolean already exists)

*   **JSON Mapping Pattern - USE THIS EXACTLY:**
    ```java
    import com.fasterxml.jackson.databind.JsonNode;
    import org.hibernate.annotations.JdbcTypeCode;
    import org.hibernate.type.SqlTypes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @io.smallrye.graphql.api.AdaptWith(villagecompute.calendar.api.graphql.scalars.JsonNodeAdapter.class)
    public JsonNode fieldName;
    ```

*   **Relationship Mapping Pattern:**
    ```java
    // Many-to-One (FK side)
    @ManyToOne
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_table_user"))
    public CalendarUser user;

    // One-to-Many (owning side)
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<UserCalendar> calendars;
    ```

*   **Index Annotations:**
    ```java
    @Table(
        name = "table_name",
        indexes = {
            @Index(name = "idx_table_column", columnList = "column_name"),
            @Index(name = "idx_table_composite", columnList = "col1, col2 DESC")
        }
    )
    ```

*   **Validation Annotations:**
    ```java
    @NotNull
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    public String fieldName;
    ```

*   **Active Record Pattern - Static Finder Methods:**
    ```java
    public static Optional<Entity> findByField(String value) {
        return find("fieldName", value).firstResultOptional();
    }

    public static PanacheQuery<Entity> findByComplexQuery(param) {
        return find("field1 = ?1 AND field2 = ?2 ORDER BY field3 DESC", p1, p2);
    }
    ```

### Critical Warnings

*   **WARNING:** Several entities from the task description DO NOT HAVE database tables in the current migration scripts:
    - CalendarSession - Session handling is via sessionId column in user_calendars
    - Event - Events are embedded in JSONB configuration
    - OrderItem - Simplified to quantity field in calendar_orders
    - Payment - Payment fields embedded in calendar_orders

*   **WARNING:** The GraphQL schema shows these entities as separate types, but the database implementation embeds them. The JPA entities should match the DATABASE structure, not the GraphQL schema structure.

*   **ACTION REQUIRED FOR THIS TASK:**
    1. ✅ **VERIFY** existing entities (CalendarUser, UserCalendar, CalendarTemplate, CalendarOrder, DelayedJob) are complete
    2. ✅ **CREATE** PageView entity based on migration 004
    3. ✅ **CREATE** AnalyticsRollup entity based on migration 005
    4. ✅ **CREATE** enums: OrderStatus, ProductType, OAuthProvider, CalendarStatus
    5. ❌ **DO NOT CREATE** entities without corresponding database tables (CalendarSession, Event, OrderItem, Payment)

*   **ACCEPTANCE CRITERIA UPDATE:** Given that only 7 entities have actual database tables (5 existing + 2 new), the acceptance criteria should be:
    - All existing entities verified and conform to standards
    - PageView and AnalyticsRollup entities created
    - 4 enum classes created
    - All entities compile and Quarkus starts without errors
    - No Hibernate schema validation errors

### Final Recommendation

**YOUR PRIMARY TASKS:**

1. **Create Missing Entities:**
   - PageView (based on migration 004)
   - AnalyticsRollup (based on migration 005)

2. **Create Missing Enums (in `data/models/enums/` package):**
   - OrderStatus
   - ProductType
   - OAuthProvider
   - CalendarStatus

3. **Verify Existing Entities:**
   - Ensure CalendarUser, UserCalendar, CalendarTemplate, CalendarOrder, DelayedJob match their migrations
   - Add any missing validation annotations or finder methods

4. **Test:**
   - Run `./mvnw compile` to ensure no compilation errors
   - Run `./mvnw quarkus:dev` to verify Hibernate schema validation passes
   - Check that all entities properly extend DefaultPanacheEntityWithTimestamps
