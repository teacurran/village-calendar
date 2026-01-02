package villagecompute.calendar.api.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import villagecompute.calendar.data.models.Cart;
import villagecompute.calendar.data.models.CartItem;
import villagecompute.calendar.data.models.ItemAsset;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests for AssetResource REST endpoints. These endpoints provide access to stored SVG assets and cart item thumbnails.
 */
@QuarkusTest
class AssetResourceTest {

    private static final String CONTENT_TYPE_SVG = "image/svg+xml";
    private static final String TEST_SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\"><text>Test</text></svg>";

    private UUID testAssetId;
    private UUID testCartItemId;

    @BeforeEach
    void setUp() {
        // Use QuarkusTransaction to commit data before tests run
        QuarkusTransaction.requiringNew().run(() -> {
            // Create a test cart first (required for cart items)
            Cart testCart = new Cart();
            testCart.sessionId = "test-session-" + System.currentTimeMillis();
            testCart.persist();

            // Create a test asset
            ItemAsset testAsset = new ItemAsset();
            testAsset.svgContent = TEST_SVG;
            testAsset.contentType = "image/svg+xml";
            testAsset.assetKey = ItemAsset.KEY_MAIN;
            testAsset.persist();

            // Create a test cart item with an asset
            CartItem testCartItem = new CartItem();
            testCartItem.cart = testCart;
            testCartItem.generatorType = "calendar";
            testCartItem.quantity = 1;
            testCartItem.unitPrice = new BigDecimal("29.99");
            testCartItem.assets.add(testAsset);
            testCartItem.persist();

            testAssetId = testAsset.id;
            testCartItemId = testCartItem.id;
        });
    }

    // ========== GET /api/assets/{assetId} TESTS ==========

    @Test
    void testGetAssetContent_ValidAsset_ReturnsSvg() {
        given().when().get("/api/assets/" + testAssetId).then().statusCode(200).contentType(CONTENT_TYPE_SVG)
                .body(containsString("<svg"));
    }

    @Test
    void testGetAssetContent_ValidAsset_HasCacheControlHeader() {
        given().when().get("/api/assets/" + testAssetId).then().statusCode(200).header("Cache-Control",
                containsString("max-age=31536000"));
    }

    @Test
    void testGetAssetContent_InvalidUuid_Returns400() {
        given().when().get("/api/assets/not-a-uuid").then().statusCode(400)
                .body(containsString("Invalid asset ID format"));
    }

    @Test
    void testGetAssetContent_NonExistentAsset_Returns404() {
        UUID nonExistent = UUID.randomUUID();
        given().when().get("/api/assets/" + nonExistent).then().statusCode(404).body(containsString("Asset not found"));
    }

    // ========== GET /api/cart-items/{itemId}/thumbnail.svg TESTS ==========

    @Test
    void testGetCartItemThumbnail_ValidCartItemWithAsset_ReturnsSvg() {
        given().when().get("/api/cart-items/" + testCartItemId + "/thumbnail.svg").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG).body(containsString("<svg"));
    }

    @Test
    void testGetCartItemThumbnail_ValidCartItemWithAsset_HasCacheControlHeader() {
        given().when().get("/api/cart-items/" + testCartItemId + "/thumbnail.svg").then().statusCode(200)
                .header("Cache-Control", containsString("max-age=31536000"));
    }

    @Test
    void testGetCartItemThumbnail_InvalidUuid_Returns400() {
        given().when().get("/api/cart-items/not-a-uuid/thumbnail.svg").then().statusCode(400)
                .body(containsString("Invalid cart item ID format"));
    }

    @Test
    void testGetCartItemThumbnail_NonExistentCartItem_Returns404() {
        UUID nonExistent = UUID.randomUUID();
        given().when().get("/api/cart-items/" + nonExistent + "/thumbnail.svg").then().statusCode(404)
                .body(containsString("Cart item not found"));
    }

    @Test
    void testGetCartItemThumbnail_CartItemWithoutSvg_Returns404() {
        // Create a cart item without any assets or SVG configuration
        UUID emptyCartItemId = QuarkusTransaction.requiringNew().call(() -> {
            Cart emptyCart = new Cart();
            emptyCart.sessionId = "empty-cart-" + System.currentTimeMillis();
            emptyCart.persist();

            CartItem emptyCartItem = new CartItem();
            emptyCartItem.cart = emptyCart;
            emptyCartItem.generatorType = "calendar";
            emptyCartItem.quantity = 1;
            emptyCartItem.unitPrice = new BigDecimal("29.99");
            emptyCartItem.persist();

            return emptyCartItem.id;
        });

        given().when().get("/api/cart-items/" + emptyCartItemId + "/thumbnail.svg").then().statusCode(404)
                .body(containsString("No thumbnail available"));
    }

    @Test
    void testGetCartItemThumbnail_CartItemWithSvgInConfiguration_ReturnsSvg() {
        // Create a cart item with SVG in configuration (legacy format)
        UUID legacyCartItemId = QuarkusTransaction.requiringNew().call(() -> {
            Cart legacyCart = new Cart();
            legacyCart.sessionId = "legacy-cart-" + System.currentTimeMillis();
            legacyCart.persist();

            CartItem legacyCartItem = new CartItem();
            legacyCartItem.cart = legacyCart;
            legacyCartItem.generatorType = "calendar";
            legacyCartItem.quantity = 1;
            legacyCartItem.unitPrice = new BigDecimal("29.99");
            legacyCartItem.configuration = "{\"svgContent\":\"<svg><text>Legacy</text></svg>\"}";
            legacyCartItem.persist();

            return legacyCartItem.id;
        });

        given().when().get("/api/cart-items/" + legacyCartItemId + "/thumbnail.svg").then().statusCode(200)
                .contentType(CONTENT_TYPE_SVG).body(containsString("<svg"));
    }
}
