import { test, expect } from '@playwright/test';

test.describe('E2E Journey: Gateway Rate Limiting & Mathematical Optimization', () => {
  test('User triggers burst traffic and observes Token-Bucket rate limiting (HTTP 429)', async ({ page }) => {
    // 1. Visit Control Plane
    await page.goto('/');

    // 2. Click Burst Traffic Button
    const burstBtn = page.locator('#gateway-burst-btn');
    await expect(burstBtn).toBeVisible();
    await burstBtn.click();

    // 3. Verify Burst Results Banner appears with 201 and 429 counts
    const burstBanner = page.locator('#burst-results-banner');
    await expect(burstBanner).toBeVisible({ timeout: 15000 });
    await expect(burstBanner).toContainText('Token-Bucket Live Evaluation');
    await expect(burstBanner).toContainText(/429 Rate-Limited/);
  });

  test('User compares Greedy vs OR-Tools MILP wave fulfillment solver', async ({ page }) => {
    await page.goto('/');

    // 1. Run Greedy Solver
    const greedyBtn = page.locator('#optimizer-run-greedy-btn');
    await greedyBtn.click();

    const optimizerCard = page.locator('#optimizer-result-card');
    await expect(optimizerCard).toContainText('Greedy Heuristic', { timeout: 10000 });
    await expect(optimizerCard).toContainText(/OPTIMAL|FEASIBLE/);

    // 2. Run OR-Tools MILP Solver
    const milpBtn = page.locator('#optimizer-run-milp-btn');
    await milpBtn.click();

    await expect(optimizerCard).toContainText('OR-Tools SCIP MILP', { timeout: 15000 });
    await expect(page.locator('#optimizer-savings-banner')).toBeVisible({ timeout: 10000 });
  });
});
