import { Page, expect } from '@playwright/test';
import { BasePage } from './BasePage';

/**
 * Page Object for Admin Order Dashboard
 */
export class AdminOrderDashboardPage extends BasePage {
  // Selectors
  private readonly ordersTableSelector = '[data-testid="orders-table"]';
  private readonly orderRowSelector = '[data-testid="order-row"]';
  private readonly orderNumberCellSelector = '[data-testid="order-number-cell"]';
  private readonly statusSelectSelector = 'select[name="orderStatus"]';
  private readonly notesTextareaSelector = 'textarea[name="orderNotes"]';
  private readonly saveNotesButtonSelector = 'button:has-text("Save Notes")';
  private readonly updateStatusButtonSelector = 'button:has-text("Update Status")';
  private readonly searchInputSelector = 'input[placeholder*="Search orders"]';
  private readonly filterStatusSelectSelector = 'select[name="filterStatus"]';
  private readonly viewDetailsButtonSelector = 'button:has-text("View Details")';

  constructor(page: Page) {
    super(page);
  }

  /**
   * Navigate to admin order dashboard
   */
  async navigate(): Promise<void> {
    await this.goto('/admin/orders');
    await this.waitForElement(this.ordersTableSelector);
  }

  /**
   * Get all visible order numbers
   */
  async getOrderNumbers(): Promise<string[]> {
    const orderNumbers = await this.page
      .locator(this.orderNumberCellSelector)
      .allTextContents();

    return orderNumbers;
  }

  /**
   * Search for an order by order number
   */
  async searchOrder(orderNumber: string): Promise<void> {
    await this.fill(this.searchInputSelector, orderNumber);
    await this.wait(500); // Wait for search filter
    console.log(`Searched for order: ${orderNumber}`);
  }

  /**
   * Find order row by order number
   */
  private async getOrderRow(orderNumber: string): Promise<any> {
    const orderRow = this.page.locator(this.orderRowSelector).filter({
      hasText: orderNumber,
    });

    await expect(orderRow).toBeVisible();
    return orderRow;
  }

  /**
   * Update order status
   */
  async updateOrderStatus(orderNumber: string, newStatus: string): Promise<void> {
    await this.searchOrder(orderNumber);
    const orderRow = await this.getOrderRow(orderNumber);

    // Select new status
    await orderRow.locator(this.statusSelectSelector).selectOption(newStatus);

    // Click update button
    await orderRow.locator(this.updateStatusButtonSelector).click();

    // Wait for GraphQL mutation
    await this.waitForGraphQL('UpdateOrder');

    console.log(`Updated order ${orderNumber} status to: ${newStatus}`);
  }

  /**
   * Add notes to an order
   */
  async addOrderNotes(orderNumber: string, notes: string): Promise<void> {
    await this.searchOrder(orderNumber);
    const orderRow = await this.getOrderRow(orderNumber);

    // Click view details to expand
    await orderRow.locator(this.viewDetailsButtonSelector).click();

    // Fill notes
    await orderRow.locator(this.notesTextareaSelector).fill(notes);

    // Save notes
    await orderRow.locator(this.saveNotesButtonSelector).click();

    // Wait for GraphQL mutation
    await this.waitForGraphQL('UpdateOrder');

    console.log(`Added notes to order ${orderNumber}: ${notes}`);
  }

  /**
   * Get order status
   */
  async getOrderStatus(orderNumber: string): Promise<string> {
    await this.searchOrder(orderNumber);
    const orderRow = await this.getOrderRow(orderNumber);

    const status = await orderRow
      .locator('[data-testid="order-status"]')
      .textContent();

    return status || '';
  }

  /**
   * Filter orders by status
   */
  async filterByStatus(status: string): Promise<void> {
    await this.page.selectOption(this.filterStatusSelectSelector, status);
    await this.wait(500); // Wait for filter
    console.log(`Filtered orders by status: ${status}`);
  }

  /**
   * Verify order exists in dashboard
   */
  async verifyOrderExists(orderNumber: string): Promise<void> {
    await this.searchOrder(orderNumber);
    const orderRow = await this.getOrderRow(orderNumber);
    await expect(orderRow).toBeVisible();
    console.log(`Verified order exists: ${orderNumber}`);
  }

  /**
   * Get order count
   */
  async getOrderCount(): Promise<number> {
    return await this.page.locator(this.orderRowSelector).count();
  }

  /**
   * View order details
   */
  async viewOrderDetails(orderNumber: string): Promise<void> {
    await this.searchOrder(orderNumber);
    const orderRow = await this.getOrderRow(orderNumber);
    await orderRow.locator(this.viewDetailsButtonSelector).click();
    console.log(`Viewing details for order: ${orderNumber}`);
  }

  /**
   * Verify dashboard is loaded
   */
  async verifyDashboardLoaded(): Promise<void> {
    await expect(this.page.locator(this.ordersTableSelector)).toBeVisible();
    console.log('Admin order dashboard loaded successfully');
  }
}
