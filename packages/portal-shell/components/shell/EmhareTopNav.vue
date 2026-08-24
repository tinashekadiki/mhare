<script setup lang="ts">
type Breadcrumb = { label: string; to?: string };

withDefaults(
  defineProps<{
    breadcrumbs: Breadcrumb[];
    homeTo?: string;
  }>(),
  {
    homeTo: "/",
  },
);
</script>

<template>
  <header
    class="sticky top-0 z-20 border-b border-slate-200 bg-white/95 backdrop-blur supports-[backdrop-filter]:bg-white/85"
  >
    <div class="mx-auto flex max-w-[80rem] items-center justify-between gap-4 px-4 py-3 sm:px-6">
      <div class="flex min-w-0 items-center gap-3">
        <NuxtLink
          :to="homeTo"
          class="grid size-9 shrink-0 place-items-center rounded-lg bg-uzazure-800 font-serif text-base font-bold text-uzorange-200 transition hover:bg-uzazure-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-uzazure-700"
          aria-label="eMhare home"
        >
          e
        </NuxtLink>
        <nav aria-label="Breadcrumb" class="min-w-0">
          <ol class="flex min-w-0 items-center gap-1.5 text-sm">
            <li
              v-for="(crumb, index) in breadcrumbs"
              :key="crumb.label"
              class="flex min-w-0 items-center gap-1.5"
            >
              <UIcon
                v-if="index > 0"
                name="i-lucide-chevron-right"
                class="size-3.5 shrink-0 text-slate-300"
              />
              <NuxtLink
                v-if="crumb.to && index < breadcrumbs.length - 1"
                :to="crumb.to"
                class="truncate text-slate-500 transition hover:text-uzazure-800"
              >
                {{ crumb.label }}
              </NuxtLink>
              <span v-else class="truncate font-semibold text-slate-900">{{ crumb.label }}</span>
            </li>
          </ol>
        </nav>
      </div>
      <div class="flex shrink-0 items-center gap-2 sm:gap-3">
        <slot name="meta" />
        <slot name="actions" />
      </div>
    </div>
  </header>
</template>
