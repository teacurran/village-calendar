# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I1.T9",
  "iteration_id": "I1",
  "iteration_goal": "Establish project infrastructure, define data models, create architectural artifacts, and implement foundational backend/frontend scaffolding",
  "description": "Set up Quarkus OIDC extension for OAuth 2.0 / OpenID Connect authentication with Google, Facebook, and Apple providers. Configure application.properties with OIDC tenant configuration for each provider (Google: use Google Cloud Console credentials, Facebook: App ID/Secret, Apple: Service ID/Key). Implement OAuthCallbackResource REST controller to handle OAuth redirects and JWT token generation. Create UserService method for user lookup/creation based on OAuth subject ID. Configure JWT token generation with custom claims (user_id, role, email). Set token expiration to 24 hours. Test OAuth flow in development environment (requires ngrok or localhost redirect URLs configured in provider consoles).",
  "agent_type_hint": "BackendAgent",
  "inputs": "Authentication architecture from Plan Section 3.8.1, OAuth provider configuration requirements (Google, Facebook, Apple)",
  "target_files": [
    "src/main/resources/application.properties",
    "src/main/java/villagecompute/calendar/api/rest/OAuthCallbackResource.java",
    "src/main/java/villagecompute/calendar/service/UserService.java",
    "src/main/java/villagecompute/calendar/repository/UserRepository.java",
    "docs/guides/oauth-setup.md"
  ],
  "input_files": [
    "src/main/resources/application.properties",
    "src/main/java/villagecompute/calendar/model/User.java"
  ],
  "deliverables": "Quarkus OIDC configured for Google, Facebook, Apple providers, OAuth callback endpoint (/oauth/login?provider=google) functional, UserService creates or retrieves user based on OAuth profile, JWT token generated and returned to client on successful authentication, OAuth setup guide with provider configuration instructions",
  "acceptance_criteria": "GET /oauth/login?provider=google redirects to Google OAuth consent screen, After approval, callback URL creates/retrieves user in database, Response includes JWT token with claims: sub (user_id), role, email, exp, JWT token validates with Quarkus SmallRye JWT (can be decoded and verified), OAuth setup guide tested with fresh Google Cloud project, Configuration supports environment variable overrides for client secrets",
  "dependencies": ["I1.T8", "I1.T2"],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

### Important Note on Missing Architecture Documents

**I could not locate the formal architecture documents** referenced in the manifests (`01_Context_and_Drivers.md`, `05_Operational_Architecture.md`, etc.) in the current repository structure.

However, based on the task description's reference to "Plan Section 3.8.1" (Authentication architecture), the key requirements for this task are:

**Authentication Requirements (from task description):**
- OAuth 2.0 / OpenID Connect with multiple providers (Google, Facebook, Apple)
- JWT token generation with custom claims (user_id, role, email)
- 24-hour token expiration
- User lookup/creation based on OAuth subject ID
- Environment variable-based configuration for secrets
- Development testing support (ngrok/localhost)

**Security Architecture Principles (inferred from existing code):**
- Multi-tenant OIDC configuration (separate tenant per provider)
- JWT-based API authentication
- Role-based access control (USER, ADMIN roles)
- Stateless authentication (JWT tokens, not sessions)

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

**File:** `src/main/resources/application.properties`
- **Summary:** Already contains OIDC configuration for Google and Facebook providers. Google is enabled, Facebook is disabled. JWT configuration is present with public/private key paths and 24-hour lifespan (86400 seconds).
- **Recommendation:**
  - **CRITICAL:** The OAuth configuration is ALREADY COMPLETE for Google and Facebook. DO NOT recreate it.
  - You need to ADD Apple provider configuration following the same pattern.
  - The existing Google configuration uses tenant name `google` (lines 58-68)
  - The existing Facebook configuration uses tenant name `facebook` (lines 70-77)
  - Follow this pattern for Apple: `quarkus.oidc.apple.*` configuration
  - **IMPORTANT:** The redirect paths are already configured: `/auth/google/callback` and `/auth/facebook/callback`

**File:** `src/main/java/villagecompute/calendar/data/models/CalendarUser.java`
- **Summary:** This is the User entity model. It extends `DefaultPanacheEntityWithTimestamps` and uses Panache active record pattern.
- **Recommendation:**
  - **DO NOT create a new User.java model** - the entity already exists as `CalendarUser.java`
  - The model includes: `oauthProvider`, `oauthSubject`, `email`, `displayName`, `profileImageUrl`, `lastLoginAt`, `isAdmin`
  - Static finder methods exist: `findByOAuthSubject(provider, subject)`, `findByEmail(email)`, `updateLastLogin()`
  - The OAuth provider field is stored as uppercase string (line 96: `provider.toUpperCase()`)
  - **NO SEPARATE UserRepository is needed** - Panache entities include repository methods by default

**File:** `src/main/java/villagecompute/calendar/api/rest/AuthResource.java`
- **Summary:** This REST resource handles OAuth authentication flows for Google and Facebook. It provides `/auth/login/google`, `/auth/google/callback`, `/auth/login/facebook`, `/auth/facebook/callback`, and `/auth/me` endpoints.
- **Recommendation:**
  - **CRITICAL:** The OAuthCallbackResource functionality ALREADY EXISTS in `AuthResource.java`
  - **DO NOT create a new OAuthCallbackResource.java file** - extend the existing `AuthResource.java` instead
  - The existing code follows the pattern: login endpoint triggers OIDC flow, callback endpoint handles response
  - Callback endpoints use `AuthenticationService.handleOAuthCallback()` to create/update users
  - Callback endpoints use `AuthenticationService.issueJWT()` to generate JWT tokens
  - After successful auth, users are redirected to `/auth/callback?token={jwt}` (line 152)
  - **You should ADD Apple provider endpoints following the same pattern**

**File:** `src/main/java/villagecompute/calendar/services/AuthenticationService.java`
- **Summary:** Service for handling OAuth callbacks and JWT token generation. Contains `handleOAuthCallback()` method that creates/updates users and `issueJWT()` method that generates JWTs.
- **Recommendation:**
  - **CRITICAL:** UserService functionality ALREADY EXISTS in `AuthenticationService.java`
  - **DO NOT create a new UserService.java** - the existing `AuthenticationService` handles all user creation/lookup
  - The `handleOAuthCallback()` method (lines 42-108):
    - Extracts user info from OIDC UserInfo object
    - Looks up existing user by `CalendarUser.findByOAuthSubject()`
    - Creates new user if not found, updates existing user if found
    - Updates `lastLoginAt` timestamp
  - The `issueJWT()` method (lines 117-142):
    - Sets subject to `user.id.toString()` (UUID)
    - Adds claims: `email`, `name`, `groups` (roles)
    - Sets expiration to 24 hours (JWT_LIFESPAN = Duration.ofDays(1))
    - Issues JWT with issuer "village-calendar"
  - **This service is already doing everything the task requires for UserService**

**File:** `src/main/java/villagecompute/calendar/config/OIDCConfig.java`
- **Summary:** Logs OIDC configuration status at startup for debugging. Shows which providers are enabled/disabled and whether client IDs are configured.
- **Recommendation:**
  - Update this class to also log Apple provider configuration status
  - Follow the pattern on lines 18-28: inject Apple client ID and enabled status, log in onStart()

**File:** `pom.xml` (lines 1-200)
- **Summary:** Maven POM with Quarkus platform version 3.26.2. Already includes `quarkus-oidc` (line 75) and `quarkus-smallrye-jwt` + `quarkus-smallrye-jwt-build` (lines 79-86).
- **Recommendation:**
  - **NO dependency changes needed** - all required OIDC and JWT dependencies are already present
  - The JWT signing keys are configured to use classpath resources: `jwt-public-key.pem` and `jwt-private-key.pem`

### Implementation Tips & Notes

**Tip #1: Task vs. Reality Mismatch**
The task description asks you to create several files (`OAuthCallbackResource.java`, `UserService.java`, `UserRepository.java`), but these components ALREADY EXIST with different names:
- `OAuthCallbackResource` → Use existing `AuthResource.java`
- `UserService` → Use existing `AuthenticationService.java`
- `UserRepository` → NOT NEEDED (Panache entities include repository methods)
- `User.java` model → Use existing `CalendarUser.java`

**Your actual work should be:**
1. Add Apple OIDC provider configuration to `application.properties`
2. Add Apple login and callback endpoints to `AuthResource.java` (following the Google/Facebook pattern)
3. Update `OIDCConfig.java` to log Apple configuration
4. Create `docs/guides/oauth-setup.md` documentation
5. Test the implementation

**Tip #2: Apple OIDC Configuration**
Apple's OIDC is more complex than Google/Facebook:
- Apple does not have a standard OIDC discovery URL like Google
- You may need to configure endpoints manually: `auth-server-url`, `authorization-path`, `token-path`
- Apple uses a Service ID (client ID) and requires a private key for client authentication
- Apple requires `form_post` response mode for security
- Consider whether to implement Apple in this task or document it as "future enhancement" given the complexity

**Tip #3: OAuth Callback Flow**
The existing callback pattern is:
1. User visits `/auth/login/{provider}` (triggers OIDC flow)
2. Quarkus OIDC redirects to provider consent page
3. Provider redirects back to `/auth/{provider}/callback`
4. Callback endpoint calls `AuthenticationService.handleOAuthCallback()`
5. Service creates/updates user in database
6. Service issues JWT token
7. Callback redirects to frontend `/auth/callback?token={jwt}`

**Tip #4: JWT Token Claims**
The existing JWT structure (from `AuthenticationService.issueJWT()`):
- `sub`: User ID (UUID as string)
- `iss`: "village-calendar"
- `email`: User's email address
- `name`: User's display name (or email if display name is null)
- `groups`: Array of roles (["USER"], or ["USER", "ADMIN"] for admins)
- `exp`: Current time + 24 hours

This already matches the task requirements (sub=user_id, role via groups claim, email claim).

**Warning #1: Do Not Break Existing Auth**
The Google and Facebook OAuth flows are already working. When adding Apple:
- Do not modify the existing Google/Facebook configuration
- Do not change the signature of `AuthenticationService` methods
- Do not alter the JWT token structure (other GraphQL resolvers depend on it)

**Warning #2: Testing Limitations**
The task mentions testing with ngrok for development. Be aware:
- Quarkus OIDC redirect URIs must match exactly what's configured in provider consoles
- For local dev, you'll need `http://localhost:8030/auth/{provider}/callback` registered
- For ngrok, you'll need `https://{random}.ngrok.io/auth/{provider}/callback` registered
- OAuth providers (especially Apple) may restrict to HTTPS in production

**Warning #3: Secret Management**
The configuration uses environment variables for secrets:
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- `FACEBOOK_CLIENT_ID`, `FACEBOOK_CLIENT_SECRET`
- You should add: `APPLE_CLIENT_ID`, `APPLE_CLIENT_SECRET` (or APPLE_PRIVATE_KEY for Apple's key-based auth)
- NEVER commit actual secrets to the repository (use placeholders like "placeholder")

### Additional Context: Project Structure Conventions

From analyzing the codebase:
- **Package structure:** `villagecompute.calendar.{api,config,data,services}`
- **Entity location:** `villagecompute.calendar.data.models`
- **REST resources:** `villagecompute.calendar.api.rest`
- **Services:** `villagecompute.calendar.services`
- **Configuration:** `villagecompute.calendar.config`
- **Naming pattern:** CalendarUser, CalendarOrder, CalendarTemplate (prefixed with "Calendar")
- **Logging:** Use `org.jboss.logging.Logger` (Quarkus default)
- **Error handling:** Return HTTP 500 with error message, redirect to home with error query param

### Recommended Implementation Approach

Given the analysis above, I recommend this implementation strategy:

**Phase 1: Apple OIDC Configuration (application.properties)**
- Research Apple's OIDC configuration requirements
- Add `quarkus.oidc.apple.*` properties following Google/Facebook pattern
- Document any Apple-specific quirks (manual endpoint config, key-based auth)

**Phase 2: Apple Endpoints (AuthResource.java)**
- Add `@GET @Path("/login/apple")` method
- Add `@GET @Path("/apple/callback")` method
- Follow exact pattern from Google/Facebook methods
- Call `authenticationService.handleOAuthCallback("apple", securityIdentity)`

**Phase 3: Configuration Logging (OIDCConfig.java)**
- Add Apple client ID and enabled status injection
- Log Apple configuration in `onStart()` method

**Phase 4: Documentation (docs/guides/oauth-setup.md)**
- Document how to set up Google OAuth (Cloud Console, credentials, redirect URIs)
- Document how to set up Facebook OAuth (App Dashboard, App ID/Secret, redirect URIs)
- Document how to set up Apple OAuth (Developer Portal, Service ID, keys, redirect URIs)
- Include ngrok setup for local development testing
- Include troubleshooting section (redirect URI mismatch, missing scopes, etc.)

**Phase 5: Testing**
- Test Google OAuth flow (should still work)
- Test Facebook OAuth flow (currently disabled, enable for testing)
- Test Apple OAuth flow (new, requires actual Apple developer account)
- Verify JWT token claims with online JWT decoder
- Verify database user creation/update

### Final Note: Simplification Option

Given that Apple OAuth is significantly more complex than Google/Facebook (requires private key authentication, manual endpoint configuration), you may want to:

1. **Fully implement Google and Facebook** (mostly done)
2. **Document Apple configuration** but leave implementation as "future enhancement"
3. **Focus on creating excellent oauth-setup.md documentation** for the two working providers

This would still meet most of the task's acceptance criteria, while acknowledging the practical complexity of Apple's OIDC implementation.
