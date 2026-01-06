package villagecompute.calendar.services;

import static org.junit.jupiter.api.Assertions.*;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import villagecompute.calendar.data.models.CalendarUser;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Unit tests for AuthenticationService. Tests OAuth callback handling, JWT issuance, and user creation/update logic.
 */
@QuarkusTest
class AuthenticationServiceTest {

    @Inject
    AuthenticationService authenticationService;

    private static final String TEST_PROVIDER = "GOOGLE";
    private static final String TEST_OAUTH_SUBJECT = "test-oauth-subject-123";
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_NAME = "Test User";
    private static final String TEST_PICTURE = "https://example.com/picture.jpg";

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up any existing test users and related data
        CalendarUser testUser = CalendarUser.find("email", TEST_EMAIL).firstResult();
        if (testUser != null) {
            cleanupUserData(testUser.id);
        }
    }

    @AfterEach
    @Transactional
    void tearDown() {
        // Clean up test users and related data
        CalendarUser testUser = CalendarUser.find("email", TEST_EMAIL).firstResult();
        if (testUser != null) {
            cleanupUserData(testUser.id);
        }
    }

    private void cleanupUserData(UUID userId) {
        // Delete in proper order to avoid FK violations
        try {
            // Delete orders related to user's calendars
            villagecompute.calendar.data.models.CalendarOrder.delete("user.id", userId);
        } catch (Exception e) {
            // Ignore cleanup errors
        }
        try {
            // Delete user's calendars
            villagecompute.calendar.data.models.UserCalendar.delete("user.id", userId);
        } catch (Exception e) {
            // Ignore cleanup errors
        }
        try {
            // Delete the user
            CalendarUser.deleteById(userId);
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @Test
    @Transactional
    void testHandleOAuthCallback_NewUser() {
        // Given: No existing user with this OAuth subject
        Optional<CalendarUser> existingUser = CalendarUser.findByOAuthSubject(TEST_PROVIDER, TEST_OAUTH_SUBJECT);
        assertFalse(existingUser.isPresent(), "User should not exist before test");

        // When: Handle OAuth callback with mock identity
        SecurityIdentity identity = createMockIdentity(TEST_OAUTH_SUBJECT, TEST_EMAIL, TEST_NAME, TEST_PICTURE);
        CalendarUser user = authenticationService.handleOAuthCallback("google", identity, null);

        // Then: User is created with correct information
        assertNotNull(user, "User should be created");
        assertNotNull(user.id, "User ID should be assigned");
        assertEquals(TEST_PROVIDER, user.oauthProvider, "OAuth provider should match");
        assertEquals(TEST_OAUTH_SUBJECT, user.oauthSubject, "OAuth subject should match");
        assertEquals(TEST_EMAIL, user.email, "Email should match");
        assertEquals(TEST_NAME, user.displayName, "Display name should match");
        assertEquals(TEST_PICTURE, user.profileImageUrl, "Profile image URL should match");
        assertNotNull(user.lastLoginAt, "Last login should be set");

        // Verify user is persisted in database
        Optional<CalendarUser> persistedUser = CalendarUser.findByOAuthSubject(TEST_PROVIDER, TEST_OAUTH_SUBJECT);
        assertTrue(persistedUser.isPresent(), "User should be persisted");
        assertEquals(user.id, persistedUser.get().id, "Persisted user ID should match");
    }

    @Test
    @Transactional
    void testHandleOAuthCallback_ExistingUser() {
        // Given: Existing user in database
        CalendarUser existingUser = new CalendarUser();
        existingUser.oauthProvider = TEST_PROVIDER;
        existingUser.oauthSubject = TEST_OAUTH_SUBJECT;
        existingUser.email = "old-email@example.com";
        existingUser.displayName = "Old Name";
        existingUser.profileImageUrl = "https://example.com/old-picture.jpg";
        existingUser.lastLoginAt = Instant.now().minusSeconds(3600); // 1 hour ago
        existingUser.persist();

        Instant oldLastLogin = existingUser.lastLoginAt;
        UUID existingUserId = existingUser.id;

        // When: Handle OAuth callback with updated information
        SecurityIdentity identity = createMockIdentity(TEST_OAUTH_SUBJECT, TEST_EMAIL, TEST_NAME, TEST_PICTURE);
        CalendarUser user = authenticationService.handleOAuthCallback("google", identity, null);

        // Then: User is updated with new information
        assertNotNull(user, "User should be returned");
        assertEquals(existingUserId, user.id, "User ID should remain the same");
        assertEquals(TEST_EMAIL, user.email, "Email should be updated");
        assertEquals(TEST_NAME, user.displayName, "Display name should be updated");
        assertEquals(TEST_PICTURE, user.profileImageUrl, "Profile image should be updated");
        assertTrue(user.lastLoginAt.isAfter(oldLastLogin), "Last login should be updated");

        // Verify only one user exists in database
        long userCount = CalendarUser.count("oauthProvider = ?1 AND oauthSubject = ?2", TEST_PROVIDER,
                TEST_OAUTH_SUBJECT);
        assertEquals(1, userCount, "Should still have only one user record");
    }

    @Test
    @Transactional
    void testHandleOAuthCallback_NoEmail() {
        // Given: OAuth identity without email
        SecurityIdentity identity = createMockIdentity(TEST_OAUTH_SUBJECT, null, TEST_NAME, TEST_PICTURE);

        // When/Then: Should throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            authenticationService.handleOAuthCallback("google", identity, null);
        }, "Should throw exception when email is missing");
    }

    @Test
    @Transactional
    void testHandleOAuthCallback_WithUserInfo() {
        // Given: OAuth identity with UserInfo available
        SecurityIdentity identity = createMockIdentityWithUserInfo(TEST_OAUTH_SUBJECT, TEST_EMAIL, TEST_NAME,
                TEST_PICTURE);

        // When: Handle OAuth callback
        CalendarUser user = authenticationService.handleOAuthCallback("google", identity, null);

        // Then: User is created with information from UserInfo
        assertNotNull(user, "User should be created");
        assertEquals(TEST_EMAIL, user.email, "Email should come from UserInfo");
        assertEquals(TEST_NAME, user.displayName, "Name should come from UserInfo");
        assertEquals(TEST_PICTURE, user.profileImageUrl, "Picture should come from UserInfo");
    }

    @Test
    @Transactional
    void testHandleOAuthCallback_WithSessionId_ConvertsCalendars() {
        // Given: A guest calendar with a session ID
        String sessionId = "test-session-" + UUID.randomUUID();

        villagecompute.calendar.data.models.CalendarTemplate template = new villagecompute.calendar.data.models.CalendarTemplate();
        template.name = "Test Template";
        template.configuration = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        template.isActive = true;
        template.isFeatured = false;
        template.displayOrder = 1;
        template.persist();

        villagecompute.calendar.data.models.UserCalendar guestCalendar = new villagecompute.calendar.data.models.UserCalendar();
        guestCalendar.sessionId = sessionId;
        guestCalendar.user = null; // Guest calendar has no user
        guestCalendar.template = template;
        guestCalendar.name = "Guest Calendar";
        guestCalendar.year = 2025;
        guestCalendar.configuration = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        guestCalendar.persist();

        UUID calendarId = guestCalendar.id;

        // When: Handle OAuth callback with sessionId
        SecurityIdentity identity = createMockIdentity(TEST_OAUTH_SUBJECT, TEST_EMAIL, TEST_NAME, TEST_PICTURE);
        CalendarUser user = authenticationService.handleOAuthCallback("google", identity, sessionId);

        // Then: Calendar should be converted to user
        assertNotNull(user, "User should be created");

        villagecompute.calendar.data.models.UserCalendar convertedCalendar = villagecompute.calendar.data.models.UserCalendar
                .findById(calendarId);
        assertNotNull(convertedCalendar, "Calendar should still exist");
        assertEquals(user.id, convertedCalendar.user.id, "Calendar should now belong to user");
        assertNull(convertedCalendar.sessionId, "Session ID should be cleared after conversion");
    }

    @Test
    @Transactional
    void testHandleOAuthCallback_WithEmptySessionId_DoesNotConvert() {
        // Given: Empty session ID
        SecurityIdentity identity = createMockIdentity(TEST_OAUTH_SUBJECT, TEST_EMAIL, TEST_NAME, TEST_PICTURE);

        // When: Handle OAuth callback with empty sessionId
        CalendarUser user = authenticationService.handleOAuthCallback("google", identity, "   ");

        // Then: User is created but no conversion attempted (no errors)
        assertNotNull(user, "User should be created");
        assertEquals(TEST_EMAIL, user.email, "Email should match");
    }

    // ========================================================================
    // isAuthenticated tests
    // ========================================================================

    @Test
    void testIsAuthenticated_Unauthenticated_ReturnsFalse() {
        // In test context without authentication, should return false
        // Note: The injected SecurityIdentity in tests is typically anonymous
        boolean result = authenticationService.isAuthenticated();

        // The default test context has no authenticated user
        assertFalse(result, "Should return false when not authenticated");
    }

    @Test
    @Transactional
    void testIssueJWT() {
        // Given: A calendar user
        CalendarUser user = new CalendarUser();
        user.oauthProvider = TEST_PROVIDER;
        user.oauthSubject = TEST_OAUTH_SUBJECT;
        user.email = TEST_EMAIL;
        user.displayName = TEST_NAME;
        user.persist();

        // When: Issue JWT
        String jwt = authenticationService.issueJWT(user);

        // Then: JWT is generated
        assertNotNull(jwt, "JWT should be generated");
        assertFalse(jwt.isEmpty(), "JWT should not be empty");
        assertEquals(3, jwt.split("\\.").length, "JWT should have three parts (header.payload.signature)");

        // Note: Full JWT validation would require decoding and verifying the token,
        // which is tested in integration tests
    }

    // ========================================================================
    // getCurrentUser tests
    // ========================================================================

    @Test
    void testGetCurrentUser_NullJwt_ReturnsEmpty() {
        Optional<CalendarUser> result = authenticationService.getCurrentUser(null);

        assertTrue(result.isEmpty(), "Should return empty when JWT is null");
    }

    @Test
    void testGetCurrentUser_NullSubject_ReturnsEmpty() {
        org.eclipse.microprofile.jwt.JsonWebToken jwt = createMockJwt(null);

        Optional<CalendarUser> result = authenticationService.getCurrentUser(jwt);

        assertTrue(result.isEmpty(), "Should return empty when subject is null");
    }

    @Test
    void testGetCurrentUser_InvalidUuidSubject_ReturnsEmpty() {
        org.eclipse.microprofile.jwt.JsonWebToken jwt = createMockJwt("not-a-valid-uuid");

        Optional<CalendarUser> result = authenticationService.getCurrentUser(jwt);

        assertTrue(result.isEmpty(), "Should return empty for invalid UUID subject");
    }

    @Test
    @Transactional
    void testGetCurrentUser_ValidUuidUserNotFound_ReturnsEmpty() {
        UUID nonExistentId = UUID.randomUUID();
        org.eclipse.microprofile.jwt.JsonWebToken jwt = createMockJwt(nonExistentId.toString());

        Optional<CalendarUser> result = authenticationService.getCurrentUser(jwt);

        assertTrue(result.isEmpty(), "Should return empty when user not found");
    }

    @Test
    @Transactional
    void testGetCurrentUser_ValidUuidUserExists_ReturnsUser() {
        // Given: An existing user
        CalendarUser user = new CalendarUser();
        user.oauthProvider = TEST_PROVIDER;
        user.oauthSubject = TEST_OAUTH_SUBJECT;
        user.email = TEST_EMAIL;
        user.displayName = TEST_NAME;
        user.persist();

        org.eclipse.microprofile.jwt.JsonWebToken jwt = createMockJwt(user.id.toString());

        // When
        Optional<CalendarUser> result = authenticationService.getCurrentUser(jwt);

        // Then
        assertTrue(result.isPresent(), "Should return user when found");
        assertEquals(user.id, result.get().id, "Should return correct user");
        assertEquals(TEST_EMAIL, result.get().email, "User email should match");
    }

    // ========================================================================
    // requireCurrentUser tests
    // ========================================================================

    @Test
    void testRequireCurrentUser_NullJwt_ThrowsSecurityException() {
        SecurityException exception = assertThrows(SecurityException.class,
                () -> authenticationService.requireCurrentUser(null));

        assertEquals("Unauthorized: User not found", exception.getMessage());
    }

    @Test
    void testRequireCurrentUser_InvalidUuid_ThrowsSecurityException() {
        org.eclipse.microprofile.jwt.JsonWebToken jwt = createMockJwt("invalid-uuid");

        SecurityException exception = assertThrows(SecurityException.class,
                () -> authenticationService.requireCurrentUser(jwt));

        assertEquals("Unauthorized: User not found", exception.getMessage());
    }

    @Test
    @Transactional
    void testRequireCurrentUser_UserNotFound_ThrowsSecurityException() {
        UUID nonExistentId = UUID.randomUUID();
        org.eclipse.microprofile.jwt.JsonWebToken jwt = createMockJwt(nonExistentId.toString());

        SecurityException exception = assertThrows(SecurityException.class,
                () -> authenticationService.requireCurrentUser(jwt));

        assertEquals("Unauthorized: User not found", exception.getMessage());
    }

    @Test
    @Transactional
    void testRequireCurrentUser_UserExists_ReturnsUser() {
        // Given: An existing user
        CalendarUser user = new CalendarUser();
        user.oauthProvider = TEST_PROVIDER;
        user.oauthSubject = TEST_OAUTH_SUBJECT + "-require";
        user.email = "require-" + TEST_EMAIL;
        user.displayName = TEST_NAME;
        user.persist();

        org.eclipse.microprofile.jwt.JsonWebToken jwt = createMockJwt(user.id.toString());

        // When
        CalendarUser result = authenticationService.requireCurrentUser(jwt);

        // Then
        assertNotNull(result, "Should return user");
        assertEquals(user.id, result.id, "Should return correct user");
    }

    /** Helper method to create a mock JsonWebToken for testing. */
    private org.eclipse.microprofile.jwt.JsonWebToken createMockJwt(String subject) {
        return new org.eclipse.microprofile.jwt.JsonWebToken() {
            @Override
            public String getName() {
                return subject;
            }

            @Override
            public String getSubject() {
                return subject;
            }

            @Override
            public Set<String> getClaimNames() {
                return Set.of("sub");
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T getClaim(String claimName) {
                if ("sub".equals(claimName)) {
                    return (T) subject;
                }
                return null;
            }

            @Override
            public String getRawToken() {
                return "mock.jwt.token";
            }

            @Override
            public String getIssuer() {
                return "village-calendar";
            }

            @Override
            public Set<String> getAudience() {
                return Set.of();
            }

            @Override
            public long getExpirationTime() {
                return System.currentTimeMillis() / 1000 + 3600;
            }

            @Override
            public long getIssuedAtTime() {
                return System.currentTimeMillis() / 1000;
            }

            @Override
            public Set<String> getGroups() {
                return Set.of("USER");
            }

            @Override
            public String getTokenID() {
                return "mock-token-id";
            }
        };
    }

    /** Helper method to create a mock SecurityIdentity with UserInfo for testing. */
    private SecurityIdentity createMockIdentityWithUserInfo(String subject, String email, String name, String picture) {
        io.quarkus.oidc.UserInfo userInfo = new io.quarkus.oidc.UserInfo() {
            private final Map<String, Object> claims = Map.of("email", email != null ? email : "", "name",
                    name != null ? name : "", "picture", picture != null ? picture : "");

            @Override
            public String getString(String key) {
                Object value = claims.get(key);
                return value != null ? value.toString() : null;
            }

            @Override
            public String getPreferredUserName() {
                return email;
            }

            @Override
            public Object get(String key) {
                return claims.get(key);
            }

            @Override
            public boolean contains(String key) {
                return claims.containsKey(key);
            }

            @Override
            public Set<String> getPropertyNames() {
                return claims.keySet();
            }

            @Override
            public jakarta.json.JsonObject getJsonObject() {
                return null;
            }

            @Override
            public Long getLong(String key) {
                return null;
            }

            @Override
            public Boolean getBoolean(String key) {
                return null;
            }

            @Override
            public jakarta.json.JsonObject getObject(String key) {
                return null;
            }

            @Override
            public jakarta.json.JsonArray getArray(String key) {
                return null;
            }
        };

        return new SecurityIdentity() {
            @Override
            public Principal getPrincipal() {
                return () -> subject;
            }

            @Override
            public boolean isAnonymous() {
                return false;
            }

            @Override
            public Set<String> getRoles() {
                return Set.of("USER");
            }

            @Override
            public boolean hasRole(String role) {
                return "USER".equals(role);
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T getAttribute(String attributeName) {
                if ("userinfo".equals(attributeName)) {
                    return (T) userInfo;
                }
                return null;
            }

            @Override
            public Map<String, Object> getAttributes() {
                return Map.of("userinfo", userInfo);
            }

            @Override
            public <T extends io.quarkus.security.credential.Credential> T getCredential(Class<T> aClass) {
                return null;
            }

            @Override
            public Set<io.quarkus.security.credential.Credential> getCredentials() {
                return Set.of();
            }

            public <T extends io.quarkus.security.credential.Credential> Set<T> getCredentials(Class<T> aClass) {
                return Set.of();
            }

            public io.smallrye.mutiny.Uni<Boolean> checkPermission(java.security.Permission permission) {
                return io.smallrye.mutiny.Uni.createFrom().item(true);
            }
        };
    }

    /** Helper method to create a mock SecurityIdentity for testing (without UserInfo). */
    private SecurityIdentity createMockIdentity(String subject, String email, String name, String picture) {

        return new SecurityIdentity() {
            @Override
            public Principal getPrincipal() {
                return () -> subject;
            }

            @Override
            public boolean isAnonymous() {
                return false;
            }

            @Override
            public Set<String> getRoles() {
                return Set.of("USER");
            }

            @Override
            public boolean hasRole(String role) {
                return "USER".equals(role);
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T getAttribute(String attributeName) {
                return switch (attributeName) {
                    case "email" -> (T) email;
                    case "name" -> (T) name;
                    case "picture" -> (T) picture;
                    default -> null;
                };
            }

            @Override
            public Map<String, Object> getAttributes() {
                return Map.of("email", email != null ? email : "", "name", name != null ? name : "", "picture",
                        picture != null ? picture : "");
            }

            @Override
            public <T extends io.quarkus.security.credential.Credential> T getCredential(Class<T> aClass) {
                return null;
            }

            @Override
            public Set<io.quarkus.security.credential.Credential> getCredentials() {
                return Set.of();
            }

            public <T extends io.quarkus.security.credential.Credential> Set<T> getCredentials(Class<T> aClass) {
                return Set.of();
            }

            public io.smallrye.mutiny.Uni<Boolean> checkPermission(java.security.Permission permission) {
                return io.smallrye.mutiny.Uni.createFrom().item(true);
            }
        };
    }
}
