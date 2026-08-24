// Author: Tinashe K

import { mount } from "@vue/test-utils";
import { computed, defineComponent, reactive, ref, watch } from "vue";
import { describe, expect, it } from "vitest";
import EmhareIdentityNameMismatchPanel from "../../components/domain/admissions/EmhareIdentityNameMismatchPanel.vue";

Object.assign(globalThis, { computed, reactive, ref, watch });

const buttonStub = defineComponent({
  props: ["label", "disabled"],
  emits: ["click"],
  template: '<button :disabled="disabled" @click="$emit(\'click\')">{{ label }}</button>',
});

const formFieldStub = defineComponent({
  props: ["modelValue", "label"],
  emits: ["update:modelValue"],
  template:
    "<label>{{ label }}<input :value=\"modelValue ?? ''\" @input=\"$emit('update:modelValue', $event.target.value)\" /></label>",
});

const badgeStub = defineComponent({
  props: ["label"],
  template: "<span>{{ label }}</span>",
});

const correction = {
  id: "correction-1",
  applicationId: "application-1",
  documentId: "document-1",
  registeredName: { firstName: "Emmanuel", middleNames: null, lastName: "Small" },
  documentName: { firstName: "Tinashe", middleNames: null, lastName: "Kadiki" },
  status: "UNRESOLVED" as const,
  requestReason: null,
  requestedAt: null,
  requestedByUserId: null,
  decisionReason: null,
  decidedAt: null,
  decidedByUserId: null,
  coreSynchronizedAt: null,
  version: 0,
};

function mountPanel(overrides: Record<string, unknown> = {}) {
  return mount(EmhareIdentityNameMismatchPanel, {
    props: { correction, ...overrides },
    global: {
      stubs: {
        UIcon: true,
        UBadge: badgeStub,
        UButton: buttonStub,
        EmhareFormField: formFieldStub,
      },
    },
  });
}

describe("EmhareIdentityNameMismatchPanel", () => {
  it("compares protected and document names and exposes all applicant actions", async () => {
    const wrapper = mountPanel();

    expect(wrapper.text()).toContain("Identity name mismatch");
    expect(wrapper.text()).toContain("Review needed");
    expect(wrapper.text()).toContain("Emmanuel Small");
    expect(wrapper.text()).toContain("Tinashe Kadiki");
    await wrapper.get("button:nth-of-type(1)").trigger("click");
    expect(wrapper.emitted("replace")).toHaveLength(1);

    await wrapper.findAll("button")[1]!.trigger("click");
    expect(wrapper.text()).toContain("Save corrected OCR reading");
    await wrapper
      .findAll("button")
      .find((button) => button.text() === "Save corrected OCR reading")!
      .trigger("click");
    expect(wrapper.emitted("corrected")?.[0]?.[0]).toEqual(correction.documentName);
  });

  it("keeps a pending request visible while allowing the draft to continue", () => {
    const wrapper = mountPanel({
      correction: {
        ...correction,
        status: "REQUESTED",
        requestReason: "My registered surname is incomplete.",
      },
    });

    expect(wrapper.text()).toContain("awaiting staff approval");
    expect(wrapper.text()).toContain("continue completing this draft");
    expect(wrapper.text()).toContain("My registered surname is incomplete.");
  });

  it("exposes staff approval actions only for a requested correction", async () => {
    const wrapper = mountPanel({
      mode: "staff",
      correction: { ...correction, status: "REQUESTED" },
    });

    expect(wrapper.text()).toContain("Approve official-name correction");
    await wrapper.findAll("button")[0]!.trigger("click");
    await wrapper.findAll("button")[1]!.trigger("click");
    expect(wrapper.emitted("approve")).toHaveLength(1);
    expect(wrapper.emitted("reject")).toHaveLength(1);
  });
});
