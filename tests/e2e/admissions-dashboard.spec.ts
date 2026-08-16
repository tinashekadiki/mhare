// Author: Tinashe K

import { expect, request as playwrightRequest, test, type APIRequestContext, type Page } from '@playwright/test'

const keycloakBaseUrl = process.env.KEYCLOAK_URL ?? 'http://localhost:8099'
const keycloakRealm = process.env.KEYCLOAK_REALM ?? 'emhare'
const keycloakAdminUsername = process.env.KEYCLOAK_ADMIN_USERNAME ?? 'admin'
const keycloakAdminPassword = process.env.KEYCLOAK_ADMIN_PASSWORD ?? 'admin'
const testPassword = 'Passw0rd!234'

type KeycloakRole = { id: string; name: string }
type PipelineReportPayload = {
  totalApplications: number
  totalApplicants: number
  filterOptions: {
    intakes: Array<{ value: string; code: string; label: string }>
  }
}

async function createKeycloakAdminRequestContext() {
  const tokenContext = await playwrightRequest.newContext()
  const tokenResponse = await tokenContext.post(
    `${keycloakBaseUrl}/realms/master/protocol/openid-connect/token`,
    {
      form: {
        client_id: 'admin-cli',
        username: keycloakAdminUsername,
        password: keycloakAdminPassword,
        grant_type: 'password'
      }
    }
  )
  expect(tokenResponse.ok()).toBeTruthy()
  const tokenPayload = await tokenResponse.json()
  await tokenContext.dispose()
  return playwrightRequest.newContext({
    extraHTTPHeaders: {
      Authorization: `Bearer ${tokenPayload.access_token}`,
      'Content-Type': 'application/json'
    }
  })
}

async function ensureSystemAdministrator(username: string) {
  const administrator = await createKeycloakAdminRequestContext()
  const createResponse = await administrator.post(
    `${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users`,
    {
      data: {
        username,
        email: username,
        firstName: 'Codex',
        lastName: 'Dashboard',
        enabled: true,
        emailVerified: true,
        credentials: [{ type: 'password', value: testPassword, temporary: false }]
      }
    }
  )
  expect([201, 409]).toContain(createResponse.status())

  const usersResponse = await administrator.get(
    `${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users?username=${encodeURIComponent(username)}&exact=true`
  )
  expect(usersResponse.ok()).toBeTruthy()
  const users = await usersResponse.json()
  expect(users.length).toBeGreaterThan(0)
  await assignRealmRole(administrator, users[0].id, 'system-admin')
  await administrator.dispose()
}

async function assignRealmRole(administrator: APIRequestContext, userId: string, roleName: string) {
  const roleResponse = await administrator.get(
    `${keycloakBaseUrl}/admin/realms/${keycloakRealm}/roles/${roleName}`
  )
  expect(roleResponse.ok()).toBeTruthy()
  const role = await roleResponse.json() as KeycloakRole
  const mappingResponse = await administrator.post(
    `${keycloakBaseUrl}/admin/realms/${keycloakRealm}/users/${userId}/role-mappings/realm`,
    { data: [role] }
  )
  expect([204, 409]).toContain(mappingResponse.status())
}

async function loginWithKeycloak(page: Page, username: string) {
  await page.getByLabel(/email|username/i).fill(username)
  await page.getByRole('textbox', { name: 'Password' }).fill(testPassword)
  await page.getByRole('button', { name: /sign in|log in/i }).click()
}

test.describe('Admissions operational dashboard', () => {
  test('renders the real pipeline payload and drills into governed work queues', async ({ page }, testInfo) => {
    test.setTimeout(60_000)
    const projectSuffix = testInfo.project.name.replace(/[^a-z0-9]+/gi, '-').toLowerCase()
    const username = `codex.admissions-dashboard.${projectSuffix}@example.test`
    await ensureSystemAdministrator(username)

    const browserErrors: string[] = []
    page.on('console', message => {
      if (message.type() === 'error') browserErrors.push(message.text())
    })
    page.on('pageerror', error => browserErrors.push(error.message))

    const initialPipelineResponse = page.waitForResponse(response =>
      response.url().endsWith('/api/admissions/reports/pipeline-summary') && response.request().method() === 'GET'
    )
    await page.goto('/operations/admissions-dashboard')
    await expect(page).toHaveURL(/\/realms\/emhare\/protocol\/openid-connect\/auth/, { timeout: 15_000 })
    await loginWithKeycloak(page, username)
    await expect(page).toHaveURL(/\/operations\/admissions-dashboard$/, { timeout: 20_000 })

    const response = await initialPipelineResponse
    expect(response.ok()).toBeTruthy()
    const payload = await response.json() as PipelineReportPayload
    const admissionsOverviewHeading = page.getByRole('heading', { name: 'Admissions overview', exact: true })
    await expect(admissionsOverviewHeading).toHaveCount(1)
    if (testInfo.project.name === 'chromium-desktop') {
      await expect(admissionsOverviewHeading).toBeVisible()
    }
    await expect(page.getByTestId('admissions-total-applications').locator('p').nth(1))
      .toHaveText(String(payload.totalApplications))
    await expect(page.getByTestId('admissions-total-applicants').locator('p').nth(1))
      .toHaveText(String(payload.totalApplicants))
    await expect(page.getByText('Programme demand', { exact: true })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Open verification queue' }).first())
      .toHaveAttribute('href', '/operations/admissions?stage=VERIFICATION')
    await expect(page.getByRole('link', { name: 'Open academic review queue' }))
      .toHaveAttribute('href', '/operations/admissions?stage=ACADEMIC_REVIEW')

    if (payload.filterOptions.intakes.length) {
      const selectedIntake = payload.filterOptions.intakes[0]
      await page.getByTestId('admissions-dashboard-intake-filter').click()
      await page.getByRole('option', { name: new RegExp(selectedIntake.code) }).click()
      const filteredResponse = page.waitForResponse(filtered => {
        const url = new URL(filtered.url())
        return url.pathname.endsWith('/api/admissions/reports/pipeline-summary')
          && url.searchParams.get('intakeId') === selectedIntake.value
      })
      await page.getByRole('button', { name: 'Apply filters' }).click()
      expect((await filteredResponse).ok()).toBeTruthy()
    }

    const contentWidth = await page.getByTestId('admissions-dashboard-content').evaluate((element) => {
      const parent = element.parentElement
      if (!parent) return { difference: Number.POSITIVE_INFINITY, maximumWidth: '' }
      const parentStyle = window.getComputedStyle(parent)
      const availableWidth = parent.getBoundingClientRect().width
        - Number.parseFloat(parentStyle.paddingLeft)
        - Number.parseFloat(parentStyle.paddingRight)
      return {
        difference: Math.abs(availableWidth - element.getBoundingClientRect().width),
        maximumWidth: window.getComputedStyle(element).maxWidth
      }
    })
    expect(contentWidth.maximumWidth).toBe('none')
    expect(contentWidth.difference).toBeLessThanOrEqual(1)

    const horizontalOverflow = await page.evaluate(() =>
      document.documentElement.scrollWidth - document.documentElement.clientWidth
    )
    expect(horizontalOverflow).toBeLessThanOrEqual(1)
    expect(browserErrors).toEqual([])
    await page.screenshot({ path: testInfo.outputPath('admissions-dashboard.png'), fullPage: true })
  })
})

test.describe('University operational dashboards', () => {
  test('renders the main dashboard and every implemented module dashboard', async ({ page }, testInfo) => {
    test.setTimeout(120_000)
    const projectSuffix = testInfo.project.name.replace(/[^a-z0-9]+/gi, '-').toLowerCase()
    const username = `codex.operations-dashboard.${projectSuffix}@example.test`
    await ensureSystemAdministrator(username)

    const browserErrors: string[] = []
    page.on('console', message => {
      if (message.type() === 'error') browserErrors.push(message.text())
    })
    page.on('pageerror', error => browserErrors.push(error.message))

    await page.goto('/operations')
    await expect(page).toHaveURL(/\/realms\/emhare\/protocol\/openid-connect\/auth/, { timeout: 15_000 })
    await loginWithKeycloak(page, username)
    await expect(page).toHaveURL(/\/operations$/, { timeout: 20_000 })
    await expect(page.getByRole('heading', { name: 'Operational pulse' })).toBeVisible()
    await expect(page.getByTestId(/^operations-module-/)).toHaveCount(11)
    await expect(page.getByRole('link', { name: 'Open dashboard' })).toHaveCount(11)

    const mainContentWidth = await contentWidth(page, 'operations-dashboard-content')
    expect(mainContentWidth.maximumWidth).toBe('none')
    expect(mainContentWidth.difference).toBeLessThanOrEqual(1)

    const moduleDashboards = [
      ['core', 'Core and Identity overview'],
      ['academic-setup', 'Academic Setup overview'],
      ['finance', 'Finance overview'],
      ['student-records', 'Student Records and Registration overview'],
      ['assessment-results', 'Teaching, Assessment and Results overview'],
      ['exams-timetabling', 'Exams and Timetabling overview'],
      ['accommodation', 'Accommodation overview'],
      ['dining', 'Dining overview'],
      ['documents', 'Documents and Reporting overview'],
      ['notifications', 'Notifications overview']
    ] as const

    for (const [moduleKey, heading] of moduleDashboards) {
      await page.goto(`/operations/dashboard/${moduleKey}`)
      await expect(page).toHaveURL(new RegExp(`/operations/dashboard/${moduleKey}$`))
      await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible()
      await expect(page.getByRole('heading', { name: 'Module snapshot' })).toBeVisible()
      await expect(page.getByText('Operational workload', { exact: true })).toBeVisible()
      const moduleContentWidth = await contentWidth(page, `operational-dashboard-${moduleKey}`)
      expect(moduleContentWidth.maximumWidth).toBe('none')
      expect(moduleContentWidth.difference).toBeLessThanOrEqual(1)
    }

    const horizontalOverflow = await page.evaluate(() =>
      document.documentElement.scrollWidth - document.documentElement.clientWidth
    )
    expect(horizontalOverflow).toBeLessThanOrEqual(1)
    expect(browserErrors).toEqual([])
    await page.screenshot({ path: testInfo.outputPath('operations-dashboard.png'), fullPage: true })
  })
})

async function contentWidth(page: Page, testId: string) {
  return page.getByTestId(testId).evaluate((element) => {
    const parent = element.parentElement
    if (!parent) return { difference: Number.POSITIVE_INFINITY, maximumWidth: '' }
    const parentStyle = window.getComputedStyle(parent)
    const availableWidth = parent.getBoundingClientRect().width
      - Number.parseFloat(parentStyle.paddingLeft)
      - Number.parseFloat(parentStyle.paddingRight)
    return {
      difference: Math.abs(availableWidth - element.getBoundingClientRect().width),
      maximumWidth: window.getComputedStyle(element).maxWidth
    }
  })
}
