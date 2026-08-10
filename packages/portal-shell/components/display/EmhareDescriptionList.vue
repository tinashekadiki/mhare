<script setup lang="ts">
defineProps<{
  items: Array<{ label: string, value?: string | number | null }>
}>()
</script>

<template>
  <dl class="grid gap-3 rounded-md border border-muted p-4 md:grid-cols-2">
    <div v-for="item in items" :key="item.label" class="min-w-0">
      <dt class="text-xs uppercase text-muted">
        {{ item.label }}
      </dt>
      <dd class="mt-1 truncate text-sm font-medium text-highlighted">
        {{ item.value ?? 'Not captured' }}
      </dd>
    </div>
  </dl>
</template>

