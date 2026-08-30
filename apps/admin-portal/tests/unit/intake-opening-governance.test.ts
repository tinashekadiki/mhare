// Author: Tinashe K
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { defineComponent, ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import IntakeOpening from "../../pages/operations/academic-calendar/intakes/[intakeId].vue";
import { campusStubs } from "../../../../tests/unit/support/campus-page";
import {
  clickButton,
  operationalContext,
  setField,
} from "../../../../tests/unit/support/operational-page";

const MultiSelect = defineComponent({
  props: ["modelValue", "items", "disabled"],
  emits: ["update:modelValue"],
  methods: {
    changed(event: Event) {
      this.$emit(
        "update:modelValue",
        Array.from((event.target as HTMLSelectElement).selectedOptions, (option) => option.value),
      );
    },
  },
  template:
    '<select multiple :disabled="disabled" :value="modelValue" @change="changed"><option v-for="item in items" :key="item.value" :value="item.value">{{item.label}}</option></select>',
});
const GuidedAction = defineComponent({
  props: ["label", "guidanceInstructions", "guidanceActionLabel"],
  emits: ["click", "guidance-action"],
  template:
    '<div><p v-for="instruction in guidanceInstructions" :key="instruction">{{instruction}}</p><button @click="$emit(\'click\')">{{label}}</button><button v-if="guidanceActionLabel" @click="$emit(\'guidance-action\')">{{guidanceActionLabel}}</button></div>',
});
const stubs = {
  ...campusStubs,
  USelectMenu: MultiSelect,
  EmhareGuidedActionButton: GuidedAction,
  UDashboardToolbar: defineComponent({
    template: '<nav><slot name="left"/><slot name="right"/></nav>',
  }),
};
let context: ReturnType<typeof operationalContext>;
let wrapper: VueWrapper;
let routeId: string | string[] | undefined;
const overview = ref<any>(null);
const ensureOverview = vi.fn();
const loadOverview = vi.fn();
let applicationTypes: any[];
let feeStructures: any[];
let routeConfigurations: any[];
let quotas: any[];
function existingIntake(overrides = {}) {
  return {
    id: "intake",
    code: "AUG-2026",
    name: "August 2026 intake",
    status: "DRAFT",
    academicYearId: "year",
    startsOn: "2026-08-01",
    endsOn: "2026-08-30",
    offerAcceptanceDeadline: "2026-09-01T21:59:59Z",
    registrationDate: "2026-09-02",
    orientationDate: "2026-09-03",
    commencementDate: "2026-09-04",
    maximumProgrammeChoices: 3,
    programmeLevels: [{ id: "level" }],
    specificProgrammes: [],
    version: 5,
    ...overrides,
  };
}
beforeEach(() => {
  context = operationalContext();
  routeId = "new";
  ensureOverview.mockReset().mockResolvedValue(undefined);
  loadOverview.mockReset().mockResolvedValue(undefined);
  vi.stubGlobal("useRoute", () => ({ params: { intakeId: routeId } }));
  vi.stubGlobal("useAcademicSetup", () => ({ overview, ensureOverview, loadOverview }));
  overview.value = {
    academicYears: [
      {
        id: "year",
        name: "2026 academic year",
        status: "OPEN",
        startDate: "2026-01-01",
        endDate: "2026-12-31",
      },
      { id: "archived", name: "Old academic year", status: "ARCHIVED" },
    ],
    programmeLevels: [
      { id: "level", code: "UG", name: "Undergraduate", status: "ACTIVE" },
      { id: "inactive-level", code: "OLD", name: "Old level", status: "INACTIVE" },
    ],
    programmes: [
      { id: "science", code: "BSC", name: "Science", programmeLevelId: "level", status: "ACTIVE" },
      { id: "arts", code: "BA", name: "Arts", programmeLevelId: "level", status: "ACTIVE" },
      {
        id: "inactive",
        code: "OLD",
        name: "Inactive programme",
        programmeLevelId: "level",
        status: "INACTIVE",
      },
      {
        id: "other-level",
        code: "OTHER",
        name: "Other level programme",
        programmeLevelId: "inactive-level",
        status: "ACTIVE",
      },
    ],
    intakes: [],
  };
  applicationTypes = [
    {
      id: "type",
      code: "UNDERGRAD",
      name: "Undergraduate applications",
      active: false,
      requiresEmploymentHistory: false,
      requiresReferees: true,
      financeFeeStructureId: "fee",
      financeFeeStructureCode: "APP",
      financeFeeStructureName: "Application fee",
      version: 6,
    },
    { id: "ignored", code: "UNSUPPORTED", name: "Unsupported route", active: false },
  ];
  feeStructures = [
    {
      id: "fee",
      code: "APP",
      name: "Application fee",
      programmeLevelCode: "UG",
      applicantCategoryCode: "LOCAL",
      feeContext: "APPLICATION",
      status: "ACTIVE",
    },
    {
      id: "new-fee",
      code: "NEW",
      name: "New application fee",
      programmeLevelCode: "UG",
      applicantCategoryCode: null,
      feeContext: "APPLICATION",
      status: "ACTIVE",
    },
    {
      id: "retired",
      code: "RETIRED",
      name: "Retired application fee",
      feeContext: "APPLICATION",
      status: "RETIRED",
    },
    { id: "tuition", code: "TUITION", name: "Tuition", feeContext: "TUITION", status: "ACTIVE" },
  ];
  routeConfigurations = [
    {
      applicationTypeId: "type",
      code: "UNDERGRAD",
      name: "Undergraduate applications",
      active: false,
      readyForActivation: true,
      readinessBlockers: [],
      programmes: [
        { programmeId: "science", programmeCode: "BSC", programmeName: "Science" },
        { programmeId: "arts", programmeCode: "BA", programmeName: "Arts" },
      ],
      sections: [{ code: "PROFILE", required: true }],
      documents: [{ code: "ID", required: true }],
      feePolicyStatus: "FEE_STRUCTURE",
      version: 6,
    },
  ];
  quotas = [
    {
      id: "quota",
      programmeId: "science",
      programmeCode: "BSC",
      programmeName: "Science",
      capacity: 50,
      reservedCapacity: 5,
      version: 9,
    },
  ];
  context.request.mockImplementation(async (path: string, options?: any) => {
    if (options?.method) {
      if (path === "/api/academic/intakes" || path === "/api/academic/intakes/intake")
        return existingIntake({ version: 7, name: options.body.name });
      if (path.endsWith("/open")) return existingIntake({ status: "OPEN", version: 8 });
      if (path.endsWith("/route-configuration"))
        return { ...routeConfigurations[0], version: 14, active: options.body.activate };
      if (path === "/api/admissions/application-types/type")
        return { ...applicationTypes[0], ...options.body, version: 13 };
      return {};
    }
    if (path === "/api/admissions/application-types") return applicationTypes;
    if (path === "/api/finance/fee-structures") return { structures: feeStructures };
    if (path.endsWith("/programme-quotas")) return quotas;
    if (path.endsWith("/route-configuration"))
      return routeConfigurations.find((configuration) =>
        path.includes(`/${configuration.applicationTypeId}/`),
      );
    throw new Error(`Unexpected request ${path}`);
  });
});
afterEach(() => {
  wrapper?.unmount();
  vi.unstubAllGlobals();
});
async function render() {
  wrapper = mount(IntakeOpening, { global: { stubs, mocks: { navigateTo: context.navigateTo } } });
  await flushPromises();
}
async function validDetails() {
  await setField(wrapper, "Intake code", "AUG-2026");
  await setField(wrapper, "Applicant-facing name", "August 2026 intake");
  await setField(wrapper, "Applications open", "2026-08-01");
  await setField(wrapper, "Applications close", "2026-08-30");
  await setField(wrapper, "Offer acceptance deadline", "2026-09-01");
  await setField(wrapper, "Commencement date", "2026-09-04");
}
async function chooseLevels(ids = ["level"]) {
  await wrapper.get('[aria-label="Programme Levels"]').setValue(ids);
  await flushPromises();
}
async function routesStep() {
  await render();
  if (routeId === "new" || routeId === undefined || (Array.isArray(routeId) && !routeId.length))
    await validDetails();
  await clickButton(wrapper, "Continue to eligibility");
  if (!overview.value.intakes.length) await chooseLevels();
  await clickButton(wrapper, "Continue to routes and fees");
}
async function quotasStep() {
  await routesStep();
  await clickButton(wrapper, "Continue to Programme quotas");
}
async function reviewStep() {
  await quotasStep();
  for (const input of wrapper.findAll('input[aria-label$="total capacity"]'))
    await input.setValue(100);
  await flushPromises();
  await clickButton(wrapper, "Review admissions opening");
  await setField(wrapper, "Opening reason", "  Approved admissions readiness  ");
}
function writes() {
  return context.request.mock.calls.filter(([, options]) => options?.method);
}
async function revisitStep(label: string) {
  const button = wrapper
    .findAll('[aria-label="Intake setup progress"] button')
    .find((button) => button.text().includes(label));
  await button!.trigger("click");
  await flushPromises();
}

describe("intake date and eligibility governance", () => {
  it("starts a new intake in the open year, hides archived years and blocks skipping stages", async () => {
    await render();
    expect(wrapper.text()).toContain("Create and open an intake");
    expect(
      (wrapper.get('[data-label="Academic year"] select').element as HTMLSelectElement).value,
    ).toBe("year");
    expect(wrapper.get('[data-label="Academic year"]').text()).not.toContain("Old academic year");
    await clickButton(wrapper, "Continue to eligibility");
    expect(wrapper.text()).toContain("Define the intake");
    expect(
      wrapper.findAll('[aria-label="Intake setup progress"] button')[1]!.attributes("disabled"),
    ).toBeDefined();
  });
  it.each([
    ["Applications close", "2026-07-31", "end date"],
    ["Offer acceptance deadline", "", "offer acceptance deadline"],
    ["Commencement date", "", "commencement date"],
    ["Registration date", "2026-09-05", "Registration cannot"],
    ["Orientation date", "2026-09-05", "Orientation cannot"],
    ["Applications open", "2025-12-31", "Keep the application window"],
    ["Applications close", "2027-01-01", "Keep the application window"],
    ["Maximum Programme choices", "0", "between 1 and 20"],
    ["Maximum Programme choices", "21", "between 1 and 20"],
  ] as const)("blocks invalid %s=%s before advancing", async (label, value, message) => {
    await render();
    await validDetails();
    await setField(wrapper, label, value);
    await clickButton(wrapper, "Continue to eligibility");
    expect(wrapper.text()).toContain(message);
    expect(wrapper.text()).toContain("Define the intake");
    expect(writes()).toHaveLength(0);
  });
  it.each([[], undefined])(
    "handles absent intake parameter %j and an empty academic catalogue",
    async (id) => {
      routeId = id;
      overview.value = null;
      await render();
      expect(wrapper.text()).toContain("Create and open an intake");
      expect(wrapper.findAll('[data-label="Academic year"] option')).toHaveLength(1);
      await clickButton(wrapper, "Continue to eligibility");
      expect(wrapper.text()).toContain("Complete the academic year");
    },
  );
  it("uses the first available year when no open year exists", async () => {
    overview.value.academicYears = [
      {
        id: "draft-year",
        name: "Draft year",
        status: "DRAFT",
        startDate: "2026-01-01",
        endDate: "2026-12-31",
      },
    ];
    await render();
    expect(
      (wrapper.get('[data-label="Academic year"] select').element as HTMLSelectElement).value,
    ).toBe("draft-year");
  });
  it.each(["missing", "OPEN"])("refuses unavailable or non-draft intake %s", async (status) => {
    routeId = ["intake", "ignored"];
    if (status !== "missing") overview.value.intakes = [existingIntake({ status })];
    await render();
    expect(wrapper.text()).toContain("Intake setup unavailable");
    expect(wrapper.text()).toContain(
      status === "missing" ? "no longer exists" : "Only draft intakes",
    );
    expect(context.request).not.toHaveBeenCalled();
  });
  it("shows initial loading and reports Academic Setup failures", async () => {
    let reject!: (error: Error) => void;
    ensureOverview.mockImplementationOnce(
      () =>
        new Promise((_resolve, fail) => {
          reject = fail;
        }),
    );
    await render();
    expect(wrapper.text()).toContain("Loading intake configuration…");
    reject(new Error("Academic Setup unavailable"));
    await flushPromises();
    expect(wrapper.text()).toContain("Academic Setup unavailable");
  });
  it("retains selected inactive programme references while filtering unselected inactive records", async () => {
    routeId = "intake";
    overview.value.intakes = [
      existingIntake({
        programmeLevels: [{ id: "level" }, { id: "inactive-level" }],
        specificProgrammes: [{ id: "inactive" }],
      }),
    ];
    await render();
    await clickButton(wrapper, "Continue to eligibility");
    expect(wrapper.get('[aria-label="Programme Levels"]').text()).toContain("Old level");
    expect(wrapper.get('[aria-label="Specific Programmes"]').text()).toContain(
      "Inactive programme",
    );
    expect(wrapper.text()).toContain("Only the 1 selected Programme will be available.");
    await chooseLevels(["inactive-level"]);
    expect(
      (wrapper.get('[aria-label="Specific Programmes"]').element as HTMLSelectElement)
        .selectedOptions,
    ).toHaveLength(0);
    expect(wrapper.text()).toContain("Programme Level coverage");
  });
  it("requires a level and clears stale programme whitelists when their level is removed", async () => {
    await render();
    await validDetails();
    await clickButton(wrapper, "Continue to eligibility");
    await clickButton(wrapper, "Continue to routes and fees");
    expect(wrapper.text()).toContain("Select at least one Programme Level");
    expect(wrapper.get('[aria-label="Specific Programmes"]').attributes("disabled")).toBeDefined();
    await chooseLevels();
    expect(wrapper.get('[aria-label="Specific Programmes"]').text()).not.toContain(
      "Inactive programme",
    );
    await wrapper.get('[aria-label="Specific Programmes"]').setValue(["science", "arts"]);
    expect(wrapper.text()).toContain("Only the 2 selected Programmes will be available.");
    await chooseLevels([]);
    expect(
      (wrapper.get('[aria-label="Specific Programmes"]').element as HTMLSelectElement)
        .selectedOptions,
    ).toHaveLength(0);
  });
  it("loads optional draft dates safely and keeps setup blocked until required dates are completed", async () => {
    routeId = "intake";
    overview.value.intakes = [
      existingIntake({
        offerAcceptanceDeadline: null,
        registrationDate: null,
        orientationDate: null,
        commencementDate: null,
      }),
    ];
    await render();
    expect(
      (wrapper.get('[data-label="Offer acceptance deadline"] input').element as HTMLInputElement)
        .value,
    ).toBe("");
    await clickButton(wrapper, "Continue to eligibility");
    expect(wrapper.text()).toContain("Set the offer acceptance deadline");
  });
});

describe("intake route coverage and capacity safeguards", () => {
  it("shows supported route coverage and only active application fees", async () => {
    await routesStep();
    expect(wrapper.text()).toContain("Confirm routes and application fees");
    expect(wrapper.text()).toContain("2 Programmes");
    expect(wrapper.text()).toContain("INACTIVE");
    const feeChoices = wrapper.get('[aria-label="UNDERGRAD application fee"]').text();
    expect(feeChoices).toContain("New application fee");
    expect(feeChoices).not.toContain("Retired application fee");
    expect(feeChoices).not.toContain("Tuition");
    expect(context.request).not.toHaveBeenCalledWith(
      "/api/admissions/application-types/ignored/route-configuration",
    );
  });
  it("reports missing programme-to-route mappings and links to their owning setup", async () => {
    routeConfigurations[0].programmes = [];
    await routesStep();
    expect(wrapper.text()).toContain("BSC has no application route");
    await clickButton(wrapper, "Continue to Programme quotas");
    expect(wrapper.text()).toContain("Confirm routes and application fees");
    await clickButton(wrapper, "Open Application Types");
    expect(context.navigateTo).toHaveBeenCalledWith("/operations/application-types");
  });
  it("requires an active programme within the selected level", async () => {
    overview.value.programmes = [];
    await routesStep();
    expect(wrapper.text()).toContain("Select at least one active Programme");
    await clickButton(wrapper, "Continue to Programme quotas");
    expect(wrapper.text()).toContain("Confirm routes and application fees");
  });
  it("surfaces opening configuration failures without concealing the intake form", async () => {
    context.request.mockRejectedValueOnce(new Error("Admissions configuration offline"));
    await routesStep();
    expect(wrapper.text()).toContain("Admissions configuration unavailable");
    expect(wrapper.text()).toContain("Admissions configuration offline");
    await clickButton(wrapper, "Continue to Programme quotas");
    expect(wrapper.text()).toContain("Confirm routes and application fees");
  });
  it("ignores fixable route fee/mapping blockers but stops on missing evidence governance", async () => {
    routeConfigurations[0].readinessBlockers = [
      "programme mapping incomplete",
      "fee structure missing",
      "fee-free audit needed",
      "required document threshold missing",
    ];
    await routesStep();
    expect(wrapper.text()).toContain("Route setup still has requirements");
    expect(wrapper.text()).toContain("UNDERGRAD: required document threshold missing.");
  });
  it.each(["FEE_FREE", "UNCONFIGURED"])(
    "handles unlinked route policy %s through explicit fee selection",
    async (policy) => {
      applicationTypes[0].financeFeeStructureId = null;
      routeConfigurations[0].feePolicyStatus = policy;
      routeConfigurations[0].readinessBlockers = ["fee structure missing", "fee-free audit needed"];
      await routesStep();
      if (policy === "UNCONFIGURED") {
        expect(wrapper.text()).toContain(
          "Select an application fee or record UNDERGRAD as fee-free.",
        );
        await wrapper.get('[aria-label="UNDERGRAD fee policy"]').setValue("FEE_FREE");
      }
      expect(wrapper.text()).toContain("Fee-free route");
      await clickButton(wrapper, "Continue to Programme quotas");
      expect(wrapper.text()).toContain("Planning capacity only");
    },
  );
  it.each([
    ["BSC total capacity", "0"],
    ["BSC total capacity", "1.5"],
    ["BSC reserved capacity", "-1"],
    ["BSC reserved capacity", "0.5"],
    ["BSC reserved capacity", "101"],
  ] as const)("blocks invalid quota %s=%s", async (label, value) => {
    await quotasStep();
    await wrapper.get('[aria-label="BSC total capacity"]').setValue("100");
    await wrapper.get('[aria-label="BA total capacity"]').setValue("100");
    await wrapper.get(`[aria-label="${label}"]`).setValue(value);
    await clickButton(wrapper, "Review admissions opening");
    expect(wrapper.text()).toContain("Enter a valid total and reserved capacity for BSC.");
    expect(wrapper.text()).toContain("Set Programme planning quotas");
  });
  it("preserves existing quotas when coverage is refined and rebuilds newly included programme rows", async () => {
    routeId = "intake";
    overview.value.intakes = [existingIntake()];
    await quotasStep();
    expect(
      (wrapper.get('[aria-label="BSC total capacity"]').element as HTMLInputElement).value,
    ).toBe("50");
    expect(
      (wrapper.get('[aria-label="BA total capacity"]').element as HTMLInputElement).value,
    ).toBe("0");
    await revisitStep("Programme eligibility");
    await wrapper.get('[aria-label="Specific Programmes"]').setValue(["science"]);
    await clickButton(wrapper, "Continue to routes and fees");
    expect(wrapper.text()).toContain("1 Programme");
    await clickButton(wrapper, "Continue to Programme quotas");
    expect(wrapper.find('[aria-label="BA total capacity"]').exists()).toBe(false);
    expect(
      (wrapper.get('[aria-label="BSC total capacity"]').element as HTMLInputElement).value,
    ).toBe("50");
    await clickButton(wrapper, "Back");
    expect(wrapper.text()).toContain("Confirm routes and application fees");
  });
});

describe("audited intake draft and opening persistence", () => {
  it.each([false, true])(
    "creates a governed intake with open=%s after route and quota configuration",
    async (open) => {
      await reviewStep();
      expect(wrapper.text()).toContain("Ready to open applications");
      await clickButton(wrapper, open ? "Create and open intake" : "Save draft");
      const calls = writes();
      expect(calls[0]).toEqual([
        "/api/academic/intakes",
        {
          method: "POST",
          body: expect.objectContaining({
            academicYearId: "year",
            code: "AUG-2026",
            offerAcceptanceDeadline: "2026-09-01T21:59:59Z",
            registrationDate: null,
            orientationDate: null,
            maximumProgrammeChoices: 3,
            programmeLevelIds: ["level"],
            programmeIds: [],
          }),
        },
      ]);
      expect(calls[1]).toEqual([
        "/api/admissions/application-types/type/route-configuration",
        {
          method: "PUT",
          body: expect.objectContaining({
            programmes: routeConfigurations[0].programmes,
            sections: routeConfigurations[0].sections,
            documents: routeConfigurations[0].documents,
            feeFree: false,
            feeFreeReason: null,
            activate: open,
            expectedVersion: 6,
            changeReason: "Approved admissions readiness",
          }),
        },
      ]);
      expect(calls[2]).toEqual([
        "/api/admissions/intakes/intake/programme-quotas",
        {
          method: "PUT",
          body: {
            quotas: [
              {
                programmeId: "science",
                programmeCode: "BSC",
                programmeName: "Science",
                quotaTypeCode: "GENERAL",
                capacity: 100,
                reservedCapacity: 0,
                expectedVersion: 0,
              },
              {
                programmeId: "arts",
                programmeCode: "BA",
                programmeName: "Arts",
                quotaTypeCode: "GENERAL",
                capacity: 100,
                reservedCapacity: 0,
                expectedVersion: 0,
              },
            ],
            changeReason: "Approved admissions readiness",
          },
        },
      ]);
      expect(calls.some(([path]) => path.endsWith("/open"))).toBe(open);
      if (open)
        expect(calls[3]).toEqual([
          "/api/academic/intakes/intake/open",
          { method: "POST", body: { expectedVersion: 7 } },
        ]);
      expect(loadOverview).toHaveBeenCalledOnce();
      expect(context.navigateTo).toHaveBeenCalledWith("/operations/academic-calendar");
      expect(context.notify).toHaveBeenCalledWith(
        expect.objectContaining({
          title: open ? "August 2026 intake configured and opened" : "Intake saved as draft",
        }),
      );
    },
  );
  it("carries changed fee versions into route configuration and preserves active routes on draft save", async () => {
    routeId = "intake";
    overview.value.intakes = [existingIntake()];
    applicationTypes[0].active = true;
    await routesStep();
    await wrapper.get('[aria-label="UNDERGRAD application fee"]').setValue("new-fee");
    await clickButton(wrapper, "Continue to Programme quotas");
    await wrapper.get('[aria-label="BA total capacity"]').setValue("20");
    await clickButton(wrapper, "Review admissions opening");
    await setField(wrapper, "Opening reason", "Updated fee approved by Finance");
    await clickButton(wrapper, "Save draft");
    const calls = writes();
    expect(calls[0]![1].body).toMatchObject({
      expectedVersion: 5,
      registrationDate: "2026-09-02",
      orientationDate: "2026-09-03",
    });
    expect(calls[1]).toEqual([
      "/api/admissions/application-types/type",
      {
        method: "PUT",
        body: expect.objectContaining({
          financeFeeStructureId: "new-fee",
          financeFeeStructureCode: "NEW",
          financeFeeStructureName: "New application fee",
          active: true,
          expectedVersion: 6,
        }),
      },
    ]);
    expect(calls[2]![1].body).toMatchObject({ expectedVersion: 13, activate: true });
    expect(calls[3]![1].body.quotas[0]).toMatchObject({
      expectedVersion: 9,
      capacity: 50,
      reservedCapacity: 5,
    });
    expect(context.notify).toHaveBeenCalledWith(
      expect.objectContaining({ title: "Intake updated" }),
    );
  });
  it("records fee-free reasons across the route and supports opening an existing draft", async () => {
    routeId = "intake";
    overview.value.intakes = [existingIntake()];
    await routesStep();
    await wrapper.get('[aria-label="UNDERGRAD fee policy"]').setValue("FEE_FREE");
    await clickButton(wrapper, "Continue to Programme quotas");
    await wrapper.get('[aria-label="BA total capacity"]').setValue("30");
    await clickButton(wrapper, "Review admissions opening");
    await setField(wrapper, "Opening reason", "Fee waived for scholarship intake");
    await clickButton(wrapper, "Save and open intake");
    expect(writes()[1]![1].body).toMatchObject({
      feeFree: true,
      feeFreeReason:
        "Fee-free decision recorded while opening August 2026 intake. Fee waived for scholarship intake",
      activate: true,
    });
  });
  it("requires an open academic year and audited opening reason", async () => {
    await reviewStep();
    await setField(wrapper, "Opening reason", "short");
    expect(wrapper.text()).toContain("at least 10 characters");
    expect(
      wrapper
        .findAll("button")
        .find((button) => button.text() === "Create and open intake")!
        .attributes("disabled"),
    ).toBeDefined();
    await setField(wrapper, "Opening reason", "Complete opening review");
    overview.value.academicYears[0].status = "DRAFT";
    await flushPromises();
    expect(wrapper.text()).toContain("Open the selected academic year before opening this intake.");
    expect(
      wrapper
        .findAll("button")
        .find((button) => button.text() === "Create and open intake")!
        .attributes("disabled"),
    ).toBeDefined();
  });
  it.each([false, true])(
    "reports initial save failure for existing=%s without claiming success",
    async (existing) => {
      if (existing) {
        routeId = "intake";
        overview.value.intakes = [existingIntake()];
      }
      await reviewStep();
      context.request.mockRejectedValueOnce(new Error("Intake version conflict"));
      await clickButton(wrapper, "Save draft");
      expect(context.showError).toHaveBeenCalledWith(
        existing ? "Intake could not be updated" : "Intake could not be created",
        existing
          ? "Intake version conflict The intake remains saved as a draft so you can correct the issue and retry."
          : "Intake version conflict",
      );
      expect(context.navigateTo).not.toHaveBeenCalled();
      expect(context.notify).not.toHaveBeenCalled();
    },
  );
  it("keeps partial setup saved as a draft and retries with its returned version", async () => {
    await reviewStep();
    const original = context.request.getMockImplementation()!;
    let fail = true;
    context.request.mockImplementation(async (path: string, options?: any) => {
      if (fail && options?.method && path.endsWith("route-configuration")) {
        fail = false;
        throw new Error("Admissions route conflict");
      }
      return original(path, options);
    });
    await clickButton(wrapper, "Create and open intake");
    expect(context.showError).toHaveBeenCalledWith(
      "Admissions opening could not be completed",
      expect.stringContaining("intake remains saved as a draft"),
    );
    expect(writes().some(([path]) => path.endsWith("/open"))).toBe(false);
    await clickButton(wrapper, "Save and open intake");
    const update = writes().find(([path]) => path === "/api/academic/intakes/intake");
    expect(update![1].body.expectedVersion).toBe(7);
    expect(context.navigateTo).toHaveBeenCalledWith("/operations/academic-calendar");
  });
  it("reports a removed fee after saving the intake and does not configure quotas or open", async () => {
    await reviewStep();
    feeStructures.splice(0, 1);
    await clickButton(wrapper, "Create and open intake");
    expect(context.showError).toHaveBeenCalledWith(
      "Admissions opening could not be completed",
      expect.stringContaining("selected UNDERGRAD application fee is no longer active"),
    );
    expect(writes()).toHaveLength(1);
    expect(context.navigateTo).not.toHaveBeenCalled();
  });
});
