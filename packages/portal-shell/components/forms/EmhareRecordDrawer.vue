<script setup lang="ts">
const props = withDefaults(defineProps<{
  open: boolean
  title: string
  description?: string
  submitLabel?: string
  submitIcon?: string
  busy?: boolean
  submitDisabled?: boolean
  submitDisabledReason?: string
  showBack?: boolean
  backLabel?: string
  width?: 'md' | 'lg' | 'xl' | 'wide'
}>(), {
  description: undefined,
  submitLabel: 'Save',
  submitIcon: 'i-lucide-save',
  busy: false,
  submitDisabled: false,
  submitDisabledReason: 'Complete all required fields before continuing.',
  showBack: false,
  backLabel: 'Back',
  width: 'lg'
})

const emit = defineEmits<{
  'update:open': [open: boolean]
  submit: []
  back: []
  close: []
}>()

const drawerUi = computed(() => ({
  content: {
    md: 'w-screen max-w-full sm:w-[30rem] sm:max-w-[calc(100vw-2rem)]',
    lg: 'w-screen max-w-full sm:w-[38rem] sm:max-w-[calc(100vw-2rem)]',
    xl: 'w-screen max-w-full sm:w-[52rem] sm:max-w-[calc(100vw-2rem)]',
    wide: 'w-screen max-w-full sm:w-[56rem] lg:w-[64rem] sm:max-w-[calc(100vw-2rem)]'
  }[props.width],
  header: 'border-b border-muted bg-elevated/60 px-5 py-4 sm:px-6',
  body: 'flex-1 min-w-0 overflow-y-auto px-5 py-5 sm:px-6',
  footer: 'border-t border-muted bg-default px-5 py-4 sm:px-6'
}))

function updateOpen(open: boolean) {
  emit('update:open', open)
  if (!open) {
    emit('close')
  }
}
</script>

<template>
  <USlideover
    :open="open"
    side="right"
    :title="title"
    :description="description"
    :dismissible="false"
    :close="!busy"
    :ui="drawerUi"
    @update:open="updateOpen"
  >
    <template #body>
      <div class="min-w-0 space-y-5">
        <slot name="body">
          <slot />
        </slot>
      </div>
    </template>

    <template #footer>
      <slot name="footer">
        <div class="flex w-full items-center justify-between gap-3">
          <UButton
            v-if="showBack"
            :label="backLabel"
            icon="i-lucide-arrow-left"
            color="neutral"
            variant="ghost"
            :disabled="busy"
            @click="emit('back')"
          />
          <span v-else aria-hidden="true" />
          <div class="flex items-center justify-end gap-3">
            <UButton
              label="Cancel"
              color="neutral"
              variant="ghost"
              :disabled="busy"
              @click="updateOpen(false)"
            />
            <EmhareGuidedActionButton
              :icon="submitIcon"
              :label="submitLabel"
              color="primary"
              :loading="busy"
              :disabled="busy"
              guidance-title="Required information is incomplete"
              :guidance-instructions="submitDisabled && !busy ? [submitDisabledReason] : []"
              @click="emit('submit')"
            />
          </div>
        </div>
      </slot>
    </template>
  </USlideover>
</template>
