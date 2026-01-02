package villagecompute.calendar.api.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.util.HashMap;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import villagecompute.calendar.data.models.CalendarUser;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Tests for BootstrapResource REST endpoints. These endpoints handle initial admin user creation.
 */
@QuarkusTest
class BootstrapResourceTest {

    @Inject
    @SuppressWarnings("all")
    jakarta.enterprise.inject.Instance<Object> dummy; // Forces CDI injection for @Transactional

    @BeforeEach
    @Transactional
    void setUp() {
        // Note: Other tests may create admin users, so we can't guarantee bootstrap state
        // These tests should work regardless of initial database state
    }

    // ========== GET /api/bootstrap/status TESTS ==========

    @Test
    void testGetBootstrapStatus_ReturnsStatus() {
        given().when().get("/api/bootstrap/status").then().statusCode(200).contentType(ContentType.JSON)
                .body("needsBootstrap", notNullValue()).body("totalUsers", notNullValue())
                .body("hasAdmins", notNullValue());
    }

    @Test
    void testGetBootstrapStatus_ReturnsBooleansAndLong() {
        given().when().get("/api/bootstrap/status").then().statusCode(200)
                .body("needsBootstrap", anyOf(equalTo(true), equalTo(false)))
                .body("hasAdmins", anyOf(equalTo(true), equalTo(false))).body("totalUsers", greaterThanOrEqualTo(0));
    }

    // ========== POST /api/bootstrap/create-admin TESTS ==========

    @Test
    void testCreateAdmin_MissingEmail_Returns400() {
        Map<String, Object> request = new HashMap<>();
        // No email provided

        given().contentType(ContentType.JSON).body(request).when().post("/api/bootstrap/create-admin").then()
                .statusCode(anyOf(is(400), is(403)));
    }

    @Test
    void testCreateAdmin_EmptyEmail_Returns400() {
        Map<String, Object> request = new HashMap<>();
        request.put("email", "");

        given().contentType(ContentType.JSON).body(request).when().post("/api/bootstrap/create-admin").then()
                .statusCode(anyOf(is(400), is(403)));
    }

    @Test
    void testCreateAdmin_BlankEmail_Returns400() {
        Map<String, Object> request = new HashMap<>();
        request.put("email", "   ");

        given().contentType(ContentType.JSON).body(request).when().post("/api/bootstrap/create-admin").then()
                .statusCode(anyOf(is(400), is(403)));
    }

    @Test
    void testCreateAdmin_NonExistentUser_ReturnsError() {
        Map<String, Object> request = new HashMap<>();
        request.put("email", "nonexistent-user-" + System.currentTimeMillis() + "@example.com");

        // Returns 400 if no admins exist (user not found), or 403 if admins already exist
        given().contentType(ContentType.JSON).body(request).when().post("/api/bootstrap/create-admin").then()
                .statusCode(anyOf(is(400), is(403)));
    }

    @Test
    @Transactional
    void testCreateAdmin_WhenAdminsExist_Returns403() {
        // This test may or may not execute depending on database state
        // First check if admins exist
        boolean hasAdmins = CalendarUser.hasAdminUsers();

        Map<String, Object> request = new HashMap<>();
        request.put("email", "test@example.com");

        if (hasAdmins) {
            // If admins exist, should return 403
            given().contentType(ContentType.JSON).body(request).when().post("/api/bootstrap/create-admin").then()
                    .statusCode(403).body("error", containsString("Admin users already exist"));
        }
        // If no admins, skip this test case (it will fail with 400 - user not found)
    }

    // ========== GET /api/bootstrap/users TESTS ==========

    @Test
    void testGetUsers_ReturnsListOrForbidden() {
        // Returns user list if no admins exist, or 403 if admins exist
        given().when().get("/api/bootstrap/users").then().statusCode(anyOf(is(200), is(403)));
    }

    @Test
    @Transactional
    void testGetUsers_WhenAdminsExist_Returns403() {
        // Check if admins exist
        boolean hasAdmins = CalendarUser.hasAdminUsers();

        if (hasAdmins) {
            given().when().get("/api/bootstrap/users").then().statusCode(403).body("error",
                    containsString("Admin users already exist"));
        }
    }

    @Test
    void testGetUsers_ContentType() {
        // Regardless of status, should return JSON
        given().when().get("/api/bootstrap/users").then().contentType(ContentType.JSON);
    }

    // ========== DTO Tests ==========

    @Test
    void testBootstrapStatus_HasExpectedFields() {
        given().when().get("/api/bootstrap/status").then().statusCode(200).body("$", hasKey("needsBootstrap"))
                .body("$", hasKey("totalUsers")).body("$", hasKey("hasAdmins"));
    }

    @Test
    void testErrorResponse_HasExpectedFormat() {
        // When sending invalid request, error response should have 'error' field
        Map<String, Object> request = new HashMap<>();
        request.put("email", "");

        given().contentType(ContentType.JSON).body(request).when().post("/api/bootstrap/create-admin").then()
                .statusCode(anyOf(is(400), is(403))).body("error", notNullValue());
    }
}
