// Author: Tinashe K

import { flushPromises, shallowMount } from "@vue/test-utils";
import { computed, onMounted, ref, watch } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";

const sweetAlertFire = vi.fn();
const request = vi.fn();
const showError = vi.fn();
const confirmAction = vi.fn();

vi.mock("sweetalert2", () => ({ default: { fire: sweetAlertFire } }));
Object.assign(globalThis, { computed, onMounted, ref, watch });
vi.stubGlobal("definePageMeta", vi.fn());
vi.stubGlobal("useHead", vi.fn());
vi.stubGlobal("useRoute", () => ({
  params: { applicationId: ["application-1", "ignored"] },
  query: { academicReviewAssignmentId: ["assignment-1", "ignored"] },
}));
vi.stubGlobal("useEmhareApi", () => ({
  request,
  errorMessage: (_error: unknown, fallback = "Request failed") => fallback,
}));
vi.stubGlobal("useEmhareAuth", () => ({ hasPermission: () => true, hasRole: () => true }));
vi.stubGlobal("useEmhareConfirm", () => ({
  confirmAction,
  showError,
  showSuccess: vi.fn(),
}));

const workItem = {
  workspace: {
    application: {
      id: "application-1",
      applicationNumber: "EMH-2026-0001",
      applicantNumber: "A0001",
      applicantName: "Applicant One",
      intakeId: "intake-1",
      intakeCode: "AUG-2026",
      applicationTypeId: "type-1",
      applicationTypeName: "Undergraduate",
      status: "UNDER_REVIEW",
      paymentClearanceStatus: "PAID",
      programmeChoices: [
        {
          id: "choice-1",
          programmeId: "programme-1",
          programmeCode: "HCS",
          programmeName: "Computer Science",
          choiceRank: 1,
          choiceStatus: "REQUIRES_REVIEW",
        },
      ],
    },
    profile: {
      firstName: "Applicant",
      lastName: "One",
      applicantNumber: "A0001",
      applicantCategoryCode: "LOCAL",
      primaryEmail: "applicant@example.test",
      completenessPercentage: 100,
    },
    sections: [
      { code: "PROFILE", status: "COMPLETE" },
      { code: "DOCUMENTS", status: "VERIFIED" },
      { code: "DECLARATION", status: "PENDING" },
    ],
    qualifications: [],
    documents: {
      requirements: [{ code: "ID", documentId: "document-1" }, { code: "RESULTS" }],
      pendingRequirementCodes: ["RESULTS"],
      missingRequirementCodes: [],
      rejectedRequirementCodes: [],
      requiredDocumentsUploaded: false,
      requiredDocumentsVerified: false,
    },
    workflowProgress: { currentStageCode: "ELIGIBILITY", stages: [] },
  },
  academicReview: { programmeChoiceId: "choice-1" },
  academicRecommendation: null,
  admissionDecision: null,
  offer: { id: "offer-1", offerNumber: "OFR-1" },
  documentVersions: [{ id: "version-1", status: "STORED", generatedDocumentId: "generated-1" }],
  publications: [],
  auditHistory: [
    {
      id: "event-1",
      fromStatus: "UNDER_REVIEW",
      toStatus: "ELIGIBLE",
      reason: "Meets requirements",
    },
  ],
  blockers: [],
  availableActions: ["RECALCULATE_ELIGIBILITY", "RESOLVE_ELIGIBILITY"],
};

async function mountAdmissionsPage() {
  const AdmissionsPage = (await import("../../pages/operations/admissions/[applicationId].vue"))
    .default;
  const wrapper = shallowMount(AdmissionsPage, {
    global: {
      stubs: {
        teleport: true,
        UDashboardPanel: true,
        UDashboardNavbar: true,
        UDashboardToolbar: true,
        UCard: true,
        UAlert: true,
        UButton: true,
        UBadge: true,
        UIcon: true,
        USkeleton: true,
        EmhareStatusPill: true,
        EmhareKpiCard: true,
        EmhareDescriptionList: true,
        EmhareFeedbackState: true,
      },
    },
  });
  await flushPromises();
  return wrapper;
}

describe("Admissions action release-gate branches", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    request.mockImplementation(async (path: string) => {
      if (path.includes("/work-items/")) return structuredClone(workItem);
      return {};
    });
    confirmAction.mockResolvedValue(true);
  });

  it("resolves eligibility, recommendation, decision, and email retry with validated reasons", async () => {
    const wrapper = await mountAdmissionsPage();
    const page = wrapper.vm as unknown as Record<string, any>;
    expect(page.applicationId).toBe("application-1");
    expect(page.academicReviewAssignmentId).toBe("assignment-1");
    expect(page.returnToWorkflowPath).toBe("/operations/admissions-recommendations");
    expect(page.completedSections).toBe(2);
    expect(page.documentCounts).toEqual({
      total: 2,
      uploaded: 1,
      pending: 1,
      missing: 0,
      rejected: 0,
    });
    expect(page.latestStoredOfferDocument.generatedDocumentId).toBe("generated-1");
    expect(page.applicantInitials).toBe("AO");
    expect(page.currentChoiceId).toBe("choice-1");
    expect(page.can("RESOLVE_ELIGIBILITY")).toBe(true);

    for (const action of [
      ["resolveEligibility", "ELIGIBLE", "Eligibility evidence is complete."],
      ["recordRecommendation", "RECOMMEND_ADMIT", "Strong academic evidence recorded."],
      ["recordDecision", "ADMIT", "Admission requirements are satisfied."],
    ] as const) {
      sweetAlertFire
        .mockResolvedValueOnce({ isConfirmed: true, value: action[1] })
        .mockResolvedValueOnce({ isConfirmed: true, value: action[2] });
      await page[action[0]]();
      const validator = sweetAlertFire.mock.calls.at(-1)?.[0].inputValidator as (
        value: string,
      ) => string | undefined;
      expect(validator("short")).toContain("10 characters");
      expect(validator("Enough evidence recorded.")).toBeUndefined();
    }

    sweetAlertFire.mockResolvedValueOnce({ isConfirmed: true, value: "Email delivery failed." });
    await page.retryOfferEmail();
    expect(request).toHaveBeenCalledWith(
      "/api/admissions/offers/offer-1/email-retry",
      expect.objectContaining({ method: "POST", body: { reason: "Email delivery failed." } }),
    );
  });

  it("keeps cancelled dialogs and failed workflow actions safe", async () => {
    const wrapper = await mountAdmissionsPage();
    const page = wrapper.vm as unknown as Record<string, any>;

    sweetAlertFire.mockResolvedValue({ isConfirmed: false });
    await page.resolveEligibility();
    await page.recordRecommendation();
    await page.recordDecision();
    await page.retryOfferEmail();

    request.mockRejectedValueOnce(new Error("offline"));
    await page.runWorkflowAction("/api/admissions/failing-action", { reason: "test" });
    expect(showError).toHaveBeenCalledWith("Admissions action failed", "Request failed");

    confirmAction.mockResolvedValueOnce(false);
    await page.publishAndSend();
    confirmAction.mockResolvedValueOnce(true);
    await page.publishAndSend();
    expect(request).toHaveBeenCalledWith(
      "/api/admissions/offers/offer-1/publish-and-send",
      expect.objectContaining({ method: "POST" }),
    );
  });
});
