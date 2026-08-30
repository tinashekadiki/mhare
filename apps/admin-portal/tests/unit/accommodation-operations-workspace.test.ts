// Author: Tinashe K
import { flushPromises, type VueWrapper } from "@vue/test-utils";
import { ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Swal from "sweetalert2";
import AccommodationOperationsPage from "../../pages/operations/accommodation-operations.vue";
import {
  clickButton,
  operationalContext,
  setField,
} from "../../../../tests/unit/support/operational-page";
import { mountCampusPage } from "../../../../tests/unit/support/campus-page";

vi.mock("sweetalert2", () => ({ default: { fire: vi.fn() } }));
let context: ReturnType<typeof operationalContext>;
let wrapper: VueWrapper;
let register: ReturnType<typeof operationsFixture>;
const ensureOverview = vi.fn();
const period = {
  id: "period",
  academicPeriodId: "period-current",
  code: "RES1",
  status: "APPLICATION_OPEN",
  occupancyStartsOn: "2026-09-01",
  occupancyEndsOn: "2026-12-01",
};
const application = {
  id: "SUBMITTED",
  applicationPeriodId: "period",
  applicationNumber: "APP-SUBMITTED",
  studentId: "student",
  studentNumber: "R260001A",
  studentName: "Registered Student",
  programmeCode: "BSC",
  paymentState: "PAID",
  priorityScore: 10,
  status: "SUBMITTED",
  version: 5,
};
const rate = {
  id: "DRAFT",
  applicationPeriodId: "period",
  applicationPeriodCode: "RES1",
  roomTypeId: "type",
  roomTypeCode: "SINGLE",
  rateVersion: 1,
  indicativeTransactionAmount: 100,
  transactionCurrencyCode: "USD",
  ratingStatus: "RATED",
  status: "DRAFT",
  version: 6,
};
const allocation = {
  id: "PROPOSED",
  applicationId: "SUBMITTED",
  allocationNumber: "ALLOC-PROPOSED",
  studentNumber: "R260001A",
  studentName: "Registered Student",
  roomCode: "101",
  residenceHallCode: "HALL",
  occupancyStartsOn: "2026-09-01",
  occupancyEndsOn: "2026-12-01",
  billingStatus: "PENDING",
  status: "PROPOSED",
  version: 7,
};
const registration = {
  id: "registration",
  studentId: "student",
  studentNumber: "R260001A",
  studentName: "Registered Student",
  academicPeriodId: "period-current",
  academicPeriodCode: "CURRENT",
  programmeCode: "BSC",
  programmeName: "Science",
  programmePeriodNumber: 5,
  status: "CONFIRMED",
};
function operationsFixture() {
  return {
    rates: [
      rate,
      { ...rate, id: "ACTIVE", status: "ACTIVE" },
      { ...rate, id: "unrated", transactionCurrencyCode: "ZWG", ratingStatus: "UNRATED" },
      { ...rate, id: "retired", status: "RETIRED" },
      {
        ...rate,
        id: "hidden-rate",
        applicationPeriodId: "other-period",
        applicationPeriodCode: "HIDDEN-RATE",
      },
    ],
    applications: [
      application,
      ...["ELIGIBLE", "WAITLISTED", "REJECTED", "ALLOCATED"].map((status) => ({
        ...application,
        id: status,
        applicationNumber: `APP-${status}`,
        status,
        paymentState: status === "REJECTED" ? "UNPAID" : "PART_PAID",
      })),
      {
        ...application,
        id: "hidden-app",
        applicationPeriodId: "other-period",
        applicationNumber: "HIDDEN-APPLICATION",
      },
    ],
    waitlistEntries: [
      {
        id: "waitlist",
        applicationPeriodId: "period",
        applicationNumber: "APP-WAITLISTED",
        applicationPeriodCode: "RES1",
        priorityScore: 20,
        waitlistPosition: 1,
        status: "ACTIVE",
        enteredAt: "2026-08-20T08:00:00Z",
      },
      {
        id: "inactive-waitlist",
        applicationPeriodId: "period",
        applicationNumber: "INACTIVE-WAITLIST",
        status: "ALLOCATED",
        enteredAt: "2026-08-20T08:00:00Z",
      },
      {
        id: "hidden-waitlist",
        applicationPeriodId: "other-period",
        applicationNumber: "HIDDEN-WAITLIST",
      },
    ],
    allocations: [
      allocation,
      ...["ALLOCATED", "CHECKED_IN", "CHECKED_OUT", "CANCELLED"].map((status) => ({
        ...allocation,
        id: status,
        allocationNumber: `ALLOC-${status}`,
        status,
      })),
      {
        ...allocation,
        id: "hidden-allocation",
        applicationId: "hidden-app",
        allocationNumber: "HIDDEN-ALLOCATION",
      },
    ],
    allocationEvents: [
      {
        id: "event",
        allocationId: "PROPOSED",
        allocationNumber: "ALLOC-PROPOSED",
        eventType: "PROPOSED",
        previousStatus: null,
        newStatus: "PROPOSED",
        reason: "Capacity checked",
        occurredAt: "2026-08-20T08:00:00Z",
      },
      {
        id: "updated-event",
        allocationId: "ALLOCATED",
        allocationNumber: "ALLOC-ALLOCATED",
        eventType: "APPROVED",
        previousStatus: "PROPOSED",
        newStatus: "ALLOCATED",
        reason: "Independent approval",
        occurredAt: "2026-08-20T08:00:00Z",
      },
      { id: "hidden-event", allocationId: "hidden-allocation", reason: "HIDDEN-EVENT" },
    ],
  };
}
beforeEach(() => {
  vi.resetAllMocks();
  context = operationalContext();
  register = operationsFixture();
  ensureOverview.mockResolvedValue(undefined);
  vi.stubGlobal("useAcademicSetup", () => ({
    overview: ref({
      programmes: [
        { id: "programme", code: "BSC", name: "Science", status: "ACTIVE" },
        { id: "second-programme", code: "BA", name: "Arts", status: "ACTIVE" },
        { id: "inactive-programme", code: "OLD", name: "Old programme", status: "INACTIVE" },
      ],
    }),
    ensureOverview,
  }));
  vi.mocked(Swal.fire).mockResolvedValue({
    isConfirmed: true,
    value: "  Independent control evidence  ",
  } as any);
  context.request.mockImplementation(async (path: string, options?: { method?: string }) => {
    if (options?.method) return {};
    if (path === "/api/accommodation/operations") return structuredClone(register);
    if (path === "/api/accommodation/setup")
      return {
        premises: [],
        residenceHalls: [],
        roomTypes: [
          { id: "type", code: "SINGLE", name: "Single room", active: true },
          { id: "inactive-type", name: "Old room type", active: false },
        ],
        rooms: [
          {
            id: "room",
            residenceHallCode: "HALL",
            code: "101",
            capacity: 1,
            roomTypeId: "type",
            active: true,
            conditionStatus: "AVAILABLE",
          },
          {
            id: "second-room",
            residenceHallCode: "HALL",
            code: "102",
            capacity: 2,
            roomTypeId: "type",
            active: true,
            conditionStatus: "AVAILABLE",
          },
          { id: "maintenance", code: "MAINTENANCE", active: true, conditionStatus: "MAINTENANCE" },
          { id: "inactive-room", code: "INACTIVE", active: false, conditionStatus: "AVAILABLE" },
        ],
        applicationPeriods: [
          period,
          { ...period, id: "closed-period", code: "CLOSED-PERIOD", status: "CLOSED" },
          { ...period, id: "other-period", code: "OTHER", academicPeriodId: "other" },
        ],
      };
    if (path === "/api/finance/fee-catalogues")
      return {
        catalogues: [
          {
            id: "fee",
            code: "RES",
            name: "Residence fee",
            status: "ACTIVE",
            chargeType: "ACCOMMODATION",
          },
          { id: "other-fee", name: "Other fee", status: "ACTIVE", chargeType: "TUITION" },
          {
            id: "inactive-fee",
            name: "Inactive fee",
            status: "DRAFT",
            chargeType: "ACCOMMODATION",
          },
        ],
      };
    if (path === "/api/finance/collections")
      return {
        exchangeRates: [
          {
            id: "exchange",
            sourceCurrencyCode: "ZWG",
            rateToBase: 0.04,
            effectiveFrom: "2026-08-01T00:00:00Z",
            sourceName: "Treasury",
            status: "ACTIVE",
          },
          {
            id: "inactive-exchange",
            sourceCurrencyCode: "ZWG",
            sourceName: "Expired rate",
            status: "RETIRED",
          },
          {
            id: "eur-exchange",
            sourceCurrencyCode: "EUR",
            sourceName: "Euro rate",
            status: "ACTIVE",
          },
        ],
        payments: [],
        receipts: [],
        allocations: [],
        creditNotes: [],
      };
    if (path === "/api/student-records/registrations")
      return [
        registration,
        {
          ...registration,
          id: "second-registration",
          studentId: "second",
          studentName: "Second Student",
          studentNumber: "R260002A",
          programmeCode: "BA",
          programmeName: "Arts",
          programmePeriodNumber: 2,
        },
        {
          ...registration,
          id: "draft-registration",
          studentName: "Draft Student",
          status: "DRAFT",
        },
        {
          ...registration,
          id: "other-registration",
          studentName: "Other Student",
          academicPeriodId: "other",
        },
      ];
    throw new Error(`Unexpected request ${path}`);
  });
});
afterEach(() => {
  wrapper?.unmount();
  vi.unstubAllGlobals();
});
describe("Accommodation operations workspace", () => {
  it("scopes operational queues and counts to academic-period applications", async () => {
    wrapper = await mountCampusPage(AccommodationOperationsPage);
    expect(wrapper.text()).toContain("Awaiting evaluation: 1");
    expect(wrapper.text()).toContain("Active wait-list: 1");
    expect(wrapper.text()).toContain("Active allocations: 3");
    expect(wrapper.text()).toContain("Checked in: 1");
    expect(wrapper.text()).not.toContain("HIDDEN-APPLICATION");
    for (const [tab, hidden] of [
      ["Wait-list", "HIDDEN-WAITLIST"],
      ["Allocations and occupancy", "HIDDEN-ALLOCATION"],
      ["Rates", "HIDDEN-RATE"],
      ["Workflow history", "HIDDEN-EVENT"],
    ] as const) {
      await clickButton(wrapper, tab);
      expect(wrapper.text()).not.toContain(hidden);
    }
    expect(wrapper.text()).toContain("Independent approval");
    await clickButton(wrapper, "Applications");
    context.selectedAcademicPeriodId.value = null;
    await flushPromises();
    expect(wrapper.text()).toContain("HIDDEN-APPLICATION");
  });
  it.each([false, true])(
    "captures a student application with optional evidence: %s",
    async (optional) => {
      wrapper = await mountCampusPage(AccommodationOperationsPage);
      await clickButton(wrapper, "Submit application");
      await setField(wrapper, "Confirmed student registration", "second-registration");
      expect(wrapper.get('[data-label="Student number"] input').element).toHaveProperty(
        "value",
        "R260002A",
      );
      expect(wrapper.get('[data-label="Year of study"] select').element).toHaveProperty(
        "value",
        "1",
      );
      expect(wrapper.get('[role="dialog"]').text()).not.toContain("Draft Student");
      expect(wrapper.get('[role="dialog"]').text()).not.toContain("Old programme");
      await setField(wrapper, "Primary email", "student@example.test");
      await setField(wrapper, "Gender code", "MALE");
      await setField(wrapper, "Country code", "ZWE");
      await setField(wrapper, "Payment state", "PAID");
      if (optional) {
        await setField(wrapper, "Preferred room type", "type");
        await setField(wrapper, "Disability code", "MOBILITY");
        await setField(wrapper, "Location code", "HARARE");
        await setField(wrapper, "Special requirements", "Ground floor");
      }
      await clickButton(wrapper, "Record");
      const write = context.request.mock.calls.find(
        ([path, options]) =>
          path === "/api/accommodation/operations/applications" && options?.method,
      )!;
      expect(write[1].body).toMatchObject({
        studentId: "second",
        studentNumber: "R260002A",
        programmeId: "second-programme",
        programmeCode: "BA",
        programmeLevel: 1,
        preferredRoomTypeId: optional ? "type" : null,
        disabilityCode: optional ? "MOBILITY" : null,
        locationCode: optional ? "HARARE" : null,
        specialRequirements: optional ? "Ground floor" : null,
        sponsorCode: null,
      });
      expect(write[1].body).not.toHaveProperty("studentRegistrationId");
    },
  );
  it.each(["USD", "ZWG-unrated", "ZWG-rated", "USD-reset"])(
    "prepares a %s rate without inventing an exchange rate",
    async (mode) => {
      wrapper = await mountCampusPage(AccommodationOperationsPage);
      await clickButton(wrapper, "Rates");
      await clickButton(wrapper, "Prepare rate");
      await setField(wrapper, "Version", "2");
      await setField(wrapper, "Transaction amount", "2500");
      await setField(wrapper, "Effective from", "2026-09-01T08:00");
      expect(wrapper.get('[role="dialog"]').text()).not.toContain("Other fee");
      expect(wrapper.get('[role="dialog"]').text()).not.toContain("Inactive fee");
      expect(wrapper.get('[role="dialog"]').text()).not.toContain("CLOSED-PERIOD");
      if (mode !== "USD") await setField(wrapper, "Transaction currency", "ZWG");
      if (mode === "ZWG-rated" || mode === "USD-reset") {
        await setField(wrapper, "Effective Finance exchange rate", "exchange");
        await setField(wrapper, "Effective until", "2026-12-01T18:00");
        expect(
          wrapper.get('[data-label="Indicative USD base amount"] input').element,
        ).toHaveProperty("value", "100");
      }
      if (mode === "USD-reset") await setField(wrapper, "Transaction currency", "USD");
      await clickButton(wrapper, "Record");
      expect(context.request).toHaveBeenCalledWith(
        "/api/accommodation/operations/rates",
        expect.objectContaining({
          body: expect.objectContaining({
            applicationPeriodId: "period",
            roomTypeId: "type",
            financeFeeCatalogueId: "fee",
            rateVersion: 2,
            indicativeTransactionAmount: 2500,
            transactionCurrencyCode: mode.startsWith("USD") ? "USD" : "ZWG",
            exchangeRateId: mode === "ZWG-rated" ? "exchange" : null,
            indicativeBaseAmount: mode === "ZWG-rated" ? 100 : null,
            effectiveFrom: new Date("2026-09-01T08:00").toISOString(),
          }),
        }),
      );
    },
  );
  it.each(["ELIGIBLE", "WAITLISTED", "REJECTED"])(
    "records the %s evaluation against the selected application version",
    async (outcome) => {
      wrapper = await mountCampusPage(AccommodationOperationsPage);
      await clickButton(wrapper, "Evaluate");
      expect(wrapper.get('[role="dialog"]').text()).toContain("APP-SUBMITTED");
      await setField(wrapper, "Outcome", outcome);
      await setField(wrapper, "Priority score", "25");
      await setField(wrapper, "Evaluation evidence", "Documented eligibility assessment");
      await clickButton(wrapper, "Record");
      expect(context.request).toHaveBeenCalledWith(
        "/api/accommodation/operations/applications/SUBMITTED/evaluate",
        {
          method: "POST",
          body: {
            outcome,
            priorityScore: 25,
            selectedGroupId: null,
            reason: "Documented eligibility assessment",
            expectedVersion: 5,
          },
        },
      );
    },
  );
  it("proposes a room allocation with eligible application, available room and rated price", async () => {
    wrapper = await mountCampusPage(AccommodationOperationsPage);
    await clickButton(wrapper, "Allocations and occupancy");
    await clickButton(wrapper, "Propose allocation");
    expect(wrapper.get('[role="dialog"]').text()).toContain("Maker-checker proposal");
    expect(wrapper.get('[role="dialog"]').text()).not.toContain("APP-REJECTED");
    expect(wrapper.get('[role="dialog"]').text()).not.toContain("MAINTENANCE");
    await setField(wrapper, "Eligible or waitlisted application", "WAITLISTED");
    await setField(wrapper, "Available room", "second-room");
    await setField(wrapper, "Proposal evidence", "Capacity and preference verified");
    expect(wrapper.get('[data-label="Occupancy starts"] input').element).toHaveProperty(
      "value",
      "2026-09-01",
    );
    await clickButton(wrapper, "Record");
    expect(context.request).toHaveBeenCalledWith("/api/accommodation/operations/allocations", {
      method: "POST",
      body: {
        applicationId: "WAITLISTED",
        roomId: "second-room",
        accommodationRateId: "ACTIVE",
        occupancyStartsOn: "2026-09-01",
        occupancyEndsOn: "2026-12-01",
        reason: "Capacity and preference verified",
      },
    });
  });
  it.each([
    ["Approve", "PROPOSED", "approve"],
    ["Cancel", "PROPOSED", "cancel"],
    ["Check in", "ALLOCATED", "check-in"],
    ["Check out", "CHECKED_IN", "check-out"],
    ["Withdraw", "ALLOCATED", "withdraw"],
  ] as const)(
    "records the %s occupancy transition and trimmed evidence",
    async (action, id, route) => {
      wrapper = await mountCampusPage(AccommodationOperationsPage);
      await clickButton(wrapper, "Allocations and occupancy");
      await clickButton(wrapper, action);
      expect(context.request).toHaveBeenCalledWith(
        `/api/accommodation/operations/allocations/${id}/${route}`,
        { method: "POST", body: { reason: "Independent control evidence", expectedVersion: 7 } },
      );
      expect(wrapper.get('[data-record="CANCELLED"]').findAll("button")).toHaveLength(0);
      const validator = (vi.mocked(Swal.fire).mock.calls[0]?.[0] as any).inputValidator;
      expect(validator(" ")).toBe("Complete control evidence is required.");
      expect(validator("Valid")).toBeUndefined();
    },
  );
  it.each([
    ["Activate", "DRAFT", "ACTIVE"],
    ["Retire", "ACTIVE", "RETIRED"],
  ] as const)("%s controls the current rate version", async (action, id, targetStatus) => {
    wrapper = await mountCampusPage(AccommodationOperationsPage);
    await clickButton(wrapper, "Rates");
    await clickButton(wrapper, action);
    expect(context.request).toHaveBeenCalledWith(
      `/api/accommodation/operations/rates/${id}/transition`,
      {
        method: "POST",
        body: { targetStatus, reason: "Independent control evidence", expectedVersion: 6 },
      },
    );
  });
  it.each([{ isConfirmed: false }, { isConfirmed: true, value: " " }, { isConfirmed: true }])(
    "does not progress a rate without confirmation/evidence: %j",
    async (result) => {
      vi.mocked(Swal.fire).mockResolvedValue(result as any);
      wrapper = await mountCampusPage(AccommodationOperationsPage);
      await clickButton(wrapper, "Rates");
      await clickButton(wrapper, "Activate");
      await clickButton(wrapper, "Allocations and occupancy");
      await clickButton(wrapper, "Approve");
      expect(context.request.mock.calls.filter(([, options]) => options?.method)).toEqual([]);
    },
  );
  it("keeps failed application input editable and reports controlled-action failure", async () => {
    wrapper = await mountCampusPage(AccommodationOperationsPage);
    context.request.mockRejectedValue(new Error("Conflict"));
    await clickButton(wrapper, "Submit application");
    await clickButton(wrapper, "Record");
    expect(context.showError).toHaveBeenCalledWith("Application could not be recorded", "Conflict");
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true);
    await clickButton(wrapper, "Cancel");
    await clickButton(wrapper, "Rates");
    await clickButton(wrapper, "Activate");
    expect(context.showError).toHaveBeenCalledWith(
      "Controlled action could not be completed",
      "Conflict",
    );
  });
  it.each(["/api/accommodation/operations", "/api/accommodation/setup"])(
    "reports required dependency failure at %s and recovers",
    async (failedPath) => {
      const original = context.request.getMockImplementation()!;
      let failed = true;
      context.request.mockImplementation((path, ...args) =>
        path === failedPath && failed
          ? Promise.reject(new Error("Required data offline"))
          : original(path, ...args),
      );
      wrapper = await mountCampusPage(AccommodationOperationsPage);
      expect(wrapper.text()).toContain("Required data offline");
      failed = false;
      await clickButton(wrapper, "Refresh");
      expect(wrapper.text()).not.toContain("Required data offline");
      expect(wrapper.text()).toContain("APP-SUBMITTED");
    },
  );
  it("retains existing operations when optional finance, registration and academic services fail", async () => {
    const original = context.request.getMockImplementation()!;
    context.request.mockImplementation((path, ...args) =>
      path.startsWith("/api/accommodation/")
        ? original(path, ...args)
        : Promise.reject(new Error("Offline")),
    );
    ensureOverview.mockRejectedValue(new Error("Academic offline"));
    wrapper = await mountCampusPage(AccommodationOperationsPage);
    expect(wrapper.text()).toContain("APP-SUBMITTED");
    expect(wrapper.text()).toContain("Finance fee catalogues are unavailable");
    expect(wrapper.text()).toContain("Exchange rates are unavailable");
    expect(wrapper.text()).toContain("Confirmed registrations are unavailable");
    expect(wrapper.text()).toContain("Academic programme details are unavailable");
  });
});
