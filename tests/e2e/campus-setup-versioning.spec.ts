// Author: Tinashe K
import { expect, request, test, type APIRequestContext, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { spawnSync } from "node:child_process";

const keycloakBaseUrl = process.env.KEYCLOAK_URL ?? "http://localhost:8099";
const keycloakRealm = process.env.KEYCLOAK_REALM ?? "emhare";
const fixturePassword = "Temporary-Campus-Versioning-42";
type CreatedRecord = { id: string; version: number; [key: string]: unknown };
type FixtureRecord = { database: string; table: string; record: CreatedRecord };

async function keycloakAdministrator() {
  const authentication = await request.newContext();
  const response = await authentication.post(
    `${keycloakBaseUrl}/realms/master/protocol/openid-connect/token`,
    {
      form: {
        client_id: "admin-cli",
        grant_type: "password",
        username: process.env.KEYCLOAK_ADMIN_USERNAME ?? "admin",
        password: process.env.KEYCLOAK_ADMIN_PASSWORD ?? "admin",
      },
    },
  );
  expect(response.ok()).toBeTruthy();
  const { access_token: accessToken } = await response.json();
  await authentication.dispose();
  return request.newContext({ extraHTTPHeaders: { Authorization: `Bearer ${accessToken}` } });
}

function executeFixtureSql(database: string, sql: string) {
  const result = spawnSync(
    "docker",
    [
      "exec",
      "-i",
      process.env.POSTGRES_CONTAINER ?? "emhare-postgres",
      "psql",
      "-qAt",
      "-v",
      "ON_ERROR_STOP=1",
      "-U",
      "postgres",
      "-d",
      database,
    ],
    { input: sql, encoding: "utf8" },
  );
  if (result.status !== 0) throw new Error(result.stderr || result.stdout);
  return result.stdout.trim();
}

async function browserApi(page: Page) {
  const accessToken = await page.evaluate(() => {
    for (let index = 0; index < localStorage.length; index++) {
      const key = localStorage.key(index);
      if (!key?.startsWith("oidc.user:")) continue;
      const rawUser = localStorage.getItem(key);
      if (rawUser) return (JSON.parse(rawUser) as { access_token?: string }).access_token;
    }
    return undefined;
  });
  expect(accessToken).toBeTruthy();
  const setupResponsePromise = page.waitForResponse(
    (response) => new URL(response.url()).pathname === "/api/dining/setup",
  );
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  const setupResponse = await setupResponsePromise;
  expect(setupResponse.ok()).toBeTruthy();
  const gatewayOrigin = new URL(setupResponse.url()).origin;
  return request.newContext({
    baseURL: gatewayOrigin,
    extraHTTPHeaders: { Authorization: `Bearer ${accessToken}` },
  });
}

async function repeatVersionedEdits(
  page: Page,
  api: APIRequestContext,
  route: string,
  tab: string,
  rowIdentifier: string,
  endpoint: string,
  fieldLabel: string,
  fieldKey: string,
  values: string[],
  record: CreatedRecord,
) {
  await page.goto(route);
  await page.waitForLoadState("networkidle");
  await page.getByRole("tab", { name: tab, exact: false }).click();
  await page.getByRole("combobox", { name: "Rows per page" }).click();
  await page.getByRole("option", { name: "100 per page", exact: true }).click();
  let previousVersion = record.version;
  for (const value of values) {
    const row = page.getByRole("row").filter({ hasText: rowIdentifier });
    await expect(row).toHaveCount(1);
    await row.getByRole("button", { name: "Edit", exact: true }).click();
    const workspace = page.locator('[data-emhare-form-presentation="page"]');
    await expect(workspace).toBeVisible();
    await workspace.getByLabel(fieldLabel, { exact: true }).fill(value);
    const responsePromise = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === endpoint && response.request().method() === "PUT",
    );
    await workspace.getByRole("button", { name: "Save", exact: true }).click();
    const response = await responsePromise;
    expect(response.ok(), await response.text()).toBeTruthy();
    expect(response.request().postDataJSON().expectedVersion).toBe(previousVersion);
    const updated = (await response.json()) as CreatedRecord;
    expect(updated.version).toBeGreaterThan(previousVersion);
    expect(String(updated[fieldKey])).toBe(value);
    previousVersion = updated.version;
    await expect(workspace).toBeHidden();
    const registerResponse = await api.get(
      endpoint.substring(0, endpoint.lastIndexOf("/", endpoint.lastIndexOf("/") - 1)),
    );
    expect(registerResponse.ok()).toBeTruthy();
    const collections = Object.values(await registerResponse.json()) as CreatedRecord[][];
    const persisted = collections.flat().find((item) => item.id === record.id);
    expect(persisted?.version).toBe(previousVersion);
    expect(String(persisted?.[fieldKey])).toBe(value);
  }
}

test("repeated Dining and Accommodation setup edits use persisted optimistic-lock versions", async ({
  page,
}) => {
  test.setTimeout(180_000);
  const runId = randomUUID();
  const suffix = runId.replaceAll("-", "").slice(0, 8).toUpperCase();
  const username = `campus-version-${runId}@example.test`;
  const records: FixtureRecord[] = [];
  const administrator = await keycloakAdministrator();
  let userId: string | undefined;
  let api: APIRequestContext | undefined;
  try {
    const createUser = await administrator.post(
      `${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users`,
      {
        data: {
          username,
          email: username,
          firstName: "Campus",
          lastName: "Versioning",
          enabled: true,
          emailVerified: true,
          credentials: [{ type: "password", value: fixturePassword, temporary: false }],
        },
      },
    );
    expect(createUser.status()).toBe(201);
    userId = createUser.headers().location!.split("/").at(-1)!;
    const role = await administrator.get(
      `${keycloakBaseUrl}/admin/realms/${keycloakRealm}/roles/system-admin`,
    );
    expect(role.ok()).toBeTruthy();
    const assignRole = await administrator.post(
      `${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users/${userId}/role-mappings/realm`,
      { data: [await role.json()] },
    );
    expect(assignRole.status()).toBe(204);
    await page.goto("/operations/dining");
    await page.locator("#username").fill(username);
    await page.locator("#password").fill(fixturePassword);
    await page.locator("#kc-login").click();
    await page.waitForURL(/\/operations\/dining/);
    await page.waitForLoadState("networkidle");
    api = await browserApi(page);
    async function createRecord(
      service: "dining" | "accommodation",
      resource: string,
      table: string,
      data: object,
    ) {
      const response = await api!.post(`/api/${service}/setup/${resource}`, { data });
      expect(response.ok(), await response.text()).toBeTruthy();
      const record = (await response.json()) as CreatedRecord;
      expect(record.id).toMatch(/^[0-9a-f-]{36}$/);
      records.push({ database: `emhare_${service}`, table, record });
      return record;
    }
    const hall = await createRecord("dining", "halls", "dining_halls", {
      code: `CVH${suffix}`,
      name: "Campus version hall",
      locationDescription: "Fixture campus",
      serviceCapacity: 10,
    });
    const meal = await createRecord("dining", "meal-options", "meal_options", {
      code: `CVM${suffix}`,
      name: "Campus version meal",
      description: "Fixture meal",
      mealCategory: "LUNCH",
    });
    const service = await createRecord("dining", "service-times", "meal_service_times", {
      diningHallId: hall.id,
      mealOptionId: meal.id,
      dayOfWeek: 1,
      serviceOpensAt: "11:00",
      serviceClosesAt: "13:00",
      graceClosesAt: "13:15",
    });
    const rule = await createRecord(
      "dining",
      "hall-assignment-rules",
      "dining_hall_assignment_rules",
      {
        diningHallId: hall.id,
        ruleDimension: "STUDENT_GROUP",
        comparisonOperator: "EQUALS",
        comparisonValue: suffix,
        priorityRank: 100,
      },
    );
    const attendant = await createRecord(
      "dining",
      "attendant-assignments",
      "dining_attendant_assignments",
      {
        diningHallId: hall.id,
        staffId: userId,
        staffNumber: `CVS${suffix}`,
        staffName: "Campus Versioning",
        effectiveFrom: "2026-01-01",
        effectiveUntil: null,
        roleCode: "ATTENDANT",
      },
    );
    const premise = await createRecord("accommodation", "premises", "accommodation_premises", {
      code: `CVP${suffix}`,
      name: "Campus version premise",
      addressLine: "Fixture campus road",
      suburb: "Mount Pleasant",
      landlordName: "UZ",
      contactDetails: "Fixture",
    });
    const roomType = await createRecord("accommodation", "room-types", "accommodation_room_types", {
      code: `CVT${suffix}`,
      name: "Campus version room type",
      description: "Fixture room type",
      defaultCapacity: 1,
    });
    const residence = await createRecord("accommodation", "residence-halls", "residence_halls", {
      premiseId: premise.id,
      code: `CVR${suffix}`,
      name: "Campus version residence",
      residentGenderPolicy: "ANY",
      wardenName: "Fixture warden",
      wardenContact: "Fixture",
    });
    const room = await createRecord("accommodation", "rooms", "accommodation_rooms", {
      residenceHallId: residence.id,
      roomTypeId: roomType.id,
      code: `CV${suffix}`,
      floorLabel: "1",
      capacity: 1,
      accessibilityReady: false,
      conditionStatus: "AVAILABLE",
      conditionNotes: null,
      reservedForGroupId: null,
    });
    for (const [tab, identifier, resource, field, key, values, record] of [
      [
        "Dining halls",
        `CVH${suffix}`,
        "halls",
        "Location",
        "locationDescription",
        ["First location", "Second location"],
        hall,
      ],
      [
        "Meal options",
        `CVM${suffix}`,
        "meal-options",
        "Description",
        "description",
        ["First meal", "Second meal"],
        meal,
      ],
      [
        "Service times",
        `CVH${suffix}`,
        "service-times",
        "Opens",
        "serviceOpensAt",
        ["11:10:00", "11:20:00"],
        service,
      ],
      [
        "Hall routing rules",
        `CVH${suffix}`,
        "hall-assignment-rules",
        "Match value",
        "comparisonValue",
        ["FIRST", "SECOND"],
        rule,
      ],
      [
        "Attendant assignments",
        `CVS${suffix}`,
        "attendant-assignments",
        "Staff name",
        "staffName",
        ["First attendant", "Second attendant"],
        attendant,
      ],
    ] as const) {
      await repeatVersionedEdits(
        page,
        api,
        "/operations/dining",
        tab,
        identifier,
        `/api/dining/setup/${resource}/${record.id}`,
        field,
        key,
        [...values],
        record,
      );
    }
    for (const [tab, identifier, resource, field, key, values, record] of [
      [
        "Premises",
        `CVP${suffix}`,
        "premises",
        "Address",
        "addressLine",
        ["First address", "Second address"],
        premise,
      ],
      [
        "Room types",
        `CVT${suffix}`,
        "room-types",
        "Description",
        "description",
        ["First description", "Second description"],
        roomType,
      ],
      [
        "Residence halls",
        `CVR${suffix}`,
        "residence-halls",
        "Warden",
        "wardenName",
        ["First warden", "Second warden"],
        residence,
      ],
      [
        "Rooms",
        `CV${suffix}`,
        "rooms",
        "Condition notes",
        "conditionNotes",
        ["First inspection", "Second inspection"],
        room,
      ],
    ] as const) {
      await repeatVersionedEdits(
        page,
        api,
        "/operations/accommodation",
        tab,
        identifier,
        `/api/accommodation/setup/${resource}/${record.id}`,
        field,
        key,
        [...values],
        record,
      );
    }
  } finally {
    await api?.dispose();
    // These UUIDs were returned by this test's successful create commands only.
    for (const { database, table, record } of records.reverse())
      executeFixtureSql(
        database,
        `BEGIN; DELETE FROM ${table}_aud WHERE id = '${record.id}'; DELETE FROM ${table} WHERE id = '${record.id}'; COMMIT;`,
      );
    if (userId) {
      const localUserId = executeFixtureSql(
        "emhare_core_identity",
        `SELECT id FROM users WHERE keycloak_user_id = '${userId}';`,
      );
      if (localUserId) {
        if (!/^[0-9a-f-]{36}$/.test(localUserId))
          throw new Error("Unexpected fixture identity lookup");
        executeFixtureSql(
          "emhare_core_identity",
          `BEGIN; DELETE FROM user_role_assignments_aud WHERE user_id = '${localUserId}'; DELETE FROM user_role_assignments WHERE user_id = '${localUserId}'; DELETE FROM login_events_aud WHERE user_id = '${localUserId}'; DELETE FROM login_events WHERE user_id = '${localUserId}'; DELETE FROM users_aud WHERE id = '${localUserId}'; DELETE FROM users WHERE id = '${localUserId}'; COMMIT;`,
        );
      }
      const deletedUser = await administrator.delete(
        `${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users/${userId}`,
      );
      expect(deletedUser.status()).toBe(204);
    }
    await administrator.dispose();
  }
});
