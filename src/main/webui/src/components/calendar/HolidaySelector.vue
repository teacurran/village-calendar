<template>
  <Card class="holiday-selector-card">
    <template #title>
      <div class="card-title">
        <i class="pi pi-calendar-plus"></i>
        Holiday Sets
      </div>
    </template>
    <template #content>
      <div class="holiday-selector-content">
        <p class="description">
          Select which holiday sets to include in your calendar. Selected
          holidays will automatically appear on their respective dates.
        </p>
        <div class="holiday-list">
          <div
            v-for="holidaySet in HOLIDAY_SETS"
            :key="holidaySet.id"
            class="holiday-item"
          >
            <Checkbox
              :model-value="isSelected(holidaySet.id)"
              :input-id="holidaySet.id"
              :binary="true"
              @update:model-value="toggleHolidaySet(holidaySet.id)"
            />
            <label :for="holidaySet.id" class="holiday-label">
              <span class="holiday-name">{{ holidaySet.name }}</span>
              <span class="holiday-description">{{
                holidaySet.description
              }}</span>
            </label>
          </div>
        </div>
      </div>
    </template>
  </Card>
</template>

<script setup lang="ts">
import { computed } from "vue";
import Card from "primevue/card";
import Checkbox from "primevue/checkbox";
import { useCalendarStore } from "@/stores/calendarStore";
import { HOLIDAY_SETS } from "@/types/calendar";

const calendarStore = useCalendarStore();

const selectedSets = computed(
  () => calendarStore.draftConfiguration?.holidays.sets || [],
);

/**
 * Check if a holiday set is selected
 */
function isSelected(setId: string): boolean {
  return selectedSets.value.includes(setId);
}

/**
 * Toggle a holiday set selection
 */
function toggleHolidaySet(setId: string) {
  const currentSets = [...selectedSets.value];
  const index = currentSets.indexOf(setId);

  if (index !== -1) {
    currentSets.splice(index, 1);
  } else {
    currentSets.push(setId);
  }

  calendarStore.updateHolidaySets(currentSets);
}
</script>

<style scoped>
.holiday-selector-card {
  height: 100%;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--primary-color);
}

.holiday-selector-content {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.description {
  color: #6b7280;
  font-size: 0.875rem;
  margin: 0;
  line-height: 1.5;
}

.holiday-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.holiday-item {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 0.75rem;
  border-radius: 8px;
  transition: background-color 0.2s;
}

.holiday-item:hover {
  background-color: #f9fafb;
}

.holiday-label {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  cursor: pointer;
}

.holiday-name {
  font-weight: 600;
  color: #1f2937;
  font-size: 0.9375rem;
}

.holiday-description {
  color: #6b7280;
  font-size: 0.8125rem;
  line-height: 1.4;
}

/* Responsive adjustments */
@media (max-width: 640px) {
  .holiday-item {
    padding: 0.5rem;
  }

  .holiday-name {
    font-size: 0.875rem;
  }

  .holiday-description {
    font-size: 0.75rem;
  }
}
</style>
