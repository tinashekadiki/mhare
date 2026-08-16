<script setup lang="ts">
import Swal from "sweetalert2";
import type {
  AdmissionOfferSummary,
  ApplicationDocumentRegister,
  ApplicationDocumentRequirementState,
  AdmissionsApplicationSummary,
} from "@emhare/portal-shell/types/admissions";
import type { UploadedDocumentSummary } from "@emhare/portal-shell/types/documents";

definePageMeta({ public: true });

const auth = useEmhareAuth();
const api = useEmhareApi();
const toast = useToast();
const { confirmAction, showError, showSuccess } = useEmhareConfirm();
const { openingOfferId, openOfferLetter } = useApplicantOfferLetter();

const applications = ref<AdmissionsApplicationSummary[]>([]);
const offers = ref<AdmissionOfferSummary[]>([]);
const loadingApplications = ref(false);
const submittingApplicationId = ref<string | null>(null);
const respondingOfferId = ref<string | null>(null);
const loadError = ref("");
const documentRegisters = reactive<
  Record<string, ApplicationDocumentRegister | undefined>
>({});
const documentRegisterErrors = reactive<Record<string, string | undefined>>({});
const loadingDocumentApplicationId = ref<string | null>(null);
const documentDrawerOpen = ref(false);
const selectedDocumentApplication = ref<AdmissionsApplicationSummary | null>(
  null,
);
const uploadingDocument = ref(false);
const documentUploadForm = reactive<{
  requirementCode: string;
  file: File | File[] | null;
}>({ requirementCode: "", file: null });

const selectedDocumentRegister = computed(() =>
  selectedDocumentApplication.value
    ? (documentRegisters[selectedDocumentApplication.value.id] ?? null)
    : null,
);
const uploadableDocumentRequirements = computed(
  () =>
    selectedDocumentRegister.value?.requirements
      .filter(
        (requirement) =>
          requirement.state === "MISSING" || requirement.state === "REJECTED",
      )
      .map((requirement) => ({
        label: `${requirement.requirementName}${requirement.required ? " · Required" : " · Optional"}`,
        value: requirement.requirementCode,
        description:
          requirement.state === "REJECTED"
            ? (requirement.rejectionReason ?? "Replacement requested")
            : "Not uploaded",
      })) ?? [],
);
const selectedUploadRequirement = computed(
  () =>
    selectedDocumentRegister.value?.requirements.find(
      (requirement) =>
        requirement.requirementCode === documentUploadForm.requirementCode,
    ) ?? null,
);
const selectedDocumentFile = computed(() =>
  Array.isArray(documentUploadForm.file)
    ? documentUploadForm.file[0]
    : documentUploadForm.file,
);
const documentUploadDisabled = computed(
  () =>
    !selectedDocumentApplication.value ||
    !selectedUploadRequirement.value ||
    !selectedDocumentFile.value,
);

onMounted(async () => {
  await auth.loadUser();
  if (!auth.authenticated.value) {
    return;
  }
  await auth.syncCoreUser();
  await Promise.all([
    loadApplications(),
    loadOffers(),
  ]);
});

async function loadApplications() {
  loadingApplications.value = true;
  loadError.value = "";
  try {
    applications.value = await api.request<AdmissionsApplicationSummary[]>(
      "/api/admissions/applications/mine",
    );
    await Promise.all(
      applications.value.map((application) =>
        loadDocumentRegister(application, false),
      ),
    );
  } catch (error) {
    loadError.value = api.errorMessage(
      error,
      "Applications could not be loaded.",
    );
  } finally {
    loadingApplications.value = false;
  }
}

async function loadDocumentRegister(
  application: AdmissionsApplicationSummary,
  showLoading = true,
) {
  if (showLoading) loadingDocumentApplicationId.value = application.id;
  documentRegisterErrors[application.id] = undefined;
  try {
    documentRegisters[application.id] =
      await api.request<ApplicationDocumentRegister>(
        `/api/admissions/applications/${application.id}/documents/mine`,
      );
  } catch (error) {
    documentRegisterErrors[application.id] = api.errorMessage(
      error,
      "Document requirements could not be loaded.",
    );
  } finally {
    if (showLoading) loadingDocumentApplicationId.value = null;
  }
}

async function openDocumentDrawer(application: AdmissionsApplicationSummary) {
  selectedDocumentApplication.value = application;
  Object.assign(documentUploadForm, { requirementCode: "", file: null });
  documentDrawerOpen.value = true;
  await loadDocumentRegister(application);
  documentUploadForm.requirementCode =
    uploadableDocumentRequirements.value[0]?.value ?? "";
}

function closeDocumentDrawer() {
  selectedDocumentApplication.value = null;
  Object.assign(documentUploadForm, { requirementCode: "", file: null });
}

function chooseDocumentRequirement(
  requirement: ApplicationDocumentRequirementState,
) {
  if (requirement.state !== "MISSING" && requirement.state !== "REJECTED")
    return;
  documentUploadForm.requirementCode = requirement.requirementCode;
}

async function uploadApplicationDocument() {
  const application = selectedDocumentApplication.value;
  const requirement = selectedUploadRequirement.value;
  const file = selectedDocumentFile.value;
  if (!application || !requirement || !file) return;
  uploadingDocument.value = true;
  try {
    const uploadBody = new FormData();
    uploadBody.append("ownerType", "APPLICATION");
    uploadBody.append("ownerId", application.id);
    uploadBody.append("documentTypeCode", requirement.requirementCode);
    if (requirement.state === "REJECTED" && requirement.documentId) {
      uploadBody.append("replacesDocumentId", requirement.documentId);
    }
    uploadBody.append("file", file);
    const uploadedDocument = await api.request<UploadedDocumentSummary>(
      "/api/documents/uploads",
      {
        method: "POST",
        body: uploadBody,
      },
    );
    documentRegisters[application.id] =
      await api.request<ApplicationDocumentRegister>(
        `/api/admissions/applications/${application.id}/documents`,
        {
          method: "POST",
          body: {
            documentId: uploadedDocument.id,
            requirementCode: requirement.requirementCode,
          },
        },
      );
    Object.assign(documentUploadForm, {
      requirementCode: uploadableDocumentRequirements.value[0]?.value ?? "",
      file: null,
    });
    await showSuccess(
      "Document uploaded",
      `${requirement.requirementName} is now pending independent verification.`,
    );
  } catch (error) {
    await showError("Document could not be uploaded", api.errorMessage(error));
  } finally {
    uploadingDocument.value = false;
  }
}

async function loadOffers() {
  try {
    offers.value = await api.request<AdmissionOfferSummary[]>(
      "/api/admissions/offers/mine",
    );
  } catch (error) {
    loadError.value = api.errorMessage(error, "Offers could not be loaded.");
  }
}

async function refreshPortal() {
  await Promise.all([loadApplications(), loadOffers()]);
}

async function submitApplication(application: AdmissionsApplicationSummary) {
  const confirmed = await confirmAction({
    title: "Submit application?",
    text: `${application.applicationNumber} will enter the admissions review queue. Submitted details cannot be treated as a draft.`,
    confirmButtonText: "Submit application",
    icon: "question",
  });
  if (!confirmed) {
    return;
  }

  submittingApplicationId.value = application.id;
  try {
    const submittedApplication =
      await api.request<AdmissionsApplicationSummary>(
        `/api/admissions/applications/${application.id}/submission`,
        { method: "POST" },
      );
    applications.value = applications.value.map((existingApplication) =>
      existingApplication.id === submittedApplication.id
        ? submittedApplication
        : existingApplication,
    );
    toast.add({
      title: "Application submitted",
      description: `${submittedApplication.applicationNumber} is now ready for admissions review.`,
      color: "success",
      icon: "i-lucide-circle-check",
    });
  } catch (error) {
    await showError(
      "Application could not be submitted",
      api.errorMessage(error),
    );
  } finally {
    submittingApplicationId.value = null;
  }
}

async function acceptOffer(offer: AdmissionOfferSummary) {
  const confirmed = await confirmAction({
    title: "Accept this offer?",
    text: `${offer.offerNumber} for ${offer.programmeName} will be accepted. This response is permanent and enables controlled student conversion.`,
    confirmButtonText: "Accept offer",
    icon: "question",
  });
  if (!confirmed) return;
  await respondToOffer(
    offer,
    "ACCEPTED",
    "Accepted by applicant through the applicant portal.",
  );
}

async function declineOffer(offer: AdmissionOfferSummary) {
  const result = await Swal.fire({
    title: "Decline this offer?",
    text: `${offer.offerNumber} will be permanently declined and the place may be released.`,
    input: "textarea",
    inputLabel: "Optional note",
    inputPlaceholder: "Add context for Admissions",
    inputAttributes: { maxlength: "1000" },
    icon: "warning",
    showCancelButton: true,
    confirmButtonText: "Decline offer",
    cancelButtonText: "Keep offer",
    confirmButtonColor: "#b42318",
  });
  if (!result.isConfirmed) return;
  await respondToOffer(offer, "DECLINED", result.value?.trim() || null);
}

async function respondToOffer(
  offer: AdmissionOfferSummary,
  response: "ACCEPTED" | "DECLINED",
  notes: string | null,
) {
  respondingOfferId.value = offer.id;
  try {
    const updatedOffer = await api.request<AdmissionOfferSummary>(
      `/api/admissions/offers/${offer.id}/response`,
      {
        method: "POST",
        body: { response, notes },
      },
    );
    offers.value = offers.value.map((existingOffer) =>
      existingOffer.id === updatedOffer.id ? updatedOffer : existingOffer,
    );
    await loadApplications();
    await showSuccess(
      response === "ACCEPTED" ? "Offer accepted" : "Offer declined",
      `${updatedOffer.offerNumber} now records your permanent response.`,
    );
  } catch (error) {
    await showError(
      "Offer response could not be recorded",
      api.errorMessage(error),
    );
  } finally {
    respondingOfferId.value = null;
  }
}

function offerStatusTone(status: AdmissionOfferSummary["status"]) {
  if (status === "SENT") return "info" as const;
  if (status === "ACCEPTED" || status === "CONVERTED")
    return "success" as const;
  if (status === "DECLINED" || status === "EXPIRED" || status === "WITHDRAWN")
    return "error" as const;
  return "neutral" as const;
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
  if (status === "UNRATED") return "warning" as const;
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

function documentStatusTone(
  state: ApplicationDocumentRequirementState["state"],
) {
  if (state === "VERIFIED") return "success" as const;
  if (state === "REJECTED" || state === "MISSING") return "error" as const;
  return "warning" as const;
}

function applicationCanSubmit(application: AdmissionsApplicationSummary) {
  return (
    application.canSubmit &&
    Boolean(documentRegisters[application.id]?.requiredDocumentsUploaded)
  );
}

function applicationHasClearedSubmissionRequirements(application: AdmissionsApplicationSummary) {
  return application.status !== "DRAFT" || applicationCanSubmit(application);
}

function applicationJourneySteps(application: AdmissionsApplicationSummary) {
  const decided = ["OFFERED", "ACCEPTED", "DECLINED", "WITHDRAWN"].includes(
    application.status,
  );
  return [
    { label: "Application", done: true },
    { label: "Documents & fee", done: applicationHasClearedSubmissionRequirements(application) },
    { label: "Submitted", done: application.status !== "DRAFT" },
    { label: "Decision", done: decided },
  ];
}

function applicationSubmissionGuidance(application: AdmissionsApplicationSummary) {
  if (application.status !== "DRAFT") return [];

  const instructions: string[] = [];
  const documentRegister = documentRegisters[application.id];

  if (!application.programmeChoices.length) instructions.push('Add at least one programme choice.');
  if (application.paymentRequired && !['PAID', 'WAIVED'].includes(application.paymentClearanceStatus)) {
    instructions.push(application.paymentClearanceStatus === 'UNRATED'
      ? 'Finance must capture an effective exchange rate and rate your payment before submission.'
      : 'Pay the application fee and wait for Finance confirmation, or obtain an authorised waiver.');
  }
  if (documentRegister?.missingRequirementCodes.length) {
    instructions.push(`Upload the missing required documents: ${documentRegister.missingRequirementCodes.join(', ')}.`);
  }
  if (documentRegister?.rejectedRequirementCodes.length) {
    instructions.push(`Replace the rejected documents: ${documentRegister.rejectedRequirementCodes.join(', ')}.`);
  }
  if (!documentRegister) instructions.push('Wait for the document requirements to finish loading.');
  if (!application.canSubmit && !instructions.length) instructions.push('Complete all required application sections before submission.');
  return applicationCanSubmit(application) ? [] : instructions;
}

function applicationDocumentStatus(application: AdmissionsApplicationSummary) {
  const register = documentRegisters[application.id];
  if (!register)
    return {
      label: "Requirements unavailable",
      tone: "neutral" as const,
      icon: "i-lucide-file-question",
    };
  if (register.rejectedRequirementCodes.length)
    return {
      label: `${register.rejectedRequirementCodes.length} replacement required`,
      tone: "error" as const,
      icon: "i-lucide-file-x-2",
    };
  if (register.missingRequirementCodes.length)
    return {
      label: `${register.missingRequirementCodes.length} document missing`,
      tone: "error" as const,
      icon: "i-lucide-file-question",
    };
  if (register.pendingRequirementCodes.length)
    return {
      label: `${register.pendingRequirementCodes.length} pending verification`,
      tone: "warning" as const,
      icon: "i-lucide-clock-3",
    };
  return {
    label: "Documents verified",
    tone: "success" as const,
    icon: "i-lucide-shield-check",
  };
}

function formatMoney(amount: number | null, currencyCode: string | null) {
  if (amount === null || !currencyCode) {
    return "No fee";
  }
  return new Intl.NumberFormat("en-ZW", {
    style: "currency",
    currency: currencyCode,
  }).format(amount);
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en-ZW", { dateStyle: "medium" }).format(
    new Date(value),
  );
}
</script>

<template>
  <div>
    <div class="border-b border-muted">
      <UContainer class="flex h-16 items-center justify-between">
        <div class="flex items-center gap-2.5">
          <div
            class="grid size-8 shrink-0 place-items-center rounded-md bg-primary text-sm font-bold text-inverted"
          >
            e
          </div>
          <div class="leading-tight">
            <p class="text-sm font-semibold text-highlighted">eMhare</p>
            <p class="text-[11px] text-muted">Admissions</p>
          </div>
        </div>
        <div class="flex items-center gap-2">
          <template v-if="!auth.authenticated.value">
            <UButton
              label="Sign in"
              color="neutral"
              variant="ghost"
              @click="auth.login('/')"
            />
            <UButton
              label="Create account"
              icon="i-lucide-user-plus"
              color="primary"
              @click="auth.signup('/')"
            />
          </template>
          <template v-else>
            <UButton
              icon="i-lucide-refresh-cw"
              color="neutral"
              variant="ghost"
              aria-label="Refresh"
              :loading="loadingApplications"
              @click="refreshPortal"
            />
            <UDropdownMenu
              :items="[
                [{ label: auth.displayName.value, disabled: true }],
                [{ label: 'Sign out', icon: 'i-lucide-log-out', onSelect: () => auth.logout() }],
              ]"
            >
              <UButton
                :label="auth.displayName.value"
                icon="i-lucide-circle-user-round"
                color="neutral"
                variant="ghost"
              />
            </UDropdownMenu>
          </template>
        </div>
      </UContainer>
    </div>

    <UContainer v-if="!auth.authenticated.value" class="py-16 sm:py-24">
      <EmhareMarketingHero
        eyebrow="University of Zimbabwe · Admissions"
        description="Create an account or sign in to start, complete, and track an admissions application."
      >
        <template #title>
          University of Zimbabwe
          <span class="text-primary">admissions</span>
        </template>
        <template #actions>
          <UButton
            size="lg"
            label="Create account"
            icon="i-lucide-user-plus"
            color="primary"
            @click="auth.signup('/')"
          />
          <UButton
            size="lg"
            label="Sign in"
            icon="i-lucide-log-in"
            color="neutral"
            variant="outline"
            @click="auth.login('/')"
          />
        </template>
      </EmhareMarketingHero>

      <div class="mt-20 border-t border-muted pt-14">
        <p class="text-xs font-semibold tracking-wide text-muted uppercase">
          Application process
        </p>
        <div class="mt-6">
          <EmhareStepList
            :steps="[
              {
                label: 'Create your account',
                description:
                  'Register or sign in before starting an application.',
              },
              {
                label: 'Choose your programmes',
                description:
                  'Select an open admission route and rank Programme choices.',
              },
              {
                label: 'Upload your documents',
                description:
                  'Provide required evidence and complete the application fee where applicable.',
              },
              {
                label: 'Track your decision',
                description:
                  'Monitor review status and respond to an admission offer.',
              },
            ]"
          />
        </div>
      </div>

      <div class="mt-20 grid gap-8 border-t border-muted pt-14 sm:grid-cols-3">
        <div>
          <UIcon name="i-lucide-shield-check" class="size-5 text-primary" />
          <p class="mt-3 text-sm font-semibold text-highlighted">
            Account access
          </p>
          <p class="mt-1 text-sm text-muted">
            Sign in to manage your applications, documents, and offer responses.
          </p>
        </div>
        <div>
          <UIcon name="i-lucide-file-check-2" class="size-5 text-primary" />
          <p class="mt-3 text-sm font-semibold text-highlighted">
            Application status
          </p>
          <p class="mt-1 text-sm text-muted">
            Review document, payment, and submission requirements for each application.
          </p>
        </div>
        <div>
          <UIcon name="i-lucide-badge-check" class="size-5 text-primary" />
          <p class="mt-3 text-sm font-semibold text-highlighted">
            Offer responses
          </p>
          <p class="mt-1 text-sm text-muted">
            Accept or decline issued admission offers through the portal.
          </p>
        </div>
      </div>
    </UContainer>

    <UContainer v-else class="py-8 sm:py-10">
      <div
        class="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between"
      >
        <div>
          <p class="text-xs font-semibold tracking-wide text-primary uppercase">
            Welcome back
          </p>
          <h1
            class="mt-1 text-2xl font-bold tracking-tight text-highlighted sm:text-3xl"
          >
            {{ auth.displayName.value }}
          </h1>
          <p class="mt-2 max-w-2xl text-sm text-muted">
            Start an application for an open intake, clear any configured
            fee, and submit it for review.
          </p>
        </div>
        <UButton
          icon="i-lucide-plus"
          label="Start application"
          color="primary"
          size="lg"
          to="/applications/new"
        />
      </div>

      <UAlert
        v-if="loadError"
        class="mb-4"
        color="error"
        variant="soft"
        icon="i-lucide-circle-alert"
        title="Applications unavailable"
        :description="loadError"
      />

      <section
        v-if="offers.length"
        class="mb-6 space-y-3"
        aria-labelledby="admission-offers-heading"
      >
        <div class="flex items-end justify-between gap-3">
          <div>
            <p class="text-xs font-medium uppercase tracking-wide text-primary">
              Decision centre
            </p>
            <h2
              id="admission-offers-heading"
              class="mt-1 text-xl font-semibold text-highlighted"
            >
              Admission offers
            </h2>
          </div>
          <span class="text-xs text-muted">Responses are permanent</span>
        </div>

        <EmharePaginatedCollection
          v-slot="{ items: paginatedOffers }"
          :items="offers"
        >
          <div class="space-y-3">
            <UCard
              v-for="offer in paginatedOffers"
              :key="offer.id"
              :data-testid="`admission-offer-${offer.id}`"
              variant="outline"
            >
              <template #header>
                <div
                  class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"
                >
                  <div>
                    <p class="font-mono text-xs text-muted">
                      {{ offer.offerNumber }}
                    </p>
                    <h3 class="mt-1 text-lg font-semibold text-highlighted">
                      {{ offer.programmeCode }} · {{ offer.programmeName }}
                    </h3>
                  </div>
                  <EmhareStatusPill
                    :label="formatStatus(offer.status)"
                    :tone="offerStatusTone(offer.status)"
                  />
                </div>
              </template>

              <div
                class="grid gap-4 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-end"
              >
                <div class="space-y-3">
                  <dl class="grid gap-3 text-sm sm:grid-cols-3">
                    <div>
                      <dt class="text-xs text-muted">Offer type</dt>
                      <dd class="mt-1 font-medium text-highlighted">
                        {{ formatStatus(offer.offerType) }}
                      </dd>
                    </div>
                    <div>
                      <dt class="text-xs text-muted">Respond by</dt>
                      <dd class="mt-1 font-medium text-highlighted">
                        {{ formatDate(offer.acceptanceDeadline) }}
                      </dd>
                    </div>
                    <div>
                      <dt class="text-xs text-muted">Commencement</dt>
                      <dd class="mt-1 font-medium text-highlighted">
                        {{ formatDate(offer.commencementDate) }}
                      </dd>
                    </div>
                  </dl>

                  <UAlert
                    v-if="offer.offerType === 'CONDITIONAL'"
                    color="warning"
                    variant="soft"
                    icon="i-lucide-list-checks"
                    title="Conditional offer"
                    :description="
                      offer.conditionsText ||
                      'Review the listed conditions before responding.'
                    "
                  />
                  <EmharePaginatedCollection
                    v-if="offer.conditions.length"
                    :items="offer.conditions"
                    :initial-page-size="5"
                    v-slot="{ items: paginatedConditions }"
                  ><ul class="space-y-2 rounded-lg border border-muted bg-elevated/40 p-3 text-sm">
                    <li
                      v-for="condition in paginatedConditions"
                      :key="condition.code"
                      class="flex items-start justify-between gap-3"
                    >
                      <span>{{ condition.description }}</span>
                      <EmhareStatusPill
                        :label="formatStatus(condition.status)"
                        :tone="
                          condition.status === 'PENDING' ? 'warning' : 'success'
                        "
                      />
                    </li>
                  </ul></EmharePaginatedCollection>
                  <p v-if="offer.response" class="text-sm text-muted">
                    Response recorded
                    {{ formatDate(offer.response.respondedAt) }}. This record
                    cannot be changed.
                  </p>
                  <UAlert
                    v-if="offer.convertedStudentNumber"
                    color="success"
                    variant="soft"
                    icon="i-lucide-graduation-cap"
                    title="Student registration complete"
                    :description="`Your student number is ${offer.convertedStudentNumber}. Your student record and portal access are active.`"
                  />
                  <UAlert
                    v-else-if="offer.conversionRequestedAt"
                    color="info"
                    variant="soft"
                    icon="i-lucide-loader-circle"
                    title="Student registration in progress"
                    description="Your accepted offer is being provisioned. Access is activated after Finance and Student Records complete their checks."
                  />
                </div>

                <div
                  v-if="offer.currentPublicationId || offer.status === 'SENT'"
                  class="space-y-3"
                >
                  <div
                    v-if="offer.currentPublicationId"
                    class="flex flex-wrap justify-end gap-2"
                  >
                    <UButton
                      label="Preview"
                      icon="i-lucide-eye"
                      color="neutral"
                      variant="outline"
                      :loading="openingOfferId === offer.id"
                      @click="openOfferLetter(offer, 'inline')"
                    />
                    <UButton
                      label="Download"
                      icon="i-lucide-download"
                      color="neutral"
                      variant="outline"
                      :loading="openingOfferId === offer.id"
                      @click="openOfferLetter(offer, 'attachment')"
                    />
                    <UButton
                      v-if="offer.status === 'SENT' && !offer.amendmentPending"
                      label="Decline"
                      color="error"
                      variant="outline"
                      :loading="respondingOfferId === offer.id"
                      @click="declineOffer(offer)"
                    />
                    <UButton
                      v-if="offer.status === 'SENT' && !offer.amendmentPending"
                      label="Accept offer"
                      icon="i-lucide-badge-check"
                      color="primary"
                      :loading="respondingOfferId === offer.id"
                      @click="acceptOffer(offer)"
                    />
                  </div>
                  <UAlert
                    v-if="offer.status === 'SENT' && !offer.currentPublicationId"
                    color="info"
                    variant="soft"
                    icon="i-lucide-file-clock"
                    title="Offer letter being prepared"
                    description="Admissions has not published the current offer letter yet. Preview, download and response actions will appear after publication."
                  />
                  <UAlert
                    v-else-if="offer.status === 'SENT' && offer.amendmentPending"
                    color="warning"
                    variant="soft"
                    title="Updated letter pending"
                    description="Admissions is preparing a replacement letter. Accept and decline will return after it is published."
                  />
                </div>
              </div>
            </UCard>
          </div>
        </EmharePaginatedCollection>
      </section>

      <div
        v-if="loadingApplications && !applications.length"
        class="space-y-3"
        aria-label="Loading applications"
      >
        <USkeleton v-for="index in 3" :key="index" class="h-36 w-full" />
      </div>

      <UEmpty
        v-else-if="!applications.length"
        variant="subtle"
        icon="i-lucide-file-plus-2"
        title="No applications yet"
        description="Select an open application type and intake to create a draft."
      >
        <template #actions>
          <UButton
            icon="i-lucide-plus"
            label="Start application"
            color="primary"
            to="/applications/new"
          />
        </template>
      </UEmpty>

      <section v-else>
        <div class="mb-3 flex items-end justify-between gap-3">
          <div>
            <p class="text-xs font-medium uppercase tracking-wide text-primary">
              Application register
            </p>
            <h2 class="mt-1 text-xl font-semibold text-highlighted">
              My applications
            </h2>
          </div>
          <span class="text-xs text-muted">
            {{ applications.length }} record{{ applications.length === 1 ? "" : "s" }}
          </span>
        </div>

        <div class="space-y-3">
          <UCard
            v-for="application in applications"
            :key="application.id"
            variant="outline"
          >
            <template #header>
              <div
                class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between"
              >
                <div>
                  <p class="font-mono text-xs text-muted">
                    {{ application.applicantNumber }} · {{ formatStatus(application.status) }}
                  </p>
                  <h3 class="mt-1 text-lg font-semibold text-highlighted">
                    {{ application.applicationNumber }}
                  </h3>
                  <p class="mt-1 text-sm text-muted">
                    {{ application.applicationTypeName }}
                  </p>
                </div>
                <div class="flex flex-wrap gap-2 lg:justify-end">
                  <EmhareStatusPill
                    :label="formatStatus(application.status)"
                    :tone="applicationStatusTone(application.status)"
                  />
                  <EmhareStatusPill
                    :label="
                      paymentStatusLabel(application.paymentClearanceStatus)
                    "
                    :tone="
                      paymentStatusTone(application.paymentClearanceStatus)
                    "
                    icon="i-lucide-receipt-text"
                  />
                  <EmhareStatusPill
                    :label="applicationDocumentStatus(application).label"
                    :tone="applicationDocumentStatus(application).tone"
                    :icon="applicationDocumentStatus(application).icon"
                  />
                </div>
              </div>
            </template>

            <div
              class="grid gap-6 lg:grid-cols-[minmax(0,1fr)_18rem] lg:items-start"
            >
              <div class="space-y-4">
                <div v-if="application.programmeChoices.length" class="space-y-2">
                  <p class="text-xs font-medium uppercase tracking-wide text-muted">
                    Programme choices
                  </p>
                  <ol class="space-y-2">
                    <li
                      v-for="choice in application.programmeChoices"
                      :key="choice.id"
                      class="flex gap-3"
                    >
                      <span
                        class="mt-0.5 inline-flex size-6 shrink-0 items-center justify-center rounded-full bg-primary/10 text-xs font-semibold text-primary"
                      >
                        {{ choice.choiceRank }}
                      </span>
                      <div class="min-w-0">
                        <p class="font-medium text-highlighted">
                          {{ choice.programmeCode }} · {{ choice.programmeName }}
                        </p>
                        <p class="mt-1 text-xs text-muted">
                          {{ choice.owningAcademicUnitName }} · Curriculum
                          {{ choice.programmeVersionCode }}
                        </p>
                      </div>
                    </li>
                  </ol>
                </div>
                <UAlert
                  v-else
                  color="warning"
                  variant="soft"
                  icon="i-lucide-list-plus"
                  title="Programme choices required"
                  description="Add at least one Programme choice before submission."
                />

                <div class="grid gap-3 text-sm sm:grid-cols-2 xl:grid-cols-4">
                  <div
                    v-for="step in applicationJourneySteps(application)"
                    :key="step.label"
                    class="flex items-center gap-2"
                  >
                    <UIcon
                      :name="step.done ? 'i-lucide-circle-check' : 'i-lucide-circle'"
                      :class="step.done ? 'text-primary' : 'text-muted'"
                    />
                    <span
                      :class="step.done ? 'font-medium text-highlighted' : 'text-muted'"
                    >
                      {{ step.label }}
                    </span>
                  </div>
                </div>

                <div
                  v-if="application.payment"
                  class="grid gap-3 text-sm sm:grid-cols-3"
                >
                  <div>
                    <p class="text-xs text-muted">Payment reference</p>
                    <p class="mt-1 font-mono font-medium text-highlighted">
                      {{ application.payment.reference }}
                    </p>
                  </div>
                  <div>
                    <p class="text-xs text-muted">Amount due</p>
                    <p class="mt-1 font-medium text-highlighted">
                      {{
                        formatMoney(
                          application.payment.amountDue,
                          application.payment.currencyCode,
                        )
                      }}
                    </p>
                  </div>
                  <div>
                    <p class="text-xs text-muted">Base amount</p>
                    <p class="mt-1 font-medium text-highlighted">
                      {{
                        application.payment.baseAmountDue === null
                          ? "Awaiting effective rate"
                          : formatMoney(
                              application.payment.baseAmountDue,
                              application.payment.baseCurrencyCode,
                            )
                      }}
                    </p>
                  </div>
                </div>

                <UAlert
                  v-if="application.paymentClearanceStatus === 'PENDING'"
                  color="warning"
                  variant="soft"
                  icon="i-lucide-clock-3"
                  title="Payment confirmation required"
                  description="Use the payment reference above. Finance confirmation will unlock submission automatically."
                />
                <UAlert
                  v-else-if="application.paymentClearanceStatus === 'UNRATED'"
                  color="warning"
                  variant="soft"
                  icon="i-lucide-scale"
                  title="Exchange rate pending"
                  description="Finance received the transaction, but submission remains locked until an effective USD exchange rate is available."
                />
                <UAlert
                  v-else-if="application.paymentClearanceStatus === 'WAIVED'"
                  color="success"
                  variant="soft"
                  icon="i-lucide-badge-check"
                  title="Application fee waived"
                  :description="
                    application.paymentWaiverReason ||
                    'An authorised finance officer waived this fee.'
                  "
                />
                <UAlert
                  v-if="documentRegisterErrors[application.id]"
                  color="error"
                  variant="soft"
                  title="Document requirements unavailable"
                  :description="documentRegisterErrors[application.id]"
                />
                <UAlert
                  v-if="applicationSubmissionGuidance(application).length"
                  color="warning"
                  variant="soft"
                  icon="i-lucide-list-checks"
                  title="Before submission"
                >
                  <template #description>
                    <ul class="mt-1 list-disc space-y-1 pl-4">
                      <li
                        v-for="instruction in applicationSubmissionGuidance(application)"
                        :key="instruction"
                      >
                        {{ instruction }}
                      </li>
                    </ul>
                  </template>
                </UAlert>
              </div>

              <aside class="space-y-3">
                <div class="rounded-md bg-elevated/50 p-3 text-sm">
                  <p class="text-xs font-medium uppercase tracking-wide text-muted">
                    Current stage
                  </p>
                  <p class="mt-1 font-semibold text-highlighted">
                    {{ formatStatus(application.status) }}
                  </p>
                  <p class="mt-2 text-muted">
                    Documents:
                    <span class="font-medium text-highlighted">
                      {{ applicationDocumentStatus(application).label }}
                    </span>
                  </p>
                  <p class="mt-1 text-muted">
                    Fee:
                    <span class="font-medium text-highlighted">
                      {{ paymentStatusLabel(application.paymentClearanceStatus) }}
                    </span>
                  </p>
                </div>
                <UButton
                  block
                  :label="application.status === 'DRAFT' ? 'Continue application' : 'View application'"
                  icon="i-lucide-arrow-right"
                  color="primary"
                  @click="navigateTo(`/applications/${application.id}`)"
                />
              </aside>
            </div>
          </UCard>
        </div>
      </section>
    </UContainer>

    <EmhareRecordDrawer
      v-model:open="documentDrawerOpen"
      :title="
        selectedDocumentApplication
          ? `Application documents · ${selectedDocumentApplication.applicationNumber}`
          : 'Application documents'
      "
      description="Upload each required item as private evidence. Rejected evidence is replaced without erasing the original audit trail."
      submit-label="Upload document"
      submit-icon="i-lucide-upload"
      :busy="uploadingDocument"
      :submit-disabled="documentUploadDisabled"
      width="lg"
      @submit="uploadApplicationDocument"
      @close="closeDocumentDrawer"
    >
      <template #body>
        <div class="space-y-4">
          <UAlert
            v-if="
              selectedDocumentApplication &&
              documentRegisterErrors[selectedDocumentApplication.id]
            "
            color="error"
            variant="soft"
            title="Document requirements unavailable"
            :description="
              documentRegisterErrors[selectedDocumentApplication.id]
            "
          />
          <div
            v-if="selectedDocumentRegister"
            class="grid gap-3 sm:grid-cols-3"
          >
            <EmhareKpiCard
              label="Missing"
              :value="selectedDocumentRegister.missingRequirementCodes.length"
              icon="i-lucide-file-question"
              tone="error"
            />
            <EmhareKpiCard
              label="Pending"
              :value="selectedDocumentRegister.pendingRequirementCodes.length"
              icon="i-lucide-clock-3"
              tone="warning"
            />
            <EmhareKpiCard
              label="Verified"
              :value="
                selectedDocumentRegister.requirements.filter(
                  (requirement) => requirement.state === 'VERIFIED',
                ).length
              "
              icon="i-lucide-shield-check"
              tone="success"
            />
          </div>

          <EmharePaginatedCollection
            v-slot="{ items: paginatedRequirements }"
            :items="selectedDocumentRegister?.requirements ?? []"
          >
            <div class="overflow-hidden rounded-md border border-muted">
              <table class="w-full text-left text-sm">
                <thead class="bg-elevated text-muted">
                  <tr>
                    <th class="p-3 font-medium">Requirement</th>
                    <th class="p-3 font-medium">Status</th>
                    <th class="p-3 text-right font-medium">Action</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-if="
                      loadingDocumentApplicationId ===
                      selectedDocumentApplication?.id
                    "
                  >
                    <td colspan="3" class="p-4">
                      <USkeleton class="h-10 w-full" />
                    </td>
                  </tr>
                  <tr
                    v-for="requirement in paginatedRequirements"
                    v-else
                    :key="requirement.requirementCode"
                    class="border-t border-muted"
                  >
                    <td class="p-3">
                      <p class="font-medium text-highlighted">
                        {{ requirement.requirementName }}
                      </p>
                      <p class="mt-1 text-xs text-muted">
                        {{ requirement.required ? "Required" : "Optional"
                        }}<template v-if="requirement.fileName">
                          · {{ requirement.fileName }}</template
                        >
                      </p>
                      <p
                        v-if="requirement.rejectionReason"
                        class="mt-1 text-xs text-error"
                      >
                        {{ requirement.rejectionReason }}
                      </p>
                    </td>
                    <td class="p-3">
                      <EmhareStatusPill
                        :label="formatStatus(requirement.state)"
                        :tone="documentStatusTone(requirement.state)"
                      />
                    </td>
                    <td class="p-3 text-right">
                      <UButton
                        v-if="
                          requirement.state === 'MISSING' ||
                          requirement.state === 'REJECTED'
                        "
                        :label="
                          requirement.state === 'REJECTED'
                            ? 'Replace'
                            : 'Upload'
                        "
                        icon="i-lucide-upload"
                        color="primary"
                        variant="soft"
                        @click="chooseDocumentRequirement(requirement)"
                      />
                      <span v-else class="text-xs text-muted">No action</span>
                    </td>
                  </tr>
                  <tr
                    v-if="
                      !loadingDocumentApplicationId &&
                      !selectedDocumentRegister?.requirements.length
                    "
                  >
                    <td colspan="3" class="p-8 text-center text-muted">
                      This application type has no document requirements.
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </EmharePaginatedCollection>

          <template v-if="uploadableDocumentRequirements.length">
            <USeparator label="Upload evidence" />
            <EmhareFormField
              v-model="documentUploadForm.requirementCode"
              type="searchable-select"
              name="documentRequirementCode"
              label="Document requirement"
              :items="uploadableDocumentRequirements"
              placeholder="Select missing or rejected evidence"
              required
            />
            <UAlert
              v-if="selectedUploadRequirement?.state === 'REJECTED'"
              color="warning"
              variant="soft"
              icon="i-lucide-file-warning"
              title="Replacement required"
              :description="
                selectedUploadRequirement.rejectionReason ??
                'Upload corrected evidence for the recorded requirement.'
              "
            />
            <EmhareFormField
              v-model="documentUploadForm.file"
              type="drop-file"
              name="applicationDocumentFile"
              label="Document file"
              description="PDF, JPEG, or PNG. Maximum 10 MB. The file signature and checksum are verified at intake."
              required
            />
          </template>
          <UAlert
            v-else-if="selectedDocumentRegister?.requirements.length"
            :color="
              selectedDocumentRegister.requiredDocumentsVerified
                ? 'success'
                : 'warning'
            "
            variant="soft"
            :icon="
              selectedDocumentRegister.requiredDocumentsVerified
                ? 'i-lucide-shield-check'
                : 'i-lucide-clock-3'
            "
            :title="
              selectedDocumentRegister.requiredDocumentsVerified
                ? 'All required documents are verified'
                : 'Verification in progress'
            "
            :description="
              selectedDocumentRegister.requiredDocumentsVerified
                ? 'No further evidence action is required.'
                : 'Admissions will review the uploaded evidence. You will be notified if a replacement is required.'
            "
          />
        </div>
      </template>
    </EmhareRecordDrawer>
  </div>
</template>
