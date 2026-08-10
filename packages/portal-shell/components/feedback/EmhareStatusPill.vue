<script setup lang="ts">
import type { EmhareStatusTone } from '../../types/emhare-ui'

withDefaults(defineProps<{
  label: string
  tone?: EmhareStatusTone
  icon?: string
}>(), {
  tone: 'neutral',
  icon: undefined
})
</script>

<template>
  <UBadge :color="tone" variant="subtle" :icon="icon" :label="label" />
</template>

