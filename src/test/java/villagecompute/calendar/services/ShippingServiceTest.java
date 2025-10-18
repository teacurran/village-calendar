package villagecompute.calendar.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import villagecompute.calendar.data.models.CalendarOrder;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ShippingService.
 * Tests shipping cost calculation for domestic and international addresses.
 */
@QuarkusTest
class ShippingServiceTest {

    @Inject
    ShippingService shippingService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Test that domestic US addresses receive the standard shipping rate ($5.99).
     */
    @Test
    void testDomesticUSAddress_ReturnsStandardRate() throws Exception {
        // Create a US shipping address
        JsonNode usAddress = objectMapper.readTree(
            "{\"country\":\"US\",\"state\":\"TN\",\"city\":\"Nashville\"," +
            "\"street\":\"123 Main St\",\"postalCode\":\"37201\"}"
        );

        // Create a mock order with the address
        CalendarOrder order = new CalendarOrder();
        order.shippingAddress = usAddress;

        // Calculate shipping cost
        BigDecimal shippingCost = shippingService.calculateShippingCost(order);

        // Verify the cost is the expected domestic standard rate
        assertEquals(new BigDecimal("5.99"), shippingCost,
            "Domestic US shipping should be $5.99");
    }

    /**
     * Test that international addresses throw an exception (not supported in MVP).
     */
    @Test
    void testInternationalAddress_ThrowsException() throws Exception {
        // Create a Canadian address
        JsonNode canadianAddress = objectMapper.readTree(
            "{\"country\":\"CA\",\"state\":\"ON\",\"city\":\"Toronto\"," +
            "\"street\":\"456 King St\",\"postalCode\":\"M5H 1A1\"}"
        );

        CalendarOrder order = new CalendarOrder();
        order.shippingAddress = canadianAddress;

        // Verify that international shipping throws IllegalStateException
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> shippingService.calculateShippingCost(order),
            "International shipping should throw IllegalStateException"
        );

        // Verify the error message is informative
        assertTrue(exception.getMessage().contains("International shipping"),
            "Error message should mention international shipping");
        assertTrue(exception.getMessage().contains("CA"),
            "Error message should mention the country code");
    }

    /**
     * Test that UK addresses are rejected (international).
     */
    @Test
    void testUKAddress_ThrowsException() throws Exception {
        JsonNode ukAddress = objectMapper.readTree(
            "{\"country\":\"GB\",\"state\":\"\",\"city\":\"London\"," +
            "\"street\":\"10 Downing Street\",\"postalCode\":\"SW1A 2AA\"}"
        );

        CalendarOrder order = new CalendarOrder();
        order.shippingAddress = ukAddress;

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> shippingService.calculateShippingCost(order)
        );

        assertTrue(exception.getMessage().contains("GB"));
    }

    /**
     * Test that null shipping address throws IllegalArgumentException.
     */
    @Test
    void testNullShippingAddress_ThrowsException() {
        CalendarOrder order = new CalendarOrder();
        order.shippingAddress = null;

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> shippingService.calculateShippingCost(order),
            "Null shipping address should throw IllegalArgumentException"
        );

        assertTrue(exception.getMessage().contains("required"),
            "Error message should indicate address is required");
    }

    /**
     * Test that missing country field throws IllegalArgumentException.
     */
    @Test
    void testMissingCountryField_ThrowsException() throws Exception {
        // Create address without country field
        JsonNode addressWithoutCountry = objectMapper.readTree(
            "{\"state\":\"TN\",\"city\":\"Nashville\"," +
            "\"street\":\"123 Main St\",\"postalCode\":\"37201\"}"
        );

        CalendarOrder order = new CalendarOrder();
        order.shippingAddress = addressWithoutCountry;

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> shippingService.calculateShippingCost(order),
            "Missing country field should throw IllegalArgumentException"
        );

        assertTrue(exception.getMessage().contains("Country"),
            "Error message should mention country field");
    }

    /**
     * Test that null country field throws IllegalArgumentException.
     */
    @Test
    void testNullCountryField_ThrowsException() throws Exception {
        // Create address with null country
        JsonNode addressWithNullCountry = objectMapper.readTree(
            "{\"country\":null,\"state\":\"TN\",\"city\":\"Nashville\"," +
            "\"street\":\"123 Main St\",\"postalCode\":\"37201\"}"
        );

        CalendarOrder order = new CalendarOrder();
        order.shippingAddress = addressWithNullCountry;

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> shippingService.calculateShippingCost(order)
        );

        assertTrue(exception.getMessage().contains("Country"));
    }

    /**
     * Test that empty country string throws IllegalArgumentException.
     */
    @Test
    void testEmptyCountryString_ThrowsException() throws Exception {
        // Create address with empty country
        JsonNode addressWithEmptyCountry = objectMapper.readTree(
            "{\"country\":\"\",\"state\":\"TN\",\"city\":\"Nashville\"," +
            "\"street\":\"123 Main St\",\"postalCode\":\"37201\"}"
        );

        CalendarOrder order = new CalendarOrder();
        order.shippingAddress = addressWithEmptyCountry;

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> shippingService.calculateShippingCost(order)
        );

        assertTrue(exception.getMessage().contains("cannot be empty"));
    }

    /**
     * Test that country code normalization works (lowercase should work).
     */
    @Test
    void testCountryCodeNormalization_LowercaseUS() throws Exception {
        // Create address with lowercase country code
        JsonNode addressLowercase = objectMapper.readTree(
            "{\"country\":\"us\",\"state\":\"CA\",\"city\":\"Los Angeles\"," +
            "\"street\":\"789 Sunset Blvd\",\"postalCode\":\"90028\"}"
        );

        CalendarOrder order = new CalendarOrder();
        order.shippingAddress = addressLowercase;

        BigDecimal shippingCost = shippingService.calculateShippingCost(order);

        assertEquals(new BigDecimal("5.99"), shippingCost,
            "Lowercase country code should be normalized to uppercase");
    }

    /**
     * Test that country code normalization works (mixed case should work).
     */
    @Test
    void testCountryCodeNormalization_MixedCase() throws Exception {
        JsonNode addressMixedCase = objectMapper.readTree(
            "{\"country\":\"Us\",\"state\":\"TX\",\"city\":\"Austin\"," +
            "\"street\":\"100 Congress Ave\",\"postalCode\":\"78701\"}"
        );

        CalendarOrder order = new CalendarOrder();
        order.shippingAddress = addressMixedCase;

        BigDecimal shippingCost = shippingService.calculateShippingCost(order);

        assertEquals(new BigDecimal("5.99"), shippingCost);
    }

    /**
     * Test that whitespace in country code is trimmed properly.
     */
    @Test
    void testCountryCodeWithWhitespace_IsTrimmed() throws Exception {
        JsonNode addressWithWhitespace = objectMapper.readTree(
            "{\"country\":\" US \",\"state\":\"FL\",\"city\":\"Miami\"," +
            "\"street\":\"200 Ocean Dr\",\"postalCode\":\"33139\"}"
        );

        CalendarOrder order = new CalendarOrder();
        order.shippingAddress = addressWithWhitespace;

        BigDecimal shippingCost = shippingService.calculateShippingCost(order);

        assertEquals(new BigDecimal("5.99"), shippingCost,
            "Whitespace in country code should be trimmed");
    }

    /**
     * Test calculateRate() convenience method with US address.
     */
    @Test
    void testCalculateRate_USAddress_ReturnsStandardRate() throws Exception {
        JsonNode usAddress = objectMapper.readTree(
            "{\"country\":\"US\",\"state\":\"NY\",\"city\":\"New York\"," +
            "\"street\":\"123 Broadway\",\"postalCode\":\"10001\"}"
        );

        BigDecimal rate = shippingService.calculateRate(usAddress);

        assertEquals(new BigDecimal("5.99"), rate,
            "calculateRate() should return standard rate for US addresses");
    }

    /**
     * Test calculateRate() convenience method with international address.
     */
    @Test
    void testCalculateRate_InternationalAddress_ThrowsException() throws Exception {
        JsonNode mexicoAddress = objectMapper.readTree(
            "{\"country\":\"MX\",\"state\":\"CDMX\",\"city\":\"Mexico City\"," +
            "\"street\":\"Reforma 100\",\"postalCode\":\"06600\"}"
        );

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> shippingService.calculateRate(mexicoAddress)
        );

        assertTrue(exception.getMessage().contains("MX"));
    }

    /**
     * Test calculateRate() with null address.
     */
    @Test
    void testCalculateRate_NullAddress_ThrowsException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> shippingService.calculateRate(null)
        );

        assertTrue(exception.getMessage().contains("required"));
    }

    /**
     * Test various US state codes to ensure they all get the same rate.
     */
    @Test
    void testDifferentUSStates_SameRate() throws Exception {
        String[] states = {"CA", "TX", "FL", "NY", "WA", "IL", "PA", "OH"};

        for (String state : states) {
            JsonNode address = objectMapper.readTree(
                String.format("{\"country\":\"US\",\"state\":\"%s\",\"city\":\"Test City\"," +
                    "\"street\":\"123 Test St\",\"postalCode\":\"12345\"}", state)
            );

            CalendarOrder order = new CalendarOrder();
            order.shippingAddress = address;

            BigDecimal shippingCost = shippingService.calculateShippingCost(order);

            assertEquals(new BigDecimal("5.99"), shippingCost,
                String.format("State %s should have standard rate", state));
        }
    }

    /**
     * Test that address with all fields populated works correctly.
     */
    @Test
    void testCompleteAddress_WithAllFields() throws Exception {
        JsonNode completeAddress = objectMapper.readTree(
            "{\"country\":\"US\",\"state\":\"MA\",\"city\":\"Boston\"," +
            "\"street\":\"100 Cambridge St\",\"street2\":\"Suite 200\"," +
            "\"postalCode\":\"02114\"}"
        );

        CalendarOrder order = new CalendarOrder();
        order.shippingAddress = completeAddress;

        BigDecimal shippingCost = shippingService.calculateShippingCost(order);

        assertEquals(new BigDecimal("5.99"), shippingCost,
            "Complete address with all fields should work correctly");
    }

    /**
     * Test that address without optional street2 field works.
     */
    @Test
    void testAddressWithoutOptionalFields() throws Exception {
        JsonNode minimalAddress = objectMapper.readTree(
            "{\"country\":\"US\",\"state\":\"WA\",\"city\":\"Seattle\"," +
            "\"street\":\"400 Broad St\",\"postalCode\":\"98109\"}"
        );

        CalendarOrder order = new CalendarOrder();
        order.shippingAddress = minimalAddress;

        BigDecimal shippingCost = shippingService.calculateShippingCost(order);

        assertEquals(new BigDecimal("5.99"), shippingCost,
            "Address without optional fields should work");
    }
}
