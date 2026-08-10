<script setup lang="ts">
definePageMeta({ public: true })

const auth = useEmhareAuth()
const targetPath = await auth.handleCallback()
await navigateTo(targetPath, { replace: true })
</script>

<template>
  <main class="grid min-h-screen place-items-center bg-default px-6">
    <div class="text-center">
      <div class="mx-auto grid size-11 place-items-center rounded-xl bg-primary text-lg font-bold text-inverted">
        e
      </div>
      <p class="mt-5 text-sm font-medium text-highlighted">
        Completing sign in
      </p>
      <p class="mt-1 text-xs text-muted">
        Your secure session is being established.
      </p>
    </div>
  </main>
</template>
