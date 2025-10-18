<template>
  <div class="calendar-browser">
    <!-- Admin Button (floating) -->
    <Button
      v-if="authStore.isAdmin"
      icon="pi pi-cog"
      label="Admin"
      class="admin-button"
      severity="secondary"
      @click="goToAdmin"
    />

    <!-- User Menu (floating) -->
    <div v-if="authStore.isAuthenticated" class="user-menu">
      <Button
        :label="authStore.userName"
        icon="pi pi-user"
        outlined
        @click="toggleUserMenu"
      />
      <Menu ref="userMenuRef" :model="userMenuItems" :popup="true" />
    </div>

    <!-- Hero Section -->
    <section class="hero-section">
      <div class="hero-content-container">
        <div class="hero-content">
          <h1 class="hero-title">Custom Calendar Printing</h1>
          <p class="hero-subtitle">
            Create beautiful, personalized calendars with custom designs.
            Perfect for gifts, planning, or decoration.
          </p>
          <div class="hero-actions">
            <Button
              label="Create New Calendar"
              icon="pi pi-plus"
              size="large"
              class="create-button"
              @click="createNewCalendar"
            />
            <Button
              label="Browse Templates"
              icon="pi pi-th-large"
              size="large"
              outlined
              severity="secondary"
              class="browse-button"
              @click="scrollToTemplates"
            />
          </div>
        </div>
      </div>
    </section>

    <!-- Templates Section -->
    <section class="templates-section">
      <div class="container">
        <h2 class="section-title">Choose a Template</h2>
        <p class="section-subtitle">
          Select from our professionally designed templates to get started
        </p>

        <!-- Loading state -->
        <div v-if="calendarStore.loading" class="loading-container">
          <ProgressSpinner />
        </div>

        <!-- Error state -->
        <div v-else-if="calendarStore.error" class="error-container">
          <Message severity="error" :closable="false">
            {{ calendarStore.error }}
          </Message>
          <Button
            label="Try Again"
            icon="pi pi-refresh"
            class="mt-4"
            @click="loadTemplates"
          />
        </div>

        <!-- Templates Grid -->
        <div
          v-else-if="calendarStore.templates.length > 0"
          class="templates-grid"
        >
          <Card
            v-for="template in calendarStore.sortedTemplates"
            :key="template.id"
            class="template-card"
          >
            <template #header>
              <div class="template-preview">
                <!-- Show preview if available -->
                <div
                  v-if="templatePreviews[template.id]"
                  class="preview-svg"
                  v-html="templatePreviews[template.id]"
                ></div>
                <!-- Show loading spinner while generating -->
                <div
                  v-else-if="
                    isGeneratingPreviews && previewQueue.includes(template.id)
                  "
                  class="preview-loading"
                >
                  <ProgressSpinner style="width: 40px; height: 40px" />
                </div>
                <!-- Show thumbnail URL if provided -->
                <img
                  v-else-if="template.thumbnailUrl"
                  :src="template.thumbnailUrl"
                  :alt="template.name"
                  class="preview-image"
                />
                <!-- Default calendar icon -->
                <div v-else class="preview-placeholder">
                  <i class="pi pi-calendar text-4xl text-gray-400"></i>
                </div>
                <Tag
                  v-if="template.isFeatured"
                  value="Featured"
                  severity="warning"
                  class="featured-badge"
                />
              </div>
            </template>
            <template #title>
              {{ template.name }}
            </template>
            <template #content>
              <p class="template-description">{{ template.description }}</p>
            </template>
            <template #footer>
              <div class="template-actions">
                <Button
                  label="Preview"
                  icon="pi pi-eye"
                  outlined
                  size="small"
                  class="flex-1"
                  @click="previewTemplate(template)"
                />
                <Button
                  label="Customize"
                  icon="pi pi-pencil"
                  size="small"
                  class="flex-1"
                  @click="customizeTemplate(template)"
                />
              </div>
            </template>
          </Card>
        </div>

        <!-- No templates state -->
        <div v-else class="empty-state">
          <i class="pi pi-inbox text-6xl text-gray-300 mb-4"></i>
          <p class="text-gray-500 mb-4">No templates available yet</p>
          <Button
            label="Create New Calendar"
            icon="pi pi-plus"
            size="large"
            @click="createNewCalendar"
          />
        </div>
      </div>
    </section>

    <!-- Preview Modal -->
    <Dialog
      v-model:visible="showPreviewModal"
      modal
      :header="selectedTemplate?.name"
      :style="{ width: '90vw', maxWidth: '1200px' }"
      :maximizable="true"
    >
      <div v-if="selectedTemplate" class="preview-modal-content">
        <div v-if="previewLoading" class="preview-modal-loading">
          <ProgressSpinner />
        </div>
        <div
          v-else-if="previewSvg"
          class="preview-modal-svg"
          v-html="previewSvg"
        ></div>
        <div v-else class="preview-modal-empty">Preview not available</div>
      </div>
      <template #footer>
        <Button label="Close" text @click="showPreviewModal = false" />
        <Button
          label="Customize This Template"
          icon="pi pi-pencil"
          @click="customizeTemplate(selectedTemplate)"
        />
      </template>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRouter } from "vue-router";
import { useToast } from "primevue/usetoast";
import { useCalendarStore } from "../../stores/calendarStore";
import { useAuthStore } from "../../stores/authStore";
import { scaleSvgForThumbnail } from "../../services/calendarService";
import Card from "primevue/card";
import Button from "primevue/button";
import Dialog from "primevue/dialog";
import ProgressSpinner from "primevue/progressspinner";
import Tag from "primevue/tag";
import Message from "primevue/message";
import Menu from "primevue/menu";
import type { CalendarTemplate } from "../../stores/calendarStore";
import type { MenuItem } from "primevue/menuitem";

const router = useRouter();
const toast = useToast();
const calendarStore = useCalendarStore();
const authStore = useAuthStore();

// State
const showPreviewModal = ref(false);
const selectedTemplate = ref<CalendarTemplate | null>(null);
const previewSvg = ref("");
const previewLoading = ref(false);
const templatePreviews = ref<Record<string, string>>({});
const previewQueue = ref<string[]>([]);
const isGeneratingPreviews = ref(false);
const userMenuRef = ref();

// User menu items
const userMenuItems = ref<MenuItem[]>([
  {
    label: "Logout",
    icon: "pi pi-sign-out",
    command: () => {
      authStore.logout();
      window.location.reload();
    },
  },
]);

// Toggle user menu
const toggleUserMenu = (event: Event) => {
  userMenuRef.value.toggle(event);
};

// Load templates
const loadTemplates = async () => {
  try {
    await calendarStore.fetchTemplates(true);
    generateTemplatePreviews();
  } catch (error: any) {
    console.error("Error loading templates:", error);
    toast.add({
      severity: "error",
      summary: "Error",
      detail: error.message || "Failed to load templates",
      life: 3000,
    });
  }
};

// Generate previews for all templates
const generateTemplatePreviews = () => {
  // Queue templates that need previews and don't have previewSvg
  previewQueue.value = calendarStore.templates
    .filter(
      (template) =>
        template.configuration &&
        !templatePreviews.value[template.id] &&
        !template.previewSvg,
    )
    .map((t) => t.id);

  // Set existing previewSvg from templates
  calendarStore.templates.forEach((template) => {
    if (template.previewSvg) {
      templatePreviews.value[template.id] = scaleSvgForThumbnail(
        template.previewSvg,
      );
    }
  });

  processPreviewQueue();
};

// Process preview generation queue
const processPreviewQueue = async () => {
  if (isGeneratingPreviews.value || previewQueue.value.length === 0) {
    return;
  }

  isGeneratingPreviews.value = true;

  while (previewQueue.value.length > 0) {
    const templateId = previewQueue.value.shift();
    if (!templateId) continue;

    const template = calendarStore.templates.find((t) => t.id === templateId);
    if (!template) continue;

    try {
      // Generate preview for this template
      const response = await fetch("/api/calendar/generate", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(template.configuration),
      });

      if (response.ok) {
        const svg = await response.text();
        templatePreviews.value[template.id] = scaleSvgForThumbnail(svg);
      }
    } catch (error) {
      console.error(
        `Error generating preview for template ${template.id}:`,
        error,
      );
    }

    // Small delay between requests to avoid overwhelming the server
    await new Promise((resolve) => setTimeout(resolve, 100));
  }

  isGeneratingPreviews.value = false;
};

// Preview template in modal
const previewTemplate = async (template: CalendarTemplate) => {
  selectedTemplate.value = template;
  showPreviewModal.value = true;
  previewLoading.value = true;

  try {
    if (template.previewSvg) {
      previewSvg.value = template.previewSvg;
    } else {
      // Generate preview from configuration
      const response = await fetch("/api/calendar/generate", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(template.configuration),
      });

      if (response.ok) {
        previewSvg.value = await response.text();
      } else {
        throw new Error("Failed to generate preview");
      }
    }
  } catch (error) {
    console.error("Error generating preview:", error);
    toast.add({
      severity: "error",
      summary: "Error",
      detail: "Failed to generate calendar preview",
      life: 3000,
    });
    previewSvg.value = "";
  } finally {
    previewLoading.value = false;
  }
};

// Customize template - navigate to editor
const customizeTemplate = (template: CalendarTemplate | null) => {
  if (!template) return;
  showPreviewModal.value = false;
  router.push({
    path: `/editor/${template.id}`,
  });
};

// Navigate to admin dashboard
const goToAdmin = () => {
  console.log("Admin button clicked");
  console.log("Is admin?", authStore.isAdmin);
  console.log("Current route:", router.currentRoute.value.path);

  router
    .push("/admin")
    .then(() => {
      console.log("Navigation successful");
    })
    .catch((err) => {
      console.error("Navigation failed:", err);
    });
};

// Create new calendar - navigate to generator
const createNewCalendar = () => {
  router.push("/generator");
};

// Scroll to templates section
const scrollToTemplates = () => {
  const templatesSection = document.querySelector(".templates-section");
  if (templatesSection) {
    templatesSection.scrollIntoView({ behavior: "smooth", block: "start" });
  }
};

// Load templates on mount
onMounted(() => {
  loadTemplates();
});
</script>

<style scoped>
.calendar-browser {
  min-height: 100vh;
}

.hero-section {
  position: relative;
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--hero-bold-gradient);
  color: white;
  padding: 3rem 1rem;
}

.hero-content-container {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
}

.hero-content {
  text-align: center;
  color: white;
  padding: 2rem;
  max-width: 800px;
}

.hero-title {
  font-size: 3rem;
  font-weight: bold;
  margin-bottom: 1rem;
  text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.2);
}

.hero-subtitle {
  font-size: 1.25rem;
  opacity: 0.95;
  margin-bottom: 2rem;
}

.hero-actions {
  display: flex;
  gap: 1rem;
  justify-content: center;
  flex-wrap: wrap;
}

.create-button,
.browse-button {
  min-width: 180px;
}

@media (max-width: 768px) {
  .hero-title {
    font-size: 2rem;
  }
  .hero-subtitle {
    font-size: 1rem;
  }
  .hero-actions {
    flex-direction: column;
    align-items: stretch;
  }
  .create-button,
  .browse-button {
    width: 100%;
  }
}

.templates-section {
  padding: 4rem 1rem;
  background: #f9fafb;
}

.container {
  max-width: 1400px;
  margin: 0 auto;
}

.section-title {
  font-size: 2.5rem;
  font-weight: bold;
  text-align: center;
  margin-bottom: 0.5rem;
  color: #111827;
}

.section-subtitle {
  text-align: center;
  color: #6b7280;
  margin-bottom: 3rem;
  font-size: 1.125rem;
}

.loading-container {
  display: flex;
  justify-content: center;
  padding: 4rem 0;
}

.error-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 2rem;
}

.templates-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 2rem;
}

@media (min-width: 768px) {
  .templates-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (min-width: 1024px) {
  .templates-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (min-width: 1280px) {
  .templates-grid {
    grid-template-columns: repeat(4, 1fr);
  }
}

.template-card {
  cursor: pointer;
  transition:
    transform 0.2s,
    box-shadow 0.2s;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.template-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1);
}

.template-preview {
  position: relative;
  height: 200px;
  background: #f3f4f6;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-svg,
.preview-image,
.preview-loading,
.preview-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-svg :deep(svg),
.preview-image {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.featured-badge {
  position: absolute;
  top: 0.5rem;
  right: 0.5rem;
}

.template-description {
  color: #6b7280;
  font-size: 0.875rem;
  min-height: 3rem;
  line-height: 1.5;
}

.template-actions {
  display: flex;
  gap: 0.5rem;
}

.empty-state {
  text-align: center;
  padding: 4rem 0;
}

.preview-modal-content {
  max-height: 70vh;
  overflow: auto;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 1rem;
}

.preview-modal-loading,
.preview-modal-empty {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 4rem 0;
  color: #9ca3af;
}

.preview-modal-svg :deep(svg) {
  max-width: 100%;
  height: auto;
}

.admin-button {
  position: fixed;
  top: 1rem;
  right: 1rem;
  z-index: 1000;
  box-shadow:
    0 4px 6px -1px rgba(0, 0, 0, 0.1),
    0 2px 4px -1px rgba(0, 0, 0, 0.06);
}

.user-menu {
  position: fixed;
  top: 1rem;
  right: 9rem;
  z-index: 1000;
}
</style>
