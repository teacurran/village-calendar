# Development Setup Guide

## Prerequisites

- Java 21+
- Maven 3.8+
- Node.js 22.19+
- PostgreSQL (running via Docker or locally)
- Docker (for running PostgreSQL)

## Quick Start

### 1. Start the Database

```bash
# The database is already running at localhost:5532
docker ps | grep calendar-main-db-1
```

### 2. Configure OAuth Credentials

Create a local environment file:

```bash
# Copy the template
cp .env.development .env.development.local

# Edit with your credentials
nano .env.development.local  # or use your preferred editor
```

Get Google OAuth credentials:
1. Go to https://console.cloud.google.com/apis/credentials
2. Create OAuth 2.0 Client ID (or use existing)
3. Add authorized redirect URI: `http://localhost:8030/auth/google/callback`
4. Copy Client ID and Secret to `.env.development.local`

**Minimum required for OAuth login:**
```bash
export GOOGLE_CLIENT_ID="YOUR_CLIENT_ID.apps.googleusercontent.com"
export GOOGLE_CLIENT_SECRET="GOCSPX-YOUR_SECRET"
```

### 3. Load Environment Variables

```bash
# Source the environment file
source .env.development.local
```

### 4. Run Migrations

```bash
cd migrations
mvn org.mybatis.maven:migrations-maven-plugin:up -Dmigration.env=development
cd ..
```

### 5. Start the Application

```bash
# From the project root
mvn quarkus:dev
```

The application will be available at:
- **Frontend**: http://localhost:8030
- **GraphQL UI**: http://localhost:8030/graphql-ui
- **Swagger UI**: http://localhost:8030/q/swagger-ui
- **Bootstrap Admin**: http://localhost:8030/bootstrap

### 6. Create First Admin User

1. Navigate to http://localhost:8030/bootstrap
2. Click "Login with Google"
3. After authentication, return to /bootstrap
4. Select your user and click "Create Admin Account"

## Environment Variables Reference

### Required (for OAuth login)
- `GOOGLE_CLIENT_ID` - Google OAuth Client ID
- `GOOGLE_CLIENT_SECRET` - Google OAuth Client Secret

### Optional Development Settings
- `MAIL_MOCK=true` - Mock email sending (default: true)
- `STRIPE_SECRET_KEY` - Stripe test key (for checkout testing)
- `JWT_PRIVATE_KEY` - Custom JWT private key (uses classpath default if not set)

## Common Issues

### "placeholder" in OAuth redirect

**Problem**: Google OAuth shows "invalid_client" error with "placeholder" in URL

**Solution**: Make sure you:
1. Created `.env.development.local` with your Google credentials
2. Ran `source .env.development.local` before starting the app
3. Restarted `mvn quarkus:dev` after setting environment variables

Verify the variable is set:
```bash
echo $GOOGLE_CLIENT_ID
```

### Database Connection Error

**Problem**: Can't connect to PostgreSQL

**Solution**: Check the database is running:
```bash
docker ps | grep calendar
# Should show: calendar-main-db-1 with port 0.0.0.0:5532->5432/tcp
```

If not running, check your docker-compose setup.

### Migration Errors

**Problem**: Migration fails with "relation already exists"

**Solution**: The migrations track what's already applied. Check status:
```bash
cd migrations
mvn org.mybatis.maven:migrations-maven-plugin:status -Dmigration.env=development
```

## Project Structure

```
village-calendar/
├── src/main/
│   ├── java/villagecompute/calendar/
│   │   ├── api/              # REST and GraphQL endpoints
│   │   ├── data/             # JPA entities and repositories
│   │   ├── services/         # Business logic
│   │   └── config/           # Configuration classes
│   ├── resources/
│   │   ├── application.properties  # Main config
│   │   └── templates/        # Email templates (Qute)
│   └── webui/src/            # Vue.js frontend
│       ├── view/             # Vue pages
│       ├── components/       # Vue components
│       └── stores/           # Pinia stores
├── migrations/               # Database migrations
│   └── src/main/resources/
│       ├── scripts/          # SQL migration scripts
│       └── environments/     # DB connection configs
└── .env.development.local    # Your local secrets (gitignored)
```

## Next Steps

- Review the [GraphQL Schema](http://localhost:8030/graphql-ui) for API documentation
- Check out the [Swagger UI](http://localhost:8030/q/swagger-ui) for REST endpoints
- Read `SECURITY_CLEANUP.md` if setting up for the first time
