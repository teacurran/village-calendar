package villagecompute.calendar.data.models;

import java.util.List;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.hibernate.orm.panache.PanacheQuery;

@Entity
@Table(
        name = "calendar_templates",
        indexes = {
            @Index(name = "idx_calendar_templates_name", columnList = "name"),
            @Index(
                    name = "idx_calendar_templates_active",
                    columnList = "is_active, display_order, name"),
            @Index(
                    name = "idx_calendar_templates_featured",
                    columnList = "is_featured, is_active, display_order")
        })
public class CalendarTemplate extends DefaultPanacheEntityWithTimestamps {

    @NotNull @Size(max = 255)
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

    @NotNull @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @org.eclipse.microprofile.graphql.Ignore
    public JsonNode configuration;

    /** Get configuration as JSON string for GraphQL. */
    @org.eclipse.microprofile.graphql.Name("configuration")
    public String getConfigurationJson() {
        if (configuration == null) {
            return null;
        }
        return configuration.toString();
    }

    @Column(name = "preview_svg", columnDefinition = "TEXT")
    public String previewSvg;

    // SEO fields for static product page generation
    @Size(max = 100)
    @Column(length = 100, unique = true)
    public String slug;

    @Size(max = 160)
    @Column(name = "og_description", length = 160)
    public String ogDescription;

    @Column(name = "meta_keywords", columnDefinition = "TEXT")
    public String metaKeywords;

    @Column(name = "price_cents")
    public Integer priceCents = 2999;

    @Size(max = 500)
    @Column(name = "generated_thumbnail_url", length = 500)
    public String generatedThumbnailUrl;

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
     * Find all active templates ordered by display order. This is the required custom query method
     * from the task specification.
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

    /**
     * Find all active templates that have a slug set (for static page generation).
     *
     * @return List of templates with slugs for static page generation
     */
    public static List<CalendarTemplate> findActiveWithSlug() {
        return find("isActive = ?1 AND slug IS NOT NULL ORDER BY displayOrder, name", true).list();
    }

    /**
     * Find a template by its slug.
     *
     * @param slug URL-friendly slug
     * @return Template if found, null otherwise
     */
    public static CalendarTemplate findBySlug(String slug) {
        return find("slug = ?1 AND isActive = ?2", slug, true).firstResult();
    }
}
