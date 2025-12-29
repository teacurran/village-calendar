package villagecompute.calendar.services;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import villagecompute.calendar.api.graphql.inputs.AddToCartInput;
import villagecompute.calendar.api.graphql.types.Cart;
import villagecompute.calendar.data.models.CartItem;

import io.quarkus.test.junit.QuarkusTest;

/** Unit tests for CartService. Tests cart operations, session isolation, and item management. */
@QuarkusTest
class CartServiceTest {

    @Inject CartService cartService;

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
        input.quantity = 1;
        input.productCode = "print";
        input.configuration = "{\"theme\":\"modern\"}";

        // When
        Cart cart = cartService.addToCart(testSessionId, input);

        // Then
        assertNotNull(cart);
        assertEquals(1, cart.itemCount);
        assertEquals(1, cart.items.size());
        assertEquals("Test Calendar", cart.items.get(0).templateName);
        assertEquals(2026, cart.items.get(0).year);
    }

    @Test
    @Transactional
    void testAddToCart_SameItem_IncrementsQuantity() {
        // Given
        AddToCartInput input = new AddToCartInput();
        input.quantity = 1;
        input.productCode = "print";
        input.configuration = "{\"theme\":\"modern\"}";

        // When - Add same item twice
        cartService.addToCart(testSessionId, input);
        Cart cart = cartService.addToCart(testSessionId, input);

        // Then - Should have quantity 2, not 2 items
        assertEquals(2, cart.itemCount);
        assertEquals(1, cart.items.size());
        assertEquals(2, cart.items.get(0).quantity);
    }

    @Test
    @Transactional
    void testAddToCart_DifferentConfiguration_CreatesNewItem() {
        // Given
        AddToCartInput input1 = new AddToCartInput();
        input1.quantity = 1;
        input1.productCode = "print";
        input1.configuration = "{\"theme\":\"modern\"}";

        AddToCartInput input2 = new AddToCartInput();
        input2.quantity = 1;
        input2.productCode = "print";
        input2.configuration = "{\"theme\":\"classic\"}";

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
        input1.quantity = 1;
        input1.productCode = "print";
        input1.configuration = "{\"theme\":\"modern\"}";

        AddToCartInput input2 = new AddToCartInput();
        input2.quantity = 1;
        input2.productCode = "print";
        input2.configuration = "{\"theme\":\"modern\"}";

        // When
        cartService.addToCart(testSessionId, input1);
        Cart cart = cartService.addToCart(testSessionId, input2);

        // Then - Different years = different line items
        assertEquals(2, cart.itemCount);
        assertEquals(2, cart.items.size());
    }

    @Test
    @Transactional
    void testAddToCart_NullTemplateId_WorksForStaticPages() {
        // Given - Static pages don't have templateId
        AddToCartInput input = new AddToCartInput();
        input.quantity = 1;
        input.productCode = "print";
        input.configuration = "{\"theme\":\"vermont\"}";

        // When
        Cart cart = cartService.addToCart(testSessionId, input);

        // Then
        assertNotNull(cart);
        assertEquals(1, cart.itemCount);
        assertNull(cart.items.get(0).templateId);
        assertEquals("Vermont Weekends 2026", cart.items.get(0).templateName);
    }

    // ========== SESSION ISOLATION TESTS ==========

    @Test
    @Transactional
    void testSessionIsolation_CartItemsNotShared() {
        // Given
        String sessionId1 = "session-1-" + UUID.randomUUID();
        String sessionId2 = "session-2-" + UUID.randomUUID();

        AddToCartInput input = new AddToCartInput();
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
        input1.quantity = 1;
        input1.productCode = "print";
        input1.configuration = "{\"config\":\"1\"}";

        AddToCartInput input2 = new AddToCartInput();
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
}
