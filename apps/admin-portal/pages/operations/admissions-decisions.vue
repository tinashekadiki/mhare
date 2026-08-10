<script setup lang="ts">
import Swal from "sweetalert2";
import type {
  AcademicReviewSummary,
  AdmissionsWorkflowBatchView,
} from "@emhare/portal-shell/types/admissions";
definePageMeta({ layout: "dashboard" });
const api = useEmhareApi();
const { showError } = useEmhareConfirm();
const toast = useToast();
const items = ref<AcademicReviewSummary[]>([]);
const loading = ref(false);
const active = ref("");
const loadError = ref("");
const pending = computed(() =>
  items.value.filter(
    (item) =>
      item.status === "RECOMMENDED" &&
      item.latestRecommendation?.reviewStatus === "PENDING",
  ),
);
const decisionBatches = computed<AdmissionsWorkflowBatchView[]>(() => {
  const grouped = new Map<string, AcademicReviewSummary[]>();
  for (const assignment of pending.value) {
    const key = `${assignment.selectionRoundId}:${assignment.recommendationAcademicUnitId}:${assignment.programmeCode}`;
    grouped.set(key, [...(grouped.get(key) ?? []), assignment]);
  }
  return Array.from(grouped.entries()).map(([id, assignments]) => {
    const first = assignments[0]!;
    return {
      id,
      code: first.programmeCode,
      title: first.programmeName,
      subtitle: `${first.recommendationAcademicUnitName} · recommendation batch`,
      stageLabel: "4 · Decide",
      statusLabel: `${assignments.length} awaiting Admissions`,
      statusTone: "warning",
      applicants: assignments.map((assignment) => ({
        id: assignment.id,
        applicationNumber: assignment.applicationNumber,
        applicantNumber: assignment.applicantNumber,
        applicantName: assignment.applicantName,
        programmeLabel: `${assignment.programmeCode} · ${assignment.programmeName}`,
        detail: `${formatStatus(assignment.latestRecommendation!.recommendation)} · ${assignment.latestRecommendation!.reason}`,
        statusLabel: "Awaiting final decision",
        statusTone: "warning",
        href: `/operations/admissions/${assignment.applicationId}`,
      })),
    };
  });
});
onMounted(load);
async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    items.value = await api.request("/api/admissions/academic-reviews");
  } catch (error) {
    loadError.value = api.errorMessage(
      error,
      "Admissions decisions could not be loaded.",
    );
  } finally {
    loading.value = false;
  }
}
function assignmentById(assignmentId: string) {
  return pending.value.find((assignment) => assignment.id === assignmentId);
}
function formatStatus(value: string) {
  return value
    .toLowerCase()
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}
async function decide(
  item: AcademicReviewSummary,
  action: "APPROVE" | "RETURN" | "OVERRIDE",
) {
  const recommendation = item.latestRecommendation;
  if (!recommendation) return;
  const result = await Swal.fire({
    title: `${action.toLowerCase()} recommendation`,
    input: "textarea",
    inputLabel:
      action === "OVERRIDE"
        ? "Mandatory override reason"
        : "Mandatory review reason",
    inputPlaceholder: "Record the governed reason",
    showCancelButton: true,
    confirmButtonText:
      action === "APPROVE"
        ? "Approve final decision"
        : action === "RETURN"
          ? "Return for reconsideration"
          : "Override decision",
    confirmButtonColor: "#005b41",
    inputValidator: (value) =>
      !value?.trim() ? "A reason is required." : undefined,
  });
  if (!result.isConfirmed) return;
  let finalDecision: string | null = null;
  if (action === "OVERRIDE") {
    const choice = await Swal.fire({
      title: "Final Admissions decision",
      input: "select",
      inputOptions: {
        SHORTLIST: "Shortlist",
        SELECT: "Select",
        WAITLIST: "Waitlist",
        REJECT: "Reject",
      },
      showCancelButton: true,
      confirmButtonText: "Use decision",
      confirmButtonColor: "#005b41",
    });
    if (!choice.isConfirmed) return;
    finalDecision = choice.value;
  }
  active.value = item.id;
  try {
    await api.request(`/api/admissions/academic-reviews/${item.id}/review`, {
      method: "POST",
      body: { action, finalDecision, reason: result.value.trim() },
    });
    await load();
    toast.add({
      title: "Admissions review recorded",
      description:
        action === "RETURN"
          ? "Returned to the academic unit."
          : "Final selection decision recorded.",
      color: "success",
    });
  } catch (error) {
    await showError(
      "Admissions review could not be recorded",
      api.errorMessage(error),
    );
  } finally {
    active.value = "";
  }
}
</script>
<template>
  <UDashboardPanel
    ><template #header
      ><UDashboardNavbar title="Admissions decision approval"
        ><template #leading><UDashboardSidebarCollapse /></template
        ><template #right
          ><UButton
            label="Refresh"
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="outline"
            :loading="loading"
            @click="load" /></template></UDashboardNavbar></template
    ><template #body
      ><div class="space-y-5 p-4 sm:p-6">
        <EmhareAdmissionsWorkflowNav current-stage="decide" />
        <UAlert
          v-if="loadError"
          color="error"
          variant="soft"
          icon="i-lucide-circle-alert"
          title="Decision stage unavailable"
          :description="loadError"
        />
        <UAlert
          color="warning"
          variant="soft"
          title="Admissions retains final authority"
          description="Approve the recommendation, return it for reconsideration, or record a different final decision with a mandatory override reason. Only an approved Select decision can lead to an offer."
        />
        <EmhareAdmissionsBatchList
          :batches="decisionBatches"
          :loading="loading"
          empty-title="No recommendation batches awaiting Admissions"
          empty-description="Completed academic-unit recommendations appear here for final governance."
        >
          <template #applicant-actions="{ applicant }">
            <template v-if="assignmentById(applicant.id)">
              <UButton
                label="Approve"
                color="primary"
                :loading="active === applicant.id"
                @click="decide(assignmentById(applicant.id)!, 'APPROVE')"
              />
              <UButton
                label="Return"
                color="warning"
                variant="soft"
                :loading="active === applicant.id"
                @click="decide(assignmentById(applicant.id)!, 'RETURN')"
              />
              <UButton
                label="Override"
                color="neutral"
                variant="outline"
                :loading="active === applicant.id"
                @click="decide(assignmentById(applicant.id)!, 'OVERRIDE')"
              />
            </template>
          </template>
        </EmhareAdmissionsBatchList></div></template
  ></UDashboardPanel>
</template>
