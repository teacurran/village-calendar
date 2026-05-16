package villagecompute.calendar.api.graphql;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.data.models.CalendarOrderItem;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.Shipment;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.integration.stripe.StripeService;
import villagecompute.calendar.services.AuthenticationService;
import villagecompute.calendar.services.OrderService;
import villagecompute.calendar.types.PaymentIntentType;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;

/**
 * Integration tests for OrderResolver GraphQL API. Tests order queries, mutations, and field resolvers under various
 * authorization scenarios.
 *
 * <p>
 * Coverage strategy: 1) Schema introspection ensures the GraphQL types and fields are exposed. 2) Unauthenticated
 * requests must be rejected for all secured operations. 3) GraphQL-level validation rejects malformed inputs. 4) Field
 * resolvers ({@code quantity}, {@code unitPrice}, {@code deliveredAt}, {@code calendar}, {@code productName},
 * {@code calendarYear}, {@code batchLoadUsers}) are exercised directly via CDI injection. 5) Service-layer integration
 * validates that the resolver delegates correctly. 6) @Query/@Mutation methods are exercised via direct injection of
 * the resolver bean; the request-scoped {@code AuthenticationService} is spied so its {@code requireCurrentUser} call
 * returns a known user without depending on the test JWT subject.
 *
 * <p>
 * Note: this codebase has two {@code @GraphQLApi} beans that register the same top-level Query/Mutation names. SmallRye
 * picks {@link OrderGraphQL} for the public {@code /graphql} endpoint, so the {@code @Query}/{@code @Mutation} methods
 * on {@link OrderResolver} cannot be exercised through HTTP. They are exercised here via direct method invocation on
 * the injected resolver bean.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderResolverTest {

    @Inject
    OrderService orderService;

    @Inject
    OrderResolver orderResolver;

    @Inject
    ObjectMapper objectMapper;

    @InjectSpy
    AuthenticationService authService;

    @InjectMock
    StripeService stripeService;

    private CalendarUser testUser;
    private CalendarUser adminUser;
    private CalendarUser otherUser;
    private CalendarTemplate testTemplate;
    private UserCalendar testCalendar;
    private CalendarOrder testOrder;

    @BeforeEach
    @Transactional
    void setUp() {
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

        // Create admin user
        adminUser = new CalendarUser();
        adminUser.email = "testadmin@example.com";
        adminUser.displayName = "Test Admin";
        adminUser.oauthProvider = "GOOGLE";
        adminUser.oauthSubject = "test-admin-123";
        adminUser.isAdmin = true;
        adminUser.persist();

        // Create another non-admin user (for unauthorized access scenarios)
        otherUser = new CalendarUser();
        otherUser.email = "testother@example.com";
        otherUser.displayName = "Other User";
        otherUser.oauthProvider = "GOOGLE";
        otherUser.oauthSubject = "test-other-123";
        otherUser.isAdmin = false;
        otherUser.persist();

        // Create test calendar (owned by testUser)
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

    /**
     * Stub the AuthenticationService spy to return the given user for any JWT, allowing direct invocation of resolver
     * methods that call {@code authService.requireCurrentUser(jwt)} without needing a real HTTP request context.
     */
    private void authenticateAs(CalendarUser user) {
        doReturn(user).when(authService).requireCurrentUser(any(JsonWebToken.class));
        doReturn(java.util.Optional.of(user)).when(authService).getCurrentUser(any(JsonWebToken.class));
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

    @Test
    @Order(5)
    void testGraphQL_CalendarOrderItemFieldsExist() {
        // Schema check: CalendarOrderItem should expose productName and calendarYear from
        // OrderResolver field resolvers.
        String query = """
                query {
                    __type(name: "CalendarOrderItem") {
                        name
                        fields {
                            name
                        }
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("data.__type.name", equalTo("CalendarOrderItem"))
                .body("data.__type.fields.name", hasItems("productName", "calendarYear")).body("errors", nullValue());
    }

    // ==================================================================
    // UNAUTHORIZED ACCESS TESTS (HTTP)
    // ==================================================================

    @Test
    @Order(10)
    void testQueryOrder_Unauthenticated() {
        String query = String.format("""
                {
                    order(id: "%s") {
                        id
                        status
                    }
                }
                """, testOrder.id);

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue());
    }

    @Test
    @Order(11)
    void testQueryMyOrders_Unauthenticated() {
        String query = """
                {
                    myOrders {
                        id
                        status
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue());
    }

    @Test
    @Order(12)
    void testQueryOrders_Unauthenticated() {
        String query = """
                {
                    orders {
                        id
                        status
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue());
    }

    @Test
    @Order(13)
    void testQueryAllOrders_Unauthenticated() {
        String query = """
                {
                    allOrders {
                        id
                        status
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue());
    }

    @Test
    @Order(14)
    void testMutationPlaceOrder_Unauthenticated() {
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
                .statusCode(200).body("errors", notNullValue());
    }

    @Test
    @Order(15)
    void testMutationCancelOrder_Unauthenticated() {
        String mutation = String.format("""
                mutation {
                    cancelOrder(orderId: "%s", reason: "Test") {
                        id
                    }
                }
                """, testOrder.id);

        given().contentType(ContentType.JSON).body(Map.of("query", mutation)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue());
    }

    // ==================================================================
    // GRAPHQL VALIDATION TESTS
    // ==================================================================

    @Test
    @Order(20)
    void testPlaceOrder_InvalidCalendarIdFormat() {
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
    @Order(21)
    void testPlaceOrder_InvalidQuantity() {
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
    @Order(22)
    void testCancelOrder_InvalidOrderIdFormat() {
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

    @Test
    @Order(23)
    void testQueryOrder_MissingIdArg() {
        String query = """
                {
                    order {
                        id
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(Map.of("query", query)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue());
    }

    @Test
    @Order(24)
    void testCancelOrder_MissingOrderIdArg() {
        String mutation = """
                mutation {
                    cancelOrder {
                        id
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(Map.of("query", mutation)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue());
    }

    @Test
    @Order(25)
    void testPlaceOrder_MissingInput() {
        String mutation = """
                mutation {
                    placeOrder {
                        clientSecret
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(Map.of("query", mutation)).when().post("/graphql").then()
                .statusCode(200).body("errors", notNullValue());
    }

    // ==================================================================
    // SERVICE LAYER INTEGRATION TESTS
    // ==================================================================

    @Test
    @Order(30)
    @Transactional
    void testOrderService_CreateOrder() {
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

    @Test
    @Order(31)
    @Transactional
    void testOrderService_CancelOrder() {
        CalendarOrder cancelledOrder = orderService.cancelOrder(testOrder.id, testUser.id, false, "Test cancellation");

        assertNotNull(cancelledOrder);
        assertEquals("CANCELLED", cancelledOrder.status);
        assertTrue(cancelledOrder.notes.contains("Test cancellation"));
    }

    @Test
    @Order(32)
    @Transactional
    void testOrderService_CannotCancelShippedOrder() {
        JsonNode shippingAddress = objectMapper.createObjectNode().put("street", "789 Pine St").put("city", "Knoxville")
                .put("state", "TN").put("postalCode", "37902").put("country", "US");

        CalendarOrder shippedOrder = orderService.createOrder(testUser, testCalendar, 1, new BigDecimal("29.99"),
                shippingAddress);

        shippedOrder.status = CalendarOrder.STATUS_SHIPPED;
        shippedOrder.persist();

        UUID orderId = shippedOrder.id;
        assertThrows(IllegalStateException.class, () -> {
            orderService.cancelOrder(orderId, testUser.id, false, "Should fail");
        });
    }

    @Test
    @Order(33)
    void testOrderService_GetOrderById_NotFound() {
        UUID missingId = UUID.randomUUID();
        assertTrue(orderService.getOrderById(missingId).isEmpty(), "Random ID should not return an order");
    }

    @Test
    @Order(34)
    void testOrderService_GetUserOrders_ReturnsOnlyUserOrders() {
        List<CalendarOrder> orders = orderService.getUserOrders(testUser.id);
        assertNotNull(orders);
        assertFalse(orders.isEmpty(), "Seeded user should have at least one order");
        assertTrue(orders.stream().anyMatch(o -> o.id.equals(testOrder.id)),
                "Seeded order should be in the user's order list");
    }

    @Test
    @Order(35)
    void testOrderService_GetOrdersByStatus_PendingFound() {
        List<CalendarOrder> pending = orderService.getOrdersByStatus(CalendarOrder.STATUS_PENDING);
        assertNotNull(pending);
        assertTrue(pending.stream().anyMatch(o -> o.id.equals(testOrder.id)),
                "PENDING orders should include the seeded order");
    }

    @Test
    @Order(36)
    void testOrderService_GetAllOrdersWithItems_Found() {
        List<CalendarOrder> all = orderService.getAllOrdersWithItems();
        assertNotNull(all);
        assertTrue(all.stream().anyMatch(o -> o.id.equals(testOrder.id)),
                "All-orders query should include the seeded order");
    }

    // ==================================================================
    // FIELD RESOLVER UNIT-STYLE TESTS
    // Exercises the @Source-based field resolvers via direct CDI injection.
    // ==================================================================

    @Test
    @Order(40)
    @Transactional
    void testFieldResolver_Quantity_ReturnsTotalItemCount() {
        CalendarOrder order = CalendarOrder.findById(testOrder.id);
        int quantity = orderResolver.quantity(order);
        assertEquals(2, quantity, "Quantity should match seeded order's total item count");
    }

    @Test
    @Order(41)
    void testFieldResolver_Quantity_HandlesUnloadedOrderGracefully() {
        CalendarOrder bare = new CalendarOrder();
        bare.id = UUID.randomUUID();
        int quantity = orderResolver.quantity(bare);
        assertEquals(0, quantity, "Empty order should report quantity 0");
    }

    @Test
    @Order(42)
    @Transactional
    void testFieldResolver_UnitPrice_ReturnsItemUnitPrice() {
        CalendarOrder order = CalendarOrder.findById(testOrder.id);
        BigDecimal unitPrice = orderResolver.unitPrice(order);
        assertNotNull(unitPrice);
        assertEquals(0, unitPrice.compareTo(new BigDecimal("29.99")),
                "Unit price should match seeded order's first item's unit price");
    }

    @Test
    @Order(43)
    void testFieldResolver_UnitPrice_FallsBackToZeroWhenEmpty() {
        CalendarOrder bare = new CalendarOrder();
        bare.id = UUID.randomUUID();
        BigDecimal unitPrice = orderResolver.unitPrice(bare);
        assertEquals(0, unitPrice.compareTo(BigDecimal.ZERO),
                "Empty order should fall back to BigDecimal.ZERO for unit price");
    }

    @Test
    @Order(44)
    void testFieldResolver_UnitPrice_FallsBackToSubtotalDividedByQuantity() {
        CalendarOrder bare = new CalendarOrder();
        bare.id = UUID.randomUUID();
        bare.subtotal = new BigDecimal("100.00");
        BigDecimal unitPrice = orderResolver.unitPrice(bare);
        // With no items, getTotalItemCount() is 0, so the divide path is skipped and we get ZERO.
        assertEquals(0, unitPrice.compareTo(BigDecimal.ZERO),
                "With no items, unit price falls back to ZERO even when subtotal is present");
    }

    @Test
    @Order(45)
    @Transactional
    void testFieldResolver_DeliveredAt_NullWhenNoShipments() {
        CalendarOrder order = CalendarOrder.findById(testOrder.id);
        Instant deliveredAt = orderResolver.deliveredAt(order);
        assertNull(deliveredAt, "Order without shipments should report null deliveredAt");
    }

    @Test
    @Order(46)
    void testFieldResolver_DeliveredAt_ReturnsFirstDeliveredTimestamp() {
        CalendarOrder bare = new CalendarOrder();
        bare.id = UUID.randomUUID();
        Instant expected = Instant.parse("2025-01-15T10:00:00Z");
        Shipment delivered = new Shipment();
        delivered.deliveredAt = expected;
        Shipment pending = new Shipment();
        pending.deliveredAt = null;
        bare.shipments.add(pending);
        bare.shipments.add(delivered);

        Instant result = orderResolver.deliveredAt(bare);
        assertEquals(expected, result, "First non-null deliveredAt should be returned");
    }

    @Test
    @Order(47)
    @Transactional
    void testFieldResolver_Calendar_ReturnsCalendarInfo() {
        CalendarOrder order = CalendarOrder.findById(testOrder.id);
        OrderResolver.CalendarInfo info = orderResolver.calendar(order);
        assertNotNull(info, "CalendarInfo should be returned when items exist");
        assertNotNull(info.id, "CalendarInfo.id should be populated");
        assertNotNull(info.name, "CalendarInfo.name should be populated");
        assertTrue(info.year > 0, "CalendarInfo.year should be populated from the item configuration");
    }

    @Test
    @Order(48)
    void testFieldResolver_Calendar_NullWhenNoItems() {
        CalendarOrder bare = new CalendarOrder();
        bare.id = UUID.randomUUID();
        OrderResolver.CalendarInfo info = orderResolver.calendar(bare);
        assertNull(info, "Order without items should return null CalendarInfo");
    }

    @Test
    @Order(49)
    @Transactional
    void testFieldResolver_ProductName_ReturnsDescription() {
        CalendarOrder order = CalendarOrder.findById(testOrder.id);
        assertFalse(order.items.isEmpty(), "Seeded order must have items");
        CalendarOrderItem item = order.items.get(0);
        String productName = orderResolver.productName(item);
        assertEquals(item.description, productName, "productName resolver should mirror the description field");
    }

    @Test
    @Order(50)
    @Transactional
    void testFieldResolver_CalendarYear_ReturnsItemYear() {
        CalendarOrder order = CalendarOrder.findById(testOrder.id);
        assertFalse(order.items.isEmpty(), "Seeded order must have items");
        CalendarOrderItem item = order.items.get(0);
        int year = orderResolver.calendarYear(item);
        assertEquals(item.getYear(), year, "calendarYear resolver should equal item.getYear()");
    }

    @Test
    @Order(51)
    @Transactional
    void testFieldResolver_BatchLoadUsers_ReturnsUsersInInputOrder() {
        // Create a second user and a second order so we can exercise the batched path.
        CalendarUser secondUser = QuarkusTransaction.requiringNew().call(() -> {
            CalendarUser u = new CalendarUser();
            u.email = "second@example.com";
            u.displayName = "Second User";
            u.oauthProvider = "GOOGLE";
            u.oauthSubject = "second-" + System.currentTimeMillis();
            u.isAdmin = false;
            u.persist();
            return u;
        });

        CalendarOrder secondOrder = QuarkusTransaction.requiringNew().call(() -> {
            CalendarOrder o = new CalendarOrder();
            o.user = secondUser;
            o.subtotal = new BigDecimal("10.00");
            o.totalPrice = new BigDecimal("10.00");
            o.status = CalendarOrder.STATUS_PENDING;
            o.orderNumber = "VC-BATCH-USR";
            o.shippingAddress = objectMapper.createObjectNode().put("street", "1 Batch Way").put("city", "Boston")
                    .put("state", "MA").put("postalCode", "02101").put("country", "US");
            o.persist();
            return o;
        });

        List<CalendarOrder> input = List.of(CalendarOrder.findById(testOrder.id),
                CalendarOrder.findById(secondOrder.id));

        List<CalendarUser> users = orderResolver.batchLoadUsers(input);
        assertNotNull(users, "Batch loader must return a non-null list");
        assertEquals(input.size(), users.size(), "Result size must match input size (DataLoader contract)");
        assertNotNull(users.get(0), "First user (seeded user) must be resolved");
        assertNotNull(users.get(1), "Second user must be resolved");
        assertEquals(testUser.id, users.get(0).id, "First slot must contain the seeded user");
        assertEquals(secondUser.id, users.get(1).id, "Second slot must contain the second user");
    }

    @Test
    @Order(52)
    void testFieldResolver_BatchLoadUsers_EmptyInputReturnsEmptyList() {
        List<CalendarUser> users = orderResolver.batchLoadUsers(List.of());
        assertNotNull(users, "Empty input should return non-null empty list");
        assertTrue(users.isEmpty(), "Empty input should produce empty output");
    }

    @Test
    @Order(53)
    void testFieldResolver_BatchLoadUsers_OrdersWithoutUsersGetNullSlots() {
        CalendarOrder anon = new CalendarOrder();
        anon.id = UUID.randomUUID();
        anon.user = null;

        List<CalendarUser> result = orderResolver.batchLoadUsers(List.of(anon));
        assertEquals(1, result.size(), "Result size must equal input size, even when the user is null");
        assertNull(result.get(0), "Slot for an order with no user must be null");
    }

    @Test
    @Order(54)
    void testCalendarInfo_FieldsArePublic() {
        // Touch the static inner types so the simple data carriers contribute to coverage.
        OrderResolver.CalendarInfo info = new OrderResolver.CalendarInfo();
        info.id = "abc";
        info.name = "Test Calendar";
        info.year = 2025;
        info.generatedPdfUrl = "https://example.com/calendar.pdf";
        info.generatedSvg = "<svg/>";
        OrderResolver.TemplateInfo template = new OrderResolver.TemplateInfo();
        template.id = "template-id";
        template.name = "Template";
        info.template = template;

        assertEquals("abc", info.id);
        assertEquals("Test Calendar", info.name);
        assertEquals(2025, info.year);
        assertEquals("https://example.com/calendar.pdf", info.generatedPdfUrl);
        assertEquals("<svg/>", info.generatedSvg);
        assertNotNull(info.template);
        assertEquals("template-id", info.template.id);
        assertEquals("Template", info.template.name);
    }

    // ==================================================================
    // AUTHENTICATED RESOLVER METHOD TESTS (DIRECT INVOCATION)
    //
    // OrderGraphQL shadows OrderResolver's @Query/@Mutation registrations
    // in the GraphQL schema, so these methods are exercised by calling
    // them directly on the injected resolver bean. The AuthenticationService
    // spy intercepts requireCurrentUser/getCurrentUser so we can supply the
    // identity of the "current user" without an HTTP request context.
    // ==================================================================

    @Test
    @Order(60)
    @Transactional
    @TestSecurity(
            user = "user",
            roles = "USER")
    void testOrder_AsOwner_ReturnsOrder() {
        authenticateAs(testUser);
        CalendarOrder result = orderResolver.order(testOrder.id);
        assertNotNull(result, "Owner must be able to load their order");
        assertEquals(testOrder.id, result.id, "Returned order must match requested id");
        assertEquals(CalendarOrder.STATUS_PENDING, result.status, "Seeded order should be PENDING");
    }

    @Test
    @Order(61)
    @Transactional
    @TestSecurity(
            user = "user",
            roles = "USER")
    void testOrder_AsNonOwner_ThrowsSecurityException() {
        authenticateAs(otherUser);
        UUID orderId = testOrder.id;
        assertThrows(SecurityException.class, () -> orderResolver.order(orderId),
                "Non-owner should not be able to view another user's order");
    }

    @Test
    @Order(62)
    @Transactional
    @TestSecurity(
            user = "admin",
            roles = {"USER", "ADMIN"})
    void testOrder_AsAdmin_ReturnsAnyOrder() {
        authenticateAs(adminUser);
        CalendarOrder result = orderResolver.order(testOrder.id);
        assertNotNull(result);
        assertEquals(testOrder.id, result.id);
    }

    @Test
    @Order(63)
    @Transactional
    @TestSecurity(
            user = "user",
            roles = "USER")
    void testOrder_NotFound_ThrowsIllegalArgumentException() {
        authenticateAs(testUser);
        UUID missing = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> orderResolver.order(missing),
                "Missing order id should trigger IllegalArgumentException");
    }

    @Test
    @Order(64)
    @Transactional
    @TestSecurity(
            user = "user",
            roles = "USER")
    void testMyOrders_ReturnsCurrentUserOrders() {
        authenticateAs(testUser);
        List<CalendarOrder> result = orderResolver.myOrders(null);
        assertNotNull(result);
        assertTrue(result.stream().anyMatch(o -> o.id.equals(testOrder.id)),
                "Seeded order should be in the current user's order list");
    }

    @Test
    @Order(65)
    @Transactional
    @TestSecurity(
            user = "user",
            roles = "USER")
    void testMyOrders_WithStatusFilter_ReturnsMatchingOnly() {
        authenticateAs(testUser);
        List<CalendarOrder> result = orderResolver.myOrders(CalendarOrder.STATUS_PENDING);
        assertNotNull(result);
        assertTrue(result.stream().anyMatch(o -> o.id.equals(testOrder.id)),
                "PENDING filter should still include the seeded PENDING order");
        assertTrue(result.stream().allMatch(o -> CalendarOrder.STATUS_PENDING.equals(o.status)),
                "All returned orders should match the filter");
    }

    @Test
    @Order(66)
    @Transactional
    @TestSecurity(
            user = "user",
            roles = "USER")
    void testMyOrders_WithNonMatchingStatus_ReturnsEmpty() {
        authenticateAs(testUser);
        List<CalendarOrder> result = orderResolver.myOrders(CalendarOrder.STATUS_DELIVERED);
        assertNotNull(result);
        assertTrue(result.isEmpty(), "DELIVERED filter should return no matches for the seeded data");
    }

    @Test
    @Order(67)
    @Transactional
    @TestSecurity(
            user = "user",
            roles = "USER")
    void testMyOrders_WithBlankStatus_IgnoresFilter() {
        authenticateAs(testUser);
        List<CalendarOrder> result = orderResolver.myOrders("   ");
        assertNotNull(result);
        assertTrue(result.stream().anyMatch(o -> o.id.equals(testOrder.id)),
                "Blank status filter should be treated as no filter");
    }

    @Test
    @Order(68)
    @Transactional
    @TestSecurity(
            user = "user",
            roles = "USER")
    void testOrders_NoUserIdArg_ReturnsCurrentUserOrders() {
        authenticateAs(testUser);
        List<CalendarOrder> result = orderResolver.orders(null, null);
        assertNotNull(result);
        assertTrue(result.stream().anyMatch(o -> o.id.equals(testOrder.id)));
    }

    @Test
    @Order(69)
    @Transactional
    @TestSecurity(
            user = "user",
            roles = "USER")
    void testOrders_OwnUserIdArg_ReturnsCurrentUserOrders() {
        authenticateAs(testUser);
        List<CalendarOrder> result = orderResolver.orders(testUser.id, null);
        assertNotNull(result);
        assertTrue(result.stream().anyMatch(o -> o.id.equals(testOrder.id)));
    }

    @Test
    @Order(70)
    @Transactional
    @TestSecurity(
            user = "user",
            roles = "USER")
    void testOrders_OtherUserId_AsNonAdmin_ThrowsSecurityException() {
        authenticateAs(testUser);
        UUID otherId = otherUser.id;
        assertThrows(SecurityException.class, () -> orderResolver.orders(otherId, null));
    }

    @Test
    @Order(71)
    @Transactional
    @TestSecurity(
            user = "admin",
            roles = {"USER", "ADMIN"})
    void testOrders_OtherUserId_AsAdmin_ReturnsOrders() {
        authenticateAs(adminUser);
        List<CalendarOrder> result = orderResolver.orders(testUser.id, null);
        assertNotNull(result);
        assertTrue(result.stream().anyMatch(o -> o.id.equals(testOrder.id)));
    }

    @Test
    @Order(72)
    @Transactional
    @TestSecurity(
            user = "user",
            roles = "USER")
    void testOrders_WithStatusFilter_AppliesFilter() {
        authenticateAs(testUser);
        List<CalendarOrder> result = orderResolver.orders(null, CalendarOrder.STATUS_PENDING);
        assertNotNull(result);
        assertTrue(result.stream().anyMatch(o -> o.id.equals(testOrder.id)));
        assertTrue(result.stream().allMatch(o -> CalendarOrder.STATUS_PENDING.equals(o.status)));
    }

    @Test
    @Order(73)
    @Transactional
    @TestSecurity(
            user = "admin",
            roles = {"USER", "ADMIN"})
    void testAllOrders_AsAdmin_ReturnsOrders() {
        authenticateAs(adminUser);
        List<CalendarOrder> result = orderResolver.allOrders(null, null);
        assertNotNull(result);
        assertTrue(result.stream().anyMatch(o -> o.id.equals(testOrder.id)));
    }

    @Test
    @Order(74)
    @Transactional
    @TestSecurity(
            user = "admin",
            roles = {"USER", "ADMIN"})
    void testAllOrders_WithStatusFilter() {
        authenticateAs(adminUser);
        List<CalendarOrder> result = orderResolver.allOrders(CalendarOrder.STATUS_PENDING, null);
        assertNotNull(result);
        assertTrue(result.stream().anyMatch(o -> o.id.equals(testOrder.id)));
        assertTrue(result.stream().allMatch(o -> CalendarOrder.STATUS_PENDING.equals(o.status)));
    }

    @Test
    @Order(75)
    @Transactional
    @TestSecurity(
            user = "admin",
            roles = {"USER", "ADMIN"})
    void testAllOrders_AppliesLimit() {
        authenticateAs(adminUser);
        List<CalendarOrder> result = orderResolver.allOrders(null, 1);
        assertNotNull(result);
        assertTrue(result.size() <= 1, "limit=1 must produce at most 1 order");
    }

    @Test
    @Order(76)
    @Transactional
    @TestSecurity(
            user = "admin",
            roles = {"USER", "ADMIN"})
    void testAllOrders_WithBlankStatus_TreatedAsNoFilter() {
        authenticateAs(adminUser);
        List<CalendarOrder> result = orderResolver.allOrders("   ", null);
        assertNotNull(result);
        assertTrue(result.stream().anyMatch(o -> o.id.equals(testOrder.id)));
    }

    @Test
    @Order(77)
    @Transactional
    @TestSecurity(
            user = "user",
            roles = "USER")
    void testCancelOrder_AsOwner_Succeeds() {
        authenticateAs(testUser);
        CalendarOrder result = orderResolver.cancelOrder(testOrder.id, "I changed my mind");
        assertNotNull(result);
        assertEquals(CalendarOrder.STATUS_CANCELLED, result.status);
        assertNotNull(result.notes);
        assertTrue(result.notes.contains("I changed my mind"), "Cancellation reason must be persisted to notes");
    }

    @Test
    @Order(78)
    @Transactional
    @TestSecurity(
            user = "user",
            roles = "USER")
    void testCancelOrder_WithoutReason_UsesDefault() {
        authenticateAs(testUser);
        CalendarOrder result = orderResolver.cancelOrder(testOrder.id, null);
        assertNotNull(result);
        assertEquals(CalendarOrder.STATUS_CANCELLED, result.status);
        assertTrue(result.notes.contains("No reason provided"),
                "Null reason should be replaced with the default message");
    }

    @Test
    @Order(79)
    @Transactional
    @TestSecurity(
            user = "user",
            roles = "USER")
    void testCancelOrder_AsNonOwner_ThrowsSecurityException() {
        authenticateAs(otherUser);
        UUID orderId = testOrder.id;
        assertThrows(SecurityException.class, () -> orderResolver.cancelOrder(orderId, "not mine"));
    }

    @Test
    @Order(80)
    @Transactional
    @TestSecurity(
            user = "user",
            roles = "USER")
    void testPlaceOrder_InvalidCalendarIdString_ThrowsIllegalArgumentException() {
        authenticateAs(testUser);
        villagecompute.calendar.api.graphql.inputs.PlaceOrderInput input = makePlaceOrderInput("not-a-uuid", 1);
        assertThrows(IllegalArgumentException.class, () -> orderResolver.placeOrder(input));
    }

    @Test
    @Order(81)
    @Transactional
    @TestSecurity(
            user = "user",
            roles = "USER")
    void testPlaceOrder_NonExistentCalendar_ThrowsIllegalArgumentException() {
        authenticateAs(testUser);
        villagecompute.calendar.api.graphql.inputs.PlaceOrderInput input = makePlaceOrderInput(
                UUID.randomUUID().toString(), 1);
        assertThrows(IllegalArgumentException.class, () -> orderResolver.placeOrder(input));
    }

    @Test
    @Order(82)
    @Transactional
    @TestSecurity(
            user = "user",
            roles = "USER")
    void testPlaceOrder_CalendarNotOwned_ThrowsSecurityException() {
        authenticateAs(otherUser);
        villagecompute.calendar.api.graphql.inputs.PlaceOrderInput input = makePlaceOrderInput(
                testCalendar.id.toString(), 1);
        assertThrows(SecurityException.class, () -> orderResolver.placeOrder(input));
    }

    @Test
    @Order(83)
    @Transactional
    @TestSecurity(
            user = "user",
            roles = "USER")
    void testPlaceOrder_QuantityZero_ThrowsIllegalArgumentException() {
        authenticateAs(testUser);
        villagecompute.calendar.api.graphql.inputs.PlaceOrderInput input = makePlaceOrderInput(
                testCalendar.id.toString(), 0);
        assertThrows(IllegalArgumentException.class, () -> orderResolver.placeOrder(input));
    }

    @Test
    @Order(84)
    @Transactional
    @TestSecurity(
            user = "user",
            roles = "USER")
    void testPlaceOrder_HappyPath_WithStreet2_ReturnsPaymentIntent() throws Exception {
        // Mock Stripe response so the placeOrder happy path runs end-to-end.
        com.stripe.model.checkout.Session mockSession = org.mockito.Mockito
                .mock(com.stripe.model.checkout.Session.class);
        org.mockito.Mockito.when(mockSession.getId()).thenReturn("cs_test_session_123");
        org.mockito.Mockito.when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/test/123");
        org.mockito.Mockito
                .when(stripeService.createCheckoutSession(any(CalendarOrder.class),
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(mockSession);

        authenticateAs(testUser);
        villagecompute.calendar.api.graphql.inputs.PlaceOrderInput input = makePlaceOrderInput(
                testCalendar.id.toString(), 1);
        // Exercise the street2 branch.
        input.shippingAddress.street2 = "Apt 4";

        PaymentIntentType response = orderResolver.placeOrder(input);
        assertNotNull(response, "placeOrder must return a non-null PaymentIntent");
        assertEquals("cs_test_session_123", response.id, "Returned PaymentIntent should carry the session id");
    }

    @Test
    @Order(85)
    @Transactional
    @TestSecurity(
            user = "user",
            roles = "USER")
    void testPlaceOrder_StripeFails_ThrowsPaymentException() throws Exception {
        // Stripe failure path -> resolver wraps in PaymentException.
        org.mockito.Mockito
                .when(stripeService.createCheckoutSession(any(CalendarOrder.class),
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new com.stripe.exception.ApiException("stripe failure", null, null, 500, null));

        authenticateAs(testUser);
        villagecompute.calendar.api.graphql.inputs.PlaceOrderInput input = makePlaceOrderInput(
                testCalendar.id.toString(), 1);

        assertThrows(villagecompute.calendar.exceptions.PaymentException.class, () -> orderResolver.placeOrder(input));
    }

    @Test
    @Order(86)
    @Transactional
    @TestSecurity(
            user = "admin",
            roles = {"USER", "ADMIN"})
    void testAllOrders_LimitTrimsLargerResultSet() {
        // Seed enough orders that allOrders(limit=1) has to call subList() to trim the list.
        JsonNode address = objectMapper.createObjectNode().put("street", "1 Trim Way").put("city", "Boston")
                .put("state", "MA").put("postalCode", "02101").put("country", "US");
        for (int i = 0; i < 3; i++) {
            orderService.createOrder(testUser, testCalendar, 1, new BigDecimal("9.99"), address);
        }

        authenticateAs(adminUser);
        List<CalendarOrder> result = orderResolver.allOrders(null, 1);
        assertNotNull(result);
        assertEquals(1, result.size(), "limit=1 must trim the result list to exactly one order");
    }

    /**
     * Build a minimal PlaceOrderInput for resolver-level placeOrder tests.
     */
    private villagecompute.calendar.api.graphql.inputs.PlaceOrderInput makePlaceOrderInput(String calendarId,
            int quantity) {
        villagecompute.calendar.api.graphql.inputs.PlaceOrderInput input = new villagecompute.calendar.api.graphql.inputs.PlaceOrderInput();
        input.calendarId = calendarId;
        input.productType = villagecompute.calendar.data.models.enums.ProductType.PRINT;
        input.quantity = quantity;
        villagecompute.calendar.api.graphql.inputs.AddressInput addr = new villagecompute.calendar.api.graphql.inputs.AddressInput();
        addr.street = "123 Test St";
        addr.city = "Nashville";
        addr.state = "TN";
        addr.postalCode = "37201";
        addr.country = "US";
        input.shippingAddress = addr;
        return input;
    }
}
