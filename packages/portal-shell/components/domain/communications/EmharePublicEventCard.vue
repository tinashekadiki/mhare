<script setup lang="ts">
import type { PublicCommunicationItem } from "@emhare/portal-shell/types/communications";

const props = defineProps<{ item: PublicCommunicationItem }>();
const communications = usePublicCommunications();

const dateParts = computed(() => {
  if (!props.item.event) return { day: "", month: "" };
  const date = new Date(props.item.event.startsAt);
  return {
    day: new Intl.DateTimeFormat("en-ZW", {
      day: "2-digit",
      timeZone: props.item.event.timezone,
    }).format(date),
    month: new Intl.DateTimeFormat("en-ZW", { month: "short", timeZone: props.item.event.timezone })
      .format(date)
      .toUpperCase(),
  };
});

function eventTime() {
  if (!props.item.event) return "";
  return new Intl.DateTimeFormat("en-ZW", {
    hour: "2-digit",
    minute: "2-digit",
    timeZone: props.item.event.timezone,
  }).format(new Date(props.item.event.startsAt));
}
</script>

<template>
  <article
    class="grid grid-cols-[4.5rem_minmax(0,1fr)] gap-4 border-b border-slate-200 bg-white py-5 first:pt-0 last:border-b-0 last:pb-0"
  >
    <div class="grid h-[4.5rem] place-items-center bg-uzorange-500 text-center text-slate-950">
      <div>
        <span class="block text-[0.65rem] font-extrabold tracking-[0.18em]">{{
          dateParts.month
        }}</span>
        <span class="block text-2xl font-black leading-none">{{ dateParts.day }}</span>
      </div>
    </div>
    <div class="min-w-0">
      <h3 class="text-lg font-bold text-slate-950">
        <NuxtLink
          :to="`/events/${item.slug}`"
          class="hover:text-uzazure-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-uzazure-600"
        >
          {{ item.title }}
        </NuxtLink>
      </h3>
      <div class="mt-3 flex flex-wrap gap-x-4 gap-y-2 text-sm text-slate-600">
        <span class="inline-flex items-center gap-1.5"
          ><UIcon name="i-lucide-clock-3" class="size-4 text-uzazure-700" />{{ eventTime() }}</span
        >
        <span v-if="item.event?.venueName" class="inline-flex items-center gap-1.5"
          ><UIcon name="i-lucide-map-pin" class="size-4 text-uzazure-700" />{{
            item.event.venueName
          }}</span
        >
      </div>
      <div class="mt-4 flex flex-wrap gap-3">
        <NuxtLink :to="`/events/${item.slug}`" class="text-sm font-bold text-uzazure-700"
          >Event details</NuxtLink
        >
        <a
          :href="communications.calendarUrl(item.slug)"
          download
          class="inline-flex items-center gap-1.5 text-sm font-bold text-slate-600 hover:text-uzazure-700"
        >
          <UIcon name="i-lucide-calendar-days" class="size-4" /> Add to calendar
        </a>
      </div>
    </div>
  </article>
</template>
