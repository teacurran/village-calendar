package villagecompute.calendar.data.repositories;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.models.UserCalendar;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class CalendarOrderRepositoryTest {

    @Inject TestDataCleaner testDataCleaner;

    @Inject CalendarOrderRepository orderRepository;

    @Inject CalendarUserRepository userRepository;

    @Inject UserCalendarRepository calendarRepository;

    @Inject CalendarTemplateRepository templateRepository;

    @Inject ObjectMapper objectMapper;

    @Inject jakarta.persistence.EntityManager entityManager;

    private CalendarUser testUser;
    private UserCalendar testCalendar;

    @BeforeEach
    @Transactional
    void setUp() {
        testDataCleaner.deleteAll();

        // Create test user
        testUser = new CalendarUser();
        testUser.oauthProvider = "GOOGLE";
        testUser.oauthSubject = "test-subject-order-repo-test";
        testUser.email = "test@example.com";
        testUser.displayName = "Test User";
        userRepository.persist(testUser);

        // Create test template
        CalendarTemplate template = new CalendarTemplate();
        template.name = "Test Template";
        template.isActive = true;
        template.displayOrder = 1;
        ObjectNode config = objectMapper.createObjectNode();
        config.put("theme", "test");
        template.configuration = config;
        templateRepository.persist(template);

        // Create test calendar
        testCalendar = new UserCalendar();
        testCalendar.user = testUser;
        testCalendar.name = "Test Calendar";
        testCalendar.year = 2025;
        testCalendar.isPublic = false;
        testCalendar.template = template;
        calendarRepository.persist(testCalendar);
    }

    @Test
    @Transactional
    void testFindByStatusOrderByCreatedDesc() {
        // Given
        orderRepository.persist(createOrder(CalendarOrder.STATUS_PENDING, "pi_pending"));
        orderRepository.persist(createOrder(CalendarOrder.STATUS_PAID, "pi_paid"));
        orderRepository.persist(createOrder(CalendarOrder.STATUS_PENDING, "pi_pending2"));
        entityManager.flush();

        // When
        List<CalendarOrder> pendingOrders =
                orderRepository.findByStatusOrderByCreatedDesc(CalendarOrder.STATUS_PENDING);

        // Then
        assertEquals(2, pendingOrders.size());
        assertTrue(
                pendingOrders.stream()
                        .allMatch(o -> CalendarOrder.STATUS_PENDING.equals(o.status)));
    }

    @Test
    @Transactional
    void testFindByUser() {
        // Given
        orderRepository.persist(createOrder(CalendarOrder.STATUS_PENDING, "pi_1"));
        orderRepository.persist(createOrder(CalendarOrder.STATUS_PAID, "pi_2"));
        entityManager.flush();

        // When
        List<CalendarOrder> orders = orderRepository.findByUser(testUser.id);

        // Then
        assertEquals(2, orders.size());
        assertTrue(orders.stream().allMatch(o -> o.user.id.equals(testUser.id)));
    }

    @Test
    @Transactional
    void testFindByCalendar() {
        // Given
        orderRepository.persist(createOrder(CalendarOrder.STATUS_PENDING, "pi_1"));
        orderRepository.persist(createOrder(CalendarOrder.STATUS_PAID, "pi_2"));
        entityManager.flush();

        // When
        List<CalendarOrder> orders = orderRepository.findByCalendar(testCalendar.id);

        // Then
        assertEquals(2, orders.size());
        assertTrue(orders.stream().allMatch(o -> o.calendar.id.equals(testCalendar.id)));
    }

    @Test
    @Transactional
    void testFindByStripePaymentIntent() {
        // Given
        CalendarOrder order = createOrder(CalendarOrder.STATUS_PAID, "pi_unique_123");
        orderRepository.persist(order);
        entityManager.flush();

        // When
        Optional<CalendarOrder> found = orderRepository.findByStripePaymentIntent("pi_unique_123");

        // Then
        assertTrue(found.isPresent());
        assertEquals("pi_unique_123", found.get().stripePaymentIntentId);
    }

    @Test
    @Transactional
    void testFindRecentOrders() {
        // Given
        Instant beforeCreation = Instant.now();

        // Create orders (both will have recent timestamps due to @CreationTimestamp)
        CalendarOrder order1 = createOrder(CalendarOrder.STATUS_PENDING, "pi_recent");
        orderRepository.persist(order1);

        CalendarOrder order2 = createOrder(CalendarOrder.STATUS_PAID, "pi_also_recent");
        orderRepository.persist(order2);
        entityManager.flush();

        // When - query for orders created after beforeCreation
        List<CalendarOrder> recentOrders = orderRepository.findRecentOrders(beforeCreation);

        // Then - both orders should be found (created after beforeCreation)
        assertEquals(2, recentOrders.size());

        // When - query for orders created in the future (should find none)
        Instant futureTime = Instant.now().plus(1, ChronoUnit.DAYS);
        List<CalendarOrder> futureOrders = orderRepository.findRecentOrders(futureTime);

        // Then
        assertEquals(0, futureOrders.size());
    }

    @Test
    @Transactional
    void testFindPendingOrders() {
        // Given
        orderRepository.persist(createOrder(CalendarOrder.STATUS_PENDING, "pi_pending1"));
        orderRepository.persist(createOrder(CalendarOrder.STATUS_PENDING, "pi_pending2"));
        orderRepository.persist(createOrder(CalendarOrder.STATUS_PAID, "pi_paid"));
        entityManager.flush();

        // When
        List<CalendarOrder> pendingOrders = orderRepository.findPendingOrders();

        // Then
        assertEquals(2, pendingOrders.size());
        assertTrue(
                pendingOrders.stream()
                        .allMatch(o -> CalendarOrder.STATUS_PENDING.equals(o.status)));
    }

    @Test
    @Transactional
    void testFindPaidNotShipped() {
        // Given
        CalendarOrder paidOrder = createOrder(CalendarOrder.STATUS_PAID, "pi_paid");
        orderRepository.persist(paidOrder);

        CalendarOrder shippedOrder = createOrder(CalendarOrder.STATUS_SHIPPED, "pi_shipped");
        orderRepository.persist(shippedOrder);
        entityManager.flush();

        // When
        List<CalendarOrder> paidNotShipped = orderRepository.findPaidNotShipped();

        // Then
        assertEquals(1, paidNotShipped.size());
        assertEquals(CalendarOrder.STATUS_PAID, paidNotShipped.get(0).status);
    }

    @Test
    @Transactional
    void testFindByUserAndStatus() {
        // Given
        orderRepository.persist(createOrder(CalendarOrder.STATUS_PENDING, "pi_1"));
        orderRepository.persist(createOrder(CalendarOrder.STATUS_PAID, "pi_2"));
        orderRepository.persist(createOrder(CalendarOrder.STATUS_PENDING, "pi_3"));
        entityManager.flush();

        // When
        List<CalendarOrder> pendingOrders =
                orderRepository.findByUserAndStatus(testUser.id, CalendarOrder.STATUS_PENDING);

        // Then
        assertEquals(2, pendingOrders.size());
        assertTrue(
                pendingOrders.stream()
                        .allMatch(
                                o ->
                                        o.user.id.equals(testUser.id)
                                                && CalendarOrder.STATUS_PENDING.equals(o.status)));
    }

    @Test
    @Transactional
    void testOrderHelperMethods() {
        // Given
        CalendarOrder order = createOrder(CalendarOrder.STATUS_PENDING, "pi_test");
        orderRepository.persist(order);

        // When - mark as paid
        order.markAsPaid();
        orderRepository.flush();

        // Then
        assertEquals(CalendarOrder.STATUS_PAID, order.status);
        assertNotNull(order.paidAt);

        // When - mark as shipped
        order.markAsShipped();
        orderRepository.flush();

        // Then
        assertEquals(CalendarOrder.STATUS_SHIPPED, order.status);
        assertNotNull(order.shippedAt);

        // When - cancel
        CalendarOrder cancelOrder = createOrder(CalendarOrder.STATUS_PENDING, "pi_cancel");
        orderRepository.persist(cancelOrder);
        cancelOrder.cancel();
        orderRepository.flush();

        // Then
        assertEquals(CalendarOrder.STATUS_CANCELLED, cancelOrder.status);
    }

    @Test
    @Transactional
    void testIsTerminal() {
        // Given
        CalendarOrder deliveredOrder = createOrder(CalendarOrder.STATUS_DELIVERED, "pi_delivered");
        CalendarOrder cancelledOrder = createOrder(CalendarOrder.STATUS_CANCELLED, "pi_cancelled");
        CalendarOrder pendingOrder = createOrder(CalendarOrder.STATUS_PENDING, "pi_pending");

        // Then
        assertTrue(deliveredOrder.isTerminal());
        assertTrue(cancelledOrder.isTerminal());
        assertFalse(pendingOrder.isTerminal());
    }

    private CalendarOrder createOrder(String status, String paymentIntentId) {
        CalendarOrder order = new CalendarOrder();
        order.user = testUser;
        order.calendar = testCalendar;
        order.quantity = 1;
        order.unitPrice = new BigDecimal("29.99");
        order.totalPrice = new BigDecimal("29.99");
        order.status = status;
        order.stripePaymentIntentId = paymentIntentId;

        // Create shipping address JSONB
        ObjectNode shippingAddress = objectMapper.createObjectNode();
        shippingAddress.put("street", "123 Test St");
        shippingAddress.put("city", "Test City");
        shippingAddress.put("state", "TS");
        shippingAddress.put("zip", "12345");
        order.shippingAddress = shippingAddress;

        return order;
    }
}
