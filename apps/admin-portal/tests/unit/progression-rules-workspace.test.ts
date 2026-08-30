// Author: Tinashe K
import { type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import RulesPage from "../../pages/operations/progression-rules.vue";
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
let rosters: any[];
let rules: any[];
const threshold = {
  id: "outcome",
  priority: 1,
  decisionCode: "PROCEED",
  decisionLabel: "Proceed",
  minimumWeightedAverage: 50,
  minimumPassedCredits: 12,
  maximumFailedCredits: 0,
  maximumFailedModules: 0,
  requireAllCompulsoryPassed: true,
  nextProgrammePeriodNumber: 2,
  fallbackOutcome: false,
};
beforeEach(() => {
  vi.resetAllMocks();
  context = operationalContext();
  rosters = [
    {
      id: "scope",
      programmeId: "programme-1",
      programmeVersionId: "version-1",
      programmePeriodNumber: 1,
    },
    {
      id: "duplicate",
      programmeId: "programme-1",
      programmeVersionId: "version-1",
      programmePeriodNumber: 1,
    },
    {
      id: "later",
      programmeId: "programme-2",
      programmeVersionId: "version-2",
      programmePeriodNumber: 9,
    },
  ];
  rules = ["DRAFT", "APPROVED", "SUPERSEDED"].map((status) => ({
    id: status,
    ruleCode: "RULE-" + status,
    ruleName: "Progression policy",
    programmeId: "programme-1",
    programmeVersionId: "version-1",
    programmePeriodNumber: 1,
    ruleVersion: 1,
    version: 4,
    status,
    outcomes: [
      threshold,
      {
        ...threshold,
        id: "fallback",
        fallbackOutcome: true,
        decisionCode: "REPEAT",
        decisionLabel: "Repeat",
      },
      {
        ...threshold,
        id: "unrestricted",
        minimumWeightedAverage: null,
        minimumPassedCredits: null,
        maximumFailedCredits: null,
        maximumFailedModules: null,
        requireAllCompulsoryPassed: false,
      },
    ],
  }));
  context.request.mockImplementation(async (path: string) =>
    path.endsWith("/rosters") ? rosters : path.endsWith("/rule-sets") ? rules : {},
  );
  fire.mockResolvedValue({ isConfirmed: true, value: " Academic board approval " });
});
afterEach(() => {
  wrapper?.unmount();
  vi.unstubAllGlobals();
});

describe("progression rule governance", () => {
  it("renders all threshold conditions and restricts approval to drafts", async () => {
    wrapper = await renderOperationalPage(RulesPage);
    for (const text of [
      "average ≥ 50%",
      "passed credits ≥ 12",
      "failed credits ≤ 0",
      "failed Modules ≤ 0",
      "all compulsory Modules passed",
      "Final fallback",
    ])
      expect(wrapper.text()).toContain(text);
    expect(
      wrapper.findAll("button").filter((button) => button.text() === "Approve rule set"),
    ).toHaveLength(1);
    await clickButton(wrapper, "New rule set");
    expect(wrapper.findAll('[data-label="Programme scope"] option')).toHaveLength(3);
    expect(
      wrapper
        .findAll('[data-label="Next year and semester"] option')
        .some((option) => option.attributes("value") === "11"),
    ).toBe(true);
    await clickButton(wrapper, "Cancel");
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
  });

  it("creates programme-version-owned rules with ordered editable outcomes", async () => {
    wrapper = await renderOperationalPage(RulesPage);
    await clickButton(wrapper, "New rule set");
    await setField(wrapper, "Rule code", " NEW-RULE ");
    await setField(wrapper, "Rule name", " New policy ");
    await setField(wrapper, "Priority", "4");
    await setField(wrapper, "Decision", "PROCEED_WITH_CARRY");
    await setField(wrapper, "Decision label", "Conditional progress");
    await setField(wrapper, "Minimum average %", "60");
    await setField(wrapper, "Minimum passed credits", "24");
    await setField(wrapper, "Maximum failed credits", "12");
    await setField(wrapper, "Maximum failed Modules", "1");
    await setField(wrapper, "Next year and semester", "3");
    await setField(wrapper, "All compulsory passed", false);
    await setField(wrapper, "Final fallback", true);
    await clickButton(wrapper, "Add outcome");
    expect(wrapper.findAll('[data-label="Priority"]')).toHaveLength(4);
    await clickButton(wrapper, "Remove", 3);
    await clickButton(wrapper, "Save draft rule set");
    expect(context.request).toHaveBeenCalledWith("/api/results/progression/rule-sets", {
      method: "POST",
      body: expect.objectContaining({
        ruleCode: "NEW-RULE",
        ruleName: "New policy",
        programmeId: "programme-1",
        programmeVersionId: "version-1",
        programmePeriodNumber: 1,
        outcomes: expect.arrayContaining([
          expect.objectContaining({
            priority: 4,
            decisionCode: "PROCEED_WITH_CARRY",
            decisionLabel: "Conditional progress",
            minimumWeightedAverage: 60,
            minimumPassedCredits: 24,
            maximumFailedCredits: 12,
            maximumFailedModules: 1,
            nextProgrammePeriodNumber: 3,
            requireAllCompulsoryPassed: false,
            fallbackOutcome: true,
          }),
        ]),
      }),
    });
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    expect(context.notify).toHaveBeenCalledWith(
      expect.objectContaining({ title: "Draft progression rule created" }),
    );
  });

  it.each(["scope", "code", "name"])("does not submit without %s", async (missing) => {
    if (missing === "scope") rosters = [];
    wrapper = await renderOperationalPage(RulesPage);
    await clickButton(wrapper, "New rule set");
    if (missing === "code") await setField(wrapper, "Rule code", " ");
    if (missing === "name") await setField(wrapper, "Rule name", " ");
    await clickButton(wrapper, "Save draft rule set");
    expect(context.request.mock.calls.some(([, options]) => options?.method === "POST")).toBe(
      false,
    );
  });

  it.each(["cancel", "empty", "missing", "confirm"])(
    "requires explicit approval evidence: %s",
    async (mode) => {
      wrapper = await renderOperationalPage(RulesPage);
      fire.mockResolvedValue({
        isConfirmed: mode !== "cancel",
        value: mode === "missing" ? undefined : mode === "empty" ? " " : " Authority ",
      });
      await clickButton(wrapper, "Approve rule set");
      const validator = fire.mock.calls[0]![0].inputValidator;
      expect(validator(" ")).toContain("required");
      expect(validator("Authority")).toBeUndefined();
      expect(context.request.mock.calls.some(([path]) => path.endsWith("/approve"))).toBe(
        mode === "confirm",
      );
      if (mode === "confirm")
        expect(context.request).toHaveBeenCalledWith(
          "/api/results/progression/rule-sets/DRAFT/approve",
          { method: "POST", body: { expectedVersion: 4, reason: "Authority" } },
        );
    },
  );

  it("distinguishes missing registration scope from missing policies", async () => {
    rules = [];
    rosters = [];
    wrapper = await renderOperationalPage(RulesPage);
    expect(wrapper.text()).toContain("No registered programme scope");
    rosters = [
      {
        id: "scope",
        programmeId: "programme",
        programmeVersionId: "version",
        programmePeriodNumber: 1,
      },
    ];
    await clickButton(wrapper, "Refresh");
    expect(wrapper.text()).toContain("No progression rules");
  });

  it.each(["load", "create", "approve"])(
    "reports %s errors and releases the operation",
    async (action) => {
      if (action === "load") context.request.mockRejectedValueOnce(new Error("Offline"));
      wrapper = await renderOperationalPage(RulesPage);
      if (action === "create") {
        await clickButton(wrapper, "New rule set");
        context.request.mockRejectedValueOnce(new Error("Offline"));
        await clickButton(wrapper, "Save draft rule set");
      }
      if (action === "approve") {
        context.request.mockRejectedValueOnce(new Error("Offline"));
        await clickButton(wrapper, "Approve rule set");
      }
      expect(context.showError).toHaveBeenCalledWith(
        expect.stringContaining("could not"),
        "Offline",
      );
      expect(context.notify).not.toHaveBeenCalled();
    },
  );
});
