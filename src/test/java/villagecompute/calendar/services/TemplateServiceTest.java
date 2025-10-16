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
        // Clean up test templates
        CalendarTemplate.deleteAll();
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
    void testDeleteTemplate_Success() {
        // Given: Template with no calendars
        CalendarTemplate template = new CalendarTemplate();
        template.name = "Test Template";
        template.configuration = validConfiguration;
        template.persist();

        UUID templateId = template.id;

        // When: Delete template
        assertDoesNotThrow(() -> {
            templateService.deleteTemplate(templateId);
        });

        // Then: Template is deleted
        CalendarTemplate found = CalendarTemplate.findById(templateId);
        assertNull(found);
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
    void testDeleteTemplate_WithExistingCalendars() {
        // Given: Template with existing UserCalendar
        CalendarTemplate template = new CalendarTemplate();
        template.name = "Popular Template";
        template.configuration = validConfiguration;
        template.persist();

        // Create a UserCalendar that uses this template
        UserCalendar calendar = new UserCalendar();
        calendar.name = "My Calendar";
        calendar.year = 2025;
        calendar.template = template;
        calendar.isPublic = false;
        calendar.persist();

        UUID templateId = template.id;

        // When/Then: Should throw IllegalStateException (cannot delete)
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            templateService.deleteTemplate(templateId);
        });

        assertTrue(exception.getMessage().contains("existing calendars"));
        assertTrue(exception.getMessage().contains("1 calendar"));

        // Verify template still exists
        CalendarTemplate stillExists = CalendarTemplate.findById(templateId);
        assertNotNull(stillExists);
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
}
