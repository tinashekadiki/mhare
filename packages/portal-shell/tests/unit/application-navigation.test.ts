// Author: Tinashe K
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { computed, h, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Stepper from "../../components/forms/EmhareVerticalStepper.vue";
import Collection from "../../components/tables/EmharePaginatedCollection.vue";
let wrapper: VueWrapper;
const scrollTo = vi.fn();
beforeEach(() => {
  for (const [name, value] of Object.entries({
    computed,
    nextTick,
    onBeforeUnmount,
    onMounted,
    ref,
    watch,
  }))
    vi.stubGlobal(name, value);
  vi.spyOn(HTMLElement.prototype, "scrollTo").mockImplementation(scrollTo);
});
afterEach(() => {
  wrapper?.unmount();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  scrollTo.mockClear();
});
describe("applicant navigation components", () => {
  it("keeps the current mobile step in view on selection and viewport changes", async () => {
    wrapper = mount(Stepper, {
      props: {
        steps: [
          { id: "route", title: "Route", disabled: true, status: "complete" },
          { id: "details", title: "Details", required: true, description: "Details" },
          { id: "kin", title: "Next of kin", status: "attention" },
        ],
        currentStep: "details",
      },
      global: { stubs: { UIcon: true } },
    });
    expect(scrollTo).toHaveBeenCalled();
    await wrapper.findAll("button")[0]!.trigger("click");
    expect(wrapper.emitted("update:currentStep")).toBeUndefined();
    await wrapper.findAll("button")[2]!.trigger("click");
    expect(wrapper.emitted("update:currentStep")).toEqual([["kin"]]);
    await wrapper.setProps({ currentStep: "kin" });
    await flushPromises();
    expect(wrapper.find('ol button[aria-current="step"]').text()).toBe("Next of kin");
    const calls = scrollTo.mock.calls.length;
    window.dispatchEvent(new Event("resize"));
    expect(scrollTo.mock.calls.length).toBe(calls + 1);
    await wrapper.setProps({ currentStep: "unknown" });
    await flushPromises();
    expect(scrollTo.mock.calls.length).toBe(calls + 1);
  });
  it("can hide pagination for short applicant lists without changing the records", async () => {
    const select = {
      props: ["modelValue", "items"],
      emits: ["update:modelValue"],
      template: "<span />",
    };
    const pagination = {
      props: ["page", "itemsPerPage", "total"],
      emits: ["update:page"],
      template: "<span />",
    };
    wrapper = mount(Collection, {
      props: { items: [1, 2, 3, 4, 5, 6], initialPageSize: 5, showPagination: false },
      slots: { default: ({ items }) => h("p", items.join(",")) },
      global: { stubs: { USelect: select, UPagination: pagination } },
    });
    expect(wrapper.get("p").text()).toBe("1,2,3,4,5");
    expect(wrapper.find("[data-emhare-pagination]").exists()).toBe(false);
    await wrapper.setProps({ showPagination: true });
    wrapper.findComponent(pagination).vm.$emit("update:page", 2);
    await flushPromises();
    expect(wrapper.get("p").text()).toBe("6");
    wrapper.findComponent(select).vm.$emit("update:modelValue", 10);
    await flushPromises();
    expect(wrapper.get("p").text()).toBe("1,2,3,4,5,6");
    await wrapper.setProps({ items: [] });
    expect(wrapper.get("p").text()).toBe("");
  });
});
