<script setup lang="ts">
defineProps<{
  changes: Array<{ field: string, before?: string | number | null, after?: string | number | null }>
}>()
</script>

<template>
  <EmharePaginatedCollection :items="changes" v-slot="{ items: paginatedChanges }">
  <div class="overflow-hidden rounded-md border border-muted">
    <table class="w-full text-sm">
      <thead class="bg-elevated text-left text-muted">
        <tr>
          <th class="p-3">Field</th>
          <th class="p-3">Before</th>
          <th class="p-3">After</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="change in paginatedChanges" :key="change.field" class="border-t border-muted">
          <td class="p-3 font-medium text-highlighted">{{ change.field }}</td>
          <td class="p-3 text-muted">{{ change.before ?? 'Blank' }}</td>
          <td class="p-3 text-highlighted">{{ change.after ?? 'Blank' }}</td>
        </tr>
      </tbody>
    </table>
  </div>
  </EmharePaginatedCollection>
</template>
