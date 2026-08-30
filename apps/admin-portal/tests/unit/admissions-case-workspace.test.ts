// Author: Tinashe K
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { defineComponent, reactive } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Swal from "sweetalert2";
import AdmissionsCase from "../../pages/operations/admissions/[applicationId].vue";
import { campusStubs } from "../../../../tests/unit/support/campus-page";
import { clickButton, operationalContext } from "../../../../tests/unit/support/operational-page";

vi.mock("sweetalert2", () => ({
  default: { fire: vi.fn(), getPopup: vi.fn(), getTitle: vi.fn(), getHtmlContainer: vi.fn() },
}));
const IdentityPanel = defineComponent({
  props: ["correction", "loading", "editable", "mode"],
  emits: ["approve", "reject"],
  template:
    '<section data-testid="identity-correction"><p>{{ correction.status }}</p><button @click="$emit(\'approve\')">Approve identity correction</button><button @click="$emit(\'reject\')">Reject identity correction</button></section>',
});
const stubs = {
  ...campusStubs,
  EmhareIdentityNameMismatchPanel: IdentityPanel,
  UDashboardToolbar: defineComponent({
    template: '<nav><slot name="left"/><slot name="right"/></nav>',
  }),
  EmhareFeedbackState: campusStubs.UAlert,
  EmhareDescriptionList: defineComponent({
    props: ["items"],
    template:
      '<dl><div v-for="item in items" :key="item.label"><dt>{{ item.label }}</dt><dd>{{ item.value }}</dd></div></dl>',
  }),
};
let context: ReturnType<typeof operationalContext>;
let wrapper: VueWrapper;
let workItem: any;
let route: {
  params: { applicationId: string | string[] | undefined };
  query: { academicReviewAssignmentId?: string | string[] };
};
let permissions: Set<string>;
let previewDownload: any;
const confirmAction = vi.fn();
const showSuccess = vi.fn();
function documentFixture(overrides = {}) {
  return {
    requirementCode: "ID",
    requirementName: "Identity evidence",
    required: true,
    state: "PENDING",
    documentId: "document",
    documentVersion: 7,
    fileName: "identity.pdf",
    mimeType: "application/pdf",
    rejectionReason: null,
    ...overrides,
  };
}
function qualificationFixture(overrides = {}) {
  return {
    id: "qualification",
    level: "A_LEVEL",
    verificationStatus: "CAPTURED",
    examBody: { name: "ZIMSEC" },
    institutionName: null,
    yearWritten: 2025,
    centreNumber: "CENTRE",
    candidateNumber: "CANDIDATE",
    version: 9,
    results: [
      { id: "math", subjectNameSnapshot: "Mathematics", grade: "A", points: 5 },
      { id: "science", subjectNameSnapshot: "Physics", grade: "B", points: null },
    ],
    ...overrides,
  };
}
function fixture() {
  return {
    workspace: {
      application: {
        id: "application",
        applicationNumber: "APP-001",
        applicantNumber: "A001",
        applicantName: "Ada Example",
        applicationTypeId: "type",
        applicationTypeName: "Undergraduate",
        intakeId: "intake",
        intakeCode: "AUG-2026",
        status: "UNDER_REVIEW",
        paymentClearanceStatus: "PAID",
        payment: { reference: "PAY-001" },
        calculatedTotalPoints: 14,
        programmeChoices: [
          {
            id: "choice",
            programmeId: "programme",
            programmeCode: "BSC",
            programmeName: "Science",
            awardName: "Bachelor",
            owningAcademicUnitName: "Science faculty",
            choiceRank: 1,
            choiceStatus: "REQUIRES_REVIEW",
            evaluationSummary: "Verified science evidence",
            decisionReason: "Pending final review",
          },
        ],
      },
      profile: {
        firstName: "Ada",
        middleNames: "Test",
        lastName: "Example",
        titleCode: "Ms",
        applicantNumber: "A001",
        applicantCategoryCode: "LOCAL",
        primaryEmail: "ada@example.test",
        primaryPhone: "+263700000001",
        completenessPercentage: 100,
        dateOfBirth: "2006-01-01",
        genderCode: "FEMALE",
      },
      identityNameCorrection: null,
      readyForSubmission: true,
      missingRequirements: [],
      sections: [
        {
          id: "profile",
          name: "Personal details",
          code: "PROFILE",
          status: "COMPLETE",
          required: true,
          completionSummary: "Complete personal record",
        },
        { id: "docs", name: "Documents", code: "DOCUMENTS", status: "VERIFIED", required: true },
      ],
      qualifications: [qualificationFixture()],
      nextOfKin: [],
      referees: [],
      employmentHistory: [],
      documents: {
        requirements: [documentFixture()],
        pendingRequirementCodes: ["ID"],
        missingRequirementCodes: [],
        rejectedRequirementCodes: [],
        requiredDocumentsUploaded: true,
        requiredDocumentsVerified: false,
      },
      workflowProgress: {
        currentStageCode: "ELIGIBILITY",
        stages: [
          {
            code: "VERIFICATION",
            sequence: 1,
            label: "Verification",
            state: "COMPLETED",
            statusLabel: "Verified",
            detail: "Evidence checked",
            occurredAt: "2026-08-20T10:00:00Z",
          },
          {
            code: "ELIGIBILITY",
            sequence: 2,
            label: "Eligibility",
            state: "CURRENT",
            statusLabel: "Under review",
            detail: "Academic threshold review",
          },
          {
            code: "OFFER",
            sequence: 3,
            label: "Offer",
            state: "LOCKED",
            statusLabel: "Waiting",
            detail: "Decision required",
          },
          {
            code: "REFERENCE",
            sequence: 4,
            label: "Reference",
            state: "NOT_APPLICABLE",
            statusLabel: "Not needed",
            detail: "No references required",
          },
        ],
      },
    },
    academicReview: { programmeChoiceId: "choice" },
    auditHistory: [
      { fromStatus: "UNDER_REVIEW", toStatus: "ELIGIBLE", reason: "Meets entry requirements" },
    ],
    blockers: [],
    availableActions: [],
    offer: null,
    documentVersions: [],
    publications: [],
  };
}
beforeEach(() => {
  context = operationalContext();
  route = reactive({ params: { applicationId: "application" }, query: {} });
  permissions = new Set(["ADMISSIONS_SETUP_MANAGE", "ADMISSIONS_APPLICATION_REVIEW"]);
  vi.stubGlobal("useRoute", () => route);
  vi.stubGlobal("useHead", vi.fn());
  vi.stubGlobal("useEmhareAuth", () => ({
    hasPermission: (permission: string) => permissions.has(permission),
    hasRole: () => false,
  }));
  confirmAction.mockReset().mockResolvedValue(true);
  showSuccess.mockReset().mockResolvedValue(undefined);
  vi.stubGlobal("useEmhareConfirm", () => ({
    confirmAction,
    showSuccess,
    showError: context.showError,
  }));
  vi.mocked(Swal.fire)
    .mockReset()
    .mockResolvedValue({ isConfirmed: true, value: "  Evidence checked and approved  " } as any);
  for (const method of [Swal.getPopup, Swal.getTitle, Swal.getHtmlContainer])
    vi.mocked(method).mockReset().mockReturnValue(null);
  workItem = fixture();
  previewDownload = {
    documentId: "document",
    mimeType: "application/pdf",
    downloadUrl: "https://documents.example.test/identity",
    originalFileName: "identity.pdf",
    expiresAt: "2026-08-30T15:00:00Z",
    checksumSha256: "1234567890abcdef",
  };
  context.request.mockImplementation(async (path: string, options?: any) => {
    if (options?.method) {
      if (/\/uploads\/[^/]+\/(verify|reject)$/.test(path))
        workItem.workspace.documents.requirements[0].state = path.endsWith("verify")
          ? "VERIFIED"
          : "REJECTED";
      if (path.endsWith("document-generation"))
        return { id: "generated", documentVersion: 3, status: "QUEUED" };
      return {};
    }
    if (path.startsWith("/api/admissions/work-items/")) return structuredClone(workItem);
    if (path.startsWith("/api/documents/")) return { ...previewDownload };
    throw new Error(`Unexpected request ${path}`);
  });
});
afterEach(() => {
  wrapper?.unmount();
  vi.useRealTimers();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  document.body.innerHTML = "";
});
async function render() {
  wrapper = mount(AdmissionsCase, { global: { stubs } });
  await flushPromises();
  return wrapper;
}
function writes() {
  return context.request.mock.calls.filter(([, options]) => options?.method);
}
function enable(...actions: string[]) {
  workItem.availableActions = actions;
}
async function selectDocument(name: string) {
  const button = wrapper
    .findAll('[data-testid="application-documents-panel"] button')
    .find((button) => button.text().includes(name));
  expect(button).toBeDefined();
  await button!.trigger("click");
  await flushPromises();
}
function dialog(index = -1) {
  return vi.mocked(Swal.fire).mock.calls.at(index)![0] as any;
}
function offerFixture() {
  workItem.offer = { id: "offer", offerNumber: "OFFER-001" };
  workItem.documentVersions = [
    {
      id: "version",
      version: 3,
      status: "STORED",
      generatedDocumentId: "official",
      documentNumber: "LETTER-003",
    },
  ];
}
function previewTab() {
  const tab = {
    location: { href: "about:blank" },
    close: vi.fn(),
    opener: window as Window | null,
  };
  vi.spyOn(window, "open").mockReturnValue(tab as unknown as Window);
  return tab;
}

describe("admissions case evidence and boundaries", () => {
  it("renders consolidated readiness, workflow stages, programme evidence and secure PDF", async () => {
    await render();
    expect(wrapper.text()).toContain("Ada Example");
    expect(wrapper.text()).toContain("Sections: 2/2");
    expect(wrapper.text()).toContain("Documents: 1/1");
    expect(wrapper.text()).toContain("Step 2 of 4");
    expect(wrapper.text()).toContain("Ms Ada Test Example");
    expect(wrapper.text()).toContain("Meets entry requirements");
    expect(wrapper.text()).toContain("Verified science evidence");
    expect(wrapper.text()).toContain("ZIMSEC points: 14");
    expect(wrapper.get('[data-testid="document-preview-frame"]').attributes("src")).toBe(
      previewDownload.downloadUrl,
    );
    expect(wrapper.text()).toContain("1234567890ab…");
    const requirements = wrapper
      .findAllComponents(campusStubs.UButton)
      .find((button) => button.props("label") === "Review programme requirements");
    expect(requirements!.vm.$attrs.to).toEqual({
      path: "/operations/programme-requirements",
      query: { programmeId: "programme", applicationTypeId: "type", intakeId: "intake" },
    });
  });
  it("does not fetch for absent IDs and resolves array route parameters", async () => {
    route.params.applicationId = [];
    await render();
    expect(context.request).not.toHaveBeenCalled();
    wrapper.unmount();
    route.params.applicationId = ["application", "ignored"];
    route.query.academicReviewAssignmentId = ["assignment", "ignored"];
    await render();
    expect(context.request).toHaveBeenCalledWith("/api/admissions/work-items/application");
    expect(wrapper.text()).toContain("Applicant profile for academic-unit recommendation");
    expect(
      wrapper
        .findAll("button")
        .find((button) => button.text() === "Back to recommendations")!
        .attributes("to"),
    ).toBe("/operations/admissions-recommendations");
  });
  it("refreshes when an existing application route changes and recovers load failures", async () => {
    context.request.mockRejectedValueOnce(new Error("Case unavailable"));
    await render();
    expect(wrapper.text()).toContain("Applicant application unavailable");
    expect(wrapper.text()).toContain("Case unavailable");
    await clickButton(wrapper, "Refresh");
    expect(wrapper.text()).toContain("Ada Example");
    route.params.applicationId = "second";
    await flushPromises();
    expect(context.request).toHaveBeenCalledWith("/api/admissions/work-items/second");
  });
  it("keeps the recommendation profile read-only for evidence and applicant corrections", async () => {
    route.query.academicReviewAssignmentId = "assignment";
    await render();
    for (const label of [
      "Request applicant correction",
      "Verify qualification",
      "Reject qualification",
      "Verify",
      "Reject",
    ])
      expect(wrapper.findAll("button").some((button) => button.text() === label)).toBe(false);
    expect(wrapper.findAll("button").some((button) => button.text() === "Expand preview")).toBe(
      true,
    );
  });
  it("honours server actions and review/setup permissions", async () => {
    permissions.clear();
    await render();
    expect(wrapper.text()).not.toContain("Review programme requirements");
    expect(wrapper.text()).not.toContain("Verify qualification");
    expect(wrapper.text()).not.toContain("Recalculate eligibility");
    enable("RECALCULATE_ELIGIBILITY", "RESOLVE_ELIGIBILITY");
    workItem.workspace.workflowProgress.currentStageCode = "VERIFICATION";
    await clickButton(wrapper, "Refresh");
    expect(wrapper.text()).not.toContain("Recalculate eligibility");
    expect(wrapper.text()).not.toContain("Resolve eligibility");
  });
  it("shows missing evidence, optional sections, incomplete profile and default workflow step", async () => {
    const workspace = workItem.workspace;
    workspace.application.status = "DRAFT";
    workspace.application.payment = null;
    workspace.application.calculatedTotalPoints = null;
    workspace.application.programmeChoices = [];
    workspace.profile = {
      ...workspace.profile,
      titleCode: null,
      middleNames: null,
      primaryPhone: null,
      dateOfBirth: null,
      completenessPercentage: 40,
    };
    workspace.readyForSubmission = false;
    workspace.missingRequirements = ["DOCUMENTS", "QUALIFICATIONS"];
    workspace.qualifications = [];
    workspace.sections = [
      { id: "missing", name: "Declaration", status: "CORRECTION_REQUIRED", required: true },
      { id: "optional", name: "Other evidence", status: "PENDING", required: false },
      {
        id: "rejected",
        name: "References",
        status: "REJECTED",
        completionSummary: "Reference rejected",
      },
    ];
    workspace.documents = {
      requirements: [],
      pendingRequirementCodes: [],
      missingRequirementCodes: [],
      rejectedRequirementCodes: [],
      requiredDocumentsUploaded: false,
      requiredDocumentsVerified: false,
    };
    workspace.workflowProgress.currentStageCode = "UNKNOWN";
    workItem.auditHistory = [];
    await render();
    for (const text of [
      "Attention required",
      "Outstanding requirements",
      "No academic evidence",
      "No programme choices",
      "No documents configured",
      "Required section",
      "Optional section",
      "Not captured",
      "ZIMSEC points: Pending submission",
      "Step 1 of 4",
    ])
      expect(wrapper.text()).toContain(text);
    expect(wrapper.find('[data-testid="recorded-eligibility"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain("Request applicant correction");
  });
  it.each([
    ["SUBMITTED", "PENDING", "info", "Payment pending"],
    ["ELIGIBLE", "WAIVED", "success", "Fee waived"],
    ["NOT_ELIGIBLE", "UNRATED", "error", "Rate pending"],
    ["DRAFT", "NOT_REQUIRED", "neutral", "Fee not required"],
  ] as const)(
    "projects %s and %s states with authoritative blockers",
    async (status, payment, tone, label) => {
      workItem.workspace.application.status = status;
      workItem.workspace.application.paymentClearanceStatus = payment;
      workItem.workspace.qualifications = [];
      workItem.auditHistory = [
        { fromStatus: "VERIFICATION", toStatus: "NOT_ELIGIBLE", reason: "Below subject minimum" },
      ];
      if (status !== "SUBMITTED") workItem.blockers = ["Qualification gap", "Document mismatch"];
      await render();
      expect(
        wrapper.findAll(`[data-tone="${tone}"]`).some(
          (pill) =>
            pill.text() ===
            status
              .toLowerCase()
              .replaceAll("_", " ")
              .replace(/(^|\s)\S/g, (value) => value.toUpperCase()),
        ),
      ).toBe(true);
      expect(wrapper.text()).toContain(label);
      expect(wrapper.text()).toContain(
        status === "SUBMITTED"
          ? "Review starts automatically"
          : "Qualification gap · Document mismatch",
      );
      expect(wrapper.text()).toContain("Below subject minimum");
    },
  );
  it("renders contacts, confidential reference outcomes and employment histories", async () => {
    workItem.workspace.nextOfKin = [
      {
        id: "kin",
        fullName: "Kin One",
        primary: true,
        relationshipCode: "PARENT",
        phoneNumber: "0700",
        email: "kin@example.test",
      },
      { id: "kin2", fullName: "Kin Two", primary: false },
    ];
    workItem.workspace.referees = [
      "SUBMITTED",
      "OPENED",
      "SENT",
      "REVOKED",
      "EXPIRED",
      "NOT_SENT",
    ].map((status, index) => ({
      id: `ref-${index}`,
      fullName: `Referee ${index}`,
      organisation: "School",
      email: "ref@example.test",
      invitationStatus: status,
      positionTitle: index ? null : "Principal",
      phoneNumber: index ? null : "0701",
    }));
    workItem.workspace.referees.push({
      id: "complete",
      fullName: "Complete Referee",
      invitationStatus: "SUBMITTED",
      referenceRelationshipToApplicant: "Teacher",
      yearsKnown: 4,
      recommendation: "STRONGLY_RECOMMEND",
      referenceSubmittedAt: "2026-08-20T09:00:00Z",
      referenceComments: "Excellent scientific ability",
    });
    workItem.workspace.employmentHistory = [
      {
        id: "current",
        positionTitle: "Intern",
        employerName: "Current Employer",
        startedOn: "2026-01-01",
        current: true,
      },
      {
        id: "past",
        positionTitle: "Assistant",
        employerName: "Past Employer",
        startedOn: "2025-01-01",
        endedOn: "2025-12-31",
        current: false,
      },
    ];
    await render();
    for (const text of [
      "Kin One",
      "Primary",
      "Reference received",
      "Invitation opened",
      "Invitation sent",
      "Revoked",
      "Expired",
      "Not Sent",
      "Not stated",
      "No comments provided.",
      "Excellent scientific ability",
      "Strongly Recommend",
      "Present",
      "Past Employer",
    ])
      expect(wrapper.text()).toContain(text);
    expect(wrapper.findAll('[data-testid="confidential-reference-response"]')).toHaveLength(2);
  });
  it("shows qualification variants with institution and identity-number fallbacks", async () => {
    workItem.workspace.qualifications = [
      qualificationFixture({
        id: "one",
        verificationStatus: "VERIFIED",
        examBody: null,
        institutionName: "Named college",
        centreNumber: null,
        results: [],
      }),
      qualificationFixture({
        id: "two",
        verificationStatus: "REJECTED",
        examBody: null,
        institutionName: null,
        yearWritten: null,
        candidateNumber: null,
      }),
      qualificationFixture({ id: "three" }),
    ];
    await render();
    for (const text of [
      "Named college",
      "Institution not captured",
      "No centre",
      "No candidate number",
      "No subject results captured.",
    ])
      expect(wrapper.text()).toContain(text);
    expect(
      wrapper.findAll("button").filter((button) => button.text() === "Verify qualification"),
    ).toHaveLength(1);
  });
});

describe("admissions workflow decisions through staff actions", () => {
  it("recalculates eligibility through the server action and reports failures", async () => {
    enable("RECALCULATE_ELIGIBILITY");
    await render();
    await clickButton(wrapper, "Recalculate eligibility");
    expect(writes()[0]).toEqual([
      "/api/admissions/applications/application/eligibility/recalculate",
      { method: "POST" },
    ]);
    context.request.mockRejectedValueOnce(new Error("Rules changed"));
    await clickButton(wrapper, "Recalculate eligibility");
    expect(context.showError).toHaveBeenCalledWith("Admissions action failed", "Rules changed");
  });
  it.each([
    ["RESOLVE_ELIGIBILITY", "Resolve eligibility", "ELIGIBLE", "eligibility-resolution", "outcome"],
    [
      "RECORD_ACADEMIC_RECOMMENDATION",
      "Record recommendation",
      "RECOMMEND_ADMIT",
      "academic-recommendation",
      "recommendation",
    ],
    ["RECORD_ADMISSION_DECISION", "Record final decision", "ADMIT", "decision", "decision"],
  ] as const)(
    "records %s with reason validation and the selected choice",
    async (action, label, outcome, suffix, field) => {
      enable(action);
      workItem.academicReview = null;
      await render();
      vi.mocked(Swal.fire)
        .mockResolvedValueOnce({ isConfirmed: true, value: outcome } as any)
        .mockResolvedValueOnce({
          isConfirmed: true,
          value: "  Satisfies verified requirements  ",
        } as any);
      await clickButton(wrapper, label);
      expect(dialog().inputValidator("short")).toContain("10 characters");
      expect(dialog().inputValidator("Evidence reviewed fully")).toBeUndefined();
      expect(writes()[0]).toEqual([
        `/api/admissions/applications/application/choices/choice/${suffix}`,
        { method: "POST", body: { [field]: outcome, reason: "Satisfies verified requirements" } },
      ]);
    },
  );
  it.each([0, 1])(
    "does not submit two-step decisions when dialog %s is cancelled",
    async (cancelledDialog) => {
      enable("RESOLVE_ELIGIBILITY", "RECORD_ACADEMIC_RECOMMENDATION", "RECORD_ADMISSION_DECISION");
      await render();
      for (const label of [
        "Resolve eligibility",
        "Record recommendation",
        "Record final decision",
      ]) {
        if (cancelledDialog)
          vi.mocked(Swal.fire).mockResolvedValueOnce({ isConfirmed: true, value: "ADMIT" } as any);
        vi.mocked(Swal.fire).mockResolvedValueOnce({ isConfirmed: false } as any);
        await clickButton(wrapper, label);
      }
      expect(writes()).toHaveLength(0);
    },
  );
  it("returns an academic recommendation with an actionable audited reason", async () => {
    enable("RETURN_ACADEMIC_RECOMMENDATION");
    workItem.academicReview = null;
    workItem.workspace.application.programmeChoices[0].choiceStatus = "ELIGIBLE";
    await render();
    await clickButton(wrapper, "Return to academic reviewer");
    expect(writes()[0]).toEqual([
      "/api/admissions/applications/application/choices/choice/academic-recommendation/return",
      { method: "POST", body: { reason: "Evidence checked and approved" } },
    ]);
    expect(dialog().inputValidator("short")).toContain("10 characters");
    expect(dialog().inputValidator("Recheck the verified grades")).toBeUndefined();
  });
  it.each([true, false])("handles correction to draft confirmed=%s", async (confirmed) => {
    await render();
    vi.mocked(Swal.fire).mockResolvedValueOnce({
      isConfirmed: confirmed,
      value: "  Correct the identity number  ",
    } as any);
    await clickButton(wrapper, "Request applicant correction");
    expect(dialog().inputValidator("short")).toContain("required correction");
    expect(dialog().inputValidator("Correct the identity number")).toBeUndefined();
    if (confirmed) {
      expect(writes()[0]).toEqual([
        "/api/admissions/applications/application/return-to-draft",
        { method: "POST", body: { reason: "Correct the identity number" } },
      ]);
      expect(showSuccess).toHaveBeenCalledWith("Application returned to draft", expect.any(String));
    } else expect(writes()).toHaveLength(0);
  });
  it("keeps correction available after failure", async () => {
    await render();
    context.request.mockRejectedValueOnce(new Error("Locked by decision"));
    await clickButton(wrapper, "Request applicant correction");
    expect(context.showError).toHaveBeenCalledWith(
      "Application could not be returned to draft",
      "Locked by decision",
    );
  });
  it.each([
    ["Verify qualification", "VERIFIED", undefined],
    ["Reject qualification", "REJECTED", "  Unreadable result evidence  "],
  ] as const)("records %s with version and normalized evidence", async (label, decision, value) => {
    await render();
    vi.mocked(Swal.fire).mockResolvedValueOnce({ isConfirmed: true, value } as any);
    await clickButton(wrapper, label);
    expect(writes()[0]).toEqual([
      "/api/admissions/applications/application/qualifications/qualification/decision",
      { method: "POST", body: { decision, reason: value?.trim() || null, expectedVersion: 9 } },
    ]);
    expect(dialog().inputValidator("short")).toBe(
      decision === "REJECTED"
        ? "Record at least 10 characters explaining what must be corrected."
        : undefined,
    );
    expect(dialog().inputValidator("Detailed evidence checked")).toBeUndefined();
    expect(showSuccess).toHaveBeenCalledWith(
      decision === "VERIFIED" ? "Qualification verified" : "Qualification rejected",
      expect.any(String),
    );
  });
  it("does not decide cancelled qualifications and recovers conflicts", async () => {
    await render();
    vi.mocked(Swal.fire).mockResolvedValueOnce({ isConfirmed: false } as any);
    await clickButton(wrapper, "Reject qualification");
    expect(writes()).toHaveLength(0);
    context.request.mockRejectedValueOnce(new Error("Stale qualification"));
    await clickButton(wrapper, "Verify qualification");
    expect(context.showError).toHaveBeenCalledWith(
      "Qualification decision could not be recorded",
      "Stale qualification",
    );
  });
  it.each(["approve", "reject"] as const)(
    "audits identity correction %s and surfaces server failures",
    async (decision) => {
      workItem.workspace.identityNameCorrection = { id: "correction", status: "REQUESTED" };
      await render();
      const label =
        decision === "approve" ? "Approve identity correction" : "Reject identity correction";
      await clickButton(wrapper, label);
      expect(writes()[0]).toEqual([
        `/api/admissions/identity-name-corrections/correction/${decision}`,
        { method: "POST", body: { reason: "Evidence checked and approved" } },
      ]);
      expect(dialog().inputValidator("short")).toContain("audit trail");
      expect(dialog().inputValidator("Identity document checked")).toBeUndefined();
      expect(showSuccess).toHaveBeenCalledWith(
        decision === "approve" ? "Official name synchronized" : "Name correction rejected",
        expect.any(String),
      );
      context.request.mockRejectedValueOnce(new Error("Identity lock"));
      await clickButton(wrapper, label);
      expect(context.showError).toHaveBeenCalledWith(
        decision === "approve"
          ? "Official name could not be synchronized"
          : "Correction could not be rejected",
        "Identity lock",
      );
    },
  );
  it("cancels identity correction and ignores stale resolved child events", async () => {
    workItem.workspace.identityNameCorrection = { id: "correction", status: "REQUESTED" };
    await render();
    vi.mocked(Swal.fire).mockResolvedValueOnce({ isConfirmed: false } as any);
    await clickButton(wrapper, "Approve identity correction");
    expect(writes()).toHaveLength(0);
    workItem.workspace.identityNameCorrection.status = "APPROVED";
    await clickButton(wrapper, "Refresh");
    vi.mocked(Swal.fire).mockClear();
    wrapper.findComponent(IdentityPanel).vm.$emit("approve");
    await flushPromises();
    expect(Swal.fire).not.toHaveBeenCalled();
    expect(writes()).toHaveLength(0);
  });
});

describe("admissions document evidence review", () => {
  it("switches among missing, rejected and uploaded evidence with clear preview states", async () => {
    workItem.workspace.documents.requirements.push(
      documentFixture({
        requirementCode: "MISSING",
        requirementName: "Missing certificate",
        documentId: null,
        state: "MISSING",
        fileName: null,
        mimeType: null,
        required: false,
      }),
      documentFixture({
        requirementCode: "REJECTED",
        requirementName: "Rejected evidence",
        documentId: "rejected",
        state: "REJECTED",
        rejectionReason: "Please upload a readable copy",
      }),
    );
    await render();
    await selectDocument("Missing certificate");
    expect(wrapper.text()).toContain("Optional upload missing");
    expect(wrapper.text()).toContain("No file uploaded");
    expect(wrapper.find('[data-testid="document-preview-frame"]').exists()).toBe(false);
    await selectDocument("Rejected evidence");
    expect(wrapper.text()).toContain("Please upload a readable copy");
    expect(wrapper.findAll("button").some((button) => button.text() === "Verify")).toBe(false);
    await selectDocument("Identity evidence");
    expect(wrapper.findAll("button").some((button) => button.text() === "Verify")).toBe(true);
  });
  it("recovers preview errors and handles missing files without network calls", async () => {
    const original = context.request.getMockImplementation()!;
    context.request.mockImplementation(async (path: string, options?: any) => {
      if (path.includes("uploads/")) throw new Error("Preview access expired");
      return original(path, options);
    });
    await render();
    expect(wrapper.text()).toContain("Preview unavailable");
    expect(wrapper.text()).toContain("Preview access expired");
    context.request.mockImplementation(original);
    await selectDocument("Identity evidence");
    expect(wrapper.text()).not.toContain("Preview access expired");
    workItem.workspace.documents.requirements = [
      documentFixture({ documentId: null, state: "MISSING", fileName: null }),
    ];
    await clickButton(wrapper, "Refresh");
    expect(wrapper.text()).toContain("Required upload missing");
    expect(wrapper.text()).toContain("No file uploaded");
  });
  it("keeps already-loaded evidence during refresh and clears previews when requirements disappear", async () => {
    await render();
    const previewCalls = () =>
      context.request.mock.calls.filter(([path]) => path.includes("disposition=inline"));
    expect(previewCalls()).toHaveLength(1);
    await clickButton(wrapper, "Refresh");
    expect(previewCalls()).toHaveLength(1);
    workItem.workspace.documents.requirements = [];
    await clickButton(wrapper, "Refresh");
    expect(wrapper.text()).toContain("No documents configured");
    expect(wrapper.find('[data-testid="document-preview-frame"]').exists()).toBe(false);
  });
  it("selects fallback uploaded evidence when pending files are absent", async () => {
    workItem.workspace.documents.requirements = [documentFixture({ state: "VERIFIED" })];
    workItem.workspace.documents.requiredDocumentsVerified = true;
    await render();
    expect(wrapper.find('[data-testid="document-preview-frame"]').exists()).toBe(true);
    expect(wrapper.findAll("button").some((button) => button.text() === "Verify")).toBe(false);
  });
  it.each([
    ["Verify", "verify", undefined],
    ["Reject", "reject", "  Replace unreadable evidence  "],
  ] as const)(
    "records %s with optimistic version and waits for projected decision",
    async (label, action, value) => {
      await render();
      vi.mocked(Swal.fire).mockResolvedValueOnce({ isConfirmed: true, value } as any);
      await clickButton(wrapper, label);
      expect(writes()[0]).toEqual([
        `/api/documents/uploads/document/${action}`,
        {
          method: "POST",
          body:
            action === "verify"
              ? { expectedVersion: 7, comment: null }
              : { expectedVersion: 7, reason: "Replace unreadable evidence" },
        },
      ]);
      expect(showSuccess).toHaveBeenCalledWith(
        action === "verify" ? "Document verified" : "Replacement requested",
        expect.any(String),
      );
      expect(wrapper.findAll("button").some((button) => button.text() === "Verify")).toBe(false);
      if (action === "reject") {
        expect(dialog().inputValidator("short")).toContain("10 characters");
        expect(dialog().inputValidator("Readable replacement required")).toBeUndefined();
      }
    },
  );
  it.each(["Verify", "Reject"])("preserves evidence when %s is cancelled", async (label) => {
    await render();
    vi.mocked(Swal.fire).mockResolvedValueOnce({ isConfirmed: false } as any);
    await clickButton(wrapper, label);
    expect(writes()).toHaveLength(0);
    expect(showSuccess).not.toHaveBeenCalled();
  });
  it("ignores pending-state actions without an uploaded document", async () => {
    workItem.workspace.documents.requirements = [documentFixture({ documentId: null })];
    await render();
    await clickButton(wrapper, "Verify");
    await clickButton(wrapper, "Reject");
    expect(Swal.fire).not.toHaveBeenCalled();
    expect(writes()).toHaveLength(0);
  });
  it("keeps document decisions recoverable after version conflicts", async () => {
    await render();
    context.request.mockRejectedValueOnce(new Error("Document version changed"));
    await clickButton(wrapper, "Verify");
    expect(context.showError).toHaveBeenCalledWith(
      "Document decision could not be recorded",
      "Document version changed",
    );
    expect(wrapper.findAll("button").some((button) => button.text() === "Verify")).toBe(true);
  });
  it("retries eventual document projection before reporting verification", async () => {
    vi.useFakeTimers();
    await render();
    const original = context.request.getMockImplementation()!;
    let projectedReads = 0;
    context.request.mockImplementation(async (path: string, options?: any) => {
      if (path.endsWith("/verify")) return {};
      if (path.startsWith("/api/admissions/work-items/")) {
        projectedReads++;
        if (projectedReads === 2) workItem.workspace.documents.requirements[0].state = "VERIFIED";
      }
      return original(path, options);
    });
    await clickButton(wrapper, "Verify");
    expect(showSuccess).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(200);
    await flushPromises();
    expect(projectedReads).toBe(2);
    expect(showSuccess).toHaveBeenCalledWith("Document verified", expect.any(String));
  });
  it("downloads secure evidence with the original filename and isolates download errors", async () => {
    let clicked: HTMLAnchorElement | undefined;
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      clicked = this;
    });
    await render();
    await clickButton(wrapper, "Download");
    expect(context.request).toHaveBeenCalledWith("/api/documents/uploads/document/download");
    expect(clicked?.download).toBe("identity.pdf");
    expect(clicked?.rel).toBe("noopener noreferrer");
    expect(clicked?.href).toBe(previewDownload.downloadUrl);
    context.request.mockRejectedValueOnce(new Error("Download expired"));
    await clickButton(wrapper, "Download");
    expect(context.showError).toHaveBeenCalledWith(
      "Document could not be downloaded",
      "Download expired",
    );
  });
  it.each(["image/png", "application/pdf", "application/octet-stream"])(
    "expands %s evidence in a safe document container",
    async (mimeType) => {
      previewDownload.mimeType = mimeType;
      workItem.workspace.documents.requirements[0].fileName = null;
      await render();
      const popup = document.createElement("div");
      const heading = document.createElement("h2");
      const container = document.createElement("div");
      const host = document.createElement("div");
      host.dataset.expandedDocumentPreview = "";
      container.append(host);
      popup.append(heading, container);
      vi.mocked(Swal.getPopup).mockReturnValue(popup);
      vi.mocked(Swal.getTitle).mockReturnValue(heading);
      vi.mocked(Swal.getHtmlContainer).mockReturnValue(container);
      await clickButton(wrapper, "Expand preview");
      expect(dialog().title).toBe("Identity evidence");
      dialog().didOpen();
      if (mimeType.startsWith("image/")) {
        expect(wrapper.find('[data-testid="document-preview"] img').exists()).toBe(true);
        expect(host.querySelector("img")?.alt).toBe("Identity evidence expanded preview");
      } else if (mimeType === "application/pdf") {
        expect(host.querySelector("iframe")?.src).toBe(previewDownload.downloadUrl);
        dialog().willClose();
        expect(host.querySelector("iframe")?.src).toBe("about:blank");
      } else {
        expect(wrapper.text()).toContain("Inline preview is not available for this file type");
        expect(host.textContent).toContain("Download the document to review it.");
        vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
        await clickButton(wrapper, "Download document");
        expect(context.request).toHaveBeenCalledWith("/api/documents/uploads/document/download");
      }
    },
  );
  it("tolerates preview dialog teardown before its host is mounted", async () => {
    await render();
    await clickButton(wrapper, "Expand preview");
    expect(() => dialog().didOpen()).not.toThrow();
    expect(() => dialog().willClose()).not.toThrow();
  });
});

describe("admissions offer document operations", () => {
  it.each(["", "Complete the final examination"])(
    "saves offer terms with condition text %s",
    async (conditions) => {
      offerFixture();
      enable("UPDATE_OFFER");
      await render();
      const type = document.createElement("select");
      type.id = "offer-type";
      const option = document.createElement("option");
      option.value = "CONDITIONAL";
      type.append(option);
      const textarea = document.createElement("textarea");
      textarea.id = "offer-conditions";
      textarea.value = conditions;
      document.body.append(type, textarea);
      vi.mocked(Swal.fire).mockImplementationOnce(
        async (options: any) => ({ isConfirmed: true, value: options.preConfirm() }) as any,
      );
      await clickButton(wrapper, "Edit offer terms");
      expect(writes()[0]).toEqual([
        "/api/admissions/offers/offer",
        { method: "PUT", body: { offerType: "CONDITIONAL", conditionsText: conditions || null } },
      ]);
      expect(dialog().html).toContain("AUG-2026");
    },
  );
  it.each([{ isConfirmed: false }, { isConfirmed: true, value: null }])(
    "leaves offer terms unchanged when dialog returns %j",
    async (result) => {
      offerFixture();
      enable("UPDATE_OFFER");
      await render();
      vi.mocked(Swal.fire).mockResolvedValueOnce(result as any);
      await clickButton(wrapper, "Edit offer terms");
      expect(writes()).toHaveLength(0);
    },
  );
  it("reports offer update errors and uses intake fallback in terms", async () => {
    offerFixture();
    enable("UPDATE_OFFER");
    workItem.workspace.application.intakeCode = null;
    await render();
    vi.mocked(Swal.fire).mockResolvedValueOnce({
      isConfirmed: true,
      value: { type: "FIRM", conditions: "" },
    } as any);
    context.request.mockRejectedValueOnce(new Error("Offer locked"));
    await clickButton(wrapper, "Edit offer terms");
    expect(dialog().html).toContain("the intake");
    expect(context.showError).toHaveBeenCalledWith(
      "Offer terms could not be saved",
      "Offer locked",
    );
  });
  it("confirms publication and audits email retries", async () => {
    offerFixture();
    enable("PUBLISH_AND_SEND", "RETRY_EMAIL");
    workItem.publications = [{ emailStatus: "FAILED" }];
    await render();
    expect(wrapper.text()).toContain("Published · Failed");
    confirmAction.mockResolvedValueOnce(false);
    await clickButton(wrapper, "Publish and send");
    expect(writes()).toHaveLength(0);
    await clickButton(wrapper, "Publish and send");
    expect(writes()[0]).toEqual([
      "/api/admissions/offers/offer/publish-and-send",
      { method: "POST" },
    ]);
    await clickButton(wrapper, "Retry email");
    expect(writes()[1]).toEqual([
      "/api/admissions/offers/offer/email-retry",
      { method: "POST", body: { reason: "Evidence checked and approved" } },
    ]);
    expect(dialog().inputValidator("short")).toContain("10 characters");
    expect(dialog().inputValidator("Retry delivery after bounce")).toBeUndefined();
  });
  it("cancels email retries and recommendation returns", async () => {
    offerFixture();
    enable("RETRY_EMAIL", "RETURN_ACADEMIC_RECOMMENDATION");
    await render();
    vi.mocked(Swal.fire).mockResolvedValue({ isConfirmed: false } as any);
    await clickButton(wrapper, "Retry email");
    await clickButton(wrapper, "Return to academic reviewer");
    expect(writes()).toHaveLength(0);
  });
  it.each([
    ["Preview offer letter", "inline"],
    ["Download offer letter", "attachment"],
  ] as const)(
    "opens %s from the stored document without retaining opener access",
    async (label, disposition) => {
      offerFixture();
      const tab = previewTab();
      await render();
      await clickButton(wrapper, label);
      expect(context.request).toHaveBeenCalledWith(
        `/api/documents/official/download?disposition=${disposition}`,
      );
      expect(tab.opener).toBeNull();
      expect(tab.location.href).toBe(previewDownload.downloadUrl);
      expect(tab.close).not.toHaveBeenCalled();
    },
  );
  it("handles popup blocking before fetching the official PDF", async () => {
    offerFixture();
    vi.spyOn(window, "open").mockReturnValue(null);
    await render();
    await clickButton(wrapper, "Preview offer letter");
    expect(context.showError).toHaveBeenCalledWith(
      "Offer letter could not be opened",
      expect.stringContaining("blocked"),
    );
    expect(context.request).not.toHaveBeenCalledWith(
      "/api/documents/official/download?disposition=inline",
    );
  });
  it("closes a failed official-document tab and reports missing stored letters", async () => {
    offerFixture();
    const tab = previewTab();
    await render();
    context.request.mockRejectedValueOnce(new Error("Official file unavailable"));
    await clickButton(wrapper, "Preview offer letter");
    expect(tab.close).toHaveBeenCalledOnce();
    expect(context.showError).toHaveBeenCalledWith(
      "Offer letter could not be opened",
      "Official file unavailable",
    );
    workItem.documentVersions = [{ status: "FAILED", generatedDocumentId: null }];
    await clickButton(wrapper, "Refresh");
    expect(wrapper.text()).toContain("Offer letter not available");
    expect(
      wrapper.findAll("button").some((button) => button.text() === "Preview offer letter"),
    ).toBe(false);
  });
  it("generates a stored offer letter and redirects the tab to its secure URL", async () => {
    offerFixture();
    enable("GENERATE_OFFER_DOCUMENT");
    const tab = previewTab();
    await render();
    await clickButton(wrapper, "Generate and preview offer letter");
    expect(writes()[0]).toEqual([
      "/api/admissions/offers/offer/document-generation",
      { method: "POST" },
    ]);
    expect(tab.location.href).toBe(previewDownload.downloadUrl);
    expect(tab.opener).toBeNull();
  });
  it("reports blocked previews after successful generation without discarding the stored PDF", async () => {
    offerFixture();
    enable("GENERATE_OFFER_DOCUMENT");
    vi.spyOn(window, "open").mockReturnValue(null);
    await render();
    await clickButton(wrapper, "Generate and preview offer letter");
    expect(context.showError).toHaveBeenCalledWith(
      "Offer letter generated",
      expect.stringContaining("blocked"),
    );
    expect(wrapper.text()).toContain("Preview offer letter");
  });
  it.each(["Renderer rejected template", null])(
    "reports failed offer generation with reason %s and closes its tab",
    async (failureReason) => {
      offerFixture();
      enable("GENERATE_OFFER_DOCUMENT");
      workItem.documentVersions[0].status = "FAILED";
      workItem.documentVersions[0].failureReason = failureReason;
      const tab = previewTab();
      await render();
      await clickButton(wrapper, "Generate and preview offer letter");
      expect(context.showError).toHaveBeenCalledWith(
        "Offer letter could not be generated",
        failureReason || "The offer-letter PDF generation failed.",
      );
      expect(tab.close).toHaveBeenCalledOnce();
    },
  );
  it("rejects a stored generation without a document reference", async () => {
    offerFixture();
    enable("GENERATE_OFFER_DOCUMENT");
    workItem.documentVersions[0].generatedDocumentId = null;
    const tab = previewTab();
    await render();
    await clickButton(wrapper, "Generate and preview offer letter");
    expect(context.showError).toHaveBeenCalledWith(
      "Offer letter could not be generated",
      "The generated offer letter was stored without a document reference.",
    );
    expect(tab.close).toHaveBeenCalledOnce();
  });
  it("polls a queued generation until its exact version is stored", async () => {
    vi.useFakeTimers();
    offerFixture();
    enable("GENERATE_OFFER_DOCUMENT");
    workItem.documentVersions = [{ version: 1, status: "STORED", generatedDocumentId: "old" }];
    const tab = previewTab();
    await render();
    await clickButton(wrapper, "Generate and preview offer letter");
    expect(tab.location.href).toBe("about:blank");
    workItem.documentVersions.push({
      version: 3,
      status: "STORED",
      generatedDocumentId: "official",
    });
    await vi.advanceTimersByTimeAsync(500);
    await flushPromises();
    expect(tab.location.href).toBe(previewDownload.downloadUrl);
    expect(context.showError).not.toHaveBeenCalled();
  });
  it("ends bounded polling when PDF generation never finishes", async () => {
    vi.useFakeTimers();
    offerFixture();
    enable("GENERATE_OFFER_DOCUMENT");
    workItem.documentVersions = [];
    const tab = previewTab();
    await render();
    await clickButton(wrapper, "Generate and preview offer letter");
    await vi.advanceTimersByTimeAsync(30000);
    await flushPromises();
    expect(context.showError).toHaveBeenCalledWith(
      "Offer letter could not be generated",
      "The offer letter is still being generated. Refresh the case before trying again.",
    );
    expect(tab.close).toHaveBeenCalledOnce();
    expect(
      context.request.mock.calls.filter(
        ([path]) => path === "/api/admissions/work-items/application",
      ),
    ).toHaveLength(61);
  });
  it("ignores stale server offer actions when no offer exists", async () => {
    enable("UPDATE_OFFER", "GENERATE_OFFER_DOCUMENT", "PUBLISH_AND_SEND", "RETRY_EMAIL");
    await render();
    for (const label of [
      "Edit offer terms",
      "Generate and preview offer letter",
      "Publish and send",
      "Retry email",
    ])
      await clickButton(wrapper, label);
    expect(writes()).toHaveLength(0);
    expect(Swal.fire).not.toHaveBeenCalled();
    expect(confirmAction).not.toHaveBeenCalled();
  });
});
