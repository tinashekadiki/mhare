// Author: Tinashe K
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { ref, defineComponent } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import CalendarPage from "../../pages/operations/academic-calendar/index.vue";
import type {
  AcademicYearSummary,
  AcademicPeriodTypeSummary,
  AcademicPeriodSummary,
  IntakeSummary,
  ProgrammeLevelSummary,
} from "../../../../packages/portal-shell/types/academic";
import {
  clickButton,
  operationalContext,
  setField,
} from "../../../../tests/unit/support/operational-page";
import { registerStubs } from "../../../../tests/unit/support/register-page";
const Guidance = defineComponent({
  props: ["label", "guidanceInstructions", "guidanceActionLabel"],
  emits: ["click", "guidance-action"],
  template: `<div><button @click="$emit('click')">{{label}}</button><p v-for="instruction in guidanceInstructions">{{instruction}}</p><button v-if="guidanceInstructions?.length" @click="$emit('guidance-action')">{{guidanceActionLabel}}</button></div>`,
});
let wrapper: VueWrapper;
let context: ReturnType<typeof operationalContext>;
const confirmAction = vi.fn(),
  loadOverview = vi.fn(),
  ensureOverview = vi.fn();
type CalendarOverview = {
  academicYears: AcademicYearSummary[];
  academicPeriodTypes: AcademicPeriodTypeSummary[];
  academicPeriods: AcademicPeriodSummary[];
  intakes: IntakeSummary[];
  programmeLevels: ProgrammeLevelSummary[];
  programmes: {
    id: string;
    code: string;
    name: string;
    programmeLevelId: string;
    status: string;
  }[];
};
const overview = ref<CalendarOverview | null>(null),
  loadError = ref("");
const year: AcademicYearSummary = {
  id: "year",
  name: "2026 Academic Year",
  startDate: "2026-01-01",
  endDate: "2026-12-31",
  status: "OPEN",
  changeReason: "Created",
  version: 5,
};
const periodType: AcademicPeriodTypeSummary = {
  id: "type",
  code: "SEMESTER",
  name: "Semester",
  sortOrder: 1,
  status: "ACTIVE",
  changeReason: "Created",
  version: 4,
};
const period: AcademicPeriodSummary = {
  id: "period",
  academicYearId: "year",
  academicYearName: year.name,
  academicPeriodTypeId: "type",
  academicPeriodTypeName: "Semester",
  code: "2026-S1",
  name: "Semester one",
  startDate: "2026-01-01",
  endDate: "2026-06-30",
  status: "DRAFT",
  changeReason: "Created",
  version: 6,
};
const level: ProgrammeLevelSummary = {
  id: "level",
  code: "UG",
  name: "Undergraduate",
  sortOrder: 1,
  status: "ACTIVE",
  version: 1,
};
const programme = {
  id: "programme",
  code: "BSC",
  name: "Science",
  programmeLevelId: "level",
  status: "ACTIVE",
};
const intake: IntakeSummary = {
  id: "intake",
  academicYearId: "year",
  academicYearName: year.name,
  code: "AUG26",
  name: "August intake",
  startsOn: "2026-01-01",
  endsOn: "2026-07-31",
  offerAcceptanceDeadline: "2026-08-15T21:59:59Z",
  registrationDate: "2026-08-20",
  orientationDate: "2026-08-21",
  commencementDate: "2026-08-24",
  status: "OPEN",
  maximumProgrammeChoices: 3,
  changeReason: "Created",
  programmeLevels: [level],
  specificProgrammes: [{ ...programme, programmeLevelName: "Undergraduate" }],
  allProgrammesInSelectedLevels: false,
  version: 9,
};
beforeEach(() => {
  vi.resetAllMocks();
  context = operationalContext();
  confirmAction.mockResolvedValue(true);
  loadError.value = "";
  overview.value = structuredClone({
    academicYears: [year],
    academicPeriodTypes: [periodType],
    academicPeriods: [period],
    intakes: [intake],
    programmeLevels: [level],
    programmes: [programme],
  });
  vi.stubGlobal("useAcademicSetup", () => ({
    overview,
    loadOverview,
    ensureOverview,
    loading: ref(false),
    loadError,
  }));
  vi.stubGlobal("useEmhareConfirm", () => ({ confirmAction, showError: context.showError }));
  context.request.mockResolvedValue({});
});
afterEach(() => {
  wrapper?.unmount();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
async function render() {
  wrapper = mount(CalendarPage, {
    global: { stubs: { ...registerStubs, EmhareGuidedActionButton: Guidance } },
  });
  await flushPromises();
}
async function submitForm() {
  await wrapper.get('[role="dialog"] form').trigger("submit");
  await flushPromises();
}
const rowButtons = () => wrapper.findAll("article button").map((button) => button.text());
describe("academic calendar governance", () => {
  it("renders current years, date ranges and lifecycle actions", async () => {
    overview.value!.academicYears = [
      { ...year, status: "DRAFT" },
      year,
      { ...year, id: "closed", status: "CLOSED" },
      { ...year, id: "archived", status: "ARCHIVED" },
    ];
    await render();
    expect(wrapper.text()).toContain("Academic years: 4");
    expect(wrapper.text()).toContain("Open years: 1");
    expect(rowButtons().filter((label) => label === "Open")).toHaveLength(1);
    expect(rowButtons().filter((label) => label === "Close")).toHaveLength(1);
    await clickButton(wrapper, "Refresh");
    expect(loadOverview).toHaveBeenCalledOnce();
  });
  it("survives absent overview and shows shared load failure with empty-state guidance", async () => {
    overview.value = null;
    loadError.value = "Service unavailable";
    ensureOverview.mockRejectedValue(new Error("Service unavailable"));
    await render();
    expect(wrapper.text()).toContain("Academic calendar unavailable");
    expect(wrapper.text()).toContain("Create an academic year to begin calendar setup.");
    await clickButton(wrapper, "Academic periods");
    expect(wrapper.text()).toContain("Create an academic year before creating an academic period.");
    await clickButton(wrapper, "Open Academic years");
    expect(wrapper.text()).toContain("Create academic year");
    await clickButton(wrapper, "Intakes");
    expect(wrapper.text()).toContain("Create an active Programme Level");
    await clickButton(wrapper, "Open Academic years");
    expect(wrapper.text()).toContain("Create academic year");
  });
  it("routes missing period-type and programme-level prerequisites", async () => {
    overview.value!.academicPeriodTypes = [];
    overview.value!.programmeLevels = [];
    await render();
    await clickButton(wrapper, "Academic periods");
    await clickButton(wrapper, "Open Period types");
    expect(wrapper.text()).toContain("Create period type");
    await clickButton(wrapper, "Intakes");
    await clickButton(wrapper, "Open Programme catalogue");
    expect(context.navigateTo).toHaveBeenCalledWith("/operations/programmes");
  });
  it("creates a year without audit-update-only fields", async () => {
    await render();
    await clickButton(wrapper, "Create academic year");
    await setField(wrapper, "Name", "2027");
    await setField(wrapper, "Start date", "2027-01-01");
    await setField(wrapper, "End date", "2027-12-31");
    await submitForm();
    expect(context.request).toHaveBeenCalledWith("/api/academic/years", {
      method: "POST",
      body: { name: "2027", startDate: "2027-01-01", endDate: "2027-12-31" },
    });
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    expect(context.notify).toHaveBeenCalledWith(
      expect.objectContaining({ title: "Academic year created" }),
    );
  });
  it("corrects a year with evidence and its persisted version", async () => {
    await render();
    await clickButton(wrapper, "Edit");
    await setField(wrapper, "Name", "Corrected year");
    await setField(wrapper, "Change reason", "Council approved correction");
    await submitForm();
    expect(context.request).toHaveBeenCalledWith("/api/academic/years/year", {
      method: "PUT",
      body: {
        name: "Corrected year",
        startDate: year.startDate,
        endDate: year.endDate,
        changeReason: "Council approved correction",
        expectedVersion: 5,
      },
    });
  });
  it("creates a period type at the next sort position and saves numeric order", async () => {
    await render();
    await clickButton(wrapper, "Period types");
    await clickButton(wrapper, "Create period type");
    expect(wrapper.get('[data-label="Sort order"] input').element).toHaveProperty("value", "2");
    await setField(wrapper, "Code", "TERM");
    await setField(wrapper, "Name", "Term");
    await setField(wrapper, "Sort order", "3");
    await submitForm();
    expect(context.request).toHaveBeenCalledWith("/api/academic/period-types", {
      method: "POST",
      body: { code: "TERM", name: "Term", sortOrder: 3 },
    });
  });
  it.each([false, true])(
    "locks a period-type code only when referenced: %s",
    async (referenced) => {
      if (!referenced) overview.value!.academicPeriods = [];
      await render();
      await clickButton(wrapper, "Period types");
      await clickButton(wrapper, "Edit");
      expect(wrapper.get('[data-label="Code"] input').attributes("disabled") !== undefined).toBe(
        referenced,
      );
      await setField(wrapper, "Name", "Semester corrected");
      await setField(wrapper, "Change reason", "Governed rename");
      await submitForm();
      expect(context.request).toHaveBeenCalledWith("/api/academic/period-types/type", {
        method: "PUT",
        body: {
          code: "SEMESTER",
          name: "Semester corrected",
          sortOrder: 1,
          changeReason: "Governed rename",
          expectedVersion: 4,
        },
      });
    },
  );
  it("creates a period with active year/type choices and explicit dates", async () => {
    overview.value!.academicYears.push({
      ...year,
      id: "archived",
      status: "ARCHIVED",
      name: "Archived year",
    });
    overview.value!.academicPeriodTypes.push({
      ...periodType,
      id: "inactive",
      status: "INACTIVE",
      name: "Inactive type",
    });
    await render();
    await clickButton(wrapper, "Academic periods");
    await clickButton(wrapper, "Create academic period");
    expect(wrapper.get('[data-label="Academic year"] select').text()).not.toContain(
      "Archived year",
    );
    expect(wrapper.get('[data-label="Period type"] select').text()).not.toContain("Inactive type");
    await setField(wrapper, "Code", "2026-S2");
    await setField(wrapper, "Name", "Semester two");
    await setField(wrapper, "Start date", "2026-07-01");
    await setField(wrapper, "End date", "2026-12-31");
    await submitForm();
    expect(context.request).toHaveBeenCalledWith("/api/academic/periods", {
      method: "POST",
      body: {
        academicYearId: "year",
        academicPeriodTypeId: "type",
        code: "2026-S2",
        name: "Semester two",
        startDate: "2026-07-01",
        endDate: "2026-12-31",
      },
    });
  });
  it.each(["DRAFT", "OPEN", "CLOSED"] as const)(
    "allows %s period corrections while locking operational identity after draft",
    async (status) => {
      overview.value!.academicPeriods = [{ ...period, status }];
      await render();
      await clickButton(wrapper, "Academic periods");
      await clickButton(wrapper, "Edit");
      expect(wrapper.get('[data-label="Code"] input').attributes("disabled") !== undefined).toBe(
        status !== "DRAFT",
      );
      expect(wrapper.text().includes("Operational identity locked")).toBe(status !== "DRAFT");
      await setField(wrapper, "Name", "Corrected semester");
      await setField(wrapper, "Change reason", "Approved date correction");
      await submitForm();
      expect(context.request).toHaveBeenCalledWith("/api/academic/periods/period", {
        method: "PUT",
        body: expect.objectContaining({
          name: "Corrected semester",
          expectedVersion: 6,
          changeReason: "Approved date correction",
          academicYearId: "year",
          academicPeriodTypeId: "type",
        }),
      });
    },
  );
  it("routes new and draft intakes into their setup workspace", async () => {
    overview.value!.intakes = [{ ...intake, status: "DRAFT" }];
    await render();
    await clickButton(wrapper, "Intakes");
    await clickButton(wrapper, "Create intake");
    await clickButton(wrapper, "Continue setup");
    expect(context.navigateTo.mock.calls).toEqual([
      ["/operations/academic-calendar/intakes/new"],
      ["/operations/academic-calendar/intakes/intake"],
    ]);
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
  });
  it.each([false, true])(
    "corrects published intake with optional dates %s and immutable eligibility",
    async (optional) => {
      overview.value!.intakes = [
        {
          ...intake,
          registrationDate: optional ? intake.registrationDate : null,
          orientationDate: optional ? intake.orientationDate : null,
          offerAcceptanceDeadline: optional ? intake.offerAcceptanceDeadline : null,
          commencementDate: optional ? intake.commencementDate : null,
        },
      ];
      overview.value!.programmeLevels.push({
        ...level,
        id: "unused-level",
        status: "INACTIVE",
        name: "Unused inactive",
      });
      overview.value!.programmeLevels[0]!.status = "INACTIVE";
      overview.value!.programmes[0]!.status = "INACTIVE";
      overview.value!.programmes.push(
        {
          ...programme,
          id: "unused-programme",
          name: "Unused inactive programme",
          status: "INACTIVE",
        },
        { ...programme, id: "other-programme", programmeLevelId: "other" },
      );
      await render();
      await clickButton(wrapper, "Intakes");
      await clickButton(wrapper, "Edit");
      expect(wrapper.get('[data-label="Code"] input').attributes("disabled")).toBeDefined();
      expect(wrapper.get('[data-label="Programme Levels"] select').text()).toContain(
        "Undergraduate",
      );
      expect(wrapper.get('[data-label="Programme Levels"] select').text()).not.toContain(
        "Unused inactive",
      );
      expect(wrapper.get('[data-label="Specific Programmes"] select').text()).toContain("Science");
      expect(wrapper.get('[data-label="Specific Programmes"] select').text()).not.toContain(
        "Unused inactive programme",
      );
      await setField(wrapper, "Offer acceptance deadline", "2026-08-18");
      await setField(wrapper, "Commencement date", "2026-08-25");
      await setField(wrapper, "Maximum Programme choices", "4");
      await setField(wrapper, "Change reason", "Approved correction");
      await submitForm();
      expect(context.request).toHaveBeenCalledWith("/api/academic/intakes/intake", {
        method: "PUT",
        body: expect.objectContaining({
          expectedVersion: 9,
          offerAcceptanceDeadline: "2026-08-18T21:59:59Z",
          registrationDate: optional ? intake.registrationDate : null,
          orientationDate: optional ? intake.orientationDate : null,
          commencementDate: "2026-08-25",
          maximumProgrammeChoices: 4,
          programmeLevelIds: ["level"],
          programmeIds: ["programme"],
        }),
      });
    },
  );
  it("renders intake eligibility counts for all selected levels or specific programmes", async () => {
    overview.value!.intakes = [
      intake,
      {
        ...intake,
        id: "all",
        allProgrammesInSelectedLevels: true,
        programmeLevels: [level, { ...level, id: "two" }],
      },
      {
        ...intake,
        id: "specific",
        specificProgrammes: [...intake.specificProgrammes, ...intake.specificProgrammes],
      },
    ];
    await render();
    await clickButton(wrapper, "Intakes");
    expect(wrapper.text()).toContain("1 Programme Level");
    expect(wrapper.text()).toContain("2 Programme Levels");
    expect(wrapper.text()).toContain("1 specific Programme");
    expect(wrapper.text()).toContain("2 specific Programmes");
    expect(wrapper.text()).toContain("All active Programmes in selected levels");
  });
  it.each([
    ["Academic years", "Create academic year", "Academic year"],
    ["Period types", "Create period type", "Academic period type"],
    ["Academic periods", "Create academic period", "Academic period"],
  ])("retains %s values after API failure", async (tab, button, label) => {
    await render();
    await clickButton(wrapper, tab);
    await clickButton(wrapper, button);
    await setField(wrapper, "Name", "Unsaved record");
    context.request.mockRejectedValueOnce(new Error("Unavailable"));
    await submitForm();
    expect(context.showError).toHaveBeenCalledWith(`${label} could not be created`, "Unavailable");
    expect(wrapper.get('[data-label="Name"] input').element).toHaveProperty(
      "value",
      "Unsaved record",
    );
  });
  it.each([
    ["Academic years", "year"],
    ["Period types", "type"],
    ["Academic periods", "period"],
    ["Intakes", "intake"],
  ])("closes cancelled %s edits without sending a write", async (tab) => {
    await render();
    await clickButton(wrapper, tab);
    await clickButton(wrapper, "Edit");
    await clickButton(wrapper, "Cancel");
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    expect(context.request).not.toHaveBeenCalled();
  });
  it.each([
    ["Academic years", "academicYears", "year", "DRAFT", "Open", "years"],
    ["Academic years", "academicYears", "year", "OPEN", "Close", "years"],
    ["Academic periods", "academicPeriods", "period", "DRAFT", "Open", "periods"],
    ["Academic periods", "academicPeriods", "period", "OPEN", "Close", "periods"],
    ["Intakes", "intakes", "intake", "OPEN", "Close", "intakes"],
  ] as const)(
    "governs %s %s with cancellation and versioned recovery",
    async (tab, collection, id, status, action, path) => {
      const item = overview.value![collection][0]!;
      item.status = status;
      await render();
      await clickButton(wrapper, tab);
      confirmAction.mockResolvedValueOnce(false);
      await clickButton(wrapper, action);
      expect(context.request).not.toHaveBeenCalled();
      context.request.mockRejectedValueOnce(new Error("Conflict"));
      await clickButton(wrapper, action);
      expect(context.showError).toHaveBeenCalledWith(expect.any(String), "Conflict");
      await clickButton(wrapper, action);
      expect(context.request).toHaveBeenLastCalledWith(
        `/api/academic/${path}/${id}/${action.toLowerCase()}`,
        { method: "POST", body: { expectedVersion: item.version } },
      );
      expect(loadOverview).toHaveBeenCalledOnce();
      expect(context.notify).toHaveBeenCalledWith(expect.objectContaining({ color: "success" }));
    },
  );
});
