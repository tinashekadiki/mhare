// Author: Tinashe K
import { expect, test } from "@playwright/test";

const applicantPortalUrl = process.env.APPLICANT_PORTAL_URL ?? "http://localhost:3001";
const studentPortalUrl = process.env.STUDENT_PORTAL_URL ?? "http://localhost:3002";

test.describe("applicant public entry", () => {
  test("shares institutional branding and supports mobile navigation and application guidance", async ({
    page,
  }, testInfo) => {
    const pageErrors: string[] = [];
    page.on("pageerror", (error) => pageErrors.push(error.message));
    await page.goto(applicantPortalUrl, { waitUntil: "networkidle" });
    await expect(page.getByRole("heading", { level: 1 })).toHaveText(
      "University of Zimbabwe admissions",
    );
    const crest = page.locator("[data-emhare-institution-logo]");
    await expect(crest).toBeVisible();
    await expect
      .poll(() => crest.evaluate((image: HTMLImageElement) => image.naturalWidth))
      .toBeGreaterThan(0);
    const applicantCrestPath = await crest.getAttribute("src");
    await expect(page.getByRole("button", { name: "Create account", exact: true })).toHaveCount(2);
    await expect(page.getByRole("button", { name: "Sign in", exact: true })).toHaveCount(2);

    const toggle = page.getByRole("button", { name: "Toggle public navigation" });
    if (await toggle.isVisible()) {
      await toggle.click();
      await expect(toggle).toHaveAttribute("aria-expanded", "true");
      await page
        .getByRole("navigation", { name: "Mobile public gateway", exact: true })
        .getByRole("link", { name: "Before you apply" })
        .click();
      await expect(toggle).toHaveAttribute("aria-expanded", "false");
      await toggle.click();
      await page.keyboard.press("Escape");
      await expect(toggle).toHaveAttribute("aria-expanded", "false");
    } else {
      await page
        .getByRole("navigation", { name: "Public gateway", exact: true })
        .getByRole("link", { name: "Before you apply" })
        .click();
    }
    await expect(page).toHaveURL(/#before-you-apply$/);
    const question = page
      .locator("summary")
      .filter({ hasText: "Can I return to an unfinished application?" });
    await question.focus();
    await page.keyboard.press("Enter");
    await expect(
      page.getByText("Yes. Sign in with the same account", { exact: false }),
    ).toBeVisible();
    await expect
      .poll(() =>
        page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        ),
      )
      .toBeLessThanOrEqual(1);
    await page.screenshot({ path: testInfo.outputPath("applicant-public.png"), fullPage: true });
    expect(pageErrors).toEqual([]);

    await page.goto(studentPortalUrl, { waitUntil: "networkidle" });
    await expect(page.getByRole("heading", { name: "Choose your portal" })).toBeVisible();
    await expect(page.locator("[data-emhare-institution-logo]")).toHaveAttribute(
      "src",
      applicantCrestPath!,
    );
    await expect
      .poll(() =>
        page
          .locator("[data-emhare-institution-logo]")
          .evaluate((image: HTMLImageElement) => image.naturalWidth),
      )
      .toBeGreaterThan(0);
  });

  test("sends returning applicants to sign-in", async ({ page }) => {
    await page.goto(applicantPortalUrl, { waitUntil: "networkidle" });
    await page.getByTestId("applicant-sign-in").click();
    await expect(page).toHaveURL(/\/protocol\/openid-connect\/auth\?/);
    await expect(page.locator('input[name="username"]')).toBeVisible();
    expect(new URL(page.url()).searchParams.get("redirect_uri")).toBe(
      `${applicantPortalUrl}/auth/callback`,
    );
  });

  test("sends new applicants to account registration", async ({ page }) => {
    await page.goto(applicantPortalUrl, { waitUntil: "networkidle" });
    await page.getByTestId("create-applicant-account").click();
    await expect(page).toHaveURL(/\/protocol\/openid-connect\/auth\?.*prompt=create/);
    await expect(page.getByRole("heading", { name: "Register", exact: true })).toBeVisible();
    await expect(page.locator('input[name="email"]')).toBeVisible();
    expect(new URL(page.url()).searchParams.get("redirect_uri")).toBe(
      `${applicantPortalUrl}/auth/callback`,
    );
  });
});
