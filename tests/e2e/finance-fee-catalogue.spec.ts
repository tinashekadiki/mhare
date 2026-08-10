import { expect, request as playwrightRequest, test, type Page, type Route } from '@playwright/test'
import { spawnSync } from 'node:child_process'
import { randomUUID } from 'node:crypto'

const keycloakBaseUrl = process.env.KEYCLOAK_URL ?? 'http://localhost:8099'
const username = `finance-fees-ui-${randomUUID()}@example.test`
const password = 'Temporary-Finance-UI-Password-42'
let keycloakUserId = ''

async function adminContext() {
  const context = await playwrightRequest.newContext()
  const response = await context.post(`${keycloakBaseUrl}/realms/master/protocol/openid-connect/token`, {
    form: { client_id: 'admin-cli', username: 'admin', password: 'admin', grant_type: 'password' }
  })
  const payload = await response.json()
  await context.dispose()
  return playwrightRequest.newContext({ extraHTTPHeaders: { Authorization: `Bearer ${payload.access_token}`, 'Content-Type': 'application/json' } })
}

test.beforeAll(async () => {
  const admin = await adminContext()
  const created = await admin.post(`${keycloakBaseUrl}/admin/realms/emhare/users`, {
    data: { username, email: username, firstName: 'Finance', lastName: 'Operator', enabled: true, emailVerified: true, credentials: [{ type: 'password', value: password, temporary: false }] }
  })
  expect(created.status()).toBe(201)
  keycloakUserId = created.headers().location!.split('/').at(-1)!
  const role = await admin.get(`${keycloakBaseUrl}/admin/realms/emhare/roles/system-admin`)
  expect((await admin.post(`${keycloakBaseUrl}/admin/realms/emhare/users/${keycloakUserId}/role-mappings/realm`, { data: [await role.json()] })).status()).toBe(204)
  await admin.dispose()
})

test.afterAll(async () => {
  if (keycloakUserId) {
    const admin = await adminContext()
    await admin.delete(`${keycloakBaseUrl}/admin/realms/emhare/users/${keycloakUserId}`)
    await admin.dispose()
  }
  const result = spawnSync('docker', ['exec', '-i', 'emhare-postgres', 'psql', '-q', '-U', 'postgres', '-d', 'emhare_core_identity'], {
    input: `BEGIN; SET LOCAL session_replication_role=replica; DELETE FROM user_role_assignments_aud WHERE user_id IN (SELECT id FROM users WHERE email='${username}'); DELETE FROM user_role_assignments WHERE user_id IN (SELECT id FROM users WHERE email='${username}'); DELETE FROM login_events_aud WHERE user_id IN (SELECT id FROM users WHERE email='${username}'); DELETE FROM login_events WHERE user_id IN (SELECT id FROM users WHERE email='${username}'); DELETE FROM users_aud WHERE email='${username}'; DELETE FROM users WHERE email='${username}'; COMMIT;`,
    encoding: 'utf8'
  })
  if (result.status !== 0) throw new Error(result.stderr)
})

async function login(
  page: Page,
  discountRoute: ((route: Route) => Promise<void> | void) | null = route => route.fulfill({ json: { discounts: [] } }),
  applicantCategoryRoute: ((route: Route) => Promise<void> | void) | null = route => route.fulfill({ json: [
    { code: 'LOCAL', label: 'Local applicant' },
    { code: 'SADC', label: 'SADC applicant' },
    { code: 'INTERNATIONAL', label: 'International applicant' },
    { code: 'CLE', label: 'Continuing legal education applicant' }
  ] })
) {
  if (discountRoute) await page.route('**/api/finance/student-discounts', discountRoute)
  if (applicantCategoryRoute) await page.route('**/api/admissions/applications/applicant-categories', applicantCategoryRoute)
  await page.locator('#username').fill(username)
  await page.locator('#password').fill(password)
  await page.locator('#kc-login').click()
  await page.waitForURL(/http:\/\/localhost:3000\/operations\/finance-fees.*/, { timeout: 30_000 })
  await page.waitForLoadState('networkidle')
}

function academicOverviewFixture() {
  const academicPeriodId = randomUUID()
  const facultyId = randomUUID()
  const departmentId = randomUUID()
  const programmeTypeId = randomUUID()
  const programmeLevelId = randomUUID()
  const programmeId = randomUUID()
  return {
    academicPeriodId,
    facultyId,
    departmentId,
    programmeTypeId,
    programmeLevelId,
    programmeId,
    overview: {
      academicUnitTypes: [],
      academicUnits: [
        { id: facultyId, academicUnitTypeId: randomUUID(), academicUnitTypeCode: 'FACULTY', parentId: null, code: 'COMM', name: 'Faculty of Commerce', status: 'ACTIVE', legacyFacultyCode: null, legacyDepartmentCode: null, version: 0 },
        { id: departmentId, academicUnitTypeId: randomUUID(), academicUnitTypeCode: 'DEPARTMENT', parentId: facultyId, code: 'ACC', name: 'Department of Accounting', status: 'ACTIVE', legacyFacultyCode: null, legacyDepartmentCode: null, version: 0 }
      ],
      academicYears: [{ id: randomUUID(), name: '2027', startDate: '2027-01-01', endDate: '2027-12-31', status: 'OPEN', changeReason: 'Opened for testing', version: 0 }],
      academicPeriodTypes: [],
      academicPeriods: [{ id: academicPeriodId, academicYearId: randomUUID(), academicYearName: '2027', academicPeriodTypeId: randomUUID(), academicPeriodTypeName: 'Semester', code: '2027-S1', name: 'Semester 1', startDate: '2027-01-06', endDate: '2027-06-20', status: 'OPEN', changeReason: 'Opened for testing', version: 0 }],
      intakes: [],
      programmeLevels: [{ id: programmeLevelId, code: 'UG', name: 'Undergraduate', sortOrder: 10, status: 'ACTIVE', version: 0 }],
      programmeTypes: [{ id: programmeTypeId, code: 'DEGREE', name: 'Degree', status: 'ACTIVE', version: 0 }],
      programmes: [{ id: programmeId, code: 'BACC', name: 'Bachelor of Accountancy', awardName: 'Bachelor of Accountancy', owningAcademicUnitId: departmentId, owningAcademicUnitName: 'Department of Accounting', programmeTypeId, programmeTypeName: 'Degree', programmeLevelId, programmeLevelName: 'Undergraduate', minimumDurationPeriods: 8, maximumDurationPeriods: 10, status: 'ACTIVE', legacyProgrammeCode: null, changeReason: 'Created', version: 0 }],
      modules: []
    }
  }
}

for (const viewport of [{ name: 'desktop', width: 1440, height: 1000 }, { name: 'mobile', width: 390, height: 844 }]) {
  test(`presents governed fee definitions and effective pricing on ${viewport.name}`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height })
    const catalogueId = randomUUID()
    const draftRuleId = randomUUID()
    const unratedRuleId = randomUUID()
    const register = {
      catalogues: [{
        id: catalogueId, code: 'TUITION-UG', name: 'Undergraduate tuition', description: 'Approved tuition charge for registered undergraduate students.',
        chargeType: 'PROGRAMME', receivableAccountCode: 'AR-STUDENT', revenueAccountCode: 'REV-TUITION', taxCode: null,
        baseCurrencyCode: 'USD', status: 'ACTIVE', preparedByUserId: randomUUID(), activatedByUserId: randomUUID(), activatedAt: '2027-01-03T08:00:00Z', version: 1,
        rules: [{
          id: draftRuleId, ruleVersion: 2, transactionCurrencyCode: 'USD', transactionAmount: 750, baseCurrencyCode: 'USD', exchangeRateId: null,
          baseAmount: 750, ratingStatus: 'RATED', effectiveFrom: '2027-01-01T00:00:00Z', effectiveUntil: '2027-06-30T23:59:59Z', scopeSignature: null,
          status: 'DRAFT', preparedByUserId: randomUUID(), version: 0,
          scopes: [{ id: randomUUID(), scopeDimension: 'PROGRAMME', referenceId: randomUUID(), referenceCode: 'BACC', referenceName: 'Bachelor of Accountancy' }]
        }, {
          id: unratedRuleId, ruleVersion: 1, transactionCurrencyCode: 'ZWG', transactionAmount: 2000, baseCurrencyCode: 'USD', exchangeRateId: null,
          baseAmount: null, ratingStatus: 'UNRATED', effectiveFrom: '2026-08-01T00:00:00Z', effectiveUntil: null, scopeSignature: null,
          status: 'PENDING_RATE', preparedByUserId: randomUUID(), version: 0,
          scopes: [{ id: randomUUID(), scopeDimension: 'ACADEMIC_PERIOD', referenceId: randomUUID(), referenceCode: '2026-S2', referenceName: '2026 Semester 2' }]
        }]
      }]
    }
    await page.route('**/api/finance/fee-catalogues', route => route.fulfill({ json: register }))
    const academic = academicOverviewFixture()
    await page.route('**/api/academic/overview', route => route.fulfill({ json: academic.overview }))
    await page.route('**/api/finance/fee-structures', route => route.fulfill({ json: { structures: [{
      id: randomUUID(), code: '2027-S1-BACC', name: 'BAcc semester 1 fees', description: 'Complete programme fee schedule.',
      feeContext: 'ACADEMIC', scopeType: 'PROGRAMME', scopeReferenceId: academic.programmeId, scopeReferenceCode: 'BACC',
      scopeReferenceName: 'Bachelor of Accountancy', programmeLevelId: academic.programmeLevelId, programmeLevelCode: 'UG',
      programmeLevelName: 'Undergraduate', academicPeriodId: academic.academicPeriodId, academicPeriodCode: '2027-S1',
      academicPeriodName: 'Semester 1', programmePeriodNumber: 1, applicantCategoryCode: null, transactionCurrencyCode: 'USD',
      effectiveFrom: '2027-01-06T00:00:00Z', effectiveUntil: '2027-06-21T00:00:00Z', status: 'ACTIVE', preparedByUserId: randomUUID(),
      activatedByUserId: randomUUID(), activatedAt: '2027-01-03T08:00:00Z', version: 1, lines: [
        { feeRuleId: randomUUID(), lineNumber: 1, feeCatalogueId: catalogueId, feeCode: 'TUITION-UG', feeName: 'Tuition', description: 'Semester tuition', chargeType: 'PROGRAMME', receivableAccountCode: 'AR-STUDENT', revenueAccountCode: 'REV-TUITION', taxCode: null, transactionAmount: 750, transactionCurrencyCode: 'USD', baseAmount: 750, ratingStatus: 'RATED', status: 'APPROVED' },
        { feeRuleId: randomUUID(), lineNumber: 2, feeCatalogueId: randomUUID(), feeCode: 'STUDENT-LEVY', feeName: 'Student levy', description: 'Student services levy', chargeType: 'PROGRAMME', receivableAccountCode: 'AR-STUDENT', revenueAccountCode: 'REV-LEVY', taxCode: null, transactionAmount: 40, transactionCurrencyCode: 'USD', baseAmount: 40, ratingStatus: 'RATED', status: 'APPROVED' }
      ],
      attachments: [],
      selectedAttachment: null
    }] } }))
    const errors: string[] = []
    page.on('pageerror', error => errors.push(error.message))
    page.on('console', message => message.type() === 'error' && errors.push(message.text()))

    await page.goto('/operations/finance-fees')
    await login(page)

    const structureRegister = page.getByLabel('Fee structure register')
    await expect(page.getByText('One complete schedule wins')).toBeVisible()
    await expect(page.getByText('BAcc semester 1 fees')).toBeVisible()
    await expect(structureRegister.getByText('Programme · BACC · Bachelor of Accountancy', { exact: true })).toBeVisible()
    await expect(page.getByText('2 governed charges')).toBeVisible()
    await expect(structureRegister.getByText('1–1 of 1 records · Page 1 of 1')).toBeVisible()
    await expect(structureRegister.getByLabel('Records per page')).toContainText('10 per page')
    await expect(page.getByRole('button', { name: 'Show line items (2)' })).toBeVisible()
    await page.getByRole('button', { name: 'Show line items (2)' }).click()
    await expect(page.getByText('Complete schedule total')).toBeVisible()
    const lineItemTab = page.getByRole('tab', { name: /Line-item catalogue/ })
    await lineItemTab.click()
    await expect(lineItemTab).toHaveAttribute('aria-selected', 'true')
    await expect(page.getByText('Undergraduate tuition', { exact: true }).first()).toBeVisible()
    await expect(page.getByText('Rate required')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Apply rate' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Approve' })).toBeVisible()
    await expect(page.getByText('US$750.00', { exact: true }).first()).toBeVisible()

    await page.getByRole('button', { name: 'Add price' }).first().click()
    const priceDrawer = page.getByRole('dialog')
    await expect(priceDrawer.getByText('Add effective price · TUITION-UG')).toBeVisible()
    await expect(page.getByText('A price is selected only when every scope dimension and effective date matches the billing event.')).toBeVisible()
    await priceDrawer.getByRole('button', { name: 'Close' }).click()
    await expect(priceDrawer).toBeHidden()
    expect(errors).toEqual([])
  })
}

test('creates a complete academic fee structure with governed line items', async ({ page }) => {
  const academic = academicOverviewFixture()
  let submittedStructure: Record<string, any> | undefined
  await page.route('**/api/academic/overview', route => route.fulfill({ json: academic.overview }))
  await page.route('**/api/finance/fee-catalogues', route => route.fulfill({ json: { catalogues: [] } }))
  await page.route('**/api/finance/fee-structures', async route => {
    if (route.request().method() === 'POST') {
      submittedStructure = route.request().postDataJSON()
      await route.fulfill({ json: { id: randomUUID(), ...submittedStructure, status: 'DRAFT', version: 0, lines: [] } })
      return
    }
    await route.fulfill({ json: { structures: [] } })
  })

  await page.goto('/operations/finance-fees')
  await login(page)
  await page.getByRole('button', { name: 'New fee structure' }).click()
  const drawer = page.getByRole('dialog')
  await expect(drawer.getByText('Create fee structure')).toBeVisible()
  await drawer.getByLabel('Structure code').fill('BACC-BASE-FEES')
  await drawer.getByLabel('Structure name').fill('Institution base fees')
  await drawer.getByLabel('Programme level').click()
  await page.getByRole('option', { name: 'UG · Undergraduate' }).click()
  await page.getByText('Applicability and precedence', { exact: true }).click()
  await expect(drawer.getByLabel('Receivable account').first()).toHaveValue('AR-STUDENT')
  await expect(drawer.getByLabel('Revenue account').first()).toHaveValue('REV-TUITION')
  await drawer.getByRole('button', { name: 'Add line item' }).click()
  await page.getByText('Student levy', { exact: true }).click()
  const amounts = drawer.getByLabel('Amount')
  await amounts.nth(0).fill('800')
  await amounts.nth(0).press('Tab')
  await expect(drawer.getByLabel('Revenue account').nth(1)).toHaveValue('REV-STUDENT-LEVY')
  await amounts.nth(1).fill('50')
  await amounts.nth(1).press('Tab')
  await drawer.getByRole('button', { name: 'Create draft structure' }).click()

  await expect.poll(() => submittedStructure).toBeTruthy()
  expect(submittedStructure).toMatchObject({
    code: 'BACC-BASE-FEES', feeContext: 'ACADEMIC', scopeType: 'INSTITUTION',
    programmeLevelId: academic.programmeLevelId, programmeLevelCode: 'UG', programmeLevelName: 'Undergraduate',
    academicPeriodId: null, programmePeriodNumber: null, transactionCurrencyCode: 'USD'
  })
  expect(submittedStructure!.lines).toHaveLength(2)
  expect(submittedStructure!.lines[0]).toMatchObject({ feeCode: 'TUITION', feeName: 'Tuition', amount: 800 })
  expect(submittedStructure!.lines[1]).toMatchObject({ feeCode: 'STUDENT-LEVY', feeName: 'Student levy', amount: 50 })
  expect(submittedStructure!.attachments).toEqual([])
})

test('scopes an application fee by programme level and a portal applicant category', async ({ page }) => {
  const academic = academicOverviewFixture()
  let submittedStructure: Record<string, any> | undefined
  await page.route('**/api/academic/overview', route => route.fulfill({ json: academic.overview }))
  await page.route('**/api/finance/fee-catalogues', route => route.fulfill({ json: { catalogues: [] } }))
  await page.route('**/api/finance/fee-structures', async (route) => {
    if (route.request().method() === 'POST') {
      submittedStructure = route.request().postDataJSON()
      await route.fulfill({ json: { id: randomUUID(), ...submittedStructure, status: 'DRAFT', version: 0, lines: [] } })
      return
    }
    await route.fulfill({ json: { structures: [] } })
  })

  await page.goto('/operations/finance-fees')
  await login(page)
  await page.getByRole('button', { name: 'New fee structure' }).click()
  const drawer = page.getByRole('dialog')
  await drawer.getByLabel('Fee class').click()
  await page.getByRole('option', { name: 'Application fee' }).click()
  await page.getByText('Applicability and precedence', { exact: true }).click()
  await expect(drawer.getByLabel('Programme level')).toBeVisible()
  await expect(drawer.getByLabel('Applicant category')).toBeVisible()
  await drawer.getByLabel('Programme level').click()
  await page.getByRole('option', { name: 'UG · Undergraduate' }).click()
  await page.getByText('Applicability and precedence', { exact: true }).click()
  await drawer.getByLabel('Applicant category').click()
  await page.getByRole('option', { name: 'Local applicant' }).click()
  await page.getByText('Applicability and precedence', { exact: true }).click()
  await drawer.getByLabel('Structure code').fill('APP-UG-LOCAL')
  await drawer.getByLabel('Structure name').fill('Local undergraduate application fee')
  await drawer.getByLabel('Amount').fill('25')
  await drawer.getByLabel('Amount').press('Tab')
  await drawer.getByRole('button', { name: 'Create draft structure' }).click()

  await expect.poll(() => submittedStructure).toBeTruthy()
  expect(submittedStructure).toMatchObject({
    code: 'APP-UG-LOCAL', feeContext: 'APPLICATION', scopeType: 'PROGRAMME_LEVEL',
    scopeReferenceId: academic.programmeLevelId, scopeReferenceCode: 'UG', scopeReferenceName: 'Undergraduate',
    programmeLevelId: academic.programmeLevelId, programmeLevelCode: 'UG', programmeLevelName: 'Undergraduate',
    applicantCategoryCode: 'LOCAL'
  })
})

test('configures a standalone institution attachment discount', async ({ page }) => {
  const academic = academicOverviewFixture()
  let submittedDiscount: Record<string, any> | undefined
  await page.route('**/api/academic/overview', route => route.fulfill({ json: academic.overview }))
  await page.route('**/api/finance/fee-catalogues', route => route.fulfill({ json: { catalogues: [] } }))
  await page.route('**/api/finance/fee-structures', route => route.fulfill({ json: { structures: [] } }))

  await page.goto('/operations/finance-fees')
  await login(page, async (route) => {
    if (route.request().method() === 'POST') {
      submittedDiscount = route.request().postDataJSON()
      await route.fulfill({ json: { id: randomUUID(), ...submittedDiscount, status: 'DRAFT', version: 0, applicableProgrammes: submittedDiscount?.applicableProgrammes ?? [] } })
      return
    }
    await route.fulfill({ json: { discounts: [] } })
  })

  await page.getByRole('tab', { name: /Student discounts/ }).click()
  await expect(page.getByRole('tab', { name: /Student discounts/ })).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByText('One discount wins')).toBeVisible()
  await page.getByRole('button', { name: 'New student discount' }).click()
  const drawer = page.getByRole('dialog')
  await drawer.getByLabel('Discount code').fill('ATTACHMENT-2027')
  await drawer.getByLabel('Discount name').fill('Student attachment discount')
  await drawer.getByLabel('Authority reference').fill('Finance Committee minute FC-2027-04')
  await drawer.getByLabel('Discount percentage').fill('15')
  await drawer.getByLabel('Effective from').fill('2027-01-01T00:00')
  await drawer.getByLabel('All programme periods').uncheck()
  await drawer.getByText('Select eligible study periods', { exact: true }).click()
  await page.getByRole('option', { name: 'Year 3 · Semester 1 · Period 5' }).click()
  await page.getByRole('option', { name: 'Year 3 · Semester 2 · Period 6' }).click()
  await page.keyboard.press('Escape')
  await drawer.getByRole('button', { name: 'Create draft discount' }).click()

  await expect.poll(() => submittedDiscount).toBeTruthy()
  expect(submittedDiscount).toMatchObject({
    code: 'ATTACHMENT-2027', scopeType: 'INSTITUTION', scopeDepth: 0,
    targetType: 'ALL_FEES', feeCatalogueId: null, discountPercentage: 15,
    authorityReference: 'Finance Committee minute FC-2027-04'
  })
  expect(submittedDiscount!.applicableProgrammes).toEqual([{
    programmeId: academic.programmeId, programmeCode: 'BACC', programmeName: 'Bachelor of Accountancy',
    programmePeriodNumbers: [5, 6]
  }])
})

test('loads the live fee structure workspace through the gateway', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => message.type() === 'error' && errors.push(message.text()))

  await page.goto('/operations/finance-fees')
  await login(page, null, null)
  await expect(page.getByRole('heading', { name: 'Fee structure register' })).toBeVisible()
  await expect(page.getByText('One complete schedule wins')).toBeVisible()
  await page.getByRole('button', { name: 'New fee structure' }).click()
  const drawer = page.getByRole('dialog')
  await expect(drawer.getByText('Structure purpose')).toBeVisible()
  await expect(drawer.getByText('Fee line items')).toBeVisible()
  await expect(drawer.getByLabel('Scope level')).toBeVisible()
  await drawer.getByLabel('Fee class').click()
  await page.getByRole('option', { name: 'Application fee' }).click()
  await page.getByText('Applicability and precedence', { exact: true }).click()
  await expect(drawer.getByLabel('Programme level')).toBeVisible()
  await expect(drawer.getByLabel('Applicant category')).toContainText('All applicant categories')
  await drawer.getByLabel('Applicant category').click()
  await expect(page.getByRole('option', { name: 'Local applicant' })).toBeVisible()
  await expect(page.getByRole('option', { name: 'SADC applicant' })).toBeVisible()
  await expect(page.getByRole('option', { name: 'International applicant' })).toBeVisible()
  await expect(page.getByRole('option', { name: 'Continuing legal education applicant' })).toBeVisible()
  await page.keyboard.press('Escape')
  await drawer.getByRole('button', { name: 'Close' }).click()
  await expect(drawer).toBeHidden()
  await page.getByRole('tab', { name: /Student discounts/ }).click()
  await page.getByRole('button', { name: 'New student discount' }).click()
  const discountDrawer = page.getByRole('dialog')
  await expect(discountDrawer.getByText('Programme-period applicability')).toBeVisible()
  await expect(discountDrawer.getByRole('columnheader', { name: 'Eligible programme periods' })).toBeVisible()
  await expect(discountDrawer.getByText('For semester programmes, Year 3 semesters 1 and 2 are periods 5 and 6.')).toBeVisible()
  await discountDrawer.getByRole('button', { name: 'Close' }).click()
  await expect(discountDrawer).toBeHidden()
  expect(errors).toEqual([])
})

test('presents the governed billing, collections, corrections, and student account workspaces', async ({ page }) => {
  const accountId = randomUUID()
  const invoiceId = randomUUID()
  const invoiceLineId = randomUUID()
  const paymentId = randomUUID()
  const billingRegister = {
    billingPolicies: [{
      id: randomUUID(), code: 'REG-TUITION', policyVersion: 1, name: 'Registration tuition', sourceEventType: 'REGISTRATION_CONFIRMED',
      feeCatalogueId: randomUUID(), feeCode: 'TUITION-UG', feeName: 'Undergraduate tuition', lineBasis: 'REGISTRATION',
      quantityBasis: 'FIXED', fixedQuantity: 1, effectiveFrom: '2026-08-01T00:00:00Z', effectiveUntil: null, status: 'ACTIVE',
      preparedByUserId: randomUUID(), activatedByUserId: randomUUID(), activatedAt: '2026-08-01T08:00:00Z', version: 1
    }],
    billingEvents: [{
      id: randomUUID(), eventNumber: 'EMH-BEV-0000000042', sourceService: 'student-records-service', sourceEventType: 'REGISTRATION_CONFIRMED',
      sourceEventId: randomUUID(), sourceAggregateType: 'REGISTRATION', sourceAggregateId: randomUUID(), sourceLineReference: '2026-S2-REGISTRATION',
      studentFinanceAccountId: accountId, accountNumber: 'EMH-SFA-0000000042', studentId: randomUUID(), studentNumber: 'R260042A',
      feeCatalogueId: randomUUID(), feeCode: 'TUITION-UG', feeName: 'Undergraduate tuition', feeRuleId: randomUUID(), feeRuleVersion: 1,
      description: '2026 semester 2 registration tuition', quantity: 1, transactionCurrencyCode: 'USD', transactionUnitAmount: 100,
      transactionAmount: 100, baseCurrencyCode: 'USD', exchangeRateId: null, baseUnitAmount: 100, baseAmount: 100,
      effectiveAt: '2026-08-02T08:00:00Z', status: 'PENDING_APPROVAL', preparedByUserId: randomUUID(), submittedAt: '2026-08-02T08:01:00Z',
      approvedByUserId: null, approvedAt: null, invoicedAt: null, version: 0, scopes: []
    }],
    invoices: [{
      id: invoiceId, invoiceNumber: 'EMH-INV-0000000042', studentFinanceAccountId: accountId, accountNumber: 'EMH-SFA-0000000042',
      studentId: randomUUID(), studentNumber: 'R260042A', transactionCurrencyCode: 'USD', baseCurrencyCode: 'USD', grossTransactionAmount: 100,
      grossBaseAmount: 100, invoiceDate: '2026-08-02', dueDate: '2026-08-31', status: 'POSTED', postedByUserId: randomUUID(),
      postedAt: '2026-08-02T09:00:00Z', version: 0, lines: [{ id: invoiceLineId, lineNumber: 1, billingEventId: randomUUID(),
        billingEventNumber: 'EMH-BEV-0000000041', feeCode: 'TUITION-UG', description: 'Registration tuition', quantity: 1,
        transactionAmount: 100, baseAmount: 100, receivableAccountCode: 'AR-STUDENT', revenueAccountCode: 'REV-TUITION' }]
    }]
  }
  const collectionsRegister = {
    exchangeRates: [{
      id: randomUUID(), sourceCurrencyCode: 'ZWG', baseCurrencyCode: 'USD', rateToBase: 0.04, effectiveFrom: '2026-08-01T00:00:00Z',
      effectiveTo: null, sourceName: 'Reserve Bank of Zimbabwe', sourceReference: 'RBZ-2026-08', status: 'ACTIVE',
      preparedByUserId: randomUUID(), approvedByUserId: randomUUID(), approvedAt: '2026-08-01T09:00:00Z', retiredByUserId: null,
      retiredAt: null, version: 1
    }],
    payments: [{
      id: paymentId, paymentNumber: 'EMH-SPY-0000000042', studentFinanceAccountId: accountId, accountNumber: 'EMH-SFA-0000000042',
      payerName: 'Tariro Moyo', providerCode: 'BANK', providerTransactionReference: 'BANK-000042', paymentChannel: 'BANK_TRANSFER',
      transactionCurrencyCode: 'USD', transactionAmount: 40, baseCurrencyCode: 'USD', exchangeRateId: null, baseAmount: 40, ratingStatus: 'RATED',
      paidAt: '2026-08-04T10:00:00Z', reconciliationStatus: 'RECONCILED', capturedByUserId: randomUUID(), capturedAt: '2026-08-04T10:05:00Z',
      reconciledByUserId: randomUUID(), reconciledAt: '2026-08-04T11:00:00Z', inSuspense: false, reversed: false,
      receiptNumber: 'EMH-SRC-0000000042', version: 1
    }],
    receipts: [{ id: randomUUID(), paymentId, paymentNumber: 'EMH-SPY-0000000042', receiptNumber: 'EMH-SRC-0000000042',
      studentFinanceAccountId: accountId, accountNumber: 'EMH-SFA-0000000042', issuedAt: '2026-08-04T11:00:00Z' }],
    allocations: [{
      id: randomUUID(), allocationNumber: 'EMH-SAL-0000000042', paymentId, paymentNumber: 'EMH-SPY-0000000042', invoiceId,
      invoiceNumber: 'EMH-INV-0000000042', transactionCurrencyCode: 'USD', transactionAmount: 40, paymentBaseAmount: 40,
      invoiceBaseAmount: 40, realisedExchangeDifference: 0, allocatedByUserId: randomUUID(), allocatedAt: '2026-08-04T11:05:00Z',
      reversed: false, reversalNumber: null, version: 0
    }],
    creditNotes: [{
      id: randomUUID(), creditNoteNumber: 'EMH-SCN-0000000042', invoiceId, invoiceNumber: 'EMH-INV-0000000042', transactionCurrencyCode: 'USD',
      transactionAmount: 10, baseCurrencyCode: 'USD', baseAmount: 10, creditNoteDate: '2026-08-05', status: 'DRAFT',
      preparedByUserId: randomUUID(), preparedAt: '2026-08-05T08:00:00Z', postedByUserId: null, postedAt: null, version: 0,
      lines: [{ id: randomUUID(), lineNumber: 1, invoiceLineId, transactionAmount: 10, baseAmount: 10, reason: 'Approved fee correction' }]
    }]
  }
  const account = {
    id: accountId, accountNumber: 'EMH-SFA-0000000042', studentId: randomUUID(), studentNumber: 'R260042A',
    primaryEmail: 'tariro.moyo@example.test', status: 'ACTIVE', baseBalance: 50
  }
  const statement = {
    account,
    lines: [
      { lineType: 'INVOICE', reference: 'EMH-INV-0000000042', occurredAt: '2026-08-02T09:00:00Z', description: 'Posted student invoice',
        transactionCurrencyCode: 'USD', transactionDebit: 100, transactionCredit: 0, baseCurrencyCode: 'USD', baseDebit: 100, baseCredit: 0, runningBaseBalance: 100 },
      { lineType: 'PAYMENT', reference: 'EMH-SPY-0000000042', occurredAt: '2026-08-04T11:00:00Z', description: 'Reconciled student payment',
        transactionCurrencyCode: 'USD', transactionDebit: 0, transactionCredit: 40, baseCurrencyCode: 'USD', baseDebit: 0, baseCredit: 40, runningBaseBalance: 60 },
      { lineType: 'CREDIT_NOTE', reference: 'EMH-SCN-0000000041', occurredAt: '2026-08-05T09:00:00Z', description: 'Posted fee correction',
        transactionCurrencyCode: 'USD', transactionDebit: 0, transactionCredit: 10, baseCurrencyCode: 'USD', baseDebit: 0, baseCredit: 10, runningBaseBalance: 50 }
    ]
  }
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => message.type() === 'error' && errors.push(message.text()))
  await page.route('**/api/finance/fee-catalogues', route => route.fulfill({ json: { catalogues: [] } }))
  await page.route('**/api/finance/fee-structures', route => route.fulfill({ json: { structures: [] } }))
  await page.route('**/api/academic/overview', route => route.fulfill({ json: academicOverviewFixture().overview }))

  await page.goto('/operations/finance-fees')
  await login(page)
  await page.route('**/api/finance/billing', route => route.fulfill({ json: billingRegister }))
  await page.route('**/api/finance/collections', route => route.fulfill({ json: collectionsRegister }))
  await page.route('**/api/finance/collections/accounts', route => route.fulfill({ json: [account] }))
  await page.route(`**/api/finance/collections/accounts/${accountId}/statement`, route => route.fulfill({ json: statement }))

  await page.goto('/operations/finance-billing')
  await page.waitForLoadState('networkidle')
  await expect(page.getByText('Approved charges become immutable invoices')).toBeVisible()
  await expect(page.getByText('EMH-BEV-0000000042')).toBeVisible()
  await page.getByRole('tab', { name: /Invoices/ }).click()
  await expect(page.getByText('EMH-INV-0000000042')).toBeVisible()

  await page.goto('/operations/finance-collections')
  await page.waitForLoadState('networkidle')
  await expect(page.getByText('Provider evidence first, reconciliation second')).toBeVisible()
  await expect(page.getByText('EMH-SPY-0000000042', { exact: true })).toBeVisible()
  await page.getByRole('tab', { name: /Allocations/ }).click()
  await expect(page.getByText('EMH-SAL-0000000042')).toBeVisible()

  await page.goto('/operations/finance-corrections')
  await page.waitForLoadState('networkidle')
  await expect(page.getByText('Correct by addition, never by rewriting')).toBeVisible()
  await expect(page.getByText('EMH-SCN-0000000042')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Verify and post' })).toBeVisible()

  await page.goto('/operations/finance-accounts')
  await page.waitForLoadState('networkidle')
  await expect(page.getByText('One chronological USD subledger')).toBeVisible()
  await page.getByRole('button', { name: /R260042A/ }).click()
  await expect(page.getByText('Official account inquiry')).toBeVisible()
  await expect(page.getByText('EMH-SCN-0000000041')).toBeVisible()
  await expect(page.getByText('US$50.00', { exact: true }).first()).toBeVisible()
  expect(errors).toEqual([])
})
