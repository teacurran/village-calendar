<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from "vue";
import Button from "primevue/button";
import ProgressSpinner from "primevue/progressspinner";
import MazeWizardDrawer from "../../components/maze/MazeWizardDrawer.vue";
import AddToCartModal from "../../components/calendar/AddToCartModal.vue";
import { useMazeEditorStore } from "../../stores/mazeEditor";
import { useCartStore } from "../../stores/cart";

// Store
const mazeEditorStore = useMazeEditorStore();
const cartStore = useCartStore();

// PRINT DIMENSIONS (35" x 23" at 100 units per inch)
const PAGE_WIDTH_INCHES = 35;
const PAGE_HEIGHT_INCHES = 23;
const UNITS_PER_INCH = 100;
const PAGE_WIDTH = PAGE_WIDTH_INCHES * UNITS_PER_INCH; // 3500
const PAGE_HEIGHT = PAGE_HEIGHT_INCHES * UNITS_PER_INCH; // 2300

// Maze configuration
const config = ref({
  mazeType: "ORTHOGONAL",
  size: 10, // 1-20: controls cell count
  difficulty: 3, // 1-5: controls shortcuts (1=easy/many, 5=hard/none)
  showSolution: false,
  showDeadEnds: false, // Highlight dead-end paths for visualization
  innerWallColor: "#000000",
  outerWallColor: "#000000",
  pathColor: "#4CAF50",
});

// UI State
const mazeSvg = ref("");
const previousSvg = ref(""); // For crossfade transition
const isTransitioning = ref(false);
const loading = ref(false);
const error = ref("");
const showWizard = ref(false);
const previewContainer = ref<HTMLElement | null>(null);

// Computed properties synced with store
const zoomLevel = computed({
  get: () => mazeEditorStore.zoomLevel,
  set: (value) => mazeEditorStore.setZoomLevel(value),
});

const showRulers = computed({
  get: () => mazeEditorStore.showRulers,
  set: (value) => {
    if (value !== mazeEditorStore.showRulers) {
      mazeEditorStore.toggleRulers();
    }
  },
});

// Add to cart modal visibility - synced with store for header access
const showAddToCartModal = computed({
  get: () => mazeEditorStore.showAddToCartModal,
  set: (value) => {
    if (value) {
      mazeEditorStore.openAddToCartModal();
    } else {
      mazeEditorStore.closeAddToCartModal();
    }
  },
});

// Session management
const getSessionId = () => {
  let sessionId = localStorage.getItem("maze_session_id");
  if (!sessionId) {
    sessionId = "session_" + Math.random().toString(36).substring(2, 15);
    localStorage.setItem("maze_session_id", sessionId);
  }
  return sessionId;
};

// Crossfade to new SVG content
const crossfadeToNewSvg = (newSvg: string) => {
  if (mazeSvg.value && mazeSvg.value !== newSvg) {
    // Store the current SVG for crossfade
    previousSvg.value = mazeSvg.value;
    isTransitioning.value = true;

    // Update to new SVG
    mazeSvg.value = newSvg;

    // After transition completes, clean up
    setTimeout(() => {
      previousSvg.value = "";
      isTransitioning.value = false;
    }, 250); // Slightly longer than the CSS transition
  } else {
    // First load or same content, just set directly
    mazeSvg.value = newSvg;
  }
};

// Generate maze preview (deterministic seed for preview)
const generatePreview = async () => {
  // Don't show loading spinner during regeneration - keep old maze visible
  const isFirstLoad = !mazeSvg.value;
  if (isFirstLoad) {
    loading.value = true;
  }
  error.value = "";

  try {
    const query = `
      query MazePreview($type: MazeType!, $size: Int!, $difficulty: Int!, $showSolution: Boolean!, $showDeadEnds: Boolean!) {
        mazePreview(type: $type, size: $size, difficulty: $difficulty, showSolution: $showSolution, showDeadEnds: $showDeadEnds)
      }
    `;

    const response = await fetch("/graphql", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        query,
        variables: {
          type: config.value.mazeType,
          size: config.value.size,
          difficulty: config.value.difficulty,
          showSolution: config.value.showSolution,
          showDeadEnds: config.value.showDeadEnds,
        },
      }),
    });

    const result = await response.json();

    if (result.errors) {
      throw new Error(result.errors[0].message);
    }

    crossfadeToNewSvg(result.data.mazePreview);
    mazeEditorStore.setHasGeneratedSVG(true);
  } catch (e: any) {
    error.value = e.message || "Failed to generate maze";
    console.error("Maze generation error:", e);
    mazeEditorStore.setHasGeneratedSVG(false);
  } finally {
    loading.value = false;
  }
};

// Create a new maze (with random seed)
const createMaze = async () => {
  // Don't show loading spinner - keep old maze visible during generation
  const isFirstLoad = !mazeSvg.value;
  if (isFirstLoad) {
    loading.value = true;
  }
  error.value = "";

  try {
    const mutation = `
      mutation CreateMaze($input: MazeInput!) {
        createMaze(input: $input) {
          id
          name
          generatedSvg
        }
      }
    `;

    const response = await fetch("/graphql", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        query: mutation,
        variables: {
          input: {
            name: "My Maze",
            mazeType: config.value.mazeType,
            size: config.value.size,
            difficulty: config.value.difficulty,
            showSolution: config.value.showSolution,
            innerWallColor: config.value.innerWallColor,
            outerWallColor: config.value.outerWallColor,
            pathColor: config.value.pathColor,
            sessionId: getSessionId(),
          },
        },
      }),
    });

    const result = await response.json();

    if (result.errors) {
      throw new Error(result.errors[0].message);
    }

    crossfadeToNewSvg(result.data.createMaze.generatedSvg);
    mazeEditorStore.setHasGeneratedSVG(true);
  } catch (e: any) {
    error.value = e.message || "Failed to create maze";
    console.error("Maze creation error:", e);
    mazeEditorStore.setHasGeneratedSVG(false);
  } finally {
    loading.value = false;
  }
};

// Zoom controls
const zoomIn = () => {
  mazeEditorStore.setZoomLevel(Math.min(zoomLevel.value + 0.05, 1));
};

const zoomOut = () => {
  mazeEditorStore.setZoomLevel(Math.max(zoomLevel.value - 0.05, 0.1));
};

const resetZoom = () => {
  // Calculate zoom to fill the available width
  if (previewContainer.value) {
    const containerWidth = previewContainer.value.clientWidth - 80; // Account for rulers/padding
    const calculatedZoom = Math.max(
      0.15,
      Math.min(containerWidth / PAGE_WIDTH, 1.0),
    );
    mazeEditorStore.setZoomLevel(calculatedZoom);
  } else {
    mazeEditorStore.setZoomLevel(0.25);
  }
};

// Wizard event handlers
const handleMazeTypeChange = (type: string) => {
  config.value.mazeType = type;
  generatePreview();
};

const handleConfigChange = (newConfig: {
  size: number;
  difficulty: number;
  showSolution: boolean;
  showDeadEnds: boolean;
}) => {
  config.value.size = newConfig.size;
  config.value.difficulty = newConfig.difficulty;
  config.value.showSolution = newConfig.showSolution;
  config.value.showDeadEnds = newConfig.showDeadEnds;
  generatePreview();
};

const handleColorsChange = (colors: {
  innerWallColor: string;
  outerWallColor: string;
  pathColor: string;
}) => {
  config.value.innerWallColor = colors.innerWallColor;
  config.value.outerWallColor = colors.outerWallColor;
  config.value.pathColor = colors.pathColor;
};

// Handle product selection from modal - add to cart
const handleAddToCartSelect = async (productCode: string, _price: number) => {
  await addToCart(productCode);
};

// Generate maze SVG with specific showSolution setting (for cart)
const generateMazeSvgForCart = async (
  showSolution: boolean,
): Promise<string | null> => {
  try {
    const query = `
      query MazePreview($type: MazeType!, $size: Int!, $difficulty: Int!, $showSolution: Boolean!) {
        mazePreview(type: $type, size: $size, difficulty: $difficulty, showSolution: $showSolution)
      }
    `;

    const response = await fetch("/graphql", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        query,
        variables: {
          type: config.value.mazeType,
          size: config.value.size,
          difficulty: config.value.difficulty,
          showSolution,
        },
      }),
    });

    const result = await response.json();

    if (result.errors) {
      throw new Error(result.errors[0].message);
    }

    return result.data.mazePreview;
  } catch (e: any) {
    console.error("Failed to generate maze SVG:", e);
    return null;
  }
};

// Build description for the maze
const getMazeDescription = () => {
  const typeNames: Record<string, string> = {
    ORTHOGONAL: "Orthogonal",
    HEXAGONAL: "Hexagonal",
    TRIANGULAR: "Triangular",
    CIRCULAR: "Circular",
  };
  const difficultyNames = [
    "",
    "Very Easy",
    "Easy",
    "Medium",
    "Hard",
    "Very Hard",
  ];
  const typeName = typeNames[config.value.mazeType] || config.value.mazeType;
  const diffName = difficultyNames[config.value.difficulty] || "";
  return `${diffName} ${typeName} Maze (${config.value.size}x${config.value.size})`;
};

const addToCart = async (productCode: string = "print") => {
  loading.value = true;
  error.value = "";

  try {
    // Generate main maze SVG (without solution) - use current SVG if available and not showing solution
    let mainSvg = config.value.showSolution ? null : mazeSvg.value;
    if (!mainSvg) {
      mainSvg = await generateMazeSvgForCart(false);
    }

    // Generate answer key SVG (with solution)
    const answerKeySvg = await generateMazeSvgForCart(true);

    if (!mainSvg) {
      throw new Error("Failed to generate maze SVG");
    }

    // Build assets array
    const assets = [
      {
        assetKey: "main",
        svgContent: mainSvg,
        widthInches: PAGE_WIDTH_INCHES,
        heightInches: PAGE_HEIGHT_INCHES,
      },
    ];

    // Add answer key if generated
    if (answerKeySvg) {
      assets.push({
        assetKey: "answer_key",
        svgContent: answerKeySvg,
        widthInches: PAGE_WIDTH_INCHES,
        heightInches: PAGE_HEIGHT_INCHES,
      });
    }

    // Add to cart with generic method
    await cartStore.addGenericItem({
      generatorType: "maze",
      description: getMazeDescription(),
      quantity: 1,
      productCode,
      configuration: {
        mazeType: config.value.mazeType,
        size: config.value.size,
        difficulty: config.value.difficulty,
        innerWallColor: config.value.innerWallColor,
        outerWallColor: config.value.outerWallColor,
        pathColor: config.value.pathColor,
      },
      assets,
    });

    // Close the modal and show success
    showAddToCartModal.value = false;
    cartStore.openCart();
  } catch (e: any) {
    error.value = e.message || "Failed to add to cart";
    console.error("Failed to add to cart:", e);
  } finally {
    loading.value = false;
  }
};

// Keyboard shortcuts
const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === "+" || e.key === "=") {
    zoomIn();
  } else if (e.key === "-") {
    zoomOut();
  } else if (e.key === "0") {
    resetZoom();
  }
};

// Open wizard handler
const openWizard = () => {
  showWizard.value = true;
};

// Generate initial preview on mount
onMounted(() => {
  // Activate the maze editor in the store
  mazeEditorStore.activate();

  // Register callbacks for header toolbar
  mazeEditorStore.registerZoomCallbacks(zoomIn, zoomOut, resetZoom);
  mazeEditorStore.registerOpenWizardCallback(openWizard);
  mazeEditorStore.registerGenerateNewCallback(createMaze);

  generatePreview();
  resetZoom();
  window.addEventListener("keydown", handleKeydown);
});

onUnmounted(() => {
  // Deactivate the maze editor
  mazeEditorStore.deactivate();
  window.removeEventListener("keydown", handleKeydown);
});

// Watch for window resize to adjust zoom
let resizeTimeout: ReturnType<typeof setTimeout>;
const handleResize = () => {
  clearTimeout(resizeTimeout);
  resizeTimeout = setTimeout(() => {
    resetZoom();
  }, 100);
};

onMounted(() => {
  window.addEventListener("resize", handleResize);
});

onUnmounted(() => {
  window.removeEventListener("resize", handleResize);
});
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

    <!-- Add to Cart Modal -->
    <AddToCartModal
      v-model:visible="showAddToCartModal"
      :calendar-year="new Date().getFullYear()"
      :default-product-code="mazeEditorStore.defaultProductCode"
      @select="handleAddToCartSelect"
    />

    <!-- Preview Area -->
    <div ref="previewContainer" class="maze-preview">
      <div v-if="loading" class="loading-overlay">
        <ProgressSpinner />
        <p>Generating maze...</p>
      </div>

      <div v-else-if="error" class="error-message">
        <i class="pi pi-exclamation-triangle"></i>
        <p>{{ error }}</p>
        <Button
          label="Try Again"
          icon="pi pi-refresh"
          @click="generatePreview"
        />
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
            <div class="page-border svg-crossfade-container">
              <!-- Previous SVG (fading out) -->
              <div
                v-if="previousSvg"
                class="svg-layer svg-previous"
                :class="{ 'fade-out': isTransitioning }"
                v-html="previousSvg"
              ></div>
              <!-- Current SVG (fading in) -->
              <div
                class="svg-layer svg-current"
                :class="{ 'fade-in': isTransitioning }"
                v-html="mazeSvg"
              ></div>
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
        <Button
          label="Open Configurator"
          icon="pi pi-palette"
          @click="openWizard"
        />
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

/* Crossfade container */
.svg-crossfade-container {
  position: relative;
}

.svg-layer {
  transition: opacity 200ms ease-in-out;
}

.svg-previous {
  position: absolute;
  top: 0;
  left: 0;
  opacity: 1;
}

.svg-previous.fade-out {
  opacity: 0;
}

.svg-current {
  opacity: 1;
}

.svg-current.fade-in {
  opacity: 1;
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
  .maze-preview {
    padding: 1rem;
  }
}
</style>
