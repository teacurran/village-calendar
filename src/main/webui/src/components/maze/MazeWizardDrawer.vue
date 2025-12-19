<script setup lang="ts">
import { ref, computed, watch, onMounted } from "vue";
import Drawer from "primevue/drawer";
import Stepper from "primevue/stepper";
import StepItem from "primevue/stepitem";
import Step from "primevue/step";
import StepPanel from "primevue/steppanel";
import Button from "primevue/button";
import Slider from "primevue/slider";
import SelectButton from "primevue/selectbutton";
import ToggleButton from "primevue/togglebutton";
import ProgressSpinner from "primevue/progressspinner";
import { VSwatches } from "vue3-swatches";
import "vue3-swatches/dist/style.css";

// Props
interface MazeConfig {
  mazeType?: string;
  size?: number;
  difficulty?: number;
  showSolution?: boolean;
  innerWallColor?: string;
  outerWallColor?: string;
  pathColor?: string;
}

interface Props {
  visible: boolean;
  config?: MazeConfig;
}

const props = defineProps<Props>();

// Emits
const emit = defineEmits<{
  (e: "update:visible", value: boolean): void;
  (e: "mazeTypeChange", type: string): void;
  (
    e: "configChange",
    config: { size: number; difficulty: number; showSolution: boolean },
  ): void;
  (
    e: "colorsChange",
    colors: {
      innerWallColor: string;
      outerWallColor: string;
      pathColor: string;
    },
  ): void;
}>();

// Types
export type MazeTypeId = "ORTHOGONAL" | "DELTA" | "SIGMA" | "THETA";

export interface MazeTypeOption {
  id: MazeTypeId;
  name: string;
  description: string;
  available: boolean;
}

// State
const activeStep = ref<string>("1");
const selectedMazeType = ref<MazeTypeId>("ORTHOGONAL");
const size = ref(10);
const difficulty = ref(3); // 1-5 scale, 3 = medium
const showSolution = ref(false);
const innerWallColor = ref("#000000");
const outerWallColor = ref("#000000");
const pathColor = ref("#4CAF50");
const loadingPreviews = ref(false);
const typePreviews = ref<Record<string, string>>({});

// Difficulty options for SelectButton
const difficultyOptions = [
  { label: "1", value: 1 },
  { label: "2", value: 2 },
  { label: "3", value: 3 },
  { label: "4", value: 4 },
  { label: "5", value: 5 },
];

// Computed labels for sliders
const sizeLabel = computed(() => {
  if (size.value <= 5) return "Few large passages";
  if (size.value <= 10) return "Medium complexity";
  if (size.value <= 15) return "Many passages";
  return "Maximum complexity";
});

const difficultyLabel = computed(() => {
  switch (difficulty.value) {
    case 1:
      return "Very easy - many shortcuts";
    case 2:
      return "Easy - some shortcuts";
    case 3:
      return "Medium - standard maze";
    case 4:
      return "Hard - fewer shortcuts";
    case 5:
      return "Very hard - no shortcuts";
    default:
      return "Standard maze";
  }
});

// Computed
const isOpen = computed({
  get: () => props.visible,
  set: (value) => emit("update:visible", value),
});

// Maze type options
const mazeTypeOptions: MazeTypeOption[] = [
  {
    id: "ORTHOGONAL",
    name: "Orthogonal (Rectangular)",
    description:
      "Standard rectangular grid with right-angle passages. The classic maze style.",
    available: true,
  },
  {
    id: "DELTA",
    name: "Delta (Triangular)",
    description:
      "Interlocking triangles creating a tessellated pattern with up to 3 passages per cell.",
    available: false,
  },
  {
    id: "SIGMA",
    name: "Sigma (Hexagonal)",
    description:
      "Honeycomb pattern with hexagonal cells allowing up to 6 passages per cell.",
    available: false,
  },
  {
    id: "THETA",
    name: "Theta (Circular)",
    description:
      "Concentric circles radiating from center to edge, like a labyrinth.",
    available: false,
  },
];

// Color swatches
const colorSwatches = [
  "#000000",
  "#333333",
  "#666666",
  "#999999",
  "#CCCCCC",
  "#8B0000",
  "#DC143C",
  "#FF6347",
  "#FF8C00",
  "#FFD700",
  "#006400",
  "#228B22",
  "#4CAF50",
  "#90EE90",
  "#00CED1",
  "#00008B",
  "#0000FF",
  "#4169E1",
  "#87CEEB",
  "#9932CC",
  "#4B0082",
  "#8B4513",
  "#D2691E",
  "#F4A460",
  "#FFFFFF",
];

// Generate static SVG thumbnails for each maze type
const generateTypeThumbnail = (type: MazeTypeId): string => {
  const size = 120;
  const strokeWidth = 2;

  switch (type) {
    case "ORTHOGONAL":
      // Draw a simple rectangular maze preview
      return `<svg viewBox="0 0 ${size} ${size}" xmlns="http://www.w3.org/2000/svg">
        <rect width="${size}" height="${size}" fill="#f9fafb"/>
        <g stroke="#333" stroke-width="${strokeWidth}" fill="none">
          <rect x="10" y="10" width="100" height="100"/>
          <line x1="10" y1="30" x2="50" y2="30"/>
          <line x1="70" y1="30" x2="110" y2="30"/>
          <line x1="30" y1="30" x2="30" y2="50"/>
          <line x1="50" y1="50" x2="90" y2="50"/>
          <line x1="70" y1="50" x2="70" y2="70"/>
          <line x1="10" y1="70" x2="50" y2="70"/>
          <line x1="90" y1="70" x2="90" y2="110"/>
          <line x1="30" y1="90" x2="70" y2="90"/>
          <line x1="50" y1="70" x2="50" y2="90"/>
        </g>
        <circle cx="20" cy="20" r="4" fill="#4CAF50"/>
        <circle cx="100" cy="100" r="4" fill="#F44336"/>
      </svg>`;

    case "DELTA":
      // Draw triangular tessellation preview
      return `<svg viewBox="0 0 ${size} ${size}" xmlns="http://www.w3.org/2000/svg">
        <rect width="${size}" height="${size}" fill="#f9fafb"/>
        <g stroke="#333" stroke-width="${strokeWidth}" fill="none">
          <polygon points="60,15 20,95 100,95"/>
          <polygon points="60,15 100,95 120,15"/>
          <polygon points="60,15 0,15 20,95"/>
          <line x1="40" y1="55" x2="80" y2="55"/>
          <line x1="60" y1="15" x2="60" y2="55"/>
        </g>
        <circle cx="60" cy="25" r="4" fill="#4CAF50"/>
        <circle cx="60" cy="85" r="4" fill="#F44336"/>
        <text x="60" y="115" text-anchor="middle" font-size="10" fill="#999">Coming Soon</text>
      </svg>`;

    case "SIGMA":
      // Draw hexagonal tessellation preview
      return `<svg viewBox="0 0 ${size} ${size}" xmlns="http://www.w3.org/2000/svg">
        <rect width="${size}" height="${size}" fill="#f9fafb"/>
        <g stroke="#333" stroke-width="${strokeWidth}" fill="none">
          <polygon points="60,10 95,30 95,70 60,90 25,70 25,30"/>
          <polygon points="60,90 95,70 95,110 60,130 25,110 25,70" transform="translate(0,-40)"/>
          <line x1="60" y1="50" x2="95" y2="30"/>
          <line x1="25" y1="70" x2="60" y2="50"/>
        </g>
        <circle cx="60" cy="20" r="4" fill="#4CAF50"/>
        <circle cx="60" cy="80" r="4" fill="#F44336"/>
        <text x="60" y="115" text-anchor="middle" font-size="10" fill="#999">Coming Soon</text>
      </svg>`;

    case "THETA":
      // Draw concentric circles preview
      return `<svg viewBox="0 0 ${size} ${size}" xmlns="http://www.w3.org/2000/svg">
        <rect width="${size}" height="${size}" fill="#f9fafb"/>
        <g stroke="#333" stroke-width="${strokeWidth}" fill="none">
          <circle cx="60" cy="60" r="45"/>
          <circle cx="60" cy="60" r="30"/>
          <circle cx="60" cy="60" r="15"/>
          <line x1="60" y1="15" x2="60" y2="30"/>
          <line x1="60" y1="45" x2="60" y2="60"/>
          <line x1="90" y1="60" x2="105" y2="60"/>
          <line x1="30" y1="60" x2="45" y2="60"/>
        </g>
        <circle cx="60" cy="60" r="4" fill="#4CAF50"/>
        <circle cx="60" cy="15" r="4" fill="#F44336"/>
        <text x="60" y="115" text-anchor="middle" font-size="10" fill="#999">Coming Soon</text>
      </svg>`;

    default:
      return "";
  }
};

// Load type previews
const loadTypePreviews = () => {
  loadingPreviews.value = true;
  const previews: Record<string, string> = {};

  for (const type of mazeTypeOptions) {
    previews[type.id] = generateTypeThumbnail(type.id);
  }

  typePreviews.value = previews;
  loadingPreviews.value = false;
};

// Methods
const selectMazeType = (typeId: MazeTypeId) => {
  const option = mazeTypeOptions.find((t) => t.id === typeId);
  if (option?.available) {
    selectedMazeType.value = typeId;
    emit("mazeTypeChange", typeId);
  }
};

const emitConfigChange = () => {
  emit("configChange", {
    size: size.value,
    difficulty: difficulty.value,
    showSolution: showSolution.value,
  });
};

const emitColorsChange = () => {
  emit("colorsChange", {
    innerWallColor: innerWallColor.value,
    outerWallColor: outerWallColor.value,
    pathColor: pathColor.value,
  });
};

const handleClose = () => {
  isOpen.value = false;
};

// Initialize from config
const initializeFromConfig = () => {
  if (!props.config) return;

  if (props.config.mazeType) {
    selectedMazeType.value = props.config.mazeType as MazeTypeId;
  }
  if (props.config.size) {
    size.value = props.config.size;
  }
  if (props.config.difficulty) {
    difficulty.value = props.config.difficulty;
  }
  if (props.config.showSolution !== undefined) {
    showSolution.value = props.config.showSolution;
  }
  if (props.config.innerWallColor) {
    innerWallColor.value = props.config.innerWallColor;
  }
  if (props.config.outerWallColor) {
    outerWallColor.value = props.config.outerWallColor;
  }
  if (props.config.pathColor) {
    pathColor.value = props.config.pathColor;
  }
};

// Watch for config changes
watch([size, difficulty, showSolution], () => {
  emitConfigChange();
});

// Load previews on mount
onMounted(() => {
  loadTypePreviews();
});

// Initialize when drawer opens
watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      initializeFromConfig();
    }
  },
);
</script>

<template>
  <Drawer
    v-model:visible="isOpen"
    position="left"
    class="maze-wizard-drawer"
    :style="{ width: '460px' }"
    @hide="handleClose"
  >
    <template #header>
      <div class="wizard-header">
        <i class="pi pi-th-large"></i>
        <span class="font-semibold">Create Your Maze</span>
      </div>
    </template>

    <div class="wizard-content">
      <Stepper
        v-model:value="activeStep"
        orientation="vertical"
        class="wizard-stepper"
      >
        <!-- Step 1: Maze Type -->
        <StepItem value="1">
          <Step>Choose Maze Type</Step>
          <StepPanel v-slot="{ activateCallback }">
            <div class="step-content">
              <p class="step-description">
                Select the tessellation pattern for your maze
              </p>

              <div class="maze-type-options">
                <div
                  v-for="mazeType in mazeTypeOptions"
                  :key="mazeType.id"
                  class="maze-type-option"
                  :class="{
                    selected: selectedMazeType === mazeType.id,
                    disabled: !mazeType.available,
                  }"
                  @click="selectMazeType(mazeType.id)"
                >
                  <div class="maze-type-preview">
                    <div v-if="loadingPreviews" class="preview-loading">
                      <ProgressSpinner
                        style="width: 30px; height: 30px"
                        stroke-width="4"
                      />
                    </div>
                    <div
                      v-else-if="typePreviews[mazeType.id]"
                      class="preview-svg"
                      v-html="typePreviews[mazeType.id]"
                    ></div>
                  </div>

                  <div class="maze-type-info">
                    <div class="maze-type-name">
                      {{ mazeType.name }}
                      <span v-if="!mazeType.available" class="coming-soon-badge"
                        >Coming Soon</span
                      >
                    </div>
                    <div class="maze-type-description">
                      {{ mazeType.description }}
                    </div>
                  </div>

                  <div class="selection-indicator">
                    <i
                      v-if="selectedMazeType === mazeType.id"
                      class="pi pi-check-circle"
                    ></i>
                    <i v-else class="pi pi-circle"></i>
                  </div>
                </div>
              </div>

              <div class="step-navigation">
                <Button
                  label="Next"
                  icon="pi pi-arrow-right"
                  icon-pos="right"
                  @click="activateCallback('2')"
                />
              </div>
            </div>
          </StepPanel>
        </StepItem>

        <!-- Step 2: Size & Difficulty -->
        <StepItem value="2">
          <Step>Size & Difficulty</Step>
          <StepPanel v-slot="{ activateCallback }">
            <div class="step-content">
              <!-- Size -->
              <div class="slider-control">
                <div class="slider-header">
                  <label class="control-label">Complexity</label>
                  <span class="slider-hint">{{ sizeLabel }}</span>
                </div>
                <div class="slider-with-labels">
                  <span class="slider-end-label">Simple</span>
                  <Slider v-model="size" :min="1" :max="20" class="flex-1" />
                  <span class="slider-end-label">Complex</span>
                </div>
              </div>

              <!-- Difficulty -->
              <div class="difficulty-control">
                <div class="difficulty-header">
                  <label class="control-label">Difficulty</label>
                  <span class="difficulty-hint">{{ difficultyLabel }}</span>
                </div>
                <div class="difficulty-buttons">
                  <SelectButton
                    v-model="difficulty"
                    :options="difficultyOptions"
                    option-label="label"
                    option-value="value"
                    :allow-empty="false"
                  />
                </div>
                <p class="control-description">
                  Lower values add shortcuts making the maze easier to solve
                </p>
              </div>

              <!-- Solution Preview Toggle -->
              <div class="toggle-control">
                <label class="control-label">Solution Preview</label>
                <div class="toggle-row">
                  <ToggleButton
                    v-model="showSolution"
                    on-label="Showing"
                    off-label="Hidden"
                    on-icon="pi pi-eye"
                    off-icon="pi pi-eye-slash"
                    class="solution-toggle"
                  />
                  <span class="toggle-hint">{{
                    showSolution
                      ? "Solution path is visible"
                      : "Solution path is hidden"
                  }}</span>
                </div>
              </div>

              <div class="step-navigation">
                <Button
                  label="Previous"
                  icon="pi pi-arrow-left"
                  outlined
                  @click="activateCallback('1')"
                />
                <Button
                  label="Next"
                  icon="pi pi-arrow-right"
                  icon-pos="right"
                  @click="activateCallback('3')"
                />
              </div>
            </div>
          </StepPanel>
        </StepItem>

        <!-- Step 3: Colors -->
        <StepItem value="3">
          <Step>Colors</Step>
          <StepPanel v-slot="{ activateCallback }">
            <div class="step-content">
              <p class="step-description">
                Customize the appearance of your maze
              </p>

              <!-- Colors -->
              <div class="color-options">
                <div class="color-option">
                  <label class="color-label">Inner Wall Color</label>
                  <VSwatches
                    v-model="innerWallColor"
                    :swatches="colorSwatches"
                    :swatch-size="24"
                    :row-length="5"
                    popover-x="left"
                    @update:model-value="emitColorsChange"
                  />
                </div>

                <div class="color-option">
                  <label class="color-label">Outer Wall Color</label>
                  <VSwatches
                    v-model="outerWallColor"
                    :swatches="colorSwatches"
                    :swatch-size="24"
                    :row-length="5"
                    popover-x="left"
                    @update:model-value="emitColorsChange"
                  />
                </div>

                <div class="color-option">
                  <div class="color-label-group">
                    <label class="color-label">Solution Path</label>
                    <small class="color-note"
                      >(only printed on answer key)</small
                    >
                  </div>
                  <VSwatches
                    v-model="pathColor"
                    :swatches="colorSwatches"
                    :swatch-size="24"
                    :row-length="5"
                    popover-x="left"
                    @update:model-value="emitColorsChange"
                  />
                </div>
              </div>

              <div class="step-navigation">
                <Button
                  label="Previous"
                  icon="pi pi-arrow-left"
                  outlined
                  @click="activateCallback('2')"
                />
                <Button
                  label="Next"
                  icon="pi pi-arrow-right"
                  icon-pos="right"
                  @click="activateCallback('4')"
                />
              </div>
            </div>
          </StepPanel>
        </StepItem>

        <!-- Step 4: Finish -->
        <StepItem value="4">
          <Step>Your Maze is Ready!</Step>
          <StepPanel v-slot="{ activateCallback }">
            <div class="step-content">
              <div class="finish-content">
                <div class="finish-icon">
                  <i class="pi pi-check-circle"></i>
                </div>

                <p class="finish-description">
                  You've configured your maze! Your changes are already visible
                  in the preview. Use the toolbar to generate a new random maze
                  or download your creation.
                </p>

                <div class="finish-features">
                  <h4>What you can do:</h4>
                  <ul>
                    <li>
                      <i class="pi pi-refresh"></i>
                      <span
                        ><strong>Generate New</strong> - Create a new random
                        maze with your settings</span
                      >
                    </li>
                    <li>
                      <i class="pi pi-download"></i>
                      <span
                        ><strong>Download PDF</strong> - Get a print-ready file
                        (35" × 23")</span
                      >
                    </li>
                    <li>
                      <i class="pi pi-shopping-cart"></i>
                      <span
                        ><strong>Order Print</strong> - Get a professionally
                        printed maze poster</span
                      >
                    </li>
                  </ul>
                </div>
              </div>

              <div class="step-navigation">
                <Button
                  label="Previous"
                  icon="pi pi-arrow-left"
                  outlined
                  @click="activateCallback('3')"
                />
                <Button label="Done" icon="pi pi-check" @click="handleClose" />
              </div>
            </div>
          </StepPanel>
        </StepItem>
      </Stepper>
    </div>
  </Drawer>
</template>

<style scoped>
.maze-wizard-drawer {
  --drawer-width: 460px;
}

.wizard-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.wizard-header i {
  font-size: 1.25rem;
  color: var(--primary-color);
}

.wizard-content {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.wizard-stepper {
  height: 100%;
}

:deep(.p-stepper) {
  display: flex;
  flex-direction: column;
  height: 100%;
}

:deep(.p-stepper-vertical) {
  flex-direction: column;
}

:deep(.p-stepitem) {
  display: flex;
  flex-direction: column;
}

:deep(.p-steppanel) {
  padding-left: 0;
  margin-left: 0;
  border-left: 2px solid var(--surface-300);
}

:deep(.p-stepitem:last-child .p-steppanel) {
  border-left: 2px solid var(--surface-300);
}

.step-content {
  padding: 1rem 0;
}

.step-navigation {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 1.5rem;
  padding-top: 1rem;
  border-top: 1px solid var(--surface-200);
}

.step-description {
  color: var(--text-color-secondary);
  margin-bottom: 1rem;
  font-size: 0.875rem;
}

.subsection-title {
  font-size: 0.9375rem;
  font-weight: 600;
  color: var(--text-color);
  margin-top: 1.25rem;
  margin-bottom: 0.75rem;
}

/* Maze type options */
.maze-type-options {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.maze-type-option {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 0.75rem;
  border: 2px solid var(--surface-200);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  background: var(--surface-0);
}

.maze-type-option:hover:not(.disabled) {
  border-color: var(--primary-300);
  background: var(--surface-50);
}

.maze-type-option.selected {
  border-color: var(--primary-color);
  background: var(--primary-50);
}

.maze-type-option.disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.maze-type-preview {
  flex-shrink: 0;
  width: 100px;
  height: 100px;
  border-radius: 4px;
  overflow: hidden;
  background: white;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  border: 1px solid var(--surface-200);
}

.preview-loading {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--surface-50);
}

.preview-svg {
  width: 100%;
  height: 100%;
}

.preview-svg :deep(svg) {
  width: 100%;
  height: 100%;
}

.maze-type-info {
  flex: 1;
  min-width: 0;
}

.maze-type-name {
  font-weight: 600;
  font-size: 0.875rem;
  color: var(--text-color);
  margin-bottom: 0.25rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.coming-soon-badge {
  font-size: 0.625rem;
  font-weight: 500;
  padding: 0.125rem 0.375rem;
  background: var(--surface-200);
  color: var(--text-color-secondary);
  border-radius: 4px;
  text-transform: uppercase;
}

.maze-type-description {
  font-size: 0.8125rem;
  color: var(--text-color-secondary);
  line-height: 1.4;
}

.selection-indicator {
  flex-shrink: 0;
  font-size: 1.125rem;
}

.selection-indicator .pi-check-circle {
  color: var(--primary-color);
}

.selection-indicator .pi-circle {
  color: var(--surface-300);
}

/* Slider controls */
.slider-control {
  margin-bottom: 1.5rem;
}

.slider-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.control-label {
  font-weight: 600;
  font-size: 0.9375rem;
  color: var(--text-color);
}

.slider-hint {
  font-size: 0.8125rem;
  color: var(--primary-color);
  font-weight: 500;
}

.slider-with-labels {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.slider-end-label {
  font-size: 0.75rem;
  color: var(--text-color-secondary);
  min-width: 55px;
  text-align: center;
}

.slider-end-label:first-child {
  text-align: right;
}

.slider-end-label:last-child {
  text-align: left;
}

.control-description {
  font-size: 0.75rem;
  color: var(--text-color-secondary);
  margin-top: 0.5rem;
  margin-left: calc(55px + 0.75rem);
}

/* Difficulty control */
.difficulty-control {
  margin-bottom: 1.5rem;
}

.difficulty-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.difficulty-hint {
  font-size: 0.8125rem;
  color: var(--primary-color);
  font-weight: 500;
}

.difficulty-buttons {
  display: flex;
  justify-content: center;
}

.difficulty-buttons :deep(.p-selectbutton) {
  display: flex;
  flex-wrap: nowrap;
}

.difficulty-buttons :deep(.p-selectbutton .p-togglebutton) {
  padding: 0.5rem 0.75rem;
  min-width: 2.5rem;
  font-size: 0.875rem;
}

/* Toggle control */
.toggle-control {
  margin-bottom: 1.5rem;
}

.toggle-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-top: 0.5rem;
}

.toggle-hint {
  font-size: 0.8125rem;
  color: var(--text-color-secondary);
}

.solution-toggle {
  min-width: 100px;
}

.solution-toggle :deep(.p-togglebutton-checked) {
  background: var(--primary-color);
  border-color: var(--primary-color);
}

/* Checkbox options */
.checkbox-options {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.checkbox-option {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
}

.checkbox-label {
  display: flex;
  flex-direction: column;
  cursor: pointer;
}

.checkbox-title {
  font-weight: 500;
  font-size: 0.875rem;
  color: var(--text-color);
}

.checkbox-description {
  font-size: 0.75rem;
  color: var(--text-color-secondary);
}

/* Color options */
.color-options {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.color-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.color-label-group {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.color-label {
  font-weight: 500;
  font-size: 0.875rem;
  color: var(--text-color);
}

.color-note {
  font-size: 0.75rem;
  color: var(--text-color-secondary);
}

.color-option :deep(.vue-swatches__trigger) {
  width: 28px;
  height: 28px;
  border-radius: 4px;
  border: 1px solid var(--surface-300);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.color-option :deep(.vue-swatches__container) {
  background: var(--surface-0);
  border: 1px solid var(--surface-200);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  padding: 6px;
}

/* Finish panel */
.finish-content {
  text-align: center;
}

.finish-icon {
  font-size: 3rem;
  color: var(--green-500);
  margin-bottom: 1rem;
}

.finish-description {
  color: var(--text-color-secondary);
  margin-bottom: 1.5rem;
  line-height: 1.5;
}

.finish-features {
  text-align: left;
  background: var(--surface-50);
  border-radius: 8px;
  padding: 1rem;
  margin-bottom: 1rem;
}

.finish-features h4 {
  font-size: 0.875rem;
  font-weight: 600;
  margin-bottom: 0.75rem;
  color: var(--text-color);
}

.finish-features ul {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.625rem;
}

.finish-features li {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  font-size: 0.8125rem;
  color: var(--text-color-secondary);
}

.finish-features li i {
  color: var(--primary-color);
  margin-top: 0.125rem;
}

@media (max-width: 480px) {
  .maze-wizard-drawer {
    --drawer-width: 100vw;
  }

  .maze-type-option {
    flex-direction: column;
  }

  .maze-type-preview {
    width: 100%;
    height: auto;
    aspect-ratio: 1;
  }
}
</style>
