import { test, expect } from '@playwright/test';

test('renders mobile shop shell', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('.app-shell')).toBeVisible();
});
