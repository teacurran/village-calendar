# Village Calendar Service - GraphQL API Documentation

## Overview

The Village Calendar Service provides a GraphQL API for creating, customizing, and ordering personalized printed calendars. The API supports OAuth2 authentication, calendar template browsing, calendar customization, and e-commerce order management with Stripe payment integration.

**Base Endpoint:** `/graphql` (HTTP POST)

**Schema Version:** 1.0.0

**API Style:** GraphQL over HTTPS

---

## Table of Contents

1. [Authentication](#authentication)
2. [Example Queries](#example-queries)
3. [Example Mutations](#example-mutations)
4. [Error Handling](#error-handling)
5. [Rate Limiting](#rate-limiting)
6. [Pagination & Filtering](#pagination--filtering)

---

## Authentication

### OAuth2 Flow

The calendar service uses OAuth2 for user authentication with JWT tokens for API authorization.

#### Step 1: Initiate OAuth Login

Redirect user to OAuth provider:

```
GET /auth/oauth/authorize?provider=google
```

Supported providers: `google`, `facebook`

The service redirects to the OAuth provider's login page.

#### Step 2: OAuth Callback

After successful authentication, the provider redirects back to:

```
GET /auth/oauth/callback?code=<auth_code>&state=<state>
```

The service exchanges the authorization code for an OAuth token, creates or updates the user account, and returns a JWT.

#### Step 3: Receive JWT Token

The callback handler returns a JWT token:

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresAt": "2025-01-16T14:30:00Z",
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com",
    "displayName": "Jane Doe"
  }
}
```

#### Step 4: Make Authenticated Requests

Include the JWT token in the `Authorization` header:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### JWT Claims

The JWT token includes the following claims:

```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "oauth_provider": "GOOGLE",
  "oauth_subject": "google|123456789",
  "roles": ["USER"],
  "iat": 1736948400,
  "exp": 1736952000
}
```

Admin users have `"roles": ["USER", "ADMIN"]`.

### Anonymous Access

Some queries work without authentication:
- `templates` - Browse calendar templates
- `template(id)` - View a specific template
- `calendar(id)` - View a public calendar (if `isPublic = true`)

---

## Example Queries

### Get Current User

Retrieve the authenticated user's profile:

```graphql
query GetCurrentUser {
  me {
    id
    email
    displayName
    profileImageUrl
    lastLoginAt
    createdAt
  }
}
```

**Response:**

```json
{
  "data": {
    "me": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "email": "jane.doe@example.com",
      "displayName": "Jane Doe",
      "profileImageUrl": "https://lh3.googleusercontent.com/a/...",
      "lastLoginAt": "2025-01-15T10:30:00Z",
      "createdAt": "2025-01-10T08:00:00Z"
    }
  }
}
```

### Browse Calendar Templates

Get all active templates, sorted by display order:

```graphql
query GetTemplates {
  templates(isActive: true) {
    id
    name
    description
    thumbnailUrl
    isFeatured
    displayOrder
  }
}
```

**Response:**

```json
{
  "data": {
    "templates": [
      {
        "id": "660e8400-e29b-41d4-a716-446655440001",
        "name": "Modern Minimalist",
        "description": "Clean design with ample white space and elegant typography",
        "thumbnailUrl": "https://cdn.villagecalendars.com/templates/modern-minimalist-thumb.jpg",
        "isFeatured": true,
        "displayOrder": 1
      },
      {
        "id": "660e8400-e29b-41d4-a716-446655440002",
        "name": "Nature Photography",
        "description": "Stunning nature photos for each month",
        "thumbnailUrl": "https://cdn.villagecalendars.com/templates/nature-photo-thumb.jpg",
        "isFeatured": true,
        "displayOrder": 2
      }
    ]
  }
}
```

### Get Template Details with Configuration

Fetch a template with its full configuration for the calendar editor:

```graphql
query GetTemplateDetails($id: ID!) {
  template(id: $id) {
    id
    name
    description
    configuration
    previewSvg
  }
}
```

**Variables:**

```json
{
  "id": "660e8400-e29b-41d4-a716-446655440001"
}
```

**Response:**

```json
{
  "data": {
    "template": {
      "id": "660e8400-e29b-41d4-a716-446655440001",
      "name": "Modern Minimalist",
      "description": "Clean design with ample white space",
      "configuration": {
        "theme": "modern",
        "font_family": "Inter",
        "font_size": 14,
        "primary_color": "#2C3E50",
        "secondary_color": "#ECF0F1",
        "show_moon_phases": true,
        "show_week_numbers": false
      },
      "previewSvg": "<svg xmlns=\"http://www.w3.org/2000/svg\">...</svg>"
    }
  }
}
```

### Get User's Calendars

Retrieve all calendars for the authenticated user:

```graphql
query GetMyCalendars {
  myCalendars {
    id
    name
    year
    status
    generatedPdfUrl
    template {
      id
      name
      thumbnailUrl
    }
    createdAt
    updatedAt
  }
}
```

**Response:**

```json
{
  "data": {
    "myCalendars": [
      {
        "id": "770e8400-e29b-41d4-a716-446655440010",
        "name": "My 2025 Calendar",
        "year": 2025,
        "status": "READY",
        "generatedPdfUrl": "https://cdn.villagecalendars.com/calendars/770e8400-e29b-41d4-a716-446655440010.pdf",
        "template": {
          "id": "660e8400-e29b-41d4-a716-446655440001",
          "name": "Modern Minimalist",
          "thumbnailUrl": "https://cdn.villagecalendars.com/templates/modern-minimalist-thumb.jpg"
        },
        "createdAt": "2025-01-14T12:00:00Z",
        "updatedAt": "2025-01-14T12:05:00Z"
      }
    ]
  }
}
```

### Get User's Orders

Retrieve all orders for the authenticated user:

```graphql
query GetMyOrders {
  myOrders {
    id
    calendar {
      id
      name
      year
    }
    quantity
    unitPrice
    totalPrice
    status
    trackingNumber
    shippingAddress
    paidAt
    shippedAt
    createdAt
  }
}
```

**Response:**

```json
{
  "data": {
    "myOrders": [
      {
        "id": "880e8400-e29b-41d4-a716-446655440020",
        "calendar": {
          "id": "770e8400-e29b-41d4-a716-446655440010",
          "name": "My 2025 Calendar",
          "year": 2025
        },
        "quantity": 2,
        "unitPrice": "24.99",
        "totalPrice": "49.98",
        "status": "SHIPPED",
        "trackingNumber": "1Z999AA10123456784",
        "shippingAddress": {
          "street": "123 Main St",
          "city": "Nashville",
          "state": "TN",
          "postalCode": "37201",
          "country": "US"
        },
        "paidAt": "2025-01-15T10:00:00Z",
        "shippedAt": "2025-01-15T14:00:00Z",
        "createdAt": "2025-01-15T10:00:00Z"
      }
    ]
  }
}
```

### Admin: Get All Orders

Retrieve all orders across all users (admin only):

```graphql
query GetAllOrders($status: OrderStatus, $limit: Int) {
  allOrders(status: $status, limit: $limit) {
    id
    user {
      id
      email
      displayName
    }
    calendar {
      id
      name
      year
    }
    quantity
    totalPrice
    status
    shippingAddress
    trackingNumber
    paidAt
    shippedAt
    createdAt
  }
}
```

**Variables:**

```json
{
  "status": "PAID",
  "limit": 20
}
```

---

## Example Mutations

### Create a Calendar

Create a new calendar based on a template:

```graphql
mutation CreateCalendar($input: CalendarInput!) {
  createCalendar(input: $input) {
    id
    name
    year
    status
    template {
      id
      name
    }
    createdAt
  }
}
```

**Variables:**

```json
{
  "input": {
    "templateId": "660e8400-e29b-41d4-a716-446655440001",
    "year": 2025,
    "name": "My 2025 Calendar",
    "configuration": {
      "primary_color": "#3498DB",
      "show_moon_phases": true
    },
    "isPublic": true
  }
}
```

**Response:**

```json
{
  "data": {
    "createCalendar": {
      "id": "770e8400-e29b-41d4-a716-446655440010",
      "name": "My 2025 Calendar",
      "year": 2025,
      "status": "GENERATING",
      "template": {
        "id": "660e8400-e29b-41d4-a716-446655440001",
        "name": "Modern Minimalist"
      },
      "createdAt": "2025-01-15T10:30:00Z"
    }
  }
}
```

**Note:** The calendar status starts as `GENERATING`. PDF generation happens asynchronously. Poll the `calendar(id)` query to check when status becomes `READY`.

### Update a Calendar

Update customization settings for an existing calendar:

```graphql
mutation UpdateCalendar($id: ID!, $input: CalendarUpdateInput!) {
  updateCalendar(id: $id, input: $input) {
    id
    name
    configuration
    status
    updatedAt
  }
}
```

**Variables:**

```json
{
  "id": "770e8400-e29b-41d4-a716-446655440010",
  "input": {
    "name": "Family Calendar 2025",
    "configuration": {
      "primary_color": "#E74C3C",
      "show_moon_phases": false
    }
  }
}
```

**Response:**

```json
{
  "data": {
    "updateCalendar": {
      "id": "770e8400-e29b-41d4-a716-446655440010",
      "name": "Family Calendar 2025",
      "configuration": {
        "primary_color": "#E74C3C",
        "show_moon_phases": false
      },
      "status": "GENERATING",
      "updatedAt": "2025-01-15T11:00:00Z"
    }
  }
}
```

**Note:** Updates trigger PDF regeneration. Status changes to `GENERATING`, then `READY` when complete.

### Create an Order (Payment Intent)

Initiate payment for ordering printed calendars:

```graphql
mutation CreateOrder(
  $calendarId: ID!,
  $quantity: Int!,
  $shippingAddress: AddressInput!
) {
  createOrder(
    calendarId: $calendarId,
    quantity: $quantity,
    shippingAddress: $shippingAddress
  ) {
    id
    clientSecret
    amount
    status
    calendarId
    quantity
  }
}
```

**Variables:**

```json
{
  "calendarId": "770e8400-e29b-41d4-a716-446655440010",
  "quantity": 2,
  "shippingAddress": {
    "street": "123 Main St",
    "city": "Nashville",
    "state": "TN",
    "postalCode": "37201",
    "country": "US"
  }
}
```

**Response:**

```json
{
  "data": {
    "createOrder": {
      "id": "pi_3KJ8YQ2eZvKYlo2C0a1b2c3d",
      "clientSecret": "pi_3KJ8YQ2eZvKYlo2C0a1b2c3d_secret_xyz123",
      "amount": 4998,
      "status": "requires_payment_method",
      "calendarId": "770e8400-e29b-41d4-a716-446655440010",
      "quantity": 2
    }
  }
}
```

**Payment Flow:**

1. Mutation returns a Stripe PaymentIntent with `clientSecret`
2. Client uses Stripe.js to complete payment:
   ```javascript
   const stripe = Stripe('pk_live_...');
   const result = await stripe.confirmCardPayment(clientSecret, {
     payment_method: { card: cardElement }
   });
   ```
3. After successful payment, Stripe sends webhook to backend
4. Backend creates the `CalendarOrder` entity
5. User can view the order via `myOrders` query

### Admin: Update Order Status

Update order status and add fulfillment details (admin only):

```graphql
mutation UpdateOrderStatus($id: ID!, $input: OrderUpdateInput!) {
  updateOrderStatus(id: $id, input: $input) {
    id
    status
    trackingNumber
    notes
    shippedAt
    updatedAt
  }
}
```

**Variables (Mark as Shipped):**

```json
{
  "id": "880e8400-e29b-41d4-a716-446655440020",
  "input": {
    "status": "SHIPPED",
    "trackingNumber": "1Z999AA10123456784",
    "notes": "Shipped via UPS Ground"
  }
}
```

**Response:**

```json
{
  "data": {
    "updateOrderStatus": {
      "id": "880e8400-e29b-41d4-a716-446655440020",
      "status": "SHIPPED",
      "trackingNumber": "1Z999AA10123456784",
      "notes": "Shipped via UPS Ground",
      "shippedAt": "2025-01-15T14:00:00Z",
      "updatedAt": "2025-01-15T14:00:00Z"
    }
  }
}
```

### Admin: Create Template

Create a new calendar template (admin only):

```graphql
mutation CreateTemplate($input: TemplateInput!) {
  createTemplate(input: $input) {
    id
    name
    description
    isActive
    isFeatured
    displayOrder
    createdAt
  }
}
```

**Variables:**

```json
{
  "input": {
    "name": "Vintage Classic",
    "description": "Classic calendar design with vintage typography",
    "configuration": {
      "theme": "vintage",
      "font_family": "Playfair Display",
      "font_size": 16,
      "primary_color": "#8B4513",
      "secondary_color": "#F5DEB3"
    },
    "isActive": true,
    "isFeatured": false,
    "displayOrder": 10
  }
}
```

---

## Error Handling

### GraphQL Error Format

Errors follow the standard GraphQL error format:

```json
{
  "errors": [
    {
      "message": "User not authenticated",
      "locations": [{ "line": 2, "column": 3 }],
      "path": ["me"],
      "extensions": {
        "code": "UNAUTHENTICATED",
        "timestamp": "2025-01-15T10:30:00Z"
      }
    }
  ],
  "data": {
    "me": null
  }
}
```

### Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `UNAUTHENTICATED` | 401 | Missing or invalid JWT token |
| `FORBIDDEN` | 403 | User lacks required role (e.g., ADMIN) |
| `NOT_FOUND` | 404 | Requested resource does not exist |
| `VALIDATION_ERROR` | 400 | Input validation failed (e.g., invalid email, negative quantity) |
| `PAYMENT_FAILED` | 402 | Stripe payment failed or was declined |
| `CONFLICT` | 409 | Operation conflicts with current state (e.g., delete calendar with paid orders) |
| `INTERNAL_ERROR` | 500 | Unexpected server error |
| `SERVICE_UNAVAILABLE` | 503 | Service temporarily unavailable (e.g., PDF generation service down) |

### Common Error Scenarios

#### Unauthenticated Request

```json
{
  "errors": [
    {
      "message": "Authentication required for this operation",
      "extensions": {
        "code": "UNAUTHENTICATED"
      }
    }
  ]
}
```

**Solution:** Include valid JWT in `Authorization: Bearer <token>` header.

#### Insufficient Permissions

```json
{
  "errors": [
    {
      "message": "User does not have required role: ADMIN",
      "extensions": {
        "code": "FORBIDDEN"
      }
    }
  ]
}
```

**Solution:** Ensure user has ADMIN role in JWT claims.

#### Resource Not Found

```json
{
  "errors": [
    {
      "message": "Calendar with id '770e8400-e29b-41d4-a716-446655440099' not found",
      "extensions": {
        "code": "NOT_FOUND",
        "resourceType": "UserCalendar",
        "resourceId": "770e8400-e29b-41d4-a716-446655440099"
      }
    }
  ]
}
```

#### Validation Error

```json
{
  "errors": [
    {
      "message": "Validation failed",
      "extensions": {
        "code": "VALIDATION_ERROR",
        "validationErrors": [
          {
            "field": "quantity",
            "message": "Quantity must be at least 1",
            "rejectedValue": 0
          },
          {
            "field": "shippingAddress.postalCode",
            "message": "Invalid postal code format",
            "rejectedValue": "1234"
          }
        ]
      }
    }
  ]
}
```

#### Payment Failed

```json
{
  "errors": [
    {
      "message": "Payment failed: card declined",
      "extensions": {
        "code": "PAYMENT_FAILED",
        "stripeErrorCode": "card_declined",
        "declineCode": "insufficient_funds"
      }
    }
  ]
}
```

---

## Rate Limiting

**Status:** Rate limiting policy is currently in development and will be implemented before production launch.

**Planned Policy:**

- **Authenticated Requests:** 1000 requests per hour per user
- **Anonymous Requests:** 100 requests per hour per IP address
- **Admin Endpoints:** 10000 requests per hour (higher limit for admin operations)

**Response Headers (Future):**

```
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 987
X-RateLimit-Reset: 1736952000
```

**Rate Limit Exceeded Response (Future):**

```json
{
  "errors": [
    {
      "message": "Rate limit exceeded. Try again in 15 minutes.",
      "extensions": {
        "code": "RATE_LIMIT_EXCEEDED",
        "retryAfter": 900
      }
    }
  ]
}
```

---

## Pagination & Filtering

### Current Implementation

The current schema uses simple list-based queries with optional filters:

- `templates(isActive: Boolean, isFeatured: Boolean)`
- `myCalendars(year: Int)`
- `myOrders(status: OrderStatus)`
- `allOrders(status: OrderStatus, limit: Int)`

**Limit Parameter:**

For admin queries with potentially large result sets, a `limit` parameter is provided:

```graphql
query GetRecentOrders {
  allOrders(limit: 20) {
    id
    status
    createdAt
  }
}
```

### Future Enhancements

**Cursor-Based Pagination (Planned for v2.0):**

```graphql
# Future schema extension
type Query {
  allOrders(
    status: OrderStatus,
    first: Int,
    after: String
  ): OrderConnection!
}

type OrderConnection {
  edges: [OrderEdge!]!
  pageInfo: PageInfo!
  totalCount: Int!
}

type OrderEdge {
  cursor: String!
  node: CalendarOrder!
}

type PageInfo {
  hasNextPage: Boolean!
  hasPreviousPage: Boolean!
  startCursor: String
  endCursor: String
}
```

---

## Additional Resources

- **GraphQL Playground:** `/graphql` (GET request in browser for development)
- **Schema Introspection:** Use GraphQL introspection query or GraphiQL
- **Stripe Integration Docs:** [Stripe Payment Intents API](https://stripe.com/docs/payments/payment-intents)
- **OAuth2 Providers:**
  - [Google OAuth 2.0](https://developers.google.com/identity/protocols/oauth2)
  - [Facebook Login](https://developers.facebook.com/docs/facebook-login)

---

## Changelog

### Version 1.0.0 (2025-01-15)

- Initial GraphQL schema release
- OAuth2 authentication with JWT
- Calendar template browsing and customization
- Order management with Stripe payment integration
- Admin operations for template and order management

---

## Support

For API support, please contact:

- **Email:** api-support@villagecalendars.com
- **Developer Slack:** #api-support
- **Issue Tracker:** https://github.com/villagecompute/calendar-service/issues
