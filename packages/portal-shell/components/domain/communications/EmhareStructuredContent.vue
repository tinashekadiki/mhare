<script setup lang="ts">
import type { StructuredContentBlock } from "@emhare/portal-shell/types/communications";

const props = defineProps<{ blocks: StructuredContentBlock[] }>();
const communications = usePublicCommunications();

function text(block: StructuredContentBlock, key = "text") {
  const value = block[key];
  return typeof value === "string" ? value : "";
}

function listItems(block: StructuredContentBlock) {
  return Array.isArray(block.items)
    ? (block.items.filter((item) => typeof item === "string") as string[])
    : [];
}

function links(block: StructuredContentBlock) {
  if (!Array.isArray(block.links)) return [];
  return block.links.filter((link): link is { label: string; url: string } =>
    Boolean(link && typeof link === "object" && "label" in link && "url" in link),
  );
}

function imageUrl(block: StructuredContentBlock) {
  return typeof block.mediaAssetId === "string" ? communications.mediaUrl(block.mediaAssetId) : "";
}
</script>

<template>
  <div class="space-y-6 text-[1.02rem] leading-8 text-slate-700">
    <template v-for="(block, index) in props.blocks" :key="index">
      <h2
        v-if="block.type === 'HEADING'"
        class="font-serif text-2xl font-semibold text-slate-950 sm:text-3xl"
      >
        {{ text(block) }}
      </h2>
      <p v-else-if="block.type === 'PARAGRAPH'">
        {{ text(block) }}
      </p>
      <ol
        v-else-if="block.type === 'LIST' && block.ordered === true"
        class="list-decimal space-y-2 pl-6 marker:font-bold marker:text-uzazure-700"
      >
        <li v-for="item in listItems(block)" :key="item">{{ item }}</li>
      </ol>
      <ul
        v-else-if="block.type === 'LIST'"
        class="list-disc space-y-2 pl-6 marker:text-uzazure-700"
      >
        <li v-for="item in listItems(block)" :key="item">{{ item }}</li>
      </ul>
      <blockquote
        v-else-if="block.type === 'QUOTE'"
        class="border-l-4 border-uzorange-400 bg-uzorange-50 px-6 py-5 font-serif text-xl italic text-slate-800"
      >
        <p>{{ text(block) }}</p>
        <cite
          v-if="text(block, 'attribution')"
          class="mt-3 block font-sans text-sm not-italic text-slate-600"
          >— {{ text(block, "attribution") }}</cite
        >
      </blockquote>
      <aside
        v-else-if="block.type === 'CALLOUT'"
        class="rounded-2xl border border-uzazure-200 bg-uzazure-50 p-5"
      >
        <h3 v-if="text(block, 'title')" class="font-bold text-uzazure-900">
          {{ text(block, "title") }}
        </h3>
        <p class="mt-1 text-uzazure-900/80">{{ text(block) }}</p>
      </aside>
      <figure
        v-else-if="block.type === 'IMAGE' && imageUrl(block)"
        class="overflow-hidden rounded-3xl bg-slate-100"
      >
        <img
          :src="imageUrl(block)"
          :alt="text(block, 'alternativeText')"
          class="max-h-[34rem] w-full object-cover"
        />
        <figcaption v-if="text(block, 'caption')" class="px-5 py-3 text-sm text-slate-600">
          {{ text(block, "caption") }}
        </figcaption>
      </figure>
      <div v-else-if="block.type === 'LINKS'" class="grid gap-3 sm:grid-cols-2">
        <a
          v-for="link in links(block)"
          :key="link.url"
          :href="link.url"
          class="inline-flex items-center justify-between gap-4 rounded-2xl border border-slate-200 bg-white px-5 py-4 font-semibold text-uzazure-800 transition hover:border-uzazure-400 hover:bg-uzazure-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-uzazure-700"
        >
          {{ link.label }}
          <UIcon name="i-lucide-external-link" class="size-4 shrink-0" />
        </a>
      </div>
    </template>
  </div>
</template>
