// Author: Tinashe K
import { flushPromises, mount } from "@vue/test-utils";
import { defineComponent, type Component } from "vue";
import { operationalStubs } from "./operational-page";

export const campusStubs = {
  ...operationalStubs,
  UDropdownMenu: defineComponent({
    props: ["items"],
    template:
      '<div><slot/><button v-for="item in items.flat()" :key="item.label" @click="item.onSelect()">{{ item.label }}</button></div>',
  }),
  EmhareRegisterPanel: defineComponent({
    props: ["title"],
    template: '<section><h2>{{ title }}</h2><slot name="actions"/><slot/></section>',
  }),
  EmhareKpiCard: defineComponent({
    props: ["label", "value"],
    template: "<output>{{ label }}: {{ value }}</output>",
  }),
  EmharePaginatedTable: defineComponent({
    props: ["data", "columns"],
    template:
      '<section class="records"><article v-for="record in data" :key="record.id" :data-record="record.id"><span v-for="column in columns" :key="column.accessorKey"><slot :name="column.accessorKey+\'-cell\'" :row="{original:record}">{{ record[column.accessorKey] }}</slot></span></article></section>',
  }),
  EmhareRecordDrawer: defineComponent({
    props: ["open", "title", "submitLabel", "submitDisabled", "busy"],
    emits: ["submit", "close", "update:open"],
    template:
      '<section v-if="open" role="dialog"><h2>{{ title }}</h2><slot name="body"/><slot/><slot name="footer"/><button :disabled="submitDisabled" :aria-busy="busy" @click="$emit(\'submit\')">{{ submitLabel || \'Save record\' }}</button><button @click="$emit(\'close\');$emit(\'update:open\',false)">Cancel</button></section>',
  }),
  UCheckboxGroup: defineComponent({
    props: ["modelValue", "items"],
    emits: ["update:modelValue"],
    template:
      '<div><label v-for="item in items" :key="item.value">{{ item.label }}<input type="checkbox" :aria-label="item.label" :checked="modelValue.includes(item.value)" @change="$emit(\'update:modelValue\', $event.target.checked ? [...modelValue,item.value] : modelValue.filter(value=>value!==item.value))"/></label></div>',
  }),
};

export async function mountCampusPage(page: Component) {
  const wrapper = mount(page, { global: { stubs: campusStubs } });
  await flushPromises();
  return wrapper;
}
