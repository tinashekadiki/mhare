<script setup lang="ts">
import type {
  AdmissionRequirementSetSummary,
  AdmissionsApplicationSummary,
  AdmissionsProgrammeChoiceSummary,
} from "@emhare/portal-shell/types/admissions";

definePageMeta({ layout: "dashboard" });

const api = useEmhareApi();
const toast = useToast();
const { confirmAction, showError } = useEmhareConfirm();
const academicPeriodContext = useAcademicPeriodContext();

const applications = ref<AdmissionsApplicationSummary[]>([]);
const requirementSets = ref<AdmissionRequirementSetSummary[]>([]);
const loading = ref(false);
const activeActionId = ref<string | null>(null);
const loadError = ref("");
const ruleModalOpen = ref(false);
const evaluationModalOpen = ref(false);
const activeEvaluationWorkspace = ref<"applications" | "requirements">(
  "applications",
);

const ruleState = reactive({
  applicationChoiceKey: "",
  versionCode: "",
  effectiveFrom: new Date().toISOString().slice(0, 10),
  effectiveTo: "",
  minimumTotalPoints: null as number | null,
  requiresEnglish: true,
  requiresMathematicsOrScience: true,
});

const evaluationState = reactive({
  applicationId: "",
  programmeChoiceId: "",
  requirementSetId: "",
  status: "ELIGIBLE",
  rankScore: null as number | null,
  missingReasonCode: "NONE",
  summary: "",
});

const reviewApplications = computed(() =>
  applications.value.filter((application) =>
    ["UNDER_REVIEW", "ELIGIBLE", "NOT_ELIGIBLE"].includes(application.status),
  ),
);

const evaluationWorkspaceTabs = computed(() => [
  {
    label: "Applications in evaluation",
    value: "applications",
    icon: "i-lucide-clipboard-check",
    badge: reviewApplications.value.length,
  },
  {
    label: "Requirement-set versions",
    value: "requirements",
    icon: "i-lucide-list-checks",
    badge: requirementSets.value.length,
  },
]);

const applicationChoiceItems = computed(() =>
  applications.value.flatMap((application) =>
    application.programmeChoices.map((choice) => ({
      label: `${application.applicationNumber} · ${choice.programmeCode} · ${choice.programmeName}`,
      value: `${application.id}:${choice.id}`,
      application,
      choice,
    })),
  ),
);

const approvedRequirementItems = computed(() =>
  requirementSets.value
    .filter((requirementSet) => requirementSet.status === "APPROVED")
    .map((requirementSet) => ({
      label: `${requirementSet.versionCode} · Effective ${formatDate(requirementSet.effectiveFrom)}`,
      value: requirementSet.id,
    })),
);

const evaluationStatusItems = [
  { label: "Eligible", value: "ELIGIBLE" },
  { label: "Conditionally eligible", value: "CONDITIONALLY_ELIGIBLE" },
  { label: "Not eligible", value: "NOT_ELIGIBLE" },
  { label: "Requires review", value: "REQUIRES_REVIEW" },
];

const missingReasonItems = [
  { label: "No missing requirement", value: "NONE" },
  { label: "Minimum points not met", value: "MINIMUM_POINTS" },
  { label: "Required subject missing", value: "SUBJECT" },
  { label: "Required pass missing", value: "PASS" },
  { label: "Required document missing", value: "DOCUMENT" },
  { label: "Manual equivalence review", value: "MANUAL_REVIEW" },
];

onMounted(loadEvaluationWorkspace);
watch(academicPeriodContext.selectedAcademicPeriodId, () => void loadEvaluationWorkspace());

async function loadEvaluationWorkspace() {
  loading.value = true;
  loadError.value = "";
  try {
    const [applicationResponse, requirementSetResponse] = await Promise.all([
      api.request<AdmissionsApplicationSummary[]>(
        "/api/admissions/applications",
      ),
      api.request<AdmissionRequirementSetSummary[]>(
        "/api/admissions/requirement-sets",
      ),
      academicPeriodContext.ensureIntakes(),
    ]);
    applications.value = applicationResponse.filter(application => (
      academicPeriodContext.matchesIntake(application.intakeId)
    ));
    requirementSets.value = requirementSetResponse.filter(requirementSet => (
      !requirementSet.intakeId
      || academicPeriodContext.matchesIntake(requirementSet.intakeId)
    ));
  } catch (error) {
    loadError.value = api.errorMessage(
      error,
      "The evaluation workspace could not be loaded.",
    );
  } finally {
    loading.value = false;
  }
}

function openRuleModal() {
  Object.assign(ruleState, {
    applicationChoiceKey: applicationChoiceItems.value[0]?.value ?? "",
    versionCode: "",
    effectiveFrom: new Date().toISOString().slice(0, 10),
    effectiveTo: "",
    minimumTotalPoints: null,
    requiresEnglish: true,
    requiresMathematicsOrScience: true,
  });
  ruleModalOpen.value = true;
}

async function createRequirementSet() {
  const context = applicationChoiceItems.value.find(
    (item) => item.value === ruleState.applicationChoiceKey,
  );
  if (!context || !ruleState.versionCode.trim() || !ruleState.effectiveFrom)
    return;
  activeActionId.value = "create-requirement-set";
  try {
    const created = await api.request<AdmissionRequirementSetSummary>(
      "/api/admissions/requirement-sets",
      {
        method: "POST",
        body: {
          programmeId: context.choice.programmeId,
          applicationTypeId: context.application.applicationTypeId,
          intakeId: context.application.intakeId,
          versionCode: ruleState.versionCode.trim(),
          effectiveFrom: ruleState.effectiveFrom,
          effectiveTo: ruleState.effectiveTo || null,
          minimumTotalPoints: ruleState.minimumTotalPoints,
          maleCutoffPoints: null,
          femaleCutoffPoints: null,
          requiresEnglish: ruleState.requiresEnglish,
          requiresMathematicsOrScience:
            ruleState.requiresMathematicsOrScience,
          advancedRules: null,
          advancedRulesVersion: null,
        },
      },
    );
    requirementSets.value = [created, ...requirementSets.value];
    ruleModalOpen.value = false;
    toast.add({
      title: "Requirement set created",
      description: `${created.versionCode} remains draft until approved.`,
      color: "success",
    });
  } catch (error) {
    await showError(
      "Requirement set could not be created",
      api.errorMessage(error),
    );
  } finally {
    activeActionId.value = null;
  }
}

async function approveRequirementSet(
  requirementSet: AdmissionRequirementSetSummary,
) {
  const confirmed = await confirmAction({
    title: "Approve this requirement set?",
    text: `${requirementSet.versionCode} becomes immutable evaluation configuration for its effective period.`,
    confirmButtonText: "Approve rules",
    icon: "question",
  });
  if (!confirmed) return;
  activeActionId.value = requirementSet.id;
  try {
    const approved = await api.request<AdmissionRequirementSetSummary>(
      `/api/admissions/requirement-sets/${requirementSet.id}/approve`,
      { method: "POST" },
    );
    await loadEvaluationWorkspace();
    toast.add({
      title: "Requirement set approved",
      description: approved.versionCode,
      color: "success",
    });
  } catch (error) {
    await showError(
      "Requirement set could not be approved",
      api.errorMessage(error),
    );
  } finally {
    activeActionId.value = null;
  }
}

function openEvaluationModal(
  application: AdmissionsApplicationSummary,
  choice: AdmissionsProgrammeChoiceSummary,
) {
  const applicableSets = requirementSets.value.filter(
    (requirementSet) =>
      requirementSet.status === "APPROVED" &&
      requirementSet.programmeId === choice.programmeId &&
      requirementSet.applicationTypeId === application.applicationTypeId &&
      (!requirementSet.intakeId ||
        requirementSet.intakeId === application.intakeId),
  );
  Object.assign(evaluationState, {
    applicationId: application.id,
    programmeChoiceId: choice.id,
    requirementSetId: applicableSets[0]?.id ?? "",
    status: "ELIGIBLE",
    rankScore: null,
    missingReasonCode: "NONE",
    summary: "",
  });
  evaluationModalOpen.value = true;
}

async function recordEvaluation() {
  if (!evaluationState.requirementSetId || !evaluationState.summary.trim())
    return;
  activeActionId.value = evaluationState.programmeChoiceId;
  const missingRequirements =
    evaluationState.missingReasonCode === "NONE"
      ? []
      : [
          {
            code: evaluationState.missingReasonCode,
            source: "MANUAL_REVIEW",
            detail: evaluationState.summary.trim(),
          },
        ];
  try {
    await api.request(
      `/api/admissions/applications/${evaluationState.applicationId}/choices/${evaluationState.programmeChoiceId}/evaluations`,
      {
        method: "POST",
        body: {
          requirementSetId: evaluationState.requirementSetId,
          status: evaluationState.status,
          rankScore: evaluationState.rankScore,
          missingRequirements,
          ruleResults: {
            manualReview: true,
            outcome: evaluationState.status,
            evidenceSource: "ADMISSIONS_OFFICER",
          },
          summary: evaluationState.summary.trim(),
        },
      },
    );
    evaluationModalOpen.value = false;
    await loadEvaluationWorkspace();
    toast.add({
      title: "Evaluation recorded",
      description: "The versioned result and evidence are preserved.",
      color: "success",
    });
  } catch (error) {
    await showError(
      "Evaluation could not be recorded",
      api.errorMessage(error),
    );
  } finally {
    activeActionId.value = null;
  }
}

function formatStatus(status: string) {
  return status
    .toLowerCase()
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en-ZW", { dateStyle: "medium" }).format(
    new Date(value),
  );
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Admissions eligibility evaluation">
        <template #leading><UDashboardSidebarCollapse /></template>
        <template #right>
          <UButton
            v-if="activeEvaluationWorkspace === 'requirements'"
            label="New requirement set"
            icon="i-lucide-list-checks"
            color="primary"
            @click="openRuleModal"
          />
          <UButton
            label="Refresh"
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="outline"
            :loading="loading"
            @click="loadEvaluationWorkspace"
          />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="space-y-6 p-4 sm:p-6">
        <UAlert
          color="info"
          variant="soft"
          icon="i-lucide-shield-check"
          title="Versioned evaluation evidence"
          description="Every outcome references an approved requirement-set version and stores points, machine-readable missing requirements, rule evidence, actor, and timestamp."
        />
        <UAlert
          v-if="loadError"
          color="error"
          variant="soft"
          title="Evaluation workspace unavailable"
          :description="loadError"
        />

        <UTabs
          v-model="activeEvaluationWorkspace"
          :items="evaluationWorkspaceTabs"
          :content="false"
          color="primary"
          variant="pill"
          aria-label="Evaluation workspace"
        />

        <section
          v-if="activeEvaluationWorkspace === 'requirements'"
          class="space-y-3"
          role="tabpanel"
          aria-labelledby="requirement-sets-heading"
        >
          <div>
            <p class="text-xs font-medium uppercase tracking-wide text-primary">
              Configuration control
            </p>
            <h2
              id="requirement-sets-heading"
              class="mt-1 text-lg font-semibold text-highlighted"
            >
              Requirement-set versions
            </h2>
          </div>
          <EmharePaginatedCollection
            v-slot="{ items: paginatedRequirementSets }"
            :items="requirementSets"
          >
            <div class="grid gap-3 lg:grid-cols-2">
              <UCard
                v-for="requirementSet in paginatedRequirementSets"
                :key="requirementSet.id"
                variant="outline"
              >
                <div class="flex items-start justify-between gap-3">
                  <div>
                    <p class="font-mono text-xs text-muted">
                      {{ requirementSet.versionCode }}
                    </p>
                    <p class="mt-1 text-sm font-medium text-highlighted">
                      Programme {{ requirementSet.programmeId }}
                    </p>
                    <p class="mt-1 text-xs text-muted">
                      Effective {{ formatDate(requirementSet.effectiveFrom) }} ·
                      Minimum points
                      {{
                        requirementSet.minimumTotalPoints ?? "Not configured"
                      }}
                    </p>
                    <p class="mt-1 text-xs text-muted">
                      English {{ requirementSet.requiresEnglish ? "required" : "not required" }} ·
                      Mathematics or Science {{ requirementSet.requiresMathematicsOrScience ? "required" : "not required" }}
                    </p>
                  </div>
                  <EmhareStatusPill
                    :label="formatStatus(requirementSet.status)"
                    :tone="
                      requirementSet.status === 'APPROVED'
                        ? 'success'
                        : 'neutral'
                    "
                  />
                </div>
                <div
                  v-if="requirementSet.status === 'DRAFT'"
                  class="mt-4 flex justify-end"
                >
                  <UButton
                    label="Approve rules"
                    icon="i-lucide-badge-check"
                    color="primary"
                    :loading="activeActionId === requirementSet.id"
                    @click="approveRequirementSet(requirementSet)"
                  />
                </div>
              </UCard>
              <UEmpty
                v-if="!requirementSets.length && !loading"
                title="No requirement sets"
                description="Create and approve a version before evaluating programme choices."
              />
            </div>
          </EmharePaginatedCollection>
        </section>

        <section
          v-if="activeEvaluationWorkspace === 'applications'"
          class="space-y-3"
          role="tabpanel"
          aria-labelledby="review-applications-heading"
        >
          <div>
            <p class="text-xs font-medium uppercase tracking-wide text-primary">
              Officer work queue
            </p>
            <h2
              id="review-applications-heading"
              class="mt-1 text-lg font-semibold text-highlighted"
            >
              Applications in evaluation
            </h2>
          </div>
          <EmharePaginatedCollection
            v-slot="{ items: paginatedReviewApplications }"
            :items="reviewApplications"
          >
            <div class="space-y-3">
              <UCard
                v-for="application in paginatedReviewApplications"
                :key="application.id"
                variant="outline"
              >
                <template #header>
                  <div class="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <p class="font-mono text-xs text-muted">
                        {{ application.applicationNumber }}
                      </p>
                      <h3 class="mt-1 font-semibold text-highlighted">
                        {{ application.applicantName }}
                      </h3>
                    </div>
                    <EmhareStatusPill
                      :label="formatStatus(application.status)"
                      tone="info"
                    />
                  </div>
                </template>
                <EmharePaginatedCollection :items="application.programmeChoices" :initial-page-size="5" v-slot="{ items: paginatedProgrammeChoices }">
                <ul class="space-y-2">
                  <li
                    v-for="choice in paginatedProgrammeChoices"
                    :key="choice.id"
                    class="flex flex-col gap-3 rounded-lg border border-muted p-3 sm:flex-row sm:items-center sm:justify-between"
                  >
                    <div>
                      <p class="font-medium text-highlighted">
                        {{ choice.choiceRank }}. {{ choice.programmeCode }} ·
                        {{ choice.programmeName }}
                      </p>
                      <p class="mt-1 text-xs text-muted">
                        {{ formatStatus(choice.choiceStatus)
                        }}<span v-if="choice.evaluationSummary">
                          · {{ choice.evaluationSummary }}</span
                        >
                      </p>
                    </div>
                    <UButton
                      v-if="
                        ['PENDING', 'REQUIRES_REVIEW'].includes(
                          choice.choiceStatus,
                        )
                      "
                      label="Record evaluation"
                      icon="i-lucide-clipboard-check"
                      color="primary"
                      variant="outline"
                      @click="openEvaluationModal(application, choice)"
                    />
                  </li>
                </ul>
                </EmharePaginatedCollection>
              </UCard>
              <UEmpty
                v-if="!reviewApplications.length && !loading"
                title="No applications awaiting evaluation"
                description="Start review from the Admissions queue after financial clearance."
              />
            </div>
          </EmharePaginatedCollection>
        </section>
      </div>
    </template>
  </UDashboardPanel>

  <EmhareRecordDrawer
    v-model:open="ruleModalOpen"
    title="Create requirement-set version"
    description="Bind immutable evaluation configuration to a Programme, application type, and intake."
  >
    <template #body>
      <form
        id="requirement-set-form"
        class="space-y-4"
        @submit.prevent="createRequirementSet"
      >
        <UFormField label="Application and programme" required
          ><USelect
            v-model="ruleState.applicationChoiceKey"
            :items="applicationChoiceItems"
            value-key="value"
            class="w-full"
        /></UFormField>
        <div class="grid gap-4 sm:grid-cols-2">
          <UFormField label="Version code" required
            ><UInput
              v-model="ruleState.versionCode"
              placeholder="2027.1"
              class="w-full"
          /></UFormField>
          <UFormField label="Minimum total points"
            ><UInput
              v-model.number="ruleState.minimumTotalPoints"
              type="number"
              min="0"
              step="0.01"
              class="w-full"
          /></UFormField>
          <UFormField label="Effective from" required
            ><UInput
              v-model="ruleState.effectiveFrom"
              type="date"
              class="w-full"
          /></UFormField>
          <UFormField label="Effective to"
            ><UInput v-model="ruleState.effectiveTo" type="date" class="w-full"
          /></UFormField>
        </div>
        <UCheckbox
          v-model="ruleState.requiresEnglish"
          label="English pass is required"
        />
        <UCheckbox
          v-model="ruleState.requiresMathematicsOrScience"
          label="Mathematics or Science subject pass is required"
        />
      </form>
    </template>
    <template #footer>
      <UButton
        label="Cancel"
        color="neutral"
        variant="outline"
        @click="ruleModalOpen = false"
      />
      <EmhareGuidedActionButton
        type="submit"
        form="requirement-set-form"
        label="Create draft"
        icon="i-lucide-plus"
        color="primary"
        :loading="activeActionId === 'create-requirement-set'"
      />
    </template>
  </EmhareRecordDrawer>

  <EmhareRecordDrawer
    v-model:open="evaluationModalOpen"
    title="Record programme eligibility"
    description="Capture the governed outcome and the evidence used to reach it."
  >
    <template #body>
      <form
        id="evaluation-form"
        class="space-y-4"
        @submit.prevent="recordEvaluation"
      >
        <UFormField label="Requirement-set version" required
          ><USelect
            v-model="evaluationState.requirementSetId"
            :items="approvedRequirementItems"
            value-key="value"
            class="w-full"
        /></UFormField>
        <div class="grid gap-4 sm:grid-cols-2">
          <UFormField label="Outcome" required
            ><USelect
              v-model="evaluationState.status"
              :items="evaluationStatusItems"
              value-key="value"
              class="w-full"
          /></UFormField>
          <UFormField label="Missing-requirement code" required
            ><USelect
              v-model="evaluationState.missingReasonCode"
              :items="missingReasonItems"
              value-key="value"
              class="w-full"
          /></UFormField>
          <UFormField label="Rank score"
            ><UInput
              v-model.number="evaluationState.rankScore"
              type="number"
              min="0"
              step="0.0001"
              class="w-full"
          /></UFormField>
        </div>
        <UAlert
          color="info"
          variant="soft"
          title="Points are calculated automatically"
          description="The evaluation uses the ZIMSEC points stored when the applicant submitted the application."
        />
        <UFormField
          label="Evaluation summary"
          description="State the evidence and policy basis. This is retained in audit history."
          required
        >
          <UTextarea
            v-model="evaluationState.summary"
            :rows="4"
            maxlength="1000"
            class="w-full"
          />
        </UFormField>
        <UAlert
          v-if="!evaluationState.requirementSetId"
          color="warning"
          variant="soft"
          title="No approved rule version"
          description="Create and approve an applicable requirement set before recording an outcome."
        />
      </form>
    </template>
    <template #footer>
      <UButton
        label="Cancel"
        color="neutral"
        variant="outline"
        @click="evaluationModalOpen = false"
      />
      <UButton
        type="submit"
        form="evaluation-form"
        label="Record evaluation"
        icon="i-lucide-clipboard-check"
        color="primary"
        guidance-title="Evaluation evidence is incomplete"
        :guidance-instructions="[...(!evaluationState.requirementSetId ? ['Select the approved admission requirement set.'] : []), ...(!evaluationState.summary.trim() ? ['Record the evaluation summary and supporting evidence.'] : [])]"
        :loading="activeActionId === evaluationState.programmeChoiceId"
      />
    </template>
  </EmhareRecordDrawer>
</template>
