<script setup lang="ts">
definePageMeta({ public: true })

const auth = useEmhareAuth()
const targetPath = await auth.handleCallback()
await navigateTo(targetPath, { replace: true })
</script>

<template>
  <UContainer class="py-10">
    <UAlert
      color="primary"
      variant="soft"
      icon="i-lucide-shield-check"
      title="Completing sign in"
      description="Your secure session is being established."
    />
  </UContainer>
</template>
