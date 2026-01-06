package villagecompute.calendar.data.repositories;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import villagecompute.calendar.data.models.CalendarTemplate;

import io.quarkus.hibernate.orm.panache.PanacheRepository;

/**
 * Repository for CalendarTemplate entities. Provides custom query methods for template management.
 */
@ApplicationScoped
public class CalendarTemplateRepository implements PanacheRepository<CalendarTemplate> {

    /**
     * Find all active templates ordered by display order. This is the required custom query method from the task
     * specification.
     *
     * @return List of active templates
     */
    public List<CalendarTemplate> findActiveTemplates() {
        return find("isActive = ?1 ORDER BY displayOrder, name", true).list();
    }

    /**
     * Find a template by name.
     *
     * @param name
     *            Template name
     * @return Optional containing the template if found
     */
    public Optional<CalendarTemplate> findByName(String name) {
        return find("name", name).firstResultOptional();
    }

    /**
     * Find all featured templates.
     *
     * @return List of featured templates
     */
    public List<CalendarTemplate> findFeaturedTemplates() {
        return find("isActive = ?1 AND isFeatured = ?2 ORDER BY displayOrder, name", true, true).list();
    }

    /**
     * Find templates by active status.
     *
     * @param isActive
     *            Active status
     * @return List of templates matching the status
     */
    public List<CalendarTemplate> findByActiveStatus(boolean isActive) {
        return find("isActive = ?1 ORDER BY displayOrder, name", isActive).list();
    }
}
