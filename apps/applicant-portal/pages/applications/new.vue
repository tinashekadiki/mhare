<script setup lang="ts">
import type {
  AdmissionsApplicationSummary,
  ApplicationSectionOption,
  ApplicationStartOptions,
  ApplicationTypeOption,
} from "@emhare/portal-shell/types/admissions";

definePageMeta({ public: true });

const auth = useEmhareAuth();
const api = useEmhareApi();
const { showError } = useEmhareConfirm();

const startOptions = ref<ApplicationStartOptions | null>(null);
const loadingOptions = ref(false);
const creatingApplication = ref(false);
const attemptedCreate = ref(false);
const pageError = ref("");
type ApplicationStartStepCode = "APPLICATION_ROUTE";

const activeStartStep = ref<ApplicationStartStepCode>("APPLICATION_ROUTE");

const form = reactive({
  applicantCategoryCode: "LOCAL",
  intakeId: "",
  applicationTypeId: "",
});

const applicantCategoryItems = computed(
  () =>
    startOptions.value?.applicantCategories.map((category) => ({
      label: category.label,
      value: category.code,
    })) ?? [],
);

const availableApplicationTypeIds = computed(
  () =>
    new Set(
      startOptions.value?.routes.map((route) => route.applicationTypeId) ?? [],
    ),
);

const routePresentationByCode: Record<
  string,
  { description: string; icon: string }
> = {
  UNDERGRAD: {
    description:
      "Bachelor’s and Diploma study for school-leavers and equivalent entrants.",
    icon: "i-lucide-school",
  },
  POSTGRAD: {
    description:
      "Advanced study beyond a first degree, outside the specialist routes.",
    icon: "i-lucide-library",
  },
  MBA: {
    description:
      "Management study for applicants bringing professional experience.",
    icon: "i-lucide-briefcase",
  },
  EDUCATION: {
    description:
      "Education programmes across undergraduate and postgraduate levels.",
    icon: "i-lucide-book-open",
  },
};

const applicationRouteCards = computed(
  () =>
    startOptions.value?.applicationTypes
      .filter((applicationType) =>
        availableApplicationTypeIds.value.has(applicationType.id),
      )
      .map((applicationType, index) => {
        const availableRoutes =
          startOptions.value?.routes.filter(
            (route) => route.applicationTypeId === applicationType.id,
          ) ?? [];
        const programmeCount = new Set(
          availableRoutes.flatMap((route) =>
            route.programmes.map((programme) => programme.id),
          ),
        ).size;
        const normalizedCode = applicationType.code.toUpperCase();
        const presentation = routePresentationByCode[normalizedCode] ?? {
          description: "A configured University of Zimbabwe admissions route.",
          icon: "i-lucide-graduation-cap",
        };
        const evidence: string[] = [];
        const sectionCodes = new Set(
          applicationType.sections.map((section) => section.code),
        );
        if (sectionCodes.has("EMPLOYMENT_HISTORY"))
          evidence.push("Employment history");
        if (sectionCodes.has("PRIOR_UZ_STUDY"))
          evidence.push("Previous UZ study");
        if (sectionCodes.has("PROFESSIONAL_ACHIEVEMENTS"))
          evidence.push("Professional achievements");
        const refereeSection = applicationType.sections.find(
          (section) => section.code === "REFEREES" && section.required,
        );
        evidence.push(
          refereeSection
            ? `${refereeSection.minimumRecords} confidential reference${refereeSection.minimumRecords === 1 ? "" : "s"}`
            : "No confidential references",
        );
        return {
          ...applicationType,
          routeNumber: String(index + 1).padStart(2, "0"),
          normalizedCode,
          description: presentation.description,
          icon: presentation.icon,
          intakeCount: availableRoutes.length,
          programmeCount,
          evidence,
        };
      }) ?? [],
);

const intakeItems = computed(() => {
  if (!form.applicationTypeId) return [];
  return (
    startOptions.value?.routes
      .filter((route) => route.applicationTypeId === form.applicationTypeId)
      .map((route) => {
        const intake = startOptions.value?.intakes.find(
          (candidate) => candidate.id === route.intakeId,
        );
        return {
          label: route.intakeName,
          value: route.intakeId,
          description: `${intake ? `Closes ${formatDate(intake.endsOn)} · ` : ""}${route.programmes.length} eligible Programme${route.programmes.length === 1 ? "" : "s"} · up to ${route.maximumProgrammeChoices} choices`,
        };
      }) ?? []
  );
});

const selectedApplicationType = computed<ApplicationTypeOption | null>(
  () =>
    startOptions.value?.applicationTypes.find(
      (applicationType) => applicationType.id === form.applicationTypeId,
    ) ?? null,
);

const selectedApplicationRouteCard = computed(
  () =>
    applicationRouteCards.value.find(
      (applicationType) => applicationType.id === form.applicationTypeId,
    ) ?? null,
);

function selectApplicationType(applicationTypeId: string) {
  form.applicationTypeId = applicationTypeId;
  attemptedCreate.value = false;
}

function defaultCompletionSections(
  applicationType?: Pick<
    ApplicationTypeOption,
    "requiresEmploymentHistory" | "requiresReferees"
  >,
): ApplicationSectionOption[] {
  const sections: ApplicationSectionOption[] = [
    {
      code: "PERSONAL_DETAILS",
      name: "Applicant details",
      required: true,
      repeatable: false,
      minimumRecords: 0,
      sortOrder: 10,
    },
    {
      code: "NEXT_OF_KIN",
      name: "Next of kin",
      required: true,
      repeatable: true,
      minimumRecords: 1,
      sortOrder: 20,
    },
    {
      code: "QUALIFICATIONS",
      name: "Qualifications",
      required: true,
      repeatable: true,
      minimumRecords: 1,
      sortOrder: 30,
    },
  ];
  if (applicationType?.requiresEmploymentHistory) {
    sections.push({
      code: "EMPLOYMENT_HISTORY",
      name: "Employment history",
      required: true,
      repeatable: true,
      minimumRecords: 1,
      sortOrder: 40,
    });
  }
  if (applicationType?.requiresReferees) {
    sections.push({
      code: "REFEREES",
      name: "Referees",
      required: true,
      repeatable: true,
      minimumRecords: 2,
      sortOrder: 50,
    });
  }
  return sections.concat([
    {
      code: "PROGRAMME_CHOICES",
      name: "Programme choices",
      required: true,
      repeatable: true,
      minimumRecords: 1,
      sortOrder: 60,
    },
    {
      code: "DOCUMENTS",
      name: "Supporting documents",
      required: true,
      repeatable: true,
      minimumRecords: 0,
      sortOrder: 70,
    },
    {
      code: "PAYMENT",
      name: "Application fee",
      required: false,
      repeatable: false,
      minimumRecords: 0,
      sortOrder: 80,
    },
    {
      code: "REVIEW_DECLARATION",
      name: "Review and declaration",
      required: true,
      repeatable: false,
      minimumRecords: 0,
      sortOrder: 90,
    },
  ]);
}

const completionSections = computed(() => {
  const applicationType = selectedApplicationType.value;
  const sections = applicationType?.sections?.length
    ? applicationType.sections
    : defaultCompletionSections(applicationType ?? undefined);
  return sections
    .filter(
      (section) =>
        section.code !== "PAYMENT" ||
        selectedApplicationType.value?.fee.required,
    )
    .map((section) => ({
      ...section,
      name:
        section.code === "PERSONAL_DETAILS"
          ? "Applicant details"
          : section.code === "PROGRAMME_CHOICES"
            ? "Programme choices"
            : section.name,
      description:
        section.code === "PAYMENT"
          ? "Payment confirmation"
          : section.required
            ? "Required before submission"
            : "Complete when applicable",
    }));
});

const routeComplete = computed(() =>
  Boolean(
    form.applicantCategoryCode && form.applicationTypeId && form.intakeId,
  ),
);
const canCreate = computed(() => routeComplete.value);
const startStepDefinitions = computed(() => [
  {
    id: "APPLICATION_ROUTE" as const,
    title: "Application route",
    description: "Application type and intake",
    icon: "i-lucide-map",
    required: true,
    disabled: false,
    complete: routeComplete.value,
  },
]);

const activeStartStepDefinition = computed(
  () =>
    startStepDefinitions.value.find(
      (step) => step.id === activeStartStep.value,
    ) ?? startStepDefinitions.value[0],
);
const activeStartStepTitle = computed(
  () => activeStartStepDefinition.value?.title ?? "Application route",
);
const activeStartStepDescription = computed(
  () =>
    activeStartStepDefinition.value?.description ??
    "Application type and intake",
);

const activeStartStepIndex = computed(() =>
  startStepDefinitions.value.findIndex(
    (step) => step.id === activeStartStep.value,
  ),
);

const applicationJourneySteps = computed(() => [
  ...startStepDefinitions.value.map((step) => ({
    id: step.id,
    title: step.title,
    description: step.description,
    icon: step.icon,
    required: step.required,
    disabled: step.disabled,
    status: step.complete
      ? ("complete" as const)
      : step.id === activeStartStep.value
        ? ("current" as const)
        : ("pending" as const),
  })),
  ...completionSections.value.map((section) => ({
    id: section.code,
    title: section.name,
    description: section.description,
    icon:
      section.code === "PERSONAL_DETAILS"
        ? "i-lucide-contact-round"
        : section.code === "QUALIFICATIONS"
          ? "i-lucide-graduation-cap"
          : section.code === "PROGRAMME_CHOICES"
            ? "i-lucide-list-ordered"
            : section.code === "DOCUMENTS"
              ? "i-lucide-folder-check"
              : section.code === "PAYMENT"
                ? "i-lucide-receipt-text"
                : section.code === "REVIEW_DECLARATION"
                  ? "i-lucide-file-check-2"
                  : "i-lucide-circle-dot",
    required: section.required,
    status: "pending" as const,
    disabled: true,
  })),
]);

const completedStartStepCount = computed(
  () => startStepDefinitions.value.filter((step) => step.complete).length,
);
const applicationProgressPercentage = computed(() => {
  const total = applicationJourneySteps.value.length;
  return total ? Math.round((completedStartStepCount.value / total) * 100) : 0;
});

const startStepValidationMessage = computed(() => {
  if (activeStartStep.value === "APPLICATION_ROUTE" && !routeComplete.value)
    return "Choose an applicant category, an open application type, and an intake.";
  return "";
});

onMounted(async () => {
  await auth.loadUser();
  if (!auth.authenticated.value) return;
  await auth.syncCoreUser();
  await loadStartOptions();
});

watch(
  () => form.applicantCategoryCode,
  async (categoryCode, previousCategoryCode) => {
    if (!auth.authenticated.value || categoryCode === previousCategoryCode)
      return;
    Object.assign(form, { applicationTypeId: "", intakeId: "" });
    activeStartStep.value = "APPLICATION_ROUTE";
    await loadStartOptions();
  },
);

watch(
  () => form.applicationTypeId,
  () => {
    const currentIntakeIsAvailable = intakeItems.value.some(
      (item) => item.value === form.intakeId,
    );
    if (!currentIntakeIsAvailable) form.intakeId = "";
  },
);

function isStartStep(stepId: string): stepId is ApplicationStartStepCode {
  return stepId === "APPLICATION_ROUTE";
}

function selectApplicationStep(stepId: string) {
  if (!isStartStep(stepId)) return;
  const step = startStepDefinitions.value.find((item) => item.id === stepId);
  if (step?.disabled) return;
  activeStartStep.value = stepId;
  attemptedCreate.value = false;
}

async function continueStartJourney() {
  attemptedCreate.value = true;
  if (startStepValidationMessage.value) return;
  await createApplication();
}

async function loadStartOptions() {
  if (loadingOptions.value) return;
  loadingOptions.value = true;
  pageError.value = "";
  try {
    startOptions.value = await api.request<ApplicationStartOptions>(
      `/api/admissions/applications/start-options?applicantCategoryCode=${encodeURIComponent(form.applicantCategoryCode)}`,
    );
  } catch (error) {
    pageError.value = api.errorMessage(
      error,
      "Application routes could not be loaded.",
    );
  } finally {
    loadingOptions.value = false;
  }
}

async function createApplication() {
  attemptedCreate.value = true;
  if (!canCreate.value) {
    return;
  }

  creatingApplication.value = true;
  try {
    const application = await api.request<AdmissionsApplicationSummary>(
      "/api/admissions/applications",
      {
        method: "POST",
        body: {
          applicantCategoryCode: form.applicantCategoryCode,
          intakeId: form.intakeId,
          applicationTypeId: form.applicationTypeId,
          programmeIds: [],
        },
      },
    );
    await navigateTo(`/applications/${application.id}`);
  } catch (error) {
    await showError(
      "Application could not be started",
      api.errorMessage(error),
    );
  } finally {
    creatingApplication.value = false;
  }
}

function formatMoney(amount: number | null, currencyCode: string | null) {
  if (amount === null || !currencyCode) return "No fee";
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
  <div class="application-route-page min-h-screen bg-slate-50 text-slate-900">
    <EmhareTopNav
      :breadcrumbs="[
        { label: 'Applications', to: '/' },
        { label: 'New application' },
      ]"
    >
      <template #actions>
        <UButton
          label="Return to applications"
          icon="i-lucide-arrow-left"
          color="neutral"
          variant="ghost"
          to="/"
        />
      </template>
    </EmhareTopNav>

    <main class="mx-auto max-w-[80rem] px-4 py-8 sm:px-6 sm:py-10">
      <section
        v-if="!auth.authenticated.value"
        class="mx-auto max-w-xl py-16 text-center"
      >
        <p class="text-xs font-bold uppercase tracking-[0.2em] text-uzgold-700">
          Application portal
        </p>
        <h1 class="mt-4 text-3xl font-semibold tracking-tight text-uzgreen-950">
          Sign in to start an application.
        </h1>
        <p class="mx-auto mt-4 max-w-md text-base leading-7 text-slate-600">
          An account is required to save and submit an application.
        </p>
        <UButton
          class="mt-8"
          label="Sign in to continue"
          icon="i-lucide-log-in"
          color="primary"
          size="xl"
          @click="auth.login('/applications/new')"
        />
      </section>

      <div
        v-else
        class="grid gap-6 lg:grid-cols-[16rem_minmax(0,1fr)] lg:items-start lg:gap-8"
      >
        <aside class="lg:sticky lg:top-20 lg:self-start">
          <div class="mb-4 rounded-xl border border-slate-200 bg-white p-4">
            <div class="flex items-center justify-between text-sm">
              <span class="font-semibold text-slate-700">Progress</span>
              <span class="font-semibold text-uzgreen-700"
                >{{ applicationProgressPercentage }}%</span
              >
            </div>
            <UProgress
              class="mt-2"
              :model-value="applicationProgressPercentage"
              color="primary"
              size="sm"
            />
            <p class="mt-2 text-xs text-slate-500">
              {{ completedStartStepCount }} of
              {{ applicationJourneySteps.length }} steps complete
            </p>
          </div>
          <EmhareVerticalStepper
            :steps="applicationJourneySteps"
            :current-step="activeStartStep"
            label="Application process"
            @update:current-step="selectApplicationStep"
          />
        </aside>

        <div class="min-w-0 space-y-6">
          <UAlert
            v-if="pageError"
            color="error"
            variant="soft"
            icon="i-lucide-circle-alert"
            title="Application routes are unavailable"
            :description="pageError"
          />

          <section
            class="route-prospectus relative overflow-hidden rounded-3xl bg-uzgreen-950 px-6 py-8 text-white shadow-xl shadow-uzgreen-950/10 sm:px-9 sm:py-10"
          >
            <div class="route-prospectus__seal" aria-hidden="true">UZ</div>
            <div class="relative max-w-2xl">
              <p
                class="text-xs font-bold tracking-[0.24em] text-uzgold-300 uppercase"
              >
                University admissions · {{ new Date().getFullYear() }}
              </p>
              <h1
                class="mt-4 max-w-xl font-serif text-3xl leading-tight font-semibold text-white sm:text-4xl"
              >
                Build your application on the right route.
              </h1>
              <p
                class="mt-4 max-w-xl text-sm leading-6 text-uzgreen-100 sm:text-base"
              >
                Compare the evidence, open intakes and Programme catalogue
                before creating your draft. Your selection determines the rest
                of the application.
              </p>
            </div>
          </section>

          <section
            id="application-step-editor"
            class="min-w-0 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm"
          >
            <div class="border-b border-slate-100 px-6 py-5 sm:px-8">
              <p
                class="text-xs font-bold tracking-[0.2em] text-uzgreen-700 uppercase"
              >
                Step {{ activeStartStepIndex + 1 }} of
                {{ applicationJourneySteps.length }}
              </p>
              <h2 class="mt-1 text-lg font-semibold text-slate-900">
                {{ activeStartStepTitle }}
              </h2>
              <p class="mt-1 text-sm text-slate-500">
                {{ activeStartStepDescription }}
              </p>
            </div>

            <form
              id="application-start-journey"
              class="p-6 sm:p-8"
              @submit.prevent="continueStartJourney"
            >
              <UAlert
                v-if="attemptedCreate && startStepValidationMessage"
                class="mb-6"
                color="warning"
                variant="soft"
                icon="i-lucide-circle-alert"
                title="Complete required fields"
                :description="startStepValidationMessage"
              />

              <div class="max-w-sm">
                <EmhareFormField
                  v-model="form.applicantCategoryCode"
                  type="select"
                  label="Applicant category"
                  description="This determines the applicable fee policy."
                  :items="applicantCategoryItems"
                  required
                  :disabled="loadingOptions"
                />
              </div>

              <div class="mt-8">
                <p
                  class="text-xs font-bold tracking-[0.2em] text-uzgold-700 uppercase"
                >
                  Route directory
                </p>
                <h2
                  class="mt-2 font-serif text-2xl font-semibold text-uzgreen-950"
                >
                  Choose your application route
                </h2>
                <p class="mt-2 max-w-2xl text-sm leading-6 text-slate-600">
                  Choose by the qualification you intend to pursue. Programme
                  availability is governed by the selected intake.
                </p>

                <div
                  v-if="loadingOptions"
                  class="mt-6 grid gap-4 md:grid-cols-2"
                  aria-label="Loading application routes"
                >
                  <USkeleton
                    v-for="index in 4"
                    :key="index"
                    class="h-56 rounded-2xl"
                  />
                </div>
                <div v-else class="mt-6 grid gap-4 md:grid-cols-2">
                  <button
                    v-for="applicationType in applicationRouteCards"
                    :key="applicationType.id"
                    type="button"
                    :data-testid="`application-route-${applicationType.normalizedCode}`"
                    :aria-pressed="
                      form.applicationTypeId === applicationType.id
                    "
                    class="route-card group relative min-h-56 overflow-hidden rounded-2xl border p-5 text-left transition duration-200 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-uzgreen-700"
                    :class="
                      form.applicationTypeId === applicationType.id
                        ? 'border-uzgreen-700 bg-uzgreen-950 text-white shadow-lg shadow-uzgreen-950/15'
                        : 'border-slate-200 bg-white text-slate-900 hover:-translate-y-0.5 hover:border-uzgreen-300 hover:shadow-lg hover:shadow-uzgreen-950/8'
                    "
                    @click="selectApplicationType(applicationType.id)"
                  >
                    <span class="flex items-start justify-between gap-4">
                      <span
                        class="grid size-11 shrink-0 place-items-center rounded-xl border"
                        :class="
                          form.applicationTypeId === applicationType.id
                            ? 'border-uzgold-400/50 bg-uzgold-400/10 text-uzgold-300'
                            : 'border-uzgreen-100 bg-uzgreen-50 text-uzgreen-800'
                        "
                      >
                        <UIcon :name="applicationType.icon" class="size-5" />
                      </span>
                      <span
                        class="font-serif text-3xl leading-none"
                        :class="
                          form.applicationTypeId === applicationType.id
                            ? 'text-uzgold-300'
                            : 'text-slate-200'
                        "
                        >{{ applicationType.routeNumber }}</span
                      >
                    </span>
                    <span
                      class="mt-5 block text-[0.68rem] font-bold tracking-[0.18em] uppercase"
                      :class="
                        form.applicationTypeId === applicationType.id
                          ? 'text-uzgold-300'
                          : 'text-uzgreen-700'
                      "
                      >{{ applicationType.code }}</span
                    >
                    <span
                      class="mt-1 block text-lg font-semibold"
                      :class="
                        form.applicationTypeId === applicationType.id
                          ? 'text-white'
                          : 'text-uzgreen-950'
                      "
                      >{{ applicationType.name }}</span
                    >
                    <span
                      class="mt-2 block text-sm leading-5"
                      :class="
                        form.applicationTypeId === applicationType.id
                          ? 'text-uzgreen-100'
                          : 'text-slate-600'
                      "
                      >{{ applicationType.description }}</span
                    >
                    <span
                      class="mt-5 flex flex-wrap gap-x-4 gap-y-2 border-t pt-4 text-xs font-medium"
                      :class="
                        form.applicationTypeId === applicationType.id
                          ? 'border-white/15 text-uzgreen-100'
                          : 'border-slate-100 text-slate-500'
                      "
                    >
                      <span
                        >{{ applicationType.intakeCount }} open intake{{
                          applicationType.intakeCount === 1 ? "" : "s"
                        }}</span
                      >
                      <span
                        >{{ applicationType.programmeCount }} Programme{{
                          applicationType.programmeCount === 1 ? "" : "s"
                        }}</span
                      >
                    </span>
                    <UIcon
                      v-if="form.applicationTypeId === applicationType.id"
                      name="i-lucide-circle-check-big"
                      class="absolute right-5 bottom-5 size-5 text-uzgold-300"
                    />
                  </button>
                </div>
              </div>

              <UAlert
                v-if="startOptions && !startOptions.routes.length"
                class="mt-6"
                color="warning"
                variant="soft"
                icon="i-lucide-calendar-x-2"
                title="No application route is currently open"
                description="Admissions must activate a fully configured route whose Programme mappings intersect an open intake."
              >
                <template #actions>
                  <UButton
                    label="Check again"
                    icon="i-lucide-refresh-cw"
                    color="warning"
                    variant="outline"
                    size="sm"
                    :loading="loadingOptions"
                    @click="loadStartOptions"
                  />
                </template>
              </UAlert>

              <div
                v-if="selectedApplicationRouteCard"
                class="mt-8 grid gap-6 rounded-2xl border border-uzgreen-200 bg-uzgreen-50/70 p-5 lg:grid-cols-[minmax(0,1fr)_minmax(16rem,0.8fr)] lg:p-6"
              >
                <div data-testid="selected-route-evidence">
                  <p
                    class="text-xs font-bold tracking-[0.18em] text-uzgreen-700 uppercase"
                  >
                    Prepare before you begin
                  </p>
                  <h3 class="mt-2 text-base font-semibold text-uzgreen-950">
                    Evidence for {{ selectedApplicationRouteCard.name }}
                  </h3>
                  <ul class="mt-4 grid gap-2 sm:grid-cols-2">
                    <li
                      v-for="evidence in selectedApplicationRouteCard.evidence"
                      :key="evidence"
                      class="flex items-center gap-2 text-sm text-slate-700"
                    >
                      <UIcon
                        name="i-lucide-check"
                        class="size-4 shrink-0 text-uzgreen-700"
                      />
                      {{ evidence }}
                    </li>
                  </ul>
                  <p class="mt-4 text-xs leading-5 text-slate-500">
                    Qualification and supporting-document requirements are
                    confirmed inside the draft from the route configuration.
                  </p>
                </div>
                <div
                  class="rounded-xl border border-white bg-white p-4 shadow-sm"
                >
                  <EmhareFormField
                    v-model="form.intakeId"
                    type="select"
                    label="Intake"
                    description="Only intakes offering this route are listed."
                    :items="intakeItems"
                    placeholder="Select an open intake"
                    required
                    :disabled="loadingOptions"
                  />
                  <div class="mt-4 border-t border-slate-100 pt-4">
                    <p
                      class="text-sm font-semibold"
                      :class="
                        selectedApplicationType?.fee.required
                          ? 'text-uzgold-800'
                          : 'text-uzgreen-800'
                      "
                    >
                      {{
                        selectedApplicationType?.fee.required
                          ? "Application fee required"
                          : "No application fee"
                      }}
                    </p>
                    <p class="mt-1 text-xs leading-5 text-slate-500">
                      {{
                        selectedApplicationType?.fee.required
                          ? `${formatMoney(selectedApplicationType.fee.amount, selectedApplicationType.fee.currencyCode)} must be confirmed or waived before Admissions review.`
                          : "This route has an audited fee-free decision."
                      }}
                    </p>
                  </div>
                </div>
              </div>
            </form>

            <footer
              class="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 bg-slate-50/60 px-6 py-4 sm:px-8"
            >
              <span class="text-sm text-slate-500"
                >Fields marked * are required.</span
              >
              <UButton
                label="Create draft"
                icon="i-lucide-file-plus-2"
                color="primary"
                variant="solid"
                size="lg"
                :loading="creatingApplication"
                @click="continueStartJourney"
              />
            </footer>
          </section>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.application-route-page {
  background-image:
    radial-gradient(circle at 8% 14%, rgb(246 189 37 / 8%), transparent 26rem),
    linear-gradient(180deg, rgb(237 248 240 / 55%), transparent 26rem);
}

.route-prospectus::after {
  position: absolute;
  inset: 0;
  background-image: repeating-linear-gradient(
    115deg,
    transparent 0 18px,
    rgb(255 255 255 / 2%) 18px 19px
  );
  content: "";
  pointer-events: none;
}

.route-prospectus__seal {
  position: absolute;
  top: 50%;
  right: clamp(1rem, 6vw, 5rem);
  display: grid;
  width: 8rem;
  height: 8rem;
  translate: 0 -50%;
  place-items: center;
  border: 1px solid rgb(246 189 37 / 25%);
  border-radius: 9999px;
  color: rgb(246 189 37 / 12%);
  font-family: Georgia, "Times New Roman", serif;
  font-size: 3rem;
  font-weight: 700;
  letter-spacing: -0.08em;
}

.route-card[aria-pressed="true"] {
  background: var(--color-uzgreen-950);
}

@media (max-width: 639px) {
  .route-prospectus__seal {
    right: -2.5rem;
    width: 7rem;
    height: 7rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .route-card {
    transition: none;
  }
}
</style>
