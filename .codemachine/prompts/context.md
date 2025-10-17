# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I3.T2",
  "iteration_id": "I3",
  "iteration_goal": "Implement complete e-commerce workflow including Stripe payment integration, order placement, order management dashboard (admin), and transactional email notifications",
  "description": "Create OrderService with business logic for e-commerce: createOrder (from cart/calendar, calculate subtotal/tax/shipping, persist Order and OrderItem entities), updateOrderStatus (transition order through lifecycle: PENDING → PAID → IN_PRODUCTION → SHIPPED → DELIVERED), cancelOrder (set status to CANCELLED, process refund if applicable), getOrder (by ID with authorization), listOrders (by user, by status, with pagination for admin). Implement OrderRepository with custom queries: findByUserId, findByStatus, findByOrderNumber (unique display number). Add order number generation (e.g., \"VC-2025-00001\" format). Handle order status state machine validation (cannot go from SHIPPED back to PENDING). Write unit tests for all order business logic.",
  "agent_type_hint": "BackendAgent",
  "inputs": "Order, OrderItem, Payment entities from I1.T8, E-commerce requirements from Plan Section \"Order Management\"",
  "target_files": [
    "src/main/java/villagecompute/calendar/service/OrderService.java",
    "src/main/java/villagecompute/calendar/repository/OrderRepository.java",
    "src/main/java/villagecompute/calendar/util/OrderNumberGenerator.java",
    "src/test/java/villagecompute/calendar/service/OrderServiceTest.java"
  ],
  "input_files": [
    "src/main/java/villagecompute/calendar/model/Order.java",
    "src/main/java/villagecompute/calendar/model/OrderItem.java",
    "src/main/java/villagecompute/calendar/model/Payment.java"
  ],
  "deliverables": "OrderService with complete e-commerce logic, OrderRepository with custom query methods, Order number generation (unique, sequential), Order status state machine validation, Unit tests with >80% coverage",
  "acceptance_criteria": "OrderService.createOrder() calculates tax and shipping, persists order, Order number format: \"VC-YYYY-NNNNN\" (e.g., VC-2025-00001), OrderService.updateOrderStatus() enforces valid transitions (PENDING can go to PAID or CANCELLED, but SHIPPED cannot go to PENDING), OrderRepository.findByOrderNumber() retrieves order by display number, Unit tests verify order creation, status transitions, cancellation logic",
  "dependencies": ["I1.T8"],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Order Management Domain

**From Analysis:**
The e-commerce domain in Village Calendar consists of:
- **CalendarOrder entity**: Already fully implemented with proper JPA annotations, relationships, validation, and helper methods
- **OrderStatus constants**: Defined in CalendarOrder class (PENDING, PAID, PROCESSING, SHIPPED, DELIVERED, CANCELLED)
- **OrderRepository**: Already implemented with all required custom queries
- **OrderService**: Partially implemented - needs enhancement for tax/shipping calculations, order number generation, and refund handling

**Key Architectural Principles:**
1. **Panache Active Record Pattern**: The project uses Panache with both active record (entity-based finders) and repository patterns
2. **Status State Machine**: Status transitions must be validated to prevent invalid state changes
3. **Transactional Integrity**: All database writes must use `@Transactional` annotation
4. **Authorization**: Order operations must verify user ownership or admin role
5. **Integration with Stripe**: Orders link to Stripe via `stripePaymentIntentId` and `stripeChargeId` fields

### Context: Data Model (from GraphQL Schema)

```graphql
"""
E-commerce order for printed calendars.
Integrates with Stripe for payment processing. Orders are created
after successful payment via Stripe webhook.

Note: Payment details are embedded directly in the order entity via
Stripe-specific fields (stripePaymentIntentId, stripeChargeId, paidAt)
rather than a separate Payment entity. Orders currently support single-item
purchases (one calendar design with quantity field).
"""
type CalendarOrder {
  """Calendar being ordered for printing"""
  calendar: UserCalendar!

  """Timestamp when order was created"""
  created: DateTime!

  """Timestamp when order was delivered"""
  deliveredAt: DateTime

  """Unique order identifier (UUID)"""
  id: ID!

  """Admin notes about order fulfillment"""
  notes: String

  """Timestamp when payment was captured"""
  paidAt: DateTime

  """Number of calendar copies to print"""
  quantity: Int!

  """Timestamp when order was shipped"""
  shippedAt: DateTime

  """Shipping address (JSONB: street, city, state, postalCode, country)"""
  shippingAddress: JSON!

  """Order fulfillment status"""
  status: OrderStatus!

  """Stripe Charge ID (set after payment captured)"""
  stripeChargeId: String

  """Stripe Payment Intent ID"""
  stripePaymentIntentId: String

  """Total order price (quantity * unitPrice, USD)"""
  totalPrice: BigDecimal!

  """Shipment tracking number (set when order ships)"""
  trackingNumber: String

  """Price per calendar (USD)"""
  unitPrice: BigDecimal!

  """Timestamp when order was last updated"""
  updated: DateTime!

  """User who placed this order"""
  user: CalendarUser!
}
```

### Context: Status Transition Rules (from Architecture)

Valid status transitions:
```
PENDING → PAID | CANCELLED
PAID → PROCESSING | SHIPPED | CANCELLED
PROCESSING → SHIPPED | CANCELLED
SHIPPED → DELIVERED
DELIVERED (terminal)
CANCELLED (terminal)
```

**Important Note**: The task description mentions "IN_PRODUCTION" as a status, but the actual codebase uses "PROCESSING" instead. You MUST use the existing "PROCESSING" status constant from the CalendarOrder entity.

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

#### File: `src/main/java/villagecompute/calendar/data/models/CalendarOrder.java`
- **Summary**: Fully implemented JPA entity representing e-commerce orders. Extends `DefaultPanacheEntityWithTimestamps` for automatic UUID id, created/updated timestamps, and optimistic locking via version field. Contains all required fields: user (FK), calendar (FK), quantity, unitPrice, totalPrice, status, shippingAddress (JSONB), Stripe IDs, notes, and timestamp fields.
- **Recommendation**: You MUST use this existing entity without modification. It already has helper methods `markAsPaid()`, `markAsShipped()`, `cancel()`, and `isTerminal()` that you SHOULD reuse in your service layer.
- **Important**: The entity defines status as String constants (STATUS_PENDING, STATUS_PAID, etc.). Do NOT create a separate OrderStatus enum - use these String constants.
- **Active Record Methods**: The entity already provides static finder methods: `findByUser()`, `findByStatusOrderByCreatedDesc()`, `findByCalendar()`, `findByStripePaymentIntent()`. These can be called directly on the entity class.

#### File: `src/main/java/villagecompute/calendar/data/repositories/CalendarOrderRepository.java`
- **Summary**: Repository layer implementing PanacheRepository<CalendarOrder>. Already contains all required custom query methods including `findByStatusOrderByCreatedDesc()`, `findByUser()`, `findByStripePaymentIntent()`, `findPendingOrders()`, and `findPaidNotShipped()`.
- **Recommendation**: This repository is COMPLETE and fully implements the task requirements. You do NOT need to modify it. Your OrderService SHOULD inject and use this repository for database operations.

#### File: `src/main/java/villagecompute/calendar/services/OrderService.java`
- **Summary**: Partially implemented service with basic CRUD operations. Contains `createOrder()` (basic version without tax/shipping), `updateOrderStatus()` (with state machine validation), `getOrdersByStatus()`, `getUserOrders()`, `getOrderById()`, and `findByStripePaymentIntent()`.
- **Current Gaps**:
  1. No tax calculation in `createOrder()`
  2. No shipping calculation in `createOrder()`
  3. No order number generation
  4. No `cancelOrder()` method with refund processing
  5. Missing enhanced logging for business events
- **Recommendation**: You MUST enhance this existing service rather than creating a new one. Focus on filling the gaps listed above.
- **State Machine**: The `validateStatusTransition()` method already implements the state machine validation. Review and ensure it matches the requirements (it currently does).

#### File: `src/main/java/villagecompute/calendar/data/models/enums/OrderStatus.java`
- **Summary**: Enum defining order statuses (PENDING, PAID, PROCESSING, SHIPPED, DELIVERED, CANCELLED, REFUNDED).
- **Warning**: This enum exists but is NOT used by CalendarOrder entity, which uses String constants instead. For consistency with the existing codebase, continue using String constants in OrderService.

#### File: `src/main/java/villagecompute/calendar/data/models/DefaultPanacheEntityWithTimestamps.java`
- **Summary**: Base class for all entities providing UUID id, created/updated timestamps (via @CreationTimestamp/@UpdateTimestamp), and optimistic locking version field.
- **Recommendation**: All entities inherit automatic timestamp management. You do NOT need to manually set created/updated fields.

#### File: `src/main/java/villagecompute/calendar/integration/stripe/StripeService.java`
- **Summary**: Handles Stripe Checkout Session creation, retrieval, and webhook signature validation. Provides `createCheckoutSession()` which generates a Stripe hosted checkout page URL.
- **Recommendation**: Your OrderService should NOT call StripeService directly for refunds. Refund processing will be handled in I3.T3 (PaymentService). For now, the `cancelOrder()` method should only update order status to CANCELLED and add notes - actual Stripe refund will be implemented later.

### Implementation Tips & Notes

#### Tip 1: Order Number Generation Strategy
- **Pattern**: "VC-YYYY-NNNNN" (e.g., VC-2025-00001)
- **Implementation Approach**: Create `OrderNumberGenerator` utility class with a method that:
  1. Extracts the current year
  2. Queries the database for the last order number for that year
  3. Increments the sequence number
  4. Formats as "VC-{year}-{sequence:05d}"
- **Concurrency Consideration**: Use a transactional query within the order creation transaction to prevent duplicate numbers. You MAY use a database query like `SELECT COUNT(*) FROM calendar_orders WHERE EXTRACT(YEAR FROM created) = ?` to get the next sequence number.
- **Alternative**: Consider adding an `orderNumber` field to CalendarOrder entity with a unique constraint and let the database enforce uniqueness.

#### Tip 2: Tax and Shipping Calculation Placeholders
- **Task Requirement**: The task says "calculate subtotal/tax/shipping" but I3.T8 specifically implements shipping calculation.
- **Recommendation for I3.T2**: Add method signatures for `calculateTax()` and `calculateShipping()` in OrderService, but implement them as simple placeholders returning BigDecimal.ZERO. Add TODO comments referencing I3.T8.
- **Rationale**: This allows I3.T2 to focus on order lifecycle management while deferring actual calculation logic to the dedicated shipping task.

#### Tip 3: Authorization Checks
- **Existing Pattern**: The project uses `@Context SecurityIdentity` injection in GraphQL resolvers and REST resources.
- **Service Layer**: OrderService methods should accept userId as a parameter (extracted from SecurityIdentity by the calling resolver/resource) and perform ownership checks.
- **Admin Override**: Check if user has "admin" role to allow admin users to access/modify any order.
- **Example Pattern** (from existing code):
```java
// In resolver/resource layer:
@Inject SecurityIdentity securityIdentity;
UUID userId = UUID.fromString(securityIdentity.getPrincipal().getName());

// Pass to service:
orderService.getOrder(orderId, userId, isAdmin);
```

#### Tip 4: Testing Strategy
- **Project Convention**: Uses Quarkus @QuarkusTest with in-memory H2 database (see pom.xml, quarkus-jdbc-h2 test dependency).
- **Existing Tests**: Three test files already exist:
  - `CalendarOrderTest.java` (entity tests)
  - `CalendarOrderRepositoryTest.java` (repository tests)
  - `OrderServiceTest.java` (service tests)
- **Recommendation**: READ the existing `OrderServiceTest.java` file first to understand the project's testing patterns, then enhance it with tests for the new functionality (order number generation, tax/shipping placeholders, cancelOrder).
- **Coverage Requirement**: JaCoCo is configured to enforce 70% coverage for model and repository packages (see pom.xml lines 318-346). Aim for >80% for OrderService.

#### Tip 5: Transactional Annotations
- **Pattern**: All OrderService methods that modify data MUST be annotated with `@Transactional` from `jakarta.transaction.Transactional`.
- **Existing Usage**: The current OrderService already uses this pattern correctly. Follow the same approach.
- **Propagation**: Do NOT nest @Transactional methods unless you understand propagation behavior. Stick to method-level annotations on public service methods.

#### Warning 1: Status String vs Enum
- **Inconsistency Detected**: The task description mentions "IN_PRODUCTION" but the codebase uses "PROCESSING" (see CalendarOrder.STATUS_PROCESSING constant).
- **Action Required**: Use "PROCESSING" everywhere. Do NOT introduce "IN_PRODUCTION" as it will break the state machine.

#### Warning 2: Payment vs Order Entity Confusion
- **Note from GraphQL Schema**: "Payment details are embedded directly in the order entity via Stripe-specific fields (stripePaymentIntentId, stripeChargeId, paidAt) rather than a separate Payment entity."
- **Action**: Do NOT create a separate Payment entity for I3.T2. Payment information is stored directly in CalendarOrder fields.
- **Clarification**: The task description mentions "Payment entities from I1.T8" but this appears to be a planning artifact. The actual implementation uses embedded payment fields in CalendarOrder.

#### Warning 3: Order Item Complexity
- **Task Description**: Mentions "OrderItem entities" but the current schema shows orders support single-item purchases via quantity field.
- **GraphQL Schema Comment**: "Orders currently support single-item purchases (one calendar design with quantity field)."
- **Action**: Do NOT create an OrderItem entity. The current design is simplified to one calendar per order with a quantity field. This is intentional for MVP scope.
- **Rationale**: The architecture may evolve to support multi-item orders in future iterations, but I3.T2 should work with the current single-item design.

### Final Recommendations

1. **Read Existing Tests First**: Start by reading `/Users/tea/dev/VillageCompute/code/village-calendar/src/test/java/villagecompute/calendar/services/OrderServiceTest.java` to understand testing patterns before writing new tests.

2. **Enhance, Don't Replace**: The OrderService and OrderRepository are already functional. Your task is to ENHANCE them with missing features (order numbers, tax/shipping placeholders, cancel method), not rewrite from scratch.

3. **Follow Existing Patterns**: The codebase has strong conventions:
   - Panache active record + repository hybrid pattern
   - String constants for status (not enums)
   - @Transactional on write methods
   - Logging with jboss Logger
   - BigDecimal for currency
   - JSONB for complex objects (shippingAddress)

4. **Defer Stripe Refund Logic**: The `cancelOrder()` method should update status and notes but should NOT call Stripe API for refunds yet. That's I3.T3's responsibility (PaymentService).

5. **Use Existing Helper Methods**: CalendarOrder entity provides `markAsPaid()`, `markAsShipped()`, `cancel()`, `isTerminal()`. Use these instead of manually updating fields and calling persist().

---

**END OF BRIEFING PACKAGE**
