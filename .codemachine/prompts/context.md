# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I3.T8",
  "iteration_id": "I3",
  "iteration_goal": "Implement complete e-commerce workflow including Stripe payment integration, order placement, order management dashboard (admin), and transactional email notifications",
  "description": "Create ShippingService for calculating shipping costs based on destination address. Implement simple shipping rate table (configurable in database or properties): domestic (US) shipping tiers (standard, priority, express with fixed rates), international shipping (fixed rate by region or disable for MVP). Add shipping cost calculation to OrderService.createOrder (parse shipping address, determine rate, add to order total). Create shipping_rates configuration table or use application.properties. Handle tax calculation (placeholder for Stripe Tax integration in future - for MVP, use simple state sales tax table or skip). Add shipping cost display in checkout UI (update OrderSummary component).",
  "agent_type_hint": "BackendAgent",
  "inputs": "Shipping requirements from Plan Section 'Shipping Options', Order and shipping address structure from Order entity",
  "target_files": [
    "src/main/java/villagecompute/calendar/services/ShippingService.java",
    "src/main/resources/application.properties",
    "src/test/java/villagecompute/calendar/services/ShippingServiceTest.java"
  ],
  "input_files": [
    "src/main/java/villagecompute/calendar/services/OrderService.java",
    "src/main/java/villagecompute/calendar/data/models/CalendarOrder.java"
  ],
  "deliverables": [
    "ShippingService with rate calculation logic",
    "Shipping rates configured (standard: $5.99, priority: $9.99, express: $14.99 for MVP)",
    "Integration into OrderService (shipping cost added to order total)",
    "Unit tests for rate calculation (domestic, international)"
  ],
  "acceptance_criteria": [
    "ShippingService.calculateRate(address) returns correct rate based on address",
    "Domestic US addresses charged standard rate ($5.99)",
    "International addresses charged higher rate or rejected (for MVP)",
    "OrderService.createOrder includes shipping cost in total",
    "Unit tests verify rate calculation for various address scenarios",
    "Shipping cost displayed in checkout UI OrderSummary component"
  ],
  "dependencies": ["I3.T2"],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Task I3.T8 Detailed Specification (from 02_Iteration_I3.md)

```markdown
### Task 3.8: Implement Shipping Cost Calculation

**Task ID:** `I3.T8`

**Description:**
Create ShippingService for calculating shipping costs based on destination address. Implement simple shipping rate table (configurable in database or properties): domestic (US) shipping tiers (standard, priority, express with fixed rates), international shipping (fixed rate by region or disable for MVP). Add shipping cost calculation to OrderService.createOrder (parse shipping address, determine rate, add to order total). Create shipping_rates configuration table or use application.properties. Handle tax calculation (placeholder for Stripe Tax integration in future - for MVP, use simple state sales tax table or skip). Add shipping cost display in checkout UI (update OrderSummary component).

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- Shipping requirements from Plan Section "Shipping Options"
- Order and shipping address structure from Order entity

**Input Files:**
- `src/main/java/villagecompute/calendar/service/OrderService.java`
- `src/main/java/villagecompute/calendar/model/Order.java`

**Target Files:**
- `src/main/java/villagecompute/calendar/service/ShippingService.java`
- `src/main/resources/application.properties` (add shipping rate config)
- `src/test/java/villagecompute/calendar/service/ShippingServiceTest.java`

**Deliverables:**
- ShippingService with rate calculation logic
- Shipping rates configured (standard: $5.99, priority: $9.99, express: $14.99 for MVP)
- Integration into OrderService (shipping cost added to order total)
- Unit tests for rate calculation (domestic, international)

**Acceptance Criteria:**
- `ShippingService.calculateRate(address)` returns correct rate based on address
- Domestic US addresses charged standard rate ($5.99)
- International addresses charged higher rate or rejected (for MVP)
- OrderService.createOrder includes shipping cost in total
- Unit tests verify rate calculation for various address scenarios
- Shipping cost displayed in checkout UI OrderSummary component

**Dependencies:** `I3.T2` (OrderService)

**Parallelizable:** Yes (can develop concurrently with email system)
```

### Context: Shipping Address Structure (from api/schema.graphql)

```graphql
input AddressInput {
  """City"""
  city: String!

  """Country code (ISO 3166-1 alpha-2, e.g., "US")"""
  country: String!

  """Postal/ZIP code"""
  postalCode: String!

  """State/province code (e.g., "TN", "CA")"""
  state: String!

  """Street address"""
  street: String!

  """Apartment, suite, etc. (optional)"""
  street2: String
}
```

### Context: Order Data Model (from database ERD references)

The Order entity (CalendarOrder) includes:
- `shipping_address` field stored as JSONB containing the address structure above
- `total_price` calculation that should include: subtotal + tax + shipping

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `src/main/java/villagecompute/calendar/services/OrderService.java`
    *   **Summary:** This is the main order management service. It already has placeholder methods `calculateTax()` (lines 283-290) and `calculateShipping()` (lines 300-307) that both return `BigDecimal.ZERO`. These methods are called during `createOrder()` at lines 73-74.
    *   **Recommendation:** You MUST inject ShippingService via `@Inject` and call it from the `calculateShipping()` method. Replace the current `return BigDecimal.ZERO;` with a call like `return shippingService.calculateShippingCost(order);`
    *   **Critical Note:** The `calculateShipping()` method receives a `CalendarOrder` parameter that already has the `shippingAddress` field populated (see line 70 where it's set before calculation). Your service can extract the JsonNode address from `order.shippingAddress`.
    *   **Integration Point:** Lines 73-74 show how calculateShipping is called: `BigDecimal shipping = calculateShipping(order);` The result is added to the total at line 77.

*   **File:** `src/main/java/villagecompute/calendar/data/models/CalendarOrder.java`
    *   **Summary:** This entity defines the order data model. The shipping address is stored as a `JsonNode` field at line 71 (`public JsonNode shippingAddress;`) with JSONB column type.
    *   **Recommendation:** Your ShippingService MUST be able to parse a `JsonNode` to extract address fields. Use Jackson's `JsonNode.get("field")` methods to extract `country`, `state`, etc.
    *   **Key Fields:** Lines 49-66 define the order structure. Note `quantity` (line 50), `unitPrice` (line 55), `totalPrice` (line 60), and `shippingAddress` (line 69).
    *   **Address Format:** The JsonNode will contain fields matching the GraphQL AddressInput: `country`, `state`, `city`, `street`, `postalCode`, `street2` (optional).

*   **File:** `src/main/java/villagecompute/calendar/services/EmailService.java`
    *   **Summary:** This is an example of a well-structured Quarkus service using `@ApplicationScoped` (line 14), dependency injection, and configuration properties with `@ConfigProperty`.
    *   **Recommendation:** You SHOULD follow this exact pattern for your ShippingService:
        - Use `@ApplicationScoped` annotation on the class
        - Use `@ConfigProperty` to inject shipping rates from application.properties (see lines 22-23 for the pattern)
        - Use `Logger.getLogger(ShippingService.class)` for logging (line 17)
        - Follow the method documentation style (Javadoc comments)

*   **File:** `src/main/resources/application.properties`
    *   **Summary:** Main configuration file. Lines 116-120 show the R2 configuration pattern, lines 133-135 show Stripe configuration pattern.
    *   **Recommendation:** You MUST add shipping rate configuration following this pattern:
        ```properties
        # Shipping Rate Configuration
        calendar.shipping.domestic.standard=${SHIPPING_STANDARD:5.99}
        calendar.shipping.domestic.priority=${SHIPPING_PRIORITY:9.99}
        calendar.shipping.domestic.express=${SHIPPING_EXPRESS:14.99}
        calendar.shipping.international=${SHIPPING_INTERNATIONAL:19.99}
        ```
    *   **Convention:** Use the `calendar.shipping.*` prefix to match other calendar-specific configs. Include environment variable fallback with default values.

### Implementation Tips & Notes

*   **Tip 1: JSON Address Parsing**
    The shipping address is stored as a Jackson `JsonNode`. To extract the country:
    ```java
    JsonNode address = order.shippingAddress;
    String country = address.get("country").asText();
    ```
    Use `asText()` to safely convert to String (returns empty string if field is missing).

*   **Tip 2: Configuration Injection Pattern**
    Inject shipping rates using `@ConfigProperty` with BigDecimal type:
    ```java
    @ConfigProperty(name = "calendar.shipping.domestic.standard", defaultValue = "5.99")
    BigDecimal domesticStandardRate;
    ```
    Quarkus will automatically convert the string to BigDecimal.

*   **Tip 3: MVP Simplification**
    For MVP, the task says to reject international orders. Implement this by:
    ```java
    if (!"US".equals(country)) {
        throw new IllegalArgumentException("International shipping not supported in MVP");
    }
    ```
    Later, you can return a higher rate instead of throwing an exception.

*   **Tip 4: Tax Calculation**
    The task mentions "Handle tax calculation" but says to "skip for MVP". The OrderService.calculateTax() method already returns zero with a TODO comment. You should:
    - Leave calculateTax() as-is (returning BigDecimal.ZERO)
    - Update the TODO comment to reference future Stripe Tax integration
    - Do NOT implement tax logic in this task

*   **Tip 5: Unit Testing with JsonNode**
    For unit tests, create sample addresses using Jackson ObjectMapper:
    ```java
    ObjectMapper mapper = new ObjectMapper();
    JsonNode usAddress = mapper.readTree("{\"country\":\"US\",\"state\":\"TN\",\"city\":\"Nashville\"}");
    ```

*   **Tip 6: Method Signature**
    The OrderService expects to call `calculateShipping(CalendarOrder order)`, so your service method should be:
    ```java
    public BigDecimal calculateShippingCost(CalendarOrder order)
    ```
    This maintains consistency with the existing `calculateTax(CalendarOrder order)` pattern.

*   **Tip 7: Null Safety**
    Add validation for null or missing address fields:
    ```java
    if (order.shippingAddress == null) {
        throw new IllegalArgumentException("Shipping address is required");
    }
    if (!order.shippingAddress.has("country")) {
        throw new IllegalArgumentException("Country is required in shipping address");
    }
    ```

*   **Tip 8: Logging Best Practices**
    Follow the existing logging pattern:
    - Use `LOG.infof()` for significant events (e.g., "Calculated shipping cost $%.2f for order %s", cost, orderId)
    - Use `LOG.debugf()` for detailed parsing steps (e.g., "Parsed address: country=%s, state=%s")
    - Use `LOG.errorf()` for validation failures before throwing exceptions

*   **Warning 1: Package Name**
    The task specification uses incorrect package names (com.villagecompute.calendar). The actual package structure is:
    - `villagecompute.calendar.services` (NOT com.villagecompute.calendar.service)
    - `villagecompute.calendar.data.models` (NOT com.villagecompute.calendar.model)
    Use the actual package names from the existing codebase.

*   **Warning 2: BigDecimal Scale**
    Always use proper scale for money calculations:
    ```java
    BigDecimal rate = new BigDecimal("5.99").setScale(2, RoundingMode.HALF_UP);
    ```
    However, since you're using @ConfigProperty, Quarkus handles the scale automatically.

*   **Warning 3: Transactional Context**
    The OrderService.createOrder() method is already `@Transactional` (line 41). Your ShippingService.calculateShippingCost() should be a pure calculation method (no database writes, no side effects) so it's safe to call within the transaction.

### Architecture Constraints

*   **Quarkus Best Practices:**
    - Use `@ApplicationScoped` for stateless singleton services
    - Use `@ConfigProperty` for configuration injection (NOT @Value like Spring)
    - Follow CDI bean lifecycle patterns

*   **BigDecimal for Money:**
    - Always use `BigDecimal` for monetary amounts (NEVER double or float)
    - Use scale of 2 for USD currency
    - Use `RoundingMode.HALF_UP` for rounding when necessary

*   **Exception Handling:**
    - Throw `IllegalArgumentException` for invalid/missing address data
    - Throw `IllegalStateException` for unsupported shipping scenarios (international)
    - Use clear, descriptive error messages
    - Log exceptions before throwing

*   **Code Organization:**
    - Place ShippingService in `villagecompute.calendar.services` package
    - Place ShippingServiceTest in `villagecompute/calendar/services` test directory
    - Follow existing naming conventions (Service suffix, Test suffix)

### Suggested Implementation Order

1. **Add configuration to application.properties** (5 minutes)
   - Add shipping rate properties with environment variable fallback
   - Add comments explaining each rate tier

2. **Create ShippingService.java** (30 minutes)
   - Create class with @ApplicationScoped annotation
   - Inject configuration properties for rates
   - Implement calculateShippingCost(CalendarOrder order) method
   - Add address parsing and validation logic
   - Add country-based rate selection (US = domestic, else reject)
   - Add comprehensive logging

3. **Update OrderService.java** (10 minutes)
   - Inject ShippingService via @Inject
   - Update calculateShipping() method to call shippingService.calculateShippingCost(order)
   - Verify logging shows correct shipping amounts

4. **Create ShippingServiceTest.java** (45 minutes)
   - Test domestic US address returns standard rate ($5.99)
   - Test international address throws exception
   - Test null address throws exception
   - Test missing country field throws exception
   - Test edge cases (empty strings, special characters in address)
   - Use ObjectMapper to create test JsonNode addresses

5. **Manual Testing** (15 minutes)
   - Run the application
   - Create a test order via GraphQL or REST
   - Verify shipping cost is added to total
   - Check logs for shipping calculation messages

### Expected File Changes

**New Files:**
- `src/main/java/villagecompute/calendar/services/ShippingService.java` (~100 lines)
- `src/test/java/villagecompute/calendar/services/ShippingServiceTest.java` (~150 lines)

**Modified Files:**
- `src/main/resources/application.properties` (+4 lines for shipping config)
- `src/main/java/villagecompute/calendar/services/OrderService.java` (+2 lines: inject ShippingService, call it in calculateShipping())

**Total Implementation Time:** ~2 hours for a skilled Java developer

---

**End of Task Briefing Package**
