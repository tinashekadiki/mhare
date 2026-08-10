import { expect, request as playwrightRequest, test, type Page } from '@playwright/test'
import { spawnSync } from 'node:child_process'
import { randomUUID } from 'node:crypto'
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'

const applicantPortalUrl = process.env.APPLICANT_PORTAL_URL ?? 'http://localhost:3001'
const keycloakBaseUrl = process.env.KEYCLOAK_URL ?? 'http://localhost:8099'
const keycloakRealm = process.env.KEYCLOAK_REALM ?? 'emhare'
const postgresContainer = process.env.POSTGRES_CONTAINER ?? 'emhare-postgres'
const testPassword = 'Temporary-Applicant-UI-Password-42'

type ApplicantFixture = {
  userId: string
  username: string
  academicUnitTypeRootId: string
  academicUnitTypeLeafId: string
  academicUnitRootId: string
  academicUnitLeafId: string
  academicYearId: string
  intakeId: string
  programmeLevelId: string
  programmeTypeId: string
  programmeId: string
  programmeVersionId: string
  moduleId: string
  curriculumModuleId: string
  examBodyId: string
  subjectId: string
  secondSubjectId: string
  applicationTypeId: string
  countryId: string
  calendarYear: number
  codeSuffix: string
  programmeCode: string
  intakeName: string
  applicationTypeName: string
  firstRefereeEmail: string
  secondRefereeEmail: string
}

type ApplicantFixtureOptions = {
  postgraduate?: boolean
}

function executeSql(database: string, sql: string, tuplesOnly = false) {
  const args = ['exec', '-i', postgresContainer, 'psql', '-q', '-v', 'ON_ERROR_STOP=1', '-U', 'postgres', '-d', database]
  if (tuplesOnly) args.push('-A', '-t')
  const result = spawnSync('docker', args, { input: sql, encoding: 'utf8' })
  if (result.status !== 0) throw new Error(result.stderr || result.stdout)
  return result.stdout
}

async function keycloakAdminContext() {
  const tokenContext = await playwrightRequest.newContext()
  const tokenResponse = await tokenContext.post(`${keycloakBaseUrl}/realms/master/protocol/openid-connect/token`, {
    form: { client_id: 'admin-cli', username: 'admin', password: 'admin', grant_type: 'password' }
  })
  expect(tokenResponse.ok()).toBeTruthy()
  const token = (await tokenResponse.json()).access_token
  await tokenContext.dispose()
  return playwrightRequest.newContext({ extraHTTPHeaders: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } })
}

async function createFixture(options: ApplicantFixtureOptions = {}): Promise<ApplicantFixture> {
  const runId = randomUUID()
  const codeSuffix = runId.replaceAll('-', '').slice(0, 8).toUpperCase()
  const username = `applicant-programme-${runId}@example.test`
  const keycloak = await keycloakAdminContext()
  const createUser = await keycloak.post(`${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users`, {
    data: {
      username, email: username, firstName: 'Browser', lastName: 'Applicant', enabled: true, emailVerified: true,
      credentials: [{ type: 'password', value: testPassword, temporary: false }]
    }
  })
  expect(createUser.status()).toBe(201)
  const userId = createUser.headers().location!.split('/').at(-1)!
  const applicantRole = await keycloak.get(`${keycloakBaseUrl}/admin/realms/${keycloakRealm}/roles/applicant`)
  await keycloak.post(`${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users/${userId}/role-mappings/realm`, {
    data: [await applicantRole.json()]
  })
  await keycloak.dispose()

  const academicUnitTypeRootId = executeSql('emhare_academic_setup', "SELECT id FROM academic_unit_types WHERE code = 'FACULTY' AND deleted_at IS NULL;", true).trim()
  const academicUnitTypeLeafId = executeSql('emhare_academic_setup', "SELECT id FROM academic_unit_types WHERE code = 'DEPARTMENT' AND deleted_at IS NULL;", true).trim()
  if (!academicUnitTypeRootId || !academicUnitTypeLeafId) throw new Error('Canonical FACULTY and DEPARTMENT academic unit types are required for the browser fixture.')
  const calendarYear = 6000 + (Number.parseInt(codeSuffix.slice(0, 4), 16) % 3000)
  const fixture: ApplicantFixture = {
    userId, username,
    academicUnitTypeRootId, academicUnitTypeLeafId,
    academicUnitRootId: randomUUID(), academicUnitLeafId: randomUUID(),
    academicYearId: executeSql('emhare_academic_setup', "SELECT id FROM academic_years WHERE status = 'OPEN' AND CURRENT_DATE BETWEEN start_date AND end_date AND deleted_at IS NULL ORDER BY start_date DESC LIMIT 1;", true).trim(),
    intakeId: randomUUID(),
    programmeLevelId: randomUUID(), programmeTypeId: randomUUID(),
    programmeId: randomUUID(), programmeVersionId: randomUUID(),
    moduleId: randomUUID(), curriculumModuleId: randomUUID(),
    examBodyId: randomUUID(), subjectId: randomUUID(), secondSubjectId: randomUUID(),
    applicationTypeId: randomUUID(),
    countryId: executeSql('emhare_core_identity', "SELECT id FROM countries WHERE iso2_code = 'ZW' AND deleted_at IS NULL;", true).trim(),
    calendarYear,
    codeSuffix,
    programmeCode: options.postgraduate ? `MBA${codeSuffix.slice(0, 2)}` : `E${codeSuffix.slice(0, 4)}`,
    intakeName: `Browser E2E Intake ${codeSuffix}`,
    applicationTypeName: options.postgraduate ? `Masters and MBA ${codeSuffix}` : `Undergraduate application ${codeSuffix}`,
    firstRefereeEmail: `referee-one-${codeSuffix.toLowerCase()}@example.test`,
    secondRefereeEmail: `referee-two-${codeSuffix.toLowerCase()}@example.test`,
  }
  const year = calendarYear
  const uniqueSortOrder = Number.parseInt(codeSuffix.slice(0, 7), 16)
  executeSql('emhare_academic_setup', `
INSERT INTO academic_units (id, academic_unit_type_id, parent_id, code, name, status, created_at, updated_at, version) VALUES
('${fixture.academicUnitRootId}', '${fixture.academicUnitTypeRootId}', null, 'BSI_${codeSuffix}', 'Faculty of Science', 'ACTIVE', now(), now(), 0),
('${fixture.academicUnitLeafId}', '${fixture.academicUnitTypeLeafId}', '${fixture.academicUnitRootId}', 'BCO_${codeSuffix}', 'Department of Computing', 'ACTIVE', now(), now(), 0);
INSERT INTO intakes (id, academic_year_id, code, name, starts_on, ends_on, status, maximum_programme_choices, created_at, updated_at, version)
VALUES ('${fixture.intakeId}', '${fixture.academicYearId}', 'BI_${codeSuffix}', '${fixture.intakeName}', CURRENT_DATE - 1, CURRENT_DATE + 30, 'DRAFT', 3, now(), now(), 0);
INSERT INTO programme_levels (id, code, name, sort_order, status, created_at, updated_at, version)
VALUES ('${fixture.programmeLevelId}', '${options.postgraduate ? 'BPG' : 'BUG'}_${codeSuffix}', '${options.postgraduate ? 'Postgraduate' : 'Undergraduate'}', ${uniqueSortOrder}, 'ACTIVE', now(), now(), 0);
INSERT INTO programme_types (id, code, name, status, created_at, updated_at, version)
VALUES ('${fixture.programmeTypeId}', '${options.postgraduate ? 'MBA' : 'BDG'}_${codeSuffix}', '${options.postgraduate ? 'Masters degree' : 'Degree'}', 'ACTIVE', now(), now(), 0);
INSERT INTO intake_programme_level_targets (id, intake_id, programme_level_id, created_at, updated_at, version)
VALUES (gen_random_uuid(), '${fixture.intakeId}', '${fixture.programmeLevelId}', now(), now(), 0);
UPDATE intakes SET status = 'OPEN', updated_at = now(), version = 1 WHERE id = '${fixture.intakeId}';
INSERT INTO modules (id, owning_academic_unit_id, code, name, description, credit_value, academic_level, status, created_at, updated_at, version)
VALUES ('${fixture.moduleId}', '${fixture.academicUnitLeafId}', 'BCS_${codeSuffix}', 'Programming Fundamentals', 'Browser fixture Module.', 12.00, 1, 'ACTIVE', now(), now(), 0);
INSERT INTO programmes (id, owning_academic_unit_id, programme_type_id, programme_level_id, code, name, award_name, minimum_duration_periods, maximum_duration_periods, status, created_at, updated_at, version)
VALUES ('${fixture.programmeId}', '${fixture.academicUnitLeafId}', '${fixture.programmeTypeId}', '${fixture.programmeLevelId}', '${fixture.programmeCode}', '${options.postgraduate ? 'Master of Business Administration' : 'Browser Verified Programme'}', '${options.postgraduate ? 'Master of Business Administration' : 'Bachelor of Science Honours Degree'}', ${options.postgraduate ? 4 : 8}, ${options.postgraduate ? 6 : 12}, 'ACTIVE', now(), now(), 0);
INSERT INTO programme_versions (id, programme_id, version_code, effective_from, status, created_at, updated_at, version)
VALUES ('${fixture.programmeVersionId}', '${fixture.programmeId}', '${year}.1', CURRENT_DATE - 1, 'DRAFT', now(), now(), 0);
INSERT INTO curriculum_modules (id, programme_version_id, module_id, period_number, module_type, credit_value, minimum_mark_required, sort_order, created_at, updated_at, version)
VALUES ('${fixture.curriculumModuleId}', '${fixture.programmeVersionId}', '${fixture.moduleId}', 1, 'COMPULSORY', 12.00, 50.00, 1, now(), now(), 0);
UPDATE programme_versions SET status = 'APPROVED', approved_by_user_id = '${userId}', approved_at = now(), version = 1 WHERE id = '${fixture.programmeVersionId}';
`)
  executeSql('emhare_admissions', `
INSERT INTO application_types (id, code, name, requires_employment_history, requires_referees, is_active, created_at, updated_at, version)
VALUES ('${fixture.applicationTypeId}', '${options.postgraduate ? 'MASTERS-MBA' : 'BROWSER-TYPE'}-${runId.slice(0, 8)}', '${fixture.applicationTypeName}', ${options.postgraduate ? 'true' : 'false'}, ${options.postgraduate ? 'true' : 'false'}, true, now(), now(), 0);
INSERT INTO application_type_document_requirements (id, application_type_id, requirement_code, requirement_name, is_required, sort_order, is_active, created_at, updated_at, version)
VALUES
(gen_random_uuid(), '${fixture.applicationTypeId}', 'IDENTITY_DOCUMENT', 'Identity document', true, 10, true, now(), now(), 0),
(gen_random_uuid(), '${fixture.applicationTypeId}', 'ACADEMIC_QUALIFICATION_EVIDENCE', 'Academic qualification evidence', true, 20, true, now(), now(), 0);
INSERT INTO exam_bodies (id, code, name, country_id, is_active, created_at, updated_at, version)
VALUES ('${fixture.examBodyId}', 'ZIMSEC_${codeSuffix}', 'Zimbabwe School Examinations Council ${codeSuffix}', '${fixture.countryId}', true, now(), now(), 0);
INSERT INTO admission_subjects (id, code, name, level, subject_group_code, is_active, created_at, updated_at, version)
VALUES
('${fixture.subjectId}', 'ENG_${codeSuffix}', 'English Language ${codeSuffix}', 'O_LEVEL', 'LANGUAGE', true, now(), now(), 0),
('${fixture.secondSubjectId}', 'MATH_${codeSuffix}', 'Mathematics ${codeSuffix}', 'O_LEVEL', 'SCIENCE', true, now(), now(), 0);
`)
  return fixture
}

async function cleanupFixture(fixture: ApplicantFixture | null) {
  if (!fixture) return
  const coreUserId = executeSql('emhare_core_identity', `SELECT id FROM users WHERE email = '${fixture.username}';`, true).trim()
  if (coreUserId) {
    executeSql('emhare_admissions', `
DELETE FROM applicant_qualification_results_aud WHERE qualification_sitting_id IN (SELECT id FROM applicant_qualification_sittings WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}')));
DELETE FROM applicant_qualification_results WHERE qualification_sitting_id IN (SELECT id FROM applicant_qualification_sittings WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}')));
DELETE FROM applicant_qualification_sittings_aud WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM applicant_qualification_sittings WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM applicant_next_of_kin_aud WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}');
DELETE FROM applicant_next_of_kin WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}');
DELETE FROM applicant_employment_histories_aud WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}');
DELETE FROM applicant_employment_histories WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}');
DELETE FROM applicant_referee_invitations_aud WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM applicant_referee_invitations WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM applicant_referees_aud WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}');
DELETE FROM applicant_referees WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}');
DELETE FROM application_payment_references_aud WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_payment_references WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_documents_aud WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_documents WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_accommodation_requests_aud WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_accommodation_requests WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_exam_arrangements_aud WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_exam_arrangements WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_evaluations_aud WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_evaluations WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_sections_aud WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_sections WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_status_events_aud WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_status_events WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_programme_choices_aud WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM application_programme_choices WHERE application_id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM applications_aud WHERE id IN (SELECT id FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}'));
DELETE FROM applications WHERE applicant_id IN (SELECT id FROM applicants WHERE user_id = '${coreUserId}');
DELETE FROM applicants_aud WHERE user_id = '${coreUserId}';
DELETE FROM applicants WHERE user_id = '${coreUserId}';
`)
    executeSql('emhare_core_identity', `
DELETE FROM user_role_assignments_aud WHERE user_id = '${coreUserId}'; DELETE FROM user_role_assignments WHERE user_id = '${coreUserId}';
DELETE FROM login_events_aud WHERE user_id = '${coreUserId}'; DELETE FROM login_events WHERE user_id = '${coreUserId}';
DELETE FROM users_aud WHERE id = '${coreUserId}'; DELETE FROM users WHERE id = '${coreUserId}';
`)
  }
  executeSql('emhare_admissions', `
DELETE FROM application_type_sections_aud WHERE application_type_id = '${fixture.applicationTypeId}';
DELETE FROM application_type_sections WHERE application_type_id = '${fixture.applicationTypeId}';
DELETE FROM application_type_document_requirements_aud WHERE application_type_id = '${fixture.applicationTypeId}';
DELETE FROM application_type_document_requirements WHERE application_type_id = '${fixture.applicationTypeId}';
DELETE FROM application_fees_aud WHERE application_type_id = '${fixture.applicationTypeId}';
DELETE FROM application_fees WHERE application_type_id = '${fixture.applicationTypeId}';
DELETE FROM admission_cycles_aud WHERE id IN (SELECT id FROM admission_cycles WHERE intake_id = '${fixture.intakeId}');
DELETE FROM admission_cycles WHERE intake_id = '${fixture.intakeId}';
DELETE FROM application_types WHERE id = '${fixture.applicationTypeId}';
DELETE FROM admission_subjects WHERE id IN ('${fixture.subjectId}', '${fixture.secondSubjectId}');
DELETE FROM exam_bodies WHERE id = '${fixture.examBodyId}';
`)
  executeSql('emhare_academic_setup', `
BEGIN; SET LOCAL session_replication_role = replica;
DELETE FROM curriculum_modules WHERE id = '${fixture.curriculumModuleId}'; DELETE FROM programme_versions WHERE id = '${fixture.programmeVersionId}';
DELETE FROM programmes WHERE id = '${fixture.programmeId}'; DELETE FROM modules WHERE id = '${fixture.moduleId}';
DELETE FROM intake_programme_targets WHERE intake_id = '${fixture.intakeId}';
DELETE FROM intake_programme_level_targets WHERE intake_id = '${fixture.intakeId}';
DELETE FROM programme_types WHERE id = '${fixture.programmeTypeId}'; DELETE FROM programme_levels WHERE id = '${fixture.programmeLevelId}';
DELETE FROM intakes WHERE id = '${fixture.intakeId}';
DELETE FROM academic_units WHERE id = '${fixture.academicUnitLeafId}'; DELETE FROM academic_units WHERE id = '${fixture.academicUnitRootId}'; COMMIT;
`)
  const keycloak = await keycloakAdminContext()
  await keycloak.delete(`${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users/${fixture.userId}`)
  await keycloak.dispose()
}

async function login(page: Page, fixture: ApplicantFixture) {
  await page.getByRole('button', { name: 'Sign in' }).first().click()
  await page.locator('#username').fill(fixture.username)
  await page.locator('#password').fill(testPassword)
  await page.locator('#kc-login').click()
  await page.waitForURL(`${applicantPortalUrl}/**`, { timeout: 30_000 })
  await page.waitForLoadState('networkidle')
}

async function selectOption(page: Page, label: string | RegExp, option: string | RegExp) {
  const field = page.getByLabel(label)
  await expect(field).toBeEnabled({ timeout: 30_000 })
  let lastError: unknown
  for (let attempt = 0; attempt < 4; attempt += 1) {
    await field.scrollIntoViewIfNeeded().catch(() => undefined)
    await field.evaluate((element: HTMLElement) => element.click())
    const optionLocator = page.getByRole('option', { name: option, exact: typeof option === 'string' }).first()
    try {
      await optionLocator.waitFor({ state: 'visible', timeout: 10_000 })
      await optionLocator.click({ force: true })
      await page.keyboard.press('Escape')
      await expect(page.getByRole('listbox')).toBeHidden({ timeout: 5_000 }).catch(() => undefined)
      return
    } catch (error) {
      lastError = error
      await page.keyboard.press('Escape')
      await page.waitForLoadState('networkidle').catch(() => undefined)
      await page.waitForTimeout(500)
    }
  }
  throw lastError
}

async function selectOptionUntilFieldContains(page: Page, label: string, option: string | RegExp, expectedText: string) {
  const field = page.getByLabel(label)
  for (let attempt = 0; attempt < 3; attempt += 1) {
    await selectOption(page, label, option)
    if ((await field.textContent() ?? '').includes(expectedText)) return
  }
  await expect(field).toContainText(expectedText)
}

async function clickVisibleButtonContaining(page: Page, label: string) {
  await page.waitForFunction((buttonLabel) => {
    return Array.from(document.querySelectorAll('button')).some((button) =>
      !button.disabled
      && button.offsetParent !== null
      && (button.textContent ?? '').replace(/\s+/g, ' ').includes(buttonLabel)
    )
  }, label)
  await page.evaluate((buttonLabel) => {
    const buttons = Array.from(document.querySelectorAll('button')).filter((candidate) =>
      !candidate.disabled
      && candidate.offsetParent !== null
      && (candidate.textContent ?? '').replace(/\s+/g, ' ').includes(buttonLabel)
    )
    const button = buttons.at(-1)
    if (!(button instanceof HTMLButtonElement)) throw new Error(`Button was not found: ${buttonLabel}`)
    button.click()
  }, label)
}

async function dismissSuccessDialog(page: Page) {
  const confirmation = page.locator('.swal2-confirm').filter({ hasText: 'OK' }).last()
  await confirmation.waitFor({ state: 'visible' })
  await confirmation.evaluate((button: HTMLElement) => button.click())
  await expect(page.locator('.swal2-container')).toBeHidden({ timeout: 15_000 })
}

async function uploadEvidence(page: Page, requirementName: string, filePath: string) {
  const activeSection = page.locator('#application-section-editor')
  const requirementRow = activeSection.locator('.space-y-3 > div').filter({ hasText: requirementName }).first()
  await requirementRow.getByRole('button', { name: /Upload|Replace/ })
    .evaluate((element: HTMLElement) => element.click())
  await page.locator('input[type="file"]').last().setInputFiles(filePath)
  await Promise.all([
    page.waitForResponse(response =>
      response.url().includes('/api/documents/uploads')
      && response.request().method() === 'POST'
      && response.ok(),
    ),
    page.waitForResponse(response =>
      response.url().includes('/api/admissions/applications/')
      && response.url().endsWith('/documents')
      && response.request().method() === 'POST'
      && response.ok(),
    ),
    clickVisibleButtonContaining(page, 'Upload evidence'),
  ])
  await dismissSuccessDialog(page)
  await expect(activeSection.getByText(requirementName)).toBeVisible()
}

async function waitForReferenceInvitation(email: string) {
  const deadline = Date.now() + 30_000
  while (Date.now() < deadline) {
    const serializedNotification = executeSql('emhare_notifications', `
SELECT json_build_object('status', status, 'providerCode', provider_code, 'body', body)::text
FROM notification_requests
WHERE recipient_address = '${email}'
  AND template_code = 'REFEREE_REFERENCE_REQUEST_EMAIL'
ORDER BY created_at DESC
LIMIT 1;
`, true).trim()
    if (serializedNotification) {
      const notification = JSON.parse(serializedNotification) as { status: string, providerCode: string | null, body: string }
      if (notification.status === 'SENT') {
        const responseUrl = notification.body.match(/https?:\/\/[^\s<]+\/references\/[A-Za-z0-9_-]+/)?.[0]
        if (!responseUrl) throw new Error(`Reference response URL was not rendered for ${email}.`)
        return { ...notification, responseUrl }
      }
    }
    await new Promise(resolve => setTimeout(resolve, 500))
  }
  throw new Error(`Reference invitation was not delivered to ${email} within 30 seconds.`)
}

async function submitConfidentialReference(page: Page, responseUrl: string, relationship: string) {
  await page.goto(responseUrl)
  await expect(page.getByRole('heading', { name: 'Confidential reference', exact: true })).toBeVisible()
  await page.getByLabel('Relationship to applicant').fill(relationship)
  await page.getByLabel('Years known').fill('5')
  await selectOption(page, 'Recommendation', 'Strongly recommend')
  await page.getByLabel('Reference comments').fill(
    'The applicant demonstrates sound judgement, leadership, academic readiness, and strong potential for MBA study.',
  )
  await page.getByLabel('I confirm that this confidential reference is accurate and represents my own assessment.')
    .evaluate((element: HTMLElement) => element.click())
  await clickVisibleButtonContaining(page, 'Submit confidential reference')
  await expect(page.getByRole('heading', { name: 'Reference submitted', exact: true })).toBeVisible()
}

test.describe('Applicant programme choices', () => {
  test('loads the authenticated applicant dashboard without service contract errors', async ({ page }) => {
    test.setTimeout(60_000)
    let fixture: ApplicantFixture | null = null
    try {
      fixture = await createFixture()
      const failedApiResponses: string[] = []
      page.on('response', response => {
        if (response.url().includes('/api/') && response.status() >= 500) {
          failedApiResponses.push(`${response.status()} ${response.request().method()} ${response.url()}`)
        }
      })

      await page.goto(applicantPortalUrl)
      await login(page, fixture)

      await expect(page.getByRole('heading', { name: 'Browser Applicant', exact: true })).toBeVisible()
      await expect(page.getByRole('link', { name: 'Start application', exact: true }).first()).toBeVisible()
      await expect(page.getByRole('heading', { name: 'Applications unavailable', exact: true })).toHaveCount(0)
      expect(failedApiResponses).toEqual([])
    } finally {
      await cleanupFixture(fixture)
    }
  })

  test('shows manual payment evidence and keeps card checkout inside the application', async ({ page }) => {
    test.setTimeout(90_000)
    let fixture: ApplicantFixture | null = null
    try {
      fixture = await createFixture()
      await page.goto(applicantPortalUrl)
      await login(page, fixture)

      const applicationId = randomUUID()
      const financePaymentReferenceId = randomUUID()
      const checkoutAttemptId = randomUUID()
      let paymentConfirmed = false
      let reconciledAttemptId: string | undefined
      await page.route(`**/api/admissions/applications/${applicationId}/workspace`, route => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          application: {
            id: applicationId,
            applicationNumber: 'EMH-PAYMENT-UI-0001',
            applicantNumber: 'APP-PAYMENT-UI-0001',
            applicantName: 'Browser Applicant',
            intakeId: fixture!.intakeId,
            intakeCode: `BI_${fixture!.codeSuffix}`,
            applicationTypeId: fixture!.applicationTypeId,
            applicationTypeName: fixture!.applicationTypeName,
            status: 'DRAFT',
            paymentRequired: true,
            paymentClearanceStatus: paymentConfirmed ? 'PAID' : 'PENDING',
            paymentWaiverReason: null,
            canSubmit: false,
            canEnterReview: false,
            calculatedTotalPoints: null,
            pointsCalculatedAt: null,
            programmeChoices: [],
            payment: {
              financePaymentReferenceId,
              reference: 'EMH-PAY-0000000442',
              amountDue: 25,
              currencyCode: 'USD',
              baseCurrencyCode: 'USD',
              baseAmountDue: 25,
              ratingStatus: 'RATED',
              status: paymentConfirmed ? 'PAID' : 'PENDING',
              requiredForSubmission: true,
              workflowCleared: paymentConfirmed,
              paidAt: paymentConfirmed ? '2026-08-10T08:43:33Z' : null,
            },
          },
          profile: {
            id: randomUUID(), userId: randomUUID(), applicantNumber: 'APP-PAYMENT-UI-0001',
            applicantCategoryCode: 'LOCAL', titleCode: 'MR', firstName: 'Browser', middleNames: null,
            lastName: 'Applicant', dateOfBirth: '1999-01-15', genderCode: 'MALE', maritalStatusCode: 'SINGLE',
            nationalIdNumber: '99-UI', passportNumber: null, countryId: fixture!.countryId,
            nationalityCountryId: fixture!.countryId, placeOfBirth: 'Harare', disabilityStatusCode: 'NONE',
            specialNeeds: null, sponsorTypeCode: 'SELF', primaryEmail: fixture!.username,
            primaryPhone: '+263772000001', postalAddress: null, residentialAddress: 'Harare',
            completenessPercentage: 100, missingRequiredFields: [], createdAt: '2026-08-10T06:00:00Z',
            updatedAt: '2026-08-10T06:00:00Z', version: 0,
          },
          sections: [{
            id: randomUUID(), code: 'PAYMENT', name: 'Application fee', required: true, repeatable: false,
            minimumRecords: 0, sortOrder: 80, status: paymentConfirmed ? 'COMPLETE' : 'IN_PROGRESS',
            completedAt: paymentConfirmed ? '2026-08-10T08:43:33Z' : null,
            completionSummary: paymentConfirmed ? 'Application fee confirmed.' : 'Application fee confirmation or waiver is required.', version: 0,
          }],
          nextOfKin: [], employmentHistory: [], referees: [], qualifications: [],
          documents: { requirements: [], requiredDocumentsUploaded: true, allRequiredDocumentsVerified: false },
          readyForSubmission: false,
          missingRequirements: ['Application fee: Application fee confirmation or waiver is required.'],
          declarationAcceptedAt: null,
          declarationVersion: null,
        }),
      }))
      await page.route('**/api/admissions/applications/start-options**', route => route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify({ applicantCategories: [], applicationTypes: [], intakes: [] }),
      }))
      await page.route('**/api/admissions/qualification-reference-data', route => route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify({ examBodies: [], oLevelSubjects: [], aLevelSubjects: [], otherSubjects: [] }),
      }))
      await page.route('**/api/core/reference/countries', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }))
      await page.route(`**/api/finance/application-payment-references/by-application/${applicationId}/payment-options`, route => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          proofOfPaymentUploadAvailable: true,
          onlinePayment: {
            available: true,
            availabilityMessage: 'Pay the application fee securely by debit or credit card.',
          },
        }),
      }))
      await page.route(`**/api/finance/application-payment-references/by-application/${applicationId}/online-checkouts`, route => route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          attemptId: checkoutAttemptId,
          embeddedCheckoutUrl: 'https://portal.host.iveri.com/Lite/LiteBox',
          returnMessageOrigin: new URL(applicantPortalUrl).origin,
          formParameters: {
            Lite_Merchant_ApplicationId: '{00000000-0000-0000-0000-000000000001}',
            Lite_Order_Amount: '2500',
            Lite_ConsumerOrderID_PreFix: 'EMH',
            Lite_Merchant_Trace: 'payment-ui-trace',
            Ecom_BillTo_Online_Email: fixture!.username,
          },
          expiresAt: '2026-08-10T07:30:00Z',
        }),
      }))
      await page.route(`**/api/finance/application-payment-references/by-application/${applicationId}/online-checkouts/reconcile`, route => {
        const request = route.request().postDataJSON() as { attemptId?: string }
        if (request.attemptId) {
          reconciledAttemptId = request.attemptId
          paymentConfirmed = true
        }
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            status: paymentConfirmed ? 'PAID' : 'PENDING',
            workflowCleared: paymentConfirmed,
          }),
        })
      })
      await page.route('https://portal.host.iveri.com/Lite/LiteBox', route => route.fulfill({
        status: 200,
        contentType: 'text/html',
        body: `<!doctype html><html><body><main></main><script>
          addEventListener('message', event => {
            const request = JSON.parse(event.data)
            const fields = Object.fromEntries(request.form.map(field => [field.name, field.value]))
            if (!fields.Lite_ConsumerOrderID_PreFix) return
            document.querySelector('main').innerHTML = '<label>Card Number <input name="cardNumber"></label>'
          })
        </script></body></html>`,
      }))
      await page.route(`**/api/documents/uploads?ownerType=FINANCE_RECORD&ownerId=${financePaymentReferenceId}`, route => route.fulfill({
        status: 200, contentType: 'application/json', body: '[]',
      }))

      await page.goto(`${applicantPortalUrl}/applications/${applicationId}`)
      await expect(page.getByRole('heading', { name: 'Application fee', exact: true })).toBeVisible()
      await expect(page.getByText('EMH-PAY-0000000442', { exact: true })).toBeVisible()
      await expect(page.getByRole('heading', { name: 'Already paid by bank?', exact: true })).toBeVisible()
      await expect(page.getByLabel('Proof of payment')).toBeVisible()
      await expect(page.getByRole('heading', { name: 'Pay online', exact: true })).toBeVisible()
      await expect(page.getByRole('button', { name: 'Pay USD 25 now', exact: true })).toBeVisible()
      await expect(page.getByText(/iVeri/i)).toHaveCount(0)

      await page.getByRole('button', { name: 'Pay USD 25 now', exact: true }).click()
      await expect(page.getByRole('dialog', { name: 'Make payment' })).toHaveCount(0)
      await expect(page.getByRole('heading', { name: 'Pay USD 25', exact: true })).toBeVisible()
      await expect(page.getByTitle('Secure card payment')).toBeVisible()
      await expect(page.getByRole('button', { name: 'Cancel payment', exact: true })).toBeVisible()
      await expect(page).toHaveURL(`${applicantPortalUrl}/applications/${applicationId}`)

      const checkoutFrame = page.frames().find(frame => frame.url() === 'https://portal.host.iveri.com/Lite/LiteBox')
      expect(checkoutFrame).toBeTruthy()
      await expect(checkoutFrame!.getByLabel('Card Number')).toBeVisible()
      await checkoutFrame!.evaluate((merchantSiteOrigin) => {
        window.parent.postMessage(JSON.stringify({
          Lite_Payment_Card_Status: '0',
          Lite_Merchant_Trace: 'payment-ui-trace',
        }), merchantSiteOrigin)
      }, new URL(applicantPortalUrl).origin)
      await expect(page.getByTitle('Secure card payment')).toBeHidden()
      await expect(page.getByRole('heading', { name: 'Payment confirmed' })).toBeVisible()
      expect(reconciledAttemptId).toBe(checkoutAttemptId)
      await expect(page.getByText('Application fee confirmed', { exact: true })).toBeVisible()
    } finally {
      await cleanupFixture(fixture)
    }
  })

  test('selects an Academic Setup programme and persists its curriculum snapshot', async ({ page }, testInfo) => {
    test.setTimeout(60_000)
    let fixture: ApplicantFixture | null = null
    try {
      fixture = await createFixture()
      const consoleErrors: string[] = []
      page.on('console', message => message.type() === 'error' && consoleErrors.push(message.text()))
      await page.goto(applicantPortalUrl)
      await login(page, fixture)
      await page.getByRole('link', { name: 'Start application' }).first().click()
      await expect(page).toHaveURL(`${applicantPortalUrl}/applications/new`)
      await expect(page.getByRole('dialog')).toHaveCount(0)

      const form = page.locator('#application-start-journey')
      await expect(form.getByLabel('Application type')).toBeEnabled()
      await form.getByLabel('Application type').click()
      await page.getByRole('option', { name: fixture.applicationTypeName, exact: true }).click()
      const startJourneyNavigator = page.getByRole('navigation', { name: 'Application process' })
      await expect(startJourneyNavigator.getByRole('button', { name: /Application route/ })).toBeVisible()
      await expect(startJourneyNavigator.getByRole('button', { name: /Applicant details/ })).toBeVisible()
      await expect(startJourneyNavigator.getByRole('button', { name: /Programme choices/ })).toBeVisible()
      await expect(startJourneyNavigator.getByRole('button', { name: /Personal details/ })).toHaveCount(0)
      await expect(startJourneyNavigator.getByRole('button', { name: /Qualifications/ })).toBeVisible()
      await expect(startJourneyNavigator.getByRole('button', { name: /Review and declaration/ })).toBeVisible()
      await form.getByLabel('Intake').click()
      await page.getByRole('option', { name: fixture.intakeName, exact: true }).click()

      await Promise.all([
        page.waitForURL(`${applicantPortalUrl}/applications/**`),
        page.getByRole('button', { name: 'Create draft', exact: true })
          .evaluate((element: HTMLElement) => element.click()),
      ])

      const workspaceNavigator = page.getByRole('navigation', { name: 'Application process' })
      await expect(workspaceNavigator.getByRole('button', { name: /Application setup/ })).toHaveCount(0)
      await expect(workspaceNavigator.getByRole('button').first()).toContainText('Application route')
      await expect(workspaceNavigator.getByRole('button', { name: /Applicant details/ })).toBeVisible()
      await expect(workspaceNavigator.getByRole('button', { name: /Personal details/ })).toHaveCount(0)
      await expect(page.getByRole('heading', { name: 'Applicant details', exact: true })).toBeVisible()
      const profileSaveRequests: string[] = []
      page.on('request', request => {
        if (request.method() === 'PUT' && request.url().includes('/profile')) profileSaveRequests.push(request.url())
      })
      await expect(page.getByLabel('First name')).not.toBeEditable()
      await expect(page.getByLabel('First name')).toHaveValue('Browser')
      await expect(page.getByLabel('Last name')).not.toBeEditable()
      await expect(page.getByLabel('Last name')).toHaveValue('Applicant')
      await page.waitForTimeout(1_100)
      expect(profileSaveRequests).toEqual([])
      await expect(page.getByRole('dialog')).toHaveCount(0)
      await workspaceNavigator.getByRole('button', { name: /Next of kin/ }).click()
      await expect(page.getByRole('heading', { name: 'Next of kin details', exact: true })).toBeVisible()
      await expect(page.getByLabel('Full name')).toBeVisible()
      await expect(page.getByRole('dialog')).toHaveCount(0)
      await workspaceNavigator
        .getByRole('button', { name: /Programme choices/ })
        .click()
      await expect(page.getByRole('heading', { name: 'Programme choices', exact: true, level: 1 })).toBeVisible()
      await page.getByLabel('Programme choices').click()
      await expect(page.getByRole('option', { name: new RegExp(fixture.programmeCode) })).toBeVisible()
      await page.getByRole('option', { name: new RegExp(fixture.programmeCode) }).click()
      await page.keyboard.press('Escape')
      await expect(page.getByRole('listbox')).toBeHidden()
      await page.getByRole('button', { name: 'Save choices', exact: true }).click()
      await dismissSuccessDialog(page)
      await expect(page.getByText(`${fixture.programmeCode} · Browser Verified Programme`, { exact: true }).last()).toBeVisible()
      await expect(page.locator('#application-section-editor').getByText(
        `Department of Computing · Curriculum ${fixture.calendarYear}.1`,
        { exact: true },
      )).toBeVisible()
      await page.screenshot({ path: testInfo.outputPath('applicant-programme-choice.png'), fullPage: true })
      expect(consoleErrors).toEqual([])
    } finally {
      await cleanupFixture(fixture)
    }
  })

  test('completes the draft workspace as one sequential applicant journey', async ({ page }, testInfo) => {
    test.setTimeout(180_000)
    let fixture: ApplicantFixture | null = null
    try {
      fixture = await createFixture()
      const consoleErrors: string[] = []
      const failedResponses: string[] = []
      page.on('console', message => message.type() === 'error' && consoleErrors.push(message.text()))
      page.on('response', response => {
        if (response.url().includes('/api/') && response.status() >= 400) {
          failedResponses.push(`${response.status()} ${response.request().method()} ${response.url()}`)
        }
      })

      await page.goto(applicantPortalUrl)
      await login(page, fixture)
      await page.getByRole('link', { name: 'Start application' }).first().click()

      const form = page.locator('#application-start-journey')
      await expect(form.getByLabel('Application type')).toBeEnabled()
      await expect(page.getByText(fixture.applicationTypeName, { exact: true })).toHaveCount(0)
      await selectOption(page, 'Application type', fixture.applicationTypeName)
      await selectOption(page, 'Intake', fixture.intakeName)
      await Promise.all([
        page.waitForURL(`${applicantPortalUrl}/applications/**`),
        page.getByRole('button', { name: 'Create draft', exact: true })
          .evaluate((element: HTMLElement) => element.click()),
      ])

      const workspaceNavigator = page.getByRole('navigation', { name: 'Application process' })
      await expect(workspaceNavigator.getByRole('button', { name: /Application setup/ })).toHaveCount(0)
      await expect(workspaceNavigator.getByRole('button').first()).toContainText('Application route')
      await expect(workspaceNavigator.getByRole('button', { name: /Applicant details/ })).toBeVisible()
      await expect(workspaceNavigator.getByRole('button', { name: /Personal details/ })).toHaveCount(0)
      await expect(page.getByRole('heading', { name: 'Applicant details', exact: true })).toBeVisible()
      await expect(page.getByLabel('First name')).not.toBeEditable()
      await expect(page.getByLabel('First name')).toHaveValue('Browser')
      await expect(page.getByLabel('Last name')).not.toBeEditable()
      await expect(page.getByLabel('Last name')).toHaveValue('Applicant')

      const profileSave = page.waitForResponse(response =>
        response.url().includes('/api/admissions/applications/')
        && response.url().endsWith('/profile')
        && response.request().method() === 'PUT'
        && response.ok(),
      )
      await selectOption(page, 'Title', 'Mr')
      await page.getByLabel('Date of birth').fill('1999-01-15')
      await selectOption(page, 'Gender', 'MALE')
      await selectOption(page, 'Marital status', 'SINGLE')
      await page.getByLabel('National ID number').fill(`99-${fixture.codeSuffix}`)
      await selectOptionUntilFieldContains(page, 'Country of residence', /ZW .*Zimbabwe/, 'Zimbabwe')
      await selectOptionUntilFieldContains(page, 'Nationality', /ZW .*Zimbabwe/, 'Zimbabwe')
      const activeSection = page.locator('#application-section-editor')
      await activeSection.getByLabel('Phone number').fill('+263772000001')
      await activeSection.getByLabel('Residential address').fill('630 Churchill Avenue, Harare')
      await profileSave
      await expect(activeSection.getByText('Applicant details complete.')).toBeVisible()

      await clickVisibleButtonContaining(page, 'Continue: Next of kin')
      await expect(page.getByRole('heading', { name: 'Next of kin', exact: true })).toBeVisible()
      await expect(page.getByRole('heading', { name: 'Next of kin details', exact: true })).toBeVisible()
      await expect(page.getByRole('dialog')).toHaveCount(0)
      await page.getByLabel('Full name').fill('Tariro Applicant')
      await selectOption(page, 'Relationship', 'PARENT')
      await page.getByLabel('Phone number').fill('+263772000002')
      await page.getByLabel('Email').fill(`kin-${fixture.codeSuffix.toLowerCase()}@example.test`)
      await page.getByLabel('Address').fill('Harare')
      await page.getByRole('button', { name: 'Save record' }).click()
      await expect(page.getByText('Tariro Applicant')).toBeVisible()

      await clickVisibleButtonContaining(page, 'Continue: Qualifications')
      await expect(page.getByRole('heading', { name: 'Qualifications', exact: true })).toBeVisible()
      await expect(page.getByRole('heading', { name: 'Qualification sitting', exact: true })).toBeVisible()
      await expect(page.getByRole('dialog')).toHaveCount(0)
      await selectOption(page, 'Exam body', new RegExp(`ZIMSEC_${fixture.codeSuffix}`))
      await page.getByLabel('School or institution').fill('Browser Test High School')
      await page.getByLabel('Year written').fill('2024')
      await page.getByLabel('Centre number').fill('C1234')
      await page.getByLabel('Candidate number').fill(`N${fixture.codeSuffix}`)
      await selectOption(page, 'Country', /ZW .*Zimbabwe/)
      await page.getByRole('button', { name: 'Save record' }).click()
      await expect(page.getByText(`Zimbabwe School Examinations Council ${fixture.codeSuffix}`)).toBeVisible()

      await page.getByRole('button', { name: 'Add result' }).click()
      await expect(page.getByRole('heading', { name: 'Add subject results', exact: true })).toBeVisible()
      await selectOption(page, 'Managed subject', new RegExp(`ENG_${fixture.codeSuffix}`))
      await selectOption(page, 'Grade', 'A')
      await page.getByRole('button', { name: 'Add another subject' }).click()
      const secondSubjectField = page.getByLabel('Managed subject').nth(1)
      await secondSubjectField.click()
      await page.getByRole('option', { name: new RegExp(`MATH_${fixture.codeSuffix}`) }).click()
      const secondGradeField = page.getByLabel('Grade').nth(1)
      await secondGradeField.click()
      await page.getByRole('option', { name: 'B', exact: true }).last().click()
      await Promise.all([
        page.waitForResponse(response =>
          response.url().endsWith('/results/batch')
          && response.request().method() === 'POST'
          && response.ok(),
        ),
        page.getByRole('button', { name: 'Save 2 subjects' }).click(),
      ])
      await expect(page.getByText(`English Language ${fixture.codeSuffix}`)).toBeVisible()
      await expect(page.getByText(`Mathematics ${fixture.codeSuffix}`)).toBeVisible()

      await page.getByRole('button', { name: 'Continue: Programme choices', exact: true }).click()
      await expect(page.getByRole('heading', { name: 'Programme choices', exact: true, level: 1 })).toBeVisible()
      await page.getByLabel('Programme choices').click()
      await expect(page.getByRole('option', { name: new RegExp(fixture.programmeCode) })).toBeVisible()
      await page.getByRole('option', { name: new RegExp(fixture.programmeCode) }).click()
      await page.keyboard.press('Escape')
      await page.getByRole('button', { name: 'Save choices', exact: true }).click()
      await dismissSuccessDialog(page)
      await expect(page.getByText(`${fixture.programmeCode} · Browser Verified Programme`, { exact: true }).last()).toBeVisible()
      await page.getByRole('button', { name: 'Continue: Supporting documents', exact: true }).click()
      await expect(page.getByRole('heading', { name: 'Supporting documents', exact: true })).toBeVisible()
      const evidencePath = testInfo.outputPath(`application-evidence-${fixture.codeSuffix}.pdf`)
      mkdirSync(dirname(evidencePath), { recursive: true })
      writeFileSync(evidencePath, `%PDF-1.4\n% eMhare applicant evidence ${fixture.codeSuffix}\n%%EOF\n`)
      await expect(activeSection.locator('p.font-medium').filter({ hasText: 'Identity document' })).toBeVisible()
      await expect(activeSection.locator('p.font-medium').filter({ hasText: 'Academic qualification evidence' })).toBeVisible()
      await uploadEvidence(page, 'Identity document', evidencePath)
      await uploadEvidence(page, 'Academic qualification evidence', evidencePath)
      await expect(activeSection.getByText('Required documents uploaded.', { exact: true })).toBeVisible()
      await clickVisibleButtonContaining(page, 'Continue: Review and declaration')

      await page.waitForFunction(() =>
        Array.from(document.querySelectorAll('h1')).some((heading) =>
          (heading.textContent ?? '').trim() === 'Review and declaration'
        )
      )
      await expect(activeSection.getByText('Review and accept the applicant declaration.', { exact: true })).toBeVisible()
      await expect(activeSection.getByRole('heading', { name: 'Application overview', exact: true })).toBeVisible()
      await expect(activeSection.getByText(fixture.applicationTypeName, { exact: true })).toBeVisible()
      await expect(activeSection.getByRole('heading', { name: 'Applicant details', exact: true })).toBeVisible()
      await expect(activeSection.getByText('Browser', { exact: true })).toBeVisible()
      await expect(activeSection.getByText('Applicant', { exact: true })).toBeVisible()
      await expect(activeSection.getByRole('heading', { name: 'Next of kin', exact: true })).toBeVisible()
      await expect(activeSection.getByText('Tariro Applicant', { exact: true })).toBeVisible()
      await expect(activeSection.getByRole('heading', { name: 'Qualifications and results', exact: true })).toBeVisible()
      await expect(activeSection.getByText(`English Language ${fixture.codeSuffix}`, { exact: true })).toBeVisible()
      await expect(activeSection.getByText(`Mathematics ${fixture.codeSuffix}`, { exact: true })).toBeVisible()
      await expect(activeSection.getByRole('heading', { name: 'Programme choices', exact: true })).toBeVisible()
      await expect(activeSection.getByText(`${fixture.programmeCode} · Browser Verified Programme`, { exact: true })).toBeVisible()
      await expect(activeSection.getByRole('heading', { name: 'Supporting documents', exact: true })).toBeVisible()
      await expect(activeSection.getByText(`application-evidence-${fixture.codeSuffix}.pdf`).first()).toBeVisible()
      await expect(activeSection.getByRole('heading', { name: 'Application fee', exact: true })).toBeVisible()

      const documentDownloadResponse = page.waitForResponse(response =>
        response.url().includes('/api/documents/uploads/')
        && response.url().endsWith('/download')
        && response.request().method() === 'GET'
        && response.ok(),
      )
      await activeSection.getByRole('button', { name: 'Preview Identity document', exact: true }).click()
      await documentDownloadResponse
      await expect(activeSection.getByTitle('Identity document preview')).toBeVisible()
      await activeSection.getByRole('button', { name: 'Close document preview', exact: true }).click()
      await expect(activeSection.getByTitle('Identity document preview')).toHaveCount(0)

      await clickVisibleButtonContaining(page, 'Accept declaration')
      await clickVisibleButtonContaining(page, 'Accept declaration')
      await expect(page.getByText('Ready for submission')).toBeVisible()

      await clickVisibleButtonContaining(page, 'Submit application')
      await clickVisibleButtonContaining(page, 'Submit application')
      await expect(page.getByRole('heading', { name: 'Application submitted' })).toBeVisible()
      await page.getByRole('button', { name: 'OK' }).evaluate((element: HTMLElement) => element.click())
      await expect(page).toHaveURL(applicantPortalUrl + '/')

      await page.screenshot({ path: testInfo.outputPath('applicant-full-journey.png'), fullPage: true })
      expect(failedResponses).toEqual([])
      expect(consoleErrors).toEqual([])
    } finally {
      await cleanupFixture(fixture)
    }
  })

  test('completes a Masters MBA application and receives two confidential references', async ({ page, browser }, testInfo) => {
    test.setTimeout(240_000)
    let fixture: ApplicantFixture | null = null
    try {
      fixture = await createFixture({ postgraduate: true })
      const consoleErrors: string[] = []
      const failedResponses: string[] = []
      page.on('console', message => message.type() === 'error' && consoleErrors.push(message.text()))
      page.on('response', response => {
        if (response.url().includes('/api/') && response.status() >= 400) {
          failedResponses.push(`${response.status()} ${response.request().method()} ${response.url()}`)
        }
      })

      await page.goto(applicantPortalUrl)
      await login(page, fixture)
      await page.getByRole('link', { name: 'Start application' }).first().click()
      await selectOption(page, 'Application type', fixture.applicationTypeName)
      await selectOption(page, 'Intake', fixture.intakeName)
      await Promise.all([
        page.waitForURL(`${applicantPortalUrl}/applications/**`),
        page.getByRole('button', { name: 'Create draft', exact: true })
          .evaluate((element: HTMLElement) => element.click()),
      ])

      const workspaceNavigator = page.getByRole('navigation', { name: 'Application process' })
      await expect(workspaceNavigator.getByRole('button', { name: /Employment history/ })).toBeVisible()
      await expect(workspaceNavigator.getByRole('button', { name: /Referees/ })).toBeVisible()
      await expect(workspaceNavigator.getByRole('button', { name: /Personal details/ })).toHaveCount(0)
      await expect(page.getByLabel('First name')).not.toBeEditable()
      await expect(page.getByLabel('First name')).toHaveValue('Browser')
      await expect(page.getByLabel('Last name')).not.toBeEditable()
      await expect(page.getByLabel('Last name')).toHaveValue('Applicant')

      const profileSave = page.waitForResponse(response =>
        response.url().endsWith('/profile') && response.request().method() === 'PUT' && response.ok(),
      )
      await selectOption(page, 'Title', 'Mr')
      await page.getByLabel('Date of birth').fill('1994-04-12')
      await selectOption(page, 'Gender', 'MALE')
      await selectOption(page, 'Marital status', 'SINGLE')
      await page.getByLabel('National ID number').fill(`94-${fixture.codeSuffix}`)
      await selectOptionUntilFieldContains(page, 'Country of residence', /ZW .*Zimbabwe/, 'Zimbabwe')
      await selectOptionUntilFieldContains(page, 'Nationality', /ZW .*Zimbabwe/, 'Zimbabwe')
      const activeSection = page.locator('#application-section-editor')
      await activeSection.getByLabel('Phone number').fill('+263772100001')
      await activeSection.getByLabel('Residential address').fill('630 Churchill Avenue, Harare')
      await profileSave

      await clickVisibleButtonContaining(page, 'Continue: Next of kin')
      await page.getByLabel('Full name').fill('Tariro Applicant')
      await selectOption(page, 'Relationship', 'PARENT')
      await page.getByLabel('Phone number').fill('+263772100002')
      await page.getByLabel('Email').fill(`kin-${fixture.codeSuffix.toLowerCase()}@example.test`)
      await page.getByLabel('Address').fill('Harare')
      await Promise.all([
        page.waitForResponse(response => response.url().endsWith('/next-of-kin')
          && response.request().method() === 'POST' && response.ok()),
        clickVisibleButtonContaining(page, 'Save record'),
      ])
      await expect(page.getByText('Tariro Applicant')).toBeVisible()

      await clickVisibleButtonContaining(page, 'Continue: Qualifications')
      await selectOption(page, 'Exam body', new RegExp(`ZIMSEC_${fixture.codeSuffix}`))
      await page.getByLabel('School or institution').fill('University of Zimbabwe')
      await page.getByLabel('Year written').fill('2024')
      await page.getByLabel('Centre number').fill('UZ-MBA')
      await page.getByLabel('Candidate number').fill(`MBA${fixture.codeSuffix}`)
      await selectOption(page, 'Country', /ZW .*Zimbabwe/)
      await Promise.all([
        page.waitForResponse(response => response.url().endsWith('/qualifications')
          && response.request().method() === 'POST' && response.ok()),
        clickVisibleButtonContaining(page, 'Save record'),
      ])
      await clickVisibleButtonContaining(page, 'Add result')
      await selectOption(page, 'Managed subject', new RegExp(`ENG_${fixture.codeSuffix}`))
      await selectOption(page, 'Grade', 'A')
      await clickVisibleButtonContaining(page, 'Add another subject')
      await page.getByLabel('Managed subject').nth(1).evaluate((element: HTMLElement) => element.click())
      await page.getByRole('option', { name: new RegExp(`MATH_${fixture.codeSuffix}`) }).click({ force: true })
      await page.getByLabel('Grade').nth(1).evaluate((element: HTMLElement) => element.click())
      await page.getByRole('option', { name: 'B', exact: true }).last().click({ force: true })
      await Promise.all([
        page.waitForResponse(response => response.url().endsWith('/results/batch')
          && response.request().method() === 'POST' && response.ok()),
        clickVisibleButtonContaining(page, 'Save 2 subjects'),
      ])
      await expect(page.getByText(`Mathematics ${fixture.codeSuffix}`)).toBeVisible()

      await clickVisibleButtonContaining(page, 'Continue: Employment history')
      await expect(page.getByRole('heading', { name: 'Employment history', exact: true })).toBeVisible()
      await page.getByLabel('Employer').fill('UZ Business School')
      await page.getByLabel('Position').fill('Operations Manager')
      await page.getByLabel('Started on').fill('2020-01-15')
      await page.getByLabel('Ended on').fill('2025-12-31')
      await page.getByLabel('Responsibilities').fill('Leading operational planning, people management, and service improvement.')
      await Promise.all([
        page.waitForResponse(response => response.url().endsWith('/employment-history')
          && response.request().method() === 'POST' && response.ok()),
        clickVisibleButtonContaining(page, 'Save record'),
      ])
      await expect(page.getByText('Operations Manager · UZ Business School')).toBeVisible()

      await clickVisibleButtonContaining(page, 'Continue: Referees')
      await expect(page.getByRole('heading', { name: 'Referees', exact: true })).toBeVisible()
      await page.getByLabel('Title').fill('Dr')
      await page.getByLabel('Full name').fill('Tariro Dube')
      await page.getByLabel('Organisation').fill('University of Zimbabwe')
      await page.getByLabel('Position').fill('Dean')
      await page.getByLabel('Email').fill(fixture.firstRefereeEmail)
      await page.getByLabel('Phone number').fill('+263772100003')
      await Promise.all([
        page.waitForResponse(response => response.url().endsWith('/referees')
          && response.request().method() === 'POST' && response.ok()),
        clickVisibleButtonContaining(page, 'Save record'),
      ])
      await expect(page.getByText('Tariro Dube')).toBeVisible()
      await expect(page.getByText('Invitation sent').first()).toBeVisible()

      await page.getByLabel('Title').fill('Prof')
      await page.getByLabel('Full name').fill('Rutendo Moyo')
      await page.getByLabel('Organisation').fill('Graduate School of Management')
      await page.getByLabel('Position').fill('Programme Director')
      await page.getByLabel('Email').fill(fixture.secondRefereeEmail)
      await page.getByLabel('Phone number').fill('+263772100004')
      await Promise.all([
        page.waitForResponse(response => response.url().endsWith('/referees')
          && response.request().method() === 'POST' && response.ok()),
        clickVisibleButtonContaining(page, 'Save record'),
      ])
      await expect(page.getByText('Rutendo Moyo')).toBeVisible()

      const [firstInvitation, secondInvitation] = await Promise.all([
        waitForReferenceInvitation(fixture.firstRefereeEmail),
        waitForReferenceInvitation(fixture.secondRefereeEmail),
      ])
      expect(firstInvitation.providerCode).toBe('LOCAL_LOG')
      expect(secondInvitation.providerCode).toBe('LOCAL_LOG')

      const firstRefereeContext = await browser.newContext()
      const secondRefereeContext = await browser.newContext()
      try {
        await Promise.all([
          submitConfidentialReference(await firstRefereeContext.newPage(), firstInvitation.responseUrl, 'Line manager'),
          submitConfidentialReference(await secondRefereeContext.newPage(), secondInvitation.responseUrl, 'Academic supervisor'),
        ])
      } finally {
        await firstRefereeContext.close()
        await secondRefereeContext.close()
      }

      await page.reload()
      await workspaceNavigator.getByRole('button', { name: /Referees/ })
        .evaluate((element: HTMLElement) => element.click())
      await expect(activeSection.locator('.rounded-lg').filter({ hasText: 'Tariro Dube' }).first()).toContainText('Reference received')
      await expect(activeSection.locator('.rounded-lg').filter({ hasText: 'Rutendo Moyo' }).first()).toContainText('Reference received')

      await clickVisibleButtonContaining(page, 'Continue: Programme choices')
      await page.getByLabel('Programme choices').evaluate((element: HTMLElement) => element.click())
      await page.getByRole('option', { name: new RegExp(fixture.programmeCode) }).click({ force: true })
      await page.keyboard.press('Escape')
      await clickVisibleButtonContaining(page, 'Save choices')
      await dismissSuccessDialog(page)
      await expect(page.getByText(`${fixture.programmeCode} · Master of Business Administration`, { exact: true }).last()).toBeVisible()

      await clickVisibleButtonContaining(page, 'Continue: Supporting documents')
      const evidencePath = testInfo.outputPath(`mba-evidence-${fixture.codeSuffix}.pdf`)
      mkdirSync(dirname(evidencePath), { recursive: true })
      writeFileSync(evidencePath, `%PDF-1.4\n% MBA application evidence ${fixture.codeSuffix}\n%%EOF\n`)
      await uploadEvidence(page, 'Identity document', evidencePath)
      await uploadEvidence(page, 'Academic qualification evidence', evidencePath)
      await clickVisibleButtonContaining(page, 'Continue: Review and declaration')
      await expect(page.getByRole('heading', { name: 'Review and declaration', exact: true })).toBeVisible()
      await expect(activeSection.getByText(fixture.applicationTypeName, { exact: true })).toBeVisible()
      await expect(activeSection.getByRole('heading', { name: 'Employment history', exact: true })).toBeVisible()
      await expect(activeSection.getByRole('heading', { name: 'Referees', exact: true })).toBeVisible()
      await expect(activeSection.getByRole('heading', {
        name: `${fixture.programmeCode} · Master of Business Administration`, exact: true,
      })).toBeVisible()

      await clickVisibleButtonContaining(page, 'Accept declaration')
      await clickVisibleButtonContaining(page, 'Accept declaration')
      await expect(page.getByText('Ready for submission')).toBeVisible()
      await clickVisibleButtonContaining(page, 'Submit application')
      await clickVisibleButtonContaining(page, 'Submit application')
      await expect(page.getByRole('heading', { name: 'Application submitted' })).toBeVisible()
      await page.getByRole('button', { name: 'OK' }).evaluate((element: HTMLElement) => element.click())
      await expect(page).toHaveURL(applicantPortalUrl + '/')
      await expect(page.getByRole('heading', { name: 'Application submitted' })).toBeHidden()
      await page.waitForLoadState('networkidle')
      await expect(page.getByText(fixture.applicationTypeName, { exact: true })).toBeVisible()
      await expect(page.getByText('Before submission', { exact: true })).toHaveCount(0)
      await expect(page.getByText('Documents & fee', { exact: true })).toHaveClass(/font-medium/)

      await page.screenshot({ path: testInfo.outputPath('mba-application-complete.png'), fullPage: true })
      expect(failedResponses).toEqual([])
      expect(consoleErrors).toEqual([])
    } finally {
      await cleanupFixture(fixture)
    }
  })
})
