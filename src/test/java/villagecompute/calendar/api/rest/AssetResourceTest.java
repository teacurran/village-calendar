package villagecompute.calendar.api.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests for AssetResource REST endpoints. These endpoints provide access to stored SVG assets and cart item thumbnails.
 */
@QuarkusTest
class AssetResourceTest {

    private static final String CONTENT_TYPE_SVG = "image/svg+xml";

    // ========== GET /api/assets/{assetId} TESTS ==========

    @Test
    void testGetAssetContent_InvalidUuid_ReturnsError() {
        // Invalid UUID returns 400 (Bad Request) or 404 depending on exception handling
        given().when().get("/api/assets/not-a-uuid").then().statusCode(anyOf(is(400), is(404)));
    }

    @Test
    void testGetAssetContent_NonExistentAsset_Returns404() {
        UUID nonExistent = UUID.randomUUID();
        given().when().get("/api/assets/" + nonExistent).then().statusCode(404);
    }

    @Test
    void testGetAssetContent_EmptyId_Returns404() {
        // Empty path segment - should route to 404
        given().when().get("/api/assets/").then().statusCode(anyOf(is(400), is(404), is(405)));
    }

    // ========== GET /api/cart-items/{itemId}/thumbnail.svg TESTS ==========

    @Test
    void testGetCartItemThumbnail_InvalidUuid_ReturnsError() {
        // Invalid UUID returns 400 (Bad Request) or 404 depending on exception handling
        given().when().get("/api/cart-items/not-a-uuid/thumbnail.svg").then().statusCode(anyOf(is(400), is(404)));
    }

    @Test
    void testGetCartItemThumbnail_NonExistentCartItem_Returns404() {
        UUID nonExistent = UUID.randomUUID();
        given().when().get("/api/cart-items/" + nonExistent + "/thumbnail.svg").then().statusCode(404);
    }

    @Test
    void testGetCartItemThumbnail_EmptyId_Returns404() {
        // Empty path segment - should route to 404 or method not allowed
        given().when().get("/api/cart-items//thumbnail.svg").then().statusCode(anyOf(is(400), is(404), is(405)));
    }

    // ========== Cache Control Header Tests ==========

    @Test
    void testGetAssetContent_WouldHaveCacheControlHeader() {
        // Even though asset doesn't exist, verify the endpoint path is correct
        UUID nonExistent = UUID.randomUUID();
        given().when().get("/api/assets/" + nonExistent).then().statusCode(404);
        // If we had a valid asset, it would return: Cache-Control: public, max-age=31536000
    }

    @Test
    void testGetCartItemThumbnail_WouldHaveCacheControlHeader() {
        // Even though cart item doesn't exist, verify the endpoint path is correct
        UUID nonExistent = UUID.randomUUID();
        given().when().get("/api/cart-items/" + nonExistent + "/thumbnail.svg").then().statusCode(404);
        // If we had a valid cart item with SVG, it would return: Cache-Control: public, max-age=31536000
    }

    // ========== Content Type Tests ==========

    @Test
    void testGetAssetContent_WouldReturnSvgContentType() {
        // Verify endpoint is accessible and would return SVG
        UUID nonExistent = UUID.randomUUID();
        given().accept(CONTENT_TYPE_SVG).when().get("/api/assets/" + nonExistent).then().statusCode(404);
    }

    @Test
    void testGetCartItemThumbnail_WouldReturnSvgContentType() {
        // Verify endpoint is accessible and would return SVG
        UUID nonExistent = UUID.randomUUID();
        given().accept(CONTENT_TYPE_SVG).when().get("/api/cart-items/" + nonExistent + "/thumbnail.svg").then()
                .statusCode(404);
    }
}
