# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I2.T8",
  "iteration_id": "I2",
  "iteration_goal": "Implement calendar creation, editing, and template system. Build calendar editor UI components. Integrate astronomical calculations (moon phases, Hebrew calendar). Create sequence diagrams for key workflows",
  "description": "Create template gallery view component displaying admin-curated calendar templates. Implement TemplateGallery.vue with grid layout (PrimeVue DataView or custom grid), showing template thumbnails, names, descriptions. Add filtering (by category, year, holiday set - future expansion). Implement \"Start from Template\" button that creates new calendar based on selected template (calls createCalendar mutation with templateId). Create TemplateCard.vue component for individual template preview. Add loading states, empty state (\"No templates available\"), error handling. Integrate with GraphQL (query templates, mutation createCalendar).",
  "agent_type_hint": "FrontendAgent",
  "inputs": "Template gallery requirements from Plan Section \"Features\", PrimeVue DataView component documentation",
  "target_files": [
    "frontend/src/views/TemplateGallery.vue",
    "frontend/src/components/calendar/TemplateCard.vue",
    "frontend/src/graphql/template-queries.ts"
  ],
  "input_files": [
    "frontend/src/views/Home.vue",
    "frontend/src/stores/calendar.ts",
    "api/graphql-schema.graphql"
  ],
  "deliverables": "Template gallery view with grid layout, Template cards show thumbnail, name, description, \"Start from Template\" button creates new calendar, GraphQL integration (fetch templates, create calendar), Loading and error states handled",
  "acceptance_criteria": "Gallery loads templates via GraphQL query on mount, Template cards display thumbnail images (placeholder if none), Clicking \"Start from Template\" creates calendar and redirects to editor, Empty state shown if no active templates, Error message displayed if GraphQL query fails, Responsive design (grid adapts to mobile, tablet, desktop)",
  "dependencies": ["I2.T4", "I2.T6"],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Template Gallery Feature Requirements (from 01_Plan_Overview_and_Setup.md)

```markdown
<!-- anchor: key-components-services -->
### Key Components/Services

**Frontend Components:**
1. **Calendar Editor**: Drag-drop event creation, emoji picker, holiday set selection, astronomical overlay toggles
2. **Template Gallery**: Browse/preview admin-curated templates, "Start from Template" flow
3. **User Dashboard**: My Calendars list (grid/list view), order history, account settings
4. **Checkout Flow**: Cart review, shipping address form, Stripe Checkout integration
5. **Admin Panel**: Template management, order processing dashboard, analytics visualization
```

### Context: Data Model - CalendarTemplate Entity (from 01_Plan_Overview_and_Setup.md)

```markdown
5. **CalendarTemplate**: Admin-created templates
   - Fields: `template_id` (PK), `created_by_user_id` (FK), `name`, `description`, `thumbnail_url`, `config` (JSONB), `is_active`, `sort_order`
```

### Context: Technology Stack - PrimeVue UI Library (from 01_Plan_Overview_and_Setup.md)

```markdown
**Frontend:**
- Framework: Vue 3.5+ (Composition API)
- UI Library: PrimeVue 4.2+ (Aura theme)
- Icons: PrimeIcons 7.0+
- CSS: TailwindCSS 4.0+
- State Management: Pinia
- Routing: Vue Router 4.5+
- Build Tool: Vite 6.1+
- TypeScript: ~5.7.3
- Integration: Quinoa plugin (Quarkus-Vue seamless integration)
```

### Context: PrimeVue Component Library (from 02_Architecture_Overview.md)

```markdown
| **UI Component Library** | PrimeVue | 4.2+ | Comprehensive component set (forms, tables, dialogs). Aura theme. Accessibility built-in. Reduces custom CSS. |
| **Icons** | PrimeIcons | 7.0+ | Consistent iconography. Optimized SVGs. Works seamlessly with PrimeVue. |
```

### Context: Calendar Creation API (from 01_Plan_Overview_and_Setup.md)

```markdown
**Key Queries:**
- `templates(isActive: Boolean): [CalendarTemplate!]!`

**Key Mutations:**
- `createCalendar(input: CreateCalendarInput!): Calendar!`

**Type Safety**: GraphQL schema generates TypeScript types for Vue.js frontend (compile-time validation)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `src/main/webui/src/services/templateService.ts`
    *   **Summary:** This file contains all GraphQL API calls for template operations. It includes `fetchTemplates(isActive?: boolean)` which queries the GraphQL endpoint and returns templates filtered by the `isActive` parameter. The service handles authentication via JWT tokens from localStorage and provides comprehensive error handling. All functions return `ServiceResponse<T>` with `data` and `error` fields.
    *   **Recommendation:** You MUST import and use the `fetchTemplates` function from this file to load templates in your TemplateGallery component. DO NOT write duplicate GraphQL queries. The service already handles authentication, error responses (401/403), and GraphQL errors properly. Call it like: `const result = await fetchTemplates(true)` and check for `result.error`.

*   **File:** `src/main/webui/src/types/template.ts`
    *   **Summary:** This file defines the TypeScript interfaces for `CalendarTemplate` and `TemplateInput`. The `CalendarTemplate` interface includes: `id`, `name`, `description`, `configuration`, `thumbnailUrl`, `previewSvg`, `isActive`, `isFeatured`, `displayOrder`, `created`, and `updated` fields. All fields are properly typed matching the GraphQL schema.
    *   **Recommendation:** You MUST import the `CalendarTemplate` type from this file for type safety. All template data should be typed using this interface: `import type { CalendarTemplate } from '@/types/template'`.

*   **File:** `src/main/webui/src/stores/templateStore.ts`
    *   **Summary:** This Pinia store manages template state and provides actions like `loadTemplates(isActive?: boolean)`, which calls the templateService and updates the store state (`templates` array, `loading`, `error`). It includes getters for `activeTemplates`, `featuredTemplates`, `getTemplateById`, and `hasTemplates`. The store follows the Options API pattern with `state`, `actions`, and `getters`.
    *   **Recommendation:** You SHOULD use this store in your TemplateGallery component instead of calling the service directly. The store provides reactive state management. Import with `import { useTemplateStore } from '@/stores/templateStore'` and call `await templateStore.loadTemplates(true)` on mount to fetch only active templates. Access templates via `templateStore.templates` or use getters like `templateStore.activeTemplates`.

*   **File:** `src/main/webui/src/stores/calendarStore.ts`
    *   **Summary:** This file contains the calendar store with the `createCalendar(input)` action (lines 313-387) that calls the GraphQL `createCalendar` mutation. The mutation requires: `templateId` (string), `name` (string), `year` (number), and optional `configuration` and `isPublic` fields. The function throws an error if user is not authenticated, returns a Promise that resolves to a `UserCalendar` object, and updates both `currentCalendar` and `userCalendars` array on success.
    *   **Recommendation:** You MUST import and use `useCalendarStore()` from this file. When the user clicks "Start from Template", you should: (1) Check authentication first, (2) Call `await calendarStore.createCalendar({ templateId: template.id, name: template.name + ' ' + currentYear, year: currentYear })`, (3) On success, redirect to the calendar editor using Vue Router with `router.push('/editor')` or the appropriate route for the new calendar.

*   **File:** `src/main/webui/src/view/Dashboard.vue`
    *   **Summary:** This component demonstrates the Vue 3 Composition API pattern with `<script setup lang="ts">`. It uses PrimeVue Card components in a responsive grid layout (`grid-template-columns: repeat(auto-fit, minmax(250px, 1fr))`), Button components with `icon` and `label` props, ProgressSpinner for loading states, and implements empty states with icons (`pi pi-inbox`) and messages. The component structure follows: template → script setup → scoped styles.
    *   **Recommendation:** You SHOULD follow the same component structure and patterns used here. Use PrimeVue Card and Button components, CSS Grid for responsive layouts (`repeat(auto-fit, minmax(300px, 1fr))` for template cards), and PrimeIcons for icons. Import PrimeVue components individually: `import Card from 'primevue/card'`. The `browseTemplates()` function at line 191 currently redirects to `/` - you should update this to redirect to `/templates` once your gallery is ready.

*   **File:** `src/main/webui/src/view/admin/TemplateManager.vue`
    *   **Summary:** This admin component uses PrimeVue DataTable for displaying templates in a table format with pagination, sorting, and filtering. It demonstrates how to use the templateStore (lines 32-34: `computed(() => templateStore.loading)`), handle CRUD operations, and show success/error toasts using `useToast()`. However, DataTable is appropriate for admin interfaces but NOT for public-facing galleries.
    *   **Recommendation:** DO NOT use DataTable for the template gallery. Instead, use PrimeVue Card components in a grid layout for a more visual, user-friendly gallery. However, you CAN refer to this file for: (1) Error handling patterns with PrimeVue Message component, (2) How to use the templateStore's loading/error states, (3) Toast notification patterns for success/error messages.

### Implementation Tips & Notes

*   **Tip:** I confirmed that the `fetchTemplates` function in `templateService.ts` accepts an `isActive` parameter. You SHOULD pass `true` to fetch only active templates for the public gallery: `await templateStore.loadTemplates(true)`. This will filter out inactive/draft templates that admins are still working on.

*   **Note:** The `CalendarTemplate` interface includes both `thumbnailUrl` (string URL to an image) and `previewSvg` (SVG markup as string) fields. For the template card display, you should: (1) First check if `template.thumbnailUrl` exists and use it in an `<img :src="template.thumbnailUrl">` tag, (2) If no thumbnailUrl, check for `template.previewSvg` and render it using `v-html` or a wrapper component, (3) If neither exists, show a placeholder image or icon (use `pi pi-image` from PrimeIcons).

*   **Warning:** Authentication is required to create calendars. The `createCalendar` mutation will fail if the user is not authenticated (returns error "Authentication required to create calendar"). Before calling `createCalendar`, you SHOULD check `authStore.isAuthenticated`. If the user is not authenticated, you should either: (1) Redirect them to the login page first, OR (2) Show a modal/dialog prompting them to log in, OR (3) Create a guest session (if that feature is implemented in I2.T9).

*   **Tip:** For responsive grid layouts, use CSS Grid with `grid-template-columns: repeat(auto-fit, minmax(300px, 1fr))`. This pattern will automatically adapt the grid to different screen sizes: 1 column on mobile (if viewport < 300px), 2 columns on tablet, 3+ columns on desktop. Add `gap: 1.5rem` for spacing between cards.

*   **Note:** PrimeVue Card component has three main slots: `#title` (for card header), `#subtitle` (for secondary text), and `#content` (for main body). You can also add a `#footer` slot for buttons. For the template card, use: `#title` for template name, `#subtitle` for short description, `#content` for thumbnail image, `#footer` for "Start from Template" button.

*   **Tip:** After creating a calendar successfully, you SHOULD redirect the user to the calendar editor. Check if there's an editor route in the router configuration. Based on the project structure, the route might be `/editor` or `/calendar/editor/:id`. Use Vue Router's `useRouter()` composable and call `router.push()` with the appropriate path after `createCalendar` succeeds.

*   **Warning:** The `createCalendar` mutation is async and may take a moment to complete. You MUST show a loading indicator on the "Start from Template" button while the mutation is in progress. Use PrimeVue Button's `loading` prop: `<Button :loading="isCreating" label="Start from Template" @click="handleCreateCalendar" />`. Set `isCreating` to `true` before calling the mutation and `false` afterward. Also disable the button during loading to prevent duplicate submissions: `:disabled="isCreating"`.

*   **Note:** For empty states (no templates available), follow the pattern from Dashboard.vue (lines 105-114): Show a PrimeIcons icon (`pi pi-inbox` or `pi pi-images`), a message ("No templates available" or "No calendar templates found"), and optionally an action button or link. Center the empty state content using flexbox: `display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 3rem 1rem;`.

*   **Tip:** Use PrimeVue Message component for displaying errors. Import it with `import Message from 'primevue/message'` and use: `<Message v-if="error" severity="error">{{ error }}</Message>`. The severity can be "error", "warn", "info", or "success". Place it above the template grid so users see errors immediately.

*   **Note:** The task description mentions filtering by category/year/holiday set for future expansion. For the MVP, you can skip implementing filters or add a simple search input that filters templates by name. Mark filter functionality as "TODO: Future expansion" in comments. Focus on getting the basic gallery with "Start from Template" working first to meet the acceptance criteria.

*   **Critical:** File paths in this project use `src/main/webui/src/` as the base for frontend code, NOT `frontend/src/`. The target files listed in the task use the `frontend/` prefix, but you MUST create files in `src/main/webui/src/` instead. The correct paths are:
    - `src/main/webui/src/view/TemplateGallery.vue` (NOT `frontend/src/views/TemplateGallery.vue`)
    - `src/main/webui/src/components/calendar/TemplateCard.vue` (correct, this directory exists)
    - DO NOT create `frontend/src/graphql/template-queries.ts` - the GraphQL queries are already in `templateService.ts`

*   **Router Configuration:** You MUST add a route for the template gallery. The routes are defined in `src/main/webui/src/router.ts` or `src/main/webui/src/navigation/routes.ts`. Add a route like:
    ```typescript
    {
      path: '/templates',
      name: 'templates',
      component: () => import('@/view/TemplateGallery.vue'),
      meta: { title: 'Browse Templates' }
    }
    ```

*   **Navigation Updates:** After creating the TemplateGallery route, update the Dashboard.vue "Browse Templates" button (line 191) to redirect to `/templates` instead of `/`. Also consider adding a navigation link in the AppHeader component so users can access the gallery from anywhere.

*   **Component Import Pattern:** In this project, PrimeVue components are imported individually (not auto-imported). Always use explicit imports: `import Card from 'primevue/card'`, `import Button from 'primevue/button'`, `import Message from 'primevue/message'`, etc. For icons, use the `pi pi-*` class names directly in templates: `<i class="pi pi-plus"></i>`.

*   **Error Handling Best Practice:** When calling `createCalendar`, wrap it in a try-catch block and show user-friendly error messages using PrimeVue Toast (not just console.error). Import toast with `import { useToast } from 'primevue/usetoast'` and show errors like: `toast.add({ severity: 'error', summary: 'Failed to create calendar', detail: error.message, life: 5000 })`.

*   **Loading State Best Practice:** While templates are loading (`templateStore.loading === true`), show a centered ProgressSpinner instead of the template grid. Import it with `import ProgressSpinner from 'primevue/progressspinner'` and use: `<div v-if="loading" class="flex justify-center items-center min-h-[400px]"><ProgressSpinner /></div>`.

*   **Year Selection:** When creating a calendar, the `createCalendar` mutation requires a `year` parameter. For the MVP, you can default to the current year or next year: `const currentYear = new Date().getFullYear()`. In a future iteration, you might add a year selector dialog before creating the calendar, but that's not required for this task.

*   **Template Card Styling:** Make template cards visually appealing with: (1) Aspect ratio 4:3 or 16:9 for thumbnail images (use CSS `aspect-ratio: 4 / 3`), (2) Hover effects (slight shadow increase, scale transform), (3) Clear typography hierarchy (name in bold, description in smaller text), (4) Prominent "Start from Template" button that stands out.

*   **Accessibility:** Ensure keyboard navigation works: Template cards should be keyboard-accessible (use proper button elements for "Start from Template"), add `alt` text to thumbnail images, ensure sufficient color contrast for text, use semantic HTML (use `<main>`, `<section>`, `<article>` tags appropriately).
