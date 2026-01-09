<template>
  <Popover
    ref="popoverRef"
    appendTo="self"
    :pt="{ root: { class: 'inline-emoji-popover' } }"
  >
    <div class="emoji-picker-content">
      <!-- Loading state -->
      <div v-if="loading" class="emoji-loading">
        <span>Loading...</span>
      </div>

      <!-- Emoji grid (flat list of available emojis) -->
      <div v-else class="emoji-grid">
        <button
          v-for="emoji in availableEmojis"
          :key="emoji"
          class="emoji-btn"
          @click="selectEmoji(emoji)"
        >
          {{ emoji }}
        </button>
      </div>
    </div>
  </Popover>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import Popover from "primevue/popover";

const emit = defineEmits<{
  select: [emoji: string];
}>();

const popoverRef = ref();
const loading = ref(true);
const availableEmojis = ref<string[]>([]);

// Fetch available emojis from API on mount
onMounted(async () => {
  try {
    const response = await fetch("/api/calendar/available-emojis");
    if (response.ok) {
      const emojis = await response.json();
      // Sort emojis for consistent display order
      availableEmojis.value = Array.from(emojis as Set<string>).sort();
    }
  } catch (error) {
    console.error("Failed to fetch available emojis:", error);
  } finally {
    loading.value = false;
  }
});

const selectEmoji = (emoji: string) => {
  emit("select", emoji);
  popoverRef.value?.hide();
};

// Expose toggle method for parent to call
const toggle = (event: Event) => {
  popoverRef.value?.toggle(event);
};

const hide = () => {
  popoverRef.value?.hide();
};

defineExpose({ toggle, hide });
</script>

<style scoped>
.emoji-picker-content {
  width: 280px;
  max-height: 320px;
  display: flex;
  flex-direction: column;
}

.emoji-loading {
  padding: 20px;
  text-align: center;
  color: var(--text-color-secondary);
}

.emoji-grid {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 2px;
  padding: 8px;
  overflow-y: auto;
  max-height: 300px;
}

.emoji-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.emoji-btn:hover {
  background: var(--surface-100);
  transform: scale(1.15);
}
</style>

<style>
/* Unscoped styles for popover positioning */
.inline-emoji-popover {
  /* Must be higher than PrimeVue Drawer (z-index ~1100) */
  z-index: 2000 !important;
  /* Ensure it doesn't overflow right edge */
  max-width: 290px;
}

.inline-emoji-popover .p-popover-content {
  padding: 0 !important;
}

/* Position popover to stay centered within drawer bounds */
.inline-emoji-popover.p-popover {
  /* Center within drawer (drawer is 460px, content ~420px after padding) */
  left: 50% !important;
  right: auto !important;
  transform: translateX(-50%) !important;
}

/* Hide arrow since positioning is customized */
.inline-emoji-popover::before,
.inline-emoji-popover::after {
  display: none !important;
}
</style>
