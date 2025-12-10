package villagecompute.calendar.services.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import villagecompute.calendar.data.models.*;
import villagecompute.calendar.services.OrderService;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for OrderEmailJobHandler that simulate real-world order scenarios.
 *
 * These tests ensure email templates render correctly for orders created through
 * the actual checkout flow, not just manually constructed test orders.
 *
 * Test scenarios cover:
 * 1. Orders created via processCheckout() (modern item-based orders)
 * 2. Legacy single-calendar orders (backward compatibility)
 * 3. Guest checkout orders (no user, only customerEmail)
 * 4. Orders with various null/missing fields
 * 5. Orders with multiple items
 */
@QuarkusTest
class OrderEmailJobHandlerIntegrationTest {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    OrderService orderService;

    private String testCss;

    @BeforeEach
    @Transactional
    void setUp() throws IOException {
        // Clean up any existing test data
        CalendarOrderItem.deleteAll();
        CalendarOrder.deleteAll();
        UserCalendar.deleteAll();
        CalendarUser.delete("oauthProvider", "GUEST");
        CalendarUser.delete("oauthSubject like ?1", "checkout-test-%");
        CalendarTemplate.delete("name like ?1", "Checkout Test%");

        // Load CSS
        testCss = loadResourceAsString("css/email.css");
    }

    @AfterEach
    @Transactional
    void tearDown() {
        // Clean up test data
        CalendarOrderItem.deleteAll();
        CalendarOrder.deleteAll();
        UserCalendar.deleteAll();
        CalendarUser.delete("oauthProvider", "GUEST");
        CalendarUser.delete("oauthSubject like ?1", "checkout-test-%");
        CalendarTemplate.delete("name like ?1", "Checkout Test%");
    }

    // ==================== processCheckout() Order Tests ====================

    @Test
    @Transactional
    void testOrderConfirmationTemplate_ProcessCheckoutOrder_WithItems() {
        // Given - Simulate a real checkout with items (the modern order flow)
        String orderNumber = orderService.processCheckout(
            "pi_test_checkout_" + System.currentTimeMillis(),
            "customer@example.com",
            createShippingAddressMap(),
            createBillingAddressMap(),
            List.of(
                Map.of(
                    "templateName", "Classic Calendar",
                    "year", 2025,
                    "quantity", 2,
                    "unitPrice", 29.99,
                    "lineTotal", 59.98,
                    "configuration", "{\"productType\": \"print\"}"
                )
            ),
            59.98,  // subtotal
            5.99,   // shipping
            0.0,    // tax
            65.97   // total
        );

        // Find the order that was created
        CalendarOrder order = CalendarOrder.find("orderNumber", orderNumber).firstResult();
        assertNotNull(order, "Order should have been created");

        // Reload with eager fetching (simulating what OrderEmailJobHandler does)
        order = CalendarOrder.find(
            "SELECT o FROM CalendarOrder o " +
            "LEFT JOIN FETCH o.user " +
            "LEFT JOIN FETCH o.items " +
            "LEFT JOIN FETCH o.calendar " +
            "WHERE o.id = ?1", order.id)
            .firstResult();

        // When - Render the order confirmation template
        String html = OrderEmailJobHandler.Templates.orderConfirmation(order, testCss).render();

        // Then - Verify template renders without errors
        assertNotNull(html, "Template should render");
        assertTrue(html.contains("Thank You for Your Order"), "Should contain thank you message");
        assertTrue(html.contains("customer@example.com") || html.contains("Valued Customer"),
            "Should show customer identifier");
        assertTrue(html.contains(order.orderNumber) || html.contains(order.id.toString()),
            "Should contain order identifier");
        assertFalse(html.contains("Property") && html.contains("not found"),
            "Should not contain template errors");
        assertFalse(html.contains("TemplateException"), "Should not contain exception text");
    }

    @Test
    @Transactional
    void testAdminNotificationTemplate_ProcessCheckoutOrder_WithItems() {
        // Given - Simulate a real checkout with items
        String orderNumber = orderService.processCheckout(
            "pi_test_admin_" + System.currentTimeMillis(),
            "admin-test@example.com",
            createShippingAddressMap(),
            null,  // no billing address
            List.of(
                Map.of(
                    "templateName", "Modern Calendar",
                    "year", 2025,
                    "quantity", 1,
                    "unitPrice", 34.99,
                    "lineTotal", 34.99
                ),
                Map.of(
                    "templateName", "PDF Calendar",
                    "year", 2025,
                    "quantity", 1,
                    "unitPrice", 9.99,
                    "lineTotal", 9.99,
                    "configuration", "{\"productType\": \"pdf\"}"
                )
            ),
            44.98,
            5.99,
            0.0,
            50.97
        );

        CalendarOrder order = CalendarOrder.find("orderNumber", orderNumber).firstResult();
        assertNotNull(order, "Order should have been created");

        // Reload with eager fetching
        order = CalendarOrder.find(
            "SELECT o FROM CalendarOrder o " +
            "LEFT JOIN FETCH o.user " +
            "LEFT JOIN FETCH o.items " +
            "LEFT JOIN FETCH o.calendar " +
            "WHERE o.id = ?1", order.id)
            .firstResult();

        String baseUrl = "https://calendar.villagecompute.com";

        // When - Render the admin notification template
        String html = OrderEmailJobHandler.Templates.adminOrderNotification(order, testCss, baseUrl).render();

        // Then - Verify template renders without errors
        assertNotNull(html, "Template should render");
        assertTrue(html.contains("New Order Received"), "Should contain admin notification message");
        assertTrue(html.contains(orderNumber), "Should contain order number");
        assertTrue(html.contains("admin-test@example.com"), "Should contain customer email");
        assertTrue(html.contains("Modern Calendar") || html.contains("PDF Calendar"),
            "Should contain item names");
        assertFalse(html.contains("Property") && html.contains("not found"),
            "Should not contain template errors");
    }

    @Test
    @Transactional
    void testOrderConfirmationTemplate_GuestCheckout_NoUser() {
        // Given - Guest checkout (user will be created but order.user may be null in some scenarios)
        String orderNumber = orderService.processCheckout(
            "pi_test_guest_" + System.currentTimeMillis(),
            "guest@example.com",
            createShippingAddressMap(),
            null,
            List.of(
                Map.of(
                    "templateName", "Guest Calendar",
                    "year", 2025,
                    "quantity", 1,
                    "unitPrice", 29.99,
                    "lineTotal", 29.99
                )
            ),
            29.99,
            5.99,
            0.0,
            35.98
        );

        CalendarOrder order = CalendarOrder.find("orderNumber", orderNumber).firstResult();
        assertNotNull(order, "Order should have been created");

        // Simulate a scenario where user is null (edge case)
        order.user = null;
        order.persist();

        // Reload with eager fetching
        order = CalendarOrder.find(
            "SELECT o FROM CalendarOrder o " +
            "LEFT JOIN FETCH o.user " +
            "LEFT JOIN FETCH o.items " +
            "LEFT JOIN FETCH o.calendar " +
            "WHERE o.id = ?1", order.id)
            .firstResult();

        // When
        String html = OrderEmailJobHandler.Templates.orderConfirmation(order, testCss).render();

        // Then
        assertNotNull(html, "Template should render");
        assertTrue(html.contains("guest@example.com") || html.contains("Valued Customer"),
            "Should show customer email or fallback");
        assertFalse(html.contains("Property") && html.contains("not found"),
            "Should not contain template errors");
    }

    // ==================== Legacy Order Tests ====================

    @Test
    @Transactional
    void testOrderConfirmationTemplate_LegacyOrder_WithCalendar() {
        // Given - Legacy order with single calendar (old order model)
        CalendarUser user = createTestUser("legacy-test");
        CalendarTemplate template = createTestTemplate("Legacy Test Template");
        UserCalendar calendar = createTestCalendar(user, template, "Legacy Calendar", 2025);

        CalendarOrder order = new CalendarOrder();
        order.user = user;
        order.customerEmail = user.email;
        order.calendar = calendar;  // Legacy field
        order.quantity = 1;
        order.unitPrice = new BigDecimal("29.99");
        order.subtotal = new BigDecimal("29.99");
        order.shippingCost = new BigDecimal("5.99");
        order.taxAmount = BigDecimal.ZERO;
        order.totalPrice = new BigDecimal("35.98");
        order.status = CalendarOrder.STATUS_PAID;
        order.orderNumber = "VC-LEGACY-" + System.currentTimeMillis();
        order.shippingAddress = createShippingAddressJson();
        order.paidAt = Instant.now();
        order.persist();

        // Reload with eager fetching
        order = CalendarOrder.find(
            "SELECT o FROM CalendarOrder o " +
            "LEFT JOIN FETCH o.user " +
            "LEFT JOIN FETCH o.items " +
            "LEFT JOIN FETCH o.calendar " +
            "WHERE o.id = ?1", order.id)
            .firstResult();

        // When
        String html = OrderEmailJobHandler.Templates.orderConfirmation(order, testCss).render();

        // Then
        assertNotNull(html, "Template should render");
        assertTrue(html.contains("Thank You for Your Order"), "Should contain thank you message");
        assertTrue(html.contains("Legacy Calendar"), "Should contain calendar name");
        assertTrue(html.contains("2025"), "Should contain calendar year");
        assertFalse(html.contains("Property") && html.contains("not found"),
            "Should not contain template errors");
    }

    // ==================== Null Field Edge Cases ====================

    @Test
    @Transactional
    void testOrderConfirmationTemplate_NullCalendarAndEmptyItems() {
        // Given - Order with null calendar AND empty items list (worst case)
        CalendarUser user = createTestUser("null-test");

        CalendarOrder order = new CalendarOrder();
        order.user = user;
        order.customerEmail = user.email;
        order.calendar = null;  // No legacy calendar
        // items list is empty by default
        order.quantity = 1;
        order.unitPrice = new BigDecimal("29.99");
        order.subtotal = new BigDecimal("29.99");
        order.shippingCost = new BigDecimal("5.99");
        order.taxAmount = BigDecimal.ZERO;
        order.totalPrice = new BigDecimal("35.98");
        order.status = CalendarOrder.STATUS_PAID;
        order.orderNumber = "VC-NULL-" + System.currentTimeMillis();
        order.shippingAddress = createShippingAddressJson();
        order.paidAt = Instant.now();
        order.persist();

        // Reload with eager fetching
        order = CalendarOrder.find(
            "SELECT o FROM CalendarOrder o " +
            "LEFT JOIN FETCH o.user " +
            "LEFT JOIN FETCH o.items " +
            "LEFT JOIN FETCH o.calendar " +
            "WHERE o.id = ?1", order.id)
            .firstResult();

        // When
        String html = OrderEmailJobHandler.Templates.orderConfirmation(order, testCss).render();

        // Then - Should render without exceptions
        assertNotNull(html, "Template should render even with null calendar");
        assertTrue(html.contains("Thank You for Your Order"), "Should contain thank you message");
        assertFalse(html.contains("Property") && html.contains("not found"),
            "Should not contain template errors");
    }

    @Test
    @Transactional
    void testAdminNotificationTemplate_NullCalendarAndEmptyItems() {
        // Given - Order with null calendar AND empty items list
        CalendarUser user = createTestUser("admin-null-test");

        CalendarOrder order = new CalendarOrder();
        order.user = user;
        order.customerEmail = user.email;
        order.calendar = null;
        order.quantity = 1;
        order.unitPrice = new BigDecimal("29.99");
        order.subtotal = new BigDecimal("29.99");
        order.shippingCost = new BigDecimal("5.99");
        order.taxAmount = BigDecimal.ZERO;
        order.totalPrice = new BigDecimal("35.98");
        order.status = CalendarOrder.STATUS_PAID;
        order.orderNumber = "VC-ADMIN-NULL-" + System.currentTimeMillis();
        order.shippingAddress = createShippingAddressJson();
        order.paidAt = Instant.now();
        order.persist();

        // Reload with eager fetching
        order = CalendarOrder.find(
            "SELECT o FROM CalendarOrder o " +
            "LEFT JOIN FETCH o.user " +
            "LEFT JOIN FETCH o.items " +
            "LEFT JOIN FETCH o.calendar " +
            "WHERE o.id = ?1", order.id)
            .firstResult();

        String baseUrl = "https://calendar.villagecompute.com";

        // When
        String html = OrderEmailJobHandler.Templates.adminOrderNotification(order, testCss, baseUrl).render();

        // Then
        assertNotNull(html, "Template should render");
        assertTrue(html.contains("New Order Received"), "Should contain admin notification");
        assertFalse(html.contains("Property") && html.contains("not found"),
            "Should not contain template errors");
    }

    @Test
    @Transactional
    void testOrderConfirmationTemplate_NullShippingAddress() {
        // Given - Order with null shipping address
        CalendarUser user = createTestUser("no-shipping-test");

        CalendarOrder order = new CalendarOrder();
        order.user = user;
        order.customerEmail = user.email;
        order.calendar = null;
        order.quantity = 1;
        order.unitPrice = new BigDecimal("29.99");
        order.subtotal = new BigDecimal("29.99");
        order.shippingCost = BigDecimal.ZERO;
        order.taxAmount = BigDecimal.ZERO;
        order.totalPrice = new BigDecimal("29.99");
        order.status = CalendarOrder.STATUS_PAID;
        order.orderNumber = "VC-NOSHIP-" + System.currentTimeMillis();
        order.shippingAddress = null;  // No shipping address
        order.paidAt = Instant.now();
        order.persist();

        // Reload
        order = CalendarOrder.find(
            "SELECT o FROM CalendarOrder o " +
            "LEFT JOIN FETCH o.user " +
            "LEFT JOIN FETCH o.items " +
            "LEFT JOIN FETCH o.calendar " +
            "WHERE o.id = ?1", order.id)
            .firstResult();

        // When
        String html = OrderEmailJobHandler.Templates.orderConfirmation(order, testCss).render();

        // Then
        assertNotNull(html, "Template should render");
        assertTrue(html.contains("Shipping address will be confirmed separately") ||
                   html.contains("No shipping address"),
            "Should show fallback message for missing address");
        assertFalse(html.contains("Property") && html.contains("not found"),
            "Should not contain template errors");
    }

    @Test
    @Transactional
    void testTemplates_AllFieldsNull_EdgeCase() {
        // Given - Minimal order with most fields null (extreme edge case)
        CalendarOrder order = new CalendarOrder();
        order.user = null;
        order.customerEmail = null;
        order.calendar = null;
        order.quantity = null;
        order.unitPrice = null;
        order.subtotal = null;
        order.shippingCost = null;
        order.taxAmount = null;
        order.totalPrice = new BigDecimal("0.00");  // Required field
        order.status = CalendarOrder.STATUS_PENDING;
        order.orderNumber = "VC-MINIMAL-" + System.currentTimeMillis();
        order.shippingAddress = null;
        order.paidAt = null;
        order.persist();

        // Reload
        order = CalendarOrder.find(
            "SELECT o FROM CalendarOrder o " +
            "LEFT JOIN FETCH o.user " +
            "LEFT JOIN FETCH o.items " +
            "LEFT JOIN FETCH o.calendar " +
            "WHERE o.id = ?1", order.id)
            .firstResult();

        // When - Both templates should handle extreme null cases
        String customerHtml = OrderEmailJobHandler.Templates.orderConfirmation(order, testCss).render();
        String adminHtml = OrderEmailJobHandler.Templates.adminOrderNotification(order, testCss, "https://example.com").render();

        // Then
        assertNotNull(customerHtml, "Customer template should render");
        assertNotNull(adminHtml, "Admin template should render");
        assertFalse(customerHtml.contains("Property") && customerHtml.contains("not found"),
            "Customer template should not contain errors");
        assertFalse(adminHtml.contains("Property") && adminHtml.contains("not found"),
            "Admin template should not contain errors");
    }

    // ==================== Helper Methods ====================

    private Map<String, Object> createShippingAddressMap() {
        return Map.of(
            "name", "John Doe",
            "line1", "123 Main St",
            "line2", "Apt 4B",
            "city", "New York",
            "state", "NY",
            "postalCode", "10001",
            "country", "US"
        );
    }

    private Map<String, Object> createBillingAddressMap() {
        return Map.of(
            "name", "John Doe",
            "line1", "123 Main St",
            "city", "New York",
            "state", "NY",
            "postalCode", "10001",
            "country", "US"
        );
    }

    private ObjectNode createShippingAddressJson() {
        ObjectNode address = objectMapper.createObjectNode();
        address.put("name", "John Doe");
        address.put("line1", "123 Main St");
        address.put("line2", "Apt 4B");
        address.put("city", "New York");
        address.put("state", "NY");
        address.put("postalCode", "10001");
        address.put("country", "US");
        return address;
    }

    private CalendarUser createTestUser(String suffix) {
        CalendarUser user = new CalendarUser();
        user.oauthProvider = "GOOGLE";
        user.oauthSubject = "checkout-test-" + suffix + "-" + System.currentTimeMillis();
        user.email = suffix + "@example.com";
        user.displayName = "Test User " + suffix;
        user.persist();
        return user;
    }

    private CalendarTemplate createTestTemplate(String name) {
        CalendarTemplate template = new CalendarTemplate();
        template.name = name;
        template.description = "Test template for email tests";
        template.isActive = true;
        template.configuration = objectMapper.createObjectNode();
        template.persist();
        return template;
    }

    private UserCalendar createTestCalendar(CalendarUser user, CalendarTemplate template, String name, int year) {
        UserCalendar calendar = new UserCalendar();
        calendar.user = user;
        calendar.name = name;
        calendar.year = year;
        calendar.template = template;
        calendar.isPublic = false;
        calendar.persist();
        return calendar;
    }

    private String loadResourceAsString(String resourcePath) throws IOException {
        try (var inputStream = Thread.currentThread().getContextClassLoader()
            .getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
