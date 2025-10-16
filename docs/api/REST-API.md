# Village Calendar Service - REST API Documentation

## Overview

The Village Calendar Service provides a **limited REST API** for webhooks, OAuth2 authentication, and health monitoring. For all calendar operations (appointments, templates, orders, etc.), please use the **GraphQL API** at `/graphql`.

### API Architecture

- **Primary API**: GraphQL (`/graphql`) - For all calendar operations
- **REST API** (this document) - Limited to:
  - Stripe payment webhooks
  - OAuth2 authentication flows (Google, Facebook)
  - System health checks and metrics

### Base URLs

- **Development**: `http://localhost:8030`
- **Production**: `https://calendar.villagecompute.com`

### OpenAPI Specification

The machine-readable OpenAPI 3.0 specification is available at:
- **YAML**: [`/q/openapi?format=yaml`](http://localhost:8030/q/openapi?format=yaml)
- **JSON**: [`/q/openapi`](http://localhost:8030/q/openapi)
- **Swagger UI**: [`/q/swagger-ui`](http://localhost:8030/q/swagger-ui)

---

## Authentication

### OAuth2 Flow

The Village Calendar Service uses **OAuth2 Authorization Code Flow** for user authentication with Google and Facebook as identity providers.

#### Authentication Flow Diagram

```mermaid
sequenceDiagram
    participant User
    participant Frontend
    participant CalendarAPI as Calendar Service
    participant OAuth as OAuth Provider<br/>(Google/Facebook)

    User->>Frontend: Click "Login with Google"
    Frontend->>CalendarAPI: GET /auth/login/google
    CalendarAPI->>OAuth: 302 Redirect to OAuth consent page
    OAuth->>User: Display consent screen
    User->>OAuth: Grant permissions
    OAuth->>CalendarAPI: 302 Redirect to /auth/google/callback?code=...
    CalendarAPI->>OAuth: Exchange code for tokens
    OAuth->>CalendarAPI: Return access token & user info
    CalendarAPI->>CalendarAPI: Create/update user record
    CalendarAPI->>CalendarAPI: Issue JWT token
    CalendarAPI->>Frontend: 200 OK with JWT + user info
    Frontend->>Frontend: Store JWT token
    Frontend->>CalendarAPI: Subsequent requests with<br/>Authorization: Bearer {JWT}
```

#### Step-by-Step Process

1. **User initiates login**
   - Frontend redirects user to `/auth/login/google` or `/auth/login/facebook`

2. **OAuth redirect**
   - Calendar service redirects to OAuth provider's consent page
   - User sees "Allow [App Name] to access your [email/profile]?"

3. **User grants permissions**
   - OAuth provider redirects back to `/auth/{provider}/callback?code=...`

4. **Token exchange**
   - Calendar service exchanges authorization code for access token
   - Retrieves user profile information from OAuth provider

5. **User creation/update**
   - Service creates new user or updates existing user record
   - Generates JWT token signed with RS256 algorithm

6. **Return credentials**
   - Service returns JWT token and user information to frontend
   - JWT token is valid for 24 hours (86400 seconds)

7. **Authenticated requests**
   - Frontend includes `Authorization: Bearer {JWT}` header in subsequent requests
   - Both REST and GraphQL APIs validate the JWT signature

### JWT Token Structure

The JWT token issued after successful OAuth authentication contains:

```json
{
  "iss": "village-calendar",
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "aud": "village-calendar-api",
  "exp": 1234567890,
  "iat": 1234481490,
  "email": "user@example.com",
  "name": "John Doe"
}
```

- **iss** (issuer): Always `village-calendar`
- **sub** (subject): User UUID
- **aud** (audience): API identifier
- **exp** (expiration): Unix timestamp (24 hours from issue)
- **iat** (issued at): Unix timestamp
- **email**: User's email address
- **name**: User's display name

---

## Endpoints

### Authentication Endpoints

#### `GET /auth/login/google`

Initiates the OAuth2 login flow with Google.

**Request**
```http
GET /auth/login/google HTTP/1.1
Host: localhost:8030
```

**Response**
```http
HTTP/1.1 303 See Other
Location: https://accounts.google.com/o/oauth2/v2/auth?...
```

**Notes**
- This endpoint triggers an automatic redirect to Google's OAuth consent page
- Not meant to be called via JavaScript fetch/axios - use browser navigation
- After successful authentication, user is redirected to `/auth/google/callback`

---

#### `GET /auth/google/callback`

OAuth2 callback endpoint for Google authentication.

**Request**
```http
GET /auth/google/callback?code=4/0Adeu5B...&state=... HTTP/1.1
Host: localhost:8030
```

**Response - Success (200 OK)**
```json
{
  "token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJ2aWxsYWdlLWNhbGVuZGFyIiwic3ViIjoiNTUwZTg0MDAtZTI5Yi00MWQ0LWE3MTYtNDQ2NjU1NDQwMDAwIiwiYXVkIjoidmlsbGFnZS1jYWxlbmRhci1hcGkiLCJleHAiOjE3Mjk2MTQ3MTIsImlhdCI6MTcyOTUyODMxMiwiZW1haWwiOiJ1c2VyQGV4YW1wbGUuY29tIiwibmFtZSI6IkpvaG4gRG9lIn0...",
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com",
    "displayName": "John Doe",
    "profileImageUrl": "https://lh3.googleusercontent.com/a/..."
  }
}
```

**Response - Error (500 Internal Server Error)**
```json
{
  "error": "Authentication failed: Invalid authorization code"
}
```

**Notes**
- This endpoint is invoked by Google's OAuth service, not directly by clients
- The `code` parameter is the authorization code from Google
- Creates a new user if first time login, or updates existing user
- JWT token expires after 24 hours

---

#### `GET /auth/login/facebook`

Initiates the OAuth2 login flow with Facebook.

**Request**
```http
GET /auth/login/facebook HTTP/1.1
Host: localhost:8030
```

**Response**
```http
HTTP/1.1 303 See Other
Location: https://www.facebook.com/v12.0/dialog/oauth?...
```

**Notes**
- Similar flow to Google OAuth
- Facebook OAuth integration is currently disabled in production (see `application.properties`)
- After successful authentication, user is redirected to `/auth/facebook/callback`

---

#### `GET /auth/facebook/callback`

OAuth2 callback endpoint for Facebook authentication.

**Response format is identical to Google callback.**

---

#### `GET /auth/me`

Get current authenticated user information.

**Request**
```http
GET /auth/me HTTP/1.1
Host: localhost:8030
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Response - Success (200 OK)**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "displayName": "John Doe",
  "profileImageUrl": "https://lh3.googleusercontent.com/a/..."
}
```

**Response - Unauthorized (401)**
```json
{
  "error": "No valid JWT token found"
}
```

**Response - Not Found (404)**
```json
{
  "error": "User not found"
}
```

**Notes**
- Requires valid JWT token in `Authorization` header
- Used to verify token validity and retrieve current user details
- Returns 401 if token is missing, expired, or invalid

---

### Webhook Endpoints

#### `POST /api/webhooks/stripe`

Handles Stripe payment webhook events.

**Request**
```http
POST /api/webhooks/stripe HTTP/1.1
Host: localhost:8030
Content-Type: application/json
Stripe-Signature: t=1234567890,v1=abc123...

{
  "id": "evt_1PqR2s3T4u5V6w7X",
  "object": "event",
  "type": "payment_intent.succeeded",
  "data": {
    "object": {
      "id": "pi_1PqR2s3T4u5V6w7X",
      "amount": 2999,
      "currency": "usd",
      "status": "succeeded"
    }
  }
}
```

**Response - Success (200 OK)**
```json
{
  "status": "success"
}
```

**Response - Invalid Signature (400 Bad Request)**
```json
{
  "error": "Invalid signature"
}
```

**Response - Missing Signature (400 Bad Request)**
```json
{
  "error": "Missing signature header"
}
```

**Response - Invalid Payload (400 Bad Request)**
```json
{
  "error": "Invalid webhook payload"
}
```

**Response - Processing Error (500 Internal Server Error)**
```json
{
  "error": "Failed to process event"
}
```

**Supported Event Types**
- `payment_intent.succeeded` - Payment completed successfully
- `payment_intent.payment_failed` - Payment attempt failed

**Notes**
- **CRITICAL**: The `Stripe-Signature` header is required for security
- Signature verification prevents replay attacks and unauthorized webhook calls
- Webhook secret is configured via `STRIPE_WEBHOOK_SECRET` environment variable
- Events are processed idempotently (duplicate events are safe)

---

### Webhook Integration Guide

#### Setting Up Stripe Webhooks

1. **Configure webhook endpoint in Stripe Dashboard**
   - Go to Developers > Webhooks
   - Add endpoint URL: `https://calendar.villagecompute.com/api/webhooks/stripe`
   - Select events: `payment_intent.succeeded`, `payment_intent.payment_failed`

2. **Get webhook signing secret**
   - After creating the endpoint, copy the "Signing secret" (starts with `whsec_`)
   - Set as environment variable: `STRIPE_WEBHOOK_SECRET=whsec_...`

3. **Test webhook locally**
   ```bash
   # Install Stripe CLI
   stripe listen --forward-to http://localhost:8030/api/webhooks/stripe

   # Trigger test event
   stripe trigger payment_intent.succeeded
   ```

4. **Verify signature in logs**
   ```
   INFO  [WebhookResource] Webhook signature validated successfully. Event type: payment_intent.succeeded
   INFO  [WebhookResource] Payment succeeded for PaymentIntent: pi_...
   INFO  [WebhookResource] Order 123e4567-e89b-12d3-a456-426614174000 marked as PAID
   ```

#### Security Considerations

- **Always verify webhook signatures** - Never process webhooks without signature verification
- **Use HTTPS in production** - Cloudflare TLS enforces HTTPS for all production traffic
- **Implement idempotency** - Process duplicate events safely (already handled by service)
- **Validate event structure** - Ensure required fields exist before processing
- **Log all webhook events** - Keep audit trail for debugging and compliance

---

### Health Check Endpoints

#### `GET /q/health/live`

Kubernetes liveness probe - checks if application is running.

**Request**
```http
GET /q/health/live HTTP/1.1
Host: localhost:8030
```

**Response - Alive (200 OK)**
```json
{
  "status": "UP",
  "checks": [
    {
      "name": "Liveness check",
      "status": "UP"
    }
  ]
}
```

**Response - Not Alive (503 Service Unavailable)**
```json
{
  "status": "DOWN",
  "checks": [
    {
      "name": "Liveness check",
      "status": "DOWN"
    }
  ]
}
```

**Notes**
- Used by Kubernetes to detect if pod needs to be restarted
- Returns 200 if application is running (even if not ready to serve traffic)
- Does not check database connectivity or external dependencies

---

#### `GET /q/health/ready`

Kubernetes readiness probe - checks if application is ready to serve traffic.

**Request**
```http
GET /q/health/ready HTTP/1.1
Host: localhost:8030
```

**Response - Ready (200 OK)**
```json
{
  "status": "UP",
  "checks": [
    {
      "name": "Database connection",
      "status": "UP"
    },
    {
      "name": "Readiness check",
      "status": "UP"
    }
  ]
}
```

**Response - Not Ready (503 Service Unavailable)**
```json
{
  "status": "DOWN",
  "checks": [
    {
      "name": "Database connection",
      "status": "DOWN",
      "data": {
        "error": "Connection timeout"
      }
    }
  ]
}
```

**Notes**
- Used by Kubernetes to determine if pod should receive traffic
- Checks database connectivity and other critical dependencies
- Returns 503 if any dependency is unavailable

---

#### `GET /q/metrics`

Prometheus metrics endpoint for monitoring.

**Request**
```http
GET /q/metrics HTTP/1.1
Host: localhost:8030
```

**Response (200 OK)**
```text
# HELP jvm_memory_used_bytes The amount of used memory
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{area="heap",id="G1 Eden Space"} 1.048576E8
jvm_memory_used_bytes{area="nonheap",id="CodeHeap 'non-nmethods'"} 1234567.0

# HELP http_server_requests_seconds Duration of HTTP server request handling
# TYPE http_server_requests_seconds summary
http_server_requests_seconds_count{method="POST",uri="/api/webhooks/stripe",status="200"} 42
http_server_requests_seconds_sum{method="POST",uri="/api/webhooks/stripe",status="200"} 1.234

# HELP db_pool_size Connection pool size
# TYPE db_pool_size gauge
db_pool_size{pool="<default>"} 10
```

**Notes**
- OpenMetrics format (compatible with Prometheus)
- Includes JVM metrics (memory, threads, GC)
- HTTP request metrics (count, duration, status codes)
- Database connection pool metrics
- Custom business metrics (orders, appointments, etc.)

---

## Error Handling

### Error Response Format

All REST endpoints return errors in a consistent format:

```json
{
  "error": "Human-readable error message"
}
```

### HTTP Status Codes

| Status Code | Meaning | Used For |
|-------------|---------|----------|
| 200 | OK | Successful request |
| 303 | See Other | OAuth redirect |
| 400 | Bad Request | Invalid webhook signature, malformed payload |
| 401 | Unauthorized | Missing or invalid JWT token |
| 404 | Not Found | User not found |
| 500 | Internal Server Error | Unexpected server error |
| 503 | Service Unavailable | Health check failure |

### Common Error Scenarios

#### Invalid JWT Token

**Request**
```http
GET /auth/me HTTP/1.1
Authorization: Bearer invalid_token
```

**Response (401)**
```json
{
  "error": "No valid JWT token found"
}
```

**Solution**: Obtain a new JWT token by completing the OAuth2 flow.

---

#### Expired JWT Token

**Request**
```http
GET /auth/me HTTP/1.1
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Response (401)**
```json
{
  "error": "Token expired"
}
```

**Solution**: Re-authenticate using `/auth/login/google` or `/auth/login/facebook`.

---

#### Invalid Webhook Signature

**Request**
```http
POST /api/webhooks/stripe HTTP/1.1
Stripe-Signature: t=1234567890,v1=invalid_signature

{...}
```

**Response (400)**
```json
{
  "error": "Invalid signature"
}
```

**Solution**: Verify webhook secret is correctly configured. Use Stripe CLI to test locally.

---

## Security Considerations

### HTTPS Enforcement

- **Production**: All traffic MUST use HTTPS (enforced by Cloudflare TLS)
- **Development**: HTTP is allowed on `localhost:8030`
- **Mixed Content**: Ensure frontend also uses HTTPS to avoid mixed content warnings

### Webhook Signature Verification

The Stripe webhook endpoint implements HMAC-SHA256 signature verification to prevent:
- **Replay attacks** - Old events cannot be resent
- **Unauthorized requests** - Only Stripe can trigger webhooks
- **Tampering** - Payload modifications are detected

**Verification Process:**
1. Extract timestamp and signature from `Stripe-Signature` header
2. Construct signed payload: `${timestamp}.${raw_body}`
3. Compute HMAC-SHA256 hash using webhook secret
4. Compare computed signature with provided signature
5. Reject if signatures don't match or timestamp is too old (>5 minutes)

### JWT Token Security

- **Algorithm**: RS256 (RSA with SHA-256)
- **Key Rotation**: Private key stored in `jwt-private-key.pem`, public key in `jwt-public-key.pem`
- **Expiration**: 24 hours (86400 seconds)
- **Validation**: Signature, expiration, issuer, and audience claims are verified

**Best Practices:**
- Store JWT tokens in `httpOnly` cookies (not localStorage)
- Implement token refresh mechanism before expiration
- Clear tokens on logout
- Validate JWT on every protected request

### Input Validation

- **Multi-layer validation**: Frontend, API layer, database constraints
- **SQL injection prevention**: Hibernate ORM with parameterized queries
- **XSS prevention**: Output encoding in frontend
- **CSRF protection**: JWT tokens prevent CSRF attacks (no session cookies)

---

## Rate Limiting

**Status**: Not yet implemented

**Planned Implementation:**
- 100 requests per minute per IP address for authentication endpoints
- 1000 requests per minute per authenticated user for API endpoints
- Webhook endpoints exempt from rate limiting (validated by signature)

**Future Headers:**
```http
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1234567890
```

---

## GraphQL API

For all calendar operations (appointments, templates, orders, users), use the **GraphQL API**:

- **Endpoint**: `/graphql`
- **GraphQL UI**: `/graphql-ui`
- **Documentation**: See [GraphQL API Documentation](./README.md)

**Example Query:**
```graphql
query GetAppointments {
  appointments(filter: { status: CONFIRMED }) {
    edges {
      node {
        id
        startTime
        endTime
        customer {
          email
          displayName
        }
        service {
          name
          duration
        }
      }
    }
  }
}
```

**Example Mutation:**
```graphql
mutation CreateOrder($input: CreateOrderInput!) {
  createOrder(input: $input) {
    order {
      id
      stripePaymentIntentId
      clientSecret
    }
  }
}
```

---

## Support

For questions or issues with the REST API:

- **Documentation**: https://docs.villagecompute.com/calendar
- **GitHub Issues**: https://github.com/villagecompute/calendar/issues
- **Support Email**: support@villagecompute.com

---

## Changelog

### Version 1.0.0 (2025-10-15)
- Initial REST API documentation
- OAuth2 authentication with Google and Facebook
- Stripe webhook integration
- Health check and metrics endpoints
- OpenAPI 3.0 specification
