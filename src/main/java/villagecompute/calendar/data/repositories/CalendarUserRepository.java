package villagecompute.calendar.data.repositories;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import villagecompute.calendar.data.models.CalendarUser;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for CalendarUser entities.
 * Provides custom query methods for OAuth user management.
 */
@ApplicationScoped
public class CalendarUserRepository implements PanacheRepository<CalendarUser> {

    /**
     * Find a user by their OAuth provider and subject (unique identifier).
     * This is the required custom query method from the task specification.
     *
     * @param provider OAuth provider (e.g., "GOOGLE", "FACEBOOK")
     * @param subject OAuth subject (sub claim from JWT)
     * @return Optional containing the user if found
     */
    public Optional<CalendarUser> findByOAuthSubject(String provider, String subject) {
        return find("oauthProvider = ?1 AND oauthSubject = ?2", provider, subject)
                .firstResultOptional();
    }

    /**
     * Find a user by email address.
     *
     * @param email Email address
     * @return Optional containing the user if found
     */
    public Optional<CalendarUser> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }

    /**
     * Find all users who have logged in since a specific time.
     *
     * @param since Instant to compare against
     * @return List of active users
     */
    public List<CalendarUser> findActiveUsersSince(Instant since) {
        return find("lastLoginAt >= ?1 ORDER BY lastLoginAt DESC", since).list();
    }

    /**
     * Find users by OAuth provider.
     *
     * @param provider OAuth provider
     * @return List of users from the provider
     */
    public List<CalendarUser> findByProvider(String provider) {
        return find("oauthProvider = ?1 ORDER BY created DESC", provider).list();
    }
}
