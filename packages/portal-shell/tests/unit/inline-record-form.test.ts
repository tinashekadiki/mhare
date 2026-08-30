// Author: Tinashe K
import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import InlineRecordForm from "../../components/forms/EmhareInlineRecordForm.vue";

const stubs = {
  UButton: {
    props: ["label", "disabled", "loading"],
    template: '<button :disabled="disabled || loading">{{label}}</button>',
  },
};
describe("inline record form", () => {
  it("uses the page actions when embedded and keeps necessary guidance", () => {
    const form = mount(InlineRecordForm, {
      props: { title: "Contact", embedded: true, description: "Saving sends an email." },
      slots: { default: '<input aria-label="Name" />' },
      global: { stubs },
    });
    expect(form.find("header").exists()).toBe(false);
    expect(form.find("footer").exists()).toBe(false);
    expect(form.find("input").exists()).toBe(true);
    expect(form.text()).toContain("Saving sends an email.");
    form.unmount();
  });
  it("retains standalone save and cancel actions for existing consumers", async () => {
    const form = mount(InlineRecordForm, {
      props: { title: "Contact", showCancel: true },
      global: { stubs },
    });
    await form.findAll("button")[0]!.trigger("click");
    await form.findAll("button")[1]!.trigger("click");
    expect(form.emitted("cancel")).toHaveLength(1);
    expect(form.emitted("submit")).toHaveLength(1);
    await form.setProps({ busy: true });
    expect(
      form.findAll("button").every((button) => button.attributes("disabled") !== undefined),
    ).toBe(true);
    await form.setProps({ busy: false, submitDisabled: true });
    expect(form.findAll("button")[1]!.attributes("disabled")).toBeDefined();
    form.unmount();
  });
});
