<script setup lang="ts">
withDefaults(defineProps<{
  eyebrow?: string
  description?: string
}>(), {
  eyebrow: undefined,
  description: undefined
})
</script>

<template>
  <div class="max-w-2xl">
    <p v-if="eyebrow" class="text-xs font-semibold tracking-wide text-primary uppercase">
      {{ eyebrow }}
    </p>
    <h1 class="mt-3 text-4xl font-extrabold tracking-tight text-highlighted sm:text-5xl">
      <slot name="title" />
    </h1>
    <p v-if="description" class="mt-4 max-w-xl text-base text-muted">
      {{ description }}
    </p>
    <div v-if="$slots.actions" class="mt-7 flex flex-wrap items-center gap-2">
      <slot name="actions" />
    </div>
  </div>
</template>
