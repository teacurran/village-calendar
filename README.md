# Village Calendar

A standalone Quarkus + Vue.js application for generating custom calendars with moon phases, Hebrew calendar support, and e-commerce functionality.

## Features

- **Calendar Generator**: Create custom calendars with configurable themes and options
- **Moon Phases**: Display accurate moon phases and illumination
- **Hebrew Calendar**: Support for Hebrew calendar with holidays
- **Shopping Cart**: Order printed calendars
- **PDF Export**: Download calendars as PDF

## Tech Stack

### Backend
- Quarkus 3.26.2 (Java 21)
- Hibernate ORM with Panache
- PostgreSQL
- JAX-RS (REST)
- Stripe (for payments)

### Frontend
- Vue 3 + TypeScript
- Vite
- PrimeVue UI components
- Tailwind CSS
- Vue Router
- Pinia (state management)
- Vue I18n (internationalization)

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
./mvnw quarkus:dev
```

This will:
- Start Quarkus backend on port 8031
- Start Vue dev server on port 5176 (automatically proxied)
- Enable hot reload for both backend and frontend

Access the application at: http://localhost:8031

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

## API Endpoints

- `POST /api/calendar/generate` - Generate SVG calendar
- `POST /api/calendar/generate-json` - Generate calendar with JSON response
- `POST /api/calendar/generate-pdf` - Generate PDF calendar
- `GET /api/calendar/themes` - Get available themes

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

## Notes

- This is a standalone version extracted from the VillageCMS project
- User authentication has been removed for simplicity
- Session-based cart functionality is retained
- Calendar templates can be managed via API

## License

Private - Village Compute
