// Author: Tinashe K
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import InvigilationPage from "../../pages/operations/exam-invigilation.vue";
import type {
  ExamVenueOperationSummary,
  ExamAttendanceSessionSummary,
  ExamIncidentSummary,
} from "../../../../packages/portal-shell/types/exams";
import {
  clickButton,
  operationalContext,
  setField,
} from "../../../../tests/unit/support/operational-page";
import { registerStubs } from "../../../../tests/unit/support/register-page";
const { confirm } = vi.hoisted(() => ({ confirm: vi.fn() }));
vi.mock("sweetalert2", () => ({ default: { fire: confirm } }));
let wrapper: VueWrapper,
  context: ReturnType<typeof operationalContext>,
  operations: ExamVenueOperationSummary[],
  failPath: string | undefined;
const stamp = "2026-08-01T08:00:00Z";
const attendance: ExamAttendanceSessionSummary = {
  id: "attendance",
  status: "OPEN",
  expectedCandidateCount: 1,
  presentCandidateCount: 0,
  absentCandidateCount: 0,
  excusedCandidateCount: 0,
  outstandingCandidateCount: 1,
  openedByUserId: "invigilator",
  openedAt: stamp,
  openingReason: "Checked",
  version: 8,
  attendanceRecords: [
    {
      id: "record",
      studentTimetableEntryId: "entry",
      studentId: "student",
      studentNumber: "R260001",
      seatNumber: 1,
      attendanceStatus: "EXPECTED",
      version: 5,
    },
  ],
  incidents: [],
};
const operation: ExamVenueOperationSummary = {
  venueAllocationId: "allocation",
  generationRunId: "run",
  runNumber: "RUN-001",
  masterTimetableEntryId: "master",
  moduleCode: "CSC101",
  moduleName: "Computing",
  scheduledStartsAt: stamp,
  scheduledEndsAt: "2026-08-01T11:00:00Z",
  venueId: "venue",
  venueCode: "HALL",
  venueName: "Great hall",
  campusName: "Main",
  allocatedCandidateCount: 1,
  attendanceSession: attendance,
};
const incident: ExamIncidentSummary = {
  id: "incident",
  incidentNumber: "INC-001",
  incidentType: "MEDICAL",
  severity: "MEDIUM",
  description: "Candidate needed medical assistance",
  occurredAt: stamp,
  status: "REPORTED",
  reportedByUserId: "reporter",
  reportedAt: stamp,
  version: 6,
};
beforeEach(() => {
  vi.resetAllMocks();
  context = operationalContext();
  operations = [structuredClone(operation)];
  failPath = undefined;
  confirm.mockResolvedValue({ isConfirmed: true, value: "  Independent room evidence  " });
  context.request.mockImplementation(async (path: string, options?: { method?: string }) => {
    if (path === failPath) throw new Error("Unavailable");
    if (options?.method) return {};
    if (path === "/api/exams/invigilation") return { venueOperations: structuredClone(operations) };
    throw new Error(`Unexpected ${path}`);
  });
});
afterEach(() => {
  wrapper?.unmount();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
async function render() {
  wrapper = mount(InvigilationPage, { global: { stubs: registerStubs } });
  await flushPromises();
}
const writes = () => context.request.mock.calls.filter(([, options]) => options?.method);
async function rejectedConfirmation(button: string) {
  confirm.mockResolvedValueOnce({ isConfirmed: false });
  await clickButton(wrapper, button);
  confirm.mockResolvedValueOnce({ isConfirmed: true, value: " " });
  await clickButton(wrapper, button);
  expect(writes()).toHaveLength(0);
  const options = confirm.mock.calls[0]![0];
  expect(options.inputValidator(" ")).toBeTruthy();
  expect(options.inputValidator("Evidence provided")).toBeUndefined();
}
describe("exam invigilation controls", () => {
  it("filters room status and searches across Module, venue and published run fields", async () => {
    operations.push(
      {
        ...operation,
        venueAllocationId: "closed",
        moduleCode: "CLOSED",
        attendanceSession: { ...attendance, status: "CLOSED", closedAt: stamp },
      },
      {
        ...operation,
        venueAllocationId: "unopened",
        moduleCode: "UNOPENED",
        attendanceSession: null,
        allocatedCandidateCount: 2,
      },
    );
    await render();
    for (const [status, code] of [
      ["OPEN", "CSC101"],
      ["CLOSED", "CLOSED"],
      ["NOT_OPENED", "UNOPENED"],
    ] as const) {
      await wrapper.get("select").setValue(status);
      expect(wrapper.text()).toContain(code);
      expect(wrapper.findAll("h2").filter((heading) => heading.text().includes("·"))).toHaveLength(
        1,
      );
    }
    await wrapper.get("select").setValue("ALL");
    const search = wrapper.get('input[placeholder="Search Module, venue, or run"]');
    for (const query of ["  cSc101  ", "computing", "great hall", "hall", "run-001"]) {
      await search.setValue(query);
      expect(wrapper.text()).not.toContain("No room operations match");
    }
    await search.setValue("missing");
    expect(wrapper.text()).toContain("No room operations match");
  });
  it("recovers failed workspace loads", async () => {
    failPath = "/api/exams/invigilation";
    await render();
    expect(context.showError).toHaveBeenCalledWith(
      "Invigilation workspace could not be loaded",
      "Unavailable",
    );
    failPath = undefined;
    await clickButton(wrapper, "Refresh");
    expect(wrapper.text()).toContain("R260001");
  });
  it("opens the exact published allocation only with opening evidence", async () => {
    operations = [{ ...operation, attendanceSession: null }];
    await render();
    expect(wrapper.text()).toContain("1 published candidate seat");
    await rejectedConfirmation("Open register");
    await clickButton(wrapper, "Open register");
    expect(writes()[0]).toEqual([
      "/api/exams/invigilation/venue-allocations/allocation/attendance-session",
      { method: "POST", body: { openingReason: "Independent room evidence" } },
    ]);
  });
  it("records presence against the persisted attendance version with seat checks", async () => {
    await render();
    await clickButton(wrapper, "Present");
    expect(confirm).not.toHaveBeenCalled();
    expect(writes()[0]).toEqual([
      "/api/exams/invigilation/attendance-records/record",
      {
        method: "PUT",
        body: {
          attendanceStatus: "PRESENT",
          evidenceNotes:
            "Identity and examination admission evidence checked at the allocated seat.",
          expectedVersion: 5,
        },
      },
    ]);
  });
  it.each(["Absent", "Excused"])("requires auditable evidence for %s", async (button) => {
    await render();
    await rejectedConfirmation(button);
    await clickButton(wrapper, button);
    expect(writes()[0]).toEqual([
      "/api/exams/invigilation/attendance-records/record",
      {
        method: "PUT",
        body: {
          attendanceStatus: button.toUpperCase(),
          evidenceNotes: "Independent room evidence",
          expectedVersion: 5,
        },
      },
    ]);
  });
  it.each([1, 2])("blocks closure with %s outstanding outcomes", async (count) => {
    operations[0]!.attendanceSession!.outstandingCandidateCount = count;
    await render();
    await clickButton(wrapper, "Close reconciled register");
    expect(writes()).toHaveLength(0);
    expect(context.showError).toHaveBeenCalledWith(
      "Register cannot be closed",
      `${count} candidate outcome${count === 1 ? "" : "s"} remain expected.`,
    );
  });
  it("closes a fully reconciled register with its optimistic version", async () => {
    operations[0]!.attendanceSession!.outstandingCandidateCount = 0;
    await render();
    await rejectedConfirmation("Close reconciled register");
    await clickButton(wrapper, "Close reconciled register");
    expect(writes()[0]).toEqual([
      "/api/exams/invigilation/attendance-sessions/attendance/close",
      { method: "POST", body: { closureReason: "Independent room evidence", expectedVersion: 8 } },
    ]);
  });
  it("shows immutable closed attendance and all recorded outcome colours", async () => {
    operations[0]!.attendanceSession = {
      ...attendance,
      status: "CLOSED",
      closedAt: stamp,
      attendanceRecords: ["PRESENT", "ABSENT", "EXCUSED", "EXPECTED"].map((status, index) => ({
        ...attendance.attendanceRecords[0]!,
        id: `record-${index}`,
        attendanceStatus: status as "PRESENT" | "ABSENT" | "EXCUSED" | "EXPECTED",
        evidenceNotes: index === 0 ? "Identity checked" : null,
      })),
    };
    await render();
    expect(wrapper.text()).toContain("Locked");
    expect(wrapper.text()).toContain("Identity checked");
    expect(wrapper.text()).toContain("Awaiting invigilator evidence");
    expect(wrapper.findAll("button").map((button) => button.text())).not.toContain("Present");
    expect(wrapper.findAll("button").map((button) => button.text())).not.toContain(
      "Report incident",
    );
  });
  it.each([false, true])(
    "records original %s candidate incident evidence and occurrence time",
    async (candidate) => {
      await render();
      await clickButton(wrapper, "Report incident");
      await clickButton(wrapper, "Record incident");
      expect(writes()).toHaveLength(0);
      await setField(wrapper, "Factual incident description", "  Medical assistance provided  ");
      await setField(wrapper, "Occurred at", "");
      await clickButton(wrapper, "Record incident");
      expect(writes()).toHaveLength(0);
      await setField(wrapper, "Occurred at", "2026-08-01T09:15");
      await setField(wrapper, "Incident type", "MEDICAL");
      await setField(wrapper, "Severity", "HIGH");
      if (candidate) await setField(wrapper, "Candidate or room", "entry");
      await clickButton(wrapper, "Record incident");
      expect(writes()[0]).toEqual([
        "/api/exams/invigilation/attendance-sessions/attendance/incidents",
        {
          method: "POST",
          body: {
            studentTimetableEntryId: candidate ? "entry" : null,
            incidentType: "MEDICAL",
            severity: "HIGH",
            description: "Medical assistance provided",
            occurredAt: new Date("2026-08-01T09:15").toISOString(),
          },
        },
      ]);
      expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    },
  );
  it("resets a cancelled incident without writing", async () => {
    await render();
    await clickButton(wrapper, "Report incident");
    await setField(wrapper, "Factual incident description", "Discarded");
    await clickButton(wrapper, "Cancel");
    await clickButton(wrapper, "Report incident");
    expect(wrapper.get('[data-label="Factual incident description"] input').element).toHaveProperty(
      "value",
      "",
    );
    expect(writes()).toHaveLength(0);
  });
  it("renders severity, candidate context and only eligible incident decisions", async () => {
    operations[0]!.attendanceSession!.incidents = [
      incident,
      { ...incident, id: "high", severity: "HIGH", studentNumber: "R260001", status: "REVIEWED" },
      { ...incident, id: "critical", severity: "CRITICAL", status: "RESOLVED" },
      { ...incident, id: "low", severity: "LOW", status: "RESOLVED" },
    ];
    await render();
    expect(wrapper.findAll("article")).toHaveLength(4);
    expect(wrapper.findAll("article button").map((button) => button.text())).toEqual([
      "Review",
      "Resolve",
    ]);
    expect(wrapper.text()).toContain("CRITICAL");
    expect(wrapper.text()).toContain("LOW");
  });
  it.each([
    ["Review", "review", "REPORTED"],
    ["Resolve", "resolve", "REVIEWED"],
  ] as const)(
    "controls incident %s with independent authority and version",
    async (button, action, status) => {
      operations[0]!.attendanceSession!.incidents = [{ ...incident, status }];
      await render();
      await rejectedConfirmation(button);
      failPath = `/api/exams/invigilation/incidents/incident/${action}`;
      await clickButton(wrapper, button);
      expect(context.showError).toHaveBeenCalledWith(
        "Exam operation could not be completed",
        "Unavailable",
      );
      failPath = undefined;
      await clickButton(wrapper, button);
      expect(writes()[1]).toEqual([
        `/api/exams/invigilation/incidents/incident/${action}`,
        { method: "POST", body: { reason: "Independent room evidence", expectedVersion: 6 } },
      ]);
      expect(context.notify).toHaveBeenCalledWith(expect.objectContaining({ color: "success" }));
    },
  );
});
