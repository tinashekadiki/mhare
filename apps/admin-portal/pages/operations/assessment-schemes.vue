<script setup lang="ts">
import Swal from "sweetalert2";
import type {
  AssessmentOfferingSummary,
  AssessmentRosterSource,
  AssessmentSchemeSummary,
  ComponentType,
} from "@emhare/portal-shell/types/assessment";

definePageMeta({ layout: "dashboard" });
const api = useEmhareApi();
const auth = useEmhareAuth();
const toast = useToast();
const { showError } = useEmhareConfirm();
const academicPeriodContext = useAcademicPeriodContext();
const offerings = ref<AssessmentOfferingSummary[]>([]);
const rosterSources = ref<AssessmentRosterSource[]>([]);
const loading = ref(false);
const saving = ref(false);
const offeringModalOpen = ref(false);
const schemeModalOpen = ref(false);
const selectedOffering = ref<AssessmentOfferingSummary | null>(null);
const selectedRosterSourceKey = ref("");
const schemeName = ref("Standard assessment scheme");
const components = ref([
  newComponent("CWK", "Coursework", "COURSEWORK", 40, 100, 1),
  newComponent("EXAM", "Final examination", "FINAL_EXAM", 60, 100, 2),
]);

const availableSources = computed(() =>
  rosterSources.value.filter((source) => !source.offeringCreated),
);
const sourceItems = computed(() =>
  availableSources.value.map((source) => ({
    label: `${source.moduleCode} · ${source.moduleName} · ${source.academicPeriodCode} · ${source.eligibleStudentCount} students`,
    value: `${source.moduleId}:${source.academicPeriodId}`,
  })),
);
const approvedCount = computed(
  () =>
    offerings.value.filter((item) =>
      item.schemes.some((scheme) => scheme.status === "APPROVED"),
    ).length,
);
const draftCount = computed(
  () =>
    offerings.value.filter((item) =>
      item.schemes.some((scheme) => scheme.status === "DRAFT"),
    ).length,
);

onMounted(loadWorkspace);
watch(academicPeriodContext.selectedAcademicPeriodId, () => void loadWorkspace());

function newComponent(
  code: string,
  name: string,
  componentType: ComponentType,
  weightPercent: number,
  maximumMark: number,
  sortOrder: number,
) {
  const opens = new Date();
  opens.setHours(0, 0, 0, 0);
  const closes = new Date();
  closes.setDate(closes.getDate() + 30);
  closes.setHours(23, 59, 0, 0);
  return {
    code,
    name,
    componentType,
    weightPercent,
    maximumMark,
    sortOrder,
    captureOpensAt: localDateTime(opens),
    captureClosesAt: localDateTime(closes),
  };
}
function localDateTime(date: Date) {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 16);
}
function totalWeight() {
  return components.value.reduce(
    (total, component) => total + Number(component.weightPercent || 0),
    0,
  );
}

async function loadWorkspace() {
  loading.value = true;
  try {
    const [offeringResponse, rosterSourceResponse] = await Promise.all([
      api.request<AssessmentOfferingSummary[]>(
        "/api/assessment-results/offerings",
      ),
      api.request<AssessmentRosterSource[]>(
        "/api/assessment-results/roster-sources",
      ),
    ]);
    offerings.value = offeringResponse.filter(offering => academicPeriodContext.matchesAcademicPeriod(offering));
    rosterSources.value = rosterSourceResponse.filter(source => academicPeriodContext.matchesAcademicPeriod(source));
  } catch (error) {
    await showError(
      "Assessment setup could not be loaded",
      api.errorMessage(error),
    );
  } finally {
    loading.value = false;
  }
}

async function createOffering() {
  const source = availableSources.value.find(
    (item) =>
      `${item.moduleId}:${item.academicPeriodId}` ===
      selectedRosterSourceKey.value,
  );
  const instructorUserId = auth.currentUserProfile.value?.user.id;
  if (!source || !instructorUserId)
    return showError(
      "Offering details are incomplete",
      "Select a confirmed registration roster and ensure your user profile is available.",
    );
  saving.value = true;
  try {
    await api.request("/api/assessment-results/offerings", {
      method: "POST",
      body: {
        moduleId: source.moduleId,
        academicPeriodId: source.academicPeriodId,
        assignedInstructorUserId: instructorUserId,
      },
    });
    offeringModalOpen.value = false;
    await loadWorkspace();
    toast.add({
      title: "Module offering created",
      description: `${source.moduleCode} is ready for assessment scheme setup.`,
      color: "success",
    });
  } catch (error) {
    await showError(
      "Module offering could not be created",
      api.errorMessage(error),
    );
  } finally {
    saving.value = false;
  }
}

function openScheme(offering: AssessmentOfferingSummary) {
  selectedOffering.value = offering;
  schemeName.value = `${offering.moduleCode} assessment scheme`;
  components.value = [
    newComponent("CWK", "Coursework", "COURSEWORK", 40, 100, 1),
    newComponent("EXAM", "Final examination", "FINAL_EXAM", 60, 100, 2),
  ];
  schemeModalOpen.value = true;
}

async function createScheme() {
  if (!selectedOffering.value || totalWeight() !== 100)
    return showError(
      "Weights must total 100%",
      `Current total is ${totalWeight()}%.`,
    );
  saving.value = true;
  try {
    await api.request(
      `/api/assessment-results/offerings/${selectedOffering.value.id}/schemes`,
      {
        method: "POST",
        body: {
          name: schemeName.value,
          components: components.value.map((component) => ({
            ...component,
            captureOpensAt: new Date(component.captureOpensAt).toISOString(),
            captureClosesAt: new Date(component.captureClosesAt).toISOString(),
          })),
        },
      },
    );
    schemeModalOpen.value = false;
    await loadWorkspace();
    toast.add({
      title: "Draft scheme created",
      description: "Review the rule before approval.",
      color: "success",
    });
  } catch (error) {
    await showError(
      "Assessment scheme could not be created",
      api.errorMessage(error),
    );
  } finally {
    saving.value = false;
  }
}

async function approveScheme(offering: AssessmentOfferingSummary) {
  const scheme = offering.schemes.find((item) => item.status === "DRAFT");
  if (!scheme) return;
  const result = await Swal.fire({
    title: "Approve assessment scheme?",
    text: "Approval locks component weights, maximum marks, and capture windows. A new version is required for later rule changes.",
    icon: "question",
    input: "textarea",
    inputLabel: "Approval reason",
    inputPlaceholder: "Record board or delegated approval evidence.",
    showCancelButton: true,
    confirmButtonText: "Approve scheme",
    confirmButtonColor: "#006633",
    inputValidator: (value) =>
      value.trim() ? undefined : "An approval reason is required.",
  });
  if (!result.isConfirmed || !result.value?.trim()) return;
  try {
    await api.request(`/api/assessment-results/schemes/${scheme.id}/approve`, {
      method: "POST",
      body: { expectedVersion: scheme.version, reason: result.value.trim() },
    });
    await loadWorkspace();
    toast.add({
      title: "Assessment scheme approved",
      description: `${offering.moduleCode} is open for controlled mark capture.`,
      color: "success",
    });
  } catch (error) {
    await showError(
      "Scheme approval could not be recorded",
      api.errorMessage(error),
    );
  }
}
</script>

<template>
  <UDashboardPanel>
    <template #header
      ><UDashboardNavbar title="Assessment scheme setup"
        ><template #leading><UDashboardSidebarCollapse /></template
        ><template #right
          ><UButton
            label="Create Module offering"
            icon="i-lucide-plus"
            @click="offeringModalOpen = true" /><UButton
            label="Refresh"
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="outline"
            :loading="loading"
            @click="loadWorkspace" /></template></UDashboardNavbar
    ></template>
    <template #body>
      <div class="space-y-5 p-4 sm:p-6">
        <UAlert
          color="primary"
          variant="soft"
          icon="i-lucide-shield-check"
          title="Versioned assessment rules"
          description="Only confirmed registration rosters can become Module offerings. Approved component weights, maxima, and capture windows are locked as a reproducible rule version."
        />
        <section class="grid gap-3 sm:grid-cols-3">
          <UCard :ui="{ body: 'p-4' }"
            ><p class="text-xs uppercase text-muted">Offerings</p>
            <p class="mt-2 text-2xl font-semibold">
              {{ offerings.length }}
            </p></UCard
          ><UCard :ui="{ body: 'p-4' }"
            ><p class="text-xs uppercase text-success">Approved</p>
            <p class="mt-2 text-2xl font-semibold">
              {{ approvedCount }}
            </p></UCard
          ><UCard :ui="{ body: 'p-4' }"
            ><p class="text-xs uppercase text-warning">Draft review</p>
            <p class="mt-2 text-2xl font-semibold">{{ draftCount }}</p></UCard
          >
        </section>
        <EmharePaginatedCollection
          :items="offerings"
          v-slot="{ items: paginatedOfferings }"
        >
          <div class="grid gap-4 xl:grid-cols-2">
            <UCard
              v-for="offering in paginatedOfferings"
              :key="offering.id"
              :ui="{ body: 'p-4' }"
            >
              <div class="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p class="text-xs font-medium text-primary">
                    {{ offering.academicPeriodCode }}
                  </p>
                  <h2 class="mt-1 text-base font-semibold">
                    {{ offering.moduleCode }} · {{ offering.moduleName }}
                  </h2>
                  <p class="mt-1 text-sm text-muted">
                    {{ offering.rosterCount }} eligible student{{
                      offering.rosterCount === 1 ? "" : "s"
                    }}
                  </p>
                </div>
                <UBadge
                  :label="offering.status"
                  :color="offering.status === 'ACTIVE' ? 'success' : 'neutral'"
                  variant="subtle"
                />
              </div>
              <EmharePaginatedCollection v-if="offering.schemes.length" :items="offering.schemes" :initial-page-size="5" v-slot="{ items: paginatedSchemes }">
              <div class="mt-4 space-y-3">
                <div
                  v-for="scheme in paginatedSchemes"
                  :key="scheme.id"
                  class="rounded-md border border-muted p-3"
                >
                  <div class="flex items-center justify-between gap-2">
                    <p class="font-medium">
                      v{{ scheme.schemeVersion }} · {{ scheme.name }}
                    </p>
                    <UBadge
                      :label="scheme.status"
                      :color="
                        scheme.status === 'APPROVED'
                          ? 'success'
                          : scheme.status === 'DRAFT'
                            ? 'warning'
                            : 'neutral'
                      "
                      variant="subtle"
                    />
                  </div>
                  <EmharePaginatedCollection :items="scheme.components" :initial-page-size="5" v-slot="{ items: paginatedComponents }">
                  <div class="mt-2 flex flex-wrap gap-2">
                    <UBadge
                      v-for="component in paginatedComponents"
                      :key="component.id"
                      :label="`${component.code} ${component.weightPercent}%`"
                      color="neutral"
                      variant="outline"
                    />
                  </div>
                  </EmharePaginatedCollection>
                </div>
              </div>
              </EmharePaginatedCollection>
              <div class="mt-4 flex gap-2">
                <UButton
                  v-if="
                    !offering.schemes.some(
                      (s: AssessmentSchemeSummary) => s.status === 'DRAFT',
                    )
                  "
                  label="New scheme version"
                  icon="i-lucide-list-plus"
                  color="neutral"
                  variant="outline"
                  @click="openScheme(offering)"
                /><UButton
                  v-if="
                    offering.schemes.some(
                      (s: AssessmentSchemeSummary) => s.status === 'DRAFT',
                    )
                  "
                  label="Approve draft"
                  icon="i-lucide-badge-check"
                  @click="approveScheme(offering)"
                />
              </div>
            </UCard>
          </div>
        </EmharePaginatedCollection>
        <UAlert
          v-if="!loading && !offerings.length"
          color="neutral"
          variant="soft"
          title="No assessment offerings"
          description="Create an offering from a confirmed registration roster."
        />
      </div>
    </template>
  </UDashboardPanel>

  <EmhareRecordDrawer
    v-model:open="offeringModalOpen"
    title="Create Module offering"
    description="Use an authoritative confirmed-registration roster."
    ><template #body
      ><div class="space-y-4">
        <UFormField label="Confirmed roster" required
          ><USelect
            v-model="selectedRosterSourceKey"
            :items="sourceItems"
            class="w-full"
            placeholder="Select Module and academic period" /></UFormField
        ><UAlert
          color="info"
          variant="soft"
          title="Instructor assignment"
          description="This offering is assigned to your operator profile. Delegated assignment will use the Core Identity user catalogue."
        /></div></template
    ><template #footer
      ><div class="flex w-full justify-end gap-2">
        <UButton
          label="Cancel"
          color="neutral"
          variant="outline"
          @click="offeringModalOpen = false"
        /><EmhareGuidedActionButton
          label="Create offering"
          :loading="saving"
          guidance-title="Confirmed roster required"
          :guidance-instructions="selectedRosterSourceKey ? [] : ['Select the confirmed Module registration roster that will own this offering.']"
          @click="createOffering"
        /></div></template
  ></EmhareRecordDrawer>
  <EmhareRecordDrawer
    v-model:open="schemeModalOpen"
    presentation="page"
    title="Create assessment scheme"
    description="Define one complete version whose component weights total exactly 100%"
    width="xl"
    ><template #body
      ><div class="space-y-4">
        <UFormField label="Scheme name" required
          ><UInput v-model="schemeName" class="w-full"
        /></UFormField>
        <div
          v-for="(component, index) in components"
          :key="index"
          class="grid gap-3 rounded-md border border-muted p-3 sm:grid-cols-2 xl:grid-cols-4"
        >
          <UFormField label="Code"
            ><UInput v-model="component.code" /></UFormField
          ><UFormField label="Name"
            ><UInput v-model="component.name" /></UFormField
          ><UFormField label="Type"
            ><USelect
              v-model="component.componentType"
              :items="[
                'COURSEWORK',
                'PRACTICAL',
                'IN_CLASS_TEST',
                'FINAL_EXAM',
                'OTHER',
              ]" /></UFormField
          ><UFormField label="Weight %"
            ><UInput
              v-model.number="component.weightPercent"
              type="number"
              min="0.01"
              max="100"
              step="0.01" /></UFormField
          ><UFormField label="Maximum mark"
            ><UInput
              v-model.number="component.maximumMark"
              type="number"
              min="0.01"
              step="0.01" /></UFormField
          ><UFormField label="Capture opens"
            ><UInput
              v-model="component.captureOpensAt"
              type="datetime-local" /></UFormField
          ><UFormField label="Capture closes"
            ><UInput v-model="component.captureClosesAt" type="datetime-local"
          /></UFormField>
          <div class="flex items-end">
            <EmhareGuidedActionButton
              label="Remove"
              icon="i-lucide-trash-2"
              color="error"
              variant="soft"
              guidance-title="Assessment component cannot be removed"
              :guidance-instructions="components.length === 1 ? ['An assessment scheme must contain at least one component.'] : []"
              @click="components.splice(index, 1)"
            />
          </div>
        </div>
        <div class="flex items-center justify-between">
          <UButton
            label="Add component"
            icon="i-lucide-plus"
            color="neutral"
            variant="outline"
            @click="
              components.push(
                newComponent('', '', 'OTHER', 0, 100, components.length + 1),
              )
            "
          /><UBadge
            :label="`Total weight ${totalWeight()}%`"
            :color="totalWeight() === 100 ? 'success' : 'error'"
            variant="subtle"
          />
        </div></div></template
    ><template #footer
      ><div class="flex w-full justify-end gap-2">
        <UButton
          label="Cancel"
          color="neutral"
          variant="outline"
          @click="schemeModalOpen = false"
        /><UButton
          label="Save draft scheme"
          :loading="saving"
          @click="createScheme"
        /></div></template
  ></EmhareRecordDrawer>
</template>
