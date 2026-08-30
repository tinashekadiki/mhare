// Author: Tinashe K
import { flushPromises, type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Swal from "sweetalert2";
import Billing from "../../pages/operations/finance-billing.vue";
import {
  clickButton,
  operationalContext,
  renderOperationalPage,
  setField,
} from "../../../../tests/unit/support/operational-page";

vi.mock("sweetalert2", () => ({ default: { fire: vi.fn() } }));
const fire = vi.mocked(Swal.fire);
let context: ReturnType<typeof operationalContext>;
let wrapper: VueWrapper;
const event = (id: string, status = "APPROVED", account = "account", currency = "USD") => ({
  id,
  status,
  studentFinanceAccountId: account,
  transactionCurrencyCode: currency,
  transactionAmount: 100,
  baseAmount: 100,
  eventNumber: `EV-${id}`,
  description: "Tuition charge",
  sourceService: "STUDENT_RECORDS",
  sourceLineReference: id,
  studentNumber: "R2026",
  accountNumber: account,
  feeCode: "TUITION",
  feeRuleVersion: 2,
  quantity: 1,
  version: 3,
  effectiveAt: "2026-08-01T00:00:00Z",
});
const policy = (status: string) => ({
  id: status,
  status,
  code: `TUITION-${status}`,
  name: "Tuition billing",
  policyVersion: 1,
  lineBasis: "REGISTRATION",
  quantityBasis: status === "ACTIVE" ? "MODULE_CREDIT" : "FIXED",
  fixedQuantity: status === "ACTIVE" ? null : 1,
  feeCode: "TUITION",
  effectiveFrom: "2026-08-01T00:00:00Z",
  version: 4,
});
let events: ReturnType<typeof event>[];
let policies: ReturnType<typeof policy>[];
let invoices: Record<string, unknown>[];
let catalogues: Record<string, unknown>[];
beforeEach(() => {
  vi.resetAllMocks();
  context = operationalContext();
  events = [
    event("pending", "PENDING_APPROVAL"),
    event("first"),
    event("second"),
    event("foreign", "APPROVED", "other"),
    event("zwg", "APPROVED", "account", "ZWG"),
    event("invoiced", "INVOICED"),
    event("rejected", "REJECTED"),
  ];
  policies = ["DRAFT", "ACTIVE", "RETIRED"].map(policy);
  invoices = [1, 2].map((count) => ({
    id: `invoice-${count}`,
    invoiceNumber: `INV-${count}`,
    studentNumber: "R2026",
    accountNumber: "account",
    lines: Array.from({ length: count }, () => ({})),
    dueDate: "2026-09-01",
    grossTransactionAmount: count * 100,
    grossBaseAmount: count * 100,
    transactionCurrencyCode: "USD",
  }));
  catalogues = [
    { id: "fee", code: "TUITION", name: "Tuition", status: "ACTIVE" },
    { id: "retired", code: "OLD", status: "RETIRED" },
  ];
  context.request.mockImplementation(async (path: string, options?: { method?: string }) => {
    if (options?.method) return {};
    return path === "/api/finance/billing"
      ? { billingEvents: events, billingPolicies: policies, invoices }
      : { catalogues };
  });
  fire.mockResolvedValue({ isConfirmed: true, value: "  Checked source evidence  " } as never);
});
afterEach(() => {
  wrapper?.unmount();
  vi.unstubAllGlobals();
});
async function render() {
  wrapper = await renderOperationalPage(Billing);
  await flushPromises();
}
async function selectEvent(id: string) {
  await wrapper.get(`[aria-label="Select EV-${id}"] input`).setValue(true);
}
async function fillPolicy() {
  await setField(wrapper, "Policy code", " REG ");
  await setField(wrapper, "Policy name", " Registration ");
  await setField(wrapper, "Effective from", "2026-09-01T08:00");
}
const transitions = [
  {
    tab: "Billing events",
    label: "Approve",
    id: "pending",
    resource: "events",
    action: "approve",
    version: 3,
  },
  {
    tab: "Billing events",
    label: "Reject",
    id: "pending",
    resource: "events",
    action: "reject",
    version: 3,
  },
  {
    tab: "Billing policies",
    label: "Activate",
    id: "DRAFT",
    resource: "policies",
    action: "activate",
    version: 4,
  },
  {
    tab: "Billing policies",
    label: "Retire",
    id: "ACTIVE",
    resource: "policies",
    action: "retire",
    version: 4,
  },
];

describe("Finance billing workspace", () => {
  it("renders governed event states, policy versions and singular/plural invoice evidence", async () => {
    await render();
    expect(wrapper.text()).toContain("Pending Approval");
    expect(wrapper.text()).toContain("Rejected");
    expect(wrapper.findAll('input[type="checkbox"]')).toHaveLength(4);
    await clickButton(wrapper, "Billing policies");
    expect(wrapper.text()).toContain("TUITION-DRAFT · v1");
    expect(wrapper.text()).toContain("Module Credit");
    await clickButton(wrapper, "Invoices");
    expect(wrapper.text()).toContain("1 line ·");
    expect(wrapper.text()).toContain("2 lines ·");
  });
  it.each(transitions)(
    "posts $action to the owning endpoint with version and reason",
    async (scenario) => {
      await render();
      await clickButton(wrapper, scenario.tab);
      await clickButton(wrapper, scenario.label);
      expect(context.request).toHaveBeenCalledWith(
        `/api/finance/billing/${scenario.resource}/${scenario.id}/${scenario.action}`,
        {
          method: "POST",
          body: { reason: "Checked source evidence", expectedVersion: scenario.version },
        },
      );
      const validator = (
        fire.mock.calls[0]![0] as unknown as { inputValidator: (value: string) => unknown }
      ).inputValidator;
      expect(validator(" ")).toBe("A complete reason is required.");
      expect(validator("Checked")).toBeUndefined();
      expect(context.notify).toHaveBeenCalled();
    },
  );
  it.each([
    { isConfirmed: false, value: "Reason" },
    { isConfirmed: true, value: "" },
    { isConfirmed: true, value: "  " },
    { isConfirmed: true },
  ])("does not mutate either workflow without confirmation and reason: %j", async (result) => {
    fire.mockResolvedValue(result as never);
    await render();
    await clickButton(wrapper, "Approve");
    await clickButton(wrapper, "Billing policies");
    await clickButton(wrapper, "Activate");
    expect(context.request.mock.calls.some(([, options]) => options?.method)).toBe(false);
  });
  it("requires a nonempty compatible account and currency selection, and toggles events off", async () => {
    await render();
    await clickButton(wrapper, "Post selected invoice");
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    await selectEvent("first");
    await selectEvent("foreign");
    expect(wrapper.text()).toContain("Selection cannot be posted together");
    await clickButton(wrapper, "Post selected invoice");
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    await wrapper.get('[aria-label="Select EV-foreign"] input').setValue(false);
    await selectEvent("zwg");
    expect(wrapper.text()).toContain("Selection cannot be posted together");
    await wrapper.get('[aria-label="Select EV-zwg"] input').setValue(false);
    await selectEvent("second");
    expect(wrapper.text()).toContain("2 selected");
    expect(wrapper.text()).not.toContain("Selection cannot be posted together");
    await clickButton(wrapper, "Post selected invoice");
    await clickButton(wrapper, "Post immutable invoice");
    expect(context.request).not.toHaveBeenCalledWith(
      "/api/finance/billing/invoices",
      expect.anything(),
    );
    await setField(wrapper, "Posting evidence", "  Batch control 200  ");
    await setField(wrapper, "Invoice date", "2026-08-30");
    await setField(wrapper, "Due date", "2026-09-30");
    await clickButton(wrapper, "Post immutable invoice");
    expect(context.request).toHaveBeenCalledWith("/api/finance/billing/invoices", {
      method: "POST",
      body: {
        billingEventIds: ["first", "second"],
        invoiceDate: "2026-08-30",
        dueDate: "2026-09-30",
        postingReason: "Batch control 200",
      },
    });
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain("2 selected");
  });
  it("drops stale selection when an event is no longer approved", async () => {
    await render();
    await selectEvent("first");
    events = events.map((item) => (item.id === "first" ? { ...item, status: "INVOICED" } : item));
    await clickButton(wrapper, "Refresh");
    expect(wrapper.text()).not.toContain("1 selected");
    await clickButton(wrapper, "Post selected invoice");
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
  });
  it.each(["FIXED", "MODULE_CREDIT"])(
    "creates a %s policy with correct quantity and effectivity",
    async (basis) => {
      await render();
      await clickButton(wrapper, "Billing policies");
      await clickButton(wrapper, "Create billing policy");
      await fillPolicy();
      expect(
        wrapper
          .get('[data-label="Fee definition"] select')
          .findAll("option")
          .map((option) => option.attributes("value")),
      ).toEqual(["", "fee"]);
      await setField(wrapper, "Quantity basis", basis);
      if (basis === "FIXED") await setField(wrapper, "Fixed quantity", "2");
      else {
        await setField(wrapper, "Line basis", "REGISTERED_MODULE");
        await setField(wrapper, "Effective until", "2027-09-01T08:00");
        expect(wrapper.find('[data-label="Fixed quantity"]').exists()).toBe(false);
      }
      await clickButton(wrapper, "Create draft policy");
      expect(context.request).toHaveBeenCalledWith("/api/finance/billing/policies", {
        method: "POST",
        body: expect.objectContaining({
          code: "REG",
          name: "Registration",
          feeCatalogueId: "fee",
          quantityBasis: basis,
          fixedQuantity: basis === "FIXED" ? 2 : null,
          effectiveFrom: new Date("2026-09-01T08:00").toISOString(),
          effectiveUntil: basis === "FIXED" ? null : new Date("2027-09-01T08:00").toISOString(),
        }),
      });
    },
  );
  it.each(["Policy code", "Policy name", "Effective from", "Fee definition"])(
    "blocks policy creation without %s",
    async (field) => {
      if (field === "Fee definition") catalogues = [];
      await render();
      await clickButton(wrapper, "Billing policies");
      await clickButton(wrapper, "Create billing policy");
      await fillPolicy();
      if (field !== "Fee definition") await setField(wrapper, field, "");
      await clickButton(wrapper, "Create draft policy");
      expect(context.request).not.toHaveBeenCalledWith(
        "/api/finance/billing/policies",
        expect.objectContaining({ method: "POST" }),
      );
    },
  );
  it("resets cancelled policy and invoice drafts", async () => {
    await render();
    await selectEvent("first");
    await clickButton(wrapper, "Post selected invoice");
    await setField(wrapper, "Posting evidence", "Cancelled");
    await clickButton(wrapper, "Cancel");
    await clickButton(wrapper, "Post selected invoice");
    expect(
      (wrapper.get('[data-label="Posting evidence"] input').element as HTMLInputElement).value,
    ).toBe("");
    await clickButton(wrapper, "Cancel");
    await clickButton(wrapper, "Billing policies");
    await clickButton(wrapper, "Create billing policy");
    await fillPolicy();
    await clickButton(wrapper, "Cancel");
    await clickButton(wrapper, "Create billing policy");
    expect(
      (wrapper.get('[data-label="Policy code"] input').element as HTMLInputElement).value,
    ).toBe("");
  });
  it.each(["Billing events", "Billing policies", "Invoices"])(
    "renders an honest empty state for %s",
    async (tab) => {
      events = [];
      policies = [];
      invoices = [];
      await render();
      await clickButton(wrapper, tab);
      expect(wrapper.text()).toContain(
        tab === "Billing events"
          ? "No billing events have reached Finance."
          : tab === "Billing policies"
            ? "No billing policies configured."
            : "No posted invoices.",
      );
    },
  );
  it.each(["load", "decision", "policy", "invoice"])(
    "reports %s errors without claiming success",
    async (action) => {
      if (action === "load") {
        context.request.mockRejectedValueOnce(new Error("Unavailable"));
        await render();
      } else {
        await render();
        if (action === "decision") {
          context.request.mockRejectedValueOnce(new Error("Unavailable"));
          await clickButton(wrapper, "Approve");
        }
        if (action === "policy") {
          await clickButton(wrapper, "Billing policies");
          await clickButton(wrapper, "Create billing policy");
          await fillPolicy();
          context.request.mockRejectedValueOnce(new Error("Unavailable"));
          await clickButton(wrapper, "Create draft policy");
        }
        if (action === "invoice") {
          await selectEvent("first");
          await clickButton(wrapper, "Post selected invoice");
          await setField(wrapper, "Posting evidence", "Checked");
          context.request.mockRejectedValueOnce(new Error("Unavailable"));
          await clickButton(wrapper, "Post immutable invoice");
        }
      }
      expect(context.showError).toHaveBeenCalledWith(expect.any(String), "Unavailable");
      expect(context.notify).not.toHaveBeenCalled();
    },
  );
});
