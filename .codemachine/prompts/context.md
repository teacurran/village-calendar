# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I2.T9",
  "iteration_id": "I2",
  "iteration_goal": "Implement calendar creation, editing, and template system. Build calendar editor UI components. Integrate astronomical calculations (moon phases, Hebrew calendar). Create sequence diagrams for key workflows",
  "description": "Implement guest session management allowing anonymous users to create calendars before authenticating. Create SessionService for managing CalendarSession entities: createSession (generate UUID, store in localStorage), saveCalendarToSession (persist calendar data in JSONB session_data field), convertSessionToUser (on login, transfer session calendars to user account). Update CalendarService.createCalendar to accept optional sessionId (if user not authenticated). Update frontend to store sessionId in localStorage, include in GraphQL mutations if user not logged in. Implement session expiration (30 days, cleanup job deletes expired sessions). Add \"Sign in to save\" prompt in calendar editor for guest users.",
  "agent_type_hint": "BackendAgent",
  "inputs": "Guest session requirements from Plan Section \"User Experience\", CalendarSession entity from I1.T8",
  "target_files": [
    "src/main/java/villagecompute/calendar/services/SessionService.java",
    "src/main/java/villagecompute/calendar/repository/SessionRepository.java",
    "src/main/java/villagecompute/calendar/api/graphql/SessionResolver.java",
    "frontend/src/utils/session.ts",
    "src/test/java/villagecompute/calendar/service/SessionServiceTest.java"
  ],
  "input_files": [
    "src/main/java/villagecompute/calendar/model/CalendarSession.java",
    "src/main/java/villagecompute/calendar/service/CalendarService.java",
    "frontend/src/stores/user.ts"
  ],
  "deliverables": "SessionService with CRUD and conversion methods, CalendarService supports sessionId parameter for guest users, Frontend stores sessionId in localStorage, Session-to-user conversion on login (GraphQL mutation), Session expiration cleanup job (Quarkus Scheduler), Unit tests for session service",
  "acceptance_criteria": "Guest user can create calendar without logging in, Calendar saved to session (CalendarSession entity in database), sessionId persisted in browser localStorage, On login, convertGuestSession mutation transfers calendars to user account, Expired sessions (>30 days old) deleted by cleanup job, Frontend prompts guest user to sign in to save permanently",
  "dependencies": [
    "I2.T2",
    "I1.T9"
  ],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: nfr-usability (from 01_Context_and_Drivers.md)

```markdown
<!-- anchor: nfr-usability -->
#### 2.2.6. Usability

**Requirements:**
- Intuitive calendar editor requiring minimal onboarding
- Seamless guest-to-authenticated user conversion (preserve session data)
- Mobile-responsive design (primary focus: desktop, but functional on tablets/phones)
- Accessible UI (WCAG 2.1 AA target for public pages)
- Real-time feedback on user actions (save confirmations, validation errors)

**Architectural Impact:**
- Vue 3 Composition API for reactive UI components
- PrimeVue UI library for consistent design system
- LocalStorage + database sync for session persistence
- Optimistic UI updates with background API calls
- Form validation both client-side (Vue) and server-side (Quarkus)
```

### Context: data-model-overview (from 03_System_Structure_and_Data.md)

```markdown
<!-- anchor: data-model-overview -->
### 3.6. Data Model Overview & ERD

**Description:**

The data model is optimized for the calendar creation and e-commerce workflows, with careful consideration for session persistence (anonymous users), job processing, and analytics. PostgreSQL's JSONB type is used for flexible calendar metadata (event details, configuration options) while maintaining relational integrity for core entities.

**Key Design Decisions:**

1. **User Identity**: `users` table stores OAuth provider info (`oauth_provider`, `oauth_subject_id`) to support multiple providers per user
2. **Anonymous Sessions**: `calendar_sessions` table tracks guest user calendars, linked to `users` table upon login conversion
3. **Calendar Versioning**: `calendars` table includes `version` field for optimistic locking, future support for edit history
4. **Order Status**: `orders.status` enum (PENDING, PAID, IN_PRODUCTION, SHIPPED, DELIVERED, CANCELLED, REFUNDED) drives workflow state machine
5. **Job Queue**: `delayed_jobs` table with `locked_at`, `locked_by`, `attempts`, `last_error` supports distributed worker coordination
6. **Templates**: `calendar_templates` is separate from `calendars` to enable admin-curated vs user-created distinction
7. **Analytics**: `page_views`, `analytics_rollups` tables support basic analytics without external service dependency (Phase 1)

**Key Entities:**

- **User**: Registered user account with OAuth authentication
- **CalendarSession**: Anonymous user session data (pre-authentication)
- **Calendar**: User's saved calendar with events and configuration
- **CalendarTemplate**: Admin-created template calendars
- **Event**: Custom event on a calendar (date, text, emoji)
- **Order**: E-commerce order for printed calendar
- **OrderItem**: Line items in an order (supports future multi-calendar orders)
- **Payment**: Stripe payment record linked to order
- **DelayedJob**: Asynchronous job queue entry
- **PageView**: Analytics event for page visits
- **AnalyticsRollup**: Aggregated analytics (daily/weekly/monthly)

[... ERD showing CalendarSession entity ...]

entity CalendarSession {
  *session_id : uuid <<PK>>
  --
  user_id : bigint <<FK, nullable>>
  session_data : jsonb <<calendar state>>
  created_at : timestamp
  expires_at : timestamp
  converted_at : timestamp
  --
  INDEX: session_id, user_id
}

[... Relationships ...]
User ||--o{ CalendarSession : "converts from"
CalendarSession ||--o{ PageView : "generates"
```

### Context: flow-user-login (from 04_Behavior_and_Communication.md)

```markdown
<!-- anchor: flow-user-login -->
##### Flow 1: User Login via OAuth (Google)

**Description:**

This flow illustrates how an anonymous user authenticates via Google OAuth, with the system converting their guest session into a permanent user account and linking any calendars created pre-authentication.

**Sequence Diagram:**

[... Shows the complete OAuth flow with session conversion ...]

**Key Steps:**
1. User clicks "Sign in with Google"
2. API redirects to Google OAuth consent
3. Google returns authorization code
4. API exchanges code for tokens
5. API looks up or creates user by oauth_provider + oauth_subject_id
6. **If new user:** API finds calendars with session_id and updates them to link to new user_id
7. API generates JWT token with user_id, role, expiration
8. Frontend stores JWT in localStorage and redirects to dashboard

**Error Scenarios:**

- **OAuth Provider Unavailable**: API returns 503 Service Unavailable, SPA shows "Google login temporarily unavailable"
- **Email Already Exists (Different Provider)**: API merges accounts or prompts user to link accounts (Phase 2 feature)
- **Session Conversion Failure**: Calendars remain in session, API logs error, user can manually save after login
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

#### File: `src/main/java/villagecompute/calendar/data/models/UserCalendar.java`
   - **Summary:** The `UserCalendar` entity already has a `sessionId` field (line 37-38) for tracking guest calendars. This is a STRING field, not a UUID.
   - **CRITICAL DISCOVERY:** The architecture spec describes a separate `CalendarSession` table, but the current implementation uses a simpler approach with `sessionId` stored directly on the `UserCalendar` entity. **You MUST follow the existing implementation pattern, not the architecture spec!**
   - **Recommendation:** Do NOT create a separate `CalendarSession` entity. Instead:
     1. Add session utility methods to work with the existing `UserCalendar.sessionId` field
     2. Session data is already stored in `UserCalendar.configuration` JSONB field
     3. The `findBySession()` method already exists (line 84-86) for retrieving calendars by sessionId

#### File: `src/main/java/villagecompute/calendar/services/CalendarService.java`
   - **Summary:** Core calendar CRUD operations service. Handles authorization, versioning, and user/session management.
   - **CRITICAL:** The `convertSessionToUser()` method **already exists** (lines 270-297)! This method:
     - Takes a sessionId and CalendarUser
     - Finds all calendars with that sessionId
     - Updates each calendar to link to the user (sets `calendar.user = user`)
     - Clears the sessionId field (`calendar.sessionId = null`)
     - Returns count of converted calendars
   - **Recommendation:** You MUST reuse the existing `convertSessionToUser()` method. Your SessionService should call `CalendarService.convertSessionToUser()` internally rather than duplicating this logic.
   - **Note:** The `createCalendar()` method (lines 49-99) already accepts both `user` and `sessionId` parameters. Validation ensures only one is provided (lines 409-414).

#### File: `src/main/java/villagecompute/calendar/services/AuthenticationService.java`
   - **Summary:** Handles OAuth2 callback processing and JWT token generation. Creates or updates CalendarUser on login.
   - **Tip:** The `handleOAuthCallback()` method (lines 42-108) creates/updates users but does NOT currently call session conversion. You MUST integrate the session conversion call here.
   - **Recommendation:** Modify `handleOAuthCallback()` to:
     1. After user creation/update, check if a sessionId is available (from frontend via query param or header)
     2. If sessionId exists, call `CalendarService.convertSessionToUser(sessionId, user)`
     3. Log the number of calendars converted

#### File: `src/main/java/villagecompute/calendar/api/rest/AuthResource.java`
   - **Summary:** REST endpoints for OAuth login and callbacks for Google, Facebook, and Apple.
   - **Note:** The callback handlers (lines 139-163, 244-268, 349-373) all redirect to `/auth/callback?token={jwt}`. This is where the frontend receives the JWT.
   - **Recommendation:** You need to modify the callback to accept an optional `sessionId` query parameter and pass it to `AuthenticationService.handleOAuthCallback()` for session conversion.

#### File: `src/main/webui/src/stores/authStore.ts`
   - **Summary:** Pinia store for managing authentication state on the frontend. Stores JWT token in localStorage.
   - **Critical:** The `handleOAuthCallback()` method (lines 132-166) receives the JWT token and stores it. This is where you MUST trigger the session conversion GraphQL mutation.
   - **Note:** There is NO existing session management in the frontend! You must create `src/main/webui/src/utils/session.ts` from scratch.
   - **Recommendation:** Create frontend session utilities to:
     1. Generate and store sessionId in localStorage (use `crypto.randomUUID()`)
     2. Include sessionId in all GraphQL mutations when user is NOT authenticated
     3. Clear sessionId from localStorage after successful session conversion

### Implementation Tips & Notes

1. **Session ID Format:** The existing code uses a STRING for sessionId in `UserCalendar`. Use standard UUID format (e.g., `crypto.randomUUID()` in frontend, `UUID.randomUUID().toString()` in backend).

2. **No Separate Session Entity Needed:** The architecture spec describes a `CalendarSession` table with `session_data` JSONB, but the actual implementation stores session calendars directly in `user_calendars` with a `sessionId` reference. This is simpler and already working. DO NOT create a separate session table.

3. **Session Expiration:** You MUST create a Quarkus `@Scheduled` job that:
   - Queries for calendars where `sessionId IS NOT NULL` and `updated < NOW() - INTERVAL '30 days'`
   - Deletes these expired guest calendars
   - Logs the count of deleted calendars
   - Runs daily at 2 AM UTC

4. **GraphQL Mutation:** Create a `convertGuestSession(sessionId: String!)` mutation that:
   - Requires authentication (user must be logged in)
   - Calls `CalendarService.convertSessionToUser(sessionId, currentUser)`
   - Returns the count of calendars converted
   - Should be called automatically by the frontend after OAuth callback

5. **Frontend Session Flow:**
   - When app loads: Check if sessionId exists in localStorage. If not, generate one and store it.
   - When creating calendar (not logged in): Include `sessionId` in `createCalendar` GraphQL mutation
   - After OAuth login: Automatically call `convertGuestSession` mutation with stored sessionId
   - After conversion succeeds: Clear sessionId from localStorage (no longer needed)

6. **Testing Strategy:**
   - Unit test: SessionService with mocked CalendarService
   - Integration test: Full flow - create calendar as guest → login → verify calendars transferred
   - Test session expiration: Create old guest calendar → run cleanup job → verify deleted

7. **Error Handling:** Session conversion can fail if:
   - SessionId doesn't match any calendars (return count=0, not an error)
   - User is null (throw IllegalArgumentException)
   - Database transaction fails (let Quarkus handle rollback)

---

## 4. Critical Implementation Differences

**IMPORTANT:** The architecture specification describes a separate `CalendarSession` entity with its own table and JSONB session_data field. However, the ACTUAL codebase implements guest sessions differently:

- ✅ **What exists:** `UserCalendar.sessionId` field (String) links guest calendars to anonymous sessions
- ✅ **What exists:** `CalendarService.convertSessionToUser()` method for session conversion
- ❌ **What does NOT exist:** Separate `CalendarSession` entity or table
- ❌ **What does NOT exist:** SessionService class
- ❌ **What does NOT exist:** Frontend session utilities

**Your implementation MUST:**
1. Create `SessionService` that wraps the existing `CalendarService.convertSessionToUser()` method
2. Create frontend `session.ts` utilities for sessionId management
3. Add GraphQL `convertGuestSession` mutation
4. Add scheduled cleanup job for expired guest calendars
5. Modify `AuthResource` callbacks to accept sessionId parameter
6. Integrate session conversion into OAuth callback flow

**Your implementation MUST NOT:**
1. Create a `CalendarSession` JPA entity
2. Create a `calendar_sessions` database table
3. Duplicate the session conversion logic (reuse existing method!)
4. Store session data in a separate JSONB field (it's already in `UserCalendar.configuration`)
