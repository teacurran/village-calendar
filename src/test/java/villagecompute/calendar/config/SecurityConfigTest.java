package villagecompute.calendar.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.security.Principal;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests for SecurityConfig - security utilities for authentication.
 */
@QuarkusTest
class SecurityConfigTest {

    @Inject
    SecurityConfig securityConfig;

    @InjectMock
    SecurityIdentity securityIdentity;

    @BeforeEach
    void setUp() {
        reset(securityIdentity);
    }

    // ========== isAuthenticated Tests ==========

    @Test
    void testIsAuthenticated_WhenSecurityIdentityIsNull_ReturnsFalse() {
        // Quarkus doesn't allow null SecurityIdentity in managed context
        // So we test the anonymous case instead
        when(securityIdentity.isAnonymous()).thenReturn(true);

        assertFalse(securityConfig.isAuthenticated());
    }

    @Test
    void testIsAuthenticated_WhenAnonymous_ReturnsFalse() {
        when(securityIdentity.isAnonymous()).thenReturn(true);

        assertFalse(securityConfig.isAuthenticated());
    }

    @Test
    void testIsAuthenticated_WhenAuthenticated_ReturnsTrue() {
        when(securityIdentity.isAnonymous()).thenReturn(false);

        assertTrue(securityConfig.isAuthenticated());
    }

    // ========== hasRole Tests ==========

    @Test
    void testHasRole_WhenAnonymous_ReturnsFalse() {
        when(securityIdentity.isAnonymous()).thenReturn(true);
        when(securityIdentity.hasRole("ADMIN")).thenReturn(false);

        assertFalse(securityConfig.hasRole("ADMIN"));
    }

    @Test
    void testHasRole_WhenHasRole_ReturnsTrue() {
        when(securityIdentity.hasRole("ADMIN")).thenReturn(true);

        assertTrue(securityConfig.hasRole("ADMIN"));
    }

    @Test
    void testHasRole_WhenDoesNotHaveRole_ReturnsFalse() {
        when(securityIdentity.hasRole("ADMIN")).thenReturn(false);

        assertFalse(securityConfig.hasRole("ADMIN"));
    }

    @Test
    void testHasRole_WithUserRole_ReturnsCorrectly() {
        when(securityIdentity.hasRole("USER")).thenReturn(true);
        when(securityIdentity.hasRole("ADMIN")).thenReturn(false);

        assertTrue(securityConfig.hasRole("USER"));
        assertFalse(securityConfig.hasRole("ADMIN"));
    }

    // ========== getCurrentUserId Tests ==========

    @Test
    void testGetCurrentUserId_WhenAnonymous_ReturnsNull() {
        when(securityIdentity.isAnonymous()).thenReturn(true);

        assertNull(securityConfig.getCurrentUserId());
    }

    @Test
    void testGetCurrentUserId_WhenAuthenticated_ReturnsUUID() {
        UUID expectedId = UUID.randomUUID();
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(expectedId.toString());
        when(securityIdentity.isAnonymous()).thenReturn(false);
        when(securityIdentity.getPrincipal()).thenReturn(principal);

        UUID result = securityConfig.getCurrentUserId();

        assertEquals(expectedId, result);
    }

    @Test
    void testGetCurrentUserId_WhenInvalidUUID_ReturnsNull() {
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("not-a-valid-uuid");
        when(securityIdentity.isAnonymous()).thenReturn(false);
        when(securityIdentity.getPrincipal()).thenReturn(principal);

        UUID result = securityConfig.getCurrentUserId();

        assertNull(result);
    }

    @Test
    void testGetCurrentUserId_WhenEmptyPrincipalName_ReturnsNull() {
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("");
        when(securityIdentity.isAnonymous()).thenReturn(false);
        when(securityIdentity.getPrincipal()).thenReturn(principal);

        UUID result = securityConfig.getCurrentUserId();

        assertNull(result);
    }

    // ========== getCurrentUserEmail Tests ==========

    @Test
    void testGetCurrentUserEmail_WhenAnonymous_ReturnsNull() {
        when(securityIdentity.isAnonymous()).thenReturn(true);

        assertNull(securityConfig.getCurrentUserEmail());
    }

    @Test
    void testGetCurrentUserEmail_WhenAuthenticated_ReturnsEmail() {
        when(securityIdentity.isAnonymous()).thenReturn(false);
        when(securityIdentity.getAttribute("email")).thenReturn("user@example.com");

        String email = securityConfig.getCurrentUserEmail();

        assertEquals("user@example.com", email);
    }

    @Test
    void testGetCurrentUserEmail_WhenNoEmailAttribute_ReturnsNull() {
        when(securityIdentity.isAnonymous()).thenReturn(false);
        when(securityIdentity.getAttribute("email")).thenReturn(null);

        String email = securityConfig.getCurrentUserEmail();

        assertNull(email);
    }
}
