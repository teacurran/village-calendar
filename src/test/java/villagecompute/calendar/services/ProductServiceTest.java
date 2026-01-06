package villagecompute.calendar.services;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/** Unit tests for ProductService. Tests product catalog, pricing, and validation logic. */
@QuarkusTest
class ProductServiceTest {

    @Inject
    ProductService productService;

    // ========== GET ALL PRODUCTS TESTS ==========

    @Test
    void testGetAllProducts_ReturnsAllProducts() {
        // When
        List<ProductService.Product> products = productService.getAllProducts();

        // Then
        assertNotNull(products);
        assertFalse(products.isEmpty());
        assertEquals(2, products.size()); // print and pdf
    }

    @Test
    void testGetAllProducts_SortedByDisplayOrder() {
        // When
        List<ProductService.Product> products = productService.getAllProducts();

        // Then
        assertEquals("print", products.get(0).code); // displayOrder 1
        assertEquals("pdf", products.get(1).code); // displayOrder 2
    }

    @Test
    void testGetAllProducts_ContainsExpectedFields() {
        // When
        List<ProductService.Product> products = productService.getAllProducts();

        // Then - verify all products have required fields populated
        for (ProductService.Product product : products) {
            assertNotNull(product.code, "Product code should not be null");
            assertNotNull(product.name, "Product name should not be null");
            assertNotNull(product.description, "Product description should not be null");
            assertNotNull(product.price, "Product price should not be null");
            assertNotNull(product.features, "Product features should not be null");
            assertNotNull(product.icon, "Product icon should not be null");
            assertTrue(product.displayOrder > 0, "Display order should be positive");
        }
    }

    // ========== GET PRODUCT TESTS ==========

    @Test
    void testGetProduct_ValidCode_ReturnsProduct() {
        // When
        Optional<ProductService.Product> product = productService.getProduct(ProductService.PRODUCT_CODE_PRINT);

        // Then
        assertTrue(product.isPresent());
        assertEquals("print", product.get().code);
        assertEquals(new BigDecimal("25.00"), product.get().price);
    }

    @Test
    void testGetProduct_PrintProduct_HasCorrectDetails() {
        // When
        Optional<ProductService.Product> product = productService.getProduct(ProductService.PRODUCT_CODE_PRINT);

        // Then
        assertTrue(product.isPresent());
        ProductService.Product print = product.get();
        assertEquals("print", print.code);
        assertTrue(print.name.contains("Poster"));
        assertNotNull(print.badge); // Print has "Most Popular" badge
        assertEquals("pi-print", print.icon);
        assertFalse(print.features.isEmpty());
    }

    @Test
    void testGetProduct_PdfProduct_HasCorrectDetails() {
        // When
        Optional<ProductService.Product> product = productService.getProduct(ProductService.PRODUCT_CODE_PDF);

        // Then
        assertTrue(product.isPresent());
        ProductService.Product pdf = product.get();
        assertEquals("pdf", pdf.code);
        assertTrue(pdf.name.contains("PDF") || pdf.name.contains("Digital"));
        assertNull(pdf.badge); // PDF has no badge
        assertEquals("pi-file-pdf", pdf.icon);
        assertFalse(pdf.features.isEmpty());
    }

    @Test
    void testGetProduct_InvalidCode_ReturnsEmpty() {
        // When
        Optional<ProductService.Product> product = productService.getProduct("nonexistent");

        // Then
        assertFalse(product.isPresent());
    }

    @Test
    void testGetProduct_NullCode_ReturnsEmpty() {
        // When
        Optional<ProductService.Product> product = productService.getProduct(null);

        // Then
        assertFalse(product.isPresent());
    }

    @Test
    void testGetProduct_EmptyCode_ReturnsEmpty() {
        // When
        Optional<ProductService.Product> product = productService.getProduct("");

        // Then
        assertFalse(product.isPresent());
    }

    // ========== GET PRICE TESTS ==========

    @Test
    void testGetPrice_ValidPrintCode_ReturnsPrice() {
        // When
        BigDecimal price = productService.getPrice(ProductService.PRODUCT_CODE_PRINT);

        // Then
        assertEquals(new BigDecimal("25.00"), price);
    }

    @Test
    void testGetPrice_ValidPdfCode_ReturnsPrice() {
        // When
        BigDecimal price = productService.getPrice(ProductService.PRODUCT_CODE_PDF);

        // Then
        assertEquals(new BigDecimal("5.00"), price);
    }

    @Test
    void testGetPrice_InvalidCode_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> productService.getPrice("invalid_code"));

        assertTrue(exception.getMessage().contains("Invalid product code"));
        assertTrue(exception.getMessage().contains("invalid_code"));
    }

    @Test
    void testGetPrice_NullCode_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> productService.getPrice(null));
    }

    // ========== IS VALID PRODUCT CODE TESTS ==========

    @Test
    void testIsValidProductCode_ValidPrint_ReturnsTrue() {
        // When & Then
        assertTrue(productService.isValidProductCode(ProductService.PRODUCT_CODE_PRINT));
    }

    @Test
    void testIsValidProductCode_ValidPdf_ReturnsTrue() {
        // When & Then
        assertTrue(productService.isValidProductCode(ProductService.PRODUCT_CODE_PDF));
    }

    @Test
    void testIsValidProductCode_InvalidCode_ReturnsFalse() {
        // When & Then
        assertFalse(productService.isValidProductCode("nonexistent"));
    }

    @Test
    void testIsValidProductCode_NullCode_ReturnsFalse() {
        // When & Then
        assertFalse(productService.isValidProductCode(null));
    }

    @Test
    void testIsValidProductCode_EmptyCode_ReturnsFalse() {
        // When & Then
        assertFalse(productService.isValidProductCode(""));
    }

    @Test
    void testIsValidProductCode_CaseSensitive() {
        // When & Then - codes should be case sensitive
        assertFalse(productService.isValidProductCode("PRINT"));
        assertFalse(productService.isValidProductCode("Print"));
        assertFalse(productService.isValidProductCode("PDF"));
    }

    // ========== GET DEFAULT PRODUCT CODE TESTS ==========

    @Test
    void testGetDefaultProductCode_ReturnsPrint() {
        // When
        String defaultCode = productService.getDefaultProductCode();

        // Then
        assertEquals(ProductService.PRODUCT_CODE_PRINT, defaultCode);
    }

    @Test
    void testGetDefaultProductCode_IsValidCode() {
        // When
        String defaultCode = productService.getDefaultProductCode();

        // Then
        assertTrue(productService.isValidProductCode(defaultCode));
    }

    // ========== PRODUCT CONSTANTS TESTS ==========

    @Test
    void testProductCodeConstants_AreCorrectValues() {
        // Then
        assertEquals("print", ProductService.PRODUCT_CODE_PRINT);
        assertEquals("pdf", ProductService.PRODUCT_CODE_PDF);
    }

    // ========== PRICE COMPARISON TESTS ==========

    @Test
    void testPriceComparison_PrintMoreExpensiveThanPdf() {
        // Given
        BigDecimal printPrice = productService.getPrice(ProductService.PRODUCT_CODE_PRINT);
        BigDecimal pdfPrice = productService.getPrice(ProductService.PRODUCT_CODE_PDF);

        // Then
        assertTrue(printPrice.compareTo(pdfPrice) > 0, "Print should be more expensive than PDF");
    }

    @Test
    void testAllPrices_ArePositive() {
        // When
        List<ProductService.Product> products = productService.getAllProducts();

        // Then
        for (ProductService.Product product : products) {
            assertTrue(product.price.compareTo(BigDecimal.ZERO) > 0,
                    "Price for " + product.code + " should be positive");
        }
    }
}
