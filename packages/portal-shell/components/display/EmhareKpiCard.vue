<script setup lang="ts">
import type { EmhareStatusTone } from '../../types/emhare-ui'

const props = withDefaults(defineProps<{
  label: string
  value: string | number
  hint?: string
  icon?: string
  trend?: string
  tone?: EmhareStatusTone
}>(), {
  hint: undefined,
  icon: undefined,
  trend: undefined,
  tone: 'primary'
})

const toneClass = computed(() => ({
  neutral: 'bg-elevated text-muted',
  primary: 'bg-primary/10 text-primary',
  success: 'bg-success/10 text-success',
  warning: 'bg-warning/10 text-warning',
  error: 'bg-error/10 text-error',
  info: 'bg-info/10 text-info'
})[props.tone])
</script>

<template>
  <UCard :ui="{ body: 'p-4' }">
    <div class="flex items-start justify-between gap-4">
      <div class="min-w-0">
        <p class="text-sm text-muted">
          {{ label }}
        </p>
        <p class="mt-2 truncate text-2xl font-semibold text-highlighted">
          {{ value }}
        </p>
        <p v-if="hint" class="mt-1 text-xs text-muted">
          {{ hint }}
        </p>
      </div>
      <div v-if="icon" class="grid size-10 shrink-0 place-items-center rounded-md" :class="toneClass">
        <UIcon :name="icon" class="size-5" />
      </div>
    </div>
    <UBadge v-if="trend" class="mt-4" color="success" variant="soft" :label="trend" />
  </UCard>
</template>
