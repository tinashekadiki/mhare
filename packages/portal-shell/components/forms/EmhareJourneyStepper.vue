<script setup lang="ts">
type JourneyStep = {
  id: string
  title: string
  description?: string
  icon?: string
  status?: 'complete' | 'current' | 'pending' | 'attention'
  required?: boolean
  disabled?: boolean
}

const props = defineProps<{
  steps: JourneyStep[]
  currentStep: string
  label?: string
}>()

const emit = defineEmits<{
  'update:currentStep': [stepId: string]
}>()

function selectStep(step: JourneyStep) {
  if (step.disabled) return
  emit('update:currentStep', step.id)
}
</script>

<template>
  <nav class="overflow-x-auto" :aria-label="label ?? 'Journey progress'">
    <ol class="journey-stepper flex min-w-max items-stretch gap-0 px-1 py-2 md:min-w-0 md:px-0">
      <li
        v-for="(step, index) in steps"
        :key="step.id"
        class="flex min-w-[10.75rem] flex-1 items-center last:min-w-[9rem]"
      >
        <button
          type="button"
          :disabled="step.disabled"
          class="group flex min-w-0 flex-1 items-center gap-3 border-b-2 px-3 py-3 text-left transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          :class="step.disabled ? 'cursor-default border-transparent text-muted' : step.id === currentStep ? 'border-uzgreen-700 text-uzgreen-950' : 'border-transparent text-muted hover:border-uzgreen-200 hover:text-uzgreen-900'"
          :aria-current="step.id === currentStep ? 'step' : undefined"
          @click="selectStep(step)"
        >
          <span
            class="grid size-8 shrink-0 place-items-center rounded-full text-xs font-bold transition"
            :class="step.status === 'complete'
              ? 'bg-uzgreen-600 text-white'
              : step.status === 'attention'
                ? 'bg-uzgold-100 text-uzgold-900'
                : step.id === currentStep
                  ? 'bg-uzgreen-100 text-uzgreen-800'
                  : 'bg-muted text-muted'"
          >
            <UIcon v-if="step.status === 'complete'" name="i-lucide-check" class="size-4" />
            <UIcon v-else-if="step.icon" :name="step.icon" class="size-4" />
            <span v-else>{{ index + 1 }}</span>
          </span>
          <span class="min-w-0">
            <span class="block truncate text-sm font-semibold text-highlighted">
              {{ step.title }}<span v-if="step.required" class="ml-1 text-uzgold-700">*</span>
            </span>
            <span v-if="step.description" class="mt-0.5 block truncate text-xs text-muted">{{ step.description }}</span>
          </span>
        </button>
        <span
          v-if="index < steps.length - 1"
          class="mx-1 hidden h-px w-6 shrink-0 bg-uzgreen-200 lg:block"
          :class="step.status === 'complete' ? 'bg-uzgreen-500' : 'bg-uzgreen-100'"
          aria-hidden="true"
        />
      </li>
    </ol>
  </nav>
</template>

<style scoped>
.journey-stepper {
  scrollbar-width: thin;
}
</style>
