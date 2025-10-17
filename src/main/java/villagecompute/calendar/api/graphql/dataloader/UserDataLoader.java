package villagecompute.calendar.api.graphql.dataloader;

import io.smallrye.graphql.api.Context;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.eclipse.microprofile.graphql.Name;
import org.jboss.logging.Logger;
import villagecompute.calendar.data.models.CalendarUser;
import villagecompute.calendar.data.repositories.CalendarUserRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * DataLoader for batch loading CalendarUser entities.
 * Prevents N+1 queries when fetching users for multiple calendars.
 *
 * Example: Fetching 10 calendars that each have a user will result in 2 queries:
 * 1. Query to fetch 10 calendars
 * 2. Query to batch fetch all unique users (SELECT * FROM calendar_user WHERE id IN (...))
 *
 * Without DataLoader, it would be 11 queries (1 for calendars + 10 separate user queries).
 */
@ApplicationScoped
public class UserDataLoader {

    private static final Logger LOG = Logger.getLogger(UserDataLoader.class);

    @Inject
    CalendarUserRepository userRepository;

    /**
     * Create a DataLoader for batching user queries.
     * This method is called by the GraphQL context for each request.
     *
     * @return DataLoader for CalendarUser entities
     */
    public DataLoader<UUID, CalendarUser> createDataLoader() {
        return DataLoaderFactory.newDataLoader(this::batchLoadUsers);
    }

    /**
     * Batch load users by IDs.
     * Called by DataLoader when multiple user IDs are requested.
     *
     * @param userIds List of user IDs to load
     * @return CompletionStage with list of users in the same order as requested IDs
     */
    private CompletionStage<List<CalendarUser>> batchLoadUsers(List<UUID> userIds) {
        LOG.debugf("Batch loading %d users", userIds.size());

        return CompletableFuture.supplyAsync(() -> {
            // Fetch all users in a single query
            List<CalendarUser> users = userRepository.findByIds(userIds);

            // Create a map for quick lookup
            Map<UUID, CalendarUser> userMap = users.stream()
                .collect(Collectors.toMap(user -> user.id, user -> user));

            // Return users in the same order as requested IDs
            // DataLoader requires the result list to be in the same order as the input
            return userIds.stream()
                .map(userMap::get)
                .collect(Collectors.toList());
        });
    }
}
