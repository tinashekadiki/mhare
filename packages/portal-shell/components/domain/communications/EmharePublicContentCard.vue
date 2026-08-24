<script setup lang="ts">
import type { PublicCommunicationItem } from "@emhare/portal-shell/types/communications";

const props = defineProps<{
  item: PublicCommunicationItem;
  compact?: boolean;
  fallbackImage?: string;
  fallbackImageAlternativeText?: string;
}>();

const route = computed(() => {
  if (props.item.kind === "LINK" && props.item.externalUrl) return props.item.externalUrl;
  if (props.item.kind === "NEWS") return `/news/${props.item.slug}`;
  if (props.item.kind === "EVENT") return `/events/${props.item.slug}`;
  if (props.item.kind === "CAMPAIGN") return `/campaigns/${props.item.slug}`;
  return `/notices/${props.item.slug}`;
});

const label = computed(() => props.item.kind.replace("_", " ").toLowerCase());
const imageSource = computed(() => props.item.mediaUrl ?? props.fallbackImage);
const imageAlternativeText = computed(() =>
  props.item.mediaUrl ? props.item.title : (props.fallbackImageAlternativeText ?? ""),
);

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en-ZW", {
    day: "numeric",
    month: "short",
    year: "numeric",
  }).format(new Date(value));
}
</script>

<template>
  <a
    :href="route"
    class="group flex h-full flex-col overflow-hidden border border-slate-200 bg-white shadow-sm transition duration-200 hover:border-uzazure-300 hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-3 focus-visible:outline-uzazure-600"
    :class="compact ? 'min-h-40' : 'min-h-52'"
  >
    <img
      v-if="imageSource"
      :src="imageSource"
      :alt="imageAlternativeText"
      class="aspect-[16/8] w-full object-cover"
      loading="lazy"
      decoding="async"
    />
    <div class="flex flex-1 flex-col p-5">
      <div
        class="flex items-center justify-between gap-4 text-[0.68rem] font-extrabold uppercase tracking-[0.16em] text-uzazure-700"
      >
        <span>{{ label }}</span>
        <span class="font-medium normal-case tracking-normal text-slate-500">{{
          formatDate(item.publishFrom)
        }}</span>
      </div>
      <h3 class="mt-4 text-xl font-bold leading-tight text-slate-950">
        {{ item.title }}
      </h3>
      <p class="mt-3 line-clamp-3 text-sm leading-6 text-slate-600">
        {{ item.summary }}
      </p>
      <span class="mt-auto inline-flex items-center gap-2 pt-5 text-sm font-bold text-uzazure-700">
        {{ item.kind === "LINK" ? "Open service" : "Read more" }}
        <UIcon
          :name="item.kind === 'LINK' ? 'i-lucide-external-link' : 'i-lucide-arrow-right'"
          class="size-4 transition group-hover:translate-x-1"
        />
      </span>
    </div>
  </a>
</template>
