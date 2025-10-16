import { Page, expect } from '@playwright/test';
import { BasePage } from './BasePage';
import { fillStripePayment } from '../support/test-utils';

/**
 * Page Object for Checkout page
 */
export class CheckoutPage extends BasePage {
  // Selectors
  private readonly streetInputSelector = 'input[name="shippingAddress.street"]';
  private readonly cityInputSelector = 'input[name="shippingAddress.city"]';
  private readonly stateInputSelector = 'input[name="shippingAddress.state"]';
  private readonly zipCodeInputSelector = 'input[name="shippingAddress.zipCode"]';
  private readonly countrySelectSelector = 'select[name="shippingAddress.country"]';
  private readonly placeOrderButtonSelector = 'button:has-text("Place Order")';
  private readonly orderSummarySelector = '[data-testid="order-summary"]';
  private readonly totalPriceSelector = '[data-testid="total-price"]';
  private readonly stripeElementSelector = '#payment-element';

  constructor(page: Page) {
    super(page);
  }

  /**
   * Navigate to checkout for a specific calendar
   */
  async navigate(calendarId: string): Promise<void> {
    await this.goto(`/checkout/${calendarId}`);
    await this.waitForElement(this.streetInputSelector);
  }

  /**
   * Fill shipping address
   */
  async fillShippingAddress(address: {
    street: string;
    city: string;
    state: string;
    zipCode: string;
    country?: string;
  }): Promise<void> {
    await this.fill(this.streetInputSelector, address.street);
    await this.fill(this.cityInputSelector, address.city);
    await this.fill(this.stateInputSelector, address.state);
    await this.fill(this.zipCodeInputSelector, address.zipCode);

    if (address.country) {
      await this.page.selectOption(this.countrySelectSelector, address.country);
    }

    console.log('Filled shipping address:', address);
  }

  /**
   * Fill Stripe payment form
   */
  async fillPayment(cardNumber: string, expiry: string, cvc: string): Promise<void> {
    // Wait for Stripe element to load
    await this.waitForElement(this.stripeElementSelector, 10000);

    // Fill payment details using iframe helper
    await fillStripePayment(this.page, cardNumber, expiry, cvc);

    console.log('Filled payment details');
  }

  /**
   * Click place order button
   */
  async clickPlaceOrder(): Promise<void> {
    await this.click(this.placeOrderButtonSelector);
    console.log('Clicked place order button');

    // Wait for payment processing
    await this.waitForGraphQL('CreateOrder');
  }

  /**
   * Verify order summary is displayed
   */
  async verifyOrderSummary(): Promise<void> {
    await expect(this.page.locator(this.orderSummarySelector)).toBeVisible();
    console.log('Order summary verified');
  }

  /**
   * Get total price
   */
  async getTotalPrice(): Promise<string> {
    return await this.getText(this.totalPriceSelector);
  }

  /**
   * Complete checkout workflow
   */
  async completeCheckout(
    address: {
      street: string;
      city: string;
      state: string;
      zipCode: string;
      country?: string;
    },
    payment: {
      cardNumber: string;
      expiry: string;
      cvc: string;
    }
  ): Promise<void> {
    await this.fillShippingAddress(address);
    await this.fillPayment(payment.cardNumber, payment.expiry, payment.cvc);
    await this.clickPlaceOrder();
  }

  /**
   * Verify checkout page is loaded
   */
  async verifyCheckoutLoaded(): Promise<void> {
    await expect(this.page.locator(this.streetInputSelector)).toBeVisible();
    await expect(this.page.locator(this.stripeElementSelector)).toBeVisible();
    await expect(this.page.locator(this.placeOrderButtonSelector)).toBeVisible();
    console.log('Checkout page loaded successfully');
  }
}
