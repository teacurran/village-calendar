package villagecompute.calendar.data.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import villagecompute.calendar.data.repositories.CalendarTemplateRepository;
import villagecompute.calendar.data.repositories.CalendarUserRepository;
import villagecompute.calendar.data.repositories.TestDataCleaner;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class CalendarOrderTest {

    @Inject
    Validator validator;

    @Inject
    TestDataCleaner testDataCleaner;

    @Inject
    CalendarUserRepository calendarUserRepository;

    @Inject
    CalendarTemplateRepository templateRepository;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    jakarta.persistence.EntityManager entityManager;

    private CalendarUser testUser;
    private UserCalendar testCalendar;

    @BeforeEach
    @Transactional
    void setUp() {
        testDataCleaner.deleteAll();

        // Create test user
        testUser = new CalendarUser();
        testUser.oauthProvider = "GOOGLE";
        testUser.oauthSubject = "test-subject-order-test";
        testUser.email = "test@example.com";
        testUser.displayName = "Test User";
        testUser.isAdmin = false;
        calendarUserRepository.persist(testUser);

        // Create test template
        CalendarTemplate testTemplate = new CalendarTemplate();
        testTemplate.name = "Test Template";
        testTemplate.isActive = true;
        testTemplate.displayOrder = 1;
        ObjectNode config = objectMapper.createObjectNode();
        config.put("theme", "test");
        testTemplate.configuration = config;
        templateRepository.persist(testTemplate);

        // Create test calendar
        testCalendar = new UserCalendar();
        testCalendar.user = testUser;
        testCalendar.name = "Test Calendar";
        testCalendar.year = 2025;
        testCalendar.isPublic = true;
        testCalendar.template = testTemplate;
        testCalendar.persist();

        // Flush to ensure entities are persisted
        entityManager.flush();
    }

    @Test
    @Transactional
    void testValidEntity_Success() {
        // Given
        CalendarOrder order = createValidOrder();

        // When
        order.persist();
        entityManager.flush(); // Flush to generate ID and timestamps

        // Then
        assertNotNull(order.id);
        assertNotNull(order.created);
        assertNotNull(order.updated);
        assertEquals(0L, order.version);
    }

    @Test
    void testInvalidEntity_NullUser() {
        // Given
        CalendarOrder order = createValidOrder();
        order.user = null;

        // When
        Set<ConstraintViolation<CalendarOrder>> violations = validator.validate(order);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<CalendarOrder> violation = violations.iterator().next();
        assertEquals("user", violation.getPropertyPath().toString());
    }

    @Test
    void testInvalidEntity_NullCalendar() {
        // Given
        CalendarOrder order = createValidOrder();
        order.calendar = null;

        // When
        Set<ConstraintViolation<CalendarOrder>> violations = validator.validate(order);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<CalendarOrder> violation = violations.iterator().next();
        assertEquals("calendar", violation.getPropertyPath().toString());
    }

    @Test
    void testInvalidEntity_NullQuantity() {
        // Given
        CalendarOrder order = createValidOrder();
        order.quantity = null;

        // When
        Set<ConstraintViolation<CalendarOrder>> violations = validator.validate(order);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<CalendarOrder> violation = violations.iterator().next();
        assertEquals("quantity", violation.getPropertyPath().toString());
    }

    @Test
    void testInvalidEntity_QuantityTooLow() {
        // Given
        CalendarOrder order = createValidOrder();
        order.quantity = 0; // Min is 1

        // When
        Set<ConstraintViolation<CalendarOrder>> violations = validator.validate(order);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<CalendarOrder> violation = violations.iterator().next();
        assertEquals("quantity", violation.getPropertyPath().toString());
    }

    @Test
    void testInvalidEntity_NullUnitPrice() {
        // Given
        CalendarOrder order = createValidOrder();
        order.unitPrice = null;

        // When
        Set<ConstraintViolation<CalendarOrder>> violations = validator.validate(order);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<CalendarOrder> violation = violations.iterator().next();
        assertEquals("unitPrice", violation.getPropertyPath().toString());
    }

    @Test
    void testInvalidEntity_NegativeUnitPrice() {
        // Given
        CalendarOrder order = createValidOrder();
        order.unitPrice = BigDecimal.valueOf(-1.00);

        // When
        Set<ConstraintViolation<CalendarOrder>> violations = validator.validate(order);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<CalendarOrder> violation = violations.iterator().next();
        assertEquals("unitPrice", violation.getPropertyPath().toString());
    }

    @Test
    void testInvalidEntity_NullStatus() {
        // Given
        CalendarOrder order = createValidOrder();
        order.status = null;

        // When
        Set<ConstraintViolation<CalendarOrder>> violations = validator.validate(order);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<CalendarOrder> violation = violations.iterator().next();
        assertEquals("status", violation.getPropertyPath().toString());
    }

    @Test
    @Transactional
    void testJsonbSerialization_ShippingAddress() {
        // Given
        CalendarOrder order = createValidOrder();
        ObjectNode address = objectMapper.createObjectNode();
        address.put("street", "123 Main St");
        address.put("city", "San Francisco");
        address.put("state", "CA");
        address.put("zip", "94102");
        order.shippingAddress = address;
        order.persist();

        // When
        CalendarOrder found = CalendarOrder.findById(order.id);

        // Then
        assertNotNull(found.shippingAddress);
        assertEquals("123 Main St", found.shippingAddress.get("street").asText());
        assertEquals("San Francisco", found.shippingAddress.get("city").asText());
        assertEquals("CA", found.shippingAddress.get("state").asText());
        assertEquals("94102", found.shippingAddress.get("zip").asText());
    }

    @Test
    @Transactional
    void testJsonbSerialization_NullShippingAddress() {
        // Given
        CalendarOrder order = createValidOrder();
        order.shippingAddress = null;
        order.persist();

        // When
        CalendarOrder found = CalendarOrder.findById(order.id);

        // Then
        assertNull(found.shippingAddress);
    }

    @Test
    @Transactional
    void testFindByUser() {
        // Given
        CalendarOrder order1 = createValidOrder();
        order1.persist();
        CalendarOrder order2 = createValidOrder();
        order2.persist();

        // When
        List<CalendarOrder> orders = CalendarOrder.findByUser(testUser.id).list();

        // Then
        assertEquals(2, orders.size());
        assertTrue(orders.stream().allMatch(o -> o.user.id.equals(testUser.id)));
    }

    @Test
    @Transactional
    void testFindByStatusOrderByCreatedDesc() {
        // Given
        CalendarOrder pendingOrder1 = createValidOrder();
        pendingOrder1.status = "PENDING";
        pendingOrder1.persist();

        CalendarOrder pendingOrder2 = createValidOrder();
        pendingOrder2.status = "PENDING";
        pendingOrder2.persist();

        CalendarOrder paidOrder = createValidOrder();
        paidOrder.status = "PAID";
        paidOrder.persist();

        // When
        List<CalendarOrder> pendingOrders = CalendarOrder.findByStatusOrderByCreatedDesc("PENDING");

        // Then
        assertEquals(2, pendingOrders.size());
        assertTrue(pendingOrders.stream().allMatch(o -> "PENDING".equals(o.status)));
    }

    @Test
    @Transactional
    void testFindByCalendar() {
        // Given
        CalendarOrder order1 = createValidOrder();
        order1.persist();
        CalendarOrder order2 = createValidOrder();
        order2.persist();

        // When
        List<CalendarOrder> orders = CalendarOrder.findByCalendar(testCalendar.id).list();

        // Then
        assertEquals(2, orders.size());
        assertTrue(orders.stream().allMatch(o -> o.calendar.id.equals(testCalendar.id)));
    }

    @Test
    @Transactional
    void testFindByStripePaymentIntent() {
        // Given
        CalendarOrder order = createValidOrder();
        order.stripePaymentIntentId = "pi_123456";
        order.persist();

        // When
        List<CalendarOrder> orders = CalendarOrder.findByStripePaymentIntent("pi_123456").list();

        // Then
        assertEquals(1, orders.size());
        assertEquals("pi_123456", orders.get(0).stripePaymentIntentId);
    }

    @Test
    @Transactional
    void testFindRecentOrders() {
        // Given
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);

        CalendarOrder recentOrder = createValidOrder();
        recentOrder.persist();

        CalendarOrder oldOrder = createValidOrder();
        oldOrder.persist();
        entityManager.flush(); // Flush to persist first

        // Manually set old created date using native SQL
        entityManager.createNativeQuery(
            "UPDATE calendar_orders SET created = :newCreated WHERE id = :id"
        ).setParameter("newCreated", yesterday.minus(1, ChronoUnit.DAYS))
         .setParameter("id", oldOrder.id)
         .executeUpdate();
        entityManager.flush();
        entityManager.clear(); // Clear persistence context to force reload

        // When
        List<CalendarOrder> recentOrders = CalendarOrder.findRecentOrders(yesterday).list();

        // Then
        assertEquals(1, recentOrders.size());
        assertTrue(recentOrders.get(0).created.isAfter(yesterday));
    }

    @Test
    @Transactional
    void testMarkAsPaid() {
        // Given
        CalendarOrder order = createValidOrder();
        order.status = "PENDING";
        order.paidAt = null;
        order.persist();

        // When
        order.markAsPaid();

        // Then
        assertEquals("PAID", order.status);
        assertNotNull(order.paidAt);
        assertTrue(order.paidAt.isAfter(Instant.now().minus(5, ChronoUnit.SECONDS)));
    }

    @Test
    @Transactional
    void testMarkAsShipped() {
        // Given
        CalendarOrder order = createValidOrder();
        order.status = "PAID";
        order.shippedAt = null;
        order.persist();

        // When
        order.markAsShipped();

        // Then
        assertEquals("SHIPPED", order.status);
        assertNotNull(order.shippedAt);
        assertTrue(order.shippedAt.isAfter(Instant.now().minus(5, ChronoUnit.SECONDS)));
    }

    @Test
    @Transactional
    void testCancel() {
        // Given
        CalendarOrder order = createValidOrder();
        order.status = "PENDING";
        order.persist();

        // When
        order.cancel();

        // Then
        assertEquals("CANCELLED", order.status);
    }

    @Test
    @Transactional
    void testIsTerminal_Delivered() {
        // Given
        CalendarOrder order = createValidOrder();
        order.status = "DELIVERED";
        order.persist();

        // When
        boolean isTerminal = order.isTerminal();

        // Then
        assertTrue(isTerminal);
    }

    @Test
    @Transactional
    void testIsTerminal_Cancelled() {
        // Given
        CalendarOrder order = createValidOrder();
        order.status = "CANCELLED";
        order.persist();

        // When
        boolean isTerminal = order.isTerminal();

        // Then
        assertTrue(isTerminal);
    }

    @Test
    @Transactional
    void testIsTerminal_Pending() {
        // Given
        CalendarOrder order = createValidOrder();
        order.status = "PENDING";
        order.persist();

        // When
        boolean isTerminal = order.isTerminal();

        // Then
        assertFalse(isTerminal);
    }

    @Test
    @Transactional
    void testRelationships_ManyToOneUser() {
        // Given
        CalendarOrder order = createValidOrder();
        order.persist();

        // When
        CalendarOrder found = CalendarOrder.findById(order.id);

        // Then
        assertNotNull(found.user);
        assertEquals(testUser.id, found.user.id);
    }

    @Test
    @Transactional
    void testRelationships_ManyToOneCalendar() {
        // Given
        CalendarOrder order = createValidOrder();
        order.persist();

        // When
        CalendarOrder found = CalendarOrder.findById(order.id);

        // Then
        assertNotNull(found.calendar);
        assertEquals(testCalendar.id, found.calendar.id);
    }

    private CalendarOrder createValidOrder() {
        CalendarOrder order = new CalendarOrder();
        order.user = testUser;
        order.calendar = testCalendar;
        order.quantity = 1;
        order.unitPrice = BigDecimal.valueOf(19.99);
        order.totalPrice = BigDecimal.valueOf(19.99);
        order.status = "PENDING";
        return order;
    }
}
