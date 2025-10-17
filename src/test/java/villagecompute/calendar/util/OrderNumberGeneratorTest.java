package villagecompute.calendar.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OrderNumberGenerator utility.
 */
class OrderNumberGeneratorTest {

    @Test
    void testGenerateOrderNumberWithYearAndCount() {
        // Given
        int year = 2025;
        long orderCount = 0;

        // When
        String orderNumber = OrderNumberGenerator.generateOrderNumber(year, orderCount);

        // Then
        assertEquals("VC-2025-00001", orderNumber);
    }

    @Test
    void testGenerateOrderNumberSequential() {
        // Given
        int year = 2025;

        // When
        String order1 = OrderNumberGenerator.generateOrderNumber(year, 0);
        String order2 = OrderNumberGenerator.generateOrderNumber(year, 1);
        String order3 = OrderNumberGenerator.generateOrderNumber(year, 2);

        // Then
        assertEquals("VC-2025-00001", order1);
        assertEquals("VC-2025-00002", order2);
        assertEquals("VC-2025-00003", order3);
    }

    @Test
    void testGenerateOrderNumberWith100thOrder() {
        // Given
        int year = 2025;
        long orderCount = 99; // 100th order

        // When
        String orderNumber = OrderNumberGenerator.generateOrderNumber(year, orderCount);

        // Then
        assertEquals("VC-2025-00100", orderNumber);
    }

    @Test
    void testGenerateOrderNumberWith1000thOrder() {
        // Given
        int year = 2025;
        long orderCount = 999; // 1000th order

        // When
        String orderNumber = OrderNumberGenerator.generateOrderNumber(year, orderCount);

        // Then
        assertEquals("VC-2025-01000", orderNumber);
    }

    @Test
    void testGenerateOrderNumberYearRollover() {
        // Given
        int year2024 = 2024;
        int year2025 = 2025;
        long lastOrderOf2024 = 99999; // Last possible order of 2024
        long firstOrderOf2025 = 0; // First order of 2025

        // When
        String order2024 = OrderNumberGenerator.generateOrderNumber(year2024, lastOrderOf2024);
        String order2025 = OrderNumberGenerator.generateOrderNumber(year2025, firstOrderOf2025);

        // Then
        assertEquals("VC-2024-100000", order2024);
        assertEquals("VC-2025-00001", order2025); // Sequence resets for new year
    }

    @Test
    void testGenerateOrderNumberDifferentYears() {
        // Given
        long orderCount = 42;

        // When
        String order2023 = OrderNumberGenerator.generateOrderNumber(2023, orderCount);
        String order2024 = OrderNumberGenerator.generateOrderNumber(2024, orderCount);
        String order2025 = OrderNumberGenerator.generateOrderNumber(2025, orderCount);

        // Then
        assertEquals("VC-2023-00043", order2023);
        assertEquals("VC-2024-00043", order2024);
        assertEquals("VC-2025-00043", order2025);
    }

    @Test
    void testGenerateOrderNumberWithoutYearParameter() {
        // Given
        long orderCount = 5;

        // When
        String orderNumber = OrderNumberGenerator.generateOrderNumber(orderCount);

        // Then
        assertNotNull(orderNumber);
        assertTrue(orderNumber.matches("VC-\\d{4}-\\d{5}"), "Should match format VC-YYYY-NNNNN");
        assertTrue(orderNumber.contains("-00006"), "Should be 6th order (count + 1)");
    }

    @Test
    void testOrderNumberFormat() {
        // Given
        int year = 2025;
        long orderCount = 123;

        // When
        String orderNumber = OrderNumberGenerator.generateOrderNumber(year, orderCount);

        // Then
        // Verify format: VC-YYYY-NNNNN
        assertTrue(orderNumber.matches("VC-\\d{4}-\\d{5}"));
        assertTrue(orderNumber.startsWith("VC-"));
        assertEquals(13, orderNumber.length()); // VC-YYYY-NNNNN = 13 chars (2+1+4+1+5)
    }

    @Test
    void testOrderNumberZeroPadding() {
        // Test that sequence numbers are zero-padded to 5 digits
        assertEquals("VC-2025-00001", OrderNumberGenerator.generateOrderNumber(2025, 0));
        assertEquals("VC-2025-00010", OrderNumberGenerator.generateOrderNumber(2025, 9));
        assertEquals("VC-2025-00100", OrderNumberGenerator.generateOrderNumber(2025, 99));
        assertEquals("VC-2025-01000", OrderNumberGenerator.generateOrderNumber(2025, 999));
        assertEquals("VC-2025-10000", OrderNumberGenerator.generateOrderNumber(2025, 9999));
    }
}
