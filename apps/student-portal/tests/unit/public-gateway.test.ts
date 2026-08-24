// Author: Tinashe K

import { flushPromises, mount } from "@vue/test-utils";
import { computed, ref } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";

const refresh = vi.fn();
const homePayload = {
  urgentNotices: [
    {
      publicationId: "notice-publication",
      itemId: "notice-item",
      versionId: "notice-version",
      kind: "NOTICE",
      slug: "registration-deadline",
      title: "Registration deadline",
      summary: "Complete registration on time.",
      schemaVersion: 1,
      structuredContent: [],
      publishFrom: "2026-08-17T08:00:00Z",
      pinned: true,
      featured: false,
    },
  ],
  importantLinks: [
    {
      publicationId: "link-publication",
      itemId: "link-item",
      versionId: "link-version",
      kind: "LINK",
      slug: "library",
      title: "University library",
      summary: "Open the library.",
      externalUrl: "https://library.uz.ac.zw",
      schemaVersion: 1,
      structuredContent: [],
      publishFrom: "2026-08-17T08:00:00Z",
      pinned: false,
      featured: false,
    },
  ],
  featuredCampaign: {
    publicationId: "campaign-publication",
    itemId: "campaign-item",
    versionId: "campaign-version",
    kind: "CAMPAIGN",
    slug: "orientation",
    title: "New student orientation",
    summary: "Prepare for the academic year.",
    schemaVersion: 1,
    structuredContent: [],
    publishFrom: "2026-08-17T08:00:00Z",
    pinned: false,
    featured: true,
  },
  upcomingEvents: [],
  latestNews: [
    {
      publicationId: "news-publication",
      itemId: "news-item",
      versionId: "news-version",
      kind: "NEWS",
      slug: "research-week",
      title: "Research week opens",
      summary: "Public scholarship and discovery.",
      schemaVersion: 1,
      structuredContent: [],
      publishFrom: "2026-08-17T08:00:00Z",
      pinned: false,
      featured: false,
    },
  ],
};
const publicHome = ref<Record<string, any>>(homePayload);

vi.stubGlobal("computed", computed);
vi.stubGlobal("definePageMeta", vi.fn());
vi.stubGlobal("portalDestinationUrl", (portal: string) => `/${portal}`);
vi.stubGlobal("usePublicCommunications", () => ({ home: vi.fn() }));
vi.stubGlobal("useAsyncData", async () => ({
  data: publicHome,
  pending: ref(false),
  error: ref(null),
  refresh,
}));

describe("student public gateway", () => {
  beforeEach(() => {
    publicHome.value = homePayload;
  });

  it("keeps the Azure public gateway and all three portal routes on the public page", async () => {
    const PublicGateway = (await import("../../pages/index.vue")).default;
    const wrapper = mount(
      {
        components: { PublicGateway },
        template: "<Suspense><PublicGateway /></Suspense>",
      },
      {
        global: {
          stubs: {
            EmharePublicGatewayHeader: true,
            EmharePublicGatewayFooter: {
              template:
                '<footer data-testid="institutional-public-footer">Institutional footer</footer>',
            },
            EmharePublicContentCard: {
              props: ["item"],
              template: '<article data-testid="public-content-card">{{ item.title }}</article>',
            },
            EmharePublicEventCard: true,
            EmharePublicGatewaySlider: {
              props: ["slides"],
              template:
                '<div data-testid="gateway-slider">{{ slides.length }} {{ slides[0]?.title }}</div>',
            },
            EmharePortalDestinationCard: {
              props: ["title", "href", "actionLabel"],
              template: '<a :href="href">{{ title }} {{ actionLabel }}</a>',
            },
            NuxtLink: { props: ["to"], template: '<a :href="to"><slot /></a>' },
            UIcon: true,
            USkeleton: true,
          },
        },
      },
    );
    await flushPromises();

    expect(wrapper.text()).toContain("Choose your portal");
    expect(wrapper.get('a[href="/applicant"]').text()).toContain("Applicants");
    expect(wrapper.get('a[href="/student"]').text()).toContain("Students");
    expect(wrapper.get('a[href="/staff"]').text()).toContain("Staff & Faculty");
    expect(wrapper.find(".bg-uzazure-700").exists()).toBe(true);
    expect(wrapper.find(".bg-uzorange-500").exists()).toBe(true);
    expect(wrapper.get("#notices .grid.gap-4").classes()).not.toContain("md:grid-cols-2");
    expect(wrapper.get('[data-testid="gateway-slider"]').text()).toContain(
      "3 New student orientation",
    );
    expect(wrapper.get('[data-testid="gateway-slider"]').text()).not.toContain(
      "Research week opens",
    );
    expect(wrapper.find("#news").exists()).toBe(true);
    expect(wrapper.text()).toContain("Research week opens");
    expect(wrapper.text()).not.toContain("Public notices and university information stay here");
    expect(wrapper.find('[data-testid="institutional-public-footer"]').exists()).toBe(false);
  });

  it("does not render public content sections that have no published content", async () => {
    publicHome.value = {
      urgentNotices: [],
      importantLinks: [],
      featuredCampaign: null,
      upcomingEvents: [],
      latestNews: [],
    };
    const PublicGateway = (await import("../../pages/index.vue")).default;
    const wrapper = mount(
      {
        components: { PublicGateway },
        template: "<Suspense><PublicGateway /></Suspense>",
      },
      {
        global: {
          stubs: {
            EmharePublicGatewayHeader: true,
            EmharePublicGatewayFooter: {
              template:
                '<footer data-testid="institutional-public-footer">Institutional footer</footer>',
            },
            EmharePublicContentCard: true,
            EmharePublicEventCard: true,
            EmharePublicGatewaySlider: {
              props: ["slides"],
              template: '<div data-testid="gateway-slider">{{ slides.length }}</div>',
            },
            EmharePortalDestinationCard: true,
            UIcon: true,
            USkeleton: true,
          },
        },
      },
    );
    await flushPromises();

    expect(wrapper.find("#notices").exists()).toBe(false);
    expect(wrapper.find("#services").exists()).toBe(false);
    expect(wrapper.find("#events").exists()).toBe(false);
    expect(wrapper.find("#news").exists()).toBe(false);
    expect(wrapper.get('[data-testid="gateway-slider"]').text()).toBe("3");
  });

  it("keeps the responsive two-column notice layout when multiple notices are published", async () => {
    publicHome.value = {
      ...homePayload,
      urgentNotices: [
        ...homePayload.urgentNotices,
        {
          ...homePayload.urgentNotices[0],
          publicationId: "second-notice-publication",
          itemId: "second-notice-item",
          versionId: "second-notice-version",
          slug: "second-notice",
          title: "Second notice",
        },
      ],
    };
    const PublicGateway = (await import("../../pages/index.vue")).default;
    const wrapper = mount(
      {
        components: { PublicGateway },
        template: "<Suspense><PublicGateway /></Suspense>",
      },
      {
        global: {
          stubs: {
            EmharePublicGatewayHeader: true,
            EmharePublicContentCard: true,
            EmharePublicEventCard: true,
            EmharePublicGatewaySlider: true,
            EmharePortalDestinationCard: true,
            UIcon: true,
            USkeleton: true,
          },
        },
      },
    );
    await flushPromises();

    expect(wrapper.get("#notices .grid.gap-4").classes()).toContain("md:grid-cols-2");
  });
});
