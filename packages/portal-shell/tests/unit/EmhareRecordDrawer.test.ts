// Author: Tinashe K
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { computed, defineComponent, nextTick, onBeforeUnmount, ref, useId, watch } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Drawer from "../../components/forms/EmhareRecordDrawer.vue";
const Button = defineComponent({
  props: ["label", "disabled"],
  template: '<button :disabled="disabled">{{label}}</button>',
});
const GuidedButton = defineComponent({
  name: "GuidedButton",
  props: ["label", "disabled", "guidanceInstructions", "loading"],
  template:
    '<button :disabled="disabled" :data-guidance="guidanceInstructions.join(\' \')">{{label}}</button>',
});
const Slideover = defineComponent({
  name: "Slideover",
  props: ["open", "title", "description", "dismissible", "close", "ui"],
  emits: ["update:open"],
  template:
    '<aside v-if="open"><h2>{{title}}</h2><p>{{description}}</p><slot name="body"/><slot name="footer"/></aside>',
});
const stubs = {
  USlideover: Slideover,
  UButton: Button,
  EmhareGuidedActionButton: GuidedButton,
  UContainer: defineComponent({ template: "<div><slot/></div>" }),
};
let wrapper: VueWrapper;
let host: HTMLElement;
let route: HTMLElement;
let workspace: HTMLElement;
let trigger: HTMLButtonElement;
beforeEach(() => {
  for (const [name, value] of Object.entries({
    computed,
    nextTick,
    onBeforeUnmount,
    ref,
    useId,
    watch,
  }))
    vi.stubGlobal(name, value);
  host = document.createElement("div");
  route = document.createElement("div");
  workspace = document.createElement("div");
  trigger = document.createElement("button");
  route.id = "emhare-route-content";
  workspace.id = "emhare-main-workspace";
  trigger.textContent = "Open record";
  route.append(trigger);
  host.append(route, workspace);
  document.body.append(host);
});
afterEach(() => {
  wrapper?.unmount();
  host.remove();
  vi.unstubAllGlobals();
});
async function render(presentation: "page" | "sidepanel" = "sidepanel", extra = {}) {
  wrapper = mount(Drawer, {
    attachTo: host,
    props: { open: false, title: "Governed record", presentation, ...extra },
    slots: { default: '<input aria-label="Record name"/>' },
    global: { stubs },
  });
  trigger.focus();
  await wrapper.setProps({ open: true });
  await flushPromises();
}
function button(label: string) {
  const match = [...host.querySelectorAll<HTMLButtonElement>("button")].find(
    (element) => element.textContent === label,
  );
  if (!match) throw new Error(`Missing button ${label}`);
  return match;
}
describe("shared record drawer accessibility and action contract", () => {
  it.each([
    ["md", "30rem"],
    ["lg", "38rem"],
    ["xl", "52rem"],
    ["wide", "64rem"],
  ] as const)("applies %s sidepanel width without outside dismissal", async (width, expected) => {
    await render("sidepanel", { width, description: "Approval evidence" });
    const panel = wrapper.getComponent(Slideover);
    expect(panel.props("ui").content).toContain(expected);
    expect(panel.props("dismissible")).toBe(false);
    expect(panel.props("close")).toBe(true);
    expect(wrapper.text()).toContain("Approval evidence");
    expect(route.hasAttribute("aria-hidden")).toBe(false);
    expect(document.activeElement).toBe(trigger);
  });
  it("emits controlled open and close separately for the sidepanel", async () => {
    await render();
    const panel = wrapper.getComponent(Slideover);
    panel.vm.$emit("update:open", true);
    expect(wrapper.emitted("close")).toBeUndefined();
    panel.vm.$emit("update:open", false);
    expect(wrapper.emitted("update:open")).toEqual([[true], [false]]);
    expect(wrapper.emitted("close")).toEqual([[]]);
  });
  it.each(["page", "sidepanel"] as const)(
    "supports submit, cancellation and explicit back events in %s presentation",
    async (presentation) => {
      await render(presentation, {
        showBack: true,
        backLabel: "Previous step",
        submitLabel: "Approve",
      });
      button("Previous step").click();
      button("Approve").click();
      button("Cancel").click();
      await flushPromises();
      expect(wrapper.emitted("back")).toEqual([[]]);
      expect(wrapper.emitted("submit")).toEqual([[]]);
      expect(wrapper.emitted("close")).toEqual([[]]);
      expect(wrapper.emitted("update:open")).toEqual([[false]]);
    },
  );
  it.each(["page", "sidepanel"] as const)(
    "prevents interaction while busy in %s presentation",
    async (presentation) => {
      await render(presentation, { busy: true, showBack: true, submitDisabled: true });
      for (const label of ["Back", "Save", "Cancel"]) {
        expect(button(label).disabled).toBe(true);
        button(label).click();
      }
      expect(wrapper.emitted("submit")).toBeUndefined();
      expect(wrapper.emitted("close")).toBeUndefined();
      expect(wrapper.emitted("back")).toBeUndefined();
      expect(button("Save").dataset.guidance).toBe("");
      if (presentation === "sidepanel")
        expect(wrapper.getComponent(Slideover).props("close")).toBe(false);
    },
  );
  it.each(["page", "sidepanel"] as const)(
    "passes actionable incomplete-field guidance in %s presentation",
    async (presentation) => {
      await render(presentation, {
        submitDisabled: true,
        submitDisabledReason: "Record an authority reference.",
      });
      expect(button("Save").dataset.guidance).toBe("Record an authority reference.");
      await wrapper.setProps({ submitDisabled: false });
      expect(button("Save").dataset.guidance).toBe("");
    },
  );
  it("moves focus into a labelled page workspace and restores the originating control on close", async () => {
    await render("page", { description: "Enter a reason" });
    const region = workspace.querySelector('[role="region"]')!;
    const titleId = region.getAttribute("aria-labelledby")!;
    const descriptionId = region.getAttribute("aria-describedby")!;
    expect(document.getElementById(titleId)?.textContent).toBe("Governed record");
    expect(document.getElementById(descriptionId)?.textContent).toBe("Enter a reason");
    expect(route.classList.contains("invisible")).toBe(true);
    expect(route.getAttribute("aria-hidden")).toBe("true");
    expect(document.activeElement).toBe(button("Back"));
    button("Back").click();
    expect(wrapper.emitted("close")).toEqual([[]]);
    await wrapper.setProps({ open: false });
    expect(workspace.querySelector('[role="region"]')).toBeNull();
    expect(route.classList.contains("invisible")).toBe(false);
    expect(route.hasAttribute("aria-hidden")).toBe(false);
    expect(document.activeElement).toBe(trigger);
  });
  it("restores route visibility even if presentation changes in the close update", async () => {
    await render("page");
    await wrapper.setProps({ open: false, presentation: "sidepanel" });
    expect(route.hasAttribute("aria-hidden")).toBe(false);
    expect(document.activeElement).toBe(trigger);
  });
  it("restores route visibility when an open page drawer unmounts", async () => {
    await render("page");
    wrapper.unmount();
    expect(route.hasAttribute("aria-hidden")).toBe(false);
    expect(route.classList.contains("invisible")).toBe(false);
  });
  it("does not focus a removed originating control or require route content to exist", async () => {
    route.remove();
    await render("page");
    await wrapper.setProps({ open: false });
    expect(workspace.querySelector('[role="region"]')).toBeNull();
    expect(trigger.isConnected).toBe(false);
  });
  it("omits absent descriptions from the accessible region", async () => {
    await render("page");
    expect(workspace.querySelector('[role="region"]')!.hasAttribute("aria-describedby")).toBe(
      false,
    );
  });
  it.each(["page", "sidepanel"] as const)(
    "honours custom body and footer slots in %s presentation",
    async (presentation) => {
      wrapper = mount(Drawer, {
        attachTo: host,
        props: { open: false, title: "Custom workflow", presentation },
        slots: {
          body: "<p>Owned body</p>",
          default: "<p>Unused fallback</p>",
          footer: "<button>Custom action</button>",
        },
        global: { stubs },
      });
      await wrapper.setProps({ open: true });
      await flushPromises();
      expect(host.textContent).toContain("Owned body");
      expect(host.textContent).toContain("Custom action");
      expect(host.textContent).not.toContain("Unused fallback");
      expect(host.textContent).not.toContain("Save");
    },
  );
});
