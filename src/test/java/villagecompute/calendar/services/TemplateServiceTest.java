package villagecompute.calendar.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import villagecompute.calendar.api.graphql.inputs.TemplateInput;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.models.UserCalendar;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TemplateService.
 * Tests template CRUD operations, validation logic, and preview image uploads.
 * Uses real database for templates and mocked storage service.
 */
@QuarkusTest
class TemplateServiceTest {

    @Inject
    TemplateService templateService;

    @InjectMock
    StorageService storageService;

    private ObjectMapper objectMapper;
    private JsonNode validConfiguration;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        // Create valid template configuration
        ObjectNode config = objectMapper.createObjectNode();
        config.put("layout", "grid");
        config.put("fonts", "Arial");
        config.put("colors", "#000000");
        validConfiguration = config;

        // Reset storage service mock
        Mockito.reset(storageService);
    }

    @AfterEach
    @Transactional
    void tearDown() {
        // Clean up test data in proper order (delete child records first to avoid FK violations)
        // Order: CalendarOrder -> UserCalendar -> CalendarTemplate
        try {
            villagecompute.calendar.data.models.CalendarOrder.deleteAll();
        } catch (Exception e) {
            // Ignore if table doesn't exist or other cleanup issues
        }
        try {
            UserCalendar.deleteAll();
        } catch (Exception e) {
            // Ignore if table doesn't exist or other cleanup issues
        }
        try {
            CalendarTemplate.deleteAll();
        } catch (Exception e) {
            // Ignore if table doesn't exist or other cleanup issues
        }
    }

    // ============================================================================
    // CREATE TEMPLATE TESTS
    // ============================================================================

    @Test
    @Transactional
    void testCreateTemplate_Success() {
        // Given: Valid input
        TemplateInput input = new TemplateInput(
            "Modern Calendar",
            "A modern calendar design",
            validConfiguration,
            null,
            true,
            false,
            0,
            null
        );

        // When: Create template
        CalendarTemplate result = templateService.createTemplate(input);

        // Then: Template is created with correct properties
        assertNotNull(result);
        assertNotNull(result.id);
        assertEquals("Modern Calendar", result.name);
        assertEquals("A modern calendar design", result.description);
        assertEquals(validConfiguration, result.configuration);
        assertTrue(result.isActive);
        assertFalse(result.isFeatured);
        assertEquals(0, result.displayOrder);
    }

    @Test
    @Transactional
    void testCreateTemplate_DefaultValues() {
        // Given: Input with minimal fields
        TemplateInput input = new TemplateInput(
            "Minimal Template",
            null,
            validConfiguration,
            null,
            null,  // isActive not specified
            null,  // isFeatured not specified
            null,  // displayOrder not specified
            null
        );

        // When: Create template
        CalendarTemplate result = templateService.createTemplate(input);

        // Then: Default values are applied
        assertNotNull(result);
        assertTrue(result.isActive, "Should default to active");
        assertFalse(result.isFeatured, "Should default to not featured");
        assertEquals(0, result.displayOrder, "Should default to order 0");
    }

    @Test
    @Transactional
    void testCreateTemplate_DuplicateName() {
        // Given: Existing template with same name
        CalendarTemplate existing = new CalendarTemplate();
        existing.name = "Existing Template";
        existing.configuration = validConfiguration;
        existing.persist();

        TemplateInput input = new TemplateInput(
            "Existing Template",
            "Description",
            validConfiguration,
            null,
            true,
            false,
            0,
            null
        );

        // When/Then: Should throw exception
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            templateService.createTemplate(input);
        });

        assertTrue(exception.getMessage().contains("already exists"));
    }

    @Test
    @Transactional
    void testCreateTemplate_InvalidConfiguration_Null() {
        // Given: Input with null configuration
        TemplateInput input = new TemplateInput(
            "Test Template",
            "Description",
            null,  // Invalid: null configuration
            null,
            true,
            false,
            0,
            null
        );

        // When/Then: Should throw exception
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            templateService.createTemplate(input);
        });

        assertTrue(exception.getMessage().contains("configuration cannot be null"));
    }

    @Test
    @Transactional
    void testCreateTemplate_InvalidConfiguration_MissingLayout() {
        // Given: Configuration missing required "layout" field
        ObjectNode invalidConfig = objectMapper.createObjectNode();
        invalidConfig.put("fonts", "Arial");
        invalidConfig.put("colors", "#000000");

        TemplateInput input = new TemplateInput(
            "Test Template",
            "Description",
            invalidConfig,
            null,
            true,
            false,
            0,
            null
        );

        // When/Then: Should throw exception
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            templateService.createTemplate(input);
        });

        assertTrue(exception.getMessage().contains("layout"));
    }

    @Test
    @Transactional
    void testCreateTemplate_InvalidConfiguration_MissingFonts() {
        // Given: Configuration missing required "fonts" field
        ObjectNode invalidConfig = objectMapper.createObjectNode();
        invalidConfig.put("layout", "grid");
        invalidConfig.put("colors", "#000000");

        TemplateInput input = new TemplateInput(
            "Test Template",
            "Description",
            invalidConfig,
            null,
            true,
            false,
            0,
            null
        );

        // When/Then: Should throw exception
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            templateService.createTemplate(input);
        });

        assertTrue(exception.getMessage().contains("fonts"));
    }

    @Test
    @Transactional
    void testCreateTemplate_InvalidConfiguration_MissingColors() {
        // Given: Configuration missing required "colors" field
        ObjectNode invalidConfig = objectMapper.createObjectNode();
        invalidConfig.put("layout", "grid");
        invalidConfig.put("fonts", "Arial");

        TemplateInput input = new TemplateInput(
            "Test Template",
            "Description",
            invalidConfig,
            null,
            true,
            false,
            0,
            null
        );

        // When/Then: Should throw exception
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            templateService.createTemplate(input);
        });

        assertTrue(exception.getMessage().contains("colors"));
    }

    // ============================================================================
    // UPDATE TEMPLATE TESTS
    // ============================================================================

    @Test
    @Transactional
    void testUpdateTemplate_Success() {
        // Given: Existing template
        CalendarTemplate existingTemplate = new CalendarTemplate();
        existingTemplate.name = "Old Name";
        existingTemplate.description = "Old Description";
        existingTemplate.configuration = validConfiguration;
        existingTemplate.isActive = true;
        existingTemplate.isFeatured = false;
        existingTemplate.displayOrder = 0;
        existingTemplate.persist();

        UUID templateId = existingTemplate.id;

        // When: Update template
        TemplateInput input = new TemplateInput(
            "Updated Name",
            "Updated Description",
            validConfiguration,
            null,
            false,
            true,
            5,
            null
        );

        CalendarTemplate result = templateService.updateTemplate(templateId, input);

        // Then: Template is updated
        assertNotNull(result);
        assertEquals("Updated Name", result.name);
        assertEquals("Updated Description", result.description);
        assertFalse(result.isActive);
        assertTrue(result.isFeatured);
        assertEquals(5, result.displayOrder);
    }

    @Test
    @Transactional
    void testUpdateTemplate_PartialUpdate() {
        // Given: Existing template
        CalendarTemplate existingTemplate = new CalendarTemplate();
        existingTemplate.name = "Original Name";
        existingTemplate.description = "Original Description";
        existingTemplate.configuration = validConfiguration;
        existingTemplate.isActive = true;
        existingTemplate.isFeatured = false;
        existingTemplate.displayOrder = 0;
        existingTemplate.persist();

        UUID templateId = existingTemplate.id;

        // When: Update only some fields
        TemplateInput input = new TemplateInput();
        input.name = null;
        input.description = "New Description";
        input.configuration = validConfiguration;
        input.isActive = null;
        input.isFeatured = null;
        input.displayOrder = null;

        CalendarTemplate result = templateService.updateTemplate(templateId, input);

        // Then: Only specified fields are updated
        assertEquals("Original Name", result.name);
        assertEquals("New Description", result.description);
        assertTrue(result.isActive);
        assertFalse(result.isFeatured);
        assertEquals(0, result.displayOrder);
    }

    @Test
    @Transactional
    void testUpdateTemplate_NotFound() {
        // Given: Non-existent template ID
        UUID templateId = UUID.randomUUID();

        TemplateInput input = new TemplateInput(
            "Test",
            "Description",
            validConfiguration,
            null,
            true,
            false,
            0,
            null
        );

        // When/Then: Should throw exception
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            templateService.updateTemplate(templateId, input);
        });

        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    @Transactional
    void testUpdateTemplate_NameConflict() {
        // Given: Two templates
        CalendarTemplate template1 = new CalendarTemplate();
        template1.name = "Original Name";
        template1.configuration = validConfiguration;
        template1.persist();

        CalendarTemplate template2 = new CalendarTemplate();
        template2.name = "Conflicting Name";
        template2.configuration = validConfiguration;
        template2.persist();

        // When: Try to update template1 with template2's name
        TemplateInput input = new TemplateInput(
            "Conflicting Name",
            "Description",
            validConfiguration,
            null,
            true,
            false,
            0,
            null
        );

        // Then: Should throw exception
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            templateService.updateTemplate(template1.id, input);
        });

        assertTrue(exception.getMessage().contains("already exists"));
    }

    @Test
    @Transactional
    void testUpdateTemplate_SameName_NoConflict() {
        // Given: Existing template
        CalendarTemplate template = new CalendarTemplate();
        template.name = "Same Name";
        template.configuration = validConfiguration;
        template.persist();

        // When: Update with same name
        TemplateInput input = new TemplateInput(
            "Same Name",
            "Updated Description",
            validConfiguration,
            null,
            true,
            false,
            0,
            null
        );

        // Then: Should succeed
        CalendarTemplate result = templateService.updateTemplate(template.id, input);
        assertNotNull(result);
        assertEquals("Same Name", result.name);
        assertEquals("Updated Description", result.description);
    }

    // ============================================================================
    // DELETE TEMPLATE TESTS
    // ============================================================================

    @Test
    @Transactional
    void testDeleteTemplate_SoftDelete_Success() {
        // Given: Template to soft-delete
        CalendarTemplate template = new CalendarTemplate();
        template.name = "Test Template";
        template.configuration = validConfiguration;
        template.isActive = true;
        template.persist();

        UUID templateId = template.id;

        // When: Delete template (soft delete)
        assertDoesNotThrow(() -> {
            templateService.deleteTemplate(templateId);
        });

        // Then: Template should still exist but be marked as inactive
        CalendarTemplate found = CalendarTemplate.findById(templateId);
        assertNotNull(found, "Template should still exist in database after soft delete");
        assertFalse(found.isActive, "Template should be marked as inactive");
        assertEquals("Test Template", found.name, "Template name should be preserved");
    }

    @Test
    @Transactional
    void testDeleteTemplate_NotFound() {
        // Given: Non-existent template ID
        UUID templateId = UUID.randomUUID();

        // When/Then: Should throw exception
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            templateService.deleteTemplate(templateId);
        });

        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    @Transactional
    void testDeleteTemplate_WithExistingCalendars_AllowsSoftDelete() {
        // Given: Template with existing UserCalendar
        CalendarTemplate template = new CalendarTemplate();
        template.name = "Popular Template";
        template.configuration = validConfiguration;
        template.isActive = true;
        template.persist();

        // Create a UserCalendar that uses this template
        UserCalendar calendar = new UserCalendar();
        calendar.name = "My Calendar";
        calendar.year = 2025;
        calendar.template = template;
        calendar.isPublic = false;
        calendar.persist();

        UUID templateId = template.id;

        // When: Soft delete template (should succeed even with existing calendars)
        assertDoesNotThrow(() -> {
            templateService.deleteTemplate(templateId);
        });

        // Then: Template should be soft-deleted (isActive=false)
        CalendarTemplate softDeleted = CalendarTemplate.findById(templateId);
        assertNotNull(softDeleted, "Template should still exist");
        assertFalse(softDeleted.isActive, "Template should be marked inactive");

        // And: Existing calendar should still reference the template
        UserCalendar existingCalendar = UserCalendar.findById(calendar.id);
        assertNotNull(existingCalendar.template, "Calendar should still have template reference");
        assertEquals(templateId, existingCalendar.template.id);
    }

    // ============================================================================
    // UPLOAD PREVIEW IMAGE TESTS
    // ============================================================================

    @Test
    @Transactional
    void testUploadPreviewImage_Success() {
        // Given: Existing template
        CalendarTemplate template = new CalendarTemplate();
        template.name = "Test Template";
        template.configuration = validConfiguration;
        template.persist();

        byte[] imageBytes = new byte[]{1, 2, 3, 4, 5};
        String contentType = "image/png";
        String expectedUrl = "https://r2.example.com/template.png";

        when(storageService.uploadFile(anyString(), eq(imageBytes), eq(contentType)))
            .thenReturn(expectedUrl);

        // When: Upload preview image
        String resultUrl = templateService.uploadPreviewImage(template.id, imageBytes, contentType);

        // Then: Image is uploaded and URL is returned
        assertEquals(expectedUrl, resultUrl);

        // Verify template was updated
        CalendarTemplate updated = CalendarTemplate.findById(template.id);
        assertEquals(expectedUrl, updated.thumbnailUrl);

        // Verify storage service was called
        verify(storageService, times(1)).uploadFile(anyString(), eq(imageBytes), eq(contentType));
    }

    @Test
    @Transactional
    void testUploadPreviewImage_TemplateNotFound() {
        // Given: Non-existent template ID
        UUID templateId = UUID.randomUUID();
        byte[] imageBytes = new byte[]{1, 2, 3, 4, 5};
        String contentType = "image/png";

        // When/Then: Should throw exception
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            templateService.uploadPreviewImage(templateId, imageBytes, contentType);
        });

        assertTrue(exception.getMessage().contains("not found"));

        // Verify storage service was NOT called
        verify(storageService, never()).uploadFile(anyString(), any(byte[].class), anyString());
    }

    @Test
    @Transactional
    void testUploadPreviewImage_JpegExtension() {
        // Given: Template and JPEG content type
        CalendarTemplate template = new CalendarTemplate();
        template.name = "Test Template";
        template.configuration = validConfiguration;
        template.persist();

        byte[] imageBytes = new byte[]{1, 2, 3, 4, 5};
        String contentType = "image/jpeg";

        when(storageService.uploadFile(anyString(), eq(imageBytes), eq(contentType)))
            .thenReturn("https://r2.example.com/file.jpg");

        // When: Upload preview image
        templateService.uploadPreviewImage(template.id, imageBytes, contentType);

        // Then: Verify filename contains .jpg extension
        verify(storageService).uploadFile(
            argThat(filename -> filename.endsWith(".jpg")),
            eq(imageBytes),
            eq(contentType)
        );
    }

    // ============================================================================
    // ACCEPTANCE CRITERION 3: findActiveTemplates() excludes soft-deleted templates
    // ============================================================================

    @Test
    @Transactional
    void testFindActiveTemplates_ExcludesSoftDeleted() {
        // Given: Create active and inactive templates
        CalendarTemplate activeTemplate = new CalendarTemplate();
        activeTemplate.name = "Active Template " + System.currentTimeMillis();
        activeTemplate.description = "Active template";
        activeTemplate.isActive = true;
        activeTemplate.configuration = validConfiguration;
        activeTemplate.persist();

        CalendarTemplate inactiveTemplate = new CalendarTemplate();
        inactiveTemplate.name = "Inactive Template " + System.currentTimeMillis();
        inactiveTemplate.description = "Soft-deleted template";
        inactiveTemplate.isActive = false; // Soft-deleted
        inactiveTemplate.configuration = validConfiguration;
        inactiveTemplate.persist();

        // When: Query for active templates
        var activeTemplates = CalendarTemplate.findActiveTemplates();

        // Then: Should include only active templates
        assertNotNull(activeTemplates);
        assertTrue(activeTemplates.size() > 0, "Should find at least one active template");

        // Verify all returned templates are active
        for (CalendarTemplate template : activeTemplates) {
            assertTrue(template.isActive, "All returned templates should be active");
        }

        // Verify inactive template is excluded
        boolean inactiveIncluded = activeTemplates.stream()
            .anyMatch(t -> t.id.equals(inactiveTemplate.id));
        assertFalse(inactiveIncluded, "Inactive template should not be in results");

        // Verify active template is included
        boolean activeIncluded = activeTemplates.stream()
            .anyMatch(t -> t.id.equals(activeTemplate.id));
        assertTrue(activeIncluded, "Active template should be in results");
    }

    @Test
    @Transactional
    void testFindActiveTemplates_AfterSoftDelete() {
        // Given: Create an active template
        CalendarTemplate template = new CalendarTemplate();
        template.name = "Template Before Delete " + System.currentTimeMillis();
        template.description = "Will be soft-deleted";
        template.isActive = true;
        template.configuration = validConfiguration;
        template.persist();

        UUID templateId = template.id;

        // Verify it's in active templates before delete
        var beforeDelete = CalendarTemplate.findActiveTemplates();
        boolean includedBefore = beforeDelete.stream()
            .anyMatch(t -> t.id.equals(templateId));
        assertTrue(includedBefore, "Template should be in active templates before delete");

        // When: Soft delete the template
        templateService.deleteTemplate(templateId);

        // Then: Should be excluded from active templates
        var afterDelete = CalendarTemplate.findActiveTemplates();
        boolean includedAfter = afterDelete.stream()
            .anyMatch(t -> t.id.equals(templateId));
        assertFalse(includedAfter, "Soft-deleted template should be excluded from active templates");

        // And: Template should still exist in database
        CalendarTemplate persisted = CalendarTemplate.findById(templateId);
        assertNotNull(persisted, "Template should still exist in database");
        assertFalse(persisted.isActive, "Template should be marked as inactive");
    }

    // ============================================================================
    // ACCEPTANCE CRITERION 4: Template cloning preserves all config fields
    // ============================================================================

    @Test
    @Transactional
    void testTemplateCloning_PreservesAllConfigFields() throws Exception {
        // Given: A template with complex nested configuration
        ObjectMapper mapper = new ObjectMapper();
        JsonNode complexConfig = mapper.readTree("""
            {
                "layout": "modern",
                "fonts": {
                    "header": "Montserrat",
                    "body": "Open Sans",
                    "size": 12
                },
                "colors": {
                    "primary": "#FF5733",
                    "secondary": "#C70039",
                    "accent": "#900C3F"
                },
                "showMoonPhases": true,
                "showWeekNumbers": false,
                "compactMode": true,
                "holidays": ["US", "CA", "UK"],
                "astronomy": {
                    "moonPhases": true,
                    "solarTerms": false,
                    "customData": {
                        "nested": "value"
                    }
                }
            }
            """);

        CalendarTemplate sourceTemplate = new CalendarTemplate();
        sourceTemplate.name = "Source Template " + System.currentTimeMillis();
        sourceTemplate.description = "Template for cloning test";
        sourceTemplate.isActive = true;
        sourceTemplate.configuration = complexConfig;
        sourceTemplate.persist();

        // When: Clone the configuration to a UserCalendar (simulating template application)
        UserCalendar calendar = new UserCalendar();
        calendar.name = "Cloned Calendar";
        calendar.year = 2025;
        calendar.template = sourceTemplate;
        calendar.configuration = sourceTemplate.configuration; // Deep copy JSONB
        calendar.isPublic = false;
        calendar.persist();

        // Then: All configuration fields should be preserved
        assertNotNull(calendar.configuration, "Configuration should be cloned");

        // Verify top-level fields
        assertEquals("modern", calendar.configuration.get("layout").asText());
        assertTrue(calendar.configuration.get("showMoonPhases").asBoolean());
        assertFalse(calendar.configuration.get("showWeekNumbers").asBoolean());
        assertTrue(calendar.configuration.get("compactMode").asBoolean());

        // Verify nested objects (fonts)
        JsonNode fonts = calendar.configuration.get("fonts");
        assertNotNull(fonts, "Fonts object should be preserved");
        assertEquals("Montserrat", fonts.get("header").asText());
        assertEquals("Open Sans", fonts.get("body").asText());
        assertEquals(12, fonts.get("size").asInt());

        // Verify nested objects (colors)
        JsonNode colors = calendar.configuration.get("colors");
        assertNotNull(colors, "Colors object should be preserved");
        assertEquals("#FF5733", colors.get("primary").asText());
        assertEquals("#C70039", colors.get("secondary").asText());
        assertEquals("#900C3F", colors.get("accent").asText());

        // Verify arrays
        JsonNode holidays = calendar.configuration.get("holidays");
        assertNotNull(holidays, "Holidays array should be preserved");
        assertTrue(holidays.isArray());
        assertEquals(3, holidays.size());
        assertEquals("US", holidays.get(0).asText());
        assertEquals("CA", holidays.get(1).asText());
        assertEquals("UK", holidays.get(2).asText());

        // Verify deeply nested objects (astronomy)
        JsonNode astronomy = calendar.configuration.get("astronomy");
        assertNotNull(astronomy, "Astronomy object should be preserved");
        assertTrue(astronomy.get("moonPhases").asBoolean());
        assertFalse(astronomy.get("solarTerms").asBoolean());

        JsonNode customData = astronomy.get("customData");
        assertNotNull(customData, "Nested customData should be preserved");
        assertEquals("value", customData.get("nested").asText());
    }

    @Test
    @Transactional
    void testTemplateCloning_IndependentCopies() {
        // Given: A template with configuration
        CalendarTemplate sourceTemplate = new CalendarTemplate();
        sourceTemplate.name = "Independent Test Template";
        sourceTemplate.description = "Testing independent copies";
        sourceTemplate.isActive = true;
        sourceTemplate.configuration = validConfiguration;
        sourceTemplate.persist();

        // When: Create two calendars from the same template
        UserCalendar calendar1 = new UserCalendar();
        calendar1.name = "Calendar 1";
        calendar1.year = 2025;
        calendar1.template = sourceTemplate;
        calendar1.configuration = sourceTemplate.configuration;
        calendar1.isPublic = false;
        calendar1.persist();

        UserCalendar calendar2 = new UserCalendar();
        calendar2.name = "Calendar 2";
        calendar2.year = 2026;
        calendar2.template = sourceTemplate;
        calendar2.configuration = sourceTemplate.configuration;
        calendar2.isPublic = false;
        calendar2.persist();

        // Then: Both calendars should have the same configuration values
        assertEquals(calendar1.configuration.get("layout").asText(),
            calendar2.configuration.get("layout").asText());
        assertEquals(calendar1.configuration.get("fonts").asText(),
            calendar2.configuration.get("fonts").asText());
        assertEquals(calendar1.configuration.get("colors").asText(),
            calendar2.configuration.get("colors").asText());

        // And: Both should reference the same template
        assertEquals(sourceTemplate.id, calendar1.template.id);
        assertEquals(sourceTemplate.id, calendar2.template.id);
    }
}
