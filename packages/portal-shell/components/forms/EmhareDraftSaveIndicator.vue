<script setup lang="ts">
const props = withDefaults(defineProps<{
  state?: 'saved' | 'saving' | 'dirty' | 'error'
  savedAt?: string
}>(), {
  state: 'saved',
  savedAt: undefined
})

const stateMeta = computed(() => ({
  saved: { label: 'Draft saved', color: 'success' as const, icon: 'i-lucide-check' },
  saving: { label: 'Saving draft', color: 'primary' as const, icon: 'i-lucide-refresh-cw' },
  dirty: { label: 'Unsaved changes', color: 'warning' as const, icon: 'i-lucide-pencil' },
  error: { label: 'Draft save failed', color: 'error' as const, icon: 'i-lucide-circle-alert' }
}[props.state]))
</script>

<template>
  <UBadge
    :color="stateMeta.color"
    variant="soft"
    :icon="stateMeta.icon"
    :label="savedAt && state === 'saved' ? `${stateMeta.label} ${savedAt}` : stateMeta.label"
  />
</template>
