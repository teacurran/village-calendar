import { Page, expect } from '@playwright/test';
import { BasePage } from './BasePage';
import { waitForPDFGeneration } from '../support/test-utils';

/**
 * Page Object for Calendar Editor
 */
export class CalendarEditorPage extends BasePage {
  // Selectors
  private readonly yearInputSelector = 'input[name="year"]';
  private readonly customTextInputSelector = 'textarea[name="customText"]';
  private readonly saveButtonSelector = 'button:has-text("Save Calendar")';
  private readonly pdfPreviewLinkSelector = '[data-testid="pdf-preview-link"]';
  private readonly pdfGeneratingMessageSelector = '[data-testid="pdf-generating"]';
  private readonly checkoutButtonSelector = 'button:has-text("Order Calendar")';
  private readonly previewImageSelector = '[data-testid="calendar-preview"]';
  private readonly themeSelectSelector = 'select[name="theme"]';
  private readonly startMonthSelectSelector = 'select[name="startMonth"]';

  constructor(page: Page) {
    super(page);
  }

  /**
   * Navigate to editor for a specific template
   */
  async navigate(templateId: string): Promise<void> {
    await this.goto(`/editor/${templateId}`);
    await this.waitForElement(this.yearInputSelector);
  }

  /**
   * Fill calendar year
   */
  async fillYear(year: string): Promise<void> {
    await this.fill(this.yearInputSelector, year);
    console.log(`Filled year: ${year}`);
  }

  /**
   * Fill custom text
   */
  async fillCustomText(text: string): Promise<void> {
    await this.fill(this.customTextInputSelector, text);
    console.log(`Filled custom text: ${text}`);
  }

  /**
   * Select theme
   */
  async selectTheme(theme: string): Promise<void> {
    await this.page.selectOption(this.themeSelectSelector, theme);
    console.log(`Selected theme: ${theme}`);
  }

  /**
   * Select start month
   */
  async selectStartMonth(month: string): Promise<void> {
    await this.page.selectOption(this.startMonthSelectSelector, month);
    console.log(`Selected start month: ${month}`);
  }

  /**
   * Click save calendar button
   */
  async clickSave(): Promise<void> {
    await this.click(this.saveButtonSelector);
    await this.waitForGraphQL('SaveCalendar');
    console.log('Calendar saved');
  }

  /**
   * Wait for PDF generation to complete
   */
  async waitForPDF(timeout: number = 30000): Promise<string> {
    console.log('Waiting for PDF generation...');

    // Wait for the generating message to appear
    await this.waitForElement(this.pdfGeneratingMessageSelector, 5000).catch(() => {
      console.log('PDF generating message not shown (may be instant)');
    });

    // Wait for PDF link to be available
    const pdfUrl = await waitForPDFGeneration(this.page, timeout);

    console.log(`PDF generated: ${pdfUrl}`);
    return pdfUrl;
  }

  /**
   * Click checkout button
   */
  async clickCheckout(): Promise<void> {
    await this.click(this.checkoutButtonSelector);
    console.log('Clicked checkout button');
  }

  /**
   * Verify preview image is loaded
   */
  async verifyPreviewLoaded(): Promise<void> {
    const preview = this.page.locator(this.previewImageSelector);
    await expect(preview).toBeVisible();
    console.log('Calendar preview loaded');
  }

  /**
   * Get PDF preview URL
   */
  async getPDFUrl(): Promise<string | null> {
    return await this.page.locator(this.pdfPreviewLinkSelector).getAttribute('href');
  }

  /**
   * Download PDF preview
   */
  async downloadPDF(): Promise<void> {
    const downloadPromise = this.page.waitForEvent('download');
    await this.click(this.pdfPreviewLinkSelector);
    const download = await downloadPromise;
    console.log(`Downloaded PDF: ${download.suggestedFilename()}`);
  }

  /**
   * Verify editor is loaded with template data
   */
  async verifyEditorLoaded(): Promise<void> {
    await expect(this.page.locator(this.yearInputSelector)).toBeVisible();
    await expect(this.page.locator(this.customTextInputSelector)).toBeVisible();
    await expect(this.page.locator(this.saveButtonSelector)).toBeVisible();
    console.log('Calendar editor loaded successfully');
  }

  /**
   * Complete customization workflow
   */
  async customizeCalendar(year: string, customText: string): Promise<string> {
    await this.fillYear(year);
    await this.fillCustomText(customText);
    await this.clickSave();
    return await this.waitForPDF();
  }
}
