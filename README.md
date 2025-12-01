# Village Calendar Service

A microservice for generating custom calendars with astronomical events, Hebrew calendar support, and e-commerce functionality. Built with Quarkus and Vue.js as part of the VillageCompute platform.

## Architecture Overview

The Village Calendar Service is designed as a standalone microservice with OAuth2 authentication, GraphQL-first API design, and cloud storage integration. The service architecture consists of four core service layers:

1. **Authentication Service**: OAuth2 integration with Google and Facebook via Quarkus OIDC
2. **Calendar Generation Service**: PDF rendering with astronomical calculations (moon phases, Hebrew calendar)
3. **Template Service**: Admin-managed calendar template configurations with JSONB storage
4. **Order Service**: Stripe payment integration with email notifications

See the [Component Architecture Diagram](docs/diagrams/component-architecture.puml) for a detailed view of the system design.

### Key Design Decisions

- **GraphQL Primary API**: SmallRye GraphQL provides the main API interface with GraphiQL UI for development
- **OAuth2 Authentication**: Replaces session-based auth for better microservice integration
- **Cloudflare R2 Storage**: S3-compatible object storage for generated PDFs
- **Separate Database**: Independent PostgreSQL instance from main VillageCMS application
- **Containerized Deployment**: Docker + Kubernetes (K3s) with health checks and metrics

## Features

- **Custom Calendar Generation**: Configurable themes, sizes, and content options
- **Astronomical Events**: Moon phases, solar/lunar illumination calculations via SunCalc
- **Hebrew Calendar**: Full Hebrew calendar support with holiday integration
- **E-Commerce**: Shopping cart, Stripe payments, order fulfillment workflow
- **PDF Export**: High-quality PDF generation with Apache PDFBox and Batik
- **Template Management**: Admin-only template CRUD with preview images
- **OAuth2 Login**: Google and Facebook authentication

## Tech Stack

### Backend
- **Framework**: Quarkus 3.26.2 (Java 21)
- **ORM**: Hibernate ORM with Panache
- **Database**: PostgreSQL 15 (separate instance)
- **API**: GraphQL (SmallRye GraphQL) + REST (JAX-RS)
- **Authentication**: OAuth2 via Quarkus OIDC (Google, Facebook)
- **Payments**: Stripe API v29.5.0
- **PDF Generation**: Apache PDFBox 3.0.3, Apache Batik 1.17
- **Astronomical**: SunCalc 3.11, Proj4J 1.3.0
- **Storage**: Cloudflare R2 (S3-compatible, AWS SDK 2.20.162)
- **Metrics**: Micrometer + Prometheus
- **Email**: Quarkus Mailer (SMTP)

### Frontend
- **Framework**: Vue 3.5 + TypeScript
- **Build**: Vite 6.0
- **UI**: PrimeVue 4.2, TailwindCSS 3.4
- **Router**: Vue Router 4.5
- **State**: Pinia 2.3
- **i18n**: Vue I18n 10.0
- **Integration**: Quinoa 2.6.2 (Quarkus frontend integration)

## Prerequisites

- Java 21
- Maven 3.8+
- Node.js 22+ and npm 10+
- PostgreSQL 14+

## Setup

### 1. Database Setup

Create a PostgreSQL database:

```sql
CREATE DATABASE village_calendar;
CREATE USER calendar_user WITH PASSWORD 'calendar_pass';
GRANT ALL PRIVILEGES ON DATABASE village_calendar TO calendar_user;
```

### 2. Configure Environment Variables

Create a `.env` file (optional) with:

```
STRIPE_SECRET_KEY=sk_test_...
STRIPE_PUBLISHABLE_KEY=pk_test_...
```

### 3. Install Dependencies

```bash
# Backend dependencies are managed by Maven
./mvnw install

# Frontend dependencies
cd src/main/webui
npm install
cd ../../..
```

## Development

### Run in Development Mode

The easiest way to run the application:

```bash
mvn quarkus:dev
```

This will:
- Start Quarkus backend on port **8030**
- Start Vue dev server on port 5176 (automatically proxied)
- Enable hot reload for both backend and frontend

Access the application at:
- **Main Application**: http://localhost:8030
- **GraphiQL UI**: http://localhost:8030/graphql-ui
- **Health Check**: http://localhost:8030/q/health
- **Metrics**: http://localhost:8030/q/metrics

### Frontend-Only Development

To run only the frontend (requires backend running separately):

```bash
cd src/main/webui
npm run dev
```

Access at: http://localhost:5176

## Building for Production

```bash
# Build everything
./mvnw package

# The built application will be in target/quarkus-app/
```

Run the production build:

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

## API Overview

### GraphQL API (Primary)

The service provides a GraphQL API as the primary interface. Access the interactive GraphiQL UI at:
- **Development**: http://localhost:8030/graphql-ui
- **GraphQL Endpoint**: http://localhost:8030/graphql

The GraphQL schema includes:
- **Queries**: `templates`, `template(id)`, `myCalendars`, `orders`, `order(id)`
- **Mutations**: `generateCalendar`, `createTemplate`, `updateTemplate`, `deleteTemplate`, `createOrder`, `confirmPayment`

See the GraphiQL UI for full schema documentation and interactive query building.

### REST Endpoints

REST endpoints are used for specific integrations:
- `GET /auth/google/callback` - Google OAuth2 callback
- `GET /auth/facebook/callback` - Facebook OAuth2 callback
- `POST /webhooks/stripe` - Stripe payment webhook
- `GET /q/health` - Health check (readiness/liveness)
- `GET /q/metrics` - Prometheus metrics

### Legacy REST Endpoints (Deprecated)

These endpoints are maintained for backward compatibility but will be removed in future versions:
- `POST /api/calendar/generate` - Use GraphQL `generateCalendar` mutation instead
- `POST /api/calendar/generate-json` - Use GraphQL query instead
- `POST /api/calendar/generate-pdf` - Use GraphQL `generateCalendar` mutation instead
- `GET /api/calendar/themes` - Use GraphQL `templates` query instead

## Project Structure

```
village-calendar/
├── src/
│   ├── main/
│   │   ├── java/villagecompute/calendar/
│   │   │   ├── api/              # REST endpoints
│   │   │   ├── data/models/      # JPA entities
│   │   │   └── services/         # Business logic
│   │   ├── resources/
│   │   │   └── application.properties
│   │   └── webui/                # Vue.js frontend
│   │       ├── src/
│   │       │   ├── view/         # Vue pages
│   │       │   ├── components/   # Vue components
│   │       │   ├── stores/       # Pinia stores
│   │       │   └── i18n/         # Translations
│   │       ├── package.json
│   │       └── vite.config.ts
│   └── test/                     # Tests
├── pom.xml
└── README.md
```

## Docker Deployment

### Build Container Image

```bash
# Build with Jib (optimized layers)
mvn clean package -Dquarkus.container-image.build=true

# Or use the Dockerfile directly
docker build -t village-calendar:latest .
```

### Run with Docker Compose

A `docker-compose.yml` file is provided for local development with all dependencies:

```bash
docker-compose up -d
```

This starts:
- PostgreSQL database (port 5432)
- Village Calendar service (port 8030)

### Environment Variables

Key environment variables for production deployment:

```bash
# Database
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://db:5432/village_calendar
QUARKUS_DATASOURCE_USERNAME=calendar_user
QUARKUS_DATASOURCE_PASSWORD=<secure-password>

# OAuth2
GOOGLE_CLIENT_ID=<google-oauth-client-id>
GOOGLE_CLIENT_SECRET=<google-oauth-secret>
FACEBOOK_CLIENT_ID=<facebook-oauth-client-id>
FACEBOOK_CLIENT_SECRET=<facebook-oauth-secret>

# Stripe
STRIPE_SECRET_KEY=<stripe-secret-key>
STRIPE_PUBLISHABLE_KEY=<stripe-publishable-key>
STRIPE_WEBHOOK_SECRET=<stripe-webhook-secret>

# Cloudflare R2
R2_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com
R2_BUCKET=calendar-pdfs
R2_ACCESS_KEY=<r2-access-key>
R2_SECRET_KEY=<r2-secret-key>

# Email
MAIL_HOST=smtp.sendgrid.net
MAIL_PORT=587
MAIL_USERNAME=apikey
MAIL_PASSWORD=<sendgrid-api-key>
MAIL_FROM=calendars@villagecompute.com
MAIL_MOCK=false
```

## Authentication Flow

The service uses OAuth2 for user authentication:

1. User clicks "Login with Google" or "Login with Facebook"
2. Frontend redirects to `/auth/{provider}` endpoint
3. Quarkus OIDC handles OAuth2 flow and callback
4. JWT token issued and returned to frontend
5. Frontend includes JWT in `Authorization: Bearer <token>` header for all GraphQL requests

## Development Roadmap

- **Iteration 2 (I2)**: Foundation setup, database schema, OAuth2, GraphQL API ✅ (In Progress)
- **Iteration 3 (I3)**: Template management UI, calendar customization, order workflow
- **Iteration 4 (I4)**: Production deployment, monitoring, performance optimization

## Notes

- Microservice extracted from VillageCMS project with architectural improvements
- OAuth2 authentication replaces session-based auth for scalability
- GraphQL-first API design for better frontend integration
- Separate PostgreSQL database instance for data isolation
- Cloud-native deployment with Docker + Kubernetes (K3s)

## Future Feature Considerations

The following calendar configuration options are available in the backend but not yet exposed in the user-facing wizard. These could be added in future iterations:

### Calendar Type & Basic Settings
- **Calendar Type**: Hebrew calendar support (currently defaults to Gregorian)
- **Year Selection**: Manual year input (currently auto-detects)
- **Theme Selection**: Direct theme picker (wizard uses weekend color themes instead)

### Display Options
- **Show Week Numbers**: Display ISO week numbers
- **Compact Mode**: Condensed calendar layout
- **Emoji Position**: Configure where emojis appear in calendar cells (top-left, top-right, bottom-left, bottom-right)
- **Show Day Numbers (1-31)**: Toggle day number display
- **Highlight Weekends**: Toggle weekend highlighting

### Moon & Location Settings
- **Observer Location**: City selection or manual latitude/longitude for accurate moon rotation
- **Use Current Location**: Geolocation-based moon positioning
- **Moon Size**: Configurable moon radius in pixels
- **Moon X/Y Position**: Fine-tune moon placement within cells
- **Moon Border Color/Width**: Moon outline customization

### Additional Color Options
- **Holiday Text Color**: Color for holiday labels
- **Custom Date Text Color**: Color for user-added event labels

## License

Private - Village Compute
