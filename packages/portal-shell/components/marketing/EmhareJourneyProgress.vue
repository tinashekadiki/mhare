<script setup lang="ts">
defineProps<{
  steps: Array<{ label: string, done: boolean }>
}>()
</script>

<template>
  <div>
    <div class="flex gap-1.5">
      <div
        v-for="step in steps"
        :key="step.label"
        class="h-1.5 flex-1 rounded-full"
        :class="step.done ? 'bg-primary' : 'bg-accented'"
      />
    </div>
    <div class="mt-1.5 flex justify-between">
      <span
        v-for="step in steps"
        :key="step.label"
        class="text-[11px]"
        :class="step.done ? 'font-medium text-highlighted' : 'text-muted'"
      >
        {{ step.label }}
      </span>
    </div>
  </div>
</template>
