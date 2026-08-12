import { defineConfig, devices } from '@playwright/test'

const adminBaseUrl = process.env.ADMIN_PORTAL_URL ?? 'http://localhost:3000'
const applicantBaseUrl = process.env.APPLICANT_PORTAL_URL ?? 'http://localhost:3001'
const workbenchBaseUrl = process.env.UI_WORKBENCH_URL ?? 'http://localhost:3003'
const studentPortalBaseUrl = process.env.STUDENT_PORTAL_URL ?? 'http://localhost:3002'

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: adminBaseUrl,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure'
  },
  webServer: [
    {
      command: 'npm run admin:dev',
      url: adminBaseUrl,
      reuseExistingServer: true,
      timeout: 120_000
    },
    {
      command: 'npm run applicant:dev',
      url: applicantBaseUrl,
      reuseExistingServer: true,
      timeout: 120_000
    },
    {
      command: 'npm run workbench:dev',
      url: workbenchBaseUrl,
      reuseExistingServer: true,
      timeout: 120_000
    },
    {
      command: 'npm run student:dev',
      url: studentPortalBaseUrl,
      reuseExistingServer: true,
      timeout: 120_000
    }
  ],
  projects: [
    {
      name: 'chromium-desktop',
      use: { ...devices['Desktop Chrome'] }
    },
    {
      name: 'chromium-mobile',
      use: { ...devices['Pixel 7'] }
    }
  ]
})
