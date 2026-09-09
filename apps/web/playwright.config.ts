import { defineConfig } from '@playwright/test'

const browserExecutable = process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH
const externalBaseURL = process.env.VOIDHAVEN_E2E_BASE_URL

export default defineConfig({
  testDir: './e2e',
  timeout: 30000,
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  outputDir: 'test-results',
  use: {
    baseURL: externalBaseURL || 'http://127.0.0.1:4173',
    viewport: { width: 1440, height: 1000 },
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    launchOptions: browserExecutable ? { executablePath: browserExecutable } : undefined,
  },
  webServer: externalBaseURL
    ? undefined
    : {
        command: 'vite --host 127.0.0.1',
        url: 'http://127.0.0.1:4173',
        reuseExistingServer: true,
      },
})
