<script setup lang="ts">
import Swal from "sweetalert2";
import type {
  ExamAttendanceRecordSummary,
  ExamAttendanceStatus,
  ExamIncidentSeverity,
  ExamIncidentSummary,
  ExamIncidentType,
  ExamInvigilationWorkspace,
  ExamVenueOperationSummary,
} from "@emhare/portal-shell/types/exams";

definePageMeta({ layout: "dashboard" });
const api = useEmhareApi();
const toast = useToast();
const { showError } = useEmhareConfirm();
const workspace = ref<ExamInvigilationWorkspace>({ venueOperations: [] });
const loading = ref(false);
const operatingId = ref<string | null>(null);
const search = ref("");
const statusFilter = ref<"ALL" | "NOT_OPENED" | "OPEN" | "CLOSED">("ALL");
const incidentModalOpen = ref(false);
const incidentSessionId = ref("");
const incidentCandidateItems = ref<Array<{ label: string; value: string }>>([]);
const roomWideIncidentSelectionValue = "__ROOM_WIDE_INCIDENT__";
const incidentForm = reactive({
  studentTimetableEntryId: roomWideIncidentSelectionValue,
  incidentType: "OTHER" as ExamIncidentType,
  severity: "MEDIUM" as ExamIncidentSeverity,
  description: "",
  occurredAt: localDateTime(new Date()),
});
const statusItems = [
  { label: "All rooms", value: "ALL" },
  { label: "Not opened", value: "NOT_OPENED" },
  { label: "Open", value: "OPEN" },
  { label: "Closed", value: "CLOSED" },
];
const incidentTypeItems = [
  "LATE_ARRIVAL",
  "SUSPECTED_MISCONDUCT",
  "MEDICAL",
  "EVACUATION",
  "DISRUPTION",
  "OTHER",
].map((value) => ({ label: title(value), value }));
const severityItems = ["LOW", "MEDIUM", "HIGH", "CRITICAL"].map((value) => ({
  label: title(value),
  value,
}));
const counts = computed(() => ({
  rooms: workspace.value.venueOperations.length,
  notOpened: workspace.value.venueOperations.filter(
    (item) => !item.attendanceSession,
  ).length,
  open: workspace.value.venueOperations.filter(
    (item) => item.attendanceSession?.status === "OPEN",
  ).length,
  outstanding: workspace.value.venueOperations.reduce(
    (sum, item) =>
      sum + (item.attendanceSession?.outstandingCandidateCount ?? 0),
    0,
  ),
  unresolved: workspace.value.venueOperations.reduce(
    (sum, item) =>
      sum +
      (item.attendanceSession?.incidents.filter(
        (incident) => incident.status !== "RESOLVED",
      ).length ?? 0),
    0,
  ),
}));
const visibleOperations = computed(() => {
  const query = search.value.trim().toLowerCase();
  return workspace.value.venueOperations.filter((operation) => {
    const operationStatus = operation.attendanceSession?.status ?? "NOT_OPENED";
    return (
      (statusFilter.value === "ALL" ||
        statusFilter.value === operationStatus) &&
      (!query ||
        [
          operation.moduleCode,
          operation.moduleName,
          operation.venueCode,
          operation.venueName,
          operation.runNumber,
        ].some((value) => value.toLowerCase().includes(query)))
    );
  });
});
onMounted(load);

async function load() {
  loading.value = true;
  try {
    workspace.value = await api.request<ExamInvigilationWorkspace>(
      "/api/exams/invigilation",
    );
  } catch (error) {
    await showError(
      "Invigilation workspace could not be loaded",
      api.errorMessage(error),
    );
  } finally {
    loading.value = false;
  }
}
async function openRegister(operation: ExamVenueOperationSummary) {
  const result = await Swal.fire({
    title: "Open attendance register?",
    text: `This snapshots all ${operation.allocatedCandidateCount} published seats for ${operation.moduleCode} in ${operation.venueCode}.`,
    input: "textarea",
    inputLabel: "Opening control evidence",
    inputPlaceholder: "Record room, materials, and published roster checks.",
    icon: "question",
    showCancelButton: true,
    confirmButtonText: "Open register",
    confirmButtonColor: "#006633",
    inputValidator: (value) =>
      value.trim() ? undefined : "Opening evidence is required.",
  });
  if (!result.isConfirmed || !result.value?.trim()) return;
  await perform(
    operation.venueAllocationId,
    async () =>
      api.request(
        `/api/exams/invigilation/venue-allocations/${operation.venueAllocationId}/attendance-session`,
        { method: "POST", body: { openingReason: result.value.trim() } },
      ),
    "Attendance register opened",
  );
}
async function recordAttendance(
  record: ExamAttendanceRecordSummary,
  attendanceStatus: Exclude<ExamAttendanceStatus, "EXPECTED">,
) {
  let evidenceNotes: string | undefined =
    attendanceStatus === "PRESENT"
      ? "Identity and examination admission evidence checked at the allocated seat."
      : undefined;
  if (attendanceStatus !== "PRESENT") {
    const result = await Swal.fire({
      title: `Record ${attendanceStatus.toLowerCase()}?`,
      text: `${record.studentNumber} · seat ${record.seatNumber}`,
      input: "textarea",
      inputLabel: "Required evidence notes",
      inputPlaceholder:
        attendanceStatus === "ABSENT"
          ? "Record non-attendance checks and any contact evidence."
          : "Record the authorised basis and supporting evidence.",
      icon: "warning",
      showCancelButton: true,
      confirmButtonText: `Record ${attendanceStatus.toLowerCase()}`,
      confirmButtonColor: "#006633",
      inputValidator: (value) =>
        value.trim() ? undefined : "Evidence notes are required.",
    });
    if (!result.isConfirmed || !result.value?.trim()) return;
    evidenceNotes = result.value.trim();
  }
  await perform(
    record.id,
    async () =>
      api.request(`/api/exams/invigilation/attendance-records/${record.id}`, {
        method: "PUT",
        body: {
          attendanceStatus,
          evidenceNotes,
          expectedVersion: record.version,
        },
      }),
    `${record.studentNumber} recorded ${attendanceStatus.toLowerCase()}`,
  );
}
async function closeRegister(operation: ExamVenueOperationSummary) {
  const session = operation.attendanceSession;
  if (!session) return;
  if (session.outstandingCandidateCount) {
    await showError(
      "Register cannot be closed",
      `${session.outstandingCandidateCount} candidate outcome${session.outstandingCandidateCount === 1 ? "" : "s"} remain expected.`,
    );
    return;
  }
  const result = await Swal.fire({
    title: "Close reconciled register?",
    text: "Closure makes candidate attendance evidence immutable. Confirm scripts, seats, incidents, and all candidate outcomes have been reconciled.",
    input: "textarea",
    inputLabel: "Closure evidence",
    inputPlaceholder: "Record the completed room reconciliation.",
    icon: "question",
    showCancelButton: true,
    confirmButtonText: "Close register",
    confirmButtonColor: "#006633",
    inputValidator: (value) =>
      value.trim() ? undefined : "Closure evidence is required.",
  });
  if (!result.isConfirmed || !result.value?.trim()) return;
  await perform(
    session.id,
    async () =>
      api.request(
        `/api/exams/invigilation/attendance-sessions/${session.id}/close`,
        {
          method: "POST",
          body: {
            closureReason: result.value.trim(),
            expectedVersion: session.version,
          },
        },
      ),
    "Attendance register closed",
  );
}
function openIncidentModal(operation: ExamVenueOperationSummary) {
  if (!operation.attendanceSession) return;
  incidentSessionId.value = operation.attendanceSession.id;
  incidentCandidateItems.value = [
    { label: "Room-wide incident", value: roomWideIncidentSelectionValue },
    ...operation.attendanceSession.attendanceRecords.map((record) => ({
      label: `${record.studentNumber} · seat ${record.seatNumber}`,
      value: record.studentTimetableEntryId,
    })),
  ];
  Object.assign(incidentForm, {
    studentTimetableEntryId: roomWideIncidentSelectionValue,
    incidentType: "OTHER",
    severity: "MEDIUM",
    description: "",
    occurredAt: localDateTime(new Date()),
  });
  incidentModalOpen.value = true;
}
async function reportIncident() {
  if (!incidentForm.description.trim() || !incidentForm.occurredAt) return;
  await perform(
    incidentSessionId.value,
    async () =>
      api.request(
        `/api/exams/invigilation/attendance-sessions/${incidentSessionId.value}/incidents`,
        {
          method: "POST",
          body: {
            studentTimetableEntryId:
              incidentForm.studentTimetableEntryId === roomWideIncidentSelectionValue
                ? null
                : incidentForm.studentTimetableEntryId,
            incidentType: incidentForm.incidentType,
            severity: incidentForm.severity,
            description: incidentForm.description.trim(),
            occurredAt: new Date(incidentForm.occurredAt).toISOString(),
          },
        },
      ),
    "Incident report recorded",
  );
  incidentModalOpen.value = false;
}
async function moveIncident(
  incident: ExamIncidentSummary,
  action: "review" | "resolve",
) {
  const result = await Swal.fire({
    title:
      action === "review"
        ? "Independently review incident?"
        : "Resolve reviewed incident?",
    text:
      action === "review"
        ? "The reviewer must be different from the original reporter."
        : "The resolver must be different from both the reporter and reviewer.",
    input: "textarea",
    inputLabel:
      action === "review"
        ? "Review evidence and decision"
        : "Resolution and follow-up",
    inputPlaceholder: "Record a complete, auditable rationale.",
    icon: "question",
    showCancelButton: true,
    confirmButtonText:
      action === "review" ? "Record review" : "Resolve incident",
    confirmButtonColor: "#006633",
    inputValidator: (value) =>
      value.trim() ? undefined : "A complete reason is required.",
  });
  if (!result.isConfirmed || !result.value?.trim()) return;
  await perform(
    incident.id,
    async () =>
      api.request(
        `/api/exams/invigilation/incidents/${incident.id}/${action}`,
        {
          method: "POST",
          body: {
            reason: result.value.trim(),
            expectedVersion: incident.version,
          },
        },
      ),
    action === "review"
      ? "Incident independently reviewed"
      : "Incident resolved",
  );
}
async function perform(
  id: string,
  action: () => Promise<unknown>,
  successTitle: string,
) {
  operatingId.value = id;
  try {
    await action();
    await load();
    toast.add({ title: successTitle, color: "success" });
  } catch (error) {
    await showError(
      "Exam operation could not be completed",
      api.errorMessage(error),
    );
  } finally {
    operatingId.value = null;
  }
}
function title(value: string) {
  return value
    .toLowerCase()
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}
function localDateTime(value: Date) {
  const offset = value.getTimezoneOffset();
  return new Date(value.getTime() - offset * 60_000).toISOString().slice(0, 16);
}
function timestamp(value: string) {
  return new Intl.DateTimeFormat("en-ZW", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}
function attendanceColour(status: ExamAttendanceStatus) {
  if (status === "PRESENT") return "success";
  if (status === "ABSENT") return "error";
  if (status === "EXCUSED") return "info";
  return "warning";
}
function incidentColour(severity: ExamIncidentSeverity) {
  if (severity === "CRITICAL" || severity === "HIGH") return "error";
  if (severity === "MEDIUM") return "warning";
  return "neutral";
}
</script>

<template>
  <UDashboardPanel>
    <template #header
      ><UDashboardNavbar title="Exam invigilation"
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
    <template #body
      ><div class="space-y-5 p-4 sm:p-6">
        <UAlert
          color="primary"
          variant="soft"
          icon="i-lucide-clipboard-check"
          title="Published roster to reconciled room evidence"
          description="Each register starts from the exact published venue allocation. Every seat requires a candidate outcome before closure; incidents retain the original report and require independent review and resolution."
        />
        <section class="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
          <UCard :ui="{ body: 'p-4' }"
            ><p class="text-xs uppercase text-muted">Published rooms</p>
            <p class="mt-2 text-2xl font-semibold">{{ counts.rooms }}</p></UCard
          ><UCard :ui="{ body: 'p-4' }"
            ><p class="text-xs uppercase text-warning">Not opened</p>
            <p class="mt-2 text-2xl font-semibold">
              {{ counts.notOpened }}
            </p></UCard
          ><UCard :ui="{ body: 'p-4' }"
            ><p class="text-xs uppercase text-primary">Open registers</p>
            <p class="mt-2 text-2xl font-semibold">{{ counts.open }}</p></UCard
          ><UCard :ui="{ body: 'p-4' }"
            ><p class="text-xs uppercase text-warning">Expected outcomes</p>
            <p class="mt-2 text-2xl font-semibold">
              {{ counts.outstanding }}
            </p></UCard
          ><UCard :ui="{ body: 'p-4' }"
            ><p class="text-xs uppercase text-error">Unresolved incidents</p>
            <p class="mt-2 text-2xl font-semibold">
              {{ counts.unresolved }}
            </p></UCard
          >
        </section>
        <UCard :ui="{ body: 'p-4' }"
          ><div class="grid gap-3 sm:grid-cols-[1fr_220px]">
            <UInput
              v-model="search"
              icon="i-lucide-search"
              placeholder="Search Module, venue, or run"
            /><USelect v-model="statusFilter" :items="statusItems" /></div
        ></UCard>
        <EmharePaginatedCollection :items="visibleOperations" v-slot="{ items: paginatedOperations }">
        <div class="space-y-4">
          <UCard
            v-for="operation in paginatedOperations"
            :key="operation.venueAllocationId"
            :ui="{ body: 'p-4 sm:p-5' }"
          >
            <div class="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p class="font-mono text-xs text-primary">
                  {{ operation.runNumber }}
                </p>
                <h2 class="mt-1 text-lg font-semibold">
                  {{ operation.moduleCode }} · {{ operation.moduleName }}
                </h2>
                <p class="mt-1 text-sm text-muted">
                  {{ operation.venueCode }} · {{ operation.venueName }} ·
                  {{ operation.campusName }}
                </p>
                <p class="mt-1 text-xs text-muted">
                  {{ timestamp(operation.scheduledStartsAt) }} –
                  {{ timestamp(operation.scheduledEndsAt) }}
                </p>
              </div>
              <UBadge
                :label="operation.attendanceSession?.status ?? 'NOT OPENED'"
                :color="
                  operation.attendanceSession?.status === 'CLOSED'
                    ? 'success'
                    : operation.attendanceSession?.status === 'OPEN'
                      ? 'primary'
                      : 'warning'
                "
                variant="subtle"
              />
            </div>
            <div
              v-if="!operation.attendanceSession"
              class="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-warning/30 bg-warning/5 p-4"
            >
              <div>
                <p class="font-medium">
                  {{ operation.allocatedCandidateCount }} published candidate
                  seat{{ operation.allocatedCandidateCount === 1 ? "" : "s" }}
                </p>
                <p class="text-sm text-muted">
                  Open only after the room, materials, and seat register have
                  been checked.
                </p>
              </div>
              <UButton
                label="Open register"
                icon="i-lucide-door-open"
                :loading="operatingId === operation.venueAllocationId"
                @click="openRegister(operation)"
              />
            </div>
            <template v-else
              ><div class="mt-4 grid gap-2 sm:grid-cols-4">
                <div class="rounded-md border border-muted p-3">
                  <p class="text-xs text-muted">Expected</p>
                  <p class="mt-1 text-xl font-semibold">
                    {{ operation.attendanceSession.expectedCandidateCount }}
                  </p>
                </div>
                <div class="rounded-md border border-muted p-3">
                  <p class="text-xs text-success">Present</p>
                  <p class="mt-1 text-xl font-semibold">
                    {{ operation.attendanceSession.presentCandidateCount }}
                  </p>
                </div>
                <div class="rounded-md border border-muted p-3">
                  <p class="text-xs text-error">Absent / excused</p>
                  <p class="mt-1 text-xl font-semibold">
                    {{
                      operation.attendanceSession.absentCandidateCount +
                      operation.attendanceSession.excusedCandidateCount
                    }}
                  </p>
                </div>
                <div class="rounded-md border border-muted p-3">
                  <p class="text-xs text-warning">Outstanding</p>
                  <p class="mt-1 text-xl font-semibold">
                    {{ operation.attendanceSession.outstandingCandidateCount }}
                  </p>
                </div>
              </div>
              <EmharePaginatedCollection :items="operation.attendanceSession.attendanceRecords" v-slot="{ items: paginatedAttendanceRecords }">
              <div class="mt-4 overflow-x-auto rounded-lg border border-muted">
                <table class="w-full min-w-[850px] text-left text-sm">
                  <thead class="bg-muted/40 text-xs uppercase text-muted">
                    <tr>
                      <th class="px-3 py-2">Seat</th>
                      <th class="px-3 py-2">Candidate</th>
                      <th class="px-3 py-2">Outcome</th>
                      <th class="px-3 py-2">Evidence</th>
                      <th class="px-3 py-2 text-right">Record</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr
                      v-for="record in paginatedAttendanceRecords"
                      :key="record.id"
                      class="border-t border-muted"
                    >
                      <td class="px-3 py-3 font-mono">
                        {{ record.seatNumber }}
                      </td>
                      <td class="px-3 py-3 font-medium">
                        {{ record.studentNumber }}
                      </td>
                      <td class="px-3 py-3">
                        <UBadge
                          :label="record.attendanceStatus"
                          :color="attendanceColour(record.attendanceStatus)"
                          variant="subtle"
                        />
                      </td>
                      <td class="max-w-xs px-3 py-3 text-xs text-muted">
                        {{
                          record.evidenceNotes ??
                          "Awaiting invigilator evidence"
                        }}
                      </td>
                      <td class="px-3 py-3">
                        <div
                          v-if="operation.attendanceSession.status === 'OPEN'"
                          class="flex justify-end gap-1"
                        >
                          <UButton
                            label="Present"
                            size="xs"
                            color="success"
                            variant="soft"
                            :loading="operatingId === record.id"
                            @click="recordAttendance(record, 'PRESENT')"
                          /><UButton
                            label="Absent"
                            size="xs"
                            color="error"
                            variant="soft"
                            @click="recordAttendance(record, 'ABSENT')"
                          /><UButton
                            label="Excused"
                            size="xs"
                            color="info"
                            variant="soft"
                            @click="recordAttendance(record, 'EXCUSED')"
                          />
                        </div>
                        <span v-else class="block text-right text-xs text-muted"
                          >Locked</span
                        >
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
              </EmharePaginatedCollection>
              <section class="mt-4 rounded-lg border border-muted">
                <div
                  class="flex flex-wrap items-center justify-between gap-3 border-b border-muted p-3"
                >
                  <div>
                    <h3 class="font-medium">Incident register</h3>
                    <p class="text-xs text-muted">
                      Original reports are immutable after capture.
                    </p>
                  </div>
                  <UButton
                    v-if="operation.attendanceSession.status === 'OPEN'"
                    label="Report incident"
                    icon="i-lucide-triangle-alert"
                    color="warning"
                    variant="soft"
                    @click="openIncidentModal(operation)"
                  />
                </div>
                <EmharePaginatedCollection
                  v-if="operation.attendanceSession.incidents.length"
                  :items="operation.attendanceSession.incidents"
                  v-slot="{ items: paginatedIncidents }"
                ><div class="divide-y divide-muted">
                  <article
                    v-for="incident in paginatedIncidents"
                    :key="incident.id"
                    class="p-3"
                  >
                    <div
                      class="flex flex-wrap items-start justify-between gap-3"
                    >
                      <div>
                        <p class="font-mono text-xs text-primary">
                          {{ incident.incidentNumber }}
                        </p>
                        <p class="mt-1 font-medium">
                          {{ title(incident.incidentType)
                          }}<span v-if="incident.studentNumber">
                            · {{ incident.studentNumber }}</span
                          >
                        </p>
                        <p class="mt-1 max-w-3xl text-sm text-muted">
                          {{ incident.description }}
                        </p>
                      </div>
                      <div class="flex gap-1">
                        <UBadge
                          :label="incident.severity"
                          :color="incidentColour(incident.severity)"
                          variant="subtle"
                        /><UBadge
                          :label="incident.status"
                          :color="
                            incident.status === 'RESOLVED'
                              ? 'success'
                              : 'warning'
                          "
                          variant="outline"
                        />
                      </div>
                    </div>
                    <div
                      class="mt-3 flex flex-wrap items-center justify-between gap-2"
                    >
                      <p class="text-xs text-muted">
                        Reported {{ timestamp(incident.reportedAt) }} by
                        {{ incident.reportedByUserId }}
                      </p>
                      <div class="flex gap-2">
                        <UButton
                          v-if="incident.status === 'REPORTED'"
                          label="Review"
                          size="xs"
                          icon="i-lucide-search-check"
                          @click="moveIncident(incident, 'review')"
                        /><UButton
                          v-if="incident.status === 'REVIEWED'"
                          label="Resolve"
                          size="xs"
                          icon="i-lucide-badge-check"
                          @click="moveIncident(incident, 'resolve')"
                        />
                      </div>
                    </div>
                  </article>
                </div></EmharePaginatedCollection>
                <p v-else class="p-4 text-sm text-muted">
                  No incidents recorded for this room allocation.
                </p>
              </section>
              <div
                class="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-muted pt-4"
              >
                <p class="text-xs text-muted">
                  Opened
                  {{ timestamp(operation.attendanceSession.openedAt) }} by
                  {{ operation.attendanceSession.openedByUserId
                  }}<span v-if="operation.attendanceSession.closedAt">
                    · closed
                    {{ timestamp(operation.attendanceSession.closedAt) }}</span
                  >
                </p>
                <EmhareGuidedActionButton
                  v-if="operation.attendanceSession.status === 'OPEN'"
                  label="Close reconciled register"
                  icon="i-lucide-lock-keyhole"
                  guidance-title="Attendance register cannot be closed yet"
                  :guidance-instructions="operation.attendanceSession.outstandingCandidateCount > 0 ? [`Resolve attendance for ${operation.attendanceSession.outstandingCandidateCount} outstanding candidate${operation.attendanceSession.outstandingCandidateCount === 1 ? '' : 's'}.`] : []"
                  :loading="operatingId === operation.attendanceSession.id"
                  @click="closeRegister(operation)"
                />
              </div>
            </template>
          </UCard>
        </div>
        </EmharePaginatedCollection>
        <UAlert
          v-if="!loading && !visibleOperations.length"
          color="neutral"
          variant="soft"
          title="No room operations match"
          description="Only venue allocations from published examination timetables can appear here."
        /></div
    ></template>
  </UDashboardPanel>
  <EmhareRecordDrawer
    v-model:open="incidentModalOpen"
    title="Report examination incident"
    description="Capture the original room evidence. It cannot be rewritten during later review."
    ><template #body
      ><div class="space-y-4">
        <div class="grid gap-4 sm:grid-cols-2">
          <UFormField label="Candidate or room"
            ><USelect
              v-model="incidentForm.studentTimetableEntryId"
              :items="incidentCandidateItems"
              class="w-full" /></UFormField
          ><UFormField label="Occurred at" required
            ><UInput
              v-model="incidentForm.occurredAt"
              type="datetime-local"
              class="w-full" /></UFormField
          ><UFormField label="Incident type" required
            ><USelect
              v-model="incidentForm.incidentType"
              :items="incidentTypeItems"
              class="w-full" /></UFormField
          ><UFormField label="Severity" required
            ><USelect
              v-model="incidentForm.severity"
              :items="severityItems"
              class="w-full"
          /></UFormField>
        </div>
        <UFormField label="Factual incident description" required
          ><UTextarea
            v-model="incidentForm.description"
            :rows="5"
            class="w-full"
            placeholder="Record what occurred, actions taken, witnesses, affected candidates, and preserved evidence."
        /></UFormField></div></template
    ><template #footer
      ><div class="flex w-full justify-end gap-2">
        <UButton
          label="Cancel"
          color="neutral"
          variant="outline"
          @click="incidentModalOpen = false"
        /><EmhareGuidedActionButton
          label="Record incident"
          icon="i-lucide-triangle-alert"
          color="warning"
          :loading="operatingId === incidentSessionId"
          guidance-title="Incident evidence is incomplete"
          :guidance-instructions="[...(!incidentForm.description.trim() ? ['Describe the incident.'] : []), ...(!incidentForm.occurredAt ? ['Record when the incident occurred.'] : [])]"
          @click="reportIncident"
        /></div></template
  ></EmhareRecordDrawer>
</template>
