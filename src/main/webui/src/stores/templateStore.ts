/**
 * Template Store
 * Pinia store for managing calendar templates (admin functions)
 */

import { defineStore } from 'pinia'
import type { CalendarTemplate, TemplateInput } from '../types/template'
import {
  fetchTemplates,
  fetchTemplate,
  createTemplate as createTemplateService,
  updateTemplate as updateTemplateService,
  deleteTemplate as deleteTemplateService,
} from '../services/templateService'

export const useTemplateStore = defineStore('template', {
  state: () => ({
    templates: [] as CalendarTemplate[],
    currentTemplate: null as CalendarTemplate | null,
    loading: false,
    error: null as string | null,
  }),

  actions: {
    /**
     * Load all templates (includes inactive for admins)
     */
    async loadTemplates(isActive?: boolean) {
      this.loading = true
      this.error = null

      try {
        const result = await fetchTemplates(isActive)

        if (result.error) {
          this.error = result.error
          return false
        }

        this.templates = result.data || []
        return true
      } catch (err: any) {
        this.error = err.message || 'Failed to load templates'
        return false
      } finally {
        this.loading = false
      }
    },

    /**
     * Load single template by ID
     */
    async loadTemplate(id: string) {
      this.loading = true
      this.error = null

      try {
        const result = await fetchTemplate(id)

        if (result.error) {
          this.error = result.error
          return false
        }

        this.currentTemplate = result.data
        return true
      } catch (err: any) {
        this.error = err.message || 'Failed to load template'
        return false
      } finally {
        this.loading = false
      }
    },

    /**
     * Create new template (admin only)
     */
    async createTemplate(input: TemplateInput) {
      this.loading = true
      this.error = null

      try {
        const result = await createTemplateService(input)

        if (result.error) {
          this.error = result.error
          return null
        }

        if (result.data) {
          // Add to templates array
          this.templates.push(result.data)
        }

        return result.data
      } catch (err: any) {
        this.error = err.message || 'Failed to create template'
        return null
      } finally {
        this.loading = false
      }
    },

    /**
     * Update existing template (admin only)
     */
    async updateTemplate(id: string, input: TemplateInput) {
      this.loading = true
      this.error = null

      try {
        const result = await updateTemplateService(id, input)

        if (result.error) {
          this.error = result.error
          return null
        }

        if (result.data) {
          // Update in templates array
          const index = this.templates.findIndex((t) => t.id === id)
          if (index !== -1) {
            this.templates[index] = result.data
          }
        }

        return result.data
      } catch (err: any) {
        this.error = err.message || 'Failed to update template'
        return null
      } finally {
        this.loading = false
      }
    },

    /**
     * Delete template (admin only)
     */
    async deleteTemplate(id: string) {
      this.loading = true
      this.error = null

      try {
        const result = await deleteTemplateService(id)

        if (result.error) {
          this.error = result.error
          return false
        }

        if (result.data) {
          // Remove from templates array
          this.templates = this.templates.filter((t) => t.id !== id)
        }

        return result.data || false
      } catch (err: any) {
        this.error = err.message || 'Failed to delete template'
        return false
      } finally {
        this.loading = false
      }
    },

    /**
     * Clear error message
     */
    clearError() {
      this.error = null
    },

    /**
     * Clear current template
     */
    clearCurrentTemplate() {
      this.currentTemplate = null
    },
  },

  getters: {
    /**
     * Get active templates only
     */
    activeTemplates: (state) => state.templates.filter((t) => t.isActive),

    /**
     * Get featured templates only
     */
    featuredTemplates: (state) => state.templates.filter((t) => t.isFeatured),

    /**
     * Get template by ID
     */
    getTemplateById: (state) => (id: string) => state.templates.find((t) => t.id === id),

    /**
     * Check if templates are loaded
     */
    hasTemplates: (state) => state.templates.length > 0,
  },
})
