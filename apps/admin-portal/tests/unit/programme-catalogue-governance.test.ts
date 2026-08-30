// Author: Tinashe K
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import ProgrammePage from "../../pages/operations/programmes.vue";
import {
  clickButton,
  operationalContext,
  operationalStubs,
  setField,
} from "../../../../tests/unit/support/operational-page";
import { registerStubs } from "../../../../tests/unit/support/register-page";
const { confirm } = vi.hoisted(() => ({ confirm: vi.fn() }));
vi.mock("sweetalert2", () => ({ default: { fire: confirm } }));
const catalogue = () => ({
  academicUnitTypes: [
    { id: "department-type", status: "ACTIVE", leafAllowed: true },
    { id: "faculty-type", status: "ACTIVE", leafAllowed: false },
    { id: "inactive-type", status: "INACTIVE", leafAllowed: true },
  ],
  academicUnits: [
    {
      id: "faculty",
      code: "SCI",
      name: "Science",
      parentId: null,
      academicUnitTypeId: "faculty-type",
      status: "ACTIVE",
    },
    {
      id: "department",
      code: "CS",
      name: "Computing",
      parentId: "faculty",
      academicUnitTypeId: "department-type",
      status: "ACTIVE",
    },
    {
      id: "inactive",
      code: "OLD",
      name: "Old Department",
      parentId: null,
      academicUnitTypeId: "department-type",
      status: "INACTIVE",
    },
    {
      id: "invalid-type",
      code: "OLDTYPE",
      name: "Inactive hierarchy",
      parentId: null,
      academicUnitTypeId: "inactive-type",
      status: "ACTIVE",
    },
  ],
  programmeLevels: [
    { id: "ug", code: "UG", name: "Undergraduate", status: "ACTIVE", sortOrder: 1, version: 2 },
    {
      id: "old-level",
      code: "OLD",
      name: "Old Level",
      status: "INACTIVE",
      sortOrder: 2,
      version: 3,
    },
  ],
  programmeTypes: [
    { id: "degree", code: "DEG", name: "Degree", status: "ACTIVE", version: 5 },
    { id: "old-type", code: "OLD", name: "Old Type", status: "INACTIVE", version: 2 },
  ],
  programmes: [
    {
      id: "programme",
      code: "BSC",
      name: "Computing",
      awardName: "Bachelor of Science",
      owningAcademicUnitId: "department",
      owningAcademicUnitName: "Computing Department",
      programmeTypeId: "degree",
      programmeLevelId: "ug",
      programmeLevelName: "Undergraduate",
      status: "DRAFT",
      minimumDurationPeriods: 8,
      maximumDurationPeriods: 12,
      legacyProgrammeCode: null as string | null,
      version: 7,
    },
  ],
});
let wrapper: VueWrapper;
let context: ReturnType<typeof operationalContext>;
const overview = ref<ReturnType<typeof catalogue> | null>(null),
  loadError = ref("");
const ensureOverview = vi.fn(),
  loadOverview = vi.fn(),
  confirmAction = vi.fn();
beforeEach(() => {
  vi.resetAllMocks();
  context = operationalContext();
  overview.value = catalogue();
  loadError.value = "";
  confirm.mockResolvedValue({ isConfirmed: true });
  confirmAction.mockResolvedValue(true);
  context.request.mockResolvedValue({});
  vi.stubGlobal("useAcademicSetup", () => ({
    overview,
    loadError,
    loading: ref(false),
    ensureOverview,
    loadOverview,
  }));
  vi.stubGlobal("useEmhareConfirm", () => ({ confirmAction, showError: context.showError }));
});
afterEach(() => {
  wrapper?.unmount();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
async function render(tab?: string) {
  wrapper = mount(ProgrammePage, {
    global: {
      stubs: { ...registerStubs, EmhareRecordDrawer: operationalStubs.EmhareRecordDrawer },
    },
  });
  await flushPromises();
  if (tab) await clickButton(wrapper, tab);
}
async function submit(id = "programme-form") {
  await wrapper.get(`#${id}`).trigger("submit");
  await flushPromises();
}
const field = (label: string) =>
  wrapper.findAll(".field").find((element) => element.attributes("data-label") === label)!;
async function fillProgramme() {
  for (const [label, value] of [
    ["Programme code", "BTECH"],
    ["Programme name", "Technology"],
    ["Award name", "Bachelor of Technology"],
    ["Minimum duration (years)", "3.5"],
    ["Maximum duration (years)", "5"],
  ] as const)
    await setField(wrapper, label, value);
}
describe("programme catalogue lifecycle and controlled references", () => {
  it("renders counts, year-based duration, statuses and programme-specific curriculum links", async () => {
    overview.value!.programmes.push(
      { ...overview.value!.programmes[0]!, id: "active", status: "ACTIVE" },
      { ...overview.value!.programmes[0]!, id: "inactive", status: "INACTIVE" },
      { ...overview.value!.programmes[0]!, id: "retired", status: "RETIRED" },
    );
    await render();
    expect(wrapper.text()).toContain("Programmes: 4");
    expect(wrapper.text()).toContain("Active: 1");
    expect(wrapper.text()).toContain("4–6 years");
    expect(wrapper.findAll("button").filter((button) => button.text() === "Activate")).toHaveLength(
      1,
    );
    expect(
      wrapper
        .findAll("button")
        .find((button) => button.text() === "Curriculum")!
        .attributes("to"),
    ).toBe("/operations/curriculum?programmeId=programme");
    await clickButton(wrapper, "Refresh");
    expect(loadOverview).toHaveBeenCalledOnce();
  });
  it.each(["bsc", "computing", "department"])(
    "filters programmes by %s and restores cleared results",
    async (query) => {
      await render();
      const search = wrapper.get('input[placeholder="Search the current register"]');
      await search.setValue(` ${query} `);
      expect(wrapper.text()).toContain("Bachelor of Science");
      await search.setValue("missing");
      expect(wrapper.text()).toContain("No programmes configured");
      await search.setValue("");
      expect(wrapper.text()).toContain("Bachelor of Science");
    },
  );
  it("blocks creation while overview is unavailable and shows shared load errors", async () => {
    overview.value = null;
    loadError.value = "Offline";
    ensureOverview.mockRejectedValue(new Error("Offline"));
    await render();
    expect(wrapper.text()).toContain("Programme catalogue unavailable");
    await clickButton(wrapper, "New programme");
    expect(confirm).toHaveBeenCalledWith(
      expect.objectContaining({ title: "Programme setup is still loading" }),
    );
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
  });
  it.each(["hierarchy", "unit", "level", "type"] as const)(
    "guides missing %s prerequisites to their owning screen",
    async (missing) => {
      if (missing === "hierarchy") overview.value!.academicUnitTypes = [];
      if (missing === "unit") overview.value!.academicUnits = [];
      if (missing === "level") overview.value!.programmeLevels = [];
      if (missing === "type") overview.value!.programmeTypes = [];
      await render();
      expect(wrapper.text()).toContain("Programme setup required");
      await clickButton(wrapper, "New programme");
      expect(confirm).toHaveBeenCalledWith(
        expect.objectContaining({ title: "Complete programme setup" }),
      );
      if (missing === "hierarchy" || missing === "unit")
        expect(context.navigateTo).toHaveBeenCalledWith("/operations/academic-structure");
      else
        expect(
          wrapper.findAll("button").some((button) => button.text() === `New programme ${missing}`),
        ).toBe(true);
      expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    },
  );
  it("does not navigate or create when prerequisite guidance is cancelled", async () => {
    overview.value!.programmeLevels = [];
    confirm.mockResolvedValue({ isConfirmed: false });
    await render();
    await clickButton(wrapper, "New programme");
    expect(context.navigateTo).not.toHaveBeenCalled();
    expect(context.request).not.toHaveBeenCalled();
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
  });
  it("offers only active leaf owners, active levels and active classifications", async () => {
    overview.value!.academicUnits.push({
      id: "child",
      code: "CH",
      name: "Child",
      parentId: "department",
      academicUnitTypeId: "department-type",
      status: "ACTIVE",
    });
    await render();
    await clickButton(wrapper, "New programme");
    const owners = field("Owning academic unit").text();
    expect(owners).toContain("Child");
    for (const excluded of ["Science", "Computing", "Old Department", "Inactive hierarchy"])
      expect(owners).not.toContain(excluded);
    expect(field("Programme level").text()).not.toContain("Old Level");
    expect(field("Programme type").text()).not.toContain("Old Type");
  });
  it.each([false, true])(
    "creates a draft with year-to-period conversion and legacy mapping present=%s",
    async (legacy) => {
      await render();
      await clickButton(wrapper, "New programme");
      await fillProgramme();
      if (legacy) await setField(wrapper, "Legacy programme code", "OLD-BSC");
      await submit();
      expect(context.request).toHaveBeenCalledWith("/api/academic/programmes", {
        method: "POST",
        body: {
          owningAcademicUnitId: "department",
          programmeTypeId: "degree",
          programmeLevelId: "ug",
          code: "BTECH",
          name: "Technology",
          awardName: "Bachelor of Technology",
          minimumDurationPeriods: 7,
          maximumDurationPeriods: 10,
          legacyProgrammeCode: legacy ? "OLD-BSC" : null,
        },
      });
      expect(loadOverview).toHaveBeenCalledOnce();
      expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
      expect(
        wrapper.get<HTMLInputElement>('input[placeholder="Search the current register"]').element
          .value,
      ).toBe("BTECH");
      expect(context.notify).toHaveBeenCalledWith(
        expect.objectContaining({ title: "Programme created in draft" }),
      );
    },
  );
  it.each(["DRAFT", "ACTIVE"])(
    "edits %s programmes with appropriate identity locks and version evidence",
    async (status) => {
      overview.value!.programmes[0]!.status = status;
      overview.value!.programmes[0]!.legacyProgrammeCode = "LEGACY";
      await render();
      await clickButton(wrapper, "Edit");
      expect(field("Minimum duration (years)").get("input").element.value).toBe("4");
      expect(field("Maximum duration (years)").get("input").element.value).toBe("6");
      expect(field("Legacy programme code").get("input").element.value).toBe("LEGACY");
      for (const label of ["Programme code", "Owning academic unit"])
        expect(field(label).get("input,select").attributes("disabled") !== undefined).toBe(
          status !== "DRAFT",
        );
      if (status === "ACTIVE") expect(wrapper.text()).toContain("Operational identity locked");
      await setField(wrapper, "Programme name", "Revised Computing");
      await setField(wrapper, "Change reason", "Committee-approved correction");
      await submit();
      expect(context.request).toHaveBeenCalledWith("/api/academic/programmes/programme", {
        method: "PUT",
        body: expect.objectContaining({
          code: "BSC",
          name: "Revised Computing",
          changeReason: "Committee-approved correction",
          expectedVersion: 7,
          minimumDurationPeriods: 8,
          maximumDurationPeriods: 12,
        }),
      });
      expect(context.notify).toHaveBeenCalledWith(
        expect.objectContaining({ title: "Programme updated" }),
      );
    },
  );
  it.each([false, true])(
    "preserves failed programme form for retry editing=%s",
    async (editing) => {
      await render();
      await clickButton(wrapper, editing ? "Edit" : "New programme");
      await setField(wrapper, "Programme name", "Retain me");
      context.request.mockRejectedValue(new Error("Version conflict"));
      await submit();
      expect(context.showError).toHaveBeenCalledWith(
        `Programme could not be ${editing ? "updated" : "created"}`,
        "Version conflict",
      );
      expect(field("Programme name").get("input").element.value).toBe("Retain me");
      await clickButton(wrapper, "Cancel");
      expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    },
  );
  it("requires confirmation to activate and sends the current catalogue version", async () => {
    await render();
    confirmAction.mockResolvedValueOnce(false);
    await clickButton(wrapper, "Activate");
    expect(context.request).not.toHaveBeenCalled();
    await clickButton(wrapper, "Activate");
    expect(context.request).toHaveBeenCalledWith("/api/academic/programmes/programme/activate", {
      method: "POST",
      body: { expectedVersion: 7 },
    });
    expect(loadOverview).toHaveBeenCalledOnce();
    expect(context.notify).toHaveBeenCalledWith(
      expect.objectContaining({ title: "Programme activated" }),
    );
  });
  it("surfaces activation rejection when an approved curriculum is missing", async () => {
    context.request.mockRejectedValue(new Error("Approved curriculum required"));
    await render();
    await clickButton(wrapper, "Activate");
    expect(context.showError).toHaveBeenCalledWith(
      "Programme could not be activated",
      "Approved curriculum required",
    );
    expect(loadOverview).not.toHaveBeenCalled();
  });
  it.each(["level", "type"] as const)(
    "creates a controlled programme %s and filters to the saved code",
    async (kind) => {
      await render(kind === "level" ? "Programme levels" : "Programme types");
      await clickButton(wrapper, `New programme ${kind}`);
      await setField(wrapper, "Code", "NEW");
      await setField(wrapper, "Name", "New reference");
      if (kind === "level") await setField(wrapper, "Sort order", "4");
      await submit("programme-reference-form");
      expect(context.request).toHaveBeenCalledWith(`/api/academic/programme-${kind}s`, {
        method: "POST",
        body:
          kind === "level"
            ? { code: "NEW", name: "New reference", sortOrder: 4 }
            : { code: "NEW", name: "New reference" },
      });
      expect(
        wrapper.get<HTMLInputElement>('input[placeholder="Search the current register"]').element
          .value,
      ).toBe("NEW");
      expect(wrapper.text()).toContain(`No programme ${kind}s match this view`);
    },
  );
  it.each(["level", "type"] as const)(
    "updates programme %s names while locking codes and passing version",
    async (kind) => {
      await render(kind === "level" ? "Programme levels" : "Programme types");
      await clickButton(wrapper, "Edit");
      expect(field("Code").get("input").attributes("disabled")).toBeDefined();
      await setField(wrapper, "Name", "Revised reference");
      if (kind === "level") await setField(wrapper, "Sort order", "3");
      await submit("programme-reference-form");
      expect(context.request).toHaveBeenCalledWith(
        `/api/academic/programme-${kind}s/${kind === "level" ? "ug" : "degree"}`,
        {
          method: "PUT",
          body:
            kind === "level"
              ? { name: "Revised reference", sortOrder: 3, expectedVersion: 2 }
              : { name: "Revised reference", expectedVersion: 5 },
        },
      );
      expect(context.notify).toHaveBeenCalledWith(
        expect.objectContaining({ title: `Programme ${kind} updated` }),
      );
    },
  );
  it.each([
    ["level", false],
    ["level", true],
    ["type", false],
    ["type", true],
  ] as const)("retains failed %s reference form editing=%s", async (kind, editing) => {
    await render(kind === "level" ? "Programme levels" : "Programme types");
    await clickButton(wrapper, editing ? "Edit" : `New programme ${kind}`);
    await setField(wrapper, "Name", "Retained reference");
    context.request.mockRejectedValue(new Error("Unavailable"));
    await submit("programme-reference-form");
    expect(context.showError).toHaveBeenCalledWith(
      `Programme ${kind} could not be ${editing ? "updated" : "created"}`,
      "Unavailable",
    );
    expect(field("Name").get("input").element.value).toBe("Retained reference");
    await clickButton(wrapper, "Cancel");
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
  });
  it.each([
    ["Programme levels", "undergraduate"],
    ["Programme types", "degree"],
  ] as const)("searches %s by controlled name", async (tab, query) => {
    await render(tab);
    await wrapper.get('input[placeholder="Search the current register"]').setValue(` ${query} `);
    expect(wrapper.get("tbody").text().toLowerCase()).toContain(query);
    expect(wrapper.get("tbody").text()).not.toContain("Old");
  });
});
