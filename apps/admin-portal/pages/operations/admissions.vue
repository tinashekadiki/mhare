<script setup lang="ts">
import Swal from "sweetalert2";
import type {
  AdmissionsApplicationSummary,
  AdmissionsWorkflowBatchView,
} from "@emhare/portal-shell/types/admissions";

definePageMeta({ layout: "dashboard" });

const route = useRoute();
const api = useEmhareApi();
const toast = useToast();
const { confirmAction, showError } = useEmhareConfirm();
const academicPeriodContext = useAcademicPeriodContext();
const applications = ref<AdmissionsApplicationSummary[]>([]);
const loading = ref(false);
const loadError = ref("");
const search = ref("");
const statusFilter = ref("ALL");
const activeActionApplicationId = ref<string | null>(null);

const statusItems = computed(() => [
  { label: "All statuses", value: "ALL" },
  ...Array.from(
    new Set(applications.value.map((application) => application.status)),
  )
    .sort()
    .map((status) => ({ label: formatStatus(status), value: status })),
]);

const filteredApplications = computed(() =>
  applications.value.filter((application) => {
    const normalizedSearch = search.value.trim().toLowerCase();
    const matchesSearch =
      !normalizedSearch ||
      application.applicationNumber.toLowerCase().includes(normalizedSearch) ||
      application.applicantNumber.toLowerCase().includes(normalizedSearch) ||
      application.payment?.reference.toLowerCase().includes(normalizedSearch);
    const matchesStatus =
      statusFilter.value === "ALL" || application.status === statusFilter.value;
    return matchesSearch && matchesStatus;
  }),
);

const confirmationBatches = computed<AdmissionsWorkflowBatchView[]>(() => {
  const grouped = new Map<string, AdmissionsApplicationSummary[]>();
  for (const application of filteredApplications.value) {
    const key = `${application.intakeId}:${application.applicationTypeId}`;
    grouped.set(key, [...(grouped.get(key) ?? []), application]);
  }

  return Array.from(grouped.entries()).map(([id, batchApplications]) => {
    const first = batchApplications[0]!;
    const readyCount = batchApplications.filter(
      (application) =>
        application.status === "SUBMITTED" && application.canEnterReview,
    ).length;
    return {
      id,
      code: first.intakeCode,
      title: first.applicationTypeName,
      subtitle: `${readyCount} ready to confirm · ${batchApplications.length - readyCount} awaiting checks`,
      stageLabel: "1 · Confirm",
      statusLabel: readyCount ? `${readyCount} ready` : "Checks pending",
      statusTone: readyCount ? "success" : "warning",
      applicants: batchApplications.map((application) => ({
        id: application.id,
        applicationNumber: application.applicationNumber,
        applicantNumber: application.applicantNumber,
        applicantName: application.applicantName,
        programmeLabel: application.programmeChoices[0]
          ? `${application.programmeChoices[0].programmeCode} · ${application.programmeChoices[0].programmeName}`
          : "No programme choice",
        detail: paymentStatusLabel(application.paymentClearanceStatus),
        statusLabel: formatStatus(application.status),
        statusTone: applicationStatusTone(application.status),
        href: `/operations/admissions/${application.id}`,
      })),
    };
  });
});

function applicationById(applicationId: string) {
  return applications.value.find(
    (application) => application.id === applicationId,
  );
}

const queueCounts = computed(() => ({
  total: applications.value.length,
  awaitingPayment: applications.value.filter(
    (application) =>
      application.paymentClearanceStatus === "PENDING" ||
      application.paymentClearanceStatus === "UNRATED",
  ).length,
  readyForReview: applications.value.filter(
    (application) =>
      application.status === "SUBMITTED" && application.canEnterReview,
  ).length,
}));

const showingApplicationDetail = computed(() =>
  Boolean(route.params.applicationId),
);

onMounted(() => {
  if (!showingApplicationDetail.value) loadApplications();
});
watch(academicPeriodContext.selectedAcademicPeriodId, () => {
  if (!showingApplicationDetail.value) void loadApplications();
});
watch(
  showingApplicationDetail,
  (isShowingApplicationDetail, wasShowingApplicationDetail) => {
    if (
      !isShowingApplicationDetail &&
      wasShowingApplicationDetail &&
      !applications.value.length
    )
      loadApplications();
  },
);

async function loadApplications() {
  loading.value = true;
  loadError.value = "";
  try {
    const [applicationResponse] = await Promise.all([
      api.request<AdmissionsApplicationSummary[]>(
        "/api/admissions/applications",
      ),
      academicPeriodContext.ensureIntakes(),
    ]);
    applications.value = applicationResponse.filter((application) =>
      academicPeriodContext.matchesIntake(application.intakeId),
    );
  } catch (error) {
    loadError.value = api.errorMessage(
      error,
      "The admissions queue could not be loaded.",
    );
  } finally {
    loading.value = false;
  }
}

async function waivePayment(application: AdmissionsApplicationSummary) {
  const result = await Swal.fire({
    title: "Authorise fee waiver?",
    text: `${application.applicationNumber} will be allowed to submit without payment confirmation.`,
    input: "textarea",
    inputLabel: "Authorisation reason",
    inputPlaceholder:
      "Record the policy basis, approver, or supporting reference",
    inputAttributes: { maxlength: "500" },
    inputValidator: (value) =>
      value.trim() ? undefined : "An authorisation reason is required.",
    icon: "warning",
    showCancelButton: true,
    confirmButtonText: "Authorise waiver",
    cancelButtonText: "Cancel",
    confirmButtonColor: "#20743a",
  });
  if (!result.isConfirmed) {
    return;
  }

  activeActionApplicationId.value = application.id;
  try {
    const updatedApplication = await api.request<AdmissionsApplicationSummary>(
      `/api/admissions/applications/${application.id}/payment-waiver`,
      { method: "POST", body: { reason: result.value.trim() } },
    );
    replaceApplication(updatedApplication);
    toast.add({
      title: "Fee waiver recorded",
      description: `${updatedApplication.applicationNumber} can now proceed to submission.`,
      color: "success",
      icon: "i-lucide-badge-check",
    });
  } catch (error) {
    await showError("Fee waiver failed", api.errorMessage(error));
  } finally {
    activeActionApplicationId.value = null;
  }
}

async function moveToReview(application: AdmissionsApplicationSummary) {
  const confirmed = await confirmAction({
    title: "Confirm application?",
    text: `${application.applicationNumber} must have cleared payment, complete sections, verified documents, and verified qualifications.`,
    confirmButtonText: "Confirm application",
    icon: "question",
  });
  if (!confirmed) {
    return;
  }

  activeActionApplicationId.value = application.id;
  try {
    const updatedApplication = await api.request<AdmissionsApplicationSummary>(
      `/api/admissions/applications/${application.id}/review`,
      {
        method: "POST",
        body: {
          reason:
            "Payment, required sections, documents, and qualifications confirmed by Admissions.",
        },
      },
    );
    replaceApplication(updatedApplication);
    toast.add({
      title: "Confirmed by Admissions",
      description: `${updatedApplication.applicationNumber} is cleared for eligibility and academic-unit release.`,
      color: "success",
      icon: "i-lucide-file-check-2",
    });
  } catch (error) {
    await showError(
      "Application could not be confirmed",
      api.errorMessage(error),
    );
  } finally {
    activeActionApplicationId.value = null;
  }
}

function reviewGuidance(application: AdmissionsApplicationSummary) {
  if (application.canEnterReview) return [];
  if (
    application.paymentRequired &&
    !["PAID", "WAIVED"].includes(application.paymentClearanceStatus)
  ) {
    return [
      "Confirm the application fee in Finance or record an authorised fee waiver before starting review.",
    ];
  }
  return [
    "Complete the required application sections and document checks before starting review.",
  ];
}

function replaceApplication(updatedApplication: AdmissionsApplicationSummary) {
  applications.value = applications.value.map((application) =>
    application.id === updatedApplication.id ? updatedApplication : application,
  );
}

function canWaivePayment(application: AdmissionsApplicationSummary) {
  return (
    application.status === "DRAFT" &&
    application.paymentRequired &&
    application.paymentClearanceStatus !== "PAID" &&
    application.paymentClearanceStatus !== "WAIVED"
  );
}

function applicationStatusTone(status: string) {
  if (status === "SUBMITTED" || status === "UNDER_REVIEW")
    return "info" as const;
  if (status === "OFFERED" || status === "ACCEPTED") return "success" as const;
  if (status === "DECLINED" || status === "WITHDRAWN") return "error" as const;
  return "neutral" as const;
}

function paymentStatusTone(
  status: AdmissionsApplicationSummary["paymentClearanceStatus"],
) {
  if (status === "PAID" || status === "WAIVED" || status === "NOT_REQUIRED")
    return "success" as const;
  return "warning" as const;
}

function paymentStatusLabel(
  status: AdmissionsApplicationSummary["paymentClearanceStatus"],
) {
  return {
    NOT_REQUIRED: "Not required",
    PENDING: "Payment pending",
    UNRATED: "Rate pending",
    PAID: "Paid",
    WAIVED: "Waived",
  }[status];
}

function formatStatus(status: string) {
  return status
    .toLowerCase()
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}
</script>

<template>
  <NuxtPage v-if="showingApplicationDetail" />
  <UDashboardPanel v-else>
    <template #header>
      <UDashboardNavbar title="Admissions review queue">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton
            icon="i-lucide-refresh-cw"
            label="Refresh"
            color="neutral"
            variant="outline"
            :loading="loading"
            @click="loadApplications"
          />
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar>
        <template #left>
          <UInput
            v-model="search"
            icon="i-lucide-search"
            placeholder="Search application, applicant, or payment reference"
            class="w-full sm:w-96"
          />
        </template>
        <template #right>
          <USelect
            v-model="statusFilter"
            :items="statusItems"
            value-key="value"
            class="w-48"
          />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="space-y-4 p-4 sm:p-6">
        <EmhareAdmissionsWorkflowNav current-stage="confirm" />
        <div class="grid gap-3 sm:grid-cols-3">
          <EmhareKpiCard
            label="Applications"
            :value="queueCounts.total"
            icon="i-lucide-files"
            tone="primary"
          />
          <EmhareKpiCard
            label="Awaiting finance"
            :value="queueCounts.awaitingPayment"
            icon="i-lucide-receipt-text"
            tone="warning"
          />
          <EmhareKpiCard
            label="Ready for review"
            :value="queueCounts.readyForReview"
            icon="i-lucide-file-check-2"
            tone="success"
          />
        </div>

        <UAlert
          color="info"
          variant="soft"
          icon="i-lucide-shield-check"
          title="Finance-owned payment clearance"
          description="Payment status is refreshed from Finance. Admissions can record an authorised waiver, but cannot manually mark a transaction as paid."
        />

        <UAlert
          v-if="loadError"
          color="error"
          variant="soft"
          icon="i-lucide-circle-alert"
          title="Admissions queue unavailable"
          :description="loadError"
        />

        <EmhareAdmissionsBatchList
          :batches="confirmationBatches"
          :loading="loading"
          empty-title="No application batches match this view"
          empty-description="Adjust the search or workflow status filter."
        >
          <template #applicant-actions="{ applicant }">
            <template v-if="applicationById(applicant.id)">
              <UButton
                v-if="canWaivePayment(applicationById(applicant.id)!)"
                label="Waive fee"
                color="warning"
                variant="ghost"
                :loading="activeActionApplicationId === applicant.id"
                @click="waivePayment(applicationById(applicant.id)!)"
              />
              <EmhareGuidedActionButton
                v-if="applicationById(applicant.id)!.status === 'SUBMITTED'"
                label="Confirm"
                icon="i-lucide-file-check-2"
                color="primary"
                guidance-title="Application cannot enter review yet"
                :guidance-instructions="
                  reviewGuidance(applicationById(applicant.id)!)
                "
                :loading="activeActionApplicationId === applicant.id"
                @click="moveToReview(applicationById(applicant.id)!)"
              />
            </template>
          </template>
        </EmhareAdmissionsBatchList>
      </div>
    </template>
  </UDashboardPanel>
</template>
