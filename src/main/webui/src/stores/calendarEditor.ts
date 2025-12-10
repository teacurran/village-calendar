// ./stores/calendarEditor.ts
import { defineStore } from "pinia";

export const useCalendarEditorStore = defineStore("calendarEditor", {
  state: () => ({
    // Whether the calendar generator is currently active/mounted
    isActive: false,
    // Whether an SVG has been generated
    hasGeneratedSVG: false,
    // Current zoom level (will be recalculated on mount based on window width)
    zoomLevel: 0.5,
    // Calendar year being edited
    calendarYear: new Date().getFullYear(),
    // Whether to show the add to cart modal
    showAddToCartModal: false,
    // Default product selection when modal opens ('print' or 'pdf')
    defaultProductCode: null as string | null,
    // Whether to show rulers on the calendar preview
    showRulers: false,
    // Callbacks for zoom actions (set by CalendarGenerator)
    zoomInCallback: null as (() => void) | null,
    zoomOutCallback: null as (() => void) | null,
    resetZoomCallback: null as (() => void) | null,
    saveAsTemplateCallback: null as (() => void) | null,
  }),

  actions: {
    // Called when CalendarGenerator mounts
    activate() {
      this.isActive = true;
    },

    // Called when CalendarGenerator unmounts
    deactivate() {
      this.isActive = false;
      this.hasGeneratedSVG = false;
      this.zoomInCallback = null;
      this.zoomOutCallback = null;
      this.resetZoomCallback = null;
      this.saveAsTemplateCallback = null;
    },

    setHasGeneratedSVG(value: boolean) {
      this.hasGeneratedSVG = value;
    },

    setZoomLevel(level: number) {
      this.zoomLevel = level;
    },

    setCalendarYear(year: number) {
      this.calendarYear = year;
    },

    // Register zoom callbacks from CalendarGenerator
    registerZoomCallbacks(
      zoomIn: () => void,
      zoomOut: () => void,
      resetZoom: () => void,
    ) {
      this.zoomInCallback = zoomIn;
      this.zoomOutCallback = zoomOut;
      this.resetZoomCallback = resetZoom;
    },

    // Register save as template callback from CalendarGenerator
    registerSaveAsTemplateCallback(callback: () => void) {
      this.saveAsTemplateCallback = callback;
    },

    // Save as template action (admin only)
    saveAsTemplate() {
      if (this.saveAsTemplateCallback) {
        this.saveAsTemplateCallback();
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
