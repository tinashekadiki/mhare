// Author: Tinashe K

import { computed, defineComponent, h, onMounted, reactive, ref, watch } from "vue";
import { config, flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";

Object.assign(globalThis, { computed, onMounted, reactive, ref, watch });
vi.stubGlobal("definePageMeta", vi.fn());
config.global.renderStubDefaultSlot = true;

const SlotStub = defineComponent({
  setup(_, { slots }) {
    return () =>
      h(
        "div",
        Object.values(slots).flatMap((slot) => slot?.() ?? []),
      );
  },
});

const ButtonStub = defineComponent({
  props: { label: { type: String, default: "" } },
  emits: ["click"],
  setup(props, { emit }) {
    return () => h("button", { onClick: () => emit("click") }, props.label);
  },
});

function mountApplicantRegisterPage(component: object) {
  return mount(component, {
    global: {
      stubs: {
        UDashboardPanel: SlotStub,
        UDashboardNavbar: true,
        UDashboardToolbar: true,
        EmhareRecordDrawer: SlotStub,
        EmharePaginatedTable: true,
        EmhareKpiCard: true,
        EmhareStatusPill: true,
        UButton: ButtonStub,
        UAlert: true,
        USkeleton: true,
      },
    },
  });
}

describe("Applicant register detail workspace", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("shows the accepted programme and opens the current published offer letter for printing", async () => {
    const applicantId = "applicant-61";
    const applicationId = "application-61";
    const offer = {
      id: "offer-1",
      applicationId,
      offerNumber: "OFR-AUG-2026-00000001",
      programmeCode: "HCS",
      programmeName: "Computer Science",
      status: "CONVERTED",
      currentPublicationId: "publication-1",
      generatedDocumentId: "generated-document-1",
      convertedStudentNumber: "R260061K",
      response: { response: "ACCEPTED", respondedAt: "2026-08-16T12:00:00Z", notes: null },
    };
    const details = {
      profile: {
        id: applicantId,
        applicantNumber: "A000061",
        applicantCategoryCode: "LOCAL",
        firstName: "Wesley",
        middleNames: null,
        lastName: "Oneill",
        primaryEmail: "wesley@example.test",
        primaryPhone: null,
        completenessPercentage: 100,
        missingRequiredFields: [],
        version: 1,
      },
      applications: [
        {
          id: applicationId,
          applicationNumber: "EMH-AUG-2026-00000061",
          intakeCode: "AUG-2026",
          applicationTypeName: "Undergraduate and Diploma",
          status: "CONVERTED",
          programmeChoices: [],
        },
      ],
    };
    const request = vi.fn(async (path: string) => {
      if (path === `/api/admissions/applicants/${applicantId}`) return details;
      if (path === `/api/admissions/work-items/${applicationId}`) return { offer };
      if (path.includes("/api/documents/generated-document-1/download")) {
        return {
          documentId: "generated-document-1",
          downloadUrl: "http://localhost:9000/current-offer.pdf",
        };
      }
      return { content: [], totalElements: 0 };
    });
    vi.stubGlobal("useEmhareApi", () => ({ request, errorMessage: vi.fn() }));
    vi.stubGlobal("useEmhareConfirm", () => ({ showError: vi.fn(), showSuccess: vi.fn() }));
    const documentTab = { location: { href: "about:blank" }, close: vi.fn(), opener: window };
    vi.spyOn(window, "open").mockReturnValue(documentTab as unknown as Window);

    const ApplicantRegisterPage = (await import("../../pages/operations/applicants.vue")).default;
    const wrapper = mountApplicantRegisterPage(ApplicantRegisterPage);
    await (
      wrapper.vm as unknown as { openApplicant: (row: { id: string }) => Promise<void> }
    ).openApplicant({ id: applicantId });
    await flushPromises();

    const applicantWorkspace = wrapper.vm as unknown as {
      offerForApplication: (selectedApplicationId: string) => typeof offer | null;
      offerOutcomeLabel: (selectedOffer: typeof offer) => string;
      isPublishedOfferLetterAvailable: (selectedOffer: typeof offer) => boolean;
      printOfferLetter: (selectedOffer: typeof offer) => Promise<void>;
    };
    expect(applicantWorkspace.offerForApplication(applicationId)).toEqual(offer);
    expect(applicantWorkspace.offerOutcomeLabel(offer)).toBe("Accepted programme");
    expect(applicantWorkspace.isPublishedOfferLetterAvailable(offer)).toBe(true);
    expect(wrapper.text()).toContain("Accepted programme");
    expect(wrapper.text()).toContain("HCS · Computer Science");
    expect(wrapper.get("button").text()).toContain("Print offer letter");

    await applicantWorkspace.printOfferLetter(offer);

    expect(request).toHaveBeenCalledWith(
      "/api/documents/generated-document-1/download?disposition=inline",
    );
    expect(documentTab.location.href).toBe("http://localhost:9000/current-offer.pdf");
  });

  it("does not offer printing when no current publication exists", async () => {
    const showError = vi.fn();
    vi.stubGlobal("useEmhareApi", () => ({
      request: vi.fn(async () => ({ content: [], totalElements: 0 })),
      errorMessage: vi.fn(),
    }));
    vi.stubGlobal("useEmhareConfirm", () => ({ showError, showSuccess: vi.fn() }));
    const ApplicantRegisterPage = (await import("../../pages/operations/applicants.vue")).default;
    const wrapper = mountApplicantRegisterPage(ApplicantRegisterPage);
    const unavailableOffer = {
      id: "offer-unpublished",
      currentPublicationId: null,
      generatedDocumentId: "generated-document-1",
    };
    const applicantWorkspace = wrapper.vm as unknown as {
      isPublishedOfferLetterAvailable: (selectedOffer: typeof unavailableOffer) => boolean;
      offerOutcomeLabel: (selectedOffer: {
        response?: { response: string };
        status: string;
      }) => string;
      printOfferLetter: (selectedOffer: typeof unavailableOffer) => Promise<void>;
    };

    expect(applicantWorkspace.isPublishedOfferLetterAvailable(unavailableOffer)).toBe(false);
    expect(applicantWorkspace.offerOutcomeLabel({ status: "DECLINED" })).toBe("Declined programme");
    expect(applicantWorkspace.offerOutcomeLabel({ status: "SENT" })).toBe("Offered programme");
    await applicantWorkspace.printOfferLetter(unavailableOffer);
    expect(showError).toHaveBeenCalledWith(
      "Offer letter is not available",
      "A current published offer letter is not available for this application.",
    );
  });

  it("handles blocked tabs, failed document requests, and unavailable offer outcomes", async () => {
    const showError = vi.fn();
    const request = vi.fn(async (path: string) => {
      if (path === "/api/admissions/applicants/applicant-61") {
        return {
          profile: {
            id: "applicant-61",
            applicantNumber: "A000061",
            applicantCategoryCode: "LOCAL",
            firstName: "Wesley",
            middleNames: null,
            lastName: "Oneill",
            primaryEmail: "wesley@example.test",
            completenessPercentage: 100,
            missingRequiredFields: [],
            version: 1,
          },
          applications: [
            { id: "application-61", applicationNumber: "EMH-61", status: "CONVERTED" },
          ],
        };
      }
      if (path === "/api/admissions/work-items/application-61") throw new Error("unavailable");
      if (path.includes("/api/documents/")) throw new Error("document unavailable");
      return { content: [], totalElements: 0 };
    });
    vi.stubGlobal("useEmhareApi", () => ({
      request,
      errorMessage: vi.fn(() => "The published document could not be loaded."),
    }));
    vi.stubGlobal("useEmhareConfirm", () => ({ showError, showSuccess: vi.fn() }));
    const ApplicantRegisterPage = (await import("../../pages/operations/applicants.vue")).default;
    const wrapper = mountApplicantRegisterPage(ApplicantRegisterPage);
    const applicantWorkspace = wrapper.vm as unknown as {
      openApplicant: (row: { id: string }) => Promise<void>;
      offerForApplication: (applicationId: string) => unknown;
      printOfferLetter: (offer: {
        id: string;
        currentPublicationId: string;
        generatedDocumentId: string;
      }) => Promise<void>;
    };

    await applicantWorkspace.openApplicant({ id: "applicant-61" });
    expect(applicantWorkspace.offerForApplication("application-61")).toBeNull();

    vi.spyOn(window, "open").mockReturnValueOnce(null);
    const publishedOffer = {
      id: "offer-61",
      currentPublicationId: "publication-61",
      generatedDocumentId: "document-61",
    };
    await applicantWorkspace.printOfferLetter(publishedOffer);
    expect(showError).toHaveBeenCalledWith(
      "Offer letter could not be opened",
      "The browser blocked the document tab. Allow pop-ups for eMhare and try again.",
    );

    const documentTab = { location: { href: "about:blank" }, close: vi.fn(), opener: window };
    vi.spyOn(window, "open").mockReturnValueOnce(documentTab as unknown as Window);
    await applicantWorkspace.printOfferLetter(publishedOffer);
    expect(documentTab.close).toHaveBeenCalled();
    expect(showError).toHaveBeenCalledWith(
      "Offer letter could not be opened",
      "The published document could not be loaded.",
    );
  });

  it("renders declined outcomes, omits unavailable offers, and reports loading failures", async () => {
    const request = vi.fn(async (path: string, options?: { query?: Record<string, string> }) => {
      if (path === "/api/admissions/applicants/applicant-61") {
        return {
          profile: {
            id: "applicant-61",
            applicantNumber: "A000061",
            applicantCategoryCode: "LOCAL",
            firstName: "Wesley",
            middleNames: null,
            lastName: "Oneill",
            primaryEmail: "wesley@example.test",
            completenessPercentage: 100,
            missingRequiredFields: [],
            version: 1,
          },
          applications: [
            {
              id: "declined-application",
              applicationNumber: "EMH-DECLINED",
              intakeCode: "AUG-2026",
              applicationTypeName: "Undergraduate and Diploma",
              status: "DECLINED",
            },
            {
              id: "application-without-offer",
              applicationNumber: "EMH-NO-OFFER",
              intakeCode: "AUG-2026",
              applicationTypeName: "Undergraduate and Diploma",
              status: "UNDER_REVIEW",
            },
          ],
        };
      }
      if (path === "/api/admissions/work-items/declined-application") {
        return {
          offer: {
            id: "declined-offer",
            applicationId: "declined-application",
            offerNumber: "OFR-DECLINED",
            programmeCode: "HCS",
            programmeName: "Computer Science",
            status: "DECLINED",
            response: { response: "DECLINED", respondedAt: "2026-08-16T12:00:00Z" },
          },
        };
      }
      if (path === "/api/admissions/work-items/application-without-offer") {
        return { offer: null };
      }
      if (path === "/api/admissions/applicants") {
        if (options?.query?.category === "LOCAL") throw new Error("register unavailable");
        return { content: [], totalElements: 0 };
      }
      throw new Error("profile unavailable");
    });
    vi.stubGlobal("useEmhareApi", () => ({
      request,
      errorMessage: vi.fn((_: unknown, fallback?: string) => fallback ?? "Request failed."),
    }));
    vi.stubGlobal("useEmhareConfirm", () => ({ showError: vi.fn(), showSuccess: vi.fn() }));
    const ApplicantRegisterPage = (await import("../../pages/operations/applicants.vue")).default;
    const wrapper = mountApplicantRegisterPage(ApplicantRegisterPage);
    const applicantWorkspace = wrapper.vm as unknown as {
      categoryFilter: string;
      statusFilter: string;
      loadApplicants: () => Promise<void>;
      openApplicant: (row: { id: string }) => Promise<void>;
      loadApplicantDetails: (applicantId: string) => Promise<void>;
      offerForApplication: (applicationId: string) => unknown;
      loadError: string;
      detailsError: string;
    };

    await applicantWorkspace.openApplicant({ id: "applicant-61" });
    await flushPromises();

    expect(wrapper.text()).toContain("Declined programme");
    expect(wrapper.text()).toContain("HCS · Computer Science");
    expect(wrapper.text()).not.toContain("Student R");
    expect(applicantWorkspace.offerForApplication("application-without-offer")).toBeNull();

    applicantWorkspace.categoryFilter = "LOCAL";
    applicantWorkspace.statusFilter = "DECLINED";
    await applicantWorkspace.loadApplicants();
    expect(applicantWorkspace.loadError).toBe("The applicant register could not be loaded.");

    await applicantWorkspace.loadApplicantDetails("missing-applicant");
    expect(applicantWorkspace.detailsError).toBe("The applicant profile could not be loaded.");
  });
});
