<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  badge?: string
  icon?: string
}>()
</script>

<template>
  <div class="flex flex-col gap-3 border-b border-muted px-4 py-4 sm:flex-row sm:items-center sm:justify-between">
    <div class="min-w-0">
      <div class="flex items-center gap-2">
        <UIcon v-if="icon" :name="icon" class="size-5 text-primary" />
        <h1 class="truncate text-xl font-semibold text-highlighted">
          {{ title }}
        </h1>
        <UBadge v-if="badge" color="primary" variant="soft" :label="badge" />
      </div>
      <p v-if="description" class="mt-1 max-w-3xl text-sm text-muted">
        {{ description }}
      </p>
    </div>
    <div class="flex flex-wrap items-center gap-2">
      <slot name="actions" />
    </div>
  </div>
</template>

