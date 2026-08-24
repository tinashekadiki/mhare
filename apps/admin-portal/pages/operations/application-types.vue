<script setup lang="ts">
import type { TableColumn } from "@nuxt/ui";
import type { AdmissionsApplicationTypeSummary } from "@emhare/portal-shell/types/admissions";
import type {
  FinanceFeeStructureRegister,
  FinanceFeeStructureSummary,
} from "@emhare/portal-shell/types/finance";

definePageMeta({ layout: "dashboard" });

type RouteProgrammeMapping = {
  programmeId: string;
  programmeCode: string;
  programmeName: string;
};

type RouteSectionConfiguration = {
  code: string;
  name: string;
  required: boolean;
  repeatable: boolean;
  minimumRecords: number;
  sortOrder: number;
};

type RouteDocumentConfiguration = {
  code: string;
  name: string;
  required: boolean;
  sortOrder: number;
  captureSectionCode: "PERSONAL_DETAILS" | "QUALIFICATIONS" | "SUPPORTING_DOCUMENTS";
  applicantCategoryCodes: string[];
};

type ApplicationRouteConfiguration = {
  applicationTypeId: string;
  code: string;
  name: string;
  active: boolean;
  readyForActivation: boolean;
  readinessBlockers: string[];
  activeProgrammeCount: number;
  programmes: RouteProgrammeMapping[];
  sections: RouteSectionConfiguration[];
  requiredDocumentCount: number;
  documents: RouteDocumentConfiguration[];
  feePolicyStatus: "UNCONFIGURED" | "FEE_STRUCTURE" | "FEE_FREE";
  version: number;
};

const api = useEmhareApi();
const toast = useToast();
const { showError } = useEmhareConfirm();
const academicSetup = useAcademicSetup();

const applicationTypes = ref<AdmissionsApplicationTypeSummary[]>([]);
const feeStructures = ref<FinanceFeeStructureSummary[]>([]);
const loading = ref(false);
const loadError = ref("");
const saving = ref(false);
const drawerOpen = ref(false);
const routeConfigurationDrawerOpen = ref(false);
const loadingRouteConfiguration = ref(false);
const savingRouteConfiguration = ref(false);
const loadedRouteConfiguration = ref<ApplicationRouteConfiguration | null>(null);
const searchQuery = ref("");
const statusFilter = ref<"ALL" | "ACTIVE" | "INACTIVE">("ALL");

const applicationTypeForm = reactive({
  id: null as string | null,
  code: "",
  name: "",
  requiresEmploymentHistory: false,
  requiresReferees: false,
  financeFeeStructureId: "",
  active: false,
  originalActive: false,
  changeReason: "",
  expectedVersion: 0,
});

const routeConfigurationForm = reactive({
  applicationTypeId: "",
  programmeIds: [] as string[],
  sections: [] as RouteSectionConfiguration[],
  documents: [] as RouteDocumentConfiguration[],
  feeFree: false,
  feeFreeReason: "",
  activate: false,
  changeReason: "",
  expectedVersion: 0,
});

const columns: TableColumn<AdmissionsApplicationTypeSummary>[] = [
  { accessorKey: "code", header: "Code" },
  { accessorKey: "name", header: "Application type" },
  { id: "feeStructure", header: "Application fee" },
  { id: "requirements", header: "Additional sections" },
  { accessorKey: "active", header: "Status" },
  { id: "actions", header: "Actions" },
];

const statusItems = [
  { label: "All statuses", value: "ALL" },
  { label: "Active", value: "ACTIVE" },
  { label: "Inactive", value: "INACTIVE" },
];

const feeStructureItems = computed(() =>
  feeStructures.value
    .filter((structure) => structure.feeContext === "APPLICATION" && structure.status !== "RETIRED")
    .map((structure) => ({
      label: `${structure.code} · ${structure.name}`,
      value: structure.id,
    })),
);
const selectedFeeStructure = computed(
  () =>
    feeStructures.value.find(
      (structure) => structure.id === applicationTypeForm.financeFeeStructureId,
    ) ?? null,
);
const activeAcademicProgrammes = computed(() =>
  (academicSetup.overview.value?.programmes ?? []).filter(
    (programme) => programme.status === "ACTIVE",
  ),
);
const routeProgrammeItems = computed(() =>
  activeAcademicProgrammes.value.map((programme) => ({
    label: `${programme.code} · ${programme.name}`,
    value: programme.id,
    description: `${programme.programmeLevelName} · ${programme.owningAcademicUnitName}`,
  })),
);

const editingApplicationType = computed(() => applicationTypeForm.id !== null);
const filteredApplicationTypes = computed(() => {
  const normalizedQuery = searchQuery.value.trim().toLowerCase();
  return applicationTypes.value.filter((applicationType) => {
    const matchesQuery =
      !normalizedQuery ||
      applicationType.code.toLowerCase().includes(normalizedQuery) ||
      applicationType.name.toLowerCase().includes(normalizedQuery);
    const matchesStatus =
      statusFilter.value === "ALL" ||
      (statusFilter.value === "ACTIVE" && applicationType.active) ||
      (statusFilter.value === "INACTIVE" && !applicationType.active);
    return matchesQuery && matchesStatus;
  });
});
const formGuidance = computed(() => {
  const instructions: string[] = [];
  if (!applicationTypeForm.code.trim()) instructions.push("Enter a stable application type code.");
  else if (!/^[A-Za-z0-9_-]+$/.test(applicationTypeForm.code.trim()))
    instructions.push("Use only letters, numbers, hyphens, or underscores in the code.");
  if (!applicationTypeForm.name.trim())
    instructions.push("Enter the applicant-facing application type name.");
  if (editingApplicationType.value && applicationTypeForm.changeReason.trim().length < 10) {
    instructions.push("Provide at least 10 characters explaining this audited change.");
  }
  return instructions;
});
const routeConfigurationGuidance = computed(() => {
  const instructions: string[] = [];
  if (!routeConfigurationForm.programmeIds.length)
    instructions.push("Select at least one active Programme.");
  if (
    !routeConfigurationForm.documents.some(
      (document) => document.required && document.code.trim() && document.name.trim(),
    )
  ) {
    instructions.push("Define at least one required supporting document.");
  }
  if (routeConfigurationForm.feeFree && routeConfigurationForm.feeFreeReason.trim().length < 10) {
    instructions.push("Explain the audited fee-free decision in at least 10 characters.");
  }
  if (
    loadedRouteConfiguration.value?.feePolicyStatus === "UNCONFIGURED" &&
    !routeConfigurationForm.feeFree
  ) {
    instructions.push("Link a Finance fee structure or record an audited fee-free decision.");
  }
  if (routeConfigurationForm.changeReason.trim().length < 10) {
    instructions.push(
      "Provide at least 10 characters explaining this audited route configuration.",
    );
  }
  return instructions;
});

onMounted(loadApplicationTypes);

async function loadApplicationTypes() {
  loading.value = true;
  loadError.value = "";
  try {
    const [applicationTypeResult, feeStructureResult] = await Promise.all([
      api.request<AdmissionsApplicationTypeSummary[]>("/api/admissions/application-types"),
      api.request<FinanceFeeStructureRegister>("/api/finance/fee-structures"),
    ]);
    applicationTypes.value = applicationTypeResult;
    feeStructures.value = feeStructureResult.structures;
  } catch (error) {
    loadError.value = api.errorMessage(error, "Application types could not be loaded.");
  } finally {
    loading.value = false;
  }
}

function openCreateDrawer() {
  Object.assign(applicationTypeForm, {
    id: null,
    code: "",
    name: "",
    requiresEmploymentHistory: false,
    requiresReferees: false,
    financeFeeStructureId: "",
    active: false,
    originalActive: false,
    changeReason: "",
    expectedVersion: 0,
  });
  drawerOpen.value = true;
}

function openEditDrawer(applicationType: AdmissionsApplicationTypeSummary) {
  Object.assign(applicationTypeForm, {
    id: applicationType.id,
    code: applicationType.code,
    name: applicationType.name,
    requiresEmploymentHistory: applicationType.requiresEmploymentHistory,
    requiresReferees: applicationType.requiresReferees,
    financeFeeStructureId: applicationType.financeFeeStructureId ?? "",
    active: applicationType.active,
    originalActive: applicationType.active,
    changeReason: "",
    expectedVersion: applicationType.version,
  });
  drawerOpen.value = true;
}

function standardDocumentRequirements(): RouteDocumentConfiguration[] {
  return [
    {
      code: "NATIONAL_ID",
      name: "National ID",
      required: true,
      sortOrder: 10,
      captureSectionCode: "PERSONAL_DETAILS",
      applicantCategoryCodes: ["LOCAL"],
    },
    {
      code: "BIRTH_CERTIFICATE",
      name: "Birth Certificate",
      required: true,
      sortOrder: 20,
      captureSectionCode: "PERSONAL_DETAILS",
      applicantCategoryCodes: ["LOCAL"],
    },
    {
      code: "PASSPORT",
      name: "Passport",
      required: true,
      sortOrder: 30,
      captureSectionCode: "PERSONAL_DETAILS",
      applicantCategoryCodes: ["SADC", "INTERNATIONAL"],
    },
  ];
}

async function openRouteConfiguration(applicationType: AdmissionsApplicationTypeSummary) {
  routeConfigurationDrawerOpen.value = true;
  loadingRouteConfiguration.value = true;
  loadedRouteConfiguration.value = null;
  try {
    const [configuration] = await Promise.all([
      api.request<ApplicationRouteConfiguration>(
        `/api/admissions/application-types/${applicationType.id}/route-configuration`,
      ),
      academicSetup.ensureOverview(),
    ]);
    loadedRouteConfiguration.value = configuration;
    Object.assign(routeConfigurationForm, {
      applicationTypeId: configuration.applicationTypeId,
      programmeIds: configuration.programmes.map((programme) => programme.programmeId),
      sections: configuration.sections.map((section) => ({ ...section })),
      documents: (configuration.documents.length
        ? configuration.documents
        : standardDocumentRequirements()
      ).map((document) => ({
        ...document,
        captureSectionCode: document.captureSectionCode ?? "SUPPORTING_DOCUMENTS",
        applicantCategoryCodes: document.applicantCategoryCodes ?? [],
      })),
      feeFree: configuration.feePolicyStatus === "FEE_FREE",
      feeFreeReason: "",
      activate: configuration.active,
      changeReason: "",
      expectedVersion: configuration.version,
    });
  } catch (error) {
    routeConfigurationDrawerOpen.value = false;
    await showError("Route configuration could not be loaded", api.errorMessage(error));
  } finally {
    loadingRouteConfiguration.value = false;
  }
}

function addDocumentRequirement() {
  routeConfigurationForm.documents.push({
    code: "",
    name: "",
    required: true,
    sortOrder: (routeConfigurationForm.documents.length + 1) * 10,
    captureSectionCode: "SUPPORTING_DOCUMENTS",
    applicantCategoryCodes: [],
  });
}

async function saveRouteConfiguration() {
  if (routeConfigurationGuidance.value.length || !loadedRouteConfiguration.value) return;
  const programmesById = new Map(
    activeAcademicProgrammes.value.map((programme) => [programme.id, programme]),
  );
  savingRouteConfiguration.value = true;
  try {
    const result = await api.request<ApplicationRouteConfiguration>(
      `/api/admissions/application-types/${routeConfigurationForm.applicationTypeId}/route-configuration`,
      {
        method: "PUT",
        body: {
          programmes: routeConfigurationForm.programmeIds.map((programmeId) => {
            const programme = programmesById.get(programmeId);
            if (!programme)
              throw new Error("A selected Programme is no longer active in Academic Setup.");
            return {
              programmeId,
              programmeCode: programme.code,
              programmeName: programme.name,
            };
          }),
          sections: routeConfigurationForm.sections.map((section) => ({
            ...section,
            minimumRecords: Number(section.minimumRecords),
            sortOrder: Number(section.sortOrder),
          })),
          documents: routeConfigurationForm.documents.map((document, index) => ({
            code: document.code.trim().toUpperCase(),
            name: document.name.trim(),
            required: document.required,
            sortOrder: (index + 1) * 10,
            captureSectionCode: document.captureSectionCode,
            applicantCategoryCodes: document.applicantCategoryCodes,
          })),
          feeFree: routeConfigurationForm.feeFree,
          feeFreeReason: routeConfigurationForm.feeFree
            ? routeConfigurationForm.feeFreeReason.trim()
            : null,
          activate: routeConfigurationForm.activate,
          changeReason: routeConfigurationForm.changeReason.trim(),
          expectedVersion: routeConfigurationForm.expectedVersion,
        },
      },
    );
    await loadApplicationTypes();
    routeConfigurationDrawerOpen.value = false;
    toast.add({
      title: result.active ? "Application route activated" : "Route configuration saved",
      description: result.active
        ? `${result.name} is available when a mapped Programme intersects an open intake.`
        : `${result.name} remains inactive.`,
      color: "success",
      icon: result.active ? "i-lucide-circle-check-big" : "i-lucide-save",
    });
  } catch (error) {
    await showError("Route configuration could not be saved", api.errorMessage(error));
  } finally {
    savingRouteConfiguration.value = false;
  }
}

async function saveApplicationType() {
  if (formGuidance.value.length) return;
  const applicationTypeId = applicationTypeForm.id;
  saving.value = true;
  try {
    const requestBody = applicationTypeId
      ? {
          name: applicationTypeForm.name.trim(),
          requiresEmploymentHistory: applicationTypeForm.requiresEmploymentHistory,
          requiresReferees: applicationTypeForm.requiresReferees,
          financeFeeStructureId: selectedFeeStructure.value?.id ?? null,
          financeFeeStructureCode: selectedFeeStructure.value?.code ?? null,
          financeFeeStructureName: selectedFeeStructure.value?.name ?? null,
          active: applicationTypeForm.active,
          changeReason: applicationTypeForm.changeReason.trim(),
          expectedVersion: applicationTypeForm.expectedVersion,
        }
      : {
          code: applicationTypeForm.code.trim().toUpperCase(),
          name: applicationTypeForm.name.trim(),
          requiresEmploymentHistory: applicationTypeForm.requiresEmploymentHistory,
          requiresReferees: applicationTypeForm.requiresReferees,
          financeFeeStructureId: selectedFeeStructure.value?.id ?? null,
          financeFeeStructureCode: selectedFeeStructure.value?.code ?? null,
          financeFeeStructureName: selectedFeeStructure.value?.name ?? null,
          active: false,
        };
    await api.request(
      applicationTypeId
        ? `/api/admissions/application-types/${applicationTypeId}`
        : "/api/admissions/application-types",
      { method: applicationTypeId ? "PUT" : "POST", body: requestBody },
    );
    await loadApplicationTypes();
    drawerOpen.value = false;
    toast.add({
      title: `Application type ${applicationTypeId ? "updated" : "created"}`,
      description: applicationTypeForm.active
        ? "The route is available to applicants."
        : "The route remains inactive until it is activated.",
      color: "success",
      icon: "i-lucide-badge-check",
    });
  } catch (error) {
    await showError(
      `Application type could not be ${applicationTypeId ? "updated" : "created"}`,
      api.errorMessage(error),
    );
  } finally {
    saving.value = false;
  }
}

function requirementLabels(applicationType: AdmissionsApplicationTypeSummary) {
  const labels: string[] = [];
  if (applicationType.requiresEmploymentHistory) labels.push("Employment history");
  if (applicationType.requiresReferees) labels.push("Referees");
  return labels;
}

function feeStructureLabel(applicationType: AdmissionsApplicationTypeSummary) {
  return applicationType.financeFeeStructureCode && applicationType.financeFeeStructureName
    ? `${applicationType.financeFeeStructureCode} · ${applicationType.financeFeeStructureName}`
    : "No fee structure associated";
}

function feeStructureDetail(applicationType: AdmissionsApplicationTypeSummary) {
  const structure = feeStructures.value.find(
    (item) => item.id === applicationType.financeFeeStructureId,
  );
  if (!structure) {
    return applicationType.financeFeeStructureId
      ? "Stored Finance snapshot"
      : "Applications using this route are treated as fee-free until Finance is linked.";
  }
  const category = structure.applicantCategoryCode ? ` · ${structure.applicantCategoryCode}` : "";
  return `${structure.programmeLevelCode} · ${structure.status}${category}`;
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Application types">
        <template #leading><UDashboardSidebarCollapse /></template>
        <template #right>
          <UButton
            label="Refresh"
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="outline"
            :loading="loading"
            @click="loadApplicationTypes"
          />
          <UButton
            label="New application type"
            icon="i-lucide-plus"
            color="primary"
            @click="openCreateDrawer"
          />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="space-y-5 p-4 sm:p-6">
        <UAlert
          color="primary"
          variant="soft"
          icon="i-lucide-route"
          title="Govern applicant routes before opening admissions"
          description="Application types control the application sections applicants must complete. Only active types are exposed in the applicant portal."
        />
        <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <EmhareKpiCard
            label="Application types"
            :value="applicationTypes.length"
            icon="i-lucide-files"
            tone="primary"
          />
          <EmhareKpiCard
            label="Active"
            :value="applicationTypes.filter((type) => type.active).length"
            icon="i-lucide-circle-check"
            tone="success"
          />
          <EmhareKpiCard
            label="Inactive"
            :value="applicationTypes.filter((type) => !type.active).length"
            icon="i-lucide-circle-pause"
            tone="warning"
          />
          <EmhareKpiCard
            label="With additional sections"
            :value="
              applicationTypes.filter(
                (type) => type.requiresEmploymentHistory || type.requiresReferees,
              ).length
            "
            icon="i-lucide-list-checks"
            tone="neutral"
          />
        </div>
        <UAlert
          v-if="loadError"
          color="error"
          variant="soft"
          icon="i-lucide-circle-alert"
          title="Application types unavailable"
          :description="loadError"
        />

        <EmhareRegisterPanel
          title="Application type register"
          description="Stable route codes, applicant-facing names, conditional sections, and availability."
          :record-count="filteredApplicationTypes.length"
        >
          <template #actions>
            <UInput
              v-model="searchQuery"
              icon="i-lucide-search"
              placeholder="Search code or name"
              class="w-full sm:w-64"
            />
            <USelect
              v-model="statusFilter"
              :items="statusItems"
              value-key="value"
              class="w-40"
              aria-label="Filter by status"
            />
          </template>
          <UCard :ui="{ body: 'p-0' }">
            <EmharePaginatedTable
              :data="filteredApplicationTypes"
              :columns="columns"
              :loading="loading"
              sticky
            >
              <template #code-cell="{ row }">
                <span class="font-mono font-semibold text-primary">{{ row.original.code }}</span>
              </template>
              <template #feeStructure-cell="{ row }">
                <div class="max-w-xs">
                  <p
                    :class="
                      row.original.financeFeeStructureId
                        ? 'font-medium text-highlighted'
                        : 'text-muted'
                    "
                  >
                    {{ feeStructureLabel(row.original) }}
                  </p>
                  <p class="text-xs text-muted">
                    {{ feeStructureDetail(row.original) }}
                  </p>
                </div>
              </template>
              <template #requirements-cell="{ row }">
                <div v-if="requirementLabels(row.original).length" class="flex flex-wrap gap-1">
                  <UBadge
                    v-for="label in requirementLabels(row.original)"
                    :key="label"
                    :label="label"
                    color="neutral"
                    variant="subtle"
                  />
                </div>
                <span v-else class="text-muted">Standard sections</span>
              </template>
              <template #active-cell="{ row }">
                <EmhareStatusPill
                  :label="row.original.active ? 'Active' : 'Inactive'"
                  :tone="row.original.active ? 'success' : 'warning'"
                />
              </template>
              <template #actions-cell="{ row }">
                <div class="flex flex-wrap gap-1">
                  <UButton
                    label="Configure"
                    icon="i-lucide-sliders-horizontal"
                    color="primary"
                    variant="ghost"
                    @click="openRouteConfiguration(row.original)"
                  />
                  <UButton
                    label="Edit"
                    icon="i-lucide-pencil"
                    color="neutral"
                    variant="ghost"
                    @click="openEditDrawer(row.original)"
                  />
                </div>
              </template>
              <template #empty>
                <EmhareFeedbackState
                  state="empty"
                  title="No application types found"
                  :description="
                    applicationTypes.length
                      ? 'Adjust the search or status filter.'
                      : 'Create the first application type before opening applications.'
                  "
                />
              </template>
            </EmharePaginatedTable>
          </UCard>
        </EmhareRegisterPanel>
      </div>
    </template>
  </UDashboardPanel>

  <EmhareRecordDrawer
    v-model:open="drawerOpen"
    presentation="page"
    :title="editingApplicationType ? 'Edit application type' : 'Create application type'"
    :description="
      editingApplicationType
        ? 'Maintain the applicant route. Every correction is retained in audit history.'
        : 'Define a governed route applicants may use when admissions are open.'
    "
    :submit-label="editingApplicationType ? 'Save changes' : 'Create application type'"
    :busy="saving"
    :submit-disabled="formGuidance.length > 0"
    :submit-disabled-reason="formGuidance.join(' ')"
    @submit="saveApplicationType"
  >
    <template #body>
      <UAlert
        v-if="editingApplicationType"
        color="info"
        variant="soft"
        icon="i-lucide-lock-keyhole"
        title="Stable operational code"
        description="The code is locked after creation so integrations and historical application records keep the same route identifier."
      />
      <UAlert
        v-else
        color="warning"
        variant="soft"
        icon="i-lucide-circle-pause"
        title="Start inactive unless this route is ready"
        description="An active type becomes available to applicants while an eligible intake is open."
      />
      <form id="application-type-form" class="space-y-5" @submit.prevent="saveApplicationType">
        <div class="grid gap-4 sm:grid-cols-2">
          <UFormField
            label="Code"
            description="Letters, numbers, hyphens, or underscores."
            required
          >
            <UInput
              v-model="applicationTypeForm.code"
              :disabled="editingApplicationType"
              maxlength="50"
              placeholder="UNDERGRAD"
              class="w-full"
            />
          </UFormField>
          <UFormField label="Name" required>
            <UInput
              v-model="applicationTypeForm.name"
              maxlength="150"
              placeholder="Undergraduate"
              class="w-full"
            />
          </UFormField>
        </div>

        <UCard variant="subtle" :ui="{ body: 'space-y-3' }">
          <div>
            <h3 class="font-medium text-highlighted">Application fee structure</h3>
            <p class="mt-1 text-sm text-muted">
              Associate this applicant route with the Finance-governed application fee schedule.
            </p>
          </div>
          <UFormField
            label="Fee structure"
            description="Create and activate application fee schedules in Finance before applicant go-live."
          >
            <USelectMenu
              v-model="applicationTypeForm.financeFeeStructureId"
              :items="feeStructureItems"
              value-key="value"
              searchable
              clearable
              class="w-full"
              placeholder="No fee structure associated"
            />
          </UFormField>
          <UAlert
            v-if="!feeStructureItems.length"
            color="warning"
            variant="soft"
            icon="i-lucide-badge-dollar-sign"
            title="No application fee structures found"
            description="Open Finance → Fee catalogue and pricing, create an Application fee structure, then return here to link it to the application type."
          />
        </UCard>

        <UCard variant="subtle" :ui="{ body: 'space-y-4' }">
          <div>
            <h3 class="font-medium text-highlighted">Required application sections</h3>
            <p class="mt-1 text-sm text-muted">
              Turn on only the evidence sections that apply to this route.
            </p>
          </div>
          <div class="flex items-center justify-between gap-4">
            <div>
              <p class="text-sm font-medium text-highlighted">Employment history</p>
              <p class="text-xs text-muted">Applicants must provide their relevant work history.</p>
            </div>
            <USwitch
              v-model="applicationTypeForm.requiresEmploymentHistory"
              aria-label="Require employment history"
            />
          </div>
          <div class="flex items-center justify-between gap-4">
            <div>
              <p class="text-sm font-medium text-highlighted">Referees</p>
              <p class="text-xs text-muted">Applicants must provide referee details.</p>
            </div>
            <USwitch v-model="applicationTypeForm.requiresReferees" aria-label="Require referees" />
          </div>
        </UCard>

        <div class="flex items-center justify-between gap-4 rounded-lg border border-muted p-4">
          <div>
            <p class="text-sm font-medium text-highlighted">Active for applications</p>
            <p class="text-xs text-muted">
              Activation is controlled by the atomic route configuration after programmes, sections,
              documents, fees, and reference thresholds are ready.
            </p>
          </div>
          <EmhareStatusPill
            :label="applicationTypeForm.active ? 'Active' : 'Inactive'"
            :tone="applicationTypeForm.active ? 'success' : 'warning'"
          />
        </div>

        <UFormField
          v-if="editingApplicationType"
          label="Change reason"
          description="Required for the audited correction history. Enter at least 10 characters."
          required
        >
          <UTextarea
            v-model="applicationTypeForm.changeReason"
            :rows="4"
            minlength="10"
            maxlength="1000"
            placeholder="Explain why this application route is being changed."
            class="w-full"
          />
        </UFormField>
      </form>
    </template>
  </EmhareRecordDrawer>

  <EmhareRecordDrawer
    v-model:open="routeConfigurationDrawerOpen"
    presentation="page"
    :title="
      loadedRouteConfiguration
        ? `Configure ${loadedRouteConfiguration.name}`
        : 'Configure application route'
    "
    description="Map Programmes, confirm applicant evidence, define supporting documents, and activate the route as one audited change."
    submit-label="Save route configuration"
    :busy="savingRouteConfiguration"
    :submit-disabled="loadingRouteConfiguration || routeConfigurationGuidance.length > 0"
    :submit-disabled-reason="routeConfigurationGuidance.join(' ')"
    @submit="saveRouteConfiguration"
  >
    <template #body>
      <EmhareFeedbackState
        v-if="loadingRouteConfiguration"
        state="loading"
        title="Loading route configuration"
      />
      <form
        v-else-if="loadedRouteConfiguration"
        id="application-route-configuration-form"
        class="space-y-5"
        @submit.prevent="saveRouteConfiguration"
      >
        <UAlert
          :color="loadedRouteConfiguration.readyForActivation ? 'success' : 'warning'"
          variant="soft"
          :icon="
            loadedRouteConfiguration.readyForActivation
              ? 'i-lucide-circle-check-big'
              : 'i-lucide-triangle-alert'
          "
          :title="
            loadedRouteConfiguration.readyForActivation
              ? 'Route configuration is ready'
              : 'Complete activation requirements'
          "
          :description="
            loadedRouteConfiguration.readyForActivation
              ? 'The saved route configuration satisfies the activation rules.'
              : loadedRouteConfiguration.readinessBlockers.join(' · ')
          "
        />

        <UCard variant="subtle" :ui="{ body: 'space-y-4' }">
          <div>
            <h3 class="font-medium text-highlighted">Eligible Programmes</h3>
            <p class="mt-1 text-sm text-muted">
              Only applicants on this route may select the mapped active Programmes.
            </p>
          </div>
          <UFormField
            label="Programme mappings"
            description="Select every Programme that belongs to this application route."
            required
          >
            <USelectMenu
              v-model="routeConfigurationForm.programmeIds"
              aria-label="Programme mappings"
              :items="routeProgrammeItems"
              value-key="value"
              multiple
              searchable
              class="w-full"
              placeholder="Select active Programmes"
            />
          </UFormField>
          <UAlert
            v-if="!routeProgrammeItems.length"
            color="warning"
            variant="soft"
            icon="i-lucide-graduation-cap"
            title="No active Programmes available"
            description="Create and activate the route's Programmes in Academic Setup before activation."
          />
        </UCard>

        <UCard variant="subtle" :ui="{ body: 'space-y-4' }">
          <div>
            <h3 class="font-medium text-highlighted">Governed application sections</h3>
            <p class="mt-1 text-sm text-muted">
              Required sections and record thresholds are snapshotted when an applicant creates a
              draft.
            </p>
          </div>
          <div class="divide-y divide-muted rounded-lg border border-muted">
            <div
              v-for="section in routeConfigurationForm.sections"
              :key="section.code"
              class="grid gap-3 p-3 sm:grid-cols-[minmax(0,1fr)_8rem_auto] sm:items-center"
            >
              <div>
                <p class="text-sm font-medium text-highlighted">
                  {{ section.name }}
                </p>
                <p class="font-mono text-xs text-muted">{{ section.code }}</p>
              </div>
              <UFormField v-if="section.repeatable" label="Minimum records">
                <UInput
                  v-model="section.minimumRecords"
                  type="number"
                  min="0"
                  max="20"
                  class="w-full"
                />
              </UFormField>
              <div class="flex items-center justify-between gap-3 sm:justify-end">
                <span class="text-xs text-muted">Required</span>
                <USwitch v-model="section.required" :aria-label="`Require ${section.name}`" />
              </div>
            </div>
          </div>
        </UCard>

        <UCard variant="subtle" :ui="{ body: 'space-y-4' }">
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h3 class="font-medium text-highlighted">Supporting documents</h3>
              <p class="mt-1 text-sm text-muted">
                At least one required document must be defined before activation.
              </p>
            </div>
            <UButton
              label="Add document"
              icon="i-lucide-plus"
              color="neutral"
              variant="outline"
              size="sm"
              @click="addDocumentRequirement"
            />
          </div>
          <div class="space-y-3">
            <div
              v-for="(document, index) in routeConfigurationForm.documents"
              :key="`${document.code}-${index}`"
              class="grid gap-3 rounded-lg border border-muted p-3 lg:grid-cols-2 lg:items-end"
            >
              <UFormField label="Document code" required>
                <UInput
                  v-model="document.code"
                  class="w-full font-mono"
                  placeholder="IDENTITY_DOCUMENT"
                />
              </UFormField>
              <UFormField label="Applicant-facing name" required>
                <UInput v-model="document.name" class="w-full" placeholder="Identity document" />
              </UFormField>
              <UFormField label="Capture section" required>
                <USelect
                  v-model="document.captureSectionCode"
                  class="w-full"
                  :items="[
                    { label: 'Personal Details', value: 'PERSONAL_DETAILS' },
                    { label: 'Qualifications', value: 'QUALIFICATIONS' },
                    { label: 'General supporting documents', value: 'SUPPORTING_DOCUMENTS' },
                  ]"
                />
              </UFormField>
              <UFormField label="Applicant categories" hint="None means all categories">
                <USelectMenu
                  v-model="document.applicantCategoryCodes"
                  multiple
                  class="w-full"
                  value-key="value"
                  :items="[
                    { label: 'Local', value: 'LOCAL' },
                    { label: 'SADC', value: 'SADC' },
                    { label: 'International', value: 'INTERNATIONAL' },
                    { label: 'Credit transfer', value: 'CLE' },
                  ]"
                  placeholder="All categories"
                />
              </UFormField>
              <div
                class="flex items-center justify-between gap-3 pb-1 lg:col-span-2 lg:justify-end"
              >
                <span class="text-xs text-muted">Required</span>
                <USwitch
                  v-model="document.required"
                  :aria-label="`Require ${document.name || 'document'}`"
                />
                <UButton
                  icon="i-lucide-trash-2"
                  aria-label="Remove document"
                  color="error"
                  variant="ghost"
                  @click="routeConfigurationForm.documents.splice(index, 1)"
                />
              </div>
            </div>
          </div>
        </UCard>

        <UCard variant="subtle" :ui="{ body: 'space-y-4' }">
          <div>
            <h3 class="font-medium text-highlighted">Fee policy</h3>
            <p class="mt-1 text-sm text-muted">
              Current policy:
              {{ loadedRouteConfiguration.feePolicyStatus.replaceAll("_", " ").toLowerCase() }}.
            </p>
          </div>
          <div
            v-if="loadedRouteConfiguration.feePolicyStatus !== 'FEE_STRUCTURE'"
            class="space-y-3"
          >
            <div class="flex items-center justify-between gap-4">
              <div>
                <p class="text-sm font-medium text-highlighted">Audited fee-free decision</p>
                <p class="text-xs text-muted">
                  Use this only when no Finance application fee applies.
                </p>
              </div>
              <USwitch
                v-model="routeConfigurationForm.feeFree"
                aria-label="Record fee-free decision"
              />
            </div>
            <UFormField v-if="routeConfigurationForm.feeFree" label="Fee-free reason" required>
              <UTextarea
                v-model="routeConfigurationForm.feeFreeReason"
                :rows="3"
                minlength="10"
                maxlength="1000"
                class="w-full"
              />
            </UFormField>
          </div>
          <UAlert
            v-else
            color="success"
            variant="soft"
            icon="i-lucide-badge-dollar-sign"
            title="Finance fee structure linked"
            description="The existing Finance-governed fee policy satisfies route activation."
          />
        </UCard>

        <div class="flex items-center justify-between gap-4 rounded-lg border border-muted p-4">
          <div>
            <p class="text-sm font-medium text-highlighted">Active for applications</p>
            <p class="text-xs text-muted">
              Activation succeeds only when every server-side readiness rule passes.
            </p>
          </div>
          <USwitch
            v-model="routeConfigurationForm.activate"
            aria-label="Activate application route"
          />
        </div>

        <UFormField
          label="Change reason"
          description="Required for the audited route-configuration history."
          required
        >
          <UTextarea
            v-model="routeConfigurationForm.changeReason"
            :rows="4"
            minlength="10"
            maxlength="1000"
            placeholder="Explain why this route configuration is being changed."
            class="w-full"
          />
        </UFormField>
      </form>
    </template>
  </EmhareRecordDrawer>
</template>
