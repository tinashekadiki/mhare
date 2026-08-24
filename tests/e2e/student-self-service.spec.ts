import { expect, test, type Page } from "@playwright/test";
import { spawnSync } from "node:child_process";

const studentPortalUrl = process.env.STUDENT_PORTAL_URL ?? "http://localhost:3002";
const studentWorkspaceUrl = `${studentPortalUrl}/student`;
const testPassword = "Temporary-Student-Self-Service-42";

type StudentSelfServiceFixture = {
  runId: string;
  primaryStudentId: string;
  registrationId: string;
};

function createLiveFixture() {
  const result = spawnSync("bash", ["infrastructure/test/verify_student_self_service.sh"], {
    cwd: process.cwd(),
    encoding: "utf8",
  });
  if (result.status !== 0) throw new Error(result.stderr || result.stdout);
  return JSON.parse(result.stdout) as StudentSelfServiceFixture;
}

async function login(page: Page, fixture: StudentSelfServiceFixture) {
  const email = `student-primary-${fixture.runId.slice(0, 8)}@example.test`;
  await page.goto(studentWorkspaceUrl);
  await page.locator("#username").fill(email);
  await page.locator("#password").fill(testPassword);
  await page.locator("#kc-login").click();
  await page.waitForURL(studentWorkspaceUrl, { timeout: 30_000 });
  await page.waitForLoadState("networkidle");
}

test.describe("Student self-service workspace", () => {
  test("uses Year and Semester while preserving governed registration ownership", async ({
    page,
  }, testInfo) => {
    test.setTimeout(90_000);
    const fixture = createLiveFixture();
    const consoleErrors: string[] = [];
    page.on(
      "console",
      (message) => message.type() === "error" && consoleErrors.push(message.text()),
    );

    await login(page, fixture);

    await expect(page.getByRole("heading", { name: "Student workspace" })).toBeVisible();
    const studentNumberCard = page
      .getByText("Institutional identifier", { exact: true })
      .locator("..");
    await expect(
      studentNumberCard.getByText(`STU-SELF-${fixture.runId.slice(0, 8)}-1`, { exact: true }),
    ).toBeVisible();
    await expect(page.getByText("Self-service Biology", { exact: true }).first()).toBeVisible();

    await page.getByRole("tab", { name: "Registrations" }).click();
    await expect(page.getByText("Year 2 · Semester 2", { exact: true })).toBeVisible();
    await expect(page.getByText("SUBMITTED", { exact: true })).toBeVisible();

    await page.getByRole("button", { name: "View Semester 2 registration" }).click();
    const detailsDrawer = page.getByRole("dialog");
    await expect(detailsDrawer.getByText("Advanced Cell Biology", { exact: true })).toBeVisible();
    await expect(detailsDrawer.getByText("Plant Ecology", { exact: true })).toBeVisible();
    await expect(detailsDrawer.getByText(/2 records · Page 1 of 1/)).toBeVisible();
    const detailsBox = await detailsDrawer.boundingBox();
    const viewport = page.viewportSize();
    expect(detailsBox).not.toBeNull();
    expect(viewport).not.toBeNull();
    if (detailsBox && viewport && viewport.width >= 640) {
      expect(detailsBox.x).toBeGreaterThan(viewport.width / 3);
    }
    await detailsDrawer.getByRole("button", { name: "Close" }).last().click();

    await page.getByRole("button", { name: "Start registration" }).first().click();
    const registrationDrawer = page.getByRole("dialog");
    await expect(registrationDrawer.getByLabel("Period number")).toHaveCount(0);
    await registrationDrawer.getByLabel("Year of study").click();
    await page.getByRole("option", { name: "Year 2", exact: true }).click();
    await registrationDrawer.getByLabel("Semester").click();
    await page.getByRole("option", { name: "Semester 2", exact: true }).click();
    await expect(registrationDrawer.getByText(/Advanced Cell Biology/)).toBeVisible();
    await expect(registrationDrawer.getByText(/Plant Ecology/)).toBeVisible();
    const registrationBox = await registrationDrawer.boundingBox();
    if (registrationBox && viewport && viewport.width >= 640) {
      expect(registrationBox.x).toBeGreaterThan(viewport.width / 3);
    }
    await registrationDrawer.getByRole("button", { name: "Cancel" }).click();

    await page.screenshot({
      path: testInfo.outputPath("student-self-service.png"),
      fullPage: true,
    });
    expect(consoleErrors).toEqual([]);
  });
});
