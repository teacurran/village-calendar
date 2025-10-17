<template>
  <div class="template-gallery">
    <div class="gallery-container">
      <!-- Header -->
      <div class="gallery-header">
        <h1 class="gallery-title">Calendar Templates</h1>
        <p class="gallery-subtitle">
          Choose a template to start creating your custom calendar
        </p>
      </div>

      <!-- Error Message -->
      <Message v-if="error" severity="error" class="error-message">
        {{ error }}
      </Message>

      <!-- Loading State -->
      <div v-if="loading" class="loading-container">
        <ProgressSpinner />
      </div>

      <!-- Template Grid -->
      <div v-else-if="activeTemplates.length > 0" class="templates-grid">
        <TemplateCard
          v-for="template in activeTemplates"
          :key="template.id"
          :template="template"
          :loading="creatingCalendarId === template.id"
          @start-from-template="handleStartFromTemplate"
        />
      </div>

      <!-- Empty State -->
      <div v-else class="empty-state">
        <i class="pi pi-images empty-icon"></i>
        <h2 class="empty-title">No Templates Available</h2>
        <p class="empty-description">
          No calendar templates are currently available. Please check back
          later.
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { useToast } from "primevue/usetoast";
import { useTemplateStore } from "@/stores/templateStore";
import { useCalendarStore } from "@/stores/calendarStore";
import { useAuthStore } from "@/stores/authStore";
import TemplateCard from "@/components/calendar/TemplateCard.vue";
import Message from "primevue/message";
import ProgressSpinner from "primevue/progressspinner";

const router = useRouter();
const toast = useToast();
const templateStore = useTemplateStore();
const calendarStore = useCalendarStore();
const authStore = useAuthStore();

// State
const creatingCalendarId = ref<string | null>(null);

// Computed
const loading = computed(() => templateStore.loading);
const error = computed(() => templateStore.error);
const activeTemplates = computed(() => templateStore.activeTemplates);

// Methods
const handleStartFromTemplate = async (templateId: string) => {
  // Check authentication
  if (!authStore.isAuthenticated) {
    toast.add({
      severity: "warn",
      summary: "Authentication Required",
      detail: "Please log in to create a calendar from this template.",
      life: 5000,
    });

    // Save intended destination and redirect to home (which will initiate OAuth)
    sessionStorage.setItem("auth_return_to", "/templates");
    router.push({ name: "home" });
    return;
  }

  // Find template
  const template = templateStore.templates.find((t) => t.id === templateId);
  if (!template) {
    toast.add({
      severity: "error",
      summary: "Template Not Found",
      detail: "The selected template could not be found.",
      life: 5000,
    });
    return;
  }

  // Set loading state for this specific card
  creatingCalendarId.value = templateId;

  try {
    // Get current year (or next year if we're in late December)
    const now = new Date();
    const currentYear = now.getFullYear();
    const currentMonth = now.getMonth();
    // If it's December, default to next year
    const defaultYear = currentMonth === 11 ? currentYear + 1 : currentYear;

    // Create calendar
    const calendar = await calendarStore.createCalendar({
      templateId: template.id,
      name: `${template.name} ${defaultYear}`,
      year: defaultYear,
    });

    // Show success message
    toast.add({
      severity: "success",
      summary: "Calendar Created",
      detail: `Your calendar "${calendar.name}" has been created successfully!`,
      life: 5000,
    });

    // Redirect to calendar editor
    router.push({ name: "calendar-editor", params: { id: calendar.id } });
  } catch (err: unknown) {
    const errorMessage =
      err instanceof Error
        ? err.message
        : "An error occurred while creating your calendar.";
    console.error("Error creating calendar:", err);
    toast.add({
      severity: "error",
      summary: "Failed to Create Calendar",
      detail: errorMessage,
      life: 5000,
    });
  } finally {
    creatingCalendarId.value = null;
  }
};

const loadTemplates = async () => {
  try {
    await templateStore.loadTemplates(true); // Load only active templates
  } catch (err) {
    console.error("Error loading templates:", err);
  }
};

// Load templates on mount
onMounted(() => {
  loadTemplates();
});
</script>

<style scoped>
.template-gallery {
  min-height: 100vh;
  background: #f9fafb;
  padding: 2rem 1rem;
}

.gallery-container {
  max-width: 1400px;
  margin: 0 auto;
}

.gallery-header {
  margin-bottom: 2rem;
  text-align: center;
}

.gallery-title {
  font-size: 2.5rem;
  font-weight: bold;
  color: #111827;
  margin-bottom: 0.5rem;
}

.gallery-subtitle {
  color: #6b7280;
  font-size: 1.125rem;
  margin-top: 0.5rem;
}

.error-message {
  margin-bottom: 2rem;
}

.loading-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}

.templates-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 1.5rem;
  margin-top: 2rem;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem 1rem;
  text-align: center;
  min-height: 400px;
}

.empty-icon {
  font-size: 5rem;
  color: #d1d5db;
  margin-bottom: 1rem;
}

.empty-title {
  font-size: 1.5rem;
  font-weight: 600;
  color: #374151;
  margin-bottom: 0.5rem;
}

.empty-description {
  font-size: 1rem;
  color: #6b7280;
  max-width: 500px;
}

/* Responsive Design */
@media (max-width: 1024px) {
  .templates-grid {
    grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  }
}

@media (max-width: 768px) {
  .template-gallery {
    padding: 1rem 0.5rem;
  }

  .gallery-title {
    font-size: 2rem;
  }

  .gallery-subtitle {
    font-size: 1rem;
  }

  .templates-grid {
    grid-template-columns: 1fr;
    gap: 1rem;
  }

  .empty-icon {
    font-size: 4rem;
  }

  .empty-title {
    font-size: 1.25rem;
  }

  .empty-description {
    font-size: 0.9375rem;
  }
}

@media (max-width: 480px) {
  .gallery-title {
    font-size: 1.5rem;
  }

  .templates-grid {
    grid-template-columns: 1fr;
  }
}
</style>
