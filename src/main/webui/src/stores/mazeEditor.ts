// ./stores/mazeEditor.ts
import { defineStore } from "pinia";

export const useMazeEditorStore = defineStore("mazeEditor", {
  state: () => ({
    // Whether the maze generator is currently active/mounted
    isActive: false,
    // Whether an SVG has been generated
    hasGeneratedSVG: false,
    // Current zoom level
    zoomLevel: 0.25,
    // Whether to show the add to cart modal
    showAddToCartModal: false,
    // Default product selection when modal opens ('print' or 'pdf')
    defaultProductCode: null as string | null,
    // Whether to show rulers on the maze preview
    showRulers: true,
    // Callbacks for zoom actions (set by MazeGenerator)
    zoomInCallback: null as (() => void) | null,
    zoomOutCallback: null as (() => void) | null,
    resetZoomCallback: null as (() => void) | null,
    // Callback to open the customize wizard
    openWizardCallback: null as (() => void) | null,
    // Callback to generate a new maze
    generateNewCallback: null as (() => void) | null,
  }),

  actions: {
    // Called when MazeGenerator mounts
    activate() {
      this.isActive = true;
    },

    // Called when MazeGenerator unmounts
    deactivate() {
      this.isActive = false;
      this.hasGeneratedSVG = false;
      this.zoomInCallback = null;
      this.zoomOutCallback = null;
      this.resetZoomCallback = null;
      this.openWizardCallback = null;
      this.generateNewCallback = null;
    },

    setHasGeneratedSVG(value: boolean) {
      this.hasGeneratedSVG = value;
    },

    setZoomLevel(level: number) {
      this.zoomLevel = level;
    },

    // Register zoom callbacks from MazeGenerator
    registerZoomCallbacks(
      zoomIn: () => void,
      zoomOut: () => void,
      resetZoom: () => void,
    ) {
      this.zoomInCallback = zoomIn;
      this.zoomOutCallback = zoomOut;
      this.resetZoomCallback = resetZoom;
    },

    // Register open wizard callback from MazeGenerator
    registerOpenWizardCallback(openWizard: () => void) {
      this.openWizardCallback = openWizard;
    },

    // Open the customize wizard
    openWizard() {
      if (this.openWizardCallback) {
        this.openWizardCallback();
      }
    },

    // Register generate new callback from MazeGenerator
    registerGenerateNewCallback(generateNew: () => void) {
      this.generateNewCallback = generateNew;
    },

    // Generate a new maze
    generateNew() {
      if (this.generateNewCallback) {
        this.generateNewCallback();
      }
    },

    // Zoom actions that delegate to callbacks
    zoomIn() {
      if (this.zoomInCallback) {
        this.zoomInCallback();
      }
    },

    zoomOut() {
      if (this.zoomOutCallback) {
        this.zoomOutCallback();
      }
    },

    resetZoom() {
      if (this.resetZoomCallback) {
        this.resetZoomCallback();
      }
    },

    openAddToCartModal(defaultProduct: string | null = null) {
      this.defaultProductCode = defaultProduct;
      this.showAddToCartModal = true;
    },

    closeAddToCartModal() {
      this.showAddToCartModal = false;
      this.defaultProductCode = null;
    },

    toggleRulers() {
      this.showRulers = !this.showRulers;
    },
  },

  getters: {
    canInteract: (state) => state.isActive && state.hasGeneratedSVG,
  },
});
