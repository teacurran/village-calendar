package villagecompute.calendar.data.models;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Year;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Unit tests for CalendarOrderItem. Tests the year getter/setter methods that work with configuration JSON.
 */
class CalendarOrderItemTest {

    private ObjectMapper objectMapper;
    private CalendarOrderItem item;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        item = new CalendarOrderItem();
    }

    // ============================================================================
    // getYear() TESTS
    // ============================================================================

    @Test
    void testGetYear_WithYearInConfiguration_ReturnsConfigYear() {
        // Given
        ObjectNode config = objectMapper.createObjectNode();
        config.put("year", 2025);
        item.configuration = config;

        // When
        int year = item.getYear();

        // Then
        assertEquals(2025, year);
    }

    @Test
    void testGetYear_WithNoConfiguration_FallsBackToDeprecatedField() {
        // Given
        item.configuration = null;
        item.calendarYear = 2024;

        // When
        int year = item.getYear();

        // Then
        assertEquals(2024, year);
    }

    @Test
    void testGetYear_WithEmptyConfiguration_FallsBackToDeprecatedField() {
        // Given
        item.configuration = objectMapper.createObjectNode(); // Empty config, no year
        item.calendarYear = 2023;

        // When
        int year = item.getYear();

        // Then
        assertEquals(2023, year);
    }

    @Test
    void testGetYear_WithNeitherConfigNorDeprecatedField_ReturnsCurrentYear() {
        // Given
        item.configuration = null;

        // When
        int year = item.getYear();

        // Then
        assertEquals(Year.now().getValue(), year);
    }

    @Test
    void testGetYear_ConfigurationTakesPrecedenceOverDeprecatedField() {
        // Given - both are set, configuration should win
        ObjectNode config = objectMapper.createObjectNode();
        config.put("year", 2026);
        item.configuration = config;

        // When
        int year = item.getYear();

        // Then
        assertEquals(2026, year);
    }

    // ============================================================================
    // setYear() TESTS
    // ============================================================================

    @Test
    void testSetYear_SetsYearInConfiguration() {
        // Given
        item.configuration = objectMapper.createObjectNode();

        // When
        item.setYear(2025);

        // Then
        assertTrue(item.configuration.has("year"));
        assertEquals(2025, item.configuration.get("year").asInt());
    }

    @Test
    void testSetYear_AlsoSetsDeprecatedFieldForBackwardCompatibility() {
        // Given
        item.configuration = objectMapper.createObjectNode();

        // When
        item.setYear(2025);

        // Then
        assertEquals(2025, item.calendarYear);
    }

    @Test
    void testSetYear_CreatesConfigurationIfNull() {
        // Given
        item.configuration = null;

        // When
        item.setYear(2025);

        // Then
        assertNotNull(item.configuration);
        assertEquals(2025, item.configuration.get("year").asInt());
        assertEquals(2025, item.calendarYear);
    }

    @Test
    void testSetYear_PreservesOtherConfigurationFields() {
        // Given
        ObjectNode config = objectMapper.createObjectNode();
        config.put("theme", "modern");
        config.put("year", 2024);
        item.configuration = config;

        // When
        item.setYear(2025);

        // Then
        assertEquals(2025, item.configuration.get("year").asInt());
        assertEquals("modern", item.configuration.get("theme").asText());
    }

    @Test
    void testSetYear_ThenGetYear_ReturnsSetValue() {
        // Given
        item.configuration = objectMapper.createObjectNode();

        // When
        item.setYear(2027);

        // Then
        assertEquals(2027, item.getYear());
    }
}
