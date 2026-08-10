<script setup lang="ts">
import type { TableColumn } from "@nuxt/ui";
import Swal from "sweetalert2";
import type { FinanceFeeCatalogueRegister } from "@emhare/portal-shell/types/finance";
import type {
  DiningAttendantAssignmentSummary,
  DiningHallAssignmentRuleSummary,
  DiningHallSummary,
  DiningPlanMealSummary,
  DiningPlanSummary,
  DiningSetupRegister,
  MealOptionSummary,
  MealServiceTimeSummary,
} from "@emhare/portal-shell/types/dining";

definePageMeta({ layout: "dashboard" });

type DrawerMode = "hall" | "meal" | "service-time" | "plan" | "plan-meal" | "rule" | "attendant";
type CoreUser = { id: string; displayName: string; username: string; status: string };

const api = useEmhareApi();
const toast = useToast();
const { showError } = useEmhareConfirm();
const loading = ref(false);
const saving = ref(false);
const actionRecordId = ref<string | null>(null);
const loadError = ref("");
const dependencyWarnings = ref<string[]>([]);
const activeDataset = ref("halls");
const drawerOpen = ref(false);
const drawerMode = ref<DrawerMode>("hall");
const editingId = ref<string | null>(null);

const register = ref<DiningSetupRegister>({
  diningHalls: [], mealOptions: [], serviceTimes: [], diningPlans: [], planMeals: [],
  hallAssignmentRules: [], attendantAssignments: [],
});
const finance = ref<FinanceFeeCatalogueRegister>({ catalogues: [] });
const coreUsers = ref<CoreUser[]>([]);

const hallForm = reactive({ code: "", name: "", locationDescription: "", serviceCapacity: 1, active: true, expectedVersion: 0 });
const mealForm = reactive({ code: "", name: "", description: "", mealCategory: "BREAKFAST", active: true, expectedVersion: 0 });
const serviceTimeForm = reactive({ diningHallId: "", mealOptionId: "", dayOfWeek: 1, serviceOpensAt: "06:00", serviceClosesAt: "08:00", graceClosesAt: "08:15", active: true, expectedVersion: 0 });
const planForm = reactive({ code: "", planVersion: 1, name: "", description: "", financeFeeCatalogueId: "", validFrom: "", validUntil: "" });
const planMealForm = reactive({ diningPlanId: "", mealOptionId: "", servingsPerService: 1, serviceDays: ["1", "2", "3", "4", "5"] });
const ruleForm = reactive({ diningHallId: "", ruleDimension: "SURNAME_PREFIX", comparisonOperator: "STARTS_WITH", comparisonValue: "", priorityRank: 100, active: true, expectedVersion: 0 });
const attendantForm = reactive({ diningHallId: "", staffId: "", staffNumber: "", staffName: "", effectiveFrom: "", effectiveUntil: "", roleCode: "ATTENDANT", active: true, expectedVersion: 0 });

const hallColumns: TableColumn<DiningHallSummary>[] = [
  { accessorKey: "code", header: "Code" }, { accessorKey: "name", header: "Dining hall" },
  { accessorKey: "locationDescription", header: "Location" }, { accessorKey: "serviceCapacity", header: "Service capacity" },
  { accessorKey: "active", header: "Status" }, { accessorKey: "actions", header: "" },
];
const mealColumns: TableColumn<MealOptionSummary>[] = [
  { accessorKey: "code", header: "Code" }, { accessorKey: "name", header: "Meal option" },
  { accessorKey: "mealCategory", header: "Category" }, { accessorKey: "description", header: "Description" },
  { accessorKey: "active", header: "Status" }, { accessorKey: "actions", header: "" },
];
const serviceTimeColumns: TableColumn<MealServiceTimeSummary>[] = [
  { accessorKey: "diningHallCode", header: "Dining hall" }, { accessorKey: "mealOptionCode", header: "Meal" },
  { accessorKey: "dayOfWeek", header: "Day" }, { accessorKey: "serviceOpensAt", header: "Opens" },
  { accessorKey: "serviceClosesAt", header: "Closes" }, { accessorKey: "graceClosesAt", header: "Grace closes" },
  { accessorKey: "active", header: "Status" }, { accessorKey: "actions", header: "" },
];
const planColumns: TableColumn<DiningPlanSummary>[] = [
  { accessorKey: "code", header: "Code" }, { accessorKey: "planVersion", header: "Version" },
  { accessorKey: "name", header: "Dining plan" }, { accessorKey: "validFrom", header: "Effective window" },
  { accessorKey: "status", header: "Control state" }, { accessorKey: "actions", header: "" },
];
const planMealColumns: TableColumn<DiningPlanMealSummary>[] = [
  { accessorKey: "diningPlanCode", header: "Dining plan" }, { accessorKey: "mealOptionCode", header: "Meal" },
  { accessorKey: "servingsPerService", header: "Servings" }, { accessorKey: "serviceDays", header: "Entitled days" },
];
const ruleColumns: TableColumn<DiningHallAssignmentRuleSummary>[] = [
  { accessorKey: "priorityRank", header: "Priority" }, { accessorKey: "diningHallCode", header: "Dining hall" },
  { accessorKey: "ruleDimension", header: "Dimension" }, { accessorKey: "comparisonOperator", header: "Operator" },
  { accessorKey: "comparisonValue", header: "Match value" }, { accessorKey: "active", header: "Status" },
  { accessorKey: "actions", header: "" },
];
const attendantColumns: TableColumn<DiningAttendantAssignmentSummary>[] = [
  { accessorKey: "staffNumber", header: "Staff number" }, { accessorKey: "staffName", header: "Attendant" },
  { accessorKey: "diningHallCode", header: "Dining hall" }, { accessorKey: "roleCode", header: "Role" },
  { accessorKey: "effectiveFrom", header: "Effective window" }, { accessorKey: "active", header: "Status" },
  { accessorKey: "actions", header: "" },
];

const datasets = computed(() => [
  { label: "Dining halls", value: "halls", icon: "i-lucide-utensils", badge: register.value.diningHalls.length },
  { label: "Meal options", value: "meals", icon: "i-lucide-soup", badge: register.value.mealOptions.length },
  { label: "Service times", value: "service-times", icon: "i-lucide-clock-3", badge: register.value.serviceTimes.length },
  { label: "Dining plans", value: "plans", icon: "i-lucide-notebook-tabs", badge: register.value.diningPlans.length },
  { label: "Plan entitlements", value: "plan-meals", icon: "i-lucide-list-checks", badge: register.value.planMeals.length },
  { label: "Hall routing rules", value: "rules", icon: "i-lucide-route", badge: register.value.hallAssignmentRules.length },
  { label: "Attendant assignments", value: "attendants", icon: "i-lucide-contact-round", badge: register.value.attendantAssignments.length },
]);
const hallItems = computed(() => register.value.diningHalls.filter((item) => item.active).map((item) => ({ label: `${item.code} · ${item.name}`, value: item.id })));
const mealItems = computed(() => register.value.mealOptions.filter((item) => item.active).map((item) => ({ label: `${item.code} · ${item.name}`, value: item.id })));
const draftPlanItems = computed(() => register.value.diningPlans.filter((item) => item.status === "DRAFT").map((item) => ({ label: `${item.code} v${item.planVersion} · ${item.name}`, value: item.id })));
const diningFeeItems = computed(() => finance.value.catalogues.filter((item) => item.status === "ACTIVE" && item.chargeType === "DINING").map((item) => ({ label: `${item.code} · ${item.name}`, value: item.id })));
const staffItems = computed(() => coreUsers.value.filter((item) => item.status === "ACTIVE").map((item) => ({ label: `${item.displayName} · ${item.username}`, value: item.id, name: item.displayName, number: item.username })));
const activeHallCount = computed(() => register.value.diningHalls.filter((item) => item.active).length);
const activePlanCount = computed(() => register.value.diningPlans.filter((item) => item.status === "ACTIVE").length);
const activeAttendantCount = computed(() => register.value.attendantAssignments.filter((item) => item.active).length);
const drawerTitle = computed(() => `${editingId.value ? "Update" : "Create"} ${title(drawerMode.value)}`);

watch(() => attendantForm.staffId, (staffId) => {
  const staff = staffItems.value.find((item) => item.value === staffId);
  if (staff) Object.assign(attendantForm, { staffName: staff.name, staffNumber: staff.number });
});
onMounted(load);

async function load() {
  loading.value = true; loadError.value = ""; dependencyWarnings.value = [];
  const [setupResult, financeResult, usersResult] = await Promise.allSettled([
    api.request<DiningSetupRegister>("/api/dining/setup"),
    api.request<FinanceFeeCatalogueRegister>("/api/finance/fee-catalogues"),
    api.request<CoreUser[]>("/api/core/users"),
  ]);

  if (setupResult.status === "fulfilled") register.value = setupResult.value;
  else loadError.value = api.errorMessage(setupResult.reason, "Dining setup could not be loaded.");

  if (financeResult.status === "fulfilled") finance.value = financeResult.value;
  else {
    finance.value = { catalogues: [] };
    dependencyWarnings.value.push("Finance fee catalogue is temporarily unavailable. Dining setup remains available, but fee linking is disabled.");
  }

  if (usersResult.status === "fulfilled") coreUsers.value = usersResult.value;
  else {
    coreUsers.value = [];
    dependencyWarnings.value.push("Core user directory is temporarily unavailable. Dining setup remains available, but new attendant assignments are disabled.");
  }

  loading.value = false;
}

function openCreate(mode: DrawerMode) {
  editingId.value = null; drawerMode.value = mode;
  if (mode === "hall") Object.assign(hallForm, { code: "", name: "", locationDescription: "", serviceCapacity: 1, active: true, expectedVersion: 0 });
  if (mode === "meal") Object.assign(mealForm, { code: "", name: "", description: "", mealCategory: "BREAKFAST", active: true, expectedVersion: 0 });
  if (mode === "service-time") Object.assign(serviceTimeForm, { diningHallId: hallItems.value[0]?.value ?? "", mealOptionId: mealItems.value[0]?.value ?? "", dayOfWeek: 1, serviceOpensAt: "06:00", serviceClosesAt: "08:00", graceClosesAt: "08:15", active: true, expectedVersion: 0 });
  if (mode === "plan") Object.assign(planForm, { code: "", planVersion: 1, name: "", description: "", financeFeeCatalogueId: diningFeeItems.value[0]?.value ?? "", validFrom: "", validUntil: "" });
  if (mode === "plan-meal") Object.assign(planMealForm, { diningPlanId: draftPlanItems.value[0]?.value ?? "", mealOptionId: mealItems.value[0]?.value ?? "", servingsPerService: 1, serviceDays: ["1", "2", "3", "4", "5"] });
  if (mode === "rule") Object.assign(ruleForm, { diningHallId: hallItems.value[0]?.value ?? "", ruleDimension: "SURNAME_PREFIX", comparisonOperator: "STARTS_WITH", comparisonValue: "", priorityRank: 100, active: true, expectedVersion: 0 });
  if (mode === "attendant") Object.assign(attendantForm, { diningHallId: hallItems.value[0]?.value ?? "", staffId: staffItems.value[0]?.value ?? "", staffNumber: staffItems.value[0]?.number ?? "", staffName: staffItems.value[0]?.name ?? "", effectiveFrom: "", effectiveUntil: "", roleCode: "ATTENDANT", active: true, expectedVersion: 0 });
  drawerOpen.value = true;
}

function openEdit(mode: "hall" | "meal" | "service-time" | "rule" | "attendant", record: DiningHallSummary | MealOptionSummary | MealServiceTimeSummary | DiningHallAssignmentRuleSummary | DiningAttendantAssignmentSummary) {
  drawerMode.value = mode; editingId.value = record.id;
  if (mode === "hall") Object.assign(hallForm, record);
  if (mode === "meal") Object.assign(mealForm, record);
  if (mode === "service-time") Object.assign(serviceTimeForm, record);
  if (mode === "rule") Object.assign(ruleForm, record);
  if (mode === "attendant") Object.assign(attendantForm, record);
  drawerOpen.value = true;
}

async function saveRecord() {
  saving.value = true;
  try {
    const base = "/api/dining/setup";
    if (drawerMode.value === "hall") await api.request(`${base}/halls${editingId.value ? `/${editingId.value}` : ""}`, { method: editingId.value ? "PUT" : "POST", body: hallForm });
    if (drawerMode.value === "meal") await api.request(`${base}/meal-options${editingId.value ? `/${editingId.value}` : ""}`, { method: editingId.value ? "PUT" : "POST", body: mealForm });
    if (drawerMode.value === "service-time") await api.request(`${base}/service-times${editingId.value ? `/${editingId.value}` : ""}`, { method: editingId.value ? "PUT" : "POST", body: serviceTimeForm });
    if (drawerMode.value === "plan") await api.request(`${base}/plans`, { method: "POST", body: { ...planForm, financeFeeCatalogueId: planForm.financeFeeCatalogueId || null, validUntil: planForm.validUntil || null } });
    if (drawerMode.value === "plan-meal") await api.request(`${base}/plans/${planMealForm.diningPlanId}/meals`, { method: "POST", body: { mealOptionId: planMealForm.mealOptionId, servingsPerService: planMealForm.servingsPerService, monday: planMealForm.serviceDays.includes("1"), tuesday: planMealForm.serviceDays.includes("2"), wednesday: planMealForm.serviceDays.includes("3"), thursday: planMealForm.serviceDays.includes("4"), friday: planMealForm.serviceDays.includes("5"), saturday: planMealForm.serviceDays.includes("6"), sunday: planMealForm.serviceDays.includes("7") } });
    if (drawerMode.value === "rule") await api.request(`${base}/hall-assignment-rules${editingId.value ? `/${editingId.value}` : ""}`, { method: editingId.value ? "PUT" : "POST", body: ruleForm });
    if (drawerMode.value === "attendant") await api.request(`${base}/attendant-assignments${editingId.value ? `/${editingId.value}` : ""}`, { method: editingId.value ? "PUT" : "POST", body: { ...attendantForm, effectiveUntil: attendantForm.effectiveUntil || null } });
    drawerOpen.value = false; toast.add({ title: "Dining setup saved", color: "success", icon: "i-lucide-circle-check" }); await load();
  } catch (error) { await showError("Dining setup was not saved", api.errorMessage(error)); }
  finally { saving.value = false; }
}

async function transitionPlan(plan: DiningPlanSummary, targetStatus: "ACTIVE" | "RETIRED") {
  const result = await Swal.fire({ title: `${title(targetStatus)} dining plan`, input: "textarea", inputLabel: "Decision evidence", inputPlaceholder: "Record the operational reason and authority…", showCancelButton: true, confirmButtonText: title(targetStatus), confirmButtonColor: "#20743a", inputValidator: (value) => value.trim() ? undefined : "Decision evidence is required." });
  if (!result.isConfirmed) return;
  actionRecordId.value = plan.id;
  try {
    await api.request(`/api/dining/setup/plans/${plan.id}/transition`, { method: "POST", body: { targetStatus, reason: result.value, expectedVersion: plan.version } });
    toast.add({ title: `Dining plan ${targetStatus.toLowerCase()}`, color: "success" }); await load();
  } catch (error) { await showError("Plan transition failed", api.errorMessage(error)); }
  finally { actionRecordId.value = null; }
}

function title(value: string) { return value.replaceAll("-", "_").split("_").map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase()).join(" "); }
function statusTone(status: string): "success" | "warning" | "error" | "neutral" | "primary" { return ["ACTIVE"].includes(status) ? "success" : ["DRAFT"].includes(status) ? "warning" : ["INACTIVE", "RETIRED"].includes(status) ? "neutral" : "primary"; }
function dayLabel(day: number) { return ["", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"][day] ?? "Unknown"; }
</script>

<template>
  <UDashboardPanel id="dining-setup">
    <template #header>
      <UDashboardNavbar title="Dining setup">
        <template #leading><UDashboardSidebarCollapse /></template>
        <template #right><UButton label="Refresh" icon="i-lucide-refresh-cw" color="neutral" variant="outline" :loading="loading" @click="load" /></template>
      </UDashboardNavbar>
    </template>
    <template #body>
      <div class="space-y-5">
        <div class="grid gap-3 sm:grid-cols-3">
          <EmhareKpiCard label="Active dining halls" :value="activeHallCount" icon="i-lucide-utensils" tone="primary" />
          <EmhareKpiCard label="Active dining plans" :value="activePlanCount" icon="i-lucide-notebook-tabs" tone="success" />
          <EmhareKpiCard label="Active attendants" :value="activeAttendantCount" icon="i-lucide-contact-round" tone="neutral" />
        </div>
        <UAlert color="primary" variant="soft" icon="i-lucide-shield-check" title="Controlled dining configuration" description="Dining plans are versioned and maker-checker controlled. Hall routing and attendant assignments are explicit operational records, not free-text settings." />
        <UAlert v-if="loadError" color="error" variant="soft" icon="i-lucide-circle-alert" title="Dining setup unavailable" :description="loadError" />
        <UAlert v-if="dependencyWarnings.length" color="warning" variant="soft" icon="i-lucide-triangle-alert" title="Limited integration availability" :description="dependencyWarnings.join(' ')" />
        <UTabs v-model="activeDataset" :items="datasets" :content="false" color="primary" variant="pill" />

        <EmhareRegisterPanel v-if="activeDataset === 'halls'" title="Dining halls" description="Service locations and safe throughput capacity." :record-count="register.diningHalls.length">
          <template #actions><UButton label="Create dining hall" icon="i-lucide-plus" @click="openCreate('hall')" /></template>
          <EmharePaginatedTable :data="register.diningHalls" :columns="hallColumns" :loading="loading">
            <template #active-cell="{ row }"><EmhareStatusPill :label="row.original.active ? 'Active' : 'Inactive'" :tone="row.original.active ? 'success' : 'neutral'" /></template>
            <template #actions-cell="{ row }"><UButton label="Edit" icon="i-lucide-pencil" size="xs" color="neutral" variant="ghost" @click="openEdit('hall', row.original)" /></template>
          </EmharePaginatedTable>
        </EmhareRegisterPanel>

        <EmhareRegisterPanel v-if="activeDataset === 'meals'" title="Meal options" description="Governed meal catalogue used by plans and service sessions." :record-count="register.mealOptions.length">
          <template #actions><UButton label="Create meal option" icon="i-lucide-plus" @click="openCreate('meal')" /></template>
          <EmharePaginatedTable :data="register.mealOptions" :columns="mealColumns" :loading="loading">
            <template #mealCategory-cell="{ row }"><EmhareStatusPill :label="title(row.original.mealCategory)" tone="primary" /></template>
            <template #active-cell="{ row }"><EmhareStatusPill :label="row.original.active ? 'Active' : 'Inactive'" :tone="row.original.active ? 'success' : 'neutral'" /></template>
            <template #actions-cell="{ row }"><UButton label="Edit" icon="i-lucide-pencil" size="xs" color="neutral" variant="ghost" @click="openEdit('meal', row.original)" /></template>
          </EmharePaginatedTable>
        </EmhareRegisterPanel>

        <EmhareRegisterPanel v-if="activeDataset === 'service-times'" title="Service times" description="Hall-specific meal windows and controlled grace periods." :record-count="register.serviceTimes.length">
          <template #actions><EmhareGuidedActionButton label="Create service time" icon="i-lucide-plus" guidance-title="Meal service time setup required" :guidance-instructions="[...(!hallItems.length ? ['Create an active dining hall.'] : []), ...(!mealItems.length ? ['Create an active meal option.'] : [])]" @click="openCreate('service-time')" /></template>
          <EmharePaginatedTable :data="register.serviceTimes" :columns="serviceTimeColumns" :loading="loading">
            <template #dayOfWeek-cell="{ row }">{{ dayLabel(row.original.dayOfWeek) }}</template>
            <template #active-cell="{ row }"><EmhareStatusPill :label="row.original.active ? 'Active' : 'Inactive'" :tone="row.original.active ? 'success' : 'neutral'" /></template>
            <template #actions-cell="{ row }"><UButton label="Edit" icon="i-lucide-pencil" size="xs" color="neutral" variant="ghost" @click="openEdit('service-time', row.original)" /></template>
          </EmharePaginatedTable>
        </EmhareRegisterPanel>

        <EmhareRegisterPanel v-if="activeDataset === 'plans'" title="Dining plans" description="Versioned student entitlements with optional Finance fee linkage." :record-count="register.diningPlans.length">
          <template #actions><UButton label="Prepare dining plan" icon="i-lucide-plus" @click="openCreate('plan')" /></template>
          <EmharePaginatedTable :data="register.diningPlans" :columns="planColumns" :loading="loading">
            <template #validFrom-cell="{ row }">{{ row.original.validFrom }} – {{ row.original.validUntil || 'Open-ended' }}</template>
            <template #status-cell="{ row }"><EmhareStatusPill :label="title(row.original.status)" :tone="statusTone(row.original.status)" /></template>
            <template #actions-cell="{ row }"><div class="flex justify-end gap-1"><UButton v-if="row.original.status === 'DRAFT'" label="Activate" icon="i-lucide-stamp" size="xs" :loading="actionRecordId === row.original.id" @click="transitionPlan(row.original, 'ACTIVE')" /><UButton v-if="row.original.status === 'ACTIVE'" label="Retire" icon="i-lucide-archive" size="xs" color="neutral" variant="ghost" :loading="actionRecordId === row.original.id" @click="transitionPlan(row.original, 'RETIRED')" /></div></template>
          </EmharePaginatedTable>
        </EmhareRegisterPanel>

        <EmhareRegisterPanel v-if="activeDataset === 'plan-meals'" title="Plan entitlements" description="Meals, allowed weekdays, and servings for each draft plan." :record-count="register.planMeals.length">
          <template #actions><EmhareGuidedActionButton label="Add entitlement" icon="i-lucide-plus" guidance-title="Dining entitlement setup required" :guidance-instructions="[...(!draftPlanItems.length ? ['Create a draft dining plan.'] : []), ...(!mealItems.length ? ['Create an active meal option.'] : [])]" @click="openCreate('plan-meal')" /></template>
          <EmharePaginatedTable :data="register.planMeals" :columns="planMealColumns" :loading="loading"><template #serviceDays-cell="{ row }">{{ row.original.serviceDays.map(dayLabel).join(', ') }}</template></EmharePaginatedTable>
        </EmhareRegisterPanel>

        <EmhareRegisterPanel v-if="activeDataset === 'rules'" title="Hall routing rules" description="Priority-ordered defaults for assigning student groups to dining halls." :record-count="register.hallAssignmentRules.length">
          <template #actions><EmhareGuidedActionButton label="Create routing rule" icon="i-lucide-plus" guidance-title="Dining routing setup required" :guidance-instructions="hallItems.length ? [] : ['Create an active dining hall before defining routing rules.']" @click="openCreate('rule')" /></template>
          <EmharePaginatedTable :data="register.hallAssignmentRules" :columns="ruleColumns" :loading="loading">
            <template #ruleDimension-cell="{ row }">{{ title(row.original.ruleDimension) }}</template><template #comparisonOperator-cell="{ row }">{{ title(row.original.comparisonOperator) }}</template>
            <template #active-cell="{ row }"><EmhareStatusPill :label="row.original.active ? 'Active' : 'Inactive'" :tone="row.original.active ? 'success' : 'neutral'" /></template>
            <template #actions-cell="{ row }"><UButton label="Edit" icon="i-lucide-pencil" size="xs" color="neutral" variant="ghost" @click="openEdit('rule', row.original)" /></template>
          </EmharePaginatedTable>
        </EmhareRegisterPanel>

        <EmhareRegisterPanel v-if="activeDataset === 'attendants'" title="Attendant assignments" description="Named operational responsibility by hall, role, and effective window." :record-count="register.attendantAssignments.length">
          <template #actions><EmhareGuidedActionButton label="Assign attendant" icon="i-lucide-plus" guidance-title="Dining attendant setup required" :guidance-instructions="[...(!hallItems.length ? ['Create an active dining hall.'] : []), ...(!staffItems.length ? ['Create an active staff profile that can be assigned as an attendant.'] : [])]" @click="openCreate('attendant')" /></template>
          <EmharePaginatedTable :data="register.attendantAssignments" :columns="attendantColumns" :loading="loading">
            <template #roleCode-cell="{ row }">{{ title(row.original.roleCode) }}</template><template #effectiveFrom-cell="{ row }">{{ row.original.effectiveFrom }} – {{ row.original.effectiveUntil || 'Open-ended' }}</template>
            <template #active-cell="{ row }"><EmhareStatusPill :label="row.original.active ? 'Active' : 'Inactive'" :tone="row.original.active ? 'success' : 'neutral'" /></template>
            <template #actions-cell="{ row }"><UButton label="Edit" icon="i-lucide-pencil" size="xs" color="neutral" variant="ghost" @click="openEdit('attendant', row.original)" /></template>
          </EmharePaginatedTable>
        </EmhareRegisterPanel>
      </div>
    </template>
  </UDashboardPanel>

  <EmhareRecordDrawer v-model:open="drawerOpen" :title="drawerTitle" description="Complete the controlled record. Updates require the current optimistic-lock version." :busy="saving" :submit-disabled="saving" @submit="saveRecord">
    <div v-if="drawerMode === 'hall'" class="grid gap-4 sm:grid-cols-2"><UFormField label="Code" required><UInput v-model="hallForm.code" class="w-full" /></UFormField><UFormField label="Service capacity" required><UInput v-model.number="hallForm.serviceCapacity" type="number" min="1" class="w-full" /></UFormField><UFormField label="Name" required class="sm:col-span-2"><UInput v-model="hallForm.name" class="w-full" /></UFormField><UFormField label="Location" required class="sm:col-span-2"><UTextarea v-model="hallForm.locationDescription" class="w-full" /></UFormField><USwitch v-if="editingId" v-model="hallForm.active" label="Active" /></div>
    <div v-else-if="drawerMode === 'meal'" class="grid gap-4 sm:grid-cols-2"><UFormField label="Code" required><UInput v-model="mealForm.code" class="w-full" /></UFormField><UFormField label="Category" required><USelect v-model="mealForm.mealCategory" :items="['BREAKFAST','LUNCH','DINNER','OTHER'].map(value => ({ label: title(value), value }))" value-key="value" class="w-full" /></UFormField><UFormField label="Name" required class="sm:col-span-2"><UInput v-model="mealForm.name" class="w-full" /></UFormField><UFormField label="Description" class="sm:col-span-2"><UTextarea v-model="mealForm.description" class="w-full" /></UFormField><USwitch v-if="editingId" v-model="mealForm.active" label="Active" /></div>
    <div v-else-if="drawerMode === 'service-time'" class="grid gap-4 sm:grid-cols-2"><UFormField label="Dining hall" required><USelect v-model="serviceTimeForm.diningHallId" :items="hallItems" value-key="value" class="w-full" /></UFormField><UFormField label="Meal option" required><USelect v-model="serviceTimeForm.mealOptionId" :items="mealItems" value-key="value" class="w-full" /></UFormField><UFormField label="Day" required><USelect v-model="serviceTimeForm.dayOfWeek" :items="[1,2,3,4,5,6,7].map(value => ({ label: dayLabel(value), value }))" value-key="value" class="w-full" /></UFormField><div /><UFormField label="Opens" required><UInput v-model="serviceTimeForm.serviceOpensAt" type="time" class="w-full" /></UFormField><UFormField label="Closes" required><UInput v-model="serviceTimeForm.serviceClosesAt" type="time" class="w-full" /></UFormField><UFormField label="Grace closes" required><UInput v-model="serviceTimeForm.graceClosesAt" type="time" class="w-full" /></UFormField><USwitch v-if="editingId" v-model="serviceTimeForm.active" label="Active" /></div>
    <div v-else-if="drawerMode === 'plan'" class="grid gap-4 sm:grid-cols-2"><UAlert color="warning" variant="soft" icon="i-lucide-shield-check" title="Maker-checker controlled" description="Another authorised operator must activate this prepared plan after entitlements are added." class="sm:col-span-2" /><UFormField label="Code" required><UInput v-model="planForm.code" class="w-full" /></UFormField><UFormField label="Version" required><UInput v-model.number="planForm.planVersion" type="number" min="1" class="w-full" /></UFormField><UFormField label="Name" required class="sm:col-span-2"><UInput v-model="planForm.name" class="w-full" /></UFormField><UFormField label="Finance fee" hint="Optional" class="sm:col-span-2"><USelect v-model="planForm.financeFeeCatalogueId" :items="diningFeeItems" value-key="value" class="w-full" placeholder="No fee linked" /></UFormField><UFormField label="Valid from" required><UInput v-model="planForm.validFrom" type="date" class="w-full" /></UFormField><UFormField label="Valid until"><UInput v-model="planForm.validUntil" type="date" class="w-full" /></UFormField><UFormField label="Description" class="sm:col-span-2"><UTextarea v-model="planForm.description" class="w-full" /></UFormField></div>
    <div v-else-if="drawerMode === 'plan-meal'" class="grid gap-4 sm:grid-cols-2"><UFormField label="Draft dining plan" required class="sm:col-span-2"><USelect v-model="planMealForm.diningPlanId" :items="draftPlanItems" value-key="value" class="w-full" /></UFormField><UFormField label="Meal option" required><USelect v-model="planMealForm.mealOptionId" :items="mealItems" value-key="value" class="w-full" /></UFormField><UFormField label="Servings per service" required><UInput v-model.number="planMealForm.servingsPerService" type="number" min="1" class="w-full" /></UFormField><UFormField label="Entitled service days" required class="sm:col-span-2"><UCheckboxGroup v-model="planMealForm.serviceDays" :items="[1,2,3,4,5,6,7].map(value => ({ label: dayLabel(value), value: String(value) }))" orientation="horizontal" /></UFormField></div>
    <div v-else-if="drawerMode === 'rule'" class="grid gap-4 sm:grid-cols-2"><UFormField label="Dining hall" required class="sm:col-span-2"><USelect v-model="ruleForm.diningHallId" :items="hallItems" value-key="value" class="w-full" /></UFormField><UFormField label="Student dimension" required><USelect v-model="ruleForm.ruleDimension" :items="['SURNAME_PREFIX','RESIDENCE_HALL','PROGRAMME','STUDENT_GROUP'].map(value => ({ label: title(value), value }))" value-key="value" class="w-full" /></UFormField><UFormField label="Operator" required><USelect v-model="ruleForm.comparisonOperator" :items="['EQUALS','STARTS_WITH','IN'].map(value => ({ label: title(value), value }))" value-key="value" class="w-full" /></UFormField><UFormField label="Match value" required><UInput v-model="ruleForm.comparisonValue" class="w-full" /></UFormField><UFormField label="Priority" required><UInput v-model.number="ruleForm.priorityRank" type="number" min="1" class="w-full" /></UFormField><USwitch v-if="editingId" v-model="ruleForm.active" label="Active" /></div>
    <div v-else class="grid gap-4 sm:grid-cols-2"><UFormField label="Dining hall" required><USelect v-model="attendantForm.diningHallId" :items="hallItems" value-key="value" class="w-full" /></UFormField><UFormField label="Staff member" required><USelect v-model="attendantForm.staffId" :items="staffItems" value-key="value" class="w-full" /></UFormField><UFormField label="Staff number" required><UInput v-model="attendantForm.staffNumber" class="w-full" /></UFormField><UFormField label="Staff name" required><UInput v-model="attendantForm.staffName" class="w-full" /></UFormField><UFormField label="Role" required><USelect v-model="attendantForm.roleCode" :items="['ATTENDANT','SUPERVISOR','MANAGER'].map(value => ({ label: title(value), value }))" value-key="value" class="w-full" /></UFormField><div /><UFormField label="Effective from" required><UInput v-model="attendantForm.effectiveFrom" type="date" class="w-full" /></UFormField><UFormField label="Effective until"><UInput v-model="attendantForm.effectiveUntil" type="date" class="w-full" /></UFormField><USwitch v-if="editingId" v-model="attendantForm.active" label="Active" /></div>
  </EmhareRecordDrawer>
</template>
