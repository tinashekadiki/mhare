<script setup lang="ts">
import type { AdmissionsWorkflowBatchView } from "../../../types/admissions";

const props = withDefaults(
  defineProps<{
    batches: AdmissionsWorkflowBatchView[];
    loading?: boolean;
    emptyTitle?: string;
    emptyDescription?: string;
  }>(),
  {
    loading: false,
    emptyTitle: "No batches in this stage",
    emptyDescription:
      "Batches appear here when applicants reach this workflow stage.",
  },
);

const openBatchIds = ref<string[]>([]);

function batchIsOpen(batchId: string) {
  return openBatchIds.value.includes(batchId);
}

function setBatchOpen(batchId: string, open: boolean) {
  openBatchIds.value = open
    ? Array.from(new Set([...openBatchIds.value, batchId]))
    : openBatchIds.value.filter((id) => id !== batchId);
}
</script>

<template>
  <div class="space-y-3">
    <USkeleton v-if="loading && !batches.length" class="h-28 w-full" />

    <UCard
      v-for="batch in props.batches"
      :key="batch.id"
      variant="outline"
      :ui="{ body: 'p-0' }"
      class="overflow-hidden"
    >
      <UCollapsible
        :open="batchIsOpen(batch.id)"
        :ui="{ content: 'border-t border-muted' }"
        @update:open="setBatchOpen(batch.id, $event)"
      >
        <div class="border-l-4 border-primary px-4 py-4 sm:px-5">
          <div
            class="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between"
          >
            <div class="min-w-0">
              <div class="flex flex-wrap items-center gap-2">
                <span class="font-mono text-xs font-semibold text-primary">
                  {{ batch.code }}
                </span>
                <UBadge color="primary" variant="subtle" size="sm">
                  {{ batch.stageLabel }}
                </UBadge>
                <EmhareStatusPill
                  :label="batch.statusLabel"
                  :tone="batch.statusTone"
                />
              </div>
              <h2
                class="mt-2 truncate text-base font-semibold text-highlighted"
              >
                {{ batch.title }}
              </h2>
              <p v-if="batch.subtitle" class="mt-1 text-sm text-muted">
                {{ batch.subtitle }}
              </p>
            </div>

            <div class="flex shrink-0 items-center gap-3">
              <slot name="batch-actions" :batch="batch" />
              <div class="text-right">
                <p class="text-2xl font-semibold tabular-nums text-highlighted">
                  {{ batch.applicants.length }}
                </p>
                <p class="text-xs text-muted">
                  applicant{{ batch.applicants.length === 1 ? "" : "s" }}
                </p>
              </div>
              <UButton
                :label="
                  batchIsOpen(batch.id) ? 'Hide applicants' : 'View applicants'
                "
                :icon="
                  batchIsOpen(batch.id)
                    ? 'i-lucide-chevron-up'
                    : 'i-lucide-chevron-down'
                "
                color="neutral"
                variant="outline"
              />
            </div>
          </div>
        </div>

        <template #content>
          <div class="bg-muted/15 p-3 sm:p-4">
            <div
              class="overflow-hidden rounded-md border border-muted bg-default"
            >
              <div
                class="hidden grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)_auto] gap-4 bg-muted/40 px-4 py-2 text-xs font-semibold uppercase tracking-wide text-muted md:grid"
              >
                <span>Applicant</span>
                <span>Programme and status</span>
                <span class="text-right">Actions</span>
              </div>
              <div
                v-for="applicant in batch.applicants"
                :key="applicant.id"
                class="grid gap-3 border-t border-muted px-4 py-3 first:border-t-0 md:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)_auto] md:items-center"
              >
                <div class="min-w-0">
                  <p class="font-mono text-xs font-semibold text-primary">
                    {{ applicant.applicationNumber }}
                  </p>
                  <p class="mt-1 truncate font-medium text-highlighted">
                    {{ applicant.applicantName || "Applicant name unavailable" }}
                  </p>
                  <p
                    v-if="applicant.applicantNumber"
                    class="text-xs text-muted"
                  >
                    Applicant {{ applicant.applicantNumber }}
                  </p>
                </div>
                <div class="min-w-0">
                  <p
                    v-if="applicant.programmeLabel"
                    class="truncate text-sm text-highlighted"
                  >
                    {{ applicant.programmeLabel }}
                  </p>
                  <p v-if="applicant.detail" class="mt-1 text-xs text-muted">
                    {{ applicant.detail }}
                  </p>
                  <EmhareStatusPill
                    class="mt-2"
                    :label="applicant.statusLabel"
                    :tone="applicant.statusTone"
                  />
                </div>
                <div class="flex flex-wrap justify-end gap-2">
                  <UButton
                    v-if="applicant.href"
                    label="View full profile"
                    icon="i-lucide-user-round-search"
                    color="neutral"
                    variant="outline"
                    :to="applicant.href"
                  />
                  <slot
                    name="applicant-actions"
                    :applicant="applicant"
                    :batch="batch"
                  />
                </div>
              </div>
            </div>
          </div>
        </template>
      </UCollapsible>
    </UCard>

    <UEmpty
      v-if="!loading && !batches.length"
      :title="emptyTitle"
      :description="emptyDescription"
    />
  </div>
</template>
