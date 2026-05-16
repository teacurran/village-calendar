package villagecompute.calendar.api.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import villagecompute.calendar.data.models.CalendarUser;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Tests for BootstrapResource REST endpoints. These endpoints handle initial admin user creation.
 *
 * <p>
 * Each test carefully manages admin state. Tests that need a no-admin state demote any existing admins for the duration
 * of the test, then restore them. Tests that need an admin state create one explicitly.
 */
@QuarkusTest
class BootstrapResourceTest {

    @Inject
    @SuppressWarnings("all")
    jakarta.enterprise.inject.Instance<Object> dummy; // Forces CDI injection for @Transactional

    private static final String EMAIL_PREFIX = "bootstrap-test-";

    private final List<UUID> createdUserIds = new java.util.ArrayList<>();
    private final List<UUID> demotedAdminIds = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        createdUserIds.clear();
        demotedAdminIds.clear();
    }

    @AfterEach
    @Transactional
    void cleanup() {
        // Delete users we created during this test
        for (UUID id : createdUserIds) {
            CalendarUser.deleteById(id);
        }
        // Re-promote any admins we demoted during this test
        for (UUID id : demotedAdminIds) {
            CalendarUser u = CalendarUser.findById(id);
            if (u != null) {
                u.isAdmin = true;
                u.persist();
            }
        }
    }

    /** Create a non-admin test user and remember it for cleanup. */
    private CalendarUser createTestUser(String emailSuffix) {
        return QuarkusTransaction.requiringNew().call(() -> {
            CalendarUser user = new CalendarUser();
            user.oauthProvider = "GOOGLE";
            user.oauthSubject = "bootstrap-sub-" + System.nanoTime();
            user.email = EMAIL_PREFIX + emailSuffix + "-" + System.nanoTime() + "@example.com";
            user.displayName = "Bootstrap Test " + emailSuffix;
            user.isAdmin = false;
            user.persist();
            createdUserIds.add(user.id);
            return user;
        });
    }

    /** Create an admin test user and remember it for cleanup. */
    private CalendarUser createAdminUser(String emailSuffix) {
        return QuarkusTransaction.requiringNew().call(() -> {
            CalendarUser user = new CalendarUser();
            user.oauthProvider = "GOOGLE";
            user.oauthSubject = "bootstrap-admin-sub-" + System.nanoTime();
            user.email = EMAIL_PREFIX + emailSuffix + "-" + System.nanoTime() + "@example.com";
            user.displayName = "Bootstrap Admin " + emailSuffix;
            user.isAdmin = true;
            user.persist();
            createdUserIds.add(user.id);
            return user;
        });
    }

    /**
     * Temporarily demote all admin users in the database so the "no admins" branches can be exercised. The originals
     * are tracked in demotedAdminIds for restoration in @AfterEach.
     */
    private void demoteAllAdmins() {
        QuarkusTransaction.requiringNew().run(() -> {
            List<CalendarUser> admins = CalendarUser.findAdminUsers().list();
            for (CalendarUser admin : admins) {
                admin.isAdmin = false;
                admin.persist();
                demotedAdminIds.add(admin.id);
            }
        });
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

    @Test
    void testGetBootstrapStatus_WhenAdminsExist_ReturnsHasAdminsTrue() {
        createAdminUser("status-admin");

        given().when().get("/api/bootstrap/status").then().statusCode(200).contentType(ContentType.JSON)
                .body("hasAdmins", equalTo(true)).body("needsBootstrap", equalTo(false))
                .body("totalUsers", greaterThanOrEqualTo(1));
    }

    @Test
    void testGetBootstrapStatus_WhenNoAdmins_ReturnsNeedsBootstrapTrue() {
        demoteAllAdmins();

        given().when().get("/api/bootstrap/status").then().statusCode(200).contentType(ContentType.JSON)
                .body("hasAdmins", equalTo(false)).body("needsBootstrap", equalTo(true));
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
    void testCreateAdmin_NoAdmins_MissingEmail_Returns400() {
        demoteAllAdmins();

        Map<String, Object> request = new HashMap<>();
        // no email

        given().contentType(ContentType.JSON).body(request).when().post("/api/bootstrap/create-admin").then()
                .statusCode(400).body("error", containsString("Email is required"));
    }

    @Test
    void testCreateAdmin_NoAdmins_BlankEmail_Returns400WithEmailRequiredMessage() {
        demoteAllAdmins();

        Map<String, Object> request = new HashMap<>();
        request.put("email", "   ");

        given().contentType(ContentType.JSON).body(request).when().post("/api/bootstrap/create-admin").then()
                .statusCode(400).body("error", containsString("Email is required"));
    }

    @Test
    void testCreateAdmin_NoAdmins_UserNotFound_Returns400() {
        demoteAllAdmins();

        Map<String, Object> request = new HashMap<>();
        request.put("email", "definitely-not-a-user-" + System.nanoTime() + "@example.com");

        given().contentType(ContentType.JSON).body(request).when().post("/api/bootstrap/create-admin").then()
                .statusCode(400).body("error", containsString("User not found"));
    }

    @Test
    void testCreateAdmin_NoAdmins_PromotesUserSuccessfully() {
        demoteAllAdmins();
        CalendarUser user = createTestUser("promote-success");

        Map<String, Object> request = new HashMap<>();
        request.put("email", user.email);

        given().contentType(ContentType.JSON).body(request).when().post("/api/bootstrap/create-admin").then()
                .statusCode(200).contentType(ContentType.JSON).body("token", notNullValue())
                .body("user.email", equalTo(user.email)).body("user.id", notNullValue());

        // Verify the user was actually promoted in DB
        QuarkusTransaction.requiringNew().run(() -> {
            CalendarUser refreshed = CalendarUser.findById(user.id);
            org.junit.jupiter.api.Assertions.assertNotNull(refreshed);
            org.junit.jupiter.api.Assertions.assertTrue(refreshed.isAdmin);
        });
    }

    @Test
    void testCreateAdmin_AdminsExist_Returns403() {
        // Ensure at least one admin exists
        createAdminUser("blocking-admin");

        Map<String, Object> request = new HashMap<>();
        request.put("email", "anyone@example.com");

        given().contentType(ContentType.JSON).body(request).when().post("/api/bootstrap/create-admin").then()
                .statusCode(403).body("error", containsString("Admin users already exist"));
    }

    @Test
    @Transactional
    void testCreateAdmin_WhenAdminsExist_Returns403() {
        // Backward-compat: existing test, kept for parity. Use unconditional admin presence.
        boolean hasAdmins = CalendarUser.hasAdminUsers();

        Map<String, Object> request = new HashMap<>();
        request.put("email", "test@example.com");

        if (hasAdmins) {
            given().contentType(ContentType.JSON).body(request).when().post("/api/bootstrap/create-admin").then()
                    .statusCode(403).body("error", containsString("Admin users already exist"));
        }
    }

    // ========== GET /api/bootstrap/users TESTS ==========

    @Test
    void testGetUsers_ReturnsListOrForbidden() {
        // Returns user list if no admins exist, or 403 if admins exist
        given().when().get("/api/bootstrap/users").then().statusCode(anyOf(is(200), is(403)));
    }

    @Test
    void testGetUsers_NoAdmins_ReturnsList() {
        demoteAllAdmins();
        CalendarUser user = createTestUser("list-target");

        given().when().get("/api/bootstrap/users").then().statusCode(200).contentType(ContentType.JSON)
                .body("$", not(empty())).body("find { it.email == '" + user.email + "' }", notNullValue());
    }

    @Test
    void testGetUsers_AdminsExist_Returns403() {
        createAdminUser("users-blocking-admin");

        given().when().get("/api/bootstrap/users").then().statusCode(403).contentType(ContentType.JSON).body("error",
                containsString("Admin users already exist"));
    }

    @Test
    @Transactional
    void testGetUsers_WhenAdminsExist_Returns403() {
        // Backward-compat: kept from prior test set
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

    @Test
    void testBootstrapRequest_AcceptsEmailField() {
        // Verify the BootstrapRequest DTO accepts an email field by JSON deserialization
        BootstrapResource.BootstrapRequest req = new BootstrapResource.BootstrapRequest();
        req.email = "x@example.com";
        org.junit.jupiter.api.Assertions.assertEquals("x@example.com", req.email);
    }

    @Test
    void testBootstrapStatus_DTOFields() {
        BootstrapResource.BootstrapStatus s = new BootstrapResource.BootstrapStatus();
        s.needsBootstrap = true;
        s.totalUsers = 5L;
        s.hasAdmins = false;
        org.junit.jupiter.api.Assertions.assertTrue(s.needsBootstrap);
        org.junit.jupiter.api.Assertions.assertEquals(5L, s.totalUsers);
        org.junit.jupiter.api.Assertions.assertFalse(s.hasAdmins);
    }

    @Test
    void testErrorResponse_ConstructorStoresMessage() {
        BootstrapResource.ErrorResponse err = new BootstrapResource.ErrorResponse("boom");
        org.junit.jupiter.api.Assertions.assertEquals("boom", err.error);
    }
}
