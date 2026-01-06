package villagecompute.calendar.api.graphql;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import villagecompute.calendar.api.graphql.inputs.AddressInput;
import villagecompute.calendar.api.graphql.inputs.PlaceOrderInput;
import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;
import villagecompute.calendar.data.models.enums.ProductType;
import villagecompute.calendar.integration.stripe.StripeService;
import villagecompute.calendar.services.AuthenticationService;
import villagecompute.calendar.services.OrderService;

/**
 * Unit tests for OrderResolver. Tests all query and mutation methods with mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
class OrderResolverUnitTest {

    @InjectMocks
    OrderResolver orderResolver;

    @Mock
    JsonWebToken jwt;

    @Mock
    AuthenticationService authService;

    @Mock
    OrderService orderService;

    @Mock
    StripeService stripeService;

    @Mock
    ObjectMapper objectMapper;

    private CalendarUser testUser;
    private CalendarUser testAdmin;
    private CalendarUser otherUser;
    private CalendarOrder testOrder;
    private CalendarOrder otherUserOrder;
    private UserCalendar testCalendar;

    @BeforeEach
    void setUp() {
        // Create test user
        testUser = new CalendarUser();
        testUser.id = UUID.randomUUID();
        testUser.email = "testuser@example.com";
        testUser.displayName = "Test User";
        testUser.isAdmin = false;

        // Create admin user
        testAdmin = new CalendarUser();
        testAdmin.id = UUID.randomUUID();
        testAdmin.email = "admin@example.com";
        testAdmin.displayName = "Admin User";
        testAdmin.isAdmin = true;

        // Create other user
        otherUser = new CalendarUser();
        otherUser.id = UUID.randomUUID();
        otherUser.email = "other@example.com";
        otherUser.displayName = "Other User";
        otherUser.isAdmin = false;

        // Create test calendar
        testCalendar = new UserCalendar();
        testCalendar.id = UUID.randomUUID();
        testCalendar.user = testUser;
        testCalendar.name = "Test Calendar";
        testCalendar.year = 2025;

        // Create test order for testUser
        testOrder = new CalendarOrder();
        testOrder.id = UUID.randomUUID();
        testOrder.user = testUser;
        testOrder.status = CalendarOrder.STATUS_PENDING;
        testOrder.totalPrice = new BigDecimal("59.98");
        testOrder.orderNumber = "ORD-001";

        // Create order for other user
        otherUserOrder = new CalendarOrder();
        otherUserOrder.id = UUID.randomUUID();
        otherUserOrder.user = otherUser;
        otherUserOrder.status = CalendarOrder.STATUS_PENDING;
        otherUserOrder.totalPrice = new BigDecimal("29.99");
        otherUserOrder.orderNumber = "ORD-002";
    }

    // ==================================================================
    // ORDER QUERY TESTS
    // ==================================================================

    @Nested
    class OrderQueryTests {

        @Test
        void order_UserOwnsOrder_ReturnsOrder() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(orderService.getOrderById(testOrder.id)).thenReturn(Optional.of(testOrder));

            CalendarOrder result = orderResolver.order(testOrder.id);

            assertNotNull(result);
            assertEquals(testOrder.id, result.id);
            verify(authService).requireCurrentUser(jwt);
            verify(orderService).getOrderById(testOrder.id);
        }

        @Test
        void order_AdminAccessesAnyOrder_ReturnsOrder() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testAdmin);
            when(orderService.getOrderById(otherUserOrder.id)).thenReturn(Optional.of(otherUserOrder));

            CalendarOrder result = orderResolver.order(otherUserOrder.id);

            assertNotNull(result);
            assertEquals(otherUserOrder.id, result.id);
        }

        @Test
        void order_UserAccessesOtherUserOrder_ThrowsSecurityException() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(orderService.getOrderById(otherUserOrder.id)).thenReturn(Optional.of(otherUserOrder));

            assertThrows(SecurityException.class, () -> orderResolver.order(otherUserOrder.id));
        }

        @Test
        void order_OrderNotFound_ThrowsIllegalArgumentException() {
            UUID nonExistentId = UUID.randomUUID();
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(orderService.getOrderById(nonExistentId)).thenReturn(Optional.empty());

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> orderResolver.order(nonExistentId));
            assertEquals("Order not found", exception.getMessage());
        }
    }

    // ==================================================================
    // ORDERS QUERY TESTS
    // ==================================================================

    @Nested
    class OrdersQueryTests {

        @Test
        void orders_NoUserIdProvided_ReturnsCurrentUserOrders() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(orderService.getUserOrders(testUser.id)).thenReturn(List.of(testOrder));

            List<CalendarOrder> result = orderResolver.orders(null, null);

            assertEquals(1, result.size());
            assertEquals(testOrder.id, result.get(0).id);
            verify(orderService).getUserOrders(testUser.id);
        }

        @Test
        void orders_OwnUserIdProvided_ReturnsOwnOrders() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(orderService.getUserOrders(testUser.id)).thenReturn(List.of(testOrder));

            List<CalendarOrder> result = orderResolver.orders(testUser.id, null);

            assertEquals(1, result.size());
            verify(orderService).getUserOrders(testUser.id);
        }

        @Test
        void orders_AdminQueriesOtherUser_ReturnsOtherUserOrders() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testAdmin);
            when(orderService.getUserOrders(otherUser.id)).thenReturn(List.of(otherUserOrder));

            List<CalendarOrder> result = orderResolver.orders(otherUser.id, null);

            assertEquals(1, result.size());
            assertEquals(otherUserOrder.id, result.get(0).id);
        }

        @Test
        void orders_NonAdminQueriesOtherUser_ThrowsSecurityException() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);

            assertThrows(SecurityException.class, () -> orderResolver.orders(otherUser.id, null));
        }

        @Test
        void orders_WithStatusFilter_ReturnsFilteredOrders() {
            CalendarOrder paidOrder = new CalendarOrder();
            paidOrder.id = UUID.randomUUID();
            paidOrder.user = testUser;
            paidOrder.status = CalendarOrder.STATUS_PAID;

            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(orderService.getUserOrders(testUser.id)).thenReturn(List.of(testOrder, paidOrder));

            List<CalendarOrder> result = orderResolver.orders(null, CalendarOrder.STATUS_PAID);

            assertEquals(1, result.size());
            assertEquals(CalendarOrder.STATUS_PAID, result.get(0).status);
        }

        @Test
        void orders_EmptyStatusFilter_ReturnsAllOrders() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(orderService.getUserOrders(testUser.id)).thenReturn(List.of(testOrder));

            List<CalendarOrder> result = orderResolver.orders(null, "   ");

            assertEquals(1, result.size());
        }

        @Test
        void orders_NoOrdersFound_ReturnsEmptyList() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(orderService.getUserOrders(testUser.id)).thenReturn(Collections.emptyList());

            List<CalendarOrder> result = orderResolver.orders(null, null);

            assertTrue(result.isEmpty());
        }
    }

    // ==================================================================
    // MY ORDERS QUERY TESTS
    // ==================================================================

    @Nested
    class MyOrdersQueryTests {

        @Test
        void myOrders_ReturnsCurrentUserOrders() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(orderService.getUserOrders(testUser.id)).thenReturn(List.of(testOrder));

            List<CalendarOrder> result = orderResolver.myOrders(null);

            assertEquals(1, result.size());
            assertEquals(testOrder.id, result.get(0).id);
            verify(orderService).getUserOrders(testUser.id);
        }

        @Test
        void myOrders_WithStatusFilter_ReturnsFilteredOrders() {
            CalendarOrder paidOrder = new CalendarOrder();
            paidOrder.id = UUID.randomUUID();
            paidOrder.user = testUser;
            paidOrder.status = CalendarOrder.STATUS_PAID;

            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(orderService.getUserOrders(testUser.id)).thenReturn(List.of(testOrder, paidOrder));

            List<CalendarOrder> result = orderResolver.myOrders(CalendarOrder.STATUS_PENDING);

            assertEquals(1, result.size());
            assertEquals(CalendarOrder.STATUS_PENDING, result.get(0).status);
        }

        @Test
        void myOrders_NoOrders_ReturnsEmptyList() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(orderService.getUserOrders(testUser.id)).thenReturn(Collections.emptyList());

            List<CalendarOrder> result = orderResolver.myOrders(null);

            assertTrue(result.isEmpty());
        }

        @Test
        void myOrders_EmptyStatusFilter_ReturnsAllOrders() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(orderService.getUserOrders(testUser.id)).thenReturn(List.of(testOrder));

            List<CalendarOrder> result = orderResolver.myOrders("");

            assertEquals(1, result.size());
        }
    }

    // ==================================================================
    // ALL ORDERS QUERY TESTS
    // ==================================================================

    @Nested
    class AllOrdersQueryTests {

        @Test
        void allOrders_WithStatusFilter_ReturnsFilteredOrders() {
            CalendarOrder paidOrder = new CalendarOrder();
            paidOrder.id = UUID.randomUUID();
            paidOrder.status = CalendarOrder.STATUS_PAID;

            when(orderService.getOrdersByStatus(CalendarOrder.STATUS_PAID)).thenReturn(List.of(paidOrder));

            List<CalendarOrder> result = orderResolver.allOrders(CalendarOrder.STATUS_PAID, null);

            assertEquals(1, result.size());
            assertEquals(CalendarOrder.STATUS_PAID, result.get(0).status);
            verify(orderService).getOrdersByStatus(CalendarOrder.STATUS_PAID);
        }

        // Note: allOrders with null/empty status calls CalendarOrder.listAll() which is a
        // static Panache method that requires integration test context. See OrderResolverTest
        // for integration tests covering this scenario.

        @Test
        void allOrders_WithLimit_AppliesLimit() {
            List<CalendarOrder> manyOrders = List.of(testOrder, otherUserOrder, testOrder, otherUserOrder);
            when(orderService.getOrdersByStatus(CalendarOrder.STATUS_PENDING)).thenReturn(manyOrders);

            List<CalendarOrder> result = orderResolver.allOrders(CalendarOrder.STATUS_PENDING, 2);

            assertEquals(2, result.size());
        }

        @Test
        void allOrders_NullLimit_UsesDefaultLimit() {
            when(orderService.getOrdersByStatus(CalendarOrder.STATUS_PENDING)).thenReturn(List.of(testOrder));

            List<CalendarOrder> result = orderResolver.allOrders(CalendarOrder.STATUS_PENDING, null);

            // Default limit is 50, we have fewer items
            assertEquals(1, result.size());
        }

        @Test
        void allOrders_ZeroLimit_UsesDefaultLimit() {
            when(orderService.getOrdersByStatus(CalendarOrder.STATUS_PENDING)).thenReturn(List.of(testOrder));

            List<CalendarOrder> result = orderResolver.allOrders(CalendarOrder.STATUS_PENDING, 0);

            // Zero or negative limit defaults to 50
            assertEquals(1, result.size());
        }

        @Test
        void allOrders_NegativeLimit_UsesDefaultLimit() {
            when(orderService.getOrdersByStatus(CalendarOrder.STATUS_PENDING)).thenReturn(List.of(testOrder));

            List<CalendarOrder> result = orderResolver.allOrders(CalendarOrder.STATUS_PENDING, -5);

            assertEquals(1, result.size());
        }

        @Test
        void allOrders_LimitLargerThanResults_ReturnsAllResults() {
            when(orderService.getOrdersByStatus(CalendarOrder.STATUS_PENDING)).thenReturn(List.of(testOrder));

            List<CalendarOrder> result = orderResolver.allOrders(CalendarOrder.STATUS_PENDING, 100);

            assertEquals(1, result.size());
        }
    }

    // ==================================================================
    // PLACE ORDER MUTATION TESTS
    // ==================================================================

    @Nested
    class PlaceOrderMutationTests {

        private PlaceOrderInput createValidInput() {
            PlaceOrderInput input = new PlaceOrderInput();
            input.calendarId = testCalendar.id.toString();
            input.productType = ProductType.PRINT;
            input.quantity = 2;
            input.shippingAddress = new AddressInput();
            input.shippingAddress.street = "123 Test St";
            input.shippingAddress.city = "Nashville";
            input.shippingAddress.state = "TN";
            input.shippingAddress.postalCode = "37201";
            input.shippingAddress.country = "US";
            return input;
        }

        // Note: placeOrder calls UserCalendar.findByIdOptional() which is a static Panache
        // method. Quantity validation occurs after the calendar lookup, so testing invalid
        // quantity requires integration test context. See OrderResolverTest for full tests.

        @Test
        void placeOrder_InvalidCalendarIdFormat_ThrowsException() {
            PlaceOrderInput input = createValidInput();
            input.calendarId = "not-a-valid-uuid";

            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);

            // UuidUtil.parse will throw IllegalArgumentException for invalid UUID
            assertThrows(IllegalArgumentException.class, () -> orderResolver.placeOrder(input));
        }
    }

    // ==================================================================
    // CANCEL ORDER MUTATION TESTS
    // ==================================================================

    @Nested
    class CancelOrderMutationTests {

        @Test
        void cancelOrder_WithReason_CancelsOrder() {
            CalendarOrder cancelledOrder = new CalendarOrder();
            cancelledOrder.id = testOrder.id;
            cancelledOrder.status = CalendarOrder.STATUS_CANCELLED;

            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(orderService.cancelOrder(testOrder.id, testUser.id, false, "Customer request"))
                    .thenReturn(cancelledOrder);

            CalendarOrder result = orderResolver.cancelOrder(testOrder.id, "Customer request");

            assertNotNull(result);
            assertEquals(CalendarOrder.STATUS_CANCELLED, result.status);
            verify(orderService).cancelOrder(testOrder.id, testUser.id, false, "Customer request");
        }

        @Test
        void cancelOrder_NullReason_UsesDefaultReason() {
            CalendarOrder cancelledOrder = new CalendarOrder();
            cancelledOrder.id = testOrder.id;
            cancelledOrder.status = CalendarOrder.STATUS_CANCELLED;

            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(orderService.cancelOrder(testOrder.id, testUser.id, false, "No reason provided"))
                    .thenReturn(cancelledOrder);

            CalendarOrder result = orderResolver.cancelOrder(testOrder.id, null);

            assertNotNull(result);
            verify(orderService).cancelOrder(testOrder.id, testUser.id, false, "No reason provided");
        }

        @Test
        void cancelOrder_AdminCancelsAnyOrder_Succeeds() {
            CalendarOrder cancelledOrder = new CalendarOrder();
            cancelledOrder.id = otherUserOrder.id;
            cancelledOrder.status = CalendarOrder.STATUS_CANCELLED;

            when(authService.requireCurrentUser(jwt)).thenReturn(testAdmin);
            when(orderService.cancelOrder(otherUserOrder.id, testAdmin.id, true, "Admin cancellation"))
                    .thenReturn(cancelledOrder);

            CalendarOrder result = orderResolver.cancelOrder(otherUserOrder.id, "Admin cancellation");

            assertNotNull(result);
            verify(orderService).cancelOrder(otherUserOrder.id, testAdmin.id, true, "Admin cancellation");
        }

        @Test
        void cancelOrder_ServiceThrowsException_PropagatesException() {
            when(authService.requireCurrentUser(jwt)).thenReturn(testUser);
            when(orderService.cancelOrder(any(), any(), anyBoolean(), anyString()))
                    .thenThrow(new IllegalStateException("Cannot cancel shipped order"));

            assertThrows(IllegalStateException.class,
                    () -> orderResolver.cancelOrder(testOrder.id, "Test"));
        }
    }

    // ==================================================================
    // BATCH LOAD USERS TESTS
    // ==================================================================

    @Nested
    class BatchLoadUsersTests {

        // Note: batchLoadUsers with orders calls CalendarUser.list() which is a static
        // Panache method that requires integration test context. See OrderResolverTest
        // for integration tests covering this scenario.

        @Test
        void batchLoadUsers_EmptyList_ReturnsEmptyList() {
            List<CalendarOrder> orders = Collections.emptyList();

            List<CalendarUser> result = orderResolver.batchLoadUsers(orders);

            assertTrue(result.isEmpty());
        }

        @Test
        void batchLoadUsers_OrdersWithNullUsers_HandlesGracefully() {
            CalendarOrder orderWithNullUser = new CalendarOrder();
            orderWithNullUser.id = UUID.randomUUID();
            orderWithNullUser.user = null;

            List<CalendarOrder> orders = List.of(orderWithNullUser);

            List<CalendarUser> result = orderResolver.batchLoadUsers(orders);

            // Should return list with null elements for orders without users
            assertEquals(1, result.size());
            assertNull(result.get(0));
        }
    }
}
