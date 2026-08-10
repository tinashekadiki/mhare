<script setup lang="ts">
import type {
  AcademicReviewBatchPreview,
  AcademicReviewSummary,
  AdmissionsWorkflowBatchView,
  SelectionRoundSummary,
} from "@emhare/portal-shell/types/admissions";

definePageMeta({ layout: "dashboard" });

type BatchMode = "PROGRAMME" | "ACADEMIC_UNIT";

const api = useEmhareApi();
const toast = useToast();
const { confirmAction, showError } = useEmhareConfirm();
const rounds = ref<SelectionRoundSummary[]>([]);
const assignments = ref<AcademicReviewSummary[]>([]);
const preview = ref<AcademicReviewBatchPreview>({
  totalApplicants: 0,
  totalEligibleApplicants: 0,
  programmes: [],
  academicUnits: [],
});
const selectedRoundId = ref("");
const batchMode = ref<BatchMode>("PROGRAMME");
const selectedBatchId = ref("");
const dueAt = ref("");
const loading = ref(false);
const releasing = ref(false);
const loadError = ref("");
const previewError = ref("");

const openRoundItems = computed(() =>
  rounds.value
    .filter((round) => round.status === "OPEN")
    .map((round) => ({
      label: `${round.code} · ${round.name}`,
      value: round.id,
    })),
);

const batchModeItems = [
  {
    label: "Programme",
    value: "PROGRAMME",
    description: "Release applicants for one programme.",
  },
  {
    label: "Academic unit",
    value: "ACADEMIC_UNIT",
    description:
      "Release by a department, school, college, or other configured unit.",
  },
];

const batchItems = computed(() =>
  batchMode.value === "PROGRAMME"
    ? preview.value.programmes.map((programme) => ({
        label: `${programme.programmeCode} · ${programme.programmeName} (${programme.eligibleApplicantCount} of ${programme.applicantCount} ready)`,
        value: programme.programmeId,
      }))
    : preview.value.academicUnits.map((unit) => ({
        label: `${unit.academicUnitCode} · ${unit.academicUnitName} (${unit.eligibleApplicantCount} of ${unit.applicantCount} ready)`,
        value: unit.academicUnitId,
      })),
);

const selectedBatch = computed(() =>
  batchMode.value === "PROGRAMME"
    ? preview.value.programmes.find(
        (programme) => programme.programmeId === selectedBatchId.value,
      )
    : preview.value.academicUnits.find(
        (unit) => unit.academicUnitId === selectedBatchId.value,
      ),
);

const selectedApplicantCount = computed(
  () => selectedBatch.value?.eligibleApplicantCount ?? 0,
);
const visibleAssignments = computed(() =>
  assignments.value.filter(
    (assignment) => assignment.selectionRoundId === selectedRoundId.value,
  ),
);
const releasedBatches = computed<AdmissionsWorkflowBatchView[]>(() => {
  const grouped = new Map<string, AcademicReviewSummary[]>();
  for (const assignment of visibleAssignments.value) {
    const key = `${assignment.selectionRoundId}:${assignment.recommendationAcademicUnitId}:${assignment.programmeCode}`;
    grouped.set(key, [...(grouped.get(key) ?? []), assignment]);
  }
  return Array.from(grouped.entries()).map(([id, batchAssignments]) => {
    const first = batchAssignments[0]!;
    const completed = batchAssignments.filter(
      (assignment) => assignment.status === "COMPLETED",
    ).length;
    return {
      id,
      code: first.programmeCode,
      title: first.programmeName,
      subtitle: `${first.owningAcademicUnitName} → ${first.recommendationAcademicUnitName}`,
      stageLabel: "2 · Release",
      statusLabel: completed
        ? `${completed}/${batchAssignments.length} completed`
        : "Released",
      statusTone: completed === batchAssignments.length ? "success" : "primary",
      applicants: batchAssignments.map((assignment) => ({
        id: assignment.id,
        applicationNumber: assignment.applicationNumber,
        applicantNumber: assignment.applicantNumber,
        applicantName: assignment.applicantName,
        programmeLabel: `${assignment.programmeCode} · ${assignment.programmeName}`,
        detail: `Choice ${assignment.choiceRank} · ${assignment.recommendationAcademicUnitName}`,
        statusLabel: formatStatus(assignment.status),
        statusTone: assignmentStatusTone(assignment.status),
        href: `/operations/admissions/${assignment.applicationId}`,
      })),
    };
  });
});

onMounted(loadWorkspace);
watch(selectedRoundId, () => {
  if (!loading.value) void loadPreview();
});
watch(batchMode, selectFirstAvailableBatch);

async function loadWorkspace() {
  loading.value = true;
  loadError.value = "";
  try {
    const [roundResponse, assignmentResponse] = await Promise.all([
      api.request<SelectionRoundSummary[]>("/api/admissions/selection-rounds"),
      api.request<AcademicReviewSummary[]>("/api/admissions/academic-reviews"),
    ]);
    rounds.value = roundResponse;
    assignments.value = assignmentResponse;
    if (
      !openRoundItems.value.some(
        (round) => round.value === selectedRoundId.value,
      )
    ) {
      selectedRoundId.value = openRoundItems.value[0]?.value ?? "";
    }
    await loadPreview();
  } catch (error) {
    loadError.value = api.errorMessage(
      error,
      "The release workspace could not be loaded.",
    );
  } finally {
    loading.value = false;
  }
}

async function loadPreview() {
  previewError.value = "";
  if (!selectedRoundId.value) {
    preview.value = {
      totalApplicants: 0,
      totalEligibleApplicants: 0,
      programmes: [],
      academicUnits: [],
    };
    selectedBatchId.value = "";
    return;
  }
  try {
    preview.value = await api.request<AcademicReviewBatchPreview>(
      `/api/admissions/academic-reviews/selection-rounds/${selectedRoundId.value}/release-preview`,
    );
    selectFirstAvailableBatch();
  } catch (error) {
    preview.value = {
      totalApplicants: 0,
      totalEligibleApplicants: 0,
      programmes: [],
      academicUnits: [],
    };
    previewError.value = api.errorMessage(
      error,
      "Batch counts are temporarily unavailable. Retry after the Admissions service is updated.",
    );
  }
}

function selectFirstAvailableBatch() {
  if (
    !batchItems.value.some((batch) => batch.value === selectedBatchId.value)
  ) {
    selectedBatchId.value = batchItems.value[0]?.value ?? "";
  }
}

async function releaseBatch() {
  if (
    !selectedRoundId.value ||
    !selectedBatchId.value ||
    !selectedApplicantCount.value
  )
    return;
  const batchLabel =
    batchItems.value.find((batch) => batch.value === selectedBatchId.value)
      ?.label ?? "selected batch";
  const confirmed = await confirmAction({
    title: `Release ${selectedApplicantCount.value} applicant${selectedApplicantCount.value === 1 ? "" : "s"}?`,
    text: `${batchLabel} will be assigned to the correct highest academic unit for recommendation.`,
    confirmButtonText: "Release batch",
    icon: "question",
  });
  if (!confirmed) return;
  releasing.value = true;
  try {
    const created = await api.request<AcademicReviewSummary[]>(
      `/api/admissions/academic-reviews/selection-rounds/${selectedRoundId.value}/releases`,
      {
        method: "POST",
        body: {
          programmeId:
            batchMode.value === "PROGRAMME" ? selectedBatchId.value : null,
          academicUnitId:
            batchMode.value === "ACADEMIC_UNIT" ? selectedBatchId.value : null,
          dueAt: dueAt.value ? new Date(dueAt.value).toISOString() : null,
        },
      },
    );
    toast.add({
      title: "Batch released",
      description: `${created.length} applicant${created.length === 1 ? "" : "s"} moved to academic-unit recommendation.`,
      color: "success",
      icon: "i-lucide-check-circle",
    });
    await loadWorkspace();
  } catch (error) {
    await showError("Batch could not be released", api.errorMessage(error));
  } finally {
    releasing.value = false;
  }
}

function formatStatus(value: string) {
  return value
    .toLowerCase()
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}

function assignmentStatusTone(status: AcademicReviewSummary["status"]) {
  if (status === "COMPLETED") return "success" as const;
  if (status === "RETURNED") return "warning" as const;
  if (status === "CANCELLED") return "error" as const;
  return "primary" as const;
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Release applicant batches">
        <template #leading><UDashboardSidebarCollapse /></template>
        <template #right>
          <UButton
            label="Refresh"
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="outline"
            :loading="loading"
            @click="loadWorkspace"
          />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="space-y-5 p-4 sm:p-6">
        <EmhareAdmissionsWorkflowNav current-stage="release" />

        <UAlert
          v-if="loadError"
          color="error"
          variant="soft"
          icon="i-lucide-circle-alert"
          title="Release stage unavailable"
          :description="loadError"
        />

        <UAlert
          color="info"
          variant="soft"
          icon="i-lucide-layers-3"
          title="Batch applicants in two steps"
          description="Choose a programme or academic unit, check the applicant count, then release the batch. Already released choices are excluded automatically."
        />

        <UCard variant="outline">
          <template #header>
            <div>
              <p
                class="text-xs font-semibold uppercase tracking-wide text-primary"
              >
                Step 1
              </p>
              <h2 class="mt-1 text-lg font-semibold text-highlighted">
                Choose the applicant batch
              </h2>
            </div>
          </template>
          <div class="grid gap-4 lg:grid-cols-3">
            <UFormField label="Selection round" required>
              <USelect
                v-model="selectedRoundId"
                :items="openRoundItems"
                value-key="value"
                class="w-full"
              />
            </UFormField>
            <UFormField label="Batch applicants by" required>
              <URadioGroup
                v-model="batchMode"
                :items="batchModeItems"
                value-key="value"
                orientation="horizontal"
              />
            </UFormField>
            <UFormField
              :label="
                batchMode === 'PROGRAMME'
                  ? 'Programme'
                  : 'Department or academic unit'
              "
              required
            >
              <USelectMenu
                v-model="selectedBatchId"
                :items="batchItems"
                value-key="value"
                searchable
                class="w-full"
              />
            </UFormField>
          </div>
          <UEmpty
            v-if="
              selectedRoundId && !preview.totalApplicants && !loading
            "
            class="mt-4"
            title="No applicants have programme choices in this round"
            description="Choose another open selection round or complete applicant programme choices first."
          />
          <UAlert
            v-else-if="
              selectedRoundId && !preview.totalEligibleApplicants && !loading
            "
            class="mt-4"
            color="warning"
            variant="soft"
            icon="i-lucide-clock-3"
            :title="`${preview.totalApplicants} applicant${preview.totalApplicants === 1 ? '' : 's'} found; none ready for release`"
            description="The programmes and academic units remain available for batching. Complete Admissions confirmation and eligibility evaluation before releasing applicants."
          >
            <template #actions>
              <UButton
                label="Open confirmation"
                color="warning"
                variant="outline"
                size="sm"
                to="/operations/admissions"
              />
            </template>
          </UAlert>
          <UAlert
            v-if="previewError"
            class="mt-4"
            color="warning"
            variant="soft"
            icon="i-lucide-triangle-alert"
            title="Batch preview unavailable"
            :description="previewError"
          />
        </UCard>

        <UCard variant="outline">
          <template #header>
            <div>
              <p
                class="text-xs font-semibold uppercase tracking-wide text-primary"
              >
                Step 2
              </p>
              <h2 class="mt-1 text-lg font-semibold text-highlighted">
                Review and release
              </h2>
            </div>
          </template>
          <div
            class="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between"
          >
            <div>
              <p class="text-3xl font-semibold text-highlighted">
                {{ selectedApplicantCount }}
              </p>
              <p class="mt-1 text-sm text-muted">
                applicant{{ selectedApplicantCount === 1 ? "" : "s" }} ready
                to release
              </p>
              <p v-if="selectedBatch" class="mt-1 text-sm text-muted">
                {{ selectedBatch.applicantCount }} applicant{{
                  selectedBatch.applicantCount === 1 ? "" : "s"
                }} in this batch
              </p>
              <p
                v-if="selectedBatch"
                class="mt-2 text-sm font-medium text-highlighted"
              >
                {{
                  "programmeCode" in selectedBatch
                    ? `${selectedBatch.programmeCode} · ${selectedBatch.programmeName}`
                    : `${selectedBatch.academicUnitCode} · ${selectedBatch.academicUnitName}`
                }}
              </p>
            </div>
            <div
              class="flex w-full flex-col gap-3 sm:flex-row lg:w-auto lg:items-end"
            >
              <UFormField label="Recommendation due date" class="sm:min-w-64">
                <UInput v-model="dueAt" type="datetime-local" class="w-full" />
              </UFormField>
              <UButton
                :label="`Release ${selectedApplicantCount} applicant${selectedApplicantCount === 1 ? '' : 's'}`"
                icon="i-lucide-send"
                color="primary"
                size="lg"
                :loading="releasing"
                :disabled="!selectedApplicantCount"
                @click="releaseBatch"
              />
            </div>
          </div>
        </UCard>

        <section class="space-y-3" aria-labelledby="release-register-heading">
          <div class="flex items-center justify-between">
            <h2
              id="release-register-heading"
              class="text-lg font-semibold text-highlighted"
            >
              Released applicants
            </h2>
            <UButton
              label="Open recommendation queue"
              icon="i-lucide-arrow-right"
              trailing
              color="neutral"
              variant="outline"
              to="/operations/admissions-recommendations"
            />
          </div>
          <EmhareAdmissionsBatchList
            :batches="releasedBatches"
            :loading="loading"
            empty-title="No released batches"
            empty-description="Choose a batch above to release applicants for recommendation."
          />
        </section>
      </div>
    </template>
  </UDashboardPanel>
</template>
