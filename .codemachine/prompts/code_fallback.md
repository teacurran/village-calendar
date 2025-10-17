# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Implement guest session management allowing anonymous users to create calendars before authenticating. Create SessionService for managing CalendarSession entities: createSession (generate UUID, store in localStorage), saveCalendarToSession (persist calendar data in JSONB session_data field), convertSessionToUser (on login, transfer session calendars to user account). Update CalendarService.createCalendar to accept optional sessionId (if user not authenticated). Update frontend to store sessionId in localStorage, include in GraphQL mutations if user not logged in. Implement session expiration (30 days, cleanup job deletes expired sessions). Add "Sign in to save" prompt in calendar editor for guest users.

---

## Issues Detected

### **Test Compilation Errors**

*   **Error 1:** Multiple test files fail to compile because they call `handleOAuthCallback(String provider, SecurityIdentity identity)` with 2 parameters, but the method signature was changed to require 3 parameters: `handleOAuthCallback(String provider, SecurityIdentity identity, String sessionId)`.
    *   Files affected:
        *   `src/test/java/villagecompute/calendar/integration/AuthenticationIntegrationTest.java` (lines 77, 120, 149, 325)
        *   `src/test/java/villagecompute/calendar/services/AuthenticationServiceTest.java` (lines 90, 128, 153)

*   **Error 2:** `SessionServiceTest.java` has type errors where `LocalDateTime` is used but `Instant` is required for the `UserCalendar.updated` field.
    *   Lines affected: 286, 325, 362
    *   Error: `incompatible types: java.time.LocalDateTime cannot be converted to java.time.Instant`

### **Missing Dependency**

*   **Fixed:** The `quarkus-scheduler` dependency was missing from `pom.xml`, which prevented compilation of `SessionCleanupJob.java`. This has been **FIXED** by adding the dependency.

---

## Best Approach to Fix

You MUST fix the test compilation errors by updating all test files that call `handleOAuthCallback()`:

1. **Update AuthenticationServiceTest.java and AuthenticationIntegrationTest.java:**
   *   Find all calls to `handleOAuthCallback(provider, identity)`
   *   Change them to `handleOAuthCallback(provider, identity, null)` to pass `null` as the sessionId parameter
   *   This maintains backward compatibility for tests that don't need session conversion

2. **Fix SessionServiceTest.java type errors:**
   *   On line 286: Change `calendar.updated = LocalDateTime.now().minusDays(31);` to `calendar.updated = Instant.now().minus(31, ChronoUnit.DAYS);`
   *   On line 325: Change `calendar.updated = LocalDateTime.now().minusDays(31);` to `calendar.updated = Instant.now().minus(31, ChronoUnit.DAYS);`
   *   On line 362: Change `calendar.updated = LocalDateTime.now().minusDays(35);` to `calendar.updated = Instant.now().minus(35, ChronoUnit.DAYS);`
   *   Add import: `import java.time.Instant;` and `import java.time.temporal.ChronoUnit;`
   *   Remove import: `import java.time.LocalDateTime;` (if it's not used elsewhere)

3. **Verify all tests pass:**
   *   Run `mvn test` to ensure all tests compile and pass
   *   Specifically verify `SessionServiceTest` passes all test cases
   *   Verify `AuthenticationServiceTest` and `AuthenticationIntegrationTest` pass

---

## Additional Context

The implementation is otherwise correct:
*   ✅ SessionService is properly implemented
*   ✅ SessionResolver GraphQL API is correctly implemented
*   ✅ SessionCleanupJob scheduled task is properly configured
*   ✅ Frontend session.ts utilities are correctly implemented
*   ✅ authStore.ts properly integrates session conversion
*   ✅ AuthResource REST endpoints correctly accept sessionId parameter
*   ✅ AuthenticationService correctly handles session conversion

The ONLY issues are the test compilation errors. Once these are fixed, the implementation will be complete.
