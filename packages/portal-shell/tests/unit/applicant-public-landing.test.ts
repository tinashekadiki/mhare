// Author: Tinashe K
import { mount } from "@vue/test-utils";
import { ref } from "vue";
import { describe, expect, it, vi } from "vitest";
import EmharePublicGatewayHeader from "../../components/public/EmharePublicGatewayHeader.vue";
import EmhareApplicantPublicLanding from "../../components/public/EmhareApplicantPublicLanding.vue";

vi.stubGlobal("ref", ref);
const globalComponents = {
  stubs: {
    NuxtLink: { props: ["to"], template: '<a :href="to"><slot /></a>' },
    UIcon: true,
    EmhareProductBrand: true,
  },
};

describe("shared public admissions entry", () => {
  it("keeps the student navigation by default and supports applicant links and actions", async () => {
    const studentHeader = mount(EmharePublicGatewayHeader, { global: globalComponents });
    expect(
      studentHeader.findAll('nav[aria-label="Public gateway"] a').map((link) => link.text()),
    ).toEqual(["Home", "Notices", "Services", "Events", "News"]);

    const applicantHeader = mount(EmharePublicGatewayHeader, {
      props: {
        navigationItems: [
          { label: "Admissions", href: "#admissions" },
          { label: "Before you apply", href: "#before-you-apply" },
        ],
      },
      slots: { actions: '<button type="button">Sign in</button>' },
      global: globalComponents,
    });
    expect(applicantHeader.text()).not.toContain("Notices");
    expect(applicantHeader.get('a[href="#before-you-apply"]').text()).toBe("Before you apply");
    expect(applicantHeader.text()).toContain("Sign in");
    const toggle = applicantHeader.get('[aria-label="Toggle public navigation"]');
    await toggle.trigger("click");
    expect(toggle.attributes("aria-expanded")).toBe("true");
    const mobileNavigation = applicantHeader.get('nav[aria-label="Mobile public gateway"]');
    expect(mobileNavigation.findAll("a")).toHaveLength(2);
    await mobileNavigation.get('a[href="#before-you-apply"]').trigger("click");
    expect(toggle.attributes("aria-expanded")).toBe("false");
    await toggle.trigger("click");
    await applicantHeader.trigger("keydown", { key: "Escape" });
    expect(toggle.attributes("aria-expanded")).toBe("false");
  });

  it("offers account creation and sign-in with concrete preparation and submission guidance", async () => {
    const landing = mount(EmhareApplicantPublicLanding, { global: globalComponents });
    expect(landing.findAll("h1")).toHaveLength(1);
    for (const section of ["admissions", "before-you-apply", "how-to-apply", "questions"]) {
      expect(landing.find(`#${section}`).exists()).toBe(true);
    }
    await landing.get('[data-testid="create-applicant-account"]').trigger("click");
    await landing.get('[data-testid="applicant-sign-in"]').trigger("click");
    expect(landing.emitted("create-account")).toHaveLength(1);
    expect(landing.emitted("sign-in")).toHaveLength(1);
    expect(landing.text()).toContain("confirmed payment or an authorised waiver");
    expect(landing.text()).toContain("PDF, JPEG or PNG");
    expect(landing.findAll("details")).toHaveLength(3);
    expect(landing.findAll("#how-to-apply ol > li")).toHaveLength(4);
  });
});
