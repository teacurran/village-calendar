package villagecompute.calendar.api.graphql;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

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
import villagecompute.calendar.services.OrderService;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration tests for OrderResolver GraphQL API. Tests order queries and mutations with various authorization
 * scenarios.
 *
 * <p>
 * NOTE: Full authentication testing with JWT tokens requires additional Quarkus test configuration for the OIDC
 * provider (Keycloak/Auth0). The current tests focus on: - Schema validation (GraphQL types and fields are correctly
 * exposed) - Unauthenticated access rejection (security annotations work) - Service layer integration (business logic
 * works correctly) - Input validation (malformed requests are rejected)
 *
 * <p>
 * For production deployments, authenticated integration tests should be added using @TestSecurity annotation or by
 * configuring a test OIDC server.
 *
 * <p>
 * Required authenticated test scenarios: - User querying their own order (should succeed) - User querying another
 * user's order (should fail with SecurityException) - Admin querying any user's order (should succeed) - placeOrder
 * mutation with valid JWT token (should create order and return checkout URL) - cancelOrder mutation for PAID order
 * (should cancel and trigger refund) - cancelOrder mutation for SHIPPED order (should fail with IllegalStateException)
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderResolverTest {

    @Inject
    OrderService orderService;

    @Inject
    ObjectMapper objectMapper;

    private CalendarUser testUser;
    private CalendarTemplate testTemplate;
    private UserCalendar testCalendar;
    private CalendarOrder testOrder;

    @BeforeEach
    @Transactional
    void setUp() throws Exception {
        // Clean up existing test data (order matters due to FK constraints)
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
        testUser.email = "testuser@example.com";
        testUser.displayName = "Test User";
        testUser.oauthProvider = "GOOGLE";
        testUser.oauthSubject = "test-user-123";
        testUser.isAdmin = false;
        testUser.persist();

        // Create test calendar
        testCalendar = new UserCalendar();
        testCalendar.user = testUser;
        testCalendar.template = testTemplate;
        testCalendar.name = "Test Calendar 2025";
        testCalendar.year = 2025;
        testCalendar.isPublic = true;
        testCalendar.configuration = objectMapper.createObjectNode().put("custom", "data");
        testCalendar.persist();

        // Create test order
        JsonNode shippingAddress = objectMapper.createObjectNode().put("street", "123 Test St").put("city", "Nashville")
                .put("state", "TN").put("postalCode", "37201").put("country", "US");

        testOrder = orderService.createOrder(testUser, testCalendar, 2, new BigDecimal("29.99"), shippingAddress);
    }

    // ==================================================================
    // SCHEMA INTROSPECTION TESTS
    // ==================================================================

    @Test
    @Order(1)
    void testGraphQL_OrderQueriesExist() {
        String query = """
                query {
                    __type(name: "Query") {
                        name
                        fields {
                            name
                        }
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("data.__type.name", equalTo("Query"))
                .body("data.__type.fields.name", hasItems("order", "orders", "myOrders", "allOrders"))
                .body("errors", nullValue());
    }

    @Test
    @Order(2)
    void testGraphQL_OrderMutationsExist() {
        String query = """
                query {
                    __type(name: "Mutation") {
                        name
                        fields {
                            name
                        }
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("data.__type.name", equalTo("Mutation"))
                .body("data.__type.fields.name", hasItems("placeOrder", "cancelOrder")).body("errors", nullValue());
    }

    @Test
    @Order(3)
    void testGraphQL_OrderTypeSchema() {
        String query = """
                query {
                    __type(name: "CalendarOrder") {
                        name
                        fields {
                            name
                            type {
                                name
                                kind
                            }
                        }
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("data.__type.name", equalTo("CalendarOrder"))
                .body("data.__type.fields.name", hasItems("id", "status", "items", "totalPrice", "subtotal", "user"))
                .body("errors", nullValue());
    }

    @Test
    @Order(4)
    void testGraphQL_PaymentIntentTypeSchema() {
        String query = """
                query {
                    __type(name: "PaymentIntent") {
                        name
                        fields {
                            name
                            type {
                                name
                            }
                        }
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("data.__type.name", equalTo("PaymentIntent"))
                .body("data.__type.fields.name",
                        hasItems("id", "clientSecret", "amount", "calendarId", "quantity", "status"))
                .body("errors", nullValue());
    }

    // ==================================================================
    // UNAUTHORIZED ACCESS TESTS
    // ==================================================================

    @Test
    @Order(10)
    void testQueryOrder_Unauthenticated() {
        // Test: Querying order without authentication should fail
        String query = String.format("""
                {
                    order(id: "%s") {
                        id
                        status
                    }
                }
                """, testOrder.id);

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue()); // Should return error for unauthenticated access
    }

    @Test
    @Order(11)
    void testQueryMyOrders_Unauthenticated() {
        // Test: myOrders query without authentication should fail
        String query = """
                {
                    myOrders {
                        id
                        status
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue()); // Should return error for unauthenticated access
    }

    @Test
    @Order(12)
    void testQueryOrders_Unauthenticated() {
        // Test: orders query without authentication should fail
        String query = """
                {
                    orders {
                        id
                        status
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue()); // Should return error for unauthenticated access
    }

    @Test
    @Order(13)
    void testQueryAllOrders_Unauthenticated() {
        // Test: allOrders query without authentication should fail (admin only)
        String query = """
                {
                    allOrders {
                        id
                        status
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue()); // Should return error for unauthenticated access
    }

    @Test
    @Order(20)
    void testMutationPlaceOrder_Unauthenticated() {
        // Test: placeOrder mutation without authentication should fail
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
                        }
                        clientSecret
                    }
                }
                """, testCalendar.id);

        given().contentType(ContentType.JSON).body(Map.of("query", mutation)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue()); // Should return error for unauthenticated access
    }

    @Test
    @Order(21)
    void testMutationCancelOrder_Unauthenticated() {
        // Test: cancelOrder mutation without authentication should fail
        String mutation = String.format("""
                mutation {
                    cancelOrder(orderId: "%s", reason: "Test") {
                        id
                    }
                }
                """, testOrder.id);

        given().contentType(ContentType.JSON).body(Map.of("query", mutation)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue()); // Should return error for unauthenticated access
    }

    // ==================================================================
    // VALIDATION TESTS
    // ==================================================================

    @Test
    @Order(30)
    void testPlaceOrder_InvalidCalendarIdFormat() {
        // Test: placeOrder with invalid UUID format should return error
        String mutation = """
                mutation {
                    placeOrder(input: {
                        calendarId: "not-a-uuid"
                        productType: PRINT
                        quantity: 1
                        shippingAddress: {
                            name: "Test User"
                            line1: "123 Test St"
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
                """;

        given().contentType(ContentType.JSON).body(Map.of("query", mutation)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue());
    }

    @Test
    @Order(31)
    void testPlaceOrder_InvalidQuantity() {
        // Test: placeOrder with quantity 0 should fail validation
        String mutation = String.format("""
                mutation {
                    placeOrder(input: {
                        calendarId: "%s"
                        productType: PRINT
                        quantity: 0
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

    @Test
    @Order(32)
    void testCancelOrder_InvalidOrderIdFormat() {
        // Test: cancelOrder with invalid UUID format should return error
        String mutation = """
                mutation {
                    cancelOrder(orderId: "not-a-uuid") {
                        id
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(Map.of("query", mutation)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue());
    }

    // ==================================================================
    // SERVICE LAYER INTEGRATION TESTS
    // ==================================================================

    /**
     * Test that the OrderService is properly integrated and can create orders. This validates the business logic
     * independent of GraphQL authentication.
     */
    @Test
    @Order(40)
    @Transactional
    void testOrderService_CreateOrder() throws Exception {
        JsonNode shippingAddress = objectMapper.createObjectNode().put("street", "456 Oak Ave").put("city", "Memphis")
                .put("state", "TN").put("postalCode", "38101").put("country", "US");

        CalendarOrder order = orderService.createOrder(testUser, testCalendar, 3, new BigDecimal("29.99"),
                shippingAddress);

        assertNotNull(order);
        assertNotNull(order.id);
        assertEquals("PENDING", order.status);
        assertEquals(3, order.getTotalItemCount());
        assertNotNull(order.orderNumber);
    }

    /** Test that OrderService properly validates and cancels orders. */
    @Test
    @Order(41)
    @Transactional
    void testOrderService_CancelOrder() {
        // Test cancelling a PENDING order (should succeed)
        CalendarOrder cancelledOrder = orderService.cancelOrder(testOrder.id, testUser.id, false, // not admin
                "Test cancellation");

        assertNotNull(cancelledOrder);
        assertEquals("CANCELLED", cancelledOrder.status);
        assertTrue(cancelledOrder.notes.contains("Test cancellation"));
    }

    /** Test that OrderService prevents cancellation of shipped orders. */
    @Test
    @Order(42)
    @Transactional
    void testOrderService_CannotCancelShippedOrder() throws Exception {
        // Create a new order for this test
        JsonNode shippingAddress = objectMapper.createObjectNode().put("street", "789 Pine St").put("city", "Knoxville")
                .put("state", "TN").put("postalCode", "37902").put("country", "US");

        CalendarOrder shippedOrder = orderService.createOrder(testUser, testCalendar, 1, new BigDecimal("29.99"),
                shippingAddress);

        // Update order to SHIPPED status
        shippedOrder.status = CalendarOrder.STATUS_SHIPPED;
        shippedOrder.persist();

        // Attempt to cancel should throw exception
        UUID orderId = shippedOrder.id;
        assertThrows(IllegalStateException.class, () -> {
            orderService.cancelOrder(orderId, testUser.id, false, "Should fail");
        });
    }
}
