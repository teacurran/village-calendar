package villagecompute.calendar.api.graphql.dataloader;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.jboss.logging.Logger;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.repositories.CalendarTemplateRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * DataLoader for batch loading CalendarTemplate entities.
 * Prevents N+1 queries when fetching templates for multiple calendars.
 *
 * Example: Fetching 10 calendars that each have a template will result in 2 queries:
 * 1. Query to fetch 10 calendars
 * 2. Query to batch fetch all unique templates (SELECT * FROM calendar_template WHERE id IN (...))
 *
 * Without DataLoader, it would be 11 queries (1 for calendars + 10 separate template queries).
 */
@ApplicationScoped
public class TemplateDataLoader {

    private static final Logger LOG = Logger.getLogger(TemplateDataLoader.class);

    @Inject
    CalendarTemplateRepository templateRepository;

    /**
     * Create a DataLoader for batching template queries.
     * This method is called by the GraphQL context for each request.
     *
     * @return DataLoader for CalendarTemplate entities
     */
    public DataLoader<UUID, CalendarTemplate> createDataLoader() {
        return DataLoaderFactory.newDataLoader(this::batchLoadTemplates);
    }

    /**
     * Batch load templates by IDs.
     * Called by DataLoader when multiple template IDs are requested.
     *
     * @param templateIds List of template IDs to load
     * @return CompletionStage with list of templates in the same order as requested IDs
     */
    private CompletionStage<List<CalendarTemplate>> batchLoadTemplates(List<UUID> templateIds) {
        LOG.debugf("Batch loading %d templates", templateIds.size());

        return CompletableFuture.supplyAsync(() -> {
            // Fetch all templates in a single query
            List<CalendarTemplate> templates = templateRepository.findByIds(templateIds);

            // Create a map for quick lookup
            Map<UUID, CalendarTemplate> templateMap = templates.stream()
                .collect(Collectors.toMap(template -> template.id, template -> template));

            // Return templates in the same order as requested IDs
            // DataLoader requires the result list to be in the same order as the input
            return templateIds.stream()
                .map(templateMap::get)
                .collect(Collectors.toList());
        });
    }
}
