<template>
  <Dialog
    v-model:visible="dialogVisible"
    header="Select Emoji"
    :modal="true"
    :closable="true"
    :style="{ width: '90vw', maxWidth: '500px' }"
    @hide="handleClose"
  >
    <div class="emoji-picker-container">
      <emoji-picker @emoji-click="handleEmojiClick"></emoji-picker>
    </div>
  </Dialog>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import Dialog from "primevue/dialog";
import "emoji-picker-element";

interface Props {
  visible: boolean;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  "update:visible": [value: boolean];
  select: [emoji: string];
}>();

const dialogVisible = ref(props.visible);

// Sync dialogVisible with props
watch(
  () => props.visible,
  (newVal) => {
    dialogVisible.value = newVal;
  },
);

// Sync props with dialogVisible
watch(dialogVisible, (newVal) => {
  emit("update:visible", newVal);
});

/**
 * Handle emoji selection
 */
function handleEmojiClick(event: { detail: { unicode: string } }) {
  const emoji = event.detail.unicode;
  emit("select", emoji);
  dialogVisible.value = false;
}

/**
 * Handle dialog close
 */
function handleClose() {
  emit("update:visible", false);
}
</script>

<style scoped>
.emoji-picker-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}

/* Style the emoji-picker web component */
:deep(emoji-picker) {
  width: 100%;
  max-width: 450px;
  height: 400px;
  --border-color: #e5e7eb;
  --category-emoji-size: 1.5rem;
}
</style>
