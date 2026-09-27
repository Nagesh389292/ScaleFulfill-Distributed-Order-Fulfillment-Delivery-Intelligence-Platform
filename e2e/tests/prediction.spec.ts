import { test, expect } from '@playwright/test';

test.describe('E2E Journey: Delivery ETA & Intelligence Prediction', () => {
  test('User queries delivery ETA and observes routing calculation & FC assignment', async ({ page }) => {
    // 1. Visit Control Plane
    await page.goto('/');

    // 2. Query Prediction for verified order
    await page.fill('#prediction-order-input', 'ORD-308B9CA5');
    await page.click('#prediction-submit-btn');

    // 3. Verify Prediction Result Card displays
    const resultCard = page.locator('#prediction-result-card');
    await expect(resultCard).toBeVisible({ timeout: 10000 });

    // 4. Verify ETA value and FC assignment
    await expect(page.locator('#prediction-eta-value')).toContainText(/min|hrs/);
    await expect(page.locator('#prediction-fc-badge')).toBeVisible();
    await expect(resultCard).toContainText(/km/);
  });
});
