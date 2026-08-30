// Author: Tinashe K
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { defineComponent, ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import SchemesPage from "../../pages/operations/assessment-schemes.vue";
import type {
  AssessmentOfferingSummary,
  AssessmentSchemeSummary,
  AssessmentRosterSource,
} from "../../../../packages/portal-shell/types/assessment";
import {
  clickButton,
  operationalContext,
  setField,
} from "../../../../tests/unit/support/operational-page";
import {
  registerStubs,
  installRegisterPeriodContext,
} from "../../../../tests/unit/support/register-page";
const { confirm } = vi.hoisted(() => ({ confirm: vi.fn() }));
vi.mock("sweetalert2", () => ({ default: { fire: confirm } }));
const Guided = defineComponent({
  props: ["label", "guidanceInstructions"],
  emits: ["click"],
  template: `<button :disabled="!!guidanceInstructions?.length" @click="$emit('click')">{{label}}</button>`,
});
let wrapper: VueWrapper,
  context: ReturnType<typeof operationalContext>,
  offerings: AssessmentOfferingSummary[],
  sources: AssessmentRosterSource[],
  failPath: string | undefined;
const profile = ref<{ user: { id: string } } | null>({ user: { id: "instructor" } });
const source: AssessmentRosterSource = {
  moduleId: "module",
  moduleCode: "CSC101",
  moduleName: "Computing",
  academicPeriodId: "period-current",
  academicPeriodCode: "2026-S1",
  academicPeriodName: "Semester one",
  eligibleStudentCount: 3,
  offeringCreated: false,
};
const scheme: AssessmentSchemeSummary = {
  id: "scheme",
  schemeVersion: 2,
  name: "Approved scheme",
  status: "APPROVED",
  approvalReason: "Approved",
  approvedByUserId: "checker",
  approvedAt: "2026-08-01T10:00:00Z",
  version: 6,
  components: [
    {
      id: "component",
      code: "EXAM",
      name: "Examination",
      componentType: "FINAL_EXAM",
      weightPercent: 100,
      maximumMark: 100,
      captureOpensAt: "2026-08-01T10:00:00Z",
      captureClosesAt: "2026-08-31T10:00:00Z",
      sortOrder: 1,
    },
  ],
};
const offering: AssessmentOfferingSummary = {
  id: "offering",
  ...source,
  assignedInstructorUserId: "instructor",
  status: "ACTIVE",
  version: 3,
  rosterCount: 3,
  schemes: [],
};
beforeEach(() => {
  vi.resetAllMocks();
  context = operationalContext();
  installRegisterPeriodContext(context.selectedAcademicPeriodId);
  vi.stubGlobal("useEmhareAuth", () => ({ currentUserProfile: profile }));
  profile.value = { user: { id: "instructor" } };
  offerings = [structuredClone(offering)];
  sources = [source];
  failPath = undefined;
  confirm.mockResolvedValue({ isConfirmed: true, value: "  Board approval  " });
  context.request.mockImplementation(async (path: string, options?: { method?: string }) => {
    if (path === failPath) throw new Error("Unavailable");
    if (options?.method) return {};
    if (path === "/api/assessment-results/offerings") return structuredClone(offerings);
    if (path === "/api/assessment-results/roster-sources") return structuredClone(sources);
    throw new Error(`Unexpected ${path}`);
  });
});
afterEach(() => {
  wrapper?.unmount();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
async function render() {
  wrapper = mount(SchemesPage, {
    global: { stubs: { ...registerStubs, EmhareGuidedActionButton: Guided } },
  });
  await flushPromises();
}
const writes = () => context.request.mock.calls.filter(([, options]) => options?.method);
describe("assessment scheme governance", () => {
  it("scopes offerings and eligible rosters to the selected period and refreshes on context change", async () => {
    offerings.push({
      ...offering,
      id: "other",
      moduleCode: "OTHER",
      academicPeriodId: "other-period",
    });
    sources.push(
      { ...source, moduleId: "other", moduleCode: "OTHER", academicPeriodId: "other-period" },
      { ...source, moduleId: "created", moduleCode: "CREATED", offeringCreated: true },
    );
    await render();
    expect(wrapper.text()).not.toContain("OTHER");
    await clickButton(wrapper, "Create Module offering");
    expect(wrapper.get('[data-label="Confirmed roster"] select').text()).toContain("CSC101");
    expect(wrapper.get('[data-label="Confirmed roster"] select').text()).not.toContain("CREATED");
    await clickButton(wrapper, "Cancel");
    context.selectedAcademicPeriodId.value = null;
    await flushPromises();
    expect(wrapper.text()).toContain("OTHER");
  });
  it("shows versioned scheme states and one-student labels without editing locked rules", async () => {
    offerings = [
      {
        ...offering,
        rosterCount: 1,
        schemes: [scheme, { ...scheme, id: "superseded", status: "SUPERSEDED" }],
      },
      {
        ...offering,
        id: "draft",
        status: "DRAFT",
        schemes: [{ ...scheme, id: "draft-scheme", status: "DRAFT" }],
      },
    ];
    await render();
    expect(wrapper.text()).toContain("1 eligible student");
    expect(wrapper.text()).toContain("SUPERSEDED");
    expect(wrapper.text()).toContain("EXAM 100%");
    expect(
      wrapper.findAll("button").filter((button) => button.text() === "Approve draft"),
    ).toHaveLength(1);
    expect(
      wrapper.findAll("button").filter((button) => button.text() === "New scheme version"),
    ).toHaveLength(1);
  });
  it("recovers failed workspace reads and displays an empty register", async () => {
    failPath = "/api/assessment-results/roster-sources";
    await render();
    expect(context.showError).toHaveBeenCalledWith(
      "Assessment setup could not be loaded",
      "Unavailable",
    );
    expect(wrapper.text()).toContain("No assessment offerings");
    failPath = undefined;
    await clickButton(wrapper, "Refresh");
    expect(wrapper.text()).toContain("Computing");
  });
  it("requires the authenticated instructor even when a valid roster was selected", async () => {
    profile.value = null;
    await render();
    await clickButton(wrapper, "Create Module offering");
    await setField(wrapper, "Confirmed roster", "module:period-current");
    await clickButton(wrapper, "Create offering");
    expect(writes()).toHaveLength(0);
    expect(context.showError).toHaveBeenCalledWith(
      "Offering details are incomplete",
      expect.any(String),
    );
  });
  it("creates an offering from a confirmed roster and the current instructor", async () => {
    await render();
    await clickButton(wrapper, "Create Module offering");
    await setField(wrapper, "Confirmed roster", "module:period-current");
    await clickButton(wrapper, "Create offering");
    expect(writes()[0]).toEqual([
      "/api/assessment-results/offerings",
      {
        method: "POST",
        body: {
          moduleId: "module",
          academicPeriodId: "period-current",
          assignedInstructorUserId: "instructor",
        },
      },
    ]);
    expect(context.notify).toHaveBeenCalledWith(
      expect.objectContaining({ title: "Module offering created" }),
    );
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
  });
  it("preserves roster selection if offering creation fails", async () => {
    await render();
    await clickButton(wrapper, "Create Module offering");
    await setField(wrapper, "Confirmed roster", "module:period-current");
    failPath = "/api/assessment-results/offerings";
    await clickButton(wrapper, "Create offering");
    expect(context.showError).toHaveBeenCalledWith(
      "Module offering could not be created",
      "Unavailable",
    );
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true);
  });
  it("prepares weighted components with UTC windows and explicit types", async () => {
    await render();
    await clickButton(wrapper, "New scheme version");
    expect(wrapper.text()).toContain("Total weight 100%");
    await setField(wrapper, "Scheme name", "Practical scheme");
    await setField(wrapper, "Code", "LAB", 0);
    await setField(wrapper, "Name", "Practical", 0);
    await setField(wrapper, "Type", "PRACTICAL", 0);
    await setField(wrapper, "Maximum mark", "50", 0);
    await setField(wrapper, "Capture opens", "2026-08-01T08:00", 0);
    await setField(wrapper, "Capture closes", "2026-09-01T16:00", 0);
    await clickButton(wrapper, "Save draft scheme");
    const body = writes()[0]![1].body;
    expect(body.name).toBe("Practical scheme");
    expect(body.components).toHaveLength(2);
    expect(body.components[0]).toEqual({
      code: "LAB",
      name: "Practical",
      componentType: "PRACTICAL",
      weightPercent: 40,
      maximumMark: 50,
      sortOrder: 1,
      captureOpensAt: new Date("2026-08-01T08:00").toISOString(),
      captureClosesAt: new Date("2026-09-01T16:00").toISOString(),
    });
    expect(body.components[1].weightPercent).toBe(60);
    expect(context.notify).toHaveBeenCalledWith(
      expect.objectContaining({ title: "Draft scheme created" }),
    );
  });
  it("rejects non-100 totals, supports additional components and protects the last component", async () => {
    await render();
    await clickButton(wrapper, "New scheme version");
    await setField(wrapper, "Weight %", "", 0);
    await clickButton(wrapper, "Save draft scheme");
    expect(writes()).toHaveLength(0);
    expect(context.showError).toHaveBeenCalledWith(
      "Weights must total 100%",
      "Current total is 60%.",
    );
    await clickButton(wrapper, "Add component");
    expect(wrapper.findAll('[data-label="Code"]')).toHaveLength(3);
    await setField(wrapper, "Weight %", "40", 2);
    expect(wrapper.text()).toContain("Total weight 100%");
    await clickButton(wrapper, "Remove", 0);
    await clickButton(wrapper, "Remove", 1);
    expect(wrapper.findAll('[data-label="Code"]')).toHaveLength(1);
    expect(
      wrapper
        .findAll("button")
        .find((button) => button.text() === "Remove")!
        .attributes("disabled"),
    ).toBeDefined();
    await setField(wrapper, "Weight %", "100");
    await clickButton(wrapper, "Save draft scheme");
    expect(writes()[0]![1].body.components).toHaveLength(1);
  });
  it("retains scheme edits on failure and resets a newly opened version", async () => {
    await render();
    await clickButton(wrapper, "New scheme version");
    await setField(wrapper, "Scheme name", "Unsaved");
    failPath = "/api/assessment-results/offerings/offering/schemes";
    await clickButton(wrapper, "Save draft scheme");
    expect(context.showError).toHaveBeenCalledWith(
      "Assessment scheme could not be created",
      "Unavailable",
    );
    await clickButton(wrapper, "Cancel");
    await clickButton(wrapper, "New scheme version");
    expect(wrapper.get('[data-label="Scheme name"] input').element).toHaveProperty(
      "value",
      "CSC101 assessment scheme",
    );
  });
  it("requires board evidence, ignores cancelled approvals and preserves optimistic version on retry", async () => {
    offerings = [{ ...offering, schemes: [{ ...scheme, status: "DRAFT" }] }];
    await render();
    confirm.mockResolvedValueOnce({ isConfirmed: false });
    await clickButton(wrapper, "Approve draft");
    confirm.mockResolvedValueOnce({ isConfirmed: true, value: " " });
    await clickButton(wrapper, "Approve draft");
    expect(writes()).toHaveLength(0);
    const options = confirm.mock.calls[0]![0];
    expect(options.inputValidator(" ")).toBeTruthy();
    expect(options.inputValidator("Board approved")).toBeUndefined();
    failPath = "/api/assessment-results/schemes/scheme/approve";
    await clickButton(wrapper, "Approve draft");
    expect(context.showError).toHaveBeenCalledWith(
      "Scheme approval could not be recorded",
      "Unavailable",
    );
    failPath = undefined;
    await clickButton(wrapper, "Approve draft");
    expect(writes()[1]).toEqual([
      "/api/assessment-results/schemes/scheme/approve",
      { method: "POST", body: { reason: "Board approval", expectedVersion: 6 } },
    ]);
    expect(context.notify).toHaveBeenCalledWith(
      expect.objectContaining({ title: "Assessment scheme approved" }),
    );
  });
});
