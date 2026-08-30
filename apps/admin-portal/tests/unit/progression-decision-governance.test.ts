// Author: Tinashe K
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import DecisionsPage from "../../pages/operations/progression-decisions.vue";
import type {
  ProgressionRosterSummary,
  ProgressionRuleSetSummary,
  ProgressionDecisionSummary,
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
let wrapper: VueWrapper,
  context: ReturnType<typeof operationalContext>,
  rosters: ProgressionRosterSummary[],
  rules: ProgressionRuleSetSummary[],
  decisions: ProgressionDecisionSummary[],
  failPath: string | undefined;
const profile = ref<{ user: { id: string } } | null>({ user: { id: "independent" } });
const roster: ProgressionRosterSummary = {
  id: "roster",
  studentId: "student",
  studentNumber: "R260001",
  programmeId: "programme",
  programmeVersionId: "programme-version",
  academicPeriodCode: "2026-S1",
  programmePeriodNumber: 1,
  eligibleModules: 2,
  publishedModules: 2,
  readyForProgression: true,
};
const rule: ProgressionRuleSetSummary = {
  id: "rule",
  ruleCode: "PROGRESS",
  ruleName: "Standing rule",
  programmeId: "programme",
  programmeVersionId: "programme-version",
  programmePeriodNumber: 1,
  ruleVersion: 2,
  status: "APPROVED",
  version: 3,
  approvedByUserId: "checker",
  approvedAt: "2026-08-01T08:00:00Z",
  outcomes: [],
};
const decision: ProgressionDecisionSummary = {
  id: "decision",
  decisionNumber: "DEC-001",
  decisionVersion: 1,
  supersedesDecisionId: null,
  progressionRuleSetId: "rule",
  progressionRuleCode: "PROGRESS",
  registrationRosterImportId: "roster",
  studentId: "student",
  studentNumber: "R260001",
  programmeId: "programme",
  programmeVersionId: "programme-version",
  academicPeriodCode: "2026-S1",
  programmePeriodNumber: 1,
  decisionCode: "PROCEED",
  decisionLabel: "Proceed",
  nextProgrammePeriodNumber: 2,
  attemptedCredits: 20,
  passedCredits: 20,
  failedCredits: 0,
  failedModules: 0,
  failedCompulsoryModules: 0,
  weightedAverage: 70,
  status: "CALCULATED",
  statusReason: "All passed",
  version: 7,
  calculatedByUserId: "calculator",
  calculatedAt: "2026-08-01T08:00:00Z",
  reviewedByUserId: "reviewer",
  reviewedAt: null,
  approvedByUserId: "approver",
  approvedAt: null,
  publishedByUserId: null,
  publishedAt: null,
  results: [
    {
      publishedResultId: "result",
      moduleCode: "CSC101",
      moduleName: "Computing",
      curriculumModuleType: "COMPULSORY",
      creditValue: 20,
      finalMark: 70,
      grade: "A",
      remark: "PASS",
      passing: true,
      publicationVersion: 1,
    },
  ],
};
beforeEach(() => {
  vi.resetAllMocks();
  context = operationalContext();
  installRegisterPeriodContext(context.selectedAcademicPeriodId);
  profile.value = { user: { id: "independent" } };
  vi.stubGlobal("useEmhareAuth", () => ({ currentUserProfile: profile }));
  rosters = [roster];
  rules = [rule];
  decisions = [decision];
  failPath = undefined;
  confirm.mockResolvedValue({ isConfirmed: true, value: "  Approved evidence  " });
  context.request.mockImplementation(async (path: string, options?: { method?: string }) => {
    if (path === failPath) throw new Error("Unavailable");
    if (options?.method) return {};
    if (path.endsWith("/rosters")) return structuredClone(rosters);
    if (path.endsWith("/rule-sets")) return structuredClone(rules);
    if (path.endsWith("/decisions")) return structuredClone(decisions);
    throw new Error(`Unexpected ${path}`);
  });
});
afterEach(() => {
  wrapper?.unmount();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
async function render() {
  wrapper = mount(DecisionsPage, { global: { stubs: registerStubs } });
  await flushPromises();
}
const writes = () => context.request.mock.calls.filter(([, options]) => options?.method);
describe("programme progression decision workflow", () => {
  it("selects only complete scoped rosters and exact approved programme-version rules", async () => {
    rosters.push(
      { ...roster, id: "incomplete", studentNumber: "INCOMPLETE", readyForProgression: false },
      { ...roster, id: "other", studentNumber: "OTHER", academicPeriodCode: "OTHER" },
    );
    rules.push(
      { ...rule, id: "draft", ruleCode: "DRAFT", status: "DRAFT" },
      { ...rule, id: "other-programme", ruleCode: "OTHER", programmeId: "other" },
      { ...rule, id: "other-version", ruleCode: "OTHER-VERSION", programmeVersionId: "other" },
      { ...rule, id: "other-period", ruleCode: "OTHER-PERIOD", programmePeriodNumber: 2 },
    );
    await render();
    expect(wrapper.get('[data-label="Complete published result set"] select').text()).not.toMatch(
      /INCOMPLETE|OTHER/,
    );
    expect(wrapper.get('[data-label="Applicable approved rule"] select').text()).toContain(
      "PROGRESS",
    );
    expect(wrapper.get('[data-label="Applicable approved rule"] select').text()).not.toMatch(
      /DRAFT|OTHER/,
    );
    expect(wrapper.text()).toContain("CSC101");
  });
  it("clears stale selection after academic-period changes and tolerates empty workspace", async () => {
    await render();
    context.selectedAcademicPeriodId.value = "other";
    await flushPromises();
    expect(wrapper.text()).toContain("No complete result set");
    expect(wrapper.text()).toContain("No progression decisions");
    await clickButton(wrapper, "Calculate decision");
    expect(writes()).toHaveLength(0);
  });
  it("does not calculate without an applicable approved rule", async () => {
    rules = [];
    await render();
    expect(wrapper.text()).toContain("No approved progression rule matches");
    await clickButton(wrapper, "Calculate decision");
    expect(confirm).not.toHaveBeenCalled();
    expect(writes()).toHaveLength(0);
  });
  it("calculates with exact roster/rule IDs after confirmation and handles retries", async () => {
    await render();
    confirm.mockResolvedValueOnce({ isConfirmed: false });
    await clickButton(wrapper, "Calculate decision");
    expect(writes()).toHaveLength(0);
    failPath = "/api/results/progression/decisions";
    await clickButton(wrapper, "Calculate decision");
    expect(context.showError).toHaveBeenCalledWith(
      "Progression decision could not be calculated",
      "Unavailable",
    );
    failPath = undefined;
    await clickButton(wrapper, "Calculate decision");
    expect(writes()[1]).toEqual([
      "/api/results/progression/decisions",
      {
        method: "POST",
        body: { registrationRosterImportId: "roster", progressionRuleSetId: "rule" },
      },
    ]);
    expect(context.notify).toHaveBeenCalledWith(
      expect.objectContaining({ title: "Progression decision calculated" }),
    );
    await setField(wrapper, "Applicable approved rule", "");
    await clickButton(wrapper, "Calculate decision");
    expect(writes()).toHaveLength(2);
  });
  it("recovers a failed dependency without losing the controlled queue", async () => {
    failPath = "/api/results/progression/rosters";
    await render();
    expect(context.showError).toHaveBeenCalledWith(
      "Progression workspace could not be loaded",
      "Unavailable",
    );
    failPath = undefined;
    await clickButton(wrapper, "Refresh");
    expect(wrapper.text()).toContain("DEC-001");
  });
  it.each([
    ["CALCULATED", "calculator", "calculator cannot review"],
    ["REVIEWED", "calculator", "calculation and review actors cannot approve"],
    ["REVIEWED", "reviewer", "calculation and review actors cannot approve"],
    ["APPROVED", "calculator", "publication requires a fourth independent actor"],
    ["APPROVED", "reviewer", "publication requires a fourth independent actor"],
    ["APPROVED", "approver", "publication requires a fourth independent actor"],
  ] as const)("requires handoff for %s by %s", async (status, actor, message) => {
    decisions = [{ ...decision, status }];
    profile.value = { user: { id: actor } };
    await render();
    expect(wrapper.text()).toContain(message);
    expect(wrapper.findAll("button").map((button) => button.text())).not.toContain(
      "Record independent review",
    );
    expect(wrapper.findAll("button").map((button) => button.text())).not.toContain(
      "Approve decision",
    );
    expect(wrapper.findAll("button").map((button) => button.text())).not.toContain(
      "Publish decision",
    );
  });
  it.each([
    ["CALCULATED", "Record independent review", "review"],
    ["REVIEWED", "Approve decision", "approve"],
    ["APPROVED", "Publish decision", "publish"],
    ["CALCULATED", "Reject", "reject"],
  ] as const)(
    "records %s %s with audited version and normalized reason",
    async (status, button, action) => {
      decisions = [{ ...decision, status }];
      await render();
      confirm.mockResolvedValueOnce({ isConfirmed: false });
      await clickButton(wrapper, button);
      confirm.mockResolvedValueOnce({ isConfirmed: true, value: " " });
      await clickButton(wrapper, button);
      expect(writes()).toHaveLength(0);
      const options = confirm.mock.calls[0]![0];
      expect(options.inputValidator(" ")).toBeTruthy();
      expect(options.inputValidator("Board authority")).toBeUndefined();
      failPath = `/api/results/progression/decisions/decision/${action}`;
      await clickButton(wrapper, button);
      expect(context.showError).toHaveBeenCalledWith(
        "Progression action could not be recorded",
        "Unavailable",
      );
      failPath = undefined;
      await clickButton(wrapper, button);
      expect(writes()[1]).toEqual([
        `/api/results/progression/decisions/decision/${action}`,
        { method: "POST", body: { expectedVersion: 7, reason: "Approved evidence" } },
      ]);
      expect(context.notify).toHaveBeenCalledWith(
        expect.objectContaining({
          title:
            action === "publish"
              ? "Progression decision published"
              : `Progression ${action} recorded`,
        }),
      );
    },
  );
  it.each(["PUBLISHED", "REJECTED"] as const)(
    "keeps final %s decisions without transition actions",
    async (status) => {
      decisions = [{ ...decision, status, failedCredits: 5, failedModules: 1 }];
      profile.value = null;
      await render();
      expect(wrapper.text()).toContain(status);
      expect(wrapper.findAll("button").map((button) => button.text())).toEqual([
        "Refresh",
        "Calculate decision",
      ]);
    },
  );
});
