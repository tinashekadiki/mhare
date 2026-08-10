import { expect, test } from '@playwright/test'

const oidcStorageKey = 'oidc.user:http://localhost:8099/realms/emhare:emhare-web'

test.describe('Authentication session recovery', () => {
  test('restarts sign-in when Core rejects a locally cached OIDC session', async ({ page }) => {
    const pageErrors: string[] = []
    page.on('pageerror', error => pageErrors.push(error.message))

    await page.addInitScript(({ storageKey, expiresAt }) => {
      localStorage.setItem(storageKey, JSON.stringify({
        id_token: 'rejected-id-token',
        session_state: 'rejected-session',
        access_token: 'rejected-access-token',
        token_type: 'Bearer',
        scope: 'openid profile email',
        profile: {
          sub: 'rejected-user',
          email: 'rejected.user@example.test',
          name: 'Rejected User'
        },
        expires_at: expiresAt
      }))
    }, {
      storageKey: oidcStorageKey,
      expiresAt: Math.floor(Date.now() / 1000) + 3600
    })

    await page.goto('/')

    await expect(page).toHaveURL(/\/realms\/emhare\/protocol\/openid-connect\/auth/, { timeout: 15_000 })
    await expect(page.getByRole('heading', { name: /sign in to your account/i })).toBeVisible()
    expect(pageErrors).toEqual([])
  })

  test('restarts sign-in when an authenticated API request reports a revoked session', async ({ page }) => {
    const pageErrors: string[] = []
    page.on('pageerror', error => pageErrors.push(error.message))

    await page.addInitScript(({ storageKey, expiresAt }) => {
      localStorage.setItem(storageKey, JSON.stringify({
        id_token: 'initially-accepted-id-token',
        session_state: 'initially-accepted-session',
        access_token: 'initially-accepted-access-token',
        token_type: 'Bearer',
        scope: 'openid profile email',
        profile: {
          sub: 'revoked-user',
          email: 'revoked.user@example.test',
          name: 'Revoked User'
        },
        expires_at: expiresAt
      }))
    }, {
      storageKey: oidcStorageKey,
      expiresAt: Math.floor(Date.now() / 1000) + 3600
    })

    await page.route('**/api/core/**', route => route.fulfill({ status: 401 }))
    await page.route('**/api/core/me', route => route.fulfill({
      json: {
        user: {
          id: '00000000-0000-4000-8000-000000000001',
          keycloakUserId: 'revoked-user',
          username: 'revoked.user@example.test',
          email: 'revoked.user@example.test',
          displayName: 'Revoked User',
          status: 'ACTIVE'
        },
        roleAssignments: []
      }
    }))

    await page.goto('/operations/core')

    await expect(page).toHaveURL(/\/realms\/emhare\/protocol\/openid-connect\/auth/, { timeout: 15_000 })
    await expect(page.getByRole('heading', { name: /sign in to your account/i })).toBeVisible()
    expect(pageErrors).toEqual([])
  })
})
