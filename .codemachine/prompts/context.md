# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I2.T7",
  "iteration_id": "I2",
  "iteration_goal": "Implement calendar creation, editing, and template system. Build calendar editor UI components. Integrate astronomical calculations (moon phases, Hebrew calendar). Create sequence diagrams for key workflows",
  "description": "Create Vue.js components for the calendar editor interface: CalendarEditor.vue (main editor container, loads calendar data, manages state), CalendarGrid.vue (visual calendar grid with month/date layout), EventEditor.vue (form for adding/editing events), EmojiPicker.vue (emoji selection dialog), HolidaySelector.vue (checkbox list of holiday sets), AstronomyToggle.vue (enable/disable moon phases, Hebrew calendar). Use PrimeVue components (DataTable, Dialog, InputText, Calendar, MultiSelect, Checkbox). Implement calendar state management in Pinia store (calendarStore.ts). Integrate with GraphQL API (use queries to load calendar, mutations to save changes). Add real-time preview updates (calendar grid updates as user adds events). Handle loading states and error messages.",
  "agent_type_hint": "FrontendAgent",
  "inputs": "Calendar editor requirements from Plan Section \"Features\", PrimeVue component documentation, GraphQL schema from I1.T6",
  "target_files": [
    "src/main/webui/src/views/CalendarEditor.vue",
    "src/main/webui/src/components/calendar/CalendarGrid.vue",
    "src/main/webui/src/components/calendar/EventEditor.vue",
    "src/main/webui/src/components/calendar/EmojiPicker.vue",
    "src/main/webui/src/components/calendar/HolidaySelector.vue",
    "src/main/webui/src/components/calendar/AstronomyToggle.vue",
    "src/main/webui/src/stores/calendarStore.ts",
    "src/main/webui/src/graphql/calendar-queries.ts"
  ],
  "deliverables": "Functional calendar editor UI with all sub-components, Calendar grid displays year with 12 months, dates clickable to add events, Event editor dialog allows adding/editing event text, emoji, color, Emoji picker shows categorized emoji list (PrimeVue Dialog + emoji data), Holiday selector updates calendar config in Pinia store, Astronomy toggle enables/disables moon phase and Hebrew calendar overlays, GraphQL integration (load calendar, save changes)",
  "acceptance_criteria": "Calendar editor loads existing calendar via GraphQL query on mount, Clicking date in calendar grid opens event editor dialog, Adding event updates Pinia store and triggers GraphQL mutation, Emoji picker allows selecting emoji, updates event in editor, Holiday selector changes persist to backend (updateCalendar mutation), Real-time preview: changes visible immediately without page reload, Loading spinner shown while GraphQL queries in flight, Error messages displayed if GraphQL mutations fail",
  "dependencies": [
    "I2.T6",
    "I1.T11"
  ],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

### Context: Data Model & Configuration Structure

The `UserCalendar` entity stores all customization data in a **JSONB `configuration` field**. This is the core data structure you must work with:

```typescript
interface CalendarConfiguration {
  events: Array<{
    date: string       // ISO 8601 format "2025-01-15"
    text: string       // Event description, max 500 chars
    emoji?: string     // Unicode emoji
    color?: string     // Hex color code
  }>
  holidays: {
    sets: string[]     // e.g., ["us-federal", "jewish"]
  }
  astronomy: {
    showMoonPhases: boolean
    showHebrewCalendar: boolean
  }
  layout?: {
    // Future: orientation, size, etc.
  }
  colors?: {
    // Future: color scheme customization
  }
}
```

**CRITICAL:** Events are embedded in the configuration field, NOT stored as separate database records. This is an MVP design choice for flexibility.

### Context: GraphQL API Contract

**Key Queries:**
- `calendar(id: ID!): UserCalendar` - Fetch single calendar with all data
- `myCalendars(year: Int): [UserCalendar!]!` - List user's calendars

**Key Mutations:**
- `createCalendar(input: CalendarInput!): UserCalendar!`
- `updateCalendar(id: ID!, input: CalendarUpdateInput!): UserCalendar!`

**Input Types:**
```graphql
input CalendarInput {
  name: String!
  year: Int!
  templateId: ID!
  configuration: JSON
  isPublic: Boolean
}

input CalendarUpdateInput {
  name: String
  configuration: JSON
  isPublic: Boolean
}
```

**IMPORTANT:** When calling `updateCalendar`, send the **entire configuration object**, not partial updates. The backend replaces the full JSONB field.

### Context: Technology Stack

- **Vue 3.5.13** - Composition API with `<script setup>` syntax
- **PrimeVue 4.3.2** - Aura theme
- **Pinia 3.0.3** - State management
- **TypeScript 5.8.2** - Strict mode enabled
- **Vite 6.2.0** - Build tool
- **emoji-picker-element 1.26.3** - Pre-installed emoji picker library

---

## 3. Codebase Analysis & Strategic Guidance

### Relevant Existing Code

#### **File:** `src/main/webui/src/stores/calendarStore.ts`
   - **Status:** **ALREADY EXISTS with comprehensive implementation!**
   - **Summary:** Pinia store with full GraphQL integration for calendar operations
   - **Existing Methods:**
     - `fetchTemplates()` - Load templates
     - `fetchCalendar(id)` - Load single calendar
     - `fetchUserCalendars()` - Load user's calendars
     - `createCalendar(input)` - Create new calendar
     - `updateCalendar(id, input)` - **THIS IS WHAT YOU'LL USE FOR SAVING EDITS**
     - `deleteCalendar(id)` - Delete calendar
     - `pollCalendarStatus(id)` - Poll for PDF generation status
   - **Recommendation:** You MUST extend this existing store, not replace it. Add these new state properties:
     ```typescript
     selectedDate: Date | null           // Currently selected date for event editor
     draftConfiguration: CalendarConfiguration | null  // Optimistic updates
     isSaving: boolean                   // Track mutation in progress
     ```
   - **Pattern:** The store uses Options API (`defineStore` with `state`, `actions`, `getters`). Continue this pattern.

#### **File:** `src/main/webui/package.json`
   - **Summary:** All required dependencies are installed
   - **emoji-picker-element:** v1.26.3 installed - use this for EmojiPicker
   - **PrimeVue:** v4.3.2 - import components like `import Dialog from 'primevue/dialog'`
   - **Recommendation:** NO new dependencies needed

#### **File:** `api/schema.graphql`
   - **Summary:** Complete GraphQL schema
   - **Key Types:**
     - `UserCalendar` with `configuration: JSON` field
     - `CalendarStatus`: DRAFT | GENERATING | READY | FAILED
   - **Recommendation:** Reference this schema when writing GraphQL queries

#### **File:** `src/main/webui/src/components/common/AppHeader.vue`
   - **Summary:** Example component using Composition API
   - **Pattern Observed:** Uses `<script setup lang="ts">`, imports from `primevue/`, uses `@/` path alias
   - **Recommendation:** Follow this exact pattern for new components

### Implementation Tips & Notes

#### **Tip #1: Component Structure Pattern**
All Vue files MUST follow this structure:
```vue
<template>
  <!-- Template here -->
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useCalendarStore } from '@/stores/calendarStore'
import Dialog from 'primevue/dialog'
// etc.

// Component logic here
</script>

<style scoped>
/* Component styles here */
</style>
```

#### **Tip #2: Date Handling**
For calendar date calculations:
```typescript
// Get days in month
function getDaysInMonth(year: number, month: number): number {
  return new Date(year, month, 0).getDate()
}

// Format date for API (ISO 8601)
function formatDateForAPI(year: number, month: number, day: number): string {
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
}
```

#### **Tip #3: Emoji Picker Integration**
```vue
<template>
  <Dialog v-model:visible="showPicker">
    <emoji-picker @emoji-click="handleEmojiSelect"></emoji-picker>
  </Dialog>
</template>

<script setup lang="ts">
import 'emoji-picker-element'

function handleEmojiSelect(event: any) {
  const emoji = event.detail.unicode
  emit('select', emoji)
}
</script>
```

#### **Tip #4: Debounced Auto-Save Pattern**
For real-time updates with debouncing:
```typescript
import { watchDebounced } from '@vueuse/core'

const calendarStore = useCalendarStore()

watchDebounced(
  () => calendarStore.currentCalendar?.configuration,
  async (newConfig) => {
    if (!newConfig || calendarStore.isSaving) return
    await calendarStore.updateCalendar(calendarId, { configuration: newConfig })
  },
  { debounce: 1000, maxWait: 5000 }
)
```

#### **Tip #5: Calendar Grid Layout**
Use CSS Grid for month layout:
```vue
<template>
  <div class="calendar-grid">
    <div v-for="month in 12" :key="month" class="month">
      <h3>{{ getMonthName(month) }}</h3>
      <div class="days">
        <div v-for="day in daysInMonth(month)" :key="day" 
             class="day" @click="selectDate(month, day)">
          {{ day }}
          <div v-if="getEventsForDate(month, day).length" class="event-indicator" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.calendar-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 1.5rem;
}
.days {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 2px;
}
.day {
  aspect-ratio: 1;
  border: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  position: relative;
}
.event-indicator {
  width: 6px;
  height: 6px;
  background: var(--primary-color);
  border-radius: 50%;
  position: absolute;
  bottom: 4px;
}
</style>
```

#### **Warning #1: TypeScript Strict Mode**
The project has strict TypeScript enabled. You MUST:
- Define proper interfaces for all data structures
- Avoid `any` types (use `unknown` and type guards if needed)
- Handle null/undefined explicitly: `calendar?.configuration?.events`

#### **Warning #2: Optimistic Updates vs Network Errors**
When implementing real-time updates:
1. Update local state immediately (optimistic)
2. Call mutation
3. On success: Keep optimistic update, maybe refresh with server response
4. On error: Show error toast, optionally revert to last known good state

#### **Warning #3: Event Editor Date Context**
The EventEditor component needs to know WHICH date was clicked. Pass this via props:
```vue
<!-- In CalendarGrid.vue -->
<EventEditor 
  v-model:visible="showEditor"
  :year="calendar.year"
  :month="selectedMonth"
  :day="selectedDay"
  @save="handleEventSave"
/>
```

#### **Note #1: Holiday Sets Data**
Define holiday sets as a constant:
```typescript
export const HOLIDAY_SETS = [
  { id: 'us-federal', name: 'US Federal Holidays', description: 'Major US holidays' },
  { id: 'jewish', name: 'Jewish Holidays', description: 'Major Jewish observances' },
  { id: 'christian', name: 'Christian Holidays', description: 'Easter, Christmas, etc.' },
  { id: 'islamic', name: 'Islamic Holidays', description: 'Ramadan, Eid, etc.' },
] as const
```

#### **Note #2: Loading States**
Use PrimeVue ProgressSpinner for loading:
```vue
<template>
  <div v-if="calendarStore.loading" class="loading-overlay">
    <ProgressSpinner />
  </div>
  <div v-else>
    <!-- Calendar editor content -->
  </div>
</template>
```

#### **Note #3: Error Handling**
Use PrimeVue Toast for error messages:
```typescript
import { useToast } from 'primevue/usetoast'

const toast = useToast()

try {
  await calendarStore.updateCalendar(id, input)
  toast.add({ severity: 'success', summary: 'Saved', detail: 'Calendar updated', life: 3000 })
} catch (error) {
  toast.add({ severity: 'error', summary: 'Error', detail: error.message, life: 5000 })
}
```

---

## Additional Guidance

### File Creation Order

Create files in this order to minimize errors:

1. **First:** Create `src/main/webui/src/types/calendar.ts` with TypeScript interfaces
2. **Second:** Extend `src/main/webui/src/stores/calendarStore.ts` with editor-specific state
3. **Third:** Create `src/main/webui/src/components/calendar/CalendarGrid.vue`
4. **Fourth:** Create `src/main/webui/src/components/calendar/EventEditor.vue`
5. **Fifth:** Create `src/main/webui/src/components/calendar/EmojiPicker.vue`
6. **Sixth:** Create `src/main/webui/src/components/calendar/HolidaySelector.vue`
7. **Seventh:** Create `src/main/webui/src/components/calendar/AstronomyToggle.vue`
8. **Eighth:** Create `src/main/webui/src/views/CalendarEditor.vue` (orchestrates all components)

### Router Integration

The CalendarEditor view needs to accept a calendar ID from the route:
```typescript
import { useRoute } from 'vue-router'

const route = useRoute()
const calendarId = computed(() => route.params.id as string)
```

### GraphQL Queries File

While the task mentions creating `calendar-queries.ts`, the existing `calendarStore.ts` already has all GraphQL queries inline. You SHOULD keep this pattern and NOT create a separate queries file, to maintain consistency with existing code.

### Code Quality

- Run `npm run lint` after each component
- Run `npm run format` before committing
- Follow existing style: single quotes, 2-space indent, no semicolons

### Testing Strategy

For acceptance criteria validation:
- Use browser DevTools Network tab to inspect GraphQL mutations
- Test optimistic updates by throttling network (DevTools → Network → Slow 3G)
- Verify error handling by temporarily breaking the GraphQL endpoint
