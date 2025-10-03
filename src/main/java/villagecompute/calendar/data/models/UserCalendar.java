package villagecompute.calendar.data.models;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_calendars")
public class UserCalendar extends DefaultPanacheEntityWithTimestamps {

    @Column(name = "session_id")
    public String sessionId;

    @Column(name = "is_public", nullable = false)
    public boolean isPublic = true;

    @Column(nullable = false)
    public String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = true)
    public JsonNode configuration;

    @ManyToOne
    @JoinColumn(name = "template_id")
    public CalendarTemplate template;

    @Column(name = "generated_svg", columnDefinition = "TEXT")
    public String generatedSvg;

    // Helper methods
    public static PanacheQuery<UserCalendar> findBySession(String sessionId) {
        return find("sessionId = ?1 ORDER BY updated DESC", sessionId);
    }

    public static PanacheQuery<UserCalendar> findBySessionAndName(String sessionId, String name) {
        return find("sessionId = ?1 AND name = ?2", sessionId, name);
    }

    public static UserCalendar findPublicById(java.util.UUID id) {
        return find("id = ?1 AND isPublic = true", id).firstResult();
    }

    public UserCalendar copyForSession(String newSessionId) {
        UserCalendar copy = new UserCalendar();
        copy.sessionId = newSessionId;
        copy.name = this.name;
        copy.configuration = this.configuration;
        copy.template = this.template;
        copy.generatedSvg = this.generatedSvg;
        copy.isPublic = true;
        return copy;
    }
}
