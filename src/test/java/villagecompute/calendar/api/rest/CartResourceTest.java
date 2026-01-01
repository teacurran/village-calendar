package villagecompute.calendar.api.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import villagecompute.calendar.data.models.Cart;
import villagecompute.calendar.data.models.CartItem;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/** Integration tests for CartResource REST API. Tests cart operations via HTTP endpoints. */
@QuarkusTest
class CartResourceTest {

    private static final String SESSION_HEADER = "X-Session-ID";
    private static final String CART_ITEMS_PATH = "/api/cart/items";
    private static final String CART_CLEAR_PATH = "/api/cart/clear";
    private static final String GENERATOR_TYPE_KEY = "generatorType";
    private static final String GENERATOR_TYPE_CALENDAR = "calendar";
    private static final String DESCRIPTION_KEY = "description";
    private static final String QUANTITY_KEY = "quantity";

    private String testSessionId;

    @BeforeEach
    void setUp() {
        testSessionId = "test-cart-session-" + UUID.randomUUID();
    }

    @AfterEach
    @Transactional
    void tearDown() {
        CartItem.deleteAll();
        Cart.deleteAll();
    }

    private RequestSpecification withSession(String sessionId) {
        return given().header(SESSION_HEADER, sessionId);
    }

    private RequestSpecification withSessionAndJson(String sessionId) {
        return withSession(sessionId).contentType(ContentType.JSON);
    }

    private Map<String, Object> createCalendarRequest(String description, int quantity) {
        Map<String, Object> request = new HashMap<>();
        request.put(GENERATOR_TYPE_KEY, GENERATOR_TYPE_CALENDAR);
        request.put(DESCRIPTION_KEY, description);
        request.put(QUANTITY_KEY, quantity);
        return request;
    }

    private String addItemAndGetId(String description, int quantity) {
        Map<String, Object> request = createCalendarRequest(description, quantity);
        return withSessionAndJson(testSessionId).body(request).when().post(CART_ITEMS_PATH).then().statusCode(200)
                .extract().path("cart.items[0].id");
    }

    // ========== GET CART ITEMS TESTS ==========

    @Test
    void testGetCartItems_EmptyCart() {
        withSession(testSessionId).when().get(CART_ITEMS_PATH).then().statusCode(200).body("itemCount", equalTo(0))
                .body("items", hasSize(0)).body("subtotal", equalTo(0.0f)).body("taxAmount", equalTo(0.0f))
                .body("total", equalTo(0.0f));
    }

    @Test
    void testGetCartItems_WithItems() {
        Map<String, Object> addRequest = createCalendarRequest("Test Calendar", 2);
        addRequest.put("productCode", "print");

        withSessionAndJson(testSessionId).body(addRequest).when().post(CART_ITEMS_PATH).then().statusCode(200);

        withSession(testSessionId).when().get(CART_ITEMS_PATH).then().statusCode(200).body("itemCount", equalTo(2))
                .body("items", hasSize(1)).body("items[0].quantity", equalTo(2)).body("subtotal", greaterThan(0.0f));
    }

    // ========== ADD TO CART TESTS ==========

    @Test
    void testAddToCart_WithGeneratorType() {
        Map<String, Object> request = createCalendarRequest("Modern Calendar 2025", 1);
        request.put("productCode", "print");

        withSessionAndJson(testSessionId).body(request).when().post(CART_ITEMS_PATH).then().statusCode(200)
                .body("success", equalTo(true)).body("message", equalTo("Item added to cart"))
                .body("itemCount", equalTo(1)).body("cart.items", hasSize(1));
    }

    @Test
    void testAddToCart_WithProductId_BackwardsCompatibility() {
        Map<String, Object> request = new HashMap<>();
        request.put("productId", GENERATOR_TYPE_CALENDAR);
        request.put("templateName", "Classic Template");
        request.put(QUANTITY_KEY, 3);

        withSessionAndJson(testSessionId).body(request).when().post(CART_ITEMS_PATH).then().statusCode(200)
                .body("success", equalTo(true)).body("itemCount", equalTo(3))
                .body("cart.items[0].quantity", equalTo(3));
    }

    @Test
    void testAddToCart_WithConfigurationString() {
        Map<String, Object> request = createCalendarRequest("Configured Calendar", 1);
        request.put("configuration", "{\"theme\":\"modern\",\"year\":2025}");

        withSessionAndJson(testSessionId).body(request).when().post(CART_ITEMS_PATH).then().statusCode(200)
                .body("success", equalTo(true));
    }

    @Test
    void testAddToCart_WithConfigurationObject() {
        Map<String, Object> request = createCalendarRequest("Configured Calendar", 1);

        Map<String, Object> config = new HashMap<>();
        config.put("theme", "classic");
        config.put("year", 2026);
        request.put("configuration", config);

        withSessionAndJson(testSessionId).body(request).when().post(CART_ITEMS_PATH).then().statusCode(200)
                .body("success", equalTo(true));
    }

    @Test
    void testAddToCart_WithDefaultQuantity() {
        Map<String, Object> request = new HashMap<>();
        request.put(GENERATOR_TYPE_KEY, GENERATOR_TYPE_CALENDAR);
        request.put(DESCRIPTION_KEY, "Calendar");

        withSessionAndJson(testSessionId).body(request).when().post(CART_ITEMS_PATH).then().statusCode(200)
                .body("success", equalTo(true)).body("itemCount", equalTo(1));
    }

    @Test
    void testAddToCart_MissingGeneratorTypeAndProductId_ReturnsBadRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put(DESCRIPTION_KEY, "Calendar");
        request.put(QUANTITY_KEY, 1);

        withSessionAndJson(testSessionId).body(request).when().post(CART_ITEMS_PATH).then().statusCode(400)
                .body("success", equalTo(false)).body("message", equalTo("generatorType or productId is required"));
    }

    @Test
    void testAddToCart_MultipleItems() {
        Map<String, Object> request1 = createCalendarRequest("Calendar 1", 1);
        withSessionAndJson(testSessionId).body(request1).when().post(CART_ITEMS_PATH).then().statusCode(200);

        Map<String, Object> request2 = createCalendarRequest("Calendar 2", 2);
        withSessionAndJson(testSessionId).body(request2).when().post(CART_ITEMS_PATH).then().statusCode(200)
                .body("itemCount", equalTo(3)).body("cart.items", hasSize(2));
    }

    // ========== REMOVE FROM CART TESTS ==========

    @Test
    void testRemoveFromCart_Success() {
        String itemId = addItemAndGetId("Test Calendar", 1);

        withSession(testSessionId).when().delete(CART_ITEMS_PATH + "/" + itemId).then().statusCode(200)
                .body("success", equalTo(true)).body("itemCount", equalTo(0));
    }

    // ========== UPDATE QUANTITY TESTS ==========

    @Test
    void testUpdateQuantity_Success() {
        String itemId = addItemAndGetId("Test Calendar", 1);

        Map<String, Object> updateRequest = new HashMap<>();
        updateRequest.put(QUANTITY_KEY, 5);

        withSessionAndJson(testSessionId).body(updateRequest).when().patch(CART_ITEMS_PATH + "/" + itemId).then()
                .statusCode(200).body("success", equalTo(true)).body("itemCount", equalTo(5));
    }

    @Test
    void testUpdateQuantity_ToZero_RemovesItem() {
        String itemId = addItemAndGetId("Test Calendar", 3);

        Map<String, Object> updateRequest = new HashMap<>();
        updateRequest.put(QUANTITY_KEY, 0);

        withSessionAndJson(testSessionId).body(updateRequest).when().patch(CART_ITEMS_PATH + "/" + itemId).then()
                .statusCode(200).body("success", equalTo(true)).body("itemCount", equalTo(0))
                .body("cart.items", hasSize(0));
    }

    // ========== CLEAR CART TESTS ==========

    @Test
    void testClearCart_Success() {
        Map<String, Object> addRequest = createCalendarRequest("Test Calendar", 2);
        withSessionAndJson(testSessionId).body(addRequest).when().post(CART_ITEMS_PATH).then().statusCode(200);

        withSession(testSessionId).when().delete(CART_CLEAR_PATH).then().statusCode(200).body("success", equalTo(true))
                .body("itemCount", equalTo(0)).body("cart.items", hasSize(0));
    }

    @Test
    void testClearCart_AlreadyEmpty() {
        withSession(testSessionId).when().delete(CART_CLEAR_PATH).then().statusCode(200).body("success", equalTo(true))
                .body("itemCount", equalTo(0));
    }

    // ========== SESSION ISOLATION TESTS ==========

    @Test
    void testSessionIsolation_DifferentSessionsHaveDifferentCarts() {
        String sessionId1 = "session-1-" + UUID.randomUUID();
        String sessionId2 = "session-2-" + UUID.randomUUID();

        Map<String, Object> request = createCalendarRequest("Session 1 Calendar", 3);
        withSessionAndJson(sessionId1).body(request).when().post(CART_ITEMS_PATH).then().statusCode(200);

        withSession(sessionId1).when().get(CART_ITEMS_PATH).then().statusCode(200).body("itemCount", equalTo(3));

        withSession(sessionId2).when().get(CART_ITEMS_PATH).then().statusCode(200).body("itemCount", equalTo(0));
    }
}
