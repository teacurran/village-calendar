# Village Calendar

[![CI](https://github.com/teacurran/village-calendar/actions/workflows/ci.yml/badge.svg)](https://github.com/teacurran/village-calendar/actions/workflows/ci.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=villagecompute_village-calendar&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=villagecompute_village-calendar)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=villagecompute_village-calendar&metric=coverage)](https://sonarcloud.io/summary/new_code?id=villagecompute_village-calendar)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=villagecompute_village-calendar&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=villagecompute_village-calendar)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=villagecompute_village-calendar&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=villagecompute_village-calendar)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.x-blue.svg)](https://quarkus.io/)
[![Vue](https://img.shields.io/badge/Vue-3-green.svg)](https://vuejs.org/)

A full-stack web application for creating beautiful, customizable year-at-a-glance calendars with moon phases, holidays, and e-commerce functionality. Built with Quarkus and Vue.js.

**Live Site:** [https://calendar.villagecompute.com](https://calendar.villagecompute.com)

## What It Does

Village Calendar lets users design and order custom wall calendars featuring:

- **Moon Phases**: Accurate lunar calculations with configurable display (major phases, full moons only, or daily illumination)
- **Holiday Sets**: Curated holiday collections including US, Canadian, UK, Jewish, Christian, Islamic, Hindu, Chinese, and more
- **Custom Styling**: Weekend color themes, text colors, grid options, and emoji customization
- **Multiple Layouts**: Left-aligned (12x31) or day-of-week aligned (12x37) layouts
- **PDF Export**: High-quality vector PDFs ready for printing
- **Print Ordering**: Integrated Stripe payments for professional printing fulfillment

## Features

- **Interactive Calendar Builder**: Step-by-step wizard with live preview
- **Astronomical Calculations**: Moon phases and illumination via SunCalc
- **Hebrew Calendar Support**: Full Hebrew date conversion with holidays
- **OAuth2 Authentication**: Sign in with Google or Facebook
- **Shopping Cart**: Save calendars, add to cart, checkout with Stripe
- **Order Management**: Admin dashboard for order fulfillment

## Tech Stack

### Backend
- **Framework**: Quarkus 3.x (Java 21)
- **Database**: PostgreSQL with Hibernate ORM/Panache
- **API**: REST (JAX-RS) + GraphQL (SmallRye)
- **Authentication**: OAuth2 via Quarkus OIDC
- **Payments**: Stripe API
- **PDF Generation**: Apache PDFBox + Batik SVG
- **Storage**: Cloudflare R2 (S3-compatible)

### Frontend
- **Framework**: Vue 3 + TypeScript
- **Build**: Vite
- **UI**: PrimeVue + TailwindCSS
- **State**: Pinia
- **Integration**: Quarkus Quinoa

## Quick Start

### Prerequisites

- Java 21
- Maven 3.8+
- Node.js 22+
- PostgreSQL 14+

### Development

```bash
# Clone and install
git clone https://github.com/villagecompute/village-calendar.git
cd village-calendar

# Start in dev mode (backend + frontend with hot reload)
./mvnw quarkus:dev
```

Access at http://localhost:8030

### Environment Variables

Create a `.env` file or set these variables:

```bash
# Required for payments
STRIPE_SECRET_KEY=sk_test_...
STRIPE_PUBLISHABLE_KEY=pk_test_...

# Required for OAuth (optional for local dev)
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
```

## Project Structure

```
village-calendar/
├── src/main/
│   ├── java/villagecompute/calendar/
│   │   ├── api/           # REST endpoints
│   │   ├── data/models/   # JPA entities
│   │   └── services/      # Business logic
│   ├── resources/
│   └── webui/             # Vue.js frontend
│       ├── src/
│       │   ├── view/      # Pages
│       │   ├── components/
│       │   └── stores/    # Pinia stores
│       └── package.json
└── pom.xml
```

## Building for Production

```bash
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

## Docker

```bash
# Build image
./mvnw package -Dquarkus.container-image.build=true

# Or with Docker Compose
docker-compose up -d
```

## API

- **GraphQL**: `/graphql` (interactive UI at `/graphql-ui`)
- **REST**: `/api/calendar/*` for calendar generation
- **Health**: `/q/health`
- **Metrics**: `/q/metrics`

## License

Private - Village Compute
