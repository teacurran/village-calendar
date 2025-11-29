package villagecompute.calendar.api.graphql;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.graphql.*;
import org.jboss.logging.Logger;
import villagecompute.calendar.api.graphql.inputs.TemplateInput;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.repositories.CalendarTemplateRepository;
import villagecompute.calendar.services.TemplateService;

import java.util.List;
import java.util.UUID;

/**
 * GraphQL resolver for calendar template queries and admin mutations.
 * Provides public access to browse available calendar templates
 * and admin-only mutations for template management.
 */
@GraphQLApi
@ApplicationScoped
public class TemplateGraphQL {

    private static final Logger LOG = Logger.getLogger(TemplateGraphQL.class);

    @Inject
    CalendarTemplateRepository templateRepository;

    @Inject
    TemplateService templateService;

    @Inject
    SecurityIdentity securityIdentity;

    /**
     * Get all calendar templates with optional filtering.
     * This is a public query - no authentication required.
     *
     * @param isActive Filter by active status (default: true shows only active templates)
     * @param isFeatured Filter by featured status (optional)
     * @return List of calendar templates
     */
    @Query("templates")
    @Description("Get all calendar templates.")
    public List<CalendarTemplate> templates(
        @DefaultValue("true")
        @Description("Filter by active status (default: true shows only active templates)")
        Boolean isActive,

        @Description("Filter by featured status (optional)")
        Boolean isFeatured
    ) {
        LOG.infof("Querying templates: isActive=%s, isFeatured=%s", isActive, isFeatured);

        // Handle different filter combinations
        if (isFeatured != null && isFeatured) {
            // Featured templates (must also be active)
            List<CalendarTemplate> templates = templateRepository.findFeaturedTemplates();
            LOG.infof("Returning %d featured templates", templates.size());
            return templates;
        } else if (isActive != null && isActive) {
            // Active templates only
            List<CalendarTemplate> templates = templateRepository.findActiveTemplates();
            LOG.infof("Returning %d active templates", templates.size());
            return templates;
        } else if (isActive != null && !isActive) {
            // Inactive templates only
            List<CalendarTemplate> templates = templateRepository.findByActiveStatus(false);
            LOG.infof("Returning %d inactive templates", templates.size());
            return templates;
        } else {
            // All templates (no filter)
            List<CalendarTemplate> templates = templateRepository.listAll();
            LOG.infof("Returning all %d templates", templates.size());
            return templates;
        }
    }

    /**
     * Get a single template by ID.
     * Returns null if template not found or inactive (unless user is admin).
     *
     * @param id Template ID
     * @return Calendar template or null if not found
     */
    @Query("template")
    @Description("Get a single template by ID. Returns null if template not found or inactive (unless user is admin).")
    public CalendarTemplate template(
        @Name("id")
        @Description("Template ID")
        @NotNull
        String id
    ) {
        LOG.infof("Querying template by ID: %s", id);

        try {
            UUID templateId = UUID.fromString(id);
            CalendarTemplate template = CalendarTemplate.<CalendarTemplate>findByIdOptional(templateId).orElse(null);

            if (template == null) {
                LOG.warnf("Template not found: %s", id);
                return null;
            }

            // For now, we only show active templates to non-admin users
            // TODO: Check if user is admin and allow viewing inactive templates
            if (!template.isActive) {
                LOG.warnf("Template %s is inactive, returning null", id);
                return null;
            }

            LOG.infof("Found template: %s (name=%s)", id, template.name);
            return template;

        } catch (IllegalArgumentException e) {
            LOG.errorf("Invalid UUID format for template ID: %s", id);
            throw new IllegalArgumentException("Invalid template ID format", e);
        }
    }

    // ============================================================================
    // ADMIN MUTATIONS
    // ============================================================================

    /**
     * Create a new calendar template (admin only).
     * Requires ADMIN role in JWT claims.
     *
     * @param input Template creation data
     * @return Created template
     */
    @Mutation("createTemplate")
    @Description("Create a new calendar template (admin only). Requires ADMIN role in JWT claims.")
    @RolesAllowed("ADMIN")
    @Transactional
    public CalendarTemplate createTemplate(
        @Name("input")
        @Description("Template creation data")
        @NotNull
        @Valid
        TemplateInput input
    ) {
        LOG.infof("Mutation: createTemplate(name=%s)", input.name);

        try {
            CalendarTemplate template = templateService.createTemplate(input);
            LOG.infof("Successfully created template: %s (ID: %s)", template.name, template.id);
            return template;

        } catch (IllegalArgumentException e) {
            LOG.errorf(e, "Failed to create template: %s", e.getMessage());
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "Unexpected error creating template");
            throw new IllegalStateException("Failed to create template: " + e.getMessage(), e);
        }
    }

    /**
     * Update an existing calendar template (admin only).
     * Requires ADMIN role in JWT claims.
     *
     * @param id Template ID
     * @param input Template update data
     * @return Updated template
     */
    /**
     * Debug query to check current user's security identity and roles.
     * Temporary - remove after debugging.
     */
    @Query("debugSecurityIdentity")
    @Description("Debug: Show current security identity and roles")
    public String debugSecurityIdentity() {
        if (securityIdentity == null) {
            return "SecurityIdentity is null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Anonymous: ").append(securityIdentity.isAnonymous()).append("\n");
        sb.append("Principal: ").append(securityIdentity.getPrincipal() != null ? securityIdentity.getPrincipal().getName() : "null").append("\n");
        sb.append("Roles: ").append(securityIdentity.getRoles()).append("\n");
        sb.append("Has ADMIN: ").append(securityIdentity.hasRole("ADMIN")).append("\n");
        sb.append("Has USER: ").append(securityIdentity.hasRole("USER")).append("\n");
        sb.append("Attributes: ").append(securityIdentity.getAttributes()).append("\n");
        LOG.info("Debug security identity: " + sb.toString());
        return sb.toString();
    }

    @Mutation("updateTemplate")
    @Description("Update an existing calendar template (admin only). Requires ADMIN role in JWT claims.")
    @RolesAllowed("ADMIN")
    @Transactional
    public CalendarTemplate updateTemplate(
        @Name("id")
        @Description("Template ID to update")
        @NotNull
        String id,

        @Name("input")
        @Description("Template update data")
        @NotNull
        @Valid
        TemplateInput input
    ) {
        LOG.infof("Mutation: updateTemplate(id=%s), user roles: %s", id, securityIdentity != null ? securityIdentity.getRoles() : "null");

        try {
            UUID templateId = UUID.fromString(id);
            CalendarTemplate template = templateService.updateTemplate(templateId, input);
            LOG.infof("Successfully updated template: %s (ID: %s)", template.name, template.id);
            return template;

        } catch (IllegalArgumentException e) {
            LOG.errorf(e, "Failed to update template: %s", e.getMessage());
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "Unexpected error updating template");
            throw new IllegalStateException("Failed to update template: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a calendar template using soft delete (admin only).
     * Requires ADMIN role in JWT claims.
     * Sets isActive=false instead of permanently removing the template.
     * Allows soft-deleting templates with existing calendars to preserve data integrity.
     *
     * @param id Template ID
     * @return true if soft-deleted successfully
     */
    @Mutation("deleteTemplate")
    @Description("Soft-delete a calendar template (admin only). Requires ADMIN role in JWT claims. Sets isActive=false instead of permanently removing the template.")
    @RolesAllowed("ADMIN")
    @Transactional
    public Boolean deleteTemplate(
        @Name("id")
        @Description("Template ID to soft-delete")
        @NotNull
        String id
    ) {
        LOG.infof("Mutation: deleteTemplate(id=%s) - soft delete", id);

        try {
            UUID templateId = UUID.fromString(id);
            templateService.deleteTemplate(templateId);
            LOG.infof("Successfully soft-deleted template: %s", id);
            return true;

        } catch (IllegalArgumentException e) {
            LOG.errorf(e, "Failed to delete template: %s", e.getMessage());
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "Unexpected error deleting template");
            throw new IllegalStateException("Failed to delete template: " + e.getMessage(), e);
        }
    }
}
