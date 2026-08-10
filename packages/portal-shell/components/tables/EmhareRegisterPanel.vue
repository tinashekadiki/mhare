<script setup lang="ts">
withDefaults(defineProps<{
  title: string
  description?: string
  recordCount?: number
}>(), {
  description: undefined,
  recordCount: undefined
})
</script>

<template>
  <section class="space-y-3" :aria-label="title">
    <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <div class="min-w-0">
        <div class="flex flex-wrap items-center gap-2">
          <h2 class="text-base font-semibold text-highlighted">
            {{ title }}
          </h2>
          <UBadge
            v-if="recordCount !== undefined"
            :label="String(recordCount)"
            color="neutral"
            variant="subtle"
            size="sm"
          />
        </div>
        <p v-if="description" class="mt-1 text-sm text-muted">
          {{ description }}
        </p>
      </div>
      <div class="flex shrink-0 flex-wrap items-center gap-2">
        <slot name="actions" />
      </div>
    </div>

    <slot />
  </section>
</template>
