import { defineConfig, devices } from "@playwright/test";

const adminBaseUrl = process.env.ADMIN_PORTAL_URL ?? "http://localhost:3100";
const applicantBaseUrl = process.env.APPLICANT_PORTAL_URL ?? "http://localhost:3101";
const studentPortalBaseUrl = process.env.STUDENT_PORTAL_URL ?? "http://localhost:3102";
const workbenchBaseUrl = process.env.UI_WORKBENCH_URL ?? "http://localhost:3103";

process.env.ADMIN_PORTAL_URL ??= adminBaseUrl;
process.env.APPLICANT_PORTAL_URL ??= applicantBaseUrl;
process.env.STUDENT_PORTAL_URL ??= studentPortalBaseUrl;
process.env.UI_WORKBENCH_URL ??= workbenchBaseUrl;

const portalEnvironment = {
  ...process.env,
  NUXT_IGNORE_LOCK: "1",
  NUXT_PUBLIC_STAFF_PORTAL_URL: `${adminBaseUrl}/`,
  NUXT_PUBLIC_APPLICANT_PORTAL_URL: `${applicantBaseUrl}/`,
  NUXT_PUBLIC_STUDENT_PORTAL_URL: `${studentPortalBaseUrl}/student`,
  VITE_EMHARE_STAFF_PORTAL_URL: `${adminBaseUrl}/`,
  VITE_EMHARE_APPLICANT_PORTAL_URL: `${applicantBaseUrl}/`,
  VITE_EMHARE_STUDENT_PORTAL_URL: `${studentPortalBaseUrl}/student`,
};

function portalPort(url: string) {
  const parsedUrl = new URL(url);
  if (!parsedUrl.port) throw new Error(`Playwright portal URL must declare a port: ${url}`);
  return parsedUrl.port;
}

export default defineConfig({
  testDir: "./tests/e2e",
  globalSetup: "./tests/e2e/global-setup.ts",
  fullyParallel: false,
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: adminBaseUrl,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  webServer: [
    {
      command: `npm run admin:dev -- --port ${portalPort(adminBaseUrl)}`,
      url: adminBaseUrl,
      reuseExistingServer: true,
      timeout: 120_000,
      env: { ...portalEnvironment, VITE_EMHARE_PORTAL_KIND: "staff" },
    },
    {
      command: `npm run applicant:dev -- --port ${portalPort(applicantBaseUrl)}`,
      url: applicantBaseUrl,
      reuseExistingServer: true,
      timeout: 120_000,
      env: { ...portalEnvironment, VITE_EMHARE_PORTAL_KIND: "applicant" },
    },
    {
      command: `npm --prefix apps/ui-workbench run dev -- --port ${portalPort(workbenchBaseUrl)}`,
      url: workbenchBaseUrl,
      reuseExistingServer: true,
      timeout: 120_000,
      env: portalEnvironment,
    },
    {
      command: `npm run student:dev -- --port ${portalPort(studentPortalBaseUrl)}`,
      url: studentPortalBaseUrl,
      reuseExistingServer: true,
      timeout: 120_000,
      env: { ...portalEnvironment, VITE_EMHARE_PORTAL_KIND: "student" },
    },
  ],
  projects: [
    {
      name: "chromium-desktop",
      use: { ...devices["Desktop Chrome"] },
    },
    {
      name: "chromium-mobile",
      use: { ...devices["Pixel 7"] },
    },
  ],
});
