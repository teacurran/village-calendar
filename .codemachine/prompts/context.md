# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I3.T7",
  "iteration_id": "I3",
  "iteration_goal": "Implement complete e-commerce workflow including Stripe payment integration, order placement, order management dashboard (admin), and transactional email notifications",
  "description": "Create EmailService for composing and sending transactional emails via SMTP (GoogleWorkspace initially). Implement email templates (HTML + plain text) for: order confirmation (sent on payment success), shipping notification (sent when order marked as SHIPPED with tracking number), order cancellation (sent when order cancelled). Configure JavaMail in application.properties (SMTP host, port, TLS, auth credentials from env variables). Create EmailJob (DelayedJob implementation) for async email sending. Implement email queueing via JobManager (enqueue EmailJob on order events). Add retry logic for failed email sends. Test with Mailpit (local SMTP server for development). Document email configuration in docs/guides/email-setup.md.",
  "agent_type_hint": "BackendAgent",
  "inputs": "Email notification requirements from Plan Section \"Order Notifications\", JavaMail documentation, DelayedJob pattern from architecture plan",
  "target_files": [
    "src/main/java/villagecompute/calendar/integration/email/EmailService.java",
    "src/main/java/villagecompute/calendar/jobs/EmailJob.java",
    "src/main/resources/email-templates/order-confirmation.html",
    "src/main/resources/email-templates/order-confirmation.txt",
    "src/main/resources/email-templates/shipping-notification.html",
    "src/main/resources/email-templates/shipping-notification.txt",
    "src/main/resources/email-templates/order-cancellation.html",
    "src/main/resources/email-templates/order-cancellation.txt",
    "docs/guides/email-setup.md"
  ],
  "input_files": [
    "src/main/resources/application.properties",
    "src/main/java/villagecompute/calendar/model/DelayedJob.java",
    "src/main/java/villagecompute/calendar/service/OrderService.java"
  ],
  "deliverables": "EmailService with template rendering and SMTP sending, EmailJob for async email processing, Email templates (HTML + plain text) for all order events, Email queueing integrated into OrderService, Retry logic for failed sends (DelayedJob retry mechanism), Email configuration guide",
  "acceptance_criteria": "EmailService sends email successfully via GoogleWorkspace SMTP, Email templates render with order data (order number, customer name, items), EmailJob enqueued when order status changes to PAID or SHIPPED, Failed email sends retry 3 times with exponential backoff, Mailpit receives emails during local development (SMTP localhost:1025), Email configuration guide tested with fresh GoogleWorkspace account",
  "dependencies": ["I3.T2"],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Email Notification System Design

**Email Notification Architecture:**

The Village Calendar application requires a robust transactional email system for order-related communications. The system must support:

1. **Transactional Email Types:**
   - **Order Confirmation**: Sent immediately when payment is captured (order status changes to PAID)
   - **Shipping Notification**: Sent when order is marked as SHIPPED with tracking information
   - **Order Cancellation**: Sent when order is cancelled before fulfillment

2. **Delivery Method:**
   - SMTP delivery via Google Workspace (GoogleWorkspace SMTP servers)
   - Configuration via environment variables for security
   - Support for both HTML and plain text email formats
   - TLS/STARTTLS encryption required

3. **Asynchronous Processing:**
   - Emails must be sent asynchronously using DelayedJob system
   - Jobs queued via Vert.x EventBus for distributed processing
   - Retry logic for transient failures (network issues, SMTP timeouts)
   - Failed jobs should retry with exponential backoff

4. **Email Content Requirements:**
   - Professional HTML templates with inline CSS (for email client compatibility)
   - Plain text fallback for email clients that don't support HTML
   - Dynamic content rendering (order details, customer info, tracking numbers)
   - Branded templates with company logo and contact information

5. **Development/Testing:**
   - Mailpit for local development testing (SMTP mock server)
   - Email mock mode in development to prevent accidental sends
   - Configuration toggles for test vs production SMTP servers

### Context: DelayedJob Queue System

**Job Processing Pattern:**

The DelayedJob system provides asynchronous, fault-tolerant job processing:

1. **Job Lifecycle:**
   ```
   CREATE → PENDING → IN_PROGRESS → COMPLETED
                                 ↘ FAILED (with retry)
   ```

2. **Job Attributes:**
   - `actorId`: Entity ID the job operates on (e.g., CalendarOrder ID)
   - `queue`: Job queue type (EMAIL_ORDER_CONFIRMATION, EMAIL_SHIPPING_NOTIFICATION, etc.)
   - `priority`: Higher priority jobs processed first
   - `attempts`: Number of retry attempts (max 3)
   - `runAt`: When to execute the job
   - `locked`: Job lock for preventing duplicate processing
   - `complete`: Job completion flag
   - `failureReason`: Error message if job failed

3. **Retry Strategy:**
   - Exponential backoff: 1 minute, 5 minutes, 15 minutes
   - Maximum 3 retry attempts
   - Job marked as permanently failed after exhausting retries
   - Failure details logged to `lastError` and `failureReason` fields

4. **Job Handler Contract:**
   ```java
   public interface DelayedJobHandler {
       void run(String actorId) throws Exception;
   }
   ```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

#### File: `src/main/java/villagecompute/calendar/services/EmailService.java`

**Summary:** **ALREADY FULLY IMPLEMENTED** - Complete email service with HTML and text email sending via Quarkus Mailer.

**Current Implementation Status:**

✅ **Core Email Methods:**
- `sendEmail(to, subject, body)` - Lines 57-59: Simple text email with default from address
- `sendEmail(from, to, subject, body)` - Lines 69-81: Text email with custom from address
- `sendHtmlEmail(to, subject, htmlBody)` - Lines 90-92: HTML email with default from address
- `sendHtmlEmail(from, to, subject, htmlBody)` - Lines 102-114: HTML email with custom from address

✅ **Environment Features:**
- Environment prefix for non-production emails (lines 43-48): `[DEV] Order Confirmation`
- Production detection via `quarkus.profile` config (lines 33-35)
- Default from email configurable (line 22-23): `quarkus.mailer.from`

✅ **Error Handling:**
- Try-catch blocks with logging (lines 75-80, 108-113)
- RuntimeException thrown on email failures
- Detailed error logging with recipient information

**CRITICAL NOTE:** The EmailService class is COMPLETE. You do NOT need to create or modify it.

#### File: `src/main/java/villagecompute/calendar/services/jobs/OrderEmailJobHandler.java`

**Summary:** **ALREADY FULLY IMPLEMENTED** - Complete DelayedJob handler for order confirmation emails using Qute templates.

**Current Implementation Status:**

✅ **Job Handler Implementation:**
- Implements `DelayedJobHandler` interface (line 24)
- `run(String actorId)` method (lines 56-94): Complete job execution logic

✅ **Email Template Rendering:**
- Uses Qute `@CheckedTemplate` for type-safe templates (lines 40-53)
- `Templates.orderConfirmation(order, stylesheet)` - Line 79: Renders HTML template
- Loads CSS from `src/main/resources/css/email.css` (line 75)

✅ **Error Handling:**
- Order not found check (lines 63-66): Throws `DelayedJobException(true, ...)` - permanent failure
- Resource loading failure (lines 87-89): `DelayedJobException(true, ...)` - permanent failure
- Email send failure (lines 90-93): `DelayedJobException(false, ...)` - **retryable failure**

✅ **OpenTelemetry Tracing:**
- `@WithSpan` annotation (line 56) for distributed tracing
- Custom span attributes for order ID, user email, status (lines 69-71)
- Span events for successful email send (line 85)

**Key Patterns:**
```java
// Permanent failure (no retry):
throw new DelayedJobException(true, "Order not found: " + actorId);

// Retryable failure (will retry with backoff):
throw new DelayedJobException(false, "Failed to send email", e);
```

**CRITICAL NOTE:** OrderEmailJobHandler is COMPLETE and implements the order confirmation email workflow. You need to create similar handlers for **shipping notification** and **order cancellation** emails.

#### File: `src/main/resources/templates/OrderEmailJobHandler/orderConfirmation.html`

**Summary:** Complete Qute template for order confirmation emails with professional styling.

**Template Features:**
- Responsive HTML email design (lines 1-84)
- Embedded CSS via `{stylesheet}` variable (line 8)
- Dynamic order data rendering using Qute expressions:
  - `{order.user.displayName ?: order.user.email}` - Line 15: User name with fallback
  - `{order.totalPrice}` - Line 20: Order total
  - `{order.calendar.name} ({order.calendar.year})` - Line 28: Calendar details
  - `{order.quantity}` - Line 33: Quantity with plural handling
  - `{order.shippingAddress.get('name').asText()}` - Line 49: Address parsing from JSONB

**Address Handling Pattern:**
```html
{order.shippingAddress.get('line1').asText()}<br>
{#if order.shippingAddress.has('line2') && !order.shippingAddress.get('line2').isNull()}
    {order.shippingAddress.get('line2').asText()}<br>
{/if}
```

**CRITICAL NOTE:** This template demonstrates the correct pattern for rendering JSONB data. You MUST use the same pattern for shipping and cancellation templates.

#### File: `src/main/resources/css/email.css`

**Summary:** Complete email stylesheet with inline-friendly CSS for maximum email client compatibility.

**Styling Conventions:**
- Outer wrapper: Max-width 600px, white background (lines 14-19)
- Headers: Blue gradient color scheme (`#3b82f6`, `#1e40af`)
- Detail boxes: Light gray background (`#f9fafb`) with blue left border (lines 49-61)
- Highlight box: Blue gradient background for important info (lines 63-76)
- Buttons: Blue with hover state (lines 78-91)
- Footer: Muted text with top border (lines 93-109)
- Shipping box: Green accents for shipping info (lines 110-116)

**CRITICAL NOTE:** Reuse this stylesheet for all email templates. The styles are designed for compatibility with email clients (Gmail, Outlook, Apple Mail).

#### File: `src/main/resources/application.properties`

**Summary:** Configuration file with Quarkus Mailer settings already configured.

**Mailer Configuration (lines 106-113):**
```properties
quarkus.mailer.from=${MAIL_FROM:noreply@villagecompute.com}
quarkus.mailer.host=${MAIL_HOST:smtp.example.com}
quarkus.mailer.port=${MAIL_PORT:587}
quarkus.mailer.start-tls=REQUIRED
quarkus.mailer.username=${MAIL_USERNAME:placeholder}
quarkus.mailer.password=${MAIL_PASSWORD:placeholder}
quarkus.mailer.mock=${MAIL_MOCK:true}
```

**Key Settings:**
- `quarkus.mailer.mock=true` by default: Prevents accidental email sends in dev
- All credentials from environment variables: `MAIL_FROM`, `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD`
- STARTTLS encryption: `start-tls=REQUIRED` (line 110)
- Port 587: Standard SMTP submission port

**CRITICAL NOTE:** Configuration is COMPLETE. You only need to document the environment variables in email-setup.md.

#### File: `src/main/java/villagecompute/calendar/data/models/DelayedJob.java`

**Summary:** JPA entity for async job queue with Panache active record pattern.

**Key Methods:**
- `createDelayedJob(actorId, queue, runAt)` - Lines 85-93: Factory method to create and persist jobs
- `unlock()` - Lines 98-101: Unlock job for retry after failure
- `findReadyToRun(limit)` - Lines 109-113: Query for pending jobs ready to execute

**Job Fields:**
- `actorId` (String): Entity ID to process (e.g., CalendarOrder ID as UUID string)
- `queue` (DelayedJobQueue enum): Job type (EMAIL_ORDER_CONFIRMATION, EMAIL_SHIPPING_NOTIFICATION)
- `priority` (Integer): Higher numbers = higher priority (set from queue default)
- `attempts` (Integer): Number of execution attempts (max 3)
- `locked` (Boolean): Prevents duplicate processing
- `complete` (Boolean): Job completion flag
- `failureReason` (String): Error message for permanently failed jobs

**CRITICAL NOTE:** Use the factory method `DelayedJob.createDelayedJob()` to create jobs. Do NOT construct DelayedJob objects manually.

#### File: `src/main/java/villagecompute/calendar/data/models/DelayedJobQueue.java`

**Summary:** Enum defining job queue types and their priorities.

**Existing Queues:**
- `EMAIL_ORDER_CONFIRMATION(10)` - High priority order confirmation emails
- `EMAIL_SHIPPING_NOTIFICATION(10)` - High priority shipping notification emails
- `EMAIL_GENERAL(5)` - Lower priority general emails

**CRITICAL NOTE:** The required email queue types ALREADY EXIST. You do NOT need to add new enum values. The task description mentions creating "EmailJob" but this likely refers to the handler classes, not queue types.

#### File: `src/main/java/villagecompute/calendar/services/OrderService.java`

**Summary:** Order management service with placeholder TODOs for email integration.

**Email Integration Points:**

**Payment Success** (after line 132-136):
```java
if (CalendarOrder.STATUS_PAID.equals(newStatus)) {
    order.paidAt = Instant.now();
    // TODO I3.T7: Enqueue order confirmation email job here
}
```

**Order Shipped** (after line 134-136):
```java
else if (CalendarOrder.STATUS_SHIPPED.equals(newStatus)) {
    order.shippedAt = Instant.now();
    // TODO I3.T7: Enqueue shipping notification email job here
}
```

**Order Cancelled** (in `cancelOrder` method after line 241):
```java
order.cancel();
// TODO I3.T7: Enqueue order cancellation email job here
```

**CRITICAL NOTE:** You MUST integrate email job enqueueing into OrderService at these three integration points.

### Implementation Tips & Notes

**Tip 1: Email Job Handler Pattern**

You need to create TWO new job handlers following the OrderEmailJobHandler pattern:

1. **ShippingNotificationJobHandler.java**
   - Similar structure to OrderEmailJobHandler
   - Template method: `Templates.shippingNotification(order, stylesheet)`
   - Include tracking number in email body
   - Template location: `src/main/resources/templates/ShippingNotificationJobHandler/shippingNotification.html`

2. **OrderCancellationJobHandler.java**
   - Similar structure to OrderEmailJobHandler
   - Template method: `Templates.orderCancellation(order, stylesheet)`
   - Include cancellation reason from order notes
   - Template location: `src/main/resources/templates/OrderCancellationJobHandler/orderCancellation.html`

**Pattern to Follow:**
```java
@ApplicationScoped
public class ShippingNotificationJobHandler implements DelayedJobHandler {

    @Inject
    EmailService emailService;

    @ConfigProperty(name = "email.order.from", defaultValue = "Village Compute Calendar <orders@villagecompute.com>")
    String orderFromEmail;

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance shippingNotification(
            CalendarOrder order,
            String stylesheet
        );
    }

    @Override
    @WithSpan("ShippingNotificationJobHandler.run")
    public void run(String actorId) throws Exception {
        // Load order, render template, send email
        // Same pattern as OrderEmailJobHandler
    }
}
```

**Tip 2: Email Template Content**

**Shipping Notification Email Should Include:**
- Order number and tracking number (prominent highlight box)
- "Your order has shipped!" header with 📦 emoji
- Shipping carrier and estimated delivery date
- Order summary (calendar name, quantity)
- Shipping address confirmation
- Link to tracking page (e.g., `https://tracking.example.com/{trackingNumber}`)
- Customer support contact information

**Order Cancellation Email Should Include:**
- "Order Cancelled" header with appropriate tone
- Order number and cancellation confirmation
- Cancellation reason (extract from order.notes field)
- Refund information (if order was paid)
- Expected refund timeline (e.g., "5-10 business days")
- Customer support contact for questions
- Link to browse other templates (encourage re-engagement)

**Tip 3: Plain Text Template Pattern**

The task requires both HTML and plain text versions. Create `.txt` templates alongside `.html`:

**Example:** `orderConfirmation.txt`
```text
Thank You for Your Order!

Hi {order.user.displayName ?: order.user.email},

We're excited to confirm that we've received your order for your custom calendar!

Order Total: ${order.totalPrice}
Order ID: {order.id}

ORDER DETAILS
----------------------------
Calendar: {order.calendar.name} ({order.calendar.year})
Quantity: {order.quantity}
Unit Price: ${order.unitPrice}
Order Date: {order.paidAt.toString()}

SHIPPING ADDRESS
----------------------------
{order.shippingAddress.get('name').asText()}
{order.shippingAddress.get('line1').asText()}
...

Questions? Contact support@villagecompute.com

Village Compute Calendar
https://calendar.villagecompute.com
```

**CRITICAL:** Plain text templates should use the SAME Qute template directory structure, just with `.txt` extension instead of `.html`.

**Tip 4: Job Enqueueing Integration**

You MUST integrate email job creation into OrderService. Add a helper method:

```java
private void enqueueEmailJob(CalendarOrder order, DelayedJobQueue queue) {
    DelayedJob emailJob = DelayedJob.createDelayedJob(
        order.id.toString(),  // actorId
        queue,                 // EMAIL_ORDER_CONFIRMATION, EMAIL_SHIPPING_NOTIFICATION, etc.
        Instant.now()          // Run immediately
    );
    LOG.infof("Enqueued %s email job for order %s", queue, order.id);
}
```

Then call this method at the appropriate integration points:
```java
if (CalendarOrder.STATUS_PAID.equals(newStatus)) {
    order.paidAt = Instant.now();
    enqueueEmailJob(order, DelayedJobQueue.EMAIL_ORDER_CONFIRMATION);
}
```

**Tip 5: DelayedJob Worker Registration**

The JobWorker (from task I4.T1, not yet implemented) will need to map queue types to handlers. You should document this mapping pattern, but the actual JobWorker implementation is outside the scope of I3.T7.

**Expected Pattern (for documentation):**
```java
// In JobWorker class (I4.T1)
Map<DelayedJobQueue, DelayedJobHandler> handlers = Map.of(
    DelayedJobQueue.EMAIL_ORDER_CONFIRMATION, orderEmailJobHandler,
    DelayedJobQueue.EMAIL_SHIPPING_NOTIFICATION, shippingNotificationJobHandler,
    DelayedJobQueue.EMAIL_GENERAL, orderCancellationJobHandler
);
```

**Tip 6: Email Configuration Documentation**

The `docs/guides/email-setup.md` file must include:

1. **Prerequisites:**
   - Google Workspace account with SMTP enabled
   - Application-specific password (not regular Google password)
   - Mailpit for local testing (Docker installation instructions)

2. **Environment Variables:**
   ```bash
   # Production (Google Workspace)
   export MAIL_FROM="Village Calendar <noreply@villagecompute.com>"
   export MAIL_HOST="smtp.gmail.com"
   export MAIL_PORT="587"
   export MAIL_USERNAME="noreply@villagecompute.com"
   export MAIL_PASSWORD="app-specific-password-here"
   export MAIL_MOCK="false"

   # Development (Mailpit)
   export MAIL_FROM="dev@localhost"
   export MAIL_HOST="localhost"
   export MAIL_PORT="1025"
   export MAIL_USERNAME=""
   export MAIL_PASSWORD=""
   export MAIL_MOCK="true"
   ```

3. **Google Workspace Setup Steps:**
   - Enable IMAP/SMTP in Gmail settings
   - Create app-specific password (requires 2FA)
   - Test connection with sample email

4. **Mailpit Setup:**
   ```bash
   docker run -d --name mailpit -p 1025:1025 -p 8025:8025 axllent/mailpit
   # Web UI: http://localhost:8025
   # SMTP: localhost:1025
   ```

5. **Testing Email Templates:**
   - Run application in dev mode with Mailpit
   - Trigger order events (payment, shipment, cancellation)
   - Check Mailpit UI to verify email rendering

6. **Troubleshooting:**
   - Common SMTP errors (authentication, TLS)
   - Checking Quarkus logs for email failures
   - Verifying DelayedJob retry behavior

**Tip 7: Error Handling and Retry Logic**

The DelayedJobHandler pattern already supports retry logic via `DelayedJobException`:

- **Permanent failures** (should NOT retry):
  - Order not found: `throw new DelayedJobException(true, "Order not found")`
  - Invalid order data: `throw new DelayedJobException(true, "Missing required field")`

- **Transient failures** (SHOULD retry):
  - SMTP timeout: `throw new DelayedJobException(false, "SMTP timeout", e)`
  - Network error: `throw new DelayedJobException(false, "Connection failed", e)`
  - Email send failure: `throw new DelayedJobException(false, "Failed to send", e)`

The DelayedJob system will automatically retry transient failures with exponential backoff (1min, 5min, 15min).

**Tip 8: Tracking Number Field**

The `CalendarOrder` entity (from I3.T2) includes a `trackingNumber` field (see GraphQL schema line 293-294). Your shipping notification template MUST include this field:

```html
<div class="highlight-box">
    <strong>Tracking Number: {order.trackingNumber}</strong>
    <p style="margin: 0; color: white;">
        <a href="https://tracking.example.com/{order.trackingNumber}"
           style="color: white; text-decoration: underline;">
            Track Your Shipment
        </a>
    </p>
</div>
```

**Tip 9: Order Notes for Cancellation Reason**

The cancellation template should extract the cancellation reason from `order.notes`:

```html
<p>
    Your order has been cancelled for the following reason:
</p>
<div class="detail-box">
    {#if order.notes}
        {order.notes}
    {#else}
        No reason provided.
    {/if}
</div>
```

**Tip 10: Testing Strategy**

1. **Unit Tests** (not required by task, but recommended):
   - Test each handler's template rendering
   - Mock EmailService to verify correct calls
   - Test error handling (order not found, email failure)

2. **Integration Tests** (not required by task):
   - Create test orders in different statuses
   - Enqueue email jobs
   - Verify jobs execute and email sent

3. **Manual Testing** (required by acceptance criteria):
   - Start Mailpit: `docker run -d -p 1025:1025 -p 8025:8025 axllent/mailpit`
   - Set `MAIL_MOCK=true` in development
   - Trigger order events via GraphQL mutations
   - Check Mailpit UI (http://localhost:8025) for received emails
   - Verify HTML and plain text rendering

**Warning: Job Enqueueing Transaction Safety**

⚠️ **IMPORTANT:** Email job enqueueing MUST happen within the same transaction as the order status update. This ensures consistency - if the status update fails, the email job is not created.

The existing `updateOrderStatus` and `cancelOrder` methods in OrderService are already annotated with `@Transactional`. Your email job creation calls MUST be within these methods, NOT in separate transactions.

**Correct Pattern:**
```java
@Transactional
public CalendarOrder updateOrderStatus(...) {
    // ... update order status ...
    order.persist();  // Persist order changes

    // Enqueue email job in SAME transaction
    if (CalendarOrder.STATUS_PAID.equals(newStatus)) {
        enqueueEmailJob(order, DelayedJobQueue.EMAIL_ORDER_CONFIRMATION);
    }

    return order;  // Transaction commits here (both order and job persisted atomically)
}
```

**Warning: Email Template Directory Structure**

The Qute template engine expects templates in:
```
src/main/resources/templates/{HandlerClassName}/{templateMethodName}.{extension}
```

For example:
- Handler: `OrderEmailJobHandler`
- Template method: `orderConfirmation(order, stylesheet)`
- Location: `src/main/resources/templates/OrderEmailJobHandler/orderConfirmation.html`

You MUST create subdirectories matching the handler class names:
```
src/main/resources/templates/
├── OrderEmailJobHandler/
│   ├── orderConfirmation.html
│   └── orderConfirmation.txt
├── ShippingNotificationJobHandler/
│   ├── shippingNotification.html
│   └── shippingNotification.txt
└── OrderCancellationJobHandler/
    ├── orderCancellation.html
    └── orderCancellation.txt
```

**Warning: CSS Loading in Templates**

All templates must load the CSS stylesheet using the exact same pattern as OrderEmailJobHandler:

```java
// In handler class:
String css = loadResourceAsString("css/email.css");
String htmlContent = Templates.shippingNotification(order, css).render();

// In template:
<style>
    {stylesheet}
</style>
```

Do NOT hardcode CSS in templates. Always pass it as a parameter for consistency and maintainability.

---

## Summary: What You Need to Do

Based on the codebase analysis, here's what is ALREADY DONE vs what you MUST IMPLEMENT:

### ✅ Already Complete (Do NOT Modify):
1. `EmailService.java` - Fully implemented
2. `OrderEmailJobHandler.java` - Complete order confirmation email handler
3. `orderConfirmation.html` - Complete HTML template
4. `email.css` - Complete stylesheet
5. `DelayedJob.java` - Job queue entity
6. `DelayedJobQueue.java` - Queue types (includes all required email queues)
7. `application.properties` - Mailer configuration complete

### ❌ You MUST Implement:

1. **Create Shipping Notification Handler:**
   - File: `src/main/java/villagecompute/calendar/services/jobs/ShippingNotificationJobHandler.java`
   - Copy pattern from OrderEmailJobHandler
   - Include tracking number prominently
   - Add OpenTelemetry tracing

2. **Create Order Cancellation Handler:**
   - File: `src/main/java/villagecompute/calendar/services/jobs/OrderCancellationJobHandler.java`
   - Copy pattern from OrderEmailJobHandler
   - Include cancellation reason from notes
   - Add refund information if order was paid

3. **Create Email Templates (6 files total):**
   - `src/main/resources/templates/ShippingNotificationJobHandler/shippingNotification.html`
   - `src/main/resources/templates/ShippingNotificationJobHandler/shippingNotification.txt`
   - `src/main/resources/templates/OrderCancellationJobHandler/orderCancellation.html`
   - `src/main/resources/templates/OrderCancellationJobHandler/orderCancellation.txt`
   - **NOTE:** Do NOT create templates in `src/main/resources/email-templates/` as specified in target_files. The correct location is under `src/main/resources/templates/{HandlerName}/` to match Qute conventions. The OrderEmailJobHandler already sets the correct pattern.

4. **Integrate Email Job Enqueueing into OrderService:**
   - Add helper method `enqueueEmailJob(order, queue)`
   - Call from `updateOrderStatus()` when status = PAID (order confirmation)
   - Call from `updateOrderStatus()` when status = SHIPPED (shipping notification)
   - Call from `cancelOrder()` after status change (cancellation email)
   - All calls MUST be within existing `@Transactional` methods

5. **Create Email Setup Documentation:**
   - File: `docs/guides/email-setup.md`
   - Include Google Workspace setup instructions
   - Include Mailpit setup for development
   - Document all environment variables
   - Add troubleshooting section

6. **Manual Testing with Mailpit:**
   - Verify emails sent successfully
   - Check HTML and plain text rendering
   - Confirm retry behavior for failures

### Verification Checklist:

- [ ] ShippingNotificationJobHandler implements DelayedJobHandler
- [ ] OrderCancellationJobHandler implements DelayedJobHandler
- [ ] Both handlers follow OrderEmailJobHandler pattern exactly
- [ ] 6 email templates created (3 HTML + 3 plain text)
- [ ] Templates use Qute expressions for dynamic data
- [ ] Templates reuse email.css stylesheet
- [ ] OrderService.enqueueEmailJob() helper method added
- [ ] Email jobs enqueued on order PAID status
- [ ] Email jobs enqueued on order SHIPPED status
- [ ] Email jobs enqueued on order cancellation
- [ ] email-setup.md documentation complete
- [ ] Mailpit testing shows emails received
- [ ] HTML emails render correctly in email clients
- [ ] Plain text fallback works
