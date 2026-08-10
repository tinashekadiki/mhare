<script setup lang="ts">
type WizardStep = {
  id: string
  title: string
  description?: string
  icon?: string
  status?: 'complete' | 'current' | 'pending' | 'error'
}

const props = withDefaults(defineProps<{
  steps: WizardStep[]
  currentStep: string
}>(), {
  steps: () => []
})

const emit = defineEmits<{
  'update:currentStep': [stepId: string]
}>()

const currentIndex = computed(() => Math.max(0, props.steps.findIndex((step) => step.id === props.currentStep)))

function goTo(step: WizardStep) {
  emit('update:currentStep', step.id)
}
</script>

<template>
  <div class="space-y-5">
    <ol class="grid gap-2 md:grid-cols-[repeat(auto-fit,minmax(10rem,1fr))]">
      <li v-for="(step, index) in steps" :key="step.id">
        <button
          type="button"
          class="flex w-full items-start gap-3 rounded-md border border-muted p-3 text-left hover:bg-elevated"
          :class="{ 'border-primary bg-primary/5': step.id === currentStep }"
          @click="goTo(step)"
        >
          <span class="grid size-7 shrink-0 place-items-center rounded-full text-xs font-semibold" :class="index <= currentIndex ? 'bg-primary text-inverted' : 'bg-elevated text-muted'">
            <UIcon v-if="step.icon" :name="step.icon" class="size-4" />
            <span v-else>{{ index + 1 }}</span>
          </span>
          <span class="min-w-0">
            <span class="block text-sm font-medium text-highlighted">{{ step.title }}</span>
            <span v-if="step.description" class="mt-1 block text-xs text-muted">{{ step.description }}</span>
          </span>
        </button>
      </li>
    </ol>
    <div>
      <slot :step="steps[currentIndex]" :index="currentIndex" />
    </div>
  </div>
</template>

