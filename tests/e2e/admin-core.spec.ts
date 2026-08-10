import {
  expect,
  request as playwrightRequest,
  test,
  type APIRequestContext,
  type Page,
} from "@playwright/test";

const keycloakBaseUrl = process.env.KEYCLOAK_URL ?? "http://localhost:8099";
const keycloakRealm = process.env.KEYCLOAK_REALM ?? "emhare";
const keycloakAdminUsername = process.env.KEYCLOAK_ADMIN_USERNAME ?? "admin";
const keycloakAdminPassword = process.env.KEYCLOAK_ADMIN_PASSWORD ?? "admin";
const applicantPortalUrl =
  process.env.APPLICANT_PORTAL_URL ?? "http://localhost:3001";
const testPassword = "Passw0rd!234";

type KeycloakRole = {
  id: string;
  name: string;
};

type ProvisionedUserAccess = {
  user: {
    id: string;
    keycloakUserId: string;
    username: string;
    email: string;
    status: string;
  };
  keycloakIdentityCreated: boolean;
  temporaryPassword?: string;
};

async function createKeycloakAdminRequestContext() {
  const tokenContext = await playwrightRequest.newContext();
  const tokenResponse = await tokenContext.post(
    `${keycloakBaseUrl}/realms/master/protocol/openid-connect/token`,
    {
      form: {
        client_id: "admin-cli",
        username: keycloakAdminUsername,
        password: keycloakAdminPassword,
        grant_type: "password",
      },
    },
  );
  expect(tokenResponse.ok()).toBeTruthy();
  const tokenPayload = await tokenResponse.json();
  await tokenContext.dispose();

  return playwrightRequest.newContext({
    extraHTTPHeaders: {
      Authorization: `Bearer ${tokenPayload.access_token}`,
      "Content-Type": "application/json",
    },
  });
}

async function findKeycloakUserId(
  adminRequest: APIRequestContext,
  username: string,
) {
  const response = await adminRequest.get(
    `${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users?username=${encodeURIComponent(username)}&exact=true`,
  );
  expect(response.ok()).toBeTruthy();
  const users = await response.json();
  expect(users.length).toBeGreaterThan(0);
  return users[0].id as string;
}

async function assignRealmRole(
  adminRequest: APIRequestContext,
  userId: string,
  roleName: string,
) {
  const roleResponse = await adminRequest.get(
    `${keycloakBaseUrl}/admin/realms/${keycloakRealm}/roles/${roleName}`,
  );
  expect(roleResponse.ok()).toBeTruthy();
  const role = (await roleResponse.json()) as KeycloakRole;
  const response = await adminRequest.post(
    `${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users/${userId}/role-mappings/realm`,
    { data: [role] },
  );
  expect([204, 409]).toContain(response.status());
}

async function ensureSystemAdminUser(username: string) {
  const adminRequest = await createKeycloakAdminRequestContext();
  const createResponse = await adminRequest.post(
    `${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users`,
    {
      data: {
        username,
        email: username,
        firstName: "Codex",
        lastName: "Admin",
        enabled: true,
        emailVerified: true,
        credentials: [
          {
            type: "password",
            value: testPassword,
            temporary: false,
          },
        ],
      },
    },
  );
  expect([201, 409]).toContain(createResponse.status());

  const userId = await findKeycloakUserId(adminRequest, username);
  await assignRealmRole(adminRequest, userId, "system-admin");
  await adminRequest.dispose();
}

async function ensureApplicantUser(username: string) {
  const adminRequest = await createKeycloakAdminRequestContext();
  const createResponse = await adminRequest.post(
    `${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users`,
    {
      data: {
        username,
        email: username,
        firstName: "Codex",
        lastName: "Applicant",
        enabled: true,
        emailVerified: true,
        credentials: [
          {
            type: "password",
            value: testPassword,
            temporary: false,
          },
        ],
      },
    },
  );
  expect([201, 409]).toContain(createResponse.status());
  await adminRequest.dispose();
}

async function ensureOperationalUser(
  username: string,
  roleName: string,
  lastName: string,
) {
  const adminRequest = await createKeycloakAdminRequestContext();
  const createResponse = await adminRequest.post(
    `${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users`,
    {
      data: {
        username,
        email: username,
        firstName: "Codex",
        lastName,
        enabled: true,
        emailVerified: true,
        credentials: [
          {
            type: "password",
            value: testPassword,
            temporary: false,
          },
        ],
      },
    },
  );
  expect([201, 409]).toContain(createResponse.status());

  const userId = await findKeycloakUserId(adminRequest, username);
  await assignRealmRole(adminRequest, userId, roleName);
  await adminRequest.dispose();
}

async function loginWithKeycloak(page: Page, username: string) {
  await page.getByLabel(/email|username/i).fill(username);
  await page.getByRole("textbox", { name: "Password" }).fill(testPassword);
  await page.getByRole("button", { name: /sign in|log in/i }).click();
}

async function readBrowserAccessToken(page: Page) {
  return page.evaluate(() => {
    for (let index = 0; index < localStorage.length; index += 1) {
      const key = localStorage.key(index);
      if (!key?.startsWith("oidc.user:")) continue;
      const storedUser = localStorage.getItem(key);
      if (!storedUser) continue;
      const parsedUser = JSON.parse(storedUser) as { access_token?: string };
      if (parsedUser.access_token) return parsedUser.access_token;
    }
    return null;
  });
}

test.describe("Core Identity authentication and RBAC", () => {
  test("shows only the operational sections granted to a finance officer", async ({
    page,
  }, testInfo) => {
    const username = `codex.finance.${testInfo.project.name.replace(/[^a-z0-9]+/gi, "-").toLowerCase()}@example.test`;
    await ensureOperationalUser(username, "finance-officer", "Finance");

    await page.goto("/operations");
    await expect(page).toHaveURL(
      /\/realms\/emhare\/protocol\/openid-connect\/auth/,
      { timeout: 15_000 },
    );
    await loginWithKeycloak(page, username);

    await expect(page).toHaveURL(/\/operations$/, { timeout: 15_000 });
    await expect(
      page.getByRole("button", { name: "Finance", exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Admissions", exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Core and Identity", exact: true }),
    ).toHaveCount(0);
    await expect(
      page.getByRole("button", { name: "Academic Setup", exact: true }),
    ).toHaveCount(0);
    await expect(
      page.getByRole("button", {
        name: "Teaching and Assessment",
        exact: true,
      }),
    ).toHaveCount(0);
  });

  test("redirects applicant identities out of the administration portal", async ({
    page,
  }, testInfo) => {
    const username = `codex.applicant.${testInfo.project.name.replace(/[^a-z0-9]+/gi, "-").toLowerCase()}@example.test`;
    await ensureApplicantUser(username);

    const coreUsersRequests: string[] = [];
    page.on("request", (request) => {
      if (request.url().endsWith("/api/core/users")) {
        coreUsersRequests.push(request.url());
      }
    });

    await page.goto("/operations/core");
    await expect(page).toHaveURL(
      /\/realms\/emhare\/protocol\/openid-connect\/auth/,
      { timeout: 15_000 },
    );
    await loginWithKeycloak(page, username);

    await expect(page).toHaveURL(
      new RegExp(
        `^${applicantPortalUrl.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}/?`,
      ),
      { timeout: 15_000 },
    );
    expect(coreUsersRequests).toHaveLength(0);
  });

  test("loads only the selected Core workspace dataset", async ({
    page,
  }, testInfo) => {
    const username = `codex.lazy-core.${testInfo.project.name.replace(/[^a-z0-9]+/gi, "-").toLowerCase()}@example.test`;
    await ensureSystemAdminUser(username);

    const coreUsersRequests: string[] = [];
    const coreRolesRequests: string[] = [];
    const coreStatisticsRequests: string[] = [];
    page.on("request", (request) => {
      if (request.url().endsWith("/api/core/users"))
        coreUsersRequests.push(request.url());
      if (request.url().endsWith("/api/core/roles"))
        coreRolesRequests.push(request.url());
      if (request.url().endsWith("/api/core/statistics"))
        coreStatisticsRequests.push(request.url());
    });

    await page.goto("/operations/core");
    await expect(page).toHaveURL(
      /\/realms\/emhare\/protocol\/openid-connect\/auth/,
      { timeout: 15_000 },
    );
    await loginWithKeycloak(page, username);

    await expect(
      page.getByRole("heading", { name: "Institution profile" }),
    ).toBeVisible();
    await expect.poll(() => coreStatisticsRequests.length).toBe(1);
    expect(coreUsersRequests).toHaveLength(0);
    expect(coreRolesRequests).toHaveLength(0);
    await expect(
      page.getByTestId("core-stat-users").locator("p").nth(1),
    ).toHaveText(/^[1-9]\d*$/);
    await expect(
      page.getByTestId("core-stat-roles").locator("p").nth(1),
    ).toHaveText(/^[1-9]\d*$/);
    await expect(
      page.getByTestId("core-stat-permissions").locator("p").nth(1),
    ).toHaveText(/^[1-9]\d*$/);
    await expect(
      page.getByTestId("core-stat-lookup-sets").locator("p").nth(1),
    ).toHaveText(/^[1-9]\d*$/);

    await page.getByRole("button", { name: "Users" }).click();
    await expect.poll(() => coreUsersRequests.length).toBe(1);
    expect(coreRolesRequests).toHaveLength(0);

    await page.getByRole("button", { name: "RBAC" }).click();
    await expect.poll(() => coreRolesRequests.length).toBe(1);
  });

  test("provisions one user in Keycloak and Core or leaves neither", async ({
    browser,
    page,
  }, testInfo) => {
    test.setTimeout(60_000);
    const projectSuffix = testInfo.project.name
      .replace(/[^a-z0-9]+/gi, "-")
      .toLowerCase();
    const administratorUsername = `codex.provisioning-admin.${projectSuffix}@example.test`;
    const uniqueSuffix = `${projectSuffix}-${Date.now()}`;
    const provisionedUsername = `codex.provisioned.${uniqueSuffix}`;
    const provisionedEmail = `${provisionedUsername}@example.test`;
    await ensureSystemAdminUser(administratorUsername);

    const keycloakAdminRequest = await createKeycloakAdminRequestContext();
    let provisionedAccess: ProvisionedUserAccess | null = null;
    let browserAccessToken: string | null = null;

    try {
      await page.goto("/operations/core");
      await expect(page).toHaveURL(
        /\/realms\/emhare\/protocol\/openid-connect\/auth/,
        { timeout: 15_000 },
      );
      await loginWithKeycloak(page, administratorUsername);
      await expect(page).toHaveURL(/\/operations\/core$/, {
        timeout: 15_000,
      });
      browserAccessToken = await readBrowserAccessToken(page);
      expect(browserAccessToken).toBeTruthy();

      await page.getByRole("button", { name: "Users" }).click();
      await page.getByRole("button", { name: "Create user" }).click();
      const userDrawer = page.getByRole("dialog", {
        name: "Provision user access",
      });
      await userDrawer.getByLabel("Username").fill(provisionedUsername);
      await userDrawer.getByLabel("Email").fill(provisionedEmail);
      await userDrawer
        .getByLabel("Display name")
        .fill("Provisioning Contract Test");
      await userDrawer
        .getByRole("button", { name: "Continue to access" })
        .click();
      await userDrawer.getByLabel("Role").click();
      await page.getByRole("option", { name: "System Admin" }).click();
      await expect(userDrawer.getByText("CORE").first()).toBeVisible();
      await userDrawer.getByRole("button", { name: "Review profile" }).click();
      await expect(userDrawer.getByText("Ready to activate")).toBeVisible();

      const provisioningResponsePromise = page.waitForResponse(
        (response) =>
          response.request().method() === "POST" &&
          response.url().endsWith("/api/core/users/provisioned-access"),
      );
      await userDrawer
        .getByRole("button", { name: "Create and activate user" })
        .click();
      const provisioningResponse = await provisioningResponsePromise;
      expect(provisioningResponse.ok()).toBeTruthy();
      provisionedAccess =
        (await provisioningResponse.json()) as ProvisionedUserAccess;

      expect(provisionedAccess.keycloakIdentityCreated).toBe(true);
      expect(provisionedAccess.temporaryPassword).toHaveLength(20);
      expect(provisionedAccess.user.status).toBe("ACTIVE");
      expect(provisionedAccess.user.username).toBe(provisionedUsername);
      expect(provisionedAccess.user.email).toBe(provisionedEmail);

      await expect(
        page.getByRole("heading", { name: "User provisioned" }),
      ).toBeVisible();
      await expect(page.locator(".swal2-html-container")).toContainText(
        provisionedAccess.temporaryPassword!,
      );

      const keycloakUserId = provisionedAccess.user.keycloakUserId;
      const keycloakUserResponse = await keycloakAdminRequest.get(
        `${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users/${keycloakUserId}`,
      );
      expect(keycloakUserResponse.ok()).toBeTruthy();
      const keycloakUser = await keycloakUserResponse.json();
      expect(keycloakUser.id).toBe(keycloakUserId);
      expect(keycloakUser.username).toBe(provisionedEmail);
      expect(keycloakUser.email).toBe(provisionedEmail);
      expect(keycloakUser.enabled).toBe(true);
      expect(keycloakUser.requiredActions).toContain("UPDATE_PASSWORD");

      const firstLoginContext = await browser.newContext();
      try {
        const firstLoginPage = await firstLoginContext.newPage();
        await firstLoginPage.goto("http://localhost:3000/operations");
        await firstLoginPage
          .getByLabel(/email|username/i)
          .fill(provisionedEmail);
        await firstLoginPage
          .getByRole("textbox", { name: "Password" })
          .fill(provisionedAccess.temporaryPassword!);
        await firstLoginPage
          .getByRole("button", { name: /sign in|log in/i })
          .click();
        await expect(firstLoginPage).toHaveURL(
          /\/login-actions\/required-action/,
          {
            timeout: 15_000,
          },
        );
        await expect(
          firstLoginPage.getByRole("heading", { name: /update password/i }),
        ).toBeVisible();
      } finally {
        await firstLoginContext.close();
      }
    } finally {
      if (provisionedAccess && browserAccessToken) {
        const coreApiRequest = await playwrightRequest.newContext({
          extraHTTPHeaders: {
            Authorization: `Bearer ${browserAccessToken}`,
          },
        });
        const deleteLocalUserResponse = await coreApiRequest.delete(
          `http://localhost:8080/api/core/users/${provisionedAccess.user.id}`,
        );
        expect(deleteLocalUserResponse.status()).toBe(204);
        await coreApiRequest.dispose();
      }
      if (provisionedAccess) {
        const deleteKeycloakUserResponse = await keycloakAdminRequest.delete(
          `${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users/${provisionedAccess.user.keycloakUserId}`,
        );
        expect([204, 404]).toContain(deleteKeycloakUserResponse.status());
      }
      await keycloakAdminRequest.dispose();
    }
  });

  test("redirects unauthenticated admin users to Keycloak and loads Core data after login", async ({
    page,
  }, testInfo) => {
    const username = `codex.admin.${testInfo.project.name.replace(/[^a-z0-9]+/gi, "-").toLowerCase()}@example.test`;
    await ensureSystemAdminUser(username);

    const consoleErrors: string[] = [];
    const pageErrors: string[] = [];

    page.on("console", (message) => {
      if (message.type() === "error") {
        consoleErrors.push(message.text());
      }
    });
    page.on("pageerror", (error) => pageErrors.push(error.message));

    await page.goto("/operations/core");
    await expect(page).toHaveURL(
      /\/realms\/emhare\/protocol\/openid-connect\/auth/,
      { timeout: 15_000 },
    );
    await expect(
      page.getByRole("heading", { name: /sign in to your account/i }),
    ).toBeVisible();

    await loginWithKeycloak(page, username);

    await expect(page).toHaveURL(/\/operations\/core$/, { timeout: 15_000 });
    await expect(
      page.getByRole("heading", { name: "Core Identity" }),
    ).toBeVisible();
    await expect(
      page.getByRole("heading", { name: "Institution profile" }),
    ).toBeVisible();
    const profileRegion = page.getByRole("region", {
      name: "Institution profile",
    });
    const institutionLogo = profileRegion.locator(
      "[data-emhare-institution-logo]",
    );
    await expect(institutionLogo).toBeVisible();
    await expect(
      institutionLogo.locator("img, [data-emhare-logo-fallback]"),
    ).toHaveCount(1);

    await page.getByRole("button", { name: "Edit profile" }).click();
    const profileDrawer = page.getByRole("dialog", {
      name: "Edit institution profile",
    });
    await expect(
      profileDrawer.getByRole("heading", { name: "Institution identity" }),
    ).toBeVisible();
    await expect(
      profileDrawer.getByRole("heading", {
        name: "Brand and official documents",
      }),
    ).toBeVisible();
    await expect(profileDrawer.getByLabel("Email address")).toHaveValue(/@/);
    await expect(profileDrawer.locator('input[type="file"]')).toHaveAttribute(
      "accept",
      "image/png,image/jpeg",
    );
    await profileDrawer
      .getByRole("textbox", { name: /Institution code/ })
      .fill("TEMP");
    await profileDrawer.getByRole("button", { name: "Cancel" }).click();
    await expect(profileDrawer).not.toBeVisible();
    await expect(profileRegion.locator("dd").first()).toHaveText("UZ");

    await page.getByRole("button", { name: "Users" }).click();
    const usersTable = page.locator("[data-emhare-paginated-table]").first();
    await expect(usersTable).toBeVisible();
    await expect(usersTable.locator("[data-emhare-pagination]")).toContainText(
      /\d+ total · Page 1 of \d+/,
    );
    await expect(usersTable.getByLabel("Rows per page")).toContainText(
      "10 per page",
    );
    await page.getByPlaceholder("Search").fill(username);
    await expect(page.getByText(username).first()).toBeVisible();
    await page.getByRole("button", { name: "Create user" }).click();
    const userDrawer = page.getByRole("dialog");
    await expect(userDrawer).toBeVisible();
    if (testInfo.project.name === "chromium-mobile") {
      await expect
        .poll(async () => (await userDrawer.boundingBox())?.x)
        .toBeLessThanOrEqual(1);
      const settledDrawerBounds = await userDrawer.boundingBox();
      expect(settledDrawerBounds?.width).toBeLessThanOrEqual(
        page.viewportSize()!.width,
      );
    }
    await expect(
      page.getByRole("heading", { name: "Provision user access" }),
    ).toBeVisible();
    const usernameInput = userDrawer.getByLabel("Username");
    await expect(usernameInput).toBeVisible();
    await expect(
      userDrawer.getByText("Keycloak account included"),
    ).toBeVisible();

    const drawerBounds = await userDrawer.boundingBox();
    const usernameBounds = await usernameInput.boundingBox();
    expect(drawerBounds).not.toBeNull();
    expect(usernameBounds).not.toBeNull();
    expect(usernameBounds!.width).toBeGreaterThan(drawerBounds!.width * 0.35);

    await usernameInput.fill("access.workflow.test");
    await userDrawer
      .getByLabel("Email")
      .fill("access.workflow.test@example.test");
    await userDrawer.getByLabel("Display name").fill("Access Workflow Test");
    await userDrawer
      .getByRole("button", { name: "Continue to access" })
      .click();
    await expect(
      userDrawer.getByText("Assign the user's working access"),
    ).toBeVisible();
    await userDrawer.getByLabel("Role").click();
    await page.getByRole("option", { name: "System Admin" }).click();
    await expect(userDrawer.getByText("CORE").first()).toBeVisible();
    await userDrawer.getByRole("button", { name: "Review profile" }).click();
    await expect(userDrawer.getByText("Ready to activate")).toBeVisible();
    await expect(userDrawer.getByText("Access Workflow Test")).toBeVisible();
    await expect(userDrawer.getByText("System Admin")).toBeVisible();
    await expect(
      userDrawer.getByRole("button", { name: "Create and activate user" }),
    ).toBeVisible();
    await userDrawer.getByRole("button", { name: "Back" }).click();
    await expect(
      userDrawer.getByText("Assign the user's working access"),
    ).toBeVisible();
    await userDrawer.getByRole("button", { name: "Cancel" }).click();
    await expect(page.getByRole("dialog")).not.toBeVisible();

    await page.getByRole("button", { name: "RBAC" }).click();
    await expect(page.getByText("System Admin").first()).toBeVisible();
    await page.getByRole("tab", { name: /Permissions/ }).click();
    await expect(page.getByText("Manage users").first()).toBeVisible();
    await page.getByRole("tab", { name: /Roles/ }).click();
    await page.getByRole("button", { name: "Create role" }).click();
    const roleDrawer = page.getByRole("dialog");
    await expect(roleDrawer).toBeVisible();
    if (testInfo.project.name === "chromium-mobile") {
      await expect
        .poll(async () => (await roleDrawer.boundingBox())?.x)
        .toBeLessThanOrEqual(1);
    }
    await expect(
      page.getByRole("heading", { name: "Create role" }),
    ).toBeVisible();
    await roleDrawer.getByRole("button", { name: "Cancel" }).click();

    await page.getByRole("tab", { name: /User assignments/ }).click();
    await page.getByRole("button", { name: "Assign role" }).click();
    const assignmentDrawer = page.getByRole("dialog");
    const assignmentRole = assignmentDrawer.getByLabel("Role");
    const academicUnit = assignmentDrawer.getByLabel("Academic unit");
    await expect(assignmentRole).toBeVisible();
    await expect(assignmentRole).toContainText("Select role");
    expect((await assignmentRole.boundingBox())?.height).toBeGreaterThanOrEqual(
      32,
    );
    await expect(academicUnit).toBeVisible();
    await expect(academicUnit).toContainText("Search by unit code or name");
    await expect(academicUnit).toBeEnabled();
    await academicUnit.click();
    await expect(page.getByRole("option").first()).toBeVisible();
    await page.keyboard.press("Escape");
    await assignmentRole.click();
    await page.getByRole("option", { name: "System Admin" }).click();
    await expect(academicUnit).toBeDisabled();
    await expect(
      assignmentDrawer.getByText("This system role applies institution-wide."),
    ).toBeVisible();
    await assignmentDrawer.getByRole("button", { name: "Cancel" }).click();

    await page.getByRole("button", { name: "Workflow Tasks" }).click();
    await expect(
      page.getByRole("heading", { name: "Workflow tasks" }),
    ).toBeVisible();
    await expect(page.getByText("Governed operational queue")).toBeVisible();

    await page.getByRole("button", { name: "Reference Data" }).click();
    await expect(page.getByRole("tab", { name: /Countries/ })).toHaveCount(0);
    await expect(
      page.getByRole("tab", { name: /Lookup values/ }),
    ).toBeVisible();
    const countryLookupValues = page.getByRole("region", {
      name: "Lookup values · Countries",
    });
    await expect(countryLookupValues).toBeVisible();
    await expect(countryLookupValues.getByLabel("Lookup set")).toContainText(
      "Countries",
    );
    await expect(
      countryLookupValues.getByText("Zimbabwe", { exact: true }),
    ).toBeVisible();
    await expect(
      countryLookupValues.getByRole("button", { name: "Create country" }),
    ).toBeVisible();

    let createdApplicationTypeRequest: Record<string, unknown> | null = null;
    const applicationTypeFixtures: Array<Record<string, unknown>> = [
      {
        id: "11111111-1111-4111-8111-111111111111",
        code: "POSTGRAD",
        name: "Postgraduate",
        requiresEmploymentHistory: true,
        requiresReferees: true,
        financeFeeStructureId: "12345678-1234-4234-8234-123456789abc",
        financeFeeStructureCode: "APP-PG-LOCAL",
        financeFeeStructureName: "Postgraduate application fee",
        active: true,
        version: 0,
      },
    ];
    await page.route("**/api/finance/fee-structures", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          structures: [
            {
              id: "12345678-1234-4234-8234-123456789abc",
              code: "APP-PG-LOCAL",
              name: "Postgraduate application fee",
              feeContext: "APPLICATION",
              scopeType: "PROGRAMME_LEVEL",
              scopeReferenceId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
              scopeReferenceCode: "PG",
              scopeReferenceName: "Postgraduate",
              programmeLevelId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
              programmeLevelCode: "PG",
              programmeLevelName: "Postgraduate",
              applicantCategoryCode: "LOCAL",
              transactionCurrencyCode: "USD",
              effectiveFrom: "2026-08-01T00:00:00Z",
              effectiveUntil: null,
              status: "ACTIVE",
              preparedByUserId: "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
              activatedByUserId: "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
              activatedAt: "2026-08-01T08:00:00Z",
              version: 1,
              lines: [],
              attachments: [],
              selectedAttachment: null,
            },
          ],
        }),
      }),
    );
    await page.route("**/api/admissions/application-types", async (route) => {
      if (route.request().method() === "GET") {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(applicationTypeFixtures),
        });
        return;
      }
      createdApplicationTypeRequest = route.request().postDataJSON() as Record<
        string,
        unknown
      >;
      applicationTypeFixtures.push({
        id: "99999999-9999-4999-8999-999999999999",
        code: String(createdApplicationTypeRequest.code),
        name: String(createdApplicationTypeRequest.name),
        requiresEmploymentHistory: Boolean(
          createdApplicationTypeRequest.requiresEmploymentHistory,
        ),
        requiresReferees: Boolean(
          createdApplicationTypeRequest.requiresReferees,
        ),
        financeFeeStructureId:
          createdApplicationTypeRequest.financeFeeStructureId as string | null,
        financeFeeStructureCode:
          createdApplicationTypeRequest.financeFeeStructureCode as
            string | null,
        financeFeeStructureName:
          createdApplicationTypeRequest.financeFeeStructureName as
            string | null,
        active: Boolean(createdApplicationTypeRequest.active),
        version: 0,
      });
      await route.fulfill({
        status: 201,
        contentType: "application/json",
        body: JSON.stringify(applicationTypeFixtures.at(-1)),
      });
    });

    await page.goto("/operations/application-types");
    await expect(
      page.getByRole("heading", { name: "Application types" }),
    ).toBeVisible();
    if (testInfo.project.name === "chromium-mobile") {
      await page.getByRole("button", { name: "Open sidebar" }).click();
    }
    await expect(
      page
        .getByRole("link", { name: "Application types", exact: true })
        .first(),
    ).toBeVisible();
    if (testInfo.project.name === "chromium-mobile") {
      await page.keyboard.press("Escape");
    }
    const applicationTypeTable = page.locator("[data-emhare-paginated-table]");
    await expect(
      applicationTypeTable.locator("[data-emhare-pagination]"),
    ).toContainText("1 of 1 records · Page 1 of 1");
    await expect(
      applicationTypeTable.getByText("POSTGRAD", { exact: true }),
    ).toBeVisible();
    await page.getByRole("button", { name: "New application type" }).click();
    const applicationTypeDrawer = page.getByRole("dialog", {
      name: "Create application type",
    });
    await applicationTypeDrawer.getByLabel("Code").fill("UNDERGRAD");
    await applicationTypeDrawer.getByLabel("Name").fill("Undergraduate");
    await applicationTypeDrawer
      .getByRole("button", { name: "Create application type" })
      .click();
    await expect(applicationTypeDrawer).not.toBeVisible();
    expect(createdApplicationTypeRequest).toEqual(
      expect.objectContaining({
        code: "UNDERGRAD",
        name: "Undergraduate",
        requiresEmploymentHistory: false,
        requiresReferees: false,
        financeFeeStructureId: null,
        financeFeeStructureCode: null,
        financeFeeStructureName: null,
        active: false,
      }),
    );
    await expect(
      applicationTypeTable.getByText("UNDERGRAD", { exact: true }),
    ).toBeVisible();
    const academicYearId = "22222222-2222-4222-8222-222222222222";
    const intakeId = "33333333-3333-4333-8333-333333333333";
    const academicUnitId = "55555555-5555-4555-8555-555555555555";
    await page.route("**/api/academic/overview", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          academicUnitTypes: [],
          academicUnits: [
            {
              id: academicUnitId,
              academicUnitTypeId: "66666666-6666-4666-8666-666666666666",
              academicUnitTypeCode: "FACULTY",
              parentId: null,
              code: "SCI",
              name: "Faculty of Science",
              status: "ACTIVE",
              legacyFacultyCode: null,
              legacyDepartmentCode: null,
              version: 0,
            },
          ],
          academicYears: [
            { id: academicYearId, name: "2026 - 2027 Academic Year" },
          ],
          academicPeriodTypes: [],
          academicPeriods: [],
          intakes: [
            {
              id: intakeId,
              academicYearId,
              code: "AUG-2026",
              name: "August 2026 Intake",
            },
          ],
          programmeLevels: [],
          programmeTypes: [],
          programmes: [],
          modules: [],
        }),
      }),
    );
    await expect(
      page.getByRole("link", { name: "Admission cycles", exact: true }),
    ).toHaveCount(0);
    if (testInfo.project.name === "chromium-mobile") {
      await page.getByRole("button", { name: "Open sidebar" }).click();
    }
    await expect(
      page.getByRole("link", { name: "Academic calendar", exact: true }),
    ).toBeVisible();
    if (testInfo.project.name === "chromium-mobile") {
      await page.keyboard.press("Escape");
    }

    const documentRegisterFixture = Array.from({ length: 11 }, (_, index) => ({
      applicationId: `77777777-7777-4777-8777-${String(index + 1).padStart(12, "0")}`,
      applicationNumber: `APP-2026-${String(index + 1).padStart(5, "0")}`,
      applicantName: `Applicant ${String(index + 1).padStart(2, "0")}`,
      applicationStatus: "UNDER_REVIEW",
      documents: {
        applicationId: `77777777-7777-4777-8777-${String(index + 1).padStart(12, "0")}`,
        applicationNumber: `APP-2026-${String(index + 1).padStart(5, "0")}`,
        requiredDocumentsUploaded: true,
        requiredDocumentsVerified: false,
        missingRequirementCodes: [],
        pendingRequirementCodes: ["NATIONAL_ID"],
        rejectedRequirementCodes: [],
        requirements: [],
      },
    }));
    await page.route(
      `**/api/admissions/academic-units/${academicUnitId}/documents`,
      (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(documentRegisterFixture),
        }),
    );
    await page.route("**/api/admissions/applications", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(
          documentRegisterFixture.map((entry) => ({
            id: entry.applicationId,
            applicationNumber: entry.applicationNumber,
            applicantName: entry.applicantName,
            intakeId,
            intakeCode: "AUG-2026",
            status: entry.applicationStatus,
            programmeChoices: [],
          })),
        ),
      }),
    );
    await page.goto("/operations/admissions-documents");
    await expect(
      page.getByRole("heading", { name: "Academic-unit document register" }),
    ).toBeVisible();
    const documentRegister = page.locator("[data-emhare-paginated-collection]");
    await expect(
      documentRegister.locator("[data-emhare-pagination]"),
    ).toContainText("1–10 of 11 records · Page 1 of 2");
    await expect(documentRegister.getByText("APP-2026-00001")).toBeVisible();
    await expect(
      documentRegister.getByText("APP-2026-00011"),
    ).not.toBeVisible();
    await documentRegister.getByRole("button", { name: "Page 2" }).click();
    await expect(documentRegister.getByText("APP-2026-00011")).toBeVisible();

    expect(consoleErrors).toEqual([]);
    expect(pageErrors).toEqual([]);
  });

  test("sends applicant sign-up through Keycloak registration before admissions work", async ({
    page,
  }) => {
    await page.goto(applicantPortalUrl);
    await expect(
      page.getByRole("heading", { name: "University of Zimbabwe admissions" }),
    ).toBeVisible({ timeout: 15_000 });

    await page
      .getByRole("button", { name: /create account/i })
      .first()
      .click();

    await expect(page).toHaveURL(
      /\/realms\/emhare\/protocol\/openid-connect\/auth/,
    );
    await expect(page).toHaveURL(/[?&]prompt=create(?:&|$)/);
    await expect(
      page.getByRole("heading", { name: "Register", exact: true }),
    ).toBeVisible();
    await expect(page.getByRole("textbox", { name: "Email" })).toBeVisible();
  });

  test("separates eligibility configuration from the officer evaluation queue with tabs", async ({
    page,
  }, testInfo) => {
    const username = `codex.evaluation.${testInfo.project.name.replace(/[^a-z0-9]+/gi, "-").toLowerCase()}@example.test`;
    await ensureSystemAdminUser(username);

    const consoleErrors: string[] = [];
    const pageErrors: string[] = [];
    page.on("console", (message) => {
      if (message.type() === "error") consoleErrors.push(message.text());
    });
    page.on("pageerror", (error) => pageErrors.push(error.message));

    await page.route("**/api/admissions/applications", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([
          {
            id: "3b76d88b-4df8-4ec9-93fb-ce1d45c1db24",
            applicationNumber: "APP-2027-00031",
            applicantNumber: "APL-00031",
            applicantName: "Tariro Moyo",
            intakeId: "b535c76e-a477-43bb-880a-1843c0e66e3c",
            intakeCode: "AUG-2027",
            applicationTypeId: "359cbb03-cf95-4b2a-853d-fe602f7e7fb8",
            applicationTypeName: "Undergraduate",
            status: "UNDER_REVIEW",
            paymentRequired: true,
            paymentClearanceStatus: "PAID",
            paymentWaiverReason: null,
            canSubmit: false,
            canEnterReview: true,
            payment: null,
            programmeChoices: [
              {
                id: "5261d957-a516-4234-b018-f4dbc5a53117",
                programmeId: "2f5f3b35-524f-4490-a07d-bd1825535027",
                programmeVersionId: "2292b0e2-4cad-47b4-a7ad-b9ebeb272f65",
                programmeCode: "BACC",
                programmeName: "Bachelor of Accountancy",
                awardName: "Bachelor of Accountancy",
                owningAcademicUnitName: "Business School",
                programmeVersionCode: "2027.1",
                choiceRank: 1,
                choiceStatus: "PENDING",
                evaluationSummary: null,
                decisionReason: null,
              },
            ],
          },
        ]),
      }),
    );
    await page.route("**/api/admissions/requirement-sets", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([
          {
            id: "73ab4bda-a8d4-4bee-a0b1-35af73960372",
            programmeId: "2f5f3b35-524f-4490-a07d-bd1825535027",
            applicationTypeId: "359cbb03-cf95-4b2a-853d-fe602f7e7fb8",
            intakeId: "b535c76e-a477-43bb-880a-1843c0e66e3c",
            versionCode: "BACC-2027.1",
            effectiveFrom: "2027-01-01",
            effectiveTo: null,
            status: "APPROVED",
            minimumTotalPoints: 12,
            requiresEnglish: true,
            advancedRulesVersion: null,
            approvedAt: "2026-12-01T08:00:00Z",
          },
        ]),
      }),
    );

    await page.goto("/operations/admissions-evaluation");
    await expect(page).toHaveURL(
      /\/realms\/emhare\/protocol\/openid-connect\/auth/,
      { timeout: 15_000 },
    );
    await loginWithKeycloak(page, username);

    await expect(page).toHaveURL(/\/operations\/admissions-evaluation$/);
    const applicationsTab = page.getByRole("tab", {
      name: /Applications in evaluation/,
    });
    const requirementSetsTab = page.getByRole("tab", {
      name: /Requirement-set versions/,
    });
    await expect(applicationsTab).toHaveAttribute("aria-selected", "true");
    await expect(
      page.getByRole("heading", { name: "Applications in evaluation" }),
    ).toBeVisible();
    await expect(page.getByText("APP-2027-00031")).toBeVisible();
    await expect(
      page.getByRole("heading", { name: "Requirement-set versions" }),
    ).toHaveCount(0);
    await expect(
      page.getByRole("button", { name: "New requirement set" }),
    ).toHaveCount(0);
    await expect(page.locator("[data-emhare-pagination]:visible")).toHaveCount(
      2,
    );

    await requirementSetsTab.click();
    await expect(requirementSetsTab).toHaveAttribute("aria-selected", "true");
    await expect(
      page.getByRole("heading", { name: "Requirement-set versions" }),
    ).toBeVisible();
    await expect(page.getByText("BACC-2027.1")).toBeVisible();
    await expect(
      page.getByRole("heading", { name: "Applications in evaluation" }),
    ).toHaveCount(0);
    await expect(
      page.getByRole("button", { name: "New requirement set" }),
    ).toBeVisible();
    await expect(page.locator("[data-emhare-pagination]:visible")).toHaveCount(
      1,
    );

    expect(consoleErrors).toEqual([]);
    expect(pageErrors).toEqual([]);
  });

  test("presents the student conversion queue as an operational control surface", async ({
    page,
  }, testInfo) => {
    const username = `codex.conversions.${testInfo.project.name.replace(/[^a-z0-9]+/gi, "-").toLowerCase()}@example.test`;
    await ensureSystemAdminUser(username);

    const consoleErrors: string[] = [];
    const pageErrors: string[] = [];
    page.on("console", (message) => {
      if (message.type() === "error") consoleErrors.push(message.text());
    });
    page.on("pageerror", (error) => pageErrors.push(error.message));

    await page.route("**/api/student-records/conversions", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([
          {
            id: "f81f3ec7-b0b4-485d-9cc4-4ef1294df681",
            status: "COMPLETED",
            financeProvisioningStatus: "COMPLETED",
            portalProvisioningStatus: "COMPLETED",
            sourceApplicationId: "05a5706e-9de8-48a9-a8c1-467889da1097",
            sourceOfferId: "c4c2ae87-66bf-4850-9aca-079005b4d5e4",
            studentId: "a3e29e1b-611d-462f-a358-8e7c090a9d75",
            studentNumber: "STU-2027-0000142",
            studentStatus: "ACTIVE",
            programmeEnrolmentId: "fc85bf92-5cdc-4f85-a93e-ddd2f5203374",
            programmeCode: "BACC",
            programmeName: "Bachelor of Accountancy",
            programmeEnrolmentStatus: "ACTIVE",
            requestedAt: "2027-01-08T10:15:30Z",
            completedAt: "2027-01-08T10:16:04Z",
            failureReason: null,
            retryCount: 0,
            lastRetryAt: null,
            lastRetryByUserId: null,
            lastRetryReason: null,
          },
          {
            id: "2aa0bc99-d9c7-4557-abf1-c63bd4d5078c",
            status: "FAILED",
            financeProvisioningStatus: "COMPLETED",
            portalProvisioningStatus: "FAILED",
            sourceApplicationId: "993954ff-f78b-4f96-914e-2d44447c92aa",
            sourceOfferId: "d676d377-1d8b-48cc-b91d-1c9d7dd3ebd4",
            studentId: "189e35bf-8fc5-4887-a81f-0bf18f8bf71a",
            studentNumber: "STU-2027-0000143",
            studentStatus: "PROVISIONING",
            programmeEnrolmentId: "0017a106-1355-4d62-b95f-c351583e755a",
            programmeCode: "BCOM",
            programmeName: "Bachelor of Commerce",
            programmeEnrolmentStatus: "PROVISIONING",
            requestedAt: "2027-01-08T10:18:30Z",
            completedAt: null,
            failureReason:
              "Portal provisioning failed: Keycloak user is missing",
            retryCount: 0,
            lastRetryAt: null,
            lastRetryByUserId: null,
            lastRetryReason: null,
          },
        ]),
      });
    });
    await page.route(
      "**/api/student-records/conversions/*/retry",
      async (route) => {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            id: "2aa0bc99-d9c7-4557-abf1-c63bd4d5078c",
            status: "PROVISIONING",
            financeProvisioningStatus: "COMPLETED",
            portalProvisioningStatus: "PENDING",
            sourceApplicationId: "993954ff-f78b-4f96-914e-2d44447c92aa",
            sourceOfferId: "d676d377-1d8b-48cc-b91d-1c9d7dd3ebd4",
            studentId: "189e35bf-8fc5-4887-a81f-0bf18f8bf71a",
            studentNumber: "STU-2027-0000143",
            studentStatus: "PROVISIONING",
            programmeEnrolmentId: "0017a106-1355-4d62-b95f-c351583e755a",
            programmeCode: "BCOM",
            programmeName: "Bachelor of Commerce",
            programmeEnrolmentStatus: "PROVISIONING",
            requestedAt: "2027-01-08T10:18:30Z",
            completedAt: null,
            failureReason: null,
            retryCount: 1,
            lastRetryAt: "2027-01-08T10:25:00Z",
            lastRetryByUserId: "1d48dcc1-c07a-4d02-b7a1-492ea3529191",
            lastRetryReason:
              "Core Identity user was synchronised from Keycloak.",
          }),
        });
      },
    );

    await page.goto("/operations/student-conversions");
    await expect(page).toHaveURL(
      /\/realms\/emhare\/protocol\/openid-connect\/auth/,
      { timeout: 15_000 },
    );
    await expect(
      page.getByRole("heading", { name: /sign in to your account/i }),
    ).toBeVisible();
    await loginWithKeycloak(page, username);

    await expect(page).toHaveURL(/\/operations\/student-conversions$/);
    await expect(
      page.getByRole("heading", { name: "Accepted-offer conversions" }),
    ).toBeVisible();
    await expect(page.getByText("STU-2027-0000142")).toBeVisible();
    await expect(page.getByText("USD base ledger").first()).toBeVisible();
    await expect(page.getByText("STUDENT role").first()).toBeVisible();
    await expect(page.getByText("Manual intervention required")).toBeVisible();
    await expect(page.getByText("Keycloak user is missing")).toBeVisible();

    await page.getByRole("button", { name: "Retry provisioning" }).click();
    await page
      .getByLabel("Retry reason")
      .fill("Core Identity user was synchronised from Keycloak.");
    await page
      .getByRole("button", { name: "Retry provisioning" })
      .last()
      .click();
    await expect(
      page.getByText("Provisioning retry queued", { exact: true }),
    ).toBeVisible();
    await expect(
      page.locator("p").filter({ hasText: "1 recorded retry" }),
    ).toBeVisible();

    expect(consoleErrors).toEqual([]);
    expect(pageErrors).toEqual([]);
  });

  test("presents governed student registration as a two-stage ERP work queue", async ({
    page,
  }, testInfo) => {
    const username = `codex.registration.${testInfo.project.name.replace(/[^a-z0-9]+/gi, "-").toLowerCase()}@example.test`;
    await ensureSystemAdminUser(username);

    const consoleErrors: string[] = [];
    const pageErrors: string[] = [];
    page.on("console", (message) => {
      if (message.type() === "error") consoleErrors.push(message.text());
    });
    page.on("pageerror", (error) => pageErrors.push(error.message));

    const submittedRegistration = {
      id: "73ebff8d-a997-4f80-9522-d61719aba048",
      studentId: "b775245d-1a4c-4b84-b848-c4372ca592f2",
      studentNumber: "STU-2027-0000191",
      studentName: "Tariro Moyo",
      programmeEnrolmentId: "89673b5c-0ae1-4d70-8a37-f89407830f0e",
      programmeCode: "BACC",
      programmeName: "Bachelor of Accountancy",
      academicPeriodId: "59af72b3-6d51-4d8e-a467-d88bd688828e",
      academicPeriodCode: "2027-S1",
      academicPeriodName: "Semester 1",
      academicPeriodStartsOn: "2027-08-16",
      academicPeriodEndsOn: "2027-12-15",
      programmePeriodNumber: 1,
      registrationType: "NORMAL",
      status: "SUBMITTED",
      statusReason: "Student and approved curriculum load verified.",
      initiatedAt: "2027-07-01T08:00:00Z",
      submittedAt: "2027-07-01T08:05:00Z",
      academicApprovedAt: null,
      confirmedAt: null,
      version: 1,
      totalCredits: 24,
      modules: [
        {
          id: "17a3f610-b540-427f-9c65-61cd8432e4af",
          curriculumModuleId: "4a378e42-657a-43ee-a07c-31b1e27e4fd8",
          moduleId: "60d42269-c941-45a0-99ce-11155fda3a8f",
          moduleCode: "ACC101",
          moduleName: "Financial Accounting I",
          curriculumModuleType: "COMPULSORY",
          creditValue: 12,
          minimumMarkRequired: 50,
          selectionSource: "AUTO_COMPULSORY",
        },
        {
          id: "df7c53b1-5198-4e85-ae70-f48d4fa81187",
          curriculumModuleId: "1405a3c2-6861-4c30-9733-dc9496a47675",
          moduleId: "c1913f44-a5b0-4029-b782-803483b11b18",
          moduleCode: "ECO101",
          moduleName: "Economics I",
          curriculumModuleType: "ELECTIVE",
          creditValue: 12,
          minimumMarkRequired: 50,
          selectionSource: "STAFF_ELECTIVE",
        },
      ],
    };

    await page.route("**/api/student-records/registrations", async (route) => {
      if (route.request().method() === "GET") {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([submittedRegistration]),
        });
        return;
      }
      await route.fallback();
    });
    await page.route(
      "**/api/student-records/registrations/*/academic-approve",
      async (route) => {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            ...submittedRegistration,
            status: "ACADEMIC_APPROVED",
            statusReason: "Academic unit approved the Module load.",
            academicApprovedAt: "2027-07-01T08:10:00Z",
            version: 2,
          }),
        });
      },
    );
    await page.route("**/api/student-records/conversions", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: "[]",
      });
    });
    await page.route("**/api/academic/overview", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          academicUnitTypes: [],
          academicUnits: [],
          academicYears: [],
          academicPeriodTypes: [],
          academicPeriods: [],
          intakes: [],
          programmeLevels: [],
          programmeTypes: [],
          programmes: [],
          modules: [],
        }),
      });
    });

    await page.goto("/operations/student-registrations");
    await expect(page).toHaveURL(
      /\/realms\/emhare\/protocol\/openid-connect\/auth/,
      { timeout: 15_000 },
    );
    await loginWithKeycloak(page, username);

    await expect(page).toHaveURL(/\/operations\/student-registrations$/);
    await expect(
      page.getByRole("heading", { name: "Registration decisions" }),
    ).toBeVisible();
    await expect(
      page.getByText("Authoritative Module registration"),
    ).toBeVisible();
    await expect(page.getByText("STU-2027-0000191")).toBeVisible();
    await expect(page.getByText("ACC101 · 12 credits")).toBeVisible();
    await expect(page.getByText("ECO101 · 12 credits")).toBeVisible();
    await expect(page.getByText("Academic review").first()).toBeVisible();

    await page.getByRole("button", { name: "Academic approve" }).click();
    await page
      .getByLabel("Decision reason")
      .fill("Academic unit approved the Module load.");
    await page
      .getByRole("button", { name: "Record academic approval" })
      .click();
    await expect(
      page.getByText("Registration academic approved", { exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Registry confirm" }),
    ).toBeVisible();

    expect(consoleErrors).toEqual([]);
    expect(pageErrors).toEqual([]);
  });

  test("presents controlled assessment capture and immutable submission on desktop and mobile", async ({
    page,
  }, testInfo) => {
    const username = `codex.assessment.${testInfo.project.name.replace(/[^a-z0-9]+/gi, "-").toLowerCase()}@example.test`;
    await ensureSystemAdminUser(username);
    const consoleErrors: string[] = [];
    const pageErrors: string[] = [];
    page.on("console", (message) => {
      if (message.type() === "error") consoleErrors.push(message.text());
    });
    page.on("pageerror", (error) => pageErrors.push(error.message));

    const offeringId = "1f6c9bd5-854a-4a46-a109-d9ab80b61562";
    const componentId = "af9ee85c-fba4-4563-8df5-0440583dce64";
    const rosterEntryId = "109623d0-f0e6-43a3-a87a-b8f2a6450469";
    const markId = "121ff536-c9d8-42ab-8960-13ad87c587f5";
    let markStatus: "CAPTURED" | "SUBMITTED" | null = null;
    let markVersion = 0;
    let score: number | null = null;
    const offering = {
      id: offeringId,
      moduleId: "5551aa31-ef5e-4838-9ca8-581c2721dfc8",
      moduleCode: "ACC101",
      moduleName: "Financial Accounting I",
      academicPeriodId: "75a89940-0a68-4c28-bfa5-b5373714ed10",
      academicPeriodCode: "2027-S1",
      academicPeriodName: "Semester 1",
      assignedInstructorUserId: "bb68760f-f25d-41c5-986f-7702830fc1eb",
      status: "ACTIVE",
      version: 1,
      rosterCount: 1,
      schemes: [
        {
          id: "71d64a95-9308-4c0c-80e2-32e8cac041a4",
          schemeVersion: 1,
          name: "Approved scheme",
          status: "APPROVED",
          approvalReason: "Board approval",
          approvedByUserId: "bb68760f-f25d-41c5-986f-7702830fc1eb",
          approvedAt: "2027-08-01T08:00:00Z",
          version: 1,
          components: [
            {
              id: componentId,
              code: "CWK",
              name: "Coursework",
              componentType: "COURSEWORK",
              weightPercent: 100,
              maximumMark: 100,
              captureOpensAt: "2026-01-01T00:00:00Z",
              captureClosesAt: "2028-01-01T00:00:00Z",
              sortOrder: 1,
            },
          ],
        },
      ],
    };
    await page.route("**/api/assessment-results/offerings", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([offering]),
      }),
    );
    await page.route(
      `**/api/assessment-results/components/${componentId}/roster`,
      (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([
            {
              rosterEntryId,
              studentId: "54dc989b-fe73-4db5-abeb-532648eb2687",
              studentNumber: "STU-2027-0000214",
              studentName: "Tariro Moyo",
              componentId,
              componentCode: "CWK",
              markId: markStatus ? markId : null,
              revisionNumber: markStatus ? 1 : null,
              score,
              status: markStatus,
              markVersion,
            },
          ]),
        }),
    );
    await page.route(
      `**/api/assessment-results/components/${componentId}/marks`,
      async (route) => {
        score = (await route.request().postDataJSON()).marks[0].score;
        markStatus = "CAPTURED";
        markVersion = 0;
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([
            {
              id: markId,
              componentId,
              rosterEntryId,
              revisionNumber: 1,
              score,
              status: markStatus,
              captureMethod: "MANUAL",
              version: markVersion,
            },
          ]),
        });
      },
    );
    await page.route(
      `**/api/assessment-results/marks/${markId}/submit*`,
      (route) => {
        markStatus = "SUBMITTED";
        markVersion = 1;
        return route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            id: markId,
            componentId,
            rosterEntryId,
            revisionNumber: 1,
            score,
            status: markStatus,
            captureMethod: "MANUAL",
            version: markVersion,
          }),
        });
      },
    );
    await page.route(
      `**/api/assessment-results/offerings/${offeringId}/calculations`,
      (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            id: "f58d3518-0d6c-42d4-b865-4cb839059ef8",
            offeringId,
            schemeId: offering.schemes[0].id,
            rosterCount: 1,
            completeResultCount: 1,
            incompleteResultCount: 0,
            status: "COMPLETED",
            initiatedAt: "2027-08-20T10:00:00Z",
            outcomes: [
              {
                rosterEntryId,
                studentNumber: "STU-2027-0000214",
                weightedTotal: 74,
                complete: true,
                missingComponentCodes: null,
              },
            ],
          }),
        }),
    );

    await page.goto("/operations/assessment-capture");
    await expect(page).toHaveURL(
      /\/realms\/emhare\/protocol\/openid-connect\/auth/,
      { timeout: 15_000 },
    );
    await loginWithKeycloak(page, username);
    await expect(page).toHaveURL(/\/operations\/assessment-capture$/);
    await expect(page.getByText("Controlled capture workspace")).toBeVisible();
    await page.getByLabel("Module offering").click();
    await page
      .getByRole("option", { name: /ACC101.*Financial Accounting I.*2027-S1/ })
      .click();
    await page.getByLabel("Assessment component").click();
    await page.getByRole("option", { name: /CWK.*Coursework.*100%/ }).click();
    await expect(page.getByText("STU-2027-0000214")).toBeVisible();
    await page.getByRole("spinbutton").fill("74");
    await page.getByRole("button", { name: "Save captured marks" }).click();
    await expect(
      page.getByText("Marks saved as captured", { exact: true }),
    ).toBeVisible();
    await page.getByRole("button", { name: "Submit" }).click();
    await page.getByRole("button", { name: "Submit mark" }).click();
    await expect(
      page.getByText("Mark submitted", { exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Request amendment" }),
    ).toBeVisible();
    await page
      .getByRole("button", { name: "Run aggregate calculation" })
      .click();
    await expect(page.getByText("74%")).toBeVisible();
    expect(consoleErrors).toEqual([]);
    expect(pageErrors).toEqual([]);
  });

  test("presents governed result publication as a responsive evidence-backed work queue", async ({
    page,
  }, testInfo) => {
    const username = `codex.results.${testInfo.project.name.replace(/[^a-z0-9]+/gi, "-").toLowerCase()}@example.test`;
    await ensureSystemAdminUser(username);

    const consoleErrors: string[] = [];
    const pageErrors: string[] = [];
    page.on("console", (message) => {
      if (message.type() === "error") consoleErrors.push(message.text());
    });
    page.on("pageerror", (error) => pageErrors.push(error.message));

    const offeringId = "2dcd559e-6cb5-467f-9785-24e635e36370";
    const calculationRunId = "032e96df-0d54-40df-9bc6-213fc92a71d2";
    const gradingSchemeId = "ea820bcb-8ae6-4896-bbcb-56d827ae121a";
    let batchStatus: "DRAFT" | "SUBMITTED" = "DRAFT";
    let batchCreated = false;

    const calculationRuns = [
      {
        id: calculationRunId,
        offeringId,
        schemeId: "52584651-2c24-48d4-8801-9254c716cc0b",
        rosterCount: 1,
        completeResultCount: 1,
        incompleteResultCount: 0,
        status: "COMPLETED",
        initiatedAt: "2027-08-20T10:00:00Z",
        publicationEvidenceAvailable: true,
        outcomes: [
          {
            rosterEntryId: "dff0b5d0-c127-4edf-beac-ff2b9e50ff3d",
            studentNumber: "STU-2027-0000214",
            weightedTotal: 74,
            complete: true,
            missingComponentCodes: null,
          },
        ],
      },
    ];
    const offerings = [
      {
        id: offeringId,
        moduleId: "02343208-947d-4b6f-aacf-d747f3e4bcb9",
        moduleCode: "ACC101",
        moduleName: "Financial Accounting I",
        academicPeriodId: "b4508d8e-33cd-4820-93d7-a5d66e682f5c",
        academicPeriodCode: "2027-S1",
        academicPeriodName: "Semester 1",
        assignedInstructorUserId: "b753690a-f1e9-43fb-badb-1ca15a23f28a",
        status: "ACTIVE",
        version: 1,
        rosterCount: 1,
        schemes: [],
      },
    ];
    const gradingSchemes = [
      {
        id: gradingSchemeId,
        code: "STANDARD",
        name: "Institutional grading scheme",
        schemeVersion: 1,
        status: "APPROVED",
        version: 1,
        bands: [
          {
            id: "4e027c46-67a8-427b-afc9-608d0bd83833",
            minimumMark: 0,
            maximumMark: 49.99,
            grade: "F",
            remark: "Fail",
            passing: false,
          },
          {
            id: "8e02adb4-3207-4a91-91cf-13c60b3e0a83",
            minimumMark: 50,
            maximumMark: 100,
            grade: "P",
            remark: "Pass",
            passing: true,
          },
        ],
      },
    ];
    const resultBatch = () => ({
      id: "83cb673c-3a7e-4de3-bfe6-87ef5e99b507",
      calculationRunId,
      batchNumber: "RES-2027-S1-ACC101-001",
      moduleCode: "ACC101",
      moduleName: "Financial Accounting I",
      academicPeriodCode: "2027-S1",
      status: batchStatus,
      statusReason:
        batchStatus === "DRAFT"
          ? "Materialised from complete calculation evidence."
          : "Department board verified the calculation evidence.",
      version: batchStatus === "DRAFT" ? 0 : 1,
      resultCount: 1,
      submittedAt: batchStatus === "SUBMITTED" ? "2027-08-21T10:00:00Z" : null,
      moderatedAt: null,
      approvedAt: null,
      publishedAt: null,
      results: [
        {
          id: "d672bc9c-3da8-435d-954e-98d512f24b99",
          studentNumber: "STU-2027-0000214",
          courseworkMark: 30,
          examinationMark: 44,
          finalMark: 74,
          grade: "P",
          remark: "Pass",
          status: "PASS",
        },
      ],
    });

    await page.route("**/api/assessment-results/calculations", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(calculationRuns),
      }),
    );
    await page.route("**/api/assessment-results/offerings", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(offerings),
      }),
    );
    await page.route("**/api/results/grading-schemes", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(gradingSchemes),
      }),
    );
    await page.route("**/api/results/batches**", async (route) => {
      const request = route.request();
      if (request.method() === "GET") {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(batchCreated ? [resultBatch()] : []),
        });
        return;
      }
      if (request.url().endsWith("/api/results/batches")) {
        expect(await request.postDataJSON()).toEqual({
          calculationRunId,
          gradingSchemeId,
        });
        batchCreated = true;
        await route.fulfill({
          status: 201,
          contentType: "application/json",
          body: JSON.stringify(resultBatch()),
        });
        return;
      }
      if (request.url().endsWith("/submit")) {
        const decision = await request.postDataJSON();
        expect(decision.reason).toBe(
          "Department board verified the calculation evidence.",
        );
        expect(decision.expectedVersion).toBe(0);
        batchStatus = "SUBMITTED";
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(resultBatch()),
        });
        return;
      }
      await route.abort("blockedbyclient");
    });

    await page.goto("/operations/result-batches");
    await expect(page).toHaveURL(
      /\/realms\/emhare\/protocol\/openid-connect\/auth/,
      { timeout: 15_000 },
    );
    await loginWithKeycloak(page, username);

    await expect(page).toHaveURL(/\/operations\/result-batches$/);
    await expect(page.getByText("Four-eyes result release")).toBeVisible();
    await expect(page.getByText("No result batches")).toBeVisible();
    await page.getByRole("button", { name: "Create result batch" }).click();
    await page.getByLabel("Completed calculation").click();
    await page
      .getByRole("option", { name: /ACC101.*2027-S1.*1 complete/ })
      .click();
    await page.getByLabel("Approved grading scheme").click();
    await page
      .getByRole("option", {
        name: /STANDARD v1.*Institutional grading scheme/,
      })
      .click();
    await page.getByRole("button", { name: "Create draft batch" }).click();

    await expect(
      page.getByText("Draft result batch created", { exact: true }),
    ).toBeVisible();
    await expect(page.getByText("RES-2027-S1-ACC101-001")).toBeVisible();
    await expect(page.getByText("STU-2027-0000214")).toBeVisible();
    await expect(
      page.getByRole("cell", { name: "74", exact: true }),
    ).toBeVisible();
    await page.getByRole("button", { name: "Submit for moderation" }).click();
    await page
      .getByLabel("Decision reason")
      .fill("Department board verified the calculation evidence.");
    await page
      .getByRole("button", { name: "Submit for moderation" })
      .last()
      .click();

    await expect(
      page.getByText("Result batch submitted", { exact: true }),
    ).toBeVisible();
    await expect(page.getByText("SUBMITTED", { exact: true })).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Record moderation" }),
    ).toBeVisible();
    await page.screenshot({
      path: testInfo.outputPath("result-board-submitted.png"),
      fullPage: true,
    });
    expect(consoleErrors).toEqual([]);
    expect(pageErrors).toEqual([]);
  });

  test("presents append-only published result corrections on desktop and mobile", async ({
    page,
  }, testInfo) => {
    const username = `codex.result-corrections.${testInfo.project.name.replace(/[^a-z0-9]+/gi, "-").toLowerCase()}@example.test`;
    await ensureSystemAdminUser(username);

    const consoleErrors: string[] = [];
    const pageErrors: string[] = [];
    page.on("console", (message) => {
      if (message.type() === "error") consoleErrors.push(message.text());
    });
    page.on("pageerror", (error) => pageErrors.push(error.message));

    const publishedResultId = "5376c905-1d51-4d4a-848a-663879e53d3b";
    const replacementModuleResultId = "bf571430-f140-4cbe-8bab-ee89c8ba477c";
    let currentOperatorUserId = "";
    let amendmentRequested = false;
    const publishedResult = {
      id: publishedResultId,
      resultBatchId: "d180d79b-b766-4d12-865d-9aa0b5925ab4",
      moduleResultId: "61d7f882-d0bc-4cd5-a244-5293838e5e4a",
      studentId: "3792d049-d13f-4508-952d-c21d354582e4",
      studentNumber: "STU-2027-0000214",
      moduleId: "6294e915-83f1-429c-9458-78279879e07a",
      moduleCode: "ACC101",
      moduleName: "Financial Accounting I",
      academicPeriodId: "65febc75-4b4c-4a47-95a6-da561c173e20",
      academicPeriodCode: "2027-S1",
      finalMark: 68,
      grade: "C",
      remark: "Credit",
      publicationVersion: 1,
      supersedesPublishedResultId: null,
      resultAmendmentId: null,
      publishedByUserId: "3ed57943-3859-4f6b-b0ac-08ba308010d3",
      publishedAt: "2027-08-21T12:00:00Z",
    };
    const correctionSource = {
      moduleResultId: replacementModuleResultId,
      resultBatchId: "890f2a66-47b1-490d-af94-bdc24e7252ba",
      batchNumber: "RES-2027-S1-ACC101-CORRECTION",
      courseworkMark: 30,
      examinationMark: 42,
      finalMark: 72,
      grade: "D",
      remark: "Distinction",
      approvedAt: "2027-08-22T10:00:00Z",
    };
    const requestedAmendment = () => ({
      id: "4b075c34-991b-45e8-b445-83b65b60dd44",
      amendmentNumber: "AMEND-2027-000001",
      originalPublishedResultId: publishedResultId,
      originalPublicationVersion: 1,
      replacementResultBatchId: correctionSource.resultBatchId,
      replacementModuleResultId,
      studentNumber: publishedResult.studentNumber,
      moduleCode: publishedResult.moduleCode,
      moduleName: publishedResult.moduleName,
      academicPeriodCode: publishedResult.academicPeriodCode,
      originalFinalMark: 68,
      originalGrade: "C",
      originalRemark: "Credit",
      proposedFinalMark: 72,
      proposedGrade: "D",
      proposedRemark: "Distinction",
      requestReason:
        "Approved mark amendment and recalculation changed the final result.",
      status: "REQUESTED",
      version: 0,
      requestedByUserId: currentOperatorUserId,
      requestedAt: "2027-08-22T11:00:00Z",
      reviewedByUserId: null,
      reviewedAt: null,
      reviewReason: null,
      approvedByUserId: null,
      approvedAt: null,
      approvalReason: null,
      appliedByUserId: null,
      appliedAt: null,
      rejectedByUserId: null,
      rejectedAt: null,
      rejectionReason: null,
    });

    await page.route("**/api/results/published-results?**", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          content: [publishedResult],
          page: 0,
          size: 25,
          totalElements: 1,
          totalPages: 1,
        }),
      }),
    );
    await page.route(
      `**/api/results/published-results/${publishedResultId}/correction-sources`,
      (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([correctionSource]),
        }),
    );
    await page.route(
      "**/api/results/published-result-amendments",
      async (route) => {
        if (route.request().method() === "GET") {
          await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify(
              amendmentRequested ? [requestedAmendment()] : [],
            ),
          });
          return;
        }
        expect(await route.request().postDataJSON()).toEqual({
          originalPublishedResultId: publishedResultId,
          replacementModuleResultId,
          reason:
            "Approved mark amendment and recalculation changed the final result.",
        });
        amendmentRequested = true;
        await route.fulfill({
          status: 201,
          contentType: "application/json",
          body: JSON.stringify(requestedAmendment()),
        });
      },
    );

    await page.goto("/operations/result-corrections");
    await expect(page).toHaveURL(
      /\/realms\/emhare\/protocol\/openid-connect\/auth/,
      { timeout: 15_000 },
    );
    const currentUserResponse = page.waitForResponse(
      (response) => response.url().includes("/api/core/me") && response.ok(),
    );
    await loginWithKeycloak(page, username);
    currentOperatorUserId = (await (await currentUserResponse).json()).user.id;

    await expect(page).toHaveURL(/\/operations\/result-corrections$/);
    await expect(
      page.getByText("Append-only correction control"),
    ).toBeVisible();
    await expect(page.getByText("STU-2027-0000214")).toBeVisible();
    await expect(page.getByText("68% · C · Credit")).toBeVisible();
    await page.getByRole("button", { name: "Request correction" }).click();
    await expect(
      page.getByText("The published result will not be edited"),
    ).toBeVisible();
    await page.getByLabel("Approved replacement result batch").click();
    await page
      .getByRole("option", {
        name: /RES-2027-S1-ACC101-CORRECTION.*72%.*Distinction/,
      })
      .click();
    await page
      .getByLabel("Correction reason")
      .fill(
        "Approved mark amendment and recalculation changed the final result.",
      );
    await page
      .getByRole("button", { name: "Submit correction request" })
      .click();

    await expect(
      page.getByText("Result correction requested", { exact: true }),
    ).toBeVisible();
    await expect(page.getByText("AMEND-2027-000001")).toBeVisible();
    await expect(page.getByText("Permanent original")).toBeVisible();
    await expect(
      page.getByText("Approved replacement evidence", { exact: true }),
    ).toBeVisible();
    await expect(page.getByText("72% · D", { exact: true })).toBeVisible();
    await expect(
      page.getByText(
        "Handoff required: the requester cannot review this correction.",
      ),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Record independent review" }),
    ).toHaveCount(0);
    await page.screenshot({
      path: testInfo.outputPath("published-result-correction-request.png"),
      fullPage: true,
    });

    expect(consoleErrors).toEqual([]);
    expect(pageErrors).toEqual([]);
  });

  test("presents governed progression rules and decisions on desktop and mobile", async ({
    page,
  }, testInfo) => {
    const username = `codex.progression.${testInfo.project.name.replace(/[^a-z0-9]+/gi, "-").toLowerCase()}@example.test`;
    await ensureSystemAdminUser(username);

    const consoleErrors: string[] = [];
    const pageErrors: string[] = [];
    page.on("console", (message) => {
      if (message.type() === "error") consoleErrors.push(message.text());
    });
    page.on("pageerror", (error) => pageErrors.push(error.message));

    const rosterImportId = "141fdc06-399e-4417-9085-56f0ff2b314b";
    const programmeId = "07309408-3b04-43e5-8178-f3a39fa6df0d";
    const programmeVersionId = "e450343a-55aa-4c5a-939e-84c79ad2943c";
    const ruleSetId = "8b5aa558-e267-4900-848c-911f2a9c4ec7";
    let currentOperatorUserId = "";
    let decisionCalculated = false;
    const roster = {
      id: rosterImportId,
      studentId: "e4408bcc-3e51-47f1-a20c-a303fb46af1f",
      studentNumber: "STU-2027-0000214",
      programmeId,
      programmeVersionId,
      academicPeriodCode: "2027-S1",
      programmePeriodNumber: 1,
      eligibleModules: 2,
      publishedModules: 2,
      readyForProgression: true,
    };
    const ruleSet = {
      id: ruleSetId,
      ruleCode: "BACC-P1",
      ruleName: "Bachelor of Accountancy period 1 progression",
      programmeId,
      programmeVersionId,
      programmePeriodNumber: 1,
      ruleVersion: 3,
      status: "APPROVED",
      version: 1,
      approvedByUserId: "643a87a5-91ae-41b3-8b3e-1a0dd1bf58f5",
      approvedAt: "2027-08-25T09:00:00Z",
      outcomes: [
        {
          id: "0ecb1119-c4b1-4142-814d-930ecb30cd9d",
          priority: 1,
          decisionCode: "PROCEED",
          decisionLabel: "Proceed to programme period 2",
          minimumWeightedAverage: 50,
          minimumPassedCredits: 24,
          maximumFailedCredits: 0,
          maximumFailedModules: 0,
          requireAllCompulsoryPassed: true,
          nextProgrammePeriodNumber: 2,
          fallbackOutcome: false,
        },
        {
          id: "40be3ef6-c03f-465f-a6ee-67055cb6f49f",
          priority: 2,
          decisionCode: "REPEAT",
          decisionLabel: "Repeat programme period 1",
          minimumWeightedAverage: null,
          minimumPassedCredits: null,
          maximumFailedCredits: null,
          maximumFailedModules: null,
          requireAllCompulsoryPassed: false,
          nextProgrammePeriodNumber: 1,
          fallbackOutcome: true,
        },
      ],
    };
    const decision = () => ({
      id: "ce3de406-36a8-40e7-aa96-99a9bb24c649",
      decisionNumber: "PRG-2027-S1-STU-2027-0000214-V1-001",
      decisionVersion: 1,
      supersedesDecisionId: null,
      progressionRuleSetId: ruleSetId,
      progressionRuleCode: "BACC-P1",
      registrationRosterImportId: rosterImportId,
      studentId: roster.studentId,
      studentNumber: roster.studentNumber,
      programmeId,
      programmeVersionId,
      academicPeriodCode: "2027-S1",
      programmePeriodNumber: 1,
      decisionCode: "PROCEED",
      decisionLabel: "Proceed to programme period 2",
      nextProgrammePeriodNumber: 2,
      attemptedCredits: 24,
      passedCredits: 24,
      failedCredits: 0,
      failedModules: 0,
      failedCompulsoryModules: 0,
      weightedAverage: 72,
      status: "CALCULATED",
      statusReason:
        "Calculated from complete current published Module results.",
      version: 0,
      calculatedByUserId: currentOperatorUserId,
      calculatedAt: "2027-08-25T10:00:00Z",
      reviewedByUserId: null,
      reviewedAt: null,
      approvedByUserId: null,
      approvedAt: null,
      publishedByUserId: null,
      publishedAt: null,
      results: [
        {
          publishedResultId: "fc5ad2c1-8e0a-4fe7-83d7-ea39944f25b0",
          moduleCode: "ACC101",
          moduleName: "Financial Accounting I",
          curriculumModuleType: "COMPULSORY",
          creditValue: 12,
          finalMark: 68,
          grade: "C",
          remark: "Credit",
          passing: true,
          publicationVersion: 1,
        },
        {
          publishedResultId: "c2d73d2d-8822-4c6c-9fe4-9db79e5eaad9",
          moduleCode: "ACC102",
          moduleName: "Financial Accounting II",
          curriculumModuleType: "COMPULSORY",
          creditValue: 12,
          finalMark: 76,
          grade: "D",
          remark: "Distinction",
          passing: true,
          publicationVersion: 2,
        },
      ],
    });

    await page.route("**/api/results/progression/rosters", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([roster]),
      }),
    );
    await page.route("**/api/results/progression/rule-sets", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([ruleSet]),
      }),
    );
    await page.route("**/api/results/progression/decisions", async (route) => {
      if (route.request().method() === "GET") {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(decisionCalculated ? [decision()] : []),
        });
        return;
      }
      expect(await route.request().postDataJSON()).toEqual({
        registrationRosterImportId: rosterImportId,
        progressionRuleSetId: ruleSetId,
      });
      decisionCalculated = true;
      await route.fulfill({
        status: 201,
        contentType: "application/json",
        body: JSON.stringify(decision()),
      });
    });

    await page.goto("/operations/progression-decisions");
    await expect(page).toHaveURL(
      /\/realms\/emhare\/protocol\/openid-connect\/auth/,
      { timeout: 15_000 },
    );
    const currentUserResponse = page.waitForResponse(
      (response) => response.url().includes("/api/core/me") && response.ok(),
    );
    await loginWithKeycloak(page, username);
    currentOperatorUserId = (await (await currentUserResponse).json()).user.id;

    await expect(page).toHaveURL(/\/operations\/progression-decisions$/);
    await expect(
      page.getByText("Evidence-bound academic standing"),
    ).toBeVisible();
    await page.getByLabel("Complete published result set").click();
    await page
      .getByRole("option", {
        name: /STU-2027-0000214.*2027-S1.*2\/2 published/,
      })
      .click();
    await page.getByLabel("Applicable approved rule").click();
    await page
      .getByRole("option", { name: /BACC-P1 v3.*Bachelor of Accountancy/ })
      .click();
    await page.getByRole("button", { name: "Calculate decision" }).click();
    await page
      .getByRole("button", { name: "Calculate decision" })
      .last()
      .click();

    await expect(
      page.getByText("Progression decision calculated", { exact: true }),
    ).toBeVisible();
    await expect(
      page.getByText("PRG-2027-S1-STU-2027-0000214-V1-001"),
    ).toBeVisible();
    await expect(
      page.getByText("STU-2027-0000214 · Proceed to programme period 2"),
    ).toBeVisible();
    await expect(page.getByText("72%", { exact: true })).toBeVisible();
    await expect(page.getByText("ACC101", { exact: true })).toBeVisible();
    await expect(page.getByText("ACC102", { exact: true })).toBeVisible();
    await expect(
      page.getByText(
        "Handoff required: the calculator cannot review this decision.",
      ),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Record independent review" }),
    ).toHaveCount(0);
    await page.screenshot({
      path: testInfo.outputPath("progression-decision-calculated.png"),
      fullPage: true,
    });

    await page.goto("/operations/progression-rules");
    await expect(
      page.getByText("Versioned, programme-owned progression policy"),
    ).toBeVisible();
    await expect(page.getByText("BACC-P1 · version 3")).toBeVisible();
    await expect(
      page.getByText("1. Proceed to programme period 2", { exact: true }),
    ).toBeVisible();
    await expect(
      page.getByText(
        /average ≥ 50%.*failed credits ≤ 0.*all compulsory Modules passed/,
      ),
    ).toBeVisible();
    await page.screenshot({
      path: testInfo.outputPath("progression-rules-approved.png"),
      fullPage: true,
    });

    expect(consoleErrors).toEqual([]);
    expect(pageErrors).toEqual([]);
  });

  test("presents the governed official document register on desktop and mobile", async ({
    page,
  }, testInfo) => {
    const username = `codex.documents.${testInfo.project.name.replace(/[^a-z0-9]+/gi, "-").toLowerCase()}@example.test`;
    await ensureSystemAdminUser(username);

    const consoleErrors: string[] = [];
    const pageErrors: string[] = [];
    page.on("console", (message) => {
      if (message.type() === "error") consoleErrors.push(message.text());
    });
    page.on("pageerror", (error) => pageErrors.push(error.message));

    let failedDocumentStatus = "FAILED";
    const documents = () => [
      {
        id: "c17cac20-89d0-4bc6-b515-d527532182f2",
        documentNumber: "RSLIP-PRG-2027-S1-STU-2027-0000214-V1",
        documentType: "RESULT_SLIP",
        studentNumber: "STU-2027-0000214",
        academicPeriodCode: "2027-S1",
        decisionCode: "PROCEED",
        decisionLabel: "Proceed to programme period 2",
        status: "STORED",
        templateCode: "OFFICIAL-RESULT-SLIP",
        templateVersion: 1,
        checksumSha256:
          "2c1803537a0fa5aee7838c03fc04c26ba79f141cd21948fe533b6a53cf9bb7f2",
        sizeBytes: 28142,
        pageCount: 2,
        requestedAt: "2027-12-20T10:00:00Z",
        generatedAt: "2027-12-20T10:00:02Z",
        generationAttemptCount: 1,
        retryAvailable: false,
        lastFailureReason: null,
        version: 2,
      },
      {
        id: "85e44686-caf8-4d75-81f0-1e669b4c519f",
        documentNumber: "RSLIP-PRG-2027-S1-STU-2027-0000215-V1",
        documentType: "RESULT_SLIP",
        studentNumber: "STU-2027-0000215",
        academicPeriodCode: "2027-S1",
        decisionCode: "PROCEED_WITH_CARRY",
        decisionLabel: "Proceed carrying one Module",
        status: failedDocumentStatus,
        templateCode: "OFFICIAL-RESULT-SLIP",
        templateVersion: 1,
        checksumSha256: null,
        sizeBytes: null,
        pageCount: null,
        requestedAt: "2027-12-20T10:04:00Z",
        generatedAt: null,
        generationAttemptCount: 2,
        retryAvailable: failedDocumentStatus === "FAILED",
        lastFailureReason:
          failedDocumentStatus === "FAILED"
            ? "Object storage was unavailable."
            : null,
        version: failedDocumentStatus === "FAILED" ? 2 : 3,
      },
    ];

    await page.route("**/api/documents", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(documents()),
      }),
    );
    await page.route(
      "**/api/documents/85e44686-caf8-4d75-81f0-1e669b4c519f/retry",
      async (route) => {
        expect(await route.request().postDataJSON()).toEqual({
          expectedVersion: 2,
        });
        failedDocumentStatus = "REQUESTED";
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(documents()[1]),
        });
      },
    );

    await page.goto("/operations/documents");
    await expect(page).toHaveURL(
      /\/realms\/emhare\/protocol\/openid-connect\/auth/,
      { timeout: 15_000 },
    );
    await loginWithKeycloak(page, username);

    await expect(page).toHaveURL(/\/operations\/documents$/);
    await expect(
      page.getByText("Stored, verifiable academic records"),
    ).toBeVisible();
    await expect(
      page.getByText("RSLIP-PRG-2027-S1-STU-2027-0000214-V1"),
    ).toBeVisible();
    await expect(
      page.getByText(
        "SHA-256 2c1803537a0fa5aee7838c03fc04c26ba79f141cd21948fe533b6a53cf9bb7f2",
      ),
    ).toBeVisible();
    await expect(page.getByRole("button", { name: "Open PDF" })).toBeVisible();
    await expect(
      page.getByText("Object storage was unavailable."),
    ).toBeVisible();

    await page.getByRole("button", { name: "Retry generation" }).click();
    await expect(
      page.getByText("The same immutable progression evidence"),
    ).toBeVisible();
    await page.getByRole("button", { name: "Queue retry" }).click();
    await expect(
      page.getByText("Document retry queued", { exact: true }),
    ).toBeVisible();
    await expect(page.getByText("Object storage was unavailable.")).toHaveCount(
      0,
    );
    await page.screenshot({
      path: testInfo.outputPath("official-document-register.png"),
      fullPage: true,
    });

    expect(consoleErrors).toEqual([]);
    expect(pageErrors).toEqual([]);
  });

  test("renders the admissions verification workspace inside the dashboard panel", async ({
    page,
  }, testInfo) => {
    const username = `codex.admissions-verification.${testInfo.project.name.replace(/[^a-z0-9]+/gi, "-").toLowerCase()}@example.test`;
    await ensureSystemAdminUser(username);

    const consoleErrors: string[] = [];
    const pageErrors: string[] = [];
    page.on("console", (message) => {
      if (message.type() === "error") consoleErrors.push(message.text());
    });
    page.on("pageerror", (error) => pageErrors.push(error.message));

    await page.route("**/api/admissions/verification-queue", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          applicationSections: [],
          qualifications: [],
          documents: [],
        }),
      }),
    );

    await page.goto("/operations/admissions-verification");
    await expect(page).toHaveURL(
      /\/realms\/emhare\/protocol\/openid-connect\/auth/,
      { timeout: 15_000 },
    );
    await loginWithKeycloak(page, username);

    await expect(page).toHaveURL(/\/operations\/admissions-verification$/);
    await expect(
      page.getByText("Independent verification workspace", { exact: true }),
    ).toBeVisible();
    await expect(page.getByRole("button", { name: "Refresh" })).toBeVisible();
    await expect(
      page.getByText("No application sections awaiting review", {
        exact: true,
      }),
    ).toBeVisible();
    expect(consoleErrors).toEqual([]);
    expect(pageErrors).toEqual([]);
  });

  test("opens an admissions application as a full page with an inline document preview", async ({
    page,
  }, testInfo) => {
    const username = `codex.admissions-detail.${testInfo.project.name.replace(/[^a-z0-9]+/gi, "-").toLowerCase()}@example.test`;
    await ensureSystemAdminUser(username);

    const consoleErrors: string[] = [];
    const pageErrors: string[] = [];
    page.on("console", (message) => {
      if (message.type() === "error") consoleErrors.push(message.text());
    });
    page.on("pageerror", (error) => pageErrors.push(error.message));

    const applicationId = "2fe27a8a-af58-4a08-93d8-d816823bc1f9";
    const documentId = "6c33eb11-8a23-448c-819e-ec2aa4b4511f";
    const application = {
      id: applicationId,
      applicationNumber: "EMH-AUG2027-00000142",
      applicantNumber: "APP-00000142",
      applicantName: "Ruvimbo Moyo",
      intakeId: "c70d2895-5509-42e5-a834-e71a0e6acb5f",
      intakeCode: "AUG2027",
      applicationTypeId: "4e87961e-e390-4f36-99c9-cc9bb8a6ab76",
      applicationTypeName: "Undergraduate",
      status: "UNDER_REVIEW",
      paymentRequired: true,
      paymentClearanceStatus: "PAID",
      paymentWaiverReason: null,
      canSubmit: true,
      canEnterReview: false,
      payment: null,
      programmeChoices: [
        {
          id: "c48ab426-b75c-4d15-9d60-b47fe841f83b",
          programmeId: "07628054-560f-4e88-9c43-56ca8d9f91b6",
          programmeVersionId: "2018590c-7ee1-46dd-aed4-c91e594301dd",
          programmeCode: "HSC",
          programmeName: "Bachelor of Science Computer Science",
          awardName: "Bachelor of Science",
          owningAcademicUnitName: "Department of Computer Science",
          programmeVersionCode: "2027",
          choiceRank: 1,
          choiceStatus: "PENDING",
          evaluationSummary: null,
          decisionReason: null,
        },
      ],
    };
    const documentRequirement = {
      requirementCode: "IDENTITY_DOCUMENT",
      requirementName: "Identity document",
      required: true,
      state: "PENDING",
      applicationDocumentId: "0e537760-8b26-42a9-a152-76c471322aa0",
      documentId,
      fileName: "national-id.pdf",
      mimeType: "application/pdf",
      checksumSha256:
        "5ed7c46ae7d12e2c019a1aadcad8e3114518c0b88a0bf6a2de003aee82ebdd13",
      linkedAt: "2027-06-09T14:00:00Z",
      verifiedByUserId: null,
      verifiedAt: null,
      rejectionReason: null,
      documentVersion: 2,
      version: 1,
    };
    const requestedDocumentDispositions: string[] = [];

    await page.route("**/api/admissions/applications", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([application]),
      }),
    );
    await page.route(
      `**/api/admissions/applications/${applicationId}/workspace/staff`,
      (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            application,
            profile: {
              id: "7a5c50fb-47d4-4fde-a466-f30083ae7b0b",
              applicantNumber: application.applicantNumber,
              applicantCategoryCode: "LOCAL",
              firstName: "Ruvimbo",
              middleNames: null,
              lastName: "Moyo",
              primaryEmail: "ruvimbo.moyo@example.test",
              primaryPhone: "+263771000142",
              completenessPercentage: 100,
              missingRequiredFields: [],
              version: 3,
            },
            sections: [
              {
                id: "41113431-2025-4c4c-bfd0-a6a0d14a9df0",
                code: "PERSONAL_DETAILS",
                name: "Applicant details",
                required: true,
                repeatable: false,
                minimumRecords: 1,
                sortOrder: 10,
                status: "COMPLETE",
                completedAt: "2027-06-09T13:30:00Z",
                completionSummary: "All required applicant details captured.",
                version: 1,
              },
            ],
            nextOfKin: [],
            employmentHistory: [],
            referees: [],
            qualifications: [],
            documents: {
              applicationId,
              applicationNumber: application.applicationNumber,
              requiredDocumentsUploaded: true,
              requiredDocumentsVerified: false,
              missingRequirementCodes: [],
              pendingRequirementCodes: ["IDENTITY_DOCUMENT"],
              rejectedRequirementCodes: [],
              requirements: [documentRequirement],
            },
            readyForSubmission: true,
            missingRequirements: [],
            declarationAcceptedAt: "2027-06-09T14:05:00Z",
            declarationVersion: "2027.1",
          }),
        }),
    );
    await page.route(
      `**/api/documents/uploads/${documentId}/download**`,
      (route) => {
        requestedDocumentDispositions.push(
          new URL(route.request().url()).searchParams.get("disposition") ??
            "attachment",
        );
        return route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            documentId,
            originalFileName: "national-id.pdf",
            mimeType: "application/pdf",
            checksumSha256: documentRequirement.checksumSha256,
            downloadUrl: "data:application/pdf;base64,JVBERi0xLjQKJSVFT0YK",
            expiresAt: "2027-06-10T09:30:00Z",
          }),
        });
      },
    );

    await page.goto("/operations/admissions");
    await expect(page).toHaveURL(
      /\/realms\/emhare\/protocol\/openid-connect\/auth/,
      { timeout: 15_000 },
    );
    await loginWithKeycloak(page, username);

    await expect(
      page.getByRole("heading", { name: "Admissions review queue" }),
    ).toBeVisible();
    await page.goto(`/operations/admissions/${applicationId}`);

    await expect(page).toHaveURL(
      new RegExp(`/operations/admissions/${applicationId}$`),
    );
    await expect(
      page.getByRole("heading", { name: "Ruvimbo Moyo" }),
    ).toBeVisible();
    await expect(
      page.getByRole("heading", { name: "Personal and contact details" }),
    ).toBeVisible();
    await expect(
      page.getByRole("heading", { name: "Programme choices" }),
    ).toBeVisible();
    const documentsPanel = page.getByTestId("application-documents-panel");
    await expect(
      documentsPanel.getByText("national-id.pdf").first(),
    ).toBeVisible();
    await expect(
      documentsPanel.getByRole("button", { name: "Verify" }),
    ).toBeVisible();
    await expect(page.getByTestId("document-preview-frame")).toHaveAttribute(
      "src",
      /^data:application\/pdf/,
    );
    await expect(page.getByTestId("document-preview-frame")).toHaveCSS(
      "height",
      "256px",
    );

    await documentsPanel
      .getByRole("button", { name: "Expand preview" })
      .click();
    await expect(page.getByRole("dialog")).toBeVisible();
    await expect(
      page.getByRole("heading", { name: "national-id.pdf" }),
    ).toBeVisible();
    await expect(
      page.getByTestId("expanded-document-preview-frame"),
    ).toHaveAttribute("src", /^data:application\/pdf/);
    await page.waitForTimeout(300);
    await page.screenshot({
      path: testInfo.outputPath("expanded-document-preview.png"),
      fullPage: true,
    });
    await page
      .getByRole("button", { name: "Close expanded document preview" })
      .click();
    await expect(page.getByRole("dialog")).toBeHidden();
    await documentsPanel
      .getByRole("button", { name: "Download selected document" })
      .click();
    await expect
      .poll(() => requestedDocumentDispositions)
      .toContain("attachment");
    expect(requestedDocumentDispositions[0]).toBe("inline");
    await page.screenshot({
      path: testInfo.outputPath("inline-document-preview.png"),
      fullPage: true,
    });
    expect(consoleErrors).toEqual([]);
    expect(pageErrors).toEqual([]);
  });

  test("supports release, academic-unit recommendation, and Admissions approval as separate operations", async ({
    page,
  }, testInfo) => {
    const username = `codex.admissions-recommendation.${testInfo.project.name.replace(/[^a-z0-9]+/gi, "-").toLowerCase()}@example.test`;
    await ensureSystemAdminUser(username);
    const selectionRoundId = "1b000000-0000-0000-0000-000000000001";
    const assignmentId = "2b000000-0000-0000-0000-000000000002";
    let operation: "release" | "recommendation" | "decision" = "release";
    const recordedRequests: string[] = [];
    const assignment = (status: "OPEN" | "CLAIMED" | "RECOMMENDED") => ({
      id: assignmentId,
      selectionRoundId,
      applicationId: "3b000000-0000-0000-0000-000000000003",
      applicationNumber: "APP-2028-0001",
      programmeChoiceId: "4b000000-0000-0000-0000-000000000004",
      programmeCode: "BSC-CS",
      programmeName: "Computer Science",
      choiceRank: 1,
      owningAcademicUnitId: "5b000000-0000-0000-0000-000000000005",
      owningAcademicUnitCode: "COMP",
      owningAcademicUnitName: "School of Computing",
      recommendationAcademicUnitId: "6b000000-0000-0000-0000-000000000006",
      recommendationAcademicUnitCode: "SCI",
      recommendationAcademicUnitName: "College of Science",
      hierarchyPathJson: "[]",
      status,
      releaseAttempt: 1,
      releasedByUserId: "7b000000-0000-0000-0000-000000000007",
      releasedAt: "2028-01-10T08:00:00Z",
      dueAt: "2028-01-12T16:00:00Z",
      claimedByUserId:
        status === "OPEN" ? null : "8b000000-0000-0000-0000-000000000008",
      claimedAt: status === "OPEN" ? null : "2028-01-10T09:00:00Z",
      version: status === "OPEN" ? 0 : 1,
      latestRecommendation:
        status === "RECOMMENDED"
          ? {
              id: "9b000000-0000-0000-0000-000000000009",
              recommendationSequence: 1,
              recommendation: "SELECT",
              rankPosition: 1,
              quotaTypeCode: "MERIT",
              reason: "Meets the academic requirements and merit threshold.",
              recommendedByUserId: "8b000000-0000-0000-0000-000000000008",
              recommendedAt: "2028-01-10T10:00:00Z",
              reviewStatus: "PENDING",
              reviewedByUserId: null,
              reviewedAt: null,
              reviewReason: null,
              finalDecision: null,
            }
          : null,
    });

    await page.route("**/api/admissions/selection-rounds", (route) =>
      route.fulfill({
        json: [
          {
            id: selectionRoundId,
            intakeId: "ab000000-0000-0000-0000-000000000010",
            intakeCode: "AUG-2028",
            code: "2028-R1",
            name: "First merit selection",
            status: "OPEN",
            openedAt: "2028-01-09T08:00:00Z",
            approvedAt: null,
            closedAt: null,
            version: 1,
          },
        ],
      }),
    );
    await page.route("**/api/admissions/academic-reviews**", async (route) => {
      const request = route.request();
      const pathname = new URL(request.url()).pathname;
      if (request.method() === "POST") recordedRequests.push(pathname);
      if (pathname.endsWith("/release-preview"))
        return route.fulfill({
          json: {
            totalApplicants: 1,
            totalEligibleApplicants: 1,
            programmes: [
              {
                programmeId: "bb000000-0000-0000-0000-000000000011",
                programmeCode: "BSC-CS",
                programmeName: "Computer Science",
                owningAcademicUnitId: "5b000000-0000-0000-0000-000000000005",
                owningAcademicUnitName: "School of Computing",
                applicantCount: 1,
                eligibleApplicantCount: 1,
              },
            ],
            academicUnits: [
              {
                academicUnitId: "6b000000-0000-0000-0000-000000000006",
                academicUnitTypeCode: "COLLEGE",
                academicUnitCode: "SCI",
                academicUnitName: "College of Science",
                applicantCount: 1,
                eligibleApplicantCount: 1,
              },
            ],
          },
        });
      if (pathname.endsWith("/releases"))
        return route.fulfill({ json: [assignment("OPEN")] });
      if (pathname.endsWith("/recommendations"))
        return route.fulfill({ json: assignment("RECOMMENDED") });
      if (pathname.endsWith("/review"))
        return route.fulfill({
          json: { ...assignment("RECOMMENDED"), status: "COMPLETED" },
        });
      if (pathname.endsWith("/mine"))
        return route.fulfill({ json: [assignment("CLAIMED")] });
      return route.fulfill({
        json: [assignment(operation === "decision" ? "RECOMMENDED" : "OPEN")],
      });
    });

    await page.goto("/operations/admissions-academic-release");
    await expect(page).toHaveURL(
      /\/realms\/emhare\/protocol\/openid-connect\/auth/,
      { timeout: 15_000 },
    );
    await loginWithKeycloak(page, username);
    await expect(
      page.getByRole("navigation", { name: "Admissions workflow stages" }),
    ).toBeVisible();
    await expect(page.getByText("Batch applicants in two steps")).toBeVisible();
    await expect(
      page.getByText("1 applicant ready to release"),
    ).toBeVisible();
    await expect(page.getByText("1 applicant in this batch")).toBeVisible();
    await expect(
      page.getByText("School of Computing → College of Science"),
    ).toBeVisible();
    await page.getByRole("button", { name: "Release 1 applicant" }).click();
    await page.getByRole("button", { name: "Release batch" }).click();
    await expect
      .poll(() => recordedRequests)
      .toContain(
        `/api/admissions/academic-reviews/selection-rounds/${selectionRoundId}/releases`,
      );

    operation = "recommendation";
    await page.goto("/operations/admissions-recommendations");
    await expect(page.getByText("Advisory authority")).toBeVisible();
    await page
      .getByRole("button", { name: "View applicants", exact: true })
      .click();
    await page.getByRole("button", { name: "Recommend", exact: true }).click();
    await page
      .locator("#reason")
      .fill("Meets the academic requirements and merit threshold.");
    await page.getByRole("button", { name: "Record recommendation" }).click();
    await expect
      .poll(() => recordedRequests)
      .toContain(
        `/api/admissions/academic-reviews/${assignmentId}/recommendations`,
      );

    operation = "decision";
    await page.goto("/operations/admissions-decisions");
    await expect(
      page.getByText("Admissions retains final authority"),
    ).toBeVisible();
    await page
      .getByRole("button", { name: "View applicants", exact: true })
      .click();
    await page.getByRole("button", { name: "Approve", exact: true }).click();
    await page
      .locator("textarea.swal2-textarea")
      .fill("Approved after Admissions verification of the recommendation.");
    await page.getByRole("button", { name: "Approve final decision" }).click();
    await expect
      .poll(() => recordedRequests)
      .toContain(`/api/admissions/academic-reviews/${assignmentId}/review`);
  });

  test("shows approved selections inside an offer batch before offer generation", async ({
    page,
  }, testInfo) => {
    const username = `codex.admissions-offer-candidate.${testInfo.project.name.replace(/[^a-z0-9]+/gi, "-").toLowerCase()}@example.test`;
    await ensureSystemAdminUser(username);
    const intakeId = "1c000000-0000-0000-0000-000000000001";
    const selectionRoundId = "2c000000-0000-0000-0000-000000000002";
    const offerBatchId = "3c000000-0000-0000-0000-000000000003";
    const applicationId = "4c000000-0000-0000-0000-000000000004";
    const programmeChoiceId = "5c000000-0000-0000-0000-000000000005";
    const programmeId = "6c000000-0000-0000-0000-000000000006";
    const academicUnitId = "7c000000-0000-0000-0000-000000000007";

    await page.route("**/api/academic/overview", (route) =>
      route.fulfill({
        json: {
          academicUnitTypes: [],
          academicUnits: [],
          academicYears: [],
          academicPeriodTypes: [],
          academicPeriods: [],
          intakes: [],
          programmeLevels: [],
          programmeTypes: [],
          programmes: [
            {
              id: programmeId,
              code: "HSC",
              name: "Bachelor of Science Computer Science",
              owningAcademicUnitId: academicUnitId,
              status: "ACTIVE",
            },
          ],
          modules: [],
        },
      }),
    );
    await page.route("**/api/admissions/applications", (route) =>
      route.fulfill({
        json: [
          {
            id: applicationId,
            applicationNumber: "EMH-FEB2027-00000762",
            applicantNumber: "APP-00000762",
            applicantName: "Jemima Megan Lindsey Stevens",
            intakeId,
            intakeCode: "FEB-2027",
            applicationTypeId: "8c000000-0000-0000-0000-000000000008",
            applicationTypeName: "Undergraduate",
            status: "SELECTED",
            paymentRequired: false,
            paymentClearanceStatus: "NOT_REQUIRED",
            paymentWaiverReason: null,
            canSubmit: false,
            canEnterReview: true,
            calculatedTotalPoints: 15,
            pointsCalculatedAt: "2027-01-10T08:00:00Z",
            admissionsClearanceStatus: "CONFIRMED",
            confirmedByUserId: "9c000000-0000-0000-0000-000000000009",
            confirmedAt: "2027-01-10T08:00:00Z",
            confirmationReason: "Admissions checks complete.",
            payment: null,
            programmeChoices: [
              {
                id: programmeChoiceId,
                programmeId,
                programmeVersionId:
                  "ac000000-0000-0000-0000-000000000010",
                programmeCode: "HSC",
                programmeName: "Bachelor of Science Computer Science",
                awardName: "Bachelor of Science",
                owningAcademicUnitName: "Faculty of Science",
                programmeVersionCode: "2027",
                choiceRank: 1,
                choiceStatus: "SELECTED",
                evaluationSummary: "Eligible",
                decisionReason: "Approved on merit.",
              },
            ],
          },
        ],
      }),
    );
    await page.route("**/api/admissions/selection-rounds", (route) =>
      route.fulfill({
        json: [
          {
            id: selectionRoundId,
            intakeId,
            intakeCode: "FEB-2027",
            code: "2027R1",
            name: "2027R1",
            status: "APPROVED",
            openedAt: "2027-01-10T08:00:00Z",
            approvedAt: "2027-01-12T08:00:00Z",
            closedAt: null,
          },
        ],
      }),
    );
    await page.route("**/api/admissions/offer-batches", (route) =>
      route.fulfill({
        json: [
          {
            id: offerBatchId,
            intakeId,
            selectionRoundId,
            code: "2027-R1-OFFERS",
            name: "2027 first merit offers",
            scopeType: "INSTITUTION",
            scopeId: null,
            status: "APPROVED",
            approvedAt: "2027-01-12T09:00:00Z",
            dispatchedAt: null,
            closedAt: null,
          },
        ],
      }),
    );
    await page.route("**/api/admissions/offers", (route) => {
      if (route.request().method() === "POST") {
        return route.fulfill({
          json: {
            id: "cc000000-0000-0000-0000-000000000012",
            offerBatchId,
            offerNumber: "OFR-FEB2027-00000001",
            applicationId,
            applicationNumber: "EMH-FEB2027-00000762",
            applicantNumber: "APP-00000762",
            applicantName: "Jemima Megan Lindsey Stevens",
            programmeChoiceId,
            programmeId,
            programmeVersionId: "ac000000-0000-0000-0000-000000000010",
            programmeCode: "HSC",
            programmeName: "Bachelor of Science Computer Science",
            intakeId,
            offerType: "FIRM",
            status: "DRAFT",
            conditionsText: null,
            acceptanceDeadline: "2027-02-12T08:00:00Z",
            registrationDate: null,
            orientationDate: null,
            commencementDate: "2027-03-01",
            generatedDocumentId: null,
            approvedAt: null,
            sentAt: null,
            expiredAt: null,
            expiryReason: null,
            conversionRequestedAt: null,
            conversionRequestId: null,
            convertedStudentId: null,
            convertedStudentNumber: null,
            convertedAt: null,
            conditions: [],
            response: null,
          },
        });
      }
      return route.fulfill({ json: [] });
    });
    await page.route(
      `**/api/admissions/selection-rounds/${selectionRoundId}/decisions`,
      (route) =>
        route.fulfill({
          json: [
            {
              id: "bc000000-0000-0000-0000-000000000011",
              selectionRoundId,
              programmeChoiceId,
              applicationNumber: "EMH-FEB2027-00000762",
              programmeCode: "HSC",
              programmeName: "Bachelor of Science Computer Science",
              decision: "SELECT",
              rankPosition: 1,
              quotaTypeCode: "MERIT",
              reason: "Approved on merit.",
              decidedByUserId: "9c000000-0000-0000-0000-000000000009",
              decidedAt: "2027-01-12T08:00:00Z",
            },
          ],
        }),
    );

    await page.goto("/operations/admissions-offers");
    await expect(page).toHaveURL(
      /\/realms\/emhare\/protocol\/openid-connect\/auth/,
      { timeout: 15_000 },
    );
    await loginWithKeycloak(page, username);
    const admissionsWorkflowStages = page.getByRole("navigation", {
      name: "Admissions workflow stages",
    });
    await expect(admissionsWorkflowStages.getByRole("link")).toHaveCount(5);
    await expect(
      page.getByRole("button", { name: "Admissions tools" }),
    ).toHaveCount(0);
    if (testInfo.project.name === "chromium-mobile") {
      await page.getByRole("button", { name: "Open sidebar" }).click();
    }
    const admissionsSidebar = page.getByRole("region", {
      name: "Admissions",
    });
    for (const navigationLabel of [
      "Admissions Workflow",
      "Application types",
      "Evidence verification",
      "Eligibility evaluation",
      "Selection rounds",
      "Applicant register",
      "Academic-unit documents",
    ]) {
      await expect(
        admissionsSidebar.getByRole("link", {
          name: navigationLabel,
          exact: true,
        }),
      ).toBeVisible();
    }
    if (testInfo.project.name === "chromium-mobile") {
      await page.keyboard.press("Escape");
    }
    await expect(page.getByText("1 ready · 0 generated")).toBeVisible();
    await page
      .getByRole("button", { name: "View applicants", exact: true })
      .click();
    await expect(
      page.getByText("Jemima Megan Lindsey Stevens", { exact: true }),
    ).toBeVisible();
    await expect(
      page.getByText("Applicant APP-00000762", { exact: true }),
    ).toBeVisible();
    await expect(page.getByText("Ready for offer", { exact: true })).toBeVisible();
    await expect(
      page.getByRole("link", { name: "View full profile" }),
    ).toHaveAttribute("href", `/operations/admissions/${applicationId}`);
    await page.getByRole("button", { name: "Generate offer" }).last().click();
    await expect(
      page.getByRole("heading", { name: "Generate offer draft" }),
    ).toBeVisible();
    await expect(
      page.getByText(
        "EMH-FEB2027-00000762 · Jemima Megan Lindsey Stevens · HSC · Bachelor of Science Computer Science",
        { exact: true },
      ),
    ).toBeVisible();
    await page.getByRole("button", { name: "Generate draft" }).click();
    await expect(page.getByText("0 ready · 1 generated")).toBeVisible();
    await expect(
      page
        .getByRole("region", { name: "Offer register" })
        .getByText("Jemima Megan Lindsey Stevens", { exact: true }),
    ).toBeVisible();
    await expect(
      page
        .getByRole("region", { name: "Offer register" })
        .getByText("EMH-FEB2027-00000762 · Applicant APP-00000762", {
          exact: true,
        }),
    ).toBeVisible();
    await page
      .getByRole("region", { name: "Offer register" })
      .getByRole("button", { name: "Approve" })
      .click();
    await expect(
      page.getByRole("heading", {
        name: "Official offer letter is still being prepared",
      }),
    ).toBeVisible();
    await expect(
      page.getByText(
        "The system generates and stores the official PDF automatically after the offer draft is created.",
        { exact: true },
      ),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Refresh offer status" }),
    ).toBeVisible();
  });
});
