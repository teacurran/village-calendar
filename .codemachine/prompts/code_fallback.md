# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Implement skeleton GraphQL resolvers for all queries and mutations defined in the GraphQL schema. Create resolver classes: CalendarResolver (queries: calendar, calendars; mutations: createCalendar, updateCalendar, deleteCalendar), OrderResolver (queries: order, orders; mutations: placeOrder, cancelOrder), UserResolver (query: currentUser; mutation: convertGuestSession), PdfResolver (query: pdfJob; mutation: generatePdf). Each resolver method should return stub data or throw NotImplementedException with TODO comments. Use SmallRye GraphQL annotations (@GraphQLApi, @Query, @Mutation). Configure authentication context injection (@Context SecurityIdentity). Ensure GraphQL endpoint (/graphql) is accessible and GraphQL UI loads at /graphql-ui.

---

## Issues Detected

### Missing Query Operations (Schema vs Implementation Mismatch)

*   **Missing Query: `currentUser`** - The schema defines both `me` and `currentUser` queries (lines 595 and 603 in schema.graphql), but only `me()` is implemented in CalendarGraphQL.java. The `currentUser` query is missing.

*   **Missing Query: `calendars(userId, year)`** - The schema defines `calendars(userId: ID, year: Int)` query (lines 582-588 in schema.graphql) that accepts an optional `userId` parameter for admin access. The current implementation only has `myCalendars(year)` which doesn't accept a userId parameter and doesn't support admin functionality.

*   **Schema Mismatch: `orders` vs `ordersByStatus`** - The schema defines `orders(userId: ID, status: OrderStatus)` query (lines 637-643 in schema.graphql), but the implementation has `ordersByStatus(status)` instead. The parameter names and functionality don't match the schema.

*   **Missing Query: `allOrders(status, limit)`** - The schema defines admin query `allOrders(status: OrderStatus, limit: Int)` (lines 551-557 in schema.graphql), but this is not implemented. The `ordersByStatus` query exists but doesn't accept a `limit` parameter and doesn't match the schema name.

*   **Missing Query: `allUsers(limit)`** - The schema defines admin query `allUsers(limit: Int)` (lines 563-566 in schema.graphql), but this query is not implemented in any resolver.

### Missing Mutation Operations

*   **Missing Mutation: `placeOrder(input)`** - The schema defines `placeOrder(input: PlaceOrderInput!)` mutation (lines 805-808 in schema.graphql) as an alternative to `createOrder`. This mutation is not implemented. Note: While `createOrder` exists, the schema explicitly defines both mutations, so both should be present.

### Return Type Issues

*   **PdfGraphQL Return Types** - The `pdfJob()` and `generatePdf()` methods in PdfGraphQL.java return `PdfJobStub` (a stub class defined in the same file), but the schema expects `PdfJob` type. While this works for stub implementation, it creates a type mismatch with the schema definition. The actual `PdfJob` entity should be used once it's available, or the stub should be properly mapped to match the schema type `PdfJob`.

---

## Best Approach to Fix

### 1. Add Missing Query: `currentUser`

Add a `currentUser()` query method to **CalendarGraphQL.java** that acts as an alias to the existing `me()` method. This can simply call the existing logic or be implemented identically.

```java
@Query("currentUser")
@Description("Get the currently authenticated user. Alias for 'me' query.")
@RolesAllowed("USER")
public CalendarUser currentUser() {
    // Same implementation as me() query
    return me();
}
```

### 2. Add Missing Query: `calendars(userId, year)`

Add a `calendars(userId, year)` query method to **CalendarGraphQL.java** that:
- If `userId` is NOT provided: returns calendars for the current authenticated user (same as `myCalendars`)
- If `userId` IS provided: verifies user has ADMIN role, then returns calendars for that user ID
- Supports optional `year` filter in both cases

```java
@Query("calendars")
@Description("Get calendars for a specific user (admin only) or filter by year.")
@RolesAllowed("USER")
public List<UserCalendar> calendars(
    @Name("userId") @Description("User ID to fetch calendars for (admin only)") String userId,
    @Name("year") @Description("Filter by calendar year (optional)") Integer year
) {
    // If userId provided, verify admin role and return that user's calendars
    // Otherwise, return current user's calendars (same as myCalendars)
    // TODO: Implement admin access and user ID filtering
    throw new UnsupportedOperationException("calendars query not yet implemented");
}
```

### 3. Fix Query Name: Rename `ordersByStatus` to `allOrders` and Add `limit` Parameter

In **OrderGraphQL.java**:
- Rename the existing `@Query("ordersByStatus")` to `@Query("allOrders")` (line 150)
- Add a `limit` parameter with default value of 50 to match the schema
- Update the method name from `ordersByStatus` to `allOrders`
- Ensure it has `@RolesAllowed("ADMIN")`

```java
@Query("allOrders")
@Description("Get all orders across all users (admin only).")
@RolesAllowed("ADMIN")
public List<CalendarOrder> allOrders(
    @Name("status") @Description("Filter by order status (optional)") OrderStatus status,
    @Name("limit") @Description("Maximum number of orders to return") Integer limit
) {
    // Existing implementation, but add limit support
}
```

### 4. Add Missing Query: `orders(userId, status)`

Add a new `orders(userId, status)` query method to **OrderGraphQL.java** that:
- If `userId` is NOT provided: returns orders for current authenticated user (same as `myOrders`)
- If `userId` IS provided: verifies user has ADMIN role, then returns orders for that user ID
- Supports optional `status` filter in both cases

```java
@Query("orders")
@Description("Get orders for a specific user (admin only) with optional status filter.")
@RolesAllowed("USER")
public List<CalendarOrder> orders(
    @Name("userId") @Description("User ID to fetch orders for (admin only)") String userId,
    @Name("status") @Description("Filter by order status (optional)") OrderStatus status
) {
    // If userId provided, verify admin role and return that user's orders
    // Otherwise, return current user's orders (same as myOrders)
    // TODO: Implement admin access and user ID filtering
    throw new UnsupportedOperationException("orders query not yet implemented");
}
```

### 5. Add Missing Query: `allUsers(limit)`

Add a new `allUsers(limit)` admin query. This should go in **CalendarGraphQL.java** since that's where user-related queries are located:

```java
@Query("allUsers")
@Description("Get all users (admin only).")
@RolesAllowed("ADMIN")
public List<CalendarUser> allUsers(
    @Name("limit") @Description("Maximum number of users to return") Integer limit
) {
    LOG.infof("Query: allUsers(limit=%d) (STUB IMPLEMENTATION)", limit);

    // TODO: Implement user listing with limit
    // Example: return CalendarUser.findAll().page(0, limit != null ? limit : 50).list();

    throw new UnsupportedOperationException("allUsers query not yet implemented");
}
```

### 6. Add Missing Mutation: `placeOrder(input)`

Add a `placeOrder(input)` mutation to **OrderGraphQL.java**. According to the schema, this returns `PaymentIntent!` just like `createOrder`, so it should have similar implementation:

```java
@Mutation("placeOrder")
@Description("Place an order for printed calendars. Alternative to createOrder with structured input type.")
@RolesAllowed("USER")
@Transactional
public PaymentIntent placeOrder(
    @Name("input") @Description("Order details") @NotNull PlaceOrderInput input
) {
    LOG.infof("Mutation: placeOrder(input) (STUB IMPLEMENTATION)");

    // TODO: Implement order placement using PlaceOrderInput
    // This is an alternative to createOrder with more structured input
    // Should extract fields from PlaceOrderInput and call similar logic to createOrder

    throw new UnsupportedOperationException("placeOrder mutation not yet implemented. Use createOrder instead.");
}
```

**Note:** You'll need to verify that `PlaceOrderInput` class exists in the data models. If it doesn't exist, you'll need to create it based on the schema definition (lines 525-537 in schema.graphql).

### 7. Fix PdfJob Return Type Issue (Optional but Recommended)

The `PdfJobStub` class in PdfGraphQL.java should ideally be replaced with the actual `PdfJob` entity from the data models once it's available. For now, ensure the stub class fields match the schema exactly (they currently do, so this is OK for stub implementation).

---

## Summary of Required Changes

**File: CalendarGraphQL.java**
- Add `currentUser()` query (alias to `me()`)
- Add `calendars(userId, year)` query with admin support
- Add `allUsers(limit)` admin query

**File: OrderGraphQL.java**
- Rename `ordersByStatus` to `allOrders` and add `limit` parameter
- Add `orders(userId, status)` query with admin support
- Add `placeOrder(input)` mutation

**File: Data Models (if needed)**
- Verify `PlaceOrderInput` class exists, create it if missing

---

## Testing After Implementation

After implementing these changes, verify:

1. Run `./mvnw compile` to ensure no compilation errors
2. Start the server with `./mvnw quarkus:dev`
3. Navigate to http://localhost:8030/graphql-ui (note: port 8030, not 8080)
4. Verify all queries and mutations appear in the GraphQL UI documentation
5. Test a simple query: `{ __schema { queryType { name } } }` should return successfully
6. Verify schema introspection shows all newly added operations

---

## Implementation Priority

**High Priority (Must Fix):**
1. Rename `ordersByStatus` to `allOrders` - This is a schema mismatch that will break clients
2. Add `currentUser` query - Simple alias, required by schema
3. Add `calendars(userId, year)` query - Required by schema
4. Add `orders(userId, status)` query - Required by schema

**Medium Priority (Should Fix):**
5. Add `allUsers(limit)` query - Admin functionality
6. Add `placeOrder` mutation - Alternative order creation path

**Low Priority (Nice to Have):**
7. Replace PdfJobStub with actual PdfJob entity - Can wait until PdfJob entity is created
