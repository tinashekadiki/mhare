import { expect, request as playwrightRequest, test, type APIRequestContext, type Page } from '@playwright/test'
import { spawnSync } from 'node:child_process'
import { randomUUID } from 'node:crypto'

const keycloakBaseUrl = process.env.KEYCLOAK_URL ?? 'http://localhost:8099'
const keycloakRealm = process.env.KEYCLOAK_REALM ?? 'emhare'
const keycloakAdminUsername = process.env.KEYCLOAK_ADMIN_USERNAME ?? 'admin'
const keycloakAdminPassword = process.env.KEYCLOAK_ADMIN_PASSWORD ?? 'admin'
const postgresContainer = process.env.POSTGRES_CONTAINER ?? 'emhare-postgres'
const testPassword = 'Temporary-Academic-UI-Password-42'

type AcademicUiFixture = {
  userId: string
  username: string
  academicUnitTypeRootId: string
  academicUnitTypeLeafId: string
  academicUnitRootId: string
  academicUnitLeafId: string
  academicYearId: string
  calendarYear: number
  academicYearName: string
  academicPeriodTypeId: string
  academicPeriodId: string
  intakeId: string
  programmeLevelId: string
  programmeTypeId: string
  programmeId: string
  programmeVersionId: string
  moduleId: string
  curriculumModuleId: string
  codeSuffix: string
}

async function createKeycloakAdminRequestContext() {
  const tokenContext = await playwrightRequest.newContext()
  const tokenResponse = await tokenContext.post(`${keycloakBaseUrl}/realms/master/protocol/openid-connect/token`, {
    form: {
      client_id: 'admin-cli', username: keycloakAdminUsername,
      password: keycloakAdminPassword, grant_type: 'password'
    }
  })
  expect(tokenResponse.ok()).toBeTruthy()
  const tokenPayload = await tokenResponse.json()
  await tokenContext.dispose()
  return playwrightRequest.newContext({
    extraHTTPHeaders: { Authorization: `Bearer ${tokenPayload.access_token}`, 'Content-Type': 'application/json' }
  })
}

async function createFixture(): Promise<AcademicUiFixture> {
  const runId = randomUUID()
  const codeSuffix = runId.replaceAll('-', '').slice(0, 8).toUpperCase()
  const username = `academic-ui-${runId}@example.test`
  const keycloakAdmin = await createKeycloakAdminRequestContext()
  const createUserResponse = await keycloakAdmin.post(`${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users`, {
    data: {
      username, email: username, firstName: 'Academic', lastName: 'Administrator',
      enabled: true, emailVerified: true,
      credentials: [{ type: 'password', value: testPassword, temporary: false }]
    }
  })
  expect(createUserResponse.status()).toBe(201)
  const userId = createUserResponse.headers().location!.split('/').at(-1)!
  const roleResponse = await keycloakAdmin.get(`${keycloakBaseUrl}/admin/realms/${keycloakRealm}/roles/system-admin`)
  expect(roleResponse.ok()).toBeTruthy()
  const assignRoleResponse = await keycloakAdmin.post(
    `${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users/${userId}/role-mappings/realm`,
    { data: [await roleResponse.json()] }
  )
  expect(assignRoleResponse.status()).toBe(204)
  await keycloakAdmin.dispose()

  const calendarYear = 2200 + (Number.parseInt(codeSuffix.slice(0, 4), 16) % 6800)
  const uniqueSortOrder = Number.parseInt(codeSuffix.slice(0, 7), 16)
  const academicUnitTypeRootId = executeSql(
    'emhare_academic_setup',
    "SELECT id FROM academic_unit_types WHERE code = 'FACULTY' AND deleted_at IS NULL;",
    true
  ).trim()
  const academicUnitTypeLeafId = executeSql(
    'emhare_academic_setup',
    "SELECT id FROM academic_unit_types WHERE code = 'DEPARTMENT' AND deleted_at IS NULL;",
    true
  ).trim()
  if (!academicUnitTypeRootId || !academicUnitTypeLeafId) {
    throw new Error('Canonical FACULTY and DEPARTMENT academic unit types are required for the browser fixture.')
  }
  const fixture: AcademicUiFixture = {
    userId,
    username,
    academicUnitTypeRootId,
    academicUnitTypeLeafId,
    academicUnitRootId: randomUUID(),
    academicUnitLeafId: randomUUID(),
    academicYearId: randomUUID(),
    calendarYear,
    academicYearName: `${calendarYear}-${codeSuffix}`,
    academicPeriodTypeId: randomUUID(),
    academicPeriodId: randomUUID(),
    intakeId: randomUUID(),
    programmeLevelId: randomUUID(),
    programmeTypeId: randomUUID(),
    programmeId: randomUUID(),
    programmeVersionId: randomUUID(),
    moduleId: randomUUID(),
    curriculumModuleId: randomUUID(),
    codeSuffix
  }
  executeSql('emhare_academic_setup', `
INSERT INTO academic_units (id, academic_unit_type_id, parent_id, code, name, status, created_at, updated_at, created_by_user_id, version)
VALUES
  ('${fixture.academicUnitRootId}', '${fixture.academicUnitTypeRootId}', null, 'SCI_${codeSuffix}', 'Faculty of Science', 'ACTIVE', now(), now(), '${userId}', 0),
  ('${fixture.academicUnitLeafId}', '${fixture.academicUnitTypeLeafId}', '${fixture.academicUnitRootId}', 'COMP_${codeSuffix}', 'Department of Computing', 'ACTIVE', now(), now(), '${userId}', 0);
INSERT INTO academic_years (id, name, start_date, end_date, status, created_at, updated_at, created_by_user_id, version)
VALUES ('${fixture.academicYearId}', '${calendarYear}-${codeSuffix}', DATE '${calendarYear}-01-01', DATE '${calendarYear}-12-31', 'OPEN', now(), now(), '${userId}', 0);
INSERT INTO academic_period_types (id, code, name, sort_order, status, created_at, updated_at, created_by_user_id, version)
VALUES ('${fixture.academicPeriodTypeId}', 'SEM_${codeSuffix}', 'Semester', ${uniqueSortOrder}, 'ACTIVE', now(), now(), '${userId}', 0);
INSERT INTO academic_periods (id, academic_year_id, academic_period_type_id, code, name, start_date, end_date, status, created_at, updated_at, created_by_user_id, version)
VALUES ('${fixture.academicPeriodId}', '${fixture.academicYearId}', '${fixture.academicPeriodTypeId}', '${calendarYear}S1_${codeSuffix}', 'Semester 1', DATE '${calendarYear}-01-10', DATE '${calendarYear}-06-20', 'OPEN', now(), now(), '${userId}', 0);
INSERT INTO intakes (id, academic_year_id, code, name, starts_on, ends_on, status, created_at, updated_at, created_by_user_id, version)
VALUES ('${fixture.intakeId}', '${fixture.academicYearId}', 'JAN${calendarYear}_${codeSuffix}', 'January ${calendarYear} Intake', DATE '${calendarYear}-01-01', DATE '${calendarYear}-02-28', 'DRAFT', now(), now(), '${userId}', 0);
INSERT INTO programme_levels (id, code, name, sort_order, status, created_at, updated_at, created_by_user_id, version)
VALUES ('${fixture.programmeLevelId}', 'UG_${codeSuffix}', 'Undergraduate', ${uniqueSortOrder}, 'ACTIVE', now(), now(), '${userId}', 0);
INSERT INTO programme_types (id, code, name, status, created_at, updated_at, created_by_user_id, version)
VALUES ('${fixture.programmeTypeId}', 'DEGREE_${codeSuffix}', 'Degree', 'ACTIVE', now(), now(), '${userId}', 0);
INSERT INTO intake_programme_level_targets (id, intake_id, programme_level_id, created_at, updated_at, created_by_user_id, version)
VALUES (gen_random_uuid(), '${fixture.intakeId}', '${fixture.programmeLevelId}', now(), now(), '${userId}', 0);
UPDATE intakes SET status = 'OPEN', updated_at = now(), version = 1 WHERE id = '${fixture.intakeId}';
INSERT INTO modules (id, owning_academic_unit_id, code, name, description, credit_value, academic_level, status, created_at, updated_at, created_by_user_id, version)
VALUES ('${fixture.moduleId}', '${fixture.academicUnitLeafId}', 'CSC101_${codeSuffix}', 'Programming Fundamentals', 'Foundational programming and problem solving.', 12.00, 1, 'ACTIVE', now(), now(), '${userId}', 0);
INSERT INTO programmes (id, owning_academic_unit_id, programme_type_id, programme_level_id, code, name, award_name, minimum_duration_periods, maximum_duration_periods, status, created_at, updated_at, created_by_user_id, version)
VALUES ('${fixture.programmeId}', '${fixture.academicUnitLeafId}', '${fixture.programmeTypeId}', '${fixture.programmeLevelId}', 'B${codeSuffix.slice(0, 4)}', 'Bachelor of Science in Information Technology', 'Bachelor of Science Honours Degree', 8, 12, 'ACTIVE', now(), now(), '${userId}', 0);
INSERT INTO programme_versions (id, programme_id, version_code, effective_from, status, created_at, updated_at, created_by_user_id, version)
VALUES ('${fixture.programmeVersionId}', '${fixture.programmeId}', '${calendarYear}.1', DATE '${calendarYear}-01-01', 'DRAFT', now(), now(), '${userId}', 0);
INSERT INTO curriculum_modules (id, programme_version_id, module_id, period_number, module_type, credit_value, minimum_mark_required, sort_order, created_at, updated_at, created_by_user_id, version)
VALUES ('${fixture.curriculumModuleId}', '${fixture.programmeVersionId}', '${fixture.moduleId}', 1, 'COMPULSORY', 12.00, 50.00, 1, now(), now(), '${userId}', 0);
UPDATE programme_versions SET status = 'APPROVED', approved_by_user_id = '${userId}', approved_at = now(), updated_at = now(), version = 1 WHERE id = '${fixture.programmeVersionId}';
`)
  return fixture
}

async function cleanupFixture(fixture: AcademicUiFixture | null) {
  if (!fixture) return
  executeSql('emhare_academic_setup', `
BEGIN;
SET LOCAL session_replication_role = replica;
DELETE FROM curriculum_modules_aud WHERE id = '${fixture.curriculumModuleId}';
DELETE FROM programme_versions_aud WHERE id = '${fixture.programmeVersionId}';
DELETE FROM programmes_aud WHERE id = '${fixture.programmeId}';
DELETE FROM modules_aud WHERE id = '${fixture.moduleId}';
DELETE FROM programme_types_aud WHERE id = '${fixture.programmeTypeId}';
DELETE FROM intake_programme_level_targets_aud WHERE intake_id = '${fixture.intakeId}';
DELETE FROM intake_programme_targets_aud WHERE intake_id = '${fixture.intakeId}';
DELETE FROM programme_levels_aud WHERE id = '${fixture.programmeLevelId}';
DELETE FROM intakes_aud WHERE id = '${fixture.intakeId}';
DELETE FROM academic_periods_aud WHERE id = '${fixture.academicPeriodId}';
DELETE FROM academic_period_types_aud WHERE id = '${fixture.academicPeriodTypeId}';
DELETE FROM academic_years_aud WHERE id = '${fixture.academicYearId}';
DELETE FROM academic_units_aud WHERE id IN ('${fixture.academicUnitLeafId}', '${fixture.academicUnitRootId}');
DELETE FROM curriculum_modules WHERE id = '${fixture.curriculumModuleId}';
DELETE FROM programme_versions WHERE id = '${fixture.programmeVersionId}';
DELETE FROM programmes WHERE id = '${fixture.programmeId}';
DELETE FROM modules WHERE id = '${fixture.moduleId}';
DELETE FROM intake_programme_targets WHERE intake_id = '${fixture.intakeId}';
DELETE FROM intake_programme_level_targets WHERE intake_id = '${fixture.intakeId}';
DELETE FROM programme_types WHERE id = '${fixture.programmeTypeId}';
DELETE FROM programme_levels WHERE id = '${fixture.programmeLevelId}';
DELETE FROM intakes WHERE id = '${fixture.intakeId}';
DELETE FROM academic_periods WHERE id = '${fixture.academicPeriodId}';
DELETE FROM academic_period_types WHERE id = '${fixture.academicPeriodTypeId}';
DELETE FROM academic_years WHERE id = '${fixture.academicYearId}';
DELETE FROM academic_units WHERE id IN ('${fixture.academicUnitLeafId}', '${fixture.academicUnitRootId}');
DELETE FROM revinfo revision
WHERE NOT EXISTS (SELECT 1 FROM academic_unit_types_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM academic_units_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM academic_years_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM academic_period_types_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM academic_periods_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM intakes_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM intake_programme_level_targets_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM intake_programme_type_targets_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM intake_programme_targets_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM programme_levels_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM programme_types_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM programmes_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM programme_versions_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM modules_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM curriculum_modules_aud audit WHERE audit.rev = revision.rev);
COMMIT;
`)
  const localUserId = executeSql('emhare_core_identity', `SELECT id FROM users WHERE email = '${fixture.username}';`, true).trim()
  if (localUserId) {
    executeSql('emhare_core_identity', `
DELETE FROM user_role_assignments_aud WHERE user_id = '${localUserId}';
DELETE FROM user_role_assignments WHERE user_id = '${localUserId}';
DELETE FROM login_events_aud WHERE user_id = '${localUserId}';
DELETE FROM login_events WHERE user_id = '${localUserId}';
DELETE FROM users_aud WHERE id = '${localUserId}';
DELETE FROM users WHERE id = '${localUserId}';
`)
  }
  const keycloakAdmin = await createKeycloakAdminRequestContext()
  await keycloakAdmin.delete(`${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users/${fixture.userId}`)
  await keycloakAdmin.dispose()
}

function executeSql(database: string, sql: string, tuplesOnly = false) {
  const argumentsList = ['exec', '-i', postgresContainer, 'psql', '-q', '-v', 'ON_ERROR_STOP=1', '-U', 'postgres', '-d', database]
  if (tuplesOnly) argumentsList.push('-A', '-t')
  const result = spawnSync('docker', argumentsList, { input: sql, encoding: 'utf8' })
  if (result.status !== 0) throw new Error(result.stderr || result.stdout)
  return result.stdout
}

type AcademicCrudFixture = {
  userId: string
  username: string
  academicUnitId: string
  academicUnitRootId: string
  codeSuffix: string
}

type ProgrammeGuidanceFixture = {
  userId: string
  username: string
}

async function createProgrammeGuidanceFixture(): Promise<ProgrammeGuidanceFixture> {
  const runId = randomUUID()
  const username = `programme-guidance-${runId}@example.test`
  const keycloakAdmin = await createKeycloakAdminRequestContext()
  const createUserResponse = await keycloakAdmin.post(`${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users`, {
    data: {
      username, email: username, firstName: 'Programme', lastName: 'Guidance Operator',
      enabled: true, emailVerified: true,
      credentials: [{ type: 'password', value: testPassword, temporary: false }]
    }
  })
  expect(createUserResponse.status()).toBe(201)
  const userId = createUserResponse.headers().location!.split('/').at(-1)!
  const roleResponse = await keycloakAdmin.get(`${keycloakBaseUrl}/admin/realms/${keycloakRealm}/roles/system-admin`)
  expect(roleResponse.ok()).toBeTruthy()
  const assignRoleResponse = await keycloakAdmin.post(
    `${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users/${userId}/role-mappings/realm`,
    { data: [await roleResponse.json()] }
  )
  expect(assignRoleResponse.status()).toBe(204)
  await keycloakAdmin.dispose()
  return { userId, username }
}

async function cleanupProgrammeGuidanceFixture(fixture: ProgrammeGuidanceFixture | null) {
  if (!fixture) return
  const keycloakAdmin = await createKeycloakAdminRequestContext()
  await keycloakAdmin.delete(`${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users/${fixture.userId}`)
  await keycloakAdmin.dispose()
}

async function createCrudFixture(): Promise<AcademicCrudFixture> {
  const runId = randomUUID()
  const codeSuffix = runId.replaceAll('-', '').slice(0, 8).toUpperCase()
  const username = `academic-crud-${runId}@example.test`
  const keycloakAdmin = await createKeycloakAdminRequestContext()
  const createUserResponse = await keycloakAdmin.post(`${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users`, {
    data: {
      username, email: username, firstName: 'Academic', lastName: 'Crud Operator',
      enabled: true, emailVerified: true,
      credentials: [{ type: 'password', value: testPassword, temporary: false }]
    }
  })
  expect(createUserResponse.status()).toBe(201)
  const userId = createUserResponse.headers().location!.split('/').at(-1)!
  const roleResponse = await keycloakAdmin.get(`${keycloakBaseUrl}/admin/realms/${keycloakRealm}/roles/system-admin`)
  expect(roleResponse.ok()).toBeTruthy()
  const assignRoleResponse = await keycloakAdmin.post(
    `${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users/${userId}/role-mappings/realm`,
    { data: [await roleResponse.json()] }
  )
  expect(assignRoleResponse.status()).toBe(204)
  await keycloakAdmin.dispose()

  const academicUnitTypeRootId = executeSql(
    'emhare_academic_setup',
    "SELECT id FROM academic_unit_types WHERE code = 'FACULTY' AND deleted_at IS NULL;",
    true
  ).trim()
  const academicUnitTypeLeafId = executeSql(
    'emhare_academic_setup',
    "SELECT id FROM academic_unit_types WHERE code = 'DEPARTMENT' AND deleted_at IS NULL;",
    true
  ).trim()
  if (!academicUnitTypeRootId || !academicUnitTypeLeafId) {
    throw new Error('Canonical FACULTY and DEPARTMENT academic unit types are required for the CRUD fixture.')
  }
  const academicUnitRootId = randomUUID()
  const academicUnitId = randomUUID()
  executeSql('emhare_academic_setup', `
INSERT INTO academic_units (id, academic_unit_type_id, parent_id, code, name, status, created_at, updated_at, created_by_user_id, version)
VALUES
  ('${academicUnitRootId}', '${academicUnitTypeRootId}', null, 'CRF_${codeSuffix}', 'Faculty of Crud Testing', 'ACTIVE', now(), now(), '${userId}', 0),
  ('${academicUnitId}', '${academicUnitTypeLeafId}', '${academicUnitRootId}', 'CRD_${codeSuffix}', 'Department of Crud Operations', 'ACTIVE', now(), now(), '${userId}', 0);
`)
  return { userId, username, academicUnitId, academicUnitRootId, codeSuffix }
}

type CrudCreatedIds = {
  curriculumModuleId?: string
  programmeVersionId?: string
  programmeId?: string
  moduleId?: string
  programmeLevelId?: string
  programmeTypeId?: string
}

async function cleanupCrudFixture(fixture: AcademicCrudFixture | null, created: CrudCreatedIds) {
  if (!fixture) return
  executeSql('emhare_academic_setup', `
BEGIN;
SET LOCAL session_replication_role = replica;
${created.curriculumModuleId ? `DELETE FROM curriculum_modules_aud WHERE id = '${created.curriculumModuleId}';` : ''}
${created.programmeVersionId ? `DELETE FROM programme_versions_aud WHERE id = '${created.programmeVersionId}';` : ''}
${created.programmeId ? `DELETE FROM programmes_aud WHERE id = '${created.programmeId}';` : ''}
${created.moduleId ? `DELETE FROM modules_aud WHERE id = '${created.moduleId}';` : ''}
${created.programmeTypeId ? `DELETE FROM programme_types_aud WHERE id = '${created.programmeTypeId}';` : ''}
${created.programmeLevelId ? `DELETE FROM programme_levels_aud WHERE id = '${created.programmeLevelId}';` : ''}
DELETE FROM academic_units_aud WHERE id IN ('${fixture.academicUnitId}', '${fixture.academicUnitRootId}');
${created.curriculumModuleId ? `DELETE FROM curriculum_modules WHERE id = '${created.curriculumModuleId}';` : ''}
${created.programmeVersionId ? `DELETE FROM programme_versions WHERE id = '${created.programmeVersionId}';` : ''}
${created.programmeId ? `DELETE FROM programmes WHERE id = '${created.programmeId}';` : ''}
${created.moduleId ? `DELETE FROM modules WHERE id = '${created.moduleId}';` : ''}
${created.programmeTypeId ? `DELETE FROM programme_types WHERE id = '${created.programmeTypeId}';` : ''}
${created.programmeLevelId ? `DELETE FROM programme_levels WHERE id = '${created.programmeLevelId}';` : ''}
DELETE FROM academic_units WHERE id IN ('${fixture.academicUnitId}', '${fixture.academicUnitRootId}');
DELETE FROM revinfo revision
WHERE NOT EXISTS (SELECT 1 FROM academic_units_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM programme_levels_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM programme_types_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM programmes_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM programme_versions_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM modules_aud audit WHERE audit.rev = revision.rev)
  AND NOT EXISTS (SELECT 1 FROM curriculum_modules_aud audit WHERE audit.rev = revision.rev);
COMMIT;
`)
  const localUserId = executeSql('emhare_core_identity', `SELECT id FROM users WHERE email = '${fixture.username}';`, true).trim()
  if (localUserId) {
    executeSql('emhare_core_identity', `
DELETE FROM user_role_assignments_aud WHERE user_id = '${localUserId}';
DELETE FROM user_role_assignments WHERE user_id = '${localUserId}';
DELETE FROM login_events_aud WHERE user_id = '${localUserId}';
DELETE FROM login_events WHERE user_id = '${localUserId}';
DELETE FROM users_aud WHERE id = '${localUserId}';
DELETE FROM users WHERE id = '${localUserId}';
`)
  }
  const keycloakAdmin = await createKeycloakAdminRequestContext()
  await keycloakAdmin.delete(`${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users/${fixture.userId}`)
  await keycloakAdmin.dispose()
}

async function loginWithKeycloak(page: Page, fixture: AcademicUiFixture) {
  await page.locator('#username').fill(fixture.username)
  await page.locator('#password').fill(testPassword)
  await page.locator('#kc-login').click()
  await page.waitForURL(/http:\/\/localhost:3000\/operations\/academic-structure.*/, { timeout: 30_000 })
  await page.waitForLoadState('networkidle')
}

test.describe('Academic Setup operational UI', () => {
  test('guides intake creation through details, eligibility, and opening review', async ({ page }) => {
    let fixture: AcademicUiFixture | null = null
    try {
      fixture = await createFixture()
      await page.goto('/operations/academic-structure')
      await loginWithKeycloak(page, fixture)
      await page.goto('/operations/academic-calendar')
      await page.getByRole('tab', { name: /Intakes/ }).click()
      await page.getByRole('button', { name: 'Create intake' }).click()

      await expect(page).toHaveURL(/\/operations\/academic-calendar\/intakes\/new$/)
      await expect(page.getByRole('dialog', { name: 'Create and open intake' })).toHaveCount(0)
      const intakeWorkspace = page.getByTestId('intake-setup-workspace')
      await expect(intakeWorkspace.getByText('Step 1 of 5', { exact: true })).toBeVisible()
      await expect(intakeWorkspace.getByLabel('Programme Levels')).toHaveCount(0)
      await intakeWorkspace.getByLabel('Academic year').click()
      await page.getByRole('option', { name: fixture.academicYearName }).click()
      await intakeWorkspace.getByLabel('Intake code').fill(`FEB_${fixture.calendarYear}`)
      await intakeWorkspace.getByLabel('Applicant-facing name').fill(`February ${fixture.calendarYear} Intake`)
      await intakeWorkspace.getByLabel('Applications open').fill(`${fixture.calendarYear}-03-01`)
      await intakeWorkspace.getByLabel('Applications close').fill(`${fixture.calendarYear}-04-30`)
      await intakeWorkspace.getByRole('button', { name: 'Continue to eligibility' }).click()

      await expect(intakeWorkspace.getByText('Step 2 of 5', { exact: true })).toBeVisible()
      await intakeWorkspace.getByLabel('Programme Levels').click()
      await page.getByRole('option', { name: new RegExp(`UG_${fixture.codeSuffix}`) }).click()
      await page.keyboard.press('Escape')
      await intakeWorkspace.getByRole('button', { name: 'Continue to routes and fees' }).click()

      await expect(intakeWorkspace.getByText('Step 3 of 5', { exact: true })).toBeVisible()
      await intakeWorkspace.getByLabel('UNDERGRAD Programmes').click()
      await page.getByRole('option', { name: new RegExp(`B${fixture.codeSuffix.slice(0, 4)}`) }).click()
      await page.keyboard.press('Escape')
      await intakeWorkspace.getByRole('button', { name: 'Continue to Programme quotas' }).click()

      await expect(intakeWorkspace.getByText('Step 4 of 5', { exact: true })).toBeVisible()
      await intakeWorkspace.getByLabel(`B${fixture.codeSuffix.slice(0, 4)} total capacity`).fill('80')
      await intakeWorkspace.getByRole('button', { name: 'Review admissions opening' }).click()

      await expect(intakeWorkspace.getByText('Step 5 of 5', { exact: true })).toBeVisible()
      await expect(intakeWorkspace.getByRole('heading', { name: 'Review and open applications' })).toBeVisible()
      await expect(intakeWorkspace.getByText(`February ${fixture.calendarYear} Intake`, { exact: true })).toBeVisible()
      await intakeWorkspace.getByLabel('Opening reason').fill('Configured all admission opening controls for browser verification.')
      await expect(intakeWorkspace.getByRole('button', { name: 'Save draft' })).toBeVisible()
      await expect(intakeWorkspace.getByRole('button', { name: 'Create and open intake' })).toBeVisible()

      const createdIntakeId = randomUUID()
      const openingRequests: string[] = []
      const intakeResponse = (status: 'DRAFT' | 'OPEN', version: number) => ({
        id: createdIntakeId,
        academicYearId: fixture!.academicYearId,
        academicYearName: fixture!.academicYearName,
        code: `FEB_${fixture!.calendarYear}`,
        name: `February ${fixture!.calendarYear} Intake`,
        startsOn: `${fixture!.calendarYear}-03-01`,
        endsOn: `${fixture!.calendarYear}-04-30`,
        status,
        maximumProgrammeChoices: 3,
        changeReason: 'Configured all admission opening controls for browser verification.',
        programmeLevels: [{ id: fixture!.programmeLevelId, code: `UG_${fixture!.codeSuffix}`, name: 'Undergraduate' }],
        specificProgrammes: [],
        allProgrammesInSelectedLevels: true,
        version
      })
      await page.route('**/api/academic/intakes', async route => {
        if (route.request().method() !== 'POST') return route.continue()
        openingRequests.push('create intake')
        await route.fulfill({ json: intakeResponse('DRAFT', 0) })
      })
      await page.route(`**/api/academic/intakes/${createdIntakeId}/open`, async route => {
        openingRequests.push('open intake')
        await route.fulfill({ json: intakeResponse('OPEN', 1) })
      })
      await page.route('**/api/admissions/application-types/*/route-configuration', async route => {
        if (route.request().method() !== 'PUT') return route.continue()
        openingRequests.push('configure routes and fees')
        const request = route.request().postDataJSON()
        await route.fulfill({ json: {
          applicationTypeId: route.request().url().split('/').at(-2),
          code: 'UNDERGRAD',
          name: 'Undergraduate',
          active: true,
          readyForActivation: true,
          readinessBlockers: [],
          programmes: request.programmes,
          sections: request.sections,
          documents: request.documents,
          feePolicyStatus: 'FEE_STRUCTURE',
          version: 1
        } })
      })
      await page.route(`**/api/admissions/intakes/${createdIntakeId}/programme-quotas`, async route => {
        openingRequests.push('configure Programme quotas')
        await route.fulfill({ json: [] })
      })
      await intakeWorkspace.getByRole('button', { name: 'Create and open intake' }).click()
      await expect(page.getByText(`February ${fixture.calendarYear} Intake configured and opened`, { exact: true })).toBeVisible()
      expect(openingRequests).toEqual([
        'create intake',
        'configure routes and fees',
        'configure Programme quotas',
        'open intake'
      ])
    } finally {
      await cleanupFixture(fixture)
    }
  })

  test('submits the visible change reason when correcting a published intake', async ({ page }) => {
    let fixture: AcademicUiFixture | null = null
    try {
      fixture = await createFixture()
      await page.goto('/operations/academic-structure')
      await loginWithKeycloak(page, fixture)

      await page.route('**/api/academic/overview', async (route) => {
        const response = await route.fetch()
        const overview = await response.json()
        await route.fulfill({ response, json: {
          ...overview,
          intakes: overview.intakes.filter((intake: { id: string }) => intake.id === fixture!.intakeId)
        } })
      })

      const correctionReason = 'Corrected the published intake dates after the approved calendar review.'
      let submittedCorrection: Record<string, unknown> | null = null
      await page.route(`**/api/academic/intakes/${fixture.intakeId}`, async (route) => {
        if (route.request().method() !== 'PUT') return route.continue()
        submittedCorrection = route.request().postDataJSON()
        await route.fulfill({ json: {
          id: fixture!.intakeId,
          academicYearId: fixture!.academicYearId,
          academicYearName: fixture!.academicYearName,
          code: `JAN${fixture!.calendarYear}_${fixture!.codeSuffix}`,
          name: `January ${fixture!.calendarYear} Intake`,
          startsOn: `${fixture!.calendarYear}-01-01`,
          endsOn: `${fixture!.calendarYear}-02-28`,
          status: 'OPEN',
          maximumProgrammeChoices: 3,
          changeReason: correctionReason,
          programmeLevels: [{ id: fixture!.programmeLevelId, code: `UG_${fixture!.codeSuffix}`, name: 'Undergraduate' }],
          specificProgrammes: [],
          allProgrammesInSelectedLevels: true,
          version: 2
        } })
      })

      await page.goto('/operations/academic-calendar')
      await page.getByRole('tab', { name: /Intakes/ }).click()
      const intakeRow = page.getByRole('row').filter({ hasText: `January ${fixture.calendarYear} Intake` })
      await intakeRow.getByRole('button', { name: 'Edit' }).click()
      const editIntakeDrawer = page.getByRole('dialog', { name: 'Edit intake' })
      await editIntakeDrawer.getByLabel('Change reason').fill(correctionReason)
      await editIntakeDrawer.getByRole('button', { name: 'Save changes' }).click()

      await expect(page.getByText('Intake updated', { exact: true })).toBeVisible()
      expect(submittedCorrection).toMatchObject({
        changeReason: correctionReason,
        expectedVersion: 1
      })
      await expect(page.locator('.swal2-popup')).toHaveCount(0)
    } finally {
      await cleanupFixture(fixture)
    }
  })

  test('shows the academic period switcher only on scoped pages and filters admissions by the selected period', async ({ page }) => {
    let fixture: AcademicUiFixture | null = null
    try {
      fixture = await createFixture()
      const firstAcademicYearId = randomUUID()
      const secondAcademicYearId = randomUUID()
      const firstAcademicPeriodId = randomUUID()
      const secondAcademicPeriodId = randomUUID()
      const firstIntakeId = randomUUID()
      const secondIntakeId = randomUUID()
      const firstApplicationId = randomUUID()
      const secondApplicationId = randomUUID()

      await page.route('**/api/academic/overview', async (route) => {
        const response = await route.fetch()
        const overview = await response.json()
        await route.fulfill({ response, json: {
          ...overview,
          academicYears: [
            { id: firstAcademicYearId, name: '2026', startDate: '2026-01-01', endDate: '2026-06-30', status: 'OPEN', changeReason: 'Browser fixture', version: 0 },
            { id: secondAcademicYearId, name: '2027', startDate: '2026-07-01', endDate: '2026-12-31', status: 'OPEN', changeReason: 'Browser fixture', version: 0 }
          ],
          academicPeriods: [
            { id: firstAcademicPeriodId, academicYearId: firstAcademicYearId, academicYearName: '2026', academicPeriodTypeId: randomUUID(), academicPeriodTypeName: 'Semester', code: '2026-S1', name: 'Semester 1', startDate: '2026-01-01', endDate: '2026-06-30', status: 'OPEN', changeReason: 'Browser fixture', version: 0 },
            { id: secondAcademicPeriodId, academicYearId: secondAcademicYearId, academicYearName: '2027', academicPeriodTypeId: randomUUID(), academicPeriodTypeName: 'Semester', code: '2027-S1', name: 'Semester 1', startDate: '2026-07-01', endDate: '2026-12-31', status: 'OPEN', changeReason: 'Browser fixture', version: 0 }
          ],
          intakes: [
            { id: firstIntakeId, academicYearId: firstAcademicYearId, code: 'JAN-2026', name: 'January 2026', startsOn: '2025-08-01', endsOn: '2025-12-31', status: 'OPEN', maximumProgrammeChoices: 3, changeReason: 'Browser fixture', version: 0 },
            { id: secondIntakeId, academicYearId: secondAcademicYearId, code: 'JAN-2027', name: 'January 2027', startsOn: '2026-08-01', endsOn: '2026-12-31', status: 'OPEN', maximumProgrammeChoices: 3, changeReason: 'Browser fixture', version: 0 }
          ]
        } })
      })
      const application = (id: string, applicationNumber: string, intakeId: string, intakeCode: string) => ({
        id, applicationNumber, applicantNumber: `APP-${applicationNumber.slice(-4)}`, applicantName: `Applicant ${applicationNumber.slice(-4)}`,
        intakeId, intakeCode, applicationTypeId: randomUUID(), applicationTypeName: 'Undergraduate',
        status: 'SUBMITTED', paymentRequired: false, paymentClearanceStatus: 'NOT_REQUIRED', paymentWaiverReason: null,
        canSubmit: false, canEnterReview: true, calculatedTotalPoints: null, pointsCalculatedAt: null, payment: null, programmeChoices: []
      })
      await page.route('**/api/admissions/applications', route => route.fulfill({ json: [
        application(firstApplicationId, 'EMH-PERIOD-2026', firstIntakeId, 'JAN-2026'),
        application(secondApplicationId, 'EMH-PERIOD-2027', secondIntakeId, 'JAN-2027')
      ] }))
      await page.route('**/api/assessment-results/offerings', route => route.fulfill({ json: [
        { id: randomUUID(), moduleId: randomUUID(), moduleCode: 'CSC-2026', moduleName: 'Period One Module', academicPeriodId: firstAcademicPeriodId, academicPeriodCode: '2026-S1', academicPeriodName: 'Semester 1', assignedInstructorUserId: fixture!.userId, status: 'ACTIVE', version: 0, rosterCount: 12, schemes: [] },
        { id: randomUUID(), moduleId: randomUUID(), moduleCode: 'CSC-2027', moduleName: 'Period Two Module', academicPeriodId: secondAcademicPeriodId, academicPeriodCode: '2027-S1', academicPeriodName: 'Semester 1', assignedInstructorUserId: fixture!.userId, status: 'ACTIVE', version: 0, rosterCount: 15, schemes: [] }
      ] }))
      await page.route('**/api/assessment-results/roster-sources', route => route.fulfill({ json: [] }))

      await page.goto('/operations/academic-structure')
      await loginWithKeycloak(page, fixture)
      await expect(page.getByTestId('academic-period-switcher')).toHaveCount(0)

      await page.goto('/operations/admissions')
      const switcher = page.getByTestId('academic-period-switcher')
      await expect(switcher).toBeVisible()
      await switcher.click()
      await page.getByRole('menuitem', { name: /2026 · Semester 1/ }).click()
      await expect(page.getByText('EMH-PERIOD-2026', { exact: true })).toBeVisible()
      await expect(page.getByText('EMH-PERIOD-2027', { exact: true })).toHaveCount(0)

      await switcher.click()
      await page.getByRole('menuitem', { name: /2027 · Semester 1/ }).click()
      await expect(page.getByText('EMH-PERIOD-2027', { exact: true })).toBeVisible()
      await expect(page.getByText('EMH-PERIOD-2026', { exact: true })).toHaveCount(0)

      await page.goto('/operations/assessment-schemes')
      await expect(page.getByText('CSC-2027 · Period Two Module', { exact: true })).toBeVisible()
      await expect(page.getByText('CSC-2026 · Period One Module', { exact: true })).toHaveCount(0)
      await switcher.click()
      await page.getByRole('menuitem', { name: /2026 · Semester 1/ }).click()
      await expect(page.getByText('CSC-2026 · Period One Module', { exact: true })).toBeVisible()
      await expect(page.getByText('CSC-2027 · Period Two Module', { exact: true })).toHaveCount(0)
    } finally {
      await cleanupFixture(fixture)
    }
  })

  test('renders governed structure, calendar, catalogues, and approved curriculum', async ({ page }, testInfo) => {
    let fixture: AcademicUiFixture | null = null
    try {
      fixture = await createFixture()
      const consoleErrors: string[] = []
      const pageErrors: string[] = []
      page.on('console', message => message.type() === 'error' && consoleErrors.push(message.text()))
      page.on('pageerror', error => pageErrors.push(error.message))

      await page.route('**/api/academic/overview', async (route) => {
        const response = await route.fetch()
        if (!response.ok()) {
          await route.fulfill({ response })
          return
        }
        const overview = await response.json()
        await route.fulfill({
          response,
          json: {
            ...overview,
            intakes: overview.intakes.map((intake: Record<string, unknown>) => {
              const legacyIntake = { ...intake }
              delete legacyIntake.programmeLevels
              delete legacyIntake.specificProgrammes
              delete legacyIntake.allProgrammesInSelectedLevels
              return legacyIntake
            })
          }
        })
      })

      await page.goto('/operations/academic-structure')
      await loginWithKeycloak(page, fixture)
      await expect(page.getByRole('heading', { name: 'Academic structure' })).toBeVisible()
      await expect(page.getByText('Faculty of Science', { exact: true }).first()).toBeVisible()
      await expect(page.getByText('Department of Computing', { exact: true }).first()).toBeVisible()
      const academicPeriodSwitcher = page.getByTestId('academic-period-switcher')
      const fixtureAcademicPeriodLabel = `${fixture.academicYearName} · Semester 1`
      await expect(academicPeriodSwitcher).toHaveCount(0)
      await page.screenshot({ path: testInfo.outputPath('academic-structure.png'), fullPage: true })

      await page.goto('/operations/admissions')
      await expect(page.getByRole('heading', { name: 'Admissions', exact: true })).toBeVisible()
      await academicPeriodSwitcher.click()
      await page.getByRole('menuitem', { name: new RegExp(fixtureAcademicPeriodLabel) }).click()
      await expect(academicPeriodSwitcher).toContainText(fixtureAcademicPeriodLabel)
      await page.reload()
      await expect(academicPeriodSwitcher).toContainText(fixtureAcademicPeriodLabel)

      await page.goto('/operations/academic-calendar')
      await expect(page.getByRole('heading', { name: 'Academic calendar' })).toBeVisible()
      await expect(academicPeriodSwitcher).toHaveCount(0)
      await page.getByRole('tab', { name: /Intakes/ }).click()
      await expect(page.getByText(`January ${fixture.calendarYear} Intake`, { exact: true })).toBeVisible()
      await expect(page.getByText('0 Programme Levels', { exact: true }).first()).toBeVisible()
      expect(pageErrors).toEqual([])
      await page.unroute('**/api/academic/overview')
      await page.getByRole('button', { name: 'Refresh' }).click()
      await expect(page.getByText('1 Programme Level', { exact: true }).first()).toBeVisible()
      await page.getByRole('tab', { name: /Academic years/ }).click()
      const academicYearRowBeforeValidation = page.getByRole('row').filter({ hasText: fixture.academicYearName })
      await academicYearRowBeforeValidation.getByRole('button', { name: 'Edit' }).click()
      const editAcademicYearDrawer = page.getByRole('dialog', { name: 'Edit academic year' })
      await expect(editAcademicYearDrawer).toBeVisible()
      const [invalidAcademicYearResponse] = await Promise.all([
        page.waitForResponse(response =>
          response.url().endsWith(`/api/academic/years/${fixture.academicYearId}`)
          && response.request().method() === 'PUT'
        ),
        editAcademicYearDrawer.getByRole('button', { name: 'Save changes' }).click()
      ])
      expect(invalidAcademicYearResponse.status()).toBe(400)
      const academicYearValidationAlert = page.locator('.swal2-popup')
      await expect(academicYearValidationAlert).toContainText('Academic year could not be updated')
      await expect(academicYearValidationAlert).toContainText('Change Reason:')
      await academicYearValidationAlert.getByRole('button', { name: 'OK' }).click()
      await expect(academicYearValidationAlert).not.toBeVisible()
      await expect(editAcademicYearDrawer).toBeVisible()
      await editAcademicYearDrawer.getByRole('button', { name: 'Cancel' }).click()
      consoleErrors.length = 0

      await page.getByRole('tab', { name: /Intakes/ }).click()
      await page.getByRole('button', { name: 'Create intake' }).click()
      const intakeWorkspace = page.getByTestId('intake-setup-workspace')
      await expect(intakeWorkspace.getByText('Step 1 of 5', { exact: true })).toBeVisible()
      await expect(intakeWorkspace.getByLabel('Programme Levels')).toHaveCount(0)
      await expect(intakeWorkspace.getByText('Letters, numbers, hyphens, and underscores only.', { exact: true })).toBeVisible()
      await intakeWorkspace.getByLabel('Academic year').click()
      await page.getByRole('option', { name: fixture.academicYearName }).click()
      await intakeWorkspace.getByLabel('Intake code').fill(`FEB ${fixture.calendarYear}`)
      await intakeWorkspace.getByLabel('Applicant-facing name').fill(`February ${fixture.calendarYear} Intake`)
      await intakeWorkspace.getByLabel('Applications open').fill(`${fixture.calendarYear}-02-01`)
      await intakeWorkspace.getByLabel('Applications close').fill(`${fixture.calendarYear}-02-28`)
      await intakeWorkspace.getByRole('button', { name: 'Continue to eligibility' }).click()
      await expect(intakeWorkspace.getByText('Step 2 of 5', { exact: true })).toBeVisible()
      await intakeWorkspace.getByLabel('Programme Levels').click()
      await page.getByRole('option', { name: new RegExp(`UG_${fixture.codeSuffix}`) }).click()
      await page.keyboard.press('Escape')
      await intakeWorkspace.getByLabel('Specific Programmes').click()
      await page.getByRole('option', { name: new RegExp(`B${fixture.codeSuffix.slice(0, 4)}`) }).click()
      await page.keyboard.press('Escape')
      await expect(intakeWorkspace.getByText('Specific Programme whitelist', { exact: true })).toBeVisible()
      await intakeWorkspace.getByRole('button', { name: 'Continue to routes and fees' }).click()
      await expect(intakeWorkspace.getByText('Step 3 of 5', { exact: true })).toBeVisible()
      await intakeWorkspace.getByLabel('UNDERGRAD Programmes').click()
      await page.getByRole('option', { name: new RegExp(`B${fixture.codeSuffix.slice(0, 4)}`) }).click()
      await page.keyboard.press('Escape')
      await intakeWorkspace.getByRole('button', { name: 'Continue to Programme quotas' }).click()
      await expect(intakeWorkspace.getByText('Step 4 of 5', { exact: true })).toBeVisible()
      await intakeWorkspace.getByLabel(`B${fixture.codeSuffix.slice(0, 4)} total capacity`).fill('80')
      await intakeWorkspace.getByRole('button', { name: 'Review admissions opening' }).click()
      await expect(intakeWorkspace.getByText('Step 5 of 5', { exact: true })).toBeVisible()
      await expect(intakeWorkspace.getByRole('heading', { name: 'Review and open applications' })).toBeVisible()
      await intakeWorkspace.getByLabel('Opening reason').fill('Configured the complete intake opening for validation testing.')
      const [invalidIntakeResponse] = await Promise.all([
        page.waitForResponse(response =>
          response.url().endsWith('/api/academic/intakes') && response.request().method() === 'POST'
        ),
        intakeWorkspace.getByRole('button', { name: 'Save draft' }).click()
      ])
      expect(invalidIntakeResponse.status()).toBe(400)
      expect(invalidIntakeResponse.request().postDataJSON().programmeLevelIds).toEqual([fixture.programmeLevelId])
      expect(invalidIntakeResponse.request().postDataJSON().programmeIds).toEqual([fixture.programmeId])
      const intakeValidationAlert = page.locator('.swal2-popup')
      await expect(intakeValidationAlert).toContainText('Intake could not be created')
      await expect(intakeValidationAlert).toContainText(
        'Code: Use only letters, numbers, hyphens, and underscores; spaces are not allowed.'
      )
      await intakeValidationAlert.getByRole('button', { name: 'OK' }).click()
      await createIntakeDrawer.getByRole('button', { name: 'Cancel' }).click()
      consoleErrors.length = 0

      const initialIntakeName = `January ${fixture.calendarYear} Intake`
      await expect(page.getByText(initialIntakeName, { exact: true })).toBeVisible()
      const correctedIntakeName = `${initialIntakeName} — corrected`
      const intakeRowBeforeCorrection = page.getByRole('row').filter({ hasText: initialIntakeName })
      await intakeRowBeforeCorrection.getByRole('button', { name: 'Edit' }).click()
      const editIntakeDrawer = page.getByRole('dialog')
      await expect(editIntakeDrawer.getByRole('heading', { name: 'Edit intake' })).toBeVisible()
      await expect(editIntakeDrawer.getByLabel('Academic year')).toBeDisabled()
      await expect(editIntakeDrawer.getByLabel('Code')).toBeDisabled()
      await expect(editIntakeDrawer.getByLabel('Programme Levels')).toBeDisabled()
      await expect(editIntakeDrawer.getByLabel('Specific Programmes')).toBeDisabled()
      await editIntakeDrawer.getByLabel('Name').fill(correctedIntakeName)
      await editIntakeDrawer.getByLabel('Change reason').fill('Corrected the published intake display name.')
      await editIntakeDrawer.getByRole('button', { name: 'Save changes' }).click()
      await expect(page.getByText(correctedIntakeName, { exact: true })).toBeVisible()
      expect(executeSql(
        'emhare_academic_setup',
        `SELECT change_reason FROM intakes WHERE id = '${fixture.intakeId}';`,
        true
      ).trim()).toBe('Corrected the published intake display name.')
      expect(executeSql(
        'emhare_academic_setup',
        `SELECT change_reason FROM intakes_aud WHERE id = '${fixture.intakeId}' ORDER BY rev DESC LIMIT 1;`,
        true
      ).trim()).toBe('Corrected the published intake display name.')
      await page.getByRole('tab', { name: /Academic periods/ }).click()
      const academicPeriodRow = page.getByRole('row').filter({
        hasText: `${fixture.calendarYear}S1_${fixture.codeSuffix}`
      })
      await academicPeriodRow.getByRole('button', { name: 'Close' }).click()
      await page.getByRole('button', { name: 'Close academic period' }).click()
      await expect(academicPeriodRow.getByText('CLOSED', { exact: true })).toBeVisible()
      await page.getByRole('tab', { name: /Intakes/ }).click()
      const intakeRow = page.getByRole('row').filter({ hasText: correctedIntakeName })
      await intakeRow.getByRole('button', { name: 'Close' }).click()
      await page.getByRole('button', { name: 'Close intake' }).click()
      await expect(intakeRow.getByText('CLOSED', { exact: true })).toBeVisible()
      await page.getByRole('tab', { name: /Academic years/ }).click()
      const academicYearRow = page.getByRole('row').filter({ hasText: fixture.academicYearName })
      await academicYearRow.getByRole('button', { name: 'Close' }).click()
      await page.getByRole('button', { name: 'Close year' }).click()
      await expect(academicYearRow.getByText('CLOSED', { exact: true }).first()).toBeVisible()

      await page.goto('/operations/modules')
      await expect(page.getByRole('heading', { name: 'Module catalogue' })).toBeVisible()
      await expect(page.getByText('Programming Fundamentals', { exact: true }).first()).toBeVisible()

      await page.goto('/operations/programmes')
      await expect(page.getByRole('heading', { name: 'Programme catalogue' })).toBeVisible()
      await expect(page.getByText('Bachelor of Science in Information Technology', { exact: true }).first()).toBeVisible()

      await page.goto(`/operations/curriculum?programmeId=${fixture.programmeId}`)
      await expect(page.getByRole('heading', { name: 'Curriculum versions' })).toBeVisible()
      await expect(page.getByText('Approved curriculum remains amendable', { exact: true })).toBeVisible()
      await expect(page.getByText('Year 1 · Semester 1', { exact: true })).toBeVisible()
      await expect(page.getByText('Programming Fundamentals', { exact: true }).first()).toBeVisible()
      await page.screenshot({ path: testInfo.outputPath('approved-curriculum.png'), fullPage: true })

      expect(consoleErrors).toEqual([])
      expect(pageErrors).toEqual([])
    } finally {
      await cleanupFixture(fixture)
    }
  })

  test('keeps setup actions available and explains missing programme and Module prerequisites', async ({ page }) => {
    let fixture: ProgrammeGuidanceFixture | null = null
    let programmeId = ''
    try {
      fixture = await createProgrammeGuidanceFixture()
      await page.route('**/api/academic/overview', async (route) => {
        const response = await route.fetch()
        const overview = await response.json()
        programmeId = overview.programmes[0]?.id ?? ''
        await route.fulfill({ response, json: { ...overview, academicUnits: [], modules: [] } })
      })
      await page.route('**/api/academic/programmes/*/versions', route => route.fulfill({
        json: [{
          id: '00000000-0000-4000-8000-000000000101',
          programmeId,
          versionCode: 'GUIDANCE.1',
          effectiveFrom: '2026-08-09',
          effectiveTo: null,
          status: 'DRAFT',
          approvedByUserId: null,
          approvedAt: null,
          curriculumModuleCount: 0,
          version: 0
        }]
      }))
      await page.route('**/api/academic/programme-versions/*/curriculum', route => route.fulfill({ json: [] }))

      await page.goto('/operations/programmes')
      await page.locator('#username').fill(fixture.username)
      await page.locator('#password').fill(testPassword)
      await page.locator('#kc-login').click()
      await page.waitForURL(/http:\/\/localhost:3000\/operations\/programmes.*/, { timeout: 30_000 })
      await page.waitForLoadState('networkidle')

      const newProgrammeButton = page.getByRole('button', { name: 'New programme' })
      await expect(newProgrammeButton).toBeEnabled()
      const setupGuidance = page.getByText('Programme setup required', { exact: true })
      await expect(setupGuidance).toBeVisible()
      await expect(setupGuidance.locator('..')).toContainText('Create an active academic unit using a hierarchy level that can own academic records')

      await newProgrammeButton.click()
      const guidanceDialog = page.locator('.swal2-popup')
      await expect(guidanceDialog).toContainText('Complete programme setup')
      await expect(guidanceDialog).toContainText('keep the unit without child units.')
      await Promise.all([
        page.waitForURL(/\/operations\/academic-structure/),
        guidanceDialog.getByRole('button', { name: 'Open academic structure' }).click()
      ])

      expect(programmeId).not.toBe('')
      await page.goto(`/operations/curriculum?programmeId=${programmeId}`)
      await expect(page.getByRole('heading', { name: 'Curriculum versions' })).toBeVisible()
      await expect(page.getByText('No Module is currently available to add', { exact: true })).toBeVisible()
      await expect(page.getByText('Create and activate at least one Module in the Module catalogue.', { exact: true }).first()).toBeVisible()

      const addModuleButton = page.getByRole('button', { name: 'Add Module', exact: true })
      await expect(addModuleButton).toBeEnabled()
      await addModuleButton.click()
      const moduleGuidanceDialog = page.locator('.swal2-popup')
      await expect(moduleGuidanceDialog).toContainText('Module setup required')
      await expect(moduleGuidanceDialog).toContainText('Create and activate at least one Module')
      await Promise.all([
        page.waitForURL(/\/operations\/modules/),
        moduleGuidanceDialog.getByRole('button', { name: 'Open Modules' }).click()
      ])
    } finally {
      await cleanupProgrammeGuidanceFixture(fixture)
    }
  })

  test('creates a Module, a programme, and its curriculum mapping end-to-end through the operator UI', async ({ page }) => {
    let fixture: AcademicCrudFixture | null = null
    const created: CrudCreatedIds = {}
    try {
      fixture = await createCrudFixture()
      const consoleErrors: string[] = []
      const pageErrors: string[] = []
      page.on('console', message => message.type() === 'error' && consoleErrors.push(message.text()))
      page.on('pageerror', error => pageErrors.push(error.message))

      const academicUnitOptionPattern = new RegExp(`CRD_${fixture.codeSuffix}`)
      const moduleCode = `CRD${fixture.codeSuffix}`
      const moduleName = `Crud Test Module ${fixture.codeSuffix}`
      const levelCode = `LVL${fixture.codeSuffix}`
      const levelName = `Crud Level ${fixture.codeSuffix}`
      const typeCode = `TYP${fixture.codeSuffix}`
      const typeName = `Crud Type ${fixture.codeSuffix}`
      const programmeCode = `P${fixture.codeSuffix.slice(0, 4)}`
      const programmeName = `Crud Test Programme ${fixture.codeSuffix}`
      const versionCode = `${fixture.codeSuffix}.1`

      // --- Module CRUD ---
      await page.goto('/operations/modules')
      await page.locator('#username').fill(fixture.username)
      await page.locator('#password').fill(testPassword)
      await page.locator('#kc-login').click()
      await page.waitForURL(/http:\/\/localhost:3000\/operations\/modules.*/, { timeout: 30_000 })
      await page.waitForLoadState('networkidle')
      await expect(page.getByRole('heading', { name: 'Module catalogue' })).toBeVisible()

      await page.getByRole('button', { name: 'New Module' }).click()
      const moduleDrawer = page.getByRole('dialog')
      await expect(moduleDrawer.getByRole('heading', { name: 'Create Module' })).toBeVisible()
      await moduleDrawer.getByLabel('Owning academic unit').click()
      await page.getByRole('option', { name: academicUnitOptionPattern }).click()
      await moduleDrawer.getByLabel('Module code').fill(moduleCode)
      await moduleDrawer.getByLabel('Academic level').fill('2')
      await moduleDrawer.getByLabel('Module name').fill(moduleName)
      await moduleDrawer.getByLabel('Description').fill('Created by the Playwright Programmes/Modules/Curriculum CRUD test.')
      await moduleDrawer.getByLabel('Credit value').fill('15')
      const [moduleCreateResponse] = await Promise.all([
        page.waitForResponse(response =>
          response.url().endsWith('/api/academic/modules') && response.request().method() === 'POST'),
        moduleDrawer.getByRole('button', { name: 'Create draft Module' }).click()
      ])
      expect(moduleCreateResponse.ok()).toBeTruthy()
      created.moduleId = (await moduleCreateResponse.json()).id
      await expect(page.getByText('Module created in draft', { exact: true })).toBeVisible()
      const moduleRow = page.getByRole('row').filter({ hasText: moduleCode })
      await expect(moduleRow).toBeVisible()
      await expect(moduleRow.getByText('DRAFT', { exact: true })).toBeVisible()

      await moduleRow.getByRole('button', { name: 'Activate' }).click()
      const [moduleActivateResponse] = await Promise.all([
        page.waitForResponse(response => response.url().endsWith(`/api/academic/modules/${created.moduleId}/activate`)),
        page.locator('button.swal2-confirm').click()
      ])
      expect(moduleActivateResponse.ok()).toBeTruthy()
      await expect(page.getByText('Module activated', { exact: true })).toBeVisible()
      await expect(moduleRow.getByText('ACTIVE', { exact: true })).toBeVisible()

      // --- Programme reference data: levels and types ---
      await page.goto('/operations/programmes')
      await expect(page.getByRole('heading', { name: 'Programme catalogue' })).toBeVisible()

      await page.getByRole('tab', { name: 'Programme levels' }).click()
      await page.getByRole('button', { name: 'New programme level' }).click()
      const levelDrawer = page.getByRole('dialog')
      const uniqueLevelSortOrder = Number.parseInt(fixture.codeSuffix.slice(0, 6), 16) + 1000
      await levelDrawer.getByLabel('Code').fill(levelCode)
      await levelDrawer.getByLabel('Sort order').fill(String(uniqueLevelSortOrder))
      await levelDrawer.getByLabel('Name').fill(levelName)
      const [levelResponse] = await Promise.all([
        page.waitForResponse(response =>
          response.url().endsWith('/api/academic/programme-levels') && response.request().method() === 'POST'),
        levelDrawer.getByRole('button', { name: 'Create level' }).click()
      ])
      expect(levelResponse.ok()).toBeTruthy()
      created.programmeLevelId = (await levelResponse.json()).id
      await expect(page.getByText('Programme level created', { exact: true })).toBeVisible()
      await expect(page.getByRole('row').filter({ hasText: levelCode })).toBeVisible()

      await page.getByRole('tab', { name: 'Programme types' }).click()
      await page.getByRole('button', { name: 'New programme type' }).click()
      const typeDrawer = page.getByRole('dialog')
      await typeDrawer.getByLabel('Code').fill(typeCode)
      await typeDrawer.getByLabel('Name').fill(typeName)
      const [typeResponse] = await Promise.all([
        page.waitForResponse(response =>
          response.url().endsWith('/api/academic/programme-types') && response.request().method() === 'POST'),
        typeDrawer.getByRole('button', { name: 'Create type' }).click()
      ])
      expect(typeResponse.ok()).toBeTruthy()
      created.programmeTypeId = (await typeResponse.json()).id
      await expect(page.getByText('Programme type created', { exact: true })).toBeVisible()
      await expect(page.getByRole('row').filter({ hasText: typeCode })).toBeVisible()

      // --- Programme CRUD ---
      await page.getByRole('tab', { name: 'Programmes' }).click()
      await page.getByRole('button', { name: 'New programme' }).click()
      const programmeDrawer = page.getByRole('dialog')
      await expect(programmeDrawer.getByRole('heading', { name: 'Create programme' })).toBeVisible()
      await programmeDrawer.getByLabel('Owning academic unit').click()
      await page.getByRole('option', { name: academicUnitOptionPattern }).click()
      await programmeDrawer.getByLabel('Programme type').click()
      await page.getByRole('option', { name: typeName, exact: true }).click()
      await programmeDrawer.getByLabel('Programme level').click()
      await page.getByRole('option', { name: levelName, exact: true }).click()
      await programmeDrawer.getByPlaceholder('BSCIT').fill(programmeCode)
      await programmeDrawer.getByLabel('Programme name').fill(programmeName)
      await programmeDrawer.getByLabel('Award name').fill('Bachelor of Science Honours Degree')
      await programmeDrawer.getByLabel('Minimum duration (years)').fill('3')
      await programmeDrawer.getByLabel('Maximum duration (years)').fill('4')
      const [programmeResponse] = await Promise.all([
        page.waitForResponse(response =>
          response.url().endsWith('/api/academic/programmes') && response.request().method() === 'POST'),
        programmeDrawer.getByRole('button', { name: 'Create draft programme' }).click()
      ])
      expect(programmeResponse.ok()).toBeTruthy()
      created.programmeId = (await programmeResponse.json()).id
      await expect(page.getByText('Programme created in draft', { exact: true })).toBeVisible()
      const programmeRow = page.getByRole('row').filter({ hasText: programmeCode })
      await expect(programmeRow).toBeVisible()
      await expect(programmeRow.getByText('DRAFT', { exact: true })).toBeVisible()

      // --- Edit programme (governed correction while still in draft) ---
      const correctedProgrammeName = `${programmeName} — corrected`
      const programmeChangeReason = 'Corrected the programme name and extended the maximum duration.'
      await programmeRow.getByRole('button', { name: 'Edit' }).click()
      const editProgrammeDrawer = page.getByRole('dialog')
      await expect(editProgrammeDrawer.getByRole('heading', { name: 'Edit programme' })).toBeVisible()
      await editProgrammeDrawer.getByLabel('Programme name').fill(correctedProgrammeName)
      await editProgrammeDrawer.getByLabel('Maximum duration (years)').fill('4.5')
      await editProgrammeDrawer.getByLabel('Change reason').fill(programmeChangeReason)
      const [programmeUpdateResponse] = await Promise.all([
        page.waitForResponse(response =>
          response.url().endsWith(`/api/academic/programmes/${created.programmeId}`)
          && response.request().method() === 'PUT'),
        editProgrammeDrawer.getByRole('button', { name: 'Save changes' }).click()
      ])
      expect(programmeUpdateResponse.ok()).toBeTruthy()
      await expect(page.getByText('Programme updated', { exact: true })).toBeVisible()
      await expect(page.getByText(correctedProgrammeName, { exact: true }).first()).toBeVisible()
      expect(executeSql(
        'emhare_academic_setup',
        `SELECT change_reason FROM programmes WHERE id = '${created.programmeId}';`,
        true
      ).trim()).toBe(programmeChangeReason)
      expect(executeSql(
        'emhare_academic_setup',
        `SELECT change_reason FROM programmes_aud WHERE id = '${created.programmeId}' ORDER BY rev DESC LIMIT 1;`,
        true
      ).trim()).toBe(programmeChangeReason)

      // --- Curriculum mapping ---
      await programmeRow.getByRole('link', { name: 'Curriculum' }).click()
      await page.waitForURL(new RegExp(`/operations/curriculum\\?programmeId=${created.programmeId}`))
      await expect(page.getByRole('heading', { name: 'Curriculum versions' })).toBeVisible()

      await page.getByRole('button', { name: 'New curriculum version' }).click()
      const versionDrawer = page.getByRole('dialog')
      await versionDrawer.getByLabel('Version code').fill(versionCode)
      await versionDrawer.getByLabel('Effective from').fill('2027-01-01')
      const [versionResponse] = await Promise.all([
        page.waitForResponse(response =>
          response.url().endsWith(`/api/academic/programmes/${created.programmeId}/versions`)
          && response.request().method() === 'POST'),
        versionDrawer.getByRole('button', { name: 'Create draft version' }).click()
      ])
      expect(versionResponse.ok()).toBeTruthy()
      created.programmeVersionId = (await versionResponse.json()).id
      await expect(page.getByText('Draft curriculum version created', { exact: true })).toBeVisible()
      await expect(page.getByText(`Version ${versionCode}`)).toBeVisible()

      await page.getByRole('button', { name: 'Add Module' }).click()
      const curriculumDrawer = page.getByRole('dialog')
      await curriculumDrawer.getByLabel('Module').click()
      await page.getByRole('option', { name: new RegExp(moduleCode) }).click()
      await expect(curriculumDrawer.getByLabel('Period number')).toHaveCount(0)
      await expect(curriculumDrawer.getByLabel('Year of study')).toContainText('Year 1')
      await expect(curriculumDrawer.getByLabel('Semester')).toContainText('Semester 1')
      await curriculumDrawer.getByLabel('Year of study').click()
      await page.getByRole('option', { name: 'Year 2', exact: true }).click()
      await curriculumDrawer.getByLabel('Semester').click()
      await page.getByRole('option', { name: 'Semester 2', exact: true }).click()
      await curriculumDrawer.getByLabel('Sort order').fill('1')
      await curriculumDrawer.getByLabel('Amendment reason').fill('Added as part of the approved programme curriculum design.')
      const [curriculumResponse] = await Promise.all([
        page.waitForResponse(response =>
          response.url().endsWith(`/api/academic/programme-versions/${created.programmeVersionId}/curriculum`)
          && response.request().method() === 'POST'),
        curriculumDrawer.getByRole('button', { name: 'Add Module', exact: true }).click()
      ])
      expect(curriculumResponse.ok()).toBeTruthy()
      expect(curriculumResponse.request().postDataJSON().periodNumber).toBe(4)
      created.curriculumModuleId = (await curriculumResponse.json()).id
      await expect(page.getByText('Module added to curriculum', { exact: true })).toBeVisible()
      await expect(page.getByText(moduleName, { exact: true }).first()).toBeVisible()
      await expect(page.getByText('Year 2 · Semester 2', { exact: true })).toBeVisible()

      await page.getByRole('button', { name: 'Approve curriculum' }).click()
      const [approveResponse] = await Promise.all([
        page.waitForResponse(response =>
          response.url().endsWith(`/api/academic/programme-versions/${created.programmeVersionId}/approve`)),
        page.locator('button.swal2-confirm').click()
      ])
      expect(approveResponse.ok()).toBeTruthy()
      await expect(page.getByText('Curriculum version approved', { exact: true })).toBeVisible()
      await expect(page.getByText('Approved curriculum remains amendable', { exact: true })).toBeVisible()

      const curriculumRow = page.getByRole('row').filter({ hasText: moduleCode })
      await curriculumRow.getByRole('button', { name: 'Edit' }).click()
      const amendmentDrawer = page.getByRole('dialog')
      await expect(amendmentDrawer.getByRole('heading', { name: `Amend ${moduleCode}` })).toBeVisible()
      await amendmentDrawer.getByLabel('Minimum pass mark').fill('55')
      await amendmentDrawer.getByLabel('Amendment reason').fill('Approved by the curriculum committee after academic review.')
      const [amendmentResponse] = await Promise.all([
        page.waitForResponse(response =>
          response.url().endsWith(`/api/academic/programme-versions/${created.programmeVersionId}/curriculum/${created.curriculumModuleId}`)
          && response.request().method() === 'PUT'),
        amendmentDrawer.getByRole('button', { name: 'Save amendment' }).click()
      ])
      expect(amendmentResponse.ok()).toBeTruthy()
      await expect(page.getByText('Curriculum Module amended', { exact: true })).toBeVisible()
      await expect(curriculumRow.getByText('55.00%', { exact: true })).toBeVisible()

      // --- Activate the programme once its curriculum is approved ---
      await page.goto('/operations/programmes')
      const activatedProgrammeRow = page.getByRole('row').filter({ hasText: programmeCode })
      await expect(activatedProgrammeRow.getByText('DRAFT', { exact: true })).toBeVisible()
      await activatedProgrammeRow.getByRole('button', { name: 'Activate' }).click()
      const [programmeActivateResponse] = await Promise.all([
        page.waitForResponse(response => response.url().endsWith(`/api/academic/programmes/${created.programmeId}/activate`)),
        page.locator('button.swal2-confirm').click()
      ])
      expect(programmeActivateResponse.ok()).toBeTruthy()
      await expect(page.getByText('Programme activated', { exact: true })).toBeVisible()
      await expect(activatedProgrammeRow.getByText('ACTIVE', { exact: true })).toBeVisible()

      // --- Retire the approved curriculum version ---
      await page.goto(`/operations/curriculum?programmeId=${created.programmeId}`)
      await expect(page.getByRole('heading', { name: 'Curriculum versions' })).toBeVisible()
      await expect(page.getByText(`Version ${versionCode}`)).toBeVisible()
      await page.getByRole('button', { name: 'Retire version' }).click()
      await page.locator('.swal2-input').fill('2027-06-01')
      const [retireResponse] = await Promise.all([
        page.waitForResponse(response =>
          response.url().endsWith(`/api/academic/programme-versions/${created.programmeVersionId}/retire`)),
        page.locator('button.swal2-confirm').click()
      ])
      expect(retireResponse.ok()).toBeTruthy()
      await expect(page.getByText('Curriculum version retired', { exact: true })).toBeVisible()
      await expect(page.getByText('RETIRED', { exact: true }).first()).toBeVisible()

      expect(consoleErrors).toEqual([])
      expect(pageErrors).toEqual([])
    } finally {
      await cleanupCrudFixture(fixture, created)
    }
  })
})
