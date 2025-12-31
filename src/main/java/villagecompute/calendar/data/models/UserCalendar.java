package villagecompute.calendar.data.models;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.eclipse.microprofile.graphql.Ignore;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.hibernate.orm.panache.PanacheQuery;

@Entity
@Table(name = "user_calendars", indexes = {
        @Index(name = "idx_user_calendars_user", columnList = "user_id, `year` DESC"),
        @Index(name = "idx_user_calendars_session", columnList = "session_id, updated DESC"),
        @Index(name = "idx_user_calendars_template", columnList = "template_id"),
        @Index(name = "idx_user_calendars_public", columnList = "is_public, updated DESC")})
public class UserCalendar extends DefaultPanacheEntityWithTimestamps {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_user_calendars_user"))
    @Ignore
    public CalendarUser user;

    @Size(max = 255)
    @Column(name = "session_id", length = 255)
    public String sessionId;

    @Column(name = "is_public", nullable = false)
    public boolean isPublic = true;

    @NotNull @Size(max = 255)
    @Column(nullable = false, length = 255)
    public String name;

    @NotNull @Column(name = "`year`", nullable = false)
    public Integer year;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = true)
    @io.smallrye.graphql.api.AdaptWith(villagecompute.calendar.api.graphql.scalars.JsonNodeAdapter.class)
    public JsonNode configuration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", foreignKey = @ForeignKey(name = "fk_user_calendars_template"))
    @Ignore
    public CalendarTemplate template;

    @Column(name = "generated_svg", columnDefinition = "TEXT")
    public String generatedSvg;

    @Size(max = 500)
    @Column(name = "generated_pdf_url", length = 500)
    public String generatedPdfUrl;

    // Relationships
    @OneToMany(mappedBy = "calendar", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<CalendarOrder> orders;

    @OneToMany(mappedBy = "calendar", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<Event> events;

    // Helper methods (ActiveRecord pattern)

    /**
     * Find calendars by session ID (for anonymous users).
     *
     * @param sessionId
     *            Session identifier
     * @return Query of calendars ordered by update time
     */
    public static PanacheQuery<UserCalendar> findBySession(String sessionId) {
        return find("sessionId = ?1 ORDER BY updated DESC", sessionId);
    }

    /**
     * Find calendars by authenticated user and year. This is the required custom query method from the task
     * specification.
     *
     * @param userId
     *            User ID
     * @param year
     *            Calendar year
     * @return List of calendars for the user and year
     */
    public static List<UserCalendar> findByUserAndYear(UUID userId, Integer year) {
        return find("user.id = ?1 AND year = ?2 ORDER BY updated DESC", userId, year).list();
    }

    /**
     * Find all calendars for a specific user.
     *
     * @param userId
     *            User ID
     * @return Query of user's calendars
     */
    public static PanacheQuery<UserCalendar> findByUser(UUID userId) {
        return find("user.id = ?1 ORDER BY year DESC, updated DESC", userId);
    }

    /**
     * Find calendars by session ID and name (for anonymous users).
     *
     * @param sessionId
     *            Session identifier
     * @param name
     *            Calendar name
     * @return Query of matching calendars
     */
    public static PanacheQuery<UserCalendar> findBySessionAndName(String sessionId, String name) {
        return find("sessionId = ?1 AND name = ?2", sessionId, name);
    }

    /**
     * Find a public calendar by ID.
     *
     * @param id
     *            Calendar ID
     * @return Calendar if found and public, null otherwise
     */
    public static UserCalendar findPublicById(UUID id) {
        return find("id = ?1 AND isPublic = true", id).firstResult();
    }

    /**
     * Find all public calendars.
     *
     * @return Query of public calendars
     */
    public static PanacheQuery<UserCalendar> findPublicCalendars() {
        return find("isPublic = true ORDER BY updated DESC");
    }

    /**
     * Create a copy of this calendar for a new session (for sharing/forking).
     *
     * @param newSessionId
     *            New session ID
     * @return Copy of the calendar
     */
    public UserCalendar copyForSession(String newSessionId) {
        UserCalendar copy = new UserCalendar();
        copy.sessionId = newSessionId;
        copy.name = this.name;
        copy.year = this.year;
        copy.configuration = this.configuration;
        copy.template = this.template;
        copy.generatedSvg = this.generatedSvg;
        copy.isPublic = true;
        return copy;
    }
}
