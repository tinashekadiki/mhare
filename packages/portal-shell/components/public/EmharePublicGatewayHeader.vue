<script setup lang="ts">
// Author: Tinashe K
withDefaults(
  defineProps<{
    navigationItems?: Array<{ label: string; href: string }>;
  }>(),
  {
    navigationItems: () => [
      { label: "Home", href: "#portal-access" },
      { label: "Notices", href: "#notices" },
      { label: "Services", href: "#services" },
      { label: "Events", href: "#events" },
      { label: "News", href: "#news" },
    ],
  },
);
const menuOpen = ref(false);
const officialUniversityLogoPath = "/images/brand/university-of-zimbabwe-logo.png";
</script>

<template>
  <header class="bg-white text-slate-900" @keydown.esc="menuOpen = false">
    <div class="h-1.5 bg-uzazure-700" />
    <div class="mx-auto flex max-w-7xl items-center gap-4 px-5 py-4 sm:px-8">
      <NuxtLink
        to="/"
        class="flex min-w-0 flex-1 items-center justify-between gap-6 rounded-sm focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-uzazure-600"
      >
        <img
          data-emhare-institution-logo
          :src="officialUniversityLogoPath"
          alt="University of Zimbabwe"
          class="h-16 w-auto object-contain sm:h-20"
        />
        <EmhareProductBrand
          appearance="wordmark"
          tone="blue-gold"
          powered-by
          class="ml-auto hidden min-w-56 justify-end border-l border-slate-200 pl-5 text-uzazure-800 sm:flex [&_[data-emhare-product-logo]]:h-10 [&_[data-emhare-product-logo]]:max-w-40"
        />
      </NuxtLink>
      <button
        type="button"
        class="grid size-11 place-items-center rounded-sm border border-slate-300 text-uzazure-700 md:hidden"
        aria-label="Toggle public navigation"
        :aria-expanded="menuOpen"
        @click="menuOpen = !menuOpen"
      >
        <UIcon name="i-lucide-menu" class="size-5" />
      </button>
    </div>
    <div class="bg-uzazure-700 text-white shadow-sm">
      <div class="mx-auto flex max-w-7xl flex-wrap items-center justify-between px-5 sm:px-8">
        <nav class="hidden items-center text-sm font-bold md:flex" aria-label="Public gateway">
          <a
            v-for="(item, index) in navigationItems"
            :key="item.href"
            :href="item.href"
            class="border-b-4 px-4 py-3.5 hover:bg-white/10 focus-visible:outline-2 focus-visible:outline-offset-[-4px] focus-visible:outline-white"
            :class="index === 0 ? 'border-uzorange-500' : 'border-transparent'"
            >{{ item.label }}</a
          >
        </nav>
        <div v-if="$slots.actions" class="flex min-h-14 flex-wrap items-center gap-2 py-2">
          <slot name="actions" />
        </div>
      </div>
      <nav v-if="menuOpen" class="px-5 py-3 md:hidden" aria-label="Mobile public gateway">
        <div class="mx-auto grid max-w-7xl grid-cols-2 gap-1 text-sm font-semibold">
          <a
            v-for="item in navigationItems"
            :key="item.href"
            :href="item.href"
            class="rounded-sm px-3 py-2.5 hover:bg-white/10"
            @click="menuOpen = false"
            >{{ item.label }}</a
          >
        </div>
      </nav>
    </div>
  </header>
</template>
