package villagecompute.calendar.data;

import io.quarkiverse.roq.data.runtime.annotations.DataMapping;
import java.util.List;

/**
 * Data mapping for calendar products loaded from data/calendars.json.
 * ROQ will automatically load this data and make it available in templates via cdi:calendars.
 */
@DataMapping(value = "calendars", parentArray = true)
public record CalendarProductData(List<CalendarProduct> list) {

    /**
     * Individual calendar product data.
     */
    public record CalendarProduct(
        String slug,
        String templateId,
        String name,
        String title,
        String description,
        String ogDescription,
        String keywords,
        String priceFormatted,
        int year
    ) {}
}
