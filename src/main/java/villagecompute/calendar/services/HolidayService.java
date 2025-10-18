package villagecompute.calendar.services;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Service for calculating holidays for different countries
 */
@ApplicationScoped
public class HolidayService {

    /**
     * Get US Federal Holidays for a given year
     */
    public Map<String, String> getUSHolidays(int year) {
        Map<String, String> holidays = new HashMap<>();

        // New Year's Day - January 1
        holidays.put(formatDate(LocalDate.of(year, Month.JANUARY, 1)), "New Year's Day");

        // Martin Luther King Jr. Day - 3rd Monday in January
        LocalDate mlkDay = LocalDate.of(year, Month.JANUARY, 1)
            .with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.MONDAY));
        holidays.put(formatDate(mlkDay), "Martin Luther King Jr. Day");

        // Presidents' Day - 3rd Monday in February
        LocalDate presidentsDay = LocalDate.of(year, Month.FEBRUARY, 1)
            .with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.MONDAY));
        holidays.put(formatDate(presidentsDay), "Presidents' Day");

        // Memorial Day - Last Monday in May
        LocalDate memorialDay = LocalDate.of(year, Month.MAY, 1)
            .with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY));
        holidays.put(formatDate(memorialDay), "Memorial Day");

        // Juneteenth - June 19
        holidays.put(formatDate(LocalDate.of(year, Month.JUNE, 19)), "Juneteenth");

        // Independence Day - July 4
        holidays.put(formatDate(LocalDate.of(year, Month.JULY, 4)), "Independence Day");

        // Labor Day - 1st Monday in September
        LocalDate laborDay = LocalDate.of(year, Month.SEPTEMBER, 1)
            .with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));
        holidays.put(formatDate(laborDay), "Labor Day");

        // Columbus Day - 2nd Monday in October
        LocalDate columbusDay = LocalDate.of(year, Month.OCTOBER, 1)
            .with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.MONDAY));
        holidays.put(formatDate(columbusDay), "Columbus Day");

        // Veterans Day - November 11
        holidays.put(formatDate(LocalDate.of(year, Month.NOVEMBER, 11)), "Veterans Day");

        // Thanksgiving - 4th Thursday in November
        LocalDate thanksgiving = LocalDate.of(year, Month.NOVEMBER, 1)
            .with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY));
        holidays.put(formatDate(thanksgiving), "Thanksgiving");

        // Christmas Day - December 25
        holidays.put(formatDate(LocalDate.of(year, Month.DECEMBER, 25)), "Christmas Day");

        return holidays;
    }

    /**
     * Get holidays for any country (currently only supports US)
     */
    public Map<String, String> getHolidays(int year, String country) {
        if ("US".equalsIgnoreCase(country)) {
            return getUSHolidays(year);
        }

        // Default to US holidays if country not supported
        return getUSHolidays(year);
    }

    /**
     * Format date as YYYY-MM-DD
     */
    private String formatDate(LocalDate date) {
        return date.toString();
    }
}
