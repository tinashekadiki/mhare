// Author: Tinashe K
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { defineComponent, ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import CorePage from "../../pages/operations/core.vue";
import {
  clickButton,
  operationalContext,
  operationalStubs,
  setField,
} from "../../../../tests/unit/support/operational-page";

const Register = defineComponent({
  props: ["title"],
  template: '<section><h2>{{ title }}</h2><slot name="actions"/><slot/></section>',
});
const Table = defineComponent({
  name: "GovernanceTable",
  props: ["rows", "columns", "state", "rowActions", "total"],
  emits: ["row-action", "update:state"],
  template:
    '<section class="register-table"><input aria-label="Search records" @input="$emit(\'update:state\', {...state, search:$event.target.value,page:1})"/><button @click="$emit(\'update:state\', {...state,sort:[{key:columns[0].key,direction:\'asc\'}]})">Sort ascending</button><button @click="$emit(\'update:state\', {...state,sort:[{key:columns[0].key,direction:\'desc\'}]})">Sort descending</button><span>{{ total }} records</span><article v-for="row in rows" :key="row.id"><span v-for="column in columns" :key="column.key"><slot :name="column.key+\'-cell\'" :row="row" :value="row[column.key]">{{ row[column.key] }}</slot></span><button v-for="action in rowActions" :key="action.id" @click="$emit(\'row-action\', {action,row})">{{ action.label }}</button></article></section>',
});
const Field = defineComponent({
  props: ["label", "name", "type", "modelValue", "items", "disabled", "readonly"],
  emits: ["update:modelValue"],
  components: {
    SelectControl: operationalStubs.USelect,
    InputControl: operationalStubs.UInput,
    CheckboxControl: operationalStubs.UCheckbox,
  },
  template:
    '<label class="field" :data-label="label">{{ label }}<SelectControl v-if="type===\'select\'||type===\'searchable-select\'" :model-value="modelValue" :items="items" :disabled="disabled" @update:model-value="$emit(\'update:modelValue\',$event)"/><CheckboxControl v-else-if="type===\'toggle\'" :model-value="modelValue" @update:model-value="$emit(\'update:modelValue\',$event)"/><InputControl v-else :model-value="modelValue" :readonly="readonly" :type="type===\'number\'?\'number\':\'text\'" @update:model-value="$emit(\'update:modelValue\',type===\'number\'?($event===\'\'?undefined:Number($event)):$event)"/></label>',
});
const Drawer = defineComponent({
  props: ["open", "title", "submitLabel", "submitDisabled", "busy"],
  emits: ["submit", "close", "update:open"],
  template:
    '<section v-if="open" role="dialog"><h2>{{ title }}</h2><slot/><button :disabled="submitDisabled" :aria-busy="busy" @click="$emit(\'submit\')">{{ submitLabel }}</button><button @click="$emit(\'close\');$emit(\'update:open\',false)">Cancel</button></section>',
});
let context: ReturnType<typeof operationalContext>;
let wrapper: VueWrapper;
const confirmAction = vi.fn(),
  showSuccess = vi.fn();
const role = {
  id: "role",
  code: "REGISTRY",
  name: "Registry",
  scope: "SYSTEM",
  systemManaged: false,
};
const permission = {
  id: "permission",
  code: "CORE_READ",
  name: "Read",
  category: "CORE",
  description: "Read core",
};
const country = {
  id: "country",
  iso2Code: "ZW",
  iso3Code: "ZWE",
  name: "Zimbabwe",
  nationalityName: "Zimbabwean",
};
const lookupSet = { id: "set", code: "TITLES", name: "Titles", description: "Managed titles" };
const value = {
  id: "value",
  lookupSetId: "set",
  code: "MR",
  name: "Mr",
  sortOrder: 0,
  active: true,
};
const subject = {
  id: "subject",
  code: "MAT",
  name: "Mathematics",
  subjectGroupCode: "SCIENCE",
  scienceSubject: true,
  mathematicsSubject: true,
  englishSubject: true,
  active: true,
  version: 4,
};
const grade = { id: "grade", grade: "A", points: 5, pass: true, sortOrder: 0, version: 3 };

beforeEach(() => {
  vi.resetAllMocks();
  context = operationalContext();
  vi.stubGlobal("shallowRef", ref);
  vi.stubGlobal("useEmhareAuth", () => ({
    hasPermission: () => true,
    hasRole: () => true,
    syncCoreUser: vi.fn(),
  }));
  vi.stubGlobal("useAcademicSetup", () => ({
    overview: ref({
      academicUnits: [
        {
          id: "unit",
          code: "SCI",
          name: "Science",
          academicUnitTypeCode: "FACULTY",
          status: "ACTIVE",
        },
      ],
    }),
    ensureOverview: vi.fn(),
  }));
  vi.stubGlobal("useEmhareConfirm", () => ({
    confirmAction,
    showSuccess,
    showError: context.showError,
  }));
  confirmAction.mockResolvedValue(true);
  context.request.mockImplementation(async (path: string, options?: { method?: string }) => {
    if (options?.method) return {};
    if (path === "/api/core/institution-profile") return null;
    if (path === "/api/core/statistics")
      return { userCount: 1, roleCount: 1, permissionCount: 1, lookupSetCount: 1 };
    if (path === "/api/core/users")
      return [
        {
          id: "user",
          username: "registry",
          displayName: "Registry Officer",
          email: "registry@example.test",
          status: "ACTIVE",
        },
      ];
    if (path === "/api/core/roles")
      return [
        role,
        { ...role, id: "academic", code: "ACADEMIC", name: "Academic", scope: "ACADEMIC_UNIT" },
      ];
    if (path === "/api/core/permissions") return [permission];
    if (path === "/api/core/countries") return [country];
    if (path === "/api/core/lookup-sets") return [lookupSet];
    if (path === "/api/core/lookup-sets/set/values")
      return [value, { ...value, id: "inactive", code: "MRS", name: "Mrs", active: false }];
    if (path.endsWith("/permissions"))
      return [
        {
          id: "grant",
          roleId: "role",
          permissionId: "permission",
          permissionName: "Read",
          permissionCode: "CORE_READ",
          category: "CORE",
        },
      ];
    if (path.endsWith("/role-assignments"))
      return [
        {
          id: "assignment",
          roleId: "academic",
          roleName: "Academic",
          roleCode: "ACADEMIC",
          academicUnitId: "unit",
          startsAt: "2026-08-01",
        },
        {
          id: "expired",
          roleId: "role",
          roleName: "Registry",
          roleCode: "REGISTRY",
          startsAt: "2026-08-01",
          endsAt: "2026-08-20",
        },
      ];
    if (path === "/api/admissions/qualification-reference-data/manage")
      return {
        oLevelSubjects: [
          subject,
          {
            ...subject,
            id: "inactive",
            scienceSubject: false,
            mathematicsSubject: false,
            englishSubject: false,
            active: false,
          },
        ],
        aLevelSubjects: [subject],
        oLevelGrades: [grade, { ...grade, id: "fail", grade: "U", points: null, pass: false }],
        aLevelGrades: [grade],
      };
    return [];
  });
});
afterEach(() => {
  wrapper?.unmount();
  vi.unstubAllGlobals();
});
async function render() {
  wrapper = mount(CorePage, {
    global: {
      stubs: {
        ...operationalStubs,
        UDashboardToolbar: defineComponent({ template: '<nav><slot name="left"/></nav>' }),
        EmhareRegisterPanel: Register,
        EmhareDataTable: Table,
        EmhareFormField: Field,
        EmhareRecordDrawer: Drawer,
        EmhareKpiCard: true,
        EmhareFormSection: Register,
        EmhareDescriptionList: true,
        USeparator: true,
        EmhareJourneyStepper: true,
      },
    },
  });
  await flushPromises();
}
async function navigate(tab: string, dataset?: string) {
  await clickButton(wrapper, tab);
  if (dataset) await clickButton(wrapper, dataset);
}
const datasets = [
  {
    tab: "RBAC",
    dataset: "Roles",
    create: "Create role",
    path: "/api/core/roles",
    id: "role",
    fields: { Code: "TEST", Name: "Test role" },
  },
  {
    tab: "RBAC",
    dataset: "Permissions",
    create: "Create permission",
    path: "/api/core/permissions",
    id: "permission",
    fields: { Code: "TEST", Name: "Test permission" },
  },
  {
    tab: "Reference Data",
    dataset: "Lookup sets",
    create: "Create lookup set",
    path: "/api/core/lookup-sets",
    id: "set",
    fields: { Code: "TEST", Name: "Test lookup" },
  },
  {
    tab: "Reference Data",
    dataset: "Lookup values",
    create: "Create country",
    path: "/api/core/countries",
    id: "country",
    fields: { ISO2: "ZA", ISO3: "ZAF", Name: "South Africa", Nationality: "South African" },
  },
  {
    tab: "Reference Data",
    dataset: "O Level subjects",
    create: "Create O Level subject",
    path: "/api/admissions/qualification-reference-data/subjects",
    id: "subject",
    fields: { "Subject code": "NEW", "Subject name": "New subject" },
  },
  {
    tab: "Reference Data",
    dataset: "A Level subjects",
    create: "Create A Level subject",
    path: "/api/admissions/qualification-reference-data/subjects",
    id: "subject",
    fields: { "Subject code": "NEW", "Subject name": "New subject" },
  },
  {
    tab: "Reference Data",
    dataset: "O Level grades",
    create: "Create O Level grade",
    path: "/api/admissions/qualification-reference-data/grades",
    id: "grade",
    fields: { Grade: "B" },
  },
  {
    tab: "Reference Data",
    dataset: "A Level grades",
    create: "Create A Level grade",
    path: "/api/admissions/qualification-reference-data/grades",
    id: "grade",
    fields: { Grade: "B" },
  },
];

describe("Core reference and access governance", () => {
  it.each(datasets)(
    "creates $dataset through its public form and correct service boundary",
    async (scenario) => {
      await render();
      await navigate(scenario.tab, scenario.dataset);
      await clickButton(wrapper, scenario.create);
      expect(wrapper.get('[role="dialog"] button').attributes("disabled")).toBeDefined();
      for (const [label, text] of Object.entries(scenario.fields))
        await setField(wrapper, label, text);
      await clickButton(wrapper, "Save record");
      expect(context.request).toHaveBeenCalledWith(
        scenario.path,
        expect.objectContaining({ method: "POST", body: expect.any(Object) }),
      );
      expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
      expect(showSuccess).toHaveBeenCalled();
    },
  );
  it.each(datasets)("edits $dataset without changing its identity", async (scenario) => {
    await render();
    await navigate(scenario.tab, scenario.dataset);
    await clickButton(wrapper, "Edit");
    expect(wrapper.get('[role="dialog"]').text()).toContain("Edit");
    await clickButton(wrapper, "Save record");
    const legacyUpsert = ["/api/core/countries", "/api/core/lookup-sets"].includes(scenario.path);
    expect(context.request).toHaveBeenCalledWith(
      legacyUpsert ? scenario.path : `${scenario.path}/${scenario.id}`,
      expect.objectContaining({ method: legacyUpsert ? "POST" : "PUT" }),
    );
  });
  it.each(datasets)(
    "confirms soft deletion of $dataset and preserves qualification versions",
    async (scenario) => {
      await render();
      await navigate(scenario.tab, scenario.dataset);
      confirmAction.mockResolvedValueOnce(false);
      await clickButton(wrapper, "Delete");
      expect(context.request.mock.calls.some(([, options]) => options?.method === "DELETE")).toBe(
        false,
      );
      await clickButton(wrapper, "Delete");
      const version = scenario.path.endsWith("subjects")
        ? "?expectedVersion=4"
        : scenario.path.endsWith("grades")
          ? "?expectedVersion=3"
          : "";
      expect(context.request).toHaveBeenCalledWith(`${scenario.path}/${scenario.id}${version}`, {
        method: "DELETE",
      });
    },
  );
  it.each(datasets)("resets $dataset forms when cancelled", async (scenario) => {
    await render();
    await navigate(scenario.tab, scenario.dataset);
    await clickButton(wrapper, "Edit");
    await clickButton(wrapper, "Cancel");
    await clickButton(wrapper, scenario.create);
    const firstLabel = Object.keys(scenario.fields)[0];
    expect(
      (
        wrapper.get(`[role="dialog"] [data-label="${firstLabel}"] input`)
          .element as HTMLInputElement
      ).value,
    ).toBe("");
  });
  it("maintains selected lookup values and their active flags", async () => {
    await render();
    await navigate("Reference Data", "Lookup values");
    await setField(wrapper, "Lookup set", "set");
    expect(wrapper.text()).toContain("Inactive");
    await clickButton(wrapper, "Create lookup value");
    await setField(wrapper, "Code", "DR");
    await setField(wrapper, "Name", "Doctor");
    await setField(wrapper, "Sort order", "2");
    await setField(wrapper, "Active", false);
    await clickButton(wrapper, "Save record");
    expect(context.request).toHaveBeenCalledWith("/api/core/lookup-sets/set/values", {
      method: "POST",
      body: expect.objectContaining({ code: "DR", name: "Doctor", sortOrder: 2, active: false }),
    });
    await clickButton(wrapper, "Edit");
    await clickButton(wrapper, "Save record");
    await clickButton(wrapper, "Delete");
    expect(context.request).toHaveBeenCalledWith("/api/core/lookup-values/value", {
      method: "DELETE",
    });
  });
  it("grants and revokes a permission for the selected role", async () => {
    await render();
    await navigate("RBAC", "Role grants");
    await clickButton(wrapper, "Grant permission");
    await setField(wrapper, "Permission", "permission");
    await clickButton(wrapper, "Grant permission", 1);
    expect(context.request).toHaveBeenCalledWith("/api/core/roles/role/permissions", {
      method: "POST",
      body: { permissionId: "permission" },
    });
    confirmAction.mockResolvedValueOnce(false);
    await clickButton(wrapper, "Revoke");
    await clickButton(wrapper, "Revoke");
    expect(context.request).toHaveBeenCalledWith("/api/core/roles/role/permissions/permission", {
      method: "DELETE",
    });
  });
  it("requires scope for academic assignments and removes it when switching to a system role", async () => {
    await render();
    await navigate("RBAC", "User assignments");
    await clickButton(wrapper, "Assign role");
    await setField(wrapper, "Role", "academic");
    expect(wrapper.get('[role="dialog"] button').attributes("disabled")).toBeDefined();
    await setField(wrapper, "Academic unit", "unit");
    await setField(wrapper, "Role", "role");
    await setField(wrapper, "Starts at", "2026-09-01");
    await clickButton(wrapper, "Assign role", 1);
    expect(context.request).toHaveBeenCalledWith("/api/core/users/user/role-assignments", {
      method: "POST",
      body: { roleId: "role", academicUnitId: undefined, startsAt: "2026-09-01" },
    });
    confirmAction.mockResolvedValueOnce(false);
    await clickButton(wrapper, "Expire assignment");
    await clickButton(wrapper, "Expire assignment");
    expect(context.request).toHaveBeenCalledWith(
      "/api/core/users/user/role-assignments/assignment",
      { method: "DELETE" },
    );
  });
  it("filters and sorts displayed records while retaining the matching total", async () => {
    await render();
    await navigate("RBAC", "Roles");
    await clickButton(wrapper, "Sort ascending");
    expect(wrapper.findAll(".register-table article")[0]!.text()).toContain("ACADEMIC");
    await clickButton(wrapper, "Sort descending");
    expect(wrapper.findAll(".register-table article")[0]!.text()).toContain("REGISTRY");
    await wrapper.get('[aria-label="Search records"]').setValue(" Academic ");
    expect(wrapper.text()).toContain("1 records");
    expect(wrapper.findAll(".register-table article")).toHaveLength(1);
  });
  it("keeps a failed save open and reports backend errors", async () => {
    await render();
    await navigate("RBAC", "Roles");
    await clickButton(wrapper, "Edit");
    context.request.mockRejectedValueOnce(new Error("Version conflict"));
    await clickButton(wrapper, "Save record");
    expect(context.showError).toHaveBeenCalledWith("Save failed", "Version conflict");
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true);
  });
});
