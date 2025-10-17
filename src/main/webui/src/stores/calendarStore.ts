// ./stores/calendarStore.ts
import { defineStore } from "pinia";
import { useAuthStore } from "./authStore";

export interface CalendarTemplate {
  id: string;
  name: string;
  description?: string;
  thumbnailUrl?: string;
  previewSvg?: string;
  configuration: any;
  displayOrder: number;
  isActive: boolean;
  isFeatured: boolean;
  created: string;
  updated: string;
}

export interface UserCalendar {
  id: string;
  name: string;
  year: number;
  status: "DRAFT" | "GENERATING" | "READY" | "FAILED";
  configuration?: any;
  generatedPdfUrl?: string;
  generatedSvg?: string;
  isPublic: boolean;
  template: CalendarTemplate;
  created: string;
  updated: string;
}

export const useCalendarStore = defineStore("calendar", {
  state: () => ({
    templates: [] as CalendarTemplate[],
    userCalendars: [] as UserCalendar[],
    currentCalendar: null as UserCalendar | null,
    loading: false,
    error: null as string | null,
    lastFetchTime: 0,
  }),

  actions: {
    /**
     * Fetch all active calendar templates
     */
    async fetchTemplates(force: boolean = false) {
      // Skip if we recently fetched (within 60 seconds) unless forced
      const now = Date.now();
      if (
        !force &&
        this.templates.length > 0 &&
        this.lastFetchTime &&
        now - this.lastFetchTime < 60000
      ) {
        return;
      }

      this.loading = true;
      this.error = null;
      try {
        const response = await fetch("/graphql", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            query: `
              query GetTemplates {
                templates(isActive: true) {
                  id
                  name
                  description
                  thumbnailUrl
                  previewSvg
                  configuration
                  displayOrder
                  isActive
                  isFeatured
                  created
                  updated
                }
              }
            `,
          }),
        });

        if (!response.ok) {
          throw new Error("Failed to fetch templates");
        }

        const result = await response.json();

        if (result.errors) {
          throw new Error(result.errors[0]?.message || "GraphQL error");
        }

        if (result.data?.templates) {
          this.templates = result.data.templates;
          this.lastFetchTime = now;
        }
      } catch (err: any) {
        this.error = err.message || "Failed to fetch templates";
        throw err;
      } finally {
        this.loading = false;
      }
    },

    /**
     * Fetch a single template by ID
     */
    async fetchTemplate(templateId: string): Promise<CalendarTemplate | null> {
      this.loading = true;
      this.error = null;
      try {
        const response = await fetch("/graphql", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            query: `
              query GetTemplate($id: ID!) {
                template(id: $id) {
                  id
                  name
                  description
                  thumbnailUrl
                  previewSvg
                  configuration
                  displayOrder
                  isActive
                  isFeatured
                  created
                  updated
                }
              }
            `,
            variables: { id: templateId },
          }),
        });

        if (!response.ok) {
          throw new Error("Failed to fetch template");
        }

        const result = await response.json();

        if (result.errors) {
          throw new Error(result.errors[0]?.message || "GraphQL error");
        }

        return result.data?.template || null;
      } catch (err: any) {
        this.error = err.message || "Failed to fetch template";
        throw err;
      } finally {
        this.loading = false;
      }
    },

    /**
     * Fetch user's calendars
     */
    async fetchUserCalendars() {
      const authStore = useAuthStore();
      if (!authStore.isAuthenticated) {
        this.userCalendars = [];
        return;
      }

      this.loading = true;
      this.error = null;
      try {
        const response = await fetch("/graphql", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${authStore.token}`,
          },
          body: JSON.stringify({
            query: `
              query GetMyCalendars {
                myCalendars {
                  id
                  name
                  year
                  status
                  configuration
                  generatedPdfUrl
                  generatedSvg
                  isPublic
                  created
                  updated
                  template {
                    id
                    name
                    description
                    thumbnailUrl
                  }
                }
              }
            `,
          }),
        });

        if (!response.ok) {
          throw new Error("Failed to fetch user calendars");
        }

        const result = await response.json();

        if (result.errors) {
          throw new Error(result.errors[0]?.message || "GraphQL error");
        }

        if (result.data?.myCalendars) {
          this.userCalendars = result.data.myCalendars;
        }
      } catch (err: any) {
        this.error = err.message || "Failed to fetch user calendars";
        throw err;
      } finally {
        this.loading = false;
      }
    },

    /**
     * Fetch a single calendar by ID
     */
    async fetchCalendar(calendarId: string): Promise<UserCalendar | null> {
      const authStore = useAuthStore();

      this.loading = true;
      this.error = null;
      try {
        const headers: Record<string, string> = {
          "Content-Type": "application/json",
        };

        if (authStore.token) {
          headers["Authorization"] = `Bearer ${authStore.token}`;
        }

        const response = await fetch("/graphql", {
          method: "POST",
          headers,
          body: JSON.stringify({
            query: `
              query GetCalendar($id: ID!) {
                calendar(id: $id) {
                  id
                  name
                  year
                  status
                  configuration
                  generatedPdfUrl
                  generatedSvg
                  isPublic
                  created
                  updated
                  template {
                    id
                    name
                    description
                    thumbnailUrl
                    configuration
                  }
                }
              }
            `,
            variables: { id: calendarId },
          }),
        });

        if (!response.ok) {
          throw new Error("Failed to fetch calendar");
        }

        const result = await response.json();

        if (result.errors) {
          throw new Error(result.errors[0]?.message || "GraphQL error");
        }

        const calendar = result.data?.calendar || null;
        if (calendar) {
          this.currentCalendar = calendar;
        }
        return calendar;
      } catch (err: any) {
        this.error = err.message || "Failed to fetch calendar";
        throw err;
      } finally {
        this.loading = false;
      }
    },

    /**
     * Create a new calendar from a template
     */
    async createCalendar(input: {
      templateId: string;
      name: string;
      year: number;
      configuration?: any;
      isPublic?: boolean;
    }): Promise<UserCalendar> {
      const authStore = useAuthStore();
      if (!authStore.isAuthenticated) {
        throw new Error("Authentication required to create calendar");
      }

      this.loading = true;
      this.error = null;
      try {
        const response = await fetch("/graphql", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${authStore.token}`,
          },
          body: JSON.stringify({
            query: `
              mutation CreateCalendar($input: CalendarInput!) {
                createCalendar(input: $input) {
                  id
                  name
                  year
                  status
                  configuration
                  generatedPdfUrl
                  generatedSvg
                  isPublic
                  created
                  updated
                  template {
                    id
                    name
                    description
                    thumbnailUrl
                    configuration
                  }
                }
              }
            `,
            variables: { input },
          }),
        });

        if (!response.ok) {
          throw new Error("Failed to create calendar");
        }

        const result = await response.json();

        if (result.errors) {
          throw new Error(
            result.errors[0]?.message || "Failed to create calendar",
          );
        }

        const calendar = result.data?.createCalendar;
        if (calendar) {
          this.currentCalendar = calendar;
          this.userCalendars.push(calendar);
          return calendar;
        }
        throw new Error("No calendar returned from mutation");
      } catch (err: any) {
        this.error = err.message || "Failed to create calendar";
        throw err;
      } finally {
        this.loading = false;
      }
    },

    /**
     * Update an existing calendar
     */
    async updateCalendar(
      calendarId: string,
      input: {
        name?: string;
        configuration?: any;
        isPublic?: boolean;
      },
    ): Promise<UserCalendar> {
      const authStore = useAuthStore();
      if (!authStore.isAuthenticated) {
        throw new Error("Authentication required to update calendar");
      }

      this.loading = true;
      this.error = null;
      try {
        const response = await fetch("/graphql", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${authStore.token}`,
          },
          body: JSON.stringify({
            query: `
              mutation UpdateCalendar($id: ID!, $input: CalendarUpdateInput!) {
                updateCalendar(id: $id, input: $input) {
                  id
                  name
                  year
                  status
                  configuration
                  generatedPdfUrl
                  generatedSvg
                  isPublic
                  created
                  updated
                  template {
                    id
                    name
                    description
                    thumbnailUrl
                    configuration
                  }
                }
              }
            `,
            variables: { id: calendarId, input },
          }),
        });

        if (!response.ok) {
          throw new Error("Failed to update calendar");
        }

        const result = await response.json();

        if (result.errors) {
          throw new Error(
            result.errors[0]?.message || "Failed to update calendar",
          );
        }

        const calendar = result.data?.updateCalendar;
        if (calendar) {
          this.currentCalendar = calendar;
          // Update in userCalendars list
          const index = this.userCalendars.findIndex(
            (c) => c.id === calendar.id,
          );
          if (index !== -1) {
            this.userCalendars[index] = calendar;
          }
          return calendar;
        }
        throw new Error("No calendar returned from mutation");
      } catch (err: any) {
        this.error = err.message || "Failed to update calendar";
        throw err;
      } finally {
        this.loading = false;
      }
    },

    /**
     * Delete a calendar
     */
    async deleteCalendar(calendarId: string): Promise<boolean> {
      const authStore = useAuthStore();
      if (!authStore.isAuthenticated) {
        throw new Error("Authentication required to delete calendar");
      }

      this.loading = true;
      this.error = null;
      try {
        const response = await fetch("/graphql", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${authStore.token}`,
          },
          body: JSON.stringify({
            query: `
              mutation DeleteCalendar($id: ID!) {
                deleteCalendar(id: $id)
              }
            `,
            variables: { id: calendarId },
          }),
        });

        if (!response.ok) {
          throw new Error("Failed to delete calendar");
        }

        const result = await response.json();

        if (result.errors) {
          throw new Error(
            result.errors[0]?.message || "Failed to delete calendar",
          );
        }

        // Remove from userCalendars list
        this.userCalendars = this.userCalendars.filter(
          (c) => c.id !== calendarId,
        );
        if (this.currentCalendar?.id === calendarId) {
          this.currentCalendar = null;
        }

        return result.data?.deleteCalendar || false;
      } catch (err: any) {
        this.error = err.message || "Failed to delete calendar";
        throw err;
      } finally {
        this.loading = false;
      }
    },

    /**
     * Poll calendar status until it's READY or FAILED
     * @param calendarId - Calendar ID to poll
     * @param maxAttempts - Maximum number of polling attempts (default: 30)
     * @param intervalMs - Polling interval in milliseconds (default: 2000)
     * @returns The updated calendar
     */
    async pollCalendarStatus(
      calendarId: string,
      maxAttempts: number = 30,
      intervalMs: number = 2000,
    ): Promise<UserCalendar> {
      let attempts = 0;

      return new Promise((resolve, reject) => {
        const poll = async () => {
          attempts++;

          try {
            const calendar = await this.fetchCalendar(calendarId);

            if (!calendar) {
              reject(new Error("Calendar not found"));
              return;
            }

            if (calendar.status === "READY") {
              resolve(calendar);
              return;
            }

            if (calendar.status === "FAILED") {
              reject(new Error("Calendar generation failed"));
              return;
            }

            if (attempts >= maxAttempts) {
              reject(new Error("Calendar generation timed out"));
              return;
            }

            // Continue polling
            setTimeout(poll, intervalMs);
          } catch (err) {
            reject(err);
          }
        };

        poll();
      });
    },

    /**
     * Clear any calendar errors
     */
    clearError() {
      this.error = null;
    },

    /**
     * Clear current calendar
     */
    clearCurrentCalendar() {
      this.currentCalendar = null;
    },
  },

  getters: {
    /**
     * Get featured templates
     */
    featuredTemplates: (state) => state.templates.filter((t) => t.isFeatured),

    /**
     * Get templates sorted by display order
     */
    sortedTemplates: (state) =>
      [...state.templates].sort((a, b) => a.displayOrder - b.displayOrder),

    /**
     * Get calendar by ID
     */
    getCalendarById: (state) => (id: string) => {
      return state.userCalendars.find((c) => c.id === id) || null;
    },

    /**
     * Check if calendar generation is in progress
     */
    isGenerating: (state) => state.currentCalendar?.status === "GENERATING",
  },
});
