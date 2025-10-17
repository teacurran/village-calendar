<template>
  <Dialog
    v-model:visible="dialogVisible"
    :header="dialogTitle"
    :modal="true"
    :closable="true"
    :style="{ width: '90vw', maxWidth: '600px' }"
    @hide="handleClose"
  >
    <div class="event-editor-form">
      <!-- Date Display -->
      <div class="form-field">
        <label class="field-label">Date</label>
        <div class="date-display">
          {{ formattedDate }}
        </div>
      </div>

      <!-- Event Text -->
      <div class="form-field">
        <label for="event-text" class="field-label">Event Description</label>
        <InputText
          id="event-text"
          v-model="eventForm.text"
          placeholder="Enter event description (max 500 characters)"
          :maxlength="500"
          class="w-full"
        />
        <small class="char-count">
          {{ eventForm.text.length }} / 500 characters
        </small>
      </div>

      <!-- Emoji Selection -->
      <div class="form-field">
        <label class="field-label">Emoji (Optional)</label>
        <div class="emoji-selector">
          <Button
            :label="eventForm.emoji || 'Select Emoji'"
            :icon="eventForm.emoji ? '' : 'pi pi-plus'"
            outlined
            @click="showEmojiPicker = true"
          >
            <template v-if="eventForm.emoji" #default>
              <span class="emoji-display">{{ eventForm.emoji }}</span>
              Select Emoji
            </template>
          </Button>
          <Button
            v-if="eventForm.emoji"
            icon="pi pi-times"
            severity="secondary"
            text
            aria-label="Clear emoji"
            @click="eventForm.emoji = undefined"
          />
        </div>
      </div>

      <!-- Color Picker -->
      <div class="form-field">
        <label for="event-color" class="field-label">Color (Optional)</label>
        <div class="color-selector">
          <ColorPicker v-model="eventForm.color" format="hex" :inline="false" />
          <InputText
            id="event-color"
            v-model="eventForm.color"
            placeholder="#3B82F6"
            class="color-input"
          />
          <Button
            v-if="eventForm.color"
            icon="pi pi-times"
            severity="secondary"
            text
            aria-label="Clear color"
            @click="eventForm.color = undefined"
          />
        </div>
      </div>

      <!-- Event Preview -->
      <div v-if="eventForm.text" class="form-field">
        <label class="field-label">Preview</label>
        <div
          class="event-preview"
          :style="{ backgroundColor: eventForm.color || '#3B82F6' }"
        >
          <span v-if="eventForm.emoji" class="preview-emoji">{{
            eventForm.emoji
          }}</span>
          <span class="preview-text">{{ eventForm.text }}</span>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <Button
          v-if="existingEvent"
          label="Delete"
          icon="pi pi-trash"
          severity="danger"
          text
          @click="handleDelete"
        />
        <div class="footer-actions">
          <Button label="Cancel" icon="pi pi-times" text @click="handleClose" />
          <Button
            label="Save"
            icon="pi pi-check"
            :disabled="!canSave"
            @click="handleSave"
          />
        </div>
      </div>
    </template>
  </Dialog>

  <!-- Emoji Picker Dialog -->
  <EmojiPicker v-model:visible="showEmojiPicker" @select="handleEmojiSelect" />
</template>

<script setup lang="ts">
import { ref, computed, watch } from "vue";
import Dialog from "primevue/dialog";
import Button from "primevue/button";
import InputText from "primevue/inputtext";
import ColorPicker from "primevue/colorpicker";
import { useCalendarStore } from "@/stores/calendarStore";
import {
  formatDateForAPI,
  getMonthName,
  type CalendarEvent,
} from "@/types/calendar";
import EmojiPicker from "./EmojiPicker.vue";

interface Props {
  visible: boolean;
  year: number;
  month: number;
  day: number;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  "update:visible": [value: boolean];
  save: [event: CalendarEvent];
  delete: [date: string];
}>();

const calendarStore = useCalendarStore();

const dialogVisible = ref(props.visible);
const showEmojiPicker = ref(false);

interface EventForm {
  text: string;
  emoji?: string;
  color?: string;
}

const eventForm = ref<EventForm>({
  text: "",
  emoji: undefined,
  color: undefined,
});

// Computed properties
const dateString = computed(() =>
  formatDateForAPI(props.year, props.month, props.day),
);

const formattedDate = computed(() => {
  return `${getMonthName(props.month)} ${props.day}, ${props.year}`;
});

const existingEvent = computed(() => {
  const events = calendarStore.getEventsForDate(dateString.value);
  return events.length > 0 ? events[0] : null;
});

const dialogTitle = computed(() => {
  return existingEvent.value ? "Edit Event" : "Add Event";
});

const canSave = computed(() => {
  return eventForm.value.text.trim().length > 0;
});

// Watch for visibility changes
watch(
  () => props.visible,
  (newVal) => {
    dialogVisible.value = newVal;
    if (newVal) {
      loadExistingEvent();
    }
  },
);

watch(dialogVisible, (newVal) => {
  emit("update:visible", newVal);
  if (!newVal) {
    resetForm();
  }
});

/**
 * Load existing event data into form
 */
function loadExistingEvent() {
  const event = existingEvent.value;
  if (event) {
    eventForm.value = {
      text: event.text,
      emoji: event.emoji,
      color: event.color,
    };
  } else {
    resetForm();
  }
}

/**
 * Reset form to empty state
 */
function resetForm() {
  eventForm.value = {
    text: "",
    emoji: undefined,
    color: undefined,
  };
}

/**
 * Handle emoji selection
 */
function handleEmojiSelect(emoji: string) {
  eventForm.value.emoji = emoji;
}

/**
 * Handle save
 */
function handleSave() {
  if (!canSave.value) return;

  const event: CalendarEvent = {
    date: dateString.value,
    text: eventForm.value.text.trim(),
    emoji: eventForm.value.emoji,
    color: eventForm.value.color,
  };

  emit("save", event);
  dialogVisible.value = false;
}

/**
 * Handle delete
 */
function handleDelete() {
  emit("delete", dateString.value);
  dialogVisible.value = false;
}

/**
 * Handle dialog close
 */
function handleClose() {
  emit("update:visible", false);
}
</script>

<style scoped>
.event-editor-form {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  padding: 1rem 0;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.field-label {
  font-weight: 600;
  font-size: 0.875rem;
  color: #374151;
}

.date-display {
  padding: 0.75rem;
  background: #f3f4f6;
  border-radius: 6px;
  font-weight: 500;
  color: #1f2937;
}

.char-count {
  color: #6b7280;
  font-size: 0.75rem;
  text-align: right;
}

.emoji-selector {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.emoji-display {
  font-size: 1.5rem;
  margin-right: 0.5rem;
}

.color-selector {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.color-input {
  flex: 1;
}

.event-preview {
  padding: 0.75rem;
  border-radius: 6px;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: white;
  font-weight: 500;
}

.preview-emoji {
  font-size: 1.25rem;
}

.preview-text {
  flex: 1;
}

.dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.footer-actions {
  display: flex;
  gap: 0.5rem;
}

/* Responsive adjustments */
@media (max-width: 640px) {
  .event-editor-form {
    padding: 0.5rem 0;
  }

  .emoji-selector,
  .color-selector {
    flex-wrap: wrap;
  }
}
</style>
