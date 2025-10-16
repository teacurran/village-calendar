package villagecompute.calendar.services;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import villagecompute.calendar.api.graphql.inputs.TemplateInput;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.repositories.CalendarTemplateRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Service layer for calendar template management operations.
 * Handles template CRUD operations, validation, and preview image uploads.
 * All operations are admin-only.
 */
@ApplicationScoped
public class TemplateService {

    private static final Logger LOG = Logger.getLogger(TemplateService.class);

    @Inject
    CalendarTemplateRepository templateRepository;

    @Inject
    StorageService storageService;

    /**
     * Create a new calendar template.
     * Validates the template configuration JSONB before creation.
     *
     * @param input Template creation data
     * @return Created template
     * @throws IllegalArgumentException if input validation fails
     */
    @Transactional
    public CalendarTemplate createTemplate(TemplateInput input) {
        LOG.infof("Creating template: name=%s", input.name);

        // Validate configuration structure
        validateTemplateConfiguration(input.configuration);

        // Check for duplicate name
        Optional<CalendarTemplate> existing = templateRepository.findByName(input.name);
        if (existing.isPresent()) {
            LOG.errorf("Template with name '%s' already exists", input.name);
            throw new IllegalArgumentException("Template with this name already exists");
        }

        // Create new template entity
        CalendarTemplate template = new CalendarTemplate();
        template.name = input.name;
        template.description = input.description;
        template.configuration = input.configuration;
        template.thumbnailUrl = input.thumbnailUrl;
        template.isActive = input.isActive != null ? input.isActive : true;
        template.isFeatured = input.isFeatured != null ? input.isFeatured : false;
        template.displayOrder = input.displayOrder != null ? input.displayOrder : 0;
        template.previewSvg = input.previewSvg;

        // Persist
        template.persist();

        LOG.infof("Created template: ID=%s, name=%s", template.id, template.name);

        return template;
    }

    /**
     * Update an existing calendar template.
     * Validates configuration if provided.
     *
     * @param id Template ID
     * @param input Template update data
     * @return Updated template
     * @throws IllegalArgumentException if template not found or validation fails
     */
    @Transactional
    public CalendarTemplate updateTemplate(UUID id, TemplateInput input) {
        LOG.infof("Updating template: ID=%s", id);

        // Find existing template
        CalendarTemplate template = CalendarTemplate.<CalendarTemplate>findByIdOptional(id).orElse(null);
        if (template == null) {
            LOG.errorf("Template not found: %s", id);
            throw new IllegalArgumentException("Template not found");
        }

        // Validate configuration if provided
        if (input.configuration != null) {
            validateTemplateConfiguration(input.configuration);
        }

        // Check for name conflicts (if name is being changed)
        if (input.name != null && !input.name.equals(template.name)) {
            Optional<CalendarTemplate> existing = templateRepository.findByName(input.name);
            if (existing.isPresent() && !existing.get().id.equals(id)) {
                LOG.errorf("Template with name '%s' already exists", input.name);
                throw new IllegalArgumentException("Template with this name already exists");
            }
        }

        // Apply updates
        if (input.name != null) {
            template.name = input.name;
        }
        if (input.description != null) {
            template.description = input.description;
        }
        if (input.configuration != null) {
            template.configuration = input.configuration;
        }
        if (input.thumbnailUrl != null) {
            template.thumbnailUrl = input.thumbnailUrl;
        }
        if (input.isActive != null) {
            template.isActive = input.isActive;
        }
        if (input.isFeatured != null) {
            template.isFeatured = input.isFeatured;
        }
        if (input.displayOrder != null) {
            template.displayOrder = input.displayOrder;
        }
        if (input.previewSvg != null) {
            template.previewSvg = input.previewSvg;
        }

        // Persist changes
        template.persist();

        LOG.infof("Updated template: ID=%s, name=%s", template.id, template.name);

        return template;
    }

    /**
     * Delete a calendar template.
     * Cannot delete templates that are referenced by existing calendars.
     *
     * @param id Template ID
     * @throws IllegalArgumentException if template not found
     * @throws IllegalStateException if template has existing calendars
     */
    @Transactional
    public void deleteTemplate(UUID id) {
        LOG.infof("Deleting template: ID=%s", id);

        // Find existing template
        CalendarTemplate template = CalendarTemplate.<CalendarTemplate>findByIdOptional(id).orElse(null);
        if (template == null) {
            LOG.errorf("Template not found: %s", id);
            throw new IllegalArgumentException("Template not found");
        }

        // Check for existing calendars using this template (use query to avoid lazy loading issues)
        long calendarCount = templateRepository.countCalendarsUsingTemplate(id);
        if (calendarCount > 0) {
            LOG.errorf("Cannot delete template %s: has %d existing calendars",
                id, calendarCount);
            throw new IllegalStateException(
                "Cannot delete template with existing calendars. " +
                "This template is used by " + calendarCount + " calendar(s)."
            );
        }

        // Delete the template
        template.delete();

        LOG.infof("Deleted template: ID=%s, name=%s", id, template.name);
    }

    /**
     * Upload a preview image for a template to R2 storage.
     * Updates the template's thumbnailUrl field with the public URL.
     *
     * @param templateId Template ID
     * @param imageBytes Image file bytes
     * @param contentType MIME type (e.g., "image/png", "image/jpeg")
     * @return Public URL of the uploaded image
     * @throws IllegalArgumentException if template not found
     */
    @Transactional
    public String uploadPreviewImage(UUID templateId, byte[] imageBytes, String contentType) {
        LOG.infof("Uploading preview image for template: ID=%s", templateId);

        // Find existing template
        CalendarTemplate template = CalendarTemplate.<CalendarTemplate>findByIdOptional(templateId).orElse(null);
        if (template == null) {
            LOG.errorf("Template not found: %s", templateId);
            throw new IllegalArgumentException("Template not found");
        }

        // Generate unique filename
        String filename = "template-" + templateId + "-" + System.currentTimeMillis() + getFileExtension(contentType);

        // Upload to R2
        String publicUrl = storageService.uploadFile(filename, imageBytes, contentType);

        // Update template with new thumbnail URL
        template.thumbnailUrl = publicUrl;
        template.persist();

        LOG.infof("Uploaded preview image for template %s: %s", templateId, publicUrl);

        return publicUrl;
    }

    /**
     * Validate the structure of a template configuration JSONB.
     * Ensures required fields are present.
     *
     * @param configuration JsonNode representing the template configuration
     * @throws IllegalArgumentException if configuration is invalid
     */
    private void validateTemplateConfiguration(JsonNode configuration) {
        if (configuration == null || configuration.isNull()) {
            throw new IllegalArgumentException("Template configuration cannot be null");
        }

        // Check for required top-level fields
        String[] requiredFields = {"layout", "fonts", "colors"};
        for (String field : requiredFields) {
            if (!configuration.has(field)) {
                LOG.errorf("Template configuration missing required field: %s", field);
                throw new IllegalArgumentException(
                    "Template configuration must include required field: " + field
                );
            }
        }

        LOG.debug("Template configuration validation passed");
    }

    /**
     * Get file extension from content type.
     *
     * @param contentType MIME type
     * @return File extension with dot (e.g., ".png")
     */
    private String getFileExtension(String contentType) {
        if (contentType == null) {
            return "";
        }

        return switch (contentType.toLowerCase()) {
            case "image/png" -> ".png";
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/svg+xml" -> ".svg";
            default -> "";
        };
    }
}
