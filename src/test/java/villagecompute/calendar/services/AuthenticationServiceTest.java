package villagecompute.calendar.services;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import villagecompute.calendar.data.models.CalendarUser;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AuthenticationService.
 * Tests OAuth callback handling, JWT issuance, and user creation/update logic.
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
        SecurityIdentity identity = createMockIdentity(
            TEST_OAUTH_SUBJECT, TEST_EMAIL, TEST_NAME, TEST_PICTURE
        );
        CalendarUser user = authenticationService.handleOAuthCallback("google", identity);

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
        SecurityIdentity identity = createMockIdentity(
            TEST_OAUTH_SUBJECT, TEST_EMAIL, TEST_NAME, TEST_PICTURE
        );
        CalendarUser user = authenticationService.handleOAuthCallback("google", identity);

        // Then: User is updated with new information
        assertNotNull(user, "User should be returned");
        assertEquals(existingUserId, user.id, "User ID should remain the same");
        assertEquals(TEST_EMAIL, user.email, "Email should be updated");
        assertEquals(TEST_NAME, user.displayName, "Display name should be updated");
        assertEquals(TEST_PICTURE, user.profileImageUrl, "Profile image should be updated");
        assertTrue(user.lastLoginAt.isAfter(oldLastLogin), "Last login should be updated");

        // Verify only one user exists in database
        long userCount = CalendarUser.count("oauthProvider = ?1 AND oauthSubject = ?2", TEST_PROVIDER, TEST_OAUTH_SUBJECT);
        assertEquals(1, userCount, "Should still have only one user record");
    }

    @Test
    @Transactional
    void testHandleOAuthCallback_NoEmail() {
        // Given: OAuth identity without email
        SecurityIdentity identity = createMockIdentity(
            TEST_OAUTH_SUBJECT, null, TEST_NAME, TEST_PICTURE
        );

        // When/Then: Should throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            authenticationService.handleOAuthCallback("google", identity);
        }, "Should throw exception when email is missing");
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
        assertTrue(jwt.split("\\.").length == 3, "JWT should have three parts (header.payload.signature)");

        // Note: Full JWT validation would require decoding and verifying the token,
        // which is tested in integration tests
    }

    /**
     * Helper method to create a mock SecurityIdentity for testing.
     */
    private SecurityIdentity createMockIdentity(
        String subject, String email, String name, String picture) {

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
                return Map.of(
                    "email", email != null ? email : "",
                    "name", name != null ? name : "",
                    "picture", picture != null ? picture : ""
                );
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
