package villagecompute.calendar.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static villagecompute.calendar.util.MimeTypes.HEADER_CONTENT_DISPOSITION;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.data.models.CalendarOrderItem;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.services.OrderService;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

/** Tests for OrderResource - secure PDF download functionality */
@QuarkusTest
public class OrderResourceTest {

    @Inject OrderService orderService;

    @Inject ObjectMapper objectMapper;

    private CalendarUser testUser;
    private CalendarTemplate testTemplate;
    private UserCalendar testCalendar;

    @BeforeEach
    @Transactional
    void setup() {
        // Create test template
        testTemplate = new CalendarTemplate();
        testTemplate.name = "OrderResource Test Template " + System.currentTimeMillis();
        testTemplate.description = "Template for OrderResource testing";
        testTemplate.isActive = true;
        testTemplate.isFeatured = false;
        testTemplate.displayOrder = 1;
        testTemplate.configuration = objectMapper.createObjectNode();
        testTemplate.persist();

        // Create test user
        testUser = new CalendarUser();
        testUser.oauthProvider = "GOOGLE";
        testUser.oauthSubject = "orderresource-test-" + System.currentTimeMillis();
        testUser.email = "orderresource-test@example.com";
        testUser.displayName = "OrderResource Test User";
        testUser.persist();

        // Create test calendar with SVG content
        testCalendar = new UserCalendar();
        testCalendar.user = testUser;
        testCalendar.name = "Test Calendar for OrderResource";
        testCalendar.year = 2025;
        testCalendar.template = testTemplate;
        testCalendar.isPublic = false;
        testCalendar.configuration = objectMapper.createObjectNode();
        testCalendar.generatedSvg = createTestSvg();
        testCalendar.persist();
    }

    @AfterEach
    @Transactional
    void cleanup() {
        // Clean up test data in reverse FK order
        CalendarOrderItem.delete(
                "order.id in (select o.id from CalendarOrder o where o.user.id = ?1)", testUser.id);
        CalendarOrder.delete("user.id", testUser.id);
        UserCalendar.delete("user.id", testUser.id);
        CalendarUser.deleteById(testUser.id);
        CalendarTemplate.deleteById(testTemplate.id);
    }

    private String createTestSvg() {
        return """
            <svg xmlns="http://www.w3.org/2000/svg" width="100" height="100">
                <rect width="100" height="100" fill="white"/>
                <text x="10" y="50">Test Calendar</text>
            </svg>
            """;
    }

    @Test
    public void testOrderNotFound() {
        UUID randomItemId = UUID.randomUUID();
        given().when()
                .get("/api/orders/INVALID123/items/" + randomItemId + "/pdf")
                .then()
                .statusCode(404)
                .body(containsString("Order not found"));
    }

    @Test
    public void testItemNotFound() {
        // This test would require creating a test order first
        // For now, just test that the endpoint exists and handles the case
        UUID randomItemId = UUID.randomUUID();
        given().when()
                .get("/api/orders/TEST123/items/" + randomItemId + "/pdf")
                .then()
                .statusCode(404);
    }

    @Test
    public void testEndpointExists() {
        // Test that the endpoint path is correctly mapped
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        UUID randomItemId = UUID.randomUUID();
        given().when()
                .get("/api/orders/TEST123/items/" + randomItemId + "/pdf")
                .then()
                .statusCode(
                        anyOf(is(404), is(400), is(500))); // Should not be 405 Method Not Allowed
    }

    @Test
    void testPdfDownload_WithCalendarYear_UsesYearFromOrderItem() {
        // Given: Create an order with item that has year set
        CalendarOrder order =
                QuarkusTransaction.requiringNew()
                        .call(
                                () -> {
                                    CalendarOrder newOrder = new CalendarOrder();
                                    newOrder.user = testUser;
                                    newOrder.calendar = testCalendar;
                                    newOrder.quantity = 1;
                                    newOrder.unitPrice = new BigDecimal("25.00");
                                    newOrder.totalPrice = new BigDecimal("25.00");
                                    newOrder.status = CalendarOrder.STATUS_PAID;
                                    newOrder.orderNumber =
                                            "VC-PDFTEST-" + System.currentTimeMillis();
                                    newOrder.customerEmail = "test@example.com";
                                    newOrder.shippingAddress =
                                            objectMapper
                                                    .createObjectNode()
                                                    .put("street", "123 Test St")
                                                    .put("city", "Test City")
                                                    .put("state", "TS")
                                                    .put("postalCode", "12345")
                                                    .put("country", "US");
                                    newOrder.persist();

                                    // Create order item with year and linked calendar
                                    CalendarOrderItem item = new CalendarOrderItem();
                                    item.order = newOrder;
                                    item.calendar = testCalendar;
                                    item.productType = CalendarOrderItem.TYPE_PRINT;
                                    item.productName = "Test Calendar 2027";
                                    item.setYear(2027); // Use setYear to set the year
                                    item.quantity = 1;
                                    item.unitPrice = new BigDecimal("25.00");
                                    item.calculateLineTotal();
                                    item.itemStatus = CalendarOrderItem.STATUS_PENDING;
                                    item.persist();
                                    newOrder.items.add(item);

                                    return newOrder;
                                });

        UUID itemId = order.items.get(0).id;

        // When: Request PDF download
        // Then: Should generate PDF (uses getYear() internally)
        given().when()
                .get("/api/orders/" + order.orderNumber + "/items/" + itemId + "/pdf")
                .then()
                .statusCode(200)
                .contentType("application/pdf")
                .header(HEADER_CONTENT_DISPOSITION, containsString("calendar-"))
                .header(HEADER_CONTENT_DISPOSITION, containsString("-2027.pdf")); // Year should appear in filename
    }

    @Test
    void testPdfDownload_ItemWithNoSvg_ReturnsBadRequest() {
        // Given: Create an order with item that has no SVG content
        CalendarOrder order =
                QuarkusTransaction.requiringNew()
                        .call(
                                () -> {
                                    CalendarOrder newOrder = new CalendarOrder();
                                    newOrder.user = testUser;
                                    newOrder.quantity = 1;
                                    newOrder.unitPrice = new BigDecimal("25.00");
                                    newOrder.totalPrice = new BigDecimal("25.00");
                                    newOrder.status = CalendarOrder.STATUS_PAID;
                                    newOrder.orderNumber = "VC-NOSVG-" + System.currentTimeMillis();
                                    newOrder.customerEmail = "test@example.com";
                                    newOrder.shippingAddress = objectMapper.createObjectNode();
                                    newOrder.persist();

                                    // Create order item WITHOUT linked calendar (no SVG)
                                    CalendarOrderItem item = new CalendarOrderItem();
                                    item.order = newOrder;
                                    item.productType = CalendarOrderItem.TYPE_PRINT;
                                    item.productName = "Test Calendar No SVG";
                                    item.setYear(2025);
                                    item.quantity = 1;
                                    item.unitPrice = new BigDecimal("25.00");
                                    item.calculateLineTotal();
                                    item.itemStatus = CalendarOrderItem.STATUS_PENDING;
                                    item.persist();
                                    newOrder.items.add(item);

                                    return newOrder;
                                });

        UUID itemId = order.items.get(0).id;

        // When: Request PDF download
        // Then: Should return bad request (no SVG content)
        given().when()
                .get("/api/orders/" + order.orderNumber + "/items/" + itemId + "/pdf")
                .then()
                .statusCode(400)
                .body(containsString("No calendar content found"));
    }

    @Test
    void testPdfDownload_ItemIdMismatch_ReturnsNotFound() {
        // Given: Create an order
        CalendarOrder order =
                QuarkusTransaction.requiringNew()
                        .call(
                                () -> {
                                    CalendarOrder newOrder = new CalendarOrder();
                                    newOrder.user = testUser;
                                    newOrder.calendar = testCalendar;
                                    newOrder.quantity = 1;
                                    newOrder.unitPrice = new BigDecimal("25.00");
                                    newOrder.totalPrice = new BigDecimal("25.00");
                                    newOrder.status = CalendarOrder.STATUS_PAID;
                                    newOrder.orderNumber =
                                            "VC-MISMATCH-" + System.currentTimeMillis();
                                    newOrder.customerEmail = "test@example.com";
                                    newOrder.shippingAddress = objectMapper.createObjectNode();
                                    newOrder.persist();

                                    CalendarOrderItem item = new CalendarOrderItem();
                                    item.order = newOrder;
                                    item.calendar = testCalendar;
                                    item.productType = CalendarOrderItem.TYPE_PRINT;
                                    item.productName = "Test Calendar";
                                    item.setYear(2025);
                                    item.quantity = 1;
                                    item.unitPrice = new BigDecimal("25.00");
                                    item.calculateLineTotal();
                                    item.itemStatus = CalendarOrderItem.STATUS_PENDING;
                                    item.persist();
                                    newOrder.items.add(item);

                                    return newOrder;
                                });

        // When: Request PDF with wrong item ID
        UUID wrongItemId = UUID.randomUUID();
        given().when()
                .get("/api/orders/" + order.orderNumber + "/items/" + wrongItemId + "/pdf")
                .then()
                .statusCode(404)
                .body(containsString("Order item not found"));
    }
}
