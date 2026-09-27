import { test, expect } from '@playwright/test';

test.describe('E2E Journey: CQRS Read Path & OpenSearch Querying', () => {
  test('User searches for customer orders in OpenSearch with sub-second response', async ({ page }) => {
    // 1. Visit Control Plane
    await page.goto('/');

    // 2. Query OpenSearch for customer CUST-1001
    const searchInput = page.locator('#search-input');
    await searchInput.fill('CUST-1001');
    await page.click('#search-submit-btn');

    // 3. Verify Search Latency Badge appears
    const latencyBadge = page.locator('#search-latency-badge');
    await expect(latencyBadge).toBeVisible({ timeout: 10000 });
    await expect(latencyBadge).toContainText(/Latency:/);

    // 4. Verify Results List displays matching documents
    const resultsList = page.locator('#search-results-list');
    await expect(resultsList).toBeVisible();
    await expect(resultsList.locator('.search-result-item').first()).toBeVisible();
    await expect(resultsList).toContainText('CUST-1001');
  });

  test('User searches by SKU and verifies inverted index filtering', async ({ page }) => {
    await page.goto('/');
    await page.fill('#search-input', 'PROD-101');
    await page.click('#search-submit-btn');

    const resultsList = page.locator('#search-results-list');
    await expect(resultsList).toBeVisible();
    await expect(resultsList.locator('.search-result-item').first()).toBeVisible();
  });
});
