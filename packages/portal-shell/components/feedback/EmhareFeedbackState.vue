<script setup lang="ts">
const props = withDefaults(defineProps<{
  state: 'empty' | 'loading' | 'error' | 'success' | 'warning' | 'info'
  title: string
  description?: string
}>(), {
  description: undefined
})

const meta = computed(() => ({
  empty: { icon: 'i-lucide-inbox', color: 'neutral' as const },
  loading: { icon: 'i-lucide-refresh-cw', color: 'primary' as const },
  error: { icon: 'i-lucide-circle-alert', color: 'error' as const },
  success: { icon: 'i-lucide-check-circle', color: 'success' as const },
  warning: { icon: 'i-lucide-triangle-alert', color: 'warning' as const },
  info: { icon: 'i-lucide-info', color: 'info' as const }
}[props.state]))
</script>

<template>
  <div class="rounded-md border border-muted p-6 text-center">
    <UIcon :name="meta.icon" class="mx-auto size-9" :class="state === 'loading' ? 'animate-spin text-primary' : 'text-muted'" />
    <p class="mt-3 text-sm font-semibold text-highlighted">
      {{ title }}
    </p>
    <p v-if="description" class="mt-1 text-sm text-muted">
      {{ description }}
    </p>
    <div class="mt-4">
      <slot />
    </div>
  </div>
</template>
