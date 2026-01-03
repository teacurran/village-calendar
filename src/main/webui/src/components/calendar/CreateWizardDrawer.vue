<script setup lang="ts">
import { ref, computed, watch, onMounted, nextTick } from "vue";
import Drawer from "primevue/drawer";
import Stepper from "primevue/stepper";
import StepItem from "primevue/stepitem";
import Step from "primevue/step";
import StepPanel from "primevue/steppanel";
import Button from "primevue/button";
import Checkbox from "primevue/checkbox";
import RadioButton from "primevue/radiobutton";
import ProgressSpinner from "primevue/progressspinner";
import Select from "primevue/select";
import Popover from "primevue/popover";
import { VSwatches } from "vue3-swatches";
import "vue3-swatches/dist/style.css";

// Props
interface CalendarConfig {
  layoutStyle?: string;
  theme?: string;
  weekendBgColor?: string;
  moonDisplayMode?: string;
  moonSize?: number;
  moonOffsetX?: number;
  moonOffsetY?: number;
  moonBorderColor?: string;
  moonBorderWidth?: number;
  moonDarkColor?: string;
  moonLightColor?: string;
  showGrid?: boolean;
  showDayNames?: boolean;
  rotateMonthNames?: boolean;
  yearColor?: string;
  monthColor?: string;
  dayTextColor?: string;
  dayNameColor?: string;
  gridLineColor?: string;
  holidayColor?: string;
  emojiFont?: string;
  holidaySets?: string[];
  eventDisplayMode?: string;
}

interface Props {
  visible: boolean;
  config?: CalendarConfig;
}

const props = defineProps<Props>();

// Emits
const emit = defineEmits<{
  (e: "update:visible", value: boolean): void;
  (e: "layoutChange", layout: LayoutType): void;
  (e: "moonChange", settings: MoonSettings): void;
  (e: "displayOptionsChange", options: DisplayOptions): void;
  (e: "colorsChange", colors: ColorSettings): void;
  (e: "holidaysChange", holidays: HolidaySettings): void;
}>();

// Types
export type LayoutType = "left-aligned" | "day-of-week-aligned";
export type MoonStyleType = "full-size" | "small-corner";
export type MoonDisplayModeType =
  | "none"
  | "phases"
  | "full-only"
  | "illumination";
export type WeekendStyleType =
  | "none"
  | "greyscale"
  | "rainbow"
  | "vermont"
  | "lakeshore"
  | "sunset"
  | "forest";

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
  solidWeekendColor: string | null;
  showGrid: boolean;
  showDayNames: boolean;
  rotateMonthNames: boolean;
}

export type EmojiFontType =
  | "noto-color"
  | "noto-mono"
  | "mono-red"
  | "mono-blue"
  | "mono-green"
  | "mono-orange"
  | "mono-purple"
  | "mono-pink"
  | "mono-teal"
  | "mono-brown"
  | "mono-navy"
  | "mono-coral";

export interface ColorSettings {
  yearColor: string;
  monthColor: string;
  dayTextColor: string;
  dayNameColor: string;
  gridLineColor: string;
  holidayColor: string;
  emojiFont: EmojiFontType;
}

export type EventDisplayMode =
  | "large"
  | "large-text"
  | "small"
  | "text"
  | "none";

export interface HolidaySettings {
  selectedSets: string[];
  displayMode: EventDisplayMode;
}

export interface WizardConfig {
  layout: LayoutType;
  moon: MoonSettings;
  displayOptions: DisplayOptions;
  colors: ColorSettings;
  holidays: HolidaySettings;
}

// State
const activeStep = ref<string>("1");
const selectedLayout = ref<LayoutType | null>(null);
const selectedMoonStyle = ref<MoonStyleType | null>(null);
const selectedMoonDisplayMode = ref<MoonDisplayModeType | null>(null);
const layoutPreviews = ref<Record<string, string>>({});
const loadingPreviews = ref(false);

// Display options state
const selectedWeekendStyle = ref<WeekendStyleType | null>("greyscale");
const solidWeekendColor = ref<string | null>(null);
const showGrid = ref(true);
const showDayNames = ref(true);
const rotateMonthNames = ref(false);

// Color settings state
const yearColor = ref("#000000");
const monthColor = ref("#000000");
const dayTextColor = ref("#000000");
const dayNameColor = ref("#666666");
const gridLineColor = ref("#c1c1c1");
const holidayColor = ref("#ff5252");
const emojiFont = ref<EmojiFontType>("noto-color");
const emojiPopover = ref();

// Flag to prevent emitting during initialization
const isInitializing = ref(false);

// Holiday settings state
const primaryHolidaySet = ref<string>("none"); // Current dropdown selection
const additionalHolidaySets = ref<string[]>([]); // Items added via + button
const eventDisplayMode = ref<EventDisplayMode>("large");

// Holiday set options (including "No Holidays" as default, rest alphabetical)
const holidaySetOptions = [
  { label: "No Holidays", value: "none" },
  { label: "Canadian Holidays", value: "canadian" },
  { label: "Chinese/Lunar Holidays", value: "chinese" },
  { label: "Christian Holidays", value: "christian" },
  { label: "Fun & Secular Holidays", value: "secular" },
  { label: "Hindu Holidays", value: "hindu" },
  { label: "Islamic Holidays", value: "islamic" },
  { label: "Jewish Holidays", value: "jewish" },
  { label: "Major World Holidays", value: "major_world" },
  { label: "Mexican Holidays", value: "mexican" },
  { label: "Pagan/Wiccan (Wheel of the Year)", value: "pagan" },
  { label: "UK Holidays", value: "uk" },
  { label: "US Holidays", value: "us" },
];

// Filter out items already in the additional list from dropdown options
const availableHolidaySets = computed(() => {
  return holidaySetOptions.filter(
    (opt) =>
      opt.value === "none" || !additionalHolidaySets.value.includes(opt.value),
  );
});

// Get current emoji style option for display
const currentEmojiStyle = computed(() => {
  return (
    emojiStyleOptions.find((o) => o.id === emojiFont.value) ||
    emojiStyleOptions[0]
  );
});

// Toggle emoji popover
const toggleEmojiPopover = (event: Event) => {
  emojiPopover.value?.toggle(event);
};

// Select emoji style and close popover
const selectEmojiStyle = (option: (typeof emojiStyleOptions)[0]) => {
  emojiFont.value = option.id;
  emojiPopover.value?.hide();
};

// Get label for a holiday set value
const getHolidaySetLabel = (value: string) => {
  const option = holidaySetOptions.find((opt) => opt.value === value);
  return option ? option.label : value;
};

// Handle primary holiday set change - just emit, no auto-add to list
const handlePrimaryHolidayChange = () => {
  emitHolidaySettings();
};

// Add current primary selection to the additional list via + button
const addHolidaySet = () => {
  if (
    primaryHolidaySet.value &&
    primaryHolidaySet.value !== "none" &&
    !additionalHolidaySets.value.includes(primaryHolidaySet.value)
  ) {
    additionalHolidaySets.value.push(primaryHolidaySet.value);
    // Reset dropdown to allow selecting another
    primaryHolidaySet.value = "none";
    emitHolidaySettings();
  }
};

// Remove a holiday set from the additional list
const removeHolidaySet = (setValue: string) => {
  additionalHolidaySets.value = additionalHolidaySets.value.filter(
    (v) => v !== setValue,
  );
  emitHolidaySettings();
};

// Emit holiday settings - combines primary selection with additional list
const emitHolidaySettings = () => {
  const allSets: string[] = [];
  // Add primary selection if not "none"
  if (primaryHolidaySet.value && primaryHolidaySet.value !== "none") {
    allSets.push(primaryHolidaySet.value);
  }
  // Add all items from the additional list
  additionalHolidaySets.value.forEach((set) => {
    if (!allSets.includes(set)) {
      allSets.push(set);
    }
  });
  emit("holidaysChange", {
    selectedSets: allSets,
    displayMode: eventDisplayMode.value,
  });
};

// Computed
const isOpen = computed({
  get: () => props.visible,
  set: (value) => emit("update:visible", value),
});

const isMoonSizeDisabled = computed(
  () => selectedMoonDisplayMode.value === "none",
);

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

// Weekend style options with preview colors for inline swatch display
const weekendStyleOptions = [
  {
    id: "none" as const,
    name: "None",
    theme: "none",
    previewColors: ["transparent"],
  },
  {
    id: "greyscale" as const,
    name: "Greyscale",
    theme: "default",
    previewColors: ["#f0f0f0"],
  },
  {
    id: "rainbow" as const,
    name: "Rainbow",
    theme: "rainbowWeekends",
    previewColors: [
      "hsl(0, 100%, 90%)",
      "hsl(60, 100%, 90%)",
      "hsl(120, 100%, 90%)",
      "hsl(180, 100%, 90%)",
      "hsl(240, 100%, 90%)",
      "hsl(300, 100%, 90%)",
    ],
  },
  {
    id: "vermont" as const,
    name: "Vermont",
    theme: "vermontWeekends",
    previewColors: [
      "#E8F4FA",
      "#7CFC00",
      "#58D68D",
      "#FAD7A0",
      "#FF4500",
      "#C0C0C0",
    ],
  },
  {
    id: "lakeshore" as const,
    name: "Lakeshore",
    theme: "lakeshoreWeekends",
    previewColors: [
      "#e3f2fd",
      "#b3e5fc",
      "#80deea",
      "#a7ffeb",
      "#b2dfdb",
      "#bbdefb",
    ],
  },
  {
    id: "sunset" as const,
    name: "Sunset",
    theme: "sunsetWeekends",
    previewColors: [
      "#fce4ec",
      "#ffcdd2",
      "#ffe0b2",
      "#ffecb3",
      "#ffd180",
      "#f8bbd0",
    ],
  },
  {
    id: "forest" as const,
    name: "Forest",
    theme: "forestWeekends",
    previewColors: [
      "#e8f5e9",
      "#dcedc8",
      "#aed581",
      "#a5d6a7",
      "#d7ccc8",
      "#efebe9",
    ],
  },
];

// Weekend color swatches for solid colors (lighter, suitable for backgrounds)
const weekendColorSwatches = [
  // Light grays
  "#f5f5f5",
  "#e8e8e8",
  // Light blue
  "#e3f2fd",
  // Light green
  "#e8f5e9",
  // Light yellow
  "#fffde7",
  // Light orange
  "#fff3e0",
  // Light pink
  "#fce4ec",
  // Light purple
  "#f3e5f5",
  // Light teal
  "#e0f2f1",
];

// Compact color swatches - flat array for inline display
const colorSwatches = [
  // Grays
  "#000000",
  "#333333",
  "#666666",
  "#999999",
  "#cccccc",
  // Earth tones
  "#4a2c2a",
  "#8b5a2b",
  "#cd853f",
  // Reds
  "#8b0000",
  "#dc143c",
  "#ff6347",
  // Oranges/Yellows
  "#ff8c00",
  "#ffd700",
  "#ffff00",
  // Greens
  "#006400",
  "#228b22",
  "#90ee90",
  // Blues
  "#00008b",
  "#0000ff",
  "#87ceeb",
  // Purples
  "#4b0082",
  "#9932cc",
  "#dda0dd",
  // Pinks
  "#c71585",
  "#ff69b4",
  "#ffc0cb",
];

// Emoji style options with CSS filter for preview
const emojiStyleOptions: {
  id: EmojiFontType;
  label: string;
  filter: string;
  color?: string;
}[] = [
  { id: "noto-color", label: "Full Color", filter: "none" },
  {
    id: "noto-mono",
    label: "Black & White",
    filter: "grayscale(100%) contrast(1.2)",
  },
  {
    id: "mono-red",
    label: "Red",
    filter: "grayscale(100%) sepia(100%) saturate(10) hue-rotate(-10deg)",
    color: "#DC2626",
  },
  {
    id: "mono-blue",
    label: "Blue",
    filter: "grayscale(100%) sepia(100%) saturate(10) hue-rotate(180deg)",
    color: "#2563EB",
  },
  {
    id: "mono-green",
    label: "Green",
    filter: "grayscale(100%) sepia(100%) saturate(10) hue-rotate(80deg)",
    color: "#16A34A",
  },
  {
    id: "mono-orange",
    label: "Orange",
    filter: "grayscale(100%) sepia(100%) saturate(10) hue-rotate(20deg)",
    color: "#EA580C",
  },
  {
    id: "mono-purple",
    label: "Purple",
    filter: "grayscale(100%) sepia(100%) saturate(10) hue-rotate(240deg)",
    color: "#9333EA",
  },
  {
    id: "mono-pink",
    label: "Pink",
    filter: "grayscale(100%) sepia(100%) saturate(10) hue-rotate(300deg)",
    color: "#EC4899",
  },
  {
    id: "mono-teal",
    label: "Teal",
    filter: "grayscale(100%) sepia(100%) saturate(10) hue-rotate(140deg)",
    color: "#0D9488",
  },
  {
    id: "mono-brown",
    label: "Brown",
    filter:
      "grayscale(100%) sepia(100%) saturate(5) hue-rotate(350deg) brightness(0.7)",
    color: "#92400E",
  },
  {
    id: "mono-navy",
    label: "Navy",
    filter:
      "grayscale(100%) sepia(100%) saturate(10) hue-rotate(200deg) brightness(0.6)",
    color: "#1E3A5F",
  },
  {
    id: "mono-coral",
    label: "Coral",
    filter: "grayscale(100%) sepia(100%) saturate(8) hue-rotate(5deg)",
    color: "#F97316",
  },
];

// Random day for preview (1-28 to be safe)
const previewDay = Math.floor(Math.random() * 28) + 1;

// Emoji for style picker preview (URL-encoded 🎉 Party Popper)
const previewEmoji = "%F0%9F%8E%89";

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

// Handle moon display mode change from dropdown
const handleMoonDisplayModeChange = () => {
  // If selecting a moon display mode (not "none") and no moon size is selected,
  // auto-select "full-size" as the default
  if (
    selectedMoonDisplayMode.value &&
    selectedMoonDisplayMode.value !== "none" &&
    !selectedMoonStyle.value
  ) {
    selectedMoonStyle.value = "full-size";
  }
  emitMoonSettings();
};

// Emit display options
const emitDisplayOptions = () => {
  emit("displayOptionsChange", {
    weekendStyle: selectedWeekendStyle.value || "greyscale",
    solidWeekendColor: solidWeekendColor.value,
    showGrid: showGrid.value,
    showDayNames: showDayNames.value,
    rotateMonthNames: rotateMonthNames.value,
  });

  // Re-emit moon settings in case day names changed (affects moon Y position)
  emitMoonSettings();
};

// Select weekend theme from inline swatches
const selectWeekendTheme = (themeId: WeekendStyleType) => {
  selectedWeekendStyle.value = themeId;
  solidWeekendColor.value = null;
  emitDisplayOptions();
};

// Select solid weekend color from inline swatches
const selectSolidWeekendColor = (color: string) => {
  solidWeekendColor.value = color;
  selectedWeekendStyle.value = null;
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
    holidayColor: holidayColor.value,
    emojiFont: emojiFont.value,
  });
};

// Individual color change handlers - explicitly update ref then emit
const handleYearColorChange = (color: string) => {
  yearColor.value = color;
  emitColorSettings();
};
const handleMonthColorChange = (color: string) => {
  monthColor.value = color;
  emitColorSettings();
};
const handleDayTextColorChange = (color: string) => {
  dayTextColor.value = color;
  emitColorSettings();
};
const handleDayNameColorChange = (color: string) => {
  dayNameColor.value = color;
  emitColorSettings();
};
const handleGridLineColorChange = (color: string) => {
  gridLineColor.value = color;
  emitColorSettings();
};
const handleHolidayColorChange = (color: string) => {
  holidayColor.value = color;
  emitColorSettings();
};

const handleClose = () => {
  isOpen.value = false;
};

// Watch for display option changes
watch([showGrid, showDayNames, rotateMonthNames], () => {
  if (!isInitializing.value) {
    emitDisplayOptions();
  }
});

// Watch for emoji font changes (not a VSwatches component, so needs watch)
watch(emojiFont, () => {
  if (!isInitializing.value) {
    emitColorSettings();
  }
});

// Initialize wizard state from config
const initializeFromConfig = () => {
  if (!props.config) return;

  // Set flag to prevent watchers from emitting during initialization
  isInitializing.value = true;

  // Initialize layout
  if (props.config.layoutStyle === "grid") {
    selectedLayout.value = "left-aligned";
  } else if (props.config.layoutStyle === "weekday-grid") {
    selectedLayout.value = "day-of-week-aligned";
  }

  // Initialize moon display mode
  if (props.config.moonDisplayMode) {
    selectedMoonDisplayMode.value = props.config
      .moonDisplayMode as MoonDisplayModeType;
  }

  // Initialize moon size/style based on moonSize
  if (props.config.moonSize !== undefined && props.config.moonSize > 0) {
    // Determine which preset matches best
    if (props.config.moonSize >= 15) {
      selectedMoonStyle.value = "full-size";
    } else {
      selectedMoonStyle.value = "small-corner";
    }
  }

  // Initialize theme/weekend style
  if (props.config.weekendBgColor) {
    // If there's a solid weekend color, use that
    solidWeekendColor.value = props.config.weekendBgColor;
    selectedWeekendStyle.value = null;
  } else if (props.config.theme) {
    const themeToWeekendStyle: Record<string, WeekendStyleType> = {
      default: "greyscale",
      rainbowWeekends: "rainbow",
      vermontWeekends: "vermont",
      lakeshoreWeekends: "lakeshore",
      sunsetWeekends: "sunset",
      forestWeekends: "forest",
    };
    selectedWeekendStyle.value =
      themeToWeekendStyle[props.config.theme] || "greyscale";
    solidWeekendColor.value = null;
  }

  // Initialize display options
  if (props.config.showGrid !== undefined) {
    showGrid.value = props.config.showGrid;
  }
  if (props.config.showDayNames !== undefined) {
    showDayNames.value = props.config.showDayNames;
  }
  if (props.config.rotateMonthNames !== undefined) {
    rotateMonthNames.value = props.config.rotateMonthNames;
  }

  // Initialize colors
  if (props.config.yearColor) {
    yearColor.value = props.config.yearColor;
  }
  if (props.config.monthColor) {
    monthColor.value = props.config.monthColor;
  }
  if (props.config.dayTextColor) {
    dayTextColor.value = props.config.dayTextColor;
  }
  if (props.config.dayNameColor) {
    dayNameColor.value = props.config.dayNameColor;
  }
  if (props.config.gridLineColor) {
    gridLineColor.value = props.config.gridLineColor;
  }
  if (props.config.holidayColor) {
    holidayColor.value = props.config.holidayColor;
  }
  if (props.config.emojiFont) {
    emojiFont.value = props.config.emojiFont;
  }

  // Initialize holiday sets from config
  if (props.config.holidaySets && props.config.holidaySets.length > 0) {
    // First item goes to primary dropdown, rest go to additional list
    primaryHolidaySet.value = props.config.holidaySets[0];
    additionalHolidaySets.value = props.config.holidaySets.slice(1);
  } else {
    // No holidays selected - reset to defaults
    primaryHolidaySet.value = "none";
    additionalHolidaySets.value = [];
  }

  // Initialize event display mode
  if (props.config.eventDisplayMode) {
    eventDisplayMode.value = props.config.eventDisplayMode as EventDisplayMode;
  }

  // Clear flag after Vue processes watchers
  nextTick(() => {
    isInitializing.value = false;
  });
};

// Load previews when drawer opens and initialize state from config
watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      // Initialize wizard state from current config
      initializeFromConfig();

      // Load layout previews if not already loaded
      if (Object.keys(layoutPreviews.value).length === 0) {
        loadLayoutPreviews();
      }
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
          <Step>Choose Your Layout</Step>
          <StepPanel v-slot="{ activateCallback }">
            <div class="step-content">
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

        <!-- Step 2: Additional Options -->
        <StepItem value="2">
          <Step>Additional Options</Step>
          <StepPanel v-slot="{ activateCallback }">
            <div class="step-content">
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

        <!-- Step 3: Moon Display -->
        <StepItem value="3">
          <Step>Moon Display</Step>
          <StepPanel v-slot="{ activateCallback }">
            <div class="step-content">
              <!-- Moon Display Mode Selection (Dropdown) -->
              <h4 class="subsection-title">Which Moons to Show</h4>
              <Select
                v-model="selectedMoonDisplayMode"
                :options="moonDisplayModeOptions"
                option-label="name"
                option-value="id"
                placeholder="Select moon display"
                class="w-full"
                @change="handleMoonDisplayModeChange"
              />

              <!-- Moon Size Selection -->
              <div
                class="moon-size-section"
                :class="{ disabled: isMoonSizeDisabled }"
              >
                <h4 class="subsection-title">Moon Size</h4>
                <p class="step-description">
                  Choose how large the moon appears in each cell
                </p>
                <div class="moon-options">
                  <div
                    v-for="moon in moonSizeOptions"
                    :key="moon.id"
                    class="moon-option"
                    :class="{
                      selected:
                        selectedMoonStyle === moon.id && !isMoonSizeDisabled,
                      disabled: isMoonSizeDisabled,
                    }"
                    @click="!isMoonSizeDisabled && selectMoonStyle(moon.id)"
                  >
                    <RadioButton
                      v-model="selectedMoonStyle"
                      :input-id="'moon-' + moon.id"
                      :value="moon.id"
                      name="moonSize"
                      :disabled="isMoonSizeDisabled"
                    />
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
                  </div>
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

        <!-- Step 4: Holidays -->
        <StepItem value="4">
          <Step>Holidays</Step>
          <StepPanel v-slot="{ activateCallback }">
            <div class="step-content">
              <p class="step-description">
                Add holiday sets to your calendar. You can select multiple sets.
              </p>

              <!-- Holiday Set Selector -->
              <div class="holiday-selector">
                <div class="holiday-add-row">
                  <Select
                    v-model="primaryHolidaySet"
                    :options="availableHolidaySets"
                    option-label="label"
                    option-value="value"
                    class="holiday-dropdown"
                    @change="handlePrimaryHolidayChange"
                  />
                  <Button
                    v-tooltip="'Add to list and select another'"
                    icon="pi pi-plus"
                    :disabled="
                      primaryHolidaySet === 'none' ||
                      additionalHolidaySets.includes(primaryHolidaySet)
                    "
                    @click="addHolidaySet"
                  />
                </div>

                <!-- Additional Holiday Sets (items added via + button) -->
                <div
                  v-if="additionalHolidaySets.length > 0"
                  class="selected-holidays"
                >
                  <p class="additional-label">Also showing:</p>
                  <div
                    v-for="setId in additionalHolidaySets"
                    :key="setId"
                    class="holiday-chip"
                  >
                    <span>{{ getHolidaySetLabel(setId) }}</span>
                    <Button
                      v-tooltip="'Remove'"
                      icon="pi pi-times"
                      text
                      rounded
                      severity="secondary"
                      size="small"
                      @click="removeHolidaySet(setId)"
                    />
                  </div>
                </div>
              </div>

              <!-- Event Display Mode -->
              <div class="display-mode-section">
                <h4 class="subsection-title">Event & Holiday Display</h4>

                <div class="event-display-options">
                  <!-- Prominent (large emoji) - DEFAULT -->
                  <div
                    class="event-display-option"
                    :class="{ selected: eventDisplayMode === 'large' }"
                    @click="
                      eventDisplayMode = 'large';
                      emitHolidaySettings();
                    "
                  >
                    <RadioButton
                      v-model="eventDisplayMode"
                      input-id="event-large"
                      value="large"
                      name="eventDisplayMode"
                    />
                    <div class="event-preview">
                      <div class="event-preview-cell">
                        <span class="cell-day">25</span>
                        <span class="cell-emoji large">🎄</span>
                      </div>
                    </div>
                    <div class="event-info">
                      <div class="event-name">Prominent</div>
                      <div class="event-description">Large centered emoji</div>
                    </div>
                  </div>

                  <!-- Prominent with text -->
                  <div
                    class="event-display-option"
                    :class="{ selected: eventDisplayMode === 'large-text' }"
                    @click="
                      eventDisplayMode = 'large-text';
                      emitHolidaySettings();
                    "
                  >
                    <RadioButton
                      v-model="eventDisplayMode"
                      input-id="event-large-text"
                      value="large-text"
                      name="eventDisplayMode"
                    />
                    <div class="event-preview">
                      <div class="event-preview-cell">
                        <span class="cell-day">25</span>
                        <span class="cell-emoji large">🎄</span>
                        <span class="cell-text">Christmas</span>
                      </div>
                    </div>
                    <div class="event-info">
                      <div class="event-name">Emoji + Text</div>
                      <div class="event-description">
                        Emoji with holiday name below
                      </div>
                    </div>
                  </div>

                  <!-- Compact (small emoji) -->
                  <div
                    class="event-display-option"
                    :class="{ selected: eventDisplayMode === 'small' }"
                    @click="
                      eventDisplayMode = 'small';
                      emitHolidaySettings();
                    "
                  >
                    <RadioButton
                      v-model="eventDisplayMode"
                      input-id="event-small"
                      value="small"
                      name="eventDisplayMode"
                    />
                    <div class="event-preview">
                      <div class="event-preview-cell">
                        <span class="cell-day">25</span>
                        <span class="cell-emoji small">🎄</span>
                      </div>
                    </div>
                    <div class="event-info">
                      <div class="event-name">Compact</div>
                      <div class="event-description">Small emoji in corner</div>
                    </div>
                  </div>

                  <!-- Text only -->
                  <div
                    class="event-display-option"
                    :class="{ selected: eventDisplayMode === 'text' }"
                    @click="
                      eventDisplayMode = 'text';
                      emitHolidaySettings();
                    "
                  >
                    <RadioButton
                      v-model="eventDisplayMode"
                      input-id="event-text"
                      value="text"
                      name="eventDisplayMode"
                    />
                    <div class="event-preview">
                      <div class="event-preview-cell">
                        <span class="cell-day holiday-color">25</span>
                        <span class="cell-text-only">Christmas</span>
                      </div>
                    </div>
                    <div class="event-info">
                      <div class="event-name">Text Only</div>
                      <div class="event-description">
                        Holiday name, no emoji
                      </div>
                    </div>
                  </div>

                  <!-- Color only (no emoji, no text) -->
                  <div
                    class="event-display-option"
                    :class="{ selected: eventDisplayMode === 'none' }"
                    @click="
                      eventDisplayMode = 'none';
                      emitHolidaySettings();
                    "
                  >
                    <RadioButton
                      v-model="eventDisplayMode"
                      input-id="event-none"
                      value="none"
                      name="eventDisplayMode"
                    />
                    <div class="event-preview">
                      <div class="event-preview-cell">
                        <span class="cell-day holiday-color">25</span>
                      </div>
                    </div>
                    <div class="event-info">
                      <div class="event-name">Color Only</div>
                      <div class="event-description">
                        Date color changes, no emoji or text
                      </div>
                    </div>
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

        <!-- Step 5: Weekend Colors -->
        <StepItem value="5">
          <Step>Weekend Colors</Step>
          <StepPanel v-slot="{ activateCallback }">
            <div class="step-content">
              <div class="weekend-color-controls">
                <!-- Theme Selection as Radio Buttons -->
                <div class="weekend-theme-section">
                  <label class="color-label">Color Theme</label>
                  <div class="weekend-theme-options">
                    <div
                      v-for="option in weekendStyleOptions"
                      :key="option.id"
                      class="weekend-theme-option"
                      :class="{
                        selected:
                          selectedWeekendStyle === option.id &&
                          !solidWeekendColor,
                      }"
                      @click="selectWeekendTheme(option.id)"
                    >
                      <RadioButton
                        v-model="selectedWeekendStyle"
                        :input-id="'weekend-theme-' + option.id"
                        :value="option.id"
                        name="weekendTheme"
                        :disabled="!!solidWeekendColor"
                      />
                      <label
                        :for="'weekend-theme-' + option.id"
                        class="weekend-theme-label"
                      >
                        {{ option.name }}
                      </label>
                    </div>
                  </div>
                </div>

                <!-- Solid Color Selection as Inline Swatches -->
                <div class="weekend-solid-section">
                  <label class="color-label">Or Solid Color</label>
                  <div class="weekend-solid-swatches">
                    <div
                      v-for="color in weekendColorSwatches"
                      :key="color"
                      class="weekend-solid-swatch"
                      :class="{ selected: solidWeekendColor === color }"
                      :style="{ backgroundColor: color }"
                      :title="color"
                      @click="selectSolidWeekendColor(color)"
                    />
                  </div>
                </div>
              </div>

              <div class="step-navigation">
                <Button
                  label="Previous"
                  icon="pi pi-arrow-left"
                  outlined
                  @click="activateCallback('4')"
                />
                <Button
                  label="Next"
                  icon="pi pi-arrow-right"
                  icon-pos="right"
                  @click="activateCallback('6')"
                />
              </div>
            </div>
          </StepPanel>
        </StepItem>

        <!-- Step 6: Text Colors -->
        <StepItem value="6">
          <Step>Text Colors</Step>
          <StepPanel v-slot="{ activateCallback }">
            <div class="step-content">
              <div class="color-options">
                <div class="color-option">
                  <label class="color-label">Year Color</label>
                  <VSwatches
                    :model-value="yearColor"
                    :swatches="colorSwatches"
                    :swatch-size="24"
                    :row-length="10"
                    popover-x="left"
                    @update:model-value="handleYearColorChange"
                  />
                </div>

                <div class="color-option">
                  <label class="color-label">Month Names</label>
                  <VSwatches
                    :model-value="monthColor"
                    :swatches="colorSwatches"
                    :swatch-size="24"
                    :row-length="10"
                    popover-x="left"
                    @update:model-value="handleMonthColorChange"
                  />
                </div>

                <div class="color-option">
                  <label class="color-label">Day Numbers</label>
                  <VSwatches
                    :model-value="dayTextColor"
                    :swatches="colorSwatches"
                    :swatch-size="24"
                    :row-length="10"
                    popover-x="left"
                    @update:model-value="handleDayTextColorChange"
                  />
                </div>

                <div class="color-option">
                  <label class="color-label">Day Names</label>
                  <VSwatches
                    :model-value="dayNameColor"
                    :swatches="colorSwatches"
                    :swatch-size="24"
                    :row-length="10"
                    popover-x="left"
                    @update:model-value="handleDayNameColorChange"
                  />
                </div>

                <div class="color-option">
                  <label class="color-label">Grid Lines</label>
                  <VSwatches
                    :model-value="gridLineColor"
                    :swatches="colorSwatches"
                    :swatch-size="24"
                    :row-length="10"
                    popover-x="left"
                    @update:model-value="handleGridLineColorChange"
                  />
                </div>

                <div class="color-option">
                  <label class="color-label">Holidays</label>
                  <VSwatches
                    :model-value="holidayColor"
                    :swatches="colorSwatches"
                    :swatch-size="24"
                    :row-length="10"
                    popover-x="left"
                    @update:model-value="handleHolidayColorChange"
                  />
                </div>

                <div class="color-option">
                  <label class="color-label">Emojis</label>
                  <div
                    class="emoji-color-trigger"
                    :title="currentEmojiStyle.label"
                    @click="toggleEmojiPopover"
                  >
                    <img
                      :src="`/api/calendar/emoji-preview?emoji=${previewEmoji}&style=${emojiFont}`"
                      alt="Emoji style preview"
                      class="emoji-trigger-img"
                    />
                  </div>
                  <Popover
                    ref="emojiPopover"
                    :pt="{ root: { class: 'emoji-popover-left' } }"
                  >
                    <div class="emoji-popover-content">
                      <div class="emoji-popover-swatches">
                        <div
                          v-for="option in emojiStyleOptions"
                          :key="option.id"
                          class="emoji-popover-swatch"
                          :class="{ selected: emojiFont === option.id }"
                          :title="option.label"
                          @click="selectEmojiStyle(option)"
                        >
                          <img
                            :src="`/api/calendar/emoji-preview?emoji=${previewEmoji}&style=${option.id}`"
                            :alt="option.label"
                            class="emoji-preview-img"
                          />
                        </div>
                      </div>
                    </div>
                  </Popover>
                </div>
              </div>

              <div class="step-navigation">
                <Button
                  label="Previous"
                  icon="pi pi-arrow-left"
                  outlined
                  @click="activateCallback('5')"
                />
                <Button
                  label="Next"
                  icon="pi pi-arrow-right"
                  icon-pos="right"
                  @click="activateCallback('7')"
                />
              </div>
            </div>
          </StepPanel>
        </StepItem>

        <!-- Step 7: Finish -->
        <StepItem value="7">
          <Step>Your Calendar is Ready!</Step>
          <StepPanel v-slot="{ activateCallback }">
            <div class="step-content">
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
                  @click="activateCallback('6')"
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

/* Disabled state for moon size section */
.moon-size-section.disabled {
  opacity: 0.5;
  pointer-events: none;
}

.moon-size-section.disabled .subsection-title,
.moon-size-section.disabled .step-description {
  color: var(--text-color-secondary);
}

.moon-option.disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.moon-option.disabled:hover {
  border-color: var(--surface-200);
  background: var(--surface-0);
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

/* Weekend color controls */
.weekend-color-controls {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  margin-bottom: 0.5rem;
}

.weekend-theme-section,
.weekend-solid-section {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

/* Weekend theme radio options */
.weekend-theme-options {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.weekend-theme-option {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  padding: 0.5rem 0.625rem;
  border: 2px solid var(--surface-200);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  background: var(--surface-0);
}

.weekend-theme-option:hover {
  border-color: var(--primary-300);
  background: var(--surface-50);
}

.weekend-theme-option.selected {
  border-color: var(--primary-color);
  background: var(--primary-50);
}

.weekend-theme-label {
  font-size: 0.875rem;
  color: var(--text-color);
  cursor: pointer;
}

/* Weekend solid color inline swatches */
.weekend-solid-swatches {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
}

.weekend-solid-swatch {
  width: 28px;
  height: 28px;
  border-radius: 4px;
  border: 2px solid var(--surface-border);
  cursor: pointer;
  transition: all 0.15s ease;
}

.weekend-solid-swatch:hover {
  transform: scale(1.1);
  border-color: var(--text-color-secondary);
}

.weekend-solid-swatch.selected {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 2px var(--primary-color);
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

.color-label {
  font-weight: 500;
  font-size: 0.875rem;
  color: var(--text-color);
}

/* Vue Swatches customization */
.color-option :deep(.vue-swatches) {
  display: flex;
  align-items: center;
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
  max-width: 280px;
}

.color-option :deep(.vue-swatches__wrapper) {
  padding: 0;
  gap: 0;
}

.color-option :deep(.vue-swatches__swatch) {
  margin: 1px;
  border-radius: 2px;
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

/* Holiday selector styles */
.holiday-selector {
  margin-bottom: 1.5rem;
}

.holiday-add-row {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.holiday-dropdown {
  flex: 1;
}

.selected-holidays {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  min-height: 60px;
  padding: 0.75rem;
  background: var(--surface-50);
  border-radius: 8px;
  border: 1px solid var(--surface-200);
}

.additional-label {
  font-size: 0.85rem;
  color: var(--text-color-secondary);
  margin: 0 0 0.25rem 0;
}

.no-holidays {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  color: var(--text-color-secondary);
  font-size: 0.875rem;
  padding: 0.5rem;
}

.no-holidays i {
  font-size: 1rem;
}

.holiday-chip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.5rem 0.75rem;
  background: var(--surface-0);
  border: 1px solid var(--surface-200);
  border-radius: 6px;
  font-size: 0.875rem;
}

.holiday-chip span {
  font-weight: 500;
}

/* Display mode section */
.display-mode-section {
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid var(--surface-200);
}

.section-title {
  font-size: 0.9375rem;
  font-weight: 600;
  color: var(--text-color);
  margin: 0 0 0.25rem 0;
}

.section-description {
  font-size: 0.8125rem;
  color: var(--text-color-secondary);
  margin-bottom: 1rem;
}

/* Event Display Options - compact layout */
.event-display-options {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.event-display-option {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  padding: 0.5rem 0.625rem;
  border: 2px solid var(--surface-200);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  background: var(--surface-0);
}

.event-display-option:hover {
  border-color: var(--primary-300);
  background: var(--surface-50);
}

.event-display-option.selected {
  border-color: var(--primary-color);
  background: var(--primary-50);
}

.event-preview {
  flex-shrink: 0;
  width: 50px;
  height: 58px;
  border-radius: 3px;
  overflow: hidden;
  background: white;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
  border: 1px solid var(--surface-200);
  display: flex;
  align-items: center;
  justify-content: center;
}

.event-preview-cell {
  width: 46px;
  height: 54px;
  background: var(--surface-50);
  border: 1px solid var(--surface-300);
  border-radius: 2px;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cell-day {
  position: absolute;
  top: 2px;
  left: 4px;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-color);
}

.cell-day.holiday-color {
  color: #ff5252;
}

.cell-emoji {
  position: absolute;
}

.cell-emoji.small {
  bottom: 4px;
  left: 4px;
  font-size: 12px;
}

.cell-emoji.large {
  top: 45%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 18px;
}

.cell-text {
  position: absolute;
  bottom: 2px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 5px;
  color: var(--text-color-secondary);
  white-space: nowrap;
}

.cell-text-only {
  position: absolute;
  top: 55%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 6px;
  color: var(--text-color);
  white-space: nowrap;
  font-weight: 500;
}

.event-info {
  flex: 1;
  min-width: 0;
}

.event-name {
  font-weight: 600;
  font-size: 0.8125rem;
  color: var(--text-color);
  margin-bottom: 0.125rem;
}

.event-description {
  font-size: 0.6875rem;
  color: var(--text-color-secondary);
}

/* Emoji color trigger (inline with other color pickers, matches vue-swatches) */
.emoji-color-trigger {
  width: 42px;
  height: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s ease;
  background: var(--surface-0);
  border: 1px solid #ccc;
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.1);
}

.emoji-color-trigger:hover {
  background: var(--surface-50);
  border-color: var(--surface-400);
}

.emoji-trigger-img {
  width: 32px;
  height: 32px;
  object-fit: contain;
}

/* Emoji popover picker */
.emoji-popover-content {
  padding: 8px;
}

.emoji-popover-swatches {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  max-width: 230px;
}

.emoji-popover-swatch {
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s ease;
  background: var(--surface-50);
  border: 2px solid transparent;
}

.emoji-popover-swatch:hover {
  transform: scale(1.1);
  background: var(--surface-100);
}

.emoji-popover-swatch.selected {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 2px var(--primary-200);
  background: var(--primary-50);
}

.emoji-preview-img {
  width: 26px;
  height: 26px;
  object-fit: contain;
}
</style>

<!-- Unscoped styles for Popover positioning (appended to body) -->
<style>
.emoji-popover-left {
  /* Align right edge of popover with right edge of 42px trigger */
  transform: translateX(calc(-100% + 42px));
}

.emoji-popover-left::before,
.emoji-popover-left::after {
  /* Hide the arrow since it won't align properly */
  display: none !important;
}

.emoji-popover-left .p-popover-content {
  padding: 0;
}
</style>
