<script setup lang="ts">
withDefaults(defineProps<{
  label: string
  value?: string | number | null
  wide?: boolean
}>(), {
  value: null,
  wide: false
})
</script>

<template>
  <div :class="wide ? 'md:col-span-2' : undefined">
    <dt class="text-xs font-medium uppercase tracking-wide text-muted">
      {{ label }}
    </dt>
    <dd class="mt-1 whitespace-pre-line text-sm font-medium text-highlighted">
      {{ value === null || value === undefined || value === '' ? 'Not provided' : value }}
    </dd>
  </div>
</template>
