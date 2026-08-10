<script setup lang="ts">
import type { TableColumn } from "@nuxt/ui";
import Swal from "sweetalert2";
import type { FinanceCollectionsRegister, FinanceFeeCatalogueRegister } from "@emhare/portal-shell/types/finance";
import type { RegistrationSummary } from "@emhare/portal-shell/types/student-records";
import type {
  AccommodationApplicationStatus,
  AccommodationApplicationSummary,
  AccommodationOperationsRegister,
  AccommodationRateSummary,
  AccommodationSetupRegister,
  AccommodationWaitlistSummary,
  RoomAllocationSummary,
} from "@emhare/portal-shell/types/accommodation";

definePageMeta({ layout: "dashboard" });

type DrawerMode = "rate" | "application" | "evaluation" | "allocation";

const api = useEmhareApi();
const toast = useToast();
const { showError } = useEmhareConfirm();
const { fromProgrammePeriodNumber, studyPeriodLabel, yearOfStudyItems } = useProgrammeStudyPeriod();
const academicSetup = useAcademicSetup();
const academicPeriodContext = useAcademicPeriodContext();
const loading = ref(false);
const saving = ref(false);
const operatingId = ref<string | null>(null);
const loadError = ref("");
const loadWarnings = ref<string[]>([]);
const activeDataset = ref("applications");
const drawerOpen = ref(false);
const drawerMode = ref<DrawerMode>("application");
const selectedApplication = ref<AccommodationApplicationSummary | null>(null);

const register = ref<AccommodationOperationsRegister>({
  rates: [],
  applications: [],
  waitlistEntries: [],
  allocations: [],
  allocationEvents: [],
});
const setup = ref<AccommodationSetupRegister>({
  premises: [],
  roomTypes: [],
  residenceHalls: [],
  rooms: [],
  applicationPeriods: [],
});
const finance = ref<FinanceFeeCatalogueRegister>({ catalogues: [] });
const financeCollections = ref<FinanceCollectionsRegister>({ exchangeRates: [], payments: [], receipts: [], allocations: [], creditNotes: [] });
const registrations = ref<RegistrationSummary[]>([]);
const accommodationYearOfStudyItems = yearOfStudyItems();
const selectedApplicationRegistration = computed(() =>
  registrations.value.find((registration) => registration.id === applicationForm.studentRegistrationId) ?? null,
);
const selectedApplicationStudyStage = computed(() =>
  selectedApplicationRegistration.value
    ? studyPeriodLabel(selectedApplicationRegistration.value.programmePeriodNumber)
    : "Select a confirmed student registration",
);

const rateForm = reactive({
  applicationPeriodId: "",
  roomTypeId: "",
  rateVersion: 1,
  financeFeeCatalogueId: "",
  transactionCurrencyCode: "USD",
  indicativeTransactionAmount: 0,
  exchangeRateId: "",
  indicativeBaseAmount: null as number | null,
  effectiveFrom: "",
  effectiveUntil: "",
});
const applicationForm = reactive({
  studentRegistrationId: "",
  applicationPeriodId: "",
  studentId: "",
  studentNumber: "",
  studentName: "",
  primaryEmail: "",
  genderCode: "FEMALE",
  disabilityCode: "",
  countryCode: "ZWE",
  locationCode: "",
  programmeId: "",
  programmeCode: "",
  programmeName: "",
  programmeLevel: 1,
  sponsorCode: "",
  paymentState: "UNKNOWN",
  preferredRoomTypeId: "",
  specialRequirements: "",
});
const evaluationForm = reactive({
  outcome: "ELIGIBLE" as AccommodationApplicationStatus,
  priorityScore: 0,
  selectedGroupId: "",
  reason: "",
  expectedVersion: 0,
});
const allocationForm = reactive({
  applicationId: "",
  roomId: "",
  accommodationRateId: "",
  occupancyStartsOn: "",
  occupancyEndsOn: "",
  reason: "",
});

const rateColumns: TableColumn<AccommodationRateSummary>[] = [
  { accessorKey: "applicationPeriodCode", header: "Period" },
  { accessorKey: "roomTypeCode", header: "Room type" },
  { accessorKey: "rateVersion", header: "Version" },
  { accessorKey: "indicativeTransactionAmount", header: "Indicative rate" },
  { accessorKey: "ratingStatus", header: "Rating" },
  { accessorKey: "status", header: "Control state" },
  { accessorKey: "actions", header: "" },
];
const applicationColumns: TableColumn<AccommodationApplicationSummary>[] = [
  { accessorKey: "applicationNumber", header: "Application" },
  { accessorKey: "studentNumber", header: "Student" },
  { accessorKey: "studentName", header: "Name" },
  { accessorKey: "programmeCode", header: "Programme" },
  { accessorKey: "paymentState", header: "Payment" },
  { accessorKey: "priorityScore", header: "Score" },
  { accessorKey: "status", header: "Status" },
  { accessorKey: "actions", header: "" },
];
const waitlistColumns: TableColumn<AccommodationWaitlistSummary>[] = [
  { accessorKey: "waitlistPosition", header: "Position" },
  { accessorKey: "applicationNumber", header: "Application" },
  { accessorKey: "applicationPeriodCode", header: "Period" },
  { accessorKey: "priorityScore", header: "Priority score" },
  { accessorKey: "status", header: "Status" },
  { accessorKey: "enteredAt", header: "Entered" },
];
const allocationColumns: TableColumn<RoomAllocationSummary>[] = [
  { accessorKey: "allocationNumber", header: "Allocation" },
  { accessorKey: "studentNumber", header: "Student" },
  { accessorKey: "studentName", header: "Name" },
  { accessorKey: "roomCode", header: "Room" },
  { accessorKey: "occupancyStartsOn", header: "Occupancy" },
  { accessorKey: "billingStatus", header: "Billing" },
  { accessorKey: "status", header: "Status" },
  { accessorKey: "actions", header: "" },
];
const eventColumns = [
  { accessorKey: "occurredAt", header: "Occurred" },
  { accessorKey: "allocationNumber", header: "Allocation" },
  { accessorKey: "eventType", header: "Event" },
  { accessorKey: "previousStatus", header: "From" },
  { accessorKey: "newStatus", header: "To" },
  { accessorKey: "reason", header: "Evidence" },
];

const datasets = computed(() => [
  {
    label: "Applications",
    value: "applications",
    icon: "i-lucide-files",
    badge: register.value.applications.length,
  },
  {
    label: "Wait-list",
    value: "waitlist",
    icon: "i-lucide-list-ordered",
    badge: activeWaitlist.value,
  },
  {
    label: "Allocations and occupancy",
    value: "allocations",
    icon: "i-lucide-bed",
    badge: activeAllocations.value,
  },
  {
    label: "Rates",
    value: "rates",
    icon: "i-lucide-badge-dollar-sign",
    badge: register.value.rates.length,
  },
  {
    label: "Workflow history",
    value: "history",
    icon: "i-lucide-history",
    badge: register.value.allocationEvents.length,
  },
]);
const activeWaitlist = computed(
  () =>
    register.value.waitlistEntries.filter((item) => item.status === "ACTIVE")
      .length,
);
const activeAllocations = computed(
  () =>
    register.value.allocations.filter((item) =>
      ["PROPOSED", "ALLOCATED", "CHECKED_IN"].includes(item.status),
    ).length,
);
const occupiedBeds = computed(
  () =>
    register.value.allocations.filter((item) => item.status === "CHECKED_IN")
      .length,
);
const pendingEvaluation = computed(
  () =>
    register.value.applications.filter((item) => item.status === "SUBMITTED")
      .length,
);
const periodItems = computed(() =>
  setup.value.applicationPeriods
    .filter((item) => item.status !== "CLOSED")
    .map((item) => ({
      label: `${item.code} · ${title(item.status)}`,
      value: item.id,
    })),
);
const roomTypeItems = computed(() =>
  setup.value.roomTypes
    .filter((item) => item.active)
    .map((item) => ({ label: `${item.code} · ${item.name}`, value: item.id })),
);
const roomItems = computed(() =>
  setup.value.rooms
    .filter((item) => item.active && item.conditionStatus === "AVAILABLE")
    .map((item) => ({
      label: `${item.residenceHallCode} · ${item.code} · ${item.capacity} bed${item.capacity === 1 ? "" : "s"}`,
      value: item.id,
      roomTypeId: item.roomTypeId,
    })),
);
const feeItems = computed(() =>
  finance.value.catalogues
    .filter(
      (item) => item.status === "ACTIVE" && item.chargeType === "ACCOMMODATION",
    )
    .map((item) => ({ label: `${item.code} · ${item.name}`, value: item.id })),
);
const programmeItems = computed(() =>
  (academicSetup.overview.value?.programmes ?? [])
    .filter((item) => item.status === "ACTIVE")
    .map((item) => ({
      label: `${item.code} · ${item.name}`,
      value: item.id,
      code: item.code,
      name: item.name,
    })),
);
const studentItems = computed(() =>
  registrations.value
    .filter((item) => item.status === "CONFIRMED")
    .map((item) => ({
      label: `${item.studentNumber} · ${item.studentName} · ${item.academicPeriodCode}`,
      value: item.id,
      registration: item,
    })),
);
const exchangeRateItems = computed(() =>
  financeCollections.value.exchangeRates
    .filter((item) => item.status === "ACTIVE" && item.sourceCurrencyCode === rateForm.transactionCurrencyCode)
    .map((item) => ({
      label: `${item.sourceCurrencyCode} 1 = USD ${item.rateToBase} · ${new Date(item.effectiveFrom).toLocaleDateString("en-ZW")} · ${item.sourceName}`,
      value: item.id,
      rateToBase: item.rateToBase,
    })),
);
const eligibleApplicationItems = computed(() =>
  register.value.applications
    .filter((item) => ["ELIGIBLE", "WAITLISTED"].includes(item.status))
    .map((item) => ({
      label: `${item.applicationNumber} · ${item.studentNumber} · ${item.studentName}`,
      value: item.id,
    })),
);
const activeRateItems = computed(() =>
  register.value.rates
    .filter((item) => item.status === "ACTIVE" && item.ratingStatus === "RATED")
    .map((item) => ({
      label: `${item.applicationPeriodCode} · ${item.roomTypeCode} · ${money(item.indicativeTransactionAmount, item.transactionCurrencyCode)}`,
      value: item.id,
      roomTypeId: item.roomTypeId,
      periodId: item.applicationPeriodId,
    })),
);
const drawerTitle = computed(
  () =>
    ({
      rate: "Prepare accommodation rate",
      application: "Submit accommodation application",
      evaluation: "Evaluate application",
      allocation: "Propose room allocation",
    })[drawerMode.value],
);

onMounted(load);
watch(academicPeriodContext.selectedAcademicPeriodId, () => void load());
watch(
  () => applicationForm.studentRegistrationId,
  (id) => {
    const registration = studentItems.value.find((item) => item.value === id)?.registration;
    if (!registration) return;
    const programme = academicSetup.overview.value?.programmes.find((item) => item.code === registration.programmeCode);
    Object.assign(applicationForm, {
      studentId: registration.studentId,
      studentNumber: registration.studentNumber,
      studentName: registration.studentName,
      programmeId: programme?.id ?? "",
      programmeCode: registration.programmeCode,
      programmeName: registration.programmeName,
      programmeLevel: fromProgrammePeriodNumber(registration.programmePeriodNumber).yearOfStudy,
    });
  },
);
watch(
  [() => rateForm.exchangeRateId, () => rateForm.indicativeTransactionAmount],
  () => {
    const rate = exchangeRateItems.value.find((item) => item.value === rateForm.exchangeRateId);
    rateForm.indicativeBaseAmount = rate
      ? Number((rateForm.indicativeTransactionAmount * rate.rateToBase).toFixed(4))
      : null;
  },
);
watch(
  () => rateForm.transactionCurrencyCode,
  (currencyCode) => {
    if (currencyCode === "USD") {
      rateForm.exchangeRateId = "";
      rateForm.indicativeBaseAmount = null;
    }
  },
);
watch(
  () => applicationForm.programmeId,
  (id) => {
    const programme = programmeItems.value.find((item) => item.value === id);
    if (programme)
      Object.assign(applicationForm, {
        programmeCode: programme.code,
        programmeName: programme.name,
      });
  },
);
watch(
  () => allocationForm.applicationId,
  (id) => {
    const application = register.value.applications.find(
      (item) => item.id === id,
    );
    const period = setup.value.applicationPeriods.find(
      (item) => item.id === application?.applicationPeriodId,
    );
    if (period)
      Object.assign(allocationForm, {
        occupancyStartsOn: period.occupancyStartsOn,
        occupancyEndsOn: period.occupancyEndsOn,
      });
  },
);

async function load() {
  loading.value = true;
  loadError.value = "";
  loadWarnings.value = [];
  try {
    const [operationsResult, setupResult, financeResult, collectionsResult, registrationsResult, academicResult] = await Promise.allSettled(
      [
        api.request<AccommodationOperationsRegister>(
          "/api/accommodation/operations",
        ),
        api.request<AccommodationSetupRegister>("/api/accommodation/setup"),
        api.request<FinanceFeeCatalogueRegister>("/api/finance/fee-catalogues"),
        api.request<FinanceCollectionsRegister>("/api/finance/collections"),
        api.request<RegistrationSummary[]>("/api/student-records/registrations"),
        academicSetup.ensureOverview(),
      ],
    );
    if (operationsResult.status === "rejected") throw operationsResult.reason;
    if (setupResult.status === "rejected") throw setupResult.reason;
    const applicationPeriods = setupResult.value.applicationPeriods.filter(period => academicPeriodContext.matchesAcademicPeriod(period));
    const applicationPeriodIds = new Set(applicationPeriods.map(period => period.id));
    const applications = operationsResult.value.applications.filter(application => applicationPeriodIds.has(application.applicationPeriodId));
    const applicationIds = new Set(applications.map(application => application.id));
    const allocations = operationsResult.value.allocations.filter(allocation => applicationIds.has(allocation.applicationId));
    const allocationIds = new Set(allocations.map(allocation => allocation.id));
    register.value = {
      rates: operationsResult.value.rates.filter(rate => applicationPeriodIds.has(rate.applicationPeriodId)),
      applications,
      waitlistEntries: operationsResult.value.waitlistEntries.filter(entry => applicationPeriodIds.has(entry.applicationPeriodId)),
      allocations,
      allocationEvents: operationsResult.value.allocationEvents.filter(event => allocationIds.has(event.allocationId))
    };
    setup.value = { ...setupResult.value, applicationPeriods };
    if (financeResult.status === "fulfilled") finance.value = financeResult.value;
    else loadWarnings.value.push("Finance fee catalogues are unavailable; rate preparation is temporarily disabled.");
    if (collectionsResult.status === "fulfilled") financeCollections.value = collectionsResult.value;
    else loadWarnings.value.push("Exchange rates are unavailable; non-USD rate preparation is temporarily disabled.");
    if (registrationsResult.status === "fulfilled") registrations.value = registrationsResult.value.filter(registration => academicPeriodContext.matchesAcademicPeriod(registration));
    else loadWarnings.value.push("Confirmed registrations are unavailable; new accommodation applications are temporarily disabled.");
    if (academicResult.status === "rejected")
      loadWarnings.value.push("Academic programme details are unavailable; programme-linked actions are temporarily disabled.");
  } catch (error) {
    loadError.value = api.errorMessage(
      error,
      "Accommodation operations could not be loaded.",
    );
  } finally {
    loading.value = false;
  }
}

function openCreate(mode: Exclude<DrawerMode, "evaluation">) {
  drawerMode.value = mode;
  if (mode === "rate")
    Object.assign(rateForm, {
      applicationPeriodId: periodItems.value[0]?.value ?? "",
      roomTypeId: roomTypeItems.value[0]?.value ?? "",
      rateVersion: 1,
      financeFeeCatalogueId: feeItems.value[0]?.value ?? "",
      transactionCurrencyCode: "USD",
      indicativeTransactionAmount: 0,
      exchangeRateId: "",
      indicativeBaseAmount: null,
      effectiveFrom: "",
      effectiveUntil: "",
    });
  if (mode === "application")
    Object.assign(applicationForm, {
      applicationPeriodId:
        periodItems.value.find(
          (item) =>
            setup.value.applicationPeriods.find(
              (period) => period.id === item.value,
            )?.status === "APPLICATION_OPEN",
        )?.value ?? "",
      studentRegistrationId: studentItems.value[0]?.value ?? "",
      studentId: "",
      studentNumber: "",
      studentName: "",
      primaryEmail: "",
      genderCode: "FEMALE",
      disabilityCode: "",
      countryCode: "ZWE",
      locationCode: "",
      programmeId: programmeItems.value[0]?.value ?? "",
      programmeCode: "",
      programmeName: "",
      programmeLevel: 1,
      sponsorCode: "",
      paymentState: "UNKNOWN",
      preferredRoomTypeId: "",
      specialRequirements: "",
    });
  if (mode === "allocation")
    Object.assign(allocationForm, {
      applicationId: eligibleApplicationItems.value[0]?.value ?? "",
      roomId: roomItems.value[0]?.value ?? "",
      accommodationRateId: activeRateItems.value[0]?.value ?? "",
      occupancyStartsOn: "",
      occupancyEndsOn: "",
      reason: "",
    });
  drawerOpen.value = true;
}

function openEvaluation(application: AccommodationApplicationSummary) {
  selectedApplication.value = application;
  drawerMode.value = "evaluation";
  Object.assign(evaluationForm, {
    outcome: "ELIGIBLE",
    priorityScore: application.priorityScore,
    selectedGroupId: "",
    reason: "",
    expectedVersion: application.version,
  });
  drawerOpen.value = true;
}

async function submitDrawer() {
  saving.value = true;
  try {
    if (drawerMode.value === "rate")
      await api.request("/api/accommodation/operations/rates", {
        method: "POST",
        body: {
          ...rateForm,
          exchangeRateId: rateForm.exchangeRateId || null,
          indicativeBaseAmount:
            rateForm.transactionCurrencyCode === "USD"
              ? null
              : rateForm.indicativeBaseAmount,
          effectiveFrom: new Date(rateForm.effectiveFrom).toISOString(),
          effectiveUntil: rateForm.effectiveUntil
            ? new Date(rateForm.effectiveUntil).toISOString()
            : null,
        },
      });
    if (drawerMode.value === "application")
      await api.request("/api/accommodation/operations/applications", {
        method: "POST",
        body: {
          ...Object.fromEntries(Object.entries(applicationForm).filter(([key]) => key !== "studentRegistrationId")),
          preferredRoomTypeId: applicationForm.preferredRoomTypeId || null,
          disabilityCode: applicationForm.disabilityCode || null,
          locationCode: applicationForm.locationCode || null,
          sponsorCode: applicationForm.sponsorCode || null,
          specialRequirements: applicationForm.specialRequirements || null,
        },
      });
    if (drawerMode.value === "evaluation" && selectedApplication.value)
      await api.request(
        `/api/accommodation/operations/applications/${selectedApplication.value.id}/evaluate`,
        {
          method: "POST",
          body: {
            ...evaluationForm,
            selectedGroupId: evaluationForm.selectedGroupId || null,
          },
        },
      );
    if (drawerMode.value === "allocation")
      await api.request("/api/accommodation/operations/allocations", {
        method: "POST",
        body: allocationForm,
      });
    drawerOpen.value = false;
    await load();
    toast.add({
      title: `${title(drawerMode.value)} recorded`,
      color: "success",
      icon: "i-lucide-check-circle-2",
    });
  } catch (error) {
    await showError(
      `${title(drawerMode.value)} could not be recorded`,
      api.errorMessage(error),
    );
  } finally {
    saving.value = false;
  }
}

async function rateAction(
  rate: AccommodationRateSummary,
  targetStatus: "ACTIVE" | "RETIRED",
) {
  const reason = await requestEvidence(
    targetStatus === "ACTIVE"
      ? "Activate accommodation rate?"
      : "Retire accommodation rate?",
    "Record Finance fee, currency, effective-date, and independent approval checks.",
  );
  if (!reason) return;
  await perform(
    rate.id,
    `/api/accommodation/operations/rates/${rate.id}/transition`,
    { targetStatus, reason, expectedVersion: rate.version },
    `Rate ${title(targetStatus)}`,
  );
}

async function allocationAction(
  allocation: RoomAllocationSummary,
  action: "approve" | "check-in" | "check-out" | "cancel" | "withdraw",
) {
  const descriptions = {
    approve:
      "Confirm application eligibility, room capacity, rate, and maker-checker evidence.",
    "check-in": "Record identity, keys, and room inventory verification.",
    "check-out": "Record independent room inspection and key return evidence.",
    cancel: "Record why this proposal must be cancelled.",
    withdraw: "Record why this approved or occupied allocation is ending.",
  };
  const reason = await requestEvidence(
    `${title(action)} allocation?`,
    descriptions[action],
  );
  if (!reason) return;
  await perform(
    allocation.id,
    `/api/accommodation/operations/allocations/${allocation.id}/${action}`,
    { reason, expectedVersion: allocation.version },
    `Allocation ${title(action)}`,
  );
}

async function perform(
  id: string,
  path: string,
  body: object,
  successTitle: string,
) {
  operatingId.value = id;
  try {
    await api.request(path, { method: "POST", body });
    await load();
    toast.add({
      title: successTitle,
      color: "success",
      icon: "i-lucide-stamp",
    });
  } catch (error) {
    await showError(
      "Controlled action could not be completed",
      api.errorMessage(error),
    );
  } finally {
    operatingId.value = null;
  }
}

async function requestEvidence(titleText: string, description: string) {
  const result = await Swal.fire({
    title: titleText,
    text: description,
    input: "textarea",
    inputLabel: "Control evidence",
    inputPlaceholder: "Record the checks performed and the operational reason.",
    icon: "question",
    showCancelButton: true,
    confirmButtonText: "Confirm action",
    confirmButtonColor: "#006633",
    inputValidator: (value) =>
      value.trim() ? undefined : "Complete control evidence is required.",
  });
  return result.isConfirmed && result.value?.trim()
    ? result.value.trim()
    : null;
}

function title(value: string) {
  return value
    .replaceAll("-", " ")
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}
function money(amount: number, currency: string) {
  return new Intl.NumberFormat("en-ZW", { style: "currency", currency }).format(
    amount,
  );
}
function formatDate(value: string) {
  return new Intl.DateTimeFormat("en-ZW", { dateStyle: "medium" }).format(
    new Date(value),
  );
}
function tone(status: string) {
  if (
    [
      "ACTIVE",
      "RATED",
      "ELIGIBLE",
      "ALLOCATED",
      "CHECKED_IN",
      "PAID",
      "WAIVED",
    ].includes(status)
  )
    return "success";
  if (
    [
      "DRAFT",
      "SUBMITTED",
      "WAITLISTED",
      "PROPOSED",
      "PART_PAID",
      "UNRATED",
    ].includes(status)
  )
    return "warning";
  if (["REJECTED", "FAILED", "CANCELLED", "UNPAID"].includes(status))
    return "error";
  return "neutral";
}
</script>

<template>
  <UDashboardPanel>
    <template #header
      ><UDashboardNavbar title="Accommodation applications and occupancy"
        ><template #leading><UDashboardSidebarCollapse /></template
        ><template #right
          ><UButton
            label="Refresh"
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="outline"
            :loading="loading"
            @click="load" /></template></UDashboardNavbar
    ></template>
    <template #body>
      <div class="space-y-5 p-4 sm:p-6">
        <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <EmhareKpiCard
            label="Awaiting evaluation"
            :value="pendingEvaluation"
            icon="i-lucide-clipboard-clock"
            tone="warning"
          />
          <EmhareKpiCard
            label="Active wait-list"
            :value="activeWaitlist"
            icon="i-lucide-list-ordered"
            tone="primary"
          />
          <EmhareKpiCard
            label="Active allocations"
            :value="activeAllocations"
            icon="i-lucide-bed"
            tone="success"
          />
          <EmhareKpiCard
            label="Checked in"
            :value="occupiedBeds"
            icon="i-lucide-key-round"
            tone="success"
          />
        </div>
        <UAlert
          color="primary"
          variant="soft"
          icon="i-lucide-shield-check"
          title="Controlled residence lifecycle"
          description="Applications, wait-list ranking, room proposals, independent approval, check-in, check-out, and immutable workflow evidence are separated into auditable states."
        />
        <UAlert
          v-if="loadError"
          color="error"
          variant="soft"
          icon="i-lucide-circle-alert"
          title="Accommodation operations unavailable"
          :description="loadError"
        />
        <UAlert
          v-if="loadWarnings.length"
          color="warning"
          variant="soft"
          icon="i-lucide-triangle-alert"
          title="Some connected services are unavailable"
          :description="loadWarnings.join(' ')"
        />
        <UTabs
          v-model="activeDataset"
          :items="datasets"
          :content="false"
          color="primary"
          variant="pill"
        />

        <EmhareRegisterPanel
          v-if="activeDataset === 'applications'"
          title="Accommodation applications"
          description="Student and programme snapshots evaluated against the governed application period."
          :record-count="register.applications.length"
        >
          <template #actions
            ><EmhareGuidedActionButton
              label="Submit application"
              icon="i-lucide-plus"
              guidance-title="Accommodation application setup required"
              :guidance-instructions="[...(!periodItems.length ? ['Open an accommodation application period.'] : []), ...(!programmeItems.length ? ['Activate at least one programme.'] : []), ...(!studentItems.length ? ['Convert or activate at least one student.'] : [])]"
              @click="openCreate('application')"
          /></template>
          <EmharePaginatedTable
            :data="register.applications"
            :columns="applicationColumns"
            :loading="loading"
          >
            <template #studentNumber-cell="{ row }"
              ><div>
                <p class="font-medium">{{ row.original.studentNumber }}</p>
                <p class="text-xs text-muted">
                  {{ row.original.applicationPeriodCode }}
                </p>
              </div></template
            >
            <template #paymentState-cell="{ row }"
              ><EmhareStatusPill
                :label="title(row.original.paymentState)"
                :tone="tone(row.original.paymentState)"
            /></template>
            <template #status-cell="{ row }"
              ><EmhareStatusPill
                :label="title(row.original.status)"
                :tone="tone(row.original.status)"
            /></template>
            <template #actions-cell="{ row }"
              ><UButton
                v-if="row.original.status === 'SUBMITTED'"
                label="Evaluate"
                icon="i-lucide-clipboard-check"
                size="xs"
                color="neutral"
                variant="ghost"
                @click="openEvaluation(row.original)"
            /></template>
          </EmharePaginatedTable>
        </EmhareRegisterPanel>

        <EmhareRegisterPanel
          v-if="activeDataset === 'waitlist'"
          title="Prioritised wait-list"
          description="Serialized positions prevent two applicants from receiving the same active rank."
          :record-count="register.waitlistEntries.length"
        >
          <EmharePaginatedTable
            :data="register.waitlistEntries"
            :columns="waitlistColumns"
            :loading="loading"
          >
            <template #status-cell="{ row }"
              ><EmhareStatusPill
                :label="title(row.original.status)"
                :tone="tone(row.original.status)"
            /></template>
            <template #enteredAt-cell="{ row }">{{
              formatDate(row.original.enteredAt)
            }}</template>
          </EmharePaginatedTable>
        </EmhareRegisterPanel>

        <EmhareRegisterPanel
          v-if="activeDataset === 'allocations'"
          title="Room allocations and occupancy"
          description="Capacity-safe proposals progress through independent approval, check-in, and check-out."
          :record-count="register.allocations.length"
        >
          <template #actions
            ><EmhareGuidedActionButton
              label="Propose allocation"
              icon="i-lucide-plus"
              guidance-title="Room allocation setup required"
              :guidance-instructions="[...(!eligibleApplicationItems.length ? ['Approve or waitlist an eligible accommodation application.'] : []), ...(!activeRateItems.length ? ['Activate a rated accommodation rate.'] : []), ...(!roomItems.length ? ['Create an allocatable room with capacity.'] : [])]"
              @click="openCreate('allocation')"
          /></template>
          <EmharePaginatedTable
            :data="register.allocations"
            :columns="allocationColumns"
            :loading="loading"
          >
            <template #roomCode-cell="{ row }"
              ><div>
                <p class="font-medium">{{ row.original.roomCode }}</p>
                <p class="text-xs text-muted">
                  {{ row.original.residenceHallCode }}
                </p>
              </div></template
            >
            <template #occupancyStartsOn-cell="{ row }"
              >{{ formatDate(row.original.occupancyStartsOn) }} –
              {{ formatDate(row.original.occupancyEndsOn) }}</template
            >
            <template #billingStatus-cell="{ row }"
              ><EmhareStatusPill
                :label="title(row.original.billingStatus)"
                :tone="tone(row.original.billingStatus)"
            /></template>
            <template #status-cell="{ row }"
              ><EmhareStatusPill
                :label="title(row.original.status)"
                :tone="tone(row.original.status)"
            /></template>
            <template #actions-cell="{ row }"
              ><div class="flex flex-wrap justify-end gap-1">
                <UButton
                  v-if="row.original.status === 'PROPOSED'"
                  label="Approve"
                  size="xs"
                  :loading="operatingId === row.original.id"
                  @click="allocationAction(row.original, 'approve')"
                />
                <UButton
                  v-if="row.original.status === 'PROPOSED'"
                  label="Cancel"
                  size="xs"
                  color="neutral"
                  variant="ghost"
                  @click="allocationAction(row.original, 'cancel')"
                />
                <UButton
                  v-if="row.original.status === 'ALLOCATED'"
                  label="Check in"
                  size="xs"
                  icon="i-lucide-key-round"
                  :loading="operatingId === row.original.id"
                  @click="allocationAction(row.original, 'check-in')"
                />
                <UButton
                  v-if="row.original.status === 'CHECKED_IN'"
                  label="Check out"
                  size="xs"
                  icon="i-lucide-log-out"
                  :loading="operatingId === row.original.id"
                  @click="allocationAction(row.original, 'check-out')"
                />
                <UButton
                  v-if="
                    ['ALLOCATED', 'CHECKED_IN'].includes(row.original.status)
                  "
                  label="Withdraw"
                  size="xs"
                  color="error"
                  variant="ghost"
                  @click="allocationAction(row.original, 'withdraw')"
                /></div
            ></template>
          </EmharePaginatedTable>
        </EmhareRegisterPanel>

        <EmhareRegisterPanel
          v-if="activeDataset === 'rates'"
          title="Accommodation rates"
          description="Finance-linked room-type rates. USD is the base currency; unrated foreign-currency rates cannot be activated."
          :record-count="register.rates.length"
        >
          <template #actions
            ><EmhareGuidedActionButton
              label="Prepare rate"
              icon="i-lucide-plus"
              guidance-title="Accommodation rate setup required"
              :guidance-instructions="[...(!periodItems.length ? ['Open an accommodation application period.'] : []), ...(!roomTypeItems.length ? ['Create an active room type.'] : []), ...(!feeItems.length ? ['Create an active accommodation fee definition in Finance.'] : [])]"
              @click="openCreate('rate')"
          /></template>
          <EmharePaginatedTable
            :data="register.rates"
            :columns="rateColumns"
            :loading="loading"
          >
            <template #indicativeTransactionAmount-cell="{ row }"
              ><div>
                <p>
                  {{
                    money(
                      row.original.indicativeTransactionAmount,
                      row.original.transactionCurrencyCode,
                    )
                  }}
                </p>
                <p
                  v-if="
                    row.original.indicativeBaseAmount !== null &&
                    row.original.transactionCurrencyCode !== 'USD'
                  "
                  class="text-xs text-muted"
                >
                  {{ money(row.original.indicativeBaseAmount, "USD") }}
                </p>
              </div></template
            >
            <template #ratingStatus-cell="{ row }"
              ><EmhareStatusPill
                :label="title(row.original.ratingStatus)"
                :tone="tone(row.original.ratingStatus)"
            /></template>
            <template #status-cell="{ row }"
              ><EmhareStatusPill
                :label="title(row.original.status)"
                :tone="tone(row.original.status)"
            /></template>
            <template #actions-cell="{ row }"
              ><EmhareGuidedActionButton
                v-if="row.original.status === 'DRAFT'"
                label="Activate"
                size="xs"
                guidance-title="Accommodation rate cannot be activated"
                :guidance-instructions="row.original.ratingStatus === 'RATED' ? [] : ['Capture a valid effective exchange rate in Finance so the foreign-currency accommodation rate has a USD base amount.']"
                :loading="operatingId === row.original.id"
                @click="rateAction(row.original, 'ACTIVE')" /><UButton
                v-else-if="row.original.status === 'ACTIVE'"
                label="Retire"
                size="xs"
                color="neutral"
                variant="ghost"
                @click="rateAction(row.original, 'RETIRED')"
            /></template>
          </EmharePaginatedTable>
        </EmhareRegisterPanel>

        <EmhareRegisterPanel
          v-if="activeDataset === 'history'"
          title="Immutable allocation history"
          description="Append-only evidence for proposals, approvals, occupancy changes, and endings."
          :record-count="register.allocationEvents.length"
          ><EmharePaginatedTable :data="register.allocationEvents" :columns="eventColumns"
            ><template #occurredAt-cell="{ row }">{{
              formatDate(row.original.occurredAt)
            }}</template
            ><template #eventType-cell="{ row }"
              ><EmhareStatusPill
                :label="title(row.original.eventType)"
                :tone="tone(row.original.eventType)" /></template
            ><template #previousStatus-cell="{ row }">{{
              row.original.previousStatus
                ? title(row.original.previousStatus)
                : "—"
            }}</template
            ><template #newStatus-cell="{ row }">{{
              title(row.original.newStatus)
            }}</template></EmharePaginatedTable
          ></EmhareRegisterPanel
        >
      </div>
    </template>
  </UDashboardPanel>

  <EmhareRecordDrawer
    v-model:open="drawerOpen"
    :title="drawerTitle"
    :description="
      drawerMode === 'evaluation'
        ? 'Record a reasoned decision. Wait-list outcomes receive a serialized active position.'
        : 'Complete the controlled record. Required fields are marked.'
    "
    :busy="saving"
    submit-label="Record"
    width="xl"
    @submit="submitDrawer"
  >
    <template #body>
      <div v-if="drawerMode === 'rate'" class="space-y-4">
        <div class="grid gap-4 sm:grid-cols-2">
          <UFormField label="Application period" required
            ><USelect
              v-model="rateForm.applicationPeriodId"
              :items="periodItems"
              class="w-full" /></UFormField
          ><UFormField label="Room type" required
            ><USelect
              v-model="rateForm.roomTypeId"
              :items="roomTypeItems"
              class="w-full"
          /></UFormField>
        </div>
        <UFormField label="Finance accommodation fee" required
          ><USelect
            v-model="rateForm.financeFeeCatalogueId"
            :items="feeItems"
            class="w-full"
        /></UFormField>
        <div class="grid gap-4 sm:grid-cols-3">
          <UFormField label="Version" required
            ><UInput
              v-model.number="rateForm.rateVersion"
              type="number"
              min="1"
              class="w-full" /></UFormField
          ><UFormField label="Transaction currency" required
            ><USelect
              v-model="rateForm.transactionCurrencyCode"
              :items="[
                { label: 'USD · base currency', value: 'USD' },
                { label: 'ZWG · rate required', value: 'ZWG' },
              ]"
              class="w-full" /></UFormField
          ><UFormField label="Transaction amount" required
            ><UInput
              v-model.number="rateForm.indicativeTransactionAmount"
              type="number"
              min="0.0001"
              step="0.0001"
              class="w-full"
          /></UFormField>
        </div>
        <UAlert
          v-if="rateForm.transactionCurrencyCode !== 'USD'"
          color="warning"
          variant="soft"
          title="Foreign-currency rating"
          description="Leave exchange-rate fields blank to save this rate as unrated. It cannot be activated until a valid Finance exchange-rate reference and USD base amount are captured."
        />
        <div
          v-if="rateForm.transactionCurrencyCode !== 'USD'"
          class="grid gap-4 sm:grid-cols-2"
        >
          <UFormField label="Effective Finance exchange rate"
            ><USelectMenu
              v-model="rateForm.exchangeRateId"
              :items="exchangeRateItems"
              value-key="value"
              label-key="label"
              aria-label="Effective Finance exchange rate"
              placeholder="Search effective exchange rates"
              class="w-full" /></UFormField
          ><UFormField label="Indicative USD base amount"
            ><UInput
              v-model.number="rateForm.indicativeBaseAmount"
              type="number"
              min="0.0001"
              step="0.0001"
              class="w-full"
          /></UFormField>
        </div>
        <div class="grid gap-4 sm:grid-cols-2">
          <UFormField label="Effective from" required
            ><UInput
              v-model="rateForm.effectiveFrom"
              type="datetime-local"
              class="w-full" /></UFormField
          ><UFormField label="Effective until"
            ><UInput
              v-model="rateForm.effectiveUntil"
              type="datetime-local"
              class="w-full"
          /></UFormField>
        </div>
      </div>
      <div v-if="drawerMode === 'application'" class="space-y-4">
        <UFormField label="Open application period" required
          ><USelect
            v-model="applicationForm.applicationPeriodId"
            :items="periodItems"
            class="w-full"
        /></UFormField>
        <div class="grid gap-4 sm:grid-cols-2">
          <UFormField label="Confirmed student registration" required class="sm:col-span-2"
            ><USelectMenu
              v-model="applicationForm.studentRegistrationId"
              :items="studentItems"
              value-key="value"
              label-key="label"
              aria-label="Confirmed student registration"
              placeholder="Search by student number, name, or period"
              class="w-full" /></UFormField
          ><UFormField label="Student number" required
            ><UInput
              v-model="applicationForm.studentNumber"
              readonly
              class="w-full" /></UFormField
          ><UFormField label="Student name" required
            ><UInput
              v-model="applicationForm.studentName"
              readonly
              class="w-full" /></UFormField
          ><UFormField label="Primary email" required
            ><UInput
              v-model="applicationForm.primaryEmail"
              type="email"
              class="w-full"
          /></UFormField>
        </div>
        <div class="grid gap-4 sm:grid-cols-3">
          <UFormField label="Gender code" required
            ><USelect
              v-model="applicationForm.genderCode"
              :items="['FEMALE', 'MALE']"
              class="w-full" /></UFormField
          ><UFormField label="Country code" required
            ><UInput
              v-model="applicationForm.countryCode"
              maxlength="3"
              class="w-full" /></UFormField
          ><UFormField label="Location code"
            ><UInput v-model="applicationForm.locationCode" class="w-full"
          /></UFormField>
        </div>
        <UFormField label="Programme" required
          ><USelect
            v-model="applicationForm.programmeId"
            :items="programmeItems"
            class="w-full"
        /></UFormField>
        <div class="grid gap-4 sm:grid-cols-2">
          <UFormField label="Year of study" :description="selectedApplicationStudyStage" required
            ><USelect
              v-model="applicationForm.programmeLevel"
              :items="accommodationYearOfStudyItems"
              value-key="value"
              disabled
              class="w-full" /></UFormField
          ><UFormField label="Payment state" required
            ><USelect
              v-model="applicationForm.paymentState"
              :items="['PAID', 'WAIVED', 'PART_PAID', 'UNPAID', 'UNKNOWN']"
              class="w-full" /></UFormField
          ><UFormField label="Preferred room type"
            ><USelect
              v-model="applicationForm.preferredRoomTypeId"
              :items="roomTypeItems"
              class="w-full" /></UFormField
          ><UFormField label="Disability code"
            ><UInput v-model="applicationForm.disabilityCode" class="w-full"
          /></UFormField>
        </div>
        <UFormField label="Special requirements"
          ><UTextarea
            v-model="applicationForm.specialRequirements"
            class="w-full"
        /></UFormField>
      </div>
      <div v-if="drawerMode === 'evaluation'" class="space-y-4">
        <UAlert
          color="primary"
          variant="soft"
          :title="
            selectedApplication
              ? `${selectedApplication.applicationNumber} · ${selectedApplication.studentName}`
              : 'Application'
          "
          :description="
            selectedApplication
              ? `${selectedApplication.programmeCode} · ${title(selectedApplication.paymentState)}`
              : ''
          "
        />
        <div class="grid gap-4 sm:grid-cols-2">
          <UFormField label="Outcome" required
            ><USelect
              v-model="evaluationForm.outcome"
              :items="['ELIGIBLE', 'WAITLISTED', 'REJECTED']"
              class="w-full" /></UFormField
          ><UFormField label="Priority score" required
            ><UInput
              v-model.number="evaluationForm.priorityScore"
              type="number"
              class="w-full"
          /></UFormField>
        </div>
        <UFormField label="Evaluation evidence" required
          ><UTextarea
            v-model="evaluationForm.reason"
            :rows="5"
            class="w-full"
            placeholder="Record eligibility, priority, payment, disability, and policy checks."
        /></UFormField>
      </div>
      <div v-if="drawerMode === 'allocation'" class="space-y-4">
        <UAlert
          color="warning"
          variant="soft"
          title="Maker-checker proposal"
          description="The proposing operator cannot approve this allocation. PostgreSQL locks the room while validating overlapping capacity."
        /><UFormField label="Eligible or waitlisted application" required
          ><USelect
            v-model="allocationForm.applicationId"
            :items="eligibleApplicationItems"
            class="w-full"
        /></UFormField>
        <div class="grid gap-4 sm:grid-cols-2">
          <UFormField label="Available room" required
            ><USelect
              v-model="allocationForm.roomId"
              :items="roomItems"
              class="w-full" /></UFormField
          ><UFormField label="Active rated price" required
            ><USelect
              v-model="allocationForm.accommodationRateId"
              :items="activeRateItems"
              class="w-full" /></UFormField
          ><UFormField label="Occupancy starts" required
            ><UInput
              v-model="allocationForm.occupancyStartsOn"
              type="date"
              class="w-full" /></UFormField
          ><UFormField label="Occupancy ends" required
            ><UInput
              v-model="allocationForm.occupancyEndsOn"
              type="date"
              class="w-full"
          /></UFormField>
        </div>
        <UFormField label="Proposal evidence" required
          ><UTextarea
            v-model="allocationForm.reason"
            :rows="4"
            class="w-full"
            placeholder="Record capacity, room policy, preference, and eligibility checks."
        /></UFormField>
      </div>
    </template>
  </EmhareRecordDrawer>
</template>
