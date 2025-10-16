import { Page, expect } from '@playwright/test';
import { BasePage } from './BasePage';

/**
 * Page Object for Calendar Browser (Home page)
 */
export class CalendarBrowserPage extends BasePage {
  // Selectors
  private readonly templateCardSelector = '[data-testid="template-card"]';
  private readonly templateNameSelector = '[data-testid="template-name"]';
  private readonly templatePriceSelector = '[data-testid="template-price"]';
  private readonly customizeButtonSelector = 'button:has-text("Customize")';
  private readonly loginButtonSelector = 'button:has-text("Login")';
  private readonly searchInputSelector = 'input[placeholder*="Search"]';

  constructor(page: Page) {
    super(page);
  }

  /**
   * Navigate to the calendar browser page
   */
  async navigate(): Promise<void> {
    await this.goto('/');
    await this.waitForElement(this.templateCardSelector);
  }

  /**
   * Get all visible templates
   */
  async getTemplates(): Promise<string[]> {
    const templates = await this.page.locator(this.templateNameSelector).allTextContents();
    return templates;
  }

  /**
   * Select a template by name
   */
  async selectTemplate(templateName: string): Promise<void> {
    // Find template card with matching name
    const templateCard = this.page.locator(this.templateCardSelector).filter({
      hasText: templateName,
    });

    await expect(templateCard).toBeVisible();
    await templateCard.click();

    console.log(`Selected template: ${templateName}`);
  }

  /**
   * Click customize button on a template
   */
  async clickCustomize(templateName: string): Promise<void> {
    const templateCard = this.page.locator(this.templateCardSelector).filter({
      hasText: templateName,
    });

    await templateCard.locator(this.customizeButtonSelector).click();

    console.log(`Clicked customize for: ${templateName}`);
  }

  /**
   * Search for templates
   */
  async searchTemplates(query: string): Promise<void> {
    await this.fill(this.searchInputSelector, query);
    await this.wait(500); // Wait for search to filter
  }

  /**
   * Get template price by name
   */
  async getTemplatePrice(templateName: string): Promise<string> {
    const templateCard = this.page.locator(this.templateCardSelector).filter({
      hasText: templateName,
    });

    return await templateCard.locator(this.templatePriceSelector).textContent() || '';
  }

  /**
   * Click login button
   */
  async clickLogin(): Promise<void> {
    await this.click(this.loginButtonSelector);
  }

  /**
   * Verify template count
   */
  async verifyTemplateCount(expectedCount: number): Promise<void> {
    const count = await this.page.locator(this.templateCardSelector).count();
    expect(count).toBeGreaterThanOrEqual(expectedCount);
  }

  /**
   * Verify template is visible
   */
  async verifyTemplateVisible(templateName: string): Promise<void> {
    const templateCard = this.page.locator(this.templateCardSelector).filter({
      hasText: templateName,
    });

    await expect(templateCard).toBeVisible();
  }
}
