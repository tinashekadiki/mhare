<!-- Author: Tinashe K -->
<script setup lang="ts">
import type { OperationalDashboardSnapshot } from '../../../utils/operational-dashboard'

const props = defineProps<{
  snapshot: OperationalDashboardSnapshot
}>()

const maximumDistributionValue = computed(() => Math.max(1, ...props.snapshot.distribution.map(item => item.value)))
</script>

<template>
  <div class="space-y-6">
    <section aria-labelledby="module-snapshot-heading" class="space-y-3">
      <div class="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p class="text-xs font-semibold uppercase tracking-wide text-primary">Current operational scope</p>
          <h2 id="module-snapshot-heading" class="mt-1 text-lg font-semibold text-highlighted">Module snapshot</h2>
        </div>
        <p class="max-w-2xl text-xs leading-5 text-muted">{{ snapshot.scopeNote }}</p>
      </div>
      <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <EmhareKpiCard
          v-for="metric in snapshot.metrics"
          :key="metric.label"
          :label="metric.label"
          :value="metric.value"
          :hint="metric.hint"
          :icon="metric.icon"
          :tone="metric.tone"
        />
      </div>
    </section>

    <section aria-labelledby="module-workload-heading" class="space-y-3">
      <div>
        <p class="text-xs font-semibold uppercase tracking-wide text-primary">Act from the dashboard</p>
        <h2 id="module-workload-heading" class="mt-1 text-lg font-semibold text-highlighted">Operational workload</h2>
      </div>
      <div class="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
        <UCard
          v-for="action in snapshot.actions"
          :key="`${action.label}-${action.to}`"
          :ui="{ body: 'p-4' }"
          class="border transition-colors hover:border-primary/40"
        >
          <div class="flex items-start justify-between gap-4">
            <div>
              <p class="text-sm font-medium text-muted">{{ action.label }}</p>
              <p class="mt-1 text-3xl font-semibold tabular-nums text-highlighted">{{ action.value }}</p>
            </div>
            <div class="grid size-10 shrink-0 place-items-center rounded-md bg-elevated text-muted">
              <UIcon :name="action.icon" class="size-5" />
            </div>
          </div>
          <p class="mt-3 min-h-10 text-xs leading-5 text-muted">{{ action.description }}</p>
          <UButton
            :to="action.to"
            label="Open queue"
            icon="i-lucide-arrow-up-right"
            trailing
            color="neutral"
            variant="ghost"
            class="mt-3 -mx-2 w-[calc(100%+1rem)] justify-between"
          />
        </UCard>
      </div>
    </section>

    <div class="grid gap-4 xl:grid-cols-[minmax(0,1.05fr)_minmax(0,.95fr)]">
      <section aria-labelledby="module-distribution-heading" class="rounded-xl border border-muted bg-default p-4 sm:p-5">
        <div class="flex items-start justify-between gap-4">
          <div>
            <p class="text-xs font-semibold uppercase tracking-wide text-primary">State distribution</p>
            <h2 id="module-distribution-heading" class="mt-1 font-semibold text-highlighted">Current position</h2>
            <p class="mt-1 text-xs text-muted">Counts retain the source service's governed status boundaries.</p>
          </div>
          <UBadge label="Live service data" color="primary" variant="soft" />
        </div>
        <div v-if="snapshot.distribution.length" class="mt-5 space-y-3">
          <div
            v-for="item in snapshot.distribution"
            :key="item.label"
            class="grid grid-cols-[minmax(8rem,11rem)_1fr_auto] items-center gap-3 text-sm"
          >
            <span class="truncate text-muted">{{ item.label }}</span>
            <div class="h-2.5 overflow-hidden rounded-full bg-elevated">
              <div
                class="h-full rounded-full bg-primary transition-[width] duration-300"
                :style="{ width: `${item.value ? Math.max(3, item.value / maximumDistributionValue * 100) : 0}%` }"
              />
            </div>
            <span class="w-8 text-right font-semibold tabular-nums text-highlighted">{{ item.value }}</span>
          </div>
        </div>
        <p v-else class="mt-5 text-sm text-muted">No governed status records are present yet.</p>
      </section>

      <section aria-labelledby="module-workspaces-heading" class="rounded-xl border border-muted bg-default p-4 sm:p-5">
        <div>
          <p class="text-xs font-semibold uppercase tracking-wide text-primary">Operational workspaces</p>
          <h2 id="module-workspaces-heading" class="mt-1 font-semibold text-highlighted">Continue the work</h2>
        </div>
        <div class="mt-4 divide-y divide-muted">
          <NuxtLink
            v-for="workspace in snapshot.links"
            :key="workspace.to"
            :to="workspace.to"
            class="group grid grid-cols-[auto_1fr_auto] items-center gap-3 py-3 first:pt-0 last:pb-0"
          >
            <span class="grid size-9 place-items-center rounded-md bg-elevated text-primary">
              <UIcon :name="workspace.icon" class="size-4" />
            </span>
            <span class="min-w-0">
              <span class="block text-sm font-medium text-highlighted">{{ workspace.label }}</span>
              <span class="mt-0.5 block text-xs leading-5 text-muted">{{ workspace.description }}</span>
            </span>
            <UIcon name="i-lucide-arrow-up-right" class="size-4 text-muted transition-colors group-hover:text-primary" />
          </NuxtLink>
        </div>
      </section>
    </div>
  </div>
</template>
