<template>
  <div class="calendar-editor">
    <!-- Loading State -->
    <div v-if="calendarStore.loading" class="loading-container">
      <ProgressSpinner />
      <p class="loading-text">Loading calendar...</p>
    </div>

    <!-- Error State -->
    <Message
      v-else-if="calendarStore.error"
      severity="error"
      :closable="true"
      @close="calendarStore.clearError()"
    >
      {{ calendarStore.error }}
    </Message>

    <!-- Editor Content -->
    <div v-else-if="calendar" class="editor-content">
      <!-- Header -->
      <div class="editor-header">
        <div class="header-left">
          <Button
            icon="pi pi-arrow-left"
            text
            rounded
            aria-label="Go back"
            @click="navigateBack"
          />
          <div class="header-info">
            <h1 class="calendar-title">{{ calendar.name }}</h1>
            <div class="calendar-meta">
              <span class="meta-item">
                <i class="pi pi-calendar"></i>
                {{ calendar.year }}
              </span>
              <span class="meta-item">
                <i class="pi pi-tag"></i>
                {{ calendar.template.name }}
              </span>
              <Badge
                :value="calendar.status"
                :severity="getStatusSeverity(calendar.status)"
              />
            </div>
          </div>
        </div>
        <div class="header-actions">
          <Button
            label="Save"
            icon="pi pi-save"
            :loading="calendarStore.isSaving"
            :disabled="!hasUnsavedChanges"
            @click="handleSave"
          />
          <Button
            label="Preview"
            icon="pi pi-eye"
            severity="secondary"
            outlined
            @click="showPreview"
          />
        </div>
      </div>

      <!-- Saving Indicator -->
      <div v-if="calendarStore.isSaving" class="saving-indicator">
        <ProgressBar mode="indeterminate" style="height: 4px" />
      </div>

      <!-- Save Error Message -->
      <Message
        v-if="calendarStore.saveError"
        severity="error"
        :closable="true"
        @close="calendarStore.clearSaveError()"
      >
        {{ calendarStore.saveError }}
      </Message>

      <!-- Main Content Area -->
      <div class="editor-main">
        <!-- Sidebar -->
        <aside class="editor-sidebar">
          <ScrollPanel style="width: 100%; height: 100%">
            <div class="sidebar-content">
              <HolidaySelector />
              <AstronomyToggle />
            </div>
          </ScrollPanel>
        </aside>

        <!-- Calendar Grid -->
        <main class="editor-calendar">
          <div class="calendar-section">
            <div class="section-header">
              <h2 class="section-title">Calendar for {{ calendar.year }}</h2>
              <p class="section-description">
                Click on any date to add or edit an event
              </p>
            </div>
            <CalendarGrid :year="calendar.year" @date-click="handleDateClick" />
          </div>
        </main>
      </div>
    </div>

    <!-- No Calendar Found -->
    <div v-else class="empty-state">
      <i class="pi pi-calendar-times empty-icon"></i>
      <h2>Calendar Not Found</h2>
      <p>
        The calendar you're looking for doesn't exist or you don't have access
        to it.
      </p>
      <Button
        label="Go to Dashboard"
        icon="pi pi-home"
        @click="navigateToDashboard"
      />
    </div>

    <!-- Event Editor Dialog -->
    <EventEditor
      v-if="showEventEditor"
      v-model:visible="showEventEditor"
      :year="calendar?.year || new Date().getFullYear()"
      :month="selectedMonth"
      :day="selectedDay"
      @save="handleEventSave"
      @delete="handleEventDelete"
    />

    <!-- Unsaved Changes Dialog -->
    <Dialog
      v-model:visible="showUnsavedDialog"
      header="Unsaved Changes"
      :modal="true"
      :closable="false"
      :style="{ width: '450px' }"
    >
      <p>You have unsaved changes. Do you want to save before leaving?</p>
      <template #footer>
        <Button
          label="Don't Save"
          icon="pi pi-times"
          text
          @click="confirmLeave(false)"
        />
        <Button label="Cancel" icon="pi pi-ban" outlined @click="cancelLeave" />
        <Button label="Save" icon="pi pi-check" @click="confirmLeave(true)" />
      </template>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from "vue";
import { useRoute, useRouter, onBeforeRouteLeave } from "vue-router";
import { useToast } from "primevue/usetoast";
import { useCalendarStore } from "@/stores/calendarStore";
import { DEFAULT_CALENDAR_CONFIG, type CalendarEvent } from "@/types/calendar";
import Button from "primevue/button";
import ProgressSpinner from "primevue/progressspinner";
import ProgressBar from "primevue/progressbar";
import Message from "primevue/message";
import Badge from "primevue/badge";
import Dialog from "primevue/dialog";
import ScrollPanel from "primevue/scrollpanel";
import CalendarGrid from "@/components/calendar/CalendarGrid.vue";
import EventEditor from "@/components/calendar/EventEditor.vue";
import HolidaySelector from "@/components/calendar/HolidaySelector.vue";
import AstronomyToggle from "@/components/calendar/AstronomyToggle.vue";

const route = useRoute();
const router = useRouter();
const toast = useToast();
const calendarStore = useCalendarStore();

const calendarId = computed(() => route.params.id as string);
const calendar = computed(() => calendarStore.currentCalendar);
const hasUnsavedChanges = computed(() => calendarStore.hasUnsavedChanges);

const showEventEditor = ref(false);
const selectedMonth = ref(1);
const selectedDay = ref(1);
const showUnsavedDialog = ref(false);
const pendingNavigation = ref<(() => void) | null>(null);

/**
 * Load calendar on mount
 */
onMounted(async () => {
  try {
    await calendarStore.fetchCalendar(calendarId.value);

    // Initialize draft configuration
    if (calendarStore.currentCalendar) {
      if (!calendarStore.currentCalendar.configuration) {
        // Initialize with default config if none exists
        calendarStore.updateDraftConfiguration(DEFAULT_CALENDAR_CONFIG);
      } else {
        calendarStore.initializeDraftConfiguration();
      }
    }
  } catch (error) {
    const errorMessage =
      error instanceof Error ? error.message : "Failed to load calendar";
    toast.add({
      severity: "error",
      summary: "Error",
      detail: errorMessage,
      life: 5000,
    });
  }
});

/**
 * Cleanup on unmount
 */
onBeforeUnmount(() => {
  calendarStore.clearCurrentCalendar();
});

/**
 * Get status badge severity
 */
function getStatusSeverity(
  status: string,
):
  | "success"
  | "info"
  | "warn"
  | "danger"
  | "secondary"
  | "contrast"
  | undefined {
  switch (status) {
    case "READY":
      return "success";
    case "GENERATING":
      return "info";
    case "DRAFT":
      return "secondary";
    case "FAILED":
      return "danger";
    default:
      return undefined;
  }
}

/**
 * Handle date click in calendar grid
 */
function handleDateClick(month: number, day: number) {
  selectedMonth.value = month;
  selectedDay.value = day;
  showEventEditor.value = true;
}

/**
 * Handle event save
 */
async function handleEventSave(event: CalendarEvent) {
  calendarStore.addOrUpdateEvent(event);

  toast.add({
    severity: "success",
    summary: "Event Added",
    detail: "Event has been added to the calendar",
    life: 3000,
  });

  // Auto-save after a short delay
  setTimeout(() => {
    handleSave();
  }, 500);
}

/**
 * Handle event delete
 */
async function handleEventDelete(date: string) {
  calendarStore.removeEvent(date);

  toast.add({
    severity: "info",
    summary: "Event Deleted",
    detail: "Event has been removed from the calendar",
    life: 3000,
  });

  // Auto-save after a short delay
  setTimeout(() => {
    handleSave();
  }, 500);
}

/**
 * Handle save
 */
async function handleSave() {
  try {
    await calendarStore.saveDraftConfiguration(calendarId.value);

    toast.add({
      severity: "success",
      summary: "Saved",
      detail: "Calendar has been saved successfully",
      life: 3000,
    });
  } catch (error) {
    const errorMessage =
      error instanceof Error ? error.message : "Failed to save calendar";
    toast.add({
      severity: "error",
      summary: "Save Failed",
      detail: errorMessage,
      life: 5000,
    });
  }
}

/**
 * Show preview
 */
function showPreview() {
  // TODO: Implement preview functionality
  toast.add({
    severity: "info",
    summary: "Preview",
    detail: "Preview functionality coming soon",
    life: 3000,
  });
}

/**
 * Navigate back
 */
function navigateBack() {
  if (hasUnsavedChanges.value) {
    showUnsavedDialog.value = true;
    pendingNavigation.value = () => router.push("/dashboard");
  } else {
    router.push("/dashboard");
  }
}

/**
 * Navigate to dashboard
 */
function navigateToDashboard() {
  router.push("/dashboard");
}

/**
 * Confirm leave with or without save
 */
async function confirmLeave(shouldSave: boolean) {
  if (shouldSave) {
    await handleSave();
  }

  showUnsavedDialog.value = false;

  if (pendingNavigation.value) {
    pendingNavigation.value();
    pendingNavigation.value = null;
  }
}

/**
 * Cancel leave
 */
function cancelLeave() {
  showUnsavedDialog.value = false;
  pendingNavigation.value = null;
}

/**
 * Guard route changes
 */
onBeforeRouteLeave((to, from, next) => {
  if (hasUnsavedChanges.value) {
    showUnsavedDialog.value = true;
    pendingNavigation.value = () => next();
    next(false);
  } else {
    next();
  }
});
</script>

<style scoped>
.calendar-editor {
  min-height: 100vh;
  background: #f9fafb;
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  gap: 1rem;
}

.loading-text {
  color: #6b7280;
  font-size: 1rem;
}

.editor-content {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.editor-header {
  background: white;
  border-bottom: 1px solid #e5e7eb;
  padding: 1rem 2rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  position: sticky;
  top: 0;
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex: 1;
  min-width: 0;
}

.header-info {
  flex: 1;
  min-width: 0;
}

.calendar-title {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 700;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.calendar-meta {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-top: 0.25rem;
  flex-wrap: wrap;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  color: #6b7280;
  font-size: 0.875rem;
}

.header-actions {
  display: flex;
  gap: 0.5rem;
}

.saving-indicator {
  position: sticky;
  top: 73px;
  z-index: 9;
}

.editor-main {
  display: flex;
  flex: 1;
  gap: 0;
}

.editor-sidebar {
  width: 320px;
  background: white;
  border-right: 1px solid #e5e7eb;
  overflow-y: auto;
  position: sticky;
  top: 73px;
  height: calc(100vh - 73px);
}

.sidebar-content {
  padding: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.editor-calendar {
  flex: 1;
  overflow-y: auto;
}

.calendar-section {
  padding: 2rem;
}

.section-header {
  margin-bottom: 1.5rem;
}

.section-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: #1f2937;
  margin: 0 0 0.5rem 0;
}

.section-description {
  color: #6b7280;
  font-size: 0.875rem;
  margin: 0;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  padding: 2rem;
  text-align: center;
}

.empty-icon {
  font-size: 4rem;
  color: #d1d5db;
  margin-bottom: 1rem;
}

.empty-state h2 {
  font-size: 1.5rem;
  font-weight: 700;
  color: #1f2937;
  margin: 0 0 0.5rem 0;
}

.empty-state p {
  color: #6b7280;
  margin: 0 0 1.5rem 0;
  max-width: 400px;
}

/* Responsive adjustments */
@media (max-width: 1024px) {
  .editor-sidebar {
    position: static;
    width: 100%;
    height: auto;
    border-right: none;
    border-bottom: 1px solid #e5e7eb;
  }

  .editor-main {
    flex-direction: column;
  }
}

@media (max-width: 768px) {
  .editor-header {
    flex-direction: column;
    align-items: flex-start;
    padding: 1rem;
  }

  .header-left {
    width: 100%;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .calendar-title {
    font-size: 1.25rem;
  }

  .calendar-section {
    padding: 1rem;
  }

  .section-title {
    font-size: 1.25rem;
  }
}
</style>
