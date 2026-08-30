// Author: Tinashe K
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import ExamSetupPage from "../../pages/operations/exam-setup.vue";
import type {
  ExamSetupRegister,
  ExamSessionSummary,
  ExamRequirementSummary,
  ExamVenueSummary,
} from "../../../../packages/portal-shell/types/exams";
import {
  clickButton,
  operationalContext,
  setField,
} from "../../../../tests/unit/support/operational-page";
import {
  RegisterDrawer,
  registerStubs,
  installRegisterPeriodContext,
} from "../../../../tests/unit/support/register-page";
const { confirm } = vi.hoisted(() => ({ confirm: vi.fn() }));
vi.mock("sweetalert2", () => ({ default: { fire: confirm } }));
let wrapper: VueWrapper,
  context: ReturnType<typeof operationalContext>,
  register: ExamSetupRegister,
  failPath: string | undefined;
const session: ExamSessionSummary = {
  id: "session",
  academicPeriodId: "period-current",
  academicPeriodCode: "2026-S1",
  code: "FINAL",
  name: "Final exams",
  assessmentType: "FINAL_EXAM",
  startsOn: "2026-08-01",
  endsOn: "2026-08-31",
  status: "DRAFT",
  version: 7,
  slots: [
    { id: "slot", code: "AM", startsAt: "2026-08-01T08:00:00Z", endsAt: "2026-08-01T12:00:00Z" },
  ],
};
const venue: ExamVenueSummary = {
  id: "venue",
  venueTypeId: "type",
  venueTypeCode: "HALL",
  code: "GREAT",
  name: "Great hall",
  campusName: "Main",
  examinationCapacity: 200,
  active: true,
  version: 2,
  availability: [
    {
      id: "availability",
      availableFrom: "2026-08-01T06:00:00Z",
      availableUntil: "2026-08-31T20:00:00Z",
    },
  ],
};
const requirement: ExamRequirementSummary = {
  id: "requirement",
  academicPeriodId: "period-current",
  moduleId: "module",
  moduleCode: "CSC101",
  moduleName: "Computing",
  requirementVersion: 1,
  durationMinutes: 180,
  readingTimeMinutes: 15,
  status: "DRAFT",
  version: 6,
};
const academic = {
  academicPeriods: [
    { id: "period-current", code: "2026-S1", name: "Semester one", status: "OPEN" },
    { id: "period-closed", code: "CLOSED", name: "Closed", status: "CLOSED" },
    { id: "other", code: "OTHER", name: "Other", status: "OPEN" },
  ],
  modules: [
    { id: "module", code: "CSC101", name: "Computing", status: "ACTIVE" },
    { id: "inactive", code: "INACTIVE", name: "Inactive", status: "INACTIVE" },
  ],
};
beforeEach(() => {
  vi.resetAllMocks();
  context = operationalContext();
  installRegisterPeriodContext(context.selectedAcademicPeriodId);
  failPath = undefined;
  register = {
    sessions: [session],
    venues: [venue],
    requirements: [requirement],
    venueTypes: [
      {
        id: "type",
        code: "HALL",
        name: "Hall",
        description: "Certified hall",
        active: true,
        version: 2,
      },
      { id: "inactive", code: "OLD", name: "Inactive type", active: false, version: 2 },
    ],
  };
  confirm.mockResolvedValue({ isConfirmed: true, value: "  Board reviewed  " });
  context.request.mockImplementation(async (path: string, options?: { method?: string }) => {
    if (path === failPath) throw new Error("Unavailable");
    if (options?.method) return {};
    if (path === "/api/exams/setup") return structuredClone(register);
    if (path === "/api/academic/overview") return structuredClone(academic);
    throw new Error(`Unexpected ${path}`);
  });
});
afterEach(() => {
  wrapper?.unmount();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
async function render() {
  wrapper = mount(ExamSetupPage, { global: { stubs: registerStubs } });
  await flushPromises();
}
const writes = () => context.request.mock.calls.filter(([, options]) => options?.method);
async function submit(label: string) {
  const button = wrapper
    .get('[role="dialog"]')
    .findAll("button")
    .find((button) => button.text() === label)!;
  await button.trigger("click");
  await flushPromises();
}
describe("exam setup workspace", () => {
  it("filters period-owned sessions and requirements and reloads after context changes", async () => {
    register.sessions.push({
      ...session,
      id: "other",
      name: "Other session",
      academicPeriodId: "other",
      status: "APPROVED",
    });
    register.requirements.push({
      ...requirement,
      id: "other",
      moduleName: "Other requirement",
      academicPeriodId: "other",
      status: "APPROVED",
    });
    await render();
    expect(wrapper.text()).not.toContain("Other session");
    expect(wrapper.text()).toContain("AM");
    context.selectedAcademicPeriodId.value = null;
    await flushPromises();
    expect(wrapper.text()).toContain("Other session");
    await clickButton(wrapper, "Module requirements");
    expect(wrapper.text()).toContain("Other requirement");
  });
  it("recovers setup load failure and renders independent empty registers", async () => {
    failPath = "/api/academic/overview";
    await render();
    expect(context.showError).toHaveBeenCalledWith("Exam setup could not be loaded", "Unavailable");
    expect(wrapper.text()).toContain("No exam sessions");
    await clickButton(wrapper, "Venues");
    expect(wrapper.text()).toContain("No certified venues");
    await clickButton(wrapper, "Venue types");
    expect(wrapper.text()).toContain("No venue types configured");
    failPath = undefined;
    await clickButton(wrapper, "Refresh");
    expect(wrapper.text()).toContain("Certified hall");
    expect(wrapper.text()).toContain("No description");
  });
  it("creates a governed venue type", async () => {
    await render();
    await clickButton(wrapper, "Venue types");
    await clickButton(wrapper, "New venue type");
    await setField(wrapper, "Code", "LAB");
    await setField(wrapper, "Name", "Laboratory");
    await setField(wrapper, "Description", "Computer laboratory");
    await submit("Create venue type");
    expect(writes()[0]).toEqual([
      "/api/exams/setup/venue-types",
      {
        method: "POST",
        body: { code: "LAB", name: "Laboratory", description: "Computer laboratory" },
      },
    ]);
  });
  it("creates a certified venue using active types, explicit capacity and accessibility", async () => {
    register.venues.push({ ...venue, id: "inactive", active: false });
    await render();
    await clickButton(wrapper, "Venues");
    expect(wrapper.text()).toContain("INACTIVE");
    await clickButton(wrapper, "New venue");
    expect(wrapper.get('[data-label="Venue type"] select').text()).not.toContain("Inactive type");
    for (const [label, value] of [
      ["Venue type", "type"],
      ["Code", "LAB1"],
      ["Name", "Lab one"],
      ["Campus", "Main"],
      ["Building", "Science"],
      ["Room", "1"],
      ["Certified exam capacity", "40"],
      ["Accessibility notes", "Ramp"],
    ] as const)
      await setField(wrapper, label, value);
    await submit("Create venue");
    expect(writes()[0]![1].body).toEqual({
      venueTypeId: "type",
      code: "LAB1",
      name: "Lab one",
      campusName: "Main",
      buildingName: "Science",
      roomName: "1",
      examinationCapacity: 40,
      accessibilityNotes: "Ramp",
    });
  });
  it("requires an open academic period before preparing a bounded session", async () => {
    await render();
    await clickButton(wrapper, "New exam session");
    await submit("Create draft session");
    expect(writes()).toHaveLength(0);
    expect(context.showError).toHaveBeenCalledWith(
      "Academic period required",
      "Select an open academic period.",
    );
    expect(wrapper.get('[data-label="Open academic period"] select').text()).not.toMatch(
      /CLOSED|OTHER/,
    );
    for (const [label, value] of [
      ["Open academic period", "period-current"],
      ["Code", "SUPP"],
      ["Name", "Supplementary"],
      ["Assessment type", "SUPPLEMENTARY"],
      ["Starts on", "2026-09-01"],
      ["Ends on", "2026-09-20"],
    ] as const)
      await setField(wrapper, label, value);
    await submit("Create draft session");
    expect(writes()[0]![1].body).toEqual({
      academicPeriodId: "period-current",
      academicPeriodCode: "2026-S1",
      code: "SUPP",
      name: "Supplementary",
      assessmentType: "SUPPLEMENTARY",
      startsOn: "2026-09-01",
      endsOn: "2026-09-20",
    });
  });
  it.each([false, true])(
    "prepares a Module requirement with optional venue type %s",
    async (specific) => {
      await render();
      await clickButton(wrapper, "Module requirements");
      await clickButton(wrapper, "New Module requirement");
      await submit("Create draft requirement");
      expect(context.showError).toHaveBeenCalledWith("Module required", "Select an active Module.");
      expect(wrapper.get('[data-label="Module"] select').text()).not.toContain("INACTIVE");
      await setField(wrapper, "Academic period", "period-current");
      await setField(wrapper, "Module", "module");
      await setField(wrapper, "Writing minutes", "120");
      await setField(wrapper, "Reading minutes", "10");
      await setField(wrapper, "Special requirements", "Accessible seating");
      if (specific) await setField(wrapper, "Required venue type", "type");
      await submit("Create draft requirement");
      expect(writes()[0]![1].body).toEqual({
        academicPeriodId: "period-current",
        moduleId: "module",
        moduleCode: "CSC101",
        moduleName: "Computing",
        durationMinutes: 120,
        readingTimeMinutes: 10,
        requiredVenueTypeId: specific ? "type" : null,
        specialRequirements: "Accessible seating",
      });
    },
  );
  it("adds a session slot from default session dates and resets a cancelled slot", async () => {
    await render();
    await clickButton(wrapper, "Add slot");
    expect(wrapper.get('[data-label="Starts at"] input').element).toHaveProperty(
      "value",
      "2026-08-01T08:00",
    );
    await setField(wrapper, "Slot code", "PM");
    await setField(wrapper, "Starts at", "2026-08-03T13:00");
    await setField(wrapper, "Ends at", "2026-08-03T16:00");
    await submit("Add slot");
    expect(writes()[0]).toEqual([
      "/api/exams/setup/sessions/session/slots",
      {
        method: "POST",
        body: {
          code: "PM",
          startsAt: new Date("2026-08-03T13:00").toISOString(),
          endsAt: new Date("2026-08-03T16:00").toISOString(),
        },
      },
    ]);
    await clickButton(wrapper, "Add slot");
    expect(wrapper.get('[data-label="Slot code"] input').element).toHaveProperty("value", "");
  });
  it("adds an explicit venue availability window", async () => {
    await render();
    await clickButton(wrapper, "Venues");
    await clickButton(wrapper, "Add availability");
    await setField(wrapper, "Available from", "2026-08-02T07:00");
    await setField(wrapper, "Available until", "2026-08-02T18:00");
    await setField(wrapper, "Notes", "Reserved for exams");
    await submit("Add availability");
    expect(writes()[0]).toEqual([
      "/api/exams/setup/venues/venue/availability",
      {
        method: "POST",
        body: {
          availableFrom: new Date("2026-08-02T07:00").toISOString(),
          availableUntil: new Date("2026-08-02T18:00").toISOString(),
          notes: "Reserved for exams",
        },
      },
    ]);
  });
  it.each([
    ["Sessions", "Approve session", "sessions", "session", 7],
    ["Module requirements", "Approve requirement", "requirements", "requirement", 6],
  ] as const)(
    "requires approval evidence for %s and records persisted version",
    async (tab, button, path, id, version) => {
      register.requirements.push(
        { ...requirement, id: "approved", status: "APPROVED", requiredVenueTypeCode: "HALL" },
        { ...requirement, id: "old", status: "SUPERSEDED" },
      );
      await render();
      await clickButton(wrapper, tab);
      confirm.mockResolvedValueOnce({ isConfirmed: false });
      await clickButton(wrapper, button);
      confirm.mockResolvedValueOnce({ isConfirmed: true, value: " " });
      await clickButton(wrapper, button);
      expect(writes()).toHaveLength(0);
      const options = confirm.mock.calls[0]![0];
      expect(options.inputValidator(" ")).toBeTruthy();
      expect(options.inputValidator("Approved")).toBeUndefined();
      await clickButton(wrapper, button);
      expect(writes()[0]).toEqual([
        `/api/exams/setup/${path}/${id}/approve`,
        { method: "POST", body: { reason: "Board reviewed", expectedVersion: version } },
      ]);
    },
  );
  it("retains draft details after a rejected save", async () => {
    await render();
    await clickButton(wrapper, "Venue types");
    await clickButton(wrapper, "New venue type");
    await setField(wrapper, "Name", "Draft hall");
    failPath = "/api/exams/setup/venue-types";
    await submit("Create venue type");
    expect(context.showError).toHaveBeenCalledWith(
      "Venue type created could not be completed",
      "Unavailable",
    );
    expect(wrapper.get('[data-label="Name"] input').element).toHaveProperty("value", "Draft hall");
  });
  it.each([
    ["Venue types", "New venue type"],
    ["Venues", "New venue"],
    ["Sessions", "New exam session"],
    ["Sessions", "Add slot"],
    ["Venues", "Add availability"],
    ["Module requirements", "New Module requirement"],
  ])("dismisses %s %s via the drawer open contract without writes", async (tab, button) => {
    await render();
    await clickButton(wrapper, tab);
    await clickButton(wrapper, button);
    const drawer = wrapper
      .findAllComponents(RegisterDrawer)
      .find((drawer) => drawer.props("open"))!;
    drawer.vm.$emit("update:open", true);
    await flushPromises();
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true);
    await clickButton(wrapper, "Cancel");
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    expect(writes()).toHaveLength(0);
  });
});
