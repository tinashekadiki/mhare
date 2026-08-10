<script setup lang="ts">
definePageMeta({ public: true })

const route = useRoute()
const auth = useEmhareAuth()

onMounted(async () => {
  const storedReturnTo = localStorage.getItem('emhare:returnTo')
  const requestedReturnTo = typeof route.query.returnTo === 'string' ? route.query.returnTo : '/'
  const returnTo = requestedReturnTo === '/' && storedReturnTo ? storedReturnTo : requestedReturnTo
  const user = await auth.loadUser()
  if (user) {
    await auth.syncCoreUser()
    await navigateTo(returnTo, { replace: true })
    return
  }
  await auth.login(returnTo)
})
</script>

<template>
  <main class="grid min-h-screen place-items-center bg-default px-6">
    <div class="text-center">
      <div class="mx-auto grid size-11 place-items-center rounded-xl bg-primary text-lg font-bold text-inverted">
        e
      </div>
      <p class="mt-5 text-sm font-medium text-highlighted">
        Taking you to sign in
      </p>
      <p class="mt-1 text-xs text-muted">
        eMhare Admissions
      </p>
    </div>
  </main>
</template>
