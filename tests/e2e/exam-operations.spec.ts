import { expect, request as playwrightRequest, test, type Page } from "@playwright/test";
import { spawnSync } from "node:child_process";
import { randomUUID } from "node:crypto";

const keycloakBaseUrl = process.env.KEYCLOAK_URL ?? "http://localhost:8099";
const username = `exam-ui-${randomUUID()}@example.test`;
const password = "Temporary-Exam-UI-Password-42";
const postgresContainer = process.env.POSTGRES_CONTAINER ?? "emhare-postgres";
let keycloakUserId = "";

async function adminContext() {
  const context = await playwrightRequest.newContext();
  const response = await context.post(
    `${keycloakBaseUrl}/realms/master/protocol/openid-connect/token`,
    {
      form: {
        client_id: "admin-cli",
        username: "admin",
        password: "admin",
        grant_type: "password",
      },
    },
  );
  const payload = await response.json();
  await context.dispose();
  return playwrightRequest.newContext({
    extraHTTPHeaders: {
      Authorization: `Bearer ${payload.access_token}`,
      "Content-Type": "application/json",
    },
  });
}

test.beforeAll(async () => {
  const admin = await adminContext();
  const created = await admin.post(`${keycloakBaseUrl}/admin/realms/emhare/users`, {
    data: {
      username,
      email: username,
      firstName: "Exam",
      lastName: "Operator",
      enabled: true,
      emailVerified: true,
      credentials: [{ type: "password", value: password, temporary: false }],
    },
  });
  expect(created.status()).toBe(201);
  keycloakUserId = created.headers().location!.split("/").at(-1)!;
  const role = await admin.get(`${keycloakBaseUrl}/admin/realms/emhare/roles/system-admin`);
  expect(
    (
      await admin.post(
        `${keycloakBaseUrl}/admin/realms/emhare/users/${keycloakUserId}/role-mappings/realm`,
        { data: [await role.json()] },
      )
    ).status(),
  ).toBe(204);
  await admin.dispose();
});

test.afterAll(async () => {
  if (keycloakUserId) {
    const admin = await adminContext();
    await admin.delete(`${keycloakBaseUrl}/admin/realms/emhare/users/${keycloakUserId}`);
    await admin.dispose();
  }
  const result = spawnSync(
    "docker",
    ["exec", "-i", postgresContainer, "psql", "-q", "-U", "postgres", "-d", "emhare_core_identity"],
    {
      input: `BEGIN; SET LOCAL session_replication_role=replica; DELETE FROM user_role_assignments_aud WHERE user_id IN (SELECT id FROM users WHERE email='${username}'); DELETE FROM user_role_assignments WHERE user_id IN (SELECT id FROM users WHERE email='${username}'); DELETE FROM login_events_aud WHERE user_id IN (SELECT id FROM users WHERE email='${username}'); DELETE FROM login_events WHERE user_id IN (SELECT id FROM users WHERE email='${username}'); DELETE FROM users_aud WHERE email='${username}'; DELETE FROM users WHERE email='${username}'; COMMIT;`,
      encoding: "utf8",
    },
  );
  if (result.status !== 0) throw new Error(result.stderr);
});

async function login(page: Page) {
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);
  await page.locator("#kc-login").click();
  await page.waitForURL(/http:\/\/localhost:3000\/operations\/exam-setup.*/, { timeout: 30_000 });
  await page.waitForLoadState("networkidle");
}

test("presents separate governed setup, timetable, and invigilation workspaces", async ({
  page,
}) => {
  const ids = Array.from({ length: 8 }, () => randomUUID());
  const setup = {
    venueTypes: [{ id: ids[0], code: "HALL", name: "Examination hall", active: true, version: 0 }],
    venues: [
      {
        id: ids[1],
        venueTypeId: ids[0],
        venueTypeCode: "HALL",
        code: "GREAT-HALL",
        name: "Great Hall",
        campusName: "Main Campus",
        examinationCapacity: 600,
        active: true,
        version: 0,
        availability: [
          {
            id: ids[2],
            availableFrom: "2027-05-01T00:00:00Z",
            availableUntil: "2027-05-10T23:59:59Z",
            notes: "Final exam period",
          },
        ],
      },
    ],
    sessions: [
      {
        id: ids[3],
        academicPeriodId: ids[4],
        academicPeriodCode: "2027-S1",
        code: "FINAL-2027-S1",
        name: "Final examinations",
        assessmentType: "FINAL_EXAM",
        startsOn: "2027-05-01",
        endsOn: "2027-05-10",
        status: "APPROVED",
        approvedByUserId: ids[5],
        approvedAt: "2027-03-01T08:00:00Z",
        approvalReason: "Registrar approval",
        version: 1,
        slots: [
          {
            id: ids[6],
            code: "DAY-1-AM",
            startsAt: "2027-05-03T08:00:00Z",
            endsAt: "2027-05-03T12:00:00Z",
          },
        ],
      },
    ],
    requirements: [
      {
        id: ids[7],
        academicPeriodId: ids[4],
        moduleId: randomUUID(),
        moduleCode: "ACC101",
        moduleName: "Financial Accounting I",
        requirementVersion: 1,
        durationMinutes: 180,
        readingTimeMinutes: 15,
        requiredVenueTypeId: ids[0],
        requiredVenueTypeCode: "HALL",
        specialRequirements: "Standard invigilation",
        status: "APPROVED",
        version: 1,
      },
    ],
  };
  const academic = {
    academicUnitTypes: [],
    academicUnits: [],
    academicYears: [],
    academicPeriodTypes: [],
    academicPeriods: [
      {
        id: ids[4],
        academicYearId: randomUUID(),
        academicYearName: "2027",
        academicPeriodTypeId: randomUUID(),
        academicPeriodTypeName: "Semester",
        code: "2027-S1",
        name: "Semester 1",
        startDate: "2027-01-12",
        endDate: "2027-06-20",
        status: "OPEN",
        version: 1,
      },
    ],
    intakes: [],
    programmeLevels: [],
    programmeTypes: [],
    programmes: [],
    modules: [],
  };
  const run = {
    id: randomUUID(),
    examSessionId: ids[3],
    sessionCode: "FINAL-2027-S1",
    sessionName: "Final examinations",
    runNumber: "EXM-FINAL-2027-S1-1",
    status: "REVIEWED",
    candidateCount: 548,
    moduleCount: 42,
    timetableEntryCount: 42,
    conflictCount: 0,
    generationPolicy: { algorithm: "largest-roster-first-v1" },
    generatedByUserId: randomUUID(),
    generatedAt: "2027-03-02T08:00:00Z",
    reviewedByUserId: randomUUID(),
    version: 1,
    entries: [
      {
        id: randomUUID(),
        moduleId: setup.requirements[0].moduleId,
        moduleCode: "ACC101",
        moduleName: "Financial Accounting I",
        candidateCount: 320,
        slotId: ids[6],
        slotCode: "DAY-1-AM",
        startsAt: "2027-05-03T08:00:00Z",
        endsAt: "2027-05-03T11:15:00Z",
        venues: [
          {
            id: randomUUID(),
            venueId: ids[1],
            venueCode: "GREAT-HALL",
            venueName: "Great Hall",
            allocatedCapacity: 320,
          },
        ],
      },
    ],
  };
  const attendanceSessionId = randomUUID();
  const attendanceRecordId = randomUUID();
  const studentTimetableEntryId = randomUUID();
  const invigilation = {
    venueOperations: [
      {
        venueAllocationId: randomUUID(),
        generationRunId: run.id,
        runNumber: run.runNumber,
        masterTimetableEntryId: run.entries[0].id,
        moduleCode: "ACC101",
        moduleName: "Financial Accounting I",
        scheduledStartsAt: "2027-05-03T08:00:00Z",
        scheduledEndsAt: "2027-05-03T11:15:00Z",
        venueId: ids[1],
        venueCode: "GREAT-HALL",
        venueName: "Great Hall",
        campusName: "Main Campus",
        allocatedCandidateCount: 1,
        attendanceSession: {
          id: attendanceSessionId,
          status: "OPEN",
          expectedCandidateCount: 1,
          presentCandidateCount: 0,
          absentCandidateCount: 0,
          excusedCandidateCount: 0,
          outstandingCandidateCount: 1,
          openedByUserId: randomUUID(),
          openedAt: "2027-05-03T07:00:00Z",
          openingReason: "Room and sealed examination materials checked.",
          version: 0,
          attendanceRecords: [
            {
              id: attendanceRecordId,
              studentTimetableEntryId,
              studentId: randomUUID(),
              studentNumber: "R2310456",
              seatNumber: 17,
              attendanceStatus: "EXPECTED",
              version: 0,
            },
          ],
          incidents: [
            {
              id: randomUUID(),
              incidentNumber: "INC-2027-000014",
              studentTimetableEntryId,
              studentNumber: "R2310456",
              incidentType: "LATE_ARRIVAL",
              severity: "LOW",
              description:
                "Candidate arrived after room opening and identity evidence was checked.",
              occurredAt: "2027-05-03T08:12:00Z",
              status: "REVIEWED",
              reportedByUserId: randomUUID(),
              reportedAt: "2027-05-03T08:15:00Z",
              reviewedByUserId: randomUUID(),
              reviewedAt: "2027-05-03T08:30:00Z",
              reviewReason: "Evidence independently checked.",
              version: 1,
            },
          ],
        },
      },
    ],
  };
  await page.route("**/api/exams/setup", (route) => route.fulfill({ json: setup }));
  await page.route("**/api/academic/overview", (route) => route.fulfill({ json: academic }));
  await page.route("**/api/timetabling/runs", (route) => route.fulfill({ json: [run] }));
  await page.route("**/api/exams/invigilation", (route) => route.fulfill({ json: invigilation }));
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("console", (message) => message.type() === "error" && errors.push(message.text()));
  await page.goto("/operations/exam-setup");
  await login(page);
  await expect(page.getByText("Approved inputs before scheduling")).toBeVisible();
  await page.getByRole("tab", { name: /Venues/ }).click();
  await expect(page.getByText("Great Hall", { exact: true })).toBeVisible();
  await page.getByRole("tab", { name: /Module requirements/ }).click();
  await expect(page.getByText("Financial Accounting I", { exact: true })).toBeVisible();
  await page.goto("/operations/exam-timetables");
  await expect(page.getByText("Candidate-safe timetable governance")).toBeVisible();
  await expect(page.getByText("548", { exact: true })).toBeVisible();
  await expect(page.getByText("GREAT-HALL · 320", { exact: true })).toBeVisible();
  await page.goto("/operations/exam-invigilation");
  await expect(page.getByText("Published roster to reconciled room evidence")).toBeVisible();
  await expect(page.getByText("R2310456", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("INC-2027-000014", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Resolve" })).toBeVisible();
  await page.getByRole("button", { name: "Report incident" }).click();
  const incidentDrawer = page.getByRole("dialog", { name: "Report examination incident" });
  const candidateOrRoomSelect = incidentDrawer.getByLabel("Candidate or room");
  await candidateOrRoomSelect.click();
  await expect(page.getByRole("option", { name: "Room-wide incident" })).toBeVisible();
  await page.getByRole("option", { name: "R2310456 · seat 17" }).click();
  await expect(candidateOrRoomSelect).toContainText("R2310456 · seat 17");
  await candidateOrRoomSelect.click();
  await page.getByRole("option", { name: "Room-wide incident" }).click();
  await expect(candidateOrRoomSelect).toContainText("Room-wide incident");
  await incidentDrawer.getByRole("button", { name: "Cancel" }).click();
  expect(errors).toEqual([]);
});
