<template>
  <div class="p-4">
    <h3 class="text-lg font-semibold mb-3">Preview</h3>
    <div class="border rounded p-2 bg-gray-50 min-h-[80px] flex flex-col">
      <!-- Preview of how the event will appear in a calendar cell -->
      <div
        class="flex gap-2"
        :class="{
          'flex-col items-center': localSettings.layout === 'vertical',
          'flex-row items-center': localSettings.layout === 'horizontal',
        }"
      >
        <!-- Emoji -->
        <div
          class="text-center"
          :style="{
            fontSize: localSettings.emojiSize + 'px',
          }"
        >
          {{ emoji }}
        </div>

        <!-- Title (if enabled) -->
        <div
          v-if="showTitle && title"
          class="font-medium"
          :style="{
            fontSize: localSettings.titleSize + 'px',
            color: localSettings.titleColor,
            textAlign: localSettings.titleAlign,
          }"
        >
          {{ title }}
        </div>
      </div>
    </div>

    <!-- Display Settings -->
    <div class="mt-4 space-y-3">
      <h4 class="font-semibold text-sm">Display Settings</h4>

      <!-- Layout -->
      <div>
        <label class="block text-sm mb-1">Layout</label>
        <div class="flex gap-2">
          <button
            type="button"
            class="px-3 py-1 text-sm border rounded"
            :class="
              localSettings.layout === 'horizontal'
                ? 'bg-blue-500 text-white border-blue-500'
                : 'bg-white border-gray-300'
            "
            @click="updateSetting('layout', 'horizontal')"
          >
            Horizontal
          </button>
          <button
            type="button"
            class="px-3 py-1 text-sm border rounded"
            :class="
              localSettings.layout === 'vertical'
                ? 'bg-blue-500 text-white border-blue-500'
                : 'bg-white border-gray-300'
            "
            @click="updateSetting('layout', 'vertical')"
          >
            Vertical
          </button>
        </div>
      </div>

      <!-- Emoji Size -->
      <div>
        <label class="block text-sm mb-1">
          Emoji Size: {{ localSettings.emojiSize }}px
        </label>
        <input
          type="range"
          min="16"
          max="48"
          v-model.number="localSettings.emojiSize"
          @input="emitUpdate"
          class="w-full"
        />
      </div>

      <!-- Title Settings (if title is shown) -->
      <div v-if="showTitle && title" class="space-y-2">
        <div>
          <label class="block text-sm mb-1">
            Title Size: {{ localSettings.titleSize }}px
          </label>
          <input
            type="range"
            min="10"
            max="20"
            v-model.number="localSettings.titleSize"
            @input="emitUpdate"
            class="w-full"
          />
        </div>

        <div>
          <label class="block text-sm mb-1">Title Color</label>
          <input
            type="color"
            v-model="localSettings.titleColor"
            @input="emitUpdate"
            class="w-full h-8"
          />
        </div>

        <div>
          <label class="block text-sm mb-1">Title Alignment</label>
          <div class="flex gap-2">
            <button
              type="button"
              class="px-3 py-1 text-sm border rounded"
              :class="
                localSettings.titleAlign === 'left'
                  ? 'bg-blue-500 text-white border-blue-500'
                  : 'bg-white border-gray-300'
              "
              @click="updateSetting('titleAlign', 'left')"
            >
              Left
            </button>
            <button
              type="button"
              class="px-3 py-1 text-sm border rounded"
              :class="
                localSettings.titleAlign === 'center'
                  ? 'bg-blue-500 text-white border-blue-500'
                  : 'bg-white border-gray-300'
              "
              @click="updateSetting('titleAlign', 'center')"
            >
              Center
            </button>
            <button
              type="button"
              class="px-3 py-1 text-sm border rounded"
              :class="
                localSettings.titleAlign === 'right'
                  ? 'bg-blue-500 text-white border-blue-500'
                  : 'bg-white border-gray-300'
              "
              @click="updateSetting('titleAlign', 'right')"
            >
              Right
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

interface DisplaySettings {
  layout: 'horizontal' | 'vertical'
  emojiSize: number
  titleSize: number
  titleColor: string
  titleAlign: 'left' | 'center' | 'right'
}

const props = defineProps<{
  emoji: string
  title?: string
  showTitle: boolean
  initialSettings?: Partial<DisplaySettings>
}>()

const emit = defineEmits<{
  (e: 'update:settings', settings: DisplaySettings): void
}>()

// Default settings
const defaultSettings: DisplaySettings = {
  layout: 'horizontal',
  emojiSize: 24,
  titleSize: 14,
  titleColor: '#000000',
  titleAlign: 'left',
}

// Local settings (merged with initial settings)
const localSettings = ref<DisplaySettings>({
  ...defaultSettings,
  ...props.initialSettings,
})

// Watch for changes to initial settings
watch(
  () => props.initialSettings,
  (newSettings) => {
    if (newSettings) {
      localSettings.value = {
        ...defaultSettings,
        ...newSettings,
      }
    }
  },
  { deep: true }
)

function updateSetting<K extends keyof DisplaySettings>(
  key: K,
  value: DisplaySettings[K]
) {
  localSettings.value[key] = value
  emitUpdate()
}

function emitUpdate() {
  emit('update:settings', { ...localSettings.value })
}
</script>

<style scoped>
/* Ensure inputs are properly styled */
input[type='range'] {
  cursor: pointer;
}

input[type='color'] {
  cursor: pointer;
  border-radius: 4px;
  border: 1px solid #d1d5db;
}

button {
  transition: all 0.2s;
}

button:hover {
  opacity: 0.9;
}
</style>
