import { expect, test, type Page } from '@playwright/test'

const workbenchUrl = process.env.UI_WORKBENCH_URL ?? 'http://localhost:3003'

async function openWorkbenchPage(page: Page, path: string) {
  await page.goto(`${workbenchUrl}${path}`)
  await page.waitForLoadState('networkidle')
}

test.describe('eMhare UI workbench', () => {
  test('renders shared shell navigation and global search', async ({ page }) => {
    await openWorkbenchPage(page, '/shell')
    await expect(page.getByRole('heading', { name: 'Application Shell' })).toBeVisible({ timeout: 20_000 })
    await expect(page.getByText('Shared top bar, sidebar, breadcrumbs')).toBeVisible()
    await page.getByRole('button', { name: /search/i }).click()
    await page.getByPlaceholder('Search pages and actions').fill('Data')
    await expect(page.getByRole('link', { name: 'Data Table /data-table' })).toBeVisible()
  })

  test('renders form patterns and wizard states', async ({ page }) => {
    await openWorkbenchPage(page, '/forms')
    await expect(page.getByRole('heading', { name: 'Forms' })).toBeVisible({ timeout: 20_000 })
    await expect(page.getByLabel('Full name')).toHaveValue('Tinashe Kadiki')
    const emptySelects = [page.getByLabel('Role'), page.getByLabel('Country')]
    await expect(emptySelects[0]).toContainText('Select role')
    await expect(emptySelects[1]).toContainText('Select country')
    for (const select of emptySelects) {
      expect((await select.boundingBox())?.height).toBeGreaterThanOrEqual(32)
    }
    await expect(page.getByText('Unsaved changes')).toBeVisible()
    await page.getByRole('button', { name: /Documents/ }).click()
    await expect(page.getByText('Upload checklist').last()).toBeVisible()
  })

  test('supports table selection, search, column menu and expansion', async ({ page }) => {
    await openWorkbenchPage(page, '/data-table')
    await expect(page.getByRole('heading', { name: 'Data Table' })).toBeVisible({ timeout: 20_000 })
    await page.getByPlaceholder('Search').fill('Rudo')
    await expect(page.getByText('Rudo Moyo')).toBeVisible()
    await expect(page.getByText('Tinashe Kadiki')).not.toBeVisible()
    await page.getByLabel('Select row').first().check()
    await expect(page.getByText(/1 selected/)).toBeVisible()
    await page.getByRole('button', { name: /Columns/ }).click()
    await expect(page.getByRole('menuitem', { name: 'Applicant' })).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(page.getByRole('button', { name: 'Expand row' }).first()).toBeVisible()
    await page.getByRole('button', { name: 'Expand row' }).first().click()
    await expect(page.getByText('Admissions review')).toBeVisible()
  })

  test('pages record sets and keeps the total record count visible', async ({ page }) => {
    await openWorkbenchPage(page, '/data-table')
    await expect(page.getByText('0 selected · 12 total · Page 1 of 2')).toBeVisible({ timeout: 20_000 })
    await expect(page.getByText('APP-001', { exact: true })).toBeVisible()
    await expect(page.getByText('APP-011', { exact: true })).not.toBeVisible()

    await page.getByRole('button', { name: 'Page 2' }).click()

    await expect(page.getByText('0 selected · 12 total · Page 2 of 2')).toBeVisible()
    await expect(page.getByText('APP-001', { exact: true })).not.toBeVisible()
    await expect(page.getByText('APP-011', { exact: true })).toBeVisible()
    await expect(page.getByText('APP-012', { exact: true })).toBeVisible()
  })

  test('renders feedback and domain component examples', async ({ page }) => {
    await openWorkbenchPage(page, '/student')
    await expect(page.getByRole('heading', { name: 'Student Components' })).toBeVisible({ timeout: 20_000 })
    await expect(page.getByText('R231234A · BSc Computer Science')).toBeVisible()

    await openWorkbenchPage(page, '/finance')
    await expect(page.getByText('Balanced')).toBeVisible()

    await openWorkbenchPage(page, '/audit-admin')
    await expect(page.getByText('CORE_USER_MANAGE')).toBeVisible()
  })
})
