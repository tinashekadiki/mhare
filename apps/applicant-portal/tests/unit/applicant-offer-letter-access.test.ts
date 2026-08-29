// Author: Tinashe K

import { flushPromises, shallowMount } from "@vue/test-utils";
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type {
  AdmissionOfferSummary,
  ApplicantQualificationSitting,
} from "@emhare/portal-shell/types/admissions";
import { useApplicantOfferLetter } from "../../composables/useApplicantOfferLetter";

const request = vi.fn();
const errorMessage = vi.fn(() => "Document access failed.");
const showError = vi.fn();

vi.stubGlobal("ref", ref);
vi.stubGlobal("useEmhareApi", () => ({ request, errorMessage }));
vi.stubGlobal("useEmhareConfirm", () => ({ showError }));
Object.assign(globalThis, { computed, nextTick, onBeforeUnmount, onMounted, reactive, watch });
vi.stubGlobal("definePageMeta", vi.fn());

const offerLetterOpeningId = ref<string | null>(null);
const openOfferLetterFromWorkspace = vi.fn();
const useApplicantOfferLetterMock = vi.fn(() => ({
  openingOfferId: offerLetterOpeningId,
  openOfferLetter: openOfferLetterFromWorkspace,
}));
const topNavigationStub = {
  template: '<nav><slot name="meta"/><slot name="actions"/><slot/></nav>',
};
const buttonStub = {
  props: ["label", "loading"],
  emits: ["click"],
  template:
    '<button type="button" :data-label="label" @click="$emit(\'click\')">{{ label }}</button>',
};

function publishedOffer(): AdmissionOfferSummary {
  return {
    id: "offer-1",
    offerBatchId: null,
    offerNumber: "OFR-0001",
    applicationId: "application-1",
    applicationNumber: "EMH-0001",
    applicantNumber: "A000001",
    applicantName: "Applicant One",
    programmeChoiceId: "choice-1",
    programmeId: "programme-1",
    programmeVersionId: "programme-version-1",
    programmeCode: "HCS",
    programmeName: "Computer Science",
    intakeId: "intake-1",
    offerType: "FIRM",
    status: "SENT",
    currentDocumentVersionId: "document-version-1",
    currentPublicationId: "publication-1",
    amendmentPending: false,
    conditionsText: null,
    acceptanceDeadline: "2026-09-30T23:59:59Z",
    registrationDate: null,
    orientationDate: null,
    commencementDate: "2026-10-01",
    generatedDocumentId: "generated-document-1",
    approvedAt: "2026-08-16T10:00:00Z",
    sentAt: "2026-08-16T10:05:00Z",
    expiredAt: null,
    expiryReason: null,
    conversionRequestedAt: null,
    conversionRequestId: null,
    convertedStudentId: null,
    convertedStudentNumber: null,
    convertedAt: null,
    conditions: [],
    response: null,
  };
}

describe("Applicant offer-letter access", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    offerLetterOpeningId.value = null;
    vi.stubGlobal("useRoute", () => ({ params: { applicationId: "application-1" } }));
    vi.stubGlobal("useRouter", () => ({ push: vi.fn() }));
    vi.stubGlobal("useToast", () => ({ add: vi.fn() }));
    vi.stubGlobal("useEmhareAuth", () => ({
      authenticated: ref(false),
      loadUser: vi.fn(async () => undefined),
      syncCoreUser: vi.fn(async () => undefined),
    }));
    vi.stubGlobal("useApplicantOfferLetter", useApplicantOfferLetterMock);
    vi.stubGlobal("useEmhareConfirm", () => ({
      confirmAction: vi.fn(),
      showError,
      showSuccess: vi.fn(),
    }));
  });

  it("keeps published offer-letter preview and download available from the application workspace", async () => {
    request.mockResolvedValueOnce([publishedOffer()]);
    const ApplicantApplicationPage = (await import("../../pages/applications/[applicationId].vue"))
      .default;
    const wrapper = shallowMount(ApplicantApplicationPage, {
      global: {
        stubs: {
          EmhareTopNav: topNavigationStub,
          UButton: buttonStub,
        },
      },
    });
    const viewModel = wrapper.vm as unknown as {
      applicationOffer: AdmissionOfferSummary | null;
      loadApplicationOffer: () => Promise<void>;
      openQualification: (record?: ApplicantQualificationSitting) => void;
      qualificationForm: {
        level: string;
        durationMonths: number | null;
      };
    };

    await viewModel.loadApplicationOffer();
    await nextTick();

    const offerLetterButtons = wrapper.findAllComponents(buttonStub);
    const previewButton = offerLetterButtons.find(
      (button) => button.props("label") === "Preview offer letter",
    );
    const downloadButton = offerLetterButtons.find(
      (button) => button.props("label") === "Download offer letter",
    );
    expect(previewButton).toBeDefined();
    expect(downloadButton).toBeDefined();
    if (!previewButton || !downloadButton)
      throw new Error("Expected published offer-letter actions.");
    expect(request).toHaveBeenCalledWith("/api/admissions/offers/mine");
    expect(previewButton.props("loading")).toBe(false);

    viewModel.openQualification({
      id: "qualification-1",
      level: "DEGREE",
      examBody: null,
      institutionName: "University of Zimbabwe",
      centreNumber: null,
      candidateNumber: null,
      yearWritten: 2025,
      durationMonths: 48,
      countryId: null,
      documentId: null,
      verificationStatus: "CAPTURED",
      verifiedByUserId: null,
      verifiedAt: null,
      rejectionReason: null,
      results: [],
      version: 1,
    });
    await nextTick();
    expect(viewModel.qualificationForm).toMatchObject({ level: "DEGREE", durationMonths: 48 });

    viewModel.openQualification({
      id: "qualification-2",
      level: "DIPLOMA",
      examBody: null,
      institutionName: "University of Zimbabwe",
      centreNumber: null,
      candidateNumber: null,
      yearWritten: 2024,
      durationMonths: null,
      countryId: null,
      documentId: null,
      verificationStatus: "CAPTURED",
      verifiedByUserId: null,
      verifiedAt: null,
      rejectionReason: null,
      results: [],
      version: 2,
    });
    expect(viewModel.qualificationForm).toMatchObject({ level: "DIPLOMA", durationMonths: null });

    viewModel.openQualification();
    await nextTick();
    expect(viewModel.qualificationForm).toMatchObject({ level: "O_LEVEL", durationMonths: null });

    await previewButton.trigger("click");
    await downloadButton.trigger("click");
    expect(openOfferLetterFromWorkspace).toHaveBeenNthCalledWith(1, publishedOffer(), "inline");
    expect(openOfferLetterFromWorkspace).toHaveBeenNthCalledWith(2, publishedOffer(), "attachment");

    offerLetterOpeningId.value = "offer-1";
    await nextTick();
    expect(previewButton.props("loading")).toBe(true);

    request.mockResolvedValueOnce([]);
    await viewModel.loadApplicationOffer();
    await nextTick();
    expect(wrapper.find('[data-label="Preview offer letter"]').exists()).toBe(false);

    request.mockRejectedValueOnce(new Error("offers unavailable"));
    await viewModel.loadApplicationOffer();
    expect(showError).toHaveBeenCalledWith(
      "Offer letter could not be loaded",
      "Document access failed.",
    );
  });

  it("maps editable qualification OCR proposals into the aggregate form", async () => {
    const ApplicantApplicationPage = (await import("../../pages/applications/[applicationId].vue"))
      .default;
    const wrapper = shallowMount(ApplicantApplicationPage);
    const viewModel = wrapper.vm as unknown as {
      applyDocumentPrefill: (documentId: string, qualificationLevel?: string) => Promise<void>;
      openQualification: (record?: ApplicantQualificationSitting) => void;
      qualificationReferences: {
        examBodies: Array<{ id: string; code: string; name: string; scienceSubject: null }>;
        oLevelSubjects: never[];
        aLevelSubjects: never[];
        otherSubjects: never[];
      };
      countries: Array<{
        id: string;
        iso2Code: string;
        name: string;
        nationalityName: string;
      }>;
      qualificationForm: {
        examBodyId: string;
        institutionName: string;
        yearWritten: number;
        centreNumber: string;
        candidateNumber: string;
        countryId: string;
      };
      resultForms: Array<{
        subjectId: string;
        grade: string;
        principalSubject: boolean;
      }>;
    };
    viewModel.qualificationReferences = {
      examBodies: [{ id: "zimsec-id", code: "ZIMSEC", name: "ZIMSEC", scienceSubject: null }],
      oLevelSubjects: [],
      aLevelSubjects: [],
      otherSubjects: [],
    };
    viewModel.countries.push({
      id: "zimbabwe-id",
      iso2Code: "ZW",
      name: "Zimbabwe",
      nationalityName: "Zimbabwean",
    });
    request.mockResolvedValueOnce({
      documentId: "document-1",
      extractionStatus: "COMPLETED",
      manualEntryAllowed: true,
      personalFields: {
        examBodyCode: " zimsec ",
        schoolOrInstitution: " Chinhoyi High School ",
        yearWritten: "2024",
        centreNumber: " 080120 ",
        candidateNumber: " 5035 ",
        countryCode: "ZWE",
      },
      qualificationResults: [
        {
          subjectId: "biology-id",
          subjectName: "Biology",
          grade: "A",
          confirmationRequired: false,
          candidateSubjects: ["Biology"],
        },
        {
          subjectId: "ambiguous-id",
          subjectName: "Mathematics",
          grade: "Z",
          confirmationRequired: true,
          candidateSubjects: ["Mathematics", "Pure Mathematics"],
        },
      ],
      identityNameMismatch: null,
      warnings: [],
    });

    await viewModel.applyDocumentPrefill("document-1", "A_LEVEL");

    expect(viewModel.qualificationForm).toMatchObject({
      examBodyId: "zimsec-id",
      institutionName: "Chinhoyi High School",
      yearWritten: 2024,
      centreNumber: "080120",
      candidateNumber: "5035",
      countryId: "zimbabwe-id",
    });
    expect(viewModel.resultForms).toMatchObject([
      { subjectId: "biology-id", grade: "A", principalSubject: true },
      { subjectId: "", grade: "", principalSubject: true },
    ]);

    viewModel.qualificationForm.examBodyId = "existing-exam-body";
    viewModel.qualificationForm.institutionName = "Existing School";
    viewModel.qualificationForm.centreNumber = "existing-centre";
    viewModel.qualificationForm.candidateNumber = "existing-candidate";
    viewModel.qualificationForm.countryId = "existing-country";
    request.mockResolvedValueOnce({
      documentId: "document-2",
      extractionStatus: "COMPLETED",
      manualEntryAllowed: true,
      personalFields: {
        examBodyCode: 42,
        schoolOrInstitution: 42,
        yearWritten: 1800,
        centreNumber: 42,
        candidateNumber: 42,
        countryCode: "GBR",
      },
      qualificationResults: [],
      identityNameMismatch: null,
      warnings: [],
    });

    await viewModel.applyDocumentPrefill("document-2", "O_LEVEL");

    expect(viewModel.qualificationForm).toMatchObject({
      examBodyId: "existing-exam-body",
      institutionName: "Existing School",
      centreNumber: "existing-centre",
      candidateNumber: "existing-candidate",
      countryId: "existing-country",
    });
    expect(viewModel.resultForms).toHaveLength(2);
  });

  it("uses the same popup-safe offer-letter access from the applicant home page", async () => {
    const ApplicantHomePage = (await import("../../pages/index.vue")).default;
    shallowMount(ApplicantHomePage, {
      global: {
        stubs: {
          EmhareTopNav: topNavigationStub,
          UButton: buttonStub,
        },
      },
    });
    await flushPromises();

    expect(useApplicantOfferLetterMock).toHaveBeenCalledOnce();
  });

  it("opens the preview window before resolving the secure document URLs", async () => {
    const callOrder: string[] = [];
    const offerLetterWindow = {
      opener: window,
      location: { href: "about:blank" },
      close: vi.fn(),
    };
    vi.spyOn(window, "open").mockImplementation(() => {
      callOrder.push("window");
      return offerLetterWindow as unknown as Window;
    });
    request
      .mockImplementationOnce(async () => {
        callOrder.push("publication");
        return { generatedDocumentId: "generated-document-1" };
      })
      .mockResolvedValueOnce({ downloadUrl: "https://documents.example.test/offer.pdf" });

    const { openOfferLetter, openingOfferId } = useApplicantOfferLetter();
    await openOfferLetter(publishedOffer(), "inline");

    expect(callOrder).toEqual(["window", "publication"]);
    expect(request).toHaveBeenNthCalledWith(
      1,
      "/api/admissions/applicant/offers/offer-1/published-document",
    );
    expect(request).toHaveBeenNthCalledWith(
      2,
      "/api/documents/generated-document-1/applicant-download?disposition=inline",
    );
    expect(offerLetterWindow.location.href).toBe("https://documents.example.test/offer.pdf");
    expect(openingOfferId.value).toBeNull();
  });

  it("does not open a draft or unpublished offer letter", async () => {
    const windowOpen = vi.spyOn(window, "open");
    const offer = { ...publishedOffer(), currentPublicationId: null };

    await useApplicantOfferLetter().openOfferLetter(offer, "inline");

    expect(windowOpen).not.toHaveBeenCalled();
    expect(request).not.toHaveBeenCalled();
    expect(showError).toHaveBeenCalledWith(
      "Offer letter is not available",
      "Admissions has not published the current offer letter yet.",
    );
  });

  it("explains how to retry when the browser blocks the preview window", async () => {
    vi.spyOn(window, "open").mockReturnValue(null);

    await useApplicantOfferLetter().openOfferLetter(publishedOffer(), "inline");

    expect(request).not.toHaveBeenCalled();
    expect(showError).toHaveBeenCalledWith(
      "Offer letter could not be opened",
      "Allow pop-ups for eMhare, then try again.",
    );
  });

  it("closes the placeholder window when secure document access fails", async () => {
    const offerLetterWindow = {
      opener: window,
      location: { href: "about:blank" },
      close: vi.fn(),
    };
    vi.spyOn(window, "open").mockReturnValue(offerLetterWindow as unknown as Window);
    request.mockRejectedValue(new Error("network failure"));

    await useApplicantOfferLetter().openOfferLetter(publishedOffer(), "attachment");

    expect(offerLetterWindow.close).toHaveBeenCalledOnce();
    expect(errorMessage).toHaveBeenCalled();
    expect(showError).toHaveBeenCalledWith(
      "Offer letter could not be opened",
      "Document access failed.",
    );
  });
});
