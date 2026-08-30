// Author: Tinashe K

import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { computed, defineComponent, onMounted, reactive, ref, watch } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type {
  ApplicationStartOptions,
  ApplicationTypeOption,
} from "@emhare/portal-shell/types/admissions";
import { useApplicationStartJourney } from "../../composables/useApplicationStartJourney";

const request = vi.fn();
const loadUser = vi.fn();
const syncCoreUser = vi.fn();
const navigateTo = vi.fn();
const showError = vi.fn();
const authenticated = ref(true);
let wrapper: VueWrapper | undefined;

function applicationType(overrides: Partial<ApplicationTypeOption> = {}): ApplicationTypeOption {
  return {
    id: "undergrad",
    code: "UNDERGRAD",
    name: "Undergraduate",
    requiresEmploymentHistory: false,
    requiresReferees: false,
    fee: { required: false, policyStatus: "FEE_FREE", amount: null, currencyCode: null },
    sections: [],
    ...overrides,
  };
}

function options(): ApplicationStartOptions {
  return {
    applicantCategoryCode: "LOCAL",
    applicantCategories: [
      { code: "LOCAL", label: "Local" },
      { code: "SADC", label: "SADC" },
    ],
    intakes: [
      {
        id: "intake-1",
        code: "AUG",
        name: "August",
        startsOn: "2026-08-01",
        endsOn: "2026-08-31",
        maximumProgrammeChoices: 3,
        programmes: [],
      },
    ],
    applicationTypes: [applicationType(), applicationType({ id: "closed", code: "CLOSED" })],
    routes: [
      {
        applicationTypeId: "undergrad",
        applicationTypeCode: "UNDERGRAD",
        applicationTypeName: "Undergraduate",
        intakeId: "intake-1",
        intakeCode: "AUG",
        intakeName: "August",
        maximumProgrammeChoices: 3,
        programmes: [],
      },
    ],
  };
}

async function start() {
  let journey!: ReturnType<typeof useApplicationStartJourney>;
  wrapper = mount(
    defineComponent({
      setup() {
        journey = useApplicationStartJourney();
        return () => null;
      },
    }),
  );
  await flushPromises();
  return journey;
}

beforeEach(() => {
  vi.resetAllMocks();
  authenticated.value = true;
  for (const [name, value] of Object.entries({ computed, onMounted, reactive, ref, watch })) {
    vi.stubGlobal(name, value);
  }
  vi.stubGlobal("useEmhareAuth", () => ({ authenticated, loadUser, syncCoreUser }));
  vi.stubGlobal("useEmhareApi", () => ({ request, errorMessage: (error: Error) => error.message }));
  vi.stubGlobal("useEmhareConfirm", () => ({ showError }));
  vi.stubGlobal("navigateTo", navigateTo);
  request.mockResolvedValue(options());
});

afterEach(() => {
  wrapper?.unmount();
  vi.unstubAllGlobals();
});

describe("Authenticated application start journey", () => {
  it("synchronizes identity before loading category-specific routes", async () => {
    const journey = await start();
    expect(loadUser).toHaveBeenCalledOnce();
    expect(syncCoreUser.mock.invocationCallOrder[0]).toBeLessThan(
      request.mock.invocationCallOrder[0]!,
    );
    expect(request).toHaveBeenCalledWith(
      "/api/admissions/applications/start-options?applicantCategoryCode=LOCAL",
    );
    expect(journey.applicantCategoryItems.value).toEqual([
      { label: "Local", value: "LOCAL" },
      { label: "SADC", value: "SADC" },
    ]);
    expect(journey.applicationRouteCards.value.map((route) => route.id)).toEqual(["undergrad"]);
    expect(journey.loadingOptions.value).toBe(false);
  });

  it("does not expose or reload application options for unauthenticated users", async () => {
    authenticated.value = false;
    const journey = await start();
    journey.form.applicantCategoryCode = "SADC";
    await flushPromises();
    expect(syncCoreUser).not.toHaveBeenCalled();
    expect(request).not.toHaveBeenCalled();
    expect(journey.applicationRouteCards.value).toEqual([]);
    expect(journey.applicantCategoryItems.value).toEqual([]);
  });

  it("keeps the route step usable with no loaded options and no selected route", async () => {
    request.mockRejectedValueOnce(new Error("Options unavailable"));
    const journey = await start();
    expect(journey.activeStartStepTitle.value).toBe("Application route");
    expect(journey.activeStartStepDescription.value).toBe("Application type and intake");
    expect(journey.activeStartStepIndex.value).toBe(0);
    expect(journey.selectedApplicationType.value).toBeNull();
    expect(journey.selectedApplicationRouteCard.value).toBeNull();
    expect(journey.intakeItems.value).toEqual([]);
    expect(journey.applicationJourneySteps.value[0]?.status).toBe("current");
    expect(journey.applicationProgressPercentage.value).toBe(0);
    expect(journey.applicationJourneySteps.value.map((section) => section.id)).not.toContain(
      "PAYMENT",
    );
    journey.selectApplicationType("unavailable-route");
    await flushPromises();
    expect(journey.intakeItems.value).toEqual([]);
    expect(journey.selectedApplicationType.value).toBeNull();
    expect(journey.startStepValidationMessage.value).toContain("Choose an applicant category");
  });

  it("clears route and intake when the applicant category changes", async () => {
    const journey = await start();
    journey.selectApplicationType("undergrad");
    await flushPromises();
    journey.form.intakeId = "intake-1";
    journey.form.applicantCategoryCode = "SADC & international";
    await flushPromises();
    expect(journey.form.applicationTypeId).toBe("");
    expect(journey.form.intakeId).toBe("");
    expect(request).toHaveBeenLastCalledWith(
      "/api/admissions/applications/start-options?applicantCategoryCode=SADC%20%26%20international",
    );
  });

  it("preserves a compatible intake but removes one unavailable in the new route", async () => {
    const data = options();
    data.applicationTypes.push(applicationType({ id: "postgrad", code: "POSTGRAD" }));
    data.routes.push({ ...data.routes[0]!, applicationTypeId: "postgrad" });
    request.mockResolvedValue(data);
    const journey = await start();
    journey.form.intakeId = "intake-1";
    journey.selectApplicationType("undergrad");
    await flushPromises();
    journey.selectApplicationType("postgrad");
    await flushPromises();
    expect(journey.form.intakeId).toBe("intake-1");
    journey.selectApplicationType("closed");
    await flushPromises();
    expect(journey.form.intakeId).toBe("");
    expect(journey.intakeItems.value).toEqual([]);
  });

  it("does not create an incomplete route or navigate into future sections", async () => {
    const journey = await start();
    request.mockClear();
    await journey.continueStartJourney();
    expect(journey.attemptedCreate.value).toBe(true);
    expect(journey.startStepValidationMessage.value).toContain("Choose an applicant category");
    journey.selectApplicationStep("PAYMENT");
    expect(journey.activeStartStep.value).toBe("APPLICATION_ROUTE");
    expect(journey.attemptedCreate.value).toBe(true);
    journey.selectApplicationStep("APPLICATION_ROUTE");
    expect(journey.attemptedCreate.value).toBe(false);
    expect(request).not.toHaveBeenCalled();
    expect(navigateTo).not.toHaveBeenCalled();
  });

  it("creates a draft with no premature programme choices then opens that draft", async () => {
    const journey = await start();
    journey.selectApplicationType("undergrad");
    await flushPromises();
    journey.form.intakeId = "intake-1";
    request.mockResolvedValueOnce({ id: "application-1" });
    expect(journey.completedStartStepCount.value).toBe(1);
    expect(journey.applicationProgressPercentage.value).toBeGreaterThan(0);
    expect(journey.applicationJourneySteps.value[0]?.status).toBe("complete");
    await journey.continueStartJourney();
    expect(request).toHaveBeenLastCalledWith("/api/admissions/applications", {
      method: "POST",
      body: {
        applicantCategoryCode: "LOCAL",
        intakeId: "intake-1",
        applicationTypeId: "undergrad",
        programmeIds: [],
      },
    });
    expect(navigateTo).toHaveBeenCalledWith("/applications/application-1");
    expect(journey.creatingApplication.value).toBe(false);
  });

  it("retains the selected route and resets busy state after draft creation fails", async () => {
    const journey = await start();
    journey.selectApplicationType("undergrad");
    await flushPromises();
    journey.form.intakeId = "intake-1";
    request.mockRejectedValueOnce(new Error("No effective fee"));
    await journey.continueStartJourney();
    expect(showError).toHaveBeenCalledWith("Application could not be started", "No effective fee");
    expect(journey.form.intakeId).toBe("intake-1");
    expect(journey.creatingApplication.value).toBe(false);
    expect(navigateTo).not.toHaveBeenCalled();
  });

  it("allows retry after a route-load failure and suppresses overlapping loads", async () => {
    request.mockRejectedValueOnce(new Error("Gateway unavailable"));
    const journey = await start();
    expect(journey.pageError.value).toBe("Gateway unavailable");
    expect(journey.loadingOptions.value).toBe(false);
    let resolveOptions!: (value: ApplicationStartOptions) => void;
    request.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveOptions = resolve;
        }),
    );
    const pending = journey.loadStartOptions();
    await journey.loadStartOptions();
    expect(request).toHaveBeenCalledTimes(2);
    expect(journey.pageError.value).toBe("");
    expect(journey.loadingOptions.value).toBe(true);
    resolveOptions(options());
    await pending;
    expect(journey.loadingOptions.value).toBe(false);
    expect(journey.applicationRouteCards.value).toHaveLength(1);
  });

  it.each([0, 1, 3])(
    "describes governed evidence with %i confidential references",
    async (minimumRecords) => {
      const data = options();
      data.applicationTypes[0] = applicationType({
        code: "mba",
        sections: [
          ...["EMPLOYMENT_HISTORY", "PRIOR_UZ_STUDY", "PROFESSIONAL_ACHIEVEMENTS"].map(
            (code, index) => ({
              code,
              name: code,
              required: true,
              repeatable: true,
              minimumRecords: 1,
              sortOrder: index,
            }),
          ),
          {
            code: "REFEREES",
            name: "Referees",
            required: minimumRecords > 0,
            repeatable: true,
            minimumRecords,
            sortOrder: 50,
          },
        ],
      });
      request.mockResolvedValue(data);
      const journey = await start();
      expect(journey.applicationRouteCards.value[0]).toMatchObject({
        normalizedCode: "MBA",
        icon: "i-lucide-briefcase",
        intakeCount: 1,
        evidence: [
          "Employment history",
          "Previous UZ study",
          "Professional achievements",
          minimumRecords
            ? `${minimumRecords} confidential reference${minimumRecords === 1 ? "" : "s"}`
            : "No confidential references",
        ],
      });
    },
  );

  it("uses safe presentation defaults and deduplicates programmes across intakes", async () => {
    const data = options();
    data.applicationTypes[0] = applicationType({ code: "TRANSFER" });
    const programme = {
      id: "programme-1",
      programmeVersionId: "version-1",
      code: "BIO",
      name: "Biology",
      awardName: "BSc",
      owningAcademicUnitName: "Science",
      programmeVersionCode: "V1",
      programmeTypeCode: null,
      programmeTypeName: null,
      programmeLevelCode: null,
      programmeLevelName: null,
      minimumEntryOptionSelections: 0,
      maximumEntryOptionSelections: 0,
      entryOptions: [],
    };
    data.routes[0]!.programmes = [programme];
    data.routes.push({ ...data.routes[0]!, intakeId: "intake-unlisted", intakeName: "February" });
    request.mockResolvedValue(data);
    const journey = await start();
    journey.selectApplicationType("undergrad");
    expect(journey.applicationRouteCards.value[0]).toMatchObject({
      programmeCount: 1,
      intakeCount: 2,
      icon: "i-lucide-graduation-cap",
    });
    expect(journey.intakeItems.value[0]?.description).toContain("Closes");
    expect(journey.intakeItems.value[1]?.description).toBe(
      "1 eligible Programme · up to 3 choices",
    );
    expect(journey.selectedApplicationRouteCard.value?.code).toBe("TRANSFER");
  });

  it("builds fallback sections from the route flags and omits payment on fee-free routes", async () => {
    const data = options();
    data.applicationTypes[0] = applicationType({
      requiresEmploymentHistory: true,
      requiresReferees: true,
    });
    request.mockResolvedValue(data);
    const journey = await start();
    journey.selectApplicationType("undergrad");
    const steps = journey.applicationJourneySteps.value;
    expect(steps.map((step) => step.id)).toContain("EMPLOYMENT_HISTORY");
    expect(steps.map((step) => step.id)).toContain("REFEREES");
    expect(steps.map((step) => step.id)).not.toContain("PAYMENT");
    expect(steps.slice(1).every((step) => step.disabled)).toBe(true);
    expect(journey.activeStartStepTitle.value).toBe("Application route");
    expect(journey.activeStartStepDescription.value).toBe("Application type and intake");
    expect(journey.activeStartStepIndex.value).toBe(0);
  });

  it("uses snapshotted section requirements including an explicit payment step", async () => {
    const data = options();
    data.applicationTypes[0] = applicationType({
      fee: { required: true, policyStatus: "FEE_STRUCTURE", amount: 20, currencyCode: "USD" },
      sections: [
        {
          code: "PERSONAL_DETAILS",
          name: "Personal",
          required: true,
          repeatable: false,
          minimumRecords: 0,
          sortOrder: 10,
        },
        {
          code: "PROGRAMME_CHOICES",
          name: "Choices",
          required: true,
          repeatable: true,
          minimumRecords: 1,
          sortOrder: 20,
        },
        {
          code: "PAYMENT",
          name: "Application fee",
          required: true,
          repeatable: false,
          minimumRecords: 0,
          sortOrder: 30,
        },
        {
          code: "OTHER",
          name: "Optional evidence",
          required: false,
          repeatable: false,
          minimumRecords: 0,
          sortOrder: 40,
        },
      ],
    });
    request.mockResolvedValue(data);
    const journey = await start();
    journey.selectApplicationType("undergrad");
    expect(journey.applicationJourneySteps.value.slice(1)).toMatchObject([
      { title: "Applicant details", description: "Required before submission" },
      { title: "Programme choices" },
      {
        title: "Application fee",
        description: "Payment confirmation",
        icon: "i-lucide-receipt-text",
      },
      { title: "Optional evidence", description: "Complete when applicable", required: false },
    ]);
  });
});
