// Author: Tinashe K
import { flushPromises, mount } from "@vue/test-utils";
import { computed, reactive, ref } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ApplicationStartPage from "../../pages/applications/new.vue";

let journey: any;
const stubs = {
  EmhareTopNav: { template: '<header><slot name="actions" /></header>' },
  EmhareVerticalStepper: {
    props: ["steps"],
    template:
      '<nav><span v-for="step in steps">{{ step.title }} {{ step.description }}</span></nav>',
  },
  EmhareFormField: {
    props: ["label", "items", "modelValue", "disabled", "description", "required"],
    emits: ["update:modelValue"],
    template:
      '<label>{{ label }}<select :aria-label="label" :value="modelValue" :disabled="disabled" :required="required" @change="$emit(\'update:modelValue\', $event.target.value)"><option value=""/><option v-for="item in items" :value="item.value">{{ item.label }}</option></select><span>{{ description }}</span></label>',
  },
  UButton: {
    props: ["label", "loading"],
    template: '<button :disabled="loading">{{ label }}</button>',
  },
  UAlert: {
    props: ["title", "description"],
    template: '<aside>{{ title }} {{ description }}<slot name="actions"/></aside>',
  },
  UIcon: true,
  USkeleton: true,
  UProgress: true,
};

beforeEach(() => {
  vi.stubGlobal("computed", computed);
  vi.stubGlobal("definePageMeta", vi.fn());
  journey = {
    auth: { authenticated: ref(true), login: vi.fn() },
    startOptions: ref({ routes: [{}] }),
    loadingOptions: ref(false),
    creatingApplication: ref(false),
    attemptedCreate: ref(false),
    pageError: ref(""),
    activeStartStep: ref("APPLICATION_ROUTE"),
    form: reactive({ applicantCategoryCode: "LOCAL", applicationTypeId: "", intakeId: "" }),
    applicantCategoryItems: ref([{ label: "Local applicant", value: "LOCAL" }]),
    applicationRouteCards: ref([
      { id: "undergrad", name: "Undergraduate", normalizedCode: "UNDERGRAD", evidence: [] },
    ]),
    intakeItems: ref([{ label: "August 2026", value: "august" }]),
    selectedApplicationType: ref(null),
    selectedApplicationRouteCard: ref(null),
    activeStartStepTitle: ref("Application route"),
    activeStartStepDescription: ref("Application type and intake"),
    activeStartStepIndex: ref(0),
    applicationJourneySteps: ref([
      {
        id: "APPLICATION_ROUTE",
        title: "Application route",
        description: "Application type and intake",
      },
    ]),
    completedStartStepCount: ref(0),
    applicationProgressPercentage: ref(0),
    startStepValidationMessage: ref(""),
    selectApplicationType: vi.fn((id: string) => {
      journey.form.applicationTypeId = id;
    }),
    selectApplicationStep: vi.fn(),
    continueStartJourney: vi.fn(),
    loadStartOptions: vi.fn(),
  };
  vi.stubGlobal("useApplicationStartJourney", () => journey);
});

const render = () => mount(ApplicationStartPage, { global: { stubs } });

describe("compact application start form", () => {
  it("uses three labelled selects without promotional or explanatory panels", async () => {
    const page = render();
    expect(page.findAll("select")).toHaveLength(3);
    expect(page.findAll("h1")).toHaveLength(1);
    expect(page.text()).not.toMatch(
      /Build your application|Route directory|Prepare before|governed|Application type and intake/,
    );
    expect(page.get('select[aria-label="Intake"]').attributes("disabled")).toBeDefined();
    await page.get('select[aria-label="Application type"]').setValue("undergrad");
    expect(journey.selectApplicationType).toHaveBeenCalledWith("undergrad");
    expect(page.get('select[aria-label="Intake"]').attributes("disabled")).toBeUndefined();
    await page.get('select[aria-label="Intake"]').setValue("august");
    expect(journey.form.intakeId).toBe("august");
    await page.get("form").trigger("submit");
    expect(journey.continueStartJourney).toHaveBeenCalledOnce();
    page.unmount();
  });

  it("keeps validation, loading and unavailable routes visible", async () => {
    journey.loadingOptions.value = true;
    journey.applicationRouteCards.value = [];
    const page = render();
    expect(
      page.findAll("select").every((select) => select.attributes("disabled") !== undefined),
    ).toBe(true);
    journey.loadingOptions.value = false;
    journey.startOptions.value.routes = [];
    journey.attemptedCreate.value = true;
    journey.startStepValidationMessage.value =
      "Choose an applicant category, an open application type, and an intake.";
    journey.pageError.value = "Service unavailable";
    await flushPromises();
    expect(page.text()).toContain("No applications are currently open");
    expect(page.text()).toContain(journey.startStepValidationMessage.value);
    expect(page.text()).toContain("Service unavailable");
    await page
      .findAll("button")
      .find((button) => button.text() === "Check again")!
      .trigger("click");
    expect(journey.loadStartOptions).toHaveBeenCalledOnce();
    page.unmount();
  });

  it.each([true, false])("keeps the concise fee status when required=%s", (required) => {
    journey.selectedApplicationType.value = { fee: { required } };
    const page = render();
    expect(page.text()).toContain(required ? "Application fee required" : "No application fee");
    page.unmount();
  });

  it("requires sign-in before showing the form", async () => {
    journey.auth.authenticated.value = false;
    const page = render();
    expect(page.find("form").exists()).toBe(false);
    await page
      .findAll("button")
      .find((button) => button.text() === "Sign in")!
      .trigger("click");
    expect(journey.auth.login).toHaveBeenCalledWith("/applications/new");
    page.unmount();
  });
});
