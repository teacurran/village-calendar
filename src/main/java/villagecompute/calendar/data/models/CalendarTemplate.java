package villagecompute.calendar.data.models;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "calendar_templates")
public class CalendarTemplate extends DefaultPanacheEntityWithTimestamps {

    @Column(nullable = false)
    public String name;

    @Column(columnDefinition = "TEXT")
    public String description;

    @Column(name = "thumbnail_url", length = 500)
    public String thumbnailUrl;

    @Column(name = "is_active")
    public boolean isActive = true;

    @Column(name = "is_featured")
    public boolean isFeatured = false;

    @Column(name = "display_order")
    public Integer displayOrder = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    public JsonNode configuration;

    @Column(name = "preview_svg", columnDefinition = "TEXT")
    public String previewSvg;

    // Helper methods
    public static CalendarTemplate findByName(String name) {
        return find("name", name).firstResult();
    }

    public static PanacheQuery<CalendarTemplate> findActive() {
        return find("isActive = ?1 ORDER BY displayOrder, name", true);
    }

    public static PanacheQuery<CalendarTemplate> findFeatured() {
        return find("isActive = ?1 AND isFeatured = ?2 ORDER BY displayOrder, name", true, true);
    }
}
