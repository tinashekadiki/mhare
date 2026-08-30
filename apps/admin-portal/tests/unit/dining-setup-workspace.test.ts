// Author: Tinashe K
import { type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Swal from "sweetalert2";
import DiningPage from "../../pages/operations/dining.vue";
import {
  clickButton,
  operationalContext,
  setField,
} from "../../../../tests/unit/support/operational-page";
import { mountCampusPage } from "../../../../tests/unit/support/campus-page";

vi.mock("sweetalert2", () => ({ default: { fire: vi.fn() } }));
let context: ReturnType<typeof operationalContext>;
let wrapper: VueWrapper;
let setup: ReturnType<typeof diningFixture>;
function diningFixture() {
  return {
    diningHalls: [
      {
        id: "hall",
        code: "MAIN",
        name: "Main dining",
        locationDescription: "Campus",
        serviceCapacity: 100,
        active: true,
        version: 5,
      },
      { id: "closed", code: "CLOSED", name: "Closed hall", active: false, version: 2 },
    ],
    mealOptions: [
      {
        id: "meal",
        code: "LUNCH",
        name: "Lunch",
        mealCategory: "LUNCH",
        description: "Midday",
        active: true,
        version: 6,
      },
      {
        id: "old-meal",
        code: "OLD",
        name: "Old meal",
        mealCategory: "OTHER",
        active: false,
        version: 2,
      },
    ],
    serviceTimes: [
      {
        id: "service",
        diningHallId: "hall",
        mealOptionId: "meal",
        diningHallCode: "MAIN",
        mealOptionCode: "LUNCH",
        dayOfWeek: 1,
        serviceOpensAt: "12:00",
        serviceClosesAt: "13:00",
        graceClosesAt: "13:15",
        active: true,
        version: 7,
      },
    ],
    diningPlans: [
      {
        id: "plan",
        code: "MEALS",
        planVersion: 1,
        name: "Term meals",
        status: "DRAFT",
        validFrom: "2026-08-01",
        validUntil: null,
        version: 8,
      },
      {
        id: "active",
        code: "ACTIVE",
        planVersion: 2,
        name: "Active meals",
        status: "ACTIVE",
        validFrom: "2026-08-01",
        validUntil: "2026-12-01",
        version: 3,
      },
      {
        id: "retired",
        code: "RETIRED",
        planVersion: 1,
        name: "Old meals",
        status: "RETIRED",
        validFrom: "2026-01-01",
        validUntil: null,
        version: 4,
      },
    ],
    planMeals: [
      {
        id: "entitlement",
        diningPlanCode: "MEALS",
        mealOptionCode: "LUNCH",
        servingsPerService: 1,
        serviceDays: [1, 7],
      },
    ],
    hallAssignmentRules: [
      {
        id: "rule",
        diningHallId: "hall",
        diningHallCode: "MAIN",
        ruleDimension: "SURNAME_PREFIX",
        comparisonOperator: "STARTS_WITH",
        comparisonValue: "K",
        priorityRank: 10,
        active: true,
        version: 9,
      },
    ],
    attendantAssignments: [
      {
        id: "attendant",
        diningHallId: "hall",
        diningHallCode: "MAIN",
        staffId: "staff",
        staffNumber: "STAFF1",
        staffName: "First Staff",
        roleCode: "ATTENDANT",
        effectiveFrom: "2026-08-01",
        effectiveUntil: null,
        active: true,
        version: 10,
      },
    ],
  };
}
beforeEach(() => {
  vi.resetAllMocks();
  context = operationalContext();
  setup = diningFixture();
  vi.mocked(Swal.fire).mockResolvedValue({
    isConfirmed: true,
    value: "Approved operating plan",
  } as any);
  context.request.mockImplementation(async (path: string, options?: { method?: string }) => {
    if (options?.method) return {};
    if (path === "/api/dining/setup") return structuredClone(setup);
    if (path === "/api/finance/fee-catalogues")
      return {
        catalogues: [
          { id: "fee", code: "DIN", name: "Meals", status: "ACTIVE", chargeType: "DINING" },
          {
            id: "other-fee",
            code: "OTHER",
            name: "Other fee",
            status: "ACTIVE",
            chargeType: "TUITION",
          },
          {
            id: "draft-fee",
            code: "DRAFT",
            name: "Draft fee",
            status: "DRAFT",
            chargeType: "DINING",
          },
        ],
      };
    if (path === "/api/core/users")
      return [
        { id: "staff", displayName: "First Staff", username: "STAFF1", status: "ACTIVE" },
        { id: "second", displayName: "Second Staff", username: "STAFF2", status: "ACTIVE" },
        { id: "disabled", displayName: "Disabled Staff", username: "DISABLED", status: "DISABLED" },
      ];
    throw new Error(`Unexpected request ${path}`);
  });
});
afterEach(() => {
  wrapper?.unmount();
  vi.unstubAllGlobals();
});
const editableRecords = [
  ["Dining halls", "hall", "halls", 5],
  ["Meal options", "meal", "meal-options", 6],
  ["Service times", "service", "service-times", 7],
  ["Hall routing rules", "rule", "hall-assignment-rules", 9],
  ["Attendant assignments", "attendant", "attendant-assignments", 10],
] as const;
describe("Dining setup governance", () => {
  it.each(editableRecords)(
    "preserves the persisted version when editing %s",
    async (tab, id, route, version) => {
      wrapper = await mountCampusPage(DiningPage);
      await clickButton(wrapper, tab);
      await wrapper.get(`[data-record="${id}"] button`).trigger("click");
      await wrapper.get('[role="dialog"] input[type="checkbox"]').setValue(false);
      await clickButton(wrapper, "Save record");
      expect(context.request).toHaveBeenCalledWith(
        `/api/dining/setup/${route}/${id}`,
        expect.objectContaining({
          method: "PUT",
          body: expect.objectContaining({ expectedVersion: version, active: false }),
        }),
      );
    },
  );
  it("shows active-only capacity metrics and retained inactive inventory", async () => {
    wrapper = await mountCampusPage(DiningPage);
    expect(wrapper.text()).toContain("Active dining halls: 1");
    expect(wrapper.text()).toContain("Active dining plans: 1");
    expect(wrapper.text()).toContain("Active attendants: 1");
    expect(wrapper.text()).toContain("Closed hall");
    expect(wrapper.text()).toContain("Inactive");
  });
  it.each([
    [
      "Dining halls",
      "Create dining hall",
      "halls",
      { Code: "NEW", Name: "New hall", Location: "East", "Service capacity": "200" },
      { code: "NEW", name: "New hall", locationDescription: "East", serviceCapacity: 200 },
    ],
    [
      "Meal options",
      "Create meal option",
      "meal-options",
      { Code: "SUP", Name: "Supper", Description: "Evening", Category: "DINNER" },
      { code: "SUP", mealCategory: "DINNER" },
    ],
    [
      "Service times",
      "Create service time",
      "service-times",
      { Day: "7", Opens: "17:00", Closes: "19:00", "Grace closes": "19:15" },
      { diningHallId: "hall", mealOptionId: "meal", dayOfWeek: 7, serviceOpensAt: "17:00" },
    ],
    [
      "Hall routing rules",
      "Create routing rule",
      "hall-assignment-rules",
      {
        "Student dimension": "PROGRAMME",
        Operator: "EQUALS",
        "Match value": "BSC",
        Priority: "20",
      },
      {
        ruleDimension: "PROGRAMME",
        comparisonOperator: "EQUALS",
        comparisonValue: "BSC",
        priorityRank: 20,
      },
    ],
    [
      "Attendant assignments",
      "Assign attendant",
      "attendant-assignments",
      { "Staff member": "second", Role: "SUPERVISOR", "Effective from": "2026-09-01" },
      {
        staffId: "second",
        staffName: "Second Staff",
        staffNumber: "STAFF2",
        roleCode: "SUPERVISOR",
        effectiveUntil: null,
      },
    ],
  ] as const)("creates %s from governed form fields", async (tab, action, route, fields, body) => {
    wrapper = await mountCampusPage(DiningPage);
    await clickButton(wrapper, tab);
    await clickButton(wrapper, action);
    for (const [label, value] of Object.entries(fields)) await setField(wrapper, label, value);
    expect(wrapper.get('[role="dialog"]').text()).not.toContain("Closed hall");
    expect(wrapper.get('[role="dialog"]').text()).not.toContain("Disabled Staff");
    await clickButton(wrapper, "Save record");
    expect(context.request).toHaveBeenCalledWith(
      `/api/dining/setup/${route}`,
      expect.objectContaining({
        method: "POST",
        body: expect.objectContaining({ ...body, expectedVersion: 0 }),
      }),
    );
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    expect(context.notify).toHaveBeenCalledWith(
      expect.objectContaining({ title: "Dining setup saved" }),
    );
  });
  it.each([false, true])(
    "prepares a plan with optional finance/window values: %s",
    async (withValues) => {
      wrapper = await mountCampusPage(DiningPage);
      await clickButton(wrapper, "Dining plans");
      await clickButton(wrapper, "Prepare dining plan");
      await setField(wrapper, "Code", "NEWPLAN");
      await setField(wrapper, "Version", "2");
      await setField(wrapper, "Name", "New plan");
      await setField(wrapper, "Description", "Meals");
      await setField(wrapper, "Valid from", "2026-09-01");
      expect(wrapper.get('[role="dialog"]').text()).toContain("Maker-checker controlled");
      expect(wrapper.get('[role="dialog"]').text()).not.toContain("Other fee");
      expect(wrapper.get('[role="dialog"]').text()).not.toContain("Draft fee");
      if (withValues) await setField(wrapper, "Valid until", "2026-12-01");
      else await setField(wrapper, "Finance fee", "");
      await clickButton(wrapper, "Save record");
      expect(context.request).toHaveBeenCalledWith(
        "/api/dining/setup/plans",
        expect.objectContaining({
          body: expect.objectContaining({
            financeFeeCatalogueId: withValues ? "fee" : null,
            validUntil: withValues ? "2026-12-01" : null,
            planVersion: 2,
          }),
        }),
      );
    },
  );
  it("maps selected service weekdays to explicit entitlement booleans", async () => {
    wrapper = await mountCampusPage(DiningPage);
    await clickButton(wrapper, "Plan entitlements");
    expect(wrapper.text()).toContain("Mon, Sun");
    await clickButton(wrapper, "Add entitlement");
    expect(wrapper.get('[role="dialog"]').text()).not.toContain("Active meals");
    await setField(wrapper, "Servings per service", "2");
    await wrapper.get('input[aria-label="Mon"]').setValue(false);
    await wrapper.get('input[aria-label="Sun"]').setValue(true);
    await clickButton(wrapper, "Save record");
    expect(context.request).toHaveBeenCalledWith("/api/dining/setup/plans/plan/meals", {
      method: "POST",
      body: {
        mealOptionId: "meal",
        servingsPerService: 2,
        monday: false,
        tuesday: true,
        wednesday: true,
        thursday: true,
        friday: true,
        saturday: false,
        sunday: true,
      },
    });
  });
  it.each([
    ["Activate", "plan", "ACTIVE", 8],
    ["Retire", "active", "RETIRED", 3],
  ] as const)(
    "%s requires recorded approval and the latest version",
    async (action, id, targetStatus, expectedVersion) => {
      wrapper = await mountCampusPage(DiningPage);
      await clickButton(wrapper, "Dining plans");
      await clickButton(wrapper, action);
      expect(context.request).toHaveBeenCalledWith(`/api/dining/setup/plans/${id}/transition`, {
        method: "POST",
        body: { targetStatus, expectedVersion, reason: "Approved operating plan" },
      });
      const validator = (vi.mocked(Swal.fire).mock.calls[0]?.[0] as any).inputValidator;
      expect(validator(" ")).toBe("Decision evidence is required.");
      expect(validator("Valid authority")).toBeUndefined();
    },
  );
  it("does not transition when approval is cancelled", async () => {
    vi.mocked(Swal.fire).mockResolvedValue({ isConfirmed: false } as any);
    wrapper = await mountCampusPage(DiningPage);
    await clickButton(wrapper, "Dining plans");
    await clickButton(wrapper, "Activate");
    expect(context.request.mock.calls.filter(([, options]) => options?.method)).toEqual([]);
  });
  it("keeps failed saves editable and reports transition errors", async () => {
    wrapper = await mountCampusPage(DiningPage);
    context.request.mockRejectedValue(new Error("Conflict"));
    await clickButton(wrapper, "Create dining hall");
    await clickButton(wrapper, "Save record");
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true);
    expect(context.showError).toHaveBeenCalledWith("Dining setup was not saved", "Conflict");
    await clickButton(wrapper, "Cancel");
    await clickButton(wrapper, "Dining plans");
    await clickButton(wrapper, "Activate");
    expect(context.showError).toHaveBeenCalledWith("Plan transition failed", "Conflict");
  });
  it("keeps setup usable when independent dependencies fail", async () => {
    const original = context.request.getMockImplementation()!;
    context.request.mockImplementation((path, ...args) =>
      path === "/api/dining/setup" ? original(path, ...args) : Promise.reject(new Error("Offline")),
    );
    wrapper = await mountCampusPage(DiningPage);
    expect(wrapper.text()).toContain("Limited integration availability");
    expect(wrapper.text()).toContain("fee linking is disabled");
    expect(wrapper.text()).toContain("new attendant assignments are disabled");
    expect(wrapper.text()).toContain("Main dining");
  });
  it("shows the primary setup error and recovers on refresh", async () => {
    context.request.mockRejectedValueOnce(new Error("Setup offline"));
    wrapper = await mountCampusPage(DiningPage);
    expect(wrapper.text()).toContain("Setup offline");
    await clickButton(wrapper, "Refresh");
    expect(wrapper.text()).not.toContain("Setup offline");
    expect(wrapper.text()).toContain("Main dining");
  });
});
