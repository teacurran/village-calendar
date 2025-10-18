package villagecompute.calendar.services;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import villagecompute.calendar.data.models.CalendarOrder;

import java.math.BigDecimal;

/**
 * Service for calculating shipping costs based on destination address.
 * Handles domestic (US) and international shipping rate calculations.
 * For MVP, only domestic US shipping is supported - international orders will be rejected.
 */
@ApplicationScoped
public class ShippingService {

    private static final Logger LOG = Logger.getLogger(ShippingService.class);

    // Domestic shipping rate constants
    private static final String US_COUNTRY_CODE = "US";

    // Inject shipping rates from configuration
    @ConfigProperty(name = "calendar.shipping.domestic.standard", defaultValue = "5.99")
    BigDecimal domesticStandardRate;

    @ConfigProperty(name = "calendar.shipping.domestic.priority", defaultValue = "9.99")
    BigDecimal domesticPriorityRate;

    @ConfigProperty(name = "calendar.shipping.domestic.express", defaultValue = "14.99")
    BigDecimal domesticExpressRate;

    @ConfigProperty(name = "calendar.shipping.international", defaultValue = "19.99")
    BigDecimal internationalRate;

    /**
     * Calculate shipping cost for an order based on the shipping address.
     * For MVP, this uses a simple rate table:
     * - Domestic US: $5.99 (standard rate)
     * - International: rejected (not supported in MVP)
     *
     * Future enhancements could include:
     * - Multiple shipping tiers (standard/priority/express)
     * - Weight-based calculations
     * - Regional rate variations
     * - Real-time carrier API integration
     *
     * @param order Order with shipping address
     * @return Calculated shipping cost
     * @throws IllegalArgumentException if shipping address is missing or invalid
     * @throws IllegalStateException if international shipping is attempted (MVP limitation)
     */
    public BigDecimal calculateShippingCost(CalendarOrder order) {
        LOG.debugf("Calculating shipping cost for order %s", order.id);

        // Validate that shipping address exists
        if (order.shippingAddress == null) {
            LOG.errorf("Order %s has no shipping address", order.id);
            throw new IllegalArgumentException("Shipping address is required");
        }

        JsonNode address = order.shippingAddress;

        // Validate that country field exists
        if (!address.has("country") || address.get("country").isNull()) {
            LOG.errorf("Order %s shipping address missing country field", order.id);
            throw new IllegalArgumentException("Country is required in shipping address");
        }

        // Extract and normalize country code
        String country = address.get("country").asText("").trim().toUpperCase();

        if (country.isEmpty()) {
            LOG.errorf("Order %s has empty country code", order.id);
            throw new IllegalArgumentException("Country cannot be empty");
        }

        LOG.debugf("Order %s shipping to country: %s", order.id, country);

        // Calculate rate based on destination
        BigDecimal shippingCost;

        if (US_COUNTRY_CODE.equals(country)) {
            // Domestic US shipping - use standard rate for MVP
            shippingCost = domesticStandardRate;
            LOG.infof("Calculated domestic shipping cost $%.2f for order %s (country: %s)",
                shippingCost, order.id, country);
        } else {
            // International shipping not supported in MVP
            LOG.errorf("International shipping requested for order %s (country: %s) - not supported in MVP",
                order.id, country);
            throw new IllegalStateException(
                String.format("International shipping to %s is not supported. Only US addresses are accepted.", country)
            );
        }

        return shippingCost;
    }

    /**
     * Calculate shipping cost for a specific address.
     * This is a convenience method that extracts the country from the address.
     * For production use, prefer calculateShippingCost(CalendarOrder) which has full context.
     *
     * @param address Shipping address as JsonNode
     * @return Calculated shipping cost
     * @throws IllegalArgumentException if address is missing or invalid
     * @throws IllegalStateException if international shipping is attempted
     */
    public BigDecimal calculateRate(JsonNode address) {
        LOG.debugf("Calculating shipping rate for address");

        // Validate that address exists
        if (address == null) {
            LOG.error("Shipping address is null");
            throw new IllegalArgumentException("Shipping address is required");
        }

        // Validate that country field exists
        if (!address.has("country") || address.get("country").isNull()) {
            LOG.error("Shipping address missing country field");
            throw new IllegalArgumentException("Country is required in shipping address");
        }

        // Extract and normalize country code
        String country = address.get("country").asText("").trim().toUpperCase();

        if (country.isEmpty()) {
            LOG.error("Country code is empty");
            throw new IllegalArgumentException("Country cannot be empty");
        }

        LOG.debugf("Calculating rate for country: %s", country);

        // Calculate rate based on destination
        BigDecimal rate;

        if (US_COUNTRY_CODE.equals(country)) {
            // Domestic US shipping - use standard rate for MVP
            rate = domesticStandardRate;
            LOG.debugf("Domestic rate: $%.2f", rate);
        } else {
            // International shipping not supported in MVP
            LOG.errorf("International shipping requested (country: %s) - not supported in MVP", country);
            throw new IllegalStateException(
                String.format("International shipping to %s is not supported. Only US addresses are accepted.", country)
            );
        }

        return rate;
    }
}
