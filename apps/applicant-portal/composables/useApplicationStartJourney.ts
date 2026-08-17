// Author: Tinashe K

import type {
  AdmissionsApplicationSummary,
  ApplicationSectionOption,
  ApplicationStartOptions,
  ApplicationTypeOption,
} from "@emhare/portal-shell/types/admissions";

type ApplicationStartStepCode = "APPLICATION_ROUTE";

const routePresentationByCode: Record<string, { description: string; icon: string }> = {
  UNDERGRAD: {
    description: "Bachelor’s and Diploma study for school-leavers and equivalent entrants.",
    icon: "i-lucide-school",
  },
  POSTGRAD: {
    description: "Advanced study beyond a first degree, outside the specialist routes.",
    icon: "i-lucide-library",
  },
  MBA: {
    description: "Management study for applicants bringing professional experience.",
    icon: "i-lucide-briefcase",
  },
  EDUCATION: {
    description: "Education programmes across undergraduate and postgraduate levels.",
    icon: "i-lucide-book-open",
  },
};

/** Owns the authenticated application-start state machine and governed route selection. */
export function useApplicationStartJourney() {
  const auth = useEmhareAuth();
  const api = useEmhareApi();
  const { showError } = useEmhareConfirm();

  const startOptions = ref<ApplicationStartOptions | null>(null);
  const loadingOptions = ref(false);
  const creatingApplication = ref(false);
  const attemptedCreate = ref(false);
  const pageError = ref("");
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
    () => new Set(startOptions.value?.routes.map((route) => route.applicationTypeId) ?? []),
  );
  const applicationRouteCards = computed(
    () =>
      startOptions.value?.applicationTypes
        .filter((applicationType) => availableApplicationTypeIds.value.has(applicationType.id))
        .map((applicationType, index) => {
          const availableRoutes =
            startOptions.value?.routes.filter(
              (route) => route.applicationTypeId === applicationType.id,
            ) ?? [];
          const programmeCount = new Set(
            availableRoutes.flatMap((route) => route.programmes.map((programme) => programme.id)),
          ).size;
          const normalizedCode = applicationType.code.toUpperCase();
          const presentation = routePresentationByCode[normalizedCode] ?? {
            description: "A configured University of Zimbabwe admissions route.",
            icon: "i-lucide-graduation-cap",
          };
          const evidence: string[] = [];
          const sectionCodes = new Set(applicationType.sections.map((section) => section.code));
          if (sectionCodes.has("EMPLOYMENT_HISTORY")) evidence.push("Employment history");
          if (sectionCodes.has("PRIOR_UZ_STUDY")) evidence.push("Previous UZ study");
          if (sectionCodes.has("PROFESSIONAL_ACHIEVEMENTS")) {
            evidence.push("Professional achievements");
          }
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
  const completionSections = computed(() => {
    const applicationType = selectedApplicationType.value;
    const sections = applicationType?.sections?.length
      ? applicationType.sections
      : defaultCompletionSections(applicationType ?? undefined);
    return sections
      .filter((section) => section.code !== "PAYMENT" || applicationType?.fee.required)
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
    Boolean(form.applicantCategoryCode && form.applicationTypeId && form.intakeId),
  );
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
      startStepDefinitions.value.find((step) => step.id === activeStartStep.value) ??
      startStepDefinitions.value[0],
  );
  const activeStartStepTitle = computed(
    () => activeStartStepDefinition.value?.title ?? "Application route",
  );
  const activeStartStepDescription = computed(
    () => activeStartStepDefinition.value?.description ?? "Application type and intake",
  );
  const activeStartStepIndex = computed(() =>
    startStepDefinitions.value.findIndex((step) => step.id === activeStartStep.value),
  );
  const applicationJourneySteps = computed(() => [
    ...startStepDefinitions.value.map((step) => ({
      ...step,
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
      icon: journeySectionIcon(section.code),
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
  const startStepValidationMessage = computed(() =>
    !routeComplete.value
      ? "Choose an applicant category, an open application type, and an intake."
      : "",
  );

  onMounted(async () => {
    await auth.loadUser();
    if (!auth.authenticated.value) return;
    await auth.syncCoreUser();
    await loadStartOptions();
  });
  watch(
    () => form.applicantCategoryCode,
    async (categoryCode, previousCategoryCode) => {
      if (!auth.authenticated.value || categoryCode === previousCategoryCode) return;
      Object.assign(form, { applicationTypeId: "", intakeId: "" });
      activeStartStep.value = "APPLICATION_ROUTE";
      await loadStartOptions();
    },
  );
  watch(
    () => form.applicationTypeId,
    () => {
      const intakeAvailable = intakeItems.value.some((item) => item.value === form.intakeId);
      if (!intakeAvailable) form.intakeId = "";
    },
  );

  function selectApplicationType(applicationTypeId: string) {
    form.applicationTypeId = applicationTypeId;
    attemptedCreate.value = false;
  }

  function selectApplicationStep(stepId: string) {
    if (stepId !== "APPLICATION_ROUTE") return;
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
      pageError.value = api.errorMessage(error, "Application routes could not be loaded.");
    } finally {
      loadingOptions.value = false;
    }
  }

  async function createApplication() {
    attemptedCreate.value = true;
    if (!routeComplete.value) return;
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
      await showError("Application could not be started", api.errorMessage(error));
    } finally {
      creatingApplication.value = false;
    }
  }

  return {
    auth,
    startOptions,
    loadingOptions,
    creatingApplication,
    attemptedCreate,
    pageError,
    activeStartStep,
    form,
    applicantCategoryItems,
    applicationRouteCards,
    intakeItems,
    selectedApplicationType,
    selectedApplicationRouteCard,
    activeStartStepTitle,
    activeStartStepDescription,
    activeStartStepIndex,
    applicationJourneySteps,
    completedStartStepCount,
    applicationProgressPercentage,
    startStepValidationMessage,
    selectApplicationType,
    selectApplicationStep,
    continueStartJourney,
    loadStartOptions,
  };
}

function defaultCompletionSections(
  applicationType?: Pick<ApplicationTypeOption, "requiresEmploymentHistory" | "requiresReferees">,
): ApplicationSectionOption[] {
  const sections: ApplicationSectionOption[] = [
    section("PERSONAL_DETAILS", "Applicant details", 10),
    section("NEXT_OF_KIN", "Next of kin", 20, true, 1),
    section("QUALIFICATIONS", "Qualifications", 30, true, 1),
  ];
  if (applicationType?.requiresEmploymentHistory) {
    sections.push(section("EMPLOYMENT_HISTORY", "Employment history", 40, true, 1));
  }
  if (applicationType?.requiresReferees) {
    sections.push(section("REFEREES", "Referees", 50, true, 2));
  }
  return sections.concat([
    section("PROGRAMME_CHOICES", "Programme choices", 60, true, 1),
    section("DOCUMENTS", "Supporting documents", 70, true),
    section("PAYMENT", "Application fee", 80, false, 0, false),
    section("REVIEW_DECLARATION", "Review and declaration", 90),
  ]);
}

function section(
  code: string,
  name: string,
  sortOrder: number,
  repeatable = false,
  minimumRecords = 0,
  required = true,
): ApplicationSectionOption {
  return { code, name, required, repeatable, minimumRecords, sortOrder };
}

function journeySectionIcon(code: string) {
  return (
    {
      PERSONAL_DETAILS: "i-lucide-contact-round",
      QUALIFICATIONS: "i-lucide-graduation-cap",
      PROGRAMME_CHOICES: "i-lucide-list-ordered",
      DOCUMENTS: "i-lucide-folder-check",
      PAYMENT: "i-lucide-receipt-text",
      REVIEW_DECLARATION: "i-lucide-file-check-2",
    }[code] ?? "i-lucide-circle-dot"
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en-ZW", { dateStyle: "medium" }).format(new Date(value));
}
