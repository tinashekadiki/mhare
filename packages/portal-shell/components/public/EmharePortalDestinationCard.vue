<script setup lang="ts">
defineProps<{
  title: string;
  href: string;
  icon: string;
  actionLabel: string;
}>();
</script>

<template>
  <a
    :href="href"
    class="group flex items-center gap-4 border border-slate-200 bg-white p-5 shadow-sm transition duration-200 hover:-translate-y-0.5 hover:border-uzazure-300 hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-3 focus-visible:outline-uzazure-600"
  >
    <span
      class="grid size-12 shrink-0 place-items-center rounded-sm bg-uzazure-50 text-uzazure-700"
    >
      <UIcon :name="icon" class="size-6" />
    </span>
    <span class="min-w-0 flex-1">
      <span class="block text-xl font-black tracking-tight text-slate-950">
        {{ title }}
      </span>
      <span class="mt-1 block text-sm font-bold text-uzazure-700">
        {{ actionLabel }}
      </span>
    </span>
    <UIcon
      name="i-lucide-arrow-right"
      class="size-5 shrink-0 text-uzazure-700 transition group-hover:translate-x-1"
    />
  </a>
</template>
