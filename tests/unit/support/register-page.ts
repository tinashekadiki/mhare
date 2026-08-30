// Author: Tinashe K
import { defineComponent } from "vue";
import type { Ref } from "vue";
import { vi } from "vitest";
import { useAcademicPeriodContext } from "../../../packages/portal-shell/composables/useAcademicPeriodContext";
import { operationalStubs } from "./operational-page";

export function installRegisterPeriodContext(selectedAcademicPeriodId: Ref<string | null>) {
  vi.stubGlobal("useCookie", () => selectedAcademicPeriodId);
  vi.stubGlobal("useAcademicSetup", () => ({
    overview: { value: { academicPeriods: [{ id: "period-current", code: "2026-S1" }] } },
    ensureOverview: vi.fn(),
  }));
  vi.stubGlobal("useAcademicPeriodContext", useAcademicPeriodContext);
}

export const RegisterTable = defineComponent({
  props: ["rows", "columns", "state", "rowActions", "total"],
  emits: ["row-action", "update:state"],
  template: `<section class="register-table">
    <input aria-label="Search records" @input="$emit('update:state', {...state, search:$event.target.value,page:1})" />
    <button @click="$emit('update:state', {...state,sort:[{key:columns[0].key,direction:'asc'}]})">Sort ascending</button>
    <button @click="$emit('update:state', {...state,sort:[{key:columns[0].key,direction:'desc'}]})">Sort descending</button>
    <button @click="$emit('update:state', {...state,page:state.page+1})">Next page</button>
    <span>{{ total }} records</span>
    <article v-for="row in rows" :key="row.id" :data-record-id="row.id">
      <span v-for="column in columns" :key="column.key"><slot :name="column.key+'-cell'" :row="row" :value="row[column.key]">{{ row[column.key] }}</slot></span>
      <button v-for="action in rowActions" :key="action.id" @click="$emit('row-action', {action,row})">{{ action.label }}</button>
    </article>
  </section>`,
});

export const RegisterField = defineComponent({
  props: ["label", "name", "type", "modelValue", "items", "disabled", "readonly"],
  emits: ["update:modelValue"],
  components: {
    SelectControl: operationalStubs.USelect,
    InputControl: operationalStubs.UInput,
    CheckboxControl: operationalStubs.UCheckbox,
  },
  template: `<label class="field" :data-label="label">{{ label }}
    <SelectControl v-if="type==='select'||type==='searchable-select'" :model-value="modelValue" :items="items" :disabled="disabled" @update:model-value="$emit('update:modelValue',$event)" />
    <CheckboxControl v-else-if="type==='toggle'" :model-value="modelValue" @update:model-value="$emit('update:modelValue',$event)" />
    <input v-else-if="type==='drop-file'" type="file" @change="$emit('update:modelValue',Array.from($event.target.files))" />
    <InputControl v-else :model-value="modelValue" :disabled="disabled" :readonly="readonly" :type="type==='number'?'number':'text'" @update:model-value="$emit('update:modelValue',type==='number'?($event===''?undefined:Number($event)):$event)" />
  </label>`,
});

export const RegisterDrawer = defineComponent({
  props: ["open", "title", "submitLabel", "submitDisabled", "busy"],
  emits: ["submit", "close", "update:open"],
  template: `<section v-if="open" role="dialog"><h2>{{ title }}</h2><slot name="body"/><slot/><slot name="footer"/>
    <button :disabled="submitDisabled" :aria-busy="busy" @click="$emit('submit')">{{ submitLabel || 'Save' }}</button>
    <button @click="$emit('close');$emit('update:open',false)">Cancel</button>
  </section>`,
});

export const PaginatedRegisterTable = defineComponent({
  props: ["data", "columns"],
  template: `<section><article v-for="record in data" :key="record.id" :data-record-id="record.id"><span v-for="column in columns" :key="column.accessorKey || column.id"><slot :name="(column.accessorKey || column.id)+'-cell'" :row="{original:record}">{{record[column.accessorKey]}}</slot></span></article><slot v-if="!data.length" name="empty"/></section>`,
});

export const registerStubs = {
  ...operationalStubs,
  UDashboardToolbar: defineComponent({
    template: '<div><slot name="left"/><slot name="right"/><slot/></div>',
  }),
  EmhareRegisterPanel: defineComponent({
    props: ["title"],
    template: '<section><h2>{{ title }}</h2><slot name="actions"/><slot/></section>',
  }),
  EmhareKpiCard: defineComponent({
    props: ["label", "value"],
    template: "<p>{{ label }}: {{ value }}</p>",
  }),
  EmhareDataTable: RegisterTable,
  EmharePaginatedTable: PaginatedRegisterTable,
  EmhareFormField: RegisterField,
  EmhareRecordDrawer: RegisterDrawer,
  EmhareDescriptionList: defineComponent({
    props: ["items"],
    template:
      '<dl><template v-for="item in items" :key="item.label"><dt>{{ item.label }}</dt><dd>{{ item.value }}</dd></template></dl>',
  }),
};
