package villagecompute.calendar.data.repositories;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Utility class for cleaning test data in the correct order to avoid foreign key violations.
 */
@ApplicationScoped
public class TestDataCleaner {

    @Inject
    CalendarOrderRepository orderRepository;

    @Inject
    UserCalendarRepository calendarRepository;

    @Inject
    CalendarUserRepository userRepository;

    @Inject
    CalendarTemplateRepository templateRepository;

    /**
     * Delete all test data in the correct order to avoid foreign key violations.
     * Order: CalendarOrder → UserCalendar → CalendarUser, CalendarTemplate
     */
    @Transactional
    public void deleteAll() {
        orderRepository.deleteAll();
        calendarRepository.deleteAll();
        userRepository.deleteAll();
        templateRepository.deleteAll();
    }
}
