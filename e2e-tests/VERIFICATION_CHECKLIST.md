# E2E Testing Verification Checklist

Use this checklist to verify the E2E testing implementation is complete and functional.

## ✅ Pre-Flight Checks

### Installation Verification

- [ ] **Node.js installed**: `node --version` (v18+)
- [ ] **npm installed**: `npm --version` (v8+)
- [ ] **Docker installed**: `docker --version`
- [ ] **Docker Compose installed**: `docker-compose --version` or `docker compose version`
- [ ] **Project dependencies installed**: `cd src/main/webui && npm install`
- [ ] **Playwright browsers installed**: `npx playwright install chromium`

### File Structure Verification

```bash
# Run this command to verify all files exist
cd village-calendar

# Check e2e-tests directory structure
ls -la e2e-tests/
ls -la e2e-tests/tests/
ls -la e2e-tests/pages/
ls -la e2e-tests/fixtures/
ls -la e2e-tests/support/

# Expected files:
# e2e-tests/
#   ├── playwright.config.ts ✓
#   ├── tsconfig.json ✓
#   ├── setup.sh ✓
#   ├── README.md ✓
#   ├── IMPLEMENTATION.md ✓
#   ├── .gitignore ✓
#   ├── tests/
#   │   ├── customer-order-flow.spec.ts ✓
#   │   ├── admin-order-management.spec.ts ✓
#   │   └── admin-template-management.spec.ts ✓
#   ├── pages/
#   │   ├── BasePage.ts ✓
#   │   ├── CalendarBrowserPage.ts ✓
#   │   ├── CalendarEditorPage.ts ✓
#   │   ├── CheckoutPage.ts ✓
#   │   ├── OrderConfirmationPage.ts ✓
#   │   ├── AdminOrderDashboardPage.ts ✓
#   │   └── AdminTemplateManagerPage.ts ✓
#   ├── fixtures/
#   │   ├── test-data.json ✓
#   │   └── auth-helpers.ts ✓
#   └── support/
#       ├── test-utils.ts ✓
#       └── api-helpers.ts ✓
```

- [ ] All test specification files exist
- [ ] All page object files exist
- [ ] All fixture files exist
- [ ] All support utility files exist
- [ ] Configuration files exist (playwright.config.ts, tsconfig.json)
- [ ] Documentation files exist (README.md, IMPLEMENTATION.md)

### Configuration Verification

- [ ] **package.json updated**: Check for Playwright dependency and test scripts
  ```bash
  cd src/main/webui
  grep '@playwright/test' package.json
  grep 'test:e2e' package.json
  ```

- [ ] **GitHub Actions workflow exists**: `.github/workflows/e2e-tests.yml`
  ```bash
  ls -la .github/workflows/e2e-tests.yml
  ```

- [ ] **Setup script is executable**: `e2e-tests/setup.sh`
  ```bash
  test -x e2e-tests/setup.sh && echo "✓ Executable" || echo "✗ Not executable"
  ```

---

## 🚀 Functional Testing

### Environment Setup

- [ ] **Start Docker Compose services**:
  ```bash
  cd village-calendar
  ./e2e-tests/setup.sh
  ```

- [ ] **Verify services are running**:
  ```bash
  docker-compose ps
  # Expected: calendar-app, calendar-db, mailpit, jaeger all "Up"
  ```

- [ ] **Check service health**:
  ```bash
  # Calendar app health
  curl http://localhost:8030/q/health/ready

  # Mailpit web UI
  curl http://localhost:8130/

  # GraphQL endpoint
  curl -X POST http://localhost:8030/graphql \
    -H "Content-Type: application/json" \
    -d '{"query":"query { __typename }"}'
  ```

### Test Execution

- [ ] **Run all E2E tests**:
  ```bash
  cd src/main/webui
  npm run test:e2e
  ```
  Expected: All tests pass (green checkmarks)

- [ ] **Verify test execution time**:
  ```bash
  time npm run test:e2e:ci
  ```
  Expected: Total time < 5 minutes

- [ ] **Run specific test file**:
  ```bash
  npx playwright test tests/customer-order-flow.spec.ts
  ```
  Expected: Customer tests pass

- [ ] **Run tests in UI mode**:
  ```bash
  npm run test:e2e:ui
  ```
  Expected: Playwright UI opens, tests can be run interactively

### Test Coverage Verification

#### Customer Order Flow Tests
- [ ] Test: Complete order flow (browse → checkout → confirmation)
- [ ] Test: Payment decline handling
- [ ] Test: Calendar preview display
- [ ] Test: Template browsing without authentication
- [ ] Test: Authentication guard enforcement

#### Admin Order Management Tests
- [ ] Test: View orders in admin dashboard
- [ ] Test: Update order status
- [ ] Test: Add order notes
- [ ] Test: Filter orders by status
- [ ] Test: Search for specific orders
- [ ] Test: Access control (prevent non-admin access)

#### Admin Template Management Tests
- [ ] Test: Create new template
- [ ] Test: Edit existing template
- [ ] Test: Delete template
- [ ] Test: Deactivate/reactivate template
- [ ] Test: Search templates
- [ ] Test: Form validation
- [ ] Test: Template visibility in customer browser
- [ ] Test: Access control (prevent non-admin access)

### Failure Scenarios

- [ ] **Intentionally fail a test** (add `expect(false).toBeTruthy()` to a test):
  ```bash
  npm run test:e2e
  ```
  Expected: Test fails, screenshot captured in `test-results/`

- [ ] **Verify screenshot capture**:
  ```bash
  ls -la src/main/webui/test-results/
  ```
  Expected: Screenshot files (*.png) present

- [ ] **View test report**:
  ```bash
  npx playwright show-report
  ```
  Expected: HTML report opens in browser showing test results

---

## 🔄 CI/CD Verification

### GitHub Actions Workflow

- [ ] **Workflow file exists**: `.github/workflows/e2e-tests.yml`

- [ ] **Workflow syntax is valid**:
  ```bash
  # Check YAML syntax (requires yamllint)
  yamllint .github/workflows/e2e-tests.yml
  ```

- [ ] **Workflow triggers are configured**:
  - On pull request to main/develop
  - On push to main
  - Manual workflow dispatch

- [ ] **Workflow steps are complete**:
  - [ ] Checkout code
  - [ ] Setup JDK 17
  - [ ] Setup Node.js 18
  - [ ] Install npm dependencies
  - [ ] Build backend (Maven)
  - [ ] Build frontend (Vite)
  - [ ] Start Docker Compose services
  - [ ] Wait for services to be healthy
  - [ ] Install Playwright browsers
  - [ ] Run E2E tests
  - [ ] Upload test artifacts
  - [ ] Publish test results

### CI Simulation (Local)

- [ ] **Run tests in CI mode**:
  ```bash
  CI=true npm run test:e2e:ci
  ```
  Expected: Tests run with 2 workers, JSON/JUnit reports generated

- [ ] **Verify test reports generated**:
  ```bash
  ls -la playwright-report/
  # Expected: results.json, junit.xml, index.html
  ```

---

## 📊 Acceptance Criteria Validation

### Criterion 1: All E2E tests pass
- [ ] Command `npm run test:e2e` completes without errors
- [ ] All test suites show green checkmarks
- [ ] No test failures or timeouts

### Criterion 2: Tests run in headless browser
- [ ] No browser window opens during test execution
- [ ] Tests can run in CI environment (no display required)
- [ ] Headless mode configured in `playwright.config.ts`

### Criterion 3: Tests validate critical paths
- [ ] Customer order flow: browse → customize → checkout → payment → confirmation ✓
- [ ] Admin order management: view → update → notes → filter ✓
- [ ] Admin template management: create → edit → delete ✓
- [ ] Stripe payment integration (success and failure) ✓
- [ ] Email delivery verification (Mailpit) ✓

### Criterion 4: Screenshots captured on failures
- [ ] Configuration includes `screenshot: 'only-on-failure'`
- [ ] Configuration includes `video: 'retain-on-failure'`
- [ ] Configuration includes `trace: 'on-first-retry'`
- [ ] Intentional test failure produces screenshot in `test-results/`

### Criterion 5: Test execution time under 5 minutes
- [ ] Run `time npm run test:e2e:ci` and verify total time < 300 seconds
- [ ] Parallel execution enabled (2 workers)
- [ ] No unnecessary waits or timeouts in tests
- [ ] Optimized health checks in setup script

---

## 🔍 Code Quality Checks

### TypeScript Compilation
- [ ] **No TypeScript errors**:
  ```bash
  cd e2e-tests
  npx tsc --noEmit
  ```

### ESLint (if configured)
- [ ] **No linting errors**:
  ```bash
  cd src/main/webui
  npm run lint
  ```

### Test Code Review
- [ ] Tests follow Page Object Model pattern
- [ ] No hardcoded waits (`setTimeout`) - use explicit waits
- [ ] Authentication uses mock helpers consistently
- [ ] Test data uses `test-data.json` configuration
- [ ] Tests are independent (no shared state between tests)
- [ ] Tests have descriptive names and comments

---

## 📝 Documentation Verification

- [ ] **README.md complete**: Covers installation, usage, troubleshooting
- [ ] **IMPLEMENTATION.md complete**: Covers deliverables, acceptance criteria, architecture
- [ ] **VERIFICATION_CHECKLIST.md complete**: This checklist
- [ ] **Code comments**: Page objects and utilities have TSDoc comments
- [ ] **Setup script comments**: Shell script has clear explanations

---

## 🎯 Final Validation

### Run Complete Test Suite

```bash
# 1. Start environment
cd village-calendar
./e2e-tests/setup.sh

# 2. Run all tests
cd src/main/webui
npm run test:e2e

# 3. Verify results
npx playwright show-report

# 4. Check execution time
time npm run test:e2e:ci
```

### Expected Results

- ✅ All 19 tests pass
- ✅ Test execution time < 5 minutes
- ✅ HTML report generated
- ✅ No errors or warnings (except email-related warnings if Mailpit unavailable)

### Success Criteria

- [ ] All acceptance criteria met (see above)
- [ ] All tests pass consistently (run 3 times to verify)
- [ ] Test reports are generated correctly
- [ ] CI workflow would pass (verify workflow syntax)
- [ ] Documentation is complete and accurate

---

## 🐛 Known Issues (Expected)

These are known limitations that do NOT indicate implementation failure:

1. **OAuth2 Mock Warning**: Tests use mock authentication (OAuth2 disabled in local dev)
   - Expected: `console.log` warnings about mock auth
   - Impact: None (tests still validate workflows)

2. **Mailpit Connection Errors**: Email verification may fail if Mailpit isn't running
   - Expected: Tests log warning and continue (don't fail)
   - Impact: Email verification skipped

3. **First Run Slowness**: Initial test run may exceed 5 minutes
   - Reason: Docker image downloads, browser installation
   - Impact: Subsequent runs will be faster

4. **PDF Generation Timing**: PDF generation may occasionally timeout
   - Expected: Test retries in CI (configured with 2 retries)
   - Impact: Minimal (retries handle transient issues)

---

## ✅ Sign-Off

### Implementation Complete
- [ ] All files created and verified
- [ ] All tests pass
- [ ] All acceptance criteria met
- [ ] Documentation complete
- [ ] CI/CD workflow configured

### Ready for Review
- [ ] Code reviewed by team member
- [ ] Tests run successfully on another developer's machine
- [ ] CI workflow validated (if possible)

### Ready for Deployment
- [ ] Tests integrated into CI pipeline
- [ ] Team trained on running and maintaining tests
- [ ] Troubleshooting guide reviewed

---

**Verification completed on**: _________________

**Verified by**: _________________

**Notes**: _________________
