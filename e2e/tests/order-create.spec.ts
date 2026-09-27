import { test, expect } from '@playwright/test';

test.describe('E2E Journey: Order Creation & Transactional Ingress', () => {
  test('User places an order and verifies outbox persistence & live feed update', async ({ page }) => {
    // 1. Visit Control Plane
    await page.goto('/');
    await expect(page).toHaveTitle(/ScaleFulfill/);

    // 2. Verify System Status and Cluster Indicators
    await expect(page.locator('#system-status-badge')).toBeVisible();
    await expect(page.locator('#gateway-indicator')).toBeVisible();
    await expect(page.locator('#kafka-indicator')).toBeVisible();

    // 3. Fill Order Form
    await page.selectOption('#order-customer-select', 'CUST-1001');
    await page.selectOption('#order-product-select', 'PROD-101');
    await page.fill('#order-quantity-input', '2');

    // 4. Submit Order (Transactional Outbox Ingress)
    await page.click('#order-submit-btn');

    // 5. Verify Confirmation Banner & Latency
    const successBanner = page.locator('#order-success-banner');
    await expect(successBanner).toBeVisible({ timeout: 10000 });
    await expect(page.locator('#created-order-id')).toContainText(/ORD-/);
    await expect(page.locator('#order-latency-badge')).toBeVisible();

    // 6. Verify Order Feed Update
    const orderFeed = page.locator('#order-feed-list');
    await expect(orderFeed).toContainText('ORD-');
    await expect(page.locator('#order-status-badge').first()).toContainText(/PENDING|CREATED/);
  });
});
