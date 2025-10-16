import { Page, expect } from '@playwright/test';

/**
 * Wait for an element to be visible with custom timeout
 */
export async function waitForElement(
  page: Page,
  selector: string,
  timeout: number = 10000
): Promise<void> {
  await page.waitForSelector(selector, { state: 'visible', timeout });
}

/**
 * Wait for a GraphQL response with specific operation name
 */
export async function waitForGraphQLResponse(
  page: Page,
  operationName: string,
  timeout: number = 15000
): Promise<void> {
  await page.waitForResponse(
    (response) =>
      response.url().includes('/graphql') &&
      response.status() === 200 &&
      response.request().postDataJSON()?.operationName === operationName,
    { timeout }
  );
}

/**
 * Retry an async operation with exponential backoff
 */
export async function retryOperation<T>(
  operation: () => Promise<T>,
  maxRetries: number = 3,
  initialDelay: number = 1000
): Promise<T> {
  let lastError: Error | undefined;

  for (let i = 0; i < maxRetries; i++) {
    try {
      return await operation();
    } catch (error) {
      lastError = error as Error;
      if (i < maxRetries - 1) {
        const delay = initialDelay * Math.pow(2, i);
        console.log(`Retry ${i + 1}/${maxRetries} after ${delay}ms...`);
        await new Promise((resolve) => setTimeout(resolve, delay));
      }
    }
  }

  throw lastError || new Error('Retry operation failed');
}

/**
 * Wait for PDF generation to complete by polling for PDF URL
 */
export async function waitForPDFGeneration(
  page: Page,
  timeout: number = 30000
): Promise<string> {
  const startTime = Date.now();

  while (Date.now() - startTime < timeout) {
    // Check if PDF preview link is available
    const pdfLink = await page.locator('[data-testid="pdf-preview-link"]').getAttribute('href');

    if (pdfLink && pdfLink !== '#' && pdfLink !== '') {
      console.log('PDF generation completed:', pdfLink);
      return pdfLink;
    }

    // Wait before next check
    await page.waitForTimeout(1000);
  }

  throw new Error('PDF generation timed out after ' + timeout + 'ms');
}

/**
 * Fill Stripe payment form in iframe
 */
export async function fillStripePayment(
  page: Page,
  cardNumber: string,
  expiry: string,
  cvc: string
): Promise<void> {
  // Wait for Stripe iframe to load
  await page.waitForTimeout(2000);

  // Locate the Stripe iframe
  const stripeFrame = page.frameLocator('iframe[name^="__privateStripeFrame"]').first();

  // Fill card number
  await stripeFrame.locator('input[name="cardnumber"]').waitFor({ state: 'visible', timeout: 10000 });
  await stripeFrame.locator('input[name="cardnumber"]').fill(cardNumber);

  // Fill expiry date
  await stripeFrame.locator('input[name="exp-date"]').fill(expiry);

  // Fill CVC
  await stripeFrame.locator('input[name="cvc"]').fill(cvc);
}

/**
 * Extract order number from confirmation page
 */
export async function extractOrderNumber(page: Page): Promise<string> {
  const orderNumber = await page.locator('[data-testid="order-number"]').textContent();

  if (!orderNumber) {
    throw new Error('Order number not found on confirmation page');
  }

  // Validate format: VC-XXXXXX
  expect(orderNumber).toMatch(/^VC-\d{6}$/);

  return orderNumber;
}

/**
 * Clear test data from database (requires API endpoint)
 */
export async function clearTestData(email: string): Promise<void> {
  // This would require a test-only API endpoint
  // For now, this is a placeholder
  console.log(`TODO: Clear test data for user: ${email}`);
}

/**
 * Generate unique email for test isolation
 */
export function generateTestEmail(prefix: string = 'test'): string {
  const timestamp = Date.now();
  const random = Math.random().toString(36).substring(7);
  return `${prefix}-${timestamp}-${random}@test.example.com`;
}

/**
 * Wait for element to be hidden
 */
export async function waitForElementToHide(
  page: Page,
  selector: string,
  timeout: number = 10000
): Promise<void> {
  await page.waitForSelector(selector, { state: 'hidden', timeout });
}

/**
 * Validate GraphQL response for errors
 */
export async function validateGraphQLResponse(response: any): Promise<void> {
  const body = await response.json();

  if (body.errors && body.errors.length > 0) {
    throw new Error(`GraphQL error: ${JSON.stringify(body.errors)}`);
  }

  expect(body.errors).toBeUndefined();
}

/**
 * Take a screenshot with timestamp
 */
export async function takeScreenshot(page: Page, name: string): Promise<void> {
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
  await page.screenshot({ path: `screenshots/${name}-${timestamp}.png`, fullPage: true });
}

/**
 * Mock OAuth2 authentication by setting localStorage
 */
export async function mockAuthToken(page: Page, token: string, userInfo: any): Promise<void> {
  await page.evaluate(
    ({ token, userInfo }) => {
      localStorage.setItem('auth_token', token);
      localStorage.setItem('user_info', JSON.stringify(userInfo));
    },
    { token, userInfo }
  );
}

/**
 * Wait for navigation with timeout
 */
export async function waitForNavigation(page: Page, url: string, timeout: number = 15000): Promise<void> {
  await page.waitForURL(url, { timeout });
}
