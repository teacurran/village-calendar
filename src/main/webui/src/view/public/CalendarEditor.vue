<template>
  <div class="calendar-editor">
    <div class="container">
      <!-- Loading state -->
      <div v-if="pageLoading" class="loading-container">
        <ProgressSpinner />
        <p class="loading-text">Loading editor...</p>
      </div>

      <!-- Main editor content -->
      <div v-else-if="template" class="editor-grid">
        <!-- Left: Editor Form -->
        <Card class="editor-form">
          <template #title>
            <div class="editor-header">
              <Button
                icon="pi pi-arrow-left"
                text
                @click="router.push('/')"
                label="Back"
              />
              <h2>Customize {{ template.name }}</h2>
            </div>
          </template>
          <template #content>
            <!-- Calendar Name -->
            <div class="form-group">
              <label for="calendar-name" class="form-label">Calendar Name</label>
              <InputText
                id="calendar-name"
                v-model="calendarName"
                placeholder="My 2025 Calendar"
                class="w-full"
              />
            </div>

            <!-- Year Selection -->
            <div class="form-group">
              <label for="calendar-year" class="form-label">Year</label>
              <Dropdown
                id="calendar-year"
                v-model="selectedYear"
                :options="availableYears"
                placeholder="Select Year"
                class="w-full"
              />
            </div>

            <!-- Configuration JSON Editor (for advanced users) -->
            <div class="form-group">
              <div class="flex justify-between items-center mb-2">
                <label class="form-label">Custom Configuration</label>
                <Button
                  label="Show Advanced"
                  text
                  size="small"
                  @click="showAdvanced = !showAdvanced"
                  :icon="showAdvanced ? 'pi pi-chevron-up' : 'pi pi-chevron-down'"
                />
              </div>
              <Textarea
                v-if="showAdvanced"
                v-model="configurationJson"
                rows="10"
                class="w-full font-mono text-sm"
                placeholder='{"customField": "value"}'
              />
              <Message v-if="configError" severity="error" :closable="false" class="mt-2">
                Invalid JSON: {{ configError }}
              </Message>
            </div>

            <!-- Public/Private Toggle -->
            <div class="form-group">
              <div class="flex items-center gap-2">
                <Checkbox
                  v-model="isPublic"
                  input-id="is-public"
                  binary
                />
                <label for="is-public">Make this calendar public (shareable link)</label>
              </div>
            </div>

            <!-- Action Buttons -->
            <div class="form-actions">
              <Button
                v-if="!currentCalendar"
                label="Create Calendar"
                icon="pi pi-plus"
                @click="createNewCalendar"
                :loading="calendarStore.loading"
                :disabled="!isFormValid"
              />
              <Button
                v-else
                label="Save Changes"
                icon="pi pi-save"
                @click="saveCalendar"
                :loading="calendarStore.loading"
                :disabled="!isFormValid"
              />
              <Button
                v-if="currentCalendar && currentCalendar.status === 'READY'"
                label="Order Calendar"
                icon="pi pi-shopping-cart"
                severity="success"
                @click="orderCalendar"
              />
            </div>

            <!-- PDF Generation Status -->
            <div v-if="currentCalendar" class="status-panel">
              <Divider />
              <div class="status-content">
                <div class="flex items-center gap-2">
                  <Tag :value="formatStatus(currentCalendar.status)" :severity="getStatusSeverity(currentCalendar.status)" />
                  <ProgressSpinner
                    v-if="isPolling || currentCalendar.status === 'GENERATING'"
                    style="width: 20px; height: 20px"
                  />
                </div>
                <p v-if="currentCalendar.status === 'GENERATING'" class="status-message">
                  PDF is being generated. This may take a minute...
                </p>
                <p v-else-if="currentCalendar.status === 'READY'" class="status-message success">
                  Your calendar is ready! You can preview it on the right or order it now.
                </p>
                <p v-else-if="currentCalendar.status === 'FAILED'" class="status-message error">
                  PDF generation failed. Please try again or contact support.
                </p>
                <Button
                  v-if="currentCalendar.status === 'READY' && currentCalendar.generatedPdfUrl"
                  label="Download PDF"
                  icon="pi pi-download"
                  outlined
                  size="small"
                  @click="downloadPDF"
                  class="mt-2"
                />
              </div>
            </div>
          </template>
        </Card>

        <!-- Right: Preview -->
        <Card class="preview-panel">
          <template #title>
            <div class="flex justify-between items-center">
              <h3>Preview</h3>
              <Button
                v-if="currentCalendar"
                label="Refresh"
                icon="pi pi-refresh"
                text
                size="small"
                @click="refreshPreview"
                :loading="refreshing"
              />
            </div>
          </template>
          <template #content>
            <CalendarPreview
              v-if="currentCalendar"
              ref="calendarPreviewRef"
              :calendar-id="currentCalendar.id"
              :configuration="customConfiguration"
              :auto-load="false"
            />
            <div v-else class="preview-placeholder">
              <i class="pi pi-calendar text-6xl text-gray-300"></i>
              <p class="text-gray-500 mt-4">Create a calendar to see preview</p>
            </div>
          </template>
        </Card>
      </div>

      <!-- Error state -->
      <div v-else class="error-container">
        <Message severity="error" :closable="false">
          Failed to load template. Please try again.
        </Message>
        <Button
          label="Go Back"
          icon="pi pi-arrow-left"
          @click="router.push('/')"
          class="mt-4"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import { useCalendarStore, type CalendarTemplate, type UserCalendar } from '../../stores/calendarStore'
import { useAuthStore } from '../../stores/authStore'
import { getAvailableYears, formatCalendarStatus, getStatusSeverity, downloadCalendarPDF } from '../../services/calendarService'
import CalendarPreview from '../../components/CalendarPreview.vue'
import Card from 'primevue/card'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Dropdown from 'primevue/dropdown'
import Textarea from 'primevue/textarea'
import Checkbox from 'primevue/checkbox'
import ProgressSpinner from 'primevue/progressspinner'
import Tag from 'primevue/tag'
import Message from 'primevue/message'
import Divider from 'primevue/divider'

const router = useRouter()
const route = useRoute()
const toast = useToast()
const calendarStore = useCalendarStore()
const authStore = useAuthStore()

// State
const pageLoading = ref(true)
const template = ref<CalendarTemplate | null>(null)
const currentCalendar = ref<UserCalendar | null>(null)
const calendarName = ref('')
const selectedYear = ref(new Date().getFullYear())
const configurationJson = ref('{}')
const configError = ref<string | null>(null)
const isPublic = ref(false)
const showAdvanced = ref(false)
const isPolling = ref(false)
const refreshing = ref(false)
const calendarPreviewRef = ref<InstanceType<typeof CalendarPreview> | null>(null)
const pollingInterval = ref<number | null>(null)

// Computed
const availableYears = computed(() => getAvailableYears())

const customConfiguration = computed(() => {
  try {
    const parsed = JSON.parse(configurationJson.value)
    configError.value = null
    return parsed
  } catch (err: any) {
    configError.value = err.message
    return null
  }
})

const isFormValid = computed(() => {
  return (
    calendarName.value.trim().length > 0 &&
    selectedYear.value &&
    !configError.value &&
    authStore.isAuthenticated
  )
})

// Methods
const formatStatus = (status: string) => formatCalendarStatus(status)

const createNewCalendar = async () => {
  if (!authStore.isAuthenticated) {
    toast.add({
      severity: 'warn',
      summary: 'Authentication Required',
      detail: 'Please log in to create a calendar',
      life: 3000,
    })
    authStore.initiateLogin('google')
    return
  }

  try {
    const calendar = await calendarStore.createCalendar({
      templateId: route.params.templateId as string,
      name: calendarName.value,
      year: selectedYear.value,
      configuration: customConfiguration.value || undefined,
      isPublic: isPublic.value,
    })

    currentCalendar.value = calendar
    toast.add({
      severity: 'success',
      summary: 'Calendar Created',
      detail: 'Your calendar is being generated',
      life: 3000,
    })

    // Start polling for PDF generation
    startPolling()
  } catch (error: any) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: error.message || 'Failed to create calendar',
      life: 3000,
    })
  }
}

const saveCalendar = async () => {
  if (!currentCalendar.value) return

  try {
    const updated = await calendarStore.updateCalendar(currentCalendar.value.id, {
      name: calendarName.value,
      configuration: customConfiguration.value || undefined,
      isPublic: isPublic.value,
    })

    currentCalendar.value = updated
    toast.add({
      severity: 'success',
      summary: 'Calendar Updated',
      detail: 'Your changes have been saved',
      life: 3000,
    })

    // Start polling if status changed to GENERATING
    if (updated.status === 'GENERATING') {
      startPolling()
    }
  } catch (error: any) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: error.message || 'Failed to save calendar',
      life: 3000,
    })
  }
}

const startPolling = () => {
  if (isPolling.value || !currentCalendar.value) return

  isPolling.value = true

  // Poll every 2 seconds
  pollingInterval.value = window.setInterval(async () => {
    try {
      const calendar = await calendarStore.fetchCalendar(currentCalendar.value.id)
      currentCalendar.value = calendar

      // Stop polling if status is READY or FAILED
      if (calendar.status === 'READY') {
        stopPolling()
        toast.add({
          severity: 'success',
          summary: 'Calendar Ready',
          detail: 'Your calendar PDF is ready!',
          life: 3000,
        })
        refreshPreview()
      } else if (calendar.status === 'FAILED') {
        stopPolling()
        toast.add({
          severity: 'error',
          summary: 'Generation Failed',
          detail: 'Failed to generate calendar PDF',
          life: 3000,
        })
      }
    } catch (error) {
      console.error('Error polling calendar status:', error)
    }
  }, 2000)

  // Set timeout to stop polling after 60 seconds
  setTimeout(() => {
    if (isPolling.value) {
      stopPolling()
      toast.add({
        severity: 'warn',
        summary: 'Generation Timeout',
        detail: 'Calendar generation is taking longer than expected. Please check back later.',
        life: 5000,
      })
    }
  }, 60000)
}

const stopPolling = () => {
  if (pollingInterval.value) {
    clearInterval(pollingInterval.value)
    pollingInterval.value = null
  }
  isPolling.value = false
}

const refreshPreview = async () => {
  if (!currentCalendar.value || !calendarPreviewRef.value) return

  refreshing.value = true
  try {
    await calendarPreviewRef.value.fetchCalendarSvg(currentCalendar.value.id)
  } catch (error: any) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to refresh preview',
      life: 3000,
    })
  } finally {
    refreshing.value = false
  }
}

const downloadPDF = () => {
  if (!currentCalendar.value?.generatedPdfUrl) return
  downloadCalendarPDF(currentCalendar.value.generatedPdfUrl, calendarName.value)
}

const orderCalendar = () => {
  if (!currentCalendar.value) return
  router.push(`/checkout/${currentCalendar.value.id}`)
}

// Initialize
onMounted(async () => {
  try {
    // Check authentication
    if (!authStore.isAuthenticated) {
      toast.add({
        severity: 'info',
        summary: 'Login Required',
        detail: 'Please log in to customize a calendar',
        life: 3000,
      })
      setTimeout(() => {
        authStore.initiateLogin('google')
      }, 1000)
      return
    }

    const templateId = route.params.templateId as string
    template.value = await calendarStore.fetchTemplate(templateId)

    if (!template.value) {
      throw new Error('Template not found')
    }

    // Set default values
    calendarName.value = `${template.value.name} ${selectedYear.value}`
    configurationJson.value = JSON.stringify(template.value.configuration || {}, null, 2)
  } catch (error: any) {
    console.error('Error loading editor:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: error.message || 'Failed to load editor',
      life: 3000,
    })
  } finally {
    pageLoading.value = false
  }
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped>
.calendar-editor {
  min-height: 100vh;
  background: #f9fafb;
  padding: 2rem 0;
}

.container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 1rem;
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  padding: 4rem 0;
}

.loading-text {
  color: #6b7280;
}

.error-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 2rem;
}

.editor-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2rem;
  align-items: start;
}

@media (max-width: 1024px) {
  .editor-grid {
    grid-template-columns: 1fr;
  }
}

.editor-header {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.editor-header h2 {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 600;
}

.form-group {
  margin-bottom: 1.5rem;
}

.form-label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 500;
  color: #374151;
}

.form-actions {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
  margin-top: 2rem;
}

.status-panel {
  margin-top: 2rem;
}

.status-content {
  padding: 1rem 0;
}

.status-message {
  margin-top: 0.5rem;
  color: #6b7280;
  font-size: 0.875rem;
}

.status-message.success {
  color: #059669;
}

.status-message.error {
  color: #dc2626;
}

.preview-panel {
  position: sticky;
  top: 2rem;
}

.preview-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem;
  background: #f3f4f6;
  border-radius: 8px;
  min-height: 400px;
}

.font-mono {
  font-family: 'Monaco', 'Menlo', 'Courier New', monospace;
}
</style>
