<template>
  <Card class="template-card">
    <template #header>
      <div class="template-thumbnail">
        <img
          v-if="template.thumbnailUrl"
          :src="template.thumbnailUrl"
          :alt="template.name"
          class="thumbnail-image"
        />
        <div
          v-else-if="template.previewSvg"
          class="preview-svg-container"
          v-html="template.previewSvg"
        ></div>
        <div v-else class="placeholder-image">
          <i class="pi pi-image"></i>
        </div>
      </div>
    </template>

    <template #title>
      <div class="template-name">{{ template.name }}</div>
    </template>

    <template #subtitle>
      <div v-if="template.description" class="template-description">
        {{ truncateDescription(template.description) }}
      </div>
    </template>

    <template #footer>
      <Button
        label="Start from Template"
        icon="pi pi-plus"
        :loading="loading"
        :disabled="loading"
        class="start-button"
        @click="handleStartFromTemplate"
      />
    </template>
  </Card>
</template>

<script setup lang="ts">
import Card from "primevue/card";
import Button from "primevue/button";
import type { CalendarTemplate } from "@/types/template";

interface Props {
  template: CalendarTemplate;
  loading?: boolean;
}

interface Emits {
  (event: "start-from-template", templateId: string): void;
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
});

const emit = defineEmits<Emits>();

const truncateDescription = (description: string, maxLength: number = 120) => {
  if (description.length <= maxLength) return description;
  return description.substring(0, maxLength).trim() + "...";
};

const handleStartFromTemplate = () => {
  emit("start-from-template", props.template.id);
};
</script>

<style scoped>
.template-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  transition: all 0.3s ease;
  cursor: pointer;
  border: 1px solid #e5e7eb;
}

.template-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.12);
}

.template-thumbnail {
  width: 100%;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  background: #f9fafb;
  display: flex;
  align-items: center;
  justify-content: center;
}

.thumbnail-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.preview-svg-container {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-svg-container :deep(svg) {
  max-width: 100%;
  max-height: 100%;
}

.placeholder-image {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 4rem;
  color: #d1d5db;
}

.template-name {
  font-size: 1.125rem;
  font-weight: 600;
  color: #111827;
  line-height: 1.4;
}

.template-description {
  font-size: 0.875rem;
  color: #6b7280;
  line-height: 1.5;
  margin-top: 0.5rem;
}

.start-button {
  width: 100%;
}

/* Responsive adjustments */
@media (max-width: 768px) {
  .template-card {
    margin-bottom: 1rem;
  }

  .template-name {
    font-size: 1rem;
  }

  .template-description {
    font-size: 0.8125rem;
  }
}
</style>
