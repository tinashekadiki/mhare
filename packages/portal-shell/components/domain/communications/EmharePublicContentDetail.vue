<script setup lang="ts">
import type { CommunicationContentKind } from "@emhare/portal-shell/types/communications";

const props = defineProps<{ slug: string; allowedKinds: CommunicationContentKind[] }>();
const communications = usePublicCommunications();
const {
  data: item,
  pending,
  error,
} = await useAsyncData(`public-communication-${props.slug}`, () => communications.item(props.slug));

if (item.value && !props.allowedKinds.includes(item.value.kind)) {
  throw createError({ statusCode: 404, statusMessage: "Public item not found" });
}

onMounted(() => {
  if (item.value?.publicationId) {
    communications.recordAuthenticatedRead(item.value.publicationId).catch(() => undefined);
  }
});

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en-ZW", {
    dateStyle: "long",
    timeStyle: "short",
    timeZone: item.value?.event?.timezone,
  }).format(new Date(value));
}
</script>

<template>
  <div class="min-h-screen bg-[#f6f4ee] text-slate-950">
    <EmharePublicGatewayHeader />
    <main>
      <div v-if="pending" class="mx-auto max-w-4xl px-5 py-20 sm:px-8">
        <USkeleton class="h-12 w-3/4" />
        <USkeleton class="mt-6 h-6 w-full" />
        <USkeleton class="mt-10 h-80 w-full" />
      </div>
      <div v-else-if="error || !item" class="mx-auto max-w-4xl px-5 py-20 sm:px-8">
        <UAlert
          color="error"
          variant="soft"
          icon="i-lucide-circle-alert"
          title="This public item is unavailable"
          description="It may have expired, been withdrawn, or the link may be incorrect."
        />
        <NuxtLink to="/" class="mt-6 inline-flex items-center gap-2 font-bold text-uzazure-800"
          ><UIcon name="i-lucide-arrow-left" class="size-4" />Return to eMhare</NuxtLink
        >
      </div>
      <article v-else>
        <header class="border-b border-slate-200 bg-white">
          <div class="mx-auto max-w-4xl px-5 py-14 sm:px-8 sm:py-20">
            <NuxtLink
              to="/"
              class="inline-flex items-center gap-2 text-sm font-bold text-uzazure-800"
              ><UIcon name="i-lucide-arrow-left" class="size-4" />Public gateway</NuxtLink
            >
            <p class="mt-10 text-xs font-bold uppercase tracking-[0.22em] text-uzazure-700">
              {{ item.kind.toLowerCase() }}
            </p>
            <h1
              class="mt-4 max-w-3xl font-serif text-4xl font-semibold leading-[1.08] tracking-tight sm:text-6xl"
            >
              {{ item.title }}
            </h1>
            <p class="mt-6 max-w-2xl text-lg leading-8 text-slate-600">{{ item.summary }}</p>
            <div
              v-if="item.event"
              class="mt-8 grid gap-3 rounded-2xl bg-uzazure-50 p-5 text-sm text-uzazure-950 sm:grid-cols-2"
            >
              <span class="inline-flex items-center gap-2"
                ><UIcon name="i-lucide-calendar-days" class="size-4" />{{
                  formatDate(item.event.startsAt)
                }}</span
              >
              <span v-if="item.event.venueName" class="inline-flex items-center gap-2"
                ><UIcon name="i-lucide-map-pin" class="size-4" />{{ item.event.venueName }}</span
              >
              <a
                :href="communications.calendarUrl(item.slug)"
                download
                class="inline-flex items-center gap-2 font-bold text-uzazure-800"
                ><UIcon name="i-lucide-download" class="size-4" />Download calendar event</a
              >
            </div>
          </div>
        </header>
        <div class="mx-auto max-w-4xl px-5 py-14 sm:px-8 sm:py-20">
          <img
            v-if="item.heroMediaAssetId"
            :src="communications.mediaUrl(item.heroMediaAssetId)"
            :alt="item.title"
            class="mb-12 max-h-[34rem] w-full rounded-3xl object-cover"
          />
          <EmhareStructuredContent :blocks="item.structuredContent" />
        </div>
      </article>
    </main>
  </div>
</template>
