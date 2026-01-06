package villagecompute.calendar.api.graphql;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;

import villagecompute.calendar.api.graphql.inputs.AddressInput;
import villagecompute.calendar.api.graphql.inputs.OrderInput;
import villagecompute.calendar.api.graphql.inputs.OrderStatusUpdateInput;
import villagecompute.calendar.api.graphql.inputs.PlaceOrderInput;
import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.data.models.enums.ProductType;
import villagecompute.calendar.services.AuthenticationService;
import villagecompute.calendar.services.OrderService;
import villagecompute.calendar.services.PaymentService;
import villagecompute.calendar.services.ProductService;
import villagecompute.calendar.util.Roles;

/**
 * Unit tests for OrderGraphQL. Tests all query and mutation methods with mocked dependencies. Achieves high coverage by
 * testing success paths, error paths, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
class OrderGraphQLUnitTest {

    @InjectMocks
    OrderGraphQL orderGraphQL;

    @Mock
    JsonWebToken jwt;

    @Mock
    AuthenticationService authService;

    @Mock
    OrderService orderService;

    @Mock
    PaymentService paymentService;

    @Mock
    ProductService productService;

    ObjectMapper objectMapper = new ObjectMapper();

    private CalendarUser testUser;
    private CalendarUser adminUser;
    private UserCalendar testCalendar;
    private CalendarOrder testOrder;

    @BeforeEach
    void setUp() throws Exception {
        // Inject ObjectMapper manually
        java.lang.reflect.Field field = OrderGraphQL.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(orderGraphQL, objectMapper);

        // Create test user
        testUser = new CalendarUser();
        testUser.id = UUID.randomUUID();
        testUser.email = "test@example.com";
        testUser.displayName = "Test User";
        testUser.isAdmin = false;

        // Create admin user
        adminUser = new CalendarUser();
        adminUser.id = UUID.randomUUID();
        adminUser.email = "admin@example.com";
        adminUser.displayName = "Admin User";
        adminUser.isAdmin = true;

        // Create test calendar
        testCalendar = new UserCalendar();
        testCalendar.id = UUID.randomUUID();
        testCalendar.user = testUser;
        testCalendar.name = "Test Calendar";
        testCalendar.year = 2025;

        // Create test order
        testOrder = new CalendarOrder();
        testOrder.id = UUID.randomUUID();
        testOrder.user = testUser;
        testOrder.orderNumber = "VC-TEST-1234";
        testOrder.status = CalendarOrder.STATUS_PENDING;
        testOrder.subtotal = new BigDecimal("25.00");
        testOrder.totalPrice = new BigDecimal("30.00");
        testOrder.notes = "";
        testOrder.items = new ArrayList<>();
    }

    // ============================================================================
    // MYORDERS QUERY TESTS
    // ============================================================================

    @Nested
    class MyOrdersTests {

        @Test
        void myOrders_ReturnsAllUserOrders() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(orderService.getUserOrders(testUser.id)).thenReturn(List.of(testOrder));

            List<CalendarOrder> result = orderGraphQL.myOrders(null);

            assertEquals(1, result.size());
            assertEquals(testOrder.id, result.get(0).id);
            verify(orderService).getUserOrders(testUser.id);
        }

        @Test
        void myOrders_WithStatusFilter_ReturnsFilteredOrders() {
            CalendarOrder paidOrder = new CalendarOrder();
            paidOrder.id = UUID.randomUUID();
            paidOrder.status = CalendarOrder.STATUS_PAID;

            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(orderService.getUserOrders(testUser.id)).thenReturn(List.of(testOrder, paidOrder));

            List<CalendarOrder> result = orderGraphQL.myOrders("PENDING");

            assertEquals(1, result.size());
            assertEquals(CalendarOrder.STATUS_PENDING, result.get(0).status);
        }

        @Test
        void myOrders_WithEmptyStatusFilter_ReturnsAllOrders() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(orderService.getUserOrders(testUser.id)).thenReturn(List.of(testOrder));

            List<CalendarOrder> result = orderGraphQL.myOrders("");

            assertEquals(1, result.size());
        }

        @Test
        void myOrders_NoOrders_ReturnsEmptyList() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(orderService.getUserOrders(testUser.id)).thenReturn(List.of());

            List<CalendarOrder> result = orderGraphQL.myOrders(null);

            assertTrue(result.isEmpty());
        }
    }

    // ============================================================================
    // ORDERS QUERY TESTS
    // ============================================================================

    @Nested
    class OrdersQueryTests {

        @Test
        void orders_WithoutUserId_ReturnsCurrentUserOrders() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(orderService.getUserOrders(testUser.id)).thenReturn(List.of(testOrder));

            List<CalendarOrder> result = orderGraphQL.orders(null, null);

            assertEquals(1, result.size());
            verify(orderService).getUserOrders(testUser.id);
        }

        @Test
        void orders_WithEmptyUserId_ReturnsCurrentUserOrders() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(orderService.getUserOrders(testUser.id)).thenReturn(List.of(testOrder));

            List<CalendarOrder> result = orderGraphQL.orders("", null);

            assertEquals(1, result.size());
        }

        @Test
        void orders_AdminAccessingOtherUserOrders_Succeeds() {
            UUID targetUserId = UUID.randomUUID();
            when(authService.requireCurrentUser(jwt)).thenReturn(adminUser);
            when(jwt.getGroups()).thenReturn(Set.of(Roles.ADMIN));
            when(orderService.getUserOrders(targetUserId)).thenReturn(List.of(testOrder));

            List<CalendarOrder> result = orderGraphQL.orders(targetUserId.toString(), null);

            assertEquals(1, result.size());
            verify(orderService).getUserOrders(targetUserId);
        }

        @Test
        void orders_NonAdminAccessingOtherUserOrders_ThrowsSecurityException() {
            String targetUserId = UUID.randomUUID().toString();
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(jwt.getGroups()).thenReturn(Set.of(Roles.USER));

            assertThrows(SecurityException.class, () -> orderGraphQL.orders(targetUserId, null));
        }

        @Test
        void orders_NonAdminWithNullGroups_ThrowsSecurityException() {
            String targetUserId = UUID.randomUUID().toString();
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(jwt.getGroups()).thenReturn(null);

            assertThrows(SecurityException.class, () -> orderGraphQL.orders(targetUserId, null));
        }

        @Test
        void orders_WithInvalidUserIdFormat_ThrowsIllegalArgumentException() {
            when(authService.requireCurrentUser(jwt)).thenReturn(adminUser);
            when(jwt.getGroups()).thenReturn(Set.of(Roles.ADMIN));

            assertThrows(IllegalArgumentException.class, () -> orderGraphQL.orders("not-a-uuid", null));
        }

        @Test
        void orders_WithStatusFilter_ReturnsFilteredOrders() {
            CalendarOrder paidOrder = new CalendarOrder();
            paidOrder.id = UUID.randomUUID();
            paidOrder.status = CalendarOrder.STATUS_PAID;

            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(orderService.getUserOrders(testUser.id)).thenReturn(List.of(testOrder, paidOrder));

            List<CalendarOrder> result = orderGraphQL.orders(null, "PAID");

            assertEquals(1, result.size());
            assertEquals(CalendarOrder.STATUS_PAID, result.get(0).status);
        }
    }

    // ============================================================================
    // ORDER QUERY (BY ID) TESTS
    // ============================================================================

    @Nested
    class OrderByIdTests {

        @Test
        void order_OwnerAccessingOwnOrder_ReturnsOrder() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(orderService.getOrderById(testOrder.id)).thenReturn(Optional.of(testOrder));
            // jwt.getGroups() not called - owner check passes before admin check

            CalendarOrder result = orderGraphQL.order(testOrder.id.toString());

            assertNotNull(result);
            assertEquals(testOrder.id, result.id);
        }

        @Test
        void order_AdminAccessingAnyOrder_ReturnsOrder() {
            testOrder.user = adminUser; // Different user
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(orderService.getOrderById(testOrder.id)).thenReturn(Optional.of(testOrder));
            when(jwt.getGroups()).thenReturn(Set.of(Roles.ADMIN));

            CalendarOrder result = orderGraphQL.order(testOrder.id.toString());

            assertNotNull(result);
        }

        @Test
        void order_NonOwnerNonAdmin_ThrowsSecurityException() {
            CalendarUser otherUser = new CalendarUser();
            otherUser.id = UUID.randomUUID();
            testOrder.user = otherUser;
            String orderId = testOrder.id.toString();

            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(orderService.getOrderById(testOrder.id)).thenReturn(Optional.of(testOrder));
            when(jwt.getGroups()).thenReturn(Set.of(Roles.USER));

            assertThrows(SecurityException.class, () -> orderGraphQL.order(orderId));
        }

        @Test
        void order_OrderNotFound_ReturnsNull() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(orderService.getOrderById(any())).thenReturn(Optional.empty());

            CalendarOrder result = orderGraphQL.order(UUID.randomUUID().toString());

            assertNull(result);
        }

        @Test
        void order_InvalidUuidFormat_ThrowsIllegalArgumentException() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);

            assertThrows(IllegalArgumentException.class, () -> orderGraphQL.order("not-a-uuid"));
        }

        @Test
        void order_OrderWithNullUser_ChecksAdmin() {
            testOrder.user = null;
            String orderId = testOrder.id.toString();
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(orderService.getOrderById(testOrder.id)).thenReturn(Optional.of(testOrder));
            when(jwt.getGroups()).thenReturn(Set.of(Roles.USER));

            assertThrows(SecurityException.class, () -> orderGraphQL.order(orderId));
        }

        @Test
        void order_NullGroups_TreatedAsNonAdmin() {
            CalendarUser otherUser = new CalendarUser();
            otherUser.id = UUID.randomUUID();
            testOrder.user = otherUser;
            String orderId = testOrder.id.toString();

            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(orderService.getOrderById(testOrder.id)).thenReturn(Optional.of(testOrder));
            when(jwt.getGroups()).thenReturn(null);

            assertThrows(SecurityException.class, () -> orderGraphQL.order(orderId));
        }
    }

    // ============================================================================
    // ORDERBYSTRIPEESSIONID QUERY TESTS
    // ============================================================================

    @Nested
    class OrderByStripeSessionIdTests {

        @Test
        void orderByStripeSessionId_ValidSession_ReturnsOrder() throws Exception {
            Session mockSession = mock(Session.class);
            Map<String, String> metadata = new HashMap<>();
            metadata.put("orderId", testOrder.id.toString());

            when(paymentService.getCheckoutSession("cs_test")).thenReturn(mockSession);
            when(mockSession.getMetadata()).thenReturn(metadata);
            when(orderService.getOrderById(testOrder.id)).thenReturn(Optional.of(testOrder));

            CalendarOrder result = orderGraphQL.orderByStripeSessionId("cs_test");

            assertNotNull(result);
            assertEquals(testOrder.id, result.id);
        }

        @Test
        void orderByStripeSessionId_SessionNotFound_ReturnsNull() throws Exception {
            when(paymentService.getCheckoutSession("cs_test")).thenReturn(null);

            CalendarOrder result = orderGraphQL.orderByStripeSessionId("cs_test");

            assertNull(result);
        }

        @Test
        void orderByStripeSessionId_NoOrderIdInMetadata_ReturnsNull() throws Exception {
            Session mockSession = mock(Session.class);
            when(paymentService.getCheckoutSession("cs_test")).thenReturn(mockSession);
            when(mockSession.getMetadata()).thenReturn(new HashMap<>());

            CalendarOrder result = orderGraphQL.orderByStripeSessionId("cs_test");

            assertNull(result);
        }

        @Test
        void orderByStripeSessionId_EmptyOrderIdInMetadata_ReturnsNull() throws Exception {
            Session mockSession = mock(Session.class);
            Map<String, String> metadata = new HashMap<>();
            metadata.put("orderId", "");

            when(paymentService.getCheckoutSession("cs_test")).thenReturn(mockSession);
            when(mockSession.getMetadata()).thenReturn(metadata);

            CalendarOrder result = orderGraphQL.orderByStripeSessionId("cs_test");

            assertNull(result);
        }

        @Test
        void orderByStripeSessionId_InvalidOrderIdFormat_ReturnsNull() throws Exception {
            Session mockSession = mock(Session.class);
            Map<String, String> metadata = new HashMap<>();
            metadata.put("orderId", "not-a-uuid");

            when(paymentService.getCheckoutSession("cs_test")).thenReturn(mockSession);
            when(mockSession.getMetadata()).thenReturn(metadata);

            CalendarOrder result = orderGraphQL.orderByStripeSessionId("cs_test");

            assertNull(result);
        }

        @Test
        void orderByStripeSessionId_OrderNotFound_ReturnsNull() throws Exception {
            Session mockSession = mock(Session.class);
            Map<String, String> metadata = new HashMap<>();
            metadata.put("orderId", UUID.randomUUID().toString());

            when(paymentService.getCheckoutSession("cs_test")).thenReturn(mockSession);
            when(mockSession.getMetadata()).thenReturn(metadata);
            when(orderService.getOrderById(any())).thenReturn(Optional.empty());

            CalendarOrder result = orderGraphQL.orderByStripeSessionId("cs_test");

            assertNull(result);
        }

        @Test
        void orderByStripeSessionId_StripeException_ReturnsNull() throws Exception {
            when(paymentService.getCheckoutSession("cs_test")).thenThrow(mock(StripeException.class));

            CalendarOrder result = orderGraphQL.orderByStripeSessionId("cs_test");

            assertNull(result);
        }
    }

    // ============================================================================
    // ALLORDERS QUERY TESTS
    // ============================================================================

    @Nested
    class AllOrdersTests {

        @Test
        void allOrders_WithStatusFilter_ReturnsFilteredOrders() {
            when(orderService.getOrdersByStatus("pending")).thenReturn(List.of(testOrder));

            List<CalendarOrder> result = orderGraphQL.allOrders("pending", null);

            assertEquals(1, result.size());
            verify(orderService).getOrdersByStatus("pending");
        }

        @Test
        void allOrders_WithLimit_ReturnsLimitedResults() {
            when(orderService.getOrdersByStatus("pending")).thenReturn(List.of(testOrder));

            List<CalendarOrder> result = orderGraphQL.allOrders("pending", 10);

            assertEquals(1, result.size());
        }

        @Test
        void allOrders_WithLimitExceedingResults_AppliesLimit() {
            List<CalendarOrder> manyOrders = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                CalendarOrder order = new CalendarOrder();
                order.id = UUID.randomUUID();
                order.status = CalendarOrder.STATUS_PENDING;
                manyOrders.add(order);
            }
            when(orderService.getOrdersByStatus("pending")).thenReturn(manyOrders);

            List<CalendarOrder> result = orderGraphQL.allOrders("pending", 5);

            assertEquals(5, result.size());
        }

        @Test
        void allOrders_ZeroLimit_UsesDefault() {
            when(orderService.getOrdersByStatus("pending")).thenReturn(List.of(testOrder));

            List<CalendarOrder> result = orderGraphQL.allOrders("pending", 0);

            assertEquals(1, result.size());
        }

        @Test
        void allOrders_NegativeLimit_UsesDefault() {
            when(orderService.getOrdersByStatus("pending")).thenReturn(List.of(testOrder));

            List<CalendarOrder> result = orderGraphQL.allOrders("pending", -1);

            assertEquals(1, result.size());
        }
    }

    // ============================================================================
    // CREATEORDER MUTATION TESTS
    // ============================================================================

    @Nested
    class CreateOrderMutationTests {

        @Test
        void createOrder_InvalidCalendarIdFormat_ThrowsIllegalArgumentException() {
            OrderInput input = new OrderInput();
            input.calendarId = "not-a-uuid";
            input.quantity = 1;
            input.shippingAddress = createShippingAddress();

            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);

            assertThrows(IllegalArgumentException.class, () -> orderGraphQL.createOrder(input));
        }

        private AddressInput createShippingAddress() {
            AddressInput address = new AddressInput();
            address.street = "123 Test St";
            address.city = "Nashville";
            address.state = "TN";
            address.postalCode = "37201";
            address.country = "US";
            return address;
        }
    }

    // ============================================================================
    // PLACEORDER MUTATION TESTS
    // ============================================================================

    @Nested
    class PlaceOrderMutationTests {

        @Test
        void placeOrder_InvalidCalendarIdFormat_ThrowsIllegalArgumentException() {
            PlaceOrderInput input = new PlaceOrderInput();
            input.calendarId = "not-a-uuid";
            input.productType = ProductType.PRINT;
            input.quantity = 1;
            input.shippingAddress = createShippingAddress();

            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);

            assertThrows(IllegalArgumentException.class, () -> orderGraphQL.placeOrder(input));
        }

        private AddressInput createShippingAddress() {
            AddressInput address = new AddressInput();
            address.street = "123 Test St";
            address.city = "Nashville";
            address.state = "TN";
            address.postalCode = "37201";
            address.country = "US";
            return address;
        }
    }

    // ============================================================================
    // UPDATEORDERSTATUS MUTATION TESTS
    // ============================================================================

    @Nested
    class UpdateOrderStatusTests {

        @Test
        void updateOrderStatus_ValidInput_UpdatesStatus() {
            OrderStatusUpdateInput input = new OrderStatusUpdateInput();
            input.orderId = testOrder.id.toString();
            input.status = "processing";
            input.notes = "Started processing";

            when(orderService.updateOrderStatus(testOrder.id, "processing", "Started processing"))
                    .thenReturn(testOrder);

            CalendarOrder result = orderGraphQL.updateOrderStatus(input);

            assertNotNull(result);
            verify(orderService).updateOrderStatus(testOrder.id, "processing", "Started processing");
        }

        @Test
        void updateOrderStatus_InvalidOrderIdFormat_ThrowsIllegalArgumentException() {
            OrderStatusUpdateInput input = new OrderStatusUpdateInput();
            input.orderId = "not-a-uuid";
            input.status = "processing";

            assertThrows(IllegalArgumentException.class, () -> orderGraphQL.updateOrderStatus(input));
        }
    }

    // ============================================================================
    // CANCELORDER MUTATION TESTS
    // ============================================================================

    @Nested
    class CancelOrderTests {

        @Test
        void cancelOrder_PendingOrder_CancelsSuccessfully() {
            testOrder.stripePaymentIntentId = null;
            testOrder.paidAt = null;

            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(jwt.getGroups()).thenReturn(Set.of(Roles.USER));
            when(orderService.cancelOrder(testOrder.id, testUser.id, false, "Test reason")).thenReturn(testOrder);

            CalendarOrder result = orderGraphQL.cancelOrder(testOrder.id.toString(), "Test reason");

            assertNotNull(result);
            verify(orderService).cancelOrder(testOrder.id, testUser.id, false, "Test reason");
        }

        @Test
        void cancelOrder_PaidOrder_CancelsAndRefunds() throws Exception {
            testOrder.stripePaymentIntentId = "pi_test";
            testOrder.paidAt = Instant.now();
            testOrder.notes = "";

            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(jwt.getGroups()).thenReturn(Set.of(Roles.USER));
            when(orderService.cancelOrder(testOrder.id, testUser.id, false, "Test reason")).thenReturn(testOrder);

            CalendarOrder result = orderGraphQL.cancelOrder(testOrder.id.toString(), "Test reason");

            assertNotNull(result);
            verify(paymentService).processRefund("pi_test", null, "Test reason");
        }

        @Test
        void cancelOrder_PaidOrderWithNullReason_UsesDefaultReason() throws Exception {
            testOrder.stripePaymentIntentId = "pi_test";
            testOrder.paidAt = Instant.now();
            testOrder.notes = "";

            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(jwt.getGroups()).thenReturn(Set.of(Roles.USER));
            when(orderService.cancelOrder(testOrder.id, testUser.id, false, null)).thenReturn(testOrder);

            CalendarOrder result = orderGraphQL.cancelOrder(testOrder.id.toString(), null);

            assertNotNull(result);
            verify(paymentService).processRefund("pi_test", null, "requested_by_customer");
        }

        @Test
        void cancelOrder_AdminCancellingAnyOrder_PassesIsAdminFlag() {
            when(authService.requireCurrentUser(jwt)).thenReturn(adminUser);
            when(jwt.getGroups()).thenReturn(Set.of(Roles.ADMIN));
            when(orderService.cancelOrder(testOrder.id, adminUser.id, true, "Admin cancel")).thenReturn(testOrder);

            CalendarOrder result = orderGraphQL.cancelOrder(testOrder.id.toString(), "Admin cancel");

            assertNotNull(result);
            verify(orderService).cancelOrder(testOrder.id, adminUser.id, true, "Admin cancel");
        }

        @Test
        void cancelOrder_NullGroups_TreatedAsNonAdmin() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(jwt.getGroups()).thenReturn(null);
            when(orderService.cancelOrder(testOrder.id, testUser.id, false, "Test reason")).thenReturn(testOrder);

            CalendarOrder result = orderGraphQL.cancelOrder(testOrder.id.toString(), "Test reason");

            assertNotNull(result);
            verify(orderService).cancelOrder(testOrder.id, testUser.id, false, "Test reason");
        }

        @Test
        void cancelOrder_InvalidOrderIdFormat_ThrowsIllegalArgumentException() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);

            assertThrows(IllegalArgumentException.class, () -> orderGraphQL.cancelOrder("not-a-uuid", "Test reason"));
        }

        @Test
        void cancelOrder_PaidOrderWithPaymentIntentButNullPaidAt_SkipsRefund() throws Exception {
            testOrder.stripePaymentIntentId = "pi_test";
            testOrder.paidAt = null; // Not actually paid yet

            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(jwt.getGroups()).thenReturn(Set.of(Roles.USER));
            when(orderService.cancelOrder(testOrder.id, testUser.id, false, "Test")).thenReturn(testOrder);

            CalendarOrder result = orderGraphQL.cancelOrder(testOrder.id.toString(), "Test");

            assertNotNull(result);
            // Should not attempt refund when paidAt is null
            verify(paymentService, never()).processRefund(anyString(), any(), anyString());
        }

        @Test
        void cancelOrder_PaidOrderWithNullPaymentIntent_SkipsRefund() throws Exception {
            testOrder.stripePaymentIntentId = null;
            testOrder.paidAt = Instant.now(); // Paid but no payment intent (edge case)

            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(jwt.getGroups()).thenReturn(Set.of(Roles.USER));
            when(orderService.cancelOrder(testOrder.id, testUser.id, false, "Test")).thenReturn(testOrder);

            CalendarOrder result = orderGraphQL.cancelOrder(testOrder.id.toString(), "Test");

            assertNotNull(result);
            // Should not attempt refund when paymentIntentId is null
            verify(paymentService, never()).processRefund(anyString(), any(), anyString());
        }
    }

    // ============================================================================
    // CREATECHECKOUTSESSION MUTATION TESTS
    // ============================================================================

    @Nested
    class CreateCheckoutSessionTests {

        @Test
        void createCheckoutSession_NoItems_ThrowsIllegalArgumentException() {
            OrderGraphQL.CheckoutSessionInput input = new OrderGraphQL.CheckoutSessionInput();
            input.items = null;

            assertThrows(IllegalArgumentException.class, () -> orderGraphQL.createCheckoutSession(input));
        }

        @Test
        void createCheckoutSession_EmptyItems_ThrowsIllegalArgumentException() {
            OrderGraphQL.CheckoutSessionInput input = new OrderGraphQL.CheckoutSessionInput();
            input.items = new ArrayList<>();

            assertThrows(IllegalArgumentException.class, () -> orderGraphQL.createCheckoutSession(input));
        }
    }

    // ============================================================================
    // RESPONSE TYPE TESTS
    // ============================================================================

    @Nested
    class ResponseTypeTests {

        @Test
        void createOrderResponse_FieldsAssignable() {
            OrderGraphQL.CreateOrderResponse response = new OrderGraphQL.CreateOrderResponse();
            response.order = testOrder;
            response.clientSecret = "cs_test";

            assertEquals(testOrder, response.order);
            assertEquals("cs_test", response.clientSecret);
        }

        @Test
        void checkoutSessionResponse_FieldsAssignable() {
            OrderGraphQL.CheckoutSessionResponse response = new OrderGraphQL.CheckoutSessionResponse();
            response.clientSecret = "cs_test";
            response.sessionId = "ses_test";
            response.orderId = testOrder.id.toString();
            response.orderNumber = "VC-TEST-1234";

            assertEquals("cs_test", response.clientSecret);
            assertEquals("ses_test", response.sessionId);
            assertEquals(testOrder.id.toString(), response.orderId);
            assertEquals("VC-TEST-1234", response.orderNumber);
        }
    }

    // ============================================================================
    // INPUT TYPE TESTS
    // ============================================================================

    @Nested
    class InputTypeTests {

        @Test
        void checkoutSessionInput_FieldsAssignable() {
            OrderGraphQL.CheckoutSessionInput input = new OrderGraphQL.CheckoutSessionInput();
            input.cartId = "cart-123";
            input.customerEmail = "test@example.com";
            input.items = new ArrayList<>();
            input.returnUrl = "https://example.com";

            assertEquals("cart-123", input.cartId);
            assertEquals("test@example.com", input.customerEmail);
            assertNotNull(input.items);
            assertEquals("https://example.com", input.returnUrl);
        }

        @Test
        void checkoutItemInput_FieldsAssignable() {
            OrderGraphQL.CheckoutItemInput item = new OrderGraphQL.CheckoutItemInput();
            item.name = "Test Item";
            item.description = "A test item";
            item.quantity = 2;
            item.unitPrice = 25.00;
            item.year = 2025;
            item.productCode = "print";
            item.templateId = "tmpl-123";
            item.configuration = "{\"theme\":\"dark\"}";

            assertEquals("Test Item", item.name);
            assertEquals("A test item", item.description);
            assertEquals(2, item.quantity);
            assertEquals(25.00, item.unitPrice);
            assertEquals(2025, item.year);
            assertEquals("print", item.productCode);
            assertEquals("tmpl-123", item.templateId);
            assertEquals("{\"theme\":\"dark\"}", item.configuration);
        }
    }
}
