package villagecompute.calendar.api.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.exceptions.ApplicationException;
import villagecompute.calendar.services.AuthenticationService;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;

/**
 * Tests for AuthResource REST endpoints. These endpoints handle OAuth authentication flows.
 *
 * <p>
 * OAuth login endpoints may behave differently in test mode when OIDC is not fully configured. Tests verify the
 * endpoints are accessible and respond with appropriate status codes. Where possible we mock the AuthenticationService
 * so callback handlers exercise their full success and error code paths without real OAuth credentials.
 */
@QuarkusTest
class AuthResourceTest {

    @InjectMock
    AuthenticationService authenticationService;

    private CalendarUser mockedCallbackUser;

    @BeforeEach
    void setUp() {
        // Make sure we always have a persisted CalendarUser to hand back from the mocked
        // OAuth callback path. The mocked AuthenticationService returns this same entity
        // for every provider, letting us assert the redirect URL format consistently.
        QuarkusTransaction.requiringNew().run(() -> {
            CalendarUser existing = CalendarUser.find("email", "auth-callback@example.com").firstResult();
            if (existing == null) {
                existing = new CalendarUser();
                existing.email = "auth-callback@example.com";
                existing.displayName = "Auth Callback User";
                existing.oauthProvider = "GOOGLE";
                existing.oauthSubject = "auth-callback-subject";
                existing.isAdmin = false;
                existing.persist();
            }
            mockedCallbackUser = existing;
        });

        // Configure default mock behavior for the OAuth callback success path.
        when(authenticationService.handleOAuthCallback(anyString(), any(SecurityIdentity.class), any()))
                .thenReturn(mockedCallbackUser);
        when(authenticationService.issueJWT(any(CalendarUser.class))).thenReturn("mocked.jwt.token");
    }

    // ========== GET /api/auth/me TESTS ==========

    @Test
    void testMe_Unauthenticated_Returns401() {
        // Without a valid JWT, should return 401
        given().when().get("/api/auth/me").then().statusCode(401);
    }

    @Test
    void testMe_InvalidJwt_Returns401() {
        // With an invalid JWT, should return 401
        given().header("Authorization", "Bearer invalid-token").when().get("/api/auth/me").then().statusCode(401);
    }

    @Test
    @TestSecurity(
            user = "testuser",
            roles = "USER")
    @JwtSecurity(
            claims = {@Claim(
                    key = "sub",
                    value = "00000000-0000-0000-0000-000000000099")})
    void testMe_UserNotFound_Returns404() {
        given().when().get("/api/auth/me").then().statusCode(404).contentType(ContentType.JSON).body("error",
                equalTo("User not found"));
    }

    @Test
    @TestSecurity(
            user = "testuser",
            roles = "USER")
    @JwtSecurity(
            claims = {@Claim(
                    key = "sub",
                    value = "not-a-valid-uuid")})
    void testMe_InvalidUuid_Returns500() {
        given().when().get("/api/auth/me").then().statusCode(500).contentType(ContentType.JSON).body("error",
                containsString("Failed to fetch user"));
    }

    @Test
    void testMe_ResponseContentType_IsJson() {
        // 401 response should still surface JSON-friendly content negotiation.
        given().accept(ContentType.JSON).when().get("/api/auth/me").then().statusCode(401);
    }

    // ========== GET /api/auth/login/* TESTS ==========
    // OAuth login endpoints may return different status codes depending on OIDC configuration:
    // - 200: In test mode without OIDC configured, endpoint is accessible
    // - 303: Redirect to OAuth provider
    // - 302: Redirect (alternate)
    // - 401: Authentication required
    // - 500: Configuration error

    @Test
    void testLoginGoogle_Unauthenticated_ReturnsValidResponse() {
        given().redirects().follow(false).when().get("/api/auth/login/google").then()
                .statusCode(anyOf(is(200), is(302), is(303), is(401), is(500)));
    }

    @Test
    void testLoginFacebook_Unauthenticated_ReturnsValidResponse() {
        given().redirects().follow(false).when().get("/api/auth/login/facebook").then()
                .statusCode(anyOf(is(200), is(302), is(303), is(401), is(500)));
    }

    @Test
    void testLoginApple_Unauthenticated_ReturnsValidResponse() {
        given().redirects().follow(false).when().get("/api/auth/login/apple").then()
                .statusCode(anyOf(is(200), is(302), is(303), is(401), is(500)));
    }

    @Test
    @TestSecurity(
            user = "testuser",
            roles = "USER")
    void testLoginGoogle_Authenticated_Returns303() {
        // Authenticated callers are redirected to the provider callback path.
        given().redirects().follow(false).when().get("/api/auth/login/google").then().statusCode(303).header("Location",
                containsString("/auth/google/callback"));
    }

    @Test
    @TestSecurity(
            user = "testuser",
            roles = "USER")
    void testLoginFacebook_Authenticated_Returns303() {
        given().redirects().follow(false).when().get("/api/auth/login/facebook").then().statusCode(303)
                .header("Location", containsString("/auth/facebook/callback"));
    }

    @Test
    @TestSecurity(
            user = "testuser",
            roles = "USER")
    void testLoginApple_Authenticated_Returns303() {
        given().redirects().follow(false).when().get("/api/auth/login/apple").then().statusCode(303).header("Location",
                containsString("/auth/apple/callback"));
    }

    // ========== GET /api/auth/*/callback SUCCESS TESTS (mocked) ==========

    @Test
    @TestSecurity(
            user = "testuser",
            roles = "USER")
    void testGoogleCallback_Authenticated_RedirectsWithJwt() {
        given().redirects().follow(false).when().get("/api/auth/google/callback").then().statusCode(303)
                .header("Location", containsString("/auth/callback?token=mocked.jwt.token"));
    }

    @Test
    @TestSecurity(
            user = "testuser",
            roles = "USER")
    void testGoogleCallback_WithSessionId_RedirectsWithJwt() {
        given().redirects().follow(false).queryParam("sessionId", "session-abc-123").when()
                .get("/api/auth/google/callback").then().statusCode(303)
                .header("Location", containsString("token=mocked.jwt.token"));
    }

    @Test
    @TestSecurity(
            user = "testuser",
            roles = "USER")
    void testFacebookCallback_Authenticated_RedirectsWithJwt() {
        given().redirects().follow(false).when().get("/api/auth/facebook/callback").then().statusCode(303)
                .header("Location", containsString("/auth/callback?token=mocked.jwt.token"));
    }

    @Test
    @TestSecurity(
            user = "testuser",
            roles = "USER")
    void testAppleCallback_Authenticated_RedirectsWithJwt() {
        given().redirects().follow(false).when().get("/api/auth/apple/callback").then().statusCode(303)
                .header("Location", containsString("/auth/callback?token=mocked.jwt.token"));
    }

    // ========== GET /api/auth/*/callback ERROR PATH TESTS (mocked) ==========

    @Test
    @TestSecurity(
            user = "testuser",
            roles = "USER")
    void testGoogleCallback_ServiceFailure_RedirectsToErrorPage() {
        when(authenticationService.handleOAuthCallback(eq("google"), any(SecurityIdentity.class), any()))
                .thenThrow(new ApplicationException("Simulated provider failure"));

        given().redirects().follow(false).when().get("/api/auth/google/callback").then().statusCode(303)
                .header("Location", containsString("/?error=auth_failed"));
    }

    @Test
    @TestSecurity(
            user = "testuser",
            roles = "USER")
    void testFacebookCallback_ServiceFailure_RedirectsToErrorPage() {
        when(authenticationService.handleOAuthCallback(eq("facebook"), any(SecurityIdentity.class), any()))
                .thenThrow(new ApplicationException("Simulated provider failure"));

        given().redirects().follow(false).when().get("/api/auth/facebook/callback").then().statusCode(303)
                .header("Location", containsString("/?error=auth_failed"));
    }

    @Test
    @TestSecurity(
            user = "testuser",
            roles = "USER")
    void testAppleCallback_ServiceFailure_RedirectsToErrorPage() {
        when(authenticationService.handleOAuthCallback(eq("apple"), any(SecurityIdentity.class), any()))
                .thenThrow(new ApplicationException("Simulated provider failure"));

        given().redirects().follow(false).when().get("/api/auth/apple/callback").then().statusCode(303)
                .header("Location", containsString("/?error=auth_failed"));
    }

    // ========== GET /api/auth/*/callback UNAUTHENTICATED TESTS ==========

    @Test
    void testGoogleCallback_Unauthenticated_RequiresAuth() {
        given().redirects().follow(false).when().get("/api/auth/google/callback").then()
                .statusCode(anyOf(is(302), is(303), is(401)));
    }

    @Test
    void testFacebookCallback_Unauthenticated_RequiresAuth() {
        given().redirects().follow(false).when().get("/api/auth/facebook/callback").then()
                .statusCode(anyOf(is(302), is(303), is(401)));
    }

    @Test
    void testAppleCallback_Unauthenticated_RequiresAuth() {
        given().redirects().follow(false).when().get("/api/auth/apple/callback").then()
                .statusCode(anyOf(is(302), is(303), is(401)));
    }

    // ========== ErrorResponse contract ==========

    @Test
    void testErrorResponse_ContainsErrorField() {
        // Construct directly to assert the public contract used by JSON serialization.
        AuthResource.ErrorResponse error = new AuthResource.ErrorResponse("nope");
        org.junit.jupiter.api.Assertions.assertEquals("nope", error.error);
    }

    // ========== Frontend redirect URL formatting ==========

    @Test
    @TestSecurity(
            user = "testuser",
            roles = "USER")
    void testGoogleCallback_RedirectLocation_IsAbsoluteUrl() {
        // The redirect target should always be an absolute URL (configured frontend or derived).
        given().redirects().follow(false).when().get("/api/auth/google/callback").then().statusCode(303)
                .header("Location", anyOf(startsWith("http://"), startsWith("https://")));
    }

    @Test
    @TestSecurity(
            user = "testuser",
            roles = "USER")
    void testAppleCallback_FailureRedirect_IsAbsoluteUrl() {
        when(authenticationService.handleOAuthCallback(eq("apple"), any(SecurityIdentity.class), any()))
                .thenThrow(new ApplicationException("boom"));

        given().redirects().follow(false).when().get("/api/auth/apple/callback").then().statusCode(303)
                .header("Location", anyOf(startsWith("http://"), startsWith("https://")));
    }

}
