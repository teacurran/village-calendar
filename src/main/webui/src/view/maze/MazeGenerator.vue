<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import Button from 'primevue/button'
import ProgressSpinner from 'primevue/progressspinner'
import Toolbar from 'primevue/toolbar'
import ToggleButton from 'primevue/togglebutton'
import MazeWizardDrawer from '../../components/maze/MazeWizardDrawer.vue'

// PRINT DIMENSIONS (35" x 23" at 100 units per inch)
const PAGE_WIDTH_INCHES = 35
const PAGE_HEIGHT_INCHES = 23
const UNITS_PER_INCH = 100
const PAGE_WIDTH = PAGE_WIDTH_INCHES * UNITS_PER_INCH // 3500
const PAGE_HEIGHT = PAGE_HEIGHT_INCHES * UNITS_PER_INCH // 2300

// Maze configuration
const config = ref({
  mazeType: 'ORTHOGONAL',
  size: 10,           // 1-20: controls cell count
  difficulty: 3,      // 1-5: controls shortcuts (1=easy/many, 5=hard/none)
  showSolution: false,
  innerWallColor: '#000000',
  outerWallColor: '#000000',
  pathColor: '#4CAF50',
})

// UI State
const mazeSvg = ref('')
const loading = ref(false)
const error = ref('')
const showWizard = ref(false)
const showRulers = ref(true)
const zoomLevel = ref(0.25)
const previewContainer = ref<HTMLElement | null>(null)

// Session management
const getSessionId = () => {
  let sessionId = localStorage.getItem('maze_session_id')
  if (!sessionId) {
    sessionId = 'session_' + Math.random().toString(36).substring(2, 15)
    localStorage.setItem('maze_session_id', sessionId)
  }
  return sessionId
}

// Generate maze preview (deterministic seed for preview)
const generatePreview = async () => {
  loading.value = true
  error.value = ''

  try {
    const query = `
      query MazePreview($type: MazeType!, $size: Int!, $difficulty: Int!, $showSolution: Boolean!) {
        mazePreview(type: $type, size: $size, difficulty: $difficulty, showSolution: $showSolution)
      }
    `

    const response = await fetch('/graphql', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        query,
        variables: {
          type: config.value.mazeType,
          size: config.value.size,
          difficulty: config.value.difficulty,
          showSolution: config.value.showSolution
        }
      })
    })

    const result = await response.json()

    if (result.errors) {
      throw new Error(result.errors[0].message)
    }

    mazeSvg.value = result.data.mazePreview
  } catch (e: any) {
    error.value = e.message || 'Failed to generate maze'
    console.error('Maze generation error:', e)
  } finally {
    loading.value = false
  }
}

// Create a new maze (with random seed)
const createMaze = async () => {
  loading.value = true
  error.value = ''

  try {
    const mutation = `
      mutation CreateMaze($input: MazeInput!) {
        createMaze(input: $input) {
          id
          name
          generatedSvg
        }
      }
    `

    const response = await fetch('/graphql', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        query: mutation,
        variables: {
          input: {
            name: 'My Maze',
            mazeType: config.value.mazeType,
            size: config.value.size,
            difficulty: config.value.difficulty,
            showSolution: config.value.showSolution,
            innerWallColor: config.value.innerWallColor,
            outerWallColor: config.value.outerWallColor,
            pathColor: config.value.pathColor,
            sessionId: getSessionId()
          }
        }
      })
    })

    const result = await response.json()

    if (result.errors) {
      throw new Error(result.errors[0].message)
    }

    mazeSvg.value = result.data.createMaze.generatedSvg
  } catch (e: any) {
    error.value = e.message || 'Failed to create maze'
    console.error('Maze creation error:', e)
  } finally {
    loading.value = false
  }
}

// Zoom controls
const zoomIn = () => {
  zoomLevel.value = Math.min(zoomLevel.value + 0.05, 1)
}

const zoomOut = () => {
  zoomLevel.value = Math.max(zoomLevel.value - 0.05, 0.1)
}

const resetZoom = () => {
  // Calculate zoom to fill the available width
  if (previewContainer.value) {
    const containerWidth = previewContainer.value.clientWidth - 80 // Account for rulers/padding
    const calculatedZoom = Math.max(0.15, Math.min(containerWidth / PAGE_WIDTH, 1.0))
    zoomLevel.value = calculatedZoom
  } else {
    zoomLevel.value = 0.25
  }
}

// Wizard event handlers
const handleMazeTypeChange = (type: string) => {
  config.value.mazeType = type
  generatePreview()
}

const handleConfigChange = (newConfig: { size: number; difficulty: number; showSolution: boolean }) => {
  config.value.size = newConfig.size
  config.value.difficulty = newConfig.difficulty
  config.value.showSolution = newConfig.showSolution
  generatePreview()
}

const handleColorsChange = (colors: { innerWallColor: string; outerWallColor: string; pathColor: string }) => {
  config.value.innerWallColor = colors.innerWallColor
  config.value.outerWallColor = colors.outerWallColor
  config.value.pathColor = colors.pathColor
}

// Download PDF placeholder
const downloadPdf = () => {
  // TODO: Implement PDF download
  alert('PDF download coming soon!')
}

// Keyboard shortcuts
const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === '+' || e.key === '=') {
    zoomIn()
  } else if (e.key === '-') {
    zoomOut()
  } else if (e.key === '0') {
    resetZoom()
  }
}

// Generate initial preview on mount
onMounted(() => {
  generatePreview()
  resetZoom()
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
})

// Watch for window resize to adjust zoom
let resizeTimeout: ReturnType<typeof setTimeout>
const handleResize = () => {
  clearTimeout(resizeTimeout)
  resizeTimeout = setTimeout(() => {
    resetZoom()
  }, 100)
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<template>
  <div class="maze-generator">
    <!-- Wizard Drawer -->
    <MazeWizardDrawer
      v-model:visible="showWizard"
      :config="config"
      @maze-type-change="handleMazeTypeChange"
      @config-change="handleConfigChange"
      @colors-change="handleColorsChange"
    />

    <!-- Toolbar -->
    <Toolbar class="maze-toolbar">
      <template #start>
        <Button
          icon="pi pi-palette"
          label="Customize"
          severity="secondary"
          outlined
          @click="showWizard = true"
        />
        <div class="toolbar-divider" />
        <Button
          icon="pi pi-refresh"
          label="Generate New"
          @click="createMaze"
          :loading="loading"
        />
      </template>

      <template #center>
        <div class="zoom-controls">
          <Button
            icon="pi pi-minus"
            text
            rounded
            size="small"
            @click="zoomOut"
            v-tooltip.bottom="'Zoom Out (-)'"
          />
          <span class="zoom-level">{{ Math.round(zoomLevel * 100) }}%</span>
          <Button
            icon="pi pi-plus"
            text
            rounded
            size="small"
            @click="zoomIn"
            v-tooltip.bottom="'Zoom In (+)'"
          />
          <Button
            icon="pi pi-arrows-alt"
            text
            rounded
            size="small"
            @click="resetZoom"
            v-tooltip.bottom="'Fit to Window (0)'"
          />
          <div class="toolbar-divider" />
          <ToggleButton
            v-model="showRulers"
            on-icon="pi pi-eye"
            off-icon="pi pi-eye-slash"
            on-label=""
            off-label=""
            v-tooltip.bottom="'Toggle Rulers'"
            class="ruler-toggle"
          />
        </div>
      </template>

      <template #end>
        <Button
          icon="pi pi-download"
          label="Download PDF"
          severity="success"
          @click="downloadPdf"
        />
      </template>
    </Toolbar>

    <!-- Preview Area -->
    <div ref="previewContainer" class="maze-preview">
      <div v-if="loading" class="loading-overlay">
        <ProgressSpinner />
        <p>Generating maze...</p>
      </div>

      <div v-else-if="error" class="error-message">
        <i class="pi pi-exclamation-triangle"></i>
        <p>{{ error }}</p>
        <Button label="Try Again" icon="pi pi-refresh" @click="generatePreview" />
      </div>

      <div
        v-else-if="mazeSvg"
        class="ruler-wrapper"
        :class="{ 'show-rulers': showRulers }"
      >
        <!-- Scaled container -->
        <div
          class="scaled-wrapper"
          :style="{
            width: `${PAGE_WIDTH * zoomLevel}px`,
            height: `${PAGE_HEIGHT * zoomLevel}px`,
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
              <div v-html="mazeSvg"></div>
            </div>
          </div>
        </div>

        <!-- Bottom ruler (35 inches) -->
        <div v-if="showRulers" class="ruler ruler-bottom">
          <div
            v-for="inch in PAGE_WIDTH_INCHES"
            :key="'b' + inch"
            class="ruler-mark"
            :style="{ width: `${UNITS_PER_INCH * zoomLevel}px` }"
          >
            <span class="ruler-label">{{ inch }}"</span>
          </div>
        </div>

        <!-- Right ruler (23 inches) -->
        <div v-if="showRulers" class="ruler ruler-right">
          <div
            v-for="inch in PAGE_HEIGHT_INCHES"
            :key="'r' + inch"
            class="ruler-mark"
            :style="{ height: `${UNITS_PER_INCH * zoomLevel}px` }"
          >
            <span class="ruler-label">{{ inch }}"</span>
          </div>
        </div>
      </div>

      <div v-else class="empty-state">
        <i class="pi pi-th-large"></i>
        <p>Configure your maze and click "Generate New" to create one</p>
        <Button label="Open Configurator" icon="pi pi-palette" @click="showWizard = true" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.maze-generator {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f5f5f5;
}

/* Toolbar */
.maze-toolbar {
  flex-shrink: 0;
  border-bottom: 1px solid #e0e0e0;
  background: white;
}

.toolbar-divider {
  width: 1px;
  height: 24px;
  background: #e0e0e0;
  margin: 0 0.75rem;
}

.zoom-controls {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.zoom-level {
  min-width: 50px;
  text-align: center;
  font-size: 0.875rem;
  font-weight: 500;
  color: #666;
}

.ruler-toggle {
  border: none;
}

/* Preview Area */
.maze-preview {
  flex: 1;
  overflow: auto;
  padding: 2rem;
  display: flex;
  justify-content: center;
  background: #e8e8e8;
}

.loading-overlay,
.error-message,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  color: #666;
  text-align: center;
  min-height: 400px;
}

.loading-overlay p,
.error-message p,
.empty-state p {
  margin: 0;
}

.error-message {
  color: #dc3545;
}

.error-message i {
  font-size: 3rem;
}

.empty-state i {
  font-size: 4rem;
  color: #ccc;
}

/* Ruler and page styles */
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

.svg-container {
  transition: transform 0.3s ease;
  display: inline-block;
}

.page-border {
  display: inline-block;
  border: 1px solid #ccc;
  border-right: 2px solid #999;
  border-bottom: 2px solid #999;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
  background: white;
}

:deep(.svg-container svg) {
  max-width: none;
  height: auto;
  display: block;
}

/* Rulers */
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

/* Responsive */
@media (max-width: 768px) {
  .maze-toolbar :deep(.p-toolbar-start),
  .maze-toolbar :deep(.p-toolbar-end) {
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .zoom-controls {
    flex-wrap: wrap;
    justify-content: center;
  }

  .maze-preview {
    padding: 1rem;
  }
}
</style>
