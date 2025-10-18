# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create EmailService for composing and sending transactional emails via SMTP (GoogleWorkspace initially). Implement email templates (HTML + plain text) for: order confirmation (sent on payment success), shipping notification (sent when order marked as SHIPPED with tracking number), order cancellation (sent when order cancelled). Configure JavaMail in application.properties (SMTP host, port, TLS, auth credentials from env variables). Create EmailJob (DelayedJob implementation) for async email sending. Implement email queueing via JobManager (enqueue EmailJob on order events). Add retry logic for failed email sends. Test with Mailpit (local SMTP server for development). Document email configuration in docs/guides/email-setup.md.

**Acceptance Criteria:**
- EmailService sends email successfully via GoogleWorkspace SMTP
- Email templates render with order data (order number, customer name, items)
- EmailJob enqueued when order status changes to PAID or SHIPPED
- Failed email sends retry 3 times with exponential backoff
- Mailpit receives emails during local development (SMTP localhost:1025)
- Email configuration guide tested with fresh GoogleWorkspace account

---

## Issues Detected

*   **Compilation Error:** The `CalendarOrder` entity is missing the `trackingNumber` field that is referenced in:
    - `ShippingNotificationJobHandler.java` lines 69, 72, 79 (validation and tracing)
    - `shippingNotification.html` line 20 (tracking number display)
    - `shippingNotification.txt` line 10 (tracking number display)

*   **Compilation Error:** The `CalendarOrder` entity is missing the `cancelledAt` field that is referenced in:
    - `orderCancellation.html` line 57 (cancelled date display)
    - `orderCancellation.txt` line 13 (cancelled date display)

*   **Missing Template:** The plain text template for order confirmation is missing:
    - Expected location: `src/main/resources/templates/OrderEmailJobHandler/orderConfirmation.txt`
    - This was listed in the target files but was not created

---

## Best Approach to Fix

You MUST perform the following steps to fix the compilation errors and complete the implementation:

### Step 1: Add Missing Fields to CalendarOrder Entity

Add the following fields to `src/main/java/villagecompute/calendar/data/models/CalendarOrder.java`:

1. **Add `trackingNumber` field** (after the `orderNumber` field around line 92):
```java
@Size(max = 255)
@Column(name = "tracking_number", length = 255)
public String trackingNumber;
```

2. **Add `cancelledAt` timestamp field** (after the `shippedAt` field around line 88):
```java
@Column(name = "cancelled_at")
public Instant cancelledAt;
```

3. **Update the `cancel()` helper method** to set the `cancelledAt` timestamp (around line 198):
```java
public void cancel() {
    this.status = STATUS_CANCELLED;
    this.cancelledAt = Instant.now();  // Add this line
    persist();
}
```

### Step 2: Create Missing Plain Text Template

Create the file `src/main/resources/templates/OrderEmailJobHandler/orderConfirmation.txt` with the following content structure (follow the same pattern as the existing shipping and cancellation plain text templates):

```text
THANK YOU FOR YOUR ORDER!
====================================

Hi {order.user.displayName ?: order.user.email},

We're excited to confirm that we've received your order for your custom calendar!

ORDER SUMMARY
------------------------------------
Order Number: {order.orderNumber}
Order Total: ${order.totalPrice}
Order Date: {order.paidAt.toString()}

CALENDAR DETAILS
------------------------------------
Calendar: {order.calendar.name} ({order.calendar.year})
Quantity: {order.quantity} {#if order.quantity > 1}calendars{#else}calendar{/if}
Unit Price: ${order.unitPrice}

SHIPPING ADDRESS
------------------------------------
{order.shippingAddress.get('name').asText()}
{order.shippingAddress.get('line1').asText()}
{#if order.shippingAddress.has('line2') && !order.shippingAddress.get('line2').isNull()}
{order.shippingAddress.get('line2').asText()}
{/if}
{order.shippingAddress.get('city').asText()}, {order.shippingAddress.get('state').asText()} {order.shippingAddress.get('postalCode').asText()}
{order.shippingAddress.get('country').asText()}

WHAT'S NEXT
------------------------------------
1. Order Confirmation: You're here! ✓
2. Production: We'll create your custom calendar (1-2 business days)
3. Shipping: Your order will be shipped (you'll receive tracking information)
4. Delivery: Enjoy your beautiful custom calendar!

Questions?
------------------------------------
Contact us at support@villagecompute.com

Village Compute Calendar
https://calendar.villagecompute.com
```

### Step 3: Create Database Migration (Optional but Recommended)

Since you're adding new fields to the `CalendarOrder` entity, you should create a Flyway migration to add these columns to the database. Create a new migration file `src/main/resources/db/migration/V1.X__add_order_tracking_and_cancelled_fields.sql` (replace X with the next version number):

```sql
-- Add tracking_number column to calendar_orders table
ALTER TABLE calendar_orders
ADD COLUMN tracking_number VARCHAR(255);

-- Add cancelled_at column to calendar_orders table
ALTER TABLE calendar_orders
ADD COLUMN cancelled_at TIMESTAMP;

-- Add index for tracking_number for faster lookups
CREATE INDEX idx_calendar_orders_tracking_number ON calendar_orders(tracking_number);
```

### Step 4: Verify the Fix

After making these changes:

1. **Compile the code:**
   ```bash
   ./mvnw clean compile
   ```
   This should complete without errors.

2. **Run the tests:**
   ```bash
   ./mvnw test
   ```
   All tests should pass.

3. **Verify template rendering:**
   - Ensure all 6 templates exist (3 HTML + 3 plain text)
   - Verify they follow the same Qute syntax patterns
   - Check that JSONB address fields use `.get()` and `.asText()` methods

### Important Notes

- The `trackingNumber` field is required for shipping notifications and should be set when updating an order to SHIPPED status via GraphQL mutation
- The `cancelledAt` field is automatically set by the `cancel()` helper method when an order is cancelled
- Both fields are nullable since they only apply to orders in specific states
- Make sure the plain text template uses the exact same Qute variable references as the HTML template to ensure consistency
