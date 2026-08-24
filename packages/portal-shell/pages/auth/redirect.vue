<script setup lang="ts">
definePageMeta({ public: true });

const route = useRoute();
const auth = useEmhareAuth();

onMounted(async () => {
  const storedReturnTo = localStorage.getItem("emhare:returnTo");
  const requestedReturnTo = typeof route.query.returnTo === "string" ? route.query.returnTo : "/";
  const candidate =
    requestedReturnTo === "/" && storedReturnTo ? storedReturnTo : requestedReturnTo;
  const returnTo = sanitizePortalReturnPath(
    candidate,
    window.location.origin,
    inferPortalKind(window.location),
    window.location,
  );
  const user = await auth.loadUser();
  if (user) {
    await auth.syncCoreUser();
    await navigateTo(returnTo, { replace: true });
    return;
  }
  await auth.login(returnTo);
});
</script>

<template>
  <main class="grid min-h-screen place-items-center bg-default px-6">
    <div class="text-center">
      <UIcon name="i-lucide-shield-check" class="mx-auto size-10 text-primary" />
      <p class="mt-4 text-sm font-medium text-highlighted">Redirecting to sign in</p>
    </div>
  </main>
</template>
