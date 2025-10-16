# Village Calendar - E2E Testing Suite

This directory contains end-to-end (E2E) tests for the Village Calendar service, implemented using [Playwright](https://playwright.dev/).

## 📋 Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Running Tests](#running-tests)
- [Test Structure](#test-structure)
- [Writing Tests](#writing-tests)
- [CI/CD Integration](#cicd-integration)
- [Troubleshooting](#troubleshooting)

## 🎯 Overview

The E2E test suite validates complete user workflows across frontend and backend:

### Customer Workflows
- **Browse Templates**: View available calendar templates without authentication
- **Customize Calendar**: Select template, customize year/text, wait for PDF generation
- **Place Order**: Complete checkout with Stripe payment integration
- **Order Confirmation**: Verify order number and confirmation email

### Admin Workflows
- **Order Management**: View orders, update status, add notes
- **Template Management**: Create, edit, delete, and manage calendar templates
- **Access Control**: Verify admin-only routes are protected

## 🔧 Prerequisites

- **Docker & Docker Compose**: For running backend services
- **Node.js**: v18+ (for Playwright)
- **npm**: v8+ (comes with Node.js)

## 📦 Installation

### 1. Install Dependencies

```bash
# Navigate to project root
cd village-calendar

# Install npm dependencies
cd src/main/webui
npm install
```

### 2. Install Playwright Browsers

```bash
# Install Playwright browsers (Chromium, Firefox, WebKit)
npx playwright install chromium
```

### 3. Setup Test Environment

```bash
# Navigate to e2e-tests directory
cd ../../../e2e-tests

# Make setup script executable
chmod +x setup.sh

# Run setup script (starts Docker Compose services)
./setup.sh
```

## 🚀 Running Tests

### Run All Tests

```bash
cd src/main/webui
npm run test:e2e
```

### Run Tests in UI Mode (Interactive)

```bash
npm run test:e2e:ui
```

### Run Specific Test File

```bash
npx playwright test tests/customer-order-flow.spec.ts
```

### Run Tests in Debug Mode

```bash
npm run test:e2e:debug
```

### Run Tests in CI Mode (Parallel)

```bash
npm run test:e2e:ci
```

### View Test Report

```bash
npx playwright show-report
```

## 📁 Test Structure

```
e2e-tests/
├── tests/                              # Test specifications
│   ├── customer-order-flow.spec.ts    # Customer journey tests
│   ├── admin-order-management.spec.ts # Admin order workflows
│   └── admin-template-management.spec.ts # Template CRUD tests
├── pages/                              # Page Object Models
│   ├── BasePage.ts                     # Base page class
│   ├── CalendarBrowserPage.ts         # Template browser
│   ├── CalendarEditorPage.ts          # Calendar editor
│   ├── CheckoutPage.ts                # Checkout and payment
│   ├── OrderConfirmationPage.ts       # Order confirmation
│   ├── AdminOrderDashboardPage.ts     # Admin order dashboard
│   └── AdminTemplateManagerPage.ts    # Admin template manager
├── fixtures/                           # Test fixtures and helpers
│   ├── test-data.json                  # Test data (users, cards, etc.)
│   └── auth-helpers.ts                 # Authentication mocking
├── support/                            # Utility functions
│   ├── test-utils.ts                   # Common test utilities
│   └── api-helpers.ts                  # API helpers (Mailpit, GraphQL)
├── playwright.config.ts                # Playwright configuration
├── setup.sh                            # Environment setup script
└── README.md                           # This file
```

## ✍️ Writing Tests

### Page Object Model (POM)

All tests use the Page Object Model pattern for maintainability:

```typescript
import { test } from '@playwright/test';
import { CalendarBrowserPage } from '../pages/CalendarBrowserPage';
import { authenticateUser, TEST_USERS } from '../fixtures/auth-helpers';

test('example test', async ({ page }) => {
  // Authenticate user
  await authenticateUser(page, TEST_USERS.customer);

  // Use page object
  const browserPage = new CalendarBrowserPage(page);
  await browserPage.navigate();
  await browserPage.selectTemplate('2026 Lunar Calendar');
});
```

### Authentication

Tests use mock authentication (OAuth2 disabled in local dev):

```typescript
import { authenticateUser, TEST_USERS } from '../fixtures/auth-helpers';

// Authenticate as customer
await authenticateUser(page, TEST_USERS.customer);

// Authenticate as admin
await authenticateUser(page, TEST_USERS.admin);
```

### Stripe Payment Testing

Use Stripe test cards for payment tests:

```typescript
import testData from '../fixtures/test-data.json';

const testCard = testData.testCards.success;
await checkoutPage.fillPayment(
  testCard.number,    // 4242 4242 4242 4242
  testCard.expiry,    // 1230
  testCard.cvc        // 123
);
```

### Email Verification

Verify emails using Mailpit API:

```typescript
import { MailpitHelper } from '../support/api-helpers';

const mailpit = new MailpitHelper();
const email = await mailpit.findMessageByRecipient('customer@test.example.com');
expect(email.Subject).toContain('Order Confirmation');
```

## 🔄 CI/CD Integration

### GitHub Actions

Tests run automatically on pull requests and commits to `main`. See `.github/workflows/e2e-tests.yml`.

### CI Environment Variables

- `BASE_URL`: Base URL for tests (default: `http://localhost:8030`)
- `CI`: Set to `true` for CI mode (enables retries, parallel execution)

### Running Tests in CI

```yaml
- name: Run E2E Tests
  run: |
    ./e2e-tests/setup.sh
    cd src/main/webui
    npm run test:e2e:ci
```

## 🐛 Troubleshooting

### Tests Fail with "Service Not Ready"

**Solution**: Ensure Docker Compose services are running:

```bash
docker-compose ps
docker-compose logs calendar-app
```

### PDF Generation Timeout

**Solution**: Increase timeout in test or check backend logs:

```typescript
await editorPage.waitForPDF(60000); // Increase to 60 seconds
```

### Stripe Payment Fails

**Solution**: Verify Stripe iframe is loaded:

```bash
# Check browser console for errors
npm run test:e2e:debug
```

### Email Not Found in Mailpit

**Solution**:
1. Verify Mailpit is running: http://localhost:8130
2. Check backend mailer configuration
3. Increase email polling timeout

### Authentication Issues

**Solution**: Clear localStorage and verify auth token generation:

```typescript
await page.evaluate(() => localStorage.clear());
await authenticateUser(page, TEST_USERS.customer);
```

### Test Database Pollution

**Solution**: Use unique test data per test:

```typescript
import { generateTestEmail } from '../support/test-utils';

const testEmail = generateTestEmail('customer');
```

## 📊 Test Coverage

### Current Coverage

- ✅ Customer order flow (browse → customize → checkout → confirmation)
- ✅ Stripe payment integration (success and decline scenarios)
- ✅ PDF generation and download
- ✅ Email delivery verification
- ✅ Admin order management (view, update status, add notes)
- ✅ Admin template management (create, edit, delete, search)
- ✅ Access control (admin-only routes)

### Future Coverage (Recommended)

- ⏳ Cross-browser testing (Firefox, Safari)
- ⏳ Mobile responsive testing
- ⏳ Performance testing (Lighthouse CI)
- ⏳ Accessibility testing (axe-core)

## 🛠️ Configuration

### Playwright Configuration

Edit `playwright.config.ts` to customize:

- **Browsers**: Chromium, Firefox, WebKit
- **Timeouts**: Action, navigation, expect timeouts
- **Retries**: Number of retries on failure
- **Workers**: Parallel execution workers
- **Base URL**: Target application URL

### Test Data

Edit `fixtures/test-data.json` to customize:

- Test users (customer, admin)
- Stripe test cards
- Template data
- Customization defaults

## 📖 Additional Resources

- [Playwright Documentation](https://playwright.dev/docs/intro)
- [Playwright Best Practices](https://playwright.dev/docs/best-practices)
- [Stripe Test Cards](https://stripe.com/docs/testing)
- [Mailpit API Documentation](https://mailpit.axllent.org/docs/api-v1/)

## 🤝 Contributing

When adding new tests:

1. Follow Page Object Model pattern
2. Add test data to `fixtures/test-data.json`
3. Use unique identifiers for test isolation
4. Add screenshots on failure
5. Update this README with new test coverage

## 📝 License

See project root LICENSE file.
