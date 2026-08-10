<script setup lang="ts">
defineProps<{
  steps: Array<{
    label: string;
    actor?: string;
    status: "complete" | "current" | "pending" | "rejected";
    time?: string;
  }>;
}>();
</script>

<template>
  <EmharePaginatedCollection :items="steps" v-slot="{ items: paginatedSteps }">
  <ol class="space-y-3">
    <li v-for="step in paginatedSteps" :key="step.label" class="flex gap-3">
      <UIcon
        :name="
          step.status === 'complete'
            ? 'i-lucide-check-circle'
            : step.status === 'rejected'
              ? 'i-lucide-x-circle'
              : 'i-lucide-circle'
        "
        class="mt-0.5 size-5"
        :class="
          step.status === 'complete'
            ? 'text-success'
            : step.status === 'rejected'
              ? 'text-error'
              : step.status === 'current'
                ? 'text-primary'
                : 'text-muted'
        "
      />
      <div class="min-w-0">
        <p class="text-sm font-medium text-highlighted">{{ step.label }}</p>
        <p class="mt-0.5 text-xs text-muted">
          {{ step.actor ?? "Unassigned"
          }}<span v-if="step.time"> · {{ step.time }}</span>
        </p>
      </div>
    </li>
  </ol>
  </EmharePaginatedCollection>
</template>
