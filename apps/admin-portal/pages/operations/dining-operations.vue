<script setup lang="ts">
import type { TableColumn } from "@nuxt/ui";
import Swal from "sweetalert2";
import type { RegistrationSummary } from "@emhare/portal-shell/types/student-records";
import type { AccommodationOperationsRegister } from "@emhare/portal-shell/types/accommodation";
import type {
  DiningOperationsRegister,
  DiningAttendanceStatisticSummary,
  DiningSetupRegister,
  DiningWorkflowEventSummary,
  MealAttendanceSummary,
  MealServiceSessionSummary,
  StudentDietaryRequirementSummary,
  StudentDiningAssignmentSummary,
} from "@emhare/portal-shell/types/dining";

definePageMeta({ layout: "dashboard" });

type DrawerMode = "assignment" | "dietary" | "session" | "attendance" | "reconcile";

const api = useEmhareApi();
const toast = useToast();
const { showError } = useEmhareConfirm();
const academicPeriodContext = useAcademicPeriodContext();
const loading = ref(false);
const saving = ref(false);
const actionRecordId = ref<string | null>(null);
const loadError = ref("");
const dependencyWarnings = ref<string[]>([]);
const activeDataset = ref("assignments");
const drawerOpen = ref(false);
const drawerMode = ref<DrawerMode>("assignment");
const selectedSession = ref<MealServiceSessionSummary | null>(null);
const lastAttendanceResult = ref<MealAttendanceSummary | null>(null);

const register = ref<DiningOperationsRegister>({ assignments: [], dietaryRequirements: [], sessions: [], attendanceEvents: [], reversals: [], workflowEvents: [], attendanceStatistics: [] });
const setup = ref<DiningSetupRegister>({ diningHalls: [], mealOptions: [], serviceTimes: [], diningPlans: [], planMeals: [], hallAssignmentRules: [], attendantAssignments: [] });
const registrations = ref<RegistrationSummary[]>([]);
const accommodationOperations = ref<AccommodationOperationsRegister>({ rates: [], applications: [], waitlistEntries: [], allocations: [], allocationEvents: [] });

const assignmentForm = reactive({ studentId: "", studentNumber: "", studentName: "", academicPeriodId: "", academicPeriodCode: "", programmeCode: "", studentGroupCode: "", diningHallId: "", diningPlanId: "", accommodationAllocationId: "", effectiveFrom: "", effectiveUntil: "" });
const dietaryForm = reactive({ studentId: "", studentNumber: "", requirementCode: "", description: "", severity: "IMPORTANT", clinicalDocumentId: "", effectiveFrom: "", effectiveUntil: "" });
const sessionForm = reactive({ diningHallId: "", mealOptionId: "", serviceDate: "", scheduledOpensAt: "", scheduledClosesAt: "", expectedServings: null as number | null });
const attendanceForm = reactive({ sessionId: "", studentId: "", studentNumber: "", studentName: "", captureChannel: "ONLINE", deviceId: "", idempotencyKey: "" });
const reconcileForm = reactive({ countedServings: 0, reason: "", expectedVersion: 0 });

const assignmentColumns: TableColumn<StudentDiningAssignmentSummary>[] = [
  { accessorKey: "assignmentNumber", header: "Assignment" }, { accessorKey: "studentNumber", header: "Student" },
  { accessorKey: "studentName", header: "Name" }, { accessorKey: "academicPeriodCode", header: "Academic period" },
  { accessorKey: "diningHallCode", header: "Dining hall" }, { accessorKey: "diningPlanCode", header: "Dining plan" },
  { accessorKey: "billingStatus", header: "Billing" }, { accessorKey: "status", header: "Status" }, { accessorKey: "actions", header: "" },
];
const dietaryColumns: TableColumn<StudentDietaryRequirementSummary>[] = [
  { accessorKey: "studentNumber", header: "Student" }, { accessorKey: "requirementCode", header: "Requirement" },
  { accessorKey: "description", header: "Description" }, { accessorKey: "severity", header: "Severity" },
  { accessorKey: "effectiveFrom", header: "Effective window" }, { accessorKey: "status", header: "Status" }, { accessorKey: "actions", header: "" },
];
const sessionColumns: TableColumn<MealServiceSessionSummary>[] = [
  { accessorKey: "sessionNumber", header: "Session" }, { accessorKey: "serviceDate", header: "Service date" },
  { accessorKey: "diningHallCode", header: "Dining hall" }, { accessorKey: "mealOptionCode", header: "Meal" },
  { accessorKey: "scheduledOpensAt", header: "Service window" }, { accessorKey: "expectedServings", header: "Expected" },
  { accessorKey: "netAdmitted", header: "Net admitted" }, { accessorKey: "status", header: "Status" }, { accessorKey: "actions", header: "" },
];
const attendanceColumns: TableColumn<MealAttendanceSummary>[] = [
  { accessorKey: "eventNumber", header: "Event" }, { accessorKey: "sessionNumber", header: "Session" },
  { accessorKey: "studentNumber", header: "Student" }, { accessorKey: "studentName", header: "Name" },
  { accessorKey: "outcome", header: "Outcome" }, { accessorKey: "denialReason", header: "Decision reason" },
  { accessorKey: "captureChannel", header: "Channel" }, { accessorKey: "capturedAt", header: "Captured" },
  { accessorKey: "reversed", header: "Evidence state" }, { accessorKey: "actions", header: "" },
];
const workflowColumns: TableColumn<DiningWorkflowEventSummary>[] = [
  { accessorKey: "occurredAt", header: "Occurred" }, { accessorKey: "aggregateType", header: "Record type" },
  { accessorKey: "eventType", header: "Event" }, { accessorKey: "previousState", header: "From" },
  { accessorKey: "newState", header: "To" }, { accessorKey: "reason", header: "Decision evidence" },
];
const statisticColumns: TableColumn<DiningAttendanceStatisticSummary>[] = [
  { accessorKey: "dimension", header: "Dimension" }, { accessorKey: "groupCode", header: "Group" },
  { accessorKey: "admitted", header: "Admitted" }, { accessorKey: "denied", header: "Denied" },
  { accessorKey: "reversed", header: "Reversed" }, { accessorKey: "netAdmitted", header: "Net attendance" },
];

const datasets = computed(() => [
  { label: "Student assignments", value: "assignments", icon: "i-lucide-users-round", badge: register.value.assignments.length },
  { label: "Dietary requirements", value: "dietary", icon: "i-lucide-shield-alert", badge: activeDietaryCount.value },
  { label: "Service sessions", value: "sessions", icon: "i-lucide-calendar-clock", badge: register.value.sessions.length },
  { label: "Meal attendance", value: "attendance", icon: "i-lucide-scan-line", badge: register.value.attendanceEvents.length },
  { label: "Attendance statistics", value: "statistics", icon: "i-lucide-chart-no-axes-column-increasing", badge: register.value.attendanceStatistics.length },
  { label: "Workflow history", value: "history", icon: "i-lucide-history", badge: register.value.workflowEvents.length },
]);
const confirmedRegistrations = computed(() => registrations.value.filter((item) => item.status === "CONFIRMED"));
const studentItems = computed(() => confirmedRegistrations.value.map((item) => ({ label: `${item.studentNumber} · ${item.studentName} · ${item.academicPeriodCode}`, value: `${item.studentId}:${item.academicPeriodId}`, registration: item })));
const hallItems = computed(() => setup.value.diningHalls.filter((item) => item.active).map((item) => ({ label: `${item.code} · ${item.name}`, value: item.id })));
const planItems = computed(() => setup.value.diningPlans.filter((item) => item.status === "ACTIVE").map((item) => ({ label: `${item.code} v${item.planVersion} · ${item.name}`, value: item.id, validFrom: item.validFrom, validUntil: item.validUntil })));
const mealItems = computed(() => setup.value.mealOptions.filter((item) => item.active).map((item) => ({ label: `${item.code} · ${item.name}`, value: item.id })));
const openSessionItems = computed(() => register.value.sessions.filter((item) => item.status === "OPEN").map((item) => ({ label: `${item.sessionNumber} · ${item.diningHallCode} · ${item.mealOptionCode}`, value: item.id })));
const accommodationAllocationItems = computed(() => accommodationOperations.value.allocations
  .filter((item) => ["ALLOCATED", "CHECKED_IN"].includes(item.status))
  .map((item) => ({ label: `${item.allocationNumber} · ${item.studentNumber} · ${item.residenceHallCode}/${item.roomCode}`, value: item.id })));
const activeAssignmentCount = computed(() => register.value.assignments.filter((item) => item.status === "ACTIVE").length);
const activeDietaryCount = computed(() => register.value.dietaryRequirements.filter((item) => item.status === "ACTIVE").length);
const openSessionCount = computed(() => register.value.sessions.filter((item) => item.status === "OPEN").length);
const todayNetAttendance = computed(() => register.value.attendanceEvents.filter((item) => item.outcome === "ADMITTED" && !item.reversed && item.capturedAt.slice(0, 10) === new Date().toISOString().slice(0, 10)).length);
const selectedAttendanceDietary = computed(() => register.value.dietaryRequirements.filter((item) => item.studentId === businessStudentId(attendanceForm.studentId) && item.status === "ACTIVE"));
const drawerTitle = computed(() => ({ assignment: "Prepare student dining assignment", dietary: "Record dietary requirement", session: "Plan meal service session", attendance: "Capture meal attendance", reconcile: "Reconcile meal service session" })[drawerMode.value]);
watch(() => assignmentForm.studentId, fillAssignmentStudent);
watch(() => dietaryForm.studentId, fillDietaryStudent);
watch(() => attendanceForm.studentId, fillAttendanceStudent);
watch(() => assignmentForm.diningPlanId, (planId) => { const plan = planItems.value.find((item) => item.value === planId); if (plan) { assignmentForm.effectiveFrom = plan.validFrom; assignmentForm.effectiveUntil = plan.validUntil ?? ""; } });
onMounted(load);
watch(academicPeriodContext.selectedAcademicPeriodId, () => void load());

async function load() {
  loading.value = true; loadError.value = ""; dependencyWarnings.value = [];
  await academicPeriodContext.ensureAcademicPeriods().catch(() => undefined);
  const [operationsResult, setupResult, registrationsResult, accommodationResult] = await Promise.allSettled([
    api.request<DiningOperationsRegister>("/api/dining/operations"),
    api.request<DiningSetupRegister>("/api/dining/setup"),
    api.request<RegistrationSummary[]>("/api/student-records/registrations"),
    api.request<AccommodationOperationsRegister>("/api/accommodation/operations"),
  ]);

  if (operationsResult.status === "fulfilled") {
    const visibleAssignments = operationsResult.value.assignments.filter(assignment => academicPeriodContext.matchesAcademicPeriod(assignment));
    const visibleStudentIds = new Set(visibleAssignments.map(assignment => assignment.studentId));
    const selectedPeriod = academicPeriodContext.selectedAcademicPeriod.value;
    const visibleSessions = operationsResult.value.sessions.filter(session => (
      !selectedPeriod || (session.serviceDate >= selectedPeriod.startDate && session.serviceDate <= selectedPeriod.endDate)
    ));
    const visibleSessionIds = new Set(visibleSessions.map(session => session.id));
    const visibleAttendance = operationsResult.value.attendanceEvents.filter(event => visibleSessionIds.has(event.sessionId));
    const visibleAttendanceIds = new Set(visibleAttendance.map(event => event.id));
    const visibleAggregateIds = new Set([
      ...visibleAssignments.map(assignment => assignment.id),
      ...visibleSessions.map(session => session.id)
    ]);
    register.value = {
      assignments: visibleAssignments,
      dietaryRequirements: operationsResult.value.dietaryRequirements.filter(requirement => visibleStudentIds.has(requirement.studentId)),
      sessions: visibleSessions,
      attendanceEvents: visibleAttendance,
      reversals: operationsResult.value.reversals.filter(reversal => visibleAttendanceIds.has(reversal.attendanceEventId)),
      workflowEvents: operationsResult.value.workflowEvents.filter(event => visibleAggregateIds.has(event.aggregateId)),
      attendanceStatistics: operationsResult.value.attendanceStatistics.filter(statistic => (
        statistic.dimension === "ACADEMIC_PERIOD" && statistic.groupCode === academicPeriodContext.selectedAcademicPeriodCode.value
      ))
    };
  }
  else loadError.value = api.errorMessage(operationsResult.reason, "Dining operations could not be loaded.");

  if (setupResult.status === "fulfilled") setup.value = setupResult.value;
  else {
    setup.value = { diningHalls: [], mealOptions: [], serviceTimes: [], diningPlans: [], planMeals: [], hallAssignmentRules: [], attendantAssignments: [] };
    dependencyWarnings.value.push("Dining setup is temporarily unavailable. Existing operational records remain visible, but setup-dependent actions are disabled.");
  }

  if (registrationsResult.status === "fulfilled") registrations.value = registrationsResult.value.filter(registration => academicPeriodContext.matchesAcademicPeriod(registration));
  else {
    registrations.value = [];
    dependencyWarnings.value.push("Student Records registration data is temporarily unavailable. Existing dining operations remain visible, but student-dependent actions are disabled.");
  }

  if (accommodationResult.status === "fulfilled") {
    const visibleApplications = accommodationResult.value.applications.filter(application => (
      registrations.value.some(registration => registration.studentId === application.studentId)
    ));
    const visibleApplicationIds = new Set(visibleApplications.map(application => application.id));
    accommodationOperations.value = {
      ...accommodationResult.value,
      applications: visibleApplications,
      allocations: accommodationResult.value.allocations.filter(allocation => visibleApplicationIds.has(allocation.applicationId))
    };
  }
  else {
    accommodationOperations.value = { rates: [], applications: [], waitlistEntries: [], allocations: [], allocationEvents: [] };
    dependencyWarnings.value.push("Accommodation allocation data is temporarily unavailable. Dining assignments can still be prepared without an accommodation link.");
  }

  loading.value = false;
}

function selectedRegistration(compositeStudentId: string) { return studentItems.value.find((item) => item.value === compositeStudentId)?.registration; }
function fillAssignmentStudent(value: string) { const student = selectedRegistration(value); if (student) Object.assign(assignmentForm, { studentId: value, studentNumber: student.studentNumber, studentName: student.studentName, academicPeriodId: student.academicPeriodId, academicPeriodCode: student.academicPeriodCode, programmeCode: student.programmeCode, effectiveFrom: student.academicPeriodStartsOn, effectiveUntil: student.academicPeriodEndsOn }); }
function fillDietaryStudent(value: string) { const student = selectedRegistration(value); if (student) Object.assign(dietaryForm, { studentId: value, studentNumber: student.studentNumber }); }
function fillAttendanceStudent(value: string) { const student = selectedRegistration(value); if (student) Object.assign(attendanceForm, { studentId: value, studentNumber: student.studentNumber, studentName: student.studentName }); }
function businessStudentId(compositeStudentId: string) { return compositeStudentId.split(":")[0] ?? compositeStudentId; }

function openCreate(mode: Exclude<DrawerMode, "reconcile">) {
  drawerMode.value = mode; lastAttendanceResult.value = null;
  const firstStudent = studentItems.value[0];
  if (mode === "assignment") { const registration = firstStudent?.registration; Object.assign(assignmentForm, { studentId: firstStudent?.value ?? "", studentNumber: registration?.studentNumber ?? "", studentName: registration?.studentName ?? "", academicPeriodId: registration?.academicPeriodId ?? "", academicPeriodCode: registration?.academicPeriodCode ?? "", programmeCode: registration?.programmeCode ?? "", studentGroupCode: "", diningHallId: hallItems.value[0]?.value ?? "", diningPlanId: planItems.value[0]?.value ?? "", accommodationAllocationId: "", effectiveFrom: registration?.academicPeriodStartsOn ?? "", effectiveUntil: registration?.academicPeriodEndsOn ?? "" }); }
  if (mode === "dietary") { const registration = firstStudent?.registration; Object.assign(dietaryForm, { studentId: firstStudent?.value ?? "", studentNumber: registration?.studentNumber ?? "", requirementCode: "", description: "", severity: "IMPORTANT", clinicalDocumentId: "", effectiveFrom: new Date().toISOString().slice(0, 10), effectiveUntil: "" }); }
  if (mode === "session") Object.assign(sessionForm, { diningHallId: hallItems.value[0]?.value ?? "", mealOptionId: mealItems.value[0]?.value ?? "", serviceDate: new Date().toISOString().slice(0, 10), scheduledOpensAt: "", scheduledClosesAt: "", expectedServings: null });
  if (mode === "attendance") { const registration = firstStudent?.registration; Object.assign(attendanceForm, { sessionId: openSessionItems.value[0]?.value ?? "", studentId: firstStudent?.value ?? "", studentNumber: registration?.studentNumber ?? "", studentName: registration?.studentName ?? "", captureChannel: "ONLINE", deviceId: "", idempotencyKey: newIdempotencyKey() }); }
  drawerOpen.value = true;
}

function openReconcile(session: MealServiceSessionSummary) { selectedSession.value = session; drawerMode.value = "reconcile"; Object.assign(reconcileForm, { countedServings: session.netAdmitted, reason: "", expectedVersion: session.version }); drawerOpen.value = true; }

async function submitDrawer() {
  saving.value = true;
  try {
    if (drawerMode.value === "assignment") await api.request("/api/dining/operations/assignments", { method: "POST", body: { ...assignmentForm, studentId: businessStudentId(assignmentForm.studentId), studentGroupCode: assignmentForm.studentGroupCode || null, accommodationAllocationId: assignmentForm.accommodationAllocationId || null } });
    if (drawerMode.value === "dietary") await api.request("/api/dining/operations/dietary-requirements", { method: "POST", body: { ...dietaryForm, studentId: businessStudentId(dietaryForm.studentId), clinicalDocumentId: dietaryForm.clinicalDocumentId || null, effectiveUntil: dietaryForm.effectiveUntil || null } });
    if (drawerMode.value === "session") await api.request("/api/dining/operations/sessions", { method: "POST", body: { ...sessionForm, scheduledOpensAt: new Date(sessionForm.scheduledOpensAt).toISOString(), scheduledClosesAt: new Date(sessionForm.scheduledClosesAt).toISOString() } });
    if (drawerMode.value === "reconcile" && selectedSession.value) await api.request(`/api/dining/operations/sessions/${selectedSession.value.id}/reconcile`, { method: "POST", body: reconcileForm });
    if (drawerMode.value === "attendance") {
      lastAttendanceResult.value = await api.request<MealAttendanceSummary>("/api/dining/operations/attendance", { method: "POST", body: { ...attendanceForm, studentId: businessStudentId(attendanceForm.studentId), deviceId: attendanceForm.deviceId || null } });
      attendanceForm.idempotencyKey = newIdempotencyKey();
      toast.add({ title: lastAttendanceResult.value.outcome === "ADMITTED" ? "Meal access admitted" : "Meal access denied", description: lastAttendanceResult.value.denialReason ?? undefined, color: lastAttendanceResult.value.outcome === "ADMITTED" ? "success" : "error", icon: lastAttendanceResult.value.outcome === "ADMITTED" ? "i-lucide-circle-check" : "i-lucide-ban" });
      await load(); return;
    }
    drawerOpen.value = false; toast.add({ title: "Dining operation recorded", color: "success", icon: "i-lucide-circle-check" }); await load();
  } catch (error) { await showError("Dining operation failed", api.errorMessage(error)); }
  finally { saving.value = false; }
}

async function evidenceAction(titleText: string, confirmText: string, endpoint: string, expectedVersion: number, extraBody: Record<string, unknown> = {}) {
  const result = await Swal.fire({ title: titleText, input: "textarea", inputLabel: "Decision evidence", inputPlaceholder: "Record the reason, authority, and relevant operational context…", showCancelButton: true, confirmButtonText: confirmText, confirmButtonColor: "#20743a", inputValidator: (value) => value.trim() ? undefined : "Decision evidence is required." });
  if (!result.isConfirmed) return false;
  await api.request(endpoint, { method: "POST", body: { ...extraBody, reason: result.value, expectedVersion } }); return true;
}

async function assignmentAction(assignment: StudentDiningAssignmentSummary, action: "activate" | "suspend" | "resume" | "end" | "cancel") {
  actionRecordId.value = assignment.id;
  try { if (await evidenceAction(`${title(action)} dining assignment`, title(action), `/api/dining/operations/assignments/${assignment.id}/${action}`, assignment.version)) { const completedAction = { activate: "activated", suspend: "suspended", resume: "resumed", end: "ended", cancel: "cancelled" }[action]; toast.add({ title: `Assignment ${completedAction}`, color: "success" }); await load(); } }
  catch (error) { await showError("Assignment action failed", api.errorMessage(error)); }
  finally { actionRecordId.value = null; }
}

async function dietaryAction(requirement: StudentDietaryRequirementSummary, targetStatus: "RESOLVED" | "EXPIRED") {
  actionRecordId.value = requirement.id;
  try { if (await evidenceAction(`${title(targetStatus)} dietary requirement`, title(targetStatus), `/api/dining/operations/dietary-requirements/${requirement.id}/resolve`, requirement.version, { targetStatus })) { toast.add({ title: `Dietary requirement ${targetStatus.toLowerCase()}`, color: "success" }); await load(); } }
  catch (error) { await showError("Dietary action failed", api.errorMessage(error)); }
  finally { actionRecordId.value = null; }
}

async function sessionAction(session: MealServiceSessionSummary, action: "open" | "close") {
  actionRecordId.value = session.id;
  try { if (await evidenceAction(`${title(action)} meal service`, title(action), `/api/dining/operations/sessions/${session.id}/${action}`, session.version)) { toast.add({ title: `Meal service ${action === "open" ? "opened" : "closed"}`, color: "success" }); await load(); } }
  catch (error) { await showError("Meal service action failed", api.errorMessage(error)); }
  finally { actionRecordId.value = null; }
}

async function reverseAttendance(event: MealAttendanceSummary) {
  const result = await Swal.fire({ title: "Reverse meal attendance evidence", icon: "warning", input: "textarea", inputLabel: "Reversal reason", inputPlaceholder: "Record why the original attendance event is invalid…", showCancelButton: true, confirmButtonText: "Record reversal", confirmButtonColor: "#b91c1c", inputValidator: (value) => value.trim() ? undefined : "A reversal reason is required." });
  if (!result.isConfirmed) return;
  actionRecordId.value = event.id;
  try { await api.request(`/api/dining/operations/attendance/${event.id}/reverse`, { method: "POST", body: { reasonCode: "OPERATOR_CORRECTION", reason: result.value } }); toast.add({ title: "Attendance reversal recorded", color: "success" }); await load(); }
  catch (error) { await showError("Attendance could not be reversed", api.errorMessage(error)); }
  finally { actionRecordId.value = null; }
}

function newIdempotencyKey() { return globalThis.crypto?.randomUUID?.() ?? `meal-${Date.now()}-${Math.random().toString(16).slice(2)}`; }
function title(value: string) { return value.replaceAll("-", "_").split("_").map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase()).join(" "); }
function formatDateTime(value: string | null) { return value ? new Intl.DateTimeFormat("en-ZW", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "—"; }
function statusTone(status: string): "success" | "warning" | "error" | "neutral" | "primary" { return ["ACTIVE", "OPEN", "ADMITTED", "RECONCILED"].includes(status) ? "success" : ["DRAFT", "PLANNED", "SUSPENDED", "IMPORTANT"].includes(status) ? "warning" : ["DENIED", "CRITICAL", "CANCELLED"].includes(status) ? "error" : "neutral"; }
</script>

<template>
  <UDashboardPanel id="dining-operations">
    <template #header><UDashboardNavbar title="Dining operations"><template #leading><UDashboardSidebarCollapse /></template><template #right><UButton label="Refresh" icon="i-lucide-refresh-cw" color="neutral" variant="outline" :loading="loading" @click="load" /></template></UDashboardNavbar></template>
    <template #body>
      <div class="space-y-5">
        <div class="grid gap-3 sm:grid-cols-4"><EmhareKpiCard label="Active assignments" :value="activeAssignmentCount" icon="i-lucide-users-round" tone="primary" /><EmhareKpiCard label="Active dietary controls" :value="activeDietaryCount" icon="i-lucide-shield-alert" :tone="activeDietaryCount ? 'warning' : 'neutral'" /><EmhareKpiCard label="Open meal sessions" :value="openSessionCount" icon="i-lucide-calendar-clock" tone="success" /><EmhareKpiCard label="Today's net attendance" :value="todayNetAttendance" icon="i-lucide-scan-line" tone="neutral" /></div>
        <UAlert color="primary" variant="soft" icon="i-lucide-shield-check" title="Auditable meal access" description="The service decides entitlement at capture time. Attendance is immutable; corrections create compensating reversal evidence. Assignments, dietary controls, and sessions preserve workflow decisions." />
        <UAlert v-if="loadError" color="error" variant="soft" icon="i-lucide-circle-alert" title="Dining operations unavailable" :description="loadError" />
        <UAlert v-if="dependencyWarnings.length" color="warning" variant="soft" icon="i-lucide-triangle-alert" title="Limited integration availability" :description="dependencyWarnings.join(' ')" />
        <UTabs v-model="activeDataset" :items="datasets" :content="false" color="primary" variant="pill" />

        <EmhareRegisterPanel v-if="activeDataset === 'assignments'" title="Student dining assignments" description="Period-bound, hall-specific student entitlements with maker-checker activation." :record-count="register.assignments.length"><template #actions><EmhareGuidedActionButton label="Prepare assignment" icon="i-lucide-plus" guidance-title="Dining assignment setup required" :guidance-instructions="[...(!studentItems.length ? ['Convert or activate at least one student.'] : []), ...(!hallItems.length ? ['Create an active dining hall.'] : []), ...(!planItems.length ? ['Activate a dining plan.'] : [])]" @click="openCreate('assignment')" /></template><EmharePaginatedTable :data="register.assignments" :columns="assignmentColumns" :loading="loading"><template #billingStatus-cell="{ row }"><EmhareStatusPill :label="title(row.original.billingStatus)" :tone="statusTone(row.original.billingStatus)" /></template><template #status-cell="{ row }"><EmhareStatusPill :label="title(row.original.status)" :tone="statusTone(row.original.status)" /></template><template #actions-cell="{ row }"><div class="flex justify-end gap-1"><UButton v-if="row.original.status === 'DRAFT'" label="Activate" size="xs" icon="i-lucide-stamp" :loading="actionRecordId === row.original.id" @click="assignmentAction(row.original, 'activate')" /><UButton v-if="row.original.status === 'ACTIVE'" label="Suspend" size="xs" color="warning" variant="soft" @click="assignmentAction(row.original, 'suspend')" /><UButton v-if="row.original.status === 'SUSPENDED'" label="Resume" size="xs" @click="assignmentAction(row.original, 'resume')" /><UDropdownMenu v-if="['DRAFT','ACTIVE','SUSPENDED'].includes(row.original.status)" :items="[[{ label: 'End assignment', icon: 'i-lucide-circle-stop', onSelect: () => assignmentAction(row.original, 'end') }, { label: 'Cancel assignment', icon: 'i-lucide-ban', color: 'error', onSelect: () => assignmentAction(row.original, 'cancel') }]]"><UButton icon="i-lucide-ellipsis" size="xs" color="neutral" variant="ghost" /></UDropdownMenu></div></template></EmharePaginatedTable></EmhareRegisterPanel>

        <EmhareRegisterPanel v-if="activeDataset === 'dietary'" title="Dietary requirements" description="Time-bound meal safety controls, with clinical document linkage where supplied." :record-count="register.dietaryRequirements.length"><template #actions><EmhareGuidedActionButton label="Record requirement" icon="i-lucide-plus" guidance-title="Dietary requirement setup required" :guidance-instructions="studentItems.length ? [] : ['Convert or activate at least one student before recording a dietary requirement.']" @click="openCreate('dietary')" /></template><EmharePaginatedTable :data="register.dietaryRequirements" :columns="dietaryColumns" :loading="loading"><template #severity-cell="{ row }"><EmhareStatusPill :label="title(row.original.severity)" :tone="statusTone(row.original.severity)" /></template><template #effectiveFrom-cell="{ row }">{{ row.original.effectiveFrom }} – {{ row.original.effectiveUntil || 'Open-ended' }}</template><template #status-cell="{ row }"><EmhareStatusPill :label="title(row.original.status)" :tone="statusTone(row.original.status)" /></template><template #actions-cell="{ row }"><UDropdownMenu v-if="row.original.status === 'ACTIVE'" :items="[[{ label: 'Resolve', icon: 'i-lucide-circle-check', onSelect: () => dietaryAction(row.original, 'RESOLVED') }, { label: 'Expire', icon: 'i-lucide-calendar-x', onSelect: () => dietaryAction(row.original, 'EXPIRED') }]]"><UButton label="Update state" icon="i-lucide-ellipsis" size="xs" color="neutral" variant="ghost" /></UDropdownMenu></template></EmharePaginatedTable></EmhareRegisterPanel>

        <EmhareRegisterPanel v-if="activeDataset === 'sessions'" title="Meal service sessions" description="Controlled meal windows with maker-checker opening, closing, and reconciliation." :record-count="register.sessions.length"><template #actions><EmhareGuidedActionButton label="Plan session" icon="i-lucide-plus" guidance-title="Meal session setup required" :guidance-instructions="[...(!hallItems.length ? ['Create an active dining hall.'] : []), ...(!mealItems.length ? ['Create an active meal option.'] : [])]" @click="openCreate('session')" /></template><EmharePaginatedTable :data="register.sessions" :columns="sessionColumns" :loading="loading"><template #scheduledOpensAt-cell="{ row }">{{ formatDateTime(row.original.scheduledOpensAt) }} – {{ formatDateTime(row.original.scheduledClosesAt) }}</template><template #status-cell="{ row }"><EmhareStatusPill :label="title(row.original.status)" :tone="statusTone(row.original.status)" /></template><template #actions-cell="{ row }"><div class="flex justify-end gap-1"><UButton v-if="row.original.status === 'PLANNED'" label="Open" icon="i-lucide-door-open" size="xs" @click="sessionAction(row.original, 'open')" /><UButton v-if="row.original.status === 'OPEN'" label="Close" icon="i-lucide-door-closed" size="xs" color="warning" variant="soft" @click="sessionAction(row.original, 'close')" /><UButton v-if="row.original.status === 'CLOSED'" label="Reconcile" icon="i-lucide-scale" size="xs" @click="openReconcile(row.original)" /></div></template></EmharePaginatedTable></EmhareRegisterPanel>

        <EmhareRegisterPanel v-if="activeDataset === 'attendance'" title="Meal attendance evidence" description="Append-only admission and denial decisions. Reversals remain separately auditable." :record-count="register.attendanceEvents.length"><template #actions><EmhareGuidedActionButton label="Capture attendance" icon="i-lucide-scan-line" guidance-title="Meal attendance is not ready" :guidance-instructions="[...(!openSessionItems.length ? ['Open a planned meal service session.'] : []), ...(!studentItems.length ? ['Convert or activate at least one student.'] : [])]" @click="openCreate('attendance')" /></template><EmharePaginatedTable :data="register.attendanceEvents" :columns="attendanceColumns" :loading="loading"><template #outcome-cell="{ row }"><EmhareStatusPill :label="title(row.original.outcome)" :tone="statusTone(row.original.outcome)" /></template><template #captureChannel-cell="{ row }">{{ title(row.original.captureChannel) }}</template><template #capturedAt-cell="{ row }">{{ formatDateTime(row.original.capturedAt) }}</template><template #reversed-cell="{ row }"><EmhareStatusPill :label="row.original.reversed ? 'Reversed' : 'Effective'" :tone="row.original.reversed ? 'warning' : 'success'" /></template><template #actions-cell="{ row }"><UButton v-if="!row.original.reversed" label="Reverse" icon="i-lucide-undo-2" size="xs" color="error" variant="ghost" :loading="actionRecordId === row.original.id" @click="reverseAttendance(row.original)" /></template></EmharePaginatedTable></EmhareRegisterPanel>

        <EmhareRegisterPanel v-if="activeDataset === 'statistics'" title="Attendance statistics" description="PostgreSQL-aggregated totals by hall, meal, academic period, programme, and student group. Net attendance excludes reversals." :record-count="register.attendanceStatistics.length"><EmharePaginatedTable :data="register.attendanceStatistics" :columns="statisticColumns" :loading="loading"><template #dimension-cell="{ row }">{{ title(row.original.dimension) }}</template></EmharePaginatedTable></EmhareRegisterPanel>
        <EmhareRegisterPanel v-if="activeDataset === 'history'" title="Dining workflow history" description="State changes and their recorded decision evidence." :record-count="register.workflowEvents.length"><EmharePaginatedTable :data="register.workflowEvents" :columns="workflowColumns" :loading="loading"><template #occurredAt-cell="{ row }">{{ formatDateTime(row.original.occurredAt) }}</template><template #aggregateType-cell="{ row }">{{ title(row.original.aggregateType) }}</template><template #eventType-cell="{ row }">{{ title(row.original.eventType) }}</template><template #previousState-cell="{ row }">{{ row.original.previousState ? title(row.original.previousState) : 'Created' }}</template><template #newState-cell="{ row }"><EmhareStatusPill :label="title(row.original.newState)" :tone="statusTone(row.original.newState)" /></template></EmharePaginatedTable></EmhareRegisterPanel>
      </div>
    </template>
  </UDashboardPanel>

  <EmhareRecordDrawer v-model:open="drawerOpen" :title="drawerTitle" description="Complete the controlled operation. State-changing actions require explicit evidence." :busy="saving" :submit-label="drawerMode === 'attendance' ? 'Evaluate access' : drawerMode === 'reconcile' ? 'Reconcile session' : 'Save record'" :submit-icon="drawerMode === 'attendance' ? 'i-lucide-scan-line' : 'i-lucide-save'" @submit="submitDrawer">
    <div v-if="drawerMode === 'assignment'" class="grid gap-4 sm:grid-cols-2"><UAlert color="warning" variant="soft" icon="i-lucide-shield-check" title="Maker-checker controlled" description="A second authorised operator must activate the prepared assignment." class="sm:col-span-2" /><UFormField label="Confirmed student registration" required class="sm:col-span-2"><USelect v-model="assignmentForm.studentId" :items="studentItems" value-key="value" class="w-full" /></UFormField><UFormField label="Programme" required><UInput v-model="assignmentForm.programmeCode" readonly class="w-full" /></UFormField><UFormField label="Student group" hint="Optional"><UInput v-model="assignmentForm.studentGroupCode" class="w-full" placeholder="COHORT-A" /></UFormField><UFormField label="Dining hall" required><USelect v-model="assignmentForm.diningHallId" :items="hallItems" value-key="value" class="w-full" /></UFormField><UFormField label="Dining plan" required><USelect v-model="assignmentForm.diningPlanId" :items="planItems" value-key="value" class="w-full" /></UFormField><UFormField label="Effective from" required><UInput v-model="assignmentForm.effectiveFrom" type="date" class="w-full" /></UFormField><UFormField label="Effective until" required><UInput v-model="assignmentForm.effectiveUntil" type="date" class="w-full" /></UFormField><UFormField label="Accommodation allocation" hint="Optional" class="sm:col-span-2"><USelectMenu v-model="assignmentForm.accommodationAllocationId" :items="accommodationAllocationItems" value-key="value" label-key="label" aria-label="Accommodation allocation" placeholder="Search active allocations" class="w-full" /></UFormField></div>
    <div v-else-if="drawerMode === 'dietary'" class="grid gap-4 sm:grid-cols-2"><UFormField label="Confirmed student registration" required class="sm:col-span-2"><USelect v-model="dietaryForm.studentId" :items="studentItems" value-key="value" class="w-full" /></UFormField><UFormField label="Requirement code" required><UInput v-model="dietaryForm.requirementCode" class="w-full" placeholder="NUT-ALLERGY" /></UFormField><UFormField label="Severity" required><USelect v-model="dietaryForm.severity" :items="['INFORMATION','IMPORTANT','CRITICAL'].map(value => ({ label: title(value), value }))" value-key="value" class="w-full" /></UFormField><UFormField label="Description" required class="sm:col-span-2"><UTextarea v-model="dietaryForm.description" class="w-full" /></UFormField><UFormField label="Effective from" required><UInput v-model="dietaryForm.effectiveFrom" type="date" class="w-full" /></UFormField><UFormField label="Effective until"><UInput v-model="dietaryForm.effectiveUntil" type="date" class="w-full" /></UFormField><UFormField label="Clinical document ID" hint="Optional" class="sm:col-span-2"><UInput v-model="dietaryForm.clinicalDocumentId" class="w-full" /></UFormField></div>
    <div v-else-if="drawerMode === 'session'" class="grid gap-4 sm:grid-cols-2"><UAlert color="warning" variant="soft" icon="i-lucide-shield-check" title="Maker-checker controlled" description="The operator who plans this session cannot open it." class="sm:col-span-2" /><UFormField label="Dining hall" required><USelect v-model="sessionForm.diningHallId" :items="hallItems" value-key="value" class="w-full" /></UFormField><UFormField label="Meal option" required><USelect v-model="sessionForm.mealOptionId" :items="mealItems" value-key="value" class="w-full" /></UFormField><UFormField label="Service date" required><UInput v-model="sessionForm.serviceDate" type="date" class="w-full" /></UFormField><UFormField label="Expected servings"><UInput v-model.number="sessionForm.expectedServings" type="number" min="0" class="w-full" /></UFormField><UFormField label="Scheduled opening" required><UInput v-model="sessionForm.scheduledOpensAt" type="datetime-local" class="w-full" /></UFormField><UFormField label="Scheduled closing" required><UInput v-model="sessionForm.scheduledClosesAt" type="datetime-local" class="w-full" /></UFormField></div>
    <div v-else-if="drawerMode === 'attendance'" class="space-y-4"><UAlert v-if="lastAttendanceResult" :color="lastAttendanceResult.outcome === 'ADMITTED' ? 'success' : 'error'" variant="solid" :icon="lastAttendanceResult.outcome === 'ADMITTED' ? 'i-lucide-circle-check' : 'i-lucide-ban'" :title="`Access ${lastAttendanceResult.outcome.toLowerCase()}`" :description="lastAttendanceResult.denialReason || `${lastAttendanceResult.eventNumber} recorded successfully.`" /><UAlert v-for="requirement in selectedAttendanceDietary" :key="requirement.id" :color="requirement.severity === 'CRITICAL' ? 'error' : 'warning'" variant="soft" icon="i-lucide-shield-alert" :title="`${title(requirement.severity)} dietary control · ${requirement.requirementCode}`" :description="requirement.description" /><div class="grid gap-4 sm:grid-cols-2"><UFormField label="Open meal session" required class="sm:col-span-2"><USelect v-model="attendanceForm.sessionId" :items="openSessionItems" value-key="value" class="w-full" /></UFormField><UFormField label="Confirmed student registration" required class="sm:col-span-2"><USelect v-model="attendanceForm.studentId" :items="studentItems" value-key="value" class="w-full" /></UFormField><UFormField label="Capture channel" required><USelect v-model="attendanceForm.captureChannel" :items="['ONLINE','OFFLINE_SYNC','MANUAL'].map(value => ({ label: title(value), value }))" value-key="value" class="w-full" /></UFormField><UFormField label="Device ID"><UInput v-model="attendanceForm.deviceId" class="w-full" /></UFormField><UFormField label="Idempotency key" description="Prevents duplicate meal capture during retries." class="sm:col-span-2"><UInput v-model="attendanceForm.idempotencyKey" readonly class="w-full" /></UFormField></div></div>
    <div v-else class="grid gap-4 sm:grid-cols-2"><UAlert color="primary" variant="soft" icon="i-lucide-scale" :title="selectedSession?.sessionNumber" :description="`${selectedSession?.netAdmitted ?? 0} net admitted from immutable attendance evidence.`" class="sm:col-span-2" /><UFormField label="Counted servings" required><UInput v-model.number="reconcileForm.countedServings" type="number" min="0" class="w-full" /></UFormField><div /><UFormField label="Reconciliation evidence" required class="sm:col-span-2"><UTextarea v-model="reconcileForm.reason" class="w-full" placeholder="Explain variances and the physical count method…" /></UFormField></div>
  </EmhareRecordDrawer>
</template>
