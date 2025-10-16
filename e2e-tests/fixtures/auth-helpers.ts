import { Page } from '@playwright/test';

/**
 * Authentication helper for E2E tests
 *
 * Since OAuth2 is disabled in local development, we mock authentication
 * by directly setting auth tokens in localStorage.
 */

export interface TestUser {
  email: string;
  name: string;
  role?: string;
  token?: string;
}

/**
 * Generate a mock JWT token for testing
 * Note: This is a fake token for local development only
 */
function generateMockToken(user: TestUser): string {
  // In real implementation, this would be obtained from a test auth endpoint
  // For now, we create a simple base64-encoded payload
  const payload = {
    sub: user.email,
    name: user.name,
    email: user.email,
    roles: user.role ? [user.role] : ['CUSTOMER'],
    iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + 3600, // 1 hour
  };

  // Note: This is NOT a real JWT signature - for testing only
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = btoa(JSON.stringify(payload));
  const signature = 'test-signature';

  return `${header}.${body}.${signature}`;
}

/**
 * Authenticate a user by setting auth tokens in localStorage
 */
export async function authenticateUser(page: Page, user: TestUser): Promise<void> {
  const token = user.token || generateMockToken(user);

  // Navigate to the app first to ensure localStorage is available
  await page.goto('/');

  // Set authentication data in localStorage
  await page.evaluate(
    ({ token, user }) => {
      localStorage.setItem('auth_token', token);
      localStorage.setItem(
        'user_info',
        JSON.stringify({
          email: user.email,
          name: user.name,
          roles: user.role ? [user.role] : ['CUSTOMER'],
        })
      );
      localStorage.setItem('auth_expires_at', String(Date.now() + 3600000)); // 1 hour
    },
    { token, user }
  );

  console.log(`Authenticated user: ${user.email} (${user.role || 'CUSTOMER'})`);
}

/**
 * Mock OAuth2 callback flow
 * This simulates the OAuth2 redirect callback with authentication data
 */
export async function mockOAuth2Callback(page: Page, user: TestUser): Promise<void> {
  const token = user.token || generateMockToken(user);

  // Navigate to OAuth callback route with mock token
  await page.goto(`/auth/callback?token=${token}&provider=google`);

  // Wait for redirect to complete
  await page.waitForURL('/', { timeout: 5000 });

  console.log(`OAuth2 callback completed for: ${user.email}`);
}

/**
 * Logout user by clearing authentication data
 */
export async function logoutUser(page: Page): Promise<void> {
  await page.evaluate(() => {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('user_info');
    localStorage.removeItem('auth_expires_at');
  });

  console.log('User logged out');
}

/**
 * Check if user is authenticated
 */
export async function isAuthenticated(page: Page): Promise<boolean> {
  return await page.evaluate(() => {
    const token = localStorage.getItem('auth_token');
    const expiresAt = localStorage.getItem('auth_expires_at');

    if (!token || !expiresAt) {
      return false;
    }

    return Date.now() < parseInt(expiresAt);
  });
}

/**
 * Get authentication token from localStorage
 */
export async function getAuthToken(page: Page): Promise<string | null> {
  return await page.evaluate(() => localStorage.getItem('auth_token'));
}

/**
 * Predefined test users
 */
export const TEST_USERS = {
  customer: {
    email: 'customer@test.example.com',
    name: 'Test Customer',
    role: 'CUSTOMER',
  } as TestUser,

  admin: {
    email: 'admin@test.example.com',
    name: 'Test Admin',
    role: 'ADMIN',
  } as TestUser,

  manager: {
    email: 'manager@test.example.com',
    name: 'Test Manager',
    role: 'MANAGER',
  } as TestUser,
};

/**
 * Setup authentication state for Playwright project
 * This can be used with storageState to share authentication across tests
 */
export async function setupAuthState(page: Page, user: TestUser, storageStatePath: string): Promise<void> {
  await authenticateUser(page, user);

  // Save storage state
  await page.context().storageState({ path: storageStatePath });

  console.log(`Saved auth state to: ${storageStatePath}`);
}
