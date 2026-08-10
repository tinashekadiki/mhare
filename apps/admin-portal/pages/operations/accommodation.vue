<script setup lang="ts">
import type { TableColumn } from "@nuxt/ui";
import Swal from "sweetalert2";
import type {
  AccommodationApplicationPeriodSummary,
  AccommodationPeriodStatus,
  AccommodationPremiseSummary,
  AccommodationRoomCondition,
  AccommodationRoomSummary,
  AccommodationRoomTypeSummary,
  AccommodationSetupRegister,
  ResidenceHallSummary,
  ResidentGenderPolicy,
} from "@emhare/portal-shell/types/accommodation";

definePageMeta({ layout: "dashboard" });

type DrawerMode =
  "premise" | "room-type" | "residence-hall" | "room" | "application-period";

const api = useEmhareApi();
const toast = useToast();
const { showError } = useEmhareConfirm();
const academicSetup = useAcademicSetup();
const loading = ref(false);
const saving = ref(false);
const actionRecordId = ref<string | null>(null);
const loadError = ref("");
const activeDataset = ref("premises");
const drawerOpen = ref(false);
const drawerMode = ref<DrawerMode>("premise");
const editingId = ref<string | null>(null);

const register = ref<AccommodationSetupRegister>({
  premises: [],
  roomTypes: [],
  residenceHalls: [],
  rooms: [],
  applicationPeriods: [],
});

const premiseForm = reactive({
  code: "",
  name: "",
  addressLine: "",
  suburb: "",
  landlordName: "",
  contactDetails: "",
  active: true,
  expectedVersion: 0,
});
const roomTypeForm = reactive({
  code: "",
  name: "",
  description: "",
  defaultCapacity: 1,
  active: true,
  expectedVersion: 0,
});
const residenceHallForm = reactive({
  premiseId: "",
  code: "",
  name: "",
  residentGenderPolicy: "ANY" as ResidentGenderPolicy,
  wardenName: "",
  wardenContact: "",
  active: true,
  expectedVersion: 0,
});
const roomForm = reactive({
  residenceHallId: "",
  roomTypeId: "",
  code: "",
  floorLabel: "",
  capacity: 1,
  accessibilityReady: false,
  conditionStatus: "AVAILABLE" as AccommodationRoomCondition,
  conditionNotes: "",
  reservedForGroupId: "",
  active: true,
  expectedVersion: 0,
});
const applicationPeriodForm = reactive({
  academicPeriodId: "",
  academicPeriodCode: "",
  code: "",
  name: "",
  applicationsOpenAt: "",
  applicationsCloseAt: "",
  occupancyStartsOn: "",
  occupancyEndsOn: "",
  allocationCutoffAt: "",
  expectedVersion: 0,
});

const premisesColumns: TableColumn<AccommodationPremiseSummary>[] = [
  { accessorKey: "code", header: "Code" },
  { accessorKey: "name", header: "Premise" },
  { accessorKey: "addressLine", header: "Address" },
  { accessorKey: "landlordName", header: "Landlord" },
  { accessorKey: "active", header: "Status" },
  { accessorKey: "actions", header: "" },
];
const roomTypeColumns: TableColumn<AccommodationRoomTypeSummary>[] = [
  { accessorKey: "code", header: "Code" },
  { accessorKey: "name", header: "Room type" },
  { accessorKey: "defaultCapacity", header: "Default capacity" },
  { accessorKey: "active", header: "Status" },
  { accessorKey: "actions", header: "" },
];
const residenceHallColumns: TableColumn<ResidenceHallSummary>[] = [
  { accessorKey: "code", header: "Code" },
  { accessorKey: "name", header: "Residence hall" },
  { accessorKey: "premiseCode", header: "Premise" },
  { accessorKey: "residentGenderPolicy", header: "Resident policy" },
  { accessorKey: "wardenName", header: "Warden" },
  { accessorKey: "active", header: "Status" },
  { accessorKey: "actions", header: "" },
];
const roomColumns: TableColumn<AccommodationRoomSummary>[] = [
  { accessorKey: "code", header: "Room" },
  { accessorKey: "residenceHallCode", header: "Residence hall" },
  { accessorKey: "roomTypeCode", header: "Type" },
  { accessorKey: "floorLabel", header: "Floor" },
  { accessorKey: "capacity", header: "Beds" },
  { accessorKey: "conditionStatus", header: "Condition" },
  { accessorKey: "accessibilityReady", header: "Accessible" },
  { accessorKey: "actions", header: "" },
];
const applicationPeriodColumns: TableColumn<AccommodationApplicationPeriodSummary>[] =
  [
    { accessorKey: "code", header: "Code" },
    { accessorKey: "name", header: "Application period" },
    { accessorKey: "academicPeriodCode", header: "Academic period" },
    { accessorKey: "applicationsOpenAt", header: "Applications" },
    { accessorKey: "occupancyStartsOn", header: "Occupancy" },
    { accessorKey: "status", header: "Control state" },
    { accessorKey: "actions", header: "" },
  ];

const datasets = computed(() => [
  {
    label: "Premises",
    value: "premises",
    icon: "i-lucide-map-pin-house",
    badge: register.value.premises.length,
  },
  {
    label: "Room types",
    value: "room-types",
    icon: "i-lucide-bed-single",
    badge: register.value.roomTypes.length,
  },
  {
    label: "Residence halls",
    value: "residence-halls",
    icon: "i-lucide-building-2",
    badge: register.value.residenceHalls.length,
  },
  {
    label: "Rooms",
    value: "rooms",
    icon: "i-lucide-door-open",
    badge: register.value.rooms.length,
  },
  {
    label: "Application periods",
    value: "application-periods",
    icon: "i-lucide-calendar-range",
    badge: register.value.applicationPeriods.length,
  },
]);
const premiseItems = computed(() =>
  register.value.premises
    .filter((item) => item.active)
    .map((item) => ({ label: `${item.code} · ${item.name}`, value: item.id })),
);
const roomTypeItems = computed(() =>
  register.value.roomTypes
    .filter((item) => item.active)
    .map((item) => ({ label: `${item.code} · ${item.name}`, value: item.id })),
);
const residenceHallItems = computed(() =>
  register.value.residenceHalls
    .filter((item) => item.active)
    .map((item) => ({ label: `${item.code} · ${item.name}`, value: item.id })),
);
const academicPeriodItems = computed(() =>
  (academicSetup.overview.value?.academicPeriods ?? []).map((item) => ({
    label: `${item.code} · ${item.name}`,
    value: item.id,
    code: item.code,
    startsOn: item.startDate,
    endsOn: item.endDate,
  })),
);
const genderPolicyItems = ["ANY", "FEMALE", "MALE"].map((value) => ({
  label: title(value),
  value,
}));
const conditionItems = ["AVAILABLE", "MAINTENANCE", "OUT_OF_SERVICE"].map(
  (value) => ({ label: title(value), value }),
);
const totalBedCapacity = computed(() =>
  register.value.rooms
    .filter((item) => item.active)
    .reduce((total, item) => total + item.capacity, 0),
);
const availableBedCapacity = computed(() =>
  register.value.rooms
    .filter((item) => item.active && item.conditionStatus === "AVAILABLE")
    .reduce((total, item) => total + item.capacity, 0),
);
const drawerTitle = computed(
  () => `${editingId.value ? "Update" : "Create"} ${title(drawerMode.value)}`,
);
const nextPeriodStatus = (
  status: AccommodationPeriodStatus,
): AccommodationPeriodStatus | null =>
  ({
    DRAFT: "APPLICATION_OPEN",
    APPLICATION_OPEN: "APPLICATION_CLOSED",
    APPLICATION_CLOSED: "ALLOCATION_ACTIVE",
    ALLOCATION_ACTIVE: "CLOSED",
    CLOSED: null,
  })[status] as AccommodationPeriodStatus | null;

onMounted(async () => {
  await Promise.all([
    loadRegister(),
    academicSetup.ensureOverview().catch(() => undefined),
  ]);
});

watch(
  () => applicationPeriodForm.academicPeriodId,
  (academicPeriodId) => {
    const selected = academicPeriodItems.value.find(
      (item) => item.value === academicPeriodId,
    );
    if (!selected) return;
    applicationPeriodForm.academicPeriodCode = selected.code;
    if (!editingId.value) {
      applicationPeriodForm.code = `RES-${selected.code}`;
      applicationPeriodForm.name = `${selected.label.split(" · ")[1]} accommodation`;
      applicationPeriodForm.occupancyStartsOn = selected.startsOn;
      applicationPeriodForm.occupancyEndsOn = selected.endsOn;
    }
  },
);

async function loadRegister() {
  loading.value = true;
  loadError.value = "";
  try {
    register.value = await api.request<AccommodationSetupRegister>(
      "/api/accommodation/setup",
    );
  } catch (error) {
    loadError.value = api.errorMessage(
      error,
      "Accommodation setup could not be loaded.",
    );
  } finally {
    loading.value = false;
  }
}

function openCreate(mode: DrawerMode) {
  drawerMode.value = mode;
  editingId.value = null;
  resetForm(mode);
  drawerOpen.value = true;
}

function openEdit(
  mode: DrawerMode,
  record:
    | AccommodationPremiseSummary
    | AccommodationRoomTypeSummary
    | ResidenceHallSummary
    | AccommodationRoomSummary
    | AccommodationApplicationPeriodSummary,
) {
  drawerMode.value = mode;
  editingId.value = record.id;
  if (mode === "premise") Object.assign(premiseForm, record);
  if (mode === "room-type") Object.assign(roomTypeForm, record);
  if (mode === "residence-hall") Object.assign(residenceHallForm, record);
  if (mode === "room") Object.assign(roomForm, record);
  if (mode === "application-period") {
    const period = record as AccommodationApplicationPeriodSummary;
    Object.assign(applicationPeriodForm, {
      ...period,
      applicationsOpenAt: localDateTime(period.applicationsOpenAt),
      applicationsCloseAt: localDateTime(period.applicationsCloseAt),
      allocationCutoffAt: localDateTime(period.allocationCutoffAt),
      expectedVersion: period.version,
    });
  }
  drawerOpen.value = true;
}

function resetForm(mode: DrawerMode) {
  if (mode === "premise")
    Object.assign(premiseForm, {
      code: "",
      name: "",
      addressLine: "",
      suburb: "",
      landlordName: "",
      contactDetails: "",
      active: true,
      expectedVersion: 0,
    });
  if (mode === "room-type")
    Object.assign(roomTypeForm, {
      code: "",
      name: "",
      description: "",
      defaultCapacity: 1,
      active: true,
      expectedVersion: 0,
    });
  if (mode === "residence-hall")
    Object.assign(residenceHallForm, {
      premiseId: premiseItems.value[0]?.value ?? "",
      code: "",
      name: "",
      residentGenderPolicy: "ANY",
      wardenName: "",
      wardenContact: "",
      active: true,
      expectedVersion: 0,
    });
  if (mode === "room")
    Object.assign(roomForm, {
      residenceHallId: residenceHallItems.value[0]?.value ?? "",
      roomTypeId: roomTypeItems.value[0]?.value ?? "",
      code: "",
      floorLabel: "",
      capacity: 1,
      accessibilityReady: false,
      conditionStatus: "AVAILABLE",
      conditionNotes: "",
      reservedForGroupId: "",
      active: true,
      expectedVersion: 0,
    });
  if (mode === "application-period")
    Object.assign(applicationPeriodForm, {
      academicPeriodId: "",
      academicPeriodCode: "",
      code: "",
      name: "",
      applicationsOpenAt: "",
      applicationsCloseAt: "",
      occupancyStartsOn: "",
      occupancyEndsOn: "",
      allocationCutoffAt: "",
      expectedVersion: 0,
    });
}

async function saveRecord() {
  saving.value = true;
  try {
    const route = (
      {
        premise: "premises",
        "room-type": "room-types",
        "residence-hall": "residence-halls",
        room: "rooms",
        "application-period": "application-periods",
      } as const
    )[drawerMode.value];
    const form = {
      premise: premiseForm,
      "room-type": roomTypeForm,
      "residence-hall": residenceHallForm,
      room: roomForm,
      "application-period": applicationPeriodForm,
    }[drawerMode.value];
    const body =
      drawerMode.value === "application-period"
        ? {
            ...form,
            applicationsOpenAt: new Date(
              applicationPeriodForm.applicationsOpenAt,
            ).toISOString(),
            applicationsCloseAt: new Date(
              applicationPeriodForm.applicationsCloseAt,
            ).toISOString(),
            allocationCutoffAt: new Date(
              applicationPeriodForm.allocationCutoffAt,
            ).toISOString(),
          }
        : drawerMode.value === "room"
          ? {
              ...form,
              reservedForGroupId: roomForm.reservedForGroupId || null,
              conditionNotes: roomForm.conditionNotes || null,
            }
          : form;
    await api.request(
      `/api/accommodation/setup/${route}${editingId.value ? `/${editingId.value}` : ""}`,
      {
        method: editingId.value ? "PUT" : "POST",
        body,
      },
    );
    drawerOpen.value = false;
    await loadRegister();
    toast.add({
      title: `${title(drawerMode.value)} ${editingId.value ? "updated" : "created"}`,
      color: "success",
      icon: "i-lucide-check-circle-2",
    });
  } catch (error) {
    await showError(
      `${title(drawerMode.value)} could not be saved`,
      api.errorMessage(error),
    );
  } finally {
    saving.value = false;
  }
}

async function transitionPeriod(period: AccommodationApplicationPeriodSummary) {
  const targetStatus = nextPeriodStatus(period.status);
  if (!targetStatus) return;
  const result = await Swal.fire({
    title: `${title(targetStatus)}?`,
    text: "This is a controlled maker-checker transition. The approving operator must differ from the preparer.",
    input: "textarea",
    inputLabel: "Approval evidence",
    inputPlaceholder:
      "Record the capacity, calendar, rate, and policy checks performed.",
    icon: "question",
    showCancelButton: true,
    confirmButtonText: `Move to ${title(targetStatus)}`,
    confirmButtonColor: "#006633",
    inputValidator: (value) =>
      value.trim() ? undefined : "Approval evidence is required.",
  });
  if (!result.isConfirmed || !result.value?.trim()) return;
  actionRecordId.value = period.id;
  try {
    await api.request(
      `/api/accommodation/setup/application-periods/${period.id}/transition`,
      {
        method: "POST",
        body: {
          targetStatus,
          reason: result.value.trim(),
          expectedVersion: period.version,
        },
      },
    );
    await loadRegister();
    toast.add({
      title: `Application period moved to ${title(targetStatus)}`,
      color: "success",
      icon: "i-lucide-stamp",
    });
  } catch (error) {
    await showError(
      "Application period could not be progressed",
      api.errorMessage(error),
    );
  } finally {
    actionRecordId.value = null;
  }
}

function title(value: string) {
  return value
    .replaceAll("-", " ")
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}
function localDateTime(value: string) {
  return value ? value.slice(0, 16) : "";
}
function formatDate(value: string) {
  return new Intl.DateTimeFormat("en-ZW", { dateStyle: "medium" }).format(
    new Date(value),
  );
}
function statusTone(value: string) {
  return value === "AVAILABLE" ||
    value === "APPLICATION_OPEN" ||
    value === "ALLOCATION_ACTIVE"
    ? "success"
    : value === "MAINTENANCE" ||
        value === "DRAFT" ||
        value === "APPLICATION_CLOSED"
      ? "warning"
      : value === "OUT_OF_SERVICE"
        ? "error"
        : "neutral";
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Accommodation operations">
        <template #leading><UDashboardSidebarCollapse /></template>
        <template #right
          ><UButton
            label="Refresh"
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="outline"
            :loading="loading"
            @click="loadRegister"
        /></template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="space-y-5 p-4 sm:p-6">
        <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <EmhareKpiCard
            label="Premises"
            :value="register.premises.filter((item) => item.active).length"
            icon="i-lucide-map-pin-house"
            tone="primary"
          />
          <EmhareKpiCard
            label="Residence halls"
            :value="
              register.residenceHalls.filter((item) => item.active).length
            "
            icon="i-lucide-building-2"
            tone="success"
          />
          <EmhareKpiCard
            label="Configured beds"
            :value="totalBedCapacity"
            icon="i-lucide-bed-single"
            tone="primary"
          />
          <EmhareKpiCard
            label="Beds available for allocation"
            :value="availableBedCapacity"
            icon="i-lucide-bed"
            tone="success"
          />
        </div>

        <UAlert
          color="primary"
          variant="soft"
          icon="i-lucide-shield-check"
          title="Governed residence inventory"
          description="Premises, room capacity, accessibility, condition, application windows, and allocation readiness are controlled records. Application periods require independent maker-checker progression."
        />
        <UAlert
          v-if="loadError"
          color="error"
          variant="soft"
          icon="i-lucide-circle-alert"
          title="Accommodation setup unavailable"
          :description="loadError"
        />
        <UTabs
          v-model="activeDataset"
          :items="datasets"
          :content="false"
          color="primary"
          variant="pill"
        />

        <EmhareRegisterPanel
          v-if="activeDataset === 'premises'"
          title="Premises"
          description="Institution-owned and leased locations that contain residence halls."
          :record-count="register.premises.length"
        >
          <template #actions
            ><UButton
              label="Create premise"
              icon="i-lucide-plus"
              @click="openCreate('premise')"
          /></template>
          <EmharePaginatedTable
            :data="register.premises"
            :columns="premisesColumns"
            :loading="loading"
          >
            <template #active-cell="{ row }"
              ><EmhareStatusPill
                :label="row.original.active ? 'Active' : 'Inactive'"
                :tone="row.original.active ? 'success' : 'neutral'"
            /></template>
            <template #actions-cell="{ row }"
              ><UButton
                label="Edit"
                icon="i-lucide-pencil"
                size="xs"
                color="neutral"
                variant="ghost"
                @click="openEdit('premise', row.original)"
            /></template>
          </EmharePaginatedTable>
        </EmhareRegisterPanel>

        <EmhareRegisterPanel
          v-if="activeDataset === 'room-types'"
          title="Room types"
          description="Capacity defaults and accommodation classifications used by rates and allocation."
          :record-count="register.roomTypes.length"
        >
          <template #actions
            ><UButton
              label="Create room type"
              icon="i-lucide-plus"
              @click="openCreate('room-type')"
          /></template>
          <EmharePaginatedTable
            :data="register.roomTypes"
            :columns="roomTypeColumns"
            :loading="loading"
          >
            <template #active-cell="{ row }"
              ><EmhareStatusPill
                :label="row.original.active ? 'Active' : 'Inactive'"
                :tone="row.original.active ? 'success' : 'neutral'"
            /></template>
            <template #actions-cell="{ row }"
              ><UButton
                label="Edit"
                icon="i-lucide-pencil"
                size="xs"
                color="neutral"
                variant="ghost"
                @click="openEdit('room-type', row.original)"
            /></template>
          </EmharePaginatedTable>
        </EmhareRegisterPanel>

        <EmhareRegisterPanel
          v-if="activeDataset === 'residence-halls'"
          title="Residence halls"
          description="Managed halls, wardens, and resident eligibility policy."
          :record-count="register.residenceHalls.length"
        >
          <template #actions
            ><EmhareGuidedActionButton
              label="Create residence hall"
              icon="i-lucide-plus"
              guidance-title="Residence hall setup required"
              :guidance-instructions="premiseItems.length ? [] : ['Create an active accommodation premise before creating a residence hall.']"
              @click="openCreate('residence-hall')"
          /></template>
          <EmharePaginatedTable
            :data="register.residenceHalls"
            :columns="residenceHallColumns"
            :loading="loading"
          >
            <template #residentGenderPolicy-cell="{ row }"
              ><EmhareStatusPill
                :label="title(row.original.residentGenderPolicy)"
                tone="primary"
            /></template>
            <template #active-cell="{ row }"
              ><EmhareStatusPill
                :label="row.original.active ? 'Active' : 'Inactive'"
                :tone="row.original.active ? 'success' : 'neutral'"
            /></template>
            <template #actions-cell="{ row }"
              ><UButton
                label="Edit"
                icon="i-lucide-pencil"
                size="xs"
                color="neutral"
                variant="ghost"
                @click="openEdit('residence-hall', row.original)"
            /></template>
          </EmharePaginatedTable>
        </EmhareRegisterPanel>

        <EmhareRegisterPanel
          v-if="activeDataset === 'rooms'"
          title="Rooms and bed capacity"
          description="Allocatable room inventory with accessibility and condition controls."
          :record-count="register.rooms.length"
        >
          <template #actions
            ><EmhareGuidedActionButton
              label="Create room"
              icon="i-lucide-plus"
              guidance-title="Room setup required"
              :guidance-instructions="[...(!residenceHallItems.length ? ['Create an active residence hall.'] : []), ...(!roomTypeItems.length ? ['Create an active room type.'] : [])]"
              @click="openCreate('room')"
          /></template>
          <EmharePaginatedTable
            :data="register.rooms"
            :columns="roomColumns"
            :loading="loading"
          >
            <template #conditionStatus-cell="{ row }"
              ><EmhareStatusPill
                :label="title(row.original.conditionStatus)"
                :tone="statusTone(row.original.conditionStatus)"
            /></template>
            <template #accessibilityReady-cell="{ row }"
              ><EmhareStatusPill
                :label="row.original.accessibilityReady ? 'Ready' : 'Standard'"
                :tone="row.original.accessibilityReady ? 'success' : 'neutral'"
            /></template>
            <template #actions-cell="{ row }"
              ><UButton
                label="Edit"
                icon="i-lucide-pencil"
                size="xs"
                color="neutral"
                variant="ghost"
                @click="openEdit('room', row.original)"
            /></template>
          </EmharePaginatedTable>
        </EmhareRegisterPanel>

        <EmhareRegisterPanel
          v-if="activeDataset === 'application-periods'"
          title="Application periods"
          description="Controlled application, occupancy, and allocation windows linked to the Academic Calendar."
          :record-count="register.applicationPeriods.length"
        >
          <template #actions
            ><EmhareGuidedActionButton
              label="Create application period"
              icon="i-lucide-plus"
              guidance-title="Accommodation application period setup required"
              :guidance-instructions="academicPeriodItems.length ? [] : ['Create an academic period in the Academic calendar before opening accommodation applications.']"
              guidance-action-label="Open Academic calendar"
              @guidance-action="navigateTo('/operations/academic-calendar')"
              @click="openCreate('application-period')"
          /></template>
          <EmharePaginatedTable
            :data="register.applicationPeriods"
            :columns="applicationPeriodColumns"
            :loading="loading"
          >
            <template #applicationsOpenAt-cell="{ row }"
              ><span
                >{{ formatDate(row.original.applicationsOpenAt) }} –
                {{ formatDate(row.original.applicationsCloseAt) }}</span
              ></template
            >
            <template #occupancyStartsOn-cell="{ row }"
              ><span
                >{{ formatDate(row.original.occupancyStartsOn) }} –
                {{ formatDate(row.original.occupancyEndsOn) }}</span
              ></template
            >
            <template #status-cell="{ row }"
              ><EmhareStatusPill
                :label="title(row.original.status)"
                :tone="statusTone(row.original.status)"
            /></template>
            <template #actions-cell="{ row }">
              <div class="flex justify-end gap-1">
                <UButton
                  v-if="row.original.status === 'DRAFT'"
                  label="Edit"
                  icon="i-lucide-pencil"
                  size="xs"
                  color="neutral"
                  variant="ghost"
                  @click="openEdit('application-period', row.original)"
                />
                <UButton
                  v-if="nextPeriodStatus(row.original.status)"
                  :label="title(nextPeriodStatus(row.original.status)!)"
                  icon="i-lucide-stamp"
                  size="xs"
                  :loading="actionRecordId === row.original.id"
                  @click="transitionPeriod(row.original)"
                />
              </div>
            </template>
          </EmharePaginatedTable>
        </EmhareRegisterPanel>
      </div>
    </template>
  </UDashboardPanel>

  <EmhareRecordDrawer
    v-model:open="drawerOpen"
    :title="drawerTitle"
    description="Complete the controlled record. Existing records require the version currently shown in the register."
    :busy="saving"
    :submit-disabled="saving"
    @submit="saveRecord"
  >
    <div v-if="drawerMode === 'premise'" class="grid gap-4 sm:grid-cols-2">
      <UFormField label="Code" required
        ><UInput
          v-model="premiseForm.code"
          class="w-full"
          placeholder="MP-CAMPUS"
      /></UFormField>
      <UFormField label="Name" required
        ><UInput v-model="premiseForm.name" class="w-full"
      /></UFormField>
      <UFormField label="Address" required class="sm:col-span-2"
        ><UInput v-model="premiseForm.addressLine" class="w-full"
      /></UFormField>
      <UFormField label="Suburb"
        ><UInput v-model="premiseForm.suburb" class="w-full"
      /></UFormField>
      <UFormField label="Landlord"
        ><UInput v-model="premiseForm.landlordName" class="w-full"
      /></UFormField>
      <UFormField label="Contact details" class="sm:col-span-2"
        ><UTextarea v-model="premiseForm.contactDetails" class="w-full"
      /></UFormField>
      <USwitch v-if="editingId" v-model="premiseForm.active" label="Active" />
    </div>

    <div
      v-else-if="drawerMode === 'room-type'"
      class="grid gap-4 sm:grid-cols-2"
    >
      <UFormField label="Code" required
        ><UInput v-model="roomTypeForm.code" class="w-full"
      /></UFormField>
      <UFormField label="Default capacity" required
        ><UInput
          v-model.number="roomTypeForm.defaultCapacity"
          type="number"
          min="1"
          class="w-full"
      /></UFormField>
      <UFormField label="Name" required class="sm:col-span-2"
        ><UInput v-model="roomTypeForm.name" class="w-full"
      /></UFormField>
      <UFormField label="Description" class="sm:col-span-2"
        ><UTextarea v-model="roomTypeForm.description" class="w-full"
      /></UFormField>
      <USwitch v-if="editingId" v-model="roomTypeForm.active" label="Active" />
    </div>

    <div
      v-else-if="drawerMode === 'residence-hall'"
      class="grid gap-4 sm:grid-cols-2"
    >
      <UFormField label="Premise" required class="sm:col-span-2"
        ><USelect
          v-model="residenceHallForm.premiseId"
          :items="premiseItems"
          value-key="value"
          class="w-full"
      /></UFormField>
      <UFormField label="Code" required
        ><UInput v-model="residenceHallForm.code" class="w-full"
      /></UFormField>
      <UFormField label="Name" required
        ><UInput v-model="residenceHallForm.name" class="w-full"
      /></UFormField>
      <UFormField label="Resident policy" required
        ><USelect
          v-model="residenceHallForm.residentGenderPolicy"
          :items="genderPolicyItems"
          value-key="value"
          class="w-full"
      /></UFormField>
      <UFormField label="Warden"
        ><UInput v-model="residenceHallForm.wardenName" class="w-full"
      /></UFormField>
      <UFormField label="Warden contact" class="sm:col-span-2"
        ><UInput v-model="residenceHallForm.wardenContact" class="w-full"
      /></UFormField>
      <USwitch
        v-if="editingId"
        v-model="residenceHallForm.active"
        label="Active"
      />
    </div>

    <div v-else-if="drawerMode === 'room'" class="grid gap-4 sm:grid-cols-2">
      <UFormField label="Residence hall" required
        ><USelect
          v-model="roomForm.residenceHallId"
          :items="residenceHallItems"
          value-key="value"
          class="w-full"
      /></UFormField>
      <UFormField label="Room type" required
        ><USelect
          v-model="roomForm.roomTypeId"
          :items="roomTypeItems"
          value-key="value"
          class="w-full"
      /></UFormField>
      <UFormField label="Room code" required
        ><UInput v-model="roomForm.code" class="w-full"
      /></UFormField>
      <UFormField label="Floor"
        ><UInput v-model="roomForm.floorLabel" class="w-full"
      /></UFormField>
      <UFormField label="Bed capacity" required
        ><UInput
          v-model.number="roomForm.capacity"
          type="number"
          min="1"
          class="w-full"
      /></UFormField>
      <UFormField label="Condition" required
        ><USelect
          v-model="roomForm.conditionStatus"
          :items="conditionItems"
          value-key="value"
          class="w-full"
      /></UFormField>
      <UFormField label="Condition notes" class="sm:col-span-2"
        ><UTextarea v-model="roomForm.conditionNotes" class="w-full"
      /></UFormField>
      <USwitch
        v-model="roomForm.accessibilityReady"
        label="Accessibility ready"
      />
      <USwitch v-if="editingId" v-model="roomForm.active" label="Active" />
    </div>

    <div v-else class="grid gap-4 sm:grid-cols-2">
      <UAlert
        color="warning"
        variant="soft"
        icon="i-lucide-shield-check"
        title="Maker-checker controlled"
        description="The operator who prepares this period cannot approve its first transition."
        class="sm:col-span-2"
      />
      <UFormField label="Academic period" required class="sm:col-span-2"
        ><USelect
          v-model="applicationPeriodForm.academicPeriodId"
          :items="academicPeriodItems"
          value-key="value"
          class="w-full"
      /></UFormField>
      <UFormField label="Code" required
        ><UInput v-model="applicationPeriodForm.code" class="w-full"
      /></UFormField>
      <UFormField label="Name" required
        ><UInput v-model="applicationPeriodForm.name" class="w-full"
      /></UFormField>
      <UFormField label="Applications open" required
        ><UInput
          v-model="applicationPeriodForm.applicationsOpenAt"
          type="datetime-local"
          class="w-full"
      /></UFormField>
      <UFormField label="Applications close" required
        ><UInput
          v-model="applicationPeriodForm.applicationsCloseAt"
          type="datetime-local"
          class="w-full"
      /></UFormField>
      <UFormField label="Occupancy starts" required
        ><UInput
          v-model="applicationPeriodForm.occupancyStartsOn"
          type="date"
          class="w-full"
      /></UFormField>
      <UFormField label="Occupancy ends" required
        ><UInput
          v-model="applicationPeriodForm.occupancyEndsOn"
          type="date"
          class="w-full"
      /></UFormField>
      <UFormField label="Allocation cutoff" required class="sm:col-span-2"
        ><UInput
          v-model="applicationPeriodForm.allocationCutoffAt"
          type="datetime-local"
          class="w-full"
      /></UFormField>
    </div>
  </EmhareRecordDrawer>
</template>
