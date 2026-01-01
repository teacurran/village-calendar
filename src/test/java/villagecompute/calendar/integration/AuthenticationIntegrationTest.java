package villagecompute.calendar.integration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.*;

import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.services.AuthenticationService;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration tests for authentication flow. Tests OAuth2 callback handling, JWT issuance, and GraphQL endpoint
 * authentication.
 *
 * <p>
 * This test suite validates: 1. OAuth callback creates/updates CalendarUser correctly 2. JWT tokens are generated with
 * valid format 3. GraphQL endpoints handle unauthenticated requests correctly
 *
 * <p>
 * Note: Full end-to-end JWT authentication testing (including JWT validation) requires running the application with
 * real OAuth providers or manual testing in the Docker Compose environment. These tests focus on the OAuth callback and
 * JWT generation logic.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthenticationIntegrationTest {

    @Inject
    AuthenticationService authService;

    private static final String TEST_PROVIDER = "GOOGLE";
    private static final String TEST_OAUTH_SUBJECT = "auth-int-test-subject-" + System.currentTimeMillis();
    private static final String TEST_EMAIL = "auth-integration@example.com";
    private static final String TEST_NAME = "Auth Integration Test User";
    private static final String TEST_PICTURE = "https://example.com/picture.jpg";

    private CalendarUser testUser;

    @AfterEach
    @Transactional
    void cleanup() {
        // Clean up test users
        if (testUser != null && testUser.id != null) {
            CalendarUser.deleteById(testUser.id);
        }
        CalendarUser.delete("email", TEST_EMAIL);
    }

    // ============================================================================
    // OAUTH CALLBACK TESTS
    // ============================================================================

    @Test
    @Order(1)
    @Transactional
    void testOAuthCallback_CreatesNewUser() {
        // Given: No existing user with this OAuth subject
        Optional<CalendarUser> existingUser = CalendarUser.findByOAuthSubject(TEST_PROVIDER, TEST_OAUTH_SUBJECT);
        assertFalse(existingUser.isPresent(), "User should not exist before OAuth callback");

        // When: Handle OAuth callback with mock identity
        SecurityIdentity identity = createMockIdentity(TEST_OAUTH_SUBJECT, TEST_EMAIL, TEST_NAME, TEST_PICTURE);
        CalendarUser user = authService.handleOAuthCallback("google", identity, null);

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

        testUser = user;
    }

    @Test
    @Order(2)
    @Transactional
    void testOAuthCallback_UpdatesExistingUser() {
        // Given: Existing user in database
        CalendarUser existingUser = new CalendarUser();
        existingUser.oauthProvider = TEST_PROVIDER;
        existingUser.oauthSubject = "update-test-subject-" + System.currentTimeMillis();
        existingUser.email = "old-email@example.com";
        existingUser.displayName = "Old Name";
        existingUser.profileImageUrl = "https://example.com/old-picture.jpg";
        existingUser.lastLoginAt = Instant.now().minusSeconds(3600); // 1 hour ago
        existingUser.persist();

        Instant oldLastLogin = existingUser.lastLoginAt;

        // When: Handle OAuth callback with updated information
        String newEmail = "new-email@example.com";
        String newName = "New Name";
        String newPicture = "https://example.com/new-picture.jpg";
        SecurityIdentity identity = createMockIdentity(existingUser.oauthSubject, newEmail, newName, newPicture);
        CalendarUser user = authService.handleOAuthCallback("google", identity, null);

        // Then: User is updated with new information
        assertNotNull(user, "User should be returned");
        assertEquals(existingUser.id, user.id, "User ID should remain the same");
        assertEquals(newEmail, user.email, "Email should be updated");
        assertEquals(newName, user.displayName, "Display name should be updated");
        assertEquals(newPicture, user.profileImageUrl, "Profile image should be updated");
        assertTrue(user.lastLoginAt.isAfter(oldLastLogin), "Last login should be updated");

        // Verify only one user exists in database
        long userCount = CalendarUser.count("oauthProvider = ?1 AND oauthSubject = ?2", TEST_PROVIDER,
                existingUser.oauthSubject);
        assertEquals(1, userCount, "Should still have only one user record");

        testUser = user;
    }

    @Test
    @Order(3)
    @Transactional
    void testOAuthCallback_RequiresEmail() {
        // Given: OAuth identity without email
        SecurityIdentity identity = createMockIdentity("no-email-subject", null, TEST_NAME, TEST_PICTURE);

        // When/Then: Should throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            authService.handleOAuthCallback("google", identity, null);
        }, "Should throw exception when email is missing");
    }

    // ============================================================================
    // JWT ISSUANCE TESTS
    // ============================================================================

    @Test
    @Order(10)
    @Transactional
    void testJWTIssuance_ValidToken() {
        // Given: A calendar user
        CalendarUser user = new CalendarUser();
        user.oauthProvider = TEST_PROVIDER;
        user.oauthSubject = "jwt-test-subject-" + System.currentTimeMillis();
        user.email = "jwt-test@example.com";
        user.displayName = "JWT Test User";
        user.persist();

        // When: Issue JWT
        String jwt = authService.issueJWT(user);

        // Then: JWT is generated with correct format
        assertNotNull(jwt, "JWT should be generated");
        assertFalse(jwt.isEmpty(), "JWT should not be empty");

        String[] jwtParts = jwt.split("\\.");
        assertEquals(3, jwtParts.length, "JWT should have three parts (header.payload.signature)");

        // Verify each part is not empty
        assertTrue(jwtParts[0].length() > 0, "JWT header should not be empty");
        assertTrue(jwtParts[1].length() > 0, "JWT payload should not be empty");
        assertTrue(jwtParts[2].length() > 0, "JWT signature should not be empty");

        testUser = user;
    }

    @Test
    @Order(11)
    @Transactional
    void testJWTIssuance_MultipleUsers() {
        // Given: Multiple users
        CalendarUser user1 = new CalendarUser();
        user1.oauthProvider = TEST_PROVIDER;
        user1.oauthSubject = "user1-" + System.currentTimeMillis();
        user1.email = "user1@example.com";
        user1.displayName = "User 1";
        user1.persist();

        CalendarUser user2 = new CalendarUser();
        user2.oauthProvider = TEST_PROVIDER;
        user2.oauthSubject = "user2-" + System.currentTimeMillis();
        user2.email = "user2@example.com";
        user2.displayName = "User 2";
        user2.persist();

        // When: Issue JWTs for both users
        String jwt1 = authService.issueJWT(user1);
        String jwt2 = authService.issueJWT(user2);

        // Then: Each JWT should be unique
        assertNotEquals(jwt1, jwt2, "JWTs for different users should be unique");
        assertEquals(3, jwt1.split("\\.").length, "JWT 1 should be valid");
        assertEquals(3, jwt2.split("\\.").length, "JWT 2 should be valid");

        // Clean up
        CalendarUser.deleteById(user1.id);
        testUser = user2;
    }

    // ============================================================================
    // GRAPHQL ENDPOINT AUTHENTICATION TESTS (Unauthenticated)
    // ============================================================================

    @Test
    @Order(20)
    void testGraphQL_MeQuery_WithoutAuthentication() {
        // When: Query 'me' endpoint without authentication
        String query = """
                query {
                    me {
                        id
                        email
                    }
                }
                """;

        // Then: Should return null (no authenticated user)
        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("data.me", nullValue()).body("errors", nullValue());
    }

    @Test
    @Order(21)
    void testGraphQL_MyCalendars_WithoutAuthentication() {
        // When: Query 'myCalendars' endpoint without authentication
        String query = """
                query {
                    myCalendars {
                        id
                        name
                    }
                }
                """;

        // Then: Should return authorization error
        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue())
                .body("errors[0].message", anyOf(containsString("Unauthorized"), containsString("System error")));
    }

    @Test
    @Order(22)
    void testGraphQL_CreateCalendar_WithoutAuthentication() {
        // When: Try to create calendar without authentication
        String mutation = """
                mutation {
                    createCalendar(input: {
                        name: "Unauthorized Calendar"
                        year: 2025
                        templateId: "00000000-0000-0000-0000-000000000001"
                    }) {
                        id
                    }
                }
                """;

        // Then: Should return authorization error
        given().contentType(ContentType.JSON).body(Map.of("query", mutation)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue())
                .body("errors[0].message", anyOf(containsString("Unauthorized"), containsString("System error")));
    }

    // ============================================================================
    // COMPLETE AUTHENTICATION WORKFLOW TEST
    // ============================================================================

    @Test
    @Order(30)
    @Transactional
    void testCompleteAuthenticationWorkflow() {
        // Test the complete authentication workflow from OAuth callback to JWT issuance

        // Step 1: Simulate OAuth callback
        SecurityIdentity identity = createMockIdentity("complete-flow-subject-" + System.currentTimeMillis(),
                "complete-flow@example.com", "Complete Flow Test", "https://example.com/avatar.jpg");
        CalendarUser user = authService.handleOAuthCallback("google", identity, null);

        assertNotNull(user, "User should be created");
        assertNotNull(user.id, "User ID should be assigned");
        assertEquals("complete-flow@example.com", user.email, "Email should match");

        // Step 2: Issue JWT for the user
        String jwt = authService.issueJWT(user);

        assertNotNull(jwt, "JWT should be issued");
        assertEquals(3, jwt.split("\\.").length, "JWT should be valid format");

        // Step 3: Verify user can be retrieved from database
        Optional<CalendarUser> retrievedUser = CalendarUser.findByOAuthSubject("GOOGLE",
                identity.getPrincipal().getName());
        assertTrue(retrievedUser.isPresent(), "User should be retrievable from database");
        assertEquals(user.id, retrievedUser.get().id, "Retrieved user should match created user");

        // Note: Full end-to-end testing with JWT validation requires running
        // the application in Docker Compose and manually testing the OAuth flow.
        // These integration tests validate the OAuth callback and JWT generation logic.

        testUser = user;
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    /**
     * Helper method to create a mock SecurityIdentity for testing OAuth callbacks. This simulates the SecurityIdentity
     * object that would be provided by OIDC providers.
     */
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
