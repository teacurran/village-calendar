package villagecompute.calendar.api.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.services.AuthenticationService;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;

/**
 * Tests for UserCalendarResource using mocked AuthenticationService.
 */
@QuarkusTest
class UserCalendarResourceMockTest {

    @Inject
    ObjectMapper objectMapper;

    @InjectMock
    AuthenticationService authService;

    private CalendarUser testUser;
    private CalendarUser otherUser;
    private UUID testTemplateId;
    private UUID testCalendarId;

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            // Create test user
            CalendarUser user = CalendarUser.find("email", "usercal-mock-test@example.com").firstResult();
            if (user == null) {
                user = new CalendarUser();
                user.email = "usercal-mock-test@example.com";
                user.displayName = "UserCal Mock Test User";
                user.oauthProvider = "GOOGLE";
                user.oauthSubject = "usercal-mock-test";
                user.isAdmin = false;
                user.persist();
            }
            testUser = user;

            // Create other user
            CalendarUser other = CalendarUser.find("email", "usercal-mock-other@example.com").firstResult();
            if (other == null) {
                other = new CalendarUser();
                other.email = "usercal-mock-other@example.com";
                other.displayName = "Other User";
                other.oauthProvider = "GOOGLE";
                other.oauthSubject = "usercal-mock-other";
                other.isAdmin = false;
                other.persist();
            }
            otherUser = other;

            // Create template
            CalendarTemplate template = new CalendarTemplate();
            template.name = "UserCal Mock Test Template " + System.currentTimeMillis();
            template.slug = "usercal-mock-test-" + System.currentTimeMillis();
            template.isActive = true;
            template.priceCents = 2999;
            template.configuration = objectMapper.createObjectNode();
            template.persist();
            testTemplateId = template.id;

            // Create calendar
            UserCalendar calendar = new UserCalendar();
            calendar.user = user;
            calendar.template = template;
            calendar.name = "Mock Test Calendar";
            calendar.year = 2025;
            calendar.isPublic = false;
            calendar.configuration = objectMapper.createObjectNode();
            calendar.generatedSvg = "<svg><text>Mock Test</text></svg>";
            calendar.persist();
            testCalendarId = calendar.id;
        });
    }

    // ========== GET /calendar-templates/user/calendars TESTS ==========

    @Test
    @TestSecurity(user = "testuser", roles = "USER")
    void testGetCalendars_Authenticated_ReturnsCalendarList() {
        when(authService.getCurrentUser(any())).thenReturn(Optional.of(testUser));

        given().when().get("/api/calendar-templates/user/calendars").then().statusCode(200)
                .contentType(ContentType.JSON).body("$", hasSize(greaterThanOrEqualTo(1)))
                .body("[0].id", notNullValue());
    }

    @Test
    @TestSecurity(user = "testuser", roles = "USER")
    void testGetCalendars_UserNotFound_Returns401() {
        when(authService.getCurrentUser(any())).thenReturn(Optional.empty());

        given().when().get("/api/calendar-templates/user/calendars").then().statusCode(401);
    }

    // ========== POST /calendar-templates/user/save TESTS ==========

    @Test
    @TestSecurity(user = "testuser", roles = "USER")
    void testSaveCalendar_CreateNew_ReturnsSuccess() {
        when(authService.getCurrentUser(any())).thenReturn(Optional.of(testUser));

        Map<String, Object> request = new HashMap<>();
        request.put("name", "New Mock Calendar " + System.currentTimeMillis());
        request.put("templateId", testTemplateId.toString());

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar-templates/user/save").then()
                .statusCode(200).body("id", notNullValue()).body("success", equalTo(true));
    }

    @Test
    @TestSecurity(user = "testuser", roles = "USER")
    void testSaveCalendar_UpdateExisting_ReturnsSuccess() {
        when(authService.getCurrentUser(any())).thenReturn(Optional.of(testUser));

        Map<String, Object> request = new HashMap<>();
        request.put("id", testCalendarId.toString());
        request.put("name", "Updated Mock Calendar");

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar-templates/user/save").then()
                .statusCode(200).body("success", equalTo(true));
    }

    @Test
    @TestSecurity(user = "testuser", roles = "USER")
    void testSaveCalendar_UpdateNotOwned_Returns403() {
        when(authService.getCurrentUser(any())).thenReturn(Optional.of(otherUser));

        Map<String, Object> request = new HashMap<>();
        request.put("id", testCalendarId.toString());
        request.put("name", "Should Fail");

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar-templates/user/save").then()
                .statusCode(403).body("error", containsString("Not authorized"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = "USER")
    void testSaveCalendar_NonExistent_Returns404() {
        when(authService.getCurrentUser(any())).thenReturn(Optional.of(testUser));

        Map<String, Object> request = new HashMap<>();
        request.put("id", UUID.randomUUID().toString());
        request.put("name", "Should Fail");

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar-templates/user/save").then()
                .statusCode(404).body("error", equalTo("Calendar not found"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = "USER")
    void testSaveCalendar_WithConfiguration_ReturnsSuccess() {
        when(authService.getCurrentUser(any())).thenReturn(Optional.of(testUser));

        Map<String, Object> config = new HashMap<>();
        config.put("year", 2026);
        config.put("theme", "dark");

        Map<String, Object> request = new HashMap<>();
        request.put("name", "Configured Calendar " + System.currentTimeMillis());
        request.put("configuration", config);

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar-templates/user/save").then()
                .statusCode(200).body("success", equalTo(true));
    }

    @Test
    @TestSecurity(user = "testuser", roles = "USER")
    void testSaveCalendar_WithSvg_ReturnsSuccess() {
        when(authService.getCurrentUser(any())).thenReturn(Optional.of(testUser));

        Map<String, Object> request = new HashMap<>();
        request.put("name", "SVG Calendar " + System.currentTimeMillis());
        request.put("generatedSvg", "<svg><text>Custom</text></svg>");

        given().contentType(ContentType.JSON).body(request).when().post("/api/calendar-templates/user/save").then()
                .statusCode(200).body("success", equalTo(true));
    }

    // ========== GET /calendar-templates/user/calendars/{id}/preview TESTS ==========

    @Test
    @TestSecurity(user = "testuser", roles = "USER")
    void testGetPreview_OwnCalendar_ReturnsSvg() {
        when(authService.getCurrentUser(any())).thenReturn(Optional.of(testUser));

        given().when().get("/api/calendar-templates/user/calendars/" + testCalendarId + "/preview").then()
                .statusCode(200).contentType("image/svg+xml").body(containsString("<svg"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = "USER")
    void testGetPreview_NotOwned_Returns403() {
        when(authService.getCurrentUser(any())).thenReturn(Optional.of(otherUser));

        given().when().get("/api/calendar-templates/user/calendars/" + testCalendarId + "/preview").then()
                .statusCode(403).body("error", containsString("Not authorized"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = "USER")
    void testGetPreview_NonExistent_Returns404() {
        when(authService.getCurrentUser(any())).thenReturn(Optional.of(testUser));

        given().when().get("/api/calendar-templates/user/calendars/" + UUID.randomUUID() + "/preview").then()
                .statusCode(404).body("error", equalTo("Calendar not found"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = "USER")
    void testGetPreview_NoSvg_Returns404() {
        when(authService.getCurrentUser(any())).thenReturn(Optional.of(testUser));

        // Create calendar without SVG
        UUID noSvgId = QuarkusTransaction.requiringNew().call(() -> {
            UserCalendar cal = new UserCalendar();
            cal.user = testUser;
            cal.name = "No SVG Cal";
            cal.year = 2025;
            cal.generatedSvg = null;
            cal.persist();
            return cal.id;
        });

        given().when().get("/api/calendar-templates/user/calendars/" + noSvgId + "/preview").then().statusCode(404)
                .body("error", equalTo("No preview available"));
    }

    // ========== DELETE /calendar-templates/user/calendars/{id} TESTS ==========

    @Test
    @TestSecurity(user = "testuser", roles = "USER")
    void testDeleteCalendar_OwnCalendar_ReturnsSuccess() {
        when(authService.getCurrentUser(any())).thenReturn(Optional.of(testUser));

        // Create calendar to delete
        UUID deleteId = QuarkusTransaction.requiringNew().call(() -> {
            UserCalendar cal = new UserCalendar();
            cal.user = testUser;
            cal.name = "To Delete";
            cal.year = 2025;
            cal.persist();
            return cal.id;
        });

        given().when().delete("/api/calendar-templates/user/calendars/" + deleteId).then().statusCode(200)
                .body("status", equalTo("success"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = "USER")
    void testDeleteCalendar_NotOwned_Returns403() {
        when(authService.getCurrentUser(any())).thenReturn(Optional.of(otherUser));

        given().when().delete("/api/calendar-templates/user/calendars/" + testCalendarId).then().statusCode(403)
                .body("error", containsString("Not authorized"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = "USER")
    void testDeleteCalendar_NonExistent_Returns404() {
        when(authService.getCurrentUser(any())).thenReturn(Optional.of(testUser));

        given().when().delete("/api/calendar-templates/user/calendars/" + UUID.randomUUID()).then().statusCode(404)
                .body("error", equalTo("Calendar not found"));
    }
}
