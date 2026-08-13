<script setup lang="ts">
import Swal from "sweetalert2";
import type { GradingSchemeSummary } from "@emhare/portal-shell/types/assessment";
definePageMeta({ layout: "dashboard" });
const api = useEmhareApi();
const toast = useToast();
const { showError } = useEmhareConfirm();
const schemes = ref<GradingSchemeSummary[]>([]);
const loading = ref(false);
const saving = ref(false);
const modalOpen = ref(false);
const form = reactive({ code: "STANDARD", name: "Standard grading scheme" });
const bands = ref(defaultBands());
function defaultBands() {
  return [
    {
      minimumMark: 0,
      maximumMark: 49.99,
      grade: "F",
      remark: "Fail",
      passing: false,
      sortOrder: 1,
    },
    {
      minimumMark: 50,
      maximumMark: 59.99,
      grade: "P",
      remark: "Pass",
      passing: true,
      sortOrder: 2,
    },
    {
      minimumMark: 60,
      maximumMark: 69.99,
      grade: "C",
      remark: "Credit",
      passing: true,
      sortOrder: 3,
    },
    {
      minimumMark: 70,
      maximumMark: 79.99,
      grade: "D",
      remark: "Distinction",
      passing: true,
      sortOrder: 4,
    },
    {
      minimumMark: 80,
      maximumMark: 100,
      grade: "HD",
      remark: "High distinction",
      passing: true,
      sortOrder: 5,
    },
  ];
}
onMounted(load);
async function load() {
  loading.value = true;
  try {
    schemes.value = await api.request("/api/results/grading-schemes");
  } catch (error) {
    await showError(
      "Grading schemes could not be loaded",
      api.errorMessage(error),
    );
  } finally {
    loading.value = false;
  }
}
function openCreate() {
  form.code = "STANDARD";
  form.name = "Standard grading scheme";
  bands.value = defaultBands();
  modalOpen.value = true;
}
async function create() {
  saving.value = true;
  try {
    await api.request("/api/results/grading-schemes", {
      method: "POST",
      body: { ...form, bands: bands.value },
    });
    modalOpen.value = false;
    await load();
    toast.add({
      title: "Draft grading scheme created",
      description: "Review complete mark coverage before approval.",
      color: "success",
    });
  } catch (error) {
    await showError(
      "Grading scheme could not be created",
      api.errorMessage(error),
    );
  } finally {
    saving.value = false;
  }
}
async function approve(scheme: GradingSchemeSummary) {
  const result = await Swal.fire({
    title: "Approve grading scheme?",
    text: "Approval locks every grade band. Future changes require a new scheme version.",
    icon: "question",
    input: "textarea",
    inputLabel: "Approval reason",
    showCancelButton: true,
    confirmButtonText: "Approve scheme",
    confirmButtonColor: "#006633",
    inputValidator: (value) =>
      value.trim() ? undefined : "An approval reason is required.",
  });
  if (!result.isConfirmed || !result.value?.trim()) return;
  try {
    await api.request(`/api/results/grading-schemes/${scheme.id}/approve`, {
      method: "POST",
      body: { expectedVersion: scheme.version, reason: result.value.trim() },
    });
    await load();
    toast.add({
      title: "Grading scheme approved",
      description: `${scheme.code} v${scheme.schemeVersion} is available for result calculation.`,
      color: "success",
    });
  } catch (error) {
    await showError(
      "Grading scheme could not be approved",
      api.errorMessage(error),
    );
  }
}
</script>
<template>
  <UDashboardPanel
    ><template #header
      ><UDashboardNavbar title="Grading scheme governance"
        ><template #leading><UDashboardSidebarCollapse /></template
        ><template #right
          ><UButton
            label="New grading scheme"
            icon="i-lucide-plus"
            @click="openCreate" /><UButton
            label="Refresh"
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="outline"
            :loading="loading"
            @click="load" /></template></UDashboardNavbar></template
    ><template #body
      ><div class="space-y-5 p-4 sm:p-6">
        <UAlert
          color="primary"
          variant="soft"
          icon="i-lucide-graduation-cap"
          title="Versioned grading policy"
          description="Approved schemes must cover every two-decimal mark from 0.00 to 100.00 without gaps or overlaps. Approval locks the bands as result evidence."
        />
        <EmharePaginatedCollection :items="schemes" v-slot="{ items: paginatedSchemes }">
        <div class="grid gap-4 xl:grid-cols-2">
          <UCard
            v-for="scheme in paginatedSchemes"
            :key="scheme.id"
            :ui="{ body: 'p-4' }"
            ><div class="flex items-start justify-between gap-3">
              <div>
                <p class="text-xs font-medium text-primary">
                  {{ scheme.code }} · version {{ scheme.schemeVersion }}
                </p>
                <h2 class="mt-1 font-semibold">{{ scheme.name }}</h2>
              </div>
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
            <EmharePaginatedCollection :items="scheme.bands" :initial-page-size="5" v-slot="{ items: paginatedBands }">
            <div class="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-3">
              <div
                v-for="band in paginatedBands"
                :key="band.id"
                class="rounded-md border border-muted p-2"
              >
                <p class="font-semibold">{{ band.grade }}</p>
                <p class="text-xs text-muted">
                  {{ band.minimumMark }}–{{ band.maximumMark }} ·
                  {{ band.remark }}
                </p>
              </div>
            </div>
            </EmharePaginatedCollection>
            <div v-if="scheme.status === 'DRAFT'" class="mt-4 flex justify-end">
              <UButton
                label="Approve scheme"
                icon="i-lucide-badge-check"
                @click="approve(scheme)"
              /></div
          ></UCard>
        </div>
        </EmharePaginatedCollection>
        <UAlert
          v-if="!loading && !schemes.length"
          color="neutral"
          variant="soft"
          title="No grading schemes"
          description="Create and approve the first institutional grading policy."
        /></div></template></UDashboardPanel
  ><EmhareRecordDrawer presentation="page"
    v-model:open="modalOpen"
    title="Create grading scheme"
    description="Define complete, non-overlapping bands"
    width="xl"
    ><template #body
      ><div class="space-y-4">
        <div class="grid gap-3 sm:grid-cols-2">
          <UFormField label="Code"
            ><UInput v-model="form.code" class="w-full" /></UFormField
          ><UFormField label="Name"
            ><UInput v-model="form.name" class="w-full"
          /></UFormField>
        </div>
        <div
          v-for="(band, index) in bands"
          :key="index"
          class="grid gap-2 rounded-md border border-muted p-3 sm:grid-cols-3 xl:grid-cols-6"
        >
          <UFormField label="Minimum"
            ><UInput
              v-model.number="band.minimumMark"
              type="number"
              step="0.01" /></UFormField
          ><UFormField label="Maximum"
            ><UInput
              v-model.number="band.maximumMark"
              type="number"
              step="0.01" /></UFormField
          ><UFormField label="Grade"><UInput v-model="band.grade" /></UFormField
          ><UFormField label="Remark"
            ><UInput v-model="band.remark" /></UFormField
          ><UFormField label="Passing"
            ><USwitch v-model="band.passing"
          /></UFormField>
          <div class="flex items-end">
            <EmhareGuidedActionButton
              label="Remove"
              color="error"
              variant="soft"
              guidance-title="Grade band cannot be removed"
              :guidance-instructions="bands.length === 1 ? ['A grading scheme must retain at least one grade band.'] : []"
              @click="bands.splice(index, 1)"
            />
          </div>
        </div>
        <UButton
          label="Add band"
          icon="i-lucide-plus"
          color="neutral"
          variant="outline"
          @click="
            bands.push({
              minimumMark: 0,
              maximumMark: 100,
              grade: '',
              remark: '',
              passing: false,
              sortOrder: bands.length + 1,
            })
          "
        /></div></template
    ><template #footer
      ><div class="flex w-full justify-end gap-2">
        <UButton
          label="Cancel"
          color="neutral"
          variant="outline"
          @click="modalOpen = false"
        /><UButton
          label="Save draft"
          :loading="saving"
          @click="create"
        /></div></template
  ></EmhareRecordDrawer>
</template>
