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
    "src/main/java/com/villagecompute/calendar/integration/email/EmailService.java",
    "src/main/java/com/villagecompute/calendar/jobs/EmailJob.java",
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
    "src/main/java/com/villagecompute/calendar/model/DelayedJob.java",
    "src/main/java/com/villagecompute/calendar/service/OrderService.java"
  ],
  "deliverables": "EmailService with template rendering and SMTP sending, EmailJob for async email processing, Email templates (HTML + plain text) for all order events, Email queueing integrated into OrderService, Retry logic for failed sends (DelayedJob retry mechanism), Email configuration guide",
  "acceptance_criteria": "EmailService sends email successfully via GoogleWorkspace SMTP, Email templates render with order data (order number, customer name, items), EmailJob enqueued when order status changes to PAID or SHIPPED, Failed email sends retry 3 times with exponential backoff, Mailpit receives emails during local development (SMTP localhost:1025), Email configuration guide tested with fresh GoogleWorkspace account",
  "dependencies": ["I3.T2"],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Logging & Monitoring (from 05_Operational_Architecture.md)

```markdown
<!-- anchor: logging-monitoring -->
#### 3.8.2. Logging & Monitoring

**Logging Strategy:**

**Structured Logging (JSON Format):**

All application logs are emitted as structured JSON to enable efficient parsing, filtering, and aggregation by observability tools.

**Example Log Entry:**
```json
{
  "timestamp": "2025-10-16T14:32:15.123Z",
  "level": "INFO",
  "logger": "com.villagecompute.calendar.service.OrderService",
  "message": "Order placed successfully",
  "context": {
    "userId": 12345,
    "orderId": 67890,
    "total": 29.99,
    "paymentMethod": "card"
  },
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId": "00f067aa0ba902b7"
}
```

**Log Levels:**
- **ERROR**: Application errors requiring immediate attention (payment failures, database connection loss, job crashes)
- **WARN**: Degraded operation or unexpected behavior (high retry count, deprecated API usage, slow queries >1s)
- **INFO**: Significant business events (order placed, user registered, PDF generated, email sent)
- **DEBUG**: Detailed troubleshooting information (GraphQL query details, external API request/response, job queue polling)
- **TRACE**: Very verbose (SQL queries, method entry/exit) - enabled only in development
```

### Context: Task 3.7 - Email Notification System (from 02_Iteration_I3.md)

```markdown
<!-- anchor: task-i3-t7 -->
### Task 3.7: Implement Email Notification System

**Task ID:** `I3.T7`

**Description:**
Create EmailService for composing and sending transactional emails via SMTP (GoogleWorkspace initially). Implement email templates (HTML + plain text) for: order confirmation (sent on payment success), shipping notification (sent when order marked as SHIPPED with tracking number), order cancellation (sent when order cancelled). Configure JavaMail in application.properties (SMTP host, port, TLS, auth credentials from env variables). Create EmailJob (DelayedJob implementation) for async email sending. Implement email queueing via JobManager (enqueue EmailJob on order events). Add retry logic for failed email sends. Test with Mailpit (local SMTP server for development). Document email configuration in docs/guides/email-setup.md.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- Email notification requirements from Plan Section "Order Notifications"
- JavaMail documentation
- DelayedJob pattern from architecture plan

**Input Files:**
- `src/main/resources/application.properties`
- `src/main/java/com/villagecompute/calendar/model/DelayedJob.java`
- `src/main/java/com/villagecompute/calendar/service/OrderService.java`

**Target Files:**
- `src/main/java/com/villagecompute/calendar/integration/email/EmailService.java`
- `src/main/java/com/villagecompute/calendar/jobs/EmailJob.java`
- `src/main/resources/email-templates/order-confirmation.html`
- `src/main/resources/email-templates/order-confirmation.txt`
- `src/main/resources/email-templates/shipping-notification.html`
- `src/main/resources/email-templates/shipping-notification.txt`
- `src/main/resources/email-templates/order-cancellation.html`
- `src/main/resources/email-templates/order-cancellation.txt`
- `docs/guides/email-setup.md`

**Deliverables:**
- EmailService with template rendering and SMTP sending
- EmailJob for async email processing
- Email templates (HTML + plain text) for all order events
- Email queueing integrated into OrderService
- Retry logic for failed sends (DelayedJob retry mechanism)
- Email configuration guide

**Acceptance Criteria:**
- EmailService sends email successfully via GoogleWorkspace SMTP
- Email templates render with order data (order number, customer name, items)
- EmailJob enqueued when order status changes to PAID or SHIPPED
- Failed email sends retry 3 times with exponential backoff
- Mailpit receives emails during local development (SMTP localhost:1025)
- Email configuration guide tested with fresh GoogleWorkspace account

**Dependencies:** `I3.T2` (OrderService for integration points)

**Parallelizable:** Yes (can develop concurrently with order workflow)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### ⚠️ CRITICAL DISCOVERY: This Task is ~80% Complete!

**Investigation Summary:** Upon examining the codebase, I discovered that the email notification system has already been substantially implemented. Here's the actual status:

### ✅ COMPLETED Components (Already Exist):

*   **File:** `src/main/java/villagecompute/calendar/services/EmailService.java` - **FULLY IMPLEMENTED**
    *   **Summary:** Complete email service using Quarkus Mailer (NOT JavaMail directly). Supports both text and HTML emails with environment-aware subject prefixes.
    *   **Methods:** `sendEmail()`, `sendHtmlEmail()` with from/to/subject/body parameters
    *   **Features:** Environment prefix for non-prod emails (e.g., "[DEV] Order Confirmation")
    *   **Recommendation:** DO NOT create a new EmailService. This implementation is production-ready. Quarkus Mailer handles SMTP configuration automatically.

*   **File:** `src/main/java/villagecompute/calendar/data/models/DelayedJob.java` - **COMPLETE**
    *   **Summary:** Job entity with retry logic, priority queuing, and factory methods
    *   **Key Method:** `createDelayedJob(actorId, queue, runAt)` - Creates and persists jobs
    *   **Recommendation:** Use this entity exactly as-is for email job creation

*   **File:** `src/main/java/villagecompute/calendar/data/models/DelayedJobQueue.java` - **COMPLETE**
    *   **Summary:** Enum with all required email queues already defined:
        - `EMAIL_ORDER_CONFIRMATION(10)` - High priority
        - `EMAIL_SHIPPING_NOTIFICATION(10)` - High priority
        - `EMAIL_GENERAL(5)` - Medium priority (for cancellations)
    *   **Recommendation:** NO changes needed. All email queues exist.

*   **File:** `src/main/java/villagecompute/calendar/services/jobs/OrderEmailJobHandler.java` - **FULLY IMPLEMENTED**
    *   **Summary:** Complete job handler for order confirmation emails using Qute templates
    *   **Pattern:** Uses @CheckedTemplate for type-safe templates, loads CSS from resources, sends HTML email via EmailService
    *   **Location:** Templates in `src/main/resources/templates/OrderEmailJobHandler/`
    *   **Recommendation:** Use this as the exact pattern for implementing OrderCancellationJobHandler

*   **File:** `src/main/java/villagecompute/calendar/services/jobs/ShippingNotificationJobHandler.java` - **FULLY IMPLEMENTED**
    *   **Summary:** Complete job handler for shipping notifications with tracking number validation
    *   **Location:** Templates in `src/main/resources/templates/ShippingNotificationJobHandler/`
    *   **Recommendation:** Reference this for cancellation handler implementation

*   **Email Templates - Order Confirmation** - **BOTH HTML AND TEXT EXIST**
    *   **Files:**
        - `src/main/resources/templates/OrderEmailJobHandler/orderConfirmation.html`
        - `src/main/resources/templates/OrderEmailJobHandler/orderConfirmation.txt`
    *   **Summary:** Professional HTML email with inline CSS, order details, shipping address, next steps
    *   **Recommendation:** These are production-ready. Do not modify unless improving design.

*   **Email Templates - Shipping Notification** - **BOTH HTML AND TEXT EXIST**
    *   **Files:**
        - `src/main/resources/templates/ShippingNotificationJobHandler/shippingNotification.html`
        - `src/main/resources/templates/ShippingNotificationJobHandler/shippingNotification.txt`
    *   **Recommendation:** Production-ready shipping notification templates exist.

*   **OrderService Integration** - **FULLY IMPLEMENTED**
    *   **File:** `src/main/java/villagecompute/calendar/services/OrderService.java`
    *   **Methods:**
        - `enqueueEmailJob(order, queue)` (lines 316-323) - Creates DelayedJob for emails
        - `updateOrderStatus()` (lines 141-145) - Enqueues EMAIL_ORDER_CONFIRMATION on PAID
        - `updateOrderStatus()` (lines 143-145) - Enqueues EMAIL_SHIPPING_NOTIFICATION on SHIPPED
    *   **Recommendation:** Email job enqueueing is COMPLETE. Only cancellation email needs to be added (line 253).

*   **SMTP Configuration** - **COMPLETE**
    *   **File:** `src/main/resources/application.properties`
    *   **Config:**
        ```properties
        quarkus.mailer.from=${MAIL_FROM:noreply@villagecompute.com}
        quarkus.mailer.host=${MAIL_HOST:smtp.example.com}
        quarkus.mailer.port=${MAIL_PORT:587}
        quarkus.mailer.start-tls=REQUIRED
        quarkus.mailer.username=${MAIL_USERNAME:placeholder}
        quarkus.mailer.password=${MAIL_PASSWORD:placeholder}
        quarkus.mailer.mock=${MAIL_MOCK:true}
        ```
    *   **Recommendation:** SMTP configuration is complete with environment variable support. No changes needed.

*   **CSS Stylesheet** - **EXISTS**
    *   **File:** `src/main/resources/css/email.css`
    *   **Summary:** Common email styles loaded by all handlers via `loadResourceAsString()`
    *   **Recommendation:** Reuse this CSS for cancellation emails

### ❌ MISSING Components (To Be Implemented):

1. **OrderCancellationJobHandler** - **NOT IMPLEMENTED**
    *   **Required:** `src/main/java/villagecompute/calendar/services/jobs/OrderCancellationJobHandler.java`
    *   **Pattern:** Copy OrderEmailJobHandler.java, modify for cancellation logic
    *   **Key Differences:**
        - Template name: `orderCancellation` instead of `orderConfirmation`
        - Subject: "Order Cancelled - Village Compute Calendar"
        - Add cancellation reason to template data
        - Include refund processing note (if order was PAID)

2. **Order Cancellation Email Templates** - **NOT IMPLEMENTED**
    *   **Required:**
        - `src/main/resources/templates/OrderCancellationJobHandler/orderCancellation.html`
        - `src/main/resources/templates/OrderCancellationJobHandler/orderCancellation.txt`
    *   **Content:** Order details, cancellation reason, refund info (if applicable), support contact

3. **OrderService.cancelOrder() Email Integration** - **PARTIALLY IMPLEMENTED**
    *   **Current:** Line 253 enqueues EMAIL_GENERAL queue
    *   **Required:** Change to specific cancellation queue or handler
    *   **Fix:** Update to use EMAIL_GENERAL with OrderCancellationJobHandler

4. **Email Setup Documentation** - **NOT IMPLEMENTED**
    *   **Required:** `docs/guides/email-setup.md`
    *   **Content:**
        - How to configure GoogleWorkspace SMTP
        - Environment variables required (MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD)
        - Mailpit setup for local testing (Docker compose example)
        - Testing email templates locally
        - Troubleshooting common SMTP issues

### Implementation Recommendations

**RECOMMENDED APPROACH: Minimal Implementation**

Since 80% of the work is done, you should:

1. **Create OrderCancellationJobHandler** (30 minutes)
   - Copy OrderEmailJobHandler.java
   - Change class name, template references
   - Modify subject and logging messages
   - Handle cancellation-specific data

2. **Create Cancellation Email Templates** (45 minutes)
   - HTML template following orderConfirmation.html pattern
   - Plain text version
   - Include: order details, cancellation reason, refund note, support info

3. **Update Email Queue Mapping** (15 minutes)
   - Verify EMAIL_GENERAL queue routes to OrderCancellationJobHandler
   - Or add EMAIL_ORDER_CANCELLATION queue if preferred

4. **Write Email Setup Guide** (60 minutes)
   - Document GoogleWorkspace SMTP setup
   - Mailpit Docker configuration
   - Testing procedures
   - Environment variable reference

**Total Estimated Time: 2-3 hours** (not days!)

### Key Existing Files YOU MUST Review:

*   **`src/main/java/villagecompute/calendar/services/EmailService.java`**
    *   **Purpose:** Core email sending service using Quarkus Mailer
    *   **DO NOT MODIFY:** This service is complete and working
    *   **Usage:** Import and inject this service in your job handlers

*   **`src/main/java/villagecompute/calendar/services/jobs/OrderEmailJobHandler.java`**
    *   **Purpose:** Reference implementation for email job handlers
    *   **Pattern to Copy:**
        - @ApplicationScoped class implementing DelayedJobHandler
        - @CheckedTemplate inner class for type-safe templates
        - @WithSpan for distributed tracing
        - loadResourceAsString() for CSS injection
        - DelayedJobException with permanent/transient flag

*   **`src/main/resources/templates/OrderEmailJobHandler/orderConfirmation.html`**
    *   **Purpose:** HTML email template example
    *   **Pattern to Copy:**
        - DOCTYPE and responsive HTML structure
        - Qute template syntax: {order.user.displayName}, {order.totalPrice}
        - CSS injection: {stylesheet} placeholder
        - Brand styling: colors, buttons, footer

*   **`src/main/java/villagecompute/calendar/services/OrderService.java`**
    *   **Purpose:** Order business logic with email enqueueing
    *   **Line 253:** Currently enqueues EMAIL_GENERAL for cancellations
    *   **DO MODIFY:** Ensure cancellation emails use correct queue

### Implementation Tips & Notes

*   **Tip 1: Handler Registration**
    - Quarkus CDI automatically discovers @ApplicationScoped job handlers
    - No manual registration needed
    - Handler must implement DelayedJobHandler interface

*   **Tip 2: Template Directory Structure**
    - Templates MUST be in `src/main/resources/templates/{HandlerClassName}/`
    - Example: `templates/OrderCancellationJobHandler/orderCancellation.html`
    - Qute @CheckedTemplate matches handler class name automatically

*   **Tip 3: Error Handling**
    - Permanent failures: `throw new DelayedJobException(true, "Order not found")`
    - Transient failures: `throw new DelayedJobException(false, "SMTP timeout")`
    - Permanent failures won't retry (e.g., order doesn't exist)
    - Transient failures retry with exponential backoff

*   **Tip 4: Testing with Mailpit**
    - Run Mailpit: `docker run -d -p 1025:1025 -p 8025:8025 mailpit/mailpit`
    - Configure: `MAIL_HOST=localhost MAIL_PORT=1025 MAIL_MOCK=false`
    - View emails: http://localhost:8025
    - No authentication needed for Mailpit

*   **Tip 5: CSS Loading**
    - CSS file: `src/main/resources/css/email.css`
    - Load via: `loadResourceAsString("css/email.css")`
    - Inject into template: `Templates.orderCancellation(order, css)`
    - Template uses: `<style>{stylesheet}</style>`

*   **Tip 6: Qute Template Syntax**
    - Variables: `{order.user.email}`
    - Conditionals: `{#if order.quantity > 1}calendars{#else}calendar{/if}`
    - JSON fields: `{order.shippingAddress.get('name').asText()}`
    - Null safety: `{order.user.displayName ?: order.user.email}` (Elvis operator)

*   **Warning 1: Package Name Mismatch**
    - Task spec says: `com.villagecompute.calendar`
    - Actual package: `villagecompute.calendar` (no "com.")
    - Use actual package names from existing code

*   **Warning 2: Template Path Mismatch**
    - Task spec says: `src/main/resources/email-templates/`
    - Actual path: `src/main/resources/templates/`
    - Follow existing convention: `templates/{HandlerClassName}/`

*   **Warning 3: Retry Configuration**
    - DelayedJob retry logic is database-driven
    - Default: 3 attempts with exponential backoff
    - Backoff: ~1 minute, 5 minutes, 15 minutes
    - Configured in job processor, not in individual handlers

### Code Quality Standards

*   **Use @WithSpan:** Add OpenTelemetry tracing to all job handlers
*   **Add Span Attributes:** `Span.current().setAttribute("order.id", orderId)`
*   **Log Events:** Use `LOG.infof()` for significant events (INFO level)
*   **Error Logging:** Use `LOG.error()` for exceptions with full stack trace
*   **Environment Awareness:** EmailService automatically prefixes subjects in non-prod
*   **Type Safety:** Use @CheckedTemplate for compile-time template validation

### Suggested Implementation Order

1. **Create OrderCancellationJobHandler.java** (copy OrderEmailJobHandler.java)
2. **Create orderCancellation.html template** (copy orderConfirmation.html pattern)
3. **Create orderCancellation.txt template** (plain text version)
4. **Test with Mailpit** (verify emails send correctly)
5. **Write email-setup.md guide** (document configuration and testing)
6. **Update OrderService** (if needed - current EMAIL_GENERAL may work)
7. **Mark task complete**

### Final Recommendation

**This task requires minimal effort** because the infrastructure is complete. Focus on:
1. Creating the cancellation job handler (one class, ~100 lines)
2. Creating cancellation email templates (two files, ~100 lines each)
3. Writing comprehensive documentation (email-setup.md)
4. Testing with Mailpit to verify emails work

**DO NOT:**
- Modify EmailService (it's perfect as-is)
- Change DelayedJob system (it works)
- Reconfigure SMTP (configuration is complete)
- Rewrite existing handlers (they're production-ready)

**Total Implementation Time: 2-3 hours** for a skilled developer.
