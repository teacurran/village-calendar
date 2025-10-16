<template>
  <div class="calendar-preview-wrapper">
    <div v-if="loading" class="preview-loading">
      <ProgressSpinner />
      <p class="loading-text">Generating preview...</p>
    </div>
    <div v-else-if="error" class="preview-error">
      <Message severity="error" :closable="false">
        {{ error }}
      </Message>
    </div>
    <div v-else-if="svgContent" class="preview-content" v-html="svgContent"></div>
    <div v-else class="preview-placeholder">
      <i class="pi pi-image text-6xl text-gray-300"></i>
      <p class="text-gray-500 mt-2">No preview available</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import ProgressSpinner from 'primevue/progressspinner'
import Message from 'primevue/message'

export interface CalendarPreviewProps {
  calendarId?: string
  configuration?: any
  autoLoad?: boolean
  scaleToFit?: boolean
}

const props = withDefaults(defineProps<CalendarPreviewProps>(), {
  autoLoad: true,
  scaleToFit: true,
})

const svgContent = ref('')
const loading = ref(false)
const error = ref<string | null>(null)

/**
 * Generate preview from configuration
 * Note: This is a placeholder for client-side preview generation.
 * In production, you might want to use a GraphQL mutation to generate
 * server-side previews if the configuration is complex.
 */
const generatePreview = async () => {
  if (!props.configuration) {
    return
  }

  loading.value = true
  error.value = null

  try {
    // For now, show a placeholder since we don't have a REST endpoint
    // In a real implementation, you would either:
    // 1. Generate SVG client-side (if you have the template rendering logic)
    // 2. Call a GraphQL mutation to generate preview server-side
    // 3. Use the calendar's generatedSvg field if it's already been created

    error.value = 'Preview generation from configuration is not yet implemented. Please save your calendar first to see a preview.'

    // Placeholder implementation - you could add client-side SVG generation here
    // or call a GraphQL mutation like:
    // mutation GeneratePreview($config: JSON!) {
    //   generatePreview(configuration: $config)
    // }

  } catch (err: any) {
    error.value = err.message || 'Failed to generate preview'
    console.error('Error generating preview:', err)
  } finally {
    loading.value = false
  }
}

/**
 * Scale SVG to fit container while maintaining aspect ratio
 */
const scaleSvg = (svg: string): string => {
  const viewBoxMatch = svg.match(/viewBox="([^"]+)"/)?.[1]
  if (viewBoxMatch) {
    // Add responsive styles to SVG
    return svg.replace(
      /<svg/,
      '<svg style="max-width: 100%; height: auto; display: block; margin: 0 auto;"'
    )
  }
  return svg
}

/**
 * Watch for configuration changes and regenerate preview
 */
watch(
  () => props.configuration,
  () => {
    if (props.autoLoad && props.configuration) {
      generatePreview()
    }
  },
  { deep: true }
)

/**
 * Watch for calendarId changes
 */
watch(
  () => props.calendarId,
  async (newId) => {
    if (newId && props.autoLoad) {
      // Fetch calendar SVG from backend if calendarId is provided
      await fetchCalendarSvg(newId)
    }
  }
)

/**
 * Fetch calendar SVG by ID using GraphQL
 */
const fetchCalendarSvg = async (calendarId: string) => {
  loading.value = true
  error.value = null

  try {
    const response = await fetch('/graphql', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        query: `
          query GetCalendarPreview($id: ID!) {
            calendar(id: $id) {
              id
              generatedSvg
              status
            }
          }
        `,
        variables: { id: calendarId },
      }),
    })

    if (!response.ok) {
      throw new Error('Failed to fetch calendar preview')
    }

    const result = await response.json()

    if (result.errors) {
      throw new Error(result.errors[0]?.message || 'Failed to fetch calendar')
    }

    const calendar = result.data?.calendar

    if (!calendar) {
      throw new Error('Calendar not found')
    }

    if (calendar.status === 'GENERATING') {
      error.value = 'Calendar is still being generated. Please wait...'
      return
    }

    if (calendar.status === 'FAILED') {
      error.value = 'Calendar generation failed. Please try regenerating.'
      return
    }

    let svg = calendar.generatedSvg

    if (!svg) {
      error.value = 'No preview available yet. The calendar may still be generating.'
      return
    }

    if (props.scaleToFit) {
      svg = scaleSvg(svg)
    }

    svgContent.value = svg
  } catch (err: any) {
    error.value = err.message || 'Failed to fetch calendar preview'
    console.error('Error fetching calendar preview:', err)
  } finally {
    loading.value = false
  }
}

/**
 * Load preview on mount if autoLoad is enabled
 */
onMounted(() => {
  if (props.autoLoad) {
    if (props.calendarId) {
      fetchCalendarSvg(props.calendarId)
    } else if (props.configuration) {
      generatePreview()
    }
  }
})

/**
 * Expose methods to parent component
 */
defineExpose({
  generatePreview,
  fetchCalendarSvg,
})
</script>

<style scoped>
.calendar-preview-wrapper {
  width: 100%;
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f9fafb;
  border-radius: 8px;
  overflow: hidden;
}

.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  padding: 2rem;
}

.loading-text {
  color: #6b7280;
  font-size: 0.875rem;
}

.preview-error {
  padding: 2rem;
  width: 100%;
}

.preview-content {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
}

.preview-content :deep(svg) {
  max-width: 100%;
  height: auto;
  display: block;
}

.preview-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem;
  color: #9ca3af;
}
</style>
