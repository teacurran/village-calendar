package villagecompute.calendar.data.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import villagecompute.calendar.data.repositories.TestDataCleaner;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class CalendarTemplateTest {

    @Inject
    Validator validator;

    @Inject
    TestDataCleaner testDataCleaner;

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
    void testValidEntity_Success() {
        // Given
        CalendarTemplate template = createValidTemplate("Test Template");

        // When
        template.persist();
        entityManager.flush(); // Flush to generate ID and timestamps

        // Then
        assertNotNull(template.id);
        assertNotNull(template.created);
        assertNotNull(template.updated);
        assertEquals(0L, template.version);
    }

    @Test
    void testInvalidEntity_NullName() {
        // Given
        CalendarTemplate template = createValidTemplate("Test Template");
        template.name = null;

        // When
        Set<ConstraintViolation<CalendarTemplate>> violations = validator.validate(template);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<CalendarTemplate> violation = violations.iterator().next();
        assertEquals("name", violation.getPropertyPath().toString());
    }

    @Test
    void testInvalidEntity_NullConfiguration() {
        // Given
        CalendarTemplate template = createValidTemplate("Test Template");
        template.configuration = null;

        // When
        Set<ConstraintViolation<CalendarTemplate>> violations = validator.validate(template);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<CalendarTemplate> violation = violations.iterator().next();
        assertEquals("configuration", violation.getPropertyPath().toString());
    }

    @Test
    void testInvalidEntity_NameTooLong() {
        // Given
        CalendarTemplate template = createValidTemplate("Test Template");
        template.name = "A".repeat(256); // Max is 255

        // When
        Set<ConstraintViolation<CalendarTemplate>> violations = validator.validate(template);

        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<CalendarTemplate> violation = violations.iterator().next();
        assertEquals("name", violation.getPropertyPath().toString());
    }

    @Test
    @Transactional
    void testJsonbSerialization_Configuration() {
        // Given
        CalendarTemplate template = createValidTemplate("Test Template");
        ObjectNode config = objectMapper.createObjectNode();
        config.put("theme", "modern");
        config.put("primaryColor", "#3498db");
        config.put("showMoonPhases", true);
        template.configuration = config;
        template.persist();

        // When
        CalendarTemplate found = CalendarTemplate.findById(template.id);

        // Then
        assertNotNull(found.configuration);
        assertEquals("modern", found.configuration.get("theme").asText());
        assertEquals("#3498db", found.configuration.get("primaryColor").asText());
        assertTrue(found.configuration.get("showMoonPhases").asBoolean());
    }

    @Test
    @Transactional
    void testFindByName() {
        // Given
        CalendarTemplate template = createValidTemplate("Unique Template");
        template.persist();

        // When
        CalendarTemplate found = CalendarTemplate.findByName("Unique Template");

        // Then
        assertNotNull(found);
        assertEquals(template.id, found.id);
    }

    @Test
    @Transactional
    void testFindByName_NotFound() {
        // Given
        CalendarTemplate template = createValidTemplate("Template 1");
        template.persist();

        // When
        CalendarTemplate found = CalendarTemplate.findByName("Template 2");

        // Then
        assertNull(found);
    }

    @Test
    @Transactional
    void testFindActiveTemplates() {
        // Given
        CalendarTemplate active1 = createValidTemplate("Active 1");
        active1.isActive = true;
        active1.displayOrder = 2;
        active1.persist();

        CalendarTemplate active2 = createValidTemplate("Active 2");
        active2.isActive = true;
        active2.displayOrder = 1;
        active2.persist();

        CalendarTemplate inactive = createValidTemplate("Inactive");
        inactive.isActive = false;
        inactive.persist();

        // When
        List<CalendarTemplate> activeTemplates = CalendarTemplate.findActiveTemplates();

        // Then
        assertEquals(2, activeTemplates.size());
        // Should be ordered by displayOrder
        assertEquals("Active 2", activeTemplates.get(0).name);
        assertEquals("Active 1", activeTemplates.get(1).name);
    }

    @Test
    @Transactional
    void testFindActive() {
        // Given
        CalendarTemplate active1 = createValidTemplate("Active 1");
        active1.isActive = true;
        active1.persist();

        CalendarTemplate active2 = createValidTemplate("Active 2");
        active2.isActive = true;
        active2.persist();

        CalendarTemplate inactive = createValidTemplate("Inactive");
        inactive.isActive = false;
        inactive.persist();

        // When
        List<CalendarTemplate> activeTemplates = CalendarTemplate.findActive().list();

        // Then
        assertEquals(2, activeTemplates.size());
        assertTrue(activeTemplates.stream().allMatch(t -> t.isActive));
    }

    @Test
    @Transactional
    void testFindFeatured() {
        // Given
        CalendarTemplate featured1 = createValidTemplate("Featured 1");
        featured1.isActive = true;
        featured1.isFeatured = true;
        featured1.displayOrder = 1;
        featured1.persist();

        CalendarTemplate featured2 = createValidTemplate("Featured 2");
        featured2.isActive = true;
        featured2.isFeatured = true;
        featured2.displayOrder = 2;
        featured2.persist();

        CalendarTemplate notFeatured = createValidTemplate("Not Featured");
        notFeatured.isActive = true;
        notFeatured.isFeatured = false;
        notFeatured.persist();

        CalendarTemplate inactiveFeatured = createValidTemplate("Inactive Featured");
        inactiveFeatured.isActive = false;
        inactiveFeatured.isFeatured = true;
        inactiveFeatured.persist();

        // When
        List<CalendarTemplate> featured = CalendarTemplate.findFeatured().list();

        // Then
        assertEquals(2, featured.size());
        assertTrue(featured.stream().allMatch(t -> t.isActive && t.isFeatured));
        // Should be ordered by displayOrder
        assertEquals("Featured 1", featured.get(0).name);
        assertEquals("Featured 2", featured.get(1).name);
    }

    @Test
    @Transactional
    void testDefaultValues() {
        // Given
        CalendarTemplate template = new CalendarTemplate();
        template.name = "Test";
        ObjectNode config = objectMapper.createObjectNode();
        config.put("test", "value");
        template.configuration = config;

        // When
        template.persist();

        // Then
        assertTrue(template.isActive); // Default true
        assertFalse(template.isFeatured); // Default false
        assertEquals(0, template.displayOrder); // Default 0
    }

    @Test
    @Transactional
    void testRelationships_OneToManyUserCalendars() {
        // Given
        CalendarTemplate template = createValidTemplate("Test Template");
        template.persist();

        CalendarUser user = new CalendarUser();
        user.oauthProvider = "GOOGLE";
        user.oauthSubject = "test-subject";
        user.email = "test@example.com";
        user.displayName = "Test User";
        user.isAdmin = false;
        user.persist();

        UserCalendar calendar1 = new UserCalendar();
        calendar1.user = user;
        calendar1.name = "Calendar 1";
        calendar1.year = 2025;
        calendar1.template = template;
        calendar1.persist();

        UserCalendar calendar2 = new UserCalendar();
        calendar2.user = user;
        calendar2.name = "Calendar 2";
        calendar2.year = 2025;
        calendar2.template = template;
        calendar2.persist();

        entityManager.flush(); // Flush to ensure all entities are persisted
        entityManager.clear(); // Clear to force reload

        // When
        CalendarTemplate found = CalendarTemplate.findById(template.id);
        // Access the collection size to trigger lazy loading
        int size = found.userCalendars.size();

        // Then
        assertNotNull(found.userCalendars);
        assertEquals(2, size);
    }

    private CalendarTemplate createValidTemplate(String name) {
        CalendarTemplate template = new CalendarTemplate();
        template.name = name;
        template.isActive = true;
        template.displayOrder = 0;
        ObjectNode config = objectMapper.createObjectNode();
        config.put("theme", "default");
        template.configuration = config;
        return template;
    }
}
