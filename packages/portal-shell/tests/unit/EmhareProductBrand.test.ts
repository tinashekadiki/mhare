// Author: Tinashe K

import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import EmhareProductBrand from "../../components/brand/EmhareProductBrand.vue";

describe("EmhareProductBrand", () => {
  it("uses the compact emblem with app context in operational headers", async () => {
    const wrapper = mount(EmhareProductBrand, {
      props: {
        label: "eMhare Student",
        description: "Academic self-service",
      },
    });

    expect(wrapper.get("img").attributes("src")).toBe("/images/brand/emhare-emblem-blue.png");
    expect(wrapper.get("img").attributes("alt")).toBe("");
    expect(wrapper.text()).toContain("eMhare Student");
    expect(wrapper.text()).toContain("Academic self-service");

    await wrapper.setProps({ showCopy: false });
    expect(wrapper.get("img").attributes("alt")).toBe("eMhare Student");
    expect(wrapper.text()).not.toContain("Academic self-service");
  });

  it("selects the full blue-gold and light wordmarks for secondary powered-by treatment", async () => {
    const wrapper = mount(EmhareProductBrand, {
      props: {
        appearance: "wordmark",
        tone: "blue-gold",
        poweredBy: true,
      },
    });

    expect(wrapper.text()).toContain("Powered by");
    expect(wrapper.get("img").attributes("src")).toBe(
      "/images/brand/emhare-wordmark-blue-gold.png",
    );
    expect(wrapper.get("img").attributes("alt")).toBe("eMhare University Information System");

    await wrapper.setProps({ tone: "light" });
    expect(wrapper.get("img").attributes("src")).toBe("/images/brand/emhare-wordmark-light.png");

    await wrapper.setProps({ tone: "blue", poweredBy: false });
    expect(wrapper.text()).not.toContain("Powered by");
    expect(wrapper.get("img").attributes("src")).toBe("/images/brand/emhare-wordmark-blue.png");
  });
});
