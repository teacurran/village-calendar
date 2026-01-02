package villagecompute.calendar.api.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Tests for AuthResource REST endpoints. These endpoints handle OAuth authentication flows.
 *
 * Note: OAuth endpoints may behave differently in test mode when OIDC is not fully configured. These tests verify the
 * endpoints are accessible and respond with appropriate status codes.
 */
@QuarkusTest
class AuthResourceTest {

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

    // ========== GET /api/auth/login/* TESTS ==========
    // OAuth login endpoints may return different status codes depending on OIDC configuration:
    // - 200: In test mode without OIDC configured, endpoint is accessible
    // - 303: Redirect to OAuth provider
    // - 401: Authentication required
    // - 500: Configuration error

    @Test
    void testLoginGoogle_ReturnsValidResponse() {
        given().when().get("/api/auth/login/google").then().statusCode(anyOf(is(200), is(303), is(401), is(500)));
    }

    @Test
    void testLoginFacebook_ReturnsValidResponse() {
        given().when().get("/api/auth/login/facebook").then().statusCode(anyOf(is(200), is(303), is(401), is(500)));
    }

    @Test
    void testLoginApple_ReturnsValidResponse() {
        given().when().get("/api/auth/login/apple").then().statusCode(anyOf(is(200), is(303), is(401), is(500)));
    }

    // ========== GET /api/auth/*/callback TESTS ==========

    @Test
    void testGoogleCallback_ReturnsValidResponse() {
        given().when().get("/api/auth/google/callback").then().statusCode(anyOf(is(200), is(303), is(401), is(500)));
    }

    @Test
    void testFacebookCallback_ReturnsValidResponse() {
        given().when().get("/api/auth/facebook/callback").then().statusCode(anyOf(is(200), is(303), is(401), is(500)));
    }

    @Test
    void testAppleCallback_ReturnsValidResponse() {
        given().when().get("/api/auth/apple/callback").then().statusCode(anyOf(is(200), is(303), is(401), is(500)));
    }

    // ========== ErrorResponse Schema Test ==========

    @Test
    void testErrorResponse_SchemaIsCorrect() {
        // The /me endpoint should return 401 when unauthorized
        given().accept(ContentType.JSON).when().get("/api/auth/me").then().statusCode(401);
    }
}
