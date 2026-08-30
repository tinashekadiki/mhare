// Author: Tinashe K
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { defineComponent, ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Swal from "sweetalert2";
import InlineFormComponent from "@emhare/portal-shell/components/forms/EmhareInlineRecordForm.vue";
import ApplicationWorkspace from "../../pages/applications/[applicationId].vue";
import { campusStubs } from "../../../../tests/unit/support/campus-page";
import {
  clickButton,
  operationalContext,
  setField,
} from "../../../../tests/unit/support/operational-page";

vi.mock("sweetalert2", () => ({ default: { fire: vi.fn() } }));
const FormField = defineComponent({
  props: ["label", "type", "modelValue", "items", "disabled", "readonly", "multiple"],
  emits: ["update:modelValue"],
  components: {
    SelectControl: campusStubs.USelect,
    InputControl: campusStubs.UInput,
    ToggleControl: campusStubs.UCheckbox,
  },
  methods: {
    selectMultiple(event: Event) {
      this.$emit(
        "update:modelValue",
        Array.from((event.target as HTMLSelectElement).selectedOptions, (option) => option.value),
      );
    },
  },
  template:
    '<label class="field" :data-label="label">{{label}}<select v-if="type===\'multi-select\'" :value="modelValue" multiple :disabled="disabled" @change="selectMultiple"><option v-for="item in items" :key="item.value" :value="item.value">{{item.label}}</option></select><SelectControl v-else-if="type===\'select\'||type===\'searchable-select\'" :model-value="modelValue" :items="items" :disabled="disabled" @update:model-value="$emit(\'update:modelValue\',$event)"/><ToggleControl v-else-if="type===\'toggle\'" :model-value="modelValue" :disabled="disabled" @update:model-value="$emit(\'update:modelValue\',$event)"/><InputControl v-else :model-value="modelValue" :disabled="disabled" :readonly="readonly" @update:model-value="$emit(\'update:modelValue\',type===\'number\'?Number($event):$event)"/></label>',
});
const Evidence = defineComponent({
  props: ["label", "documentTypeCode", "existingDocumentId", "existingState", "disabled"],
  emits: ["uploaded", "extraction-ready"],
  template: '<section class="evidence"><p>{{label}} {{existingState}}</p></section>',
});
const Identity = defineComponent({
  props: ["correction", "loading", "editable"],
  emits: ["corrected", "request", "replace"],
  template:
    '<aside data-testid="name-mismatch">{{correction.status}} {{correction.documentName?.firstName}}</aside>',
});
const Stepper = defineComponent({
  props: ["steps", "currentStep"],
  emits: ["update:current-step"],
  template:
    '<nav class="steps"><button v-for="step in steps" :key="step.id" :disabled="step.disabled" :data-state="step.status" @click="$emit(\'update:current-step\',step.id)">{{step.title}}</button></nav>',
});
const stubs = {
  ...campusStubs,
  EmhareFormField: FormField,
  EmhareEvidenceUploader: Evidence,
  EmhareIdentityNameMismatchPanel: Identity,
  EmhareVerticalStepper: Stepper,
  EmhareTopNav: defineComponent({
    template: '<nav><slot name="meta"/><slot name="actions"/></nav>',
  }),
  EmhareFeedbackState: defineComponent({
    props: ["title", "description", "actionLabel"],
    emits: ["action"],
    template:
      '<aside>{{title}} {{description}}<button v-if="actionLabel" @click="$emit(\'action\')">{{actionLabel}}</button></aside>',
  }),
  EmhareInlineRecordForm: InlineFormComponent,
  EmhareDraftSaveIndicator: defineComponent({
    props: ["state", "savedAt"],
    template: "<output>Draft: {{state}} {{savedAt}}</output>",
  }),
  EmhareFormSection: campusStubs.UCard,
  EmhareReviewField: defineComponent({
    props: ["label", "value"],
    template: '<p :data-review="label">{{label}}: {{value}}</p>',
  }),
  UProgress: true,
  USeparator: true,
};
const sectionDefinitions = [
  ["PERSONAL_DETAILS", "Applicant details"],
  ["NEXT_OF_KIN", "Next of kin"],
  ["PRIOR_UZ_STUDY", "Prior UZ study"],
  ["PROFESSIONAL_ACHIEVEMENTS", "Professional achievements"],
  ["QUALIFICATIONS", "Qualifications"],
  ["PROGRAMME_CHOICES", "Programme choices"],
  ["DOCUMENTS", "Supporting documents"],
  ["PAYMENT", "Application fee"],
  ["REVIEW_DECLARATION", "Review and declaration"],
] as const;
let wrapper: VueWrapper;
let context: ReturnType<typeof operationalContext>;
let workspace: any;
async function nextSection() {
  await wrapper.findAll("footer button").at(-1)!.trigger("click");
  await flushPromises();
}

let startOptions: any;
let prefill: any;
let referenceData: any;
let paymentProofs: any[];
let paymentOptions: any;
const authenticated = ref(true);
const loadUser = vi.fn();
const syncCoreUser = vi.fn();
const push = vi.fn();
const showSuccess = vi.fn();
const confirmAction = vi.fn();
const openOfferLetter = vi.fn();
const name = { firstName: "Ada", middleNames: null, lastName: "Example" };
function identityCorrection(overrides = {}) {
  return {
    id: null,
    documentId: "identity-document",
    status: "MISMATCH",
    registeredName: { ...name },
    documentName: { ...name, firstName: "Adah" },
    ...overrides,
  };
}
function documentRequirement(overrides = {}) {
  return {
    requirementCode: "NATIONAL_ID",
    requirementName: "National ID",
    required: true,
    captureSectionCode: "PERSONAL_DETAILS",
    documentId: null,
    state: "MISSING",
    fileName: null,
    mimeType: null,
    ...overrides,
  };
}
function fixture() {
  return {
    application: {
      id: "application",
      applicationNumber: "APP-001",
      applicantNumber: "A001",
      status: "DRAFT",
      applicationTypeId: "type",
      applicationTypeName: "Undergraduate",
      intakeId: "intake",
      intakeCode: "2026-S1",
      programmeChoices: [],
      paymentRequired: false,
      paymentClearanceStatus: "NOT_REQUIRED",
      canEnterReview: true,
      payment: null,
      calculatedTotalPoints: 14,
    },
    profile: {
      applicantCategoryCode: "LOCAL",
      firstName: "Ada",
      lastName: "Example",
      primaryEmail: "ada@example.test",
      dateOfBirth: "2000-01-01",
      genderCode: "FEMALE",
      nationalIdNumber: "123456789A12",
      countryId: "zw",
      nationalityCountryId: "zw",
      primaryPhone: "+263771234567",
      residentialAddress: "Harare",
      missingRequiredFields: [],
      updatedAt: "2026-08-01",
      version: 4,
    },
    sections: sectionDefinitions.map(([code, sectionName], index) => ({
      id: code,
      code,
      name: sectionName,
      required: true,
      repeatable: [
        "NEXT_OF_KIN",
        "EMPLOYMENT_HISTORY",
        "REFEREES",
        "QUALIFICATIONS",
        "PROGRAMME_CHOICES",
      ].includes(code),
      minimumRecords: 1,
      sortOrder: index,
      status: "IN_PROGRESS",
      completionSummary: "Complete this section",
      version: 0,
    })),
    nextOfKin: [],
    employmentHistory: [],
    referees: [],
    qualifications: [],
    programmeEntryPreferences: [],
    professionalAchievements: [],
    professionalAchievementsDeclaredNone: false,
    priorUzDeclaration: null,
    identityNameCorrection: null,
    documents: {
      requirements: [],
      requiredDocumentsUploaded: true,
      requiredDocumentsVerified: false,
    },
    readyForSubmission: false,
    missingRequirements: ["Next of kin"],
    declarationAcceptedAt: null,
    declarationVersion: null,
  };
}
function qualification(overrides = {}) {
  return {
    id: "sitting",
    level: "A_LEVEL",
    awardTypeCode: null,
    examBody: { id: "zimsec", name: "ZIMSEC" },
    institutionName: "Example School",
    yearWritten: 2025,
    countryId: "zw",
    centreNumber: "CENTRE",
    candidateNumber: "CANDIDATE",
    documentId: "certificate",
    durationMonths: null,
    verificationStatus: "CAPTURED",
    version: 8,
    results: [
      {
        id: "result",
        subject: { id: "physics" },
        subjectNameSnapshot: "Physics",
        grade: "A",
        principalSubject: true,
        points: 5,
        version: 2,
      },
    ],
    ...overrides,
  };
}
beforeEach(() => {
  vi.useFakeTimers();
  context = operationalContext();
  authenticated.value = true;
  for (const mock of [loadUser, syncCoreUser, push, showSuccess, openOfferLetter])
    mock.mockReset().mockResolvedValue(undefined);
  confirmAction.mockReset().mockResolvedValue(true);
  vi.mocked(Swal.fire)
    .mockReset()
    .mockResolvedValue({
      isConfirmed: true,
      value: "  Correct official identity spelling  ",
    } as any);
  vi.stubGlobal("useRoute", () => ({ params: { applicationId: "application" } }));
  vi.stubGlobal("useRouter", () => ({ push }));
  vi.stubGlobal("useEmhareAuth", () => ({ authenticated, loadUser, syncCoreUser }));
  vi.stubGlobal("useApplicantOfferLetter", () => ({ openingOfferId: ref(null), openOfferLetter }));
  vi.stubGlobal("useEmhareConfirm", () => ({
    confirmAction,
    showSuccess,
    showError: context.showError,
  }));
  workspace = fixture();
  prefill = {
    personalFields: {},
    qualificationResults: [],
    warnings: [],
    identityNameMismatch: null,
  };
  startOptions = {
    applicationTypes: [],
    routes: [
      {
        applicationTypeId: "type",
        intakeId: "intake",
        programmes: [
          {
            id: "science",
            code: "BSC",
            name: "Science",
            owningAcademicUnitName: "Science Faculty",
            programmeVersionCode: "2026",
            minimumEntryOptionSelections: 1,
            maximumEntryOptionSelections: 2,
            entryOptions: [
              { id: "physics-option", code: "PHY", name: "Physics specialisation" },
              { id: "maths-option", code: "MAT", name: "Mathematics specialisation" },
            ],
          },
          { id: "arts", code: "BA", name: "Arts", entryOptions: [] },
        ],
      },
    ],
  };
  referenceData = {
    examBodies: [{ id: "zimsec", code: "ZIMSEC", name: "ZIMSEC" }],
    oLevelSubjects: [{ id: "maths", code: "MAT", name: "Mathematics", scienceSubject: false }],
    aLevelSubjects: [{ id: "physics", code: "PHY", name: "Physics", scienceSubject: true }],
    otherSubjects: [],
  };
  paymentProofs = [];
  paymentOptions = { onlinePayment: { available: false }, bankAccounts: [] };
  context.request.mockImplementation(async (path: string, options?: any) => {
    if (path.endsWith("/prefill") || path.includes("/prefill?")) return structuredClone(prefill);
    if (path.endsWith("/ocr-reading"))
      return identityCorrection({
        documentName: {
          firstName: options.body.firstName,
          middleNames: options.body.middleNames,
          lastName: options.body.lastName,
        },
      });
    if (path.endsWith("/identity-name-correction/request"))
      return identityCorrection({ id: "request", status: "REQUESTED" });
    if (options?.method || path.endsWith("/workspace")) return structuredClone(workspace);
    if (path.includes("/start-options")) return structuredClone(startOptions);
    if (path === "/api/admissions/qualification-reference-data")
      return structuredClone(referenceData);
    if (path === "/api/core/reference/countries")
      return [{ id: "zw", iso2Code: "ZW", name: "Zimbabwe", nationalityName: "Zimbabwean" }];
    if (path.endsWith("/payment-options")) return paymentOptions;
    if (path.startsWith("/api/documents/uploads?")) return paymentProofs;
    if (path.startsWith("/api/documents/uploads/"))
      return {
        documentId: "evidence",
        downloadUrl: "https://documents.example.test/evidence",
        originalFileName: "evidence.pdf",
        mimeType: "application/pdf",
      };
    if (path === "/api/admissions/offers/mine") return [];
    throw new Error(`Unexpected request ${path}`);
  });
});
afterEach(() => {
  wrapper?.unmount();
  vi.clearAllTimers();
  vi.useRealTimers();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  document.body.innerHTML = "";
});
async function render(attach = false) {
  wrapper = mount(ApplicationWorkspace, {
    ...(attach ? { attachTo: document.body } : {}),
    global: { stubs },
  });
  await flushPromises();
}
async function section(label: string) {
  await clickButton(wrapper, label);
}
function field(label: string, index = 0) {
  return wrapper
    .findAllComponents(FormField)
    .filter((component) => component.props("label") === label)[index]!;
}
async function emitField(label: string, value: unknown, index = 0) {
  field(label, index).vm.$emit("update:modelValue", value);
  await flushPromises();
}
function evidence(label: string) {
  return wrapper
    .findAllComponents(Evidence)
    .find((component) => component.props("label") === label)!;
}
async function extraction(label: string, documentId = "identity-document") {
  evidence(label).vm.$emit("extraction-ready", { documentId });
  await flushPromises();
}
function writes() {
  return context.request.mock.calls.filter(([, options]) => options?.method);
}
async function uploadEvidence(label: string, documentId = "identity-document") {
  evidence(label).vm.$emit("uploaded", {
    id: documentId,
    originalFileName: "identity.pdf",
    mimeType: "application/pdf",
    checksumSha256: "abc",
    version: 3,
  });
  await flushPromises();
}

describe("applicant identity extraction and governed corrections", () => {
  it("requires authenticated identity before loading private workspace data", async () => {
    authenticated.value = false;
    await render();
    expect(loadUser).toHaveBeenCalledOnce();
    expect(syncCoreUser).not.toHaveBeenCalled();
    expect(context.request).not.toHaveBeenCalled();
  });
  it.each([
    ["M", "MALE", "1/2/2001", "2001-02-01"],
    [" female ", "FEMALE", "2001-02-03", "2001-02-03"],
    ["OTHER", "OTHER", "3-4-2002", "2002-04-03"],
  ] as const)(
    "normalizes extracted %s and %s without overwriting registered names",
    async (gender, normalizedGender, birthDate, normalizedDate) => {
      workspace.profile = {
        ...workspace.profile,
        dateOfBirth: null,
        genderCode: null,
        nationalIdNumber: null,
        passportNumber: null,
        middleNames: null,
        placeOfBirth: null,
      };
      workspace.documents.requirements = [documentRequirement()];
      prefill.personalFields = {
        firstName: "Different",
        lastName: "Wrong",
        middleNames: "  Jane  ",
        genderCode: gender,
        dateOfBirth: birthDate,
        nationalIdNumber: " ab123 ",
        passportNumber: " p123 ",
        placeOfBirth: "  Harare  ",
      };
      prefill.warnings = ["Check original evidence", "Confirm spelling"];
      await render();
      await uploadEvidence("National ID");
      await extraction("National ID");
      expect(field("First name").props("modelValue")).toBe("Ada");
      expect(field("Last name").props("modelValue")).toBe("Example");
      expect(field("Middle names").props("modelValue")).toBe("Jane");
      expect(field("Gender").props("modelValue")).toBe(normalizedGender);
      expect(field("Date of birth").props("modelValue")).toBe(normalizedDate);
      expect(field("National ID number").props("modelValue")).toBe("AB123");
      expect(context.notify).toHaveBeenCalledWith(
        expect.objectContaining({
          title: "Check the extracted details",
          description: "Check original evidence Confirm spelling",
        }),
      );
      await clickButton(wrapper, "Save");
      expect(writes().at(-1)![1].body).toMatchObject({
        passportNumber: "P123",
        placeOfBirth: "Harare",
      });
    },
  );
  it("preserves captured personal values and offers manual entry when extraction fails", async () => {
    workspace.documents.requirements = [documentRequirement()];
    prefill.personalFields = {
      dateOfBirth: "2/3/1999",
      genderCode: "M",
      nationalIdNumber: "WRONG",
      middleNames: 123,
    };
    await render();
    await uploadEvidence("National ID");
    await extraction("National ID");
    expect(field("Date of birth").props("modelValue")).toBe("2000-01-01");
    expect(field("Gender").props("modelValue")).toBe("FEMALE");
    context.request.mockRejectedValueOnce(new Error("OCR service unavailable"));
    await extraction("National ID");
    expect(context.notify).toHaveBeenCalledWith(
      expect.objectContaining({
        title: "Enter the details manually",
        description: "OCR service unavailable",
      }),
    );
    expect(evidence("National ID").props("existingDocumentId")).toBe("identity-document");
  });
  it("hydrates existing mismatch evidence once and preserves it when the same file is re-emitted", async () => {
    workspace.documents.requirements = [
      documentRequirement({ documentId: "identity-document", state: "PENDING" }),
    ];
    prefill.identityNameMismatch = identityCorrection();
    await render();
    expect(wrapper.find('[data-testid="name-mismatch"]').exists()).toBe(true);
    await uploadEvidence("National ID");
    expect(wrapper.find('[data-testid="name-mismatch"]').exists()).toBe(true);
    await section("Next of kin");
    await setField(wrapper, "Full name", "Kin");
    await setField(wrapper, "Relationship", "PARENT");
    await setField(wrapper, "Phone number", "+263771234567");
    await clickButton(wrapper, "Save");
    await section("Applicant details");
    expect(context.request.mock.calls.filter(([path]) => path.endsWith("/prefill"))).toHaveLength(
      1,
    );
    await uploadEvidence("National ID", "replacement");
    expect(wrapper.find('[data-testid="name-mismatch"]').exists()).toBe(false);
  });
  it.each([true, false])(
    "corrects OCR reading and clears the mismatch when names match=%s",
    async (matching) => {
      workspace.identityNameCorrection = identityCorrection({ id: "correction" });
      await render();
      const correctedName = { ...name, firstName: matching ? " ada " : "Another" };
      wrapper.findComponent(Identity).vm.$emit("corrected", correctedName);
      await flushPromises();
      expect(writes()[0]).toEqual([
        "/api/admissions/applications/application/identity-name-correction/ocr-reading",
        { method: "PUT", body: { documentId: "identity-document", ...correctedName } },
      ]);
      expect(showSuccess).toHaveBeenCalledWith("OCR reading corrected", expect.any(String));
      expect(wrapper.findComponent(Identity).exists()).toBe(!matching);
    },
  );
  it("retains different surnames after OCR correction and reports correction errors", async () => {
    workspace.identityNameCorrection = identityCorrection();
    await render();
    wrapper.findComponent(Identity).vm.$emit("corrected", { ...name, lastName: "Other" });
    await flushPromises();
    expect(wrapper.findComponent(Identity).exists()).toBe(true);
    context.request.mockRejectedValueOnce(new Error("Correction conflict"));
    wrapper.findComponent(Identity).vm.$emit("corrected", name);
    await flushPromises();
    expect(context.showError).toHaveBeenCalledWith(
      "OCR reading could not be saved",
      "Correction conflict",
    );
  });
  it("requests an audited official correction without changing the account name", async () => {
    workspace.identityNameCorrection = identityCorrection();
    await render();
    wrapper.findComponent(Identity).vm.$emit("request", name);
    await flushPromises();
    const dialog = vi.mocked(Swal.fire).mock.calls[0]![0] as any;
    expect(dialog.inputValidator("short")).toContain("10 characters");
    expect(dialog.inputValidator("Official document spelling")).toBeUndefined();
    expect(writes()[0]).toEqual([
      "/api/admissions/applications/application/identity-name-correction/request",
      {
        method: "POST",
        body: {
          documentId: "identity-document",
          ...name,
          reason: "Correct official identity spelling",
        },
      },
    ]);
    expect(wrapper.text()).toContain("REQUESTED");
    expect(field("First name").props("modelValue")).toBe("Ada");
    expect(showSuccess).toHaveBeenCalledWith(
      "Official-name correction requested",
      expect.any(String),
    );
  });
  it("cancels and safely retries an official-name correction", async () => {
    workspace.identityNameCorrection = identityCorrection();
    await render();
    vi.mocked(Swal.fire).mockResolvedValueOnce({ isConfirmed: false } as any);
    wrapper.findComponent(Identity).vm.$emit("request", name);
    await flushPromises();
    expect(writes()).toHaveLength(0);
    context.request.mockRejectedValueOnce(new Error("Request service unavailable"));
    wrapper.findComponent(Identity).vm.$emit("request", name);
    await flushPromises();
    expect(context.showError).toHaveBeenCalledWith(
      "Correction request could not be submitted",
      "Request service unavailable",
    );
  });
  it("replaces identity evidence from the correction panel's public action", async () => {
    workspace.identityNameCorrection = identityCorrection();
    workspace.documents.requirements = [
      documentRequirement({ documentId: "identity-document", state: "PENDING" }),
    ];
    await render(true);
    const region = wrapper.get('[data-evidence-document-id="identity-document"]');
    const input = document.createElement("input");
    input.type = "file";
    region.element.append(input);
    const clicked = vi.spyOn(input, "click").mockImplementation(() => {});
    wrapper.findComponent(Identity).vm.$emit("replace");
    await flushPromises();
    expect(clicked).toHaveBeenCalledOnce();
  });
});

describe("applicant evidence-aware qualification edits", () => {
  it("prefills school evidence with managed subjects and leaves ambiguous/invalid results for correction", async () => {
    prefill = {
      personalFields: {
        examBodyCode: " zimsec ",
        schoolOrInstitution: " Example School ",
        yearWritten: "2024",
        centreNumber: " C100 ",
        candidateNumber: " A001 ",
        countryCode: "ZWE",
      },
      qualificationResults: [
        { subjectId: "maths", grade: "A", confirmationRequired: false },
        { subjectId: "maths", grade: "D", confirmationRequired: true },
        { subjectId: null, grade: "B", confirmationRequired: false },
      ],
      warnings: [],
      identityNameMismatch: null,
    };
    await render();
    await section("Qualifications");
    await uploadEvidence("Qualification evidence", "certificate");
    await extraction("Qualification evidence", "certificate");
    expect(field("Exam body").props("modelValue")).toBe("zimsec");
    expect(field("School or institution").props("modelValue")).toBe("Example School");
    expect(field("Year written").props("modelValue")).toBe(2024);
    expect(field("Country").props("modelValue")).toBe("zw");
    expect(field("Subject", 0).props("modelValue")).toBe("maths");
    expect(field("Subject", 1).props("modelValue")).toBe("");
    expect(field("Grade", 1).props("modelValue")).toBe("");
    expect(field("Subject", 2).props("modelValue")).toBe("");
    expect(
      wrapper
        .findAll("button")
        .find((button) => button.text() === "Save")!
        .attributes("disabled"),
    ).toBeDefined();
  });
  it("does not overwrite corrected evidence fields with later extraction and bounds results at twenty", async () => {
    await render();
    await section("Qualifications");
    await setField(wrapper, "Qualification type", "A_LEVEL");
    await uploadEvidence("Qualification evidence", "certificate");
    await setField(wrapper, "Exam body", "zimsec");
    await setField(wrapper, "School or institution", "My school");
    await setField(wrapper, "Centre number", "MY-CENTRE");
    await setField(wrapper, "Candidate number", "MY-CANDIDATE");
    await setField(wrapper, "Country", "zw");
    prefill.personalFields = {
      examBodyCode: "OTHER",
      schoolOrInstitution: "Wrong school",
      yearWritten: "1800",
      centreNumber: "WRONG",
      candidateNumber: "WRONG",
      countryCode: "ZWE",
    };
    prefill.qualificationResults = Array.from({ length: 24 }, () => ({
      subjectId: "physics",
      grade: "E",
      confirmationRequired: false,
    }));
    await extraction("Qualification evidence", "certificate");
    expect(field("School or institution").props("modelValue")).toBe("My school");
    expect(field("Centre number").props("modelValue")).toBe("MY-CENTRE");
    expect(field("Candidate number").props("modelValue")).toBe("MY-CANDIDATE");
    expect(field("Year written").props("modelValue")).not.toBe(1800);
    expect(
      wrapper
        .findAllComponents(FormField)
        .filter((component) => component.props("label") === "Grade"),
    ).toHaveLength(20);
    expect(field("Principal subject").props("modelValue")).toBe(true);
  });
  it("keeps unknown exam bodies and countries unselected and rejects nonintegral extracted years", async () => {
    prefill.personalFields = { examBodyCode: "UNKNOWN", countryCode: "ZZZ", yearWritten: 2024.5 };
    await render();
    await section("Qualifications");
    await uploadEvidence("Qualification evidence");
    await extraction("Qualification evidence");
    expect(field("Exam body").props("modelValue")).toBe("");
    expect(field("Country").props("modelValue")).toBe("");
    expect(field("Year written").props("modelValue")).not.toBe(2024.5);
  });
  it("edits existing aggregate results with locked qualification type and current version", async () => {
    workspace.qualifications = [qualification()];
    await render();
    await section("Qualifications");
    await clickButton(wrapper, "Edit");
    expect(field("Qualification type").props("disabled")).toBe(true);
    expect(field("Subject").props("modelValue")).toBe("physics");
    await setField(wrapper, "Grade", "B");
    await clickButton(wrapper, "Save");
    expect(writes()[0]).toEqual([
      "/api/admissions/applications/application/qualification-aggregates/sitting",
      {
        method: "PUT",
        body: expect.objectContaining({
          expectedVersion: 8,
          documentId: "certificate",
          results: [{ subjectId: "physics", grade: "B", principalSubject: true }],
        }),
      },
    ]);
  });
  it("populates historical non-school qualification fallbacks and leaves missing requirements unsavable", async () => {
    workspace.qualifications = [
      qualification({
        level: "DEGREE",
        awardTypeCode: "MASTERS",
        examBody: null,
        institutionName: null,
        yearWritten: null,
        centreNumber: null,
        candidateNumber: null,
        documentId: null,
        countryId: null,
        results: [],
      }),
    ];
    await render();
    await section("Qualifications");
    await clickButton(wrapper, "Edit");
    expect(field("Qualification type").props("modelValue")).toBe("MASTERS");
    expect(wrapper.text()).toContain("Upload evidence to continue");
    await uploadEvidence("Qualification evidence");
    expect(field("Qualification title").props("modelValue")).toBe("");
    expect(
      wrapper
        .findAll("button")
        .find((button) => button.text() === "Save")!
        .attributes("disabled"),
    ).toBeDefined();
    await clickButton(wrapper, "Cancel");
    expect(wrapper.find("#inline-record-editor").exists()).toBe(false);
    await clickButton(wrapper, "Add another qualification");
    expect(field("Qualification type").props("modelValue")).toBe("O_LEVEL");
  });
});

describe("applicant profile and programme preference contracts", () => {
  it("accepts calendar and selector component values while preserving nullable fields", async () => {
    await render();
    await emitField("Date of birth", { toString: () => "2001-02-03" });
    await emitField("Country of residence", { value: "zw" });
    await emitField("Nationality", { value: "zw" });
    await emitField("Title", { value: "Ms" });
    await emitField("Postal address", { value: "" });
    await emitField("Marital status", 123);
    await setField(wrapper, "Disability status", "DECLARED");
    await setField(wrapper, "Support requirements", "Accessible accommodation");
    await clickButton(wrapper, "Save");
    expect(writes()[0]).toEqual([
      "/api/admissions/applications/application/profile",
      {
        method: "PUT",
        body: expect.objectContaining({
          dateOfBirth: "2001-02-03",
          countryId: "zw",
          nationalityCountryId: "zw",
          titleCode: "Ms",
          postalAddress: null,
          maritalStatusCode: null,
          disabilityStatusCode: "DECLARED",
          specialNeeds: "Accessible accommodation",
          expectedVersion: 4,
        }),
      },
    ]);
    expect(writes()[0]![1].body).not.toHaveProperty("firstName");
    expect(context.notify).toHaveBeenCalledWith(expect.objectContaining({ title: "Saved" }));
  });
  it("blocks malformed calendar values and uses passports for international applicants", async () => {
    workspace.profile.applicantCategoryCode = "INTERNATIONAL";
    workspace.profile.passportNumber = "P123";
    await render();
    expect(wrapper.find('[data-label="National ID number"]').exists()).toBe(false);
    expect(field("Passport number").props("modelValue")).toBe("P123");
    await emitField("Date of birth", { toString: () => "invalid-calendar" });
    await clickButton(wrapper, "Save");
    expect(writes()).toHaveLength(0);
    await emitField("Date of birth", { value: "2001-02-03" });
    await clickButton(wrapper, "Save");
    expect(writes()[0]![1].body.dateOfBirth).toBe("2001-02-03");
  });
  it("debounces autosaves and announces current state without redundant saved-profile writes", async () => {
    await render();
    await clickButton(wrapper, "Save");
    expect(writes()).toHaveLength(0);
    await setField(wrapper, "Middle names", "First correction");
    await vi.advanceTimersByTimeAsync(500);
    await setField(wrapper, "Middle names", "Final correction");
    await vi.advanceTimersByTimeAsync(899);
    expect(writes()).toHaveLength(0);
    await vi.advanceTimersByTimeAsync(1);
    await flushPromises();
    expect(writes()).toHaveLength(1);
    expect(writes()[0]![1].body.middleNames).toBe("Final correction");
    expect(wrapper.text()).toContain("Draft: saved");
    await section("Next of kin");
    await clickButton(wrapper, "Save");
    expect(context.notify).toHaveBeenCalledWith(expect.objectContaining({ title: "Saved" }));
    await clickButton(wrapper, "Back");
    expect(wrapper.get("h1").text()).toBe("Applicant details");
    await clickButton(wrapper, "Return to applications");
    expect(push).toHaveBeenCalledWith("/");
  });
  it("stores ordered programme preferences with and without entry specializations", async () => {
    await render();
    await section("Programme choices");
    expect(
      wrapper
        .findAll("button")
        .find((button) => button.text() === "Save")!
        .attributes("disabled"),
    ).toBeDefined();
    await emitField("Programme choices", ["science", "arts"]);
    expect(wrapper.text()).toContain("Select between 1 and 2 option(s)");
    await emitField("Specialization or entry preferences", ["physics-option", "maths-option"]);
    await clickButton(wrapper, "Save");
    expect(writes()[0]).toEqual([
      "/api/admissions/applications/application/programme-choices",
      {
        method: "PUT",
        body: {
          choices: [
            { programmeId: "science", entryOptionIds: ["physics-option", "maths-option"] },
            { programmeId: "arts", entryOptionIds: [] },
          ],
        },
      },
    ]);
    expect(context.notify).toHaveBeenCalledWith(expect.objectContaining({ title: "Saved" }));
  });
  it("restores ranked choices and entry preferences and preserves them after save errors", async () => {
    workspace.application.programmeChoices = [
      { id: "arts-choice", programmeId: "arts", choiceRank: 2 },
      { id: "science-choice", programmeId: "science", choiceRank: 1 },
    ];
    workspace.programmeEntryPreferences = [
      { programmeChoiceId: "science-choice", preferenceRank: 2, entryOptionId: "maths-option" },
      { programmeChoiceId: "science-choice", preferenceRank: 1, entryOptionId: "physics-option" },
    ];
    await render();
    await section("Programme choices");
    expect(field("Programme choices").props("modelValue")).toEqual(["science", "arts"]);
    expect(field("Specialization or entry preferences").props("modelValue")).toEqual([
      "physics-option",
      "maths-option",
    ]);
    context.request.mockRejectedValueOnce(new Error("Intake route closed"));
    await clickButton(wrapper, "Save");
    expect(context.showError).toHaveBeenCalledWith(
      "Programme choices could not be saved",
      "Intake route closed",
    );
    expect(field("Programme choices").props("modelValue")).toEqual(["science", "arts"]);
  });
  it("hides nonexistent route options without creating synthetic choices", async () => {
    startOptions.routes = [];
    await render();
    await section("Programme choices");
    expect(field("Programme choices").props("items")).toEqual([]);
    expect(
      wrapper
        .findAll("button")
        .find((button) => button.text() === "Save")!
        .attributes("disabled"),
    ).toBeDefined();
  });
  it.each([false, true])(
    "uses configured fallback section progress for completed=%s",
    async (complete) => {
      workspace.sections = [];
      workspace.application.paymentRequired = true;
      workspace.application.canEnterReview = complete;
      workspace.documents.requirements = [
        documentRequirement({
          requirementCode: "SUPPORT",
          captureSectionCode: "SUPPORTING_DOCUMENTS",
        }),
      ];
      workspace.documents.requiredDocumentsUploaded = complete;
      workspace.profile.missingRequiredFields = complete ? [] : ["nationalIdNumber"];
      workspace.nextOfKin = complete
        ? [{ id: "kin", fullName: "Kin", relationshipCode: "PARENT" }]
        : [];
      workspace.qualifications = complete ? [qualification()] : [];
      workspace.application.programmeChoices = complete
        ? [{ id: "choice", programmeId: "science", choiceRank: 1 }]
        : [];
      workspace.priorUzDeclaration = complete ? { previouslyStudiedAtUz: false } : null;
      workspace.professionalAchievementsDeclaredNone = complete;
      workspace.declarationAcceptedAt = complete ? "2026-08-20" : null;
      startOptions.applicationTypes = [
        {
          id: "type",
          sections: sectionDefinitions.map(([code, sectionName], index) => ({
            code,
            name: sectionName,
            required: true,
            repeatable: true,
            minimumRecords: 1,
            sortOrder: index,
          })),
        },
      ];
      await render();
      const steps = wrapper.findComponent(Stepper).props("steps") as any[];
      expect(
        steps
          .filter((step) => step.id !== "APPLICATION_ROUTE")
          .every(
            (step) =>
              step.status ===
              (complete ? "complete" : step.id === "PERSONAL_DETAILS" ? "current" : "pending"),
          ),
      ).toBe(true);
      expect(steps.every((step) => step.description === undefined)).toBe(true);
    },
  );
  it("derives baseline sections when configuration is absent and rejects invalid step events", async () => {
    workspace.sections = null;
    await render();
    expect(
      wrapper
        .findComponent(Stepper)
        .props("steps")
        .map((step: any) => step.id),
    ).toEqual([
      "APPLICATION_ROUTE",
      "PERSONAL_DETAILS",
      "NEXT_OF_KIN",
      "QUALIFICATIONS",
      "PROGRAMME_CHOICES",
      "REVIEW_DECLARATION",
    ]);
    const heading = wrapper.get("h1").text();
    wrapper.findComponent(Stepper).vm.$emit("update:current-step", "MISSING_EDITOR");
    await flushPromises();
    expect(wrapper.get("h1").text()).toBe(heading);
  });
  it("shows unsupported configured sections and distinct attention/current states", async () => {
    workspace.sections = [
      {
        id: "custom",
        code: "CUSTOM",
        name: "Extra declaration",
        status: "REJECTED",
        sortOrder: 0,
        required: false,
      },
      {
        id: "review",
        code: "REVIEW_DECLARATION",
        name: "Review and declaration",
        status: "NOT_STARTED",
        sortOrder: 1,
        required: true,
      },
    ];
    await render();
    expect(wrapper.text()).toContain("Section unavailable");
    expect(wrapper.findComponent(Stepper).props("steps")[1].status).toBe("attention");
    expect(wrapper.find('[data-tone="error"]').text()).toBe("Rejected");
    await section("Review and declaration");
    expect(wrapper.find('[data-tone="neutral"]').text()).toBe("Not Started");
  });
});

describe("applicant final review evidence visibility", () => {
  it("renders populated submitted evidence and never exposes edit or submission actions", async () => {
    workspace.application.status = "SUBMITTED";
    workspace.application.paymentRequired = true;
    workspace.application.paymentClearanceStatus = "PAID";
    workspace.application.paymentWaiverReason = "Scholarship fee approval";
    workspace.application.payment = {
      financePaymentReferenceId: "payment",
      reference: "PAY-001",
      currencyCode: "USD",
      amountDue: 25,
      baseAmountDue: 25,
      ratingStatus: "RATED",
      status: "PAID",
      paidAt: "2026-08-20T09:30:00Z",
    };
    workspace.readyForSubmission = true;
    workspace.missingRequirements = [];
    workspace.declarationAcceptedAt = "2026-08-20";
    workspace.profile = {
      ...workspace.profile,
      titleCode: "Ms",
      middleNames: "Jane",
      maritalStatusCode: "SINGLE",
      sponsorTypeCode: "SELF",
      disabilityStatusCode: "DECLARED",
      specialNeeds: "Accessible room",
      nationalityCountryId: "unknown-country",
    };
    workspace.nextOfKin = [
      {
        id: "kin",
        fullName: "Kin Example",
        relationshipCode: "PARENT",
        primary: true,
        phoneNumber: "0700",
        email: "kin@example.test",
        address: "Harare",
      },
    ];
    workspace.employmentHistory = [
      {
        id: "current",
        employerName: "Current laboratory",
        positionTitle: "Assistant",
        current: true,
        startedOn: "2025-01-01",
        responsibilities: "Research assistance",
      },
      {
        id: "past",
        employerName: "Past laboratory",
        positionTitle: "Intern",
        current: false,
        startedOn: "2024-01-01",
        endedOn: "2024-12-31",
      },
    ];
    workspace.priorUzDeclaration = {
      previouslyStudiedAtUz: true,
      registrationNumber: "R240001",
      enrolmentStartedOn: "2024-01-01",
      enrolmentEndedOn: "2025-12-31",
      previouslyAcceptedOffer: true,
      previouslyTookUpPlace: false,
    };
    workspace.professionalAchievements = [
      {
        id: "award",
        type: "AWARD",
        title: "Science award",
        organisation: "Science Society",
        achievedOn: "2025-01-01",
        description: "Research distinction",
      },
    ];
    workspace.referees = [
      {
        id: "referee",
        title: "Dr",
        fullName: "Referee Example",
        organisation: "School",
        positionTitle: "Principal",
        verificationStatus: "VERIFIED",
        email: "referee@example.test",
        phoneNumber: "0701",
      },
    ];
    workspace.qualifications = [
      qualification(),
      qualification({
        id: "rejected",
        level: "O_LEVEL",
        examBody: null,
        institutionName: null,
        yearWritten: null,
        candidateNumber: null,
        countryId: null,
        verificationStatus: "REJECTED",
        results: [],
      }),
    ];
    workspace.application.programmeChoices = [
      {
        id: "choice",
        programmeId: "science",
        choiceRank: 1,
        programmeCode: "BSC",
        programmeName: "Science",
        awardName: "Bachelor",
        owningAcademicUnitName: "Science faculty",
        programmeVersionCode: "2026",
      },
    ];
    workspace.documents.requirements = [
      documentRequirement({
        documentId: "identity",
        state: "VERIFIED",
        fileName: "identity.pdf",
        mimeType: "application/pdf",
      }),
      documentRequirement({
        requirementCode: "REF",
        requirementName: "Reference",
        documentId: null,
        state: "REJECTED",
        required: false,
        rejectionReason: "Unreadable reference",
      }),
    ];
    paymentProofs = ["VERIFIED", "REJECTED", "PENDING"].map((status) => ({
      id: status,
      documentTypeCode: "PROOF_OF_PAYMENT",
      originalFileName: `${status}.pdf`,
      uploadedAt: "2026-08-20",
      verificationStatus: status,
    }));
    await render();
    await section("Review and declaration");
    for (const text of [
      "Ready for submission",
      "Ms",
      "Ada",
      "Accessible room",
      "unknown-country",
      "Kin Example",
      "Current employment",
      "Past laboratory",
      "R240001",
      "Science award",
      "Dr Referee Example",
      "Qualification sitting",
      "Year not supplied",
      "No subject results supplied.",
      "Principal",
      "Unreadable reference",
      "USD 25",
      "Scholarship fee approval",
      "VERIFIED.pdf",
      "Declaration accepted",
    ])
      expect(wrapper.text()).toContain(text);
    expect(wrapper.text()).not.toContain("Submit application");
    expect(wrapper.text()).not.toContain("Save");
    expect(wrapper.text()).not.toContain("Accept declaration");
  });
  it.each([null, { previouslyStudiedAtUz: false }])(
    "distinguishes absent prior-study evidence from explicit none %j",
    async (declaration) => {
      workspace.priorUzDeclaration = declaration;
      workspace.professionalAchievementsDeclaredNone = Boolean(declaration);
      workspace.profile.dateOfBirth = "invalid-date";
      await render();
      await section("Review and declaration");
      expect(wrapper.text()).toContain(
        declaration
          ? "No previous UZ study declared."
          : "Prior UZ study declaration is incomplete.",
      );
      expect(wrapper.text()).toContain(
        declaration
          ? "No professional achievements declared."
          : "Professional achievements declaration is incomplete.",
      );
      expect(wrapper.text()).toContain("invalid-date");
      expect(wrapper.text()).toContain("No qualification sittings supplied.");
      expect(wrapper.text()).toContain("No documents listed.");
    },
  );
  it.each(["image/png", "application/pdf", "application/octet-stream"])(
    "previews and downloads uploaded %s evidence before declaration",
    async (mimeType) => {
      workspace.documents.requirements = [
        documentRequirement({
          documentId: "evidence",
          state: "PENDING",
          fileName: "evidence.file",
        }),
      ];
      const original = context.request.getMockImplementation()!;
      context.request.mockImplementation(async (path: string, options?: any) =>
        path.endsWith("/download")
          ? {
              downloadUrl: "https://documents.example.test/evidence",
              originalFileName: "evidence.file",
              mimeType,
            }
          : original(path, options),
      );
      let clicked: HTMLAnchorElement | undefined;
      vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (
        this: HTMLAnchorElement,
      ) {
        clicked = this;
      });
      await render();
      await section("Review and declaration");
      await clickButton(wrapper, "Preview");
      const panel = wrapper.get("#application-document-preview");
      if (mimeType.startsWith("image/"))
        expect(panel.get("img").attributes("alt")).toBe("National ID");
      else if (mimeType === "application/pdf")
        expect(panel.get("iframe").attributes("title")).toBe("National ID preview");
      else expect(panel.text()).toContain("Inline preview is unavailable for this file type");
      await clickButton(
        wrapper,
        mimeType === "application/octet-stream" ? "Download document" : "Download",
      );
      expect(clicked?.download).toBe("evidence.file");
      expect(clicked?.rel).toBe("noopener noreferrer");
      await wrapper.get('[aria-label="Close document preview"]').trigger("click");
      expect(wrapper.find("#application-document-preview").exists()).toBe(false);
    },
  );
  it("reports preview access failure and recovers by selecting the evidence again", async () => {
    workspace.documents.requirements = [
      documentRequirement({ documentId: "evidence", state: "PENDING" }),
    ];
    await render();
    await section("Review and declaration");
    context.request.mockRejectedValueOnce(new Error("Secure URL expired"));
    await clickButton(wrapper, "Preview");
    expect(wrapper.text()).toContain("Preview unavailable");
    expect(wrapper.text()).toContain("Secure URL expired");
    await clickButton(wrapper, "Preview");
    expect(wrapper.text()).not.toContain("Secure URL expired");
    expect(wrapper.find("#application-document-preview iframe").exists()).toBe(true);
  });
  it("projects supporting uploader changes without mixing them into identity capture", async () => {
    workspace.documents.requirements = [
      documentRequirement({
        requirementCode: "TRANSCRIPT",
        requirementName: "Transcript",
        captureSectionCode: "SUPPORTING_DOCUMENTS",
        required: false,
        state: "REJECTED",
        rejectionReason: "Replace unreadable file",
      }),
    ];
    await render();
    await section("Supporting documents");
    expect(wrapper.get("h1").text()).toBe("Supporting documents");
    await uploadEvidence("Transcript", "new-transcript");
    expect(evidence("Transcript").props("existingDocumentId")).toBe("new-transcript");
    expect(evidence("Transcript").props("existingState")).toBe("PENDING");
  });
});
