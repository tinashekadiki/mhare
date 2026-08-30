// Author: Tinashe K
import { mount, type VueWrapper } from "@vue/test-utils";
import { defineComponent, computed } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Field from "../../components/forms/EmhareFormField.vue";
const control = defineComponent({
  props: [
    "modelValue",
    "type",
    "placeholder",
    "disabled",
    "readonly",
    "items",
    "label",
    "icon",
    "min",
    "max",
    "step",
    "multiple",
    "range",
    "accept",
    "variant",
  ],
  emits: ["update:modelValue"],
  template:
    '<input :value="modelValue" :disabled="disabled" :readonly="readonly" :type="type || \'text\'" :placeholder="placeholder" @input="$emit(\'update:modelValue\',$event.target.value)"/>',
});
const controls = [
  "UInput",
  "UInputNumber",
  "UTextarea",
  "USelect",
  "USelectMenu",
  "URadioGroup",
  "UCheckbox",
  "USwitch",
  "UInputDate",
  "UInputTime",
  "UFileUpload",
  "UInputMenu",
  "UInputTags",
];
const stubs = {
  ...Object.fromEntries(controls.map((name) => [name, defineComponent({ ...control, name })])),
  UFormField: defineComponent({
    props: ["name", "label", "description", "hint", "required", "error"],
    template: "<label>{{label}}<slot/><small>{{description}} {{hint}}</small></label>",
  }),
};
let wrapper: VueWrapper;
beforeEach(() => {
  vi.stubGlobal("computed", computed);
});
afterEach(() => {
  wrapper?.unmount();
  vi.unstubAllGlobals();
});
describe("common form field contracts", () => {
  it("passes inline errors to the accessible Nuxt form field", () => {
    wrapper = mount(Field, {
      props: { label: "Full name", error: "Full name is required." },
      global: { stubs },
    });
    expect(wrapper.findComponent(stubs.UFormField).props("error")).toBe("Full name is required.");
  });
  it.each([
    ["text", "UInput", "text"],
    ["phone", "UInput", "tel"],
    ["email", "UInput", "email"],
    ["password", "UInput", "password"],
    ["currency", "UInput", "text"],
    ["percentage", "UInput", "text"],
    ["address", "UInput", "text"],
    ["number", "UInputNumber", undefined],
    ["textarea", "UTextarea", undefined],
    ["rich-text", "UTextarea", undefined],
    ["select", "USelect", undefined],
    ["searchable-select", "USelectMenu", undefined],
    ["multi-select", "USelectMenu", undefined],
    ["radio", "URadioGroup", undefined],
    ["checkbox", "UCheckbox", undefined],
    ["toggle", "USwitch", undefined],
    ["date", "UInput", "date"],
    ["date-range", "UInputDate", undefined],
    ["time", "UInputTime", undefined],
    ["file", "UFileUpload", undefined],
    ["drop-file", "UFileUpload", undefined],
    ["autocomplete", "UInputMenu", undefined],
    ["tags", "UInputTags", undefined],
  ] as const)("maps %s to %s while forwarding model updates", async (type, name, inputType) => {
    wrapper = mount(Field, {
      props: {
        type,
        label: "Evidence",
        modelValue: "initial",
        items: [{ label: "A", value: "a" }],
        description: "Guidance",
        hint: "Optional",
      },
      global: { stubs },
    });
    const child = wrapper.getComponent({ name });
    expect(child.props("modelValue")).toBe("initial");
    if (inputType) expect(child.props("type")).toBe(inputType);
    child.vm.$emit("update:modelValue", "changed");
    expect(wrapper.emitted("update:modelValue")).toEqual([["changed"]]);
    expect(wrapper.text()).toContain("Guidance");
    await wrapper.setProps({ disabled: true, readonly: true });
    expect(child.props("disabled")).toBe(true);
  });
  it("uses items ahead of options and normalizes empty select placeholders", async () => {
    wrapper = mount(Field, {
      props: { type: "select", label: "Programme Level", options: ["UG"], placeholder: "   " },
      global: { stubs },
    });
    expect(wrapper.getComponent({ name: "USelect" }).props("items")).toEqual(["UG"]);
    expect(wrapper.getComponent({ name: "USelect" }).props("placeholder")).toBe(
      "Select programme level",
    );
    await wrapper.setProps({ items: ["PG"], placeholder: " Choose route " });
    expect(wrapper.getComponent({ name: "USelect" }).props("items")).toEqual(["PG"]);
    expect(wrapper.getComponent({ name: "USelect" }).props("placeholder")).toBe("Choose route");
  });
  it("exposes colour picker and text entry as the same value", async () => {
    wrapper = mount(Field, {
      props: { type: "color", label: "Primary", modelValue: "#006633" },
      global: { stubs },
    });
    const inputs = wrapper.findAllComponents({ name: "UInput" });
    expect(inputs).toHaveLength(2);
    expect(inputs[0]!.props("type")).toBe("color");
    expect(inputs[1]!.props("placeholder")).toBe("#006633");
    inputs[1]!.vm.$emit("update:modelValue", "#001f6e");
    expect(wrapper.emitted("update:modelValue")).toEqual([["#001f6e"]]);
    await wrapper.setProps({ placeholder: "#ffffff" });
    expect(inputs[1]!.props("placeholder")).toBe("#ffffff");
  });
  it("retains numeric bounds and selection/file options", async () => {
    wrapper = mount(Field, {
      props: { type: "number", label: "Amount", modelValue: 20, min: 0, max: 100, step: 0.01 },
      global: { stubs },
    });
    expect(wrapper.getComponent({ name: "UInputNumber" }).props()).toEqual(
      expect.objectContaining({ modelValue: 20, min: 0, max: 100, step: 0.01 }),
    );
    await wrapper.setProps({ type: "drop-file", multiple: true, accept: "image/png" });
    expect(wrapper.getComponent({ name: "UFileUpload" }).props()).toEqual(
      expect.objectContaining({ variant: "area", multiple: true, accept: "image/png" }),
    );
    await wrapper.setProps({ type: "file" });
    expect(wrapper.getComponent({ name: "UFileUpload" }).props("variant")).toBe("button");
    await wrapper.setProps({ type: "checkbox", placeholder: "Accept terms" });
    expect(wrapper.getComponent({ name: "UCheckbox" }).props("label")).toBe("Accept terms");
  });
});
