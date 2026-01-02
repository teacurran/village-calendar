package villagecompute.calendar.data.models.enums;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * Tests for enum types in the data models package.
 */
class EnumsTest {

    // ========== CalendarStatus Tests ==========

    @Test
    void testCalendarStatus_HasExpectedValues() {
        assertEquals(4, CalendarStatus.values().length);
        assertNotNull(CalendarStatus.DRAFT);
        assertNotNull(CalendarStatus.GENERATING);
        assertNotNull(CalendarStatus.READY);
        assertNotNull(CalendarStatus.FAILED);
    }

    @Test
    void testCalendarStatus_ValueOf_ReturnsCorrectEnum() {
        assertEquals(CalendarStatus.DRAFT, CalendarStatus.valueOf("DRAFT"));
        assertEquals(CalendarStatus.GENERATING, CalendarStatus.valueOf("GENERATING"));
        assertEquals(CalendarStatus.READY, CalendarStatus.valueOf("READY"));
        assertEquals(CalendarStatus.FAILED, CalendarStatus.valueOf("FAILED"));
    }

    @Test
    void testCalendarStatus_Ordinal_MatchesDefinitionOrder() {
        assertEquals(0, CalendarStatus.DRAFT.ordinal());
        assertEquals(1, CalendarStatus.GENERATING.ordinal());
        assertEquals(2, CalendarStatus.READY.ordinal());
        assertEquals(3, CalendarStatus.FAILED.ordinal());
    }

    // ========== MazeType Tests ==========

    @Test
    void testMazeType_HasExpectedValues() {
        assertEquals(4, MazeType.values().length);
        assertNotNull(MazeType.ORTHOGONAL);
        assertNotNull(MazeType.DELTA);
        assertNotNull(MazeType.SIGMA);
        assertNotNull(MazeType.THETA);
    }

    @Test
    void testMazeType_ValueOf_ReturnsCorrectEnum() {
        assertEquals(MazeType.ORTHOGONAL, MazeType.valueOf("ORTHOGONAL"));
        assertEquals(MazeType.DELTA, MazeType.valueOf("DELTA"));
        assertEquals(MazeType.SIGMA, MazeType.valueOf("SIGMA"));
        assertEquals(MazeType.THETA, MazeType.valueOf("THETA"));
    }

    @Test
    void testMazeType_Name_ReturnsEnumName() {
        assertEquals("ORTHOGONAL", MazeType.ORTHOGONAL.name());
        assertEquals("DELTA", MazeType.DELTA.name());
        assertEquals("SIGMA", MazeType.SIGMA.name());
        assertEquals("THETA", MazeType.THETA.name());
    }

    // ========== OAuthProvider Tests ==========

    @Test
    void testOAuthProvider_HasExpectedValues() {
        assertEquals(2, OAuthProvider.values().length);
        assertNotNull(OAuthProvider.GOOGLE);
        assertNotNull(OAuthProvider.FACEBOOK);
    }

    @Test
    void testOAuthProvider_ValueOf_ReturnsCorrectEnum() {
        assertEquals(OAuthProvider.GOOGLE, OAuthProvider.valueOf("GOOGLE"));
        assertEquals(OAuthProvider.FACEBOOK, OAuthProvider.valueOf("FACEBOOK"));
    }

    // ========== OrderStatus Tests ==========

    @Test
    void testOrderStatus_HasExpectedValues() {
        assertEquals(8, OrderStatus.values().length);
        assertNotNull(OrderStatus.PENDING);
        assertNotNull(OrderStatus.PAID);
        assertNotNull(OrderStatus.PROCESSING);
        assertNotNull(OrderStatus.PRINTED);
        assertNotNull(OrderStatus.SHIPPED);
        assertNotNull(OrderStatus.DELIVERED);
        assertNotNull(OrderStatus.CANCELLED);
        assertNotNull(OrderStatus.REFUNDED);
    }

    @Test
    void testOrderStatus_ValueOf_ReturnsCorrectEnum() {
        assertEquals(OrderStatus.PENDING, OrderStatus.valueOf("PENDING"));
        assertEquals(OrderStatus.PAID, OrderStatus.valueOf("PAID"));
        assertEquals(OrderStatus.PROCESSING, OrderStatus.valueOf("PROCESSING"));
        assertEquals(OrderStatus.PRINTED, OrderStatus.valueOf("PRINTED"));
        assertEquals(OrderStatus.SHIPPED, OrderStatus.valueOf("SHIPPED"));
        assertEquals(OrderStatus.DELIVERED, OrderStatus.valueOf("DELIVERED"));
        assertEquals(OrderStatus.CANCELLED, OrderStatus.valueOf("CANCELLED"));
        assertEquals(OrderStatus.REFUNDED, OrderStatus.valueOf("REFUNDED"));
    }

    @Test
    void testOrderStatus_Ordinal_MatchesLifecycleOrder() {
        // PENDING is first (0)
        assertEquals(0, OrderStatus.PENDING.ordinal());
        // PAID follows (1)
        assertEquals(1, OrderStatus.PAID.ordinal());
        // Terminal states are at the end
        assertTrue(OrderStatus.CANCELLED.ordinal() > OrderStatus.PAID.ordinal());
        assertTrue(OrderStatus.REFUNDED.ordinal() > OrderStatus.PAID.ordinal());
    }

    // ========== ProductType Tests ==========

    @Test
    void testProductType_HasExpectedValues() {
        assertEquals(2, ProductType.values().length);
        assertNotNull(ProductType.PRINT);
        assertNotNull(ProductType.PDF);
    }

    @Test
    void testProductType_ValueOf_ReturnsCorrectEnum() {
        assertEquals(ProductType.PRINT, ProductType.valueOf("PRINT"));
        assertEquals(ProductType.PDF, ProductType.valueOf("PDF"));
    }

    @Test
    void testProductType_GetProductCode_ReturnsCorrectCode() {
        assertEquals("print", ProductType.PRINT.getProductCode());
        assertEquals("pdf", ProductType.PDF.getProductCode());
    }

    @Test
    void testProductType_ProductCodes_AreLowercase() {
        for (ProductType type : ProductType.values()) {
            String code = type.getProductCode();
            assertEquals(code.toLowerCase(), code, "Product code should be lowercase");
        }
    }

    @Test
    void testProductType_ProductCodes_AreUnique() {
        String[] codes = Arrays.stream(ProductType.values()).map(ProductType::getProductCode).toArray(String[]::new);
        long uniqueCount = Arrays.stream(codes).distinct().count();
        assertEquals(codes.length, uniqueCount, "All product codes should be unique");
    }

    // ========== valueOf Invalid Values Tests ==========

    @Test
    void testCalendarStatus_ValueOf_InvalidValue_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> CalendarStatus.valueOf("INVALID"));
    }

    @Test
    void testMazeType_ValueOf_InvalidValue_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> MazeType.valueOf("INVALID"));
    }

    @Test
    void testOAuthProvider_ValueOf_InvalidValue_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> OAuthProvider.valueOf("INVALID"));
    }

    @Test
    void testOrderStatus_ValueOf_InvalidValue_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> OrderStatus.valueOf("INVALID"));
    }

    @Test
    void testProductType_ValueOf_InvalidValue_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> ProductType.valueOf("INVALID"));
    }
}
