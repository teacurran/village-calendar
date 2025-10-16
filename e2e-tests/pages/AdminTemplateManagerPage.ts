import { Page, expect } from '@playwright/test';
import { BasePage } from './BasePage';

/**
 * Page Object for Admin Template Manager
 */
export class AdminTemplateManagerPage extends BasePage {
  // Selectors
  private readonly templatesTableSelector = '[data-testid="templates-table"]';
  private readonly templateRowSelector = '[data-testid="template-row"]';
  private readonly createTemplateButtonSelector = 'button:has-text("Create Template")';
  private readonly templateNameInputSelector = 'input[name="templateName"]';
  private readonly templateDescriptionInputSelector = 'textarea[name="templateDescription"]';
  private readonly templatePriceInputSelector = 'input[name="templatePrice"]';
  private readonly paperTypeSelectSelector = 'select[name="paperType"]';
  private readonly activeCheckboxSelector = 'input[name="active"]';
  private readonly saveTemplateButtonSelector = 'button:has-text("Save Template")';
  private readonly editButtonSelector = 'button:has-text("Edit")';
  private readonly deleteButtonSelector = 'button:has-text("Delete")';
  private readonly confirmDeleteButtonSelector = 'button:has-text("Confirm Delete")';
  private readonly searchInputSelector = 'input[placeholder*="Search templates"]';

  constructor(page: Page) {
    super(page);
  }

  /**
   * Navigate to admin template manager
   */
  async navigate(): Promise<void> {
    await this.goto('/admin/templates');
    await this.waitForElement(this.templatesTableSelector);
  }

  /**
   * Click create template button
   */
  async clickCreateTemplate(): Promise<void> {
    await this.click(this.createTemplateButtonSelector);
    await this.waitForElement(this.templateNameInputSelector);
    console.log('Clicked create template button');
  }

  /**
   * Fill template form
   */
  async fillTemplateForm(data: {
    name: string;
    description: string;
    price: number;
    paperType?: string;
    active?: boolean;
  }): Promise<void> {
    await this.fill(this.templateNameInputSelector, data.name);
    await this.fill(this.templateDescriptionInputSelector, data.description);
    await this.fill(this.templatePriceInputSelector, data.price.toString());

    if (data.paperType) {
      await this.page.selectOption(this.paperTypeSelectSelector, data.paperType);
    }

    if (data.active !== undefined) {
      const checkbox = this.page.locator(this.activeCheckboxSelector);
      const isChecked = await checkbox.isChecked();

      if (isChecked !== data.active) {
        await checkbox.click();
      }
    }

    console.log('Filled template form:', data);
  }

  /**
   * Click save template button
   */
  async clickSaveTemplate(): Promise<void> {
    await this.click(this.saveTemplateButtonSelector);
    await this.waitForGraphQL('CreateTemplate');
    console.log('Template saved');
  }

  /**
   * Create a new template
   */
  async createTemplate(data: {
    name: string;
    description: string;
    price: number;
    paperType?: string;
    active?: boolean;
  }): Promise<void> {
    await this.clickCreateTemplate();
    await this.fillTemplateForm(data);
    await this.clickSaveTemplate();
    console.log(`Created template: ${data.name}`);
  }

  /**
   * Find template row by name
   */
  private async getTemplateRow(templateName: string): Promise<any> {
    const templateRow = this.page.locator(this.templateRowSelector).filter({
      hasText: templateName,
    });

    await expect(templateRow).toBeVisible();
    return templateRow;
  }

  /**
   * Edit an existing template
   */
  async editTemplate(
    templateName: string,
    updates: {
      name?: string;
      description?: string;
      price?: number;
      paperType?: string;
      active?: boolean;
    }
  ): Promise<void> {
    await this.searchTemplate(templateName);
    const templateRow = await this.getTemplateRow(templateName);

    // Click edit button
    await templateRow.locator(this.editButtonSelector).click();
    await this.waitForElement(this.templateNameInputSelector);

    // Update fields
    if (updates.name) {
      await this.fill(this.templateNameInputSelector, updates.name);
    }

    if (updates.description) {
      await this.fill(this.templateDescriptionInputSelector, updates.description);
    }

    if (updates.price) {
      await this.fill(this.templatePriceInputSelector, updates.price.toString());
    }

    if (updates.paperType) {
      await this.page.selectOption(this.paperTypeSelectSelector, updates.paperType);
    }

    if (updates.active !== undefined) {
      const checkbox = this.page.locator(this.activeCheckboxSelector);
      const isChecked = await checkbox.isChecked();

      if (isChecked !== updates.active) {
        await checkbox.click();
      }
    }

    // Save changes
    await this.click(this.saveTemplateButtonSelector);
    await this.waitForGraphQL('UpdateTemplate');

    console.log(`Updated template: ${templateName}`, updates);
  }

  /**
   * Delete a template
   */
  async deleteTemplate(templateName: string): Promise<void> {
    await this.searchTemplate(templateName);
    const templateRow = await this.getTemplateRow(templateName);

    // Click delete button
    await templateRow.locator(this.deleteButtonSelector).click();

    // Confirm deletion
    await this.click(this.confirmDeleteButtonSelector);
    await this.waitForGraphQL('DeleteTemplate');

    console.log(`Deleted template: ${templateName}`);
  }

  /**
   * Search for a template
   */
  async searchTemplate(templateName: string): Promise<void> {
    await this.fill(this.searchInputSelector, templateName);
    await this.wait(500); // Wait for search filter
    console.log(`Searched for template: ${templateName}`);
  }

  /**
   * Verify template exists
   */
  async verifyTemplateExists(templateName: string): Promise<void> {
    await this.searchTemplate(templateName);
    const templateRow = await this.getTemplateRow(templateName);
    await expect(templateRow).toBeVisible();
    console.log(`Verified template exists: ${templateName}`);
  }

  /**
   * Get template count
   */
  async getTemplateCount(): Promise<number> {
    return await this.page.locator(this.templateRowSelector).count();
  }

  /**
   * Verify template manager is loaded
   */
  async verifyTemplateManagerLoaded(): Promise<void> {
    await expect(this.page.locator(this.templatesTableSelector)).toBeVisible();
    await expect(this.page.locator(this.createTemplateButtonSelector)).toBeVisible();
    console.log('Admin template manager loaded successfully');
  }
}
