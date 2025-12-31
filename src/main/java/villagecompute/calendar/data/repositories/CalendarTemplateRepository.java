package villagecompute.calendar.data.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import villagecompute.calendar.data.models.CalendarTemplate;

import io.quarkus.hibernate.orm.panache.PanacheRepository;

/**
 * Repository for CalendarTemplate entities. Provides custom query methods for template management.
 */
@ApplicationScoped
public class CalendarTemplateRepository implements PanacheRepository<CalendarTemplate> {

    @Inject
    EntityManager entityManager;

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

    /**
     * Count the number of user calendars that use a specific template. Used to prevent deletion of templates that are
     * in use.
     *
     * @param templateId
     *            Template ID
     * @return Number of calendars using this template
     */
    public long countCalendarsUsingTemplate(UUID templateId) {
        return entityManager
                .createQuery("SELECT COUNT(c) FROM UserCalendar c WHERE c.template.id = :templateId", Long.class)
                .setParameter("templateId", templateId).getSingleResult();
    }

    /**
     * Batch load templates by their IDs. Used by DataLoader to prevent N+1 queries.
     *
     * @param ids
     *            List of template IDs
     * @return List of templates matching the IDs
     */
    public List<CalendarTemplate> findByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return find("id IN ?1", ids).list();
    }
}
