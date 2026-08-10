import type { User, UserManager, UserManagerSettings } from 'oidc-client-ts'

type CoreUserSummary = {
  id: string
  keycloakUserId: string
  username: string
  email: string
  displayName: string
  status: string
}

type CoreCurrentUserProfile = {
  user: CoreUserSummary
  roleAssignments: Array<{
    id: string
    roleId: string
    roleCode: string
    roleName: string
    academicUnitId?: string
  }>
  realmRoles?: string[]
  effectivePermissionCodes?: string[]
  operationalAccess?: boolean
}

const OPERATIONAL_REALM_ROLES = new Set([
  'system-admin',
  'academic-admin',
  'admissions-officer',
  'finance-officer',
  'registry-officer',
  'exams-officer',
  'exam-invigilator',
  'accommodation-officer',
  'dining-officer',
  'notifications-officer'
])
const SELF_SERVICE_ROLE_CODES = new Set(['APPLICANT', 'STUDENT'])

let authUserManager: UserManager | undefined
let signInRedirectPromise: Promise<void> | undefined
let rejectedSessionRecoveryPromise: Promise<void> | undefined

type HttpErrorResponse = {
  status?: number
  statusCode?: number
}

type HttpError = {
  status?: number
  statusCode?: number
  response?: HttpErrorResponse
}

function httpStatus(error: unknown) {
  const httpError = error as HttpError
  return httpError?.response?.status
    ?? httpError?.response?.statusCode
    ?? httpError?.status
    ?? httpError?.statusCode
}

function publicEnv(name: string, fallback: string) {
  const env = (import.meta as unknown as { env?: Record<string, string | undefined> }).env
  return env?.[name] ?? fallback
}

function browserOrigin() {
  if (import.meta.server) {
    return 'http://localhost:3000'
  }
  return window.location.origin
}

async function userManager() {
  if (authUserManager) {
    return authUserManager
  }
  const { UserManager, WebStorageStateStore } = await import('oidc-client-ts')
  const origin = browserOrigin()
  const settings: UserManagerSettings = {
    authority: publicEnv('NUXT_PUBLIC_OIDC_ISSUER', 'http://localhost:8099/realms/emhare'),
    client_id: publicEnv('NUXT_PUBLIC_OIDC_CLIENT_ID', 'emhare-web'),
    redirect_uri: `${origin}/auth/callback`,
    post_logout_redirect_uri: origin,
    response_type: 'code',
    scope: 'openid profile email',
    automaticSilentRenew: true,
    userStore: new WebStorageStateStore({ store: window.localStorage })
  }
  authUserManager = new UserManager(settings)
  return authUserManager
}

export function useEmhareAuth() {
  const oidcUser = useState<User | null>('emhare-oidc-user', () => null)
  const currentUserProfile = useState<CoreCurrentUserProfile | null>('emhare-core-user-profile', () => null)
  const loading = useState('emhare-auth-loading', () => false)

  const authenticated = computed(() => Boolean(oidcUser.value && !oidcUser.value.expired))
  const accessToken = computed(() => oidcUser.value?.access_token ?? null)
  const displayName = computed(() => currentUserProfile.value?.user.displayName ?? oidcUser.value?.profile.name ?? oidcUser.value?.profile.email ?? 'Operator')
  const realmRoles = computed(() => new Set(currentUserProfile.value?.realmRoles ?? []))
  const localRoleCodes = computed(() => new Set(
    currentUserProfile.value?.roleAssignments.map(assignment => assignment.roleCode) ?? []
  ))
  const effectivePermissionCodes = computed(() => new Set(currentUserProfile.value?.effectivePermissionCodes ?? []))
  const isSystemAdministrator = computed(() => realmRoles.value.has('system-admin'))
  const operationalAccess = computed(() => {
    if (currentUserProfile.value?.operationalAccess !== undefined) {
      return currentUserProfile.value.operationalAccess
    }
    return [...realmRoles.value].some(role => OPERATIONAL_REALM_ROLES.has(role))
      || Boolean(currentUserProfile.value?.roleAssignments.some(
        assignment => !SELF_SERVICE_ROLE_CODES.has(assignment.roleCode)
      ))
  })

  function hasPermission(permissionCode: string) {
    return isSystemAdministrator.value || effectivePermissionCodes.value.has(permissionCode)
  }

  function hasRole(roleCode: string) {
    if (isSystemAdministrator.value) {
      return true
    }
    const normalizedRoleCode = roleCode.trim().toUpperCase().replaceAll('-', '_')
    const realmRoleCode = normalizedRoleCode.toLowerCase().replaceAll('_', '-')
    return localRoleCodes.value.has(normalizedRoleCode) || realmRoles.value.has(realmRoleCode)
  }

  function hasPermissionPrefix(permissionPrefix: string) {
    if (isSystemAdministrator.value) {
      return true
    }
    return [...effectivePermissionCodes.value].some(permissionCode => permissionCode.startsWith(permissionPrefix))
  }

  function assignedSelfServicePortalUrl() {
    const localRoleCodes = new Set(currentUserProfile.value?.roleAssignments.map(assignment => assignment.roleCode) ?? [])
    if (realmRoles.value.has('student') || localRoleCodes.has('STUDENT')) {
      return publicEnv('NUXT_PUBLIC_STUDENT_PORTAL_URL', 'http://localhost:3002')
    }
    return publicEnv('NUXT_PUBLIC_APPLICANT_PORTAL_URL', 'http://localhost:3001')
  }

  function redirectToAssignedPortal() {
    if (import.meta.server) {
      return
    }
    window.location.replace(assignedSelfServicePortalUrl())
  }

  async function loadUser() {
    if (import.meta.server) {
      return null
    }
    const manager = await userManager()
    oidcUser.value = await manager.getUser()
    if (oidcUser.value?.expired) {
      await manager.removeUser()
      oidcUser.value = null
      currentUserProfile.value = null
    }
    return oidcUser.value
  }

  async function login(returnTo?: string) {
    if (import.meta.server) {
      return
    }
    if (!signInRedirectPromise) {
      signInRedirectPromise = (async () => {
        const requestedTargetPath = returnTo || window.location.pathname + window.location.search
        const existingTargetPath = localStorage.getItem('emhare:returnTo')
        const targetPath = requestedTargetPath === '/' && existingTargetPath ? existingTargetPath : requestedTargetPath
        sessionStorage.setItem('emhare:returnTo', targetPath)
        localStorage.setItem('emhare:returnTo', targetPath)
        await (await userManager()).signinRedirect({ state: targetPath })
      })()
    }
    try {
      await signInRedirectPromise
    } finally {
      signInRedirectPromise = undefined
    }
  }

  async function signup(returnTo?: string) {
    if (import.meta.server) {
      return
    }
    const requestedTargetPath = returnTo || window.location.pathname + window.location.search
    const existingTargetPath = localStorage.getItem('emhare:returnTo')
    const targetPath = requestedTargetPath === '/' && existingTargetPath ? existingTargetPath : requestedTargetPath
    sessionStorage.setItem('emhare:returnTo', targetPath)
    localStorage.setItem('emhare:returnTo', targetPath)
    await (await userManager()).signinRedirect({
      state: targetPath,
      prompt: 'create'
    })
  }

  async function logout() {
    if (import.meta.server) {
      return
    }
    currentUserProfile.value = null
    oidcUser.value = null
    await (await userManager()).signoutRedirect()
  }

  async function discardRejectedSession() {
    currentUserProfile.value = null
    oidcUser.value = null
    if (import.meta.server) {
      return
    }
    await (await userManager()).removeUser()
  }

  async function restartLogin(returnTo?: string) {
    if (import.meta.server) {
      return
    }
    if (!rejectedSessionRecoveryPromise) {
      rejectedSessionRecoveryPromise = (async () => {
        await discardRejectedSession()
        await login(returnTo)
      })()
    }
    try {
      await rejectedSessionRecoveryPromise
    } finally {
      rejectedSessionRecoveryPromise = undefined
    }
  }

  async function handleCallback() {
    if (import.meta.server) {
      return '/'
    }
    loading.value = true
    try {
      oidcUser.value = await (await userManager()).signinCallback() ?? null
      const callbackState = typeof oidcUser.value?.state === 'string' ? oidcUser.value.state : null
      const returnTo = callbackState || sessionStorage.getItem('emhare:returnTo') || localStorage.getItem('emhare:returnTo') || '/'
      const currentProfile = await syncCoreUser()
      if (!currentProfile) {
        await login(returnTo)
        return returnTo
      }
      sessionStorage.removeItem('emhare:returnTo')
      localStorage.removeItem('emhare:returnTo')
      return returnTo
    } finally {
      loading.value = false
    }
  }

  async function syncCoreUser() {
    if (!accessToken.value) {
      currentUserProfile.value = null
      return null
    }
    try {
      currentUserProfile.value = await $fetch<CoreCurrentUserProfile>(`${publicEnv('NUXT_PUBLIC_API_BASE', 'http://localhost:8080')}/api/core/me`, {
        headers: {
          Authorization: `Bearer ${accessToken.value}`
        }
      })
      return currentUserProfile.value
    } catch (error) {
      if (httpStatus(error) === 401) {
        await discardRejectedSession()
        return null
      }
      throw error
    }
  }

  async function requireUser(returnTo?: string) {
    const user = await loadUser()
    if (!user) {
      await login(returnTo)
      return false
    }
    if (!currentUserProfile.value) {
      const currentProfile = await syncCoreUser()
      if (!currentProfile) {
        await login(returnTo)
        return false
      }
    }
    return true
  }

  return {
    oidcUser,
    currentUserProfile,
    authenticated,
    accessToken,
    displayName,
    realmRoles,
    localRoleCodes,
    effectivePermissionCodes,
    isSystemAdministrator,
    operationalAccess,
    loading,
    loadUser,
    login,
    signup,
    logout,
    handleCallback,
    syncCoreUser,
    restartLogin,
    requireUser,
    hasPermission,
    hasRole,
    hasPermissionPrefix,
    redirectToAssignedPortal
  }
}
