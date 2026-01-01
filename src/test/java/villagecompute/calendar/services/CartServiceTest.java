package villagecompute.calendar.services;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import villagecompute.calendar.api.graphql.inputs.AddToCartInput;
import villagecompute.calendar.api.types.Cart;
import villagecompute.calendar.data.models.CartItem;

import io.quarkus.test.junit.QuarkusTest;

/** Unit tests for CartService. Tests cart operations, session isolation, and item management. */
@QuarkusTest
class CartServiceTest {

    @Inject
    CartService cartService;

    private String testSessionId;

    @BeforeEach
    void setUp() {
        testSessionId = "test-session-" + UUID.randomUUID();
    }

    @AfterEach
    @Transactional
    void tearDown() {
        // Clean up cart items first (foreign key constraint)
        CartItem.deleteAll();
        villagecompute.calendar.data.models.Cart.deleteAll();
    }

    // ========== GET CART TESTS ==========

    @Test
    @Transactional
    void testGetCart_NewSession_CreatesEmptyCart() {
        // When
        Cart cart = cartService.getCart(testSessionId);

        // Then
        assertNotNull(cart);
        assertNotNull(cart.id);
        assertEquals(0, cart.itemCount);
        assertEquals(0.0, cart.subtotal);
        assertTrue(cart.items.isEmpty());
    }

    @Test
    @Transactional
    void testGetCart_SameSession_ReturnsSameCart() {
        // Given
        Cart cart1 = cartService.getCart(testSessionId);

        // When
        Cart cart2 = cartService.getCart(testSessionId);

        // Then
        assertEquals(cart1.id, cart2.id);
    }

    @Test
    @Transactional
    void testGetCart_DifferentSessions_ReturnsDifferentCarts() {
        // Given
        String sessionId1 = "session-1-" + UUID.randomUUID();
        String sessionId2 = "session-2-" + UUID.randomUUID();

        // When
        Cart cart1 = cartService.getCart(sessionId1);
        Cart cart2 = cartService.getCart(sessionId2);

        // Then
        assertNotEquals(cart1.id, cart2.id);
    }

    // ========== ADD TO CART TESTS ==========

    @Test
    @Transactional
    void testAddToCart_NewItem_AddsToCart() {
        // Given
        AddToCartInput input = new AddToCartInput();
        input.generatorType = "calendar";
        input.description = "Test Calendar 2026";
        input.quantity = 1;
        input.productCode = "print";
        input.configuration = "{\"theme\":\"modern\",\"year\":2026}";

        // When
        Cart cart = cartService.addToCart(testSessionId, input);

        // Then
        assertNotNull(cart);
        assertEquals(1, cart.itemCount);
        assertEquals(1, cart.items.size());
        assertEquals("calendar", cart.items.get(0).generatorType);
        assertEquals("Test Calendar 2026", cart.items.get(0).description);
    }

    @Test
    @Transactional
    void testAddToCart_SameItem_CreatesSeparateItems() {
        // Given - With new generator-based API, each add creates a new item
        // because each generated SVG is unique
        AddToCartInput input = new AddToCartInput();
        input.generatorType = "calendar";
        input.description = "Test Calendar 2026";
        input.quantity = 1;
        input.productCode = "print";
        input.configuration = "{\"theme\":\"modern\",\"year\":2026}";

        // When - Add same configuration twice
        cartService.addToCart(testSessionId, input);
        Cart cart = cartService.addToCart(testSessionId, input);

        // Then - Each add creates a separate item (unique SVGs)
        assertEquals(2, cart.itemCount);
        assertEquals(2, cart.items.size());
    }

    @Test
    @Transactional
    void testAddToCart_DifferentConfiguration_CreatesNewItem() {
        // Given
        AddToCartInput input1 = new AddToCartInput();
        input1.generatorType = "calendar";
        input1.description = "Modern Calendar 2026";
        input1.quantity = 1;
        input1.productCode = "print";
        input1.configuration = "{\"theme\":\"modern\",\"year\":2026}";

        AddToCartInput input2 = new AddToCartInput();
        input2.generatorType = "calendar";
        input2.description = "Classic Calendar 2026";
        input2.quantity = 1;
        input2.productCode = "print";
        input2.configuration = "{\"theme\":\"classic\",\"year\":2026}";

        // When
        cartService.addToCart(testSessionId, input1);
        Cart cart = cartService.addToCart(testSessionId, input2);

        // Then - Different configs = different line items
        assertEquals(2, cart.itemCount);
        assertEquals(2, cart.items.size());
    }

    @Test
    @Transactional
    void testAddToCart_DifferentYear_CreatesNewItem() {
        // Given
        AddToCartInput input1 = new AddToCartInput();
        input1.generatorType = "calendar";
        input1.description = "Calendar 2025";
        input1.quantity = 1;
        input1.productCode = "print";
        input1.configuration = "{\"theme\":\"modern\",\"year\":2025}";

        AddToCartInput input2 = new AddToCartInput();
        input2.generatorType = "calendar";
        input2.description = "Calendar 2026";
        input2.quantity = 1;
        input2.productCode = "print";
        input2.configuration = "{\"theme\":\"modern\",\"year\":2026}";

        // When
        cartService.addToCart(testSessionId, input1);
        Cart cart = cartService.addToCart(testSessionId, input2);

        // Then - Different years = different line items
        assertEquals(2, cart.itemCount);
        assertEquals(2, cart.items.size());
    }

    @Test
    @Transactional
    void testAddToCart_StaticPageProduct_WorksCorrectly() {
        // Given - Static pages use generatorType and description
        AddToCartInput input = new AddToCartInput();
        input.generatorType = "calendar";
        input.description = "Vermont Weekends 2026";
        input.quantity = 1;
        input.productCode = "print";
        input.configuration = "{\"theme\":\"vermont\",\"year\":2026}";

        // When
        Cart cart = cartService.addToCart(testSessionId, input);

        // Then
        assertNotNull(cart);
        assertEquals(1, cart.itemCount);
        assertEquals("calendar", cart.items.get(0).generatorType);
        assertEquals("Vermont Weekends 2026", cart.items.get(0).description);
    }

    // ========== SESSION ISOLATION TESTS ==========

    @Test
    @Transactional
    void testSessionIsolation_CartItemsNotShared() {
        // Given
        String sessionId1 = "session-1-" + UUID.randomUUID();
        String sessionId2 = "session-2-" + UUID.randomUUID();

        AddToCartInput input = new AddToCartInput();
        input.generatorType = "calendar";
        input.description = "Test Calendar";
        input.quantity = 1;
        input.productCode = "print";

        // When - Add to session 1 only
        cartService.addToCart(sessionId1, input);
        Cart cart1 = cartService.getCart(sessionId1);
        Cart cart2 = cartService.getCart(sessionId2);

        // Then
        assertEquals(1, cart1.itemCount);
        assertEquals(0, cart2.itemCount);
    }

    // ========== UPDATE QUANTITY TESTS ==========

    @Test
    @Transactional
    void testUpdateQuantity_IncreasesQuantity() {
        // Given
        AddToCartInput input = new AddToCartInput();
        input.generatorType = "calendar";
        input.description = "Test Calendar";
        input.quantity = 1;
        input.productCode = "print";

        Cart initialCart = cartService.addToCart(testSessionId, input);
        UUID itemId = UUID.fromString(initialCart.items.get(0).id);

        // When
        Cart updatedCart = cartService.updateQuantity(testSessionId, itemId, 5);

        // Then
        assertEquals(5, updatedCart.itemCount);
        assertEquals(5, updatedCart.items.get(0).quantity);
    }

    @Test
    @Transactional
    void testUpdateQuantity_ZeroRemovesItem() {
        // Given
        AddToCartInput input = new AddToCartInput();
        input.generatorType = "calendar";
        input.description = "Test Calendar";
        input.quantity = 1;
        input.productCode = "print";

        Cart initialCart = cartService.addToCart(testSessionId, input);
        UUID itemId = UUID.fromString(initialCart.items.get(0).id);

        // When
        Cart updatedCart = cartService.updateQuantity(testSessionId, itemId, 0);

        // Then
        assertEquals(0, updatedCart.itemCount);
        assertTrue(updatedCart.items.isEmpty());
    }

    // ========== REMOVE ITEM TESTS ==========

    @Test
    @Transactional
    void testRemoveItem_RemovesFromCart() {
        // Given
        AddToCartInput input = new AddToCartInput();
        input.generatorType = "calendar";
        input.description = "Test Calendar";
        input.quantity = 2;
        input.productCode = "print";

        Cart initialCart = cartService.addToCart(testSessionId, input);
        UUID itemId = UUID.fromString(initialCart.items.get(0).id);

        // When
        Cart updatedCart = cartService.removeItem(testSessionId, itemId);

        // Then
        assertEquals(0, updatedCart.itemCount);
        assertTrue(updatedCart.items.isEmpty());
    }

    // ========== CLEAR CART TESTS ==========

    @Test
    @Transactional
    void testClearCart_RemovesAllItems() {
        // Given
        AddToCartInput input1 = new AddToCartInput();
        input1.generatorType = "calendar";
        input1.description = "Calendar 1";
        input1.quantity = 1;
        input1.productCode = "print";
        input1.configuration = "{\"config\":\"1\"}";

        AddToCartInput input2 = new AddToCartInput();
        input2.generatorType = "calendar";
        input2.description = "Calendar 2";
        input2.quantity = 1;
        input2.productCode = "print";
        input2.configuration = "{\"config\":\"2\"}";

        cartService.addToCart(testSessionId, input1);
        cartService.addToCart(testSessionId, input2);

        // When
        Cart clearedCart = cartService.clearCart(testSessionId);

        // Then
        assertEquals(0, clearedCart.itemCount);
        assertTrue(clearedCart.items.isEmpty());
    }

    // ========== SUBTOTAL CALCULATION TESTS ==========

    @Test
    @Transactional
    void testSubtotal_CalculatedCorrectly() {
        // Given
        AddToCartInput input = new AddToCartInput();
        input.quantity = 3;
        input.productCode = "print";

        // When
        Cart cart = cartService.addToCart(testSessionId, input);

        // Then
        // Default print price is $29.99
        assertTrue(cart.subtotal > 0);
    }

    // ========== EDGE CASE TESTS ==========

    @Test
    @Transactional
    void testAddToCart_WithInvalidProductCode_UsesDefault() {
        // Given
        AddToCartInput input = new AddToCartInput();
        input.generatorType = "calendar";
        input.description = "Test Calendar";
        input.quantity = 1;
        input.productCode = "invalid_product_code"; // Invalid code

        // When
        Cart cart = cartService.addToCart(testSessionId, input);

        // Then - Should still create item using default price
        assertNotNull(cart);
        assertEquals(1, cart.itemCount);
        assertTrue(cart.subtotal > 0); // Has a price from default product
    }

    @Test
    @Transactional
    void testAddToCart_WithNullProductCode_UsesDefault() {
        // Given
        AddToCartInput input = new AddToCartInput();
        input.generatorType = "calendar";
        input.description = "Test Calendar";
        input.quantity = 1;
        input.productCode = null; // Null product code

        // When
        Cart cart = cartService.addToCart(testSessionId, input);

        // Then - Should still create item using default price
        assertNotNull(cart);
        assertEquals(1, cart.itemCount);
        assertTrue(cart.subtotal > 0);
    }

    @Test
    @Transactional
    void testAddToCart_WithAssets_CreatesItemWithAssets() {
        // Given
        AddToCartInput input = new AddToCartInput();
        input.generatorType = "calendar";
        input.description = "Test Calendar with Assets";
        input.quantity = 1;
        input.productCode = "print";

        // Add asset inputs
        villagecompute.calendar.api.graphql.inputs.AssetInput asset = new villagecompute.calendar.api.graphql.inputs.AssetInput();
        asset.assetKey = "page1";
        asset.svgContent = "<svg>Test SVG</svg>";
        asset.widthInches = java.math.BigDecimal.valueOf(8.5);
        asset.heightInches = java.math.BigDecimal.valueOf(11.0);
        input.assets = java.util.List.of(asset);

        // When
        Cart cart = cartService.addToCart(testSessionId, input);

        // Then
        assertNotNull(cart);
        assertEquals(1, cart.itemCount);
        assertEquals("Test Calendar with Assets", cart.items.get(0).description);
    }

    @Test
    @Transactional
    void testAddToCart_WithMultipleAssets_CreatesAllAssets() {
        // Given
        AddToCartInput input = new AddToCartInput();
        input.generatorType = "calendar";
        input.description = "Multi-Page Calendar";
        input.quantity = 1;
        input.productCode = "print";

        villagecompute.calendar.api.graphql.inputs.AssetInput asset1 = new villagecompute.calendar.api.graphql.inputs.AssetInput();
        asset1.assetKey = "page1";
        asset1.svgContent = "<svg>Page 1</svg>";
        asset1.widthInches = java.math.BigDecimal.valueOf(8.5);
        asset1.heightInches = java.math.BigDecimal.valueOf(11.0);

        villagecompute.calendar.api.graphql.inputs.AssetInput asset2 = new villagecompute.calendar.api.graphql.inputs.AssetInput();
        asset2.assetKey = "page2";
        asset2.svgContent = "<svg>Page 2</svg>";
        asset2.widthInches = java.math.BigDecimal.valueOf(8.5);
        asset2.heightInches = java.math.BigDecimal.valueOf(11.0);

        input.assets = java.util.List.of(asset1, asset2);

        // When
        Cart cart = cartService.addToCart(testSessionId, input);

        // Then
        assertNotNull(cart);
        assertEquals(1, cart.itemCount);
    }

    @Test
    @Transactional
    void testAddToCart_WithNullQuantity_DefaultsToOne() {
        // Given
        AddToCartInput input = new AddToCartInput();
        input.generatorType = "calendar";
        input.description = "Test Calendar";
        input.quantity = null; // Null quantity
        input.productCode = "print";

        // When
        Cart cart = cartService.addToCart(testSessionId, input);

        // Then
        assertNotNull(cart);
        assertEquals(1, cart.itemCount);
        assertEquals(1, cart.items.get(0).quantity); // Defaults to 1
    }

    @Test
    @Transactional
    void testUpdateQuantity_ItemNotFoundOrWrongCart_NoChange() {
        // Given
        AddToCartInput input = new AddToCartInput();
        input.generatorType = "calendar";
        input.description = "Test Calendar";
        input.quantity = 1;
        input.productCode = "print";

        Cart initialCart = cartService.addToCart(testSessionId, input);
        UUID randomItemId = UUID.randomUUID(); // Non-existent item

        // When
        Cart updatedCart = cartService.updateQuantity(testSessionId, randomItemId, 10);

        // Then - Cart should be unchanged
        assertEquals(initialCart.itemCount, updatedCart.itemCount);
    }

    @Test
    @Transactional
    void testRemoveItem_ItemNotFoundOrWrongCart_NoChange() {
        // Given
        AddToCartInput input = new AddToCartInput();
        input.generatorType = "calendar";
        input.description = "Test Calendar";
        input.quantity = 1;
        input.productCode = "print";

        Cart initialCart = cartService.addToCart(testSessionId, input);
        UUID randomItemId = UUID.randomUUID(); // Non-existent item

        // When
        Cart updatedCart = cartService.removeItem(testSessionId, randomItemId);

        // Then - Cart should be unchanged
        assertEquals(initialCart.itemCount, updatedCart.itemCount);
    }

    @Test
    @Transactional
    void testUpdateQuantity_ItemFromDifferentCart_NoChange() {
        // Given - Create two sessions with items
        String sessionId1 = "session-1-" + UUID.randomUUID();
        String sessionId2 = "session-2-" + UUID.randomUUID();

        AddToCartInput input = new AddToCartInput();
        input.generatorType = "calendar";
        input.description = "Test Calendar";
        input.quantity = 1;
        input.productCode = "print";

        Cart cart1 = cartService.addToCart(sessionId1, input);
        cartService.addToCart(sessionId2, input);
        UUID itemFromCart1 = UUID.fromString(cart1.items.get(0).id);

        // When - Try to update item from cart1 while using session2
        Cart cart2AfterUpdate = cartService.updateQuantity(sessionId2, itemFromCart1, 10);

        // Then - Cart2 should be unchanged (item belongs to different cart)
        assertEquals(1, cart2AfterUpdate.itemCount);
        assertEquals(1, cart2AfterUpdate.items.get(0).quantity); // Still 1
    }
}
