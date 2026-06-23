import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests/e2e',
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1',
    url: 'http://127.0.0.1:5174',
    reuseExistingServer: !process.env.CI
  },
  use: {
    baseURL: 'http://127.0.0.1:5174',
    trace: 'on-first-retry',
    ...devices['Desktop Chrome']
  }
});
