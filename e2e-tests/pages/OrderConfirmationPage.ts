import { Page, expect } from '@playwright/test';
import { BasePage } from './BasePage';

/**
 * Page Object for Order Confirmation page
 */
export class OrderConfirmationPage extends BasePage {
  // Selectors
  private readonly orderNumberSelector = '[data-testid="order-number"]';
  private readonly confirmationMessageSelector = '[data-testid="confirmation-message"]';
  private readonly orderDetailsSelector = '[data-testid="order-details"]';
  private readonly downloadPDFButtonSelector = 'button:has-text("Download PDF")';
  private readonly orderStatusSelector = '[data-testid="order-status"]';
  private readonly trackingInfoSelector = '[data-testid="tracking-info"]';

  constructor(page: Page) {
    super(page);
  }

  /**
   * Navigate to order confirmation page
   */
  async navigate(orderId: string): Promise<void> {
    await this.goto(`/order/${orderId}/confirmation`);
    await this.waitForElement(this.orderNumberSelector);
  }

  /**
   * Get order number
   */
  async getOrderNumber(): Promise<string> {
    const orderNumber = await this.getText(this.orderNumberSelector);

    // Validate format: VC-XXXXXX
    expect(orderNumber).toMatch(/^VC-\d{6}$/);

    console.log(`Order number: ${orderNumber}`);
    return orderNumber;
  }

  /**
   * Verify confirmation message is displayed
   */
  async verifyConfirmationMessage(): Promise<void> {
    await expect(this.page.locator(this.confirmationMessageSelector)).toBeVisible();
    console.log('Confirmation message verified');
  }

  /**
   * Get order status
   */
  async getOrderStatus(): Promise<string> {
    return await this.getText(this.orderStatusSelector);
  }

  /**
   * Download calendar PDF
   */
  async downloadPDF(): Promise<void> {
    const downloadPromise = this.page.waitForEvent('download');
    await this.click(this.downloadPDFButtonSelector);
    const download = await downloadPromise;
    console.log(`Downloaded order PDF: ${download.suggestedFilename()}`);
  }

  /**
   * Verify order details are displayed
   */
  async verifyOrderDetails(): Promise<void> {
    await expect(this.page.locator(this.orderDetailsSelector)).toBeVisible();
    console.log('Order details verified');
  }

  /**
   * Verify confirmation page is loaded
   */
  async verifyConfirmationPageLoaded(): Promise<void> {
    await expect(this.page.locator(this.orderNumberSelector)).toBeVisible();
    await expect(this.page.locator(this.confirmationMessageSelector)).toBeVisible();
    console.log('Order confirmation page loaded successfully');
  }

  /**
   * Extract order ID from URL
   */
  getOrderIdFromUrl(): string {
    const url = this.getCurrentUrl();
    const match = url.match(/\/order\/([^\/]+)\/confirmation/);

    if (!match) {
      throw new Error('Order ID not found in URL');
    }

    return match[1];
  }
}
