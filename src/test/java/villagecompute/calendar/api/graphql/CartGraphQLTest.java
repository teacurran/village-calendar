package villagecompute.calendar.api.graphql;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import villagecompute.calendar.data.models.Cart;
import villagecompute.calendar.data.models.CartItem;
import villagecompute.calendar.data.models.ItemAsset;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/**
 * Integration tests for CartGraphQL resolvers. Exercises all queries and mutations against the live GraphQL endpoint
 * with a guest session via the X-Session-ID header.
 */
@QuarkusTest
class CartGraphQLTest {

    private static final String SESSION_HEADER = "X-Session-ID";
    private static final String GRAPHQL_PATH = "/graphql";

    private String testSessionId;

    @BeforeEach
    void setUp() {
        testSessionId = "test-cart-graphql-" + UUID.randomUUID();
    }

    @AfterEach
    @Transactional
    void tearDown() {
        // Order matters due to FK constraints
        CartItem.deleteAll();
        Cart.deleteAll();
        ItemAsset.deleteAll();
    }

    // ========================================================
    // Helpers
    // ========================================================

    private RequestSpecification graphqlRequest(String sessionId) {
        return given().header(SESSION_HEADER, sessionId).contentType(ContentType.JSON);
    }

    private Map<String, Object> body(String query) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("query", query);
        return payload;
    }

    private Map<String, Object> body(String query, Map<String, Object> variables) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("query", query);
        payload.put("variables", variables);
        return payload;
    }

    private String addItem(String description, int quantity, String productCode) {
        Map<String, Object> input = new HashMap<>();
        input.put("generatorType", "calendar");
        input.put("description", description);
        input.put("quantity", quantity);
        if (productCode != null) {
            input.put("productCode", productCode);
        }
        input.put("configuration", "{\"theme\":\"modern\",\"year\":2026}");

        String mutation = """
                mutation AddToCart($input: AddToCartInput!) {
                    addToCart(input: $input) {
                        id
                        itemCount
                        items {
                            id
                            description
                            quantity
                        }
                    }
                }
                """;

        return graphqlRequest(testSessionId).body(body(mutation, Map.of("input", input))).when().post(GRAPHQL_PATH)
                .then().statusCode(200).body("errors", nullValue())
                .body("data.addToCart.items.size()", greaterThanOrEqualTo(1)).extract()
                .path("data.addToCart.items[0].id");
    }

    // ========================================================
    // Query: cart
    // ========================================================

    @Test
    void testGetCart_NewSession_ReturnsEmptyCart() {
        String query = """
                query {
                    cart {
                        id
                        itemCount
                        subtotal
                        taxAmount
                        totalAmount
                        items {
                            id
                        }
                    }
                }
                """;

        graphqlRequest(testSessionId).body(body(query)).when().post(GRAPHQL_PATH).then().statusCode(200)
                .body("errors", nullValue()).body("data.cart", notNullValue()).body("data.cart.id", notNullValue())
                .body("data.cart.itemCount", equalTo(0)).body("data.cart.subtotal", equalTo(0.0f))
                .body("data.cart.taxAmount", equalTo(0.0f)).body("data.cart.totalAmount", equalTo(0.0f))
                .body("data.cart.items", hasSize(0));
    }

    @Test
    void testGetCart_SameSessionTwice_ReturnsSameCartId() {
        String query = """
                query {
                    cart {
                        id
                    }
                }
                """;

        String firstId = graphqlRequest(testSessionId).body(body(query)).when().post(GRAPHQL_PATH).then()
                .statusCode(200).body("errors", nullValue()).extract().path("data.cart.id");

        String secondId = graphqlRequest(testSessionId).body(body(query)).when().post(GRAPHQL_PATH).then()
                .statusCode(200).body("errors", nullValue()).extract().path("data.cart.id");

        org.junit.jupiter.api.Assertions.assertEquals(firstId, secondId);
    }

    // ========================================================
    // Mutation: addToCart
    // ========================================================

    @Test
    void testAddToCart_WithValidProductCode_AddsItem() {
        Map<String, Object> input = new HashMap<>();
        input.put("generatorType", "calendar");
        input.put("description", "2026 Calendar");
        input.put("quantity", 2);
        input.put("productCode", "print");
        input.put("configuration", "{\"theme\":\"modern\"}");

        String mutation = """
                mutation AddToCart($input: AddToCartInput!) {
                    addToCart(input: $input) {
                        id
                        itemCount
                        subtotal
                        items {
                            id
                            description
                            quantity
                            unitPrice
                        }
                    }
                }
                """;

        graphqlRequest(testSessionId).body(body(mutation, Map.of("input", input))).when().post(GRAPHQL_PATH).then()
                .statusCode(200).body("errors", nullValue()).body("data.addToCart.itemCount", equalTo(2))
                .body("data.addToCart.items", hasSize(1))
                .body("data.addToCart.items[0].description", equalTo("2026 Calendar"))
                .body("data.addToCart.items[0].quantity", equalTo(2))
                .body("data.addToCart.items[0].unitPrice", equalTo(25.0f))
                .body("data.addToCart.subtotal", equalTo(50.0f));
    }

    @Test
    void testAddToCart_NoProductCode_UsesDefaultPricing() {
        Map<String, Object> input = new HashMap<>();
        input.put("generatorType", "calendar");
        input.put("description", "Default Pricing Calendar");
        input.put("quantity", 1);
        // No productCode provided -> falls back to default ("print" / $25)
        input.put("configuration", "{}");

        String mutation = """
                mutation AddToCart($input: AddToCartInput!) {
                    addToCart(input: $input) {
                        itemCount
                        subtotal
                        items {
                            unitPrice
                        }
                    }
                }
                """;

        graphqlRequest(testSessionId).body(body(mutation, Map.of("input", input))).when().post(GRAPHQL_PATH).then()
                .statusCode(200).body("errors", nullValue()).body("data.addToCart.itemCount", equalTo(1))
                .body("data.addToCart.items[0].unitPrice", equalTo(25.0f));
    }

    @Test
    void testAddToCart_NullQuantity_DefaultsToOne() {
        Map<String, Object> input = new HashMap<>();
        input.put("generatorType", "calendar");
        input.put("description", "Null Quantity Calendar");
        // quantity intentionally omitted (null) -> CartService defaults to 1
        input.put("productCode", "pdf");
        input.put("configuration", "{}");

        String mutation = """
                mutation AddToCart($input: AddToCartInput!) {
                    addToCart(input: $input) {
                        itemCount
                        items {
                            quantity
                            unitPrice
                        }
                    }
                }
                """;

        graphqlRequest(testSessionId).body(body(mutation, Map.of("input", input))).when().post(GRAPHQL_PATH).then()
                .statusCode(200).body("errors", nullValue()).body("data.addToCart.itemCount", equalTo(1))
                .body("data.addToCart.items[0].quantity", equalTo(1))
                .body("data.addToCart.items[0].unitPrice", equalTo(5.0f));
    }

    @Test
    void testAddToCart_InvalidProductCode_FallsBackToDefault() {
        Map<String, Object> input = new HashMap<>();
        input.put("generatorType", "calendar");
        input.put("description", "Invalid product code");
        input.put("quantity", 1);
        input.put("productCode", "bogus-code-that-does-not-exist");
        input.put("configuration", "{}");

        String mutation = """
                mutation AddToCart($input: AddToCartInput!) {
                    addToCart(input: $input) {
                        items {
                            unitPrice
                        }
                    }
                }
                """;

        graphqlRequest(testSessionId).body(body(mutation, Map.of("input", input))).when().post(GRAPHQL_PATH).then()
                .statusCode(200).body("errors", nullValue())
                // Falls back to default product ("print" -> $25)
                .body("data.addToCart.items[0].unitPrice", equalTo(25.0f));
    }

    @Test
    void testAddToCart_WithAssets_PersistsAssets() {
        Map<String, Object> asset = new HashMap<>();
        asset.put("assetKey", "main");
        asset.put("svgContent", "<svg xmlns='http://www.w3.org/2000/svg'><rect/></svg>");
        asset.put("widthInches", 35.0);
        asset.put("heightInches", 23.0);

        Map<String, Object> input = new HashMap<>();
        input.put("generatorType", "maze");
        input.put("description", "Hard Maze");
        input.put("quantity", 1);
        input.put("productCode", "print");
        input.put("configuration", "{\"difficulty\":\"hard\"}");
        input.put("assets", List.of(asset));

        String mutation = """
                mutation AddToCart($input: AddToCartInput!) {
                    addToCart(input: $input) {
                        itemCount
                        items {
                            id
                            description
                        }
                    }
                }
                """;

        graphqlRequest(testSessionId).body(body(mutation, Map.of("input", input))).when().post(GRAPHQL_PATH).then()
                .statusCode(200).body("errors", nullValue()).body("data.addToCart.itemCount", equalTo(1))
                .body("data.addToCart.items[0].description", equalTo("Hard Maze"));
    }

    @Test
    void testAddToCart_MultipleItems_AddedAsSeparateLineItems() {
        addItem("First Calendar", 1, "print");
        addItem("Second Calendar", 3, "pdf");

        String query = """
                query {
                    cart {
                        itemCount
                        items {
                            description
                        }
                    }
                }
                """;

        graphqlRequest(testSessionId).body(body(query)).when().post(GRAPHQL_PATH).then().statusCode(200)
                .body("errors", nullValue())
                // 1 + 3 = 4 individual units across 2 line items
                .body("data.cart.itemCount", equalTo(4)).body("data.cart.items", hasSize(2));
    }

    // ========================================================
    // Mutation: updateCartItemQuantity
    // ========================================================

    @Test
    void testUpdateCartItemQuantity_Increases() {
        String itemId = addItem("Quantity Update Calendar", 1, "print");

        String mutation = """
                mutation UpdateQty($itemId: String!, $quantity: Int!) {
                    updateCartItemQuantity(itemId: $itemId, quantity: $quantity) {
                        itemCount
                        items {
                            id
                            quantity
                        }
                    }
                }
                """;

        Map<String, Object> variables = Map.of("itemId", itemId, "quantity", 5);

        graphqlRequest(testSessionId).body(body(mutation, variables)).when().post(GRAPHQL_PATH).then().statusCode(200)
                .body("errors", nullValue()).body("data.updateCartItemQuantity.itemCount", equalTo(5))
                .body("data.updateCartItemQuantity.items[0].quantity", equalTo(5));
    }

    @Test
    void testUpdateCartItemQuantity_ZeroQuantity_RemovesItem() {
        String itemId = addItem("Will be removed via zero qty", 2, "print");

        String mutation = """
                mutation UpdateQty($itemId: String!, $quantity: Int!) {
                    updateCartItemQuantity(itemId: $itemId, quantity: $quantity) {
                        itemCount
                        items {
                            id
                        }
                    }
                }
                """;

        Map<String, Object> variables = Map.of("itemId", itemId, "quantity", 0);

        graphqlRequest(testSessionId).body(body(mutation, variables)).when().post(GRAPHQL_PATH).then().statusCode(200)
                .body("errors", nullValue()).body("data.updateCartItemQuantity.itemCount", equalTo(0))
                .body("data.updateCartItemQuantity.items", hasSize(0));
    }

    @Test
    void testUpdateCartItemQuantity_NonExistentItem_NoOp() {
        // Have at least one item to ensure the cart exists
        addItem("Untouched Calendar", 1, "print");

        String mutation = """
                mutation UpdateQty($itemId: String!, $quantity: Int!) {
                    updateCartItemQuantity(itemId: $itemId, quantity: $quantity) {
                        itemCount
                        items {
                            quantity
                        }
                    }
                }
                """;

        Map<String, Object> variables = Map.of("itemId", UUID.randomUUID().toString(), "quantity", 10);

        graphqlRequest(testSessionId).body(body(mutation, variables)).when().post(GRAPHQL_PATH).then().statusCode(200)
                .body("errors", nullValue())
                // Original item untouched
                .body("data.updateCartItemQuantity.itemCount", equalTo(1))
                .body("data.updateCartItemQuantity.items[0].quantity", equalTo(1));
    }

    @Test
    void testUpdateCartItemQuantity_InvalidUuid_ReturnsError() {
        addItem("Some Calendar", 1, "print");

        String mutation = """
                mutation UpdateQty($itemId: String!, $quantity: Int!) {
                    updateCartItemQuantity(itemId: $itemId, quantity: $quantity) {
                        itemCount
                    }
                }
                """;

        Map<String, Object> variables = Map.of("itemId", "not-a-uuid", "quantity", 3);

        graphqlRequest(testSessionId).body(body(mutation, variables)).when().post(GRAPHQL_PATH).then().statusCode(200)
                .body("errors", notNullValue());
    }

    // ========================================================
    // Mutation: removeFromCart
    // ========================================================

    @Test
    void testRemoveFromCart_RemovesExistingItem() {
        String itemId = addItem("To Be Removed", 2, "print");

        String mutation = """
                mutation Remove($itemId: String!) {
                    removeFromCart(itemId: $itemId) {
                        itemCount
                        items {
                            id
                        }
                    }
                }
                """;

        graphqlRequest(testSessionId).body(body(mutation, Map.of("itemId", itemId))).when().post(GRAPHQL_PATH).then()
                .statusCode(200).body("errors", nullValue()).body("data.removeFromCart.itemCount", equalTo(0))
                .body("data.removeFromCart.items", hasSize(0));
    }

    @Test
    void testRemoveFromCart_NonExistentItem_NoOp() {
        addItem("Survivor", 1, "print");

        String mutation = """
                mutation Remove($itemId: String!) {
                    removeFromCart(itemId: $itemId) {
                        itemCount
                    }
                }
                """;

        graphqlRequest(testSessionId).body(body(mutation, Map.of("itemId", UUID.randomUUID().toString()))).when()
                .post(GRAPHQL_PATH).then().statusCode(200).body("errors", nullValue())
                // Original item still present
                .body("data.removeFromCart.itemCount", equalTo(1));
    }

    @Test
    void testRemoveFromCart_InvalidUuid_ReturnsError() {
        String mutation = """
                mutation Remove($itemId: String!) {
                    removeFromCart(itemId: $itemId) {
                        itemCount
                    }
                }
                """;

        graphqlRequest(testSessionId).body(body(mutation, Map.of("itemId", "not-a-uuid"))).when().post(GRAPHQL_PATH)
                .then().statusCode(200).body("errors", notNullValue());
    }

    // ========================================================
    // Mutation: clearCart
    // ========================================================

    @Test
    void testClearCart_RemovesAllItems() {
        addItem("Item One", 1, "print");
        addItem("Item Two", 2, "pdf");

        String mutation = """
                mutation {
                    clearCart {
                        itemCount
                        subtotal
                        items {
                            id
                        }
                    }
                }
                """;

        graphqlRequest(testSessionId).body(body(mutation)).when().post(GRAPHQL_PATH).then().statusCode(200)
                .body("errors", nullValue()).body("data.clearCart.itemCount", equalTo(0))
                .body("data.clearCart.subtotal", equalTo(0.0f)).body("data.clearCart.items", hasSize(0));
    }

    @Test
    void testClearCart_EmptyCart_StillSucceeds() {
        String mutation = """
                mutation {
                    clearCart {
                        itemCount
                        items {
                            id
                        }
                    }
                }
                """;

        graphqlRequest(testSessionId).body(body(mutation)).when().post(GRAPHQL_PATH).then().statusCode(200)
                .body("errors", nullValue()).body("data.clearCart.itemCount", equalTo(0))
                .body("data.clearCart.items", hasSize(0));
    }

    // ========================================================
    // Session isolation
    // ========================================================

    @Test
    void testCart_DifferentSessions_AreIsolated() {
        String otherSession = "other-session-" + UUID.randomUUID();

        // Add an item to the primary session
        addItem("Primary Session Item", 1, "print");

        String query = """
                query {
                    cart {
                        id
                        itemCount
                    }
                }
                """;

        // Other session should have its own empty cart
        graphqlRequest(otherSession).body(body(query)).when().post(GRAPHQL_PATH).then().statusCode(200)
                .body("errors", nullValue()).body("data.cart.itemCount", equalTo(0));
    }
}
