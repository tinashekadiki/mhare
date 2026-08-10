<script setup lang="ts">
withDefaults(defineProps<{
  title: string
  description?: string
  submitLabel?: string
  submitIcon?: string
  busy?: boolean
  submitDisabled?: boolean
  showCancel?: boolean
  cancelLabel?: string
}>(), {
  description: undefined,
  submitLabel: 'Save record',
  submitIcon: 'i-lucide-save',
  busy: false,
  submitDisabled: false,
  showCancel: false,
  cancelLabel: 'Cancel editing'
})

const emit = defineEmits<{
  submit: []
  cancel: []
}>()
</script>

<template>
  <section id="inline-record-editor" class="overflow-hidden rounded-xl border border-muted bg-elevated/20">
    <header class="border-b border-muted bg-elevated/50 px-5 py-4 sm:px-6">
      <h2 class="text-base font-semibold text-highlighted">
        {{ title }}
      </h2>
      <p v-if="description" class="mt-1 text-sm text-muted">
        {{ description }}
      </p>
    </header>

    <div class="p-5 sm:p-6">
      <slot />
    </div>

    <footer class="flex flex-wrap items-center justify-end gap-3 border-t border-muted bg-default px-5 py-4 sm:px-6">
      <UButton
        v-if="showCancel"
        :label="cancelLabel"
        color="neutral"
        variant="ghost"
        :disabled="busy"
        @click="emit('cancel')"
      />
      <UButton
        :label="submitLabel"
        :icon="submitIcon"
        color="primary"
        :loading="busy"
        :disabled="busy || submitDisabled"
        @click="emit('submit')"
      />
    </footer>
  </section>
</template>
