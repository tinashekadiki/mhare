<script setup lang="ts">
import type { EmhareAcademicPeriod } from '../../../types/emhare-ui'

defineProps<{
  modelValue?: string
  periods: EmhareAcademicPeriod[]
  label?: string
}>()

defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<template>
  <UFormField :label="label ?? 'Academic period'">
    <USelect
      :model-value="modelValue"
      :items="periods"
      value-key="id"
      label-key="label"
      placeholder="Select academic period"
      @update:model-value="$emit('update:modelValue', String($event))"
    />
  </UFormField>
</template>

