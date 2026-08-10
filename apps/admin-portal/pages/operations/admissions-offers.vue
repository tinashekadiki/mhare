<script setup lang="ts">
import Swal from "sweetalert2";
import type {
  AdmissionOfferSummary,
  AdmissionsApplicationSummary,
  AdmissionsWorkflowBatchView,
  OfferBatchSummary,
  SelectionDecisionSummary,
  SelectionRoundSummary,
} from "@emhare/portal-shell/types/admissions";

definePageMeta({ layout: "dashboard" });

const api = useEmhareApi();
const toast = useToast();
const { confirmAction, showError } = useEmhareConfirm();
const academicSetup = useAcademicSetup();
const academicPeriodContext = useAcademicPeriodContext();

const applications = ref<AdmissionsApplicationSummary[]>([]);
const selectionRounds = ref<SelectionRoundSummary[]>([]);
const selectionDecisions = ref<SelectionDecisionSummary[]>([]);
const batches = ref<OfferBatchSummary[]>([]);
const offers = ref<AdmissionOfferSummary[]>([]);
const loading = ref(false);
const activeActionId = ref<string | null>(null);
const loadError = ref("");
const batchModalOpen = ref(false);
const offerModalOpen = ref(false);

const workflowOfferBatches = computed<AdmissionsWorkflowBatchView[]>(() =>
  batches.value.map((batch) => {
    const batchOffers = offers.value.filter(
      (offer) => offer.offerBatchId === batch.id,
    );
    const readyCandidates = offerCandidatesForBatch(batch);
    return {
      id: batch.id,
      code: batch.code,
      title: batch.name,
      subtitle: `${formatStatus(batch.scopeType)} scope · ${readyCandidates.length} ready · ${batchOffers.length} generated`,
      stageLabel: "5 · Offer",
      statusLabel: formatStatus(batch.status),
      statusTone: batchTone(batch.status),
      applicants: [
        ...readyCandidates.map(({ application, choice }) => ({
          id: choice.id,
          applicationNumber: application.applicationNumber,
          applicantNumber: application.applicantNumber,
          applicantName: application.applicantName,
          programmeLabel: `${choice.programmeCode} · ${choice.programmeName}`,
          detail: "Approved selection · offer not yet generated",
          statusLabel: "Ready for offer",
          statusTone: "warning" as const,
          href: `/operations/admissions/${application.id}`,
        })),
        ...batchOffers.map((offer) => ({
          id: offer.id,
          applicationNumber: offer.applicationNumber,
          applicantNumber: offer.applicantNumber,
          applicantName: offer.applicantName,
          programmeLabel: `${offer.programmeCode} · ${offer.programmeName}`,
          detail: `${offer.offerNumber} · respond by ${formatDate(offer.acceptanceDeadline)}`,
          statusLabel: formatStatus(offer.status),
          statusTone: offerTone(offer.status),
          href: `/operations/admissions/${offer.applicationId}`,
        })),
      ],
    };
  }),
);

function offerBatchById(batchId: string) {
  return batches.value.find((batch) => batch.id === batchId);
}

function offerById(offerId: string) {
  return offers.value.find((offer) => offer.id === offerId);
}

function selectedApplicationChoice(programmeChoiceId: string) {
  for (const application of applications.value) {
    const choice = application.programmeChoices.find(
      (candidateChoice) => candidateChoice.id === programmeChoiceId,
    );
    if (choice) return { application, choice };
  }
  return null;
}

function programmeChoiceMatchesBatchScope(
  batch: OfferBatchSummary,
  programmeChoiceId: string,
) {
  if (batch.scopeType === "INSTITUTION") return true;
  const selectedChoice = selectedApplicationChoice(programmeChoiceId);
  if (!selectedChoice || !batch.scopeId) return false;
  if (batch.scopeType === "PROGRAMME") {
    return selectedChoice.choice.programmeId === batch.scopeId;
  }
  const programme = academicSetup.overview.value?.programmes.find(
    (item) => item.id === selectedChoice.choice.programmeId,
  );
  return programme?.owningAcademicUnitId === batch.scopeId;
}

function offerCandidatesForBatch(batch: OfferBatchSummary) {
  const generatedChoiceIds = new Set(
    offers.value.map((offer) => offer.programmeChoiceId),
  );
  return selectionDecisions.value.flatMap((decision) => {
    if (
      decision.selectionRoundId !== batch.selectionRoundId ||
      decision.decision !== "SELECT" ||
      generatedChoiceIds.has(decision.programmeChoiceId) ||
      !programmeChoiceMatchesBatchScope(batch, decision.programmeChoiceId)
    ) {
      return [];
    }
    const selectedChoice = selectedApplicationChoice(decision.programmeChoiceId);
    return selectedChoice ? [selectedChoice] : [];
  });
}

function isReadyOfferCandidate(programmeChoiceId: string) {
  return batches.value.some((batch) =>
    offerCandidatesForBatch(batch).some(
      ({ choice }) => choice.id === programmeChoiceId,
    ),
  );
}

const batchState = reactive({
  selectionRoundId: "",
  code: "",
  name: "",
  scopeType: "INSTITUTION",
  scopeId: "",
});

const offerState = reactive({
  offerBatchId: "",
  programmeChoiceId: "",
  offerType: "FIRM",
  conditionsText: "",
  acceptanceDeadline: "",
  registrationDate: "",
  orientationDate: "",
  commencementDate: "",
});

const approvedRoundItems = computed(() =>
  selectionRounds.value
    .filter((round) => round.status === "APPROVED")
    .map((round) => ({
      label: `${round.code} · ${round.name}`,
      value: round.id,
    })),
);

const approvedBatchItems = computed(() =>
  batches.value
    .filter((batch) => batch.status === "APPROVED")
    .map((batch) => ({
      label: `${batch.code} · ${batch.name}`,
      value: batch.id,
    })),
);

const selectedChoiceItems = computed(() =>
  applications.value.flatMap((application) =>
    application.programmeChoices
      .filter(
        (choice) =>
          choice.choiceStatus === "SELECTED" &&
          !offers.value.some(
            (offer) => offer.programmeChoiceId === choice.id,
          ),
      )
      .map((choice) => ({
        label: `${application.applicationNumber} · ${application.applicantName} · ${choice.programmeCode} · ${choice.programmeName}`,
        value: choice.id,
      })),
  ),
);
const newBatchGuidance = computed(() =>
  approvedRoundItems.value.length
    ? []
    : ["Approve a selection round before creating an offer batch."],
);
const generateOfferGuidance = computed(() => {
  const instructions: string[] = [];
  if (!approvedBatchItems.value.length)
    instructions.push("Create and approve an offer batch.");
  if (!selectedChoiceItems.value.length)
    instructions.push(
      "Approve a selected programme choice in Admissions selection.",
    );
  return instructions;
});

const scopeItems = [
  { label: "Institution", value: "INSTITUTION" },
  { label: "Academic unit", value: "ACADEMIC_UNIT" },
  { label: "Programme", value: "PROGRAMME" },
];

const scopeReferenceItems = computed(() => {
  if (batchState.scopeType === "ACADEMIC_UNIT") {
    return (academicSetup.overview.value?.academicUnits ?? [])
      .filter((unit) => unit.status === "ACTIVE")
      .map((unit) => ({
        label: `${unit.code} · ${unit.name} · ${unit.academicUnitTypeCode}`,
        value: unit.id,
      }));
  }
  if (batchState.scopeType === "PROGRAMME") {
    return (academicSetup.overview.value?.programmes ?? [])
      .filter((programme) => programme.status === "ACTIVE")
      .map((programme) => ({
        label: `${programme.code} · ${programme.name}`,
        value: programme.id,
      }));
  }
  return [];
});

const offerTypeItems = [
  { label: "Firm", value: "FIRM" },
  { label: "Conditional", value: "CONDITIONAL" },
];

onMounted(loadOfferWorkspace);
watch(
  academicPeriodContext.selectedAcademicPeriodId,
  () => void loadOfferWorkspace(),
);

watch(
  () => batchState.scopeType,
  () => {
    batchState.scopeId = "";
  },
);

async function loadOfferWorkspace() {
  loading.value = true;
  loadError.value = "";
  try {
    const [applicationResponse, roundResponse, batchResponse, offerResponse] =
      await Promise.all([
        api.request<AdmissionsApplicationSummary[]>(
          "/api/admissions/applications",
        ),
        api.request<SelectionRoundSummary[]>(
          "/api/admissions/selection-rounds",
        ),
        api.request<OfferBatchSummary[]>("/api/admissions/offer-batches"),
        api.request<AdmissionOfferSummary[]>("/api/admissions/offers"),
        academicSetup.ensureOverview().catch(() => undefined),
        academicPeriodContext.ensureIntakes(),
      ]);
    applications.value = applicationResponse.filter((application) =>
      academicPeriodContext.matchesIntake(application.intakeId),
    );
    selectionRounds.value = roundResponse.filter((round) =>
      academicPeriodContext.matchesIntake(round.intakeId),
    );
    batches.value = batchResponse.filter((batch) =>
      academicPeriodContext.matchesIntake(batch.intakeId),
    );
    const visibleSelectionRoundIds = [
      ...new Set(batches.value.map((batch) => batch.selectionRoundId)),
    ];
    const decisionResponses = await Promise.all(
      visibleSelectionRoundIds.map((selectionRoundId) =>
        api.request<SelectionDecisionSummary[]>(
          `/api/admissions/selection-rounds/${selectionRoundId}/decisions`,
        ),
      ),
    );
    selectionDecisions.value = decisionResponses.flat();
    const visibleApplicationIds = new Set(
      applications.value.map((application) => application.id),
    );
    offers.value = offerResponse.filter((offer) =>
      visibleApplicationIds.has(offer.applicationId),
    );
  } catch (error) {
    loadError.value = api.errorMessage(
      error,
      "The offer workspace could not be loaded.",
    );
  } finally {
    loading.value = false;
  }
}

function openBatchModal() {
  Object.assign(batchState, {
    selectionRoundId: approvedRoundItems.value[0]?.value ?? "",
    code: "",
    name: "",
    scopeType: "INSTITUTION",
    scopeId: "",
  });
  batchModalOpen.value = true;
}

async function createOfferBatch() {
  const round = selectionRounds.value.find(
    (item) => item.id === batchState.selectionRoundId,
  );
  if (!round || !batchState.code.trim() || !batchState.name.trim()) return;
  if (batchState.scopeType !== "INSTITUTION" && !batchState.scopeId.trim())
    return;
  activeActionId.value = "create-batch";
  try {
    const created = await api.request<OfferBatchSummary>(
      "/api/admissions/offer-batches",
      {
        method: "POST",
        body: {
          intakeId: round.intakeId,
          selectionRoundId: round.id,
          code: batchState.code.trim(),
          name: batchState.name.trim(),
          scopeType: batchState.scopeType,
          scopeId:
            batchState.scopeType === "INSTITUTION"
              ? null
              : batchState.scopeId.trim(),
        },
      },
    );
    batches.value = [created, ...batches.value];
    batchModalOpen.value = false;
    toast.add({
      title: "Offer batch created",
      description: "Approve it before generating offer records.",
      color: "success",
    });
  } catch (error) {
    await showError(
      "Offer batch could not be created",
      api.errorMessage(error),
    );
  } finally {
    activeActionId.value = null;
  }
}

async function approveBatch(batch: OfferBatchSummary) {
  const confirmed = await confirmAction({
    title: "Approve offer batch?",
    text: `${batch.code} will become the controlled container for generated offers from its approved selection round.`,
    confirmButtonText: "Approve batch",
    icon: "question",
  });
  if (!confirmed) return;
  activeActionId.value = batch.id;
  try {
    const updated = await api.request<OfferBatchSummary>(
      `/api/admissions/offer-batches/${batch.id}/approve`,
      { method: "POST" },
    );
    batches.value = batches.value.map((existing) =>
      existing.id === updated.id ? updated : existing,
    );
    toast.add({
      title: "Offer batch approved",
      description: updated.code,
      color: "success",
    });
  } catch (error) {
    await showError("Offer batch approval failed", api.errorMessage(error));
  } finally {
    activeActionId.value = null;
  }
}

async function transitionBatch(
  batch: OfferBatchSummary,
  action: "dispatch" | "close",
) {
  const confirmed = await confirmAction({
    title:
      action === "dispatch" ? "Complete batch dispatch?" : "Close offer batch?",
    text:
      action === "dispatch"
        ? "Every offer in this batch must already be dispatched or resolved. This records completion of the controlled release."
        : "A closed batch is final and cannot receive further offers.",
    confirmButtonText:
      action === "dispatch" ? "Complete dispatch" : "Close batch",
    icon: "question",
  });
  if (!confirmed) return;
  activeActionId.value = batch.id;
  try {
    const updated = await api.request<OfferBatchSummary>(
      `/api/admissions/offer-batches/${batch.id}/${action}`,
      { method: "POST" },
    );
    batches.value = batches.value.map((existing) =>
      existing.id === updated.id ? updated : existing,
    );
    toast.add({
      title:
        action === "dispatch"
          ? "Batch dispatch completed"
          : "Offer batch closed",
      description: updated.code,
      color: "success",
    });
  } catch (error) {
    await showError("Offer batch transition failed", api.errorMessage(error));
  } finally {
    activeActionId.value = null;
  }
}

function openOfferModal() {
  openOfferModalForChoice(
    approvedBatchItems.value[0]?.value ?? "",
    selectedChoiceItems.value[0]?.value ?? "",
  );
}

function openOfferModalForChoice(
  offerBatchId: string,
  programmeChoiceId: string,
) {
  const today = new Date();
  const deadline = new Date(today);
  deadline.setDate(deadline.getDate() + 30);
  const commencement = new Date(today);
  commencement.setDate(commencement.getDate() + 60);
  Object.assign(offerState, {
    offerBatchId,
    programmeChoiceId,
    offerType: "FIRM",
    conditionsText: "",
    acceptanceDeadline: deadline.toISOString().slice(0, 16),
    registrationDate: "",
    orientationDate: "",
    commencementDate: commencement.toISOString().slice(0, 10),
  });
  offerModalOpen.value = true;
}

async function createOffer() {
  if (
    !offerState.offerBatchId ||
    !offerState.programmeChoiceId ||
    !offerState.acceptanceDeadline ||
    !offerState.commencementDate
  )
    return;
  if (
    offerState.offerType === "CONDITIONAL" &&
    !offerState.conditionsText.trim()
  )
    return;
  activeActionId.value = "create-offer";
  try {
    const created = await api.request<AdmissionOfferSummary>(
      "/api/admissions/offers",
      {
        method: "POST",
        body: {
          offerBatchId: offerState.offerBatchId,
          programmeChoiceId: offerState.programmeChoiceId,
          offerType: offerState.offerType,
          conditionsText: offerState.conditionsText.trim() || null,
          acceptanceDeadline: new Date(
            offerState.acceptanceDeadline,
          ).toISOString(),
          registrationDate: offerState.registrationDate || null,
          orientationDate: offerState.orientationDate || null,
          commencementDate: offerState.commencementDate,
          conditions:
            offerState.offerType === "CONDITIONAL"
              ? [
                  {
                    code: "ADMISSION_CONDITION",
                    description: offerState.conditionsText.trim(),
                    required: true,
                  },
                ]
              : [],
        },
      },
    );
    offers.value = [created, ...offers.value];
    offerModalOpen.value = false;
    toast.add({
      title: "Offer draft generated",
      description: `${created.offerNumber} requires a stored document before approval.`,
      color: "success",
    });
  } catch (error) {
    await showError("Offer could not be generated", api.errorMessage(error));
  } finally {
    activeActionId.value = null;
  }
}

async function approveOffer(offer: AdmissionOfferSummary) {
  const confirmed = await confirmAction({
    title: "Approve offer for dispatch?",
    text: `${offer.offerNumber} will move the application to Offered. A stored generated document is mandatory.`,
    confirmButtonText: "Approve offer",
    icon: "question",
  });
  if (!confirmed) return;
  activeActionId.value = offer.id;
  try {
    const updated = await api.request<AdmissionOfferSummary>(
      `/api/admissions/offers/${offer.id}/approve`,
      { method: "POST" },
    );
    replaceOffer(updated);
    await loadApplicationsOnly();
    toast.add({
      title: "Offer approved",
      description: updated.offerNumber,
      color: "success",
    });
  } catch (error) {
    await showError("Offer approval failed", api.errorMessage(error));
  } finally {
    activeActionId.value = null;
  }
}

async function dispatchOffer(offer: AdmissionOfferSummary) {
  const result = await Swal.fire({
    title: "Dispatch approved offer",
    input: "email",
    inputLabel: "Recipient email address",
    inputPlaceholder: "applicant@example.com",
    inputValidator: (value) =>
      value.trim() ? undefined : "Recipient email is required.",
    icon: "question",
    showCancelButton: true,
    confirmButtonText: "Record dispatch",
    cancelButtonText: "Cancel",
    confirmButtonColor: "#20743a",
  });
  if (!result.isConfirmed) return;
  activeActionId.value = offer.id;
  try {
    const updated = await api.request<AdmissionOfferSummary>(
      `/api/admissions/offers/${offer.id}/dispatch`,
      {
        method: "POST",
        body: {
          deliveryMethodCode: "EMAIL",
          sentTo: result.value.trim(),
          providerMessageId: null,
        },
      },
    );
    replaceOffer(updated);
    toast.add({
      title: "Offer dispatch recorded",
      description: `${updated.offerNumber} is now visible for applicant response.`,
      color: "success",
    });
  } catch (error) {
    await showError("Offer dispatch failed", api.errorMessage(error));
  } finally {
    activeActionId.value = null;
  }
}

async function resolveCondition(
  offer: AdmissionOfferSummary,
  condition: AdmissionOfferSummary["conditions"][number],
) {
  const resolutionResult = await Swal.fire({
    title: "Resolve offer condition",
    input: "select",
    inputLabel: condition.description,
    inputOptions: { SATISFIED: "Satisfied", WAIVED: "Waived with authority" },
    inputPlaceholder: "Select a resolution",
    showCancelButton: true,
    confirmButtonText: "Continue",
    confirmButtonColor: "#20743a",
    inputValidator: (value) => (value ? undefined : "Select a resolution."),
  });
  if (!resolutionResult.isConfirmed) return;
  const notesResult = await Swal.fire({
    title:
      resolutionResult.value === "WAIVED"
        ? "Record waiver authority"
        : "Record resolution evidence",
    input: "textarea",
    inputLabel:
      resolutionResult.value === "WAIVED"
        ? "Waiver reason and authority"
        : "Evidence or verification notes",
    inputPlaceholder: "Record the evidence used for this decision",
    showCancelButton: true,
    confirmButtonText: "Resolve condition",
    confirmButtonColor: "#20743a",
    inputValidator: (value) =>
      resolutionResult.value === "WAIVED" && !value.trim()
        ? "A waiver reason is required."
        : undefined,
  });
  if (!notesResult.isConfirmed) return;
  activeActionId.value = condition.id;
  try {
    const updated = await api.request<AdmissionOfferSummary>(
      `/api/admissions/offers/${offer.id}/conditions/${condition.id}/resolve`,
      {
        method: "POST",
        body: {
          resolution: resolutionResult.value,
          notes: notesResult.value?.trim() || null,
        },
      },
    );
    replaceOffer(updated);
    toast.add({
      title: "Offer condition resolved",
      description: `${condition.code} · ${formatStatus(resolutionResult.value)}`,
      color: "success",
    });
  } catch (error) {
    await showError(
      "Offer condition could not be resolved",
      api.errorMessage(error),
    );
  } finally {
    activeActionId.value = null;
  }
}

async function withdrawOffer(offer: AdmissionOfferSummary) {
  const result = await Swal.fire({
    title: "Withdraw admission offer?",
    text: `${offer.offerNumber} will no longer be actionable. The application returns to Selected when the offer was already approved.`,
    input: "textarea",
    inputLabel: "Withdrawal reason",
    inputPlaceholder: "Record the controlled reason for withdrawal",
    icon: "warning",
    showCancelButton: true,
    confirmButtonText: "Withdraw offer",
    confirmButtonColor: "#b42318",
    inputValidator: (value) =>
      value.trim() ? undefined : "A withdrawal reason is required.",
  });
  if (!result.isConfirmed) return;
  activeActionId.value = offer.id;
  try {
    const updated = await api.request<AdmissionOfferSummary>(
      `/api/admissions/offers/${offer.id}/withdraw`,
      {
        method: "POST",
        body: { reason: result.value.trim() },
      },
    );
    replaceOffer(updated);
    await loadApplicationsOnly();
    toast.add({
      title: "Offer withdrawn",
      description: updated.offerNumber,
      color: "success",
    });
  } catch (error) {
    await showError("Offer withdrawal failed", api.errorMessage(error));
  } finally {
    activeActionId.value = null;
  }
}

async function expireOffer(offer: AdmissionOfferSummary) {
  const confirmed = await confirmAction({
    title: "Expire overdue offer?",
    text: `${offer.offerNumber} passed its acceptance deadline and will return the application to Selected.`,
    confirmButtonText: "Expire offer",
    icon: "warning",
  });
  if (!confirmed) return;
  activeActionId.value = offer.id;
  try {
    const updated = await api.request<AdmissionOfferSummary>(
      `/api/admissions/offers/${offer.id}/expire`,
      { method: "POST" },
    );
    replaceOffer(updated);
    await loadApplicationsOnly();
    toast.add({
      title: "Offer expired",
      description: updated.offerNumber,
      color: "success",
    });
  } catch (error) {
    await showError("Offer expiry failed", api.errorMessage(error));
  } finally {
    activeActionId.value = null;
  }
}

function isPastDeadline(offer: AdmissionOfferSummary) {
  return new Date(offer.acceptanceDeadline).getTime() < Date.now();
}

async function loadApplicationsOnly() {
  const applicationResponse = await api.request<AdmissionsApplicationSummary[]>(
    "/api/admissions/applications",
  );
  applications.value = applicationResponse.filter((application) =>
    academicPeriodContext.matchesIntake(application.intakeId),
  );
}

function replaceOffer(updated: AdmissionOfferSummary) {
  offers.value = offers.value.map((existing) =>
    existing.id === updated.id ? updated : existing,
  );
}

function offerTone(status: AdmissionOfferSummary["status"]) {
  if (status === "SENT") return "info" as const;
  if (status === "ACCEPTED" || status === "CONVERTED")
    return "success" as const;
  if (status === "DECLINED" || status === "EXPIRED" || status === "WITHDRAWN")
    return "error" as const;
  return "neutral" as const;
}

function batchTone(status: OfferBatchSummary["status"]) {
  if (status === "APPROVED" || status === "DISPATCHED")
    return "success" as const;
  return "neutral" as const;
}

function formatStatus(status: string) {
  return status
    .toLowerCase()
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en-ZW", { dateStyle: "medium" }).format(
    new Date(value),
  );
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Admissions offer management">
        <template #leading><UDashboardSidebarCollapse /></template>
        <template #right>
          <EmhareGuidedActionButton
            label="New batch"
            icon="i-lucide-layers-3"
            color="neutral"
            variant="outline"
            guidance-title="Offer batch setup required"
            :guidance-instructions="newBatchGuidance"
            guidance-action-label="Open Selection rounds"
            @guidance-action="navigateTo('/operations/admissions-selection')"
            @click="openBatchModal"
          />
          <EmhareGuidedActionButton
            label="Generate offer"
            icon="i-lucide-file-badge"
            color="primary"
            guidance-title="Offer generation is not ready"
            :guidance-instructions="generateOfferGuidance"
            :guidance-action-label="
              !selectedChoiceItems.length ? 'Open Selection rounds' : undefined
            "
            @guidance-action="navigateTo('/operations/admissions-selection')"
            @click="openOfferModal"
          />
          <UButton
            label="Refresh"
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="outline"
            :loading="loading"
            @click="loadOfferWorkspace"
          />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="space-y-6 p-4 sm:p-6">
        <EmhareAdmissionsWorkflowNav current-stage="offer" />
        <UAlert
          color="info"
          variant="soft"
          icon="i-lucide-file-lock-2"
          title="Approved-selection and document gate"
          description="Offers can only originate from an approved selected decision inside an approved scoped batch. Approval requires a stored generated document; applicant responses are immutable."
        />
        <UAlert
          v-if="loadError"
          color="error"
          variant="soft"
          title="Offer workspace unavailable"
          :description="loadError"
        />

        <section class="space-y-3" aria-labelledby="offer-batches-heading">
          <h2
            id="offer-batches-heading"
            class="text-lg font-semibold text-highlighted"
          >
            Offer batches
          </h2>
          <EmhareAdmissionsBatchList
            :batches="workflowOfferBatches"
            :loading="loading"
            empty-title="No offer batches"
            empty-description="Approve a selection round, then create a scoped offer batch."
          >
            <template #batch-actions="{ batch }">
              <template v-if="offerBatchById(batch.id)">
                <UButton
                  v-if="offerBatchById(batch.id)!.status === 'DRAFT'"
                  label="Approve batch"
                  icon="i-lucide-badge-check"
                  color="primary"
                  size="sm"
                  :loading="activeActionId === batch.id"
                  @click="approveBatch(offerBatchById(batch.id)!)"
                />
                <UButton
                  v-if="offerBatchById(batch.id)!.status === 'APPROVED'"
                  label="Complete dispatch"
                  icon="i-lucide-send-horizontal"
                  color="primary"
                  size="sm"
                  :loading="activeActionId === batch.id"
                  @click="
                    transitionBatch(offerBatchById(batch.id)!, 'dispatch')
                  "
                />
                <UButton
                  v-if="offerBatchById(batch.id)!.status === 'DISPATCHED'"
                  label="Close batch"
                  icon="i-lucide-lock-keyhole"
                  color="neutral"
                  variant="outline"
                  size="sm"
                  :loading="activeActionId === batch.id"
                  @click="transitionBatch(offerBatchById(batch.id)!, 'close')"
                />
              </template>
            </template>
            <template #applicant-actions="{ applicant, batch }">
              <UButton
                v-if="
                  isReadyOfferCandidate(applicant.id) &&
                  offerBatchById(batch.id)?.status === 'APPROVED'
                "
                label="Generate offer"
                icon="i-lucide-file-badge"
                color="primary"
                size="sm"
                @click="openOfferModalForChoice(batch.id, applicant.id)"
              />
              <template v-if="offerById(applicant.id)">
                <EmhareGuidedActionButton
                  v-if="offerById(applicant.id)!.status === 'DRAFT'"
                  label="Approve"
                  icon="i-lucide-badge-check"
                  color="primary"
                  guidance-title="Official offer letter is still being prepared"
                  guidance-description="The system generates and stores the official PDF automatically after the offer draft is created."
                  :guidance-instructions="
                    offerById(applicant.id)!.generatedDocumentId
                      ? []
                      : [
                          'Wait a moment, then refresh the offer status. Approval becomes available as soon as the stored document is confirmed.',
                        ]
                  "
                  :guidance-action-label="
                    offerById(applicant.id)!.generatedDocumentId
                      ? undefined
                      : 'Refresh offer status'
                  "
                  :loading="activeActionId === applicant.id"
                  @guidance-action="loadOfferWorkspace"
                  @click="approveOffer(offerById(applicant.id)!)"
                />
                <UButton
                  v-if="offerById(applicant.id)!.status === 'APPROVED'"
                  label="Dispatch"
                  icon="i-lucide-send"
                  color="primary"
                  :loading="activeActionId === applicant.id"
                  @click="dispatchOffer(offerById(applicant.id)!)"
                />
                <UButton
                  v-if="
                    offerById(applicant.id)!.status === 'SENT' &&
                    isPastDeadline(offerById(applicant.id)!)
                  "
                  label="Expire"
                  color="warning"
                  variant="soft"
                  :loading="activeActionId === applicant.id"
                  @click="expireOffer(offerById(applicant.id)!)"
                />
                <UButton
                  v-if="
                    ['DRAFT', 'APPROVED', 'SENT'].includes(
                      offerById(applicant.id)!.status,
                    )
                  "
                  label="Withdraw"
                  color="error"
                  variant="soft"
                  :loading="activeActionId === applicant.id"
                  @click="withdrawOffer(offerById(applicant.id)!)"
                />
              </template>
            </template>
          </EmhareAdmissionsBatchList>
        </section>

        <section class="space-y-3" aria-labelledby="offers-register-heading">
          <h2
            id="offers-register-heading"
            class="text-lg font-semibold text-highlighted"
          >
            Offer register
          </h2>
          <EmharePaginatedCollection
            v-slot="{ items: paginatedOffers }"
            :items="offers"
          >
            <div class="space-y-3">
              <UCard
                v-for="offer in paginatedOffers"
                :key="offer.id"
                variant="outline"
              >
                <div
                  class="grid gap-4 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-center"
                >
                  <div>
                    <div class="flex flex-wrap items-center gap-2">
                      <p class="font-mono text-xs text-muted">
                        {{ offer.offerNumber }}
                      </p>
                      <EmhareStatusPill
                        :label="formatStatus(offer.status)"
                        :tone="offerTone(offer.status)"
                      />
                    </div>
                    <h3 class="mt-2 font-semibold text-highlighted">
                      {{ offer.applicantName }}
                    </h3>
                    <p class="mt-1 font-mono text-xs text-muted">
                      {{ offer.applicationNumber }} · Applicant
                      {{ offer.applicantNumber }}
                    </p>
                    <p class="mt-1 text-sm font-medium text-highlighted">
                      {{ offer.programmeCode }} · {{ offer.programmeName }}
                    </p>
                    <p class="mt-1 text-sm text-muted">
                      {{ formatStatus(offer.offerType) }} · Respond by
                      {{ formatDate(offer.acceptanceDeadline) }} · Commences
                      {{ formatDate(offer.commencementDate) }}
                    </p>
                    <p class="mt-1 text-xs text-muted">
                      {{
                        offer.generatedDocumentId
                          ? `Stored document ${offer.generatedDocumentId}`
                          : "Official letter generation pending"
                      }}
                    </p>
                    <UAlert
                      v-if="offer.convertedStudentNumber"
                      class="mt-3"
                      color="success"
                      variant="soft"
                      icon="i-lucide-user-round-check"
                      title="Student record active"
                      :description="`${offer.convertedStudentNumber} was provisioned on ${formatDate(offer.convertedAt!)}.`"
                    />
                    <UAlert
                      v-else-if="offer.conversionRequestedAt"
                      class="mt-3"
                      color="info"
                      variant="soft"
                      icon="i-lucide-loader-circle"
                      title="Student conversion in progress"
                      description="Finance account and portal access must both complete before the student becomes active."
                    />
                    <EmharePaginatedCollection
                      v-if="offer.conditions.length"
                      :items="offer.conditions"
                      :initial-page-size="5"
                      v-slot="{ items: paginatedConditions }"
                      ><div class="mt-3 space-y-2 rounded-md bg-muted/40 p-3">
                        <div
                          v-for="condition in paginatedConditions"
                          :key="condition.id"
                          class="flex flex-wrap items-start justify-between gap-2"
                        >
                          <div>
                            <p class="text-xs font-medium text-highlighted">
                              {{ condition.code }} · {{ condition.description }}
                            </p>
                            <p
                              v-if="condition.resolutionNotes"
                              class="mt-0.5 text-xs text-muted"
                            >
                              {{ condition.resolutionNotes }}
                            </p>
                          </div>
                          <div class="flex items-center gap-2">
                            <EmhareStatusPill
                              :label="formatStatus(condition.status)"
                              :tone="
                                condition.status === 'PENDING'
                                  ? 'warning'
                                  : 'success'
                              "
                            />
                            <UButton
                              v-if="
                                condition.status === 'PENDING' &&
                                ['SENT', 'ACCEPTED'].includes(offer.status)
                              "
                              label="Resolve"
                              size="xs"
                              color="neutral"
                              variant="outline"
                              :loading="activeActionId === condition.id"
                              @click="resolveCondition(offer, condition)"
                            />
                          </div>
                        </div></div
                    ></EmharePaginatedCollection>
                  </div>
                  <div class="flex flex-wrap justify-end gap-2">
                    <EmhareGuidedActionButton
                      v-if="offer.status === 'DRAFT'"
                      label="Approve"
                      icon="i-lucide-badge-check"
                      color="primary"
                      guidance-title="Official offer letter is still being prepared"
                      guidance-description="The system generates and stores the official PDF automatically after the offer draft is created."
                      :guidance-instructions="
                        offer.generatedDocumentId
                          ? []
                          : [
                              'Wait a moment, then refresh the offer status. Approval becomes available as soon as the stored document is confirmed.',
                            ]
                      "
                      :guidance-action-label="
                        offer.generatedDocumentId
                          ? undefined
                          : 'Refresh offer status'
                      "
                      :loading="activeActionId === offer.id"
                      @guidance-action="loadOfferWorkspace"
                      @click="approveOffer(offer)"
                    />
                    <UButton
                      v-if="offer.status === 'APPROVED'"
                      label="Dispatch"
                      icon="i-lucide-send"
                      color="primary"
                      :loading="activeActionId === offer.id"
                      @click="dispatchOffer(offer)"
                    />
                    <UButton
                      v-if="offer.status === 'SENT' && isPastDeadline(offer)"
                      label="Expire"
                      icon="i-lucide-clock-alert"
                      color="warning"
                      variant="soft"
                      :loading="activeActionId === offer.id"
                      @click="expireOffer(offer)"
                    />
                    <UButton
                      v-if="
                        ['DRAFT', 'APPROVED', 'SENT'].includes(offer.status)
                      "
                      label="Withdraw"
                      icon="i-lucide-ban"
                      color="error"
                      variant="soft"
                      :loading="activeActionId === offer.id"
                      @click="withdrawOffer(offer)"
                    />
                    <span
                      v-if="
                        [
                          'ACCEPTED',
                          'DECLINED',
                          'EXPIRED',
                          'WITHDRAWN',
                          'CONVERTED',
                        ].includes(offer.status)
                      "
                      class="text-xs text-muted"
                      >No pending staff action</span
                    >
                  </div>
                </div>
              </UCard>
              <UEmpty
                v-if="!offers.length && !loading"
                title="No offers generated"
                description="Generate an offer from an approved selected programme choice."
              />
            </div>
          </EmharePaginatedCollection>
        </section>
      </div>
    </template>
  </UDashboardPanel>

  <EmhareRecordDrawer
    v-model:open="batchModalOpen"
    title="Create offer batch"
    description="Bind offers to one approved selection round and an explicit organisational scope."
  >
    <template #body>
      <form
        id="offer-batch-form"
        class="space-y-4"
        @submit.prevent="createOfferBatch"
      >
        <UFormField label="Approved selection round" required
          ><USelect
            v-model="batchState.selectionRoundId"
            :items="approvedRoundItems"
            value-key="value"
            class="w-full"
        /></UFormField>
        <div class="grid gap-4 sm:grid-cols-2">
          <UFormField label="Batch code" required
            ><UInput
              v-model="batchState.code"
              maxlength="50"
              placeholder="2027-R1-OFFERS"
              class="w-full"
          /></UFormField>
          <UFormField label="Batch name" required
            ><UInput
              v-model="batchState.name"
              maxlength="180"
              placeholder="First merit offers"
              class="w-full"
          /></UFormField>
          <UFormField label="Scope" required
            ><USelect
              v-model="batchState.scopeType"
              :items="scopeItems"
              value-key="value"
              class="w-full"
          /></UFormField>
          <UFormField
            v-if="batchState.scopeType !== 'INSTITUTION'"
            :label="
              batchState.scopeType === 'ACADEMIC_UNIT'
                ? 'Academic unit'
                : 'Programme'
            "
            required
          >
            <USelectMenu
              v-model="batchState.scopeId"
              :items="scopeReferenceItems"
              value-key="value"
              label-key="label"
              :aria-label="
                batchState.scopeType === 'ACADEMIC_UNIT'
                  ? 'Academic unit'
                  : 'Programme'
              "
              :placeholder="
                batchState.scopeType === 'ACADEMIC_UNIT'
                  ? 'Search academic units'
                  : 'Search programmes'
              "
              class="w-full"
            />
          </UFormField>
        </div>
      </form>
    </template>
    <template #footer>
      <UButton
        label="Cancel"
        color="neutral"
        variant="outline"
        @click="batchModalOpen = false"
      />
      <UButton
        type="submit"
        form="offer-batch-form"
        label="Create batch"
        color="primary"
        :loading="activeActionId === 'create-batch'"
      />
    </template>
  </EmhareRecordDrawer>

  <EmhareRecordDrawer
    v-model:open="offerModalOpen"
    title="Generate offer draft"
    description="Create an auditable offer from one approved selected programme choice."
  >
    <template #body>
      <form id="offer-form" class="space-y-4" @submit.prevent="createOffer">
        <UFormField label="Approved offer batch" required
          ><USelect
            v-model="offerState.offerBatchId"
            :items="approvedBatchItems"
            value-key="value"
            class="w-full"
        /></UFormField>
        <UFormField label="Selected programme choice" required
          ><USelect
            v-model="offerState.programmeChoiceId"
            :items="selectedChoiceItems"
            value-key="value"
            class="w-full"
        /></UFormField>
        <div class="grid gap-4 sm:grid-cols-2">
          <UFormField label="Offer type" required
            ><USelect
              v-model="offerState.offerType"
              :items="offerTypeItems"
              value-key="value"
              class="w-full"
          /></UFormField>
          <UFormField label="Acceptance deadline" required
            ><UInput
              v-model="offerState.acceptanceDeadline"
              type="datetime-local"
              class="w-full"
          /></UFormField>
          <UFormField label="Registration date"
            ><UInput
              v-model="offerState.registrationDate"
              type="date"
              class="w-full"
          /></UFormField>
          <UFormField label="Orientation date"
            ><UInput
              v-model="offerState.orientationDate"
              type="date"
              class="w-full"
          /></UFormField>
          <UFormField label="Commencement date" required
            ><UInput
              v-model="offerState.commencementDate"
              type="date"
              class="w-full"
          /></UFormField>
        </div>
        <UFormField
          v-if="offerState.offerType === 'CONDITIONAL'"
          label="Conditions"
          required
          ><UTextarea
            v-model="offerState.conditionsText"
            :rows="4"
            maxlength="4000"
            class="w-full"
        /></UFormField>
      </form>
    </template>
    <template #footer>
      <UButton
        label="Cancel"
        color="neutral"
        variant="outline"
        @click="offerModalOpen = false"
      />
      <UButton
        type="submit"
        form="offer-form"
        label="Generate draft"
        icon="i-lucide-file-badge"
        color="primary"
        :loading="activeActionId === 'create-offer'"
      />
    </template>
  </EmhareRecordDrawer>
</template>
