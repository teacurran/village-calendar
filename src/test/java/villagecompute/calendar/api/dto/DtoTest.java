package villagecompute.calendar.api.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import villagecompute.calendar.data.models.CalendarUser;

/**
 * Tests for DTO classes in the api.dto package.
 */
class DtoTest {

    // ========== UserInfo Tests ==========

    @Test
    void testUserInfo_DefaultConstructor_CreatesEmptyObject() {
        UserInfo userInfo = new UserInfo();

        assertNull(userInfo.id);
        assertNull(userInfo.email);
        assertNull(userInfo.displayName);
        assertNull(userInfo.profileImageUrl);
    }

    @Test
    void testUserInfo_ParameterizedConstructor_SetsAllFields() {
        UUID id = UUID.randomUUID();
        String email = "test@example.com";
        String displayName = "Test User";
        String profileImageUrl = "https://example.com/avatar.jpg";

        UserInfo userInfo = new UserInfo(id, email, displayName, profileImageUrl);

        assertEquals(id, userInfo.id);
        assertEquals(email, userInfo.email);
        assertEquals(displayName, userInfo.displayName);
        assertEquals(profileImageUrl, userInfo.profileImageUrl);
    }

    @Test
    void testUserInfo_FromEntity_ConvertsCorrectly() {
        CalendarUser user = new CalendarUser();
        user.id = UUID.randomUUID();
        user.email = "entity@example.com";
        user.displayName = "Entity User";
        user.profileImageUrl = "https://example.com/profile.jpg";

        UserInfo userInfo = UserInfo.fromEntity(user);

        assertEquals(user.id, userInfo.id);
        assertEquals(user.email, userInfo.email);
        assertEquals(user.displayName, userInfo.displayName);
        assertEquals(user.profileImageUrl, userInfo.profileImageUrl);
    }

    @Test
    void testUserInfo_FromEntity_HandlesNullValues() {
        CalendarUser user = new CalendarUser();
        user.id = UUID.randomUUID();
        user.email = "minimal@example.com";
        user.displayName = null;
        user.profileImageUrl = null;

        UserInfo userInfo = UserInfo.fromEntity(user);

        assertEquals(user.id, userInfo.id);
        assertEquals(user.email, userInfo.email);
        assertNull(userInfo.displayName);
        assertNull(userInfo.profileImageUrl);
    }

    // ========== AuthResponse Tests ==========

    @Test
    void testAuthResponse_DefaultConstructor_CreatesEmptyObject() {
        AuthResponse response = new AuthResponse();

        assertNull(response.token);
        assertNull(response.user);
    }

    @Test
    void testAuthResponse_ParameterizedConstructor_SetsAllFields() {
        String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.test";
        UserInfo user = new UserInfo(UUID.randomUUID(), "test@example.com", "Test", null);

        AuthResponse response = new AuthResponse(token, user);

        assertEquals(token, response.token);
        assertSame(user, response.user);
    }

    @Test
    void testAuthResponse_Of_StaticFactory_CreatesCorrectly() {
        String token = "jwt-token-123";
        UserInfo user = new UserInfo(UUID.randomUUID(), "factory@example.com", "Factory User", null);

        AuthResponse response = AuthResponse.of(token, user);

        assertNotNull(response);
        assertEquals(token, response.token);
        assertSame(user, response.user);
    }

    @Test
    void testAuthResponse_Of_WithNullValues_Works() {
        AuthResponse response = AuthResponse.of(null, null);

        assertNotNull(response);
        assertNull(response.token);
        assertNull(response.user);
    }

    @Test
    void testAuthResponse_WithFullUserInfo_IntegratesCorrectly() {
        // Create a full user info
        UUID userId = UUID.randomUUID();
        UserInfo user = new UserInfo(userId, "full@example.com", "Full User", "https://example.com/img.jpg");

        // Create auth response
        String token = "full-jwt-token";
        AuthResponse response = AuthResponse.of(token, user);

        // Verify everything is accessible
        assertEquals(token, response.token);
        assertEquals(userId, response.user.id);
        assertEquals("full@example.com", response.user.email);
        assertEquals("Full User", response.user.displayName);
        assertEquals("https://example.com/img.jpg", response.user.profileImageUrl);
    }
}
