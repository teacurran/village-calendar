/**
 * TypeScript type definitions for CalendarTemplate entities
 * Matches GraphQL schema types exactly
 */

export interface CalendarTemplate {
  id: string
  name: string
  description?: string
  configuration: any // JSON type
  thumbnailUrl?: string
  previewSvg?: string
  isActive: boolean
  isFeatured: boolean
  displayOrder: number
  created: string // DateTime
  updated: string // DateTime
}

export interface TemplateInput {
  name: string
  description?: string
  configuration: any // JSON type
  thumbnailUrl?: string
  previewSvg?: string
  isActive?: boolean
  isFeatured?: boolean
  displayOrder?: number
}
