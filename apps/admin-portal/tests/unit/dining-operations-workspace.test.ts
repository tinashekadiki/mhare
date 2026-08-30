// Author: Tinashe K
import { flushPromises, type VueWrapper } from "@vue/test-utils";
import { computed, ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Swal from "sweetalert2";
import DiningOperationsPage from "../../pages/operations/dining-operations.vue";
import {
  clickButton,
  operationalContext,
  setField,
} from "../../../../tests/unit/support/operational-page";
import { mountCampusPage } from "../../../../tests/unit/support/campus-page";

vi.mock("sweetalert2", () => ({ default: { fire: vi.fn() } }));
let context: ReturnType<typeof operationalContext>;
let wrapper: VueWrapper;
let register: ReturnType<typeof operationFixture>;
const today = new Date().toISOString().slice(0, 10);
const registration = {
  id: "registration",
  studentId: "student",
  studentNumber: "R260001A",
  studentName: "Registered Student",
  academicPeriodId: "period-current",
  academicPeriodCode: "CURRENT",
  programmeCode: "BSC",
  academicPeriodStartsOn: "2026-08-01",
  academicPeriodEndsOn: "2026-12-31",
  status: "CONFIRMED",
};
const session = {
  id: "OPEN",
  sessionNumber: "SESSION-OPEN",
  serviceDate: today,
  diningHallCode: "MAIN",
  mealOptionCode: "LUNCH",
  scheduledOpensAt: `${today}T11:00:00Z`,
  scheduledClosesAt: `${today}T13:00:00Z`,
  status: "OPEN",
  netAdmitted: 5,
  version: 4,
};
const attendance = {
  id: "attendance",
  eventNumber: "ATT-1",
  sessionId: "OPEN",
  sessionNumber: "SESSION-OPEN",
  studentId: "student",
  studentNumber: "R260001A",
  studentName: "Registered Student",
  outcome: "ADMITTED",
  denialReason: null,
  captureChannel: "ONLINE",
  capturedAt: `${today}T12:00:00Z`,
  reversed: false,
};
function operationFixture() {
  return {
    assignments: [
      ...["DRAFT", "ACTIVE", "SUSPENDED", "ENDED", "CANCELLED"].map((status) => ({
        id: status,
        assignmentNumber: `ASSIGN-${status}`,
        studentId: "student",
        studentNumber: "R260001A",
        studentName: "Registered Student",
        academicPeriodId: "period-current",
        academicPeriodCode: "CURRENT",
        diningHallCode: "MAIN",
        diningPlanCode: "MEALS",
        billingStatus: "PENDING",
        status,
        version: 3,
      })),
      {
        id: "hidden",
        assignmentNumber: "HIDDEN-ASSIGNMENT",
        studentId: "hidden",
        academicPeriodId: "other",
        status: "ACTIVE",
        billingStatus: "PENDING",
      },
    ],
    dietaryRequirements: [
      {
        id: "diet",
        studentId: "student",
        studentNumber: "R260001A",
        requirementCode: "NO-NUTS",
        description: "Nut allergy",
        severity: "CRITICAL",
        status: "ACTIVE",
        effectiveFrom: today,
        effectiveUntil: null,
        version: 2,
      },
      {
        id: "resolved",
        studentId: "student",
        studentNumber: "R260001A",
        requirementCode: "RESOLVED",
        description: "Resolved restriction",
        severity: "IMPORTANT",
        status: "RESOLVED",
        effectiveFrom: today,
        effectiveUntil: today,
        version: 2,
      },
      { id: "hidden-diet", studentId: "hidden", description: "Hidden allergy", status: "ACTIVE" },
    ],
    sessions: [
      session,
      ...["PLANNED", "CLOSED", "RECONCILED"].map((status) => ({
        ...session,
        id: status,
        sessionNumber: `SESSION-${status}`,
        status,
      })),
      { ...session, id: "old-session", sessionNumber: "HIDDEN-SESSION", serviceDate: "2025-01-01" },
    ],
    attendanceEvents: [
      attendance,
      { ...attendance, id: "reversed", eventNumber: "REVERSED-ATT", reversed: true },
      {
        ...attendance,
        id: "denied",
        eventNumber: "DENIED-ATT",
        outcome: "DENIED",
        denialReason: "No entitlement",
      },
      { ...attendance, id: "yesterday", capturedAt: "2026-01-01T12:00:00Z" },
      { ...attendance, id: "hidden-att", sessionId: "old-session", eventNumber: "HIDDEN-ATT" },
    ],
    reversals: [
      { id: "reversal", attendanceEventId: "reversed" },
      { id: "hidden-reversal", attendanceEventId: "hidden-att" },
    ],
    workflowEvents: [
      {
        id: "event",
        aggregateId: "ACTIVE",
        aggregateType: "ASSIGNMENT",
        eventType: "ACTIVATED",
        previousState: "DRAFT",
        newState: "ACTIVE",
        reason: "Approved",
        occurredAt: `${today}T11:00:00Z`,
      },
      {
        id: "created",
        aggregateId: "OPEN",
        aggregateType: "SESSION",
        eventType: "CREATED",
        previousState: null,
        newState: "OPEN",
        reason: "Created session",
        occurredAt: null,
      },
      {
        id: "hidden-event",
        aggregateId: "hidden",
        aggregateType: "ASSIGNMENT",
        eventType: "CREATED",
        previousState: null,
        newState: "ACTIVE",
        occurredAt: null,
        reason: "HIDDEN-EVENT",
      },
    ],
    attendanceStatistics: [
      {
        id: "stat",
        dimension: "ACADEMIC_PERIOD",
        groupCode: "CURRENT",
        admitted: 4,
        denied: 1,
        reversed: 1,
        netAdmitted: 3,
      },
      { id: "hidden-stat", dimension: "PROGRAMME", groupCode: "HIDDEN-STAT" },
      { id: "other-stat", dimension: "ACADEMIC_PERIOD", groupCode: "OTHER" },
    ],
  };
}
beforeEach(() => {
  vi.resetAllMocks();
  context = operationalContext();
  register = operationFixture();
  vi.stubGlobal("useAcademicPeriodContext", () => ({
    selectedAcademicPeriodId: context.selectedAcademicPeriodId,
    selectedAcademicPeriod: computed(() =>
      context.selectedAcademicPeriodId.value
        ? { startDate: "2026-08-01", endDate: "2026-12-31" }
        : null,
    ),
    selectedAcademicPeriodCode: ref("CURRENT"),
    ensureAcademicPeriods: vi.fn().mockResolvedValue(undefined),
    matchesAcademicPeriod: (record: { academicPeriodId: string }) =>
      !context.selectedAcademicPeriodId.value ||
      record.academicPeriodId === context.selectedAcademicPeriodId.value,
  }));
  vi.mocked(Swal.fire).mockResolvedValue({
    isConfirmed: true,
    value: "Approved operational evidence",
  } as any);
  context.request.mockImplementation(async (path: string, options?: { method?: string }) => {
    if (options?.method) return path.endsWith("/attendance") ? structuredClone(attendance) : {};
    if (path === "/api/dining/operations") return structuredClone(register);
    if (path === "/api/dining/setup")
      return {
        diningHalls: [
          { id: "hall", code: "MAIN", name: "Main hall", active: true },
          { id: "old-hall", name: "Inactive hall", active: false },
        ],
        mealOptions: [
          { id: "meal", code: "LUNCH", name: "Lunch", active: true },
          { id: "old-meal", name: "Inactive meal", active: false },
        ],
        diningPlans: [
          {
            id: "plan",
            code: "PLAN",
            name: "Meal plan",
            planVersion: 1,
            status: "ACTIVE",
            validFrom: "2026-08-15",
            validUntil: null,
          },
          {
            id: "second-plan",
            code: "PLAN2",
            name: "Second plan",
            planVersion: 2,
            status: "ACTIVE",
            validFrom: "2026-09-01",
            validUntil: "2026-12-01",
          },
          { id: "draft", name: "Draft plan", status: "DRAFT" },
        ],
        serviceTimes: [],
        planMeals: [],
        hallAssignmentRules: [],
        attendantAssignments: [],
      };
    if (path === "/api/student-records/registrations")
      return [
        registration,
        {
          ...registration,
          id: "second-registration",
          studentId: "second",
          studentNumber: "R260002A",
          studentName: "Second Student",
        },
        {
          ...registration,
          id: "draft-registration",
          studentName: "Draft Student",
          status: "DRAFT",
        },
        {
          ...registration,
          id: "old-registration",
          studentName: "Other Period Student",
          academicPeriodId: "other",
        },
      ];
    if (path === "/api/accommodation/operations")
      return {
        rates: [],
        applications: [
          { id: "application", studentId: "student" },
          { id: "hidden-app", studentId: "hidden" },
        ],
        waitlistEntries: [],
        allocations: [
          {
            id: "allocation",
            applicationId: "application",
            allocationNumber: "ALLOC1",
            studentNumber: "R260001A",
            residenceHallCode: "HALL",
            roomCode: "101",
            status: "CHECKED_IN",
          },
          {
            id: "old-allocation",
            applicationId: "application",
            allocationNumber: "OLD-ALLOC",
            status: "CANCELLED",
          },
          {
            id: "hidden-allocation",
            applicationId: "hidden-app",
            allocationNumber: "HIDDEN-ALLOC",
            status: "ALLOCATED",
          },
        ],
        allocationEvents: [],
      };
    throw new Error(`Unexpected request ${path}`);
  });
});
afterEach(() => {
  wrapper?.unmount();
  vi.unstubAllGlobals();
});
describe("Dining operations workspace", () => {
  it("scopes assignments, sessions, dietary controls, attendance and history to the selected academic period", async () => {
    wrapper = await mountCampusPage(DiningOperationsPage);
    expect(wrapper.text()).toContain("Active assignments: 1");
    expect(wrapper.text()).toContain("Today's net attendance: 1");
    expect(wrapper.text()).not.toContain("HIDDEN-ASSIGNMENT");
    for (const [tab, hidden] of [
      ["Dietary requirements", "Hidden allergy"],
      ["Service sessions", "HIDDEN-SESSION"],
      ["Meal attendance", "HIDDEN-ATT"],
      ["Attendance statistics", "HIDDEN-STAT"],
      ["Workflow history", "HIDDEN-EVENT"],
    ] as const) {
      await clickButton(wrapper, tab);
      expect(wrapper.text()).not.toContain(hidden);
    }
    expect(wrapper.text()).toContain("Created session");
    context.selectedAcademicPeriodId.value = null;
    await flushPromises();
    expect(
      context.request.mock.calls.filter(([path]) => path === "/api/dining/operations").length,
    ).toBe(2);
  });
  it("prepares an assignment from a confirmed registration and active plan", async () => {
    wrapper = await mountCampusPage(DiningOperationsPage);
    await clickButton(wrapper, "Prepare assignment");
    await setField(wrapper, "Confirmed student registration", "second:period-current");
    await setField(wrapper, "Dining plan", "second-plan");
    await setField(wrapper, "Student group", "SPORT");
    await setField(wrapper, "Accommodation allocation", "allocation");
    expect(wrapper.get('[role="dialog"]').text()).not.toContain("Draft Student");
    expect(wrapper.get('[role="dialog"]').text()).not.toContain("Draft plan");
    expect(wrapper.get('[role="dialog"]').text()).not.toContain("HIDDEN-ALLOC");
    await clickButton(wrapper, "Save record");
    expect(context.request).toHaveBeenCalledWith(
      "/api/dining/operations/assignments",
      expect.objectContaining({
        body: expect.objectContaining({
          studentId: "second",
          studentNumber: "R260002A",
          studentName: "Second Student",
          programmeCode: "BSC",
          academicPeriodId: "period-current",
          effectiveFrom: "2026-09-01",
          effectiveUntil: "2026-12-01",
          studentGroupCode: "SPORT",
          accommodationAllocationId: "allocation",
        }),
      }),
    );
  });
  it("leaves optional assignment links explicitly absent", async () => {
    wrapper = await mountCampusPage(DiningOperationsPage);
    await clickButton(wrapper, "Prepare assignment");
    await clickButton(wrapper, "Save record");
    expect(context.request).toHaveBeenCalledWith(
      "/api/dining/operations/assignments",
      expect.objectContaining({
        body: expect.objectContaining({ studentGroupCode: null, accommodationAllocationId: null }),
      }),
    );
  });
  it.each([false, true])(
    "captures dietary evidence with optional clinical link: %s",
    async (linked) => {
      wrapper = await mountCampusPage(DiningOperationsPage);
      await clickButton(wrapper, "Dietary requirements");
      await clickButton(wrapper, "Record requirement");
      await setField(wrapper, "Confirmed student registration", "second:period-current");
      await setField(wrapper, "Requirement code", "LOW-SALT");
      await setField(wrapper, "Severity", "CRITICAL");
      await setField(wrapper, "Description", "Clinical restriction");
      if (linked) {
        await setField(wrapper, "Clinical document ID", "document");
        await setField(wrapper, "Effective until", "2026-12-01");
      }
      await clickButton(wrapper, "Save record");
      expect(context.request).toHaveBeenCalledWith(
        "/api/dining/operations/dietary-requirements",
        expect.objectContaining({
          body: expect.objectContaining({
            studentId: "second",
            studentNumber: "R260002A",
            requirementCode: "LOW-SALT",
            severity: "CRITICAL",
            clinicalDocumentId: linked ? "document" : null,
            effectiveUntil: linked ? "2026-12-01" : null,
          }),
        }),
      );
    },
  );
  it("plans a session with explicit date-time values and expected serving count", async () => {
    wrapper = await mountCampusPage(DiningOperationsPage);
    await clickButton(wrapper, "Service sessions");
    await clickButton(wrapper, "Plan session");
    await setField(wrapper, "Service date", "2026-09-01");
    await setField(wrapper, "Expected servings", "200");
    await setField(wrapper, "Scheduled opening", "2026-09-01T11:00");
    await setField(wrapper, "Scheduled closing", "2026-09-01T13:00");
    await clickButton(wrapper, "Save record");
    expect(context.request).toHaveBeenCalledWith(
      "/api/dining/operations/sessions",
      expect.objectContaining({
        body: expect.objectContaining({
          diningHallId: "hall",
          mealOptionId: "meal",
          expectedServings: 200,
          scheduledOpensAt: new Date("2026-09-01T11:00").toISOString(),
        }),
      }),
    );
  });
  it.each([
    ["Activate", "DRAFT", "activate"],
    ["Suspend", "ACTIVE", "suspend"],
    ["Resume", "SUSPENDED", "resume"],
    ["End assignment", "DRAFT", "end"],
    ["Cancel assignment", "DRAFT", "cancel"],
  ] as const)("records %s with current assignment version", async (label, id, action) => {
    wrapper = await mountCampusPage(DiningOperationsPage);
    await clickButton(wrapper, label);
    expect(context.request).toHaveBeenCalledWith(
      `/api/dining/operations/assignments/${id}/${action}`,
      { method: "POST", body: { expectedVersion: 3, reason: "Approved operational evidence" } },
    );
    const validator = (vi.mocked(Swal.fire).mock.calls[0]?.[0] as any).inputValidator;
    expect(validator(" ")).toBe("Decision evidence is required.");
    expect(validator("Valid")).toBeUndefined();
  });
  it.each([
    ["Resolve", "RESOLVED"],
    ["Expire", "EXPIRED"],
  ] as const)("%s preserves dietary decision evidence", async (label, targetStatus) => {
    wrapper = await mountCampusPage(DiningOperationsPage);
    await clickButton(wrapper, "Dietary requirements");
    await clickButton(wrapper, label);
    expect(context.request).toHaveBeenCalledWith(
      "/api/dining/operations/dietary-requirements/diet/resolve",
      {
        method: "POST",
        body: { expectedVersion: 2, targetStatus, reason: "Approved operational evidence" },
      },
    );
  });
  it.each([
    ["Open", "PLANNED", "open"],
    ["Close", "OPEN", "close"],
  ] as const)("%s records the session boundary decision", async (label, id, action) => {
    wrapper = await mountCampusPage(DiningOperationsPage);
    await clickButton(wrapper, "Service sessions");
    await clickButton(wrapper, label);
    expect(context.request).toHaveBeenCalledWith(
      `/api/dining/operations/sessions/${id}/${action}`,
      { method: "POST", body: { expectedVersion: 4, reason: "Approved operational evidence" } },
    );
  });
  it("reconciles against the selected closed session's version", async () => {
    wrapper = await mountCampusPage(DiningOperationsPage);
    await clickButton(wrapper, "Service sessions");
    await clickButton(wrapper, "Reconcile");
    expect(wrapper.get('[data-label="Counted servings"] input').element).toHaveProperty(
      "value",
      "5",
    );
    await setField(wrapper, "Counted servings", "6");
    await setField(wrapper, "Reconciliation evidence", "One guest meal approved");
    await clickButton(wrapper, "Reconcile session");
    expect(context.request).toHaveBeenCalledWith(
      "/api/dining/operations/sessions/CLOSED/reconcile",
      {
        method: "POST",
        body: { countedServings: 6, reason: "One guest meal approved", expectedVersion: 4 },
      },
    );
  });
  it.each(["ADMITTED", "DENIED"])(
    "shows the server's %s result and rotates the next capture key",
    async (outcome) => {
      const original = context.request.getMockImplementation()!;
      context.request.mockImplementation((path, ...args) =>
        path === "/api/dining/operations/attendance" && args[0]?.method
          ? Promise.resolve({
              ...attendance,
              outcome,
              denialReason: outcome === "DENIED" ? "No entitlement" : null,
            })
          : original(path, ...args),
      );
      wrapper = await mountCampusPage(DiningOperationsPage);
      await clickButton(wrapper, "Meal attendance");
      await clickButton(wrapper, "Capture attendance");
      expect(wrapper.get('[role="dialog"]').text()).toContain("Nut allergy");
      const firstKey = (
        wrapper.get('[data-label="Idempotency key"] input').element as HTMLInputElement
      ).value;
      await setField(wrapper, "Confirmed student registration", "second:period-current");
      await setField(wrapper, "Capture channel", "OFFLINE_SYNC");
      await setField(wrapper, "Device ID", "gate-1");
      await clickButton(wrapper, "Evaluate access");
      expect(context.request).toHaveBeenCalledWith(
        "/api/dining/operations/attendance",
        expect.objectContaining({
          body: expect.objectContaining({
            studentId: "second",
            studentName: "Second Student",
            deviceId: "gate-1",
            idempotencyKey: firstKey,
          }),
        }),
      );
      expect(wrapper.find('[role="dialog"]').exists()).toBe(true);
      expect(
        (wrapper.get('[data-label="Idempotency key"] input').element as HTMLInputElement).value,
      ).not.toBe(firstKey);
      expect(context.notify).toHaveBeenCalledWith(
        expect.objectContaining({
          title: outcome === "ADMITTED" ? "Meal access admitted" : "Meal access denied",
          color: outcome === "ADMITTED" ? "success" : "error",
        }),
      );
    },
  );
  it("records an append-only correction without changing the original evidence", async () => {
    wrapper = await mountCampusPage(DiningOperationsPage);
    await clickButton(wrapper, "Meal attendance");
    expect(wrapper.get('[data-record="reversed"]').findAll("button")).toHaveLength(0);
    await clickButton(wrapper, "Reverse");
    expect(context.request).toHaveBeenCalledWith(
      "/api/dining/operations/attendance/attendance/reverse",
      {
        method: "POST",
        body: { reasonCode: "OPERATOR_CORRECTION", reason: "Approved operational evidence" },
      },
    );
    const validator = (vi.mocked(Swal.fire).mock.calls[0]?.[0] as any).inputValidator;
    expect(validator(" ")).toBe("A reversal reason is required.");
    expect(validator("Valid")).toBeUndefined();
  });
  it.each([
    ["Student assignments", "Activate"],
    ["Dietary requirements", "Resolve"],
    ["Service sessions", "Open"],
    ["Meal attendance", "Reverse"],
  ])("does not mutate %s when the operator cancels", async (tab, action) => {
    vi.mocked(Swal.fire).mockResolvedValue({ isConfirmed: false } as any);
    wrapper = await mountCampusPage(DiningOperationsPage);
    await clickButton(wrapper, tab);
    await clickButton(wrapper, action);
    expect(context.request.mock.calls.filter(([, options]) => options?.method)).toEqual([]);
  });
  it.each([
    ["Student assignments", "Activate", "Assignment action failed"],
    ["Dietary requirements", "Resolve", "Dietary action failed"],
    ["Service sessions", "Open", "Meal service action failed"],
    ["Meal attendance", "Reverse", "Attendance could not be reversed"],
  ])("reports failed %s actions", async (tab, action, title) => {
    wrapper = await mountCampusPage(DiningOperationsPage);
    context.request.mockRejectedValue(new Error("Conflict"));
    await clickButton(wrapper, tab);
    await clickButton(wrapper, action);
    expect(context.showError).toHaveBeenCalledWith(title, "Conflict");
  });
  it("preserves failed captures and tolerates unavailable independent services", async () => {
    const original = context.request.getMockImplementation()!;
    context.request.mockImplementation((path, ...args) =>
      path === "/api/dining/operations"
        ? original(path, ...args)
        : Promise.reject(new Error("Offline")),
    );
    wrapper = await mountCampusPage(DiningOperationsPage);
    expect(wrapper.text()).toContain("Existing operational records remain visible");
    expect(wrapper.text()).toContain(
      "Student Records registration data is temporarily unavailable",
    );
    expect(wrapper.text()).toContain("without an accommodation link");
    await clickButton(wrapper, "Prepare assignment");
    await clickButton(wrapper, "Save record");
    expect(context.showError).toHaveBeenCalledWith("Dining operation failed", "Offline");
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true);
  });
  it("displays operations loading errors and recovers on refresh", async () => {
    context.request.mockRejectedValueOnce(new Error("Operations offline"));
    wrapper = await mountCampusPage(DiningOperationsPage);
    expect(wrapper.text()).toContain("Operations offline");
    await clickButton(wrapper, "Refresh");
    expect(wrapper.text()).not.toContain("Operations offline");
    expect(wrapper.text()).toContain("ASSIGN-ACTIVE");
  });
});
