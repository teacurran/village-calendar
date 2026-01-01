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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.data.models.CalendarOrderItem;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.ItemAsset;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.services.OrderService;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

/** Tests for OrderResource - secure PDF download functionality */
@QuarkusTest
public class OrderResourceTest {

    @Inject
    OrderService orderService;

    @Inject
    ObjectMapper objectMapper;

    private CalendarUser testUser;
    private CalendarUser otherUser;
    private CalendarUser adminUser;
    private CalendarTemplate testTemplate;
    private UserCalendar testCalendar;

    @BeforeEach
    @Transactional
    void setup() {
        long timestamp = System.currentTimeMillis();

        // Create test template
        testTemplate = new CalendarTemplate();
        testTemplate.name = "OrderResource Test Template " + timestamp;
        testTemplate.description = "Template for OrderResource testing";
        testTemplate.isActive = true;
        testTemplate.isFeatured = false;
        testTemplate.displayOrder = 1;
        testTemplate.configuration = objectMapper.createObjectNode();
        testTemplate.persist();

        // Create test user
        testUser = new CalendarUser();
        testUser.oauthProvider = "GOOGLE";
        testUser.oauthSubject = "orderresource-test-" + timestamp;
        testUser.email = "orderresource-test@example.com";
        testUser.displayName = "OrderResource Test User";
        testUser.isAdmin = false;
        testUser.persist();

        // Create another user for access denial tests
        otherUser = new CalendarUser();
        otherUser.oauthProvider = "GOOGLE";
        otherUser.oauthSubject = "orderresource-other-" + timestamp;
        otherUser.email = "orderresource-other@example.com";
        otherUser.displayName = "Other User";
        otherUser.isAdmin = false;
        otherUser.persist();

        // Create admin user
        adminUser = new CalendarUser();
        adminUser.oauthProvider = "GOOGLE";
        adminUser.oauthSubject = "orderresource-admin-" + timestamp;
        adminUser.email = "orderresource-admin@example.com";
        adminUser.displayName = "Admin User";
        adminUser.isAdmin = true;
        adminUser.persist();

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
        // Clean up all test orders (both owned and guest)
        CalendarOrderItem.delete("order.id in (select o.id from CalendarOrder o where o.orderNumber like 'VC-%')");
        CalendarOrder.delete("orderNumber like 'VC-%'");
        UserCalendar.delete("user.id in (?1, ?2, ?3)", testUser.id, otherUser.id, adminUser.id);
        CalendarUser.deleteById(testUser.id);
        CalendarUser.deleteById(otherUser.id);
        CalendarUser.deleteById(adminUser.id);
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

    /** Create an order item with an ItemAsset containing SVG */
    private CalendarOrderItem createOrderItemWithAsset(CalendarOrder order, String svgContent, int year) {
        CalendarOrderItem item = new CalendarOrderItem();
        item.order = order;
        item.productType = CalendarOrderItem.TYPE_PRINT;
        item.productName = "Test Calendar " + year;
        item.setYear(year);
        item.quantity = 1;
        item.unitPrice = new BigDecimal("25.00");
        item.calculateLineTotal();
        item.itemStatus = CalendarOrderItem.STATUS_PENDING;
        item.persist();

        // Add SVG as ItemAsset
        if (svgContent != null) {
            ItemAsset asset = ItemAsset.create(ItemAsset.KEY_MAIN, svgContent);
            asset.persist();
            item.addAsset(asset);
        }

        order.items.add(item);
        return item;
    }

    /** Create a guest order (no user) */
    private CalendarOrder createGuestOrder(String orderNumber) {
        return QuarkusTransaction.requiringNew().call(() -> {
            CalendarOrder order = new CalendarOrder();
            order.user = null; // Guest order
            order.quantity = 1;
            order.unitPrice = new BigDecimal("25.00");
            order.totalPrice = new BigDecimal("25.00");
            order.status = CalendarOrder.STATUS_PAID;
            order.orderNumber = orderNumber;
            order.customerEmail = "guest@example.com";
            order.shippingAddress = objectMapper.createObjectNode();
            order.persist();

            createOrderItemWithAsset(order, createTestSvg(), 2025);

            return order;
        });
    }

    /** Create an order owned by a specific user */
    private CalendarOrder createUserOrder(CalendarUser owner, String orderNumber) {
        return QuarkusTransaction.requiringNew().call(() -> {
            CalendarOrder order = new CalendarOrder();
            order.user = owner;
            order.quantity = 1;
            order.unitPrice = new BigDecimal("25.00");
            order.totalPrice = new BigDecimal("25.00");
            order.status = CalendarOrder.STATUS_PAID;
            order.orderNumber = orderNumber;
            order.customerEmail = owner.email;
            order.shippingAddress = objectMapper.createObjectNode();
            order.persist();

            createOrderItemWithAsset(order, createTestSvg(), 2025);

            return order;
        });
    }

    // ==================== Basic Endpoint Tests ====================

    @Test
    @DisplayName("Order not found returns 404")
    void testOrderNotFound() {
        UUID randomItemId = UUID.randomUUID();
        given().when().get("/api/orders/INVALID123/items/" + randomItemId + "/pdf").then().statusCode(404)
                .body(containsString("Order not found"));
    }

    @Test
    @DisplayName("Item not found returns 404")
    void testItemNotFound() {
        CalendarOrder order = createGuestOrder("VC-ITEMNOTFOUND-" + System.currentTimeMillis());
        UUID wrongItemId = UUID.randomUUID();

        given().when().get("/api/orders/" + order.orderNumber + "/items/" + wrongItemId + "/pdf").then().statusCode(404)
                .body(containsString("Order item not found"));
    }

    @Test
    @DisplayName("Endpoint is correctly mapped")
    void testEndpointExists() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        UUID randomItemId = UUID.randomUUID();
        given().when().get("/api/orders/TEST123/items/" + randomItemId + "/pdf").then()
                .statusCode(anyOf(is(404), is(400), is(403), is(500))); // Should not be 405 Method Not Allowed
    }

    // ==================== PDF Generation Tests ====================

    @Test
    @DisplayName("PDF download uses year from order item")
    void testPdfDownload_WithCalendarYear_UsesYearFromOrderItem() {
        // Create a guest order with specific year
        CalendarOrder order = QuarkusTransaction.requiringNew().call(() -> {
            CalendarOrder newOrder = new CalendarOrder();
            newOrder.user = null; // Guest order for simpler testing
            newOrder.quantity = 1;
            newOrder.unitPrice = new BigDecimal("25.00");
            newOrder.totalPrice = new BigDecimal("25.00");
            newOrder.status = CalendarOrder.STATUS_PAID;
            newOrder.orderNumber = "VC-PDFTEST-" + System.currentTimeMillis();
            newOrder.customerEmail = "test@example.com";
            newOrder.shippingAddress = objectMapper.createObjectNode();
            newOrder.persist();

            createOrderItemWithAsset(newOrder, createTestSvg(), 2027);

            return newOrder;
        });

        UUID itemId = order.items.get(0).id;

        given().when().get("/api/orders/" + order.orderNumber + "/items/" + itemId + "/pdf").then().statusCode(200)
                .contentType("application/pdf").header(HEADER_CONTENT_DISPOSITION, containsString("calendar-"))
                .header(HEADER_CONTENT_DISPOSITION, containsString("-2027.pdf"));
    }

    @Test
    @DisplayName("PDF download with no SVG returns 400")
    void testPdfDownload_ItemWithNoSvg_ReturnsBadRequest() {
        CalendarOrder order = QuarkusTransaction.requiringNew().call(() -> {
            CalendarOrder newOrder = new CalendarOrder();
            newOrder.user = null; // Guest order
            newOrder.quantity = 1;
            newOrder.unitPrice = new BigDecimal("25.00");
            newOrder.totalPrice = new BigDecimal("25.00");
            newOrder.status = CalendarOrder.STATUS_PAID;
            newOrder.orderNumber = "VC-NOSVG-" + System.currentTimeMillis();
            newOrder.customerEmail = "test@example.com";
            newOrder.shippingAddress = objectMapper.createObjectNode();
            newOrder.persist();

            // Create order item WITHOUT SVG asset
            createOrderItemWithAsset(newOrder, null, 2025);

            return newOrder;
        });

        UUID itemId = order.items.get(0).id;

        given().when().get("/api/orders/" + order.orderNumber + "/items/" + itemId + "/pdf").then().statusCode(400)
                .body(containsString("No calendar content found"));
    }

    @Test
    @DisplayName("PDF download with wrong item ID returns 404")
    void testPdfDownload_ItemIdMismatch_ReturnsNotFound() {
        CalendarOrder order = createGuestOrder("VC-MISMATCH-" + System.currentTimeMillis());

        UUID wrongItemId = UUID.randomUUID();
        given().when().get("/api/orders/" + order.orderNumber + "/items/" + wrongItemId + "/pdf").then().statusCode(404)
                .body(containsString("Order item not found"));
    }

    @Test
    @DisplayName("PDF download with null customer email uses default filename")
    void testPdfDownload_NullCustomerEmail_UsesDefaultFilename() {
        CalendarOrder order = QuarkusTransaction.requiringNew().call(() -> {
            CalendarOrder newOrder = new CalendarOrder();
            newOrder.user = null;
            newOrder.quantity = 1;
            newOrder.unitPrice = new BigDecimal("25.00");
            newOrder.totalPrice = new BigDecimal("25.00");
            newOrder.status = CalendarOrder.STATUS_PAID;
            newOrder.orderNumber = "VC-NULLEMAIL-" + System.currentTimeMillis();
            newOrder.customerEmail = null; // No email
            newOrder.shippingAddress = objectMapper.createObjectNode();
            newOrder.persist();

            createOrderItemWithAsset(newOrder, createTestSvg(), 2025);

            return newOrder;
        });

        UUID itemId = order.items.get(0).id;

        given().when().get("/api/orders/" + order.orderNumber + "/items/" + itemId + "/pdf").then().statusCode(200)
                .header(HEADER_CONTENT_DISPOSITION, containsString("customer")); // Default name
    }

    // ==================== Security Tests ====================

    @Test
    @DisplayName("Guest order can be accessed without authentication")
    void testGuestOrder_UnauthenticatedAccess_Succeeds() {
        CalendarOrder order = createGuestOrder("VC-GUESTOK-" + System.currentTimeMillis());
        UUID itemId = order.items.get(0).id;

        given().when().get("/api/orders/" + order.orderNumber + "/items/" + itemId + "/pdf").then().statusCode(200)
                .contentType("application/pdf");
    }

    @Test
    @DisplayName("User order cannot be accessed without authentication")
    void testUserOrder_UnauthenticatedAccess_Denied() {
        CalendarOrder order = createUserOrder(testUser, "VC-NOAUTH-" + System.currentTimeMillis());
        UUID itemId = order.items.get(0).id;

        given().when().get("/api/orders/" + order.orderNumber + "/items/" + itemId + "/pdf").then().statusCode(403)
                .body(containsString("Access denied"));
    }

    // Note: JWT-authenticated access tests (owner access, admin access, cross-user access denial)
    // require proper OIDC test configuration. The canUserAccessOrder() method is tested via:
    // - Guest order access (order.user == null): testGuestOrder_UnauthenticatedAccess_Succeeds
    // - Unauthenticated access to user order: testUserOrder_UnauthenticatedAccess_Denied

    // ==================== Edge Case Tests ====================

    @Test
    @DisplayName("Sanitize filename removes special characters")
    void testFilenameWithSpecialCharacters() {
        CalendarOrder order = QuarkusTransaction.requiringNew().call(() -> {
            CalendarOrder newOrder = new CalendarOrder();
            newOrder.user = null;
            newOrder.quantity = 1;
            newOrder.unitPrice = new BigDecimal("25.00");
            newOrder.totalPrice = new BigDecimal("25.00");
            newOrder.status = CalendarOrder.STATUS_PAID;
            newOrder.orderNumber = "VC-SPECIAL-" + System.currentTimeMillis();
            newOrder.customerEmail = "user+tag@exam!ple.com"; // Special chars
            newOrder.shippingAddress = objectMapper.createObjectNode();
            newOrder.persist();

            createOrderItemWithAsset(newOrder, createTestSvg(), 2025);

            return newOrder;
        });

        UUID itemId = order.items.get(0).id;

        given().when().get("/api/orders/" + order.orderNumber + "/items/" + itemId + "/pdf").then().statusCode(200)
                .header(HEADER_CONTENT_DISPOSITION, containsString("usertag")); // Special chars removed
    }

    @Test
    @DisplayName("SVG from configuration fallback works")
    void testSvgFromConfigurationFallback() {
        CalendarOrder order = QuarkusTransaction.requiringNew().call(() -> {
            CalendarOrder newOrder = new CalendarOrder();
            newOrder.user = null;
            newOrder.quantity = 1;
            newOrder.unitPrice = new BigDecimal("25.00");
            newOrder.totalPrice = new BigDecimal("25.00");
            newOrder.status = CalendarOrder.STATUS_PAID;
            newOrder.orderNumber = "VC-CONFIGSVG-" + System.currentTimeMillis();
            newOrder.customerEmail = "test@example.com";
            newOrder.shippingAddress = objectMapper.createObjectNode();
            newOrder.persist();

            // Create item with SVG in configuration instead of asset
            CalendarOrderItem item = new CalendarOrderItem();
            item.order = newOrder;
            item.productType = CalendarOrderItem.TYPE_PRINT;
            item.productName = "Config SVG Test";
            item.setYear(2025);
            item.quantity = 1;
            item.unitPrice = new BigDecimal("25.00");
            item.calculateLineTotal();
            item.itemStatus = CalendarOrderItem.STATUS_PENDING;
            item.configuration = objectMapper.createObjectNode().put("generatedSvg", createTestSvg());
            item.persist();
            newOrder.items.add(item);

            return newOrder;
        });

        UUID itemId = order.items.get(0).id;

        given().when().get("/api/orders/" + order.orderNumber + "/items/" + itemId + "/pdf").then().statusCode(200)
                .contentType("application/pdf");
    }

    @Test
    @DisplayName("PDF generation with invalid SVG returns 500")
    void testPdfDownload_InvalidSvg_ReturnsError() {
        // Create order with malformed SVG that will cause PDF rendering to fail
        String invalidSvg = "not valid svg content <broken";

        CalendarOrder order = QuarkusTransaction.requiringNew().call(() -> {
            CalendarOrder newOrder = new CalendarOrder();
            newOrder.user = null;
            newOrder.quantity = 1;
            newOrder.unitPrice = new BigDecimal("25.00");
            newOrder.totalPrice = new BigDecimal("25.00");
            newOrder.status = CalendarOrder.STATUS_PAID;
            newOrder.orderNumber = "VC-INVALIDSVG-" + System.currentTimeMillis();
            newOrder.customerEmail = "test@example.com";
            newOrder.shippingAddress = objectMapper.createObjectNode();
            newOrder.persist();

            createOrderItemWithAsset(newOrder, invalidSvg, 2025);

            return newOrder;
        });

        UUID itemId = order.items.get(0).id;

        // Expect 500 error due to PDF rendering failure
        given().when().get("/api/orders/" + order.orderNumber + "/items/" + itemId + "/pdf").then().statusCode(500)
                .body(containsString("PDF generation failed"));
    }
}
