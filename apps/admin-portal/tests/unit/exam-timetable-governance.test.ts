// Author: Tinashe K
import { flushPromises, type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Swal from "sweetalert2";
import Timetables from "../../pages/operations/exam-timetables.vue";
import {
  clickButton,
  operationalContext,
  renderOperationalPage,
  setField,
} from "../../../../tests/unit/support/operational-page";

vi.mock("sweetalert2", () => ({ default: { fire: vi.fn() } }));
const fire = vi.mocked(Swal.fire);
let context: ReturnType<typeof operationalContext>;
let wrapper: VueWrapper;
const session = {
  id: "session",
  academicPeriodId: "period-current",
  academicPeriodCode: "2026-S1",
  code: "MAIN",
  name: "Main exams",
  status: "APPROVED",
};
const run = (status: string) => ({
  id: status,
  examSessionId: "session",
  runNumber: `RUN-${status}`,
  sessionCode: "MAIN",
  sessionName: "Main exams",
  status,
  generatedAt: "2026-08-20T10:00:00Z",
  generationPolicy: { algorithm: "DETERMINISTIC" },
  candidateCount: 20,
  timetableEntryCount: 1,
  moduleCount: 1,
  conflictCount: status === "REJECTED" ? 2 : 0,
  version: 4,
  generatedByUserId: "generator",
  reviewedByUserId: status === "GENERATED" ? null : "reviewer",
  approvedByUserId: ["APPROVED", "PUBLISHED"].includes(status) ? "approver" : null,
  publishedByUserId: status === "PUBLISHED" ? "publisher" : null,
  entries: [
    {
      id: "entry",
      moduleCode: "CSC101",
      moduleName: "Programming",
      candidateCount: 20,
      slotCode: "AM",
      startsAt: "2026-09-01T08:00:00Z",
      endsAt: "2026-09-01T11:00:00Z",
      venues: [{ id: "venue", venueCode: "HALL", allocatedCapacity: 20 }],
    },
  ],
});
let runs: ReturnType<typeof run>[];
beforeEach(() => {
  vi.resetAllMocks();
  context = operationalContext();
  runs = ["GENERATED", "REVIEWED", "APPROVED", "PUBLISHED", "REJECTED"].map(run);
  context.request.mockImplementation(async (path: string, options?: { method?: string }) => {
    if (options?.method) return {};
    if (path === "/api/timetabling/runs")
      return [...runs, { ...run("FOREIGN"), examSessionId: "foreign" }];
    return {
      sessions: [
        session,
        { ...session, id: "draft", status: "DRAFT" },
        { ...session, id: "foreign", academicPeriodId: "old" },
      ],
      requirements: [{ academicPeriodId: "period-current" }, { academicPeriodId: "old" }],
      venues: [],
      venueTypes: [],
    };
  });
  fire.mockResolvedValue({ isConfirmed: true, value: "  Independently verified  " } as never);
});
afterEach(() => {
  wrapper?.unmount();
  vi.unstubAllGlobals();
});
async function render() {
  wrapper = await renderOperationalPage(Timetables);
  await flushPromises();
}
const transitions = [
  { label: "Review", status: "GENERATED", action: "review" },
  { label: "Approve", status: "REVIEWED", action: "approve" },
  { label: "Publish", status: "APPROVED", action: "publish" },
  { label: "Reject", status: "GENERATED", action: "reject" },
];

describe("Exam timetable governance", () => {
  it("shows current-period allocation evidence and only actions allowed by each state", async () => {
    await render();
    expect(wrapper.text()).not.toContain("RUN-FOREIGN");
    expect(wrapper.text()).toContain("CSC101");
    expect(wrapper.text()).toContain("HALL · 20");
    expect(wrapper.text()).toContain("reviewer reviewer");
    expect(wrapper.text()).toContain("publisher publisher");
    expect(wrapper.findAll("button").filter((button) => button.text() === "Reject")).toHaveLength(
      3,
    );
    await wrapper
      .get('input[placeholder="Search run or exam session"]')
      .setValue(" run-published ");
    expect(wrapper.text()).toContain("RUN-PUBLISHED");
    expect(wrapper.text()).not.toContain("RUN-GENERATED");
    await wrapper.get("select").setValue("REJECTED");
    expect(wrapper.text()).toContain("No timetable runs match");
    await wrapper.get("input").setValue("");
    expect(wrapper.text()).toContain("RUN-REJECTED");
  });
  it.each(transitions)(
    "records $action with immutable identity, trimmed reason and expected version",
    async (scenario) => {
      await render();
      await clickButton(wrapper, scenario.label);
      expect(context.request).toHaveBeenCalledWith(
        `/api/timetabling/runs/${scenario.status}/${scenario.action}`,
        { method: "POST", body: { reason: "Independently verified", expectedVersion: 4 } },
      );
      const validator = (
        fire.mock.calls[0]![0] as unknown as { inputValidator: (value: string) => unknown }
      ).inputValidator;
      expect(validator(" ")).toBe("A workflow reason is required.");
      expect(validator("Evidence")).toBeUndefined();
      expect(context.notify).toHaveBeenCalled();
    },
  );
  it.each([
    { isConfirmed: false, value: "Reason" },
    { isConfirmed: true, value: "" },
    { isConfirmed: true, value: "  " },
    { isConfirmed: true },
  ])("does not advance a run without confirmation and a reason: %j", async (result) => {
    fire.mockResolvedValue(result as never);
    await render();
    await clickButton(wrapper, "Review");
    expect(context.request.mock.calls.some(([, options]) => options?.method)).toBe(false);
  });
  it("requires an approved session and explicit confirmation before generation", async () => {
    await render();
    await clickButton(wrapper, "Generate timetable");
    await clickButton(wrapper, "Generate timetable", 1);
    expect(fire).not.toHaveBeenCalled();
    expect(
      wrapper
        .get('[role="dialog"] select')
        .findAll("option")
        .map((option) => option.attributes("value")),
    ).toEqual(["", "session"]);
    await setField(wrapper, "Approved exam session", "session");
    fire.mockResolvedValueOnce({ isConfirmed: false } as never);
    await clickButton(wrapper, "Generate timetable", 1);
    expect(context.request).not.toHaveBeenCalledWith(
      "/api/timetabling/runs",
      expect.objectContaining({ method: "POST" }),
    );
    await clickButton(wrapper, "Generate timetable", 1);
    expect(context.request).toHaveBeenCalledWith("/api/timetabling/runs", {
      method: "POST",
      body: { examSessionId: "session" },
    });
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
  });
  it.each(transitions)(
    "reports a failed $action without a success notification",
    async (scenario) => {
      await render();
      context.request.mockRejectedValueOnce(new Error("Independent actor required"));
      await clickButton(wrapper, scenario.label);
      expect(context.showError).toHaveBeenCalledWith(
        expect.stringContaining("failed"),
        "Independent actor required",
      );
      expect(context.notify).not.toHaveBeenCalled();
    },
  );
  it("keeps failed generation open for correction and supports cancellation", async () => {
    await render();
    await clickButton(wrapper, "Generate timetable");
    await setField(wrapper, "Approved exam session", "session");
    context.request.mockRejectedValueOnce(new Error("Candidate clash"));
    await clickButton(wrapper, "Generate timetable", 1);
    expect(context.showError).toHaveBeenCalledWith(
      "Timetable could not be generated",
      "Candidate clash",
    );
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true);
    await clickButton(wrapper, "Cancel");
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
  });
  it("reports load errors and recovers through Refresh", async () => {
    context.request.mockRejectedValueOnce(new Error("Unavailable"));
    await render();
    expect(context.showError).toHaveBeenCalledWith(
      "Exam timetables could not be loaded",
      "Unavailable",
    );
    await clickButton(wrapper, "Refresh");
    expect(wrapper.text()).toContain("RUN-GENERATED");
  });
});
