export const useSessionStore = defineStore('session', () => {
  const displayName = ref('Operator')
  const authenticated = ref(false)

  return {
    authenticated,
    displayName
  }
})
