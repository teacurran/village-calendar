package villagecompute.calendar.data.repositories;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.repositories.TestDataCleaner;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class CalendarTemplateRepositoryTest {

    @Inject
    TestDataCleaner testDataCleaner;

    @Inject
    CalendarTemplateRepository repository;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    jakarta.persistence.EntityManager entityManager;

    @BeforeEach
    @Transactional
    void setUp() {
        testDataCleaner.deleteAll();
    }

    @Test
    @Transactional
    void testFindActiveTemplates() {
        // Given
        repository.persist(createTemplate("Template 1", true, false, 1));
        repository.persist(createTemplate("Template 2", true, false, 2));
        repository.persist(createTemplate("Inactive Template", false, false, 3));
        entityManager.flush();

        // When
        List<CalendarTemplate> activeTemplates = repository.findActiveTemplates();

        // Then
        assertEquals(2, activeTemplates.size());
        assertEquals("Template 1", activeTemplates.get(0).name); // Ordered by displayOrder
        assertEquals("Template 2", activeTemplates.get(1).name);
    }

    @Test
    @Transactional
    void testFindByName() {
        // Given
        CalendarTemplate template = createTemplate("Unique Template", true, false, 1);
        repository.persist(template);
        entityManager.flush();

        // When
        Optional<CalendarTemplate> found = repository.findByName("Unique Template");

        // Then
        assertTrue(found.isPresent());
        assertEquals("Unique Template", found.get().name);
    }

    @Test
    @Transactional
    void testFindFeaturedTemplates() {
        // Given
        repository.persist(createTemplate("Featured 1", true, true, 1));
        repository.persist(createTemplate("Featured 2", true, true, 2));
        repository.persist(createTemplate("Not Featured", true, false, 3));
        repository.persist(createTemplate("Inactive Featured", false, true, 4));
        entityManager.flush();

        // When
        List<CalendarTemplate> featured = repository.findFeaturedTemplates();

        // Then
        assertEquals(2, featured.size());
        assertTrue(featured.stream().allMatch(t -> t.isFeatured && t.isActive));
    }

    @Test
    @Transactional
    void testFindByActiveStatus() {
        // Given
        repository.persist(createTemplate("Active 1", true, false, 1));
        repository.persist(createTemplate("Active 2", true, false, 2));
        repository.persist(createTemplate("Inactive", false, false, 3));
        entityManager.flush();

        // When
        List<CalendarTemplate> activeTemplates = repository.findByActiveStatus(true);
        List<CalendarTemplate> inactiveTemplates = repository.findByActiveStatus(false);

        // Then
        assertEquals(2, activeTemplates.size());
        assertEquals(1, inactiveTemplates.size());
    }

    @Test
    @Transactional
    void testTemplateOrdering() {
        // Given - create templates with specific display orders
        repository.persist(createTemplate("Third", true, false, 3));
        repository.persist(createTemplate("First", true, false, 1));
        repository.persist(createTemplate("Second", true, false, 2));
        entityManager.flush();

        // When
        List<CalendarTemplate> activeTemplates = repository.findActiveTemplates();

        // Then - should be ordered by displayOrder
        assertEquals("First", activeTemplates.get(0).name);
        assertEquals("Second", activeTemplates.get(1).name);
        assertEquals("Third", activeTemplates.get(2).name);
    }

    private CalendarTemplate createTemplate(String name, boolean isActive, boolean isFeatured, int displayOrder) {
        CalendarTemplate template = new CalendarTemplate();
        template.name = name;
        template.description = "Test description for " + name;
        template.isActive = isActive;
        template.isFeatured = isFeatured;
        template.displayOrder = displayOrder;

        // Create a simple JSONB configuration
        ObjectNode config = objectMapper.createObjectNode();
        config.put("theme", "default");
        config.put("showMoonPhases", true);
        template.configuration = config;

        return template;
    }
}
