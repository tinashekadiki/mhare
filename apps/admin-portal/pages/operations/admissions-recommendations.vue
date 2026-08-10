<script setup lang="ts">
import Swal from "sweetalert2";
import type {
  AcademicReviewSummary,
  AdmissionsWorkflowBatchView,
} from "@emhare/portal-shell/types/admissions";
definePageMeta({ layout: "dashboard" });
const api = useEmhareApi();
const toast = useToast();
const { showError } = useEmhareConfirm();
const queue = ref<AcademicReviewSummary[]>([]);
const loading = ref(false);
const active = ref("");
const loadError = ref("");
const recommendationBatches = computed<AdmissionsWorkflowBatchView[]>(() => {
  const grouped = new Map<string, AcademicReviewSummary[]>();
  for (const assignment of queue.value) {
    const key = `${assignment.selectionRoundId}:${assignment.recommendationAcademicUnitId}:${assignment.programmeCode}`;
    grouped.set(key, [...(grouped.get(key) ?? []), assignment]);
  }
  return Array.from(grouped.entries()).map(([id, assignments]) => {
    const first = assignments[0]!;
    const actionable = assignments.filter((assignment) =>
      ["OPEN", "CLAIMED", "RETURNED"].includes(assignment.status),
    ).length;
    return {
      id,
      code: first.programmeCode,
      title: first.programmeName,
      subtitle: `${first.recommendationAcademicUnitName} · programmes below this unit`,
      stageLabel: "3 · Recommend",
      statusLabel: actionable ? `${actionable} awaiting action` : "Submitted",
      statusTone: actionable ? "warning" : "success",
      applicants: assignments.map((assignment) => ({
        id: assignment.id,
        applicationNumber: assignment.applicationNumber,
        applicantNumber: assignment.applicantNumber,
        applicantName: assignment.applicantName,
        programmeLabel: `${assignment.programmeCode} · ${assignment.programmeName}`,
        detail: `Choice ${assignment.choiceRank} · owner ${assignment.owningAcademicUnitName}`,
        statusLabel: formatStatus(assignment.status),
        statusTone: assignmentStatusTone(assignment.status),
        href: `/operations/admissions/${assignment.applicationId}?academicReviewAssignmentId=${assignment.id}`,
      })),
    };
  });
});
onMounted(load);
async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    queue.value = await api.request("/api/admissions/academic-reviews/mine");
  } catch (error) {
    loadError.value = api.errorMessage(
      error,
      "The recommendation queue could not be loaded.",
    );
  } finally {
    loading.value = false;
  }
}
function assignmentById(assignmentId: string) {
  return queue.value.find((assignment) => assignment.id === assignmentId);
}
function formatStatus(value: string) {
  return value
    .toLowerCase()
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}
function assignmentStatusTone(status: AcademicReviewSummary["status"]) {
  if (status === "RECOMMENDED" || status === "COMPLETED")
    return "success" as const;
  if (status === "RETURNED") return "warning" as const;
  if (status === "CANCELLED") return "error" as const;
  return "primary" as const;
}
async function claim(item: AcademicReviewSummary) {
  active.value = item.id;
  try {
    await api.request(`/api/admissions/academic-reviews/${item.id}/claim`, {
      method: "POST",
      body: { expectedVersion: item.version },
    });
    await load();
    toast.add({
      title: "Review claimed",
      description: item.applicationNumber,
      color: "success",
    });
  } catch (error) {
    await showError("Review could not be claimed", api.errorMessage(error));
  } finally {
    active.value = "";
  }
}
async function recommend(item: AcademicReviewSummary) {
  const result = await Swal.fire({
    title: "Record academic-unit recommendation",
    html: `<select id="recommendation" class="swal2-select"><option value="SHORTLIST">Shortlist</option><option value="SELECT">Select</option><option value="WAITLIST">Waitlist</option><option value="REJECT">Reject</option></select><textarea id="reason" class="swal2-textarea" placeholder="Mandatory recommendation reason"></textarea>`,
    showCancelButton: true,
    confirmButtonText: "Record recommendation",
    confirmButtonColor: "#005b41",
    preConfirm: () => {
      const recommendation = (
        document.getElementById("recommendation") as HTMLSelectElement
      )?.value;
      const reason = (
        document.getElementById("reason") as HTMLTextAreaElement
      )?.value.trim();
      if (!reason) {
        Swal.showValidationMessage("A recommendation reason is required.");
        return false;
      }
      return { recommendation, reason };
    },
  });
  if (!result.isConfirmed || !result.value) return;
  active.value = item.id;
  try {
    await api.request(
      `/api/admissions/academic-reviews/${item.id}/recommendations`,
      {
        method: "POST",
        body: {
          ...result.value,
          rankPosition: null,
          quotaTypeCode: null,
          expectedVersion: item.version,
        },
      },
    );
    await load();
    toast.add({
      title: "Recommendation recorded",
      description: "Admissions will make the final decision.",
      color: "success",
    });
  } catch (error) {
    await showError(
      "Recommendation could not be recorded",
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
      ><UDashboardNavbar title="Academic-unit recommendation work queue"
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
        <EmhareAdmissionsWorkflowNav current-stage="recommend" />
        <UAlert
          v-if="loadError"
          color="error"
          variant="soft"
          icon="i-lucide-circle-alert"
          title="Recommendation stage unavailable"
          :description="loadError"
        />
        <UAlert
          color="info"
          variant="soft"
          title="Advisory authority"
          description="This queue contains programmes owned anywhere below your exact highest academic unit. Recommendations are advisory and cannot select an applicant or create an offer."
        />
        <EmhareAdmissionsBatchList
          :batches="recommendationBatches"
          :loading="loading"
          empty-title="No academic-review batches assigned"
          empty-description="Only batches released to an academic unit where you hold an active direct staff assignment appear here."
        >
          <template #applicant-actions="{ applicant }">
            <template v-if="assignmentById(applicant.id)">
              <UButton
                v-if="
                  ['OPEN', 'RETURNED'].includes(
                    assignmentById(applicant.id)!.status,
                  )
                "
                label="Claim"
                color="neutral"
                variant="outline"
                :loading="active === applicant.id"
                @click="claim(assignmentById(applicant.id)!)"
              />
              <UButton
                v-if="assignmentById(applicant.id)!.status === 'CLAIMED'"
                label="Recommend"
                icon="i-lucide-clipboard-check"
                color="primary"
                :loading="active === applicant.id"
                @click="recommend(assignmentById(applicant.id)!)"
              />
            </template>
          </template>
        </EmhareAdmissionsBatchList></div></template
  ></UDashboardPanel>
</template>
