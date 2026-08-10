<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  icon?: string
}>()
</script>

<template>
  <section class="space-y-4">
    <div class="flex items-start gap-3">
      <UIcon v-if="icon" :name="icon" class="mt-0.5 size-5 text-primary" />
      <div>
        <h2 class="text-base font-semibold text-highlighted">
          {{ title }}
        </h2>
        <p v-if="description" class="mt-1 text-sm text-muted">
          {{ description }}
        </p>
      </div>
    </div>
    <div class="grid gap-4 md:grid-cols-2">
      <slot />
    </div>
  </section>
</template>

