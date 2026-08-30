<script setup lang="ts">
// Author: Tinashe K
withDefaults(
  defineProps<{
    embedded?: boolean;
    title: string;
    description?: string;
    submitLabel?: string;
    submitIcon?: string;
    busy?: boolean;
    submitDisabled?: boolean;
    showCancel?: boolean;
    cancelLabel?: string;
  }>(),
  {
    embedded: false,
    description: undefined,
    submitLabel: "Save record",
    submitIcon: "i-lucide-save",
    busy: false,
    submitDisabled: false,
    showCancel: false,
    cancelLabel: "Cancel editing",
  },
);

const emit = defineEmits<{
  submit: [];
  cancel: [];
}>();
</script>

<template>
  <section
    id="inline-record-editor"
    :class="
      embedded ? 'space-y-4' : 'overflow-hidden rounded-xl border border-muted bg-elevated/20'
    "
  >
    <header v-if="!embedded" class="border-b border-muted bg-elevated/50 px-5 py-4 sm:px-6">
      <h2 class="text-base font-semibold text-highlighted">
        {{ title }}
      </h2>
      <p v-if="description" class="mt-1 text-sm text-muted">
        {{ description }}
      </p>
    </header>

    <p v-if="embedded && description" class="text-sm text-muted">{{ description }}</p>
    <div :class="embedded ? undefined : 'p-5 sm:p-6'">
      <slot />
    </div>

    <footer
      v-if="!embedded"
      class="flex flex-wrap items-center justify-end gap-3 border-t border-muted bg-default px-5 py-4 sm:px-6"
    >
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
