// Author: Tinashe K
import { mount, type VueWrapper } from "@vue/test-utils";
import { computed, defineComponent, reactive, ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import EmhareDataTable from "../../components/tables/EmhareDataTable.vue";
import type { EmhareDataTableState } from "../../types/emhare-ui";

const Button = defineComponent({
  props: ["label"],
  template: '<button type="button">{{ label }}<slot /></button>',
});
const Input = defineComponent({
  props: ["modelValue"],
  emits: ["update:modelValue"],
  template:
    '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
});
const Checkbox = defineComponent({
  props: ["modelValue"],
  emits: ["update:modelValue"],
  template:
    '<input type="checkbox" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked)" />',
});
const Dropdown = defineComponent({
  props: ["items"],
  template:
    '<section class="menu"><slot /><button v-for="item in items" :key="item.label" type="button" @click="item.onSelect()">{{ item.label }}</button></section>',
});
const Select = defineComponent({
  props: ["modelValue", "items"],
  emits: ["update:modelValue"],
  template:
    '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><option v-for="item in items" :key="item.value" :value="item.value">{{ item.label }}</option></select>',
});
const rows = [
  { id: "first", name: "Alice", credits: 12, notes: "Reviewed" },
  { id: "second", name: "Brian", credits: 18, notes: "Pending" },
];
const columns = [
  { key: "name", label: "Student", sortable: true, frozen: true },
  { key: "credits", label: "Credits", total: true, align: "right" as const },
  { key: "notes", label: "Notes", hidden: true, editable: true, align: "center" as const },
];
let wrapper: VueWrapper;

function render(props: Record<string, unknown> = {}) {
  wrapper = mount(EmhareDataTable, {
    props: { columns, rows, total: 26, state: { page: 3, pageSize: 10 }, ...props },
    global: {
      stubs: {
        UButton: Button,
        EmhareGuidedActionButton: Button,
        UInput: Input,
        UCheckbox: Checkbox,
        UDropdownMenu: Dropdown,
        USelect: Select,
        UPagination: true,
        UIcon: true,
        USkeleton: true,
        UAlert: defineComponent({
          props: ["title", "description"],
          template: '<aside role="alert">{{ title }} {{ description }}</aside>',
        }),
      },
    },
  });
  return wrapper;
}

function latestState() {
  return wrapper.emitted("update:state")!.at(-1)![0] as EmhareDataTableState;
}

function menu(label: string) {
  return wrapper.findAll(".menu").find((item) => item.find("button").text() === label)!;
}

async function choose(menuLabel: string, itemLabel: string) {
  await menu(menuLabel)
    .findAll("button")
    .find((item) => item.text() === itemLabel)!
    .trigger("click");
}

beforeEach(() => {
  vi.stubGlobal("computed", computed);
  vi.stubGlobal("reactive", reactive);
  vi.stubGlobal("ref", ref);
});
afterEach(() => {
  wrapper?.unmount();
  vi.unstubAllGlobals();
});

describe("shared operational data table", () => {
  it("shows default columns, totals and server pagination without exposing hidden fields", () => {
    render();
    expect(wrapper.findAll("thead th").map((item) => item.text())).toEqual([
      "",
      "Student",
      "Credits",
    ]);
    expect(wrapper.get("tbody").text()).toContain("Alice");
    expect(wrapper.get("tbody").text()).not.toContain("Reviewed");
    expect(wrapper.get("tfoot").text()).toContain("30");
    expect(wrapper.get("[data-emhare-pagination]").text()).toContain("26 total · Page 3 of 3");
  });

  it("resets pagination for search and page-size changes while preserving filters and selection", async () => {
    const state = {
      page: 3,
      pageSize: 10,
      selectedKeys: ["first"],
      filters: [{ key: "status", value: "ACTIVE" }],
    };
    render({ state });
    await wrapper.get('input[placeholder="Search"]').setValue("Alice");
    expect(latestState()).toEqual({ ...state, search: "Alice", page: 1 });
    await wrapper.get('select[aria-label="Rows per page"]').setValue("25");
    expect(latestState()).toEqual({ ...state, page: 1, pageSize: 25 });
    wrapper.findComponent({ name: "UPagination" }).vm.$emit("update:page", 2);
    expect(latestState()).toEqual({ ...state, page: 2 });
    wrapper.findAllComponents(Input)[0]!.vm.$emit("update:modelValue", null);
    expect(latestState().search).toBe("");
  });

  it("toggles sortable columns in both directions and ignores non-sortable headers", async () => {
    render();
    const headers = wrapper.findAll("thead button");
    await headers[1]!.trigger("click");
    expect(wrapper.emitted("update:state")).toBeUndefined();
    await headers[0]!.trigger("click");
    expect(latestState().sort).toEqual([{ key: "name", direction: "asc" }]);
    await wrapper.setProps({ state: latestState() });
    expect(wrapper.findComponent({ name: "UIcon" }).attributes("name")).toBe("i-lucide-arrow-up");
    await headers[0]!.trigger("click");
    expect(latestState().sort).toEqual([{ key: "name", direction: "desc" }]);
    await wrapper.setProps({ state: latestState() });
    expect(wrapper.findComponent({ name: "UIcon" }).attributes("name")).toBe("i-lucide-arrow-down");
    await headers[0]!.trigger("click");
    expect(latestState().sort).toEqual([{ key: "name", direction: "asc" }]);
  });

  it("changes column visibility and emits independent draft cell edits", async () => {
    render();
    await choose("Columns", "Notes");
    expect(latestState().visibleColumns).toEqual(["name", "credits", "notes"]);
    await wrapper.setProps({ state: latestState() });
    const edits = wrapper.findAll("tbody input:not([type=checkbox])");
    await edits[0]!.setValue("Evidence verified");
    await edits[1]!.setValue("Awaiting transcript");
    await edits[0]!.setValue("");
    expect(wrapper.emitted("inline-edit")).toEqual([
      [{ row: rows[0], key: "notes", value: "Evidence verified" }],
      [{ row: rows[1], key: "notes", value: "Awaiting transcript" }],
      [{ row: rows[0], key: "notes", value: "" }],
    ]);
    expect((edits[0]!.element as HTMLInputElement).value).toBe("");
    expect(rows[0]!.notes).toBe("Reviewed");
    await choose("Columns", "Credits");
    await wrapper.setProps({ state: latestState() });
    expect(wrapper.find("tfoot").exists()).toBe(false);
  });

  it("selects and deselects individual rows without dropping off-page selection", async () => {
    render({ state: { page: 1, pageSize: 10, selectedKeys: ["off-page"] } });
    const first = wrapper.findAll('input[aria-label="Select row"]')[0]!;
    await first.setValue(true);
    expect(latestState().selectedKeys).toEqual(["off-page", "first"]);
    await wrapper.setProps({ state: latestState() });
    await first.setValue(false);
    expect(latestState().selectedKeys).toEqual(["off-page"]);
  });

  it("selects only the current page and sends only loaded selected rows to bulk actions", async () => {
    const action = { id: "review", label: "Review selected" };
    render({
      state: { page: 1, pageSize: 10, selectedKeys: ["off-page", "first"] },
      bulkActions: [action],
    });
    await wrapper.get('input[aria-label="Select all rows"]').setValue(true);
    expect(latestState().selectedKeys).toEqual(["off-page", "first", "second"]);
    await wrapper.setProps({ state: latestState() });
    await choose("Bulk actions", "Review selected");
    expect(wrapper.emitted("bulk-action")).toEqual([[{ action, selectedRows: rows }]]);
    await wrapper.get('input[aria-label="Select all rows"]').setValue(false);
    expect(latestState().selectedKeys).toEqual(["off-page"]);
  });

  it("emits saved views, current view, all export formats and the chosen row action", async () => {
    const view = { id: "active", label: "Active students", state: { page: 1, pageSize: 25 } };
    const action = { id: "open", label: "Open student", tone: "primary" };
    render({ savedViews: [view], rowActions: [action] });
    await choose("Views", "Active students");
    await choose("Views", "Save current view");
    expect(wrapper.emitted("saved-view-apply")).toEqual([[view]]);
    expect(wrapper.emitted("saved-view-create")).toEqual([[{ page: 3, pageSize: 10 }]]);
    for (const label of ["CSV", "Excel", "PDF", "Print"]) await choose("Export", label);
    expect(wrapper.emitted("export")!.map((event) => (event[0] as { id: string }).id)).toEqual([
      "csv",
      "excel",
      "pdf",
      "print",
    ]);
    const rowMenu = wrapper.findAll("tbody .menu")[1]!;
    await rowMenu.findAll("button")[1]!.trigger("click");
    expect(wrapper.emitted("row-action")).toEqual([[{ action, row: rows[1] }]]);
  });

  it("expands and collapses the chosen custom-key row", async () => {
    render({ expandable: true, rowKey: "name" });
    await wrapper.findAll('button[aria-label="Expand row"]')[1]!.trigger("click");
    expect(wrapper.get("pre").text()).toContain("Brian");
    expect(wrapper.get("pre").text()).not.toContain("Alice");
    await wrapper.get('button[aria-label="Collapse row"]').trigger("click");
    expect(wrapper.find("pre").exists()).toBe(false);
  });

  it("ignores missing and nonnumeric totals and displays loading, error and empty states", async () => {
    render({
      rows: [...rows, { id: "missing" }, { id: "invalid", credits: "not rated" }],
      loading: true,
      error: "Service unavailable",
    });
    expect(wrapper.findAllComponents({ name: "USkeleton" })).toHaveLength(5);
    expect(wrapper.get('[role="alert"]').text()).toContain("Service unavailable");
    expect(wrapper.get("tfoot").text()).toContain("30");
    await wrapper.setProps({
      rows: [],
      total: 0,
      loading: false,
      error: undefined,
      state: { page: 1, pageSize: 10, visibleColumns: [] },
    });
    expect(wrapper.text()).toContain("No records found");
    expect(wrapper.text()).toContain("Page 1 of 1");
    expect(
      (wrapper.get('input[aria-label="Select all rows"]').element as HTMLInputElement).checked,
    ).toBe(false);
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
  });
});
