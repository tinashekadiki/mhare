// Author: Tinashe K
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { defineComponent, ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import ApplicationTypes from "../../pages/operations/application-types.vue";
import { campusStubs } from "../../../../tests/unit/support/campus-page";
import {
  clickButton,
  operationalContext,
  setField,
} from "../../../../tests/unit/support/operational-page";

const SelectMenu = defineComponent({
  props: ["modelValue", "items", "multiple"],
  emits: ["update:modelValue"],
  methods: {
    changed(event: Event) {
      const target = event.target as HTMLSelectElement;
      this.$emit(
        "update:modelValue",
        this.multiple !== undefined && this.multiple !== false
          ? Array.from(target.selectedOptions, (option) => option.value)
          : target.value,
      );
    },
  },
  template:
    '<select :multiple="multiple !== undefined && multiple !== false" :value="modelValue" @change="changed"><option v-if="multiple === undefined" value="">Choose</option><option v-for="item in items" :key="item.value" :value="item.value">{{ item.label }}</option></select>',
});
const stubs = {
  ...campusStubs,
  USelectMenu: SelectMenu,
  EmhareFeedbackState: campusStubs.UAlert,
  EmharePaginatedTable: defineComponent({
    props: ["data", "columns"],
    template:
      '<section class="records"><article v-for="record in data" :key="record.id" :data-record="record.id"><span v-for="column in columns" :key="column.accessorKey || column.id"><slot :name="(column.accessorKey || column.id)+\'-cell\'" :row="{original:record}">{{ record[column.accessorKey] }}</slot></span></article><slot v-if="!data.length" name="empty"/></section>',
  }),
};
let context: ReturnType<typeof operationalContext>;
let wrapper: VueWrapper;
const overview = ref<any>(null);
const ensureOverview = vi.fn();
let records: any[];
let fees: any[];
let configuration: any;
const typeFixture = (overrides = {}) => ({
  id: "undergrad",
  code: "UG",
  name: "Undergraduate",
  requiresEmploymentHistory: false,
  requiresReferees: false,
  financeFeeStructureId: "fee",
  financeFeeStructureCode: "APP",
  financeFeeStructureName: "Application fee",
  active: true,
  version: 8,
  ...overrides,
});
const programme = {
  id: "programme",
  code: "BSC",
  name: "Science",
  programmeLevelName: "Bachelor",
  owningAcademicUnitName: "Science faculty",
  status: "ACTIVE",
};
const routeFixture = (overrides = {}) => ({
  applicationTypeId: "undergrad",
  code: "UG",
  name: "Undergraduate",
  active: true,
  readyForActivation: true,
  readinessBlockers: [],
  activeProgrammeCount: 1,
  programmes: [{ programmeId: "programme", programmeCode: "BSC", programmeName: "Science" }],
  sections: [
    {
      code: "PERSONAL_DETAILS",
      name: "Personal details",
      repeatable: false,
      required: true,
      minimumRecords: 1,
      sortOrder: 10,
    },
    {
      code: "REFEREES",
      name: "Referees",
      repeatable: true,
      required: false,
      minimumRecords: 0,
      sortOrder: 20,
    },
  ],
  requiredDocumentCount: 1,
  documents: [
    {
      code: "ID",
      name: "Identity",
      required: true,
      sortOrder: 30,
      captureSectionCode: "PERSONAL_DETAILS",
      applicantCategoryCodes: ["LOCAL"],
    },
  ],
  feePolicyStatus: "FEE_STRUCTURE",
  version: 11,
  ...overrides,
});
beforeEach(() => {
  context = operationalContext();
  overview.value = {
    programmes: [{ ...programme }, { ...programme, id: "inactive", status: "DRAFT" }],
  };
  ensureOverview.mockReset().mockResolvedValue(undefined);
  vi.stubGlobal("useAcademicSetup", () => ({ overview, ensureOverview }));
  records = [
    typeFixture(),
    typeFixture({
      id: "postgrad",
      code: "PG",
      name: "Postgraduate",
      active: false,
      requiresEmploymentHistory: true,
      requiresReferees: true,
      financeFeeStructureId: null,
      financeFeeStructureCode: null,
      financeFeeStructureName: null,
    }),
  ];
  fees = [
    {
      id: "fee",
      code: "APP",
      name: "Application fee",
      feeContext: "APPLICATION",
      status: "ACTIVE",
      programmeLevelCode: "UG",
      applicantCategoryCode: "LOCAL",
    },
    { id: "retired", code: "OLD", name: "Old fee", feeContext: "APPLICATION", status: "RETIRED" },
    { id: "tuition", code: "TUITION", name: "Tuition", feeContext: "TUITION", status: "ACTIVE" },
  ];
  configuration = routeFixture();
  context.request.mockImplementation(async (path: string, options?: any) => {
    if (options?.method)
      return path.endsWith("route-configuration")
        ? { ...configuration, active: options.body.activate }
        : records[0];
    if (path.endsWith("route-configuration")) return configuration;
    if (path === "/api/admissions/application-types") return records;
    if (path === "/api/finance/fee-structures") return { structures: fees };
    throw new Error(`Unexpected request ${path}`);
  });
});
afterEach(() => {
  wrapper?.unmount();
  vi.unstubAllGlobals();
});
async function render() {
  wrapper = mount(ApplicationTypes, { global: { stubs } });
  await flushPromises();
  return wrapper;
}
async function toggle(label: string, enabled: boolean) {
  const control = wrapper.get(`[aria-label="${label}"]`);
  await (control.element.tagName === "INPUT" ? control : control.get("input")).setValue(enabled);
  await flushPromises();
}
function writes() {
  return context.request.mock.calls.filter(([, options]) => options?.method);
}
async function validType() {
  await setField(wrapper, "Code", " diploma_1 ");
  await setField(wrapper, "Name", " Diploma route ");
}
async function configure() {
  await render();
  await clickButton(wrapper, "Configure");
}
async function routeReason() {
  await setField(wrapper, "Change reason", "  Approved route review  ");
}

describe("application-type governance through public form controls", () => {
  it("renders counts, fee snapshots, requirements and combined status/search filters", async () => {
    records.push(
      typeFixture({
        id: "snapshot",
        code: "SNAP",
        name: "Archived snapshot",
        financeFeeStructureId: "missing",
        requiresReferees: true,
      }),
    );
    await render();
    expect(wrapper.text()).toContain("Application types: 3");
    expect(wrapper.text()).toContain("With additional sections: 2");
    expect(wrapper.text()).toContain("UG · ACTIVE · LOCAL");
    expect(wrapper.text()).toContain("Stored Finance snapshot");
    expect(wrapper.text()).toContain("Standard sections");
    await wrapper.get('[placeholder="Search code or name"]').setValue(" postgraduate ");
    expect(wrapper.findAll("[data-record]")).toHaveLength(1);
    await wrapper.get('[aria-label="Filter by status"]').setValue("ACTIVE");
    expect(wrapper.text()).toContain("Adjust the search or status filter.");
    await wrapper.get('[placeholder="Search code or name"]').setValue("ug");
    expect(wrapper.get("[data-record]").attributes("data-record")).toBe("undergrad");
    await wrapper.get('[aria-label="Filter by status"]').setValue("INACTIVE");
    await wrapper.get('[placeholder="Search code or name"]').setValue("");
    expect(wrapper.get("[data-record]").attributes("data-record")).toBe("postgrad");
  });
  it("shows initial empty guidance and renders fee details without category", async () => {
    records = [];
    await render();
    expect(wrapper.text()).toContain(
      "Create the first application type before opening applications.",
    );
    records = [typeFixture()];
    fees[0].applicantCategoryCode = null;
    await clickButton(wrapper, "Refresh");
    expect(wrapper.text()).toContain("UG · ACTIVE");
  });
  it("recovers register loading failures on refresh", async () => {
    context.request.mockRejectedValueOnce(new Error("Admissions offline"));
    await render();
    expect(wrapper.text()).toContain("Admissions offline");
    await clickButton(wrapper, "Refresh");
    expect(wrapper.text()).not.toContain("Application types unavailable");
    expect(wrapper.findAll("[data-record]")).toHaveLength(2);
  });
  it("blocks blank, malformed and unnamed route creation even on form submit", async () => {
    await render();
    await clickButton(wrapper, "New application type");
    await wrapper.get("#application-type-form").trigger("submit");
    expect(writes()).toHaveLength(0);
    await setField(wrapper, "Code", "invalid code!");
    await setField(wrapper, "Name", "New route");
    await wrapper.get("#application-type-form").trigger("submit");
    expect(writes()).toHaveLength(0);
    await setField(wrapper, "Code", "VALID");
    await setField(wrapper, "Name", "  ");
    expect(
      wrapper
        .findAll("button")
        .find((button) => button.text() === "Create application type")!
        .attributes("disabled"),
    ).toBeDefined();
  });
  it.each([true, false])(
    "creates a normalized inactive type with fee selection=%s",
    async (linked) => {
      await render();
      await clickButton(wrapper, "New application type");
      await validType();
      const options = wrapper.get('[data-label="Fee structure"]').text();
      expect(options).not.toContain("Old fee");
      expect(options).not.toContain("Tuition");
      if (linked) await setField(wrapper, "Fee structure", "fee");
      await toggle("Require employment history", true);
      await toggle("Require referees", true);
      await clickButton(wrapper, "Create application type");
      expect(writes()[0]).toEqual([
        "/api/admissions/application-types",
        {
          method: "POST",
          body: {
            code: "DIPLOMA_1",
            name: "Diploma route",
            requiresEmploymentHistory: true,
            requiresReferees: true,
            financeFeeStructureId: linked ? "fee" : null,
            financeFeeStructureCode: linked ? "APP" : null,
            financeFeeStructureName: linked ? "Application fee" : null,
            active: false,
          },
        },
      ]);
      expect(context.notify).toHaveBeenCalledWith(
        expect.objectContaining({
          title: "Application type created",
          description: "The route remains inactive until it is activated.",
        }),
      );
      expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    },
  );
  it.each([true, false])(
    "preserves locked code/version and active=%s in audited edits",
    async (active) => {
      records[0].active = active;
      if (!active) records[0].financeFeeStructureId = null;
      await render();
      await clickButton(wrapper, "Edit");
      expect(wrapper.get('[data-label="Code"] input').attributes("disabled")).toBeDefined();
      await setField(wrapper, "Change reason", "short");
      await wrapper.get("#application-type-form").trigger("submit");
      expect(writes()).toHaveLength(0);
      await setField(wrapper, "Name", "  Renamed route  ");
      await routeReason();
      await clickButton(wrapper, "Save changes");
      expect(writes()[0]).toEqual([
        "/api/admissions/application-types/undergrad",
        {
          method: "PUT",
          body: expect.objectContaining({
            name: "Renamed route",
            expectedVersion: 8,
            active,
            changeReason: "Approved route review",
          }),
        },
      ]);
      expect(writes()[0]![1].body).not.toHaveProperty("code");
      expect(context.notify).toHaveBeenCalledWith(
        expect.objectContaining({
          title: "Application type updated",
          description: active
            ? "The route is available to applicants."
            : "The route remains inactive until it is activated.",
        }),
      );
    },
  );
  it.each(["created", "updated"])(
    "keeps recoverable form after type cannot be %s",
    async (action) => {
      fees = [];
      await render();
      await clickButton(wrapper, action === "created" ? "New application type" : "Edit");
      expect(wrapper.text()).toContain("No application fee structures found");
      if (action === "created") await validType();
      else await routeReason();
      context.request.mockRejectedValueOnce(new Error("Version conflict"));
      await clickButton(wrapper, action === "created" ? "Create application type" : "Save changes");
      expect(context.showError).toHaveBeenCalledWith(
        `Application type could not be ${action}`,
        "Version conflict",
      );
      expect(wrapper.find('[role="dialog"]').exists()).toBe(true);
    },
  );
  it("resets an abandoned edit when creating a new route", async () => {
    await render();
    await clickButton(wrapper, "Edit");
    await clickButton(wrapper, "Cancel");
    await clickButton(wrapper, "New application type");
    expect((wrapper.get('[data-label="Code"] input').element as HTMLInputElement).value).toBe("");
    expect(wrapper.find('[data-label="Change reason"]').exists()).toBe(false);
  });
  it("loads programme mappings, section thresholds and ready fee-governed configuration", async () => {
    await configure();
    expect(ensureOverview).toHaveBeenCalledOnce();
    expect(wrapper.text()).toContain("Route configuration is ready");
    expect(wrapper.text()).toContain("Finance fee structure linked");
    expect(wrapper.get('[aria-label="Programme mappings"]').findAll("option")).toHaveLength(1);
    await setField(wrapper, "Minimum records", "2");
    await toggle("Require Referees", true);
    await routeReason();
    await setField(wrapper, "Document code", " identity ");
    await setField(wrapper, "Applicant-facing name", " Identity proof ");
    await setField(wrapper, "Capture section", "QUALIFICATIONS");
    await wrapper
      .get('[data-label="Applicant categories"] select')
      .setValue(["SADC", "INTERNATIONAL"]);
    await clickButton(wrapper, "Save route configuration");
    expect(writes()[0]).toEqual([
      "/api/admissions/application-types/undergrad/route-configuration",
      {
        method: "PUT",
        body: {
          programmes: [
            { programmeId: "programme", programmeCode: "BSC", programmeName: "Science" },
          ],
          sections: [
            configuration.sections[0],
            { ...configuration.sections[1], required: true, minimumRecords: 2 },
          ],
          documents: [
            {
              code: "IDENTITY",
              name: "Identity proof",
              required: true,
              sortOrder: 10,
              captureSectionCode: "QUALIFICATIONS",
              applicantCategoryCodes: ["SADC", "INTERNATIONAL"],
            },
          ],
          feeFree: false,
          feeFreeReason: null,
          activate: true,
          changeReason: "Approved route review",
          expectedVersion: 11,
        },
      },
    ]);
    expect(context.notify).toHaveBeenCalledWith(
      expect.objectContaining({ title: "Application route activated" }),
    );
  });
  it("supports standard document defaults, category scopes and audited fee-free inactive saves", async () => {
    configuration = routeFixture({
      documents: [],
      feePolicyStatus: "UNCONFIGURED",
      active: false,
      readyForActivation: false,
      readinessBlockers: ["Fee decision missing", "Evidence review required"],
    });
    await configure();
    expect(wrapper.text()).toContain("Fee decision missing · Evidence review required");
    expect(wrapper.findAll('[data-label="Document code"]')).toHaveLength(3);
    await routeReason();
    await wrapper.get("#application-route-configuration-form").trigger("submit");
    expect(writes()).toHaveLength(0);
    await toggle("Record fee-free decision", true);
    await setField(wrapper, "Fee-free reason", "short");
    await wrapper.get("#application-route-configuration-form").trigger("submit");
    expect(writes()).toHaveLength(0);
    await setField(wrapper, "Fee-free reason", "  Approved scholarship route  ");
    await clickButton(wrapper, "Save route configuration");
    expect(writes()[0]![1].body).toMatchObject({
      feeFree: true,
      feeFreeReason: "Approved scholarship route",
      activate: false,
      documents: [
        expect.objectContaining({ code: "NATIONAL_ID", applicantCategoryCodes: ["LOCAL"] }),
        expect.objectContaining({ code: "BIRTH_CERTIFICATE", applicantCategoryCodes: ["LOCAL"] }),
        expect.objectContaining({
          code: "PASSPORT",
          applicantCategoryCodes: ["SADC", "INTERNATIONAL"],
        }),
      ],
    });
    expect(context.notify).toHaveBeenCalledWith(
      expect.objectContaining({
        title: "Route configuration saved",
        description: "Undergraduate remains inactive.",
      }),
    );
  });
  it("maps historical document defaults and requires renewed fee-free audit evidence", async () => {
    configuration = routeFixture({
      feePolicyStatus: "FEE_FREE",
      documents: [{ code: "OLD", name: "Historical proof", required: true, sortOrder: 100 }],
    });
    await configure();
    expect(wrapper.find('[data-label="Fee-free reason"]').exists()).toBe(true);
    await routeReason();
    await setField(wrapper, "Fee-free reason", "Policy reconfirmed by Finance");
    await clickButton(wrapper, "Save route configuration");
    expect(writes()[0]![1].body.documents).toEqual([
      {
        code: "OLD",
        name: "Historical proof",
        required: true,
        sortOrder: 10,
        captureSectionCode: "SUPPORTING_DOCUMENTS",
        applicantCategoryCodes: [],
      },
    ]);
  });
  it("blocks missing programme/document evidence and supports removing and replacing requirements", async () => {
    configuration.programmes = [];
    await configure();
    await routeReason();
    await wrapper.get("#application-route-configuration-form").trigger("submit");
    expect(writes()).toHaveLength(0);
    await wrapper.get('[aria-label="Programme mappings"]').setValue(["programme"]);
    await wrapper.get('[aria-label="Remove document"]').trigger("click");
    await clickButton(wrapper, "Add document");
    await wrapper.get("#application-route-configuration-form").trigger("submit");
    expect(writes()).toHaveLength(0);
    await setField(wrapper, "Document code", " result ");
    await wrapper.get("#application-route-configuration-form").trigger("submit");
    expect(writes()).toHaveLength(0);
    await setField(wrapper, "Applicant-facing name", " Results ");
    await toggle("Require  Results ", false);
    await wrapper.get("#application-route-configuration-form").trigger("submit");
    expect(writes()).toHaveLength(0);
    await toggle("Require  Results ", true);
    await toggle("Activate application route", false);
    await clickButton(wrapper, "Save route configuration");
    expect(writes()[0]![1].body.documents).toEqual([
      {
        code: "RESULT",
        name: "Results",
        required: true,
        sortOrder: 10,
        captureSectionCode: "SUPPORTING_DOCUMENTS",
        applicantCategoryCodes: [],
      },
    ]);
  });
  it("prevents stale programme mapping when Academic Setup withdraws a programme", async () => {
    await configure();
    await routeReason();
    overview.value = { programmes: [] };
    await flushPromises();
    expect(wrapper.text()).toContain("No active Programmes available");
    await clickButton(wrapper, "Save route configuration");
    expect(writes()).toHaveLength(0);
    expect(context.showError).toHaveBeenCalledWith(
      "Route configuration could not be saved",
      "A selected Programme is no longer active in Academic Setup.",
    );
  });
  it("handles absent overview, configuration fetch failures and save errors", async () => {
    overview.value = null;
    await configure();
    expect(wrapper.text()).toContain("No active Programmes available");
    await clickButton(wrapper, "Cancel");
    context.request.mockRejectedValueOnce(new Error("Route unavailable"));
    await clickButton(wrapper, "Configure");
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    expect(context.showError).toHaveBeenCalledWith(
      "Route configuration could not be loaded",
      "Route unavailable",
    );
    overview.value = { programmes: [programme] };
    await clickButton(wrapper, "Configure");
    await routeReason();
    context.request.mockRejectedValueOnce(new Error("Readiness changed"));
    await clickButton(wrapper, "Save route configuration");
    expect(context.showError).toHaveBeenCalledWith(
      "Route configuration could not be saved",
      "Readiness changed",
    );
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true);
  });
  it("shows loading configuration before the route response arrives", async () => {
    await render();
    let resolve!: (value: any) => void;
    context.request.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    await clickButton(wrapper, "Configure");
    expect(wrapper.text()).toContain("Loading route configuration");
    resolve(configuration);
    await flushPromises();
    expect(wrapper.text()).toContain("Configure Undergraduate");
  });
});
