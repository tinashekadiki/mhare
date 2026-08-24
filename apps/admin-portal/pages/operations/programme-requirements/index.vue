<script setup lang="ts">
import type {
  AdmissionsApplicationTypeSummary,
  AdmissionRequirementSetSummary,
} from "@emhare/portal-shell/types/admissions";

definePageMeta({ layout: "dashboard" });

type QualificationLevel = "O_LEVEL" | "A_LEVEL" | "DIPLOMA" | "DEGREE" | "PROFESSIONAL" | "OTHER";
type QualificationRequirementItem = {
  qualificationLevel: QualificationLevel;
  minimumCount: number;
  minimumTotalPoints: number | null;
  minimumDurationMonths: number | null;
  sortOrder: number;
};
type QualificationRequirementGroup = {
  code: string;
  name: string;
  minimumSatisfiedItems: number;
  sortOrder: number;
  items: QualificationRequirementItem[];
};
type SubjectReferenceOption = {
  id: string;
  code: string;
  name: string;
  subjectGroupCode: string | null;
  active: boolean;
};
type SubjectRequirement = {
  level: "O_LEVEL" | "A_LEVEL";
  subjectId: string;
  subjectGroupCode: string;
  requirementType: "COMPULSORY" | "ONE_OF" | "ANY_OF" | "EXCLUDED" | "WEIGHTED";
  minimumGrade: string;
  minimumPoints: number | null;
  minimumCount: number | null;
  weight: number | null;
  sortOrder: number;
};
type ProgrammeRequirementSet = AdmissionRequirementSetSummary & {
  qualificationGroups?: Array<QualificationRequirementGroup & { id?: string }>;
};

const api = useEmhareApi();
const auth = useEmhareAuth();
const route = useRoute();
const toast = useToast();
const { confirmAction, showError } = useEmhareConfirm();
const academicSetup = useAcademicSetup();
const academicPeriodContext = useAcademicPeriodContext();

const applicationTypes = ref<AdmissionsApplicationTypeSummary[]>([]);
const requirementSets = ref<ProgrammeRequirementSet[]>([]);
const qualificationSubjects = ref<{
  oLevelSubjects: SubjectReferenceOption[];
  aLevelSubjects: SubjectReferenceOption[];
}>({ oLevelSubjects: [], aLevelSubjects: [] });
const loading = ref(false);
const saving = ref(false);
const activeRequirementSetId = ref<string | null>(null);
const loadError = ref("");
const creating = ref(false);

const requirementForm = reactive({
  programmeId: "",
  applicationTypeId: "",
  intakeId: "",
  allIntakes: true,
  versionCode: "",
  effectiveFrom: new Date().toISOString().slice(0, 10),
  effectiveTo: "",
  minimumTotalPoints: null as number | null,
  maleCutoffPoints: null as number | null,
  femaleCutoffPoints: null as number | null,
  requiresEnglish: true,
  requiresMathematics: true,
  requiresScience: false,
  subjectRequirements: [] as SubjectRequirement[],
  qualificationGroups: [] as QualificationRequirementGroup[],
});

const canManageRequirements = computed(() => auth.hasPermission("ADMISSIONS_SETUP_MANAGE"));
const programmes = computed(() =>
  (academicSetup.overview.value?.programmes ?? []).filter(
    (programme) => programme.status === "ACTIVE",
  ),
);
const intakes = computed(() =>
  (academicSetup.overview.value?.intakes ?? []).filter((intake) =>
    academicPeriodContext.matchesIntake(intake.id),
  ),
);
const programmeItems = computed(() =>
  programmes.value.map((programme) => ({
    label: `${programme.code} · ${programme.name}`,
    value: programme.id,
  })),
);
const applicationTypeItems = computed(() =>
  applicationTypes.value
    .filter((applicationType) => applicationType.active)
    .map((applicationType) => ({
      label: `${applicationType.code} · ${applicationType.name}`,
      value: applicationType.id,
    })),
);
const intakeItems = computed(() =>
  intakes.value.map((intake) => ({
    label: `${intake.code} · ${intake.name}`,
    value: intake.id,
  })),
);
const qualificationLevelItems = [
  { label: "O Level", value: "O_LEVEL" },
  { label: "A Level", value: "A_LEVEL" },
  { label: "Diploma", value: "DIPLOMA" },
  { label: "Degree", value: "DEGREE" },
  { label: "Professional qualification", value: "PROFESSIONAL" },
  { label: "Other qualification", value: "OTHER" },
];
const subjectRequirementTypeItems = [
  { label: "Compulsory", value: "COMPULSORY" },
  { label: "One of group", value: "ONE_OF" },
  { label: "Any of group", value: "ANY_OF" },
  { label: "Excluded", value: "EXCLUDED" },
  { label: "Weighted", value: "WEIGHTED" },
];
const filteredRequirementSets = computed(() =>
  requirementSets.value.filter(
    (requirementSet) =>
      !requirementSet.intakeId || academicPeriodContext.matchesIntake(requirementSet.intakeId),
  ),
);
const formGuidance = computed(() => [
  ...(!requirementForm.programmeId ? ["Select the Programme these requirements govern."] : []),
  ...(!requirementForm.applicationTypeId ? ["Select the application type."] : []),
  ...(!requirementForm.allIntakes && !requirementForm.intakeId
    ? ["Select an intake or apply the requirement to all intakes."]
    : []),
  ...(!requirementForm.versionCode.trim() ? ["Enter a version code."] : []),
  ...(!requirementForm.effectiveFrom ? ["Select the effective-from date."] : []),
  ...requirementForm.subjectRequirements.flatMap((requirement, index) => [
    ...(!requirement.subjectId && !requirement.subjectGroupCode.trim()
      ? [`Select a subject or enter a subject group for requirement ${index + 1}.`]
      : []),
    ...(["ONE_OF", "ANY_OF"].includes(requirement.requirementType) &&
    !requirement.subjectGroupCode.trim()
      ? [`Enter a group code for subject requirement ${index + 1}.`]
      : []),
  ]),
  ...requirementForm.qualificationGroups.flatMap((group, groupIndex) => [
    ...(!group.code.trim() ? [`Enter a code for qualification group ${groupIndex + 1}.`] : []),
    ...(!group.name.trim() ? [`Enter a name for qualification group ${groupIndex + 1}.`] : []),
    ...(!group.items.length
      ? [`Add at least one route to qualification group ${groupIndex + 1}.`]
      : []),
    ...(group.minimumSatisfiedItems > group.items.length
      ? [`Reduce the minimum routes for qualification group ${groupIndex + 1}.`]
      : []),
  ]),
]);

onMounted(async () => {
  await loadRequirements();
  openRequestedCreation();
});
watch(academicPeriodContext.selectedAcademicPeriodId, () => void loadRequirements());

function openRequestedCreation() {
  if (queryValue(route.query.create) === "1") startCreation();
}

async function loadRequirements() {
  loading.value = true;
  loadError.value = "";
  try {
    const [, loadedApplicationTypes, loadedRequirementSets, loadedSubjects] = await Promise.all([
      academicSetup.ensureOverview(),
      api.request<AdmissionsApplicationTypeSummary[]>("/api/admissions/application-types"),
      api.request<ProgrammeRequirementSet[]>("/api/admissions/requirement-sets"),
      api.request<{
        oLevelSubjects: SubjectReferenceOption[];
        aLevelSubjects: SubjectReferenceOption[];
      }>("/api/admissions/qualification-reference-data/manage"),
    ]);
    applicationTypes.value = loadedApplicationTypes;
    requirementSets.value = loadedRequirementSets;
    qualificationSubjects.value = loadedSubjects;
  } catch (error) {
    loadError.value = api.errorMessage(error, "Programme requirements could not be loaded.");
  } finally {
    loading.value = false;
  }
}

function queryValue(value: string | null | Array<string | null> | undefined) {
  return Array.isArray(value) ? (value.find((item) => Boolean(item)) ?? "") : (value ?? "");
}

function startCreation() {
  const requestedIntakeId = queryValue(route.query.intakeId);
  Object.assign(requirementForm, {
    programmeId: queryValue(route.query.programmeId) || programmeItems.value[0]?.value || "",
    applicationTypeId:
      queryValue(route.query.applicationTypeId) || applicationTypeItems.value[0]?.value || "",
    intakeId: requestedIntakeId,
    allIntakes: !requestedIntakeId,
    versionCode: "",
    effectiveFrom: new Date().toISOString().slice(0, 10),
    effectiveTo: "",
    minimumTotalPoints: null,
    maleCutoffPoints: null,
    femaleCutoffPoints: null,
    requiresEnglish: true,
    requiresMathematics: true,
    requiresScience: false,
    subjectRequirements: [],
    qualificationGroups: [],
  });
  creating.value = true;
}

function addSubjectRequirement() {
  requirementForm.subjectRequirements.push({
    level: "A_LEVEL",
    subjectId: "",
    subjectGroupCode: "",
    requirementType: "COMPULSORY",
    minimumGrade: "",
    minimumPoints: null,
    minimumCount: 1,
    weight: null,
    sortOrder: requirementForm.subjectRequirements.length + 1,
  });
}

function subjectItems(level: "O_LEVEL" | "A_LEVEL") {
  const subjects =
    level === "O_LEVEL"
      ? qualificationSubjects.value.oLevelSubjects
      : qualificationSubjects.value.aLevelSubjects;
  return subjects
    .filter((subject) => subject.active)
    .map((subject) => ({ label: `${subject.code} · ${subject.name}`, value: subject.id }));
}

function addQualificationGroup() {
  requirementForm.qualificationGroups.push({
    code: `ROUTE_${requirementForm.qualificationGroups.length + 1}`,
    name: "",
    minimumSatisfiedItems: 1,
    sortOrder: requirementForm.qualificationGroups.length + 1,
    items: [
      {
        qualificationLevel: "A_LEVEL",
        minimumCount: 1,
        minimumTotalPoints: null,
        minimumDurationMonths: null,
        sortOrder: 1,
      },
    ],
  });
}

function addQualificationItem(group: QualificationRequirementGroup) {
  group.items.push({
    qualificationLevel: "A_LEVEL",
    minimumCount: 1,
    minimumTotalPoints: null,
    minimumDurationMonths: null,
    sortOrder: group.items.length + 1,
  });
}

async function saveRequirementSet() {
  if (formGuidance.value.length) {
    await showError("Programme requirements are incomplete", formGuidance.value.join(" "));
    return;
  }

  saving.value = true;
  try {
    const created = await api.request<ProgrammeRequirementSet>("/api/admissions/requirement-sets", {
      method: "POST",
      body: {
        programmeId: requirementForm.programmeId,
        applicationTypeId: requirementForm.applicationTypeId,
        intakeId: requirementForm.allIntakes ? null : requirementForm.intakeId,
        versionCode: requirementForm.versionCode.trim(),
        effectiveFrom: requirementForm.effectiveFrom,
        effectiveTo: requirementForm.effectiveTo || null,
        minimumTotalPoints: requirementForm.minimumTotalPoints,
        maleCutoffPoints: requirementForm.maleCutoffPoints,
        femaleCutoffPoints: requirementForm.femaleCutoffPoints,
        requiresEnglish: requirementForm.requiresEnglish,
        requiresMathematics: requirementForm.requiresMathematics,
        requiresScience: requirementForm.requiresScience,
        requiresMathematicsOrScience: false,
        advancedRules: null,
        advancedRulesVersion: null,
        subjectRequirements: requirementForm.subjectRequirements.map((requirement, index) => ({
          ...requirement,
          subjectId: requirement.subjectId || null,
          subjectGroupCode: requirement.subjectGroupCode.trim() || null,
          minimumGrade: requirement.minimumGrade.trim() || null,
          sortOrder: index + 1,
        })),
        qualificationGroups: requirementForm.qualificationGroups.map((group, groupIndex) => ({
          code: group.code.trim().toUpperCase(),
          name: group.name.trim(),
          minimumSatisfiedItems: group.minimumSatisfiedItems,
          sortOrder: groupIndex + 1,
          items: group.items.map((item, itemIndex) => ({ ...item, sortOrder: itemIndex + 1 })),
        })),
      },
    });
    requirementSets.value = [created, ...requirementSets.value];
    creating.value = false;
    toast.add({
      title: "Programme requirements saved as draft",
      description: "Approve the version before it can be used for eligibility checks.",
      color: "success",
      icon: "i-lucide-list-checks",
    });
  } catch (error) {
    await showError("Programme requirements could not be saved", api.errorMessage(error));
  } finally {
    saving.value = false;
  }
}

async function approveRequirementSet(requirementSet: ProgrammeRequirementSet) {
  const confirmed = await confirmAction({
    title: "Approve these programme requirements?",
    text: `${requirementSet.versionCode} will become active evaluation configuration. An overlapping approved version will be retired automatically.`,
    confirmButtonText: "Approve requirements",
    icon: "question",
  });
  if (!confirmed) return;

  activeRequirementSetId.value = requirementSet.id;
  try {
    await api.request(`/api/admissions/requirement-sets/${requirementSet.id}/approve`, {
      method: "POST",
    });
    await loadRequirements();
    toast.add({
      title: "Programme requirements approved",
      color: "success",
      icon: "i-lucide-badge-check",
    });
  } catch (error) {
    await showError("Programme requirements could not be approved", api.errorMessage(error));
  } finally {
    activeRequirementSetId.value = null;
  }
}

function programmeLabel(programmeId: string) {
  const programme = programmes.value.find((item) => item.id === programmeId);
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

function formatStatus(value: string) {
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
  <UDashboardPanel data-testid="programme-requirements-workspace">
    <template #header>
      <UDashboardNavbar :title="creating ? 'New programme requirements' : 'Programme requirements'">
        <template #leading><UDashboardSidebarCollapse /></template>
        <template #right>
          <UButton
            v-if="creating"
            label="Cancel"
            icon="i-lucide-arrow-left"
            color="neutral"
            variant="ghost"
            @click="creating = false"
          /><UButton
            v-else-if="canManageRequirements"
            label="Add programme requirement"
            icon="i-lucide-plus"
            color="primary"
            @click="startCreation"
          /><UButton
            v-if="!creating"
            label="Refresh"
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="outline"
            :loading="loading"
            @click="loadRequirements"
          />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="space-y-5 p-4 sm:p-6">
        <template v-if="creating">
          <UAlert
            color="info"
            variant="soft"
            icon="i-lucide-info"
            title="Define the rules used by automatic eligibility"
            description="Create a version for one Programme and application route. Save it as draft, review it, then approve it before processing applicants."
          />

          <form
            id="programme-requirements-form"
            class="space-y-5"
            @submit.prevent="saveRequirementSet"
          >
            <UCard>
              <template #header>
                <div>
                  <h2 class="font-semibold text-highlighted">Scope and effective dates</h2>
                  <p class="mt-1 text-sm text-muted">
                    Choose exactly where and when this version applies.
                  </p>
                </div>
              </template>
              <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                <UFormField label="Programme" required>
                  <USelectMenu
                    v-model="requirementForm.programmeId"
                    :items="programmeItems"
                    value-key="value"
                    searchable
                    aria-label="Programme"
                    class="w-full"
                  />
                </UFormField>
                <UFormField label="Application type" required>
                  <USelect
                    v-model="requirementForm.applicationTypeId"
                    :items="applicationTypeItems"
                    value-key="value"
                    class="w-full"
                  />
                </UFormField>
                <UFormField
                  label="Intake scope"
                  description="Choose all intakes or bind this version to one intake"
                >
                  <UCheckbox
                    v-model="requirementForm.allIntakes"
                    label="Apply requirement to all intakes"
                    aria-label="Apply requirement to all intakes"
                  />
                  <USelect
                    v-if="!requirementForm.allIntakes"
                    v-model="requirementForm.intakeId"
                    :items="intakeItems"
                    value-key="value"
                    placeholder="Select intake"
                    class="w-full"
                  />
                  <p v-else class="mt-2 text-xs font-medium text-primary">
                    All intakes — no intake restriction will be saved.
                  </p>
                </UFormField>
                <UFormField label="Version code" required>
                  <UInput
                    v-model="requirementForm.versionCode"
                    placeholder="HCS-AUG-2026.1"
                    maxlength="50"
                    class="w-full"
                  />
                </UFormField>
                <UFormField label="Effective from" required>
                  <UInput v-model="requirementForm.effectiveFrom" type="date" class="w-full" />
                </UFormField>
                <UFormField label="Effective to">
                  <UInput v-model="requirementForm.effectiveTo" type="date" class="w-full" />
                </UFormField>
              </div>
            </UCard>

            <UCard>
              <template #header>
                <div class="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <h2 class="font-semibold text-highlighted">Subject and grade requirements</h2>
                    <p class="mt-1 text-sm text-muted">
                      Define compulsory, grouped, excluded, or weighted O Level and A Level
                      subjects.
                    </p>
                  </div>
                  <UButton
                    label="Add subject requirement"
                    icon="i-lucide-plus"
                    color="neutral"
                    variant="outline"
                    @click="addSubjectRequirement"
                  />
                </div>
              </template>
              <div v-if="requirementForm.subjectRequirements.length" class="space-y-3">
                <div
                  v-for="(requirement, index) in requirementForm.subjectRequirements"
                  :key="index"
                  class="grid gap-3 rounded-md bg-elevated p-3 md:grid-cols-4"
                >
                  <UFormField label="Level" required
                    ><USelect
                      v-model="requirement.level"
                      :items="qualificationLevelItems.slice(0, 2)"
                      value-key="value"
                      class="w-full"
                  /></UFormField>
                  <UFormField
                    :label="
                      requirement.level === 'A_LEVEL' ? 'Required A Level subject' : 'Subject'
                    "
                    :required="requirement.level === 'A_LEVEL'"
                    ><USelectMenu
                      v-model="requirement.subjectId"
                      :items="subjectItems(requirement.level)"
                      value-key="value"
                      searchable
                      clearable
                      class="w-full"
                  /></UFormField>
                  <UFormField label="Rule" required
                    ><USelect
                      v-model="requirement.requirementType"
                      :items="subjectRequirementTypeItems"
                      value-key="value"
                      class="w-full"
                  /></UFormField>
                  <UFormField label="Group code"
                    ><UInput v-model="requirement.subjectGroupCode" maxlength="50" class="w-full"
                  /></UFormField>
                  <UFormField label="Minimum grade"
                    ><UInput v-model="requirement.minimumGrade" maxlength="20" class="w-full"
                  /></UFormField>
                  <UFormField label="Minimum points"
                    ><UInput
                      v-model.number="requirement.minimumPoints"
                      type="number"
                      min="0"
                      step="0.01"
                      class="w-full"
                  /></UFormField>
                  <UFormField label="Minimum count"
                    ><UInput
                      v-model.number="requirement.minimumCount"
                      type="number"
                      min="0"
                      class="w-full"
                  /></UFormField>
                  <UButton
                    label="Remove subject rule"
                    icon="i-lucide-trash-2"
                    color="error"
                    variant="ghost"
                    @click="requirementForm.subjectRequirements.splice(index, 1)"
                  />
                </div>
              </div>
              <UEmpty
                v-else
                title="No subject rules"
                description="Add the programme's compulsory or grouped subject requirements where applicable."
              />
            </UCard>

            <UCard>
              <template #header>
                <div>
                  <h2 class="font-semibold text-highlighted">Points and baseline passes</h2>
                  <p class="mt-1 text-sm text-muted">
                    Points are calculated by the system from the applicant's verified A Level
                    results.
                  </p>
                </div>
              </template>
              <div class="grid gap-4 md:grid-cols-3">
                <UFormField label="Minimum total points"
                  ><UInput
                    v-model.number="requirementForm.minimumTotalPoints"
                    type="number"
                    min="0"
                    step="0.01"
                    class="w-full"
                /></UFormField>
                <UFormField label="Male cutoff points" description="Optional"
                  ><UInput
                    v-model.number="requirementForm.maleCutoffPoints"
                    type="number"
                    min="0"
                    step="0.01"
                    class="w-full"
                /></UFormField>
                <UFormField label="Female cutoff points" description="Optional"
                  ><UInput
                    v-model.number="requirementForm.femaleCutoffPoints"
                    type="number"
                    min="0"
                    step="0.01"
                    class="w-full"
                /></UFormField>
              </div>
              <div class="mt-4 grid gap-3 sm:grid-cols-3">
                <UCheckbox
                  v-model="requirementForm.requiresEnglish"
                  label="Require an English pass"
                /><UCheckbox
                  v-model="requirementForm.requiresMathematics"
                  label="Require a Mathematics pass"
                /><UCheckbox
                  v-model="requirementForm.requiresScience"
                  label="Require a Science pass"
                />
              </div>
            </UCard>

            <UCard>
              <template #header>
                <div class="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <h2 class="font-semibold text-highlighted">Alternative qualification routes</h2>
                    <p class="mt-1 text-sm text-muted">
                      Optional. Use groups when applicants may qualify through alternatives such as
                      A Level, Diploma, or Degree.
                    </p>
                  </div>
                  <UButton
                    label="Add qualification group"
                    icon="i-lucide-plus"
                    color="neutral"
                    variant="outline"
                    @click="addQualificationGroup"
                  />
                </div>
              </template>

              <div v-if="requirementForm.qualificationGroups.length" class="space-y-4">
                <article
                  v-for="(group, groupIndex) in requirementForm.qualificationGroups"
                  :key="groupIndex"
                  class="rounded-lg border border-muted p-4"
                >
                  <div class="flex items-start justify-between gap-3">
                    <h3 class="font-medium text-highlighted">
                      Qualification group {{ groupIndex + 1 }}
                    </h3>
                    <UButton
                      label="Remove group"
                      icon="i-lucide-trash-2"
                      color="error"
                      variant="ghost"
                      size="xs"
                      @click="requirementForm.qualificationGroups.splice(groupIndex, 1)"
                    />
                  </div>
                  <div class="mt-3 grid gap-4 md:grid-cols-3">
                    <UFormField label="Group code" required
                      ><UInput v-model="group.code" maxlength="50" class="w-full"
                    /></UFormField>
                    <UFormField label="Group name" required
                      ><UInput
                        v-model="group.name"
                        placeholder="Recognised entry qualification"
                        maxlength="160"
                        class="w-full"
                    /></UFormField>
                    <UFormField label="Minimum routes satisfied" required
                      ><UInput
                        v-model.number="group.minimumSatisfiedItems"
                        type="number"
                        min="1"
                        :max="Math.max(group.items.length, 1)"
                        class="w-full"
                    /></UFormField>
                  </div>

                  <div class="mt-4 space-y-3">
                    <div
                      v-for="(item, itemIndex) in group.items"
                      :key="itemIndex"
                      class="grid gap-3 rounded-md bg-elevated p-3 md:grid-cols-5 md:items-end"
                    >
                      <UFormField label="Qualification level" required
                        ><USelect
                          v-model="item.qualificationLevel"
                          :items="qualificationLevelItems"
                          value-key="value"
                          class="w-full"
                      /></UFormField>
                      <UFormField label="Minimum sittings" required
                        ><UInput
                          v-model.number="item.minimumCount"
                          type="number"
                          min="1"
                          class="w-full"
                      /></UFormField>
                      <UFormField label="Minimum points"
                        ><UInput
                          v-model.number="item.minimumTotalPoints"
                          type="number"
                          min="0"
                          step="0.01"
                          class="w-full"
                      /></UFormField>
                      <UFormField label="Minimum months"
                        ><UInput
                          v-model.number="item.minimumDurationMonths"
                          type="number"
                          min="0"
                          class="w-full"
                      /></UFormField>
                      <UButton
                        label="Remove route"
                        icon="i-lucide-trash-2"
                        color="error"
                        variant="ghost"
                        :disabled="group.items.length === 1"
                        @click="group.items.splice(itemIndex, 1)"
                      />
                    </div>
                    <UButton
                      label="Add alternative route"
                      icon="i-lucide-plus"
                      color="neutral"
                      variant="outline"
                      size="sm"
                      @click="addQualificationItem(group)"
                    />
                  </div>
                </article>
              </div>
              <UEmpty
                v-else
                title="No alternative routes"
                description="The points and baseline-pass rules above will be used without an additional qualification group."
              />
            </UCard>

            <div class="flex flex-wrap justify-end gap-2 border-t border-muted pt-5">
              <UButton label="Cancel" color="neutral" variant="outline" @click="creating = false" />
              <EmhareGuidedActionButton
                type="submit"
                form="programme-requirements-form"
                label="Save draft requirements"
                icon="i-lucide-save"
                color="primary"
                :loading="saving"
                guidance-title="Programme requirements are incomplete"
                :guidance-instructions="formGuidance"
              />
            </div>
          </form>
        </template>

        <template v-else>
          <UAlert
            color="info"
            variant="soft"
            icon="i-lucide-list-checks"
            title="Configure eligibility before processing applicants"
            description="Eligibility runs automatically after verification. Each Programme and application type needs an approved requirement-set version for the applicable intake and date."
            :actions="[
              {
                label: 'Open Admissions queue',
                to: '/operations/admissions',
                color: 'neutral',
                variant: 'outline',
              },
            ]"
          />
          <UAlert
            v-if="loadError"
            color="error"
            variant="soft"
            title="Programme requirements unavailable"
            :description="loadError"
          />

          <div class="flex flex-wrap items-end justify-between gap-3">
            <div>
              <h2 class="text-lg font-semibold text-highlighted">Requirement-set versions</h2>
              <p class="mt-1 text-sm text-muted">
                Draft versions do not affect eligibility until approved.
              </p>
            </div>
            <div class="flex flex-wrap items-center gap-3">
              <span class="text-sm text-muted">{{ filteredRequirementSets.length }} versions</span>
              <UButton
                v-if="canManageRequirements"
                label="Add programme requirement"
                icon="i-lucide-plus"
                color="primary"
                variant="soft"
                @click="startCreation"
              />
            </div>
          </div>

          <div v-if="filteredRequirementSets.length" class="grid gap-3 lg:grid-cols-2">
            <UCard
              v-for="requirementSet in filteredRequirementSets"
              :key="requirementSet.id"
              variant="outline"
              ><div class="flex items-start justify-between gap-3">
                <div class="min-w-0">
                  <p class="font-mono text-xs text-muted">{{ requirementSet.versionCode }}</p>
                  <h3 class="mt-1 font-semibold text-highlighted">
                    {{ programmeLabel(requirementSet.programmeId) }}
                  </h3>
                  <p class="mt-1 text-sm text-muted">
                    {{ applicationTypeLabel(requirementSet.applicationTypeId) }} ·
                    {{ intakeLabel(requirementSet.intakeId) }}
                  </p>
                </div>
                <EmhareStatusPill
                  :label="formatStatus(requirementSet.status)"
                  :tone="
                    requirementSet.status === 'APPROVED'
                      ? 'success'
                      : requirementSet.status === 'RETIRED'
                        ? 'neutral'
                        : 'warning'
                  "
                />
              </div>
              <div class="mt-4 grid gap-2 text-sm sm:grid-cols-2">
                <p>
                  <span class="text-muted">Effective:</span>
                  {{ formatDate(requirementSet.effectiveFrom) }} to
                  {{ formatDate(requirementSet.effectiveTo) }}
                </p>
                <p>
                  <span class="text-muted">Minimum points:</span>
                  {{ requirementSet.minimumTotalPoints ?? "Not set" }}
                </p>
                <p>
                  <span class="text-muted">English:</span>
                  {{ requirementSet.requiresEnglish ? "Required" : "Not required" }}
                </p>
                <p>
                  <span class="text-muted">Mathematics:</span>
                  {{ requirementSet.requiresMathematics ? "Required" : "Not required" }}
                </p>
                <p>
                  <span class="text-muted">Science:</span>
                  {{ requirementSet.requiresScience ? "Required" : "Not required" }}
                </p>
                <p v-if="requirementSet.requiresMathematicsOrScience">
                  <span class="text-muted">Historical Mathematics or Science rule:</span>
                  Required
                </p>
                <p>
                  <span class="text-muted">Subject rules:</span>
                  {{ requirementSet.subjectRequirements?.length ?? 0 }}
                </p>
                <p>
                  <span class="text-muted">Alternative groups:</span>
                  {{ requirementSet.qualificationGroups?.length ?? 0 }}
                </p>
              </div>
              <div class="mt-4 flex flex-wrap justify-end gap-2 border-t border-muted pt-4">
                <UButton
                  label="View full details"
                  icon="i-lucide-arrow-up-right"
                  color="neutral"
                  variant="outline"
                  :to="`/operations/programme-requirements/${requirementSet.id}`"
                />
                <UButton
                  v-if="requirementSet.status === 'DRAFT' && canManageRequirements"
                  label="Approve requirements"
                  icon="i-lucide-badge-check"
                  color="primary"
                  :loading="activeRequirementSetId === requirementSet.id"
                  @click="approveRequirementSet(requirementSet)"
                />
              </div>
            </UCard>
          </div>
          <UEmpty
            v-else-if="!loading"
            title="No programme requirements configured"
            description="Create and approve a requirement set before verified applications can complete automatic eligibility."
            :actions="
              canManageRequirements
                ? [
                    {
                      label: 'Add programme requirement',
                      icon: 'i-lucide-plus',
                      onClick: startCreation,
                    },
                  ]
                : []
            "
          />
          <div v-else class="grid gap-3 lg:grid-cols-2">
            <USkeleton v-for="index in 4" :key="index" class="h-48 rounded-xl" />
          </div>
        </template>
      </div>
    </template>
  </UDashboardPanel>
</template>
