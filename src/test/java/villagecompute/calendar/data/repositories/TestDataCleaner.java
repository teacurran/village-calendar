package villagecompute.calendar.data.repositories;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/** Utility class for cleaning test data in the correct order to avoid foreign key violations. */
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

    @Inject
    EntityManager entityManager;

    /**
     * Delete all test data in the correct order to avoid foreign key violations. Order: CalendarOrder → UserCalendar →
     * PageView → AnalyticsRollup → DelayedJob → CalendarUser → CalendarTemplate
     */
    @Transactional
    public void deleteAll() {
        orderRepository.deleteAll();
        calendarRepository.deleteAll();

        // Delete entities without repositories using EntityManager
        // Use try-catch in case tables don't exist yet
        try {
            entityManager.createQuery("DELETE FROM PageView").executeUpdate();
        } catch (Exception e) {
            // Ignore if table doesn't exist
        }
        try {
            entityManager.createQuery("DELETE FROM AnalyticsRollup").executeUpdate();
        } catch (Exception e) {
            // Ignore if table doesn't exist
        }
        try {
            entityManager.createQuery("DELETE FROM DelayedJob").executeUpdate();
        } catch (Exception e) {
            // Ignore if table doesn't exist
        }

        userRepository.deleteAll();
        templateRepository.deleteAll();
    }
}
