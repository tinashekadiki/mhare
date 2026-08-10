export default defineNuxtRouteMiddleware(async (to) => {
  if (to.path === '/auth/callback' || to.path === '/auth/redirect' || to.meta.public === true) {
    return
  }
  if (import.meta.server) {
    return
  }

  const auth = useEmhareAuth()
  const isAuthenticated = await auth.requireUser(to.fullPath)
  if (!isAuthenticated) {
    return abortNavigation()
  }

  if (!auth.operationalAccess.value) {
    auth.redirectToAssignedPortal()
    return abortNavigation()
  }

  const requiredAnyPermissions = Array.isArray(to.meta.requiredAnyPermissions)
    ? to.meta.requiredAnyPermissions.filter((permission): permission is string => typeof permission === 'string')
    : []
  if (requiredAnyPermissions.length && !requiredAnyPermissions.some(auth.hasPermission)) {
    return navigateTo({ path: '/operations', query: { access: 'restricted' } })
  }
})
