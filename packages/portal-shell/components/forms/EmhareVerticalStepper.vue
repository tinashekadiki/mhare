<script setup lang="ts">
type Step = {
  id: string;
  title: string;
  description?: string;
  icon?: string;
  status?: "complete" | "current" | "pending" | "attention";
  required?: boolean;
  disabled?: boolean;
};

const props = defineProps<{
  steps: Step[];
  currentStep: string;
  label?: string;
}>();

const emit = defineEmits<{
  "update:currentStep": [stepId: string];
}>();

function select(step: Step) {
  if (step.disabled) return;
  emit("update:currentStep", step.id);
}
</script>

<template>
  <nav :aria-label="label ?? 'Application steps'">
    <ol class="scrollbar-thin flex gap-2 overflow-x-auto pb-1 lg:hidden">
      <li v-for="step in steps" :key="step.id" class="shrink-0">
        <button
          type="button"
          :disabled="step.disabled"
          class="flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-semibold whitespace-nowrap transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-uzazure-700"
          :class="
            step.disabled
              ? 'cursor-default border-slate-200 text-slate-300'
              : step.id === currentStep
                ? 'border-uzazure-700 bg-uzazure-50 text-uzazure-900'
                : step.status === 'attention'
                  ? 'border-uzorange-400 bg-uzorange-50 text-uzorange-900 hover:border-uzorange-500'
                  : step.status === 'complete'
                    ? 'border-uzazure-200 bg-white text-uzazure-700 hover:border-uzazure-400'
                    : 'border-slate-200 bg-white text-slate-500 hover:border-slate-300'
          "
          :aria-current="step.id === currentStep ? 'step' : undefined"
          @click="select(step)"
        >
          <span
            class="grid size-4 shrink-0 place-items-center rounded-full text-[10px] font-bold"
            :class="
              step.status === 'complete'
                ? 'bg-uzazure-600 text-white'
                : step.id === currentStep
                  ? 'bg-uzazure-700 text-white'
                  : 'bg-slate-200 text-slate-500'
            "
          >
            <UIcon v-if="step.status === 'complete'" name="i-lucide-check" class="size-2.5" />
          </span>
          {{ step.title }}
        </button>
      </li>
    </ol>

    <ol class="hidden lg:block">
      <li v-for="(step, index) in steps" :key="step.id" class="relative pb-6 last:pb-0">
        <span
          v-if="index < steps.length - 1"
          class="absolute top-9 left-[1.15rem] h-[calc(100%-1.5rem)] w-px"
          :class="step.status === 'complete' ? 'bg-uzazure-300' : 'bg-slate-200'"
          aria-hidden="true"
        />
        <button
          type="button"
          :disabled="step.disabled"
          class="group flex w-full items-start gap-3 rounded-lg border-l-4 py-2 pr-2 pl-3 text-left transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-uzazure-700"
          :class="
            step.disabled
              ? 'cursor-default border-transparent'
              : step.id === currentStep
                ? 'border-uzazure-700 bg-uzazure-50'
                : step.status === 'attention'
                  ? 'border-uzorange-400 hover:bg-slate-50'
                  : 'border-transparent hover:bg-slate-50'
          "
          :aria-current="step.id === currentStep ? 'step' : undefined"
          @click="select(step)"
        >
          <span
            class="grid size-9 shrink-0 place-items-center rounded-full border-2 text-sm font-bold transition"
            :class="
              step.status === 'complete'
                ? 'border-uzazure-600 bg-uzazure-600 text-white'
                : step.status === 'attention'
                  ? 'border-uzorange-400 bg-uzorange-50 text-uzorange-800'
                  : step.id === currentStep
                    ? 'border-uzazure-700 bg-white text-uzazure-800'
                    : 'border-slate-200 bg-white text-slate-400'
            "
          >
            <UIcon v-if="step.status === 'complete'" name="i-lucide-check" class="size-4" />
            <UIcon
              v-else-if="step.status === 'attention'"
              name="i-lucide-triangle-alert"
              class="size-4"
            />
            <span v-else>{{ index + 1 }}</span>
          </span>
          <span class="min-w-0 pt-1">
            <span
              class="block text-sm font-semibold"
              :class="
                step.id === currentStep
                  ? 'text-uzazure-950'
                  : step.disabled
                    ? 'text-slate-400'
                    : 'text-slate-700 group-hover:text-slate-900'
              "
            >
              {{ step.title }}<span v-if="step.required" class="ml-1 text-uzorange-700">*</span>
            </span>
            <span v-if="step.description" class="mt-0.5 block truncate text-xs text-slate-500">{{
              step.description
            }}</span>
          </span>
        </button>
      </li>
    </ol>
  </nav>
</template>

<style scoped>
.scrollbar-thin {
  scrollbar-width: thin;
}
</style>
