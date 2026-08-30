// Author: Tinashe K
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { defineComponent, ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import CurriculumPage from "../../pages/operations/curriculum.vue";
import type {
  CurriculumModuleSummary,
  ProgrammeVersionSummary,
} from "../../../../packages/portal-shell/types/academic";
import {
  clickButton,
  operationalContext,
  operationalStubs,
  setField,
} from "../../../../tests/unit/support/operational-page";
import { registerStubs } from "../../../../tests/unit/support/register-page";
const { confirm } = vi.hoisted(() => ({ confirm: vi.fn() }));
vi.mock("sweetalert2", () => ({ default: { fire: confirm } }));
const Guidance = defineComponent({
  props: ["label", "guidanceInstructions", "guidanceActionLabel", "disabled"],
  emits: ["click", "guidance-action"],
  template:
    '<div><button :disabled="disabled || !!guidanceInstructions?.length" @click="$emit(\'click\')">{{label}}</button><p v-for="item in guidanceInstructions">{{item}}</p><button v-if="guidanceInstructions?.length && guidanceActionLabel" @click="$emit(\'guidance-action\')">{{guidanceActionLabel}}</button></div>',
});
const baseVersion: ProgrammeVersionSummary = {
  id: "version",
  programmeId: "programme",
  programmeCode: "BSC",
  versionCode: "2026.1",
  effectiveFrom: "2026-01-01",
  effectiveTo: null,
  status: "DRAFT",
  approvedByUserId: null,
  approvedAt: null,
  version: 4,
  curriculumModuleCount: 1,
  totalCredits: 12,
};
const basePlacement: CurriculumModuleSummary = {
  id: "placement",
  programmeVersionId: "version",
  moduleId: "attached",
  moduleCode: "CSC101",
  moduleName: "Foundations",
  periodNumber: 3,
  moduleType: "COMPULSORY",
  creditValue: 12,
  minimumMarkRequired: 50,
  sortOrder: 1,
  version: 7,
};
const catalogue = () => ({
  programmes: [
    { id: "programme", code: "BSC", name: "Computing", maximumDurationPeriods: 8 },
    { id: "second", code: "MSC", name: "Advanced computing", maximumDurationPeriods: 4 },
  ],
  modules: [
    { id: "attached", code: "CSC101", name: "Foundations", status: "ACTIVE", creditValue: 12 },
    { id: "available", code: "CSC201", name: "Databases", status: "ACTIVE", creditValue: 18 },
    { id: "inactive", code: "CSC000", name: "Obsolete", status: "INACTIVE", creditValue: 6 },
  ],
});
let wrapper: VueWrapper;
let context: ReturnType<typeof operationalContext>;
let versions: ProgrammeVersionSummary[];
let placements: CurriculumModuleSummary[];
let failPath: string | undefined;
let failWrite: boolean;
let usage: { registrationCount: number; resultCount: number; removable: boolean };
let query: Record<string, unknown>;
const overview = ref<ReturnType<typeof catalogue> | null>(null),
  loadError = ref("");
const ensureOverview = vi.fn(),
  confirmAction = vi.fn();
beforeEach(() => {
  vi.resetAllMocks();
  context = operationalContext();
  versions = [structuredClone(baseVersion)];
  placements = [structuredClone(basePlacement)];
  overview.value = catalogue();
  loadError.value = "";
  query = {};
  failPath = undefined;
  failWrite = false;
  usage = { registrationCount: 0, resultCount: 0, removable: true };
  confirm.mockResolvedValue({
    isConfirmed: true,
    value: "  Committee minute confirms correction  ",
  });
  confirmAction.mockResolvedValue(true);
  vi.stubGlobal("useRoute", () => ({ query }));
  vi.stubGlobal("useAcademicSetup", () => ({
    overview,
    ensureOverview,
    loadError,
    loading: ref(false),
  }));
  vi.stubGlobal("useEmhareConfirm", () => ({ showError: context.showError, confirmAction }));
  context.request.mockImplementation(async (path: string, options?: { method?: string }) => {
    if (path === failPath || (options?.method && failWrite)) throw new Error("Unavailable");
    if (options?.method) return { ...baseVersion, id: "created" };
    if (path.endsWith("/versions")) return structuredClone(versions);
    if (path.endsWith("/curriculum")) return structuredClone(placements);
    if (path.endsWith("/usage")) return { ...usage };
    throw new Error(`Unexpected ${path}`);
  });
});
afterEach(() => {
  wrapper?.unmount();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
async function render() {
  wrapper = mount(CurriculumPage, {
    global: {
      mocks: { navigateTo: context.navigateTo },
      stubs: {
        ...registerStubs,
        EmhareRecordDrawer: operationalStubs.EmhareRecordDrawer,
        EmhareGuidedActionButton: Guidance,
      },
    },
  });
  await flushPromises();
}
const writes = () => context.request.mock.calls.filter(([, options]) => options?.method);
const field = (label: string) =>
  wrapper.findAll(".field").find((element) => element.attributes("data-label") === label)!;
async function submit(id: string) {
  await wrapper.get(`#${id}`).trigger("submit");
  await flushPromises();
}
describe("curriculum versions and guarded Module amendments", () => {
  it("loads the first programme by default and resolves the selected version and placement totals", async () => {
    await render();
    expect(context.request).toHaveBeenCalledWith("/api/academic/programmes/programme/versions");
    expect(context.request).toHaveBeenCalledWith(
      "/api/academic/programme-versions/version/curriculum",
    );
    expect(wrapper.text()).toContain("Total credits: 12.00");
    expect(wrapper.text()).toContain("CSC101");
    expect(wrapper.text()).toContain("Year 2");
    expect(wrapper.text()).toContain("50.00%");
    expect(wrapper.text()).toContain("with no scheduled end");
  });
  it("honours a linked programme query and resets dependent selections when switching programmes", async () => {
    query = { programmeId: "second" };
    await render();
    expect(context.request).toHaveBeenCalledWith("/api/academic/programmes/second/versions");
    versions = [];
    await wrapper.get('select[aria-label="Programme"]').setValue("programme");
    await flushPromises();
    expect(wrapper.text()).not.toContain("CSC101");
    expect(wrapper.findAll("select")[1]!.attributes("disabled")).toBeDefined();
  });
  it("offers prerequisite guidance when Academic Setup is absent and recovers without an unhandled rejection", async () => {
    overview.value = null;
    loadError.value = "Academic service unavailable";
    ensureOverview.mockRejectedValue(new Error("Unavailable"));
    await render();
    expect(wrapper.text()).toContain("Curriculum setup unavailable");
    expect(wrapper.text()).toContain("Create a programme before starting");
    await clickButton(wrapper, "Open Programmes");
    expect(context.navigateTo).toHaveBeenCalledWith("/operations/programmes");
    expect(writes()).toHaveLength(0);
  });
  it.each([
    "/api/academic/programmes/programme/versions",
    "/api/academic/programme-versions/version/curriculum",
  ])("reports loading failure for %s", async (path) => {
    failPath = path;
    await render();
    expect(context.showError).toHaveBeenCalledWith(
      path.endsWith("versions")
        ? "Programme versions could not be loaded"
        : "Curriculum could not be loaded",
      "Unavailable",
    );
  });
  it.each([null, "2027-12-31"])("creates a new version with optional end date %s", async (end) => {
    await render();
    await clickButton(wrapper, "New version");
    await setField(wrapper, "Version code", "2027.1");
    await setField(wrapper, "Effective from", "2027-01-01");
    if (end) await setField(wrapper, "Effective to", end);
    await submit("version-form");
    expect(writes()[0]).toEqual([
      "/api/academic/programmes/programme/versions",
      {
        method: "POST",
        body: { versionCode: "2027.1", effectiveFrom: "2027-01-01", effectiveTo: end },
      },
    ]);
    expect(context.request).toHaveBeenCalledWith(
      "/api/academic/programme-versions/created/curriculum",
    );
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
  });
  it("retains a failed version draft and resets cancelled version fields", async () => {
    await render();
    await clickButton(wrapper, "New version");
    await setField(wrapper, "Version code", "2027.1");
    failWrite = true;
    await submit("version-form");
    expect(context.showError).toHaveBeenCalledWith(
      "Programme version could not be created",
      "Unavailable",
    );
    expect(field("Version code").get("input").element.value).toBe("2027.1");
    await clickButton(wrapper, "Cancel");
    await clickButton(wrapper, "New version");
    expect(field("Version code").get("input").element.value).toBe("");
  });
  it("offers only active unattached Modules, copies credits, and captures placement as programme period", async () => {
    await render();
    await clickButton(wrapper, "Add Module");
    expect(field("Module").text()).toContain("CSC201");
    expect(field("Module").text()).not.toContain("CSC101");
    expect(field("Module").text()).not.toContain("CSC000");
    expect(field("Credit value").get("input").element.value).toBe("18");
    expect(
      wrapper
        .findAll("button")
        .find(
          (button) =>
            button.text() === "Add Module" && button.attributes("form") === "curriculum-form",
        )!
        .attributes("disabled"),
    ).toBeDefined();
    await setField(wrapper, "Year of study", "2");
    await setField(wrapper, "Semester", "2");
    await setField(wrapper, "Requirement type", "ELECTIVE");
    await setField(wrapper, "Amendment reason", "Committee minute 42");
    await submit("curriculum-form");
    expect(writes()[0]).toEqual([
      "/api/academic/programme-versions/version/curriculum",
      {
        method: "POST",
        body: {
          moduleId: "available",
          periodNumber: 4,
          moduleType: "ELECTIVE",
          creditValue: 18,
          minimumMarkRequired: 50,
          sortOrder: 2,
          changeReason: "Committee minute 42",
        },
      },
    ]);
    expect(context.notify).toHaveBeenCalledWith(
      expect.objectContaining({ title: "Module added to curriculum" }),
    );
  });
  it.each([null, 60])(
    "amends placement without changing identity, preserving version and initial mark %s",
    async (mark) => {
      placements[0]!.minimumMarkRequired = mark;
      placements[0]!.moduleType = "ELECTIVE";
      await render();
      if (mark === null) expect(wrapper.text()).toContain("Institution rule");
      await clickButton(wrapper, "Edit");
      expect(wrapper.text()).toContain("Module identity remains fixed");
      expect(wrapper.find('.field[data-label="Module"]').exists()).toBe(false);
      expect(field("Minimum pass mark").get("input").element.value).toBe(String(mark ?? 50));
      expect(field("Year of study").get("select").element.value).toBe("2");
      expect(field("Semester").get("select").element.value).toBe("1");
      await setField(wrapper, "Semester", "2");
      await setField(wrapper, "Credit value", "24");
      await setField(wrapper, "Amendment reason", "Audited correction evidence");
      await submit("curriculum-form");
      expect(writes()[0]).toEqual([
        "/api/academic/programme-versions/version/curriculum/placement",
        {
          method: "PUT",
          body: {
            periodNumber: 4,
            moduleType: "ELECTIVE",
            creditValue: 24,
            minimumMarkRequired: mark ?? 50,
            sortOrder: 1,
            changeReason: "Audited correction evidence",
            expectedVersion: 7,
          },
        },
      ]);
    },
  );
  it.each(["add", "edit"] as const)(
    "retains failed %s amendments and cancels without further requests",
    async (mode) => {
      await render();
      await clickButton(wrapper, mode === "add" ? "Add Module" : "Edit");
      await setField(wrapper, "Amendment reason", "Committee authorisation evidence");
      failWrite = true;
      await submit("curriculum-form");
      expect(context.showError).toHaveBeenCalledWith(
        mode === "add" ? "Module could not be added" : "Curriculum Module could not be amended",
        "Unavailable",
      );
      expect(wrapper.find('[role="dialog"]').exists()).toBe(true);
      await clickButton(wrapper, "Cancel");
      expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
      expect(writes()).toHaveLength(1);
    },
  );
  it.each([
    { registrationCount: 2, resultCount: 0 },
    { registrationCount: 0, resultCount: 3 },
  ])("blocks removal when downstream evidence exists: %j", async (evidence) => {
    usage = { ...evidence, removable: false };
    await render();
    await clickButton(wrapper, "Remove");
    expect(confirm).toHaveBeenCalledWith(
      expect.objectContaining({
        title: "Module cannot be removed",
        text: expect.stringContaining(
          `${evidence.registrationCount} student registration(s) and ${evidence.resultCount} result record(s)`,
        ),
      }),
    );
    expect(writes()).toHaveLength(0);
  });
  it("checks both downstream owners before recording audited removal", async () => {
    await render();
    await clickButton(wrapper, "Remove");
    expect(context.request.mock.calls.findIndex(([path]) => path.endsWith("/usage"))).toBeLessThan(
      context.request.mock.calls.findIndex(([path]) => path.endsWith("/removal")),
    );
    expect(writes()[0]).toEqual([
      "/api/academic/programme-versions/version/curriculum/placement/removal",
      {
        method: "POST",
        body: { changeReason: "Committee minute confirms correction", expectedVersion: 7 },
      },
    ]);
    const validator = confirm.mock.calls[0]![0].inputValidator;
    expect(validator("short")).toContain("10 characters");
    expect(validator("A complete committee minute")).toBeUndefined();
  });
  it("does not remove when the operator cancels after usage verification", async () => {
    confirm.mockResolvedValue({ isConfirmed: false });
    await render();
    await clickButton(wrapper, "Remove");
    expect(context.request).toHaveBeenCalledWith(
      "/api/academic/programme-versions/version/curriculum/placement/usage",
    );
    expect(writes()).toHaveLength(0);
  });
  it.each(["usage", "removal"])("fails closed on %s service errors", async (endpoint) => {
    failPath = `/api/academic/programme-versions/version/curriculum/placement/${endpoint}`;
    await render();
    await clickButton(wrapper, "Remove");
    expect(context.showError).toHaveBeenCalledWith(
      "Curriculum Module could not be removed",
      "Unavailable",
    );
    if (endpoint === "usage") {
      expect(confirm).not.toHaveBeenCalled();
      expect(writes()).toHaveLength(0);
    }
  });
  it("approves a populated draft with optimistic version evidence", async () => {
    await render();
    await clickButton(wrapper, "Approve curriculum");
    expect(writes()[0]).toEqual([
      "/api/academic/programme-versions/version/approve",
      { method: "POST", body: { expectedVersion: 4 } },
    ]);
    expect(context.notify).toHaveBeenCalledWith(
      expect.objectContaining({ title: "Curriculum version approved" }),
    );
  });
  it("cancels approval and displays independent failure when approval is rejected", async () => {
    await render();
    confirmAction.mockResolvedValueOnce(false);
    await clickButton(wrapper, "Approve curriculum");
    expect(writes()).toHaveLength(0);
    failWrite = true;
    await clickButton(wrapper, "Approve curriculum");
    expect(context.showError).toHaveBeenCalledWith(
      "Curriculum version could not be approved",
      "Unavailable",
    );
  });
  it("retires an approved version at the selected effective date without erasing history", async () => {
    versions[0] = {
      ...baseVersion,
      status: "APPROVED",
      approvedAt: "2026-01-01T10:00:00Z",
      effectiveTo: "2026-12-31",
    };
    confirm.mockResolvedValue({ isConfirmed: true, value: "2026-12-31" });
    await render();
    expect(wrapper.text()).toContain("Approved curriculum remains amendable");
    await clickButton(wrapper, "Retire version");
    expect(writes()[0]).toEqual([
      "/api/academic/programme-versions/version/retire",
      { method: "POST", body: { expectedVersion: 4, retirementDate: "2026-12-31" } },
    ]);
    const validator = confirm.mock.calls[0]![0].inputValidator;
    expect(validator("")).toBe("A retirement date is required.");
    expect(validator("2026-12-31")).toBeUndefined();
  });
  it("handles cancelled and failed retirement separately", async () => {
    versions[0]!.status = "APPROVED";
    await render();
    confirm.mockResolvedValueOnce({ isConfirmed: false });
    await clickButton(wrapper, "Retire version");
    expect(writes()).toHaveLength(0);
    failWrite = true;
    await clickButton(wrapper, "Retire version");
    expect(context.showError).toHaveBeenCalledWith(
      "Curriculum version could not be retired",
      "Unavailable",
    );
  });
  it("makes retired placements historical and hides amendment/approval actions", async () => {
    versions[0]!.status = "RETIRED";
    await render();
    expect(wrapper.text()).toContain("Historical");
    for (const label of ["Edit", "Remove", "Add Module", "Approve curriculum", "Retire version"])
      expect(wrapper.findAll("button").some((button) => button.text() === label)).toBe(false);
  });
  it.each(["none", "attached"] as const)(
    "explains unavailable Module prerequisite: %s",
    async (kind) => {
      overview.value!.modules = kind === "none" ? [] : [overview.value!.modules[0]!];
      await render();
      expect(wrapper.text()).toContain(
        kind === "none"
          ? "Create and activate at least one Module"
          : "Every active Module is already attached",
      );
      await clickButton(wrapper, "Open Modules");
      expect(context.navigateTo).toHaveBeenCalledWith("/operations/modules");
    },
  );
  it("prevents approval of an empty curriculum through guided action eligibility", async () => {
    placements = [];
    await render();
    expect(wrapper.text()).toContain("No Modules in this version");
    expect(wrapper.text()).toContain("Add at least one Module");
    await clickButton(wrapper, "Approve curriculum");
    expect(confirmAction).not.toHaveBeenCalled();
    expect(writes()).toHaveLength(0);
  });
});
