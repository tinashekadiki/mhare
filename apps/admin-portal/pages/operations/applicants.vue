<script setup lang="ts">
import type { TableColumn } from "@nuxt/ui";
import type {
  ApplicantRegisterPage,
  ApplicantRegisterRow,
  ApplicantDetails,
  ApplicantProfile,
  AdmissionOfferSummary,
  AdmissionsWorkItemCase,
} from "@emhare/portal-shell/types/admissions";
import type { OfficialDocumentDownload } from "@emhare/portal-shell/types/documents";

definePageMeta({ layout: "dashboard" });

const api = useEmhareApi();
const { showError, showSuccess } = useEmhareConfirm();

const registerPage = ref<ApplicantRegisterPage | null>(null);
const loading = ref(false);
const loadError = ref("");

const search = ref("");
const categoryFilter = ref("ALL");
const statusFilter = ref("ALL");
const page = ref(1);
const pageSize = ref(10);
let searchDebounceHandle: ReturnType<typeof setTimeout> | null = null;

const drawerOpen = ref(false);
const drawerMode = ref<"view" | "edit">("view");
const applicantDetails = ref<ApplicantDetails | null>(null);
const loadingDetails = ref(false);
const detailsError = ref("");
const savingProfile = ref(false);
const openingOfferDocumentId = ref<string | null>(null);
const applicationOffersById = ref<Record<string, AdmissionOfferSummary>>({});

const categoryItems = [
  { label: "All categories", value: "ALL" },
  { label: "Local applicant", value: "LOCAL" },
  { label: "SADC applicant", value: "SADC" },
  { label: "International applicant", value: "INTERNATIONAL" },
  { label: "Continuing legal education applicant", value: "CLE" },
];

const statusItems = [
  { label: "Any application status", value: "ALL" },
  ...[
    "DRAFT",
    "SUBMITTED",
    "PAYMENT_PENDING",
    "UNDER_REVIEW",
    "INCOMPLETE",
    "ELIGIBLE",
    "NOT_ELIGIBLE",
    "SHORTLISTED",
    "SELECTED",
    "OFFERED",
    "ACCEPTED",
    "DECLINED",
    "WITHDRAWN",
    "CONVERTED",
  ].map((status) => ({ label: formatStatus(status), value: status })),
];

const profileCategoryItems = categoryItems.filter((item) => item.value !== "ALL");

const profileForm = reactive({
  applicantCategoryCode: "LOCAL",
  titleCode: "",
  firstName: "",
  middleNames: "",
  lastName: "",
  dateOfBirth: "",
  genderCode: "",
  maritalStatusCode: "",
  nationalIdNumber: "",
  passportNumber: "",
  placeOfBirth: "",
  disabilityStatusCode: "",
  specialNeeds: "",
  sponsorTypeCode: "",
  primaryEmail: "",
  primaryPhone: "",
  postalAddress: "",
  residentialAddress: "",
  changeReason: "",
});

const columns: TableColumn<ApplicantRegisterRow>[] = [
  { accessorKey: "applicantNumber", header: "Applicant" },
  { accessorKey: "applicantCategoryCode", header: "Category" },
  { accessorKey: "primaryEmail", header: "Contact" },
  { accessorKey: "profileCompletenessPercentage", header: "Profile" },
  { accessorKey: "latestApplicationNumber", header: "Latest application" },
  { accessorKey: "updatedAt", header: "Updated" },
  { id: "actions", header: "Actions" },
];

const drawerTitle = computed(() => {
  if (!applicantDetails.value) return "Applicant";
  const title = applicantDetails.value.profile.applicantNumber;
  return drawerMode.value === "view" ? `Applicant · ${title}` : `Edit profile · ${title}`;
});
const drawerDescription = computed(() =>
  drawerMode.value === "view"
    ? "Profile, completeness, and every application submitted by this applicant."
    : "Corrections are recorded with a change reason and cannot overwrite a stale version.",
);
const applicationsWithOffers = computed(
  () =>
    applicantDetails.value?.applications.map((application) => ({
      application,
      offer: offerForApplication(application.id),
    })) ?? [],
);

const saveDisabled = computed(
  () =>
    !profileForm.firstName.trim() ||
    !profileForm.lastName.trim() ||
    !profileForm.primaryEmail.trim() ||
    profileForm.changeReason.trim().length < 10,
);

onMounted(loadApplicants);
watch([categoryFilter, statusFilter, pageSize], () => {
  page.value = 1;
  loadApplicants();
});
watch(page, loadApplicants);
watch(search, () => {
  if (searchDebounceHandle) clearTimeout(searchDebounceHandle);
  searchDebounceHandle = setTimeout(() => {
    page.value = 1;
    loadApplicants();
  }, 350);
});

async function loadApplicants() {
  loading.value = true;
  loadError.value = "";
  try {
    registerPage.value = await api.request<ApplicantRegisterPage>("/api/admissions/applicants", {
      query: {
        search: search.value.trim() || undefined,
        category: categoryFilter.value === "ALL" ? undefined : categoryFilter.value,
        applicationStatus: statusFilter.value === "ALL" ? undefined : statusFilter.value,
        page: page.value - 1,
        size: pageSize.value,
      },
    });
  } catch (error) {
    loadError.value = api.errorMessage(error, "The applicant register could not be loaded.");
  } finally {
    loading.value = false;
  }
}

async function openApplicant(row: ApplicantRegisterRow) {
  drawerMode.value = "view";
  drawerOpen.value = true;
  await loadApplicantDetails(row.id);
}

async function loadApplicantDetails(applicantId: string) {
  loadingDetails.value = true;
  detailsError.value = "";
  try {
    const details = await api.request<ApplicantDetails>(
      `/api/admissions/applicants/${applicantId}`,
    );
    applicantDetails.value = details;
    await loadApplicationOffers(details);
  } catch (error) {
    detailsError.value = api.errorMessage(error, "The applicant profile could not be loaded.");
  } finally {
    loadingDetails.value = false;
  }
}

async function loadApplicationOffers(details: ApplicantDetails) {
  const offerEntries = await Promise.all(
    details.applications.map(async (application) => {
      try {
        const workItem = await api.request<AdmissionsWorkItemCase>(
          `/api/admissions/work-items/${application.id}`,
        );
        return workItem.offer ? ([application.id, workItem.offer] as const) : null;
      } catch {
        return null;
      }
    }),
  );
  applicationOffersById.value = Object.fromEntries(offerEntries.filter((entry) => entry !== null));
}

function beginEdit() {
  if (!applicantDetails.value) return;
  seedProfileForm(applicantDetails.value.profile);
  drawerMode.value = "edit";
}

function seedProfileForm(profile: ApplicantProfile) {
  profileForm.applicantCategoryCode = profile.applicantCategoryCode;
  profileForm.titleCode = profile.titleCode ?? "";
  profileForm.firstName = profile.firstName;
  profileForm.middleNames = profile.middleNames ?? "";
  profileForm.lastName = profile.lastName;
  profileForm.dateOfBirth = profile.dateOfBirth ?? "";
  profileForm.genderCode = profile.genderCode ?? "";
  profileForm.maritalStatusCode = profile.maritalStatusCode ?? "";
  profileForm.nationalIdNumber = profile.nationalIdNumber ?? "";
  profileForm.passportNumber = profile.passportNumber ?? "";
  profileForm.placeOfBirth = profile.placeOfBirth ?? "";
  profileForm.disabilityStatusCode = profile.disabilityStatusCode ?? "";
  profileForm.specialNeeds = profile.specialNeeds ?? "";
  profileForm.sponsorTypeCode = profile.sponsorTypeCode ?? "";
  profileForm.primaryEmail = profile.primaryEmail;
  profileForm.primaryPhone = profile.primaryPhone ?? "";
  profileForm.postalAddress = profile.postalAddress ?? "";
  profileForm.residentialAddress = profile.residentialAddress ?? "";
  profileForm.changeReason = "";
}

function cancelEdit() {
  drawerMode.value = "view";
}

async function saveProfile() {
  const profile = applicantDetails.value?.profile;
  if (!profile || saveDisabled.value) return;
  savingProfile.value = true;
  try {
    const details = await api.request<ApplicantDetails>(
      `/api/admissions/applicants/${profile.id}`,
      {
        method: "PUT",
        body: {
          applicantCategoryCode: profileForm.applicantCategoryCode,
          titleCode: profileForm.titleCode.trim() || null,
          firstName: profileForm.firstName.trim(),
          middleNames: profileForm.middleNames.trim() || null,
          lastName: profileForm.lastName.trim(),
          dateOfBirth: profileForm.dateOfBirth || null,
          genderCode: profileForm.genderCode.trim() || null,
          maritalStatusCode: profileForm.maritalStatusCode.trim() || null,
          nationalIdNumber: profileForm.nationalIdNumber.trim() || null,
          passportNumber: profileForm.passportNumber.trim() || null,
          countryId: profile.countryId,
          nationalityCountryId: profile.nationalityCountryId,
          placeOfBirth: profileForm.placeOfBirth.trim() || null,
          disabilityStatusCode: profileForm.disabilityStatusCode.trim() || null,
          specialNeeds: profileForm.specialNeeds.trim() || null,
          sponsorTypeCode: profileForm.sponsorTypeCode.trim() || null,
          primaryEmail: profileForm.primaryEmail.trim(),
          primaryPhone: profileForm.primaryPhone.trim() || null,
          postalAddress: profileForm.postalAddress.trim() || null,
          residentialAddress: profileForm.residentialAddress.trim() || null,
          changeReason: profileForm.changeReason.trim(),
          expectedVersion: profile.version,
        },
      },
    );
    applicantDetails.value = details;
    await loadApplicationOffers(details);
    drawerMode.value = "view";
    await showSuccess(
      "Profile updated",
      `${applicantDetails.value.profile.applicantNumber} was corrected.`,
    );
    await loadApplicants();
  } catch (error) {
    await showError("Profile could not be saved", api.errorMessage(error));
  } finally {
    savingProfile.value = false;
  }
}

function closeDrawer() {
  drawerOpen.value = false;
  applicantDetails.value = null;
  applicationOffersById.value = {};
  detailsError.value = "";
  drawerMode.value = "view";
}

function categoryLabel(code: string) {
  return categoryItems.find((item) => item.value === code)?.label ?? code;
}

function completenessTone(percentage: number) {
  if (percentage >= 100) return "success" as const;
  if (percentage >= 50) return "warning" as const;
  return "error" as const;
}

function formatStatus(status: string) {
  return status
    .toLowerCase()
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en-ZW", { dateStyle: "medium" }).format(new Date(value));
}

function applicationStatusTone(status: string) {
  if (status === "SUBMITTED" || status === "UNDER_REVIEW") return "info" as const;
  if (status === "OFFERED" || status === "ACCEPTED" || status === "ELIGIBLE")
    return "success" as const;
  if (status === "DECLINED" || status === "WITHDRAWN" || status === "NOT_ELIGIBLE")
    return "error" as const;
  return "neutral" as const;
}

function offerForApplication(applicationId: string) {
  return applicationOffersById.value[applicationId] ?? null;
}

function offerOutcomeLabel(offer: AdmissionOfferSummary) {
  if (offer.response?.response === "ACCEPTED" || ["ACCEPTED", "CONVERTED"].includes(offer.status)) {
    return "Accepted programme";
  }
  if (offer.response?.response === "DECLINED" || offer.status === "DECLINED")
    return "Declined programme";
  return "Offered programme";
}

function isPublishedOfferLetterAvailable(offer: AdmissionOfferSummary) {
  return Boolean(offer.currentPublicationId && offer.generatedDocumentId);
}

async function printOfferLetter(offer: AdmissionOfferSummary) {
  if (!isPublishedOfferLetterAvailable(offer) || !offer.generatedDocumentId) {
    await showError(
      "Offer letter is not available",
      "A current published offer letter is not available for this application.",
    );
    return;
  }

  const documentWindow = window.open("about:blank", "_blank");
  if (!documentWindow) {
    await showError(
      "Offer letter could not be opened",
      "The browser blocked the document tab. Allow pop-ups for eMhare and try again.",
    );
    return;
  }

  documentWindow.opener = null;
  openingOfferDocumentId.value = offer.id;
  try {
    const document = await api.request<OfficialDocumentDownload>(
      `/api/documents/${offer.generatedDocumentId}/download?disposition=inline`,
    );
    documentWindow.location.href = document.downloadUrl;
  } catch (error) {
    documentWindow.close();
    await showError("Offer letter could not be opened", api.errorMessage(error));
  } finally {
    openingOfferDocumentId.value = null;
  }
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Applicants">
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
            @click="loadApplicants"
          />
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar>
        <template #left>
          <UInput
            v-model="search"
            icon="i-lucide-search"
            placeholder="Search name, applicant number, or email"
            class="w-full sm:w-96"
          />
        </template>
        <template #right>
          <USelect v-model="categoryFilter" :items="categoryItems" value-key="value" class="w-56" />
          <USelect v-model="statusFilter" :items="statusItems" value-key="value" class="w-56" />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="space-y-4 p-4 sm:p-6">
        <UAlert
          v-if="loadError"
          color="error"
          variant="soft"
          icon="i-lucide-circle-alert"
          title="Applicant register unavailable"
          :description="loadError"
        />

        <div class="overflow-hidden rounded-lg border border-muted bg-default">
          <EmharePaginatedTable
            :data="registerPage?.content ?? []"
            :columns="columns"
            :loading="loading"
            manual-pagination
            :page="page"
            :page-size="pageSize"
            :total-records="registerPage?.totalElements ?? 0"
            sticky
            @update:page="page = $event"
            @update:page-size="pageSize = $event"
          >
            <template #applicantNumber-cell="{ row }">
              <button type="button" class="text-left" @click="openApplicant(row.original)">
                <p class="font-medium text-highlighted underline-offset-2 hover:underline">
                  {{ row.original.displayName }}
                </p>
                <p class="mt-1 font-mono text-xs text-muted">{{ row.original.applicantNumber }}</p>
              </button>
            </template>

            <template #applicantCategoryCode-cell="{ row }">
              <EmhareStatusPill
                :label="categoryLabel(row.original.applicantCategoryCode)"
                tone="neutral"
              />
            </template>

            <template #primaryEmail-cell="{ row }">
              <div>
                <p class="text-sm text-highlighted">{{ row.original.primaryEmail }}</p>
                <p v-if="row.original.primaryPhone" class="mt-1 text-xs text-muted">
                  {{ row.original.primaryPhone }}
                </p>
              </div>
            </template>

            <template #profileCompletenessPercentage-cell="{ row }">
              <EmhareStatusPill
                :label="`${row.original.profileCompletenessPercentage}% complete`"
                :tone="completenessTone(row.original.profileCompletenessPercentage)"
              />
            </template>

            <template #latestApplicationNumber-cell="{ row }">
              <div v-if="row.original.latestApplicationNumber">
                <p class="text-sm text-highlighted">{{ row.original.latestApplicationNumber }}</p>
                <p class="mt-1 text-xs text-muted">
                  {{ formatStatus(row.original.latestApplicationStatus ?? "") }} ·
                  {{ row.original.latestIntakeCode }}
                </p>
              </div>
              <span v-else class="text-xs text-muted">No applications yet</span>
            </template>

            <template #updatedAt-cell="{ row }">
              <span class="text-sm text-muted">{{ formatDate(row.original.updatedAt) }}</span>
            </template>

            <template #actions-cell="{ row }">
              <div class="flex justify-end">
                <UButton
                  label="View"
                  icon="i-lucide-eye"
                  color="neutral"
                  variant="ghost"
                  @click="openApplicant(row.original)"
                />
              </div>
            </template>

            <template #empty>
              <div class="py-10">
                <UIcon name="i-lucide-users" class="mx-auto size-8 text-muted" />
                <p class="mt-3 font-medium text-highlighted">No applicants match this view</p>
                <p class="mt-1 text-sm text-muted">
                  Adjust the search, category, or application status filter.
                </p>
              </div>
            </template>
          </EmharePaginatedTable>
        </div>

        <EmhareRecordDrawer
          v-model:open="drawerOpen"
          presentation="page"
          :title="drawerTitle"
          :description="drawerDescription"
          submit-label="Save changes"
          submit-icon="i-lucide-save"
          :busy="savingProfile"
          :submit-disabled="saveDisabled"
          width="lg"
          @submit="saveProfile"
          @close="closeDrawer"
        >
          <template #body>
            <div v-if="loadingDetails" class="space-y-3">
              <USkeleton class="h-10 w-full" />
              <USkeleton class="h-32 w-full" />
            </div>

            <UAlert
              v-else-if="detailsError"
              color="error"
              variant="soft"
              title="Profile unavailable"
              :description="detailsError"
            />

            <div v-else-if="drawerMode === 'view' && applicantDetails" class="space-y-5">
              <div class="grid gap-3 sm:grid-cols-3">
                <EmhareKpiCard
                  label="Profile completeness"
                  :value="`${applicantDetails.profile.completenessPercentage}%`"
                  icon="i-lucide-user-round-check"
                  :tone="completenessTone(applicantDetails.profile.completenessPercentage)"
                />
                <EmhareKpiCard
                  label="Applications"
                  :value="applicantDetails.applications.length"
                  icon="i-lucide-files"
                  tone="primary"
                />
                <EmhareKpiCard
                  label="Category"
                  :value="categoryLabel(applicantDetails.profile.applicantCategoryCode)"
                  icon="i-lucide-tag"
                  tone="neutral"
                />
              </div>

              <UAlert
                v-if="applicantDetails.profile.missingRequiredFields.length"
                color="warning"
                variant="soft"
                icon="i-lucide-triangle-alert"
                title="Missing profile fields"
                :description="applicantDetails.profile.missingRequiredFields.join(', ')"
              />

              <div class="grid gap-3 sm:grid-cols-2">
                <div>
                  <p class="text-xs text-muted">Name</p>
                  <p class="text-sm text-highlighted">
                    {{ applicantDetails.profile.firstName }}
                    {{ applicantDetails.profile.middleNames }}
                    {{ applicantDetails.profile.lastName }}
                  </p>
                </div>
                <div>
                  <p class="text-xs text-muted">Applicant number</p>
                  <p class="font-mono text-sm text-highlighted">
                    {{ applicantDetails.profile.applicantNumber }}
                  </p>
                </div>
                <div>
                  <p class="text-xs text-muted">Primary email</p>
                  <p class="text-sm text-highlighted">
                    {{ applicantDetails.profile.primaryEmail }}
                  </p>
                </div>
                <div>
                  <p class="text-xs text-muted">Primary phone</p>
                  <p class="text-sm text-highlighted">
                    {{ applicantDetails.profile.primaryPhone ?? "Not set" }}
                  </p>
                </div>
                <div>
                  <p class="text-xs text-muted">Date of birth</p>
                  <p class="text-sm text-highlighted">
                    {{ applicantDetails.profile.dateOfBirth ?? "Not set" }}
                  </p>
                </div>
                <div>
                  <p class="text-xs text-muted">Gender</p>
                  <p class="text-sm text-highlighted">
                    {{ applicantDetails.profile.genderCode ?? "Not set" }}
                  </p>
                </div>
                <div>
                  <p class="text-xs text-muted">National ID</p>
                  <p class="text-sm text-highlighted">
                    {{ applicantDetails.profile.nationalIdNumber ?? "Not set" }}
                  </p>
                </div>
                <div>
                  <p class="text-xs text-muted">Passport number</p>
                  <p class="text-sm text-highlighted">
                    {{ applicantDetails.profile.passportNumber ?? "Not set" }}
                  </p>
                </div>
                <div>
                  <p class="text-xs text-muted">Residential address</p>
                  <p class="text-sm text-highlighted">
                    {{ applicantDetails.profile.residentialAddress ?? "Not set" }}
                  </p>
                </div>
                <div>
                  <p class="text-xs text-muted">Postal address</p>
                  <p class="text-sm text-highlighted">
                    {{ applicantDetails.profile.postalAddress ?? "Not set" }}
                  </p>
                </div>
              </div>

              <div>
                <p class="mb-2 text-sm font-medium text-highlighted">Applications</p>
                <div
                  v-if="!applicantDetails.applications.length"
                  class="rounded-md border border-muted p-4 text-center text-sm text-muted"
                >
                  No applications submitted yet.
                </div>
                <div v-else class="space-y-2">
                  <div
                    v-for="applicationOutcome in applicationsWithOffers"
                    :key="applicationOutcome.application.id"
                    class="rounded-lg border border-muted bg-default p-4 shadow-sm"
                  >
                    <div class="flex flex-wrap items-start justify-between gap-3">
                      <div>
                        <p class="text-sm font-semibold text-highlighted">
                          {{ applicationOutcome.application.applicationNumber }}
                        </p>
                        <p class="mt-1 text-xs text-muted">
                          {{ applicationOutcome.application.intakeCode }} ·
                          {{ applicationOutcome.application.applicationTypeName }}
                        </p>
                      </div>
                      <div class="flex flex-wrap items-center justify-end gap-2">
                        <EmhareStatusPill
                          :label="formatStatus(applicationOutcome.application.status)"
                          :tone="applicationStatusTone(applicationOutcome.application.status)"
                        />
                        <UButton
                          label="Open application"
                          icon="i-lucide-arrow-up-right"
                          color="neutral"
                          variant="outline"
                          :to="`/operations/admissions/${applicationOutcome.application.id}`"
                        />
                      </div>
                    </div>

                    <div v-if="applicationOutcome.offer" class="mt-4 border-t border-muted pt-4">
                      <div class="flex flex-wrap items-end justify-between gap-4">
                        <div class="min-w-0">
                          <p class="text-xs font-medium uppercase tracking-wide text-muted">
                            {{ offerOutcomeLabel(applicationOutcome.offer) }}
                          </p>
                          <p class="mt-1 font-semibold text-highlighted">
                            {{ applicationOutcome.offer.programmeCode }} ·
                            {{ applicationOutcome.offer.programmeName }}
                          </p>
                          <div class="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted">
                            <span>Offer {{ applicationOutcome.offer.offerNumber }}</span>
                            <span>Status {{ formatStatus(applicationOutcome.offer.status) }}</span>
                            <span v-if="applicationOutcome.offer.response?.response === 'ACCEPTED'">
                              Accepted
                              {{ formatDate(applicationOutcome.offer.response.respondedAt) }}
                            </span>
                            <span v-if="applicationOutcome.offer.convertedStudentNumber">
                              Student {{ applicationOutcome.offer.convertedStudentNumber }}
                            </span>
                          </div>
                        </div>
                        <UButton
                          v-if="isPublishedOfferLetterAvailable(applicationOutcome.offer)"
                          label="Print offer letter"
                          icon="i-lucide-printer"
                          color="primary"
                          variant="soft"
                          :loading="openingOfferDocumentId === applicationOutcome.offer.id"
                          @click="printOfferLetter(applicationOutcome.offer)"
                        />
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div v-else-if="drawerMode === 'edit'" class="grid gap-4 sm:grid-cols-2">
              <EmhareFormField
                v-model="profileForm.applicantCategoryCode"
                type="select"
                name="applicantCategoryCode"
                label="Category"
                :items="profileCategoryItems"
                required
              />
              <EmhareFormField v-model="profileForm.titleCode" name="titleCode" label="Title" />
              <EmhareFormField
                v-model="profileForm.firstName"
                name="firstName"
                label="First name"
                required
              />
              <EmhareFormField
                v-model="profileForm.middleNames"
                name="middleNames"
                label="Middle names"
              />
              <EmhareFormField
                v-model="profileForm.lastName"
                name="lastName"
                label="Last name"
                required
              />
              <UFormField label="Date of birth" name="dateOfBirth">
                <UInput v-model="profileForm.dateOfBirth" type="date" class="w-full" />
              </UFormField>
              <EmhareFormField v-model="profileForm.genderCode" name="genderCode" label="Gender" />
              <EmhareFormField
                v-model="profileForm.maritalStatusCode"
                name="maritalStatusCode"
                label="Marital status"
              />
              <EmhareFormField
                v-model="profileForm.nationalIdNumber"
                name="nationalIdNumber"
                label="National ID number"
              />
              <EmhareFormField
                v-model="profileForm.passportNumber"
                name="passportNumber"
                label="Passport number"
              />
              <EmhareFormField
                v-model="profileForm.placeOfBirth"
                name="placeOfBirth"
                label="Place of birth"
              />
              <EmhareFormField
                v-model="profileForm.disabilityStatusCode"
                name="disabilityStatusCode"
                label="Disability status"
              />
              <EmhareFormField
                v-model="profileForm.sponsorTypeCode"
                name="sponsorTypeCode"
                label="Sponsor type"
              />
              <EmhareFormField
                v-model="profileForm.primaryEmail"
                type="email"
                name="primaryEmail"
                label="Primary email"
                required
              />
              <EmhareFormField
                v-model="profileForm.primaryPhone"
                type="phone"
                name="primaryPhone"
                label="Primary phone"
              />
              <EmhareFormField
                v-model="profileForm.postalAddress"
                type="textarea"
                name="postalAddress"
                label="Postal address"
                class="sm:col-span-2"
              />
              <EmhareFormField
                v-model="profileForm.residentialAddress"
                type="textarea"
                name="residentialAddress"
                label="Residential address"
                class="sm:col-span-2"
              />
              <EmhareFormField
                v-model="profileForm.specialNeeds"
                type="textarea"
                name="specialNeeds"
                label="Special needs"
                class="sm:col-span-2"
              />
              <EmhareFormField
                v-model="profileForm.changeReason"
                type="textarea"
                name="changeReason"
                label="Change reason"
                description="Recorded in the audit trail. At least 10 characters."
                placeholder="State why this profile is being corrected"
                required
                class="sm:col-span-2"
              />
            </div>
          </template>

          <template #footer>
            <div class="flex w-full items-center justify-between gap-3">
              <EmhareGuidedActionButton
                v-if="drawerMode === 'view'"
                label="Edit profile"
                icon="i-lucide-pencil"
                color="primary"
                variant="soft"
                guidance-title="Applicant profile is still loading"
                :guidance-instructions="
                  applicantDetails
                    ? []
                    : ['Wait for the applicant profile to finish loading before editing it.']
                "
                @click="beginEdit"
              />
              <UButton
                v-else
                label="Back to profile"
                icon="i-lucide-arrow-left"
                color="neutral"
                variant="ghost"
                @click="cancelEdit"
              />
              <div class="flex gap-3">
                <UButton
                  label="Close"
                  color="neutral"
                  variant="outline"
                  @click="drawerOpen = false"
                />
                <EmhareGuidedActionButton
                  v-if="drawerMode === 'edit'"
                  label="Save changes"
                  icon="i-lucide-save"
                  color="primary"
                  :loading="savingProfile"
                  guidance-title="Applicant profile details are incomplete"
                  :guidance-instructions="[
                    ...(!profileForm.firstName.trim() ? ['Enter the first name.'] : []),
                    ...(!profileForm.lastName.trim() ? ['Enter the last name.'] : []),
                    ...(!profileForm.primaryEmail.trim()
                      ? ['Enter the primary email address.']
                      : []),
                    ...(profileForm.changeReason.trim().length < 10
                      ? ['Provide at least 10 characters of change evidence.']
                      : []),
                  ]"
                  @click="saveProfile"
                />
              </div>
            </div>
          </template>
        </EmhareRecordDrawer>
      </div>
    </template>
  </UDashboardPanel>
</template>
