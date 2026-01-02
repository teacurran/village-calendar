package villagecompute.calendar.api.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Tests for ShippingResource REST endpoints. These endpoints calculate shipping rates for calendar products.
 */
@QuarkusTest
class ShippingResourceTest {

    // ========== POST /api/shipping/calculate-calendar TESTS ==========

    @Test
    void testCalculateCalendarShipping_USAddress_ReturnsOptions() {
        Map<String, Object> request = new HashMap<>();
        request.put("country", "US");
        request.put("state", "TN");
        request.put("postalCode", "37201");

        given().contentType(ContentType.JSON).body(request).when().post("/api/shipping/calculate-calendar").then()
                .statusCode(200).contentType(ContentType.JSON).body("options", hasSize(3))
                .body("options[0].id", equalTo("standard")).body("options[0].name", equalTo("Standard Shipping"))
                .body("options[0].price", notNullValue()).body("options[1].id", equalTo("priority"))
                .body("options[1].name", equalTo("Priority Shipping")).body("options[2].id", equalTo("express"))
                .body("options[2].name", equalTo("Express Shipping"));
    }

    @Test
    void testCalculateCalendarShipping_LowercaseCountry_Works() {
        Map<String, Object> request = new HashMap<>();
        request.put("country", "us");
        request.put("state", "CA");

        given().contentType(ContentType.JSON).body(request).when().post("/api/shipping/calculate-calendar").then()
                .statusCode(200).body("options", hasSize(3));
    }

    @Test
    void testCalculateCalendarShipping_CountryWithSpaces_Works() {
        Map<String, Object> request = new HashMap<>();
        request.put("country", "  US  ");

        given().contentType(ContentType.JSON).body(request).when().post("/api/shipping/calculate-calendar").then()
                .statusCode(200).body("options", hasSize(3));
    }

    @Test
    void testCalculateCalendarShipping_NoCountry_Returns400() {
        Map<String, Object> request = new HashMap<>();
        request.put("state", "TN");

        given().contentType(ContentType.JSON).body(request).when().post("/api/shipping/calculate-calendar").then()
                .statusCode(400);
    }

    @Test
    void testCalculateCalendarShipping_EmptyCountry_Returns400() {
        Map<String, Object> request = new HashMap<>();
        request.put("country", "");

        given().contentType(ContentType.JSON).body(request).when().post("/api/shipping/calculate-calendar").then()
                .statusCode(400);
    }

    @Test
    void testCalculateCalendarShipping_BlankCountry_Returns400() {
        Map<String, Object> request = new HashMap<>();
        request.put("country", "   ");

        given().contentType(ContentType.JSON).body(request).when().post("/api/shipping/calculate-calendar").then()
                .statusCode(400);
    }

    @Test
    void testCalculateCalendarShipping_Canada_Returns400WithMessage() {
        Map<String, Object> request = new HashMap<>();
        request.put("country", "CA");

        given().contentType(ContentType.JSON).body(request).when().post("/api/shipping/calculate-calendar").then()
                .statusCode(400).body(containsString("Canada"));
    }

    @Test
    void testCalculateCalendarShipping_Mexico_Returns400WithMessage() {
        Map<String, Object> request = new HashMap<>();
        request.put("country", "MX");

        given().contentType(ContentType.JSON).body(request).when().post("/api/shipping/calculate-calendar").then()
                .statusCode(400).body(containsString("Mexico"));
    }

    @Test
    void testCalculateCalendarShipping_UnsupportedCountry_Returns400() {
        Map<String, Object> request = new HashMap<>();
        request.put("country", "UK");

        given().contentType(ContentType.JSON).body(request).when().post("/api/shipping/calculate-calendar").then()
                .statusCode(400).body(containsString("United States"));
    }

    @Test
    void testCalculateCalendarShipping_ReturnsCorrectPricing() {
        Map<String, Object> request = new HashMap<>();
        request.put("country", "US");

        given().contentType(ContentType.JSON).body(request).when().post("/api/shipping/calculate-calendar").then()
                .statusCode(200).body("options[0].description", equalTo("5-7 business days"))
                .body("options[1].description", equalTo("2-3 business days"))
                .body("options[2].description", equalTo("1-2 business days"));
    }

    @Test
    void testCalculateCalendarShipping_MinimalRequest_Works() {
        Map<String, Object> request = new HashMap<>();
        request.put("country", "US");
        // No state or postalCode

        given().contentType(ContentType.JSON).body(request).when().post("/api/shipping/calculate-calendar").then()
                .statusCode(200).body("options", hasSize(3));
    }
}
