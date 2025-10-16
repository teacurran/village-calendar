package villagecompute.calendar.data.models;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(
    name = "calendar_templates",
    indexes = {
        @Index(name = "idx_calendar_templates_name", columnList = "name"),
        @Index(name = "idx_calendar_templates_active", columnList = "is_active, display_order, name"),
        @Index(name = "idx_calendar_templates_featured", columnList = "is_featured, is_active, display_order")
    }
)
public class CalendarTemplate extends DefaultPanacheEntityWithTimestamps {

    @NotNull
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    public String name;

    @Column(columnDefinition = "TEXT")
    public String description;

    @Size(max = 500)
    @Column(name = "thumbnail_url", length = 500)
    public String thumbnailUrl;

    @Column(name = "is_active", nullable = false)
    public boolean isActive = true;

    @Column(name = "is_featured", nullable = false)
    public boolean isFeatured = false;

    @Column(name = "display_order", nullable = false)
    public Integer displayOrder = 0;

    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @io.smallrye.graphql.api.AdaptWith(villagecompute.calendar.api.graphql.scalars.JsonNodeAdapter.class)
    public JsonNode configuration;

    @Column(name = "preview_svg", columnDefinition = "TEXT")
    public String previewSvg;

    // Relationships
    @OneToMany(mappedBy = "template")
    public List<UserCalendar> userCalendars;

    // Helper methods (ActiveRecord pattern)

    /**
     * Find a template by name.
     *
     * @param name Template name
     * @return Template if found, null otherwise
     */
    public static CalendarTemplate findByName(String name) {
        return find("name", name).firstResult();
    }

    /**
     * Find all active templates ordered by display order.
     * This is the required custom query method from the task specification.
     *
     * @return List of active templates
     */
    public static List<CalendarTemplate> findActiveTemplates() {
        return find("isActive = ?1 ORDER BY displayOrder, name", true).list();
    }

    /**
     * Find all active templates (returns query for pagination).
     *
     * @return Query of active templates
     */
    public static PanacheQuery<CalendarTemplate> findActive() {
        return find("isActive = ?1 ORDER BY displayOrder, name", true);
    }

    /**
     * Find all featured templates.
     *
     * @return Query of featured templates
     */
    public static PanacheQuery<CalendarTemplate> findFeatured() {
        return find("isActive = ?1 AND isFeatured = ?2 ORDER BY displayOrder, name", true, true);
    }
}
