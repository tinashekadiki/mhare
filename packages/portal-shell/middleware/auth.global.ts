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
})
