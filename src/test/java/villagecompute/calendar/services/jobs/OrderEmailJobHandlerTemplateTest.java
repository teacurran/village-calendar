package villagecompute.calendar.services.jobs;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import villagecompute.calendar.data.models.CalendarOrder;
import villagecompute.calendar.data.models.CalendarOrderItem;
import villagecompute.calendar.data.models.CalendarUser;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests for OrderEmailJobHandler Qute template rendering. Ensures templates render correctly with
 * various combinations of null/present fields.
 */
@QuarkusTest
class OrderEmailJobHandlerTemplateTest {

    @Inject ObjectMapper objectMapper;

    private CalendarUser testUser;
    private String testCss;

    @BeforeEach
    @Transactional
    void setUp() throws IOException {
        // Clean up test data
        CalendarOrderItem.deleteAll();
        CalendarOrder.deleteAll();
        CalendarUser.deleteAll();

        // Create test user
        testUser = new CalendarUser();
        testUser.oauthProvider = "GOOGLE";
        testUser.oauthSubject = "test-oauth-subject-" + System.currentTimeMillis();
        testUser.email = "test@example.com";
        testUser.displayName = "Test User";
        testUser.persist();

        // Load CSS
        testCss = loadResourceAsString("css/email.css");
    }

    @Test
    @Transactional
    void testOrderConfirmationTemplateWithFullShippingAddress() {
        // Given - Order with complete shipping address
        CalendarOrder order = createTestOrder();
        order.shippingAddress = createFullShippingAddress();
        order.paidAt = Instant.now();
        order.persist();

        // When
        String html =
                OrderEmailJobHandler.Templates.orderConfirmation(
                                order,
                                testCss,
                                Collections.emptyList(),
                                "https://calendar.villagecompute.com")
                        .render();

        // Then
        assertNotNull(html);
        assertTrue(
                html.contains("Thank You for Your Order"),
                "Should contain order thank you message");
        assertTrue(html.contains("John Doe"), "Should contain customer name");
        assertTrue(html.contains("123 Main St"), "Should contain address line1");
        assertTrue(html.contains("New York"), "Should contain city");
        assertTrue(html.contains("NY"), "Should contain state");
        assertTrue(html.contains("10001"), "Should contain postal code");
        assertTrue(html.contains("US"), "Should contain country");
    }

    @Test
    @Transactional
    void testOrderConfirmationTemplateWithNullShippingAddress() {
        // Given - Order with null shipping address
        CalendarOrder order = createTestOrder();
        order.shippingAddress = null;
        order.paidAt = Instant.now();
        order.persist();

        // When
        String html =
                OrderEmailJobHandler.Templates.orderConfirmation(
                                order,
                                testCss,
                                Collections.emptyList(),
                                "https://calendar.villagecompute.com")
                        .render();

        // Then
        assertNotNull(html);
        assertTrue(
                html.contains("Thank You for Your Order"),
                "Should contain order thank you message");
        assertTrue(
                html.contains("Shipping address will be confirmed separately"),
                "Should show placeholder for missing address");
        assertFalse(html.contains("\"null\""), "Should not contain literal 'null' text");
    }

    @Test
    @Transactional
    void testOrderConfirmationTemplateWithPartialShippingAddress() {
        // Given - Order with partial shipping address (missing name)
        CalendarOrder order = createTestOrder();
        ObjectNode address = objectMapper.createObjectNode();
        address.put("line1", "456 Oak Ave");
        address.put("city", "Chicago");
        address.put("state", "IL");
        address.put("postalCode", "60601");
        address.put("country", "US");
        // Note: 'name' field is missing
        order.shippingAddress = address;
        order.paidAt = Instant.now();
        order.persist();

        // When
        String html =
                OrderEmailJobHandler.Templates.orderConfirmation(
                                order,
                                testCss,
                                Collections.emptyList(),
                                "https://calendar.villagecompute.com")
                        .render();

        // Then
        assertNotNull(html);
        assertTrue(
                html.contains("Thank You for Your Order"),
                "Should contain order thank you message");
        assertTrue(html.contains("456 Oak Ave"), "Should contain address line1");
        assertTrue(html.contains("Chicago"), "Should contain city");
        assertFalse(html.contains("\"null\""), "Should not contain literal 'null' text");
    }

    @Test
    @Transactional
    void testOrderConfirmationTemplateWithNullName() {
        // Given - Order with shipping address where name is explicitly null
        CalendarOrder order = createTestOrder();
        ObjectNode address = objectMapper.createObjectNode();
        address.putNull("name");
        address.put("line1", "789 Pine St");
        address.put("city", "Seattle");
        address.put("state", "WA");
        address.put("postalCode", "98101");
        address.put("country", "US");
        order.shippingAddress = address;
        order.paidAt = Instant.now();
        order.persist();

        // When
        String html =
                OrderEmailJobHandler.Templates.orderConfirmation(
                                order,
                                testCss,
                                Collections.emptyList(),
                                "https://calendar.villagecompute.com")
                        .render();

        // Then
        assertNotNull(html);
        assertTrue(
                html.contains("Thank You for Your Order"),
                "Should contain order thank you message");
        assertTrue(html.contains("789 Pine St"), "Should contain address line1");
        assertFalse(html.contains("asText"), "Should not contain template error message");
    }

    @Test
    @Transactional
    void testOrderConfirmationTemplateWithNullPaidAt() {
        // Given - Order with null paidAt (falls back to created)
        CalendarOrder order = createTestOrder();
        order.shippingAddress = createFullShippingAddress();
        order.paidAt = null;
        order.persist();

        // When
        String html =
                OrderEmailJobHandler.Templates.orderConfirmation(
                                order,
                                testCss,
                                Collections.emptyList(),
                                "https://calendar.villagecompute.com")
                        .render();

        // Then
        assertNotNull(html);
        assertTrue(
                html.contains("Thank You for Your Order"),
                "Should contain order thank you message");
        assertFalse(html.contains("\"null\""), "Should not contain literal 'null' text");
    }

    @Test
    @Transactional
    void testAdminOrderNotificationTemplateWithFullShippingAddress() {
        // Given - Order with complete shipping address
        CalendarOrder order = createTestOrder();
        order.shippingAddress = createFullShippingAddress();
        order.paidAt = Instant.now();
        order.stripePaymentIntentId = "pi_test_123456";
        order.persist();

        String baseUrl = "https://calendar.villagecompute.com";

        // When
        String html =
                OrderEmailJobHandler.Templates.adminOrderNotification(
                                order, testCss, baseUrl, Collections.emptyList())
                        .render();

        // Then
        assertNotNull(html);
        assertTrue(
                html.contains("New Order Received"), "Should contain admin notification message");
        assertTrue(html.contains(order.orderNumber), "Should contain order number");
        assertTrue(html.contains("John Doe"), "Should contain customer name");
        assertTrue(html.contains("123 Main St"), "Should contain address line1");
        assertTrue(html.contains("pi_test_123456"), "Should contain Stripe payment intent");
        assertTrue(html.contains(baseUrl + "/admin/orders"), "Should contain admin link");
    }

    @Test
    @Transactional
    void testAdminOrderNotificationTemplateWithNullShippingAddress() {
        // Given - Order with null shipping address
        CalendarOrder order = createTestOrder();
        order.shippingAddress = null;
        order.paidAt = Instant.now();
        order.persist();

        String baseUrl = "https://calendar.villagecompute.com";

        // When
        String html =
                OrderEmailJobHandler.Templates.adminOrderNotification(
                                order, testCss, baseUrl, Collections.emptyList())
                        .render();

        // Then
        assertNotNull(html);
        assertTrue(
                html.contains("New Order Received"), "Should contain admin notification message");
        assertTrue(
                html.contains("No shipping address provided"),
                "Should show placeholder for missing address");
        assertFalse(html.contains("\"null\""), "Should not contain literal 'null' text");
    }

    @Test
    @Transactional
    void testAdminOrderNotificationTemplateWithPartialShippingAddress() {
        // Given - Shipping address with only line1 and city
        CalendarOrder order = createTestOrder();
        ObjectNode address = objectMapper.createObjectNode();
        address.put("line1", "123 Partial St");
        address.put("city", "Partial City");
        order.shippingAddress = address;
        order.paidAt = Instant.now();
        order.persist();

        String baseUrl = "https://calendar.villagecompute.com";

        // When
        String html =
                OrderEmailJobHandler.Templates.adminOrderNotification(
                                order, testCss, baseUrl, Collections.emptyList())
                        .render();

        // Then
        assertNotNull(html);
        assertTrue(html.contains("123 Partial St"), "Should contain address line1");
        assertTrue(html.contains("Partial City"), "Should contain city");
        assertFalse(html.contains("\"null\""), "Should not contain literal 'null' text");
    }

    @Test
    @Transactional
    void testAdminOrderNotificationTemplateWithNullPaidAt() {
        // Given - Order with null paidAt (falls back to created)
        CalendarOrder order = createTestOrder();
        order.shippingAddress = createFullShippingAddress();
        order.paidAt = null;
        order.persist();

        String baseUrl = "https://calendar.villagecompute.com";

        // When
        String html =
                OrderEmailJobHandler.Templates.adminOrderNotification(
                                order, testCss, baseUrl, Collections.emptyList())
                        .render();

        // Then
        assertNotNull(html);
        assertTrue(
                html.contains("New Order Received"), "Should contain admin notification message");
        assertFalse(html.contains("\"null\""), "Should not contain literal 'null' text");
    }

    @Test
    @Transactional
    void testOrderConfirmationTemplateWithLine2() {
        // Given - Shipping address with line2
        CalendarOrder order = createTestOrder();
        ObjectNode address = objectMapper.createObjectNode();
        address.put("name", "Jane Smith");
        address.put("line1", "100 Business Blvd");
        address.put("line2", "Suite 500");
        address.put("city", "San Francisco");
        address.put("state", "CA");
        address.put("postalCode", "94105");
        address.put("country", "US");
        order.shippingAddress = address;
        order.paidAt = Instant.now();
        order.persist();

        // When
        String html =
                OrderEmailJobHandler.Templates.orderConfirmation(
                                order,
                                testCss,
                                Collections.emptyList(),
                                "https://calendar.villagecompute.com")
                        .render();

        // Then
        assertNotNull(html);
        assertTrue(html.contains("Jane Smith"), "Should contain customer name");
        assertTrue(html.contains("100 Business Blvd"), "Should contain address line1");
        assertTrue(html.contains("Suite 500"), "Should contain address line2");
    }

    @Test
    @Transactional
    void testOrderConfirmationTemplateWithEmptyLine2() {
        // Given - Shipping address with explicitly null line2
        CalendarOrder order = createTestOrder();
        ObjectNode address = objectMapper.createObjectNode();
        address.put("name", "Bob Wilson");
        address.put("line1", "200 Home Rd");
        address.putNull("line2");
        address.put("city", "Austin");
        address.put("state", "TX");
        address.put("postalCode", "78701");
        address.put("country", "US");
        order.shippingAddress = address;
        order.paidAt = Instant.now();
        order.persist();

        // When
        String html =
                OrderEmailJobHandler.Templates.orderConfirmation(
                                order,
                                testCss,
                                Collections.emptyList(),
                                "https://calendar.villagecompute.com")
                        .render();

        // Then
        assertNotNull(html);
        assertTrue(html.contains("Bob Wilson"), "Should contain customer name");
        assertTrue(html.contains("200 Home Rd"), "Should contain address line1");
        // line2 should not appear since it's null
        assertFalse(html.contains("\"null\""), "Should not contain literal 'null' text");
    }

    @Test
    @Transactional
    void testOrderConfirmationTemplateWithNullUser() {
        // Given - Guest checkout order with null user
        CalendarOrder order = createTestOrder();
        order.user = null; // Guest checkout - no user
        order.customerEmail = "guest@example.com";
        order.shippingAddress = createFullShippingAddress();
        order.paidAt = Instant.now();
        order.persist();

        // When
        String html =
                OrderEmailJobHandler.Templates.orderConfirmation(
                                order,
                                testCss,
                                Collections.emptyList(),
                                "https://calendar.villagecompute.com")
                        .render();

        // Then
        assertNotNull(html);
        assertTrue(
                html.contains("Thank You for Your Order"),
                "Should contain order thank you message");
        assertTrue(html.contains("guest@example.com"), "Should show customer email for guest");
        assertFalse(
                html.contains("Property") && html.contains("not found"),
                "Should not contain template error");
    }

    @Test
    @Transactional
    void testAdminOrderNotificationTemplateWithNullUser() {
        // Given - Guest checkout order with null user
        CalendarOrder order = createTestOrder();
        order.user = null; // Guest checkout - no user
        order.customerEmail = "guest@example.com";
        order.shippingAddress = createFullShippingAddress();
        order.paidAt = Instant.now();
        order.persist();

        String baseUrl = "https://calendar.villagecompute.com";

        // When
        String html =
                OrderEmailJobHandler.Templates.adminOrderNotification(
                                order, testCss, baseUrl, Collections.emptyList())
                        .render();

        // Then
        assertNotNull(html);
        assertTrue(
                html.contains("New Order Received"), "Should contain admin notification message");
        assertTrue(html.contains("guest@example.com"), "Should show customer email");
        assertFalse(
                html.contains("Property") && html.contains("not found"),
                "Should not contain template error");
    }

    @Test
    @Transactional
    void testOrderConfirmationTemplateWithNullUserAndNullCustomerEmail() {
        // Given - Edge case: both user and customerEmail are null
        CalendarOrder order = createTestOrder();
        order.user = null;
        order.customerEmail = null; // Both are null
        order.shippingAddress = createFullShippingAddress();
        order.paidAt = Instant.now();
        order.persist();

        // When
        String html =
                OrderEmailJobHandler.Templates.orderConfirmation(
                                order,
                                testCss,
                                Collections.emptyList(),
                                "https://calendar.villagecompute.com")
                        .render();

        // Then
        assertNotNull(html);
        assertTrue(
                html.contains("Thank You for Your Order"),
                "Should contain order thank you message");
        assertTrue(
                html.contains("Valued Customer"),
                "Should show fallback greeting for missing email");
    }

    @Test
    @Transactional
    void testAdminOrderNotificationTemplateWithNullUserAndNullCustomerEmail() {
        // Given - Edge case: both user and customerEmail are null
        CalendarOrder order = createTestOrder();
        order.user = null;
        order.customerEmail = null; // Both are null
        order.shippingAddress = createFullShippingAddress();
        order.paidAt = Instant.now();
        order.persist();

        String baseUrl = "https://calendar.villagecompute.com";

        // When
        String html =
                OrderEmailJobHandler.Templates.adminOrderNotification(
                                order, testCss, baseUrl, Collections.emptyList())
                        .render();

        // Then
        assertNotNull(html);
        assertTrue(
                html.contains("New Order Received"), "Should contain admin notification message");
        assertTrue(html.contains("N/A"), "Should show N/A when no email available");
    }

    // ==================== Helper Methods ====================

    private CalendarOrder createTestOrder() {
        CalendarOrder order = new CalendarOrder();
        order.user = testUser;
        order.customerEmail = "customer@test.com";
        order.subtotal = new BigDecimal("29.99");
        order.shippingCost = new BigDecimal("5.99");
        order.taxAmount = BigDecimal.ZERO;
        order.totalPrice = new BigDecimal("35.98");
        order.status = CalendarOrder.STATUS_PAID;
        order.orderNumber = "VC-TEST-" + System.currentTimeMillis();
        order.persist();

        // Add a test item
        CalendarOrderItem item = new CalendarOrderItem();
        item.order = order;
        item.productName = "Test Calendar 2025";
        item.productType = CalendarOrderItem.TYPE_PRINT;
        item.setYear(2025);
        item.quantity = 1;
        item.unitPrice = new BigDecimal("29.99");
        item.lineTotal = new BigDecimal("29.99");
        item.persist();
        order.items.add(item);

        return order;
    }

    private ObjectNode createFullShippingAddress() {
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

    private String loadResourceAsString(String resourcePath) throws IOException {
        try (var inputStream =
                Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
