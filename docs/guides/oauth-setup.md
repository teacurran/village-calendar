# OAuth 2.0 / OpenID Connect Setup Guide

This guide provides step-by-step instructions for configuring OAuth 2.0 authentication with Google, Facebook, and Apple providers for the Village Calendar application.

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Google OAuth Setup](#google-oauth-setup)
4. [Facebook OAuth Setup](#facebook-oauth-setup)
5. [Apple OAuth Setup](#apple-oauth-setup)
6. [Local Development with ngrok](#local-development-with-ngrok)
7. [Environment Configuration](#environment-configuration)
8. [Testing OAuth Flows](#testing-oauth-flows)
9. [Troubleshooting](#troubleshooting)

## Overview

The Village Calendar application uses OAuth 2.0 / OpenID Connect (OIDC) for user authentication. After successful OAuth authentication, the application issues a JWT (JSON Web Token) that clients use for API access.

### Authentication Flow

1. User clicks "Sign in with Google/Facebook/Apple"
2. Browser redirects to `/auth/login/{provider}`
3. Quarkus OIDC redirects to provider's consent page
4. User grants permissions
5. Provider redirects back to `/auth/{provider}/callback`
6. Application creates/updates user record in database
7. Application issues JWT token (24-hour expiration)
8. Frontend receives token and stores it for API requests

### JWT Token Structure

The JWT token contains the following claims:

- `sub`: User ID (UUID)
- `iss`: "village-calendar"
- `email`: User's email address
- `name`: User's display name
- `groups`: User roles (e.g., `["USER"]` or `["USER", "ADMIN"]`)
- `exp`: Expiration timestamp (24 hours from issuance)

## Prerequisites

- Village Calendar application running locally (default port: 8030)
- PostgreSQL database configured and running
- For production: Domain with HTTPS support
- For development: ngrok installed (optional, for testing with actual OAuth providers)

## Google OAuth Setup

### 1. Create Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Click "Select a project" → "New Project"
3. Enter project name: "Village Calendar"
4. Click "Create"

### 2. Configure OAuth Consent Screen

1. Navigate to "APIs & Services" → "OAuth consent screen"
2. Select "External" user type (or "Internal" if using Google Workspace)
3. Click "Create"
4. Fill in required fields:
   - **App name**: Village Calendar
   - **User support email**: Your email
   - **Developer contact**: Your email
5. Click "Save and Continue"
6. **Scopes**: Click "Add or Remove Scopes"
   - Add: `openid`
   - Add: `profile`
   - Add: `email`
7. Click "Save and Continue"
8. **Test users** (for development): Add your Google email
9. Click "Save and Continue"

### 3. Create OAuth 2.0 Credentials

1. Navigate to "APIs & Services" → "Credentials"
2. Click "Create Credentials" → "OAuth client ID"
3. Select "Web application"
4. Configure:
   - **Name**: Village Calendar Web Client
   - **Authorized JavaScript origins**:
     - `http://localhost:8030` (development)
     - `https://your-domain.com` (production)
   - **Authorized redirect URIs**:
     - `http://localhost:8030/auth/google/callback` (development)
     - `https://your-domain.com/auth/google/callback` (production)
     - If using ngrok: `https://your-subdomain.ngrok.io/auth/google/callback`
5. Click "Create"
6. **Save the Client ID and Client Secret** – you'll need these for environment variables

### 4. Configure Application

Set environment variables:

```bash
export GOOGLE_CLIENT_ID="your-client-id.apps.googleusercontent.com"
export GOOGLE_CLIENT_SECRET="your-client-secret"
```

Or add to `.env` file (not committed to git):

```env
GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-client-secret
```

### 5. Enable Google Provider

Edit `src/main/resources/application.properties`:

```properties
quarkus.oidc.google.enabled=true
```

## Facebook OAuth Setup

### 1. Create Facebook App

1. Go to [Facebook Developers](https://developers.facebook.com/)
2. Click "My Apps" → "Create App"
3. Select "Consumer" use case
4. Click "Next"
5. Fill in app details:
   - **App name**: Village Calendar
   - **App contact email**: Your email
6. Click "Create App"

### 2. Add Facebook Login Product

1. In the app dashboard, click "Add Product"
2. Find "Facebook Login" and click "Set Up"
3. Select "Web" platform
4. Enter Site URL: `http://localhost:8030` (or your production URL)
5. Click "Save"

### 3. Configure Facebook Login Settings

1. Navigate to "Facebook Login" → "Settings"
2. Configure **Valid OAuth Redirect URIs**:
   - `http://localhost:8030/auth/facebook/callback`
   - `https://your-domain.com/auth/facebook/callback`
   - If using ngrok: `https://your-subdomain.ngrok.io/auth/facebook/callback`
3. Enable **Login with the JavaScript SDK**: Off
4. Enable **Use Strict Mode for Redirect URIs**: On
5. Click "Save Changes"

### 4. Get App Credentials

1. Navigate to "Settings" → "Basic"
2. Copy the **App ID** (this is your Client ID)
3. Click "Show" next to **App Secret** (this is your Client Secret)
4. **Save these credentials securely**

### 5. Configure Application

Set environment variables:

```bash
export FACEBOOK_CLIENT_ID="your-app-id"
export FACEBOOK_CLIENT_SECRET="your-app-secret"
```

### 6. Enable Facebook Provider

Edit `src/main/resources/application.properties`:

```properties
quarkus.oidc.facebook.enabled=true
```

### 7. App Review (Production Only)

For production, you'll need to submit your app for Facebook review:

1. Navigate to "App Review" → "Permissions and Features"
2. Request "public_profile" and "email" permissions
3. Provide required information and submit for review

During development, you can test with Test Users or users with Developer/Admin/Tester roles in your app.

## Apple OAuth Setup

### Important Notes

Apple OAuth is significantly more complex than Google/Facebook:

- Requires paid Apple Developer Program membership ($99/year)
- Uses JWT-based client authentication instead of client secrets
- Requires manual configuration of OIDC endpoints
- Limited to apps with valid bundle IDs and domain verification

### 1. Apple Developer Account Setup

1. Enroll in [Apple Developer Program](https://developer.apple.com/programs/)
2. Sign in to [Apple Developer Portal](https://developer.apple.com/account/)

### 2. Create App ID

1. Navigate to "Certificates, Identifiers & Profiles"
2. Click "Identifiers" → "+" button
3. Select "App IDs" → "Continue"
4. Select "App" → "Continue"
5. Configure:
   - **Description**: Village Calendar
   - **Bundle ID**: `com.villagecompute.calendar` (or your reverse domain)
   - Enable "Sign in with Apple" capability
6. Click "Continue" → "Register"

### 3. Create Service ID

1. Navigate to "Identifiers" → "+" button
2. Select "Services IDs" → "Continue"
3. Configure:
   - **Description**: Village Calendar Web Service
   - **Identifier**: `com.villagecompute.calendar.web` (this is your Client ID)
   - Enable "Sign in with Apple"
4. Click "Continue" → "Register"
5. Click on the Service ID you just created
6. Click "Configure" next to "Sign in with Apple"
7. Configure:
   - **Primary App ID**: Select the App ID you created earlier
   - **Domains and Subdomains**: `your-domain.com` (must be HTTPS, no localhost)
   - **Return URLs**: `https://your-domain.com/auth/apple/callback`
   - Note: For development, you may need ngrok or a test domain
8. Click "Save" → "Continue" → "Register"

### 4. Create Private Key for Client Authentication

1. Navigate to "Keys" → "+" button
2. Configure:
   - **Key Name**: Village Calendar Sign In Key
   - Enable "Sign in with Apple"
3. Click "Configure" → Select your Primary App ID
4. Click "Save" → "Continue" → "Register"
5. **Download the `.p8` key file** – you can only download this once
6. Note the **Key ID** (10 characters, e.g., `ABC1234567`)
7. Note your **Team ID** (found in top-right of Developer Portal)

### 5. Generate Client Secret (JWT)

Apple doesn't use a traditional client secret. Instead, you must generate a JWT token signed with your private key. This is complex and typically done programmatically.

**Option 1: Use a JWT generation tool**

Create a JWT with these claims:

```json
{
  "iss": "YOUR_TEAM_ID",
  "iat": 1234567890,
  "exp": 1234654290,
  "aud": "https://appleid.apple.com",
  "sub": "com.villagecompute.calendar.web"
}
```

Sign with the ES256 algorithm using your `.p8` private key.

**Option 2: Defer Apple implementation**

Due to the complexity of Apple's OAuth implementation (requiring JWT-based client authentication, HTTPS-only callbacks, and manual endpoint configuration), you may choose to:

1. Leave Apple OAuth disabled (`quarkus.oidc.apple.enabled=false`)
2. Document it as a future enhancement
3. Focus on Google and Facebook providers which are simpler to test in development

### 6. Configure Application (If Implementing Apple)

Set environment variables:

```bash
export APPLE_CLIENT_ID="com.villagecompute.calendar.web"
export APPLE_CLIENT_SECRET="generated-jwt-token"
```

### 7. Enable Apple Provider

Edit `src/main/resources/application.properties`:

```properties
quarkus.oidc.apple.enabled=true
```

## Local Development with ngrok

OAuth providers often require HTTPS callback URLs. For local development, use ngrok to create a secure tunnel:

### 1. Install ngrok

```bash
# macOS
brew install ngrok

# Or download from https://ngrok.com/download
```

### 2. Start ngrok tunnel

```bash
ngrok http 8030
```

This will output:

```
Forwarding  https://abc123.ngrok.io -> http://localhost:8030
```

### 3. Update OAuth Provider Redirect URIs

Add the ngrok URL to your OAuth app's authorized redirect URIs:

- Google: `https://abc123.ngrok.io/auth/google/callback`
- Facebook: `https://abc123.ngrok.io/auth/facebook/callback`
- Apple: `https://abc123.ngrok.io/auth/apple/callback`

### 4. Access Application via ngrok

Visit `https://abc123.ngrok.io` instead of `http://localhost:8030`

**Note**: ngrok URLs change each time you restart ngrok (unless you have a paid plan). Update your OAuth apps accordingly.

## Environment Configuration

### Development (.env file)

Create a `.env` file in the project root (not committed to git):

```env
# Google OAuth
GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-client-secret

# Facebook OAuth
FACEBOOK_CLIENT_ID=your-facebook-app-id
FACEBOOK_CLIENT_SECRET=your-facebook-app-secret

# Apple OAuth (if implemented)
APPLE_CLIENT_ID=com.villagecompute.calendar.web
APPLE_CLIENT_SECRET=your-generated-jwt-token

# Database
DB_URL=jdbc:postgresql://localhost:5532/calendar
DB_USERNAME=calendar
DB_PASSWORD=calendar

# JWT Keys
JWT_PUBLIC_KEY=classpath:/jwt-public-key.pem
JWT_PRIVATE_KEY=classpath:/jwt-private-key.pem
```

### Production (Environment Variables)

Set environment variables in your production environment:

```bash
export GOOGLE_CLIENT_ID="..."
export GOOGLE_CLIENT_SECRET="..."
export FACEBOOK_CLIENT_ID="..."
export FACEBOOK_CLIENT_SECRET="..."
export APPLE_CLIENT_ID="..."
export APPLE_CLIENT_SECRET="..."
```

### Enabling/Disabling Providers

Edit `src/main/resources/application.properties`:

```properties
# Enable/disable providers
quarkus.oidc.google.enabled=true
quarkus.oidc.facebook.enabled=true
quarkus.oidc.apple.enabled=false
```

## Testing OAuth Flows

### 1. Start the Application

```bash
./mvnw quarkus:dev
```

Check the startup logs for OIDC configuration:

```
=== OIDC Configuration ===
Google OAuth: ENABLED (Client ID: configured)
Facebook OAuth: ENABLED (Client ID: configured)
Apple OAuth: DISABLED (Client ID: NOT CONFIGURED)
==========================
```

### 2. Test Google OAuth

1. Open browser to `http://localhost:8030/auth/login/google`
2. You should be redirected to Google's consent page
3. Sign in with your Google account
4. Grant permissions
5. You should be redirected to `/auth/callback?token=eyJhbGc...`
6. Check browser console or application logs for the JWT token

### 3. Test Facebook OAuth

1. Open browser to `http://localhost:8030/auth/login/facebook`
2. You should be redirected to Facebook's login dialog
3. Sign in with your Facebook account (must be Test User or app admin during development)
4. Grant permissions
5. You should be redirected to `/auth/callback?token=eyJhbGc...`

### 4. Test Apple OAuth

1. Open browser to `https://your-domain.com/auth/login/apple` (must be HTTPS)
2. You should be redirected to Apple's Sign In page
3. Sign in with your Apple ID
4. Grant permissions
5. You should be redirected to `/auth/callback?token=eyJhbGc...`

### 5. Verify JWT Token

Use [jwt.io](https://jwt.io) to decode the JWT token:

**Expected payload:**

```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "iss": "village-calendar",
  "email": "user@example.com",
  "name": "John Doe",
  "groups": ["USER"],
  "exp": 1234567890
}
```

### 6. Test API Access with JWT

Use the JWT token to access protected GraphQL API:

```bash
curl -X POST http://localhost:8030/graphql \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"query": "{ me { id email displayName } }"}'
```

Expected response:

```json
{
  "data": {
    "me": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "email": "user@example.com",
      "displayName": "John Doe"
    }
  }
}
```

### 7. Verify User Creation in Database

Connect to PostgreSQL:

```bash
psql -h localhost -p 5532 -U calendar -d calendar
```

Query users:

```sql
SELECT id, email, display_name, oauth_provider, oauth_subject, is_admin, last_login_at
FROM calendar_user
ORDER BY created_at DESC;
```

## Troubleshooting

### Error: redirect_uri_mismatch

**Cause**: The redirect URI in your OAuth app configuration doesn't match the callback URL.

**Solution**:
1. Check the exact error message for the redirect URI that was attempted
2. Add this URI to your OAuth app's authorized redirect URIs
3. Ensure the protocol (http/https), domain, port, and path match exactly
4. No trailing slashes

### Error: invalid_client

**Cause**: Client ID or Client Secret is incorrect.

**Solution**:
1. Verify environment variables are set correctly
2. Check for extra whitespace in credentials
3. Regenerate credentials if necessary
4. Ensure the OAuth app is not deleted or suspended

### Error: Email not found in UserInfo

**Cause**: OAuth provider didn't return an email address.

**Solution**:
1. Verify "email" scope is requested (`quarkus.oidc.google.authentication.scopes=openid,profile,email`)
2. For Facebook: Ensure email permission is granted
3. For Apple: User may have chosen to hide email (Apple provides a relay email)
4. Check OAuth consent screen configuration

### Error: User is not in test mode (Facebook)

**Cause**: Facebook app is in development mode and user is not a test user/admin.

**Solution**:
1. Add user as a Test User in Facebook app settings
2. Or grant user a role (Admin, Developer, Tester)
3. Or submit app for Facebook App Review to go live

### Error: SSL/HTTPS required (Apple)

**Cause**: Apple OAuth requires HTTPS redirect URIs.

**Solution**:
1. Use ngrok for local development
2. Or deploy to a test server with HTTPS
3. Or defer Apple OAuth implementation until production deployment

### Error: Token expired

**Cause**: JWT token has expired (24-hour lifespan).

**Solution**:
1. User must re-authenticate with OAuth provider
2. Frontend should handle 401 Unauthorized responses and redirect to login
3. Consider implementing refresh tokens if needed

### Provider configuration not loading

**Cause**: Quarkus may not be reading environment variables correctly.

**Solution**:
1. Restart Quarkus application
2. Check `application.properties` syntax
3. Verify environment variable names match exactly
4. Use `.env` file with `quarkus.env.file=.env` property
5. Check startup logs for OIDC configuration status

### OAuth callback receives error instead of code

**Cause**: User denied permissions or OAuth app misconfiguration.

**Solution**:
1. Check browser URL for `?error=` parameter
2. Common errors:
   - `access_denied`: User clicked "Cancel"
   - `invalid_scope`: Requested scope not allowed
   - `server_error`: Provider issue, try again
3. Check OAuth app status (not suspended/deleted)

### OIDC discovery fails

**Cause**: Quarkus cannot reach provider's `.well-known/openid-configuration` endpoint.

**Solution**:
1. Verify internet connectivity
2. Check provider status (Google/Facebook/Apple service outages)
3. For Apple: Verify manual endpoint configuration is correct
4. Check firewall/proxy settings

### JWT signature verification fails

**Cause**: JWT signing keys don't match.

**Solution**:
1. Verify `jwt-private-key.pem` and `jwt-public-key.pem` are a matching key pair
2. Ensure keys are in PEM format
3. Regenerate keys if necessary:

```bash
# Generate private key
openssl genrsa -out jwt-private-key.pem 2048

# Extract public key
openssl rsa -in jwt-private-key.pem -pubout -out jwt-public-key.pem
```

4. Place keys in `src/main/resources/`

## Security Best Practices

1. **Never commit secrets to git**
   - Use `.env` files (add to `.gitignore`)
   - Use environment variables in production
   - Use secrets management (AWS Secrets Manager, Vault, etc.)

2. **Use HTTPS in production**
   - OAuth providers require HTTPS for production
   - Prevents token interception
   - Use valid SSL certificates

3. **Validate JWT tokens**
   - Verify signature with public key
   - Check expiration timestamp
   - Verify issuer matches "village-calendar"

4. **Rotate secrets regularly**
   - Regenerate OAuth client secrets periodically
   - Rotate JWT signing keys (requires user re-authentication)

5. **Monitor OAuth usage**
   - Check for unusual authentication patterns
   - Monitor failed authentication attempts
   - Set up alerts for quota limits

6. **Limit OAuth scopes**
   - Only request necessary permissions
   - Minimal: `openid`, `email`, `profile`
   - Avoid requesting write permissions unless needed

## Additional Resources

- [Google OAuth 2.0 Documentation](https://developers.google.com/identity/protocols/oauth2)
- [Facebook Login Documentation](https://developers.facebook.com/docs/facebook-login)
- [Apple Sign In Documentation](https://developer.apple.com/sign-in-with-apple/)
- [Quarkus OIDC Guide](https://quarkus.io/guides/security-oidc-code-flow-authentication)
- [JWT.io](https://jwt.io) - JWT debugger
- [OpenID Connect Specification](https://openid.net/specs/openid-connect-core-1_0.html)
