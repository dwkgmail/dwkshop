import { test, expect } from '@playwright/test';

test('renders admin login shell', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('.login-page, .admin-layout').first()).toBeVisible();
});
