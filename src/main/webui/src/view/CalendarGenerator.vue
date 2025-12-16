<template>
  <div class="calendar-generator">
    <!-- Template Selection Drawer -->
    <Drawer
      v-model:visible="showTemplateDrawer"
      position="left"
      :style="{ width: '400px' }"
      class="template-drawer"
    >
      <template #header>
        <div class="flex items-center gap-2">
          <i class="pi pi-palette text-xl"></i>
          <span class="font-semibold">Choose a Template</span>
        </div>
      </template>

      <div class="template-drawer-content">
        <p class="text-sm text-gray-600 mb-4">
          Select a template to customize your calendar
        </p>

        <div v-if="loadingAllTemplates" class="flex justify-center py-8">
          <ProgressSpinner />
        </div>

        <div
          v-else-if="allTemplates.length === 0"
          class="text-center py-8 text-gray-500"
        >
          <i class="pi pi-inbox text-4xl mb-3"></i>
          <p>No templates available</p>
        </div>

        <div v-else class="space-y-3">
          <div
            v-for="template in sortedAllTemplates"
            :key="template.id"
            class="template-card-item p-3 border rounded-lg cursor-pointer hover:shadow-md transition-shadow"
            :class="{
              'ring-2 ring-blue-500': currentTemplateId === template.id,
            }"
            @click="selectTemplate(template)"
          >
            <div class="flex gap-3">
              <!-- Template Preview Thumbnail -->
              <div
                class="template-thumbnail w-20 h-20 flex-shrink-0 bg-gray-100 rounded overflow-hidden"
              >
                <div
                  v-if="templatePreviews[template.id]"
                  class="w-full h-full"
                  v-html="templatePreviews[template.id]"
                ></div>
                <div
                  v-else
                  class="w-full h-full flex items-center justify-center"
                >
                  <i class="pi pi-calendar text-2xl text-gray-400"></i>
                </div>
              </div>
              <!-- Template Info -->
              <div class="flex-1 min-w-0">
                <div class="flex items-center gap-2 mb-1">
                  <h4 class="font-semibold text-sm truncate">
                    {{ template.name }}
                  </h4>
                  <Tag
                    v-if="template.isFeatured"
                    value="Popular"
                    severity="warning"
                    class="text-xs"
                  />
                </div>
                <p
                  v-if="template.description"
                  class="text-xs text-gray-600 line-clamp-2"
                >
                  {{ template.description }}
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <Button
          label="Close"
          icon="pi pi-times"
          outlined
          class="w-full"
          @click="showTemplateDrawer = false"
        />
      </template>
    </Drawer>

    <!-- Create Your Own Wizard Drawer -->
    <CreateWizardDrawer
      v-model:visible="showCreateWizard"
      :config="config"
      @layout-change="handleWizardLayoutChange"
      @moon-change="handleWizardMoonChange"
      @display-options-change="handleWizardDisplayOptionsChange"
      @colors-change="handleWizardColorsChange"
      @holidays-change="handleWizardHolidaysChange"
    />

    <!-- Add to Cart Modal -->
    <AddToCartModal
      v-model:visible="showAddToCartModal"
      :calendar-year="config.year"
      :default-product-code="calendarEditorStore.defaultProductCode"
      @select="handleAddToCartSelect"
    />

    <!-- Main Content Area -->
    <div class="p-4">
      <!-- Preview Panel -->
      <div ref="previewContainer" class="calendar-preview">
        <div v-if="!generatedSVG" class="text-center py-12 text-gray-500">
          <ProgressSpinner />
          <p class="mt-4">Generating calendar...</p>
        </div>
        <div
          v-else
          class="ruler-wrapper"
          :class="{ 'show-rulers': showRulers }"
        >
          <!-- Scaled container - width/height set to match visual size after scale -->
          <div
            class="scaled-wrapper"
            :style="{
              width: `${3500 * zoomLevel}px`,
              height: `${2300 * zoomLevel}px`,
            }"
          >
            <div
              class="svg-container"
              :style="{
                transform: `scale(${zoomLevel})`,
                transformOrigin: 'top left',
              }"
            >
              <div class="page-border">
                <div :key="svgKey" v-html="generatedSVG"></div>
              </div>
            </div>
          </div>
          <!-- Bottom ruler -->
          <div v-if="showRulers" class="ruler ruler-bottom">
            <div
              v-for="inch in 35"
              :key="'b' + inch"
              class="ruler-mark"
              :style="{ width: `${100 * zoomLevel}px` }"
            >
              <span class="ruler-label">{{ inch }}"</span>
            </div>
          </div>
          <!-- Right ruler (23 inches total) -->
          <div v-if="showRulers" class="ruler ruler-right">
            <div
              v-for="inch in 23"
              :key="'r' + inch"
              class="ruler-mark"
              :style="{ height: `${100 * zoomLevel}px` }"
            >
              <span class="ruler-label">{{ inch }}"</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- Templates Dialog (Admin Only) -->
  <Dialog
    v-model:visible="showTemplatesDialog"
    modal
    header="Calendar Templates"
    :style="{ width: '90vw', maxWidth: '900px' }"
  >
    <div v-if="loadingTemplates" class="flex justify-center py-8">
      <ProgressSpinner />
    </div>
    <div v-else class="space-y-4">
      <DataTable
        :value="templates"
        :paginator="true"
        :rows="10"
        responsive-layout="scroll"
      >
        <Column field="name" header="Name" :sortable="true"></Column>
        <Column field="description" header="Description">
          <template #body="slotProps">
            <span class="text-sm text-gray-600">{{
              slotProps.data.description || "-"
            }}</span>
          </template>
        </Column>
        <Column header="Status" style="width: 120px">
          <template #body="slotProps">
            <div class="flex gap-2">
              <Tag
                v-if="slotProps.data.isActive"
                value="Active"
                severity="success"
              />
              <Tag
                v-if="slotProps.data.isFeatured"
                value="Featured"
                severity="warning"
              />
            </div>
          </template>
        </Column>
        <Column header="Actions" style="width: 200px">
          <template #body="slotProps">
            <div class="flex gap-2">
              <Button
                v-tooltip="'Load Template'"
                icon="pi pi-download"
                text
                rounded
                size="small"
                @click="loadTemplateConfig(slotProps.data)"
              />
              <Button
                v-if="currentTemplateId === slotProps.data.id"
                v-tooltip="'Update Template'"
                icon="pi pi-save"
                text
                rounded
                size="small"
                @click="updateTemplate(slotProps.data)"
              />
              <Button
                v-tooltip="'Duplicate Template'"
                icon="pi pi-copy"
                text
                rounded
                size="small"
                @click="duplicateTemplate(slotProps.data)"
              />
            </div>
          </template>
        </Column>
      </DataTable>
    </div>
    <template #footer>
      <Button label="Close" @click="showTemplatesDialog = false" />
    </template>
  </Dialog>

  <!-- Save Template Dialog -->
  <Dialog
    v-model:visible="showSaveTemplateDialog"
    modal
    header="Save as Template"
    :style="{ width: '500px' }"
  >
    <div class="space-y-4">
      <div>
        <label for="template-name" class="block text-sm font-medium mb-1"
          >Template Name *</label
        >
        <InputText
          id="template-name"
          v-model="templateToSave.name"
          class="w-full"
          placeholder="Enter template name"
          :class="{
            'p-invalid': !templateToSave.name && showSaveTemplateDialog,
          }"
        />
        <small
          v-if="!templateToSave.name && showSaveTemplateDialog"
          class="p-error"
          >Name is required</small
        >
      </div>

      <div>
        <label for="template-description" class="block text-sm font-medium mb-1"
          >Description</label
        >
        <Textarea
          id="template-description"
          v-model="templateToSave.description"
          rows="3"
          class="w-full"
          placeholder="Enter template description (optional)"
        />
      </div>

      <div class="flex gap-4">
        <div class="flex items-center">
          <Checkbox
            v-model="templateToSave.isActive"
            input-id="template-active"
            binary
          />
          <label for="template-active" class="ml-2">Active</label>
        </div>

        <div class="flex items-center">
          <Checkbox
            v-model="templateToSave.isFeatured"
            input-id="template-featured"
            binary
          />
          <label for="template-featured" class="ml-2">Featured</label>
        </div>
      </div>
    </div>

    <template #footer>
      <Button label="Cancel" text @click="showSaveTemplateDialog = false" />
      <Button
        label="Save Template"
        :disabled="!templateToSave.name"
        @click="confirmSaveTemplate"
      />
    </template>
  </Dialog>

  <!-- Duplicate Template Dialog -->
  <Dialog
    v-model:visible="showDuplicateDialog"
    modal
    header="Duplicate Template"
    :style="{ width: '500px' }"
  >
    <div class="space-y-4">
      <div>
        <label for="duplicate-name" class="block text-sm font-medium mb-1"
          >New Template Name *</label
        >
        <InputText
          id="duplicate-name"
          v-model="duplicateName"
          class="w-full"
          placeholder="Enter name for duplicate"
          :class="{ 'p-invalid': !duplicateName && showDuplicateDialog }"
        />
        <small v-if="!duplicateName && showDuplicateDialog" class="p-error"
          >Name is required</small
        >
      </div>
    </div>

    <template #footer>
      <Button label="Cancel" text @click="showDuplicateDialog = false" />
      <Button
        label="Duplicate"
        :disabled="!duplicateName"
        @click="confirmDuplicateTemplate"
      />
    </template>
  </Dialog>

  <!-- Saved Calendars Dialog -->
  <Dialog
    v-model:visible="showSavedCalendarsDialog"
    modal
    header="Saved Calendars"
    :style="{ width: '90vw', maxWidth: '1200px' }"
  >
    <div class="space-y-4">
      <!-- Save New/Update Section -->
      <div class="border rounded-lg p-4 bg-gray-50">
        <h3 class="font-semibold mb-3">
          {{ selectedSavedCalendar ? "Update Calendar" : "Save New Calendar" }}
        </h3>
        <div class="flex gap-2">
          <InputText
            v-model="editingCalendarName"
            placeholder="Enter calendar name"
            class="flex-1"
          />
          <Button
            :label="selectedSavedCalendar ? 'Update' : 'Save'"
            :disabled="!editingCalendarName.trim() || savingNewCalendar"
            :loading="savingNewCalendar"
            @click="saveNewCalendar"
          />
          <Button
            v-if="selectedSavedCalendar"
            label="Cancel Edit"
            outlined
            @click="handleCancelEdit"
          />
        </div>
      </div>

      <!-- Loading State -->
      <div v-if="loadingSavedCalendars" class="flex justify-center py-8">
        <ProgressSpinner />
      </div>

      <!-- Saved Calendars Grid -->
      <div
        v-else-if="savedCalendars.length > 0"
        class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4"
      >
        <div
          v-for="calendar in savedCalendars"
          :key="calendar.id"
          class="border rounded-lg p-4 hover:shadow-lg transition-shadow"
          :class="{
            'ring-2 ring-blue-500': selectedSavedCalendar?.id === calendar.id,
          }"
        >
          <!-- Calendar Preview -->
          <div class="mb-3">
            <div class="bg-gray-100 rounded p-2 h-48 overflow-hidden relative">
              <!-- Loading indicator -->
              <div
                v-if="calendarPreviews[calendar.id] === 'loading'"
                class="absolute inset-0 flex items-center justify-center bg-gray-100"
              >
                <ProgressSpinner
                  style="width: 50px; height: 50px"
                  stroke-width="4"
                />
              </div>
              <!-- SVG Preview -->
              <div
                v-else-if="
                  calendarPreviews[calendar.id] &&
                  calendarPreviews[calendar.id] !== 'error'
                "
                class="w-full h-full"
                style="
                  display: flex;
                  align-items: center;
                  justify-content: center;
                "
                v-html="calendarPreviews[calendar.id]"
              ></div>
              <!-- Error or Fallback -->
              <div v-else class="h-full flex items-center justify-center">
                <div class="text-center">
                  <i class="pi pi-calendar text-4xl text-gray-400 mb-2"></i>
                  <p class="text-sm font-medium">{{ calendar.name }}</p>
                  <p class="text-xs text-gray-500">
                    Year: {{ calendar.configuration?.year || "N/A" }}
                  </p>
                  <p class="text-xs text-gray-500">
                    Theme: {{ calendar.configuration?.theme || "default" }}
                  </p>
                  <p
                    v-if="
                      calendar.configuration?.customDates &&
                      Object.keys(calendar.configuration.customDates).length > 0
                    "
                    class="text-xs text-gray-500"
                  >
                    {{ Object.keys(calendar.configuration.customDates).length }}
                    custom event(s)
                  </p>
                </div>
              </div>
            </div>
          </div>

          <!-- Calendar Info -->
          <div class="mb-3">
            <h4 class="font-semibold">{{ calendar.name }}</h4>
            <p class="text-sm text-gray-500">
              Created: {{ new Date(calendar.createdAt).toLocaleDateString() }}
            </p>
            <p v-if="calendar.updatedAt" class="text-sm text-gray-500">
              Updated: {{ new Date(calendar.updatedAt).toLocaleDateString() }}
            </p>
          </div>

          <!-- Action Buttons -->
          <div class="flex gap-2">
            <Button
              label="Load"
              size="small"
              class="flex-1"
              @click="loadSavedCalendar(calendar)"
            />
            <Button
              label="Edit"
              size="small"
              outlined
              class="flex-1"
              @click="handleEditCalendar(calendar)"
            />
            <Button
              v-tooltip="'Delete'"
              icon="pi pi-trash"
              size="small"
              severity="danger"
              outlined
              @click="deleteSavedCalendar(calendar)"
            />
          </div>
        </div>
      </div>

      <!-- Empty State -->
      <div v-else class="text-center py-8">
        <i class="pi pi-calendar text-5xl text-gray-300 mb-3"></i>
        <p class="text-gray-500">No saved calendars yet</p>
        <p class="text-sm text-gray-400 mt-2">
          Generate a calendar and save it to see it here
        </p>
      </div>
    </div>

    <template #footer>
      <Button label="Close" @click="showSavedCalendarsDialog = false" />
    </template>
  </Dialog>

  <!-- Custom Events Dialog -->
  <Dialog
    v-model:visible="showEventsDialog"
    modal
    header="Manage Custom Events"
    :style="{ width: '90vw', maxWidth: '1200px' }"
  >
    <!-- List View (Default) -->
    <div v-if="!showEventForm" class="space-y-4">
      <!-- Compact Holiday Sets Controls -->
      <div class="border rounded-lg p-3 bg-blue-50">
        <div class="flex gap-2 items-center">
          <Dropdown
            v-model="selectedHolidaySet"
            :options="holidaySetOptions"
            option-label="label"
            option-value="value"
            placeholder="Select holiday set"
            class="flex-1"
          />
          <Button
            label="Add Holidays"
            icon="pi pi-calendar-plus"
            :disabled="!selectedHolidaySet"
            size="small"
            @click="addHolidaySet"
          />
          <div class="border-l pl-2 ml-2">
            <Button
              label="Add Event"
              icon="pi pi-plus"
              severity="primary"
              size="small"
              @click="openAddEventForm"
            />
          </div>
        </div>
      </div>

      <!-- Events Table -->
      <div class="border rounded-lg">
        <DataTable
          :value="customEvents"
          :paginator="customEvents.length > 10"
          :rows="10"
          data-key="id"
          :row-hover="true"
          responsive-layout="scroll"
        >
          <template #empty>
            <div class="text-center py-8 text-gray-500">
              No events added yet. Add holidays or create custom events.
            </div>
          </template>
          <Column field="date" header="Date" :sortable="true">
            <template #body="slotProps">
              {{ formatEventDate(slotProps.data.date) }}
            </template>
          </Column>
          <Column
            field="emoji"
            header="Emoji"
            style="width: 80px; text-align: center"
          >
            <template #body="slotProps">
              <span class="text-2xl">{{ slotProps.data.emoji }}</span>
            </template>
          </Column>
          <Column field="title" header="Title">
            <template #body="slotProps">
              {{ slotProps.data.title || "(No title)" }}
            </template>
          </Column>
          <Column field="showTitle" header="Show Title" style="width: 120px">
            <template #body="slotProps">
              <Tag
                :severity="slotProps.data.showTitle ? 'success' : 'secondary'"
              >
                {{ slotProps.data.showTitle ? "Yes" : "No" }}
              </Tag>
            </template>
          </Column>
          <Column header="Actions" style="width: 150px">
            <template #body="slotProps">
              <div class="flex gap-1">
                <Button
                  v-tooltip="'Edit event'"
                  icon="pi pi-pencil"
                  text
                  rounded
                  @click="editCustomEvent(slotProps.data)"
                />
                <Button
                  v-tooltip="'Remove event'"
                  icon="pi pi-trash"
                  severity="danger"
                  text
                  rounded
                  @click="removeCustomEvent(slotProps.index)"
                />
              </div>
            </template>
          </Column>
        </DataTable>
      </div>
    </div>

    <!-- Add/Edit Event Form View -->
    <div v-else class="space-y-4">
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <!-- Event Form -->
        <div class="border rounded-lg p-4 bg-gray-50">
          <h3 class="font-semibold mb-3">
            {{ isEditingEvent ? "Edit Event" : "Add Event" }}
          </h3>
          <div class="space-y-3">
            <div>
              <label for="event-date" class="block text-sm font-medium mb-1"
                >Date</label
              >
              <Calendar
                id="event-date"
                v-model="newEvent.date"
                date-format="yy-mm-dd"
                placeholder="Select date"
                :min-date="new Date(config.year, 0, 1)"
                :max-date="new Date(config.year, 11, 31)"
                :default-date="new Date(config.year, 0, 1)"
                class="w-full"
              />
            </div>
            <div>
              <label for="event-title" class="block text-sm font-medium mb-1"
                >Event Title</label
              >
              <InputText
                id="event-title"
                v-model="newEvent.title"
                placeholder="Birthday, Anniversary, etc."
                class="w-full"
                maxlength="50"
              />
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">Emoji</label>
              <div class="flex gap-2">
                <InputText
                  v-model="newEvent.emoji"
                  placeholder="Click to select →"
                  class="flex-1"
                  readonly
                />
                <Button
                  v-tooltip="'Select Emoji'"
                  icon="pi pi-face-smile"
                  @click="showEmojiPicker = true"
                />
              </div>
            </div>
            <div class="flex items-center gap-4">
              <Checkbox
                v-model="newEvent.showTitle"
                input-id="show-title"
                binary
              />
              <label for="show-title" class="text-sm"
                >Display title on calendar</label
              >
            </div>
            <div class="flex justify-end gap-2 pt-2">
              <Button
                label="Clear"
                icon="pi pi-times"
                text
                size="small"
                @click="clearNewEvent"
              />
              <Button
                v-if="isEditingEvent"
                label="Cancel Edit"
                text
                severity="secondary"
                size="small"
                @click="clearNewEvent"
              />
              <Button
                :label="isEditingEvent ? 'Update Event' : 'Add Event'"
                :icon="isEditingEvent ? 'pi pi-check' : 'pi pi-plus'"
                :disabled="!newEvent.date || !newEvent.emoji"
                size="small"
                @click="addCustomEvent"
              />
            </div>
          </div>
        </div>

        <!-- Cell Preview -->
        <div v-if="newEvent.emoji" class="border rounded-lg bg-white">
          <CustomEventCellPreview
            :emoji="newEvent.emoji"
            :title="newEvent.title"
            :show-title="newEvent.showTitle"
            :initial-settings="newEvent.displaySettings"
            @update:settings="updateEventDisplaySettings"
          />
        </div>
      </div>

      <!-- Form Action Buttons -->
      <div class="flex justify-end gap-2 pt-4 border-t">
        <Button label="Cancel" text @click="clearNewEvent" />
        <Button
          :label="isEditingEvent ? 'Update Event' : 'Add Event'"
          :icon="isEditingEvent ? 'pi pi-check' : 'pi pi-plus'"
          :disabled="!newEvent.date || !newEvent.emoji"
          severity="primary"
          @click="addCustomEvent"
        />
      </div>
    </div>

    <template #footer></template>
  </Dialog>

  <!-- Emoji Picker Dialog -->
  <Dialog
    v-model:visible="showEmojiPicker"
    modal
    header="Select Emoji"
    :style="{ width: '400px' }"
  >
    <div id="emoji-picker-container"></div>
    <template #footer>
      <Button label="Cancel" text @click="showEmojiPicker = false" />
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick, computed } from "vue";
import { useToast } from "primevue/usetoast";
import { useRoute, useRouter } from "vue-router";
import { useUserStore } from "../stores/user";
import { useCartStore } from "../stores/cart";
import { useAuthStore } from "../stores/authStore";
import { useCalendarEditorStore } from "../stores/calendarEditor";
import Button from "primevue/button";
import InputNumber from "primevue/inputnumber";
import Dropdown from "primevue/dropdown";
import Checkbox from "primevue/checkbox";
import Calendar from "primevue/calendar";
import InputText from "primevue/inputtext";
import Textarea from "primevue/textarea";
import Drawer from "primevue/drawer";
import Dialog from "primevue/dialog";
import DataTable from "primevue/datatable";
import Column from "primevue/column";
import Tag from "primevue/tag";
import ProgressSpinner from "primevue/progressspinner";
import "emoji-picker-element";
import CustomEventCellPreview from "../components/CustomEventCellPreview.vue";
import CreateWizardDrawer from "../components/calendar/CreateWizardDrawer.vue";
import AddToCartModal from "../components/calendar/AddToCartModal.vue";
import type {
  LayoutType,
  MoonSettings,
  DisplayOptions,
  ColorSettings,
  HolidaySettings,
  EmojiFontType,
} from "../components/calendar/CreateWizardDrawer.vue";
import { sessionFetch } from "../services/sessionService";
import {
  fetchTemplates,
  createTemplate,
  updateTemplate as updateTemplateGraphQL,
} from "../services/graphqlService";

// Feature flags
const FEATURE_FLAGS = {
  enableRulers: false, // Ruler functionality - disabled for now
};

const toast = useToast();
const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const cartStore = useCartStore();
const authStore = useAuthStore();
const calendarEditorStore = useCalendarEditorStore();

// Drawer visibility
const showTemplateDrawer = ref(false);
const showCreateWizard = ref(false);
// Add to cart modal visibility - synced with store for header access
const showAddToCartModal = computed({
  get: () => calendarEditorStore.showAddToCartModal,
  set: (value) => {
    if (value) {
      calendarEditorStore.openAddToCartModal();
    } else {
      calendarEditorStore.closeAddToCartModal();
    }
  },
});

// All templates (for all users)
const allTemplates = ref([]);
const loadingAllTemplates = ref(false);
const templatePreviews = ref({});

// Session calendar state
const currentCalendarId = ref(null);
const isAutoSaving = ref(false);
const isViewingSharedCalendar = ref(false); // True when viewing someone else's calendar
const originalCalendarId = ref(null); // Store the original calendar ID when viewing shared

// Determine default year based on current month
const currentDate = new Date();
const currentMonth = currentDate.getMonth(); // 0-indexed (0 = January, 2 = March)
const defaultYear =
  currentMonth <= 2 ? currentDate.getFullYear() : currentDate.getFullYear() + 1;

// Configuration state - minimal defaults for clean initial view
const config = ref({
  calendarType: "gregorian", // 'gregorian' or 'hebrew'
  year: defaultYear, // Current year if Jan-Mar, next year if Apr-Dec
  theme: "default", // Simple black and white theme
  layoutStyle: "grid",
  moonDisplayMode: "none", // 'none', 'phases', 'illumination'
  showWeekNumbers: false,
  compactMode: false,
  showDayNames: true,
  showDayNumbers: true,
  showGrid: true,
  highlightWeekends: true, // Highlight weekends for better visibility
  rotateMonthNames: false,
  firstDayOfWeek: "SUNDAY",
  latitude: 0, // No location by default (no rotation)
  longitude: 0, // No location by default (no rotation)
  moonSize: 20, // Default moon radius in pixels
  moonOffsetX: 25, // Horizontal offset in pixels from left edge of cell
  moonOffsetY: 45, // Vertical offset in pixels from top edge of cell
  moonBorderColor: "#c1c1c1", // Default border color
  moonBorderWidth: 0.5, // Default border width
  // Color customization - simple black and gray
  yearColor: "#000000", // Black for year
  monthColor: "#000000", // Black for months
  dayTextColor: "#000000", // Black for day numbers
  dayNameColor: "#666666", // Gray for day names
  gridLineColor: "#c1c1c1", // Light gray for grid
  weekendBgColor: "", // No weekend background
  holidayColor: "#ff5252", // Default red for holidays (not shown by default)
  customDateColor: "#4caf50", // Default green for custom dates
  moonDarkColor: "#c1c1c1", // Default gray for moon dark side
  moonLightColor: "#FFFFFF", // Default white for moon light side
  emojiPosition: "bottom-left", // Position of emojis in calendar cells
  // Holiday settings
  holidaySets: [] as string[], // List of selected holiday set IDs
  eventDisplayMode: "large" as
    | "large"
    | "large-text"
    | "small"
    | "text"
    | "none", // Display mode for events/holidays
  // Emoji font settings
  emojiFont: "noto-color" as "noto-color" | "noto-mono", // Emoji font style
});

// Theme options
const themeOptions = ref([
  { label: "Default (Black & White)", value: "default" },
  { label: "Vermont Weekends", value: "vermontWeekends" },
  { label: "Rainbow Weekends", value: "rainbowWeekends" },
  { label: "Rainbow Days (Warm)", value: "rainbowDays1" },
  { label: "Rainbow Days (Cool)", value: "rainbowDays2" },
  { label: "Rainbow Days (Full Spectrum)", value: "rainbowDays3" },
]);

// Calendar type options
const calendarTypeOptions = ref([
  { label: "Gregorian Calendar", value: "gregorian" },
  { label: "Hebrew Lunar Calendar", value: "hebrew" },
]);

// Calculate current Hebrew year (approximation)
const currentHebrewYear = computed(() => {
  const gregorianYear = new Date().getFullYear();
  const currentMonth = new Date().getMonth();
  // Hebrew year starts in September/October, so adjust accordingly
  // This is an approximation - exact conversion requires complex calculations
  const hebrewYear = gregorianYear + 3760;
  return currentMonth >= 8 ? hebrewYear + 1 : hebrewYear;
});

// Layout options
const layoutOptions = ref([
  { label: "Left Aligned (12x31)", value: "grid" },
  { label: "Day of Week Aligned (12x37)", value: "weekday-grid" },
]);

// Weekday options
const weekdayOptions = ref([
  { label: "Sunday", value: "SUNDAY" },
  { label: "Monday", value: "MONDAY" },
  { label: "Tuesday", value: "TUESDAY" },
  { label: "Wednesday", value: "WEDNESDAY" },
  { label: "Thursday", value: "THURSDAY" },
  { label: "Friday", value: "FRIDAY" },
  { label: "Saturday", value: "SATURDAY" },
]);

// Emoji position options
const emojiPositionOptions = ref([
  { label: "Top Left", value: "top-left" },
  { label: "Top Center", value: "top-center" },
  { label: "Top Right", value: "top-right" },
  { label: "Middle Left", value: "middle-left" },
  { label: "Middle Center", value: "middle-center" },
  { label: "Middle Right", value: "middle-right" },
  { label: "Bottom Left", value: "bottom-left" },
  { label: "Bottom Center", value: "bottom-center" },
  { label: "Bottom Right", value: "bottom-right" },
]);

// Popular cities with coordinates (sorted alphabetically)
const popularCities = ref([
  { label: "No Location (No Rotation)", value: "none", lat: 0, lng: 0 }, // No location option
  {
    label: "Groton, VT, USA (HQ)",
    value: "groton_vt",
    lat: 44.2172,
    lng: -72.2011,
  }, // Keep HQ at top

  // All other cities alphabetically
  { label: "Abu Dhabi, UAE", value: "abu_dhabi", lat: 24.4539, lng: 54.3773 },
  {
    label: "Adelaide, Australia",
    value: "adelaide",
    lat: -34.9285,
    lng: 138.6007,
  },
  { label: "Ahmedabad, India", value: "ahmedabad", lat: 23.0225, lng: 72.5714 },
  {
    label: "Alexandria, Egypt",
    value: "alexandria",
    lat: 31.2001,
    lng: 29.9187,
  },
  {
    label: "Amsterdam, Netherlands",
    value: "amsterdam",
    lat: 52.3676,
    lng: 4.9041,
  },
  { label: "Athens, Greece", value: "athens", lat: 37.9838, lng: 23.7275 },
  { label: "Atlanta, GA, USA", value: "atlanta", lat: 33.749, lng: -84.388 },
  {
    label: "Auckland, New Zealand",
    value: "auckland",
    lat: -36.8485,
    lng: 174.7633,
  },
  { label: "Austin, TX, USA", value: "austin", lat: 30.2672, lng: -97.7431 },
  { label: "Bangalore, India", value: "bangalore", lat: 12.9716, lng: 77.5946 },
  { label: "Bangkok, Thailand", value: "bangkok", lat: 13.7563, lng: 100.5018 },
  { label: "Barcelona, Spain", value: "barcelona", lat: 41.3851, lng: 2.1734 },
  { label: "Beijing, China", value: "beijing", lat: 39.9042, lng: 116.4074 },
  { label: "Berlin, Germany", value: "berlin", lat: 52.52, lng: 13.405 },
  { label: "Bogotá, Colombia", value: "bogota", lat: 4.711, lng: -74.0721 },
  { label: "Boston, MA, USA", value: "boston", lat: 42.3601, lng: -71.0589 },
  {
    label: "Brisbane, Australia",
    value: "brisbane",
    lat: -27.4698,
    lng: 153.0251,
  },
  { label: "Brussels, Belgium", value: "brussels", lat: 50.8503, lng: 4.3517 },
  { label: "Budapest, Hungary", value: "budapest", lat: 47.4979, lng: 19.0402 },
  {
    label: "Buenos Aires, Argentina",
    value: "buenos_aires",
    lat: -34.6037,
    lng: -58.3816,
  },
  {
    label: "Burlington, VT, USA",
    value: "burlington_vt",
    lat: 44.4759,
    lng: -73.2121,
  },
  { label: "Busan, South Korea", value: "busan", lat: 35.1796, lng: 129.0756 },
  { label: "Cairo, Egypt", value: "cairo", lat: 30.0444, lng: 31.2357 },
  { label: "Calgary, Canada", value: "calgary", lat: 51.0447, lng: -114.0719 },
  { label: "Cancún, Mexico", value: "cancun", lat: 21.1619, lng: -86.8515 },
  {
    label: "Cape Town, South Africa",
    value: "cape_town",
    lat: -33.9249,
    lng: 18.4241,
  },
  {
    label: "Caracas, Venezuela",
    value: "caracas",
    lat: 10.4806,
    lng: -66.9036,
  },
  {
    label: "Casablanca, Morocco",
    value: "casablanca",
    lat: 33.5731,
    lng: -7.5898,
  },
  {
    label: "Charlotte, NC, USA",
    value: "charlotte",
    lat: 35.2271,
    lng: -80.8431,
  },
  { label: "Chengdu, China", value: "chengdu", lat: 30.5728, lng: 104.0668 },
  { label: "Chennai, India", value: "chennai", lat: 13.0827, lng: 80.2707 },
  { label: "Chicago, IL, USA", value: "chicago", lat: 41.8781, lng: -87.6298 },
  {
    label: "Christchurch, New Zealand",
    value: "christchurch",
    lat: -43.5321,
    lng: 172.6362,
  },
  {
    label: "Cincinnati, OH, USA",
    value: "cincinnati",
    lat: 39.1031,
    lng: -84.512,
  },
  {
    label: "Cleveland, OH, USA",
    value: "cleveland",
    lat: 41.4993,
    lng: -81.6944,
  },
  {
    label: "Copenhagen, Denmark",
    value: "copenhagen",
    lat: 55.6761,
    lng: 12.5683,
  },
  { label: "Dallas, TX, USA", value: "dallas", lat: 32.7767, lng: -96.797 },
  { label: "Delhi, India", value: "delhi", lat: 28.7041, lng: 77.1025 },
  { label: "Denver, CO, USA", value: "denver", lat: 39.7392, lng: -104.9903 },
  { label: "Detroit, MI, USA", value: "detroit", lat: 42.3314, lng: -83.0458 },
  { label: "Doha, Qatar", value: "doha", lat: 25.2854, lng: 51.531 },
  { label: "Dubai, UAE", value: "dubai", lat: 25.2048, lng: 55.2708 },
  { label: "Dublin, Ireland", value: "dublin", lat: 53.3498, lng: -6.2603 },
  {
    label: "Durban, South Africa",
    value: "durban",
    lat: -29.8587,
    lng: 31.0218,
  },
  { label: "Edinburgh, UK", value: "edinburgh", lat: 55.9533, lng: -3.1883 },
  {
    label: "Edmonton, Canada",
    value: "edmonton",
    lat: 53.5461,
    lng: -113.4938,
  },
  {
    label: "Frankfurt, Germany",
    value: "frankfurt",
    lat: 50.1109,
    lng: 8.6821,
  },
  { label: "Geneva, Switzerland", value: "geneva", lat: 46.2044, lng: 6.1432 },
  {
    label: "Guadalajara, Mexico",
    value: "guadalajara",
    lat: 20.6597,
    lng: -103.3496,
  },
  {
    label: "Guangzhou, China",
    value: "guangzhou",
    lat: 23.1291,
    lng: 113.2644,
  },
  { label: "Hamburg, Germany", value: "hamburg", lat: 53.5511, lng: 9.9937 },
  { label: "Hanoi, Vietnam", value: "hanoi", lat: 21.0285, lng: 105.8542 },
  { label: "Helsinki, Finland", value: "helsinki", lat: 60.1699, lng: 24.9384 },
  {
    label: "Ho Chi Minh City, Vietnam",
    value: "ho_chi_minh",
    lat: 10.8231,
    lng: 106.6297,
  },
  { label: "Hong Kong", value: "hong_kong", lat: 22.3193, lng: 114.1694 },
  { label: "Houston, TX, USA", value: "houston", lat: 29.7604, lng: -95.3698 },
  { label: "Hyderabad, India", value: "hyderabad", lat: 17.385, lng: 78.4867 },
  { label: "Istanbul, Turkey", value: "istanbul", lat: 41.0082, lng: 28.9784 },
  {
    label: "Jakarta, Indonesia",
    value: "jakarta",
    lat: -6.2088,
    lng: 106.8456,
  },
  {
    label: "Jerusalem, Israel",
    value: "jerusalem",
    lat: 31.7683,
    lng: 35.2137,
  },
  {
    label: "Johannesburg, South Africa",
    value: "johannesburg",
    lat: -26.2041,
    lng: 28.0473,
  },
  {
    label: "Kansas City, MO, USA",
    value: "kansas_city",
    lat: 39.0997,
    lng: -94.5786,
  },
  { label: "Kolkata, India", value: "kolkata", lat: 22.5726, lng: 88.3639 },
  {
    label: "Kuala Lumpur, Malaysia",
    value: "kuala_lumpur",
    lat: 3.139,
    lng: 101.6869,
  },
  {
    label: "Kuwait City, Kuwait",
    value: "kuwait_city",
    lat: 29.3759,
    lng: 47.9774,
  },
  { label: "Kyoto, Japan", value: "kyoto", lat: 35.0116, lng: 135.7681 },
  { label: "Lagos, Nigeria", value: "lagos", lat: 6.5244, lng: 3.3792 },
  {
    label: "Las Vegas, NV, USA",
    value: "las_vegas",
    lat: 36.1699,
    lng: -115.1398,
  },
  { label: "Lima, Peru", value: "lima", lat: -12.0464, lng: -77.0428 },
  { label: "Lisbon, Portugal", value: "lisbon", lat: 38.7223, lng: -9.1393 },
  { label: "London, UK", value: "london", lat: 51.5074, lng: -0.1278 },
  {
    label: "Los Angeles, CA, USA",
    value: "los_angeles",
    lat: 34.0522,
    lng: -118.2437,
  },
  { label: "Lyon, France", value: "lyon", lat: 45.764, lng: 4.8357 },
  { label: "Madrid, Spain", value: "madrid", lat: 40.4168, lng: -3.7038 },
  { label: "Manchester, UK", value: "manchester", lat: 53.4808, lng: -2.2426 },
  {
    label: "Manila, Philippines",
    value: "manila",
    lat: 14.5995,
    lng: 120.9842,
  },
  { label: "Marseille, France", value: "marseille", lat: 43.2965, lng: 5.3698 },
  {
    label: "Melbourne, Australia",
    value: "melbourne",
    lat: -37.8136,
    lng: 144.9631,
  },
  {
    label: "Mexico City, Mexico",
    value: "mexico_city",
    lat: 19.4326,
    lng: -99.1332,
  },
  { label: "Miami, FL, USA", value: "miami", lat: 25.7617, lng: -80.1918 },
  { label: "Milan, Italy", value: "milan", lat: 45.4642, lng: 9.19 },
  {
    label: "Milwaukee, WI, USA",
    value: "milwaukee",
    lat: 43.0389,
    lng: -87.9065,
  },
  {
    label: "Minneapolis, MN, USA",
    value: "minneapolis",
    lat: 44.9778,
    lng: -93.265,
  },
  {
    label: "Monterrey, Mexico",
    value: "monterrey",
    lat: 25.6866,
    lng: -100.3161,
  },
  { label: "Montreal, Canada", value: "montreal", lat: 45.5017, lng: -73.5673 },
  { label: "Moscow, Russia", value: "moscow", lat: 55.7558, lng: 37.6173 },
  { label: "Mumbai, India", value: "mumbai", lat: 19.076, lng: 72.8777 },
  { label: "Munich, Germany", value: "munich", lat: 48.1351, lng: 11.582 },
  { label: "Nagoya, Japan", value: "nagoya", lat: 35.1815, lng: 136.9066 },
  { label: "Nairobi, Kenya", value: "nairobi", lat: -1.2921, lng: 36.8219 },
  { label: "Naples, Italy", value: "naples", lat: 40.8518, lng: 14.2681 },
  {
    label: "Nashville, TN, USA",
    value: "nashville",
    lat: 36.1627,
    lng: -86.7816,
  },
  { label: "New York, NY, USA", value: "new_york", lat: 40.7128, lng: -74.006 },
  { label: "Orlando, FL, USA", value: "orlando", lat: 28.5383, lng: -81.3792 },
  { label: "Osaka, Japan", value: "osaka", lat: 34.6937, lng: 135.5023 },
  { label: "Oslo, Norway", value: "oslo", lat: 59.9139, lng: 10.7522 },
  { label: "Ottawa, Canada", value: "ottawa", lat: 45.4215, lng: -75.6972 },
  { label: "Paris, France", value: "paris", lat: 48.8566, lng: 2.3522 },
  { label: "Perth, Australia", value: "perth", lat: -31.9505, lng: 115.8605 },
  {
    label: "Philadelphia, PA, USA",
    value: "philadelphia",
    lat: 39.9526,
    lng: -75.1652,
  },
  { label: "Phoenix, AZ, USA", value: "phoenix", lat: 33.4484, lng: -112.074 },
  {
    label: "Pittsburgh, PA, USA",
    value: "pittsburgh",
    lat: 40.4406,
    lng: -79.9959,
  },
  {
    label: "Portland, ME, USA",
    value: "portland_me",
    lat: 43.6591,
    lng: -70.2568,
  },
  {
    label: "Portland, OR, USA",
    value: "portland_or",
    lat: 45.5152,
    lng: -122.6784,
  },
  { label: "Porto, Portugal", value: "porto", lat: 41.1579, lng: -8.6291 },
  {
    label: "Prague, Czech Republic",
    value: "prague",
    lat: 50.0755,
    lng: 14.4378,
  },
  {
    label: "Providence, RI, USA",
    value: "providence",
    lat: 41.824,
    lng: -71.4128,
  },
  { label: "Pune, India", value: "pune", lat: 18.5204, lng: 73.8567 },
  {
    label: "Quebec City, Canada",
    value: "quebec_city",
    lat: 46.8139,
    lng: -71.208,
  },
  {
    label: "Reykjavik, Iceland",
    value: "reykjavik",
    lat: 64.1466,
    lng: -21.9426,
  },
  {
    label: "Rio de Janeiro, Brazil",
    value: "rio",
    lat: -22.9068,
    lng: -43.1729,
  },
  {
    label: "Riyadh, Saudi Arabia",
    value: "riyadh",
    lat: 24.7136,
    lng: 46.6753,
  },
  { label: "Rome, Italy", value: "rome", lat: 41.9028, lng: 12.4964 },
  {
    label: "Salt Lake City, UT, USA",
    value: "salt_lake_city",
    lat: 40.7608,
    lng: -111.891,
  },
  {
    label: "San Antonio, TX, USA",
    value: "san_antonio",
    lat: 29.4241,
    lng: -98.4936,
  },
  {
    label: "San Diego, CA, USA",
    value: "san_diego",
    lat: 32.7157,
    lng: -117.1611,
  },
  {
    label: "San Francisco, CA, USA",
    value: "san_francisco",
    lat: 37.7749,
    lng: -122.4194,
  },
  { label: "Santiago, Chile", value: "santiago", lat: -33.4489, lng: -70.6693 },
  {
    label: "São Paulo, Brazil",
    value: "sao_paulo",
    lat: -23.5505,
    lng: -46.6333,
  },
  { label: "Seattle, WA, USA", value: "seattle", lat: 47.6062, lng: -122.3321 },
  { label: "Seoul, South Korea", value: "seoul", lat: 37.5665, lng: 126.978 },
  { label: "Shanghai, China", value: "shanghai", lat: 31.2304, lng: 121.4737 },
  { label: "Shenzhen, China", value: "shenzhen", lat: 22.5431, lng: 114.0579 },
  { label: "Singapore", value: "singapore", lat: 1.3521, lng: 103.8198 },
  {
    label: "South Haven, MI, USA",
    value: "south_haven",
    lat: 42.4031,
    lng: -86.2736,
  },
  {
    label: "St. Louis, MO, USA",
    value: "st_louis",
    lat: 38.627,
    lng: -90.1994,
  },
  {
    label: "St. Petersburg, Russia",
    value: "st_petersburg",
    lat: 59.9311,
    lng: 30.3609,
  },
  {
    label: "Stockholm, Sweden",
    value: "stockholm",
    lat: 59.3293,
    lng: 18.0686,
  },
  { label: "Sydney, Australia", value: "sydney", lat: -33.8688, lng: 151.2093 },
  { label: "Taipei, Taiwan", value: "taipei", lat: 25.033, lng: 121.5654 },
  { label: "Tampa, FL, USA", value: "tampa", lat: 27.9506, lng: -82.4572 },
  { label: "Tel Aviv, Israel", value: "tel_aviv", lat: 32.0853, lng: 34.7818 },
  { label: "Tokyo, Japan", value: "tokyo", lat: 35.6762, lng: 139.6503 },
  { label: "Toronto, Canada", value: "toronto", lat: 43.6532, lng: -79.3832 },
  { label: "Tunis, Tunisia", value: "tunis", lat: 36.8065, lng: 10.1815 },
  { label: "Valencia, Spain", value: "valencia", lat: 39.4699, lng: -0.3763 },
  {
    label: "Vancouver, Canada",
    value: "vancouver",
    lat: 49.2827,
    lng: -123.1207,
  },
  { label: "Vienna, Austria", value: "vienna", lat: 48.2082, lng: 16.3738 },
  { label: "Warsaw, Poland", value: "warsaw", lat: 52.2297, lng: 21.0122 },
  {
    label: "Wellington, New Zealand",
    value: "wellington",
    lat: -41.2865,
    lng: 174.7762,
  },
  { label: "Winnipeg, Canada", value: "winnipeg", lat: 49.8951, lng: -97.1384 },
  { label: "Yokohama, Japan", value: "yokohama", lat: 35.4437, lng: 139.638 },
  { label: "Zurich, Switzerland", value: "zurich", lat: 47.3769, lng: 8.5417 },
]);

// Selected city
const selectedCity = ref(null);

// Custom events management
const showEventsDialog = ref(false);
const showEmojiPicker = ref(false);
const customEvents = ref([]);
const showEventForm = ref(false); // Toggle between list and form view
// Initialize new event with January 1st of the calendar year
const getDefaultEventDate = () => new Date(config.value.year, 0, 1); // January 1st

const newEvent = ref({
  date: getDefaultEventDate(),
  emoji: "",
  title: "",
  showTitle: false,
  id: null,
  displaySettings: {},
});

// Editing state
const isEditingEvent = ref(false);
const editingEventIndex = ref(null);

// Holiday sets
const selectedHolidaySet = ref(null);
const holidaySetOptions = computed(() => {
  // Different holiday sets based on calendar type
  if (config.value.calendarType === "hebrew") {
    return [
      { label: "Hebrew Religious Holidays", value: "hewbrew_religious" },
      { label: "No Holidays", value: "none" },
    ];
  } else {
    return [
      // Regional/National
      { label: "US Federal Holidays", value: "us" },
      { label: "Canadian Holidays", value: "ca" },
      { label: "UK Bank Holidays", value: "uk" },
      { label: "Mexican Holidays", value: "mexican" },
      // Religious
      { label: "Jewish Holidays", value: "jewish" },
      { label: "Christian Holidays", value: "christian" },
      { label: "Islamic Holidays", value: "islamic" },
      { label: "Hindu Holidays", value: "hindu" },
      { label: "Pagan/Wiccan (Wheel of the Year)", value: "pagan" },
      // Cultural
      { label: "Chinese/Lunar Holidays", value: "chinese" },
      { label: "Fun & Secular Holidays", value: "secular" },
      // None
      { label: "No Holidays", value: "none" },
    ];
  }
});

// Holiday data for different sets
const holidayData = {
  us: [
    { date: "01-01", title: "New Year's Day", emoji: "🎊" },
    { date: "01-15", title: "Martin Luther King Jr. Day", emoji: "🕊️" },
    { date: "02-14", title: "Valentine's Day", emoji: "❤️" },
    { date: "02-19", title: "Presidents' Day", emoji: "🎩" },
    { date: "03-17", title: "St. Patrick's Day", emoji: "☘️" },
    { date: "04-01", title: "April Fool's Day", emoji: "🤡" },
    { date: "05-27", title: "Memorial Day", emoji: "🇺🇸" },
    { date: "06-19", title: "Juneteenth", emoji: "🤎" },
    { date: "07-04", title: "Independence Day", emoji: "🇺🇸" },
    { date: "09-02", title: "Labor Day", emoji: "👷" },
    { date: "10-31", title: "Halloween", emoji: "🎃" },
    { date: "11-11", title: "Veterans Day", emoji: "🎖️" },
    { date: "11-28", title: "Thanksgiving", emoji: "🦃" },
    { date: "12-25", title: "Christmas", emoji: "🎄" },
  ],
  ca: [
    { date: "01-01", title: "New Year's Day", emoji: "🎊" },
    { date: "02-19", title: "Family Day", emoji: "👨‍👩‍👧‍👦" },
    { date: "03-17", title: "St. Patrick's Day", emoji: "☘️" },
    { date: "04-01", title: "Good Friday", emoji: "✝️" },
    { date: "05-20", title: "Victoria Day", emoji: "👑" },
    { date: "07-01", title: "Canada Day", emoji: "🇨🇦" },
    { date: "08-05", title: "Civic Holiday", emoji: "🏛️" },
    { date: "09-02", title: "Labour Day", emoji: "👷" },
    { date: "10-14", title: "Thanksgiving", emoji: "🦃" },
    { date: "11-11", title: "Remembrance Day", emoji: "🌺" },
    { date: "12-25", title: "Christmas", emoji: "🎄" },
    { date: "12-26", title: "Boxing Day", emoji: "📦" },
  ],
  uk: [
    { date: "01-01", title: "New Year's Day", emoji: "🎊" },
    { date: "03-17", title: "St. Patrick's Day", emoji: "☘️" },
    { date: "04-01", title: "Good Friday", emoji: "✝️" },
    { date: "04-02", title: "Easter Monday", emoji: "🐰" },
    { date: "05-06", title: "Early May Bank Holiday", emoji: "🌸" },
    { date: "05-27", title: "Spring Bank Holiday", emoji: "🌺" },
    { date: "08-26", title: "Summer Bank Holiday", emoji: "☀️" },
    { date: "11-05", title: "Guy Fawkes Night", emoji: "🎆" },
    { date: "12-25", title: "Christmas Day", emoji: "🎄" },
    { date: "12-26", title: "Boxing Day", emoji: "📦" },
  ],
  mx: [
    { date: "01-01", title: "Año Nuevo", emoji: "🎊" },
    { date: "02-05", title: "Día de la Constitución", emoji: "📜" },
    { date: "03-21", title: "Natalicio de Benito Juárez", emoji: "🎩" },
    { date: "05-01", title: "Día del Trabajo", emoji: "👷" },
    { date: "05-05", title: "Cinco de Mayo", emoji: "🇲🇽" },
    { date: "09-16", title: "Día de la Independencia", emoji: "🇲🇽" },
    { date: "11-01", title: "Día de Muertos", emoji: "💀" },
    { date: "11-20", title: "Revolución Mexicana", emoji: "⚔️" },
    { date: "12-12", title: "Día de la Virgen de Guadalupe", emoji: "🙏" },
    { date: "12-25", title: "Navidad", emoji: "🎄" },
  ],
  cn: [
    { date: "01-01", title: "New Year's Day", emoji: "🎊" },
    { date: "01-22", title: "Chinese New Year", emoji: "🐲" },
    { date: "04-04", title: "Qingming Festival", emoji: "🌸" },
    { date: "05-01", title: "Labor Day", emoji: "👷" },
    { date: "06-07", title: "Dragon Boat Festival", emoji: "🚣" },
    { date: "09-13", title: "Mid-Autumn Festival", emoji: "🥮" },
    { date: "10-01", title: "National Day", emoji: "🇨🇳" },
  ],
  in: [
    { date: "01-26", title: "Republic Day", emoji: "🇮🇳" },
    { date: "03-08", title: "Holi", emoji: "🎨" },
    { date: "08-15", title: "Independence Day", emoji: "🇮🇳" },
    { date: "10-02", title: "Gandhi Jayanti", emoji: "👓" },
    { date: "10-24", title: "Diwali", emoji: "🪔" },
  ],
  international: [
    { date: "03-08", title: "International Women's Day", emoji: "👩" },
    { date: "04-22", title: "Earth Day", emoji: "🌍" },
    { date: "05-01", title: "International Workers Day", emoji: "👷" },
    { date: "06-05", title: "World Environment Day", emoji: "🌱" },
    { date: "10-16", title: "World Food Day", emoji: "🍽️" },
    { date: "12-10", title: "Human Rights Day", emoji: "⚖️" },
  ],
  jewish: [
    { date: "01-06", title: "Tu BiShvat", emoji: "🌳" },
    { date: "03-06", title: "Purim", emoji: "🎭" },
    { date: "04-15", title: "Passover", emoji: "🍷" },
    { date: "05-05", title: "Lag BaOmer", emoji: "🔥" },
    { date: "05-26", title: "Shavuot", emoji: "📜" },
    { date: "09-15", title: "Rosh Hashanah", emoji: "🍎" },
    { date: "09-24", title: "Yom Kippur", emoji: "📖" },
    { date: "09-29", title: "Sukkot", emoji: "🌿" },
    { date: "10-07", title: "Simchat Torah", emoji: "📜" },
    { date: "12-10", title: "Hanukkah", emoji: "🕎" },
  ],
  hewbrew_religious: [
    // Hebrew calendar holidays (using Hebrew month-day format)
    // Note: These are Hebrew dates, not Gregorian dates
    // They need special handling for the Hebrew calendar
    { hebrewDate: "1-1", title: "Rosh Hashanah (Day 1)", emoji: "🍎" },
    { hebrewDate: "1-2", title: "Rosh Hashanah (Day 2)", emoji: "🍏" },
    { hebrewDate: "1-10", title: "Yom Kippur", emoji: "📖" },
    { hebrewDate: "1-15", title: "Sukkot (Day 1)", emoji: "🌿" },
    { hebrewDate: "1-22", title: "Shemini Atzeret", emoji: "🎊" },
    { hebrewDate: "1-23", title: "Simchat Torah", emoji: "📜" },
    { hebrewDate: "3-25", title: "Chanukah (Day 1)", emoji: "🕎" },
    { hebrewDate: "5-15", title: "Tu BiShvat", emoji: "🌳" },
    { hebrewDate: "6-14", title: "Purim", emoji: "🎭" },
    { hebrewDate: "7-15", title: "Passover (Day 1)", emoji: "🍷" },
    { hebrewDate: "8-18", title: "Lag BaOmer", emoji: "🔥" },
    { hebrewDate: "9-6", title: "Shavuot", emoji: "📜" },
    { hebrewDate: "11-9", title: "Tisha B'Av", emoji: "😢" },
  ],
  christian: [
    { date: "01-06", title: "Epiphany", emoji: "⭐" },
    { date: "02-14", title: "St. Valentine's Day", emoji: "❤️" },
    { date: "03-01", title: "Ash Wednesday", emoji: "✝️" },
    { date: "03-17", title: "St. Patrick's Day", emoji: "☘️" },
    { date: "04-02", title: "Palm Sunday", emoji: "🌴" },
    { date: "04-07", title: "Good Friday", emoji: "✝️" },
    { date: "04-09", title: "Easter Sunday", emoji: "🐣" },
    { date: "05-18", title: "Ascension Day", emoji: "☁️" },
    { date: "05-28", title: "Pentecost", emoji: "🔥" },
    { date: "11-01", title: "All Saints' Day", emoji: "😇" },
    { date: "12-24", title: "Christmas Eve", emoji: "🎄" },
    { date: "12-25", title: "Christmas Day", emoji: "🎅" },
  ],
  islamic: [
    { date: "01-10", title: "Muharram", emoji: "☪️" },
    { date: "03-11", title: "Mawlid al-Nabi", emoji: "🌙" },
    { date: "04-13", title: "Ramadan Begins", emoji: "🌙" },
    { date: "04-27", title: "Laylat al-Qadr", emoji: "✨" },
    { date: "05-13", title: "Eid al-Fitr", emoji: "🎊" },
    { date: "07-20", title: "Eid al-Adha", emoji: "🐑" },
    { date: "08-19", title: "Islamic New Year", emoji: "📅" },
    { date: "10-28", title: "Prophet's Birthday", emoji: "🕌" },
  ],
  pagan: [
    { date: "02-01", title: "Imbolc", emoji: "🕯️" },
    { date: "03-20", title: "Ostara (Spring Equinox)", emoji: "🐣" },
    { date: "05-01", title: "Beltane", emoji: "🔥" },
    { date: "06-21", title: "Litha (Summer Solstice)", emoji: "☀️" },
    { date: "08-01", title: "Lughnasadh", emoji: "🌾" },
    { date: "09-22", title: "Mabon (Autumn Equinox)", emoji: "🍂" },
    { date: "10-31", title: "Samhain", emoji: "🎃" },
    { date: "12-21", title: "Yule (Winter Solstice)", emoji: "🌲" },
  ],
  secular: [
    { date: "02-02", title: "Groundhog Day", emoji: "🦫" },
    { date: "02-14", title: "Valentine's Day", emoji: "❤️" },
    { date: "03-17", title: "St. Patrick's Day", emoji: "☘️" },
    { date: "04-01", title: "April Fools' Day", emoji: "🃏" },
    { date: "04-22", title: "Earth Day", emoji: "🌍" },
    { date: "05-05", title: "Cinco de Mayo", emoji: "🌮" },
    { date: "05-12", title: "Mother's Day", emoji: "💐" },
    { date: "06-01", title: "Pride Month", emoji: "🏳️‍🌈" },
    { date: "06-15", title: "Father's Day", emoji: "👔" },
    { date: "10-31", title: "Halloween", emoji: "🎃" },
    { date: "11-29", title: "Black Friday", emoji: "🛒" },
    { date: "12-26", title: "Kwanzaa", emoji: "🕯️" },
    { date: "12-31", title: "New Year's Eve", emoji: "🎆" },
  ],
  mexican: [
    { date: "01-06", title: "Día de los Reyes", emoji: "👑" },
    { date: "02-05", title: "Día de la Constitución", emoji: "📜" },
    { date: "03-21", title: "Natalicio de Benito Juárez", emoji: "⚖️" },
    { date: "05-05", title: "Cinco de Mayo", emoji: "🇲🇽" },
    { date: "09-16", title: "Día de la Independencia", emoji: "🎉" },
    { date: "11-01", title: "Día de los Muertos", emoji: "💀" },
    { date: "11-20", title: "Día de la Revolución", emoji: "🎖️" },
    { date: "12-12", title: "Virgen de Guadalupe", emoji: "🌹" },
    { date: "12-16", title: "Las Posadas", emoji: "🕯️" },
  ],
  hindu: [
    { date: "01-14", title: "Makar Sankranti", emoji: "🪁" },
    { date: "03-14", title: "Holi", emoji: "🎨" },
    { date: "04-17", title: "Ram Navami", emoji: "🏹" },
    { date: "08-26", title: "Janmashtami", emoji: "🪈" },
    { date: "09-07", title: "Ganesh Chaturthi", emoji: "🐘" },
    { date: "10-03", title: "Navaratri", emoji: "💃" },
    { date: "10-12", title: "Dussehra", emoji: "🏹" },
    { date: "11-01", title: "Diwali", emoji: "🪔" },
  ],
  chinese: [
    { date: "01-29", title: "Chinese New Year", emoji: "🧧" },
    { date: "02-12", title: "Lantern Festival", emoji: "🏮" },
    { date: "04-04", title: "Qingming Festival", emoji: "🪦" },
    { date: "06-10", title: "Dragon Boat Festival", emoji: "🐉" },
    { date: "09-17", title: "Mid-Autumn Festival", emoji: "🥮" },
    { date: "10-11", title: "Double Ninth Festival", emoji: "🏔️" },
  ],
};

// Templates (Admin)
const isAdmin = computed(() => authStore.isAdmin);
const showTemplatesDialog = ref(false);
const templates = ref([]);
const loadingTemplates = ref(false);
const currentTemplateId = ref(null);

// Sorted templates for user selection (by displayOrder, featured first)
const sortedAllTemplates = computed(() => {
  return [...allTemplates.value].sort((a, b) => {
    // Featured templates first
    if (a.isFeatured && !b.isFeatured) return -1;
    if (!a.isFeatured && b.isFeatured) return 1;
    // Then by displayOrder
    return a.displayOrder - b.displayOrder;
  });
});

// Template save dialog
const showSaveTemplateDialog = ref(false);
const templateToSave = ref({
  name: "",
  description: "",
  isActive: true,
  isFeatured: false,
  displayOrder: 0,
});

// Template duplicate dialog
const showDuplicateDialog = ref(false);

// Saved Calendars Dialog
const showSavedCalendarsDialog = ref(false);
const savedCalendars = ref([]);
const loadingSavedCalendars = ref(false);
const selectedSavedCalendar = ref(null);
const editingCalendarName = ref("");
const savingNewCalendar = ref(false);
const templateToDuplicate = ref(null);
const calendarPreviews = ref({});
const duplicateName = ref("");

// Calendar state
const generatedSVG = ref("");
const svgKey = ref(0); // Key to force re-render of SVG
const generating = ref(false);
const holidays = ref(new Set());
const lastFetchedHolidayYear = ref(null); // Track which year's holidays we have cached

// ===========================================
// PRINT DIMENSIONS (must match backend CalendarRenderingService)
// ===========================================
const PAGE_WIDTH_INCHES = 35;
const PAGE_HEIGHT_INCHES = 23;
const MARGIN_INCHES = 0.5; // Change this to adjust margins (e.g., 1.0 for 1 inch)
const PRINTABLE_WIDTH_INCHES = PAGE_WIDTH_INCHES - 2 * MARGIN_INCHES; // 34"
const PRINTABLE_HEIGHT_INCHES = PAGE_HEIGHT_INCHES - 2 * MARGIN_INCHES; // 22"

/**
 * Wraps the SVG with margins so content fits within the printable area.
 * The outer SVG represents the full page, and the inner content is scaled
 * and positioned within the margins.
 */
const wrapSvgWithMargins = (innerSvg: string): string => {
  // Extract viewBox from inner SVG
  const viewBoxMatch = innerSvg.match(
    /viewBox="([\d.]+)\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)"/,
  );
  if (!viewBoxMatch) {
    console.warn("No viewBox found in SVG, returning without margins");
    return innerSvg;
  }

  const innerWidth = parseFloat(viewBoxMatch[3]);
  const innerHeight = parseFloat(viewBoxMatch[4]);

  // Use a coordinate system scaled by 100 for precision
  const pageWidth = PAGE_WIDTH_INCHES * 100;
  const pageHeight = PAGE_HEIGHT_INCHES * 100;
  const marginSize = MARGIN_INCHES * 100;
  const printableWidth = PRINTABLE_WIDTH_INCHES * 100;
  const printableHeight = PRINTABLE_HEIGHT_INCHES * 100;

  // Calculate scale to fit inner SVG within printable area
  const scaleX = printableWidth / innerWidth;
  const scaleY = printableHeight / innerHeight;
  const scale = Math.min(scaleX, scaleY);

  // Calculate actual size of scaled content
  const scaledWidth = innerWidth * scale;
  const scaledHeight = innerHeight * scale;

  // Calculate offset - center horizontally, align to top vertically
  const offsetX = marginSize + (printableWidth - scaledWidth) / 2;
  const offsetY = marginSize; // Top-aligned, extra space goes to bottom

  // Strip outer SVG tags and XML declaration from inner SVG
  const innerContent = innerSvg
    .replace(/<\?xml[^?]*\?>\s*/g, "")
    .replace(/<svg[^>]*>/, "")
    .replace(/<\/svg>\s*$/, "");

  // Build wrapper SVG
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${pageWidth}" height="${pageHeight}" viewBox="0 0 ${pageWidth} ${pageHeight}" preserveAspectRatio="xMidYMid meet">
  <rect width="${pageWidth}" height="${pageHeight}" fill="white"/>
  <g transform="translate(${offsetX.toFixed(2)}, ${offsetY.toFixed(2)}) scale(${scale.toFixed(6)})">
    ${innerContent}
  </g>
</svg>`;
};

// Zoom state (calculated to fit window width on initial load)
const zoomLevel = ref(0.5);
// Rulers visibility - synced with store for header access
const showRulers = computed({
  get: () => calendarEditorStore.showRulers,
  set: (value) => {
    if (value !== calendarEditorStore.showRulers) {
      calendarEditorStore.toggleRulers();
    }
  },
});
const previewContainer = ref(null);
const isInitialGeneration = ref(true);

// Emoji picker instance
let emojiPicker = null;

// Auto-generate on mount
onMounted(async () => {
  // Activate the calendar editor store and register callbacks
  calendarEditorStore.activate();
  calendarEditorStore.setCalendarYear(config.value.year);
  calendarEditorStore.registerZoomCallbacks(zoomIn, zoomOut, resetZoom);
  calendarEditorStore.registerSaveAsTemplateCallback(saveAsTemplate);

  // Fetch user data to ensure store is populated
  await userStore.fetchCurrentUser();

  // Load holidays (but don't show them by default)
  await fetchHolidays();

  // Load calendar or template from URL if specified
  const calendarLoaded = await loadCalendarFromUrl();

  // If nothing was loaded from URL, initialize a new calendar with backend defaults
  if (!calendarLoaded) {
    await initializeNewCalendar();
  }

  // Check if wizard should open from query param
  if (route.query.wizard === "true") {
    showCreateWizard.value = true;
    // Clear the query param without adding to history
    router.replace({ query: { ...route.query, wizard: undefined } });
  }

  // Check if manage templates dialog should open from query param (admin only)
  if (route.query["manage-templates"] === "true" && isAdmin.value) {
    await loadAllTemplates(); // Load templates only when admin opens dialog
    showTemplatesDialog.value = true;
    router.replace({
      query: { ...route.query, "manage-templates": undefined },
    });
  }
});

// Clean up store when component unmounts
onUnmounted(() => {
  calendarEditorStore.deactivate();
});

// Watch for wizard query param changes (for when already on page)
watch(
  () => route.query.wizard,
  (wizard) => {
    if (wizard === "true") {
      showCreateWizard.value = true;
      router.replace({ query: { ...route.query, wizard: undefined } });
    }
  },
);

// Watch for manage-templates query param changes (for when already on page)
watch(
  () => route.query["manage-templates"],
  async (manageTemplates) => {
    if (manageTemplates === "true" && isAdmin.value) {
      await loadAllTemplates(); // Load templates only when admin opens dialog
      showTemplatesDialog.value = true;
      router.replace({
        query: { ...route.query, "manage-templates": undefined },
      });
    }
  },
);

// Update event date when calendar year changes
watch(
  () => config.value.year,
  (newYear) => {
    // Update the new event date to Jan 1st of the new year if it's currently empty or from a different year
    if (
      !newEvent.value.date ||
      new Date(newEvent.value.date).getFullYear() !== newYear
    ) {
      newEvent.value.date = new Date(newYear, 0, 1);
    }
    // Sync with calendar editor store
    calendarEditorStore.setCalendarYear(newYear);
  },
);

// Sync generatedSVG state with store for header button visibility
watch(
  () => generatedSVG.value,
  (svg) => {
    calendarEditorStore.setHasGeneratedSVG(!!svg);
  },
);

// Sync zoom level with store
watch(
  () => zoomLevel.value,
  (level) => {
    calendarEditorStore.setZoomLevel(level);
  },
);

// Initialize emoji picker when dialog opens
watch(showEmojiPicker, async (visible) => {
  if (visible) {
    await nextTick();
    const container = document.getElementById("emoji-picker-container");
    if (container && !emojiPicker) {
      // Dynamically import and create emoji picker
      const { Picker } = await import("emoji-picker-element");
      emojiPicker = new Picker({
        locale: "en",
        dataSource:
          "https://cdn.jsdelivr.net/npm/emoji-picker-element-data@^1/en/emojibase/data.json",
        skinToneEmoji: "🖐️",
      });

      emojiPicker.addEventListener("emoji-click", (event) => {
        newEvent.value.emoji = event.detail.unicode;
        showEmojiPicker.value = false;
      });

      container.appendChild(emojiPicker);
    }
  }
});

// Watch for calendar type changes to adjust the year and settings
watch(
  () => config.value.calendarType,
  (newType, oldType) => {
    if (newType !== oldType) {
      if (newType === "hebrew") {
        // Convert to Hebrew year
        config.value.year = currentHebrewYear.value;
        config.value.layoutStyle = "grid"; // Always use grid for Hebrew
        config.value.moonSize = 20;
        // Set default holiday set for Hebrew calendar if not already set
        if (
          !selectedHolidaySet.value ||
          selectedHolidaySet.value === "US" ||
          selectedHolidaySet.value === "JEWISH"
        ) {
          selectedHolidaySet.value = "HEBREW_RELIGIOUS";
        }
      } else {
        // Convert back to Gregorian year
        config.value.year = defaultYear;
        // Set default holiday set for Gregorian calendar if coming from Hebrew
        if (selectedHolidaySet.value === "HEBREW_RELIGIOUS") {
          selectedHolidaySet.value = "US";
        }
      }
    }
  },
);

// Note: No separate regeneration watcher needed - autoSaveCalendar() regenerates SVG on backend
// and returns it in the response, eliminating the need for separate generate-json calls

// Fetch holidays for the selected year (only if year changed)
const fetchHolidays = async () => {
  const currentYear = config.value.year;
  // Skip fetch if we already have this year's holidays
  if (lastFetchedHolidayYear.value === currentYear) {
    return;
  }
  try {
    const response = await fetch(
      `/api/calendar/holidays?year=${currentYear}&country=US`,
    );
    if (response.ok) {
      const data = await response.json();
      holidays.value = new Set(data.holidays);
      lastFetchedHolidayYear.value = currentYear;
    }
  } catch (error) {
    console.error("Error fetching holidays:", error);
  }
};

// Generate calendar
const generateCalendar = async () => {
  try {
    generating.value = true;

    // Fetch holidays if year changed
    await fetchHolidays();

    // Build custom dates map from events
    const customDatesMap = {};
    const eventTitles = {};

    customEvents.value.forEach((event) => {
      if (event.date) {
        const dateStr = formatDate(event.date);
        // Store emoji with display settings
        customDatesMap[dateStr] = {
          emoji: event.emoji || "📅",
          displaySettings: event.displaySettings || {},
        };
        if (event.showTitle && event.title) {
          eventTitles[dateStr] = event.title;
        }
      }
    });

    // Build request
    const request = {
      calendarType: config.value.calendarType,
      year: config.value.year,
      theme: config.value.theme,
      layoutStyle: config.value.layoutStyle,
      showMoonPhases: config.value.moonDisplayMode === "phases",
      showMoonIllumination: config.value.moonDisplayMode === "illumination",
      showFullMoonOnly: config.value.moonDisplayMode === "full-only",
      showWeekNumbers: config.value.showWeekNumbers,
      showDayNames: config.value.showDayNames,
      showDayNumbers: config.value.showDayNumbers,
      showGrid: config.value.showGrid,
      highlightWeekends: config.value.highlightWeekends,
      rotateMonthNames: config.value.rotateMonthNames,
      compactMode: config.value.compactMode,
      firstDayOfWeek: config.value.firstDayOfWeek,
      latitude: config.value.latitude,
      longitude: config.value.longitude,
      moonSize: config.value.moonSize,
      moonOffsetX: config.value.moonOffsetX,
      moonOffsetY: config.value.moonOffsetY,
      moonBorderColor: config.value.moonBorderColor,
      moonBorderWidth: config.value.moonBorderWidth,
      yearColor: config.value.yearColor,
      monthColor: config.value.monthColor,
      dayTextColor: config.value.dayTextColor,
      dayNameColor: config.value.dayNameColor,
      gridLineColor: config.value.gridLineColor,
      weekendBgColor: config.value.weekendBgColor,
      holidayColor: config.value.holidayColor,
      customDateColor: config.value.customDateColor,
      moonDarkColor: config.value.moonDarkColor,
      moonLightColor: config.value.moonLightColor,
      emojiPosition: config.value.emojiPosition,
      customDates: customDatesMap,
      eventTitles: eventTitles,
      holidaySet: selectedHolidaySet.value,
      holidaySets: config.value.holidaySets,
      eventDisplayMode: config.value.eventDisplayMode,
      emojiFont: config.value.emojiFont,
      showHolidays:
        selectedHolidaySet.value && selectedHolidaySet.value !== "none",
      locale: "en-US",
    };

    // Generate calendar
    const response = await fetch("/api/calendar/generate-json", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    });

    if (response.ok) {
      const data = await response.json();
      // Wrap SVG with margins so content fits within printable area
      generatedSVG.value = wrapSvgWithMargins(data.svg);
      // Increment key to force Vue to re-render the SVG element
      svgKey.value++;
      // Only reset zoom on initial generation, preserve zoom/pan on updates
      if (isInitialGeneration.value) {
        resetZoom();
        isInitialGeneration.value = false;
      }
    } else {
      throw new Error("Failed to generate calendar");
    }
  } catch (error) {
    console.error("Error generating calendar:", error);
    toast.add({
      severity: "error",
      summary: "Generation Failed",
      detail: "Failed to generate calendar. Please try again.",
      life: 5000,
    });
  } finally {
    generating.value = false;
  }
};

/**
 * Initialize a new session calendar with default configuration.
 * Calls /api/session-calendar/new which returns pre-generated SVG and config.
 * This replaces the separate generateCalendar() + autoSaveCalendar() calls on mount.
 */
const initializeNewCalendar = async () => {
  generating.value = true;
  try {
    const response = await sessionFetch("/api/session-calendar/new", {
      method: "POST",
    });

    if (response.ok) {
      const data = await response.json();

      // Set the calendar ID
      currentCalendarId.value = data.id;

      // Apply configuration from backend
      if (data.configuration) {
        Object.assign(config.value, data.configuration);

        // Load custom events if any
        if (data.configuration.customDates) {
          customEvents.value = Object.entries(
            data.configuration.customDates,
          ).map(([date, eventData]) => {
            const isNewFormat =
              typeof eventData === "object" && eventData !== null;
            return {
              date: new Date(date),
              emoji: isNewFormat ? eventData.emoji : eventData,
              title: data.configuration.eventTitles?.[date] || "",
              showTitle: !!data.configuration.eventTitles?.[date],
              displaySettings: isNewFormat ? eventData.displaySettings : {},
              id: Date.now() + Math.random(),
            };
          });
        }

        // Update calendar editor store with the year
        calendarEditorStore.setCalendarYear(config.value.year);
      }

      // Use pre-generated SVG from backend
      if (data.svg) {
        generatedSVG.value = wrapSvgWithMargins(data.svg);
        svgKey.value++;
        resetZoom();
      }

      // Mark initialization complete - watchers use flush: 'sync' so they've already
      // checked isInitialGeneration synchronously and returned early
      isInitialGeneration.value = false;
    } else {
      throw new Error("Failed to initialize calendar");
    }
  } catch (error) {
    console.error("Error initializing calendar:", error);
    toast.add({
      severity: "error",
      summary: "Initialization Failed",
      detail: "Failed to initialize calendar. Please refresh the page.",
      life: 5000,
    });
  } finally {
    generating.value = false;
  }
};

const formatDate = (date) => {
  const d = new Date(date);
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
};

const formatEventDate = (date) => {
  if (!date) return "";
  const d = new Date(date);
  return d.toLocaleDateString("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
};

const clearNewEvent = () => {
  newEvent.value = {
    date: getDefaultEventDate(), // Reset to Jan 1st of calendar year
    emoji: "",
    title: "",
    showTitle: false,
    id: null,
    displaySettings: {},
  };
  isEditingEvent.value = false;
  editingEventIndex.value = null;
  showEventForm.value = false; // Return to list view
};

// Edit an existing custom event
const editCustomEvent = (event) => {
  const index = customEvents.value.findIndex((e) => e.id === event.id);
  if (index !== -1) {
    // Create a deep copy of the event for editing
    newEvent.value = {
      date: new Date(event.date),
      emoji: event.emoji,
      title: event.title,
      showTitle: event.showTitle,
      id: event.id,
      displaySettings: { ...(event.displaySettings || {}) },
    };
    isEditingEvent.value = true;
    editingEventIndex.value = index;
    showEventForm.value = true; // Switch to form view
  }
};

// Open add event form
const openAddEventForm = () => {
  clearNewEvent();
  newEvent.value.date = getDefaultEventDate();
  showEventForm.value = true;
};

// Update display settings for new event
const updateEventDisplaySettings = (settings) => {
  newEvent.value.displaySettings = settings;
};

const addCustomEvent = () => {
  if (!newEvent.value.date || !newEvent.value.emoji) {
    toast.add({
      severity: "warn",
      summary: "Missing Information",
      detail: "Please select a date and emoji.",
      life: 3000,
    });
    return;
  }

  if (isEditingEvent.value && editingEventIndex.value !== null) {
    // Update existing event
    customEvents.value[editingEventIndex.value] = {
      ...newEvent.value,
      displaySettings: newEvent.value.displaySettings || {},
      id: newEvent.value.id, // Keep the existing ID
    };

    toast.add({
      severity: "success",
      summary: "Event Updated",
      detail: "Custom event has been updated.",
      life: 3000,
    });
  } else {
    // Add new event
    const event = {
      ...newEvent.value,
      displaySettings: newEvent.value.displaySettings || {},
      id: Date.now(), // Simple ID generation
    };

    customEvents.value.push(event);

    toast.add({
      severity: "success",
      summary: "Event Added",
      detail: "Custom event has been added to your calendar.",
      life: 3000,
    });
  }

  clearNewEvent();
  showEventForm.value = false; // Return to list view

  // Regenerate calendar to show the updated event
  generateCalendar();
};

// Add all holidays from selected set
const addHolidaySet = () => {
  if (!selectedHolidaySet.value) {
    toast.add({
      severity: "warn",
      summary: "No Holiday Set Selected",
      detail: "Please select a holiday set to add.",
      life: 3000,
    });
    return;
  }

  const holidays = holidayData[selectedHolidaySet.value];
  if (!holidays) {
    toast.add({
      severity: "error",
      summary: "Holiday Set Not Found",
      detail: "The selected holiday set could not be loaded.",
      life: 3000,
    });
    return;
  }

  let addedCount = 0;
  const year = config.value.year;

  // Default display settings for holidays - emoji in bottom left
  const holidayDisplaySettings = {
    emojiSize: 16,
    emojiX: 20, // Left side
    emojiY: 80, // Bottom
    textSize: 0, // Hide text by default
    textX: 50,
    textY: 70,
    textRotation: 0,
    textAlign: "center",
    textColor: "#374151",
    textWrap: false,
    textBold: false,
  };

  holidays.forEach((holiday) => {
    let eventDate;

    if (holiday.hebrewDate) {
      // Hebrew calendar holiday - for now, we'll skip these in Gregorian calendar
      // and handle them separately when Hebrew calendar is selected
      if (config.value.calendarType !== "hebrew") {
        // Skip Hebrew dates when in Gregorian calendar mode
        return;
      }
      // For Hebrew calendar, we need to handle these dates specially
      // For now, we'll create placeholder dates that will be handled by the backend
      const [hebrewMonth, hebrewDay] = holiday.hebrewDate
        .split("-")
        .map(Number);
      // Create a special marker for Hebrew dates
      eventDate = new Date(year, hebrewMonth - 1, hebrewDay);
      // Add a special property to mark this as a Hebrew date
      holiday.isHebrewDate = true;
    } else if (holiday.date) {
      // Regular Gregorian calendar holiday
      const [month, day] = holiday.date.split("-").map(Number);
      eventDate = new Date(year, month - 1, day); // month is 0-indexed in JavaScript
    } else {
      // Skip if neither date format is present
      return;
    }

    // Check if event already exists on this date
    const existingEvent = customEvents.value.find(
      (e) =>
        e.date.getTime() === eventDate.getTime() && e.title === holiday.title,
    );

    if (!existingEvent) {
      const event = {
        date: eventDate,
        emoji: holiday.emoji,
        title: holiday.title,
        showTitle: false, // Don't show title for holidays by default
        id: Date.now() + addedCount, // Unique ID for each event
        displaySettings: { ...holidayDisplaySettings },
        isHebrewDate: holiday.isHebrewDate || false,
      };
      customEvents.value.push(event);
      addedCount++;
    }
  });

  if (addedCount > 0) {
    toast.add({
      severity: "success",
      summary: "Holidays Added",
      detail: `Added ${addedCount} holidays to your calendar.`,
      life: 3000,
    });

    // Reset selection
    selectedHolidaySet.value = null;

    // Regenerate calendar with new events
    generateCalendar();
  } else {
    toast.add({
      severity: "info",
      summary: "No New Holidays",
      detail: "All holidays from this set are already on the calendar.",
      life: 3000,
    });
  }
};

const removeCustomEvent = (index) => {
  customEvents.value.splice(index, 1);
  toast.add({
    severity: "info",
    summary: "Event Removed",
    detail: "Custom event has been removed.",
    life: 3000,
  });

  // Regenerate calendar to reflect the removal
  generateCalendar();
};

const onCitySelect = (event) => {
  const cityValue = event.value;
  if (cityValue) {
    const city = popularCities.value.find((c) => c.value === cityValue);
    if (city) {
      config.value.latitude = city.lat;
      config.value.longitude = city.lng;
      toast.add({
        severity: "info",
        summary: "Location Set",
        detail: `Observer location set to ${city.label}`,
        life: 3000,
      });
    }
  }
};

const useCurrentLocation = () => {
  if ("geolocation" in navigator) {
    navigator.geolocation.getCurrentPosition(
      (position) => {
        config.value.latitude = position.coords.latitude;
        config.value.longitude = position.coords.longitude;
        toast.add({
          severity: "success",
          summary: "Location Updated",
          detail: `Latitude: ${position.coords.latitude.toFixed(4)}, Longitude: ${position.coords.longitude.toFixed(4)}`,
          life: 3000,
        });
      },
      (error) => {
        toast.add({
          severity: "error",
          summary: "Location Error",
          detail: "Could not get your location. Please enter manually.",
          life: 5000,
        });
      },
    );
  } else {
    toast.add({
      severity: "warn",
      summary: "Not Supported",
      detail: "Geolocation is not supported by your browser.",
      life: 5000,
    });
  }
};

// Zoom functions
const zoomIn = () => {
  zoomLevel.value = Math.min(zoomLevel.value + 0.05, 1);
};

const zoomOut = () => {
  zoomLevel.value = Math.max(zoomLevel.value - 0.05, 0.1);
};

const resetZoom = () => {
  // SVG is 3500x2300 units (100 units per inch for 35"x23" page)
  const calendarWidth = 3500;

  // Calculate zoom to fill the browser window width, accounting for page padding
  // Subtract padding: 16px left page padding + 16px right + 16px calendar-preview padding
  const availableWidth = window.innerWidth - 48;

  // Calculate zoom to fill the available width
  // Min 0.15 to prevent too small, max 1.0 to prevent over-zoom
  const calculatedZoom = Math.max(
    0.15,
    Math.min(1.0, availableWidth / calendarWidth),
  );
  zoomLevel.value = calculatedZoom;
};

const resetColorsToTheme = () => {
  // Reset colors based on current theme
  const theme = config.value.theme;
  if (theme === "default") {
    config.value.yearColor = "#333333";
    config.value.monthColor = "#333333";
    config.value.dayTextColor = "#000000";
    config.value.dayNameColor = "#666666";
    config.value.weekendBgColor = "#f0f0f0";
  } else if (theme === "vermontWeekends") {
    config.value.yearColor = "#1b5e20";
    config.value.monthColor = "#1b5e20";
    config.value.dayTextColor = "#000000";
    config.value.dayNameColor = "#333333";
    config.value.weekendBgColor = ""; // Dynamic
  } else if (theme === "rainbowWeekends") {
    config.value.yearColor = "#e91e63";
    config.value.monthColor = "#e91e63";
    config.value.dayTextColor = "#000000";
    config.value.dayNameColor = "#9c27b0";
    config.value.weekendBgColor = ""; // Dynamic
  } else {
    // Rainbow days themes
    config.value.yearColor = "#333333";
    config.value.monthColor = "#333333";
    config.value.dayTextColor = "#000000";
    config.value.dayNameColor = "#666666";
    config.value.weekendBgColor = ""; // Dynamic
  }

  // Common colors for all themes
  config.value.gridLineColor = "#c1c1c1";
  config.value.holidayColor = "#ff5252";
  config.value.customDateColor = "#4caf50";
  config.value.moonDarkColor = "#c1c1c1";
  config.value.moonLightColor = "#FFFFFF";

  toast.add({
    severity: "info",
    summary: "Colors Reset",
    detail: "Colors have been reset to theme defaults.",
    life: 3000,
  });
};

const downloadCalendar = async () => {
  if (!generatedSVG.value) return;

  try {
    // Prepare the configuration
    const fullConfig = buildFullConfiguration();

    // Send request to generate PDF
    const response = await fetch("/api/calendar/generate-pdf", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        year: config.value.year,
        theme: config.value.theme,
        layoutStyle: config.value.layoutStyle,
        showMoonPhases: config.value.showMoonPhases,
        showMoonIllumination: config.value.showMoonIllumination,
        showWeekNumbers: config.value.showWeekNumbers,
        showDayNumbers: config.value.showDayNumbers,
        showDayNames: config.value.showDayNames,
        showGrid: config.value.showGrid,
        compactMode: config.value.compactMode,
        highlightWeekends: config.value.highlightWeekends,
        rotateMonthNames: config.value.rotateMonthNames,
        firstDayOfWeek: config.value.firstDayOfWeek,
        locale: config.value.locale || "en-US",
        calendarType: config.value.calendarType,
        holidaySet: config.value.holidaySet,
        monthColor: config.value.monthColor,
        dayNameColor: config.value.dayNameColor,
        dayTextColor: config.value.dayTextColor,
        weekendBgColor: config.value.weekendBgColor,
        gridLineColor: config.value.gridLineColor,
        yearColor: config.value.yearColor,
        holidayColor: config.value.holidayColor,
        customDateColor: config.value.customDateColor,
        moonDarkColor: config.value.moonDarkColor,
        moonLightColor: config.value.moonLightColor,
        moonBorderColor: config.value.moonBorderColor,
        moonBorderWidth: config.value.moonBorderWidth,
        moonSize: config.value.moonSize,
        moonPosition: config.value.moonPosition,
        moonOffsetY: config.value.moonOffsetY,
        latitude: config.value.latitude,
        longitude: config.value.longitude,
        emojiPosition: config.value.emojiPosition,
        observationTime: config.value.observationTime,
        timeZone:
          config.value.timeZone ||
          Intl.DateTimeFormat().resolvedOptions().timeZone,
        customDates: fullConfig.customDates,
        eventTitles: fullConfig.eventTitles,
        holidays: holidays.value ? Array.from(holidays.value) : [],
      }),
    });

    if (!response.ok) {
      const errorText = await response.text();
      console.error("PDF generation failed:", response.status, errorText);
      throw new Error(
        `Failed to generate PDF: ${response.status} - ${errorText}`,
      );
    }

    // Download the PDF
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `calendar-${config.value.year}.pdf`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);

    toast.add({
      severity: "success",
      summary: "Download Started",
      detail: 'Your calendar PDF (35" x 23") has been downloaded.',
      life: 3000,
    });
  } catch (error) {
    console.error("Failed to download PDF:", error);
    toast.add({
      severity: "error",
      summary: "Download Failed",
      detail: "Failed to generate PDF. Please try again.",
      life: 3000,
    });
  }
};

// Save calendar functionality - opens saved calendars dialog
const saveCalendar = async () => {
  if (!generatedSVG.value) {
    toast.add({
      severity: "warn",
      summary: "No Calendar",
      detail: "Please generate a calendar first.",
      life: 3000,
    });
    return;
  }

  // Open the saved calendars dialog
  await loadSavedCalendars();
  showSavedCalendarsDialog.value = true;
};

// Add to cart functionality
// Show the add to cart modal
const openAddToCartModal = () => {
  if (!generatedSVG.value) {
    toast.add({
      severity: "warn",
      summary: "No Calendar",
      detail: "Please generate a calendar first.",
      life: 3000,
    });
    return;
  }
  showAddToCartModal.value = true;
};

// Handle product selection from modal - add to cart and go to checkout
const handleAddToCartSelect = async (productCode: string, price: number) => {
  await addToCart(productCode);
  router.push("/checkout");
};

const addToCart = async (productCode: string = "print") => {
  if (!generatedSVG.value) {
    toast.add({
      severity: "warn",
      summary: "No Calendar",
      detail: "Please generate a calendar first.",
      life: 3000,
    });
    return;
  }

  // Product display names and success messages
  const productMessages: Record<
    string,
    { name: string; successMessage: string }
  > = {
    pdf: {
      name: `Calendar ${config.value.year} (PDF)`,
      successMessage: "Your PDF calendar has been added to the cart.",
    },
    print: {
      name: `Calendar ${config.value.year} (Printed)`,
      successMessage:
        "Your printed calendar has been added to the cart. PDF download included!",
    },
  };

  const productInfo = productMessages[productCode] || productMessages.print;

  try {
    const calendarData = {
      year: config.value.year,
      name: productInfo.name,
      productCode: productCode,
      configuration: buildFullConfiguration(),
      svgContent: generatedSVG.value,
    };

    // If user is logged in, try to save the calendar first
    if (userStore.isLoggedIn) {
      try {
        const saveResponse = await fetch("/api/calendar-templates/user/save", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            name: calendarData.name,
            configuration: calendarData.configuration,
            generatedSvg: calendarData.svgContent,
            templateId: route.query.templateId || null,
          }),
        });

        if (saveResponse.ok) {
          const savedCalendar = await saveResponse.json();
          calendarData.calendarId = savedCalendar.id;
        }
      } catch (saveError) {
        console.warn(
          "Could not save calendar to user account, proceeding with cart addition",
          saveError,
        );
      }
    }

    // Add to cart using GraphQL (works for both logged-in and anonymous users)
    // Price is determined by backend based on productCode
    await cartStore.addToCart(
      productCode, // templateId - use productCode directly (print or pdf)
      calendarData.name,
      calendarData.year,
      1, // quantity
      productCode, // Backend looks up price from product catalog
      { ...calendarData, calendarId: calendarData.calendarId }, // Full configuration including svgContent and calendarId
    );

    toast.add({
      severity: "success",
      summary: "Added to Cart",
      detail: productInfo.successMessage,
      life: 3000,
    });
  } catch (error) {
    console.error("Error adding to cart:", error);
    toast.add({
      severity: "error",
      summary: "Cart Error",
      detail: "Failed to add calendar to cart. Please try again.",
      life: 5000,
    });
  }
};

// Load template if specified in query params
const loadTemplate = async () => {
  const templateId = route.query.templateId;
  if (!templateId) return false; // Return false if no template to load

  try {
    const response = await fetch(`/api/calendar-templates/${templateId}`);
    if (response.ok) {
      const template = await response.json();

      // Load template configuration
      if (template.configuration) {
        Object.assign(config.value, template.configuration);

        // Also load custom events if present
        if (template.configuration.customDates) {
          // Convert custom dates to events format
          customEvents.value = Object.entries(
            template.configuration.customDates,
          ).map(([date, data]) => {
            // Handle both old format (emoji string) and new format (object with settings)
            const isNewFormat = typeof data === "object" && data !== null;
            return {
              date: new Date(date),
              emoji: isNewFormat ? data.emoji : data,
              title: template.configuration.eventTitles?.[date] || "",
              showTitle: !!template.configuration.eventTitles?.[date],
              displaySettings: isNewFormat ? data.displaySettings : {},
              id: Date.now() + Math.random(),
            };
          });
        }
      }

      // Auto-generate preview
      generateCalendar();
      return true; // Template was loaded
    } else {
      throw new Error("Failed to load template");
    }
  } catch (error) {
    console.error("Error loading template:", error);
    toast.add({
      severity: "error",
      summary: "Load Failed",
      detail: "Failed to load template.",
      life: 5000,
    });
    return false; // Failed to load template
  }
};

// Session-based calendar management - saves config and regenerates SVG in one call
const autoSaveCalendar = async () => {
  isAutoSaving.value = true;

  try {
    const fullConfig = buildFullConfiguration();

    // If viewing a shared calendar, copy it to session first
    if (isViewingSharedCalendar.value && originalCalendarId.value) {
      const copyResponse = await fetch(
        `/api/session-calendar/${originalCalendarId.value}/copy-to-session`,
        {
          method: "POST",
        },
      );

      if (copyResponse.ok) {
        const calendar = await copyResponse.json();
        currentCalendarId.value = calendar.id;
        isViewingSharedCalendar.value = false;
        originalCalendarId.value = null;

        // Update URL to new calendar ID
        router.replace({
          path: route.path,
          query: { id: calendar.id },
        });

        toast.add({
          severity: "info",
          summary: "Calendar Copied",
          detail: "This calendar has been copied to your session",
          life: 3000,
        });
      } else {
        throw new Error("Failed to copy calendar to session");
      }
    }

    if (currentCalendarId.value) {
      // Update existing calendar - autosave also regenerates SVG
      const response = await sessionFetch(
        `/api/session-calendar/${currentCalendarId.value}/autosave`,
        {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            configuration: fullConfig,
            name: config.value.name || "Untitled Calendar",
          }),
        },
      );

      if (!response.ok) {
        throw new Error("Failed to autosave");
      }

      // Use the regenerated SVG from the response
      const data = await response.json();
      if (data.svg) {
        generatedSVG.value = wrapSvgWithMargins(data.svg);
        svgKey.value++;
      }
    } else {
      // Create new calendar
      const response = await sessionFetch("/api/session-calendar/save", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          configuration: fullConfig,
          name: config.value.name || "Untitled Calendar",
        }),
      });

      if (response.ok) {
        const data = await response.json();
        currentCalendarId.value = data.id;

        // Update URL with calendar ID
        router.replace({
          path: route.path,
          query: { id: data.id },
        });
      } else {
        throw new Error("Failed to save calendar");
      }
    }
  } catch (error) {
    console.error("Autosave failed:", error);
  } finally {
    isAutoSaving.value = false;
  }
};

// Load calendar from ID or template
const loadCalendarFromUrl = async () => {
  const calendarId = route.query.id;
  const templateId = route.query.templateId;

  if (calendarId) {
    // Load existing calendar for viewing
    try {
      const response = await fetch(`/api/session-calendar/${calendarId}`);
      if (response.ok) {
        const data = await response.json();
        const calendar = data.calendar;
        const isOwnCalendar = data.isOwnCalendar;

        if (isOwnCalendar) {
          // This is the user's own calendar
          currentCalendarId.value = calendar.id;
          isViewingSharedCalendar.value = false;
        } else {
          // Viewing someone else's calendar
          originalCalendarId.value = calendar.id;
          isViewingSharedCalendar.value = true;
          // Don't set currentCalendarId yet - will be set when user makes changes
        }

        // Load configuration
        if (calendar.configuration) {
          Object.assign(config.value, calendar.configuration);

          // Load custom events
          if (calendar.configuration.customDates) {
            customEvents.value = Object.entries(
              calendar.configuration.customDates,
            ).map(([date, eventData]) => {
              const isNewFormat =
                typeof eventData === "object" && eventData !== null;
              return {
                date: new Date(date),
                emoji: isNewFormat ? eventData.emoji : eventData,
                title: calendar.configuration.eventTitles?.[date] || "",
                showTitle: !!calendar.configuration.eventTitles?.[date],
                displaySettings: isNewFormat ? eventData.displaySettings : {},
                id: Date.now() + Math.random(),
              };
            });
          }
        }

        generateCalendar();
        return true;
      }
    } catch (error) {
      console.error("Failed to load calendar:", error);
    }
  } else if (templateId) {
    // Load from template and create session calendar
    try {
      const response = await sessionFetch(
        `/api/session-calendar/from-template/${templateId}`,
        {
          method: "POST",
        },
      );

      if (response.ok) {
        const data = await response.json();
        currentCalendarId.value = data.id;

        // Update URL with new calendar ID
        router.replace({
          path: route.path,
          query: { id: data.id },
        });

        // Load configuration
        if (data.configuration) {
          Object.assign(config.value, data.configuration);

          // Load custom events
          if (data.configuration.customDates) {
            customEvents.value = Object.entries(
              data.configuration.customDates,
            ).map(([date, eventData]) => {
              const isNewFormat =
                typeof eventData === "object" && eventData !== null;
              return {
                date: new Date(date),
                emoji: isNewFormat ? eventData.emoji : eventData,
                title: data.configuration.eventTitles?.[date] || "",
                showTitle: !!data.configuration.eventTitles?.[date],
                displaySettings: isNewFormat ? eventData.displaySettings : {},
                id: Date.now() + Math.random(),
              };
            });
          }
        }

        generateCalendar();
        return true;
      }
    } catch (error) {
      console.error("Failed to create from template:", error);
    }
  }

  // No URL params - start fresh with defaults
  return false;
};

// Watch for configuration changes and autosave
// Use flush: 'sync' so the isInitialGeneration check works correctly
watch(
  config,
  () => {
    // Skip during initial load - initializeNewCalendar handles this
    if (isInitialGeneration.value) {
      return;
    }
    // Auto-save if we have a calendar ID OR if viewing a shared calendar (will trigger copy)
    if (currentCalendarId.value || isViewingSharedCalendar.value) {
      autoSaveCalendar();
    }
  },
  { deep: true, flush: "sync" },
);

// Watch for custom events changes
watch(
  customEvents,
  () => {
    // Skip during initial load - initializeNewCalendar handles this
    if (isInitialGeneration.value) {
      return;
    }
    // Auto-save if we have a calendar ID OR if viewing a shared calendar (will trigger copy)
    if (currentCalendarId.value || isViewingSharedCalendar.value) {
      autoSaveCalendar();
    }
  },
  { deep: true, flush: "sync" },
);

// Build complete configuration including custom events
const buildFullConfiguration = () => {
  const fullConfig = {
    ...config.value,
    customDates: {},
    eventTitles: {},
  };

  // Add custom events to configuration with display settings
  customEvents.value.forEach((event) => {
    if (event.date) {
      const dateStr = formatDate(event.date);
      fullConfig.customDates[dateStr] = {
        emoji: event.emoji || "📅",
        displaySettings: event.displaySettings || {},
      };
      if (event.showTitle && event.title) {
        fullConfig.eventTitles[dateStr] = event.title;
      }
    }
  });

  return fullConfig;
};

// Load all templates for admin management dialog (lazy loaded when dialog opens)
const loadAllTemplates = async () => {
  if (!isAdmin.value) return;

  loadingAllTemplates.value = true;
  loadingTemplates.value = true;
  try {
    // Admin gets all templates (including inactive)
    templates.value = await fetchTemplates(undefined, undefined);
    allTemplates.value = templates.value;
  } catch (error) {
    console.error("Error loading templates:", error);
  } finally {
    loadingAllTemplates.value = false;
    loadingTemplates.value = false;
  }
};

// Select a template from the drawer
const selectTemplate = async (template) => {
  await loadTemplateConfig(template);
  showTemplateDrawer.value = false;
};

// Handle layout change from wizard
const handleWizardLayoutChange = (layout: LayoutType) => {
  // Map wizard layout names to config layoutStyle values
  const layoutMap: Record<LayoutType, string> = {
    "left-aligned": "grid",
    "day-of-week-aligned": "weekday-grid",
  };
  config.value.layoutStyle = layoutMap[layout];
};

// Handle moon settings change from wizard
const handleWizardMoonChange = (settings: MoonSettings) => {
  config.value.moonDisplayMode = settings.moonDisplayMode;
  config.value.moonSize = settings.moonSize;
  config.value.moonOffsetX = settings.moonX;
  config.value.moonOffsetY = settings.moonY;
  config.value.moonBorderColor = settings.moonBorderColor;
  config.value.moonBorderWidth = settings.moonBorderWidth;
  config.value.moonDarkColor = settings.moonDarkSideColor;
  config.value.moonLightColor = settings.moonLightSideColor;
};

// Handle display options change from wizard
const handleWizardDisplayOptionsChange = (options: DisplayOptions) => {
  // Handle solid weekend color vs theme
  if (options.solidWeekendColor) {
    // Use default theme but override weekend background color
    config.value.theme = "default";
    config.value.weekendBgColor = options.solidWeekendColor;
  } else if (options.weekendStyle === "none") {
    // No weekend coloring - use transparent background
    config.value.theme = "default";
    config.value.weekendBgColor = "transparent";
  } else {
    // Map weekend style to theme
    const weekendStyleMap: Record<string, string> = {
      greyscale: "default",
      rainbow: "rainbowWeekends",
      vermont: "vermontWeekends",
      lakeshore: "lakeshoreWeekends",
      sunset: "sunsetWeekends",
      forest: "forestWeekends",
    };
    config.value.theme = weekendStyleMap[options.weekendStyle] || "default";
    config.value.weekendBgColor = ""; // Clear solid color when using theme
  }
  config.value.showGrid = options.showGrid;
  config.value.showDayNames = options.showDayNames;
  config.value.rotateMonthNames = options.rotateMonthNames;
  // Config watcher handles generateCalendar() with debounce
};

// Handle color settings change from wizard
const handleWizardColorsChange = (colors: ColorSettings) => {
  config.value.yearColor = colors.yearColor;
  config.value.monthColor = colors.monthColor;
  config.value.dayTextColor = colors.dayTextColor;
  config.value.dayNameColor = colors.dayNameColor;
  config.value.gridLineColor = colors.gridLineColor;
  config.value.holidayColor = colors.holidayColor;
  config.value.emojiFont = colors.emojiFont;
  // Config watcher handles generateCalendar() with debounce
};

// Handle holiday settings change from wizard
const handleWizardHolidaysChange = (holidays: HolidaySettings) => {
  config.value.holidaySets = holidays.selectedSets;
  config.value.eventDisplayMode = holidays.displayMode;
  // Config watcher handles generateCalendar() with debounce
};

// Load saved calendars for the current user
const loadSavedCalendars = async () => {
  loadingSavedCalendars.value = true;
  try {
    const response = await fetch("/api/calendar-templates/user/calendars");
    if (response.ok) {
      savedCalendars.value = await response.json();
      // Load previews for each calendar
      loadCalendarPreviews();
    } else if (response.status === 401) {
      savedCalendars.value = [];
    }
  } catch (error) {
    console.error("Error loading saved calendars:", error);
    savedCalendars.value = [];
  } finally {
    loadingSavedCalendars.value = false;
  }
};

// Load calendar preview SVGs
const loadCalendarPreviews = async () => {
  for (const calendar of savedCalendars.value) {
    // Mark as loading
    calendarPreviews.value[calendar.id] = "loading";

    try {
      const response = await fetch(
        `/api/calendar-templates/user/calendars/${calendar.id}/preview`,
      );
      if (response.ok) {
        const svg = await response.text();
        // Scale the SVG to fit in the preview container
        const scaledSvg = scaleSvgForPreview(svg);
        calendarPreviews.value[calendar.id] = scaledSvg;
      } else {
        calendarPreviews.value[calendar.id] = "error";
      }
    } catch (error) {
      console.error(
        `Error loading preview for calendar ${calendar.id}:`,
        error,
      );
      calendarPreviews.value[calendar.id] = "error";
    }
  }
};

// Scale SVG to fit in preview container
const scaleSvgForPreview = (svgString) => {
  // Parse the SVG string to modify its attributes
  const parser = new DOMParser();
  const doc = parser.parseFromString(svgString, "image/svg+xml");
  const svgElement = doc.querySelector("svg");

  if (svgElement) {
    // Set a smaller viewBox if needed and ensure it scales properly
    svgElement.setAttribute("width", "100%");
    svgElement.setAttribute("height", "100%");
    svgElement.setAttribute("preserveAspectRatio", "xMidYMid meet");

    // Add a style to ensure it fits
    svgElement.style.maxWidth = "100%";
    svgElement.style.maxHeight = "100%";

    // Return the modified SVG as a string
    const serializer = new XMLSerializer();
    return serializer.serializeToString(doc);
  }

  return svgString;
};

// Save a new calendar or update existing
const saveNewCalendar = async () => {
  if (!editingCalendarName.value.trim()) {
    toast.add({
      severity: "warn",
      summary: "Name Required",
      detail: "Please enter a name for your calendar.",
      life: 3000,
    });
    return;
  }

  // Build custom dates map from current events
  const customDatesMap = {};
  const eventTitles = {};

  customEvents.value.forEach((event) => {
    if (event.date) {
      const dateStr = formatDate(event.date);
      customDatesMap[dateStr] = {
        emoji: event.emoji || "📅",
        displaySettings: event.displaySettings || {},
      };
      if (event.showTitle && event.title) {
        eventTitles[dateStr] = event.title;
      }
    }
  });

  savingNewCalendar.value = true;
  try {
    // Create configuration with custom events
    const configToSave = {
      ...config.value,
      customDates: customDatesMap,
      eventTitles: eventTitles,
    };

    const response = await fetch("/api/calendar-templates/user/save", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        id: selectedSavedCalendar.value?.id || null,
        name: editingCalendarName.value,
        configuration: configToSave,
      }),
    });

    if (response.ok) {
      toast.add({
        severity: "success",
        summary: "Calendar Saved",
        detail: `Calendar "${editingCalendarName.value}" has been saved.`,
        life: 3000,
      });
      await loadSavedCalendars();
      editingCalendarName.value = "";
      selectedSavedCalendar.value = null;
    } else {
      throw new Error("Failed to save calendar");
    }
  } catch (error) {
    console.error("Error saving calendar:", error);
    toast.add({
      severity: "error",
      summary: "Save Failed",
      detail: "Failed to save calendar. Please try again.",
      life: 5000,
    });
  } finally {
    savingNewCalendar.value = false;
  }
};

// Handle cancel edit click for saved calendars
const handleCancelEdit = () => {
  selectedSavedCalendar.value = null;
  editingCalendarName.value = "";
};

// Handle edit calendar click
const handleEditCalendar = (calendar) => {
  selectedSavedCalendar.value = calendar;
  editingCalendarName.value = calendar.name;
};

// Load a saved calendar configuration
const loadSavedCalendar = (calendar) => {
  config.value = { ...calendar.configuration };

  // Restore custom events from saved configuration
  customEvents.value = [];
  if (calendar.configuration.customDates) {
    Object.entries(calendar.configuration.customDates).forEach(
      ([dateStr, data]) => {
        // Handle both old format (emoji string) and new format (object with settings)
        const isNewFormat =
          typeof data === "object" && data !== null && data.emoji;
        const event = {
          date: new Date(dateStr),
          emoji: isNewFormat ? data.emoji : data,
          title: calendar.configuration.eventTitles?.[dateStr] || "",
          showTitle: !!calendar.configuration.eventTitles?.[dateStr],
          displaySettings: isNewFormat ? data.displaySettings || {} : {},
          id: Date.now() + Math.random(),
        };
        customEvents.value.push(event);
      },
    );
  }

  selectedSavedCalendar.value = calendar;
  editingCalendarName.value = calendar.name;
  showSavedCalendarsDialog.value = false;
  generateCalendar();

  toast.add({
    severity: "success",
    summary: "Calendar Loaded",
    detail: `Loaded calendar "${calendar.name}" with ${customEvents.value.length} custom event(s).`,
    life: 3000,
  });
};

// Delete a saved calendar
const deleteSavedCalendar = async (calendar) => {
  if (!confirm(`Are you sure you want to delete "${calendar.name}"?`)) {
    return;
  }

  try {
    const response = await fetch(
      `/api/calendar-templates/user/calendars/${calendar.id}`,
      {
        method: "DELETE",
      },
    );

    if (response.ok) {
      toast.add({
        severity: "success",
        summary: "Calendar Deleted",
        detail: `Calendar "${calendar.name}" has been deleted.`,
        life: 3000,
      });
      await loadSavedCalendars();
      if (selectedSavedCalendar.value?.id === calendar.id) {
        selectedSavedCalendar.value = null;
        editingCalendarName.value = "";
      }
    } else {
      throw new Error("Failed to delete calendar");
    }
  } catch (error) {
    console.error("Error deleting calendar:", error);
    toast.add({
      severity: "error",
      summary: "Delete Failed",
      detail: "Failed to delete calendar. Please try again.",
      life: 5000,
    });
  }
};

// Update an existing saved calendar
const updateSavedCalendar = async (calendar) => {
  selectedSavedCalendar.value = calendar;
  editingCalendarName.value = calendar.name;
  config.value = { ...calendar.configuration };
  if (calendar.configuration.customDates) {
    config.value.customDates = calendar.configuration.customDates;
  }
  await saveNewCalendar();
};

// Load template configuration
const loadTemplateConfig = async (template) => {
  if (template.configuration) {
    // Create session calendar from template
    try {
      const response = await sessionFetch(
        `/api/session-calendar/from-template/${template.id}`,
        {
          method: "POST",
        },
      );

      if (response.ok) {
        const result = await response.json();

        // Update current calendar ID
        currentCalendarId.value = result.id;
        currentTemplateId.value = template.id;

        // Apply configuration
        if (result.configuration) {
          Object.assign(config.value, result.configuration);

          // Load custom events if present
          if (result.configuration.customDates) {
            customEvents.value = Object.entries(
              result.configuration.customDates,
            ).map(([date, data]) => {
              // Handle both old format (emoji string) and new format (object with settings)
              const isNewFormat = typeof data === "object" && data !== null;
              return {
                date: new Date(date),
                emoji: isNewFormat ? data.emoji : data,
                title: result.configuration.eventTitles?.[date] || "",
                showTitle: !!result.configuration.eventTitles?.[date],
                displaySettings: isNewFormat ? data.displaySettings : {},
                id: Date.now() + Math.random(),
              };
            });
          }
        }

        // Update URL to use calendar ID
        router.replace({
          query: { id: result.id },
        });

        showTemplatesDialog.value = false;
        await generateCalendar();
      }
    } catch (error) {
      console.error("Failed to load template:", error);
      toast.add({
        severity: "error",
        summary: "Load Failed",
        detail: "Failed to load template",
        life: 3000,
      });
    }
  }
};

// Open save template dialog
const saveAsTemplate = () => {
  templateToSave.value = {
    name: "",
    description: "",
    isActive: true,
    isFeatured: false,
    displayOrder: 0,
  };
  showSaveTemplateDialog.value = true;
};

// Confirm save template
const confirmSaveTemplate = async () => {
  if (!templateToSave.value.name) return;

  const fullConfig = {
    ...config.value,
    customDates: {},
    eventTitles: {},
  };

  // Add custom events to configuration with display settings
  customEvents.value.forEach((event) => {
    if (event.date) {
      const dateStr = formatDate(event.date);
      fullConfig.customDates[dateStr] = {
        emoji: event.emoji || "📅",
        displaySettings: event.displaySettings || {},
      };
      if (event.showTitle && event.title) {
        fullConfig.eventTitles[dateStr] = event.title;
      }
    }
  });

  const requestBody = {
    name: templateToSave.value.name,
    description: templateToSave.value.description,
    configuration: fullConfig,
    isActive: templateToSave.value.isActive,
    isFeatured: templateToSave.value.isFeatured,
    displayOrder: templateToSave.value.displayOrder,
  };

  try {
    // Use GraphQL to create template
    const newTemplate = await createTemplate(requestBody);
    currentTemplateId.value = newTemplate.id;
    toast.add({
      severity: "success",
      summary: "Template Saved",
      detail: `Template "${templateToSave.value.name}" has been created.`,
      life: 3000,
    });
    showSaveTemplateDialog.value = false;
    loadAllTemplates(); // Reload templates list
  } catch (error) {
    console.error("Error saving template:", error);
    toast.add({
      severity: "error",
      summary: "Save Failed",
      detail:
        error.message ||
        "Failed to save template. Please check the console for details.",
      life: 5000,
    });
  }
};

// Update existing template
const updateTemplate = async (template) => {
  const fullConfig = {
    ...config.value,
    customDates: {},
    eventTitles: {},
  };

  // Add custom events to configuration with display settings
  customEvents.value.forEach((event) => {
    if (event.date) {
      const dateStr = formatDate(event.date);
      fullConfig.customDates[dateStr] = {
        emoji: event.emoji || "📅",
        displaySettings: event.displaySettings || {},
      };
      if (event.showTitle && event.title) {
        fullConfig.eventTitles[dateStr] = event.title;
      }
    }
  });

  try {
    // Use GraphQL to update template
    await updateTemplateGraphQL(template.id, {
      name: template.name,
      description: template.description,
      configuration: fullConfig,
      isActive: template.isActive,
      isFeatured: template.isFeatured,
      displayOrder: template.displayOrder,
    });

    toast.add({
      severity: "success",
      summary: "Template Updated",
      detail: `Template "${template.name}" has been updated.`,
      life: 3000,
    });
    loadAllTemplates(); // Reload templates list
  } catch (error) {
    console.error("Error updating template:", error);
    toast.add({
      severity: "error",
      summary: "Update Failed",
      detail: "Failed to update template.",
      life: 5000,
    });
  }
};

// Open duplicate dialog
const duplicateTemplate = (template) => {
  templateToDuplicate.value = template;
  duplicateName.value = `${template.name} (Copy)`;
  showDuplicateDialog.value = true;
};

// Confirm duplicate template
const confirmDuplicateTemplate = async () => {
  if (!duplicateName.value || !templateToDuplicate.value) return;

  try {
    // Use GraphQL to create duplicate template
    await createTemplate({
      name: duplicateName.value,
      description: templateToDuplicate.value.description,
      configuration: templateToDuplicate.value.configuration,
      isActive: templateToDuplicate.value.isActive,
      isFeatured: false, // Don't copy featured status
      displayOrder: templateToDuplicate.value.displayOrder,
    });

    toast.add({
      severity: "success",
      summary: "Template Duplicated",
      detail: `Template "${duplicateName.value}" has been created.`,
      life: 3000,
    });
    showDuplicateDialog.value = false;
    loadAllTemplates(); // Reload templates list
  } catch (error) {
    console.error("Error duplicating template:", error);
    toast.add({
      severity: "error",
      summary: "Duplication Failed",
      detail:
        error.message ||
        "Failed to duplicate template. Please check the console for details.",
      life: 5000,
    });
  }
};
</script>

<style scoped>
.calendar-generator {
  /* Let content determine height naturally */
}

.calendar-preview {
  position: relative;
  overflow: visible;
  border: 1px solid #f8f9fa;
  border-radius: 8px;
  padding: 1rem;
  display: inline-block;
}

.svg-container {
  transition: transform 0.3s ease;
  display: inline-block;
}

:deep(.svg-container svg) {
  max-width: none;
  height: auto;
}

/* Ruler styles */
.ruler-wrapper {
  display: inline-block;
  position: relative;
}

.ruler-wrapper.show-rulers {
  padding-right: 32px;
  padding-bottom: 28px;
}

.scaled-wrapper {
  position: relative;
  overflow: hidden;
}

.page-border {
  display: inline-block;
  border: 1px solid #ccc;
  border-right: 2px solid #999;
  border-bottom: 2px solid #999;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
}

.ruler {
  position: absolute;
  background: transparent;
  display: flex;
  z-index: 100;
}

.ruler-bottom {
  bottom: 0;
  left: 0;
  height: 24px;
  flex-direction: row;
  border-top: 2px solid #999;
}

.ruler-right {
  top: 0;
  right: 0;
  width: 28px;
  flex-direction: column;
  border-left: 2px solid #999;
}

/* Each inch mark - width/height set dynamically via inline styles */
/* Base: 100px per inch (matching SVG's 100 units per inch), scaled by zoomLevel */
.ruler-bottom .ruler-mark {
  height: 100%;
  border-right: 1px solid #999;
  position: relative;
  box-sizing: border-box;
  flex-shrink: 0;
}

.ruler-right .ruler-mark {
  width: 100%;
  border-bottom: 1px solid #999;
  position: relative;
  box-sizing: border-box;
  flex-shrink: 0;
}

.ruler-label {
  font-size: 10px;
  font-weight: 500;
  color: #333;
  position: absolute;
  white-space: nowrap;
}

.ruler-bottom .ruler-label {
  bottom: 4px;
  right: 2px;
}

.ruler-right .ruler-label {
  top: 2px;
  left: 5px;
}

:deep(.p-accordion .p-accordion-header-link) {
  padding: 0.75rem 1rem;
}

:deep(.p-accordion .p-accordion-content) {
  padding: 0;
}

.config-content::-webkit-scrollbar {
  width: 6px;
}

.config-content::-webkit-scrollbar-track {
  background: #f1f1f1;
}

.config-content::-webkit-scrollbar-thumb {
  background: #888;
  border-radius: 3px;
}

.config-content::-webkit-scrollbar-thumb:hover {
  background: #555;
}

/* Template drawer styles */
.template-drawer-content {
  overflow-y: auto;
  height: calc(100vh - 180px);
  padding: 0 0.5rem;
}

.template-drawer-content::-webkit-scrollbar {
  width: 6px;
}

.template-drawer-content::-webkit-scrollbar-track {
  background: #f1f1f1;
}

.template-drawer-content::-webkit-scrollbar-thumb {
  background: #888;
  border-radius: 3px;
}

.template-thumbnail :deep(svg) {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.template-card-item:hover {
  background-color: #f9fafb;
}

.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
