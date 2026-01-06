package villagecompute.calendar.api.graphql;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import villagecompute.calendar.services.ProductService;
import villagecompute.calendar.types.ProductType;

/**
 * Unit tests for ProductGraphQL. Tests all query methods with mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
class ProductGraphQLUnitTest {

    @InjectMocks
    ProductGraphQL productGraphQL;

    @Mock
    ProductService productService;

    private ProductService.Product testPrintProduct;
    private ProductService.Product testPdfProduct;

    @BeforeEach
    void setUp() {
        testPrintProduct = new ProductService.Product("print", "Printed Poster", "A beautiful printed poster",
                new BigDecimal("25.00"), List.of("Premium quality", "Ships fast"), "pi-print", "Most Popular", 1);

        testPdfProduct = new ProductService.Product("pdf", "Digital PDF", "High-resolution PDF download",
                new BigDecimal("5.00"), List.of("Instant download"), "pi-file-pdf", null, 2);
    }

    @Nested
    class GetProductsTests {

        @Test
        void getProducts_ReturnsAllProducts() {
            when(productService.getAllProducts()).thenReturn(List.of(testPrintProduct, testPdfProduct));

            List<ProductType> result = productGraphQL.getProducts();

            assertNotNull(result);
            assertEquals(2, result.size());
            assertEquals("print", result.get(0).code);
            assertEquals("pdf", result.get(1).code);
            verify(productService).getAllProducts();
        }

        @Test
        void getProducts_EmptyList_ReturnsEmptyList() {
            when(productService.getAllProducts()).thenReturn(Collections.emptyList());

            List<ProductType> result = productGraphQL.getProducts();

            assertNotNull(result);
            assertTrue(result.isEmpty());
            verify(productService).getAllProducts();
        }

        @Test
        void getProducts_MapsAllFields() {
            when(productService.getAllProducts()).thenReturn(List.of(testPrintProduct));

            List<ProductType> result = productGraphQL.getProducts();

            assertEquals(1, result.size());
            ProductType product = result.get(0);
            assertEquals("print", product.code);
            assertEquals("Printed Poster", product.name);
            assertEquals("A beautiful printed poster", product.description);
            assertEquals(25.00, product.price);
            assertEquals(List.of("Premium quality", "Ships fast"), product.features);
            assertEquals("pi-print", product.icon);
            assertEquals("Most Popular", product.badge);
            assertEquals(1, product.displayOrder);
        }

        @Test
        void getProducts_NullBadge_MapsCorrectly() {
            when(productService.getAllProducts()).thenReturn(List.of(testPdfProduct));

            List<ProductType> result = productGraphQL.getProducts();

            assertEquals(1, result.size());
            assertNull(result.get(0).badge);
        }
    }

    @Nested
    class GetProductTests {

        @Test
        void getProduct_ExistingCode_ReturnsProduct() {
            when(productService.getProduct("print")).thenReturn(Optional.of(testPrintProduct));

            ProductType result = productGraphQL.getProduct("print");

            assertNotNull(result);
            assertEquals("print", result.code);
            assertEquals("Printed Poster", result.name);
            verify(productService).getProduct("print");
        }

        @Test
        void getProduct_NonExistentCode_ReturnsNull() {
            when(productService.getProduct("invalid")).thenReturn(Optional.empty());

            ProductType result = productGraphQL.getProduct("invalid");

            assertNull(result);
            verify(productService).getProduct("invalid");
        }

        @Test
        void getProduct_NullCode_ReturnsNull() {
            when(productService.getProduct(null)).thenReturn(Optional.empty());

            ProductType result = productGraphQL.getProduct(null);

            assertNull(result);
            verify(productService).getProduct(null);
        }

        @Test
        void getProduct_MapsAllFields() {
            when(productService.getProduct("pdf")).thenReturn(Optional.of(testPdfProduct));

            ProductType result = productGraphQL.getProduct("pdf");

            assertNotNull(result);
            assertEquals("pdf", result.code);
            assertEquals("Digital PDF", result.name);
            assertEquals("High-resolution PDF download", result.description);
            assertEquals(5.00, result.price);
            assertEquals(List.of("Instant download"), result.features);
            assertEquals("pi-file-pdf", result.icon);
            assertNull(result.badge);
            assertEquals(2, result.displayOrder);
        }
    }

    @Nested
    class GetDefaultProductCodeTests {

        @Test
        void getDefaultProductCode_ReturnsDefaultCode() {
            when(productService.getDefaultProductCode()).thenReturn("print");

            String result = productGraphQL.getDefaultProductCode();

            assertEquals("print", result);
            verify(productService).getDefaultProductCode();
        }

        @Test
        void getDefaultProductCode_DelegatesDirectlyToService() {
            when(productService.getDefaultProductCode()).thenReturn("custom-default");

            String result = productGraphQL.getDefaultProductCode();

            assertEquals("custom-default", result);
        }
    }
}
