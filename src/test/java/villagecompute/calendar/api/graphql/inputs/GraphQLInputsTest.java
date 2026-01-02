package villagecompute.calendar.api.graphql.inputs;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import villagecompute.calendar.data.models.enums.ProductType;

/**
 * Tests for GraphQL input types.
 */
class GraphQLInputsTest {

    // ========== AddToCartInput Tests ==========

    @Test
    void testAddToCartInput_DefaultConstructor_CreatesEmptyObject() {
        AddToCartInput input = new AddToCartInput();

        assertNull(input.generatorType);
        assertNull(input.description);
        assertNull(input.assets);
        assertNull(input.quantity);
        assertNull(input.productCode);
        assertNull(input.configuration);
    }

    @Test
    void testAddToCartInput_ParameterizedConstructor_SetsAllFields() {
        AssetInput asset = new AssetInput("main", "<svg></svg>");

        AddToCartInput input = new AddToCartInput("calendar", "2026 Calendar", 2, "print", "{\"year\":2026}",
                List.of(asset));

        assertEquals("calendar", input.generatorType);
        assertEquals("2026 Calendar", input.description);
        assertEquals(2, input.quantity);
        assertEquals("print", input.productCode);
        assertEquals("{\"year\":2026}", input.configuration);
        assertEquals(1, input.assets.size());
        assertEquals("main", input.assets.get(0).assetKey);
    }

    @Test
    void testAddToCartInput_WithNullAssets_Works() {
        AddToCartInput input = new AddToCartInput("maze", "Hard Maze", 1, "pdf", "{}", null);

        assertEquals("maze", input.generatorType);
        assertNull(input.assets);
    }

    @Test
    void testAddToCartInput_WithEmptyAssets_Works() {
        AddToCartInput input = new AddToCartInput("calendar", "Test", 1, "print", "{}", List.of());

        assertTrue(input.assets.isEmpty());
    }

    // ========== AddressInput Tests ==========

    @Test
    void testAddressInput_DefaultConstructor_CreatesEmptyObject() {
        AddressInput input = new AddressInput();

        assertNull(input.street);
        assertNull(input.street2);
        assertNull(input.city);
        assertNull(input.state);
        assertNull(input.postalCode);
        assertNull(input.country);
    }

    @Test
    void testAddressInput_CanSetAllFields() {
        AddressInput input = new AddressInput();
        input.street = "123 Main St";
        input.street2 = "Apt 4B";
        input.city = "Nashville";
        input.state = "TN";
        input.postalCode = "37203";
        input.country = "US";

        assertEquals("123 Main St", input.street);
        assertEquals("Apt 4B", input.street2);
        assertEquals("Nashville", input.city);
        assertEquals("TN", input.state);
        assertEquals("37203", input.postalCode);
        assertEquals("US", input.country);
    }

    @Test
    void testAddressInput_Street2IsOptional() {
        AddressInput input = new AddressInput();
        input.street = "456 Oak Ave";
        input.city = "Austin";
        input.state = "TX";
        input.postalCode = "78701";
        input.country = "US";

        assertNull(input.street2);
        assertNotNull(input.street);
        assertNotNull(input.city);
    }

    // ========== AssetInput Tests ==========

    @Test
    void testAssetInput_DefaultConstructor_CreatesEmptyObject() {
        AssetInput input = new AssetInput();

        assertNull(input.assetKey);
        assertNull(input.svgContent);
        assertNull(input.widthInches);
        assertNull(input.heightInches);
    }

    @Test
    void testAssetInput_TwoArgConstructor_SetsKeyAndContent() {
        AssetInput input = new AssetInput("answer_key", "<svg xmlns='http://www.w3.org/2000/svg'><rect/></svg>");

        assertEquals("answer_key", input.assetKey);
        assertTrue(input.svgContent.contains("svg"));
        assertNull(input.widthInches);
        assertNull(input.heightInches);
    }

    @Test
    void testAssetInput_FullConstructor_SetsAllFields() {
        AssetInput input = new AssetInput("main", "<svg></svg>", new BigDecimal("35"), new BigDecimal("23"));

        assertEquals("main", input.assetKey);
        assertEquals("<svg></svg>", input.svgContent);
        assertEquals(new BigDecimal("35"), input.widthInches);
        assertEquals(new BigDecimal("23"), input.heightInches);
    }

    // ========== CalendarInput Tests ==========

    @Test
    void testCalendarInput_DefaultConstructor_CreatesEmptyObject() {
        CalendarInput input = new CalendarInput();

        assertNull(input.name);
        assertNull(input.year);
        assertNull(input.templateId);
        assertNull(input.configuration);
        assertNull(input.isPublic);
    }

    @Test
    void testCalendarInput_ParameterizedConstructor_SetsAllFields() {
        CalendarInput input = new CalendarInput("My 2026 Calendar", 2026, "template-1", null, true);

        assertEquals("My 2026 Calendar", input.name);
        assertEquals(2026, input.year);
        assertEquals("template-1", input.templateId);
        assertNull(input.configuration);
        assertTrue(input.isPublic);
    }

    @Test
    void testCalendarInput_CanSetFields() {
        CalendarInput input = new CalendarInput();
        input.name = "Test Calendar";
        input.year = 2027;
        input.templateId = "my-template";
        input.isPublic = false;

        assertEquals("Test Calendar", input.name);
        assertEquals(2027, input.year);
        assertEquals("my-template", input.templateId);
        assertFalse(input.isPublic);
    }

    // ========== CalendarUpdateInput Tests ==========

    @Test
    void testCalendarUpdateInput_DefaultConstructor_CreatesEmptyObject() {
        CalendarUpdateInput input = new CalendarUpdateInput();

        assertNull(input.name);
        assertNull(input.configuration);
        assertNull(input.isPublic);
    }

    @Test
    void testCalendarUpdateInput_ParameterizedConstructor_SetsAllFields() {
        CalendarUpdateInput input = new CalendarUpdateInput("Updated Name", null, true);

        assertEquals("Updated Name", input.name);
        assertNull(input.configuration);
        assertTrue(input.isPublic);
    }

    @Test
    void testCalendarUpdateInput_HasChanges_WithNoChanges_ReturnsFalse() {
        CalendarUpdateInput input = new CalendarUpdateInput();

        assertFalse(input.hasChanges());
    }

    @Test
    void testCalendarUpdateInput_HasChanges_WithName_ReturnsTrue() {
        CalendarUpdateInput input = new CalendarUpdateInput("New Name", null, null);

        assertTrue(input.hasChanges());
    }

    @Test
    void testCalendarUpdateInput_HasChanges_WithIsPublic_ReturnsTrue() {
        CalendarUpdateInput input = new CalendarUpdateInput(null, null, true);

        assertTrue(input.hasChanges());
    }

    // ========== OrderInput Tests ==========

    @Test
    void testOrderInput_DefaultConstructor_CreatesEmptyObject() {
        OrderInput input = new OrderInput();

        assertNull(input.calendarId);
        assertNull(input.shippingAddress);
        assertNull(input.quantity);
    }

    @Test
    void testOrderInput_CanSetFields() {
        OrderInput input = new OrderInput();
        input.calendarId = "550e8400-e29b-41d4-a716-446655440000";
        input.quantity = 3;

        AddressInput address = new AddressInput();
        address.street = "789 Elm St";
        address.city = "Seattle";
        address.state = "WA";
        address.postalCode = "98101";
        address.country = "US";
        input.shippingAddress = address;

        assertEquals("550e8400-e29b-41d4-a716-446655440000", input.calendarId);
        assertEquals(3, input.quantity);
        assertEquals("Seattle", input.shippingAddress.city);
    }

    // ========== OrderStatusUpdateInput Tests ==========

    @Test
    void testOrderStatusUpdateInput_DefaultConstructor_CreatesEmptyObject() {
        OrderStatusUpdateInput input = new OrderStatusUpdateInput();

        assertNull(input.orderId);
        assertNull(input.status);
        assertNull(input.notes);
    }

    @Test
    void testOrderStatusUpdateInput_CanSetFields() {
        OrderStatusUpdateInput input = new OrderStatusUpdateInput();
        input.orderId = "order-123";
        input.status = "SHIPPED";
        input.notes = "Shipped via UPS, tracking: 1Z999AA10123456784";

        assertEquals("order-123", input.orderId);
        assertEquals("SHIPPED", input.status);
        assertTrue(input.notes.contains("UPS"));
    }

    // ========== PlaceOrderInput Tests ==========

    @Test
    void testPlaceOrderInput_DefaultConstructor_CreatesEmptyObject() {
        PlaceOrderInput input = new PlaceOrderInput();

        assertNull(input.calendarId);
        assertNull(input.productType);
        assertNull(input.quantity);
        assertNull(input.shippingAddress);
    }

    @Test
    void testPlaceOrderInput_CanSetFields() {
        PlaceOrderInput input = new PlaceOrderInput();
        input.calendarId = "cal-456";
        input.productType = ProductType.PRINT;
        input.quantity = 2;

        AddressInput address = new AddressInput();
        address.street = "100 Market St";
        address.city = "San Francisco";
        address.state = "CA";
        address.postalCode = "94102";
        address.country = "US";
        input.shippingAddress = address;

        assertEquals("cal-456", input.calendarId);
        assertEquals(ProductType.PRINT, input.productType);
        assertEquals(2, input.quantity);
        assertEquals("San Francisco", input.shippingAddress.city);
    }

    @Test
    void testPlaceOrderInput_WithPdfProductType() {
        PlaceOrderInput input = new PlaceOrderInput();
        input.calendarId = "cal-789";
        input.productType = ProductType.PDF;
        input.quantity = 1;

        assertEquals(ProductType.PDF, input.productType);
    }

    // ========== TemplateInput Tests ==========

    @Test
    void testTemplateInput_DefaultConstructor_CreatesEmptyObject() {
        TemplateInput input = new TemplateInput();

        assertNull(input.name);
        assertNull(input.description);
        assertNull(input.configuration);
        assertNull(input.thumbnailUrl);
        assertNull(input.isActive);
        assertNull(input.isFeatured);
        assertNull(input.displayOrder);
        assertNull(input.previewSvg);
    }

    @Test
    void testTemplateInput_ParameterizedConstructor_SetsAllFields() {
        TemplateInput input = new TemplateInput("Nature Template", "A beautiful nature template",
                "{\"theme\":\"nature\"}", "https://example.com/thumb.jpg", true, true, 1, "<svg></svg>");

        assertEquals("Nature Template", input.name);
        assertEquals("A beautiful nature template", input.description);
        assertEquals("{\"theme\":\"nature\"}", input.configuration);
        assertEquals("https://example.com/thumb.jpg", input.thumbnailUrl);
        assertTrue(input.isActive);
        assertTrue(input.isFeatured);
        assertEquals(1, input.displayOrder);
        assertEquals("<svg></svg>", input.previewSvg);
    }

    @Test
    void testTemplateInput_CanSetMinimalFields() {
        TemplateInput input = new TemplateInput();
        input.name = "Minimal Template";
        input.configuration = "{}";

        assertEquals("Minimal Template", input.name);
        assertEquals("{}", input.configuration);
        assertNull(input.description);
        assertNull(input.isActive);
    }
}
