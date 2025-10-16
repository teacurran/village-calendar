/**
 * Calendar Service
 * Provides utility functions for calendar operations beyond store actions
 */

export interface CalendarGenerationOptions {
  templateId: string
  year: number
  configuration?: any
}

/**
 * Generate calendar preview SVG from configuration
 * This calls the backend API to render a calendar preview
 */
export async function generateCalendarPreview(
  configuration: any
): Promise<string> {
  const response = await fetch('/api/calendar/generate', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(configuration),
  })

  if (!response.ok) {
    throw new Error('Failed to generate calendar preview')
  }

  return await response.text()
}

/**
 * Download calendar PDF
 * Opens the PDF URL in a new tab for download
 */
export function downloadCalendarPDF(pdfUrl: string, calendarName: string) {
  const link = document.createElement('a')
  link.href = pdfUrl
  link.download = `${calendarName}.pdf`
  link.target = '_blank'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

/**
 * Scale SVG for thumbnail display
 * Adjusts SVG dimensions while maintaining aspect ratio
 */
export function scaleSvgForThumbnail(svg: string, maxWidth: number = 300): string {
  const viewBoxMatch = svg.match(/viewBox="([^"]+)"/)?.[1]
  if (viewBoxMatch) {
    const [x, y, width, height] = viewBoxMatch.split(' ').map(Number)
    const aspectRatio = height / width
    const thumbnailWidth = maxWidth
    const thumbnailHeight = thumbnailWidth * aspectRatio

    return svg
      .replace(/width="[^"]+"/, `width="${thumbnailWidth}"`)
      .replace(/height="[^"]+"/, `height="${thumbnailHeight}"`)
      .replace(/style="[^"]*"/, 'style="max-width: 100%; height: auto;"')
  }
  return svg
}

/**
 * Validate calendar year
 */
export function validateCalendarYear(year: number): boolean {
  const currentYear = new Date().getFullYear()
  return year >= currentYear && year <= currentYear + 10
}

/**
 * Get available years for calendar creation
 */
export function getAvailableYears(): number[] {
  const currentYear = new Date().getFullYear()
  const years = []
  for (let i = 0; i < 5; i++) {
    years.push(currentYear + i)
  }
  return years
}

/**
 * Format calendar status for display
 */
export function formatCalendarStatus(status: string): string {
  const statusMap: Record<string, string> = {
    DRAFT: 'Draft',
    GENERATING: 'Generating...',
    READY: 'Ready',
    FAILED: 'Failed',
  }
  return statusMap[status] || status
}

/**
 * Get status severity for PrimeVue components
 */
export function getStatusSeverity(status: string): 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast' {
  const severityMap: Record<string, 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast'> = {
    DRAFT: 'secondary',
    GENERATING: 'info',
    READY: 'success',
    FAILED: 'danger',
  }
  return severityMap[status] || 'secondary'
}

/**
 * Calculate estimated delivery date based on order date
 * @param orderDate - Order date (defaults to now)
 * @param processingDays - Number of processing days (default: 3)
 * @param shippingDays - Number of shipping days (default: 5-7, uses 6 average)
 */
export function calculateEstimatedDelivery(
  orderDate: Date = new Date(),
  processingDays: number = 3,
  shippingDays: number = 6
): Date {
  const totalDays = processingDays + shippingDays
  const deliveryDate = new Date(orderDate)
  deliveryDate.setDate(deliveryDate.getDate() + totalDays)
  return deliveryDate
}

/**
 * Format date for display (Eastern Time)
 */
export function formatDate(date: Date | string): string {
  const dateObj = typeof date === 'string' ? new Date(date) : date
  return new Intl.DateTimeFormat('en-US', {
    timeZone: 'America/New_York',
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  }).format(dateObj)
}

/**
 * Format date and time for display (Eastern Time)
 */
export function formatDateTime(date: Date | string): string {
  const dateObj = typeof date === 'string' ? new Date(date) : date
  return new Intl.DateTimeFormat('en-US', {
    timeZone: 'America/New_York',
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(dateObj)
}
