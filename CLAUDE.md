# Village Calendar - Development Guidelines

## Named Query Pattern

Use this pattern for all JPA named queries:

```java
// 1. Define constant for query name (in the entity class)
public static final String QUERY_FIND_BY_ORDER_NUMBER_WITH_ITEMS =
        "CalendarOrder.findByOrderNumberWithItems";

// 2. NamedQuery annotation references the constant
@NamedQueries({
    @NamedQuery(
            name = QUERY_FIND_BY_ORDER_NUMBER_WITH_ITEMS,
            query =
                    "SELECT DISTINCT o FROM CalendarOrder o "
                            + "LEFT JOIN FETCH o.items i "
                            + "LEFT JOIN FETCH i.calendar "
                            + "WHERE o.orderNumber = :orderNumber")
})

// 3. Static finder method uses Panache find() with # prefix and Parameters
public static Optional<CalendarOrder> findByOrderNumberWithItems(String orderNumber) {
    return find("#" + QUERY_FIND_BY_ORDER_NUMBER_WITH_ITEMS,
                Parameters.with("orderNumber", orderNumber))
           .firstResultOptional();
}
```

Key points:
- Query name constant: `QUERY_` prefix, referenced in both annotation and finder
- Use `#` prefix with `find()` to invoke named queries
- Use `Parameters.with()` for type-safe parameter binding
- Use `JOIN FETCH` to eagerly load relationships and avoid N+1 queries
- Return `Optional` via `firstResultOptional()` for single results

## Application Exceptions

Use custom exceptions from `villagecompute.calendar.exceptions` instead of throwing `RuntimeException`:

```java
// Base exception (extends RuntimeException - no throws declaration needed)
throw new ApplicationException("Something went wrong");
throw new ApplicationException("Something went wrong", cause);

// Domain-specific exceptions
throw new EmailException("Failed to send email", cause);
throw new RenderingException("PDF rendering failed: " + e.getMessage(), e);
throw new PaymentException("Payment intent creation failed", e);
```

Key points:
- Never throw raw `RuntimeException` - use `ApplicationException` or a specific subclass
- All exceptions extend `RuntimeException` so they don't require throws declarations
- Use the appropriate domain exception: `EmailException`, `RenderingException`, `PaymentException`
- Always include the cause exception when wrapping: `new XxxException("message", cause)`
- Add new exception types to the `exceptions` package as needed for new domains
