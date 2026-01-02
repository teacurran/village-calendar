package villagecompute.calendar.api.rest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/** Shipping Resource - REST API for shipping rate calculations */
@Path("/shipping")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ShippingResource {

    private static final Logger LOG = Logger.getLogger(ShippingResource.class);

    private static final String US_COUNTRY_CODE = "US";
    private static final String CA_COUNTRY_CODE = "CA";
    private static final String MX_COUNTRY_CODE = "MX";

    @ConfigProperty(
            name = "calendar.shipping.domestic.standard",
            defaultValue = "5.99")
    BigDecimal domesticStandardRate;

    @ConfigProperty(
            name = "calendar.shipping.domestic.priority",
            defaultValue = "9.99")
    BigDecimal domesticPriorityRate;

    @ConfigProperty(
            name = "calendar.shipping.domestic.express",
            defaultValue = "14.99")
    BigDecimal domesticExpressRate;

    /** Calculate shipping options for calendar products */
    @POST
    @Path("/calculate-calendar")
    public Response calculateCalendarShipping(ShippingRequest request) {
        LOG.infof("Calculating shipping for country: %s, state: %s", request.country, request.state);

        if (request.country == null || request.country.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Country is required").build();
        }

        String country = request.country.trim().toUpperCase();

        // Check if we ship to this country
        if (!US_COUNTRY_CODE.equals(country)) {
            String message = switch (country) {
                case CA_COUNTRY_CODE -> "Shipping to Canada is coming soon. Please check back later.";
                case MX_COUNTRY_CODE -> "Shipping to Mexico is coming soon. Please check back later.";
                default -> "We currently only ship within the United States.";
            };
            return Response.status(Response.Status.BAD_REQUEST).entity(message).build();
        }

        // Build shipping options for US
        List<Map<String, Object>> options = new ArrayList<>();

        // Standard shipping
        Map<String, Object> standard = new HashMap<>();
        standard.put("id", "standard");
        standard.put("name", "Standard Shipping");
        standard.put("description", "5-7 business days");
        standard.put("price", domesticStandardRate);
        options.add(standard);

        // Priority shipping
        Map<String, Object> priority = new HashMap<>();
        priority.put("id", "priority");
        priority.put("name", "Priority Shipping");
        priority.put("description", "2-3 business days");
        priority.put("price", domesticPriorityRate);
        options.add(priority);

        // Express shipping
        Map<String, Object> express = new HashMap<>();
        express.put("id", "express");
        express.put("name", "Express Shipping");
        express.put("description", "1-2 business days");
        express.put("price", domesticExpressRate);
        options.add(express);

        Map<String, Object> response = new HashMap<>();
        response.put("options", options);

        return Response.ok(response).build();
    }

    /** Request object for shipping calculation */
    public static class ShippingRequest {
        public String country;
        public String state;
        public String postalCode;
    }
}
