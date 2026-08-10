<script setup lang="ts">
defineProps<{
  title: string
  items: Array<{ label: string, status: 'complete' | 'pending' | 'blocked', description?: string }>
}>()
</script>

<template>
  <div class="rounded-md border border-muted p-4">
    <h3 class="text-sm font-semibold text-highlighted">
      {{ title }}
    </h3>
    <EmharePaginatedCollection :items="items" v-slot="{ items: paginatedItems }">
    <ul class="mt-3 space-y-2">
      <li v-for="item in paginatedItems" :key="item.label" class="flex items-start gap-3">
        <UIcon
          :name="item.status === 'complete' ? 'i-lucide-check-circle' : item.status === 'blocked' ? 'i-lucide-circle-alert' : 'i-lucide-circle'"
          class="mt-0.5 size-4"
          :class="item.status === 'complete' ? 'text-success' : item.status === 'blocked' ? 'text-error' : 'text-muted'"
        />
        <span class="min-w-0">
          <span class="block text-sm font-medium text-highlighted">{{ item.label }}</span>
          <span v-if="item.description" class="mt-0.5 block text-xs text-muted">{{ item.description }}</span>
        </span>
      </li>
    </ul>
    </EmharePaginatedCollection>
  </div>
</template>
