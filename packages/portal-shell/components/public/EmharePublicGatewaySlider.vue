<script setup lang="ts">
export type PublicGatewaySlide = {
  image: string;
  alternativeText: string;
  title?: string;
  href?: string;
  actionLabel?: string;
};

const props = defineProps<{ slides: PublicGatewaySlide[] }>();
const activeIndex = ref(0);
const hasMultipleSlides = computed(() => props.slides.length > 1);
const activeSlide = computed(() => props.slides[activeIndex.value]);

watch(
  () => props.slides.length,
  (slideCount) => {
    if (activeIndex.value >= slideCount) activeIndex.value = 0;
  },
);

function showSlide(index: number) {
  activeIndex.value = index;
}

function showPreviousSlide() {
  activeIndex.value = (activeIndex.value - 1 + props.slides.length) % props.slides.length;
}

function showNextSlide() {
  activeIndex.value = (activeIndex.value + 1) % props.slides.length;
}
</script>

<template>
  <section
    v-if="slides.length"
    class="relative overflow-hidden bg-slate-900"
    aria-label="Featured university updates"
    aria-roledescription="carousel"
    tabindex="0"
    @keydown.left.prevent="showPreviousSlide"
    @keydown.right.prevent="showNextSlide"
  >
    <div
      class="flex transition-transform duration-500 ease-out motion-reduce:transition-none"
      :style="{ transform: `translateX(-${activeIndex * 100}%)` }"
    >
      <article
        v-for="(slide, index) in slides"
        :key="`${slide.image}-${index}`"
        class="relative h-64 min-w-full sm:h-72"
        :aria-hidden="index !== activeIndex"
      >
        <img
          :src="slide.image"
          :alt="slide.alternativeText"
          class="size-full object-cover"
          :loading="index === 0 ? 'eager' : 'lazy'"
          decoding="async"
        />
        <div
          v-if="slide.title"
          class="absolute inset-x-0 bottom-0 bg-gradient-to-t from-slate-950/95 via-slate-950/70 to-transparent px-6 pb-6 pt-16 text-white sm:px-8"
        >
          <h2 class="max-w-3xl text-xl font-black leading-tight sm:text-2xl">{{ slide.title }}</h2>
          <NuxtLink
            v-if="slide.href"
            :to="slide.href"
            class="mt-3 inline-flex items-center gap-2 text-sm font-bold text-uzorange-300 hover:text-white"
          >
            {{ slide.actionLabel ?? "View update" }}
            <UIcon name="i-lucide-arrow-right" class="size-4" />
          </NuxtLink>
        </div>
      </article>
    </div>

    <template v-if="hasMultipleSlides">
      <button
        type="button"
        class="absolute left-3 top-1/2 grid size-11 -translate-y-1/2 place-items-center rounded-full bg-slate-950/75 text-white shadow-lg hover:bg-uzazure-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
        aria-label="Show previous image"
        @click="showPreviousSlide"
      >
        <UIcon name="i-lucide-arrow-left" class="size-5" />
      </button>
      <button
        type="button"
        class="absolute right-3 top-1/2 grid size-11 -translate-y-1/2 place-items-center rounded-full bg-slate-950/75 text-white shadow-lg hover:bg-uzazure-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
        aria-label="Show next image"
        @click="showNextSlide"
      >
        <UIcon name="i-lucide-arrow-right" class="size-5" />
      </button>
      <div class="absolute bottom-4 right-5 flex items-center gap-2" aria-label="Choose image">
        <button
          v-for="(_, index) in slides"
          :key="index"
          type="button"
          class="size-2.5 rounded-full border border-white shadow"
          :class="index === activeIndex ? 'bg-uzorange-500' : 'bg-white/50'"
          :aria-label="`Show image ${index + 1}`"
          :aria-current="index === activeIndex ? 'true' : undefined"
          @click="showSlide(index)"
        />
      </div>
    </template>

    <p class="sr-only" aria-live="polite">
      Image {{ activeIndex + 1 }} of {{ slides.length
      }}<template v-if="activeSlide?.title"> : {{ activeSlide.title }}</template>
    </p>
  </section>
</template>
