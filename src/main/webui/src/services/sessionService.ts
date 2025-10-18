/**
 * Session Service
 * Manages anonymous user session IDs for calendar storage
 */

const SESSION_ID_KEY = "village_calendar_session_id";

/**
 * Generate a unique session ID
 */
function generateSessionId(): string {
  return `session_${Date.now()}_${Math.random().toString(36).substring(2, 15)}`;
}

/**
 * Get or create a session ID
 * The session ID is stored in localStorage and persists across page reloads
 */
export function getSessionId(): string {
  let sessionId = localStorage.getItem(SESSION_ID_KEY);

  if (!sessionId) {
    sessionId = generateSessionId();
    localStorage.setItem(SESSION_ID_KEY, sessionId);
  }

  return sessionId;
}

/**
 * Clear the current session ID
 * Useful when converting a guest session to a user account
 */
export function clearSessionId(): void {
  localStorage.removeItem(SESSION_ID_KEY);
}

/**
 * Create fetch options with session ID header
 * Use this to automatically include the session ID in API requests
 */
export function withSessionHeaders(
  options: RequestInit = {},
): RequestInit {
  const headers = new Headers(options.headers);
  headers.set("X-Session-ID", getSessionId());

  return {
    ...options,
    headers,
  };
}

/**
 * Fetch wrapper that automatically includes session ID
 */
export async function sessionFetch(
  url: string,
  options: RequestInit = {},
): Promise<Response> {
  return fetch(url, withSessionHeaders(options));
}
