<template>
  <div class="calendar-grid">
    <div v-for="month in 12" :key="month" class="month-container">
      <div class="month-header">
        <h3 class="month-title">{{ getMonthName(month) }}</h3>
      </div>
      <div class="month-grid">
        <!-- Day of week headers -->
        <div v-for="day in dayHeaders" :key="day" class="day-header">
          {{ day }}
        </div>
        <!-- Empty cells for first week alignment -->
        <div
          v-for="empty in getFirstDayOfWeek(year, month)"
          :key="`empty-${month}-${empty}`"
          class="day-cell empty"
        />
        <!-- Date cells -->
        <div
          v-for="day in getDaysInMonth(year, month)"
          :key="`${month}-${day}`"
          class="day-cell"
          :class="{
            'has-event': hasEventOnDate(month, day),
            'is-today': isToday(month, day),
          }"
          @click="handleDateClick(month, day)"
        >
          <span class="day-number">{{ day }}</span>
          <div v-if="hasEventOnDate(month, day)" class="event-indicators">
            <div
              v-for="(event, index) in getEventsForDate(month, day)"
              :key="index"
              class="event-indicator"
              :style="{
                backgroundColor: event.color || 'var(--primary-color)',
              }"
            >
              <span v-if="event.emoji" class="event-emoji">{{
                event.emoji
              }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useCalendarStore } from "@/stores/calendarStore";
import {
  getDaysInMonth,
  getMonthName,
  getFirstDayOfWeek,
  formatDateForAPI,
  type CalendarEvent,
} from "@/types/calendar";

interface Props {
  year: number;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  dateClick: [month: number, day: number];
}>();

const calendarStore = useCalendarStore();

const dayHeaders = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

/**
 * Check if a date has events
 */
function hasEventOnDate(month: number, day: number): boolean {
  const dateString = formatDateForAPI(props.year, month, day);
  return calendarStore.getEventsForDate(dateString).length > 0;
}

/**
 * Get events for a specific date
 */
function getEventsForDate(month: number, day: number): CalendarEvent[] {
  const dateString = formatDateForAPI(props.year, month, day);
  return calendarStore.getEventsForDate(dateString);
}

/**
 * Check if date is today
 */
function isToday(month: number, day: number): boolean {
  const today = new Date();
  return (
    today.getFullYear() === props.year &&
    today.getMonth() + 1 === month &&
    today.getDate() === day
  );
}

/**
 * Handle date cell click
 */
function handleDateClick(month: number, day: number) {
  emit("dateClick", month, day);
}
</script>

<style scoped>
.calendar-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 2rem;
  padding: 1rem;
}

.month-container {
  background: white;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.month-header {
  background: var(--primary-color);
  color: white;
  padding: 1rem;
  text-align: center;
}

.month-title {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
}

.month-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 1px;
  background: #e5e7eb;
  padding: 1px;
}

.day-header {
  background: #f9fafb;
  padding: 0.5rem;
  text-align: center;
  font-size: 0.75rem;
  font-weight: 600;
  color: #6b7280;
  text-transform: uppercase;
}

.day-cell {
  background: white;
  aspect-ratio: 1;
  padding: 0.5rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  cursor: pointer;
  position: relative;
  transition: background-color 0.2s;
  min-height: 60px;
}

.day-cell:hover:not(.empty) {
  background: #f3f4f6;
}

.day-cell.empty {
  background: #fafafa;
  cursor: default;
}

.day-cell.is-today {
  background: #fef3c7;
}

.day-cell.has-event {
  background: #eff6ff;
}

.day-number {
  font-size: 0.875rem;
  font-weight: 500;
  color: #374151;
  margin-bottom: 0.25rem;
}

.event-indicators {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  margin-top: 2px;
}

.event-indicator {
  width: 100%;
  min-height: 18px;
  border-radius: 3px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  padding: 2px;
}

.event-emoji {
  font-size: 14px;
  line-height: 1;
}

/* Responsive adjustments */
@media (max-width: 768px) {
  .calendar-grid {
    grid-template-columns: 1fr;
    gap: 1rem;
    padding: 0.5rem;
  }

  .day-cell {
    min-height: 50px;
    padding: 0.25rem;
  }

  .day-number {
    font-size: 0.75rem;
  }

  .event-indicator {
    min-height: 16px;
    font-size: 10px;
  }

  .event-emoji {
    font-size: 12px;
  }
}

@media (min-width: 1440px) {
  .calendar-grid {
    grid-template-columns: repeat(4, 1fr);
  }
}

@media (min-width: 1920px) {
  .calendar-grid {
    grid-template-columns: repeat(6, 1fr);
  }
}
</style>
