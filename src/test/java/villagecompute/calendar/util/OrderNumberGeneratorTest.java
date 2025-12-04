package villagecompute.calendar.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OrderNumberGenerator utility.
 * Order numbers follow format VC-XXXX-XXXX where X is alphanumeric (non-guessable).
 */
class OrderNumberGeneratorTest {

    // Pattern for new secure format: VC-XXXX-XXXX (12 chars total)
    // Uses chars: 23456789ABCDEFGHJKLMNPQRSTUVWXYZ (no 0, 1, I, O, l)
    private static final Pattern ORDER_NUMBER_PATTERN =
        Pattern.compile("^VC-[23456789A-HJ-NP-Z]{4}-[23456789A-HJ-NP-Z]{4}$");

    @Test
    void testGenerateSecureOrderNumber() {
        // When
        String orderNumber = OrderNumberGenerator.generateSecureOrderNumber();

        // Then
        assertNotNull(orderNumber);
        assertTrue(ORDER_NUMBER_PATTERN.matcher(orderNumber).matches(),
            "Order number should match format VC-XXXX-XXXX, got: " + orderNumber);
        assertEquals(12, orderNumber.length()); // VC-XXXX-XXXX = 2+1+4+1+4 = 12
    }

    @Test
    void testGenerateOrderNumberWithYearAndCount() {
        // Year and count are ignored in secure implementation (kept for API compatibility)
        String orderNumber = OrderNumberGenerator.generateOrderNumber(2025, 0);

        assertNotNull(orderNumber);
        assertTrue(ORDER_NUMBER_PATTERN.matcher(orderNumber).matches(),
            "Order number should match format VC-XXXX-XXXX, got: " + orderNumber);
    }

    @Test
    void testGenerateOrderNumberWithCountOnly() {
        // Count is ignored in secure implementation (kept for API compatibility)
        String orderNumber = OrderNumberGenerator.generateOrderNumber(5);

        assertNotNull(orderNumber);
        assertTrue(ORDER_NUMBER_PATTERN.matcher(orderNumber).matches(),
            "Order number should match format VC-XXXX-XXXX, got: " + orderNumber);
    }

    @Test
    void testOrderNumbersAreUnique() {
        // Generate many order numbers and verify uniqueness
        Set<String> orderNumbers = new HashSet<>();
        int count = 1000;

        for (int i = 0; i < count; i++) {
            String orderNumber = OrderNumberGenerator.generateSecureOrderNumber();
            assertTrue(orderNumbers.add(orderNumber),
                "Duplicate order number generated: " + orderNumber);
        }

        assertEquals(count, orderNumbers.size());
    }

    @Test
    void testOrderNumberStartsWithVC() {
        String orderNumber = OrderNumberGenerator.generateSecureOrderNumber();
        assertTrue(orderNumber.startsWith("VC-"));
    }

    @Test
    void testOrderNumberHasCorrectStructure() {
        String orderNumber = OrderNumberGenerator.generateSecureOrderNumber();

        String[] parts = orderNumber.split("-");
        assertEquals(3, parts.length, "Should have 3 parts separated by dashes");
        assertEquals("VC", parts[0], "First part should be VC");
        assertEquals(4, parts[1].length(), "Second part should be 4 chars");
        assertEquals(4, parts[2].length(), "Third part should be 4 chars");
    }

    @Test
    void testOrderNumberDoesNotContainConfusingCharacters() {
        // Generate many and check none contain 0, O, I, l, 1
        for (int i = 0; i < 100; i++) {
            String orderNumber = OrderNumberGenerator.generateSecureOrderNumber();
            String randomPart = orderNumber.substring(3); // Remove "VC-"

            assertFalse(randomPart.contains("0"), "Should not contain 0");
            assertFalse(randomPart.contains("O"), "Should not contain O");
            assertFalse(randomPart.contains("I"), "Should not contain I");
            assertFalse(randomPart.contains("l"), "Should not contain l");
            assertFalse(randomPart.contains("1"), "Should not contain 1");
        }
    }

    @Test
    void testOrderNumberIsNonGuessable() {
        // Generate two consecutive order numbers - they should be completely different
        String order1 = OrderNumberGenerator.generateSecureOrderNumber();
        String order2 = OrderNumberGenerator.generateSecureOrderNumber();

        assertNotEquals(order1, order2, "Consecutive order numbers should be different");

        // The random parts should be completely different (not sequential)
        String random1 = order1.substring(3);
        String random2 = order2.substring(3);
        assertNotEquals(random1, random2);
    }

    @Test
    void testLegacyMethodsStillWork() {
        // Ensure the legacy method signatures still work (for backwards compatibility)
        String order1 = OrderNumberGenerator.generateOrderNumber(2025, 100);
        String order2 = OrderNumberGenerator.generateOrderNumber(50);

        assertNotNull(order1);
        assertNotNull(order2);
        assertTrue(ORDER_NUMBER_PATTERN.matcher(order1).matches());
        assertTrue(ORDER_NUMBER_PATTERN.matcher(order2).matches());
    }
}
