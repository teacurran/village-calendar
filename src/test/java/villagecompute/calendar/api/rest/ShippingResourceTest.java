package villagecompute.calendar.api.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

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

    /**
     * Verifies that a missing, empty, or blank country value returns HTTP 400. Consolidates three previously-similar
     * tests (S5976).
     */
    @ParameterizedTest(
            name = "country=[{0}] returns 400")
    @NullSource
    @ValueSource(
            strings = {"", "   "})
    void testCalculateCalendarShipping_InvalidCountry_Returns400(String country) {
        Map<String, Object> request = new HashMap<>();
        if (country != null) {
            request.put("country", country);
        }
        // For null case: also exercise the original "no country key with state present" scenario
        if (country == null) {
            request.put("state", "TN");
        }

        given().contentType(ContentType.JSON).body(request).when().post("/api/shipping/calculate-calendar").then()
                .statusCode(400);
    }

    /**
     * Verifies that unsupported countries return HTTP 400 with an explanatory message. Consolidates three
     * previously-similar tests (S5976).
     */
    @ParameterizedTest(
            name = "country={0} returns 400 with message containing \"{1}\"")
    @CsvSource({"CA, Canada", "MX, Mexico", "UK, United States"})
    void testCalculateCalendarShipping_UnsupportedCountry_Returns400WithMessage(String countryCode,
            String expectedMessageFragment) {
        Map<String, Object> request = new HashMap<>();
        request.put("country", countryCode);

        given().contentType(ContentType.JSON).body(request).when().post("/api/shipping/calculate-calendar").then()
                .statusCode(400).body(containsString(expectedMessageFragment));
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
