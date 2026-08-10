<script setup lang="ts">
type ModuleOption = {
  id: string
  code: string
  title: string
  credits?: number
}

defineProps<{
  modelValue?: string | string[]
  modules: ModuleOption[]
  multiple?: boolean
  label?: string
}>()

defineEmits<{
  'update:modelValue': [value: string | string[]]
}>()
</script>

<template>
  <UFormField :label="label ?? 'Module'">
    <USelectMenu
      :model-value="modelValue"
      :items="modules.map((module) => ({ label: `${module.code} · ${module.title}`, value: module.id }))"
      value-key="value"
      label-key="label"
      :multiple="multiple"
      placeholder="Search modules"
      @update:model-value="$emit('update:modelValue', $event as string | string[])"
    />
  </UFormField>
</template>

