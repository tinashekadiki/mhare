// Author: Tinashe K
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { defineComponent, ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import InlineFormComponent from "@emhare/portal-shell/components/forms/EmhareInlineRecordForm.vue";
import ApplicationWorkspace from "../../pages/applications/[applicationId].vue";
import {
  clickButton,
  operationalContext,
  operationalStubs,
  setField,
} from "../../../../tests/unit/support/operational-page";

const FormField = defineComponent({
  props: ["label", "type", "modelValue", "items", "disabled", "readonly", "multiple"],
  emits: ["update:modelValue"],
  components: {
    SelectControl: operationalStubs.USelect,
    InputControl: operationalStubs.UInput,
    ToggleControl: operationalStubs.UCheckbox,
  },
  template:
    '<label class="field" :data-label="label">{{ label }}<SelectControl v-if="type===\'select\'||type===\'searchable-select\'" :model-value="multiple?modelValue[0]:modelValue" :items="items" :disabled="disabled" @update:model-value="$emit(\'update:modelValue\',multiple?[$event]:$event)"/><ToggleControl v-else-if="type===\'toggle\'" :model-value="modelValue" :disabled="disabled" @update:model-value="$emit(\'update:modelValue\',$event)"/><InputControl v-else :model-value="modelValue" :disabled="disabled" :readonly="readonly" :type="type===\'number\'?\'number\':\'text\'" @update:model-value="$emit(\'update:modelValue\',type===\'number\'?Number($event):$event)"/></label>',
});
const InlineForm = InlineFormComponent;
const EvidenceUploader = defineComponent({
  props: ["label", "documentTypeCode", "existingDocumentId", "disabled"],
  emits: ["uploaded", "extraction-ready"],
  template:
    "<section class=\"evidence\"><span>{{ label }}</span><button :disabled=\"disabled\" @click=\"$emit('uploaded',{id:'document',verificationStatus:'PENDING'})\">Upload {{label}}</button></section>",
});
const Stepper = defineComponent({
  props: ["steps", "currentStep"],
  emits: ["update:current-step"],
  template:
    '<nav class="steps"><button v-for="step in steps" :key="step.id" :data-state="step.status" @click="$emit(\'update:current-step\',step.id)">{{step.title}}</button></nav>',
});
const Feedback = defineComponent({
  props: ["title", "description", "actionLabel"],
  emits: ["action"],
  template:
    '<aside>{{title}} {{description}}<button v-if="actionLabel" @click="$emit(\'action\')">{{actionLabel}}</button></aside>',
});
let context: ReturnType<typeof operationalContext>;
let wrapper: VueWrapper;
const confirmAction = vi.fn(),
  showSuccess = vi.fn(),
  push = vi.fn();
let workspace: any;
async function nextSection() {
  await wrapper.findAll("footer button").at(-1)!.trigger("click");
  await flushPromises();
}

const sections = [
  ["PERSONAL_DETAILS", "Applicant details"],
  ["NEXT_OF_KIN", "Next of kin"],
  ["EMPLOYMENT_HISTORY", "Employment history"],
  ["REFEREES", "Referees"],
  ["PRIOR_UZ_STUDY", "Prior UZ study"],
  ["PROFESSIONAL_ACHIEVEMENTS", "Professional achievements"],
  ["QUALIFICATIONS", "Qualifications"],
  ["PROGRAMME_CHOICES", "Programme choices"],
  ["DOCUMENTS", "Supporting documents"],
  ["PAYMENT", "Application fee"],
  ["REVIEW_DECLARATION", "Review and declaration"],
];
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
      calculatedTotalPoints: 10,
    },
    profile: {
      applicantCategoryCode: "LOCAL",
      firstName: "Test",
      lastName: "Applicant",
      primaryEmail: "applicant@example.test",
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
    sections: sections.map(([code, name], index) => ({
      id: code,
      code,
      name,
      required: true,
      repeatable: [
        "NEXT_OF_KIN",
        "EMPLOYMENT_HISTORY",
        "REFEREES",
        "QUALIFICATIONS",
        "PROGRAMME_CHOICES",
      ].includes(code!),
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
beforeEach(() => {
  vi.resetAllMocks();
  context = operationalContext();
  workspace = fixture();
  confirmAction.mockResolvedValue(true);
  vi.stubGlobal("useRoute", () => ({ params: { applicationId: "application" } }));
  vi.stubGlobal("useRouter", () => ({ push }));
  vi.stubGlobal("useEmhareAuth", () => ({
    authenticated: ref(true),
    loadUser: vi.fn(),
    syncCoreUser: vi.fn(),
  }));
  vi.stubGlobal("useApplicantOfferLetter", () => ({
    openingOfferId: ref(null),
    openOfferLetter: vi.fn(),
  }));
  vi.stubGlobal("useEmhareConfirm", () => ({
    confirmAction,
    showSuccess,
    showError: context.showError,
  }));
  context.request.mockImplementation(async (path: string, options?: { method?: string }) => {
    if (options?.method || path.endsWith("/workspace")) return structuredClone(workspace);
    if (path.includes("/start-options"))
      return {
        applicationTypes: [],
        routes: [
          {
            applicationTypeId: "type",
            intakeId: "intake",
            programmes: [
              {
                id: "programme",
                code: "HCS",
                name: "Computer Science",
                minimumEntryOptionCount: 0,
                maximumEntryOptionCount: 0,
                entryOptions: [],
              },
            ],
          },
        ],
      };
    if (path === "/api/admissions/qualification-reference-data")
      return {
        examBodies: [{ id: "zimsec", code: "ZIMSEC", name: "ZIMSEC" }],
        oLevelSubjects: [{ id: "maths", code: "MAT", name: "Mathematics" }],
        aLevelSubjects: [{ id: "physics", code: "PHY", name: "Physics" }],
        otherSubjects: [],
      };
    if (path === "/api/core/reference/countries")
      return [{ id: "zw", iso2Code: "ZW", name: "Zimbabwe", nationalityName: "Zimbabwean" }];
    return [];
  });
});
afterEach(() => {
  wrapper?.unmount();
  vi.clearAllTimers();
  vi.useRealTimers();
  vi.unstubAllGlobals();
});
async function render() {
  wrapper = mount(ApplicationWorkspace, {
    global: {
      stubs: {
        ...operationalStubs,
        EmhareTopNav: defineComponent({
          template: '<nav><slot name="meta"/><slot name="actions"/></nav>',
        }),
        EmhareVerticalStepper: Stepper,
        EmhareFeedbackState: Feedback,
        EmhareInlineRecordForm: InlineForm,
        EmhareFormField: FormField,
        EmhareFormSection: operationalStubs.UCard,
        EmhareEvidenceUploader: EvidenceUploader,
        EmhareDraftSaveIndicator: true,
        EmhareIdentityNameMismatchPanel: true,
        EmhareReviewField: defineComponent({
          props: ["label", "value"],
          template: "<p>{{label}}: {{value}}</p>",
        }),
        UProgress: true,
        USeparator: true,
      },
    },
  });
  await flushPromises();
}
async function section(name: string) {
  await clickButton(wrapper, name);
}
const recordCases = [
  {
    section: "Next of kin",
    resource: "next-of-kin",
    collection: "nextOfKin",
    record: {
      id: "record",
      fullName: "Parent",
      relationshipCode: "PARENT",
      phoneNumber: "+263771111111",
      email: null,
      address: null,
      primary: true,
      version: 5,
    },
    fields: { "Full name": "Guardian", Relationship: "GUARDIAN", "Phone number": "+263772222222" },
  },
  {
    section: "Employment history",
    resource: "employment-history",
    collection: "employmentHistory",
    record: {
      id: "record",
      employerName: "Employer",
      positionTitle: "Assistant",
      startedOn: "2020-01-01",
      endedOn: null,
      current: true,
      responsibilities: null,
      version: 5,
    },
    fields: { Employer: "University", Position: "Researcher", "Started on": "2026-01-01" },
  },
  {
    section: "Referees",
    resource: "referees",
    collection: "referees",
    record: {
      id: "record",
      fullName: "Dr Referee",
      organisation: "University",
      positionTitle: "Lecturer",
      email: "referee@example.test",
      title: null,
      phoneNumber: null,
      expertise: "Research",
      relationshipToApplicant: "Supervisor",
      invitationStatus: "NOT_SENT",
      version: 3,
    },
    fields: {
      "Full name": "Professor Referee",
      Organisation: "University",
      Position: "Professor",
      Email: "professor@example.test",
      "Relationship to applicant": "Supervisor",
      "Area of expertise": "Research",
    },
  },
];

describe("Applicant workspace public actions", () => {
  it.each([
    ["Qualifications", "Upload the qualification document"],
    ["Programme choices", "Choose at least one programme"],
    ["Prior UZ study", "Complete the highlighted fields"],
    ["Professional achievements", "Enter an achievement title"],
  ])("keeps incomplete %s on screen when Next is selected", async (sectionName, message) => {
    await render();
    await section(sectionName);
    if (sectionName === "Prior UZ study")
      await setField(wrapper, "I previously studied at UZ", true);
    await nextSection();
    expect(wrapper.get("h1").text()).toBe(sectionName);
    expect(wrapper.text()).toContain(message);
    expect(context.request.mock.calls.some(([, options]) => options?.method)).toBe(false);
  });
  it("asks for required identity documents before saving", async () => {
    workspace.documents.requirements = [
      {
        requirementCode: "NATIONAL_ID",
        requirementName: "National ID",
        captureSectionCode: "PERSONAL_DETAILS",
        state: "MISSING",
        required: true,
        documentId: null,
      },
    ];
    await render();
    await nextSection();
    expect(wrapper.text()).toContain("Upload the required identity documents first.");
    expect(wrapper.get("h1").text()).toBe("Applicant details");
  });
  it.each(recordCases)(
    "offers additional $section entries only when repeatable",
    async (scenario) => {
      workspace[scenario.collection] = [scenario.record];
      await render();
      await section(scenario.section);
      expect(wrapper.find("#inline-record-editor").exists()).toBe(false);
      await wrapper
        .findAll("button")
        .find((button) => button.text().startsWith("Add another"))!
        .trigger("click");
      await flushPromises();
      for (const [label, value] of Object.entries(scenario.fields))
        await setField(wrapper, label, value);
      if (scenario.section === "Next of kin") {
        await setField(wrapper, "Email", "contact@example.test");
        await setField(wrapper, "Address", "Harare");
      }
      if (scenario.section === "Referees") await setField(wrapper, "Title", "Dr");
      await clickButton(wrapper, "Save");
      expect(context.request).toHaveBeenCalledWith(
        `/api/admissions/applications/application/${scenario.resource}`,
        expect.objectContaining({ method: "POST" }),
      );
    },
  );
  it("cancels the pending autosave when profile changes are discarded", async () => {
    await render();
    await setField(wrapper, "Phone number", "+263771111111");
    await clickButton(wrapper, "Cancel");
    expect(
      (wrapper.get('[data-label="Phone number"] input').element as HTMLInputElement).value,
    ).toBe(workspace.profile.primaryPhone);
    expect(context.request.mock.calls.some(([, options]) => options?.method)).toBe(false);
    expect(wrapper.text()).not.toContain("Unsaved changes");
  });

  it("does not overwrite edits made while a profile save is pending", async () => {
    await render();
    let finishSave!: (value: any) => void;
    context.request.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finishSave = resolve;
        }),
    );
    await setField(wrapper, "Phone number", "+263771111111");
    await wrapper
      .findAll("button")
      .find((button) => button.text() === "Save")!
      .trigger("click");
    await flushPromises();
    await setField(wrapper, "Phone number", "+263772222222");
    finishSave({
      ...workspace,
      profile: { ...workspace.profile, primaryPhone: "+263771111111", version: 5 },
    });
    await flushPromises();
    expect(
      (wrapper.get('[data-label="Phone number"] input').element as HTMLInputElement).value,
    ).toBe("+263772222222");
    await clickButton(wrapper, "Save");
    expect(context.request).toHaveBeenLastCalledWith(
      "/api/admissions/applications/application/profile",
      expect.objectContaining({
        body: expect.objectContaining({ primaryPhone: "+263772222222", expectedVersion: 5 }),
      }),
    );
  });
  it("saves a no-previous-study declaration on Next without an extra save click", async () => {
    await render();
    await section("Prior UZ study");
    await nextSection();
    expect(context.request).toHaveBeenCalledWith(
      "/api/admissions/applications/application/prior-uz-declaration",
      { method: "PUT", body: { previouslyStudiedAtUz: false } },
    );
    expect(wrapper.get("h1").text()).toBe("Professional achievements");
  });
  it("shows the first achievement form immediately and saves it on Next", async () => {
    await render();
    await section("Professional achievements");
    await setField(wrapper, "Title", "Research award");
    await nextSection();
    expect(context.request).toHaveBeenCalledWith(
      "/api/admissions/applications/application/professional-achievements",
      expect.objectContaining({
        body: expect.objectContaining({
          achievements: [expect.objectContaining({ title: "Research award" })],
        }),
      }),
    );
    expect(wrapper.get("h1").text()).toBe("Qualifications");
  });
  it("keeps invalid contact fields visible and supports cancelling before leaving", async () => {
    await render();
    await section("Next of kin");
    await setField(wrapper, "Full name", "Unsaved person");
    await section("Qualifications");
    expect(wrapper.get("h1").text()).toBe("Next of kin");
    expect(wrapper.text()).toContain("Complete the highlighted fields.");
    await clickButton(wrapper, "Cancel");
    await section("Qualifications");
    expect(wrapper.get("h1").text()).toBe("Qualifications");
  });
  it("saves contact edits before returning to the application list", async () => {
    await render();
    await section("Next of kin");
    for (const [label, value] of Object.entries(recordCases[0]!.fields))
      await setField(wrapper, label, value);
    await clickButton(wrapper, "Return to applications");
    expect(context.request).toHaveBeenCalledWith(
      "/api/admissions/applications/application/next-of-kin",
      expect.objectContaining({ method: "POST" }),
    );
    expect(push).toHaveBeenCalledWith("/");
  });

  it.each(recordCases)("saves $section before moving to the next step", async (scenario) => {
    await render();
    await section(scenario.section);
    for (const [label, value] of Object.entries(scenario.fields))
      await setField(wrapper, label, value);
    const heading = wrapper.get("h1").text();
    await wrapper.findAll("footer button").at(-1)!.trigger("click");
    await flushPromises();
    expect(context.request).toHaveBeenCalledWith(
      `/api/admissions/applications/application/${scenario.resource}`,
      expect.objectContaining({ method: "POST" }),
    );
    expect(wrapper.get("h1").text()).not.toBe(heading);
  });
  it("keeps unsaved contact details when Next fails", async () => {
    await render();
    await section("Next of kin");
    await setField(wrapper, "Full name", "My parent");
    await setField(wrapper, "Relationship", "PARENT");
    await setField(wrapper, "Phone number", "+263771234567");
    context.request.mockRejectedValueOnce(new Error("Connection lost"));
    await wrapper.findAll("footer button").at(-1)!.trigger("click");
    await flushPromises();
    expect(wrapper.get("h1").text()).toBe("Next of kin");
    expect((wrapper.get('[data-label="Full name"] input').element as HTMLInputElement).value).toBe(
      "My parent",
    );
    expect(context.notify).not.toHaveBeenCalled();
  });
  it("opens an existing single-entry contact for editing without an Add action", async () => {
    workspace.nextOfKin = [recordCases[0]!.record];
    workspace.sections.find((item: any) => item.code === "NEXT_OF_KIN").repeatable = false;
    await render();
    await section("Next of kin");
    expect((wrapper.get('[data-label="Full name"] input').element as HTMLInputElement).value).toBe(
      "Parent",
    );
    expect(wrapper.findAll("button").some((button) => /Add another/.test(button.text()))).toBe(
      false,
    );
  });
  it("saves unsaved programme choices when continuing", async () => {
    await render();
    await section("Programme choices");
    wrapper
      .findAllComponents(FormField)
      .find((field) => field.props("label") === "Programme choices")!
      .vm.$emit("update:modelValue", ["programme"]);
    await flushPromises();
    await wrapper.findAll("footer button").at(-1)!.trigger("click");
    await flushPromises();
    expect(context.request).toHaveBeenCalledWith(
      "/api/admissions/applications/application/programme-choices",
      expect.objectContaining({
        method: "PUT",
        body: { choices: [{ programmeId: "programme", entryOptionIds: [] }] },
      }),
    );
  });

  it.each(recordCases)(
    "creates $section through the inline form and resets after save",
    async (scenario) => {
      await render();
      await section(scenario.section);
      for (const [label, value] of Object.entries(scenario.fields))
        await setField(wrapper, label, value);
      await clickButton(wrapper, "Save");
      expect(context.request).toHaveBeenCalledWith(
        `/api/admissions/applications/application/${scenario.resource}`,
        { method: "POST", body: expect.objectContaining({ expectedVersion: 0 }) },
      );
      expect(context.notify).toHaveBeenCalledWith(expect.objectContaining({ title: "Saved" }));
      expect(
        (
          wrapper.get(
            `#inline-record-editor [data-label="${Object.keys(scenario.fields)[0]}"] input`,
          ).element as HTMLInputElement
        ).value,
      ).toBe("");
    },
  );
  it.each(recordCases)("edits $section and cancels back to a new-record form", async (scenario) => {
    workspace[scenario.collection] = [scenario.record];
    await render();
    await section(scenario.section);
    await clickButton(wrapper, "Edit");
    expect(wrapper.find("#inline-record-editor").exists()).toBe(true);
    await clickButton(wrapper, "Save");
    expect(context.request).toHaveBeenCalledWith(
      `/api/admissions/applications/application/${scenario.resource}/record`,
      expect.objectContaining({
        method: "PUT",
        body: expect.objectContaining({ expectedVersion: scenario.record.version }),
      }),
    );
    await clickButton(wrapper, "Edit");
    await clickButton(wrapper, "Cancel");
    expect(wrapper.find("#inline-record-editor").exists()).toBe(false);
  });
  it.each(recordCases)(
    "keeps failed $section edits visible and reports the backend error",
    async (scenario) => {
      await render();
      await section(scenario.section);
      for (const [label, value] of Object.entries(scenario.fields))
        await setField(wrapper, label, value);
      context.request.mockRejectedValueOnce(new Error("Version conflict"));
      await clickButton(wrapper, "Save");
      expect(context.showError).toHaveBeenCalledWith(
        "Record could not be saved",
        "Version conflict",
      );
      expect(context.notify).not.toHaveBeenCalled();
    },
  );
  it("confirms removal with the version and handles cancellation and failures", async () => {
    workspace.nextOfKin = [recordCases[0]!.record];
    await render();
    await section("Next of kin");
    confirmAction.mockResolvedValueOnce(false);
    await clickButton(wrapper, "Remove");
    expect(context.request.mock.calls.some(([, options]) => options?.method === "DELETE")).toBe(
      false,
    );
    context.request.mockRejectedValueOnce(new Error("Conflict"));
    await clickButton(wrapper, "Remove");
    expect(context.showError).toHaveBeenCalledWith("Record could not be removed", "Conflict");
    await clickButton(wrapper, "Remove");
    expect(context.request).toHaveBeenCalledWith(
      "/api/admissions/applications/application/next-of-kin/record?expectedVersion=5",
      { method: "DELETE" },
    );
  });
  it.each(["NOT_SENT", "SENT", "EXPIRED", "REVOKED", "SUBMITTED"])(
    "shows the correct referee invitation action for %s",
    async (status) => {
      workspace.referees = [
        {
          ...recordCases[2]!.record,
          invitationStatus: status,
          invitedAt: status === "NOT_SENT" ? null : "2026-08-01",
          referenceSubmittedAt: status === "SUBMITTED" ? "2026-08-02" : null,
        },
      ];
      await render();
      await section("Referees");
      if (status === "SUBMITTED") {
        expect(wrapper.text()).toContain("Reference received");
        expect(
          wrapper
            .findAll("button")
            .some((button) => ["Resend", "Send invitation"].includes(button.text())),
        ).toBe(false);
      } else {
        await clickButton(wrapper, status === "NOT_SENT" ? "Send invitation" : "Resend");
        expect(context.request).toHaveBeenCalledWith(
          "/api/admissions/applications/application/referees/record/invitation?expectedVersion=3",
          { method: "POST" },
        );
      }
    },
  );
  it("reports referee delivery errors without removing the nomination", async () => {
    workspace.referees = [recordCases[2]!.record];
    await render();
    await section("Referees");
    context.request.mockRejectedValueOnce(new Error("Delivery unavailable"));
    await clickButton(wrapper, "Send invitation");
    expect(context.showError).toHaveBeenCalledWith(
      "Reference invitation could not be sent",
      "Delivery unavailable",
    );
    expect(wrapper.text()).toContain("Dr Referee");
  });
  it.each([false, true])(
    "saves the prior-UZ declaration with previously-studied=%s",
    async (previous) => {
      await render();
      await section("Prior UZ study");
      if (previous) {
        await setField(wrapper, "I previously studied at UZ", true);
        await setField(wrapper, "Previous registration number", "R123");
        await setField(wrapper, "Enrolment started", "2020-01-01");
        await setField(wrapper, "I accepted the previous offer", true);
      }
      await clickButton(wrapper, "Save");
      expect(context.request).toHaveBeenCalledWith(
        "/api/admissions/applications/application/prior-uz-declaration",
        {
          method: "PUT",
          body: previous
            ? expect.objectContaining({
                previouslyStudiedAtUz: true,
                registrationNumber: "R123",
                enrolmentEndedOn: null,
                previouslyAcceptedOffer: true,
              })
            : { previouslyStudiedAtUz: false },
        },
      );
    },
  );
  it.each([false, true])("saves explicit achievements or a none declaration: %s", async (none) => {
    await render();
    await section("Professional achievements");
    if (none) await setField(wrapper, "I have no professional achievements to declare", true);
    else {
      await setField(wrapper, "Title", "Research award");
      await clickButton(wrapper, "Add achievement");
      await clickButton(wrapper, "Remove", 1);
    }
    await clickButton(wrapper, "Save");
    expect(context.request).toHaveBeenCalledWith(
      "/api/admissions/applications/application/professional-achievements",
      {
        method: "PUT",
        body: {
          declaredNone: none,
          achievements: none
            ? []
            : [
                expect.objectContaining({
                  type: "AWARD",
                  title: "Research award",
                  organisation: null,
                  description: null,
                }),
              ],
        },
      },
    );
  });
  it.each(["DIPLOMA", "CERTIFICATE", "DEGREE", "MASTERS", "PROFESSIONAL", "OTHER"])(
    "saves %s evidence with the correct eligibility-level mapping",
    async (kind) => {
      await render();
      await section("Qualifications");
      await setField(wrapper, "Qualification type", kind);
      expect(
        wrapper
          .findAll("button")
          .find((button) => button.text() === "Save")!
          .attributes("disabled"),
      ).toBeDefined();
      await clickButton(wrapper, "Upload Qualification evidence");
      await setField(wrapper, "Qualification title", "Advanced qualification");
      await setField(wrapper, "School or institution", "University");
      await setField(wrapper, "Qualification duration (months)", "24");
      await clickButton(wrapper, "Save");
      expect(context.request).toHaveBeenCalledWith(
        "/api/admissions/applications/application/qualification-aggregates",
        {
          method: "POST",
          body: expect.objectContaining({
            level: kind === "CERTIFICATE" ? "OTHER" : kind === "MASTERS" ? "DEGREE" : kind,
            awardTypeCode: kind,
            documentId: "document",
            durationMonths: 24,
            results: [],
          }),
        },
      );
    },
  );
  it.each(["O_LEVEL", "A_LEVEL"])(
    "captures %s subjects only after evidence upload and keeps one final row",
    async (level) => {
      await render();
      await section("Qualifications");
      await setField(wrapper, "Qualification type", level);
      await clickButton(wrapper, "Upload Qualification evidence");
      expect(wrapper.findAll('[data-label="Subject"]')).toHaveLength(level === "O_LEVEL" ? 8 : 3);
      while (wrapper.findAll('[aria-label^="Remove subject"]').length)
        await wrapper.find('[aria-label^="Remove subject"]').trigger("click");
      await setField(wrapper, "Exam body", "zimsec");
      await setField(wrapper, "Subject", level === "O_LEVEL" ? "maths" : "physics");
      await setField(wrapper, "Grade", "A");
      if (level === "A_LEVEL") await setField(wrapper, "Principal subject", true);
      await clickButton(wrapper, "Save");
      expect(context.request).toHaveBeenCalledWith(
        "/api/admissions/applications/application/qualification-aggregates",
        {
          method: "POST",
          body: expect.objectContaining({
            level,
            documentId: "document",
            examBodyId: "zimsec",
            results: [
              {
                subjectId: level === "O_LEVEL" ? "maths" : "physics",
                grade: "A",
                principalSubject: level === "A_LEVEL",
              },
            ],
          }),
        },
      );
    },
  );
  it("bounds school result rows at twenty and prevents duplicate subject selection", async () => {
    await render();
    await section("Qualifications");
    await clickButton(wrapper, "Upload Qualification evidence");
    await setField(wrapper, "Subject", "maths");
    expect(wrapper.findAll('[data-label="Subject"]')[1]!.text()).not.toContain("Mathematics");
    for (let count = 8; count < 20; count++) await clickButton(wrapper, "Add another subject");
    expect(wrapper.findAll('[data-label="Subject"]')).toHaveLength(20);
    expect(
      wrapper.findAll("button").some((button) => button.text() === "Add another subject"),
    ).toBe(false);
  });
  it.each(["Prior UZ study", "Professional achievements"])(
    "reports declaration errors in %s",
    async (name) => {
      await render();
      await section(name);
      if (name === "Professional achievements")
        await setField(wrapper, "I have no professional achievements to declare", true);
      context.request.mockRejectedValueOnce(new Error("Invalid evidence"));
      await clickButton(wrapper, name === "Prior UZ study" ? "Save" : "Save");
      expect(context.showError).toHaveBeenCalledWith(
        expect.stringContaining("could not be saved"),
        "Invalid evidence",
      );
      expect(showSuccess).not.toHaveBeenCalled();
    },
  );
  it("requires confirmation for declaration and submission and preserves failure feedback", async () => {
    workspace.readyForSubmission = true;
    workspace.missingRequirements = [];
    await render();
    await section("Review and declaration");
    confirmAction.mockResolvedValueOnce(false);
    await clickButton(wrapper, "Accept declaration");
    expect(context.request.mock.calls.some(([, options]) => options?.method)).toBe(false);
    context.request.mockRejectedValueOnce(new Error("Missing requirement"));
    await clickButton(wrapper, "Accept declaration");
    expect(context.showError).toHaveBeenCalledWith(
      "Declaration could not be recorded",
      "Missing requirement",
    );
    await clickButton(wrapper, "Accept declaration");
    expect(context.request).toHaveBeenCalledWith(
      "/api/admissions/applications/application/declaration",
      { method: "PUT", body: { accepted: true, declarationVersion: "2026.1" } },
    );
    confirmAction.mockResolvedValueOnce(false);
    await clickButton(wrapper, "Submit application");
    expect(push).not.toHaveBeenCalled();
    context.request.mockRejectedValueOnce(new Error("Payment required"));
    await clickButton(wrapper, "Submit application");
    expect(context.showError).toHaveBeenCalledWith(
      "Application could not be submitted",
      "Payment required",
    );
    await clickButton(wrapper, "Submit application");
    expect(context.request).toHaveBeenCalledWith(
      "/api/admissions/applications/application/submission",
      { method: "POST" },
    );
    expect(push).toHaveBeenCalledWith("/");
  });
  it("renders submitted records read-only and never offers draft capture", async () => {
    workspace.application.status = "SUBMITTED";
    workspace.nextOfKin = [recordCases[0]!.record];
    workspace.employmentHistory = [recordCases[1]!.record];
    workspace.referees = [recordCases[2]!.record];
    await render();
    for (const scenario of recordCases) {
      await section(scenario.section);
      expect(wrapper.find("#inline-record-editor").exists()).toBe(false);
      expect(wrapper.findAll("button").some((button) => button.text() === "Edit")).toBe(false);
    }
    expect(wrapper.findAll("button").some((button) => button.text() === "Save")).toBe(false);
    await clickButton(wrapper, "Return to applications");
    expect(push).toHaveBeenCalledWith("/");
  });
  it("recovers workspace load failures via Retry and hides unconfigured fee/document steps", async () => {
    context.request.mockRejectedValueOnce(new Error("Service unavailable"));
    await render();
    expect(wrapper.text()).toContain("Service unavailable");
    await clickButton(wrapper, "Retry");
    expect(wrapper.text()).toContain("Undergraduate · 2026-S1");
    expect(wrapper.get(".steps").text()).not.toContain("Application fee");
    expect(wrapper.get(".steps").text()).not.toContain("Supporting documents");
  });
});

describe("Applicant payment and profile readiness", () => {
  async function embeddedCheckout() {
    configurePayment();
    await render();
    await section("Application fee");
    await clickButton(wrapper, "Pay USD 20 now");
    const frame = wrapper.get("iframe");
    const checkoutWindow = { postMessage: vi.fn() };
    Object.defineProperty(frame.element, "contentWindow", {
      value: checkoutWindow,
      configurable: true,
    });
    return { frame, checkoutWindow };
  }
  async function checkoutMessage(
    source: unknown,
    data: unknown,
    origin = "https://checkout.example.test",
  ) {
    window.dispatchEvent(new MessageEvent("message", { source: source as Window, origin, data }));
    await flushPromises();
  }

  it("posts the checkout form once and only to the configured payment origin", async () => {
    const { frame, checkoutWindow } = await embeddedCheckout();
    await frame.trigger("load");
    await frame.trigger("load");
    expect(checkoutWindow.postMessage).toHaveBeenCalledExactlyOnceWith(
      JSON.stringify({
        form: [
          {
            id: "Lite_Merchant_Trace",
            name: "Lite_Merchant_Trace",
            type: "hidden",
            value: "trace",
          },
          { id: "amount", name: "amount", type: "hidden", value: "20.00" },
        ],
      }),
      "https://checkout.example.test",
    );
  });

  it.each([
    "wrong-window",
    "wrong-origin",
    "invalid-json",
    "null",
    "array",
    "no-status",
    "wrong-trace",
  ])("ignores %s payment messages", async (scenario) => {
    const { checkoutWindow } = await embeddedCheckout();
    const body =
      scenario === "invalid-json"
        ? "{"
        : scenario === "null"
          ? null
          : scenario === "array"
            ? []
            : scenario === "no-status"
              ? {}
              : {
                  Lite_Payment_Card_Status: "0",
                  Lite_Merchant_Trace: scenario === "wrong-trace" ? "foreign-trace" : "trace",
                };
    context.request.mockClear();
    await checkoutMessage(
      scenario === "wrong-window" ? window : checkoutWindow,
      body,
      scenario === "wrong-origin"
        ? "https://attacker.example.test"
        : "https://checkout.example.test",
    );
    expect(context.request).not.toHaveBeenCalled();
    expect(showSuccess).not.toHaveBeenCalled();
    expect(wrapper.find("iframe").exists()).toBe(true);
  });

  it.each(["declined", "pending", "unavailable", "paid"])(
    "handles a trusted %s checkout response through Finance reconciliation",
    async (status) => {
      const { checkoutWindow } = await embeddedCheckout();
      if (status === "unavailable")
        context.request.mockRejectedValueOnce(new Error("Finance unavailable"));
      if (status === "paid") {
        workspace.application.paymentClearanceStatus = "PAID";
        context.request.mockResolvedValueOnce({ status: "PAID", workflowCleared: true });
      }
      await checkoutMessage(
        checkoutWindow,
        JSON.stringify({
          Lite_Payment_Card_Status: status === "declined" ? "5" : "0",
          Lite_Merchant_Trace: "trace",
        }),
        "http://localhost:3001",
      );
      expect(wrapper.find("iframe").exists()).toBe(false);
      if (status === "declined")
        expect(context.showError).toHaveBeenCalledWith(
          "Payment was not completed",
          expect.any(String),
        );
      else {
        expect(context.request).toHaveBeenCalledWith(
          "/api/finance/application-payment-references/by-application/application/online-checkouts/reconcile",
          { method: "POST", body: { attemptId: "attempt" } },
        );
        if (status === "paid") {
          expect(showSuccess).toHaveBeenCalledWith(
            "Payment confirmed",
            "Your application fee has been cleared.",
          );
          expect(wrapper.text()).toContain("Application fee confirmed");
        } else
          expect(context.showError).toHaveBeenCalledWith(
            "Payment confirmation pending",
            expect.any(String),
          );
      }
    },
  );

  function configurePayment(available = true, proofs: Record<string, unknown>[] = []) {
    Object.assign(workspace.application, {
      paymentRequired: true,
      paymentClearanceStatus: "PENDING",
      canEnterReview: false,
      payment: {
        reference: "PAY-001",
        financePaymentReferenceId: "finance-reference",
        currencyCode: "USD",
        amountDue: 20,
        baseAmountDue: 20,
        status: "CREATED",
        ratingStatus: "RATED",
      },
    });
    const original = context.request.getMockImplementation()!;
    context.request.mockImplementation(async (path: string, options?: { method?: string }) => {
      if (path.endsWith("/payment-options"))
        return {
          onlinePayment: { available, availabilityMessage: "Payments temporarily unavailable" },
        };
      if (path.startsWith("/api/documents/uploads?")) return proofs;
      if (path.endsWith("/reconcile")) return { status: "PENDING", workflowCleared: false };
      if (path.endsWith("/online-checkouts"))
        return {
          attemptId: "attempt",
          embeddedCheckoutUrl: "https://checkout.example.test/payment",
          returnMessageOrigin: "http://localhost:3001",
          formParameters: { Lite_Merchant_Trace: "trace", amount: "20.00" },
        };
      return original(path, options);
    });
  }
  it.each(["PAID", "UNRATED", "PENDING"])(
    "renders truthful fee guidance for %s",
    async (status) => {
      configurePayment();
      workspace.application.paymentClearanceStatus = status;
      await render();
      await section("Application fee");
      expect(wrapper.text()).toContain(
        status === "PAID"
          ? "Your application fee has been paid"
          : status === "UNRATED"
            ? "confirm the exchange rate"
            : "Confirmation appears here after payment",
      );
      if (status === "PAID")
        expect(
          wrapper.findAll("button").some((button) => button.text().startsWith("Pay USD")),
        ).toBe(false);
    },
  );
  it("does not advertise checkout when the configured provider is unavailable", async () => {
    configurePayment(false);
    await render();
    await section("Application fee");
    expect(wrapper.text()).toContain("Online payment unavailable");
    expect(wrapper.text()).toContain("Payments temporarily unavailable");
    expect(wrapper.findAll("button").some((button) => button.text().startsWith("Pay USD"))).toBe(
      false,
    );
  });
  it("creates and cancels embedded checkout without claiming a payment", async () => {
    configurePayment();
    await render();
    await section("Application fee");
    await clickButton(wrapper, "Pay USD 20 now");
    expect(context.request).toHaveBeenCalledWith(
      "/api/finance/application-payment-references/by-application/application/online-checkouts",
      { method: "POST", body: { emailAddress: "applicant@example.test" } },
    );
    expect(wrapper.get("iframe").attributes("src")).toBe("https://checkout.example.test/payment");
    await clickButton(wrapper, "Cancel payment");
    expect(wrapper.find("iframe").exists()).toBe(false);
    expect(showSuccess).not.toHaveBeenCalled();
  });
  it("reports checkout creation errors without hiding retry controls", async () => {
    configurePayment();
    await render();
    await section("Application fee");
    context.request.mockRejectedValueOnce(new Error("Gateway unavailable"));
    await clickButton(wrapper, "Pay USD 20 now");
    expect(context.showError).toHaveBeenCalledWith(
      "Online payment could not be started",
      "Gateway unavailable",
    );
    expect(wrapper.find("iframe").exists()).toBe(false);
  });
  it.each(["pending", "error", "paid"])(
    "reconciles %s against Finance instead of trusting a browser success",
    async (status) => {
      configurePayment();
      await render();
      await section("Application fee");
      if (status === "error") context.request.mockRejectedValueOnce(new Error("Unavailable"));
      else if (status === "paid") {
        workspace.application.paymentClearanceStatus = "PAID";
        context.request.mockResolvedValueOnce({ status: "PAID", workflowCleared: true });
      }
      await clickButton(wrapper, "Check payment status");
      expect(context.request).toHaveBeenCalledWith(
        "/api/finance/application-payment-references/by-application/application/online-checkouts/reconcile",
        { method: "POST", body: {} },
      );
      if (status === "paid") {
        expect(wrapper.text()).toContain("Application fee confirmed");
        expect(showSuccess).toHaveBeenCalledWith(
          "Payment confirmed",
          "Your application fee has been cleared.",
        );
      } else {
        expect(context.showError).toHaveBeenCalledWith(
          status === "error" ? "Payment status could not be checked" : "Payment not yet confirmed",
          expect.any(String),
        );
        expect(showSuccess).not.toHaveBeenCalled();
      }
    },
  );
  it.each([false, true])(
    "uploads Finance-owned proof with rejected-evidence replacement=%s",
    async (replacing) => {
      configurePayment(
        true,
        replacing
          ? [
              {
                id: "old-proof",
                documentTypeCode: "PROOF_OF_PAYMENT",
                originalFileName: "old.pdf",
                uploadedAt: "2026-08-01",
                verificationStatus: "REJECTED",
                rejectionReason: "Illegible",
              },
            ]
          : [],
      );
      await render();
      await section("Application fee");
      const field = wrapper
        .findAllComponents(FormField)
        .find((field) => field.props("label") === "Proof of payment")!;
      const proof = new File(["evidence"], "proof.pdf", { type: "application/pdf" });
      field.vm.$emit("update:modelValue", replacing ? [proof] : proof);
      await flushPromises();
      const upload = context.request.mock.calls.find(
        ([path, options]) => path === "/api/documents/uploads" && options?.method === "POST",
      )!;
      const form = upload[1].body as FormData;
      expect(form.get("ownerType")).toBe("FINANCE_RECORD");
      expect(form.get("ownerId")).toBe("finance-reference");
      expect(form.get("documentTypeCode")).toBe("PROOF_OF_PAYMENT");
      expect(form.get("replacesDocumentId")).toBe(replacing ? "old-proof" : null);
      expect(showSuccess).toHaveBeenCalledWith(
        "Proof of payment uploaded",
        expect.stringContaining("independently verify"),
      );
    },
  );
  it("reports proof upload failure and keeps the selected evidence available", async () => {
    configurePayment();
    await render();
    await section("Application fee");
    context.request.mockRejectedValueOnce(new Error("Storage unavailable"));
    const field = wrapper
      .findAllComponents(FormField)
      .find((field) => field.props("label") === "Proof of payment")!;
    field.vm.$emit("update:modelValue", new File(["evidence"], "proof.pdf"));
    await flushPromises();
    expect(context.showError).toHaveBeenCalledWith(
      "Proof of payment could not be uploaded",
      "Storage unavailable",
    );
    expect(field.props("modelValue")).toBeInstanceOf(File);
  });
  it("saves profile changes before continuing and carries the current version", async () => {
    await render();
    await setField(wrapper, "Phone number", "+263771999999");
    await nextSection();
    expect(context.request).toHaveBeenCalledWith(
      "/api/admissions/applications/application/profile",
      {
        method: "PUT",
        body: expect.objectContaining({
          primaryPhone: "+263771999999",
          expectedVersion: 4,
          middleNames: null,
        }),
      },
    );
    expect(wrapper.get("h1").text()).toBe("Next of kin");
    await clickButton(wrapper, "Save");
    expect(wrapper.text()).toContain("Complete the highlighted fields.");
    expect(context.notify).not.toHaveBeenCalled();
    await clickButton(wrapper, "Back");
    expect(wrapper.find('.field[data-label="First name"]').exists()).toBe(true);
  });
  it("keeps an incomplete or failed profile on the same step", async () => {
    await render();
    await setField(wrapper, "Phone number", "");
    await nextSection();
    expect(wrapper.find("#inline-record-editor").exists()).toBe(false);
    await setField(wrapper, "Phone number", "+263771999999");
    context.request.mockRejectedValueOnce(new Error("Duplicate identity"));
    await nextSection();
    expect(context.showError).toHaveBeenCalledWith(
      "Profile could not be saved",
      "Duplicate identity",
    );
    expect(wrapper.find("#inline-record-editor").exists()).toBe(false);
    await clickButton(wrapper, "Save");
    expect(context.notify).toHaveBeenCalledWith(expect.objectContaining({ title: "Saved" }));
  });
  it("unlocks identity capture on stored evidence without waiting for OCR", async () => {
    workspace.documents.requirements = [
      {
        requirementCode: "NATIONAL_ID",
        requirementName: "National ID",
        captureSectionCode: "PERSONAL_DETAILS",
        state: "MISSING",
        required: true,
        documentId: null,
      },
    ];
    await render();
    expect(wrapper.text()).toContain("Upload the required identity evidence to continue");
    await clickButton(wrapper, "Upload National ID");
    expect(wrapper.text()).not.toContain("Upload the required identity evidence to continue");
    expect(context.request.mock.calls.some(([path]) => path.includes("prefill"))).toBe(false);
  });
});
