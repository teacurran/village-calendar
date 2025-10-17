<script setup lang="ts">
import { onMounted, ref, computed } from "vue";
import {
  Breadcrumb,
  Button,
  Column,
  DataTable,
  Dialog,
  InputText,
  Textarea,
  Checkbox,
  Message,
  Tag,
  InputNumber,
} from "primevue";
import { useToast } from "primevue/usetoast";
import { useTemplateStore } from "../../stores/templateStore";
import type { CalendarTemplate, TemplateInput } from "../../types/template";

const toast = useToast();
const templateStore = useTemplateStore();

// Breadcrumb
const homeBreadcrumb = ref({
  icon: "pi pi-home",
  url: "/",
});

const breadCrumbs = ref([{ label: "Admin" }, { label: "Template Manager" }]);

// Loading state
const loading = computed(() => templateStore.loading);
const error = computed(() => templateStore.error);
const templates = computed(() => templateStore.templates);

// Dialog state
const templateDialog = ref(false);
const editingTemplate = ref<CalendarTemplate | null>(null);
const templateForm = ref<TemplateInput>({
  name: "",
  description: "",
  configuration: {},
  thumbnailUrl: "",
  previewSvg: "",
  isActive: true,
  isFeatured: false,
  displayOrder: 0,
});

// Configuration JSON editor
const configurationJson = ref("");

// Validation errors
const validationErrors = ref<string[]>([]);

// Search filter
const searchFilter = ref("");

// Filtered templates
const filteredTemplates = computed(() => {
  if (!searchFilter.value) {
    return templates.value;
  }
  const search = searchFilter.value.toLowerCase();
  return templates.value.filter(
    (t) =>
      t.name.toLowerCase().includes(search) ||
      (t.description && t.description.toLowerCase().includes(search)),
  );
});

/**
 * Open dialog to add new template
 */
function openAddTemplate() {
  editingTemplate.value = null;
  templateForm.value = {
    name: "",
    description: "",
    configuration: {},
    thumbnailUrl: "",
    previewSvg: "",
    isActive: true,
    isFeatured: false,
    displayOrder: 0,
  };
  configurationJson.value = JSON.stringify({}, null, 2);
  validationErrors.value = [];
  templateDialog.value = true;
}

/**
 * Open dialog to edit existing template
 */
function editTemplate(template: CalendarTemplate) {
  editingTemplate.value = template;
  templateForm.value = {
    name: template.name,
    description: template.description || "",
    configuration: template.configuration,
    thumbnailUrl: template.thumbnailUrl || "",
    previewSvg: template.previewSvg || "",
    isActive: template.isActive,
    isFeatured: template.isFeatured,
    displayOrder: template.displayOrder,
  };
  configurationJson.value = JSON.stringify(template.configuration, null, 2);
  validationErrors.value = [];
  templateDialog.value = true;
}

/**
 * Validate template form
 */
function validateTemplateForm(): boolean {
  validationErrors.value = [];

  if (!templateForm.value.name || templateForm.value.name.trim() === "") {
    validationErrors.value.push("Template name is required");
  }

  // Validate JSON configuration
  try {
    const config = JSON.parse(configurationJson.value);
    templateForm.value.configuration = config;
  } catch (err) {
    validationErrors.value.push(
      "Invalid JSON configuration: " + (err as Error).message,
    );
    return false;
  }

  return validationErrors.value.length === 0;
}

/**
 * Save template (create or update)
 */
async function saveTemplate() {
  if (!validateTemplateForm()) {
    return;
  }

  let result;
  if (editingTemplate.value) {
    // Update existing
    result = await templateStore.updateTemplate(
      editingTemplate.value.id,
      templateForm.value,
    );
  } else {
    // Create new
    result = await templateStore.createTemplate(templateForm.value);
  }

  if (result) {
    toast.add({
      severity: "success",
      summary: "Success",
      detail: `Template ${editingTemplate.value ? "updated" : "created"} successfully`,
      life: 3000,
    });
    templateDialog.value = false;
  } else {
    toast.add({
      severity: "error",
      summary: "Error",
      detail: templateStore.error || "Failed to save template",
      life: 5000,
    });
  }
}

/**
 * Delete template with confirmation
 */
async function confirmDeleteTemplate(template: CalendarTemplate) {
  if (
    !confirm(`Are you sure you want to delete template "${template.name}"?`)
  ) {
    return;
  }

  const success = await templateStore.deleteTemplate(template.id);

  if (success) {
    toast.add({
      severity: "success",
      summary: "Success",
      detail: "Template deleted successfully",
      life: 3000,
    });
  } else {
    toast.add({
      severity: "error",
      summary: "Error",
      detail: templateStore.error || "Failed to delete template",
      life: 5000,
    });
  }
}

/**
 * Toggle template active status
 */
async function toggleActive(template: CalendarTemplate) {
  const input: TemplateInput = {
    name: template.name,
    description: template.description || undefined,
    configuration: template.configuration,
    thumbnailUrl: template.thumbnailUrl || undefined,
    previewSvg: template.previewSvg || undefined,
    isActive: !template.isActive,
    isFeatured: template.isFeatured,
    displayOrder: template.displayOrder,
  };

  const result = await templateStore.updateTemplate(template.id, input);

  if (result) {
    toast.add({
      severity: "success",
      summary: "Success",
      detail: `Template ${result.isActive ? "activated" : "deactivated"}`,
      life: 3000,
    });
  } else {
    toast.add({
      severity: "error",
      summary: "Error",
      detail: templateStore.error || "Failed to update template",
      life: 5000,
    });
  }
}

/**
 * Format date for display
 */
function formatDate(date: string): string {
  return new Date(date).toLocaleDateString();
}

onMounted(async () => {
  await templateStore.loadTemplates(undefined); // Load all templates (including inactive)
});
</script>

<template>
  <Breadcrumb :home="homeBreadcrumb" :model="breadCrumbs" class="mb-4" />

  <div class="template-manager-page">
    <div class="flex justify-between items-center mb-6">
      <h1 class="text-3xl font-bold">Template Manager</h1>
      <Button icon="pi pi-plus" label="Add Template" @click="openAddTemplate" />
    </div>

    <!-- Error Display -->
    <Message v-if="error" severity="error" class="mb-4">{{ error }}</Message>

    <!-- Search Filter -->
    <div class="mb-4">
      <InputText
        v-model="searchFilter"
        placeholder="Search templates by name or description..."
        class="w-full md:w-1/2"
      >
        <template #prepend>
          <i class="pi pi-search" />
        </template>
      </InputText>
    </div>

    <!-- Templates DataTable -->
    <DataTable
      :value="filteredTemplates"
      :loading="loading"
      striped-rows
      paginator
      :rows="10"
      table-style="min-width: 50rem"
    >
      <template #empty>
        <div class="text-center py-4">No templates found</div>
      </template>

      <Column field="name" header="Name" sortable style="width: 20%">
        <template #body="{ data }">
          <span class="font-semibold">{{ data.name }}</span>
        </template>
      </Column>

      <Column field="description" header="Description" style="width: 25%">
        <template #body="{ data }">
          {{ data.description || "-" }}
        </template>
      </Column>

      <Column field="isActive" header="Status" sortable style="width: 10%">
        <template #body="{ data }">
          <Tag v-if="data.isActive" severity="success" value="Active" />
          <Tag v-else severity="secondary" value="Inactive" />
        </template>
      </Column>

      <Column field="isFeatured" header="Featured" sortable style="width: 10%">
        <template #body="{ data }">
          <Tag v-if="data.isFeatured" severity="info" value="Yes" />
          <Tag v-else severity="secondary" value="No" />
        </template>
      </Column>

      <Column field="displayOrder" header="Order" sortable style="width: 10%">
        <template #body="{ data }">
          {{ data.displayOrder }}
        </template>
      </Column>

      <Column field="updated" header="Last Updated" sortable style="width: 12%">
        <template #body="{ data }">
          {{ formatDate(data.updated) }}
        </template>
      </Column>

      <Column header="Actions" style="width: 13%">
        <template #body="{ data }">
          <div class="flex gap-2">
            <Button
              v-tooltip.top="'Edit Template'"
              icon="pi pi-pencil"
              size="small"
              text
              severity="secondary"
              @click="editTemplate(data)"
            />
            <Button
              v-tooltip.top="data.isActive ? 'Deactivate' : 'Activate'"
              :icon="data.isActive ? 'pi pi-eye-slash' : 'pi pi-eye'"
              size="small"
              text
              :severity="data.isActive ? 'warning' : 'success'"
              @click="toggleActive(data)"
            />
            <Button
              v-tooltip.top="'Delete Template'"
              icon="pi pi-trash"
              size="small"
              text
              severity="danger"
              @click="confirmDeleteTemplate(data)"
            />
          </div>
        </template>
      </Column>
    </DataTable>

    <!-- Template Dialog -->
    <Dialog
      v-model:visible="templateDialog"
      :style="{ width: '800px' }"
      :header="editingTemplate ? 'Edit Template' : 'Add Template'"
      :modal="true"
    >
      <div class="flex flex-col gap-4">
        <!-- Validation Errors -->
        <Message v-if="validationErrors.length > 0" severity="error">
          <ul class="list-disc pl-4">
            <li v-for="err in validationErrors" :key="err">{{ err }}</li>
          </ul>
        </Message>

        <!-- Template Name -->
        <div class="field">
          <label for="template-name" class="font-semibold"
            >Template Name *</label
          >
          <InputText
            id="template-name"
            v-model="templateForm.name"
            class="w-full"
            placeholder="E.g., Modern Minimalist 2025"
          />
        </div>

        <!-- Template Description -->
        <div class="field">
          <label for="template-description" class="font-semibold"
            >Description</label
          >
          <Textarea
            id="template-description"
            v-model="templateForm.description"
            class="w-full"
            rows="3"
            placeholder="Brief description of the template design..."
          />
        </div>

        <!-- Configuration JSON -->
        <div class="field">
          <label for="template-config" class="font-semibold"
            >Configuration (JSON) *</label
          >
          <Textarea
            id="template-config"
            v-model="configurationJson"
            class="w-full font-mono text-sm"
            rows="8"
            placeholder='{"theme": "modern", "colors": {...}}'
          />
          <small class="text-surface-500">Enter valid JSON configuration</small>
        </div>

        <!-- Thumbnail URL -->
        <div class="field">
          <label for="template-thumbnail" class="font-semibold"
            >Thumbnail URL</label
          >
          <InputText
            id="template-thumbnail"
            v-model="templateForm.thumbnailUrl"
            class="w-full"
            placeholder="https://example.com/thumbnail.jpg"
          />
        </div>

        <!-- Display Order -->
        <div class="field">
          <label for="template-order" class="font-semibold"
            >Display Order</label
          >
          <InputNumber
            id="template-order"
            v-model="templateForm.displayOrder"
            class="w-full"
            :min="0"
          />
          <small class="text-surface-500">Lower numbers appear first</small>
        </div>

        <!-- Active Checkbox -->
        <div class="field flex items-center gap-2">
          <Checkbox
            v-model="templateForm.isActive"
            binary
            input-id="template-active"
          />
          <label for="template-active" class="font-semibold"
            >Active (visible to users)</label
          >
        </div>

        <!-- Featured Checkbox -->
        <div class="field flex items-center gap-2">
          <Checkbox
            v-model="templateForm.isFeatured"
            binary
            input-id="template-featured"
          />
          <label for="template-featured" class="font-semibold">Featured</label>
        </div>
      </div>

      <template #footer>
        <Button
          label="Cancel"
          icon="pi pi-times"
          class="p-button-text"
          @click="templateDialog = false"
        />
        <Button label="Save" icon="pi pi-check" @click="saveTemplate" />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.template-manager-page {
  margin: 0 auto;
  width: 95%;
  max-width: 1400px;
  padding: 2rem;
  background-color: white;
  border-radius: 0.5rem;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1);
}

.field {
  margin-bottom: 1rem;
}

.field label {
  display: block;
  margin-bottom: 0.5rem;
}

@media (max-width: 768px) {
  .template-manager-page {
    width: 100%;
    padding: 1rem;
  }
}
</style>
