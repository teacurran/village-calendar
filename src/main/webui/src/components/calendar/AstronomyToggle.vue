<template>
  <Card class="astronomy-toggle-card">
    <template #title>
      <div class="card-title">
        <i class="pi pi-star"></i>
        Astronomy Features
      </div>
    </template>
    <template #content>
      <div class="astronomy-toggle-content">
        <p class="description">
          Enhance your calendar with astronomical information and alternative
          calendar systems.
        </p>

        <div class="toggle-list">
          <!-- Moon Phases Toggle -->
          <div class="toggle-item">
            <div class="toggle-header">
              <div class="toggle-info">
                <i class="pi pi-moon feature-icon"></i>
                <div class="toggle-label">
                  <span class="toggle-title">Moon Phases</span>
                  <span class="toggle-description">
                    Display moon phase icons for each day of the month
                  </span>
                </div>
              </div>
              <InputSwitch
                :model-value="astronomySettings.showMoonPhases"
                @update:model-value="toggleMoonPhases"
              />
            </div>
          </div>

          <Divider />

          <!-- Hebrew Calendar Toggle -->
          <div class="toggle-item">
            <div class="toggle-header">
              <div class="toggle-info">
                <i class="pi pi-calendar feature-icon"></i>
                <div class="toggle-label">
                  <span class="toggle-title">Hebrew Calendar</span>
                  <span class="toggle-description">
                    Show Hebrew calendar dates alongside Gregorian dates
                  </span>
                </div>
              </div>
              <InputSwitch
                :model-value="astronomySettings.showHebrewCalendar"
                @update:model-value="toggleHebrewCalendar"
              />
            </div>
          </div>
        </div>

        <!-- Info Message -->
        <Message v-if="hasAnyFeatureEnabled" severity="info" :closable="false">
          <template #icon>
            <i class="pi pi-info-circle"></i>
          </template>
          Astronomy features will be visible in the generated calendar PDF.
        </Message>
      </div>
    </template>
  </Card>
</template>

<script setup lang="ts">
import { computed } from "vue";
import Card from "primevue/card";
import InputSwitch from "primevue/inputswitch";
import Divider from "primevue/divider";
import Message from "primevue/message";
import { useCalendarStore } from "@/stores/calendarStore";

const calendarStore = useCalendarStore();

const astronomySettings = computed(
  () =>
    calendarStore.draftConfiguration?.astronomy || {
      showMoonPhases: false,
      showHebrewCalendar: false,
    },
);

const hasAnyFeatureEnabled = computed(
  () =>
    astronomySettings.value.showMoonPhases ||
    astronomySettings.value.showHebrewCalendar,
);

/**
 * Toggle moon phases display
 */
function toggleMoonPhases(value: boolean) {
  calendarStore.updateAstronomySettings({
    showMoonPhases: value,
  });
}

/**
 * Toggle Hebrew calendar display
 */
function toggleHebrewCalendar(value: boolean) {
  calendarStore.updateAstronomySettings({
    showHebrewCalendar: value,
  });
}
</script>

<style scoped>
.astronomy-toggle-card {
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

.astronomy-toggle-content {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.description {
  color: #6b7280;
  font-size: 0.875rem;
  margin: 0;
  line-height: 1.5;
}

.toggle-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.toggle-item {
  padding: 0.75rem 0;
}

.toggle-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
}

.toggle-info {
  flex: 1;
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
}

.feature-icon {
  font-size: 1.5rem;
  color: var(--primary-color);
  margin-top: 0.125rem;
}

.toggle-label {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.toggle-title {
  font-weight: 600;
  color: #1f2937;
  font-size: 0.9375rem;
}

.toggle-description {
  color: #6b7280;
  font-size: 0.8125rem;
  line-height: 1.4;
}

/* Responsive adjustments */
@media (max-width: 640px) {
  .toggle-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .toggle-info {
    width: 100%;
  }

  .toggle-title {
    font-size: 0.875rem;
  }

  .toggle-description {
    font-size: 0.75rem;
  }
}
</style>
