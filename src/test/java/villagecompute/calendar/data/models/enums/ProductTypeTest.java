package villagecompute.calendar.data.models.enums;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Unit tests for ProductType enum. */
class ProductTypeTest {

    @Test
    void testPrintProductCode() {
        assertEquals("print", ProductType.PRINT.getProductCode());
    }

    @Test
    void testPdfProductCode() {
        assertEquals("pdf", ProductType.PDF.getProductCode());
    }

    @Test
    void testEnumValues() {
        ProductType[] values = ProductType.values();
        assertEquals(2, values.length);
        assertArrayEquals(new ProductType[] {ProductType.PRINT, ProductType.PDF}, values);
    }

    @Test
    void testValueOf() {
        assertEquals(ProductType.PRINT, ProductType.valueOf("PRINT"));
        assertEquals(ProductType.PDF, ProductType.valueOf("PDF"));
    }

    @Test
    void testValueOfInvalid() {
        assertThrows(IllegalArgumentException.class, () -> ProductType.valueOf("WALL_CALENDAR"));
        assertThrows(IllegalArgumentException.class, () -> ProductType.valueOf("DESK_CALENDAR"));
        assertThrows(IllegalArgumentException.class, () -> ProductType.valueOf("POSTER"));
    }

    @Test
    void testProductCodeMatchesProductService() {
        // These product codes must match the keys in ProductService.PRODUCTS
        assertEquals("print", ProductType.PRINT.getProductCode());
        assertEquals("pdf", ProductType.PDF.getProductCode());
    }
}
