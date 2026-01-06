package villagecompute.calendar.data.repositories;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import villagecompute.calendar.data.models.CalendarUser;

import io.quarkus.hibernate.orm.panache.PanacheRepository;

/**
 * Repository for CalendarUser entities. Provides custom query methods for OAuth user management.
 */
@ApplicationScoped
public class CalendarUserRepository implements PanacheRepository<CalendarUser> {

    /**
     * Find a user by email address.
     *
     * @param email
     *            Email address
     * @return Optional containing the user if found
     */
    public Optional<CalendarUser> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }
}
