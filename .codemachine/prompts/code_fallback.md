# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Replace stub implementations in CalendarResolver with real service calls. Implement queries: calendar(id) (fetch calendar with events, authorize user), calendars(userId, year) (list user's calendars with pagination). Implement mutations: createCalendar(input) (validate input, call CalendarService), updateCalendar(id, input) (authorize, update), deleteCalendar(id) (authorize, soft/hard delete). Inject SecurityIdentity for user context. Implement DataLoader pattern to prevent N+1 queries when fetching related entities (e.g., calendar with user and events). Add error handling (map service exceptions to GraphQL errors). Write integration tests for all resolver methods.

---

## Issues Detected

### **CRITICAL ISSUE #1: DataLoaders are not integrated into CalendarGraphQL**

You created three DataLoader classes (`UserDataLoader`, `EventDataLoader`, `TemplateDataLoader`) in the `src/main/java/villagecompute/calendar/api/graphql/dataloader/` directory, but they are **NOT INJECTED OR USED** in `CalendarGraphQL.java`.

**Evidence:**
- Searched for "UserDataLoader", "EventDataLoader", "TemplateDataLoader" in CalendarGraphQL.java - **NO MATCHES FOUND**
- The DataLoader classes exist but serve no purpose without integration
- This means N+1 query problem is NOT resolved

**What's missing:**
1. No `@Inject` fields for the DataLoaders in CalendarGraphQL
2. No field resolvers using `@Source` annotation to leverage DataLoaders
3. No DataLoader usage when fetching related entities (user, template, events)

### **CRITICAL ISSUE #2: No field resolvers for UserCalendar type**

The acceptance criteria states: "GraphQL query { calendar(id: \"123\") { title events { eventText } } } returns calendar with events"

However:
- There is **NO FIELD RESOLVER** for `events` on the UserCalendar type
- There are no `@Source` annotated methods in CalendarGraphQL that expose related entities through DataLoaders
- The schema doesn't define an `events` field on UserCalendar (events are embedded in configuration JSON)
- Without field resolvers, fetching related entities will cause N+1 queries via JPA lazy loading

**What's missing:**
1. Field resolver for `user` on UserCalendar using UserDataLoader
2. Field resolver for `template` on UserCalendar using TemplateDataLoader
3. Field resolver for `events` on UserCalendar using EventDataLoader (if events should be exposed as a separate field per acceptance criteria)
4. OR clarification on whether events should remain embedded in configuration vs exposed as a GraphQL field

### **CRITICAL ISSUE #3: Linting errors in CalendarGraphQL.java**

Multiple checkstyle violations detected:

**Import issues:**
*   Line 10: Wildcard import (`org.eclipse.microprofile.graphql.*`) should be avoided
*   Line 16: Unused import - `villagecompute.calendar.data.models.CalendarTemplate`

**Missing Javadoc:**
*   Lines 34, 36, 39, 42, 45, 48: Missing Javadoc comments for injected fields

**Visibility modifiers:**
*   Lines 37, 40, 43, 46, 49: Injected fields should be private (currently package-private)

**Final parameters:**
*   Multiple methods have non-final parameters (lines 108, 145, 149, 210, 260, 294, 351)

**Magic numbers:**
*   Lines 124, 191: Magic number `1000` used for pagination limit
*   Line 267: Magic number `50` used for limit

**Line length:**
*   Multiple lines exceed 80 character limit (lines 51, 53, 62, 79, 90, 105, 123-124, 127, 142, 168, 170, 172, 183, 190-191, 194, 207, 223, 229, 231, 239, 257, 278, 280, 290, 347)

### **Issue #4: Test coverage incomplete**

While 18 tests pass, the tests don't verify the key acceptance criteria:

**Missing test verification:**
1. **No test verifies DataLoader batching works** - Acceptance criteria: "fetching 10 calendars with users requires 2 DB queries, not 11"
   - Need a test that creates 10 calendars, queries them with related entities, and counts SQL queries to verify batching
2. **No test for events field** - Acceptance criteria: "GraphQL query { calendar(id: \"123\") { title events { eventText } } }"
   - Test at line 166 tries to query events but likely relies on JPA relationship, not DataLoader
3. **No integration test demonstrating end-to-end GraphQL request/response flow with all related entities**

### **Issue #5: Acceptance criteria field name mismatch**

The acceptance criteria references `{ title events { eventText } }` but:
- UserCalendar schema has `name` field, NOT `title`
- This suggests the acceptance criteria may be outdated or refer to a different schema version
- Need clarification on correct field names

---

## Best Approach to Fix

### **Step 1: Integrate DataLoaders into CalendarGraphQL**

You MUST add field resolvers to CalendarGraphQL that use the DataLoaders. Follow this pattern:

```java
@Inject
UserDataLoader userDataLoader;

@Inject
TemplateDataLoader templateDataLoader;

@Inject
EventDataLoader eventDataLoader;

// Field resolver for UserCalendar.user
@Query
public CompletionStage<CalendarUser> user(@Source UserCalendar calendar, @Context DataLoaderRegistry registry) {
    if (calendar.user == null) {
        return CompletableFuture.completedFuture(null);
    }
    DataLoader<UUID, CalendarUser> loader = registry.getDataLoader(UserDataLoader.class);
    return loader.load(calendar.user.id);
}

// Field resolver for UserCalendar.template
@Query
public CompletionStage<CalendarTemplate> template(@Source UserCalendar calendar, @Context DataLoaderRegistry registry) {
    DataLoader<UUID, CalendarTemplate> loader = registry.getDataLoader(TemplateDataLoader.class);
    return loader.load(calendar.template.id);
}

// Field resolver for UserCalendar.events (if required by acceptance criteria)
@Query
public CompletionStage<List<Event>> events(@Source UserCalendar calendar, @Context DataLoaderRegistry registry) {
    DataLoader<UUID, List<Event>> loader = registry.getDataLoader(EventDataLoader.class);
    return loader.load(calendar.id);
}
```

**IMPORTANT:** You need to:
1. Inject the DataLoader classes in CalendarGraphQL
2. Create field resolvers using `@Source` annotation for each related entity
3. Use `DataLoaderRegistry` from GraphQL context to get the appropriate DataLoader
4. Return `CompletionStage` for async batch loading
5. Register the DataLoaders with the GraphQL engine (may require configuration in application.properties or a DataLoaderRegistryProducer)

### **Step 2: Fix linting errors**

Before submitting, you MUST fix ALL checkstyle violations in CalendarGraphQL.java:

1. **Replace wildcard import** on line 10 with explicit imports
2. **Remove unused import** CalendarTemplate on line 16
3. **Add Javadoc comments** for all injected fields (lines 34, 36, 39, 42, 45, 48)
4. **Make injected fields private** (lines 37, 40, 43, 46, 49)
5. **Add final modifier** to all method parameters
6. **Extract magic numbers** to named constants:
   ```java
   private static final int DEFAULT_PAGE_SIZE = 1000;
   private static final int MAX_USER_LIMIT = 50;
   ```
7. **Break long lines** to stay under 80 characters (use multi-line formatting for method signatures and chained calls)

### **Step 3: Add DataLoader integration tests**

You MUST create a test that verifies DataLoader batching prevents N+1 queries:

```java
@Test
@Order(70)
void testDataLoader_BatchesUserQueries() {
    // Create 10 calendars for the test user
    List<UUID> calendarIds = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
        UUID id = createAndPersistPublicCalendar("Calendar " + i);
        calendarIds.add(id);
    }

    try {
        // Enable SQL logging to count queries
        // Query all 10 calendars with their users
        String query = String.format("""
            query {
                calendars(userId: "%s") {
                    id
                    name
                    user {
                        email
                    }
                    template {
                        name
                    }
                }
            }
            """, testUser.id.toString());

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("query", query))
            .when()
            .post("/graphql")
            .then()
            .statusCode(200)
            .body("data.calendars", hasSize(10))
            .body("errors", nullValue());

        // TODO: Add assertion to verify query count
        // Expected: 3 queries (1 for calendars, 1 batch for users, 1 batch for templates)
        // Without DataLoader: 21 queries (1 for calendars + 10 for users + 10 for templates)
    } finally {
        calendarIds.forEach(this::deleteTestCalendar);
    }
}
```

### **Step 4: Clarify and test events field**

You need to determine:
1. Should events be a separate GraphQL field on UserCalendar? (acceptance criteria suggests yes)
2. Or should events remain embedded in configuration JSON? (current schema suggests yes)

**If events should be a separate field:**
- Add field resolver for events using EventDataLoader
- Update tests to verify events can be queried
- Add Event type to GraphQL schema if not present

**If events remain in configuration:**
- Update acceptance criteria interpretation
- Document that events are accessed via configuration.events
- Ensure tests reflect this

### **Step 5: Run full verification before resubmission**

Before resubmitting, you MUST verify:

```bash
# 1. All linting passes
mvn checkstyle:check

# 2. All tests pass
mvn test -Dtest=CalendarGraphQLTest

# 3. No compilation errors
mvn compile
```

---

## Summary

The core issue is that DataLoaders were created but **NEVER INTEGRATED**. The code refactored CalendarGraphQL to use services (good!), but failed to implement the DataLoader pattern to prevent N+1 queries (critical requirement). You must add field resolvers that use DataLoaders, fix all linting errors, and add tests that verify batching works.

**Priority order:**
1. **CRITICAL:** Integrate DataLoaders with field resolvers (Step 1)
2. **CRITICAL:** Fix all linting errors (Step 2)
3. **HIGH:** Add DataLoader integration test (Step 3)
4. **MEDIUM:** Clarify/fix events field handling (Step 4)
5. **MANDATORY:** Run full verification (Step 5)

Do not resubmit until ALL issues are resolved and ALL tests pass with zero linting errors.
