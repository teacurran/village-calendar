# Stripe Payment Integration Setup Guide

This guide provides step-by-step instructions for configuring Stripe payment processing for the Village Calendar application's e-commerce functionality.

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Creating a Stripe Account](#creating-a-stripe-account)
4. [Obtaining Test API Keys](#obtaining-test-api-keys)
5. [Configuring Webhook Endpoints](#configuring-webhook-endpoints)
6. [Local Development Testing](#local-development-testing)
7. [Environment Configuration](#environment-configuration)
8. [Testing the Integration](#testing-the-integration)
9. [Production Setup](#production-setup)
10. [Security Best Practices](#security-best-practices)
11. [Troubleshooting](#troubleshooting)

## Overview

The Village Calendar application uses Stripe for payment processing to handle calendar orders. The integration uses **Stripe Checkout Sessions**, which provide:

- **PCI DSS Compliance**: Card data never touches your servers (handled by Stripe's hosted payment page)
- **Secure Payment Flow**: Customers are redirected to Stripe's secure checkout page
- **Webhook Notifications**: Receive real-time updates when payments succeed or fail
- **Test Mode**: Safely test the integration without processing real payments

### Payment Flow

1. Customer creates an order for a custom calendar
2. Application creates a Stripe Checkout Session via `StripeService.createCheckoutSession()`
3. Customer is redirected to Stripe's hosted payment page
4. Customer enters payment details and completes purchase
5. Stripe redirects customer back to success/cancel URL
6. Stripe sends webhook notification to application
7. Application validates webhook signature and updates order status
8. Order is marked as paid and ready for fulfillment

## Prerequisites

- Village Calendar application running locally (default port: 8030)
- PostgreSQL database configured and running
- Email address for Stripe account registration
- For webhook testing: ngrok or Stripe CLI installed

## Creating a Stripe Account

### 1. Sign Up for Stripe

1. Go to [https://stripe.com/](https://stripe.com/)
2. Click "Start now" or "Sign up"
3. Enter your email address and create a password
4. Verify your email address by clicking the link in the confirmation email

### 2. Complete Account Information

1. Log in to the [Stripe Dashboard](https://dashboard.stripe.com/)
2. Fill in business information:
   - **Business name**: Your business or personal name
   - **Industry**: Software or SaaS
   - **Business location**: Your country
3. You can skip detailed business information for development/testing

### 3. Activate Your Account (Optional for Testing)

For testing purposes, you can use Stripe in test mode without activating your account. To activate for production:

1. Navigate to "Settings" → "Account"
2. Complete business verification:
   - Business details
   - Bank account information (for payouts)
   - Identity verification documents
3. This process can take 1-3 business days

**Note**: You can start development immediately in test mode without account activation.

## Obtaining Test API Keys

Stripe provides separate API keys for test mode and live mode. **Always use test mode keys during development.**

### 1. Access API Keys

1. Log in to the [Stripe Dashboard](https://dashboard.stripe.com/)
2. Click "Developers" in the top navigation
3. Click "API keys" in the left sidebar
4. **Ensure you're viewing "Test mode"** (toggle switch in top-right)

### 2. Copy Test API Keys

You'll need three keys for the Village Calendar integration:

#### Secret Key (Server-side)

- **Format**: Starts with `sk_test_`
- **Example**: `sk_test_51AbCdEfGhIjKlMnOpQrStUvWxYz1234567890...`
- **Purpose**: Used by backend to create checkout sessions and process payments
- **Security**: NEVER expose this key in frontend code or commit to git

**To get the secret key:**
1. Under "Standard keys" → "Secret key"
2. Click "Reveal test key" or copy the visible value
3. Save this value securely

#### Publishable Key (Client-side)

- **Format**: Starts with `pk_test_`
- **Example**: `pk_test_51AbCdEfGhIjKlMnOpQrStUvWxYz1234567890...`
- **Purpose**: Used by frontend to initialize Stripe.js
- **Security**: Safe to expose in frontend code

**To get the publishable key:**
1. Under "Standard keys" → "Publishable key"
2. Copy the visible value

#### Webhook Signing Secret

- **Format**: Starts with `whsec_`
- **Example**: `whsec_1234567890abcdefghijklmnopqrstuvwxyz`
- **Purpose**: Used to verify webhook signatures and prevent spoofing
- **Security**: Keep secret, never expose in frontend

**To get the webhook signing secret:**
1. Follow the steps in [Configuring Webhook Endpoints](#configuring-webhook-endpoints) below
2. The signing secret is displayed after creating a webhook endpoint

## Configuring Webhook Endpoints

Webhooks allow Stripe to notify your application when payment events occur (e.g., payment succeeded, payment failed).

### 1. Create Webhook Endpoint

1. In the Stripe Dashboard, click "Developers" → "Webhooks"
2. **Ensure you're in "Test mode"** (toggle switch in top-right)
3. Click "+ Add endpoint"

### 2. Configure Endpoint URL

**For Local Development (with ngrok):**
```
https://your-subdomain.ngrok.io/api/webhooks/stripe
```

**For Production:**
```
https://your-domain.com/api/webhooks/stripe
```

### 3. Select Events to Listen To

Select the following events:

- **checkout.session.completed**: Fired when a Checkout Session is successfully completed
- **checkout.session.expired**: Fired when a Checkout Session expires (24 hours)
- **payment_intent.succeeded**: Fired when a payment succeeds
- **payment_intent.payment_failed**: Fired when a payment fails

**To add these events:**
1. Click "+ Select events"
2. Search for each event type above
3. Check the box next to each event
4. Click "Add events"

### 4. Save Webhook Signing Secret

1. After creating the webhook endpoint, click on it to view details
2. Under "Signing secret", click "Reveal"
3. Copy the secret (starts with `whsec_`)
4. Save this as the `STRIPE_WEBHOOK_SECRET` environment variable

### 5. Test the Webhook (Optional)

1. In the webhook details page, click "Send test webhook"
2. Select an event type (e.g., `checkout.session.completed`)
3. Click "Send test webhook"
4. Check your application logs to verify the webhook was received

## Local Development Testing

For local development, Stripe webhooks require a publicly accessible URL. You have two options:

### Option 1: Stripe CLI (Recommended)

The Stripe CLI forwards webhook events to your local server.

#### Install Stripe CLI

**macOS:**
```bash
brew install stripe/stripe-cli/stripe
```

**Linux:**
```bash
# Download from https://github.com/stripe/stripe-cli/releases
wget https://github.com/stripe/stripe-cli/releases/latest/download/stripe_linux_x86_64.tar.gz
tar -xvf stripe_linux_x86_64.tar.gz
sudo mv stripe /usr/local/bin/
```

**Windows:**
```powershell
# Download from https://github.com/stripe/stripe-cli/releases
# Or use Scoop: scoop install stripe
```

#### Login to Stripe CLI

```bash
stripe login
```

This will open a browser window to authorize the CLI.

#### Forward Webhooks to Local Server

```bash
stripe listen --forward-to localhost:8030/api/webhooks/stripe
```

This will output:
```
> Ready! Your webhook signing secret is whsec_1234567890abcdefghijklmnopqrstuvwxyz
```

**Copy this webhook secret** and set it as `STRIPE_WEBHOOK_SECRET` in your environment.

#### Trigger Test Events

In another terminal, trigger test webhook events:

```bash
# Test checkout session completed
stripe trigger checkout.session.completed

# Test payment intent succeeded
stripe trigger payment_intent.succeeded

# Test payment failed
stripe trigger payment_intent.payment_failed
```

Check your application logs to verify events are received and processed.

### Option 2: ngrok

ngrok creates a secure tunnel to your local server, providing a public HTTPS URL.

#### Install ngrok

**macOS:**
```bash
brew install ngrok
```

**Or download from [https://ngrok.com/download](https://ngrok.com/download)**

#### Start ngrok Tunnel

```bash
ngrok http 8030
```

This will output:
```
Forwarding  https://abc123.ngrok.io -> http://localhost:8030
```

#### Update Webhook Endpoint

1. Copy the ngrok URL (e.g., `https://abc123.ngrok.io`)
2. Go to Stripe Dashboard → Developers → Webhooks
3. Edit your webhook endpoint
4. Update the URL to: `https://abc123.ngrok.io/api/webhooks/stripe`
5. Click "Save"

#### Access Application via ngrok

Visit `https://abc123.ngrok.io` instead of `http://localhost:8030` to test the full payment flow.

**Note**: ngrok URLs change each time you restart ngrok (unless you have a paid plan). Update your webhook endpoint accordingly.

## Environment Configuration

### Development (.env file)

Create a `.env` file in the project root (**DO NOT commit this file to git**):

```env
# Stripe API Keys (Test Mode)
STRIPE_SECRET_KEY=sk_test_51AbCdEfGhIjKlMnOpQrStUvWxYz1234567890
STRIPE_PUBLISHABLE_KEY=pk_test_51AbCdEfGhIjKlMnOpQrStUvWxYz1234567890
STRIPE_WEBHOOK_SECRET=whsec_1234567890abcdefghijklmnopqrstuvwxyz
```

### Verify Configuration is Loaded

The Stripe configuration is already set up in `src/main/resources/application.properties`:

```properties
# Stripe Payment Integration
stripe.api.key=${STRIPE_SECRET_KEY:sk_test_placeholder}
stripe.publishable.key=${STRIPE_PUBLISHABLE_KEY:pk_test_placeholder}
stripe.webhook.secret=${STRIPE_WEBHOOK_SECRET:whsec_placeholder}
```

The `${VAR:default}` syntax means:
- Use environment variable `VAR` if set
- Otherwise, use the placeholder value
- **Placeholder values will not work for actual API calls**

### Verify Keys are Loaded at Startup

Start the application:

```bash
./mvnw quarkus:dev
```

Check the logs for:
```
Payment service initialized with Stripe API
StripeService initialized with Stripe Checkout API
```

If you see these messages, the Stripe SDK is initialized successfully.

## Testing the Integration

### 1. Create a Test Order

Use the GraphQL API or REST endpoint to create a calendar order:

```bash
curl -X POST http://localhost:8030/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "calendarId": "your-calendar-uuid",
    "quantity": 1,
    "shippingAddress": {
      "name": "John Doe",
      "address": "123 Main St",
      "city": "San Francisco",
      "state": "CA",
      "zip": "94105",
      "country": "US"
    }
  }'
```

### 2. Create Checkout Session

The application will call `StripeService.createCheckoutSession()` and return a checkout URL.

Example response:
```json
{
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "checkoutUrl": "https://checkout.stripe.com/c/pay/cs_test_abc123...",
  "sessionId": "cs_test_abc123..."
}
```

### 3. Complete Test Payment

1. Open the `checkoutUrl` in a browser
2. You'll see Stripe's hosted checkout page
3. Use Stripe's test card numbers:

**Successful Payment:**
- **Card number**: `4242 4242 4242 4242`
- **Expiration**: Any future date (e.g., `12/34`)
- **CVC**: Any 3 digits (e.g., `123`)
- **ZIP**: Any 5 digits (e.g., `12345`)

**Payment Declined:**
- **Card number**: `4000 0000 0000 0002`

**More test cards**: [https://stripe.com/docs/testing](https://stripe.com/docs/testing)

4. Click "Pay"
5. You'll be redirected to the success URL

### 4. Verify Webhook Received

Check application logs for:
```
Received Stripe webhook: checkout.session.completed
Webhook signature validated successfully
Order 550e8400-e29b-41d4-a716-446655440000 marked as PAID
```

### 5. Verify Order Status

Query the order status:

```bash
curl http://localhost:8030/api/orders/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

Expected response:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PAID",
  "paidAt": "2025-10-17T12:34:56Z",
  "stripePaymentIntentId": "pi_abc123...",
  ...
}
```

### 6. Verify in Stripe Dashboard

1. Go to [Stripe Dashboard](https://dashboard.stripe.com/) (Test mode)
2. Click "Payments" in the left sidebar
3. You should see the test payment listed
4. Click on the payment to view details including metadata (order ID, user ID)

## Production Setup

### 1. Activate Your Stripe Account

Complete business verification in the Stripe Dashboard (see [Creating a Stripe Account](#creating-a-stripe-account)).

### 2. Obtain Live API Keys

1. In Stripe Dashboard, **toggle to "Live mode"** (top-right)
2. Go to "Developers" → "API keys"
3. Copy the **live** secret key (starts with `sk_live_`)
4. Copy the **live** publishable key (starts with `pk_live_`)

### 3. Create Production Webhook Endpoint

1. In Stripe Dashboard (Live mode), go to "Developers" → "Webhooks"
2. Click "+ Add endpoint"
3. Enter your production URL: `https://your-domain.com/api/webhooks/stripe`
4. Select the same events as test mode
5. Copy the webhook signing secret (starts with `whsec_`)

### 4. Set Production Environment Variables

**In your production environment** (Kubernetes secrets, AWS Secrets Manager, etc.):

```bash
export STRIPE_SECRET_KEY="sk_live_..."
export STRIPE_PUBLISHABLE_KEY="pk_live_..."
export STRIPE_WEBHOOK_SECRET="whsec_..."
```

**CRITICAL**: Never commit live API keys to git or store them in plaintext files.

### 5. Test Production Integration

1. Create a real order with a small amount (e.g., $0.50)
2. Use a real credit card to complete payment
3. Verify webhook is received and order status updates
4. **Refund the test payment** in Stripe Dashboard

### 6. Monitor Payments

1. Set up Stripe Dashboard notifications (email alerts for payments)
2. Monitor webhook delivery in "Developers" → "Webhooks"
3. Check application logs for payment errors
4. Set up alerts for failed payments

## Security Best Practices

### 1. Never Commit Secrets to Git

**CRITICAL**: Never commit API keys to version control.

- Add `.env` to `.gitignore`
- Use environment variables in production
- Use placeholder values in `application.properties`
- Rotate keys if accidentally exposed

**Verify no secrets in git history:**
```bash
git log -p | grep "sk_live_"
git log -p | grep "whsec_"
```

If you find secrets, immediately:
1. Rotate the keys in Stripe Dashboard
2. Remove from git history using `git filter-branch` or BFG Repo-Cleaner

### 2. Always Validate Webhook Signatures

The application uses `StripeService.validateWebhookSignature()` to verify webhooks are from Stripe.

**Never process webhooks without validation** - this prevents fraudulent order completion.

### 3. Use HTTPS in Production

- Stripe requires HTTPS for live mode webhooks
- Use valid SSL/TLS certificates
- Never use self-signed certificates in production

### 4. Implement Idempotency

The application uses idempotency keys to prevent duplicate charges:

```java
String idempotencyKey = "checkout_order_" + order.id + "_" + System.currentTimeMillis();
```

This ensures that network retries don't create duplicate checkout sessions.

### 5. Limit API Key Permissions

Stripe allows creating restricted API keys:

1. Go to "Developers" → "API keys"
2. Click "Create restricted key"
3. Grant only necessary permissions (e.g., write for checkout sessions, read for retrieving sessions)
4. Use restricted keys for added security

### 6. Monitor for Fraud

- Enable Stripe Radar (fraud detection) in Dashboard
- Set up alerts for high-value transactions
- Review declined payments regularly
- Block suspicious IP addresses

### 7. Handle PCI Compliance

Stripe Checkout handles PCI compliance for you:
- Card data never touches your servers
- Stripe maintains PCI DSS Level 1 certification
- You qualify for SAQ A (simplest compliance questionnaire)

**Do not:**
- Collect card numbers in your application
- Log card data
- Store card data in your database

### 8. Rotate Secrets Regularly

- Rotate webhook secrets annually
- Rotate API keys if compromised
- Update all environments after rotation
- Test after rotation to ensure no downtime

## Troubleshooting

### Error: Invalid API Key

**Cause**: API key is incorrect, expired, or not set.

**Solution**:
1. Verify environment variable is set: `echo $STRIPE_SECRET_KEY`
2. Check for extra whitespace in the key
3. Ensure you're using the correct mode (test vs. live)
4. Regenerate the key in Stripe Dashboard if necessary

### Error: No such checkout session

**Cause**: Session ID is invalid or expired (sessions expire after 24 hours).

**Solution**:
1. Verify the session ID format (starts with `cs_test_` or `cs_live_`)
2. Check session wasn't created more than 24 hours ago
3. Create a new checkout session for the order

### Error: Webhook signature verification failed

**Cause**: Webhook secret is incorrect or payload was modified.

**Solution**:
1. Verify `STRIPE_WEBHOOK_SECRET` matches the value in Stripe Dashboard
2. Ensure you're using the correct webhook endpoint (test vs. live)
3. Check for middleware that modifies request body before validation
4. If using Stripe CLI, copy the webhook secret from the CLI output

### Error: Amount must be at least $0.50

**Cause**: Stripe requires a minimum charge amount.

**Solution**:
1. Ensure order total is at least $0.50 USD
2. Check price calculation logic (convert dollars to cents correctly)
3. For testing, use test amounts >= $0.50

### Webhook not received

**Cause**: Webhook endpoint unreachable or not configured correctly.

**Solution**:
1. Verify webhook endpoint URL is publicly accessible
2. Check firewall allows inbound HTTPS traffic
3. Test webhook delivery in Stripe Dashboard ("Send test webhook")
4. Check application logs for webhook processing errors
5. Verify webhook endpoint is registered in Stripe Dashboard
6. For local development, ensure ngrok or Stripe CLI is running

### Test payment not working

**Cause**: Using real card numbers in test mode or test card numbers in live mode.

**Solution**:
1. In test mode, use Stripe test card numbers (e.g., `4242 4242 4242 4242`)
2. In live mode, use real payment methods
3. Verify you're in the correct mode (check API key prefix: `sk_test_` vs `sk_live_`)

### Order status not updating after payment

**Cause**: Webhook not processed or order lookup failed.

**Solution**:
1. Check application logs for webhook processing errors
2. Verify webhook includes correct `metadata.order_id`
3. Check database for order with matching ID
4. Verify webhook signature validation passed
5. Test webhook delivery manually using Stripe CLI

### SSL/TLS certificate errors

**Cause**: Invalid SSL certificate or missing CA certificates.

**Solution**:
1. Use valid SSL certificates in production (Let's Encrypt, commercial CA)
2. Ensure Java trusts your certificate authority
3. Update Java cacerts if necessary:
   ```bash
   keytool -import -trustcacerts -alias mycert -file certificate.crt -keystore $JAVA_HOME/lib/security/cacerts
   ```

### Checkout session URL not redirecting

**Cause**: Success/cancel URLs invalid or not allowed.

**Solution**:
1. Ensure success/cancel URLs are absolute URLs (include `https://`)
2. Verify URLs are accessible from user's browser
3. Check for typos in URL configuration
4. Test URLs manually in browser

## Additional Resources

- [Stripe Checkout Documentation](https://stripe.com/docs/payments/checkout)
- [Stripe API Reference](https://stripe.com/docs/api)
- [Stripe Testing Guide](https://stripe.com/docs/testing)
- [Stripe Webhooks Guide](https://stripe.com/docs/webhooks)
- [Stripe Java SDK Documentation](https://stripe.com/docs/api/java)
- [PCI DSS Compliance Guide](https://stripe.com/docs/security/guide)
- [Stripe Dashboard](https://dashboard.stripe.com/)
- [Stripe CLI Documentation](https://stripe.com/docs/stripe-cli)

## Support

For Stripe-specific issues:
- [Stripe Support](https://support.stripe.com/)
- [Stripe Status Page](https://status.stripe.com/)

For Village Calendar application issues:
- Check application logs for detailed error messages
- Review this guide's troubleshooting section
- Contact your development team
