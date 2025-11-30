<script setup lang="ts">
import { ref, computed, watch, onMounted } from "vue";
import Drawer from "primevue/drawer";
import Stepper from "primevue/stepper";
import StepItem from "primevue/stepitem";
import Step from "primevue/step";
import StepPanel from "primevue/steppanel";
import Button from "primevue/button";
import Checkbox from "primevue/checkbox";
import ColorPicker from "primevue/colorpicker";
import ProgressSpinner from "primevue/progressspinner";

// Props
interface Props {
  visible: boolean;
}

const props = defineProps<Props>();

// Emits
const emit = defineEmits<{
  (e: "update:visible", value: boolean): void;
  (e: "layoutChange", layout: LayoutType): void;
  (e: "moonChange", settings: MoonSettings): void;
  (e: "displayOptionsChange", options: DisplayOptions): void;
  (e: "colorsChange", colors: ColorSettings): void;
}>();

// Types
export type LayoutType = "left-aligned" | "day-of-week-aligned";
export type MoonStyleType = "full-size" | "small-corner";
export type MoonDisplayModeType =
  | "none"
  | "phases"
  | "full-only"
  | "illumination";
export type WeekendStyleType = "greyscale" | "rainbow" | "vermont";

export interface MoonSettings {
  style: MoonStyleType;
  displayMode: MoonDisplayModeType;
  moonSize: number;
  moonX: number;
  moonY: number;
  moonBorderColor: string;
  moonBorderWidth: number;
  moonDarkSideColor: string;
  moonLightSideColor: string;
  moonDisplayMode: string;
}

export interface DisplayOptions {
  weekendStyle: WeekendStyleType;
  showGrid: boolean;
  showDayNames: boolean;
  showWeekNumbers: boolean;
  rotateMonthNames: boolean;
}

export interface ColorSettings {
  yearColor: string;
  monthColor: string;
  dayTextColor: string;
  dayNameColor: string;
  gridLineColor: string;
}

export interface WizardConfig {
  layout: LayoutType;
  moon: MoonSettings;
  displayOptions: DisplayOptions;
  colors: ColorSettings;
}

// State
const activeStep = ref<string>("1");
const selectedLayout = ref<LayoutType | null>(null);
const selectedMoonStyle = ref<MoonStyleType | null>(null);
const selectedMoonDisplayMode = ref<MoonDisplayModeType | null>(null);
const layoutPreviews = ref<Record<string, string>>({});
const loadingPreviews = ref(false);

// Display options state
const selectedWeekendStyle = ref<WeekendStyleType>("greyscale");
const showGrid = ref(true);
const showDayNames = ref(true);
const showWeekNumbers = ref(true);
const rotateMonthNames = ref(false);

// Color settings state
const yearColor = ref("#000000");
const monthColor = ref("#000000");
const dayTextColor = ref("#000000");
const dayNameColor = ref("#666666");
const gridLineColor = ref("#c1c1c1");

// Computed
const isOpen = computed({
  get: () => props.visible,
  set: (value) => emit("update:visible", value),
});

// Layout options with their corresponding API layoutStyle values
const layoutOptions = [
  {
    id: "left-aligned" as const,
    apiLayoutStyle: "grid",
    name: "Left Aligned (12x31)",
    description:
      "12 months as rows, 31 day columns. Days 1-31 flow left to right for each month.",
    default: true,
  },
  {
    id: "day-of-week-aligned" as const,
    apiLayoutStyle: "weekday-grid",
    name: "Day of Week Aligned (12x37)",
    description:
      "12 months as rows, 7 day-of-week columns with weeks stacked. See all Mondays, Tuesdays, etc. at a glance.",
    default: false,
  },
];

// Moon size/position presets
const moonSizeOptions = [
  {
    id: "full-size" as const,
    name: "Full Size",
    description: "Large moon prominently shown in each cell.",
    settings: {
      moonSize: 20,
      moonX: 25,
      moonY: 45,
      moonBorderColor: "#666666",
      moonBorderWidth: 1.5,
      moonDarkSideColor: "#dddddd",
      moonLightSideColor: "#FFFFFF",
    },
  },
  {
    id: "small-corner" as const,
    name: "Small in Corner",
    description: "Subtle moon tucked in the corner of each cell.",
    settings: {
      moonSize: 8,
      moonX: 32,
      moonY: 14,
      moonBorderColor: "#666666",
      moonBorderWidth: 0.5,
      moonDarkSideColor: "#dddddd",
      moonLightSideColor: "#FFFFFF",
    },
  },
];

// Moon display mode options
const moonDisplayModeOptions = [
  {
    id: "none" as const,
    name: "No Moons",
    description: "Don't display any moon phases.",
  },
  {
    id: "phases" as const,
    name: "Major Phases",
    description: "New moon, first quarter, full moon, and last quarter.",
  },
  {
    id: "full-only" as const,
    name: "Full Moons Only",
    description: "Only show full moon dates.",
  },
  {
    id: "illumination" as const,
    name: "Daily Moon",
    description: "Show the moon's illumination every day.",
  },
];

// Weekend style options
const weekendStyleOptions = [
  {
    id: "greyscale" as const,
    name: "Greyscale",
    description: "Subtle grey background for weekends.",
    theme: "default",
  },
  {
    id: "rainbow" as const,
    name: "Rainbow Weekends",
    description: "Colorful rainbow gradient for weekends.",
    theme: "rainbowWeekends",
  },
  {
    id: "vermont" as const,
    name: "Vermont Seasons",
    description: "Seasonal colors inspired by Vermont landscapes.",
    theme: "vermontWeekends",
  },
];

// Random day for preview (1-28 to be safe)
const previewDay = Math.floor(Math.random() * 28) + 1;

// Generate moon preview cell SVG for size options (no day name)
const generateMoonPreviewSVG = (moonOption: (typeof moonSizeOptions)[0]) => {
  const cellWidth = 55;
  const cellHeight = 65;
  const settings = moonOption.settings;

  // Calculate moon position and size
  const moonRadius = settings.moonSize / 2;
  const moonCx = settings.moonX;
  const moonCy = settings.moonY;

  // Draw a half moon (first quarter) for preview
  const phase = 0.25; // First quarter

  // Create moon path for first quarter moon
  const moonPath = createMoonPath(
    moonCx,
    moonCy,
    moonRadius,
    phase,
    settings.moonLightSideColor,
    settings.moonDarkSideColor,
  );

  return `
    <svg viewBox="0 0 ${cellWidth} ${cellHeight}" xmlns="http://www.w3.org/2000/svg">
      <rect x="0" y="0" width="${cellWidth}" height="${cellHeight}" fill="white" stroke="#c1c1c1" stroke-width="1"/>
      <text x="5" y="20" font-size="14" fill="#000000">${previewDay}</text>
      ${moonPath}
      <circle cx="${moonCx}" cy="${moonCy}" r="${moonRadius}" fill="none" stroke="${settings.moonBorderColor}" stroke-width="${settings.moonBorderWidth}"/>
    </svg>
  `;
};

// Create moon path for a given phase
const createMoonPath = (
  cx: number,
  cy: number,
  r: number,
  phase: number,
  lightColor: string,
  darkColor: string,
) => {
  // phase: 0 = new moon, 0.25 = first quarter, 0.5 = full moon, 0.75 = last quarter

  // Background circle (dark side)
  let svg = `<circle cx="${cx}" cy="${cy}" r="${r}" fill="${darkColor}"/>`;

  if (phase < 0.01 || phase > 0.99) {
    // New moon - all dark
    return svg;
  }

  if (phase > 0.49 && phase < 0.51) {
    // Full moon - all light
    return `<circle cx="${cx}" cy="${cy}" r="${r}" fill="${lightColor}"/>`;
  }

  // Calculate the illuminated portion
  // For first quarter (phase ~0.25), right half is lit
  // For last quarter (phase ~0.75), left half is lit
  const isWaxing = phase < 0.5;
  const illuminatedFraction = isWaxing ? phase * 2 : (1 - phase) * 2;

  // Create ellipse width based on illumination
  const ellipseRx = r * Math.abs(1 - illuminatedFraction * 2);

  if (isWaxing) {
    // Waxing: light on right side
    // Draw light half circle on right
    svg += `<path d="M ${cx} ${cy - r} A ${r} ${r} 0 0 1 ${cx} ${cy + r} A ${ellipseRx} ${r} 0 0 ${illuminatedFraction > 0.5 ? 1 : 0} ${cx} ${cy - r}" fill="${lightColor}"/>`;
  } else {
    // Waning: light on left side
    svg += `<path d="M ${cx} ${cy - r} A ${r} ${r} 0 0 0 ${cx} ${cy + r} A ${ellipseRx} ${r} 0 0 ${illuminatedFraction > 0.5 ? 0 : 1} ${cx} ${cy - r}" fill="${lightColor}"/>`;
  }

  return svg;
};

// Get current year for preview
const currentDate = new Date();
const currentMonth = currentDate.getMonth();
const previewYear =
  currentMonth <= 2 ? currentDate.getFullYear() : currentDate.getFullYear() + 1;

// Generate minimal config for thumbnail preview
const getMinimalConfig = (layoutStyle: string) => ({
  calendarType: "gregorian",
  year: previewYear,
  theme: "default",
  layoutStyle: layoutStyle,
  moonDisplayMode: "none",
  showWeekNumbers: false,
  compactMode: false,
  showDayNames: true,
  showDayNumbers: true,
  showGrid: true,
  highlightWeekends: true,
  rotateMonthNames: false,
  firstDayOfWeek: "SUNDAY",
  latitude: 0,
  longitude: 0,
  // Default grey theme colors
  yearColor: "#000000",
  monthColor: "#000000",
  dayTextColor: "#000000",
  dayNameColor: "#666666",
  gridLineColor: "#c1c1c1",
  weekendBgColor: "#f0f0f0",
  holidayColor: "#ff5252",
  customDateColor: "#4caf50",
  emojiPosition: "bottom-left",
  customDates: {},
  eventTitles: {},
  showHolidays: false,
  locale: "en-US",
});

// Fetch calendar preview SVG from API
const fetchLayoutPreview = async (layoutStyle: string): Promise<string> => {
  try {
    const config = getMinimalConfig(layoutStyle);
    const response = await fetch("/api/calendar/generate-json", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(config),
    });

    if (response.ok) {
      const data = await response.json();
      return data.svg || "";
    }
  } catch (error) {
    console.error(`Error fetching preview for ${layoutStyle}:`, error);
  }
  return "";
};

// Scale and enhance SVG for thumbnail display
const scaleSvgForThumbnail = (svg: string): string => {
  if (!svg) return "";

  // Parse viewBox to get dimensions
  const viewBoxMatch = svg.match(/viewBox="([^"]+)"/)?.[1];
  if (!viewBoxMatch) return svg;

  // Add thicker stroke to day cells to accentuate the layout
  // This targets rect elements that are day cells
  const enhancedSvg = svg
    // Make grid lines slightly thicker for thumbnails
    .replace(/stroke-width="0\.5"/g, 'stroke-width="1.5"')
    .replace(/stroke-width="1"/g, 'stroke-width="2"')
    // Ensure the SVG scales properly
    .replace(/width="[^"]+"/, 'width="100%"')
    .replace(/height="[^"]+"/, 'height="100%"');

  return enhancedSvg;
};

// Load all layout previews
const loadLayoutPreviews = async () => {
  loadingPreviews.value = true;

  try {
    const previews: Record<string, string> = {};

    for (const layout of layoutOptions) {
      const svg = await fetchLayoutPreview(layout.apiLayoutStyle);
      if (svg) {
        previews[layout.id] = scaleSvgForThumbnail(svg);
      }
    }

    layoutPreviews.value = previews;
  } finally {
    loadingPreviews.value = false;
  }
};

// Methods
const selectLayout = (layoutId: LayoutType) => {
  selectedLayout.value = layoutId;
  // Emit immediately so the calendar updates in real-time
  emit("layoutChange", layoutId);
};

// Emit current moon settings
const emitMoonSettings = () => {
  if (!selectedMoonStyle.value || !selectedMoonDisplayMode.value) {
    return; // Need both selections before emitting
  }

  const sizeOption = moonSizeOptions.find(
    (m) => m.id === selectedMoonStyle.value,
  );
  if (!sizeOption) return;

  // Map display mode to backend moonDisplayMode value
  const displayModeMap: Record<MoonDisplayModeType, string> = {
    none: "none",
    phases: "phases",
    "full-only": "full-only",
    illumination: "illumination",
  };

  // Adjust moon Y position if day names are shown and large moon is selected
  let adjustedMoonY = sizeOption.settings.moonY;
  if (showDayNames.value && selectedMoonStyle.value === "full-size") {
    adjustedMoonY += 4;
  }

  emit("moonChange", {
    style: selectedMoonStyle.value,
    displayMode: selectedMoonDisplayMode.value,
    ...sizeOption.settings,
    moonY: adjustedMoonY,
    moonDisplayMode: displayModeMap[selectedMoonDisplayMode.value],
  });
};

const selectMoonStyle = (moonId: MoonStyleType) => {
  selectedMoonStyle.value = moonId;
  emitMoonSettings();
};

const selectMoonDisplayMode = (modeId: MoonDisplayModeType) => {
  selectedMoonDisplayMode.value = modeId;
  emitMoonSettings();
};

// Emit display options
const emitDisplayOptions = () => {
  emit("displayOptionsChange", {
    weekendStyle: selectedWeekendStyle.value,
    showGrid: showGrid.value,
    showDayNames: showDayNames.value,
    showWeekNumbers: showWeekNumbers.value,
    rotateMonthNames: rotateMonthNames.value,
  });

  // Re-emit moon settings in case day names changed (affects moon Y position)
  emitMoonSettings();
};

const selectWeekendStyle = (styleId: WeekendStyleType) => {
  selectedWeekendStyle.value = styleId;
  emitDisplayOptions();
};

// Emit color settings
const emitColorSettings = () => {
  emit("colorsChange", {
    yearColor: yearColor.value,
    monthColor: monthColor.value,
    dayTextColor: dayTextColor.value,
    dayNameColor: dayNameColor.value,
    gridLineColor: gridLineColor.value,
  });
};

const handleClose = () => {
  isOpen.value = false;
};

// Watch for display option changes
watch([showGrid, showDayNames, showWeekNumbers, rotateMonthNames], () => {
  emitDisplayOptions();
});

// Watch for color changes
watch(
  [yearColor, monthColor, dayTextColor, dayNameColor, gridLineColor],
  () => {
    emitColorSettings();
  },
);

// Load previews when drawer opens
watch(
  () => props.visible,
  (visible) => {
    if (visible && Object.keys(layoutPreviews.value).length === 0) {
      loadLayoutPreviews();
    }
  },
);

// Also load on mount if already visible
onMounted(() => {
  if (props.visible) {
    loadLayoutPreviews();
  }
});
</script>

<template>
  <Drawer
    v-model:visible="isOpen"
    position="left"
    class="create-wizard-drawer"
    :style="{ width: '460px' }"
    @hide="handleClose"
  >
    <template #header>
      <div class="wizard-header">
        <i class="pi pi-palette"></i>
        <span class="font-semibold">Create Your Own Calendar</span>
      </div>
    </template>

    <div class="wizard-content">
      <Stepper
        v-model:value="activeStep"
        orientation="vertical"
        class="wizard-stepper"
      >
        <!-- Step 1: Layout -->
        <StepItem value="1">
          <Step>Layout</Step>
          <StepPanel v-slot="{ activateCallback }">
            <div class="step-content">
              <h3 class="step-title">Choose Your Layout</h3>
              <p class="step-description">
                Select how you want your full-year calendar arranged
              </p>

              <div class="layout-options">
                <div
                  v-for="layout in layoutOptions"
                  :key="layout.id"
                  class="layout-option"
                  :class="{ selected: selectedLayout === layout.id }"
                  @click="selectLayout(layout.id)"
                >
                  <div class="layout-preview">
                    <!-- Loading state -->
                    <div v-if="loadingPreviews" class="preview-loading">
                      <ProgressSpinner
                        style="width: 30px; height: 30px"
                        stroke-width="4"
                      />
                    </div>
                    <!-- Generated SVG preview -->
                    <div
                      v-else-if="layoutPreviews[layout.id]"
                      class="preview-svg"
                      v-html="layoutPreviews[layout.id]"
                    ></div>
                    <!-- Fallback placeholder -->
                    <div v-else class="preview-placeholder">
                      <i class="pi pi-calendar"></i>
                    </div>
                  </div>

                  <div class="layout-info">
                    <div class="layout-name">
                      {{ layout.name }}
                      <span v-if="layout.default" class="default-badge"
                        >Default</span
                      >
                    </div>
                    <div class="layout-description">
                      {{ layout.description }}
                    </div>
                  </div>

                  <div class="selection-indicator">
                    <i
                      v-if="selectedLayout === layout.id"
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

        <!-- Step 2: Moon Display -->
        <StepItem value="2">
          <Step>Moon</Step>
          <StepPanel v-slot="{ activateCallback }">
            <div class="step-content">
              <h3 class="step-title">Moon Display</h3>

              <!-- Moon Size Selection -->
              <h4 class="subsection-title">Moon Size</h4>
              <p class="step-description">
                Choose how large the moon appears in each cell
              </p>
              <div class="moon-options">
                <div
                  v-for="moon in moonSizeOptions"
                  :key="moon.id"
                  class="moon-option"
                  :class="{ selected: selectedMoonStyle === moon.id }"
                  @click="selectMoonStyle(moon.id)"
                >
                  <div class="moon-preview">
                    <div
                      class="preview-cell"
                      v-html="generateMoonPreviewSVG(moon)"
                    ></div>
                  </div>

                  <div class="moon-info">
                    <div class="moon-name">{{ moon.name }}</div>
                    <div class="moon-description">{{ moon.description }}</div>
                  </div>

                  <div class="selection-indicator">
                    <i
                      v-if="selectedMoonStyle === moon.id"
                      class="pi pi-check-circle"
                    ></i>
                    <i v-else class="pi pi-circle"></i>
                  </div>
                </div>
              </div>

              <!-- Moon Display Mode Selection -->
              <h4 class="subsection-title">Which Moons to Show</h4>
              <p class="step-description">
                Select which moon phases appear on your calendar
              </p>
              <div class="display-mode-options">
                <div
                  v-for="mode in moonDisplayModeOptions"
                  :key="mode.id"
                  class="display-mode-option"
                  :class="{ selected: selectedMoonDisplayMode === mode.id }"
                  @click="selectMoonDisplayMode(mode.id)"
                >
                  <div class="mode-info">
                    <div class="mode-name">{{ mode.name }}</div>
                    <div class="mode-description">{{ mode.description }}</div>
                  </div>

                  <div class="selection-indicator">
                    <i
                      v-if="selectedMoonDisplayMode === mode.id"
                      class="pi pi-check-circle"
                    ></i>
                    <i v-else class="pi pi-circle"></i>
                  </div>
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

        <!-- Step 3: Display Options -->
        <StepItem value="3">
          <Step>Display</Step>
          <StepPanel v-slot="{ activateCallback }">
            <div class="step-content">
              <h3 class="step-title">Display Options</h3>

              <!-- Weekend Colors -->
              <h4 class="subsection-title">Weekend Colors</h4>
              <p class="step-description">
                Choose how weekends are highlighted
              </p>
              <div class="display-mode-options">
                <div
                  v-for="style in weekendStyleOptions"
                  :key="style.id"
                  class="display-mode-option"
                  :class="{ selected: selectedWeekendStyle === style.id }"
                  @click="selectWeekendStyle(style.id)"
                >
                  <div class="mode-info">
                    <div class="mode-name">{{ style.name }}</div>
                    <div class="mode-description">{{ style.description }}</div>
                  </div>

                  <div class="selection-indicator">
                    <i
                      v-if="selectedWeekendStyle === style.id"
                      class="pi pi-check-circle"
                    ></i>
                    <i v-else class="pi pi-circle"></i>
                  </div>
                </div>
              </div>

              <!-- Checkbox Options -->
              <h4 class="subsection-title">Additional Options</h4>
              <div class="checkbox-options">
                <div class="checkbox-option">
                  <Checkbox
                    v-model="showGrid"
                    input-id="showGrid"
                    :binary="true"
                  />
                  <label for="showGrid" class="checkbox-label">
                    <span class="checkbox-title">Show Grid Lines</span>
                    <span class="checkbox-description"
                      >Display lines between calendar cells</span
                    >
                  </label>
                </div>

                <div class="checkbox-option">
                  <Checkbox
                    v-model="showDayNames"
                    input-id="showDayNames"
                    :binary="true"
                  />
                  <label for="showDayNames" class="checkbox-label">
                    <span class="checkbox-title">Show Day Names</span>
                    <span class="checkbox-description"
                      >Display abbreviated day names (Mon, Tue, etc.)</span
                    >
                  </label>
                </div>

                <div class="checkbox-option">
                  <Checkbox
                    v-model="showWeekNumbers"
                    input-id="showWeekNumbers"
                    :binary="true"
                  />
                  <label for="showWeekNumbers" class="checkbox-label">
                    <span class="checkbox-title">Show Week Numbers</span>
                    <span class="checkbox-description"
                      >Display week numbers on the left side</span
                    >
                  </label>
                </div>

                <div class="checkbox-option">
                  <Checkbox
                    v-model="rotateMonthNames"
                    input-id="rotateMonthNames"
                    :binary="true"
                  />
                  <label for="rotateMonthNames" class="checkbox-label">
                    <span class="checkbox-title">Rotate Month Names</span>
                    <span class="checkbox-description"
                      >Display month names vertically</span
                    >
                  </label>
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

        <!-- Step 4: Colors -->
        <StepItem value="4">
          <Step>Colors</Step>
          <StepPanel v-slot="{ activateCallback }">
            <div class="step-content">
              <h3 class="step-title">Color Customization</h3>
              <p class="step-description">
                Fine-tune the colors of your calendar
              </p>

              <div class="color-options">
                <div class="color-option">
                  <label class="color-label">Year Color</label>
                  <div class="color-picker-wrapper">
                    <ColorPicker v-model="yearColor" />
                    <span class="color-value">{{ yearColor }}</span>
                  </div>
                </div>

                <div class="color-option">
                  <label class="color-label">Month Names</label>
                  <div class="color-picker-wrapper">
                    <ColorPicker v-model="monthColor" />
                    <span class="color-value">{{ monthColor }}</span>
                  </div>
                </div>

                <div class="color-option">
                  <label class="color-label">Day Numbers</label>
                  <div class="color-picker-wrapper">
                    <ColorPicker v-model="dayTextColor" />
                    <span class="color-value">{{ dayTextColor }}</span>
                  </div>
                </div>

                <div class="color-option">
                  <label class="color-label">Day Names</label>
                  <div class="color-picker-wrapper">
                    <ColorPicker v-model="dayNameColor" />
                    <span class="color-value">{{ dayNameColor }}</span>
                  </div>
                </div>

                <div class="color-option">
                  <label class="color-label">Grid Lines</label>
                  <div class="color-picker-wrapper">
                    <ColorPicker v-model="gridLineColor" />
                    <span class="color-value">{{ gridLineColor }}</span>
                  </div>
                </div>
              </div>

              <div class="step-navigation">
                <Button
                  label="Previous"
                  icon="pi pi-arrow-left"
                  outlined
                  @click="activateCallback('3')"
                />
                <Button
                  label="Next"
                  icon="pi pi-arrow-right"
                  icon-pos="right"
                  @click="activateCallback('5')"
                />
              </div>
            </div>
          </StepPanel>
        </StepItem>

        <!-- Step 5: Finish -->
        <StepItem value="5">
          <Step>Finish</Step>
          <StepPanel v-slot="{ activateCallback }">
            <div class="step-content">
              <h3 class="step-title">Your Calendar is Ready!</h3>

              <div class="finish-content">
                <div class="finish-icon">
                  <i class="pi pi-check-circle"></i>
                </div>

                <p class="finish-description">
                  You've customized your calendar with your preferred layout,
                  moon display, and color options. Your changes are already
                  visible in the preview.
                </p>

                <div class="finish-features">
                  <h4>What you can do next:</h4>
                  <ul>
                    <li>
                      <i class="pi pi-download"></i>
                      <span
                        ><strong>Download as PDF</strong> - Perfect for printing
                        at home or at a print shop</span
                      >
                    </li>
                    <li>
                      <i class="pi pi-save"></i>
                      <span
                        ><strong>Save your calendar</strong> - Keep your design
                        for future reference</span
                      >
                    </li>
                    <li>
                      <i class="pi pi-calendar-plus"></i>
                      <span
                        ><strong>Add custom events</strong> - Mark birthdays,
                        anniversaries, and special dates</span
                      >
                    </li>
                    <li>
                      <i class="pi pi-shopping-cart"></i>
                      <span
                        ><strong>Order a print</strong> - Get a professionally
                        printed calendar delivered to you</span
                      >
                    </li>
                  </ul>
                </div>

                <p class="finish-cta">
                  Close this wizard to continue customizing or use the toolbar
                  buttons to download or save your calendar.
                </p>
              </div>

              <div class="step-navigation">
                <Button
                  label="Previous"
                  icon="pi pi-arrow-left"
                  outlined
                  @click="activateCallback('4')"
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
.create-wizard-drawer {
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

/* Vertical stepper layout */
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

.step-title {
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--text-color);
  margin-bottom: 0.5rem;
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
  margin-bottom: 0.25rem;
}

.subsection-title:first-of-type {
  margin-top: 0;
}

.layout-options,
.moon-options {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.layout-option,
.moon-option {
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

.layout-option:hover,
.moon-option:hover {
  border-color: var(--primary-300);
  background: var(--surface-50);
}

.layout-option.selected,
.moon-option.selected {
  border-color: var(--primary-color);
  background: var(--primary-50);
}

.layout-preview {
  flex-shrink: 0;
  width: 140px;
  height: 95px;
  border-radius: 4px;
  overflow: hidden;
  background: white;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  border: 1px solid var(--surface-200);
}

.moon-preview {
  flex-shrink: 0;
  width: 60px;
  height: 70px;
  border-radius: 4px;
  overflow: hidden;
  background: white;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  border: 1px solid var(--surface-200);
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-cell {
  width: 55px;
  height: 65px;
}

.preview-cell :deep(svg) {
  width: 100%;
  height: 100%;
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
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-svg :deep(svg) {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.preview-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--surface-100);
  color: var(--surface-400);
}

.preview-placeholder i {
  font-size: 2rem;
}

.layout-info,
.moon-info {
  flex: 1;
  min-width: 0;
}

.layout-name,
.moon-name {
  font-weight: 600;
  font-size: 0.875rem;
  color: var(--text-color);
  margin-bottom: 0.125rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.default-badge {
  font-size: 0.625rem;
  font-weight: 500;
  padding: 0.125rem 0.375rem;
  background: var(--surface-200);
  color: var(--text-color-secondary);
  border-radius: 4px;
  text-transform: uppercase;
}

.layout-description,
.moon-description {
  font-size: 0.8125rem;
  color: var(--text-color-secondary);
  line-height: 1.4;
}

/* Display mode options (simpler, no preview) */
.display-mode-options {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.display-mode-option {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.625rem 0.75rem;
  border: 2px solid var(--surface-200);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  background: var(--surface-0);
}

.display-mode-option:hover {
  border-color: var(--primary-300);
  background: var(--surface-50);
}

.display-mode-option.selected {
  border-color: var(--primary-color);
  background: var(--primary-50);
}

.mode-info {
  flex: 1;
  min-width: 0;
}

.mode-name {
  font-weight: 600;
  font-size: 0.875rem;
  color: var(--text-color);
  margin-bottom: 0.0625rem;
}

.mode-description {
  font-size: 0.75rem;
  color: var(--text-color-secondary);
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

/* Checkbox options */
.checkbox-options {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
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
  gap: 1rem;
}

.color-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.color-label {
  font-weight: 500;
  font-size: 0.875rem;
  color: var(--text-color);
}

.color-picker-wrapper {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.color-value {
  font-size: 0.75rem;
  font-family: monospace;
  color: var(--text-color-secondary);
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

.finish-cta {
  font-size: 0.8125rem;
  color: var(--text-color-secondary);
  font-style: italic;
}

@media (max-width: 480px) {
  .create-wizard-drawer {
    --drawer-width: 100vw;
  }

  .layout-option,
  .moon-option {
    flex-direction: column;
  }

  .layout-preview {
    width: 100%;
    height: auto;
    aspect-ratio: 140 / 95;
  }

  .moon-preview {
    width: 100%;
    height: auto;
    aspect-ratio: 60 / 70;
  }
}
</style>
