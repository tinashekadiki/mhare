<script setup lang="ts">
type AdmissionsWorkflowStage =
  "confirm" | "release" | "recommend" | "decide" | "offer";

defineProps<{
  currentStage: AdmissionsWorkflowStage;
}>();

const stages: Array<{
  id: AdmissionsWorkflowStage;
  number: number;
  title: string;
  description: string;
  icon: string;
  to: string;
}> = [
  {
    id: "confirm",
    number: 1,
    title: "Confirm",
    description: "Admissions checks",
    icon: "i-lucide-shield-check",
    to: "/operations/admissions",
  },
  {
    id: "release",
    number: 2,
    title: "Release",
    description: "Create batches",
    icon: "i-lucide-layers-3",
    to: "/operations/admissions-academic-release",
  },
  {
    id: "recommend",
    number: 3,
    title: "Recommend",
    description: "Academic unit",
    icon: "i-lucide-clipboard-check",
    to: "/operations/admissions-recommendations",
  },
  {
    id: "decide",
    number: 4,
    title: "Decide",
    description: "Admissions final",
    icon: "i-lucide-scale",
    to: "/operations/admissions-decisions",
  },
  {
    id: "offer",
    number: 5,
    title: "Offer",
    description: "Issue and dispatch",
    icon: "i-lucide-mail-check",
    to: "/operations/admissions-offers",
  },
];

</script>

<template>
  <section
    aria-labelledby="admissions-workflow-heading"
    class="overflow-hidden rounded-lg border border-muted bg-default"
  >
    <div class="border-b border-muted px-4 py-3 sm:px-5">
      <div>
        <p class="text-xs font-semibold uppercase tracking-wide text-primary">
          One workspace
        </p>
        <h2
          id="admissions-workflow-heading"
          class="mt-0.5 font-semibold text-highlighted"
        >
          Admissions Workflow
        </h2>
      </div>
    </div>

    <nav aria-label="Admissions workflow stages" class="overflow-x-auto p-2">
      <div class="grid min-w-[760px] grid-cols-5 gap-2">
        <NuxtLink
          v-for="stage in stages"
          :key="stage.id"
          :to="stage.to"
          class="group flex min-w-0 items-center gap-3 rounded-md border px-3 py-3 transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          :class="
            currentStage === stage.id
              ? 'border-primary bg-primary/5'
              : 'border-transparent hover:border-muted hover:bg-elevated/60'
          "
          :aria-current="currentStage === stage.id ? 'page' : undefined"
        >
          <span
            class="flex size-8 shrink-0 items-center justify-center rounded-md text-sm font-bold"
            :class="
              currentStage === stage.id
                ? 'bg-primary text-inverted'
                : 'bg-elevated text-muted group-hover:text-highlighted'
            "
          >
            {{ stage.number }}
          </span>
          <span class="min-w-0">
            <span
              class="flex items-center gap-1.5 font-semibold text-highlighted"
            >
              <UIcon :name="stage.icon" class="size-4" />
              {{ stage.title }}
            </span>
            <span class="mt-0.5 block truncate text-xs text-muted">
              {{ stage.description }}
            </span>
          </span>
        </NuxtLink>
      </div>
    </nav>
  </section>
</template>
