/**
 * Guest session management utilities.
 * Handles session ID generation, storage, and retrieval for anonymous users.
 */

const SESSION_ID_KEY = "guest_session_id";

/**
 * Get or create a session ID for guest users.
 * If no session ID exists in localStorage, generates a new one and stores it.
 *
 * @returns The current session ID
 */
export function getOrCreateSessionId(): string {
  let sessionId = localStorage.getItem(SESSION_ID_KEY);

  if (!sessionId) {
    sessionId = crypto.randomUUID();
    localStorage.setItem(SESSION_ID_KEY, sessionId);
    console.log("[Session] Created new session ID:", sessionId);
  }

  return sessionId;
}

/**
 * Get the current session ID if it exists, without creating a new one.
 *
 * @returns The current session ID or null if none exists
 */
export function getSessionId(): string | null {
  return localStorage.getItem(SESSION_ID_KEY);
}

/**
 * Clear the session ID from localStorage.
 * Called after successful conversion of guest session to user account.
 */
export function clearSessionId(): void {
  const sessionId = localStorage.getItem(SESSION_ID_KEY);
  if (sessionId) {
    console.log("[Session] Clearing session ID:", sessionId);
    localStorage.removeItem(SESSION_ID_KEY);
  }
}

/**
 * Check if a session ID exists in localStorage.
 *
 * @returns true if a session ID exists, false otherwise
 */
export function hasSessionId(): boolean {
  return !!localStorage.getItem(SESSION_ID_KEY);
}

/**
 * Set a specific session ID (mainly for testing purposes).
 *
 * @param sessionId The session ID to set
 */
export function setSessionId(sessionId: string): void {
  localStorage.setItem(SESSION_ID_KEY, sessionId);
  console.log("[Session] Set session ID:", sessionId);
}
