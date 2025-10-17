# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create comprehensive GraphQL schema definition file (SDL format) for the Village Calendar API. Define root Query type with queries: calendar(id), calendars(userId, year), templates(isActive), order(orderId), orders(userId, status), currentUser, pdfJob(id). Define root Mutation type with mutations: createCalendar(input), updateCalendar(id, input), deleteCalendar(id), generatePdf(calendarId, watermark), placeOrder(input), cancelOrder(orderId, reason), convertGuestSession(sessionId). Define all necessary types: Calendar, Event, CalendarConfig, CalendarTemplate, Order, OrderItem, Payment, User, PdfJob. Define input types for mutations. Define enums: OrderStatus, ProductType, OAuthProvider. Include field descriptions and deprecation annotations where applicable.

---

## Issues Detected

*   **Linting Error**: The type `Event` is defined in the schema but not used anywhere. (api/schema.graphql:364)
*   **Linting Error**: The type `CalendarConfig` is defined in the schema but not used anywhere. (api/schema.graphql:385)
*   **Linting Error**: The type `OrderItem` is defined in the schema but not used anywhere. (api/schema.graphql:448)
*   **Linting Error**: The type `Payment` is defined in the schema but not used anywhere. (api/schema.graphql:472)
*   **Linting Warning**: Multiple types have fields not in alphabetical order (style issue)

---

## Best Approach to Fix

You have defined the `Event`, `CalendarConfig`, `OrderItem`, and `Payment` types as standalone types, but they are not referenced by any fields in other types. This violates GraphQL schema best practices where all types should be reachable from the root Query or Mutation types.

**IMPORTANT DECISION**: You need to decide whether these types should be:

**Option A: Remove unused types** (RECOMMENDED based on Java entity analysis)
- The Java entities (`UserCalendar.java`, `CalendarOrder.java`) use JSONB fields for flexible data storage
- The `Event`, `CalendarConfig` types are stored within the JSONB `configuration` field, not as separate database entities
- The `OrderItem` type is not implemented in the current data model (orders are single-item only)
- The `Payment` type functionality is embedded in `CalendarOrder` with Stripe fields

If you choose Option A:
1. Remove the `Event` type definition (lines 364-379)
2. Remove the `CalendarConfig` type and its related types `AstronomyConfig`, `LayoutConfig`, `ColorScheme` (lines 385-442)
3. Remove the `OrderItem` type definition (lines 448-466)
4. Remove the `Payment` type definition (lines 472-502)
5. Add comments in the schema explaining that event and configuration data is stored as JSON in the `configuration` field of `UserCalendar`

**Option B: Integrate unused types into the schema**
If these types are intended for future type safety or code generation, you must connect them:
1. Add `events: [Event!]!` field to `UserCalendar` type
2. Add `items: [OrderItem!]!` field to `CalendarOrder` type (and remove `quantity` field)
3. Add `payment: Payment` field to `CalendarOrder` type
4. Either change `UserCalendar.configuration` from `JSON` to `CalendarConfig` OR add a separate `typedConfig: CalendarConfig` field

**CRITICAL**: Before choosing Option B, verify that the Java resolver implementations support these types. The current Java entities use JSONB, so Option B would require significant backend changes.

**RECOMMENDATION**: Choose **Option A** and remove the unused types, as they don't align with the current Java entity implementation. Document in schema comments that these structures are embedded in JSONB fields for flexibility.

After fixing the unused types issue, optionally fix the alphabetical sorting warnings by reordering fields in:
- `ProductType` enum (lines 88-97)
- `PdfJob` type (lines 325-358)
- `Event` type (lines 364-379) - if you keep it
- Other types flagged by the linter

Run `graphql-schema-linter api/schema.graphql` to verify all issues are resolved.
