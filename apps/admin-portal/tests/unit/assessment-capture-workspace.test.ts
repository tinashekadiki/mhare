// Author: Tinashe K
import { flushPromises, type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import CapturePage from "../../pages/operations/assessment-capture.vue";
import {
  clickButton,
  operationalContext,
  renderOperationalPage,
  setField,
} from "../../../../tests/unit/support/operational-page";

const { fire } = vi.hoisted(() => ({ fire: vi.fn() }));
vi.mock("sweetalert2", () => ({ default: { fire } }));
let context: ReturnType<typeof operationalContext>;
let wrapper: VueWrapper;
let roster: any[];
const component = {
  id: "component",
  code: "TEST",
  name: "Test",
  weightPercent: 40,
  maximumMark: 100,
  captureOpensAt: "2026-08-01T00:00:00Z",
  captureClosesAt: "2026-12-01T00:00:00Z",
};
const offering = {
  id: "offering",
  moduleCode: "CSC101",
  moduleName: "Computing",
  academicPeriodId: "period-current",
  academicPeriodCode: "2026-S1",
  schemes: [
    { status: "DRAFT", components: [] },
    { status: "APPROVED", components: [component] },
  ],
};
const calculation = {
  id: "calculation",
  initiatedAt: "2026-08-30T00:00:00Z",
  completeResultCount: 1,
  incompleteResultCount: 1,
  outcomes: [
    { rosterEntryId: "one", studentNumber: "R0001", complete: true, weightedTotal: 75 },
    {
      rosterEntryId: "two",
      studentNumber: "R0002",
      complete: false,
      missingComponentCodes: "EXAM",
    },
  ],
};

beforeEach(() => {
  vi.resetAllMocks();
  context = operationalContext();
  roster = [
    {
      rosterEntryId: "one",
      studentNumber: "R0001",
      status: "CAPTURED",
      score: 70,
      markId: "mark-one",
      markVersion: 4,
      revisionNumber: 1,
    },
    {
      rosterEntryId: "two",
      studentNumber: "R0002",
      status: "SUBMITTED",
      score: 60,
      markId: "mark-two",
      markVersion: 2,
      revisionNumber: 2,
    },
    {
      rosterEntryId: "three",
      studentNumber: "R0003",
      status: null,
      score: null,
      markId: null,
      markVersion: 0,
    },
  ];
  context.request.mockImplementation(async (path: string) => {
    if (path.endsWith("/offerings"))
      return [
        offering,
        { ...offering, id: "other-period", academicPeriodId: "other" },
        { ...offering, id: "unapproved", schemes: [] },
      ];
    if (path.endsWith("/roster")) return roster;
    if (path.endsWith("/calculations")) return calculation;
    return {};
  });
  fire.mockResolvedValue({ isConfirmed: true, value: " Evidence corrected " });
});
afterEach(() => {
  wrapper?.unmount();
  vi.unstubAllGlobals();
});

async function openCapture() {
  wrapper = await renderOperationalPage(CapturePage);
  await setField(wrapper, "Module offering", "offering");
  await setField(wrapper, "Assessment component", "component");
}

describe("assessment capture workspace", () => {
  it("offers only approved schemes in the selected period and resets selection when scope changes", async () => {
    await openCapture();
    expect(
      wrapper
        .findAll("select")[0]!
        .findAll("option")
        .map((option) => option.attributes("value")),
    ).toEqual(["", "offering"]);
    expect(wrapper.text()).toContain("Capture window:");
    expect(wrapper.text()).toContain("NOT CAPTURED");
    expect(wrapper.findAll("tbody input")[1]!.attributes("disabled")).toBeDefined();
    context.selectedAcademicPeriodId.value = "other";
    await flushPromises();
    expect(wrapper.find("tbody").exists()).toBe(false);
    expect(context.request.mock.calls.filter(([path]) => path.endsWith("/offerings"))).toHaveLength(
      2,
    );
  });

  it("saves only editable entered marks with optimistic versions and refreshes their state", async () => {
    await openCapture();
    await wrapper.findAll("tbody input")[0]!.setValue("72");
    await wrapper.findAll("tbody input")[2]!.setValue("0");
    await clickButton(wrapper, "Save captured marks");
    expect(context.request).toHaveBeenCalledWith(
      "/api/assessment-results/components/component/marks",
      {
        method: "POST",
        body: {
          captureMethod: "MANUAL",
          marks: [
            { rosterEntryId: "one", score: 72, expectedVersion: 4 },
            { rosterEntryId: "three", score: 0, expectedVersion: 0 },
          ],
        },
      },
    );
    expect(context.notify).toHaveBeenCalledWith(
      expect.objectContaining({ description: "2 marks remain editable until submission." }),
    );
  });

  it("handles one editable mark and refuses empty capture submissions", async () => {
    await openCapture();
    await clickButton(wrapper, "Save captured marks");
    expect(context.notify).toHaveBeenCalledWith(
      expect.objectContaining({ description: "1 mark remain editable until submission." }),
    );
    roster = [];
    await setField(wrapper, "Assessment component", "");
    await setField(wrapper, "Assessment component", "component");
    expect(wrapper.text()).toContain("No eligible students");
    await clickButton(wrapper, "Save captured marks");
    expect(context.showError).toHaveBeenCalledWith("No marks to save", expect.any(String));
  });

  it.each([true, false])(
    "requires confirmation before locking a mark: confirmed=%s",
    async (confirmed) => {
      await openCapture();
      fire.mockResolvedValue({ isConfirmed: confirmed });
      await clickButton(wrapper, "Submit");
      expect(
        context.request.mock.calls.some(([path]) => path.includes("/submit?expectedVersion=4")),
      ).toBe(confirmed);
      expect(
        wrapper
          .findAll("button")
          .find((button) => button.text() === "Submit")
          ?.attributes("aria-busy"),
      ).toBe("false");
    },
  );

  it("requires a persisted mark before submission or amendment", async () => {
    roster[0].markId = null;
    roster[1].markId = null;
    await openCapture();
    await clickButton(wrapper, "Submit");
    await clickButton(wrapper, "Request amendment");
    expect(fire).not.toHaveBeenCalled();
  });

  it("requests an amendment with a bounded score and trimmed supporting reason", async () => {
    await openCapture();
    fire
      .mockResolvedValueOnce({ isConfirmed: true, value: "65" })
      .mockResolvedValueOnce({ isConfirmed: true, value: " Corrected source sheet " });
    await clickButton(wrapper, "Request amendment");
    const scoreValidator = fire.mock.calls[0]![0].inputValidator;
    for (const value of ["", "-1", "101"]) expect(scoreValidator(value)).toContain("within");
    for (const value of ["0", "100"]) expect(scoreValidator(value)).toBeUndefined();
    const reasonValidator = fire.mock.calls[1]![0].inputValidator;
    expect(reasonValidator(" ")).toContain("required");
    expect(reasonValidator("Evidence")).toBeUndefined();
    expect(context.request).toHaveBeenCalledWith(
      "/api/assessment-results/marks/mark-two/amendments",
      { method: "POST", body: { proposedScore: 65, reason: "Corrected source sheet" } },
    );
  });

  it.each(["score-cancel", "reason-cancel", "reason-empty", "reason-missing"])(
    "does not mutate after %s",
    async (mode) => {
      await openCapture();
      fire
        .mockResolvedValueOnce({ isConfirmed: mode !== "score-cancel", value: "65" })
        .mockResolvedValueOnce({
          isConfirmed: mode !== "reason-cancel",
          value: mode === "reason-missing" ? undefined : " ",
        });
      await clickButton(wrapper, "Request amendment");
      expect(context.request.mock.calls.some(([path]) => path.endsWith("/amendments"))).toBe(false);
    },
  );

  it.each([0, 1])(
    "shows immutable calculation evidence and warns for %s incomplete results",
    async (incomplete) => {
      await openCapture();
      context.request.mockResolvedValueOnce({ ...calculation, incompleteResultCount: incomplete });
      await clickButton(wrapper, "Run aggregate calculation");
      expect(wrapper.text()).toContain("75%");
      expect(wrapper.text()).toContain("Missing EXAM");
      expect(context.notify).toHaveBeenCalledWith(
        expect.objectContaining({ color: incomplete ? "warning" : "success" }),
      );
    },
  );

  it.each([
    ["Save captured marks", "Marks could not be saved"],
    ["Submit", "Mark could not be submitted"],
    ["Request amendment", "Amendment request could not be recorded"],
    ["Run aggregate calculation", "Aggregate calculation could not run"],
  ])("surfaces API failures for %s without success notification", async (button, title) => {
    await openCapture();
    context.request.mockRejectedValueOnce(new Error("Service unavailable"));
    await clickButton(wrapper, button);
    expect(context.showError).toHaveBeenCalledWith(title, "Service unavailable");
    expect(context.notify).not.toHaveBeenCalled();
  });

  it("reports offering and roster loading failures and supports refresh", async () => {
    context.request.mockRejectedValueOnce(new Error("Offerings offline"));
    wrapper = await renderOperationalPage(CapturePage);
    expect(context.showError).toHaveBeenCalledWith(
      "Assessment offerings could not be loaded",
      "Offerings offline",
    );
    await clickButton(wrapper, "Refresh");
    await setField(wrapper, "Module offering", "offering");
    context.request.mockRejectedValueOnce(new Error("Roster offline"));
    await setField(wrapper, "Assessment component", "component");
    expect(context.showError).toHaveBeenCalledWith(
      "Mark capture roster could not be loaded",
      "Roster offline",
    );
  });
});
