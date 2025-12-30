package villagecompute.calendar.integration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import jakarta.transaction.Transactional;

import org.junit.jupiter.api.*;

import villagecompute.calendar.data.models.Cart;
import villagecompute.calendar.data.models.CartItem;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * End-to-end integration tests for shopping cart workflows.
 *
 * <p>Tests: 1. Guest adds item to cart via GraphQL with X-Session-ID header 2. Same session ID
 * returns same cart with items 3. Different session IDs have isolated carts 4. Static page
 * add-to-cart workflow (no templateId) 5. Cart persists across multiple requests
 *
 * <p>These tests verify the critical flow where static product pages add items to cart and redirect
 * to checkout.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CartWorkflowTest {

    @AfterEach
    @Transactional
    void cleanup() {
        // Clean up cart items first (foreign key constraint)
        CartItem.deleteAll();
        Cart.deleteAll();
    }

    // ============================================================================
    // TEST: Add to Cart with X-Session-ID Header
    // ============================================================================

    @Test
    @Order(1)
    void testAddToCart_WithSessionIdHeader_CreatesCartItem() {
        String sessionId = "test-session-" + UUID.randomUUID();

        String mutation =
                """
            mutation AddToCart($input: AddToCartInput!) {
                addToCart(input: $input) {
                    id
                    itemCount
                    subtotal
                    items {
                        id
                        generatorType
                        description
                        quantity
                        unitPrice
                        configuration
                    }
                }
            }
            """;

        String variables =
                """
            {
                "input": {
                    "generatorType": "calendar",
                    "description": "Test Calendar 2026",
                    "quantity": 1,
                    "productCode": "print",
                    "configuration": "{\\"theme\\":\\"modern\\",\\"year\\":2026}"
                }
            }
            """;

        given().contentType(ContentType.JSON)
                .header("X-Session-ID", sessionId)
                .body(
                        """
                {
                    "query": "%s",
                    "variables": %s
                }
                """
                                .formatted(
                                        mutation.replace("\n", "\\n").replace("\"", "\\\""),
                                        variables))
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.addToCart.itemCount", equalTo(1))
                .body("data.addToCart.items", hasSize(1))
                .body("data.addToCart.items[0].generatorType", equalTo("calendar"))
                .body("data.addToCart.items[0].description", equalTo("Test Calendar 2026"))
                .body("data.addToCart.items[0].quantity", equalTo(1));
    }

    // ============================================================================
    // TEST: Same Session ID Returns Same Cart
    // ============================================================================

    @Test
    @Order(2)
    void testGetCart_SameSessionId_ReturnsSameCart() {
        String sessionId = "persistent-session-" + UUID.randomUUID();

        // First: Add item to cart
        String addMutation =
                """
            {
                "query": "mutation { addToCart(input: { generatorType: \\"calendar\\", description: \\"Persistent Calendar\\", quantity: 1, productCode: \\"print\\" }) { id itemCount } }"
            }
            """;

        String cartId =
                given().contentType(ContentType.JSON)
                        .header("X-Session-ID", sessionId)
                        .body(addMutation)
                        .when()
                        .post("/graphql")
                        .then()
                        .statusCode(200)
                        .body("data.addToCart.itemCount", equalTo(1))
                        .extract()
                        .path("data.addToCart.id");

        // Second: Query cart with same session ID
        String getQuery =
                """
            {
                "query": "query { cart { id itemCount items { description } } }"
            }
            """;

        given().contentType(ContentType.JSON)
                .header("X-Session-ID", sessionId)
                .body(getQuery)
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.cart.id", equalTo(cartId))
                .body("data.cart.itemCount", equalTo(1))
                .body("data.cart.items[0].description", equalTo("Persistent Calendar"));
    }

    // ============================================================================
    // TEST: Different Session IDs Have Isolated Carts
    // ============================================================================

    @Test
    @Order(3)
    void testCartIsolation_DifferentSessionIds_SeparateCarts() {
        String sessionId1 = "session-1-" + UUID.randomUUID();
        String sessionId2 = "session-2-" + UUID.randomUUID();

        // Add item to session 1
        String addMutation1 =
                """
            {
                "query": "mutation { addToCart(input: { generatorType: \\"calendar\\", description: \\"Session 1 Calendar\\", quantity: 1, productCode: \\"print\\" }) { id itemCount } }"
            }
            """;

        given().contentType(ContentType.JSON)
                .header("X-Session-ID", sessionId1)
                .body(addMutation1)
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.addToCart.itemCount", equalTo(1));

        // Query cart for session 2 - should be empty (new cart)
        String getQuery =
                """
            {
                "query": "query { cart { id itemCount } }"
            }
            """;

        given().contentType(ContentType.JSON)
                .header("X-Session-ID", sessionId2)
                .body(getQuery)
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.cart.itemCount", equalTo(0));
    }

    // ============================================================================
    // TEST: Static Page Add-to-Cart (No templateId)
    // ============================================================================

    @Test
    @Order(4)
    void testStaticPageAddToCart_NoTemplateId_WorksCorrectly() {
        String sessionId = "static-page-session-" + UUID.randomUUID();

        // Simulates static product page behavior - uses generatorType and description
        String addMutation =
                """
            {
                "query": "mutation { addToCart(input: { generatorType: \\"calendar\\", description: \\"Vermont Weekends 2026\\", quantity: 1, productCode: \\"print\\", configuration: \\"{\\\\\\"theme\\\\\\":\\\\\\"vermont\\\\\\",\\\\\\"year\\\\\\":2026,\\\\\\"holidaySets\\\\\\":[]}\\" }) { id itemCount items { generatorType description configuration } } }"
            }
            """;

        given().contentType(ContentType.JSON)
                .header("X-Session-ID", sessionId)
                .body(addMutation)
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.addToCart.itemCount", equalTo(1))
                .body("data.addToCart.items[0].generatorType", equalTo("calendar"))
                .body("data.addToCart.items[0].description", equalTo("Vermont Weekends 2026"))
                .body("data.addToCart.items[0].configuration", containsString("vermont"));
    }

    // ============================================================================
    // TEST: Cart Persists After Add - Simulates Checkout Flow
    // ============================================================================

    @Test
    @Order(5)
    void testCheckoutFlow_CartPersistsAcrossRequests() {
        String sessionId = "checkout-flow-" + UUID.randomUUID();

        // Step 1: Add to cart (from static page)
        String addMutation =
                """
            {
                "query": "mutation { addToCart(input: { generatorType: \\"calendar\\", description: \\"Checkout Test Calendar\\", quantity: 1, productCode: \\"print\\" }) { id itemCount } }"
            }
            """;

        given().contentType(ContentType.JSON)
                .header("X-Session-ID", sessionId)
                .body(addMutation)
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.addToCart.itemCount", equalTo(1));

        // Step 2: Simulate page load delay (what happens when redirecting to /checkout)
        // No sleep needed - just verify persistence

        // Step 3: Fetch cart (what CheckoutEmbedded.vue does on mount)
        String getQuery =
                """
            {
                "query": "query { cart { id itemCount subtotal items { description quantity unitPrice } } }"
            }
            """;

        given().contentType(ContentType.JSON)
                .header("X-Session-ID", sessionId)
                .body(getQuery)
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.cart.itemCount", equalTo(1))
                .body("data.cart.items", hasSize(1))
                .body("data.cart.items[0].description", equalTo("Checkout Test Calendar"))
                .body("data.cart.subtotal", greaterThan(0.0f));
    }

    // ============================================================================
    // TEST: Multiple Items In Cart
    // ============================================================================

    @Test
    @Order(6)
    void testAddMultipleItems_DifferentConfigurations_CreatesSeparateLineItems() {
        String sessionId = "multi-item-" + UUID.randomUUID();

        // Add first item
        String addMutation1 =
                """
            {
                "query": "mutation { addToCart(input: { generatorType: \\"calendar\\", description: \\"Calendar Config A\\", quantity: 1, productCode: \\"print\\", configuration: \\"{\\\\\\"theme\\\\\\":\\\\\\"A\\\\\\"}\\" }) { itemCount } }"
            }
            """;

        given().contentType(ContentType.JSON)
                .header("X-Session-ID", sessionId)
                .body(addMutation1)
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.addToCart.itemCount", equalTo(1));

        // Add second item with different configuration
        String addMutation2 =
                """
            {
                "query": "mutation { addToCart(input: { generatorType: \\"calendar\\", description: \\"Calendar Config B\\", quantity: 2, productCode: \\"print\\", configuration: \\"{\\\\\\"theme\\\\\\":\\\\\\"B\\\\\\"}\\" }) { itemCount items { description quantity } } }"
            }
            """;

        given().contentType(ContentType.JSON)
                .header("X-Session-ID", sessionId)
                .body(addMutation2)
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.addToCart.itemCount", equalTo(3)) // 1 + 2 = 3 total quantity
                .body("data.addToCart.items", hasSize(2)); // 2 separate line items
    }

    // ============================================================================
    // TEST: Clear Cart
    // ============================================================================

    @Test
    @Order(7)
    void testClearCart_RemovesAllItems() {
        String sessionId = "clear-cart-" + UUID.randomUUID();

        // Add items
        String addMutation =
                """
            {
                "query": "mutation { addToCart(input: { generatorType: \\"calendar\\", description: \\"To Be Cleared\\", quantity: 3, productCode: \\"print\\" }) { itemCount } }"
            }
            """;

        given().contentType(ContentType.JSON)
                .header("X-Session-ID", sessionId)
                .body(addMutation)
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.addToCart.itemCount", equalTo(3));

        // Clear cart
        String clearMutation =
                """
            {
                "query": "mutation { clearCart { itemCount items { id } } }"
            }
            """;

        given().contentType(ContentType.JSON)
                .header("X-Session-ID", sessionId)
                .body(clearMutation)
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.clearCart.itemCount", equalTo(0))
                .body("data.clearCart.items", hasSize(0));
    }

    // ============================================================================
    // TEST: Database Persistence Verification
    // ============================================================================

    @Test
    @Order(8)
    @Transactional
    void testDatabasePersistence_CartSavedCorrectly() {
        String sessionId = "db-test-" + UUID.randomUUID();

        // Add item via GraphQL
        String addMutation =
                """
            {
                "query": "mutation { addToCart(input: { generatorType: \\"calendar\\", description: \\"DB Test Calendar\\", quantity: 2, productCode: \\"print\\" }) { id } }"
            }
            """;

        given().contentType(ContentType.JSON)
                .header("X-Session-ID", sessionId)
                .body(addMutation)
                .when()
                .post("/graphql")
                .then()
                .statusCode(200);

        // Verify directly in database
        Cart dbCart = Cart.find("sessionId", sessionId).firstResult();
        assertNotNull(dbCart, "Cart should exist in database");
        assertEquals(sessionId, dbCart.sessionId);
        assertEquals(1, dbCart.items.size());
        assertEquals("calendar", dbCart.items.get(0).generatorType);
        assertEquals("DB Test Calendar", dbCart.items.get(0).description);
        assertEquals(2, dbCart.items.get(0).quantity);
    }
}
