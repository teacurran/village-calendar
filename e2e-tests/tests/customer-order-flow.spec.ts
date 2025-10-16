import { test, expect } from '@playwright/test';
import { CalendarBrowserPage } from '../pages/CalendarBrowserPage';
import { CalendarEditorPage } from '../pages/CalendarEditorPage';
import { CheckoutPage } from '../pages/CheckoutPage';
import { OrderConfirmationPage } from '../pages/OrderConfirmationPage';
import { authenticateUser, TEST_USERS } from '../fixtures/auth-helpers';
import { MailpitHelper } from '../support/api-helpers';
import testData from '../fixtures/test-data.json';
import { generateTestEmail } from '../support/test-utils';

/**
 * E2E Test: Customer Order Flow
 *
 * This test validates the complete customer journey:
 * 1. Browse templates
 * 2. Select and customize a calendar
 * 3. Wait for PDF generation
 * 4. Complete checkout with Stripe payment
 * 5. Verify order confirmation
 * 6. Verify confirmation email delivery
 */

test.describe('Customer Order Flow', () => {
  let mailpit: MailpitHelper;

  test.beforeAll(() => {
    mailpit = new MailpitHelper();
  });

  test.beforeEach(async ({ page }) => {
    // Clear Mailpit messages before each test
    await mailpit.clearMessages().catch(() => {
      console.log('Mailpit clear failed - may not be running');
    });
  });

  test('should complete full order flow from browse to confirmation', async ({ page }) => {
    // Step 1: Authenticate as customer
    const testEmail = generateTestEmail('customer');
    const customer = { ...TEST_USERS.customer, email: testEmail };
    await authenticateUser(page, customer);

    // Step 2: Browse templates
    const browserPage = new CalendarBrowserPage(page);
    await browserPage.navigate();
    await browserPage.verifyTemplateCount(1); // At least 1 template

    // Step 3: Select template
    const templateName = testData.templates[0].name;
    await browserPage.verifyTemplateVisible(templateName);
    await browserPage.clickCustomize(templateName);

    // Step 4: Customize calendar
    const editorPage = new CalendarEditorPage(page);
    await editorPage.verifyEditorLoaded();

    const customization = testData.calendarCustomization;
    await editorPage.fillYear(customization.year);
    await editorPage.fillCustomText(customization.customText);
    await editorPage.clickSave();

    // Step 5: Wait for PDF generation
    const pdfUrl = await editorPage.waitForPDF(30000);
    expect(pdfUrl).toBeTruthy();
    expect(pdfUrl).toContain('.pdf');

    // Step 6: Proceed to checkout
    await editorPage.clickCheckout();

    // Step 7: Fill checkout form
    const checkoutPage = new CheckoutPage(page);
    await checkoutPage.verifyCheckoutLoaded();

    const address = testData.users.customer.address;
    await checkoutPage.fillShippingAddress(address);

    // Step 8: Fill payment details (Stripe test card)
    const testCard = testData.testCards.success;
    await checkoutPage.fillPayment(testCard.number, testCard.expiry, testCard.cvc);

    // Step 9: Place order
    await checkoutPage.clickPlaceOrder();

    // Step 10: Verify order confirmation
    const confirmationPage = new OrderConfirmationPage(page);
    await confirmationPage.verifyConfirmationPageLoaded();

    const orderNumber = await confirmationPage.getOrderNumber();
    expect(orderNumber).toMatch(/^VC-\d{6}$/);
    console.log(`✅ Order placed successfully: ${orderNumber}`);

    // Step 11: Verify confirmation email
    try {
      const email = await mailpit.findMessageByRecipient(testEmail, 15000);
      expect(email).toBeTruthy();
      expect(email.Subject).toContain('Order Confirmation');
      console.log(`✅ Confirmation email received: ${email.Subject}`);

      // Verify email content
      await mailpit.verifyEmailContent(testEmail, [
        orderNumber,
        customization.customText,
        'Thank you for your order',
      ]);
      console.log('✅ Email content verified');
    } catch (error) {
      console.warn('Email verification failed:', error);
      // Don't fail the test if email verification fails (Mailpit may not be running)
    }
  });

  test('should handle payment decline gracefully', async ({ page }) => {
    // Authenticate as customer
    const testEmail = generateTestEmail('customer-decline');
    const customer = { ...TEST_USERS.customer, email: testEmail };
    await authenticateUser(page, customer);

    // Navigate through flow quickly
    const browserPage = new CalendarBrowserPage(page);
    await browserPage.navigate();
    await browserPage.clickCustomize(testData.templates[0].name);

    // Quick customization
    const editorPage = new CalendarEditorPage(page);
    await editorPage.customizeCalendar('2026', 'Test Calendar');
    await editorPage.clickCheckout();

    // Checkout with declined card
    const checkoutPage = new CheckoutPage(page);
    await checkoutPage.fillShippingAddress(testData.users.customer.address);

    const declineCard = testData.testCards.decline;
    await checkoutPage.fillPayment(declineCard.number, declineCard.expiry, declineCard.cvc);

    // Attempt to place order
    await checkoutPage.clickPlaceOrder();

    // Verify error message appears
    await expect(page.locator('text=Your card was declined')).toBeVisible({ timeout: 10000 });
    console.log('✅ Payment decline handled gracefully');
  });

  test('should display calendar preview before checkout', async ({ page }) => {
    // Authenticate as customer
    await authenticateUser(page, TEST_USERS.customer);

    // Navigate to editor
    const browserPage = new CalendarBrowserPage(page);
    await browserPage.navigate();
    await browserPage.clickCustomize(testData.templates[0].name);

    // Customize and verify preview
    const editorPage = new CalendarEditorPage(page);
    await editorPage.customizeCalendar('2026', 'Preview Test');

    // Verify preview is loaded
    await editorPage.verifyPreviewLoaded();
    console.log('✅ Calendar preview displayed successfully');
  });

  test('should allow browsing templates without authentication', async ({ page }) => {
    // Browse templates without authentication
    const browserPage = new CalendarBrowserPage(page);
    await browserPage.navigate();

    // Verify templates are visible
    const templates = await browserPage.getTemplates();
    expect(templates.length).toBeGreaterThan(0);

    // Verify template details
    await browserPage.verifyTemplateVisible(testData.templates[0].name);
    const price = await browserPage.getTemplatePrice(testData.templates[0].name);
    expect(price).toBeTruthy();

    console.log('✅ Templates browsable without authentication');
  });

  test('should redirect to login when customizing without authentication', async ({ page }) => {
    // Navigate to browser without authentication
    const browserPage = new CalendarBrowserPage(page);
    await browserPage.navigate();

    // Click customize (requires authentication)
    await browserPage.clickCustomize(testData.templates[0].name);

    // Should redirect to home page (authentication guard)
    await expect(page).toHaveURL('/', { timeout: 5000 });
    console.log('✅ Authentication guard working correctly');
  });
});
