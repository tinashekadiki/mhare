<script setup lang="ts">
import { computed } from "vue";

type ProductBrandAppearance = "compact" | "wordmark";
type ProductBrandTone = "blue" | "blue-gold" | "light";

const props = withDefaults(
  defineProps<{
    appearance?: ProductBrandAppearance;
    tone?: ProductBrandTone;
    label?: string;
    description?: string;
    poweredBy?: boolean;
    showCopy?: boolean;
  }>(),
  {
    appearance: "compact",
    tone: "blue-gold",
    label: "eMhare",
    description: "University Information System",
    poweredBy: false,
    showCopy: true,
  },
);

const wordmarkSourceByTone: Record<ProductBrandTone, string> = {
  blue: "/images/brand/emhare-wordmark-blue.png",
  "blue-gold": "/images/brand/emhare-wordmark-blue-gold.png",
  light: "/images/brand/emhare-wordmark-light.png",
};

const logoSource = computed(() =>
  props.appearance === "compact"
    ? "/images/brand/emhare-emblem-blue.png"
    : wordmarkSourceByTone[props.tone],
);
</script>

<template>
  <div
    v-if="appearance === 'compact'"
    class="flex min-w-0 items-center gap-3"
    data-emhare-product-brand
  >
    <img
      data-emhare-product-logo
      :src="logoSource"
      :alt="showCopy ? '' : label"
      :aria-hidden="showCopy"
      class="size-9 shrink-0 object-contain"
    />
    <div v-if="showCopy" class="min-w-0 leading-tight">
      <p class="truncate text-sm font-semibold text-highlighted">
        {{ label }}
      </p>
      <p class="truncate text-xs text-muted">
        {{ description }}
      </p>
    </div>
  </div>

  <div v-else class="flex min-w-0 items-center gap-2" data-emhare-product-brand>
    <span
      v-if="poweredBy"
      class="shrink-0 text-[0.625rem] font-semibold uppercase tracking-[0.14em] opacity-70"
    >
      Powered by
    </span>
    <img
      data-emhare-product-logo
      :src="logoSource"
      alt="eMhare University Information System"
      class="h-8 w-auto max-w-28 object-contain"
    />
  </div>
</template>
