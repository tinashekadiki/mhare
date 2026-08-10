<script setup lang="ts">
defineProps<{
  steps: Array<{ label: string, description?: string }>
}>()
</script>

<template>
  <ol class="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
    <li v-for="(step, index) in steps" :key="step.label">
      <span class="text-xs font-bold text-primary">{{ String(index + 1).padStart(2, '0') }}</span>
      <p class="mt-1 text-sm font-semibold text-highlighted">
        {{ step.label }}
      </p>
      <p v-if="step.description" class="mt-1 text-xs text-muted">
        {{ step.description }}
      </p>
    </li>
  </ol>
</template>
