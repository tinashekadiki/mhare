<script setup lang="ts">
withDefaults(defineProps<{
  saving?: boolean
  saveLabel?: string
  cancelLabel?: string
  disabled?: boolean
  disabledReason?: string
}>(), {
  saving: false,
  saveLabel: 'Save',
  cancelLabel: 'Cancel',
  disabled: false,
  disabledReason: 'Complete all required fields before saving.'
})

defineEmits<{
  save: []
  cancel: []
}>()
</script>

<template>
  <div class="sticky bottom-0 z-10 flex items-center justify-end gap-2 border-t border-muted bg-default/95 px-4 py-3 backdrop-blur">
    <slot name="left" />
    <div class="ml-auto flex items-center gap-2">
      <UButton color="neutral" variant="ghost" :label="cancelLabel" @click="$emit('cancel')" />
      <EmhareGuidedActionButton color="primary" :label="saveLabel" :loading="saving" :disabled="saving" guidance-title="Record is not ready to save" :guidance-instructions="disabled && !saving ? [disabledReason] : []" @click="$emit('save')" />
    </div>
  </div>
</template>
