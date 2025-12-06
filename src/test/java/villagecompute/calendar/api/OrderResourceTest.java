package villagecompute.calendar.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import villagecompute.calendar.services.OrderService;
import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.data.models.CalendarOrderItem;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.data.models.CalendarUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Tests for OrderResource - secure PDF download functionality
 */
@QuarkusTest
public class OrderResourceTest {

    @Inject
    OrderService orderService;

    @Inject
    ObjectMapper objectMapper;

    @Test
    public void testOrderNotFound() {
        UUID randomItemId = UUID.randomUUID();
        given()
            .when().get("/api/orders/INVALID123/items/" + randomItemId + "/pdf")
            .then()
            .statusCode(404)
            .body(containsString("Order not found"));
    }

    @Test
    public void testItemNotFound() {
        // This test would require creating a test order first
        // For now, just test that the endpoint exists and handles the case
        UUID randomItemId = UUID.randomUUID();
        given()
            .when().get("/api/orders/TEST123/items/" + randomItemId + "/pdf")
            .then()
            .statusCode(404);
    }

    @Test
    public void testEndpointExists() {
        // Test that the endpoint path is correctly mapped
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        UUID randomItemId = UUID.randomUUID();
        given()
            .when().get("/api/orders/TEST123/items/" + randomItemId + "/pdf")
            .then()
            .statusCode(anyOf(is(404), is(400), is(500))); // Should not be 405 Method Not Allowed
    }

    // Note: Full integration tests would require setting up test data
    // with actual orders, users, and calendar content. This is a basic
    // smoke test to ensure the endpoint is properly configured.
}