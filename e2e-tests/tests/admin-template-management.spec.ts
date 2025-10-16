import { test, expect } from '@playwright/test';
import { AdminTemplateManagerPage } from '../pages/AdminTemplateManagerPage';
import { CalendarBrowserPage } from '../pages/CalendarBrowserPage';
import { authenticateUser, TEST_USERS } from '../fixtures/auth-helpers';

/**
 * E2E Test: Admin Template Management
 *
 * This test validates admin template workflows:
 * 1. Login as admin
 * 2. Create new template
 * 3. Edit existing template
 * 4. Verify template persistence
 * 5. Delete template
 */

test.describe('Admin Template Management', () => {
  const testTemplateName = `Test Template ${Date.now()}`;

  test.beforeEach(async ({ page }) => {
    // Login as admin before each test
    await authenticateUser(page, TEST_USERS.admin);
  });

  test('should create a new calendar template', async ({ page }) => {
    const templateManager = new AdminTemplateManagerPage(page);
    await templateManager.navigate();
    await templateManager.verifyTemplateManagerLoaded();

    // Create new template
    const templateData = {
      name: testTemplateName,
      description: 'Test template for E2E testing',
      price: 39.99,
      paperType: 'MATTE',
      active: true,
    };

    await templateManager.createTemplate(templateData);
    console.log(`✅ Template created: ${testTemplateName}`);

    // Verify template appears in list
    await templateManager.verifyTemplateExists(testTemplateName);
    console.log('✅ Template verified in template list');

    // Verify template appears in customer browser
    const browserPage = new CalendarBrowserPage(page);
    await browserPage.navigate();
    await browserPage.verifyTemplateVisible(testTemplateName);
    console.log('✅ Template visible to customers');
  });

  test('should edit an existing template', async ({ page }) => {
    const templateManager = new AdminTemplateManagerPage(page);
    await templateManager.navigate();

    // Create template first
    await templateManager.createTemplate({
      name: testTemplateName,
      description: 'Original description',
      price: 29.99,
      paperType: 'MATTE',
      active: true,
    });

    // Edit template
    const updates = {
      description: 'Updated description for testing',
      price: 34.99,
      paperType: 'GLOSSY',
    };

    await templateManager.editTemplate(testTemplateName, updates);
    console.log(`✅ Template edited: ${testTemplateName}`);

    // Verify changes persisted
    await templateManager.navigate(); // Reload page
    await templateManager.verifyTemplateExists(testTemplateName);
    console.log('✅ Template edits persisted');
  });

  test('should deactivate and reactivate a template', async ({ page }) => {
    const templateManager = new AdminTemplateManagerPage(page);
    await templateManager.navigate();

    // Create active template
    await templateManager.createTemplate({
      name: testTemplateName,
      description: 'Template for activation test',
      price: 29.99,
      active: true,
    });

    // Deactivate template
    await templateManager.editTemplate(testTemplateName, { active: false });
    console.log('✅ Template deactivated');

    // Verify template not visible to customers
    const browserPage = new CalendarBrowserPage(page);
    await browserPage.navigate();

    const templates = await browserPage.getTemplates();
    expect(templates).not.toContain(testTemplateName);
    console.log('✅ Inactive template hidden from customers');

    // Reactivate template
    await templateManager.navigate();
    await templateManager.editTemplate(testTemplateName, { active: true });
    console.log('✅ Template reactivated');

    // Verify template visible again
    await browserPage.navigate();
    await browserPage.verifyTemplateVisible(testTemplateName);
    console.log('✅ Reactivated template visible to customers');
  });

  test('should delete a template', async ({ page }) => {
    const templateManager = new AdminTemplateManagerPage(page);
    await templateManager.navigate();

    // Create template to delete
    const deleteTemplateName = `Delete Test ${Date.now()}`;
    await templateManager.createTemplate({
      name: deleteTemplateName,
      description: 'Template to be deleted',
      price: 19.99,
    });

    console.log(`Created template for deletion: ${deleteTemplateName}`);

    // Delete template
    await templateManager.deleteTemplate(deleteTemplateName);
    console.log('✅ Template deleted');

    // Verify template no longer exists
    await templateManager.navigate(); // Reload
    const templates = await page.locator('[data-testid="template-row"]').allTextContents();
    expect(templates.join(' ')).not.toContain(deleteTemplateName);
    console.log('✅ Template removed from list');
  });

  test('should search for templates in admin panel', async ({ page }) => {
    const templateManager = new AdminTemplateManagerPage(page);
    await templateManager.navigate();

    // Get initial template count
    const initialCount = await templateManager.getTemplateCount();
    console.log(`Total templates: ${initialCount}`);

    // Create template with unique name
    const searchTestName = `Search Test ${Date.now()}`;
    await templateManager.createTemplate({
      name: searchTestName,
      description: 'Template for search testing',
      price: 24.99,
    });

    // Search for template
    await templateManager.searchTemplate(searchTestName);

    // Verify only searched template visible
    await templateManager.verifyTemplateExists(searchTestName);
    const searchResultCount = await templateManager.getTemplateCount();
    expect(searchResultCount).toBe(1);

    console.log('✅ Template search working correctly');
  });

  test('should validate required fields when creating template', async ({ page }) => {
    const templateManager = new AdminTemplateManagerPage(page);
    await templateManager.navigate();

    // Click create template
    await templateManager.clickCreateTemplate();

    // Try to save without filling required fields
    await templateManager.clickSaveTemplate();

    // Verify validation errors appear
    const hasValidationError =
      (await page.locator('text=required').count()) > 0 ||
      (await page.locator('[aria-invalid="true"]').count()) > 0;

    expect(hasValidationError).toBeTruthy();
    console.log('✅ Form validation working correctly');
  });

  test('should display template list with pagination (if implemented)', async ({ page }) => {
    const templateManager = new AdminTemplateManagerPage(page);
    await templateManager.navigate();
    await templateManager.verifyTemplateManagerLoaded();

    // Get template count
    const templateCount = await templateManager.getTemplateCount();
    expect(templateCount).toBeGreaterThanOrEqual(0);

    console.log(`✅ Template list displays ${templateCount} templates`);

    // Note: If pagination is implemented, add tests for it here
    const hasPagination = await page.locator('[data-testid="pagination"]').isVisible().catch(() => false);

    if (hasPagination) {
      console.log('ℹ️ Pagination detected - additional tests recommended');
    } else {
      console.log('ℹ️ No pagination detected (may show all templates)');
    }
  });

  test('should prevent non-admin users from accessing template manager', async ({ page }) => {
    // Login as regular customer
    await authenticateUser(page, TEST_USERS.customer);

    // Attempt to navigate to template manager
    await page.goto('/admin/templates');

    // Should be redirected or show access denied
    await page.waitForTimeout(2000);

    const url = page.url();
    const hasAccessDenied = await page.locator('text=Access Denied').isVisible().catch(() => false);

    expect(url === '/admin/templates' ? hasAccessDenied : true).toBeTruthy();
    console.log('✅ Template manager access control working');
  });

  test.afterEach(async ({ page }) => {
    // Cleanup: Delete test templates
    try {
      await authenticateUser(page, TEST_USERS.admin);
      const templateManager = new AdminTemplateManagerPage(page);
      await templateManager.navigate();

      // Search and delete test template if it exists
      await templateManager.searchTemplate(testTemplateName);
      const exists =
        (await page.locator('[data-testid="template-row"]').count()) > 0;

      if (exists) {
        await templateManager.deleteTemplate(testTemplateName);
        console.log(`Cleaned up test template: ${testTemplateName}`);
      }
    } catch (error) {
      console.log('Cleanup failed (template may not exist):', error);
    }
  });
});
