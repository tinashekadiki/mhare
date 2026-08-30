// Author: Tinashe K
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { defineComponent } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import CollectionsPage from "../../pages/operations/finance-collections.vue";
import type {
  FinanceCollectionsRegister,
  FinanceStudentPaymentSummary,
  FinanceExchangeRateSummary,
  FinancePaymentAllocationSummary,
  FinanceInvoiceSummary,
} from "../../../../packages/portal-shell/types/finance";
import {
  clickButton,
  operationalContext,
  setField,
} from "../../../../tests/unit/support/operational-page";
import { registerStubs } from "../../../../tests/unit/support/register-page";
const { confirm } = vi.hoisted(() => ({ confirm: vi.fn() }));
vi.mock("sweetalert2", () => ({ default: { fire: confirm } }));
const Dropdown = defineComponent({
  props: ["items"],
  template: `<div><slot/><template v-for="group in items"><button v-for="item in group" @click="item.onSelect()">{{item.label}}</button></template></div>`,
});
let wrapper: VueWrapper;
let context: ReturnType<typeof operationalContext>;
let register: FinanceCollectionsRegister;
let invoices: FinanceInvoiceSummary[];
let failPath: string | undefined;
const stamp = "2026-08-01T10:00:00Z";
const payment = (
  overrides: Partial<FinanceStudentPaymentSummary> = {},
): FinanceStudentPaymentSummary => ({
  id: "payment",
  paymentNumber: "PAY-001",
  studentFinanceAccountId: "account",
  accountNumber: "ACC-001",
  payerName: "Student",
  providerCode: "BANK",
  providerTransactionReference: "TXN-001",
  paymentChannel: "BANK_TRANSFER",
  transactionCurrencyCode: "USD",
  transactionAmount: 100,
  baseCurrencyCode: "USD",
  baseAmount: 100,
  ratingStatus: "RATED",
  paidAt: stamp,
  reconciliationStatus: "PENDING",
  capturedByUserId: "maker",
  capturedAt: stamp,
  inSuspense: false,
  reversed: false,
  version: 7,
  ...overrides,
});
const rate = (overrides: Partial<FinanceExchangeRateSummary> = {}): FinanceExchangeRateSummary => ({
  id: "rate",
  sourceCurrencyCode: "ZWG",
  baseCurrencyCode: "USD",
  rateToBase: 0.04,
  effectiveFrom: stamp,
  effectiveTo: null,
  sourceName: "RBZ",
  sourceReference: null,
  status: "DRAFT",
  preparedByUserId: "treasury",
  version: 3,
  ...overrides,
});
const allocation: FinancePaymentAllocationSummary = {
  id: "allocation",
  allocationNumber: "ALLOC-001",
  paymentId: "payment",
  paymentNumber: "PAY-001",
  invoiceId: "invoice",
  invoiceNumber: "INV-001",
  transactionCurrencyCode: "USD",
  transactionAmount: 50,
  paymentBaseAmount: 50,
  invoiceBaseAmount: 48,
  realisedExchangeDifference: 2,
  allocatedByUserId: "checker",
  allocatedAt: stamp,
  reversed: false,
  version: 4,
};
const invoice: FinanceInvoiceSummary = {
  id: "invoice",
  invoiceNumber: "INV-001",
  studentFinanceAccountId: "account",
  accountNumber: "ACC-001",
  studentId: "student",
  studentNumber: "R260001",
  transactionCurrencyCode: "USD",
  baseCurrencyCode: "USD",
  grossTransactionAmount: 100,
  transactionDiscountAmount: 0,
  netTransactionAmount: 100,
  grossBaseAmount: 100,
  baseDiscountAmount: 0,
  netBaseAmount: 100,
  invoiceDate: "2026-08-01",
  dueDate: "2026-08-31",
  status: "POSTED",
  postedByUserId: "checker",
  postedAt: stamp,
  version: 1,
  lines: [],
};
beforeEach(() => {
  vi.resetAllMocks();
  context = operationalContext();
  confirm.mockResolvedValue({ isConfirmed: true, value: "  Independently verified  " });
  failPath = undefined;
  register = {
    payments: [payment()],
    exchangeRates: [rate()],
    allocations: [allocation],
    receipts: [],
    creditNotes: [],
  };
  invoices = [invoice];
  context.request.mockImplementation(async (path: string, options?: { method?: string }) => {
    if (path === failPath) throw new Error("Unavailable");
    if (options?.method) return {};
    if (path === "/api/finance/collections") return structuredClone(register);
    if (path === "/api/finance/billing")
      return { billingPolicies: [], billingEvents: [], invoices: structuredClone(invoices) };
    if (path === "/api/finance/collections/accounts")
      return [{ id: "account", studentNumber: "R260001", accountNumber: "ACC-001" }];
    throw new Error(`Unexpected ${path}`);
  });
});
afterEach(() => {
  wrapper?.unmount();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
async function render() {
  wrapper = mount(CollectionsPage, {
    global: { stubs: { ...registerStubs, UDropdownMenu: Dropdown } },
  });
  await flushPromises();
}
const writes = () => context.request.mock.calls.filter(([, options]) => options?.method);
async function fillPayment() {
  await clickButton(wrapper, "Capture payment");
  for (const [label, value] of [
    ["Payer name", "Student"],
    ["Provider code", "BANK"],
    ["Provider transaction reference", "BANK-123"],
    ["Transaction amount", "100"],
    ["Provider paid timestamp", "2026-08-01T12:00"],
    ["Provider event fingerprint", "fingerprint-123"],
  ] as const)
    await setField(wrapper, label, value);
}
describe("cash collection workflow", () => {
  it("keeps unrated, pending, suspense, reconciled and reversed queues distinct", async () => {
    register.payments = [
      payment(),
      payment({
        id: "unrated",
        paymentNumber: "UNRATED-001",
        transactionCurrencyCode: "ZWG",
        ratingStatus: "UNRATED",
        baseAmount: null,
      }),
      payment({
        id: "suspense",
        paymentNumber: "SUSPENSE-001",
        reconciliationStatus: "RECONCILED",
        inSuspense: true,
        studentFinanceAccountId: null,
      }),
      payment({
        id: "reconciled",
        paymentNumber: "RECONCILED-001",
        reconciliationStatus: "RECONCILED",
        receiptNumber: "REC-001",
      }),
      payment({
        id: "reversed",
        paymentNumber: "REVERSED-001",
        reconciliationStatus: "RECONCILED",
        reversed: true,
      }),
      payment({ id: "rejected", paymentNumber: "REJECTED-001", reconciliationStatus: "REJECTED" }),
    ];
    await render();
    expect(wrapper.text()).toContain("Unrated");
    expect(wrapper.text()).toContain("REC-001");
    for (const [filter, numbers] of [
      ["UNRATED", ["UNRATED-001"]],
      ["PENDING", ["PAY-001"]],
      ["SUSPENSE", ["SUSPENSE-001"]],
      ["RECONCILED", ["SUSPENSE-001", "RECONCILED-001"]],
      ["REVERSED", ["REVERSED-001"]],
    ] as const) {
      await wrapper.get("select").setValue(filter);
      const body = wrapper.get("tbody");
      expect(body.findAll("tr")).toHaveLength(numbers.length);
      for (const number of numbers) expect(body.text()).toContain(number);
    }
    await wrapper.get("select").setValue("ALL");
    expect(wrapper.get("tbody").findAll("tr")).toHaveLength(6);
  });
  it("shows empty queues and recovers a failed data dependency on refresh", async () => {
    failPath = "/api/finance/billing";
    await render();
    expect(context.showError).toHaveBeenCalledWith(
      "Collections workspace could not be loaded",
      "Unavailable",
    );
    expect(wrapper.text()).toContain("No payments match");
    register.exchangeRates = [];
    register.allocations = [];
    failPath = undefined;
    await clickButton(wrapper, "Refresh");
    expect(wrapper.text()).toContain("PAY-001");
    await clickButton(wrapper, "Exchange rates");
    expect(wrapper.text()).toContain("No foreign-currency rates captured");
    await clickButton(wrapper, "Allocations");
    expect(wrapper.text()).toContain("No payment allocations recorded");
  });
  it.each([false, true])(
    "captures an effective rate with optional ending/reference %s",
    async (optional) => {
      await render();
      await clickButton(wrapper, "Exchange rates");
      await clickButton(wrapper, "New exchange rate");
      await clickButton(wrapper, "Create draft rate");
      expect(writes()).toHaveLength(0);
      await setField(wrapper, "USD per source unit", "0.04");
      await clickButton(wrapper, "Create draft rate");
      expect(writes()).toHaveLength(0);
      await setField(wrapper, "Effective from", "2026-08-01T12:00");
      await setField(wrapper, "Published source", " ");
      await clickButton(wrapper, "Create draft rate");
      expect(writes()).toHaveLength(0);
      await setField(wrapper, "Published source", " RBZ bulletin ");
      if (optional) {
        await setField(wrapper, "Effective until", "2026-09-01T12:00");
        await setField(wrapper, "Source reference", " circular-123 ");
      }
      await clickButton(wrapper, "Create draft rate");
      expect(writes()[0]!).toEqual([
        "/api/finance/collections/exchange-rates",
        {
          method: "POST",
          body: {
            sourceCurrencyCode: "ZWG",
            rateToBase: 0.04,
            effectiveFrom: new Date("2026-08-01T12:00").toISOString(),
            effectiveTo: optional ? new Date("2026-09-01T12:00").toISOString() : null,
            sourceName: "RBZ bulletin",
            sourceReference: optional ? "circular-123" : null,
          },
        },
      ]);
      expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    },
  );
  it.each([
    "Payer name",
    "Provider code",
    "Provider transaction reference",
    "Transaction amount",
    "Provider paid timestamp",
    "Provider event fingerprint",
  ])("does not capture evidence with missing %s", async (label) => {
    await render();
    await fillPayment();
    await setField(wrapper, label, label === "Transaction amount" ? "0" : "");
    await clickButton(wrapper, "Capture immutable evidence");
    expect(writes()).toHaveLength(0);
  });
  it.each([false, true])(
    "captures provider evidence with %s account association without supplying a fallback rate",
    async (linked) => {
      await render();
      await fillPayment();
      if (linked) await setField(wrapper, "Student finance account", "account");
      await setField(wrapper, "Transaction currency", "ZWG");
      await setField(wrapper, "Payment channel", "MOBILE_MONEY");
      expect(wrapper.text()).toContain("No fallback exchange rate");
      await clickButton(wrapper, "Capture immutable evidence");
      expect(writes()[0]!).toEqual([
        "/api/finance/collections/payments",
        {
          method: "POST",
          body: {
            studentFinanceAccountId: linked ? "account" : null,
            payerName: "Student",
            providerCode: "BANK",
            providerTransactionReference: "BANK-123",
            transactionAmount: 100,
            transactionCurrencyCode: "ZWG",
            paymentChannel: "MOBILE_MONEY",
            paidAt: new Date("2026-08-01T12:00").toISOString(),
            providerEventFingerprint: "fingerprint-123",
          },
        },
      ]);
    },
  );
  it("keeps a failed capture for correction and resets on a fresh capture", async () => {
    await render();
    await fillPayment();
    failPath = "/api/finance/collections/payments";
    await clickButton(wrapper, "Capture immutable evidence");
    expect(context.showError).toHaveBeenCalledWith(
      "Collections operation could not be completed",
      "Unavailable",
    );
    expect(wrapper.get('[data-label="Payer name"] input').element).toHaveProperty(
      "value",
      "Student",
    );
    await clickButton(wrapper, "Cancel");
    await clickButton(wrapper, "Capture payment");
    expect(wrapper.get('[data-label="Payer name"] input').element).toHaveProperty("value", "");
  });
  it.each([
    ["Approve", "approve", "DRAFT"],
    ["Retire", "retire", "ACTIVE"],
  ] as const)("requires independent evidence to %s a rate", async (button, action, status) => {
    register.exchangeRates = [
      rate({ status, sourceReference: "RBZ-2026", effectiveTo: stamp }),
      rate({ id: "retired", status: "RETIRED" }),
    ];
    await render();
    await clickButton(wrapper, "Exchange rates");
    confirm.mockResolvedValueOnce({ isConfirmed: false });
    await clickButton(wrapper, button);
    confirm.mockResolvedValueOnce({ isConfirmed: true, value: " " });
    await clickButton(wrapper, button);
    expect(writes()).toHaveLength(0);
    expect(confirm.mock.calls[0]![0].inputValidator(" ")).toBeTruthy();
    expect(confirm.mock.calls[0]![0].inputValidator("Audited")).toBeUndefined();
    await clickButton(wrapper, button);
    expect(writes()[0]!).toEqual([
      `/api/finance/collections/exchange-rates/rate/${action}`,
      { method: "POST", body: { reason: "Independently verified", expectedVersion: 3 } },
    ]);
  });
  it("rates only after confirmation using the backend effective-rate selection and persisted version", async () => {
    register.payments = [
      payment({ ratingStatus: "UNRATED", baseAmount: null, transactionCurrencyCode: "ZWG" }),
    ];
    await render();
    expect(wrapper.findAll("button").some((button) => button.text() === "Reconcile payment")).toBe(
      false,
    );
    confirm.mockResolvedValueOnce({ isConfirmed: false });
    await clickButton(wrapper, "Apply rate");
    expect(writes()).toHaveLength(0);
    await clickButton(wrapper, "Apply rate");
    expect(writes()[0]!).toEqual([
      "/api/finance/collections/payments/payment/apply-rate?expectedVersion=7",
      { method: "POST" },
    ]);
  });
  it.each([
    ["Reconcile payment", "reconcile"],
    ["Reject evidence", "reject"],
  ])(
    "controls %s with version, authority and cancelled/empty decisions",
    async (button, action) => {
      await render();
      confirm.mockResolvedValueOnce({ isConfirmed: false });
      await clickButton(wrapper, button);
      confirm.mockResolvedValueOnce({ isConfirmed: true, value: "" });
      await clickButton(wrapper, button);
      expect(writes()).toHaveLength(0);
      const options = confirm.mock.calls[0]![0];
      expect(options.inputValidator(" ")).toBeTruthy();
      expect(options.inputValidator("Matched bank evidence")).toBeUndefined();
      await clickButton(wrapper, button);
      expect(writes()[0]!).toEqual([
        `/api/finance/collections/payments/payment/${action}`,
        { method: "POST", body: { reason: "Independently verified", expectedVersion: 7 } },
      ]);
    },
  );
  it("requires an account and evidence before resolving a suspense payment", async () => {
    register.payments = [
      payment({
        reconciliationStatus: "RECONCILED",
        inSuspense: true,
        studentFinanceAccountId: null,
      }),
    ];
    await render();
    await clickButton(wrapper, "Resolve suspense");
    await clickButton(wrapper, "Assign and issue receipt");
    expect(writes()).toHaveLength(0);
    await setField(wrapper, "Student finance account", "account");
    await clickButton(wrapper, "Assign and issue receipt");
    expect(writes()).toHaveLength(0);
    await setField(wrapper, "Matching evidence", "  Bank payer independently matched  ");
    await clickButton(wrapper, "Assign and issue receipt");
    expect(writes()[0]!).toEqual([
      "/api/finance/collections/payments/payment/resolve-suspense",
      {
        method: "POST",
        body: {
          studentFinanceAccountId: "account",
          reason: "Bank payer independently matched",
          expectedPaymentVersion: 7,
        },
      },
    ]);
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
  });
  it("only offers invoices with the same account and currency and requires a positive allocation with evidence", async () => {
    register.payments = [payment({ reconciliationStatus: "RECONCILED" })];
    invoices.push(
      {
        ...invoice,
        id: "other-account",
        invoiceNumber: "OTHER-ACCOUNT",
        studentFinanceAccountId: "other",
      },
      {
        ...invoice,
        id: "other-currency",
        invoiceNumber: "OTHER-CURRENCY",
        transactionCurrencyCode: "ZWG",
      },
    );
    await render();
    await clickButton(wrapper, "Allocate to invoice");
    expect(wrapper.get('[data-label="Matching posted invoice"] select').text()).toContain(
      "INV-001",
    );
    expect(wrapper.get('[data-label="Matching posted invoice"] select').text()).not.toContain(
      "OTHER-",
    );
    await clickButton(wrapper, "Allocate payment");
    await setField(wrapper, "Matching posted invoice", "invoice");
    await clickButton(wrapper, "Allocate payment");
    await setField(wrapper, "Transaction amount", "30");
    await clickButton(wrapper, "Allocate payment");
    expect(writes()).toHaveLength(0);
    await setField(wrapper, "Allocation evidence", "  Tuition settlement  ");
    await clickButton(wrapper, "Allocate payment");
    expect(writes()[0]!).toEqual([
      "/api/finance/collections/payments/payment/allocations",
      {
        method: "POST",
        body: {
          invoiceId: "invoice",
          transactionAmount: 30,
          reason: "Tuition settlement",
          expectedPaymentVersion: 7,
        },
      },
    ]);
  });
  it.each(["payment", "allocation"])(
    "requires authority before reversing %s and retains failures for recovery",
    async (kind) => {
      register.payments = [payment({ reconciliationStatus: "RECONCILED" })];
      register.allocations.push({
        ...allocation,
        id: "reversed",
        allocationNumber: "ALLOC-002",
        reversed: true,
        realisedExchangeDifference: 0,
      });
      await render();
      if (kind === "allocation") {
        await clickButton(wrapper, "Allocations");
        expect(wrapper.text()).toContain("Realised FX");
        expect(
          wrapper.findAll("button").filter((button) => button.text() === "Reverse"),
        ).toHaveLength(1);
      }
      const button = kind === "payment" ? "Reverse payment" : "Reverse";
      confirm.mockResolvedValueOnce({ isConfirmed: false });
      await clickButton(wrapper, button);
      confirm.mockResolvedValueOnce({ isConfirmed: true, value: " " });
      await clickButton(wrapper, button);
      expect(writes()).toHaveLength(0);
      const options = confirm.mock.calls[0]![0];
      expect(options.inputValidator("")).toBeTruthy();
      expect(options.inputValidator("Authorised")).toBeUndefined();
      const path =
        kind === "payment"
          ? "/api/finance/collections/payments/payment/reverse"
          : "/api/finance/collections/allocations/allocation/reverse";
      failPath = path;
      await clickButton(wrapper, button);
      expect(context.showError).toHaveBeenCalledWith(
        "Collections operation could not be completed",
        "Unavailable",
      );
      failPath = undefined;
      await clickButton(wrapper, button);
      expect(writes()[1]!).toEqual([
        path,
        {
          method: "POST",
          body: { reason: "Independently verified", expectedVersion: kind === "payment" ? 7 : 4 },
        },
      ]);
    },
  );
  it.each([
    ["Exchange rates", "New exchange rate"],
    ["Payments", "Capture payment"],
    ["Payments", "Resolve suspense"],
    ["Payments", "Allocate to invoice"],
  ])("cancels %s %s without writes", async (tab, button) => {
    register.payments = [
      payment({ reconciliationStatus: "RECONCILED", inSuspense: button === "Resolve suspense" }),
    ];
    await render();
    await clickButton(wrapper, tab);
    await clickButton(wrapper, button);
    await clickButton(wrapper, "Cancel");
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    expect(writes()).toHaveLength(0);
  });
});
