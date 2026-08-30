// Author: Tinashe K
import { type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import CorrectionsPage from "../../pages/operations/finance-corrections.vue";
import {
  clickButton,
  operationalContext,
  renderOperationalPage,
  setField,
} from "../../../../tests/unit/support/operational-page";
const { fire } = vi.hoisted(() => ({ fire: vi.fn() }));
vi.mock("sweetalert2", () => ({ default: { fire } }));
let context: ReturnType<typeof operationalContext>;
let wrapper: VueWrapper;
let collections: any;
const invoice = {
  id: "invoice",
  invoiceNumber: "INV-001",
  studentNumber: "R260001",
  grossTransactionAmount: 100,
  transactionCurrencyCode: "ZWG",
  lines: [{ id: "line", lineNumber: 1, feeCode: "TUITION", transactionAmount: 100 }],
};
beforeEach(() => {
  vi.resetAllMocks();
  context = operationalContext();
  collections = {
    exchangeRates: [],
    receipts: [],
    creditNotes: [
      {
        id: "credit",
        creditNoteNumber: "CN-001",
        invoiceNumber: "INV-001",
        creditNoteDate: "2026-08-30",
        transactionAmount: 10,
        transactionCurrencyCode: "ZWG",
        baseAmount: 1,
        lines: [{}],
        status: "DRAFT",
        version: 4,
      },
      {
        id: "posted",
        creditNoteNumber: "CN-002",
        invoiceNumber: "INV-002",
        creditNoteDate: "2026-08-30",
        postedAt: "2026-08-30T12:00:00Z",
        transactionAmount: 20,
        transactionCurrencyCode: "USD",
        baseAmount: 20,
        lines: [{}, {}],
        status: "POSTED",
      },
    ],
    payments: [
      {
        id: "payment",
        paymentNumber: "PAY-001",
        payerName: "Payer",
        receiptNumber: "REC-001",
        transactionAmount: 10,
        transactionCurrencyCode: "USD",
        reversed: true,
      },
      { id: "active-payment", reversed: false },
    ],
    allocations: [
      {
        id: "allocation",
        reversalNumber: "REV-001",
        paymentNumber: "PAY-001",
        invoiceNumber: "INV-001",
        transactionAmount: 10,
        transactionCurrencyCode: "USD",
        reversed: true,
      },
      { id: "active-allocation", reversed: false },
    ],
  };
  context.request.mockImplementation(async (path: string) =>
    path.endsWith("/collections")
      ? collections
      : path.endsWith("/billing")
        ? { billingPolicies: [], billingEvents: [], invoices: [invoice] }
        : {},
  );
  fire.mockResolvedValue({ isConfirmed: true, value: " Independent evidence " });
});
afterEach(() => {
  wrapper?.unmount();
  vi.unstubAllGlobals();
});
async function prepare() {
  wrapper = await renderOperationalPage(CorrectionsPage);
  await clickButton(wrapper, "Prepare credit note");
  await setField(wrapper, "Posted invoice", "invoice");
  await setField(wrapper, "Preparation authority", " Approved correction ");
  await setField(wrapper, "Invoice line", "line");
  await setField(wrapper, "ZWG amount", "10");
  await setField(wrapper, "USD amount", "1");
  await setField(wrapper, "Line reason", " Source corrected ");
}

describe("finance correction workspace", () => {
  it("renders immutable credit evidence and separates reversed from active payments and allocations", async () => {
    wrapper = await renderOperationalPage(CorrectionsPage);
    expect(wrapper.text()).toContain("1 linked line");
    expect(wrapper.text()).toContain("2 linked lines");
    expect(
      wrapper.findAll("button").filter((button) => button.text() === "Verify and post"),
    ).toHaveLength(1);
    await clickButton(wrapper, "Allocation reversals");
    expect(wrapper.text()).toContain("REV-001");
    expect(wrapper.text()).toContain("PAY-001 → INV-001");
    await clickButton(wrapper, "Payment reversals");
    expect(wrapper.text()).toContain("Original receipt REC-001");
    expect(wrapper.text()).not.toContain("active-payment");
  });
  it("shows explicit empty states for all three registers", async () => {
    collections.creditNotes = [];
    collections.payments = [];
    collections.allocations = [];
    wrapper = await renderOperationalPage(CorrectionsPage);
    expect(wrapper.text()).toContain("No credit notes have been prepared");
    await clickButton(wrapper, "Allocation reversals");
    expect(wrapper.text()).toContain("No allocation reversals");
    await clickButton(wrapper, "Payment reversals");
    expect(wrapper.text()).toContain("No payment reversals");
  });
  it("creates a credit note with exact original lines, currency amounts and trimmed evidence", async () => {
    await prepare();
    await setField(wrapper, "Credit-note date", "2026-08-29");
    await clickButton(wrapper, "Submit draft credit note");
    expect(context.request).toHaveBeenCalledWith("/api/finance/collections/credit-notes", {
      method: "POST",
      body: {
        invoiceId: "invoice",
        creditNoteDate: "2026-08-29",
        preparationReason: "Approved correction",
        lines: [
          {
            invoiceLineId: "line",
            transactionAmount: 10,
            baseAmount: 1,
            reason: "Source corrected",
          },
        ],
      },
    });
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    expect(context.notify).toHaveBeenCalledWith(
      expect.objectContaining({ title: "Draft credit note submitted" }),
    );
  });
  it("adds and removes lines but retains one and clears them when the invoice changes", async () => {
    await prepare();
    await clickButton(wrapper, "Add line");
    expect(wrapper.findAll('[data-label="Invoice line"]')).toHaveLength(2);
    // The public guided-action event is the contract between the shared control and this page.
    const buttons = wrapper
      .findAll("button")
      .filter((button) => button.attributes("guidance-title") === "Journal line cannot be removed");
    await buttons[1]!.trigger("click");
    expect(wrapper.findAll('[data-label="Invoice line"]')).toHaveLength(1);
    await buttons[0]!.trigger("click");
    expect(wrapper.findAll('[data-label="Invoice line"]')).toHaveLength(1);
    await setField(wrapper, "Posted invoice", "");
    expect(wrapper.findAll('[data-label="Invoice line"]')).toHaveLength(1);
    expect(
      (wrapper.get('[data-label="Transaction amount"] input').element as HTMLInputElement).value,
    ).toBe("0");
    await clickButton(wrapper, "Cancel");
    await clickButton(wrapper, "Prepare credit note");
    expect(
      (wrapper.get('[data-label="Preparation authority"] input').element as HTMLInputElement).value,
    ).toBe("");
  });
  it.each(["invoice", "authority", "line", "transaction", "base", "reason"])(
    "rejects incomplete %s before submitting",
    async (missing) => {
      await prepare();
      const label = {
        invoice: "Posted invoice",
        authority: "Preparation authority",
        line: "Invoice line",
        transaction: "ZWG amount",
        base: "USD amount",
        reason: "Line reason",
      }[missing]!;
      await setField(wrapper, label, missing === "transaction" || missing === "base" ? "0" : "");
      await clickButton(wrapper, "Submit draft credit note");
      expect(context.request.mock.calls.some(([, options]) => options?.method === "POST")).toBe(
        false,
      );
    },
  );
  it.each(["confirm", "cancel", "empty", "missing"])(
    "requires independent posting evidence: %s",
    async (mode) => {
      wrapper = await renderOperationalPage(CorrectionsPage);
      fire.mockResolvedValue({
        isConfirmed: mode !== "cancel",
        value: mode === "missing" ? undefined : mode === "empty" ? " " : " Evidence ",
      });
      await clickButton(wrapper, "Verify and post");
      const validator = fire.mock.calls[0]![0].inputValidator;
      expect(validator(" ")).toContain("required");
      expect(validator("Evidence")).toBeUndefined();
      expect(context.request.mock.calls.some(([path]) => path.endsWith("/post"))).toBe(
        mode === "confirm",
      );
      if (mode === "confirm")
        expect(context.request).toHaveBeenCalledWith(
          "/api/finance/collections/credit-notes/credit/post",
          { method: "POST", body: { reason: "Evidence", expectedVersion: 4 } },
        );
    },
  );
  it.each(["load", "create", "post"])("surfaces %s errors and permits retry", async (action) => {
    if (action === "create") await prepare();
    else {
      if (action === "load") context.request.mockRejectedValueOnce(new Error("Offline"));
      wrapper = await renderOperationalPage(CorrectionsPage);
    }
    if (action !== "load") {
      context.request.mockRejectedValueOnce(new Error("Offline"));
      await clickButton(
        wrapper,
        action === "create" ? "Submit draft credit note" : "Verify and post",
      );
    }
    expect(context.showError).toHaveBeenCalledWith(expect.stringContaining("could not"), "Offline");
    expect(context.notify).not.toHaveBeenCalled();
    if (action === "create") expect(wrapper.find('[role="dialog"]').exists()).toBe(true);
  });
});
