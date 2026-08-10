<script setup lang="ts">
withDefaults(defineProps<{
  items: Array<{ title: string, description?: string, time?: string, tone?: 'neutral' | 'primary' | 'success' | 'warning' | 'error' | 'info' }>
}>(), {
  items: () => []
})
</script>

<template>
  <EmharePaginatedCollection :items="items" v-slot="{ items: paginatedItems }">
  <ol class="space-y-4">
    <li v-for="item in paginatedItems" :key="`${item.title}-${item.time}`" class="flex gap-3">
      <span class="mt-1 size-2.5 shrink-0 rounded-full bg-primary" />
      <div class="min-w-0">
        <p class="text-sm font-medium text-highlighted">
          {{ item.title }}
        </p>
        <p v-if="item.description" class="mt-1 text-sm text-muted">
          {{ item.description }}
        </p>
        <p v-if="item.time" class="mt-1 text-xs text-muted">
          {{ item.time }}
        </p>
      </div>
    </li>
  </ol>
  </EmharePaginatedCollection>
</template>
