// Author: Tinashe K
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { defineComponent, ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import FeePage from "../../pages/operations/finance-fees.vue";
import type {
  FinanceFeeCatalogueSummary,
  FinanceFeeRuleSummary,
  FinanceFeeStructureSummary,
  FinanceStudentDiscountSummary,
} from "../../../../packages/portal-shell/types/finance";
import {
  clickButton,
  operationalContext,
  operationalStubs,
  setField,
} from "../../../../tests/unit/support/operational-page";
import { registerStubs } from "../../../../tests/unit/support/register-page";
const { confirm } = vi.hoisted(() => ({ confirm: vi.fn() }));
vi.mock("sweetalert2", () => ({ default: { fire: confirm } }));
const StructureDrawer = defineComponent({
  props: ["open", "initialContext", "catalogues", "academicOverview", "applicantCategories"],
  emits: ["created", "update:open"],
  template:
    '<aside v-if="open" data-structure-drawer>{{initialContext}}<button @click="$emit(\'created\')">Saved structure</button><button @click="$emit(\'update:open\', false)">Close structure</button></aside>',
});
const Collapsible = defineComponent({
  props: ["open"],
  emits: ["update:open"],
  template:
    '<section><div @click="$emit(\'update:open\', !open)"><slot/></div><div v-if="open"><slot name="content"/></div></section>',
});
const stamp = "2026-08-01T00:00:00Z";
const rule = (changes: Partial<FinanceFeeRuleSummary> = {}): FinanceFeeRuleSummary => ({
  id: "rule",
  ruleVersion: 2,
  transactionCurrencyCode: "USD",
  transactionAmount: 100,
  baseCurrencyCode: "USD",
  baseAmount: 100,
  ratingStatus: "RATED",
  effectiveFrom: stamp,
  status: "DRAFT",
  preparedByUserId: "maker",
  version: 7,
  scopes: [{ id: "scope", scopeDimension: "GLOBAL" }],
  ...changes,
});
const catalogue = (
  changes: Partial<FinanceFeeCatalogueSummary> = {},
): FinanceFeeCatalogueSummary => ({
  id: "catalogue",
  code: "TUITION",
  name: "Tuition fee",
  chargeType: "PROGRAMME",
  receivableAccountCode: "AR-STUDENT",
  revenueAccountCode: "REV-TUITION",
  baseCurrencyCode: "USD",
  status: "ACTIVE",
  preparedByUserId: "maker",
  version: 3,
  rules: [],
  ...changes,
});
const structure = (
  changes: Partial<FinanceFeeStructureSummary> = {},
): FinanceFeeStructureSummary => ({
  id: "structure",
  code: "UG-2026",
  name: "Undergraduate schedule",
  feeContext: "APPLICATION",
  scopeType: "INSTITUTION",
  programmeLevelCode: "UG",
  programmeLevelName: "Undergraduate",
  transactionCurrencyCode: "USD",
  effectiveFrom: stamp,
  status: "DRAFT",
  preparedByUserId: "maker",
  version: 8,
  attachments: [],
  lines: [
    {
      feeRuleId: "line",
      lineNumber: 1,
      feeCatalogueId: "catalogue",
      feeCode: "TUITION",
      feeName: "Tuition",
      description: "Semester tuition",
      chargeType: "PROGRAMME",
      receivableAccountCode: "AR",
      revenueAccountCode: "REV",
      transactionAmount: 100,
      transactionCurrencyCode: "USD",
      baseAmount: 100,
      ratingStatus: "RATED",
      status: "DRAFT",
    },
  ],
  ...changes,
});
const discount = (
  changes: Partial<FinanceStudentDiscountSummary> = {},
): FinanceStudentDiscountSummary => ({
  id: "discount",
  code: "ATTACH",
  name: "Attachment reduction",
  scopeType: "INSTITUTION",
  academicUnitDepth: 0,
  programmeLevelId: "ug",
  programmeLevelCode: "UG",
  programmeLevelName: "Undergraduate",
  programmeStudyLevel: "1.1",
  targetType: "ALL_FEES",
  discountPercentage: 10,
  authorityReference: "Minute 7",
  effectiveFrom: stamp,
  status: "DRAFT",
  preparedByUserId: "maker",
  version: 9,
  ...changes,
});
let wrapper: VueWrapper;
let context: ReturnType<typeof operationalContext>;
let catalogues: FinanceFeeCatalogueSummary[];
let structures: FinanceFeeStructureSummary[];
let discounts: FinanceStudentDiscountSummary[];
let failPath: string | undefined;
let failWrites: boolean;
const academic = () => ({
  academicUnits: [
    { id: "faculty", code: "SCI", name: "Science", status: "ACTIVE", parentId: null },
    { id: "department", code: "CS", name: "Computing", status: "ACTIVE", parentId: "faculty" },
    { id: "retired", code: "OLD", name: "Old unit", status: "INACTIVE", parentId: null },
  ],
  programmeLevels: [
    { id: "ug", code: "UG", name: "Undergraduate", status: "ACTIVE" },
    { id: "pg", code: "PG", name: "Postgraduate", status: "ACTIVE" },
    { id: "short", code: "SHORT", name: "Short study", status: "ACTIVE" },
  ],
  programmes: [
    {
      id: "computing",
      code: "BSC",
      name: "Computing",
      programmeLevelId: "ug",
      owningAcademicUnitId: "department",
      status: "ACTIVE",
      maximumDurationPeriods: 8,
    },
    {
      id: "masters",
      code: "MSC",
      name: "Masters",
      programmeLevelId: "pg",
      owningAcademicUnitId: "faculty",
      status: "ACTIVE",
      maximumDurationPeriods: 2,
    },
    {
      id: "retired-programme",
      code: "OLD",
      name: "Retired programme",
      programmeLevelId: "ug",
      owningAcademicUnitId: "department",
      status: "INACTIVE",
      maximumDurationPeriods: 4,
    },
  ],
});
let overview: ReturnType<typeof ref<ReturnType<typeof academic> | null>>;
beforeEach(() => {
  vi.resetAllMocks();
  context = operationalContext();
  catalogues = [catalogue()];
  structures = [structure()];
  discounts = [discount()];
  overview = ref(academic());
  failPath = undefined;
  failWrites = false;
  vi.stubGlobal("useAcademicSetup", () => ({ overview, ensureOverview: vi.fn() }));
  confirm.mockResolvedValue({ isConfirmed: true, value: "  Approved schedule  " });
  context.request.mockImplementation(async (path: string, options?: { method?: string }) => {
    if (path === failPath || (options?.method && failWrites)) throw new Error("Unavailable");
    if (options?.method) return {};
    if (path === "/api/finance/fee-catalogues") return structuredClone({ catalogues });
    if (path === "/api/finance/fee-structures") return structuredClone({ structures });
    if (path === "/api/finance/student-discounts") return structuredClone({ discounts });
    if (path === "/api/admissions/applications/applicant-categories")
      return [{ code: "LOCAL", label: "Local applicants" }];
    throw new Error(`Unexpected ${path}`);
  });
});
afterEach(() => {
  wrapper?.unmount();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
async function render(tab?: string) {
  wrapper = mount(FeePage, {
    global: {
      stubs: {
        ...registerStubs,
        EmhareFeedbackState: operationalStubs.UAlert,
        EmhareRecordDrawer: operationalStubs.EmhareRecordDrawer,
        EmhareFeeStructureDrawer: StructureDrawer,
        UCollapsible: Collapsible,
      },
    },
  });
  await flushPromises();
  if (tab) await clickButton(wrapper, tab);
}
const writes = () => context.request.mock.calls.filter(([, options]) => options?.method);
const field = (label: string) =>
  wrapper.findAll(".field").find((element) => element.attributes("data-label") === label)!;
async function fillDiscount() {
  await clickButton(wrapper, "New student discount");
  for (const [label, value] of [
    ["Discount code", "  ATTACH  "],
    ["Discount name", "  Attachment  "],
    ["Authority reference", "  Minute 9  "],
    ["Programme level", "ug"],
    ["Programme study level", "1.1"],
    ["Discount rate", "15"],
    ["Effective from", "2026-08-01T10:00"],
  ] as const)
    await setField(wrapper, label, value);
}
async function fillPrice() {
  await clickButton(wrapper, "Add price");
  await setField(wrapper, "Transaction amount", "120");
  await setField(wrapper, "Effective from", "2026-08-01T10:00");
}
describe("fee catalogue and effective price governance", () => {
  it.each(["TUITION", "tuition fee", "programme", "AR-STUDENT", "REV-TUITION"])(
    "searches governed definitions by %s",
    async (query) => {
      catalogues.push(
        catalogue({
          id: "other",
          code: "DINING",
          name: "Meals",
          chargeType: "DINING",
          receivableAccountCode: "AR-MEAL",
          revenueAccountCode: "REV-MEAL",
        }),
      );
      await render("Line-item catalogue");
      await wrapper
        .get('input[placeholder="Search fee, charge type, or posting account"]')
        .setValue(` ${query} `);
      expect(wrapper.get("tbody").text()).toContain("Tuition fee");
      expect(wrapper.get("tbody").text()).not.toContain("Meals");
    },
  );
  it("combines status and search filters and recovers an empty result", async () => {
    await render("Line-item catalogue");
    await wrapper.get("select").setValue("RETIRED");
    expect(wrapper.text()).toContain("No fee definitions match");
    await wrapper.get("select").setValue("ACTIVE");
    expect(wrapper.text()).toContain("No effective price");
  });
  it.each(["Fee code", "Fee name", "Receivable account code", "Revenue account code"])(
    "rejects a definition missing %s",
    async (missing) => {
      await render("Line-item catalogue");
      await clickButton(wrapper, "New line definition");
      for (const label of [
        "Fee code",
        "Fee name",
        "Receivable account code",
        "Revenue account code",
      ])
        await setField(wrapper, label, label === missing ? " " : "VALID");
      await clickButton(wrapper, "Create draft definition");
      expect(writes()).toHaveLength(0);
      expect(wrapper.find('[role="dialog"]').exists()).toBe(true);
    },
  );
  it.each([false, true])(
    "captures a definition with optional posting evidence present=%s",
    async (populated) => {
      await render("Line-item catalogue");
      await clickButton(wrapper, "New line definition");
      for (const label of [
        "Fee code",
        "Fee name",
        "Receivable account code",
        "Revenue account code",
      ])
        await setField(wrapper, label, "VALID");
      if (populated) {
        await setField(wrapper, "Tax code", " VAT ");
        await setField(wrapper, "Policy description", " Authority ");
      }
      await clickButton(wrapper, "Create draft definition");
      expect(writes()[0]).toEqual([
        "/api/finance/fee-catalogues",
        {
          method: "POST",
          body: expect.objectContaining({
            taxCode: populated ? "VAT" : null,
            description: populated ? "Authority" : null,
          }),
        },
      ]);
      expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    },
  );
  it("retains a rejected definition for retry and resets after cancellation", async () => {
    await render("Line-item catalogue");
    await clickButton(wrapper, "New line definition");
    for (const label of ["Fee code", "Fee name", "Receivable account code", "Revenue account code"])
      await setField(wrapper, label, "VALID");
    failWrites = true;
    await clickButton(wrapper, "Create draft definition");
    expect(context.showError).toHaveBeenCalledWith(
      "Fee definition could not be created",
      "Unavailable",
    );
    expect(field("Fee code").get("input").element.value).toBe("VALID");
    await clickButton(wrapper, "Cancel");
    await clickButton(wrapper, "New line definition");
    expect(field("Fee code").get("input").element.value).toBe("");
  });
  it.each([
    ["Activate", "catalogue", "/api/finance/fee-catalogues/catalogue/activate", 3],
    ["Approve", "draft", "/api/finance/fee-catalogues/rules/rule/approve", 7],
    ["Retire price", "approved", "/api/finance/fee-catalogues/rules/rule/retire", 7],
  ] as const)("requires evidence and version for %s", async (label, kind, path, version) => {
    catalogues = [
      catalogue({
        status: kind === "catalogue" ? "DRAFT" : "ACTIVE",
        rules:
          kind === "catalogue" ? [] : [rule({ status: kind === "draft" ? "DRAFT" : "APPROVED" })],
      }),
    ];
    await render("Line-item catalogue");
    await clickButton(wrapper, label);
    expect(writes()[0]).toEqual([
      path,
      { method: "POST", body: { reason: "Approved schedule", expectedVersion: version } },
    ]);
    const validator = confirm.mock.calls[0]![0].inputValidator;
    expect(validator(" ")).toBe("A complete reason is required.");
    expect(validator("Checked")).toBeUndefined();
  });
  it.each([{ isConfirmed: false }, { isConfirmed: true }, { isConfirmed: true, value: " " }])(
    "does not approve without confirmed evidence: %j",
    async (result) => {
      catalogues = [catalogue({ rules: [rule()] })];
      confirm.mockResolvedValue(result);
      await render("Line-item catalogue");
      await clickButton(wrapper, "Approve");
      expect(writes()).toHaveLength(0);
    },
  );
  it("applies effective rate with optimistic version and keeps unrated amounts explicit", async () => {
    catalogues = [
      catalogue({
        rules: [
          rule({
            status: "PENDING_RATE",
            ratingStatus: "UNRATED",
            baseAmount: null,
            transactionCurrencyCode: "ZWG",
          }),
        ],
      }),
    ];
    await render("Line-item catalogue");
    expect(wrapper.text()).toContain("Unrated");
    expect(wrapper.text()).toContain("Rate required");
    expect(wrapper.findAll("button").some((button) => button.text() === "Approve")).toBe(false);
    await clickButton(wrapper, "Apply rate");
    expect(writes()[0]).toEqual([
      "/api/finance/fee-catalogues/rules/rule/rate?expectedVersion=7",
      { method: "POST" },
    ]);
  });
  it("does not expose price changes on retired definitions or retired rules", async () => {
    catalogues = [
      catalogue({
        status: "RETIRED",
        taxCode: "VAT",
        rules: [
          rule({
            status: "RETIRED",
            effectiveUntil: stamp,
            scopes: [
              { id: "s1", scopeDimension: "PROGRAMME", referenceName: "Computing" },
              { id: "s2", scopeDimension: "MODULE", referenceCode: "CSC101" },
            ],
          }),
        ],
      }),
    ];
    await render("Line-item catalogue");
    expect(wrapper.text()).toContain("Programme · Computing");
    expect(wrapper.text()).toContain("Module · CSC101");
    expect(wrapper.text()).toContain("VAT");
    expect(wrapper.get("tbody").findAll("button")).toHaveLength(0);
  });
  it.each(["USD", "ZWG"])(
    "creates a global %s price without inventing exchange evidence",
    async (currency) => {
      await render("Line-item catalogue");
      await fillPrice();
      await setField(wrapper, "Transaction currency", currency);
      await clickButton(wrapper, "Add dimension");
      expect(wrapper.findAll('.field[data-label="Dimension"]')).toHaveLength(1);
      await wrapper.get('button[aria-label="Remove scope"]').trigger("click");
      expect(wrapper.findAll('.field[data-label="Dimension"]')).toHaveLength(1);
      await clickButton(wrapper, "Create draft price");
      expect(writes()[0]).toEqual([
        "/api/finance/fee-catalogues/catalogue/rules",
        {
          method: "POST",
          body: {
            transactionCurrencyCode: currency,
            transactionAmount: 120,
            effectiveFrom: new Date("2026-08-01T10:00").toISOString(),
            effectiveUntil: null,
            scopes: [
              {
                scopeDimension: "GLOBAL",
                referenceId: null,
                referenceCode: null,
                referenceName: null,
              },
            ],
          },
        },
      ]);
      expect(context.notify).toHaveBeenCalledWith(
        expect.objectContaining({
          title:
            currency === "USD"
              ? "Draft effective price created"
              : "Foreign-currency price captured for rating",
        }),
      );
    },
  );
  it("validates non-global scope references, adds/removes dimensions, and clears identifiers on global scope", async () => {
    await render("Line-item catalogue");
    await fillPrice();
    await setField(wrapper, "Dimension", "PROGRAMME");
    await clickButton(wrapper, "Create draft price");
    expect(writes()).toHaveLength(0);
    await wrapper.get('input[placeholder="UUID"]').setValue(" programme-id ");
    await setField(wrapper, "Reference name", " Computing ");
    await clickButton(wrapper, "Add dimension");
    expect(wrapper.findAll('.field[data-label="Dimension"]')).toHaveLength(2);
    await wrapper.findAll('button[aria-label="Remove scope"]')[1]!.trigger("click");
    await setField(wrapper, "Effective until", "2026-09-01T10:00");
    await clickButton(wrapper, "Create draft price");
    expect(writes()[0]![1].body).toMatchObject({
      scopes: [
        {
          scopeDimension: "PROGRAMME",
          referenceId: "programme-id",
          referenceCode: null,
          referenceName: "Computing",
        },
      ],
      effectiveUntil: new Date("2026-09-01T10:00").toISOString(),
    });
    await fillPrice();
    await setField(wrapper, "Dimension", "MODULE");
    await clickButton(wrapper, "Add dimension");
    await setField(wrapper, "Dimension", "GLOBAL");
    expect(wrapper.findAll('.field[data-label="Dimension"]')).toHaveLength(1);
    expect(wrapper.find('input[placeholder="UUID"]').exists()).toBe(false);
  });
  it("retains failed price values, cancels without submission, and surfaces failed operations", async () => {
    catalogues = [catalogue({ rules: [rule()] })];
    await render("Line-item catalogue");
    await fillPrice();
    failWrites = true;
    await clickButton(wrapper, "Create draft price");
    expect(context.showError).toHaveBeenCalledWith(
      "Effective price could not be created",
      "Unavailable",
    );
    expect(field("Transaction amount").get("input").element.value).toBe("120");
    await clickButton(wrapper, "Cancel");
    await clickButton(wrapper, "Approve");
    expect(context.showError).toHaveBeenCalledWith(
      "Finance operation could not be completed",
      "Unavailable",
    );
  });
});
describe("complete schedules and governed student discounts", () => {
  it.each(["activate", "retire"] as const)(
    "governs complete schedule %s with immutable version evidence",
    async (action) => {
      structures = [
        structure({
          status: action === "activate" ? "DRAFT" : "ACTIVE",
          applicantCategoryCode: "LOCAL",
        }),
      ];
      await render();
      expect(wrapper.text()).toContain("Local applicants");
      await clickButton(wrapper, action === "activate" ? "Verify and activate" : "Retire");
      expect(writes()[0]).toEqual([
        `/api/finance/fee-structures/structure/${action}`,
        { method: "POST", body: { reason: "Approved schedule", expectedVersion: 8 } },
      ]);
    },
  );
  it("blocks unrated schedule activation and retains unknown applicant-category labels", async () => {
    const pending = structure({
      transactionCurrencyCode: "ZWG",
      applicantCategoryCode: "SPECIAL_CASE",
    });
    pending.lines[0]!.ratingStatus = "UNRATED";
    pending.lines[0]!.baseAmount = null;
    structures = [pending];
    await render();
    expect(wrapper.text()).toContain("Special Case");
    expect(wrapper.text()).toContain("Exchange rate required");
    expect(
      wrapper
        .findAll("button")
        .find((button) => button.text() === "Verify and activate")!
        .attributes("disabled"),
    ).toBeDefined();
    expect(writes()).toHaveLength(0);
  });
  it.each([{ isConfirmed: false }, { isConfirmed: true, value: " " }])(
    "cancels schedule changes without writes: %j",
    async (result) => {
      confirm.mockResolvedValue(result);
      await render();
      await clickButton(wrapper, "Verify and activate");
      expect(writes()).toHaveLength(0);
    },
  );
  it("renders precedence, totals, tax, rating and reversible line-item expansion", async () => {
    const academicSchedule = structure({
      feeContext: "ACADEMIC",
      scopeType: "PROGRAMME",
      scopeReferenceCode: "BSC",
      scopeReferenceName: "Computing",
      status: "RETIRED",
    });
    academicSchedule.lines[0]!.taxCode = "VAT";
    academicSchedule.lines[0]!.ratingStatus = "UNRATED";
    structures = [
      academicSchedule,
      structure({ id: "accommodation", feeContext: "ACCOMMODATION", status: "ACTIVE" }),
      structure({ id: "fallback", feeContext: "ACADEMIC", scopeType: "INSTITUTION" }),
    ];
    await render("Student fee structures");
    expect(wrapper.text()).toContain("Programme · BSC · Computing");
    expect(wrapper.text()).toContain("Global accommodation rate");
    expect(wrapper.text()).toContain("Institution default");
    await clickButton(wrapper, "Show line items (1)");
    expect(wrapper.text()).toContain("Semester tuition");
    expect(wrapper.text()).toContain("VAT");
    expect(wrapper.text()).toContain("Rate required");
    expect(wrapper.text()).toContain("Complete schedule total");
    await clickButton(wrapper, "Hide line items (1)");
    expect(wrapper.text()).not.toContain("Semester tuition");
  });
  it("passes the correct context to the shared structure drawer and reloads after creation", async () => {
    await render();
    await clickButton(wrapper, "Configure application fee");
    expect(wrapper.get("[data-structure-drawer]").text()).toContain("APPLICATION");
    const before = context.request.mock.calls.length;
    await clickButton(wrapper, "Saved structure");
    expect(context.request.mock.calls.length).toBe(before + 4);
    await clickButton(wrapper, "Close structure");
    await clickButton(wrapper, "Student fee structures");
    await clickButton(wrapper, "New student fee structure");
    expect(wrapper.get("[data-structure-drawer]").text()).toContain("ACADEMIC");
  });
  it.each([
    "Discount code",
    "Discount name",
    "Authority reference",
    "Discount rate",
    "Effective from",
  ])("does not persist discount missing %s", async (missing) => {
    await render("Student discounts");
    await fillDiscount();
    await setField(wrapper, missing, missing === "Discount rate" ? "100" : "");
    await clickButton(wrapper, "Create draft discount");
    expect(writes()).toHaveLength(0);
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true);
  });
  it.each(["global", "unit", "programme"] as const)(
    "snapshots %s discount scope with authoritative hierarchy and optional dates",
    async (scope) => {
      await render("Student discounts");
      await fillDiscount();
      if (scope !== "global") await setField(wrapper, "Academic unit", "department");
      if (scope === "programme") {
        await setField(wrapper, "Programme", "computing");
        await setField(wrapper, "Apply reduction to", "FEE_LINE");
        await setField(wrapper, "Fee line", "catalogue");
        await setField(wrapper, "Effective until", "2026-09-01T10:00");
      }
      await clickButton(wrapper, "Create draft discount");
      expect(writes()[0]).toEqual([
        "/api/finance/student-discounts",
        {
          method: "POST",
          body: expect.objectContaining({
            code: "ATTACH",
            name: "Attachment",
            authorityReference: "Minute 9",
            academicUnitId: scope === "global" ? null : "department",
            academicUnitDepth: scope === "global" ? 0 : 2,
            programmeId: scope === "programme" ? "computing" : null,
            programmeLevelCode: "UG",
            programmeStudyLevel: "1.1",
            feeCatalogueId: scope === "programme" ? "catalogue" : null,
            effectiveUntil:
              scope === "programme" ? new Date("2026-09-01T10:00").toISOString() : null,
          }),
        },
      ]);
      expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    },
  );
  it("uses only active academic references and clears incompatible programme/study-level choices", async () => {
    await render("Student discounts");
    await fillDiscount();
    expect(field("Programme level").text()).not.toContain("Short study");
    expect(field("Academic unit").text()).not.toContain("Old unit");
    expect(field("Programme").text()).not.toContain("Retired programme");
    await setField(wrapper, "Programme", "computing");
    await setField(wrapper, "Programme study level", "4.2");
    await setField(wrapper, "Programme level", "pg");
    expect(field("Programme").get("select").element.value).toBe("");
    expect(field("Programme study level").get("select").element.value).toBe("");
    expect(field("Programme").text()).toContain("Masters");
    await setField(wrapper, "Programme", "masters");
    await setField(wrapper, "Academic unit", "department");
    expect(field("Programme").get("select").element.value).toBe("");
  });
  it("requires a targeted fee line and clears that target when changed to all fees", async () => {
    await render("Student discounts");
    await fillDiscount();
    await setField(wrapper, "Apply reduction to", "FEE_LINE");
    await clickButton(wrapper, "Create draft discount");
    expect(writes()).toHaveLength(0);
    await setField(wrapper, "Fee line", "catalogue");
    await setField(wrapper, "Apply reduction to", "ALL_FEES");
    await clickButton(wrapper, "Create draft discount");
    expect(writes()[0]![1].body.feeCatalogueId).toBeNull();
  });
  it("handles failed discount saves without losing inputs, then cancels and resets", async () => {
    await render("Student discounts");
    await fillDiscount();
    failWrites = true;
    await clickButton(wrapper, "Create draft discount");
    expect(context.showError).toHaveBeenCalledWith(
      "Student discount could not be created",
      "Unavailable",
    );
    expect(field("Discount code").get("input").element.value).toBe("  ATTACH  ");
    await clickButton(wrapper, "Cancel");
    await clickButton(wrapper, "New student discount");
    expect(field("Discount code").get("input").element.value).toBe("");
  });
  it.each(["activate", "retire"] as const)("requires evidence for discount %s", async (action) => {
    discounts = [
      discount({
        status: action === "activate" ? "DRAFT" : "ACTIVE",
        programmeId: "computing",
        programmeCode: "BSC",
        programmeName: "Computing",
        academicUnitId: "department",
        academicUnitCode: "CS",
        academicUnitName: "Computing Department",
        targetType: "FEE_LINE",
        feeCode: "TUITION",
        feeName: "Tuition",
      }),
    ];
    await render("Student discounts");
    expect(wrapper.text()).toContain("Computing Department");
    await clickButton(wrapper, action === "activate" ? "Activate" : "Retire");
    expect(writes()[0]).toEqual([
      `/api/finance/student-discounts/discount/${action}`,
      { method: "POST", body: { reason: "Approved schedule", expectedVersion: 9 } },
    ]);
  });
  it("cancels discount approval and leaves retired discounts immutable", async () => {
    confirm.mockResolvedValue({ isConfirmed: false });
    await render("Student discounts");
    await clickButton(wrapper, "Activate");
    expect(writes()).toHaveLength(0);
    discounts = [discount({ status: "RETIRED", academicUnitId: "department" })];
    await clickButton(wrapper, "Refresh");
    expect(wrapper.get("tbody").text()).toContain("Academic unit");
    expect(wrapper.get("tbody").findAll("button")).toHaveLength(0);
  });
  it("surfaces register failure and recovers empty registers on refresh", async () => {
    failPath = "/api/finance/fee-structures";
    await render();
    expect(context.showError).toHaveBeenCalledWith(
      "Fee catalogue register could not be loaded",
      "Unavailable",
    );
    failPath = undefined;
    structures = [];
    discounts = [];
    await clickButton(wrapper, "Refresh");
    expect(wrapper.text()).toContain("No application fees configured");
    await clickButton(wrapper, "Student fee structures");
    expect(wrapper.text()).toContain("No student fee structures configured");
    await clickButton(wrapper, "Student discounts");
    expect(wrapper.text()).toContain("No student discounts configured");
  });
});
