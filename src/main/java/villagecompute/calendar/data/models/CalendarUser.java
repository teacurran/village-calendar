package villagecompute.calendar.data.models;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Entity representing an OAuth authenticated user in the calendar service.
 * Users can create and manage calendars, and place orders for printed calendars.
 */
@Entity
@Table(
    name = "calendar_users",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_calendar_users_oauth", columnNames = {"oauth_provider", "oauth_subject"})
    },
    indexes = {
        @Index(name = "idx_calendar_users_email", columnList = "email"),
        @Index(name = "idx_calendar_users_last_login", columnList = "last_login_at DESC")
    }
)
public class CalendarUser extends DefaultPanacheEntityWithTimestamps {

    @NotNull
    @Size(max = 50)
    @Column(name = "oauth_provider", nullable = false, length = 50)
    public String oauthProvider;

    @NotNull
    @Size(max = 255)
    @Column(name = "oauth_subject", nullable = false, length = 255)
    public String oauthSubject;

    @NotNull
    @Email
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    public String email;

    @Size(max = 255)
    @Column(name = "display_name", length = 255)
    public String displayName;

    @Size(max = 500)
    @Column(name = "profile_image_url", length = 500)
    public String profileImageUrl;

    @Column(name = "last_login_at")
    public Instant lastLoginAt;

    @NotNull
    @Column(name = "is_admin", nullable = false)
    public Boolean isAdmin = false;

    // Relationships
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 10)
    public List<UserCalendar> calendars;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 10)
    public List<CalendarOrder> orders;

    // Static finder methods (ActiveRecord pattern)

    /**
     * Find a user by their OAuth provider and subject (unique identifier).
     *
     * @param provider OAuth provider (e.g., "GOOGLE", "FACEBOOK")
     * @param subject OAuth subject (sub claim from JWT)
     * @return Optional containing the user if found
     */
    public static Optional<CalendarUser> findByOAuthSubject(String provider, String subject) {
        return find("oauthProvider = ?1 AND oauthSubject = ?2", provider, subject).firstResultOptional();
    }

    /**
     * Find a user by email address.
     *
     * @param email Email address
     * @return Optional containing the user if found
     */
    public static Optional<CalendarUser> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }

    /**
     * Find all users who have logged in since a specific time.
     *
     * @param since Instant to compare against
     * @return Query of active users
     */
    public static PanacheQuery<CalendarUser> findActiveUsersSince(Instant since) {
        return find("lastLoginAt >= ?1 ORDER BY lastLoginAt DESC", since);
    }

    /**
     * Update the last login timestamp for this user.
     */
    public void updateLastLogin() {
        this.lastLoginAt = Instant.now();
        persist();
    }

    /**
     * Check if any admin users exist in the system.
     *
     * @return true if at least one admin user exists
     */
    public static boolean hasAdminUsers() {
        return count("isAdmin = true") > 0;
    }

    /**
     * Find all admin users.
     *
     * @return Query of admin users
     */
    public static PanacheQuery<CalendarUser> findAdminUsers() {
        return find("isAdmin = true ORDER BY created ASC");
    }
}
