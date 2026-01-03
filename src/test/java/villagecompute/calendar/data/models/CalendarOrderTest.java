package villagecompute.calendar.data.models;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import villagecompute.calendar.data.repositories.CalendarOrderRepository;
import villagecompute.calendar.data.repositories.CalendarTemplateRepository;
import villagecompute.calendar.data.repositories.CalendarUserRepository;
import villagecompute.calendar.data.repositories.TestDataCleaner;

import io.quarkus.test.junit.QuarkusTest;

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
    CalendarOrderRepository orderRepository;

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
        orderRepository.persist(order);
        entityManager.flush(); // Flush to generate ID and timestamps

        // Then
        assertNotNull(order.id);
        assertNotNull(order.created);
        assertNotNull(order.updated);
        assertEquals(0L, order.version);
    }

    @Test
    void testValidEntity_NullUser_GuestCheckout() {
        // Given - user is now nullable to support guest checkout
        CalendarOrder order = createValidOrder();
        order.user = null;

        // When
        Set<ConstraintViolation<CalendarOrder>> violations = validator.validate(order);

        // Then - no violations, null user is allowed for guest checkout
        assertEquals(0, violations.size());
    }

    // Tests for deprecated calendar/quantity/unitPrice fields have been removed
    // Orders now use the items list instead of these fields

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
        orderRepository.persist(order);
        entityManager.flush();

        // When
        CalendarOrder found = orderRepository.findById(order.id).orElseThrow();

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
        orderRepository.persist(order);
        entityManager.flush();

        // When
        CalendarOrder found = orderRepository.findById(order.id).orElseThrow();

        // Then
        assertNull(found.shippingAddress);
    }

    @Test
    @Transactional
    void testFindByUser() {
        // Given
        CalendarOrder order1 = createValidOrder();
        orderRepository.persist(order1);
        CalendarOrder order2 = createValidOrder();
        orderRepository.persist(order2);
        entityManager.flush();

        // When
        List<CalendarOrder> orders = orderRepository.findByUser(testUser.id);

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
        orderRepository.persist(pendingOrder1);

        CalendarOrder pendingOrder2 = createValidOrder();
        pendingOrder2.status = "PENDING";
        orderRepository.persist(pendingOrder2);

        CalendarOrder paidOrder = createValidOrder();
        paidOrder.status = "PAID";
        orderRepository.persist(paidOrder);
        entityManager.flush();

        // When
        List<CalendarOrder> pendingOrders = orderRepository.findByStatusOrderByCreatedDesc("PENDING");

        // Then
        assertEquals(2, pendingOrders.size());
        assertTrue(pendingOrders.stream().allMatch(o -> "PENDING".equals(o.status)));
    }

    // testFindByCalendar removed - the findByCalendar method was deprecated and removed
    // Orders now use items to track calendars instead of a direct calendar reference

    @Test
    @Transactional
    void testFindByStripePaymentIntent() {
        // Given
        CalendarOrder order = createValidOrder();
        order.stripePaymentIntentId = "pi_123456";
        orderRepository.persist(order);
        entityManager.flush();

        // When
        CalendarOrder found = orderRepository.findByStripePaymentIntent("pi_123456").orElse(null);

        // Then
        assertNotNull(found);
        assertEquals("pi_123456", found.stripePaymentIntentId);
    }

    @Test
    @Transactional
    void testFindRecentOrders() {
        // Given
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);

        CalendarOrder recentOrder = createValidOrder();
        orderRepository.persist(recentOrder);

        CalendarOrder oldOrder = createValidOrder();
        orderRepository.persist(oldOrder);
        entityManager.flush(); // Flush to persist first

        // Manually set old created date using native SQL
        entityManager.createNativeQuery("UPDATE calendar_orders SET created = :newCreated WHERE id = :id")
                .setParameter("newCreated", yesterday.minus(1, ChronoUnit.DAYS)).setParameter("id", oldOrder.id)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear(); // Clear persistence context to force reload

        // When
        List<CalendarOrder> recentOrders = orderRepository.findRecentOrders(yesterday);

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
        orderRepository.persist(order);
        entityManager.flush();

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
        orderRepository.persist(order);
        entityManager.flush();

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
        orderRepository.persist(order);
        entityManager.flush();

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
        orderRepository.persist(order);
        entityManager.flush();

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
        orderRepository.persist(order);
        entityManager.flush();

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
        orderRepository.persist(order);
        entityManager.flush();

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
        orderRepository.persist(order);
        entityManager.flush();

        // When
        CalendarOrder found = orderRepository.findById(order.id).orElseThrow();

        // Then
        assertNotNull(found.user);
        assertEquals(testUser.id, found.user.id);
    }

    // testRelationships_ManyToOneCalendar removed - the calendar field was deprecated and removed
    // Orders now use items to track calendars instead of a direct calendar reference

    @Test
    @Transactional
    void testAllFields_SetAndRetrieve() {
        // Given - Create order with ALL fields populated
        CalendarOrder order = new CalendarOrder();
        order.user = testUser;
        order.subtotal = BigDecimal.valueOf(149.95);
        order.totalPrice = BigDecimal.valueOf(149.95);
        order.status = CalendarOrder.STATUS_PAID;

        ObjectNode address = objectMapper.createObjectNode();
        address.put("name", "John Doe");
        address.put("street", "456 Oak Ave");
        address.put("city", "Portland");
        address.put("state", "OR");
        address.put("zip", "97201");
        address.put("country", "USA");
        order.shippingAddress = address;

        order.stripePaymentIntentId = "pi_complete_123";
        order.stripeChargeId = "ch_complete_456";
        order.notes = "Please ship to side door";
        order.paidAt = Instant.now().minus(2, ChronoUnit.HOURS);
        order.shippedAt = Instant.now().minus(1, ChronoUnit.HOURS);

        // When
        orderRepository.persist(order);
        entityManager.flush();
        entityManager.clear();

        // Then - Verify ALL fields persisted and can be retrieved
        CalendarOrder found = orderRepository.findById(order.id).orElseThrow();
        assertNotNull(found);
        assertEquals(testUser.id, found.user.id);
        assertEquals(0, BigDecimal.valueOf(149.95).compareTo(found.subtotal));
        assertEquals(0, BigDecimal.valueOf(149.95).compareTo(found.totalPrice));
        assertEquals(CalendarOrder.STATUS_PAID, found.status);
        assertNotNull(found.shippingAddress);
        assertEquals("John Doe", found.shippingAddress.get("name").asText());
        assertEquals("pi_complete_123", found.stripePaymentIntentId);
        assertEquals("ch_complete_456", found.stripeChargeId);
        assertEquals("Please ship to side door", found.notes);
        assertNotNull(found.paidAt);
        assertNotNull(found.shippedAt);
        assertNotNull(found.created);
        assertNotNull(found.updated);
        assertEquals(0L, found.version);
    }

    @Test
    @Transactional
    void testUpdate_ModifiesUpdatedTimestamp() {
        // Given
        CalendarOrder order = createValidOrder();
        order.persist();
        entityManager.flush();
        Instant originalUpdated = order.updated;

        // Wait to ensure timestamp changes
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
        }

        // When
        order.totalPrice = BigDecimal.valueOf(59.97);
        order.notes = "Updated delivery instructions";
        orderRepository.persist(order);
        entityManager.flush();

        // Then
        assertTrue(order.updated.isAfter(originalUpdated));
        assertEquals(0, BigDecimal.valueOf(59.97).compareTo(order.totalPrice));
        assertEquals("Updated delivery instructions", order.notes);
        assertEquals(1L, order.version);
    }

    @Test
    @Transactional
    void testDelete_RemovesEntity() {
        // Given
        CalendarOrder order = createValidOrder();
        orderRepository.persist(order);
        java.util.UUID orderId = order.id;
        entityManager.flush();

        // When
        orderRepository.delete(order);
        entityManager.flush();

        // Then
        assertTrue(orderRepository.findById(orderId).isEmpty());
    }

    @Test
    @Transactional
    void testListAll() {
        // Given
        orderRepository.persist(createValidOrder());
        orderRepository.persist(createValidOrder());
        entityManager.flush();

        // When
        List<CalendarOrder> allOrders = orderRepository.listAll();

        // Then
        assertEquals(2, allOrders.size());
    }

    @Test
    @Transactional
    void testCount() {
        // Given
        orderRepository.persist(createValidOrder());
        orderRepository.persist(createValidOrder());
        orderRepository.persist(createValidOrder());
        entityManager.flush();

        // When
        long count = orderRepository.count();

        // Then
        assertEquals(3, count);
    }

    @Test
    @Transactional
    void testStatusConstants() {
        // Verify all status constants are accessible
        assertEquals("PENDING", CalendarOrder.STATUS_PENDING);
        assertEquals("PAID", CalendarOrder.STATUS_PAID);
        assertEquals("PROCESSING", CalendarOrder.STATUS_PROCESSING);
        assertEquals("SHIPPED", CalendarOrder.STATUS_SHIPPED);
        assertEquals("DELIVERED", CalendarOrder.STATUS_DELIVERED);
        assertEquals("CANCELLED", CalendarOrder.STATUS_CANCELLED);
    }

    @Test
    @Transactional
    void testMarkAsPaid_FromPending() {
        // Given
        CalendarOrder order = createValidOrder();
        order.status = CalendarOrder.STATUS_PENDING;
        order.paidAt = null;
        orderRepository.persist(order);
        entityManager.flush();

        // When
        order.markAsPaid();
        entityManager.flush();

        // Then
        assertEquals(CalendarOrder.STATUS_PAID, order.status);
        assertNotNull(order.paidAt);
    }

    @Test
    @Transactional
    void testMarkAsShipped_FromPaid() {
        // Given
        CalendarOrder order = createValidOrder();
        order.status = CalendarOrder.STATUS_PAID;
        order.shippedAt = null;
        orderRepository.persist(order);
        entityManager.flush();

        // When
        order.markAsShipped();
        entityManager.flush();

        // Then
        assertEquals(CalendarOrder.STATUS_SHIPPED, order.status);
        assertNotNull(order.shippedAt);
    }

    @Test
    @Transactional
    void testCancel_FromAnyStatus() {
        // Given
        CalendarOrder order = createValidOrder();
        order.status = CalendarOrder.STATUS_PROCESSING;
        orderRepository.persist(order);
        entityManager.flush();

        // When
        order.cancel();
        entityManager.flush();

        // Then
        assertEquals(CalendarOrder.STATUS_CANCELLED, order.status);
    }

    @Test
    @Transactional
    void testIsTerminal_AllStatuses() {
        // Test DELIVERED is terminal
        CalendarOrder deliveredOrder = createValidOrder();
        deliveredOrder.status = CalendarOrder.STATUS_DELIVERED;
        assertTrue(deliveredOrder.isTerminal());

        // Test CANCELLED is terminal
        CalendarOrder cancelledOrder = createValidOrder();
        cancelledOrder.status = CalendarOrder.STATUS_CANCELLED;
        assertTrue(cancelledOrder.isTerminal());

        // Test PENDING is not terminal
        CalendarOrder pendingOrder = createValidOrder();
        pendingOrder.status = CalendarOrder.STATUS_PENDING;
        assertFalse(pendingOrder.isTerminal());

        // Test PAID is not terminal
        CalendarOrder paidOrder = createValidOrder();
        paidOrder.status = CalendarOrder.STATUS_PAID;
        assertFalse(paidOrder.isTerminal());

        // Test PROCESSING is not terminal
        CalendarOrder processingOrder = createValidOrder();
        processingOrder.status = CalendarOrder.STATUS_PROCESSING;
        assertFalse(processingOrder.isTerminal());

        // Test SHIPPED is not terminal
        CalendarOrder shippedOrder = createValidOrder();
        shippedOrder.status = CalendarOrder.STATUS_SHIPPED;
        assertFalse(shippedOrder.isTerminal());
    }

    @Test
    void testValidation_StatusTooLong() {
        // Given
        CalendarOrder order = createValidOrder();
        order.status = "A".repeat(51); // Max is 50

        // When
        Set<ConstraintViolation<CalendarOrder>> violations = validator.validate(order);

        // Then
        assertTrue(violations.size() >= 1);
        boolean hasViolation = violations.stream().anyMatch(v -> "status".equals(v.getPropertyPath().toString()));
        assertTrue(hasViolation);
    }

    @Test
    void testValidation_StripePaymentIntentIdTooLong() {
        // Given
        CalendarOrder order = createValidOrder();
        order.stripePaymentIntentId = "pi_" + "a".repeat(253); // Exceeds 255 char limit

        // When
        Set<ConstraintViolation<CalendarOrder>> violations = validator.validate(order);

        // Then
        assertTrue(violations.size() >= 1);
        boolean hasViolation = violations.stream()
                .anyMatch(v -> "stripePaymentIntentId".equals(v.getPropertyPath().toString()));
        assertTrue(hasViolation);
    }

    @Test
    void testValidation_StripeChargeIdTooLong() {
        // Given
        CalendarOrder order = createValidOrder();
        order.stripeChargeId = "ch_" + "a".repeat(254); // Exceeds 255 char limit

        // When
        Set<ConstraintViolation<CalendarOrder>> violations = validator.validate(order);

        // Then
        assertTrue(violations.size() >= 1);
        boolean hasViolation = violations.stream()
                .anyMatch(v -> "stripeChargeId".equals(v.getPropertyPath().toString()));
        assertTrue(hasViolation);
    }

    @Test
    @Transactional
    void testNotes_LongText() {
        // Given - TEXT column should support long text
        CalendarOrder order = createValidOrder();
        String longNotes = "Customer notes: " + "This is important delivery information. ".repeat(100);
        order.notes = longNotes;
        orderRepository.persist(order);
        entityManager.flush();

        // When
        CalendarOrder found = orderRepository.findById(order.id).orElseThrow();

        // Then
        assertNotNull(found.notes);
        assertEquals(longNotes, found.notes);
    }

    @Test
    @Transactional
    void testOptionalFields_NullValues() {
        // Given - Order with only required fields
        CalendarOrder order = new CalendarOrder();
        order.user = testUser;
        order.subtotal = BigDecimal.valueOf(19.99);
        order.totalPrice = BigDecimal.valueOf(19.99);
        order.status = CalendarOrder.STATUS_PENDING;
        // Leave optional fields null
        order.shippingAddress = null;
        order.stripePaymentIntentId = null;
        order.stripeChargeId = null;
        order.notes = null;
        order.paidAt = null;
        order.shippedAt = null;
        orderRepository.persist(order);
        entityManager.flush();

        // When
        CalendarOrder found = orderRepository.findById(order.id).orElseThrow();

        // Then
        assertNotNull(found);
        assertNull(found.shippingAddress);
        assertNull(found.stripePaymentIntentId);
        assertNull(found.stripeChargeId);
        assertNull(found.notes);
        assertNull(found.paidAt);
        assertNull(found.shippedAt);
    }

    // ==================== Item Management Tests ====================

    @Test
    @Transactional
    void testAddItem() {
        // Given
        CalendarOrder order = createValidOrder();
        orderRepository.persist(order);

        CalendarOrderItem item = new CalendarOrderItem();
        item.productType = CalendarOrderItem.TYPE_PRINT;
        item.description = "Test Calendar 2025";
        item.quantity = 2;
        item.unitPrice = BigDecimal.valueOf(29.99);
        item.lineTotal = BigDecimal.valueOf(59.98);
        item.itemStatus = CalendarOrderItem.STATUS_PENDING;

        // When
        CalendarOrderItem addedItem = order.addItem(item);
        entityManager.flush();

        // Then
        assertNotNull(addedItem);
        assertEquals(order, addedItem.order);
        assertEquals(1, order.items.size());
        assertTrue(order.items.contains(item));
    }

    @Test
    @Transactional
    void testGetPhysicalItems() {
        // Given
        CalendarOrder order = createValidOrder();
        orderRepository.persist(order);

        // Add a print item (physical)
        CalendarOrderItem printItem = new CalendarOrderItem();
        printItem.productType = CalendarOrderItem.TYPE_PRINT;
        printItem.description = "Print Calendar";
        printItem.quantity = 1;
        printItem.unitPrice = BigDecimal.valueOf(29.99);
        printItem.lineTotal = BigDecimal.valueOf(29.99);
        printItem.itemStatus = CalendarOrderItem.STATUS_PENDING;
        order.addItem(printItem);
        printItem.persist();

        // Add a PDF item (digital)
        CalendarOrderItem pdfItem = new CalendarOrderItem();
        pdfItem.productType = CalendarOrderItem.TYPE_PDF;
        pdfItem.description = "PDF Calendar";
        pdfItem.quantity = 1;
        pdfItem.unitPrice = BigDecimal.valueOf(9.99);
        pdfItem.lineTotal = BigDecimal.valueOf(9.99);
        pdfItem.itemStatus = CalendarOrderItem.STATUS_PENDING;
        order.addItem(pdfItem);
        pdfItem.persist();

        entityManager.flush();

        // When
        List<CalendarOrderItem> physicalItems = order.getPhysicalItems();

        // Then
        assertEquals(1, physicalItems.size());
        assertEquals(CalendarOrderItem.TYPE_PRINT, physicalItems.get(0).productType);
    }

    @Test
    @Transactional
    void testGetDigitalItems() {
        // Given
        CalendarOrder order = createValidOrder();
        orderRepository.persist(order);

        // Add a print item
        CalendarOrderItem printItem = new CalendarOrderItem();
        printItem.productType = CalendarOrderItem.TYPE_PRINT;
        printItem.description = "Print Calendar";
        printItem.quantity = 1;
        printItem.unitPrice = BigDecimal.valueOf(29.99);
        printItem.lineTotal = BigDecimal.valueOf(29.99);
        printItem.itemStatus = CalendarOrderItem.STATUS_PENDING;
        order.addItem(printItem);
        printItem.persist();

        // Add two PDF items
        CalendarOrderItem pdfItem1 = new CalendarOrderItem();
        pdfItem1.productType = CalendarOrderItem.TYPE_PDF;
        pdfItem1.description = "PDF Calendar 1";
        pdfItem1.quantity = 1;
        pdfItem1.unitPrice = BigDecimal.valueOf(9.99);
        pdfItem1.lineTotal = BigDecimal.valueOf(9.99);
        pdfItem1.itemStatus = CalendarOrderItem.STATUS_PENDING;
        order.addItem(pdfItem1);
        pdfItem1.persist();

        CalendarOrderItem pdfItem2 = new CalendarOrderItem();
        pdfItem2.productType = CalendarOrderItem.TYPE_PDF;
        pdfItem2.description = "PDF Calendar 2";
        pdfItem2.quantity = 1;
        pdfItem2.unitPrice = BigDecimal.valueOf(9.99);
        pdfItem2.lineTotal = BigDecimal.valueOf(9.99);
        pdfItem2.itemStatus = CalendarOrderItem.STATUS_PENDING;
        order.addItem(pdfItem2);
        pdfItem2.persist();

        entityManager.flush();

        // When
        List<CalendarOrderItem> digitalItems = order.getDigitalItems();

        // Then
        assertEquals(2, digitalItems.size());
        assertTrue(digitalItems.stream().allMatch(item -> CalendarOrderItem.TYPE_PDF.equals(item.productType)));
    }

    @Test
    @Transactional
    void testGetUnshippedItems() {
        // Given
        CalendarOrder order = createValidOrder();
        orderRepository.persist(order);

        // Add unshipped print item
        CalendarOrderItem unshippedItem = new CalendarOrderItem();
        unshippedItem.productType = CalendarOrderItem.TYPE_PRINT;
        unshippedItem.description = "Unshipped Calendar";
        unshippedItem.quantity = 1;
        unshippedItem.unitPrice = BigDecimal.valueOf(29.99);
        unshippedItem.lineTotal = BigDecimal.valueOf(29.99);
        unshippedItem.itemStatus = CalendarOrderItem.STATUS_PENDING;
        unshippedItem.shipment = null; // Not assigned to shipment
        order.addItem(unshippedItem);
        unshippedItem.persist();

        // Add shipped print item (with shipment)
        Shipment shipment = new Shipment();
        shipment.order = order;
        shipment.carrier = "USPS";
        shipment.trackingNumber = "123456";
        shipment.status = Shipment.STATUS_SHIPPED;
        shipment.persist();

        CalendarOrderItem shippedItem = new CalendarOrderItem();
        shippedItem.productType = CalendarOrderItem.TYPE_PRINT;
        shippedItem.description = "Shipped Calendar";
        shippedItem.quantity = 1;
        shippedItem.unitPrice = BigDecimal.valueOf(29.99);
        shippedItem.lineTotal = BigDecimal.valueOf(29.99);
        shippedItem.itemStatus = CalendarOrderItem.STATUS_SHIPPED;
        shippedItem.shipment = shipment;
        order.addItem(shippedItem);
        shippedItem.persist();

        // Add PDF item (digital, doesn't need shipping)
        CalendarOrderItem pdfItem = new CalendarOrderItem();
        pdfItem.productType = CalendarOrderItem.TYPE_PDF;
        pdfItem.description = "PDF Calendar";
        pdfItem.quantity = 1;
        pdfItem.unitPrice = BigDecimal.valueOf(9.99);
        pdfItem.lineTotal = BigDecimal.valueOf(9.99);
        pdfItem.itemStatus = CalendarOrderItem.STATUS_PENDING;
        order.addItem(pdfItem);
        pdfItem.persist();

        entityManager.flush();

        // When
        List<CalendarOrderItem> unshippedItems = order.getUnshippedItems();

        // Then
        assertEquals(1, unshippedItems.size());
        assertEquals("Unshipped Calendar", unshippedItems.get(0).description);
    }

    @Test
    @Transactional
    void testIsFullyShipped_AllShipped() {
        // Given
        CalendarOrder order = createValidOrder();
        orderRepository.persist(order);

        // Add item with shipment
        Shipment shipment = new Shipment();
        shipment.order = order;
        shipment.carrier = "USPS";
        shipment.trackingNumber = "123456";
        shipment.status = Shipment.STATUS_SHIPPED;
        shipment.persist();

        CalendarOrderItem item = new CalendarOrderItem();
        item.productType = CalendarOrderItem.TYPE_PRINT;
        item.description = "Shipped Calendar";
        item.quantity = 1;
        item.unitPrice = BigDecimal.valueOf(29.99);
        item.lineTotal = BigDecimal.valueOf(29.99);
        item.itemStatus = CalendarOrderItem.STATUS_SHIPPED;
        item.shipment = shipment;
        order.addItem(item);
        item.persist();

        entityManager.flush();

        // When/Then
        assertTrue(order.isFullyShipped());
    }

    @Test
    @Transactional
    void testIsFullyShipped_NotAllShipped() {
        // Given
        CalendarOrder order = createValidOrder();
        orderRepository.persist(order);

        CalendarOrderItem item = new CalendarOrderItem();
        item.productType = CalendarOrderItem.TYPE_PRINT;
        item.description = "Pending Calendar";
        item.quantity = 1;
        item.unitPrice = BigDecimal.valueOf(29.99);
        item.lineTotal = BigDecimal.valueOf(29.99);
        item.itemStatus = CalendarOrderItem.STATUS_PENDING;
        item.shipment = null;
        order.addItem(item);
        item.persist();

        entityManager.flush();

        // When/Then
        assertFalse(order.isFullyShipped());
    }

    @Test
    @Transactional
    void testGetTotalItemCount() {
        // Given
        CalendarOrder order = createValidOrder();
        orderRepository.persist(order);

        CalendarOrderItem item1 = new CalendarOrderItem();
        item1.productType = CalendarOrderItem.TYPE_PRINT;
        item1.description = "Calendar 1";
        item1.quantity = 3;
        item1.unitPrice = BigDecimal.valueOf(29.99);
        item1.lineTotal = BigDecimal.valueOf(89.97);
        item1.itemStatus = CalendarOrderItem.STATUS_PENDING;
        order.addItem(item1);
        item1.persist();

        CalendarOrderItem item2 = new CalendarOrderItem();
        item2.productType = CalendarOrderItem.TYPE_PDF;
        item2.description = "Calendar 2";
        item2.quantity = 2;
        item2.unitPrice = BigDecimal.valueOf(9.99);
        item2.lineTotal = BigDecimal.valueOf(19.98);
        item2.itemStatus = CalendarOrderItem.STATUS_PENDING;
        order.addItem(item2);
        item2.persist();

        entityManager.flush();

        // When
        int totalCount = order.getTotalItemCount();

        // Then
        assertEquals(5, totalCount); // 3 + 2
    }

    @Test
    @Transactional
    void testCalculateSubtotal() {
        // Given
        CalendarOrder order = createValidOrder();
        order.subtotal = null;
        orderRepository.persist(order);

        CalendarOrderItem item1 = new CalendarOrderItem();
        item1.productType = CalendarOrderItem.TYPE_PRINT;
        item1.description = "Calendar 1";
        item1.quantity = 2;
        item1.unitPrice = BigDecimal.valueOf(29.99);
        item1.lineTotal = BigDecimal.valueOf(59.98);
        item1.itemStatus = CalendarOrderItem.STATUS_PENDING;
        order.addItem(item1);
        item1.persist();

        CalendarOrderItem item2 = new CalendarOrderItem();
        item2.productType = CalendarOrderItem.TYPE_PDF;
        item2.description = "Calendar 2";
        item2.quantity = 1;
        item2.unitPrice = BigDecimal.valueOf(9.99);
        item2.lineTotal = BigDecimal.valueOf(9.99);
        item2.itemStatus = CalendarOrderItem.STATUS_PENDING;
        order.addItem(item2);
        item2.persist();

        entityManager.flush();

        // When
        order.calculateSubtotal();

        // Then
        assertEquals(0, new BigDecimal("69.97").compareTo(order.subtotal));
    }

    @Test
    void testCalculateTotalPrice() {
        // Given - test calculateTotalPrice without persisting (in-memory)
        CalendarOrder order = new CalendarOrder();
        order.subtotal = new BigDecimal("69.97");
        order.shippingCost = new BigDecimal("5.99");
        order.taxAmount = new BigDecimal("6.30");

        // When
        order.calculateTotalPrice();

        // Then
        assertEquals(0, new BigDecimal("82.26").compareTo(order.totalPrice));
    }

    @Test
    void testCalculateTotalPrice_WithNullValues() {
        // Given - test with null values (in-memory, no persistence)
        CalendarOrder order = new CalendarOrder();
        order.subtotal = new BigDecimal("50.00");
        order.shippingCost = null;
        order.taxAmount = null;

        // When
        order.calculateTotalPrice();

        // Then - Should handle null values as zero
        assertEquals(0, new BigDecimal("50.00").compareTo(order.totalPrice));
    }

    @Test
    @Transactional
    void testFindByOrderNumberWithItems() {
        // Given
        CalendarOrder order = createValidOrder();
        order.orderNumber = "VC-TEST-1234";
        orderRepository.persist(order);

        CalendarOrderItem item = new CalendarOrderItem();
        item.productType = CalendarOrderItem.TYPE_PRINT;
        item.description = "Test Calendar";
        item.quantity = 1;
        item.unitPrice = BigDecimal.valueOf(29.99);
        item.lineTotal = BigDecimal.valueOf(29.99);
        item.itemStatus = CalendarOrderItem.STATUS_PENDING;
        order.addItem(item);
        item.persist();

        entityManager.flush();
        entityManager.clear();

        // When
        java.util.Optional<CalendarOrder> found = CalendarOrder.findByOrderNumberWithItems("VC-TEST-1234");

        // Then
        assertTrue(found.isPresent());
        assertEquals("VC-TEST-1234", found.get().orderNumber);
        assertEquals(1, found.get().items.size());
    }

    @Test
    @Transactional
    void testCountOrdersByYear() {
        // Given
        CalendarOrder order1 = createValidOrder();
        orderRepository.persist(order1);
        CalendarOrder order2 = createValidOrder();
        orderRepository.persist(order2);
        entityManager.flush();

        // When
        int currentYear = java.time.Year.now().getValue();
        long count = CalendarOrder.countOrdersByYear(currentYear);

        // Then
        assertEquals(2, count);
    }

    private CalendarOrder createValidOrder() {
        CalendarOrder order = new CalendarOrder();
        order.user = testUser;
        order.subtotal = BigDecimal.valueOf(19.99);
        order.totalPrice = BigDecimal.valueOf(19.99);
        order.status = "PENDING";
        return order;
    }
}
