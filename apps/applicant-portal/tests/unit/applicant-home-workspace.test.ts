// Author: Tinashe K
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { defineComponent, ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Swal from "sweetalert2";
import ApplicantHome from "../../pages/index.vue";
import { campusStubs } from "../../../../tests/unit/support/campus-page";
import { clickButton, operationalContext } from "../../../../tests/unit/support/operational-page";

vi.mock("sweetalert2", () => ({ default: { fire: vi.fn() } }));
const Alert = defineComponent({
  props: ["title", "description", "color"],
  template:
    '<aside :data-color="color"><strong>{{ title }}</strong>{{ description }}<slot name="description"/><slot name="actions"/><slot/></aside>',
});
const stubs = {
  ...campusStubs,
  UContainer: defineComponent({ template: "<section><slot/></section>" }),
  UAlert: Alert,
  UEmpty: Alert,
  EmhareProductBrand: true,
  EmhareStepList: true,
  USeparator: true,
  EmhareFormField: true,
  EmhareMarketingHero: defineComponent({
    props: ["description"],
    template:
      '<section><h1><slot name="title"/></h1>{{ description }}<slot name="actions"/></section>',
  }),
  UDropdownMenu: defineComponent({
    props: ["items"],
    template:
      '<div><slot/><button v-for="item in items.flat()" :key="item.label" :disabled="item.disabled" @click="item.onSelect?.()">{{ item.label }}</button></div>',
  }),
};
let wrapper: VueWrapper;
let context: ReturnType<typeof operationalContext>;
let applications: any[];
let offers: any[];
let registers: Record<string, any>;
let auth: ReturnType<typeof authentication>;
const confirmAction = vi.fn();
const showSuccess = vi.fn();
const openOfferLetter = vi.fn();
const openingOfferId = ref<string | null>(null);
function authentication() {
  return {
    authenticated: ref(true),
    displayName: ref("Applicant Example"),
    loadUser: vi.fn().mockResolvedValue(undefined),
    syncCoreUser: vi.fn().mockResolvedValue(undefined),
    login: vi.fn(),
    signup: vi.fn(),
    logout: vi.fn(),
  };
}
function applicationFixture(overrides = {}) {
  return {
    id: "application",
    applicantNumber: "APPLICANT-001",
    applicationNumber: "APP-001",
    applicationTypeName: "Undergraduate",
    status: "DRAFT",
    paymentRequired: false,
    paymentClearanceStatus: "NOT_REQUIRED",
    canSubmit: true,
    programmeChoices: [
      {
        id: "choice",
        choiceRank: 1,
        programmeCode: "BSC",
        programmeName: "Science",
        owningAcademicUnitName: "Science Faculty",
        programmeVersionCode: "2026",
      },
    ],
    payment: null,
    ...overrides,
  };
}
function registerFixture(overrides = {}) {
  return {
    requiredDocumentsUploaded: true,
    requirements: [],
    missingRequirementCodes: [],
    rejectedRequirementCodes: [],
    pendingRequirementCodes: [],
    ...overrides,
  };
}
function offerFixture(overrides = {}) {
  return {
    id: "offer",
    offerNumber: "OFFER-001",
    programmeCode: "BSC",
    programmeName: "Science",
    status: "SENT",
    offerType: "FIRM",
    acceptanceDeadline: "2026-09-30T23:59:59Z",
    commencementDate: "2026-10-01",
    conditions: [],
    response: null,
    currentPublicationId: "publication",
    amendmentPending: false,
    convertedStudentNumber: null,
    conversionRequestedAt: null,
    ...overrides,
  };
}
beforeEach(() => {
  context = operationalContext();
  auth = authentication();
  confirmAction.mockReset().mockResolvedValue(true);
  showSuccess.mockReset().mockResolvedValue(undefined);
  openOfferLetter.mockReset();
  openingOfferId.value = null;
  vi.mocked(Swal.fire)
    .mockReset()
    .mockResolvedValue({ isConfirmed: true, value: "  Another opportunity  " } as any);
  vi.stubGlobal("useEmhareAuth", () => auth);
  vi.stubGlobal("useEmhareConfirm", () => ({
    confirmAction,
    showSuccess,
    showError: context.showError,
  }));
  vi.stubGlobal("useApplicantOfferLetter", () => ({ openingOfferId, openOfferLetter }));
  applications = [applicationFixture()];
  offers = [];
  registers = { application: registerFixture() };
  context.request.mockImplementation(async (path: string, options?: any) => {
    if (options?.method === "POST")
      return {
        ...offers.find((offer) => path.includes(`/${offer.id}/`)),
        status: options.body.response,
        response: { respondedAt: "2026-08-30T10:00:00Z" },
      };
    if (path === "/api/admissions/applications/mine") return applications;
    if (path === "/api/admissions/offers/mine") return offers;
    const applicationId = path.match(/\/applications\/([^/]+)\/documents\/mine/)?.[1];
    if (applicationId) {
      if (registers[applicationId] instanceof Error) throw registers[applicationId];
      return registers[applicationId] ?? registerFixture();
    }
    throw new Error(`Unexpected request ${path}`);
  });
});
afterEach(() => {
  wrapper?.unmount();
  vi.unstubAllGlobals();
});
async function render() {
  wrapper = mount(ApplicantHome, { global: { stubs, mocks: { navigateTo: context.navigateTo } } });
  await flushPromises();
  return wrapper;
}
async function refresh() {
  await wrapper.get('[aria-label="Refresh"]').trigger("click");
  await flushPromises();
}
function writes() {
  return context.request.mock.calls.filter(([, options]) => options?.method);
}
function offerCard(id = "offer") {
  return wrapper.get(`[data-testid="admission-offer-${id}"]`);
}

describe("applicant home authenticated workspace", () => {
  it("requires account access before requesting any personal application data", async () => {
    auth.authenticated.value = false;
    await render();
    expect(auth.loadUser).toHaveBeenCalledOnce();
    expect(auth.syncCoreUser).not.toHaveBeenCalled();
    expect(context.request).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("University of Zimbabwe admissions");
    await clickButton(wrapper, "Sign in", 0);
    await clickButton(wrapper, "Sign in", 1);
    await clickButton(wrapper, "Create account", 0);
    await clickButton(wrapper, "Create account", 1);
    expect(auth.login).toHaveBeenNthCalledWith(2, "/");
    expect(auth.signup).toHaveBeenNthCalledWith(2, "/");
    expect(wrapper.find('[aria-label="Refresh"]').exists()).toBe(false);
  });
  it("syncs signed-in identity, offers empty-state routes, refreshes and signs out", async () => {
    applications = [];
    await render();
    expect(auth.syncCoreUser).toHaveBeenCalledOnce();
    expect(wrapper.text()).toContain("No applications yet");
    expect(wrapper.text()).toContain("Applicant Example");
    const starts = wrapper
      .findAll("button")
      .filter((button) => button.text() === "Start application");
    expect(starts).toHaveLength(2);
    expect(starts.every((button) => button.attributes("to") === "/applications/new")).toBe(true);
    applications = [applicationFixture()];
    await refresh();
    expect(wrapper.text()).toContain("APP-001");
    expect(wrapper.text()).not.toContain("No applications yet");
    await clickButton(wrapper, "Sign out");
    expect(auth.logout).toHaveBeenCalledOnce();
  });
  it("shows loading before application data arrives", async () => {
    let resolve!: (value: any[]) => void;
    context.request.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    await render();
    expect(wrapper.find('[aria-label="Loading applications"]').exists()).toBe(true);
    resolve(applications);
    await flushPromises();
    expect(wrapper.find('[aria-label="Loading applications"]').exists()).toBe(false);
  });
  it.each(["applications", "offers"])(
    "reports %s fetch failure and clears it on refresh",
    async (resource) => {
      const original = context.request.getMockImplementation()!;
      let failed = false;
      context.request.mockImplementation(async (path: string, options?: any) => {
        if (!failed && path === `/api/admissions/${resource}/mine`) {
          failed = true;
          throw new Error(`${resource} offline`);
        }
        return original(path, options);
      });
      await render();
      expect(wrapper.text()).toContain(`${resource} offline`);
      expect(wrapper.text()).toContain("Applications unavailable");
      await refresh();
      expect(wrapper.text()).not.toContain(`${resource} offline`);
    },
  );
  it.each([
    ["DRAFT", "Draft", "neutral", false, false],
    ["SUBMITTED", "Submitted", "info", true, false],
    ["UNDER_REVIEW", "Under Review", "info", true, false],
    ["OFFERED", "Offered", "success", true, true],
    ["ACCEPTED", "Accepted", "success", true, true],
    ["DECLINED", "Declined", "error", true, true],
    ["WITHDRAWN", "Withdrawn", "error", true, true],
  ] as const)(
    "renders %s journey and navigates to its application",
    async (status, label, tone, submitted, decided) => {
      applications = [applicationFixture({ status })];
      await render();
      expect(wrapper.findAll(`[data-tone="${tone}"]`).some((pill) => pill.text() === label)).toBe(
        true,
      );
      const steps = wrapper
        .findAll("span")
        .filter((step) => step.attributes("data-tone") === undefined);
      expect(
        steps
          .find((step) => step.text() === "Submitted")!
          .classes()
          .includes("font-medium"),
      ).toBe(submitted);
      expect(
        steps
          .find((step) => step.text() === "Decision")!
          .classes()
          .includes("font-medium"),
      ).toBe(decided);
      expect(wrapper.text()).toContain("Science Faculty · Curriculum 2026");
      expect(wrapper.text()).toContain("1 application");
      await clickButton(wrapper, status === "DRAFT" ? "Continue application" : "View application");
      expect(context.navigateTo).toHaveBeenCalledWith("/applications/application");
    },
  );
  it("lists blockers for missing choices, payment, missing and rejected evidence", async () => {
    applications = [
      applicationFixture({
        canSubmit: false,
        programmeChoices: [],
        paymentRequired: true,
        paymentClearanceStatus: "PENDING",
      }),
    ];
    registers.application = registerFixture({
      requiredDocumentsUploaded: false,
      missingRequirementCodes: ["ID"],
      rejectedRequirementCodes: ["TRANSCRIPT"],
    });
    await render();
    expect(wrapper.text()).toContain("Programme choices required");
    expect(wrapper.text()).toContain("Add at least one programme choice.");
    expect(wrapper.text()).toContain(
      "Pay the application fee and wait for Finance confirmation, or obtain an authorised waiver.",
    );
    expect(wrapper.text()).toContain("Upload the missing required documents: ID.");
    expect(wrapper.text()).toContain("Replace the rejected documents: TRANSCRIPT.");
    expect(wrapper.text()).toContain("1 replacement required");
    const documentsStep = wrapper
      .findAll("span")
      .find((step) => step.text() === "Documents & fee")!;
    expect(documentsStep.classes()).toContain("text-muted");
  });
  it("explains unrated payments without fabricating a USD amount", async () => {
    applications = [
      applicationFixture({
        canSubmit: false,
        paymentRequired: true,
        paymentClearanceStatus: "UNRATED",
        payment: {
          reference: "ZWG-001",
          amountDue: 1000,
          currencyCode: "ZWG",
          baseAmountDue: null,
          baseCurrencyCode: "USD",
        },
      }),
    ];
    await render();
    expect(wrapper.text()).toContain(
      "Finance must confirm the exchange rate before you can submit.",
    );
    expect(wrapper.text()).toContain("Awaiting effective rate");
    expect(wrapper.text()).toContain("Exchange rate pending");
    expect(wrapper.text()).toContain("Rate pending");
    expect(wrapper.text()).toContain("ZWG-001");
  });
  it("shows automatic online confirmation after submission without asking to submit again", async () => {
    applications = [
      applicationFixture({
        status: "SUBMITTED",
        paymentRequired: true,
        paymentClearanceStatus: "PENDING",
        canSubmit: false,
        payment: {
          reference: "PAY-001",
          amountDue: 25,
          currencyCode: "USD",
          baseAmountDue: 25,
          baseCurrencyCode: "USD",
        },
      }),
    ];
    await render();
    expect(wrapper.text()).toContain(
      "Successful online payments are confirmed automatically. Your application will enter review after confirmation.",
    );
    expect(wrapper.text()).not.toContain("Before submission");
    expect(wrapper.text()).not.toContain("unlock submission");
    expect(wrapper.text()).toContain("25.00");
  });
  it.each([null, "Waived for approved scholarship"])(
    "renders authorised waiver reason %s and clears fee guidance",
    async (reason) => {
      applications = [
        applicationFixture({
          paymentRequired: true,
          paymentClearanceStatus: "WAIVED",
          paymentWaiverReason: reason,
          canSubmit: false,
        }),
      ];
      await render();
      expect(wrapper.text()).toContain(reason || "An authorised finance officer waived this fee.");
      expect(wrapper.text()).not.toContain("Pay the application fee");
      expect(wrapper.text()).toContain(
        "Complete all required application sections before submission.",
      );
    },
  );
  it.each([
    [null, "USD"],
    [0, null],
  ] as const)(
    "renders absent fee amount %s or currency %s safely",
    async (amountDue, currencyCode) => {
      applications = [
        applicationFixture({
          paymentClearanceStatus: "PAID",
          paymentRequired: true,
          payment: {
            reference: "PAID-001",
            amountDue,
            currencyCode,
            baseAmountDue: 0,
            baseCurrencyCode: "USD",
          },
        }),
      ];
      await render();
      expect(wrapper.text()).toContain("No fee");
      expect(wrapper.text()).toContain("Paid");
      expect(wrapper.text()).not.toContain("Before submission");
    },
  );
  it.each([
    ["missingRequirementCodes", "1 document missing", "error"],
    ["pendingRequirementCodes", "1 pending verification", "warning"],
    ["rejectedRequirementCodes", "1 replacement required", "error"],
  ] as const)(
    "projects document state %s independently from workflow status",
    async (field, label, tone) => {
      registers.application = registerFixture({
        [field]: ["ID"],
        requiredDocumentsUploaded: field === "pendingRequirementCodes",
      });
      await render();
      expect(wrapper.findAll(`[data-tone="${tone}"]`).some((pill) => pill.text() === label)).toBe(
        true,
      );
    },
  );
  it("isolates document failures to their application and retries on refresh", async () => {
    applications.push(applicationFixture({ id: "second", applicationNumber: "APP-002" }));
    registers.application = new Error("Documents offline");
    await render();
    expect(wrapper.text()).toContain("2 applications");
    expect(wrapper.text()).toContain("Document requirements unavailable");
    expect(wrapper.text()).toContain("Requirements unavailable");
    expect(wrapper.text()).toContain("Wait for the document requirements to finish loading.");
    expect(wrapper.text()).toContain("Documents verified");
    registers.application = registerFixture();
    await refresh();
    expect(wrapper.text()).not.toContain("Documents offline");
    expect(wrapper.text()).not.toContain("Before submission");
  });
});

describe("applicant home offer decisions", () => {
  it.each([
    ["SENT", "Sent", "info"],
    ["ACCEPTED", "Accepted", "success"],
    ["CONVERTED", "Converted", "success"],
    ["DECLINED", "Declined", "error"],
    ["EXPIRED", "Expired", "error"],
    ["WITHDRAWN", "Withdrawn", "error"],
    ["DRAFT", "Draft", "neutral"],
  ] as const)(
    "renders %s and only exposes responses for actionable published offers",
    async (status, label, tone) => {
      offers = [offerFixture({ status })];
      await render();
      expect(
        offerCard()
          .findAll(`[data-tone="${tone}"]`)
          .some((pill) => pill.text() === label),
      ).toBe(true);
      expect(
        offerCard()
          .findAll("button")
          .some((button) => button.text() === "Accept offer"),
      ).toBe(status === "SENT");
      expect(offerCard().text()).toContain("Preview");
    },
  );
  it("does not expose response or document actions before publication", async () => {
    offers = [
      offerFixture({ currentPublicationId: null }),
      offerFixture({ id: "draft", status: "DRAFT", currentPublicationId: null }),
    ];
    await render();
    expect(offerCard().text()).toContain("Offer letter being prepared");
    expect(offerCard().findAll("button")).toHaveLength(0);
    expect(offerCard("draft").text()).not.toContain("Offer letter being prepared");
  });
  it("retains letter access but blocks responses during an amendment", async () => {
    offers = [offerFixture({ amendmentPending: true })];
    openingOfferId.value = "offer";
    await render();
    expect(offerCard().text()).toContain("Updated letter pending");
    expect(offerCard().text()).not.toContain("Accept offer");
    await clickButton(wrapper, "Preview");
    await clickButton(wrapper, "Download");
    expect(openOfferLetter).toHaveBeenNthCalledWith(1, offers[0], "inline");
    expect(openOfferLetter).toHaveBeenNthCalledWith(2, offers[0], "attachment");
    expect(offerCard().get("button").attributes("aria-busy")).toBe("true");
  });
  it.each([null, "Provide certified final results"])(
    "shows conditional evidence and conversion progress with condition text %s",
    async (conditionsText) => {
      offers = [
        offerFixture({
          offerType: "CONDITIONAL",
          conditionsText,
          conditions: [
            { code: "RESULT", description: "Certified results", status: "PENDING" },
            { code: "ID", description: "Identity verified", status: "SATISFIED" },
          ],
          response: { respondedAt: "2026-08-20T12:00:00Z" },
          conversionRequestedAt: "2026-08-21T12:00:00Z",
        }),
      ];
      await render();
      expect(offerCard().text()).toContain(
        conditionsText || "Review the listed conditions before responding.",
      );
      expect(offerCard().text()).toContain("Certified results");
      expect(offerCard().text()).toContain("This record cannot be changed.");
      expect(offerCard().text()).toContain("Student registration in progress");
      expect(offerCard().find('[data-tone="warning"]').text()).toBe("Pending");
    },
  );
  it("shows completed student conversion instead of progress", async () => {
    offers = [
      offerFixture({
        status: "CONVERTED",
        convertedStudentNumber: "R260001",
        conversionRequestedAt: "2026-08-21T12:00:00Z",
      }),
    ];
    await render();
    expect(offerCard().text()).toContain("Your student number is R260001");
    expect(offerCard().text()).not.toContain("Student registration in progress");
  });
  it("does not send a response when acceptance confirmation is cancelled", async () => {
    offers = [offerFixture()];
    confirmAction.mockResolvedValue(false);
    await render();
    await clickButton(wrapper, "Accept offer");
    expect(writes()).toHaveLength(0);
    expect(confirmAction).toHaveBeenCalledWith(
      expect.objectContaining({ title: "Accept this offer?", confirmButtonText: "Accept offer" }),
    );
  });
  it("records permanent acceptance, refreshes applications and preserves other offers", async () => {
    offers = [
      offerFixture(),
      offerFixture({ id: "other", offerNumber: "OFFER-002", status: "EXPIRED" }),
    ];
    await render();
    await clickButton(wrapper, "Accept offer");
    expect(writes()).toEqual([
      [
        "/api/admissions/offers/offer/response",
        {
          method: "POST",
          body: {
            response: "ACCEPTED",
            notes: "Accepted by applicant through the applicant portal.",
          },
        },
      ],
    ]);
    expect(showSuccess).toHaveBeenCalledWith(
      "Offer accepted",
      "OFFER-001 now records your permanent response.",
    );
    expect(offerCard().text()).not.toContain("Accept offer");
    expect(offerCard("other").text()).toContain("OFFER-002");
    expect(
      context.request.mock.calls.filter(([path]) => path === "/api/admissions/applications/mine"),
    ).toHaveLength(2);
  });
  it.each(["  Changed plans  ", "  ", undefined])(
    "records confirmed decline with optional note %s",
    async (value) => {
      offers = [offerFixture()];
      vi.mocked(Swal.fire).mockResolvedValue({ isConfirmed: true, value } as any);
      await render();
      await clickButton(wrapper, "Decline");
      expect(writes()[0]).toEqual([
        "/api/admissions/offers/offer/response",
        { method: "POST", body: { response: "DECLINED", notes: value?.trim() || null } },
      ]);
      expect(showSuccess).toHaveBeenCalledWith(
        "Offer declined",
        "OFFER-001 now records your permanent response.",
      );
      expect(offerCard().text()).not.toContain("Accept offer");
    },
  );
  it("keeps the offer when decline is cancelled", async () => {
    offers = [offerFixture()];
    vi.mocked(Swal.fire).mockResolvedValue({ isConfirmed: false } as any);
    await render();
    await clickButton(wrapper, "Decline");
    expect(writes()).toHaveLength(0);
    expect(offerCard().text()).toContain("Accept offer");
  });
  it("keeps response actions recoverable after a server conflict", async () => {
    offers = [offerFixture()];
    await render();
    context.request.mockRejectedValueOnce(new Error("Offer amendment published"));
    await clickButton(wrapper, "Accept offer");
    expect(context.showError).toHaveBeenCalledWith(
      "Offer response could not be recorded",
      "Offer amendment published",
    );
    expect(offerCard().text()).toContain("Accept offer");
    expect(showSuccess).not.toHaveBeenCalled();
  });
});
