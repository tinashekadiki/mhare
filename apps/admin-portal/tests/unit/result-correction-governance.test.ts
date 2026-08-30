// Author: Tinashe K
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import CorrectionsPage from "../../pages/operations/result-corrections.vue";
import type {
  PublishedResultSummary,
  PublishedResultAmendmentSummary,
  ResultCorrectionSource,
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
  results: PublishedResultSummary[],
  amendments: PublishedResultAmendmentSummary[],
  sources: ResultCorrectionSource[],
  failPath: string | undefined;
const profile = ref<{ user: { id: string } } | null>({ user: { id: "independent" } }),
  stamp = "2026-08-01T08:00:00Z";
const result: PublishedResultSummary = {
  id: "published",
  resultBatchId: "batch",
  moduleResultId: "result",
  studentId: "student",
  studentNumber: "R260001",
  moduleId: "module",
  moduleCode: "CSC101",
  moduleName: "Computing",
  academicPeriodId: "period-current",
  academicPeriodCode: "2026-S1",
  finalMark: 60,
  grade: "B",
  remark: "PASS",
  publicationVersion: 1,
  supersedesPublishedResultId: null,
  resultAmendmentId: null,
  publishedByUserId: "publisher",
  publishedAt: stamp,
};
const source: ResultCorrectionSource = {
  moduleResultId: "replacement",
  resultBatchId: "new-batch",
  batchNumber: "BATCH-002",
  courseworkMark: 30,
  examinationMark: 50,
  finalMark: 80,
  grade: "A",
  remark: "PASS",
  approvedAt: stamp,
};
const amendment: PublishedResultAmendmentSummary = {
  id: "amendment",
  amendmentNumber: "AMD-001",
  originalPublishedResultId: "published",
  originalPublicationVersion: 1,
  replacementResultBatchId: "new-batch",
  replacementModuleResultId: "replacement",
  studentNumber: "R260001",
  moduleCode: "CSC101",
  moduleName: "Computing",
  academicPeriodCode: "2026-S1",
  originalFinalMark: 60,
  originalGrade: "B",
  originalRemark: "PASS",
  proposedFinalMark: 80,
  proposedGrade: "A",
  proposedRemark: "PASS",
  requestReason: "Approved source amendment",
  status: "REQUESTED",
  version: 8,
  requestedByUserId: "requester",
  requestedAt: stamp,
  reviewedByUserId: "reviewer",
  reviewedAt: null,
  reviewReason: null,
  approvedByUserId: "approver",
  approvedAt: null,
  approvalReason: null,
  appliedByUserId: null,
  appliedAt: null,
  rejectedByUserId: null,
  rejectedAt: null,
  rejectionReason: null,
};
beforeEach(() => {
  vi.resetAllMocks();
  context = operationalContext();
  installRegisterPeriodContext(context.selectedAcademicPeriodId);
  profile.value = { user: { id: "independent" } };
  vi.stubGlobal("useEmhareAuth", () => ({ currentUserProfile: profile }));
  results = [result];
  amendments = [amendment];
  sources = [source];
  failPath = undefined;
  confirm.mockResolvedValue({ isConfirmed: true, value: "  Board reviewed evidence  " });
  context.request.mockImplementation(async (path: string, options?: { method?: string }) => {
    if (path === failPath) throw new Error("Unavailable");
    if (options?.method) return {};
    if (path.includes("/published-results?"))
      return {
        content: structuredClone(results),
        page: 0,
        size: 25,
        totalElements: results.length,
        totalPages: results.length ? 1 : 0,
      };
    if (path.endsWith("/correction-sources")) return structuredClone(sources);
    if (path.endsWith("/published-result-amendments")) return structuredClone(amendments);
    throw new Error(`Unexpected ${path}`);
  });
});
afterEach(() => {
  wrapper?.unmount();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
async function render() {
  wrapper = mount(CorrectionsPage, { global: { stubs: registerStubs } });
  await flushPromises();
}
const writes = () => context.request.mock.calls.filter(([, options]) => options?.method);
describe("append-only result correction controls", () => {
  it("shows original/replacement evidence and scopes both queues to the selected period", async () => {
    results.push({ ...result, id: "other", studentNumber: "OTHER", academicPeriodId: "other" });
    amendments.push({
      ...amendment,
      id: "other",
      studentNumber: "OTHER",
      academicPeriodCode: "OTHER",
    });
    await render();
    expect(wrapper.text()).toContain("Permanent original");
    expect(wrapper.text()).toContain("60% · B");
    expect(wrapper.text()).toContain("80% · A");
    expect(wrapper.text()).not.toContain("OTHER");
    expect(wrapper.text()).toContain("1 current published result");
    context.selectedAcademicPeriodId.value = null;
    await flushPromises();
    expect(wrapper.text()).toContain("2 current published results");
  });
  it("searches by trimmed student number and retries failed queue reads", async () => {
    failPath = "/api/results/published-result-amendments";
    await render();
    expect(context.showError).toHaveBeenCalledWith(
      "Published result controls could not be loaded",
      "Unavailable",
    );
    expect(wrapper.text()).toContain("No current published results");
    failPath = undefined;
    await setField(wrapper, "Student number", " R260001 ");
    await clickButton(wrapper, "Search");
    expect(context.request).toHaveBeenCalledWith(
      "/api/results/published-results?studentNumber=R260001&page=0&size=25",
    );
    await wrapper.get('[data-label="Student number"] input').trigger("keyup.enter");
    expect(wrapper.text()).toContain("AMD-001");
  });
  it.each(["empty", "error"])(
    "does not open a correction without approved evidence: %s",
    async (outcome) => {
      sources = [];
      await render();
      if (outcome === "error")
        failPath = "/api/results/published-results/published/correction-sources";
      await clickButton(wrapper, "Request correction");
      expect(context.showError).toHaveBeenCalledWith(
        outcome === "error"
          ? "Correction sources could not be loaded"
          : "No approved replacement evidence",
        expect.any(String),
      );
      expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    },
  );
  it("requires a replacement and reason, then links rather than modifies the original result", async () => {
    await render();
    await clickButton(wrapper, "Request correction");
    expect(wrapper.text()).toContain("v1 remains permanent");
    await clickButton(wrapper, "Submit correction request");
    await setField(wrapper, "Approved replacement result batch", "replacement");
    await clickButton(wrapper, "Submit correction request");
    expect(writes()).toHaveLength(0);
    await setField(wrapper, "Correction reason", "  Board-approved recalculation  ");
    await clickButton(wrapper, "Submit correction request");
    expect(writes()[0]).toEqual([
      "/api/results/published-result-amendments",
      {
        method: "POST",
        body: {
          originalPublishedResultId: "published",
          replacementModuleResultId: "replacement",
          reason: "Board-approved recalculation",
        },
      },
    ]);
    expect(context.notify).toHaveBeenCalledWith(
      expect.objectContaining({ title: "Result correction requested" }),
    );
  });
  it("preserves failed corrections and resets cancelled requests", async () => {
    await render();
    await clickButton(wrapper, "Request correction");
    await setField(wrapper, "Approved replacement result batch", "replacement");
    await setField(wrapper, "Correction reason", "Audited reason");
    failPath = "/api/results/published-result-amendments";
    await clickButton(wrapper, "Submit correction request");
    expect(context.showError).toHaveBeenCalledWith(
      "Result correction could not be requested",
      "Unavailable",
    );
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true);
    await clickButton(wrapper, "Cancel");
    await clickButton(wrapper, "Request correction");
    expect(wrapper.get('[data-label="Correction reason"] input').element).toHaveProperty(
      "value",
      "",
    );
  });
  it.each([
    ["REQUESTED", "requester", "requester cannot review"],
    ["REVIEWED", "requester", "requester and reviewer cannot approve"],
    ["REVIEWED", "reviewer", "requester and reviewer cannot approve"],
    ["APPROVED", "approver", "approver cannot release"],
  ] as const)("requires %s handoff for %s", async (status, actor, message) => {
    amendments = [{ ...amendment, status }];
    profile.value = { user: { id: actor } };
    await render();
    expect(wrapper.text()).toContain(message);
    expect(wrapper.findAll("button").map((button) => button.text())).not.toContain(
      "Record independent review",
    );
    expect(wrapper.findAll("button").map((button) => button.text())).not.toContain(
      "Approve correction",
    );
    expect(wrapper.findAll("button").map((button) => button.text())).not.toContain(
      "Release corrected version",
    );
  });
  it.each([
    ["REQUESTED", "Record independent review", "review"],
    ["REVIEWED", "Approve correction", "approve"],
    ["APPROVED", "Release corrected version", "apply"],
    ["REQUESTED", "Reject", "reject"],
  ] as const)(
    "records %s %s with independent authority and optimistic version",
    async (status, button, action) => {
      amendments = [{ ...amendment, status }];
      await render();
      confirm.mockResolvedValueOnce({ isConfirmed: false });
      await clickButton(wrapper, button);
      confirm.mockResolvedValueOnce({ isConfirmed: true, value: " " });
      await clickButton(wrapper, button);
      expect(writes()).toHaveLength(0);
      const options = confirm.mock.calls[0]![0];
      expect(options.inputValidator(" ")).toBeTruthy();
      expect(options.inputValidator("Approved evidence")).toBeUndefined();
      failPath = `/api/results/published-result-amendments/amendment/${action}`;
      await clickButton(wrapper, button);
      expect(context.showError).toHaveBeenCalledWith(
        "Correction decision could not be recorded",
        "Unavailable",
      );
      failPath = undefined;
      await clickButton(wrapper, button);
      expect(writes()[1]).toEqual([
        `/api/results/published-result-amendments/amendment/${action}`,
        { method: "POST", body: { expectedVersion: 8, reason: "Board reviewed evidence" } },
      ]);
      expect(context.notify).toHaveBeenCalledWith(
        expect.objectContaining({
          title:
            action === "apply"
              ? "Corrected result version released"
              : `Correction ${action} recorded`,
        }),
      );
    },
  );
  it.each(["APPLIED", "REJECTED"] as const)(
    "retains final %s corrections without mutation controls",
    async (status) => {
      amendments = [{ ...amendment, status }];
      profile.value = null;
      await render();
      expect(wrapper.text()).toContain(status);
      expect(wrapper.findAll("button").map((button) => button.text())).not.toContain("Reject");
      expect(wrapper.text()).not.toContain("Handoff required");
    },
  );
});
