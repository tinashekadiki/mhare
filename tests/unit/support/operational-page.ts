// Author: Tinashe K
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import {
  computed,
  defineComponent,
  nextTick,
  onMounted,
  onBeforeUnmount,
  onUnmounted,
  reactive,
  ref,
  watch,
  watchEffect,
  type Component,
} from "vue";
import { vi } from "vitest";
import { useProgrammeStudyPeriod } from "../../../packages/portal-shell/composables/useProgrammeStudyPeriod";

const Container = defineComponent({
  props: ["title"],
  template:
    '<section><h2 v-if="title">{{ title }}</h2><slot name="header" /><slot name="body" /><slot name="right" /><slot /><slot name="footer" /></section>',
});
const Button = defineComponent({
  props: ["label", "loading", "disabled"],
  template:
    '<button type="button" :disabled="disabled" :aria-busy="loading">{{ label }}<slot /></button>',
});
const Select = defineComponent({
  props: ["modelValue", "items", "disabled"],
  emits: ["update:modelValue"],
  methods: {
    value(item: any) {
      return typeof item === "object" && item !== null ? item.value : item;
    },
    label(item: any) {
      return typeof item === "object" && item !== null ? item.label : item;
    },
  },
  template:
    '<select :disabled="disabled" :value="modelValue ?? \'\'" @change="$emit(\'update:modelValue\', value(items.find(item => String(value(item) ?? \'\') === $event.target.value)))"><option value="">Choose</option><option v-for="(item, index) in items" :key="index" :value="value(item) ?? \'\'">{{ label(item) }}</option></select>',
});
const Input = defineComponent({
  props: ["modelValue", "disabled", "type", "modelModifiers"],
  emits: ["update:modelValue"],
  template:
    '<input :type="type || \'text\'" :disabled="disabled" :value="modelValue" @input="$emit(\'update:modelValue\', modelModifiers?.number && $event.target.value !== \'\' ? Number($event.target.value) : $event.target.value)" />',
});
const Checkbox = defineComponent({
  props: ["modelValue", "disabled", "label"],
  emits: ["update:modelValue"],
  template:
    '<label>{{ label }}<input type="checkbox" :disabled="disabled" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked)" /></label>',
});
const Alert = defineComponent({
  props: ["title", "description", "color"],
  template: '<aside :data-color="color">{{ title }} {{ description }}<slot /></aside>',
});

export const operationalStubs = {
  UTabs: defineComponent({
    props: ["modelValue", "items"],
    emits: ["update:modelValue"],
    template:
      '<nav><button v-for="item in items" :key="item.value" @click="$emit(\'update:modelValue\', item.value)">{{ item.label }}</button></nav>',
  }),
  UDashboardPanel: Container,
  UDashboardNavbar: Container,
  UDashboardSidebarCollapse: true,
  UCard: Container,
  UButton: Button,
  EmhareGuidedActionButton: Button,
  USelect: Select,
  USelectMenu: Select,
  UInput: Input,
  UTextarea: Input,
  UCheckbox: Checkbox,
  USwitch: Checkbox,
  UAlert: Alert,
  UEmpty: Alert,
  UBadge: defineComponent({
    props: ["label", "color"],
    template: '<span :data-color="color">{{ label }}<slot /></span>',
  }),
  UFormField: defineComponent({
    props: ["label"],
    template: '<label class="field" :data-label="label">{{ label }}<slot /></label>',
  }),
  EmhareRecordDrawer: defineComponent({
    props: ["open", "title"],
    template:
      '<section v-if="open" role="dialog"><h2>{{ title }}</h2><slot name="body" /><slot /><slot name="footer" /></section>',
  }),
  UModal: defineComponent({
    props: ["open", "title"],
    template:
      '<section v-if="open" role="dialog"><h2>{{ title }}</h2><slot name="body" /><slot /><slot name="footer" /></section>',
  }),
  EmharePaginatedCollection: defineComponent({
    props: ["items"],
    template: '<section><slot :items="items" /></section>',
  }),
  EmhareStatusPill: defineComponent({
    props: ["label", "tone"],
    template: '<span :data-tone="tone">{{ label }}</span>',
  }),
  USkeleton: true,
  UIcon: true,
  NuxtLink: defineComponent({ props: ["to"], template: '<a :href="to"><slot /></a>' }),
};

export function operationalContext() {
  const request = vi.fn();
  const showError = vi.fn();
  const notify = vi.fn();
  const selectedAcademicPeriodId = ref<string | null>("period-current");
  const navigateTo = vi.fn();
  for (const [name, value] of Object.entries({
    computed,
    onMounted,
    onBeforeUnmount,
    onUnmounted,
    reactive,
    ref,
    watch,
    watchEffect,
    nextTick,
    useProgrammeStudyPeriod,
  }))
    vi.stubGlobal(name, value);
  vi.stubGlobal("definePageMeta", vi.fn());
  vi.stubGlobal("navigateTo", navigateTo);
  vi.stubGlobal("useEmhareApi", () => ({ request, errorMessage: (error: Error) => error.message }));
  vi.stubGlobal("useToast", () => ({ add: notify }));
  vi.stubGlobal("useEmhareConfirm", () => ({ showError }));
  vi.stubGlobal("useAcademicPeriodContext", () => ({
    selectedAcademicPeriodId,
    matchesAcademicPeriod: (record: { academicPeriodId?: string }) =>
      !selectedAcademicPeriodId.value ||
      !record.academicPeriodId ||
      record.academicPeriodId === selectedAcademicPeriodId.value,
  }));
  return { request, showError, notify, selectedAcademicPeriodId, navigateTo };
}

export async function renderOperationalPage(page: Component) {
  const wrapper = mount(page, { global: { stubs: operationalStubs } });
  await flushPromises();
  return wrapper;
}
export async function clickButton(wrapper: VueWrapper, label: string, index = 0) {
  const buttons = wrapper.findAll("button").filter((button) => button.text() === label);
  if (!buttons[index])
    throw new Error(
      `Button not found: ${label}. Available: ${wrapper
        .findAll("button")
        .map((button) => button.text())
        .join(", ")}`,
    );
  await buttons[index].trigger("click");
  await flushPromises();
}
export async function setField(
  wrapper: VueWrapper,
  label: string,
  value: string | boolean,
  index = 0,
) {
  const field = wrapper
    .findAll(".field")
    .filter((field) => field.attributes("data-label") === label)[index];
  if (!field) throw new Error(`Field not found: ${label}`);
  await field.get("input, select, textarea").setValue(value);
  await flushPromises();
}
