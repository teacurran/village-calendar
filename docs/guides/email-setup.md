# Email Setup Guide

This guide walks through setting up email notifications for the Village Calendar application. The system sends transactional emails for order confirmations, shipping notifications, and order cancellations.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Email Service Overview](#email-service-overview)
- [Google Workspace Setup (Production)](#google-workspace-setup-production)
- [Mailpit Setup (Development)](#mailpit-setup-development)
- [Environment Configuration](#environment-configuration)
- [Testing Email Templates](#testing-email-templates)
- [Troubleshooting](#troubleshooting)

## Prerequisites

### For Production (Google Workspace)
- Google Workspace account with administrative access
- Domain configured with Google Workspace
- Two-factor authentication (2FA) enabled on the email account

### For Development (Mailpit)
- Docker installed on your development machine
- Or Mailpit binary installed locally

## Email Service Overview

The Village Calendar application uses Quarkus Mailer for email delivery with the following features:

- **SMTP Delivery**: Emails sent via SMTP with TLS encryption
- **HTML & Plain Text**: All emails include both HTML and plain text versions
- **Async Processing**: Emails sent asynchronously via DelayedJob queue system
- **Retry Logic**: Failed email sends automatically retry with exponential backoff (1min, 5min, 15min)
- **Mock Mode**: Development mode prevents accidental email sends

### Email Types

1. **Order Confirmation** - Sent when payment is captured (order status: PAID)
2. **Shipping Notification** - Sent when order is marked as shipped (order status: SHIPPED)
3. **Order Cancellation** - Sent when order is cancelled (order status: CANCELLED)

## Google Workspace Setup (Production)

### Step 1: Enable SMTP Access

1. Log in to your Google Workspace Admin Console: https://admin.google.com
2. Navigate to **Apps > Google Workspace > Gmail**
3. Click **End User Access**
4. Ensure **IMAP/SMTP** access is enabled

### Step 2: Create Application-Specific Password

Since Google requires 2FA for SMTP authentication, you need to generate an app-specific password:

1. Log in to the Google Account used for sending emails (e.g., `noreply@villagecompute.com`)
2. Navigate to **Security Settings**: https://myaccount.google.com/security
3. Under "2-Step Verification", verify 2FA is enabled
4. Scroll to **App passwords** and click to generate a new one
5. Select **Mail** as the app type and **Other** as the device type
6. Enter "Village Calendar SMTP" as the custom name
7. Click **Generate**
8. **IMPORTANT**: Copy the 16-character app password immediately (it won't be shown again)

### Step 3: Test SMTP Connection

You can test the SMTP connection using a simple telnet command:

```bash
# Test connection to Google SMTP server
telnet smtp.gmail.com 587

# You should see a response like:
# 220 smtp.gmail.com ESMTP
```

If the connection succeeds, press `Ctrl+C` to exit.

### Step 4: Configure Environment Variables

Set the following environment variables for production:

```bash
export MAIL_FROM="Village Calendar <noreply@villagecompute.com>"
export MAIL_HOST="smtp.gmail.com"
export MAIL_PORT="587"
export MAIL_USERNAME="noreply@villagecompute.com"
export MAIL_PASSWORD="your-16-char-app-password-here"
export MAIL_MOCK="false"
```

**Security Note**: Never commit these credentials to version control. Use environment variables or a secure secrets manager.

## Mailpit Setup (Development)

Mailpit is a lightweight SMTP server for testing emails during development. It captures all outgoing emails and provides a web UI for viewing them.

### Option 1: Docker (Recommended)

Run Mailpit as a Docker container:

```bash
docker run -d \
  --name mailpit \
  -p 1025:1025 \
  -p 8025:8025 \
  axllent/mailpit
```

**Ports:**
- `1025`: SMTP server (application sends emails here)
- `8025`: Web UI (view captured emails)

Access the web UI at: http://localhost:8025

### Option 2: Binary Installation

Download and install Mailpit from: https://github.com/axllent/mailpit/releases

```bash
# macOS (Homebrew)
brew install mailpit

# Start Mailpit
mailpit
```

### Configure for Development

Set these environment variables for local development:

```bash
export MAIL_FROM="dev@localhost"
export MAIL_HOST="localhost"
export MAIL_PORT="1025"
export MAIL_USERNAME=""
export MAIL_PASSWORD=""
export MAIL_MOCK="true"
```

When `MAIL_MOCK=true`, Quarkus logs emails to the console instead of sending them. Set to `false` to actually send to Mailpit:

```bash
export MAIL_MOCK="false"  # Send to Mailpit for real email rendering testing
```

## Environment Configuration

All email configuration is managed via environment variables in `application.properties`:

```properties
# Email sender configuration
quarkus.mailer.from=${MAIL_FROM:noreply@villagecompute.com}

# SMTP server settings
quarkus.mailer.host=${MAIL_HOST:smtp.example.com}
quarkus.mailer.port=${MAIL_PORT:587}
quarkus.mailer.start-tls=REQUIRED

# Authentication
quarkus.mailer.username=${MAIL_USERNAME:placeholder}
quarkus.mailer.password=${MAIL_PASSWORD:placeholder}

# Mock mode (prevents actual sending in dev)
quarkus.mailer.mock=${MAIL_MOCK:true}
```

### Configuration Profiles

You can create profile-specific configurations:

**Development (default):**
```bash
export MAIL_MOCK="true"  # No emails sent
```

**Local Testing with Mailpit:**
```bash
export MAIL_MOCK="false"
export MAIL_HOST="localhost"
export MAIL_PORT="1025"
```

**Production:**
```bash
export MAIL_MOCK="false"
export MAIL_HOST="smtp.gmail.com"
export MAIL_PORT="587"
export MAIL_USERNAME="noreply@villagecompute.com"
export MAIL_PASSWORD="your-app-specific-password"
```

## Testing Email Templates

### 1. Start the Application

```bash
# Start in dev mode
./mvnw quarkus:dev

# Or with Gradle
./gradlew quarkusDev
```

### 2. Trigger Email Events

Use GraphQL mutations to create orders and trigger email events:

**Order Confirmation Email:**
```graphql
mutation UpdateOrderStatus {
  updateOrderStatus(
    orderId: "order-uuid-here"
    newStatus: "PAID"
    notes: "Payment confirmed"
  ) {
    id
    status
    paidAt
  }
}
```

**Shipping Notification Email:**
```graphql
mutation UpdateOrderStatus {
  updateOrderStatus(
    orderId: "order-uuid-here"
    newStatus: "SHIPPED"
    notes: "Shipped via USPS"
  ) {
    id
    status
    shippedAt
    trackingNumber
  }
}
```

**Order Cancellation Email:**
```graphql
mutation CancelOrder {
  cancelOrder(
    orderId: "order-uuid-here"
    cancellationReason: "Customer requested cancellation"
  ) {
    id
    status
    cancelledAt
  }
}
```

### 3. View Emails in Mailpit

1. Open http://localhost:8025 in your browser
2. You should see the captured emails in the inbox
3. Click on an email to view:
   - HTML rendering (desktop and mobile preview)
   - Plain text version
   - Email headers and metadata
   - SMTP transaction details

### 4. Verify Email Content

Check the following in each email:

**Order Confirmation:**
- ✅ Correct recipient email address
- ✅ Order number and order ID displayed
- ✅ Order total price formatted correctly
- ✅ Calendar name and year
- ✅ Shipping address formatted correctly
- ✅ "What's Next" section with timeline

**Shipping Notification:**
- ✅ Tracking number prominently displayed
- ✅ Order summary (calendar, quantity)
- ✅ Shipped date
- ✅ Shipping address confirmation
- ✅ Estimated delivery timeframe

**Order Cancellation:**
- ✅ Order number and cancellation status
- ✅ Cancellation reason (from order notes)
- ✅ Refund information (if order was paid)
- ✅ Expected refund timeline
- ✅ Link to browse other calendars

## Troubleshooting

### Emails Not Appearing in Mailpit

**Problem**: Triggered order events but no emails in Mailpit UI.

**Solutions**:
1. Verify Mailpit is running:
   ```bash
   docker ps | grep mailpit
   # Should show running container
   ```

2. Check `MAIL_MOCK` is set to `false`:
   ```bash
   echo $MAIL_MOCK
   # Should output: false
   ```

3. Verify SMTP port is correct:
   ```bash
   echo $MAIL_PORT
   # Should output: 1025
   ```

4. Check application logs for email send confirmation:
   ```
   OrderEmailJobHandler: Order confirmation email sent successfully to: user@example.com
   ```

5. Check DelayedJob table for pending email jobs:
   ```sql
   SELECT * FROM delayed_jobs WHERE queue LIKE 'EMAIL_%' AND complete = false;
   ```

### Google Workspace SMTP Authentication Failure

**Problem**: `535-5.7.8 Username and Password not accepted` error.

**Solutions**:
1. Verify you're using an **app-specific password**, not your regular Google password
2. Ensure 2FA is enabled on the Google account
3. Check the username is the full email address (e.g., `noreply@villagecompute.com`)
4. Verify the app password has no spaces (should be 16 characters)
5. Try regenerating the app-specific password

### TLS/STARTTLS Errors

**Problem**: `STARTTLS command failed` or TLS connection errors.

**Solutions**:
1. Verify `quarkus.mailer.start-tls=REQUIRED` in `application.properties`
2. Ensure port 587 is used (not 25 or 465)
3. Check firewall rules allow outbound connections on port 587
4. For Google Workspace, verify SMTP is enabled in admin console

### Email Jobs Not Processing

**Problem**: DelayedJobs are created but emails never send.

**Solutions**:
1. Verify the DelayedJob worker is running (task I4.T1)
2. Check job handler registration in JobWorker:
   ```java
   // JobWorker should map handlers to queues
   EMAIL_ORDER_CONFIRMATION -> OrderEmailJobHandler
   EMAIL_SHIPPING_NOTIFICATION -> ShippingNotificationJobHandler
   EMAIL_GENERAL -> OrderCancellationJobHandler
   ```

3. Check for job failures in database:
   ```sql
   SELECT * FROM delayed_jobs
   WHERE queue LIKE 'EMAIL_%'
   AND completed_with_failure = true;
   ```

4. Review `failure_reason` column for error details

### Template Rendering Errors

**Problem**: `TemplateException` or missing template errors.

**Solutions**:
1. Verify template files exist in correct locations:
   ```
   src/main/resources/templates/OrderEmailJobHandler/orderConfirmation.html
   src/main/resources/templates/ShippingNotificationJobHandler/shippingNotification.html
   src/main/resources/templates/OrderCancellationJobHandler/orderCancellation.html
   ```

2. Ensure template method names match file names (case-sensitive)
3. Check CSS file exists: `src/main/resources/css/email.css`
4. Verify Qute syntax is valid (no unclosed tags)

### Retry Logic Not Working

**Problem**: Failed email jobs are not retrying.

**Explanation**: The retry logic is implemented in the DelayedJob worker (task I4.T1, not yet implemented). When a job handler throws `DelayedJobException(false, ...)`, the worker should:

1. Increment `attempts` counter
2. Update `lastError` with exception message
3. Calculate next retry time with exponential backoff:
   - Attempt 1: 1 minute
   - Attempt 2: 5 minutes
   - Attempt 3: 15 minutes
4. If max attempts (3) exceeded, mark job as permanently failed

**Verification**:
```sql
-- Check job retry attempts
SELECT id, queue, attempts, last_error, run_at
FROM delayed_jobs
WHERE actor_id = 'order-uuid-here';
```

## Email Template Development

### Template Locations

Templates follow Qute naming conventions:

```
src/main/resources/templates/{HandlerClassName}/{templateMethodName}.{extension}
```

Example:
- Handler: `OrderEmailJobHandler`
- Method: `orderConfirmation(order, stylesheet)`
- Template: `templates/OrderEmailJobHandler/orderConfirmation.html`

### CSS Styling

All templates use a shared stylesheet: `src/main/resources/css/email.css`

The CSS is loaded as a string and injected into each template:

```html
<style>
    {stylesheet}
</style>
```

**Important**: Email clients have limited CSS support. The shared stylesheet uses inline-friendly styles:
- No external stylesheets
- Minimal CSS3 features
- Table-based layouts for maximum compatibility
- Tested with Gmail, Outlook, Apple Mail

### JSONB Field Access

Order addresses are stored as JSONB and accessed in templates like this:

```html
{order.shippingAddress.get('name').asText()}
{order.shippingAddress.get('line1').asText()}
{#if order.shippingAddress.has('line2') && !order.shippingAddress.get('line2').isNull()}
    {order.shippingAddress.get('line2').asText()}
{/if}
```

### Conditional Rendering

Use Qute conditionals for optional fields:

```html
{#if order.paidAt != null}
    <p>Paid on: {order.paidAt.toString()}</p>
{/if}

{#if order.quantity > 1}
    calendars
{#else}
    calendar
{/if}
```

## Production Deployment Checklist

Before deploying to production, verify:

- [ ] Google Workspace SMTP access enabled
- [ ] App-specific password generated and stored securely
- [ ] Environment variables set correctly (no placeholders)
- [ ] `MAIL_MOCK=false` in production environment
- [ ] Email templates tested with real order data
- [ ] HTML rendering verified in multiple email clients (Gmail, Outlook, Apple Mail)
- [ ] Plain text fallback works correctly
- [ ] Retry logic tested with deliberate failures
- [ ] From address matches domain SPF/DKIM records
- [ ] Support email address monitored for bounce-backs
- [ ] DelayedJob worker running and processing email queue
- [ ] Monitoring/alerting configured for failed email jobs

## Additional Resources

- [Quarkus Mailer Documentation](https://quarkus.io/guides/mailer)
- [Qute Template Engine](https://quarkus.io/guides/qute)
- [Mailpit GitHub](https://github.com/axllent/mailpit)
- [Google App Passwords](https://support.google.com/accounts/answer/185833)
- [Email Client CSS Support](https://www.caniemail.com/)

## Support

For questions or issues with email configuration, contact the development team or open an issue in the project repository.
