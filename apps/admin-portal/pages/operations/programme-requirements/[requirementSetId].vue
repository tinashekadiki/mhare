<script setup lang="ts">
import type {
  AdmissionRequirementSetSummary,
  AdmissionsApplicationTypeSummary,
} from "@emhare/portal-shell/types/admissions";

definePageMeta({ layout: "dashboard" });

type SubjectReferenceOption = {
  id: string;
  code: string;
  name: string;
  active: boolean;
};

const api = useEmhareApi();
const route = useRoute();
const academicSetup = useAcademicSetup();
const loading = ref(false);
const loadError = ref("");
const requirementSet = ref<AdmissionRequirementSetSummary | null>(null);
const applicationTypes = ref<AdmissionsApplicationTypeSummary[]>([]);
const subjects = ref<SubjectReferenceOption[]>([]);

const requirementSetId = computed(() => {
  const value = route.params.requirementSetId;
  return Array.isArray(value) ? (value[0] ?? "") : (value ?? "");
});
const createVersionLink = computed(() => {
  if (!requirementSet.value) return "/operations/programme-requirements";
  const query = new URLSearchParams({
    create: "1",
    programmeId: requirementSet.value.programmeId,
    applicationTypeId: requirementSet.value.applicationTypeId,
  });
  if (requirementSet.value.intakeId) query.set("intakeId", requirementSet.value.intakeId);
  return `/operations/programme-requirements?${query.toString()}`;
});

onMounted(loadRequirementSet);

async function loadRequirementSet() {
  loading.value = true;
  loadError.value = "";
  try {
    const [, loadedApplicationTypes, loadedRequirementSets, loadedSubjects] = await Promise.all([
      academicSetup.ensureOverview(),
      api.request<AdmissionsApplicationTypeSummary[]>("/api/admissions/application-types"),
      api.request<AdmissionRequirementSetSummary[]>("/api/admissions/requirement-sets"),
      api.request<{
        oLevelSubjects: SubjectReferenceOption[];
        aLevelSubjects: SubjectReferenceOption[];
      }>("/api/admissions/qualification-reference-data/manage"),
    ]);
    applicationTypes.value = loadedApplicationTypes;
    subjects.value = [...loadedSubjects.oLevelSubjects, ...loadedSubjects.aLevelSubjects];
    requirementSet.value =
      loadedRequirementSets.find((item) => item.id === requirementSetId.value) ?? null;
    if (!requirementSet.value) loadError.value = "The programme requirement could not be found.";
  } catch (error) {
    loadError.value = api.errorMessage(error, "Programme requirement details could not be loaded.");
  } finally {
    loading.value = false;
  }
}

function programmeLabel(programmeId: string) {
  const programme = academicSetup.overview.value?.programmes.find(
    (item) => item.id === programmeId,
  );
  return programme ? `${programme.code} · ${programme.name}` : programmeId;
}

function applicationTypeLabel(applicationTypeId: string) {
  const applicationType = applicationTypes.value.find((item) => item.id === applicationTypeId);
  return applicationType ? `${applicationType.code} · ${applicationType.name}` : applicationTypeId;
}

function intakeLabel(intakeId: string | null) {
  if (!intakeId) return "All intakes";
  const intake = academicSetup.overview.value?.intakes.find((item) => item.id === intakeId);
  return intake ? `${intake.code} · ${intake.name}` : intakeId;
}

function subjectLabel(subjectId: string | null, subjectGroupCode: string | null) {
  const subject = subjects.value.find((item) => item.id === subjectId);
  if (subject) return `${subject.code} · ${subject.name}`;
  return subjectGroupCode || "Any subject in the configured rule";
}

function formatLabel(value: string) {
  return value
    .toLowerCase()
    .replaceAll("_", " ")
    .replace(/(^|\s)\S/g, (character) => character.toUpperCase());
}

function formatDate(value: string | null) {
  if (!value) return "No end date";
  return new Intl.DateTimeFormat("en-ZW", { dateStyle: "medium" }).format(new Date(value));
}
</script>

<template>
  <UDashboardPanel data-testid="programme-requirement-detail">
    <template #header>
      <UDashboardNavbar title="Programme requirement details">
        <template #leading><UDashboardSidebarCollapse /></template>
        <template #right>
          <UButton
            label="Back to requirements"
            icon="i-lucide-arrow-left"
            color="neutral"
            variant="outline"
            to="/operations/programme-requirements"
          />
          <UButton
            v-if="requirementSet"
            label="Add new version"
            icon="i-lucide-plus"
            color="primary"
            :to="createVersionLink"
          />
          <UButton
            label="Refresh"
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="outline"
            :loading="loading"
            @click="loadRequirementSet"
          />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="space-y-5 p-4 sm:p-6">
        <UAlert
          v-if="loadError"
          color="error"
          variant="soft"
          title="Programme requirement unavailable"
          :description="loadError"
        />
        <template v-else-if="requirementSet">
          <UCard>
            <div class="flex flex-wrap items-start justify-between gap-4">
              <div>
                <p class="font-mono text-xs text-muted">{{ requirementSet.versionCode }}</p>
                <h1 class="mt-1 text-xl font-semibold text-highlighted">
                  {{ programmeLabel(requirementSet.programmeId) }}
                </h1>
                <p class="mt-1 text-sm text-muted">
                  {{ applicationTypeLabel(requirementSet.applicationTypeId) }}
                </p>
              </div>
              <EmhareStatusPill
                :label="formatLabel(requirementSet.status)"
                :tone="
                  requirementSet.status === 'APPROVED'
                    ? 'success'
                    : requirementSet.status === 'RETIRED'
                      ? 'neutral'
                      : 'warning'
                "
              />
            </div>
          </UCard>

          <section class="grid gap-4 xl:grid-cols-2">
            <UCard>
              <template #header><h2 class="font-semibold">Scope and effective period</h2></template>
              <dl class="grid gap-4 text-sm sm:grid-cols-2">
                <div>
                  <dt class="text-muted">Programme</dt>
                  <dd class="mt-1 font-medium">{{ programmeLabel(requirementSet.programmeId) }}</dd>
                </div>
                <div>
                  <dt class="text-muted">Application type</dt>
                  <dd class="mt-1 font-medium">
                    {{ applicationTypeLabel(requirementSet.applicationTypeId) }}
                  </dd>
                </div>
                <div>
                  <dt class="text-muted">Intake scope</dt>
                  <dd class="mt-1 font-medium">{{ intakeLabel(requirementSet.intakeId) }}</dd>
                </div>
                <div>
                  <dt class="text-muted">Effective dates</dt>
                  <dd class="mt-1 font-medium">
                    {{ formatDate(requirementSet.effectiveFrom) }} to
                    {{ formatDate(requirementSet.effectiveTo) }}
                  </dd>
                </div>
              </dl>
            </UCard>

            <UCard>
              <template #header><h2 class="font-semibold">Baseline requirements</h2></template>
              <dl class="grid gap-4 text-sm sm:grid-cols-2">
                <div>
                  <dt class="text-muted">Minimum total points</dt>
                  <dd class="mt-1 font-medium">
                    {{ requirementSet.minimumTotalPoints ?? "Not set" }}
                  </dd>
                </div>
                <div>
                  <dt class="text-muted">Male cutoff points</dt>
                  <dd class="mt-1 font-medium">
                    {{ requirementSet.maleCutoffPoints ?? "Not set" }}
                  </dd>
                </div>
                <div>
                  <dt class="text-muted">Female cutoff points</dt>
                  <dd class="mt-1 font-medium">
                    {{ requirementSet.femaleCutoffPoints ?? "Not set" }}
                  </dd>
                </div>
                <div>
                  <dt class="text-muted">English</dt>
                  <dd class="mt-1 font-medium">
                    {{ requirementSet.requiresEnglish ? "Required" : "Not required" }}
                  </dd>
                </div>
                <div>
                  <dt class="text-muted">Mathematics</dt>
                  <dd class="mt-1 font-medium">
                    {{ requirementSet.requiresMathematics ? "Required" : "Not required" }}
                  </dd>
                </div>
                <div>
                  <dt class="text-muted">Science</dt>
                  <dd class="mt-1 font-medium">
                    {{ requirementSet.requiresScience ? "Required" : "Not required" }}
                  </dd>
                </div>
                <div v-if="requirementSet.requiresMathematicsOrScience">
                  <dt class="text-muted">Historical combined rule</dt>
                  <dd class="mt-1 font-medium">Mathematics or Science required</dd>
                </div>
              </dl>
            </UCard>
          </section>

          <UCard>
            <template #header>
              <div>
                <h2 class="font-semibold">Subject requirements</h2>
                <p class="mt-1 text-sm text-muted">
                  Exact O Level and A Level subject rules in evaluation order.
                </p>
              </div>
            </template>
            <div v-if="requirementSet.subjectRequirements?.length" class="space-y-3">
              <div
                v-for="rule in requirementSet.subjectRequirements"
                :key="rule.id"
                class="grid gap-3 rounded-lg border border-muted p-4 text-sm md:grid-cols-4"
              >
                <div>
                  <p class="text-muted">Level</p>
                  <p class="mt-1 font-medium">{{ formatLabel(rule.level) }}</p>
                </div>
                <div>
                  <p class="text-muted">Subject</p>
                  <p class="mt-1 font-medium">
                    {{ subjectLabel(rule.subjectId, rule.subjectGroupCode) }}
                  </p>
                </div>
                <div>
                  <p class="text-muted">Rule</p>
                  <p class="mt-1 font-medium">{{ formatLabel(rule.requirementType) }}</p>
                </div>
                <div>
                  <p class="text-muted">Threshold</p>
                  <p class="mt-1 font-medium">
                    Grade {{ rule.minimumGrade ?? "Any" }} · Points
                    {{ rule.minimumPoints ?? "Any" }} · Count {{ rule.minimumCount ?? "Any" }}
                  </p>
                </div>
              </div>
            </div>
            <UAlert
              v-else
              color="neutral"
              variant="soft"
              title="No exact subject rules"
              description="Eligibility uses the baseline and qualification-route requirements only."
            />
          </UCard>

          <UCard>
            <template #header>
              <div>
                <h2 class="font-semibold">Alternative qualification routes</h2>
                <p class="mt-1 text-sm text-muted">
                  Each group states how many of its configured routes an applicant must satisfy.
                </p>
              </div>
            </template>
            <div v-if="requirementSet.qualificationGroups?.length" class="space-y-4">
              <section
                v-for="group in requirementSet.qualificationGroups"
                :key="group.id"
                class="rounded-lg border border-muted p-4"
              >
                <div class="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p class="font-mono text-xs text-muted">{{ group.code }}</p>
                    <h3 class="mt-1 font-semibold">{{ group.name }}</h3>
                  </div>
                  <span class="text-sm text-muted"
                    >Satisfy {{ group.minimumSatisfiedItems }} of {{ group.items.length }}</span
                  >
                </div>
                <div class="mt-3 grid gap-3 md:grid-cols-2">
                  <div
                    v-for="item in group.items"
                    :key="item.id"
                    class="rounded-md bg-elevated p-3 text-sm"
                  >
                    <p class="font-medium">{{ formatLabel(item.qualificationLevel) }}</p>
                    <p class="mt-1 text-muted">
                      Minimum count {{ item.minimumCount }} · Points
                      {{ item.minimumTotalPoints ?? "Any" }} · Duration
                      {{
                        item.minimumDurationMonths ? `${item.minimumDurationMonths} months` : "Any"
                      }}
                    </p>
                  </div>
                </div>
              </section>
            </div>
            <UAlert
              v-else
              color="neutral"
              variant="soft"
              title="No alternative qualification routes"
              description="No diploma, degree, professional, or other alternative route is configured."
            />
          </UCard>

          <UCard>
            <template #header><h2 class="font-semibold">Governance</h2></template>
            <dl class="grid gap-4 text-sm sm:grid-cols-3">
              <div>
                <dt class="text-muted">Version</dt>
                <dd class="mt-1 font-medium">{{ requirementSet.versionCode }}</dd>
              </div>
              <div>
                <dt class="text-muted">Approved at</dt>
                <dd class="mt-1 font-medium">
                  {{
                    requirementSet.approvedAt
                      ? formatDate(requirementSet.approvedAt)
                      : "Not approved"
                  }}
                </dd>
              </div>
              <div>
                <dt class="text-muted">Advanced rules version</dt>
                <dd class="mt-1 font-medium">
                  {{ requirementSet.advancedRulesVersion ?? "Not configured" }}
                </dd>
              </div>
            </dl>
          </UCard>
        </template>
        <div v-else class="space-y-3">
          <USkeleton class="h-32 rounded-xl" /><USkeleton class="h-64 rounded-xl" />
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>
