/**
 * Calendar configuration and event data types
 * Defines the structure of the JSONB configuration field in UserCalendar
 */

/**
 * Individual event in the calendar
 */
export interface CalendarEvent {
  /** ISO 8601 date format "YYYY-MM-DD" */
  date: string;
  /** Event description, max 500 chars */
  text: string;
  /** Unicode emoji (optional) */
  emoji?: string;
  /** Hex color code (optional) */
  color?: string;
}

/**
 * Holiday configuration
 */
export interface HolidayConfig {
  /** Array of holiday set IDs (e.g., ["us-federal", "jewish"]) */
  sets: string[];
}

/**
 * Astronomy features configuration
 */
export interface AstronomyConfig {
  /** Enable moon phase overlay */
  showMoonPhases: boolean;
  /** Enable Hebrew calendar overlay */
  showHebrewCalendar: boolean;
}

/**
 * Layout configuration (future expansion)
 */
export interface LayoutConfig {
  orientation?: "portrait" | "landscape";
  size?: string;
  [key: string]: unknown;
}

/**
 * Color scheme configuration (future expansion)
 */
export interface ColorConfig {
  primary?: string;
  secondary?: string;
  [key: string]: unknown;
}

/**
 * Complete calendar configuration structure stored in JSONB field
 */
export interface CalendarConfiguration {
  /** Array of user-defined events */
  events: CalendarEvent[];
  /** Holiday set selections */
  holidays: HolidayConfig;
  /** Astronomy feature toggles */
  astronomy: AstronomyConfig;
  /** Layout configuration (optional, for future use) */
  layout?: LayoutConfig;
  /** Color scheme configuration (optional, for future use) */
  colors?: ColorConfig;
}

/**
 * Holiday set definition
 */
export interface HolidaySet {
  id: string;
  name: string;
  description: string;
}

/**
 * Available holiday sets
 */
export const HOLIDAY_SETS: readonly HolidaySet[] = [
  {
    id: "us-federal",
    name: "US Federal Holidays",
    description:
      "Major US federal holidays including Independence Day, Thanksgiving, etc.",
  },
  {
    id: "jewish",
    name: "Jewish Holidays",
    description:
      "Major Jewish observances including Rosh Hashanah, Yom Kippur, Passover, etc.",
  },
  {
    id: "christian",
    name: "Christian Holidays",
    description: "Christian holidays including Easter, Christmas, etc.",
  },
  {
    id: "islamic",
    name: "Islamic Holidays",
    description:
      "Islamic holidays including Ramadan, Eid al-Fitr, Eid al-Adha, etc.",
  },
] as const;

/**
 * Default calendar configuration for new calendars
 */
export const DEFAULT_CALENDAR_CONFIG: CalendarConfiguration = {
  events: [],
  holidays: {
    sets: [],
  },
  astronomy: {
    showMoonPhases: false,
    showHebrewCalendar: false,
  },
};

/**
 * Date selection context for event editor
 */
export interface DateSelection {
  year: number;
  month: number;
  day: number;
}

/**
 * Helper function to format date for API (ISO 8601)
 */
export function formatDateForAPI(
  year: number,
  month: number,
  day: number,
): string {
  return `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
}

/**
 * Helper function to parse API date string
 */
export function parseDateFromAPI(dateString: string): DateSelection | null {
  const match = dateString.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!match) return null;

  return {
    year: parseInt(match[1], 10),
    month: parseInt(match[2], 10),
    day: parseInt(match[3], 10),
  };
}

/**
 * Helper function to get days in a month
 */
export function getDaysInMonth(year: number, month: number): number {
  return new Date(year, month, 0).getDate();
}

/**
 * Helper function to get month name
 */
export function getMonthName(month: number): string {
  const date = new Date(2000, month - 1, 1);
  return date.toLocaleDateString("en-US", { month: "long" });
}

/**
 * Helper function to get short month name
 */
export function getShortMonthName(month: number): string {
  const date = new Date(2000, month - 1, 1);
  return date.toLocaleDateString("en-US", { month: "short" });
}

/**
 * Helper function to get day of week for first day of month
 */
export function getFirstDayOfWeek(year: number, month: number): number {
  return new Date(year, month - 1, 1).getDay();
}
