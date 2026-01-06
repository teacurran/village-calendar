package villagecompute.calendar.api.graphql;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import villagecompute.calendar.api.graphql.inputs.TemplateInput;
import villagecompute.calendar.data.models.CalendarTemplate;
import villagecompute.calendar.data.repositories.CalendarTemplateRepository;
import villagecompute.calendar.services.TemplateService;

import io.quarkus.security.identity.SecurityIdentity;

/**
 * Unit tests for TemplateGraphQL. Tests all query and mutation methods with mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
class TemplateGraphQLUnitTest {

    @InjectMocks
    TemplateGraphQL templateGraphQL;

    @Mock
    CalendarTemplateRepository templateRepository;

    @Mock
    TemplateService templateService;

    @Mock
    SecurityIdentity securityIdentity;

    private CalendarTemplate activeTemplate;
    private CalendarTemplate inactiveTemplate;
    private CalendarTemplate featuredTemplate;

    @BeforeEach
    void setUp() {
        activeTemplate = new CalendarTemplate();
        activeTemplate.id = UUID.randomUUID();
        activeTemplate.name = "Active Template";
        activeTemplate.isActive = true;
        activeTemplate.isFeatured = false;

        inactiveTemplate = new CalendarTemplate();
        inactiveTemplate.id = UUID.randomUUID();
        inactiveTemplate.name = "Inactive Template";
        inactiveTemplate.isActive = false;
        inactiveTemplate.isFeatured = false;

        featuredTemplate = new CalendarTemplate();
        featuredTemplate.id = UUID.randomUUID();
        featuredTemplate.name = "Featured Template";
        featuredTemplate.isActive = true;
        featuredTemplate.isFeatured = true;
    }

    @Nested
    class TemplatesQueryTests {

        @Test
        void templates_FeaturedTrue_ReturnsFeaturedTemplates() {
            when(templateRepository.findFeaturedTemplates()).thenReturn(List.of(featuredTemplate));

            List<CalendarTemplate> result = templateGraphQL.templates(true, true);

            assertEquals(1, result.size());
            assertEquals(featuredTemplate.id, result.get(0).id);
            verify(templateRepository).findFeaturedTemplates();
        }

        @Test
        void templates_ActiveTrue_ReturnsActiveTemplates() {
            when(templateRepository.findActiveTemplates()).thenReturn(List.of(activeTemplate, featuredTemplate));

            List<CalendarTemplate> result = templateGraphQL.templates(true, null);

            assertEquals(2, result.size());
            verify(templateRepository).findActiveTemplates();
        }

        @Test
        void templates_ActiveFalse_ReturnsInactiveTemplates() {
            when(templateRepository.findByActiveStatus(false)).thenReturn(List.of(inactiveTemplate));

            List<CalendarTemplate> result = templateGraphQL.templates(false, null);

            assertEquals(1, result.size());
            assertEquals(inactiveTemplate.id, result.get(0).id);
            verify(templateRepository).findByActiveStatus(false);
        }

        @Test
        void templates_NoFilter_ReturnsAllTemplates() {
            when(templateRepository.listAll()).thenReturn(List.of(activeTemplate, inactiveTemplate, featuredTemplate));

            List<CalendarTemplate> result = templateGraphQL.templates(null, null);

            assertEquals(3, result.size());
            verify(templateRepository).listAll();
        }

        @Test
        void templates_EmptyResult_ReturnsEmptyList() {
            when(templateRepository.findActiveTemplates()).thenReturn(Collections.emptyList());

            List<CalendarTemplate> result = templateGraphQL.templates(true, null);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class TemplateQueryTests {

        @Test
        void template_InvalidUuid_ThrowsException() {
            assertThrows(IllegalArgumentException.class, () -> templateGraphQL.template("not-a-valid-uuid"));
        }
    }

    @Nested
    class CreateTemplateMutationTests {

        @Test
        void createTemplate_ValidInput_ReturnsCreatedTemplate() {
            TemplateInput input = new TemplateInput();
            input.name = "New Template";
            input.description = "A new template";
            input.isActive = true;

            when(templateService.createTemplate(input)).thenReturn(activeTemplate);

            CalendarTemplate result = templateGraphQL.createTemplate(input);

            assertNotNull(result);
            assertEquals(activeTemplate.id, result.id);
            verify(templateService).createTemplate(input);
        }

        @Test
        void createTemplate_ServiceThrowsIllegalArgument_Rethrows() {
            TemplateInput input = new TemplateInput();
            input.name = "Duplicate Name";

            when(templateService.createTemplate(input))
                    .thenThrow(new IllegalArgumentException("Template name already exists"));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> templateGraphQL.createTemplate(input));

            assertTrue(ex.getMessage().contains("already exists"));
        }

        @Test
        void createTemplate_ServiceThrowsUnexpected_WrapsInIllegalState() {
            TemplateInput input = new TemplateInput();
            input.name = "Test";

            when(templateService.createTemplate(input)).thenThrow(new RuntimeException("Database error"));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> templateGraphQL.createTemplate(input));

            assertTrue(ex.getMessage().contains("Failed to create template"));
        }
    }

    @Nested
    class UpdateTemplateMutationTests {

        @Test
        void updateTemplate_ValidInput_ReturnsUpdatedTemplate() {
            TemplateInput input = new TemplateInput();
            input.name = "Updated Template";

            when(securityIdentity.getRoles()).thenReturn(Set.of("ADMIN"));
            when(templateService.updateTemplate(activeTemplate.id, input)).thenReturn(activeTemplate);

            CalendarTemplate result = templateGraphQL.updateTemplate(activeTemplate.id.toString(), input);

            assertNotNull(result);
            verify(templateService).updateTemplate(activeTemplate.id, input);
        }

        @Test
        void updateTemplate_InvalidUuid_ThrowsException() {
            TemplateInput input = new TemplateInput();
            when(securityIdentity.getRoles()).thenReturn(Set.of("ADMIN"));

            assertThrows(IllegalArgumentException.class, () -> templateGraphQL.updateTemplate("invalid-uuid", input));
        }

        @Test
        void updateTemplate_ServiceThrowsIllegalArgument_Rethrows() {
            TemplateInput input = new TemplateInput();
            input.name = "Test";

            when(securityIdentity.getRoles()).thenReturn(Set.of("ADMIN"));
            when(templateService.updateTemplate(any(), eq(input)))
                    .thenThrow(new IllegalArgumentException("Template not found"));

            String templateId = activeTemplate.id.toString();
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> templateGraphQL.updateTemplate(templateId, input));

            assertTrue(ex.getMessage().contains("not found"));
        }

        @Test
        void updateTemplate_ServiceThrowsUnexpected_WrapsInIllegalState() {
            TemplateInput input = new TemplateInput();
            input.name = "Test";

            when(securityIdentity.getRoles()).thenReturn(Set.of("ADMIN"));
            when(templateService.updateTemplate(any(), eq(input))).thenThrow(new RuntimeException("DB error"));

            String templateId = activeTemplate.id.toString();
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> templateGraphQL.updateTemplate(templateId, input));

            assertTrue(ex.getMessage().contains("Failed to update template"));
        }
    }

    @Nested
    class DeleteTemplateMutationTests {

        @Test
        void deleteTemplate_ValidId_ReturnsTrue() {
            doNothing().when(templateService).deleteTemplate(activeTemplate.id);

            Boolean result = templateGraphQL.deleteTemplate(activeTemplate.id.toString());

            assertTrue(result);
            verify(templateService).deleteTemplate(activeTemplate.id);
        }

        @Test
        void deleteTemplate_InvalidUuid_ThrowsException() {
            assertThrows(IllegalArgumentException.class, () -> templateGraphQL.deleteTemplate("invalid-uuid"));
        }

        @Test
        void deleteTemplate_ServiceThrowsIllegalArgument_Rethrows() {
            doThrow(new IllegalArgumentException("Template not found")).when(templateService).deleteTemplate(any());

            String templateId = activeTemplate.id.toString();
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> templateGraphQL.deleteTemplate(templateId));

            assertTrue(ex.getMessage().contains("not found"));
        }

        @Test
        void deleteTemplate_ServiceThrowsUnexpected_WrapsInIllegalState() {
            doThrow(new RuntimeException("DB error")).when(templateService).deleteTemplate(any());

            String templateId = activeTemplate.id.toString();
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> templateGraphQL.deleteTemplate(templateId));

            assertTrue(ex.getMessage().contains("Failed to delete template"));
        }
    }
}
