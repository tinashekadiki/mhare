// Author: Tinashe K

import { mount } from "@vue/test-utils";
import { computed, nextTick, onMounted, onBeforeUnmount, ref, watch } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import EmhareJourneyStepper from "../../components/forms/EmhareJourneyStepper.vue";
import EmhareVerticalStepper from "../../components/forms/EmhareVerticalStepper.vue";
import EmharePublicGatewaySlider from "../../components/public/EmharePublicGatewaySlider.vue";

const sweetAlertFire = vi.fn();

vi.mock("sweetalert2", () => ({ default: { fire: sweetAlertFire } }));
vi.stubGlobal("computed", computed);
vi.stubGlobal("ref", ref);
vi.stubGlobal("watch", watch);
vi.stubGlobal("nextTick", nextTick);
vi.stubGlobal("onMounted", onMounted);
vi.stubGlobal("onBeforeUnmount", onBeforeUnmount);

const globalComponents = {
  stubs: {
    UIcon: { props: ["name"], template: '<span class="icon" :data-name="name" />' },
    NuxtLink: { props: ["to"], template: '<a :href="to"><slot /></a>' },
  },
};

const steps = [
  {
    id: "complete",
    title: "Complete",
    description: "Finished step",
    icon: "i-lucide-check",
    status: "complete" as const,
    required: true,
  },
  { id: "current", title: "Current", icon: "i-lucide-circle", status: "current" as const },
  { id: "attention", title: "Attention", status: "attention" as const },
  { id: "pending", title: "Pending", status: "pending" as const },
  { id: "disabled", title: "Disabled", disabled: true },
];

describe("release gate branch regressions", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    document.body.innerHTML = "";
    sweetAlertFire.mockResolvedValue({ isConfirmed: true });
  });

  it("renders and selects every journey and vertical step state", async () => {
    const journey = mount(EmhareJourneyStepper, {
      props: { steps, currentStep: "current" },
      global: globalComponents,
    });
    expect(journey.get("nav").attributes("aria-label")).toBe("Journey progress");
    expect(journey.text()).toContain("Finished step");
    await journey.findAll("button")[2]!.trigger("click");
    await journey.findAll("button")[4]!.trigger("click");
    expect(journey.emitted("update:currentStep")).toEqual([["attention"]]);

    const vertical = mount(EmhareVerticalStepper, {
      props: { steps, currentStep: "current", label: "Application journey" },
      global: globalComponents,
    });
    expect(vertical.get("nav").attributes("aria-label")).toBe("Application journey");
    const verticalButtons = vertical.findAll("button");
    await verticalButtons[0]!.trigger("click");
    await verticalButtons[4]!.trigger("click");
    expect(vertical.emitted("update:currentStep")).toEqual([["complete"]]);
  });

  it("covers empty, single, linked, direct-select, wraparound, and shrinking slider states", async () => {
    const wrapper = mount(EmharePublicGatewaySlider, {
      props: { slides: [] },
      global: globalComponents,
    });
    expect(wrapper.find("section").exists()).toBe(false);

    await wrapper.setProps({
      slides: [{ image: "/one.webp", alternativeText: "First image" }],
    });
    expect(wrapper.find('button[aria-label="Show next image"]').exists()).toBe(false);

    await wrapper.setProps({
      slides: [
        {
          image: "/one.webp",
          alternativeText: "First image",
          title: "First update",
          href: "/news/first",
        },
        {
          image: "/two.webp",
          alternativeText: "Second image",
          title: "Second update",
          href: "/news/second",
          actionLabel: "Read announcement",
        },
      ],
    });
    const track = wrapper.get("section > div");
    await wrapper.get('button[aria-label="Show previous image"]').trigger("click");
    expect(track.attributes("style")).toContain("translateX(-100%)");
    await wrapper.get('button[aria-label="Show image 1"]').trigger("click");
    expect(track.attributes("style")).toContain("translateX(-0%)");
    await wrapper.get('button[aria-label="Show next image"]').trigger("click");
    await wrapper.setProps({
      slides: [{ image: "/one.webp", alternativeText: "First image", title: "First update" }],
    });
    await nextTick();
    expect(track.attributes("style")).toContain("translateX(-0%)");
  });

  it("uses the active dialog for confirmations and escapes multiline guidance", async () => {
    const dialog = document.createElement("div");
    dialog.setAttribute("role", "dialog");
    dialog.setAttribute("data-state", "open");
    const button = document.createElement("button");
    dialog.append(button);
    document.body.append(dialog);
    button.focus();

    const { useEmhareConfirm } = await import("../../composables/useEmhareConfirm");
    const confirmations = useEmhareConfirm();
    expect(await confirmations.confirmAction({ title: "Delete?", destructive: true })).toBe(true);
    expect(sweetAlertFire).toHaveBeenLastCalledWith(
      expect.objectContaining({ target: dialog, icon: "warning", confirmButtonColor: "#dc2626" }),
    );

    dialog.classList.add("swal2-popup");
    await confirmations.confirmAction({ title: "Continue?", icon: "info" });
    expect(sweetAlertFire).toHaveBeenLastCalledWith(
      expect.objectContaining({ target: "body", icon: "info" }),
    );
    await confirmations.showSuccess("Saved", "The record is ready.");
    await confirmations.showError("Invalid", "• First <item>\n\n• Second & item");
    expect(sweetAlertFire).toHaveBeenLastCalledWith(
      expect.objectContaining({ html: expect.stringContaining("&lt;item&gt;") }),
    );
    await confirmations.showError("Offline", "Try again");
    expect(sweetAlertFire).toHaveBeenLastCalledWith(expect.objectContaining({ text: "Try again" }));
    await confirmations.showActionGuidance({
      title: "Complete prerequisites",
      description: "Review <requirements>",
      instructions: ["Upload & verify", "Submit the form"],
      actionLabel: "Open application",
    });
    expect(sweetAlertFire).toHaveBeenLastCalledWith(
      expect.objectContaining({
        showCancelButton: true,
        html: expect.stringContaining("Review &lt;requirements&gt;"),
      }),
    );
    await confirmations.showActionGuidance({ title: "Done", instructions: [] });
    expect(sweetAlertFire).toHaveBeenLastCalledWith(
      expect.objectContaining({ showCancelButton: false, confirmButtonText: "Understood" }),
    );
  });
});
