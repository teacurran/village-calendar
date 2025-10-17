# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Replace stub implementations in CalendarResolver with real service calls. Implement queries: calendar(id) (fetch calendar with events, authorize user), calendars(userId, year) (list user's calendars with pagination). Implement mutations: createCalendar(input) (validate input, call CalendarService), updateCalendar(id, input) (authorize, update), deleteCalendar(id) (authorize, soft/hard delete). Inject SecurityIdentity for user context. **Implement DataLoader pattern to prevent N+1 queries when fetching related entities (e.g., calendar with user and events).** Add error handling (map service exceptions to GraphQL errors). Write integration tests for all resolver methods.

---

## Issues Detected

### **CRITICAL ISSUE #1: DataLoaders are injected but NEVER USED**

You created three DataLoader classes (`UserDataLoader`, `TemplateDataLoader`, `EventDataLoader`) and injected them into `CalendarGraphQL.java` (lines 92-105), but they are **COMPLETELY UNUSED**.

**Evidence:**
- Lines 92-105 inject the DataLoaders
- NO methods in CalendarGraphQL.java call these DataLoaders
- NO field resolvers exist that use `@Source` annotation to leverage DataLoaders
- The comment at lines 625-647 explicitly states DataLoaders are "provided for future enhancement"

**What's missing:**
The task requires **DataLoader pattern implementation**, which means:
1. Field resolvers using `@Source` annotation for UserCalendar type
2. Field resolvers must use injected DataLoader instances to batch-load related entities
3. Resolvers should return `CompletionStage<T>` for async batch loading

**Current implementation uses Hibernate @BatchSize instead of DataLoader pattern:**
- UserCalendar.java line 69: `@BatchSize(size = 10)` on orders
- UserCalendar.java line 73: `@BatchSize(size = 10)` on events
- UserCalendar.java lines 31, 56: `FetchType.EAGER` on user and template

This is NOT the same as the DataLoader pattern required by the task.

### **ISSUE #2: Unused imports in CalendarGraphQL.java**

The following imports are declared but never used:
- Line 10: `org.dataloader.DataLoader` - imported but DataLoader is never used
- Line 17: `org.eclipse.microprofile.graphql.Source` - imported but no field resolvers exist
- Line 26: `villagecompute.calendar.data.models.CalendarTemplate` - unused
- Line 28: `villagecompute.calendar.data.models.Event` - unused
- Line 37: `java.util.concurrent.CompletionStage` - unused

These must be removed OR properly used (if you implement DataLoader field resolvers, Source and CompletionStage will be used).

### **ISSUE #3: SmallRye GraphQL DataLoader Integration**

**CRITICAL UNDERSTANDING:** SmallRye GraphQL (Quarkus's GraphQL implementation) does NOT have built-in DataLoader support like graphql-java. The DataLoader classes you created follow the graphql-java pattern, but they need to be integrated differently.

**Two possible approaches:**

#### **Approach A: Use SmallRye GraphQL Batching (Recommended for Quarkus)**
SmallRye GraphQL provides `@Batched` annotation for batch loading:

```java
// Field resolver for user on UserCalendar
@Query
@Batched
public List<CalendarUser> user(@Source List<UserCalendar> calendars) {
    // Extract unique user IDs
    List<UUID> userIds = calendars.stream()
        .map(c -> c.user != null ? c.user.id : null)
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.toList());

    // Batch load users
    List<CalendarUser> users = userRepository.findByIds(userIds);
    Map<UUID, CalendarUser> userMap = users.stream()
        .collect(Collectors.toMap(u -> u.id, u -> u));

    // Return users in same order as input calendars
    return calendars.stream()
        .map(c -> c.user != null ? userMap.get(c.user.id) : null)
        .collect(Collectors.toList());
}
```

#### **Approach B: Manual DataLoader Integration**
If you want to use the DataLoader classes you created, you need to:
1. Create a `DataLoaderRegistry` producer
2. Register DataLoaders in the registry
3. Access DataLoaders via GraphQL context in field resolvers

**However, Approach A is simpler and is the standard Quarkus/SmallRye GraphQL pattern.**

### **ISSUE #4: Missing CalendarUserRepository.findByIds() method**

The `UserDataLoader.java` line 61 calls:
```java
List<CalendarUser> users = userRepository.findByIds(userIds);
```

But I cannot verify if `CalendarUserRepository` has a `findByIds(List<UUID>)` method. If this method doesn't exist, the DataLoader will fail at runtime.

You MUST ensure:
1. `CalendarUserRepository` has `findByIds(List<UUID> ids)` method
2. Similar methods exist for `CalendarTemplateRepository` and any other repositories used by DataLoaders

---

## Best Approach to Fix

### **Step 1: Decide on DataLoader Implementation Strategy**

**RECOMMENDED: Use SmallRye GraphQL's `@Batched` annotation**

This is the native Quarkus approach and doesn't require external DataLoader libraries. You should:

1. **Remove the graphql-java DataLoader classes** (UserDataLoader.java, TemplateDataLoader.java, EventDataLoader.java) since they're not compatible with SmallRye GraphQL's architecture
2. **Remove the DataLoader injections** from CalendarGraphQL.java (lines 92-105)
3. **Implement batched field resolvers** using `@Batched` annotation

### **Step 2: Implement Batched Field Resolvers**

Add these field resolvers to `CalendarGraphQL.java`:

```java
/**
 * Batched field resolver for UserCalendar.user relationship.
 * Prevents N+1 queries by batch-loading users for multiple calendars.
 */
@Query
@Name("user")
@Description("Get the user who owns this calendar")
@Batched
public List<CalendarUser> batchLoadUsers(
    @Source final List<UserCalendar> calendars
) {
    LOG.debugf("Batch loading users for %d calendars", calendars.size());

    // Extract unique user IDs (handle nulls for guest calendars)
    List<UUID> userIds = calendars.stream()
        .map(c -> c.user != null ? c.user.id : null)
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.toList());

    if (userIds.isEmpty()) {
        return calendars.stream()
            .map(c -> null)
            .collect(Collectors.toList());
    }

    // Batch load all users in a single query
    List<CalendarUser> users = CalendarUser
        .<CalendarUser>find("id IN ?1", userIds)
        .list();

    // Create lookup map
    Map<UUID, CalendarUser> userMap = users.stream()
        .collect(Collectors.toMap(u -> u.id, u -> u));

    // Return users in same order as input calendars
    return calendars.stream()
        .map(c -> c.user != null ? userMap.get(c.user.id) : null)
        .collect(Collectors.toList());
}

/**
 * Batched field resolver for UserCalendar.template relationship.
 * Prevents N+1 queries by batch-loading templates for multiple calendars.
 */
@Query
@Name("template")
@Description("Get the template used by this calendar")
@Batched
public List<CalendarTemplate> batchLoadTemplates(
    @Source final List<UserCalendar> calendars
) {
    LOG.debugf("Batch loading templates for %d calendars", calendars.size());

    // Extract unique template IDs
    List<UUID> templateIds = calendars.stream()
        .map(c -> c.template != null ? c.template.id : null)
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.toList());

    if (templateIds.isEmpty()) {
        return calendars.stream()
            .map(c -> null)
            .collect(Collectors.toList());
    }

    // Batch load all templates in a single query
    List<CalendarTemplate> templates = CalendarTemplate
        .<CalendarTemplate>find("id IN ?1", templateIds)
        .list();

    // Create lookup map
    Map<UUID, CalendarTemplate> templateMap = templates.stream()
        .collect(Collectors.toMap(t -> t.id, t -> t));

    // Return templates in same order as input calendars
    return calendars.stream()
        .map(c -> c.template != null ? templateMap.get(c.template.id) : null)
        .collect(Collectors.toList());
}
```

**Key points:**
- `@Batched` tells SmallRye GraphQL to batch calls to this method
- `@Source` indicates this is a field resolver for the source type
- Input is `List<UserCalendar>` instead of single calendar
- Must return `List<T>` in the SAME ORDER as input calendars
- Use `IN` query to fetch all related entities in one database query

### **Step 3: Remove unused DataLoader classes and fix imports**

1. **Delete these files:**
   - `src/main/java/villagecompute/calendar/api/graphql/dataloader/UserDataLoader.java`
   - `src/main/java/villagecompute/calendar/api/graphql/dataloader/TemplateDataLoader.java`
   - `src/main/java/villagecompute/calendar/api/graphql/dataloader/EventDataLoader.java`

2. **Update CalendarGraphQL.java imports:**
   - Remove: `import org.dataloader.DataLoader;` (line 10)
   - Keep: `import org.eclipse.microprofile.graphql.Source;` (needed for field resolvers)
   - Remove: `import villagecompute.calendar.data.models.CalendarTemplate;` (line 26) - unless you use it in field resolver
   - Remove: `import villagecompute.calendar.data.models.Event;` (line 28)
   - Remove: `import java.util.concurrent.CompletionStage;` (line 37)
   - Add: `import java.util.Objects;` (needed for null filtering)
   - Add: `import java.util.stream.Collectors;` (if not already present)
   - Add: `import java.util.Map;` (if not already present)

3. **Remove DataLoader injections from CalendarGraphQL.java:**
   - Remove lines 90-105 (DataLoader field injections)

4. **Update the comment at the end of CalendarGraphQL.java:**
   - Remove lines 625-647 (the note about @BatchSize and future DataLoader enhancement)
   - Replace with: "Field resolvers use SmallRye GraphQL's @Batched annotation to prevent N+1 queries"

### **Step 4: Remove Hibernate batch fetching (optional but recommended)**

Since you're implementing proper DataLoader pattern, you should remove the Hibernate workarounds:

In `UserCalendar.java`:
- Line 31: Change `FetchType.EAGER` to `FetchType.LAZY` on user relationship
- Line 56: Change `FetchType.EAGER` to `FetchType.LAZY` on template relationship
- Line 69-70: Remove `@BatchSize(size = 10)` from orders (or keep if you want extra optimization)
- Line 73-74: Remove `@BatchSize(size = 10)` from events (or keep if you want extra optimization)

### **Step 5: Add integration test for DataLoader batching**

Update the test at line 665 (`testDataLoader_BatchLoading_MultipleCalendars`) to verify batching works:

```java
@Test
@Order(70)
void testDataLoader_BatchLoading_MultipleCalendars() {
    // Create 10 public calendars for testing
    List<UUID> calendarIds = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
        UUID id = createAndPersistPublicCalendar("Calendar " + i);
        calendarIds.add(id);
    }

    try {
        // Enable Hibernate SQL logging to count queries
        // This query should fetch 10 calendars with user and template
        // Expected: 3 queries (1 for calendars, 1 batch for users, 1 batch for templates)
        String query = String.format("""
            query {
                calendar1: calendar(id: "%s") { id name user { id email } template { id name } }
                calendar2: calendar(id: "%s") { id name user { id email } template { id name } }
                calendar3: calendar(id: "%s") { id name user { id email } template { id name } }
                calendar4: calendar(id: "%s") { id name user { id email } template { id name } }
                calendar5: calendar(id: "%s") { id name user { id email } template { id name } }
                calendar6: calendar(id: "%s") { id name user { id email } template { id name } }
                calendar7: calendar(id: "%s") { id name user { id email } template { id name } }
                calendar8: calendar(id: "%s") { id name user { id email } template { id name } }
                calendar9: calendar(id: "%s") { id name user { id email } template { id name } }
                calendar10: calendar(id: "%s") { id name user { id email } template { id name } }
            }
            """,
            calendarIds.get(0), calendarIds.get(1), calendarIds.get(2),
            calendarIds.get(3), calendarIds.get(4), calendarIds.get(5),
            calendarIds.get(6), calendarIds.get(7), calendarIds.get(8),
            calendarIds.get(9)
        );

        // Execute query - if batching works, this should succeed without N+1 queries
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("query", query))
            .when()
            .post("/graphql")
            .then()
            .statusCode(200)
            .body("data.calendar1.user.email", equalTo(TEST_EMAIL))
            .body("data.calendar10.template.name", equalTo(testTemplate.name))
            .body("errors", nullValue());

        // TODO: Add Hibernate statistics to verify query count
        // Expected: 3 DB queries (1 for calendars + 1 batch for users + 1 batch for templates)
        // Without DataLoader: 21 queries (1 + 10 + 10)
    } finally {
        for (UUID calendarId : calendarIds) {
            deleteTestCalendar(calendarId);
        }
    }
}
```

### **Step 6: Run full verification**

Before resubmitting:

```bash
# 1. Check for linting errors
mvn checkstyle:check

# 2. Run all tests
mvn test -Dtest=CalendarGraphQLTest

# 3. Verify compilation
mvn compile
```

---

## Summary

The core issue is that **DataLoader classes were created but never integrated**. The task explicitly requires "Implement DataLoader pattern to prevent N+1 queries", but the current implementation:
- Injects DataLoaders that are never used
- Relies on Hibernate @BatchSize instead of GraphQL DataLoader pattern
- Has no field resolvers using `@Source` annotation

**You must:**
1. **CRITICAL:** Implement batched field resolvers using SmallRye GraphQL's `@Batched` annotation
2. **CRITICAL:** Remove unused DataLoader classes (they follow graphql-java pattern, not SmallRye)
3. **CRITICAL:** Fix all linting errors (unused imports)
4. **HIGH:** Add field resolvers for `user` and `template` relationships on UserCalendar
5. **MEDIUM:** Update tests to verify batching works
6. **OPTIONAL:** Remove Hibernate @BatchSize and EAGER fetching since you're implementing proper DataLoader pattern

**Do not resubmit until:**
- Field resolvers with `@Batched` annotation are implemented
- All linting errors are fixed (zero unused imports)
- All tests pass (20/20)
- DataLoader pattern is properly implemented per SmallRye GraphQL standards
