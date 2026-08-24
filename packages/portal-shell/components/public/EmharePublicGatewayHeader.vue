<script setup lang="ts">
const menuOpen = ref(false);
const officialUniversityLogoPath = "/images/brand/university-of-zimbabwe-logo.png";
</script>

<template>
  <header class="bg-white text-slate-900">
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
      <nav
        class="mx-auto hidden max-w-7xl items-center px-5 text-sm font-bold sm:px-8 md:flex"
        aria-label="Public gateway"
      >
        <a href="#portal-access" class="border-b-4 border-uzorange-500 px-4 py-3.5">Home</a>
        <a href="#notices" class="px-4 py-3.5 hover:bg-white/10">Notices</a>
        <a href="#services" class="px-4 py-3.5 hover:bg-white/10">Services</a>
        <a href="#events" class="px-4 py-3.5 hover:bg-white/10">Events</a>
        <a href="#news" class="px-4 py-3.5 hover:bg-white/10">News</a>
      </nav>
      <nav v-if="menuOpen" class="px-5 py-3 md:hidden" aria-label="Mobile public gateway">
        <div class="mx-auto grid max-w-7xl grid-cols-2 gap-1 text-sm font-semibold">
          <a
            href="#portal-access"
            class="rounded-sm bg-white/10 px-3 py-2.5"
            @click="menuOpen = false"
            >Home</a
          >
          <a
            href="#notices"
            class="rounded-sm px-3 py-2.5 hover:bg-white/10"
            @click="menuOpen = false"
            >Notices</a
          >
          <a
            href="#services"
            class="rounded-sm px-3 py-2.5 hover:bg-white/10"
            @click="menuOpen = false"
            >Services</a
          >
          <a
            href="#events"
            class="rounded-sm px-3 py-2.5 hover:bg-white/10"
            @click="menuOpen = false"
            >Events</a
          >
          <a href="#news" class="rounded-sm px-3 py-2.5 hover:bg-white/10" @click="menuOpen = false"
            >News</a
          >
        </div>
      </nav>
    </div>
  </header>
</template>
