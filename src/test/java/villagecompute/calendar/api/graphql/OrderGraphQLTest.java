package villagecompute.calendar.api.graphql;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.data.models.CalendarOrderItem;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.data.models.enums.ProductType;
import villagecompute.calendar.services.OrderService;
import villagecompute.calendar.services.ProductService;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Tests for OrderGraphQL mutations and queries. Focuses on: - GraphQL schema validation for ProductType enum - Service
 * layer integration for placeOrder/cancelOrder logic - Input validation and error handling
 *
 * <p>
 * Note: Full authenticated mutation tests require JWT token configuration. These tests focus on schema validation,
 * service logic, and unauthenticated rejection.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderGraphQLTest {

    @Inject
    OrderService orderService;
    @Inject
    ProductService productService;
    @Inject
    ObjectMapper objectMapper;

    private CalendarUser testUser;
    private CalendarTemplate testTemplate;
    private UserCalendar testCalendar;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up (order matters due to FK constraints)
        CalendarOrderItem.deleteAll();
        CalendarOrder.deleteAll();
        UserCalendar.deleteAll();
        CalendarUser.deleteAll();
        CalendarTemplate.deleteAll();

        // Create test template
        testTemplate = new CalendarTemplate();
        testTemplate.name = "Test Template";
        testTemplate.configuration = objectMapper.createObjectNode().put("test", "config");
        testTemplate.isActive = true;
        testTemplate.isFeatured = false;
        testTemplate.displayOrder = 1;
        testTemplate.persist();

        // Create test user
        testUser = new CalendarUser();
        testUser.email = "ordertest@example.com";
        testUser.displayName = "Order Test User";
        testUser.oauthProvider = "GOOGLE";
        testUser.oauthSubject = "order-test-123";
        testUser.isAdmin = false;
        testUser.persist();

        // Create test calendar
        testCalendar = new UserCalendar();
        testCalendar.user = testUser;
        testCalendar.template = testTemplate;
        testCalendar.name = "Order Test Calendar";
        testCalendar.year = 2025;
        testCalendar.isPublic = true;
        testCalendar.configuration = objectMapper.createObjectNode();
        testCalendar.persist();
    }

    // ==================================================================
    // GRAPHQL SCHEMA TESTS - ProductType Enum
    // ==================================================================

    @Test
    @Order(1)
    void testGraphQL_ProductTypeEnumExists() {
        String query = """
                query {
                    __type(name: "ProductType") {
                        name
                        kind
                        enumValues {
                            name
                        }
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("data.__type.name", equalTo("ProductType"))
                .body("data.__type.kind", equalTo("ENUM")).body("data.__type.enumValues.name", hasItems("PRINT", "PDF"))
                .body("data.__type.enumValues.name", not(hasItems("WALL_CALENDAR")))
                .body("data.__type.enumValues.name", not(hasItems("DESK_CALENDAR")))
                .body("data.__type.enumValues.name", not(hasItems("POSTER"))).body("errors", nullValue());
    }

    @Test
    @Order(2)
    void testGraphQL_PlaceOrderInputType() {
        String query = """
                query {
                    __type(name: "PlaceOrderInput") {
                        name
                        inputFields {
                            name
                            type {
                                name
                                kind
                                ofType {
                                    name
                                }
                            }
                        }
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("data.__type.name", equalTo("PlaceOrderInput"))
                .body("data.__type.inputFields.name",
                        hasItems("calendarId", "productType", "quantity", "shippingAddress"))
                .body("errors", nullValue());
    }

    @Test
    @Order(3)
    void testGraphQL_CreateOrderResponseType() {
        String query = """
                query {
                    __type(name: "CreateOrderResponse") {
                        name
                        fields {
                            name
                        }
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("data.__type.name", equalTo("CreateOrderResponse"))
                .body("data.__type.fields.name", hasItems("order", "clientSecret")).body("errors", nullValue());
    }

    // ==================================================================
    // PRODUCT TYPE PRICING INTEGRATION TESTS
    // ==================================================================

    @Test
    @Order(10)
    void testProductType_PrintPriceMatchesProductService() {
        String productCode = ProductType.PRINT.getProductCode();
        BigDecimal price = productService.getPrice(productCode);
        assertEquals(new BigDecimal("25.00"), price);
    }

    @Test
    @Order(11)
    void testProductType_PdfPriceMatchesProductService() {
        String productCode = ProductType.PDF.getProductCode();
        BigDecimal price = productService.getPrice(productCode);
        assertEquals(new BigDecimal("5.00"), price);
    }

    @Test
    @Order(12)
    void testProductType_AllTypesHaveValidProductCodes() {
        for (ProductType type : ProductType.values()) {
            String productCode = type.getProductCode();
            assertNotNull(productCode);
            assertTrue(productService.isValidProductCode(productCode), "Invalid product code for " + type);
        }
    }

    // ==================================================================
    // SERVICE LAYER ORDER CREATION TESTS
    // ==================================================================

    @Test
    @Order(20)
    @Transactional
    void testOrderService_CreateOrderWithPrintProduct() {
        BigDecimal printPrice = productService.getPrice(ProductType.PRINT.getProductCode());

        JsonNode shippingAddress = objectMapper.createObjectNode().put("street", "123 Print St")
                .put("city", "Nashville").put("state", "TN").put("postalCode", "37201").put("country", "US");

        CalendarOrder order = orderService.createOrder(testUser, testCalendar, 2, printPrice, shippingAddress);

        assertNotNull(order);
        assertEquals(CalendarOrder.STATUS_PENDING, order.status);
        assertNotNull(order.subtotal);
        assertNotNull(order.orderNumber);
    }

    @Test
    @Order(21)
    @Transactional
    void testOrderService_CreateOrderWithPdfProduct() {
        BigDecimal pdfPrice = productService.getPrice(ProductType.PDF.getProductCode());

        JsonNode shippingAddress = objectMapper.createObjectNode().put("street", "456 PDF Ave").put("city", "Memphis")
                .put("state", "TN").put("postalCode", "38101").put("country", "US");

        CalendarOrder order = orderService.createOrder(testUser, testCalendar, 1, pdfPrice, shippingAddress);

        assertNotNull(order);
        assertEquals(CalendarOrder.STATUS_PENDING, order.status);
        assertNotNull(order.subtotal);
    }

    // ==================================================================
    // SERVICE LAYER CANCEL ORDER TESTS
    // ==================================================================

    @Test
    @Order(30)
    @Transactional
    void testOrderService_CancelPendingOrder() {
        // Create order
        JsonNode shippingAddress = objectMapper.createObjectNode().put("street", "789 Cancel St")
                .put("city", "Knoxville").put("state", "TN").put("postalCode", "37902").put("country", "US");

        CalendarOrder order = orderService.createOrder(testUser, testCalendar, 1, productService.getPrice("print"),
                shippingAddress);

        assertEquals(CalendarOrder.STATUS_PENDING, order.status);

        // Cancel the order
        CalendarOrder cancelledOrder = orderService.cancelOrder(order.id, testUser.id, false, "Changed my mind");

        assertEquals(CalendarOrder.STATUS_CANCELLED, cancelledOrder.status);
        assertNotNull(cancelledOrder.cancelledAt);
        assertTrue(cancelledOrder.notes.contains("Changed my mind"));
    }

    @Test
    @Order(31)
    @Transactional
    void testOrderService_CancelPaidOrder() {
        JsonNode shippingAddress = objectMapper.createObjectNode().put("street", "101 Paid St").put("city", "Nashville")
                .put("state", "TN").put("postalCode", "37201").put("country", "US");

        CalendarOrder order = orderService.createOrder(testUser, testCalendar, 1, productService.getPrice("print"),
                shippingAddress);

        // Simulate payment
        order.status = CalendarOrder.STATUS_PAID;
        order.paidAt = java.time.Instant.now();
        order.stripePaymentIntentId = "pi_test_123";
        order.persist();

        // Cancel the paid order
        CalendarOrder cancelledOrder = orderService.cancelOrder(order.id, testUser.id, false, "No longer needed");

        assertEquals(CalendarOrder.STATUS_CANCELLED, cancelledOrder.status);
        assertTrue(cancelledOrder.notes.contains("No longer needed"));
        // Note: actual Stripe refund would be processed by OrderGraphQL.cancelOrder mutation
    }

    @Test
    @Order(32)
    @Transactional
    void testOrderService_CannotCancelDeliveredOrder() {
        JsonNode shippingAddress = objectMapper.createObjectNode().put("street", "202 Delivered St")
                .put("city", "Memphis").put("state", "TN").put("postalCode", "38101").put("country", "US");

        CalendarOrder order = orderService.createOrder(testUser, testCalendar, 1, productService.getPrice("print"),
                shippingAddress);

        // Simulate delivery
        order.status = CalendarOrder.STATUS_DELIVERED;
        order.persist();

        // Attempt to cancel should fail
        assertThrows(IllegalStateException.class,
                () -> orderService.cancelOrder(order.id, testUser.id, false, "Too late"));
    }

    @Test
    @Order(33)
    @Transactional
    void testOrderService_NonOwnerCannotCancel() {
        // Create another user
        CalendarUser otherUser = new CalendarUser();
        otherUser.email = "other@example.com";
        otherUser.displayName = "Other User";
        otherUser.oauthProvider = "GOOGLE";
        otherUser.oauthSubject = "other-user-456";
        otherUser.isAdmin = false;
        otherUser.persist();

        JsonNode shippingAddress = objectMapper.createObjectNode().put("street", "303 Owner St")
                .put("city", "Knoxville").put("state", "TN").put("postalCode", "37902").put("country", "US");

        CalendarOrder order = orderService.createOrder(testUser, testCalendar, 1, productService.getPrice("print"),
                shippingAddress);

        // Other user should not be able to cancel
        assertThrows(SecurityException.class,
                () -> orderService.cancelOrder(order.id, otherUser.id, false, "Trying to cancel"));
    }

    @Test
    @Order(34)
    @Transactional
    void testOrderService_AdminCanCancelAnyOrder() {
        // Create admin user
        CalendarUser adminUser = new CalendarUser();
        adminUser.email = "admin@example.com";
        adminUser.displayName = "Admin User";
        adminUser.oauthProvider = "GOOGLE";
        adminUser.oauthSubject = "admin-user-789";
        adminUser.isAdmin = true;
        adminUser.persist();

        JsonNode shippingAddress = objectMapper.createObjectNode().put("street", "404 Admin St")
                .put("city", "Nashville").put("state", "TN").put("postalCode", "37201").put("country", "US");

        CalendarOrder order = orderService.createOrder(testUser, testCalendar, 1, productService.getPrice("print"),
                shippingAddress);

        // Admin should be able to cancel
        CalendarOrder cancelledOrder = orderService.cancelOrder(order.id, adminUser.id, true, "Admin override");

        assertEquals(CalendarOrder.STATUS_CANCELLED, cancelledOrder.status);
        assertTrue(cancelledOrder.notes.contains("Admin override"));
    }

    // ==================================================================
    // GRAPHQL MUTATION UNAUTHENTICATED TESTS
    // ==================================================================

    @Test
    @Order(40)
    void testPlaceOrder_UnauthenticatedWithPrint() {
        String mutation = String.format("""
                mutation {
                    placeOrder(input: {
                        calendarId: "%s"
                        productType: PRINT
                        quantity: 1
                        shippingAddress: {
                            street: "123 Test St"
                            city: "Nashville"
                            state: "TN"
                            postalCode: "37201"
                            country: "US"
                        }
                    }) {
                        order {
                            id
                            status
                        }
                        clientSecret
                    }
                }
                """, testCalendar.id);

        given().contentType(ContentType.JSON).body(Map.of("query", mutation)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue()); // Requires authentication
    }

    @Test
    @Order(41)
    void testPlaceOrder_UnauthenticatedWithPdf() {
        String mutation = String.format("""
                mutation {
                    placeOrder(input: {
                        calendarId: "%s"
                        productType: PDF
                        quantity: 1
                        shippingAddress: {
                            street: "123 Test St"
                            city: "Nashville"
                            state: "TN"
                            postalCode: "37201"
                            country: "US"
                        }
                    }) {
                        order {
                            id
                        }
                        clientSecret
                    }
                }
                """, testCalendar.id);

        given().contentType(ContentType.JSON).body(Map.of("query", mutation)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue()); // Requires authentication
    }

    @Test
    @Order(42)
    @Transactional
    void testCancelOrder_Unauthenticated() {
        JsonNode shippingAddress = objectMapper.createObjectNode().put("country", "US");
        CalendarOrder order = orderService.createOrder(testUser, testCalendar, 1, productService.getPrice("print"),
                shippingAddress);

        String mutation = String.format("""
                mutation {
                    cancelOrder(orderId: "%s", reason: "Test cancellation") {
                        id
                        status
                    }
                }
                """, order.id);

        given().contentType(ContentType.JSON).body(Map.of("query", mutation)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue()); // Requires authentication
    }

    // ==================================================================
    // PUBLIC QUERIES - orderByNumber
    // ==================================================================

    @Test
    @Order(50)
    void testOrderByNumber_NotFound() {
        String query = """
                query {
                    orderByNumber(orderNumber: "VC-XXXX-XXXX") {
                        id
                        orderNumber
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("data.orderByNumber", nullValue()).body("errors", nullValue());
    }

    @Test
    @Order(52)
    void testOrderByNumber_Found() {
        // Create an order in a separate transaction
        String orderNumber = QuarkusTransaction.requiringNew().call(() -> {
            JsonNode shippingAddress = objectMapper.createObjectNode().put("country", "US");
            CalendarOrder order = orderService.createOrder(testUser, testCalendar, 1, productService.getPrice("print"),
                    shippingAddress);
            return order.orderNumber;
        });

        String query = String.format("""
                query {
                    orderByNumber(orderNumber: "%s") {
                        id
                        orderNumber
                        status
                    }
                }
                """, orderNumber);

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("data.orderByNumber.orderNumber", equalTo(orderNumber))
                .body("data.orderByNumber.status", equalTo("PENDING")).body("errors", nullValue());
    }

    // ==================================================================
    // PUBLIC QUERIES - orderByNumberAndId
    // ==================================================================

    @Test
    @Order(51)
    void testOrderByNumberAndId_InvalidUUID() {
        String query = """
                query {
                    orderByNumberAndId(orderNumber: "VC-TEST-1234", orderId: "not-a-uuid") {
                        id
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("data.orderByNumberAndId", nullValue()).body("errors", nullValue());
    }

    @Test
    @Order(55)
    void testOrderByNumberAndId_NotFoundOrderNumber() {
        String query = String.format("""
                query {
                    orderByNumberAndId(orderNumber: "VC-NOTFOUND-123", orderId: "%s") {
                        id
                    }
                }
                """, java.util.UUID.randomUUID());

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("data.orderByNumberAndId", nullValue()).body("errors", nullValue());
    }

    @Test
    @Order(56)
    void testOrderByNumberAndId_Found() {
        // Create an order in a separate transaction
        String[] orderDetails = QuarkusTransaction.requiringNew().call(() -> {
            JsonNode shippingAddress = objectMapper.createObjectNode().put("country", "US");
            CalendarOrder order = orderService.createOrder(testUser, testCalendar, 1, productService.getPrice("print"),
                    shippingAddress);
            return new String[]{order.orderNumber, order.id.toString()};
        });
        String orderNumber = orderDetails[0];
        String orderId = orderDetails[1];

        String query = String.format("""
                query {
                    orderByNumberAndId(orderNumber: "%s", orderId: "%s") {
                        id
                        orderNumber
                        status
                    }
                }
                """, orderNumber, orderId);

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("data.orderByNumberAndId.orderNumber", equalTo(orderNumber))
                .body("data.orderByNumberAndId.status", equalTo("PENDING")).body("errors", nullValue());
    }

    @Test
    @Order(57)
    void testOrderByNumberAndId_UuidMismatch() {
        // Create an order in a separate transaction
        String orderNumber = QuarkusTransaction.requiringNew().call(() -> {
            JsonNode shippingAddress = objectMapper.createObjectNode().put("country", "US");
            CalendarOrder order = orderService.createOrder(testUser, testCalendar, 1, productService.getPrice("print"),
                    shippingAddress);
            return order.orderNumber;
        });

        // Use correct order number but wrong UUID
        String query = String.format("""
                query {
                    orderByNumberAndId(orderNumber: "%s", orderId: "%s") {
                        id
                    }
                }
                """, orderNumber, java.util.UUID.randomUUID());

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("data.orderByNumberAndId", nullValue()).body("errors", nullValue());
    }

    // ==================================================================
    // MYORDERS QUERY - Unauthenticated
    // ==================================================================

    @Test
    @Order(60)
    void testMyOrders_Unauthenticated() {
        String query = """
                query {
                    myOrders {
                        id
                        orderNumber
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue()); // Requires authentication
    }

    @Test
    @Order(61)
    void testMyOrders_WithStatusFilter_Unauthenticated() {
        String query = """
                query {
                    myOrders(status: "pending") {
                        id
                        orderNumber
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue());
    }

    // ==================================================================
    // ORDER QUERY (by id) - Unauthenticated
    // ==================================================================

    @Test
    @Order(62)
    @Transactional
    void testOrder_ById_Unauthenticated() {
        JsonNode shippingAddress = objectMapper.createObjectNode().put("country", "US");
        CalendarOrder order = orderService.createOrder(testUser, testCalendar, 1, productService.getPrice("print"),
                shippingAddress);

        String query = String.format("""
                query {
                    order(id: "%s") {
                        id
                        orderNumber
                    }
                }
                """, order.id);

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue()); // Requires authentication
    }

    @Test
    @Order(63)
    void testOrder_InvalidId() {
        String query = """
                query {
                    order(id: "not-a-uuid") {
                        id
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue()); // Should fail due to invalid UUID or auth
    }

    // ==================================================================
    // ORDERS QUERY (with userId filter) - Unauthenticated
    // ==================================================================

    @Test
    @Order(64)
    void testOrders_Unauthenticated() {
        String query = """
                query {
                    orders {
                        id
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue());
    }

    @Test
    @Order(65)
    void testOrders_WithUserId_Unauthenticated() {
        String query = String.format("""
                query {
                    orders(userId: "%s") {
                        id
                    }
                }
                """, java.util.UUID.randomUUID());

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue());
    }

    // ==================================================================
    // ALLORDERS QUERY - Admin only
    // ==================================================================

    @Test
    @Order(66)
    void testAllOrders_Unauthenticated() {
        String query = """
                query {
                    allOrders {
                        id
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue()); // Requires ADMIN role
    }

    @Test
    @Order(67)
    void testAllOrders_WithFilters_Unauthenticated() {
        String query = """
                query {
                    allOrders(status: "pending", limit: 10) {
                        id
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue());
    }

    // ==================================================================
    // CREATEORDER MUTATION - Unauthenticated
    // ==================================================================

    @Test
    @Order(70)
    void testCreateOrder_Unauthenticated() {
        String mutation = String.format("""
                mutation {
                    createOrder(input: {
                        calendarId: "%s"
                        quantity: 1
                        shippingAddress: {
                            street: "123 Test St"
                            city: "Nashville"
                            state: "TN"
                            postalCode: "37201"
                            country: "US"
                        }
                    }) {
                        order {
                            id
                        }
                        clientSecret
                    }
                }
                """, testCalendar.id);

        given().contentType(ContentType.JSON).body(Map.of("query", mutation)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue());
    }

    // ==================================================================
    // UPDATEORDERSTATUS MUTATION - Unauthenticated
    // ==================================================================

    @Test
    @Order(71)
    @Transactional
    void testUpdateOrderStatus_Unauthenticated() {
        JsonNode shippingAddress = objectMapper.createObjectNode().put("country", "US");
        CalendarOrder order = orderService.createOrder(testUser, testCalendar, 1, productService.getPrice("print"),
                shippingAddress);

        String mutation = String.format("""
                mutation {
                    updateOrderStatus(input: {
                        orderId: "%s"
                        status: "processing"
                    }) {
                        id
                        status
                    }
                }
                """, order.id);

        given().contentType(ContentType.JSON).body(Map.of("query", mutation)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue()); // Requires ADMIN role
    }

}
